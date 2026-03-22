"""Phase 1: Migrate reference/master data (groups, vehicle types, designations, expense types, company)."""
from utils.date_utils import now, sanitize_date
from utils.lookups import LookupCache
from mappings import DESIGNATION_MAP, VEHICLE_TYPE_MAP


def migrate(mysql_conn, pg_conn, lookups):
    """Migrate reference data. Returns updated lookups dict."""
    pg = pg_conn.cursor()
    my = mysql_conn.cursor(dictionary=True)

    ts = now()

    # --- Build lookups for already-seeded data ---
    lookups['roles'] = _build_lookup(pg, 'roles', 'role_type', 'Roles')
    lookups['party'] = _build_lookup(pg, 'party', 'party_type', 'Party')
    lookups['payment_mode'] = _build_lookup(pg, 'payment_mode', 'mode_name', 'PaymentMode')

    # --- 1. Customer Groups ---
    print("\n  [1.1] Migrating customer groups...")
    lookups['groups'] = LookupCache('CustomerGroup')

    # Keep existing seed groups
    pg.execute("SELECT id, group_name FROM customer_group")
    for row in pg.fetchall():
        lookups['groups'].add(row[1], row[0])

    my.execute("SELECT GroupID, GroupName FROM customergroups ORDER BY GroupID")
    groups = my.fetchall()
    inserted = 0
    for g in groups:
        name = g['GroupName'].strip()
        if not name or name in lookups['groups']:
            continue
        pg.execute(
            "INSERT INTO customer_group (group_name) VALUES (%s) RETURNING id",
            (name,)
        )
        gid = pg.fetchone()[0]
        lookups['groups'].add(name, gid)
        inserted += 1

    pg_conn.commit()
    print(f"    Inserted {inserted} new groups (total: {len(lookups['groups'])})")

    # --- 2. Vehicle Types ---
    print("\n  [1.2] Migrating vehicle types...")
    lookups['vehicle_types'] = LookupCache('VehicleType')

    # Build from existing PG data
    pg.execute("SELECT id, type_name FROM vehicle_type")
    for row in pg.fetchall():
        lookups['vehicle_types'].add(row[1], row[0])

    # Add new types from MySQL that don't exist in PG
    my.execute("SELECT VehicleType_ID, VehicleType FROM vehicle_type ORDER BY VehicleType_ID")
    vtypes = my.fetchall()
    inserted = 0
    for vt in vtypes:
        mysql_name = vt['VehicleType'].strip()
        if not mysql_name or mysql_name == 'Select':
            continue
        # Map to PG name or use as-is
        pg_name = VEHICLE_TYPE_MAP.get(mysql_name, mysql_name)
        if pg_name in lookups['vehicle_types']:
            continue
        pg.execute(
            "INSERT INTO vehicle_type (type_name, description, created_at, updated_at) VALUES (%s, %s, %s, %s) RETURNING id",
            (pg_name, f"Migrated from: {mysql_name}", ts, ts)
        )
        vtid = pg.fetchone()[0]
        lookups['vehicle_types'].add(pg_name, vtid)
        inserted += 1

    # Also add the MySQL names as aliases so lookups work
    for vt in vtypes:
        mysql_name = vt['VehicleType'].strip()
        pg_name = VEHICLE_TYPE_MAP.get(mysql_name)
        if pg_name and pg_name in lookups['vehicle_types']:
            lookups['vehicle_types'].add(mysql_name, lookups['vehicle_types'].get(mysql_name) or lookups['vehicle_types'].get(pg_name))

    pg_conn.commit()
    print(f"    Inserted {inserted} new vehicle types (total: {len(lookups['vehicle_types'])})")

    # --- 3. Designations ---
    print("\n  [1.3] Migrating designations...")
    lookups['designations'] = LookupCache('Designation')

    pg.execute("SELECT id, name FROM designations")
    for row in pg.fetchall():
        lookups['designations'].add(row[1], row[0])

    # Extract unique designations from MySQL employees
    my.execute("SELECT DISTINCT Employee_Designation FROM employee WHERE Employee_Designation != ''")
    desigs = my.fetchall()
    inserted = 0
    for d in desigs:
        mysql_desig = d['Employee_Designation'].strip().upper()
        pg_name = DESIGNATION_MAP.get(mysql_desig, d['Employee_Designation'].strip().title())
        if pg_name in lookups['designations']:
            # Add alias for MySQL name
            lookups['designations'].add(mysql_desig, lookups['designations'].get(mysql_desig) or lookups['designations'].get(pg_name))
            continue
        pg.execute(
            "INSERT INTO designations (name, description) VALUES (%s, %s) ON CONFLICT (name) DO NOTHING RETURNING id",
            (pg_name, f"Migrated from: {mysql_desig}")
        )
        result = pg.fetchone()
        if result:
            did = result[0]
            lookups['designations'].add(pg_name, did)
            lookups['designations'].add(mysql_desig, did)
            inserted += 1
        else:
            # Already exists, just add alias
            pg.execute("SELECT id FROM designations WHERE name = %s", (pg_name,))
            did = pg.fetchone()[0]
            lookups['designations'].add(pg_name, did)
            lookups['designations'].add(mysql_desig, did)

    pg_conn.commit()
    print(f"    Inserted {inserted} new designations (total: {len(lookups['designations'])})")

    # --- 4. Expense Types ---
    print("\n  [1.4] Migrating expense types...")
    lookups['expense_types'] = LookupCache('ExpenseType')

    pg.execute("SELECT id, type_name FROM expense_type")
    for row in pg.fetchall():
        lookups['expense_types'].add(row[1], row[0])

    my.execute("SELECT DISTINCT ExpenseTypeName FROM expensetype ORDER BY ExpenseTypeName")
    etypes = my.fetchall()
    inserted = 0
    for et in etypes:
        name = et['ExpenseTypeName'].strip()
        if not name or name in lookups['expense_types']:
            continue
        # Try case-insensitive match
        existing = lookups['expense_types'].get(name, fuzzy=False)
        if existing:
            continue
        pg.execute(
            "INSERT INTO expense_type (type_name, created_at, updated_at) VALUES (%s, %s, %s) RETURNING id",
            (name, ts, ts)
        )
        etid = pg.fetchone()[0]
        lookups['expense_types'].add(name, etid)
        inserted += 1

    pg_conn.commit()
    print(f"    Inserted {inserted} new expense types (total: {len(lookups['expense_types'])})")

    # --- 5. Company ---
    print("\n  [1.5] Migrating company profile...")
    my.execute("SELECT * FROM companyprofile LIMIT 1")
    comp = my.fetchone()
    if comp:
        pg.execute("SELECT id FROM company LIMIT 1")
        existing = pg.fetchone()
        if existing:
            pg.execute("""
                UPDATE company SET
                    name = %s, open_date = %s, sap_code = %s, gst_no = %s,
                    type = %s, address = %s, updated_at = %s
                WHERE id = %s
            """, (
                comp['name'], sanitize_date(comp['opened_Date']),
                str(comp['sap_code']), comp['gst_No'],
                comp['type'], comp['bunk_Address'], ts,
                existing[0]
            ))
            print(f"    Updated company: {comp['name']}")
        else:
            pg.execute("""
                INSERT INTO company (scid, name, open_date, sap_code, gst_no, type, address, created_at, updated_at)
                VALUES (1, %s, %s, %s, %s, %s, %s, %s, %s)
            """, (
                comp['name'], sanitize_date(comp['opened_Date']),
                str(comp['sap_code']), comp['gst_No'],
                comp['type'], comp['bunk_Address'], ts, ts
            ))
            print(f"    Inserted company: {comp['name']}")

    pg_conn.commit()

    my.close()
    pg.close()
    return lookups


def _build_lookup(pg_cursor, table, name_col, cache_name):
    """Build a LookupCache from an existing PostgreSQL table."""
    cache = LookupCache(cache_name)
    pg_cursor.execute(f"SELECT id, {name_col} FROM {table}")
    for row in pg_cursor.fetchall():
        cache.add(row[1], row[0])
    return cache
