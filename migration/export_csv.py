#!/usr/bin/env python3
"""
Export master data from MySQL (bunkdb) into CSV files + a PostgreSQL import script.

The CSVs are ready for PostgreSQL COPY import. For JOINED inheritance tables
(person_entity, users, employees, customer), IDs are pre-assigned so they match
across the 3 tables.

Usage:
    python export_csv.py [--output-dir ./output]

Output:
    csv/  - CSV files for each table
    import.sql - PostgreSQL script to COPY all CSVs and reset sequences
"""
import os
import csv
import sys
import argparse
import datetime
from decimal import Decimal

import mysql.connector

from config import MYSQL_CONFIG
from mappings import (
    OIL_TABLE_TO_PRODUCT, FUEL_PRODUCT_MAP, DESIGNATION_MAP,
    VEHICLE_TYPE_MAP, PARTY_TYPE_MAP,
)


# ============================================================
# Utilities
# ============================================================

def sanitize_date(val):
    if val is None:
        return ''
    s = str(val)
    if s.startswith('0000') or s.strip() == '':
        return ''
    if isinstance(val, (datetime.date, datetime.datetime)):
        if val.year < 1970:
            return ''
        return str(val)
    return ''


def clean(val):
    """Return cleaned string or empty string for CSV."""
    if val is None:
        return ''
    s = str(val).strip()
    if s in ('null', 'NULL', '0') and not s.isdigit():
        return ''
    return s


def clean_phone(val):
    if val is None:
        return ''
    s = str(val).strip()
    if s in ('null', 'NULL', '0', ''):
        return ''
    return s


NOW = datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')


class CSVWriter:
    """Helper to write CSV files with consistent formatting."""

    def __init__(self, outdir, filename, columns):
        self.path = os.path.join(outdir, filename)
        self.columns = columns
        self.count = 0
        self.file = open(self.path, 'w', newline='', encoding='utf-8')
        self.writer = csv.writer(self.file)
        self.writer.writerow(columns)

    def write(self, row):
        self.writer.writerow(row)
        self.count += 1

    def close(self):
        self.file.close()
        return self.count


# ============================================================
# Export functions
# ============================================================

def export_customer_groups(cursor, csvdir):
    print("\n[1] Exporting customer groups...")
    cursor.execute("SELECT GroupID, GroupName FROM customergroups ORDER BY GroupID")
    rows = cursor.fetchall()

    w = CSVWriter(csvdir, 'customer_group.csv', ['group_name', 'description'])
    for row in rows:
        name = clean(row['GroupName'])
        if not name:
            continue
        w.write([name, ''])
    count = w.close()
    print(f"  -> {w.path} ({count} rows)")
    return count


def export_vehicle_types(cursor, csvdir):
    print("\n[2] Exporting vehicle types...")
    cursor.execute("SELECT VehicleType_ID, VehicleType FROM vehicle_type ORDER BY VehicleType_ID")
    rows = cursor.fetchall()

    seen = set()
    w = CSVWriter(csvdir, 'vehicle_type.csv', ['type_name', 'description', 'created_at', 'updated_at'])
    for row in rows:
        mysql_name = clean(row['VehicleType'])
        if not mysql_name or mysql_name == 'Select':
            continue
        pg_name = VEHICLE_TYPE_MAP.get(mysql_name, mysql_name)
        if pg_name in seen:
            continue
        seen.add(pg_name)
        desc = f"Migrated from: {mysql_name}" if pg_name != mysql_name else ''
        w.write([pg_name, desc, NOW, NOW])
    count = w.close()
    print(f"  -> {w.path} ({count} rows)")
    return count


def export_designations(cursor, csvdir):
    print("\n[3] Exporting designations...")
    cursor.execute("SELECT DISTINCT Employee_Designation FROM employee WHERE Employee_Designation != '' ORDER BY Employee_Designation")
    rows = cursor.fetchall()

    seen = set()
    w = CSVWriter(csvdir, 'designations.csv', ['name', 'description', 'default_role'])
    for row in rows:
        mysql_desig = clean(row['Employee_Designation'])
        if not mysql_desig:
            continue
        pg_name = DESIGNATION_MAP.get(mysql_desig.upper(), mysql_desig.title())
        if pg_name in seen:
            continue
        seen.add(pg_name)
        # Default role mapping
        role = 'EMPLOYEE'
        if pg_name == 'Cashier':
            role = 'CASHIER'
        elif pg_name == 'Manager':
            role = 'ADMIN'
        w.write([pg_name, f'From: {mysql_desig}', role])
    count = w.close()
    print(f"  -> {w.path} ({count} rows)")
    return count


def export_expense_types(cursor, csvdir):
    print("\n[4] Exporting expense types...")
    cursor.execute("SELECT ExpenseTypeName FROM expensetype ORDER BY ExpenseTypeName")
    rows = cursor.fetchall()

    w = CSVWriter(csvdir, 'expense_type.csv', ['type_name', 'created_at', 'updated_at'])
    for row in rows:
        name = clean(row['ExpenseTypeName'])
        if not name:
            continue
        w.write([name, NOW, NOW])
    count = w.close()
    print(f"  -> {w.path} ({count} rows)")
    return count


