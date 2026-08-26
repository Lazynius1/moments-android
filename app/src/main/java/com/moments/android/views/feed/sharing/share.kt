package com.moments.android.views.feed.sharing

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.moments.android.R
import com.moments.android.coordinators.LegacyNavigationBridge
import com.moments.android.extensions.fromHex
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.AppUser
import com.moments.android.models.Moment
import com.moments.android.models.Point
import com.moments.android.models.StickerData
import com.moments.android.models.StickerInteractionData
import com.moments.android.models.StickerItem
import com.moments.android.models.StickerType
import com.moments.android.services.cache.ImagePrefetchManager
import com.moments.android.services.cache.UserCacheService
import com.moments.android.services.cache.VideoThumbnailCache
import com.moments.android.services.content.FeedMoment
import com.moments.android.views.shared.MomentsModalSheet
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.searchUsers
import com.moments.android.services.privacy.PrivacyService
import com.moments.android.services.social.StoryRingResolverService
import com.moments.android.services.social.StoryRingSnapshot
import com.moments.android.utilities.HapticManager
import com.moments.android.utilities.legacyPoppinsSize
import com.moments.android.views.components.LiveUsernameContent
import com.moments.android.views.components.MomentRowButton
import com.moments.android.views.components.VerifiedBadgeView
import com.moments.android.views.components.shimmer
import com.moments.android.views.creator.CreatorView
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.feed.moments.FeedMomentCardLayout
import com.moments.android.views.messaging.components.AttachmentIcon
import com.moments.android.views.messaging.components.AttachmentIconPreset
import com.moments.android.views.messaging.components.AttachmentIconView
import com.moments.android.views.messaging.components.GlassmorphicAvatar
import com.moments.android.views.messaging.core.Conversation
import com.moments.android.views.messaging.core.EnhancedMessage
import com.moments.android.views.messaging.core.MessageType
import com.moments.android.views.messaging.services.ChatService
import com.moments.android.views.messaging.services.sendSharedMomentMessage
import com.moments.android.services.messaging.DirectMessageRoute
import com.moments.android.services.messaging.MessageRequestInteractionContext
import com.moments.android.services.messaging.MessageRequestService
import com.moments.android.views.story.StoryRingAvatarView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Date


/**
 * Port de `share.swift` — ModernShareBottomSheet, picker, AddToStory, Shared DM bubbles.
 */

enum class ShareSheetViewState { Main, Messaging }

fun buildMomentShareUrl(moment: FeedMoment): String {
    if (moment.id.isBlank()) return "https://momentsapp.app/moment"
    val base = "https://momentsapp.app/moment/${moment.id}"
    return if (moment.authorId.isNotEmpty()) "$base?a=${moment.authorId}" else base
}

@Composable
fun ModernShareBottomSheet(
    moment: FeedMoment,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var viewState by remember { mutableStateOf(ShareSheetViewState.Main) }
    var showStoryCreator by remember { mutableStateOf(false) }

    fun shareExternally() {
        if (moment.id.isBlank()) return
        val freshUsername = UserCacheService.getCachedUser(moment.authorId)?.username
            ?: moment.username
        val shareText = context.getString(R.string.share_moment_by, freshUsername)
        val shareUrl = buildMomentShareUrl(moment)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "$shareText\n$shareUrl")
        }
        context.startActivity(Intent.createChooser(intent, null))
        onDismiss()
    }

    // M3 ModalBottomSheet — sin scrim/drag custom iOS (glass overlay).
    MomentsModalSheet(
        onDismissRequest = {
            if (viewState == ShareSheetViewState.Messaging) {
                viewState = ShareSheetViewState.Main
            } else {
                onDismiss()
            }
        },
        largeOnly = false,
    ) { dismiss ->
        Column(modifier.fillMaxWidth()) {
            AnimatedContent(
                targetState = viewState,
                transitionSpec = {
                    // Forward/backward within sheet — M3 effects (fade+slide), not iOS scale pop.
                    if (targetState == ShareSheetViewState.Messaging) {
                        (slideInHorizontally { it } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it / 4 } + fadeOut())
                    } else {
                        (slideInHorizontally { -it / 4 } + fadeIn()) togetherWith
                            (slideOutHorizontally { it } + fadeOut())
                    }
                },
                label = "shareSheetState",
            ) { state ->
                when (state) {
                    ShareSheetViewState.Main -> MainActionsView(
                        moment = moment,
                        onSendMessage = { viewState = ShareSheetViewState.Messaging },
                        onAddToStory = { showStoryCreator = true },
                        onExternalShare = { shareExternally() },
                    )
                    ShareSheetViewState.Messaging -> ModernShareSheet(
                        moment = moment,
                        onBack = { viewState = ShareSheetViewState.Main },
                        onDismiss = dismiss,
                    )
                }
            }
        }
    }

    if (showStoryCreator) {
        AddToStoryView(
            moment = moment,
            onDismiss = {
                showStoryCreator = false
                onDismiss()
            },
        )
    }
}

/** Compat call sites antiguos hasta cablear solo `onDismiss`. */
@Composable
fun ModernShareBottomSheet(
    moment: FeedMoment,
    onDismiss: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onSendMessage: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onAddToStory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModernShareBottomSheet(moment = moment, onDismiss = onDismiss, modifier = modifier)
}

// MARK: - Main Actions View

/** Port de `MainActionsView` (share.swift) — también usado por ContextMenu. */
@Composable
fun ShareMainActionsView(
    moment: FeedMoment,
    onSendMessage: () -> Unit,
    onAddToStory: () -> Unit,
    onExternalShare: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val colors = rememberAdaptiveShareColors()

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 0.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StoryRingAvatarView(
                userId = moment.authorId,
                size = 44.dp,
                lineWidth = 2.4.dp,
                onTap = {
                    if (moment.authorId.isNotEmpty()) {
                        LegacyNavigationBridge.profile(moment.authorId)
                    }
                },
            )

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.share_moment_title),
                    color = colors.primary,
                    fontSize = with(density) { legacyPoppinsSize(context, 18).toSp() },
                    fontWeight = FontWeight.SemiBold,
                )
                LiveUsernameContent(
                    userId = moment.authorId,
                    fallbackUsername = moment.username,
                ) { username ->
                    Text(
                        text = stringResource(R.string.share_moment_from, username),
                        color = colors.secondary,
                        fontSize = with(density) { legacyPoppinsSize(context, 14).toSp() },
                    )
                }
            }
        }

        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ShareActionButton(
                title = stringResource(R.string.messaging_send_message),
                subtitle = stringResource(R.string.context_menu_share_moment_subtitle),
                usesStoryRingIcon = false,
                usesShareAttachmentIcon = false,
                leading = {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(20.dp),
                    )
                },
                onClick = onSendMessage,
            )
            ShareActionButton(
                title = stringResource(R.string.share_add_to_story),
                subtitle = stringResource(R.string.creator_story_subtitle),
                usesStoryRingIcon = true,
                usesShareAttachmentIcon = false,
                leading = { StoryAddGlyph(size = 24.dp) },
                onClick = onAddToStory,
            )
            ShareActionButton(
                title = stringResource(R.string.context_menu_copy_link),
                subtitle = stringResource(R.string.context_menu_copy_link_subtitle),
                usesStoryRingIcon = false,
                usesShareAttachmentIcon = true,
                leading = {
                    AttachmentIconView(
                        icon = AttachmentIcon.SHARE,
                        preset = AttachmentIconPreset.SHARE_SHEET_ROW,
                        tintColor = colors.primary,
                    )
                },
                onClick = onExternalShare,
            )
        }
    }
}

