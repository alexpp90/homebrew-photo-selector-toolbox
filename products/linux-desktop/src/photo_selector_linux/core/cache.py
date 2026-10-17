"""Bounded in-memory LRU decoded image cache.

Adheres to:
- R-LINUX-CACHE-01: Bounded In-Memory LRU Cache (OrderedDict, default 512 MB ceiling)
- R-LINUX-CACHE-02: Asymmetric Sliding Prefetch Window ([i-2..i+3] forward, [i-3..i+2] backward)
- R-LINUX-CACHE-03: Modal Teardown & Memory Reclamation
"""

from __future__ import annotations

import sys
import threading
from collections import OrderedDict
from pathlib import Path
from typing import Any, Optional, Set, Tuple


class ThumbnailCache:
    """Thread-safe bounded LRU in-memory cache tracking byte footprints (R-LINUX-CACHE-01)."""

    def __init__(self, max_mb: float = 512, max_memory_mb: Optional[float] = None) -> None:
        self._lock = threading.RLock()
        self._store: OrderedDict[Any, Tuple[Any, int]] = OrderedDict()
        self._current_bytes: int = 0
        limit_mb = max_memory_mb if max_memory_mb is not None else max_mb
        self.max_mb: float = float(limit_mb)
        self.max_bytes: int = max(0, int(self.max_mb * 1024 * 1024))

    @property
    def current_bytes(self) -> int:
        """Total byte footprint of all currently cached entries."""
        with self._lock:
            return self._current_bytes

    @property
    def count(self) -> int:
        """Total number of entries currently in the cache."""
        with self._lock:
            return len(self._store)

    @property
    def entry_count(self) -> int:
        return self.count

    def __len__(self) -> int:
        return self.count

    def __contains__(self, key: Any) -> bool:
        with self._lock:
            return key in self._store

    @staticmethod
    def _estimate_size_bytes(value: Any) -> int:
        """Estimate byte footprint of a cached thumbnail or decoded buffer."""
        if value is None:
            return 0
        if isinstance(value, (bytes, bytearray, memoryview)):
            return len(value)
        if hasattr(value, "nbytes"):
            try:
                return int(value.nbytes)
            except (TypeError, ValueError):
                pass
        if hasattr(value, "get_byte_length"):
            try:
                return int(value.get_byte_length())
            except (TypeError, ValueError):
                pass
        return max(1, sys.getsizeof(value))

    def get(self, key: Any) -> Optional[Any]:
        """Retrieve cached item and promote it to most-recently-used position."""
        with self._lock:
            if key not in self._store:
                return None
            self._store.move_to_end(key)
            value, _ = self._store[key]
            return value

    def put(
        self,
        key: Any,
        value: Any,
        size_bytes: Optional[int] = None,
        byte_size: Optional[int] = None,
        is_full_res: Optional[bool] = None,
    ) -> None:
        """Insert or update a cached item and evict oldest entries if over max_bytes."""
        explicit_size = byte_size if byte_size is not None else size_bytes
        sz = int(explicit_size) if explicit_size is not None else self._estimate_size_bytes(value)
        sz = max(0, sz)

        with self._lock:
            if key in self._store:
                _, old_sz = self._store.pop(key)
                self._current_bytes = max(0, self._current_bytes - old_sz)

            self._store[key] = (value, sz)
            self._store.move_to_end(key)
            self._current_bytes += sz
            self.evict_to_limit()

    def evict_to_limit(self) -> int:
        """Evict least-recently-used entries until current_bytes <= max_bytes."""
        with self._lock:
            evicted = 0
            while self._current_bytes > self.max_bytes and self._store:
                _, (_, sz) = self._store.popitem(last=False)
                self._current_bytes = max(0, self._current_bytes - sz)
                evicted += 1
            return evicted

    def set_limit_mb(self, max_mb: float) -> int:
        """Update memory ceiling in megabytes and immediately evict excess entries."""
        with self._lock:
            self.max_mb = max(0.0, float(max_mb))
            self.max_bytes = max(0, int(self.max_mb * 1024 * 1024))
            return self.evict_to_limit()

    def remove(self, key: Any) -> bool:
        """Remove a specific key from the cache."""
        with self._lock:
            if key in self._store:
                _, sz = self._store.pop(key)
                self._current_bytes = max(0, self._current_bytes - sz)
                return True
            return False

    def evict_outside_window(self, keep_paths: Set[Path]) -> int:
        """Reclaim memory for any cached entries whose path is outside keep_paths (R-LINUX-CACHE-03)."""
        resolved_keep = {Path(p).resolve() for p in keep_paths}
        with self._lock:
            keys_to_remove = []
            for key in self._store.keys():
                key_path = key[0] if isinstance(key, tuple) and len(key) > 0 else key
                if isinstance(key_path, (str, Path)):
                    if Path(key_path).resolve() not in resolved_keep:
                        keys_to_remove.append(key)
            for key in keys_to_remove:
                _, sz = self._store.pop(key)
                self._current_bytes = max(0, self._current_bytes - sz)
            return len(keys_to_remove)

    def evict_full_resolution(self, keep_paths: Optional[Set[Path]] = None) -> int:
        """Reclaim full-resolution (max_edge=None) buffers outside keep_paths (R-LINUX-CACHE-03)."""
        resolved_keep = {Path(p).resolve() for p in (keep_paths or set())}
        with self._lock:
            keys_to_remove = []
            for key in self._store.keys():
                if isinstance(key, tuple) and len(key) == 2 and key[1] is None:
                    key_path = key[0]
                    if isinstance(key_path, (str, Path)):
                        if Path(key_path).resolve() not in resolved_keep:
                            keys_to_remove.append(key)
                    else:
                        keys_to_remove.append(key)
            for key in keys_to_remove:
                _, sz = self._store.pop(key)
                self._current_bytes = max(0, self._current_bytes - sz)
            return len(keys_to_remove)

    def clear(self) -> None:
        """Release all cached buffers immediately (R-LINUX-CACHE-03)."""
        with self._lock:
            self._store.clear()
            self._current_bytes = 0
