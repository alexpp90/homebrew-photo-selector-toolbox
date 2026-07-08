from pathlib import Path
from photo_selector_toolbox.cache import ScoreCache
db = Path("test_db.db")
if db.exists(): db.unlink()
cache = ScoreCache(db)
cache.set_multiple_scores({Path("p1"): {"a": 1}})
cache.set_multiple_scores({Path("p1"): {"b": 2}})
print(cache.get_scores(Path("p1")))