def export_products(cursor, csvdir):
    """Export oil/lubricant products. Fuel products (Petrol, Diesel, XP) are already seeded."""
    print("\n[5] Exporting products...")

    # Get prices from gst_oil_prices
    cursor.execute("SELECT * FROM gst_oil_prices ORDER BY Sno")
    gst_prices = {}
    for row in cursor.fetchall():
        gst_prices[row['idx_Oil_Name'].strip().upper()] = row

    w = CSVWriter(csvdir, 'product.csv', [
        'scid', 'name', 'hsn_code', 'price', 'category', 'unit', 'volume',
        'brand', 'active', 'created_at', 'updated_at',
        '_oil_type_name', '_grade_name', '_supplier_hint'  # helper columns for import script
    ])

    seen = set()
    for table_name, info in sorted(OIL_TABLE_TO_PRODUCT.items()):
        prod_name, hsn, category, oil_type, grade, brand, volume, unit = info
        if prod_name in seen:
            continue
        seen.add(prod_name)

        gst_key = table_name.upper()
        price = gst_prices.get(gst_key, {}).get('Total_Amount', '')

        w.write([
            1, prod_name, hsn if hsn != '0' else '', price or '',
            category, unit, volume or '', brand or '', 'true', NOW, NOW,
            oil_type or '', grade or '', brand or ''
        ])

    count = w.close()

    # Also export fuel price updates
    fuel_w = CSVWriter(csvdir, 'fuel_price_updates.csv', ['name', 'price'])
    if 'PETROL' in gst_prices:
        fuel_w.write(['Petrol', gst_prices['PETROL']['Total_Amount']])
    if 'XTRA_PREMIUM' in gst_prices:
        fuel_w.write(['Xtra Premium', gst_prices['XTRA_PREMIUM']['Total_Amount']])
    if 'DIESEL' in gst_prices:
        fuel_w.write(['Diesel', gst_prices['DIESEL']['Total_Amount']])
    fuel_w.close()

    # Export oil types needed
    oil_types = set()
    grades = set()
    for _, info in OIL_TABLE_TO_PRODUCT.items():
        if info[3]:
            oil_types.add(info[3])
        if info[4]:
            grades.add(info[4])

    ot_w = CSVWriter(csvdir, 'oil_type.csv', ['name', 'active', 'created_at', 'updated_at', 'description'])
    for ot in sorted(oil_types):
        ot_w.write([ot, 'true', NOW, NOW, ''])
    ot_w.close()

    gr_w = CSVWriter(csvdir, 'grade_type.csv', ['name', 'scid', 'active', 'created_at', 'updated_at', 'description', '_oil_type_name'])
    for gr in sorted(grades):
        gr_w.write([gr, 1, 'true', NOW, NOW, '', 'Engine Oil'])
    gr_w.close()

    print(f"  -> {w.path} ({count} products)")
    return count


def export_employees(cursor, csvdir):
    """Export employees as 3 CSVs: person_entity, users, employees + person_phones."""
    print("\n[6] Exporting employees...")
    cursor.execute("SELECT * FROM employee ORDER BY EmployeeID")
    employees = cursor.fetchall()

    # Pre-assign IDs starting from 1
    # (We'll use DO $$ blocks in the import script instead of COPY for inheritance tables)
    pe_w = CSVWriter(csvdir, 'employee_person_entity.csv', [
        'mysql_emp_id', 'scid', 'name', 'address', 'person_type', 'created_at', 'updated_at'
    ])
    u_w = CSVWriter(csvdir, 'employee_users.csv', [
        'mysql_emp_id', 'username', 'role_type', 'join_date', 'status'
    ])
    e_w = CSVWriter(csvdir, 'employee_details.csv', [
        'mysql_emp_id', 'designation_name', 'salary', 'aadhar_number',
        'bank_name', 'bank_ifsc', 'bank_account_number',
        'gender', 'date_of_birth', 'blood_group', 'employee_code'
    ])
    ph_w = CSVWriter(csvdir, 'employee_phones.csv', [
        'mysql_emp_id', 'phone_number'
    ])

    count = 0
    for emp in employees:
        first_name = clean(emp['Employee_Name'])
        last_name = clean(emp['EmployeeLastName'])
        name = f"{first_name} {last_name}".strip()
        if not name:
            continue

        mysql_desig = (emp['Employee_Designation'] or '').strip().upper()
        pg_desig = DESIGNATION_MAP.get(mysql_desig, (emp['Employee_Designation'] or '').strip().title())

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
        address = clean(emp['Employee_Address'])
        gender = (emp.get('Employee_Gender') or '').strip().upper() or ''
        aadhar = clean(emp.get('Employee_AdharCard'))
        if aadhar:
            aadhar = ''.join(c for c in aadhar if c.isdigit())
            if len(aadhar) != 12:
                aadhar = ''
        blood = clean(emp.get('EmployeeBloodGrp'))
        salary = emp.get('Employee_Salary') or 0
        emp_code = f"emp{emp['EmployeeID']:03d}"

        mid = emp['EmployeeID']

        pe_w.write([mid, 1, name, address, 'Individual', NOW, NOW])
        u_w.write([mid, username, role_type, join_date, status])
        e_w.write([
            mid, pg_desig, salary, aadhar or '',
            clean(emp.get('Employee_BankName')),
            clean(emp.get('Employee_IFSC')),
            clean(emp.get('Employee_BankAccNo')),
            gender, dob, blood, emp_code
        ])

        phone = clean_phone(emp.get('Employee_Phone'))
        if phone:
            ph_w.write([mid, phone])

        count += 1

    pe_w.close()
    u_w.close()
    e_w.close()
    ph_w.close()
    print(f"  -> {csvdir}/employee_*.csv ({count} employees)")
    return count


