import shutil
import sys
import os
import urllib.parse
import logging
import functools
from pathlib import Path
from collections import Counter
from typing import List, Tuple, Optional
from PIL import Image
logger = logging.getLogger(__name__)

try:
    import rawpy
except ImportError:
    rawpy = None


def resolve_path(path_str: str | Path) -> Path:
    """
    Resolves a path string to a pathlib.Path object.
    Supports resolving smb:// URLs to local mount points on Linux (GVFS) and macOS.

    Args:
        path_str: The input path string or Path object (e.g., '/tmp/test' or 'smb://server/share/path')

    Returns:
        Path object pointing to the local file system location.
    """
    if isinstance(path_str, Path):
        path_str = str(path_str)
    # Check if it looks like an SMB URL
    if path_str.startswith("smb://"):
        # Parse the URL
        parsed = urllib.parse.urlparse(path_str)
        server = parsed.hostname
        # Path usually comes as '/share/folder/file'
        # We need to strip the leading slash to split easily, but keep it for logic
        full_path = parsed.path
        if not full_path:
            return Path(path_str)  # Should probably just return as is if malformed

        # Unquote to handle spaces (%20)
        full_path_decoded = urllib.parse.unquote(full_path)

        # Split into share and relative path
        # full_path_decoded starts with /, e.g. /private/Bilder_Alben
        parts = full_path_decoded.strip("/").split("/", 1)
        share_name = parts[0]
        remainder = parts[1] if len(parts) > 1 else ""

        if sys.platform == "linux":
            # GVFS mount point pattern: /run/user/<uid>/gvfs/smb-share:server=<server>,share=<share>/<remainder>
            try:
                uid = os.getuid()
                gvfs_root = Path(f"/run/user/{uid}/gvfs")

                # Construct the directory name.
                # Note: commas in server or share names might need escaping in theory,
                # but standard GVFS behavior for simple names is server=<server>,share=<share>
                # We assume standard behavior.
                mount_dir_name = f"smb-share:server={server},share={share_name}"

                potential_path = gvfs_root / mount_dir_name
                if remainder:
                    potential_path = potential_path / remainder

                return potential_path
            except AttributeError:
                # os.getuid might not be available on Windows, but we are in linux block
                pass

        elif sys.platform == "darwin":
            # macOS mount point pattern: /Volumes/<share>/<remainder>
            # macOS typically mounts using just the share name in /Volumes
            potential_path = Path(f"/Volumes/{share_name}")
            if remainder:
                potential_path = potential_path / remainder
            return potential_path

    # Default: treat as local path
    return Path(path_str)


@functools.lru_cache()
def get_exiftool_path() -> str | None:
    """
    Returns the path to the exiftool executable.

    It checks:
    1. System PATH.
    2. A 'bin' directory adjacent to the executable (bundled).
    3. The PyInstaller temp directory (sys._MEIPASS).
    """
    # Check system PATH first
    if shutil.which("exiftool"):
        return "exiftool"

    # Check for bundled executable
    # If running as a PyInstaller bundle
    if getattr(sys, "frozen", False):
        base_path = Path(sys._MEIPASS)
    else:
        # If running from source, check a 'bin' folder in the package
        base_path = Path(__file__).parent / "bin"

    # Determine executable name based on OS
    exe_name = "exiftool.exe" if sys.platform == "win32" else "exiftool"

    potential_path = base_path / exe_name

    if potential_path.exists():
        return str(potential_path)

    # Also check if it's just 'exiftool' without extension on Linux/Mac
    potential_path_no_ext = base_path / "exiftool"
    if potential_path_no_ext.exists():
        return str(potential_path_no_ext)

    return None


def _get_focal_length_groups(unique_fls: List[float], threshold: float) -> List[List[float]]:
    groups = []
    if not unique_fls:
        return groups

    current_group = [unique_fls[0]]

    for fl in unique_fls[1:]:
        if (fl - current_group[0]) / current_group[0] <= threshold:
            current_group.append(fl)
        else:
            groups.append(current_group)
            current_group = [fl]
    groups.append(current_group)
    return groups


