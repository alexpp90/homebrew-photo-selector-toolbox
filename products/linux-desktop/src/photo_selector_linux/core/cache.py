"""Bounded in-memory LRU decoded image cache and neighbour prefetch manager.

Adheres to:
- REQ-LINUX-CACHE.01: Bounded In-Memory LRU Cache (OrderedDict, default 512 MB ceiling)
- REQ-LINUX-CACHE.02: Sliding Prefetch Window (±2 neighbours with generation cancellation)
- REQ-LINUX-CACHE.03: Modal Teardown & Memory Reclamation
"""

from __future__ import annotations

import sys
import threading
from collections import OrderedDict
from pathlib import Path
from typing import Any, Callable, Dict, List, Optional, Sequence, Set, Tuple


class ThumbnailCache:
    """Thread-safe bounded LRU in-memory cache tracking byte footprints (REQ-LINUX-CACHE.01)."""

    def __init__(self, max_mb: float = 512) -> None:
        self._lock = threading.RLock()
        self._store: OrderedDict[Any, Tuple[Any, int]] = OrderedDict()
        self._current_bytes: int = 0
        self.max_mb: float = float(max_mb)
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

    def put(self, key: Any, value: Any, size_bytes: Optional[int] = None) -> None:
        """Insert or update a cached item and evict oldest entries if over max_bytes."""
        sz = int(size_bytes) if size_bytes is not None else self._estimate_size_bytes(value)
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
        """Reclaim memory for any cached entries whose path is outside keep_paths (REQ-LINUX-CACHE.03)."""
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

    def clear(self) -> None:
        """Release all cached buffers immediately (REQ-LINUX-CACHE.03)."""
        with self._lock:
            self._store.clear()
            self._current_bytes = 0


class PrefetchCancelToken:
    """Cancellation token tracking generation and candidate index for in-flight prefetch jobs."""

    def __init__(self, generation: int, index: int, path: Optional[Path] = None) -> None:
        self.generation: int = generation
        self.index: int = index
        self.path: Optional[Path] = path
        self._event = threading.Event()

    @property
    def is_cancelled(self) -> bool:
        return self._event.is_set()

    @property
    def cancelled(self) -> bool:
        return self._event.is_set()

    def cancel(self) -> None:
        self._event.set()


class PrefetchManager:
    """Manages ±2 neighbour prefetch window with generation-based cancellation (REQ-LINUX-CACHE.02)."""

    def __init__(
        self,
        cache: Optional[ThumbnailCache] = None,
        radius: int = 2,
        tier: Any = "preview",
        loader_fn: Optional[Callable[..., Any]] = None,
    ) -> None:
        self.cache: ThumbnailCache = cache if cache is not None else ThumbnailCache()
        self.radius: int = max(0, int(radius))
        self.tier: Any = tier
        self.loader_fn = loader_fn
        self._lock = threading.RLock()
        self._generation: int = 0
        self.current_index: int = 0
        self._tokens: Dict[int, PrefetchCancelToken] = {}

    @property
    def generation(self) -> int:
        """Current prefetch generation counter."""
        with self._lock:
            return self._generation

    @property
    def active_tokens(self) -> Dict[int, PrefetchCancelToken]:
        """Snapshot of currently active (non-cancelled) prefetch tokens by index."""
        with self._lock:
            return {idx: tok for idx, tok in self._tokens.items() if not tok.is_cancelled}

    def get_window_indices(self, current_index: int, total_count: int) -> List[int]:
        """Compute valid indices within [current_index - radius, current_index + radius]."""
        if total_count <= 0:
            return []
        clamped_idx = max(0, min(total_count - 1, int(current_index)))
        start = max(0, clamped_idx - self.radius)
        end = min(total_count - 1, clamped_idx + self.radius)
        return list(range(start, end + 1))

    def get_token(self, index: int) -> Optional[PrefetchCancelToken]:
        """Retrieve token for candidate index if tracked."""
        with self._lock:
            return self._tokens.get(index)

    def cancel_outside_window(self, current_index: int, total_count: int) -> int:
        """Cancel any tracked tokens outside [current_index - radius, current_index + radius]."""
        with self._lock:
            window_set = set(self.get_window_indices(current_index, total_count))
            cancelled_count = 0
            for idx, token in list(self._tokens.items()):
                if idx not in window_set and not token.is_cancelled:
                    token.cancel()
                    cancelled_count += 1
            return cancelled_count

    def update_window(self, current_index: int, total_count: int) -> List[int]:
        """Advance generation, cancel jobs outside the new ±2 window, and issue tokens for window."""
        with self._lock:
            self._generation += 1
            self.current_index = current_index
            window = self.get_window_indices(current_index, total_count)
            window_set = set(window)

            for idx, token in list(self._tokens.items()):
                if idx not in window_set:
                    token.cancel()

            for idx in window:
                existing = self._tokens.get(idx)
                if existing is None or existing.is_cancelled:
                    self._tokens[idx] = PrefetchCancelToken(
                        generation=self._generation,
                        index=idx,
                    )

            return window

    @staticmethod
    def _extract_path(candidate: Any) -> Path:
        if hasattr(candidate, "primary_path"):
            return Path(candidate.primary_path)
        if hasattr(candidate, "path"):
            return Path(candidate.path)
        return Path(candidate)

    def _default_load(self, path: Path) -> Tuple[Optional[bytes], int]:
        """Lightweight file header/thumbnail read for caching."""
        try:
            if not path.is_file():
                return None, 0
            size = path.stat().st_size
            with open(path, "rb") as f:
                head = f.read(min(size, 65536))
            return head, max(len(head), size)
        except OSError:
            return None, 0

    def prefetch(
        self,
        candidates: Sequence[Any],
        current_index: int,
        tier: Optional[Any] = None,
    ) -> List[int]:
        """Update ±2 prefetch window around current_index and populate ThumbnailCache."""
        total = len(candidates)
        window = self.update_window(current_index, total)
        if not window:
            return []

        use_tier = tier if tier is not None else self.tier

        # Prioritize active index first, then nearest neighbours (±1, ±2)
        prioritized = sorted(window, key=lambda i: (abs(i - current_index), i))

        for idx in prioritized:
            token = self.get_token(idx)
            if token is None or token.is_cancelled:
                continue

            path = self._extract_path(candidates[idx])
            token.path = path
            key = (path, use_tier)

            if self.cache.get(key) is not None:
                continue

            if self.loader_fn is not None:
                try:
                    result = self.loader_fn(path, token)
                except TypeError:
                    result = self.loader_fn(path)

                if token.is_cancelled:
                    continue

                if isinstance(result, tuple) and len(result) == 2:
                    payload, sz = result
                else:
                    payload, sz = result, None

                if payload is not None and not token.is_cancelled:
                    self.cache.put(key, payload, size_bytes=sz)
            else:
                payload, sz = self._default_load(path)
                if payload is not None and not token.is_cancelled:
                    self.cache.put(key, payload, size_bytes=sz)

        return window

    def cancel_all(self) -> None:
        """Cancel all in-flight prefetch tokens and increment generation."""
        with self._lock:
            self._generation += 1
            for token in self._tokens.values():
                token.cancel()
            self._tokens.clear()

    def teardown(self) -> None:
        """Cancel all prefetch work and clear cache memory (REQ-LINUX-CACHE.03)."""
        self.cancel_all()
        self.cache.clear()