def export_customers(cursor, csvdir):
    """Export customers as CSVs for person_entity, users, customer + phones."""
    print("\n[7] Exporting customers...")
    cursor.execute("SELECT * FROM customer_data ORDER BY Customer_ID")
    customers = cursor.fetchall()

    # Customer contacts
    cursor.execute("SELECT * FROM customer_employees ORDER BY CE_ID")
    contacts = cursor.fetchall()
    contact_map = {}
    for ce in contacts:
        contact_map.setdefault(ce['CID'], []).append(ce)

    pe_w = CSVWriter(csvdir, 'customer_person_entity.csv', [
        'mysql_cid', 'scid', 'name', 'address', 'person_type', 'created_at', 'updated_at'
    ])
    u_w = CSVWriter(csvdir, 'customer_users.csv', [
        'mysql_cid', 'username', 'join_date', 'status'
    ])
    c_w = CSVWriter(csvdir, 'customer_details.csv', [
        'mysql_cid', 'group_name', 'party_type', 'credit_limit_amount'
    ])
    ph_w = CSVWriter(csvdir, 'customer_phones.csv', [
        'mysql_cid', 'phone_number', 'contact_name', 'contact_designation'
    ])
    em_w = CSVWriter(csvdir, 'customer_emails.csv', [
        'mysql_cid', 'email'
    ])

    count = 0
    for cust in customers:
        name = clean(cust['Customer_Name'])
        if not name:
            continue

        party_raw = (cust.get('Customer_PartyType') or 'Local').strip()
        party_type = PARTY_TYPE_MAP.get(party_raw, PARTY_TYPE_MAP.get(party_raw.rstrip(), 'Local'))

        group_name = clean(cust.get('Customer_Group_Name'))
        credit_limit = cust.get('Customer_CreditLimit', -1)
        credit_val = credit_limit if credit_limit and credit_limit > 0 else ''

        username = f"cust_{cust['Customer_ID']}"
        join_date = sanitize_date(cust.get('Customer_Join_Date'))
        address = clean(cust.get('Customer_Address'))
        person_type = 'Company' if party_type == 'Statement' else 'Individual'

        cid = cust['Customer_ID']

        pe_w.write([cid, 1, name, address, person_type, NOW, NOW])
        u_w.write([cid, username, join_date, 'ACTIVE'])
        c_w.write([cid, group_name, party_type, credit_val])

        # Primary phone
        phone = clean_phone(cust.get('Customer_Phone'))
        if phone:
            ph_w.write([cid, phone, name, 'Primary'])

        # Email
        email = clean(cust.get('Customer_Email'))
        if email and '@' in email:
            em_w.write([cid, email])

        # Customer contacts (additional phones)
        if cid in contact_map:
            for ce in contact_map[cid]:
                ce_phone = clean_phone(ce.get('CE_Phone'))
                ce_name = clean(ce.get('CE_Names'))
                ce_desig = clean(ce.get('CE_Designation'))
                if ce_phone:
                    ph_w.write([cid, ce_phone, ce_name or '', ce_desig or ''])

        count += 1

    pe_w.close()
    u_w.close()
    c_w.close()
    ph_w.close()
    em_w.close()
    print(f"  -> {csvdir}/customer_*.csv ({count} customers)")
    return count


def export_vehicles(cursor, csvdir):
    """Export vehicles from credit bills (unique vehicle numbers with customer linkage)."""
    print("\n[8] Exporting vehicles...")

    # Get unique vehicles from credit bills with customer info
    cursor.execute("""
        SELECT cb.idx_VehicleNo AS vno,
               cb.idx_CustomerName AS cust_name,
               cb.CID AS mysql_cid,
               MAX(cb.idx_Date) AS last_seen
        FROM creditbill_test cb
        WHERE cb.idx_VehicleNo IS NOT NULL
          AND cb.idx_VehicleNo != ''
          AND cb.idx_VehicleNo != '0'
          AND cb.CID > 0
        GROUP BY cb.idx_VehicleNo, cb.idx_CustomerName, cb.CID
        ORDER BY cb.idx_VehicleNo
    """)
    bill_vehicles = cursor.fetchall()

    # Also get vehicles from cash bills (no customer link usually)
    cursor.execute("""
        SELECT DISTINCT idx_VehicleNo AS vno
        FROM cashbill
        WHERE idx_VehicleNo IS NOT NULL
          AND idx_VehicleNo != ''
          AND idx_VehicleNo != '0'
          AND TRIM(idx_VehicleNo) != ''
    """)
    cash_vehicles = {clean(r['vno']).upper(): clean(r['vno']) for r in cursor.fetchall() if clean(r['vno'])}

    # Build unique vehicle set
    seen = set()
    w = CSVWriter(csvdir, 'vehicle.csv', [
        'vehicle_number', 'customer_name', 'mysql_cid', 'status', 'created_at', 'updated_at'
    ])

    for bv in bill_vehicles:
        vno = clean(bv['vno'])
        if not vno or vno.upper() in seen:
            continue
        if len(vno) > 20:
            vno = vno[:20]
        seen.add(vno.upper())
        cust_name = clean(bv['cust_name'])
        w.write([vno, cust_name or '', bv['mysql_cid'] or '', 'ACTIVE', NOW, NOW])

    # Add cash-only vehicles (no customer link)
    for vno_upper, vno in cash_vehicles.items():
        if vno_upper not in seen:
            if len(vno) > 20:
                vno = vno[:20]
            seen.add(vno_upper)
            w.write([vno, '', '', 'ACTIVE', NOW, NOW])

    count = w.close()
    print(f"  -> {w.path} ({count} vehicles)")
    return count


