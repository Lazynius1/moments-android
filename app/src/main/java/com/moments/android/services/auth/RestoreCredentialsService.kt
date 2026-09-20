package com.moments.android.services.auth

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CreateRestoreCredentialRequest
import androidx.credentials.CreateRestoreCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetRestoreCredentialOption
import androidx.credentials.RestoreCredential
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.credentials.exceptions.restorecredential.E2eeUnavailableException
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.ad.findActivity
import com.moments.android.services.network.CloudFunctionsClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Android Credential Manager Restore Credentials.
 *
 * A WebAuthn restore credential is created after an authenticated session and can later
 * recreate that Firebase session after Android restores the application's data.
 */
object RestoreCredentialsService {
    private const val TAG = "RestoreCredentials"
    private const val PREFS = "moments_restore_credentials"
    private const val REGISTERED_UID = "registered_uid"
    private val restoreMutex = Mutex()

    suspend fun registerForCurrentUserIfNeeded(context: Context) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val preferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (preferences.getString(REGISTERED_UID, null) == user.uid) return
        val activity = context.findActivity() ?: return

        try {
            val options = CloudFunctionsClient.postJson(
                function = "restoreCredentialRegisterChallenge",
                payload = JSONObject(),
            )
            val manager = CredentialManager.create(activity)
            val response = try {
                withContext(Dispatchers.Main) {
                    manager.createCredential(
                        context = activity,
                        request = CreateRestoreCredentialRequest(options.toString(), true),
                    )
                }
            } catch (_: E2eeUnavailableException) {
                // Official fallback: retain a local restore key if cloud backup/E2EE is unavailable.
                withContext(Dispatchers.Main) {
                    manager.createCredential(
                        context = activity,
                        request = CreateRestoreCredentialRequest(options.toString(), false),
                    )
                }
            } as? CreateRestoreCredentialResponse ?: return
            CloudFunctionsClient.postJson(
                function = "restoreCredentialRegisterVerify",
                payload = JSONObject(response.responseJson),
            )
            preferences.edit().putString(REGISTERED_UID, user.uid).apply()
        } catch (error: CreateCredentialException) {
            // Unsupported devices and a user cancellation must leave normal login untouched.
            Log.i(TAG, "Restore credential was not created: ${error.message}")
        } catch (error: Exception) {
            Log.w(TAG, "Could not register restore credential", error)
        }
    }

    /** Returns true only after Firebase accepted a restored custom token. */
    suspend fun restoreSignedOutSessionIfAvailable(context: Context): Boolean = restoreMutex.withLock {
        if (FirebaseAuth.getInstance().currentUser != null) return true
        val activity = context.findActivity() ?: return false
        try {
            val options = CloudFunctionsClient.postPublicJson("restoreCredentialLoginChallenge")
            val credential = withContext(Dispatchers.Main) {
                CredentialManager.create(activity).getCredential(
                    context = activity,
                    request = GetCredentialRequest.Builder()
                        .addCredentialOption(GetRestoreCredentialOption(options.toString()))
                        .build(),
                ).credential
            } as? RestoreCredential ?: return false

            val assertion = JSONObject(credential.authenticationResponseJson)
                .put("originalChallenge", options.getString("challenge"))
            val result = CloudFunctionsClient.postPublicJson(
                function = "restoreCredentialLoginVerify",
                payload = assertion,
            )
            FirebaseAuth.getInstance().signInWithCustomToken(result.getString("customToken")).await()
            true
        } catch (_: NoCredentialException) {
            Log.i(TAG, "No restore credential available")
            false
        } catch (error: GetCredentialException) {
            Log.i(TAG, "No restore credential available: ${error.message}")
            false
        } catch (error: Exception) {
            Log.w(TAG, "Could not restore session", error)
            false
        }
    }

    suspend fun clearForLogout(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(REGISTERED_UID).apply()
        runCatching {
            CredentialManager.create(context.applicationContext).clearCredentialState(
                ClearCredentialStateRequest(ClearCredentialStateRequest.TYPE_CLEAR_RESTORE_CREDENTIAL),
            )
        }.onFailure { Log.w(TAG, "Could not clear restore credential state", it) }
    }
}
