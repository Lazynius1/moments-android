package com.moments.android.views.settings

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.services.messaging.ChatCacheStore
import com.moments.android.services.messaging.ChatMediaAutoDownload
import com.moments.android.services.messaging.ChatMediaDownloadPolicy
import com.moments.android.services.messaging.ChatMediaRetention
import com.moments.android.services.messaging.ChatStorageBreakdown
import com.moments.android.services.persistence.LocalPersistenceService
import com.moments.android.views.story.StoryRingAvatarView

private data class ConversationStorageUsage(
    val id: String,
    val userId: String,
    val name: String,
    val bytes: Long,
)

/**
 * Port 1:1 de `ChatStorageSettingsView.swift` (343 líneas).
 */
@Composable
fun ChatStorageSettingsView(onNavigateBack: () -> Unit = {}) {
    val isDark = isSystemInDarkTheme()
    val primary = if (isDark) Color.White else Color.Black
    val secondary = if (isDark) Color.White.copy(alpha = 0.65f) else Color.Black.copy(alpha = 0.55f)
    val context = LocalContext.current

    var breakdown by remember { mutableStateOf(ChatCacheStore.storageBreakdown()) }
    var autoDownload by remember { mutableStateOf(ChatMediaDownloadPolicy.autoDownload) }
    var retention by remember { mutableStateOf(ChatMediaDownloadPolicy.retention) }
    var showClearMediaConfirm by remember { mutableStateOf(false) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var conversationUsage by remember { mutableStateOf<List<ConversationStorageUsage>>(emptyList()) }
    var visibleCount by remember { mutableIntStateOf(10) }

    fun refreshUsage() {
        breakdown = ChatCacheStore.storageBreakdown()
        val conversations = LocalPersistenceService.loadConversations()
        val byId = conversations.mapNotNull { convo ->
            val id = convo.id ?: return@mapNotNull null
            id to convo
        }.toMap()
        val bytesById = ChatCacheStore.bytesByConversation(byId.keys.toList())
        conversationUsage = bytesById.entries
            .sortedByDescending { it.value }
            .map { (id, bytes) ->
                val convo = byId[id]
                ConversationStorageUsage(
                    id = id,
                    userId = convo?.otherParticipantId.orEmpty(),
                    name = convo?.otherParticipantUsername
                        ?.takeIf { it.isNotBlank() }
                        ?: context.getString(R.string.common_user_fallback),
                    bytes = bytes,
                )
            }
    }

    LaunchedEffect(Unit) {
        refreshUsage()
    }

    SettingsSubsectionWrapper(
        title = stringResource(R.string.settings_chat_storage_title),
        onNavigateBack = onNavigateBack,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
                .padding(top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            SummaryHeader(
                used = breakdown.totalMediaBytes,
                quota = ChatMediaDownloadPolicy.maxMediaBytes,
                primary = primary,
                secondary = secondary,
            )

            if (conversationUsage.isNotEmpty()) {
                ManageStorageSection(
                    usage = conversationUsage,
                    visibleCount = visibleCount,
                    onShowMore = { visibleCount += 10 },
                    onClearConversation = { conversationId ->
                        ChatCacheStore.deleteConversation(conversationId, emptyList())
                        refreshUsage()
                        statusMessage = context.getString(R.string.settings_chat_storage_clear_media_done)
                    },
                    primary = primary,
                    secondary = secondary,
                )
            }

            PreferencesSection(
                autoDownload = autoDownload,
                onAutoDownload = {
                    autoDownload = it
                    ChatMediaDownloadPolicy.autoDownload = it
                },
                retention = retention,
                onRetention = {
                    retention = it
                    ChatMediaDownloadPolicy.retention = it
                    ChatCacheStore.enforceRetention()
                    refreshUsage()
                },
                primary = primary,
                secondary = secondary,
            )

            ActionsSection(
                primary = primary,
                secondary = secondary,
                onClearMedia = { showClearMediaConfirm = true },
                onClearAll = { showClearAllConfirm = true },
            )

            statusMessage?.let { message ->
                Text(
                    message,
                    fontSize = 13.sp,
                    color = secondary,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }

    if (showClearMediaConfirm) {
        AlertDialog(
            onDismissRequest = { showClearMediaConfirm = false },
            title = { Text(stringResource(R.string.settings_chat_storage_clear_media_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        ChatCacheStore.clearAllMedia()
                        refreshUsage()
                        statusMessage = context.getString(R.string.settings_chat_storage_clear_media_done)
                        showClearMediaConfirm = false
                    },
                ) {
                    Text(
                        stringResource(R.string.settings_chat_storage_clear_media_action),
                        color = Color(0xFFFF3B30),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearMediaConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text(stringResource(R.string.settings_chat_storage_clear_all_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        LocalPersistenceService.clearAllChatCache()
                        refreshUsage()
                        statusMessage = context.getString(R.string.settings_chat_storage_clear_all_done)
                        showClearAllConfirm = false
                    },
                ) {
                    Text(
                        stringResource(R.string.settings_chat_storage_clear_all_action),
                        color = Color(0xFFFF3B30),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun SummaryHeader(
    used: Long,
    quota: Long,
    primary: Color,
    secondary: Color,
) {
    val context = LocalContext.current
    val fraction = if (quota > 0) minOf(1.0, used.toDouble() / quota.toDouble()) else 0.0

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            formatBytes(context, used),
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = primary,
            fontFamily = FontFamily.Monospace,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(primary.copy(alpha = 0.1f)),
        ) {
            val barFraction = if (fraction <= 0.0) 0f else fraction.toFloat().coerceIn(0.02f, 1f)
            Box(
                Modifier
                    .fillMaxWidth(barFraction)
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF007AFF)),
            )
        }
        Text(
            stringResource(R.string.settings_chat_storage_summary_limit, formatBytes(context, quota)),
            fontSize = 13.sp,
            color = secondary,
        )
    }
}

@Composable
private fun ManageStorageSection(
    usage: List<ConversationStorageUsage>,
    visibleCount: Int,
    onShowMore: () -> Unit,
    onClearConversation: (String) -> Unit,
    primary: Color,
    secondary: Color,
) {
    val visible = usage.take(visibleCount)
    val context = LocalContext.current

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(
            Modifier.padding(start = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                stringResource(R.string.settings_chat_storage_manage_title).uppercase(),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = secondary.copy(alpha = 0.9f),
            )
            Text(
                stringResource(R.string.settings_chat_storage_manage_subtitle),
                fontSize = 13.sp,
                color = secondary,
            )
        }

        Column(Modifier.fillMaxWidth()) {
            visible.forEachIndexed { index, item ->
                ConversationStorageRow(
                    usage = item,
                    bytesLabel = formatBytes(context, item.bytes),
                    onClear = { onClearConversation(item.id) },
                    primary = primary,
                    secondary = secondary,
                )
                if (index < visible.lastIndex) {
                    HorizontalDivider(
                        Modifier.padding(start = 56.dp),
                        color = secondary.copy(alpha = 0.2f),
                    )
                }
            }
        }

        if (usage.size > visibleCount) {
            Text(
                stringResource(R.string.settings_chat_storage_manage_show_more),
                modifier = Modifier
                    .padding(start = 4.dp, top = 4.dp)
                    .clickable(onClick = onShowMore),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF007AFF),
            )
        }
    }
}

@Composable
private fun ConversationStorageRow(
    usage: ConversationStorageUsage,
    bytesLabel: String,
    onClear: () -> Unit,
    primary: Color,
    secondary: Color,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (usage.userId.isNotBlank()) {
            StoryRingAvatarView(userId = usage.userId, size = 40.dp, lineWidth = 2.dp)
        } else {
            Box(
                Modifier
                    .size(40.dp)
                    .background(Color.Gray.copy(alpha = 0.25f), CircleShape),
            )
        }
        Text(
            usage.name,
            modifier = Modifier.weight(1f),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            bytesLabel,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = secondary,
            fontFamily = FontFamily.Monospace,
        )
        IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(R.string.settings_chat_storage_clear_media_action),
                tint = Color(0xFFFF3B30),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun PreferencesSection(
    autoDownload: ChatMediaAutoDownload,
    onAutoDownload: (ChatMediaAutoDownload) -> Unit,
    retention: ChatMediaRetention,
    onRetention: (ChatMediaRetention) -> Unit,
    primary: Color,
    secondary: Color,
) {
    var retentionMenu by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            stringResource(R.string.settings_chat_storage_preferences_title).uppercase(),
            modifier = Modifier.padding(start = 4.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = secondary.copy(alpha = 0.9f),
        )

        Column(
            Modifier.padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.settings_chat_storage_auto_download_title),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = primary,
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(primary.copy(alpha = 0.08f))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                ChatMediaAutoDownload.entries.forEach { option ->
                    val selected = autoDownload == option
                    Text(
                        stringResource(option.titleRes),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) Color(0xFF007AFF) else Color.Transparent)
                            .clickable { onAutoDownload(option) }
                            .padding(vertical = 8.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) Color.White else primary,
                        maxLines = 1,
                    )
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.settings_chat_storage_retention_title),
                modifier = Modifier.weight(1f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = primary,
            )
            Box {
                Row(
                    Modifier.clickable { retentionMenu = true },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        stringResource(retention.titleRes),
                        fontSize = 15.sp,
                        color = secondary,
                    )
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = secondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                DropdownMenu(
                    expanded = retentionMenu,
                    onDismissRequest = { retentionMenu = false },
                ) {
                    ChatMediaRetention.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(stringResource(option.titleRes)) },
                            onClick = {
                                onRetention(option)
                                retentionMenu = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionsSection(
    primary: Color,
    secondary: Color,
    onClearMedia: () -> Unit,
    onClearAll: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        ActionRow(
            title = stringResource(R.string.settings_chat_storage_clear_media_action),
            subtitle = stringResource(R.string.settings_chat_storage_clear_media_subtitle),
            destructive = false,
            primary = primary,
            secondary = secondary,
            onClick = onClearMedia,
        )
        HorizontalDivider(
            Modifier.padding(start = 4.dp),
            color = secondary.copy(alpha = 0.2f),
        )
        ActionRow(
            title = stringResource(R.string.settings_chat_storage_clear_all_action),
            subtitle = stringResource(R.string.settings_chat_storage_clear_all_subtitle),
            destructive = true,
            primary = primary,
            secondary = secondary,
            onClick = onClearAll,
        )
    }
}

@Composable
private fun ActionRow(
    title: String,
    subtitle: String,
    destructive: Boolean,
    primary: Color,
    secondary: Color,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (destructive) Color(0xFFFF3B30) else primary,
        )
        Text(subtitle, fontSize = 13.sp, color = secondary)
    }
}

private val ChatMediaAutoDownload.titleRes: Int
    get() = when (this) {
        ChatMediaAutoDownload.WIFI_ONLY -> R.string.settings_chat_storage_auto_download_wifi
        ChatMediaAutoDownload.ALWAYS -> R.string.settings_chat_storage_auto_download_always
        ChatMediaAutoDownload.NEVER -> R.string.settings_chat_storage_auto_download_never
    }

private val ChatMediaRetention.titleRes: Int
    get() = when (this) {
        ChatMediaRetention.DAYS_7 -> R.string.settings_chat_storage_retention_7days
        ChatMediaRetention.DAYS_30 -> R.string.settings_chat_storage_retention_30days
        ChatMediaRetention.DAYS_90 -> R.string.settings_chat_storage_retention_90days
        ChatMediaRetention.FOREVER -> R.string.settings_chat_storage_retention_forever
    }

private fun formatBytes(context: android.content.Context, bytes: Long): String =
    Formatter.formatFileSize(context, bytes)
