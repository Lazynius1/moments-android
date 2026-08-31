package com.moments.android.views.feed.stories

import android.os.SystemClock
import android.media.AudioAttributes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.moments.android.R
import com.moments.android.extensions.MomentsChromeGlass
import com.moments.android.extensions.MomentsGlassStyle
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.MediaItem
import com.moments.android.models.Story
import com.moments.android.services.content.StoryTrayService
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.performance.MotionPolicy
import com.moments.android.services.privacy.PrivacyService
import com.moments.android.services.video.GlobalVideoManager
import com.moments.android.utilities.MomentsAudioSession
import com.moments.android.views.components.LiveUsernameContent
import com.moments.android.views.components.MomentRowButton
import com.moments.android.views.components.MomentRowButtonFeedback
import com.moments.android.views.creator.components.resolvedTextOverlays
import com.moments.android.views.feed.video.VideoPosterOverlay
import com.moments.android.views.story.StoryRepository
import com.moments.android.views.story.StoryRevealStickerOverlay
import com.moments.android.views.story.storystickers.StickerVideoPlayer
import com.moments.android.views.shared.ScreenshotProtectedView
import com.moments.android.views.shared.ScreenshotProtectionMode
import com.moments.android.views.story.storyviewer.GlassmorphicStoryConfirmationDialog
import com.moments.android.views.story.storyviewer.GlassmorphicSuccessMessage
import com.moments.android.views.story.storyviewer.StoryMediaOverlayRendererView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** ≡ iOS `FeedStoryRingPreviewSelection`. */
data class FeedStoryRingPreviewSelection(
    val userId: String,
    val anchorFrame: Rect,
)

private const val PhotoPreviewDurationMs = 4_000L
private const val VideoPreviewFraction = 0.40
private const val VideoPreviewMinimumDurationMs = 2_000L

/**
 * Port de `FeedStoryRingPreviewOverlay.swift`.
 * Preview 9:16 debajo del anillo, menú glass, avance automático, sin marcar vista.
 */
