package com.moments.android.views.messaging.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.services.cache.UserCacheService
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.PublicProfileAvailability
import com.moments.android.services.firestore.checkPublicProfileAvailability
import com.moments.android.utilities.MomentsFormat
import com.moments.android.views.components.VerifiedBadgeView
import com.moments.android.views.messaging.components.ChatVanishInboxIndicator
import com.moments.android.views.messaging.components.ChatViewOnceInboxIndicator
import com.moments.android.views.messaging.components.ConversationListInteraction
import com.moments.android.views.messaging.components.conversationRowMenuHighlight
import com.moments.android.views.messaging.core.Conversation
import com.moments.android.views.messaging.services.ChatDraftEvent
import com.moments.android.views.messaging.services.ChatDraftEvents
import com.moments.android.views.messaging.services.ChatDraftStore
import com.moments.android.views.profile.userprofile.sections.ProfileUnavailableAvatar
import com.moments.android.views.story.StoryRingAvatarView
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Port de `GlassmorphicConversationRow` (`MessagingView.swift`).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlassmorphicConversationRow(
    conversation: Conversation,
    onOpenProfile: () -> Unit,
    onTap: () -> Unit,
    onOpenStory: (String) -> Unit = {},
    listInteraction: ConversationListInteraction? = null,
    isMenuSelected: Boolean = false,
    pressScale: Float = 1f,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current
    val firestore = remember { FirestoreService() }
    val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()

    var liveUsername by remember(conversation.otherParticipantId) { mutableStateOf("") }
    var isUnavailable by remember(conversation.otherParticipantId) { mutableStateOf(false) }
    var isBlockedByCurrentUser by remember(conversation.otherParticipantId) { mutableStateOf(false) }
    var draftText by remember(conversation.id) { mutableStateOf("") }

    val displayUsername = remember(liveUsername, conversation.otherParticipantUsername) {
        val live = liveUsername.trim()
        if (live.isNotEmpty()) live
        else conversation.otherParticipantUsername
            ?: context.getString(R.string.messaging_user_default)
    }

    LaunchedEffect(conversation.id) {
        val id = conversation.id
        draftText = if (id.isNullOrBlank()) "" else runCatching { ChatDraftStore.draft(context, id) }.getOrDefault("")
    }
    LaunchedEffect(conversation.id) {
        ChatDraftEvents.events.collectLatest { event ->
            if (event is ChatDraftEvent.Changed && event.conversationId == conversation.id) {
                draftText = runCatching { ChatDraftStore.draft(context, event.conversationId) }.getOrDefault("")
            }
        }
    }
    LaunchedEffect(conversation.otherParticipantId) {
        val otherId = conversation.otherParticipantId.trim()
        if (otherId.isEmpty()) return@LaunchedEffect
        liveUsername = ""
        isUnavailable = false
        isBlockedByCurrentUser = false
        runCatching {
            suspendCancellableCoroutine { cont ->
                UserCacheService.refreshUser(otherId) { user ->
                    cont.resume(user?.username?.trim().orEmpty())
                }
            }
        }.onSuccess { name ->
            if (conversation.otherParticipantId.trim() == otherId) liveUsername = name
        }
        val availability = runCatching { firestore.checkPublicProfileAvailability(otherId) }.getOrNull()
        if (availability == PublicProfileAvailability.UNAVAILABLE) {
            if (conversation.otherParticipantId.trim() == otherId) {
                isUnavailable = true
                liveUsername = ""
                isBlockedByCurrentUser = false
            }
            return@LaunchedEffect
        }
        if (uid.isNotBlank()) {
            val block = runCatching { firestore.checkIfBlocked(uid, otherId) }.getOrNull()
            if (block != null && (block.isBlockedByCurrentUser || block.isCurrentUserBlocked)) {
                if (conversation.otherParticipantId.trim() == otherId) {
                    isBlockedByCurrentUser = block.isBlockedByCurrentUser
                    isUnavailable = true
                }
            } else if (conversation.otherParticipantId.trim() == otherId) {
                isBlockedByCurrentUser = false
                isUnavailable = false
            }
        }
    }

    val showsUnavailablePreview = isUnavailable && !isBlockedByCurrentUser
    val cleanDraft = draftText.trim()
    val showsDraftPreview = !showsUnavailablePreview && cleanDraft.isNotEmpty()
    val isUnread = uid.isNotBlank() && conversation.readStatus[uid] == false
    val isOwnLast = conversation.isOwnLastMessage(uid)
    val unreadCount = conversation.unreadCount(uid)

    val resolvedPreview = when {
        showsUnavailablePreview -> stringResource(R.string.messaging_profile_unavailable_preview)
        showsDraftPreview -> stringResource(R.string.chat_draft_preview, cleanDraft)
        conversation.lastMessageReaction != null && isOwnLast ->
            "${conversation.lastMessageReaction!!.emoji} " + stringResource(R.string.chat_preview_reacted)
        unreadCount >= 2 -> stringResource(R.string.chat_unread_count_preview, unreadCount)
        isOwnLast && conversation.lastMessageSeenAt?.get(conversation.otherParticipantId) != null ->
            stringResource(R.string.chat_seen)
        isOwnLast -> stringResource(R.string.chat_status_sent)
        else -> conversation.inboxMessagePreview(context, uid)
    }

    val previewColor = when {
        showsDraftPreview -> Color(0xFF3F6F8F)
        isUnread -> if (isDark) Color.White else Color.Black
        else -> if (isDark) Color.White.copy(0.6f) else Color.Black.copy(0.5f)
    }
    val secondaryColor = if (isDark) Color.White.copy(0.45f) else Color.Black.copy(0.38f)
    val relativeSource =
        if (isOwnLast) {
            conversation.lastMessageSeenAt?.get(conversation.otherParticipantId) ?: conversation.timestamp
        } else {
            conversation.timestamp
        }
    val relativeTime = MomentsFormat.relativeTime(
        from = relativeSource,
        style = MomentsFormat.RelativeTimeStyle.COMPACT_BARE,
    )

    val rowModifier = modifier
        .fillMaxWidth()
        // ≡ iOS ConversationRowMenuHighlight + scale 0.96 cuando menú abierto
        .conversationRowMenuHighlight(isMenuSelected)
        .graphicsLayer {
            val s = if (isMenuSelected) 0.96f else pressScale
            scaleX = s
            scaleY = s
        }
        .padding(horizontal = 12.dp, vertical = 6.dp)
        .then(
            if (listInteraction != null) {
                Modifier.combinedClickable(
                    onClick = listInteraction.onTap,
                    onLongClick = listInteraction.onLongPress,
                )
            } else {
                Modifier.clickable(onClick = onTap)
            },
        )

    Row(
        rowModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showsUnavailablePreview) {
            // Sin historia → abrir conversación
            Box(Modifier.clickable(onClick = onTap)) {
                ProfileUnavailableAvatar(size = 56.dp)
            }
        } else {
            StoryRingAvatarView(
                userId = conversation.otherParticipantId,
                size = 56.dp,
                lineWidth = 2.5.dp,
                isOwnStory = false,
                hapticsEnabled = true,
                onTap = { hasStory ->
                    if (hasStory && !isBlockedByCurrentUser) {
                        onOpenStory(conversation.otherParticipantId)
                    } else {
                        // Sin historia → abrir el chat
                        onTap()
                    }
                },
            )
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = if (listInteraction == null) Modifier.clickable(onClick = onTap) else Modifier,
            ) {
                Text(
                    displayUsername,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (showsUnavailablePreview) TextDecoration.LineThrough else TextDecoration.None,
                    color = (if (isDark) Color.White else Color.Black).copy(
                        if (isUnavailable) 0.72f else 1f,
                    ),
                )
                if (!isUnavailable) {
                    VerifiedBadgeView(userId = conversation.otherParticipantId, size = 14.dp)
                }
                if (conversation.isPinned(uid)) {
                    Icon(Icons.Filled.PushPin, null, tint = Color(0xFF007AFF), modifier = Modifier.size(12.dp))
                }
                if (conversation.isMuted(uid)) {
                    Icon(Icons.Filled.NotificationsOff, null, tint = Color(0xFFFF9500), modifier = Modifier.size(12.dp))
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    resolvedPreview,
                    fontSize = 14.sp,
                    fontWeight = if (isUnread && !showsDraftPreview) FontWeight.SemiBold else FontWeight.Normal,
                    color = previewColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(relativeTime, fontSize = 14.sp, color = secondaryColor, maxLines = 1)
            }
        }

        when {
            conversation.showsViewOnceInboxPlayButton(uid) -> ChatViewOnceInboxIndicator()
            conversation.vanishModeActive == true -> ChatVanishInboxIndicator(isUnread = isUnread)
            isUnread -> {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF007AFF))
                        .border(2.dp, if (isDark) Color.Black else Color.White, CircleShape),
                )
            }
        }
    }
}
