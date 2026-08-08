package com.moments.android.services.messaging

import android.content.Context
import android.util.Log
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.PasswordCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.ad.findActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Port de `ChatRecoveryDeviceVault.swift`.
 *
 * 1) Siempre guarda en [EncryptionKeyStore] (silencioso, este dispositivo).
 * 2) Además intenta [Credential Manager] → Google Password Manager (sync entre
 *    Androids con la misma cuenta Google), igual que iCloud Keychain en iOS.
 *
 * iOS Keychain ≉ Google Password Manager: entre iPhone ↔ Android sigue siendo QR.
 */
object ChatRecoveryDeviceVault {
    private const val TAG = "ChatRecoveryDeviceVault"
    private const val TAG_PREFIX = "chat_recovery_pin_v1_"
    private const val PASSWORD_ID_PREFIX = "moments-chat-recovery/"

    fun passwordId(uid: String): String = PASSWORD_ID_PREFIX + uid

    suspend fun savePIN(uid: String, pin: String, context: Context? = null) {
        val trimmed = pin.trim()
        if (uid.isBlank() || trimmed.length != 6 || !trimmed.all(Char::isDigit)) return

        // Local Keystore-backed copy (siempre).
        runCatching {
            EncryptionKeyStore.store(TAG_PREFIX + uid, trimmed.toByteArray(Charsets.UTF_8))
        }.onFailure { Log.w(TAG, "Keystore save failed", it) }

        // Google Password Manager (best-effort; puede mostrar confirmación una vez).
        val activity = context?.findActivity() ?: return
        withContext(Dispatchers.Main) {
            try {
                val manager = CredentialManager.create(activity)
                manager.createCredential(
                    context = activity,
                    request = CreatePasswordRequest(
                        id = passwordId(uid),
                        password = trimmed,
                        preferImmediatelyAvailableCredentials = false,
                        isAutoSelectAllowed = false,
                    ),
                )
                Log.i(TAG, "PIN saved to Credential Manager / GPM")
            } catch (_: CreateCredentialCancellationException) {
                Log.i(TAG, "User cancelled GPM save; Keystore copy kept")
            } catch (error: CreateCredentialException) {
                Log.w(TAG, "GPM save unavailable: ${error.message}")
            } catch (error: Exception) {
                Log.w(TAG, "GPM save failed", error)
            }
        }
    }

    suspend fun loadPIN(uid: String, context: Context? = null): String? {
        if (uid.isBlank()) return null

        loadFromKeystore(uid)?.let { return it }

        val activity = context?.findActivity() ?: return null
        return withContext(Dispatchers.Main) {
            try {
                val manager = CredentialManager.create(activity)
                val response = manager.getCredential(
                    context = activity,
                    request = GetCredentialRequest.Builder()
                        .addCredentialOption(
                            GetPasswordOption(
                                allowedUserIds = setOf(passwordId(uid), uid),
                                isAutoSelectAllowed = true,
                            ),
                        )
                        .setPreferImmediatelyAvailableCredentials(true)
                        .build(),
                )
                val credential = response.credential as? PasswordCredential ?: return@withContext null
                val pin = credential.password.trim()
                if (pin.length != 6 || !pin.all(Char::isDigit)) return@withContext null
                // Cache local para restores silenciosos siguientes.
                runCatching {
                    EncryptionKeyStore.store(TAG_PREFIX + uid, pin.toByteArray(Charsets.UTF_8))
                }
                pin
            } catch (_: NoCredentialException) {
                null
            } catch (_: GetCredentialCancellationException) {
                null
            } catch (error: GetCredentialException) {
                Log.w(TAG, "GPM load unavailable: ${error.message}")
                null
            } catch (error: Exception) {
                Log.w(TAG, "GPM load failed", error)
                null
            }
        }
    }

    fun clear(uid: String) {
        if (uid.isBlank()) return
        EncryptionKeyStore.delete(TAG_PREFIX + uid)
        // Credential Manager no expone borrado fiable de passwords de app;
        // el usuario puede eliminarlo en Google Password Manager.
    }

    fun clearCurrentUser() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        clear(uid)
    }

    private fun loadFromKeystore(uid: String): String? {
        val bytes = EncryptionKeyStore.retrieve(TAG_PREFIX + uid) ?: return null
        val pin = bytes.toString(Charsets.UTF_8).trim()
        return pin.takeIf { it.length == 6 && it.all(Char::isDigit) }
    }
}
