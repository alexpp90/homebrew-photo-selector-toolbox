import logging
import queue
import threading
import os
from concurrent.futures import ProcessPoolExecutor, as_completed
import gc
from collections import OrderedDict
from typing import Dict, Optional, Callable, List, Union
from pathlib import Path
from PIL import Image

from photo_selector_toolbox.core.utils import load_image_preview
from photo_selector_toolbox.core.models import ScanResult
from photo_selector_toolbox.exif.reader import get_exif_data
from photo_selector_toolbox.tools.registry import ToolRegistry
from photo_selector_toolbox.core.cache import ScoreCache
import photo_selector_toolbox.core.sharpness
# Registers the dispatcher "aesthetic" tool (Apple Vision / NIMA ONNX / Ollama).
import photo_selector_toolbox.tools.aesthetic

logger = logging.getLogger(__name__)


class ImageCacheManager:
    """
    Manages background loading and caching of image previews and full-resolution images.
    Decouples threading and cache state from the GUI with LRU eviction and memory bounds.
    """

    def __init__(
        self,
        preview_cache_limit: int = 30,
        full_res_cache_limit: int = 3,
        preview_size: tuple[int, int] = (800, 800),
    ):
        self.preview_cache_limit = preview_cache_limit
        self.full_res_cache_limit = full_res_cache_limit
        self.preview_size = preview_size

        # Queues
        self.preview_queue: queue.Queue = queue.Queue()
        self.full_res_queue: queue.Queue = queue.Queue()

        # Caches with LRU ordering
        self.preview_cache: OrderedDict[Path, Image.Image] = OrderedDict()
        self.full_res_cache: OrderedDict[Path, Image.Image] = OrderedDict()

        # Locks
        self.preview_lock = threading.Lock()
        self.full_res_lock = threading.Lock()

        # Threads
        self.preview_thread: Optional[threading.Thread] = None
        self.full_res_thread: Optional[threading.Thread] = None

        self._start_threads()

    def _start_threads(self) -> None:
        self.preview_thread = threading.Thread(
            target=self._run_preview_worker, daemon=True
        )
        self.full_res_thread = threading.Thread(
            target=self._run_full_res_worker, daemon=True
        )
        # Ensure they are not None before starting (satisfy type checker)
        if self.preview_thread:
            self.preview_thread.start()
        if self.full_res_thread:
            self.full_res_thread.start()

    def queue_preview(self, path: Path):
        self.preview_queue.put(path)

    def queue_full_res(self, path: Path):
        self.full_res_queue.put(path)

    def get_preview(self, path: Path) -> Optional[Image.Image]:
        with self.preview_lock:
            img = self.preview_cache.get(path)
            if img is not None and isinstance(self.preview_cache, OrderedDict):
                self.preview_cache.move_to_end(path)
            return img

    def put_preview(self, path: Path, img: Image.Image) -> None:
        """Stores a preview image and evicts LRU items exceeding limit, closing image resources."""
        with self.preview_lock:
            self.preview_cache[path] = img
            if isinstance(self.preview_cache, OrderedDict):
                self.preview_cache.move_to_end(path)
            while len(self.preview_cache) > self.preview_cache_limit:
                if isinstance(self.preview_cache, OrderedDict):
                    _, oldest_img = self.preview_cache.popitem(last=False)
                else:
                    first = next(iter(self.preview_cache))
                    oldest_img = self.preview_cache.pop(first, None)
                if oldest_img is not None:
                    try:
                        oldest_img.close()
                    except Exception:
                        pass

    def get_full_res(self, path: Path) -> Optional[Image.Image]:
        with self.full_res_lock:
            img = self.full_res_cache.get(path)
            if img is not None and isinstance(self.full_res_cache, OrderedDict):
                self.full_res_cache.move_to_end(path)
            return img

    def put_full_res(self, path: Path, img: Image.Image) -> None:
        """Stores a full-resolution image and evicts LRU items exceeding limit, closing image resources."""
        with self.full_res_lock:
            self.full_res_cache[path] = img
            if isinstance(self.full_res_cache, OrderedDict):
                self.full_res_cache.move_to_end(path)
            while len(self.full_res_cache) > self.full_res_cache_limit:
                if isinstance(self.full_res_cache, OrderedDict):
                    _, oldest_img = self.full_res_cache.popitem(last=False)
                else:
                    first = next(iter(self.full_res_cache))
                    oldest_img = self.full_res_cache.pop(first, None)
                if oldest_img is not None:
                    try:
                        oldest_img.close()
                    except Exception:
                        pass

    def clear_preview_queue(self):
        while not self.preview_queue.empty():
            try:
                self.preview_queue.get_nowait()
            except queue.Empty:
                break

    def clear_full_res_queue(self):
        while not self.full_res_queue.empty():
            try:
                self.full_res_queue.get_nowait()
            except queue.Empty:
                break

    def clear_queues(self):
        self.clear_preview_queue()
        self.clear_full_res_queue()

    def clear_previews(self):
        """Releases all cached preview images and drains preview queue."""
        with self.preview_lock:
            for img in self.preview_cache.values():
                try:
                    img.close()
                except Exception:
                    pass
            self.preview_cache.clear()
        self.clear_preview_queue()
        gc.collect()

    def clear_full_res(self):
        """Releases all cached full-resolution images and drains full-res queue."""
        with self.full_res_lock:
            for img in self.full_res_cache.values():
                try:
                    img.close()
                except Exception:
                    pass
            self.full_res_cache.clear()
        self.clear_full_res_queue()
        gc.collect()

    def clear(self):
        """Completely flushes all preview and full-res caches, closing image handles and triggering GC."""
        self.clear_previews()
        self.clear_full_res()

    def _run_preview_worker(self):
        while True:
            try:
                path = self.preview_queue.get()
                if path is None:
                    continue

                with self.preview_lock:
                    if path in self.preview_cache:
                        continue

                # Use load_image_preview from utils
                img = load_image_preview(path, max_size=self.preview_size)
                if img:
                    self.put_preview(path, img)
            except Exception as e:
                # 'path' might not be defined if get() fails
                path_str = str(path) if "path" in locals() else "unknown"
                logger.debug(f"Preview load error for {path_str}: {e}")

    def _run_full_res_worker(self):
        while True:
            try:
                path = self.full_res_queue.get()
                if path is None:
                    continue

                with self.full_res_lock:
                    if path in self.full_res_cache:
                        continue

                # Load full resolution
                img = load_image_preview(path, full_res=True)
                if img:
                    self.put_full_res(path, img)
            except Exception as e:
                path_str = str(path) if "path" in locals() else "unknown"
                logger.debug(f"Full res load error for {path_str}: {e}")


