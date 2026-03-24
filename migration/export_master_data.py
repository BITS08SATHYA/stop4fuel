#!/usr/bin/env python3
"""
Export master data from MySQL (bunkdb) into PostgreSQL-compatible SQL files.

Connects to MySQL, reads and transforms data, and writes SQL INSERT files
that can be directly imported into the PostgreSQL stopforfuel database.

Usage:
    python export_master_data.py [--output-dir ./output]

Output files (in execution order):
    01_customer_groups.sql
    02_vehicle_types.sql
    03_designations.sql
    04_expense_types.sql
    05_company.sql
    06_products.sql
    07_employees.sql
    08_customers.sql
    09_vehicles.sql
"""
import os
import sys
import argparse
import datetime
from decimal import Decimal

import mysql.connector

from config import MYSQL_CONFIG
from mappings import (
    OIL_TABLE_TO_PRODUCT, FUEL_PRODUCT_MAP, DESIGNATION_MAP,
    VEHICLE_TYPE_MAP, PARTY_TYPE_MAP, MYSQL_PRODUCT_NAME_TO_TABLE,
)


# ============================================================
# Utilities
# ============================================================

def sql_str(val):
    """Escape a value for SQL. Returns 'NULL' for None."""
    if val is None:
        return 'NULL'
    if isinstance(val, bool):
        return 'true' if val else 'false'
    if isinstance(val, (int, float, Decimal)):
        return str(val)
    if isinstance(val, (datetime.date, datetime.datetime)):
        return f"'{val}'"
    # String: escape single quotes
    s = str(val).replace("'", "''")
    return f"'{s}'"


def sanitize_date(val):
    """Convert MySQL 0000-00-00 and invalid dates to None."""
    if val is None:
        return None
    s = str(val)
    if s.startswith('0000') or s.strip() == '':
        return None
    if isinstance(val, (datetime.date, datetime.datetime)):
        if val.year < 1970:
            return None
        return val
    return None


def clean_str(val):
    """Strip and return string, or None if empty/null."""
    if val is None:
        return None
    s = str(val).strip()
    if s in ('', 'null', 'NULL', '0'):
        return None
    return s


NOW = datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')


# ============================================================
# Export functions
# ============================================================

def export_customer_groups(cursor, outdir):
    """Export customer groups from MySQL customergroups table."""
    print("\n[1] Exporting customer groups...")
    cursor.execute("SELECT GroupID, GroupName FROM customergroups ORDER BY GroupID")
    rows = cursor.fetchall()

    path = os.path.join(outdir, '01_customer_groups.sql')
    with open(path, 'w') as f:
        f.write("-- Customer Groups from MySQL customergroups table\n")
        f.write("-- Existing seed groups (Default, Transport, Government, Corporate) will be kept\n\n")

        count = 0
        for row in rows:
            name = clean_str(row['GroupName'])
            if not name:
                continue
            f.write(f"INSERT INTO customer_group (group_name) VALUES ({sql_str(name)}) ON CONFLICT DO NOTHING;\n")
            count += 1

        f.write(f"\n-- Total: {count} groups\n")

    print(f"  -> {path} ({count} groups)")
    return count


def export_vehicle_types(cursor, outdir):
    """Export vehicle types from MySQL vehicle_type table."""
    print("\n[2] Exporting vehicle types...")
    cursor.execute("SELECT VehicleType_ID, VehicleType FROM vehicle_type ORDER BY VehicleType_ID")
    rows = cursor.fetchall()

    # Collect unique PG names
    seen = set()
    mapped = []
    for row in rows:
        mysql_name = clean_str(row['VehicleType'])
        if not mysql_name or mysql_name == 'Select':
            continue
        pg_name = VEHICLE_TYPE_MAP.get(mysql_name, mysql_name)
        if pg_name not in seen:
            seen.add(pg_name)
            mapped.append((pg_name, mysql_name))

    path = os.path.join(outdir, '02_vehicle_types.sql')
    with open(path, 'w') as f:
        f.write("-- Vehicle Types from MySQL vehicle_type table\n")
        f.write("-- Existing seed types (Car, Bus, Truck, Jeep, Bike) will be kept\n\n")

        for pg_name, mysql_name in mapped:
            desc = f"Migrated from: {mysql_name}" if pg_name != mysql_name else None
            f.write(f"INSERT INTO vehicle_type (type_name, description, created_at, updated_at) "
                    f"VALUES ({sql_str(pg_name)}, {sql_str(desc)}, '{NOW}', '{NOW}') "
                    f"ON CONFLICT DO NOTHING;\n")

        f.write(f"\n-- Total: {len(mapped)} vehicle types\n")

    print(f"  -> {path} ({len(mapped)} types)")
    return mapped


