# Moments — Inventario de archivos (port iOS → Android)

Checklist **archivo por archivo** de los `.swift` del target iOS y su equivalente en Android. Generado desde `../Moments/Moments`. Complementa a [PORT_CHECKLIST.md](PORT_CHECKLIST.md) (vista por carpeta).

**Progreso: 13 / 574 archivos** (2%). Marcado `[x]` = portado o N/A. La lógica no-UI (Services/Models/…) se ha portado en parte inline.

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
- [ ] AudienceSelectionView.swift
- [ ] CustomAudienceManagementViews.swift
- [ ] CustomListSelectorView.swift
**`Creator`**
- [ ] BackgroundMomentUploadService.swift
- [ ] BackgroundStoryUploadService.swift
**`Creator/CameraKit`**
- [ ] CameraKitSpike.swift
- [ ] LensReel.swift
**`Creator`**
- [ ] ChainConfigurationView.swift
- [ ] ChainContinuationSelectorView.swift
**`Creator/Components`**
- [ ] CaptureButton.swift
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
- [ ] StoryDrawingEditorOverlay.swift
- [ ] StoryEditingControls.swift
- [ ] StoryEditorTextTypes.swift
- [ ] StoryFilterSelectorView.swift
- [ ] StoryFontRegistry.swift
- [ ] StoryTextAttributesBuilder.swift
- [ ] StoryTextEditorChrome.swift
- [ ] StoryTextGradientSettings.swift
- [ ] StoryTextMotionEngine.swift
- [ ] StoryTextOverlayLabel.swift
- [ ] StoryTextOverlayMetadata.swift
- [ ] StoryTextVisualRenderer.swift
- [ ] StoryVideoPlayerView.swift
**`Creator/CreatorScreens`**
- [ ] AlbumPickerView.swift
- [ ] CaptionAndDetailsView.swift
- [ ] ContentTypeSelectionView.swift
- [ ] FilterOption.swift
- [ ] LocationPickerView.swift
- [ ] MediaEditingView.swift
- [ ] MediaGridCell.swift
- [ ] MediaSelectionView.swift
- [ ] StickerOverlayView.swift
- [ ] StoryCameraView.swift
- [ ] StoryOverlaysView.swift
- [ ] StoryTextEditor.swift
- [ ] UserSearchView.swift
**`Creator`**
- [ ] CreatorSharedModels.swift
**`Creator/CreatorUIKit`**
- [ ] BackgroundCameraView.swift
- [ ] CameraCapture.swift
- [ ] CameraPreviewView.swift
- [ ] CreatorCaptureGeometry.swift
- [ ] CreatorControls.swift
- [ ] CreatorUIImageExtensions.swift
- [ ] CropViewWrapper.swift
- [ ] DrawingView.swift
- [ ] StoryGalleryPicker.swift
- [ ] StoryMediaPicker.swift
**`Creator`**
- [ ] CreatorView.swift
- [ ] HiddenLayersEditorView.swift
- [ ] PhotoTagSelectionView.swift
- [ ] StickerEmojiPalettePicker.swift
- [ ] StoryVideoProcessingService.swift
- [ ] StoryVideoTrimEditorView.swift
- [ ] VideoEditor.swift
- [ ] stickerview.swift
- [ ] storyeditor.swift
**`Echoes`**
- [ ] EchoHistoryView.swift
- [ ] EchoInvitationView.swift
- [ ] EchoViewerUI.swift
**`Explore`**
- [ ] ExploreGridLayout.swift
- [ ] ExploreMomentDetailView.swift
**`Explore/ExploreSections`**
- [ ] ExploreResultsSection.swift
- [ ] ExploreSuggestionsSection.swift
**`Explore`**
- [ ] ExploreView.swift
- [ ] ExploreViewModel.swift
- [ ] ModernExploreDetailHeader.swift
- [ ] MomentDetailView.swift
- [ ] SuggestedUsersView.swift
**`Feed/Controls`**
- [ ] FeedTypeSelector.swift
- [ ] feedchange.swift
**`Feed/Core`**
- [ ] FeedNotificationRoutingModifier.swift
- [ ] FeedPresentationModifier.swift
- [ ] FeedRoutes.swift
- [ ] FeedView.swift
- [ ] FeedViewModel.swift
- [ ] ModernEmptyFeedView.swift
**`Feed/Core/Sections`**
- [ ] FeedHeaderSection.swift
- [ ] FeedListSection.swift
- [ ] FeedMomentComponents.swift
- [ ] FeedMomentDetailRoute.swift
- [ ] FeedOverlaysSection.swift
- [ ] FeedPostSkeletonView.swift
- [ ] FeedStoryRingComponents.swift
**`Feed/Moments`**
- [ ] ClickableHashtagsView.swift
- [ ] HiddenLayersOverlayView.swift
- [ ] MomentCarouselLayoutRules.swift
**`Feed/Reactions`**
- [ ] MomentReactionButton.swift
- [ ] reacciones.swift
**`Feed/Sharing`**
- [ ] ShareMomentSticker.swift
- [ ] StoryShare.swift
- [ ] share.swift
**`Feed/Stories`**
- [ ] FeedStoryRingCoordinator.swift
- [ ] StoryRingTraySkeleton.swift
**`Feed/Uploads`**
- [ ] FeedUploadProgressRow.swift
- [ ] FloatingMomentUploadOverlay.swift
- [ ] StoryUploadProgressManager.swift
**`Feed/Video`**
- [ ] LiveVideoTimeLabel.swift
- [ ] Reels.swift
- [ ] VideoFeedProgressBar.swift
- [ ] VideoPlaybackChromeStyle.swift
- [ ] VideoPlayer.swift
- [ ] VideoPosterOverlay.swift
**`Feed/maps`**
- [ ] DiscoverMapView.swift
- [ ] LocationMomentDetailView.swift
- [ ] MapAnnotationModels.swift
- [ ] MapDiscoverSupport.swift
- [ ] MapLocationServices.swift
- [ ] MapPlaceBottomSheet.swift
- [ ] MapPlaceClusterEngine.swift
- [ ] MapPlaceStoryDeck.swift
- [ ] MapWeatherEffects.swift
- [ ] Maps.swift
**`Feed/maps/MapsSections`**
- [ ] MapBottomSheetSection.swift
- [ ] MapCanvasSection.swift
**`Feed/maps`**
- [ ] WeatherService.swift
**`Login`**
- [x] AuthUIComponents.swift → `ui/login/AuthComponents.kt`
- [x] CreatingProfileView.swift → `ui/login/CreatingProfileScreen.kt`
- [x] DeactivatedAccountView.swift → `ui/login/AccountStateScreens.kt`
- [x] Interestview.swift — N/A (archivo vacío)
- [x] LiquidGlassComponents.swift → `ui/login/AuthComponents.kt + AuthTheme.kt`
- [x] LoginView.swift → `ui/login/LoginScreen.kt`
- [x] PrivacyPolicyView.swift → `ui/login/PrivacyPolicyScreen.kt`
- [x] ProfileOnboardingView.swift → `ui/login/OnboardingScreen.kt`
- [x] RegisterView.swift → `→ OnboardingScreen.kt`
- [x] SocialProfileCompletionView.swift — N/A (era Apple; Google crea perfil directo)
- [x] SplashScreen.swift → `ui/login/SplashScreen.kt`
- [x] SuspendedAccount.swift → `ui/login/AccountStateScreens.kt`
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
- [ ] MessagingViewModel.swift
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
- [ ] MessagingView.swift
**`Messaging/Services`**
- [ ] ChatAccessCoordinator.swift
- [ ] ChatBuzzProcessedStore.swift
- [ ] ChatDraftStore.swift
- [ ] ChatGiphyService.swift
- [ ] ChatKeyboardScrollCoordinator.swift
- [ ] ChatNavigationIntentStore.swift
- [ ] ChatRowHeightEstimator.swift
- [ ] ChatScrollDebug.swift
- [ ] ChatScrollStateStore.swift
- [ ] ChatService+Buzz.swift
- [ ] ChatService+ChunkedVideoUpload.swift
- [ ] ChatService+EncryptedMediaResolver.swift
- [ ] ChatService+EphemeralCleanup.swift
- [ ] ChatService+LocalFirstSnapshot.swift
- [ ] ChatService+MediaPipeline.swift
- [ ] ChatService+MessageActions.swift
- [ ] ChatService+MessageHydration.swift
- [ ] ChatService+MessageReactions.swift
- [ ] ChatService+Search.swift
- [ ] ChatService+SharingAndViewOnce.swift
- [ ] ChatService+VanishMode.swift
- [ ] ChatService.swift
- [ ] ChatSessionEngine.swift
- [ ] ChatVideoPosterGenerator.swift
- [ ] LiveLocationSharingService.swift
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
- [ ] ContextMenu.swift
- [ ] EditMomentView.swift
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
- [ ] UserProfileView.swift
- [ ] UserProfileViewModel.swift
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
- [ ] MomentDetailContainerView.swift
- [ ] MomentDetailContext.swift
- [ ] SingleMomentDetailView.swift
**`Shared`**
- [ ] MomentsVideoPlaybackTimeline.swift
- [ ] MomentsVideoPlayer.swift
- [ ] OfflineBannerModifier.swift
- [ ] PhotoTagOverlayView.swift
- [ ] ScreenshotProtectedView.swift
**`comments`**
- [ ] CommentMentionSearchOverlay.swift
- [ ] CommentsView.swift
- [ ] ModernCommentsView.swift
**`story`**
- [ ] QuestionResponsesView.swift
- [ ] StoriesView.swift
- [ ] StoryChainView.swift
- [ ] StoryDeckGestureGate.swift
- [ ] StoryInteractiveStickers.swift
- [ ] StoryModels.swift
- [ ] StoryPlaybackCoordinator.swift
- [ ] StoryRepository.swift
- [ ] StoryRingAvatarView.swift
- [ ] StorySegmentedRing.swift
**`story/StoryStickers`**
- [ ] StoryStickerEffects.swift
- [ ] StoryStickerViews.swift
**`story`**
- [ ] StoryViewModel.swift
**`story/StoryViewer`**
- [ ] StoryDeckInteractionLayout.swift
- [ ] StoryGestureCoordinator.swift
- [ ] StoryLiveTextOverlayView.swift
- [ ] StoryMediaOverlayRendererView.swift
- [ ] StoryQuickActionsMenu.swift
- [ ] StoryReplyViews.swift
- [ ] StoryUserDeckPager.swift
- [ ] StoryViewerBottomComponents.swift
- [ ] StoryViewerLayers.swift
- [ ] StoryViewerLayoutHelpers.swift
- [ ] StoryViewerMedia.swift
- [ ] StoryViewerOverlay.swift
- [ ] StoryViewerScreen.swift
**`story`**
- [ ] archived stories.swift

