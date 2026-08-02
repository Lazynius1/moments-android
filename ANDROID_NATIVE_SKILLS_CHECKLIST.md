# Android Native Skills Checklist — Moments

Checklist para **nativizar** Moments Android con las [Android skills oficiales](https://github.com/android/skills) (Google), no con patrones forkeados de iOS.

| Documento | Pregunta |
|-----------|----------|
| [`IOS_PORT_CHECKLIST.md`](IOS_PORT_CHECKLIST.md) | ¿Está portado lo que hace iOS? |
| [`ANDROID_UI_UX_REVIEW_CHECKLIST.md`](ANDROID_UI_UX_REVIEW_CHECKLIST.md) | ¿Se siente bien estéticamente? |
| **Este archivo** | ¿Usa APIs / patrones oficiales Android (skills Google)? |

Skills instaladas en `.agents/skills/android-*` y `~/.agents/skills/android-*`.

Fecha: 2026-08-02.

## Principio

1. **Paridad de producto** (backend, flujos, datos) sigue viniendo de iOS cuando aplique.
2. **Implementación de plataforma** (insets, nav, cámara, theme, intents, tests, R8) → skills oficiales Android.
3. Si un patrón “igual que SwiftUI/UIKit” choca con Material3 / CameraX / Nav3 / edge-to-edge → **gana el skill Android**.

## Estados

| Estado | Significado |
|--------|-------------|
| `[ ]` | Pendiente de auditar / aplicar skill |
| `[~]` | En curso |
| `[x]` | Aplicado + verificado vs skill |
| `N/A` | No aplica al producto (Wear/XR/Plus/etc.) |
| `[!]` | Hallazgo: patrón iOS-like a corregir |

## Resumen de capas

| # | Capa | Skill(s) | Prioridad | Estado global |
|--:|------|----------|:---------:|:-------------:|
| 1 | Build / toolchain | `android-cli`, `agp-9-upgrade`, `r8-analyzer` | P0 | `[~]` AGP9 OK; minify off |
| 2 | System UI (edge-to-edge + adaptive) | `edge-to-edge`, `adaptive` | P0 | `[~]` shell |
| 3 | Compose theme / styles | `styles`, `migrate-xml-views-to-jetpack-compose` | P0 | `[~]` tokens OK; Styles API experimental diferida |
| 4 | Navegación + deep links | `navigation-3` | P0 | `[x]` 2a/2b overlays+push+synthetic deep links; Up toolbars→navigateUp |
| 5 | Cámara | `camerax` | P1 | `[x]` CameraX Preview/Capture/Video + dispose/stop seguro |
| 6 | Auth / identity | `verified-email` | — | `N/A` Firebase email (paridad iOS); CredMan diferido |
| 7 | Seguridad de Intents | `android-intent-security` | P1 | `[x]` pass cerrado |
| 8 | Testing | `testing-setup` | P1 | `[ ]` |
| 9 | Performance (Perfetto) | `perfetto-trace-analysis`, `perfetto-sql` | P2 | `[ ]` |
| 10 | Play / Engage / Billing / Policy | Engage, Billing, Policy | P3 / N/A parcial | `[ ]` |
| 11 | AppFunctions | `appfunctions` | P3 | `[ ]` |
| 12 | Wear / XR / Glimmer | wear / glimmer | N/A producto phone | `N/A` |

---

## 1. Build / toolchain

**Skills:** `android-cli` · `agp-9-upgrade` · `r8-analyzer`

**Objetivo:** toolchain actual, R8 sano, CLI usable para skills/docs/device.

| Estado | Archivo | Notas |
|:------:|---------|-------|
| `[x]` | `app/build.gradle.kts` | AGP app; compileSdk/target 37; minify release **off** |
| `[x]` | `gradle/libs.versions.toml` | AGP 9.3.1 + Kotlin 2.4.10 + Compose BOM 2026.06 |
| `[x]` | `settings.gradle.kts` | FAIL_ON_PROJECT_REPOS OK |
| `[x]` | `build.gradle.kts` | plugins apply false OK |
| `[x]` | `gradle.properties` | suppressCompileSdk→37; sin fullMode=false |
| `[~]` | `app/proguard-rules.pro` | casi vacío; minify aún off → r8-analyzer pendiente |

**Acciones tipicas:**
- [x] Confirmar AGP 9 + Kotlin plugin alineados con skill `agp-9-upgrade`
- [x] Instalar/usar `android` CLI (`android-cli`) — `~/.local/bin/android` 1.0.15985488
- [ ] Auditar `proguard-rules.pro` + minify release con `r8-analyzer` (activar minify primero)
- [ ] `isMinifyEnabled` release cuando toque

---

## 2. System UI — edge-to-edge + adaptive

**Skills:** `edge-to-edge` · `adaptive`

**Objetivo:** content bajo status/nav bars con WindowInsets; layouts que no asuman iPhone fijo; foldables/tablets cuando aplique.

### Shell / métricas / theme base
| Estado | Archivo | Notas |
|:------:|---------|-------|
| `[x]` | `MainActivity.kt` | enableEdgeToEdge + barras transparentes + contrast sync |
| `[ ]` | `MomentsApp.kt` | insets / window / chrome |
| `[ ]` | `MomentsApplication.kt` | insets / window / chrome |
| `[ ]` | `coordinators/AppRouter.kt` | Nav3 pendiente |
| `[ ]` | `coordinators/LegacyNavigationBridge.kt` | Nav3 pendiente |
| `[ ]` | `coordinators/MainViewModel.kt` | — |
| `[ ]` | `coordinators/NavigationEventBus.kt` | — |
| `[ ]` | `coordinators/SharedComponents.kt` | — |
| `[x]` | `coordinators/TabBarView.kt` | Scaffold contentWindowInsets; dock bajo nav bar; Δ adaptive rail |
| `[x]` | `views/shared/Theme.kt` | light+dark Material3 (ink/paper); MotionScheme internal → MomentsMotion |
| `[x]` | `views/shared/Type.kt` | Typography M3 + Inter; Styles API experimental **diferida** |
| `[x]` | `views/shared/Color.kt` | Ink/Surface + ControlDark/Light (chrome elevado) |
| `[x]` | `views/feed/AdaptiveColors.kt` | canvas + controlSurface/stroke + placeholder; textos sin Gray |
| `[x]` | `views/feed/FeedStyle.kt` | re-export → AdaptiveColors |
| `[x]` | `views/login/AuthTheme.kt` | AuthColors ↔ Ink/Surface |
| `[x]` | `views/nova/novacore/NovaTheme.kt` | background ↔ Ink/Surface |
| `[x]` | `views/shared/MomentsModalSheet.kt` | host M3 sheets |
| `[x]` | `extensions/LiquidGlass.kt` | chrome opaco elevado (#151D21 / blanco); no fill=canvas |
| `[x]` | `views/shared/MomentsMotion.kt` | MotionScheme + container transform |
| `[x]` | `views/shared/MomentsSharedTransition.kt` | SharedTransition host |
| `[ ]` | `utilities/ActiveWindowMetrics.kt` | — |
| `[ ]` | `utilities/MomentsAppearModifiers.kt` | — |
| `[x]` | `res/values/themes.xml` + `values-night/` | status/nav transparent + light/dark icons |

### UI completa (todas las capas visuales) — aplicar edge-to-edge al tocar cada superficie

_Inventario UI: **~499** archivos `.kt` de presentación (excl. services no-UI)._

#### `ad` (4)

| Estado | Archivo | Skills |
|:------:|---------|--------|
| `[ ]` | `ad/AdAspectRatioContext.kt` | edge-to-edge |
| `[ ]` | `ad/AdMobConfiguration.kt` | edge-to-edge |
| `[ ]` | `ad/FeedNativeAd.kt` | edge-to-edge |
| `[ ]` | `ad/StoryNativeAd.kt` | edge-to-edge |

#### `models` (2)

| Estado | Archivo | Skills |
|:------:|---------|--------|
| `[ ]` | `models/BestFriendsView.kt` | edge-to-edge |
| `[ ]` | `models/VisitsView.kt` | edge-to-edge |

#### `notifications/components` (3)

| Estado | Archivo | Skills |
|:------:|---------|--------|
| `[ ]` | `notifications/components/NotificationGroupedFollowersOverlay.kt` | edge-to-edge |
| `[ ]` | `notifications/components/NotificationRowComponents.kt` | edge-to-edge |
| `[ ]` | `notifications/components/NotificationSharedViews.kt` | edge-to-edge |

#### `notifications/row` (5)

| Estado | Archivo | Skills |
|:------:|---------|--------|
| `[ ]` | `notifications/row/EnhancedNotificationRow.kt` | edge-to-edge |
| `[ ]` | `notifications/row/EnhancedNotificationRowFollow.kt` | edge-to-edge |
| `[ ]` | `notifications/row/EnhancedNotificationRowMessages.kt` | edge-to-edge |
| `[ ]` | `notifications/row/EnhancedNotificationRowPreviews.kt` | edge-to-edge |
| `[ ]` | `notifications/row/EnhancedNotificationRowTrailing.kt` | edge-to-edge |

#### `notifications/screens` (2)

| Estado | Archivo | Skills |
|:------:|---------|--------|
| `[ ]` | `notifications/screens/NotificationSummaryPopup.kt` | edge-to-edge |
| `[ ]` | `notifications/screens/NotificationsView.kt` | edge-to-edge |

#### `reportes` (10)

| Estado | Archivo | Skills |
|:------:|---------|--------|
| `[ ]` | `reportes/AppealError.kt` | edge-to-edge |
| `[ ]` | `reportes/AppealFormView.kt` | edge-to-edge |
| `[ ]` | `reportes/AppealModels.kt` | edge-to-edge |
| `[ ]` | `reportes/AppealService.kt` | edge-to-edge |
| `[ ]` | `reportes/AppealStatus.kt` | edge-to-edge |
| `[ ]` | `reportes/ModerationReviewStatusView.kt` | edge-to-edge |
| `[ ]` | `reportes/ModernReportContent.kt` | edge-to-edge |
| `[ ]` | `reportes/ReportBottomSheet.kt` | edge-to-edge |
| `[ ]` | `reportes/ReportModels.kt` | edge-to-edge |
| `[ ]` | `reportes/UserReportContent.kt` | edge-to-edge |

#### `views/comments` (6)

| Estado | Archivo | Skills |
|:------:|---------|--------|
| `[ ]` | `views/comments/CommentMentionDraft.kt` | edge-to-edge |
| `[ ]` | `views/comments/CommentMentionSearchOverlay.kt` | edge-to-edge |
| `[ ]` | `views/comments/CommentMuteFilters.kt` | edge-to-edge |
| `[ ]` | `views/comments/EnhancedModernCommentRow.kt` | edge-to-edge |
| `[ ]` | `views/comments/ModernCommentsSheet.kt` | edge-to-edge |
| `[ ]` | `views/comments/ModernCommentsView.kt` | edge-to-edge |

#### `views/components` (22)

| Estado | Archivo | Skills |
|:------:|---------|--------|
| `[ ]` | `views/components/AnimatedStickerView.kt` | edge-to-edge |
| `[ ]` | `views/components/AudienceIconView.kt` | edge-to-edge |
| `[ ]` | `views/components/CommentRowSkeletonView.kt` | edge-to-edge |
| `[ ]` | `views/components/EchoesIconView.kt` | edge-to-edge |
| `[ ]` | `views/components/InAppBannerView.kt` | edge-to-edge |
| `[ ]` | `views/components/InAppMessageQuickReplyPanel.kt` | edge-to-edge |
| `[ ]` | `views/components/IntelligentGlow.kt` | edge-to-edge |
| `[ ]` | `views/components/InteractiveStickerSharedViews.kt` | edge-to-edge |
| `[ ]` | `views/components/LiveUsernameText.kt` | edge-to-edge |
| `[ ]` | `views/components/LocationMomentCardSkeletonView.kt` | edge-to-edge |
| `[ ]` | `views/components/MomentCaptionView.kt` | edge-to-edge |
| `[ ]` | `views/components/MomentHashtagText.kt` | edge-to-edge |
| `[ ]` | `views/components/MomentRailComponents.kt` | edge-to-edge |
| `[ ]` | `views/components/MomentRefresh.kt` | edge-to-edge |
| `[ ]` | `views/components/MomentRowButton.kt` | edge-to-edge |
| `[ ]` | `views/components/OfflineBanner.kt` | edge-to-edge |
| `[ ]` | `views/components/RefreshControl.kt` | edge-to-edge |
| `[ ]` | `views/components/SkeletonShimmer.kt` | edge-to-edge |
| `[ ]` | `views/components/StoryViewerSkeletonView.kt` | edge-to-edge |
| `[ ]` | `views/components/UserRowSkeletonView.kt` | edge-to-edge |
| `[ ]` | `views/components/VerifiedBadge.kt` | edge-to-edge |
| `[ ]` | `views/components/hiddenlayers/HiddenLayerLayout.kt` | edge-to-edge |

#### `views/creator` (76)

| Estado | Archivo | Skills |
|:------:|---------|--------|
| `[ ]` | `views/creator/BackgroundMomentUploadService.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/BackgroundStoryUploadService.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/ChainConfigurationView.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/ChainContinuationSelectorView.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/CreatorSharedModels.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/CreatorView.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/EmojiPickerView.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/HiddenLayersEditorView.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/PhotoTagSelectionView.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/RevealStickerEditor.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/StickerEmojiPalettePicker.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/StoryStickerDraft.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/StoryStickerRebuild.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/StoryVideoOverlayBake.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/StoryVideoProcessingService.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/StoryVideoTrimEditorView.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/VideoEditor.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/audienceselector/AudienceModels.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/audienceselector/AudienceSelectionRows.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/audienceselector/AudienceSelectionView.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/audienceselector/CustomAudienceManagementViews.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/audienceselector/CustomListSelectorView.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/camerakit/CameraKitSpike.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/camerakit/LensReel.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/components/CaptureButton.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/components/EditableImageView.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/components/StickerDetailPalette.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/components/StickerGiphyViews.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/components/StickerInputViews.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/components/StickerLocationInputView.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/components/StickerMediaInputs.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/components/StickerPickerGeneratedStickers.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/components/StickerPickerLayout.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/components/StickerPickerSupportExtensions.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/components/StoryBackgroundPresets.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/components/StoryColorPickerView.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/components/StoryDominantColorsExtractor.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/components/StoryDrawingEditorOverlay.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/components/StoryEditingControls.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/components/StoryEditorTextTypes.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/components/StoryFilterSelectorView.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/components/StoryFontRegistry.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/components/StoryTextAttributesBuilder.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/components/StoryTextEditorChrome.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/components/StoryTextGradientSettings.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/components/StoryTextMotionEngine.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/components/StoryTextOverlayLabel.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/components/StoryTextOverlayMetadata.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/components/StoryTextVisualRenderer.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/components/StoryVideoPlayerView.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/creatorscreens/AlbumPickerView.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/creatorscreens/CaptionAndDetailsView.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/creatorscreens/ContentTypeSelectionView.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/creatorscreens/CreatorFlowPendingScreen.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/creatorscreens/FilterOption.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/creatorscreens/LocationPickerView.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/creatorscreens/MediaEditingView.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/creatorscreens/MediaGridCell.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/creatorscreens/MediaSelectionView.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/creatorscreens/StickerOverlayView.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/creatorscreens/StoryCameraView.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/creatorscreens/StoryOverlaysView.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/creatorscreens/StoryTextEditor.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/creatorscreens/UserSearchView.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/creatoruikit/BackgroundCameraView.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/creatoruikit/CameraCapture.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/creatoruikit/CameraPreviewView.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/creatoruikit/CreatorCaptureGeometry.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/creatoruikit/CreatorControls.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/creatoruikit/CreatorUIImageExtensions.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/creatoruikit/CropViewWrapper.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/creatoruikit/DrawingView.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/creatoruikit/StoryGalleryPicker.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/creatoruikit/StoryMediaPicker.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/stickerview.kt` | edge-to-edge, camerax |
| `[ ]` | `views/creator/storyeditor.kt` | edge-to-edge, camerax |

#### `views/echoes` (3)

| Estado | Archivo | Skills |
|:------:|---------|--------|
| `[ ]` | `views/echoes/EchoHistoryView.kt` | edge-to-edge |
| `[ ]` | `views/echoes/EchoInvitationView.kt` | edge-to-edge |
| `[ ]` | `views/echoes/EchoViewerUI.kt` | edge-to-edge |

#### `views/explore` (10)

| Estado | Archivo | Skills |
|:------:|---------|--------|
| `[ ]` | `views/explore/ExploreGridLayout.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/explore/ExploreMomentDetailView.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/explore/ExploreMomentMapper.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/explore/ExploreView.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/explore/ExploreViewModel.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/explore/ModernExploreDetailHeader.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/explore/SuggestedUsersView.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/explore/SuggestedUsersViewModel.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/explore/exploresections/ExploreResultsSection.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/explore/exploresections/ExploreSuggestionsSection.kt` | edge-to-edge, adaptive |

#### `views/feed` (62)

| Estado | Archivo | Skills |
|:------:|---------|--------|
| `[ ]` | `views/feed/AdaptiveColors.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/FeedStyle.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/controls/FeedTypeSelector.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/controls/feedchange.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/core/FeedNotificationRoutingModifier.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/core/FeedPresentationModifier.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/core/FeedRoutes.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/core/FeedView.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/core/FeedViewModel.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/core/ModernEmptyFeedView.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/core/sections/CarouselImmersivePeek.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/core/sections/FeedHeaderSection.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/core/sections/FeedListSection.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/core/sections/FeedMomentComponents.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/core/sections/FeedMomentDetailRoute.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/core/sections/FeedOverlaysSection.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/core/sections/FeedPostSkeletonView.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/core/sections/FeedStoryRingComponents.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/maps/DiscoverMapView.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/maps/LocationMapChrome.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/maps/LocationMapView.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/maps/LocationMapViewSupport.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/maps/LocationMomentCard.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/maps/LocationMomentDetailSections.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/maps/LocationMomentDetailView.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/maps/MapAnnotationModels.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/maps/MapDiscoverSupport.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/maps/MapLocationServices.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/maps/MapPlaceBottomSheet.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/maps/MapPlaceClusterEngine.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/maps/MapPlaceStoryDeck.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/maps/MapWeatherEffects.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/maps/Maps.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/maps/MomentsMapStyle.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/maps/WeatherService.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/maps/mapssections/MapBottomSheetSection.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/maps/mapssections/MapCanvasSection.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/maps/mapssections/MapHeaderSection.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/moments/ClickableHashtagsView.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/moments/HiddenLayersOverlayView.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/moments/MomentCarouselLayoutRules.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/moments/MomentMediaCarousel.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/reactions/MomentReactionButton.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/reactions/ReactionsListSheet.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/reactions/reacciones.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/sharing/ShareMomentSticker.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/sharing/StoryShare.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/sharing/share.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/stories/FeedStoryRingCoordinator.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/stories/StoryRingLayout.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/stories/StoryRingTraySkeleton.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/uploads/FeedUploadProgressRow.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/uploads/FloatingMomentUploadOverlay.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/uploads/StoryUploadProgressManager.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/video/LiveVideoTimeLabel.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/video/ReelVideoPlayerManager.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/video/ReelVideoView.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/video/Reels.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/video/VideoFeedProgressBar.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/video/VideoPlaybackChromeStyle.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/video/VideoPlayer.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/feed/video/VideoPosterOverlay.kt` | edge-to-edge, adaptive |

#### `views/login` (12)

| Estado | Archivo | Skills |
|:------:|---------|--------|
| `[ ]` | `views/login/AccountStateScreens.kt` | edge-to-edge, verified-email |
| `[ ]` | `views/login/AuthErrors.kt` | edge-to-edge, verified-email |
| `[ ]` | `views/login/AuthTheme.kt` | edge-to-edge, verified-email |
| `[ ]` | `views/login/AuthUIComponents.kt` | edge-to-edge, verified-email |
| `[ ]` | `views/login/CreatingProfileView.kt` | edge-to-edge, verified-email |
| `[ ]` | `views/login/GoogleAuthHelper.kt` | edge-to-edge, verified-email |
| `[ ]` | `views/login/Interests.kt` | edge-to-edge, verified-email |
| `[ ]` | `views/login/LoginView.kt` | edge-to-edge, verified-email |
| `[ ]` | `views/login/PrivacyPolicyView.kt` | edge-to-edge, verified-email |
| `[ ]` | `views/login/ProfileOnboardingView.kt` | edge-to-edge, verified-email |
| `[ ]` | `views/login/RegisterView.kt` | edge-to-edge, verified-email |
| `[ ]` | `views/login/SplashScreen.kt` | edge-to-edge, verified-email |

#### `views/messaging` (86)

| Estado | Archivo | Skills |
|:------:|---------|--------|
| `[ ]` | `views/messaging/attachments/ChatGiphyPickerSheet.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/attachments/ChatLocationSheet.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/components/AttachmentIconView.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/components/ChatAdaptiveColors.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/components/ChatAttachmentSheet.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/components/ChatBuzzEffectViews.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/components/ChatChromeViews.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/components/ChatClusterMediaViews.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/components/ChatEphemeralMessageViews.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/components/ChatFloatingNavigationOverlay.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/components/ChatGifMessageBubble.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/components/ChatInputViews.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/components/ChatKFImageViews.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/components/ChatLocationMessageBubble.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/components/ChatMediaViews.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/components/ChatMessageBubbleViews.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/components/ChatMessageForwardSheet.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/components/ChatMessageInteractionModifiers.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/components/ChatMessageListView.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/components/ChatMessageOptionsMenu.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/components/ChatMessageSupportViews.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/components/ChatRecoveryViews.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/components/ChatSearchNavigationBar.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/components/ChatSpeechBubbleViews.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/components/ChatStickerMessageBubble.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/components/ChatVanishModeViews.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/components/ConversationContextMenu.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/components/MediaProgressRing.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/components/MessageTypeIconView.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/components/MessagingComposerAndStatusViews.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/components/ViewOnceMessageBubble.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/components/VoiceNotes.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/components/VoiceRecordingGestureViews.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/core/ChatViewModel.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/core/MessageItem.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/core/MessageModel.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/core/MessagingViewModel.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/media/CameraPickerView.kt` | edge-to-edge, adaptive, camerax |
| `[ ]` | `views/messaging/media/ChatCameraView.kt` | edge-to-edge, adaptive, camerax |
| `[ ]` | `views/messaging/media/ChatMediaOverlayPayload.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/media/ChatMediaSendMode.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/media/ViewOnceImmersiveViewer.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/models/ChatAttachmentAssets.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/screens/ArchivedConversationsView.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/screens/ConversationFullScreenMediaView.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/screens/ConversationSettingsView.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/screens/GlassmorphicConversationRow.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/screens/MessageRequestsView.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/screens/MessagingView.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/screens/chat/GlassmorphicChatView.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/screens/chat/GlassmorphicChatViewClustering.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/screens/chat/GlassmorphicChatViewComposerAndChrome.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/screens/chat/GlassmorphicChatViewLifecycle.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/screens/chat/GlassmorphicChatViewMessageList.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/screens/chat/GlassmorphicChatViewMessageRendering.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/screens/chat/GlassmorphicChatViewScroll.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/screens/chat/GlassmorphicChatViewSearch.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/screens/chat/GlassmorphicChatViewToolbar.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/screens/chat/GlassmorphicChatViewViewModelAudio.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/screens/chat/GlassmorphicChatViewVoice.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/screens/chat/MomentsChatViewModelMedia.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/services/ChatAccessCoordinator.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/services/ChatBuzzProcessedStore.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/services/ChatDraftStore.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/services/ChatEncryptedMediaResolver.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/services/ChatGiphyService.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/services/ChatKeyboardScrollCoordinator.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/services/ChatNavigationIntentStore.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/services/ChatRowHeightEstimator.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/services/ChatScrollStateStore.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/services/ChatService.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/services/ChatServiceBuzz.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/services/ChatServiceChunkedVideoUpload.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/services/ChatServiceEphemeralCleanup.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/services/ChatServiceLocalFirstSnapshot.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/services/ChatServiceMediaPipeline.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/services/ChatServiceMessageActions.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/services/ChatServiceMessageHydration.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/services/ChatServiceMessageReactions.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/services/ChatServiceSearch.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/services/ChatServiceSharingAndViewOnce.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/services/ChatServiceVanishMode.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/services/ChatSessionEngine.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/services/ChatVideoPosterGenerator.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/services/LiveLocationSharingService.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/messaging/services/ViewOnceConsumptionService.kt` | edge-to-edge, adaptive |

#### `views/misc` (1)

| Estado | Archivo | Skills |
|:------:|---------|--------|
| `[ ]` | `views/misc/WhatsNewView.kt` | edge-to-edge |

#### `views/nova` (34)

| Estado | Archivo | Skills |
|:------:|---------|--------|
| `[ ]` | `views/nova/Conversationmodels.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/nova/NovaMemoryManagementView.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/nova/NovaView.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/nova/agent/NovaAgent.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/nova/agent/NovaContextAssembler.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/nova/agent/NovaPendingAction.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/nova/agent/NovaToolExecutor.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/nova/agent/NovaToolRegistry.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/nova/ai/NovaAIService.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/nova/ai/NovaGenerationConfig.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/nova/ai/NovaPromptCatalog.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/nova/conversation/NovaConversationStore.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/nova/core/NovaLocaleContext.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/nova/memory/NovaContextStore.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/nova/memory/NovaMemoryCrypto.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/nova/memory/NovaMemoryEngine.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/nova/memory/NovaMemoryModels.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/nova/memory/NovaMemoryStore.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/nova/novacore/NovaModels.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/nova/novacore/NovaTheme.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/nova/novasections/NovaAttachmentSheet.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/nova/novasections/NovaChatSection.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/nova/novasections/NovaChatTextFormatting.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/nova/novasections/NovaChromeSection.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/nova/novasections/NovaHistorySection.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/nova/novasections/NovaInputSection.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/nova/tools/NovaActivityTools.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/nova/tools/NovaEvents.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/nova/tools/NovaMemoryTools.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/nova/tools/NovaMomentAudience.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/nova/tools/NovaMomentDraftParser.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/nova/tools/NovaProfileTools.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/nova/tools/NovaSocialTools.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/nova/ui/NovaActionConfirmationOverlay.kt` | edge-to-edge, adaptive |

#### `views/permission` (15)

| Estado | Archivo | Skills |
|:------:|---------|--------|
| `[ ]` | `views/permission/camera/Contentview.kt` | edge-to-edge, camerax |
| `[ ]` | `views/permission/camera/helpers/CameraPermissionsview.kt` | edge-to-edge, camerax |
| `[ ]` | `views/permission/location/LocationPermissionView.kt` | edge-to-edge |
| `[ ]` | `views/permission/microphone/MicrophonePermissionView.kt` | edge-to-edge |
| `[ ]` | `views/permission/notifications/NotificationsPermissionView.kt` | edge-to-edge |
| `[ ]` | `views/permission/photos/PhotosPermissionView.kt` | edge-to-edge |
| `[ ]` | `views/permission/shared/LocationPermissionGate.kt` | edge-to-edge |
| `[ ]` | `views/permission/shared/PermissionLoopTime.kt` | edge-to-edge |
| `[ ]` | `views/permission/shared/PermissionMockOverflow.kt` | edge-to-edge |
| `[ ]` | `views/permission/shared/PermissionPhoneFrame.kt` | edge-to-edge |
| `[ ]` | `views/permission/shared/PermissionPhoneWallpaper.kt` | edge-to-edge |
| `[ ]` | `views/permission/shared/PermissionPrimerFullScreenDialog.kt` | edge-to-edge |
| `[ ]` | `views/permission/shared/PermissionPrimerGate.kt` | edge-to-edge |
| `[ ]` | `views/permission/shared/PermissionPrimerScaffold.kt` | edge-to-edge |
| `[ ]` | `views/permission/tracking/TrackingPermissionView.kt` | edge-to-edge |

#### `views/permissions` (2)

| Estado | Archivo | Skills |
|:------:|---------|--------|
| `[ ]` | `views/permissions/CameraAccessBoundary.kt` | edge-to-edge, camerax |
| `[ ]` | `views/permissions/CameraPermissionGate.kt` | edge-to-edge, camerax |

#### `views/profile` (47)

| Estado | Archivo | Skills |
|:------:|---------|--------|
| `[ ]` | `views/profile/core/MomentGridPreview.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/profile/core/ProfileView.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/profile/core/ProfileViewModel.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/profile/core/SharedActivityDetailView.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/profile/core/SharedActivityView.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/profile/core/SocialConnectionUserRow.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/profile/core/SocialConnectionsView.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/profile/core/UserListView.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/profile/core/sections/ProfileBentoLayout.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/profile/core/sections/ProfileGridHeroTransition.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/profile/core/sections/ProfileGridMomentMenu.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/profile/core/sections/ProfileGridPreviewEditorView.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/profile/core/sections/ProfileHeaderSection.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/profile/core/sections/ProfileHeaderSkeletonView.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/profile/core/sections/ProfileMomentZoomNavigation.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/profile/core/sections/ProfileMomentsSection.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/profile/core/sections/ProfileSavedSection.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/profile/core/sections/ProfileSharedComponents.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/profile/core/sections/ProfileShellComponents.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/profile/core/sections/UserProfileZoomNavigation.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/profile/editor/PhotoCropEditorView.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/profile/editor/ProfileEditor.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/profile/editor/sections/ProfileEditorPickerViews.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/profile/highlights/HighlightComponents.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/profile/highlights/HighlightCreateFlowView.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/profile/highlights/HighlightCreateFlowViewModel.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/profile/highlights/HighlightNameCoverStep.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/profile/highlights/HighlightPresentationCoordinator.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/profile/highlights/HighlightSelectStoriesStep.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/profile/highlights/HighlightViewer.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/profile/highlights/ProfileHighlightsView.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/profile/incognito/IncognitoGlobalOverlay.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/profile/incognito/IncognitoModeSheet.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/profile/momentsview/ContextMenu.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/profile/momentsview/EditMomentView.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/profile/momentsview/ModernMomentDetailView.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/profile/momentsview/ModernSavedMomentsDetailView.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/profile/userprofile/UserProfileView.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/profile/userprofile/UserProfileViewModel.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/profile/userprofile/sections/UserProfileAvatarBadges.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/profile/userprofile/sections/UserProfileHeaderSection.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/profile/userprofile/sections/UserProfileMomentsSection.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/profile/userprofile/sections/UserProfileOverviewSection.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/profile/userprofile/sections/UserProfilePublicProfileView.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/profile/userprofile/sections/UserProfileRelationshipViews.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/profile/userprofile/sections/UserProfileSharedViews.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/profile/userprofile/sections/UserProfileStateViews.kt` | edge-to-edge, adaptive |

#### `views/settings` (47)

| Estado | Archivo | Skills |
|:------:|---------|--------|
| `[ ]` | `views/settings/AccountHistoryActivityView.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/settings/AccountManagement.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/settings/ActivityCollapsibleFilterScroll.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/settings/BlockedUsersView.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/settings/ChatStorageSettingsView.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/settings/ContentVisibilityView.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/settings/ContentVisibilityViewModel.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/settings/DailyLimitView.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/settings/DataExportView.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/settings/DataExportViewModel.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/settings/LoginActivityView.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/settings/LoginActivityViewModel.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/settings/MuteSettingsView.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/settings/MuteSettingsViewModel.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/settings/PasswordChangeView.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/settings/QRCode.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/settings/RestModeView.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/settings/SearchHistoryActivityView.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/settings/SetPasswordView.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/settings/SettingsNavigationComponents.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/settings/SettingsSearchField.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/settings/SettingsView.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/settings/SettingsViewModel.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/settings/TimeSpentCardView.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/settings/TimeSpentDetailsView.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/settings/UserActivityBackendModels.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/settings/UserActivityCache.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/settings/UserActivityComponents.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/settings/UserActivityDetailView.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/settings/UserActivityDetailViewModel.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/settings/UserActivityModels.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/settings/UserActivityRows.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/settings/UserActivitySummaryViewModel.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/settings/UserActivityTypes.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/settings/UserActivityView.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/settings/savedmoments/ModernSavedMomentsDetailView.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/settings/savedmoments/SavedMomentsView.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/settings/savedmoments/SavedMomentsViewModel.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/settings/sections/AccountSections.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/settings/sections/ContentAndSupportSections.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/settings/sections/PrivacySections.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/settings/sections/SecuritySections.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/settings/sections/SettingsFormView.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/settings/sections/SettingsSharedComponents.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/settings/settingssections/NotificationSettingsView.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/settings/settingssections/OnlineStatusSection.kt` | edge-to-edge, adaptive |
| `[ ]` | `views/settings/settingssections/PersonalInfoSettingsViews.kt` | edge-to-edge, adaptive |

#### `views/shared` (17)

| Estado | Archivo | Skills |
|:------:|---------|--------|
| `[ ]` | `views/shared/AppErrorBanner.kt` | edge-to-edge |
| `[ ]` | `views/shared/ChatPreviewPrivacy.kt` | edge-to-edge |
| `[ ]` | `views/shared/Color.kt` | edge-to-edge, styles |
| `[ ]` | `views/shared/MomentsModalSheet.kt` | edge-to-edge, styles |
| `[ ]` | `views/shared/MomentsSharedTransition.kt` | edge-to-edge |
| `[ ]` | `views/shared/MomentsVideoPlaybackTimeline.kt` | edge-to-edge |
| `[ ]` | `views/shared/MomentsVideoPlayer.kt` | edge-to-edge |
| `[ ]` | `views/shared/OfflineBannerModifier.kt` | edge-to-edge |
| `[ ]` | `views/shared/PhotoTagOverlayView.kt` | edge-to-edge |
| `[ ]` | `views/shared/ScreenshotProtectedView.kt` | edge-to-edge |
| `[ ]` | `views/shared/SecureComposeSurfaceHost.kt` | edge-to-edge |
| `[ ]` | `views/shared/Theme.kt` | edge-to-edge, styles |
| `[ ]` | `views/shared/Type.kt` | edge-to-edge, styles |
| `[ ]` | `views/shared/momentdetail/FeedPinnedTopChrome.kt` | edge-to-edge |
| `[ ]` | `views/shared/momentdetail/MomentDetailContainerView.kt` | edge-to-edge |
| `[ ]` | `views/shared/momentdetail/MomentDetailContext.kt` | edge-to-edge |
| `[ ]` | `views/shared/momentdetail/SingleMomentDetailView.kt` | edge-to-edge |

#### `views/story` (30)

| Estado | Archivo | Skills |
|:------:|---------|--------|
| `[ ]` | `views/story/ArchiveViewModel.kt` | edge-to-edge |
| `[ ]` | `views/story/ArchivedStoriesView.kt` | edge-to-edge |
| `[ ]` | `views/story/QuestionResponsesView.kt` | edge-to-edge |
| `[ ]` | `views/story/StoriesView.kt` | edge-to-edge |
| `[ ]` | `views/story/StoryChainView.kt` | edge-to-edge |
| `[ ]` | `views/story/StoryDeckGestureGate.kt` | edge-to-edge |
| `[ ]` | `views/story/StoryInteractiveStickers.kt` | edge-to-edge |
| `[ ]` | `views/story/StoryModels.kt` | edge-to-edge |
| `[ ]` | `views/story/StoryPlaybackCoordinator.kt` | edge-to-edge |
| `[ ]` | `views/story/StoryRepository.kt` | edge-to-edge |
| `[ ]` | `views/story/StoryRingAvatarView.kt` | edge-to-edge |
| `[ ]` | `views/story/StorySegmentedRing.kt` | edge-to-edge |
| `[ ]` | `views/story/StoryStatsView.kt` | edge-to-edge |
| `[ ]` | `views/story/StoryViewModel.kt` | edge-to-edge |
| `[ ]` | `views/story/storystickers/StoryStickerEffects.kt` | edge-to-edge |
| `[ ]` | `views/story/storystickers/StoryStickerViews.kt` | edge-to-edge |
| `[ ]` | `views/story/storyviewer/StoryDeckInteractionLayout.kt` | edge-to-edge |
| `[ ]` | `views/story/storyviewer/StoryGestureCoordinator.kt` | edge-to-edge |
| `[ ]` | `views/story/storyviewer/StoryLiveTextOverlayView.kt` | edge-to-edge |
| `[ ]` | `views/story/storyviewer/StoryMediaOverlayRendererView.kt` | edge-to-edge |
| `[ ]` | `views/story/storyviewer/StoryQuickActionsMenu.kt` | edge-to-edge |
| `[ ]` | `views/story/storyviewer/StoryReplyViews.kt` | edge-to-edge |
| `[ ]` | `views/story/storyviewer/StoryUserDeckPager.kt` | edge-to-edge |
| `[ ]` | `views/story/storyviewer/StoryViewerBottomComponents.kt` | edge-to-edge |
| `[ ]` | `views/story/storyviewer/StoryViewerLayers.kt` | edge-to-edge |
| `[ ]` | `views/story/storyviewer/StoryViewerLayoutHelpers.kt` | edge-to-edge |
| `[ ]` | `views/story/storyviewer/StoryViewerMedia.kt` | edge-to-edge |
| `[ ]` | `views/story/storyviewer/StoryViewerOverlay.kt` | edge-to-edge |
| `[ ]` | `views/story/storyviewer/StoryViewerScreen.kt` | edge-to-edge |
| `[ ]` | `views/story/storyviewer/StoryViewerScreenChain.kt` | edge-to-edge |

#### `widget` (3)

| Estado | Archivo | Skills |
|:------:|---------|--------|
| `[ ]` | `widget/MomentsWidgetProvider.kt` | edge-to-edge |
| `[ ]` | `widget/MomentsWidgetRemoteViews.kt` | edge-to-edge |
| `[ ]` | `widget/MomentsWidgetStore.kt` | edge-to-edge |

---

## 3. Compose theme / styles + XML residual

**Skills:** `styles` · `migrate-xml-views-to-jetpack-compose`

### Theme / tokens
| Estado | Archivo | Notas |
|:------:|---------|-------|
| `[x]` | `views/shared/Theme.kt` | light+dark; dark `surfaceVariant`=ControlDark |
| `[x]` | `views/shared/Type.kt` | Typography M3 + Inter; Styles API **diferida** (experimental) |
| `[x]` | `views/shared/Color.kt` | Ink/Surface/Control + MomentsBrandColors |
| `[x]` | `views/feed/AdaptiveColors.kt` | canvas/control/text; chatBackground→Ink/Surface |
| `[x]` | `views/feed/FeedStyle.kt` | re-export vacío → AdaptiveColors |
| `[x]` | `views/login/AuthTheme.kt` | AuthColors.canvas=Surface; primary=Ink |
| `[ ]` | `views/login/AuthUIComponents.kt` | auditar hardcodes vs AuthColors |
| `[x]` | `views/nova/novacore/NovaTheme.kt` | background→Ink/Surface compartidos |
| `[ ]` | `views/settings/SettingsNavigationComponents.kt` | tokens settings |
| `[ ]` | `views/settings/sections/SettingsSharedComponents.kt` | tokens settings |
| `[x]` | `views/shared/MomentsModalSheet.kt` | host M3; canvas AdaptiveColors |
| `[x]` | `views/components/MomentsCircularProgressIndicator.kt` | spinner marca (story ring rotativo) |

### XML layouts (candidatos migrate o dejar como App Widget / Player)
| Estado | Archivo | Notas |
|:------:|---------|-------|
| `[ ]` | `app/src/main/res/layout/story_player_view.xml` | XML → Compose si aplica; widget puede quedarse RemoteViews |
| `[ ]` | `app/src/main/res/layout/widget_moments_medium.xml` | XML → Compose si aplica; widget puede quedarse RemoteViews |
| `[ ]` | `app/src/main/res/layout/widget_moments_small.xml` | XML → Compose si aplica; widget puede quedarse RemoteViews |


---

## 4. Navegación + deep links

**Skill:** `navigation-3` (local: `.agents/skills/android-navigation-3`)

**Objetivo:** migrar orquestación tipo iOS (`AppRouter` / `TabBarView` / NotificationCenter-like) hacia Navigation 3 (back stacks, scenes/sheets, deep links).

**Fase 1 hecha (incremental — no migración atómica Nav2→Nav3):**
- Deps: `navigation3-runtime/ui` **1.1.4** + `lifecycle-viewmodel-navigation3` + plugin serialization
- [`MomentsNavKey`](app/src/main/java/com/moments/android/coordinators/nav3/MomentsNavKey.kt) ≡ `AppRouter.Destination`
- [`MomentsDeepLinkParser`](app/src/main/java/com/moments/android/coordinators/nav3/MomentsDeepLinkParser.kt) + `TabBarDeepLinkHandler` vía `navigateViaAppRouter()`

**Fase 2a hecha (2026-08-02):**
- [`MomentsTabNavKey`](app/src/main/java/com/moments/android/coordinators/nav3/MomentsTabNavKey.kt) — índices ≡ AppTab (Feed/Nova/Create/Explore/Profile)
- [`MomentsTabNavigation`](app/src/main/java/com/moments/android/coordinators/nav3/MomentsTabNavigation.kt) — multi-backstack + exit-through-home
- [`MomentsTabNavHost`](app/src/main/java/com/moments/android/coordinators/nav3/MomentsTabNavHost.kt) — `NavDisplay` + entry roots
- `TabBarView` usa NavDisplay; Create sigue como overlay; AppRouter/EventBus conviven

**Fase 2b (2026-08-02 — overlays + pushes):**
- Creator / Notifications / Messages → `DialogSceneStrategy`
- **Profile** SinglePane: `openProfile` → `UserProfileView` (sheet local feed intacto)
- **Moment / Conversation / Stories / StoryChain** DialogScene: `openMoment` / `openConversation` / `openStories` / `openStory` / `openStoryChain`
- Feed ya no monta Dialogs duplicados para esos EventBus/pending (anillo stories con zoom sigue local)

**Fase 2b — Up sintético (2026-08-02):**
- [`MomentsDeepLinkSynthetic.kt`](app/src/main/java/com/moments/android/coordinators/nav3/MomentsDeepLinkSynthetic.kt): `deepLinkParent` + `buildSyntheticBackStack` + `createDeepLinkUpTaskStack`
- `openDeepLink` resetea tab root y empuja camino sintético (ej. Conversation → Feed→Messages→Chat)
- `MainActivity` propaga `FLAG_ACTIVITY_NEW_TASK` → `deepLinkFromNewTask`
- Handler deep link ya no usa solo AppRouter; llama `openDeepLink`

**Fase 2b — Up toolbars (2026-08-02):**
- Dismiss/chevron de overlays Nav3 → `navigator.navigateUp()` (Conversation→Messages→Feed en deep link)
- Creator desde Feed sigue con `popIfTop` (no tumbar otro destino)
- `createDeepLinkUpTaskStack` listo si un toolbar necesita reiniciar Task

**Opcional futuro:** BottomSheetSceneStrategy para chains; https App Links en `captureDeepLink`.

| Estado | Archivo | Notas |
|:------:|---------|-------|
| `[~]` | `coordinators/AppRouter.kt` | Destination ↔ MomentsNavKey; dispatch EventBus |
| `[ ]` | `coordinators/LegacyNavigationBridge.kt` | sigue → AppRouter |
| `[ ]` | `coordinators/MainViewModel.kt` | Nav3 / deep link / scenes |
| `[~]` | `coordinators/NavigationEventBus.kt` | convive; tabs vía MomentsTabNavigator |
| `[ ]` | `coordinators/SharedComponents.kt` | Nav3 / deep link / scenes |
| `[x]` | `coordinators/TabBarView.kt` | NavDisplay + overlays vía navigator.push |
| `[x]` | `coordinators/nav3/MomentsNavKey.kt` | NavKey + bridge |
| `[x]` | `coordinators/nav3/MomentsTabNavKey.kt` | tabs ≡ AppTab |
| `[x]` | `coordinators/nav3/MomentsTabNavigation.kt` | multi-backstack + openProfile/Moment/Conversation/Stories/Chain |
| `[x]` | `coordinators/nav3/MomentsTabNavHost.kt` | DialogScene + Profile/Moment/Conversation/Stories/Chain |
| `[x]` | `coordinators/nav3/MomentsDeepLinkParser.kt` | moments/glowsy/https |
| `[x]` | `coordinators/nav3/MomentsDeepLinkSynthetic.kt` | parents + synthetic stack + TaskStackBuilder |
| `[~]` | `MainActivity.kt` | TabBarScreen = host; deep link capture OK |
| `[ ]` | `MomentsApp.kt` | Nav3 / deep link / scenes |
| `[ ]` | `views/feed/core/FeedRoutes.kt` | Nav3 / deep link / scenes |
| `[ ]` | `views/feed/core/FeedNotificationRoutingModifier.kt` | Nav3 / deep link / scenes |
| `[ ]` | `views/feed/core/FeedPresentationModifier.kt` | Nav3 / deep link / scenes |
| `[ ]` | `notifications/services/NotificationOpenIntentStore.kt` | Nav3 / deep link / scenes |
| `[ ]` | `notifications/services/NotificationNavigationService.kt` | Nav3 / deep link / scenes |
| `[ ]` | `views/messaging/services/ChatNavigationIntentStore.kt` | Nav3 / deep link / scenes |
| `[ ]` | `views/profile/core/sections/ProfileMomentZoomNavigation.kt` | shared-element; no Nav3 aún |
| `[ ]` | `views/profile/core/sections/UserProfileZoomNavigation.kt` | shared-element; no Nav3 aún |
| `[ ]` | `views/profile/highlights/HighlightPresentationCoordinator.kt` | Nav3 / deep link / scenes |
| `[ ]` | `widget/MomentsWidgetProvider.kt` | moments:// intents OK |
| `[~]` | `AndroidManifest.xml` | VIEW moments/glowsy; sin https App Links aún |

**Superficies con sheets/modales (scenes Nav3):**
| Estado | Archivo | Notas |
|:------:|---------|-------|
| `[x]` | `views/shared/MomentsModalSheet.kt` | M3 host; altura=detent; patrón header/scroll/footer |
| `[x]` | `views/comments/ModernCommentsSheet.kt` | MomentsModalSheet |
| `[x]` | `views/feed/reactions/ReactionsListSheet.kt` | MomentsModalSheet |
| `[x]` | `views/feed/maps/MapPlaceBottomSheet.kt` | MomentsModalSheet en Discover |
| `[x]` | `views/profile/incognito/IncognitoModeSheet.kt` | MomentsModalSheet |
| `[x]` | `views/nova/novasections/NovaAttachmentSheet.kt` | MomentsModalSheet |
| `[x]` | `reportes/ReportBottomSheet.kt` | MomentsModalSheet largeOnly=false |
| `[x]` | Audience hosts (EditMoment/storyeditor/Caption) | MomentsModalSheet; Caption `largeOnly=false` |


---

## 5. Cámara (CameraX)

**Skill:** `camerax` (deps `camera-*` **1.6.1**)

**Pass 2026-08-02 (cerrado core):**
- Preview/ImageCapture/VideoCapture vía CameraX (no Camera1)
- `withAudioEnabled()` encadenado (Story + CameraPreviewView)
- UI recording con `VideoRecordEvent.Start` / `Finalize`
- `unbindAll()` + stop seguro en dispose (Story / Preview / Background / Selfie sticker)
- `stopRecording` tolerante a carrera Start async
- `CameraCapture.kt` = Intents sistema (N/A sesión CameraX)
- Opcional futuro: CameraXViewfinder Compose, ExtensionsManager, thermals

| Estado | Archivo | Notas |
|:------:|---------|-------|
| `[x]` | `views/creator/creatoruikit/BackgroundCameraView.kt` | Preview + unbind dispose |
| `N/A` | `views/creator/creatoruikit/CameraCapture.kt` | Intent TakePicture/Video (no sesión CameraX) |
| `[x]` | `views/creator/creatoruikit/CameraPreviewView.kt` | ViewPort + VideoCapture + stop seguro |
| `N/A` | `views/creator/creatoruikit/CreatorCaptureGeometry.kt` | geometría UI, no session |
| `N/A` | `views/creator/creatoruikit/CreatorControls.kt` | chrome UI |
| `N/A` | `views/creator/creatoruikit/CreatorUIImageExtensions.kt` | bitmap helpers |
| `N/A` | `views/creator/creatoruikit/CropViewWrapper.kt` | crop UI |
| `N/A` | `views/creator/creatoruikit/DrawingView.kt` | draw UI |
| `N/A` | `views/creator/creatoruikit/StoryGalleryPicker.kt` | picker |
| `N/A` | `views/creator/creatoruikit/StoryMediaPicker.kt` | picker |
| `[~]` | `views/creator/camerakit/CameraKitSpike.kt` | Snap Camera Kit híbrido |
| `N/A` | `views/creator/camerakit/LensReel.kt` | UI lenses |
| `[x]` | `views/creator/creatorscreens/StoryCameraView.kt` | CameraX + unbind + stop async |
| `[x]` | `SelfieStickerLiveCameraView` (StickerOverlay) | unbind dispose |
| `[x]` | `views/messaging/media/ChatCameraView.kt` | host → CameraPreviewView |
| `[~]` | `views/permission/camera/Contentview.kt` | permission UX |
| `[~]` | `views/permission/camera/helpers/CameraPermissionsview.kt` | permission UX |
| `[x]` | `views/permissions/CameraAccessBoundary.kt` | gate CAMERA(+mic) |
| `[x]` | `views/permissions/CameraPermissionGate.kt` | gate |

### Relacionados (media / permisos UI)
| Estado | Archivo | Notas |
|:------:|---------|-------|
| `[ ]` | `views/permission/camera/Contentview.kt` | permission UX; CameraX al conceder |
| `[ ]` | `views/permission/camera/helpers/CameraPermissionsview.kt` | permission UX; CameraX al conceder |
| `[ ]` | `views/permission/location/LocationPermissionView.kt` | permission UX; CameraX al conceder |
| `[ ]` | `views/permission/microphone/MicrophonePermissionView.kt` | permission UX; CameraX al conceder |
| `[ ]` | `views/permission/notifications/NotificationsPermissionView.kt` | permission UX; CameraX al conceder |
| `[ ]` | `views/permission/photos/PhotosPermissionView.kt` | permission UX; CameraX al conceder |
| `[ ]` | `views/permission/shared/LocationPermissionGate.kt` | permission UX; CameraX al conceder |
| `[ ]` | `views/permission/shared/PermissionLoopTime.kt` | permission UX; CameraX al conceder |
| `[ ]` | `views/permission/shared/PermissionMockOverflow.kt` | permission UX; CameraX al conceder |
| `[ ]` | `views/permission/shared/PermissionPhoneFrame.kt` | permission UX; CameraX al conceder |
| `[ ]` | `views/permission/shared/PermissionPhoneWallpaper.kt` | permission UX; CameraX al conceder |
| `[ ]` | `views/permission/shared/PermissionPrimerFullScreenDialog.kt` | permission UX; CameraX al conceder |
| `[ ]` | `views/permission/shared/PermissionPrimerGate.kt` | permission UX; CameraX al conceder |
| `[ ]` | `views/permission/shared/PermissionPrimerScaffold.kt` | permission UX; CameraX al conceder |
| `[ ]` | `views/permission/tracking/TrackingPermissionView.kt` | permission UX; CameraX al conceder |
| `[ ]` | `views/permissions/CameraAccessBoundary.kt` | permission UX; CameraX al conceder |
| `[ ]` | `views/permissions/CameraPermissionGate.kt` | permission UX; CameraX al conceder |


---

## 6. Auth / identity

**Skill:** `verified-email` (Credential Manager / Digital Credentials)

**Decisión 2026-08-02: `N/A` para Moments.**

- Producto = verificación Firebase (`sendEmailVerification` + `emailVerified`), **paridad iOS**.
- El skill CredMan OTP-less **no** mejora el flujo actual (rompe paridad, exige validación server, adopción Google-centric).
- No implementar salvo decisión explícita de producto más adelante.

| Estado | Archivo | Notas |
|:------:|---------|-------|
| `N/A` | Skill `verified-email` / CredMan Digital Credentials | Diferido a propósito |
| `[x]` | `views/login/RegisterView.kt` + `AuthService` | Firebase `sendEmailVerification` (fuente de verdad) |
| `N/A` | Resto login/settings listados solo por el skill | No aplica CredMan |


---

## 7. Seguridad de Intents / componentes

**Skill:** `android-intent-security`

**Pass Manifest (2026-08-02):**
- `MainActivity` exported (launcher + deep links) — data URI filtrada a `moments`/`glowsy`
- Widget receiver exported (requisito AppWidget) — PendingIntent `FLAG_IMMUTABLE`
- FCM service + reply receiver `exported=false`
- Reply RemoteInput: `FLAG_MUTABLE` justificado (API RemoteInput)
- `allowBackup=false`, `usesCleartextTraffic=false`

| Estado | Archivo | Notas |
|:------:|---------|-------|
| `[x]` | `AndroidManifest.xml` | exported audit OK |
| `[x]` | `MainActivity.kt` | scheme allowlist en captureDeepLink |
| `[x]` | `widget/MomentsWidgetRemoteViews.kt` | FLAG_IMMUTABLE |
| `[x]` | `services/messaging/ChatNotificationReplyReceiver.kt` | exported=false |
| `[x]` | `notifications/services/MomentsFirebaseMessagingService.kt` | exported=false; PI IMMUTABLE |
| `[x]` | `services/messaging/ChatCommunicationIntentDonor.kt` | MUTABLE solo RemoteInput |
| `N/A` | `services/messaging/ChatSendMessageIntentHandler.kt` | stub doc; reply = RemoteInput |
| `N/A` | `views/shared/ScreenshotProtectedView.kt` | FLAG_SECURE UI; sin intents |
| `N/A` | `views/shared/SecureComposeSurfaceHost.kt` | FLAG_SECURE UI; sin intents |


---

## 8. Testing

**Skill:** `testing-setup`

| Estado | Área | Notas |
|:------:|------|-------|
| `[ ]` | Unit (`app/src/test`) | harness + libs |
| `[ ]` | Instrumented (`app/src/androidTest`) | |
| `[ ]` | UI / Compose tests | Login, TabBar, Feed, Chat smoke |
| `[ ]` | Screenshot tests | chrome crítico |
| `[ ]` | E2E device (`android` CLI / journeys) | |

---

## 9. Performance (Perfetto)

**Skills:** `perfetto-trace-analysis` · `perfetto-sql`

**Hot paths a trazar (jank / memoria):**
| Estado | Archivo | Notas |
|:------:|---------|-------|
| `[ ]` | `services/performance/FeedVisibilityCoordinator.kt` | trace feed/chat/stories/video |
| `[ ]` | `services/performance/MotionPolicy.kt` | trace feed/chat/stories/video |
| `[ ]` | `services/performance/PerformanceSignposts.kt` | trace feed/chat/stories/video |
| `[ ]` | `services/performance/VideoMomentsIndex.kt` | trace feed/chat/stories/video |
| `[ ]` | `services/video/GlobalVideoManager.kt` | trace feed/chat/stories/video |
| `[ ]` | `services/video/ReelPrebufferService.kt` | trace feed/chat/stories/video |
| `[ ]` | `services/video/SharedVideoPlayerPool.kt` | trace feed/chat/stories/video |
| `[ ]` | `services/video/VideoAdaptivePlayback.kt` | trace feed/chat/stories/video |
| `[ ]` | `services/video/VideoPlaybackSelector.kt` | trace feed/chat/stories/video |
| `[ ]` | `views/feed/core/FeedNotificationRoutingModifier.kt` | trace feed/chat/stories/video |
| `[ ]` | `views/feed/core/FeedPresentationModifier.kt` | trace feed/chat/stories/video |
| `[ ]` | `views/feed/core/FeedRoutes.kt` | trace feed/chat/stories/video |
| `[ ]` | `views/feed/core/FeedView.kt` | trace feed/chat/stories/video |
| `[ ]` | `views/feed/core/FeedViewModel.kt` | trace feed/chat/stories/video |
| `[ ]` | `views/feed/core/ModernEmptyFeedView.kt` | trace feed/chat/stories/video |
| `[ ]` | `views/feed/core/sections/CarouselImmersivePeek.kt` | trace feed/chat/stories/video |
| `[ ]` | `views/feed/core/sections/FeedHeaderSection.kt` | trace feed/chat/stories/video |
| `[ ]` | `views/feed/core/sections/FeedListSection.kt` | trace feed/chat/stories/video |
| `[ ]` | `views/feed/core/sections/FeedMomentComponents.kt` | trace feed/chat/stories/video |
| `[ ]` | `views/feed/core/sections/FeedMomentDetailRoute.kt` | trace feed/chat/stories/video |
| `[ ]` | `views/feed/core/sections/FeedOverlaysSection.kt` | trace feed/chat/stories/video |
| `[ ]` | `views/feed/core/sections/FeedPostSkeletonView.kt` | trace feed/chat/stories/video |
| `[ ]` | `views/feed/core/sections/FeedStoryRingComponents.kt` | trace feed/chat/stories/video |
| `[ ]` | `views/feed/video/LiveVideoTimeLabel.kt` | trace feed/chat/stories/video |
| `[ ]` | `views/feed/video/ReelVideoPlayerManager.kt` | trace feed/chat/stories/video |
| `[ ]` | `views/feed/video/ReelVideoView.kt` | trace feed/chat/stories/video |
| `[ ]` | `views/feed/video/Reels.kt` | trace feed/chat/stories/video |
| `[ ]` | `views/feed/video/VideoFeedProgressBar.kt` | trace feed/chat/stories/video |
| `[ ]` | `views/feed/video/VideoPlaybackChromeStyle.kt` | trace feed/chat/stories/video |
| `[ ]` | `views/feed/video/VideoPlayer.kt` | trace feed/chat/stories/video |
| `[ ]` | `views/feed/video/VideoPosterOverlay.kt` | trace feed/chat/stories/video |
| `[ ]` | `views/messaging/components/ChatMessageListView.kt` | trace feed/chat/stories/video |
| `[ ]` | `views/messaging/screens/chat/GlassmorphicChatView.kt` | trace feed/chat/stories/video |
| `[ ]` | `views/messaging/screens/chat/GlassmorphicChatViewClustering.kt` | trace feed/chat/stories/video |
| `[ ]` | `views/messaging/screens/chat/GlassmorphicChatViewComposerAndChrome.kt` | trace feed/chat/stories/video |
| `[ ]` | `views/messaging/screens/chat/GlassmorphicChatViewLifecycle.kt` | trace feed/chat/stories/video |
| `[ ]` | `views/messaging/screens/chat/GlassmorphicChatViewMessageList.kt` | trace feed/chat/stories/video |
| `[ ]` | `views/messaging/screens/chat/GlassmorphicChatViewMessageRendering.kt` | trace feed/chat/stories/video |
| `[ ]` | `views/messaging/screens/chat/GlassmorphicChatViewScroll.kt` | trace feed/chat/stories/video |
| `[ ]` | `views/messaging/screens/chat/GlassmorphicChatViewSearch.kt` | trace feed/chat/stories/video |
| `[ ]` | `views/messaging/screens/chat/GlassmorphicChatViewToolbar.kt` | trace feed/chat/stories/video |
| `[ ]` | `views/messaging/screens/chat/GlassmorphicChatViewViewModelAudio.kt` | trace feed/chat/stories/video |
| `[ ]` | `views/messaging/screens/chat/GlassmorphicChatViewVoice.kt` | trace feed/chat/stories/video |
| `[ ]` | `views/messaging/screens/chat/MomentsChatViewModelMedia.kt` | trace feed/chat/stories/video |
| `[ ]` | `views/messaging/services/ChatKeyboardScrollCoordinator.kt` | trace feed/chat/stories/video |
| `[ ]` | `views/messaging/services/ChatRowHeightEstimator.kt` | trace feed/chat/stories/video |
| `[ ]` | `views/story/storyviewer/StoryDeckInteractionLayout.kt` | trace feed/chat/stories/video |
| `[ ]` | `views/story/storyviewer/StoryGestureCoordinator.kt` | trace feed/chat/stories/video |
| `[ ]` | `views/story/storyviewer/StoryLiveTextOverlayView.kt` | trace feed/chat/stories/video |
| `[ ]` | `views/story/storyviewer/StoryMediaOverlayRendererView.kt` | trace feed/chat/stories/video |
| `[ ]` | `views/story/storyviewer/StoryQuickActionsMenu.kt` | trace feed/chat/stories/video |
| `[ ]` | `views/story/storyviewer/StoryReplyViews.kt` | trace feed/chat/stories/video |
| `[ ]` | `views/story/storyviewer/StoryUserDeckPager.kt` | trace feed/chat/stories/video |
| `[ ]` | `views/story/storyviewer/StoryViewerBottomComponents.kt` | trace feed/chat/stories/video |
| `[ ]` | `views/story/storyviewer/StoryViewerLayers.kt` | trace feed/chat/stories/video |
| `[ ]` | `views/story/storyviewer/StoryViewerLayoutHelpers.kt` | trace feed/chat/stories/video |
| `[ ]` | `views/story/storyviewer/StoryViewerMedia.kt` | trace feed/chat/stories/video |
| `[ ]` | `views/story/storyviewer/StoryViewerOverlay.kt` | trace feed/chat/stories/video |
| `[ ]` | `views/story/storyviewer/StoryViewerScreen.kt` | trace feed/chat/stories/video |
| `[ ]` | `views/story/storyviewer/StoryViewerScreenChain.kt` | trace feed/chat/stories/video |


---

## 10. Play / Engage / Billing / Policy

| Skill | Estado | Notas Moments |
|-------|:------:|---------------|
| `play-policy-insights` | `[ ]` | Auditoría pre-release Play |
| `engage-sdk-integration` | `[ ]` | Evaluar si Engage aplica (stories/moments surfaces) |
| `play-billing-library-version-upgrade` | `N/A` | Plus/StoreKit fuera de alcance Android hoy |

**Archivos ads / compliance relacionados:**
| Estado | Archivo | Notas |
|:------:|---------|-------|
| `[ ]` | `ad/AdAspectRatioContext.kt` | AdMob; cruzar con policy |
| `[ ]` | `ad/AdMobConfiguration.kt` | AdMob; cruzar con policy |
| `[ ]` | `ad/FeedNativeAd.kt` | AdMob; cruzar con policy |
| `[ ]` | `ad/StoryNativeAd.kt` | AdMob; cruzar con policy |


---

## 11. AppFunctions

**Skill:** `appfunctions`

| Estado | Workflow candidato | Entrada actual |
|:------:|--------------------|----------------|
| `[ ]` | Crear story | `moments://story/create` / Creator |
| `[ ]` | Abrir mensajes | deep link messages |
| `[ ]` | Abrir chat concreto | `ChatCommunicationIntentDonor` |
| `[ ]` | Nova acción | Nova tools |

| Estado | Archivo | Notas |
|:------:|---------|-------|
| `[ ]` | `services/messaging/ChatCommunicationIntentDonor.kt` | atajos / agent triggers |
| `[ ]` | `services/messaging/ChatSendMessageIntentHandler.kt` | atajos / agent triggers |
| `[ ]` | `widget/MomentsWidgetProvider.kt` | atajos / agent triggers |
| `[ ]` | `views/nova/agent/NovaToolExecutor.kt` | atajos / agent triggers |


---

## 12. Fuera de producto (N/A)

| Skill | Estado | Motivo |
|-------|:------:|--------|
| `wear-compose-m3` | `N/A` | No hay target Wear |
| `display-glasses-with-jetpack-compose-glimmer` | `N/A` | No hay XR glasses |

---

## Sheets Material 3 (prioridad gestos)

Referencia: [m3.material.io/components/bottom-sheets](https://m3.material.io/components/bottom-sheets/overview) + skill `edge-to-edge`.

**Reglas:**
1. Host único: [`MomentsModalSheet`](app/src/main/java/com/moments/android/views/shared/MomentsModalSheet.kt) → `ModalBottomSheet` M3.
2. **Prohibido** en contenido: `fillMaxHeight(0.xx)`, drag vertical custom, offset que compita con el sheet.
3. Drag handle = `BottomSheetDefaults.DragHandle` (no reinventar).
4. Insets: los del componente M3 (no forzar `WindowInsets(0)`).
5. Estilo: canvas `surfaceBackground` (default del host); cabecera pegada al handle (`MomentsSheetHeader` / `top = 0`); **sin chevron de dismiss**.

| Estado | Archivo / superficie | Notas |
|:------:|----------------------|-------|
| `[x]` | `views/shared/MomentsModalSheet.kt` | M3 nativo; sin altura forzada |
| `[x]` | `views/comments/ModernCommentsSheet.kt` | weight en content |
| `[x]` | `views/nova/.../NovaAttachmentSheet.kt` | quitado detectVerticalDrag custom |
| `[x]` | `views/creator/stickerview.kt` | quitado fillMaxHeight(0.78) |
| `[x]` | `views/feed/sharing/share.kt` (`ModernShareBottomSheet`) | MomentsModalSheet |
| `[x]` | `views/feed/maps/DiscoverMapView` sheet | MomentsModalSheet |
| `[x]` | `views/components/MomentCaptionView.kt` sheet | MomentsModalSheet |
| `[x]` | `views/login/LoginView.kt` ModalBottomSheet | MomentsModalSheet |
| `[x]` | `views/echoes/EchoHistoryView.kt` | MomentsModalSheet |

## Motion / container transform (prioridad zoom)

Referencia: [m3.material.io — Transition patterns](https://m3.material.io/styles/motion/transitions/transition-patterns) (container transform).

**Mapeo iOS → Android (no copiar bounce/springs iOS):**

| iOS | Android M3 |
|-----|------------|
| `matchedTransitionSource(id:in:)` | `sharedBounds` / `sharedElementWithCallerManagedVisibility` |
| `.navigationTransition(.zoom(sourceID:in:))` | mismo `sharedContentState` en destino + `MotionScheme.defaultSpatialSpec` |
| `Namespace.ID` | `MomentsSharedTransitionLayout` |
| `navigationDestination` / fullScreenCover con zoom | `MomentsContainerTransformOverlay` **in-tree** (no `Dialog`) |

| Estado | Archivo / superficie | Notas |
|:------:|----------------------|-------|
| `[x]` | `views/shared/MomentsMotion.kt` | MotionScheme spatial/effects + StoryZoomNavigation |
| `[x]` | `views/shared/MomentsSharedTransition.kt` | SharedTransitionLayout + overlay host |
| `[x]` | `ProfileMomentZoomNavigation.kt` | boundsTransform M3 |
| `[x]` | `UserProfileZoomNavigation.kt` | nest-aware; fade M3 (no tween/scale iOS) |
| `[x]` | `ProfileView` settings/edit/moment | overlay in-tree (ids `settings-view` / `edit-profile-view`) |
| `[x]` | `FeedPresentationModifier` + story rings | `story-ring-{userId}` container transform |
| `[x]` | Explore / Notifications / DiscoverMap / Saved / Activity | zoom sin Dialog |
| `[~]` | View-once chat zoom | iOS `matchedTransitionSource` — stub en bubble |
| `[~]` | `profile-message-chat` UserProfile | pendiente Dialog→overlay |
| `[ ]` | Creator `matchedGeometryEffect("momentSource")` | unfold — no zoom nav |

## Skeletons / loading (misma familia M3)

Referencias en [m3.material.io](https://m3.material.io/):
- [Progress indicators](https://m3.material.io/components/progress-indicators/overview) — proceso/circular (proceso conocido)
- [Loading indicator](https://m3.material.io/components/loading-indicator/guidelines) — actividad indeterminada

**Hecho clave:** en **phone Material 3 no hay componente “Skeleton” oficial**. Los content placeholders (formas del layout + pulse/shimmer) son patrón de producto; en Wear sí existe `Modifier.placeholder` / `placeholderShimmer` (`androidx.wear.compose.material3`) — **no portar a phone**.

| Skill | ¿Sirve para skeletons phone? |
|-------|------------------------------|
| `android-styles` | Solo tokens/theme de componentes custom; no define placeholders |
| `android-wear-compose-m3` | Tiene placeholder API, pero es **Wear OS** — no usar en Moments phone |
| `edge-to-edge` / `adaptive` | Solo insets/layout al tocar esas vistas |
| Motion (`MomentsMotion`) | Sí para duración/reduceMotion del pulse |

**Reglas Moments:**
1. Layout del skeleton = paridad iOS (mismas cajas); motion/color = M3 (`surface`/`onSurface` alphas, `MotionPolicy.reduceMotion`).
2. Host único de efecto: [`SkeletonShimmer.kt`](app/src/main/java/com/moments/android/views/components/SkeletonShimmer.kt) (`Modifier.shimmer`) — unificar brushes sueltos (`rememberShimmerBrush`) hacia ese host.
3. Spinner indeterminado de marca: [`MomentsCircularProgressIndicator`](app/src/main/java/com/moments/android/views/components/MomentsCircularProgressIndicator.kt) (rota story ring). Blanco/contraste local OK en overlays oscuros.
4. Prohibido dependencias Wear o librerías skeleton de terceros salvo decisión explícita.

| Estado | Archivo | Notas |
|:------:|---------|-------|
| `[x]` | `views/components/SkeletonShimmer.kt` | `rememberMomentsSkeletonColor` + pulse `onSurface` |
| `[x]` | `CommentRowSkeletonView.kt` | host único |
| `[x]` | `UserRowSkeletonView.kt` | host único |
| `[x]` | `LocationMomentCardSkeletonView.kt` | host único |
| `[x]` | `StoryViewerSkeletonView.kt` | onSurface 0.16 (canvas stories) |
| `[x]` | `FeedPostSkeletonView.kt` | quitado brush sweep; shimmer host |
| `[x]` | `StoryRingTraySkeleton.kt` | quitado rememberShimmerBrush |
| `[x]` | `ProfileHeaderSkeletonView.kt` | + grid bento onSurface |
| `[x]` | Unificar brush → `Modifier.shimmer` / tokens M3 | hecho |

## 13. Migración visual pantalla por pantalla — Liquid Glass → Moments Material 3

**Skills aplicadas:** `edge-to-edge` · `adaptive` · `ios-to-android`.

Esta sección es la cola visual solicitada. iOS sigue siendo la fuente de verdad
para jerarquía, contenido y comportamiento; Android decide materiales, insets,
navegación, componentes y adaptación de ventana.

La skill `styles` se consultó, pero **no forma parte de esta migración**: la API
es experimental, requiere Compose alpha y no estiliza componentes Material. Se
mantienen `MaterialTheme`, tokens y componentes compartidos estables.

### Estado base detectado

- [x] `compileSdk` y `targetSdk` 37.
- [x] `MainActivity.enableEdgeToEdge()` antes de `setContent`.
- [x] `android:windowSoftInputMode="adjustResize"`.
- [x] Tema Material 3 con esquemas claro y oscuro.
- [~] Navigation 3 está disponible y parcialmente integrado; no todas las rutas
  usan todavía `NavDisplay`/Scenes.
- [!] `MainActivity` fuerza orientación vertical. Antes de certificar tablets y
  foldables hay que decidir si se elimina el bloqueo en ventanas grandes.
- [!] Hay 49 archivos UI con nombres o patrones `glass*`/`LiquidGlass`.
- [!] Hay 61 archivos UI con campos de texto: todos requieren prueba real de IME.
- [!] Hay 35 archivos con `DialogProperties`; cada diálogo fullscreen debe
  justificar su uso y aplicar `decorFitsSystemWindows = false`.

### Qué se elimina y qué se conserva

Eliminar o sustituir cuando sea **chrome de interfaz**:

- [ ] Blur/transparencia en app bars, buscadores, botones, tabs, dock, menús,
  banners, cards, composers, headers y sheets.
- [ ] Bordes blancos translúcidos, brillos y sombras dobles usados para simular
  cristal.
- [ ] Colores `White`/`Black`/`Gray` que sustituyan accidentalmente a
  `colorScheme.onSurface`, `onSurfaceVariant`, `surfaceContainer*`, `outlineVariant`
  o colores semánticos.
- [ ] Componentes llamados `Glassmorphic*` que sigan renderizando glass. El
  renombrado se hará después de estabilizar la UI para evitar churn innecesario.

Conservar cuando sea **contenido o semántica**, documentándolo en la fila:

- [ ] Blur de privacidad, spoiler, contenido moderado/restringido o captura segura.
- [ ] Backdrop desenfocado derivado de una foto o vídeo dentro de un visor.
- [ ] Efectos creativos elegidos por el usuario (`glassText`, stickers, filtros).
- [ ] Scrim Material para modal, selección o legibilidad sobre media.

### Contrato visual estable

- [ ] Canvas: `MaterialTheme.colorScheme.background`; ninguna pantalla inventa
  otro fondo base sin motivo de producto.
- [ ] Superficie agrupada: `surfaceContainerLow/High` o token Moments equivalente,
  sólida en claro y oscuro.
- [ ] Texto principal/secundario: `onSurface`/`onSurfaceVariant`; disabled usa
  opacidad semántica, no gris fijo.
- [ ] Separadores/bordes: `outlineVariant`; errores y acciones destructivas usan
  `error`/`onErrorContainer`.
- [ ] Los controles conservan identidad Moments mediante color, forma, tipografía
  e iconografía; no mediante materiales Liquid Glass.
- [ ] Ripple/state layer, foco, pressed, selected, loading, disabled y error son
  perceptibles en ambos temas.
- [ ] Target táctil mínimo 48 dp y semántica TalkBack útil.
- [ ] Contraste objetivo: 4.5:1 para texto normal y 3:1 para texto grande/iconos
  esenciales.

### Contrato edge-to-edge e IME por pantalla

- [ ] Usar una sola fuente de insets: `Scaffold`/componente Material **o** modifier
  explícito; nunca ambos.
- [ ] Propagar `innerPadding` y llamar a `consumeWindowInsets(innerPadding)`.
- [ ] Listas dibujan detrás de las barras, pero protegen primer/último elemento con
  `contentPadding`.
- [ ] FAB, CTA y controles flotantes quedan por encima de navigation bar/cutout.
- [ ] App bars Material reciben sus `windowInsets`; no aplicar `statusBarsPadding`
  otra vez al contenido.
- [ ] Campos de texto mantienen foco y visibilidad con `fitInside` del IME o una
  única aplicación correcta de `imePadding`.
- [ ] Fullscreen `Dialog` usa `decorFitsSystemWindows = false`; una subsección
  normal debe ser ruta, no diálogo fullscreen.
- [ ] Status/navigation bar tienen iconos legibles en claro/oscuro y no muestran
  una transparencia accidental distinta al canvas.

### Contrato adaptive

Para cerrar una pantalla se comprobarán como mínimo teléfono compacto y teléfono
alto. Las columnas `AD` se cierran además en foldable/tablet cuando se resuelva el
bloqueo de orientación.

- [ ] Añadir baseline/screenshot de teléfono claro y oscuro.
- [ ] Verificar fuente 1.0× y 1.3×; los textos no se pisan ni expulsan acciones.
- [ ] Grids usan `GridCells.Adaptive` cuando el contenido admite más columnas.
- [ ] List/detail o supporting pane se plantean con Navigation 3 Scenes, nunca con
  una duplicación manual de layouts.
- [ ] Navegación inferior pasa a rail/suite en ancho grande cuando la migración
  Nav3 llegue al shell.
- [ ] Pantallas inmersivas de cámara/media siguen fullscreen en teléfono; en
  layouts multipane se decide explícitamente si deben continuar así.
- [ ] No introducir `Grid`, `FlexBox`, `MediaQuery` o Styles experimentales sin una
  tarea separada y aprobación explícita.

### Leyenda de auditoría

| Código | Comprobar |
|---|---|
| `GL` | Retirar glass/transparencia de chrome; clasificar blur legítimo |
| `TH` | Tema claro/oscuro, contraste y colores semánticos |
| `E2E` | Status/nav bars, cutout, listas, FAB y fuente única de insets |
| `IME` | Teclado, foco, scroll, composer y CTA visible |
| `M3` | Componente/anatomía Material 3, state layers y targets |
| `BK` | Back, predictive back, Up, dismiss y jerarquía modal |
| `SH` | Bottom sheet/dialog/menu, scrim, handle y cierre animado |
| `AD` | Fuente grande, teléfono alto/compacto y ventana grande |
| `A11Y` | TalkBack, orden semántico, labels y contenido no basado sólo en color |

`TH`, `AD` (fuente) y `A11Y` son obligatorios en **todas** las filas aunque no se
repitan en la columna.

### 13.1 Shell, componentes y superficies compartidas

| Estado | Pantalla / superficie | Archivos principales | Auditoría | Resultado Android esperado |
|:---:|---|---|---|---|
| `[~]` | Tema global | `shared/Theme.kt`, `Color.kt`, `Type.kt`, `AdaptiveColors.kt` | `GL TH M3` | Tokens únicos claro/oscuro; familias Auth/Chat/Nova sólo extienden, no contradicen |
| `[~]` | Ventana y barras de sistema | `MainActivity.kt`, themes XML | `E2E TH` | Barras transparentes coherentes e iconos correctos al cambiar tema |
| `[ ]` | Shell principal | `MomentsApp.kt`, `TabBarView.kt` | `GL E2E M3 BK AD` | Scaffold estable; dock legible y futura NavigationSuite en ancho grande |
| `[ ]` | Navegación y presentaciones | `AppRouter.kt`, `FeedPresentationModifier.kt`, hosts Nav3 | `E2E BK SH AD` | Ruta real para pantallas; fullscreen sólo para experiencia inmersiva |
| `[~]` | Bottom sheets comunes | `MomentsModalSheet.kt` | `GL E2E IME M3 BK SH` | Superficie sólida, handle único, callback de dismiss y sin doble inset |
| `[ ]` | Diálogos fullscreen | 35 call sites con `DialogProperties` | `E2E BK SH` | `decorFitsSystemWindows=false` o migración a ruta/sheet según anatomía |
| `[ ]` | App bars y back | componentes de feature + `SettingsNavigationComponents.kt` | `GL E2E M3 BK` | Icono Up consistente, target 48 dp y mismo resultado que Back |
| `[ ]` | Buscadores | `SettingsSearchField.kt` y search fields de features | `GL TH IME M3 A11Y` | Campo sólido/tonal, estados focused/error/disabled y clear accesible |
| `[ ]` | Banners/snackbars | `InAppBannerView.kt`, `OfflineBanner.kt`, action banners | `GL E2E M3 A11Y` | Snackbar/banner sólido, no tapa status bar y acciones alcanzables |
| `[ ]` | Skeletons/loading | skeletons compartidos, `MomentsCircularProgressIndicator.kt` | `TH M3 AD` | Una familia tonal, sin flash claro en dark ni saltos de layout |
| `[ ]` | Menús y selección | context menus, selection bars, dropdowns | `GL TH M3 BK SH` | `DropdownMenu`/superficie tonal, state layer y Back sale primero de selección |
| `[ ]` | Motion/haptics | `MomentsMotion.kt`, `MotionPolicy.kt`, `HapticManager.kt` | `M3 BK A11Y` | Motion coherente, interrumpible y respetuosa con escala 0× |

### 13.2 Acceso, onboarding y permisos

| Estado | Pantalla / flujo | Archivos principales | Auditoría | Foco |
|:---:|---|---|---|---|
| `[ ]` | Splash y auth gate | `SplashScreen.kt`, `MomentsApp.kt` | `TH E2E M3 BK` | Sin flash de tema ni salto entre splash nativo y Compose |
| `[ ]` | Login | `LoginView.kt`, `AuthUIComponents.kt` | `GL TH E2E IME M3 BK` | Inputs/autofill legibles, CTA visible y errores inline |
| `[ ]` | Registro | `RegisterView.kt`, account state screens | `GL TH E2E IME M3 BK` | Password manager, teclado adecuado y scroll sin doble padding |
| `[ ]` | Completar perfil | `ProfileOnboardingView.kt`, `CreatingProfileView.kt` | `GL TH E2E IME M3 BK` | Foto, intereses, validación y progreso estables |
| `[ ]` | Privacidad y términos | `PrivacyPolicyView.kt` | `TH E2E M3 BK AD` | Texto largo, enlaces y fuente grande sin pérdida de navegación |
| `[ ]` | Primer cámara | `CameraPermissionsview.kt` | `GL TH E2E M3 BK` | Material sólido, diálogo del sistema una vez y salida clara |
| `[ ]` | Primer micrófono | `MicrophonePermissionView.kt` | `GL TH E2E M3 BK` | Rationale, denied y Settings coherentes |
| `[ ]` | Primer fotos | `PhotosPermissionView.kt` | `GL TH E2E M3 BK` | Photo Picker/permisos parciales; mock legible en ambos temas |
| `[ ]` | Primer ubicación | `LocationPermissionView.kt` | `GL TH E2E M3 BK` | Aproximada/precisa, while-in-use y denied |
| `[ ]` | Primer notificaciones | `NotificationsPermissionView.kt` | `GL TH E2E M3 BK` | Momento contextual; no request prematuro |
| `[ ]` | Primer tracking | `TrackingPermissionView.kt` | `GL TH E2E M3 BK` | Explicar alternativa Android sin copiar ATT literalmente |

### 13.3 Feed, Moments, Stories y comentarios

| Estado | Pantalla / flujo | Archivos principales | Auditoría | Foco |
|:---:|---|---|---|---|
| `[ ]` | Feed principal | `FeedView.kt`, header/list/overlays | `GL TH E2E M3 BK AD` | Header/dock sólidos, refresh Android y scroll sin tapar extremos |
| `[ ]` | Feed vacío/error/offline | `ModernEmptyFeedView.kt`, banners | `GL TH E2E M3 AD` | Estados tonales, retry claro y altura estable |
| `[ ]` | Ring de Stories | `FeedStoryRingComponents.kt`, tray skeleton | `GL TH E2E M3 AD` | Scroll/targets/rings legibles; sin brillo glass de chrome |
| `[ ]` | Card de Moment | `FeedMomentComponents.kt`, caption/rail | `GL TH M3 AD A11Y` | Acciones 48 dp, state layer, caption y metadatos con contraste |
| `[ ]` | Carrusel de media | `MomentMediaCarousel.kt` | `TH E2E M3 BK AD` | Pager/indicador; blur de backdrop permitido y documentado |
| `[ ]` | Vídeo/Reels | `ReelVideoView.kt`, `VideoPlayer.kt`, `Reels.kt` | `GL TH E2E M3 BK` | Chrome sobre media con scrim, audio/lifecycle y gesto lateral |
| `[ ]` | Detalle de Moment | `SingleMomentDetailView.kt`, routes | `GL TH E2E M3 BK AD` | Ruta/back predecible y top chrome sólido/legible |
| `[ ]` | Reacciones | `MomentReactionButton.kt`, `ReactionsListSheet.kt` | `GL TH M3 BK SH` | Sheet Material, selección y haptic sin overlays glass |
| `[ ]` | Compartir | `share.kt`, `StoryShare.kt` | `GL TH E2E M3 BK SH` | Sharesheet nativo, previews y sheets sólidos |
| `[ ]` | Capas ocultas | `HiddenLayersOverlayView.kt` | `TH E2E M3 BK A11Y` | Blur semántico permitido; controles y explicación legibles |
| `[ ]` | Comentarios | `ModernCommentsView.kt`, `ModernCommentsSheet.kt` | `GL TH E2E IME M3 BK SH` | Composer pegado al IME, lista y menciones sin doble inset |
| `[ ]` | Búsqueda de menciones | `CommentMentionSearchOverlay.kt` | `GL TH IME M3 BK` | Overlay sólido, debounce, clear y foco |
| `[ ]` | Story viewer | `StoriesView.kt`, `StoryViewerScreen.kt` | `GL TH E2E IME M3 BK` | Media fullscreen; chrome/scrim legible y gestos no compiten |
| `[ ]` | Story replies | `StoryReplyViews.kt` | `GL TH E2E IME M3 BK SH` | Composer, teclado y sheets; retirar extensiones glass de chrome |
| `[ ]` | Stickers interactivos | `storystickers/*`, `StoryInteractiveStickers.kt` | `TH E2E M3 BK A11Y` | Preservar estilo creativo; targets/semántica y exclusión de gestos |
| `[ ]` | Cadenas y respuestas | `StoryChainView.kt`, `QuestionResponsesView.kt` | `GL TH E2E M3 BK SH AD` | Dialog fullscreen auditado, grids adaptativos y Back correcto |
| `[ ]` | Archivo y estadísticas | `ArchivedStoriesView.kt`, `StoryStatsView.kt` | `GL TH E2E M3 BK SH AD` | App bar, calendario/grids y superficies claras/oscuras |

### 13.4 Explore, mapas y Echoes

| Estado | Pantalla / flujo | Archivos principales | Auditoría | Foco |
|:---:|---|---|---|---|
| `[ ]` | Explore | `ExploreView.kt`, sections | `GL TH E2E IME M3 BK AD` | Search/chips Material y grid adaptativo |
| `[ ]` | Resultados y recientes | `ExploreResultsSection.kt` | `GL TH IME M3 AD` | Estados, listas y targets con fuente grande |
| `[ ]` | Usuarios sugeridos | `SuggestedUsersView.kt` | `GL TH E2E M3 BK AD` | Filas, follow state y pantalla fullscreen auditada |
| `[ ]` | Detalle Explore | `ExploreMomentDetailView.kt` | `GL TH E2E M3 BK` | Back/transición y dialogs secundarios con insets |
| `[ ]` | Mapa descubrir | `DiscoverMapView.kt` | `GL TH E2E M3 BK SH AD` | Chrome tonal sobre mapa, clusters, FAB y gestos |
| `[ ]` | Mapa de ubicación | `LocationMapView.kt` | `GL TH E2E M3 BK SH` | Botones contrastados, permiso y dialogs fullscreen |
| `[ ]` | Lugar / detalle en mapa | `MapPlaceBottomSheet.kt`, `LocationMomentDetailView.kt` | `GL TH E2E M3 BK SH AD` | Sheet Material + mapa, estado medio/expandido y multipane futuro |
| `[ ]` | Invitación Echo | `EchoInvitationView.kt` | `GL TH E2E M3 BK` | Entrada por link, confirmación y error |
| `[ ]` | Visor Echo | `EchoViewerUI.kt` | `GL TH E2E M3 BK` | Media fullscreen; blur sólo de contenido y chrome con scrim |
| `[ ]` | Historial Echo | `EchoHistoryView.kt` | `GL TH E2E M3 BK SH AD` | Lista/vacío y sheet informativo sólido |

### 13.5 Perfil, conexiones y guardados

| Estado | Pantalla / flujo | Archivos principales | Auditoría | Foco |
|:---:|---|---|---|---|
| `[ ]` | Perfil propio | `ProfileView.kt`, shell/sections | `GL TH E2E M3 BK AD` | Header/tabs/grid; eliminar setDecor local si duplica Activity |
| `[ ]` | Perfil ajeno | `UserProfileView.kt`, public/relationship views | `GL TH E2E M3 BK SH AD` | Muchos dialogs fullscreen: convertir rutas/sheets donde corresponda |
| `[ ]` | Header/estadísticas/bio | `ProfileHeaderSection.kt`, shared components | `GL TH M3 AD A11Y` | Contraste, expansión de bio y targets |
| `[ ]` | Grid/Bento | `ProfileMomentsSection.kt`, saved section | `TH E2E M3 BK AD` | Grid adaptativo en ancho grande, skeleton y scroll restoration |
| `[ ]` | Transición a detalle | zoom/shared transition components | `TH E2E M3 BK AD` | Interrumpible, reduce motion y fallback sin layer negra |
| `[ ]` | Detalle/menú de Moment | `ModernMomentDetailView.kt`, `ContextMenu.kt` | `GL TH E2E M3 BK SH` | Menú/sheets sólidos y acciones owner/visitor |
| `[ ]` | Highlights | `profile/highlights/*` | `GL TH E2E IME M3 BK SH AD` | Rail, editor, nombre/cover y dialogs auditados |
| `[~]` | Guardados | `settings/savedmoments/*`, `ProfileSavedSection.kt` | `GL TH E2E M3 BK AD` | Selección/cancelar/quitar, grid y nav bar ya detectados |
| `[~]` | Editar perfil | `ProfileEditor.kt`, picker/crop | `GL TH E2E IME M3 BK SH` | Cápsula, botones top e inputs legibles claro/oscuro |
| `[ ]` | Conexiones/listas de usuario | `SocialConnectionsView.kt`, `UserListView.kt` | `GL TH E2E IME M3 BK AD` | Search, tabs, follow state y list-detail futuro |
| `[ ]` | Mejores amigos | `BestFriendsView.kt` | `GL TH E2E IME M3 BK AD` | Inputs/superficies y selección legibles |
| `[ ]` | Actividad compartida | `SharedActivityView.kt`, detail | `GL TH E2E M3 BK AD` | Quitar glass de chrome; gráficas/listas con tokens |
| `[ ]` | Incógnito | `IncognitoModeSheet.kt`, overlay | `GL TH E2E M3 BK SH` | Sheet tonal; overlay semántico y countdown visibles |
| `[ ]` | QR/compartir perfil | `QRCode.kt`, acciones de header | `TH E2E M3 BK SH` | Sharesheet, guardar y contraste del QR |
| `[ ]` | Captura segura | `ScreenshotProtectedView.kt`, secure host | `TH E2E BK A11Y` | Seguridad sin canvas negro ni pérdida de semántica |

### 13.6 Creator

| Estado | Pantalla / flujo | Archivos principales | Auditoría | Foco |
|:---:|---|---|---|---|
| `[ ]` | Entrada y selección de tipo | `CreatorView.kt`, `ContentTypeSelectionView.kt` | `GL TH E2E M3 BK` | Fullscreen intencional; opciones sólidas y salida segura |
| `[ ]` | Galería/álbum | `MediaSelectionView.kt`, `AlbumPickerView.kt` | `GL TH E2E M3 BK AD` | Photo Picker, grid adaptativo y selección clara |
| `[ ]` | Cámara | `StoryCameraView.kt`, CameraX wrappers | `GL TH E2E M3 BK` | Preview fullscreen; controles sobre scrim, cutout y nav gestures |
| `[ ]` | Editor multimedia | `MediaEditingView.kt`, `EditableImageView.kt` | `GL TH E2E M3 BK` | Chrome sólido/tonal sobre media, crop/pan y back |
| `[ ]` | Editor vídeo/trim | `VideoEditor.kt`, `StoryVideoTrimEditorView.kt` | `GL TH E2E M3 BK SH` | Sustituir picker glass; timeline/touch y dialog fullscreen |
| `[ ]` | Editor Story | `storyeditor.kt`, overlays | `GL TH E2E IME M3 BK` | Canvas intacto, herramientas Android y teclado sin tapar |
| `[ ]` | Texto Story | `StoryTextEditor.kt`, text components | `TH E2E IME M3 BK` | Efecto glass del contenido se conserva; chrome no |
| `[ ]` | Dibujo | `StoryDrawingEditorOverlay.kt`, `DrawingView.kt` | `TH E2E M3 BK` | Latencia, paleta y gestos del sistema |
| `[ ]` | Stickers/picker | `stickerview.kt`, `StickerOverlayView.kt`, inputs | `GL TH E2E IME M3 BK SH` | Search, sheets y controles sólidos; arte de stickers intacto |
| `[ ]` | Ubicación y etiquetas | `LocationPickerView.kt`, `PhotoTagSelectionView.kt` | `GL TH E2E IME M3 BK SH` | Search/permiso, chips y overlays con foco |
| `[ ]` | Capas ocultas | `HiddenLayersEditorView.kt` | `GL TH E2E IME M3 BK SH` | Blur semántico, editor y schedule sheet |
| `[ ]` | Caption/detalles | `CaptionAndDetailsView.kt` | `GL TH E2E IME M3 BK` | Scroll/formulario, menciones y CTA visible |
| `[ ]` | Audiencia/listas | `audienceselector/*` | `GL TH E2E IME M3 BK SH AD` | Rows/sheets/dialogs, selección y listas grandes |
| `[ ]` | Cadenas | `ChainConfigurationView.kt`, selector | `GL TH E2E IME M3 BK` | Jerarquía de rutas, back y búsqueda |
| `[ ]` | Publicación/progreso | upload overlays/workers | `TH E2E M3 BK A11Y` | Progreso persistente, cancel/retry y notificación |

### 13.7 Mensajería

| Estado | Pantalla / flujo | Archivos principales | Auditoría | Foco |
|:---:|---|---|---|---|
| `[ ]` | Bandeja | `MessagingView.kt`, conversation rows | `GL TH E2E IME M3 BK AD` | Renunciar a glass de filas/FAB/search; listas y rail futuro |
| `[ ]` | Nueva conversación | contenido de `MessagingView.kt` | `GL TH E2E IME M3 BK SH` | Search/selección y sheet/dialog sólido |
| `[ ]` | Chat | `screens/chat/GlassmorphicChatView*.kt` | `GL TH E2E IME M3 BK AD` | Renombrado posterior; canvas, toolbar, lista y composer sin glass |
| `[ ]` | Toolbar/búsqueda | `GlassmorphicChatViewToolbar.kt`, chrome | `GL TH E2E IME M3 BK` | App bar/search tonal, Up 48 dp y estado de búsqueda |
| `[ ]` | Composer | `ChatInputViews.kt`, composer/chrome | `GL TH E2E IME M3 BK` | Superficie sólida, pegado al IME sin hueco/doble inset |
| `[ ]` | Burbujas/fechas/estado | bubble/support/chrome views | `GL TH M3 AD A11Y` | Contenido legible; píldoras tonales sin glass |
| `[ ]` | Media y galería | media/cluster/fullscreen views | `TH E2E M3 BK AD` | Blur permitido para previews restringidas/backdrops; chrome tonal |
| `[ ]` | View once/vanish | immersive/ephemeral/vanish views | `GL TH E2E M3 BK SH` | Seguridad, timer, scrim y acciones legibles |
| `[ ]` | Opciones y selección | options/context menu/interactions | `GL TH M3 BK SH` | Menú Material, long press y Back sale de selección |
| `[ ]` | Adjuntos | `ChatAttachmentSheet.kt` | `GL TH E2E IME M3 BK SH` | Sheet con handle, permisos y media grid |
| `[ ]` | GIF/stickers | `ChatGiphyPickerSheet.kt`, bubbles | `GL TH E2E IME M3 BK SH` | Search/paging y sheet sólido |
| `[ ]` | Ubicación | `ChatLocationSheet.kt`, location bubble/detail | `GL TH E2E M3 BK SH` | Mapa+sheet, permiso y sharing activo |
| `[ ]` | Cámara del chat | `ChatCameraView.kt`, `CameraPickerView.kt` | `GL TH E2E M3 BK` | CameraX/fullscreen, controles y lifecycle |
| `[ ]` | Solicitudes | `MessageRequestsView.kt` | `TH E2E M3 BK SH AD` | List/empty/dialog de accept-reject |
| `[ ]` | Archivadas | `ArchivedConversationsView.kt` | `GL TH E2E M3 BK AD` | Eliminar glass de rows/header y restaurar |
| `[ ]` | Ajustes conversación | `ConversationSettingsView.kt` | `GL TH E2E IME M3 BK SH AD` | Secciones/cards, shared media y acciones destructivas |
| `[ ]` | Reenvío/recuperación PIN | forward sheet, `ChatRecoveryViews.kt` | `GL TH E2E IME M3 BK SH` | Autofill, foco, error y sheet sólido |
| `[ ]` | Audio/voz | `VoiceNotes.kt`, recording views | `GL TH E2E M3 BK A11Y` | No glass en reposo, estado grabando claro y audio focus |

### 13.8 Ajustes y actividad

Aplicar además el contrato detallado de `ANDROID_SETTINGS_UI_STYLE.md`.

| Estado | Pantalla / flujo | Archivos principales | Auditoría | Foco |
|:---:|---|---|---|---|
| `[~]` | Ajustes raíz | `SettingsView.kt`, section components | `GL TH E2E M3 BK AD` | Cajas aprobadas, alineación y contraste final ambos temas |
| `[~]` | Información personal | `PersonalInfoSettingsViews.kt` | `GL TH E2E IME M3 BK` | Inputs comunes, app bar y cooldown |
| `[~]` | Contraseña/PIN | `PasswordChangeView.kt`, `SetPasswordView.kt` | `GL TH E2E IME M3 BK` | Autofill, visibilidad, error y CTA |
| `[~]` | Cuenta | `AccountManagement.kt` | `GL TH E2E IME M3 BK SH` | Secciones, Google link/unlink y destructivas |
| `[~]` | Privacidad del contenido | `ContentVisibilityView.kt`, privacy sections | `GL TH E2E M3 BK SH` | Cajas Moments, switches y selectors sin chevron de dismiss |
| `[ ]` | Bloqueados | `BlockedUsersView.kt` | `TH E2E M3 BK AD` | Lista/empty, unblock y confirmación |
| `[~]` | Silenciados | `MuteSettingsView.kt` | `GL TH E2E IME M3 BK SH` | Search, horarios y sheets comunes |
| `[~]` | Notificaciones | `NotificationSettingsView.kt` | `GL TH E2E M3 BK SH` | Canales/permisos Android y cajas coherentes |
| `[ ]` | Estado online | `OnlineStatusSection.kt` | `TH M3 BK SH` | Selector, explicación y persistencia |
| `[ ]` | Almacenamiento chat | `ChatStorageSettingsView.kt` | `TH E2E M3 BK SH` | Tamaños/progreso y borrar caché |
| `[~]` | Descargar datos | `DataExportView.kt` | `GL TH E2E IME M3 BK SH` | Progreso/error y botones inferiores sólidos |
| `[~]` | Tu actividad | `UserActivityView.kt` | `GL TH E2E M3 BK AD` | Cajas, rutas internas y back jerárquico |
| `[~]` | Detalles/selección actividad | `UserActivityDetailView.kt`, rows | `GL TH E2E M3 BK SH AD` | Top bar compacta, selection bar y action banner/snackbar |
| `[ ]` | Historial cuenta | `AccountHistoryActivityView.kt` | `TH E2E M3 BK AD` | Timeline/filtros/fechas |
| `[ ]` | Historial búsqueda | `SearchHistoryActivityView.kt` | `TH E2E M3 BK SH AD` | Empty/delete/confirmación |
| `[ ]` | Actividad de login | `LoginActivityView.kt` | `TH E2E M3 BK SH AD` | Dispositivo actual, sesiones y revoke |
| `[ ]` | Tiempo/descanso | daily limit/rest/time spent views | `TH E2E M3 BK SH AD` | Pickers Android, controles y Digital Wellbeing |
| `[~]` | Guardados | saved moments views | `GL TH E2E M3 BK AD` | Selección, cancelar/quitar y grid compacto |

### 13.9 Nova, notificaciones, moderación y misc.

| Estado | Pantalla / flujo | Archivos principales | Auditoría | Foco |
|:---:|---|---|---|---|
| `[ ]` | Nova principal | `NovaView.kt`, chat/chrome/input sections | `GL TH E2E IME M3 BK AD` | Canvas/composer sólidos, tools/status legibles y scroll estable |
| `[ ]` | Adjuntos Nova | `NovaAttachmentSheet.kt` | `GL TH E2E IME M3 BK SH` | Sheet Material, cámara/galería y subniveles con Back |
| `[ ]` | Confirmación de tool | `NovaActionConfirmationOverlay.kt` | `GL TH E2E M3 BK SH A11Y` | Dialog tonal, riesgo/acción claros y back bloqueado cuando proceda |
| `[~]` | Memoria Nova | `NovaMemoryManagementView.kt` | `GL TH E2E IME M3 BK SH AD` | Sheet/subsección, app bar, cajas y búsqueda |
| `[ ]` | Centro notificaciones | `NotificationsView.kt`, rows/components | `GL TH E2E M3 BK AD` | Tabs/listas/read state y deep links |
| `[ ]` | Popup/resumen notificación | `NotificationSummaryPopup.kt` | `GL TH E2E M3 BK SH` | Popup/sheet sólido, prioridad y dismiss |
| `[ ]` | Quick reply/banner in-app | banner + quick reply panel | `GL TH E2E IME M3 BK` | No tapar barras/IME; acción y timeout accesibles |
| `[ ]` | Reportar | `ReportBottomSheet.kt`, report content | `GL TH E2E IME M3 BK SH` | Sheet, formulario, progreso y errores |
| `[ ]` | Apelación/moderación | appeal/status views | `GL TH E2E IME M3 BK SH AD` | Cards/steps/acciones semánticas ambos temas |
| `[ ]` | What's New | `WhatsNewView.kt` | `GL TH E2E M3 BK AD` | Scroll/fuente grande, CTA y gate por versión |
| `[ ]` | Ads feed/story | `FeedNativeAd.kt`, `StoryNativeAd.kt` | `TH E2E M3 BK A11Y` | Disclosure legible, tamaños y chrome coherente con host |
| `[ ]` | Widget | `widget/*`, layouts XML | `TH AD A11Y` | Day/night, tamaños small/medium, preview y targets |

### Matriz de verificación para cerrar cualquier fila

- [ ] Teléfono físico: claro, oscuro, navegación por gestos y 3 botones.
- [ ] Fuente 1.0× y 1.3×; si hay mucho texto, comprobar también 2.0×.
- [ ] Captura antes/después adjunta o ruta anotada.
- [ ] Loading, empty, contenido, error, offline y disabled cuando apliquen.
- [ ] Status/nav bars, cutout e IME sin solapes ni huecos duplicados.
- [ ] Back/Up/dismiss recorren la jerarquía correcta.
- [ ] TalkBack: nombre, rol, estado, orden y acciones custom.
- [ ] Sin chrome Liquid Glass ni transparencias accidentales.
- [ ] Blur restante clasificado como contenido/privacidad, con motivo.
- [ ] Build debug y prueba manual del flujo principal.
- [ ] Para `AD`: screenshot phone/foldable/tablet después de resolver orientación.

Formato de evidencia recomendado:

```text
Pixel/Android/API · claro/oscuro · gestos/3 botones · font 1.0/1.3
Antes: docs/ui-audit/<flujo>/before.png
Después: docs/ui-audit/<flujo>/after.png
Glass retirado: ...
Blur conservado (motivo): ...
Insets/IME: ...
Decisión Material/adaptive: ...
```

## Orden de trabajo sugerido

1. **P0** Build + edge-to-edge shell (`MainActivity`, `TabBarView`, `Theme`)
2. **P0** Sheets M3 (gestos nativos; migrar hosts custom)
3. **P0** Styles / tokens (`Theme`, `AdaptiveColors`, Auth/Nova themes)
4. **P0** Navigation-3 diseño (AppRouter → Nav3) sin romper deep links
5. **P1** CameraX en Creator + ChatCamera — core cerrado
6. **P1** Intent security — cerrado
7. **N/A** `verified-email` CredMan — Firebase email basta (paridad iOS)
8. **P1** Testing harness mínimo (pausado si no se pide)
9. **P2** Perfetto en Feed list + Chat scroll + Story viewer
10. **P3** AppFunctions + Play policy / Engage

## Cómo cerrar una fila

1. Abrir el skill correspondiente en `.agents/skills/android-<name>/SKILL.md`.
2. Auditar el archivo vs la skill (no vs Swift).
3. Aplicar el cambio nativo Android.
4. Verificar en device/emulator (`android` CLI si está instalado).
5. Marcar `[x]` y nota corta (qué cambió).

---

_Generado: inventario UI ~499 archivos; total proyecto ~647 `.kt`. Services de datos no-UI se auditan en capas 7–11 si aplica skill._