def export_designations(cursor, outdir):
    """Export unique designations from MySQL employee table."""
    print("\n[3] Exporting designations...")
    cursor.execute("SELECT DISTINCT Employee_Designation FROM employee WHERE Employee_Designation != '' ORDER BY Employee_Designation")
    rows = cursor.fetchall()

    seen = set()
    mapped = []
    for row in rows:
        mysql_desig = clean_str(row['Employee_Designation'])
        if not mysql_desig:
            continue
        pg_name = DESIGNATION_MAP.get(mysql_desig.upper(), mysql_desig.title())
        if pg_name not in seen:
            seen.add(pg_name)
            mapped.append((pg_name, mysql_desig))

    path = os.path.join(outdir, '03_designations.sql')
    with open(path, 'w') as f:
        f.write("-- Designations extracted from MySQL employee table\n")
        f.write("-- Existing seed designations (Manager, Cashier, Pump Attendant, Attendant, Supervisor) will be kept\n\n")

        for pg_name, mysql_name in mapped:
            f.write(f"INSERT INTO designations (name, description) "
                    f"VALUES ({sql_str(pg_name)}, {sql_str(f'From: {mysql_name}')}) "
                    f"ON CONFLICT (name) DO NOTHING;\n")

        f.write(f"\n-- Total: {len(mapped)} designations\n")

    print(f"  -> {path} ({len(mapped)} designations)")
    return mapped


def export_expense_types(cursor, outdir):
    """Export expense types from MySQL expensetype table."""
    print("\n[4] Exporting expense types...")
    cursor.execute("SELECT ExpenseTypeName FROM expensetype ORDER BY ExpenseTypeName")
    rows = cursor.fetchall()

    path = os.path.join(outdir, '04_expense_types.sql')
    with open(path, 'w') as f:
        f.write("-- Expense Types from MySQL expensetype table\n\n")
        count = 0
        for row in rows:
            name = clean_str(row['ExpenseTypeName'])
            if not name:
                continue
            f.write(f"INSERT INTO expense_type (type_name, created_at, updated_at) "
                    f"VALUES ({sql_str(name)}, '{NOW}', '{NOW}') ON CONFLICT DO NOTHING;\n")
            count += 1

        f.write(f"\n-- Total: {count} expense types\n")

    print(f"  -> {path} ({count} types)")
    return count


def export_company(cursor, outdir):
    """Export company profile from MySQL companyprofile table."""
    print("\n[5] Exporting company profile...")
    cursor.execute("SELECT * FROM companyprofile LIMIT 1")
    comp = cursor.fetchone()

    path = os.path.join(outdir, '05_company.sql')
    with open(path, 'w') as f:
        f.write("-- Company profile from MySQL companyprofile table\n")
        f.write("-- Updates the existing seed company record\n\n")

        if comp:
            open_date = sanitize_date(comp['opened_Date'])
            f.write(f"UPDATE company SET\n")
            f.write(f"  name = {sql_str(comp['name'])},\n")
            f.write(f"  open_date = {sql_str(open_date)},\n")
            f.write(f"  sap_code = {sql_str(str(comp['sap_code']))},\n")
            f.write(f"  gst_no = {sql_str(comp['gst_No'])},\n")
            f.write(f"  type = {sql_str(comp['type'])},\n")
            f.write(f"  address = {sql_str(comp['bunk_Address'])},\n")
            f.write(f"  updated_at = '{NOW}'\n")
            f.write(f"WHERE id = (SELECT id FROM company LIMIT 1);\n")

    print(f"  -> {path}")