@Composable
fun FeedStoryRingPreviewOverlay(
    selection: FeedStoryRingPreviewSelection?,
    onSelectionChange: (FeedStoryRingPreviewSelection?) -> Unit,
    onOpenStory: (String, String?, Double) -> Unit,
    onOpenProfile: (String) -> Unit,
    onMuted: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val density = LocalDensity.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val soundEnabledInSession by GlobalVideoManager.userHasEnabledSoundInSession.collectAsState()
    val primaryTextColor = MomentsChromeGlass.contentColor(isDark)
    val previewShape = RoundedCornerShape(26.dp)
    val menuCardShape = RoundedCornerShape(26.dp)

    var isPresented by remember { mutableStateOf(false) }
    var dismissGeneration by remember { mutableIntStateOf(0) }
    var previewStories by remember { mutableStateOf<List<Story>>(emptyList()) }
    var previewIndex by remember { mutableIntStateOf(0) }
    var previewCycle by remember { mutableIntStateOf(0) }
    var previewStory by remember { mutableStateOf<Story?>(null) }
    var videoDurationMs by remember { mutableStateOf<Long?>(null) }
    var isPreviewVideoReady by remember { mutableStateOf(false) }
    var showMuteConfirmation by remember { mutableStateOf(false) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var resolvedUsername by remember { mutableStateOf("") }
    var overlayWindowBounds by remember { mutableStateOf(Rect.Zero) }
    var previewSegmentStartedAtMs by remember { mutableStateOf<Long?>(null) }
    var previewElapsedBeforePauseMs by remember { mutableLongStateOf(0L) }

    val reduceMotion = MotionPolicy.reduceMotion
    val presentedAlpha by animateFloatAsState(
        targetValue = if (isPresented) 1f else 0f,
        animationSpec = if (reduceMotion) tween(0) else spring(dampingRatio = 0.84f, stiffness = 380f),
        label = "storyRingPreviewAlpha",
    )
    val presentedScale by animateFloatAsState(
        targetValue = if (isPresented) 1f else 0.92f,
        animationSpec = if (reduceMotion) tween(0) else spring(dampingRatio = 0.84f, stiffness = 380f),
        label = "storyRingPreviewScale",
    )

    fun resetPreviewSegmentClock() {
        previewElapsedBeforePauseMs = 0L
        previewSegmentStartedAtMs = null
    }

    fun markPreviewSegmentStart() {
        previewElapsedBeforePauseMs = 0L
        previewSegmentStartedAtMs = SystemClock.elapsedRealtime()
    }

    fun pausePreviewSegmentClock() {
        var elapsed = previewElapsedBeforePauseMs
        previewSegmentStartedAtMs?.let { elapsed += SystemClock.elapsedRealtime() - it }
        previewElapsedBeforePauseMs = elapsed.coerceAtLeast(0L)
        previewSegmentStartedAtMs = null
    }

    fun resumePreviewSegmentClock() {
        if (previewSegmentStartedAtMs != null) return
        previewSegmentStartedAtMs = SystemClock.elapsedRealtime()
    }

    fun currentPreviewElapsedSeconds(): Double {
        var elapsed = previewElapsedBeforePauseMs
        previewSegmentStartedAtMs?.let { elapsed += SystemClock.elapsedRealtime() - it }
        return elapsed.coerceAtLeast(0L) / 1000.0
    }

    fun beginPreviewSegmentClockIfNeeded() {
        if (previewStory?.mediaItem?.type == MediaItem.MediaType.VIDEO) {
            resetPreviewSegmentClock()
        } else {
            markPreviewSegmentStart()
        }
    }

    fun resetPreviewPlayback() {
        previewStories = emptyList()
        previewIndex = 0
        previewCycle = 0
        previewStory = null
        videoDurationMs = null
        isPreviewVideoReady = false
        resetPreviewSegmentClock()
    }

    fun applyPreviewStories(stories: List<Story>) {
        val newIds = stories.mapNotNull { it.id }
        val oldIds = previewStories.mapNotNull { it.id }
        if (newIds == oldIds && previewStory != null) return
        previewStories = stories
        if (previewStories.isEmpty()) {
            previewStory = null
            resetPreviewSegmentClock()
            return
        }
        previewIndex = min(previewIndex, previewStories.lastIndex)
        previewStory = previewStories[previewIndex]
        videoDurationMs = null
        isPreviewVideoReady = false
        beginPreviewSegmentClockIfNeeded()
    }

    fun advancePreview() {
        if (previewStories.isEmpty()) return
        previewCycle += 1
        previewIndex = (previewIndex + 1) % previewStories.size
        previewStory = previewStories[previewIndex]
        videoDurationMs = null
        isPreviewVideoReady = false
        beginPreviewSegmentClockIfNeeded()
    }

    suspend fun filterVisibleStories(stories: List<Story>, viewerId: String): List<Story> {
        if (stories.isEmpty()) return emptyList()
        return stories.filter { story ->
            val id = story.id ?: return@filter false
            id.isNotEmpty() && PrivacyService.canUserViewStoryEnhanced(story, viewerId)
        }.sortedBy { it.timestamp }
    }

    suspend fun loadPreview(userId: String) {
        val viewerId = FirebaseAuth.getInstance().currentUser?.uid
        if (viewerId == null) {
            applyPreviewStories(emptyList())
            return
        }
        val bundle = StoryTrayService.fetchAuthorStoryBundle(userId)
        val fromBundle = bundle?.stories?.mapNotNull { StoryRepository.decodeBackendStory(it) }.orEmpty()
        if (fromBundle.isNotEmpty()) {
            applyPreviewStories(fromBundle)
            return
        }
        val fetched = runCatching { StoryRepository().fetchActiveStories(userId) }.getOrDefault(emptyList())
        applyPreviewStories(filterVisibleStories(fetched, viewerId))
    }

    fun dismissOverlay(then: (() -> Unit)? = null) {
        dismissGeneration += 1
        val generation = dismissGeneration
        showMuteConfirmation = false
        isPresented = false
        val delayMs = if (reduceMotion) 0L else 260L
        scope.launch {
            delay(delayMs)
            if (generation != dismissGeneration) return@launch
            onSelectionChange(null)
            then?.invoke()
        }
    }

    fun preparePreviewAudioIfNeeded() {
        if (!soundEnabledInSession) return
        MomentsAudioSession.initialize(context)
        scope.launch {
            MomentsAudioSession.activate(
                usage = AudioAttributes.USAGE_MEDIA,
                contentType = AudioAttributes.CONTENT_TYPE_MOVIE,
            )
        }
    }

    LaunchedEffect(selection?.userId) {
        val userId = selection?.userId
        if (userId == null) {
            GlobalVideoManager.endPlaybackHold("feed-story-preview")
            isPresented = false
            resetPreviewPlayback()
            showMuteConfirmation = false
            successMessage = null
            return@LaunchedEffect
        }
        GlobalVideoManager.beginPlaybackHold("feed-story-preview")
        dismissGeneration += 1
        isPresented = false
        showMuteConfirmation = false
        successMessage = null
        resetPreviewPlayback()
        loadPreview(userId)
        preparePreviewAudioIfNeeded()
        isPresented = true
    }

    LaunchedEffect(
        previewStory?.id,
        previewCycle,
        isPresented,
        showMuteConfirmation,
        selection?.userId,
    ) {
        if (selection == null || !isPresented || showMuteConfirmation) return@LaunchedEffect
        val story = previewStory ?: return@LaunchedEffect
        if (previewStories.isEmpty()) return@LaunchedEffect
        val duration = if (story.mediaItem.type == MediaItem.MediaType.VIDEO) {
            val videoMs = videoDurationMs ?: snapshotFlow { videoDurationMs }.filterNotNull().first()
            max((videoMs * VideoPreviewFraction).toLong(), VideoPreviewMinimumDurationMs)
        } else {
            PhotoPreviewDurationMs
        }
        delay(duration)
        advancePreview()
    }

    LaunchedEffect(showMuteConfirmation) {
        if (showMuteConfirmation) {
            pausePreviewSegmentClock()
        } else if (selection != null && isPresented && previewElapsedBeforePauseMs > 0L) {
            resumePreviewSegmentClock()
        }
    }

    LaunchedEffect(soundEnabledInSession, previewStory?.mediaItem?.type) {
        if (soundEnabledInSession && previewStory?.mediaItem?.type == MediaItem.MediaType.VIDEO) {
            preparePreviewAudioIfNeeded()
        }
    }

    if (selection == null) return

    val layout = remember(selection, overlayWindowBounds, density) {
        previewLayout(
            selection = selection,
            overlayGlobal = overlayWindowBounds,
            density = density,
        )
    }

    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { overlayWindowBounds = it.boundsInWindow() },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.28f * presentedAlpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { dismissOverlay() },
                ),
        )

        Column(
            modifier = Modifier
                .width(with(density) { layout.previewWidth.toDp() })
                .offset { IntOffset(layout.originX.roundToInt(), layout.originY.roundToInt()) }
                .graphicsLayer {
                    alpha = presentedAlpha
                    scaleX = presentedScale
                    scaleY = presentedScale
                    transformOrigin = TransformOrigin(0.5f, 0f)
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PreviewCard(
                sizePx = layout.previewWidth to layout.previewHeight,
                previewStory = previewStory,
                previewCycle = previewCycle,
                soundEnabledInSession = soundEnabledInSession,
                previewShape = previewShape,
                isPresented = isPresented,
                isDark = isDark,
                onOpen = {
                    val storyId = previewStory?.id
                    val elapsed = currentPreviewElapsedSeconds()
                    dismissOverlay { onOpenStory(selection.userId, storyId, elapsed) }
                },
                onVideoAppear = { preparePreviewAudioIfNeeded() },
                isPreviewVideoReady = isPreviewVideoReady,
                onVideoDurationMs = {
                    isPreviewVideoReady = true
                    videoDurationMs = it
                    markPreviewSegmentStart()
                },
            )
            Spacer(Modifier.height(10.dp))
            ActionsMenu(
                widthPx = layout.previewWidth,
                shape = menuCardShape,
                primaryTextColor = primaryTextColor,
                isDark = isDark,
                onViewProfile = { dismissOverlay { onOpenProfile(selection.userId) } },
                onMute = { showMuteConfirmation = true },
            )
        }

        LiveUsernameContent(userId = selection.userId, fallbackUsername = "") { username ->
            LaunchedEffect(username) { resolvedUsername = username }
        }

        if (showMuteConfirmation) {
            val muteTitle = stringResource(
                R.string.story_context_menu_mute_confirm_title,
                resolvedUsername,
            )
            val muteSuccess = stringResource(R.string.story_context_menu_mute_success_with_hint)
            val muteFailed = stringResource(R.string.story_context_menu_action_failed)
            val mutedUserId = selection.userId
            GlassmorphicStoryConfirmationDialog(
                title = muteTitle,
                message = stringResource(R.string.story_context_menu_mute_confirm_message),
                confirmTitle = stringResource(R.string.story_context_menu_mute_confirm_action),
                cancelTitle = stringResource(R.string.story_context_menu_mute_confirm_cancel),
                isDestructive = true,
                onConfirm = {
                    showMuteConfirmation = false
                    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return@GlassmorphicStoryConfirmationDialog
                    if (currentUserId == mutedUserId) return@GlassmorphicStoryConfirmationDialog
                    scope.launch {
                        runCatching {
                            FirestoreService().db.collection("users").document(currentUserId)
                                .update("muteSettings.mutedUsers", FieldValue.arrayUnion(mutedUserId))
                                .await()
                        }.onSuccess {
                            onMuted(mutedUserId)
                            successMessage = muteSuccess
                            delay(900)
                            dismissOverlay()
                        }.onFailure { error ->
                            successMessage = error.message?.takeIf { it.isNotEmpty() } ?: muteFailed
                        }
                    }
                },
                onCancel = { showMuteConfirmation = false },
            )
        }

        successMessage?.let { message ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(top = 12.dp)
                    .zIndex(30f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                GlassmorphicSuccessMessage(text = message)
            }
        }
    }
}

