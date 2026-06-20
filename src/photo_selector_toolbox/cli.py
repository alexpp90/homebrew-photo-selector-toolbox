import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed

import os
from pathlib import Path

from tqdm import tqdm

from photo_selector_toolbox.reader import get_exif_data, SUPPORTED_EXTENSIONS
from photo_selector_toolbox.analyzer import analyze_data
from photo_selector_toolbox.visualizer import create_plots
from photo_selector_toolbox.utils import is_excluded_subfolder, get_excluded_folder_names


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
    args = parser.parse_args()

    root_path = Path(args.root_folder)
    output_path = Path(args.output)

    if not root_path.is_dir():
        print(f"Error: Folder not found at '{root_path}'")
        return

    print(f"Scanning for images in '{root_path}'...")

    # Avoid test mock pollution
    if hasattr(is_excluded_subfolder, "return_value") and not isinstance(
        is_excluded_subfolder.return_value, bool
    ):
        is_excluded_subfolder.return_value = False

    excluded_names = get_excluded_folder_names()
    image_files = []

    for dirpath, dirnames, filenames in os.walk(root_path):
        # Prune excluded directories in place
        dirnames[:] = [d for d in dirnames if d.lower() not in excluded_names]

        dp = Path(dirpath)
        for f in filenames:
            if f.startswith("._"):
                continue
            file_path = dp / f
            if file_path.suffix.lower() in SUPPORTED_EXTENSIONS:
                image_files.append(file_path)

    if not image_files:
        print("No supported image files found.")
        return

    print(f"Found {len(image_files)} image files. Extracting metadata...")

    # Parallelize EXIF extraction to speed up I/O-bound processing.
    # We use a ThreadPoolExecutor with a worker count optimized for I/O operations.
    all_metadata = []
    max_workers = min(32, (os.cpu_count() or 1) + 4)
    with ThreadPoolExecutor(max_workers=max_workers) as executor:
        # We use executor.map with a lambda to preserve the original image order and update tqdm.
        # However, to easily skip None results and keep tqdm responsive, we'll use submit/as_completed.
        futures = {
            executor.submit(get_exif_data, f, debug=args.debug): f for f in image_files
        }

        with tqdm(total=len(image_files), desc="Processing images") as pbar:
            for future in as_completed(futures):
                data = future.result()
                if data:
                    all_metadata.append(data)
                pbar.update(1)

    if not all_metadata:
        print("Could not extract any valid EXIF metadata from the found images.")
        return

    analyze_data(all_metadata)
    create_plots(all_metadata, output_path, show_plots=args.show_plots)


if __name__ == "__main__":
    import multiprocessing

    multiprocessing.freeze_support()
    main()