def export_products(cursor, outdir):
    """Export products: update fuel prices + insert oil/lubricant products."""
    print("\n[6] Exporting products...")

    # Get fuel prices from MySQL
    cursor.execute("SELECT * FROM gst_oil_prices ORDER BY Sno")
    gst_prices = {row['idx_Oil_Name'].strip().upper(): row for row in cursor.fetchall()}

    path = os.path.join(outdir, '06_products.sql')
    with open(path, 'w') as f:
        f.write("-- Products from MySQL gst_oil_prices + product tables\n\n")

        # --- Oil types ---
        f.write("-- Oil Types\n")
        oil_types_needed = set()
        for _, info in OIL_TABLE_TO_PRODUCT.items():
            if info[3]:
                oil_types_needed.add(info[3])
        for ot in sorted(oil_types_needed):
            f.write(f"INSERT INTO oil_type (name, active, created_at, updated_at) "
                    f"VALUES ({sql_str(ot)}, true, '{NOW}', '{NOW}') ON CONFLICT (name) DO NOTHING;\n")

        # --- Grade types ---
        f.write("\n-- Grade Types\n")
        grades_needed = set()
        for _, info in OIL_TABLE_TO_PRODUCT.items():
            if info[4]:
                grades_needed.add(info[4])
        for gr in sorted(grades_needed):
            f.write(f"INSERT INTO grade_type (name, scid, active, created_at, updated_at) "
                    f"VALUES ({sql_str(gr)}, 1, true, '{NOW}', '{NOW}') ON CONFLICT (name) DO NOTHING;\n")

        # --- Update fuel prices ---
        f.write("\n-- Update fuel product prices from MySQL\n")
        if 'PETROL' in gst_prices:
            f.write(f"UPDATE product SET price = {gst_prices['PETROL']['Total_Amount']}, updated_at = '{NOW}' WHERE name = 'Petrol';\n")
        if 'XTRA_PREMIUM' in gst_prices:
            f.write(f"UPDATE product SET price = {gst_prices['XTRA_PREMIUM']['Total_Amount']}, updated_at = '{NOW}' WHERE name = 'Xtra Premium';\n")
        if 'DIESEL' in gst_prices:
            f.write(f"UPDATE product SET price = {gst_prices['DIESEL']['Total_Amount']}, updated_at = '{NOW}' WHERE name = 'Diesel';\n")

        # --- Delete seed oil products (if no FK references) ---
        f.write("\n-- Remove seed oil/accessory products (only if no FK references exist)\n")
        f.write("DELETE FROM product WHERE id > 3\n")
        f.write("  AND id NOT IN (SELECT DISTINCT product_id FROM invoice_product WHERE product_id IS NOT NULL)\n")
        f.write("  AND id NOT IN (SELECT DISTINCT product_id FROM tank WHERE product_id IS NOT NULL)\n")
        f.write("  AND id NOT IN (SELECT DISTINCT product_id FROM product_inventory WHERE product_id IS NOT NULL)\n")
        f.write("  AND id NOT IN (SELECT DISTINCT product_id FROM godown_stock WHERE product_id IS NOT NULL)\n")
        f.write("  AND id NOT IN (SELECT DISTINCT product_id FROM cashier_stock WHERE product_id IS NOT NULL)\n")
        f.write("  AND id NOT IN (SELECT DISTINCT product_id FROM vehicle WHERE product_id IS NOT NULL);\n")

        # --- Insert oil/lubricant/consumable products ---
        f.write("\n-- Oil/Lubricant/Consumable products\n")
        seen = set()
        count = 0
        for table_name, info in sorted(OIL_TABLE_TO_PRODUCT.items()):
            prod_name, hsn, category, oil_type, grade, brand, volume, unit = info
            if prod_name in seen:
                continue
            seen.add(prod_name)

            # Get price from gst_oil_prices
            gst_key = table_name.upper()
            price = gst_prices.get(gst_key, {}).get('Total_Amount')

            oil_type_ref = f"(SELECT id FROM oil_type WHERE name = {sql_str(oil_type)})" if oil_type else 'NULL'
            grade_ref = f"(SELECT id FROM grade_type WHERE name = {sql_str(grade)})" if grade else 'NULL'
            supplier_ref = 'NULL'
            if brand in ('Servo', 'IOCL'):
                supplier_ref = "(SELECT id FROM supplier WHERE name LIKE '%Indian Oil%' LIMIT 1)"
            elif brand == 'Castrol':
                supplier_ref = "(SELECT id FROM supplier WHERE name LIKE '%Castrol%' LIMIT 1)"

            f.write(f"INSERT INTO product (scid, name, hsn_code, price, category, unit, volume, brand, "
                    f"supplier_id, oil_type_id, grade_id, active, created_at, updated_at) VALUES "
                    f"(1, {sql_str(prod_name)}, {sql_str(hsn if hsn != '0' else None)}, "
                    f"{sql_str(price)}, {sql_str(category)}, {sql_str(unit)}, {sql_str(volume)}, {sql_str(brand)}, "
                    f"{supplier_ref}, {oil_type_ref}, {grade_ref}, true, '{NOW}', '{NOW}');\n")
            count += 1

        f.write(f"\n-- Total: {count} oil/lubricant/consumable products\n")

    print(f"  -> {path} ({count} products)")
    return count