@Composable
private fun MainActionsView(
    moment: FeedMoment,
    onSendMessage: () -> Unit,
    onAddToStory: () -> Unit,
    onExternalShare: () -> Unit,
) {
    ShareMainActionsView(
        moment = moment,
        onSendMessage = onSendMessage,
        onAddToStory = onAddToStory,
        onExternalShare = onExternalShare,
    )
}

@Composable
private fun ShareActionButton(
    title: String,
    subtitle: String,
    usesStoryRingIcon: Boolean,
    usesShareAttachmentIcon: Boolean,
    leading: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    @Suppress("UNUSED_PARAMETER")
    val unusedFlags = usesStoryRingIcon || usesShareAttachmentIcon
    val context = LocalContext.current
    val density = LocalDensity.current
    val colors = rememberAdaptiveShareColors()

    MomentRowButton(action = onClick) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                leading()
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    color = colors.primary,
                    fontSize = with(density) { legacyPoppinsSize(context, 16).toSp() },
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    color = colors.secondary,
                    fontSize = with(density) { legacyPoppinsSize(context, 13).toSp() },
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = colors.secondary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

// MARK: - StoryAddGlyph

@Composable
private fun StoryAddGlyph(size: Dp) {
    val lineWidth = 2.1.dp
    val gapAngle = 16.0
    val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    var snapshot by remember {
        mutableStateOf(
            StoryRingSnapshot(
                hasStory = false,
                hasUnseenStory = false,
                storyCount = 0,
                storyViewedStatus = emptyList(),
                storyAudiences = emptyList(),
            ),
        )
    }

    LaunchedEffect(uid) {
        if (uid.isEmpty()) return@LaunchedEffect
        snapshot = StoryRingResolverService.resolve(
            viewerId = uid,
            authorId = uid,
        )
    }

    val segmentCount = maxOf(snapshot.storyCount + 1, 1)
    val ringBrush = Brush.linearGradient(
        listOf(Color(0xFF007AFF), Color(0xFFAF52DE), Color(0xFFFF2D55)),
    )
    val isDark = isSystemInDarkTheme()

    Box(
        Modifier
            .size(size)
            .rotate(-90f),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val stroke = Stroke(width = lineWidth.toPx(), cap = StrokeCap.Round)
            if (segmentCount == 1) {
                drawCircle(brush = ringBrush, style = stroke)
            } else {
                val segmentAngle = 360.0 / segmentCount
                for (index in 0 until segmentCount) {
                    val start = (index * segmentAngle + gapAngle / 2).toFloat()
                    val sweep = maxOf(segmentAngle - gapAngle, 1.0).toFloat()
                    drawArc(
                        brush = ringBrush,
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = false,
                        style = stroke,
                    )
                }
            }
        }
        Box(
            Modifier
                .size(size * 0.44f)
                .background(
                    (if (isDark) Color.Black else Color.White).copy(0.9f),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.size(size * 0.34f),
                tint = Color.Unspecified,
            )
            // Gradient tint via brush not on Icon easily — use primary blue as fallback;
            // Compose Icon doesn't take Brush; approximate with purple mid.
        }
        // Plus with gradient: draw over center
        Icon(
            Icons.Filled.Add,
            contentDescription = null,
            modifier = Modifier.size(size * 0.34f),
            tint = Color(0xFFAF52DE),
        )
    }
}

// MARK: - Picker de destinatarios reutilizable (share / reenviar)

private enum class ShareRecipientsFilter { None, Favorites, Recents }

/**
 * Port de `ShareRecipientsPickerSheet` — titleKey iOS → [title] resuelto.
 * Público para `ChatMessageForwardSheet`.
 */
