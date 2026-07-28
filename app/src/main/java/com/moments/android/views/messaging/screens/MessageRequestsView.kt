package com.moments.android.views.messaging.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Message
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.services.messaging.MessageRequestService
import com.moments.android.utilities.MomentsFormat
import com.moments.android.utilities.momentsEmptyStateAppear
import com.moments.android.views.feed.AdaptiveColors
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.messaging.core.MessageRequest

/**
 * Port de `Views/Messaging/Screens/MessageRequestsView.swift`.
 *
 * Si [service] viene del padre (p.ej. [MessagingView]), no se llaman `removeAllListeners`
 * al salir: el padre sigue dueño del ciclo de vida del listener.
 */
@Composable
fun MessageRequestsView(
    service: MessageRequestService? = null,
    onOpenRequest: (MessageRequest) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    val ownedService = remember { MessageRequestService() }
    val requestService = service ?: ownedService
    val ownsListeners = service == null
    val requests by requestService.pendingRequests.collectAsState()
    var actionRequest by remember { mutableStateOf<MessageRequest?>(null) }

    DisposableEffect(requestService, ownsListeners) {
        FirebaseAuth.getInstance().currentUser?.uid?.let(requestService::listenToPendingRequests)
        onDispose {
            if (ownsListeners) requestService.removeAllListeners()
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.surfaceBackground),
    ) {
        if (requests.isNotEmpty()) {
            RequestCountHeader(count = requests.size, colors = colors)
        }
        if (requests.isEmpty()) {
            MessageRequestsEmptyState(colors = colors, modifier = Modifier.weight(1f))
        } else {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(top = 2.dp, bottom = 24.dp),
            ) {
                items(
                    requests,
                    key = { it.id ?: "${it.senderId}_${it.timestamp.time}" },
                ) { request ->
                    RequestListRow(
                        request = request,
                        onTap = { onOpenRequest(request) },
                        onAction = { actionRequest = request },
                    )
                }
            }
        }
    }

    actionRequest?.let { request ->
        MessageRequestActionDialog(
            onDismiss = { actionRequest = null },
            onAccept = {
                requestService.acceptRequest(request) { }
                actionRequest = null
            },
            onDelete = {
                requestService.rejectRequest(request) { }
                actionRequest = null
            },
            onBlock = {
                requestService.blockUser(request) { }
                actionRequest = null
            },
        )
    }
}

@Composable
private fun RequestCountHeader(count: Int, colors: AdaptiveColors) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(R.string.message_requests_count, count),
            color = colors.secondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .momentsChromeGlass(RoundedCornerShape(percent = 50), interactive = false)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun MessageRequestsEmptyState(
    colors: AdaptiveColors,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .momentsEmptyStateAppear()
            .padding(top = 96.dp)
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            Icons.Outlined.Message,
            contentDescription = null,
            tint = colors.secondary.copy(alpha = 0.72f),
            modifier = Modifier.size(28.dp),
        )
        Text(
            stringResource(R.string.message_requests_empty_title),
            color = colors.primary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.message_requests_empty_description),
            color = colors.secondary,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/** ≡ iOS `.confirmationDialog` de MessageRequestsView. */
@Composable
private fun MessageRequestActionDialog(
    onDismiss: () -> Unit,
    onAccept: () -> Unit,
    onDelete: () -> Unit,
    onBlock: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.message_requests_action_title)) },
        text = { Text(stringResource(R.string.message_requests_action_message)) },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onAccept) {
                    Text(stringResource(R.string.message_requests_accept))
                }
                TextButton(onClick = onDelete) {
                    Text(
                        stringResource(R.string.message_requests_delete),
                        color = androidx.compose.ui.graphics.Color(0xFFFF3B30),
                    )
                }
                TextButton(onClick = onBlock) {
                    Text(
                        stringResource(R.string.message_requests_block_user),
                        color = androidx.compose.ui.graphics.Color(0xFFFF3B30),
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        },
        dismissButton = {},
    )
}

@Composable
fun RequestListRow(
    request: MessageRequest,
    onTap: () -> Unit,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    val context = LocalContext.current
    val relativeTime = MomentsFormat.relativeTime(from = request.timestamp)

    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(colors.secondary.copy(alpha = 0.12f))
                .clickable(onClick = onTap),
            contentAlignment = Alignment.Center,
        ) {
            val path = request.senderProfileImagePath
            if (path.isNullOrBlank()) {
                Icon(Icons.Filled.Person, contentDescription = null, tint = colors.secondary)
            } else {
                AsyncImage(
                    model = path,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }

        Row(
            Modifier
                .weight(1f)
                .clickable(onClick = onTap),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    request.senderUsername
                        ?: stringResource(R.string.messaging_user_default),
                    color = colors.primary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    request.messagePreview(context),
                    color = colors.secondary,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                relativeTime,
                color = colors.secondary,
                fontSize = 12.sp,
                maxLines = 1,
            )
        }

        Box(
            Modifier
                .size(34.dp)
                .momentsChromeGlass(CircleShape, interactive = true)
                .clickable(onClick = onAction),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.MoreHoriz,
                contentDescription = null,
                tint = colors.secondary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