def export_employees(cursor, outdir):
    """Export employees with JOINED inheritance (person_entity -> users -> employees)."""
    print("\n[7] Exporting employees...")
    cursor.execute("SELECT * FROM employee ORDER BY EmployeeID")
    employees = cursor.fetchall()

    path = os.path.join(outdir, '07_employees.sql')
    with open(path, 'w') as f:
        f.write("-- Employees from MySQL employee table\n")
        f.write("-- JOINED inheritance: person_entity -> users -> employees\n")
        f.write("-- IMPORTANT: Run AFTER 01-06 scripts. Clears all seed people first.\n\n")

        # Clear seed data
        f.write("-- Clear seed people data (cascade will handle FKs)\n")
        f.write("DELETE FROM employee_advances;\n")
        f.write("DELETE FROM cash_advances;\n")
        f.write("DELETE FROM attendance;\n")
        f.write("DELETE FROM leave_balances;\n")
        f.write("DELETE FROM leave_requests;\n")
        f.write("DELETE FROM salary_history;\n")
        f.write("DELETE FROM salary_payments;\n")
        f.write("DELETE FROM incentive;\n")
        f.write("DELETE FROM invoice_product;\n")
        f.write("DELETE FROM payment;\n")
        f.write("DELETE FROM invoice_bill;\n")
        f.write("DELETE FROM statement;\n")
        f.write("DELETE FROM vehicle;\n")
        f.write("DELETE FROM customer_vehicle_mapper;\n")
        f.write("DELETE FROM customer;\n")
        f.write("DELETE FROM employees;\n")
        f.write("DELETE FROM users;\n")
        f.write("DELETE FROM person_phones;\n")
        f.write("DELETE FROM person_emails;\n")
        f.write("DELETE FROM person_entity;\n\n")

        count = 0
        for emp in employees:
            first_name = clean_str(emp['Employee_Name']) or ''
            last_name = clean_str(emp['EmployeeLastName']) or ''
            name = f"{first_name} {last_name}".strip()
            if not name:
                continue

            mysql_desig = (emp['Employee_Designation'] or '').strip().upper()
            pg_desig = DESIGNATION_MAP.get(mysql_desig, (emp['Employee_Designation'] or '').strip().title())

            # Role
            role_type = 'EMPLOYEE'
            if pg_desig in ('Cashier',):
                role_type = 'CASHIER'
            elif pg_desig in ('Manager',):
                role_type = 'ADMIN'

            username = f"emp_{emp['EmployeeID']}"
            status = (emp.get('EmployeeStatus') or 'ACTIVE').strip().upper()
            if status not in ('ACTIVE', 'INACTIVE', 'TERMINATED'):
                status = 'ACTIVE'

            join_date = sanitize_date(emp['Employee_JoinDate'])
            dob = sanitize_date(emp['Employee_BirthDate'])
            address = clean_str(emp['Employee_Address'])
            phone = clean_str(emp['Employee_Phone'])
            gender = (emp.get('Employee_Gender') or '').strip().upper() or None
            aadhar = clean_str(emp.get('Employee_AdharCard'))
            if aadhar:
                aadhar = ''.join(c for c in aadhar if c.isdigit())
                if len(aadhar) != 12:
                    aadhar = None
            blood = clean_str(emp.get('EmployeeBloodGrp'))
            salary = emp.get('Employee_Salary') or 0
            bank_name = clean_str(emp.get('Employee_BankName'))
            bank_ifsc = clean_str(emp.get('Employee_IFSC'))
            bank_acc = clean_str(emp.get('Employee_BankAccNo'))
            emp_code = f"emp{emp['EmployeeID']:03d}"

            f.write(f"\n-- Employee: {name} (MySQL ID: {emp['EmployeeID']})\n")
            f.write("DO $$ DECLARE v_id BIGINT; BEGIN\n")

            # 1. person_entity
            f.write(f"  INSERT INTO person_entity (scid, name, address, person_type, created_at, updated_at)\n")
            f.write(f"  VALUES (1, {sql_str(name)}, {sql_str(address)}, 'Individual', '{NOW}', '{NOW}')\n")
            f.write(f"  RETURNING id INTO v_id;\n\n")

            # 2. users
            f.write(f"  INSERT INTO users (id, username, role_id, join_date, status)\n")
            f.write(f"  VALUES (v_id, {sql_str(username)}, "
                    f"(SELECT id FROM roles WHERE role_type = {sql_str(role_type)}), "
                    f"{sql_str(join_date)}, {sql_str(status)});\n\n")

            # 3. employees
            f.write(f"  INSERT INTO employees (id, designation_id, salary, aadhar_number, "
                    f"bank_name, bank_ifsc, bank_account_number, gender, date_of_birth, blood_group, employee_code)\n")
            f.write(f"  VALUES (v_id, "
                    f"(SELECT id FROM designations WHERE name = {sql_str(pg_desig)} LIMIT 1), "
                    f"{salary}, {sql_str(aadhar)}, "
                    f"{sql_str(bank_name)}, {sql_str(bank_ifsc)}, {sql_str(bank_acc)}, "
                    f"{sql_str(gender)}, {sql_str(dob)}, {sql_str(blood)}, {sql_str(emp_code)});\n\n")

            # 4. phone
            if phone:
                f.write(f"  INSERT INTO person_phones (person_id, phone_number) VALUES (v_id, {sql_str(phone)});\n")

            f.write("END $$;\n")
            count += 1

        f.write(f"\n-- Total: {count} employees\n")

    print(f"  -> {path} ({count} employees)")
    return count