@Composable
fun ShareRecipientsPickerSheet(
    title: String,
    subtitle: String? = null,
    showsBackButton: Boolean = true,
    onBack: (() -> Unit)? = null,
    flexibleListHeight: Boolean = false,
    onDismiss: () -> Unit,
    onSend: (Set<String>, List<Conversation>) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val colors = rememberAdaptiveShareColors()
    val firestore = remember { FirestoreService() }
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    val teal = Color.fromHex("00A896")

    var searchText by remember { mutableStateOf("") }
    var selectedUsers by remember { mutableStateOf<Set<String>>(emptySet()) }
    var conversations by remember { mutableStateOf<List<Conversation>>(emptyList()) }
    var globalSearchResults by remember { mutableStateOf<List<AppUser>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var activeFilter by remember { mutableStateOf(ShareRecipientsFilter.None) }

    val filteredConversations = remember(conversations, activeFilter, searchText) {
        var base = conversations
        if (activeFilter == ShareRecipientsFilter.Favorites) {
            base = conversations.filter { it.isPinned == true || it.isPinned(uid) }
        }
        if (searchText.isEmpty()) base
        else base.filter {
            it.otherParticipantUsername?.contains(searchText, ignoreCase = true) == true
        }
    }

    DisposableEffect(uid) {
        if (uid.isNullOrEmpty()) {
            isLoading = false
            return@DisposableEffect onDispose { }
        }
        ChatService.fetchConversations(uid) { result ->
            result.onSuccess { fetched ->
                conversations = fetched
                isLoading = false
            }.onFailure {
                isLoading = false
            }
        }
        onDispose { }
    }

    LaunchedEffect(searchText) {
        if (searchText.length < 3) {
            globalSearchResults = emptyList()
            return@LaunchedEffect
        }
        val users = runCatching { firestore.searchUsers(searchText) }.getOrDefault(emptyList())
        val localIds = conversations.map { it.otherParticipantId }.toSet()
        globalSearchResults = users.filter { it.id !in localIds }
    }

    fun toggleUserSelection(userId: String) {
        selectedUsers = if (userId in selectedUsers) selectedUsers - userId else selectedUsers + userId
        HapticManager.shared.lightImpact()
    }

    Column(
        Modifier
            .fillMaxWidth()
            .then(if (flexibleListHeight) Modifier.fillMaxSize() else Modifier),
    ) {
        // Header
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 0.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Solo back dentro del sheet; dismiss = drag handle / swipe (sin chevron/X).
            if (showsBackButton && onBack != null) {
                Box(
                    Modifier
                        .size(36.dp)
                        .momentsChromeGlass(CircleShape, interactive = true)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = null,
                        tint = colors.primary,
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = colors.primary,
                    fontSize = with(density) { legacyPoppinsSize(context, 18).toSp() },
                    fontWeight = FontWeight.SemiBold,
                )
                if (!subtitle.isNullOrEmpty()) {
                    Text(
                        subtitle,
                        color = colors.secondary,
                        fontSize = with(density) { legacyPoppinsSize(context, 14).toSp() },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Search
        Row(
            Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
                .fillMaxWidth()
                .height(56.dp)
                .momentsChromeGlass(RoundedCornerShape(percent = 50), interactive = true)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Filled.Search, null, tint = colors.secondary, modifier = Modifier.size(16.dp))
            BasicTextField(
                value = searchText,
                onValueChange = { searchText = it },
                singleLine = true,
                cursorBrush = SolidColor(colors.primary),
                textStyle = TextStyle(
                    color = colors.primary,
                    fontSize = with(density) { legacyPoppinsSize(context, 16).toSp() },
                ),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (searchText.isEmpty()) {
                        Text(
                            stringResource(R.string.share_search_placeholder),
                            color = colors.secondary,
                            fontSize = with(density) { legacyPoppinsSize(context, 16).toSp() },
                        )
                    }
                    inner()
                },
            )
        }

        // Filters
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ShareFilterChip(
                icon = Icons.Filled.Star,
                title = stringResource(R.string.share_favorites),
                color = teal,
                isSelected = activeFilter == ShareRecipientsFilter.Favorites,
                onClick = {
                    activeFilter = if (activeFilter == ShareRecipientsFilter.Favorites) {
                        ShareRecipientsFilter.None
                    } else {
                        ShareRecipientsFilter.Favorites
                    }
                },
            )
            ShareFilterChip(
                icon = Icons.Filled.AccessTime,
                title = stringResource(R.string.share_recents),
                color = Color(0xFF007AFF),
                isSelected = activeFilter == ShareRecipientsFilter.Recents,
                onClick = {
                    activeFilter = if (activeFilter == ShareRecipientsFilter.Recents) {
                        ShareRecipientsFilter.None
                    } else {
                        ShareRecipientsFilter.Recents
                    }
                },
            )
        }

        // List
        Box(
            Modifier
                .fillMaxWidth()
                .then(
                    if (flexibleListHeight) Modifier.weight(1f)
                    else Modifier.heightIn(max = 350.dp),
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 10.dp, bottom = 8.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                when {
                    isLoading -> PeopleSkeletonGrid()
                    else -> {
                        if (filteredConversations.isNotEmpty()) {
                            ShareRecipientsGrid(
                                count = filteredConversations.size,
                            ) { index ->
                                val conversation = filteredConversations[index]
                                PersonCell(
                                    conversation = conversation,
                                    isSelected = conversation.otherParticipantId in selectedUsers,
                                    animationDelayMs = index * 50L,
                                    onTap = { toggleUserSelection(conversation.otherParticipantId) },
                                )
                            }
                        }

                        if (globalSearchResults.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    stringResource(R.string.share_search_global_results),
                                    color = colors.secondary,
                                    fontSize = with(density) { legacyPoppinsSize(context, 14).toSp() },
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                                ShareRecipientsGrid(count = globalSearchResults.size) { index ->
                                    val user = globalSearchResults[index]
                                    GlobalUserCell(
                                        user = user,
                                        isSelected = user.id in selectedUsers,
                                        onTap = { toggleUserSelection(user.id) },
                                    )
                                }
                            }
                        }

                        if (filteredConversations.isEmpty() && globalSearchResults.isEmpty() && searchText.isNotEmpty()) {
                            EmptySearchState()
                        }

                        if (filteredConversations.isEmpty() && searchText.isEmpty() &&
                            activeFilter == ShareRecipientsFilter.Favorites
                        ) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 40.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    Icons.Filled.StarBorder,
                                    null,
                                    tint = colors.secondary,
                                    modifier = Modifier.size(40.dp),
                                )
                                Text(
                                    stringResource(R.string.share_favorites_empty),
                                    color = colors.secondary,
                                    fontSize = with(density) { legacyPoppinsSize(context, 16).toSp() },
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }
        }

        SendActionBottomBar(
            selectedCount = selectedUsers.size,
            onSend = { onSend(selectedUsers, conversations) },
        )
    }
}

/** Grid 4 columnas sin LazyVerticalGrid anidado en scroll (altura intrínseca). */
@Composable
private fun ShareRecipientsGrid(
    count: Int,
    item: @Composable (index: Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        var index = 0
        while (index < count) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                repeat(4) { col ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                        if (index + col < count) item(index + col)
                    }
                }
            }
            index += 4
        }
    }
}

// MARK: - Modern Share Sheet