def _find_best_threshold(unique_fls: List[float], max_buckets: int) -> float:
    low = 0.0
    high = 2.0  # Allow up to 200% difference
    best_threshold = high

    for _ in range(20):
        mid = (low + high) / 2
        groups = _get_focal_length_groups(unique_fls, mid)
        if len(groups) <= max_buckets:
            best_threshold = mid
            high = mid
        else:
            low = mid

    return best_threshold


def _format_focal_length_label(min_fl: float, max_fl: float) -> str:
    def fmt(v):
        return f"{int(v)}" if v.is_integer() else f"{v:.1f}".rstrip("0").rstrip(".")

    if min_fl == max_fl:
        return f"{fmt(min_fl)} mm"

    if fmt(min_fl) == fmt(max_fl):
        return f"{fmt(min_fl)} mm"

    return f"{fmt(min_fl)}-{fmt(max_fl)} mm"


def _generate_exact_buckets(unique_fls: List[float], counts: Counter) -> List[Tuple[str, int, float]]:
    """Generates buckets with exact focal length values when no aggregation is needed."""
    result = []
    for fl in unique_fls:
        label = f"{int(fl)} mm" if fl.is_integer() else f"{fl:.1f} mm"
        result.append((label, counts[fl], fl))
    return result


def _generate_aggregated_buckets(unique_fls: List[float], counts: Counter, max_buckets: int) -> List[Tuple[str, int, float]]:
    """Generates aggregated buckets when there are too many unique focal lengths."""
    best_threshold = _find_best_threshold(unique_fls, max_buckets)
    final_groups = _get_focal_length_groups(unique_fls, best_threshold)

    result = []
    for group in final_groups:
        group_count = sum(counts[fl] for fl in group)
        min_fl = min(group)
        max_fl = max(group)
        label = _format_focal_length_label(min_fl, max_fl)

        result.append((label, group_count, min_fl))

    return result


def aggregate_focal_lengths(
    focal_lengths: List[float], max_buckets: int = 25
) -> List[Tuple[str, int, float]]:
    """
    Aggregates focal lengths into buckets based on percentage difference.

    Args:
        focal_lengths: List of focal length values.
        max_buckets: Maximum number of buckets to create.

    Returns:
        List of tuples (label, count, sort_key).
        - label: String representation (e.g., "50 mm" or "24-28 mm").
        - count: Number of items in this bucket.
        - sort_key: The representative value for sorting (e.g., min value of the bucket).
    """
    if not focal_lengths:
        return []

    # Filter out focal lengths <= 0
    valid_focal_lengths = [fl for fl in focal_lengths if fl > 0]
    if not valid_focal_lengths:
        return []

    # Count exact values first
    counts = Counter(valid_focal_lengths)

    unique_fls = sorted(counts.keys())

    if len(unique_fls) <= max_buckets:
        # No aggregation needed
        return _generate_exact_buckets(unique_fls, counts)

    return _generate_aggregated_buckets(unique_fls, counts, max_buckets)


