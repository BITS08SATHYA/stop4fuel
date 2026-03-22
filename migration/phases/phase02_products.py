"""Phase 2: Migrate products (fuel + oil/lubricant/consumable products)."""
from utils.date_utils import now
from utils.lookups import LookupCache
from mappings import OIL_TABLE_TO_PRODUCT, FUEL_PRODUCT_MAP


def migrate(mysql_conn, pg_conn, lookups):
    """Migrate products from MySQL gst_oil_prices + product tables."""
    pg = pg_conn.cursor()
    my = mysql_conn.cursor(dictionary=True)
    ts = now()

    lookups['products'] = LookupCache('Product')

    # --- 1. Ensure oil_type and grade_type have required entries ---
    print("\n  [2.1] Ensuring oil types and grades exist...")
    lookups['oil_types'] = LookupCache('OilType')
    lookups['grades'] = LookupCache('GradeType')

    # Load existing oil types
    pg.execute("SELECT id, name FROM oil_type")
    for row in pg.fetchall():
        lookups['oil_types'].add(row[1], row[0])

    # Add missing oil types from our product mapping
    oil_types_needed = set()
    for _, info in OIL_TABLE_TO_PRODUCT.items():
        if info[3]:  # oil_type field
            oil_types_needed.add(info[3])

    for ot_name in oil_types_needed:
        if ot_name not in lookups['oil_types']:
            pg.execute(
                "INSERT INTO oil_type (name, active, created_at, updated_at) VALUES (%s, true, %s, %s) "
                "ON CONFLICT (name) DO NOTHING RETURNING id",
                (ot_name, ts, ts)
            )
            result = pg.fetchone()
            if result:
                lookups['oil_types'].add(ot_name, result[0])
            else:
                pg.execute("SELECT id FROM oil_type WHERE name = %s", (ot_name,))
                lookups['oil_types'].add(ot_name, pg.fetchone()[0])

    # Load existing grades
    pg.execute("SELECT id, name FROM grade_type")
    for row in pg.fetchall():
        lookups['grades'].add(row[1], row[0])

    # Add missing grades
    grades_needed = set()
    for _, info in OIL_TABLE_TO_PRODUCT.items():
        if info[4]:  # grade field
            grades_needed.add(info[4])

    for gr_name in grades_needed:
        if gr_name not in lookups['grades']:
            pg.execute(
                "INSERT INTO grade_type (name, scid, active, created_at, updated_at) VALUES (%s, 1, true, %s, %s) "
                "ON CONFLICT (name) DO NOTHING RETURNING id",
                (gr_name, ts, ts)
            )
            result = pg.fetchone()
            if result:
                lookups['grades'].add(gr_name, result[0])
            else:
                pg.execute("SELECT id FROM grade_type WHERE name = %s", (gr_name,))
                lookups['grades'].add(gr_name, pg.fetchone()[0])

    pg_conn.commit()
    print(f"    Oil types: {len(lookups['oil_types'])}, Grades: {len(lookups['grades'])}")

    # --- 2. Ensure suppliers exist ---
    print("\n  [2.2] Ensuring suppliers exist...")
    lookups['suppliers'] = LookupCache('Supplier')
    pg.execute("SELECT id, name FROM supplier")
    for row in pg.fetchall():
        lookups['suppliers'].add(row[1], row[0])

    # Add IOCL alias for seed data
    iocl_id = lookups['suppliers'].get('Indian Oil Corporation Ltd', fuzzy=False)
    if iocl_id:
        lookups['suppliers'].add('IOCL', iocl_id)
        lookups['suppliers'].add('Servo', iocl_id)

    castrol_id = lookups['suppliers'].get('Castrol India Ltd', fuzzy=False)
    if castrol_id:
        lookups['suppliers'].add('Castrol', castrol_id)

    # Add new suppliers that don't exist
    brands_needed = set()
    for _, info in OIL_TABLE_TO_PRODUCT.items():
        if info[5]:  # brand field
            brands_needed.add(info[5])

    for brand in brands_needed:
        if brand not in lookups['suppliers']:
            # Check if it's a known brand mapping
            if brand in ('Servo', 'IOCL'):
                continue  # Already mapped
            if brand in ('Castrol',):
                continue  # Already mapped
            pg.execute(
                "INSERT INTO supplier (scid, name, active, created_at, updated_at) VALUES (1, %s, true, %s, %s) RETURNING id",
                (brand, ts, ts)
            )
            lookups['suppliers'].add(brand, pg.fetchone()[0])

    pg_conn.commit()

    # --- 3. Clear seed products and insert real products ---
    print("\n  [2.3] Migrating products...")

    # First, delete seed products that have no FK references
    # Keep fuel products (1,2,3) as they match, delete oil/accessory seeds
    pg.execute("""
        DELETE FROM product WHERE id > 3
        AND id NOT IN (SELECT DISTINCT product_id FROM invoice_product WHERE product_id IS NOT NULL)
        AND id NOT IN (SELECT DISTINCT product_id FROM tank WHERE product_id IS NOT NULL)
        AND id NOT IN (SELECT DISTINCT product_id FROM product_inventory WHERE product_id IS NOT NULL)
        AND id NOT IN (SELECT DISTINCT product_id FROM godown_stock WHERE product_id IS NOT NULL)
        AND id NOT IN (SELECT DISTINCT product_id FROM cashier_stock WHERE product_id IS NOT NULL)
        AND id NOT IN (SELECT DISTINCT product_id FROM vehicle WHERE product_id IS NOT NULL)
    """)
    deleted = pg.rowcount
    if deleted > 0:
        print(f"    Cleared {deleted} seed oil/accessory products")

    # Update fuel products with correct prices from MySQL
    my.execute("SELECT * FROM gst_oil_prices WHERE idx_Oil_Name IN ('PETROL', 'XTRA_PREMIUM', 'DIESEL') ORDER BY Sno")
    fuel_prices = {row['idx_Oil_Name']: row for row in my.fetchall()}

    # Load existing fuel products
    pg.execute("SELECT id, name FROM product WHERE category = 'FUEL'")
    for row in pg.fetchall():
        lookups['products'].add(row[1], row[0])
        # Also add MySQL-style names
        for mysql_name, pg_name in FUEL_PRODUCT_MAP.items():
            if pg_name == row[1]:
                lookups['products'].add(mysql_name, row[0])

    # Update fuel prices from MySQL
    if 'PETROL' in fuel_prices:
        pg.execute("UPDATE product SET price = %s, updated_at = %s WHERE name = 'Petrol'",
                    (fuel_prices['PETROL']['Total_Amount'], ts))
    if 'XTRA_PREMIUM' in fuel_prices:
        pg.execute("UPDATE product SET price = %s, updated_at = %s WHERE name = 'Xtra Premium'",
                    (fuel_prices['XTRA_PREMIUM']['Total_Amount'], ts))
    if 'DIESEL' in fuel_prices:
        pg.execute("UPDATE product SET price = %s, updated_at = %s WHERE name = 'Diesel'",
                    (fuel_prices['DIESEL']['Total_Amount'], ts))

    # --- 4. Insert oil/lubricant/consumable products ---
    inserted = 0
    seen_products = set()  # Avoid duplicate product names

    for table_name, info in OIL_TABLE_TO_PRODUCT.items():
        prod_name, hsn, category, oil_type_name, grade_name, brand, volume, unit = info

        if prod_name in seen_products:
            continue
        seen_products.add(prod_name)

        if prod_name in lookups['products']:
            continue

        oil_type_id = lookups['oil_types'].get(oil_type_name, fuzzy=False) if oil_type_name else None
        grade_id = lookups['grades'].get(grade_name, fuzzy=False) if grade_name else None
        supplier_id = lookups['suppliers'].get(brand, fuzzy=True) if brand else None

        # Get price from gst_oil_prices
        gst_name = table_name.upper()
        my.execute("SELECT Total_Amount FROM gst_oil_prices WHERE UPPER(idx_Oil_Name) = %s LIMIT 1", (gst_name,))
        price_row = my.fetchone()
        price = price_row['Total_Amount'] if price_row else None

        pg.execute("""
            INSERT INTO product (scid, name, hsn_code, price, category, unit, volume, brand,
                                 supplier_id, oil_type_id, grade_id, active, created_at, updated_at)
            VALUES (1, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, true, %s, %s)
            RETURNING id
        """, (prod_name, hsn if hsn != '0' else None, price, category, unit, volume, brand,
              supplier_id, oil_type_id, grade_id, ts, ts))

        pid = pg.fetchone()[0]
        lookups['products'].add(prod_name, pid)
        inserted += 1

    pg_conn.commit()

    # --- 5. Build product name aliases for bill migration ---
    # Map MySQL product names (from bills) to PG product IDs
    my.execute("SELECT ProductID, Product_Name FROM product ORDER BY ProductID")
    mysql_products = my.fetchall()
    for mp in mysql_products:
        mysql_name = mp['Product_Name'].strip()
        # Try exact match first
        pid = lookups['products'].get(mysql_name, fuzzy=False)
        if not pid:
            # Map via FUEL_PRODUCT_MAP or OIL mapping
            if mysql_name in FUEL_PRODUCT_MAP:
                pid = lookups['products'].get(FUEL_PRODUCT_MAP[mysql_name], fuzzy=False)
            if pid:
                lookups['products'].add(mysql_name, pid)

    # Also map gst_oil_prices names
    my.execute("SELECT idx_Oil_Name FROM gst_oil_prices")
    for row in my.fetchall():
        gst_name = row['idx_Oil_Name'].strip()
        # Find matching table in OIL_TABLE_TO_PRODUCT
        table_key = gst_name.lower().replace(' ', '_')
        if table_key in OIL_TABLE_TO_PRODUCT:
            prod_name = OIL_TABLE_TO_PRODUCT[table_key][0]
            pid = lookups['products'].get(prod_name, fuzzy=False)
            if pid:
                lookups['products'].add(gst_name, pid)
                lookups['products'].add(gst_name.replace('_', ' '), pid)

    pg_conn.commit()
    print(f"    Inserted {inserted} new products (total in lookup: {len(lookups['products'])})")

    my.close()
    pg.close()
    return lookups