def export_customers(cursor, outdir):
    """Export customers with JOINED inheritance (person_entity -> users -> customer)."""
    print("\n[8] Exporting customers...")
    cursor.execute("SELECT * FROM customer_data ORDER BY Customer_ID")
    customers = cursor.fetchall()

    # Also get customer contacts
    cursor.execute("SELECT * FROM customer_employees ORDER BY CE_ID")
    contacts = cursor.fetchall()
    # Group contacts by CID
    contact_map = {}
    for ce in contacts:
        cid = ce['CID']
        if cid not in contact_map:
            contact_map[cid] = []
        contact_map[cid].append(ce)

    path = os.path.join(outdir, '08_customers.sql')
    with open(path, 'w') as f:
        f.write("-- Customers from MySQL customer_data table\n")
        f.write("-- JOINED inheritance: person_entity -> users -> customer\n")
        f.write("-- IMPORTANT: Run AFTER 07_employees.sql\n\n")

        count = 0
        for cust in customers:
            name = clean_str(cust['Customer_Name'])
            if not name:
                continue

            party_raw = (cust.get('Customer_PartyType') or 'Local').strip()
            party_type = PARTY_TYPE_MAP.get(party_raw, PARTY_TYPE_MAP.get(party_raw.rstrip(), 'Local'))

            group_name = clean_str(cust.get('Customer_Group_Name'))
            credit_limit = cust.get('Customer_CreditLimit', -1)
            credit_limit_val = credit_limit if credit_limit and credit_limit > 0 else 'NULL'

            username = f"cust_{cust['Customer_ID']}"
            join_date = sanitize_date(cust.get('Customer_Join_Date'))
            address = clean_str(cust.get('Customer_Address'))
            phone = clean_str(cust.get('Customer_Phone'))
            email = clean_str(cust.get('Customer_Email'))
            person_type = 'Company' if party_type == 'Statement' else 'Individual'

            cid = cust['Customer_ID']

            f.write(f"\n-- Customer: {name} (MySQL CID: {cid}, {party_type})\n")
            f.write("DO $$ DECLARE v_id BIGINT; BEGIN\n")

            # 1. person_entity
            f.write(f"  INSERT INTO person_entity (scid, name, address, person_type, created_at, updated_at)\n")
            f.write(f"  VALUES (1, {sql_str(name)}, {sql_str(address)}, {sql_str(person_type)}, '{NOW}', '{NOW}')\n")
            f.write(f"  RETURNING id INTO v_id;\n\n")

            # 2. users
            f.write(f"  INSERT INTO users (id, username, role_id, join_date, status)\n")
            f.write(f"  VALUES (v_id, {sql_str(username)}, "
                    f"(SELECT id FROM roles WHERE role_type = 'CUSTOMER'), "
                    f"{sql_str(join_date)}, 'ACTIVE');\n\n")

            # 3. customer
            group_ref = f"(SELECT id FROM customer_group WHERE group_name = {sql_str(group_name)} LIMIT 1)" if group_name else 'NULL'
            f.write(f"  INSERT INTO customer (id, group_id, party_id, credit_limit_amount, consumed_liters)\n")
            f.write(f"  VALUES (v_id, {group_ref}, "
                    f"(SELECT id FROM party WHERE party_type = {sql_str(party_type)}), "
                    f"{credit_limit_val}, 0);\n\n")

            # 4. phone
            if phone and phone not in ('null',):
                f.write(f"  INSERT INTO person_phones (person_id, phone_number) VALUES (v_id, {sql_str(phone)});\n")

            # 5. email
            if email and '@' in email:
                f.write(f"  INSERT INTO person_emails (person_id, email) VALUES (v_id, {sql_str(email)});\n")

            # 6. customer contacts (additional phones)
            if cid in contact_map:
                for ce in contact_map[cid]:
                    ce_phone = clean_str(ce.get('CE_Phone'))
                    ce_name = clean_str(ce.get('CE_Names'))
                    if ce_phone:
                        f.write(f"  -- Contact: {ce_name} ({ce.get('CE_Designation', '')})\n")
                        f.write(f"  INSERT INTO person_phones (person_id, phone_number) VALUES (v_id, {sql_str(ce_phone)});\n")

            f.write("END $$;\n")
            count += 1

        f.write(f"\n-- Total: {count} customers\n")

    print(f"  -> {path} ({count} customers)")
    return count


