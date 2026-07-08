import sqlite3
import json

conn = sqlite3.connect(":memory:")
conn.execute("CREATE TABLE image_cache (filepath TEXT PRIMARY KEY, last_used INTEGER, scores TEXT)")
conn.execute("INSERT INTO image_cache VALUES ('p', 1, '{\"a\": 1}')")
conn.execute("""
    INSERT INTO image_cache (filepath, last_used, scores)
    VALUES (?, ?, ?)
    ON CONFLICT(filepath) DO UPDATE SET
        last_used = excluded.last_used,
        scores = json_patch(image_cache.scores, excluded.scores)
""", ('p', 2, '{"b": 2}'))
print(conn.execute("SELECT * FROM image_cache").fetchone())
