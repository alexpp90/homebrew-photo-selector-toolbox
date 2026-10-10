"""Unit tests for Linux Desktop Debian packaging and in-repo APT repository.

Adheres to:
- R-LINUX-PKG-01: Debian 13 (Trixie) packaging metadata (control, rules, changelog)
- R-LINUX-PKG-02: DFSG compliance and machine-readable copyright (DEP-5)
- R-LINUX-PKG-03: In-repo APT repository architecture (dists/trixie/..., pool/main/...)
- R-LINUX-PKG-04: Modern deb822 repository source format
- R-LINUX-PKG-05: Automated packaging and distribution tooling
"""

import gzip
import hashlib
import io
import os
import re
import subprocess
import sys
import tarfile
from pathlib import Path


def get_product_root() -> Path:
    """Return products/linux-desktop directory."""
    return Path(__file__).resolve().parents[2]


def get_repo_root() -> Path:
    """Return repository root directory."""
    return get_product_root().parents[1]


def test_debian_control_metadata():
    """Verify debian/control fields and dependency definitions (R-LINUX-PKG-01)."""
    product_dir = get_product_root()
    control_file = product_dir / "debian" / "control"
    assert control_file.is_file(), f"Missing control file at {control_file}"

    content = control_file.read_text(encoding="utf-8")
    stanzas = content.strip().split("\n\n")
    assert len(stanzas) >= 2, "debian/control must contain source and binary stanzas"

    # Source stanza
    src_stanza = stanzas[0]
    assert "Source: photo-selector-linux" in src_stanza
    assert "debhelper-compat (= 13)" in src_stanza
    assert "Section: graphics" in src_stanza
    assert "Standards-Version: 4.7.0" in src_stanza or "Standards-Version: 4.6.2" in src_stanza

    # Binary stanza
    bin_stanza = stanzas[1]
    assert "Package: photo-selector-linux" in bin_stanza
    assert "Architecture: all" in bin_stanza

    # Mandatory runtime dependencies
    for dep in (
        "python3",
        "python3-gi",
        "gir1.2-gtk-4.0",
        "gir1.2-adw-1",
        "gir1.2-gexiv2-0.10",
        "python3-numpy",
        "python3-pil",
    ):
        assert dep in bin_stanza, f"Missing dependency '{dep}' in binary stanza"


def test_dfsg_compliance():
    """Verify machine-readable DEP-5 copyright and license terms (R-LINUX-PKG-02)."""
    product_dir = get_product_root()
    copyright_file = product_dir / "debian" / "copyright"
    assert copyright_file.is_file(), f"Missing copyright file at {copyright_file}"

    text = copyright_file.read_text(encoding="utf-8")
    assert "Format: https://www.debian.org/doc/packaging-manuals/copyright-format/1.0/" in text
    assert "Upstream-Name: photo-selector-linux" in text
    assert "License: MIT" in text
    assert "Copyright:" in text


def test_debian_rules_syntax():
    """Verify debian/rules executable permissions and pybuild sequence (R-LINUX-PKG-01)."""
    product_dir = get_product_root()
    rules_file = product_dir / "debian" / "rules"
    assert rules_file.is_file(), f"Missing rules file at {rules_file}"
    assert os.access(rules_file, os.X_OK), "debian/rules must be executable"

    content = rules_file.read_text(encoding="utf-8")
    assert content.startswith("#!/usr/bin/make -f"), "debian/rules must start with #!/usr/bin/make -f"
    assert "pybuild" in content or "dh $@" in content


def test_debian_changelog_format():
    """Verify debian/changelog syntax and target suite trixie (R-LINUX-PKG-01)."""
    product_dir = get_product_root()
    changelog_file = product_dir / "debian" / "changelog"
    assert changelog_file.is_file(), f"Missing changelog file at {changelog_file}"

    lines = changelog_file.read_text(encoding="utf-8").splitlines()
    assert len(lines) > 0, "debian/changelog is empty"

    header = lines[0]
    match = re.match(r"^photo-selector-linux\s+\(([^)]+)\)\s+([a-zA-Z0-9_-]+);", header)
    assert match is not None, f"Invalid changelog header: {header}"
    suite = match.group(2)
    assert suite in ("trixie", "unstable"), f"Target distribution must be trixie, got: {suite}"

    trailer = [line for line in lines if line.strip().startswith("--")]
    assert len(trailer) > 0, "debian/changelog missing trailer line"
    assert re.search(r"\+\d{4}|UTC|GMT", trailer[0]), "Trailer must contain timezone offset"


