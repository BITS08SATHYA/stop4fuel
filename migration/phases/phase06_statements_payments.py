"""Phase 6: Migrate statements + payments from MySQL to PostgreSQL.

Source tables:
  Statements:
    - credit_statement (7,178) → statement
    - statement_association_1 (62,584) → invoice_bill.statement_id linkage

  Individual bill payments (NOT statement-linked):
    - incomebill (15,224 non-statement) → payment with invoice_bill_id
    - intermediatebills (1,544 non-statement) → payment with invoice_bill_id
    - cheque_incomebill, card_incomebill, ccms_incomebill, bank_transfer_incomebill → payment details

  Statement payments:
    - income_creditbill_statement (6,839) → payment with statement_id
    - intermediate_creditbill_statement (1,537) → payment with statement_id

Key insight:
  - incomebill entries for statement-linked bills are auto-generated duplicates → SKIP them
  - For bills/statements with both intermediate + full records, use intermediate only (full is summary)
"""
import datetime
from decimal import Decimal
from utils.date_utils import sanitize_date, sanitize_timestamp, now
from utils.lookups import LookupCache
from utils.migration_logger import MigrationLogger

BATCH_SIZE = 1000


def _build_payment_mode_map(pg_cursor):
    """Dynamically build payment mode name → ID map from PG table."""
    mode_map = {}
    pg_cursor.execute("SELECT id, mode_name FROM payment_mode")
    for row in pg_cursor.fetchall():
        mode_id, mode_name = row[0], row[1]
        mode_map[mode_name] = mode_id
        mode_map[mode_name.lower()] = mode_id
        mode_map[mode_name.upper()] = mode_id
    # Add common aliases
    aliases = {
        'Bank Statements': 'BANK', 'Bank_Statements': 'BANK', 'Bank Transfer': 'BANK',
        'NEFT': 'BANK', 'card': 'CARD', 'Card': 'CARD',
        'cash': 'CASH', 'Cash': 'CASH',
        'cheque': 'CHEQUE', 'Cheque': 'CHEQUE',
        'upi': 'UPI', 'Upi': 'UPI',
        'ccms': 'CCMS',
    }
    for alias, canonical in aliases.items():
        canonical_upper = canonical.upper()
        if canonical_upper in mode_map and alias not in mode_map:
            mode_map[alias] = mode_map[canonical_upper]
    return mode_map


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
    if s in ('', 'null', 'NULL', '0', 'None', 'XXXXX'):
        return None
    return s


