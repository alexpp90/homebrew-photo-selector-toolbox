from photo_selector_toolbox.readers.base import read_exif  # noqa: F401
from photo_selector_toolbox.readers.exiftool import ExifToolReader
from photo_selector_toolbox.readers.exifread_reader import ExifReadReader
from photo_selector_toolbox.readers.pillow import PillowReader
from photo_selector_toolbox.readers.base import register_reader

# Register strategies in preferred order
register_reader(ExifToolReader())
register_reader(ExifReadReader())
register_reader(PillowReader())