def export_vehicles(cursor, outdir):
    """Export vehicles from MySQL vehicle_new table (single source of truth).

    vehicle_new has: Vehicle_ID, Customer_ID, VehicleNo, VehicleType, VehicleLimit
    Credit/cash invoices resolve vehicles by matching against vehicle_new — we do NOT
    create vehicle records from invoice data.
    """
    print("\n[9] Exporting vehicles from vehicle_new...")

    cursor.execute("""
        SELECT vn.Vehicle_ID, vn.Customer_ID, vn.VehicleNo, vn.VehicleType, vn.VehicleLimit,
               cd.Customer_Name
        FROM vehicle_new vn
        LEFT JOIN customer_data cd ON cd.Customer_ID = vn.Customer_ID
        ORDER BY vn.Vehicle_ID
    """)
    vehicles = cursor.fetchall()

    path = os.path.join(outdir, '09_vehicles.sql')
    seen_numbers = set()

    with open(path, 'w') as f:
        f.write("-- Vehicles from MySQL vehicle_new table (single source of truth)\n")
        f.write("-- IMPORTANT: Run AFTER 08_customers.sql\n\n")

        count = 0
        for v in vehicles:
            vno = clean_str(v['VehicleNo'])
            if not vno or vno.upper() in seen_numbers:
                continue
            seen_numbers.add(vno.upper())

            # Truncate to 20 chars (PG column limit)
            if len(vno) > 20:
                vno = vno[:20]

            # Vehicle type mapping
            vtype = clean_str(v.get('VehicleType'))
            pg_vtype = VEHICLE_TYPE_MAP.get(vtype, vtype) if vtype else None
            vtype_ref = f"(SELECT id FROM vehicle_type WHERE type_name = {sql_str(pg_vtype)} LIMIT 1)" if pg_vtype else 'NULL'

            # Customer mapping via customer_data name -> person_entity
            cust_name = clean_str(v.get('Customer_Name'))
            cust_ref = 'NULL'
            if cust_name:
                cust_ref = f"(SELECT pe.id FROM person_entity pe JOIN customer c ON c.id = pe.id WHERE pe.name = {sql_str(cust_name)} LIMIT 1)"

            # Monthly limit: -1 means no limit
            vehicle_limit = v.get('VehicleLimit', -1)
            limit_val = 'NULL'
            if vehicle_limit and vehicle_limit > 0:
                limit_val = str(vehicle_limit)

            f.write(f"INSERT INTO vehicle (vehicle_number, vehicle_type_id, customer_id, max_liters_per_month, status, created_at, updated_at)\n")
            f.write(f"  VALUES ({sql_str(vno)}, {vtype_ref}, {cust_ref}, {limit_val}, 'ACTIVE', '{NOW}', '{NOW}')\n")
            f.write(f"  ON CONFLICT (vehicle_number) DO NOTHING;\n")
            count += 1

        f.write(f"\n-- Total: {count} vehicles (from vehicle_new)\n")

    print(f"  -> {path} ({count} vehicles)")
    return count


