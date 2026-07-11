from pathlib import Path
from typing import Optional

from photo_selector_toolbox.readers.base import ExifReader, register_reader, _readers
from photo_selector_toolbox.models import ExifData


class DummyReader(ExifReader):
    def can_handle(self, path: Path) -> bool:
        return True

    def read(self, path: Path, debug: bool = False) -> Optional[ExifData]:
        return None

def test_register_reader():
    initial_length = len(_readers)
    dummy_reader = DummyReader()

    try:
        register_reader(dummy_reader)
        assert len(_readers) == initial_length + 1
        assert _readers[-1] is dummy_reader
    finally:
        # Clean up to prevent side-effects on other tests
        if dummy_reader in _readers:
            _readers.remove(dummy_reader)
