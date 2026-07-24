package com.moments.android.views.messaging.screens

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.models.MessageRequest
import com.moments.android.services.messaging.MessageRequestService
import com.moments.android.views.feed.rememberAdaptiveColors

/** Port de `Views/Messaging/Screens/MessageRequestsView.swift`. */
@Composable
fun MessageRequestsView(
    service: MessageRequestService = remember { MessageRequestService() },
    onOpenRequest: (MessageRequest) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    val requests by service.pendingRequests.collectAsState()
    var actionRequest by remember { mutableStateOf<MessageRequest?>(null) }
    DisposableEffect(service) {
        FirebaseAuth.getInstance().currentUser?.uid?.let(service::listenToPendingRequests)
        onDispose(service::removeAllListeners)
    }
    Column(modifier.fillMaxSize().background(colors.chatBackground.first())) {
        if (requests.isNotEmpty()) {
            Text(stringResource(R.string.message_requests_count, requests.size), color = colors.secondary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 12.dp))
        }
        if (requests.isEmpty()) MessageRequestsEmptyState(colors, Modifier.weight(1f))
        else LazyColumn(Modifier.fillMaxSize()) {
            items(requests, key = { it.id ?: "${it.senderId}_${it.timestamp.time}" }) { request ->
                RequestListRow(request, onTap = { onOpenRequest(request) }, onAction = { actionRequest = request })
            }
        }
    }
    actionRequest?.let { request ->
        AlertDialog(
            onDismissRequest = { actionRequest = null },
            title = { Text(stringResource(R.string.message_requests_action_title)) },
            text = { Text(stringResource(R.string.message_requests_action_message)) },
            confirmButton = {
                Row {
                    Text(stringResource(R.string.message_requests_accept), modifier = Modifier.clickable { service.acceptRequest(request) {}; actionRequest = null }.padding(12.dp))
                    Text(stringResource(R.string.message_requests_delete), color = MaterialTheme.colorScheme.error, modifier = Modifier.clickable { service.rejectRequest(request) {}; actionRequest = null }.padding(12.dp))
                }
            },
            dismissButton = { Text(stringResource(R.string.message_requests_block_user), color = MaterialTheme.colorScheme.error, modifier = Modifier.clickable { service.blockUser(request) {}; actionRequest = null }.padding(12.dp)) },
        )
    }
}

@Composable
private fun MessageRequestsEmptyState(colors: com.moments.android.views.feed.AdaptiveColors, modifier: Modifier = Modifier) = Column(modifier.fillMaxWidth().padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
    Icon(Icons.Default.Message, null, tint = colors.secondary.copy(.72f), modifier = Modifier.size(28.dp))
    Text(stringResource(R.string.message_requests_empty_title), color = colors.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
    Text(stringResource(R.string.message_requests_empty_description), color = colors.secondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
}

@Composable
fun RequestListRow(request: MessageRequest, onTap: () -> Unit, onAction: () -> Unit, modifier: Modifier = Modifier) {
    val colors = rememberAdaptiveColors()
    Row(modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(colors.secondary.copy(.12f)).clickable(onClick = onTap), contentAlignment = Alignment.Center) {
            if (request.senderProfileImagePath.isNullOrBlank()) Icon(Icons.Default.Person, null, tint = colors.secondary)
            else AsyncImage(request.senderProfileImagePath, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Column(Modifier.weight(1f).clickable(onClick = onTap)) {
            Text(request.senderUsername ?: stringResource(R.string.messaging_user_default), color = colors.primary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(request.message, color = colors.secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(DateUtils.getRelativeTimeSpanString(request.timestamp.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(), color = colors.secondary, style = MaterialTheme.typography.labelSmall)
        IconButton(onClick = onAction) { Icon(Icons.Default.MoreVert, null, tint = colors.secondary) }
    }
}
