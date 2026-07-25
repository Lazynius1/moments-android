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
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.moments.android.models.AppUser
import com.moments.android.models.Conversation
import com.moments.android.models.EnhancedMessage
import com.moments.android.services.messaging.MessageRequestService
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.messaging.components.ChatRecoveryGateView
import com.moments.android.views.messaging.core.MessagingViewModel
import com.moments.android.views.messaging.screens.chat.GlassmorphicChatView
import kotlinx.coroutines.launch

/**
 * Port de `MessagingView.swift` — bandeja de conversaciones; el hilo abierto delega en
 * `GlassmorphicChatView` (el chat real portado) tras pasar por `ChatRecoveryGateView`.
 * Pendiente respecto a iOS: nuevo chat, solicitudes y filtros de la barra superior.
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
    var showingNewConversation by remember { mutableStateOf(false) }
    var showingRequests by remember { mutableStateOf(false) }
    var pendingRequestUser by remember { mutableStateOf<AppUser?>(null) }

    LaunchedEffect(Unit) {
        viewModel.start(targetConversationId)
    }
    LaunchedEffect(targetConversationId) {
        if (!targetConversationId.isNullOrBlank()) {
            viewModel.onTargetConversationId(targetConversationId)
            onTargetConversationIdConsumed()
        }
    }

    if (showingNewConversation) {
        GlassmorphicNewConversationView(
            viewModel = viewModel,
            onDismiss = { showingNewConversation = false },
            onConversationReady = { conversation ->
                showingNewConversation = false
                viewModel.openConversation(conversation)
            },
            onNeedsRequest = { user -> pendingRequestUser = user },
        )
        return
    }

    if (showingRequests) {
        Column(Modifier.fillMaxSize().background(colors.surfaceBackground).statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { showingRequests = false }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null, tint = colors.primary)
                }
                Text(
                    stringResource(R.string.message_requests_title),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    color = colors.primary,
                )
            }
            MessageRequestsView()
        }
        return
    }

    pendingRequestUser?.let { user ->
        SendMessageRequestDialog(
            user = user,
            onDismiss = { pendingRequestUser = null },
            onSent = { pendingRequestUser = null; showingNewConversation = false },
        )
    }

    // Como iOS (`MessagingView.body`): el gate envuelve TODA la bandeja, no solo el hilo — el PIN
    // se resuelve al entrar a Mensajes, antes de ver siquiera la lista de conversaciones.
    ChatRecoveryGateView(onCancel = onDismiss) {
        Box(
            modifier
                .fillMaxSize()
                .background(colors.surfaceBackground)
                .padding(contentPadding),
        ) {
            val selected = viewModel.selectedConversation
            if (selected != null) {
                GlassmorphicChatView(
                    conversation = selected,
                    onBack = { viewModel.closeChat() },
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
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = colors.primary,
                        )
                        // Port del `messagingToolbarTrailingCluster` de iOS: solicitudes + redactar.
                        IconButton(onClick = { showingRequests = true }) {
                            Icon(
                                Icons.Filled.MarkEmailUnread,
                                contentDescription = stringResource(R.string.message_requests_title),
                                tint = colors.primary,
                            )
                        }
                        IconButton(onClick = { showingNewConversation = true }) {
                            Icon(
                                Icons.AutoMirrored.Filled.Message,
                                contentDescription = stringResource(R.string.messaging_new_title),
                                tint = colors.primary,
                            )
                        }
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
}

/**
 * Envío de solicitud de mensaje cuando no hay follow mutuo. En iOS este caso abre el chat con un
 * `PendingChatContext` cuyo composer manda la solicitud; aquí se resuelve con un diálogo directo
 * sobre `MessageRequestService`, que ya aplica las reglas del backend (y actualiza la solicitud
 * existente en vez de duplicarla).
 */
