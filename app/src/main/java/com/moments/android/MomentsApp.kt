package com.moments.android

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import com.moments.android.services.auth.AuthService
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
import com.moments.android.views.login.resolveAccountState
import com.moments.android.views.messaging.services.ChatService
import com.moments.android.views.messaging.services.LiveLocationSharingService
import com.moments.android.views.misc.WhatsNewView
import com.moments.android.views.profile.incognito.IncognitoGlobalOverlay
import com.moments.android.views.shared.MomentsModalSheet
import com.moments.android.views.shared.MomentsTheme
import com.moments.android.views.shared.Surface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    var accountState by launchState.accountState
    var validatedAccountUid by launchState.validatedAccountUid

    val incognitoActive by IncognitoModeService.isActive.collectAsState()

    // ≡ Auth.auth().addStateDidChangeListener
    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            val user = auth.currentUser
            if (user != null) {
                NotificationBadgeService.setupListeners()
                syncLastAppOpenIfNeeded(prefs, force = true, scope = scope)
                IncognitoModeService.loadState()
                scope.launch { MessageIngestService.drainPendingQueue() }
            } else {
                manuallyAuthenticated = false
                NotificationBadgeService.cleanup()
                IncognitoModeService.resetForSignedOutUser()
                LiveLocationSharingService.handleUserSignedOut()
            }
        }
        FirebaseAuth.getInstance().addAuthStateListener(listener)
        onDispose { FirebaseAuth.getInstance().removeAuthStateListener(listener) }
    }

    val signedInUid = FirebaseAuth.getInstance().currentUser?.uid
    LaunchedEffect(signedIn, signedInUid) {
        if (!signedIn) {
            accountState = AccountState.Loading
            validatedAccountUid = null
        } else if (signedInUid != null && validatedAccountUid != signedInUid) {
            accountState = AccountState.Loading
            accountState = resolveAccountState(signedInUid)
            validatedAccountUid = signedInUid
        }
    }

    // ≡ onAppear: post-launch init (una vez) + restore live location
    LaunchedEffect(Unit) {
        if (!didPostLaunchInit) {
            didPostLaunchInit = true
            delay(200)
            OfflineSyncService.enableAutomaticSync()
            BackgroundMomentUploadService.cleanupStaleUploadActivities()
            // BackgroundStoryUploadService.cleanupStaleUploadActivities — Live Activity N/A
            LocalPersistenceService.cleanupOldData()
            ChatCacheStore.runMaintenance()
            if (FirebaseAuth.getInstance().currentUser != null) {
                MessageIngestService.drainPendingQueue()
                MessageCatchUpService.syncRecent(LocalPersistenceService.loadConversations())
            }
            AffinityTracker.applyTimeDecayIfNeeded()
            AffinityTracker.cleanupVeryLowAffinities()
        }
        LiveLocationSharingService.restoreIfNeeded()
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
                MessageCatchUpService.syncRecent(LocalPersistenceService.loadConversations())
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(Modifier.fillMaxSize()) {
        // Contenido principal (bajo el splash, como iOS ZStack)
        when {
            !signedIn -> LoginScreen(onAuthenticated = { manuallyAuthenticated = true })
            accountState is AccountState.Loading -> AccountLoading()
            accountState is AccountState.Deactivated -> DeactivatedScreen(accountState as AccountState.Deactivated) {
                scope.launch {
                    val uid = FirebaseAuth.getInstance().currentUser?.uid
                    accountState = if (uid != null) resolveAccountState(uid) else AccountState.Active
                }
            }
            accountState is AccountState.Suspended -> SuspendedScreen(accountState as AccountState.Suspended)
            else -> TabBarScreen(
                deepLinkUri = deepLinkUri,
                deepLinkFromNewTask = deepLinkFromNewTask,
                onDeepLinkHandled = onDeepLinkHandled,
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
                    checkVersion(context, prefs, scope) { showWhatsNew = true }
                },
            )
        }

        if (showWhatsNew) {
            // ≡ `.sheet` + `.presentationDetents([.medium, .large])`
            MomentsModalSheet(
                onDismissRequest = { showWhatsNew = false },
                largeOnly = false,
            ) { dismiss ->
                WhatsNewView(onDismiss = dismiss)
            }
        }
    }
}

/** ≡ checkVersion() — solo en actualización real, no en 1ª instalación / datos limpios. */
private fun checkVersion(
    context: Context,
    prefs: android.content.SharedPreferences,
    scope: CoroutineScope,
    onShowWhatsNew: () -> Unit,
) {
    val currentVersion = runCatching {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        info.versionName
    }.getOrNull() ?: "2.9.0"

    // Sin clave = install limpio / clear: marcar y no enseñar (iOS default "1.0.0" dispara
    // el sheet en cada wipe de debug; en Android no interrumpimos el arranque).
    if (!prefs.contains(KEY_LAST_VERSION_PROMPTED)) {
        prefs.edit().putString(KEY_LAST_VERSION_PROMPTED, currentVersion).apply()
        return
    }
    val lastPrompted = prefs.getString(KEY_LAST_VERSION_PROMPTED, currentVersion) ?: currentVersion
    if (lastPrompted == currentVersion) return
    scope.launch {
        delay(1_500)
        prefs.edit().putString(KEY_LAST_VERSION_PROMPTED, currentVersion).apply()
        onShowWhatsNew()
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
    val accountState = mutableStateOf<AccountState>(AccountState.Loading)
    val validatedAccountUid = mutableStateOf<String?>(null)
}

private const val PREFS_NAME = "moments_app"
private const val KEY_LAST_VERSION_PROMPTED = "lastVersionPrompted"
private const val KEY_LAST_APP_OPEN_SYNC_AT = "lastAppOpenSyncAt"

@Preview(showBackground = true)
@Composable
private fun MomentsAppPreview() {
    MomentsTheme { MomentsApp() }
}
