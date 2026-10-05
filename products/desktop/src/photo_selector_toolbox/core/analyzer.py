import logging
from collections import Counter
import statistics
from typing import Any, Dict
from photo_selector_toolbox.core.utils import aggregate_focal_lengths
from photo_selector_toolbox.core.models import ExifData

logger = logging.getLogger(__name__)


def _extract_metadata_single_pass(data: list[ExifData]) -> Dict[str, Any]:
    # OPTIMIZATION: Accumulate Counters in a single pass to avoid redundant list allocations.
    shutter_speeds = []
    apertures = []
    focal_lengths = []
    isos = []
    fallback_count = 0

    lens_counts = Counter()
    shutter_speed_counts = Counter()
    aperture_counts = Counter()
    aperture_display_counts = Counter()
    focal_length_counts = Counter()
    focal_length_35mm_counts = Counter()
    focal_length_apsc_counts = Counter()
    iso_counts = Counter()
    iso_display_counts = Counter()
    combination_counts = Counter()

    for d in data:
        if d.is_fallback:
            fallback_count += 1

        ss = d.shutter_speed
        if ss is not None:
            shutter_speeds.append(ss)
            shutter_speed_counts[ss] += 1

        ap = d.aperture
        if ap is not None:
            apertures.append(ap)
            aperture_counts[ap] += 1
            ap_disp = int(ap) if ap.is_integer() else ap
            aperture_display_counts[ap_disp] += 1

        fl = d.focal_length
        if fl is not None:
            focal_lengths.append(fl)
            focal_length_counts[fl] += 1

        fl35 = d.focal_length_35mm
        if fl35 is not None:
            fl35_rounded = int(round(fl35))
            focal_length_35mm_counts[fl35_rounded] += 1
            focal_length_apsc_counts[int(round(fl35 / 1.5))] += 1

        iso = d.iso
        if iso is not None:
            isos.append(iso)
            iso_counts[iso] += 1
            iso_disp = int(iso) if iso.is_integer() else iso
            iso_display_counts[iso_disp] += 1

        lens = d.lens
        if lens is not None:
            lens_counts[lens] += 1

        if ap is not None and fl is not None:
            combination_counts[(ap, fl)] += 1

    return {
        "shutter_speed": shutter_speeds,
        "aperture": apertures,
        "focal_length": focal_lengths,
        "iso": isos,
        "fallback_count": fallback_count,
        "lens_counts": lens_counts,
        "shutter_speed_counts": shutter_speed_counts,
        "aperture_counts": aperture_counts,
        "aperture_display_counts": aperture_display_counts,
        "focal_length_counts": focal_length_counts,
        "focal_length_35mm_counts": focal_length_35mm_counts,
        "focal_length_apsc_counts": focal_length_apsc_counts,
        "iso_counts": iso_counts,
        "iso_display_counts": iso_display_counts,
        "combination_counts": combination_counts,
    }


def analyze_data(data: list[ExifData]):
    """Prints a formatted statistical summary of the metadata to stdout."""
    logger.info("Total images with EXIF data analyzed: %d", len(data))
    print("\n--- Image Metadata Analysis ---")
    print(f"Total images with EXIF data analyzed: {len(data)}")

    if not data:
        logger.info("No data to analyze")
        print("No data to analyze.")
        return

    extracted = _extract_metadata_single_pass(data)

    # Calculate fallback statistics
    fallback_count = extracted["fallback_count"]
    fallback_percent = (fallback_count / len(data)) * 100
    if fallback_count > 0:
        print(
            f"Images using fallback focal length (original): {fallback_count} ({fallback_percent:.1f}%)"
        )
    else:
        print("All images had valid 35mm equivalent focal length metadata.")

    print("\n--- Basic Statistics ---")

    for key, val_key in [
        ("Shutter Speed", "shutter_speed"),
        ("Aperture", "aperture"),
        ("Focal Length", "focal_length"),
        ("ISO", "iso"),
    ]:
        values = extracted[val_key]
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
    for name, count in extracted["lens_counts"].most_common(5):
        logger.info("  %s: %d", name, count)
        print(f"  {name}: {count}")

    print("\n\nTop Focal Lengths (mm):")
    focal_lengths = extracted["focal_length"]
    aggregated_fls = aggregate_focal_lengths(focal_lengths)
    aggregated_fls.sort(key=lambda x: x[1], reverse=True)
    for label, count, _ in aggregated_fls[:15]:
        print(f"  {label}: {count}")

    print("\n\nTop 15 Equivalent Focal Lengths (35mm):")
    for fl, count in extracted["focal_length_35mm_counts"].most_common(15):
        print(f"  {fl}mm: {count}")

    print("\n\nTop 15 Equivalent Focal Lengths (APS-C):")
    for fl, count in extracted["focal_length_apsc_counts"].most_common(15):
        print(f"  {fl}mm: {count}")

    print("\n\nTop 25 Aperture & Focal Length Combinations:")
    for (ap, fl), count in extracted["combination_counts"].most_common(25):
        fl_str = f"{int(fl)}" if fl.is_integer() else f"{fl:.1f}"
        logger.info("  f/%s @ %smm: %d", ap, fl_str, count)
        print(f"  f/{ap} @ {fl_str}mm: {count}")

    print("\n\nTop 5 Apertures (f-stop):")
    for ap, count in extracted["aperture_display_counts"].most_common(5):
        print(f"  {ap}: {count}")

    print("\n\nTop 5 ISOs:")
    logger.info("Top 5 ISOs:")
    for iso, count in extracted["iso_display_counts"].most_common(5):
        logger.info("  %s: %d", iso, count)
        print(f"  {iso}: {count}")
    print("\n----------------------------")


def analyze_data_json(data: list[ExifData]) -> Dict[str, Any]:
    """Returns analysis results as a JSON-serializable dictionary."""
    if not data:
        return {"total_images": 0}

    extracted = _extract_metadata_single_pass(data)

    result: Dict[str, Any] = {
        "total_images": len(data),
        "fallback_count": extracted["fallback_count"],
        "statistics": {},
        "distributions": {},
    }

    key_counts_map = {
        "shutter_speed": extracted["shutter_speed_counts"],
        "aperture": extracted["aperture_counts"],
        "focal_length": extracted["focal_length_counts"],
        "iso": extracted["iso_counts"],
    }

    for key in ["shutter_speed", "aperture", "focal_length", "iso"]:
        values = extracted[key]
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
            counter = key_counts_map[key]
            result["distributions"][key] = [
                {"value": v, "count": c} for v, c in counter.most_common(25)
            ]

    # Lens distribution
    result["distributions"]["lens"] = [
        {"value": name, "count": c} for name, c in extracted["lens_counts"].most_common()
    ]

    # Combinations
    result["distributions"]["aperture_focal_combinations"] = [
        {"value": f"f/{ap}@{fl}mm", "count": c}
        for (ap, fl), c in extracted["combination_counts"].most_common(25)
    ]

    return result
