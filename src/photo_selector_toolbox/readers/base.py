import logging
from abc import ABC, abstractmethod
from pathlib import Path
from typing import List, Optional

from photo_selector_toolbox.models import ExifData

logger = logging.getLogger(__name__)


class ExifReader(ABC):
    """Base class for EXIF extraction strategies."""

    @abstractmethod
    def can_handle(self, path: Path) -> bool:
        """Whether this reader supports the given file type."""
        pass

    @abstractmethod
    def read(self, path: Path, debug: bool = False) -> Optional[ExifData]:
        """Extract EXIF data. Returns None on failure."""
        pass


# Ordered list of readers (tried in order)
_readers: List[ExifReader] = []


def register_reader(reader: ExifReader) -> None:
    _readers.append(reader)


def read_exif(path: Path, debug: bool = False) -> Optional[ExifData]:
    """Try each registered reader in order until one succeeds."""
    for reader in _readers:
        if reader.can_handle(path):
            try:
                result = reader.read(path, debug=debug)
                if result is not None:
                    return result
            except Exception as e:
                logger.debug(
                    f"Reader {reader.__class__.__name__} raised exception: {e}"
                )
    return None
