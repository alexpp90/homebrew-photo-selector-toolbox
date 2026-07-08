import sqlite3
import json
from pathlib import Path
import time
import random

def run():
    conn = sqlite3.connect(":memory:")
    conn.execute("CREATE TABLE image_cache (filepath TEXT UNIQUE, last_used INTEGER, scores TEXT)")

    num_items = 5000
    path_map = {f"/path/{i}.jpg": {"a": random.random()} for i in range(num_items)}
    now = int(time.time())

    insert_data = [(fp, now, json.dumps(scores)) for fp, scores in path_map.items()]

    start_time = time.time()
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
    insert_time = time.time() - start_time
    print(f"Insert time: {insert_time:.4f}")

    update_map = {f"/path/{i}.jpg": {"b": random.random()} for i in range(num_items)}
    update_data = [(fp, now, json.dumps(scores)) for fp, scores in update_map.items()]

    start_time = time.time()
    conn.executemany(
        """
        INSERT INTO image_cache (filepath, last_used, scores)
        VALUES (?, ?, ?)
        ON CONFLICT(filepath) DO UPDATE SET
            last_used = excluded.last_used,
            scores = json_patch(image_cache.scores, excluded.scores)
        """,
        update_data
    )
    update_time = time.time() - start_time
    print(f"Update time: {update_time:.4f}")

    print(conn.execute("SELECT scores FROM image_cache LIMIT 1").fetchone())

if __name__ == "__main__":
    run()
