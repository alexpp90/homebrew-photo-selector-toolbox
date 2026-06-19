package com.photoselectortoolbox.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.photoselectortoolbox.domain.grouping.GroupingLevel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
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
}
