package com.moments.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.moments.android.notifications.services.FCMTokenService
import com.moments.android.notifications.services.MomentsFirebaseMessagingService
import com.moments.android.notifications.services.NotificationBadgeService
import com.moments.android.notifications.services.NotificationNavigationService
import com.moments.android.MomentsApp
import com.moments.android.views.shared.MomentsTheme

/**
 * Wiring de permisos POST_NOTIFICATIONS y deep links desde push (paridad AppDelegate iOS).
 */
class MainActivity : ComponentActivity() {

    private var pendingDeepLink by mutableStateOf<android.net.Uri?>(null)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) FCMTokenService.updateFCMToken()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        captureDeepLink(intent)
        handlePushIntent(intent)
        setContent {
            MomentsTheme {
                MomentsApp(
                    deepLinkUri = pendingDeepLink,
                    onDeepLinkHandled = { pendingDeepLink = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureDeepLink(intent)
        handlePushIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        NotificationBadgeService.setupListeners()
        FCMTokenService.updateFCMToken()
    }

    private fun captureDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (intent.getBooleanExtra(MomentsFirebaseMessagingService.EXTRA_FROM_PUSH, false)) return
        pendingDeepLink = data
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            FCMTokenService.updateFCMToken()
            return
        }
        when (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)) {
            PackageManager.PERMISSION_GRANTED -> FCMTokenService.updateFCMToken()
            else -> notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun handlePushIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(MomentsFirebaseMessagingService.EXTRA_FROM_PUSH, false) != true) return
        val userInfo = intent.extras?.keySet()?.associateWith { key ->
            intent.extras?.get(key) as Any?
        } ?: return
        NotificationNavigationService.handleNotificationData(userInfo)
        val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        val notificationId = userInfo["notificationId"] as? String ?: userInfo["gcm.message_id"] as? String
        if (userId != null && !notificationId.isNullOrBlank()) {
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users").document(userId)
                .collection("notifications").document(notificationId)
                .update(mapOf("isPending" to false))
        }
    }
}
