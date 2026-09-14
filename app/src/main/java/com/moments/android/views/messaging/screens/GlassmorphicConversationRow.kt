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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.moments.android.utilities.MomentsFormat
import com.moments.android.views.components.VerifiedBadgeView
import com.moments.android.views.messaging.components.ChatVanishInboxIndicator
import com.moments.android.views.messaging.components.ChatViewOnceInboxIndicator
import com.moments.android.views.messaging.components.ConversationListInteraction
import com.moments.android.views.messaging.components.conversationRowMenuHighlight
import com.moments.android.views.messaging.core.Conversation
import com.moments.android.views.messaging.core.InboxParticipantState
import com.moments.android.views.profile.userprofile.sections.ProfileUnavailableAvatar
import com.moments.android.views.story.StoryRingAvatarView

data class InboxStoryLaunch(
    val startUserId: String,
    val ringUserIds: List<String> = emptyList(),
)

/**
 * Port de `GlassmorphicConversationRow` (`MessagingView.swift`).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlassmorphicConversationRow(
    conversation: Conversation,
    participantState: InboxParticipantState?,
    draftText: String,
    groupMemberIds: List<String>,
    onOpenProfile: () -> Unit,
    onTap: () -> Unit,
    onOpenStory: (InboxStoryLaunch) -> Unit = {},
    listInteraction: ConversationListInteraction? = null,
    isMenuSelected: Boolean = false,
    pressScale: Float = 1f,
    modifier: Modifier = Modifier,
    onNeedsParticipantState: () -> Unit = {},
) {
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current
    val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    val muteDeadline = conversation.mutedUntil?.get(uid)
    val showsMute by produceState(conversation.isMuted(uid), uid,
        listOf(conversation.mutedByUserIds, muteDeadline, conversation.isMuted, conversation.mutedBy)) {
        value = conversation.isMuted(uid)
        if (value && muteDeadline != null) {
            kotlinx.coroutines.delay((muteDeadline.time - System.currentTimeMillis()).coerceAtLeast(0))
            value = conversation.isMuted(uid)
        }
    }

    val isUnavailable = participantState?.isUnavailable == true
    val isBlockedByCurrentUser = participantState?.isBlockedByCurrentUser == true

    val displayUsername = remember(participantState?.username, conversation.otherParticipantUsername) {
        val live = participantState?.username?.trim().orEmpty()
        if (live.isNotEmpty()) live
        else conversation.otherParticipantUsername
            ?: context.getString(R.string.messaging_user_default)
    }

    LaunchedEffect(conversation.otherParticipantId, conversation.isGroup) {
        onNeedsParticipantState()
    }

    val showsUnavailablePreview = isUnavailable && !isBlockedByCurrentUser
    val cleanDraft = draftText.trim()
    val isUnread = uid.isNotBlank() && conversation.readStatus[uid] == false
    val isOwnLast = conversation.isOwnLastMessage(uid)
    val unreadCount = conversation.unreadCount(uid)
    // ≡ iOS: el borrador no tapa mensajes recibidos ni no leídos.
    val showsDraftPreview = !showsUnavailablePreview &&
        cleanDraft.isNotEmpty() &&
        !isUnread &&
        (isOwnLast || conversation.lastMessageSenderId == null)

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
        if (conversation.isGroup) {
            val memberIds = remember(groupMemberIds, uid) {
                groupMemberIds.filter { it.isNotBlank() && it != uid }
            }
            com.moments.android.views.story.GroupStoryRingAvatarView(
                memberUserIds = memberIds,
                groupImage = conversation.otherParticipantProfileImagePath.orEmpty(),
                size = 56.dp,
                lineWidth = 2.5.dp,
                hapticsEnabled = true,
                onTap = { hasStory, startUserId, ringUserIds ->
                    if (hasStory && !startUserId.isNullOrBlank()) {
                        onOpenStory(InboxStoryLaunch(startUserId, ringUserIds))
                    } else {
                        onTap()
                    }
                },
            )
        } else if (showsUnavailablePreview) {
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
                        onOpenStory(InboxStoryLaunch(conversation.otherParticipantId))
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
                    textDecoration = if (!conversation.isGroup && showsUnavailablePreview) TextDecoration.LineThrough else TextDecoration.None,
                    color = (if (isDark) Color.White else Color.Black).copy(
                        if (!conversation.isGroup && isUnavailable) 0.72f else 1f,
                    ),
                )
                if (!conversation.isGroup && !isUnavailable) {
                    VerifiedBadgeView(userId = conversation.otherParticipantId, size = 14.dp)
                }
                if (conversation.isPinned(uid)) {
                    Icon(Icons.Filled.PushPin, null, tint = Color(0xFF007AFF), modifier = Modifier.size(12.dp))
                }
                if (showsMute) {
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