@Composable
fun ModernShareSheet(
    moment: FeedMoment,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val author = UserCacheService.getCachedUser(moment.authorId)?.username ?: moment.username
    var deliveryFeedback by remember { mutableStateOf<String?>(null) }

    ShareRecipientsPickerSheet(
        title = stringResource(R.string.share_send_to),
        subtitle = stringResource(R.string.share_moment_by, author),
        showsBackButton = true,
        onBack = onBack,
        onDismiss = onDismiss,
        onSend = { selectedUsers, _ ->
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return@ShareRecipientsPickerSheet
            if (moment.id.isBlank()) return@ShareRecipientsPickerSheet
            val freshUsername = UserCacheService.getCachedUser(moment.authorId)?.username ?: moment.username
            val shareText = context.getString(R.string.share_moment_by, freshUsername)
            val momentUrl = buildMomentShareUrl(moment)
            val momentModel = moment.toShareMoment()
            scope.launch {
                val coordinator = MessageRequestService()
                val failures = mutableListOf<String>()
                for (userId in selectedUsers) {
                    if (userId.isBlank()) {
                        failures += context.getString(R.string.messaging_error_invalid_recipient)
                        continue
                    }
                    runCatching {
                        val interaction = MessageRequestInteractionContext(
                            kind = MessageRequestInteractionContext.Kind.SHARE_MOMENT,
                            sharedContentId = moment.id,
                            sharedContentOwnerId = moment.authorId,
                        )
                        when (val route = coordinator.resolveRoute(userId, interaction)) {
                            is DirectMessageRoute.Conversation -> route.id
                            is DirectMessageRoute.ConversationDraft -> coordinator.activateConversationDraft(userId, route.threadId)
                            is DirectMessageRoute.IncomingRequest -> coordinator.acceptIncomingThread(route.threadId).conversationId
                            is DirectMessageRoute.OutgoingRequest -> {
                                coordinator.appendRequestMessage(
                                    receiverId = userId,
                                    text = shareText,
                                    messageType = MessageType.SHARED_MOMENT,
                                    interaction = interaction,
                                )
                                null
                            }
                        }?.let { conversationId ->
                            ChatService.sendSharedMomentMessage(
                                conversationId = conversationId,
                                senderId = currentUserId,
                                moment = momentModel,
                                shareText = shareText,
                                momentUrl = momentUrl,
                            ).getOrThrow()
                        }
                    }.onFailure { failures += it.localizedMessage ?: context.getString(R.string.common_error) }
                }
                if (selectedUsers.isNotEmpty() && failures.isEmpty()) {
                    HapticManager.shared.success()
                    onDismiss()
                } else {
                    deliveryFeedback = failures.firstOrNull() ?: context.getString(R.string.common_error)
                }
            }
        },
    )
    deliveryFeedback?.let { message ->
        AlertDialog(
            onDismissRequest = { deliveryFeedback = null },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { deliveryFeedback = null }) { Text(stringResource(R.string.common_ok)) } },
        )
    }
}

private fun FeedMoment.toShareMoment(): Moment = Moment(
    id = id,
    authorId = authorId,
    username = username,
    content = content,
    imagePath = imagePath ?: thumbnailUrl ?: mediaItems.firstOrNull()?.url,
    videoUrl = mediaItems.firstOrNull { it.type.equals("video", ignoreCase = true) }?.url,
    timestamp = Date(timestamp),
    commentCount = commentCount,
    profileImagePath = profileImagePath,
    location = location,
    locationCoordinate = locationCoordinate,
    audience = audience,
    aspectRatio = aspectRatio,
    customListId = customListId,
    thumbnailUrl = thumbnailUrl,
    videoDuration = videoDuration,
    isArchived = isArchived,
    hasHiddenLayers = hasHiddenLayers,
    hiddenLayerCount = hiddenLayerCount,
    disableComments = disableComments,
    hideLikeCounts = hideLikeCounts,
    allowSharing = allowSharing,
)

// MARK: - Filter Chip

@Composable
private fun ShareFilterChip(
    icon: ImageVector,
    title: String,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val colors = rememberAdaptiveShareColors()
    Row(
        Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(if (isSelected) color.copy(0.3f) else Color.White.copy(0.1f))
            .border(
                width = 1.dp,
                color = if (isSelected) color else Color.White.copy(0.2f),
                shape = RoundedCornerShape(percent = 50),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, tint = if (isSelected) color else colors.primary, modifier = Modifier.size(14.dp))
        Text(
            title,
            color = if (isSelected) color else colors.primary,
            fontSize = with(density) { legacyPoppinsSize(context, 13).toSp() },
            fontWeight = FontWeight.Medium,
        )
    }
}

// MARK: - Send Action Bottom Bar

@Composable
private fun SendActionBottomBar(
    selectedCount: Int,
    onSend: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val colors = rememberAdaptiveShareColors()
    val teal = Color.fromHex("00A896")
    val enabled = selectedCount > 0

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .height(54.dp)
            .clip(RoundedCornerShape(27.dp))
            .background(
                if (enabled) {
                    Brush.horizontalGradient(listOf(teal, teal.copy(0.8f)))
                } else {
                    Brush.horizontalGradient(listOf(colors.primary.copy(0.05f), colors.primary.copy(0.05f)))
                },
            )
            .clickable(enabled = enabled, onClick = onSend),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                null,
                tint = if (enabled) Color.White else colors.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = if (enabled) {
                    stringResource(R.string.share_send_to_count, selectedCount)
                } else {
                    stringResource(R.string.share_select_contacts)
                },
                color = if (enabled) Color.White else colors.primary,
                fontSize = with(density) { legacyPoppinsSize(context, 16).toSp() },
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// MARK: - Person Cell

@Composable
private fun PersonCell(
    conversation: Conversation,
    isSelected: Boolean,
    animationDelayMs: Long,
    onTap: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val colors = rememberAdaptiveShareColors()
    val teal = Color.fromHex("00A896")
    var isVisible by remember { mutableStateOf(false) }
    val appearScale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.8f,
        animationSpec = tween(durationMillis = 600, delayMillis = animationDelayMs.toInt()),
        label = "personCellScale",
    )
    val appearAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 600, delayMillis = animationDelayMs.toInt()),
        label = "personCellAlpha",
    )
    LaunchedEffect(Unit) { isVisible = true }

    Column(
        Modifier
            .scale(appearScale)
            .graphicsLayer { this.alpha = appearAlpha }
            .semantics { selected = isSelected }
            .clickable(onClick = onTap),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            val image = conversation.otherParticipantProfileImagePath
            if (!image.isNullOrBlank()) {
                AsyncImage(
                    model = image,
                    contentDescription = conversation.otherParticipantUsername,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(68.dp)
                        .shadow(
                            elevation = if (isSelected) 8.dp else 4.dp,
                            shape = CircleShape,
                            clip = false,
                            ambientColor = if (isSelected) teal.copy(0.3f) else Color.Black.copy(0.2f),
                            spotColor = if (isSelected) teal.copy(0.3f) else Color.Black.copy(0.2f),
                        )
                        .clip(CircleShape)
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            brush = if (isSelected) {
                                Brush.linearGradient(listOf(teal, teal.copy(0.7f)))
                            } else {
                                Brush.linearGradient(listOf(Color.White.copy(0.1f), Color.White.copy(0.1f)))
                            },
                            shape = CircleShape,
                        ),
                )
            } else {
                Box(
                    Modifier
                        .size(68.dp)
                        .momentsChromeGlass(CircleShape, interactive = false)
                        .border(1.dp, Color.White.copy(0.1f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Person, null, tint = Color.White.copy(0.6f), modifier = Modifier.size(28.dp))
                }
            }
            if (isSelected) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .size(24.dp)
                        .shadow(4.dp, CircleShape, clip = false, ambientColor = teal.copy(0.4f), spotColor = teal.copy(0.4f))
                        .background(Brush.linearGradient(listOf(teal, teal.copy(0.8f))), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
                }
            }
        }
        Text(
            text = conversation.otherParticipantUsername ?: "Usuario",
            color = colors.primary,
            fontSize = with(density) { legacyPoppinsSize(context, 12).toSp() },
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

// MARK: - Global User Cell

@Composable
private fun GlobalUserCell(
    user: AppUser,
    isSelected: Boolean,
    onTap: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val colors = rememberAdaptiveShareColors()
    val teal = Color.fromHex("00A896")

    Column(
        Modifier
            .semantics { selected = isSelected }
            .clickable(onClick = onTap),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (!user.profileImagePath.isNullOrBlank()) {
                AsyncImage(
                    model = user.profileImagePath,
                    contentDescription = user.username,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .border(
                            width = 2.dp,
                            color = if (isSelected) teal else Color.White.copy(0.1f),
                            shape = CircleShape,
                        ),
                )
            } else {
                Box(
                    Modifier
                        .size(60.dp)
                        .background(Color.White.copy(0.1f), CircleShape)
                        .border(2.dp, if (isSelected) teal else Color.White.copy(0.1f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Person, null, tint = Color.White.copy(0.6f))
                }
            }
            if (isSelected) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = (-2).dp)
                        .size(20.dp)
                        .background(teal, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(10.dp))
                }
            }
        }
        Text(
            user.username,
            color = colors.primary,
            fontSize = with(density) { legacyPoppinsSize(context, 11).toSp() },
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// MARK: - People Skeleton Grid / Person Skeleton Cell

@Composable
private fun PeopleSkeletonGrid() {
    ShareRecipientsGrid(count = 8) {
        PersonSkeletonCell()
    }
}

@Composable
private fun PersonSkeletonCell() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(68.dp)
                .momentsChromeGlass(CircleShape, interactive = false)
                .border(1.dp, Color.White.copy(0.1f), CircleShape)
                .shimmer(isAnimating = true),
        )
        Box(
            Modifier
                .width(60.dp)
                .height(12.dp)
                .momentsChromeGlass(RoundedCornerShape(6.dp), interactive = false)
                .shimmer(isAnimating = true),
        )
    }
}

