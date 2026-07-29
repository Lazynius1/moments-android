# Inventario iOS → Android (Moments)

Checklist de archivos Swift en `Moments/` con conteo de líneas y **seguimiento de paridad**.
Usar como guía para portar **trozo a trozo** al `.kt` equivalente.

> Estado inicial de **Paridad** importado desde [PORT_FILES.md](PORT_FILES.md) (fase 1).
> Actualizar la columna **Paridad** al cerrar cada archivo vs su Swift.

## Resumen

| Métrica | Valor |
|---------|------:|
| Archivos `.swift` | 587 |
| Líneas totales | 254,611 |
| Promedio líneas/archivo | 433 |

### Paridad (actualizar aquí)

| Estado | Significado | Archivos |
|--------|-------------|--------:|
| `[ ]` | Pendiente — sin paridad cerrada | 13 |
| `[~]` | En curso — `.kt` existe, falta comparar/cerrar vs Swift | 159 |
| `[x]` | Cerrado — revisado 1:1 contra Swift | 406 |
| 🚫 | Fuera de alcance Android | 14 |
| **En alcance** | excl. 🚫 (+ `[N/A]`) | 573 |
| **Progreso paridad** | `[x]` / en alcance | 406 / 573 (70.9%) |

### Cerrados — líneas iOS vs Android

| Archivo | iOS | Android | Δ | Notas |
|---------|----:|--------:|--:|-------|
| `AnimatedStickerView` | 87 | 71 | -16 | Coil = GIFCache; sin spinner (paridad iOS) |
| `AudienceIconView` | 74 | 110 | +36 | Métricas + ActivityGrid; isDark ≡ colorScheme |
| `CommentRowSkeletonView` | 46 | 85 | +39 | shimmer compartido; padding en call site |
| `EchoesIconView` | 75 | 107 | +32 | métricas + brand orange→purple |
| `HiddenLayerLayout` | 87 | 93 | +6 | +frame(HiddenLayerDraft) |
| `InAppBannerView` | 405 | 535 | +130 | glass+gestos+preview+strings 8 locales |
| `InAppMessageQuickReplyPanel` | 148 | 270 | +122 | glass+focus+preview+send; conversationPreview |
| `IntelligentGlow` | 64 | 165 | +101 | 3 capas + blur + ciclo 3s; MotionPolicy |
| `InteractiveStickerSharedViews` | 2012 | 2417 | +405 | Polaroid/Quiz/Countdown/EmojiSlider/Dither/Audio + surfaces; Caveat≈Cursive |
| `LiveUsernameText` | 60 | 87 | +27 | LiveUsernameContent + Text; fallback "Usuario" como iOS |
| `LocationMomentCardSkeletonView` | 50 | 88 | +38 | shimmer compartido; overlay avatar+líneas; a11y hidden |
| `MomentHashtagText` | 205 | 97 | −108 | parser+link+mentions+action |
| `MomentCaptionView` | 408 | 598 | +190 | HashtagText+sheet+Reels truncado (revisado muestras) |
| `MomentRefresh` | 191 | 201 | +10 | state+detector+gota; overlay host corregido (audit) |
| `MomentRowButton` | 81 | 90 | +9 | press/menu+haptic; menu bg drawBehind (fix audit) |
| `OfflineBanner` | 202 | 206 | +4 | offline+slow pills; auto-hide 4s/5s; restore |
| `SkeletonShimmer` | 36 | 28 | −8 | pulso 0.04↔0.22 / 1.4s + MotionPolicy |
| `StoryViewerSkeletonView` | 46 | 42 | −4 | segmentos+header+shimmer+a11y |
| `UserRowSkeletonView` | 47 | 45 | −2 | fila+lista; surface dark/light |
| `VerifiedBadge` | 138 | 134 | −4 | seal gradient+Username*; +VerifiedBadgeView load |
| `RefreshControl` | 53 | 127 | +74 | midY delta>50 + spinner glass; height 0 como iOS |
| `MomentRailComponents` | 295 | 300 | +5 | ModernActionButtons+FollowButton; AdaptiveColors en .kt aparte |
| `AudienceModels` | 162 | 183 | +21 | ContentAudience+CustomAudienceList+CaptionAudienceSetting |
| `AudienceSelectionRows` | 372 | 506 | +134 | filas+grid+carousel; strings 8 locales |
| `CustomAudienceManagementViews` | 563 | 722 | +159 | selector+lists+card+VM desde Swift; UserSelectionCard iOS |
| `CustomListSelectorView` | 152 | 249 | +97 | cancel/+; sheet ListsView; Auth uid load; strings |
| `BackgroundMomentUploadService` | 1481 | 1154 | −327 | UploadingMoment+persist+resume→uploadMoment; Live Activity 🚫 stubs; overlay retry = archivo aparte |
| `FloatingMomentUploadOverlay` | 667 | 798 | +131 | orb+panel+rocket/ripple parallel+aura blur; haptics orb/panel |
| `MomentReactionButton` | 1077 | 1195 | +118 | EpicButton+picker+particles+sheet+listener; strings 8 locales |
| `reacciones` | 539 | 377 | −162 | types+tracker+Modern+Picker; FS helpers en FirestoreService |
| `StoragePathBuilder` | 217 | 249 | +32 | domains+targets+sanitize+extractObjectPath 1:1 |
| `MediaUploadService` | 286 | 234 | −52 | sessions+encrypt blob/file+cancel prefix+resolve/delete |
| `StorageService` | 344 | 302 | −42 | profile/nova/feed/hidden layers/delete; StorageError aquí |
| `UIImage+StorageUpload` | 51 | 40 | −11 | BitmapStorageUpload normalize+JPEG |
| `VideoCompressionService` | 114 | 212 | +98 | presets+límites CreatorMedia; Media3 720p; strings 8 locales |
| `NetworkMonitor` | 87 | 159 | +72 | ConnectivityManager; Flows; slow/offline helpers |
| `OfflineSyncService` | 635 | 572 | −63 | cola+backoff+optimize toggles; todos ActionType |
| `VideoThumbnailCache` | 58 | 71 | +13 | mem+disk SHA256; 0.8s@480 |
| `PersistentAudioCache` | 94 | 90 | −4 | StoryAudio SHA256 .m4a + cleanup 7d |
| `ImagePrefetchManager` | 97 | 110 | +13 | Coil; max 20; retry 2×2s; cancelAll |
| `VideoPreloader` | 115 | 91 | −24 | Media3 + PersistentVideoCache; signpost |
| `PersistentVideoCache` | 131 | 84 | −47 | MomentVideos 500MB LRU |
| `UserCacheService` | 135 | 122 | −13 | 5min TTL; coalesce; trim memory |
| `CacheManager` | 240 | 144 | −96 | 2GB/1.5GB; Coil mem; video/audio/temp |
| `PerformanceSignposts` | 24 | 25 | +1 | Trace begin/end/event + makeID |
| `VideoMomentsIndex` | 20 | 52 | +32 | 1 VideoMoment/moment; Reels start |
| `MotionPolicy` | 88 | 80 | −8 | reduceMotion+partículas+Spring/Transition |
| `FeedVisibilityCoordinator` | 96 | 67 | −29 | umbral 0.55; PreferenceKey queda en View |
| `ReelPrebufferService` | 45 | 64 | +19 | ExoPlayer warm mudo; take/discard |
| `SharedVideoPlayerPool` | 96 | 106 | +10 | pool 3 + eviction handlers |
| `VideoAdaptivePlayback` | 132 | 144 | +12 | bitrate+stall downgrade+recovery |
| `VideoPlaybackSelector` | 194 | 125 | −69 | ABR low/med/high; NetworkMonitor |
| `ProfileVisitsService` | 108 | 86 | −22 | CF+Firestore; VisitGrouping |
| `ForYouDiscoveryService` | 247 | 168 | −79 | tiers A/B/C + global everyone |
| `FilterService` | 260 | 166 | −94 | CIFilter→ColorMatrix; looks |
| `BackendFeedService` | 692 | 848 | +156 | CF feed/profile/tags/tray+circuit |
| `BestFriendsService` | 149 | 87 | −62 | add/remove/optOut CF+fetch |
| `StoryChainLimitsService` | 193 | 147 | −46 | 10 partes/48h/5min; strings |
| `AffinityTracker` | 213 | 164 | −49 | scores+decay; prefs JSON |
| `StoryRingCacheService` | 297 | 230 | −67 | cache+resolver privacy/seen |
| `EchoService` | 592 | 435 | −157 | overlap 500m/24h; accept/leave |
| `IncognitoModeService` | 414 | 389 | −25 | CF+mirror+countdown; Live Activity 🚫 |
| `NovaEmbeddingService` | 117 | 67 | −50 | cosine+dedup; NLEmbedding 🚫 stub null |
| `TimeSpentManager` | 222 | 279 | +57 | foreground+limit+banner+local notif |
| `SnapCameraKitConfiguration` | 37 | 59 | +22 | flag off; BuildConfig≡plist; SDK 🚫 |
| `MomentsAppCheckProviderFactory` | 15 | 43 | +28 | Play Integrity↔App Attest; wire Application |
| `ContentVisibilityservice` | 278 | 176 | −102 | settings+filter 1:1; sin adapters inventados |
| `PrivacyService` | 1424 | 738 | −686 | core+extensiones del mismo .swift |
| `PrivacyServiceExtension` | 45 | 30 | −15 | solo filterVisibleContent+canViewMoment |
| `FirestoreActivityRepository` | 157 | 106 | −51 | visits+summary+lastOpen/Moment; incognito |
| `FirestoreAudienceRepository` | 167 | 159 | −8 | customAudiences/Lists; +create/update de VMs |
| `FirestoreCore` | 163 | 183 | +20 | summary+expiración+media; helpers Stories/outbox |
| `FirestoreSearchRepository` | 306 | 144 | −162 | 2 overloads search; mute; suggestions; Explore helper |
| `FirestoreHiddenLayersRepository` | 377 | 194 | −183 | save/fetch/discoveries/metrics/moderation |
| `FirestoreProfilesRepository` | 473 | 248 | −225 | create/change/fetch+availability/mutuals; Auth email fallback |
| `FirestoreCommentsRepository` | 807 | 545 | −262 | CRUD+reactions+menciones/reply+audience gate |
| `FirestoreMomentsRepository` | 988 | 524 | −464 | CRUD+save+create/fetch; addDoc vs set+mapVis; visibility≡iOS |
| `FirestoreStoriesRepository` | 1179 | 627 | −552 | create+chain+summary+prefetch; stickers positionX/Y |
| `FirestoreService` | 1746 | 876 | −870 | follow/block/reactions; quitó stubs que eclipsaban Moments |
| `ChatCacheStore` | 442 | 345 | −97 | media cache+quota; poster fallback; write atómico |
| `ChatCommunicationNotificationService` | 24 | 28 | +4 | solo donateFromPush → IntentDonor |
| `ChatCommunicationIntentDonor` | 137 | 211 | +74 | donate+MessagingStyle+reply (Shared) |
| `ChatMediaChunkedCipher` | 183 | 196 | +13 | MCHAT02 AES-GCM; cleanup output on error |
| `ChatMediaDownloadPolicy` | 118 | 98 | −20 | wifi/always/never; retention; SharedPrefs≈AppGroup |
| `ChatMediaPrefetcher` | 66 | 82 | +16 | maxConcurrent=3; pump/finish≡iOS; no image/video view-once |
| `ChatRecoveryCrypto` | 51 | 44 | −7 | PBKDF2-HMAC-SHA256; salt 32; Nonce helper en call site |
| `LocalFirstMessagingSettings` | 16 | 49 | +33 | default true; messagesIngested; highlights→IntentStore |
| `VanishMessageTimer` | 58 | 51 | −7 | onceSeen/24h/7d; notice tokens; expiresAt desde seen |
| `MessageCatchUpService` | 150 | 122 | −28 | 30s/20 conv/50×10 pages; IngestSource→IngestService |
| `MessageIngestService` | 270 | 221 | −49 | catch-up fire-and-forget; cursor solo en batch |
| `OnlineStatusService` | 332 | 284 | −48 | presence displayName; formatLastSeen loc; autoChange API |
| `MessageRequestService` | 660 | 376 | −284 | alreadyPending≠update; CF accept; policy following |
| `EncryptionService` | 3108 | 1156 | −1952 | limpio inventos; Δ≈types+device-scan+metrics upload iOS |
| `MessageIngestQueue` | 154 | 163 | +9 | queue+cursor store; write atómico; SharedPrefs≈AppGroup |
| `ChatAccessCoordinator` | 90 | 125 | +35 | ensure/refresh/invalidate; unavailable string loc |
| `ChatBuzzProcessedStore` | 39 | 120 | +81 | array ordenado JSON (≠StringSet); tope 40 sufijo; init |
| `ChatDraftStore` | 51 | 112 | +61 | draft+notifs; VanishMode+active; SharedFlow≡NC |
| `ChatGiphyService` | 90 | 105 | +15 | proxy CF region+rating; hasMore/pagination; query≡isEmpty |
| `ChatKeyboardScrollCoordinator` | 96 | 99 | +3 | IME insets≡keyboard frame; transition reset +32ms |
| `ChatNavigationIntentStore` | 70 | 95 | +25 | peek/enqueue/clear; SharedFlow≡NC reaction/buzz |
| `ChatRowHeightEstimator` | 158 | 170 | +12 | tipos+cluster+media; minW 40/80; Dp≡pt |
| `ChatScrollStateStore` | 80 | 81 | +1 | solo clear legacy; Bottom.messageId non-null |
| `ChatService+Buzz` | 93 | 118 | +25 | send+listen buzzEvents; gen; TTL≡replayWindow |
| `ChatService+ChunkedVideoUpload` | 125 | 177 | +52 | encrypt file+thumb; markUploadFinished≡defer; frames 0.15/0/0.5 |
| `ChatService+EncryptedMediaResolver` | 381 | 352 | −29 | resolve+cache; progress download; async parallel |
| `ChatService+EphemeralCleanup` | 178 | 170 | −8 | collectionGroup senderId; literal ES cifrado; timer +30s/1h |
| `ChatService+LocalFirstSnapshot` | 181 | 182 | +1 | cache hit+metadata; hydrate paralelo; ViewOnce apply |
| `ChatService+MediaPipeline` | 484 | 421 | −63 | upload+encrypt+thumbs; stage/mark; deleteMedia* |
| `ChatService+MessageActions` | 128 | 148 | +20 | forward E2E+preview neutra; star; updateConversation |
| `ChatService+MessageHydration` | 355 | 367 | +12 | ViewOnce store+createBasic+buildEnhanced in-file (sin mapper extra) |
| `ChatService+MessageReactions` | 159 | 153 | −6 | collectionGroup listen+fetch; merge legacy/live; gen listener |
| `ChatService+Search` | 89 | 57 | −32 | batch 200; decrypt+override; diacríticos; sort asc |
| `ChatService+SharingAndViewOnce` | 557 | 354 | −203 | materialize en core (sin cambiar su cuerpo); Δ≈boilerplate |
| `ChatService+VanishMode` | 253 | 201 | −52 | APIs juntas; sin vanishEvents inventado; notice screenshot/rec |
| `ChatSessionEngine` | 184 | 220 | +36 | cache VM+activate/preload/invalidate; unread ≡ `?? true`; eviction distantPast |
| `ViewOnceConsumptionService` | 33 | 27 | −6 | CF consumeViewOnceMessage europe-southwest1; reasons raw |
| `ChatVideoPosterGenerator` | 59 | 52 | −7 | cache mem+disk; frames 0.15/0/0.5; JPEG 78; quota |
| `LiveLocationSharingService` | 298 | 356 | +58 | GPS+throttle 10s/10m; restore+server; stop OR match; signOut |
| `ChatService` (core) | 2835 | 2043 | −792 | APIs 1:1; FAILED+late-ack; deleteMessage; status bus; Δ≈completions/ext |
| `MessageItem` | 49 | 67 | +18 | MessageItem+ChatRenderRow+Section; ids epoch segundos |
| `MessagingViewModel` | 856 | 727 | −129 | refreshUser/applyLocal; unread ≡ `?? true`; startConversation cubre createOrFind |
| `MessageModel` | 2514 | 1327 | −1187 | Core path; +status/error/request display; Δ≈Codable/OO |
| `ChatViewModel` | 3520 | 2284 | −1236 | Core; sin PHAsset/PhotosPicker; Δ≈UIKit/observers/OO |
| `ChatAttachmentAssets` | 151 | 170 | +19 | Giphy/sticker/location/live; recent stickers SharedPrefs |
| `ChatAttachmentSheet` | 1113 | 952 | −161 | pickers+menu+photos; fling predicted+primer+search glass; Δ≈PHAsset/UIKit |
| `ChatCameraView` | 460 | 534 | +74 | BackHandler; gallery thumb; recording pill+timer; haptic; aspectRatio; Boundary mic→Settings |
| `CameraAccessBoundary` | 106 | 160 | +54 | primer vs denied prefs; cam/mic DENIED→Settings; ON_RESUME refresh |
| `ViewOnceMessageBubble` | 287 | 268 | −19 | pills unread/replay/opened/sent+progress; estado desde message; zoom matchedTransition stub |
| `ViewOnceImmersiveViewer` | 613 | 669 | +56 | canvas captureRect; chrome; emoji sheet; consume/replay store; FIT_WITH_BLUR; Δ≈blur vídeo muted |
| `ChatMessageBubbleViews` | 838 | 669 | −169 | row swipe/chrome+badges; texto sin double overlay; link preview; Δ≈LPLink scrape |
| `ChatSpeechBubbleViews` | 432 | 500 | +68 | shape+spoilers+markdown inline+links underline+search diacríticos+gutter; fontScale |
| `ChatMediaViews` | 502 | 565 | +63 | image/video states+download+players; downsample 208×272; drag px; BlurView≈sólido |
| `ChatEphemeralMessageViews` | 337 | 389 | +52 | tap/image/resolving/expired; blur+borders; hydrate; markViewed; Spring.toggle |
| `ChatInputViews` | 801 | 706 | −95 | vanish stroke+fill sólido floating/locked+held cancelOffset+trim seek; Δ no aurora iOS |
| `ChatMessageInteractionModifiers` | 379 | 414 | +35 | pan scroll-friendly+spring return+haptics+longPress 0.42+wrapContentHeight; gutter fillMaxHeight en Row |
| `ChatMessageOptionsMenu` | 535 | 544 | +9 | anchor window→local+safeArea+press chrome+haptic menu+cornerRadius+star.slash≈StarBorder |
| `ChatMessageSupportViews` | 882 | 693 | −189 | reply/quote/reactions+cutout Clear+star+timestamp; status twin checks; clusterHitTargetInset |
| `ChatRecoveryViews` | 781 | 800 | +19 | gate/create/restore/settings+PIN 48×60+lockout; material→sólido; change PIN MomentsModalSheet; lock gradient+press |
| `ConversationContextMenu` | 279 | 355 | +76 | cutout Clear+chrome+layout+MomentRowButton.menu; pin.slash≈slash overlay; systemBars insets; row highlight 0.96 |
| `ChatVanishModeViews` | 620 | 762 | +142 | metrics/overlay/notices/timer MomentsModalSheet+inbox; Δ liquidGlass→sólido; medium-only≈medium+large |
| `SplashScreen` | 103 | 84 | −19 | canvas 0B1215/FAF9F6+logo dark/light+shadow AuthColors; reduceMotion; Δ MinimalSplash no en flujo |
| `SocialConnectionsView` | 791 | 747 | −44 | tabs+search/sort+Visits/Users/Common+pull+floating chips; Profile shell wire; stalker overlay |
| `SocialConnectionUserRow` | 452 | 486 | +34 | metrics+compact follow+mutual cutout+press+ellipsis menu+remove stroke |
| `ActivityCollapsibleFilterScroll` | 201 | 137 | −64 | header fade+floating reveal on scroll-up+PullToRefreshBox |
| `UserListView` | 666 | 674 | +8 | empty states iOS keys+SuggestedUserRow Explore+glass search+ModernProfile row flame/press |
| `HighlightComponents` | 439 | 422 | −17 | grid 9:16+date badge+select ring+editor chrome+cover picker rail |
| `HighlightCreateFlowView` | 148 | 196 | +48 | toolbar cancel/next/back/ellipsis delete+save check/plus |
| `HighlightCreateFlowViewModel` | 256 | 210 | −46 | archive page 24+edit seed+save/delete; defaultTitle localized |
| `HighlightNameCoverStep` | 101 | 150 | +49 | cover 118+editCover+focus; MomentsModalSheet cover picker |
| `HighlightPresentationCoordinator` | 90 | 79 | −11 | sheet/viewer mutex+delay MapSheetPresentationDelay |
| `HighlightSelectStoriesStep` | 33 | 41 | +8 | archiveEmpty+pagination on last appear |
| `HighlightViewer` | 135 | 165 | +30 | load+privacy filter+drag dismiss>120; StoriesView |
| `ProfileHighlightsView` | 372 | 365 | −7 | rail create/load skeleton+context menu edit/delete+viewer/create sheets |
| `UserProfileSharedViews` | 339 | 361 | +22 | StatItem+blur bg+preview+expandable+image viewer+refresh; Flow→ProfileFlowLayout |
| `UserProfileStateViews` | 665 | 613 | −52 | empty/blocked/private/offline; swipe>100 dismiss; msg vía host |
| `UserProfileView` | 688 | 629 | −59 | estados+sheets+SocialConnections; openMessage→chat/pending; zoom |
| `UserProfileZoomNavigation` | 58 | 129 | +71 | SharedTransition sharedBounds; Host overlay; Story+Feed; **todos los zoom source/dest → post-paridad** |
| `ProfileMomentZoomNavigation` | 422 | 342 | −80 | models+source/dest sharedBounds; surfaces; Saved stub; destinations |
| `ProfileHeaderSkeletonView` | 93 | 111 | +18 | header+grid skeleton; metrics 3col; shimmer+a11y |
| `ProfileBentoLayout` | 239 | 160 | −79 | assigner+planner; BentoGrid vía planFrames Dp |
| `ProfileMomentsSection` | 539 | 438 | −101 | metrics bentoHeight Dp; thumbnail chrome; carousel/play/audience |
| `ProfileSavedSection` | 541 | 586 | +45 | filters+grid+recent; zoom; ScreenshotProtected; media/video |
| `ProfileSharedComponents` | 672 | 520 | −152 | error/bio/flow/note; sticky chrome canvas; collapse metrics |
| `ProfileHeaderSection` | 592 | 547 | −45 | StickyChrome+note+ExpandableBio; zoom settings/edit; hasActiveStory; badges 🚫 |
| `ProfileShellComponents` | 588 | 521 | −67 | scroll+collapse tabsMinY; sticky+floating tabs; PTR; ScreenshotProtected; themes 🚫 |
| `ProfileView` | 637 | 616 | −21 | sheets+hero menu+edit/delete; ShowProfileVisits; stories own; temas 🚫 |
| `ProfileGridHeroTransition` | 1229 | 1102 | −127 | peek flying+menu glass; frames+lifted; zoom handoff; expand/retract latentes ≡ Swift |
| `ProfileGridMomentMenu` | 452 | 397 | −55 | hero card+video/badge; visitor rail fijo+glass; avatar authorId; háptica long-press |
| `ProfileGridPreviewEditorView` | 520 | 633 | +113 | crop+pan/pinch; fill/fit+fondo; sheet large desde menú owner |
| `ContextMenu` | 1413 | 1060 | −353 | metrics+ModernShareSheet+AddToStory+StoriesView discoveries; share text+URL |
| `ProfileViewModel` | 857 | 566 | −291 | performRefresh; removeFollower FS; note maxLength; pin/archive/delete; FollowStateStore |
| `IncognitoModeSheet` | 347 | 422 | +75 | ring+CTA glass+onboarding; detents medium/large; Live Activity 🚫 |
| `IncognitoGlobalOverlay` | 214 | 216 | +2 | edge aura+pill glass+expand pause; host MomentsApp |
| `GlassmorphicChatView+ComposerAndChrome` | 1006 | 718 | −288 | plus/buzz/shake+media FS+report+cluster reply/gallery; Δ≈UIKit scroll |
| `ChatMessageForwardSheet` | 28 | 151 | +123 | wrapper+picker local (ShareRecipientsPicker = Feed) |
| `MessageTypeIconView` | 38 | 54 | +16 | AttachmentIcon + fallback Material |
| `ChatSearchNavigationBar` | 35 | 54 | +19 | field+close 44; a11y common_close |
| `ChatStickerMessageBubble` | 46 | 66 | +20 | GIF 140; isMediaPendingResolution; α 0.7 sending |
| `ChatKFImageViews` | 66 | 89 | +23 | Coil≡KF; downsampling+crossfade 200; prefetch remote |
| `MediaProgressRing` | 84 | 58 | −26 | gradient track; % si size>50; Δ≈preview |
| `ChatGifMessageBubble` | 95 | 117 | +22 | layout+AnimatedGIF; pending/sending overlays |
| `ChatMediaSendMode` | 37 | 27 | −10 | ciclo viewOnce→replay→keep; icons+labels 8 locales |
| `ChatMediaOverlayPayload` | 71 | 60 | −11 | payload+usesLive/resolved*; stickers=StickerData (Compose) |
| `CachedConnection` | 19 | 17 | −2 | id userId_targetId_type; follower|following |
| `CachedAction` | 66 | 83 | +17 | offline queue; ActionType/Status 1:1; UUID id; ByteArray eq |
| `CachedSearch` | 20 | 17 | −3 | id query_type_targetId; types user|hashtag|text |
| `InterestModels` | 80 | 63 | −17 | InterestOption keys ES; UNKNOWN=unknown; localize≡iOS |
| `UserAffinity` | 39 | 24 | −15 | affinityKey owner|target; score+decay fields; AffinityTracker |
| `AccountHistoryItem` | 46 | 56 | +10 | event types+labelRes+iconName; from/toMap Firestore |
| `CachedNotification` | 105 | 87 | −18 | campos 1:1; from/toNotification; fallback newFollower |
| `OutboxPayloads` | 119 | 224 | +105 | 13 payloads 1:1; encode≡Codable; LPS usa .encode() |
| `EchoModels` | 157 | 221 | +64 | status/participant/momentRef/Echo; create+from+toMap; visibleMoments | status/participant/momentRef/Echo; create+from+toMap; visibleMoments |
| `CachedStory` | 165 | 247 | +82 | fromStory/toStory+overlays/stickers; LPS persiste blobs |
| `CachedConversation` | 166 | 195 | +29 | from/toConversation completo; prefs+vanish+archive; blobs JSON |
| `CachedUser` | 217 | 229 | +12 | from/toAppUser; sin Plus/badges 🚫; LPS vía CachedUser |
| `StickerItem` | 243 | 174 | −69 | MediaLibrary+StickerItem+Type+Interaction; PHAsset 🚫; Bitmap/Point |
| `CachedMoment` | 271 | 321 | +50 | from/toMoment+blobs; pin/grid en prefs LPS; equals ByteArray |
| `ChatSecurityModels` | 278 | 195 | −83 | identity/KDF/bundle/attempts/wrapped/access; chatKey+chatRecovery |
| `CachedMessage` | 305 | 395 | +90 | from/toEnhanced+sanitize file://; blobs full; status fallback .sent |
| `User` | 677 | 269 | −408 | AppUser+enums+privacy; isPlusSubscriber/ads; badges/Plus/themes 🚫 |
| `Models` | 2722 | 1612 | −1110 | Filter→Questions+toMap; UIKit convertStickers/Codable Δ; Notif fallback newFollower |
| `OnboardingDraftStore` | 155 | 189 | +34 | SharedPrefs+filesDir; TTL 30d; JPEG 82; +GOOGLE context |
| `LoginActivityService` | 543 | 412 | −131 | loginActivity FS 1:1; ANDROID_ID↔vendor; LocationManager |
| `AuthService` | 2571 | 1635 | −936 | email+Google; mapAuthError loc×8; Apple/Passkey/Widget 🚫; Δ≈OO |
| `MessagePersistenceStore` | 277 | 465 | +188 | save/reconcile/cursors+merge/trim; extras=LPS helpers JSON |
| `LocalPersistenceService` | 2614 | 1701 | −913 | JSON/prefs vs SwiftData; StorySeen=.kt aparte; **Room → post-paridad** (ver abajo) |
| `AppealService` | 740 | 827 | +87 | split Models+Error; HTTP admin panel 1:1; Dispatchers.IO |
| `CommentsModerationService` | 512 | 426 | −86 | FS settings+proxyOpenAI; categoriesDetailed; platform=android |
| `MediaModerationService` | 1972 | 972 | −1000 | ruta viva=CF moderateMediaContent; Vision/audio client Δ muerto iOS |
| `AppLog` | 27 | 24 | −3 | debug=DEBUG only; error=Log.e |
| `LegacyTypographyScale` | 10 | 21 | +11 | ×0.94 + fontScale; Context overload |
| `ActiveWindowMetrics` | 23 | 60 | +37 | DisplayMetrics; fallback 393×852 |
| `OrientationManager` | 51 | 97 | +46 | accel 0.3s; StateFlow; ×/y thresholds 0.6 |
| `MomentsAppearModifiers` | 38 | 49 | +11 | empty-state spring; MotionPolicy |
| `MomentsPressButtonStyle` | 65 | 94 | +29 | scale/opacity+haptic; subtle/icon |
| `MomentsAudioSession` | 81 | 88 | +7 | AudioFocus ↔ AVAudioSession |
| `EmojiUsageTracker` | 114 | 138 | +24 | prefs+migrate slider; story icons 1:1 |
| `HapticManager` | 154 | 198 | +44 | tones+Vibrator; chatBuzz pulses |
| `MentionParsing` | 137 | 130 | −7 | @regex+draft+resolver FS |
| `MomentsFormat` | 372 | 437 | +65 | relative/smartDate/count/distance; 8 locales |
| `FCMTokenService` | 102 | 99 | −3 | fcmToken FS; retry×3; sin APNs (Δ Android) |
| `NotificationExtensions` | 59 | 81 | +22 | AppEventBus+Coil clearAll |
| `NotificationOpenIntentStore` | 32 | 31 | −1 | enqueue/consume+tab map |
| `NotificationBadgeService` | 228 | 181 | −47 | counts Flow; widget prefs; badge OS 🚫 |
| `InAppNotificationService` | 205 | 167 | −38 | fallback reactions/buzz; banner→coordinator |
| `InAppNotificationPreviewResolver` | 225 | 146 | −79 | decrypt preview; vanish; chat_preview_text |
| `NotificationNavigationService` | 301 | 196 | −105 | AppRouter; keys 1:1; chat_buzz highlight |
| `NotificationCopyResolver` | 412 | 284 | −128 | banners+copy; strings 8 locales OK |
| `NotificationPresentationCoordinator` | 350 | 290 | −60 | dedup; ChatService archived; ingest |
| `Notificationservice` | 633 | 538 | −95 | observe/LPS; markAll; undo delete 3s |
| `AppDelegate` (push) | 261 | 195 | −66 | → FCM+MainActivity+Reply; APNs/Intents Δ |
| `NotificationGroup` | 13 | 11 | −2 | id+list+isUnread |
| `NotificationRowSupport` | 136 | 171 | +35 | metrics; styled/grouped msg; ReactionType skip |
| `NotificationsViewModel` | 329 | 295 | −34 | tabs; group/agg; LPS follow; profile cache |
| `NotificationRowComponents` | 109 | 221 | +112 | avatars AsyncProfile; story thumb+emoji |
| `NotificationSharedViews` | 171 | 201 | +30 | header; skeleton shimmer; toast glass; btn |
| `NotificationGroupedFollowersOverlay` | 323 | 432 | +109 | dialog; follow; story ring; strings 8 |
| `EnhancedNotificationRow` | 304 | 292 | −12 | shell; press; unread; View/Review |
| `EnhancedNotificationRow+Follow` | 196 | 262 | +66 | titles; confirm; store; chrome glass |
| `EnhancedNotificationRow+Trailing` | 311 | 339 | +28 | switch; Accept/Reject; Echo/Export; chain |
| `EnhancedNotificationRow+Previews` | 113 | 215 | +102 | storyPreviewURL; fetch; resolvedAuthor |
| `EnhancedNotificationRow+Messages` | 597 | 582 | −15 | AnnotatedString; mention/tag/reply; strings 8 |
| `NotificationSummaryPopup` | 200 | 293 | +93 | pill; service 30min+1.5s; nav messages/notif |
| `NotificationsView` | 629 | 616 | −13 | tabs; swipe delete; zoom; moderation; empty |
| `SharedChatDecryptor` | 144 | 90 | −54 | facade EncryptionService decrypt/media |
| `NotificationService` (NSE) | 812 | 405 | −407 | FCM≡NSE: preview+reaction+media+counts |
| `ChatPreviewPrivacy` | 26 | 67 | +41 | prefs; vanish; setUserPreviewEnabled |
| `InterestEmojiHelper` | 90 | 103 | +13 | switch+lista; sin inventos InterestOption |
| `Date+Extensions` | 8 | 10 | +2 | timeAgoDisplay → MomentsFormat |
| `Color+Hex` | 79 | 74 | −5 | fromHex/toHex/isLight/contrast; parseAndroidColor |
| `View+LiquidGlass` | 500 | 360 | −140 | API 1:1; render opaco (sin Liquid Glass) |
| `AVAssetImageGenerator+Thumbnail` | 1 | 91 | +90 | Swift vacío; MMR 0.8s@480 helper |
| `LiveActivityThumbnailStore` | 48 | 57 | +9 | cacheDir; load+save+remove; sin App Group |
| `MomentUploadActivityAttributes` | 39 | 27 | −12 | modelo → notificación ongoing |
| `StoryUploadActivityAttributes` | 37 | 26 | −11 | modelo → notificación ongoing |
| `EchoViewModel` | 318 | 295 | −23 | group+live privacy+preload; StateFlow |
| `MomentsApp` | 211 | 258 | +47 | +Application init; resume; WhatsNew; incognito |
| `MainViewModel` | 32 | 63 | +31 | badges feed/notif; ProcessLifecycle ≡ active |
| `LegacyNavigationBridge` | 46 | 58 | +12 | → AppRouter; +wireMentionNavigation |
| `AppRouter` | 226 | 187 | −39 | Destination+dispatch; EventBus ≡ NC |
| `SharedComponents` | 407 | 427 | +20 | carousel/video/avatar/actions; sin loop video |
| `StickerPickerSupportExtensions` | 198 | 217 | +19 | glow/press/MeshGradient; menciones+audience; pressAnimatioon stub |
| `StoryTextEditorChrome` | 598 | 816 | +218 | context+toolbar 6 tools; ColorOption; gradient long-press; strings 8 locales |
| `StoryEditorTextTypes` | 459 | 358 | −101 | presets+legacy styles; fill/stroke/effect/motion; ActiveEditorMode |
| `EditableImageView` | 535 | 494 | −41 | layout+clamp+snap; storyDominantBackgroundColors 36px; gestos onEnd |
| `StoryTextOverlayMetadata` | 427 | 347 | −80 | Draft+build+renderConfig; legacy black/white; Story.resolved* |
| `StickerGiphyViews` | 315 | 371 | +56 | ChatGIFImageCache 40MB; grid 120/12; modelos Giphy |
| `StoryDrawingEditorOverlay` | 914 | 911 | −3 | PencilKit→Canvas; glow bake/slider/palette; ColorPicker panel |
| `StickerMediaInputs` | 397 | 538 | +141 | Selfie+Audio; waveform/glass/primer; strings 8 locales |
| `StickerPickerGeneratedStickers` | 343 | 153 | −190 | time MomentsFormat; weather symbol/fallback 🌤️; drafts (no UIKit bake) |
| `StickerLocationInputView` | 670 | 724 | +54 | gate+palette; category icons; 10 queries prefix4; Geocoder≈MapKit |
| `StickerInputViews` | 1348 | 1177 | −171 | Mention/Link cableados; resto portados (catálogo=insertInstant) |
| `StoryTextOverlayLabel` | 1109 | 673 | −436 | treatments Compose; editor dual-layer; canvas cableado |
| `AlbumPickerView` | 204 | 237 | +33 | sheet+filas+thumb MediaStore; canvas sólido AdaptiveColors |
| `CaptionAndDetailsView` | 845 | 859 | +14 | canvas sólido AdaptiveColors; menciones; prefs; schedule DatePicker; audience Firestore |
| `ContentTypeSelectionView` | 443 | 470 | +27 | dial+shutters; photos PermissionPrimerGate; blur collage; story delay |
| `LocationPickerView` | 570 | 726 | +156 | mapa Compose+key; gate; nearby Geocoder; queries/categorías Localizable |
| `MediaEditingView` | 401 | 546 | +145 | crop sheet; ratio recomendado; filtros; fondo negro sólido (sin blur) |
| `MediaGridCell` | 103 | 179 | +76 | Fit+loading; badge nº/vídeo; tint 00A896 |
| `MediaSelectionView` | 898 | 716 | −182 | PermissionPrimerGate; CameraAccessBoundary; album sheet; preview sin blur |
| `StickerOverlayView` | 1199 | 493 | −706 | geom+pinch base; frame content; zIndex; selfie badge/debounce; content slot |
| `DiscoverMapView` | 938 | 1165 | +227 | mapa+chrome+pins+search+sheet+stories stub+zoom |
| `MapAnnotationModels` | 107 | 90 | −17 | MapsLocation+Combined+5 Moment helpers · sin inventos |
| `MapLocationServices` | 616 | 557 | −59 | LocationUtilities+RegionStore+Search+DisplayFormatter |
| `MapPlaceBottomSheet` | 465 | 551 | +86 | header/chips/gallery/list/index row |
| `MapPlaceStoryDeck` | 117 | 115 | −2 | fetchStories + StoryViewerScreen |
| `MapWeatherEffects` | 318 | 297 | −21 | rain/snow/thunder + overlay color/opacity |
| `MapBottomSheetSection` | 680 | 643 | −37 | LocationBottomSheet gallery/list + cells |
| `MapCanvasSection` | 537 | 522 | −15 | Mapbox canvas + ModernLocationPin/Gallery |
| `WeatherService` | 445 | 217 | −228 | OpenWeather Current 2.5 + cache/rate · overlay helpers |
| `MapDiscoverSupport` | 246 | 282 | +36 | BackendMapStory+VisibilityPolicy+ZoneContext async+formatters |
| `MapPlaceClusterEngine` | 313 | 364 | +51 | build/merge/valid/unnamed · jitter fórmula≡iOS (hash≠Swift) |
| `LocationMomentDetailView` | 1640 | 1537 | −103 | Detail+Card+Sections · ModernPostCard path + visibility |
| `Maps` / LocationMapView | 1985 | 1605 | −380 | View+Support+Chrome · echo/geocode/sheet MomentsModal |
| `StoryRingAvatarView` | 200 | 219 | +19 | Layout+gap mask+baseStroke+resolve; zoom Namespace stub |
| `StorySegmentedRing` | 246 | 222 | −24 | gaps 15º+audiencia+gris visto; triggerHaptic medium |
| `StoryModels` | 214 | 203 | −11 | Reaction/Viewer/Ring+badges Firestore; reexport VerifiedBadge |
| `StoryRepository` | 435 | 330 | −105 | ReplyData+CRUD+decodeBackendStory; Storage helpers |
| `StoryDeckGestureGate` | 54 | 67 | +13 | scopes+regiones+legacy.sticker; Local≡Environment |
| `StoryPlaybackCoordinator` | 224 | 259 | +35 | progress+timer+preload Coil/Video+memory trim |
| `StoryViewModel` | 912 | 739 | −173 | ring+privacy+replies/vanish/ephemeral+reactions+preload; strings 8 locales |
| `StoryGestureCoordinator` | 180 | 186 | +6 | intents+scopes+chrome+deck/hold/drag/tap; fix deck Elvis |

