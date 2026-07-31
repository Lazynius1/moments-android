package com.moments.android.views.nova.novasections

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.extensions.timeAgoDisplay
import com.moments.android.views.messaging.components.AttachmentIcon
import com.moments.android.views.messaging.components.AttachmentIconPreset
import com.moments.android.views.messaging.components.AttachmentIconView
import com.moments.android.views.nova.NovaConversationTitle
import com.moments.android.views.nova.agent.NovaAgent
import com.moments.android.views.nova.novacore.NovaColors
import kotlinx.coroutines.launch

/**
 * Port de `Views/Nova/NovaSections/NovaHistorySection.swift`.
 * Overlay de historial + fila de conversación con menú borrar.
 */
@Composable
fun ConversationHistoryOverlay(
    agent: NovaAgent,
    showConversationHistory: (Boolean) -> Unit,
    showSuggestedOptions: (Boolean) -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val scrim = Color.Black.copy(alpha = if (isDark) 0.5f else 0.3f)
    val sheetShape = RoundedCornerShape(28.dp)
    val maxListHeight = (LocalConfiguration.current.screenHeightDp * 0.6f).dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scrim)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) { showConversationHistory(false) },
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp, top = 6.dp)
                .fillMaxWidth()
                .momentsChromeGlass(sheetShape, interactive = false)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {},
                ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.nova_recent_conversations),
                    color = NovaColors.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .momentsChromeGlass(CircleShape, interactive = true)
                        .clickable { showConversationHistory(false) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.common_close),
                        tint = NovaColors.textPrimary,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }

            if (agent.conversationTitles.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    AttachmentIconView(
                        icon = AttachmentIcon.COMMENTS,
                        preset = AttachmentIconPreset.EMPTY_STATE_HERO,
                        tintColor = NovaColors.textSecondary,
                    )
                    Text(
                        text = stringResource(R.string.nova_no_conversations),
                        color = NovaColors.textSecondary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = stringResource(R.string.nova_start_new_conversation),
                        color = NovaColors.textTertiary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .padding(top = 16.dp)
                        .fillMaxWidth()
                        .momentsChromeGlass(CircleShape, interactive = true)
                        .clickable {
                            agent.startNewConversation()
                            showConversationHistory(false)
                            showSuggestedOptions(true)
                        }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .momentsChromeGlass(CircleShape, interactive = true),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = NovaColors.textPrimary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Text(
                        text = stringResource(R.string.nova_new_conversation),
                        color = NovaColors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 12.dp).weight(1f),
                    )
                    Text("›", color = NovaColors.textSecondary, fontSize = 18.sp)
                }

                LazyColumn(
                    modifier = Modifier
                        .heightIn(max = maxListHeight)
                        .padding(top = 12.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(agent.conversationTitles.reversed(), key = { it.id }) { conversation ->
                        ConversationHistoryItem(
                            conversation = conversation,
                            agent = agent,
                            onSelect = {
                                showConversationHistory(false)
                                showSuggestedOptions(false)
                            },
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun ConversationHistoryItem(
    conversation: NovaConversationTitle,
    agent: NovaAgent,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(stringResource(R.string.nova_delete_conversation_title)) },
            text = { Text(stringResource(R.string.nova_delete_conversation_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDelete = false
                        scope.launch { agent.deleteConversation(conversation.id) }
                    },
                ) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .momentsChromeGlass(RoundedCornerShape(18.dp), interactive = true)
            .clickable {
                scope.launch {
                    agent.loadConversation(conversation.id)
                    onSelect()
                }
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = conversation.title,
                color = NovaColors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = conversation.lastUpdated.timeAgoDisplay(),
                color = NovaColors.textSecondary,
                fontSize = 12.sp,
            )
            if (conversation.messageCount > 0) {
                Text(
                    text = stringResource(R.string.nova_messages_count, conversation.messageCount),
                    color = NovaColors.textTertiary,
                    fontSize = 11.sp,
                )
            }
        }
        Box {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .momentsChromeGlass(CircleShape, interactive = true)
                    .clickable { menu = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.nova_actions_delete),
                    tint = NovaColors.textPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.nova_actions_delete)) },
                    onClick = {
                        menu = false
                        showDelete = true
                    },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                )
            }
        }
    }
}
