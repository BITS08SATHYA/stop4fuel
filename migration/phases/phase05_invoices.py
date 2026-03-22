"""Phase 5: Migrate invoices (cash bills + credit bills) from MySQL to PostgreSQL.

Source tables:
  - creditbill_test + creditbill_products_test  (~74K credit invoices)
  - cashbill + cashbill_products                (~157K cash invoices)

Target tables:
  - invoice_bill     (header)
  - invoice_product  (line items)
"""
import datetime
from decimal import Decimal
from utils.date_utils import sanitize_timestamp, now
from utils.lookups import LookupCache, IDMapper
from utils.migration_logger import MigrationLogger
from mappings import FUEL_PRODUCT_MAP


# Payment mode mapping: MySQL -> PostgreSQL
PAYMENT_MODE_MAP = {
    'Cash': 'CASH',
    'cash': 'CASH',
    'CASH': 'CASH',
    'Card': 'CARD',
    'card': 'CARD',
    'CARD': 'CARD',
    'UPI': 'UPI',
    'upi': 'UPI',
    'Upi': 'UPI',
    'Cheque': 'CHEQUE',
    'cheque': 'CHEQUE',
    'CHEQUE': 'CHEQUE',
    'Bank Transfer': 'BANK',
    'NEFT': 'BANK',
    'CCMS': 'CCMS',
    'ccms': 'CCMS',
    '': 'CASH',
}

BATCH_SIZE = 1000


def _to_decimal(val):
    """Convert a numeric value to Decimal, defaulting to 0."""
    if val is None:
        return Decimal('0')
    try:
        return Decimal(str(val))
    except Exception:
        return Decimal('0')


def _clean(val):
    """Strip string or return None."""
    if val is None:
        return None
    s = str(val).strip()
    if s in ('', 'null', 'NULL', '0', 'XXXXX', 'None'):
        return None
    return s


def _combine_datetime(date_val, time_val):
    """Combine MySQL date + time fields into a datetime."""
    dt = sanitize_timestamp(date_val)
    if dt is None:
        return None
    if time_val and isinstance(time_val, datetime.timedelta):
        total_seconds = int(time_val.total_seconds())
        hours = total_seconds // 3600
        minutes = (total_seconds % 3600) // 60
        seconds = total_seconds % 60
        dt = dt.replace(hour=hours % 24, minute=minutes, second=seconds)
    return dt


