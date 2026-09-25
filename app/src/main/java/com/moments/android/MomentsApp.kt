package com.moments.android

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import com.moments.android.views.components.MomentsCircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.coordinators.TabBarScreen
import com.moments.android.notifications.services.NotificationBadgeService
import com.moments.android.notifications.services.NotificationService
import com.moments.android.views.components.InAppBannerView
import com.moments.android.services.auth.AuthService
import com.moments.android.services.auth.RestoreCredentialsService
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.updateLastAppOpenAt
import com.moments.android.services.incognito.IncognitoModeService
import com.moments.android.services.messaging.ChatCacheStore
import com.moments.android.services.messaging.MessageCatchUpService
import com.moments.android.services.messaging.MessageIngestService
import com.moments.android.services.network.OfflineSyncService
import com.moments.android.services.persistence.LocalPersistenceService
import com.moments.android.services.social.AffinityTracker
import com.moments.android.views.creator.BackgroundMomentUploadService
import com.moments.android.views.login.AccountState
import com.moments.android.views.login.DeactivatedScreen
import com.moments.android.views.login.LoginScreen
import com.moments.android.views.login.SplashScreen
import com.moments.android.views.login.SuspendedScreen
import com.moments.android.views.messaging.services.ChatService
import com.moments.android.views.messaging.services.LiveLocationSharingService
import com.moments.android.views.misc.WhatsNewPresentationCoordinator
import com.moments.android.views.misc.WhatsNewView
import com.moments.android.views.profile.incognito.IncognitoGlobalOverlay
import com.moments.android.views.shared.MomentsModalSheet
import com.moments.android.views.shared.MomentsTheme
import com.moments.android.views.shared.Surface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Port de `MomentsApp.swift` (cuerpo Compose / Scene).
 *
 * Init Firebase/AppCheck/caches → [MomentsApplication] (≡ `MomentsApp.init` + partes AppDelegate).
 * Auth gate (Login/cuenta) es capa Android; iOS monta TabBar siempre.
 */
