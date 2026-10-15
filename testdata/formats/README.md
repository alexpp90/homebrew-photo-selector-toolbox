# Format conformance data

Test data shared by every product's format conformance tests — data only, no code.

- `manifest.json` — the supported-format contract and the pinned sample corpus with ground-truth
  EXIF. Read by each product's tests; validated by `scripts/fetch_format_corpus.py --validate`.
- `bitmaps/` — small committed samples (JPEG, HEIC, PNG, TIFF, WebP). Generated from the CR2
  corpus sample (a field of grass) with `sips`, `cwebp` and ExifTool, carrying that camera's EXIF.
- RAW samples are downloaded into a cache, not committed: `python3 scripts/fetch_format_corpus.py`.

Rules and workflow: [`docs/shared/SUPPORTED_FORMATS.md`](../../docs/shared/SUPPORTED_FORMATS.md).
