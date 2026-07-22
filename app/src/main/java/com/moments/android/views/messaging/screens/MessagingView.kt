package com.moments.android.views.messaging.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.models.Conversation
import com.moments.android.models.EnhancedMessage
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.messaging.core.MessagingViewModel

/**
 * Port MVP de `MessagingView.swift` — inbox + chat texto.
 * New chat / requests / media / glass = stubs honestos (omitidos).
 */
@Composable
fun MessagingView(
    targetConversationId: String? = null,
    onTargetConversationIdConsumed: () -> Unit = {},
    onDismiss: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    val viewModel = remember { MessagingViewModel() }

    LaunchedEffect(Unit) {
        viewModel.start(targetConversationId)
    }
    LaunchedEffect(targetConversationId) {
        if (!targetConversationId.isNullOrBlank()) {
            viewModel.onTargetConversationId(targetConversationId)
            onTargetConversationIdConsumed()
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(colors.surfaceBackground)
            .padding(contentPadding),
    ) {
        val selected = viewModel.selectedConversation
        if (selected != null) {
            ChatThreadScreen(
                conversation = selected,
                messages = viewModel.chatMessages,
                isLoading = viewModel.isChatLoading,
                onBack = { viewModel.closeChat() },
                onSend = { viewModel.sendText(it) },
            )
        } else {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null)
                    }
                    Text(
                        stringResource(R.string.messaging_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = colors.primary,
                    )
                }
                when {
                    viewModel.isLoading && viewModel.conversations.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    viewModel.errorMessage != null && viewModel.conversations.isEmpty() -> {
                        Column(
                            Modifier.fillMaxSize().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(viewModel.errorMessage.orEmpty(), color = colors.secondary)
                            TextButton(onClick = { viewModel.start(null) }) {
                                Text(stringResource(R.string.explore_error_retry))
                            }
                        }
                    }
                    viewModel.conversations.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.messaging_empty), color = colors.secondary)
                        }
                    }
                    else -> {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(viewModel.conversations, key = { it.id.orEmpty() }) { conv ->
                                ConversationRow(
                                    conversation = conv,
                                    onClick = { viewModel.openConversation(conv) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(
    conversation: Conversation,
    onClick: () -> Unit,
) {
    val colors = rememberAdaptiveColors()
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    val unread = uid != null && conversation.readStatus[uid] == false
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = conversation.otherParticipantProfileImagePath,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(colors.secondary.copy(0.2f)),
        )
        Column(Modifier.weight(1f)) {
            Text(
                conversation.otherParticipantUsername ?: stringResource(R.string.messaging_user_default),
                fontWeight = if (unread) FontWeight.Bold else FontWeight.SemiBold,
                color = colors.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                conversation.lastMessage.orEmpty(),
                color = colors.secondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ChatThreadScreen(
    conversation: Conversation,
    messages: List<EnhancedMessage>,
    isLoading: Boolean,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
) {
    val colors = rememberAdaptiveColors()
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.surfaceBackground),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null)
            }
            AsyncImage(
                model = conversation.otherParticipantProfileImagePath,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colors.secondary.copy(0.2f)),
            )
            Spacer(Modifier.size(10.dp))
            Text(
                conversation.otherParticipantUsername ?: stringResource(R.string.messaging_user_default),
                fontWeight = FontWeight.SemiBold,
                color = colors.primary,
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (isLoading && messages.isEmpty()) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(messages, key = { it.id }) { msg ->
                        val mine = msg.senderId == uid
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
                        ) {
                            Text(
                                text = msg.content?.toString().orEmpty(),
                                modifier = Modifier
                                    .widthIn(max = 280.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (mine) colors.accent.copy(alpha = 0.85f)
                                        else colors.primary.copy(alpha = 0.08f),
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                color = if (mine) colors.surfaceBackground else colors.primary,
                                fontSize = 15.sp,
                            )
                        }
                    }
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                textStyle = TextStyle(color = colors.primary, fontSize = 16.sp),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.primary.copy(0.06f))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                decorationBox = { inner ->
                    if (draft.isEmpty()) {
                        Text(stringResource(R.string.messaging_compose_placeholder), color = colors.secondary)
                    }
                    inner()
                },
            )
            IconButton(
                onClick = {
                    val text = draft
                    draft = ""
                    onSend(text)
                },
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = colors.accent)
            }
        }
    }
}
