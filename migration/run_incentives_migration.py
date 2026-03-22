#!/usr/bin/env python3
"""Run Phase 8: Incentives migration."""
import sys
import os
sys.path.insert(0, os.path.dirname(__file__))

import mysql.connector
import psycopg2
from config import MYSQL_CONFIG, PG_CONFIG
from phases.phase08_incentives import migrate


def _pre_check(pg_conn):
    """Pre-flight checks."""
    pg = pg_conn.cursor()

    # Check invoices exist
    pg.execute("SELECT COUNT(*) FROM invoice_product")
    prod_count = pg.fetchone()[0]
    if prod_count == 0:
        print("  ERROR: No invoice_product rows — run Phase 5 first!")
        pg.close()
        return False
    print(f"  Prerequisites: {prod_count} invoice products in PG")

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
    return True


def _reconcile(pg_conn, mysql_conn):
    """Post-migration reconciliation."""
    pg = pg_conn.cursor()
    my = mysql_conn.cursor()
    print("\n" + "=" * 60)
    print("Reconciliation")
    print("=" * 60)

    # Source counts
    my.execute("SELECT COUNT(*) FROM incentive_table WHERE incentive_amt > 0")
    src_incentives = my.fetchone()[0]

    pg.execute("SELECT COUNT(*) FROM invoice_product WHERE discount_amount IS NOT NULL AND discount_amount > 0")
    tgt_discounts = pg.fetchone()[0]
    print(f"  Incentive rows: MySQL={src_incentives}, PG discounts applied={tgt_discounts}")

    # Amount totals
    my.execute("SELECT SUM(incentive_amt) FROM incentive_table WHERE incentive_amt > 0")
    src_total = my.fetchone()[0] or 0
    pg.execute("SELECT SUM(discount_amount) FROM invoice_product WHERE discount_amount > 0")
    tgt_total = pg.fetchone()[0] or 0
    print(f"  Total discount: MySQL={src_total:,.2f}, PG={tgt_total:,.2f}")

    # Rules
    pg.execute("SELECT COUNT(*) FROM incentive")
    rule_count = pg.fetchone()[0]
    print(f"  Incentive rules in PG: {rule_count}")

    # Verify fuel product IDs are valid
    pg.execute("""
        SELECT COUNT(*) FROM incentive i
        WHERE NOT EXISTS (SELECT 1 FROM product p WHERE p.id = i.product_id)
    """)
    orphan_rules = pg.fetchone()[0]
    if orphan_rules > 0:
        print(f"  WARNING: {orphan_rules} incentive rules with invalid product_id!")

    print("=" * 60)
    pg.close()
    my.close()


def main():
    print("=" * 60)
    print("Phase 8: Incentives Migration")
    print("=" * 60)

    pg_conn = psycopg2.connect(**PG_CONFIG)
    pg_conn.autocommit = False

    if not _pre_check(pg_conn):
        pg_conn.close()
        return

    mysql_conn = mysql.connector.connect(**MYSQL_CONFIG)
    print("\nConnected to both databases. Starting migration...\n")

    lookups = {}
    try:
        lookups = migrate(mysql_conn, pg_conn, lookups)
        pg_conn.commit()
        print("\n  Migration committed successfully!")
    except Exception as e:
        pg_conn.rollback()
        print(f"\n  ERROR: Migration failed: {e}")
        import traceback
        traceback.print_exc()
        raise
    finally:
        _reconcile(pg_conn, mysql_conn)
        pg_conn.close()
        mysql_conn.close()

    print("\nDone!")


if __name__ == '__main__':
    main()
