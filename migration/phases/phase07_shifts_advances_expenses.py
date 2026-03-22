"""Phase 7: Migrate shifts, advances, and expenses from MySQL to PostgreSQL.

Source tables:
  Shifts:
    - shift_closing_timings (2,598) → shifts
    - sft_time (1,708) → shifts (adds end_time from Shift_TimeStamp_Close)

  Shift Transactions (daily advance collections):
    - card_advances (57,170) → shift_transaction (CARD)
    - ccms_advance (8,577) → shift_transaction (CCMS)
    - cheque_advance (1,526) → shift_transaction (CHEQUE)
    - bank_transfer_advance (1,562) → shift_transaction (BANK)

  Cash Advances (money given out):
    - cash_advances (3,628) → cash_advances

  Employee Advances:
    - home_advances (3,141) → employee_advances (HOME_ADVANCE)

  Expenses:
    - expense (10,854) → shift_transaction (EXPENSE)
"""
import datetime
from decimal import Decimal
from utils.date_utils import sanitize_date, sanitize_timestamp, now
from utils.lookups import LookupCache
from utils.migration_logger import MigrationLogger

BATCH_SIZE = 1000


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


# Employee name aliases: MySQL name variants → canonical PG name
EMPLOYEE_ALIASES = {
    'JAYARAMAN': 'JAYARAMAN K',
    'JayaRamn K': 'JAYARAMAN K',
    'Manikanda P': 'Manikandan P',
    'Manikandan': 'Manikandan P',
    'MANIKANADAN V': 'MANIKANADAN V',  # different person (V vs P)
    'Rajendran': 'Rajendran M',
    'S. Sakthivel': 'SAKTHIVEL S SUBRAMANI',
    'Sakthivel S': 'SAKTHIVEL S SUBRAMANI',
    'Sathivel S': 'SAKTHIVEL S SUBRAMANI',
    'Sathya': 'Sathyaram B',
    'Sathyaram': 'Sathyaram B',
    'SAKTHYARAM': 'Sathyaram B',
    'Senthil': 'Senthil Nathan',
    'Suganya': 'Suganya Agalaya',
    'vijay': 'vijay G',
    'Vijay M': 'vijay G',
    'SANKAR': 'SANKAR K',
    'ANANDHA KUMAR M': 'ANANDHA KUMAR M',
    'KALPANA R': 'KALPANA R',
    'PRAVEEN': 'PRAVEEN V',
}


def _build_employee_lookup(pg_cursor):
    """Build employee name → PG ID lookup with alias support."""
    employees = LookupCache('Employee')
    pg_cursor.execute(
        "SELECT e.id, pe.name FROM employees e "
        "JOIN person_entity pe ON pe.id = e.id"
    )
    for row in pg_cursor.fetchall():
        if row[1]:
            employees.add(row[1], row[0])

    # Add aliases
    for alias, canonical in EMPLOYEE_ALIASES.items():
        pg_id = employees.get(canonical, fuzzy=False)
        if pg_id:
            employees.add(alias, pg_id)

    return employees


def _lookup_expense_type_misc(pg_cursor, log):
    """Find or create 'Miscellaneous' expense type — replaces hardcoded id=5."""
    pg_cursor.execute("SELECT id FROM expense_type WHERE LOWER(type_name) LIKE '%miscellaneous%' LIMIT 1")
    row = pg_cursor.fetchone()
    if row:
        return row[0]
    # Create it if missing
    pg_cursor.execute(
        "INSERT INTO expense_type (type_name, created_at, updated_at) VALUES ('Miscellaneous', NOW(), NOW()) RETURNING id"
    )
    result = pg_cursor.fetchone()
    log.warn("Created 'Miscellaneous' expense type (was missing)")
    return result[0]


