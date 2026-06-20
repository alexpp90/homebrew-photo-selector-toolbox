package com.phototok.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "phototok_settings"
)

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private val KEY_SELECTION_FOLDER_NAME = stringPreferencesKey("selection_folder_name")
        private val KEY_SORTING_ENABLED = booleanPreferencesKey("sorting_enabled")
        private val KEY_LAST_FOLDER_URI = stringPreferencesKey("last_folder_uri")

        private val KEY_COLLECTION_ACTION = stringPreferencesKey("collection_action")
        private val KEY_COLLECTION_URI = stringPreferencesKey("collection_uri")
        private val KEY_DELETE_CONFIRM = booleanPreferencesKey("delete_confirm")
        private val KEY_SORT_BY_ORIENTATION = booleanPreferencesKey("sort_by_orientation")
        private val KEY_RANDOMIZE_ORDER = booleanPreferencesKey("randomize_order")
        private val KEY_GESTURE_TUTORIAL_TS = longPreferencesKey("gesture_tutorial_ts")
        private val KEY_FILE_TYPE_FILTER = stringPreferencesKey("file_type_filter")
        private val KEY_SHOW_EXIF_OVERLAY = booleanPreferencesKey("show_exif_overlay")

        const val DEFAULT_SELECTION_FOLDER_NAME = "Selection"
        const val DEFAULT_SORTING_ENABLED = true
    }

    val selectionFolderName: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_SELECTION_FOLDER_NAME] ?: DEFAULT_SELECTION_FOLDER_NAME
    }

    val sortingEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SORTING_ENABLED] ?: DEFAULT_SORTING_ENABLED
    }

    val lastFolderUri: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_LAST_FOLDER_URI]
    }

    suspend fun setSelectionFolderName(name: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SELECTION_FOLDER_NAME] = name
        }
    }

    suspend fun setSortingEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SORTING_ENABLED] = enabled
        }
    }

    suspend fun setLastFolderUri(uri: String?) {
        context.dataStore.edit { prefs ->
            if (uri != null) prefs[KEY_LAST_FOLDER_URI] = uri
            else prefs.remove(KEY_LAST_FOLDER_URI)
        }
    }

    // ── Collection & browsing settings ──────────────────────────────────

    /** "copy" (default) or "move" */
    val phoneCollectionAction: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_COLLECTION_ACTION] ?: "copy"
    }

    /** Optional custom collection target folder URI. */
    val phoneCollectionUri: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_COLLECTION_URI]
    }

    /** Whether to show delete confirmation dialog (default true). */
    val phoneDeleteConfirmEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_DELETE_CONFIRM] ?: true
    }

    /** Sort images horizontal-first, then vertical (default true). */
    val phoneSortByOrientation: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SORT_BY_ORIENTATION] ?: true
    }

    /** Randomize picture order (default false). */
    val phoneRandomizeOrder: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_RANDOMIZE_ORDER] ?: false
    }

    /** Timestamp (millis) when gesture tutorial was last shown. 0 = never. */
    val phoneGestureTutorialTs: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[KEY_GESTURE_TUTORIAL_TS] ?: 0L
    }

    /** "all" (default), "raw", or "jpg" */
    val phoneFileTypeFilter: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_FILE_TYPE_FILTER] ?: "all"
    }

    /** Whether to show EXIF stats overlay (default true). */
    val phoneShowExifOverlay: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SHOW_EXIF_OVERLAY] ?: true
    }

    suspend fun setPhoneCollectionAction(action: String) {
        context.dataStore.edit { prefs -> prefs[KEY_COLLECTION_ACTION] = action }
    }

    suspend fun setPhoneCollectionUri(uri: String?) {
        context.dataStore.edit { prefs ->
            if (uri != null) prefs[KEY_COLLECTION_URI] = uri
            else prefs.remove(KEY_COLLECTION_URI)
        }
    }

    suspend fun setPhoneDeleteConfirmEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_DELETE_CONFIRM] = enabled }
    }

    suspend fun setPhoneSortByOrientation(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_SORT_BY_ORIENTATION] = enabled }
    }

    suspend fun setPhoneRandomizeOrder(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_RANDOMIZE_ORDER] = enabled }
    }

    suspend fun setPhoneGestureTutorialTs(ts: Long) {
        context.dataStore.edit { prefs -> prefs[KEY_GESTURE_TUTORIAL_TS] = ts }
    }

    suspend fun setPhoneFileTypeFilter(filter: String) {
        context.dataStore.edit { prefs -> prefs[KEY_FILE_TYPE_FILTER] = filter }
    }

    suspend fun setPhoneShowExifOverlay(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_SHOW_EXIF_OVERLAY] = enabled }
    }

    // ── Per-folder last position ─────────────────────────────────────────

    suspend fun setFolderLastPosition(folderUri: String, index: Int) {
        val key = intPreferencesKey("folder_pos_${folderUri.hashCode()}")
        context.dataStore.edit { prefs -> prefs[key] = index }
    }

    suspend fun getFolderLastPosition(folderUri: String): Int {
        val key = intPreferencesKey("folder_pos_${folderUri.hashCode()}")
        return context.dataStore.data.map { prefs -> prefs[key] ?: 0 }.first()
    }
}