# ============================================================
# Main
# ============================================================

def main():
    parser = argparse.ArgumentParser(description='Export master data from MySQL to PostgreSQL SQL files')
    parser.add_argument('--output-dir', default='./output', help='Output directory for SQL files')
    args = parser.parse_args()

    outdir = args.output_dir
    os.makedirs(outdir, exist_ok=True)

    print("=" * 60)
    print("StopForFuel MySQL -> PostgreSQL Master Data Export")
    print("=" * 60)

    # Connect to MySQL
    print(f"\nConnecting to MySQL ({MYSQL_CONFIG['host']}:{MYSQL_CONFIG['port']}/{MYSQL_CONFIG['database']})...")
    conn = mysql.connector.connect(**MYSQL_CONFIG)
    cursor = conn.cursor(dictionary=True)
    print("  Connected!")

    # Generate import script
    import_path = os.path.join(outdir, '00_import_all.sh')
    with open(import_path, 'w') as f:
        f.write("#!/bin/bash\n")
        f.write("# Import all master data SQL files into PostgreSQL\n")
        f.write("# Usage: ./00_import_all.sh\n\n")
        f.write('DB_HOST="${DB_HOST:-127.0.0.1}"\n')
        f.write('DB_PORT="${DB_PORT:-5432}"\n')
        f.write('DB_NAME="${DB_NAME:-stopforfuel}"\n')
        f.write('DB_USER="${DB_USER:-postgres}"\n\n')
        f.write("set -e\n\n")

    try:
        export_customer_groups(cursor, outdir)
        export_vehicle_types(cursor, outdir)
        export_designations(cursor, outdir)
        export_expense_types(cursor, outdir)
        export_company(cursor, outdir)
        export_products(cursor, outdir)
        export_employees(cursor, outdir)
        export_customers(cursor, outdir)
        export_vehicles(cursor, outdir)
    finally:
        cursor.close()
        conn.close()

    # Complete import script
    with open(import_path, 'a') as f:
        for i, name in enumerate([
            '01_customer_groups.sql',
            '02_vehicle_types.sql',
            '03_designations.sql',
            '04_expense_types.sql',
            '05_company.sql',
            '06_products.sql',
            '07_employees.sql',
            '08_customers.sql',
            '09_vehicles.sql',
        ], 1):
            f.write(f'echo "[{i}/9] Importing {name}..."\n')
            f.write(f'psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -f "{name}"\n\n')
        f.write('echo "\\nDone! All master data imported."\n')

    os.chmod(import_path, 0o755)

    print("\n" + "=" * 60)
    print("Export complete!")
    print(f"Output directory: {outdir}")
    print(f"\nTo import into PostgreSQL:")
    print(f"  cd {outdir}")
    print(f"  ./00_import_all.sh")
    print(f"\n  Or import individually:")
    print(f"  psql -h localhost -U postgres -d stopforfuel -f 01_customer_groups.sql")
    print("=" * 60)


if __name__ == '__main__':
    main()
