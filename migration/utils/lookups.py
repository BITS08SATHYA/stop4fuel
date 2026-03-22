"""Lookup cache for name-to-ID mappings with fuzzy matching fallback."""
import difflib


class LookupCache:
    """Maps string keys to PostgreSQL IDs with fuzzy matching support."""

    def __init__(self, name: str):
        self.name = name
        self._exact = {}        # normalized_key -> id
        self._original = {}     # normalized_key -> original_key
        self._unmatched = set()

    def add(self, key: str, pg_id: int):
        norm = self._normalize(key)
        self._exact[norm] = pg_id
        self._original[norm] = key

    def get(self, key: str, fuzzy=True, threshold=0.8) -> int | None:
        if key is None:
            return None
        norm = self._normalize(key)
        if norm in self._exact:
            return self._exact[norm]
        if not fuzzy:
            self._unmatched.add(key)
            return None
        matches = difflib.get_close_matches(norm, self._exact.keys(), n=1, cutoff=threshold)
        if matches:
            return self._exact[matches[0]]
        self._unmatched.add(key)
        return None

    def get_all(self) -> dict:
        """Return all mappings as {original_key: pg_id}."""
        return {self._original[k]: v for k, v in self._exact.items()}

    def report_unmatched(self):
        if self._unmatched:
            print(f"\n  [WARN] {self.name}: {len(self._unmatched)} unmatched keys:")
            for k in sorted(self._unmatched)[:20]:
                print(f"    - '{k}'")
            if len(self._unmatched) > 20:
                print(f"    ... and {len(self._unmatched) - 20} more")

    @staticmethod
    def _normalize(key: str) -> str:
        return key.strip().upper()

    def __len__(self):
        return len(self._exact)

    def __contains__(self, key):
        return self._normalize(key) in self._exact


class IDMapper:
    """Tracks MySQL ID -> PostgreSQL ID mappings."""

    def __init__(self):
        self._maps = {}  # (table_name, mysql_id) -> pg_id

    def add(self, table: str, mysql_id, pg_id: int):
        self._maps[(table, mysql_id)] = pg_id

    def get(self, table: str, mysql_id) -> int | None:
        return self._maps.get((table, mysql_id))

    def count(self, table: str = None) -> int:
        if table:
            return sum(1 for k in self._maps if k[0] == table)
        return len(self._maps)
