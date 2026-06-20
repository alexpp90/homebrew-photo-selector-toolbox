package com.photoselectortoolbox.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.photoselectortoolbox.domain.grouping.GroupingLevel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "photo_selector_settings"
)

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private val KEY_SELECTION_FOLDER_NAME = stringPreferencesKey("selection_folder_name")
        private val KEY_SORTING_ENABLED = booleanPreferencesKey("sorting_enabled")
        private val KEY_GROUPING_ENABLED = booleanPreferencesKey("grouping_enabled")
        private val KEY_GROUPING_LEVEL = stringPreferencesKey("grouping_level")
        private val KEY_LAST_FOLDER_URI = stringPreferencesKey("last_folder_uri")
        private val KEY_ANALYSIS_THREAD_COUNT = intPreferencesKey("analysis_thread_count")

        // Phone-mode settings
        private val KEY_PHONE_COLLECTION_ACTION = stringPreferencesKey("phone_collection_action")
        private val KEY_PHONE_COLLECTION_URI = stringPreferencesKey("phone_collection_uri")
        private val KEY_PHONE_DELETE_CONFIRM = booleanPreferencesKey("phone_delete_confirm")
        private val KEY_PHONE_SORT_BY_ORIENTATION = booleanPreferencesKey("phone_sort_by_orientation")
        private val KEY_PHONE_GESTURE_TUTORIAL_TS = longPreferencesKey("phone_gesture_tutorial_ts")
        private val KEY_PHONE_FORCE_MODE = stringPreferencesKey("phone_force_mode")
        private val KEY_PHONE_FILE_TYPE_FILTER = stringPreferencesKey("phone_file_type_filter")
        private val KEY_FULLSCREEN_BUTTONS_ENABLED = booleanPreferencesKey("fullscreen_buttons_enabled")
        private val KEY_FULLSCREEN_GESTURE_ACTION = stringPreferencesKey("fullscreen_gesture_action")

        const val DEFAULT_SELECTION_FOLDER_NAME = "Selection"
        const val DEFAULT_SORTING_ENABLED = true
        const val DEFAULT_GROUPING_ENABLED = false
        val DEFAULT_GROUPING_LEVEL = GroupingLevel.TIME_FILENAME
        val DEFAULT_ANALYSIS_THREAD_COUNT = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
    }

    val selectionFolderName: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_SELECTION_FOLDER_NAME] ?: DEFAULT_SELECTION_FOLDER_NAME
    }

    val sortingEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SORTING_ENABLED] ?: DEFAULT_SORTING_ENABLED
    }

    val groupingEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_GROUPING_ENABLED] ?: DEFAULT_GROUPING_ENABLED
    }

    val groupingLevel: Flow<GroupingLevel> = context.dataStore.data.map { prefs ->
        val value = prefs[KEY_GROUPING_LEVEL]
        if (value != null) {
            try {
                GroupingLevel.valueOf(value)
            } catch (_: IllegalArgumentException) {
                DEFAULT_GROUPING_LEVEL
            }
        } else {
            DEFAULT_GROUPING_LEVEL
        }
    }

    val lastFolderUri: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_LAST_FOLDER_URI]
    }

    val analysisThreadCount: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_ANALYSIS_THREAD_COUNT] ?: DEFAULT_ANALYSIS_THREAD_COUNT
    }

    val fullscreenButtonsEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_FULLSCREEN_BUTTONS_ENABLED] ?: true
    }

    val fullscreenGestureAction: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_FULLSCREEN_GESTURE_ACTION] ?: "copy"
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

    suspend fun setGroupingEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_GROUPING_ENABLED] = enabled
        }
    }

    suspend fun setGroupingLevel(level: GroupingLevel) {
        context.dataStore.edit { prefs ->
            prefs[KEY_GROUPING_LEVEL] = level.name
        }
    }

    suspend fun setLastFolderUri(uri: String?) {
        context.dataStore.edit { prefs ->
            if (uri != null) {
                prefs[KEY_LAST_FOLDER_URI] = uri
            } else {
                prefs.remove(KEY_LAST_FOLDER_URI)
            }
        }
    }

    suspend fun setAnalysisThreadCount(count: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ANALYSIS_THREAD_COUNT] = count.coerceIn(1, 4)
        }
    }

    suspend fun setFullscreenButtonsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_FULLSCREEN_BUTTONS_ENABLED] = enabled
        }
    }

    suspend fun setFullscreenGestureAction(action: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_FULLSCREEN_GESTURE_ACTION] = action
        }
    }

    // ── Phone-mode settings ──────────────────────────────────────────────

    /** "copy" (default) or "move" */
    val phoneCollectionAction: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_PHONE_COLLECTION_ACTION] ?: "copy"
    }

    /** Optional custom collection target folder URI. null = use Selection subfolder. */
    val phoneCollectionUri: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_PHONE_COLLECTION_URI]
    }

    /** Whether to show delete confirmation dialog (default true). */
    val phoneDeleteConfirmEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_PHONE_DELETE_CONFIRM] ?: true
    }

    /** Sort images horizontal-first, then vertical (default true). */
    val phoneSortByOrientation: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_PHONE_SORT_BY_ORIENTATION] ?: true
    }

    /** Timestamp (millis) when gesture tutorial was last shown. 0 = never. */
    val phoneGestureTutorialTs: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[KEY_PHONE_GESTURE_TUTORIAL_TS] ?: 0L
    }

    /**
     * Force mode for large-screen devices: "phone", "desktop", or null (auto).
     * On compact devices this is ignored.
     */
    val phoneForcedMode: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_PHONE_FORCE_MODE]
    }

    suspend fun setPhoneCollectionAction(action: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PHONE_COLLECTION_ACTION] = action
        }
    }

    suspend fun setPhoneCollectionUri(uri: String?) {
        context.dataStore.edit { prefs ->
            if (uri != null) prefs[KEY_PHONE_COLLECTION_URI] = uri
            else prefs.remove(KEY_PHONE_COLLECTION_URI)
        }
    }

    suspend fun setPhoneDeleteConfirmEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PHONE_DELETE_CONFIRM] = enabled
        }
    }

    suspend fun setPhoneSortByOrientation(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PHONE_SORT_BY_ORIENTATION] = enabled
        }
    }

    suspend fun setPhoneGestureTutorialTs(ts: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PHONE_GESTURE_TUTORIAL_TS] = ts
        }
    }

    suspend fun setPhoneForcedMode(mode: String?) {
        context.dataStore.edit { prefs ->
            if (mode != null) prefs[KEY_PHONE_FORCE_MODE] = mode
            else prefs.remove(KEY_PHONE_FORCE_MODE)
        }
    }

    // ── File type filter ─────────────────────────────────────────────────

    /** "all" (default), "raw", or "jpg" */
    val phoneFileTypeFilter: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_PHONE_FILE_TYPE_FILTER] ?: "all"
    }

    suspend fun setPhoneFileTypeFilter(filter: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PHONE_FILE_TYPE_FILTER] = filter
        }
    }

    // ── Per-folder last position ─────────────────────────────────────────

    /**
     * Persist the last viewed image index for a given folder URI.
     * Key is derived by hashing the URI to keep preference keys short.
     */
    suspend fun setFolderLastPosition(folderUri: String, index: Int) {
        val key = intPreferencesKey("folder_pos_${folderUri.hashCode()}")
        context.dataStore.edit { prefs ->
            prefs[key] = index
        }
    }

    /** Retrieve the last viewed position for a folder. Returns 0 if none stored. */
    suspend fun getFolderLastPosition(folderUri: String): Int {
        val key = intPreferencesKey("folder_pos_${folderUri.hashCode()}")
        return context.dataStore.data.map { prefs -> prefs[key] ?: 0 }.first()
    }
}
