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
import com.moments.android.services.messaging.ChatCommunicationNotificationService
import com.moments.android.services.messaging.EncryptionService
import com.moments.android.services.messaging.ChatMediaDownloadPolicy
import com.moments.android.services.messaging.LocalFirstMessagingSettings
import com.moments.android.views.messaging.services.LiveLocationSharingService
import com.moments.android.services.messaging.MessageIngestQueue
import com.moments.android.services.messaging.MessageSyncCursorStore
import com.moments.android.services.messaging.OnlineStatusService
import com.moments.android.services.network.NetworkMonitor
import com.moments.android.services.network.OfflineSyncService
import com.moments.android.services.persistence.LocalPersistenceService
import com.moments.android.services.persistence.StorySeenStateService
import com.moments.android.services.performance.MotionPolicy
import com.moments.android.services.social.AffinityTracker
import com.moments.android.services.storage.VideoCompressionService
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

class MomentsApplication : Application() {
    companion object {
        @Volatile
        var instance: MomentsApplication? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        NetworkMonitor.initialize(this)
        TimeSpentManager.initialize(this)
        VideoCompressionService.initialize(this)
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
        CacheManager.initialize(this)
        UserCacheService.initialize(this)
        MotionPolicy.initialize(this)
        HapticManager.initialize(this)
        MomentsFormat.initialize(this)
        MomentsAudioSession.initialize(this)
        EmojiUsageStore.initialize(this)
        OrientationManager.initialize(this)
        AffinityTracker.initialize(this)
        AffinityTracker.applyTimeDecayIfNeeded()
        AffinityTracker.cleanupVeryLowAffinities()
        BackgroundMomentUploadService.initialize(this)
        BackgroundStoryUploadService.initialize(this)
        LiveLocationSharingService.initialize(this)
        SharedVideoPlayerPool.initialize(this)
        ReelPrebufferService.initialize(this)
        IncognitoModeService.initialize(this)
        ChatCommunicationNotificationService.initialize(this)
        EncryptionService.initialize(this)
        OnlineStatusService.initialize(this)
        // LocalFirstMessaging / ChatCache ya inicializados arriba (antes de CacheManager)
        OfflineSyncService.enableAutomaticSync()
        NotificationService.initialize(this)
        NotificationBadgeService.initialize(this)
        InAppNotificationService.startListening()
        AdMobConfiguration.initialize(this)
    }
}
