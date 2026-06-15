import json
from pathlib import Path
from photo_selector_toolbox.cache import ScoreCache, set_default_db_path


def test_score_cache_basic_get_set(tmp_path):
    db_file = tmp_path / "test_cache.db"
    cache = ScoreCache(db_file)

    p1 = Path("/tmp/img1.jpg")
    scores = {"sharpness": 12.3, "noise": 4.5}

    # Setting scores
    cache.set_scores(p1, scores)

    # Getting scores
    retrieved = cache.get_scores(p1)
    assert retrieved == scores


def test_score_cache_merge(tmp_path):
    db_file = tmp_path / "test_cache.db"
    cache = ScoreCache(db_file)

    p1 = Path("/tmp/img1.jpg")

    # First write
    cache.set_scores(p1, {"sharpness": 12.3})

    # Second write (different tool)
    cache.set_scores(p1, {"noise": 4.5})

    # The scores should be merged!
    retrieved = cache.get_scores(p1)
    assert retrieved == {"sharpness": 12.3, "noise": 4.5}


def test_score_cache_bulk(tmp_path):
    db_file = tmp_path / "test_cache.db"
    cache = ScoreCache(db_file)

    p1 = Path("/tmp/img1.jpg")
    p2 = Path("/tmp/img2.jpg")
    p3 = Path("/tmp/img3.jpg")

    data = {
        p1: {"sharpness": 10.0},
        p2: {"noise": 2.0},
    }

    # Bulk set
    cache.set_multiple_scores(data)

    # Bulk get
    retrieved = cache.get_multiple_scores([p1, p2, p3])
    assert retrieved[p1] == {"sharpness": 10.0}
    assert retrieved[p2] == {"noise": 2.0}
    assert p3 not in retrieved


def test_score_cache_pruning(tmp_path):
    db_file = tmp_path / "test_cache.db"
    cache = ScoreCache(db_file)

    # Insert 10,005 dummy items
    # To test pruning, we can temporarily lower the limit or just verify it handles a smaller prune limit.
    # But since it's hardcoded to 10000 in cache.py, let's verify that pruning works by inserting 10,005 items.
    # Since executing 10k inserts might be slow, let's use a smaller mock prune or a batch insert to speed it up.
    # Actually, we can batch insert 10005 items using executemany, and then call self._prune()!
    # Let's write a batch inserter to test pruning directly.
    import sqlite3

    with sqlite3.connect(db_file) as conn:
        data = []
        for i in range(10010):
            # We vary last_used so we can test LRU order
            data.append((f"/tmp/img{i}.jpg", i, json.dumps({"sharpness": float(i)})))

        conn.executemany(
            "INSERT INTO image_cache (filepath, last_used, scores) VALUES (?, ?, ?)",
            data,
        )
        conn.commit()

        # Prune
        cache._prune(conn)

    # Now verify total count is 10,000
    with sqlite3.connect(db_file) as conn:
        cursor = conn.cursor()
        cursor.execute("SELECT count(*) FROM image_cache")
        count = cursor.fetchone()[0]
        assert count == 10000

        # And verify that the ones with smaller last_used (which were the first 10) are deleted
        cursor.execute(
            "SELECT filepath FROM image_cache ORDER BY last_used ASC LIMIT 5"
        )
        lowest = cursor.fetchall()
        # The remaining lowest should be img10.jpg
        assert lowest[0][0] == "/tmp/img10.jpg"


def test_controller_uses_cache(tmp_path):
    db_file = tmp_path / "test_cache.db"
    set_default_db_path(db_file)

    try:
        from photo_selector_toolbox.controllers import _process_single_file
        from unittest.mock import patch

        img_path = Path("/tmp/dummy_img.jpg")

        # Pre-seed the cache with sharpness score 999.0
        cache = ScoreCache()
        cache.set_scores(img_path, {"sharpness": 999.0})

        # Run process single file for sharpness and noise
        tools = {"sharpness": True, "noise": True}

        # Mock EXIF reader and sharpness/noise tools
        with (
            patch("photo_selector_toolbox.controllers.get_exif_data") as mock_exif,
            patch(
                "photo_selector_toolbox.sharpness.calculate_all_scores"
            ) as mock_calculate_all,
        ):
            mock_exif.return_value = None

            # Setup mock calculate_all_scores
            mock_calculate_all.return_value = {"noise": 1.5}

            # Execute
            res = _process_single_file(img_path, grid_size=1, tools=tools)

            # Assert results
            assert res.scores["sharpness"] == 999.0  # restored from cache!
            assert res.scores["noise"] == 1.5  # calculated!

            # Assert that only noise was requested to be calculated!
            # Since sharpness was in cache
            mock_calculate_all.assert_called_once_with(
                img_path, grid_size=1, tools={"noise": True}
            )

            # Check database now has both merged
            cached = cache.get_scores(img_path)
            assert cached == {"sharpness": 999.0, "noise": 1.5}

    finally:
        set_default_db_path(None)


def test_score_cache_clear(tmp_path):
    db_file = tmp_path / "test_cache.db"
    cache = ScoreCache(db_file)

    p1 = Path("/tmp/img1.jpg")
    p2 = Path("/tmp/img2.jpg")

    cache.set_scores(p1, {"sharpness": 12.3})
    cache.set_scores(p2, {"noise": 4.5})

    assert cache.get_scores(p1) == {"sharpness": 12.3}
    assert cache.get_scores(p2) == {"noise": 4.5}

    # Clear cache
    cache.clear_cache()

    assert cache.get_scores(p1) == {}
    assert cache.get_scores(p2) == {}