@Composable
private fun PreviewCard(
    sizePx: Pair<Float, Float>,
    previewStory: Story?,
    previewCycle: Int,
    soundEnabledInSession: Boolean,
    previewShape: RoundedCornerShape,
    isPresented: Boolean,
    isDark: Boolean,
    isPreviewVideoReady: Boolean,
    onOpen: () -> Unit,
    onVideoAppear: () -> Unit,
    onVideoDurationMs: (Long) -> Unit,
) {
    val density = LocalDensity.current
    val width = with(density) { sizePx.first.toDp() }
    val height = with(density) { sizePx.second.toDp() }
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .shadow(
                elevation = if (isPresented) 28.dp else 0.dp,
                shape = previewShape,
                ambientColor = Color.Black.copy(alpha = if (isDark) 0.28f else 0.16f),
                spotColor = Color.Black.copy(alpha = if (isDark) 0.28f else 0.16f),
            )
            .clip(previewShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onOpen,
            ),
        contentAlignment = Alignment.Center,
    ) {
        ScreenshotProtectedView(
            isProtected = (previewStory?.audience?.lowercase() ?: "") != "everyone",
            fillsContainer = true,
            cornerRadius = 26.dp,
            // ≡ StoryViewerScreen: vídeo en preview → FLAG_SECURE, no ContentSurface.
            mode = ScreenshotProtectionMode.WindowFlag,
        ) {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                if (previewStory == null) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                }
                val media = previewStory?.mediaItem
                if (media?.type == MediaItem.MediaType.IMAGE && media.url.isNotBlank()) {
                    AsyncImage(
                        model = media.url,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .width(width)
                            .height(height),
                    )
                }
                if (media?.type == MediaItem.MediaType.VIDEO) {
                    VideoPosterOverlay(
                        posterUrl = media.thumbnailUrl,
                        isReadyToPlay = isPreviewVideoReady,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .width(width)
                            .height(height),
                    )
                    val videoUrl = media.url
                    if (videoUrl.isNotBlank()) {
                        LaunchedEffect(previewStory?.id, previewCycle) {
                            onVideoAppear()
                        }
                        key("${previewStory?.id ?: "empty"}-$previewCycle") {
                            StickerVideoPlayer(
                                url = videoUrl,
                                isMuted = !soundEnabledInSession,
                                onDurationMs = onVideoDurationMs,
                                modifier = Modifier
                                    .width(width)
                                    .height(height),
                            )
                        }
                    }
                }
                previewStory?.let { story ->
                    val stickers = remember(story.id, story.stickers) {
                        story.stickers.orEmpty().filter { it.moderationState != "hidden" }
                    }
                    StoryMediaOverlayRendererView(
                        textOverlays = story.resolvedTextOverlays,
                        stickers = stickers,
                        drawingData = null,
                        storyId = story.id.orEmpty(),
                        userId = story.authorId,
                        reportsDeckInteractionExclusion = false,
                        allowsStickerHitTesting = false,
                        modifier = Modifier.matchParentSize(),
                    )
                    StoryRevealStickerOverlay(
                        storyId = story.id.orEmpty(),
                        stickers = stickers,
                        reportsDeckInteractionExclusion = false,
                        onPauseStory = {},
                        onResumeStory = {},
                        modifier = Modifier.matchParentSize(),
                    )
                }
            }
        }
        // iOS StickerVideoPlayer.allowsHitTesting(false): el tap abre el visor, no el player.
        Box(
            Modifier
                .matchParentSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpen,
                ),
        )
    }
}

