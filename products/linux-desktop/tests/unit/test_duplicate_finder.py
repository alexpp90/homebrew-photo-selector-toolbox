"""Unit tests for streaming cryptographic duplicate finder.

Adheres to:
- R-LINUX-TOOLS-01: Streaming Cryptographic Duplicate Finder
- R-LINUX-META-05: Dynamic Selection Subfolder Exclusion Invariant
"""

import hashlib
from pathlib import Path
from photo_selector_linux.core.duplicate_finder import (
    compute_file_sha256,
    find_duplicates,
)


def test_duplicate_detection_by_content(tmp_path: Path):
    """Verify duplicate files with identical content are grouped into clusters (R-LINUX-TOOLS-01)."""
    f1 = tmp_path / "img1.jpg"
    f2 = tmp_path / "img1_copy.jpg"
    unique = tmp_path / "unique.jpg"

    content = b"identical-photo-bytes-12345"
    f1.write_bytes(content)
    f2.write_bytes(content)
    unique.write_bytes(b"completely-different-image")

    clusters = find_duplicates(tmp_path)
    assert len(clusters) == 1
    assert clusters[0].count == 2
    assert set(clusters[0].file_paths) == {f1, f2}
    assert clusters[0].hash == hashlib.sha256(content).hexdigest()
    assert clusters[0].file_size == len(content)
    assert clusters[0].wasted_bytes == len(content)


def test_size_filtering_precheck(tmp_path: Path):
    """Verify files with unique sizes are skipped in Stage 1 without being hashed (R-LINUX-TOOLS-01)."""
    (tmp_path / "a.jpg").write_bytes(b"a" * 100)
    (tmp_path / "b.jpg").write_bytes(b"b" * 200)
    (tmp_path / "c.jpg").write_bytes(b"c" * 300)

    clusters = find_duplicates(tmp_path)
    assert len(clusters) == 0


def test_same_size_different_content(tmp_path: Path):
    """Verify files sharing exact byte size but different content are separated (R-LINUX-TOOLS-01)."""
    f1 = tmp_path / "alpha.jpg"
    f2 = tmp_path / "beta.jpg"
    f1.write_bytes(b"A" * 500)
    f2.write_bytes(b"B" * 500)

    clusters = find_duplicates(tmp_path)
    assert len(clusters) == 0


def test_streaming_hash_chunks(tmp_path: Path):
    """Verify 64 KB chunked streaming hashing matches direct SHA-256 (R-LINUX-TOOLS-01)."""
    data = b"X" * (200 * 1024)  # 200 KB
    file_p = tmp_path / "large.jpg"
    file_p.write_bytes(data)

    digest_stream = compute_file_sha256(file_p)
    digest_direct = hashlib.sha256(data).hexdigest()

    assert digest_stream == digest_direct


def test_selection_folder_exclusion(tmp_path: Path):
    """Verify duplicate files in Selection/ are excluded while Trip_Selection_Final/ is scanned (R-LINUX-META-05)."""
    data = b"matching-bytes-999"
    root_file = tmp_path / "pic.jpg"
    root_file.write_bytes(data)

    # Excluded folders
    sel_dir = tmp_path / "Selection"
    sel_dir.mkdir()
    (sel_dir / "sel_dup.jpg").write_bytes(data)

    tok_dir = tmp_path / "PhotoTok_Selection"
    tok_dir.mkdir()
    (tok_dir / "tok_dup.jpg").write_bytes(data)

    # Allowed substring folder
    trip_dir = tmp_path / "Trip_Selection_Final"
    trip_dir.mkdir()
    trip_file = trip_dir / "trip_dup.jpg"
    trip_file.write_bytes(data)

    clusters = find_duplicates(tmp_path)
    assert len(clusters) == 1
    assert clusters[0].count == 2
    assert set(clusters[0].file_paths) == {root_file, trip_file}


def test_cluster_sorting_by_wasted_space(tmp_path: Path):
    """Verify clusters are ordered descending by wasted_bytes = size * (count - 1) (R-LINUX-TOOLS-01)."""
    # Cluster 1: size 100, 2 files -> wasted = 100 * 1 = 100 bytes
    c1_a = tmp_path / "c1_a.jpg"
    c1_b = tmp_path / "c1_b.jpg"
    c1_a.write_bytes(b"1" * 100)
    c1_b.write_bytes(b"1" * 100)

    # Cluster 2: size 50, 4 files -> wasted = 50 * 3 = 150 bytes
    for i in range(4):
        (tmp_path / f"c2_{i}.jpg").write_bytes(b"2" * 50)

    # Cluster 3: size 500, 2 files -> wasted = 500 * 1 = 500 bytes
    c3_a = tmp_path / "c3_a.jpg"
    c3_b = tmp_path / "c3_b.jpg"
    c3_a.write_bytes(b"3" * 500)
    c3_b.write_bytes(b"3" * 500)

    clusters = find_duplicates(tmp_path)
    assert len(clusters) == 3
    # Ordered: Cluster 3 (500) -> Cluster 2 (150) -> Cluster 1 (100)
    assert clusters[0].wasted_bytes == 500
    assert clusters[1].wasted_bytes == 150
    assert clusters[2].wasted_bytes == 100


def test_duplicate_progress_callback(tmp_path: Path):
    """Verify progress callback is invoked with valid increasing counters (R-LINUX-TOOLS-01)."""
    (tmp_path / "x1.jpg").write_bytes(b"same" * 10)
    (tmp_path / "x2.jpg").write_bytes(b"same" * 10)

    calls = []

    def on_progress(processed, total):
        calls.append((processed, total))

    clusters = find_duplicates(tmp_path, progress_callback=on_progress)
    assert len(clusters) == 1
    assert len(calls) == 2
    assert calls[-1][0] == calls[-1][1] == 2