def _process_single_file(
    f: Path,
    grid_size: int,
    tools: Dict[str, bool],
    cached_scores: Optional[Dict[str, Union[float, str]]] = None,
) -> ScanResult:
    """Helper module function to process a single image for parallel execution."""
    if cached_scores is None:
        cache = ScoreCache()
        cached = cache.get_scores(f)
    else:
        cached = cached_scores

    # Initialize scores with cached values
    scores = {name: val for name, val in cached.items() if val != "N/A"}
    new_calculations = {}

    # --- Optimized path: calculate all built-in metrics with a single image load ---
    # Built-in tools that share the same image data pipeline
    BUILTIN_TOOLS = {"sharpness", "noise", "highlight_clipping", "shadow_clipping"}

    # Determine which built-in tools need computation (enabled AND not yet cached)
    builtin_to_compute = {
        name: True
        for name in BUILTIN_TOOLS
        if tools.get(name, False) and name not in scores
    }

    if builtin_to_compute:
        # Single image load → single grayscale conversion → all analyses
        combined_results = photo_selector_toolbox.core.sharpness.calculate_all_scores(
            f, grid_size=grid_size, tools=builtin_to_compute
        )
        for tool_name, val in combined_results.items():
            scores[tool_name] = val
            new_calculations[tool_name] = val

    # Mark disabled built-in tools as N/A if not already set
    for name in BUILTIN_TOOLS:
        if not tools.get(name, False) and name not in scores:
            scores[name] = "N/A"

    # --- Standard path: non-builtin tools (e.g., aesthetic/Ollama) via ToolRegistry ---
    for tool_name, enabled in tools.items():
        if tool_name in BUILTIN_TOOLS:
            continue  # Already handled above
        if enabled:
            if tool_name in scores:
                continue
            try:
                tool_class = ToolRegistry.get(tool_name)
                tool_instance = tool_class()
                val = tool_instance.analyze(f, grid_size=grid_size)
                if isinstance(val, tuple) and len(val) == 2:
                    score_val, analysis_val = val
                    scores[tool_name] = score_val
                    new_calculations[tool_name] = score_val
                    scores[f"{tool_name}_analysis"] = analysis_val
                    new_calculations[f"{tool_name}_analysis"] = analysis_val
                else:
                    scores[tool_name] = val
                    new_calculations[tool_name] = val
            except KeyError:
                logger.warning(f"Tool {tool_name} not registered in ToolRegistry.")
                scores[tool_name] = "N/A"
            except Exception as e:
                logger.error(f"Error executing tool {tool_name}: {e}")
                scores[tool_name] = "N/A"
        else:
            if tool_name not in scores:
                scores[tool_name] = "N/A"

    # Fetch EXIF
    exif = get_exif_data(f)

    return ScanResult(
        path=f,
        scores=scores,
        exif=exif,
        new_calculations=new_calculations,
    )