@Composable
private fun ActionsMenu(
    widthPx: Float,
    shape: RoundedCornerShape,
    primaryTextColor: Color,
    isDark: Boolean,
    onViewProfile: () -> Unit,
    onMute: () -> Unit,
) {
    val density = LocalDensity.current
    Column(
        modifier = Modifier
            .width(with(density) { widthPx.toDp() })
            .padding(vertical = 6.dp)
            .momentsChromeGlass(shape, interactive = true, style = MomentsGlassStyle.NATIVE_TINTED)
            .clip(shape)
            .shadow(
                elevation = 24.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = if (isDark) 0.24f else 0.12f),
                spotColor = Color.Black.copy(alpha = if (isDark) 0.24f else 0.12f),
            ),
    ) {
        MenuRow(
            title = stringResource(R.string.user_activity_event_action_view_profile),
            icon = { Icon(Icons.Outlined.AccountCircle, contentDescription = null, modifier = Modifier.size(24.dp)) },
            isDestructive = false,
            primaryTextColor = primaryTextColor,
            onClick = onViewProfile,
        )
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 14.dp),
            color = primaryTextColor.copy(alpha = 0.35f),
        )
        MenuRow(
            title = stringResource(R.string.story_context_menu_mute),
            icon = { Icon(Icons.Filled.NotificationsOff, contentDescription = null, modifier = Modifier.size(24.dp)) },
            isDestructive = true,
            primaryTextColor = primaryTextColor,
            onClick = onMute,
        )
    }
}

