import logging
from collections import Counter
import statistics
from typing import Any, Dict
from photo_selector_toolbox.utils import aggregate_focal_lengths
from photo_selector_toolbox.models import ExifData

logger = logging.getLogger(__name__)


def _extract_metadata_single_pass(data: list[ExifData]) -> Dict[str, Any]:
    shutter_speeds = []
    apertures = []
    focal_lengths = []
    focal_lengths_35mm = []
    isos = []
    lenses = []
    combinations = []
    fallback_count = 0

    for d in data:
        if d.is_fallback:
            fallback_count += 1

        ss = d.shutter_speed
        if ss is not None:
            shutter_speeds.append(ss)

        ap = d.aperture
        if ap is not None:
            apertures.append(ap)

        fl = d.focal_length
        if fl is not None:
            focal_lengths.append(fl)

        fl35 = d.focal_length_35mm
        if fl35 is not None:
            focal_lengths_35mm.append(fl35)

        iso = d.iso
        if iso is not None:
            isos.append(iso)

        lens = d.lens
        if lens is not None:
            lenses.append(lens)

        if ap is not None and fl is not None:
            combinations.append((ap, fl))

    return {
        "shutter_speed": shutter_speeds,
        "aperture": apertures,
        "focal_length": focal_lengths,
        "focal_length_35mm": focal_lengths_35mm,
        "iso": isos,
        "lens": lenses,
        "combinations": combinations,
        "fallback_count": fallback_count,
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
    lenses = extracted["lens"]
    for name, count in Counter(lenses).most_common(5):
        logger.info("  %s: %d", name, count)
        print(f"  {name}: {count}")

    print("\n\nTop Focal Lengths (mm):")
    focal_lengths = extracted["focal_length"]
    aggregated_fls = aggregate_focal_lengths(focal_lengths)
    aggregated_fls.sort(key=lambda x: x[1], reverse=True)
    for label, count, _ in aggregated_fls[:15]:
        print(f"  {label}: {count}")

    print("\n\nTop 15 Equivalent Focal Lengths (35mm):")
    focal_lengths_35 = extracted["focal_length_35mm"]
    focal_lengths_35_rounded = [int(round(fl)) for fl in focal_lengths_35]
    for fl, count in Counter(focal_lengths_35_rounded).most_common(15):
        print(f"  {fl}mm: {count}")

    print("\n\nTop 15 Equivalent Focal Lengths (APS-C):")
    focal_lengths_apsc = [int(round(fl / 1.5)) for fl in focal_lengths_35]
    for fl, count in Counter(focal_lengths_apsc).most_common(15):
        print(f"  {fl}mm: {count}")

    print("\n\nTop 25 Aperture & Focal Length Combinations:")
    combinations = extracted["combinations"]

    for (ap, fl), count in Counter(combinations).most_common(25):
        fl_str = f"{int(fl)}" if fl.is_integer() else f"{fl:.1f}"
        logger.info("  f/%s @ %smm: %d", ap, fl_str, count)
        print(f"  f/{ap} @ {fl_str}mm: {count}")

    print("\n\nTop 5 Apertures (f-stop):")
    apertures = extracted["aperture"]
    apertures_display = [int(ap) if ap.is_integer() else ap for ap in apertures]
    for ap, count in Counter(apertures_display).most_common(5):
        print(f"  {ap}: {count}")

    print("\n\nTop 5 ISOs:")
    logger.info("Top 5 ISOs:")
    isos = extracted["iso"]
    isos_display = [int(iso) if iso.is_integer() else iso for iso in isos]
    for iso, count in Counter(isos_display).most_common(5):
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
            counter = Counter(values)
            result["distributions"][key] = [
                {"value": v, "count": c} for v, c in counter.most_common(25)
            ]

    # Lens distribution
    lenses = extracted["lens"]
    result["distributions"]["lens"] = [
        {"value": name, "count": c} for name, c in Counter(lenses).most_common()
    ]

    # Combinations
    combos = [f"f/{ap}@{fl}mm" for ap, fl in extracted["combinations"]]
    result["distributions"]["aperture_focal_combinations"] = [
        {"value": combo, "count": c} for combo, c in Counter(combos).most_common(25)
    ]

    return result
