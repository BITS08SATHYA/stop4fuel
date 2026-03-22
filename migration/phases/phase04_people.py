"""Phase 4: Migrate employees and customers (JOINED inheritance: person_entity -> users -> employees/customer)."""
from decimal import Decimal
from utils.date_utils import now, sanitize_date
from utils.lookups import LookupCache, IDMapper
from mappings import DESIGNATION_MAP, PARTY_TYPE_MAP


def migrate(mysql_conn, pg_conn, lookups):
    """Migrate employees and customers from MySQL."""
    pg = pg_conn.cursor()
    my = mysql_conn.cursor(dictionary=True)
    ts = now()

    lookups['employees'] = LookupCache('Employee')
    lookups['customers'] = LookupCache('Customer')
    lookups['id_mapper'] = lookups.get('id_mapper', IDMapper())

    # --- Clear seed people data (employees/customers) if any ---
    print("\n  [4.0] Clearing seed people data...")
    # Check if there are seed employees/customers (from DataInitializer)
    pg.execute("SELECT COUNT(*) FROM employees")
    seed_emp_count = pg.fetchone()[0]
    pg.execute("SELECT COUNT(*) FROM customer")
    seed_cust_count = pg.fetchone()[0]

    if seed_emp_count > 0 or seed_cust_count > 0:
        # Delete in reverse FK order
        pg.execute("DELETE FROM employee_advances")
        pg.execute("DELETE FROM cash_advances")
        pg.execute("DELETE FROM attendance")
        pg.execute("DELETE FROM leave_balances")
        pg.execute("DELETE FROM leave_requests")
        pg.execute("DELETE FROM salary_history")
        pg.execute("DELETE FROM salary_payments")
        pg.execute("DELETE FROM incentive")
        pg.execute("DELETE FROM invoice_product")
        pg.execute("DELETE FROM payment")
        pg.execute("DELETE FROM invoice_bill")
        pg.execute("DELETE FROM statement")
        pg.execute("DELETE FROM vehicle")
        pg.execute("DELETE FROM customer_vehicle_mapper")
        pg.execute("DELETE FROM customer")
        pg.execute("DELETE FROM employees")
        pg.execute("DELETE FROM users")
        pg.execute("DELETE FROM person_phones")
        pg.execute("DELETE FROM person_emails")
        pg.execute("DELETE FROM person_entity")
        pg_conn.commit()
        print(f"    Cleared {seed_emp_count} seed employees, {seed_cust_count} seed customers")

    # --- 1. Migrate Employees ---
    print("\n  [4.1] Migrating employees...")
    my.execute("SELECT * FROM employee ORDER BY EmployeeID")
    employees = my.fetchall()

    emp_count = 0
    for emp in employees:
        name = f"{emp['Employee_Name'].strip()} {emp['EmployeeLastName'].strip()}".strip()
        if not name:
            continue

        # Determine designation
        mysql_desig = emp['Employee_Designation'].strip().upper()
        pg_desig_name = DESIGNATION_MAP.get(mysql_desig, emp['Employee_Designation'].strip().title())
        desig_id = lookups['designations'].get(pg_desig_name, fuzzy=True)

        # Role: map from designation
        role_type = 'EMPLOYEE'
        if pg_desig_name in ('Cashier',):
            role_type = 'CASHIER'
        elif pg_desig_name in ('Manager',):
            role_type = 'ADMIN'
        role_id = lookups['roles'].get(role_type)

        # Generate username
        username = f"emp_{emp['EmployeeID']}"

        # Status
        status = emp['EmployeeStatus'].strip().upper() if emp['EmployeeStatus'] else 'ACTIVE'
        if status not in ('ACTIVE', 'INACTIVE', 'TERMINATED'):
            status = 'ACTIVE'

        join_date = sanitize_date(emp['Employee_JoinDate'])
        dob = sanitize_date(emp['Employee_BirthDate'])
        address = emp['Employee_Address'].strip() if emp['Employee_Address'] else None

        # 1. Insert person_entity
        pg.execute("""
            INSERT INTO person_entity (scid, name, address, person_type, created_at, updated_at)
            VALUES (1, %s, %s, 'Individual', %s, %s)
            RETURNING id
        """, (name, address, ts, ts))
        person_id = pg.fetchone()[0]

        # 2. Insert users
        pg.execute("""
            INSERT INTO users (id, username, password, role_id, join_date, status)
            VALUES (%s, %s, %s, %s, %s, %s)
        """, (person_id, username, None, role_id, join_date, status))

        # 3. Insert employees
        salary = emp['Employee_Salary'] if emp['Employee_Salary'] else 0
        gender = emp['Employee_Gender'].strip().upper() if emp['Employee_Gender'] else None
        aadhar = emp['Employee_AdharCard'].strip() if emp['Employee_AdharCard'] else None
        # Clean aadhar - remove non-digits
        if aadhar:
            aadhar = ''.join(c for c in aadhar if c.isdigit())
            if len(aadhar) != 12:
                aadhar = None  # Invalid aadhar

        pg.execute("""
            INSERT INTO employees (id, designation_id, salary, aadhar_number,
                                   bank_name, bank_ifsc, bank_account_number,
                                   gender, date_of_birth, blood_group, employee_code)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        """, (
            person_id, desig_id, float(salary), aadhar,
            emp['Employee_BankName'].strip() if emp['Employee_BankName'] else None,
            emp['Employee_IFSC'].strip() if emp['Employee_IFSC'] else None,
            emp['Employee_BankAccNo'].strip() if emp['Employee_BankAccNo'] else None,
            gender, dob,
            emp.get('EmployeeBloodGrp', '').strip() or None,
            f"emp{emp['EmployeeID']:03d}"
        ))

        # 4. Insert phone number
        phone = emp['Employee_Phone'].strip() if emp['Employee_Phone'] else None
        if phone and phone != '0':
            pg.execute("INSERT INTO person_phones (person_id, phone_number) VALUES (%s, %s)",
                        (person_id, phone))

        # 5. Insert email
        email = emp.get('Employee_Email', '').strip() if emp.get('Employee_Email') else None
        if email and email != '0' and '@' in email:
            pg.execute("INSERT INTO person_emails (person_id, email) VALUES (%s, %s)",
                        (person_id, email))

        # Build lookups - use both full name and first name for matching
        lookups['employees'].add(name, person_id)
        lookups['employees'].add(emp['Employee_Name'].strip(), person_id)
        lookups['id_mapper'].add('employee', emp['EmployeeID'], person_id)
        emp_count += 1

    pg_conn.commit()
    print(f"    Migrated {emp_count} employees")

    # --- 2. Migrate Customers ---
    print("\n  [4.2] Migrating customers...")
    my.execute("SELECT * FROM customer_data ORDER BY Customer_ID")
    customers = my.fetchall()

    cust_count = 0
    for cust in customers:
        name = cust['Customer_Name'].strip()
        if not name:
            continue

        # Party type
        party_type_raw = (cust['Customer_PartyType'] or 'Local').strip()
        party_type = PARTY_TYPE_MAP.get(party_type_raw, PARTY_TYPE_MAP.get(party_type_raw.rstrip(), 'Local'))
        party_id = lookups['party'].get(party_type)

        # Group
        group_name = (cust.get('Customer_Group_Name') or '').strip()
        group_id = lookups['groups'].get(group_name, fuzzy=True) if group_name else None

        # Credit limit
        credit_limit = cust.get('Customer_CreditLimit', -1)
        if credit_limit and credit_limit > 0:
            credit_limit_amount = Decimal(str(credit_limit))
        else:
            credit_limit_amount = None

        # Username
        username = f"cust_{cust['Customer_ID']}"

        role_id = lookups['roles'].get('CUSTOMER')
        join_date = sanitize_date(cust.get('Customer_Join_Date'))
        address = (cust.get('Customer_Address') or '').strip() or None
        phone = (cust.get('Customer_Phone') or '').strip()

        # Determine person_type based on party type
        person_type = 'Company' if party_type == 'Statement' else 'Individual'

        # 1. Insert person_entity
        pg.execute("""
            INSERT INTO person_entity (scid, name, address, person_type, created_at, updated_at)
            VALUES (1, %s, %s, %s, %s, %s)
            RETURNING id
        """, (name, address, person_type, ts, ts))
        person_id = pg.fetchone()[0]

        # 2. Insert users
        pg.execute("""
            INSERT INTO users (id, username, role_id, join_date, status)
            VALUES (%s, %s, %s, %s, 'ACTIVE')
        """, (person_id, username, role_id, join_date))

        # 3. Insert customer
        pg.execute("""
            INSERT INTO customer (id, group_id, party_id, credit_limit_amount, consumed_liters)
            VALUES (%s, %s, %s, %s, 0)
        """, (person_id, group_id, party_id, credit_limit_amount))

        # 4. Phone
        if phone and phone not in ('null', '0', 'NULL', ''):
            pg.execute("INSERT INTO person_phones (person_id, phone_number) VALUES (%s, %s)",
                        (person_id, phone))

        # 5. Email
        email = (cust.get('Customer_Email') or '').strip()
        if email and email not in ('null', '0', 'NULL', '') and '@' in email:
            pg.execute("INSERT INTO person_emails (person_id, email) VALUES (%s, %s)",
                        (person_id, email))

        # Build lookups
        lookups['customers'].add(name, person_id)
        lookups['id_mapper'].add('customer_data', cust['Customer_ID'], person_id)
        cust_count += 1

    pg_conn.commit()
    print(f"    Migrated {cust_count} customers")

    # --- 3. Migrate customer employees (contacts) ---
    print("\n  [4.3] Migrating customer contacts...")
    my.execute("SELECT * FROM customer_employees ORDER BY CE_ID")
    contacts = my.fetchall()
    contact_count = 0
    for ce in contacts:
        cust_pg_id = lookups['id_mapper'].get('customer_data', ce['CID'])
        if not cust_pg_id:
            continue
        phone = (ce.get('CE_Phone') or '').strip()
        if phone and phone not in ('null', '0'):
            pg.execute("INSERT INTO person_phones (person_id, phone_number) VALUES (%s, %s)",
                        (cust_pg_id, phone))
            contact_count += 1

    pg_conn.commit()
    print(f"    Added {contact_count} customer contact phone numbers")

    my.close()
    pg.close()
    return lookups
