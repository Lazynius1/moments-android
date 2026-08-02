package com.moments.android.views.story.storyviewer

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.moments.android.R
import com.moments.android.coordinators.CoordinatorNavigationEvent
import com.moments.android.coordinators.NavigationEventBus
import com.moments.android.extensions.MomentsChromeGlass
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.MediaItem
import com.moments.android.models.StickerData
import com.moments.android.models.Story
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.persistence.StorySeenStateService
import com.moments.android.services.social.BestFriendsService
import com.moments.android.utilities.EmojiReactionDefaults
import com.moments.android.utilities.EmojiUsageTracker
import com.moments.android.utilities.MomentsAudioSession
import com.moments.android.utilities.MomentsFormat
import com.moments.android.views.creator.EmojiPickerView
import com.moments.android.views.creator.components.resolvedTextOverlays
import com.moments.android.views.creator.creatoruikit.storyViewerCaptureRect
import com.moments.android.views.creator.creatoruikit.storyViewerCanvasCornerRadius
import com.moments.android.views.feed.core.FeedProfileSheetRoute
import com.moments.android.views.feed.core.sections.FeedMomentDetailRoute
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.feed.sharing.StoryShareBottomSheet
import com.moments.android.views.messaging.components.AttachmentIcon
import com.moments.android.views.messaging.components.AttachmentIconPreset
import com.moments.android.views.messaging.components.AttachmentIconView
import com.moments.android.views.messaging.components.messageTextColor
import com.moments.android.views.messaging.components.replyBarSecondaryText
import com.moments.android.views.permission.shared.PermissionPrimerGate
import com.moments.android.views.permission.shared.PermissionPrimerGateHost
import com.moments.android.views.profile.core.sections.UserProfileZoomNavigationHost
import com.moments.android.views.profile.core.sections.userProfileZoomSource
import com.moments.android.views.profile.highlights.HighlightViewerTitlePill
import com.moments.android.views.shared.MomentsModalSheet
import com.moments.android.views.shared.ScreenshotProtectedView
import com.moments.android.views.shared.ScreenshotProtectionMode
import com.moments.android.views.story.CurrentUserVerifiedBadge
import com.moments.android.views.story.LocalStoryDeckGestureGate
import com.moments.android.views.story.StoryChainView
import com.moments.android.views.story.StoryDeckGestureGate
import com.moments.android.views.story.StoryPlaybackCoordinator
import com.moments.android.views.story.StoryReaction
import com.moments.android.views.story.StoryRepository
import com.moments.android.views.story.StoryRevealStickerOverlay
import com.moments.android.views.story.StoryViewModel
import com.moments.android.views.story.StoryViewer
import com.moments.android.views.story.VerifiedBadgeView
import com.moments.android.views.story.storystickers.FloatingHeart
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.net.URL

/** ≡ `StoryConfirmationKind` en StoryViewerScreen.swift. */
private enum class StoryConfirmationKind {
    DELETE, UNFOLLOW, MUTE, LEAVE_BEST_FRIENDS,
}

/**
 * Port de `Views/story/StoryViewer/StoryViewerScreen.swift` (por MARKs).
 * Trozos 1–7: firma/estado/helpers, geometry, reveal/bottom, overlays, quick actions, gestos, actions.
 */