## Activities  (0/3)

- [ ] LiveActivityThumbnailStore.swift
- [ ] MomentUploadActivityAttributes.swift
- [ ] StoryUploadActivityAttributes.swift

## Coordinators  (0/5)

- [ ] AppRouter.swift
- [ ] LegacyNavigationBridge.swift
- [ ] MainViewModel.swift
- [ ] SharedComponents.swift
- [ ] TabBarView.swift

## Extensions  (0/5)

- [ ] AVAssetImageGenerator+Thumbnail.swift
- [ ] Color+Hex.swift
- [ ] Date+Extensions.swift
- [ ] InterestEmojiHelper.swift
- [ ] View+LiquidGlass.swift

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

## Moderation  (0/2)

- [ ] CommentsModerationService.swift
- [ ] MediaModerationService.swift

## MomentsApp.swift  (0/1)

- [ ] MomentsApp.swift

## Notifications  (0/24)

- [ ] NotificationGroupedFollowersOverlay.swift
- [ ] NotificationRowComponents.swift
- [ ] NotificationSharedViews.swift
- [ ] NotificationGroup.swift
- [ ] NotificationRowSupport.swift
- [ ] NotificationsViewModel.swift
- [ ] EnhancedNotificationRow+Follow.swift
- [ ] EnhancedNotificationRow+Messages.swift
- [ ] EnhancedNotificationRow+Previews.swift
- [ ] EnhancedNotificationRow+Trailing.swift
- [ ] EnhancedNotificationRow.swift
- [ ] NotificationSummaryPopup.swift
- [ ] NotificationsView.swift
- [ ] AppDelegate.swift
- [ ] FCMTokenService.swift
- [ ] InAppNotificationPreviewResolver.swift
- [ ] InAppNotificationService.swift
- [ ] NotificationBadgeService.swift
- [ ] NotificationCopyResolver.swift
- [ ] NotificationExtensions.swift
- [ ] NotificationNavigationService.swift
- [ ] NotificationOpenIntentStore.swift
- [~] NotificationPresentationCoordinator.swift → `notifications/NotificationPresentationCoordinator.kt` (stub mínimo para banners; port completo pendiente)
- [ ] Notificationservice.swift

