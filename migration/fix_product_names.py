#!/usr/bin/env python3
"""Fix unmatched product names in migrated invoice_product rows.

Matches MySQL product names to PostgreSQL product names and updates
invoice_product.product_id for rows where it's currently NULL.
"""
import psycopg2
from config import PG_CONFIG

# MySQL bill product name -> PostgreSQL product name
PRODUCT_ALIAS_MAP = {
    # Fuel variants
    'XTRA_PREMIUM': 'Xtra Premium',
    'XRTA PREMIUM': 'Xtra Premium',
    'HSD_2': 'Diesel',
    'HSD_3': 'Diesel',

    # 2T oils
    '2T 40 ML POUCH': '2T 40ml Pouch',
    '2T LOOSE OIL': '2T Loose Oil',
    '2T 1 LITER': '2T 1 Liter',
    '2TOIL 1/LTS': '2T 1 Liter',
    '2t Oil 1/2 Lts': '2T 1/2 Liter',

    # 4T oils
    '4 T Oil 1 Lts': '4T 1 Liter',
    '4TOIL_20W_50W': '4T Oil 20W-50',
    '4TOIL_JOSH_10W_30W': 'Servo Honda Josh 10W-30',
    '4T_SCOOTOMATIC_10W-30W': 'Scootomatic 10W-30 (800ml)',
    'SCOOTOMATIC_800ML': 'Scootomatic 10W-30 (800ml)',
    'TRU4_KRAAFT_TVS_900_ML': 'Tru4 Kraaft TVS (900ml)',

    # 15W-40
    '15_40_1_LITER': 'Pride Alt Plus 15W-40 (1L)',
    '15 W/40 1lts': 'Pride Alt Plus 15W-40 (1L)',
    '15_40_5_LITER': 'Pride Alt Plus 15W-40 (5L)',
    '15w/40 5lts': 'Pride Alt Plus 15W-40 (5L)',
    '15/40 5 LITER': 'Pride Alt Plus 15W-40 (5L)',
    'PRIDE ALT PLUS 15W- 40W 15 LITER': 'Pride Alt Plus 15W-40 (15L)',

    # 20W-40
    '20_40_1BY2_LITER': '20W-40 (1/2L)',
    '20_40_1_LITER': '20W-40 (1L)',
    '20/40 1 LITER': '20W-40 (1L)',
    '20_40_5_LITER': '20W-40 (5L)',
    '20/40 5 LITER': '20W-40 (5L)',

    # Gear oils
    '140_1_LITER': 'Gear Oil 140 (1L)',
    '140_5_LITER': 'Gear Oil 140 (5L)',
    '90 5 LITER': 'Gear Oil 90 (5L)',
    'GEAR_OIL_90_1_LITER': 'Gear Oil 90 (1L)',

    # Grease
    'RR3 GEM 1 KG': 'RR3 Gem Grease (1 Kg)',
    'SERVO LONG LIFE GREESE 5 KG': 'RR3 Gem Grease (3 Kg)',  # closest match

    # Coolant
    'KOOL_PLUS_1_LITER': 'Kool Plus Coolant (1L)',
    'KOOL PLUS 1 L': 'Kool Plus Coolant (1L)',
    'KOOL_PLUS_1BY2_LITER': 'Kool Plus Coolant (1/2L)',
    'KOOL PLUS 1/2 L': 'Kool Plus Coolant (1/2L)',

    # Brake fluid
    'BREAK_OIL_1BY2_LITER': 'Brake Oil (1/2L)',
    'BREAK OIL 1/2 L': 'Brake Oil (1/2L)',
    'BRAKE FLUID DOT 4 - 1/4 LITER': 'Brake Oil (1/2L)',  # closest match

    # Accessories
    'BLUE_CLOTH': 'Blue Cloth',
    'YELLOW_CLOTH': 'Yellow Cloth',

    # Industrial
    'TATA_Genuine_20Lit': 'Tata Genuine Oil (20L)',

    # Names that exist in mappings but fuzzy match failed in phase05
    'DISTEL_WATER': 'Distilled Water',
    'D/ WATER': 'Distilled Water',
    'ACID': 'Battery Acid',
    'ACV': 'ACV',
    'ADON_PETROL': 'Adon Petrol Additive',
    'ADON_DIESEL': 'Adon Diesel Additive',
    '2T_40ML_POUCH': '2T 40ml Pouch',
    '2T_20ML_POUCH': '2T 20ml Pouch',
    '2T_1_LITER': '2T 1 Liter',
    '2T_1BY2_LITER': '2T 1/2 Liter',
    '2T_LOOSE_OIL': '2T Loose Oil',
    '4T_1_LITER': '4T 1 Liter',
    '4TOIL_ZOOM': '4T Oil Zoom',
    '4TXTRA_20W_40_BS6': '4T Xtra 20W-40 BS6',
    '90_1_LITER': 'Gear Oil 90 (1L)',
    '90_5_LITER': 'Gear Oil 90 (5L)',
    'GEAROIL_90_1_LITER': 'Gear Oil 90 (1L)',
    'RR3_GEM_1BY2_KG': 'RR3 Gem Grease (1/2 Kg)',
    'RR3_GEM_1_KG': 'RR3 Gem Grease (1 Kg)',
    'RR3_GEM_3_KG': 'RR3 Gem Grease (3 Kg)',
    'KOOL_PLUS_1BY2_L': 'Kool Plus Coolant (1/2L)',
    'KOOL_PLUS_1_L': 'Kool Plus Coolant (1L)',
    'BREAK_OIL_1BY2_L': 'Brake Oil (1/2L)',
    'SCOOTOMATIC_10W_30W': 'Scootomatic 10W-30 (800ml)',
    'TRU4_KRAAFT_TVS': 'Tru4 Kraaft TVS (900ml)',
    '15_40_10_LITER': 'Pride Alt Plus 15W-40 (10L)',
    '20_40_10_LITER': '20W-40 (10L)',
    'PRIDE_ALT_PLUS_15W_40': 'Pride Alt Plus 15W-40 (15L)',
    'SERVO_68_26_LIT': 'Servo 68 (26L)',
    'CLEAR_BLUE_10L': 'Clear Blue (10L)',
    'CLEAR_BLUE_20L': 'Clear Blue (20L)',
    'TATA_GENUINE_20L': 'Tata Genuine Oil (20L)',
}


