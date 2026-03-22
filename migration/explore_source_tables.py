"""Explore MySQL source tables for the next migration phase."""
import mysql.connector
from config import MYSQL_CONFIG

def run():
    conn = mysql.connector.connect(**MYSQL_CONFIG)
    cur = conn.cursor()

    # 1. Show all tables
    print("=" * 80)
    print("1. ALL TABLES IN bunkdb")
    print("=" * 80)
    cur.execute("SHOW TABLES")
    all_tables = [row[0] for row in cur.fetchall()]
    for t in all_tables:
        print(f"  {t}")
    print(f"\nTotal: {len(all_tables)} tables\n")

    # 2 & 3. For each target table, show DESCRIBE, row count, and 3 sample rows
    target_tables = [
        'card_advances', 'ccms_advance', 'cash_advances',
        'cheque_advance', 'bank_transfer_advance', 'home_advances',
        'expense', 'expensetype',
        'shift_closing_timings', 'sft_time',
        'dsr_diesel', 'dsr_ms',
        # summation tables for context
        'card_advances_summation', 'cash_advances_summation',
        'ccms_advance_summation', 'cheque_advance_summation',
        'bank_transfer_advance_summation', 'home_advances_summation',
        'expense_summation',
    ]

    # Filter to tables that actually exist
    existing_targets = [t for t in target_tables if t in all_tables]
    missing = [t for t in target_tables if t not in all_tables]
    if missing:
        print(f"NOTE: These tables do NOT exist: {missing}")
        # Try fuzzy match for shift/dsr
        for m in missing:
            matches = [t for t in all_tables if m.replace('_', '') in t.replace('_', '') or t.startswith(m[:4])]
            if matches:
                print(f"  Possible matches for '{m}': {matches}")
        print()

    for table in existing_targets:
        print("=" * 80)
        print(f"TABLE: {table}")
        print("=" * 80)

        # DESCRIBE
        print("\n--- STRUCTURE ---")
        cur.execute(f"DESCRIBE `{table}`")
        cols = [desc[0] for desc in cur.description]
        rows = cur.fetchall()
        # Print header
        header = f"{'Field':<30} {'Type':<30} {'Null':<6} {'Key':<6} {'Default':<15} {'Extra':<15}"
        print(header)
        print("-" * len(header))
        for row in rows:
            print(f"{str(row[0]):<30} {str(row[1]):<30} {str(row[2]):<6} {str(row[3]):<6} {str(row[4]):<15} {str(row[5]):<15}")

        # Row count
        cur.execute(f"SELECT COUNT(*) FROM `{table}`")
        count = cur.fetchone()[0]
        print(f"\nRow count: {count}")

        # 3 sample rows
        print("\n--- SAMPLE ROWS (3) ---")
        cur.execute(f"SELECT * FROM `{table}` LIMIT 3")
        col_names = [desc[0] for desc in cur.description]
        sample_rows = cur.fetchall()
        if not sample_rows:
            print("  (empty table)")
        else:
            for i, row in enumerate(sample_rows, 1):
                print(f"\n  Row {i}:")
                for cname, val in zip(col_names, row):
                    print(f"    {cname}: {val}")

        print()

    cur.close()
    conn.close()

if __name__ == '__main__':
    run()
