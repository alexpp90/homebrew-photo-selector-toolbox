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
import com.phototok.data.model.RecentPath
import com.phototok.domain.CollectionAction
import com.phototok.domain.FileTypeFilter
import com.phototok.domain.SwipeAction
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
        private val KEY_LEFT_SWIPE_ACTION = stringPreferencesKey("left_swipe_action")
        private val KEY_LEFT_SWIPE_URI = stringPreferencesKey("left_swipe_uri")
        private val KEY_TRASH_CONFIRM = booleanPreferencesKey("trash_confirm")
        private val KEY_DIRECT_DELETE_CONFIRM = booleanPreferencesKey("direct_delete_confirm")
        private val KEY_SORT_BY_ORIENTATION = booleanPreferencesKey("sort_by_orientation")
        private val KEY_RANDOMIZE_ORDER = booleanPreferencesKey("randomize_order")
        private val KEY_GESTURE_TUTORIAL_TS = longPreferencesKey("gesture_tutorial_ts")
        private val KEY_FILE_TYPE_FILTER = stringPreferencesKey("file_type_filter")
        private val KEY_SHOW_EXIF_OVERLAY = booleanPreferencesKey("show_exif_overlay")
        private val KEY_MOVE_RELATED_FILES = booleanPreferencesKey("move_related_files")
        private val KEY_RECENT_PATHS_ENABLED = booleanPreferencesKey("recent_paths_enabled")
        private val KEY_RECENT_PATHS_COUNT = intPreferencesKey("recent_paths_count")
        private val KEY_RECENT_PATHS = stringPreferencesKey("recent_paths")

        const val DEFAULT_SELECTION_FOLDER_NAME = "Selection"
        const val DEFAULT_SORTING_ENABLED = true
        const val DEFAULT_RECENT_PATHS_COUNT = 3
        private const val MAX_STORED_RECENT_PATHS = RecentPathCodec.MAX_STORED

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

    /** Action to perform when adding a photo to the collection. */
    val phoneCollectionAction: Flow<CollectionAction> = context.dataStore.data.map { prefs ->
        CollectionAction.fromKey(prefs[KEY_COLLECTION_ACTION])
    }

    /** Optional custom collection target folder URI. */
    val phoneCollectionUri: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_COLLECTION_URI]
    }

    /** Action to perform on a left swipe. */
    val phoneLeftSwipeAction: Flow<SwipeAction> = context.dataStore.data.map { prefs ->
        SwipeAction.fromKey(prefs[KEY_LEFT_SWIPE_ACTION])
    }

    /** Optional custom left swipe target folder URI. */
    val phoneLeftSwipeUri: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_LEFT_SWIPE_URI]
    }

    /** Whether to show trash confirmation dialog (default true). */
    val phoneTrashConfirmEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_TRASH_CONFIRM] ?: true
    }

    /** Whether to show direct delete confirmation dialog (default true). */
    val phoneDirectDeleteConfirmEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_DIRECT_DELETE_CONFIRM] ?: true
    }

    /** Sort images horizontal-first, then vertical (default false; base order is by date). */
    val phoneSortByOrientation: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SORT_BY_ORIENTATION] ?: false
    }

    /** Randomize picture order (default false). */
    val phoneRandomizeOrder: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_RANDOMIZE_ORDER] ?: false
    }

    /** Timestamp (millis) when gesture tutorial was last shown. 0 = never. */
    val phoneGestureTutorialTs: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[KEY_GESTURE_TUTORIAL_TS] ?: 0L
    }

    /** File-type filter applied to the feed. */
    val phoneFileTypeFilter: Flow<FileTypeFilter> = context.dataStore.data.map { prefs ->
        FileTypeFilter.fromKey(prefs[KEY_FILE_TYPE_FILTER])
    }

    /** Whether to show EXIF stats overlay (default false; toggled via the logo). */
    val phoneShowExifOverlay: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SHOW_EXIF_OVERLAY] ?: false
    }

    /** Whether collection/delete actions also affect same-name sibling files (default false). */
    val phoneMoveRelatedFiles: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_MOVE_RELATED_FILES] ?: false
    }

    /** Whether to show the recent-folders list on the landing screen (default true). */
    val phoneRecentPathsEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_RECENT_PATHS_ENABLED] ?: true
    }

    /** How many recent folders to show on the landing screen (default 3). */
    val phoneRecentPathsCount: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_RECENT_PATHS_COUNT] ?: DEFAULT_RECENT_PATHS_COUNT
    }

    /** Most-recently-used source folders, newest first. */
    val phoneRecentPaths: Flow<List<RecentPath>> = context.dataStore.data.map { prefs ->
        RecentPathCodec.decode(prefs[KEY_RECENT_PATHS])
    }

    suspend fun setPhoneCollectionAction(action: CollectionAction) {
        context.dataStore.edit { prefs -> prefs[KEY_COLLECTION_ACTION] = action.key }
    }

    suspend fun setPhoneCollectionUri(uri: String?) {
        context.dataStore.edit { prefs ->
            if (uri != null) prefs[KEY_COLLECTION_URI] = uri
            else prefs.remove(KEY_COLLECTION_URI)
        }
    }

    suspend fun setPhoneLeftSwipeAction(action: SwipeAction) {
        context.dataStore.edit { prefs -> prefs[KEY_LEFT_SWIPE_ACTION] = action.key }
    }

    suspend fun setPhoneLeftSwipeUri(uri: String?) {
        context.dataStore.edit { prefs ->
            if (uri != null) prefs[KEY_LEFT_SWIPE_URI] = uri
            else prefs.remove(KEY_LEFT_SWIPE_URI)
        }
    }

    suspend fun setPhoneTrashConfirmEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_TRASH_CONFIRM] = enabled }
    }

    suspend fun setPhoneDirectDeleteConfirmEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_DIRECT_DELETE_CONFIRM] = enabled }
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

    suspend fun setPhoneFileTypeFilter(filter: FileTypeFilter) {
        context.dataStore.edit { prefs -> prefs[KEY_FILE_TYPE_FILTER] = filter.key }
    }

    suspend fun setPhoneShowExifOverlay(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_SHOW_EXIF_OVERLAY] = enabled }
    }

    suspend fun setPhoneMoveRelatedFiles(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_MOVE_RELATED_FILES] = enabled }
    }

    suspend fun setPhoneRecentPathsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_RECENT_PATHS_ENABLED] = enabled }
    }

    suspend fun setPhoneRecentPathsCount(count: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_RECENT_PATHS_COUNT] = count.coerceAtLeast(1) }
    }

    /** Record a folder as recently used, moving it to the front and de-duplicating. */
    suspend fun addRecentPath(uri: String, name: String) {
        context.dataStore.edit { prefs ->
            val current = RecentPathCodec.decode(prefs[KEY_RECENT_PATHS])
            val updated = RecentPathCodec.add(current, uri, name, MAX_STORED_RECENT_PATHS)
            prefs[KEY_RECENT_PATHS] = RecentPathCodec.encode(updated)
        }
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
