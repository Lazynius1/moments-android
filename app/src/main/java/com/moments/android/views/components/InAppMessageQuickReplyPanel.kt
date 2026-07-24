package com.moments.android.views.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.models.ChatAccessState
import com.moments.android.models.MomentsNotification
import com.moments.android.notifications.services.InAppNotificationService
import com.moments.android.notifications.services.NotificationCopyResolver
import com.moments.android.views.messaging.services.ChatAccessCoordinator
import com.moments.android.views.messaging.services.ChatService
import kotlinx.coroutines.launch

/** Port de `InAppMessageQuickReplyPanel.swift`. */
@Composable
fun InAppMessageQuickReplyPanel(notification: MomentsNotification, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val access by ChatAccessCoordinator.accessState.collectAsState()
    var replyText by remember(notification.id) { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    val canReply = access is ChatAccessState.Available && !notification.conversationId.isNullOrBlank()
    val preview = NotificationCopyResolver.resolve(notification).body?.takeIf { it.isNotBlank() }

    LaunchedEffect(notification.id) {
        InAppNotificationService.pauseDismissTimer()
        ChatAccessCoordinator.ensureAccess()
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { InAppNotificationService.resumeDismissTimerIfNeeded() }
    }

    Column(
        modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xEE1C2025)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(.12f)), contentAlignment = Alignment.Center) {
                Text(notification.senderUsername.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(notification.senderUsername, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("Reply", color = Color.White.copy(.62f), fontSize = 12.sp)
            }
            Icon(Icons.Filled.Close, null, tint = Color.White.copy(.65f), modifier = Modifier.size(22.dp).clip(CircleShape).background(Color.White.copy(.06f)).clickable(onClick = onDismiss))
        }
        preview?.let { Text(it, color = Color.White.copy(.68f), fontSize = 13.sp, maxLines = 3, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(.08f)).padding(14.dp)) }
        if (canReply) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BasicTextField(replyText, { replyText = it }, textStyle = TextStyle(color = Color.White, fontSize = 15.sp), modifier = Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(Color.White.copy(.10f)).padding(horizontal = 14.dp, vertical = 12.dp), singleLine = false, maxLines = 4, decorationBox = { inner -> if (replyText.isEmpty()) Text("Message", color = Color.White.copy(.55f)); inner() })
                Icon(Icons.Filled.Send, null, tint = if (replyText.trim().isEmpty() || isSending) Color.White.copy(.35f) else Color(0xFF0A84FF), modifier = Modifier.size(32.dp).clip(CircleShape).background(Color.White.copy(.06f)).padding(6.dp).clickable(enabled = replyText.trim().isNotEmpty() && !isSending) {
                    val conversationId = notification.conversationId ?: return@clickable
                    val senderId = FirebaseAuth.getInstance().currentUser?.uid ?: return@clickable
                    isSending = true
                    scope.launch {
                        ChatService.sendTextMessage(conversationId, senderId, replyText.trim())
                        isSending = false
                        InAppNotificationService.dismissManually()
                        onDismiss()
                    }
                })
            }
        } else Text("Chat unavailable", color = Color.White.copy(.62f), fontSize = 12.sp)
    }
}