def generate_import_sql(outdir, csvdir):
    """Generate the PostgreSQL import script that reads CSVs."""
    print("\n[9] Generating import.sql...")

    # Get absolute path of csv dir relative to the SQL file
    csv_rel = os.path.basename(csvdir)

    path = os.path.join(outdir, 'import.sql')
    with open(path, 'w') as f:
        f.write("-- ============================================================\n")
        f.write("-- StopForFuel: Import master data from MySQL CSV exports\n")
        f.write("-- ============================================================\n")
        f.write("-- Usage: psql -h localhost -U postgres -d stopforfuel -f import.sql\n")
        f.write(f"-- CSV directory: {csv_rel}/\n")
        f.write("-- ============================================================\n\n")

        f.write("BEGIN;\n\n")

        # === 1. Customer Groups ===
        f.write("-- [1] Customer Groups\n")
        f.write("CREATE TEMP TABLE _tmp_groups (group_name TEXT, description TEXT);\n")
        f.write(f"\\copy _tmp_groups FROM '{csv_rel}/customer_group.csv' CSV HEADER\n")
        f.write("INSERT INTO customer_group (group_name)\n")
        f.write("  SELECT group_name FROM _tmp_groups\n")
        f.write("  WHERE group_name NOT IN (SELECT group_name FROM customer_group)\n")
        f.write("  ON CONFLICT DO NOTHING;\n")
        f.write("DROP TABLE _tmp_groups;\n\n")

        # === 2. Vehicle Types ===
        f.write("-- [2] Vehicle Types\n")
        f.write("CREATE TEMP TABLE _tmp_vtypes (type_name TEXT, description TEXT, created_at TIMESTAMP, updated_at TIMESTAMP);\n")
        f.write(f"\\copy _tmp_vtypes FROM '{csv_rel}/vehicle_type.csv' CSV HEADER\n")
        f.write("INSERT INTO vehicle_type (type_name, description, created_at, updated_at)\n")
        f.write("  SELECT type_name, description, created_at, updated_at FROM _tmp_vtypes\n")
        f.write("  WHERE type_name NOT IN (SELECT type_name FROM vehicle_type);\n")
        f.write("DROP TABLE _tmp_vtypes;\n\n")

        # === 3. Designations ===
        f.write("-- [3] Designations\n")
        f.write("CREATE TEMP TABLE _tmp_desig (name TEXT, description TEXT, default_role TEXT);\n")
        f.write(f"\\copy _tmp_desig FROM '{csv_rel}/designations.csv' CSV HEADER\n")
        f.write("INSERT INTO designations (name, description, default_role)\n")
        f.write("  SELECT name, description, default_role FROM _tmp_desig\n")
        f.write("  WHERE name NOT IN (SELECT name FROM designations)\n")
        f.write("  ON CONFLICT (name) DO NOTHING;\n")
        f.write("DROP TABLE _tmp_desig;\n\n")

        # === 4. Expense Types ===
        f.write("-- [4] Expense Types\n")
        f.write("CREATE TEMP TABLE _tmp_etypes (type_name TEXT, created_at TIMESTAMP, updated_at TIMESTAMP);\n")
        f.write(f"\\copy _tmp_etypes FROM '{csv_rel}/expense_type.csv' CSV HEADER\n")
        f.write("INSERT INTO expense_type (type_name, created_at, updated_at)\n")
        f.write("  SELECT type_name, created_at, updated_at FROM _tmp_etypes\n")
        f.write("  WHERE type_name NOT IN (SELECT type_name FROM expense_type);\n")
        f.write("DROP TABLE _tmp_etypes;\n\n")

        # === 5. Oil Types & Grade Types ===
        f.write("-- [5] Oil Types\n")
        f.write("CREATE TEMP TABLE _tmp_oil (name TEXT, active BOOLEAN, created_at TIMESTAMP, updated_at TIMESTAMP, description TEXT);\n")
        f.write(f"\\copy _tmp_oil FROM '{csv_rel}/oil_type.csv' CSV HEADER\n")
        f.write("INSERT INTO oil_type (name, active, created_at, updated_at)\n")
        f.write("  SELECT name, active, created_at, updated_at FROM _tmp_oil\n")
        f.write("  WHERE name NOT IN (SELECT name FROM oil_type)\n")
        f.write("  ON CONFLICT (name) DO NOTHING;\n")
        f.write("DROP TABLE _tmp_oil;\n\n")

        f.write("-- [5b] Grade Types\n")
        f.write("CREATE TEMP TABLE _tmp_grade (name TEXT, scid BIGINT, active BOOLEAN, created_at TIMESTAMP, updated_at TIMESTAMP, description TEXT, oil_type_name TEXT);\n")
        f.write(f"\\copy _tmp_grade FROM '{csv_rel}/grade_type.csv' CSV HEADER\n")
        f.write("INSERT INTO grade_type (name, scid, active, created_at, updated_at, oil_type_id)\n")
        f.write("  SELECT g.name, g.scid, g.active, g.created_at, g.updated_at,\n")
        f.write("         (SELECT id FROM oil_type WHERE oil_type.name = g.oil_type_name LIMIT 1)\n")
        f.write("  FROM _tmp_grade g\n")
        f.write("  WHERE g.name NOT IN (SELECT name FROM grade_type)\n")
        f.write("  ON CONFLICT (name) DO NOTHING;\n")
        f.write("DROP TABLE _tmp_grade;\n\n")

        # === 6. Products ===
        f.write("-- [6] Products (oil/lubricant/consumable)\n")
        f.write("-- First delete seed products that aren't referenced\n")
        f.write("DELETE FROM product WHERE id > 3\n")
        f.write("  AND id NOT IN (SELECT DISTINCT product_id FROM invoice_product WHERE product_id IS NOT NULL)\n")
        f.write("  AND id NOT IN (SELECT DISTINCT product_id FROM tank WHERE product_id IS NOT NULL)\n")
        f.write("  AND id NOT IN (SELECT DISTINCT product_id FROM product_inventory WHERE product_id IS NOT NULL)\n")
        f.write("  AND id NOT IN (SELECT DISTINCT product_id FROM godown_stock WHERE product_id IS NOT NULL)\n")
        f.write("  AND id NOT IN (SELECT DISTINCT product_id FROM cashier_stock WHERE product_id IS NOT NULL)\n")
        f.write("  AND id NOT IN (SELECT DISTINCT product_id FROM vehicle WHERE product_id IS NOT NULL);\n\n")

        f.write("-- Update fuel prices\n")
        f.write("CREATE TEMP TABLE _tmp_fuel (name TEXT, price NUMERIC);\n")
        f.write(f"\\copy _tmp_fuel FROM '{csv_rel}/fuel_price_updates.csv' CSV HEADER\n")
        f.write("UPDATE product SET price = f.price, updated_at = NOW()\n")
        f.write("  FROM _tmp_fuel f WHERE product.name = f.name;\n")
        f.write("DROP TABLE _tmp_fuel;\n\n")

        f.write("-- Insert oil/lubricant products\n")
        f.write("CREATE TEMP TABLE _tmp_prod (\n")
        f.write("  scid INT, name TEXT, hsn_code TEXT, price NUMERIC, category TEXT, unit TEXT,\n")
        f.write("  volume DOUBLE PRECISION, brand TEXT, active BOOLEAN, created_at TIMESTAMP, updated_at TIMESTAMP,\n")
        f.write("  oil_type_name TEXT, grade_name TEXT, supplier_hint TEXT\n")
        f.write(");\n")
        f.write(f"\\copy _tmp_prod FROM '{csv_rel}/product.csv' CSV HEADER\n")
        f.write("INSERT INTO product (scid, name, hsn_code, price, category, unit, volume, brand, active,\n")
        f.write("                     created_at, updated_at, oil_type_id, grade_id, supplier_id)\n")
        f.write("  SELECT p.scid, p.name, NULLIF(p.hsn_code, ''), p.price, p.category, p.unit, p.volume, p.brand, p.active,\n")
        f.write("         p.created_at, p.updated_at,\n")
        f.write("         (SELECT id FROM oil_type WHERE oil_type.name = p.oil_type_name LIMIT 1),\n")
        f.write("         (SELECT id FROM grade_type WHERE grade_type.name = p.grade_name LIMIT 1),\n")
        f.write("         CASE WHEN p.supplier_hint IN ('Servo', 'IOCL') THEN (SELECT id FROM supplier WHERE name LIKE '%Indian Oil%' LIMIT 1)\n")
        f.write("              WHEN p.supplier_hint = 'Castrol' THEN (SELECT id FROM supplier WHERE name LIKE '%Castrol%' LIMIT 1)\n")
        f.write("              ELSE NULL END\n")
        f.write("  FROM _tmp_prod p\n")
        f.write("  WHERE p.name NOT IN (SELECT name FROM product);\n")
        f.write("DROP TABLE _tmp_prod;\n\n")

        # === 7. Clear seed people ===
        f.write("-- [7] Clear seed people data before importing real data\n")
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

        # === 8. Employees (JOINED inheritance via DO $$ blocks) ===
        f.write("-- [8] Employees (JOINED inheritance: person_entity -> users -> employees)\n")
        f.write("CREATE TEMP TABLE _tmp_emp_pe (\n")
        f.write("  mysql_emp_id INT, scid INT, name TEXT, address TEXT, person_type TEXT, created_at TIMESTAMP, updated_at TIMESTAMP\n")
        f.write(");\n")
        f.write("CREATE TEMP TABLE _tmp_emp_u (\n")
        f.write("  mysql_emp_id INT, username TEXT, role_type TEXT, join_date DATE, status TEXT\n")
        f.write(");\n")
        f.write("CREATE TEMP TABLE _tmp_emp_d (\n")
        f.write("  mysql_emp_id INT, designation_name TEXT, salary DOUBLE PRECISION, aadhar_number TEXT,\n")
        f.write("  bank_name TEXT, bank_ifsc TEXT, bank_account_number TEXT,\n")
        f.write("  gender TEXT, date_of_birth DATE, blood_group TEXT, employee_code TEXT\n")
        f.write(");\n")
        f.write("CREATE TEMP TABLE _tmp_emp_ph (\n")
        f.write("  mysql_emp_id INT, phone_number TEXT\n")
        f.write(");\n")
        f.write("-- Mapping table: mysql_emp_id -> pg_id\n")
        f.write("CREATE TEMP TABLE _emp_id_map (mysql_id INT PRIMARY KEY, pg_id BIGINT NOT NULL);\n\n")

        f.write(f"\\copy _tmp_emp_pe FROM '{csv_rel}/employee_person_entity.csv' CSV HEADER\n")
        f.write(f"\\copy _tmp_emp_u FROM '{csv_rel}/employee_users.csv' CSV HEADER\n")
        f.write(f"\\copy _tmp_emp_d FROM '{csv_rel}/employee_details.csv' CSV HEADER\n")
        f.write(f"\\copy _tmp_emp_ph FROM '{csv_rel}/employee_phones.csv' CSV HEADER\n\n")

        f.write("-- Insert person_entity rows and capture IDs\n")
        f.write("INSERT INTO person_entity (scid, name, address, person_type, created_at, updated_at)\n")
        f.write("  SELECT scid, name, NULLIF(address, ''), person_type, created_at, updated_at\n")
        f.write("  FROM _tmp_emp_pe ORDER BY mysql_emp_id;\n\n")

        f.write("-- Map MySQL IDs to generated PG IDs (by matching name + created_at)\n")
        f.write("INSERT INTO _emp_id_map (mysql_id, pg_id)\n")
        f.write("  SELECT t.mysql_emp_id, pe.id\n")
        f.write("  FROM _tmp_emp_pe t\n")
        f.write("  JOIN person_entity pe ON pe.name = t.name AND pe.person_type = 'Individual'\n")
        f.write("    AND pe.created_at = t.created_at;\n\n")

        f.write("-- Insert users\n")
        f.write("INSERT INTO users (id, username, role_id, join_date, status)\n")
        f.write("  SELECT m.pg_id, u.username,\n")
        f.write("         (SELECT id FROM roles WHERE role_type = u.role_type),\n")
        f.write("         NULLIF(u.join_date::TEXT, '')::DATE,\n")
        f.write("         u.status\n")
        f.write("  FROM _tmp_emp_u u\n")
        f.write("  JOIN _emp_id_map m ON m.mysql_id = u.mysql_emp_id;\n\n")

        f.write("-- Insert employees\n")
        f.write("INSERT INTO employees (id, designation_id, salary, aadhar_number,\n")
        f.write("  bank_name, bank_ifsc, bank_account_number, gender, date_of_birth, blood_group, employee_code)\n")
        f.write("  SELECT m.pg_id,\n")
        f.write("         (SELECT id FROM designations WHERE name = d.designation_name LIMIT 1),\n")
        f.write("         d.salary, NULLIF(d.aadhar_number, ''),\n")
        f.write("         NULLIF(d.bank_name, ''), NULLIF(d.bank_ifsc, ''), NULLIF(d.bank_account_number, ''),\n")
        f.write("         NULLIF(d.gender, ''), NULLIF(d.date_of_birth::TEXT, '')::DATE,\n")
        f.write("         NULLIF(d.blood_group, ''), d.employee_code\n")
        f.write("  FROM _tmp_emp_d d\n")
        f.write("  JOIN _emp_id_map m ON m.mysql_id = d.mysql_emp_id;\n\n")

        f.write("-- Insert employee phones\n")
        f.write("INSERT INTO person_phones (person_id, phone_number)\n")
        f.write("  SELECT m.pg_id, ph.phone_number\n")
        f.write("  FROM _tmp_emp_ph ph\n")
        f.write("  JOIN _emp_id_map m ON m.mysql_id = ph.mysql_emp_id;\n\n")

        f.write("DROP TABLE _tmp_emp_pe, _tmp_emp_u, _tmp_emp_d, _tmp_emp_ph;\n\n")

        # === 9. Customers ===
        f.write("-- [9] Customers (JOINED inheritance: person_entity -> users -> customer)\n")
        f.write("CREATE TEMP TABLE _tmp_cust_pe (\n")
        f.write("  mysql_cid INT, scid INT, name TEXT, address TEXT, person_type TEXT, created_at TIMESTAMP, updated_at TIMESTAMP\n")
        f.write(");\n")
        f.write("CREATE TEMP TABLE _tmp_cust_u (\n")
        f.write("  mysql_cid INT, username TEXT, join_date DATE, status TEXT\n")
        f.write(");\n")
        f.write("CREATE TEMP TABLE _tmp_cust_d (\n")
        f.write("  mysql_cid INT, group_name TEXT, party_type TEXT, credit_limit_amount NUMERIC\n")
        f.write(");\n")
        f.write("CREATE TEMP TABLE _tmp_cust_ph (\n")
        f.write("  mysql_cid INT, phone_number TEXT, contact_name TEXT, contact_designation TEXT\n")
        f.write(");\n")
        f.write("CREATE TEMP TABLE _tmp_cust_em (\n")
        f.write("  mysql_cid INT, email TEXT\n")
        f.write(");\n")
        f.write("-- Mapping table: mysql_cid -> pg_id\n")
        f.write("CREATE TEMP TABLE _cust_id_map (mysql_id INT PRIMARY KEY, pg_id BIGINT NOT NULL);\n\n")

        f.write(f"\\copy _tmp_cust_pe FROM '{csv_rel}/customer_person_entity.csv' CSV HEADER\n")
        f.write(f"\\copy _tmp_cust_u FROM '{csv_rel}/customer_users.csv' CSV HEADER\n")
        f.write(f"\\copy _tmp_cust_d FROM '{csv_rel}/customer_details.csv' CSV HEADER\n")
        f.write(f"\\copy _tmp_cust_ph FROM '{csv_rel}/customer_phones.csv' CSV HEADER\n")
        f.write(f"\\copy _tmp_cust_em FROM '{csv_rel}/customer_emails.csv' CSV HEADER\n\n")

        f.write("-- Insert person_entity rows\n")
        f.write("INSERT INTO person_entity (scid, name, address, person_type, created_at, updated_at)\n")
        f.write("  SELECT scid, name, NULLIF(address, ''), person_type, created_at, updated_at\n")
        f.write("  FROM _tmp_cust_pe ORDER BY mysql_cid;\n\n")

        f.write("-- Map MySQL CIDs to PG IDs (by username since it's unique)\n")
        f.write("INSERT INTO _cust_id_map (mysql_id, pg_id)\n")
        f.write("  SELECT t.mysql_cid, pe.id\n")
        f.write("  FROM _tmp_cust_pe t\n")
        f.write("  JOIN person_entity pe ON pe.name = t.name AND pe.created_at = t.created_at\n")
        f.write("  WHERE pe.id NOT IN (SELECT pg_id FROM _emp_id_map);\n\n")

        f.write("-- Insert users\n")
        f.write("INSERT INTO users (id, username, role_id, join_date, status)\n")
        f.write("  SELECT m.pg_id, u.username,\n")
        f.write("         (SELECT id FROM roles WHERE role_type = 'CUSTOMER'),\n")
        f.write("         NULLIF(u.join_date::TEXT, '')::DATE,\n")
        f.write("         u.status\n")
        f.write("  FROM _tmp_cust_u u\n")
        f.write("  JOIN _cust_id_map m ON m.mysql_id = u.mysql_cid;\n\n")

        f.write("-- Insert customer\n")
        f.write("INSERT INTO customer (id, group_id, party_id, credit_limit_amount, consumed_liters)\n")
        f.write("  SELECT m.pg_id,\n")
        f.write("         (SELECT id FROM customer_group WHERE group_name = d.group_name LIMIT 1),\n")
        f.write("         (SELECT id FROM party WHERE party_type = d.party_type LIMIT 1),\n")
        f.write("         d.credit_limit_amount,\n")
        f.write("         0\n")
        f.write("  FROM _tmp_cust_d d\n")
        f.write("  JOIN _cust_id_map m ON m.mysql_id = d.mysql_cid;\n\n")

        f.write("-- Insert customer phones\n")
        f.write("INSERT INTO person_phones (person_id, phone_number)\n")
        f.write("  SELECT m.pg_id, ph.phone_number\n")
        f.write("  FROM _tmp_cust_ph ph\n")
        f.write("  JOIN _cust_id_map m ON m.mysql_id = ph.mysql_cid\n")
        f.write("  WHERE ph.phone_number IS NOT NULL AND ph.phone_number != '';\n\n")

        f.write("-- Insert customer emails\n")
        f.write("INSERT INTO person_emails (person_id, email)\n")
        f.write("  SELECT m.pg_id, em.email\n")
        f.write("  FROM _tmp_cust_em em\n")
        f.write("  JOIN _cust_id_map m ON m.mysql_id = em.mysql_cid\n")
        f.write("  WHERE em.email IS NOT NULL AND em.email != '';\n\n")

        f.write("DROP TABLE _tmp_cust_pe, _tmp_cust_u, _tmp_cust_d, _tmp_cust_ph, _tmp_cust_em;\n\n")

        # === 10. Vehicles ===
        f.write("-- [10] Vehicles\n")
        f.write("CREATE TEMP TABLE _tmp_veh (\n")
        f.write("  vehicle_number TEXT, customer_name TEXT, mysql_cid INT, status TEXT, created_at TIMESTAMP, updated_at TIMESTAMP\n")
        f.write(");\n")
        f.write(f"\\copy _tmp_veh FROM '{csv_rel}/vehicle.csv' CSV HEADER\n")
        f.write("INSERT INTO vehicle (vehicle_number, customer_id, status, created_at, updated_at)\n")
        f.write("  SELECT v.vehicle_number,\n")
        f.write("         COALESCE(\n")
        f.write("           (SELECT m.pg_id FROM _cust_id_map m WHERE m.mysql_id = v.mysql_cid),\n")
        f.write("           (SELECT pe.id FROM person_entity pe JOIN customer c ON c.id = pe.id WHERE pe.name = v.customer_name LIMIT 1)\n")
        f.write("         ),\n")
        f.write("         v.status, v.created_at, v.updated_at\n")
        f.write("  FROM _tmp_veh v\n")
        f.write("  WHERE v.vehicle_number NOT IN (SELECT vehicle_number FROM vehicle)\n")
        f.write("  ON CONFLICT (vehicle_number) DO NOTHING;\n")
        f.write("DROP TABLE _tmp_veh;\n\n")

        # === Cleanup ===
        f.write("-- [11] Persist ID maps for later use (transactional data migration)\n")
        f.write("CREATE TABLE IF NOT EXISTS _migration_emp_map (mysql_id INT PRIMARY KEY, pg_id BIGINT NOT NULL);\n")
        f.write("CREATE TABLE IF NOT EXISTS _migration_cust_map (mysql_id INT PRIMARY KEY, pg_id BIGINT NOT NULL);\n")
        f.write("TRUNCATE _migration_emp_map;\n")
        f.write("TRUNCATE _migration_cust_map;\n")
        f.write("INSERT INTO _migration_emp_map SELECT * FROM _emp_id_map;\n")
        f.write("INSERT INTO _migration_cust_map SELECT * FROM _cust_id_map;\n")
        f.write("DROP TABLE _emp_id_map, _cust_id_map;\n\n")

        # === Reset sequences ===
        f.write("-- [12] Reset sequences\n")
        f.write("SELECT setval('person_entity_id_seq', (SELECT COALESCE(MAX(id), 1) FROM person_entity));\n")
        f.write("SELECT setval('customer_group_id_seq', (SELECT COALESCE(MAX(id), 1) FROM customer_group));\n")
        f.write("SELECT setval('vehicle_type_id_seq', (SELECT COALESCE(MAX(id), 1) FROM vehicle_type));\n")
        f.write("SELECT setval('designations_id_seq', (SELECT COALESCE(MAX(id), 1) FROM designations));\n")
        f.write("SELECT setval('expense_type_id_seq', (SELECT COALESCE(MAX(id), 1) FROM expense_type));\n")
        f.write("SELECT setval('product_id_seq', (SELECT COALESCE(MAX(id), 1) FROM product));\n")
        f.write("SELECT setval('oil_type_id_seq', (SELECT COALESCE(MAX(id), 1) FROM oil_type));\n")
        f.write("SELECT setval('grade_type_id_seq', (SELECT COALESCE(MAX(id), 1) FROM grade_type));\n")
        f.write("SELECT setval('vehicle_id_seq', (SELECT COALESCE(MAX(id), 1) FROM vehicle));\n\n")

        # === Summary ===
        f.write("-- [13] Summary\n")
        f.write("SELECT 'customer_group' AS table_name, COUNT(*) AS row_count FROM customer_group\n")
        f.write("UNION ALL SELECT 'vehicle_type', COUNT(*) FROM vehicle_type\n")
        f.write("UNION ALL SELECT 'designations', COUNT(*) FROM designations\n")
        f.write("UNION ALL SELECT 'expense_type', COUNT(*) FROM expense_type\n")
        f.write("UNION ALL SELECT 'oil_type', COUNT(*) FROM oil_type\n")
        f.write("UNION ALL SELECT 'grade_type', COUNT(*) FROM grade_type\n")
        f.write("UNION ALL SELECT 'product', COUNT(*) FROM product\n")
        f.write("UNION ALL SELECT 'employees', COUNT(*) FROM employees\n")
        f.write("UNION ALL SELECT 'customer', COUNT(*) FROM customer\n")
        f.write("UNION ALL SELECT 'vehicle', COUNT(*) FROM vehicle\n")
        f.write("UNION ALL SELECT '_migration_emp_map', COUNT(*) FROM _migration_emp_map\n")
        f.write("UNION ALL SELECT '_migration_cust_map', COUNT(*) FROM _migration_cust_map\n")
        f.write("ORDER BY table_name;\n\n")

        f.write("COMMIT;\n")

    print(f"  -> {path}")


