"""Phase 8: Migrate incentives from MySQL to PostgreSQL.

Source table:
  - incentive_table (25,562) → invoice_product discount enrichment + incentive rules

Strategy:
  1. Match incentive rows to PG invoice_product by bill_no + product
  2. Update invoice_product with discount_rate and discount_amount
  3. Derive incentive rules (customer + product → discount_rate) for the incentive config table
"""
from decimal import Decimal
from utils.date_utils import now
from utils.lookups import LookupCache

BATCH_SIZE = 1000

# MySQL incentive product name → PG product_id
INCENTIVE_PRODUCT_MAP = {
    'PETROL': 1,
    'DIESEL': 2,
    'XTRA_PREMIUM': 3,
}


def _to_decimal(val):
    if val is None:
        return Decimal('0')
    try:
        return Decimal(str(val))
    except Exception:
        return Decimal('0')


def _clean(val):
    if val is None:
        return None
    s = str(val).strip()
    if s in ('', 'null', 'NULL', '0', 'None', 'XXXXX', 'Nil', 'NIL', '0000'):
        return None
    return s


def migrate(mysql_conn, pg_conn, lookups):
    pg = pg_conn.cursor()
    my = mysql_conn.cursor(dictionary=True)
    ts = now()

    # ─── Load lookups ─────────────────────────────────────────
    print("\n  [8.0] Loading lookups...")

    # Bill_no → PG invoice_bill_id (both credit and cash bills can have incentives)
    bill_map = {}
    pg.execute("SELECT id, bill_no FROM invoice_bill")
    for row in pg.fetchall():
        if row[1]:
            bill_map[row[1]] = row[0]
    print(f"    Invoice bills: {len(bill_map)} entries")

    # Non-fuel product name mapping
    product_name_map = {}
    pg.execute("SELECT id, name FROM product")
    for row in pg.fetchall():
        product_name_map[row[1].upper().replace(' ', '_')] = row[0]
    # Add explicit mappings for MySQL names
    product_name_map['2T_LOOSE_OIL'] = product_name_map.get('2T_LOOSE_OIL')
    product_name_map['2T_40ML_POUCH'] = product_name_map.get('2T_40ML_POUCH')
    product_name_map['DISTEL_WATER'] = product_name_map.get('DISTILLED_WATER')
    product_name_map['BLUE_CLOTH'] = product_name_map.get('BLUE_CLOTH')
    product_name_map['YELLOW_CLOTH'] = product_name_map.get('YELLOW_CLOTH')
    product_name_map['ACID'] = product_name_map.get('BATTERY_ACID')

    # Customer name lookup
    customers = LookupCache('Customer')
    pg.execute("SELECT pe.id, pe.name FROM person_entity pe JOIN customer c ON c.id = pe.id")
    for row in pg.fetchall():
        if row[1]:
            customers.add(row[1], row[0])
    print(f"    Customers: {len(customers)} entries")

    # ─── Step 1: Update invoice_product with discounts ─────────
    print("\n  [8.1] Updating invoice_product with discounts...")

    my.execute("""
        SELECT * FROM incentive_table
        WHERE incentive_billno NOT IN ('XXXX','Nil','NIL','0','','0000')
        AND incentive_amt > 0
        ORDER BY incentive_billdate
    """)
    incentive_rows = my.fetchall()
    print(f"    Incentive rows to process: {len(incentive_rows)}")

    updated = 0
    skipped_no_bill = 0
    skipped_no_product = 0
    skipped_no_match = 0

    for row in incentive_rows:
        bill_no = _clean(row.get('incentive_billno'))
        if not bill_no:
            skipped_no_bill += 1
            continue

        invoice_bill_id = bill_map.get(bill_no)
        if not invoice_bill_id:
            # Try case-insensitive
            invoice_bill_id = bill_map.get(bill_no.upper()) or bill_map.get(bill_no.lower())
            if not invoice_bill_id:
                skipped_no_bill += 1
                continue

        product_name = _clean(row.get('incentive_productname'))
        if not product_name:
            skipped_no_product += 1
            continue

        # Resolve product_id
        product_id = INCENTIVE_PRODUCT_MAP.get(product_name)
        if not product_id:
            product_id = product_name_map.get(product_name.upper().replace(' ', '_'))
        if not product_id:
            skipped_no_product += 1
            continue

        disc_rate = _to_decimal(row.get('incentive_disc_rate'))
        disc_amt = _to_decimal(row.get('incentive_amt'))

        if disc_amt <= 0:
            continue

        # Update matching invoice_product row (use ctid subquery since PG doesn't support LIMIT in UPDATE)
        try:
            pg.execute("""
                UPDATE invoice_product
                SET discount_rate = %s, discount_amount = %s,
                    amount = gross_amount - %s
                WHERE ctid = (
                    SELECT ctid FROM invoice_product
                    WHERE invoice_bill_id = %s AND product_id = %s
                    AND (discount_rate IS NULL OR discount_rate = 0)
                    LIMIT 1
                )
            """, (disc_rate, disc_amt, disc_amt, invoice_bill_id, product_id))
        except Exception as e:
            pg.connection.rollback()
            skipped_no_match += 1
            continue

        if pg.rowcount > 0:
            updated += 1
        else:
            skipped_no_match += 1

        if updated % BATCH_SIZE == 0:
            pg_conn.commit()

    pg_conn.commit()
    print(f"    Updated: {updated} invoice_product rows")
    print(f"    Skipped: {skipped_no_bill} no bill, {skipped_no_product} no product, {skipped_no_match} no match")

    # Also update invoice_bill.total_discount from sum of product discounts
    print("\n  [8.2] Updating invoice_bill discount totals...")
    pg.execute("""
        UPDATE invoice_bill ib SET total_discount = sub.total_disc
        FROM (
            SELECT invoice_bill_id, SUM(COALESCE(discount_amount, 0)) as total_disc
            FROM invoice_product
            WHERE discount_amount IS NOT NULL AND discount_amount > 0
            GROUP BY invoice_bill_id
        ) sub
        WHERE ib.id = sub.invoice_bill_id
    """)
    bills_updated = pg.rowcount
    pg_conn.commit()
    print(f"    Updated discount_amount on {bills_updated} bills")

    # ─── Step 2: Derive incentive rules ────────────────────────
    print("\n  [8.3] Deriving incentive rules...")

    # Get the most recent/common discount rate per (customer, product)
    my.execute("""
        SELECT incentive_customername, incentive_productname, incentive_disc_rate,
               COUNT(*) as cnt, MAX(incentive_billdate) as last_date
        FROM incentive_table
        WHERE incentive_customername IS NOT NULL
        AND incentive_customername NOT IN ('', 'Nil', 'NIL', 'NULL')
        AND incentive_productname NOT IN ('Nil', 'NIL', '')
        AND incentive_disc_rate > 0
        GROUP BY incentive_customername, incentive_productname, incentive_disc_rate
        ORDER BY incentive_customername, incentive_productname, cnt DESC
    """)
    rule_rows = my.fetchall()

    # Keep the most frequent rate per (customer, product)
    rules = {}
    for row in rule_rows:
        cust = row['incentive_customername']
        prod = row['incentive_productname']
        key = (cust, prod)
        if key not in rules:
            rules[key] = (row['incentive_disc_rate'], row['cnt'])

    rule_count = 0
    rule_skipped = 0
    for (cust_name, prod_name), (disc_rate, _) in rules.items():
        customer_id = customers.get(cust_name, fuzzy=True, threshold=0.85)
        if not customer_id:
            rule_skipped += 1
            continue

        product_id = INCENTIVE_PRODUCT_MAP.get(prod_name)
        if not product_id:
            product_id = product_name_map.get(prod_name.upper().replace(' ', '_'))
        if not product_id:
            rule_skipped += 1
            continue

        try:
            pg.execute("""
                INSERT INTO incentive (scid, customer_id, product_id, discount_rate, active, created_at, updated_at)
                VALUES (1, %s, %s, %s, true, %s, %s)
                ON CONFLICT DO NOTHING
            """, (customer_id, product_id, Decimal(str(disc_rate)), ts, ts))
            if pg.rowcount > 0:
                rule_count += 1
        except Exception:
            pg.connection.rollback()

    pg_conn.commit()
    print(f"    Incentive rules created: {rule_count} ({rule_skipped} skipped)")

    # ─── Summary ──────────────────────────────────────────────
    print(f"\n  [8.4] Summary:")
    print(f"    Invoice products updated with discounts: {updated}")
    print(f"    Invoice bills with discount totals: {bills_updated}")
    print(f"    Incentive rules: {rule_count}")
    customers.report_unmatched()

    return lookups
