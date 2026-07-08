import sqlite3
import json
conn = sqlite3.connect("benchmark.db")
print(conn.execute("SELECT scores FROM image_cache LIMIT 1").fetchone())
