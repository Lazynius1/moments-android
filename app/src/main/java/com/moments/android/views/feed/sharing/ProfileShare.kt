package com.moments.android.views.feed.sharing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.coordinators.LegacyNavigationBridge
import com.moments.android.models.MomentGridPreviewSettings
import com.moments.android.models.AppUser
import com.moments.android.models.Moment
import com.moments.android.services.messaging.DirectMessageRoute
import com.moments.android.services.messaging.MessageRequestInteractionContext
import com.moments.android.services.messaging.MessageRequestService
import com.moments.android.services.privacy.VisibleConnectionTypes
import com.moments.android.utilities.HapticManager
import com.moments.android.utilities.MomentsFormat
import com.moments.android.utilities.legacyPoppinsSize
import com.moments.android.views.components.VerifiedBadge
import com.moments.android.views.messaging.components.ChatVideoPlayBadge
import com.moments.android.views.messaging.core.EnhancedMessage
import com.moments.android.views.messaging.core.MessageType
import com.moments.android.views.messaging.services.ChatService
import com.moments.android.views.messaging.services.sendSharedProfileMessage
import com.moments.android.views.profile.core.GridPreviewThumbnailFrame
import com.moments.android.views.profile.core.gridPreviewSettings
import com.moments.android.views.profile.core.sections.ProfileAvatarNoteMetrics
import com.moments.android.views.profile.core.sections.ProfileAvatarNoteView
import com.moments.android.views.profile.userprofile.UserProfileColors
import com.moments.android.views.profile.userprofile.UserProfileView
import com.moments.android.views.profile.userprofile.UserProfileViewModel
import com.moments.android.views.settings.hasVideoMedia
import com.moments.android.views.shared.MomentsModalSheet
import com.moments.android.views.shared.ScreenshotProtectedView
import com.moments.android.views.story.StoryRingAvatarView
import kotlinx.coroutines.launch
import org.json.JSONArray
import kotlin.math.max

private val sharedProfileCardWidth = 280.dp
private val sharedProfileCardCornerRadius = 18.dp
private val sharedProfileCardPadding = 11.dp
private val sharedProfileGridSpacing = 2.dp
private val sharedProfileAvatarSize = 40.dp
private val sharedProfileAvatarColumnWidth = 56.dp

// MARK: - Payload

/** ≡ iOS `SharedProfilePayloadBuilder`. */
object SharedProfilePayloadBuilder {
    fun make(
        user: AppUser,
        moments: List<Moment>,
        canViewContent: Boolean,
        visibleConnectionTypes: VisibleConnectionTypes,
        isOwnProfile: Boolean,
        fallbackUserId: String = "",
    ): Map<String, String> {
        val previewURLs = moments
            .take(4)
            .mapNotNull { it.previewImageURLString?.trim()?.takeIf(String::isNotEmpty) }
        val previewJSON = runCatching { JSONArray(previewURLs).toString() }.getOrDefault("[]")
        val profileUserId = user.id.trim().takeIf { it.isNotEmpty() } ?: fallbackUserId.trim()
        val showMoments = isOwnProfile || canViewContent
        return mapOf(
            "profileUserId" to profileUserId,
            "username" to user.username,
            "displayName" to "",
            "profileImagePath" to (user.profileImagePath ?: ""),
            "bio" to (user.bio ?: ""),
            "profileNote" to (user.profileNote ?: ""),
            "isVerified" to if (user.isVerified) "true" else "false",
            "momentsCount" to max(user.momentsCount, moments.size).toString(),
            "followersCount" to user.followersCount.toString(),
            "followingCount" to user.followingCount.toString(),
            "previewMomentUrls" to previewJSON,
            "shareUrl" to "https://glowsy.app/${user.username}",
            "showMoments" to if (showMoments) "true" else "false",
            "showFollowers" to if (visibleConnectionTypes.canViewFollowers) "true" else "false",
            "showFollowing" to if (visibleConnectionTypes.canViewFollowing) "true" else "false",
        )
    }
}

data class ProfileShareSheetItem(
    val profileUserId: String,
    val sharedProfileData: Map<String, String>,
)