@Composable
fun StoryViewerScreen(
    story: Story,
    segmentCount: Int,
    segmentIndex: Int,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onDismiss: () -> Unit,
    onProfileTap: () -> Unit = {},
    onReportStory: () -> Unit = {},
    onBlockUser: () -> Unit = {},
    showingReportSheet: Boolean = false,
    showingBlockConfirmation: Boolean = false,
    onStoryDeleted: (() -> Unit)? = null,
    onViewActivity: () -> Unit = {},
    onSaveStory: () -> Unit = {},
    onUnfollowAuthor: () -> Unit = {},
    onMuteAuthor: () -> Unit = {},
    onLeaveBestFriends: () -> Unit = {},
    onSendMessage: (String) -> Unit = {},
    onOpenChainStory: (List<Story>, Int) -> Unit = { _, _ -> },
    onContinueChain: (String, String, Int) -> Unit = { _, _, _ -> },
    storyViewModel: StoryViewModel? = null,
    viewers: List<StoryViewer> = emptyList(),
    reactions: List<StoryReaction> = emptyList(),
    onHoldChanged: (Boolean) -> Unit = {},
    gestureGate: StoryDeckGestureGate? = null,
    isDeckPageActive: Boolean = true,
    highlightTitle: String? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val isDark = isSystemInDarkTheme()
    val adaptive = rememberAdaptiveColors()
    val scope = rememberCoroutineScope()
    val storyRepository = remember { StoryRepository() }
    val bestFriendsService = remember { BestFriendsService() }
    val firestore = remember { FirestoreService() }
    val emojiUsageTracker = remember { EmojiUsageTracker() }
    val gestureCoordinator = remember { StoryGestureCoordinator() }
    val envGate = LocalStoryDeckGestureGate.current
    val deckGestureGate = gestureGate ?: envGate

    val playbackCoordinator = remember(context.applicationContext) {
        StoryPlaybackCoordinator(context.applicationContext)
    }

    // MARK: - State (espejo @State iOS)
    var messageText by remember { mutableStateOf("") }
    var showReactions by remember { mutableStateOf(false) }
    var showQuickActions by remember { mutableStateOf(false) }
    var showActivity by remember { mutableStateOf(false) }
    var activityTab by remember { mutableIntStateOf(0) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showUnfollowConfirmation by remember { mutableStateOf(false) }
    var showMuteConfirmation by remember { mutableStateOf(false) }
    var showBestFriendsOptOutConfirmation by remember { mutableStateOf(false) }
    var showChain by remember { mutableStateOf(false) }
    var showChainActions by remember { mutableStateOf(false) }
    var canContinueChain by remember { mutableStateOf(false) }
    var isUIHidden by remember { mutableStateOf(false) }
    var isHoldingStory by remember { mutableStateOf(false) }
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var lastZoomScale by remember { mutableFloatStateOf(1f) }
    var authorAllowsMessages by remember { mutableStateOf(true) }
    var authorAllowsReactions by remember { mutableStateOf(true) }
    var authorAllowsEphemeralPhotos by remember { mutableStateOf(true) }
    var isVanishActiveWithAuthor by remember { mutableStateOf(false) }
    var floatingHearts by remember { mutableStateOf<List<FloatingHeart>>(emptyList()) }
    var successMessageText by remember { mutableStateOf<String?>(null) }
    var suppressNavigationTapUntil by remember { mutableLongStateOf(0L) }
    var lastPreparedStoryId by remember { mutableStateOf<String?>(null) }
    var chainStories by remember { mutableStateOf<List<Story>>(emptyList()) }
    var menuAutoResumeJob by remember { mutableStateOf<Job?>(null) }
    var holdPauseJob by remember { mutableStateOf<Job?>(null) }
    var showStoryShareSheet by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var isMenuInteractionActive by remember { mutableStateOf(false) }
    var showStoryReactionEmojiPicker by remember { mutableStateOf(false) }
    var showMomentDetail by remember { mutableStateOf(false) }
    var targetMomentId by remember { mutableStateOf<String?>(null) }
    var targetMomentUserId by remember { mutableStateOf<String?>(null) }
    var profileRoute by remember { mutableStateOf<FeedProfileSheetRoute?>(null) }
    var storyStickers by remember { mutableStateOf<List<StickerData>>(emptyList()) }
    var storyStickerCache by remember { mutableStateOf<Map<String, List<StickerData>>>(emptyMap()) }
    var currentChainIndex by remember { mutableIntStateOf(0) }
    var gestureActionTriggered by remember { mutableStateOf(false) }
    var isTextFieldFocused by remember { mutableStateOf(false) }
    var showEphemeralPicker by remember { mutableStateOf(false) }
    var smileyButtonCenterX by remember { mutableFloatStateOf(0f) }
    var smileyButtonCenterY by remember { mutableFloatStateOf(0f) }
    var isStoryVideoReady by remember { mutableStateOf(false) }
    var textMotionReplayToken by remember { mutableIntStateOf(0) }
    var viewerScreenWidth by remember { mutableFloatStateOf(0f) }
    var viewerScreenHeight by remember { mutableFloatStateOf(0f) }
    val photosSaveGate = remember { PermissionPrimerGate(PermissionPrimerGate.Kind.PHOTOS_SAVE) }

    val onNextState = rememberUpdatedState(onNext)
    val onPreviousState = rememberUpdatedState(onPrevious)
    val storyCount = segmentCount
    val storyIndex = segmentIndex
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val isKeyboardVisible = imeBottomPx > 0

    // MARK: - Helpers (Trozo 1)
    val isOwnStory = story.authorId == FirebaseAuth.getInstance().currentUser?.uid
    // ≡ iOS `(audience ?? "") != "everyone"` → vacío/null = protegido (no everyone)
    val isEveryoneStoryAudience =
        story.audience?.trim()?.lowercase().orEmpty() == "everyone"
    val canOptOutFromAuthorBestFriends = !isOwnStory && run {
        val normalized = story.audience?.trim()?.lowercase()
            ?.replace("_", "")?.replace("-", "").orEmpty()
        normalized == "bestfriends"
    }
    val reactionEmojis = remember(emojiUsageTracker) {
        emojiUsageTracker.orderedEmojis(EmojiReactionDefaults.story)
    }
    val quickActionTextColor = if (isDark) Color.White else Color.Black.copy(0.88f)
    val quickActionDividerColor = if (isDark) Color.White.copy(0.12f) else Color.Black.copy(0.10f)
    val vanishReplyStrokeColor = if (isDark) Color.White.copy(0.55f) else Color.Black.copy(0.28f)

    val currentStoryViewers = story.id?.let { id ->
        storyViewModel?.storyViewers?.get(id) ?: viewers
    }.orEmpty()
    val currentStoryReactions = story.id?.let { id ->
        storyViewModel?.storyReactions?.get(id) ?: reactions
    }.orEmpty()

    val activeStoryConfirmation: StoryConfirmationKind? = when {
        showDeleteConfirmation -> StoryConfirmationKind.DELETE
        showUnfollowConfirmation -> StoryConfirmationKind.UNFOLLOW
        showMuteConfirmation -> StoryConfirmationKind.MUTE
        showBestFriendsOptOutConfirmation -> StoryConfirmationKind.LEAVE_BEST_FRIENDS
        else -> null
    }

    val isStoryInteractionBlocked =
        isMenuInteractionActive ||
            showQuickActions ||
            showActivity ||
            showChain ||
            showReactions ||
            showStoryShareSheet ||
            showStoryReactionEmojiPicker ||
            showMomentDetail ||
            showEphemeralPicker ||
            profileRoute != null ||
            showingReportSheet ||
            showingBlockConfirmation ||
            activeStoryConfirmation != null

    fun stickerCacheKey(forStory: Story): String {
        val id = forStory.id
        return if (!id.isNullOrEmpty()) id else "${forStory.authorId}_${forStory.timestamp.time}"
    }

    /** ≡ `resolvedStoryStickers(for:)` — Android ya tiene [StickerData]; cachea por historia. */
    fun resolvedStoryStickers(forStory: Story): List<StickerData> {
        val key = stickerCacheKey(forStory)
        storyStickerCache[key]?.let { return it }
        val stickers = forStory.stickers.orEmpty()
        storyStickerCache = storyStickerCache + (key to stickers)
        return stickers
    }

    fun isUnrevealedRevealActive(): Boolean {
        val storyId = story.id.orEmpty()
        if (storyId.isEmpty()) return false
        val hasReveal = storyStickers.any { it.type == "reveal" }
        if (!hasReveal) return false
        val prefs = context.getSharedPreferences("moments_story_stickers", Context.MODE_PRIVATE)
        return !prefs.getBoolean("reveal_revealed_$storyId", false)
    }

    val shouldMuteVideoForReveal =
        story.mediaItem.type == MediaItem.MediaType.VIDEO && isUnrevealedRevealActive()

    fun clearAllStoryConfirmations() {
        showDeleteConfirmation = false
        showUnfollowConfirmation = false
        showMuteConfirmation = false
        showBestFriendsOptOutConfirmation = false
    }

    /** ≡ `pauseStory()` — no toca `isHoldingStory` (hold es path aparte). */
    fun pauseStoryPlayback() {
        playbackCoordinator.pauseStory()
    }

    /** ≡ `resumeStory()` — no limpia hold (eso lo hace el gesto). */
    fun resumeStoryPlayback() {
        isUIHidden = false
        val overlaysVisible =
            showQuickActions ||
                showActivity ||
                showChain ||
                showReactions ||
                showStoryShareSheet ||
                showStoryReactionEmojiPicker ||
                showMomentDetail ||
                showEphemeralPicker ||
                showDeleteConfirmation ||
                showUnfollowConfirmation ||
                showMuteConfirmation ||
                showBestFriendsOptOutConfirmation ||
                showingReportSheet ||
                showingBlockConfirmation ||
                profileRoute != null ||
                isTextFieldFocused
        val canResume =
            !isKeyboardVisible &&
                !isDragging &&
                !isHoldingStory &&
                !isMenuInteractionActive &&
                !overlaysVisible &&
                isDeckPageActive
        playbackCoordinator.resumeStory(story, canResume = canResume) { onNextState.value() }
    }

    fun beginHoldPause() {
        isHoldingStory = true
        onHoldChanged(true)
        pauseStoryPlayback()
        isUIHidden = true
    }

    fun endHoldPause() {
        if (!isHoldingStory) {
            holdPauseJob?.cancel()
            holdPauseJob = null
            return
        }
        isHoldingStory = false
        onHoldChanged(false)
        suppressNavigationTapUntil = System.currentTimeMillis() + 250
        isUIHidden = false
        resumeStoryPlayback()
    }

    fun cancelPendingHoldPause() {
        holdPauseJob?.cancel()
        holdPauseJob = null
    }

    fun pauseOrResumeForOverlay(isPresented: Boolean) {
        if (isPresented) {
            pauseStoryPlayback()
        } else {
            scope.launch {
                delay(300)
                resumeStoryPlayback()
            }
        }
    }

    fun cancelMenuAutoResume() {
        menuAutoResumeJob?.cancel()
        menuAutoResumeJob = null
        isMenuInteractionActive = false
    }

    fun pauseForMenuInteraction() {
        pauseStoryPlayback()
        isMenuInteractionActive = true
        menuAutoResumeJob?.cancel()
        menuAutoResumeJob = scope.launch {
            delay(8_000)
            val stillBlocked = showQuickActions || showActivity || showChain ||
                showReactions || showStoryShareSheet || showEphemeralPicker ||
                showingReportSheet || showingBlockConfirmation || profileRoute != null ||
                showDeleteConfirmation || showUnfollowConfirmation ||
                showMuteConfirmation || showBestFriendsOptOutConfirmation
            if (stillBlocked) return@launch
            isMenuInteractionActive = false
            resumeStoryPlayback()
        }
    }

    fun dismissQuickActions(resume: Boolean = true) {
        showQuickActions = false
        cancelMenuAutoResume()
        if (resume) {
            scope.launch {
                delay(120)
                resumeStoryPlayback()
            }
        }
    }

    fun toggleQuickActions() {
        if (showQuickActions) {
            dismissQuickActions()
        } else {
            pauseForMenuInteraction()
            showQuickActions = true
        }
    }

    fun showSuccess(text: String) {
        successMessageText = text
        scope.launch {
            delay(2_000)
            if (successMessageText == text) successMessageText = null
        }
    }

    fun markStoryAsViewedIfNeeded() {
        // ≡ iOS: no excluye own story en el guard del viewer
        if (!isDeckPageActive) return
        val storyId = story.id ?: return
        storyViewModel?.markStoryAsViewed(
            userId = story.authorId,
            storyId = storyId,
            storyTimestamp = story.timestamp,
            audience = story.audience,
        ) ?: run {
            if (isOwnStory) return
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            StorySeenStateService.markSeen(uid, story.authorId, story.timestamp, syncRemote = true)
        }
    }

    fun deleteStoryAction() {
        val storyId = story.id ?: return
        pauseStoryPlayback()
        showQuickActions = false
        // ≡ deleteStory() iOS: success → toast → 1.8s → onStoryDeleted ?? onNext
        fun onDeletedOk() {
            showSuccess(context.getString(R.string.story_context_menu_delete_success))
            scope.launch {
                delay(1_800)
                if (onStoryDeleted != null) onStoryDeleted.invoke() else onNextState.value()
            }
        }
        fun onDeletedFail(message: String? = null) {
            showSuccess(message ?: context.getString(R.string.story_context_menu_action_failed))
            scope.launch {
                delay(100)
                resumeStoryPlayback()
            }
        }
        storyViewModel?.deleteStory(story.authorId, storyId) { err ->
            if (err == null) onDeletedOk() else onDeletedFail(err.message?.takeIf { it.isNotBlank() })
        } ?: scope.launch {
            runCatching { storyRepository.softDeleteStory(story.authorId, storyId) }
                .onSuccess { onDeletedOk() }
                .onFailure { onDeletedFail(it.message) }
        }
    }

    fun unfollowStoryAuthor() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        pauseStoryPlayback()
        scope.launch {
            runCatching { firestore.unfollowUser(uid, story.authorId) }
                .onSuccess {
                    onUnfollowAuthor()
                    showSuccess(context.getString(R.string.story_context_menu_unfollow_success))
                }
                .onFailure {
                    showSuccess(context.getString(R.string.story_context_menu_action_failed))
                }
            delay(100)
            resumeStoryPlayback()
        }
    }

    fun muteStoryAuthor() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        pauseStoryPlayback()
        scope.launch {
            runCatching {
                firestore.db.collection("users").document(uid)
                    .update("muteSettings.mutedUsers", FieldValue.arrayUnion(story.authorId)).await()
            }.onSuccess {
                onMuteAuthor()
                showSuccess(context.getString(R.string.story_context_menu_mute_success_with_hint))
            }.onFailure {
                showSuccess(context.getString(R.string.story_context_menu_action_failed))
            }
            delay(100)
            resumeStoryPlayback()
        }
    }

    fun optOutFromBestFriends() {
        pauseStoryPlayback()
        scope.launch {
            runCatching { bestFriendsService.optOutFromBestFriends(story.authorId) }
                .onSuccess {
                    val uid = FirebaseAuth.getInstance().currentUser?.uid
                    if (uid != null) StorySeenStateService.invalidate(uid, story.authorId)
                    onLeaveBestFriends()
                    showSuccess(context.getString(R.string.best_friends_opt_out_success))
                    delay(2_000)
                    onNextState.value()
                }
                .onFailure {
                    showSuccess(context.getString(R.string.best_friends_opt_out_error))
                    resumeStoryPlayback()
                }
        }
    }

    fun handleStoryConfirmation(kind: StoryConfirmationKind) {
        clearAllStoryConfirmations()
        when (kind) {
            StoryConfirmationKind.DELETE -> deleteStoryAction()
            StoryConfirmationKind.UNFOLLOW -> unfollowStoryAuthor()
            StoryConfirmationKind.MUTE -> muteStoryAuthor()
            StoryConfirmationKind.LEAVE_BEST_FRIENDS -> optOutFromBestFriends()
        }
    }

    fun sendMessageAction() {
        val text = messageText.trim()
        if (text.isEmpty()) return
        val storyId = story.id ?: return
        messageText = ""
        isTextFieldFocused = false
        focusManager.clearFocus()
        storyViewModel?.sendMessage(story.authorId, storyId, text) { ok ->
            if (ok) {
                onSendMessage(text)
                showSuccess(context.getString(R.string.stories_message_sent))
            } else {
                messageText = text
                showSuccess(context.getString(R.string.story_context_menu_action_failed))
            }
        } ?: run {
            onSendMessage(text)
            showSuccess(context.getString(R.string.stories_message_sent))
        }
    }

    fun sendReactionAction(
        emoji: String,
        widthPx: Float,
        heightPx: Float,
        // FloatingHeart usa dp (≡ puntos iOS); smiley ya está en dp
        sourceX: Float = if (smileyButtonCenterX > 0f) smileyButtonCenterX else with(density) { (widthPx * 0.82f).toDp().value },
        sourceY: Float = if (smileyButtonCenterY > 0f) smileyButtonCenterY else with(density) { (heightPx * 0.92f).toDp().value },
    ) {
        val storyId = story.id ?: return
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        storyViewModel?.sendReaction(story.authorId, storyId, emoji)
            ?: scope.launch { runCatching { storyRepository.addReaction(story.authorId, storyId, uid, emoji) } }
        emojiUsageTracker.increment(emoji)
        val widthDp = with(density) { widthPx.toDp().value }
        val heightDp = with(density) { heightPx.toDp().value }
        floatingHearts = StoryReactionBurst.emit(
            floatingHearts, emoji, widthDp, heightDp, sourceX = sourceX, sourceY = sourceY,
        )
        showReactions = false
        scope.launch {
            delay(300)
            resumeStoryPlayback()
        }
    }

    fun fetchViewersAndShow(tab: Int = 0) {
        val storyId = story.id ?: return
        activityTab = tab
        pauseStoryPlayback()
        storyViewModel?.fetchViewers(story.authorId, storyId) {
            storyViewModel?.fetchReactions(story.authorId, storyId)
            showActivity = true
        } ?: run {
            storyViewModel?.fetchReactions(story.authorId, storyId)
            showActivity = true
        }
    }

    fun loadChainStoriesIfNeeded() {
        val chainId = story.chainId ?: return
        scope.launch {
            val loaded = runCatching {
                firestore.db.collectionGroup("stories")
                    .whereEqualTo("chainId", chainId)
                    .orderBy("chainPosition")
                    .get().await()
                    .documents.mapNotNull { doc ->
                        @Suppress("UNCHECKED_CAST")
                        Story.from(doc.id, doc.data as? Map<String, Any?> ?: return@mapNotNull null)
                    }
            }.getOrDefault(emptyList())
            chainStories = loaded
            currentChainIndex = loaded.indexOfFirst { it.id == story.id }.coerceAtLeast(0)
            val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
            canContinueChain = StoryViewerChainLogic.canContinueChain(
                chainId = chainId,
                storyAuthorId = story.authorId,
                currentUserId = uid,
                db = firestore.db,
            )
        }
    }

    val ephemeralPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        showEphemeralPicker = false
        if (uri == null) {
            scope.launch {
                delay(500)
                resumeStoryPlayback()
            }
            return@rememberLauncherForActivityResult
        }
        val storyId = story.id ?: run {
            scope.launch {
                delay(500)
                resumeStoryPlayback()
            }
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }
            if (bytes == null) {
                delay(500)
                resumeStoryPlayback()
                return@launch
            }
            storyViewModel?.sendEphemeralMoment(story.authorId, storyId, bytes) { ok ->
                showSuccess(
                    context.getString(
                        if (ok) R.string.stories_moment_sent else R.string.story_context_menu_action_failed,
                    ),
                )
                scope.launch {
                    delay(500)
                    resumeStoryPlayback()
                }
            }
            if (storyViewModel == null) {
                delay(500)
                resumeStoryPlayback()
            }
        }
    }

    fun audienceForSegment(index: Int): String? {
        val storiesForAuthor = storyViewModel?.stories?.get(story.authorId) ?: return null
        return storiesForAuthor.getOrNull(index)?.audience
    }

    val interactionBlockedState = rememberUpdatedState(isStoryInteractionBlocked)
    var pinchGestureSeen by remember { mutableStateOf(false) }
    val zoomGesture = rememberTransformableState { zoomChange, _, _ ->
        if (interactionBlockedState.value) return@rememberTransformableState
        // ≡ MagnifyGesture: acumular desde lastZoomScale al inicio del pinch
        if (!pinchGestureSeen) {
            zoomScale = lastZoomScale
            pinchGestureSeen = true
        }
        zoomScale = (zoomScale * zoomChange).coerceIn(1f, 3f)
    }
    LaunchedEffect(zoomGesture.isTransformInProgress) {
        if (zoomGesture.isTransformInProgress) {
            if (!pinchGestureSeen) {
                zoomScale = lastZoomScale
                pinchGestureSeen = true
            }
            return@LaunchedEffect
        }
        if (!pinchGestureSeen) return@LaunchedEffect
        if (interactionBlockedState.value) {
            lastZoomScale = zoomScale
            return@LaunchedEffect
        }
        if (zoomScale < 1.2f) {
            val anim = Animatable(zoomScale)
            anim.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.7f,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ) {
                zoomScale = value
            }
            lastZoomScale = 1f
            zoomScale = 1f
        } else {
            lastZoomScale = zoomScale
        }
        pinchGestureSeen = false
    }

    // MARK: - Lifecycle
    LaunchedEffect(story.id) {
        if (story.id != lastPreparedStoryId) {
            lastPreparedStoryId = story.id
            isStoryVideoReady = false
            textMotionReplayToken += 1
            storyStickers = resolvedStoryStickers(story)
            playbackCoordinator.prepareStory(story) { onNextState.value() }
            markStoryAsViewedIfNeeded()
            val allStories = storyViewModel?.stories?.get(story.authorId).orEmpty()
            if (allStories.isNotEmpty()) {
                storyViewModel?.preloadNextStory(story.id.orEmpty(), allStories)
            }
        } else if (storyStickers.isEmpty()) {
            storyStickers = resolvedStoryStickers(story)
        }
    }
    LaunchedEffect(story.authorId) {
        val settings = runCatching {
            @Suppress("UNCHECKED_CAST")
            firestore.db.collection("users").document(story.authorId).get().await().data
                ?.get("contentVisibilitySettings") as? Map<String, Any?>
        }.getOrNull()
        authorAllowsMessages = settings?.get("allowStoryMessages") as? Boolean ?: true
        authorAllowsReactions = settings?.get("allowStoryReactions") as? Boolean ?: true
        authorAllowsEphemeralPhotos = settings?.get("allowStoryEphemeralPhotos") as? Boolean ?: true
        if (!isOwnStory) {
            storyViewModel?.fetchVanishState(story.authorId) { isVanishActiveWithAuthor = it }
        }
    }
    LaunchedEffect(story.chainId) {
        if (story.chainId != null) loadChainStoriesIfNeeded()
    }
    LaunchedEffect(isOwnStory, story.id) {
        if (isOwnStory) {
            val storyId = story.id ?: return@LaunchedEffect
            storyViewModel?.fetchViewers(story.authorId, storyId)
            storyViewModel?.fetchReactions(story.authorId, storyId)
        }
    }
    LaunchedEffect(isDeckPageActive) {
        if (isDeckPageActive) {
            markStoryAsViewedIfNeeded()
            resumeStoryPlayback()
        } else {
            pauseStoryPlayback()
        }
    }
    DisposableEffect(playbackCoordinator) {
        onDispose {
            playbackCoordinator.stopStory()
            playbackCoordinator.close()
            MomentsAudioSession.deactivate()
            story.id?.let { storyViewModel?.stopObservingReactions(it) }
        }
    }
    LaunchedEffect(showQuickActions) { pauseOrResumeForOverlay(showQuickActions) }
    LaunchedEffect(showActivity) { pauseOrResumeForOverlay(showActivity) }
    LaunchedEffect(showChain) { pauseOrResumeForOverlay(showChain) }
    LaunchedEffect(showStoryShareSheet) { pauseOrResumeForOverlay(showStoryShareSheet) }
    LaunchedEffect(showReactions) { pauseOrResumeForOverlay(showReactions) }
    LaunchedEffect(showStoryReactionEmojiPicker) { pauseOrResumeForOverlay(showStoryReactionEmojiPicker) }
    LaunchedEffect(showMomentDetail) { pauseOrResumeForOverlay(showMomentDetail) }
    LaunchedEffect(showingReportSheet) { pauseOrResumeForOverlay(showingReportSheet) }
    LaunchedEffect(showingBlockConfirmation) { pauseOrResumeForOverlay(showingBlockConfirmation) }
    LaunchedEffect(profileRoute) { pauseOrResumeForOverlay(profileRoute != null) }
    LaunchedEffect(activeStoryConfirmation != null) {
        pauseOrResumeForOverlay(activeStoryConfirmation != null)
    }
    LaunchedEffect(isKeyboardVisible) {
        if (!isKeyboardVisible) {
            delay(100)
            resumeStoryPlayback()
        } else {
            pauseStoryPlayback()
        }
    }

    val confirmationTitle = when (activeStoryConfirmation) {
        StoryConfirmationKind.DELETE -> stringResource(R.string.story_context_menu_delete_confirm_title)
        StoryConfirmationKind.UNFOLLOW -> stringResource(R.string.story_context_menu_unfollow_confirm_title, story.username)
        StoryConfirmationKind.MUTE -> stringResource(R.string.story_context_menu_mute_confirm_title, story.username)
        StoryConfirmationKind.LEAVE_BEST_FRIENDS -> stringResource(R.string.best_friends_opt_out_confirm_title, story.username)
        null -> ""
    }
    val confirmationMessage = when (activeStoryConfirmation) {
        StoryConfirmationKind.DELETE -> stringResource(R.string.story_context_menu_delete_confirm_message)
        StoryConfirmationKind.UNFOLLOW -> stringResource(R.string.story_context_menu_unfollow_confirm_message)
        StoryConfirmationKind.MUTE -> stringResource(R.string.story_context_menu_mute_confirm_message)
        StoryConfirmationKind.LEAVE_BEST_FRIENDS -> stringResource(R.string.best_friends_opt_out_confirm_message)
        null -> ""
    }
    val confirmationAction = when (activeStoryConfirmation) {
        StoryConfirmationKind.DELETE -> stringResource(R.string.story_context_menu_delete_confirm_action)
        StoryConfirmationKind.UNFOLLOW -> stringResource(R.string.story_context_menu_unfollow_confirm_action)
        StoryConfirmationKind.MUTE -> stringResource(R.string.story_context_menu_mute_confirm_action)
        StoryConfirmationKind.LEAVE_BEST_FRIENDS -> stringResource(R.string.best_friends_opt_out_confirm_action)
        null -> ""
    }
    val confirmationCancel = when (activeStoryConfirmation) {
        StoryConfirmationKind.DELETE -> stringResource(R.string.story_context_menu_delete_confirm_cancel)
        StoryConfirmationKind.UNFOLLOW -> stringResource(R.string.story_context_menu_unfollow_confirm_cancel)
        StoryConfirmationKind.MUTE -> stringResource(R.string.story_context_menu_mute_confirm_cancel)
        StoryConfirmationKind.LEAVE_BEST_FRIENDS -> stringResource(R.string.best_friends_opt_out_confirm_cancel)
        null -> ""
    }

    val canvasBg = adaptive.surfaceBackground
    val replyPlaceholder = when {
        !authorAllowsMessages -> stringResource(R.string.stories_replies_disabled_placeholder)
        isVanishActiveWithAuthor -> stringResource(R.string.chat_input_vanish_placeholder)
        else -> stringResource(R.string.stories_send_message_placeholder, story.username)
    }

    // MARK: - Body / geometry stack ≡ profileAndChainBoundView + userProfileNavigationDestination
    UserProfileZoomNavigationHost(
        profileRoute = profileRoute,
        onProfileRouteChange = { profileRoute = it },
        modifier = modifier,
    ) { profileOpen ->
    Box(
        Modifier
            .fillMaxSize()
            .background(canvasBg)
            .graphicsLayer(scaleX = zoomScale, scaleY = zoomScale)
            .transformable(state = zoomGesture)
            .pointerInput(story.id, deckGestureGate) {
                detectTapGestures(onTap = { focusManager.clearFocus() })
            },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val screenW = constraints.maxWidth.toFloat()
            val screenH = constraints.maxHeight.toFloat()
            SideEffect {
                viewerScreenWidth = screenW
                viewerScreenHeight = screenH
            }
            val screenSize = Size(screenW, screenH)
            val topInset = WindowInsets.statusBars.getTop(density).toFloat()
            val bottomInset = WindowInsets.navigationBars.getBottom(density).toFloat()
            val captureRect = storyViewerCaptureRect(
                inSize = screenSize,
                safeAreaTopPx = topInset,
                safeAreaBottomPx = bottomInset,
                density = density,
            )
            val bottomChromeHeight = with(density) {
                (screenH - bottomInset - captureRect.bottom).coerceAtLeast(0f).toDp()
            }
            // IG-like: encima de nav + buffer (~25dp). Antes 8dp quedaba demasiado pegado.
            val replyBottomPadding = if (isKeyboardVisible) 6.dp else 25.dp
            val canvasRect = Rect(captureRect.left, captureRect.top, captureRect.right, captureRect.bottom)
            val corner = storyViewerCanvasCornerRadius
            val regions = deckGestureGate?.interactionRegions.orEmpty()

            fun shouldSuppressNav(point: Offset): Boolean =
                System.currentTimeMillis() < suppressNavigationTapUntil ||
                    gestureCoordinator.shouldSuppressNavigationTap(point, canvasRect, regions, deckGestureGate)

            // Hold (0.12s) + unified drag / swipe-up — ≡ holdToPauseGesture + unifiedDragGesture
            val blockedState = rememberUpdatedState(isStoryInteractionBlocked)
            val keyboardState = rememberUpdatedState(isKeyboardVisible)
            val deckActiveState = rememberUpdatedState(isDeckPageActive)
            val allowsMessagesState = rememberUpdatedState(authorAllowsMessages)
            val holdingState = rememberUpdatedState(isHoldingStory)
            val textFocusedState = rememberUpdatedState(isTextFieldFocused)
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(story.id, canvasRect, regions) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val start = down.position
                            val blocked = blockedState.value
                            val allowHold = gestureCoordinator.shouldAllowHoldStart(
                                point = start,
                                screenSize = screenSize,
                                canvasRect = canvasRect,
                                regions = regions,
                                gate = deckGestureGate,
                                isKeyboardVisible = keyboardState.value,
                                overlaysBlocked = blocked,
                            )
                            val allowDrag = gestureCoordinator.shouldAllowUnifiedViewerDragStart(
                                point = start,
                                screenSize = screenSize,
                                canvasRect = canvasRect,
                                regions = regions,
                                gate = deckGestureGate,
                                overlaysBlocked = blocked,
                            )
                            if (!allowHold && !allowDrag) return@awaitEachGesture

                            var holdArmed = allowHold
                            var dragActive = false
                            var swipeTriggered = false
                            var didHold = false
                            gestureActionTriggered = false

                            holdPauseJob?.cancel()
                            if (allowHold) {
                                holdPauseJob = scope.launch {
                                    delay(120)
                                    if (holdArmed && !dragActive && !blockedState.value) {
                                        didHold = true
                                        beginHoldPause()
                                    }
                                }
                            }

                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    val translation = change.position - start

                                    if (holdArmed && (abs(translation.x) > 14f || abs(translation.y) > 14f)) {
                                        holdPauseJob?.cancel()
                                        holdArmed = false
                                    }

                                    if (!dragActive && allowDrag &&
                                        (abs(translation.x) > 8f || abs(translation.y) > 8f)
                                    ) {
                                        dragActive = true
                                        isDragging = true
                                        holdPauseJob?.cancel()
                                        holdArmed = false
                                        if (!playbackCoordinator.isPaused &&
                                            !holdingState.value &&
                                            deckActiveState.value
                                        ) {
                                            pauseStoryPlayback()
                                            isUIHidden = true
                                        }
                                    }

                                    if (dragActive && !swipeTriggered &&
                                        translation.y < -60f && abs(translation.x) < 50f &&
                                        allowsMessagesState.value
                                    ) {
                                        swipeTriggered = true
                                        gestureActionTriggered = true
                                        isUIHidden = false
                                        isTextFieldFocused = true
                                        try {
                                            focusRequester.requestFocus()
                                        } catch (_: Exception) {
                                        }
                                    }

                                    if (!change.pressed) break
                                    change.consume()
                                }
                            } finally {
                                holdPauseJob?.cancel()
                                holdPauseJob = null
                                if (didHold || holdingState.value) {
                                    endHoldPause()
                                } else if (dragActive) {
                                    isDragging = false
                                    gestureActionTriggered = false
                                    isUIHidden = false
                                    if (!textFocusedState.value && deckActiveState.value) {
                                        resumeStoryPlayback()
                                    }
                                }
                            }
                        }
                    },
            )

            // MARK: 1. Media canvas
            Box(
                Modifier
                    .offset { IntOffset(captureRect.left.roundToInt(), captureRect.top.roundToInt()) }
                    .size(
                        width = with(density) { captureRect.width.toDp() },
                        height = with(density) { captureRect.height.toDp() },
                    )
                    .clip(RoundedCornerShape(corner))
                    .background(Color.Black),
            ) {
                ScreenshotProtectedView(
                    isProtected = !isEveryoneStoryAudience,
                    fillsContainer = true,
                    cornerRadius = storyViewerCanvasCornerRadius,
                    // Fullscreen: FLAG_SECURE (ContentSurface no aporta y ya validado).
                    mode = ScreenshotProtectionMode.WindowFlag,
                ) {
                    StoryViewerMedia(
                        story = story,
                        isPaused = playbackCoordinator.isPaused || !isDeckPageActive,
                        isMutedExternally = shouldMuteVideoForReveal,
                        onVideoProgress = { playbackCoordinator.updateVideoProgress(it, story) },
                        onVideoComplete = {
                            if (playbackCoordinator.canAdvanceAfterVideoComplete()) onNextState.value()
                        },
                        onReadyToPlayChanged = { isStoryVideoReady = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                    StoryMediaOverlayRendererView(
                        // ≡ iOS: resolvedTextOverlays; drawingData nil (dibujo ya bakeado en media)
                        textOverlays = story.resolvedTextOverlays,
                        stickers = storyStickers,
                        drawingData = null,
                        storyId = story.id.orEmpty(),
                        userId = story.authorId,
                        replayToken = textMotionReplayToken,
                        gestureGate = deckGestureGate,
                        reportsDeckInteractionExclusion = isDeckPageActive,
                        allowsStickerHitTesting = true,
                        onPauseStory = ::pauseStoryPlayback,
                        onResumeStory = { resumeStoryPlayback() },
                        onMomentTap = { momentId, authorId ->
                            targetMomentId = momentId
                            targetMomentUserId = authorId
                            showMomentDetail = true
                            pauseStoryPlayback()
                        },
                        onMentionTap = { userId ->
                            if (userId.isNotBlank()) {
                                profileRoute = FeedProfileSheetRoute(userId)
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            // MARK: 3. Floating hearts
            StoryFloatingReactionLayer(
                hearts = floatingHearts,
                containerSize = DpSize(
                    with(density) { screenW.toDp() },
                    with(density) { screenH.toDp() },
                ),
                onHeartExpired = { id ->
                    floatingHearts = floatingHearts.filterNot { it.id == id }
                },
                modifier = Modifier.fillMaxSize(),
            )

            // MARK: 3.5 Reveal — mismo captureRect que media/overlays (≡ iOS frame+position)
            StoryRevealStickerOverlay(
                storyId = story.id.orEmpty(),
                stickers = storyStickers,
                gestureGate = deckGestureGate,
                onPauseStory = ::pauseStoryPlayback,
                onResumeStory = { resumeStoryPlayback() },
                modifier = Modifier
                    .offset { IntOffset(captureRect.left.roundToInt(), captureRect.top.roundToInt()) }
                    .size(
                        width = with(density) { captureRect.width.toDp() },
                        height = with(density) { captureRect.height.toDp() },
                    )
                    .clip(RoundedCornerShape(corner)),
            )

            // Navigation bands ≡ StoryNavigationTouchAreas (ocultas con teclado)
            val navigationHitEnabled =
                !isStoryInteractionBlocked &&
                    deckGestureGate?.suppressStoryNavigationGestures != true &&
                    deckGestureGate?.suppressViewerGestures != true
            if (!isUIHidden && !isKeyboardVisible) {
                StoryNavigationTouchAreas(
                    // ≡ iOS: punto local al canvas → screenPoint vía captureRect
                    shouldSuppressNavigationTapAt = { local ->
                        shouldSuppressNav(
                            Offset(canvasRect.left + local.x, canvasRect.top + local.y),
                        )
                    },
                    onPrevious = { onPreviousState.value() },
                    onNext = { onNextState.value() },
                    enabled = navigationHitEnabled,
                    modifier = Modifier
                        .offset { IntOffset(captureRect.left.roundToInt(), captureRect.top.roundToInt()) }
                        .size(
                            width = with(density) { captureRect.width.toDp() },
                            height = with(density) { captureRect.height.toDp() },
                        ),
                )
            }

            // MARK: 4. Progress (fuera del marco) + Header (dentro)
            // ≡ iOS: progressY = max(topInset+1, captureRect.minY - 26); header at minY + 26
            // SwiftUI `.position` centra el view en ese punto → offset(y - height/2)
            if (!isUIHidden) {
                val progressCenterY = maxOf(
                    topInset + with(density) { 1.dp.toPx() },
                    captureRect.top - with(density) { 26.dp.toPx() },
                )
                val headerCenterY = captureRect.top + with(density) { 26.dp.toPx() }
                var progressH by remember { mutableIntStateOf(with(density) { 3.dp.roundToPx() }) }
                var headerH by remember { mutableIntStateOf(with(density) { 40.dp.roundToPx() }) }

                Box(
                    Modifier
                        .offset {
                            IntOffset(0, (progressCenterY - progressH / 2f).roundToInt())
                        }
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .onSizeChanged { progressH = it.height },
                ) {
                    StorySegmentProgressChrome(
                        storyCount = storyCount.coerceAtLeast(1),
                        storyIndex = storyIndex.coerceIn(0, (storyCount - 1).coerceAtLeast(0)),
                        progressForSegment = { idx ->
                            playbackCoordinator.progressForSegment(idx, storyIndex)
                        },
                        audienceForSegment = ::audienceForSegment,
                    )
                }
                Box(
                    Modifier
                        .offset {
                            IntOffset(
                                captureRect.left.roundToInt(),
                                (headerCenterY - headerH / 2f).roundToInt(),
                            )
                        }
                        .width(with(density) { captureRect.width.toDp() })
                        .padding(horizontal = 16.dp),
                ) {
                    Column {
                        // Medir solo el header (no el panel chain) ≡ iOS .position del glassmorphicHeader
                        Box(Modifier.onSizeChanged { headerH = it.height }) {
                            StoryViewerHeaderChrome(
                                username = story.username,
                                authorId = story.authorId,
                                isOwnStory = isOwnStory,
                                profileImagePath = story.profileImagePath,
                                timestamp = story.timestamp,
                                highlightTitle = null,
                                hasChain = story.chainId != null && story.chainTitle != null && story.chainPosition != null,
                                profileZoomVisible = !profileOpen,
                                onClose = onDismiss,
                                onProfileTap = {
                                    profileRoute = FeedProfileSheetRoute(story.authorId)
                                    onProfileTap()
                                },
                                onMore = { toggleQuickActions() },
                                onChain = { showChainActions = !showChainActions },
                            )
                        }
                        if (showChainActions && story.chainId != null) {
                            ChainActionsPanel(
                                chainTitle = story.chainTitle.orEmpty(),
                                chainPosition = story.chainPosition ?: 1,
                                canContinue = canContinueChain,
                                onViewChain = {
                                    showChainActions = false
                                    showChain = true
                                },
                                onContinue = {
                                    val id = story.chainId ?: return@ChainActionsPanel
                                    val title = story.chainTitle.orEmpty()
                                    // ≡ iOS: posición actual; el editor hace (pos ?? 0) + 1 al publicar
                                    val pos = story.chainPosition ?: 1
                                    showChainActions = false
                                    onDismiss()
                                    NavigationEventBus.emit(
                                        CoordinatorNavigationEvent.OpenCreatorForChain(id, title, pos),
                                    )
                                    onContinueChain(id, title, pos)
                                },
                                onPreviousPart = {
                                    if (currentChainIndex > 0) {
                                        val next = currentChainIndex - 1
                                        currentChainIndex = next
                                        onOpenChainStory(chainStories, next)
                                    }
                                },
                                onNextPart = {
                                    if (currentChainIndex < chainStories.lastIndex) {
                                        val next = currentChainIndex + 1
                                        currentChainIndex = next
                                        onOpenChainStory(chainStories, next)
                                    }
                                },
                                currentChainIndex = currentChainIndex,
                                chainCount = chainStories.size,
                            )
                        }
                    }
                }
            }

            // ≡ HighlightViewerTitlePill (sobre el borde inferior del canvas)
            if (!isUIHidden && !highlightTitle.isNullOrBlank()) {
                Box(
                    Modifier
                        .offset {
                            IntOffset(
                                captureRect.left.roundToInt(),
                                (captureRect.bottom - with(density) { 52.dp.toPx() }).roundToInt(),
                            )
                        }
                        .width(with(density) { captureRect.width.toDp() })
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    HighlightViewerTitlePill(title = highlightTitle)
                }
            }

            // MARK: 5. Bottom area ≡ glassmorphicBottomArea
            if (!isUIHidden) {
                if (isOwnStory) {
                    Box(
                        Modifier
                            .offset {
                                IntOffset(0, captureRect.bottom.roundToInt())
                            }
                            .fillMaxWidth()
                            .height(bottomChromeHeight)
                            .padding(horizontal = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        StoryOwnStoryBottomBar(
                            viewers = currentStoryViewers,
                            reactions = currentStoryReactions,
                            audience = story.audience,
                            expirationHours = story.expirationHours,
                            authorId = story.authorId,
                            customListId = story.customListId,
                            onViewActivity = { fetchViewersAndShow(0) },
                            onReactionsActivity = { fetchViewersAndShow(1) },
                            showsShare = isEveryoneStoryAudience,
                            onShare = {
                                pauseStoryPlayback()
                                showStoryShareSheet = true
                            },
                        )
                    }
                } else {
                    // IG-like height: encima de nav bars + gap corto (no +25 / mid-gap)
                    Column(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .imePadding()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = replyBottomPadding),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (showReactions && authorAllowsReactions) {
                            StoryReactionsStrip(
                                reactions = reactionEmojis,
                                showReactions = true,
                                onReaction = { sendReactionAction(it, screenW, screenH) },
                                onMoreReactions = {
                                    showStoryReactionEmojiPicker = true
                                },
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .padding(bottom = 2.dp),
                            )
                        }
                        when {
                            authorAllowsMessages || authorAllowsReactions || authorAllowsEphemeralPhotos || isEveryoneStoryAudience -> {
                                val showsReplyComposer =
                                    authorAllowsMessages ||
                                        (!authorAllowsMessages && (authorAllowsReactions || authorAllowsEphemeralPhotos))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    if (showsReplyComposer) {
                                        // ≡ HStack + padding(h:16, v:14) + Capsule chrome (iOS)
                                        Row(
                                            Modifier
                                                .weight(1f)
                                                .momentsChromeGlass(
                                                    RoundedCornerShape(percent = 50),
                                                    interactive = !isVanishActiveWithAuthor,
                                                )
                                                .then(
                                                    if (isVanishActiveWithAuthor) {
                                                        Modifier
                                                            .background(Color.Transparent)
                                                            .drawBehind {
                                                                // ≡ Capsule().stroke(dash: [5, 4], lineWidth: 1.2)
                                                                val stroke = Stroke(
                                                                    width = 1.2.dp.toPx(),
                                                                    pathEffect = PathEffect.dashPathEffect(
                                                                        floatArrayOf(5.dp.toPx(), 4.dp.toPx()),
                                                                    ),
                                                                )
                                                                drawRoundRect(
                                                                    color = vanishReplyStrokeColor,
                                                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                                                                        size.minDimension / 2f,
                                                                        size.minDimension / 2f,
                                                                    ),
                                                                    style = stroke,
                                                                )
                                                            }
                                                    } else {
                                                        Modifier
                                                    },
                                                )
                                                .padding(horizontal = 16.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            if (authorAllowsMessages) {
                                                // ≡ TextField compacto (no Material TextField min-height)
                                                val replyText = adaptive.messageTextColor
                                                val replyHint = adaptive.replyBarSecondaryText
                                                BasicTextField(
                                                    value = messageText,
                                                    onValueChange = { messageText = it },
                                                    singleLine = true,
                                                    textStyle = TextStyle(
                                                        color = replyText,
                                                        fontSize = 14.sp,
                                                    ),
                                                    cursorBrush = SolidColor(replyText),
                                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                                    keyboardActions = KeyboardActions(
                                                        onSend = {
                                                            if (messageText.isNotEmpty()) sendMessageAction()
                                                        },
                                                    ),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .focusRequester(focusRequester)
                                                        .onFocusChanged { isTextFieldFocused = it.isFocused },
                                                    decorationBox = { inner ->
                                                        Box(Modifier.fillMaxWidth()) {
                                                            if (messageText.isEmpty()) {
                                                                Text(
                                                                    replyPlaceholder,
                                                                    color = replyHint,
                                                                    fontSize = 14.sp,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis,
                                                                )
                                                            }
                                                            inner()
                                                        }
                                                    },
                                                )
                                            } else {
                                                Text(
                                                    stringResource(R.string.stories_replies_disabled_placeholder),
                                                    color = adaptive.replyBarSecondaryText,
                                                    fontSize = 14.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f),
                                                )
                                            }
                                        }
                                    } else if (isEveryoneStoryAudience) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                    // ≡ HStack(spacing: 2) + storyViewerReplyActionButton;
                                    // con teclado: círculo chrome (contraste sobre IME)
                                    val replyActionTint = adaptive.messageTextColor
                                    val replyIconsGlass = isKeyboardVisible || isTextFieldFocused
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(
                                            if (replyIconsGlass) 6.dp else 2.dp,
                                        ),
                                    ) {
                                        if (authorAllowsReactions && (messageText.isEmpty() || !authorAllowsMessages)) {
                                            StoryViewerReplyActionButton(
                                                glass = replyIconsGlass,
                                                onClick = {
                                                    // ≡ MotionPolicy.withOptionalAnimation(Spring.toggle)
                                                    showReactions = !showReactions
                                                },
                                                modifier = Modifier.onGloballyPositioned { coords ->
                                                    // ≡ geo.frame(in: .named("storyViewerSpace")).mid → dp
                                                    val p = coords.positionInRoot()
                                                    smileyButtonCenterX = with(density) {
                                                        (p.x + coords.size.width / 2f).toDp().value
                                                    }
                                                    smileyButtonCenterY = with(density) {
                                                        (p.y + coords.size.height / 2f).toDp().value
                                                    }
                                                },
                                            ) {
                                                Icon(
                                                    if (showReactions) {
                                                        Icons.Filled.SentimentSatisfied
                                                    } else {
                                                        Icons.Outlined.SentimentSatisfied
                                                    },
                                                    contentDescription = stringResource(R.string.stories_reactions),
                                                    tint = replyActionTint,
                                                    modifier = Modifier.size(22.dp),
                                                )
                                            }
                                        }
                                        if (authorAllowsEphemeralPhotos) {
                                            StoryViewerReplyActionButton(
                                                glass = replyIconsGlass,
                                                onClick = {
                                                    pauseStoryPlayback()
                                                    showEphemeralPicker = true
                                                    ephemeralPicker.launch("image/*")
                                                },
                                            ) {
                                                AttachmentIconView(
                                                    icon = AttachmentIcon.CAMERA,
                                                    preset = AttachmentIconPreset.STORY_REPLY_ACTION,
                                                    tintColor = replyActionTint,
                                                )
                                            }
                                        }
                                        if (messageText.isNotEmpty() && authorAllowsMessages) {
                                            StoryViewerReplyActionButton(
                                                glass = replyIconsGlass,
                                                onClick = ::sendMessageAction,
                                            ) {
                                                Icon(
                                                    Icons.AutoMirrored.Filled.Send,
                                                    contentDescription = stringResource(R.string.messaging_send_message),
                                                    tint = replyActionTint,
                                                    modifier = Modifier.size(22.dp),
                                                )
                                            }
                                        }
                                        if (isEveryoneStoryAudience && messageText.isEmpty()) {
                                            StoryViewerReplyActionButton(
                                                glass = replyIconsGlass,
                                                onClick = {
                                                    pauseStoryPlayback()
                                                    showStoryShareSheet = true
                                                },
                                            ) {
                                                Icon(
                                                    Icons.AutoMirrored.Filled.Send,
                                                    contentDescription = stringResource(R.string.stories_own_bottom_share),
                                                    tint = replyActionTint,
                                                    modifier = Modifier.size(22.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            else -> StoryNoInteractionsNotice(Modifier.align(Alignment.CenterHorizontally))
                        }
                    }
                }
            }
        }

        // MARK: Quick actions overlay
        if (showQuickActions && !isUIHidden) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable { dismissQuickActions() },
            )
            StoryQuickActionsMenu(
                isOwnStory = isOwnStory,
                canLeaveBestFriends = canOptOutFromAuthorBestFriends,
                textColor = quickActionTextColor,
                dividerColor = quickActionDividerColor,
                onViewActivity = {
                    dismissQuickActions(resume = false)
                    fetchViewersAndShow()
                    onViewActivity()
                },
                onSave = {
                    dismissQuickActions(resume = false)
                    photosSaveGate.requestAccess(context) {
                        scope.launch {
                            val ok = saveStoryMediaToDevice(context, story)
                            showSuccess(
                                context.getString(
                                    when {
                                        !ok -> R.string.story_context_menu_action_failed
                                        story.mediaItem.type == MediaItem.MediaType.VIDEO -> R.string.stories_saved_video
                                        else -> R.string.stories_saved_image
                                    },
                                ),
                            )
                            onSaveStory()
                            resumeStoryPlayback()
                        }
                    }
                },
                onDelete = {
                    dismissQuickActions(resume = false)
                    showDeleteConfirmation = true
                },
                onUnfollow = {
                    dismissQuickActions(resume = false)
                    showUnfollowConfirmation = true
                },
                onMute = {
                    dismissQuickActions(resume = false)
                    showMuteConfirmation = true
                },
                onReport = {
                    dismissQuickActions(resume = false)
                    onReportStory()
                },
                onLeaveBestFriends = {
                    dismissQuickActions(resume = false)
                    showBestFriendsOptOutConfirmation = true
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 56.dp, end = 16.dp),
            )
        }

        // MARK: Confirmation dialog
        activeStoryConfirmation?.let { kind ->
            GlassmorphicStoryConfirmationDialog(
                title = confirmationTitle,
                message = confirmationMessage,
                confirmTitle = confirmationAction,
                cancelTitle = confirmationCancel,
                isDestructive = true,
                onConfirm = { handleStoryConfirmation(kind) },
                onCancel = { clearAllStoryConfirmations() },
            )
        }

        // MARK: Activity sheet ≡ .presentationDetents([.medium, .large])
        if (showActivity) {
            MomentsModalSheet(
                onDismissRequest = { showActivity = false },
                largeOnly = false,
            ) {
                GlassmorphicViewersSheet(
                    story = story,
                    viewers = currentStoryViewers,
                    reactions = currentStoryReactions,
                    initialTab = activityTab,
                    onDismiss = { showActivity = false },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // MARK: Chain sheet ≡ .sheet StoryChainView (sin detents → large)
        if (showChain) {
            val chainId = story.chainId
            val chainTitle = story.chainTitle
            if (chainId != null && chainTitle != null) {
                MomentsModalSheet(
                    onDismissRequest = { showChain = false },
                    largeOnly = true,
                ) {
                    StoryChainView(
                        chainId = chainId,
                        chainTitle = chainTitle,
                        canContinueChain = canContinueChain,
                        initialStoryId = story.id,
                        initialChainPosition = story.chainPosition,
                        onDismiss = { showChain = false },
                        onContinueChain = { id, title, position ->
                            showChain = false
                            onContinueChain(id, title, position)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        // MARK: Share sheet ≡ StoryShareBottomSheet
        if (showStoryShareSheet) {
            StoryShareBottomSheet(
                story = story,
                onDismiss = {
                    showStoryShareSheet = false
                    resumeStoryPlayback()
                },
            )
        }

        // MARK: Emoji picker — M3 ModalBottomSheet (no overlay + altura % iOS)
        if (showStoryReactionEmojiPicker) {
            MomentsModalSheet(
                onDismissRequest = { showStoryReactionEmojiPicker = false },
                largeOnly = false,
            ) { dismiss ->
                EmojiPickerView(
                    onDismiss = dismiss,
                    onSelect = { emoji ->
                        dismiss()
                        sendReactionAction(emoji, viewerScreenWidth, viewerScreenHeight)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
        }

        // MARK: Moment detail from sticker ≡ .sheet OpenMomentFromStory
        if (showMomentDetail) {
            val momentId = targetMomentId
            val userId = targetMomentUserId
            if (momentId != null && userId != null) {
                MomentsModalSheet(
                    onDismissRequest = {
                        showMomentDetail = false
                        targetMomentId = null
                        targetMomentUserId = null
                    },
                    largeOnly = true,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        FeedMomentDetailRoute(
                            momentId = momentId,
                            authorId = userId,
                            onDismiss = {
                                showMomentDetail = false
                                targetMomentId = null
                                targetMomentUserId = null
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        // MARK: profileRoute via UserProfileZoomNavigationHost (shared-element, no Dialog)

        PermissionPrimerGateHost(gate = photosSaveGate)

        // MARK: Success toast
        successMessageText?.let { msg ->
            GlassmorphicSuccessMessage(
                text = msg,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 72.dp),
            )
        }
    }
    } // UserProfileZoomNavigationHost
}

@Composable
private fun StoryViewerHeaderChrome(
    username: String,
    authorId: String,
    isOwnStory: Boolean,
    profileImagePath: String?,
    timestamp: java.util.Date,
    highlightTitle: String?,
    hasChain: Boolean,
    profileZoomVisible: Boolean = true,
    onClose: () -> Unit,
    onProfileTap: () -> Unit,
    onMore: () -> Unit,
    onChain: () -> Unit,
) {
    val timeAgo = remember(timestamp) { MomentsFormat.relativeTime(timestamp) }
    // Chrome opaco Android: no blanco fijo (iOS glass translúcido sobre media oscura).
    val chromeFg = MomentsChromeGlass.contentColor(isSystemInDarkTheme())
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier
                .weight(1f)
                .clickable(onClick = onProfileTap),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AsyncImage(
                model = profileImagePath,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(38.dp)
                    .shadow(10.dp, CircleShape, ambientColor = Color.Black.copy(0.38f), spotColor = Color.Black.copy(0.38f))
                    .userProfileZoomSource(
                        userId = authorId,
                        visible = profileZoomVisible,
                        cornerRadius = 19.dp,
                    )
                    .clip(CircleShape)
                    .background(Color.Black.copy(0.16f)),
            )
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        username,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                    )
                    if (isOwnStory) {
                        CurrentUserVerifiedBadge(size = 12.dp)
                    } else {
                        VerifiedBadgeView(userId = authorId, size = 12.dp)
                    }
                }
                Text(
                    highlightTitle?.takeIf { it.isNotBlank() } ?: timeAgo,
                    color = Color.White.copy(0.7f),
                    fontSize = 11.sp,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (hasChain) {
                Box(
                    Modifier
                        .size(40.dp)
                        .momentsChromeGlass(CircleShape, interactive = true)
                        .clickable(onClick = onChain),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Link, contentDescription = null, tint = chromeFg, modifier = Modifier.size(16.dp))
                }
            }
            Box(
                Modifier
                    .size(40.dp)
                    .momentsChromeGlass(CircleShape, interactive = true)
                    .clickable(onClick = onMore),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.MoreHoriz, contentDescription = null, tint = chromeFg, modifier = Modifier.size(16.dp))
            }
            Box(
                Modifier
                    .size(40.dp)
                    .momentsChromeGlass(CircleShape, interactive = true)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, contentDescription = null, tint = chromeFg, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun ChainActionsPanel(
    chainTitle: String,
    chainPosition: Int,
    canContinue: Boolean,
    onViewChain: () -> Unit,
    onContinue: () -> Unit,
    onPreviousPart: () -> Unit,
    onNextPart: () -> Unit,
    currentChainIndex: Int,
    chainCount: Int,
) {
    val isDark = isSystemInDarkTheme()
    val primary = if (isDark) Color.White else Color.Black
    val secondary = if (isDark) Color.White.copy(0.88f) else Color.Black.copy(0.82f)
    Column(
        Modifier
            .width(270.dp)
            .padding(top = 8.dp)
            .momentsChromeGlass(RoundedCornerShape(18.dp), interactive = false)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Link, contentDescription = null, tint = secondary, modifier = Modifier.size(13.dp))
            Text(
                stringResource(R.string.story_chains_part_of, chainPosition, chainTitle),
                color = secondary,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(R.string.story_chains_view_chain),
                color = primary,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                modifier = Modifier
                    .weight(1f)
                    .momentsChromeGlass(RoundedCornerShape(percent = 50), interactive = true)
                    .clickable(onClick = onViewChain)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
            if (canContinue) {
                Text(
                    stringResource(R.string.story_chains_continue_story),
                    color = primary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .weight(1f)
                        .momentsChromeGlass(RoundedCornerShape(percent = 50), interactive = true)
                        .clickable(onClick = onContinue)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
        if (chainCount > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.story_chains_previous_part),
                    color = primary.copy(if (currentChainIndex > 0) 1f else 0.45f),
                    fontSize = 13.sp,
                    modifier = Modifier
                        .weight(1f)
                        .momentsChromeGlass(RoundedCornerShape(percent = 50), interactive = currentChainIndex > 0)
                        .clickable(enabled = currentChainIndex > 0, onClick = onPreviousPart)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
                Text(
                    stringResource(R.string.story_chains_next_part),
                    color = primary.copy(if (currentChainIndex < chainCount - 1) 1f else 0.45f),
                    fontSize = 13.sp,
                    modifier = Modifier
                        .weight(1f)
                        .momentsChromeGlass(RoundedCornerShape(percent = 50), interactive = currentChainIndex < chainCount - 1)
                        .clickable(enabled = currentChainIndex < chainCount - 1, onClick = onNextPart)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/**
 * ≡ `storyViewerReplyActionButton` (34×40 → círculo 40).
 * Con teclado: [momentsChromeGlass] Circle para contraste sobre el IME.
 */
@Composable
private fun StoryViewerReplyActionButton(
    glass: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .size(40.dp)
            .then(
                if (glass) {
                    Modifier.momentsChromeGlass(CircleShape, interactive = true)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** ≡ `saveStoryToDevice()` de Swift. */
private suspend fun saveStoryMediaToDevice(context: android.content.Context, story: Story): Boolean =
    withContext(Dispatchers.IO) {
        val isVideo = story.mediaItem.type == MediaItem.MediaType.VIDEO
        val resolver = context.contentResolver
        val mimeType = if (isVideo) "video/mp4" else "image/jpeg"
        val collection = if (isVideo) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val values = ContentValues().apply {
            put(
                MediaStore.MediaColumns.DISPLAY_NAME,
                "Moment_${System.currentTimeMillis()}${if (isVideo) ".mp4" else ".jpg"}",
            )
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    if (isVideo) "Movies/Moments" else "Pictures/Moments",
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val target = resolver.insert(collection, values) ?: return@withContext false
        try {
            URL(story.mediaItem.url).openStream().use { input ->
                resolver.openOutputStream(target)?.use { output -> input.copyTo(output) }
                    ?: error("MediaStore")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(
                    target,
                    ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                    null,
                    null,
                )
            }
            true
        } catch (_: Exception) {
            resolver.delete(target, null, null)
            false
        }
    }
