package com.phototok.domain

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lightweight JSON journal (`filesDir/card_transfer_journal.json`) for removable-card
 * safety (`U-14b`) and the *"Done with this card"* workflow (`U-14c`).
 *
 * Tracks:
 * 1. In-flight transfers (`inFlight`) so a mid-copy surprise card pull can clean up any
 *    partial destination file and tell the user which file was interrupted.
 * 2. Card source URIs that were successfully copied out during a card session
 *    (`copiedBySource`) so the user can optionally delete only the copied files from the card
 *    via *"Done with this card"*.
 */
class CardTransferJournal(
    private val journalFile: File? = null,
) {

    data class InFlightTransfer(
        val sourceUri: String,
        val destFolderUri: String,
        val targetFileName: String,
        val volumeUuid: String? = null,
        val destFileUri: String? = null,
        val startedAtMs: Long = System.currentTimeMillis(),
    )

    private val inFlight = LinkedHashMap<String, InFlightTransfer>()
    private val copiedBySource = LinkedHashMap<String, MutableSet<String>>()

    init {
        loadFromDisk()
    }

    @Synchronized
    fun recordTransferStart(entry: InFlightTransfer) {
        inFlight[entry.sourceUri] = entry
        persistLocked()
    }

    @Synchronized
    fun updateInFlightDestinationUri(sourceUri: String, destFileUri: String) {
        val current = inFlight[sourceUri] ?: return
        inFlight[sourceUri] = current.copy(destFileUri = destFileUri)
        persistLocked()
    }

    @Synchronized
    fun recordTransferComplete(
        sourceUri: String,
        cardSourceKey: String? = null,
    ) {
        inFlight.remove(sourceUri)
        if (!cardSourceKey.isNullOrBlank()) {
            copiedBySource.getOrPut(cardSourceKey) { LinkedHashSet() }.add(sourceUri)
        }
        persistLocked()
    }

    @Synchronized
    fun recordTransferFailed(sourceUri: String) {
        if (inFlight.remove(sourceUri) != null) {
            persistLocked()
        }
    }

    @Synchronized
    fun inFlightTransfers(): List<InFlightTransfer> = inFlight.values.toList()

    @Synchronized
    fun popInterruptedTransfers(volumeUuid: String? = null): List<InFlightTransfer> {
        val matching = inFlight.values.filter { entry ->
            volumeUuid == null ||
                entry.volumeUuid == null ||
                entry.volumeUuid.equals(volumeUuid, ignoreCase = true)
        }
        if (matching.isNotEmpty()) {
            matching.forEach { inFlight.remove(it.sourceUri) }
            persistLocked()
        }
        return matching
    }

    @Synchronized
    fun copiedCardUris(cardSourceKey: String): Set<String> =
        copiedBySource[cardSourceKey]?.toSet() ?: emptySet()

    @Synchronized
    fun copiedCountForFolder(cardSourceKey: String): Int =
        copiedBySource[cardSourceKey]?.size ?: 0

    @Synchronized
    fun recordCopy(
        sourceUri: String,
        destUri: String,
        byteSize: Long,
        sourceFolderUri: String,
    ) {
        recordTransferComplete(sourceUri = sourceUri, cardSourceKey = sourceFolderUri)
    }

    @Synchronized
    fun removeCopiedCardUris(cardSourceKey: String, uris: Collection<String>) {
        val set = copiedBySource[cardSourceKey] ?: return
        set.removeAll(uris.toSet())
        if (set.isEmpty()) {
            copiedBySource.remove(cardSourceKey)
        }
        persistLocked()
    }

    @Synchronized
    fun clearSource(cardSourceKey: String) {
        if (copiedBySource.remove(cardSourceKey) != null) {
            persistLocked()
        }
    }

    @Synchronized
    fun clearAll() {
        inFlight.clear()
        copiedBySource.clear()
        persistLocked()
    }

    @Synchronized
    private fun loadFromDisk() {
        val file = journalFile ?: return
        if (!file.exists()) return
        try {
            file.forEachLine { line ->
                if (line.isBlank()) return@forEachLine
                val parts = line.split('\t')
                when (parts.firstOrNull()) {
                    "I" -> if (parts.size >= 7) {
                        val src = parts[1]
                        if (src.isNotBlank()) {
                            inFlight[src] = InFlightTransfer(
                                sourceUri = src,
                                destFolderUri = parts[2],
                                targetFileName = parts[3],
                                volumeUuid = parts[4].takeIf { it.isNotBlank() },
                                destFileUri = parts[5].takeIf { it.isNotBlank() },
                                startedAtMs = parts[6].toLongOrNull() ?: 0L,
                            )
                        }
                    }
                    "C" -> if (parts.size >= 3) {
                        val key = parts[1]
                        val uri = parts[2]
                        if (key.isNotBlank() && uri.isNotBlank()) {
                            copiedBySource.getOrPut(key) { LinkedHashSet() }.add(uri)
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun persistLocked() {
        val file = journalFile ?: return
        try {
            file.parentFile?.mkdirs()
            val sb = StringBuilder()
            for (entry in inFlight.values) {
                sb.append("I\t")
                    .append(entry.sourceUri).append('\t')
                    .append(entry.destFolderUri).append('\t')
                    .append(entry.targetFileName).append('\t')
                    .append(entry.volumeUuid.orEmpty()).append('\t')
                    .append(entry.destFileUri.orEmpty()).append('\t')
                    .append(entry.startedAtMs).append('\n')
            }
            for ((source, uris) in copiedBySource) {
                for (u in uris) {
                    sb.append("C\t").append(source).append('\t').append(u).append('\n')
                }
            }
            file.writeText(sb.toString())
        } catch (_: Exception) {
        }
    }
}
