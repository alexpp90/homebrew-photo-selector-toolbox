## 2024-05-24 - Optimize batch score caching using SQLite JSON merge
**Learning:** In SQLite, when updating JSON data on conflict, you can use the `json_patch()` function combined with `ON CONFLICT DO UPDATE SET` to merge data completely in the database without needing to fetch previous data first. This avoids the N+1 queries pattern where data is fetched and updated iteratively on the application side.
**Action:** When performing upserts with JSON columns in SQLite, leverage `json_patch(table.column, excluded.column)` to avoid pre-fetching.
