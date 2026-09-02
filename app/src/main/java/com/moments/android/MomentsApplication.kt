package com.moments.android

import android.app.Application
import com.moments.android.services.activity.TimeSpentManager
import com.moments.android.reportes.AppealService
import com.moments.android.services.auth.AuthService
import com.moments.android.services.auth.LoginActivityService
import com.moments.android.services.auth.OnboardingDraftStore
import com.moments.android.views.creator.BackgroundMomentUploadService
import com.moments.android.views.creator.BackgroundStoryUploadService
import com.moments.android.services.cache.CacheManager
import com.moments.android.services.cache.UserCacheService
import com.moments.android.services.cache.ImagePrefetchManager
import com.moments.android.services.cache.PersistentAudioCache
import com.moments.android.services.cache.PersistentVideoCache
import com.moments.android.services.cache.VideoThumbnailCache
import com.moments.android.services.incognito.IncognitoModeService
import com.moments.android.services.messaging.ChatCacheStore
import com.moments.android.services.messaging.ChatCommunicationIntentDonor
import com.moments.android.services.messaging.EncryptionService
import com.moments.android.services.messaging.ChatMediaDownloadPolicy
import com.moments.android.services.messaging.LocalFirstMessagingSettings
import com.moments.android.views.messaging.services.LiveLocationSharingService
import com.moments.android.services.messaging.MessageIngestQueue
import com.moments.android.services.messaging.MessageSyncCursorStore
import com.moments.android.services.messaging.OnlineStatusService
import com.moments.android.services.network.NetworkMonitor
import com.moments.android.services.network.OfflineSyncService
import com.moments.android.services.network.OfflineSyncWorker
import com.moments.android.services.persistence.LocalPersistenceService
import com.moments.android.services.persistence.StorySeenStateService
import com.moments.android.services.performance.MotionPolicy
import com.moments.android.services.social.AffinityTracker
import com.moments.android.services.storage.VideoCompressionService
import com.moments.android.services.video.GlobalVideoManager
import com.moments.android.services.video.ReelPrebufferService
import com.moments.android.services.video.SharedVideoPlayerPool
import com.moments.android.utilities.EmojiUsageStore
import com.moments.android.utilities.HapticManager
import com.moments.android.utilities.MomentsAudioSession
import com.moments.android.utilities.MomentsFormat
import com.moments.android.utilities.OrientationManager
import com.moments.android.ad.AdMobConfiguration
import com.moments.android.notifications.services.InAppNotificationService
import com.moments.android.notifications.services.NotificationBadgeService
import com.moments.android.notifications.services.NotificationService
import com.moments.android.services.security.MomentsAppCheckProviderFactory
import com.google.firebase.appcheck.FirebaseAppCheck
import com.mapbox.common.MapboxOptions
import com.moments.android.views.feed.maps.FeedMaps
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.imageLoader
import com.moments.android.services.cache.MomentsImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Application process entry — pares con [MomentsApp] Compose.
 * Cubre `MomentsApp.init` (AppCheck) + bootstrap de servicios que iOS hace
 * en onAppear diferido / AppDelegate.
 */
class MomentsApplication : Application(), ImageLoaderFactory {
    companion object {
        @Volatile
        var instance: MomentsApplication? = null
            private set
    }

    override fun newImageLoader(): ImageLoader = MomentsImageLoader.create(this)

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Mapbox: token público desde local.properties → BuildConfig (antes de cualquier MapView).
        if (FeedMaps.hasMapboxToken()) {
            MapboxOptions.accessToken = BuildConfig.MAPBOX_ACCESS_TOKEN
        }
        // iOS: AppCheck.setAppCheckProviderFactory antes de FirebaseApp.configure().
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(MomentsAppCheckProviderFactory)
        NetworkMonitor.initialize(this)
        TimeSpentManager.initialize(this)
        VideoCompressionService.initialize(this)
        com.moments.android.views.creator.StoryVideoProcessingService.initialize(this)
        LocalPersistenceService.initialize(this)
        StorySeenStateService.initialize(this)
        com.moments.android.services.persistence.MessagePersistenceStore.initialize(this)
        OnboardingDraftStore.initialize(this)
        LoginActivityService.initialize(this)
        AuthService.initialize(this)
        AppealService.getInstance(this)
        PersistentAudioCache.initialize(this)
        PersistentVideoCache.initialize(this)
        VideoThumbnailCache.initialize(this)
        ImagePrefetchManager.initialize(this)
        // ChatCacheStore antes de CacheManager: el cleanup mide totalMediaBytes del chat.
        LocalFirstMessagingSettings.initialize(this)
        MessageIngestQueue.initialize(this)
        MessageSyncCursorStore.initialize(this)
        ChatMediaDownloadPolicy.initialize(this)
        ChatCacheStore.initialize(this)
        com.moments.android.views.messaging.services.ChatBuzzProcessedStore.initialize(this)
        com.moments.android.views.messaging.services.ChatDraftStore.initialize(this)
        com.moments.android.views.messaging.services.ChatScrollStateStore.initialize(this)
        CacheManager.initialize(this)
        UserCacheService.initialize(this)
        com.moments.android.views.creator.components.ChatGIFImageCache.initialize(this)
        MotionPolicy.initialize(this)
        HapticManager.initialize(this)
        MomentsFormat.initialize(this)
        MomentsAudioSession.initialize(this)
        EmojiUsageStore.initialize(this)
        OrientationManager.initialize(this)
        AffinityTracker.initialize(this)
        // Mantenimiento de afinidades diferido en background para no congelar arranque en frío
        CoroutineScope(Dispatchers.IO).launch {
            delay(12_000)
            runCatching {
                AffinityTracker.applyTimeDecayIfNeeded()
                AffinityTracker.cleanupVeryLowAffinities()
            }
        }
        BackgroundMomentUploadService.initialize(this)
        BackgroundStoryUploadService.initialize(this)
        LiveLocationSharingService.initialize(this)
        // Solo registrar contexto; los ExoPlayers se crean al primer vídeo / reel.
        SharedVideoPlayerPool.initialize(this)
        GlobalVideoManager.initialize(this)
        ReelPrebufferService.initialize(this)
        IncognitoModeService.initialize(this)
        ChatCommunicationIntentDonor.initialize(this)
        EncryptionService.initialize(this)
        OnlineStatusService.initialize(this)
        // ≡ MomentsApp @StateObject EphemeralCleanupManager
        com.moments.android.views.messaging.services.EphemeralCleanupManager.startCleanupSystem()
        // LocalFirstMessaging / ChatCache ya inicializados arriba (antes de CacheManager)
        OfflineSyncService.enableAutomaticSync()
        OfflineSyncWorker.schedule(this)

        // Fase 2: notificaciones + ads tras primer frame (no bloquear cold start).
        CoroutineScope(Dispatchers.Main).launch {
            delay(1_500)
            NotificationService.initialize(this@MomentsApplication)
            NotificationBadgeService.initialize(this@MomentsApplication)
            InAppNotificationService.startListening()
            AdMobConfiguration.initialize(this@MomentsApplication)
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_COMPLETE || level >= TRIM_MEMORY_BACKGROUND) {
            imageLoader.memoryCache?.clear()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        imageLoader.memoryCache?.clear()
    }
}
