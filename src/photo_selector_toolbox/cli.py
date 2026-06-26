import argparse
import json
import logging
import os
import sys
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

from tqdm import tqdm

from photo_selector_toolbox import __version__
from photo_selector_toolbox.reader import get_exif_data, SUPPORTED_EXTENSIONS
from photo_selector_toolbox.analyzer import analyze_data, analyze_data_json
from photo_selector_toolbox.visualizer import create_plots
from photo_selector_toolbox.utils import get_excluded_folder_names


def main():
    """Main function to orchestrate the script execution."""
    parser = argparse.ArgumentParser(
        description="Analyze image metadata from a folder."
    )
    parser.add_argument(
        "root_folder", type=str, help="The root folder to search for images."
    )
    parser.add_argument(
        "-o",
        "--output",
        type=str,
        default="analysis_results",
        help="The folder to save graphs.",
    )
    parser.add_argument(
        "--debug",
        action="store_true",
        help="Enable detailed debug output for files that could not be processed.",
    )
    parser.add_argument(
        "--show-plots",
        action="store_true",
        help="Automatically open the aperture, focal length, lens, and combination plots after creation.",
    )
    parser.add_argument(
        "-v",
        "--verbose",
        action="store_true",
        help="Enable verbose (DEBUG) logging output.",
    )
    parser.add_argument(
        "--version",
        action="version",
        version=f"%(prog)s {__version__}",
    )
    parser.add_argument(
        "--format",
        choices=["text", "json", "csv"],
        default="text",
        help="Output format for analysis results (default: text).",
    )
    args = parser.parse_args()

    # Configure logging
    log_level = logging.DEBUG if args.verbose else logging.WARNING
    logging.basicConfig(
        level=log_level,
        format="%(levelname)s: %(message)s",
        stream=sys.stderr,
    )

    root_path = Path(args.root_folder)
    output_path = Path(args.output)

    if not root_path.is_dir():
        print(f"Error: Folder not found at '{root_path}'")
        return

    print(f"Scanning for images in '{root_path}'...")

    excluded_names = get_excluded_folder_names()
    image_files = []

    # Pre-compute tuple of extensions for fast string matching
    supported_exts_tuple = tuple(SUPPORTED_EXTENSIONS)

    for dirpath, dirnames, filenames in os.walk(root_path):
        # Prune excluded directories in place
        dirnames[:] = [d for d in dirnames if d.lower() not in excluded_names]

        dp = Path(dirpath)
        for f in filenames:
            if f.startswith("._"):
                continue
            if f.lower().endswith(supported_exts_tuple):
                image_files.append(dp / f)

    if not image_files:
        print("No supported image files found.")
        return

    print(f"Found {len(image_files)} image files. Extracting metadata...")

    # Parallelize EXIF extraction
    all_metadata = []
    max_workers = min(32, (os.cpu_count() or 1) + 4)
    with ThreadPoolExecutor(max_workers=max_workers) as executor:
        futures = {
            executor.submit(get_exif_data, f, debug=args.debug): f for f in image_files
        }

        with tqdm(total=len(image_files), desc="Processing images", file=sys.stderr) as pbar:
            for future in as_completed(futures):
                data = future.result()
                if data:
                    all_metadata.append(data)
                pbar.update(1)

    if not all_metadata:
        print("Could not extract any valid EXIF metadata from the found images.")
        return

    # Output based on format
    if args.format == "json":
        result = analyze_data_json(all_metadata)
        print(json.dumps(result, indent=2))
    elif args.format == "csv":
        _output_csv(all_metadata)
    else:
        analyze_data(all_metadata)
        create_plots(all_metadata, output_path, show_plots=args.show_plots)


def _output_csv(data):
    """Output metadata as CSV to stdout."""
    import csv
    writer = csv.writer(sys.stdout)
    writer.writerow(["filename", "shutter_speed", "aperture", "iso", "focal_length", "focal_length_35mm", "lens"])
    for d in data:
        writer.writerow([
            getattr(d, '_filepath', ''),
            d.shutter_speed if d.shutter_speed is not None else '',
            d.aperture if d.aperture is not None else '',
            d.iso if d.iso is not None else '',
            d.focal_length if d.focal_length is not None else '',
            d.focal_length_35mm if d.focal_length_35mm is not None else '',
            d.lens if d.lens != 'Unknown' else '',
        ])


if __name__ == "__main__":
    import multiprocessing

    multiprocessing.freeze_support()
    main()