// MARK: - Empty Search State

@Composable
private fun EmptySearchState() {
    val context = LocalContext.current
    val density = LocalDensity.current
    val colors = rememberAdaptiveShareColors()
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Filled.Search, null, tint = colors.secondary, modifier = Modifier.size(40.dp))
        Text(
            stringResource(R.string.share_search_no_results),
            color = colors.secondary,
            fontSize = with(density) { legacyPoppinsSize(context, 16).toSp() },
            fontWeight = FontWeight.Medium,
        )
    }
}

// MARK: - Add to Story View

@Composable
fun AddToStoryView(
    moment: FeedMoment,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showCreatorView by remember { mutableStateOf(false) }
    var createdSticker by remember { mutableStateOf<StickerData?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(moment.id) {
        val prepared = prepareShareMomentSticker(context, moment)
        prepared.onSuccess { sticker ->
            createdSticker = sticker
            delay(100)
            showCreatorView = true
        }.onFailure { err ->
            errorMessage = err.message
                ?: context.getString(R.string.errors_sticker_generation_failed)
        }
    }

    // ≡ mismo Dialog que TabBarView → CreatorView / StoryCamera (insets + canvas)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        val creatorSurface = rememberAdaptiveColors().surfaceBackground
        Box(Modifier.fillMaxSize().background(creatorSurface)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.navigationBars)),
            ) {
                if (showCreatorView && createdSticker != null) {
                    CreatorView(
                        showCreatorView = true,
                        onShowCreatorViewChange = { visible -> if (!visible) onDismiss() },
                        isCreatingStory = true,
                        onIsCreatingStoryChange = {},
                        initialSticker = createdSticker,
                        initialMedia = null,
                        openInStoryMode = false,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        PreparingStoryOverlay(
                            errorMessage = errorMessage,
                            onCancel = onDismiss,
                        )
                    }
                }
            }
        }
    }
}

/**
 * ≡ iOS `preFetchAndRender` + `performFinalRender` (ShareMomentSticker renderClean → StickerItem).
 */
private suspend fun prepareShareMomentSticker(
    context: Context,
    moment: FeedMoment,
): Result<StickerData> = withContext(Dispatchers.IO) {
    val primaryMedia = moment.visibleMediaItems.firstOrNull()
    val contentUrl = when {
        primaryMedia?.type.equals("video", ignoreCase = true) ->
            primaryMedia?.thumbnailUrl?.takeIf(String::isNotBlank) ?: primaryMedia?.url
        primaryMedia != null -> primaryMedia.url
        else -> moment.imagePath ?: moment.thumbnailUrl
    }
    if (contentUrl.isNullOrBlank()) {
        return@withContext Result.failure(
            IllegalStateException(context.getString(R.string.errors_moment_image_unavailable)),
        )
    }

    var profilePath: String? = moment.profileImagePath
    runCatching {
        val snap = FirebaseFirestore.getInstance()
            .collection("users")
            .document(moment.authorId)
            .get()
            .await()
        snap.getString("profileImagePath")?.takeIf { it.isNotBlank() }?.let { profilePath = it }
    }

    val urlsToPrefetch = buildList {
        add(contentUrl)
        profilePath?.let { add(it) }
    }
    ImagePrefetchManager.initialize(context)
    ImagePrefetchManager.prefetch(urlsToPrefetch)

    val contentBitmap = loadShareBitmap(context, contentUrl)
        ?: return@withContext Result.failure(
            IllegalStateException(context.getString(R.string.errors_sticker_generation_failed)),
        )
    // Profile se prefetchea como iOS; con renderClean no entra en el bitmap final.
    if (!profilePath.isNullOrBlank()) {
        loadShareBitmap(context, profilePath)
    }

    val videoUrl = primaryMedia
        ?.takeIf { it.type.equals("video", ignoreCase = true) }
        ?.url
    val stickerBitmap = renderCleanShareMomentStickerBitmap(
        content = contentBitmap,
        aspectRatio = primaryMedia?.aspectRatio ?: moment.aspectRatio,
    )

    val interaction = StickerInteractionData(
        username = moment.username,
        userId = moment.authorId,
        caption = moment.content.takeIf { it.isNotBlank() },
        styleVariant = 0,
        cardLayoutVariant = 0,
        profileImagePath = profilePath,
        sharedMediaPath = primaryMedia
            ?.takeIf { it.type.equals("image", ignoreCase = true) }
            ?.url,
        momentId = moment.id,
        mediaCount = moment.visibleMediaItems.size.coerceAtLeast(1),
    )
    val stickerItem = StickerItem.create(
        image = stickerBitmap,
        position = Point(0.5, 0.5),
        type = StickerType.SHARE_MOMENT,
        interactionData = interaction,
        videoURL = videoUrl,
    )
    Result.success(StickerData.from(stickerItem))
}