def migrate(mysql_conn, pg_conn, lookups):
    log = MigrationLogger('phase07_shifts_advances')
    pg = pg_conn.cursor()
    my = mysql_conn.cursor(dictionary=True)
    ts = now()

    # ─── Load lookups ─────────────────────────────────────────
    log.info("[7.0] Loading lookups...")
    employees = _build_employee_lookup(pg)
    log.info(f"  Employees: {len(employees)} entries (with aliases)")

    # Expense type lookup
    expense_types = LookupCache('ExpenseType')
    pg.execute("SELECT id, type_name FROM expense_type")
    for row in pg.fetchall():
        if row[1]:
            expense_types.add(row[1], row[0])
    log.info(f"  Expense types: {len(expense_types)} entries")

    # Fallback expense type (dynamic, not hardcoded)
    misc_expense_type_id = _lookup_expense_type_misc(pg, log)

    # ─── Step 1: Migrate Shifts ───────────────────────────────
    log.info("\n[7.1] Migrating shifts...")

    # Get close times from sft_time (subset with open+close timestamps)
    my.execute("SELECT * FROM sft_time ORDER BY shiftDate")
    sft_time_rows = {row['shiftDate']: row for row in my.fetchall()}

    # Main shift data from shift_closing_timings
    my.execute("SELECT * FROM shift_closing_timings ORDER BY shiftDate")
    shifts = my.fetchall()

    date_to_shift_id = {}
    shift_count = 0

    for s in shifts:
        shift_date = sanitize_date(s.get('shiftDate'))
        if shift_date is None:
            continue

        cashier_name = _clean(s.get('SC_Cashier'))
        attendant_id = None
        if cashier_name:
            attendant_id = employees.get(cashier_name, fuzzy=True, threshold=0.85)

        start_time = sanitize_timestamp(s.get('Shift_TimeStamp'))
        if start_time is None:
            start_time = datetime.datetime.combine(shift_date, datetime.time(6, 0))
            log.warn(f"Shift {shift_date}: NULL start_time, defaulting to 06:00")

        # Get end_time from sft_time if available
        end_time = None
        sft = sft_time_rows.get(shift_date)
        if sft:
            end_time = sanitize_timestamp(sft.get('Shift_TimeStamp_Close'))

        try:
            pg.execute("""
                INSERT INTO shifts (scid, start_time, end_time, attendant_id, status, created_at, updated_at)
                VALUES (1, %s, %s, %s, 'CLOSED', %s, %s)
                RETURNING id
            """, (start_time, end_time, attendant_id, ts, ts))
            result = pg.fetchone()
            if result:
                date_to_shift_id[shift_date] = result[0]
                shift_count += 1
        except Exception as e:
            pg.connection.rollback()
            log.error_record(f"shift date={shift_date}", e)

    pg_conn.commit()
    log.increment('shifts', shift_count)
    log.info(f"  Inserted: {shift_count} shifts ({len(sft_time_rows)} with end_time)")

    # Helper to find shift_id by date
    def _find_shift_id(dt):
        if dt is None:
            return None
        if isinstance(dt, datetime.datetime):
            d = dt.date()
        elif isinstance(dt, datetime.date):
            d = dt
        else:
            return None
        return date_to_shift_id.get(d)

    # ─── Step 2: Card Advances → CARD transactions ────────────
    log.info("\n[7.2] Migrating card advances (→ CARD transactions)...")
    my.execute("SELECT * FROM card_advances ORDER BY Crd_Adv_Date")
    card_rows = my.fetchall()
    card_count = 0

    for row in card_rows:
        txn_date = sanitize_timestamp(row.get('Crd_Adv_Date'))
        if txn_date is None:
            continue

        amount = _to_decimal(row.get('Crd_Adv_Amount'))
        if amount <= 0:
            continue

        shift_id = _find_shift_id(txn_date)
        batch_id = _clean(str(row.get('Crd_Adv_BatchID', '')))
        tid = _clean(str(row.get('Crd_Adv_TID', '')))
        cust_name = _clean(row.get('Crd_Adv_CustomerName'))
        cust_phone = _clean(row.get('Crd_Adv_CustomerPhoneNo'))
        bank_name = _clean(row.get('CRD_Adv_BankName'))
        card_last4 = _clean(row.get('Crd_Adv_CardLast4Digit'))

        try:
            pg.execute("""
                INSERT INTO shift_transaction (
                    scid, shift_id, txn_type, transaction_date, received_amount,
                    batch_id, tid, customer_name, customer_phone, bank_name, card_last4_digit,
                    created_at, updated_at
                ) VALUES (1, %s, 'CARD', %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
            """, (shift_id, txn_date, amount, batch_id, tid,
                  cust_name, cust_phone, bank_name, card_last4, ts, ts))
            card_count += 1
        except Exception as e:
            pg.connection.rollback()
            log.error_record(f"card_txn date={txn_date}", e)

        if card_count % BATCH_SIZE == 0:
            pg_conn.commit()

    pg_conn.commit()
    log.increment('card_txn', card_count)
    log.info(f"  Inserted: {card_count} card transactions")

    # ─── Step 3: CCMS Advances → CCMS transactions ───────────
    log.info("\n[7.3] Migrating CCMS advances (→ CCMS transactions)...")
    my.execute("SELECT * FROM ccms_advance ORDER BY ccms_Date")
    ccms_rows = my.fetchall()
    ccms_count = 0

    for row in ccms_rows:
        txn_date = sanitize_timestamp(row.get('ccms_Date'))
        if txn_date is None:
            continue

        amount = _to_decimal(row.get('ccms_cardamt'))
        if amount <= 0:
            continue

        shift_id = _find_shift_id(txn_date)
        ccms_number = _clean(row.get('ccms_cardno'))
        cust_name = _clean(row.get('ccms_customer'))

        try:
            pg.execute("""
                INSERT INTO shift_transaction (
                    scid, shift_id, txn_type, transaction_date, received_amount,
                    ccms_number, customer_name, created_at, updated_at
                ) VALUES (1, %s, 'CCMS', %s, %s, %s, %s, %s, %s)
            """, (shift_id, txn_date, amount, ccms_number, cust_name, ts, ts))
            ccms_count += 1
        except Exception as e:
            pg.connection.rollback()
            log.error_record(f"ccms_txn date={txn_date}", e)

        if ccms_count % BATCH_SIZE == 0:
            pg_conn.commit()

    pg_conn.commit()
    log.increment('ccms_txn', ccms_count)
    log.info(f"  Inserted: {ccms_count} CCMS transactions")

    # ─── Step 4: Cheque Advances → CHEQUE transactions ────────
    log.info("\n[7.4] Migrating cheque advances (→ CHEQUE transactions)...")
    my.execute("SELECT * FROM cheque_advance ORDER BY cheque_Date_today")
    cheque_rows = my.fetchall()
    cheque_count = 0

    for row in cheque_rows:
        txn_date = sanitize_timestamp(row.get('cheque_Date_today'))
        if txn_date is None:
            continue

        amount = _to_decimal(row.get('cheque_Amt'))
        if amount <= 0:
            continue

        shift_id = _find_shift_id(txn_date)
        bank_name = _clean(row.get('cheque_bank'))
        in_favor_of = _clean(row.get('cheque_infavourof'))
        cheque_no = _clean(row.get('cheque_no'))
        cheque_date = sanitize_date(row.get('cheque_Date'))
        cust_name = _clean(row.get('cheque_cusname'))

        try:
            pg.execute("""
                INSERT INTO shift_transaction (
                    scid, shift_id, txn_type, transaction_date, received_amount,
                    bank_name, in_favor_of, cheque_no, cheque_date, customer_name,
                    created_at, updated_at
                ) VALUES (1, %s, 'CHEQUE', %s, %s, %s, %s, %s, %s, %s, %s, %s)
            """, (shift_id, txn_date, amount, bank_name, in_favor_of,
                  cheque_no, cheque_date, cust_name, ts, ts))
            cheque_count += 1
        except Exception as e:
            pg.connection.rollback()
            log.error_record(f"cheque_txn date={txn_date}", e)

        if cheque_count % BATCH_SIZE == 0:
            pg_conn.commit()

    pg_conn.commit()
    log.increment('cheque_txn', cheque_count)
    log.info(f"  Inserted: {cheque_count} cheque transactions")

    # ─── Step 5: Bank Transfer Advances → BANK transactions ───
    log.info("\n[7.5] Migrating bank transfer advances (→ BANK transactions)...")
    my.execute("SELECT * FROM bank_transfer_advance ORDER BY Date_today")
    bank_rows = my.fetchall()
    bank_count = 0

    for row in bank_rows:
        txn_date = sanitize_timestamp(row.get('Date_today'))
        if txn_date is None:
            continue

        amount = _to_decimal(row.get('Received_Amt'))
        if amount <= 0:
            continue

        shift_id = _find_shift_id(txn_date)
        bank_name = _clean(row.get('Bank_Name'))

        try:
            pg.execute("""
                INSERT INTO shift_transaction (
                    scid, shift_id, txn_type, transaction_date, received_amount,
                    bank_name, created_at, updated_at
                ) VALUES (1, %s, 'BANK', %s, %s, %s, %s, %s)
            """, (shift_id, txn_date, amount, bank_name, ts, ts))
            bank_count += 1
        except Exception as e:
            pg.connection.rollback()
            log.error_record(f"bank_txn date={txn_date}", e)

        if bank_count % BATCH_SIZE == 0:
            pg_conn.commit()

    pg_conn.commit()
    log.increment('bank_txn', bank_count)
    log.info(f"  Inserted: {bank_count} bank transactions")

    # ─── Step 6: Cash Advances → cash_advances ────────────────
    log.info("\n[7.6] Migrating cash advances...")
    my.execute("SELECT * FROM cash_advances ORDER BY CashAdv_Date")
    cash_adv_rows = my.fetchall()
    cash_adv_count = 0

    for row in cash_adv_rows:
        adv_date = sanitize_timestamp(row.get('CashAdv_Date'))
        if adv_date is None:
            continue

        amount = _to_decimal(row.get('CashAdv_Amount'))
        if amount <= 0:
            continue

        emp_name = _clean(row.get('CashAdv_Employee'))
        employee_id = None
        if emp_name:
            employee_id = employees.get(emp_name, fuzzy=True, threshold=0.85)

        shift_id = _find_shift_id(adv_date)
        recipient = _clean(row.get('CashAdv_Received_By'))
        purpose = _clean(row.get('CashAdv_Desc'))

        try:
            pg.execute("""
                INSERT INTO cash_advances (
                    scid, shift_id, advance_date, amount, advance_type,
                    employee_id, recipient_name, purpose, status,
                    created_at, updated_at
                ) VALUES (1, %s, %s, %s, 'CASH_ADVANCE', %s, %s, %s, 'GIVEN', %s, %s)
            """, (shift_id, adv_date, amount, employee_id, recipient, purpose, ts, ts))
            cash_adv_count += 1
        except Exception as e:
            pg.connection.rollback()
            log.error_record(f"cash_advance date={adv_date} emp={emp_name}", e)

        if cash_adv_count % BATCH_SIZE == 0:
            pg_conn.commit()

    pg_conn.commit()
    log.increment('cash_advances', cash_adv_count)
    log.info(f"  Inserted: {cash_adv_count} cash advances")

    # ─── Step 7: Home Advances → employee_advances ────────────
    log.info("\n[7.7] Migrating home advances (→ employee_advances)...")
    my.execute("SELECT * FROM home_advances ORDER BY HA_Date")
    home_rows = my.fetchall()
    home_count = 0
    home_skipped = 0

    for row in home_rows:
        adv_date = sanitize_date(row.get('HA_Date'))
        if adv_date is None:
            continue

        amount = _to_decimal(row.get('HA_Amount'))
        if amount <= 0:
            continue

        emp_name = _clean(row.get('HA_Employee'))
        employee_id = None
        if emp_name:
            employee_id = employees.get(emp_name, fuzzy=True, threshold=0.85)

        if not employee_id:
            home_skipped += 1
            log.skip_record(f"home_advance date={adv_date} emp={emp_name}", "no employee match")
            continue

        try:
            pg.execute("""
                INSERT INTO employee_advances (
                    employee_id, amount, advance_date, advance_type, status,
                    created_at, updated_at
                ) VALUES (%s, %s, %s, 'HOME_ADVANCE', 'DEDUCTED', %s, %s)
            """, (employee_id, float(amount), adv_date, ts, ts))
            home_count += 1
        except Exception as e:
            pg.connection.rollback()
            log.error_record(f"home_advance date={adv_date} emp={emp_name}", e)

        if home_count % BATCH_SIZE == 0:
            pg_conn.commit()

    pg_conn.commit()
    log.increment('home_advances', home_count)
    log.increment('home_advances_skipped', home_skipped)
    log.info(f"  Inserted: {home_count} home advances ({home_skipped} skipped - no employee match)")

    # ─── Step 8: Expenses → EXPENSE transactions ──────────────
    log.info("\n[7.8] Migrating expenses (→ EXPENSE transactions)...")

    my.execute("SELECT * FROM expense ORDER BY Exp_Date")
    expense_rows = my.fetchall()
    expense_count = 0
    new_expense_types = 0

    for row in expense_rows:
        txn_date = sanitize_timestamp(row.get('Exp_Date'))
        if txn_date is None:
            continue

        amount = _to_decimal(row.get('Exp_Amount'))
        if amount <= 0:
            continue

        shift_id = _find_shift_id(txn_date)
        exp_type_raw = _clean(row.get('Exp_Type'))
        description = _clean(row.get('Exp_Description')) or exp_type_raw

        # Try to match expense type
        expense_type_id = None
        if exp_type_raw:
            expense_type_id = expense_types.get(exp_type_raw, fuzzy=True, threshold=0.85)
            if expense_type_id is None:
                # Create new expense type
                try:
                    pg.execute(
                        "INSERT INTO expense_type (type_name, created_at, updated_at) VALUES (%s, %s, %s) RETURNING id",
                        (exp_type_raw, ts, ts)
                    )
                    result = pg.fetchone()
                    if result:
                        expense_type_id = result[0]
                        expense_types.add(exp_type_raw, expense_type_id)
                        new_expense_types += 1
                except Exception:
                    pg.connection.rollback()
                    expense_type_id = misc_expense_type_id  # Dynamic fallback
                    log.warn(f"Expense type creation failed for '{exp_type_raw}', using Miscellaneous")

        try:
            pg.execute("""
                INSERT INTO shift_transaction (
                    scid, shift_id, txn_type, transaction_date,
                    expense_amount, expense_description, expense_type_id,
                    created_at, updated_at
                ) VALUES (1, %s, 'EXPENSE', %s, %s, %s, %s, %s, %s)
            """, (shift_id, txn_date, amount, description, expense_type_id, ts, ts))
            expense_count += 1
        except Exception as e:
            pg.connection.rollback()
            log.error_record(f"expense date={txn_date} type={exp_type_raw}", e)

        if expense_count % BATCH_SIZE == 0:
            pg_conn.commit()

    pg_conn.commit()
    log.increment('expenses', expense_count)
    log.info(f"  Inserted: {expense_count} expense transactions ({new_expense_types} new expense types created)")

    # ─── Summary ──────────────────────────────────────────────
    total_txn = card_count + ccms_count + cheque_count + bank_count + expense_count
    log.increment('total_shift_txn', total_txn)
    employees.report_unmatched()

    log.summary()
    return lookups
