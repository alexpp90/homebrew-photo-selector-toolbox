import logging
from collections import Counter
import statistics
from typing import Any, Dict
from photo_selector_toolbox.utils import aggregate_focal_lengths
from photo_selector_toolbox.models import ExifData

logger = logging.getLogger(__name__)


def analyze_data(data: list[ExifData]):
    """Prints a formatted statistical summary of the metadata to stdout."""
    logger.info("Total images with EXIF data analyzed: %d", len(data))
    print("\n--- Image Metadata Analysis ---")
    print(f"Total images with EXIF data analyzed: {len(data)}")

    if not data:
        logger.info("No data to analyze")
        print("No data to analyze.")
        return

    # Calculate fallback statistics
    fallback_count = sum(1 for d in data if d.is_fallback)
    fallback_percent = (fallback_count / len(data)) * 100
    if fallback_count > 0:
        print(
            f"Images using fallback focal length (original): {fallback_count} ({fallback_percent:.1f}%)"
        )
    else:
        print("All images had valid 35mm equivalent focal length metadata.")

    print("\n--- Basic Statistics ---")

    attr_map = {
        "Shutter Speed": "shutter_speed",
        "Aperture": "aperture",
        "Focal Length": "focal_length",
        "Focal Length (35mm)": "focal_length_35mm",
        "ISO": "iso",
        "Lens": "lens",
    }

    # Helper to extract values
    def get_values(key):
        attr_name = attr_map[key]
        return [getattr(d, attr_name) for d in data if getattr(d, attr_name) is not None]

    for key in ["Shutter Speed", "Aperture", "Focal Length", "ISO"]:
        values = get_values(key)
        if values:
            print(f"\n{key}:")
            print(f"  Count: {len(values)}")
            print(f"  Mean:  {statistics.mean(values):.2f}")
            if len(values) > 1:
                print(f"  Std:   {statistics.stdev(values):.2f}")
            print(f"  Min:   {min(values)}")
            print(f"  Max:   {max(values)}")
        else:
            print(f"\n{key}: No data")

    print("\n--- Most Common Settings ---")

    print("\nTop 5 Lenses:")
    logger.info("Top 5 Lenses:")
    lenses = get_values("Lens")
    for name, count in Counter(lenses).most_common(5):
        logger.info("  %s: %d", name, count)
        print(f"  {name}: {count}")

    print("\n\nTop Focal Lengths (mm):")
    focal_lengths = get_values("Focal Length")
    aggregated_fls = aggregate_focal_lengths(focal_lengths)
    aggregated_fls.sort(key=lambda x: x[1], reverse=True)
    for label, count, _ in aggregated_fls[:15]:
        print(f"  {label}: {count}")

    print("\n\nTop 15 Equivalent Focal Lengths (35mm):")
    focal_lengths_35 = get_values("Focal Length (35mm)")
    focal_lengths_35_rounded = [int(round(fl)) for fl in focal_lengths_35]
    for fl, count in Counter(focal_lengths_35_rounded).most_common(15):
        print(f"  {fl}mm: {count}")

    print("\n\nTop 15 Equivalent Focal Lengths (APS-C):")
    focal_lengths_apsc = [int(round(fl / 1.5)) for fl in focal_lengths_35]
    for fl, count in Counter(focal_lengths_apsc).most_common(15):
        print(f"  {fl}mm: {count}")

    print("\n\nTop 25 Aperture & Focal Length Combinations:")
    combinations = []
    for d in data:
        if d.aperture is not None and d.focal_length is not None:
            combinations.append((d.aperture, d.focal_length))

    for (ap, fl), count in Counter(combinations).most_common(25):
        fl_str = f"{int(fl)}" if fl.is_integer() else f"{fl:.1f}"
        logger.info("  f/%s @ %smm: %d", ap, fl_str, count)
        print(f"  f/{ap} @ {fl_str}mm: {count}")

    print("\n\nTop 5 Apertures (f-stop):")
    apertures = get_values("Aperture")
    apertures_display = [int(ap) if ap.is_integer() else ap for ap in apertures]
    for ap, count in Counter(apertures_display).most_common(5):
        print(f"  {ap}: {count}")

    print("\n\nTop 5 ISOs:")
    logger.info("Top 5 ISOs:")
    isos = get_values("ISO")
    isos_display = [int(iso) if iso.is_integer() else iso for iso in isos]
    for iso, count in Counter(isos_display).most_common(5):
        logger.info("  %s: %d", iso, count)
        print(f"  {iso}: {count}")
    print("\n----------------------------")


def analyze_data_json(data: list[ExifData]) -> Dict[str, Any]:
    """Returns analysis results as a JSON-serializable dictionary."""
    if not data:
        return {"total_images": 0}

    attr_map = {
        "shutter_speed": "shutter_speed",
        "aperture": "aperture",
        "focal_length": "focal_length",
        "focal_length_35mm": "focal_length_35mm",
        "iso": "iso",
        "lens": "lens",
    }

    def get_values(attr_name):
        return [getattr(d, attr_name) for d in data if getattr(d, attr_name) is not None]

    result: Dict[str, Any] = {
        "total_images": len(data),
        "fallback_count": sum(1 for d in data if d.is_fallback),
        "statistics": {},
        "distributions": {},
    }

    for key in ["shutter_speed", "aperture", "focal_length", "iso"]:
        values = get_values(attr_map[key])
        if values:
            stats: Dict[str, Any] = {
                "count": len(values),
                "mean": round(statistics.mean(values), 4),
                "min": min(values),
                "max": max(values),
            }
            if len(values) > 1:
                stats["stdev"] = round(statistics.stdev(values), 4)
            result["statistics"][key] = stats

            # Distribution (top 25)
            counter = Counter(values)
            result["distributions"][key] = [
                {"value": v, "count": c} for v, c in counter.most_common(25)
            ]

    # Lens distribution
    lenses = get_values("lens")
    result["distributions"]["lens"] = [
        {"value": name, "count": c} for name, c in Counter(lenses).most_common()
    ]

    # Combinations
    combos = []
    for d in data:
        if d.aperture is not None and d.focal_length is not None:
            combos.append(f"f/{d.aperture}@{d.focal_length}mm")
    result["distributions"]["aperture_focal_combinations"] = [
        {"value": combo, "count": c} for combo, c in Counter(combos).most_common(25)
    ]

    return result
