package com.moments.android.views.messaging.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.moments.android.views.feed.rememberAdaptiveColors
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.models.Conversation
import com.moments.android.views.messaging.components.ConversationContextMenuInsets
import com.moments.android.views.messaging.components.ConversationContextMenuOverlay
import com.moments.android.views.messaging.components.ConversationMenuData
import com.moments.android.views.messaging.components.ConversationMenuSelection
import com.moments.android.views.messaging.core.MessagingViewModel

/** Port de `ArchivedConversationsView.swift`. */
@Composable
fun ArchivedConversationsView(
    viewModel: MessagingViewModel,
    onBack: () -> Unit,
    onOpenConversation: (Conversation) -> Unit,
    onOpenProfile: (String) -> Unit = {},
    onDelete: (Conversation) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    var selection by remember { mutableStateOf<ConversationMenuSelection?>(null) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    Box(modifier.fillMaxSize().background(colors.background).onSizeChanged { containerSize = it }) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ArrowBack, stringResource(R.string.common_back), modifier = Modifier.size(28.dp).combinedClickable(onClick = onBack))
                Text(stringResource(R.string.messaging_archived), fontWeight = FontWeight.SemiBold, color = colors.primary, modifier = Modifier.padding(start = 18.dp))
            }
            if (viewModel.archivedConversations.isEmpty()) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Filled.Archive, null, tint = colors.secondary, modifier = Modifier.size(44.dp))
                    Text(stringResource(R.string.messaging_archived), color = colors.secondary, modifier = Modifier.padding(top = 12.dp))
                }
            } else LazyColumn(Modifier.fillMaxSize()) {
                items(viewModel.archivedConversations, key = { it.id.orEmpty() }) { conversation ->
                    ArchivedConversationRow(conversation, onClick = { onOpenConversation(conversation) }, onProfile = onOpenProfile, onLongPress = { frame ->
                        val uid = FirebaseAuth.getInstance().currentUser?.uid
                        selection = ConversationMenuSelection(ConversationMenuData(conversation, unreadCount = conversation.unreadCount(uid.orEmpty()), isPinned = conversation.isPinned(uid), isMuted = conversation.isMuted(uid), isArchived = true), frame)
                    })
                }
            }
        }
        ConversationContextMenuOverlay(selection, containerSize, ConversationContextMenuInsets(), onDismiss = { selection = null }, onMarkUnread = { viewModel.markConversationAsUnread(it); selection = null }, onPin = { selection = null }, onMute = { selection = null }, onArchive = { selection = null }, onUnarchive = { viewModel.unarchiveConversation(it); selection = null }, onDelete = { onDelete(it); selection = null })
    }
}

@Composable private fun ArchivedConversationRow(conversation: Conversation, onClick: () -> Unit, onProfile: (String) -> Unit, onLongPress: (Rect) -> Unit) {
    val colors = rememberAdaptiveColors(); var frame by remember { mutableStateOf(Rect.Zero) }
    Row(Modifier.fillMaxWidth().onGloballyPositioned { frame = it.boundsInRoot() }.combinedClickable(onClick = onClick, onLongClick = { onLongPress(frame) }).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AsyncImage(conversation.otherParticipantProfileImagePath, null, Modifier.size(52.dp).clip(CircleShape).combinedClickable(onClick = { onProfile(conversation.otherParticipantId) }), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        Column(Modifier.weight(1f)) { Text(conversation.otherParticipantUsername ?: stringResource(R.string.messaging_user_default), color = colors.primary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(conversation.lastMessage.orEmpty(), color = colors.secondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}