> Actualizar esta tabla al marcar cada `[x]`. Δ negativo = Kotlin más corto (normal: menos boilerplate SwiftUI/UIKit).
> **Formato obligatorio en inventario:** `[x] 123↔456` (nunca solo `[x]`).
> **También obligatorio:** actualizar conteos en **Paridad (actualizar aquí)** (`[x]`/`[~]`/`[ ]` + %).

### Post-paridad (aplazado — al final de todo)

Trabajo de plataforma **después** de cerrar el inventario `[x]`. No bloquea paridad iOS↔Android.

| Ítem | Estado | Notas |
|------|--------|-------|
| **Room ↔ SwiftData** | `[ ]` aplazado | Migrar `LocalPersistenceService` + `MessagePersistenceStore` (+ modelos `Cached*`) de prefs/JSON → **Room** (contraparte Android de SwiftData). Mantener la misma fachada pública LPS. Incluye esquema ≡ entidades SwiftData, migración de datos existentes y rewrite interno LPS/MPS. `ChatCacheStore` (media en disco) y `StorySeenStateService` se reevaluán al migrar. |
| **System Back (Android)** | `[ ]` aplazado | Solo Android (iOS no tiene). **No cablear `BackHandler` durante el port.** En ajustes finos al final: Atrás = ir a la view anterior / dismiss de la capa activa (misma idea que Close/Cancel/chevron). Definir mapeo fino entonces. |
| **CaptionAndDetailsView fondo** | decisión | iOS usa transparencias/blur; **Android = canvas sólido** dark `#0B1215` / light `#FAF9F6` (AdaptiveColors). No portar blur/material. |
| **Creator fondos blur** | decisión | Mismo criterio en CreatorScreens: **Android sin blur de imagen/material de fondo**. LocationPicker = AdaptiveColors sólido; MediaEditing = negro sólido (iOS sí blur). |
| **iOS `.sheet` → ModalBottomSheet** | decisión | Paridad de presentación: SwiftUI `.sheet` / detents → Android `ModalBottomSheet` (`MomentsModalSheet`). No full-screen replace. |
| **Material 3 Foundations** | referencia (ajustes finos) | Arquitectura / cimientos UI Android: [m3.material.io/foundations](https://m3.material.io/foundations). Usar en pulido post-paridad; durante el port sigue ganando iOS. Regla: `.cursor/rules/android-material3-foundations.mdc`. |
| **Zoom SharedTransition** | `[ ]` aplazado | Al final: auditar **todos** los zoom source/destination de iOS (perfil, momento, highlight, view-once, etc. — `*ZoomSource` / `*ZoomDestination` / `matchedTransitionSource` / `.navigationTransition(.zoom)` y variantes) y cablear el equivalente Android (`MomentsSharedTransitionLayout` + `sharedBounds` / hosts). Hoy: API base + algunos hosts; faltan call sites y orígenes. |

### Por tamaño

| Rango líneas | Archivos |
|--------------|--------:|
| ≤50 | 67 |
| 51–150 | 143 |
| 151–500 | 220 |
| 501–1000 | 95 |
| 1001–2000 | 47 |
| >2000 | 16 |


### Audit manual (2026-07-25)

Revisión 1:1 por el agente principal (no subagentes) del lote Components reciente:

| Archivo | Veredicto | Motivo |
|---------|-----------|--------|
| `MomentRowButton` | `[x]` | OK tras fix menú (padding en background) |
| `SkeletonShimmer` | `[x]` | OK |
| `StoryViewerSkeletonView` | `[x]` | OK |
| `UserRowSkeletonView` | `[x]` | OK |
| `OfflineBanner` | `[x]` | OK |
| `VerifiedBadge` | `[x]` | OK (+ helpers StoryModels) |
| `MomentRefresh` | `[x]` | OK tras fix overlay host |
| `MomentCaptionView` / `MomentHashtagText` | `[x]` | OK a nivel muestras |
| `RefreshControl` | `[x]` | OK: pull midY>50 + spinner; overload isRefreshing |
| `MomentRailComponents` | `[x]` | OK: ModernActionButtons real + ModernFollowButton (ya no fachada) |
| `AudienceModels` | `[x]` | OK: enum+list+fromAudienceValue+CaptionAudienceSetting |
| `AudienceSelectionView` | `[x]` | OK: AnimatedContent flow, MemberPicker footer/sheet, strings, load-more chevron |
| `CustomAudienceManagementViews` | `[x]` | Reescrito desde Swift (no KT previo); UserSelectionCard + listener Firestore |

## Leyenda

- **Líneas**: conteo actual del `.swift`
- **Android**: ruta Kotlin si ya existe un archivo con el mismo nombre base
- **Paridad**: estado del cierre 1:1 vs Swift (no solo “existe el .kt”)
  - `[ ]` pendiente
  - `[~]` portado / en revisión
  - `[x]` paridad cerrada (datos, estados, errores, UI/UX)
- Al marcar `[x]`, **obligatorio** el formato `[x] 123↔456` en la columna Paridad (iOS↔Android líneas). Nunca solo `[x]`. Actualizar también la tabla **Cerrados** y los conteos de **Paridad (actualizar aquí)**.
- Archivos marcados con ⚠️ superan 1000 líneas → portar obligatoriamente por secciones MARK
- Si **no hay MARK**: portar por `enum` / `func` / `struct` / `@Composable` (~50–150 líneas por trozo), mismo archivo Kotlin
- **🚫** = fuera de alcance en Android (ver abajo)
- **🔗 Dependencias faltantes** → escribir la llamada igual que iOS + stub honesto; reconciliar al portar el callee (ver sección abajo)
- **🌐 Strings** → todo texto user-facing en los **8 idiomas**, portado desde los `*.lproj/Localizable.strings` de iOS (nunca solo EN/ES)

---

## Alcance del port

### ✅ Compartido con iOS (mismo backend)

| Área | Notas |
|------|--------|
| **Cloud Functions** | Mismas funciones, misma región (`europe-southwest1`), mismo contrato HTTP: `POST https://{region}-{projectId}.cloudfunctions.net/{nombre}` + `Authorization: Bearer <idToken>`. |
| **Firestore / Storage / FCM** | Mismas colecciones, campos y rutas que iOS (iOS = fuente de verdad). |
| **Firebase Auth** | Email/contraseña y **Google Sign-In** en Android. |

### 🚫 Fuera de alcance en Android

| Exclusión | Archivos iOS típicos | En Android |
|-----------|---------------------|------------|
| **Sign in with Apple** | `AuthService.swift` (Apple), `LoginView.swift`, `SocialProfileCompletionView.swift` | N/A — usar Google Sign-In |
| **Passkeys** | `PasskeyService.swift` | 🚫 |
| **Temas de perfil** | `ProfileTheme.swift`, `EnhancedProfileBackground.swift`, `ProfileThemeSelector.swift`, `ProfileThemeDemo.swift` | 🚫 |
| **Suscripciones / StoreKit** | `UserBadge.swift` (`PlusSubscription`, `SupporterLevel`) | 🚫 |
| **Chapas / badges de pago** | `UserBadge.swift`, chapas en perfil | 🚫 — no comprar ni mostrar |
| **Live Activities / ActivityKit** | `*LiveActivity.swift`, `*ActivityAttributes.swift`, `PauseIncognitoIntent.swift` | 🚫 — en uploads: notificación ongoing |
| **Badge verificado** | `isVerified` en `User.swift`, `VerifiedBadge` en UI | **Sí portar** |

### ⚠️ Reglas al encontrar código excluido en un archivo mixto

1. Portar solo la parte core fiel a iOS.
2. Omitir la UI de temas/chapas/suscripciones.
3. **Seguir leyendo** campos de Firestore si existen (`isVerified`, etc.) pero no renderizar chapas de pago.
4. No inventar APIs alternativas.

### 🔗 Dependencias aún no portadas — escribir igual y reconciliar después

Si en el Swift se llama a una función/servicio/tipo que **aún no existe** (o está incompleto) en Android:

1. **En el call site** — escribe la llamada **igual que en iOS**: mismo nombre, mismos parámetros, mismo orden, mismo contrato.
2. **Si falta el callee** — stub **honesto** con el nombre iOS (`// TODO: port Foo.swift` / `fun foo(...) { /* stub */ }`). No inventes una API alternativa “más androide”.
3. **No saltes la llamada** ni la sustituyas por otra cosa (Toast, otra colección Firestore, helper distinto…).
4. Más adelante, al portar el archivo del callee, el stub se rellena y **se reconcilia** solo.

```
Swift:  HapticManager.shared.notification(.success)
Kotlin: HapticManager.shared.success()   // aunque aún sea parcial/no-op

Swift:  FirestoreService().fetchMoment(...)
Kotlin: FirestoreService().fetchMoment(...)  // aunque fetchMoment esté incompleto
```

**Prohibido:** omitir la llamada “porque aún no compila / no está” o inventar un camino distinto. Compila con stub; no divergas del flujo iOS.

### 🌐 Strings / i18n — los 8 idiomas, igual que iOS

Todo texto **user-facing** que aparezca en el Swift vía `NSLocalizedString` / `String(localized:)` / claves de `Localizable.strings` se porta a Android en **los 8** `values-*/strings.xml`. Nunca dejar solo EN (ni EN+ES).

| Android `res/` | iOS `*.lproj` |
|----------------|---------------|
| `values/` | `en.lproj` |
| `values-es/` | `es.lproj` |
| `values-ca/` | `ca.lproj` |
| `values-de/` | `de.lproj` |
| `values-fr/` | `fr.lproj` |
| `values-it/` | `it.lproj` |
| `values-b+pt+BR/` | `pt-BR.lproj` |
| `values-b+pt+PT/` | `pt-PT.lproj` |

Reglas:

1. **Misma clave iOS → nombre Android** estable (`banner.verb.foo` → `banner_verb_foo`). Si ya existe en `strings.xml`, reutilizarla.
2. **Copiar el valor de cada locale** desde el `.lproj` correspondiente; no traducir a ojo ni inventar.
3. Si en algún locale iOS aún está en inglés (p. ej. fr sin traducir), **igual**: copiar lo que diga iOS, no “mejorarlo”.
4. **Prohibido** hardcodear strings en Kotlin (`Text("Reply")`). Siempre `stringResource(R.string.…)` / `context.getString(…)`.
5. Al cerrar un archivo `[x]`, las claves nuevas de ese archivo deben estar en los **8** XML.

### Auth Android vs iOS

| iOS | Android |
|-----|---------|
| Apple Sign In | Google Sign-In |
| Passkey | — (email + Google) |
| Email / contraseña | Email / contraseña |

**Bug anotado (2026-07-26) — registro social sin perfil**

En iOS, tras **Sign in with Apple** (registro nuevo) el flujo sigue por el onboarding de perfil en la misma app (`SocialProfileCompletionView` / equivalente): username, intereses, etc. (**sin** pedir email otra vez).

En Android, un registro vía **Google** (el substituto de Apple) **se salta ese paso**: el usuario queda en **Firebase Auth** pero **sin** documento `users/{uid}` ni `username`. Entra a la app “vacío”.

- No arreglar aquí a ciegas: al llegar a las views de login/onboarding (`LoginView`, `SocialProfileCompletionView`, gates de `AuthService` / `IncompleteProfile`), forzar el mismo flujo iOS antes de entrar al feed.
- Referencia iOS: `SocialProfileCompletionView.swift`, resolución `IncompleteProfile` en `AuthService`.
- Checklist: `SocialProfileCompletionView` → portar al llegar a login (Google; sin Apple nativo).

---

## Índice por área

- [Views](#views) — 421 archivos, 198,629 líneas · paridad `8/415`
- [Services](#services) — 69 archivos, 26,913 líneas · paridad `0/67`
- [Models](#models) — 21 archivos, 7,653 líneas · paridad `0/20`
- [Notifications](#notifications) — 24 archivos, 6,239 líneas · paridad `0/24`
- [Reportes](#reportes) — 7 archivos, 3,539 líneas · paridad `0/7`
- [ad](#ad) — 4 archivos, 2,621 líneas · paridad `0/4`
- [Moderation](#moderation) — 2 archivos, 2,484 líneas · paridad `0/2`
- [Coordinators](#coordinators) — 5 archivos, 1,479 líneas · paridad `0/5`
- [GlowsyWidgetExtension](#glowsywidgetextension) — 7 archivos, 1,315 líneas · paridad `0/7`
- [Utilities](#utilities) — 11 archivos, 1,072 líneas · paridad `0/11`
- [MomentsNotificationService](#momentsnotificationservice) — 2 archivos, 956 líneas · paridad `0/2`
- [Extensions](#extensions) — 5 archivos, 678 líneas · paridad `0/5`
- [Shared](#shared) — 5 archivos, 380 líneas · paridad `0/5`
- [ViewModels](#viewmodels) — 1 archivos, 318 líneas · paridad `0/1`
- [MomentsApp.swift](#momentsapp.swift) — 1 archivos, 211 líneas · paridad `0/1`
- [Activities](#activities) — 3 archivos, 124 líneas · paridad `0/3`

---

## Views

*421 archivos · 198,629 líneas*

| Líneas | Archivo iOS | Android (.kt) | Paridad |
|-------:|-------------|---------------|:-------:|
| 87 | `Moments/Moments/Views/Components/AnimatedStickerView.swift` | `views/components/AnimatedStickerView.kt` | [x] 87↔71 |
| 74 | `Moments/Moments/Views/Components/AudienceIconView.swift` | `views/components/AudienceIconView.kt` | [x] 74↔110 |
| 46 | `Moments/Moments/Views/Components/CommentRowSkeletonView.swift` | `views/components/CommentRowSkeletonView.kt` | [x] 46↔85 |
| 75 | `Moments/Moments/Views/Components/EchoesIconView.swift` | `views/components/EchoesIconView.kt` | [x] 75↔107 |
| 87 | `Moments/Moments/Views/Components/HiddenLayers/HiddenLayerLayout.swift` | `views/components/hiddenlayers/HiddenLayerLayout.kt` | [x] 87↔93 |
| 405 | `Moments/Moments/Views/Components/InAppBannerView.swift` | `views/components/InAppBannerView.kt` | [x] 405↔535 |
| 148 | `Moments/Moments/Views/Components/InAppMessageQuickReplyPanel.swift` | `views/components/InAppMessageQuickReplyPanel.kt` | [x] 148↔270 |
| 64 | `Moments/Moments/Views/Components/IntelligentGlow.swift` | `views/components/IntelligentGlow.kt` | [x] 64↔165 |
| 2012 ⚠️ | `Moments/Moments/Views/Components/InteractiveStickerSharedViews.swift` | `views/components/InteractiveStickerSharedViews.kt` | [x] 2012↔2417 |
| 60 | `Moments/Moments/Views/Components/LiveUsernameText.swift` | `views/components/LiveUsernameText.kt` | [x] 60↔87 |
| 50 | `Moments/Moments/Views/Components/LocationMomentCardSkeletonView.swift` | `views/components/LocationMomentCardSkeletonView.kt` | [x] 50↔88 |
| 408 | `Moments/Moments/Views/Components/MomentCaptionView.swift` | `views/components/MomentCaptionView.kt` | [x] 408↔598 |
| 205 | `Moments/Moments/Views/Components/MomentHashtagText.swift` | `views/components/MomentHashtagText.kt` | [x] 205↔97 |
| 295 | `Moments/Moments/Views/Components/MomentRailComponents.swift` | `views/components/MomentRailComponents.kt` | [x] 295↔300 |
| 191 | `Moments/Moments/Views/Components/MomentRefresh.swift` | `views/components/MomentRefresh.kt` | [x] 191↔201 |
| 81 | `Moments/Moments/Views/Components/MomentRowButton.swift` | `views/components/MomentRowButton.kt` | [x] 81↔90 |
| 202 | `Moments/Moments/Views/Components/OfflineBanner.swift` | `views/components/OfflineBanner.kt` | [x] 202↔206 |
| 53 | `Moments/Moments/Views/Components/RefreshControl.swift` | `views/components/RefreshControl.kt` | [x] 53↔127 |
| 36 | `Moments/Moments/Views/Components/SkeletonShimmer.swift` | `views/components/SkeletonShimmer.kt` | [x] 36↔28 |
| 46 | `Moments/Moments/Views/Components/StoryViewerSkeletonView.swift` | `views/components/StoryViewerSkeletonView.kt` | [x] 46↔42 |
| 47 | `Moments/Moments/Views/Components/UserRowSkeletonView.swift` | `views/components/UserRowSkeletonView.kt` | [x] 47↔45 |
| 138 | `Moments/Moments/Views/Components/VerifiedBadge.swift` | `views/components/VerifiedBadge.kt` | [x] 138↔134 |
| 162 | `Moments/Moments/Views/Creator/AudienceSelector/AudienceModels.swift` | `views/creator/audienceselector/AudienceModels.kt` | [x] 162↔183 |
| 372 | `Moments/Moments/Views/Creator/AudienceSelector/AudienceSelectionRows.swift` | `views/creator/audienceselector/AudienceSelectionRows.kt` | [x] 372↔506 |
| 2170 ⚠️ | `Moments/Moments/Views/Creator/AudienceSelector/AudienceSelectionView.swift` | `views/creator/audienceselector/AudienceSelectionView.kt` | [x] 2170↔1723 |
| 563 | `Moments/Moments/Views/Creator/AudienceSelector/CustomAudienceManagementViews.swift` | `views/creator/audienceselector/CustomAudienceManagementViews.kt` | [x] 563↔722 |
| 152 | `Moments/Moments/Views/Creator/AudienceSelector/CustomListSelectorView.swift` | `views/creator/audienceselector/CustomListSelectorView.kt` | [x] 152↔249 |
| 1481 ⚠️ | `Moments/Moments/Views/Creator/BackgroundMomentUploadService.swift` | `views/creator/BackgroundMomentUploadService.kt` | [x] 1481↔1154 |
| 2419 ⚠️ | `Moments/Moments/Views/Creator/BackgroundStoryUploadService.swift` | `views/creator/BackgroundStoryUploadService.kt` | [x] 2419↔1504 · Live Activity 🚫 · UIKit layout helpers N/A |
| 345 | `Moments/Moments/Views/Creator/CameraKit/CameraKitSpike.swift` | `views/creator/camerakit/CameraKitSpike.kt` | [x] 345↔210 · contrato+stubs; SDK Snap 🚫 (flag off = iOS) |
| 275 | `Moments/Moments/Views/Creator/CameraKit/LensReel.swift` | `views/creator/camerakit/LensReel.kt` | [x] 275↔179 · snap+α/scale+shutter; lentes vía flag |
| 347 | `Moments/Moments/Views/Creator/ChainConfigurationView.swift` | `views/creator/ChainConfigurationView.kt` | [x] 347↔399 · strings 8 locales + flow AnimatedContent |
| 568 | `Moments/Moments/Views/Creator/ChainContinuationSelectorView.swift` | `views/creator/ChainContinuationSelectorView.kt` | [x] 568↔664 · nested create/edit/manage + loadUsers |
| 88 | `Moments/Moments/Views/Creator/Components/CaptureButton.swift` | `views/creator/components/CaptureButton.kt` | [x] 88↔119 |
| 535 | `Moments/Moments/Views/Creator/Components/EditableImageView.swift` | `views/creator/components/EditableImageView.kt` | [x] 535↔494 |
| 49 | `Moments/Moments/Views/Creator/Components/StickerDetailPalette.swift` | `views/creator/components/StickerDetailPalette.kt` | [x] 49↔27 |
| 315 | `Moments/Moments/Views/Creator/Components/StickerGiphyViews.swift` | `views/creator/components/StickerGiphyViews.kt` | [x] 315↔371 |
| 1348 ⚠️ | `Moments/Moments/Views/Creator/Components/StickerInputViews.swift` | `views/creator/components/StickerInputViews.kt` | [x] 1348↔1177 · catálogo=insertInstant; detail=Mention/Link |
| 670 | `Moments/Moments/Views/Creator/Components/StickerLocationInputView.swift` | `views/creator/components/StickerLocationInputView.kt` | [x] 670↔724 |
| 397 | `Moments/Moments/Views/Creator/Components/StickerMediaInputs.swift` | `views/creator/components/StickerMediaInputs.kt` | [x] 397↔538 |
| 343 | `Moments/Moments/Views/Creator/Components/StickerPickerGeneratedStickers.swift` | `views/creator/components/StickerPickerGeneratedStickers.kt` | [x] 343↔153 |
| 152 | `Moments/Moments/Views/Creator/Components/StickerPickerLayout.swift` | `views/creator/components/StickerPickerLayout.kt` | [x] 152↔177 |
| 198 | `Moments/Moments/Views/Creator/Components/StickerPickerSupportExtensions.swift` | `views/creator/components/StickerPickerSupportExtensions.kt` | [x] 198↔217 |
| 42 | `Moments/Moments/Views/Creator/Components/StoryBackgroundPresets.swift` | `views/creator/components/StoryBackgroundPresets.kt` | [x] 42↔44 |
| 130 | `Moments/Moments/Views/Creator/Components/StoryColorPickerView.swift` | `views/creator/components/StoryColorPickerView.kt` | [x] 130↔241 |
| 100 | `Moments/Moments/Views/Creator/Components/StoryDominantColorsExtractor.swift` | `views/creator/components/StoryDominantColorsExtractor.kt` | [x] 100↔82 |
| 914 | `Moments/Moments/Views/Creator/Components/StoryDrawingEditorOverlay.swift` | `views/creator/components/StoryDrawingEditorOverlay.kt` | [x] 914↔911 |
| 111 | `Moments/Moments/Views/Creator/Components/StoryEditingControls.swift` | `views/creator/components/StoryEditingControls.kt` | [x] 111↔142 |
| 459 | `Moments/Moments/Views/Creator/Components/StoryEditorTextTypes.swift` | `views/creator/components/StoryEditorTextTypes.kt` | [x] 459↔358 |
| 96 | `Moments/Moments/Views/Creator/Components/StoryFilterSelectorView.swift` | `views/creator/components/StoryFilterSelectorView.kt` | [x] 96↔166 |
| 76 | `Moments/Moments/Views/Creator/Components/StoryFontRegistry.swift` | `views/creator/components/StoryFontRegistry.kt` | [x] 76↔91 |
| 206 | `Moments/Moments/Views/Creator/Components/StoryTextAttributesBuilder.swift` | `views/creator/components/StoryTextAttributesBuilder.kt` | [x] 206↔298 |
| 598 | `Moments/Moments/Views/Creator/Components/StoryTextEditorChrome.swift` | `views/creator/components/StoryTextEditorChrome.kt` | [x] 598↔816 |
| 89 | `Moments/Moments/Views/Creator/Components/StoryTextGradientSettings.swift` | `views/creator/components/StoryTextGradientSettings.kt` | [x] 89↔60 |
| 243 | `Moments/Moments/Views/Creator/Components/StoryTextMotionEngine.swift` | `views/creator/components/StoryTextMotionEngine.kt` | [x] 243↔211 |
| 1109 ⚠️ | `Moments/Moments/Views/Creator/Components/StoryTextOverlayLabel.swift` | `views/creator/components/StoryTextOverlayLabel.kt` | [x] 1109↔673 |
| 427 | `Moments/Moments/Views/Creator/Components/StoryTextOverlayMetadata.swift` | `views/creator/components/StoryTextOverlayMetadata.kt` | [x] 427↔347 |
| 99 | `Moments/Moments/Views/Creator/Components/StoryTextVisualRenderer.swift` | `views/creator/components/StoryTextVisualRenderer.kt` | [x] 99↔112 |
| 240 | `Moments/Moments/Views/Creator/Components/StoryVideoPlayerView.swift` | `views/creator/components/StoryVideoPlayerView.kt` | [x] 240↔236 |
| 204 | `Moments/Moments/Views/Creator/CreatorScreens/AlbumPickerView.swift` | `views/creator/creatorscreens/AlbumPickerView.kt` | [x] 204↔237 · canvas sólido AdaptiveColors (sin material) |
| 845 | `Moments/Moments/Views/Creator/CreatorScreens/CaptionAndDetailsView.swift` | `views/creator/creatorscreens/CaptionAndDetailsView.kt` | [x] 845↔859 · canvas sólido AdaptiveColors (sin blur) |
| 443 | `Moments/Moments/Views/Creator/CreatorScreens/ContentTypeSelectionView.swift` | `views/creator/creatorscreens/ContentTypeSelectionView.kt` | [x] 443↔470 |
| 66 | `Moments/Moments/Views/Creator/CreatorScreens/FilterOption.swift` | `views/creator/creatorscreens/FilterOption.kt` | [x] 66↔115 |
| 570 | `Moments/Moments/Views/Creator/CreatorScreens/LocationPickerView.swift` | `views/creator/creatorscreens/LocationPickerView.kt` | [x] 570↔726 · mapa+gate+nearby; Localizable queries/categorías |
| 401 | `Moments/Moments/Views/Creator/CreatorScreens/MediaEditingView.swift` | `views/creator/creatorscreens/MediaEditingView.kt` | [x] 401↔546 · crop sheet; ratio recomendado; fondo sólido |
| 103 | `Moments/Moments/Views/Creator/CreatorScreens/MediaGridCell.swift` | `views/creator/creatorscreens/MediaGridCell.kt` | [x] 103↔179 |
| 898 | `Moments/Moments/Views/Creator/CreatorScreens/MediaSelectionView.swift` | `views/creator/creatorscreens/MediaSelectionView.kt` | [x] 898↔716 · gate+camera boundary; album sheet; preview sólido |
| 1199 ⚠️ | `Moments/Moments/Views/Creator/CreatorScreens/StickerOverlayView.swift` | `views/creator/creatorscreens/StickerOverlayView.kt` | [x] 1199↔493 · geom+frame edit+selfie; render tipado en StoryStickerChip |
| 535 | `Moments/Moments/Views/Creator/CreatorScreens/StoryCameraView.swift` | `views/creator/creatorscreens/StoryCameraView.kt` | [x] 535↔653 · captureRect dp; shutter≠galería/flip; Aa; lenses stub |
| 883 | `Moments/Moments/Views/Creator/CreatorScreens/StoryOverlaysView.swift` | `views/creator/creatorscreens/StoryOverlaysView.kt` | [x] 883↔~450 · toast+cycle+reveal badge+polaroid+drawing trash; orq. en storyeditor |
| 681 | `Moments/Moments/Views/Creator/CreatorScreens/StoryTextEditor.swift` | `views/creator/creatorscreens/StoryTextEditor.kt` | [x] 681↔~460 · gradient+IME lift+Done capsule+eyedropper; UIKit legacy N/A |
| 224 | `Moments/Moments/Views/Creator/CreatorScreens/UserSearchView.swift` | `views/creator/creatorscreens/UserSearchView.kt` | [x] 224↔~280 · Localizable; search bar; chips; searching row |
| 368 | `Moments/Moments/Views/Creator/CreatorSharedModels.swift` | `views/creator/CreatorSharedModels.kt` | [x] 368↔314 · media+layout+GlowSharePill+limits; blur view API (canvas sólido en screens) |
| 72 | `Moments/Moments/Views/Creator/CreatorUIKit/BackgroundCameraView.swift` | `views/creator/creatoruikit/BackgroundCameraView.kt` | [x] 72↔115 · back preview FILL; StopBackgroundCameraSession; fondo negro |
| 77 | `Moments/Moments/Views/Creator/CreatorUIKit/CameraCapture.swift` | `views/creator/creatoruikit/CameraCapture.kt` | [x] 77↔276 · TakePicture/CaptureVideo; 60s+quality; dismiss cancel; auto 1 tipo |
| 739 | `Moments/Moments/Views/Creator/CreatorUIKit/CameraPreviewView.swift` | `views/creator/creatoruikit/CameraPreviewView.kt` | [x] 739↔254 · ViewPort crop; quality; zoom 0.1; Center Stage stub false |
| 110 | `Moments/Moments/Views/Creator/CreatorUIKit/CreatorCaptureGeometry.swift` | `views/creator/creatoruikit/CreatorCaptureGeometry.kt` | [x] 110↔91 · insets dp≡pt; density en captureRect |
| 18 | `Moments/Moments/Views/Creator/CreatorUIKit/CreatorControls.swift` | `views/creator/creatoruikit/CreatorControls.kt` | [x] 18↔47 · ToolIconButton chrome+stroke+haptic+18dp |
| 11 | `Moments/Moments/Views/Creator/CreatorUIKit/CreatorUIImageExtensions.swift` | `views/creator/creatoruikit/CreatorUIImageExtensions.kt` | [x] 11↔63 · creatorNormalizedUp + EXIF Uri |
| 99 | `Moments/Moments/Views/Creator/CreatorUIKit/CropViewWrapper.swift` | `views/creator/creatoruikit/CropViewWrapper.kt` | [x] 99↔377 · Fit+pan crop; free cycle preset; rotate/reset; default lock |
| 404 | `Moments/Moments/Views/Creator/CreatorUIKit/DrawingView.swift` | `views/creator/creatoruikit/DrawingView.kt` | [x] 404↔58 · fachada StoryDrawingEditorOverlay; Crop+dim; Done→dismiss; 0 call sites iOS |
| 377 | `Moments/Moments/Views/Creator/CreatorUIKit/StoryGalleryPicker.swift` | `views/creator/creatoruikit/StoryGalleryPicker.kt` | [x] 377↔370 · split/trim/tooLong Localizable; 9:16; constants servicio |
| 115 | `Moments/Moments/Views/Creator/CreatorUIKit/StoryMediaPicker.swift` | `views/creator/creatoruikit/StoryMediaPicker.kt` | [x] 115↔~120 · PickVisualMedia 1 ítem; content:// directo; cache solo si export falla |
| 1227 ⚠️ | `Moments/Moments/Views/Creator/CreatorView.swift` | `views/creator/CreatorView.kt` + `RevealStickerEditor.kt` | [x] orquestador + Reveal*; dead ModernSelection/Guide (0 call sites) |
| 2170 ⚠️ | `Moments/Moments/Views/Creator/HiddenLayersEditorView.swift` | `views/creator/HiddenLayersEditorView.kt` | [x] Paridad editor; audioPreviewCard waveform no portado (casi dead en dock iOS) |
| 418 | `Moments/Moments/Views/Creator/PhotoTagSelectionView.swift` | `views/creator/PhotoTagSelectionView.kt` | [x] |
| 374 | `Moments/Moments/Views/Creator/StickerEmojiPalettePicker.swift` | `views/creator/StickerEmojiPalettePicker.kt` | [x] catalog ICU+extras+skin; grid 7; tray long-press; cableado ModernEmojiSliderInputView |
| 128 | `Moments/Moments/Views/Creator/StoryVideoProcessingService.swift` | `views/creator/StoryVideoProcessingService.kt` | [x] duration/export/split/thumb; 720p; errors Localizable×8; thumbnailUri |
| 558 | `Moments/Moments/Views/Creator/StoryVideoTrimEditorView.swift` | `views/creator/StoryVideoTrimEditorView.kt` | [x] Nitidez light/dark; handles+playhead; strings×8; timeline 10f |
| 1925 ⚠️ | `Moments/Moments/Views/Creator/VideoEditor.swift` | `views/creator/VideoEditor.kt` | [x] SocialVideoEditorView: export format-aware + metadatos; Nitidez; timeline handles; glass pickers; cover FS; trim/speed/vol preview-only (=iOS) |
| 2081 ⚠️ | `Moments/Moments/Views/Creator/stickerview.swift` | `views/creator/stickerview.kt` | [x] picker completo; cards Compose en storyeditor (=bake UIKit); press+GIF4+pills accent+limits |
| 3248 ⚠️ | `Moments/Moments/Views/Creator/storyeditor.swift` | `views/creator/storyeditor.kt` | [x] 3248↔3705 · bake imagen+vídeo paleta, chat/cadena, EmojiPicker, Done/palette sticker, publish dismiss |
| 362 | `Moments/Moments/Views/Echoes/EchoHistoryView.swift` | `views/echoes/EchoHistoryView.kt` | [x] 362↔399 · fullscreen EchoViewerUI + info sheet |
| 232 | `Moments/Moments/Views/Echoes/EchoInvitationView.swift` | `views/echoes/EchoInvitationView.kt` | [x] 232↔324 · listener echoes/ + accept/decline |
| 736 | `Moments/Moments/Views/Echoes/EchoViewerUI.swift` | `views/echoes/EchoViewerUI.kt` | [x] 736↔853 · overlays glass + leave menu + mapa FS |
| 553 | `Moments/Moments/Views/Explore/ExploreGridLayout.swift` | `views/explore/ExploreGridLayout.kt` | [x] 553↔427 · bento mosaic + thumbnail chrome |
| 479 | `Moments/Moments/Views/Explore/ExploreMomentDetailView.swift` | `views/explore/ExploreMomentDetailView.kt` | [x] 479↔542 · feed scroll + dismiss + overlays |
| 402 | `Moments/Moments/Views/Explore/ExploreSections/ExploreResultsSection.swift` | `views/explore/exploresections/ExploreResultsSection.kt` | [x] 402↔689 · SmartSearch + MiniUser + Recent + SearchResultCard |
| 622 | `Moments/Moments/Views/Explore/ExploreSections/ExploreSuggestionsSection.swift` | `views/explore/exploresections/ExploreSuggestionsSection.kt` | [x] 622↔659 · cards bg+blur, loading/error, SearchBar, FollowButton |
| 457 | `Moments/Moments/Views/Explore/ExploreView.swift` | `views/explore/ExploreView.kt` | [x] 457↔~560 · profile UserProfileView + MomentZoomDetailDestination explorer/single |
| 919 | `Moments/Moments/Views/Explore/ExploreViewModel.swift` | `views/explore/ExploreViewModel.kt` | [x] 919↔509 · follow/request+pending+search detect; connections+notifications |
| 262 | `Moments/Moments/Views/Explore/ModernExploreDetailHeader.swift` | `views/explore/ModernExploreDetailHeader.kt` | [x] 262↔~310 · glass pill + follow/unfollow + live username |
| 1547 ⚠️ | `Moments/Moments/Views/Explore/MomentDetailView.swift` | — | [N/A] dead code iOS · 0 call sites · usar ExploreMomentDetailView |
| 614 | `Moments/Moments/Views/Explore/SuggestedUsersView.swift` | `views/explore/SuggestedUsersView.kt` | [x] 614↔360+186 · VM real + rows + infinite scroll + refresh |
| 138 | `Moments/Moments/Views/Feed/Controls/FeedTypeSelector.swift` | `views/feed/controls/FeedTypeSelector.kt` | [x] 138↔~130 · FloatingGlass wrap-content + pill (no full-width) |
| 467 | `Moments/Moments/Views/Feed/Controls/feedchange.swift` | `views/feed/controls/feedchange.kt` | [x] 467↔534 · FeedType+prefs+Expandable/Compact/Segmented/HeaderChip |
| 171 | `Moments/Moments/Views/Feed/Core/FeedNotificationRoutingModifier.swift` | `views/feed/core/FeedNotificationRoutingModifier.kt` | [x] 171↔169 · lifecycle+pendingNav+EventBus; cableado FeedView |
| 215 | `Moments/Moments/Views/Feed/Core/FeedPresentationModifier.swift` | `views/feed/core/FeedPresentationModifier.kt` | [x] 215↔394 · todas destinations reales (notif/msg/stories/comments/explore/map/edit/profile/echo) |
| 21 | `Moments/Moments/Views/Feed/Core/FeedRoutes.swift` | `views/feed/core/FeedRoutes.kt` | [x] 21↔28 · Profile/Echo/Story routes |
| 608 | `Moments/Moments/Views/Feed/Core/FeedView.swift` | `views/feed/core/FeedView.kt` | [x] 608↔~820 · load/refresh+messaging+notif primer 20s+prefetch; storyChain UI fuera (=iOS) |
| 1300 ⚠️ | `Moments/Moments/Views/Feed/Core/FeedViewModel.swift` | `views/feed/core/FeedViewModel.kt` | [x] 1300↔1323 · dual cache+backend/legacy+privacy listeners+mute TTL |
| 127 | `Moments/Moments/Views/Feed/Core/ModernEmptyFeedView.swift` | `views/feed/core/ModernEmptyFeedView.kt` | [x] 127↔160 · empty following/forYou + glass CTA → Explore |
| 220 | `Moments/Moments/Views/Feed/Core/Sections/FeedHeaderSection.swift` | `views/feed/core/sections/FeedHeaderSection.kt` | [x] 220↔~370 · re-audit: skeletonRow+loadingTail+YourStory first+loadMore(index) |
| 240 | `Moments/Moments/Views/Feed/Core/Sections/FeedListSection.swift` | `views/feed/core/sections/FeedListSection.kt` | [x] 240↔~290 · re-audit: heights+tagTap+prefetch mediaItems+scrollEdgeChrome |
| 1894 ⚠️ | `Moments/Moments/Views/Feed/Core/Sections/FeedMomentComponents.swift` | `views/feed/core/sections/FeedMomentComponents.kt` (+ carousel/peek + GlobalVideoManager) | [x] buttons+PostCard+detect+MediaItem+CroppedVideo+Expandable+peek · LoadingMore en skeleton |
| 97 | `Moments/Moments/Views/Feed/Core/Sections/FeedMomentDetailRoute.swift` | `views/feed/core/sections/FeedMomentDetailRoute.kt` | [x] 97↔210 · notif→detail+loading/error |
| 113 | `Moments/Moments/Views/Feed/Core/Sections/FeedOverlaysSection.swift` | `views/feed/core/sections/FeedOverlaysSection.kt` | [x] 113↔~170 · re-audit: peek shape+transitions+editedContent |
| 46 | `Moments/Moments/Views/Feed/Core/Sections/FeedPostSkeletonView.swift` | `views/feed/core/sections/FeedPostSkeletonView.kt` | [x] 46↔~180 · skeleton+breathing LoadingMore |
| 508 | `Moments/Moments/Views/Feed/Core/Sections/FeedStoryRingComponents.swift` | `views/feed/core/sections/FeedStoryRingComponents.kt` | [x] 508↔~650 · rings+retry+LiveUsername+overlay anim |
| 172 | `Moments/Moments/Views/Feed/Moments/ClickableHashtagsView.swift` | `views/feed/moments/ClickableHashtagsView.kt` | [x] View+HStack+parse+FeedFlowLayout VStack |
| 1128 ⚠️ | `Moments/Moments/Views/Feed/Moments/HiddenLayersOverlayView.swift` | `views/feed/moments/HiddenLayersOverlayView.kt` | [x] overlay+focus+locked+text/audio/image+discovery+hints |
| 136 | `Moments/Moments/Views/Feed/Moments/MomentCarouselLayoutRules.swift` | `views/feed/moments/MomentCarouselLayoutRules.kt` | [x] rules+indicators+FeedMomentCardLayout+scaledRadius |
| 1077 ⚠️ | `Moments/Moments/Views/Feed/Reactions/MomentReactionButton.swift` | `views/feed/reactions/MomentReactionButton.kt` (+ ReactionsListSheet) | [x] 1077↔1195 · EpicButton+picker+particles+sheet+listener |
| 539 | `Moments/Moments/Views/Feed/Reactions/reacciones.swift` | `views/feed/reactions/reacciones.kt` (+ FirestoreService helpers) | [x] 539↔377 · types+tracker+Modern+Picker; FS helpers |
| 171 | `Moments/Moments/Views/Feed/Sharing/ShareMomentSticker.swift` | `views/feed/sharing/ShareMomentSticker.kt` | [x] 171↔241 · Moment+Bitmaps+renderClean+header/caption/play |
| 644 | `Moments/Moments/Views/Feed/Sharing/StoryShare.swift` | `views/feed/sharing/StoryShare.kt` | [x] 644↔478 · helpers+access+sheet+bubble+preview |
| 1863 ⚠️ | `Moments/Moments/Views/Feed/Sharing/share.swift` | `views/feed/sharing/share.kt` | [x] 1863↔2045 · sheet+picker+AddToStory+SharedDM bubbles |
| 673 | `Moments/Moments/Views/Feed/Stories/FeedStoryRingCoordinator.swift` | `views/feed/stories/FeedStoryRingCoordinator.kt` | [x] 673↔463 · tray+legacy+cache+offline+sort; evaluate→Resolver |
| 116 | `Moments/Moments/Views/Feed/Stories/StoryRingTraySkeleton.swift` | `views/feed/stories/StoryRingTraySkeleton.kt` | [x] 116↔135 · cell+row+tail+avatar atenuado+StorySegmentedRing |
| 241 | `Moments/Moments/Views/Feed/Uploads/FeedUploadProgressRow.swift` | `views/feed/uploads/FeedUploadProgressRow.kt` | [x] 241↔449 · UploadProgressRow+status+bar+retry; compat UploadProgressItem |
| 667 | `Moments/Moments/Views/Feed/Uploads/FloatingMomentUploadOverlay.swift` | `views/feed/uploads/FloatingMomentUploadOverlay.kt` | [x] 667↔798 · orb+panel+rocket/ripple parallel+aura blur; haptics orb/panel |
| 29 | `Moments/Moments/Views/Feed/Uploads/StoryUploadProgressManager.swift` | `views/feed/uploads/StoryUploadProgressManager.kt` | [x] 29↔80 · isUploading+progress API 1:1; UploadStatus/helpers abajo |
| 65 | `Moments/Moments/Views/Feed/Video/LiveVideoTimeLabel.swift` | `views/feed/video/LiveVideoTimeLabel.kt` | [x] 65↔94 · livePlaybackSeconds+standalone/inline; bridge tick→capture |
| 1608 ⚠️ | `Moments/Moments/Views/Feed/Video/Reels.swift` | `views/feed/video/Reels.kt` (+ReelVideoView/Manager) | [x] 1608↔1583 split ReelsViewer+ReelVideoView+Manager+EnhancedBtn |
| 23 | `Moments/Moments/Views/Feed/Video/VideoFeedProgressBar.swift` | `views/feed/video/VideoFeedProgressBar.kt` | [x] 23↔41 · track 0.3 + fill white · height 2 |
| 55 | `Moments/Moments/Views/Feed/Video/VideoPlaybackChromeStyle.swift` | `views/feed/video/VideoPlaybackChromeStyle.kt` | [x] 55↔116 · ActivationMode+ChromeStyle+SocialVideoPausedControls |
| 1144 ⚠️ | `Moments/Moments/Views/Feed/Video/VideoPlayer.swift` | `views/feed/video/VideoPlayer.kt` (941) + `services/video/GlobalVideoManager.kt` (355) | [x] 1144↔1296 · Global+Modern+Manager+Representable+Halo |
| 27 | `Moments/Moments/Views/Feed/Video/VideoPosterOverlay.swift` | `views/feed/video/VideoPosterOverlay.kt` | [x] 27↔73 · poster+fade 0.2+contentScale+cornerRadius |
| 938 | `Moments/Moments/Views/Feed/maps/DiscoverMapView.swift` | `views/feed/maps/DiscoverMapView.kt` | [x] 938↔1165 · mapa+chrome+pins+search región/lugar+sheet+stories stub+zoom · weather stub |
| 1640 ⚠️ | `Moments/Moments/Views/Feed/maps/LocationMomentDetailView.swift` | `LocationMomentDetailView` + `Card` + `Sections` | [x] 1640↔1537 · path activo ModernPostCard+visibility+stories · FeedVM N/A (card self-contained) |
| 107 | `Moments/Moments/Views/Feed/maps/MapAnnotationModels.swift` | `views/feed/maps/MapAnnotationModels.kt` | [x] 107↔90 · MapsLocation+Combined+5 Moment helpers · sin inventos |
| 246 | `Moments/Moments/Views/Feed/maps/MapDiscoverSupport.swift` | `views/feed/maps/MapDiscoverSupport.kt` | [x] 246↔282 · BackendMapStory+VisibilityPolicy+ZoneContext async+formatters |
| 616 | `Moments/Moments/Views/Feed/maps/MapLocationServices.swift` | `views/feed/maps/MapLocationServices.kt` | [x] 616↔557 · LocationUtilities+RegionStore+Search+DisplayFormatter |
| 465 | `Moments/Moments/Views/Feed/maps/MapPlaceBottomSheet.swift` | `views/feed/maps/MapPlaceBottomSheet.kt` | [x] 465↔551 · header/chips/gallery/list/index row + formatters |
| 313 | `Moments/Moments/Views/Feed/maps/MapPlaceClusterEngine.swift` | `views/feed/maps/MapPlaceClusterEngine.kt` | [x] 313↔364 · build/merge/valid/unnamed · jitter fórmula≡iOS (hash≠Swift) |
| 117 | `Moments/Moments/Views/Feed/maps/MapPlaceStoryDeck.swift` | `views/feed/maps/MapPlaceStoryDeck.kt` | [x] 117↔115 · fetchStories + StoryViewerScreen |
| 318 | `Moments/Moments/Views/Feed/maps/MapWeatherEffects.swift` | `views/feed/maps/MapWeatherEffects.kt` | [x] 318↔297 · rain/snow/thunder + overlay color/opacity iOS |
| 1985 ⚠️ | `Moments/Moments/Views/Feed/maps/Maps.swift` | `LocationMapView.kt` + `Support` + `Chrome` | [x] 1985↔1605 · echo/geocode/sheet MomentsModal+userLoc+search CTA · Dialog≠nav |
| 680 | `Moments/Moments/Views/Feed/maps/MapsSections/MapBottomSheetSection.swift` | `views/feed/maps/mapssections/MapBottomSheetSection.kt` | [x] 680↔643 · LocationBottomSheet gallery/list + cells/video/row/unavailable |
| 538 | `Moments/Moments/Views/Feed/maps/MapsSections/MapCanvasSection.swift` | `views/feed/maps/mapssections/MapCanvasSection.kt` | [x] 537↔522 · Mapbox canvas + ModernLocationPin/Gallery/PhotoCard/GalleryView · pins en DiscoverMapView |
| 445 | `Moments/Moments/Views/Feed/maps/WeatherService.swift` | `views/feed/maps/WeatherService.kt` | [x] 445↔217 · OpenWeather Current 2.5 + cache/rate · overlay helpers |
| 364 | `Moments/Moments/Views/Login/AuthUIComponents.swift` | `views/login/AuthUIComponents.kt` | [~] |
| 123 | `Moments/Moments/Views/Login/CreatingProfileView.swift` | `views/login/CreatingProfileView.kt` | [~] |
| 326 | `Moments/Moments/Views/Login/DeactivatedAccountView.swift` | — | [~] |
| 1 | `Moments/Moments/Views/Login/Interestview.swift` | — | 🚫 |
| 475 | `Moments/Moments/Views/Login/LiquidGlassComponents.swift` | — | [~] |
| 1153 ⚠️ | `Moments/Moments/Views/Login/LoginView.swift` | `views/login/LoginView.kt` | [~] |
| 100 | `Moments/Moments/Views/Login/PrivacyPolicyView.swift` | `views/login/PrivacyPolicyView.kt` | [~] |
| 1255 ⚠️ | `Moments/Moments/Views/Login/ProfileOnboardingView.swift` | `views/login/ProfileOnboardingView.kt` | [~] |
| 17 | `Moments/Moments/Views/Login/RegisterView.swift` | `views/login/RegisterView.kt` | [~] |
| 10 | `Moments/Moments/Views/Login/SocialProfileCompletionView.swift` | — | [ ] bug: Google registra Auth sin username/user — portar al llegar a login |
| 103 | `Moments/Moments/Views/Login/SplashScreen.swift` | `views/login/SplashScreen.kt` | [x] 103↔84 · canvas 0B1215/FAF9F6+logo dark/light+shadow AuthColors; reduceMotion; Δ MinimalSplash no en flujo |
| 662 | `Moments/Moments/Views/Login/SuspendedAccount.swift` | — | [~] |
| 395 | `Moments/Moments/Views/Messaging/Attachments/ChatGiphyPickerSheet.swift` | `views/messaging/attachments/ChatGiphyPickerSheet.kt` | [~] · load-more solo último ítem |
| 370 | `Moments/Moments/Views/Messaging/Attachments/ChatLocationSheet.swift` | `views/messaging/attachments/ChatLocationSheet.kt` | [~] · LocationPermissionGate ALWAYS + errors |
| 367 | `Moments/Moments/Views/Messaging/Components/AttachmentIconView.swift` | `views/messaging/components/AttachmentIconView.kt` | [x] 367↔225 · enum+metrics+presets+resolvedSize |
| 146 | `Moments/Moments/Views/Messaging/Components/ChatAdaptiveColors.swift` | `views/messaging/components/ChatAdaptiveColors.kt` | [x] 146↔70 · locals+extension colors; blue=#007AFF |
| 1113 ⚠️ | `Moments/Moments/Views/Messaging/Components/ChatAttachmentSheet.swift` | `views/messaging/components/ChatAttachmentSheet.kt` | [x] 1113↔952 · pickers+menu+photos; fling predicted+PermissionPrimer+popover gap+search glass |
| 145 | `Moments/Moments/Views/Messaging/Components/ChatBuzzEffectViews.swift` | `views/messaging/components/ChatBuzzEffectViews.kt` | [x] 145↔~160 · toast+timeline+shake; icon gradient; shake wire en chat shell |
| 902 | `Moments/Moments/Views/Messaging/Components/ChatChromeViews.swift` | `views/messaging/components/ChatChromeViews.kt` | [~] · typing/FAB reduceMotion wired via MotionPolicy |
| 1293 ⚠️ | `Moments/Moments/Views/Messaging/Components/ChatClusterMediaViews.swift` | `views/messaging/components/ChatClusterMediaViews.kt` | [~] · fan+gallery masonry/select/delete; detail push host no portado |
| 337 | `Moments/Moments/Views/Messaging/Components/ChatEphemeralMessageViews.swift` | `views/messaging/components/ChatEphemeralMessageViews.kt` | [x] 337↔389 · tap/image/resolving/expired; blur+borders; hydrate; markViewed; Spring.toggle |
| 112 | `Moments/Moments/Views/Messaging/Components/ChatFloatingNavigationOverlay.swift` | `views/messaging/components/ChatFloatingNavigationOverlay.kt` | [x] 112↔~180 · resolve+search appear+shadow+AnimatedVisibility |
| 95 | `Moments/Moments/Views/Messaging/Components/ChatGifMessageBubble.swift` | `views/messaging/components/ChatGifMessageBubble.kt` | [x] 95↔117 |
| 801 | `Moments/Moments/Views/Messaging/Components/ChatInputViews.swift` | `views/messaging/components/ChatInputViews.kt` | [x] 801↔706 · vanish+fill sólido floating/locked+held+trim; Δ no aurora/glass iOS |
| 66 | `Moments/Moments/Views/Messaging/Components/ChatKFImageViews.swift` | `views/messaging/components/ChatKFImageViews.kt` | [x] 66↔89 |
| 606 | `Moments/Moments/Views/Messaging/Components/ChatLocationMessageBubble.swift` | `views/messaging/components/ChatLocationMessageBubble.kt` | [~] · bubble+detail+avatar pin+countdown; MK snapshot→GoogleMap |
| 502 | `Moments/Moments/Views/Messaging/Components/ChatMediaViews.swift` | `views/messaging/components/ChatMediaViews.kt` | [x] 502↔565 · image/video+download+players; downsample; drag px; BlurView≈sólido |
| 838 | `Moments/Moments/Views/Messaging/Components/ChatMessageBubbleViews.swift` | `views/messaging/components/ChatMessageBubbleViews.kt` | [x] 838↔669 · row swipe/chrome+AttachBubbleBadges; texto sin double overlay; link preview; Δ≈LPLink |
| 28 | `Moments/Moments/Views/Messaging/Components/ChatMessageForwardSheet.swift` | `views/messaging/components/ChatMessageForwardSheet.kt` | [x] 28↔151 |
| 379 | `Moments/Moments/Views/Messaging/Components/ChatMessageInteractionModifiers.swift` | `views/messaging/components/ChatMessageInteractionModifiers.kt` | [x] 379↔414 · pan scroll-friendly+spring return+haptics+longPress 0.42+wrapContentHeight |
| 1708 ⚠️ | `Moments/Moments/Views/Messaging/Components/ChatMessageListView.swift` | `views/messaging/components/ChatMessageListView.kt` | [~] · contrato apply/normalize/scroll/force/pending/suppress+frames; sin UIKit vanish/heightCache |
| 535 | `Moments/Moments/Views/Messaging/Components/ChatMessageOptionsMenu.swift` | `views/messaging/components/ChatMessageOptionsMenu.kt` | [x] 535↔544 · anchor window→local+safeArea+press chrome+haptic menu+cornerRadius+star.slash≈StarBorder |
| 882 | `Moments/Moments/Views/Messaging/Components/ChatMessageSupportViews.swift` | `views/messaging/components/ChatMessageSupportViews.kt` | [x] 882↔693 · reply/quote/reactions+cutout Clear+star+timestamp; status twin checks; clusterHitTargetInset |
| 781 | `Moments/Moments/Views/Messaging/Components/ChatRecoveryViews.swift` | `views/messaging/components/ChatRecoveryViews.kt` | [x] 781↔800 · gate/create/restore/settings+PIN 48×60+lockout; material→sólido; change PIN MomentsModalSheet; lock gradient+press |
| 35 | `Moments/Moments/Views/Messaging/Components/ChatSearchNavigationBar.swift` | `views/messaging/components/ChatSearchNavigationBar.kt` | [x] 35↔54 |
| 432 | `Moments/Moments/Views/Messaging/Components/ChatSpeechBubbleViews.swift` | `views/messaging/components/ChatSpeechBubbleViews.kt` | [x] 432↔500 · shape+spoilers+markdown inline+links underline+search diacríticos+gutter |
| 46 | `Moments/Moments/Views/Messaging/Components/ChatStickerMessageBubble.swift` | `views/messaging/components/ChatStickerMessageBubble.kt` | [x] 46↔66 |
| 620 | `Moments/Moments/Views/Messaging/Components/ChatVanishModeViews.swift` | `views/messaging/components/ChatVanishModeViews.kt` | [x] 620↔762 · metrics/overlay/notices/timer MomentsModalSheet+inbox; Δ liquidGlass→sólido; medium-only≈medium+large |
| 279 | `Moments/Moments/Views/Messaging/Components/ConversationContextMenu.swift` | `views/messaging/components/ConversationContextMenu.kt` | [x] 279↔355 · cutout Clear+chrome+layout+MomentRowButton.menu; pin.slash≈slash overlay; systemBars insets; row highlight 0.96 |
| 84 | `Moments/Moments/Views/Messaging/Components/MediaProgressRing.swift` | `views/messaging/components/MediaProgressRing.kt` | [x] 84↔58 |
| 38 | `Moments/Moments/Views/Messaging/Components/MessageTypeIconView.swift` | `views/messaging/components/MessageTypeIconView.kt` | [x] 38↔54 |
| 186 | `Moments/Moments/Views/Messaging/Components/MessagingComposerAndStatusViews.swift` | `views/messaging/components/MessagingComposerAndStatusViews.kt` | [~] · composer gradient+campo+send; status MomentsModalSheet medium+large+dividers |
| 287 | `Moments/Moments/Views/Messaging/Components/ViewOnceMessageBubble.swift` | `views/messaging/components/ViewOnceMessageBubble.kt` | [x] 287↔268 · pills+progress; estado desde message; zoom matchedTransition stub |
| 1121 ✅ | `Moments/Moments/Views/Messaging/Components/VoiceNotes.swift` | `views/messaging/components/VoiceNotes.kt` | [x] · recorder/compose/trim+bubble scrub/speed/remaining+cache+proximidad auricular |
| 535 | `Moments/Moments/Views/Messaging/Components/VoiceRecordingGestureViews.swift` | `views/messaging/components/VoiceRecordingGestureViews.kt` | [~] · fases hold/lock/cancel+follow goma+ticks hápticos+chrome; aura dual; VoiceBlob/AuroraMesh morph→brush stub |
| 3520 ⚠️ | `Moments/Moments/Views/Messaging/Core/ChatViewModel.swift` | `views/messaging/core/ChatViewModel.kt` | [x] 3520↔2284 |
| 49 | `Moments/Moments/Views/Messaging/Core/MessageItem.swift` | `views/messaging/core/MessageItem.kt` | [x] 49↔67 |
| 2514 ⚠️ | `Moments/Moments/Views/Messaging/Core/MessageModel.swift` | `views/messaging/core/MessageModel.kt` | [x] 2514↔1327 |
| 856 | `Moments/Moments/Views/Messaging/Core/MessagingViewModel.swift` | `views/messaging/core/MessagingViewModel.kt` | [x] 856↔727 |
| 1845 ⚠️ | `Moments/Moments/Views/Messaging/Media/CameraPickerView.swift` | `views/messaging/media/CameraPickerView.kt` | [~] · huérfano; path chat = ChatCameraView |
| 460 | `Moments/Moments/Views/Messaging/Media/ChatCameraView.swift` | `views/messaging/media/ChatCameraView.kt` | [x] 460↔534 · BackHandler+gallery thumb+recording pill+haptic+aspectRatio |
| 71 | `Moments/Moments/Views/Messaging/Media/ChatMediaOverlayPayload.swift` | `views/messaging/media/ChatMediaOverlayPayload.kt` | [x] 71↔60 |
| 37 | `Moments/Moments/Views/Messaging/Media/ChatMediaSendMode.swift` | `views/messaging/media/ChatMediaSendMode.kt` | [x] 37↔27 |
| 613 | `Moments/Moments/Views/Messaging/Media/ViewOnceImmersiveViewer.swift` | `views/messaging/media/ViewOnceImmersiveViewer.kt` | [x] 613↔669 · canvas+chrome+emoji sheet+consume/replay; FIT_WITH_BLUR; Δ≈blur vídeo muted |
| 151 | `Moments/Moments/Views/Messaging/Models/ChatAttachmentAssets.swift` | `views/messaging/models/ChatAttachmentAssets.kt` | [x] 151↔170 |
| 146 | `Moments/Moments/Views/Messaging/Screens/ArchivedConversationsView.swift` | `views/messaging/screens/ArchivedConversationsView.kt` | [x] · empty+GlassmorphicRow+menu+auto-dismiss |
| 142 | `Moments/Moments/Views/Messaging/Screens/Chat/GlassmorphicChatView+Clustering.swift` | — | [~] |
| 1006 ⚠️ | `Moments/Moments/Views/Messaging/Screens/Chat/GlassmorphicChatView+ComposerAndChrome.swift` | `screens/chat/GlassmorphicChatViewComposerAndChrome.kt` | [x] 1006↔718 · plus/buzz/shake+media FS+report+cluster reply picker+gallery |
| 209 | `Moments/Moments/Views/Messaging/Screens/Chat/GlassmorphicChatView+Lifecycle.swift` | — | [~] · markViewed+openCamera delay+view-once wiring |
| 361 | `Moments/Moments/Views/Messaging/Screens/Chat/GlassmorphicChatView+MessageList.swift` | `screens/chat/GlassmorphicChatViewMessageList.kt` | [~] · rows+CompositionLocal search highlight/activeId |
| 262 | `Moments/Moments/Views/Messaging/Screens/Chat/GlassmorphicChatView+MessageRendering.swift` | — | [~] |
| 450 | `Moments/Moments/Views/Messaging/Screens/Chat/GlassmorphicChatView+Scroll.swift` | — | [~] |
| 107 | `Moments/Moments/Views/Messaging/Screens/Chat/GlassmorphicChatView+Search.swift` | `screens/chat/GlassmorphicChatViewSearch.kt` | [~] · sync matches+clearKeepingMode+canGoUp/Down |
| 251 | `Moments/Moments/Views/Messaging/Screens/Chat/GlassmorphicChatView+Toolbar.swift` | `screens/chat/GlassmorphicChatViewToolbar.kt` | [~] · presence+stories+search chrome; hide when search |
| 88 | `Moments/Moments/Views/Messaging/Screens/Chat/GlassmorphicChatView+ViewModelAudio.swift` | — | [~] |
| 520 | `Moments/Moments/Views/Messaging/Screens/Chat/GlassmorphicChatView+Voice.swift` | `screens/chat/GlassmorphicChatViewVoice.kt` | [~] · wired hold-to-record+lock+floating+send |
| 725 | `Moments/Moments/Views/Messaging/Screens/Chat/GlassmorphicChatView.swift` | `views/messaging/screens/chat/GlassmorphicChatView.kt` | [~] · shell+scroll; search highlight CompositionLocal; pending request ops→MessageRequestService |
| 435 | `Moments/Moments/Views/Messaging/Screens/Chat/MomentsChatViewModel+Media.swift` | — | [~] |
| 2816 ⚠️ | `Moments/Moments/Views/Messaging/Screens/ConversationSettingsView.swift` | `screens/ConversationSettingsView.kt` + `ConversationFullScreenMediaView.kt` | [~] 2816↔~2065 · hydrate policy+poster+thumb; prefs grupos+desc+events; LinkPreview; footer created; FullScreen video play/mute/expand/timeline |
| 254 | `Moments/Moments/Views/Messaging/Screens/MessageRequestsView.swift` | `views/messaging/screens/MessageRequestsView.kt` | [x] · lista+empty+actions+open pending chat |
| 1864 ⚠️ | `Moments/Moments/Views/Messaging/Screens/MessagingView.swift` | `views/messaging/screens/MessagingView.kt` + `GlassmorphicConversationRow.kt` | [x] · toolbar+search+merged list+row+menu+destinations+pending chat |
| 90 | `Moments/Moments/Views/Messaging/Services/ChatAccessCoordinator.swift` | `views/messaging/services/ChatAccessCoordinator.kt` | [x] 90↔125 |
| 39 | `Moments/Moments/Views/Messaging/Services/ChatBuzzProcessedStore.swift` | `views/messaging/services/ChatBuzzProcessedStore.kt` | [x] 39↔120 |
| 51 | `Moments/Moments/Views/Messaging/Services/ChatDraftStore.swift` | `views/messaging/services/ChatDraftStore.kt` | [x] 51↔112 |
| 90 | `Moments/Moments/Views/Messaging/Services/ChatGiphyService.swift` | `views/messaging/services/ChatGiphyService.kt` | [x] 90↔105 |
| 96 | `Moments/Moments/Views/Messaging/Services/ChatKeyboardScrollCoordinator.swift` | `views/messaging/services/ChatKeyboardScrollCoordinator.kt` | [x] 96↔99 |
| 70 | `Moments/Moments/Views/Messaging/Services/ChatNavigationIntentStore.swift` | `views/messaging/services/ChatNavigationIntentStore.kt` | [x] 70↔95 |
| 158 | `Moments/Moments/Views/Messaging/Services/ChatRowHeightEstimator.swift` | `views/messaging/services/ChatRowHeightEstimator.kt` | [x] 158↔170 |
| 80 | `Moments/Moments/Views/Messaging/Services/ChatScrollStateStore.swift` | `views/messaging/services/ChatScrollStateStore.kt` | [x] 80↔81 |
| 93 | `Moments/Moments/Views/Messaging/Services/ChatService+Buzz.swift` | `views/messaging/services/ChatServiceBuzz.kt` | [x] 93↔118 |
| 125 | `Moments/Moments/Views/Messaging/Services/ChatService+ChunkedVideoUpload.swift` | `views/messaging/services/ChatServiceChunkedVideoUpload.kt` | [x] 125↔177 |
| 381 | `Moments/Moments/Views/Messaging/Services/ChatService+EncryptedMediaResolver.swift` | `views/messaging/services/ChatEncryptedMediaResolver.kt` | [x] 381↔352 |
| 178 | `Moments/Moments/Views/Messaging/Services/ChatService+EphemeralCleanup.swift` | `views/messaging/services/ChatServiceEphemeralCleanup.kt` | [x] 178↔170 |
| 181 | `Moments/Moments/Views/Messaging/Services/ChatService+LocalFirstSnapshot.swift` | `views/messaging/services/ChatServiceLocalFirstSnapshot.kt` | [x] 181↔182 |
| 484 | `Moments/Moments/Views/Messaging/Services/ChatService+MediaPipeline.swift` | `views/messaging/services/ChatServiceMediaPipeline.kt` | [x] 484↔421 |
| 128 | `Moments/Moments/Views/Messaging/Services/ChatService+MessageActions.swift` | `views/messaging/services/ChatServiceMessageActions.kt` | [x] 128↔148 |
| 355 | `Moments/Moments/Views/Messaging/Services/ChatService+MessageHydration.swift` | `views/messaging/services/ChatServiceMessageHydration.kt` | [x] 355↔367 |
| 159 | `Moments/Moments/Views/Messaging/Services/ChatService+MessageReactions.swift` | `views/messaging/services/ChatServiceMessageReactions.kt` | [x] 159↔153 |
| 89 | `Moments/Moments/Views/Messaging/Services/ChatService+Search.swift` | `views/messaging/services/ChatServiceSearch.kt` | [x] 89↔57 |
| 562 | `Moments/Moments/Views/Messaging/Services/ChatService+SharingAndViewOnce.swift` | `views/messaging/services/ChatServiceSharingAndViewOnce.kt` | [x] 557↔354 |
| 253 | `Moments/Moments/Views/Messaging/Services/ChatService+VanishMode.swift` | `views/messaging/services/ChatServiceVanishMode.kt` | [x] 253↔201 |
| 2834 ⚠️ | `Moments/Moments/Views/Messaging/Services/ChatService.swift` | `views/messaging/services/ChatService.kt` | [x] 2835↔2043 |
| 184 | `Moments/Moments/Views/Messaging/Services/ChatSessionEngine.swift` | `views/messaging/services/ChatSessionEngine.kt` | [x] 184↔220 |
| 59 | `Moments/Moments/Views/Messaging/Services/ChatVideoPosterGenerator.swift` | `views/messaging/services/ChatVideoPosterGenerator.kt` | [x] 59↔52 |
| 298 | `Moments/Moments/Views/Messaging/Services/LiveLocationSharingService.swift` | `views/messaging/services/LiveLocationSharingService.kt` | [x] 298↔356 |
| 33 | `Moments/Moments/Views/Messaging/Services/ViewOnceConsumptionService.swift` | `views/messaging/services/ViewOnceConsumptionService.kt` | [x] 33↔27 |
| 231 | `Moments/Moments/Views/Misc/WhatsNewView.swift` | `views/misc/WhatsNewView.kt` | [~] |
| 89 | `Moments/Moments/Views/Nova/AI/NovaAIService.swift` | `views/nova/ai/NovaAIService.kt` | [~] |
| 40 | `Moments/Moments/Views/Nova/AI/NovaGenerationConfig.swift` | `views/nova/ai/NovaGenerationConfig.kt` | [~] |
| 105 | `Moments/Moments/Views/Nova/AI/NovaPromptCatalog.swift` | `views/nova/ai/NovaPromptCatalog.kt` | [~] |
| 769 | `Moments/Moments/Views/Nova/Agent/NovaAgent.swift` | `views/nova/agent/NovaAgent.kt` | [~] |
| 55 | `Moments/Moments/Views/Nova/Agent/NovaContextAssembler.swift` | `views/nova/agent/NovaContextAssembler.kt` | [~] |
| 308 | `Moments/Moments/Views/Nova/Agent/NovaPendingAction.swift` | `views/nova/agent/NovaPendingAction.kt` | [~] |
| 291 | `Moments/Moments/Views/Nova/Agent/NovaToolExecutor.swift` | `views/nova/agent/NovaToolExecutor.kt` | [~] |
| 304 | `Moments/Moments/Views/Nova/Agent/NovaToolRegistry.swift` | `views/nova/agent/NovaToolRegistry.kt` | [~] |
| 507 | `Moments/Moments/Views/Nova/Conversation/NovaConversationStore.swift` | `views/nova/conversation/NovaConversationStore.kt` | [~] |
| 214 | `Moments/Moments/Views/Nova/Conversationmodels.swift` | `views/nova/Conversationmodels.kt` | [~] |
| 12 | `Moments/Moments/Views/Nova/Core/NovaLocaleContext.swift` | `views/nova/core/NovaLocaleContext.kt` | [~] |
| 136 | `Moments/Moments/Views/Nova/Memory/NovaContextStore.swift` | `views/nova/memory/NovaContextStore.kt` | [~] |
| 104 | `Moments/Moments/Views/Nova/Memory/NovaMemoryCrypto.swift` | `views/nova/memory/NovaMemoryCrypto.kt` | [~] |
| 251 | `Moments/Moments/Views/Nova/Memory/NovaMemoryEngine.swift` | `views/nova/memory/NovaMemoryEngine.kt` | [~] |
| 304 | `Moments/Moments/Views/Nova/Memory/NovaMemoryModels.swift` | `views/nova/memory/NovaMemoryModels.kt` | [~] |
| 61 | `Moments/Moments/Views/Nova/Memory/NovaMemoryStore.swift` | `views/nova/memory/NovaMemoryStore.kt` | [~] |
| 63 | `Moments/Moments/Views/Nova/NovaCore/NovaModels.swift` | `views/nova/novacore/NovaModels.kt` | [~] |
| 92 | `Moments/Moments/Views/Nova/NovaCore/NovaTheme.swift` | `views/nova/novacore/NovaTheme.kt` | [~] |
| 420 | `Moments/Moments/Views/Nova/NovaMemoryManagementView.swift` | `views/nova/NovaMemoryManagementView.kt` | [~] |
| 1041 ⚠️ | `Moments/Moments/Views/Nova/NovaSections/NovaAttachmentSheet.swift` | `views/nova/novasections/NovaAttachmentSheet.kt` | [~] |
| 853 | `Moments/Moments/Views/Nova/NovaSections/NovaChatSection.swift` | `views/nova/novasections/NovaChatSection.kt` | [~] |
| 726 | `Moments/Moments/Views/Nova/NovaSections/NovaChromeSection.swift` | `views/nova/novasections/NovaChromeSection.kt` | [~] |
| 211 | `Moments/Moments/Views/Nova/NovaSections/NovaHistorySection.swift` | `views/nova/novasections/NovaHistorySection.kt` | [~] |
| 322 | `Moments/Moments/Views/Nova/NovaSections/NovaInputSection.swift` | `views/nova/novasections/NovaInputSection.kt` | [~] |
| 385 | `Moments/Moments/Views/Nova/NovaView.swift` | `views/nova/NovaView.kt` | [~] |
| 317 | `Moments/Moments/Views/Nova/Tools/NovaActivityTools.swift` | `views/nova/tools/NovaActivityTools.kt` | [~] |
| 52 | `Moments/Moments/Views/Nova/Tools/NovaMemoryTools.swift` | `views/nova/tools/NovaMemoryTools.kt` | [~] |
| 229 | `Moments/Moments/Views/Nova/Tools/NovaMomentAudience.swift` | `views/nova/tools/NovaMomentAudience.kt` | [~] |
| 53 | `Moments/Moments/Views/Nova/Tools/NovaMomentDraftParser.swift` | `views/nova/tools/NovaMomentDraftParser.kt` | [~] |
| 478 | `Moments/Moments/Views/Nova/Tools/NovaProfileTools.swift` | `views/nova/tools/NovaProfileTools.kt` | [~] |
| 164 | `Moments/Moments/Views/Nova/Tools/NovaSocialTools.swift` | `views/nova/tools/NovaSocialTools.kt` | [~] |
| 103 | `Moments/Moments/Views/Nova/UI/NovaActionConfirmationOverlay.swift` | `views/nova/ui/NovaActionConfirmationOverlay.kt` | [~] |
| 21 | `Moments/Moments/Views/Permission/camera/Contentview.swift` | `views/permission/camera/Contentview.kt` | [~] |
| 318 | `Moments/Moments/Views/Permission/camera/helpers/CameraPermissionsview.swift` | `views/permission/camera/helpers/CameraPermissionsview.kt` | [~] |
| 182 | `Moments/Moments/Views/Permission/location/LocationPermissionView.swift` | `views/permission/location/LocationPermissionView.kt` | [~] |
| 134 | `Moments/Moments/Views/Permission/microphone/MicrophonePermissionView.swift` | `views/permission/microphone/MicrophonePermissionView.kt` | [~] |
| 215 | `Moments/Moments/Views/Permission/notifications/NotificationsPermissionView.swift` | `views/permission/notifications/NotificationsPermissionView.kt` | [~] |
| 99 | `Moments/Moments/Views/Permission/photos/PhotosPermissionView.swift` | `views/permission/photos/PhotosPermissionView.kt` | [~] |
| 153 | `Moments/Moments/Views/Permission/shared/LocationPermissionGate.swift` | `views/permission/shared/LocationPermissionGate.kt` | [~] |
| 165 | `Moments/Moments/Views/Permission/shared/PermissionPhoneFrame.swift` | `views/permission/shared/PermissionPhoneFrame.kt` | [~] |
| 17 | `Moments/Moments/Views/Permission/shared/PermissionPhoneWallpaper.swift` | `views/permission/shared/PermissionPhoneWallpaper.kt` | [~] |
| 167 | `Moments/Moments/Views/Permission/shared/PermissionPrimerGate.swift` | `views/permission/shared/PermissionPrimerGate.kt` | [~] |
| 147 | `Moments/Moments/Views/Permission/shared/PermissionPrimerScaffold.swift` | `views/permission/shared/PermissionPrimerScaffold.kt` | [~] |
| 148 | `Moments/Moments/Views/Permission/tracking/TrackingPermissionView.swift` | `views/permission/tracking/TrackingPermissionView.kt` | [~] |
| 106 | `Moments/Moments/Views/Permissions/CameraAccessBoundary.swift` | `views/permissions/CameraAccessBoundary.kt` | [x] 106↔160 · primer/denied prefs; mic+cam→Settings; ON_RESUME |
| 111 | `Moments/Moments/Views/Permissions/CameraPermissionGate.swift` | `views/permissions/CameraPermissionGate.kt` | [~] |
| 78 | `Moments/Moments/Views/Profile/Core/MomentGridPreview.swift` | `views/profile/core/MomentGridPreview.kt` | [x] 78↔93 · settings+frame; ZStack center FIT; DEFAULT |
| 637 | `Moments/Moments/Views/Profile/Core/ProfileView.swift` | `views/profile/core/ProfileView.kt` | [x] 637↔616 · sheets+hero menu+edit/delete; ShowProfileVisits; stories own; temas 🚫 |
| 857 | `Moments/Moments/Views/Profile/Core/ProfileViewModel.swift` | `views/profile/core/ProfileViewModel.kt` | [x] 857↔566 · performRefresh; removeFollower FS; note maxLength; pin/archive/delete |
| 239 | `Moments/Moments/Views/Profile/Core/Sections/ProfileBentoLayout.swift` | `views/profile/core/sections/ProfileBentoLayout.kt` | [x] 239↔160 · assigner; BentoGrid planFrames Dp |
| 1229 ⚠️ | `Moments/Moments/Views/Profile/Core/Sections/ProfileGridHeroTransition.swift` | `views/profile/core/sections/ProfileGridHeroTransition.kt` | [x] 1229↔1102 · peek flying+menu; frames+lifted; zoom handoff |
| 452 | `Moments/Moments/Views/Profile/Core/Sections/ProfileGridMomentMenu.swift` | `views/profile/core/sections/ProfileGridMomentMenu.kt` | [x] 452↔397 · hero+video/badge; visitor rail fijo+glass; avatar authorId |
| 520 | `Moments/Moments/Views/Profile/Core/Sections/ProfileGridPreviewEditorView.swift` | `views/profile/core/sections/ProfileGridPreviewEditorView.kt` | [x] 520↔633 · crop+pan/pinch; fill/fit+fondo; sheet large |
| 592 | `Moments/Moments/Views/Profile/Core/Sections/ProfileHeaderSection.swift` | `views/profile/core/sections/ProfileHeaderSection.kt` | [x] 592↔547 · StickyChrome+note+ExpandableBio; zoom settings/edit; hasActiveStory; badges 🚫 |
| 93 | `Moments/Moments/Views/Profile/Core/Sections/ProfileHeaderSkeletonView.swift` | `views/profile/core/sections/ProfileHeaderSkeletonView.kt` | [x] 93↔111 · header+grid skeleton; 3col metrics; shimmer+a11y |
| 422 | `Moments/Moments/Views/Profile/Core/Sections/ProfileMomentZoomNavigation.swift` | `views/profile/core/sections/ProfileMomentZoomNavigation.kt` | [x] 422↔342 · sharedBounds source/dest; surfaces; Saved→stub; Highlight/MomentZoomOpener |
| 539 | `Moments/Moments/Views/Profile/Core/Sections/ProfileMomentsSection.swift` | `views/profile/core/sections/ProfileMomentsSection.kt` | [x] 539↔438 · bentoHeight/planFrames Dp; carousel+ChatVideoPlay+ActivityGridAudience; hero frames → post-paridad |
| 541 | `Moments/Moments/Views/Profile/Core/Sections/ProfileSavedSection.swift` | `views/profile/core/sections/ProfileSavedSection.kt` | [x] 541↔586 · filters+grid+recent; zoom source; ScreenshotProtected; media/video/text |
| 672 | `Moments/Moments/Views/Profile/Core/Sections/ProfileSharedComponents.swift` | `views/profile/core/sections/ProfileSharedComponents.kt` | [x] 672↔520 · error/bio/flow/note; sticky chrome canvas (sin blur); collapse metrics; report minY |
| 588 | `Moments/Moments/Views/Profile/Core/Sections/ProfileShellComponents.swift` | `views/profile/core/sections/ProfileShellComponents.kt` | [x] 588↔521 · scroll+collapse tabsMinY; sticky+floating tabs; PTR; ScreenshotProtected; themes 🚫 |
| 58 | `Moments/Moments/Views/Profile/Core/Sections/UserProfileZoomNavigation.swift` | `views/profile/core/sections/UserProfileZoomNavigation.kt` | [x] 58↔129 · SharedTransition sharedBounds; Host≡navDestination; Story+Feed |
| 1566 ⚠️ | `Moments/Moments/Views/Profile/Core/SharedActivityDetailView.swift` | `views/profile/core/SharedActivityDetailView.kt` | [x] 1566↔1440 · VM CF pages+batch delete; ActivityCollapsibleFilterScroll+floating chips; tabs/filters/select/grid/comments |
| 474 | `Moments/Moments/Views/Profile/Core/SharedActivityView.swift` | `views/profile/core/SharedActivityView.kt` | [x] 474↔576 · hero cutout+chrome glass btns; timeline; AttachmentIcon/AnimatedReaction modules; PTR; Detail nav |
| 452 | `Moments/Moments/Views/Profile/Core/SocialConnectionUserRow.swift` | `views/profile/core/SocialConnectionUserRow.kt` | [x] 452↔486 · metrics+compact follow+mutual cutout+press+ellipsis menu+remove stroke |
| 791 | `Moments/Moments/Views/Profile/Core/SocialConnectionsView.swift` | `views/profile/core/SocialConnectionsView.kt` | [x] 791↔747 · tabs+search/sort+Visits/Users/Common+pull+floating chips; Profile shell wire; stalker overlay |
| 666 | `Moments/Moments/Views/Profile/Core/UserListView.swift` | `views/profile/core/UserListView.kt` | [x] 666↔674 · empty states iOS keys+SuggestedUserRow Explore+glass search+ModernProfile row flame/press |
| 808 | `Moments/Moments/Views/Profile/Editor/PhotoCropEditorView.swift` | `views/profile/editor/PhotoCropEditorView.kt` | [x] 808↔796 · square crop+blur bg+circle mask+grid on drag; albums MediaStore; export 400; EXIF normalize |
| 1743 ⚠️ | `Moments/Moments/Views/Profile/Editor/ProfileEditor.swift` | `views/profile/editor/ProfileEditor.kt` | [x] 1743↔1234 · ModernEditProfile tabs+glass+load FS+photo library/camera/delete sheets; InterestPicker; Grid→LibraryCrop entry |
| 285 | `Moments/Moments/Views/Profile/Editor/Sections/ProfileEditorPickerViews.swift` | `views/profile/editor/sections/ProfileEditorPickerViews.kt` | [x] 285↔418 · AlbumPicker thumbs+drag; LibraryCropEntry PermissionPrimer+recent→PhotoCrop; denied/settings |
| 439 | `Moments/Moments/Views/Profile/Highlights/HighlightComponents.swift` | `views/profile/highlights/HighlightComponents.kt` | [x] 439↔422 · grid 9:16+date badge+select ring+editor chrome+cover picker rail |
| 148 | `Moments/Moments/Views/Profile/Highlights/HighlightCreateFlowView.swift` | `views/profile/highlights/HighlightCreateFlowView.kt` | [x] 148↔196 · toolbar cancel/next/back/ellipsis delete+save check/plus |
| 256 | `Moments/Moments/Views/Profile/Highlights/HighlightCreateFlowViewModel.swift` | `views/profile/highlights/HighlightCreateFlowViewModel.kt` | [x] 256↔210 · archive page 24+edit seed+save/delete; defaultTitle localized |
| 101 | `Moments/Moments/Views/Profile/Highlights/HighlightNameCoverStep.swift` | `views/profile/highlights/HighlightNameCoverStep.kt` | [x] 101↔150 · cover 118+editCover+focus; MomentsModalSheet cover picker |
| 90 | `Moments/Moments/Views/Profile/Highlights/HighlightPresentationCoordinator.swift` | `views/profile/highlights/HighlightPresentationCoordinator.kt` | [x] 90↔79 · sheet/viewer mutex+delay MapSheetPresentationDelay |
| 33 | `Moments/Moments/Views/Profile/Highlights/HighlightSelectStoriesStep.swift` | `views/profile/highlights/HighlightSelectStoriesStep.kt` | [x] 33↔41 · archiveEmpty+pagination on last appear |
| 135 | `Moments/Moments/Views/Profile/Highlights/HighlightViewer.swift` | `views/profile/highlights/HighlightViewer.kt` | [x] 135↔165 · load+privacy filter+drag dismiss>120; StoriesView |
| 372 | `Moments/Moments/Views/Profile/Highlights/ProfileHighlightsView.swift` | `views/profile/highlights/ProfileHighlightsView.kt` | [x] 372↔365 · rail create/load skeleton+context menu edit/delete+viewer/create sheets |
| 214 | `Moments/Moments/Views/Profile/Incognito/IncognitoGlobalOverlay.swift` | `views/profile/incognito/IncognitoGlobalOverlay.kt` | [x] 214↔216 · edge aura+pill glass+expand pause; host MomentsApp |
| 347 | `Moments/Moments/Views/Profile/Incognito/IncognitoModeSheet.swift` | `views/profile/incognito/IncognitoModeSheet.kt` | [x] 347↔422 · ring+CTA glass+onboarding; detents medium/large; Live Activity 🚫 |
| 1413 ⚠️ | `Moments/Moments/Views/Profile/MomentsView/ContextMenu.swift` | `views/profile/momentsview/ContextMenu.kt` | [x] 1413↔1060 · metrics+ModernShareSheet+AddToStory+StoriesView discoveries |
| 622 | `Moments/Moments/Views/Profile/MomentsView/EditMomentView.swift` | `views/profile/momentsview/EditMomentView.kt` | [x] 622↔697 · Audience/Location/PhotoTag sheets + mediaItems save |
| 611 | `Moments/Moments/Views/Profile/MomentsView/ModernMomentDetailView.swift` | `views/profile/momentsview/ModernMomentDetailView.kt` | [x] 611↔686 · ModernShareBottomSheet + StoriesView(startWithUserId) |
| 933 | `Moments/Moments/Views/Profile/Theme/EnhancedProfileBackground.swift` | — | 🚫 |
| 925 | `Moments/Moments/Views/Profile/Theme/ProfileTheme.swift` | — | 🚫 |
| 146 | `Moments/Moments/Views/Profile/Theme/ProfileThemeDemo.swift` | — | 🚫 |
| 274 | `Moments/Moments/Views/Profile/Theme/ProfileThemeSelector.swift` | — | 🚫 |
| 244 | `Moments/Moments/Views/Profile/UserProfile/Sections/UserProfileAvatarBadges.swift` | `views/profile/userprofile/sections/UserProfileAvatarBadges.kt` | [x] 244↔57 · ring+longPress; chapas Plus/Support 🚫; refresh en SharedViews |
| 321 | `Moments/Moments/Views/Profile/UserProfile/Sections/UserProfileHeaderSection.swift` | `views/profile/userprofile/sections/UserProfileHeaderSection.kt` | [x] 321↔352 · StickyChrome+note+ShareLink URL+QR; badges 🚫; msg vía host |
| 394 | `Moments/Moments/Views/Profile/UserProfile/Sections/UserProfileMomentsSection.swift` | `views/profile/userprofile/sections/UserProfileMomentsSection.kt` | [x] 394↔64 · delega ModernMomentThumbnail sin audience; bento heights + press scale |
| 351 | `Moments/Moments/Views/Profile/UserProfile/Sections/UserProfileOverviewSection.swift` | `views/profile/userprofile/sections/UserProfileOverviewSection.kt` | [x] 351↔390 · stats+intereses; shared via Firestore onAppear; UserProfileColors |
| 320 | `Moments/Moments/Views/Profile/UserProfile/Sections/UserProfilePublicProfileView.swift` | `views/profile/userprofile/sections/UserProfilePublicProfileView.kt` | [x] 320↔268 · sticky+floating tabs+momentRefresh; grids/highlights |
| 363 | `Moments/Moments/Views/Profile/UserProfile/Sections/UserProfileRelationshipViews.swift` | `views/profile/userprofile/sections/UserProfileRelationshipViews.kt` | [x] 363↔440 · chip+sheet BF/mute/listas/unfollow; icons SF; MomentsModalSheet host |
| 339 | `Moments/Moments/Views/Profile/UserProfile/Sections/UserProfileSharedViews.swift` | `views/profile/userprofile/sections/UserProfileSharedViews.kt` | [x] 339↔361 · StatItem+blur+preview+expandable+viewer+refresh; UserFlowLayout→ProfileFlowLayout |
| 665 | `Moments/Moments/Views/Profile/UserProfile/Sections/UserProfileStateViews.swift` | `views/profile/userprofile/sections/UserProfileStateViews.kt` | [x] 665↔613 · estados; swipe>100 blocked dismiss; onOpenMessage host |
| 688 | `Moments/Moments/Views/Profile/UserProfile/UserProfileView.swift` | `views/profile/userprofile/UserProfileView.kt` | [x] 688↔629 · root+sheets+SocialConnections; startConversation→chat/pending; temas 🚫 |
| 1071 ⚠️ | `Moments/Moments/Views/Profile/UserProfile/UserProfileViewModel.swift` | `views/profile/userprofile/UserProfileViewModel.kt` | [x] 1071↔723 · listener FollowStateStore; errores unavailable/network; mute get+update; BF vía PrivacyService |
| 430 | `Moments/Moments/Views/Settings/AccountHistoryActivityView.swift` | `views/settings/AccountHistoryActivityView.kt` | [x] 430↔~640 · filters+custom date+PTR+join synth+timeline |
| 859 | `Moments/Moments/Views/Settings/AccountManagement.swift` | `views/settings/AccountManagement.kt` | [x] 859↔~980 · Advanced+DeleteVerification; Apple→Google |
| 201 | `Moments/Moments/Views/Settings/ActivityCollapsibleFilterScroll.swift` | `views/settings/ActivityCollapsibleFilterScroll.kt` | [x] 201↔137 · header fade+floating reveal on scroll-up+PullToRefreshBox |
| 184 | `Moments/Moments/Views/Settings/BlockedUsersView.swift` | `views/settings/BlockedUsersView.kt` | [x] 184↔~260 · list+unblock glass+PTR+error; SettingsSubsectionWrapper |
| 343 | `Moments/Moments/Views/Settings/ChatStorageSettingsView.swift` | `views/settings/ChatStorageSettingsView.kt` | [x] 343↔~520 · real ChatCacheStore+prefs+per-convo+clear |
| 853 | `Moments/Moments/Views/Settings/ContentVisibilityView.swift` | `views/settings/ContentVisibilityView.kt` | [x] |
| 168 | `Moments/Moments/Views/Settings/DailyLimitView.swift` | `views/settings/DailyLimitView.kt` | [x] |
| 788 | `Moments/Moments/Views/Settings/DataExportView.swift` | `views/settings/DataExportView.kt` | [x] |
| 606 | `Moments/Moments/Views/Settings/LoginActivityView.swift` | `views/settings/LoginActivityView.kt` | [x] |
| 629 | `Moments/Moments/Views/Settings/MuteSettingsView.swift` | `views/settings/MuteSettingsView.kt` | [x] |
| 421 | `Moments/Moments/Views/Settings/PasswordChangeView.swift` | `views/settings/PasswordChangeView.kt` | [x] |
| 166 | `Moments/Moments/Views/Settings/QRCode.swift` | `views/settings/QRCode.kt` | [x] 166↔~290 · QR real glowsy:// + share/save + photos gate |
| 190 | `Moments/Moments/Views/Settings/RestModeView.swift` | `views/settings/RestModeView.kt` | [x] |
| 383 | `Moments/Moments/Views/Settings/SavedMoments/SavedMomentsViewModel.swift` | `views/settings/savedmoments/SavedMomentsViewModel.kt` | [x] |
| 1595 ⚠️ | `Moments/Moments/Views/Settings/SavedMoments/SavedMomentsView.swift` | `views/settings/savedmoments/SavedMomentsView.kt` + `ModernSavedMomentsDetailView.kt` | [x] |
| 270 | `Moments/Moments/Views/Settings/SearchHistoryActivityView.swift` | `views/settings/SearchHistoryActivityView.kt` | [x] |
| 349 | `Moments/Moments/Views/Settings/SetPasswordView.swift` | `views/settings/SetPasswordView.kt` | [x] |
| 121 | `Moments/Moments/Views/Settings/SettingsNavigationComponents.swift` | `views/settings/SettingsNavigationComponents.kt` | [x] 121↔142 · back chrome+nav bar+subsection bg/wrapper+chrome modifier |
| 252 | `Moments/Moments/Views/Settings/SettingsSections/NotificationSettingsView.swift` | `views/settings/settingssections/NotificationSettingsView.kt` | [x] 252↔~420 · schedule+types+advanced+save toast |
| 67 | `Moments/Moments/Views/Settings/SettingsSections/OnlineStatusSection.swift` | `views/settings/settingssections/OnlineStatusSection.kt` | [x] 67↔~160 · menu allCases + setGlobalStatus + strings |
| 352 | `Moments/Moments/Views/Settings/SettingsSections/PersonalInfoSettingsViews.swift` | `views/settings/settingssections/PersonalInfoSettingsViews.kt` | [x] 352↔~480 · main+username cooldown+availability+cache |
| 1579 ⚠️ | `Moments/Moments/Views/Settings/SettingsSections/SettingsSections.swift` | `views/settings/sections/*` | [x] 1579↔~1600 · form+sections; Google link/unlink (≡Apple); Passkey 🚫 |
| 332 | `Moments/Moments/Views/Settings/SettingsView.swift` | `views/settings/SettingsView.kt` + `sections/SettingsFormView.kt` | [x] 332↔~370 · shell+form real (SettingsSections) |
| 97 | `Moments/Moments/Views/Settings/SettingsViewModel.swift` | `views/settings/SettingsViewModel.kt` | [x] 97↔151 · fetch+privacy+receipts+policy+hours+prefs; settingsToggleCases |
| 99 | `Moments/Moments/Views/Settings/TimeSpentCardView.swift` | `views/settings/TimeSpentCardView.kt` | [x] |
| 95 | `Moments/Moments/Views/Settings/TimeSpentDetailsView.swift` | `views/settings/TimeSpentDetailsView.kt` | [x] |
| 116 | `Moments/Moments/Views/Settings/UserActivityBackendModels.swift` | `views/settings/UserActivityBackendModels.kt` | [x] |
| 183 | `Moments/Moments/Views/Settings/UserActivityCache.swift` | `views/settings/UserActivityCache.kt` | [x] |
| 220 | `Moments/Moments/Views/Settings/UserActivityComponents.swift` | `views/settings/UserActivityComponents.kt` | [x] |
| 2199 ⚠️ | `Moments/Moments/Views/Settings/UserActivityDetailView.swift` | `views/settings/UserActivityDetailView.kt` | [x] |
| 1224 ⚠️ | `Moments/Moments/Views/Settings/UserActivityDetailViewModel.swift` | `views/settings/UserActivityDetailViewModel.kt` | [x] |
| 98 | `Moments/Moments/Views/Settings/UserActivityModels.swift` | `views/settings/UserActivityModels.kt` | [x] |
| 1178 ⚠️ | `Moments/Moments/Views/Settings/UserActivityRows.swift` | `views/settings/UserActivityRows.kt` | [x] |
| 119 | `Moments/Moments/Views/Settings/UserActivitySummaryViewModel.swift` | `views/settings/UserActivitySummaryViewModel.kt` | [x] |
| 327 | `Moments/Moments/Views/Settings/UserActivityTypes.swift` | `views/settings/UserActivityTypes.kt` | [x] |
| 266 | `Moments/Moments/Views/Settings/UserActivityView.swift` | `views/settings/UserActivityView.kt` | [x] |
| 33 | `Moments/Moments/Views/Shared/AppErrorBanner.swift` | `views/shared/AppErrorBanner.kt` | [x] 33↔75 · chrome glass+retry; Feed usa shared (quitado dup rojo) |
| 10 | `Moments/Moments/Views/Shared/BlurView.swift` | — | 🚫 UIKit `UIViewRepresentable`; call sites → fill / `momentsChromeGlass` |
| 34 | `Moments/Moments/Views/Shared/MomentDetail/MomentDetailContainerView.swift` | `views/shared/momentdetail/MomentDetailContainerView.kt` | [x] 34↔52 · single/carousel/map → Single/Modern/Location |
| 21 | `Moments/Moments/Views/Shared/MomentDetail/MomentDetailContext.swift` | `views/shared/momentdetail/MomentDetailContext.kt` | [x] 21↔30 · Single/ProfileCarousel/Map |
| 412 | `Moments/Moments/Views/Shared/MomentDetail/SingleMomentDetailView.swift` | `views/shared/momentdetail/SingleMomentDetailView.kt` | [x] 412↔484 · chrome blur+Stories startWithUserId+video+velocity dismiss |
| 100 | `Moments/Moments/Views/Shared/MomentsVideoPlaybackTimeline.swift` | `views/shared/MomentsVideoPlaybackTimeline.kt` | [x] 100↔160 · scrub+knob+formatTime |
| 331 | `Moments/Moments/Views/Shared/MomentsVideoPlayer.swift` | `views/shared/MomentsVideoPlayer.kt` | [x] 331↔255 · Exo+Preloader+stall+gravity; NormalVideo/FullScreen cableados |
| 214 | `Moments/Moments/Views/Shared/OfflineBannerModifier.swift` | `views/shared/OfflineBannerModifier.kt` | [x] 214↔208 · Collapsible+orb; TabBar+UserProfile overlay |
| 74 | `Moments/Moments/Views/Shared/PhotoTagOverlayView.swift` | `views/shared/PhotoTagOverlayView.kt` | [x] 74↔134 · position+triangle+spring |
| 175 | `Moments/Moments/Views/Shared/ScreenshotProtectedView.swift` | `views/shared/ScreenshotProtectedView.kt` | [x] 175↔~120 · FLAG_SECURE refcount; cornerRadius+updateToken; fillsContainer |
| 227 | `Moments/Moments/Views/comments/CommentMentionSearchOverlay.swift` | `views/comments/CommentMentionSearchOverlay.kt` | [~] chrome glass header/cancel/+ + stroke panel |
| 429 | `Moments/Moments/Views/comments/CommentsView.swift` | — | [ ] |
| 1900 ⚠️ | `Moments/Moments/Views/comments/ModernCommentsView.swift` | `views/comments/ModernCommentsView.kt` | [~] P1: floating composer, edit UI, sort icons, diacritic mentions, chrome overlay; pendiente QA |
| 503 | `Moments/Moments/Views/story/QuestionResponsesView.swift` | `views/story/QuestionResponsesView.kt` | [x] sheet medium/large; chrome glass; list+detalle+creator; avatar→perfil |
| 1018 ⚠️ | `Moments/Moments/Views/story/StoriesView.swift` | `views/story/StoriesView.kt` | [x] 1018↔~1050 · NavigateToChainStory+loadChain; pauseAllVideos; ring vacío→following; unseen/error; updateUserIds c/ad |
| 635 | `Moments/Moments/Views/story/StoryChainView.swift` | `views/story/StoryChainView.kt` | [x] sheet+grid+stats+continue≡ContinueStoryChain; errores tipados; chrome; ItemView legacy N/A |
| 54 | `Moments/Moments/Views/story/StoryDeckGestureGate.swift` | `views/story/StoryDeckGestureGate.kt` | [x] 54↔67 · scopes+regiones+legacy.sticker; Local≡Environment |
| 1106 ⚠️ | `Moments/Moments/Views/story/StoryInteractiveStickers.swift` | `views/story/StoryInteractiveStickers.kt` | [x] 1106↔~1100 · quiz+confetti; polaroid; reveal patrones+reduceMotion; hint chrome glass; exclusion+passthrough laterales |
| 214 | `Moments/Moments/Views/story/StoryModels.swift` | `views/story/StoryModels.kt` | [x] 214↔203 · Reaction/Viewer/Ring+badges Firestore; reexport VerifiedBadge |
| 224 | `Moments/Moments/Views/story/StoryPlaybackCoordinator.swift` | `views/story/StoryPlaybackCoordinator.kt` | [x] 224↔259 · progress+timer+preload Coil/Video+memory trim |
| 435 | `Moments/Moments/Views/story/StoryRepository.swift` | `views/story/StoryRepository.kt` | [x] 435↔330 · ReplyData+CRUD+decodeBackendStory; Storage helpers |
| 200 | `Moments/Moments/Views/story/StoryRingAvatarView.swift` | `views/story/StoryRingAvatarView.kt` | [x] 200↔219 · Layout+gap mask+baseStroke+resolve; zoom Namespace stub |
| 246 | `Moments/Moments/Views/story/StorySegmentedRing.swift` | `views/story/StorySegmentedRing.kt` | [x] 246↔222 · gaps 15º+audiencia+gris visto; triggerHaptic medium |
| 617 | `Moments/Moments/Views/story/StoryStickers/StoryStickerEffects.swift` | `views/story/storystickers/StoryStickerEffects.kt` | [x] 617↔~400 · weather+Bolt+sombras; hearts EaseOut/expire/haptic(Layers); Exo; KeyboardIgnoring N/A |
| 1911 ⚠️ | `Moments/Moments/Views/story/StoryStickers/StoryStickerViews.swift` | `views/story/storystickers/StoryStickerViews.kt` | [x] 1911↔~1350 · cards+emoji pan+map/explore+shareMoment; AnimatedSurfaces poll/Q; exclusion→gate; PollVoteView dead iOS N/A |
| 912 | `Moments/Moments/Views/story/StoryViewModel.swift` | `views/story/StoryViewModel.kt` | [x] 912↔739 · ring+privacy+replies/vanish/ephemeral+reactions+preload; strings 8 locales |
| 225 | `Moments/Moments/Views/story/StoryViewer/StoryDeckInteractionLayout.swift` | `views/story/storyviewer/StoryDeckInteractionLayout.kt` | [x] 225↔~200 · exclusion+gate; EmojiSliderVotePan; RevealScratchPan laterales |
| 180 | `Moments/Moments/Views/story/StoryViewer/StoryGestureCoordinator.swift` | `views/story/storyviewer/StoryGestureCoordinator.kt` | [x] 180↔186 · intents+scopes+chrome+deck/hold/drag/tap; fix deck Elvis |
| 29 | `Moments/Moments/Views/story/StoryViewer/StoryLiveTextOverlayView.swift` | `views/story/storyviewer/StoryLiveTextOverlayView.kt` | [x] 29↔~75 · Label+position+scale 375; styleRaw nil; motion sanitize; zIndex layerOrder; hit-through |
| 76 | `Moments/Moments/Views/story/StoryViewer/StoryMediaOverlayRendererView.swift` | `views/story/storyviewer/StoryMediaOverlayRendererView.kt` | [x] 76↔~110 · zIndex interleave; drawing -1; exclusion+hitTesting; ViewOnce/Deck flags |
| 118 | `Moments/Moments/Views/story/StoryViewer/StoryQuickActionsMenu.swift` | `views/story/storyviewer/StoryQuickActionsMenu.kt` | [x] 118↔~130 · chrome glass 22; strings 8 locales; momentsMenuRow; swallow taps |
| 777 | `Moments/Moments/Views/story/StoryViewer/StoryReplyViews.swift` | `views/story/storyviewer/StoryReplyViews.kt` | [x] 777↔~790 · bubble+gated+ephemeral+DM; glassmorphic/storyGlassmorphic → Overlay+NativeAd |
| 257 | `Moments/Moments/Views/story/StoryViewer/StoryUserDeckPager.swift` | `views/story/storyviewer/StoryUserDeckPager.kt` | [x] 257↔~310 · Deck Pass; hit-test solo center; root coords exclusión; sheet+0.22 commit |
| 470 | `Moments/Moments/Views/story/StoryViewer/StoryViewerBottomComponents.swift` | `views/story/storyviewer/StoryViewerBottomComponents.kt` | [x] nav Offset local→root; strip spring+stagger+glass+haptic; empty icon; reverse-mask; label shadows |
| 134 | `Moments/Moments/Views/story/StoryViewer/StoryViewerLayers.swift` | `views/story/storyviewer/StoryViewerLayers.kt` | [x] progress chrome + floating burst (haptic/reduceMotion/trim) |
| 144 | `Moments/Moments/Views/story/StoryViewer/StoryViewerLayoutHelpers.swift` | `views/story/storyviewer/StoryViewerLayoutHelpers.kt` | [x] ratio/contentRect/sticker scale+pos + resolved video size |
| 255 | `Moments/Moments/Views/story/StoryViewer/StoryViewerMedia.swift` | `views/story/storyviewer/StoryViewerMedia.kt` | [x] VideoPreloader+audio; blur×1.1; poster; contentUnavailable |
| 1067 ⚠️ | `Moments/Moments/Views/story/StoryViewer/StoryViewerOverlay.swift` | `views/story/storyviewer/StoryViewerOverlay.kt` | [x] progress shadow+anim; dialog chrome/scrim; empty SF icons+appear; sheet audience/search/timeAgo |
| 2381 ⚠️ | `Moments/Moments/Views/story/StoryViewer/StoryViewerScreen.swift` | `views/story/storyviewer/StoryViewerScreen.kt` | [x] delete≡iOS; confirm destructive; smiley filled/outline+dp; nav Offset; overlays |
| 1831 ⚠️ | `Moments/Moments/Views/story/archived stories.swift` | `views/story/ArchivedStoriesView.kt` + `ArchiveViewModel.kt` + `StoryStatsView.kt` | [x] · grid+viewer+StoryStatsView+calendar+map |

## Services

*69 archivos · 26,913 líneas*

| Líneas | Archivo iOS | Android (.kt) | Paridad |
|-------:|-------------|---------------|:-------:|
| 222 | `Moments/Moments/Services/Activity/TimeSpentManager.swift` | `services/activity/TimeSpentManager.kt` | [x] 222↔279 |
| 2571 ⚠️ | `Moments/Moments/Services/Auth/AuthService.swift` | `services/auth/AuthService.kt` | [x] 2571↔1635 |
| 543 | `Moments/Moments/Services/Auth/LoginActivityService.swift` | `services/auth/LoginActivityService.kt` | [x] 543↔412 |
| 155 | `Moments/Moments/Services/Auth/OnboardingDraftStore.swift` | `services/auth/OnboardingDraftStore.kt` | [x] 155↔189 |
| 272 | `Moments/Moments/Services/Auth/PasskeyService.swift` | — | 🚫 |
| 240 | `Moments/Moments/Services/Cache/CacheManager.swift` | `services/cache/CacheManager.kt` | [x] 240↔144 |
| 97 | `Moments/Moments/Services/Cache/ImagePrefetchManager.swift` | `services/cache/ImagePrefetchManager.kt` | [x] 97↔110 |
| 94 | `Moments/Moments/Services/Cache/PersistentAudioCache.swift` | `services/cache/PersistentAudioCache.kt` | [x] 94↔90 |
| 131 | `Moments/Moments/Services/Cache/PersistentVideoCache.swift` | `services/cache/PersistentVideoCache.kt` | [x] 131↔84 |
| 135 | `Moments/Moments/Services/Cache/UserCacheService.swift` | `services/cache/UserCacheService.kt` | [x] 135↔122 |
| 115 | `Moments/Moments/Services/Cache/VideoPreloader.swift` | `services/cache/VideoPreloader.kt` | [x] 115↔91 |
| 58 | `Moments/Moments/Services/Cache/VideoThumbnailCache.swift` | `services/cache/VideoThumbnailCache.kt` | [x] 58↔71 |
| 37 | `Moments/Moments/Services/Camera/SnapCameraKitConfiguration.swift` | `services/camera/SnapCameraKitConfiguration.kt` | [x] 37↔59 |
| 692 | `Moments/Moments/Services/Content/BackendFeedService.swift` | `services/content/BackendFeedService.kt` | [x] 692↔848 |
| 260 | `Moments/Moments/Services/Content/FilterService.swift` | `services/content/FilterService.kt` | [x] 260↔166 |
| 247 | `Moments/Moments/Services/Content/ForYouDiscoveryService.swift` | `services/content/ForYouDiscoveryService.kt` | [x] 247↔168 |
| 108 | `Moments/Moments/Services/Content/ProfileVisitsService.swift` | `services/content/ProfileVisitsService.kt` | [x] 108↔86 |
| 157 | `Moments/Moments/Services/Firestore/FirestoreActivityRepository.swift` | `services/firestore/FirestoreActivityRepository.kt` | [x] 157↔106 |
| 167 | `Moments/Moments/Services/Firestore/FirestoreAudienceRepository.swift` | `services/firestore/FirestoreAudienceRepository.kt` | [x] 167↔159 |
| 807 | `Moments/Moments/Services/Firestore/FirestoreCommentsRepository.swift` | `services/firestore/FirestoreCommentsRepository.kt` | [x] 807↔545 |
| 163 | `Moments/Moments/Services/Firestore/FirestoreCore.swift` | `services/firestore/FirestoreCore.kt` | [x] 163↔183 |
| 377 | `Moments/Moments/Services/Firestore/FirestoreHiddenLayersRepository.swift` | `services/firestore/FirestoreHiddenLayersRepository.kt` | [x] 377↔194 |
| 988 | `Moments/Moments/Services/Firestore/FirestoreMomentsRepository.swift` | `services/firestore/FirestoreMomentsRepository.kt` | [x] 988↔524 |
| 473 | `Moments/Moments/Services/Firestore/FirestoreProfilesRepository.swift` | `services/firestore/FirestoreProfilesRepository.kt` | [x] 473↔248 |
| 306 | `Moments/Moments/Services/Firestore/FirestoreSearchRepository.swift` | `services/firestore/FirestoreSearchRepository.kt` | [x] 306↔144 |
| 1730 ⚠️ | `Moments/Moments/Services/Firestore/FirestoreService.swift` | `services/firestore/FirestoreService.kt` | [x] 1746↔876 |
| 1179 ⚠️ | `Moments/Moments/Services/Firestore/FirestoreStoriesRepository.swift` | `services/firestore/FirestoreStoriesRepository.kt` | [x] 1179↔627 |
| 414 | `Moments/Moments/Services/Incognito/IncognitoModeService.swift` | `services/incognito/IncognitoModeService.kt` | [x] 414↔389 |
| 442 | `Moments/Moments/Services/Messaging/ChatCacheStore.swift` | `services/messaging/ChatCacheStore.kt` | [x] 442↔345 |
| 24 | `Moments/Moments/Services/Messaging/ChatCommunicationNotificationService.swift` | `services/messaging/ChatCommunicationNotificationService.kt` | [x] 24↔28 |
| 183 | `Moments/Moments/Services/Messaging/ChatMediaChunkedCipher.swift` | `services/messaging/ChatMediaChunkedCipher.kt` | [x] 183↔196 |
| 118 | `Moments/Moments/Services/Messaging/ChatMediaDownloadPolicy.swift` | `services/messaging/ChatMediaDownloadPolicy.kt` | [x] 118↔98 |
| 66 | `Moments/Moments/Services/Messaging/ChatMediaPrefetcher.swift` | `services/messaging/ChatMediaPrefetcher.kt` | [x] 66↔82 |
| 51 | `Moments/Moments/Services/Messaging/ChatRecoveryCrypto.swift` | `services/messaging/ChatRecoveryCrypto.kt` | [x] 51↔44 |
| 54 | `Moments/Moments/Services/Messaging/ChatSendMessageIntentHandler.swift` | `services/messaging/ChatSendMessageIntentHandler.kt` | 🚫 |
| 3108 ⚠️ | `Moments/Moments/Services/Messaging/EncryptionService.swift` | `services/messaging/EncryptionService.kt` | [x] 3108↔1156 |
| 16 | `Moments/Moments/Services/Messaging/LocalFirstMessagingSettings.swift` | `services/messaging/LocalFirstMessagingSettings.kt` | [x] 16↔49 |
| 150 | `Moments/Moments/Services/Messaging/MessageCatchUpService.swift` | `services/messaging/MessageCatchUpService.kt` | [x] 150↔122 |
| 270 | `Moments/Moments/Services/Messaging/MessageIngestService.swift` | `services/messaging/MessageIngestService.kt` | [x] 270↔221 |
| 660 | `Moments/Moments/Services/Messaging/MessageRequestService.swift` | `services/messaging/MessageRequestService.kt` | [x] 660↔376 |
| 332 | `Moments/Moments/Services/Messaging/OnlineStatusService.swift` | `services/messaging/OnlineStatusService.kt` | [x] 332↔284 |
| 58 | `Moments/Moments/Services/Messaging/VanishMessageTimer.swift` | `services/messaging/VanishMessageTimer.kt` | [x] 58↔51 |
| 87 | `Moments/Moments/Services/Network/NetworkMonitor.swift` | `services/network/NetworkMonitor.kt` | [x] 87↔159 |
| 635 | `Moments/Moments/Services/Network/OfflineSyncService.swift` | `services/network/OfflineSyncService.kt` | [x] 635↔572 |
| 117 | `Moments/Moments/Services/Nova/NovaEmbeddingService.swift` | `services/nova/NovaEmbeddingService.kt` | [x] 117↔67 |
| 96 | `Moments/Moments/Services/Performance/FeedVisibilityCoordinator.swift` | `services/performance/FeedVisibilityCoordinator.kt` | [x] 96↔67 |
| 88 | `Moments/Moments/Services/Performance/MotionPolicy.swift` | `services/performance/MotionPolicy.kt` | [x] 88↔80 |
| 24 | `Moments/Moments/Services/Performance/PerformanceSignposts.swift` | `services/performance/PerformanceSignposts.kt` | [x] 24↔25 |
| 20 | `Moments/Moments/Services/Performance/VideoMomentsIndex.swift` | `services/performance/VideoMomentsIndex.kt` | [x] 20↔52 |
| 2614 ⚠️ | `Moments/Moments/Services/Persistence/LocalPersistenceService.swift` | `services/persistence/LocalPersistenceService.kt` | [x] 2614↔1701 |
| 277 | `Moments/Moments/Services/Persistence/MessagePersistenceStore.swift` | `services/persistence/MessagePersistenceStore.kt` | [x] 277↔465 |
| 278 | `Moments/Moments/Services/Privacy/ContentVisibilityservice.swift` | `services/privacy/ContentVisibilityservice.kt` | [x] 278↔176 |
| 1424 ⚠️ | `Moments/Moments/Services/Privacy/PrivacyService.swift` | `services/privacy/PrivacyService.kt` | [x] 1424↔738 |
| 45 | `Moments/Moments/Services/Privacy/PrivacyServiceExtension.swift` | `services/privacy/PrivacyServiceExtension.kt` | [x] 45↔30 |
| 15 | `Moments/Moments/Services/Security/MomentsAppCheckProviderFactory.swift` | `services/security/MomentsAppCheckProviderFactory.kt` | [x] 15↔43 |
| 213 | `Moments/Moments/Services/Social/AffinityTracker.swift` | `services/social/AffinityTracker.kt` | [x] 213↔164 |
| 149 | `Moments/Moments/Services/Social/BestFriendsService.swift` | `services/social/BestFriendsService.kt` | [x] 149↔87 |
| 592 | `Moments/Moments/Services/Social/EchoService.swift` | `services/social/EchoService.kt` | [x] 592↔435 |
| 193 | `Moments/Moments/Services/Social/StoryChainLimitsService.swift` | `services/social/StoryChainLimitsService.kt` | [x] 193↔147 |
| 297 | `Moments/Moments/Services/Social/StoryRingCacheService.swift` | `services/social/StoryRingCacheService.kt` | [x] 297↔230 |
| 286 | `Moments/Moments/Services/Storage/MediaUploadService.swift` | `services/storage/MediaUploadService.kt` | [x] 286↔234 |
| 217 | `Moments/Moments/Services/Storage/StoragePathBuilder.swift` | `services/storage/StoragePathBuilder.kt` | [x] 217↔249 |
| 344 | `Moments/Moments/Services/Storage/StorageService.swift` | `services/storage/StorageService.kt` | [x] 344↔302 |
| 51 | `Moments/Moments/Services/Storage/UIImage+StorageUpload.swift` | `services/storage/BitmapStorageUpload.kt` | [x] 51↔40 |
| 114 | `Moments/Moments/Services/Storage/VideoCompressionService.swift` | `services/storage/VideoCompressionService.kt` | [x] 114↔212 |
| 45 | `Moments/Moments/Services/Video/ReelPrebufferService.swift` | `services/video/ReelPrebufferService.kt` | [x] 45↔64 |
| 96 | `Moments/Moments/Services/Video/SharedVideoPlayerPool.swift` | `services/video/SharedVideoPlayerPool.kt` | [x] 96↔106 |
| 132 | `Moments/Moments/Services/Video/VideoAdaptivePlayback.swift` | `services/video/VideoAdaptivePlayback.kt` | [x] 132↔144 |
| 194 | `Moments/Moments/Services/Video/VideoPlaybackSelector.swift` | `services/video/VideoPlaybackSelector.kt` | [x] 194↔125 |

## Models

*21 archivos · 7,653 líneas*

| Líneas | Archivo iOS | Android (.kt) | Paridad |
|-------:|-------------|---------------|:-------:|
| 46 | `Moments/Moments/Models/AccountHistoryItem.swift` | `models/AccountHistoryItem.kt` | [x] 46↔56 |
| 683 | `Moments/Moments/Models/BestFriendsView.swift` | `models/BestFriendsView.kt` | [ ] |
| 66 | `Moments/Moments/Models/Cache/CachedAction.swift` | `models/cache/CachedAction.kt` | [x] 66↔83 |
| 19 | `Moments/Moments/Models/Cache/CachedConnection.swift` | `models/cache/CachedConnection.kt` | [x] 19↔17 |
| 166 | `Moments/Moments/Models/Cache/CachedConversation.swift` | `models/cache/CachedConversation.kt` | [x] 166↔195 |
| 305 | `Moments/Moments/Models/Cache/CachedMessage.swift` | `models/cache/CachedMessage.kt` | [x] 305↔395 |
| 271 | `Moments/Moments/Models/Cache/CachedMoment.swift` | `models/cache/CachedMoment.kt` | [x] 271↔321 |
| 105 | `Moments/Moments/Models/Cache/CachedNotification.swift` | `models/cache/CachedNotification.kt` | [x] 105↔87 |
| 20 | `Moments/Moments/Models/Cache/CachedSearch.swift` | `models/cache/CachedSearch.kt` | [x] 20↔17 |
| 165 | `Moments/Moments/Models/Cache/CachedStory.swift` | `models/cache/CachedStory.kt` | [x] 165↔247 |
| 217 | `Moments/Moments/Models/Cache/CachedUser.swift` | `models/cache/CachedUser.kt` | [x] 217↔229 |
| 278 | `Moments/Moments/Models/ChatSecurityModels.swift` | `models/ChatSecurityModels.kt` | [x] 278↔195 |
| 157 | `Moments/Moments/Models/EchoModels.swift` | `models/EchoModels.kt` | [x] 157↔221 |
| 80 | `Moments/Moments/Models/InterestModels.swift` | `models/InterestModels.kt` | [x] 80↔63 |
| 2722 ⚠️ | `Moments/Moments/Models/Models.swift` | `models/Models.kt` | [x] 2722↔1612 |
| 119 | `Moments/Moments/Models/OutboxPayloads.swift` | `models/OutboxPayloads.kt` | [x] 119↔224 |
| 243 | `Moments/Moments/Models/StickerItem.swift` | `models/StickerItem.kt` | [x] 243↔174 |
| 677 | `Moments/Moments/Models/User.swift` | `models/User.kt` | [x] 677↔269 |
| 39 | `Moments/Moments/Models/UserAffinity.swift` | `models/UserAffinity.kt` | [x] 39↔24 |
| 306 | `Moments/Moments/Models/UserBadge.swift` | — | 🚫 |
| 969 | `Moments/Moments/Models/VisitsView.swift` | `models/VisitsView.kt` | [ ] |

## Notifications

*24 archivos · 6,239 líneas*

| Líneas | Archivo iOS | Android (.kt) | Paridad |
|-------:|-------------|---------------|:-------:|
| 323 | `Moments/Moments/Notifications/Components/NotificationGroupedFollowersOverlay.swift` | `notifications/components/NotificationGroupedFollowersOverlay.kt` | [x] 323↔432 |
| 109 | `Moments/Moments/Notifications/Components/NotificationRowComponents.swift` | `notifications/components/NotificationRowComponents.kt` | [x] 109↔221 |
| 171 | `Moments/Moments/Notifications/Components/NotificationSharedViews.swift` | `notifications/components/NotificationSharedViews.kt` | [x] 171↔201 |
| 13 | `Moments/Moments/Notifications/Core/NotificationGroup.swift` | `notifications/core/NotificationGroup.kt` | [x] 13↔11 |
| 136 | `Moments/Moments/Notifications/Core/NotificationRowSupport.swift` | `notifications/core/NotificationRowSupport.kt` | [x] 136↔171 |
| 329 | `Moments/Moments/Notifications/Core/NotificationsViewModel.swift` | `notifications/core/NotificationsViewModel.kt` | [x] 329↔295 |
| 196 | `Moments/Moments/Notifications/Row/EnhancedNotificationRow+Follow.swift` | `notifications/row/EnhancedNotificationRowFollow.kt` | [x] 196↔262 |
| 597 | `Moments/Moments/Notifications/Row/EnhancedNotificationRow+Messages.swift` | `notifications/row/EnhancedNotificationRowMessages.kt` | [x] 597↔582 |
| 113 | `Moments/Moments/Notifications/Row/EnhancedNotificationRow+Previews.swift` | `notifications/row/EnhancedNotificationRowPreviews.kt` | [x] 113↔215 |
| 311 | `Moments/Moments/Notifications/Row/EnhancedNotificationRow+Trailing.swift` | `notifications/row/EnhancedNotificationRowTrailing.kt` | [x] 311↔339 |
| 304 | `Moments/Moments/Notifications/Row/EnhancedNotificationRow.swift` | `notifications/row/EnhancedNotificationRow.kt` | [x] 304↔292 |
| 200 | `Moments/Moments/Notifications/Screens/NotificationSummaryPopup.swift` | `notifications/screens/NotificationSummaryPopup.kt` | [x] 200↔293 |
| 629 | `Moments/Moments/Notifications/Screens/NotificationsView.swift` | `notifications/screens/NotificationsView.kt` | [x] 629↔616 |
| 261 | `Moments/Moments/Notifications/Services/AppDelegate.swift` | `notifications/services/MomentsFirebaseMessagingService.kt` | [x] 261↔405 |
| 102 | `Moments/Moments/Notifications/Services/FCMTokenService.swift` | `notifications/services/FCMTokenService.kt` | [x] 102↔99 |
| 225 | `Moments/Moments/Notifications/Services/InAppNotificationPreviewResolver.swift` | `notifications/services/InAppNotificationPreviewResolver.kt` | [x] 225↔146 |
| 205 | `Moments/Moments/Notifications/Services/InAppNotificationService.swift` | `notifications/services/InAppNotificationService.kt` | [x] 205↔167 |
| 228 | `Moments/Moments/Notifications/Services/NotificationBadgeService.swift` | `notifications/services/NotificationBadgeService.kt` | [x] 228↔206 |
| 412 | `Moments/Moments/Notifications/Services/NotificationCopyResolver.swift` | `notifications/services/NotificationCopyResolver.kt` | [x] 412↔284 |
| 59 | `Moments/Moments/Notifications/Services/NotificationExtensions.swift` | `notifications/services/NotificationExtensions.kt` | [x] 59↔81 |
| 301 | `Moments/Moments/Notifications/Services/NotificationNavigationService.swift` | `notifications/services/NotificationNavigationService.kt` | [x] 301↔196 |
| 32 | `Moments/Moments/Notifications/Services/NotificationOpenIntentStore.swift` | `notifications/services/NotificationOpenIntentStore.kt` | [x] 32↔31 |
| 350 | `Moments/Moments/Notifications/Services/NotificationPresentationCoordinator.swift` | `notifications/services/NotificationPresentationCoordinator.kt` | [x] 350↔290 |
| 633 | `Moments/Moments/Notifications/Services/Notificationservice.swift` | `notifications/services/Notificationservice.kt` | [x] 633↔538 |

## Reportes

*7 archivos · 3,539 líneas*

| Líneas | Archivo iOS | Android (.kt) | Paridad |
|-------:|-------------|---------------|:-------:|
| 1128 ⚠️ | `Moments/Moments/Reportes/AppealFormView.swift` | `reportes/AppealFormView.kt` | [~] |
| 740 | `Moments/Moments/Reportes/AppealService.swift` | `reportes/AppealService.kt` | [x] 740↔827 |
| 524 | `Moments/Moments/Reportes/AppealStatus.swift` | `reportes/AppealStatus.kt` | [~] |
| 299 | `Moments/Moments/Reportes/ModerationReviewStatusView.swift` | `reportes/ModerationReviewStatusView.kt` | [~] |
| 393 | `Moments/Moments/Reportes/ModernReportContent.swift` | `reportes/ModernReportContent.kt` | [~] |
| 234 | `Moments/Moments/Reportes/ReportBottomSheet.swift` | `reportes/ReportBottomSheet.kt` | [~] |
| 221 | `Moments/Moments/Reportes/UserReportContent.swift` | `reportes/UserReportContent.kt` | [~] |

## ad

*4 archivos · 2,621 líneas*

| Líneas | Archivo iOS | Android (.kt) | Paridad |
|-------:|-------------|---------------|:-------:|
| 64 | `Moments/Moments/ad/AdAspectRatioContext.swift` | `ad/AdAspectRatioContext.kt` | [~] |
| 474 | `Moments/Moments/ad/AdMob Configuration.swift` | — | [~] |
| 604 | `Moments/Moments/ad/FeedNativeAd.swift` | `ad/FeedNativeAd.kt` | [~] |
| 1479 ⚠️ | `Moments/Moments/ad/StoryNativeAd.swift` | `ad/StoryNativeAd.kt` | [~] |

## Moderation

*2 archivos · 2,484 líneas*

| Líneas | Archivo iOS | Android (.kt) | Paridad |
|-------:|-------------|---------------|:-------:|
| 512 | `Moments/Moments/Moderation/CommentsModerationService.swift` | `moderation/CommentsModerationService.kt` | [x] 512↔426 |
| 1972 ⚠️ | `Moments/Moments/Moderation/MediaModerationService.swift` | `moderation/MediaModerationService.kt` | [x] 1972↔972 |

## Coordinators

*5 archivos · 1,479 líneas*

| Líneas | Archivo iOS | Android (.kt) | Paridad |
|-------:|-------------|---------------|:-------:|
| 226 | `Moments/Moments/Coordinators/AppRouter.swift` | `coordinators/AppRouter.kt` | [x] 226↔187 |
| 46 | `Moments/Moments/Coordinators/LegacyNavigationBridge.swift` | `coordinators/LegacyNavigationBridge.kt` | [x] 46↔58 |
| 32 | `Moments/Moments/Coordinators/MainViewModel.swift` | `coordinators/MainViewModel.kt` | [x] 32↔63 |
| 407 | `Moments/Moments/Coordinators/SharedComponents.swift` | `coordinators/SharedComponents.kt` | [x] 407↔427 |
| 768 | `Moments/Moments/Coordinators/TabBarView.swift` | `coordinators/TabBarView.kt` | [~] |

## GlowsyWidgetExtension

*7 archivos · 1,315 líneas*

| Líneas | Archivo iOS | Android (.kt) | Paridad |
|-------:|-------------|---------------|:-------:|
| 18 | `Moments/GlowsyWidgetExtension/AppIntent.swift` | — | [ ] |
| 492 | `Moments/GlowsyWidgetExtension/GlowsyWidgetExtension.swift` | — | [ ] |
| 22 | `Moments/GlowsyWidgetExtension/GlowsyWidgetExtensionBundle.swift` | — | [ ] |
| 74 | `Moments/GlowsyWidgetExtension/GlowsyWidgetExtensionControl.swift` | — | [ ] |
| 471 | `Moments/GlowsyWidgetExtension/GlowsyWidgetExtensionLiveActivity.swift` | — | 🚫 |
| 201 | `Moments/GlowsyWidgetExtension/IncognitoLiveActivity.swift` | — | 🚫 |
| 37 | `Moments/GlowsyWidgetExtension/WidgetColor+Hex.swift` | — | [ ] |

## Utilities

*11 archivos · 1,072 líneas*

| Líneas | Archivo iOS | Android (.kt) | Paridad |
|-------:|-------------|---------------|:-------:|
| 23 | `Moments/Moments/Utilities/ActiveWindowMetrics.swift` | `utilities/ActiveWindowMetrics.kt` | [x] 23↔60 |
| 27 | `Moments/Moments/Utilities/AppLog.swift` | `utilities/AppLog.kt` | [x] 27↔24 |
| 114 | `Moments/Moments/Utilities/EmojiUsageTracker.swift` | `utilities/EmojiUsageTracker.kt` | [x] 114↔138 |
| 154 | `Moments/Moments/Utilities/HapticManager.swift` | `utilities/HapticManager.kt` | [x] 154↔198 |
| 10 | `Moments/Moments/Utilities/LegacyTypographyScale.swift` | `utilities/LegacyTypographyScale.kt` | [x] 10↔21 |
| 137 | `Moments/Moments/Utilities/MentionParsing.swift` | `utilities/MentionParsing.kt` | [x] 137↔130 |
| 38 | `Moments/Moments/Utilities/MomentsAppearModifiers.swift` | `utilities/MomentsAppearModifiers.kt` | [x] 38↔49 |
| 81 | `Moments/Moments/Utilities/MomentsAudioSession.swift` | `utilities/MomentsAudioSession.kt` | [x] 81↔88 |
| 372 | `Moments/Moments/Utilities/MomentsFormat.swift` | `utilities/MomentsFormat.kt` | [x] 372↔437 |
| 65 | `Moments/Moments/Utilities/MomentsPressButtonStyle.swift` | `utilities/MomentsPressButtonStyle.kt` | [x] 65↔94 |
| 51 | `Moments/Moments/Utilities/OrientationManager.swift` | `utilities/OrientationManager.kt` | [x] 51↔97 |

## MomentsNotificationService

*2 archivos · 956 líneas*

| Líneas | Archivo iOS | Android (.kt) | Paridad |
|-------:|-------------|---------------|:-------:|
| 812 | `Moments/MomentsNotificationService/NotificationService.swift` | `notifications/services/MomentsFirebaseMessagingService.kt` | [x] 812↔405 |
| 144 | `Moments/MomentsNotificationService/SharedChatDecryptor.swift` | `services/messaging/SharedChatDecryptor.kt` | [x] 144↔90 |

## Extensions

*5 archivos · 678 líneas*

| Líneas | Archivo iOS | Android (.kt) | Paridad |
|-------:|-------------|---------------|:-------:|
| 1 | `Moments/Moments/Extensions/AVAssetImageGenerator+Thumbnail.swift` | `extensions/AvAssetThumbnail.kt` | [x] 1↔91 |
| 79 | `Moments/Moments/Extensions/Color+Hex.swift` | `extensions/ColorHex.kt` | [x] 79↔74 |
| 8 | `Moments/Moments/Extensions/Date+Extensions.swift` | `extensions/DateExtensions.kt` | [x] 8↔10 |
| 90 | `Moments/Moments/Extensions/InterestEmojiHelper.swift` | `extensions/InterestEmojiHelper.kt` | [x] 90↔103 |
| 500 | `Moments/Moments/Extensions/View+LiquidGlass.swift` | `extensions/LiquidGlass.kt` | [x] 500↔360 |

## Shared

*5 archivos · 380 líneas*

| Líneas | Archivo iOS | Android (.kt) | Paridad |
|-------:|-------------|---------------|:-------:|
| 137 | `Moments/Shared/ChatCommunicationIntentDonor.swift` | `services/messaging/ChatCommunicationIntentDonor.kt` | [x] 137↔211 |
| 26 | `Moments/Shared/ChatPreviewPrivacy.swift` | `views/shared/ChatPreviewPrivacy.kt` | [x] 26↔67 |
| 20 | `Moments/Shared/IncognitoActivityAttributes.swift` | — | 🚫 |
| 154 | `Moments/Shared/MessageIngestQueue.swift` | `services/messaging/MessageIngestQueue.kt` | [x] 154↔163 |
| 43 | `Moments/Shared/PauseIncognitoIntent.swift` | — | 🚫 |

## ViewModels

*1 archivos · 318 líneas*

| Líneas | Archivo iOS | Android (.kt) | Paridad |
|-------:|-------------|---------------|:-------:|
| 318 | `Moments/Moments/ViewModels/EchoViewModel.swift` | `viewmodels/EchoViewModel.kt` | [x] 318↔295 |

## MomentsApp.swift

*1 archivos · 211 líneas*

| Líneas | Archivo iOS | Android (.kt) | Paridad |
|-------:|-------------|---------------|:-------:|
| 211 | `Moments/Moments/MomentsApp.swift` | `MomentsApp.kt` (+ `MomentsApplication`) | [x] 211↔258 |

## Activities

*3 archivos · 124 líneas*

| Líneas | Archivo iOS | Android (.kt) | Paridad |
|-------:|-------------|---------------|:-------:|
| 48 | `Moments/Moments/Activities/LiveActivityThumbnailStore.swift` | `activities/LiveActivityThumbnailStore.kt` | [x] 48↔57 |
| 39 | `Moments/Moments/Activities/MomentUploadActivityAttributes.swift` | `activities/MomentUploadActivityAttributes.kt` | [x] 39↔27 |
| 37 | `Moments/Moments/Activities/StoryUploadActivityAttributes.swift` | `activities/StoryUploadActivityAttributes.kt` | [x] 37↔26 |

---

## Lista completa (orden alfabético)

| Líneas | Archivo iOS | Paridad |
|-------:|-------------|:-------:|
| 18 | `Moments/GlowsyWidgetExtension/AppIntent.swift` | [ ] |
| 492 | `Moments/GlowsyWidgetExtension/GlowsyWidgetExtension.swift` | [ ] |
| 22 | `Moments/GlowsyWidgetExtension/GlowsyWidgetExtensionBundle.swift` | [ ] |
| 74 | `Moments/GlowsyWidgetExtension/GlowsyWidgetExtensionControl.swift` | [ ] |
| 471 | `Moments/GlowsyWidgetExtension/GlowsyWidgetExtensionLiveActivity.swift` | 🚫 |
| 201 | `Moments/GlowsyWidgetExtension/IncognitoLiveActivity.swift` | 🚫 |
| 37 | `Moments/GlowsyWidgetExtension/WidgetColor+Hex.swift` | [ ] |
| 48 | `Moments/Moments/Activities/LiveActivityThumbnailStore.swift` | [x] 48↔57 |
| 39 | `Moments/Moments/Activities/MomentUploadActivityAttributes.swift` | [x] 39↔27 |
| 37 | `Moments/Moments/Activities/StoryUploadActivityAttributes.swift` | [x] 37↔26 |
| 226 | `Moments/Moments/Coordinators/AppRouter.swift` | [x] 226↔187 |
| 46 | `Moments/Moments/Coordinators/LegacyNavigationBridge.swift` | [x] 46↔58 |
| 32 | `Moments/Moments/Coordinators/MainViewModel.swift` | [x] 32↔63 |
| 407 | `Moments/Moments/Coordinators/SharedComponents.swift` | [x] 407↔427 |
| 768 | `Moments/Moments/Coordinators/TabBarView.swift` | [~] |
| 1 | `Moments/Moments/Extensions/AVAssetImageGenerator+Thumbnail.swift` | [x] 1↔91 |
| 79 | `Moments/Moments/Extensions/Color+Hex.swift` | [x] 79↔74 |
| 8 | `Moments/Moments/Extensions/Date+Extensions.swift` | [x] 8↔10 |
| 90 | `Moments/Moments/Extensions/InterestEmojiHelper.swift` | [x] 90↔103 |
| 500 | `Moments/Moments/Extensions/View+LiquidGlass.swift` | [x] 500↔360 |
| 46 | `Moments/Moments/Models/AccountHistoryItem.swift` | [x] 46↔56 |
| 683 | `Moments/Moments/Models/BestFriendsView.swift` | [ ] |
| 66 | `Moments/Moments/Models/Cache/CachedAction.swift` | [x] 66↔83 |
| 19 | `Moments/Moments/Models/Cache/CachedConnection.swift` | [x] 19↔17 |
| 166 | `Moments/Moments/Models/Cache/CachedConversation.swift` | [x] 166↔195 |
| 305 | `Moments/Moments/Models/Cache/CachedMessage.swift` | [x] 305↔395 |
| 271 | `Moments/Moments/Models/Cache/CachedMoment.swift` | [x] 271↔321 |
| 105 | `Moments/Moments/Models/Cache/CachedNotification.swift` | [x] 105↔87 |
| 20 | `Moments/Moments/Models/Cache/CachedSearch.swift` | [x] 20↔17 |
| 165 | `Moments/Moments/Models/Cache/CachedStory.swift` | [x] 165↔247 |
| 217 | `Moments/Moments/Models/Cache/CachedUser.swift` | [x] 217↔229 |
| 278 | `Moments/Moments/Models/ChatSecurityModels.swift` | [x] 278↔195 |
| 157 | `Moments/Moments/Models/EchoModels.swift` | [x] 157↔221 |
| 80 | `Moments/Moments/Models/InterestModels.swift` | [x] 80↔63 |
| 2722 ⚠️ | `Moments/Moments/Models/Models.swift` | [x] 2722↔1612 |
| 119 | `Moments/Moments/Models/OutboxPayloads.swift` | [x] 119↔224 |
| 243 | `Moments/Moments/Models/StickerItem.swift` | [x] 243↔174 |
| 677 | `Moments/Moments/Models/User.swift` | [x] 677↔269 |
| 39 | `Moments/Moments/Models/UserAffinity.swift` | [x] 39↔24 |
| 306 | `Moments/Moments/Models/UserBadge.swift` | 🚫 |
| 969 | `Moments/Moments/Models/VisitsView.swift` | [ ] |
| 512 | `Moments/Moments/Moderation/CommentsModerationService.swift` | [x] 512↔426 |
| 1972 ⚠️ | `Moments/Moments/Moderation/MediaModerationService.swift` | [x] 1972↔972 |
| 211 | `Moments/Moments/MomentsApp.swift` | [x] 211↔258 |
| 323 | `Moments/Moments/Notifications/Components/NotificationGroupedFollowersOverlay.swift` | [x] 323↔432 |
| 109 | `Moments/Moments/Notifications/Components/NotificationRowComponents.swift` | [x] 109↔221 |
| 171 | `Moments/Moments/Notifications/Components/NotificationSharedViews.swift` | [x] 171↔201 |
| 13 | `Moments/Moments/Notifications/Core/NotificationGroup.swift` | [x] 13↔11 |
| 136 | `Moments/Moments/Notifications/Core/NotificationRowSupport.swift` | [x] 136↔171 |
| 329 | `Moments/Moments/Notifications/Core/NotificationsViewModel.swift` | [x] 329↔295 |
| 196 | `Moments/Moments/Notifications/Row/EnhancedNotificationRow+Follow.swift` | [x] 196↔262 |
| 597 | `Moments/Moments/Notifications/Row/EnhancedNotificationRow+Messages.swift` | [x] 597↔582 |
| 113 | `Moments/Moments/Notifications/Row/EnhancedNotificationRow+Previews.swift` | [x] 113↔215 |
| 311 | `Moments/Moments/Notifications/Row/EnhancedNotificationRow+Trailing.swift` | [x] 311↔339 |
| 304 | `Moments/Moments/Notifications/Row/EnhancedNotificationRow.swift` | [x] 304↔292 |
| 200 | `Moments/Moments/Notifications/Screens/NotificationSummaryPopup.swift` | [x] 200↔293 |
| 629 | `Moments/Moments/Notifications/Screens/NotificationsView.swift` | [x] 629↔616 |
| 261 | `Moments/Moments/Notifications/Services/AppDelegate.swift` | [x] 261↔405 |
| 102 | `Moments/Moments/Notifications/Services/FCMTokenService.swift` | [x] 102↔99 |
| 225 | `Moments/Moments/Notifications/Services/InAppNotificationPreviewResolver.swift` | [x] 225↔146 |
| 205 | `Moments/Moments/Notifications/Services/InAppNotificationService.swift` | [x] 205↔167 |
| 228 | `Moments/Moments/Notifications/Services/NotificationBadgeService.swift` | [x] 228↔206 |
| 412 | `Moments/Moments/Notifications/Services/NotificationCopyResolver.swift` | [x] 412↔284 |
| 59 | `Moments/Moments/Notifications/Services/NotificationExtensions.swift` | [x] 59↔81 |
| 301 | `Moments/Moments/Notifications/Services/NotificationNavigationService.swift` | [x] 301↔196 |
| 32 | `Moments/Moments/Notifications/Services/NotificationOpenIntentStore.swift` | [x] 32↔31 |
| 350 | `Moments/Moments/Notifications/Services/NotificationPresentationCoordinator.swift` | [x] 350↔290 |
| 633 | `Moments/Moments/Notifications/Services/Notificationservice.swift` | [x] 633↔538 |
| 1128 ⚠️ | `Moments/Moments/Reportes/AppealFormView.swift` | [~] |
| 740 | `Moments/Moments/Reportes/AppealService.swift` | [x] 740↔827 |
| 524 | `Moments/Moments/Reportes/AppealStatus.swift` | [~] |
| 299 | `Moments/Moments/Reportes/ModerationReviewStatusView.swift` | [~] |
| 393 | `Moments/Moments/Reportes/ModernReportContent.swift` | [~] |
| 234 | `Moments/Moments/Reportes/ReportBottomSheet.swift` | [~] |
| 221 | `Moments/Moments/Reportes/UserReportContent.swift` | [~] |
| 222 | `Moments/Moments/Services/Activity/TimeSpentManager.swift` | [x] 222↔279 |
| 2571 ⚠️ | `Moments/Moments/Services/Auth/AuthService.swift` | [x] 2571↔1635 |
| 543 | `Moments/Moments/Services/Auth/LoginActivityService.swift` | [x] 543↔412 |
| 155 | `Moments/Moments/Services/Auth/OnboardingDraftStore.swift` | [x] 155↔189 |
| 272 | `Moments/Moments/Services/Auth/PasskeyService.swift` | 🚫 |
| 240 | `Moments/Moments/Services/Cache/CacheManager.swift` | [x] 240↔144 |
| 97 | `Moments/Moments/Services/Cache/ImagePrefetchManager.swift` | [x] 97↔110 |
| 94 | `Moments/Moments/Services/Cache/PersistentAudioCache.swift` | [x] 94↔90 |
| 131 | `Moments/Moments/Services/Cache/PersistentVideoCache.swift` | [x] 131↔84 |
| 135 | `Moments/Moments/Services/Cache/UserCacheService.swift` | [x] 135↔122 |
| 115 | `Moments/Moments/Services/Cache/VideoPreloader.swift` | [x] 115↔91 |
| 58 | `Moments/Moments/Services/Cache/VideoThumbnailCache.swift` | [x] 58↔71 |
| 37 | `Moments/Moments/Services/Camera/SnapCameraKitConfiguration.swift` | [x] 37↔59 |
| 692 | `Moments/Moments/Services/Content/BackendFeedService.swift` | [x] 692↔848 |
| 260 | `Moments/Moments/Services/Content/FilterService.swift` | [x] 260↔166 |
| 247 | `Moments/Moments/Services/Content/ForYouDiscoveryService.swift` | [x] 247↔168 |
| 108 | `Moments/Moments/Services/Content/ProfileVisitsService.swift` | [x] 108↔86 |
| 157 | `Moments/Moments/Services/Firestore/FirestoreActivityRepository.swift` | [x] 157↔106 |
| 167 | `Moments/Moments/Services/Firestore/FirestoreAudienceRepository.swift` | [x] 167↔159 |
| 807 | `Moments/Moments/Services/Firestore/FirestoreCommentsRepository.swift` | [x] 807↔545 |
| 163 | `Moments/Moments/Services/Firestore/FirestoreCore.swift` | [x] 163↔183 |
| 377 | `Moments/Moments/Services/Firestore/FirestoreHiddenLayersRepository.swift` | [x] 377↔194 |
| 988 | `Moments/Moments/Services/Firestore/FirestoreMomentsRepository.swift` | [x] 988↔524 |
| 473 | `Moments/Moments/Services/Firestore/FirestoreProfilesRepository.swift` | [x] 473↔248 |
| 306 | `Moments/Moments/Services/Firestore/FirestoreSearchRepository.swift` | [x] 306↔144 |
| 1730 ⚠️ | `Moments/Moments/Services/Firestore/FirestoreService.swift` | [x] 1746↔876 |
| 1179 ⚠️ | `Moments/Moments/Services/Firestore/FirestoreStoriesRepository.swift` | [x] 1179↔627 |
| 414 | `Moments/Moments/Services/Incognito/IncognitoModeService.swift` | [x] 414↔389 |
| 442 | `Moments/Moments/Services/Messaging/ChatCacheStore.swift` | [x] 442↔345 |
| 24 | `Moments/Moments/Services/Messaging/ChatCommunicationNotificationService.swift` | [x] 24↔28 |
| 183 | `Moments/Moments/Services/Messaging/ChatMediaChunkedCipher.swift` | [x] 183↔196 |
| 118 | `Moments/Moments/Services/Messaging/ChatMediaDownloadPolicy.swift` | [x] 118↔98 |
| 66 | `Moments/Moments/Services/Messaging/ChatMediaPrefetcher.swift` | [x] 66↔82 |
| 51 | `Moments/Moments/Services/Messaging/ChatRecoveryCrypto.swift` | [x] 51↔44 |
| 54 | `Moments/Moments/Services/Messaging/ChatSendMessageIntentHandler.swift` | 🚫 |
| 3108 ⚠️ | `Moments/Moments/Services/Messaging/EncryptionService.swift` | [x] 3108↔1156 |
| 16 | `Moments/Moments/Services/Messaging/LocalFirstMessagingSettings.swift` | [x] 16↔49 |
| 150 | `Moments/Moments/Services/Messaging/MessageCatchUpService.swift` | [x] 150↔122 |
| 270 | `Moments/Moments/Services/Messaging/MessageIngestService.swift` | [x] 270↔221 |
| 660 | `Moments/Moments/Services/Messaging/MessageRequestService.swift` | [x] 660↔376 |
| 332 | `Moments/Moments/Services/Messaging/OnlineStatusService.swift` | [x] 332↔284 |
| 58 | `Moments/Moments/Services/Messaging/VanishMessageTimer.swift` | [x] 58↔51 |
| 87 | `Moments/Moments/Services/Network/NetworkMonitor.swift` | [x] 87↔159 |
| 635 | `Moments/Moments/Services/Network/OfflineSyncService.swift` | [x] 635↔572 |
| 117 | `Moments/Moments/Services/Nova/NovaEmbeddingService.swift` | [x] 117↔67 |
| 96 | `Moments/Moments/Services/Performance/FeedVisibilityCoordinator.swift` | [x] 96↔67 |
| 88 | `Moments/Moments/Services/Performance/MotionPolicy.swift` | [x] 88↔80 |
| 24 | `Moments/Moments/Services/Performance/PerformanceSignposts.swift` | [x] 24↔25 |
| 20 | `Moments/Moments/Services/Performance/VideoMomentsIndex.swift` | [x] 20↔52 |
| 2614 ⚠️ | `Moments/Moments/Services/Persistence/LocalPersistenceService.swift` | [x] 2614↔1701 |
| 277 | `Moments/Moments/Services/Persistence/MessagePersistenceStore.swift` | [x] 277↔465 |
| 278 | `Moments/Moments/Services/Privacy/ContentVisibilityservice.swift` | [x] 278↔176 |
| 1424 ⚠️ | `Moments/Moments/Services/Privacy/PrivacyService.swift` | [x] 1424↔738 |
| 45 | `Moments/Moments/Services/Privacy/PrivacyServiceExtension.swift` | [x] 45↔30 |
| 15 | `Moments/Moments/Services/Security/MomentsAppCheckProviderFactory.swift` | [x] 15↔43 |
| 213 | `Moments/Moments/Services/Social/AffinityTracker.swift` | [x] 213↔164 |
| 149 | `Moments/Moments/Services/Social/BestFriendsService.swift` | [x] 149↔87 |
| 592 | `Moments/Moments/Services/Social/EchoService.swift` | [x] 592↔435 |
| 193 | `Moments/Moments/Services/Social/StoryChainLimitsService.swift` | [x] 193↔147 |
| 297 | `Moments/Moments/Services/Social/StoryRingCacheService.swift` | [x] 297↔230 |
| 286 | `Moments/Moments/Services/Storage/MediaUploadService.swift` | [x] 286↔234 |
| 217 | `Moments/Moments/Services/Storage/StoragePathBuilder.swift` | [x] 217↔249 |
| 344 | `Moments/Moments/Services/Storage/StorageService.swift` | [x] 344↔302 |
| 51 | `Moments/Moments/Services/Storage/UIImage+StorageUpload.swift` | [x] 51↔40 |
| 114 | `Moments/Moments/Services/Storage/VideoCompressionService.swift` | [x] 114↔212 |
| 45 | `Moments/Moments/Services/Video/ReelPrebufferService.swift` | [x] 45↔64 |
| 96 | `Moments/Moments/Services/Video/SharedVideoPlayerPool.swift` | [x] 96↔106 |
| 132 | `Moments/Moments/Services/Video/VideoAdaptivePlayback.swift` | [x] 132↔144 |
| 194 | `Moments/Moments/Services/Video/VideoPlaybackSelector.swift` | [x] 194↔125 |
| 23 | `Moments/Moments/Utilities/ActiveWindowMetrics.swift` | [x] 23↔60 |
| 27 | `Moments/Moments/Utilities/AppLog.swift` | [x] 27↔24 |
| 114 | `Moments/Moments/Utilities/EmojiUsageTracker.swift` | [x] 114↔138 |
| 154 | `Moments/Moments/Utilities/HapticManager.swift` | [x] 154↔198 |
| 10 | `Moments/Moments/Utilities/LegacyTypographyScale.swift` | [x] 10↔21 |
| 137 | `Moments/Moments/Utilities/MentionParsing.swift` | [x] 137↔130 |
| 38 | `Moments/Moments/Utilities/MomentsAppearModifiers.swift` | [x] 38↔49 |
| 81 | `Moments/Moments/Utilities/MomentsAudioSession.swift` | [x] 81↔88 |
| 372 | `Moments/Moments/Utilities/MomentsFormat.swift` | [x] 372↔437 |
| 65 | `Moments/Moments/Utilities/MomentsPressButtonStyle.swift` | [x] 65↔94 |
| 51 | `Moments/Moments/Utilities/OrientationManager.swift` | [x] 51↔97 |
| 318 | `Moments/Moments/ViewModels/EchoViewModel.swift` | [x] 318↔295 |
| 87 | `Moments/Moments/Views/Components/AnimatedStickerView.swift` | [x] 87↔71 |
| 74 | `Moments/Moments/Views/Components/AudienceIconView.swift` | [x] 74↔110 |
| 46 | `Moments/Moments/Views/Components/CommentRowSkeletonView.swift` | [x] 46↔85 |
| 75 | `Moments/Moments/Views/Components/EchoesIconView.swift` | [x] 75↔107 |
| 87 | `Moments/Moments/Views/Components/HiddenLayers/HiddenLayerLayout.swift` | [x] 87↔93 |
| 405 | `Moments/Moments/Views/Components/InAppBannerView.swift` | [x] 405↔535 |
| 148 | `Moments/Moments/Views/Components/InAppMessageQuickReplyPanel.swift` | [x] 148↔270 |
| 64 | `Moments/Moments/Views/Components/IntelligentGlow.swift` | [x] 64↔165 |
| 2012 ⚠️ | `Moments/Moments/Views/Components/InteractiveStickerSharedViews.swift` | [x] 2012↔2417 |
| 60 | `Moments/Moments/Views/Components/LiveUsernameText.swift` | [x] 60↔87 |
| 50 | `Moments/Moments/Views/Components/LocationMomentCardSkeletonView.swift` | [x] 50↔88 |
| 408 | `Moments/Moments/Views/Components/MomentCaptionView.swift` | [x] 408↔598 |
| 205 | `Moments/Moments/Views/Components/MomentHashtagText.swift` | [x] 205↔97 |
| 295 | `Moments/Moments/Views/Components/MomentRailComponents.swift` | [x] 295↔300 |
| 191 | `Moments/Moments/Views/Components/MomentRefresh.swift` | [x] 191↔201 |
| 81 | `Moments/Moments/Views/Components/MomentRowButton.swift` | [x] 81↔90 |
| 202 | `Moments/Moments/Views/Components/OfflineBanner.swift` | [x] 202↔206 |
| 53 | `Moments/Moments/Views/Components/RefreshControl.swift` | [x] 53↔127 |
| 36 | `Moments/Moments/Views/Components/SkeletonShimmer.swift` | [x] 36↔28 |
| 46 | `Moments/Moments/Views/Components/StoryViewerSkeletonView.swift` | [x] 46↔42 |
| 47 | `Moments/Moments/Views/Components/UserRowSkeletonView.swift` | [x] 47↔45 |
| 138 | `Moments/Moments/Views/Components/VerifiedBadge.swift` | [x] 138↔134 |
| 162 | `Moments/Moments/Views/Creator/AudienceSelector/AudienceModels.swift` | [x] 162↔183 |
| 372 | `Moments/Moments/Views/Creator/AudienceSelector/AudienceSelectionRows.swift` | [x] 372↔506 |
| 2170 ⚠️ | `Moments/Moments/Views/Creator/AudienceSelector/AudienceSelectionView.swift` | [x] 2170↔1723 |
| 563 | `Moments/Moments/Views/Creator/AudienceSelector/CustomAudienceManagementViews.swift` | [x] 563↔722 |
| 152 | `Moments/Moments/Views/Creator/AudienceSelector/CustomListSelectorView.swift` | [x] 152↔249 |
| 1481 ⚠️ | `Moments/Moments/Views/Creator/BackgroundMomentUploadService.swift` | [x] 1481↔1154 |
| 2419 ⚠️ | `Moments/Moments/Views/Creator/BackgroundStoryUploadService.swift` | [x] 2419↔1504 · Live Activity 🚫 · UIKit layout helpers N/A |
| 345 | `Moments/Moments/Views/Creator/CameraKit/CameraKitSpike.swift` | [x] 345↔210 · contrato+stubs; SDK Snap 🚫 (flag off = iOS) |
| 275 | `Moments/Moments/Views/Creator/CameraKit/LensReel.swift` | [x] 275↔179 · snap+α/scale+shutter; lentes vía flag |
| 347 | `Moments/Moments/Views/Creator/ChainConfigurationView.swift` | [x] 347↔399 · strings 8 locales + flow AnimatedContent |
| 568 | `Moments/Moments/Views/Creator/ChainContinuationSelectorView.swift` | [x] 568↔664 · nested create/edit/manage + loadUsers |
| 88 | `Moments/Moments/Views/Creator/Components/CaptureButton.swift` | [x] 88↔119 |
| 535 | `Moments/Moments/Views/Creator/Components/EditableImageView.swift` | [x] 535↔494 |
| 49 | `Moments/Moments/Views/Creator/Components/StickerDetailPalette.swift` | [x] 49↔27 |
| 315 | `Moments/Moments/Views/Creator/Components/StickerGiphyViews.swift` | [x] 315↔371 |
| 1348 ⚠️ | `Moments/Moments/Views/Creator/Components/StickerInputViews.swift` | [x] 1348↔1177 · detail=Mention/Link; resto=insertInstant |
| 670 | `Moments/Moments/Views/Creator/Components/StickerLocationInputView.swift` | [x] 670↔724 |
| 397 | `Moments/Moments/Views/Creator/Components/StickerMediaInputs.swift` | [x] 397↔538 |
| 343 | `Moments/Moments/Views/Creator/Components/StickerPickerGeneratedStickers.swift` | [x] 343↔153 |
| 152 | `Moments/Moments/Views/Creator/Components/StickerPickerLayout.swift` | [x] 152↔177 |
| 198 | `Moments/Moments/Views/Creator/Components/StickerPickerSupportExtensions.swift` | [x] 198↔217 |
| 42 | `Moments/Moments/Views/Creator/Components/StoryBackgroundPresets.swift` | [x] 42↔44 |
| 130 | `Moments/Moments/Views/Creator/Components/StoryColorPickerView.swift` | [x] 130↔241 |
| 100 | `Moments/Moments/Views/Creator/Components/StoryDominantColorsExtractor.swift` | [x] 100↔82 |
| 914 | `Moments/Moments/Views/Creator/Components/StoryDrawingEditorOverlay.swift` | [x] 914↔911 |
| 111 | `Moments/Moments/Views/Creator/Components/StoryEditingControls.swift` | [x] 111↔142 |
| 459 | `Moments/Moments/Views/Creator/Components/StoryEditorTextTypes.swift` | [x] 459↔358 |
| 96 | `Moments/Moments/Views/Creator/Components/StoryFilterSelectorView.swift` | [x] 96↔166 |
| 76 | `Moments/Moments/Views/Creator/Components/StoryFontRegistry.swift` | [x] 76↔91 |
| 206 | `Moments/Moments/Views/Creator/Components/StoryTextAttributesBuilder.swift` | [x] 206↔298 |
| 598 | `Moments/Moments/Views/Creator/Components/StoryTextEditorChrome.swift` | [x] 598↔816 |
| 89 | `Moments/Moments/Views/Creator/Components/StoryTextGradientSettings.swift` | [x] 89↔60 |
| 243 | `Moments/Moments/Views/Creator/Components/StoryTextMotionEngine.swift` | [x] 243↔211 |
| 1109 ⚠️ | `Moments/Moments/Views/Creator/Components/StoryTextOverlayLabel.swift` | [x] 1109↔673 |
| 427 | `Moments/Moments/Views/Creator/Components/StoryTextOverlayMetadata.swift` | [x] 427↔347 |
| 99 | `Moments/Moments/Views/Creator/Components/StoryTextVisualRenderer.swift` | [x] 99↔112 |
| 240 | `Moments/Moments/Views/Creator/Components/StoryVideoPlayerView.swift` | [x] 240↔236 |
| 204 | `Moments/Moments/Views/Creator/CreatorScreens/AlbumPickerView.swift` | [x] 204↔237 · canvas sólido AdaptiveColors |
| 845 | `Moments/Moments/Views/Creator/CreatorScreens/CaptionAndDetailsView.swift` | [x] 845↔859 · canvas sólido AdaptiveColors |
| 443 | `Moments/Moments/Views/Creator/CreatorScreens/ContentTypeSelectionView.swift` | [x] 443↔470 |
| 66 | `Moments/Moments/Views/Creator/CreatorScreens/FilterOption.swift` | [x] 66↔115 |
| 570 | `Moments/Moments/Views/Creator/CreatorScreens/LocationPickerView.swift` | [x] 570↔726 · mapa+gate+nearby; Localizable |
| 401 | `Moments/Moments/Views/Creator/CreatorScreens/MediaEditingView.swift` | [x] 401↔546 · crop sheet; ratio recomendado; fondo sólido |
| 103 | `Moments/Moments/Views/Creator/CreatorScreens/MediaGridCell.swift` | [x] 103↔179 |
| 898 | `Moments/Moments/Views/Creator/CreatorScreens/MediaSelectionView.swift` | [x] 898↔716 · gate+camera boundary; album sheet |
| 1199 ⚠️ | `Moments/Moments/Views/Creator/CreatorScreens/StickerOverlayView.swift` | [x] 1199↔493 · geom+frame+selfie; chip tipado aparte |
| 535 | `Moments/Moments/Views/Creator/CreatorScreens/StoryCameraView.swift` | [x] 535↔653 · captureRect dp; shutter≠galería/flip; Aa; lenses stub |
| 883 | `Moments/Moments/Views/Creator/CreatorScreens/StoryOverlaysView.swift` | [x] 883↔~450 · toast+cycle+reveal+polaroid+drawing; orq. storyeditor |
| 681 | `Moments/Moments/Views/Creator/CreatorScreens/StoryTextEditor.swift` | [x] 681↔~460 · IME+Done+eyedropper; UIKit legacy N/A |
| 224 | `Moments/Moments/Views/Creator/CreatorScreens/UserSearchView.swift` | [x] 224↔~280 · Localizable+search UI |
| 368 | `Moments/Moments/Views/Creator/CreatorSharedModels.swift` | [x] 368↔314 · media+layout+GlowSharePill+limits; blur view API (canvas sólido en screens) |
| 72 | `Moments/Moments/Views/Creator/CreatorUIKit/BackgroundCameraView.swift` | [x] 72↔115 · back preview FILL; StopBackgroundCameraSession; fondo negro |
| 77 | `Moments/Moments/Views/Creator/CreatorUIKit/CameraCapture.swift` | [x] 77↔276 · TakePicture/CaptureVideo; 60s+quality; dismiss cancel; auto 1 tipo |
| 739 | `Moments/Moments/Views/Creator/CreatorUIKit/CameraPreviewView.swift` | [x] 739↔254 · ViewPort crop; quality; zoom 0.1; Center Stage stub false |
| 110 | `Moments/Moments/Views/Creator/CreatorUIKit/CreatorCaptureGeometry.swift` | [x] 110↔91 · insets dp≡pt; density en captureRect |
| 18 | `Moments/Moments/Views/Creator/CreatorUIKit/CreatorControls.swift` | [x] 18↔47 · ToolIconButton chrome+stroke+haptic+18dp |
| 11 | `Moments/Moments/Views/Creator/CreatorUIKit/CreatorUIImageExtensions.swift` | [x] 11↔63 · creatorNormalizedUp + EXIF Uri |
| 99 | `Moments/Moments/Views/Creator/CreatorUIKit/CropViewWrapper.swift` | [x] 99↔377 · Fit+pan crop; free cycle preset; rotate/reset; default lock |
| 404 | `Moments/Moments/Views/Creator/CreatorUIKit/DrawingView.swift` | [x] 404↔58 · fachada StoryDrawingEditorOverlay; Crop+dim; Done→dismiss; 0 call sites iOS |
| 377 | `Moments/Moments/Views/Creator/CreatorUIKit/StoryGalleryPicker.swift` | [x] 377↔370 · split/trim/tooLong Localizable; 9:16; constants servicio |
| 115 | `Moments/Moments/Views/Creator/CreatorUIKit/StoryMediaPicker.swift` | [x] 115↔~120 · PickVisualMedia 1 ítem; content:// directo; cache solo si export falla |
| 1227 ⚠️ | `Moments/Moments/Views/Creator/CreatorView.swift` | [x] orquestador + Reveal*; dead ModernSelection/Guide (0 call sites) |
| 2170 ⚠️ | `Moments/Moments/Views/Creator/HiddenLayersEditorView.swift` | [x] Paridad editor; audioPreviewCard waveform no portado (casi dead en dock iOS) |
| 418 | `Moments/Moments/Views/Creator/PhotoTagSelectionView.swift` | [x] |
| 374 | `Moments/Moments/Views/Creator/StickerEmojiPalettePicker.swift` | [x] catalog ICU+extras+skin; grid 7; tray long-press; cableado ModernEmojiSliderInputView |
| 128 | `Moments/Moments/Views/Creator/StoryVideoProcessingService.swift` | [x] duration/export/split/thumb; 720p; errors Localizable×8; thumbnailUri |
| 558 | `Moments/Moments/Views/Creator/StoryVideoTrimEditorView.swift` | [x] Nitidez light/dark; handles+playhead; strings×8; timeline 10f |
| 1925 ⚠️ | `Moments/Moments/Views/Creator/VideoEditor.swift` | [x] SocialVideoEditorView: export format-aware + metadatos; Nitidez; timeline handles; glass pickers; cover FS; trim/speed/vol preview-only (=iOS) |
| 2081 ⚠️ | `Moments/Moments/Views/Creator/stickerview.swift` | [x] picker completo; cards Compose en storyeditor (=bake UIKit); press+GIF4+pills accent+limits |
| 3248 ⚠️ | `Moments/Moments/Views/Creator/storyeditor.swift` | [x] 3248↔3705 · bake imagen+vídeo paleta, chat/cadena, EmojiPicker, Done/palette sticker, publish dismiss |
| 362 | `Moments/Moments/Views/Echoes/EchoHistoryView.swift` | [x] 362↔399 · fullscreen EchoViewerUI + info sheet |
| 232 | `Moments/Moments/Views/Echoes/EchoInvitationView.swift` | [x] 232↔324 · listener echoes/ + accept/decline |
| 736 | `Moments/Moments/Views/Echoes/EchoViewerUI.swift` | [x] 736↔853 · overlays glass + leave menu + mapa FS |
| 553 | `Moments/Moments/Views/Explore/ExploreGridLayout.swift` | [x] 553↔427 · bento mosaic + thumbnail chrome |
| 479 | `Moments/Moments/Views/Explore/ExploreMomentDetailView.swift` | [x] 479↔542 · feed scroll + dismiss + overlays |
| 402 | `Moments/Moments/Views/Explore/ExploreSections/ExploreResultsSection.swift` | [x] 402↔689 · SmartSearch + MiniUser + Recent + SearchResultCard |
| 622 | `Moments/Moments/Views/Explore/ExploreSections/ExploreSuggestionsSection.swift` | [x] 622↔659 · cards bg+blur, loading/error, SearchBar, FollowButton |
| 457 | `Moments/Moments/Views/Explore/ExploreView.swift` | [x] 457↔~560 · profile UserProfileView + MomentZoomDetailDestination explorer/single |
| 919 | `Moments/Moments/Views/Explore/ExploreViewModel.swift` | [x] 919↔509 · follow/request+pending+search detect; connections+notifications |
| 262 | `Moments/Moments/Views/Explore/ModernExploreDetailHeader.swift` | [x] 262↔~310 · glass pill + follow/unfollow + live username |
| 1547 ⚠️ | `Moments/Moments/Views/Explore/MomentDetailView.swift` | [N/A] dead code iOS · 0 call sites · usar ExploreMomentDetailView |
| 614 | `Moments/Moments/Views/Explore/SuggestedUsersView.swift` | [x] 614↔360+186 · VM real + rows + infinite scroll + refresh |
| 138 | `Moments/Moments/Views/Feed/Controls/FeedTypeSelector.swift` | [x] 138↔~130 · FloatingGlass wrap-content + pill (no full-width) |
| 467 | `Moments/Moments/Views/Feed/Controls/feedchange.swift` | [x] 467↔534 · FeedType+prefs+Expandable/Compact/Segmented/HeaderChip |
| 171 | `Moments/Moments/Views/Feed/Core/FeedNotificationRoutingModifier.swift` | [x] 171↔169 · lifecycle+pendingNav+EventBus; cableado FeedView |
| 215 | `Moments/Moments/Views/Feed/Core/FeedPresentationModifier.swift` | [x] 215↔394 · todas destinations reales (notif/msg/stories/comments/explore/map/edit/profile/echo) |
| 21 | `Moments/Moments/Views/Feed/Core/FeedRoutes.swift` | [x] 21↔28 · Profile/Echo/Story routes |
| 608 | `Moments/Moments/Views/Feed/Core/FeedView.swift` | [x] 608↔~820 · load/refresh+messaging+notif primer 20s+prefetch; storyChain UI fuera (=iOS) |
| 1300 ⚠️ | `Moments/Moments/Views/Feed/Core/FeedViewModel.swift` | [x] 1300↔1323 · dual cache+backend/legacy+privacy listeners+mute TTL |
| 127 | `Moments/Moments/Views/Feed/Core/ModernEmptyFeedView.swift` | [x] 127↔160 · empty following/forYou + glass CTA → Explore |
| 220 | `Moments/Moments/Views/Feed/Core/Sections/FeedHeaderSection.swift` | [x] 220↔~370 · re-audit: skeletonRow+loadingTail+YourStory first+loadMore(index) |
| 240 | `Moments/Moments/Views/Feed/Core/Sections/FeedListSection.swift` | [x] 240↔~290 · re-audit: heights+tagTap+prefetch mediaItems+scrollEdgeChrome |
| 1894 ⚠️ | `Moments/Moments/Views/Feed/Core/Sections/FeedMomentComponents.swift` | [x] buttons+PostCard+detect+MediaItem+CroppedVideo+Expandable+peek · LoadingMore en skeleton |
| 97 | `Moments/Moments/Views/Feed/Core/Sections/FeedMomentDetailRoute.swift` | [x] 97↔210 · notif→detail+loading/error |
| 113 | `Moments/Moments/Views/Feed/Core/Sections/FeedOverlaysSection.swift` | [x] 113↔~170 · re-audit: peek shape+transitions+editedContent |
| 46 | `Moments/Moments/Views/Feed/Core/Sections/FeedPostSkeletonView.swift` | [x] 46↔~180 · skeleton+breathing LoadingMore |
| 508 | `Moments/Moments/Views/Feed/Core/Sections/FeedStoryRingComponents.swift` | [x] 508↔~650 · rings+retry+LiveUsername+overlay anim |
| 172 | `Moments/Moments/Views/Feed/Moments/ClickableHashtagsView.swift` | [x] View+HStack+parse+FeedFlowLayout VStack |
| 1128 ⚠️ | `Moments/Moments/Views/Feed/Moments/HiddenLayersOverlayView.swift` | [x] overlay+focus+locked+text/audio/image+discovery+hints |
| 136 | `Moments/Moments/Views/Feed/Moments/MomentCarouselLayoutRules.swift` | [x] rules+indicators+FeedMomentCardLayout+scaledRadius |
| 1077 ⚠️ | `Moments/Moments/Views/Feed/Reactions/MomentReactionButton.swift` | [x] 1077↔1195 · EpicButton+picker+particles+sheet+listener |
| 539 | `Moments/Moments/Views/Feed/Reactions/reacciones.swift` | [x] 539↔377 · types+tracker+Modern+Picker; FS helpers |
| 171 | `Moments/Moments/Views/Feed/Sharing/ShareMomentSticker.swift` | [x] 171↔241 |
| 644 | `Moments/Moments/Views/Feed/Sharing/StoryShare.swift` | [x] 644↔478 |
| 1863 ⚠️ | `Moments/Moments/Views/Feed/Sharing/share.swift` | [x] 1863↔2045 |
| 673 | `Moments/Moments/Views/Feed/Stories/FeedStoryRingCoordinator.swift` | [x] 673↔463 |
| 116 | `Moments/Moments/Views/Feed/Stories/StoryRingTraySkeleton.swift` | [x] 116↔135 |
| 241 | `Moments/Moments/Views/Feed/Uploads/FeedUploadProgressRow.swift` | [x] 241↔449 |
| 667 | `Moments/Moments/Views/Feed/Uploads/FloatingMomentUploadOverlay.swift` | [x] 667↔798 |
| 29 | `Moments/Moments/Views/Feed/Uploads/StoryUploadProgressManager.swift` | [x] 29↔80 |
| 65 | `Moments/Moments/Views/Feed/Video/LiveVideoTimeLabel.swift` | [x] 65↔94 |
| 1608 ⚠️ | `Moments/Moments/Views/Feed/Video/Reels.swift` | [x] ReelsViewer+ReelVideoView+Manager |
| 23 | `Moments/Moments/Views/Feed/Video/VideoFeedProgressBar.swift` | [x] 23↔41 |
| 55 | `Moments/Moments/Views/Feed/Video/VideoPlaybackChromeStyle.swift` | [x] 55↔116 |
| 1144 ⚠️ | `Moments/Moments/Views/Feed/Video/VideoPlayer.swift` | [x] 1144↔1296 (split GlobalVideoManager) |
| 27 | `Moments/Moments/Views/Feed/Video/VideoPosterOverlay.swift` | [x] 27↔73 |
| 938 | `Moments/Moments/Views/Feed/maps/DiscoverMapView.swift` | [x] 938↔1165 |
| 1640 ⚠️ | `Moments/Moments/Views/Feed/maps/LocationMomentDetailView.swift` | [x] 1640↔1537 |
| 107 | `Moments/Moments/Views/Feed/maps/MapAnnotationModels.swift` | [x] 107↔90 |
| 246 | `Moments/Moments/Views/Feed/maps/MapDiscoverSupport.swift` | [x] 246↔282 |
| 616 | `Moments/Moments/Views/Feed/maps/MapLocationServices.swift` | [x] 616↔557 |
| 465 | `Moments/Moments/Views/Feed/maps/MapPlaceBottomSheet.swift` | [x] 465↔551 |
| 313 | `Moments/Moments/Views/Feed/maps/MapPlaceClusterEngine.swift` | [x] 313↔364 |
| 117 | `Moments/Moments/Views/Feed/maps/MapPlaceStoryDeck.swift` | [x] 117↔115 |
| 318 | `Moments/Moments/Views/Feed/maps/MapWeatherEffects.swift` | [x] 318↔297 |
| 1985 ⚠️ | `Moments/Moments/Views/Feed/maps/Maps.swift` | [x] 1985↔1605 |
| 680 | `Moments/Moments/Views/Feed/maps/MapsSections/MapBottomSheetSection.swift` | [x] 680↔643 |
| 538 | `Moments/Moments/Views/Feed/maps/MapsSections/MapCanvasSection.swift` | [x] 537↔522 |
| 445 | `Moments/Moments/Views/Feed/maps/WeatherService.swift` | [x] 445↔217 |
| 364 | `Moments/Moments/Views/Login/AuthUIComponents.swift` | [~] |
| 123 | `Moments/Moments/Views/Login/CreatingProfileView.swift` | [~] |
| 326 | `Moments/Moments/Views/Login/DeactivatedAccountView.swift` | [~] |
| 1 | `Moments/Moments/Views/Login/Interestview.swift` | 🚫 |
| 475 | `Moments/Moments/Views/Login/LiquidGlassComponents.swift` | [~] |
| 1153 ⚠️ | `Moments/Moments/Views/Login/LoginView.swift` | [~] |
| 100 | `Moments/Moments/Views/Login/PrivacyPolicyView.swift` | [~] |
| 1255 ⚠️ | `Moments/Moments/Views/Login/ProfileOnboardingView.swift` | [~] |
| 17 | `Moments/Moments/Views/Login/RegisterView.swift` | [~] |
| 10 | `Moments/Moments/Views/Login/SocialProfileCompletionView.swift` | [ ] bug Auth sin username |
| 103 | `Moments/Moments/Views/Login/SplashScreen.swift` | [x] 103↔84 · canvas 0B1215/FAF9F6+logo dark/light+shadow AuthColors; reduceMotion; Δ MinimalSplash no en flujo |
| 662 | `Moments/Moments/Views/Login/SuspendedAccount.swift` | [~] |
| 395 | `Moments/Moments/Views/Messaging/Attachments/ChatGiphyPickerSheet.swift` | [~] · load-more solo último ítem |
| 370 | `Moments/Moments/Views/Messaging/Attachments/ChatLocationSheet.swift` | [~] · LocationPermissionGate ALWAYS + errors |
| 367 | `Moments/Moments/Views/Messaging/Components/AttachmentIconView.swift` | [x] 367↔225 · enum+metrics+presets+resolvedSize |
| 146 | `Moments/Moments/Views/Messaging/Components/ChatAdaptiveColors.swift` | [x] 146↔70 · locals+extension colors; blue=#007AFF |
| 1113 ⚠️ | `Moments/Moments/Views/Messaging/Components/ChatAttachmentSheet.swift` | [x] 1113↔952 · pickers+menu+photos; fling predicted+PermissionPrimer+popover gap+search glass |
| 145 | `Moments/Moments/Views/Messaging/Components/ChatBuzzEffectViews.swift` | [x] 145↔~160 · toast+timeline+shake; icon gradient |
| 902 | `Moments/Moments/Views/Messaging/Components/ChatChromeViews.swift` | [~] · typing/FAB reduceMotion wired via MotionPolicy |
| 1293 ⚠️ | `Moments/Moments/Views/Messaging/Components/ChatClusterMediaViews.swift` | [~] · fan+gallery masonry/select/delete; detail push host no portado |
| 337 | `Moments/Moments/Views/Messaging/Components/ChatEphemeralMessageViews.swift` | [x] 337↔389 · tap/image/resolving/expired; blur+borders; hydrate; markViewed; Spring.toggle |
| 112 | `Moments/Moments/Views/Messaging/Components/ChatFloatingNavigationOverlay.swift` | [x] 112↔~180 · resolve+search appear+shadow+AnimatedVisibility |
| 95 | `Moments/Moments/Views/Messaging/Components/ChatGifMessageBubble.swift` | [x] 95↔117 |
| 801 | `Moments/Moments/Views/Messaging/Components/ChatInputViews.swift` | [x] 801↔706 · vanish+fill sólido floating/locked+held+trim; Δ no aurora/glass iOS |
| 66 | `Moments/Moments/Views/Messaging/Components/ChatKFImageViews.swift` | [x] 66↔89 |
| 606 | `Moments/Moments/Views/Messaging/Components/ChatLocationMessageBubble.swift` | [~] · bubble+detail+avatar pin+countdown; MK snapshot→GoogleMap |
| 502 | `Moments/Moments/Views/Messaging/Components/ChatMediaViews.swift` | [x] 502↔565 · image/video+download+players; downsample; drag px; BlurView≈sólido |
| 838 | `Moments/Moments/Views/Messaging/Components/ChatMessageBubbleViews.swift` | [x] 838↔669 · row swipe/chrome+AttachBubbleBadges; texto sin double overlay; link preview; Δ≈LPLink |
| 28 | `Moments/Moments/Views/Messaging/Components/ChatMessageForwardSheet.swift` | [x] 28↔151 |
| 379 | `Moments/Moments/Views/Messaging/Components/ChatMessageInteractionModifiers.swift` | [x] 379↔414 · pan scroll-friendly+spring return+haptics+longPress 0.42+wrapContentHeight |
| 1708 ⚠️ | `Moments/Moments/Views/Messaging/Components/ChatMessageListView.swift` | [~] · contrato apply/normalize/scroll/force/pending/suppress+frames; sin UIKit vanish/heightCache |
| 535 | `Moments/Moments/Views/Messaging/Components/ChatMessageOptionsMenu.swift` | [x] 535↔544 · anchor window→local+safeArea+press chrome+haptic menu+cornerRadius+star.slash≈StarBorder |
| 882 | `Moments/Moments/Views/Messaging/Components/ChatMessageSupportViews.swift` | [x] 882↔693 · reply/quote/reactions+cutout Clear+star+timestamp; status twin checks; clusterHitTargetInset |
| 781 | `Moments/Moments/Views/Messaging/Components/ChatRecoveryViews.swift` | [x] 781↔800 · gate/create/restore/settings+PIN 48×60+lockout; material→sólido; change PIN MomentsModalSheet; lock gradient+press |
| 35 | `Moments/Moments/Views/Messaging/Components/ChatSearchNavigationBar.swift` | [x] 35↔54 |
| 432 | `Moments/Moments/Views/Messaging/Components/ChatSpeechBubbleViews.swift` | [x] 432↔500 · shape+spoilers+markdown inline+links underline+search diacríticos+gutter |
| 46 | `Moments/Moments/Views/Messaging/Components/ChatStickerMessageBubble.swift` | [x] 46↔66 |
| 620 | `Moments/Moments/Views/Messaging/Components/ChatVanishModeViews.swift` | [x] 620↔762 · metrics/overlay/notices/timer MomentsModalSheet+inbox; Δ liquidGlass→sólido; medium-only≈medium+large |
| 279 | `Moments/Moments/Views/Messaging/Components/ConversationContextMenu.swift` | [x] 279↔355 · cutout Clear+chrome+layout+MomentRowButton.menu; pin.slash≈slash overlay; systemBars insets; row highlight 0.96 |
| 84 | `Moments/Moments/Views/Messaging/Components/MediaProgressRing.swift` | [x] 84↔58 |
| 38 | `Moments/Moments/Views/Messaging/Components/MessageTypeIconView.swift` | [x] 38↔54 |
| 186 | `Moments/Moments/Views/Messaging/Components/MessagingComposerAndStatusViews.swift` | [~] · composer gradient+campo+send; status MomentsModalSheet medium+large+dividers |
| 287 | `Moments/Moments/Views/Messaging/Components/ViewOnceMessageBubble.swift` | [x] 287↔268 · pills+progress; estado desde message; zoom matchedTransition stub |
| 1121 ✅ | `Moments/Moments/Views/Messaging/Components/VoiceNotes.swift` | [x] · recorder/compose/trim+audio bubble scrub/speed/shape+proximidad auricular |
| 535 | `Moments/Moments/Views/Messaging/Components/VoiceRecordingGestureViews.swift` | [~] · fases hold/lock/cancel+follow goma+ticks+chrome; aura dual; VoiceBlob/AuroraMesh→brush stub |
| 520 | `Moments/Moments/Views/Messaging/Screens/Chat/GlassmorphicChatView+Voice.swift` | [~] · hold-to-record wired |
| 3520 ⚠️ | `Moments/Moments/Views/Messaging/Core/ChatViewModel.swift` | [x] 3520↔2284 |
| 49 | `Moments/Moments/Views/Messaging/Core/MessageItem.swift` | [x] 49↔67 |
| 2514 ⚠️ | `Moments/Moments/Views/Messaging/Core/MessageModel.swift` | [x] 2514↔1327 |
| 856 | `Moments/Moments/Views/Messaging/Core/MessagingViewModel.swift` | [x] 856↔727 |
| 1845 ⚠️ | `Moments/Moments/Views/Messaging/Media/CameraPickerView.swift` | [~] · huérfano; path chat = ChatCameraView |
| 460 | `Moments/Moments/Views/Messaging/Media/ChatCameraView.swift` | [x] 460↔534 · BackHandler+gallery thumb+recording pill+haptic+aspectRatio |
| 71 | `Moments/Moments/Views/Messaging/Media/ChatMediaOverlayPayload.swift` | [x] 71↔60 |
| 37 | `Moments/Moments/Views/Messaging/Media/ChatMediaSendMode.swift` | [x] 37↔27 |
| 613 | `Moments/Moments/Views/Messaging/Media/ViewOnceImmersiveViewer.swift` | [x] 613↔669 · canvas+chrome+emoji sheet+consume/replay; FIT_WITH_BLUR; Δ≈blur vídeo muted |
| 151 | `Moments/Moments/Views/Messaging/Models/ChatAttachmentAssets.swift` | [x] 151↔170 |
| 146 | `Moments/Moments/Views/Messaging/Screens/ArchivedConversationsView.swift` | [x] · empty+GlassmorphicRow+menu+auto-dismiss |
| 142 | `Moments/Moments/Views/Messaging/Screens/Chat/GlassmorphicChatView+Clustering.swift` | [~] |
| 1006 ⚠️ | `Moments/Moments/Views/Messaging/Screens/Chat/GlassmorphicChatView+ComposerAndChrome.swift` | [x] 1006↔718 · plus/buzz/shake+media FS+report+cluster reply picker+gallery |
| 209 | `Moments/Moments/Views/Messaging/Screens/Chat/GlassmorphicChatView+Lifecycle.swift` | [~] · markViewed+openCamera delay+view-once wiring |
| 361 | `Moments/Moments/Views/Messaging/Screens/Chat/GlassmorphicChatView+MessageList.swift` | [~] · rows+CompositionLocal search highlight |
| 262 | `Moments/Moments/Views/Messaging/Screens/Chat/GlassmorphicChatView+MessageRendering.swift` | [~] |
| 450 | `Moments/Moments/Views/Messaging/Screens/Chat/GlassmorphicChatView+Scroll.swift` | [~] |
| 107 | `Moments/Moments/Views/Messaging/Screens/Chat/GlassmorphicChatView+Search.swift` | [~] · sync+canGoUp/Down |
| 251 | `Moments/Moments/Views/Messaging/Screens/Chat/GlassmorphicChatView+Toolbar.swift` | [~] · presence+stories+search chrome |
| 88 | `Moments/Moments/Views/Messaging/Screens/Chat/GlassmorphicChatView+ViewModelAudio.swift` | [~] |
| 520 | `Moments/Moments/Views/Messaging/Screens/Chat/GlassmorphicChatView+Voice.swift` | [~] · hold-to-record wired |
| 725 | `Moments/Moments/Views/Messaging/Screens/Chat/GlassmorphicChatView.swift` | [~] · shell+scroll; search highlight; pending request ops |
| 435 | `Moments/Moments/Views/Messaging/Screens/Chat/MomentsChatViewModel+Media.swift` | [~] |
| 2816 ⚠️ | `Moments/Moments/Views/Messaging/Screens/ConversationSettingsView.swift` | [~] 2816↔~2065 · hydrate+prefs+links+footer+video controls |
| 254 | `Moments/Moments/Views/Messaging/Screens/MessageRequestsView.swift` | [x] · lista+empty+actions+open pending chat |
| 1864 ⚠️ | `Moments/Moments/Views/Messaging/Screens/MessagingView.swift` | [x] · toolbar+search+merged list+row+menu+destinations+pending chat |
| 90 | `Moments/Moments/Views/Messaging/Services/ChatAccessCoordinator.swift` | [x] 90↔125 |
| 39 | `Moments/Moments/Views/Messaging/Services/ChatBuzzProcessedStore.swift` | [x] 39↔120 |
| 51 | `Moments/Moments/Views/Messaging/Services/ChatDraftStore.swift` | [x] 51↔112 |
| 90 | `Moments/Moments/Views/Messaging/Services/ChatGiphyService.swift` | [x] 90↔105 |
| 96 | `Moments/Moments/Views/Messaging/Services/ChatKeyboardScrollCoordinator.swift` | [x] 96↔99 |
| 70 | `Moments/Moments/Views/Messaging/Services/ChatNavigationIntentStore.swift` | [x] 70↔95 |
| 158 | `Moments/Moments/Views/Messaging/Services/ChatRowHeightEstimator.swift` | [x] 158↔170 |
| 80 | `Moments/Moments/Views/Messaging/Services/ChatScrollStateStore.swift` | [x] 80↔81 |
| 93 | `Moments/Moments/Views/Messaging/Services/ChatService+Buzz.swift` | [x] 93↔118 |
| 125 | `Moments/Moments/Views/Messaging/Services/ChatService+ChunkedVideoUpload.swift` | [x] 125↔177 |
| 381 | `Moments/Moments/Views/Messaging/Services/ChatService+EncryptedMediaResolver.swift` | [x] 381↔352 |
| 178 | `Moments/Moments/Views/Messaging/Services/ChatService+EphemeralCleanup.swift` | [x] 178↔170 |
| 181 | `Moments/Moments/Views/Messaging/Services/ChatService+LocalFirstSnapshot.swift` | [x] 181↔182 |
| 484 | `Moments/Moments/Views/Messaging/Services/ChatService+MediaPipeline.swift` | [x] 484↔421 |
| 128 | `Moments/Moments/Views/Messaging/Services/ChatService+MessageActions.swift` | [x] 128↔148 |
| 355 | `Moments/Moments/Views/Messaging/Services/ChatService+MessageHydration.swift` | [x] 355↔367 |
| 159 | `Moments/Moments/Views/Messaging/Services/ChatService+MessageReactions.swift` | [x] 159↔153 |
| 89 | `Moments/Moments/Views/Messaging/Services/ChatService+Search.swift` | [x] 89↔57 |
| 562 | `Moments/Moments/Views/Messaging/Services/ChatService+SharingAndViewOnce.swift` | [x] 557↔354 |
| 253 | `Moments/Moments/Views/Messaging/Services/ChatService+VanishMode.swift` | [x] 253↔201 |
| 2834 ⚠️ | `Moments/Moments/Views/Messaging/Services/ChatService.swift` | [x] 2835↔2043 |
| 184 | `Moments/Moments/Views/Messaging/Services/ChatSessionEngine.swift` | [x] 184↔220 |
| 59 | `Moments/Moments/Views/Messaging/Services/ChatVideoPosterGenerator.swift` | [x] 59↔52 |
| 298 | `Moments/Moments/Views/Messaging/Services/LiveLocationSharingService.swift` | [x] 298↔356 |
| 33 | `Moments/Moments/Views/Messaging/Services/ViewOnceConsumptionService.swift` | [x] 33↔27 |
| 231 | `Moments/Moments/Views/Misc/WhatsNewView.swift` | [~] |
| 89 | `Moments/Moments/Views/Nova/AI/NovaAIService.swift` | [~] |
| 40 | `Moments/Moments/Views/Nova/AI/NovaGenerationConfig.swift` | [~] |
| 105 | `Moments/Moments/Views/Nova/AI/NovaPromptCatalog.swift` | [~] |
| 769 | `Moments/Moments/Views/Nova/Agent/NovaAgent.swift` | [~] |
| 55 | `Moments/Moments/Views/Nova/Agent/NovaContextAssembler.swift` | [~] |
| 308 | `Moments/Moments/Views/Nova/Agent/NovaPendingAction.swift` | [~] |
| 291 | `Moments/Moments/Views/Nova/Agent/NovaToolExecutor.swift` | [~] |
| 304 | `Moments/Moments/Views/Nova/Agent/NovaToolRegistry.swift` | [~] |
| 507 | `Moments/Moments/Views/Nova/Conversation/NovaConversationStore.swift` | [~] |
| 214 | `Moments/Moments/Views/Nova/Conversationmodels.swift` | [~] |
| 12 | `Moments/Moments/Views/Nova/Core/NovaLocaleContext.swift` | [~] |
| 136 | `Moments/Moments/Views/Nova/Memory/NovaContextStore.swift` | [~] |
| 104 | `Moments/Moments/Views/Nova/Memory/NovaMemoryCrypto.swift` | [~] |
| 251 | `Moments/Moments/Views/Nova/Memory/NovaMemoryEngine.swift` | [~] |
| 304 | `Moments/Moments/Views/Nova/Memory/NovaMemoryModels.swift` | [~] |
| 61 | `Moments/Moments/Views/Nova/Memory/NovaMemoryStore.swift` | [~] |
| 63 | `Moments/Moments/Views/Nova/NovaCore/NovaModels.swift` | [~] |
| 92 | `Moments/Moments/Views/Nova/NovaCore/NovaTheme.swift` | [~] |
| 420 | `Moments/Moments/Views/Nova/NovaMemoryManagementView.swift` | [~] |
| 1041 ⚠️ | `Moments/Moments/Views/Nova/NovaSections/NovaAttachmentSheet.swift` | [~] |
| 853 | `Moments/Moments/Views/Nova/NovaSections/NovaChatSection.swift` | [~] |
| 726 | `Moments/Moments/Views/Nova/NovaSections/NovaChromeSection.swift` | [~] |
| 211 | `Moments/Moments/Views/Nova/NovaSections/NovaHistorySection.swift` | [~] |
| 322 | `Moments/Moments/Views/Nova/NovaSections/NovaInputSection.swift` | [~] |
| 385 | `Moments/Moments/Views/Nova/NovaView.swift` | [~] |
| 317 | `Moments/Moments/Views/Nova/Tools/NovaActivityTools.swift` | [~] |
| 52 | `Moments/Moments/Views/Nova/Tools/NovaMemoryTools.swift` | [~] |
| 229 | `Moments/Moments/Views/Nova/Tools/NovaMomentAudience.swift` | [~] |
| 53 | `Moments/Moments/Views/Nova/Tools/NovaMomentDraftParser.swift` | [~] |
| 478 | `Moments/Moments/Views/Nova/Tools/NovaProfileTools.swift` | [~] |
| 164 | `Moments/Moments/Views/Nova/Tools/NovaSocialTools.swift` | [~] |
| 103 | `Moments/Moments/Views/Nova/UI/NovaActionConfirmationOverlay.swift` | [~] |
| 21 | `Moments/Moments/Views/Permission/camera/Contentview.swift` | [~] |
| 318 | `Moments/Moments/Views/Permission/camera/helpers/CameraPermissionsview.swift` | [~] |
| 182 | `Moments/Moments/Views/Permission/location/LocationPermissionView.swift` | [~] |
| 134 | `Moments/Moments/Views/Permission/microphone/MicrophonePermissionView.swift` | [~] |
| 215 | `Moments/Moments/Views/Permission/notifications/NotificationsPermissionView.swift` | [~] |
| 99 | `Moments/Moments/Views/Permission/photos/PhotosPermissionView.swift` | [~] |
| 153 | `Moments/Moments/Views/Permission/shared/LocationPermissionGate.swift` | [~] |
| 165 | `Moments/Moments/Views/Permission/shared/PermissionPhoneFrame.swift` | [~] |
| 17 | `Moments/Moments/Views/Permission/shared/PermissionPhoneWallpaper.swift` | [~] |
| 167 | `Moments/Moments/Views/Permission/shared/PermissionPrimerGate.swift` | [~] |
| 147 | `Moments/Moments/Views/Permission/shared/PermissionPrimerScaffold.swift` | [~] |
| 148 | `Moments/Moments/Views/Permission/tracking/TrackingPermissionView.swift` | [~] |
| 106 | `Moments/Moments/Views/Permissions/CameraAccessBoundary.swift` | [x] 106↔160 · primer/denied prefs; mic+cam→Settings; ON_RESUME |
| 111 | `Moments/Moments/Views/Permissions/CameraPermissionGate.swift` | [~] |
| 78 | `Moments/Moments/Views/Profile/Core/MomentGridPreview.swift` | [x] 78↔93 |
| 637 | `Moments/Moments/Views/Profile/Core/ProfileView.swift` | [x] 637↔616 |
| 857 | `Moments/Moments/Views/Profile/Core/ProfileViewModel.swift` | [x] 857↔566 |
| 239 | `Moments/Moments/Views/Profile/Core/Sections/ProfileBentoLayout.swift` | [x] 239↔160 |
| 1229 ⚠️ | `Moments/Moments/Views/Profile/Core/Sections/ProfileGridHeroTransition.swift` | [x] 1229↔1102 |
| 452 | `Moments/Moments/Views/Profile/Core/Sections/ProfileGridMomentMenu.swift` | [x] 452↔397 |
| 520 | `Moments/Moments/Views/Profile/Core/Sections/ProfileGridPreviewEditorView.swift` | [x] 520↔633 |
| 592 | `Moments/Moments/Views/Profile/Core/Sections/ProfileHeaderSection.swift` | [x] 592↔547 |
| 93 | `Moments/Moments/Views/Profile/Core/Sections/ProfileHeaderSkeletonView.swift` | [x] 93↔111 |
| 422 | `Moments/Moments/Views/Profile/Core/Sections/ProfileMomentZoomNavigation.swift` | [x] 422↔342 |
| 539 | `Moments/Moments/Views/Profile/Core/Sections/ProfileMomentsSection.swift` | [x] 539↔438 |
| 541 | `Moments/Moments/Views/Profile/Core/Sections/ProfileSavedSection.swift` | [x] 541↔586 |
| 672 | `Moments/Moments/Views/Profile/Core/Sections/ProfileSharedComponents.swift` | [x] 672↔520 |
| 588 | `Moments/Moments/Views/Profile/Core/Sections/ProfileShellComponents.swift` | [x] 588↔521 |
| 58 | `Moments/Moments/Views/Profile/Core/Sections/UserProfileZoomNavigation.swift` | [x] 58↔129 |
| 1566 ⚠️ | `Moments/Moments/Views/Profile/Core/SharedActivityDetailView.swift` | [x] 1566↔1440 |
| 474 | `Moments/Moments/Views/Profile/Core/SharedActivityView.swift` | [x] 474↔576 |
| 452 | `Moments/Moments/Views/Profile/Core/SocialConnectionUserRow.swift` | [x] 452↔486 |
| 791 | `Moments/Moments/Views/Profile/Core/SocialConnectionsView.swift` | [x] 791↔747 |
| 666 | `Moments/Moments/Views/Profile/Core/UserListView.swift` | [x] 666↔674 |
| 808 | `Moments/Moments/Views/Profile/Editor/PhotoCropEditorView.swift` | [x] 808↔796 |
| 1743 ⚠️ | `Moments/Moments/Views/Profile/Editor/ProfileEditor.swift` | [x] 1743↔1234 |
| 285 | `Moments/Moments/Views/Profile/Editor/Sections/ProfileEditorPickerViews.swift` | [x] 285↔418 |
| 439 | `Moments/Moments/Views/Profile/Highlights/HighlightComponents.swift` | [x] 439↔422 |
| 148 | `Moments/Moments/Views/Profile/Highlights/HighlightCreateFlowView.swift` | [x] 148↔196 |
| 256 | `Moments/Moments/Views/Profile/Highlights/HighlightCreateFlowViewModel.swift` | [x] 256↔210 |
| 101 | `Moments/Moments/Views/Profile/Highlights/HighlightNameCoverStep.swift` | [x] 101↔150 |
| 90 | `Moments/Moments/Views/Profile/Highlights/HighlightPresentationCoordinator.swift` | [x] 90↔79 |
| 33 | `Moments/Moments/Views/Profile/Highlights/HighlightSelectStoriesStep.swift` | [x] 33↔41 |
| 135 | `Moments/Moments/Views/Profile/Highlights/HighlightViewer.swift` | [x] 135↔165 |
| 372 | `Moments/Moments/Views/Profile/Highlights/ProfileHighlightsView.swift` | [x] 372↔365 |
| 214 | `Moments/Moments/Views/Profile/Incognito/IncognitoGlobalOverlay.swift` | [x] 214↔216 |
| 347 | `Moments/Moments/Views/Profile/Incognito/IncognitoModeSheet.swift` | [x] 347↔422 |
| 1413 ⚠️ | `Moments/Moments/Views/Profile/MomentsView/ContextMenu.swift` | [x] 1413↔1060 |
| 622 | `Moments/Moments/Views/Profile/MomentsView/EditMomentView.swift` | [x] 622↔697 |
| 611 | `Moments/Moments/Views/Profile/MomentsView/ModernMomentDetailView.swift` | [x] 611↔686 |
| 933 | `Moments/Moments/Views/Profile/Theme/EnhancedProfileBackground.swift` | 🚫 |
| 925 | `Moments/Moments/Views/Profile/Theme/ProfileTheme.swift` | 🚫 |
| 146 | `Moments/Moments/Views/Profile/Theme/ProfileThemeDemo.swift` | 🚫 |
| 274 | `Moments/Moments/Views/Profile/Theme/ProfileThemeSelector.swift` | 🚫 |
| 244 | `Moments/Moments/Views/Profile/UserProfile/Sections/UserProfileAvatarBadges.swift` | [x] 244↔57 |
| 321 | `Moments/Moments/Views/Profile/UserProfile/Sections/UserProfileHeaderSection.swift` | [x] 321↔352 |
| 394 | `Moments/Moments/Views/Profile/UserProfile/Sections/UserProfileMomentsSection.swift` | [x] 394↔64 |
| 351 | `Moments/Moments/Views/Profile/UserProfile/Sections/UserProfileOverviewSection.swift` | [x] 351↔390 |
| 320 | `Moments/Moments/Views/Profile/UserProfile/Sections/UserProfilePublicProfileView.swift` | [x] 320↔268 |
| 363 | `Moments/Moments/Views/Profile/UserProfile/Sections/UserProfileRelationshipViews.swift` | [x] 363↔440 |
| 339 | `Moments/Moments/Views/Profile/UserProfile/Sections/UserProfileSharedViews.swift` | [x] 339↔361 |
| 665 | `Moments/Moments/Views/Profile/UserProfile/Sections/UserProfileStateViews.swift` | [x] 665↔613 |
| 688 | `Moments/Moments/Views/Profile/UserProfile/UserProfileView.swift` | [x] 688↔629 |
| 1071 ⚠️ | `Moments/Moments/Views/Profile/UserProfile/UserProfileViewModel.swift` | [x] 1071↔723 |
| 430 | `Moments/Moments/Views/Settings/AccountHistoryActivityView.swift` | [x] |
| 859 | `Moments/Moments/Views/Settings/AccountManagement.swift` | [x] |
| 201 | `Moments/Moments/Views/Settings/ActivityCollapsibleFilterScroll.swift` | [x] 201↔137 |
| 184 | `Moments/Moments/Views/Settings/BlockedUsersView.swift` | [x] |
| 343 | `Moments/Moments/Views/Settings/ChatStorageSettingsView.swift` | [x] |
| 853 | `Moments/Moments/Views/Settings/ContentVisibilityView.swift` | [x] |
| 168 | `Moments/Moments/Views/Settings/DailyLimitView.swift` | [x] |
| 788 | `Moments/Moments/Views/Settings/DataExportView.swift` | [x] |
| 606 | `Moments/Moments/Views/Settings/LoginActivityView.swift` | [x] |
| 629 | `Moments/Moments/Views/Settings/MuteSettingsView.swift` | [x] |
| 421 | `Moments/Moments/Views/Settings/PasswordChangeView.swift` | [x] |
| 166 | `Moments/Moments/Views/Settings/QRCode.swift` | [x] |
| 190 | `Moments/Moments/Views/Settings/RestModeView.swift` | [x] |
| 383 | `Moments/Moments/Views/Settings/SavedMoments/SavedMomentsViewModel.swift` | [x] |
| 1595 ⚠️ | `Moments/Moments/Views/Settings/SavedMoments/SavedMomentsView.swift` | [x] |
| 270 | `Moments/Moments/Views/Settings/SearchHistoryActivityView.swift` | [x] |
| 349 | `Moments/Moments/Views/Settings/SetPasswordView.swift` | [x] |
| 121 | `Moments/Moments/Views/Settings/SettingsNavigationComponents.swift` | [x] 121↔142 |
| 252 | `Moments/Moments/Views/Settings/SettingsSections/NotificationSettingsView.swift` | [x] |
| 67 | `Moments/Moments/Views/Settings/SettingsSections/OnlineStatusSection.swift` | [x] |
| 352 | `Moments/Moments/Views/Settings/SettingsSections/PersonalInfoSettingsViews.swift` | [x] |
| 1579 ⚠️ | `Moments/Moments/Views/Settings/SettingsSections/SettingsSections.swift` | [x] 1579↔~1600 |
| 332 | `Moments/Moments/Views/Settings/SettingsView.swift` | [x] 332↔~370 · shell+form real |
| 97 | `Moments/Moments/Views/Settings/SettingsViewModel.swift` | [x] 97↔151 |
| 99 | `Moments/Moments/Views/Settings/TimeSpentCardView.swift` | [x] |
| 95 | `Moments/Moments/Views/Settings/TimeSpentDetailsView.swift` | [x] |
| 116 | `Moments/Moments/Views/Settings/UserActivityBackendModels.swift` | [x] |
| 183 | `Moments/Moments/Views/Settings/UserActivityCache.swift` | [x] |
| 220 | `Moments/Moments/Views/Settings/UserActivityComponents.swift` | [x] |
| 2199 ⚠️ | `Moments/Moments/Views/Settings/UserActivityDetailView.swift` | [x] |
| 1224 ⚠️ | `Moments/Moments/Views/Settings/UserActivityDetailViewModel.swift` | [x] |
| 98 | `Moments/Moments/Views/Settings/UserActivityModels.swift` | [x] |
| 1178 ⚠️ | `Moments/Moments/Views/Settings/UserActivityRows.swift` | [x] |
| 119 | `Moments/Moments/Views/Settings/UserActivitySummaryViewModel.swift` | [x] |
| 327 | `Moments/Moments/Views/Settings/UserActivityTypes.swift` | [x] |
| 266 | `Moments/Moments/Views/Settings/UserActivityView.swift` | [x] |
| 33 | `Moments/Moments/Views/Shared/AppErrorBanner.swift` | [x] |
| 10 | `Moments/Moments/Views/Shared/BlurView.swift` | 🚫 |
| 34 | `Moments/Moments/Views/Shared/MomentDetail/MomentDetailContainerView.swift` | [x] |
| 21 | `Moments/Moments/Views/Shared/MomentDetail/MomentDetailContext.swift` | [x] |
| 412 | `Moments/Moments/Views/Shared/MomentDetail/SingleMomentDetailView.swift` | [x] |
| 100 | `Moments/Moments/Views/Shared/MomentsVideoPlaybackTimeline.swift` | [x] 100↔160 |
| 331 | `Moments/Moments/Views/Shared/MomentsVideoPlayer.swift` | [x] 331↔255 |
| 214 | `Moments/Moments/Views/Shared/OfflineBannerModifier.swift` | [x] 214↔208 |
| 74 | `Moments/Moments/Views/Shared/PhotoTagOverlayView.swift` | [x] 74↔134 |
| 175 | `Moments/Moments/Views/Shared/ScreenshotProtectedView.swift` | [x] |
| 227 | `Moments/Moments/Views/comments/CommentMentionSearchOverlay.swift` | [~] chrome glass |
| 429 | `Moments/Moments/Views/comments/CommentsView.swift` | [ ] |
| 1900 ⚠️ | `Moments/Moments/Views/comments/ModernCommentsView.swift` | [~] P1 cerrados; pendiente QA |
| 503 | `Moments/Moments/Views/story/QuestionResponsesView.swift` | [x] |
| 1018 ⚠️ | `Moments/Moments/Views/story/StoriesView.swift` | [x] · NavigateToChainStory+loadChain; pauseAllVideos; ring/following |
| 635 | `Moments/Moments/Views/story/StoryChainView.swift` | [x] |
| 54 | `Moments/Moments/Views/story/StoryDeckGestureGate.swift` | [x] 54↔67 |
| 1106 ⚠️ | `Moments/Moments/Views/story/StoryInteractiveStickers.swift` | [x] · hint chrome+exclusion; patrones reveal≡iOS |
| 214 | `Moments/Moments/Views/story/StoryModels.swift` | [x] 214↔203 |
| 224 | `Moments/Moments/Views/story/StoryPlaybackCoordinator.swift` | [x] 224↔259 |
| 435 | `Moments/Moments/Views/story/StoryRepository.swift` | [x] 435↔330 |
| 200 | `Moments/Moments/Views/story/StoryRingAvatarView.swift` | [x] 200↔219 |
| 246 | `Moments/Moments/Views/story/StorySegmentedRing.swift` | [x] 246↔222 |
| 617 | `Moments/Moments/Views/story/StoryStickers/StoryStickerEffects.swift` | [x] |
| 1911 ⚠️ | `Moments/Moments/Views/story/StoryStickers/StoryStickerViews.swift` | [x] 1911↔~1350 · surfaces+exclusion; PollVoteView dead N/A |
| 912 | `Moments/Moments/Views/story/StoryViewModel.swift` | [x] 912↔739 |
| 225 | `Moments/Moments/Views/story/StoryViewer/StoryDeckInteractionLayout.swift` | [x] 225↔~200 · exclusion+EmojiSlider+RevealScratch pans |
| 180 | `Moments/Moments/Views/story/StoryViewer/StoryGestureCoordinator.swift` | [x] 180↔186 |
| 29 | `Moments/Moments/Views/story/StoryViewer/StoryLiveTextOverlayView.swift` | [x] 29↔~75 · Label+position; style nil; zIndex |
| 76 | `Moments/Moments/Views/story/StoryViewer/StoryMediaOverlayRendererView.swift` | [x] 76↔~110 · zIndex+exclusion+hitTesting |
| 118 | `Moments/Moments/Views/story/StoryViewer/StoryQuickActionsMenu.swift` | [x] 118↔~130 · glass+strings+menuRow |
| 777 | `Moments/Moments/Views/story/StoryViewer/StoryReplyViews.swift` | [x] 777↔~790 · glassmorphic ext+Overlay/Ad |
| 257 | `Moments/Moments/Views/story/StoryViewer/StoryUserDeckPager.swift` | [x] 257↔~310 · center hit-test+root exclusión |
| 470 | `Moments/Moments/Views/story/StoryViewer/StoryViewerBottomComponents.swift` | [x] |
| 134 | `Moments/Moments/Views/story/StoryViewer/StoryViewerLayers.swift` | [x] |
| 144 | `Moments/Moments/Views/story/StoryViewer/StoryViewerLayoutHelpers.swift` | [x] |
| 255 | `Moments/Moments/Views/story/StoryViewer/StoryViewerMedia.swift` | [x] |
| 1067 ⚠️ | `Moments/Moments/Views/story/StoryViewer/StoryViewerOverlay.swift` | [x] |
| 2381 ⚠️ | `Moments/Moments/Views/story/StoryViewer/StoryViewerScreen.swift` | [x] |
| 1831 ⚠️ | `Moments/Moments/Views/story/archived stories.swift` | [x] · card+viewer+StoryStatsView+calendar+map |
| 64 | `Moments/Moments/ad/AdAspectRatioContext.swift` | [~] |
| 474 | `Moments/Moments/ad/AdMob Configuration.swift` | [~] |
| 604 | `Moments/Moments/ad/FeedNativeAd.swift` | [~] |
| 1479 ⚠️ | `Moments/Moments/ad/StoryNativeAd.swift` | [~] |
| 812 | `Moments/MomentsNotificationService/NotificationService.swift` | [x] 812↔405 |
| 144 | `Moments/MomentsNotificationService/SharedChatDecryptor.swift` | [x] 144↔90 |
| 137 | `Moments/Shared/ChatCommunicationIntentDonor.swift` | [x] 137↔211 |
| 26 | `Moments/Shared/ChatPreviewPrivacy.swift` | [x] 26↔67 |
| 20 | `Moments/Shared/IncognitoActivityAttributes.swift` | 🚫 |
| 154 | `Moments/Shared/MessageIngestQueue.swift` | [x] 154↔163 |
| 43 | `Moments/Shared/PauseIncognitoIntent.swift` | 🚫 |
