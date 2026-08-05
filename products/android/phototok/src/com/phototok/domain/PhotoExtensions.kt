package com.phototok.domain

/**
 * Single source of truth for photographic file-extension sets.
 * Previously duplicated in PhoneModeViewModel and ImageRepositoryImpl.
 */
object PhotoExtensions {

    val RAW = setOf(
        "arw", "cr2", "cr3", "nef", "nrw", "orf", "raf", "rw2",
        "pef", "srw", "dng", "raw", "3fr", "ari", "bay", "cap",
        "iiq", "eip", "erf", "fff", "mef", "mdc", "mos", "mrw",
        "obm", "ptx", "pxn", "rwl", "rwz", "sr2", "srf", "x3f",
    )

    val JPEG = setOf("jpg", "jpeg")

    const val XMP = "xmp"

    /** Lowercased extension of [fileName], or "" when there is none. */
    fun extensionOf(fileName: String): String =
        fileName.substringAfterLast('.', "").lowercase()

    fun isRaw(fileName: String): Boolean = extensionOf(fileName) in RAW

    fun isJpeg(fileName: String): Boolean = extensionOf(fileName) in JPEG
}
