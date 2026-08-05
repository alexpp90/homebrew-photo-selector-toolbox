from photo_selector_toolbox.exif.readers.base import read_exif  # noqa: F401
from photo_selector_toolbox.exif.readers.exiftool import ExifToolReader
from photo_selector_toolbox.exif.readers.exifread_reader import ExifReadReader
from photo_selector_toolbox.exif.readers.pillow import PillowReader
from photo_selector_toolbox.exif.readers.base import register_reader

# Register strategies in preferred order
register_reader(ExifToolReader())
register_reader(ExifReadReader())
register_reader(PillowReader())
