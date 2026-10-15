# Supported file formats

Applies to **every product**. The products share this *contract*, not code: each implements it
in its own stack and proves it with its own tests against the same files.

## The contract

The single source of truth is the machine-readable
[`testdata/formats/manifest.json`](../../testdata/formats/manifest.json). It defines two tiers:

| Tier | Formats | What every product must do |
|---|---|---|
| **required** | RAW: Sony `ARW`, Canon `CR2` `CR3`, Nikon `NEF` `NRW`, Fujifilm `RAF`, Panasonic `RW2`, OM System / Olympus `ORF`, Pentax `PEF`, `DNG` (Leica, Pentax, phones), Samsung `SRW`. Bitmaps: `JPG`/`JPEG`, `HEIC`/`HEIF`, `PNG`, `TIF`/`TIFF`, `WebP` | Discover the file, pair a RAW with its JPEG/HEIC companion, display it, read its EXIF, score it (products that score). Proven against a real camera file per format. |
| **recognized** | Hasselblad `3FR` `FFF`, Phase One `IIQ`, Leica `RWL` `RAW`, Sony `SR2` `SRF`, Minolta `MRW`, Sigma `X3F`, Epson `ERF`, Mamiya `MEF` `MOS` | Discover the file and pair it as a RAW. Display is best effort — no sample proves it. |

Extensions match case-insensitively (`DSC00001.ARW` and `dsc00001.arw` are the same format).

Behavioural rules every product's tests check against the manifest:

- **Display size.** A display decode reaches a long edge of at least
  `min(1024 px, the native long edge)`. RAW files may use their embedded preview (fast first
  paint) but must fall back to a real decode when that preview is smaller — phone DNGs embed
  previews of only ~672 px.
- **EXIF.** Exposure time, f-number and focal length match the camera within 2 %; ISO matches
  exactly. Make, model and lens compare case-insensitively after trimming whitespace — Pentax and
  Olympus pad these fields with spaces, which must never reach the screen. Canon CR3 stores ISO
  in `ISOSpeed` / `RecommendedExposureIndex`, not the classic `ISOSpeedRatings` tag.

## The corpus

| Source | Files | Where |
|---|---|---|
| Committed | `sample.jpg`, `sample.heic`, `sample.png`, `sample.tif`, `sample.webp` (≤ 800 px, real EXIF) | [`testdata/formats/bitmaps/`](../../testdata/formats/bitmaps/) |
| Downloaded | One real camera file per required RAW format (two for ARW: classic compressed and the lossless-compressed format of newer bodies), ~280 MB | Cache directory, never committed |

The RAW samples are public-domain (CC0) files from [raw.pixls.us](https://raw.pixls.us),
pinned by URL, size and SHA-256 together with the camera's ground-truth EXIF (read with
ExifTool). None shows a person. Fetch them once:

```bash
python3 scripts/fetch_format_corpus.py          # download missing files, verify all (~280 MB once)
python3 scripts/fetch_format_corpus.py --check  # verify only, no network
```

The cache is `$PST_FORMAT_CORPUS`, default `~/.cache/photo-selector-toolbox/format-corpus`.
Downloads try this repository's `format-corpus-v1` release assets first and fall back to
raw.pixls.us.

## How it stays working

- Every product has a **format conformance test** that reads the manifest. The contract part
  (extensions, RAW classification) and the committed bitmaps always run. The corpus part runs when
  the corpus is present and skips with a pointer to the fetch script otherwise.
- CI fetches the corpus for every product test job (`.github/actions/format-corpus`, cached by
  manifest hash) and sets `PST_REQUIRE_FORMAT_CORPUS=1`, which turns a missing corpus into a
  failure. `./scripts/run_tests.sh` does the same locally when the corpus is downloaded.
- A capability a product genuinely cannot provide is listed in its test as a **known gap** with
  its reason. Known gaps are strict: when one starts passing, the test fails until the entry is
  removed, so the list can only shrink.
- The manifest is validated in `repo-hygiene.yml` (`fetch_format_corpus.py --validate`): every
  required format needs a sample, extensions are unique, committed fixtures match their hashes.

## Adding a format

1. Pick a CC0 sample on raw.pixls.us without people in the frame; prefer a recent body and a
   file under ~35 MB.
2. Add the format (tier `required`) and the sample — URL, `bytes`, `sha256` and `expect` from
   `exiftool -Make -Model -ISO -ExposureTime -FNumber -FocalLength -LensModel -ImageSize` — to the
   manifest. Upload the file to the mirror release
   (`gh release upload format-corpus-v1 <file>`; a new manifest hash also invalidates the CI cache).
3. Run `./scripts/run_tests.sh --all`. Every product's conformance test now exercises the new
   format and fails where support is missing — implement it per product, or record a strict known
   gap with the reason.

A format moves from *recognized* to *required* the same way: add a sample.