private suspend fun loadShareBitmap(context: Context, url: String): Bitmap? =
    withContext(Dispatchers.IO) {
        val request = ImageRequest.Builder(context)
            .data(url)
            .allowHardware(false)
            .build()
        val result = context.imageLoader.execute(request)
        (result as? SuccessResult)?.drawable?.toBitmap()
    }

/** Espejo de `ShareMomentSticker` con `renderClean: true` → bitmap para CreatorView. */
private fun renderCleanShareMomentStickerBitmap(
    content: Bitmap,
    aspectRatio: String?,
): Bitmap {
    val heightDp = shareMomentStickerHeight(aspectRatio, content)
    val widthPx = 260
    val heightPx = heightDp.value.toInt().coerceAtLeast(1)
    val corner = FeedMomentCardLayout.mediaCornerRadius.value
    val strokeW = 1.2f

    val out = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(out)
    val roundRect = android.graphics.RectF(0f, 0f, widthPx.toFloat(), heightPx.toFloat())
    val clip = android.graphics.Path().apply {
        addRoundRect(roundRect, corner, corner, android.graphics.Path.Direction.CW)
    }
    canvas.clipPath(clip)
    // ≡ ShareMomentSticker background (sólido + glass tint)
    canvas.drawColor(android.graphics.Color.parseColor("#1A1A1A"))
    val glassTint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb((0.08f * 255).toInt(), 255, 255, 255)
    }
    canvas.drawRoundRect(roundRect, corner, corner, glassTint)

    val scale = maxOf(widthPx.toFloat() / content.width, heightPx.toFloat() / content.height)
    val drawW = content.width * scale
    val drawH = content.height * scale
    val left = (widthPx - drawW) / 2f
    val top = (heightPx - drawH) / 2f
    canvas.drawBitmap(
        content,
        null,
        android.graphics.RectF(left, top, left + drawW, top + drawH),
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
    )

    // ≡ overlay stroke LinearGradient white 0.4 → 0.05 → 0.2
    val inset = strokeW / 2f
    val strokeRect = android.graphics.RectF(
        inset,
        inset,
        widthPx - inset,
        heightPx - inset,
    )
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeW
        shader = android.graphics.LinearGradient(
            0f,
            0f,
            widthPx.toFloat(),
            heightPx.toFloat(),
            intArrayOf(
                android.graphics.Color.argb((0.4f * 255).toInt(), 255, 255, 255),
                android.graphics.Color.argb((0.05f * 255).toInt(), 255, 255, 255),
                android.graphics.Color.argb((0.2f * 255).toInt(), 255, 255, 255),
            ),
            floatArrayOf(0f, 0.5f, 1f),
            android.graphics.Shader.TileMode.CLAMP,
        )
    }
    canvas.drawRoundRect(strokeRect, corner, corner, strokePaint)
    return out
}

// MARK: - ✅ Preparing Story Overlay (Shared)

@Composable
fun PreparingStoryOverlay(
    errorMessage: String?,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(0.4f))
            .clip(RoundedCornerShape(32.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(horizontal = 24.dp),
        ) {
            if (errorMessage != null) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = Color.Yellow,
                    modifier = Modifier.size(50.dp),
                )
                Text(
                    errorMessage,
                    color = Color.White,
                    fontSize = with(density) { legacyPoppinsSize(context, 16).toSp() },
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.share_cancel),
                    color = Color.White,
                    fontSize = with(density) { legacyPoppinsSize(context, 15).toSp() },
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(Color.White.copy(0.1f))
                        .clickable(onClick = onCancel)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                )
            } else {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(48.dp),
                )
                Text(
                    stringResource(R.string.share_preparing),
                    color = Color.White,
                    fontSize = with(density) { legacyPoppinsSize(context, 16).toSp() },
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

// MARK: - Shared DM preview card (historia + momento)

object SharedDMMediaCardMetrics {
    val width = 200.dp
    val height = 280.dp
    val cornerRadius = 12.dp
}

// MARK: - Tarjeta compartida (cabecera arriba · media limpia · caption debajo)

object SharedDMPostCardMetrics {
    val width = 248.dp
    val defaultMediaHeight = 248.dp
    val cornerRadius = 12.dp
}

/** ≡ iOS `parseSharedAspectRatio` — ("9:16", "4:5", "1.0") → width/height. */
fun parseSharedAspectRatio(raw: String?): Float {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty()) return 1f
    if (trimmed.contains(":")) {
        val parts = trimmed.split(":")
        if (parts.size == 2) {
            val w = parts[0].toFloatOrNull()
            val h = parts[1].toFloatOrNull()
            if (w != null && h != null && h > 0f) return w / h
        }
    }
    return trimmed.toFloatOrNull()?.takeIf { it > 0f } ?: 1f
}