def test_desktop_integration_files():
    """Verify desktop entry, AppStream metainfo, and icon assets (R-LINUX-PKG-01)."""
    product_dir = get_product_root()
    data_dir = product_dir / "data"

    desktop_file = data_dir / "org.photoselector.Linux.desktop"
    assert desktop_file.is_file(), "Missing org.photoselector.Linux.desktop"
    desktop_text = desktop_file.read_text(encoding="utf-8")
    assert "Type=Application" in desktop_text
    assert "Exec=photo-selector-linux %F" in desktop_text
    assert "Icon=org.photoselector.Linux" in desktop_text
    assert "StartupWMClass=org.photoselector.Linux" in desktop_text

    meta_file = data_dir / "org.photoselector.Linux.metainfo.xml"
    assert meta_file.is_file(), "Missing org.photoselector.Linux.metainfo.xml"
    meta_text = meta_file.read_text(encoding="utf-8")
    assert "<id>org.photoselector.Linux</id>" in meta_text
    assert "<binary>photo-selector-linux</binary>" in meta_text

    svg_icon = data_dir / "icons" / "hicolor" / "scalable" / "apps" / "org.photoselector.Linux.svg"
    png_icon = data_dir / "icons" / "hicolor" / "128x128" / "apps" / "org.photoselector.Linux.png"
    assert svg_icon.is_file(), "Missing scalable SVG icon"
    assert png_icon.is_file(), "Missing 128x128 PNG icon"


def test_package_deb_builder(tmp_path):
    """Verify pure-Python builder produces valid .deb archive (R-LINUX-PKG-05)."""
    product_dir = get_product_root()
    packager_script = product_dir / "scripts" / "package_deb.py"
    assert packager_script.is_file(), "Missing package_deb.py script"

    out_dir = tmp_path / "deb_out"
    out_dir.mkdir()

    cmd = [
        sys.executable,
        str(packager_script),
        "--product-dir",
        str(product_dir),
        "--output-dir",
        str(out_dir),
        "--version",
        "0.1.0",
        "--output-filename",
        "test-package_0.1.0_all.deb",
    ]
    res = subprocess.run(cmd, capture_output=True, text=True)
    assert res.returncode == 0, f"package_deb.py failed:\n{res.stderr}"

    deb_path = out_dir / "test-package_0.1.0_all.deb"
    assert deb_path.is_file(), f"Output package not created at {deb_path}"
    assert deb_path.stat().st_size > 1024, "Package size suspiciously small"

    # Inspect ar container
    with open(deb_path, "rb") as f:
        magic = f.read(8)
        assert magic == b"!<arch>\n", f"Invalid ar magic: {magic}"

        member_names = []
        while True:
            hdr = f.read(60)
            if not hdr or len(hdr) < 60:
                break
            m_name = hdr[:16].decode("ascii", errors="replace").strip().rstrip("/")
            m_size = int(hdr[48:58].decode("ascii", errors="replace").strip())
            member_names.append(m_name)
            data = f.read(m_size)
            if m_size % 2 != 0:
                f.read(1)

            if m_name == "debian-binary":
                assert data == b"2.0\n", f"Invalid debian-binary content: {data}"
            elif m_name.startswith("control.tar"):
                with tarfile.open(fileobj=io.BytesIO(data), mode="r:*") as t:
                    ctrl_names = t.getnames()
                    assert "./control" in ctrl_names or "control" in ctrl_names
                    assert "./md5sums" in ctrl_names or "md5sums" in ctrl_names
            elif m_name.startswith("data.tar"):
                with tarfile.open(fileobj=io.BytesIO(data), mode="r:*") as t:
                    data_names = t.getnames()
                    assert any("usr/bin/photo-selector-linux" in n for n in data_names)
                    assert any("org.photoselector.Linux.desktop" in n for n in data_names)

        assert "debian-binary" in member_names
        assert any(n.startswith("control.tar") for n in member_names)
        assert any(n.startswith("data.tar") for n in member_names)


