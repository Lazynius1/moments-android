package com.moments.android

import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import com.moments.android.notifications.services.FCMTokenService
import com.moments.android.notifications.services.MomentsFirebaseMessagingService
import com.moments.android.notifications.services.NotificationBadgeService
import com.moments.android.notifications.services.NotificationNavigationService
import com.moments.android.views.shared.MomentsTheme
import com.moments.android.views.shared.MomentsSystemBarsHost

/**
 * Deep links desde push + edge-to-edge (skill `edge-to-edge`).
 * Barras de sistema transparentes; el chrome (tab bar / pantallas) pinta debajo.
 * POST_NOTIFICATIONS: no pedir aquí — ≡ iOS AppDelegate (request comentado);
 * el primer va en FeedView tras 20s vía PermissionPrimerGate.
 */
class MainActivity : ComponentActivity() {

    private var pendingDeepLink by mutableStateOf<android.net.Uri?>(null)
    /** true si el Intent del deep link trae NEW_TASK (recipe deeplinks-advanced). */
    private var pendingDeepLinkNewTask by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        // ≡ iOS INFOPLIST_KEY_UISupportedInterfaceOrientations = Portrait only
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        super.onCreate(savedInstanceState)
        // Skill: enableEdgeToEdge before setContent; transparent bars (auto light/dark icons).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = Color.TRANSPARENT,
                darkScrim = Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = Color.TRANSPARENT,
                darkScrim = Color.TRANSPARENT,
            ),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        captureDeepLink(intent)
        handlePushIntent(intent)
        setContent {
            MomentsTheme {
                val darkTheme = isSystemInDarkTheme()
                SideEffect {
                    // Re-sync icon contrast when theme flips (auto already handles; enforce contrast off).
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        window.isNavigationBarContrastEnforced = false
                    }
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !darkTheme
                        isAppearanceLightNavigationBars = !darkTheme
                    }
                }
                MomentsSystemBarsHost {
                    MomentsApp(
                        deepLinkUri = pendingDeepLink,
                        deepLinkFromNewTask = pendingDeepLinkNewTask,
                        onDeepLinkHandled = {
                            pendingDeepLink = null
                            pendingDeepLinkNewTask = false
                        },
                    )
                }
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
        if (intent == null) return
        if (intent.getBooleanExtra(MomentsFirebaseMessagingService.EXTRA_FROM_PUSH, false)) return
        // Skill android-intent-security: no aceptar data URI arbitraria en Activity exported.
        val data = intent.data ?: return
        val scheme = data.scheme?.lowercase()
        if (scheme != "moments" && scheme != "glowsy") return
        pendingDeepLink = data
        pendingDeepLinkNewTask =
            intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0
    }

    private fun handlePushIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(MomentsFirebaseMessagingService.EXTRA_FROM_PUSH, false) != true) return
        val userInfo = intent.extras?.keySet()?.associateWith { key ->
            intent.extras?.get(key) as Any?
        } ?: return
        NotificationNavigationService.handleNotificationData(userInfo)
        val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        val notificationId = userInfo["notificationId"] as? String
        if (userId != null && !notificationId.isNullOrBlank()) {
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users").document(userId)
                .collection("notifications").document(notificationId)
                .update(mapOf("isPending" to false))
        }
    }
}