@Composable
private fun SendMessageRequestDialog(
    user: AppUser,
    onDismiss: () -> Unit,
    onSent: () -> Unit,
) {
    val colors = rememberAdaptiveColors()
    val service = remember { MessageRequestService() }
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.messaging_new_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.messaging_error_message_request_required),
                    color = colors.secondary,
                    fontSize = 13.sp,
                )
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.secondary.copy(0.12f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    textStyle = TextStyle(color = colors.primary, fontSize = 15.sp),
                    cursorBrush = SolidColor(colors.primary),
                    decorationBox = { inner ->
                        if (text.isEmpty()) {
                            Text(stringResource(R.string.chat_input_placeholder), color = colors.secondary, fontSize = 15.sp)
                        }
                        inner()
                    },
                )
                error?.let { Text(it, color = androidx.compose.ui.graphics.Color.Red, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSending && text.isNotBlank(),
                onClick = {
                    isSending = true
                    error = null
                    scope.launch {
                        service.sendMessageRequest(receiverId = user.id, message = text.trim()) { result ->
                            isSending = false
                            result.onSuccess { onSent() }.onFailure { error = it.message }
                        }
                    }
                },
            ) {
                Text(stringResource(R.string.messaging_send_message))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

/**
 * Port de `GlassmorphicNewConversationView` (vive dentro de MessagingView.swift): buscador de
 * usuarios con sugerencias y arranque de conversación. Si el destinatario requiere solicitud,
 * `startConversation` devuelve null con `requiresMessageRequest`, y aquí se ofrece enviarla.
 */
@Composable
private fun GlassmorphicNewConversationView(
    viewModel: MessagingViewModel,
    onDismiss: () -> Unit,
    onConversationReady: (Conversation) -> Unit,
    onNeedsRequest: (AppUser) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    var searchText by remember { mutableStateOf("") }
    val uid = FirebaseAuth.getInstance().currentUser?.uid

    LaunchedEffect(Unit) { viewModel.searchUsers("") }
    LaunchedEffect(searchText) { viewModel.searchUsers(searchText) }

    Column(modifier.fillMaxSize().background(colors.surfaceBackground).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null, tint = colors.primary)
            }
            Text(
                stringResource(R.string.messaging_new_title),
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                color = colors.primary,
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.messaging_new_to),
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = colors.primary,
            )
            BasicTextField(
                value = searchText,
                onValueChange = { searchText = it },
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(color = colors.primary, fontSize = 16.sp),
                cursorBrush = SolidColor(colors.primary),
                singleLine = true,
                decorationBox = { inner ->
                    if (searchText.isEmpty()) {
                        Text(
                            stringResource(R.string.messaging_new_search_placeholder),
                            color = colors.secondary,
                            fontSize = 16.sp,
                        )
                    }
                    inner()
                },
            )
        }

        viewModel.errorMessage?.let { message ->
            Text(
                message,
                Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                color = androidx.compose.ui.graphics.Color.Red.copy(alpha = 0.9f),
                fontSize = 14.sp,
            )
        }

        if (searchText.isBlank()) {
            Text(
                stringResource(R.string.messaging_new_suggestions),
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = colors.secondary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
        }

        val users = viewModel.suggestedUsers
        when {
            users.isEmpty() && searchText.isBlank() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            users.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.messaging_no_results), color = colors.primary, fontWeight = FontWeight.SemiBold)
            }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(users, key = { it.id }) { user ->
                    NewConversationUserRow(user) {
                        val currentUserId = uid ?: return@NewConversationUserRow
                        viewModel.startConversation(user = user, fromUserId = currentUserId) { conversation ->
                            if (conversation != null) onConversationReady(conversation)
                            else if (viewModel.requiresMessageRequest) onNeedsRequest(user)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NewConversationUserRow(user: AppUser, onSelect: () -> Unit) {
    val colors = rememberAdaptiveColors()
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onSelect).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AsyncImage(
            model = user.profileImagePath,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(56.dp).clip(CircleShape).background(colors.secondary.copy(0.2f)),
        )
        Text(
            user.username,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            color = colors.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
