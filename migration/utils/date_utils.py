"""MySQL date sanitization utilities."""
import datetime


def sanitize_date(val):
    """Convert MySQL 0000-00-00 dates and other invalid dates to None."""
    if val is None:
        return None
    if isinstance(val, str):
        if val.startswith('0000') or val.strip() == '':
            return None
        try:
            return datetime.date.fromisoformat(val)
        except ValueError:
            return None
    if isinstance(val, (datetime.date, datetime.datetime)):
        if val.year == 0 or val.year < 1970:
            return None
        return val
    return None


def sanitize_timestamp(val):
    """Convert MySQL timestamp to Python datetime, handling 0000-00-00."""
    if val is None:
        return None
    if isinstance(val, str):
        if val.startswith('0000') or val.strip() == '':
            return None
        try:
            return datetime.datetime.fromisoformat(val)
        except ValueError:
            return None
    if isinstance(val, datetime.datetime):
        if val.year == 0 or val.year < 1970:
            return None
        return val
    if isinstance(val, datetime.date):
        if val.year == 0 or val.year < 1970:
            return None
        return datetime.datetime.combine(val, datetime.time.min)
    return None


def now():
    """Current timestamp for created_at/updated_at."""
    return datetime.datetime.now()
