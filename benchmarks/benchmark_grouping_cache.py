import time
import os
from pathlib import Path
from PIL import Image
from photo_selector_toolbox.cache import ScoreCache

def create_dummy_images(num_images):
    directory = Path("benchmarks/test_images_grouping")
    directory.mkdir(parents=True, exist_ok=True)
    paths = []
    for i in range(num_images):
        path = directory / f"img_{i}.jpg"
        if not path.exists():
            img = Image.new('RGB', (100, 100), color = (i % 255, i % 255, i % 255))
            img.save(path)
        paths.append(path)
    return paths

def main():
    paths = create_dummy_images(50)

    # We will just time the DB insertions natively in a loop similar to sharpness_gui
    cache = ScoreCache(db_path="benchmarks/test_grouping_cache.db")

    # Clear existing db
    import sqlite3
    with sqlite3.connect("benchmarks/test_grouping_cache.db") as conn:
        conn.execute("DELETE FROM image_cache")
        conn.commit()

    start_time = time.time()

    # Simulate the loop
    for path in paths:
        dhash_str = f"dhash_{path.name}"
        cache.set_scores(path, {"dhash_8": dhash_str})

    duration = time.time() - start_time
    print(f"Sequential set_scores for {len(paths)} images took: {duration:.4f} seconds")

    # Clear existing db again
    with sqlite3.connect("benchmarks/test_grouping_cache.db") as conn:
        conn.execute("DELETE FROM image_cache")
        conn.commit()

    start_time = time.time()

    # Simulate batched
    updates = {}
    for path in paths:
        dhash_str = f"dhash_{path.name}"
        updates[path] = {"dhash_8": dhash_str}

    cache.set_multiple_scores(updates)

    duration2 = time.time() - start_time
    print(f"Batched set_multiple_scores for {len(paths)} images took: {duration2:.4f} seconds")
    print(f"Improvement: {duration/duration2:.2f}x")

if __name__ == '__main__':
    main()