def load_image_preview(
    path: Path, max_size: Tuple[int, int] = (150, 150), full_res: bool = False
) -> Optional[Image.Image]:
    """
    Loads an image for preview, handling both standard formats (via Pillow)
    and RAW formats (via rawpy). Resizes the image to fit within max_size.

    Args:
        path: Path to the image file.
        max_size: Tuple (width, height) for thumbnail size.
        full_res: If True, loads the full resolution image (ignoring max_size).

    Returns:
        PIL Image object or None if loading fails.
    """
    try:
        from photo_selector_toolbox.reader import RAW_EXTENSIONS
        ext = path.suffix.lower()
        img = None

        # Try rawpy for known RAW extensions
        if (ext in RAW_EXTENSIONS or ext in {".tif", ".tiff"}) and rawpy is not None:
            try:
                with rawpy.imread(str(path)) as raw:
                    # Fast processing for preview: half size, auto bright
                    # If full_res, disable half_size
                    rgb = raw.postprocess(
                        use_camera_wb=True, bright=1.0, half_size=not full_res
                    )
                    img = Image.fromarray(rgb)
            except (rawpy.LibRawError, OSError, ValueError) as e:
                # Catch common rawpy failures and fall through to Pillow
                logger.debug("rawpy failed to load %s: %s", path, e)

        # Fallback to Pillow if not RAW or rawpy failed
        if img is None:
            img = Image.open(path)
            img = img.convert("RGB")  # REQUIRED — prevents I;16 crashes in ImageTk

        # Resize (thumbnail modifies in-place)
        if not full_res:
            img.thumbnail(max_size)
        return img

    except (Image.UnidentifiedImageError, OSError, ValueError) as e:
        # Catch common image loading/processing errors
        logger.warning(f"Failed to load image preview for {path}: {e}")
        return None


def is_excluded_subfolder(file_path: Path, root_path: Path) -> bool:
    """
    Checks if a file_path is located inside a subfolder named 'Selection' or 'Selected'
    under root_path, to exclude it from scanning.
    But if the root_path itself is named 'Selection' or 'Selected' (e.g. specifically selected),
    it is not excluded.
    """
    try:
        relative = file_path.relative_to(root_path)
        excluded_names = {"selection", "selected"}
        try:
            from photo_selector_toolbox.ollama_tool import load_config
            config = load_config()
            custom_folder = config.get("selection_folder", "Selection")
            custom_path = Path(custom_folder)
            if not custom_path.is_absolute():
                excluded_names.add(custom_path.name.lower())
        except Exception:
            pass

        # Check all parts of the relative path except the last one (the filename)
        for part in relative.parts[:-1]:
            if part.lower() in excluded_names:
                return True
    except ValueError:
        pass
    return False


def calculate_dhash(image: Image.Image, hash_size: int = 8) -> int:
    """
    Calculates the difference hash (dHash) of a PIL Image.

    Args:
        image: The PIL Image to hash.
        hash_size: The grid size. The image will be resized to (hash_size + 1) x hash_size.

    Returns:
        The 64-bit integer hash representing the image.
    """
    # Resize to (hash_size + 1) x hash_size, convert to grayscale (L)
    resized = image.resize((hash_size + 1, hash_size), Image.Resampling.BILINEAR).convert('L')
    pixels = list(resized.getdata())

    diff = []
    for row in range(hash_size):
        for col in range(hash_size):
            pixel_left = pixels[row * (hash_size + 1) + col]
            pixel_right = pixels[row * (hash_size + 1) + col + 1]
            diff.append(pixel_left > pixel_right)

    # Convert binary array to integer
    decimal_value = 0
    for value in diff:
        decimal_value = (decimal_value << 1) | value
    return decimal_value


def group_files_by_similarity(files: List[Path], files_map, threshold: int = 10) -> List[List[Path]]:
    """
    Groups consecutive image files that are visually similar based on their dHash Hamming distance.

    Args:
        files: A list of Paths sorted alphabetically/chronologically.
        files_map: A dict mapping Path to ScanResult, which has a scores dict.
        threshold: Hamming distance threshold (<= threshold means similar).

    Returns:
        A list of groups, where each group is a list of Paths.
    """
    if not files:
        return []

    groups = []
    current_group = [files[0]]

    for next_file in files[1:]:
        prev_file = current_group[-1]

        prev_res = files_map.get(prev_file)
        next_res = files_map.get(next_file)

        prev_dhash = prev_res.scores.get("dhash") if prev_res else None
        next_dhash = next_res.scores.get("dhash") if next_res else None

        similar = False
        if prev_dhash is not None and next_dhash is not None:
            try:
                # Convert hex string back to int if needed
                h1 = int(prev_dhash, 16) if isinstance(prev_dhash, str) else int(prev_dhash)
                h2 = int(next_dhash, 16) if isinstance(next_dhash, str) else int(next_dhash)

                # Hamming distance: number of set bits in XOR
                dist = bin(h1 ^ h2).count('1')
                if dist <= threshold:
                    similar = True
            except (ValueError, TypeError):
                pass

        if similar:
            current_group.append(next_file)
        else:
            groups.append(current_group)
            current_group = [next_file]

    groups.append(current_group)
    return groups