@Composable
fun MomentsApp(
    deepLinkUri: Uri? = null,
    deepLinkFromNewTask: Boolean = false,
    onDeepLinkHandled: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    // Activity-scoped: survives rotation, folding and uiMode recreation without
    // replaying the launch gate. A real process restart still gets fresh state.
    val launchState: MomentsAppLaunchState = viewModel()
    val prefs = remember {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var showSplash by launchState.showSplash
    var showWhatsNew by launchState.showWhatsNew
    var didPostLaunchInit by launchState.didPostLaunchInit
    // ≡ iOS `shouldShowMainApp = authService.isLoggedIn && authState == .authenticated`.
    // Ojo: NO basta con `FirebaseAuth.currentUser != null` — tras un login social nuevo
    // la sesión de Firebase existe pero el perfil de Firestore todavía no, y entrar así
    // dejaba al usuario dentro de la app sin username ni documento (perfil fantasma).
    // `AuthService.isLoggedIn` solo pasa a true tras `hydrateAuthenticatedSession`.
    val hasProfileSession by AuthService.isLoggedIn.collectAsState()
    // El registro por correo escribe el perfil por su cuenta y avisa con `onAuthenticated`
    // antes de que el listener de AuthService llegue a hidratar la sesión; se respeta ese
    // aviso para no rebotar al login en ese hueco.
    var manuallyAuthenticated by launchState.manuallyAuthenticated
    val signedIn = hasProfileSession || manuallyAuthenticated
    var isRestoringSession by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser == null) }
    // ≡ iOS LoginView: suspended / deactivated salen de AuthService, sin get() extra a Firestore.
    val authState by AuthService.authState.collectAsState()
    val isAccountDeactivated by AuthService.isAccountDeactivated.collectAsState()
    val deactivatedUser by AuthService.deactivatedUserData.collectAsState()
    val isVerifyingAccount by AuthService.isVerifyingAccount.collectAsState()
    val isRegistering by AuthService.isRegistering.collectAsState()

    val incognitoActive by IncognitoModeService.isActive.collectAsState()

    // Restore Credentials may restore the Firebase session after Android has restored the
    // app data (including a reinstall/restore), before exposing the normal login UI.
    LaunchedEffect(Unit) {
        if (FirebaseAuth.getInstance().currentUser == null) {
            RestoreCredentialsService.restoreSignedOutSessionIfAvailable(context)
        }
        isRestoringSession = false
    }

    LaunchedEffect(signedIn) {
        if (signedIn) {
            RestoreCredentialsService.registerForCurrentUserIfNeeded(context)
        }
    }

    // ≡ Auth.auth().addStateDidChangeListener
    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            val user = auth.currentUser
            if (user != null) {
                NotificationBadgeService.setupListeners()
                NotificationService.startObserving()
                syncLastAppOpenIfNeeded(prefs, force = true, scope = scope)
                IncognitoModeService.loadState()
                scope.launch { MessageIngestService.drainPendingQueue() }
            } else {
                manuallyAuthenticated = false
                NotificationBadgeService.cleanup()
                NotificationService.stopObserving()
                IncognitoModeService.resetForSignedOutUser()
                LiveLocationSharingService.handleUserSignedOut()
            }
        }
        FirebaseAuth.getInstance().addAuthStateListener(listener)
        onDispose { FirebaseAuth.getInstance().removeAuthStateListener(listener) }
    }

    // ≡ onAppear: post-launch init (una vez) + restore live location
    LaunchedEffect(Unit) {
        if (!didPostLaunchInit) {
            didPostLaunchInit = true
            delay(200)
            runCatching {
                OfflineSyncService.enableAutomaticSync()
                BackgroundMomentUploadService.cleanupStaleUploadActivities()
                // BackgroundStoryUploadService.cleanupStaleUploadActivities — Live Activity N/A
                withContext(Dispatchers.IO) {
                    LocalPersistenceService.cleanupOldDataAsync()
                    ChatCacheStore.runMaintenance()
                }
                if (FirebaseAuth.getInstance().currentUser != null) {
                    val cachedConversations = withContext(Dispatchers.IO) {
                        LocalPersistenceService.loadConversationsAsync()
                    }
                    MessageIngestService.drainPendingQueue()
                    MessageCatchUpService.syncRecent(cachedConversations)
                }
                AffinityTracker.applyTimeDecayIfNeeded()
                AffinityTracker.cleanupVeryLowAffinities()
            }
        }
        runCatching { LiveLocationSharingService.restoreIfNeeded() }
    }

    // ≡ UIApplication.didBecomeActiveNotification
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            scope.launch { ChatService.markAllPendingMessagesAsDelivered() }
            NotificationBadgeService.refreshAllCounts()
            LiveLocationSharingService.restoreIfNeeded()
            syncLastAppOpenIfNeeded(prefs, force = false, scope = scope)
            IncognitoModeService.refresh()
            IncognitoModeService.handlePendingAppGroupActionIfNeeded()
            scope.launch {
                ChatCacheStore.runMaintenance()
                MessageIngestService.drainPendingQueue()
                val cachedConversations = withContext(Dispatchers.IO) {
                    LocalPersistenceService.loadConversationsAsync()
                }
                MessageCatchUpService.syncRecent(cachedConversations)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val suspendedState = authState as? AuthService.AuthState.Suspended
    val deactivatedUiState = remember(deactivatedUser) {
        AccountState.Deactivated(
            username = deactivatedUser?.username,
            email = deactivatedUser?.email,
            profileImagePath = deactivatedUser?.profileImagePath,
        )
    }

    Box(Modifier.fillMaxSize()) {
        // Contenido principal (bajo el splash, como iOS ZStack / LoginView gate)
        when {
            suspendedState != null -> SuspendedScreen(
                AccountState.Suspended(suspendedState.reason, suspendedState.expiresAt?.time),
            )
            isAccountDeactivated -> DeactivatedScreen(deactivatedUiState) {
                // reactivateAccount ya hidrata AuthService; no hace falta otro get() a Firestore.
            }
            isRestoringSession && !signedIn -> AccountLoading()
            isVerifyingAccount && !isRegistering && !signedIn -> AccountLoading()
            !signedIn -> LoginScreen(onAuthenticated = { manuallyAuthenticated = true })
            else -> TabBarScreen(
                deepLinkUri = deepLinkUri,
                deepLinkFromNewTask = deepLinkFromNewTask,
                onDeepLinkHandled = onDeepLinkHandled,
            )
        }

        // ≡ iOS `InAppBannerWindowAnchor` / UIWindow `.alert+1`: encima de tabs, sheets y overlays.
        if (signedIn) {
            InAppBannerView(
                Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(1500f),
            )
        }

        if (incognitoActive && signedIn) {
            IncognitoGlobalOverlay(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1600f),
            )
        }

        if (showSplash) {
            // Encima del contenido (≡ iOS ZStack order) para la cortina zoom→fade.
            SplashScreen(
                onComplete = {
                    showSplash = false
                    WhatsNewPresentationCoordinator.handleSplashFinished(context)
                },
            )
        }

        LaunchedEffect(Unit) {
            WhatsNewPresentationCoordinator.readyToPresent.collect {
                showWhatsNew = true
            }
        }

        if (showWhatsNew) {
            // ≡ `.sheet` + `.presentationDetents([.medium, .large])`
            MomentsModalSheet(
                onDismissRequest = { showWhatsNew = false },
                largeOnly = false,
            ) { dismiss ->
                // weight(1f): altura acotada → verticalScroll no pelea con el drag del sheet.
                WhatsNewView(
                    onDismiss = dismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true),
                )
            }
        }
    }
}

/** ≡ syncLastAppOpenIfNeeded(force:) — mínimo 15 min */
private fun syncLastAppOpenIfNeeded(
    prefs: android.content.SharedPreferences,
    force: Boolean,
    scope: CoroutineScope,
) {
    if (FirebaseAuth.getInstance().currentUser == null) return
    val nowSec = System.currentTimeMillis() / 1000.0
    val last = Double.fromBits(prefs.getLong(KEY_LAST_APP_OPEN_SYNC_AT, 0L))
    val minimumInterval = 15 * 60.0
    if (!force && (nowSec - last) < minimumInterval) return
    prefs.edit().putLong(KEY_LAST_APP_OPEN_SYNC_AT, nowSec.toBits()).apply()
    scope.launch {
        runCatching { FirestoreService().updateLastAppOpenAt() }
    }
}

@Composable
private fun AccountLoading() {
    Box(Modifier.fillMaxSize().background(Surface), contentAlignment = Alignment.Center) {
        MomentsCircularProgressIndicator()
    }
}

/**
 * Estado de la puerta de arranque retenido únicamente durante la vida lógica de
 * la Activity. Android conserva el ViewModel al recrearla por orientación o
 * uiMode, pero lo descarta si el proceso realmente termina.
 */
internal class MomentsAppLaunchState : ViewModel() {
    val showSplash = mutableStateOf(true)
    val showWhatsNew = mutableStateOf(false)
    val didPostLaunchInit = mutableStateOf(false)
    val manuallyAuthenticated = mutableStateOf(false)
}

private const val PREFS_NAME = "moments_app"
private const val KEY_LAST_APP_OPEN_SYNC_AT = "lastAppOpenSyncAt"

@Preview(showBackground = true)
@Composable
private fun MomentsAppPreview() {
    MomentsTheme { MomentsApp() }
}
