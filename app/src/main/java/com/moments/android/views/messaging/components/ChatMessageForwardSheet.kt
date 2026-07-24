package com.moments.android.views.messaging.components

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.models.EnhancedMessage

/** Port de `ForwardMessageWrapper`. */
data class ForwardMessageWrapper(val message: EnhancedMessage) { val id: String get() = message.id }

/** Datos que `ShareRecipientsPickerSheet` entrega a la vista de forward. */
data class ChatForwardRecipient(val userId: String, val name: String, val username: String? = null)

/** Port de `ChatMessageForwardSheet.swift`.
 * El selector de destinatarios se recibe como datos/callbacks para que el
 * contenedor de chat conserve su propia fuente de conversaciones y contactos.
 */
@Composable
fun ChatMessageForwardSheet(
    message: EnhancedMessage,
    recipients: List<ChatForwardRecipient>,
    onDismiss: () -> Unit,
    onForward: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by remember(message.id) { mutableStateOf<Set<String>>(emptySet()) }
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(MaterialTheme.colorScheme.surface).padding(20.dp)) {
        Text(stringResource(R.string.chat_forward_title), style = MaterialTheme.typography.titleLarge)
        message.content?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        }
        if (recipients.isEmpty()) {
            Text(stringResource(R.string.chat_forward_empty), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 28.dp))
        } else {
            LazyColumn(Modifier.weight(1f, fill = false).padding(top = 12.dp)) {
                items(recipients, key = { it.userId }) { recipient ->
                    val selectedRecipient = recipient.userId in selected
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable {
                            selected = if (selectedRecipient) selected - recipient.userId else selected + recipient.userId
                        }.padding(vertical = 11.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                            Text(recipient.name.take(1).uppercase(), fontSize = 14.sp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(recipient.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            recipient.username?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                        if (selectedRecipient) androidx.compose.material3.Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        Button(
            onClick = { if (selected.isNotEmpty()) { onForward(selected); onDismiss() } },
            enabled = selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        ) { Text(if (selected.isEmpty()) stringResource(R.string.chat_forward_send) else stringResource(R.string.chat_forward_selected, selected.size)) }
    }
}
