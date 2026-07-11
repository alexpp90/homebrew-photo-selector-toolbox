package com.phototok.ui.components

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phototok.data.source.googledrive.DrivePickerResponse
import com.phototok.data.source.googledrive.PickedDriveDoc
import com.phototok.viewmodel.DrivePickerConfig
import com.phototok.viewmodel.DrivePickerUiState
import com.phototok.viewmodel.DrivePickerViewModel
import org.json.JSONObject

/**
 * Full-screen Google Picker hosted in a WebView.
 *
 * Under the `drive.file` scope the app cannot browse the user's Drive; the
 * Google Picker is Google's UI for the user to explicitly grant the app
 * access to individual files. Users multi-select the photos to review;
 * selecting whole folders is intentionally disabled because a folder grant
 * does NOT extend to the files inside it under `drive.file`.
 */
@Composable
fun DrivePhotoPickerDialog(
    onPhotosPicked: (List<PickedDriveDoc>) -> Unit,
    onDismiss: () -> Unit,
    viewModel: DrivePickerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Fetch a fresh access token each time the dialog opens.
    LaunchedEffect(Unit) { viewModel.load() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            when (val state = uiState) {
                is DrivePickerUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                is DrivePickerUiState.Error -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    ) {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.padding(top = 16.dp),
                        ) { Text("Close") }
                    }
                }
                is DrivePickerUiState.Ready -> {
                    PickerWebView(
                        config = state.config,
                        onPicked = { json ->
                            val docs = DrivePickerResponse.parsePickedDocs(json)
                            if (docs.isEmpty()) onDismiss() else onPhotosPicked(docs)
                        },
                        onCancelled = onDismiss,
                    )
                }
            }
        }
    }
}

/** JS bridge the picker page calls back through. */
private class PickerBridge(
    private val onPicked: (String) -> Unit,
    private val onCancelled: () -> Unit,
) {
    @JavascriptInterface
    fun onPicked(docsJson: String) = onPicked.invoke(docsJson)

    @JavascriptInterface
    fun onCancelled() = onCancelled.invoke()
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun PickerWebView(
    config: DrivePickerConfig,
    onPicked: (String) -> Unit,
    onCancelled: () -> Unit,
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = WebViewClient()
                addJavascriptInterface(
                    PickerBridge(onPicked = onPicked, onCancelled = onCancelled),
                    "PhotoTokBridge",
                )
                // The base URL provides an https origin for the picker iframe's
                // postMessage origin checks; no network request is made for it.
                loadDataWithBaseURL(
                    "https://phototok.local/",
                    buildPickerHtml(config),
                    "text/html",
                    "UTF-8",
                    null,
                )
            }
        },
        onRelease = { it.destroy() },
    )
}

/**
 * The picker page. Values are injected via JSON string encoding (never raw
 * string interpolation) so tokens/keys cannot break out of the script context.
 */
private fun buildPickerHtml(config: DrivePickerConfig): String {
    val token = JSONObject.quote(config.accessToken)
    val apiKey = JSONObject.quote(config.apiKey)
    val appId = JSONObject.quote(config.appId)
    return """
        <!DOCTYPE html>
        <html>
        <head>
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <style>html,body{margin:0;height:100%;background:#111}</style>
        <script>
        var OAUTH_TOKEN = $token;
        var API_KEY = $apiKey;
        var APP_ID = $appId;

        function onApiLoad() {
            gapi.load('picker', createPicker);
        }

        function createPicker() {
            var view = new google.picker.DocsView(google.picker.ViewId.DOCS_IMAGES)
                .setIncludeFolders(true)
                .setSelectFolderEnabled(false);
            var picker = new google.picker.PickerBuilder()
                .setOAuthToken(OAUTH_TOKEN)
                .setDeveloperKey(API_KEY)
                .setAppId(APP_ID)
                .setOrigin('https://phototok.local')
                .enableFeature(google.picker.Feature.MULTISELECT_ENABLED)
                .addView(view)
                .setCallback(pickerCallback)
                .build();
            picker.setVisible(true);
        }

        function pickerCallback(data) {
            if (data.action === google.picker.Action.PICKED) {
                PhotoTokBridge.onPicked(JSON.stringify(data));
            } else if (data.action === google.picker.Action.CANCEL) {
                PhotoTokBridge.onCancelled();
            }
        }
        </script>
        <script src="https://apis.google.com/js/api.js?onload=onApiLoad" async defer></script>
        </head>
        <body></body>
        </html>
    """.trimIndent()
}
