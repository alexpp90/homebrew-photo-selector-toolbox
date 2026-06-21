package com.photoselectortoolbox.data.source.googledrive

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Google Sign-In with Drive scope for reading and writing files.
 *
 * Setup required:
 * 1. Create a project in Google Cloud Console
 * 2. Enable the Google Drive API
 * 3. Create an OAuth 2.0 Client ID (Android type) with your app's SHA-1 fingerprint
 * 4. No client ID string is needed in code — Play Services resolves it via package name + SHA-1
 */
@Singleton
class GoogleDriveAuth @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "GoogleDriveAuth"
        private const val SCOPE_DRIVE = "https://www.googleapis.com/auth/drive"
        private const val SCOPE_DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"
    }

    private val _signedInAccount = MutableStateFlow<GoogleSignInAccount?>(null)
    val signedInAccount: StateFlow<GoogleSignInAccount?> = _signedInAccount.asStateFlow()

    val isSignedIn: Boolean
        get() = _signedInAccount.value != null

    private val gso: GoogleSignInOptions by lazy {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(SCOPE_DRIVE), Scope(SCOPE_DRIVE_FILE))
            .build()
    }

    private val signInClient: GoogleSignInClient by lazy {
        GoogleSignIn.getClient(context, gso)
    }

    init {
        // Restore existing sign-in silently
        val lastAccount = GoogleSignIn.getLastSignedInAccount(context)
        if (lastAccount != null && !lastAccount.isExpired) {
            _signedInAccount.value = lastAccount
        }
    }

    /** Returns an Intent to launch the Google Sign-In UI. */
    fun getSignInIntent(): Intent = signInClient.signInIntent

    /** Call after the sign-in activity returns successfully. */
    fun handleSignInResult(account: GoogleSignInAccount?) {
        _signedInAccount.value = account
        if (account != null) {
            Log.d(TAG, "Signed in as ${account.email}")
        }
    }

    /** Try silent sign-in (no UI). Returns true if successful. */
    suspend fun trySilentSignIn(): Boolean = withContext(Dispatchers.IO) {
        try {
            val account = signInClient.silentSignIn().await()
            _signedInAccount.value = account
            true
        } catch (e: Exception) {
            Log.d(TAG, "Silent sign-in failed: ${e.message}")
            false
        }
    }

    /** Sign out and clear the cached account. */
    suspend fun signOut() {
        try {
            signInClient.signOut().await()
        } catch (e: Exception) {
            Log.e(TAG, "Sign-out failed", e)
        }
        _signedInAccount.value = null
    }

    /**
     * Get a valid OAuth access token for Drive API calls.
     * Must be called on a background thread.
     */
    suspend fun getAccessToken(): String? = withContext(Dispatchers.IO) {
        val account = _signedInAccount.value ?: return@withContext null
        try {
            val accountName = account.email ?: return@withContext null
            val googleAccount = account.account ?: android.accounts.Account(accountName, "com.google")
            val token = com.google.android.gms.auth.GoogleAuthUtil.getToken(
                context,
                googleAccount,
                "oauth2:$SCOPE_DRIVE $SCOPE_DRIVE_FILE"
            )
            token
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get access token", e)
            null
        }
    }
}
