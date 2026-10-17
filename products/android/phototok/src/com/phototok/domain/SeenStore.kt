package com.phototok.domain

import com.phototok.data.model.ImageItem
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Append-only seen-photo fingerprint store (`U-15`).
 *
 * Uses a 64-bit FNV-1a fingerprint of `(documentIdOrUri, fileSize, lastModified)` so
 * re-grants of the same folder recognize already-reviewed photos.
 */
class SeenStore(
    private val logFile: File? = null,
    private val maxEntriesBeforeCompact: Int = MAX_ENTRIES_BEFORE_COMPACT,
) {

    companion object {
        /** Dwell threshold in milliseconds before a viewed card is marked seen. */
        const val SEEN_DWELL_MS = 500L

        /** Compaction threshold for the append-only seen log. */
        const val MAX_ENTRIES_BEFORE_COMPACT = 250_000

        private const val FNV_64_OFFSET_BASIS = -0x340d631b7bdddcdbL // 0xcbf29ce484222325UL
        private const val FNV_64_PRIME = 0x100000001b3L

        private const val OP_ADD = "A"
        private const val OP_RESET_SOURCE = "R"
        private const val OP_RESET_ALL = "X"

        /**
         * Compute a deterministic 64-bit FNV-1a fingerprint from [documentKey], [fileSize],
         * and [lastModified].
         */
        fun fingerprint(documentKey: String, fileSize: Long, lastModified: Long): Long {
            var hash = FNV_64_OFFSET_BASIS
            fun mixString(s: String) {
                for (i in 0 until s.length) {
                    hash = hash xor (s[i].code.toLong() and 0xFFFFL)
                    hash *= FNV_64_PRIME
                }
            }
            fun mixLong(v: Long) {
                for (shift in 0 until 64 step 8) {
                    hash = hash xor ((v ushr shift) and 0xFFL)
                    hash *= FNV_64_PRIME
                }
            }
            mixString(documentKey)
            hash = hash xor '|'.code.toLong()
            hash *= FNV_64_PRIME
            mixLong(fileSize)
            hash = hash xor '|'.code.toLong()
            hash *= FNV_64_PRIME
            mixLong(lastModified)
            return hash
        }

        /** Extract stable document ID suffix from a SAF URI if present, else full URI. */
        fun canonicalDocumentKey(uri: String): String {
            val marker = "/document/"
            val idx = uri.indexOf(marker)
            return if (idx >= 0) uri.substring(idx + marker.length) else uri
        }

        fun fingerprintOf(item: ImageItem): Long =
            fingerprint(
                documentKey = canonicalDocumentKey(item.uri),
                fileSize = item.fileSize,
                lastModified = item.lastModified,
            )
    }

    private val bySource = ConcurrentHashMap<String, MutableSet<Long>>()
    private val allFingerprints: MutableSet<Long> = ConcurrentHashMap.newKeySet()
    private var logLineCount = 0

    init {
        loadFromDisk()
    }

    @Synchronized
    private fun loadFromDisk() {
        val file = logFile ?: return
        if (!file.exists()) return
        var lines = 0
        try {
            file.forEachLine { line ->
                if (line.isBlank()) return@forEachLine
                lines++
                val parts = line.split('\t')
                when (parts.firstOrNull()) {
                    OP_ADD -> if (parts.size >= 3) {
                        val source = parts[1]
                        val fp = parts[2].toULongOrNull(16)?.toLong() ?: return@forEachLine
                        bySource.getOrPut(source) { ConcurrentHashMap.newKeySet() }.add(fp)
                        allFingerprints.add(fp)
                    }
                    OP_RESET_SOURCE -> if (parts.size >= 2) {
                        val source = parts[1]
                        bySource.remove(source)
                        rebuildAllFingerprints()
                    }
                    OP_RESET_ALL -> {
                        bySource.clear()
                        allFingerprints.clear()
                    }
                }
            }
            logLineCount = lines
            if (logLineCount > maxEntriesBeforeCompact) {
                compactLocked()
            }
        } catch (_: Exception) {
            // Corrupt or unreadable log line does not crash startup.
        }
    }

    private fun rebuildAllFingerprints() {
        allFingerprints.clear()
        for (set in bySource.values) {
            allFingerprints.addAll(set)
        }
    }

    fun isSeen(item: ImageItem, sourceUri: String? = null): Boolean {
        val fp = fingerprintOf(item)
        return if (sourceUri != null) {
            bySource[sourceUri]?.contains(fp) == true || allFingerprints.contains(fp)
        } else {
            allFingerprints.contains(fp)
        }
    }

    @Synchronized
    fun markSeen(item: ImageItem, sourceUri: String): Boolean {
        val fp = fingerprintOf(item)
        val sourceSet = bySource.getOrPut(sourceUri) { ConcurrentHashMap.newKeySet() }
        val addedToSource = sourceSet.add(fp)
        val addedGlobal = allFingerprints.add(fp)
        if (!addedToSource && !addedGlobal) return false

        appendLineLocked("$OP_ADD\t$sourceUri\t${java.lang.Long.toHexString(fp)}")
        return true
    }

    fun filterSeenUris(items: List<ImageItem>, sourceUri: String? = null): Set<String> {
        if (items.isEmpty() || allFingerprints.isEmpty()) return emptySet()
        val result = HashSet<String>()
        for (item in items) {
            if (isSeen(item, sourceUri)) {
                result.add(item.uri)
            }
        }
        return result
    }

    fun countSeen(items: List<ImageItem>, sourceUri: String? = null): Int =
        items.count { isSeen(it, sourceUri) }

    @Synchronized
    fun resetSource(sourceUri: String) {
        bySource.remove(sourceUri)
        rebuildAllFingerprints()
        appendLineLocked("$OP_RESET_SOURCE\t$sourceUri")
        compactLocked()
    }

    @Synchronized
    fun resetAll() {
        bySource.clear()
        allFingerprints.clear()
        logLineCount = 0
        try {
            logFile?.writeText("")
        } catch (_: Exception) {
        }
    }

    @Synchronized
    fun compact() {
        compactLocked()
    }

    private fun appendLineLocked(line: String) {
        val file = logFile ?: return
        try {
            file.parentFile?.mkdirs()
            file.appendText("$line\n")
            logLineCount++
            if (logLineCount > maxEntriesBeforeCompact) {
                compactLocked()
            }
        } catch (_: Exception) {
        }
    }

    private fun compactLocked() {
        val file = logFile ?: return
        try {
            file.parentFile?.mkdirs()
            val tempFile = File(file.parentFile, "${file.name}.tmp")
            var written = 0
            tempFile.bufferedWriter().use { writer ->
                for ((source, fps) in bySource) {
                    for (fp in fps) {
                        writer.write("$OP_ADD\t$source\t${java.lang.Long.toHexString(fp)}\n")
                        written++
                    }
                }
            }
            if (!tempFile.renameTo(file)) {
                tempFile.copyTo(file, overwrite = true)
                tempFile.delete()
            }
            logLineCount = written
        } catch (_: Exception) {
        }
    }
}
