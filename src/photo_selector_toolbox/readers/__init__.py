from photo_selector_toolbox.readers.base import read_exif, register_reader
from photo_selector_toolbox.readers.exifread_reader import ExifReadReader
from photo_selector_toolbox.readers.exiftool import ExifToolReader
from photo_selector_toolbox.readers.pillow import PillowReader

# Register strategies in preferred order
register_reader(ExifToolReader())
register_reader(ExifReadReader())
register_reader(PillowReader())
