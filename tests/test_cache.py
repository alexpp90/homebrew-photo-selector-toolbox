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

        # Mock EXIF reader, calculate_all_scores, and ToolRegistry
        with (
            patch("photo_selector_toolbox.controllers.get_exif_data") as mock_exif,
            patch("photo_selector_toolbox.sharpness.calculate_all_scores") as mock_calc,
            patch("photo_selector_toolbox.controllers.ToolRegistry.get") as mock_tool_registry,
        ):
            mock_exif.return_value = None
            mock_calc.return_value = {"noise": 1.5}

            # Execute
            res = _process_single_file(img_path, grid_size=1, tools=tools)

            # Assert results
            assert res.scores["sharpness"] == 999.0  # restored from cache!
            assert res.scores["noise"] == 1.5  # calculated!

            # Assert that calculate_all_scores was called only for 'noise' (not cached)
            mock_calc.assert_called_once_with(img_path, grid_size=1, tools={"noise": True})

            # Assert that ToolRegistry.get was NOT called (both are built-in tools)
            mock_tool_registry.assert_not_called()
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


def test_score_cache_error_handling(tmp_path):
    """Test that DB errors are caught and handled gracefully in all database methods."""
    import sqlite3
    from unittest.mock import patch
    import pytest

    db_file = tmp_path / "test_error_cache.db"

    # Pre-create the cache so it doesn't fail immediately in __init__
    cache = ScoreCache(db_file)

    p1 = Path("/tmp/err_img1.jpg")

    with patch("sqlite3.connect") as mock_connect:
        # Make the connection context manager throw an exception
        mock_connect.side_effect = sqlite3.Error("Mock DB Error")

        # 1. test get_scores error
        with patch("photo_selector_toolbox.cache.logger.warning") as mock_warning:
            result = cache.get_scores(p1)
            assert result == {}
            mock_warning.assert_called_once_with(
                "Error reading from score cache: Mock DB Error"
            )

        # 2. test set_scores error
        with patch("photo_selector_toolbox.cache.logger.warning") as mock_warning:
            cache.set_scores(p1, {"sharpness": 10.0})
            mock_warning.assert_called_once_with(
                "Error writing to score cache: Mock DB Error"
            )

        # 3. test get_multiple_scores error
        with patch("photo_selector_toolbox.cache.logger.warning") as mock_warning:
            result = cache.get_multiple_scores([p1])
            assert result == {}
            mock_warning.assert_called_once_with(
                "Error bulk reading from score cache: Mock DB Error"
            )

        # 4. test set_multiple_scores error
        with patch("photo_selector_toolbox.cache.logger.warning") as mock_warning:
            cache.set_multiple_scores({p1: {"sharpness": 10.0}})
            mock_warning.assert_called_once_with(
                "Error bulk writing to score cache: Mock DB Error"
            )

        # 5. test clear_cache error
        with patch("photo_selector_toolbox.cache.logger.error") as mock_error:
            with pytest.raises(sqlite3.Error):
                cache.clear_cache()
            mock_error.assert_called_once_with(
                "Failed to clear score cache: Mock DB Error"
            )

        # 6. test _init_db error
        with patch("photo_selector_toolbox.cache.logger.error") as mock_error:
            cache._init_db()
            mock_error.assert_called_once_with(
                "Failed to initialize score cache database: Mock DB Error"
            )

        # 7. test _prune error
        # _prune expects a connection object, not going to open one so we can just pass a dummy one
        # To make it throw exception, we mock conn.execute inside _prune
        mock_conn = mock_connect.return_value
        mock_conn.execute.side_effect = sqlite3.Error("Mock Prune DB Error")

        with patch("photo_selector_toolbox.cache.logger.warning") as mock_warning:
            cache._prune(mock_conn)
            mock_warning.assert_called_once_with(
                "Error pruning score cache database: Mock Prune DB Error"
            )

def test_db_default_path():
    from photo_selector_toolbox.cache import ScoreCache, set_default_db_path
    from unittest.mock import patch, MagicMock
    from pathlib import Path

    with patch("photo_selector_toolbox.config.CONFIG_DIR", new_callable=MagicMock) as mock_config_dir:
        mock_config_dir.__truediv__.return_value = Path("/tmp/scores_cache.db")

        # We need to make _DEFAULT_DB_PATH None
        set_default_db_path(None)

        with patch("photo_selector_toolbox.cache.ScoreCache._init_db"):
            cache = ScoreCache()

            mock_config_dir.mkdir.assert_called_once_with(parents=True, exist_ok=True)
            assert str(cache.db_path) == "/tmp/scores_cache.db"

