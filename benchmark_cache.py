import time
import os
import random
from pathlib import Path
from photo_selector_toolbox.cache import ScoreCache

def run_benchmark():
    db_file = Path("benchmark.db")
    if db_file.exists():
        db_file.unlink()

    cache = ScoreCache(db_file)

    # Generate mock data
    num_items = 5000
    scores_dict = {}
    for i in range(num_items):
        p = Path(f"/mock/path/image_{i}.jpg")
        scores_dict[p] = {"sharpness": random.uniform(0, 100), "noise": random.uniform(0, 100)}

    # Benchmark initial insert
    start_time = time.time()
    cache.set_multiple_scores(scores_dict)
    insert_time = time.time() - start_time
    print(f"Insert {num_items} items: {insert_time:.4f} seconds")

    # Generate updates (same paths, new scores)
    updates_dict = {}
    for i in range(num_items):
        p = Path(f"/mock/path/image_{i}.jpg")
        updates_dict[p] = {"new_metric": random.uniform(0, 10)}

    # Benchmark update
    start_time = time.time()
    cache.set_multiple_scores(updates_dict)
    update_time = time.time() - start_time
    print(f"Update {num_items} items: {update_time:.4f} seconds")

    # Verify merge worked
    p_check = Path("/mock/path/image_0.jpg")
    res = cache.get_scores(p_check)
    print(f"Merged scores for image_0: {res}")

if __name__ == "__main__":
    run_benchmark()
