"""Unit tests for the live decoded image cache and asymmetric sliding prefetch window.

Adheres to:
- R-LINUX-CACHE-01: Bounded In-Memory LRU Cache (default 512 MB)
- R-LINUX-CACHE-02: Asymmetric Sliding Prefetch Window ([i-2..i+3] forward, [i-3..i+2] backward)
- R-LINUX-CACHE-03: Modal Teardown & Memory Reclamation
"""

from pathlib import Path
from unittest.mock import MagicMock

from photo_selector_linux.core.cache import ThumbnailCache
from photo_selector_linux.core.models import CandidatePhoto
from photo_selector_linux.ui.canvas import CanvasView
from photo_selector_linux.ui.photo_loader import PhotoTextureLoader
from photo_selector_linux.viewmodels.culling_workspace import CullingWorkspaceViewModel


def _make_photo(name: str) -> CandidatePhoto:
    return CandidatePhoto(primary_path=Path(f"/tmp/test_cache/{name}"))


def test_lru_cache_eviction():
    """Verify ThumbnailCache and PhotoTextureLoader enforce bounded LRU eviction (R-LINUX-CACHE-01)."""
    loader = PhotoTextureLoader()
    assert loader.cache.max_bytes == 512 * 1024 * 1024

    # Small cache holding at most 10 MB
    cache = ThumbnailCache(max_memory_mb=10.0)
    mb = 1024 * 1024
    cache.put("p1", "tex1", byte_size=4 * mb)
    cache.put("p2", "tex2", byte_size=4 * mb)
    assert cache.entry_count == 2
    assert cache.current_bytes == 8 * mb

    # Touch p1 so p2 becomes the oldest LRU entry
    assert cache.get("p1") == "tex1"

    # Inserting p3 (4 MB) exceeds 10 MB -> p2 is evicted, p1 and p3 remain
    cache.put("p3", "tex3", byte_size=4 * mb)
    assert cache.get("p2") is None
    assert cache.get("p1") == "tex1"
    assert cache.get("p3") == "tex3"
    assert cache.current_bytes == 8 * mb

    # Shrinking the limit via PhotoTextureLoader.set_limit_mb evicts oldest entries immediately
    loader._cache = cache
    loader.set_limit_mb(5.0)
    assert cache.max_bytes == 5 * mb
    assert cache.get("p1") is None
    assert cache.get("p3") == "tex3"
    assert cache.current_bytes == 4 * mb


def test_sliding_prefetch_window():
    """Verify CanvasView._prefetch_neighbours uses asymmetric window [i-2..i+3] / [i-3..i+2] (R-LINUX-CACHE-02)."""
    vm = CullingWorkspaceViewModel()
    vm.candidates = [_make_photo(f"DSC_{i:04d}.JPG") for i in range(12)]
    loader = MagicMock()
    canvas = CanvasView(viewmodel=vm, loader=loader)

    # Navigating forward to index 5: window is [3, 4, 5, 6, 7, 8] (excluding active index 5 -> [3, 4, 6, 7, 8])
    vm.current_index = 5
    loader.reset_mock()
    canvas._prefetch_neighbours()
    forward_prefetched = [call.args[0].filename for call in loader.prefetch.call_args_list]
    assert forward_prefetched == [
        "DSC_0003.JPG",
        "DSC_0004.JPG",
        "DSC_0006.JPG",
        "DSC_0007.JPG",
        "DSC_0008.JPG",
    ]
    loader.evict_outside_window.assert_called_once()
    keep_forward = {p.name for p in loader.evict_outside_window.call_args.args[0]}
    assert keep_forward == {
        "DSC_0003.JPG",
        "DSC_0004.JPG",
        "DSC_0005.JPG",
        "DSC_0006.JPG",
        "DSC_0007.JPG",
        "DSC_0008.JPG",
    }

    # Navigating backward from 5 to 4: window is [1, 2, 3, 4, 5, 6] (excluding active index 4 -> [1, 2, 3, 5, 6])
    vm.current_index = 4
    loader.reset_mock()
    canvas._prefetch_neighbours()
    backward_prefetched = [call.args[0].filename for call in loader.prefetch.call_args_list]
    assert backward_prefetched == [
        "DSC_0001.JPG",
        "DSC_0002.JPG",
        "DSC_0003.JPG",
        "DSC_0005.JPG",
        "DSC_0006.JPG",
    ]
    keep_backward = {p.name for p in loader.evict_outside_window.call_args.args[0]}
    assert keep_backward == {
        "DSC_0001.JPG",
        "DSC_0002.JPG",
        "DSC_0003.JPG",
        "DSC_0004.JPG",
        "DSC_0005.JPG",
        "DSC_0006.JPG",
    }


def test_cache_teardown_reclamation(monkeypatch):
    """Verify full-res and out-of-window buffers are reclaimed on zoom reset and teardown (R-LINUX-CACHE-03)."""
    loader = PhotoTextureLoader(max_mb=512.0)
    p1 = _make_photo("IMG_0001.ARW")
    p2 = _make_photo("IMG_0002.ARW")
    p3 = _make_photo("IMG_0003.ARW")

    mb = 1024 * 1024
    loader.cache.put(loader._cache_key(p1, False), "fit-1", byte_size=8 * mb, is_full_res=False)
    loader.cache.put(loader._cache_key(p1, True), "full-1", byte_size=96 * mb, is_full_res=True)
    loader.cache.put(loader._cache_key(p2, True), "full-2", byte_size=96 * mb, is_full_res=True)
    loader.cache.put(loader._cache_key(p3, False), "fit-3", byte_size=8 * mb, is_full_res=False)

    # Reclaim full-resolution buffers outside p1
    evicted = loader.evict_full_resolution(keep_photos=[p1])
    assert evicted == 1
    assert loader.cache.get(loader._cache_key(p2, True)) is None
    assert loader.cache.get(loader._cache_key(p1, True)) == "full-1"

    # The cache holds string stand-ins for textures; a real Gtk.Picture rejects them, and rendering
    # is not what this test is about (it checks buffer reclamation).
    monkeypatch.setattr(CanvasView, "_on_texture_ready", lambda *args, **kwargs: None)

    # Resetting 1:1 zoom on CanvasView reclaims all full-resolution buffers
    vm = CullingWorkspaceViewModel()
    vm.candidates = [p1, p2, p3]
    canvas = CanvasView(viewmodel=vm, loader=loader)
    canvas.toggle_zoom()
    assert canvas.is_1to1_zoom is True
    canvas.reset_zoom()
    assert canvas.is_1to1_zoom is False
    assert loader.cache.get(loader._cache_key(p1, True)) is None
    assert loader.cache.get(loader._cache_key(p1, False)) == "fit-1"

    # Evicting outside active window drops fit-3 when only p1 is kept
    loader.evict_outside_window([p1.primary_path])
    assert loader.cache.get(loader._cache_key(p3, False)) is None
    assert loader.cache.get(loader._cache_key(p1, False)) == "fit-1"
