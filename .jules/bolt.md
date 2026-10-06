## 2026-07-27 - SQLite Batch Query Chunking & Path Resolution
**Learning:** SQLite query variables are capped at 999 (SQLITE_MAX_VARIABLE_NUMBER). Increasing chunk size from 500 to 999 cuts SQL statement overhead nearly in half for batch queries. Furthermore, recording matched string filepaths directly during query fetch avoids redundant `os.path.abspath` calls and dictionary lookups in post-processing.
**Action:** Use 999 chunking for SQLite IN clauses and accumulate keys in the query loop whenever bulk updating.
