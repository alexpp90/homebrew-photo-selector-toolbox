import sqlite3
import json

conn = sqlite3.connect(":memory:")
conn.execute("CREATE TABLE image_cache (filepath TEXT UNIQUE, last_used INTEGER, scores TEXT)")
conn.execute("INSERT INTO image_cache VALUES ('path1', 1, '{\"sharpness\": 12.3}')")

conn.execute(
    """
    INSERT INTO image_cache (filepath, last_used, scores)
    VALUES (?, ?, ?)
    ON CONFLICT(filepath) DO UPDATE SET
        last_used = excluded.last_used,
        scores = json_patch(image_cache.scores, excluded.scores)
    """,
    ('path1', 2, '{"noise": 4.5}')
)
print(conn.execute("SELECT scores FROM image_cache").fetchone())
