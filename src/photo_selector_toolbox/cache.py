import json
import logging
import sqlite3
import time
from pathlib import Path
from typing import Dict, List, Optional, Union

from photo_selector_toolbox.config import _set_secure_dir_permissions, _set_secure_permissions

logger = logging.getLogger(__name__)

_DEFAULT_DB_PATH: Optional[Path] = None


def set_default_db_path(path: Optional[Path]) -> None:
    """Sets a global default DB path, useful for redirecting to temporary files in tests."""
    global _DEFAULT_DB_PATH
    _DEFAULT_DB_PATH = path


class ScoreCache:
    """
    Manages persistent caching of image analysis scores in a SQLite database.
    Retains only the 10,000 most recently used records.

    Performance notes:
    - Uses WAL journal mode for concurrent reader/writer access across threads.
    - Defers pruning to every _PRUNE_INTERVAL writes to avoid per-write overhead.
    - Reads (get_scores) do NOT update last_used to avoid write contention on
      the hot read path; timestamps are updated on set_scores instead.
    """

    _PRUNE_INTERVAL = 50  # Prune every N set_scores calls

    def __init__(self, db_path: Optional[Path] = None):
        if db_path is None:
            if _DEFAULT_DB_PATH is not None:
                db_path = _DEFAULT_DB_PATH
            else:
                from photo_selector_toolbox.config import CONFIG_DIR

                CONFIG_DIR.mkdir(parents=True, exist_ok=True)
                _set_secure_dir_permissions(CONFIG_DIR)
                db_path = CONFIG_DIR / "scores_cache.db"
        self.db_path = db_path
        self._write_count = 0
        self._init_db()

    def _init_db(self) -> None:
        try:
            with sqlite3.connect(self.db_path) as conn:
                # WAL mode: allows concurrent reads while writes are in progress
                conn.execute("PRAGMA journal_mode=WAL")
                conn.execute("""
                    CREATE TABLE IF NOT EXISTS image_cache (
                        filepath TEXT PRIMARY KEY,
                        last_used INTEGER,
                        scores TEXT
                    )
                    """)
                # Index on last_used to speed up ORDER BY in prune queries
                conn.execute(
                    "CREATE INDEX IF NOT EXISTS idx_last_used ON image_cache(last_used)"
                )
                conn.commit()
            _set_secure_permissions(self.db_path)
            # Secure WAL/SHM auxiliary files if they were created
            wal_path = Path(str(self.db_path) + "-wal")
            if wal_path.exists():
                _set_secure_permissions(wal_path)
            shm_path = Path(str(self.db_path) + "-shm")
            if shm_path.exists():
                _set_secure_permissions(shm_path)
        except sqlite3.DatabaseError as e:
            logger.error(
                f"Score cache database is corrupted or inaccessible: {e}. "
                f"Attempting to recreate at {self.db_path}"
            )
            try:
                self.db_path.unlink(missing_ok=True)
                self._init_db()
            except Exception as e2:
                logger.error(f"Failed to recreate score cache database: {e2}")
        except Exception as e:
            logger.error(f"Failed to initialize score cache database: {e}")

    def get_scores(self, filepath: Path) -> Dict[str, Union[float, str]]:
        """Retrieves cached scores for a single image.

        Does NOT update last_used on read to avoid write contention.
        Timestamps are refreshed when scores are written via set_scores().
        """
        try:
            filepath_str = str(filepath.resolve())
            with sqlite3.connect(self.db_path) as conn:
                cursor = conn.cursor()
                cursor.execute(
                    "SELECT scores FROM image_cache WHERE filepath = ?",
                    (filepath_str,),
                )
                row = cursor.fetchone()
                if row:
                    return json.loads(row[0])
        except Exception as e:
            logger.warning(f"Error reading from score cache: {e}")
        return {}

    def set_scores(self, filepath: Path, scores: Dict[str, Union[float, str]]) -> None:
        """Stores or updates scores for a single image, updating its last_used timestamp and pruning if necessary."""
        try:
            filepath_str = str(filepath.resolve())
            now = int(time.time())
            with sqlite3.connect(self.db_path) as conn:
                conn.execute(
                    """
                    INSERT INTO image_cache (filepath, last_used, scores)
                    VALUES (?, ?, ?)
                    ON CONFLICT(filepath) DO UPDATE SET
                        last_used = excluded.last_used,
                        scores = json_patch(image_cache.scores, excluded.scores)
                    """,
                    (filepath_str, now, json.dumps(scores)),
                )
                conn.commit()
                self._write_count += 1
                if self._write_count >= self._PRUNE_INTERVAL:
                    self._write_count = 0
                    self._prune(conn)
        except Exception as e:
            logger.warning(f"Error writing to score cache: {e}")

    def get_multiple_scores(
        self, filepaths: List[Path]
    ) -> Dict[Path, Dict[str, Union[float, str]]]:
        """Retrieves cached scores for multiple images in a batch, updating their timestamps."""
        results: Dict[Path, Dict[str, Union[float, str]]] = {}
        if not filepaths:
            return results

        try:
            now = int(time.time())
            # Map resolved string path to the original Path object
            path_map = {str(p.resolve()): p for p in filepaths}
            path_list = list(path_map.keys())

            chunk_size = 500
            with sqlite3.connect(self.db_path) as conn:
                cursor = conn.cursor()
                for i in range(0, len(path_list), chunk_size):
                    chunk = path_list[i : i + chunk_size]
                    placeholders = ",".join("?" for _ in chunk)
                    cursor.execute(
                        f"SELECT filepath, scores FROM image_cache WHERE filepath IN ({placeholders})",
                        chunk,
                    )
                    rows = cursor.fetchall()
                    for fp, score_str in rows:
                        orig_path = path_map.get(fp)
                        if orig_path:
                            results[orig_path] = json.loads(score_str)

                # Bulk update last_used for matches
                if results:
                    matched_fps = [str(p.resolve()) for p in results.keys()]
                    update_data = [(now, fp) for fp in matched_fps]
                    conn.executemany(
                        "UPDATE image_cache SET last_used = ? WHERE filepath = ?",
                        update_data,
                    )
                    conn.commit()
        except Exception as e:
            logger.warning(f"Error bulk reading from score cache: {e}")
        return results

    def set_multiple_scores(
        self, scores_dict: Dict[Path, Dict[str, Union[float, str]]]
    ) -> None:
        """Stores or updates scores for multiple images in a batch, pruning the database to 10,000 items afterwards."""
        if not scores_dict:
            return

        try:
            now = int(time.time())
            with sqlite3.connect(self.db_path) as conn:
                cursor = conn.cursor()
                path_map = {str(p.resolve()): p for p in scores_dict.keys()}

                # Prepare insert batch
                insert_data = []
                for fp, orig_path in path_map.items():
                    new_scores = scores_dict[orig_path]
                    insert_data.append((fp, now, json.dumps(new_scores)))

                cursor.executemany(
                    """
                    INSERT INTO image_cache (filepath, last_used, scores)
                    VALUES (?, ?, ?)
                    ON CONFLICT(filepath) DO UPDATE SET
                        last_used = excluded.last_used,
                        scores = json_patch(image_cache.scores, excluded.scores)
                    """,
                    insert_data,
                )
                conn.commit()
                self._prune(conn)
        except Exception as e:
            logger.warning(f"Error bulk writing to score cache: {e}")

    def _prune(self, conn: sqlite3.Connection) -> None:
        """Limits database records to the 10,000 most recently used ones."""
        try:
            conn.execute("""
                DELETE FROM image_cache
                WHERE filepath NOT IN (
                    SELECT filepath FROM image_cache
                    ORDER BY last_used DESC
                    LIMIT 10000
                )
                """)
            conn.commit()
        except Exception as e:
            logger.warning(f"Error pruning score cache database: {e}")

    def clear_cache(self) -> None:
        """Deletes all cached scores from the database."""
        try:
            with sqlite3.connect(self.db_path) as conn:
                conn.execute("DELETE FROM image_cache")
                conn.commit()
            logger.info("Score cache cleared successfully.")
        except Exception as e:
            logger.error(f"Failed to clear score cache: {e}")
            raise
