#!/usr/bin/env python3
"""
Run statements + payments migration (Phase 6): MySQL -> PostgreSQL.

Prerequisites: Phase 05 (invoices) must already be migrated.

Usage:
    python3 run_statements_payments_migration.py
"""
import mysql.connector
import psycopg2

from config import MYSQL_CONFIG, PG_CONFIG
from phases import phase06_statements_payments


def _pre_check(pg_conn, mysql_conn):
    """Pre-flight checks."""
    pg = pg_conn.cursor()
    my = mysql_conn.cursor()

    # Check invoices exist
    pg.execute("SELECT COUNT(*) FROM invoice_bill")
    bill_count = pg.fetchone()[0]
    if bill_count == 0:
        print("  ERROR: No invoice_bill rows — run Phase 5 first!")
        pg.close()
        my.close()
        return False
    print(f"  Prerequisites: {bill_count} invoice bills in PG")

    # Check existing data
    pg.execute("SELECT COUNT(*) FROM statement")
    existing_stmts = pg.fetchone()[0]
    pg.execute("SELECT COUNT(*) FROM payment")
    existing_pays = pg.fetchone()[0]
    if existing_stmts > 0 or existing_pays > 0:
        print(f"\n  [WARN] Existing data: {existing_stmts} statements, {existing_pays} payments")
        print("  Statements are idempotent (duplicates skipped).")
        print("  Payments will be re-inserted — consider clearing first.")
        resp = input("  Clear payments and re-migrate? (y/N): ").strip().lower()
        if resp == 'y':
            pg.execute("DELETE FROM payment")
            pg.execute("UPDATE statement SET received_amount = 0, balance_amount = net_amount, status = 'NOT_PAID'")
            pg.execute("UPDATE invoice_bill SET payment_status = 'NOT_PAID' WHERE bill_type = 'CREDIT'")
            pg_conn.commit()
            print("  Cleared payments and reset statuses")

    # Source counts
    my.execute("SELECT COUNT(*) FROM credit_statement")
    src_stmts = my.fetchone()[0]
    my.execute("SELECT COUNT(*) FROM incomebill")
    src_income = my.fetchone()[0]
    my.execute("SELECT COUNT(*) FROM intermediatebills")
    src_inter = my.fetchone()[0]
    print(f"  Source: {src_stmts} statements, {src_income} incomebills, {src_inter} intermediates in MySQL")

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

    # Statement counts
    my.execute("SELECT COUNT(*) FROM credit_statement")
    src_stmts = my.fetchone()[0]
    pg.execute("SELECT COUNT(*) FROM statement")
    tgt_stmts = pg.fetchone()[0]
    print(f"  Statements: MySQL={src_stmts}, PG={tgt_stmts}, diff={src_stmts - tgt_stmts}")

    # Payment counts
    pg.execute("SELECT COUNT(*) FROM payment")
    tgt_pays = pg.fetchone()[0]
    print(f"  Payments in PG: {tgt_pays}")

    # Status breakdown
    pg.execute("SELECT status, COUNT(*) FROM statement GROUP BY status ORDER BY status")
    for row in pg.fetchall():
        print(f"    Statement {row[0]}: {row[1]}")

    # Payment amount reconciliation
    my.execute("SELECT SUM(ReceivedAmount) FROM incomebill")
    src_pay_amt = my.fetchone()[0] or 0
    pg.execute("SELECT SUM(amount) FROM payment")
    tgt_pay_amt = pg.fetchone()[0] or 0
    print(f"  Payment amounts: MySQL incomebill={src_pay_amt:,.2f}, PG total={tgt_pay_amt:,.2f}")

    # Orphan checks
    pg.execute("SELECT COUNT(*) FROM payment WHERE customer_id IS NULL")
    null_cust = pg.fetchone()[0]
    pg.execute("SELECT COUNT(*) FROM payment WHERE invoice_bill_id IS NULL AND statement_id IS NULL")
    unlinked = pg.fetchone()[0]
    print(f"  Payments with NULL customer: {null_cust}")
    print(f"  Payments unlinked (no bill or statement): {unlinked}")

    # Statement balance check
    pg.execute("""
        SELECT COUNT(*) FROM statement s
        WHERE ABS(s.received_amount - COALESCE(
            (SELECT SUM(p.amount) FROM payment p WHERE p.statement_id = s.id), 0
        )) > 1
    """)
    mismatched = pg.fetchone()[0]
    print(f"  Statements with payment mismatch (>1 diff): {mismatched}")

    print("=" * 60)
    pg.close()
    my.close()


def main():
    print("=" * 60)
    print("StopForFuel Statements & Payments Migration (Phase 6)")
    print("=" * 60)

    print(f"\nConnecting to MySQL...")
    mysql_conn = mysql.connector.connect(**MYSQL_CONFIG)
    print("  Connected!")

    print(f"Connecting to PostgreSQL...")
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
        lookups = phase06_statements_payments.migrate(mysql_conn, pg_conn, lookups)
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
