"""Migration logging utility — replaces silent error swallowing with proper tracking."""
import logging
import os
import sys
from datetime import datetime


class MigrationLogger:
    """Tracks migration progress, errors, and skipped records per phase."""

    def __init__(self, phase_name: str, log_dir: str = None):
        self.phase_name = phase_name
        self.errors = []       # (record_info, error_message)
        self.skipped = []      # (record_info, reason)
        self.warnings = []     # (message,)
        self.counts = {}       # label -> count

        # Set up file + console logging
        if log_dir is None:
            log_dir = os.path.join(os.path.dirname(os.path.dirname(__file__)), 'output', 'logs')
        os.makedirs(log_dir, exist_ok=True)

        timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
        log_file = os.path.join(log_dir, f'{phase_name}_{timestamp}.log')

        self.logger = logging.getLogger(f'migration.{phase_name}')
        self.logger.setLevel(logging.DEBUG)
        self.logger.handlers.clear()

        # File handler — detailed
        fh = logging.FileHandler(log_file)
        fh.setLevel(logging.DEBUG)
        fh.setFormatter(logging.Formatter('%(asctime)s [%(levelname)s] %(message)s'))
        self.logger.addHandler(fh)

        # Console handler — info+
        ch = logging.StreamHandler(sys.stdout)
        ch.setLevel(logging.INFO)
        ch.setFormatter(logging.Formatter('  %(message)s'))
        self.logger.addHandler(ch)

        self.log_file = log_file
        self.logger.info(f"Migration log: {log_file}")

    def info(self, msg: str):
        self.logger.info(msg)

    def warn(self, msg: str):
        self.warnings.append(msg)
        self.logger.warning(msg)

    def error_record(self, record_info: str, error: Exception):
        """Log a failed record with its error."""
        self.errors.append((record_info, str(error)))
        self.logger.debug(f"ERROR [{record_info}]: {error}")

    def skip_record(self, record_info: str, reason: str):
        """Log a skipped record with reason."""
        self.skipped.append((record_info, reason))
        self.logger.debug(f"SKIP [{record_info}]: {reason}")

    def increment(self, label: str, count: int = 1):
        """Increment a named counter."""
        self.counts[label] = self.counts.get(label, 0) + count

    def get_count(self, label: str) -> int:
        return self.counts.get(label, 0)

    def summary(self) -> str:
        """Print migration summary with error/skip details."""
        lines = [
            f"\n{'=' * 60}",
            f"  {self.phase_name} — Migration Summary",
            f"{'=' * 60}",
        ]

        # Counts
        if self.counts:
            lines.append("  Counts:")
            for label, count in self.counts.items():
                lines.append(f"    {label}: {count}")

        # Errors
        if self.errors:
            lines.append(f"\n  ERRORS: {len(self.errors)} records failed")
            # Show first 10 in console, all in log file
            for record_info, err in self.errors[:10]:
                lines.append(f"    [{record_info}] {err}")
            if len(self.errors) > 10:
                lines.append(f"    ... and {len(self.errors) - 10} more (see log file)")
        else:
            lines.append("\n  ERRORS: 0")

        # Skipped
        if self.skipped:
            lines.append(f"  SKIPPED: {len(self.skipped)} records")
            # Group by reason
            reasons = {}
            for _, reason in self.skipped:
                reasons[reason] = reasons.get(reason, 0) + 1
            for reason, count in sorted(reasons.items(), key=lambda x: -x[1]):
                lines.append(f"    {reason}: {count}")
        else:
            lines.append("  SKIPPED: 0")

        # Warnings
        if self.warnings:
            lines.append(f"  WARNINGS: {len(self.warnings)}")

        lines.append(f"\n  Full log: {self.log_file}")
        lines.append('=' * 60)

        text = '\n'.join(lines)
        self.logger.info(text)

        # Write all errors to log file (detailed)
        if len(self.errors) > 10:
            self.logger.debug(f"\n--- All {len(self.errors)} errors ---")
            for record_info, err in self.errors:
                self.logger.debug(f"  [{record_info}] {err}")

        if self.skipped:
            self.logger.debug(f"\n--- All {len(self.skipped)} skipped records ---")
            for record_info, reason in self.skipped:
                self.logger.debug(f"  [{record_info}] {reason}")

        return text

    @property
    def error_count(self) -> int:
        return len(self.errors)

    @property
    def skip_count(self) -> int:
        return len(self.skipped)
