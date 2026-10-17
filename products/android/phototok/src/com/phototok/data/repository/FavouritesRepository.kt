package com.phototok.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local store of favourited photo URIs and pending system `IS_FAVORITE` sync queue (`U-17`).
 */
class FavouritesStore(
    private val storeFile: File? = null,
) {
    private val _favouritedUris = MutableStateFlow<Set<String>>(emptySet())
    val favouritedUris: StateFlow<Set<String>> = _favouritedUris.asStateFlow()

    private val _unsyncedSystemUris = MutableStateFlow<Set<String>>(emptySet())
    val unsyncedSystemUris: StateFlow<Set<String>> = _unsyncedSystemUris.asStateFlow()

    init {
        loadFromDisk()
    }

    fun isFavourite(uri: String): Boolean = uri in _favouritedUris.value

    @Synchronized
    fun setFavourite(
        uri: String,
        favourite: Boolean,
        companionUris: Collection<String> = emptyList(),
        queueForSystemSync: Boolean = false,
    ) {
        val allTargets = (listOf(uri) + companionUris).distinct()
        val updatedFavs = _favouritedUris.value.toMutableSet()
        val updatedUnsynced = _unsyncedSystemUris.value.toMutableSet()
        if (favourite) {
            updatedFavs.addAll(allTargets)
            if (queueForSystemSync) {
                updatedUnsynced.addAll(allTargets)
            }
        } else {
            updatedFavs.removeAll(allTargets.toSet())
            updatedUnsynced.removeAll(allTargets.toSet())
        }
        _favouritedUris.value = updatedFavs
        _unsyncedSystemUris.value = updatedUnsynced
        persistLocked(updatedFavs, updatedUnsynced)
    }

    @Synchronized
    fun toggleFavourite(
        uri: String,
        companionUris: Collection<String> = emptyList(),
        queueForSystemSync: Boolean = false,
    ): Boolean {
        val newState = !isFavourite(uri)
        setFavourite(
            uri = uri,
            favourite = newState,
            companionUris = companionUris,
            queueForSystemSync = queueForSystemSync,
        )
        return newState
    }

    @Synchronized
    fun markSystemSynced(uris: Collection<String>) {
        if (uris.isEmpty()) return
        val updatedUnsynced = _unsyncedSystemUris.value - uris.toSet()
        _unsyncedSystemUris.value = updatedUnsynced
        persistLocked(_favouritedUris.value, updatedUnsynced)
    }

    @Synchronized
    fun clearAll() {
        _favouritedUris.value = emptySet()
        _unsyncedSystemUris.value = emptySet()
        persistLocked(emptySet(), emptySet())
    }

    private fun loadFromDisk() {
        val file = storeFile ?: return
        if (!file.exists()) return
        try {
            val favs = LinkedHashSet<String>()
            val unsynced = LinkedHashSet<String>()
            file.forEachLine { line ->
                if (line.isBlank()) return@forEachLine
                val parts = line.split('\t', limit = 2)
                if (parts.size == 2 && parts[1].isNotBlank()) {
                    when (parts[0]) {
                        "F" -> favs.add(parts[1])
                        "U" -> unsynced.add(parts[1])
                    }
                }
            }
            _favouritedUris.value = favs
            _unsyncedSystemUris.value = unsynced
        } catch (_: Exception) {
        }
    }

    private fun persistLocked(favs: Set<String>, unsynced: Set<String>) {
        val file = storeFile ?: return
        try {
            file.parentFile?.mkdirs()
            val sb = StringBuilder()
            for (u in favs) sb.append("F\t").append(u).append('\n')
            for (u in unsynced) sb.append("U\t").append(u).append('\n')
            file.writeText(sb.toString())
        } catch (_: Exception) {
        }
    }
}

@Singleton
class FavouritesRepository(
    internal val store: FavouritesStore = FavouritesStore(storeFile = null),
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(
        store = FavouritesStore(storeFile = File(context.filesDir, "favourites_store.json")),
    )

    val favouritedUris: StateFlow<Set<String>> = store.favouritedUris
    val unsyncedSystemUris: StateFlow<Set<String>> = store.unsyncedSystemUris

    fun isFavourite(uri: String): Boolean = store.isFavourite(uri)

    fun setFavourite(
        uri: String,
        favourite: Boolean,
        companionUris: Collection<String> = emptyList(),
        queueForSystemSync: Boolean = false,
    ) = store.setFavourite(uri, favourite, companionUris, queueForSystemSync)

    fun toggleFavourite(
        uri: String,
        companionUris: Collection<String> = emptyList(),
        queueForSystemSync: Boolean = false,
    ): Boolean = store.toggleFavourite(uri, companionUris, queueForSystemSync)

    fun markSystemSynced(uris: Collection<String>) = store.markSystemSynced(uris)
}