def test_apt_repository_structure():
    """Verify in-repo APT repository tree structure and packages (R-LINUX-PKG-03)."""
    repo_root = get_repo_root()
    apt_dir = repo_root / "apt"
    assert apt_dir.is_dir(), f"apt/ directory missing at {apt_dir}"

    release_file = apt_dir / "dists" / "trixie" / "Release"
    inrelease_file = apt_dir / "dists" / "trixie" / "InRelease"
    assert release_file.is_file(), "Missing dists/trixie/Release"
    assert inrelease_file.is_file(), "Missing dists/trixie/InRelease"

    for arch in ("binary-all", "binary-amd64"):
        pkg_file = apt_dir / "dists" / "trixie" / "main" / arch / "Packages"
        pkg_gz = apt_dir / "dists" / "trixie" / "main" / arch / "Packages.gz"
        assert pkg_file.is_file(), f"Missing {pkg_file}"
        assert pkg_gz.is_file(), f"Missing {pkg_gz}"

        text = pkg_file.read_text(encoding="utf-8")
        assert "Package: photo-selector-linux" in text
        assert "Filename: pool/main/p/photo-selector-linux/photo-selector-linux_" in text

        # Verify Packages.gz decompresses cleanly
        gz_data = pkg_gz.read_bytes()
        decompressed = gzip.decompress(gz_data).decode("utf-8")
        assert decompressed == text, "Packages.gz content mismatch"

    pool_pkg = apt_dir / "pool" / "main" / "p" / "photo-selector-linux" / "photo-selector-linux_0.1.0_all.deb"
    assert pool_pkg.is_file(), f"Expected pool package missing: {pool_pkg}"


def test_deb822_sources_format():
    """Verify modern deb822 sources file syntax and setup.sh (R-LINUX-PKG-04)."""
    repo_root = get_repo_root()
    apt_dir = repo_root / "apt"

    sources_file = apt_dir / "photo-selector.sources"
    assert sources_file.is_file(), f"Missing sources file at {sources_file}"
    text = sources_file.read_text(encoding="utf-8")

    assert "Types: deb" in text
    assert "Suites: trixie" in text
    assert "Components: main" in text
    assert "Signed-By: /etc/apt/keyrings/photo-selector-archive-keyring.gpg" in text

    setup_file = apt_dir / "setup.sh"
    assert setup_file.is_file(), f"Missing setup script at {setup_file}"
    assert os.access(setup_file, os.X_OK), "setup.sh must be executable"


def test_release_checksum_integrity():
    """Verify cryptographic integrity of Release manifest hashes (R-LINUX-PKG-03)."""
    repo_root = get_repo_root()
    trixie_dir = repo_root / "apt" / "dists" / "trixie"
    release_path = trixie_dir / "Release"
    assert release_path.is_file()

    rel_text = release_path.read_text(encoding="utf-8")
    assert "SHA256:" in rel_text

    sha256_section = rel_text.split("SHA256:")[1].split("SHA512:")[0]
    entries = {}
    for line in sha256_section.strip().splitlines():
        parts = line.strip().split()
        if len(parts) == 3:
            sha, size, rel_path = parts
            entries[rel_path] = (sha.lower(), int(size))

    assert len(entries) >= 4, "Release must index at least 4 files (all + amd64 Packages & Packages.gz)"

    for rel_path, (expected_sha, expected_size) in entries.items():
        disk_path = trixie_dir / rel_path
        assert disk_path.is_file(), f"Index file listed in Release does not exist: {disk_path}"
        actual_size = disk_path.stat().st_size
        assert actual_size == expected_size, f"Size mismatch for {rel_path}"

        actual_sha = hashlib.sha256(disk_path.read_bytes()).hexdigest().lower()
        assert actual_sha == expected_sha, f"SHA256 mismatch for {rel_path}"