@Composable
fun ProfileShareBottomSheet(
    item: ProfileShareSheetItem,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MomentsModalSheet(
        onDismissRequest = onDismiss,
        largeOnly = false,
    ) { dismiss ->
        Column(modifier.fillMaxWidth()) {
            ProfileShareSheet(
                profileUserId = item.profileUserId,
                sharedProfileData = item.sharedProfileData,
                onDismiss = {
                    dismiss()
                    onDismiss()
                },
            )
        }
    }
}

@Composable
fun ProfileShareSheet(
    profileUserId: String,
    sharedProfileData: Map<String, String>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val username = sharedProfileData["username"].orEmpty()
    var isSending by remember { mutableStateOf(false) }
    var deliveryFeedback by remember { mutableStateOf<String?>(null) }
    var dismissAfterFeedback by remember { mutableStateOf(false) }
    var showSuccessFeedback by remember { mutableStateOf(false) }

    ShareRecipientsPickerSheet(
        title = stringResource(R.string.share_send_to),
        subtitle = stringResource(R.string.share_profile_by, username),
        showsBackButton = false,
        onDismiss = onDismiss,
        onSend = { selectedUsers, _ ->
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            val fromProp = profileUserId.trim()
            val fromPayload = sharedProfileData["profileUserId"]?.trim().orEmpty()
            val resolvedProfileUserId = fromProp.ifEmpty { fromPayload }
            if (uid == null || resolvedProfileUserId.isEmpty() || isSending) {
                if (resolvedProfileUserId.isEmpty()) {
                    deliveryFeedback = context.getString(R.string.share_send_invalid_payload)
                }
                return@ShareRecipientsPickerSheet
            }
            if (selectedUsers.isEmpty()) return@ShareRecipientsPickerSheet
            isSending = true
            val shareText = context.getString(R.string.share_profile_by, username)
            scope.launch {
                val coordinator = MessageRequestService()
                val failures = mutableListOf<String>()
                var successCount = 0
                selectedUsers.sorted().forEach { userId ->
                    if (userId.isBlank()) {
                        failures += context.getString(R.string.messaging_error_invalid_recipient)
                        return@forEach
                    }
                    runCatching {
                        val interaction = MessageRequestInteractionContext(
                            kind = MessageRequestInteractionContext.Kind.SHARE_PROFILE,
                            sharedContentId = resolvedProfileUserId,
                            sharedContentOwnerId = resolvedProfileUserId,
                        )
                        val conversationId = when (val route = coordinator.resolveRoute(userId, interaction)) {
                            is DirectMessageRoute.Conversation -> route.id
                            is DirectMessageRoute.ConversationDraft ->
                                coordinator.activateConversationDraft(userId, route.threadId)
                            is DirectMessageRoute.IncomingRequest ->
                                coordinator.acceptIncomingThread(route.threadId).conversationId
                            is DirectMessageRoute.OutgoingRequest -> {
                                coordinator.appendRequestMessage(
                                    receiverId = userId,
                                    text = shareText,
                                    messageType = MessageType.SHARED_PROFILE,
                                    interaction = interaction,
                                )
                                null
                            }
                        }
                        conversationId?.let { id ->
                            val payload = sharedProfileData.toMutableMap().apply {
                                put("profileUserId", resolvedProfileUserId)
                            }
                            ChatService.sendSharedProfileMessage(
                                conversationId = id,
                                senderId = uid,
                                sharedProfileData = payload,
                                shareText = shareText,
                            ).getOrThrow()
                        }
                    }.onSuccess { successCount++ }
                        .onFailure { failures += it.localizedMessage ?: context.getString(R.string.common_error) }
                }
                isSending = false
                if (successCount > 0 && failures.isEmpty()) {
                    HapticManager.shared.success()
                    showSuccessFeedback = true
                } else {
                    HapticManager.shared.error()
                    deliveryFeedback = when {
                        failures.size == 1 -> failures.first()
                        failures.isNotEmpty() ->
                            context.getString(
                                R.string.messaging_forward_partial_failure,
                                failures.size,
                                selectedUsers.size,
                            )
                        else -> context.getString(R.string.common_error)
                    }
                    dismissAfterFeedback = successCount > 0
                }
            }
        },
    )

    deliveryFeedback?.let { message ->
        AlertDialog(
            onDismissRequest = { deliveryFeedback = null },
            title = { Text(stringResource(R.string.common_error)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = {
                    deliveryFeedback = null
                    if (dismissAfterFeedback) onDismiss()
                    dismissAfterFeedback = false
                }) {
                    Text(stringResource(R.string.common_ok))
                }
            },
        )
    }

    if (showSuccessFeedback) {
        AlertDialog(
            onDismissRequest = { showSuccessFeedback = false; onDismiss() },
            title = { Text(stringResource(R.string.share_send_success_title)) },
            text = { Text(stringResource(R.string.share_send_success_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showSuccessFeedback = false
                    onDismiss()
                }) {
                    Text(stringResource(R.string.common_ok))
                }
            },
        )
    }
}

