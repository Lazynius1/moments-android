package com.moments.android.views.messaging.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.coordinators.AsyncProfileImageView
import com.moments.android.models.AppUser
import com.moments.android.models.OnlineStatus

/** Port de `Views/Messaging/Components/MessagingComposerAndStatusViews.swift`. */
@Composable
fun MessageComposerView(
    selectedUser: AppUser?,
    messageText: String,
    onMessageTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val primary = if (isDark) Color.White else Color(0xFF0B1215)
    val secondary = if (isDark) Color.White.copy(.65f) else Color.Black.copy(.55f)
    val canSend = messageText.trim().isNotEmpty()
    Column(modifier.fillMaxSize().background(if (isDark) Color(0xFF0B1215) else Color(0xFFFAF9F6)).padding(20.dp)) {
        Text(stringResource(R.string.common_cancel), color = Color(0xFF007AFF), modifier = Modifier.clickable(onClick = onDismiss).padding(bottom = 16.dp))
        selectedUser?.let { user ->
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AsyncProfileImageView(user.id, Modifier.size(60.dp).background(Color.Gray, CircleShape))
                Text(user.username, color = primary, fontSize = 20.sp)
                Text(stringResource(R.string.messaging_write_message_to_start), color = secondary, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.weight(1f))
        OutlinedTextField(messageText, onMessageTextChange, placeholder = { Text(stringResource(R.string.messaging_compose_placeholder)) }, modifier = Modifier.fillMaxWidth().height(132.dp), maxLines = 6)
        Button(onClick = onSend, enabled = canSend, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Icon(Icons.Default.Send, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.messaging_send_message))
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun OnlineStatusSelectorView(currentStatus: OnlineStatus, onStatusSelected: (OnlineStatus) -> Unit, onDismiss: () -> Unit) {
    val isDark = isSystemInDarkTheme()
    val primary = if (isDark) Color.White else Color(0xFF0B1215)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false), containerColor = if (isDark) Color(0xFF0B1215) else Color(0xFFFAF9F6)) {
        Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
            Column(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.messaging_status_current), color = primary.copy(.6f), fontSize = 14.sp)
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Circle, null, tint = currentStatus.color, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(10.dp)); Text(stringResource(currentStatus.displayNameRes), color = primary, fontSize = 24.sp) }
            }
            Spacer(Modifier.height(14.dp))
            OnlineStatus.entries.forEach { status ->
                Row(Modifier.fillMaxWidth().background(if (status == currentStatus) (if (isDark) Color.White else Color.Black).copy(.06f) else Color.Transparent).clickable { onStatusSelected(status); onDismiss() }.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(status.icon, null, tint = status.color, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp)); Text(stringResource(status.displayNameRes), color = primary, fontSize = 16.sp); Spacer(Modifier.weight(1f))
                    if (status == currentStatus) Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF007AFF))
                }
            }
        }
    }
}

private val OnlineStatus.displayNameRes: Int get() = when (this) { OnlineStatus.ONLINE -> R.string.messaging_status_online; OnlineStatus.AWAY -> R.string.messaging_status_away; OnlineStatus.BUSY -> R.string.messaging_status_busy; OnlineStatus.OFFLINE -> R.string.messaging_status_offline; OnlineStatus.INVISIBLE -> R.string.messaging_status_invisible }
private val OnlineStatus.color: Color get() = when (this) { OnlineStatus.ONLINE -> Color(0xFF34C759); OnlineStatus.AWAY -> Color(0xFFFF9500); OnlineStatus.BUSY -> Color(0xFFFF3B30); OnlineStatus.OFFLINE -> Color.Gray; OnlineStatus.INVISIBLE -> Color(0xFF8E8E93) }
private val OnlineStatus.icon: ImageVector get() = when (this) { OnlineStatus.ONLINE -> Icons.Default.Circle; OnlineStatus.AWAY -> Icons.Default.Schedule; OnlineStatus.BUSY -> Icons.Default.DoNotDisturbOn; OnlineStatus.OFFLINE -> Icons.Default.Circle; OnlineStatus.INVISIBLE -> Icons.Default.RemoveRedEye }
