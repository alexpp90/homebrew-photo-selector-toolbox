package com.photoselectortoolbox.domain.format

/**
 * The one list of file formats Android Desktop knows — discovery, RAW/JPEG
 * sorting into the Selection, rating write-back and the decode path all ask
 * here. It implements the cross-product contract in
 * `testdata/formats/manifest.json` (docs/shared/SUPPORTED_FORMATS.md);
 * `FormatConformanceTest` fails if the two drift apart.
 *
 * Extensions are matched case-insensitively and without the dot.
 */
object ImageFormats {

    /**
     * Camera RAW containers. The contract's required and recognized RAW tiers,
     * plus further vendor formats that are discovered and sorted as RAW.
     */
    val RAW_EXTENSIONS: Set<String> = setOf(
        // required
        "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "pef", "dng", "srw",
        // recognized
        "3fr", "fff", "iiq", "rwl", "raw", "sr2", "srf", "mrw", "x3f", "erf", "mef", "mos",
        // further vendor RAWs
        "ari", "bay", "cap", "eip", "mdc", "obm", "ptx", "pxn", "rwz",
    )

    val JPEG_EXTENSIONS: Set<String> = setOf("jpg", "jpeg")

    val TIFF_EXTENSIONS: Set<String> = setOf("tif", "tiff")

    /** Rendered (non-RAW) images. */
    val BITMAP_EXTENSIONS: Set<String> =
        JPEG_EXTENSIONS + TIFF_EXTENSIONS + setOf("png", "webp", "heic", "heif", "bmp", "gif")

    /** Everything folder discovery picks up. */
    val SUPPORTED_EXTENSIONS: Set<String> = RAW_EXTENSIONS + BITMAP_EXTENSIONS

    const val XMP_EXTENSION = "xmp"

    /** Lightroom's suffix for an edited derivative (`DSC001-Edit.tif`). */
    const val EDIT_SUFFIX = "-Edit"

    /** Lower-case extension of [fileName] without the dot, or "" if it has none. */
    fun extensionOf(fileName: String): String =
        fileName.substringAfterLast('/').substringAfterLast('.', "").lowercase()

    fun isSupported(fileName: String): Boolean = extensionOf(fileName) in SUPPORTED_EXTENSIONS
    fun isRaw(fileName: String): Boolean = extensionOf(fileName) in RAW_EXTENSIONS
    fun isJpeg(fileName: String): Boolean = extensionOf(fileName) in JPEG_EXTENSIONS
    fun isTiff(fileName: String): Boolean = extensionOf(fileName) in TIFF_EXTENSIONS

    /** Where a file goes inside the Selection folder when File Sorting is on. */
    enum class SelectionSubfolder { RAW, JPEG, ROOT }

    /**
     * Sorting rule for the Selection folder:
     * - Lightroom edits (`*-Edit.*`) → RAW, whatever their extension;
     * - RAW → RAW; JPEG → JPEG;
     * - XMP sidecars named after a RAW (`IMG_1.CR3.xmp`) → RAW;
     * - everything else → the Selection root.
     */
    fun selectionSubfolder(fileName: String): SelectionSubfolder {
        val name = fileName.substringAfterLast('/')
        val extension = extensionOf(name)
        val stem = name.substringBeforeLast('.')
        return when {
            stem.endsWith(EDIT_SUFFIX) -> SelectionSubfolder.RAW
            extension in RAW_EXTENSIONS -> SelectionSubfolder.RAW
            extension in JPEG_EXTENSIONS -> SelectionSubfolder.JPEG
            extension == XMP_EXTENSION ->
                if (stem.contains('.') && isRaw(stem)) SelectionSubfolder.RAW else SelectionSubfolder.ROOT
            else -> SelectionSubfolder.ROOT
        }
    }
}