@Composable
fun SharedProfileMessageBubble(
    message: EnhancedMessage,
    isCurrentUser: Boolean,
) {
    var profileUserIdToOpen by remember { mutableStateOf<String?>(null) }
    val currentUid = FirebaseAuth.getInstance().currentUser?.uid
    message.sharedProfileData?.let { data ->
        SharedProfilePreviewCard(
            sharedProfileData = data,
            onOpenProfile = {
                data["profileUserId"]?.trim()?.takeIf { it.isNotEmpty() }?.let { userId ->
                    if (userId == currentUid) {
                        LegacyNavigationBridge.ownProfileTab()
                    } else {
                        profileUserIdToOpen = userId
                    }
                }
            },
            modifier = Modifier
                .width(sharedProfileCardWidth)
                .padding(vertical = 4.dp),
        )
    }
    profileUserIdToOpen?.let { userId ->
        Dialog(
            onDismissRequest = { profileUserIdToOpen = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            UserProfileView(userId = userId, onDismiss = { profileUserIdToOpen = null })
        }
    }
}

@Composable
fun SharedProfilePreviewCard(
    sharedProfileData: Map<String, String>,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val profileUserId = sharedProfileData["profileUserId"].orEmpty()
    val viewModel = remember(profileUserId) { UserProfileViewModel(profileUserId) }
    val isDark = isSystemInDarkTheme()
    val cardBackground = if (isDark) Color(0xFF151C1D) else Color(0xFFE8EEF0)
    val cardShape = RoundedCornerShape(sharedProfileCardCornerRadius)
    val currentUid = FirebaseAuth.getInstance().currentUser?.uid
    val isOwnProfile = profileUserId.isNotEmpty() && profileUserId == currentUid
    val isUnavailableForViewer = profileUserId.isEmpty() ||
        viewModel.isProfileUnavailable ||
        viewModel.isCurrentUserBlocked ||
        viewModel.isBlockedByCurrentUser

    LaunchedEffect(profileUserId) {
        if (profileUserId.isEmpty()) return@LaunchedEffect
        viewModel.checkFollowButtonState()
        viewModel.fetchProfile(momentsLimit = 50)
        viewModel.refreshMutualRelationship()
        if (isOwnProfile) {
            viewModel.fetchMoments()
        }
    }

    val previewMoments = viewModel.moments.take(4)
    val snapshotPreviewURLs = remember(sharedProfileData) { parseSnapshotPreviewURLs(sharedProfileData) }
    val hasPreviewContent = previewMoments.isNotEmpty() || snapshotPreviewURLs.isNotEmpty()
    val shouldShowMomentsGrid = hasPreviewContent && (isOwnProfile || viewModel.canViewContent)
    val shouldShowMomentsLoading = !hasPreviewContent &&
        (isOwnProfile || viewModel.canViewContent) &&
        viewModel.isLoadingMoments

    when {
        profileUserId.isEmpty() || isUnavailableForViewer -> {
            SharedDMUnavailablePreviewCard(
                title = stringResource(R.string.share_profile_unavailable),
                message = stringResource(R.string.share_no_permission),
                icon = Icons.Filled.Lock,
                previewImageURL = null,
                authorId = null,
                authorName = null,
                useStoryRing = false,
                modifier = modifier,
            )
        }
        viewModel.isLoading && viewModel.userProfile == null -> {
            SharedProfileCardShell(cardBackground, cardShape, isDark, modifier, onOpenProfile) {
                SharedProfileHeaderRow(sharedProfileData, viewModel, profileUserId, useLive = false)
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = UserProfileColors.accent,
                    )
                }
            }
        }
        else -> {
            SharedProfileCardShell(cardBackground, cardShape, isDark, modifier, onOpenProfile) {
                SharedProfileHeaderRow(sharedProfileData, viewModel, profileUserId, useLive = true)
                if (shouldShowMomentsGrid) {
                    SharedProfileMomentsGrid(
                        moments = previewMoments,
                        snapshotURLs = snapshotPreviewURLs,
                    )
                } else if (shouldShowMomentsLoading) {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = UserProfileColors.accent,
                        )
                    }
                }
                SharedProfileStatsRow(viewModel, isOwnProfile)
            }
        }
    }
}

