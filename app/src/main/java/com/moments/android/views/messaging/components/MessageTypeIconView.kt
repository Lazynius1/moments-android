package com.moments.android.views.messaging.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import com.moments.android.models.MessageType

/** Port de `Views/Messaging/Components/MessageTypeIconView.swift`. */
@Composable
fun MessageTypeIconView(
    type: MessageType,
    tintColor: Color = Color.Gray,
    modifier: Modifier = Modifier,
) {
    type.attachmentIcon?.let { icon ->
        AttachmentIconView(icon, AttachmentIconPreset.MESSAGE_REQUEST_ROW, tintColor, modifier)
        return
    }
    Icon(type.fallbackIcon, contentDescription = null, tint = tintColor, modifier = modifier, )
}

val MessageType.attachmentIcon: AttachmentIcon?
    get() = when (this) {
        MessageType.GIF -> AttachmentIcon.GIF
        MessageType.LOCATION -> AttachmentIcon.LOCATION
        MessageType.IMAGE -> AttachmentIcon.PHOTOS
        MessageType.EPHEMERAL, MessageType.VIEW_ONCE_IMAGE, MessageType.VIEW_ONCE_VIDEO -> AttachmentIcon.EPHEMERAL
        MessageType.SHARED_MOMENT -> AttachmentIcon.SHARE
        else -> null
    }

private val MessageType.fallbackIcon: ImageVector
    get() = when (this) {
        MessageType.TEXT -> Icons.Default.TextFields
        MessageType.VIDEO -> Icons.Default.Videocam
        MessageType.AUDIO -> Icons.Default.AudioFile
        MessageType.STICKER -> Icons.Default.StickyNote2
        MessageType.FILE -> Icons.Default.InsertDriveFile
        MessageType.SHARED_STORY -> Icons.Default.PlayCircle
        MessageType.CHAT_NOTICE -> Icons.Default.Article
        else -> Icons.Default.Image
    }
