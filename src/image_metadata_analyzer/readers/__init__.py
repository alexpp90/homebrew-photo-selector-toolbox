from image_metadata_analyzer.readers.base import read_exif
from image_metadata_analyzer.readers.exiftool import ExifToolReader
from image_metadata_analyzer.readers.exifread_reader import ExifReadReader
from image_metadata_analyzer.readers.pillow import PillowReader
from image_metadata_analyzer.readers.base import register_reader

# Register strategies in preferred order
register_reader(ExifToolReader())
register_reader(ExifReadReader())
register_reader(PillowReader())