def _init_scan_worker() -> None:
    """Lower scheduling priority of background scan processes so GUI and preview decoding remain responsive."""
    if hasattr(os, "nice"):
        try:
            os.nice(10)
        except Exception:
            pass


class ScanController:
    """
    Manages the background execution of the image scanning and scoring pipeline.
    Decouples process management and caching logic from the GUI.
    """

    def __init__(self):
        self.is_scanning = False
        self.stop_event = threading.Event()
        self.scan_thread: Optional[threading.Thread] = None

    def start_scan(
        self,
        files: List[Path],
        grid_size: int,
        tools: Dict[str, bool],
        progress_callback: Callable[[ScanResult, int, int], None],
        finished_callback: Callable[[], None],
        log_callback: Optional[Callable[[str], None]] = None,
    ) -> bool:
        if self.is_scanning:
            return False

        self.is_scanning = True
        self.stop_event.clear()

        self.scan_thread = threading.Thread(
            target=self._scan_worker,
            args=(
                files,
                grid_size,
                tools,
                progress_callback,
                finished_callback,
                log_callback,
            ),
            daemon=True,
        )
        self.scan_thread.start()
        return True

    def stop_scan(self):
        self.stop_event.set()

    # Aliases for backwards compatibility
    run_scan = start_scan
    cancel = stop_scan

    def _scan_worker(
        self,
        files: List[Path],
        grid_size: int,
        tools: Dict[str, bool],
        progress_callback: Callable[[ScanResult, int, int], None],
        finished_callback: Callable[[], None],
        log_callback: Optional[Callable[[str], None]] = None,
    ):
        def log(msg: str):
            if log_callback:
                log_callback(msg)

        try:
            total = len(files)
            if total == 0:
                log("No images to scan.")
                return

            log(f"Scanning {total} images. Starting analysis...")

            # Pre-fetch all cached scores in a single batch
            cache = ScoreCache()
            all_cached_scores = cache.get_multiple_scores(files)

            cpu_cnt = os.cpu_count() or 4
            # Prioritize UI responsiveness by reserving at least 2 cores for Tk and preview threads
            max_workers = max(1, min(cpu_cnt - 2, 6)) if cpu_cnt > 2 else 1
            accumulated_updates = {}
            with ProcessPoolExecutor(max_workers=max_workers, initializer=_init_scan_worker) as executor:
                # Submit all tasks
                futures = {
                    executor.submit(_process_single_file, f, grid_size, tools, all_cached_scores.get(f, {})): f
                    for f in files
                }

                completed_count = 0
                for future in as_completed(futures):
                    if self.stop_event.is_set():
                        log("Scan cancelled.")
                        if accumulated_updates:
                            cache.set_multiple_scores(accumulated_updates)
                        # Attempt to cancel pending futures
                        for pending_future in futures:
                            pending_future.cancel()
                        break

                    f = futures[future]
                    log(f"Analyzed {f.name}...")

                    try:
                        res = future.result()
                        if res.new_calculations:
                            accumulated_updates[f] = res.new_calculations
                            if len(accumulated_updates) >= cache._PRUNE_INTERVAL:
                                cache.set_multiple_scores(accumulated_updates)
                                accumulated_updates.clear()

                        completed_count += 1
                        # Notify progress
                        progress_callback(res, completed_count, total)
                    except Exception as e:
                        log(f"Error processing {f.name}: {e}")
                        logger.exception(f"Error processing {f.name}")

                if accumulated_updates:
                    cache.set_multiple_scores(accumulated_updates)

            log("Scan complete.")

        except Exception as e:
            log(f"Error during scan: {e}")
            logger.exception("Scan worker error")
        finally:
            self.is_scanning = False
            finished_callback()
