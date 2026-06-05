import logging
from collections import Counter
import statistics
from photo_selector_toolbox.utils import aggregate_focal_lengths
from photo_selector_toolbox.models import ExifData

logger = logging.getLogger(__name__)


def analyze_data(data: list[ExifData]):
    """Logs a formatted statistical summary of the metadata."""
    logger.info("\n--- Image Metadata Analysis ---")
    logger.info(f"Total images with EXIF data analyzed: {len(data)}")

    if not data:
        logger.info("No data to analyze.")
        return

    # Calculate fallback statistics
    fallback_count = sum(1 for d in data if d.is_fallback)
    fallback_percent = (fallback_count / len(data)) * 100
    if fallback_count > 0:
        logger.info(
            f"Images using fallback focal length (original): {fallback_count} ({fallback_percent:.1f}%)"
        )
    else:
        logger.info("All images had valid 35mm equivalent focal length metadata.")

    logger.info("\n--- Basic Statistics ---")

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
            logger.info(f"\n{key}:")
            logger.info(f"  Count: {len(values)}")
            logger.info(f"  Mean:  {statistics.mean(values):.2f}")
            if len(values) > 1:
                logger.info(f"  Std:   {statistics.stdev(values):.2f}")
            logger.info(f"  Min:   {min(values)}")
            logger.info(f"  Max:   {max(values)}")
        else:
            logger.info(f"\n{key}: No data")

    logger.info("\n--- Most Common Settings ---")

    logger.info("\nTop 5 Lenses:")
    lenses = get_values("Lens")
    for name, count in Counter(lenses).most_common(5):
        logger.info(f"  {name}: {count}")

    logger.info("\n\nTop Focal Lengths (mm):")
    focal_lengths = get_values("Focal Length")
    # Use aggregation logic
    aggregated_fls = aggregate_focal_lengths(focal_lengths)
    # Sort by count descending
    aggregated_fls.sort(key=lambda x: x[1], reverse=True)
    # Display top 15 of the aggregated buckets
    for label, count, _ in aggregated_fls[:15]:
        logger.info(f"  {label}: {count}")

    logger.info("\n\nTop 15 Equivalent Focal Lengths (35mm):")
    focal_lengths_35 = get_values("Focal Length (35mm)")
    # Round to nearest integer for cleaner display
    focal_lengths_35_rounded = [int(round(fl)) for fl in focal_lengths_35]
    for fl, count in Counter(focal_lengths_35_rounded).most_common(15):
        logger.info(f"  {fl}mm: {count}")

    logger.info("\n\nTop 15 Equivalent Focal Lengths (APS-C):")
    # APS-C is 35mm / 1.5
    focal_lengths_apsc = [int(round(fl / 1.5)) for fl in focal_lengths_35]
    for fl, count in Counter(focal_lengths_apsc).most_common(15):
        logger.info(f"  {fl}mm: {count}")

    logger.info("\n\nTop 25 Aperture & Focal Length Combinations:")
    combinations = []
    for d in data:
        if d.aperture is not None and d.focal_length is not None:
            combinations.append((d.aperture, d.focal_length))

    for (ap, fl), count in Counter(combinations).most_common(25):
        fl_str = f"{int(fl)}" if fl.is_integer() else f"{fl:.1f}"
        logger.info(f"  f/{ap} @ {fl_str}mm: {count}")

    logger.info("\n\nTop 5 Apertures (f-stop):")
    apertures = get_values("Aperture")
    apertures_display = [int(ap) if ap.is_integer() else ap for ap in apertures]
    for ap, count in Counter(apertures_display).most_common(5):
        logger.info(f"  {ap}: {count}")

    logger.info("\n\nTop 5 ISOs:")
    isos = get_values("ISO")
    isos_display = [int(iso) if iso.is_integer() else iso for iso in isos]
    for iso, count in Counter(isos_display).most_common(5):
        logger.info(f"  {iso}: {count}")
    logger.info("\n----------------------------")
