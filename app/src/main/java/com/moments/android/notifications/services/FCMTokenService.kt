package com.moments.android.notifications.services

import android.os.Build
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.moments.android.BuildConfig
import com.moments.android.MomentsApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap

/**
 * Port de FCMTokenService.swift — guarda fcmToken en users/{id}; limpia en logout.
 *
 * Android no requiere APNs token; usa [FirebaseMessaging.getInstance().token] directamente.
 */
object FCMTokenService {
    private val db = FirebaseFirestore.getInstance()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val retryCount = ConcurrentHashMap<String, Int>()

    fun updateFCMToken() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val currentRetries = retryCount.getOrDefault(userId, 0)
        if (currentRetries >= 3) return
        retryCount[userId] = currentRetries + 1

        scope.launch {
            runCatching {
                val token = FirebaseMessaging.getInstance().token.await()
                saveFCMToken(token, userId)
                retryCount[userId] = 0
            }.onFailure {
                android.util.Log.w(TAG, "FCM token fetch failed (attempt ${retryCount[userId]}): $it")
                delay(5_000)
                updateFCMToken()
            }
        }
    }

    fun saveFCMTokenDirectly(token: String, userId: String) {
        scope.launch { saveFCMToken(token, userId) }
    }

    private suspend fun saveFCMToken(token: String, userId: String) {
        val app = MomentsApplication.instance
        val appVersion = app?.let {
            runCatching {
                it.packageManager.getPackageInfo(it.packageName, 0).versionName
            }.getOrNull()
        } ?: BuildConfig.VERSION_NAME

        val userData = mapOf(
            "fcmToken" to token,
            "fcmTokenUpdatedAt" to FieldValue.serverTimestamp(),
            "deviceInfo" to mapOf(
                "model" to Build.MODEL,
                "systemVersion" to "Android ${Build.VERSION.RELEASE}",
                "appVersion" to appVersion,
            ),
        )

        runCatching {
            writeToken(userId, userData, token)
        }.onFailure {
            android.util.Log.w(TAG, "Failed to save FCM token, retry in 30s: $it")
            delay(30_000)
            updateFCMToken()
        }
    }

    fun clearFCMToken() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        scope.launch {
            runCatching {
                writeToken(
                    userId,
                    mapOf(
                        "fcmToken" to FieldValue.delete(),
                        "fcmTokenUpdatedAt" to FieldValue.serverTimestamp(),
                    ),
                    FieldValue.delete(),
                )
                notifyBackendTokenCleared(userId)
            }
        }
    }

    /** Doc público (como siempre) + `private/account` en un único batch. */
    private suspend fun writeToken(userId: String, publicData: Map<String, Any>, privateToken: Any) {
        val userRef = db.collection("users").document(userId)
        val privateRef = userRef.collection("private").document("account")
        db.runBatch { batch ->
            batch.update(userRef, publicData)
            batch.set(
                privateRef,
                mapOf("fcmToken" to privateToken, "updatedAt" to FieldValue.serverTimestamp()),
                SetOptions.merge(),
            )
        }.await()
    }

    private fun notifyBackendTokenCleared(userId: String) {
        // ≡ iOS stub: futuro httpsCallable("clearUserToken") si el backend lo expone.
        android.util.Log.d(TAG, "FCM token cleared for $userId")
    }

    private const val TAG = "FCMTokenService"
}
