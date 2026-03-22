#!/usr/bin/env python3
"""Run Phase 7: Shifts, advances, and expenses migration."""
import sys
import os
sys.path.insert(0, os.path.dirname(__file__))

import mysql.connector
import psycopg2
from config import MYSQL_CONFIG, PG_CONFIG
from phases.phase07_shifts_advances_expenses import migrate


def _pre_check(pg_conn):
    """Pre-flight checks — offer to clear existing data."""
    pg = pg_conn.cursor()

    tables_to_check = [
        ('shifts', 'shifts'),
        ('shift_transaction', 'shift_transaction'),
        ('cash_advances', 'cash_advances'),
        ('employee_advances', 'employee_advances'),
    ]

    for label, table in tables_to_check:
        pg.execute(f"SELECT COUNT(*) FROM {table}")
        count = pg.fetchone()[0]
        if count > 0:
            print(f"\n  [WARN] {label} already has {count} rows!")
            resp = input(f"  Clear {label} and re-migrate? (y/N): ").strip().lower()
            if resp == 'y':
                if table == 'shift_transaction':
                    pg.execute(f"DELETE FROM {table}")
                elif table == 'shifts':
                    pg.execute("UPDATE shift_closing_reports SET shift_id_ref = NULL WHERE shift_id_ref IS NOT NULL")
                    pg.execute(f"DELETE FROM {table}")
                elif table == 'cash_advances':
                    pg.execute("UPDATE invoice_bill SET cash_advance_id = NULL WHERE cash_advance_id IS NOT NULL")
                    pg.execute(f"DELETE FROM {table}")
                else:
                    pg.execute(f"DELETE FROM {table}")
                pg_conn.commit()
                print(f"    Cleared {label}")
            else:
                print(f"  Skipping {label}")

    pg.close()
    return True


def _reconcile(pg_conn, mysql_conn):
    """Post-migration reconciliation."""
    pg = pg_conn.cursor()
    my = mysql_conn.cursor()
    print("\n" + "=" * 60)
    print("Reconciliation")
    print("=" * 60)

    # Shift counts
    my.execute("SELECT COUNT(*) FROM shift_closing_timings")
    src_shifts = my.fetchone()[0]
    pg.execute("SELECT COUNT(*) FROM shifts")
    tgt_shifts = pg.fetchone()[0]
    print(f"  Shifts: MySQL={src_shifts}, PG={tgt_shifts}, diff={src_shifts - tgt_shifts}")

    # Transaction counts by type
    src_counts = {}
    for table, label in [('card_advances', 'CARD'), ('ccms_advance', 'CCMS'),
                         ('cheque_advance', 'CHEQUE'), ('bank_transfer_advance', 'BANK'),
                         ('expense', 'EXPENSE')]:
        my.execute(f"SELECT COUNT(*) FROM {table}")
        src_counts[label] = my.fetchone()[0]

    pg.execute("SELECT txn_type, COUNT(*) FROM shift_transaction GROUP BY txn_type ORDER BY txn_type")
    tgt_counts = {row[0]: row[1] for row in pg.fetchall()}

    print("\n  Shift transactions:")
    for label in ['CARD', 'CCMS', 'CHEQUE', 'BANK', 'EXPENSE']:
        src = src_counts.get(label, 0)
        tgt = tgt_counts.get(label, 0)
        print(f"    {label}: MySQL={src}, PG={tgt}, diff={src - tgt}")

    # Advances
    my.execute("SELECT COUNT(*) FROM cash_advances")
    src_cash_adv = my.fetchone()[0]
    pg.execute("SELECT COUNT(*) FROM cash_advances")
    tgt_cash_adv = pg.fetchone()[0]
    print(f"\n  Cash advances: MySQL={src_cash_adv}, PG={tgt_cash_adv}, diff={src_cash_adv - tgt_cash_adv}")

    my.execute("SELECT COUNT(*) FROM home_advances")
    src_home = my.fetchone()[0]
    pg.execute("SELECT COUNT(*) FROM employee_advances")
    tgt_home = pg.fetchone()[0]
    print(f"  Home advances: MySQL={src_home}, PG={tgt_home}, diff={src_home - tgt_home}")

    # Transactions with NULL shift_id
    pg.execute("SELECT COUNT(*) FROM shift_transaction WHERE shift_id IS NULL")
    null_shift = pg.fetchone()[0]
    print(f"\n  Transactions with NULL shift_id: {null_shift}")

    # Amount totals
    my.execute("SELECT SUM(Crd_Adv_Amount) FROM card_advances")
    src_card_amt = my.fetchone()[0] or 0
    pg.execute("SELECT SUM(received_amount) FROM shift_transaction WHERE txn_type = 'CARD'")
    tgt_card_amt = pg.fetchone()[0] or 0
    print(f"  Card amount: MySQL={src_card_amt:,.2f}, PG={tgt_card_amt:,.2f}")

    print("=" * 60)
    pg.close()
    my.close()


def main():
    print("=" * 60)
    print("Phase 7: Shifts + Advances + Expenses Migration")
    print("=" * 60)

    pg_conn = psycopg2.connect(**PG_CONFIG)
    pg_conn.autocommit = False

    _pre_check(pg_conn)

    mysql_conn = mysql.connector.connect(**MYSQL_CONFIG)
    mysql_conn.cursor().execute("SET SESSION group_concat_max_len = 1000000")

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