@Composable
private fun MenuRow(
    title: String,
    icon: @Composable () -> Unit,
    isDestructive: Boolean,
    primaryTextColor: Color,
    onClick: () -> Unit,
) {
    MomentRowButton(
        action = onClick,
        feedback = MomentRowButtonFeedback.MENU,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { icon() }
            Spacer(Modifier.width(12.dp))
            Text(
                title,
                fontSize = 17.sp,
                color = if (isDestructive) Color.Red else primaryTextColor,
            )
            Spacer(Modifier.weight(1f))
        }
    }
}

private data class PreviewLayout(
    val previewWidth: Float,
    val previewHeight: Float,
    val originX: Float,
    val originY: Float,
)

private fun previewLayout(
    selection: FeedStoryRingPreviewSelection,
    overlayGlobal: Rect,
    density: androidx.compose.ui.unit.Density,
): PreviewLayout {
    val previewWidthDp = 196.dp
    val menuRowHeight = 44.dp
    val stackGap = 10.dp
    val horizontalInset = 16.dp
    val ringGap = 10.dp
    val previewWidthPx = with(density) { previewWidthDp.toPx() }
    val menuHeight = with(density) { menuRowHeight.toPx() } * 2f + with(density) { 12.dp.toPx() }
    val stackGapPx = with(density) { stackGap.toPx() }
    val insetPx = with(density) { horizontalInset.toPx() }
    val ringGapPx = with(density) { ringGap.toPx() }
    val overlayWidth = overlayGlobal.width.takeIf { it > 1f } ?: previewWidthPx
    val overlayHeight = overlayGlobal.height.takeIf { it > 1f } ?: previewWidthPx * 16f / 9f
    val anchor = Rect(
        selection.anchorFrame.left - overlayGlobal.left,
        selection.anchorFrame.top - overlayGlobal.top,
        selection.anchorFrame.right - overlayGlobal.left,
        selection.anchorFrame.bottom - overlayGlobal.top,
    )
    val previewTop = max(anchor.bottom + ringGapPx, with(density) { 8.dp.toPx() })
    val maxHeight = max(
        with(density) { 220.dp.toPx() },
        overlayHeight - previewTop - stackGapPx - menuHeight - with(density) { 16.dp.toPx() },
    )
    val availableWidth = max(with(density) { 160.dp.toPx() }, overlayWidth - insetPx * 2f)
    var width = min(previewWidthPx, availableWidth)
    var height = width * 16f / 9f
    if (height > maxHeight) {
        height = maxHeight
        width = height * 9f / 16f
    }
    val minX = insetPx
    val maxX = max(minX, overlayWidth - insetPx - width)
    val originX = min(max(anchor.center.x - width / 2f, minX), maxX)
    return PreviewLayout(width, height, originX, previewTop)
}
