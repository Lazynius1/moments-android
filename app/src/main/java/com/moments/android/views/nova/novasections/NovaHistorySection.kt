package com.moments.android.views.nova.novasections

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.extensions.timeAgoDisplay
import com.moments.android.views.nova.NovaConversationTitle
import com.moments.android.views.nova.agent.NovaAgent
import com.moments.android.views.nova.novacore.NovaColors
import kotlinx.coroutines.launch

@Composable
fun ConversationHistoryOverlay(agent: NovaAgent, showConversationHistory: (Boolean) -> Unit, showSuggestedOptions: (Boolean) -> Unit) = Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = .3f)).clickable { showConversationHistory(false) }) {
    Column(Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp, vertical = 40.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(NovaColors.background).clickable(enabled = false) {}) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.nova_recent_conversations), color = NovaColors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f)); Icon(Icons.Default.Close, stringResource(R.string.common_close), tint = NovaColors.textPrimary, modifier = Modifier.size(36.dp).clip(CircleShape).background(NovaColors.materialBackground).clickable { showConversationHistory(false) }.padding(10.dp)) }
        if (agent.conversationTitles.isEmpty()) Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) { Icon(Icons.Default.MoreVert, null, tint = NovaColors.textSecondary, modifier = Modifier.size(40.dp)); Text(stringResource(R.string.nova_no_conversations), color = NovaColors.textSecondary, fontSize = 16.sp, fontWeight = FontWeight.Medium); Text(stringResource(R.string.nova_start_new_conversation), color = NovaColors.textTertiary, fontSize = 14.sp, textAlign = TextAlign.Center) } else { Text(stringResource(R.string.nova_new_conversation), color = NovaColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp).fillMaxWidth().clip(CircleShape).background(NovaColors.materialBackground).clickable { agent.startNewConversation(); showConversationHistory(false); showSuggestedOptions(true) }.padding(horizontal = 20.dp)); LazyColumn(Modifier.heightIn(max = 480.dp).padding(top = 12.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { items(agent.conversationTitles.reversed(), key = { it.id }) { conversation -> ConversationHistoryItem(conversation, agent, { showConversationHistory(false); showSuggestedOptions(false) }, Modifier.padding(horizontal = 20.dp)) } } }
    }
}

@Composable
fun ConversationHistoryItem(conversation: NovaConversationTitle, agent: NovaAgent, onSelect: () -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope(); var menu by remember { mutableStateOf(false) }; var showDelete by remember { mutableStateOf(false) }
    if (showDelete) AlertDialog(onDismissRequest = { showDelete = false }, title = { Text(stringResource(R.string.nova_delete_conversation_title)) }, text = { Text(stringResource(R.string.nova_delete_conversation_confirm)) }, confirmButton = { Text(stringResource(R.string.common_delete), modifier = Modifier.clickable { showDelete = false; scope.launch { agent.deleteConversation(conversation.id) } }.padding(16.dp)) }, dismissButton = { Text(stringResource(R.string.common_cancel), modifier = Modifier.clickable { showDelete = false }.padding(16.dp)) })
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(NovaColors.materialBackground).clickable { scope.launch { agent.loadConversation(conversation.id); onSelect() } }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(conversation.title, color = NovaColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis); Text(conversation.lastUpdated.timeAgoDisplay(), color = NovaColors.textSecondary, fontSize = 12.sp); if (conversation.messageCount > 0) Text(stringResource(R.string.nova_messages_count, conversation.messageCount), color = NovaColors.textTertiary, fontSize = 11.sp) }; Box { Icon(Icons.Default.MoreVert, stringResource(R.string.nova_actions_delete), tint = NovaColors.textPrimary, modifier = Modifier.size(34.dp).clip(CircleShape).clickable { menu = true }.padding(8.dp)); DropdownMenu(menu, { menu = false }) { DropdownMenuItem(text = { Text(stringResource(R.string.nova_actions_delete)) }, onClick = { menu = false; showDelete = true }, leadingIcon = { Icon(Icons.Default.Delete, null) }) } } }
}
