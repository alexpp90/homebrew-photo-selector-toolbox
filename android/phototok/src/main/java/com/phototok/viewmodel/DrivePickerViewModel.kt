package com.phototok.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phototok.BuildConfig
import com.phototok.data.source.googledrive.GoogleDriveAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Everything the WebView-hosted Google Picker page needs to open. */
data class DrivePickerConfig(
    val accessToken: String,
    val apiKey: String,
    val appId: String,
)

sealed interface DrivePickerUiState {
    data object Loading : DrivePickerUiState
    data class Ready(val config: DrivePickerConfig) : DrivePickerUiState
    data class Error(val message: String) : DrivePickerUiState
}

/**
 * Backs the WebView-hosted Google Picker, so the Compose layer never holds a
 * reference to [GoogleDriveAuth]. Fetches a fresh OAuth access token each time
 * the picker opens (tokens are short-lived).
 */
@HiltViewModel
class DrivePickerViewModel @Inject constructor(
    private val driveAuth: GoogleDriveAuth,
) : ViewModel() {

    private val _uiState = MutableStateFlow<DrivePickerUiState>(DrivePickerUiState.Loading)
    val uiState: StateFlow<DrivePickerUiState> = _uiState.asStateFlow()

    /** (Re-)load the picker configuration; called when the picker dialog opens. */
    fun load() {
        _uiState.value = DrivePickerUiState.Loading
        viewModelScope.launch {
            if (BuildConfig.DRIVE_PICKER_API_KEY.isEmpty() ||
                BuildConfig.DRIVE_PICKER_APP_ID.isEmpty()
            ) {
                _uiState.value = DrivePickerUiState.Error(
                    "Google Drive is not configured in this build."
                )
                return@launch
            }
            val token = driveAuth.getAccessToken()
            _uiState.value = if (token == null) {
                DrivePickerUiState.Error("Could not get Google Drive access. Please sign in again.")
            } else {
                DrivePickerUiState.Ready(
                    DrivePickerConfig(
                        accessToken = token,
                        apiKey = BuildConfig.DRIVE_PICKER_API_KEY,
                        appId = BuildConfig.DRIVE_PICKER_APP_ID,
                    )
                )
            }
        }
    }
}
