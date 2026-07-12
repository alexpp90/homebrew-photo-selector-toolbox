package com.phototok.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.drivePickedDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "phototok_drive_picked"
)

/**
 * Persists the Google Drive file selections the user granted via the Google
 * Picker, keyed for `gdrive-picked://<key>` source URIs. Needed because the
 * `drive.file` scope only allows access to explicitly picked files — the app
 * re-resolves a picked selection (e.g. from the recents list) by intersecting
 * these stored IDs with the currently accessible files.
 */
@Singleton
class DrivePickedStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private val KEY_PICKED_SELECTIONS = stringPreferencesKey("drive_picked_selections")
    }

    suspend fun save(selection: DrivePickedSelection) {
        context.drivePickedDataStore.edit { prefs ->
            val current = DrivePickedCodec.decode(prefs[KEY_PICKED_SELECTIONS])
            prefs[KEY_PICKED_SELECTIONS] =
                DrivePickedCodec.encode(DrivePickedCodec.add(current, selection))
        }
    }

    suspend fun load(key: String): DrivePickedSelection? {
        val raw = context.drivePickedDataStore.data.map { it[KEY_PICKED_SELECTIONS] }.first()
        return DrivePickedCodec.find(DrivePickedCodec.decode(raw), key)
    }
}
