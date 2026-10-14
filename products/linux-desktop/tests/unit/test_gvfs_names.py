"""Unit tests for GVFS cloud-mount support (Google Drive lists files under opaque IDs).

Adheres to:
- R-LINUX-META-07: Cloud Mounts via GVFS
"""

from pathlib import Path
from typing import Dict

from photo_selector_linux.core.file_ops import FileCullingManager
from photo_selector_linux.core.gvfs_names import GioDisplayNameResolver, is_gvfs_fuse_path
from photo_selector_linux.core.models import CandidatePhoto
from photo_selector_linux.core.scanner import DirectoryScanner
from photo_selector_linux.viewmodels.culling_workspace import CullingWorkspaceViewModel


class FakeResolver:
    """Display names per directory, as GIO reports them for a google-drive:// mount."""

    def __init__(self, names: Dict[Path, Dict[str, str]]):
        self.names = names

    def names_in(self, directory: Path) -> Dict[str, str]:
        return dict(self.names.get(directory.resolve(), {}))


class _FakeInfo:
    def __init__(self, name, display):
        self._name, self._display = name, display

    def get_name(self):
        return self._name

    def get_display_name(self):
        return self._display


class _FakeNativeDir:
    def __init__(self, children, log):
        self.children, self.log = children, log

    def resolve_relative_path(self, rel):
        self.log.append(rel)
        return self

    def enumerate_children(self, *args):
        return _FakeEnumerator(self.children)


class _FakeEnumerator(list):
    def __init__(self, children):
        super().__init__(_FakeInfo(n, d) for n, d in children)

    def close(self, *args):
        pass


def test_gvfs_fuse_path_detection():
    roots = [Path("/run/user/1000/gvfs")]
    assert is_gvfs_fuse_path(Path("/run/user/1000/gvfs/google-drive:host=x/1abc"), roots)
    assert not is_gvfs_fuse_path(Path("/home/me/Pictures/DSC0001.ARW"), roots)
    assert not is_gvfs_fuse_path(Path("/run/user/1000/gvfsx/file"), roots)


def test_gio_resolver_maps_fuse_paths_onto_the_native_mount():
    log = []
    root = Path("/run/user/1000/gvfs/google-drive:host=gmail.com,user=me")
    native = _FakeNativeDir([("1aW85", "DSC0001.ARW"), ("same.jpg", "same.jpg")], log)
    resolver = GioDisplayNameResolver(mounts=[(root, native)])

    assert resolver.names_in(root / "0ABB" / "13_fe") == {"1aW85": "DSC0001.ARW"}
    assert log == ["0ABB/13_fe"]
    assert resolver.names_in(Path("/home/me/Pictures")) == {}


def test_scanner_finds_photos_listed_under_opaque_ids(tmp_path: Path):
    """Regression: a Google Drive folder loaded 0 photos because no ID has an extension."""
    for file_id in ("13b8", "13bw", "13xH", "1doc"):
        (tmp_path / file_id).write_bytes(b"x")
    resolver = FakeResolver({tmp_path.resolve(): {
        "13b8": "DSC02906.ARW", "13bw": "DSC02906.JPG", "13xH": "DSC02905.ARW", "1doc": "Notes",
    }})

    candidates = DirectoryScanner(name_resolver=resolver).scan_directory(tmp_path)

    assert [c.filename for c in candidates] == ["DSC02905.ARW", "DSC02906.ARW"]
    pair = candidates[1]
    assert pair.primary_path.name == "13b8"  # RAW is primary, chosen by display name
    assert [pair.name_of(p) for p in pair.companion_paths] == ["DSC02906.JPG"]
    assert pair.extension == "arw"


def test_scanner_prunes_selection_folder_listed_under_an_id(tmp_path: Path):
    (tmp_path / "1sel").mkdir()
    (tmp_path / "1sel" / "1moved").write_bytes(b"x")
    (tmp_path / "1keep").write_bytes(b"x")
    resolver = FakeResolver({
        tmp_path.resolve(): {"1sel": "Selection", "1keep": "DSC0001.JPG"},
        (tmp_path / "1sel").resolve(): {"1moved": "DSC0002.JPG"},
    })

    candidates = DirectoryScanner(name_resolver=resolver).scan_directory(tmp_path)

    assert [c.filename for c in candidates] == ["DSC0001.JPG"]


def test_copy_keeps_real_names_and_reuses_existing_selection_folder(tmp_path: Path):
    """Culled files must not be renamed to their Drive ID, nor spawn duplicate Selection folders."""
    (tmp_path / "1sel").mkdir()
    (tmp_path / "13b8").write_bytes(b"raw")
    (tmp_path / "13bw").write_bytes(b"jpg")
    resolver = FakeResolver({tmp_path.resolve(): {"1sel": "Selection"}})
    candidate = CandidatePhoto(
        primary_path=tmp_path / "13b8",
        companion_paths=[tmp_path / "13bw"],
        display_names={tmp_path / "13b8": "DSC0001.ARW", tmp_path / "13bw": "DSC0001.JPG"},
    )

    FileCullingManager(name_resolver=resolver).copy_candidate(candidate, tmp_path)

    assert sorted(p.name for p in (tmp_path / "1sel").iterdir()) == ["DSC0001.ARW", "DSC0001.JPG"]
    assert not (tmp_path / "Selection").exists()


def test_viewmodel_reports_the_real_folder_name(tmp_path: Path, monkeypatch):
    folder = tmp_path / "13_feolAXzI"
    folder.mkdir()
    resolver = FakeResolver({tmp_path.resolve(): {"13_feolAXzI": "Wedding 2024"}})
    monkeypatch.setattr(
        "photo_selector_linux.core.scanner.default_resolver", lambda: resolver
    )
    vm = CullingWorkspaceViewModel()

    vm.load_directory(folder)

    assert vm.current_folder_name == "Wedding 2024"
