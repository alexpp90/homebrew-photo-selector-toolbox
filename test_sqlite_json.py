import sqlite3

conn = sqlite3.connect(":memory:")
conn.execute("CREATE TABLE image_cache (filepath TEXT UNIQUE, last_used INTEGER, scores TEXT)")
conn.execute("INSERT INTO image_cache VALUES ('path1', 1, '{\"a\": 1, \"b\": 2}')")

insert_data = [('path1', 2, '{"b": 3, "c": 4}'), ('path2', 2, '{"a": 1}')]

conn.executemany(
    """
    INSERT INTO image_cache (filepath, last_used, scores)
    VALUES (?, ?, ?)
    ON CONFLICT(filepath) DO UPDATE SET
        last_used = excluded.last_used,
        scores = json_patch(image_cache.scores, excluded.scores)
    """,
    insert_data
)
print(conn.execute("SELECT * FROM image_cache").fetchall())