## Reportes  (0/7)

- [ ] AppealFormView.swift
- [ ] AppealService.swift
- [ ] AppealStatus.swift
- [ ] ModerationReviewStatusView.swift
- [ ] ModernReportContent.swift
- [ ] ReportBottomSheet.swift
- [ ] UserReportContent.swift

## Services  (1/69)

**`Activity`**
- [x] TimeSpentManager.swift → `services/activity/TimeSpentManager.kt`
**`Auth`**
- [ ] AuthService.swift
- [ ] LoginActivityService.swift
- [ ] OnboardingDraftStore.swift
- [ ] PasskeyService.swift
**`Cache`**
- [ ] CacheManager.swift
- [ ] ImagePrefetchManager.swift
- [ ] PersistentAudioCache.swift
- [ ] PersistentVideoCache.swift
- [ ] UserCacheService.swift
- [ ] VideoPreloader.swift
- [ ] VideoThumbnailCache.swift
**`Camera`**
- [ ] SnapCameraKitConfiguration.swift
**`Content`**
- [ ] BackendFeedService.swift
- [ ] FilterService.swift
- [ ] ForYouDiscoveryService.swift
- [ ] ProfileVisitsService.swift
**`Firestore`**
- [ ] FirestoreActivityRepository.swift
- [ ] FirestoreAudienceRepository.swift
- [ ] FirestoreCommentsRepository.swift
- [ ] FirestoreCore.swift
- [ ] FirestoreHiddenLayersRepository.swift
- [ ] FirestoreMomentsRepository.swift
- [ ] FirestoreProfilesRepository.swift
- [ ] FirestoreSearchRepository.swift
- [ ] FirestoreService.swift
- [ ] FirestoreStoriesRepository.swift
**`Incognito`**
- [ ] IncognitoModeService.swift
**`Messaging`**
- [ ] ChatCacheStore.swift
- [ ] ChatCommunicationNotificationService.swift
- [ ] ChatMediaChunkedCipher.swift
- [ ] ChatMediaDownloadPolicy.swift
- [ ] ChatMediaPrefetcher.swift
- [ ] ChatRecoveryCrypto.swift
- [ ] ChatSendMessageIntentHandler.swift
- [ ] EncryptionService.swift
- [ ] LocalFirstMessagingSettings.swift
- [ ] MessageCatchUpService.swift
- [ ] MessageIngestService.swift
- [ ] MessageRequestService.swift
- [ ] OnlineStatusService.swift
- [ ] VanishMessageTimer.swift
**`Network`**
- [ ] NetworkMonitor.swift
- [ ] OfflineSyncService.swift
**`Nova`**
- [ ] NovaEmbeddingService.swift
**`Performance`**
- [ ] FeedVisibilityCoordinator.swift
- [ ] MotionPolicy.swift
- [ ] PerformanceSignposts.swift
- [ ] VideoMomentsIndex.swift
**`Persistence`**
- [ ] LocalPersistenceService.swift
- [ ] MessagePersistenceStore.swift
**`Privacy`**
- [ ] ContentVisibilityservice.swift
- [ ] PrivacyService.swift
- [ ] PrivacyServiceExtension.swift
**`Security`**
- [ ] MomentsAppCheckProviderFactory.swift
**`Social`**
- [ ] AffinityTracker.swift
- [ ] BestFriendsService.swift
- [ ] EchoService.swift
- [ ] StoryChainLimitsService.swift
- [ ] StoryRingCacheService.swift
**`Storage`**
- [ ] MediaUploadService.swift
- [ ] StoragePathBuilder.swift
- [ ] StorageService.swift
- [ ] UIImage+StorageUpload.swift
- [ ] VideoCompressionService.swift
**`Video`**
- [ ] ReelPrebufferService.swift
- [ ] SharedVideoPlayerPool.swift
- [ ] VideoAdaptivePlayback.swift
- [ ] VideoPlaybackSelector.swift

## Utilities  (0/11)

- [ ] ActiveWindowMetrics.swift
- [ ] AppLog.swift
- [ ] EmojiUsageTracker.swift
- [ ] HapticManager.swift
- [ ] LegacyTypographyScale.swift
- [ ] MentionParsing.swift
- [ ] MomentsAppearModifiers.swift
- [ ] MomentsAudioSession.swift
- [ ] MomentsFormat.swift
- [ ] MomentsPressButtonStyle.swift
- [ ] OrientationManager.swift

## ViewModels  (0/1)

- [ ] EchoViewModel.swift

## ad  (0/4)

- [ ] AdAspectRatioContext.swift
- [ ] AdMob Configuration.swift
- [ ] FeedNativeAd.swift
- [ ] StoryNativeAd.swift