def migrate(mysql_conn, pg_conn, lookups):
    """Migrate credit + cash invoices from MySQL to PostgreSQL."""
    log = MigrationLogger('phase05_invoices')
    pg = pg_conn.cursor()
    my = mysql_conn.cursor(dictionary=True)
    ts = now()

    # ─── Load lookups from PostgreSQL ─────────────────────────
    log.info("[5.0] Loading lookups from PostgreSQL...")

    # Customers: name -> id
    customers = LookupCache('Customer')
    pg.execute("SELECT pe.id, pe.name FROM person_entity pe JOIN customer c ON c.id = pe.id")
    for row in pg.fetchall():
        if row[1]:
            customers.add(row[1], row[0])

    # Customer MySQL CID -> PG id mapping
    # Build from username pattern: cust_{mysql_id}
    cid_map = {}
    pg.execute("SELECT pe.id, u.username FROM person_entity pe JOIN users u ON u.id = pe.id JOIN customer c ON c.id = pe.id")
    for row in pg.fetchall():
        username = row[1]
        if username and username.startswith('cust_'):
            try:
                mysql_cid = int(username.replace('cust_', ''))
                cid_map[mysql_cid] = row[0]
            except ValueError:
                pass
    log.info(f"  Customers: {len(customers)} names, {len(cid_map)} CID mappings")

    # Vehicles: number -> id
    vehicles = LookupCache('Vehicle')
    pg.execute("SELECT id, vehicle_number FROM vehicle")
    for row in pg.fetchall():
        if row[1]:
            vehicles.add(row[1], row[0])
    log.info(f"  Vehicles: {len(vehicles)} entries")

    # Products: name -> id
    products = LookupCache('Product')
    pg.execute("SELECT id, name FROM product")
    for row in pg.fetchall():
        if row[1]:
            products.add(row[1], row[0])
    # Add fuel aliases from FUEL_PRODUCT_MAP
    for mysql_name, pg_name in FUEL_PRODUCT_MAP.items():
        pg_id = products.get(pg_name, fuzzy=False)
        if pg_id:
            products.add(mysql_name, pg_id)
    log.info(f"  Products: {len(products)} entries")

    # Employees: name -> id (for raised_by)
    employees = LookupCache('Employee')
    eid_map = {}
    pg.execute("SELECT pe.id, pe.name, u.username FROM person_entity pe JOIN users u ON u.id = pe.id JOIN employees e ON e.id = pe.id")
    for row in pg.fetchall():
        if row[1]:
            employees.add(row[1], row[0])
        if row[2] and row[2].startswith('emp_'):
            try:
                mysql_eid = int(row[2].replace('emp_', ''))
                eid_map[mysql_eid] = row[0]
            except ValueError:
                pass
    log.info(f"  Employees: {len(employees)} names, {len(eid_map)} EID mappings")

    # Check for existing bills (idempotence)
    existing_bills = set()
    pg.execute("SELECT bill_no FROM invoice_bill")
    for row in pg.fetchall():
        if row[0]:
            existing_bills.add(row[0])
    if existing_bills:
        log.warn(f"Found {len(existing_bills)} existing bills in PG — will skip duplicates")

    # Bill number tracking (to avoid duplicates)
    bill_no_to_pg_id = {}

    # Pre-load existing bill mappings for product migration
    if existing_bills:
        pg.execute("SELECT id, bill_no FROM invoice_bill")
        for row in pg.fetchall():
            if row[1]:
                bill_no_to_pg_id[row[1]] = row[0]

    # ─── Migrate Credit Bills ─────────────────────────────────
    log.info("\n[5.1] Migrating credit bills...")
    my.execute("SELECT * FROM creditbill_test ORDER BY idx_Date")
    credit_bills = my.fetchall()
    log.info(f"  Source: {len(credit_bills)} credit bills")

    credit_count = 0
    batch = []

    for bill in credit_bills:
        bill_no = _clean(bill.get('idx_BillID'))
        if not bill_no:
            log.skip_record(f"credit_bill idx={bill.get('Sno', '?')}", "no bill_no")
            continue

        # Idempotence: skip if already exists
        if bill_no in existing_bills:
            bill_no_to_pg_id.setdefault(bill_no, None)  # will be filled from existing
            continue

        bill_date = _combine_datetime(bill.get('idx_Date'), bill.get('time'))
        if bill_date is None:
            log.skip_record(f"credit_bill {bill_no}", "invalid date")
            continue

        # Resolve customer
        customer_id = None
        mysql_cid = bill.get('CID')
        if mysql_cid and mysql_cid in cid_map:
            customer_id = cid_map[mysql_cid]
        if not customer_id:
            cust_name = _clean(bill.get('idx_CustomerName'))
            if cust_name:
                customer_id = customers.get(cust_name, fuzzy=True, threshold=0.9)

        # Resolve vehicle
        vehicle_id = None
        vno = _clean(bill.get('idx_VehicleNo'))
        if vno:
            vehicle_id = vehicles.get(vno, fuzzy=True, threshold=0.9)

        # Resolve employee
        raised_by_id = None
        mysql_eid = bill.get('EID')
        if mysql_eid and mysql_eid in eid_map:
            raised_by_id = eid_map[mysql_eid]
        if not raised_by_id:
            emp_name = _clean(bill.get('idx_Employee'))
            if emp_name:
                raised_by_id = employees.get(emp_name, fuzzy=True, threshold=0.9)

        # Amounts
        net_amount = _to_decimal(bill.get('NetAmount'))
        sub_total = _to_decimal(bill.get('Sub_total'))
        discount = _to_decimal(bill.get('Discount'))
        gross_amount = sub_total if sub_total > 0 else net_amount

        # Payment status
        bill_status = (bill.get('Bill_Status') or 'Not Paid').strip()
        payment_status = 'PAID' if bill_status.lower() == 'paid' else 'NOT_PAID'

        # Optional fields
        signatory_name = _clean(bill.get('SignatorysName'))
        signatory_cell = _clean(bill.get('SignatorysCellno'))
        vehicle_km = bill.get('VehicleKM')
        if vehicle_km and vehicle_km > 0:
            vehicle_km = int(vehicle_km)
        else:
            vehicle_km = None
        reading_open = bill.get('PumpReadingOpen')
        reading_close = bill.get('PumpReadingClose')
        if reading_open and reading_open > 0:
            reading_open = int(reading_open)
        else:
            reading_open = None
        if reading_close and reading_close > 0:
            reading_close = int(reading_close)
        else:
            reading_close = None
        customer_gst = _clean(bill.get('Customer_GST'))
        indent_no = _clean(bill.get('idx_indentNo'))
        bill_desc = _clean(bill.get('Bill_Description'))

        batch.append((
            1,  # scid
            bill_date,
            bill_no,
            'CREDIT',
            None,  # payment_mode (credit bills don't have one at creation)
            payment_status,
            'PAID' if payment_status == 'PAID' else 'PENDING',  # bill_status
            customer_id,
            vehicle_id,
            raised_by_id,
            gross_amount,
            discount,
            net_amount,
            customer_gst,
            indent_no,
            bill_desc,
            signatory_name,
            signatory_cell,
            vehicle_km,
            reading_open,
            reading_close,
            ts, ts,
        ))

        if len(batch) >= BATCH_SIZE:
            credit_count += _insert_invoice_batch(pg, batch, bill_no_to_pg_id, log)
            batch = []
            pg_conn.commit()

    if batch:
        credit_count += _insert_invoice_batch(pg, batch, bill_no_to_pg_id, log)
        pg_conn.commit()

    log.increment('credit_bills', credit_count)
    log.info(f"  Inserted: {credit_count} credit bills")

    # ─── Migrate Credit Bill Products ─────────────────────────
    log.info("\n[5.2] Migrating credit bill products...")
    my.execute("SELECT * FROM creditbill_products_test ORDER BY PSno")
    credit_products = my.fetchall()
    log.info(f"  Source: {len(credit_products)} line items")

    cp_count = _migrate_products(pg, pg_conn, credit_products, bill_no_to_pg_id, products, ts, log)
    log.increment('credit_products', cp_count)
    log.info(f"  Inserted: {cp_count} credit product lines")

    # ─── Migrate Cash Bills ───────────────────────────────────
    log.info("\n[5.3] Migrating cash bills...")
    my.execute("SELECT * FROM cashbill ORDER BY idx_Date")
    cash_bills = my.fetchall()
    log.info(f"  Source: {len(cash_bills)} cash bills")

    cash_count = 0
    batch = []

    for bill in cash_bills:
        bill_no = _clean(bill.get('idx_BillID'))
        if not bill_no:
            log.skip_record(f"cash_bill idx={bill.get('C_Sno', '?')}", "no bill_no")
            continue

        # Idempotence: skip if already exists
        if bill_no in existing_bills:
            bill_no_to_pg_id.setdefault(bill_no, None)
            continue

        bill_date = _combine_datetime(bill.get('idx_Date'), bill.get('time'))
        if bill_date is None:
            log.skip_record(f"cash_bill {bill_no}", "invalid date")
            continue

        # Resolve customer (many cash bills won't have one)
        customer_id = None
        cust_name = _clean(bill.get('idx_CustomerName'))
        if cust_name:
            customer_id = customers.get(cust_name, fuzzy=True, threshold=0.9)

        # Resolve vehicle
        vehicle_id = None
        vno = _clean(bill.get('idx_VehicleNo'))
        if vno:
            vehicle_id = vehicles.get(vno, fuzzy=True, threshold=0.9)

        # Resolve employee
        raised_by_id = None
        mysql_eid = bill.get('EID')
        if mysql_eid and mysql_eid in eid_map:
            raised_by_id = eid_map[mysql_eid]
        if not raised_by_id:
            emp_name = _clean(bill.get('idx_Employee'))
            if emp_name:
                raised_by_id = employees.get(emp_name, fuzzy=True, threshold=0.9)

        # Payment mode
        pay_mode_raw = (bill.get('Payment_Mode') or '').strip()
        payment_mode = PAYMENT_MODE_MAP.get(pay_mode_raw, 'CASH')

        # Amounts
        net_amount = _to_decimal(bill.get('NetAmount'))
        discount = _to_decimal(bill.get('Discount'))
        final_amount = _to_decimal(bill.get('Final_Amount'))
        gross_amount = final_amount if final_amount > 0 else net_amount

        # Optional fields
        vehicle_km = bill.get('VehicleKM')
        if vehicle_km and vehicle_km > 0:
            vehicle_km = int(vehicle_km)
        else:
            vehicle_km = None
        reading_open = bill.get('PumpReadingOpen')
        reading_close = bill.get('PumpReadingClose')
        if reading_open and reading_open > 0:
            reading_open = int(reading_open)
        else:
            reading_open = None
        if reading_close and reading_close > 0:
            reading_close = int(reading_close)
        else:
            reading_close = None
        customer_gst = _clean(bill.get('Customer_GST'))

        batch.append((
            1,  # scid
            bill_date,
            bill_no,
            'CASH',
            payment_mode,
            'PAID',  # cash bills always paid
            'PAID',  # bill_status
            customer_id,
            vehicle_id,
            raised_by_id,
            gross_amount,
            discount,
            net_amount,
            customer_gst,
            None,  # indent_no
            None,  # bill_desc
            None,  # signatory_name
            None,  # signatory_cell
            vehicle_km,
            reading_open,
            reading_close,
            ts, ts,
        ))

        if len(batch) >= BATCH_SIZE:
            cash_count += _insert_invoice_batch(pg, batch, bill_no_to_pg_id, log)
            batch = []
            pg_conn.commit()

    if batch:
        cash_count += _insert_invoice_batch(pg, batch, bill_no_to_pg_id, log)
        pg_conn.commit()

    log.increment('cash_bills', cash_count)
    log.info(f"  Inserted: {cash_count} cash bills")

    # ─── Migrate Cash Bill Products ───────────────────────────
    log.info("\n[5.4] Migrating cash bill products...")
    my.execute("SELECT * FROM cashbill_products ORDER BY C_Sno")
    cash_products = my.fetchall()
    log.info(f"  Source: {len(cash_products)} line items")

    cashp_count = _migrate_products(pg, pg_conn, cash_products, bill_no_to_pg_id, products, ts, log)
    log.increment('cash_products', cashp_count)
    log.info(f"  Inserted: {cashp_count} cash product lines")

    # ─── Summary ──────────────────────────────────────────────
    log.increment('total_bills', credit_count + cash_count)
    log.increment('total_products', cp_count + cashp_count)

    customers.report_unmatched()
    vehicles.report_unmatched()
    products.report_unmatched()
    employees.report_unmatched()

    log.summary()

    lookups['invoices'] = bill_no_to_pg_id
    return lookups


