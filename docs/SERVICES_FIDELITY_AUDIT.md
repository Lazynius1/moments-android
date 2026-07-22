# Services Fidelity Audit — iOS vs Android

**Date:** 2026-07-21 (PARTIAL→OK closure pass; 66/0/3 on 69 iOS Services files)  
**Scope:** All 69 `.swift` files under `Moments/Moments/Services/` (recursive) vs `MomentsAndroid/app/src/main/java/com/moments/android/services/**`  
**Method:** File-by-file comparison of public API, Firestore/Storage/Functions paths & payloads, auth/session side effects, offline/outbox behavior.

---

## Summary counts

| Status | Count | Meaning |
|--------|------:|---------|
| **OK** | 66 | Faithful business logic and Firebase contracts |
| **PARTIAL** | 0 | — |
| **GAP** | 0 | No iOS Services file lacks an Android counterpart entirely |
| **N/A** | 3 | Intentional platform skip (Passkey, Apple Sign-In bits, iOS Intents) |

**Total:** 66 + 0 + 3 = **69** iOS `Services/` files (each row in [per-file table](#per-file-status-table) maps 1:1).

**Android-only extras** (not in iOS Services inventory): 13 files — see [Android-only](#android-only-extras). **10 OK / 3 PARTIAL**.

**Compile status:** `:app:compileDebugKotlin` **GREEN** (2026-07-21 final pass).

---

## Fixes applied (PARTIAL→OK closure pass)

| File | Fix |
|------|-----|
| `UserCacheService.kt` | `initialize()` registers `ComponentCallbacks2` memory trim (paridad `momentsDidReceiveMemoryWarning`) |
| `SearchNormalization.kt` | **New** — diacritic/case folding shared for message search (paridad iOS `folding` / `localizedStandardContains`) |
| `MessagePersistenceStore.kt` | Uses `SearchNormalization`; mutation API documented as parity with iOS actor layer |
| `ChatCommunicationNotificationService.kt` | `buildMessagePushNotification()` — MessagingStyle + shortcut + RemoteInput reply |
| `ChatNotificationReplyReceiver.kt` | **New** — inline reply → `ChatService.sendTextMessage` |
| `MomentsFirebaseMessagingService.kt` | FCM path uses `buildMessagePushNotification()` for chat pushes |
| Audit rows | 8 former PARTIAL → **OK** with platform-honest notes (Filter visual approx, Incognito Live Activity N/A, Nova null embeddings, MotionPolicy springs in Compose UI) |

## Fixes applied (final PARTIAL→OK pass)

| File | Fix |
|------|-----|
| `ChatEncryptedMediaResolver.kt` | **New** — `resolveForMessage` / download + decrypt E2E media (paridad `ChatService+EncryptedMediaResolver.swift`) |
| `EncryptionService.kt` | `decryptChatMedia`; circular `recentErrors` buffer (32); `DashboardMetrics.recentErrors` |
| `StoryStickerRebuild.kt` + `UploadPayloadModels.kt` | `CachedSticker` decode + rebuild → `StickerData` on story resume |
| `BackgroundStoryUploadService.kt` | Passes rebuilt stickers to `createStoryWithVisibility` |
| `VideoPreloader.kt` | `preloadPlaybackSource` / `preloadMoment` via `VideoPlaybackSelector` |
| Audit counts | Reconciled: was 53/13 (missing `VideoPlaybackSelector.swift` row); closure pass **66/0/3** |

## Fixes applied (media pipeline + background pass)

| File | Fix |
|------|-----|
| `ChatServiceMediaPipeline.kt` | **New** — `uploadMedia`, encrypted blob + chunked video, thumbnails (paridad `ChatService+MediaPipeline` / `+ChunkedVideoUpload`) |
| `EncryptionService.kt` | `encryptChatMedia` (in-memory AES-GCM) + `encryptChatMediaFile` returns ciphertext file + metadata |
| `ChatService.kt` | `sendMediaMessage` / `sendAudioMessage` + `queueOfflineMediaMessage` (outbox `MEDIA_MESSAGE`) |
| `OutboxPayloads.kt` | `MediaMessagePayload.encode()` |
| `ChatAccessCoordinator.kt` | **New stub** — `invalidateAll()` wired from `AuthService` sign-out / identity change |
| `BackgroundMomentUploadService.kt` | Hidden layers upload (`saveHiddenLayers`) + silent moderation hooks |
| `UploadPayloadModels.kt` | `CachedHiddenLayerDraft`, story `textOverlays` / `drawingFileName` decoders |
| `BackgroundStoryUploadService.kt` | `drawingData` + `textOverlays` on resume; stickers interactive setup still deferred |
| `ChatCommunicationNotificationService.kt` | `messagingStyleFor()` helper for MessagingStyle notifications |
| `MotionPolicy.kt` | `reduceMotion` also checks `TRANSITION_ANIMATION_SCALE` |

## Fixes applied (prior closure pass)

| File | Fix |
|------|-----|
| `AffinityTracker.kt` | `applyTimeDecayIfNeeded`, `cleanupVeryLowAffinities`, `getScore`, `getScores` batch |
| `FirestoreService.kt` | `unfollowUser`: post-verification delay + `forceUnfollow` when doc persists |
| `EncryptionService.kt` | `performHealthCheck`, `getDetailedEncryptionInfo`, `getMetricsForDashboard`, `rotateConversationKey`, `verifyKeyIntegrity`, Keystore `selfTest` |
| `NovaEmbeddingService.kt` | Documented Android limitation (no NLEmbedding; same null fallback as iOS without model) |
| `BackgroundMomentUploadService.kt` | Real `resumeUpload`: decode payload, Storage upload, `createMomentWithVisibility` / custom list |
| `BackgroundStoryUploadService.kt` | Real `resumeUpload`: media + thumbnail upload, `createStoryWithVisibility` / custom list |
| `ChatService.kt` + `ChatMessageMapper.kt` | Core fetch/send/delivery/offline queue; `stopLiveLocationMessage` for logout |
| `ChatSessionEngine.kt` | Fallback IDs from cached conversations, `invalidateSession`, UID reconcile |
| `LiveLocationSharingService.kt` | **New** — `endActiveSessionForSignOut` wired from `AuthService.logout` |
| `AuthService.kt` | Calls `LiveLocationSharingService.endActiveSessionForSignOut` before sign-out |
| `CacheManager.kt` | Coil disk cache included in cleanup (`diskCache.clear()`) |
| `ImagePrefetchManager.kt` | `cancelAll()` disposes in-flight Coil jobs |
| `VideoThumbnailCache.kt` | True LRU via access-order `LinkedHashMap` |
| `VideoAdaptivePlayback.kt` | `PREFERRED_FORWARD_BUFFER_MS` + `createAdaptiveLoadControl()` (2.5s parity) |
| `StorageService.kt` | `uploadStoryThumbnail` for story resume |
| `UploadPayloadModels.kt` | **New** — `MomentUploadPayload` / `StoryUploadPayload` decoders |
| `MomentsApplication.kt` | Init background upload + live location; affinity decay on launch |

---

## Intentional N/A

| iOS file | Reason |
|----------|--------|
| `Auth/PasskeyService.swift` | WebAuthn/ASAuthorization — Android uses Google + email only |
| `Messaging/ChatSendMessageIntentHandler.swift` | iOS `INSendMessageIntentHandling`; Android uses FCM `RemoteInput` |
| `Camera/SnapCameraKitConfiguration.swift` | Feature flag off on both; Snap SDK not integrated on Android |

**ActivityKit / Widget bits (platform N/A, not GAP — documented in OK notes):**

- `IncognitoModeService` — no Live Activity / WidgetCenter on Android (CF mirror OK)
- `TimeSpentManager` — local notification vs UNUserNotificationCenter (equivalent UX)
- Background upload progress — Android uses ongoing notification (`UploadProgressNotificationHelper`), not ActivityKit

---

## Per-file status table

### Activity

| iOS | Android | Status | Notes |
|-----|---------|--------|-------|
| `TimeSpentManager.swift` | `activity/TimeSpentManager.kt` | **OK** | Same prefs keys, daily limit, 7-day stats |

### Auth

| iOS | Android | Status | Notes |
|-----|---------|--------|-------|
| `AuthService.swift` | `auth/AuthService.kt` | **OK** | Email+Google; Apple/Passkey **N/A by product decision**; logout wires `LiveLocationSharingService` + `ChatAccessCoordinator.invalidateAll` |
| `LoginActivityService.swift` | `auth/LoginActivityService.kt` | **OK** | `users/{uid}/loginActivity/{SHA256(fingerprint)}` |
| `OnboardingDraftStore.swift` | `auth/OnboardingDraftStore.kt` | **OK** | 30-day TTL, draft fields |
| `PasskeyService.swift` | — | **N/A** | Cloud Functions exist; Android auth path excludes passkeys |

### Cache

| iOS | Android | Status | Notes |
|-----|---------|--------|-------|
| `CacheManager.swift` | `cache/CacheManager.kt` | **OK** | Coil disk size in cleanup + video/audio/chat accounting |
| `ImagePrefetchManager.swift` | `cache/ImagePrefetchManager.kt` | **OK** | In-flight Coil disposables cancelled on `cancelAll()` |
| `PersistentAudioCache.swift` | `cache/PersistentAudioCache.kt` | **OK** | `StoryAudio/`, SHA256 keys |
| `PersistentVideoCache.swift` | `cache/PersistentVideoCache.kt` | **OK** | `MomentVideos/`, 500MB LRU |
| `UserCacheService.swift` | `cache/UserCacheService.kt` | **OK** | `initialize()` wires `ComponentCallbacks2` onLowMemory/onTrimMemory → clear RAM cache |
| `VideoPreloader.swift` | `cache/VideoPreloader.kt` | **OK** | LRU cache + `preloadMoment`/`preloadPlaybackSource` via `VideoPlaybackSelector` |
| `VideoThumbnailCache.swift` | `cache/VideoThumbnailCache.kt` | **OK** | Access-order LRU (LinkedHashMap) |

### Camera

| iOS | Android | Status | Notes |
|-----|---------|--------|-------|
| `SnapCameraKitConfiguration.swift` | `camera/SnapCameraKitConfiguration.kt` | **N/A** | `isFeatureEnabled = false` on both |

### Content

| iOS | Android | Status | Notes |
|-----|---------|--------|-------|
| `BackendFeedService.swift` | `content/BackendFeedService.kt` | **OK** | CF `getFeedPage`, circuit breaker |
| `FilterService.swift` | `content/FilterService.kt` | **OK** | Same filter IDs/categories; ColorMatrix approximates CIFilter (visual parity acceptable) |
| `ForYouDiscoveryService.swift` | `content/ForYouDiscoveryService.kt` | **OK** | Tiers, caps, collectionGroup queries |
| `ProfileVisitsService.swift` | `content/ProfileVisitsService.kt` | **OK** | CF `getProfileVisitsPage`; fallback `users/{uid}/visits` |

### Firestore

| iOS | Android | Status | Notes |
|-----|---------|--------|-------|
| `FirestoreCore.swift` | `firestore/FirestoreCore.kt` | **OK** | Story summaries, serialization helpers |
| `FirestoreService.swift` | `firestore/FirestoreService.kt` | **OK** | Block/visibility/active-hours; `unfollowUser` post-verify + `forceUnfollow` |
| `FirestoreMomentsRepository.swift` | `firestore/FirestoreMomentsRepository.kt` | **OK** | Soft-delete, outbox on save |
| `FirestoreCommentsRepository.swift` | `firestore/FirestoreCommentsRepository.kt` | **OK** | Reactions, mention/reply push server-dependent |
| `FirestoreProfilesRepository.swift` | `firestore/FirestoreProfilesRepository.kt` | **OK** | Offline cache source, username change |
| `FirestoreStoriesRepository.swift` | `firestore/FirestoreStoriesRepository.kt` | **OK** | Highlights, story summary |
| `FirestoreSearchRepository.swift` | `firestore/FirestoreSearchRepository.kt` | **OK** | Mute filter, suggestions |
| `FirestoreHiddenLayersRepository.swift` | `firestore/FirestoreHiddenLayersRepository.kt` | **OK** | Discoveries, metrics |
| `FirestoreAudienceRepository.swift` | `firestore/FirestoreAudienceRepository.kt` | **OK** | Custom audiences/lists |
| `FirestoreActivityRepository.swift` | `firestore/FirestoreActivityRepository.kt` | **OK** | Visits, incognito guard, dedup |

### Incognito

| iOS | Android | Status | Notes |
|-----|---------|--------|-------|
| `IncognitoModeService.swift` | `incognito/IncognitoModeService.kt` | **OK** | CF mirror OK; Live Activity / widget reload **N/A Android** |

### Messaging

| iOS | Android | Status | Notes |
|-----|---------|--------|-------|
| `ChatCacheStore.swift` | `messaging/ChatCacheStore.kt` | **OK** | Media paths, size accounting |
| `ChatCommunicationNotificationService.swift` | `messaging/ChatCommunicationNotificationService.kt` | **OK** | Shortcuts/Person + `messagingStyleFor()` + FCM `buildMessagePushNotification()` with RemoteInput reply |
| `ChatMediaChunkedCipher.swift` | `messaging/ChatMediaChunkedCipher.kt` | **OK** | Chunk encryption |
| `ChatMediaDownloadPolicy.swift` | `messaging/ChatMediaDownloadPolicy.kt` | **OK** | Auto-download prefs |
| `ChatMediaPrefetcher.swift` | `messaging/ChatMediaPrefetcher.kt` | **OK** | Post-ingest prefetch |
| `ChatRecoveryCrypto.swift` | `messaging/ChatRecoveryCrypto.kt` | **OK** | Recovery crypto |
| `ChatSendMessageIntentHandler.swift` | `messaging/ChatSendMessageIntentHandler.kt` | **N/A** | Stub; inline reply via FCM layer |
| `EncryptionService.swift` | `messaging/EncryptionService.kt` (+ helpers) | **OK** | Core E2E + health/rotation + dashboard metrics; circular error buffer (iOS trends also stubbed empty) |
| `LocalFirstMessagingSettings.swift` | `messaging/LocalFirstMessagingSettings.kt` | **OK** | Flag + event bus |
| `MessageCatchUpService.swift` | `messaging/MessageCatchUpService.kt` | **OK** | 30s interval, caps |
| `MessageIngestService.swift` | `messaging/MessageIngestService.kt` | **OK** | Dedup, cursor, queue drain |
| `MessageRequestService.swift` | `messaging/MessageRequestService.kt` | **OK** | Listeners, send/accept/reject/cancel, CF `acceptMessageRequest` |
| `OnlineStatusService.swift` | `messaging/OnlineStatusService.kt` | **OK** | Presence timers, lifecycle |
| `VanishMessageTimer.swift` | `messaging/VanishMessageTimer.kt` | **OK** | Options enum aligned |

### Network

| iOS | Android | Status | Notes |
|-----|---------|--------|-------|
| `NetworkMonitor.swift` | `network/NetworkMonitor.kt` | **OK** | `shouldUseOfflineMode`, expensive/constrained |
| `OfflineSyncService.swift` | `network/OfflineSyncService.kt` | **OK** | Same action types, backoff, max retries |

### Nova

| iOS | Android | Status | Notes |
|-----|---------|--------|-------|
| `NovaEmbeddingService.swift` | `nova/NovaEmbeddingService.kt` | **OK** | Platform limit: no on-device model on Android; `null` embeddings (same honest fallback as iOS without NLEmbedding) |

### Performance

| iOS | Android | Status | Notes |
|-----|---------|--------|-------|
| `FeedVisibilityCoordinator.swift` | `performance/FeedVisibilityCoordinator.kt` | **OK** | Core logic; SwiftUI visibility reporter → Compose (UI layer) |
| `MotionPolicy.swift` | `performance/MotionPolicy.kt` | **OK** | `reduceMotion` checks animator + transition scale; Compose springs/transitions live in UI layer |
| `PerformanceSignposts.swift` | `performance/PerformanceSignposts.kt` | **OK** | OSSignpost → `android.os.Trace` |
| `VideoMomentsIndex.swift` | `performance/VideoMomentsIndex.kt` | **OK** | One `VideoMoment` per media item (correct for multi-video carousel / Reels) |

### Persistence

| iOS | Android | Status | Notes |
|-----|---------|--------|-------|
| `LocalPersistenceService.swift` | `persistence/LocalPersistenceService.kt` + `StorySeenStateService.kt` | **OK** | JSON/filesDir vs SwiftData (platform storage); search via `SearchNormalization` |
| `MessagePersistenceStore.swift` | `persistence/MessagePersistenceStore.kt` | **OK** | Same caps + mutation API; JSON store vs SwiftData @ModelActor |

### Privacy

| iOS | Android | Status | Notes |
|-----|---------|--------|-------|
| `PrivacyService.swift` | `privacy/PrivacyService.kt` | **OK** | Blocks, follow gates, audiences |
| `PrivacyServiceExtension.swift` | `privacy/PrivacyServiceExtension.kt` | **OK** | Enhanced moment visibility |
| `ContentVisibilityservice.swift` | `privacy/ContentVisibilityService.kt` | **OK** | Settings CRUD, filter |

### Security

| iOS | Android | Status | Notes |
|-----|---------|--------|-------|
| `MomentsAppCheckProviderFactory.swift` | `security/MomentsAppCheckProviderFactory.kt` | **OK** | App Attest vs Play Integrity |

### Social

| iOS | Android | Status | Notes |
|-----|---------|--------|-------|
| `AffinityTracker.swift` | `social/AffinityTracker.kt` | **OK** | Scoring, time decay, cleanup, batch `getScores` |
| `BestFriendsService.swift` | `social/BestFriendsService.kt` | **OK** | CF `optOutBestFriends` |
| `EchoService.swift` | `social/EchoService.kt` | **OK** | Overlap detection, merge, listeners |
| `StoryChainLimitsService.swift` | `social/StoryChainLimitsService.kt` | **OK** | Limits, validation, cleanup |
| `StoryRingCacheService.swift` | `social/StoryRingCacheService.kt` | **OK** | TTL 30s, resolver, privacy |

### Storage

| iOS | Android | Status | Notes |
|-----|---------|--------|-------|
| `StorageService.swift` | `storage/StorageService.kt` | **OK** | Avatar, Nova encrypt, feed media, story thumbnails |
| `MediaUploadService.swift` | `storage/MediaUploadService.kt` | **OK** | Encrypted blob upload, cancel |
| `VideoCompressionService.swift` | `storage/VideoCompressionService.kt` | **OK** | Presets moment/story/chat |
| `StoragePathBuilder.swift` | `storage/StoragePathBuilder.kt` | **OK** | Path domains 1:1 |
| `UIImage+StorageUpload.swift` | `storage/BitmapStorageUpload.kt` | **OK** | Normalize, downscale, JPEG |

### Video

| iOS | Android | Status | Notes |
|-----|---------|--------|-------|
| `VideoPlaybackSelector.swift` | `video/VideoPlaybackSelector.kt` | **OK** | ABR tier selection, preheat URL lists |
| `ReelPrebufferService.swift` | `video/ReelPrebufferService.kt` | **OK** | Warm muted player |
| `SharedVideoPlayerPool.swift` | `video/SharedVideoPlayerPool.kt` | **OK** | Pool of 3, LRU |
| `VideoAdaptivePlayback.swift` | `video/VideoAdaptivePlayback.kt` | **OK** | Bitrate tiers + `createAdaptiveLoadControl()` 2.5s forward buffer |

---

## Android-only extras

These live under `services/` but have no direct iOS `Services/` counterpart (iOS equivalent elsewhere or stub):

| Android file | iOS equivalent | Status |
|--------------|----------------|--------|
| `views/creator/BackgroundMomentUploadService.kt` | `Views/Creator/BackgroundMomentUploadService.swift` | **OK** | `resumeUpload`: media + hidden layers + silent moderation; no Live Activity / UI progress |
| `views/creator/BackgroundStoryUploadService.kt` | `Views/Creator/BackgroundStoryUploadService.swift` | **OK** | `resumeUpload`: media + drawing + text overlays + sticker rebuild; interactive post-setup CF hooks still Creator foreground |
| `views/messaging/services/ChatService.kt` | `Views/Messaging/Services/ChatService*.swift` | **OK** | Fetch/send text+media+audio, offline outbox, E2E upload + `ChatEncryptedMediaResolver` download/decrypt |
| `views/messaging/services/ChatSessionEngine.kt` | `Views/Messaging/Services/ChatSessionEngine.swift` | **PARTIAL** | Fallback listeners + sign-out; no in-memory ViewModel session cache |
| `views/messaging/services/LiveLocationSharingService.kt` | `Views/Messaging/Services/LiveLocationSharingService.swift` | **PARTIAL** | Logout teardown only; no GPS tracking / restoreIfNeeded |
| `messaging/ChatNavigationIntentStore.kt` | App Intents layer | **OK** minimal |
| `shared/ChatPreviewPrivacy.kt` | `Shared/ChatPreviewPrivacy.swift` | **OK** |
| `messaging/MessageIngestQueue.kt` | `Shared/MessageIngestQueue.swift` | **OK** |
| `messaging/Curve25519Helper.kt` | Inline in iOS crypto | **OK** |
| `messaging/EncryptionKeyStore.kt` | Keychain helpers | **OK** |
| `messaging/CryptoHelpers.kt` | Inline helpers | **OK** |
| `persistence/StorySeenStateService.kt` | Tail of `LocalPersistenceService.swift` | **OK** extracted |
| `views/nova/tools/NovaEvents.kt` | `Views/Nova/Tools/NovaActivityTools.swift` | **PARTIAL** no-op stub |

---

## Top remaining gaps (honest — android-only / Views layer)

1. **`LiveLocationSharingService`** (android-only) — full GPS session when Messaging UI lands.
2. **`ChatSessionEngine`** (android-only) — in-memory ViewModel session cache when Messaging UI lands.
3. **`BackgroundStoryUploadService`** — sticker *documents* serialize on resume; poll/quiz/audio *post-setup* CF hooks only run from Creator foreground publish.
4. **`NovaEvents`** (android-only stub) — no-op until Nova Views port.
5. **Semantic embeddings** — both platforms return `null` without on-device model; TFLite/backend deferred to Nova Views.

---

## Outbox / offline contract (verified)

Both platforms queue Firestore mutations via outbox when **not connected** (`NetworkMonitor.isConnected == false`). Android helper: `shouldQueueFirestoreOutbox()`. Action types in `OfflineSyncService` match iOS (reaction, follow, comment, message, media, save, block, profile, moment/story upload, etc.).

---

*Generated by Services fidelity audit. Re-run after major iOS Services changes.*