def test_db_error_recreate():
    import sqlite3
    from unittest.mock import patch
    from pathlib import Path
    from photo_selector_toolbox.cache import ScoreCache

    db_file = Path("/tmp/mock_db.db")

    with patch("sqlite3.connect") as mock_connect:
        mock_conn = mock_connect.return_value
        # First call throws DatabaseError, second call succeeds
        mock_connect.side_effect = [sqlite3.DatabaseError("Corrupted!"), mock_conn]

        with patch("photo_selector_toolbox.cache.logger.error") as mock_error:
            with patch("pathlib.Path.unlink") as mock_unlink:
                cache = ScoreCache(db_file)

                mock_unlink.assert_called_once_with(missing_ok=True)
                mock_error.assert_any_call(
                    f"Score cache database is corrupted or inaccessible: Corrupted!. "
                    f"Attempting to recreate at {db_file}"
                )

def test_db_error_recreate_fail():
    import sqlite3
    from unittest.mock import patch
    from pathlib import Path
    from photo_selector_toolbox.cache import ScoreCache

    db_file = Path("/tmp/mock_db2.db")

    with patch("sqlite3.connect") as mock_connect:
        mock_connect.side_effect = sqlite3.DatabaseError("Corrupted!")

        with patch("photo_selector_toolbox.cache.logger.error") as mock_error:
            with patch("pathlib.Path.unlink") as mock_unlink:
                mock_unlink.side_effect = Exception("Unlink failed")

                cache = ScoreCache(db_file)

                mock_unlink.assert_called_once_with(missing_ok=True)
                mock_error.assert_any_call(
                    f"Score cache database is corrupted or inaccessible: Corrupted!. "
                    f"Attempting to recreate at {db_file}"
                )
                mock_error.assert_any_call(
                    f"Failed to recreate score cache database: Unlink failed"
                )

def test_db_empty_cases(tmp_path):
    from photo_selector_toolbox.cache import ScoreCache
    db_file = tmp_path / "test_cache.db"
    cache = ScoreCache(db_file)

    # Empty list
    assert cache.get_multiple_scores([]) == {}

    # Empty dict
    cache.set_multiple_scores({})
    # just asserts it returns without error

def test_trigger_prune(tmp_path):
    from pathlib import Path
    from photo_selector_toolbox.cache import ScoreCache
    from unittest.mock import patch

    db_file = tmp_path / "test_cache.db"
    cache = ScoreCache(db_file)

    with patch.object(cache, '_prune') as mock_prune:
        cache._write_count = 49  # _PRUNE_INTERVAL is 50

        p1 = Path("/tmp/img1.jpg")
        cache.set_scores(p1, {"sharpness": 12.3})

        mock_prune.assert_called_once()
        assert cache._write_count == 0

def test_set_multiple_scores_existing_entries(tmp_path):
    from pathlib import Path
    from photo_selector_toolbox.cache import ScoreCache

    db_file = tmp_path / "test_cache.db"
    cache = ScoreCache(db_file)

    p1 = Path("/tmp/img1.jpg")
    p2 = Path("/tmp/img2.jpg")

    # Pre-seed
    cache.set_scores(p1, {"sharpness": 12.3})

    # Bulk update which should merge p1 and add p2
    data = {
        p1: {"noise": 4.5},
        p2: {"sharpness": 10.0}
    }

    cache.set_multiple_scores(data)

    assert cache.get_scores(p1) == {"sharpness": 12.3, "noise": 4.5}
    assert cache.get_scores(p2) == {"sharpness": 10.0}

def test_get_scores_error_path(tmp_path):
    from pathlib import Path
    from photo_selector_toolbox.cache import ScoreCache
    from unittest.mock import patch
    import sqlite3

    db_file = tmp_path / "test_cache.db"
    cache = ScoreCache(db_file)
    p1 = Path("/tmp/img1.jpg")

    # We mock the context manager from sqlite3.connect to throw when used
    with patch("sqlite3.connect") as mock_connect:
        mock_connect.side_effect = sqlite3.Error("Mocked DB Read Error")

        with patch("photo_selector_toolbox.cache.logger.warning") as mock_warning:
            result = cache.get_scores(p1)

            assert result == {}
            mock_warning.assert_called_once_with(
                "Error reading from score cache: Mocked DB Read Error"
            )