@Composable
fun SharedDMPostCard(
    authorId: String?,
    authorName: String?,
    isVideo: Boolean,
    caption: String?,
    modifier: Modifier = Modifier,
    useStoryRing: Boolean = false,
    aspectRatio: Float = 1f,
    captionAuthor: String? = null,
    media: @Composable () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val cardBackground = if (isDark) {
        Color.fromHex("FAF9F6").copy(0.14f)
    } else {
        Color.fromHex("0B1215").copy(0.07f)
    }
    val primaryText = if (isDark) Color.fromHex("FAF9F6") else Color.fromHex("0B1215")
    val mediaHeight = remember(aspectRatio) {
        if (aspectRatio <= 0.01f) {
            SharedDMPostCardMetrics.defaultMediaHeight
        } else {
            val raw = SharedDMPostCardMetrics.width / aspectRatio
            raw.coerceIn(
                SharedDMPostCardMetrics.width * 0.6f,
                SharedDMPostCardMetrics.width * 1.25f,
            )
        }
    }

    Column(
        modifier
            .width(SharedDMPostCardMetrics.width)
            .clip(RoundedCornerShape(SharedDMPostCardMetrics.cornerRadius))
            .background(cardBackground),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!authorId.isNullOrBlank()) {
                if (useStoryRing) {
                    StoryRingAvatarView(
                        userId = authorId,
                        size = 28.dp,
                        lineWidth = 1.8.dp,
                        showBaseStroke = true,
                        baseStrokeColor = cardBackground,
                        baseStrokeWidth = 1.5.dp,
                    )
                } else {
                    GlassmorphicAvatar(userId = authorId, modifier = Modifier.size(28.dp))
                }
            }
            if (!authorName.isNullOrBlank()) {
                Text(
                    authorName,
                    color = primaryText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            if (!authorId.isNullOrBlank()) {
                VerifiedBadgeView(userId = authorId, size = 13.dp)
            }
            Spacer(Modifier.weight(1f))
        }

        Box(
            Modifier
                .width(SharedDMPostCardMetrics.width)
                .height(mediaHeight)
                .background(Color.Black)
                .clip(RoundedCornerShape(0.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.fillMaxSize()) { media() }
            if (isVideo) {
                SharedDMCenteredPlayOverlay()
            }
        }

        if (!caption.isNullOrBlank()) {
            val captionAnnotated = buildAnnotatedString {
                if (!captionAuthor.isNullOrBlank()) {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                        append(captionAuthor)
                    }
                    append(" ")
                }
                append(caption)
            }
            Text(
                captionAnnotated,
                color = primaryText,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}

fun Modifier.sharedDMPreviewCardChrome(): Modifier = this
    .clip(RoundedCornerShape(SharedDMMediaCardMetrics.cornerRadius))
    .border(0.5.dp, Color.White.copy(0.2f), RoundedCornerShape(SharedDMMediaCardMetrics.cornerRadius))

@Composable
fun SharedDMPreviewCardSkeleton(modifier: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme()
    val cardBackground = if (isDark) {
        Color.fromHex("FAF9F6").copy(0.14f)
    } else {
        Color.fromHex("0B1215").copy(0.07f)
    }
    val placeholderFill = if (isDark) Color.White.copy(0.10f) else Color.Black.copy(0.07f)
    Column(
        modifier
            .width(SharedDMPostCardMetrics.width)
            .clip(RoundedCornerShape(SharedDMPostCardMetrics.cornerRadius))
            .background(cardBackground)
            .border(
                0.5.dp,
                if (isDark) Color.White.copy(0.10f) else Color.Black.copy(0.06f),
                RoundedCornerShape(SharedDMPostCardMetrics.cornerRadius),
            ),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(26.dp).clip(CircleShape).background(placeholderFill))
            Box(
                Modifier
                    .width(90.dp)
                    .height(11.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(placeholderFill),
            )
            Spacer(Modifier.weight(1f))
        }
        Box(
            Modifier
                .width(SharedDMPostCardMetrics.width)
                .height(SharedDMPostCardMetrics.defaultMediaHeight)
                .background(placeholderFill),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                color = Color.Gray.copy(0.7f),
                strokeWidth = 2.dp,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
fun SharedDMPreviewAuthorRow(
    authorId: String?,
    authorName: String?,
    modifier: Modifier = Modifier,
    useStoryRing: Boolean = true,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!authorId.isNullOrBlank()) {
            if (useStoryRing) {
                StoryRingAvatarView(
                    userId = authorId,
                    size = 24.dp,
                    lineWidth = 1.8.dp,
                    showBaseStroke = false,
                )
            } else {
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Person, null, tint = Color.White, modifier = Modifier.size(12.dp))
                }
            }
        }
        if (!authorName.isNullOrBlank()) {
            // Un solo weight: el Spacer extra partía el ancho a la mitad y truncaba a "l..."
            // en tarjetas estrechas (story share ~140dp). Paridad con iOS (Spacer sin flex).
            Text(
                authorName,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        if (!authorId.isNullOrBlank()) {
            VerifiedBadgeView(userId = authorId, size = 12.dp)
        }
    }
}

@Composable
fun SharedDMPreviewBottomGradient(modifier: Modifier = Modifier) {
    Box(
        modifier.background(
            Brush.verticalGradient(
                listOf(Color.Transparent, Color.Black.copy(0.4f), Color.Black.copy(0.8f)),
            ),
        ),
    )
}

@Composable
fun SharedDMCenteredPlayOverlay(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(44.dp)
            .shadow(6.dp, CircleShape, ambientColor = Color.Black.copy(0.35f), spotColor = Color.Black.copy(0.35f))
            .clip(CircleShape)
            .background(Color.White.copy(0.22f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp).offset(x = 2.dp),
        )
    }
}

@Composable
fun SharedDMUnavailablePreviewCard(
    title: String,
    message: String,
    icon: ImageVector,
    previewImageURL: String?,
    authorId: String?,
    authorName: String?,
    modifier: Modifier = Modifier,
    useStoryRing: Boolean = true,
) {
    SharedDMPostCard(
        authorId = authorId,
        authorName = authorName,
        useStoryRing = useStoryRing,
        isVideo = false,
        caption = message,
        modifier = modifier,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (!previewImageURL.isNullOrBlank()) {
                AsyncImage(
                    model = previewImageURL,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(22.dp),
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(Color.White.copy(0.14f), Color.White.copy(0.06f)),
                            ),
                        ),
                )
            }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.5f)))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 14.dp),
            ) {
                Icon(icon, null, tint = Color.White.copy(0.9f), modifier = Modifier.size(26.dp))
                Text(
                    title,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// MARK: - ✅ Shared Moment Message Bubble (Actualizado)

@Composable
fun SharedMomentMessageBubble(
    message: EnhancedMessage,
    isCurrentUser: Boolean,
    @Suppress("UNUSED_PARAMETER") onTap: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var canViewMoment by remember(message.id) { mutableStateOf<Boolean?>(null) }
    var isLoading by remember(message.id) { mutableStateOf(true) }
    var displayData by remember(message.id) { mutableStateOf(message.sharedMomentData) }
    val firestore = remember { FirestoreService() }

    LaunchedEffect(message.id, message.sharedMomentData) {
        val data = message.sharedMomentData
        displayData = data
        val momentId = data?.get("momentId")
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        if (data == null || momentId.isNullOrBlank() || currentUserId.isNullOrBlank()) {
            canViewMoment = false
            isLoading = false
            return@LaunchedEffect
        }
        val authorId = data["momentAuthorId"]?.takeIf { it.isNotBlank() } ?: message.senderId
        if (authorId == currentUserId) {
            val ownMoment = runCatching { firestore.fetchMoment(momentId, authorId) }.getOrNull()
            if (ownMoment != null) {
                val author = UserCacheService.getCachedUser(ownMoment.authorId)?.username ?: ownMoment.username
                displayData = data + mapOf(
                    "momentId" to ownMoment.id.orEmpty(),
                    "momentAuthor" to author,
                    "momentAuthorId" to ownMoment.authorId,
                    "momentContent" to ownMoment.content,
                    "momentImageUrl" to (ownMoment.thumbnailUrl ?: ownMoment.imagePath).orEmpty(),
                    "momentAspectRatio" to (ownMoment.aspectRatio ?: "1:1"),
                    "momentVideoUrl" to ownMoment.videoUrl.orEmpty(),
                    "momentTimestamp" to (ownMoment.timestamp.time / 1000.0).toString(),
                )
                canViewMoment = true
            } else {
                canViewMoment = false
            }
            isLoading = false
            return@LaunchedEffect
        }
        val moment = runCatching { firestore.fetchMoment(momentId, authorId) }.getOrNull()
        if (moment == null) {
            canViewMoment = false
            isLoading = false
            return@LaunchedEffect
        }
        canViewMoment = PrivacyService.canUserViewMomentEnhanced(moment, currentUserId)
        if (canViewMoment == true) {
            val author = UserCacheService.getCachedUser(moment.authorId)?.username ?: moment.username
            displayData = data + mapOf(
                "momentId" to moment.id.orEmpty(),
                "momentAuthor" to author,
                "momentAuthorId" to moment.authorId,
                "momentContent" to moment.content,
                "momentImageUrl" to (moment.thumbnailUrl ?: moment.imagePath).orEmpty(),
                "momentAspectRatio" to (moment.aspectRatio ?: "1:1"),
                "momentVideoUrl" to moment.videoUrl.orEmpty(),
                "momentTimestamp" to (moment.timestamp.time / 1000.0).toString(),
            )
        }
        isLoading = false
    }

    val align = if (isCurrentUser) Alignment.CenterEnd else Alignment.CenterStart
    Box(modifier.fillMaxWidth(), contentAlignment = align) {
        when {
            isLoading -> SharedDMPreviewCardSkeleton(
                Modifier
                    .widthIn(max = 280.dp)
                    .padding(vertical = 4.dp),
            )
            canViewMoment == true && displayData != null -> {
                Box(
                    Modifier.padding(vertical = 4.dp),
                ) {
                    MomentBubbleContent(
                        content = null,
                        sharedMomentData = displayData!!,
                        isCurrentUser = isCurrentUser,
                    )
                }
            }
            else -> BlockedMomentBubble(
                sharedMomentData = displayData,
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
fun BlockedMomentBubble(
    sharedMomentData: Map<String, String>?,
    modifier: Modifier = Modifier,
) {
    SharedDMUnavailablePreviewCard(
        title = stringResource(R.string.share_moment_unavailable),
        message = stringResource(R.string.share_no_permission),
        icon = Icons.Filled.Lock,
        previewImageURL = sharedMomentData?.get("momentImageUrl"),
        authorId = sharedMomentData?.get("momentAuthorId"),
        authorName = sharedMomentData?.get("momentAuthor"),
        useStoryRing = false,
        modifier = modifier,
    )
}

// MARK: - ✅ Moment Bubble Content (Actualizado)

@Composable
fun MomentBubbleContent(
    content: String?,
    sharedMomentData: Map<String, String>,
    isCurrentUser: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = if (isCurrentUser) Alignment.End else Alignment.Start,
    ) {
        if (!content.isNullOrBlank()) {
            Text(content, fontSize = 14.sp, modifier = Modifier.padding(bottom = 4.dp))
        }
        MomentPreviewCard(sharedMomentData = sharedMomentData)
    }
}

// MARK: - ✅ Moment Preview Card (Actualizado Premium)

@Composable
fun MomentPreviewCard(
    sharedMomentData: Map<String, String>,
    modifier: Modifier = Modifier,
) {
    val isVideo = !sharedMomentData["momentVideoUrl"].isNullOrBlank()
    SharedDMPostCard(
        authorId = sharedMomentData["momentAuthorId"],
        authorName = sharedMomentData["momentAuthor"],
        useStoryRing = true,
        isVideo = isVideo,
        aspectRatio = parseSharedAspectRatio(sharedMomentData["momentAspectRatio"]),
        captionAuthor = sharedMomentData["momentAuthor"],
        caption = sharedMomentData["momentContent"],
        modifier = modifier,
    ) {
        MomentVisualContent(sharedMomentData = sharedMomentData)
    }
}

// MARK: - ✅ Moment Visual Content

@Composable
fun MomentVisualContent(
    sharedMomentData: Map<String, String>,
    modifier: Modifier = Modifier,
) {
    val imageUrl = sharedMomentData["momentImageUrl"]?.takeIf { it.isNotBlank() }
    val videoUrl = sharedMomentData["momentVideoUrl"]?.takeIf { it.isNotBlank() }
    Box(modifier.fillMaxSize().background(Color.Black)) {
        when {
            imageUrl != null -> {
                var loading by remember(imageUrl) { mutableStateOf(true) }
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    onSuccess = { loading = false },
                    onError = { loading = false },
                    onLoading = { loading = true },
                )
                if (loading) {
                    Box(Modifier.fillMaxSize().background(Color.Gray.copy(0.2f)), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                    }
                }
            }
            videoUrl != null -> {
                VideoThumbnailView(videoUrl = videoUrl, modifier = Modifier.fillMaxSize())
            }
            else -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(Color.fromHex("00A896"), Color.fromHex("02C39A")),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Image, null, tint = Color.White.copy(0.5f), modifier = Modifier.size(40.dp))
                }
            }
        }
    }
}

// MARK: - ✅ Video Thumbnail View (Flexible)

@Composable
fun VideoThumbnailView(
    videoUrl: String,
    modifier: Modifier = Modifier,
) {
    var thumbnail by remember(videoUrl) {
        mutableStateOf(VideoThumbnailCache.cachedThumbnail(videoUrl))
    }
    var isLoading by remember(videoUrl) { mutableStateOf(thumbnail == null) }

    LaunchedEffect(videoUrl) {
        if (thumbnail != null) {
            isLoading = false
            return@LaunchedEffect
        }
        thumbnail = VideoThumbnailCache.thumbnail(videoUrl)
        isLoading = false
    }

    Box(modifier.background(Color.Gray.copy(0.3f)), contentAlignment = Alignment.Center) {
        thumbnail?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (isLoading) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun rememberAdaptiveShareColors(): ShareAdaptiveColors {
    val isDark = isSystemInDarkTheme()
    return remember(isDark) {
        if (isDark) {
            ShareAdaptiveColors(primary = Color.White, secondary = Color.White.copy(0.7f))
        } else {
            ShareAdaptiveColors(
                primary = Color.fromHex("0B1215"),
                secondary = Color.fromHex("0B1215").copy(0.65f),
            )
        }
    }
}

private data class ShareAdaptiveColors(
    val primary: Color,
    val secondary: Color,
)
