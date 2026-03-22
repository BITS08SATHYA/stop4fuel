#!/usr/bin/env python3
"""Run Phase 7: Shifts, advances, and expenses migration."""
import sys
import os
sys.path.insert(0, os.path.dirname(__file__))

import mysql.connector
import psycopg2
from config import MYSQL_CONFIG, PG_CONFIG
from phases.phase07_shifts_advances_expenses import migrate


def main():
    print("=" * 60)
    print("Phase 7: Shifts + Advances + Expenses Migration")
    print("=" * 60)

    # Verify target tables are empty
    pg_conn = psycopg2.connect(**PG_CONFIG)
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
                    # Must delete shift_closing_reports references first
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

    # Connect MySQL
    mysql_conn = mysql.connector.connect(**MYSQL_CONFIG)
    mysql_conn.cursor().execute("SET SESSION group_concat_max_len = 1000000")

    print("\nConnected to both databases. Starting migration...\n")

    lookups = {}
    lookups = migrate(mysql_conn, pg_conn, lookups)

    # Final verification
    print("\n" + "=" * 60)
    print("Verification")
    print("=" * 60)
    pg2 = pg_conn.cursor()
    for label, table in tables_to_check:
        pg2.execute(f"SELECT COUNT(*) FROM {table}")
        count = pg2.fetchone()[0]
        print(f"  {label}: {count} rows")

    # Shift transaction breakdown
    pg2.execute("SELECT txn_type, COUNT(*) FROM shift_transaction GROUP BY txn_type ORDER BY txn_type")
    print("\n  Shift transaction breakdown:")
    for row in pg2.fetchall():
        print(f"    {row[0]}: {row[1]}")

    pg2.close()
    pg_conn.close()
    mysql_conn.close()
    print("\nDone!")


if __name__ == '__main__':
    main()
