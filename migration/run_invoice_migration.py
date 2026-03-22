#!/usr/bin/env python3
"""
Run invoice migration (Phase 5): MySQL -> PostgreSQL.

Migrates cash bills and credit bills with their line items.
Prerequisites: Master data (phases 01-04) must already be imported.

Usage:
    docker start mysql57 stopforfuel-db
    cd migration
    python run_invoice_migration.py
"""
import mysql.connector
import psycopg2

from config import MYSQL_CONFIG, PG_CONFIG
from phases import phase05_invoices


def _pre_check(pg_conn, mysql_conn):
    """Pre-flight checks before migration."""
    pg = pg_conn.cursor()
    my = mysql_conn.cursor()

    # Check prerequisites exist
    pg.execute("SELECT COUNT(*) FROM customer")
    cust_count = pg.fetchone()[0]
    pg.execute("SELECT COUNT(*) FROM product")
    prod_count = pg.fetchone()[0]
    print(f"  Prerequisites: {cust_count} customers, {prod_count} products in PG")
    if cust_count == 0 or prod_count == 0:
        print("  ERROR: Run phases 01-04 first!")
        return False

    # Check existing data
    pg.execute("SELECT COUNT(*) FROM invoice_bill")
    existing = pg.fetchone()[0]
    if existing > 0:
        print(f"\n  [WARN] invoice_bill already has {existing} rows.")
        print("  The migration is idempotent — existing bills will be skipped.")
        resp = input("  Continue? (Y/n): ").strip().lower()
        if resp == 'n':
            return False

    # Source counts
    my.execute("SELECT COUNT(*) FROM creditbill_test")
    credit_src = my.fetchone()[0]
    my.execute("SELECT COUNT(*) FROM cashbill")
    cash_src = my.fetchone()[0]
    print(f"  Source: {credit_src} credit bills, {cash_src} cash bills in MySQL")

    pg.close()
    my.close()
    return True


def _reconcile(pg_conn, mysql_conn):
    """Post-migration reconciliation."""
    pg = pg_conn.cursor()
    my = mysql_conn.cursor()
    print("\n" + "=" * 60)
    print("Reconciliation")
    print("=" * 60)

    # Row counts
    my.execute("SELECT COUNT(*) FROM creditbill_test")
    src_credit = my.fetchone()[0]
    my.execute("SELECT COUNT(*) FROM cashbill")
    src_cash = my.fetchone()[0]

    pg.execute("SELECT bill_type, COUNT(*) FROM invoice_bill GROUP BY bill_type ORDER BY bill_type")
    tgt = {row[0]: row[1] for row in pg.fetchall()}
    tgt_credit = tgt.get('CREDIT', 0)
    tgt_cash = tgt.get('CASH', 0)

    print(f"  Credit bills: MySQL={src_credit}, PG={tgt_credit}, diff={src_credit - tgt_credit}")
    print(f"  Cash bills:   MySQL={src_cash}, PG={tgt_cash}, diff={src_cash - tgt_cash}")

    # Product line counts
    my.execute("SELECT COUNT(*) FROM creditbill_products_test")
    src_cp = my.fetchone()[0]
    my.execute("SELECT COUNT(*) FROM cashbill_products")
    src_cashp = my.fetchone()[0]

    pg.execute("SELECT COUNT(*) FROM invoice_product")
    tgt_products = pg.fetchone()[0]
    print(f"  Product lines: MySQL={src_cp + src_cashp}, PG={tgt_products}, diff={src_cp + src_cashp - tgt_products}")

    # Amount totals
    my.execute("SELECT SUM(NetAmount) FROM creditbill_test")
    src_credit_amt = my.fetchone()[0] or 0
    pg.execute("SELECT SUM(net_amount) FROM invoice_bill WHERE bill_type = 'CREDIT'")
    tgt_credit_amt = pg.fetchone()[0] or 0
    print(f"  Credit amount: MySQL={src_credit_amt:,.2f}, PG={tgt_credit_amt:,.2f}")

    # Orphan checks
    pg.execute("SELECT COUNT(*) FROM invoice_bill WHERE customer_id IS NULL AND bill_type = 'CREDIT'")
    null_cust = pg.fetchone()[0]
    pg.execute("SELECT COUNT(*) FROM invoice_product WHERE product_id IS NULL")
    null_prod = pg.fetchone()[0]
    print(f"  Credit bills with NULL customer: {null_cust}")
    print(f"  Product lines with NULL product: {null_prod}")

    print("=" * 60)
    pg.close()
    my.close()


def main():
    print("=" * 60)
    print("StopForFuel Invoice Migration (Phase 5)")
    print("=" * 60)

    # Connect to MySQL
    print(f"\nConnecting to MySQL ({MYSQL_CONFIG['host']}:{MYSQL_CONFIG['port']}/{MYSQL_CONFIG['database']})...")
    mysql_conn = mysql.connector.connect(**MYSQL_CONFIG)
    print("  Connected!")

    # Connect to PostgreSQL
    print(f"Connecting to PostgreSQL ({PG_CONFIG['host']}:{PG_CONFIG['port']}/{PG_CONFIG['dbname']})...")
    pg_conn = psycopg2.connect(**PG_CONFIG)
    pg_conn.autocommit = False
    print("  Connected!")

    if not _pre_check(pg_conn, mysql_conn):
        print("  Aborted.")
        mysql_conn.close()
        pg_conn.close()
        return

    lookups = {}

    try:
        print("\n" + "-" * 60)
        print("Phase 5: Invoice Migration")
        print("-" * 60)
        lookups = phase05_invoices.migrate(mysql_conn, pg_conn, lookups)
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
        mysql_conn.close()
        pg_conn.close()


if __name__ == '__main__':
    main()