# ============================================================
# Main
# ============================================================

def main():
    parser = argparse.ArgumentParser(description='Export master data from MySQL to CSV + PostgreSQL import script')
    parser.add_argument('--output-dir', default='./output', help='Output directory')
    args = parser.parse_args()

    outdir = args.output_dir
    csvdir = os.path.join(outdir, 'csv')
    os.makedirs(csvdir, exist_ok=True)

    print("=" * 60)
    print("StopForFuel MySQL -> CSV Master Data Export")
    print("=" * 60)

    print(f"\nConnecting to MySQL ({MYSQL_CONFIG['host']}:{MYSQL_CONFIG['port']}/{MYSQL_CONFIG['database']})...")
    conn = mysql.connector.connect(**MYSQL_CONFIG)
    cursor = conn.cursor(dictionary=True)
    print("  Connected!")

    try:
        export_customer_groups(cursor, csvdir)
        export_vehicle_types(cursor, csvdir)
        export_designations(cursor, csvdir)
        export_expense_types(cursor, csvdir)
        export_products(cursor, csvdir)
        export_employees(cursor, csvdir)
        export_customers(cursor, csvdir)
        export_vehicles(cursor, csvdir)
        generate_import_sql(outdir, csvdir)
    finally:
        cursor.close()
        conn.close()

    print("\n" + "=" * 60)
    print("Export complete!")
    print(f"\nOutput:")
    print(f"  CSVs:       {csvdir}/")
    print(f"  Import SQL: {outdir}/import.sql")
    print(f"\nTo review: open the CSV files in any spreadsheet")
    print(f"\nTo import into PostgreSQL:")
    print(f"  cd {outdir}")
    print(f"  psql -h localhost -U postgres -d stopforfuel -f import.sql")
    print("=" * 60)


if __name__ == '__main__':
    main()