def main():
    print("Fixing unmatched product names in invoice_product...")
    conn = psycopg2.connect(**PG_CONFIG)
    cur = conn.cursor()

    # Load product name -> id
    cur.execute("SELECT id, name FROM product")
    product_map = {row[1]: row[0] for row in cur.fetchall()}

    # Get all invoice_product rows with NULL product_id
    # We need to find the original MySQL product name — it's not stored in PG.
    # So we'll query MySQL for the bill_no -> product_name mapping and match.

    # Instead, let's use a smarter approach: query PG for invoice_products with NULL product_id,
    # then go back to MySQL to find the product names for those bills.
    # But that's complex. Simpler: just re-scan MySQL product tables and UPDATE directly.

    import mysql.connector
    from config import MYSQL_CONFIG

    mysql_conn = mysql.connector.connect(**MYSQL_CONFIG)
    my = mysql_conn.cursor(dictionary=True)

    # Get bill_no -> pg invoice_bill id mapping
    cur.execute("SELECT id, bill_no FROM invoice_bill")
    bill_map = {row[1]: row[0] for row in cur.fetchall()}

    total_fixed = 0

    # Process credit bill products
    print("\n  Processing credit bill products...")
    my.execute("SELECT fk_BillID, Product_Name FROM creditbill_products_test")
    for row in my.fetchall():
        bill_no = row['fk_BillID']
        prod_name = (row['Product_Name'] or '').strip()
        if prod_name not in PRODUCT_ALIAS_MAP:
            continue
        pg_prod_name = PRODUCT_ALIAS_MAP[prod_name]
        pg_prod_id = product_map.get(pg_prod_name)
        pg_bill_id = bill_map.get(bill_no)
        if pg_prod_id and pg_bill_id:
            cur.execute(
                "UPDATE invoice_product SET product_id = %s WHERE invoice_bill_id = %s AND product_id IS NULL",
                (pg_prod_id, pg_bill_id)
            )
            total_fixed += cur.rowcount

    conn.commit()
    print(f"    Fixed: {total_fixed} credit product lines")

    # Process cash bill products
    print("  Processing cash bill products...")
    cash_fixed = 0
    my.execute("SELECT fk_BillID, Product_Name FROM cashbill_products")
    for row in my.fetchall():
        bill_no = row['fk_BillID']
        prod_name = (row['Product_Name'] or '').strip()
        if prod_name not in PRODUCT_ALIAS_MAP:
            continue
        pg_prod_name = PRODUCT_ALIAS_MAP[prod_name]
        pg_prod_id = product_map.get(pg_prod_name)
        pg_bill_id = bill_map.get(bill_no)
        if pg_prod_id and pg_bill_id:
            cur.execute(
                "UPDATE invoice_product SET product_id = %s WHERE invoice_bill_id = %s AND product_id IS NULL",
                (pg_prod_id, pg_bill_id)
            )
            cash_fixed += cur.rowcount

    conn.commit()
    total_fixed += cash_fixed
    print(f"    Fixed: {cash_fixed} cash product lines")

    # Check remaining nulls
    cur.execute("SELECT COUNT(*) FROM invoice_product WHERE product_id IS NULL")
    remaining = cur.fetchone()[0]

    print(f"\n  Total fixed: {total_fixed}")
    print(f"  Remaining NULL product_id: {remaining}")

    mysql_conn.close()
    conn.close()


if __name__ == '__main__':
    main()