def select_representative(group_files: List[Path], files_map) -> Path:
    """
    Selects the best representative image in a group.
    Prefers the one with the highest sharpness score, falling back to the first file in the group.

    Args:
        group_files: List of Paths in the group.
        files_map: Dict mapping Path to ScanResult.

    Returns:
        The Path of the selected representative image.
    """
    if not group_files:
        raise ValueError("Cannot select representative from empty group")

    best_path = group_files[0]
    best_score = -1.0

    for path in group_files:
        res = files_map.get(path)
        if res:
            score_val = res.scores.get("sharpness")
            if isinstance(score_val, (int, float)):
                if float(score_val) > best_score:
                    best_score = float(score_val)
                    best_path = path

    return best_path


def create_placeholder_image(width: int, height: int, text: str) -> Image.Image:
    """
    Dynamically generates a modern, beautiful dark-gradient placeholder image
    with a camera icon outline and text below it.
    """
    from PIL import ImageDraw

    # Create base image with Zinc-900 base color
    img = Image.new("RGB", (width, height), color="#18181B")
    draw = ImageDraw.Draw(img)

    # Draw simple gradient by interpolation
    # Background gradient: #1E1E24 (dark charcoal) to #121214 (deep charcoal)
    c1 = (30, 30, 36)
    c2 = (18, 18, 20)
    for y in range(height):
        ratio = y / max(1, height - 1)
        r = int(c1[0] * (1 - ratio) + c2[0] * ratio)
        g = int(c1[1] * (1 - ratio) + c2[1] * ratio)
        b = int(c1[2] * (1 - ratio) + c2[2] * ratio)
        draw.line([(0, y), (width, y)], fill=(r, g, b))

    # Draw subtle inner border
    border_color = (63, 63, 70)  # Zinc-700
    draw.rectangle([(5, 5), (width - 6, height - 6)], outline=border_color, width=1)

    # Draw simple camera icon in the center
    # Calculate center coordinates
    cx = width // 2
    cy = height // 2 - 20  # slightly shifted up to make room for text below

    # Camera body dimensions
    cam_w, cam_h = 60, 40

    # Camera lens dimensions
    lens_r = 16

    # Draw camera body
    # Base rectangle
    draw.rectangle(
        [(cx - cam_w // 2, cy - cam_h // 2), (cx + cam_w // 2, cy + cam_h // 2)],
        outline=(161, 161, 170),  # Zinc-400
        width=3
    )
    # Camera flash/top prism shape
    draw.polygon(
        [
            (cx - 15, cy - cam_h // 2),
            (cx - 10, cy - cam_h // 2 - 8),
            (cx + 10, cy - cam_h // 2 - 8),
            (cx + 15, cy - cam_h // 2)
        ],
        outline=(161, 161, 170),
        fill=(30, 30, 36),
        width=3
    )
    # Camera lens (circle)
    draw.ellipse(
        [(cx - lens_r, cy - lens_r), (cx + lens_r, cy + lens_r)],
        outline=(161, 161, 170),
        width=3
    )

    # Centering text helper
    try:
        if hasattr(draw, "textbbox"):
            bbox = draw.textbbox((0, 0), text)
            tw = bbox[2] - bbox[0]
        elif hasattr(draw, "textsize"):
            tw, _ = draw.textsize(text)
        else:
            tw = len(text) * 6
    except Exception:
        tw = len(text) * 6

    tx = cx - tw // 2
    ty = cy + cam_h // 2 + 15
    draw.text((tx, ty), text, fill=(244, 244, 245))

    return img



