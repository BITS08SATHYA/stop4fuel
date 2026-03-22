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
from utils.migration_logger import MigrationLogger

BATCH_SIZE = 1000


def _build_fuel_product_map(pg_cursor, log):
    """Dynamically build fuel product name → ID map from PG (replaces hardcoded 1,2,3)."""
    fuel_map = {}
    pg_cursor.execute("SELECT id, name FROM product WHERE category = 'FUEL'")
    for row in pg_cursor.fetchall():
        product_id, name = row[0], row[1]
        fuel_map[name.upper()] = product_id
        fuel_map[name.upper().replace(' ', '_')] = product_id
    # Add common MySQL aliases
    if 'PETROL' not in fuel_map and 'PETROL' not in fuel_map:
        log.warn("No 'Petrol' product found in PG — fuel incentives may fail")
    if 'XTRA PREMIUM' in fuel_map:
        fuel_map['XTRA_PREMIUM'] = fuel_map['XTRA PREMIUM']
    elif 'XTRA_PREMIUM' in fuel_map:
        fuel_map['XTRA PREMIUM'] = fuel_map['XTRA_PREMIUM']
    log.info(f"  Fuel products (dynamic): {fuel_map}")
    return fuel_map


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
    log = MigrationLogger('phase08_incentives')
    pg = pg_conn.cursor()
    my = mysql_conn.cursor(dictionary=True)
    ts = now()

    # ─── Load lookups ─────────────────────────────────────────
    log.info("[8.0] Loading lookups...")

    # Fuel product map — dynamic, not hardcoded
    fuel_product_map = _build_fuel_product_map(pg, log)

    # Bill_no → PG invoice_bill_id
    bill_map = {}
    pg.execute("SELECT id, bill_no FROM invoice_bill")
    for row in pg.fetchall():
        if row[1]:
            bill_map[row[1]] = row[0]
    log.info(f"  Invoice bills: {len(bill_map)} entries")

    # Non-fuel product name mapping
    product_name_map = {}
    pg.execute("SELECT id, name FROM product")
    for row in pg.fetchall():
        product_name_map[row[1].upper().replace(' ', '_')] = row[0]

    # Add explicit mappings for MySQL names that differ from PG names
    # (only add cross-references, not self-assignments)
    distilled_id = product_name_map.get('DISTILLED_WATER')
    if distilled_id:
        product_name_map['DISTEL_WATER'] = distilled_id

    acid_id = product_name_map.get('BATTERY_ACID')
    if acid_id:
        product_name_map['ACID'] = acid_id

    # Customer name lookup
    customers = LookupCache('Customer')
    pg.execute("SELECT pe.id, pe.name FROM person_entity pe JOIN customer c ON c.id = pe.id")
    for row in pg.fetchall():
        if row[1]:
            customers.add(row[1], row[0])
    log.info(f"  Customers: {len(customers)} entries")

    # ─── Step 1: Update invoice_product with discounts ─────────
    log.info("\n[8.1] Updating invoice_product with discounts...")

    my.execute("""
        SELECT * FROM incentive_table
        WHERE incentive_billno NOT IN ('XXXX','Nil','NIL','0','','0000')
        AND incentive_amt > 0
        ORDER BY incentive_billdate
    """)
    incentive_rows = my.fetchall()
    log.info(f"  Incentive rows to process: {len(incentive_rows)}")

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
                log.skip_record(f"incentive bill={bill_no}", "bill not found in PG")
                continue

        product_name = _clean(row.get('incentive_productname'))
        if not product_name:
            skipped_no_product += 1
            continue

        # Resolve product_id: try fuel map first, then general product map
        product_id = fuel_product_map.get(product_name)
        if not product_id:
            product_id = fuel_product_map.get(product_name.upper())
        if not product_id:
            product_id = product_name_map.get(product_name.upper().replace(' ', '_'))
        if not product_id:
            skipped_no_product += 1
            log.skip_record(f"incentive bill={bill_no} product={product_name}", "product not found")
            continue

        disc_rate = _to_decimal(row.get('incentive_disc_rate'))
        disc_amt = _to_decimal(row.get('incentive_amt'))

        if disc_amt <= 0:
            continue

        # Update matching invoice_product row using id subquery (not ctid)
        try:
            pg.execute("""
                UPDATE invoice_product
                SET discount_rate = %s, discount_amount = %s,
                    amount = gross_amount - %s
                WHERE id = (
                    SELECT id FROM invoice_product
                    WHERE invoice_bill_id = %s AND product_id = %s
                    AND (discount_rate IS NULL OR discount_rate = 0)
                    ORDER BY id
                    LIMIT 1
                )
            """, (disc_rate, disc_amt, disc_amt, invoice_bill_id, product_id))
        except Exception as e:
            pg.connection.rollback()
            log.error_record(f"incentive bill={bill_no} product={product_name}", e)
            skipped_no_match += 1
            continue

        if pg.rowcount > 0:
            updated += 1
        else:
            skipped_no_match += 1

        if updated % BATCH_SIZE == 0:
            pg_conn.commit()

    pg_conn.commit()
    log.increment('products_updated', updated)
    log.info(f"  Updated: {updated} invoice_product rows")
    log.info(f"  Skipped: {skipped_no_bill} no bill, {skipped_no_product} no product, {skipped_no_match} no match")

    # Also update invoice_bill.total_discount from sum of product discounts
    log.info("\n[8.2] Updating invoice_bill discount totals...")
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
    log.increment('bills_discount_updated', bills_updated)
    log.info(f"  Updated discount_amount on {bills_updated} bills")

    # ─── Step 2: Derive incentive rules ────────────────────────
    log.info("\n[8.3] Deriving incentive rules...")

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

        product_id = fuel_product_map.get(prod_name)
        if not product_id:
            product_id = fuel_product_map.get(prod_name.upper())
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
        except Exception as e:
            pg.connection.rollback()
            log.error_record(f"incentive_rule cust={cust_name} prod={prod_name}", e)

    pg_conn.commit()
    log.increment('incentive_rules', rule_count)
    log.info(f"  Incentive rules created: {rule_count} ({rule_skipped} skipped)")

    # ─── Summary ──────────────────────────────────────────────
    customers.report_unmatched()
    log.summary()

    return lookups
