# Moments — Inventario de archivos (port iOS → Android)

Checklist **archivo por archivo** de los `.swift` del target iOS y su equivalente en Android. Generado desde `../Moments/Moments`. Complementa a [PORT_CHECKLIST.md](PORT_CHECKLIST.md) (vista por carpeta).

**Mapa actual: ~165 / 574 archivos con contraparte nombrada** (conteo aproximado; actualizar al cerrar cada lote). Esto no es una certificación de paridad:

| Marca | Significado |
|---|---|
| `[ ]` | Sin contraparte Kotlin |
| `[x]` | Reservado para el cierre final, tras tu revisión manual de paridad |
| `[~]` | Contraparte creada/portada, **en revisión**: puede tener stubs, placeholders o divergencias pendientes de validar |

Ningún archivo se considera espejo 1:1 hasta comparar datos, estados, errores y UI/UX con su Swift y recibir tu revisión final. **Regla de trabajo:** todo archivo portado permanece `[~]` hasta ese cierre; al cerrar un lote, actualizar este archivo en el mismo turno.

## Views  (12/421)

**`Components`**
- [~] AnimatedStickerView.swift → `views/components/AnimatedStickerView.kt` (GIF animado Coil cacheado y fallback Bitmap; gestos delegados al contenedor)
- [~] AudienceIconView.swift → `views/components/AudienceIconView.kt` (assets, tint por audiencia, métricas y variante para grid)
- [~] CommentRowSkeletonView.swift → `views/components/CommentRowSkeletonView.kt` (fila/lista shimmer con medidas del comentario Swift)
- [~] EchoesIconView.swift → `views/components/EchoesIconView.kt` (asset template, tint primario por defecto y gradientes de marca)
**`Components/HiddenLayers`**
- [ ] HiddenLayerLayout.swift
**`Components`**
- [~] InAppBannerView.swift → `views/components/InAppBannerView.kt` (estado, animación, swipe-dismiss, navegación y preview Story; avatar remoto, preview Moment y quick reply pendientes en el siguiente archivo)
- [~] InAppMessageQuickReplyPanel.swift → `views/components/InAppMessageQuickReplyPanel.kt` (acceso chat, preview, envío y ciclo del timer; pulido de avatar/foco pendiente)
- [~] IntelligentGlow.swift → `views/components/IntelligentGlow.kt` (halo angular de tres capas, foco y reduce-motion)
- [~] InteractiveStickerSharedViews.swift → `views/components/InteractiveStickerSharedViews.kt` (subbloque Polaroid; resto pending)
- [~] LiveUsernameText.swift → `views/components/LiveUsernameText.kt` (fallback, refresh UserCache y protección contra callbacks obsoletos)
- [~] LocationMomentCardSkeletonView.swift → `views/components/LocationMomentCardSkeletonView.kt` (tarjeta, overlay y shimmer compartido)
- [~] MomentCaptionView.swift → `views/components/MomentCaptionView.kt` (feed/detail reader + Reels colapsado/expandido; media preview contextual pendiente de ampliar el contrato Android)
- [~] MomentHashtagText.swift → `views/components/MomentHashtagText.kt` (parser Unicode, hashtags/menciones/acciones inline y estilos)
- [~] MomentRailComponents.swift → `views/components/MomentRailComponents.kt` (fachada) + `views/feed/AdaptiveColors.kt`, `views/feed/reactions/MomentReactionButton.kt` y `views/feed/core/sections/FeedMomentComponents.kt` (paleta, rail y follow)
- [~] MomentRefresh.swift → `views/components/MomentRefresh.kt` (estado único, threshold, acción y overlay gota; conectar pull real de cada `PullToRefreshBox` en pulido)
- [~] MomentRowButton.swift → `views/components/MomentRowButton.kt` (press/menu feedback, selección háptica y modifier de fila)
- [~] OfflineBanner.swift → `views/components/OfflineBanner.kt` (offline/slow reactivos, auto-hide y retry; sustituir banner simplificado del feed en pulido)
- [~] RefreshControl.swift → `views/components/RefreshControl.kt` (indicador rotatorio; gesto nativo delegado a `PullToRefreshBox`)
- [~] SkeletonShimmer.swift → `views/components/SkeletonShimmer.kt` (modifier de pulso, respeta reduce-motion)
- [~] StoryViewerSkeletonView.swift → `views/components/StoryViewerSkeletonView.kt` (segmentos, cabecera y shimmer)
- [~] UserRowSkeletonView.swift → `views/components/UserRowSkeletonView.kt` (fila/lista, avatar configurable y shimmer)
- [~] VerifiedBadge.swift → `views/components/VerifiedBadge.kt` (sello gradiente y wrappers de username; lookup vivo ya existente)
**`Creator/AudienceSelector`**
- [~] AudienceModels.swift → `views/creator/audienceselector/AudienceModels.kt` (audiencias, normalización, metadatos y listas Firestore; aliases de compatibilidad)
- [~] AudienceSelectionRows.swift → `views/creator/audienceselector/AudienceSelectionRows.kt` (filas, opciones y carrusel de listas; acciones de borrado conservadas para gestión)
- [~] AudienceSelectionView.swift → `views/creator/audienceselector/AudienceSelectionView.kt` (filas/selección predefinida y carrusel espejo; flujos de personas, crear/editar/gestionar listas se completan en sus tres archivos siguientes)
- [~] CustomAudienceManagementViews.swift → `views/creator/audienceselector/CustomAudienceManagementViews.kt` (búsqueda/selección de personas, listener de listas, grilla, edición delegada y borrado Firestore)
- [~] CustomListSelectorView.swift → `views/creator/audienceselector/CustomListSelectorView.kt` (carga Firestore, vacío/carga, selección y derivación de creación)
**`Creator`**
- [~] BackgroundMomentUploadService.swift → `views/creator/BackgroundMomentUploadService.kt`
- [~] BackgroundStoryUploadService.swift → `views/creator/BackgroundStoryUploadService.kt` (+ `StoryStickerRebuild.kt`)
**`Creator/CameraKit`**
- [~] CameraKitSpike.swift → `views/creator/camerakit/CameraKitSpike.kt` (contrato/controller y preview CameraX; lentes reales bloqueadas hasta añadir SDK/credenciales Snap Android)
- [~] LensReel.swift → `views/creator/camerakit/LensReel.kt` (carrusel centrado y disparador nativo; contenido de lentes pendiente del SDK Snap Android)
**`Creator`**
- [~] ChainConfigurationView.swift → `views/creator/ChainConfigurationView.kt` (reglas, readonly en continuación, audiencia, validación de título y confirmación)
- [~] ChainContinuationSelectorView.swift → `views/creator/ChainContinuationSelectorView.kt` (predefinidas, listas y personas; creación/edición de listas se conecta al portar esos destinos Swift)
**`Creator/Components`**
- [~] CaptureButton.swift → `views/creator/creatorscreens/CaptureButton.kt`
- [~] EditableImageView.swift → `views/creator/components/EditableImageView.kt` (layout, límites, paleta/fondo y transformaciones pan/pinch/rotación de Bitmap)
- [~] StickerDetailPalette.swift → `views/creator/components/StickerDetailPalette.kt` (tokens oscuro/claro y helper Compose)
- [~] StickerGiphyViews.swift → `views/creator/components/StickerGiphyViews.kt` (modelos, grid y GIF animado Coil)
- [~] StickerInputViews.swift → `views/creator/components/StickerInputViews.kt` (Mention con búsqueda/recientes/sugerencias y Link; el Swift no declara otros inputs activos)
- [~] StickerLocationInputView.swift → `views/creator/components/StickerLocationInputView.kt`
- [~] StickerMediaInputs.swift → `views/creator/components/StickerMediaInputs.kt` (SelfieCameraView/ImagePicker frontal y AudioStickerRecordingView; frame/reveal son inputs inline de stickerview)
- [~] StickerPickerGeneratedStickers.swift → `views/creator/components/StickerPickerGeneratedStickers.kt` (time/weather, símbolos/gradientes semánticos y fallback de ubicación/clima; el backend WeatherService se completa en su archivo)
- [~] StickerPickerLayout.swift → `views/creator/components/StickerPickerLayout.kt` (flow centrado con stagger por fila y glyph animado del emoji slider; usado por el catálogo)
- [~] StickerPickerSupportExtensions.swift → `views/creator/components/StickerPickerSupportExtensions.kt` (glow/press/mesh fallback y menciones filtradas por audiencia, conectadas al publish de Story)
- [~] StoryBackgroundPresets.swift → `views/creator/components/StoryBackgroundPresets.kt` (modelo, modo auto y los presets/colores Swift)
- [~] StoryColorPickerView.swift → `views/creator/components/StoryColorPickerView.kt` (rueda HSB, S/B, sugerencias, swatches y callback de eyedropper)
- [~] StoryDominantColorsExtractor.swift → `views/creator/components/StoryDominantColorsExtractor.kt` (buckets 48×48 y sampleColor fill para eyedropper; EditableImageView usa este extractor)
- [~] StoryDrawingEditorOverlay.swift → `StoryDrawingEditorOverlay.kt` (pinceles/undo/redo/export PNG; ColorPicker sistema deferred)
- [~] StoryEditingControls.swift → `views/creator/components/StoryEditingControls.kt` (chrome, botones/herramientas, filas y toggles Compose)
- [~] StoryEditorTextTypes.swift → `StoryEditorTextTypes.kt` (estilos Aa + swatches; motion/effects pending)
- [~] StoryFilterSelectorView.swift → `StoryFilterSelectorView.kt` (+ intensidad; cableado en StoryEditing)
- [~] StoryFontRegistry.swift → `StoryFontRegistry.kt` + assets/fonts/*.ttf
- [~] StoryTextAttributesBuilder.swift → `views/creator/components/StoryTextAttributesBuilder.kt` (contrato de render, contraste/fondos/tracking/stroke, display transform y medición/caja; renderer visual en el siguiente archivo)
- [~] StoryTextEditorChrome.swift → `views/creator/components/StoryTextEditorChrome.kt` (dos filas completas: fuentes, color/gradiente, motion, visual y toolbar de seis acciones state-hoisted)
- [~] StoryTextGradientSettings.swift → `views/creator/components/StoryTextGradientSettings.kt` (límites, presets, encode/decode, puntos y ciclo de ángulo)
- [~] StoryTextMotionEngine.swift → `views/creator/components/StoryTextMotionEngine.kt` (pop/bounce/wave/typewriter/reveal con frame Compose, replay token, transform y máscara de texto)
- [~] StoryTextOverlayMetadata.swift → `StoryTextOverlayDraft.kt` + modelo `Models.kt` (draft/metadata, placement, build, gradiente, raw legacy, configuración escalada y migración desde Story)
- [~] StoryTextEditor.swift → `views/creator/creatorscreens/StoryTextEditor.kt` (estado completo y persistido: chrome, HSB/eyedropper, motion, gradiente, fondo, alineación, caps y slider cónico; renderer visual fino pendiente)
- [~] StoryTextVisualRenderer.swift → `views/creator/components/StoryTextVisualRenderer.kt` (tratamientos visuales, mapping de efectos/estilos y orden de toolbar)
- [~] StoryVideoPlayerView.swift → `views/creator/components/StoryVideoPlayerView.kt` (ExoPlayer privado, gravity fit/fill, mute, scrub, playhead a 20 Hz, loop de trim y cleanup; usado por el preview de vídeo del editor)
**`Creator/CreatorScreens`**
- [~] AlbumPickerView.swift → `views/creator/creatorscreens/AlbumPickerView.kt` (sheet, filas, cancelación, selección y thumbnails perezosas desde MediaStore; MediaSelectionView lo consume)
- [~] CaptionAndDetailsView.swift → `views/creator/creatorscreens/CaptionAndDetailsView.kt` (share + Location/Audience/PhotoTag/HiddenLayers texto; schedule pending)
- [~] ContentTypeSelectionView.swift → `views/creator/creatorscreens/ContentTypeSelectionView.kt`
- [~] FilterOption.swift → `views/creator/creatorscreens/FilterOption.kt` (preview filtrada asíncrona, selección/estilo y cableado en MediaEditing y StoryFilterSelector)
- [~] LocationPickerView.swift → `views/creator/creatorscreens/LocationPickerView.kt` (search + GPS; sin MapKit)
- [~] MediaEditingView.swift → `views/creator/creatorscreens/MediaEditingView.kt` (paginación, crop libre/ratios recomendados, filtros temporales y persistidos, intensidad, preview, miniaturas y navegación)
- [~] MediaGridCell.swift → `views/creator/creatorscreens/MediaGridCell.kt` (thumbnail/carga, selección numerada, overlay, duración de vídeo y formato; usado por el grid de MediaSelection)
- [~] MediaSelectionView.swift → `views/creator/creatorscreens/MediaSelectionView.kt` (galería/álbumes MediaStore, permisos, selección, preview, límites de vídeo y CameraCapture conectado)
- [~] StickerOverlayView.swift → `views/creator/creatorscreens/StickerOverlayView.kt` (contenedor Compose con límites por tipo/tamaño/rotación, arrastre, pinch amortiguado, giro, edición interna de frame y feedback; cámara selfie CameraX con captura/tap y flip/long-press)
- [~] StoryCameraView.swift → `views/creator/creatorscreens/StoryCameraView.kt` (CameraX foto+vídeo long-press/flip/galería/Aa; lenses pending)
- [~] StoryOverlaysView.swift → `views/creator/creatorscreens/StoryOverlaysView.kt` (texto libre con pinch/arrastre/papelera, geometría de sticker/papelera, foco inline, Polaroid, Reveal y toast; navegación y ciclos visuales pendientes de revisión)
- [~] StoryTextEditor.swift → ver `creatorscreens/StoryTextEditor.kt` (estado/chrome/input/slider portados; renderer visual fino pendiente)
- [~] UserSearchView.swift → `views/creator/creatorscreens/UserSearchView.kt` (búsqueda Firestore, selección múltiple, chips, cancelar/confirmar; pendiente de revisión visual)
**`Creator`**
- [~] CreatorSharedModels.swift → parcial en `CreatorView.kt` (`CreatorMedia` / `CreatorAlbumInfo` / flows)
**`Creator/CreatorUIKit`**
- [~] BackgroundCameraView.swift → `views/creator/creatoruikit/BackgroundCameraView.kt` (preview CameraX trasera sin captura, lifecycle/stop por composición; conectado al fondo del selector)
- [~] CameraCapture.swift → `views/creator/creatoruikit/CameraCapture.kt` (captura sistema de foto/vídeo en URI MediaStore, ratio/duración y callbacks CreatorMedia)
- [~] CameraPreviewView.swift → `views/creator/creatoruikit/CameraPreviewView.kt` (CameraX reusable: preview, foto/vídeo, flash, zoom, flip, orientación/callbacks y cleanup; Center Stage no tiene equivalente Android)
- [~] CreatorCaptureGeometry.swift → `views/creator/creatoruikit/CreatorCaptureGeometry.kt` (aspect/capture rect, safe area de lenses, salida 1080×1920 y corner radius)
- [~] CreatorControls.swift → `views/creator/creatoruikit/CreatorControls.kt` (ToolIconButton Compose con háptica)
- [~] CreatorUIImageExtensions.swift → `views/creator/creatoruikit/CreatorUIImageExtensions.kt` (normalización Bitmap según EXIF)
- [~] CropViewWrapper.swift → `views/creator/creatorscreens/CropViewWrapper.kt` (Compose crop; TOCropViewController paridad funcional)
- [~] DrawingView.swift → `views/creator/creatoruikit/DrawingView.kt` (fachada sobre StoryDrawingEditorOverlay con imagen de fondo, cancelar y exportar trazo)
- [~] StoryGalleryPicker.swift → creatoruikit/StoryGalleryPicker.kt (one-item picker, long-video split/trim decision)
- [~] StoryMediaPicker.swift → creatoruikit/StoryMediaPicker.kt (system scoped photo picker + shared media decoding)
**`Creator`**
- [~] CreatorView.swift → `views/creator/CreatorView.kt` (shell + type/media/edit/caption/story camera+editor chunk1)
- [~] HiddenLayersEditorView.swift → `views/creator/creatorscreens/HiddenLayersEditorView.kt` (texto máx 3; image/audio/schedule pending)
- [~] PhotoTagSelectionView.swift → `views/creator/creatorscreens/PhotoTagSelectionView.kt`
- [~] StickerEmojiPalettePicker.swift → catálogo en `StickerEmojiCatalog.kt` + UI en `StickerPickerView.kt`
- [~] StoryVideoProcessingService.swift → StoryVideoProcessingService.kt (duration, thumbnail, trim export, split)
- [~] StoryVideoTrimEditorView.swift → StoryVideoTrimEditorView.kt (preview loop, timeline thumbnails, trim handles/playhead, export)
- [~] VideoEditor.swift → VideoEditor.kt (multi-clip player, trim bounds, speed/format/volume controls, cover picker and processing route; timeline exactness pending review)
- [~] stickerview.swift → `views/creator/stickerview.kt` (todas las rutas activas del catálogo: Selfie/GIF/Frame/Reveal/Audio + emoji/time/weather/hashtag/mention/poll/question/link/quiz/location; playback interactivo queda en sus archivos de Story)
- [~] storyeditor.swift → `views/creator/storyeditor.kt` (+ stickers chunk7 mention/poll/question/selfie/GIF/frame/reveal/audio; motion pending)
**`Echoes`**
- [~] EchoHistoryView.swift → `views/echoes/EchoHistoryView.kt` (cableado en FeedPresentationModifier)
- [~] EchoInvitationView.swift → `views/echoes/EchoInvitationView.kt` (cableado en FeedOverlaysSection)
- [~] EchoViewerUI.swift → views/echoes/EchoViewerUI.kt (EchoViewModel live, horizontal/vertical navigation, availability/blur, video state, ripple, header progress/context, luminance-aware overlay tone, leave lockout/incomplete and location map)
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
- [~] FeedTypeSelector.swift → `views/feed/controls/FeedTypeSelector.kt`
- [~] feedchange.swift → `views/feed/controls/FeedChange.kt`
**`Feed/Core`**
- [~] FeedNotificationRoutingModifier.swift → `views/feed/core/FeedNotificationRoutingModifier.kt`
- [~] FeedPresentationModifier.swift → `views/feed/core/FeedPresentationModifier.kt` (Explore/Stories/Messaging/Profile/Edit/Comments/EchoHistory cableados; sin placeholders del contrato Feed)
- [~] FeedRoutes.swift → `views/feed/core/FeedRoutes.kt`
- [~] FeedView.swift → `views/feed/core/FeedView.kt` (feed usable; pulido visual card vs iOS)
- [~] FeedViewModel.swift → `views/feed/core/FeedViewModel.kt`
- [~] ModernEmptyFeedView.swift → `views/feed/core/ModernEmptyFeedView.kt`
**`Feed/Core/Sections`**
- [~] FeedHeaderSection.swift → `views/feed/core/sections/FeedHeaderSection.kt`
- [~] FeedListSection.swift → `views/feed/core/sections/FeedListSection.kt`
- [~] FeedMomentComponents.swift → `views/feed/core/sections/FeedMomentComponents.kt` (`ModernPostCardView`)
- [~] FeedMomentDetailRoute.swift → `views/feed/core/sections/FeedMomentDetailRoute.kt` → `MomentDetailContainerView(Single)`
- [~] FeedOverlaysSection.swift → `views/feed/core/sections/FeedOverlaysSection.kt` (EchoInvitation + ContextMenu cableados; resto parcial)
- [~] FeedPostSkeletonView.swift → `views/feed/core/sections/FeedPostSkeletonView.kt`
- [~] FeedStoryRingComponents.swift → `views/feed/core/sections/FeedStoryRingComponents.kt`
**`Feed/Moments`**
- [~] ClickableHashtagsView.swift → `views/feed/moments/ClickableHashtagsView.kt`
- [~] HiddenLayersOverlayView.swift → `views/feed/moments/HiddenLayersOverlayView.kt`
- [~] MomentCarouselLayoutRules.swift → `views/feed/moments/MomentCarouselLayoutRules.kt` (+ `MomentMediaCarousel.kt`)
**`Feed/Reactions`**
- [~] MomentReactionButton.swift → `views/feed/reactions/MomentReactionButton.kt`
- [~] reacciones.swift → `views/feed/reactions/Reacciones.kt`
**`Feed/Sharing`**
- [~] ShareMomentSticker.swift → `views/feed/sharing/ShareMomentSticker.kt`
- [~] StoryShare.swift → `views/feed/sharing/StoryShare.kt`
- [~] share.swift → `views/feed/sharing/Share.kt`
**`Feed/Stories`**
- [~] FeedStoryRingCoordinator.swift → `views/feed/stories/FeedStoryRingCoordinator.kt` (anillo en feed; viewer = stub hasta lote Stories)
- [~] StoryRingTraySkeleton.swift → `views/feed/stories/StoryRingTraySkeleton.kt` (+ `StoryRingLayout.kt`)
**`Feed/Uploads`**
- [~] FeedUploadProgressRow.swift → `views/feed/uploads/FeedUploadProgressRow.kt`
- [~] FloatingMomentUploadOverlay.swift → `views/feed/uploads/FloatingMomentUploadOverlay.kt`
- [~] StoryUploadProgressManager.swift → `views/feed/uploads/StoryUploadProgressManager.kt`
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
- [~] MapAnnotationModels.swift → `views/feed/maps/MapAnnotationModels.kt`
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
- [~] AuthUIComponents.swift → `views/login/AuthComponents.kt`
- [~] CreatingProfileView.swift → `views/login/CreatingProfileScreen.kt`
- [~] DeactivatedAccountView.swift → `views/login/AccountStateScreens.kt`
- [~] Interestview.swift — N/A (archivo vacío)
- [~] LiquidGlassComponents.swift → `views/login/AuthComponents.kt + AuthTheme.kt`
- [~] LoginView.swift → `views/login/LoginScreen.kt`
- [~] PrivacyPolicyView.swift → `views/login/PrivacyPolicyScreen.kt`
- [~] ProfileOnboardingView.swift → `views/login/OnboardingScreen.kt`
- [~] RegisterView.swift → `→ OnboardingScreen.kt`
- [~] SocialProfileCompletionView.swift — N/A (era Apple; Google crea perfil directo)
- [~] SplashScreen.swift → `views/login/SplashScreen.kt`
- [~] SuspendedAccount.swift → `views/login/AccountStateScreens.kt`
**`Messaging/Attachments`**
- [~] ChatGiphyPickerSheet.swift → views/messaging/attachments/ChatGiphyPickerSheet.kt (GIF/sticker, búsqueda debounce, paginación, masonry, recientes)
- [~] ChatLocationSheet.swift → views/messaging/attachments/ChatLocationSheet.kt (mapa pasivo, ubicación actual, POIs, búsqueda, live location y permisos)
**`Messaging/Components`**
- [~] AttachmentIconView.swift → views/messaging/components/AttachmentIconView.kt (assets existentes, métricas, presets y tint template)
- [~] ChatAdaptiveColors.swift → views/messaging/components/ChatAdaptiveColors.kt (paleta chat y CompositionLocals de burbuja/fila)
- [~] ChatAttachmentSheet.swift → views/messaging/components/ChatAttachmentSheet.kt (menú anclado, sheets GIF/sticker/ubicación y grid MediaStore multi-select)
- [~] ChatBuzzEffectViews.swift → views/messaging/components/ChatBuzzEffectViews.kt (shake, toast, evento timeline y wave shape)
- [~] ChatChromeViews.swift → views/messaging/components/ChatChromeViews.kt (chrome, historial, requests, divisores, typing, toasts y búsqueda)
- [~] ChatClusterMediaViews.swift → views/messaging/components/ChatClusterMediaViews.kt (agrupado, abanico, tiles, galería, selección y borrado)
- [~] ChatEphemeralMessageViews.swift → views/messaging/components/ChatEphemeralMessageViews.kt (compact/standard, preview, media, resolving y expirado)
- [~] ChatFloatingNavigationOverlay.swift → views/messaging/components/ChatFloatingNavigationOverlay.kt (estado derivado, navegación de búsqueda y scroll)
- [~] ChatGifMessageBubble.swift → views/messaging/components/ChatGifMessageBubble.kt (ratio, fallback, GIF y progreso de envío)
- [~] ChatInputViews.swift → views/messaging/components/ChatInputViews.kt (barra, attachments, envío, controles y preview de voz)
- [~] ChatKFImageViews.swift → views/messaging/components/ChatKFImageViews.kt (Coil, placeholder y prefetch remoto)
- [~] ChatLocationMessageBubble.swift → views/messaging/components/ChatLocationMessageBubble.kt (mapa, estática/live, countdown, detalle y Maps intents)
- [~] ChatMediaViews.swift → views/messaging/components/ChatMediaViews.kt (placeholders, descarga/subida, image/video y fullscreen image)
- [~] ChatMessageBubbleViews.swift → views/messaging/components/ChatMessageBubbleViews.kt (fila, grupos, reply/long-press/swipe, tipos de mensaje, reacciones, enlaces y preview)
- [~] ChatMessageForwardSheet.swift → views/messaging/components/ChatMessageForwardSheet.kt (wrapper y selector multi-destinatario)
- [~] ChatMessageInteractionModifiers.swift → views/messaging/components/ChatMessageInteractionModifiers.kt (pan horizontal, reply swipe, indicador, reveal timestamp y long press)
- [~] ChatMessageListView.swift → views/messaging/components/ChatMessageListView.kt (LazyColumn, transacciones, ancla prepend, scroll serializado, navegación, top/prefetch y estado viewport)
- [~] ChatMessageOptionsMenu.swift → views/messaging/components/ChatMessageOptionsMenu.kt (selección, chrome, reacciones y menú de acciones condicionado)
- [~] ChatMessageSupportViews.swift → views/messaging/components/ChatMessageSupportViews.kt (reply bars/previews, quotes, reaction chips, star y status)
- [~] ChatRecoveryViews.swift → views/messaging/components/ChatRecoveryViews.kt (gate, PIN setup/restore, settings, estado y validación)
- [~] ChatSearchNavigationBar.swift → views/messaging/components/ChatSearchNavigationBar.kt
- [~] ChatSpeechBubbleViews.swift → views/messaging/components/ChatSpeechBubbleViews.kt (grupos, forma, spoilers, reply y avatar gutter)
- [~] ChatStickerMessageBubble.swift → views/messaging/components/ChatStickerMessageBubble.kt (sticker inline, resolución cifrada y progreso)
- [~] ChatVanishModeViews.swift → views/messaging/components/ChatVanishModeViews.kt (pull, avisos, timer e indicadores)
- [~] ConversationContextMenu.swift → views/messaging/components/ConversationContextMenu.kt (selección, overlay, acciones y highlight)
- [~] MediaProgressRing.swift → views/messaging/components/MediaProgressRing.kt (anillo, gradiente, progreso y porcentaje)
- [~] MessageTypeIconView.swift → views/messaging/components/MessageTypeIconView.kt (assets custom y fallback por tipo)
- [~] MessagingComposerAndStatusViews.swift → views/messaging/components/MessagingComposerAndStatusViews.kt (composer y selector de presencia)
- [~] ViewOnceMessageBubble.swift → views/messaging/components/ViewOnceMessageBubble.kt (unread, replay, abierto y enviado)
- [~] VoiceNotes.swift → views/messaging/components/VoiceNotes.kt (grabación, composición, trim, waveform y reproducción)
- [~] VoiceRecordingGestureViews.swift → views/messaging/components/VoiceRecordingGestureViews.kt (hold, lock, cancel y aura reactiva)
**`Messaging/Core`**
- [~] ChatViewModel.swift → `views/messaging/core/ChatViewModel.kt` (estado/cache/realtime, paginación, media, envío/acciones, lectura, vanish, búsqueda y borrador; revisión de paridad pendiente)
- [~] MessageItem.swift → `views/messaging/core/MessageItem.kt` (item/cluster, filas sintéticas y secciones; revisión de paridad pendiente)
- [~] MessageModel.swift → `models/ChatModels.kt` (conversation, requests/contexto, enhanced media/view-once, política/reacciones, persistencia y mapper; revisión de paridad pendiente)
- [~] MessagingViewModel.swift → `views/messaging/core/MessagingViewModel.kt` (inbox + targetId + chat texto)
**`Messaging/Media`**
- [~] CameraPickerView.swift → `views/messaging/media/CameraPickerView.kt` (CameraX, galería, preview/retake/send y modo efímero; revisión de paridad pendiente)
- [~] ChatCameraView.swift → `views/messaging/media/ChatCameraView.kt` (captura/foto-vídeo, zoom, flash, galería y entrada al editor; revisión de paridad pendiente)
- [~] ChatMediaOverlayPayload.swift → `views/messaging/media/ChatMediaOverlayPayload.kt` (payload, overlays de texto y stickers resueltos; revisión de paridad pendiente)
- [~] ChatMediaSendMode.swift → `views/messaging/media/ChatMediaSendMode.kt` (ciclo, icono y etiqueta localizada; revisión de paridad pendiente)
- [~] ViewOnceImmersiveViewer.swift → `views/messaging/media/ViewOnceImmersiveViewer.kt` (media protegida, progreso, overlays, reply/reacciones y consumo; revisión de paridad pendiente)
**`Messaging/Models`**
- [~] ChatAttachmentAssets.swift → views/messaging/models/ChatAttachmentAssets.kt (GIF/sticker assets, recientes persistentes, payload de ubicación y duraciones live)
**`Messaging/Screens`**
- [~] ArchivedConversationsView.swift → `views/messaging/screens/ArchivedConversationsView.kt` (lista, vacío, acciones y menú contextual; revisión de paridad pendiente)
**`Messaging/Screens/Chat`**
- [~] GlassmorphicChatView+Clustering.swift → `views/messaging/screens/chat/GlassmorphicChatViewClustering.kt` (cluster, navegación, highlight y replay buzz; revisión de paridad pendiente)
- [~] GlassmorphicChatView+ComposerAndChrome.swift → `views/messaging/screens/chat/GlassmorphicChatViewComposerAndChrome.kt` (composer, requests, chrome de input, media compartida, render de filas y ciclo de pantalla; revisión de paridad pendiente)
- [~] GlassmorphicChatView+Lifecycle.swift → `views/messaging/screens/chat/GlassmorphicChatViewLifecycle.kt` (cámara/view-once, presencia, disponibilidad, bloqueo y agrupación visual; revisión de paridad pendiente)
- [~] GlassmorphicChatView+MessageList.swift → `views/messaging/screens/chat/GlassmorphicChatViewMessageList.kt` (política de filas, transacción, prefetch, aviso de historial y renderizado de filas; revisión de paridad pendiente)
- [~] GlassmorphicChatView+MessageRendering.swift → `views/messaging/screens/chat/GlassmorphicChatViewMessageRendering.kt` (filas, reply/edición, raíz chrome, toast y menú de mensaje; revisión de paridad pendiente)
- [~] GlassmorphicChatView+Scroll.swift → `views/messaging/screens/chat/GlassmorphicChatViewScroll.kt` (rutas iniciales, anclaje, snap, historial, vanish y saltos/highlight; revisión de paridad pendiente)
- [~] GlassmorphicChatView+Search.swift → `views/messaging/screens/chat/GlassmorphicChatViewSearch.kt` (toggle, foco, resultados, navegación y restauración de layout; revisión de paridad pendiente)
- [~] GlassmorphicChatView+Toolbar.swift → `views/messaging/screens/chat/GlassmorphicChatViewToolbar.kt` (toolbar, avatar, estado, rutas perfil/story/settings y cabecera de búsqueda; revisión de paridad pendiente)
- [~] GlassmorphicChatView+ViewModelAudio.swift → `views/messaging/screens/chat/GlassmorphicChatViewViewModelAudio.kt` (viewmodel derivado, agrupación/filas y contador de envíos; revisión de paridad pendiente)
- [~] GlassmorphicChatView+Voice.swift → `views/messaging/screens/chat/GlassmorphicChatViewVoice.kt` (grabación, pausa/reanudar, trim, compose/envío y divisor no leído; revisión de paridad pendiente)
- [~] GlassmorphicChatView.swift
- [~] MomentsChatViewModel+Media.swift
**`Messaging/Screens`**
- [~] ConversationSettingsView.swift
- [~] MessageRequestsView.swift
- [~] MessagingView.swift → `views/messaging/screens/MessagingView.kt` (inbox + thread texto; glass/media = abiertos)
**`Messaging/Services`**
- [~] ChatAccessCoordinator.swift → `views/messaging/services/ChatAccessCoordinator.kt`
- [~] ChatBuzzProcessedStore.swift
- [~] ChatDraftStore.swift
- [~] ChatGiphyService.swift → `views/messaging/services/ChatGiphyService.kt` (proxy Cloud Functions autenticado, trending/búsqueda/paginación)
- [~] ChatKeyboardScrollCoordinator.swift
- [~] ChatNavigationIntentStore.swift → `views/messaging/services/ChatNavigationIntentStore.kt`
- [~] ChatRowHeightEstimator.swift
- [~] ChatScrollDebug.swift
- [~] ChatScrollStateStore.swift
- [~] ChatService+Buzz.swift → `views/messaging/services/ChatServiceBuzz.kt` (evento, envío y listener Firestore; revisión de paridad pendiente)
- [~] ChatService+ChunkedVideoUpload.swift
- [~] ChatService+EncryptedMediaResolver.swift → `views/messaging/services/ChatEncryptedMediaResolver.kt`
- [~] ChatService+EphemeralCleanup.swift
- [~] ChatService+LocalFirstSnapshot.swift
- [~] ChatService+MediaPipeline.swift → `views/messaging/services/ChatServiceMediaPipeline.kt`
- [~] ChatService+MessageActions.swift
- [~] ChatService+MessageHydration.swift
- [~] ChatService+MessageReactions.swift
- [~] ChatService+Search.swift
- [~] ChatService+SharingAndViewOnce.swift
- [~] ChatService+VanishMode.swift
- [~] ChatService.swift → `views/messaging/services/ChatService.kt` (+ `ChatMessageMapper.kt`)
- [~] ChatSessionEngine.swift → `views/messaging/services/ChatSessionEngine.kt`
- [~] ChatVideoPosterGenerator.swift
- [~] LiveLocationSharingService.swift → `views/messaging/services/LiveLocationSharingService.kt` (teardown logout)
- [~] ViewOnceConsumptionService.swift → `views/messaging/services/ViewOnceConsumptionService.kt` (callable de consumo/replay; revisión de paridad pendiente)
**`Misc`**
- [~] WhatsNewView.swift
**`Nova/AI`**
- [~] NovaAIService.swift → `views/nova/ai/NovaAIService.kt` (Vertex AI, chat/stream/tools/search grounding, JSON, títulos, compactación e imagen)
- [~] NovaGenerationConfig.swift → `views/nova/ai/NovaGenerationConfig.kt` (model, generation/safety settings; SDK integration pending review with NovaAIService)
- [~] NovaPromptCatalog.swift → `views/nova/ai/NovaPromptCatalog.kt` (catálogo interno de prompts, contexto de sesión e historial)
**`Nova/Agent`**
- [~] NovaAgent.swift → `views/nova/agent/NovaAgent.kt` (turnos streaming/tools, confirmación, historial, memoria y conversaciones; dependencias Nova se resuelven en sus counterparts)
- [~] NovaContextAssembler.swift → `views/nova/agent/NovaContextAssembler.kt` (instrucción de sistema con sesión, memoria, resúmenes e historial interno)
- [~] NovaPendingAction.swift → `views/nova/agent/NovaPendingAction.kt` (confirmaciones y resúmenes de writes sensibles localizados)
- [~] NovaToolExecutor.swift → `views/nova/agent/NovaToolExecutor.kt` (dispatch completo, dedupe, confirmaciones, media y refresh de memoria)
- [~] NovaToolRegistry.swift → `views/nova/agent/NovaToolRegistry.kt` (declaraciones completas y gate de confirmación)
**`Nova/Conversation`**
- [~] NovaConversationStore.swift → `views/nova/conversation/NovaConversationStore.kt` (Firestore user-scoped/legacy, cifrado, títulos, imágenes privadas y grounding)
**`Nova`**
- [~] Conversationmodels.swift → `views/nova/Conversationmodels.kt` (títulos, documentos Firestore, media legacy y grounding)
**`Nova/Core`**
- [~] NovaLocaleContext.swift → `views/nova/core/NovaLocaleContext.kt` (locale e idioma de sesión)
**`Nova/Memory`**
- [~] NovaContextStore.swift
- [~] NovaMemoryCrypto.swift → `views/nova/memory/NovaMemoryCrypto.kt` (cifrado de facts/resúmenes y migración legacy)
- [~] NovaMemoryEngine.swift
- [~] NovaMemoryModels.swift → `views/nova/memory/NovaMemoryModels.kt` (facts, dedupe, nombre preferido, límites y Firestore)
- [~] NovaMemoryStore.swift
**`Nova/NovaCore`**
- [~] NovaModels.swift
- [~] NovaTheme.swift
**`Nova`**
- [~] NovaMemoryManagementView.swift
**`Nova/NovaSections`**
- [~] NovaAttachmentSheet.swift
- [~] NovaChatSection.swift
- [~] NovaChromeSection.swift
- [~] NovaHistorySection.swift
- [~] NovaInputSection.swift
**`Nova`**
- [~] NovaView.swift
**`Nova/Tools`**
- [~] NovaActivityTools.swift
- [~] NovaMemoryTools.swift
- [~] NovaMomentAudience.swift
- [~] NovaMomentDraftParser.swift
- [~] NovaProfileTools.swift
- [~] NovaSocialTools.swift
**`Nova/UI`**
- [~] NovaActionConfirmationOverlay.swift
**`Permission/camera`**
- [~] Contentview.swift
**`Permission/camera/helpers`**
- [~] CameraPermissionsview.swift
**`Permission/location`**
- [~] LocationPermissionView.swift
**`Permission/microphone`**
- [~] MicrophonePermissionView.swift
**`Permission/notifications`**
- [~] NotificationsPermissionView.swift
**`Permission/photos`**
- [~] PhotosPermissionView.swift
**`Permission/shared`**
- [~] LocationPermissionGate.swift
- [~] PermissionPhoneFrame.swift
- [~] PermissionPhoneWallpaper.swift
- [~] PermissionPrimerGate.swift
- [~] PermissionPrimerScaffold.swift
**`Permission/tracking`**
- [~] TrackingPermissionView.swift
**`Permissions`**
- [~] CameraAccessBoundary.swift
- [~] CameraPermissionGate.swift
**`Profile/Core`**
- [~] MomentGridPreview.swift
- [~] ProfileView.swift
- [~] ProfileViewModel.swift
**`Profile/Core/Sections`**
- [~] ProfileBentoLayout.swift
- [~] ProfileGridHeroTransition.swift
- [~] ProfileGridMomentMenu.swift
- [~] ProfileGridPreviewEditorView.swift
- [~] ProfileHeaderSection.swift
- [~] ProfileHeaderSkeletonView.swift
- [~] ProfileMomentZoomNavigation.swift
- [~] ProfileMomentsSection.swift
- [~] ProfileSavedSection.swift
- [~] ProfileSharedComponents.swift
- [~] ProfileShellComponents.swift
- [~] UserProfileZoomNavigation.swift
**`Profile/Core`**
- [~] SharedActivityDetailView.swift
- [~] SharedActivityView.swift
- [~] SocialConnectionUserRow.swift
- [~] SocialConnectionsView.swift
- [~] UserListView.swift
**`Profile/Editor`**
- [~] PhotoCropEditorView.swift
- [~] ProfileEditor.swift
**`Profile/Editor/Sections`**
- [~] ProfileEditorPickerViews.swift
**`Profile/Highlights`**
- [~] HighlightComponents.swift
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
- [~] ContextMenu.swift → `views/profile/momentsview/ContextMenu.kt` (`ModernContextMenuOverlay`; cableado en FeedOverlays)
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
- [~] AccountHistoryActivityView.swift → `views/settings/AccountHistoryActivityView.kt`
- [~] AccountManagement.swift → `views/settings/AccountManagement.kt`
- [~] BlockedUsersView.swift → `views/settings/BlockedUsersView.kt`
- [~] ChatStorageSettingsView.swift → `views/settings/ChatStorageSettingsView.kt`
- [~] ContentVisibilityView.swift → `views/settings/ContentVisibilityView.kt`
- [~] DailyLimitView.swift → `views/settings/DailyLimitView.kt`
- [~] DataExportView.swift → `views/settings/DataExportView.kt`
- [~] LoginActivityView.swift → `views/settings/LoginActivityView.kt`
- [~] MuteSettingsView.swift → `views/settings/MuteSettingsView.kt`
- [~] PasswordChangeView.swift → `views/settings/PasswordChangeView.kt`
- [~] QRCode.swift → `views/settings/QRCodeView.kt`
- [~] RestModeView.swift → `views/settings/RestModeView.kt`
**`Settings/SavedMoments`**
- [~] SavedMomentsViewModel.swift → `views/settings/savedmoments/SavedMomentsViewModel.kt`
**`Settings`**
- [~] SavedMomentsView.swift → `views/settings/savedmoments/SavedMomentsView.kt`
- [~] SearchHistoryActivityView.swift → `views/settings/SearchHistoryActivityView.kt`
- [~] SetPasswordView.swift → `views/settings/SetPasswordView.kt`
- [~] SettingsNavigationComponents.swift → `views/settings/SettingsNavigationComponents.kt`
**`Settings/SettingsSections`**
- [~] NotificationSettingsView.swift → `views/settings/settingssections/NotificationSettingsView.kt`
- [~] OnlineStatusSection.swift → `views/settings/settingssections/OnlineStatusSection.kt`
- [~] PersonalInfoSettingsViews.swift → `views/settings/settingssections/PersonalInfoSettingsViews.kt`
- [~] SettingsSections.swift → `views/settings/settingssections/SettingsSections.kt`
**`Settings`**
- [~] SettingsView.swift → `views/settings/SettingsView.kt`
- [~] SettingsViewModel.swift → `views/settings/SettingsViewModel.kt`
- [~] TimeSpentCardView.swift → `views/settings/TimeSpentDetailsView.kt` (TimeSpentCardView: media diaria + barras 7 días)
- [~] TimeSpentDetailsView.swift → `views/settings/TimeSpentDetailsView.kt` (cabecera + card + filas a DailyLimit/RestMode; enganchado en UserActivityView)
- [~] UserActivityBackendModels.swift → `views/settings/UserActivityBackendModels.kt` (contrato de las Cloud Functions con `org.json` en vez de Codable; `BackendTagsCursor` se reutiliza de `BackendFeedService`)
- [~] UserActivityCache.swift → `views/settings/UserActivityCache.kt` (SharedPreferences con las mismas claves `activityCache_*_{uid}` que UserDefaults; sin verificar en pantalla)
- [~] UserActivityComponents.swift → `views/settings/UserActivityComponents.kt` (fila de categoría con iconos animados + badge de contador, `StripThumbCell` con thumbnail de vídeo/blur/candado, `AuthorFilterSheet`; sin verificar en pantalla)
- [~] UserActivityDetailView.swift → `views/settings/UserActivityDetailView.kt` (MVP: estados carga/error/vacío, grid reacciones/tags/archivados/papelera + listas comentarios/eventos + grid moments/reels, filtros orden/fecha/autor, modo selección y borrado/restauración por lotes con diálogo de confirmación y banner de éxito. **Diferido a pulido:** drag-select por arrastre, auto-scroll, rango de fechas custom (se comporta como "todo"), y la transición de zoom compartida — delegada al host vía callback `onOpenMoment` reutilizando `MomentZoomOpener`/`ProfileMomentZoomNavigation` que YA existen en el port)
- [~] UserActivityDetailViewModel.swift → `views/settings/UserActivityDetailViewModel.kt` (11 categorías, borrado por lotes, cursores y caché-primero; `fetchNotifications` sin consumidor igual que en iOS; sin verificar en pantalla)
- [~] UserActivityModels.swift → `views/settings/UserActivityModels.kt`
- [~] UserActivityRows.swift → `views/settings/UserActivityRows.kt` (7 componentes: fila comentario, preview moment, fila evento con variantes echo/visita/follower, tarjetas reacción/portrait/story borrada, indicador de vídeo; helper `MomentPreviewContent` unifica la cascada de preview; sin verificar en pantalla)
- [~] UserActivitySummaryViewModel.swift → `views/settings/UserActivitySummaryViewModel.kt` (contadores + warm-up de VMs; thumbnails vacíos como en iOS; sin verificar en pantalla)
- [~] UserActivityTypes.swift → `views/settings/UserActivityTypes.kt` (iconos SF→Material salvo tags/echoes por asset; `AnimatedReactionIcon`/`AnimatedCommentIcon` con su animación; bubbles de comentario reducidas de 6 a 3 por falta de equivalentes Material)
- [~] UserActivityView.swift → `views/settings/UserActivityView.kt` (pantalla raíz "Tu actividad" con las 5 secciones, contadores del SummaryVM, navegación interna por categoría; **enganchada en Settings** vía ruta `user_activity` en ProfileView. `searches`→SearchHistoryActivityView, `storiesArchive`→ArchivedStoriesView, resto→DetailView; `timeSpent`/`accountHistory` abren el detalle vacío hasta portar sus pantallas)
**`Shared`**
- [ ] AppErrorBanner.swift
- [ ] BlurView.swift
**`Shared/MomentDetail`**
- [~] MomentDetailContainerView.swift → `views/shared/momentdetail/MomentDetailContainerView.kt` (ProfileCarousel cae a Single hasta portar ModernMomentDetailView)
- [~] MomentDetailContext.swift → `views/shared/momentdetail/MomentDetailContext.kt`
- [~] SingleMomentDetailView.swift → `views/shared/momentdetail/SingleMomentDetailView.kt` (card/chrome/menu/peek/mapa/delete + ModernComments; Edit/Explore = placeholders)
**`Shared`**
- [ ] MomentsVideoPlaybackTimeline.swift
- [ ] MomentsVideoPlayer.swift
- [ ] OfflineBannerModifier.swift
- [ ] PhotoTagOverlayView.swift
- [~] ScreenshotProtectedView.swift → `views/shared/ScreenshotProtectedView.kt`
**`comments`**
- [~] CommentMentionSearchOverlay.swift → `views/comments/CommentMentionSearchOverlay.kt`
- [ ] CommentsView.swift (legacy iOS; el feed usa ModernCommentsView)
- [~] ModernCommentsView.swift → `views/comments/ModernCommentsView.kt` (+ `CommentMuteFilters.kt`, `CommentMentionDraft.kt`, `EnhancedModernCommentRow.kt`) — listener, mute filters, menciones, skeletons, StoryRing, like/reply/edit/delete, moderación; cableado en FeedPresentation + SingleMomentDetail
**`story`**
- [~] QuestionResponsesView.swift → `views/story/QuestionResponsesView.kt` (tarjeta, listado Firestore de respuestas y creator con `questionResponse`)
- [~] StoriesView.swift → `views/story/StoriesView.kt` (MVP ring + viewer; ads/chains omitidos)
- [~] StoryChainView.swift → `views/story/StoryChainView.kt` (carga/stats, selección inicial, grid, apertura y validación de límite antes de continuar; `StoryChainItemView` legacy no se usa por el grid Swift actual)
- [~] StoryDeckGestureGate.swift → `views/story/StoryDeckGestureGate.kt` (scopes + regiones; cableado al Reveal/deck; otros stickers pending)
- [~] StoryInteractiveStickers.swift → `views/story/StoryInteractiveStickers.kt` (Polaroid frame: base64, posición/escala/rotación, shake-to-reveal y persistencia; Reveal scratch 65 % + persistencia + todos los patrones, incluido holographic con tilt; pulido de efectos y coordinación de gestos pendiente)
- [~] StoryModels.swift → `views/story/StoryModels.kt` (StoryReaction/latestPerUser, StoryViewer Firestore, StoryRing; badges ya en `views/components/VerifiedBadge.kt`)
- [~] StoryPlaybackCoordinator.swift → `views/story/StoryPlaybackCoordinator.kt` (estado/progreso/pausa/timer imagen + cache de stories; preload Coil y cableado vídeo pending)
- [~] StoryRepository.swift → `views/story/StoryRepository.kt` (StoryReplyData + lecturas + reactions/viewers/view transaction + soft/permanent delete/restore; decode backend es `BackendFeedService` existente)
- [~] StoryRingAvatarView.swift → `views/story/StoryRingAvatarView.kt`
- [~] StorySegmentedRing.swift → `views/story/StorySegmentedRing.kt` (anillo único/segmentado, gaps de 15º, estado visto, audiencias best friends/mutuals y tema)
**`story/StoryStickers`**
- [~] StoryStickerEffects.swift → `views/story/StoryStickers/StoryStickerEffects.kt` (clima animado, corazones flotantes y vídeo en loop; consumidores del viewer/sticker renderer pendientes)
- [~] StoryStickerViews.swift → `views/story/StoryStickers/StoryStickerViews.kt` (chunks 1–5: poll, slider, renderer, preguntas y tap-cycling de location/mention/hashtag; quiz/audio y tarjetas específicas pendientes)
**`story`**
- [~] StoryViewModel.swift → `views/story/StoryViewModel.kt` (carga + privacy + markSeen)
**`story/StoryViewer`**
- [~] StoryDeckInteractionLayout.swift → `views/story/storyviewer/StoryDeckInteractionLayout.kt` (zonas Compose; overlays específicos de slider pending)
- [~] StoryGestureCoordinator.swift → `views/story/storyviewer/StoryGestureCoordinator.kt` (contrato y arbitraje base; gate cableado al deck; regiones de otros stickers pending)
- [~] StoryLiveTextOverlayView.swift → `views/story/storyviewer/StoryLiveTextOverlayView.kt` (posición normalizada, estilo básico y replay de motion)
- [~] StoryMediaOverlayRendererView.swift → `views/story/storyviewer/StoryMediaOverlayRendererView.kt` (dibujo, texto live y stickers consolidados; Reveal queda en su overlay de scratch)
- [~] StoryQuickActionsMenu.swift → `views/story/storyviewer/StoryQuickActionsMenu.kt` (menú propio/ajeno y callbacks de acción)
- [~] StoryReplyViews.swift → `views/story/storyviewer/StoryReplyViews.kt` (chunks 1–2: previews/efímeras y burbuja con payload explícito; falta incorporar `storyReplyData` al contrato persistido de `EnhancedMessage` y gating backend)
- [~] StoryUserDeckPager.swift → `views/story/storyviewer/StoryUserDeckPager.kt` (pager deck-pass y gate; `StoriesView` aún usa el swipe simplificado hasta la integración fina)
- [~] StoryViewerBottomComponents.swift → `views/story/storyviewer/StoryViewerBottomComponents.kt` (barra propia, reacciones, notice y touch areas)
- [~] StoryViewerLayers.swift → `views/story/storyviewer/StoryViewerLayers.kt` (progress chrome, capa flotante y pool/generador de reacciones)
- [~] StoryViewerLayoutHelpers.swift → `views/story/storyviewer/StoryViewerLayoutHelpers.kt` (metadata, ratio, content rect y escala/posición de stickers)
- [~] StoryViewerMedia.swift → `views/story/storyviewer/StoryViewerMedia.kt` (sesión de audio, player, ready/progreso/final y loop)
- [~] StoryViewerOverlay.swift → `views/story/storyviewer/StoryViewerOverlay.kt` (progreso por audiencia, acciones/confirmación y actividad de viewers/reacciones/audiencia)
- [~] StoryViewerScreen.swift → `views/story/storyviewer/StoryViewerScreen.kt` (MVP gestos/timer)
**`story`**
- [~] archived stories.swift → `views/story/ArchivedStories.kt` (carga, grid/calendario/map fallback, visor por día y estadísticas; pulido visual/mapa nativo pendiente)

## Activities  (3/3)

- [~] LiveActivityThumbnailStore.swift → `activities/LiveActivityThumbnailStore.kt` (cache interno; N/A App Group iOS)
- [~] MomentUploadActivityAttributes.swift → `activities/MomentUploadActivityAttributes.kt` + `UploadProgressNotificationHelper.kt`
- [~] StoryUploadActivityAttributes.swift → `activities/StoryUploadActivityAttributes.kt` + `UploadProgressNotificationHelper.kt`

## Coordinators  (5/5)

- [~] AppRouter.swift → `coordinators/AppRouter.kt`
- [~] LegacyNavigationBridge.swift → `coordinators/LegacyNavigationBridge.kt`
- [~] MainViewModel.swift → `coordinators/MainViewModel.kt`
- [~] SharedComponents.swift → `coordinators/SharedComponents.kt`
- [~] TabBarView.swift → `coordinators/TabBarScreen.kt`

## Extensions  (5/5)

- [~] AVAssetImageGenerator+Thumbnail.swift → `extensions/AvAssetThumbnail.kt`
- [~] Color+Hex.swift → `extensions/ColorHex.kt`
- [~] Date+Extensions.swift → `extensions/DateExtensions.kt`
- [~] InterestEmojiHelper.swift → `extensions/InterestEmojiHelper.kt`
- [~] View+LiquidGlass.swift → `extensions/LiquidGlass.kt`

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

- [~] CommentsModerationService.swift → `moderation/CommentsModerationService.kt`
- [~] MediaModerationService.swift → `moderation/MediaModerationService.kt`

## MomentsApp.swift  (1/1)

- [~] MomentsApp.swift → `MomentsApp.kt` (raíz `com.moments.android`, como iOS)

## Notifications  (24/24)

- [~] NotificationGroupedFollowersOverlay.swift → `notifications/components/NotificationGroupedFollowersOverlay.kt`
- [~] NotificationRowComponents.swift → `notifications/components/NotificationRowComponents.kt`
- [~] NotificationSharedViews.swift → `notifications/components/NotificationSharedViews.kt`
- [~] NotificationGroup.swift → `notifications/core/NotificationGroup.kt`
- [~] NotificationRowSupport.swift → `notifications/core/NotificationRowSupport.kt`
- [~] NotificationsViewModel.swift → `notifications/core/NotificationsViewModel.kt`
- [~] EnhancedNotificationRow+Follow.swift → `notifications/row/EnhancedNotificationRowFollow.kt`
- [~] EnhancedNotificationRow+Messages.swift → `notifications/row/EnhancedNotificationRowMessages.kt`
- [~] EnhancedNotificationRow+Previews.swift → `notifications/row/EnhancedNotificationRowPreviews.kt`
- [~] EnhancedNotificationRow+Trailing.swift → `notifications/row/EnhancedNotificationRowTrailing.kt`
- [~] EnhancedNotificationRow.swift → `notifications/row/EnhancedNotificationRow.kt`
- [~] NotificationSummaryPopup.swift → `notifications/screens/NotificationSummaryPopup.kt`
- [~] NotificationsView.swift → `notifications/screens/NotificationsScreen.kt`
- [~] AppDelegate.swift (push) → `notifications/services/MomentsFirebaseMessagingService.kt` + `MainActivity` / `MomentsApplication`
- [~] FCMTokenService.swift → `notifications/services/FCMTokenService.kt`
- [~] InAppNotificationPreviewResolver.swift → `notifications/services/InAppNotificationPreviewResolver.kt`
- [~] InAppNotificationService.swift → `notifications/services/InAppNotificationService.kt`
- [~] NotificationBadgeService.swift → `notifications/services/NotificationBadgeService.kt`
- [~] NotificationCopyResolver.swift → `notifications/services/NotificationCopyResolver.kt`
- [~] NotificationExtensions.swift → `notifications/services/NotificationExtensions.kt`
- [~] NotificationNavigationService.swift → `notifications/services/NotificationNavigationService.kt`
- [~] NotificationOpenIntentStore.swift → `notifications/services/NotificationOpenIntentStore.kt`
- [~] NotificationPresentationCoordinator.swift → `notifications/services/NotificationPresentationCoordinator.kt`
- [~] Notificationservice.swift → `notifications/services/NotificationService.kt` (re-export deprecated en `services/notifications/`)

## Reportes  (7/7)

- [~] AppealFormView.swift → `reportes/AppealFormView.kt` (+ ModerationReviewRequestSheet)
- [~] AppealService.swift → `reportes/AppealService.kt` + `AppealModels.kt` + `AppealError.kt`
- [~] AppealStatus.swift → `reportes/AppealStatusView.kt`
- [~] ModerationReviewStatusView.swift → `reportes/ModerationReviewStatusView.kt`
- [~] ModernReportContent.swift → `reportes/ModernReportContent.kt`
- [~] ReportBottomSheet.swift → `reportes/ReportBottomSheet.kt` + `ReportModels.kt`
- [~] UserReportContent.swift → `reportes/UserReportContent.kt`

## Services  (69 named mappings; 0 parity-certified)

**`Activity`**
- [~] TimeSpentManager.swift → `services/activity/TimeSpentManager.kt`
**`Auth`**
- [~] AuthService.swift → `services/auth/AuthService.kt` (email+Google; Apple/Passkey N/A)
- [~] LoginActivityService.swift → `services/auth/LoginActivityService.kt`
- [~] OnboardingDraftStore.swift → `services/auth/OnboardingDraftStore.kt`
- [~] PasskeyService.swift — N/A (Android: solo Google + email)
**`Cache`**
- [~] CacheManager.swift → `services/cache/CacheManager.kt`
- [~] ImagePrefetchManager.swift → `services/cache/ImagePrefetchManager.kt`
- [~] PersistentAudioCache.swift → `services/cache/PersistentAudioCache.kt`
- [~] PersistentVideoCache.swift → `services/cache/PersistentVideoCache.kt`
- [~] UserCacheService.swift → `services/cache/UserCacheService.kt`
- [~] VideoPreloader.swift → `services/cache/VideoPreloader.kt`
- [~] VideoThumbnailCache.swift → `services/cache/VideoThumbnailCache.kt`
**`Camera`**
- [~] SnapCameraKitConfiguration.swift → `services/camera/SnapCameraKitConfiguration.kt` (feature off)
**`Content`**
- [~] BackendFeedService.swift → `services/content/BackendFeedService.kt`
- [~] FilterService.swift → `services/content/FilterService.kt`
- [~] ForYouDiscoveryService.swift → `services/content/ForYouDiscoveryService.kt`
- [~] ProfileVisitsService.swift → `services/content/ProfileVisitsService.kt`
**`Firestore`**
- [~] FirestoreActivityRepository.swift → `services/firestore/FirestoreActivityRepository.kt`
- [~] FirestoreAudienceRepository.swift → `services/firestore/FirestoreAudienceRepository.kt`
- [~] FirestoreCommentsRepository.swift → `services/firestore/FirestoreCommentsRepository.kt` (reply/mention notification and raw-mention handling differ from iOS)
- [~] FirestoreCore.swift → `services/firestore/FirestoreCore.kt`
- [~] FirestoreHiddenLayersRepository.swift → `services/firestore/FirestoreHiddenLayersRepository.kt`
- [~] FirestoreMomentsRepository.swift → `services/firestore/FirestoreMomentsRepository.kt` (corregido localmente el ID único de audiencia personalizada; falta validar en Firebase/emulador)
- [~] FirestoreProfilesRepository.swift → `services/firestore/FirestoreProfilesRepository.kt` (operaciones principales alineadas; falta recuperación de e-mail/Auth y decodificación legacy de iOS)
- [~] FirestoreSearchRepository.swift → `services/firestore/FirestoreSearchRepository.kt` (API consolidada; degradación de sugerencias alineada y compilada)
- [~] FirestoreService.swift → `services/firestore/FirestoreService.kt`
- [~] FirestoreStoriesRepository.swift → `services/firestore/FirestoreStoriesRepository.kt` (corregido localmente `mapLocation` y los valores de `mapVisibility`; falta validar escritura/lectura con Firebase)
**`Incognito`**
- [~] IncognitoModeService.swift → `services/incognito/IncognitoModeService.kt` (sin Live Activity/Widget iOS)
**`Messaging`**
- [~] ChatCacheStore.swift → `services/messaging/ChatCacheStore.kt`
- [~] ChatCommunicationNotificationService.swift → `services/messaging/ChatCommunicationNotificationService.kt` (shortcuts + Person; MessagingStyle en notificaciones)
- [~] ChatMediaChunkedCipher.swift → `services/messaging/ChatMediaChunkedCipher.kt`
- [~] ChatMediaDownloadPolicy.swift → `services/messaging/ChatMediaDownloadPolicy.kt`
- [~] ChatMediaPrefetcher.swift → `services/messaging/ChatMediaPrefetcher.kt`
- [~] ChatRecoveryCrypto.swift → `services/messaging/ChatRecoveryCrypto.kt`
- [~] ChatSendMessageIntentHandler.swift — N/A (Intents iOS; Android RemoteInput)
- [~] EncryptionService.swift → `services/messaging/EncryptionService.kt` (E2E identity/recovery correction is local; cross-device verification pending)
- [~] LocalFirstMessagingSettings.swift → `services/messaging/LocalFirstMessagingSettings.kt`
- [~] MessageCatchUpService.swift → `services/messaging/MessageCatchUpService.kt`
- [~] MessageIngestService.swift → `services/messaging/MessageIngestService.kt`
- [~] MessageRequestService.swift → `services/messaging/MessageRequestService.kt`
- [~] OnlineStatusService.swift → `services/messaging/OnlineStatusService.kt`
- [~] VanishMessageTimer.swift → `services/messaging/VanishMessageTimer.kt`
**`Network`**
- [~] NetworkMonitor.swift → `services/network/NetworkMonitor.kt`
- [~] OfflineSyncService.swift → `services/network/OfflineSyncService.kt`
**`Nova`**
- [~] NovaEmbeddingService.swift → `services/nova/NovaEmbeddingService.kt`
**`Performance`**
- [~] FeedVisibilityCoordinator.swift → `services/performance/FeedVisibilityCoordinator.kt`
- [~] MotionPolicy.swift → `services/performance/MotionPolicy.kt`
- [~] PerformanceSignposts.swift → `services/performance/PerformanceSignposts.kt`
- [~] VideoMomentsIndex.swift → `services/performance/VideoMomentsIndex.kt`
**`Persistence`**
- [~] LocalPersistenceService.swift → `services/persistence/LocalPersistenceService.kt` (JSON/filesDir; StorySeen en archivo aparte)
- [~] MessagePersistenceStore.swift → `services/persistence/MessagePersistenceStore.kt`
- [~] StorySeenStateService (en LocalPersistence iOS) → `services/persistence/StorySeenStateService.kt`
**`Privacy`**
- [~] ContentVisibilityservice.swift → `services/privacy/ContentVisibilityService.kt`
- [~] PrivacyService.swift → `services/privacy/PrivacyService.kt` + `services/privacy/PrivacyServiceExtension.kt` (port dividido: la extensión Kotlin contiene también las rutas avanzadas que Swift conserva en el archivo principal; auditoría de ramas/errores pendiente)
- [~] PrivacyServiceExtension.swift → `services/privacy/PrivacyServiceExtension.kt` (filtrado de contenido y `canViewMoment`)
**`Security`**
- [~] MomentsAppCheckProviderFactory.swift → `services/security/MomentsAppCheckProviderFactory.kt`
**`Social`**
- [~] AffinityTracker.swift → `services/social/AffinityTracker.kt`
- [~] BestFriendsService.swift → `services/social/BestFriendsService.kt`
- [~] EchoService.swift → `services/social/EchoService.kt`
- [~] StoryChainLimitsService.swift → `services/social/StoryChainLimitsService.kt`
- [~] StoryRingCacheService.swift → `services/social/StoryRingCacheService.kt`
**`Storage`**
- [~] MediaUploadService.swift → `services/storage/MediaUploadService.kt`
- [~] StoragePathBuilder.swift → `services/storage/StoragePathBuilder.kt`
- [~] StorageService.swift → `services/storage/StorageService.kt`
- [~] UIImage+StorageUpload.swift → `services/storage/BitmapStorageUpload.kt`
- [~] VideoCompressionService.swift → `services/storage/VideoCompressionService.kt`
**`Video`**
- [~] ReelPrebufferService.swift → `services/video/ReelPrebufferService.kt`
- [~] SharedVideoPlayerPool.swift → `services/video/SharedVideoPlayerPool.kt`
- [~] VideoAdaptivePlayback.swift → `services/video/VideoAdaptivePlayback.kt`
- [~] VideoPlaybackSelector.swift → `services/video/VideoPlaybackSelector.kt`

## Utilities  (11/11)

- [~] ActiveWindowMetrics.swift → `utilities/ActiveWindowMetrics.kt`
- [~] AppLog.swift → `utilities/AppLog.kt`
- [~] EmojiUsageTracker.swift → `utilities/EmojiUsageTracker.kt`
- [~] HapticManager.swift → `utilities/HapticManager.kt`
- [~] LegacyTypographyScale.swift → `utilities/LegacyTypographyScale.kt`
- [~] MentionParsing.swift → `utilities/MentionParsing.kt`
- [~] MomentsAppearModifiers.swift → `utilities/MomentsAppearModifiers.kt`
- [~] MomentsAudioSession.swift → `utilities/MomentsAudioSession.kt`
- [~] MomentsFormat.swift → `utilities/MomentsFormat.kt`
- [~] MomentsPressButtonStyle.swift → `utilities/MomentsPressButtonStyle.kt`
- [~] OrientationManager.swift → `utilities/OrientationManager.kt`


## ViewModels  (1/1)

- [~] EchoViewModel.swift → `viewmodels/EchoViewModel.kt` (StateFlow)

## ad  (4/4)

- [~] AdAspectRatioContext.swift → `ad/AdAspectRatioContext.kt`
- [~] AdMob Configuration.swift → `ad/AdMobConfiguration.kt` (+ NativeAdManager, PlusAdManager)
- [~] FeedNativeAd.swift → `ad/FeedNativeAd.kt` (Compose)
- [~] StoryNativeAd.swift → `ad/StoryNativeAd.kt` (Compose)
