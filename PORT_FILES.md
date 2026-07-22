# Moments — Inventario de archivos (port iOS → Android)

Checklist **archivo por archivo** de los `.swift` del target iOS y su equivalente en Android. Generado desde `../Moments/Moments`. Complementa a [PORT_CHECKLIST.md](PORT_CHECKLIST.md) (vista por carpeta).

**Mapa actual: ~165 / 574 archivos con contraparte nombrada** (conteo aproximado; actualizar al cerrar cada lote). Esto no es una certificación de paridad:

| Marca | Significado |
|---|---|
| `[ ]` | Sin contraparte Kotlin |
| `[x]` | Contraparte existe y el flujo principal está cableado (aún puede faltar pulido e2e) |
| `[~]` | Contraparte existe pero **a medias**: stubs, placeholders, o divergencia confirmada |

Ningún archivo se considera espejo 1:1 hasta comparar datos, estados, errores y UI/UX con su Swift. **Regla de trabajo:** al cerrar un lote de port, actualizar este archivo en el mismo turno.

## Views  (12/421)

**`Components`**
- [ ] AnimatedStickerView.swift
- [ ] AudienceIconView.swift
- [ ] CommentRowSkeletonView.swift
- [ ] EchoesIconView.swift
**`Components/HiddenLayers`**
- [ ] HiddenLayerLayout.swift
**`Components`**
- [ ] InAppBannerView.swift
- [ ] InAppMessageQuickReplyPanel.swift
- [ ] IntelligentGlow.swift
- [ ] InteractiveStickerSharedViews.swift
- [ ] LiveUsernameText.swift
- [ ] LocationMomentCardSkeletonView.swift
- [ ] MomentCaptionView.swift
- [ ] MomentHashtagText.swift
- [ ] MomentRailComponents.swift
- [ ] MomentRefresh.swift
- [ ] MomentRowButton.swift
- [ ] OfflineBanner.swift
- [ ] RefreshControl.swift
- [ ] SkeletonShimmer.swift
- [ ] StoryViewerSkeletonView.swift
- [ ] UserRowSkeletonView.swift
- [ ] VerifiedBadge.swift
**`Creator/AudienceSelector`**
- [ ] AudienceModels.swift
- [ ] AudienceSelectionRows.swift
- [~] AudienceSelectionView.swift → `views/creator/audienceselector/AudienceSelectionView.kt` (predefinidas + listas; custom people pending)
- [ ] CustomAudienceManagementViews.swift
- [ ] CustomListSelectorView.swift
**`Creator`**
- [x] BackgroundMomentUploadService.swift → `views/creator/BackgroundMomentUploadService.kt`
- [x] BackgroundStoryUploadService.swift → `views/creator/BackgroundStoryUploadService.kt` (+ `StoryStickerRebuild.kt`)
**`Creator/CameraKit`**
- [ ] CameraKitSpike.swift
- [ ] LensReel.swift
**`Creator`**
- [ ] ChainConfigurationView.swift
- [ ] ChainContinuationSelectorView.swift
**`Creator/Components`**
- [~] CaptureButton.swift → `views/creator/creatorscreens/CaptureButton.kt`
- [ ] EditableImageView.swift
- [ ] StickerDetailPalette.swift
- [ ] StickerGiphyViews.swift
- [ ] StickerInputViews.swift
- [ ] StickerLocationInputView.swift
- [ ] StickerMediaInputs.swift
- [ ] StickerPickerGeneratedStickers.swift
- [ ] StickerPickerLayout.swift
- [ ] StickerPickerSupportExtensions.swift
- [ ] StoryBackgroundPresets.swift
- [ ] StoryColorPickerView.swift
- [ ] StoryDominantColorsExtractor.swift
- [~] StoryDrawingEditorOverlay.swift → `StoryDrawingEditorOverlay.kt` (pinceles/undo/redo/export PNG; ColorPicker sistema deferred)
- [ ] StoryEditingControls.swift
- [~] StoryEditorTextTypes.swift → `StoryEditorTextTypes.kt` (estilos Aa + swatches; motion/effects pending)
- [~] StoryFilterSelectorView.swift → `StoryFilterSelectorView.kt` (+ intensidad; cableado en StoryEditing)
- [~] StoryFontRegistry.swift → `StoryFontRegistry.kt` + assets/fonts/*.ttf
- [ ] StoryTextAttributesBuilder.swift
- [ ] StoryTextEditorChrome.swift
- [ ] StoryTextGradientSettings.swift
- [ ] StoryTextMotionEngine.swift
- [~] StoryTextOverlayMetadata.swift → draft en `StoryTextOverlayDraft.kt` + model `Models.kt`; encode en payload
- [~] StoryTextEditor.swift → `views/creator/creatorscreens/StoryTextEditor.kt` (fonts + color swatches; HSB/eyedropper/motion pending)
- [ ] StoryTextVisualRenderer.swift
- [ ] StoryVideoPlayerView.swift
**`Creator/CreatorScreens`**
- [ ] AlbumPickerView.swift
- [~] CaptionAndDetailsView.swift → `views/creator/creatorscreens/CaptionAndDetailsView.kt` (share + Location/Audience/PhotoTag/HiddenLayers texto; schedule pending)
- [~] ContentTypeSelectionView.swift → `views/creator/creatorscreens/ContentTypeSelectionView.kt`
- [~] FilterOption.swift → `views/creator/creatorscreens/FilterOption.kt` (cableado en MediaEditing filter mode)
- [~] LocationPickerView.swift → `views/creator/creatorscreens/LocationPickerView.kt` (search + GPS; sin MapKit)
- [~] MediaEditingView.swift → `views/creator/creatorscreens/MediaEditingView.kt` (preview + crop + filtros; OK)
- [~] MediaGridCell.swift → `views/creator/creatorscreens/MediaGridCell.kt`
- [~] MediaSelectionView.swift → `views/creator/creatorscreens/MediaSelectionView.kt` (galería + álbumes MediaStore; CameraCapture pendiente)
- [ ] StickerOverlayView.swift
- [~] StoryCameraView.swift → `views/creator/creatorscreens/StoryCameraView.kt` (CameraX foto+vídeo long-press/flip/galería/Aa; lenses pending)
- [~] StoryOverlaysView.swift
- [~] StoryTextEditor.swift → ver `creatorscreens/StoryTextEditor.kt` (fonts/colors chunk3)
- [ ] UserSearchView.swift
**`Creator`**
- [~] CreatorSharedModels.swift → parcial en `CreatorView.kt` (`CreatorMedia` / `CreatorAlbumInfo` / flows)
**`Creator/CreatorUIKit`**
- [ ] BackgroundCameraView.swift
- [ ] CameraCapture.swift
- [ ] CameraPreviewView.swift
- [ ] CreatorCaptureGeometry.swift
- [ ] CreatorControls.swift
- [ ] CreatorUIImageExtensions.swift
- [~] CropViewWrapper.swift → `views/creator/creatorscreens/CropViewWrapper.kt` (Compose crop; TOCropViewController paridad funcional)
- [ ] DrawingView.swift
- [ ] StoryGalleryPicker.swift
- [ ] StoryMediaPicker.swift
**`Creator`**
- [~] CreatorView.swift → `views/creator/CreatorView.kt` (shell + type/media/edit/caption/story camera+editor chunk1)
- [~] HiddenLayersEditorView.swift → `views/creator/creatorscreens/HiddenLayersEditorView.kt` (texto máx 3; image/audio/schedule pending)
- [~] PhotoTagSelectionView.swift → `views/creator/creatorscreens/PhotoTagSelectionView.kt`
- [ ] StickerEmojiPalettePicker.swift
- [ ] StoryVideoProcessingService.swift
- [ ] StoryVideoTrimEditorView.swift
- [ ] VideoEditor.swift
- [ ] stickerview.swift
- [~] storyeditor.swift → `views/creator/creatorscreens/StoryEditingView.kt` (+ texto/fonts/colors + dibujo + filtros chunk5; stickers/motion pending)
**`Echoes`**
- [x] EchoHistoryView.swift → `views/echoes/EchoHistoryView.kt` (cableado en FeedPresentationModifier)
- [x] EchoInvitationView.swift → `views/echoes/EchoInvitationView.kt` (cableado en FeedOverlaysSection)
- [ ] EchoViewerUI.swift
**`Explore`**
- [~] ExploreGridLayout.swift → `views/explore/ExploreGridLayout.kt` (grid 3 cols; bento exacto = pulido)
- [~] ExploreMomentDetailView.swift → `views/explore/ExploreMomentDetailView.kt` (reusa SingleMomentDetail; scroll multi = pulido)
**`Explore/ExploreSections`**
- [~] ExploreResultsSection.swift → `views/explore/sections/ExploreResultsSection.kt`
- [~] ExploreSuggestionsSection.swift → `views/explore/sections/ExploreSuggestionsSection.kt`
**`Explore`**
- [~] ExploreView.swift → `views/explore/ExploreView.kt` (tab + sheet feed/hashtag cableados; Profile sheet stub)
- [~] ExploreViewModel.swift → `views/explore/ExploreViewModel.kt`
- [ ] ModernExploreDetailHeader.swift — no en MVP (chrome vía SingleMomentDetail)
- [ ] MomentDetailView.swift — N/A legacy iOS (cero call sites)
- [~] SuggestedUsersView.swift → `views/explore/SuggestedUsersView.kt` (lista + follow; sin paginación infinita)
**`Feed/Controls`**
- [x] FeedTypeSelector.swift → `views/feed/controls/FeedTypeSelector.kt`
- [x] feedchange.swift → `views/feed/controls/FeedChange.kt`
**`Feed/Core`**
- [~] FeedNotificationRoutingModifier.swift → `views/feed/core/FeedNotificationRoutingModifier.kt`
- [~] FeedPresentationModifier.swift → `views/feed/core/FeedPresentationModifier.kt` (Explore/Stories/Messaging/Profile/Edit/Comments/EchoHistory cableados; sin placeholders del contrato Feed)
- [x] FeedRoutes.swift → `views/feed/core/FeedRoutes.kt`
- [~] FeedView.swift → `views/feed/core/FeedView.kt` (feed usable; pulido visual card vs iOS)
- [~] FeedViewModel.swift → `views/feed/core/FeedViewModel.kt`
- [x] ModernEmptyFeedView.swift → `views/feed/core/ModernEmptyFeedView.kt`
**`Feed/Core/Sections`**
- [~] FeedHeaderSection.swift → `views/feed/core/sections/FeedHeaderSection.kt`
- [~] FeedListSection.swift → `views/feed/core/sections/FeedListSection.kt`
- [~] FeedMomentComponents.swift → `views/feed/core/sections/FeedMomentComponents.kt` (`ModernPostCardView`)
- [x] FeedMomentDetailRoute.swift → `views/feed/core/sections/FeedMomentDetailRoute.kt` → `MomentDetailContainerView(Single)`
- [~] FeedOverlaysSection.swift → `views/feed/core/sections/FeedOverlaysSection.kt` (EchoInvitation + ContextMenu cableados; resto parcial)
- [x] FeedPostSkeletonView.swift → `views/feed/core/sections/FeedPostSkeletonView.kt`
- [~] FeedStoryRingComponents.swift → `views/feed/core/sections/FeedStoryRingComponents.kt`
**`Feed/Moments`**
- [x] ClickableHashtagsView.swift → `views/feed/moments/ClickableHashtagsView.kt`
- [x] HiddenLayersOverlayView.swift → `views/feed/moments/HiddenLayersOverlayView.kt`
- [x] MomentCarouselLayoutRules.swift → `views/feed/moments/MomentCarouselLayoutRules.kt` (+ `MomentMediaCarousel.kt`)
**`Feed/Reactions`**
- [x] MomentReactionButton.swift → `views/feed/reactions/MomentReactionButton.kt`
- [x] reacciones.swift → `views/feed/reactions/Reacciones.kt`
**`Feed/Sharing`**
- [x] ShareMomentSticker.swift → `views/feed/sharing/ShareMomentSticker.kt`
- [x] StoryShare.swift → `views/feed/sharing/StoryShare.kt`
- [x] share.swift → `views/feed/sharing/Share.kt`
**`Feed/Stories`**
- [~] FeedStoryRingCoordinator.swift → `views/feed/stories/FeedStoryRingCoordinator.kt` (anillo en feed; viewer = stub hasta lote Stories)
- [x] StoryRingTraySkeleton.swift → `views/feed/stories/StoryRingTraySkeleton.kt` (+ `StoryRingLayout.kt`)
**`Feed/Uploads`**
- [x] FeedUploadProgressRow.swift → `views/feed/uploads/FeedUploadProgressRow.kt`
- [x] FloatingMomentUploadOverlay.swift → `views/feed/uploads/FloatingMomentUploadOverlay.kt`
- [x] StoryUploadProgressManager.swift → `views/feed/uploads/StoryUploadProgressManager.kt`
**`Feed/Video`**
- [~] LiveVideoTimeLabel.swift → `views/feed/video/LiveVideoTimeLabel.kt`
- [~] Reels.swift → `views/feed/video/Reels.kt`
- [~] VideoFeedProgressBar.swift → `views/feed/video/VideoFeedProgressBar.kt`
- [~] VideoPlaybackChromeStyle.swift → `views/feed/video/VideoPlaybackChromeStyle.kt`
- [~] VideoPlayer.swift → `views/feed/video/VideoPlayer.kt` (existe; paridad global vs iOS aún abierta)
- [~] VideoPosterOverlay.swift → `views/feed/video/VideoPosterOverlay.kt`
**`Feed/maps`**
- [~] DiscoverMapView.swift → `views/feed/maps/DiscoverMapView.kt`
- [~] LocationMomentDetailView.swift → `views/feed/maps/LocationMomentDetailView.kt` (lista + cards; sin swipe-dismiss/prefetch completo vs iOS)
- [x] MapAnnotationModels.swift → `views/feed/maps/MapAnnotationModels.kt`
- [~] MapDiscoverSupport.swift → `views/feed/maps/MapDiscoverSupport.kt`
- [~] MapLocationServices.swift → `views/feed/maps/MapLocationServices.kt`
- [~] MapPlaceBottomSheet.swift → `views/feed/maps/MapPlaceBottomSheet.kt`
- [~] MapPlaceClusterEngine.swift → `views/feed/maps/MapPlaceClusterEngine.kt`
- [~] MapPlaceStoryDeck.swift → `views/feed/maps/MapPlaceStoryDeck.kt`
- [~] MapWeatherEffects.swift → `views/feed/maps/MapWeatherEffects.kt`
- [~] Maps.swift → `views/feed/maps/LocationMapView.kt` / `Maps.kt` (+ secciones; parcial)
**`Feed/maps/MapsSections`**
- [~] MapBottomSheetSection.swift → `views/feed/maps/mapsections/MapBottomSheetSection.kt`
- [~] MapCanvasSection.swift → `views/feed/maps/mapsections/MapCanvasSection.kt` (+ `MapHeaderSection.kt`)
**`Feed/maps`**
- [~] WeatherService.swift → `views/feed/maps/WeatherService.kt`
**`Login`**
- [x] AuthUIComponents.swift → `views/login/AuthComponents.kt`
- [x] CreatingProfileView.swift → `views/login/CreatingProfileScreen.kt`
- [x] DeactivatedAccountView.swift → `views/login/AccountStateScreens.kt`
- [x] Interestview.swift — N/A (archivo vacío)
- [x] LiquidGlassComponents.swift → `views/login/AuthComponents.kt + AuthTheme.kt`
- [x] LoginView.swift → `views/login/LoginScreen.kt`
- [x] PrivacyPolicyView.swift → `views/login/PrivacyPolicyScreen.kt`
- [x] ProfileOnboardingView.swift → `views/login/OnboardingScreen.kt`
- [x] RegisterView.swift → `→ OnboardingScreen.kt`
- [x] SocialProfileCompletionView.swift — N/A (era Apple; Google crea perfil directo)
- [x] SplashScreen.swift → `views/login/SplashScreen.kt`
- [x] SuspendedAccount.swift → `views/login/AccountStateScreens.kt`
**`Messaging/Attachments`**
- [ ] ChatGiphyPickerSheet.swift
- [ ] ChatLocationSheet.swift
**`Messaging/Components`**
- [ ] AttachmentIconView.swift
- [ ] ChatAdaptiveColors.swift
- [ ] ChatAttachmentSheet.swift
- [ ] ChatBuzzEffectViews.swift
- [ ] ChatChromeViews.swift
- [ ] ChatClusterMediaViews.swift
- [ ] ChatEphemeralMessageViews.swift
- [ ] ChatFloatingNavigationOverlay.swift
- [ ] ChatGifMessageBubble.swift
- [ ] ChatInputViews.swift
- [ ] ChatKFImageViews.swift
- [ ] ChatLocationMessageBubble.swift
- [ ] ChatMediaViews.swift
- [ ] ChatMessageBubbleViews.swift
- [ ] ChatMessageForwardSheet.swift
- [ ] ChatMessageInteractionModifiers.swift
- [ ] ChatMessageListView.swift
- [ ] ChatMessageOptionsMenu.swift
- [ ] ChatMessageSupportViews.swift
- [ ] ChatRecoveryViews.swift
- [ ] ChatSearchNavigationBar.swift
- [ ] ChatSpeechBubbleViews.swift
- [ ] ChatStickerMessageBubble.swift
- [ ] ChatVanishModeViews.swift
- [ ] ConversationContextMenu.swift
- [ ] MediaProgressRing.swift
- [ ] MessageTypeIconView.swift
- [ ] MessagingComposerAndStatusViews.swift
- [ ] ViewOnceMessageBubble.swift
- [ ] VoiceNotes.swift
- [ ] VoiceRecordingGestureViews.swift
**`Messaging/Core`**
- [ ] ChatViewModel.swift
- [ ] MessageItem.swift
- [ ] MessageModel.swift
- [~] MessagingViewModel.swift → `views/messaging/core/MessagingViewModel.kt` (inbox + targetId + chat texto)
**`Messaging/Media`**
- [ ] CameraPickerView.swift
- [ ] ChatCameraView.swift
- [ ] ChatMediaOverlayPayload.swift
- [ ] ChatMediaSendMode.swift
- [ ] ViewOnceImmersiveViewer.swift
**`Messaging/Models`**
- [ ] ChatAttachmentAssets.swift
**`Messaging/Screens`**
- [ ] ArchivedConversationsView.swift
**`Messaging/Screens/Chat`**
- [ ] GlassmorphicChatView+Clustering.swift
- [ ] GlassmorphicChatView+ComposerAndChrome.swift
- [ ] GlassmorphicChatView+Lifecycle.swift
- [ ] GlassmorphicChatView+MessageList.swift
- [ ] GlassmorphicChatView+MessageRendering.swift
- [ ] GlassmorphicChatView+Scroll.swift
- [ ] GlassmorphicChatView+Search.swift
- [ ] GlassmorphicChatView+Toolbar.swift
- [ ] GlassmorphicChatView+ViewModelAudio.swift
- [ ] GlassmorphicChatView+Voice.swift
- [ ] GlassmorphicChatView.swift
- [ ] MomentsChatViewModel+Media.swift
**`Messaging/Screens`**
- [ ] ConversationSettingsView.swift
- [ ] MessageRequestsView.swift
- [~] MessagingView.swift → `views/messaging/screens/MessagingView.kt` (inbox + thread texto; glass/media = abiertos)
**`Messaging/Services`**
- [x] ChatAccessCoordinator.swift → `views/messaging/services/ChatAccessCoordinator.kt`
- [ ] ChatBuzzProcessedStore.swift
- [ ] ChatDraftStore.swift
- [ ] ChatGiphyService.swift
- [ ] ChatKeyboardScrollCoordinator.swift
- [x] ChatNavigationIntentStore.swift → `views/messaging/services/ChatNavigationIntentStore.kt`
- [ ] ChatRowHeightEstimator.swift
- [ ] ChatScrollDebug.swift
- [ ] ChatScrollStateStore.swift
- [ ] ChatService+Buzz.swift
- [ ] ChatService+ChunkedVideoUpload.swift
- [x] ChatService+EncryptedMediaResolver.swift → `views/messaging/services/ChatEncryptedMediaResolver.kt`
- [ ] ChatService+EphemeralCleanup.swift
- [ ] ChatService+LocalFirstSnapshot.swift
- [x] ChatService+MediaPipeline.swift → `views/messaging/services/ChatServiceMediaPipeline.kt`
- [ ] ChatService+MessageActions.swift
- [ ] ChatService+MessageHydration.swift
- [ ] ChatService+MessageReactions.swift
- [ ] ChatService+Search.swift
- [ ] ChatService+SharingAndViewOnce.swift
- [ ] ChatService+VanishMode.swift
- [x] ChatService.swift → `views/messaging/services/ChatService.kt` (+ `ChatMessageMapper.kt`)
- [x] ChatSessionEngine.swift → `views/messaging/services/ChatSessionEngine.kt`
- [ ] ChatVideoPosterGenerator.swift
- [x] LiveLocationSharingService.swift → `views/messaging/services/LiveLocationSharingService.kt` (teardown logout)
- [ ] ViewOnceConsumptionService.swift
**`Misc`**
- [ ] WhatsNewView.swift
**`Nova/AI`**
- [ ] NovaAIService.swift
- [ ] NovaGenerationConfig.swift
- [ ] NovaPromptCatalog.swift
**`Nova/Agent`**
- [ ] NovaAgent.swift
- [ ] NovaContextAssembler.swift
- [ ] NovaPendingAction.swift
- [ ] NovaToolExecutor.swift
- [ ] NovaToolRegistry.swift
**`Nova/Conversation`**
- [ ] NovaConversationStore.swift
**`Nova`**
- [ ] Conversationmodels.swift
**`Nova/Core`**
- [ ] NovaLocaleContext.swift
**`Nova/Memory`**
- [ ] NovaContextStore.swift
- [ ] NovaMemoryCrypto.swift
- [ ] NovaMemoryEngine.swift
- [ ] NovaMemoryModels.swift
- [ ] NovaMemoryStore.swift
**`Nova/NovaCore`**
- [ ] NovaModels.swift
- [ ] NovaTheme.swift
**`Nova`**
- [ ] NovaMemoryManagementView.swift
**`Nova/NovaSections`**
- [ ] NovaAttachmentSheet.swift
- [ ] NovaChatSection.swift
- [ ] NovaChromeSection.swift
- [ ] NovaHistorySection.swift
- [ ] NovaInputSection.swift
**`Nova`**
- [ ] NovaView.swift
**`Nova/Tools`**
- [ ] NovaActivityTools.swift
- [ ] NovaMemoryTools.swift
- [ ] NovaMomentAudience.swift
- [ ] NovaMomentDraftParser.swift
- [ ] NovaProfileTools.swift
- [ ] NovaSocialTools.swift
**`Nova/UI`**
- [ ] NovaActionConfirmationOverlay.swift
**`Permission/camera`**
- [ ] Contentview.swift
**`Permission/camera/helpers`**
- [ ] CameraPermissionsview.swift
**`Permission/location`**
- [ ] LocationPermissionView.swift
**`Permission/microphone`**
- [ ] MicrophonePermissionView.swift
**`Permission/notifications`**
- [ ] NotificationsPermissionView.swift
**`Permission/photos`**
- [ ] PhotosPermissionView.swift
**`Permission/shared`**
- [ ] LocationPermissionGate.swift
- [ ] PermissionPhoneFrame.swift
- [ ] PermissionPhoneWallpaper.swift
- [ ] PermissionPrimerGate.swift
- [ ] PermissionPrimerScaffold.swift
**`Permission/tracking`**
- [ ] TrackingPermissionView.swift
**`Permissions`**
- [ ] CameraAccessBoundary.swift
- [ ] CameraPermissionGate.swift
**`Profile/Core`**
- [ ] MomentGridPreview.swift
- [ ] ProfileView.swift
- [ ] ProfileViewModel.swift
**`Profile/Core/Sections`**
- [ ] ProfileBentoLayout.swift
- [ ] ProfileGridHeroTransition.swift
- [ ] ProfileGridMomentMenu.swift
- [ ] ProfileGridPreviewEditorView.swift
- [ ] ProfileHeaderSection.swift
- [ ] ProfileHeaderSkeletonView.swift
- [ ] ProfileMomentZoomNavigation.swift
- [ ] ProfileMomentsSection.swift
- [ ] ProfileSavedSection.swift
- [ ] ProfileSharedComponents.swift
- [ ] ProfileShellComponents.swift
- [ ] UserProfileZoomNavigation.swift
**`Profile/Core`**
- [ ] SharedActivityDetailView.swift
- [ ] SharedActivityView.swift
- [ ] SocialConnectionUserRow.swift
- [ ] SocialConnectionsView.swift
- [ ] UserListView.swift
**`Profile/Editor`**
- [ ] PhotoCropEditorView.swift
- [ ] ProfileEditor.swift
**`Profile/Editor/Sections`**
- [ ] ProfileEditorPickerViews.swift
**`Profile/Highlights`**
- [ ] HighlightComponents.swift
- [ ] HighlightCreateFlowView.swift
- [ ] HighlightCreateFlowViewModel.swift
- [ ] HighlightNameCoverStep.swift
- [ ] HighlightPresentationCoordinator.swift
- [ ] HighlightSelectStoriesStep.swift
- [ ] HighlightViewer.swift
- [ ] ProfileHighlightsView.swift
**`Profile/Incognito`**
- [ ] IncognitoGlobalOverlay.swift
- [ ] IncognitoModeSheet.swift
**`Profile/MomentsView`**
- [x] ContextMenu.swift → `views/profile/momentsview/ContextMenu.kt` (`ModernContextMenuOverlay`; cableado en FeedOverlays)
- [~] EditMomentView.swift → `views/profile/momentsview/EditMomentView.kt` (cableado desde Feed + SingleMomentDetail; AudienceSelector/LocationPicker/PhotoTag = pickers simples / stubs honestos)
- [ ] ModernMomentDetailView.swift
**`Profile/Theme`**
- [ ] EnhancedProfileBackground.swift
- [ ] ProfileTheme.swift
- [ ] ProfileThemeDemo.swift
- [ ] ProfileThemeSelector.swift
**`Profile/UserProfile/Sections`**
- [ ] UserProfileAvatarBadges.swift
- [ ] UserProfileHeaderSection.swift
- [ ] UserProfileMomentsSection.swift
- [ ] UserProfileOverviewSection.swift
- [ ] UserProfilePublicProfileView.swift
- [ ] UserProfileRelationshipViews.swift
- [ ] UserProfileSharedViews.swift
- [ ] UserProfileStateViews.swift
**`Profile/UserProfile`**
- [~] UserProfileView.swift → `views/profile/userprofile/UserProfileView.kt` (sheet Feed + tab propio MVP)
- [~] UserProfileViewModel.swift → `views/profile/userprofile/UserProfileViewModel.kt`
**`Settings`**
- [ ] AccountHistoryActivityView.swift
- [ ] AccountManagement.swift
- [ ] ActivityCollapsibleFilterScroll.swift
- [ ] BlockedUsersView.swift
- [ ] ChatStorageSettingsView.swift
- [ ] ContentVisibilityView.swift
- [ ] DailyLimitView.swift
- [ ] DataExportView.swift
- [ ] LoginActivityView.swift
- [ ] MuteSettingsView.swift
- [ ] PasswordChangeView.swift
- [ ] QRCode.swift
- [ ] RestModeView.swift
**`Settings/SavedMoments`**
- [ ] SavedMomentsViewModel.swift
**`Settings`**
- [ ] SavedMomentsView.swift
- [ ] SearchHistoryActivityView.swift
- [ ] SetPasswordView.swift
- [ ] SettingsNavigationComponents.swift
**`Settings/SettingsSections`**
- [ ] NotificationSettingsView.swift
- [ ] OnlineStatusSection.swift
- [ ] PersonalInfoSettingsViews.swift
- [ ] SettingsSections.swift
**`Settings`**
- [ ] SettingsView.swift
- [ ] SettingsViewModel.swift
- [ ] TimeSpentCardView.swift
- [ ] TimeSpentDetailsView.swift
- [ ] UserActivityBackendModels.swift
- [ ] UserActivityCache.swift
- [ ] UserActivityComponents.swift
- [ ] UserActivityDetailView.swift
- [ ] UserActivityDetailViewModel.swift
- [ ] UserActivityModels.swift
- [ ] UserActivityRows.swift
- [ ] UserActivitySummaryViewModel.swift
- [ ] UserActivityTypes.swift
- [ ] UserActivityView.swift
**`Shared`**
- [ ] AppErrorBanner.swift
- [ ] BlurView.swift
**`Shared/MomentDetail`**
- [x] MomentDetailContainerView.swift → `views/shared/momentdetail/MomentDetailContainerView.kt` (ProfileCarousel cae a Single hasta portar ModernMomentDetailView)
- [x] MomentDetailContext.swift → `views/shared/momentdetail/MomentDetailContext.kt`
- [~] SingleMomentDetailView.swift → `views/shared/momentdetail/SingleMomentDetailView.kt` (card/chrome/menu/peek/mapa/delete + ModernComments; Edit/Explore = placeholders)
**`Shared`**
- [ ] MomentsVideoPlaybackTimeline.swift
- [ ] MomentsVideoPlayer.swift
- [ ] OfflineBannerModifier.swift
- [ ] PhotoTagOverlayView.swift
- [x] ScreenshotProtectedView.swift → `views/shared/ScreenshotProtectedView.kt`
**`comments`**
- [x] CommentMentionSearchOverlay.swift → `views/comments/CommentMentionSearchOverlay.kt`
- [ ] CommentsView.swift (legacy iOS; el feed usa ModernCommentsView)
- [x] ModernCommentsView.swift → `views/comments/ModernCommentsView.kt` (+ `CommentMuteFilters.kt`, `CommentMentionDraft.kt`, `EnhancedModernCommentRow.kt`) — listener, mute filters, menciones, skeletons, StoryRing, like/reply/edit/delete, moderación; cableado en FeedPresentation + SingleMomentDetail
**`story`**
- [ ] QuestionResponsesView.swift
- [~] StoriesView.swift → `views/story/StoriesView.kt` (MVP ring + viewer; ads/chains omitidos)
- [ ] StoryChainView.swift
- [ ] StoryDeckGestureGate.swift
- [ ] StoryInteractiveStickers.swift
- [ ] StoryModels.swift — modelo `Story` ya en `models/Models.kt`
- [ ] StoryPlaybackCoordinator.swift — timer inline en StoryViewerScreen MVP
- [ ] StoryRepository.swift — usa `FirestoreStoriesRepository.fetchActiveStoriesForUsers`
- [x] StoryRingAvatarView.swift → `views/story/StoryRingAvatarView.kt`
- [ ] StorySegmentedRing.swift
**`story/StoryStickers`**
- [ ] StoryStickerEffects.swift
- [ ] StoryStickerViews.swift
**`story`**
- [~] StoryViewModel.swift → `views/story/StoryViewModel.kt` (carga + privacy + markSeen)
**`story/StoryViewer`**
- [ ] StoryDeckInteractionLayout.swift
- [ ] StoryGestureCoordinator.swift
- [ ] StoryLiveTextOverlayView.swift
- [ ] StoryMediaOverlayRendererView.swift
- [ ] StoryQuickActionsMenu.swift
- [ ] StoryReplyViews.swift
- [ ] StoryUserDeckPager.swift — swipe usuarios simplificado en StoriesView
- [ ] StoryViewerBottomComponents.swift
- [ ] StoryViewerLayers.swift
- [ ] StoryViewerLayoutHelpers.swift
- [~] StoryViewerMedia.swift → `views/story/storyviewer/StoryViewerScreen.kt` (`StoryViewerMedia`)
- [~] StoryViewerOverlay.swift — progress + header mínimos en StoryViewerScreen
- [~] StoryViewerScreen.swift → `views/story/storyviewer/StoryViewerScreen.kt` (MVP gestos/timer)
**`story`**
- [ ] archived stories.swift

## Activities  (3/3)

- [x] LiveActivityThumbnailStore.swift → `activities/LiveActivityThumbnailStore.kt` (cache interno; N/A App Group iOS)
- [x] MomentUploadActivityAttributes.swift → `activities/MomentUploadActivityAttributes.kt` + `UploadProgressNotificationHelper.kt`
- [x] StoryUploadActivityAttributes.swift → `activities/StoryUploadActivityAttributes.kt` + `UploadProgressNotificationHelper.kt`

## Coordinators  (5/5)

- [x] AppRouter.swift → `coordinators/AppRouter.kt`
- [x] LegacyNavigationBridge.swift → `coordinators/LegacyNavigationBridge.kt`
- [x] MainViewModel.swift → `coordinators/MainViewModel.kt`
- [x] SharedComponents.swift → `coordinators/SharedComponents.kt`
- [x] TabBarView.swift → `coordinators/TabBarScreen.kt`

## Extensions  (5/5)

- [x] AVAssetImageGenerator+Thumbnail.swift → `extensions/AvAssetThumbnail.kt`
- [x] Color+Hex.swift → `extensions/ColorHex.kt`
- [x] Date+Extensions.swift → `extensions/DateExtensions.kt`
- [x] InterestEmojiHelper.swift → `extensions/InterestEmojiHelper.kt`
- [x] View+LiquidGlass.swift → `extensions/LiquidGlass.kt`

## Models  (0/21)

- [ ] AccountHistoryItem.swift
- [ ] BestFriendsView.swift
- [ ] CachedAction.swift
- [ ] CachedConnection.swift
- [ ] CachedConversation.swift
- [ ] CachedMessage.swift
- [ ] CachedMoment.swift
- [ ] CachedNotification.swift
- [ ] CachedSearch.swift
- [ ] CachedStory.swift
- [ ] CachedUser.swift
- [ ] ChatSecurityModels.swift
- [ ] EchoModels.swift
- [ ] InterestModels.swift
- [ ] Models.swift
- [ ] OutboxPayloads.swift
- [ ] StickerItem.swift
- [ ] User.swift
- [ ] UserAffinity.swift
- [ ] UserBadge.swift
- [ ] VisitsView.swift

## Moderation  (2/2)

- [x] CommentsModerationService.swift → `moderation/CommentsModerationService.kt`
- [x] MediaModerationService.swift → `moderation/MediaModerationService.kt`

## MomentsApp.swift  (1/1)

- [x] MomentsApp.swift → `MomentsApp.kt` (raíz `com.moments.android`, como iOS)

## Notifications  (24/24)

- [x] NotificationGroupedFollowersOverlay.swift → `notifications/components/NotificationGroupedFollowersOverlay.kt`
- [x] NotificationRowComponents.swift → `notifications/components/NotificationRowComponents.kt`
- [x] NotificationSharedViews.swift → `notifications/components/NotificationSharedViews.kt`
- [x] NotificationGroup.swift → `notifications/core/NotificationGroup.kt`
- [x] NotificationRowSupport.swift → `notifications/core/NotificationRowSupport.kt`
- [x] NotificationsViewModel.swift → `notifications/core/NotificationsViewModel.kt`
- [x] EnhancedNotificationRow+Follow.swift → `notifications/row/EnhancedNotificationRowFollow.kt`
- [x] EnhancedNotificationRow+Messages.swift → `notifications/row/EnhancedNotificationRowMessages.kt`
- [x] EnhancedNotificationRow+Previews.swift → `notifications/row/EnhancedNotificationRowPreviews.kt`
- [x] EnhancedNotificationRow+Trailing.swift → `notifications/row/EnhancedNotificationRowTrailing.kt`
- [x] EnhancedNotificationRow.swift → `notifications/row/EnhancedNotificationRow.kt`
- [x] NotificationSummaryPopup.swift → `notifications/screens/NotificationSummaryPopup.kt`
- [x] NotificationsView.swift → `notifications/screens/NotificationsScreen.kt`
- [x] AppDelegate.swift (push) → `notifications/services/MomentsFirebaseMessagingService.kt` + `MainActivity` / `MomentsApplication`
- [x] FCMTokenService.swift → `notifications/services/FCMTokenService.kt`
- [x] InAppNotificationPreviewResolver.swift → `notifications/services/InAppNotificationPreviewResolver.kt`
- [x] InAppNotificationService.swift → `notifications/services/InAppNotificationService.kt`
- [x] NotificationBadgeService.swift → `notifications/services/NotificationBadgeService.kt`
- [x] NotificationCopyResolver.swift → `notifications/services/NotificationCopyResolver.kt`
- [x] NotificationExtensions.swift → `notifications/services/NotificationExtensions.kt`
- [x] NotificationNavigationService.swift → `notifications/services/NotificationNavigationService.kt`
- [x] NotificationOpenIntentStore.swift → `notifications/services/NotificationOpenIntentStore.kt`
- [x] NotificationPresentationCoordinator.swift → `notifications/services/NotificationPresentationCoordinator.kt`
- [x] Notificationservice.swift → `notifications/services/NotificationService.kt` (re-export deprecated en `services/notifications/`)

## Reportes  (7/7)

- [x] AppealFormView.swift → `reportes/AppealFormView.kt` (+ ModerationReviewRequestSheet)
- [x] AppealService.swift → `reportes/AppealService.kt` + `AppealModels.kt` + `AppealError.kt`
- [x] AppealStatus.swift → `reportes/AppealStatusView.kt`
- [x] ModerationReviewStatusView.swift → `reportes/ModerationReviewStatusView.kt`
- [x] ModernReportContent.swift → `reportes/ModernReportContent.kt`
- [x] ReportBottomSheet.swift → `reportes/ReportBottomSheet.kt` + `ReportModels.kt`
- [x] UserReportContent.swift → `reportes/UserReportContent.kt`

## Services  (69 named mappings; 0 parity-certified)

**`Activity`**
- [x] TimeSpentManager.swift → `services/activity/TimeSpentManager.kt`
**`Auth`**
- [x] AuthService.swift → `services/auth/AuthService.kt` (email+Google; Apple/Passkey N/A)
- [x] LoginActivityService.swift → `services/auth/LoginActivityService.kt`
- [x] OnboardingDraftStore.swift → `services/auth/OnboardingDraftStore.kt`
- [x] PasskeyService.swift — N/A (Android: solo Google + email)
**`Cache`**
- [x] CacheManager.swift → `services/cache/CacheManager.kt`
- [x] ImagePrefetchManager.swift → `services/cache/ImagePrefetchManager.kt`
- [x] PersistentAudioCache.swift → `services/cache/PersistentAudioCache.kt`
- [x] PersistentVideoCache.swift → `services/cache/PersistentVideoCache.kt`
- [x] UserCacheService.swift → `services/cache/UserCacheService.kt`
- [x] VideoPreloader.swift → `services/cache/VideoPreloader.kt`
- [x] VideoThumbnailCache.swift → `services/cache/VideoThumbnailCache.kt`
**`Camera`**
- [x] SnapCameraKitConfiguration.swift → `services/camera/SnapCameraKitConfiguration.kt` (feature off)
**`Content`**
- [x] BackendFeedService.swift → `services/content/BackendFeedService.kt`
- [x] FilterService.swift → `services/content/FilterService.kt`
- [x] ForYouDiscoveryService.swift → `services/content/ForYouDiscoveryService.kt`
- [x] ProfileVisitsService.swift → `services/content/ProfileVisitsService.kt`
**`Firestore`**
- [x] FirestoreActivityRepository.swift → `services/firestore/FirestoreActivityRepository.kt`
- [x] FirestoreAudienceRepository.swift → `services/firestore/FirestoreAudienceRepository.kt`
- [~] FirestoreCommentsRepository.swift → `services/firestore/FirestoreCommentsRepository.kt` (reply/mention notification and raw-mention handling differ from iOS)
- [x] FirestoreCore.swift → `services/firestore/FirestoreCore.kt`
- [x] FirestoreHiddenLayersRepository.swift → `services/firestore/FirestoreHiddenLayersRepository.kt`
- [~] FirestoreMomentsRepository.swift → `services/firestore/FirestoreMomentsRepository.kt` (corregido localmente el ID único de audiencia personalizada; falta validar en Firebase/emulador)
- [~] FirestoreProfilesRepository.swift → `services/firestore/FirestoreProfilesRepository.kt` (operaciones principales alineadas; falta recuperación de e-mail/Auth y decodificación legacy de iOS)
- [~] FirestoreSearchRepository.swift → `services/firestore/FirestoreSearchRepository.kt` (API consolidada; degradación de sugerencias alineada y compilada)
- [x] FirestoreService.swift → `services/firestore/FirestoreService.kt`
- [~] FirestoreStoriesRepository.swift → `services/firestore/FirestoreStoriesRepository.kt` (corregido localmente `mapLocation` y los valores de `mapVisibility`; falta validar escritura/lectura con Firebase)
**`Incognito`**
- [x] IncognitoModeService.swift → `services/incognito/IncognitoModeService.kt` (sin Live Activity/Widget iOS)
**`Messaging`**
- [x] ChatCacheStore.swift → `services/messaging/ChatCacheStore.kt`
- [x] ChatCommunicationNotificationService.swift → `services/messaging/ChatCommunicationNotificationService.kt` (shortcuts + Person; MessagingStyle en notificaciones)
- [x] ChatMediaChunkedCipher.swift → `services/messaging/ChatMediaChunkedCipher.kt`
- [x] ChatMediaDownloadPolicy.swift → `services/messaging/ChatMediaDownloadPolicy.kt`
- [x] ChatMediaPrefetcher.swift → `services/messaging/ChatMediaPrefetcher.kt`
- [x] ChatRecoveryCrypto.swift → `services/messaging/ChatRecoveryCrypto.kt`
- [x] ChatSendMessageIntentHandler.swift — N/A (Intents iOS; Android RemoteInput)
- [~] EncryptionService.swift → `services/messaging/EncryptionService.kt` (E2E identity/recovery correction is local; cross-device verification pending)
- [x] LocalFirstMessagingSettings.swift → `services/messaging/LocalFirstMessagingSettings.kt`
- [x] MessageCatchUpService.swift → `services/messaging/MessageCatchUpService.kt`
- [x] MessageIngestService.swift → `services/messaging/MessageIngestService.kt`
- [x] MessageRequestService.swift → `services/messaging/MessageRequestService.kt`
- [x] OnlineStatusService.swift → `services/messaging/OnlineStatusService.kt`
- [x] VanishMessageTimer.swift → `services/messaging/VanishMessageTimer.kt`
**`Network`**
- [x] NetworkMonitor.swift → `services/network/NetworkMonitor.kt`
- [x] OfflineSyncService.swift → `services/network/OfflineSyncService.kt`
**`Nova`**
- [x] NovaEmbeddingService.swift → `services/nova/NovaEmbeddingService.kt`
**`Performance`**
- [x] FeedVisibilityCoordinator.swift → `services/performance/FeedVisibilityCoordinator.kt`
- [x] MotionPolicy.swift → `services/performance/MotionPolicy.kt`
- [x] PerformanceSignposts.swift → `services/performance/PerformanceSignposts.kt`
- [x] VideoMomentsIndex.swift → `services/performance/VideoMomentsIndex.kt`
**`Persistence`**
- [x] LocalPersistenceService.swift → `services/persistence/LocalPersistenceService.kt` (JSON/filesDir; StorySeen en archivo aparte)
- [x] MessagePersistenceStore.swift → `services/persistence/MessagePersistenceStore.kt`
- [x] StorySeenStateService (en LocalPersistence iOS) → `services/persistence/StorySeenStateService.kt`
**`Privacy`**
- [x] ContentVisibilityservice.swift → `services/privacy/ContentVisibilityService.kt`
- [~] PrivacyService.swift → `services/privacy/PrivacyService.kt` + `services/privacy/PrivacyServiceExtension.kt` (port dividido: la extensión Kotlin contiene también las rutas avanzadas que Swift conserva en el archivo principal; auditoría de ramas/errores pendiente)
- [x] PrivacyServiceExtension.swift → `services/privacy/PrivacyServiceExtension.kt` (filtrado de contenido y `canViewMoment`)
**`Security`**
- [x] MomentsAppCheckProviderFactory.swift → `services/security/MomentsAppCheckProviderFactory.kt`
**`Social`**
- [x] AffinityTracker.swift → `services/social/AffinityTracker.kt`
- [x] BestFriendsService.swift → `services/social/BestFriendsService.kt`
- [x] EchoService.swift → `services/social/EchoService.kt`
- [x] StoryChainLimitsService.swift → `services/social/StoryChainLimitsService.kt`
- [x] StoryRingCacheService.swift → `services/social/StoryRingCacheService.kt`
**`Storage`**
- [x] MediaUploadService.swift → `services/storage/MediaUploadService.kt`
- [x] StoragePathBuilder.swift → `services/storage/StoragePathBuilder.kt`
- [x] StorageService.swift → `services/storage/StorageService.kt`
- [x] UIImage+StorageUpload.swift → `services/storage/BitmapStorageUpload.kt`
- [x] VideoCompressionService.swift → `services/storage/VideoCompressionService.kt`
**`Video`**
- [x] ReelPrebufferService.swift → `services/video/ReelPrebufferService.kt`
- [x] SharedVideoPlayerPool.swift → `services/video/SharedVideoPlayerPool.kt`
- [x] VideoAdaptivePlayback.swift → `services/video/VideoAdaptivePlayback.kt`
- [x] VideoPlaybackSelector.swift → `services/video/VideoPlaybackSelector.kt`

## Utilities  (11/11)

- [x] ActiveWindowMetrics.swift → `utilities/ActiveWindowMetrics.kt`
- [x] AppLog.swift → `utilities/AppLog.kt`
- [x] EmojiUsageTracker.swift → `utilities/EmojiUsageTracker.kt`
- [x] HapticManager.swift → `utilities/HapticManager.kt`
- [x] LegacyTypographyScale.swift → `utilities/LegacyTypographyScale.kt`
- [x] MentionParsing.swift → `utilities/MentionParsing.kt`
- [x] MomentsAppearModifiers.swift → `utilities/MomentsAppearModifiers.kt`
- [x] MomentsAudioSession.swift → `utilities/MomentsAudioSession.kt`
- [x] MomentsFormat.swift → `utilities/MomentsFormat.kt`
- [x] MomentsPressButtonStyle.swift → `utilities/MomentsPressButtonStyle.kt`
- [x] OrientationManager.swift → `utilities/OrientationManager.kt`


## ViewModels  (1/1)

- [x] EchoViewModel.swift → `viewmodels/EchoViewModel.kt` (StateFlow)

## ad  (4/4)

- [x] AdAspectRatioContext.swift → `ad/AdAspectRatioContext.kt`
- [x] AdMob Configuration.swift → `ad/AdMobConfiguration.kt` (+ NativeAdManager, PlusAdManager)
- [x] FeedNativeAd.swift → `ad/FeedNativeAd.kt` (Compose)
- [x] StoryNativeAd.swift → `ad/StoryNativeAd.kt` (Compose)