def migrate(mysql_conn, pg_conn, lookups):
    log = MigrationLogger('phase06_statements_payments')
    pg = pg_conn.cursor()
    my = mysql_conn.cursor(dictionary=True)
    ts = now()

    # ─── Load lookups ─────────────────────────────────────────
    log.info("[6.0] Loading lookups...")

    # Dynamic payment mode lookup
    payment_mode_map = _build_payment_mode_map(pg)
    default_cash_id = payment_mode_map.get('CASH')
    if not default_cash_id:
        raise RuntimeError("payment_mode table missing 'CASH' entry — cannot proceed")
    log.info(f"  Payment modes: {len(set(payment_mode_map.values()))} modes loaded dynamically")

    # Customers
    customers = LookupCache('Customer')
    pg.execute("SELECT pe.id, pe.name FROM person_entity pe JOIN customer c ON c.id = pe.id")
    for row in pg.fetchall():
        if row[1]:
            customers.add(row[1], row[0])

    cid_map = {}
    pg.execute("SELECT pe.id, u.username FROM person_entity pe JOIN users u ON u.id = pe.id JOIN customer c ON c.id = pe.id")
    for row in pg.fetchall():
        if row[1] and row[1].startswith('cust_'):
            try:
                cid_map[int(row[1].replace('cust_', ''))] = row[0]
            except ValueError:
                pass
    log.info(f"  Customers: {len(customers)} names, {len(cid_map)} CID mappings")

    # Bill map
    bill_map = {}
    pg.execute("SELECT id, bill_no FROM invoice_bill")
    for row in pg.fetchall():
        if row[1]:
            bill_map[row[1]] = row[0]
    log.info(f"  Invoice bills: {len(bill_map)} entries")

    # Check existing statements (idempotence)
    existing_statements = set()
    pg.execute("SELECT statement_no FROM statement")
    for row in pg.fetchall():
        if row[0]:
            existing_statements.add(row[0])
    if existing_statements:
        log.warn(f"Found {len(existing_statements)} existing statements — will skip duplicates")

    # Statement-linked bills (from MySQL) — to exclude from individual payment migration
    my.execute("SELECT DISTINCT Billno FROM statement_association_1")
    statement_linked_bills = set(row['Billno'] for row in my.fetchall())
    log.info(f"  Statement-linked bills: {len(statement_linked_bills)}")

    # Bills with intermediate payments (use intermediate, skip incomebill summary)
    my.execute("SELECT DISTINCT fkBillID FROM intermediatebills")
    bills_with_intermediates = set(row['fkBillID'] for row in my.fetchall())

    # Statements with intermediate payments
    my.execute("SELECT DISTINCT idxIntermediate_CreditBill_Statement_No FROM intermediate_creditbill_statement")
    stmts_with_intermediates = set(row['idxIntermediate_CreditBill_Statement_No'] for row in my.fetchall())

    # Load payment detail tables for enrichment
    log.info("  Loading payment details (cheque/card/ccms/bank)...")
    cheque_details = {}
    my.execute("SELECT idxBill_ID, ChequeNo, ChequeBankName, CheqCusName FROM cheque_incomebill")
    for row in my.fetchall():
        bid = row['idxBill_ID']
        ref = _clean(str(row.get('ChequeNo', '')))
        bank = _clean(row.get('ChequeBankName'))
        name = _clean(row.get('CheqCusName'))
        cheque_details[bid] = (ref, f"{bank or ''} {name or ''}".strip() or None)

    card_details = {}
    my.execute("SELECT idxBill_ID, Card_Number, Card_Name FROM card_incomebill")
    for row in my.fetchall():
        bid = row['idxBill_ID']
        card_details[bid] = (_clean(row.get('Card_Number')), _clean(row.get('Card_Name')))

    ccms_details = {}
    my.execute("SELECT idxBill_ID, CCMS_CardNo, CCMS_CName FROM ccms_incomebill")
    for row in my.fetchall():
        bid = row['idxBill_ID']
        ccms_details[bid] = (_clean(row.get('CCMS_CardNo')), _clean(row.get('CCMS_CName')))

    bank_details = {}
    my.execute("SELECT BS_BillNo, BS_BANK_Name FROM bank_transfer_incomebill")
    for row in my.fetchall():
        bid = row['BS_BillNo']
        bank_details[bid] = (None, _clean(row.get('BS_BANK_Name')))

    log.info(f"  Details: {len(cheque_details)} cheque, {len(card_details)} card, {len(ccms_details)} ccms, {len(bank_details)} bank")

    # ─── Step 1: Migrate Statements ──────────────────────────
    log.info("\n[6.1] Migrating statements...")
    my.execute("SELECT * FROM credit_statement ORDER BY idx_Date")
    statements = my.fetchall()

    smt_no_to_pg_id = {}
    smt_count = 0

    for smt in statements:
        smt_no_raw = smt.get('idx_SmtNo')
        if smt_no_raw is None:
            log.skip_record("statement", "no statement number")
            continue

        statement_no = f"S-{smt_no_raw}"

        # Idempotence
        if statement_no in existing_statements:
            # Load existing ID for FK resolution
            pg.execute("SELECT id FROM statement WHERE statement_no = %s", (statement_no,))
            row = pg.fetchone()
            if row:
                smt_no_to_pg_id[smt_no_raw] = row[0]
            continue

        smt_date = sanitize_date(smt.get('idx_Date'))
        if smt_date is None:
            log.skip_record(f"statement S-{smt_no_raw}", "invalid date")
            continue

        customer_id = None
        cust_name = _clean(smt.get('Customer_Name'))
        if cust_name:
            customer_id = customers.get(cust_name, fuzzy=True, threshold=0.9)
        if not customer_id:
            smt_name = _clean(smt.get('Smt_Name'))
            if smt_name:
                customer_id = customers.get(smt_name, fuzzy=True, threshold=0.9)
        if not customer_id:
            log.skip_record(f"statement S-{smt_no_raw}", "no customer match")
            continue

        amount = _to_decimal(smt.get('Smt_Amt'))
        status_raw = (smt.get('Smt_Status') or 'Not Paid').strip()

        try:
            pg.execute("""
                INSERT INTO statement (
                    scid, statement_no, customer_id, from_date, to_date,
                    statement_date, total_amount, rounding_amount, net_amount,
                    received_amount, balance_amount, status,
                    created_at, updated_at
                ) VALUES (1, %s, %s, %s, %s, %s, %s, 0, %s, 0, %s, %s, %s, %s)
                RETURNING id
            """, (statement_no, customer_id, smt_date, smt_date, smt_date,
                  amount, amount, amount, 'NOT_PAID', ts, ts))
            result = pg.fetchone()
            if result:
                smt_no_to_pg_id[smt_no_raw] = result[0]
                smt_count += 1
        except Exception as e:
            pg.connection.rollback()
            log.error_record(f"statement S-{smt_no_raw}", e)

    pg_conn.commit()
    log.increment('statements', smt_count)
    log.info(f"  Inserted: {smt_count} statements")

    # ─── Step 2: Link bills to statements ────────────────────
    log.info("\n[6.2] Linking bills to statements...")
    my.execute("SELECT Stno, Billno FROM statement_association_1")
    assoc_rows = my.fetchall()

    linked = 0
    smt_bill_counts = {}
    for row in assoc_rows:
        smt_no = row['Stno']
        bill_no = row['Billno']
        pg_smt_id = smt_no_to_pg_id.get(smt_no)
        pg_bill_id = bill_map.get(bill_no)
        if pg_smt_id and pg_bill_id:
            pg.execute("UPDATE invoice_bill SET statement_id = %s WHERE id = %s AND statement_id IS NULL",
                       (pg_smt_id, pg_bill_id))
            linked += pg.rowcount
            smt_bill_counts[pg_smt_id] = smt_bill_counts.get(pg_smt_id, 0) + 1

    # Update number_of_bills on each statement
    for smt_id, count in smt_bill_counts.items():
        pg.execute("UPDATE statement SET number_of_bills = %s WHERE id = %s", (count, smt_id))

    pg_conn.commit()
    log.increment('bills_linked', linked)
    log.info(f"  Linked: {linked} bills to statements")

    # ─── Step 3: Individual bill payments ────────────────────
    log.info("\n[6.3] Migrating individual bill payments...")

    # 3a: One-shot payments (incomebill, NOT in statement, NOT in intermediates)
    my.execute("SELECT * FROM incomebill ORDER BY idx_Received_Date")
    all_incomebills = my.fetchall()

    oneshot_count = 0
    for pay in all_incomebills:
        bill_no = _clean(pay.get('idx_BillID'))
        if not bill_no:
            continue
        # Skip statement-linked bills
        if bill_no in statement_linked_bills:
            continue
        # Skip bills that have intermediate payments (use those instead)
        if bill_no in bills_with_intermediates:
            continue

        received_date = sanitize_timestamp(pay.get('idx_Received_Date'))
        if received_date is None:
            continue

        customer_id = None
        cust_name = _clean(pay.get('idx_CustomerName'))
        if cust_name:
            customer_id = customers.get(cust_name, fuzzy=True, threshold=0.9)
        if not customer_id:
            log.skip_record(f"payment bill={bill_no}", "no customer match")
            continue

        invoice_bill_id = bill_map.get(bill_no)
        pay_mode_raw = (pay.get('Payment_mode') or '').strip()
        payment_mode_id = payment_mode_map.get(pay_mode_raw, default_cash_id)

        amount = _to_decimal(pay.get('ReceivedAmount'))
        if amount <= 0:
            amount = _to_decimal(pay.get('NetAmount'))

        # Enrich with payment details
        ref_no, remarks = None, None
        if bill_no in cheque_details:
            ref_no, remarks = cheque_details[bill_no]
        elif bill_no in card_details:
            ref_no, remarks = card_details[bill_no]
        elif bill_no in ccms_details:
            ref_no, remarks = ccms_details[bill_no]
        elif bill_no in bank_details:
            ref_no, remarks = bank_details[bill_no]

        try:
            pg.execute("""
                INSERT INTO payment (scid, payment_date, amount, payment_mode_id,
                    customer_id, invoice_bill_id, reference_no, remarks, created_at, updated_at)
                VALUES (1, %s, %s, %s, %s, %s, %s, %s, %s, %s)
            """, (received_date, amount, payment_mode_id, customer_id,
                  invoice_bill_id, ref_no, remarks, ts, ts))
            oneshot_count += 1
        except Exception as e:
            pg.connection.rollback()
            log.error_record(f"payment bill={bill_no} type=oneshot", e)

        if oneshot_count % BATCH_SIZE == 0:
            pg_conn.commit()

    pg_conn.commit()
    log.increment('oneshot_payments', oneshot_count)
    log.info(f"  One-shot bill payments: {oneshot_count}")

    # 3b: Partial payments (intermediatebills, NOT in statement)
    my.execute("SELECT * FROM intermediatebills ORDER BY Received_Date")
    all_intermediates = my.fetchall()

    partial_count = 0
    for pay in all_intermediates:
        bill_no = _clean(pay.get('fkBillID'))
        if not bill_no:
            continue
        if bill_no in statement_linked_bills:
            continue

        received_date = sanitize_timestamp(pay.get('Received_Date'))
        if received_date is None:
            continue

        invoice_bill_id = bill_map.get(bill_no)
        if not invoice_bill_id:
            log.skip_record(f"partial_payment bill={bill_no}", "bill not found in PG")
            continue

        # Get customer from the bill
        pg.execute("SELECT customer_id FROM invoice_bill WHERE id = %s", (invoice_bill_id,))
        cust_row = pg.fetchone()
        customer_id = cust_row[0] if cust_row else None
        if not customer_id:
            log.skip_record(f"partial_payment bill={bill_no}", "bill has no customer")
            continue

        pay_mode_raw = (pay.get('Payment_mode') or '').strip()
        payment_mode_id = payment_mode_map.get(pay_mode_raw, default_cash_id)
        amount = _to_decimal(pay.get('Received_Amount'))

        try:
            pg.execute("""
                INSERT INTO payment (scid, payment_date, amount, payment_mode_id,
                    customer_id, invoice_bill_id, created_at, updated_at)
                VALUES (1, %s, %s, %s, %s, %s, %s, %s)
            """, (received_date, amount, payment_mode_id, customer_id,
                  invoice_bill_id, ts, ts))
            partial_count += 1
        except Exception as e:
            pg.connection.rollback()
            log.error_record(f"partial_payment bill={bill_no}", e)

        if partial_count % BATCH_SIZE == 0:
            pg_conn.commit()

    pg_conn.commit()
    log.increment('partial_payments', partial_count)
    log.info(f"  Partial bill payments: {partial_count}")

    # ─── Step 4: Statement payments ──────────────────────────
    log.info("\n[6.4] Migrating statement payments...")

    # 4a: Full statement payments (NOT in intermediate)
    my.execute("SELECT * FROM income_creditbill_statement ORDER BY idx_Recv_Date")
    all_smt_payments = my.fetchall()

    smt_full_count = 0
    for pay in all_smt_payments:
        smt_no = pay.get('idx_CreditBill_Statement')
        if smt_no in stmts_with_intermediates:
            continue  # Use intermediate rows instead

        pg_smt_id = smt_no_to_pg_id.get(smt_no)
        if not pg_smt_id:
            continue

        received_date = sanitize_timestamp(pay.get('idx_Recv_Date'))
        if received_date is None:
            continue

        # Get customer from statement
        pg.execute("SELECT customer_id FROM statement WHERE id = %s", (pg_smt_id,))
        cust_row = pg.fetchone()
        customer_id = cust_row[0] if cust_row else None
        if not customer_id:
            continue

        pay_type = (pay.get('Payment_Types') or '').strip()
        payment_mode_id = payment_mode_map.get(pay_type, default_cash_id)
        amount = _to_decimal(pay.get('idx_CreditBill_RecvAmount'))

        try:
            pg.execute("""
                INSERT INTO payment (scid, payment_date, amount, payment_mode_id,
                    customer_id, statement_id, created_at, updated_at)
                VALUES (1, %s, %s, %s, %s, %s, %s, %s)
            """, (received_date, amount, payment_mode_id, customer_id,
                  pg_smt_id, ts, ts))
            smt_full_count += 1
        except Exception as e:
            pg.connection.rollback()
            log.error_record(f"smt_payment smt={smt_no} type=full", e)

    pg_conn.commit()
    log.increment('smt_full_payments', smt_full_count)
    log.info(f"  Full statement payments: {smt_full_count}")

    # 4b: Partial statement payments
    my.execute("SELECT * FROM intermediate_creditbill_statement ORDER BY Intermediate_CreditBill_Statement_RecvDate")
    all_smt_partials = my.fetchall()

    smt_partial_count = 0
    for pay in all_smt_partials:
        smt_no = pay.get('idxIntermediate_CreditBill_Statement_No')
        pg_smt_id = smt_no_to_pg_id.get(smt_no)
        if not pg_smt_id:
            continue

        received_date = sanitize_timestamp(pay.get('Intermediate_CreditBill_Statement_RecvDate'))
        if received_date is None:
            continue

        pg.execute("SELECT customer_id FROM statement WHERE id = %s", (pg_smt_id,))
        cust_row = pg.fetchone()
        customer_id = cust_row[0] if cust_row else None
        if not customer_id:
            continue

        pay_type = (pay.get('Intermediate_CreditBill_Statement_Payment') or '').strip()
        payment_mode_id = payment_mode_map.get(pay_type, default_cash_id)
        amount = _to_decimal(pay.get('Intermediate_CreditBill_Statement_RecvAmt'))

        try:
            pg.execute("""
                INSERT INTO payment (scid, payment_date, amount, payment_mode_id,
                    customer_id, statement_id, created_at, updated_at)
                VALUES (1, %s, %s, %s, %s, %s, %s, %s)
            """, (received_date, amount, payment_mode_id, customer_id,
                  pg_smt_id, ts, ts))
            smt_partial_count += 1
        except Exception as e:
            pg.connection.rollback()
            log.error_record(f"smt_payment smt={smt_no} type=partial", e)

    pg_conn.commit()
    log.increment('smt_partial_payments', smt_partial_count)
    log.info(f"  Partial statement payments: {smt_partial_count}")

    # ─── Step 5: Derive statuses ─────────────────────────────
    log.info("\n[6.5] Updating statuses from payment data...")

    # Update individual bill payment_status
    pg.execute("""
        UPDATE invoice_bill ib SET payment_status = 'PAID'
        WHERE bill_type = 'CREDIT' AND statement_id IS NULL
        AND (SELECT COALESCE(SUM(p.amount), 0) FROM payment p WHERE p.invoice_bill_id = ib.id) >= ib.net_amount
    """)
    bills_paid_individual = pg.rowcount

    # Update statement received_amount, balance, status
    pg.execute("""
        UPDATE statement s SET
            received_amount = sub.total_received,
            balance_amount = s.net_amount - sub.total_received,
            status = CASE WHEN sub.total_received >= s.net_amount THEN 'PAID' ELSE 'NOT_PAID' END
        FROM (
            SELECT statement_id, COALESCE(SUM(amount), 0) as total_received
            FROM payment WHERE statement_id IS NOT NULL
            GROUP BY statement_id
        ) sub
        WHERE s.id = sub.statement_id
    """)
    stmts_updated = pg.rowcount

    # Bills linked to PAID statements → PAID
    pg.execute("""
        UPDATE invoice_bill SET payment_status = 'PAID'
        WHERE statement_id IN (SELECT id FROM statement WHERE status = 'PAID')
        AND payment_status != 'PAID'
    """)
    bills_paid_via_statement = pg.rowcount

    pg_conn.commit()

    log.increment('bills_paid_individual', bills_paid_individual)
    log.increment('stmts_status_updated', stmts_updated)
    log.increment('bills_paid_via_statement', bills_paid_via_statement)
    log.info(f"  Bills marked PAID (individual): {bills_paid_individual}")
    log.info(f"  Statements updated: {stmts_updated}")
    log.info(f"  Bills marked PAID (via statement): {bills_paid_via_statement}")

    # ─── Summary ─────────────────────────────────────────────
    total_payments = oneshot_count + partial_count + smt_full_count + smt_partial_count
    log.increment('total_payments', total_payments)
    customers.report_unmatched()

    log.summary()
    return lookups
