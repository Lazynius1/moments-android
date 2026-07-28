package com.moments.android.views.messaging.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.moments.android.R
import com.moments.android.utilities.HapticManager
import com.moments.android.views.feed.sharing.ShareRecipientsPickerSheet
import com.moments.android.views.messaging.core.EnhancedMessage

/** Port de `ForwardMessageWrapper`. */
data class ForwardMessageWrapper(val message: EnhancedMessage) {
    val id: String get() = message.id
}

/**
 * Port de `ChatMessageForwardSheet.swift`.
 * iOS: wrapper fino sobre `ShareRecipientsPickerSheet`.
 */
@Composable
fun ChatMessageForwardSheet(
    message: EnhancedMessage,
    onDismiss: () -> Unit,
    onForward: (Set<String>) -> Unit,
) {
    ShareRecipientsPickerSheet(
        title = stringResource(R.string.chat_forward_title),
        subtitle = message.content,
        showsBackButton = false,
        flexibleListHeight = true,
        onDismiss = onDismiss,
        onSend = { selectedUsers, _ ->
            if (selectedUsers.isEmpty()) return@ShareRecipientsPickerSheet
            onForward(selectedUsers)
            HapticManager.shared.success()
            onDismiss()
        },
    )
}

/** Destinatario legacy (callers antiguos). Preferir [ChatMessageForwardSheet] + ShareRecipients. */
data class ChatForwardRecipient(
    val userId: String,
    val username: String,
    val profileImagePath: String? = null,
)