def _insert_invoice_batch(pg, batch, bill_no_to_pg_id, log):
    """Insert a batch of invoice_bill rows. Returns count inserted."""
    count = 0
    for row in batch:
        bill_no = row[2]  # bill_no is 3rd element
        try:
            pg.execute("""
                INSERT INTO invoice_bill (
                    scid, bill_date, bill_no, bill_type, payment_mode,
                    payment_status, bill_status, customer_id, vehicle_id, raised_by_id,
                    gross_amount, total_discount, net_amount, customer_gst,
                    indent_no, bill_desc, signatory_name, signatory_cell_no,
                    vehicle_km, reading_open, reading_close,
                    created_at, updated_at
                ) VALUES (
                    %s, %s, %s, %s, %s,
                    %s, %s, %s, %s, %s,
                    %s, %s, %s, %s,
                    %s, %s, %s, %s,
                    %s, %s, %s,
                    %s, %s
                ) RETURNING id
            """, row)
            result = pg.fetchone()
            if result:
                bill_no_to_pg_id[bill_no] = result[0]
                count += 1
        except Exception as e:
            pg.connection.rollback()
            log.error_record(f"invoice_bill {bill_no}", e)
    return count


def _migrate_products(pg, pg_conn, product_rows, bill_no_to_pg_id, products_lookup, ts, log):
    """Migrate invoice product line items. Returns count inserted."""
    count = 0
    batch = []

    for prod in product_rows:
        bill_no = _clean(prod.get('fk_BillID'))
        if not bill_no or bill_no not in bill_no_to_pg_id:
            log.skip_record(f"product fk_BillID={bill_no}", "bill not found in PG")
            continue

        invoice_bill_id = bill_no_to_pg_id[bill_no]
        if invoice_bill_id is None:
            continue  # Existing bill but no ID loaded

        # Resolve product
        prod_name = _clean(prod.get('Product_Name'))
        product_id = None
        if prod_name:
            product_id = products_lookup.get(prod_name, fuzzy=True, threshold=0.85)

        quantity = _to_decimal(prod.get('Quantity'))
        unit_price = _to_decimal(prod.get('Rate'))
        amount = _to_decimal(prod.get('Amount'))

        batch.append((
            1,  # scid
            invoice_bill_id,
            product_id,
            quantity,
            unit_price,
            amount,
            amount,  # gross_amount = amount (no separate field in source)
            ts, ts,
        ))

        if len(batch) >= BATCH_SIZE:
            count += _insert_product_batch(pg, batch, log)
            batch = []
            pg_conn.commit()

    if batch:
        count += _insert_product_batch(pg, batch, log)
        pg_conn.commit()

    return count


def _insert_product_batch(pg, batch, log):
    """Insert a batch of invoice_product rows. Returns count inserted."""
    count = 0
    for row in batch:
        try:
            pg.execute("""
                INSERT INTO invoice_product (
                    scid, invoice_bill_id, product_id,
                    quantity, unit_price, amount, gross_amount,
                    created_at, updated_at
                ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)
            """, row)
            count += 1
        except Exception as e:
            pg.connection.rollback()
            log.error_record(f"invoice_product bill_id={row[1]} product_id={row[2]}", e)
    return count
