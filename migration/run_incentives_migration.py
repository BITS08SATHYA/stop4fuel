#!/usr/bin/env python3
"""Run Phase 8: Incentives migration."""
import sys
import os
sys.path.insert(0, os.path.dirname(__file__))

import mysql.connector
import psycopg2
from config import MYSQL_CONFIG, PG_CONFIG
from phases.phase08_incentives import migrate


def main():
    print("=" * 60)
    print("Phase 8: Incentives Migration")
    print("=" * 60)

    pg_conn = psycopg2.connect(**PG_CONFIG)
    pg = pg_conn.cursor()

    # Check current state
    pg.execute("SELECT COUNT(*) FROM invoice_product WHERE discount_amount IS NOT NULL AND discount_amount > 0")
    existing_discounts = pg.fetchone()[0]
    pg.execute("SELECT COUNT(*) FROM incentive")
    existing_rules = pg.fetchone()[0]

    if existing_discounts > 0 or existing_rules > 0:
        print(f"\n  Existing discounts on invoice_product: {existing_discounts}")
        print(f"  Existing incentive rules: {existing_rules}")
        resp = input("  Clear and re-migrate? (y/N): ").strip().lower()
        if resp == 'y':
            pg.execute("UPDATE invoice_product SET discount_rate = NULL, discount_amount = NULL, amount = gross_amount WHERE discount_amount IS NOT NULL AND discount_amount > 0")
            pg.execute("UPDATE invoice_bill SET total_discount = NULL WHERE total_discount IS NOT NULL AND total_discount > 0")
            pg.execute("DELETE FROM incentive")
            pg_conn.commit()
            print("  Cleared")

    pg.close()

    mysql_conn = mysql.connector.connect(**MYSQL_CONFIG)
    print("\nConnected to both databases. Starting migration...\n")

    lookups = {}
    lookups = migrate(mysql_conn, pg_conn, lookups)

    # Final verification
    print("\n" + "=" * 60)
    print("Verification")
    print("=" * 60)
    pg2 = pg_conn.cursor()
    pg2.execute("SELECT COUNT(*) FROM invoice_product WHERE discount_amount IS NOT NULL AND discount_amount > 0")
    print(f"  Invoice products with discounts: {pg2.fetchone()[0]}")
    pg2.execute("SELECT COUNT(*) FROM incentive")
    print(f"  Incentive rules: {pg2.fetchone()[0]}")
    pg2.execute("SELECT SUM(discount_amount) FROM invoice_product WHERE discount_amount > 0")
    print(f"  Total discount amount: {pg2.fetchone()[0]}")

    pg2.close()
    pg_conn.close()
    mysql_conn.close()
    print("\nDone!")


if __name__ == '__main__':
    main()
