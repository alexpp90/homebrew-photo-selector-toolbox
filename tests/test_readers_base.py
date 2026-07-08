import logging
from pathlib import Path

from photo_selector_toolbox.readers.base import read_exif, ExifReader
from photo_selector_toolbox.models import ExifData

class MockFailingReader(ExifReader):
    def can_handle(self, path: Path) -> bool:
        return True

    def read(self, path: Path, debug: bool = False):
        raise ValueError("Simulated reader failure")

class MockSuccessReader(ExifReader):
    def can_handle(self, path: Path) -> bool:
        return True

    def read(self, path: Path, debug: bool = False):
        return ExifData(iso=100.0, focal_length=50.0, aperture=2.8, shutter_speed=1/100)

def test_read_exif_continues_on_exception(caplog, monkeypatch):
    from photo_selector_toolbox.readers import base

    # Temporarily clear readers list to isolate test
    monkeypatch.setattr(base, "_readers", [])

    # Register failing reader then success reader
    failing_reader = MockFailingReader()
    success_reader = MockSuccessReader()
    base.register_reader(failing_reader)
    base.register_reader(success_reader)

    caplog.set_level(logging.DEBUG)

    # Test
    dummy_path = Path("dummy.jpg")
    result = read_exif(dummy_path)

    # Assert result is from success reader
    assert result is not None
    assert result.iso == 100.0

    # Assert error was logged
    assert any("Simulated reader failure" in record.message for record in caplog.records)
    assert any("Reader MockFailingReader raised exception" in record.message for record in caplog.records)

def test_read_exif_returns_none_if_all_fail(caplog, monkeypatch):
    from photo_selector_toolbox.readers import base

    # Temporarily clear readers list to isolate test
    monkeypatch.setattr(base, "_readers", [])

    # Register failing reader
    failing_reader = MockFailingReader()
    base.register_reader(failing_reader)

    caplog.set_level(logging.DEBUG)

    # Test
    dummy_path = Path("dummy.jpg")
    result = read_exif(dummy_path)

    # Assert result is None
    assert result is None

    # Assert error was logged
    assert any("Simulated reader failure" in record.message for record in caplog.records)
    assert any("Reader MockFailingReader raised exception" in record.message for record in caplog.records)
