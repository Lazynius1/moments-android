package com.moments.android.views.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.coordinators.AsyncProfileImageView
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.ChatAccessState
import com.moments.android.views.messaging.core.MessageType
import com.moments.android.models.MomentsNotification
import com.moments.android.views.messaging.core.conversationPreview
import com.moments.android.notifications.services.InAppNotificationService
import com.moments.android.notifications.services.NotificationCopyResolver
import com.moments.android.utilities.HapticManager
import com.moments.android.utilities.legacyPoppinsSize
import com.moments.android.views.messaging.services.ChatAccessCoordinator
import com.moments.android.views.messaging.services.ChatService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Port de `InAppMessageQuickReplyPanel.swift`.
 */
@Composable
fun InAppMessageQuickReplyPanel(
    notification: MomentsNotification,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val isDark = isSystemInDarkTheme()
    val access by ChatAccessCoordinator.accessState.collectAsState()
    var replyText by remember(notification.id) { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    // iOS: accessCoordinator.isAvailable && conversationId?.isEmpty == false
    val canReply = (access is ChatAccessState.Available || ChatAccessCoordinator.isAvailable) &&
        !notification.conversationId.isNullOrBlank()

    val previewText = remember(notification) {
        resolveQuickReplyPreview(notification, context)
    }

    val titleSp = with(density) { legacyPoppinsSize(context, 15).toSp() }
    val subtitleSp = with(density) { legacyPoppinsSize(context, 12).toSp() }
    val previewSp = with(density) { legacyPoppinsSize(context, 13).toSp() }
    val fieldSp = with(density) { legacyPoppinsSize(context, 15).toSp() }

    val panelShape = RoundedCornerShape(24.dp)
    val primary = LocalContentColor.current
    val secondary = primary.copy(alpha = 0.62f)
    val accent = MaterialTheme.colorScheme.primary

    LaunchedEffect(notification.id) {
        InAppNotificationService.pauseDismissTimer()
        ChatAccessCoordinator.ensureAccess()
        delay(200)
        // iOS: si canReply tras ensureAccess → focus
        if (ChatAccessCoordinator.isAvailable && !notification.conversationId.isNullOrBlank()) {
            runCatching { focusRequester.requestFocus() }
        }
    }
    DisposableEffect(Unit) {
        onDispose { InAppNotificationService.resumeDismissTimerIfNeeded() }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .momentsChromeGlass(panelShape, interactive = true)
            .border(1.dp, accent.copy(alpha = 0.25f), panelShape)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AsyncProfileImageView(
                userId = notification.senderId,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = notification.senderUsername,
                    color = primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = titleSp,
                )
                Text(
                    text = stringResource(R.string.notification_action_reply),
                    color = secondary,
                    fontWeight = FontWeight.Medium,
                    fontSize = subtitleSp,
                )
            }
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = null,
                tint = secondary,
                modifier = Modifier
                    .size(20.dp)
                    .clickable(onClick = onDismiss),
            )
        }

        previewText?.let { preview ->
            Text(
                text = preview,
                color = secondary,
                fontSize = previewSp,
                maxLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        primary.copy(alpha = if (isDark) 0.08f else 0.05f),
                        RoundedCornerShape(16.dp),
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }

        if (canReply) {
            val trimmedEmpty = replyText.trim().isEmpty()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BasicTextField(
                    value = replyText,
                    onValueChange = { replyText = it },
                    textStyle = TextStyle(color = primary, fontSize = fieldSp),
                    cursorBrush = SolidColor(accent),
                    maxLines = 4,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .background(
                            primary.copy(alpha = if (isDark) 0.1f else 0.06f),
                            RoundedCornerShape(percent = 50),
                        )
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    decorationBox = { inner ->
                        Box {
                            if (replyText.isEmpty()) {
                                Text(
                                    stringResource(R.string.notification_action_placeholder),
                                    color = secondary.copy(alpha = 0.9f),
                                    fontSize = fieldSp,
                                )
                            }
                            inner()
                        }
                    },
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clickable(enabled = !isSending && !trimmedEmpty) {
                            val conversationId = notification.conversationId ?: return@clickable
                            val senderId = FirebaseAuth.getInstance().currentUser?.uid ?: return@clickable
                            val trimmed = replyText.trim()
                            if (trimmed.isEmpty()) return@clickable
                            isSending = true
                            scope.launch {
                                // iOS: ChatService.shared.sendTextMessage(..., completion)
                                ChatService.sendTextMessage(conversationId, senderId, trimmed)
                                isSending = false
                                HapticManager.shared.success()
                                onDismiss()
                                InAppNotificationService.dismissManually()
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = secondary,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowUp,
                            contentDescription = null,
                            tint = if (trimmedEmpty) secondary else accent,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            }
        } else {
            Text(
                text = stringResource(R.string.chat_recovery_unavailable_title),
                color = secondary,
                fontWeight = FontWeight.Medium,
                fontSize = subtitleSp,
            )
        }
    }
}

/** Misma lógica que `previewText` en el Swift. */
private fun resolveQuickReplyPreview(
    notification: MomentsNotification,
    context: android.content.Context,
): String? {
    val copy = NotificationCopyResolver.resolve(notification)
    val body = copy.body?.trim().orEmpty()
    if (body.isNotEmpty()) {
        val singleText = context.getString(R.string.notification_message_single_text)
        val singleDefault = context.getString(R.string.notification_message_single_default)
        if (body == singleText || body == singleDefault) return null
        return body
    }
    val messageTypeRaw = notification.messageType ?: return null
    val type = MessageType.from(messageTypeRaw)
    if (type != MessageType.TEXT) {
        return type.conversationPreview(context)
    }
    return null
}