@Composable
private fun SharedProfileCardShell(
    cardBackground: Color,
    cardShape: RoundedCornerShape,
    isDark: Boolean,
    modifier: Modifier,
    onOpenProfile: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .clip(cardShape)
            .background(cardBackground)
            .border(
                width = 1.dp,
                color = UserProfileColors.borderColor.copy(alpha = if (isDark) 0.14f else 0.22f),
                shape = cardShape,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onOpenProfile,
            )
            .padding(sharedProfileCardPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        content()
    }
}

@Composable
private fun SharedProfileHeaderRow(
    sharedProfileData: Map<String, String>,
    viewModel: UserProfileViewModel,
    profileUserId: String,
    useLive: Boolean,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val noteScale = sharedProfileAvatarColumnWidth / ProfileAvatarNoteMetrics.columnWidth
    val username = viewModel.userProfile?.username?.trim()?.takeIf { it.isNotEmpty() }
        ?: sharedProfileData["username"]?.trim()?.takeIf { it.isNotEmpty() }
        ?: stringResource(R.string.user_profile_user)
    val bio = if (useLive) {
        viewModel.userProfile?.bio?.trim()?.takeIf { it.isNotEmpty() }
            ?: sharedProfileData["bio"]?.trim()?.takeIf { it.isNotEmpty() }
    } else {
        sharedProfileData["bio"]?.trim()?.takeIf { it.isNotEmpty() }
    }
    val note = if (useLive) {
        viewModel.userProfile?.profileNote?.trim()?.takeIf { it.isNotEmpty() }
            ?: sharedProfileData["profileNote"]?.trim()?.takeIf { it.isNotEmpty() }
    } else {
        sharedProfileData["profileNote"]?.trim()?.takeIf { it.isNotEmpty() }
    }
    val verified = viewModel.userProfile?.isVerified ?: (sharedProfileData["isVerified"] == "true")

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Column(
            modifier = Modifier.width(sharedProfileAvatarColumnWidth),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            StoryRingAvatarView(
                userId = profileUserId,
                size = sharedProfileAvatarSize,
                lineWidth = 2.dp,
                allowOwnStories = true,
            )
            ProfileAvatarNoteView(
                note = note,
                isEditable = false,
                modifier = Modifier
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0.5f, 0f)
                        scaleX = noteScale
                        scaleY = noteScale
                    }
                    .width(sharedProfileAvatarColumnWidth),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = username,
                    color = UserProfileColors.textPrimary,
                    fontSize = with(density) { legacyPoppinsSize(context, 11).toSp() },
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (verified) VerifiedBadge(size = 10.dp)
            }
            bio?.let {
                Text(
                    text = it,
                    color = UserProfileColors.textSecondary,
                    fontSize = with(density) { legacyPoppinsSize(context, 9).toSp() },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SharedProfileMomentsGrid(
    moments: List<Moment>,
    snapshotURLs: List<String> = emptyList(),
) {
    val contentWidth = sharedProfileCardWidth - sharedProfileCardPadding * 2
    val cellSize = max(
        0f,
        (contentWidth - sharedProfileGridSpacing * 3).value / 4f,
    ).dp
    Row(
        modifier = Modifier
            .width(contentWidth)
            .clip(RoundedCornerShape(7.dp)),
        horizontalArrangement = Arrangement.spacedBy(sharedProfileGridSpacing),
    ) {
        if (moments.isNotEmpty()) {
            moments.forEach { moment ->
                ScreenshotProtectedView(
                    isProtected = (moment.audience?.lowercase() ?: "") != "everyone",
                ) {
                    Box(Modifier.size(cellSize), contentAlignment = Alignment.BottomStart) {
                        GridPreviewThumbnailFrame(size = cellSize, settings = moment.gridPreviewSettings) { contentScale ->
                            val url = moment.previewImageURLString
                            if (!url.isNullOrBlank()) {
                                AsyncImage(
                                    model = url,
                                    contentDescription = null,
                                    contentScale = contentScale,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(UserProfileColors.cardBackground),
                                )
                            }
                        }
                        if (moment.hasVideoMedia) {
                            ChatVideoPlayBadge(size = 14.dp, padding = 8.dp)
                        }
                    }
                }
            }
            repeat(4 - moments.size) {
                Box(Modifier.size(cellSize))
            }
        } else {
            snapshotURLs.take(4).forEach { url ->
                Box(Modifier.size(cellSize)) {
                    GridPreviewThumbnailFrame(size = cellSize, settings = MomentGridPreviewSettings.DEFAULT) { contentScale ->
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = contentScale,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
            repeat(4 - snapshotURLs.size.coerceAtMost(4)) {
                Box(Modifier.size(cellSize))
            }
        }
    }
}

private fun parseSnapshotPreviewURLs(sharedProfileData: Map<String, String>): List<String> {
    val json = sharedProfileData["previewMomentUrls"] ?: return emptyList()
    return runCatching {
        val array = JSONArray(json)
        buildList {
            for (index in 0 until array.length()) {
                array.optString(index)?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
            }
        }
    }.getOrDefault(emptyList())
}

@Composable
private fun SharedProfileStatsRow(
    viewModel: UserProfileViewModel,
    isOwnProfile: Boolean,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val stats = sharedProfileVisibleStats(viewModel, isOwnProfile)
    if (stats.isEmpty()) return
    val isDark = isSystemInDarkTheme()
    Row(Modifier.fillMaxWidth()) {
        stats.forEachIndexed { index, stat ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = MomentsFormat.count(stat.count, MomentsFormat.CountStyle.PROFILE_STAT),
                    color = UserProfileColors.textPrimary,
                    fontSize = with(density) { legacyPoppinsSize(context, 11).toSp() },
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stat.label,
                    color = UserProfileColors.textSecondary,
                    fontSize = with(density) { legacyPoppinsSize(context, 7).toSp() },
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (index < stats.lastIndex) {
                Box(
                    Modifier
                        .padding(vertical = 2.dp)
                        .width(1.dp)
                        .height(20.dp)
                        .background(
                            UserProfileColors.borderColor.copy(alpha = if (isDark) 0.22f else 0.35f),
                        ),
                )
            }
        }
    }
}

private data class SharedProfileStat(val label: String, val count: Int)

@Composable
private fun sharedProfileVisibleStats(
    viewModel: UserProfileViewModel,
    isOwnProfile: Boolean,
): List<SharedProfileStat> {
    val postsLabel = stringResource(R.string.profile_ui_posts)
    val followersLabel = stringResource(R.string.profile_ui_followers)
    val followingLabel = stringResource(R.string.profile_ui_following)
    val postsCount = max(viewModel.moments.size, viewModel.userProfile?.momentsCount ?: 0)
    val stats = mutableListOf<SharedProfileStat>()
    if (viewModel.canViewContent || isOwnProfile) {
        stats += SharedProfileStat(postsLabel, postsCount)
        if (viewModel.visibleConnectionTypes.canViewFollowers) {
            stats += SharedProfileStat(
                followersLabel,
                max(viewModel.followers.size, viewModel.userProfile?.followersCount ?: 0),
            )
        }
        if (viewModel.visibleConnectionTypes.canViewFollowing) {
            stats += SharedProfileStat(
                followingLabel,
                max(viewModel.following.size, viewModel.userProfile?.followingCount ?: 0),
            )
        }
    } else {
        if (viewModel.visibleConnectionTypes.canViewFollowers) {
            stats += SharedProfileStat(
                followersLabel,
                max(viewModel.followers.size, viewModel.userProfile?.followersCount ?: 0),
            )
        }
        if (viewModel.visibleConnectionTypes.canViewFollowing) {
            stats += SharedProfileStat(
                followingLabel,
                max(viewModel.following.size, viewModel.userProfile?.followingCount ?: 0),
            )
        }
    }
    return stats
}
