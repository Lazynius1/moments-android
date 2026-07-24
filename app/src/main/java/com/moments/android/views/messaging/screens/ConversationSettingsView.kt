package com.moments.android.views.messaging.screens

import android.text.format.Formatter
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.moments.android.R
import com.moments.android.models.Conversation
import com.moments.android.models.EnhancedMessage
import com.moments.android.models.MessageType
import com.moments.android.services.messaging.ChatCacheStore
import com.moments.android.services.persistence.LocalPersistenceService
import com.moments.android.services.messaging.VanishMessageTimer
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.cache.UserCacheService
import com.moments.android.services.messaging.MessageCatchUpService
import com.moments.android.views.feed.AdaptiveColors
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.messaging.services.ChatService
import com.moments.android.views.messaging.services.ChatEncryptedMediaResolver
import com.moments.android.views.feed.video.FeedVideoPage
import kotlinx.coroutines.launch

/** Port de `Views/Messaging/Screens/ConversationSettingsView.swift`. */
enum class SharedContentTab { MEDIA, LINKS }

data class SharedMedia(
    val id: String,
    val type: Type,
    val thumbnailUrl: String,
    val originalUrl: String,
    val senderId: String,
    val timestamp: java.util.Date,
    val sourceMessage: EnhancedMessage? = null,
    val allowsSaving: Boolean = true,
) {
    enum class Type { IMAGE, VIDEO }
}

@Stable
class ConversationSettingsViewModel(
    private val currentUserId: String = FirebaseAuth.getInstance().currentUser?.uid.orEmpty(),
) {
    var conversation by mutableStateOf<Conversation?>(null)
        private set
    var totalMessages by mutableIntStateOf(0)
        private set
    var sentMessagesCount by mutableIntStateOf(0)
        private set
    var receivedMessagesCount by mutableIntStateOf(0)
        private set
    var conversationMediaBytes by mutableLongStateOf(0)
        private set
    var sharedMedia by mutableStateOf<List<SharedMedia>>(emptyList())
        private set
    var sharedGalleryMessages by mutableStateOf<List<EnhancedMessage>>(emptyList())
        private set
    var starredMessages by mutableStateOf<List<EnhancedMessage>>(emptyList())
        private set
    var notificationsEnabled by mutableStateOf(true)
    var liveOtherParticipantUsername by mutableStateOf("")
        private set
    var vanishModeActive by mutableStateOf(false)
        private set
    var vanishTimer by mutableStateOf(VanishMessageTimer.DEFAULT)
        private set
    var readReceiptsEnabled by mutableStateOf(true)
    var forwardingEnabled by mutableStateOf(true)
    var typingIndicatorEnabled by mutableStateOf(true)
    var messagePreviewEnabled by mutableStateOf(true)
    var buzzEnabled by mutableStateOf(true)

    fun loadConversationData(value: Conversation) {
        conversation = value
        vanishModeActive = value.vanishModeActive == true
        vanishTimer = VanishMessageTimer.fromStored(value.vanishMessageTimer)
        notificationsEnabled = !value.isMuted(currentUserId)
        conversationMediaBytes = value.id?.let(ChatCacheStore::bytes) ?: 0L
        value.id?.let { conversationId ->
            processMessages(LocalPersistenceService.loadMessagesFast(conversationId))
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                MessageCatchUpService.sync(conversationId)
                val refreshed = LocalPersistenceService.loadMessagesFast(conversationId)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { processMessages(refreshed) }
            }
        }
        value.otherParticipantId.takeIf { it.isNotBlank() }?.let { userId ->
            UserCacheService.refreshUser(userId) { user ->
                liveOtherParticipantUsername = user?.username?.trim().orEmpty()
            }
        }
    }

    fun refreshMediaUsage() { conversationMediaBytes = conversation?.id?.let(ChatCacheStore::bytes) ?: 0L }

    fun clearConversationMedia() {
        conversation?.id?.let { ChatCacheStore.deleteConversation(it, emptyList()) }
        refreshMediaUsage()
    }

    fun processMessages(messages: List<EnhancedMessage>) {
        totalMessages = messages.size
        val active = messages.filterNot { it.isDeleted }
        sentMessagesCount = active.count { it.senderId == currentUserId }
        receivedMessagesCount = active.size - sentMessagesCount
        sharedGalleryMessages = messages.filter(::isSharedGalleryEligible).sortedByDescending { it.timestamp }
        sharedMedia = sharedGalleryMessages.filter(::isSharedMedia).mapNotNull(::makeSharedMedia)
        starredMessages = active.filter { currentUserId in it.starredBy.orEmpty() }.sortedByDescending { it.timestamp }
    }

    fun sharedLinks(): List<EnhancedMessage> = sharedGalleryMessages.filter {
        it.type == MessageType.TEXT && LINK.containsMatchIn(it.content.orEmpty())
    }

    fun deleteForMe(message: EnhancedMessage) {
        val id = conversation?.id ?: return
        sharedGalleryMessages = sharedGalleryMessages.filterNot { it.id == message.id }
        sharedMedia = sharedMedia.filterNot { it.id == message.id }
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            ChatService.deleteMessageForMe(id, message.id, currentUserId)
        }
    }

    fun deleteForEveryone(message: EnhancedMessage) {
        if (message.senderId != currentUserId) return
        val id = conversation?.id ?: return
        sharedGalleryMessages = sharedGalleryMessages.filterNot { it.id == message.id }
        sharedMedia = sharedMedia.filterNot { it.id == message.id }
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            ChatService.deleteMessageForEveryone(id, message.id)
        }
    }

    fun toggleNotifications() {
        val id = conversation?.id ?: return
        notificationsEnabled = !notificationsEnabled
        FirebaseFirestore.getInstance().collection("conversations").document(id).update(
            "mutedByUserIds",
            if (notificationsEnabled) FieldValue.arrayRemove(currentUserId) else FieldValue.arrayUnion(currentUserId),
        )
    }

    fun updateVanish(active: Boolean, timer: VanishMessageTimer) {
        val id = conversation?.id ?: return
        vanishModeActive = active
        vanishTimer = timer
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            ChatService.setVanishMode(id, active, timer.takeIf { active })
            val conversationRef = FirebaseFirestore.getInstance().collection("conversations").document(id)
            if (active) {
                conversation?.vanishDisabledNoticeMessageId?.let { disabledId ->
                    ChatService.deleteMessageForEveryone(id, disabledId)
                    conversationRef.update("vanishDisabledNoticeMessageId", FieldValue.delete())
                    conversation?.vanishDisabledNoticeMessageId = null
                }
                val existingNoticeId = conversation?.vanishSettingsNoticeMessageId
                if (existingNoticeId != null) {
                    ChatService.updateChatNotice(id, existingNoticeId, timer.enabledNoticeToken)
                } else {
                    ChatService.sendChatNotice(id, currentUserId, timer.enabledNoticeToken).getOrNull()?.id?.let { noticeId ->
                        conversationRef.update("vanishSettingsNoticeMessageId", noticeId)
                        conversation?.vanishSettingsNoticeMessageId = noticeId
                    }
                }
            } else {
                conversation?.vanishSettingsNoticeMessageId?.let { enabledId ->
                    ChatService.deleteMessageForEveryone(id, enabledId)
                    conversationRef.update("vanishSettingsNoticeMessageId", FieldValue.delete())
                    conversation?.vanishSettingsNoticeMessageId = null
                }
                ChatService.sendChatNotice(id, currentUserId, VanishMessageTimer.DISABLED_NOTICE_TOKEN).getOrNull()?.id?.let { noticeId ->
                    conversationRef.update("vanishDisabledNoticeMessageId", noticeId)
                    conversation?.vanishDisabledNoticeMessageId = noticeId
                }
            }
        }
    }

    fun persistPreferences(context: android.content.Context) {
        val id = conversation?.id ?: return
        context.getSharedPreferences("conversation_settings", android.content.Context.MODE_PRIVATE).edit()
            .putBoolean("read_receipts_$id", readReceiptsEnabled)
            .putBoolean("forwarding_$id", forwardingEnabled)
            .putBoolean("typing_$id", typingIndicatorEnabled)
            .putBoolean("preview_$id", messagePreviewEnabled)
            .putBoolean("buzz_$id", buzzEnabled)
            .apply()
        FirebaseFirestore.getInstance().collection("conversations").document(id).update(
            mapOf(
                "readReceiptPreferences.$currentUserId" to readReceiptsEnabled,
                "forwardingPreferences.$currentUserId" to forwardingEnabled,
                "buzzPreferences.$currentUserId" to buzzEnabled,
            ),
        )
    }

    fun sendReplyToMedia(media: SharedMedia, text: String) {
        val id = conversation?.id ?: return
        val outgoing = text.trim()
        if (outgoing.isEmpty() || currentUserId.isEmpty()) return
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            ChatService.sendTextMessage(id, currentUserId, outgoing, replyTo = media.id, isVanishModeMessage = vanishModeActive)
        }
    }

    fun openMediaForViewing(media: SharedMedia, onResolved: (SharedMedia) -> Unit) {
        val source = media.sourceMessage
        if (source?.mediaObjectPath.isNullOrBlank() || source?.mediaEncryption == null) {
            onResolved(media)
            return
        }
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val resolved = ChatEncryptedMediaResolver.resolveForMessage(source, forceDownload = true)
            val updated = resolved?.mediaUrl?.let {
                media.copy(originalUrl = it, thumbnailUrl = resolved.thumbnailUrl ?: media.thumbnailUrl)
            } ?: media
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                sharedMedia = sharedMedia.map { if (it.id == updated.id) updated else it }
                onResolved(updated)
            }
        }
    }

    fun blockOtherParticipant(onBlocked: () -> Unit = {}) {
        val targetUserId = conversation?.otherParticipantId?.takeIf { it.isNotBlank() } ?: return
        if (currentUserId.isBlank()) return
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            FirestoreService().blockUser(currentUserId, targetUserId)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onBlocked() }
        }
    }

    fun clearConversation(onCleared: () -> Unit = {}) {
        val targetUserId = conversation?.otherParticipantId?.takeIf { it.isNotBlank() } ?: return
        if (currentUserId.isBlank()) return
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            ChatService.deleteConversationsBetweenUsers(currentUserId, targetUserId)
            conversation?.id?.let(LocalPersistenceService::deleteConversationCache)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onCleared() }
        }
    }

    private fun isSharedGalleryEligible(message: EnhancedMessage): Boolean =
        !message.isDeleted && (isSharedMedia(message) || (message.type == MessageType.TEXT && LINK.containsMatchIn(message.content.orEmpty())))

    private fun isSharedMedia(message: EnhancedMessage): Boolean =
        message.type in setOf(MessageType.IMAGE, MessageType.VIDEO) &&
            !message.isViewOnce && !message.isVanishModeMessage && message.storyReplyData == null &&
            (!message.mediaUrl.isNullOrBlank() || (!message.mediaObjectPath.isNullOrBlank() && message.mediaEncryption != null))

    private fun makeSharedMedia(message: EnhancedMessage): SharedMedia? {
        val (cachedMedia, cachedThumbnail) = ChatCacheStore.localURLsIfPresent(message)
        val original = cachedMedia ?: message.mediaUrl ?: cachedThumbnail ?: message.thumbnailUrl ?: return null
        return SharedMedia(
            id = message.id,
            type = if (message.type == MessageType.VIDEO) SharedMedia.Type.VIDEO else SharedMedia.Type.IMAGE,
            thumbnailUrl = cachedThumbnail ?: message.thumbnailUrl ?: original,
            originalUrl = original,
            senderId = message.senderId,
            timestamp = message.timestamp,
            sourceMessage = message,
            allowsSaving = !message.isVanishModeMessage && message.type != MessageType.EPHEMERAL,
        )
    }

    companion object { private val LINK = Regex("https?://\\S+", RegexOption.IGNORE_CASE) }
}

@Composable
fun ConversationSettingsView(
    conversation: Conversation,
    onBack: () -> Unit,
    onJumpToMessage: (String) -> Unit = {},
    onSearchRequested: () -> Unit = {},
    onProfile: (String) -> Unit = {},
    onReport: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    val model = remember(conversation.id) { ConversationSettingsViewModel() }
    var tab by remember { mutableStateOf(SharedContentTab.MEDIA) }
    var clearMediaConfirm by remember { mutableStateOf(false) }
    var showStarred by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showPreferences by remember { mutableStateOf(false) }
    var showVanish by remember { mutableStateOf(false) }
    var selectedMedia by remember { mutableStateOf<SharedMedia?>(null) }
    LaunchedEffect(conversation.id) { model.loadConversationData(conversation) }

    Column(modifier.fillMaxSize().background(colors.chatBackground.first())) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = colors.primary) }
            Text(stringResource(R.string.conversation_settings_title), modifier = Modifier.weight(1f), color = colors.primary, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = { showMenu = !showMenu }) { Icon(Icons.Default.MoreVert, null, tint = colors.primary) }
        }
        if (showMenu) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End) {
                Text(stringResource(R.string.conversation_settings_block), color = MaterialTheme.colorScheme.error, modifier = Modifier.clickable { model.blockOtherParticipant(onBack) }.padding(10.dp))
                Text(stringResource(R.string.conversation_settings_report), color = MaterialTheme.colorScheme.error, modifier = Modifier.clickable { onReport(conversation.otherParticipantId) }.padding(10.dp))
            }
        }
        ConversationSettingsHeader(conversation, model.liveOtherParticipantUsername, colors, model.notificationsEnabled, onProfile, onSearchRequested) { model.toggleNotifications() }
        SettingsRows(model, colors, onStarred = { showStarred = true }, onVanish = { showVanish = true }, onPreferences = { showPreferences = true }, onClearMedia = { clearMediaConfirm = true })
        SharedContentTabs(tab, { tab = it }, model, colors, onOpenMedia = { model.openMediaForViewing(it) { resolved -> selectedMedia = resolved } })
    }
    if (clearMediaConfirm) AlertDialog(
        onDismissRequest = { clearMediaConfirm = false },
        title = { Text(stringResource(R.string.conversation_settings_clear_media)) },
        text = { Text(stringResource(R.string.conversation_settings_media_reload)) },
        confirmButton = { Text(stringResource(R.string.conversation_settings_clear_media), modifier = Modifier.clickable { model.clearConversationMedia(); clearMediaConfirm = false }.padding(16.dp)) },
        dismissButton = { Text(stringResource(R.string.common_cancel), modifier = Modifier.clickable { clearMediaConfirm = false }.padding(16.dp)) },
    )
    if (showStarred) ConversationStarredMessagesSheet(model.starredMessages, onDismiss = { showStarred = false }, onSelect = { showStarred = false; onJumpToMessage(it) })
    if (showPreferences) ConversationChatPreferencesView(model, onDismiss = { showPreferences = false })
    if (showVanish) ConversationVanishModeView(model, onDismiss = { showVanish = false })
    selectedMedia?.let { media -> ConversationSettingsMediaViewer(media, onDismiss = { selectedMedia = null }, onSendReply = model::sendReplyToMedia) }
}

@Composable
private fun ConversationSettingsHeader(conversation: Conversation, liveUsername: String, colors: AdaptiveColors, notificationsEnabled: Boolean, onProfile: (String) -> Unit, onSearch: () -> Unit, onToggleMute: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AsyncImage(conversation.otherParticipantProfileImagePath, null, Modifier.size(92.dp).clip(CircleShape).background(colors.secondary.copy(.16f)), contentScale = ContentScale.Crop)
        Text(liveUsername.ifBlank { conversation.otherParticipantUsername.orEmpty() }, color = colors.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(34.dp)) {
            HeaderAction(Icons.Default.Person, R.string.conversation_settings_profile) { onProfile(conversation.otherParticipantId) }
            HeaderAction(Icons.Default.Search, R.string.conversation_settings_search, onSearch)
            HeaderAction(if (notificationsEnabled) Icons.Default.MoreVert else Icons.Default.MoreVert, if (notificationsEnabled) R.string.conversation_settings_mute else R.string.conversation_settings_unmute, onToggleMute)
        }
    }
}

@Composable
private fun HeaderAction(icon: androidx.compose.ui.graphics.vector.ImageVector, title: Int, action: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = action).padding(6.dp)) {
        Icon(icon, null)
        Text(stringResource(title), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SettingsRows(model: ConversationSettingsViewModel, colors: AdaptiveColors, onStarred: () -> Unit, onVanish: () -> Unit, onPreferences: () -> Unit, onClearMedia: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(Modifier.padding(horizontal = 16.dp)) {
        SettingsRow(Icons.Default.Star, R.string.conversation_settings_starred, model.starredMessages.size.takeIf { it > 0 }?.toString() ?: stringResource(R.string.conversation_settings_starred_none), colors, onStarred)
        SettingsRow(Icons.Default.Timer, R.string.conversation_settings_vanish, if (model.vanishModeActive) stringResource(R.string.conversation_settings_yes) else stringResource(R.string.conversation_settings_no), colors, onVanish)
        SettingsRow(Icons.Default.MoreVert, R.string.conversation_settings_preferences, null, colors, onPreferences)
        SettingsRow(Icons.Default.Folder, R.string.conversation_settings_storage, Formatter.formatFileSize(context, model.conversationMediaBytes), colors, {})
        if (model.conversationMediaBytes > 0) SettingsRow(Icons.Default.Delete, R.string.conversation_settings_clear_media, null, colors, onClearMedia, destructive = true)
        Text(stringResource(R.string.conversation_settings_preferences_desc), color = colors.tertiary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 12.dp))
    }
}

@Composable
private fun SettingsRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: Int, detail: String?, colors: AdaptiveColors, action: () -> Unit, destructive: Boolean = false) {
    Row(Modifier.fillMaxWidth().clickable(onClick = action).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (destructive) MaterialTheme.colorScheme.error else colors.secondary)
        Text(stringResource(title), color = if (destructive) MaterialTheme.colorScheme.error else colors.primary, modifier = Modifier.padding(start = 14.dp).weight(1f))
        detail?.let { Text(it, color = colors.tertiary) }
        if (!destructive) Icon(Icons.Default.ChevronRight, null, tint = colors.tertiary, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun ColumnScope.SharedContentTabs(tab: SharedContentTab, onTab: (SharedContentTab) -> Unit, model: ConversationSettingsViewModel, colors: AdaptiveColors, onOpenMedia: (SharedMedia) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TabButton(R.string.conversation_settings_media, tab == SharedContentTab.MEDIA) { onTab(SharedContentTab.MEDIA) }
        TabButton(R.string.conversation_settings_links, tab == SharedContentTab.LINKS) { onTab(SharedContentTab.LINKS) }
    }
    if (tab == SharedContentTab.MEDIA) {
        if (model.sharedMedia.isEmpty()) EmptyContent(Icons.Default.Folder, R.string.conversation_settings_media_empty, colors)
        else LazyVerticalGrid(GridCells.Fixed(3), Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            gridItems(model.sharedMedia, key = { it.id }) { media -> AsyncImage(media.thumbnailUrl, null, Modifier.fillMaxWidth().height(118.dp).clip(RoundedCornerShape(2.dp)).clickable { onOpenMedia(media) }, contentScale = ContentScale.Crop) }
        }
    } else {
        val links = model.sharedLinks()
        if (links.isEmpty()) EmptyContent(Icons.Default.Link, R.string.conversation_settings_links_empty, colors)
        else {
            val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
            LazyRow(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                lazyItems(links, key = { it.id }) { message ->
                    val url = Regex("https?://\\S+", RegexOption.IGNORE_CASE).find(message.content.orEmpty())?.value
                    Text(message.content.orEmpty(), color = colors.primary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(220.dp).clip(RoundedCornerShape(12.dp)).background(colors.secondary.copy(.10f)).clickable(enabled = url != null) { url?.let(uriHandler::openUri) }.padding(12.dp))
                }
            }
        }
    }
}

@Composable private fun TabButton(title: Int, selected: Boolean, onClick: () -> Unit) = Text(stringResource(title), modifier = Modifier.clip(CircleShape).background(if (selected) MaterialTheme.colorScheme.primary.copy(.16f) else MaterialTheme.colorScheme.surface).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp))

@Composable private fun EmptyContent(icon: androidx.compose.ui.graphics.vector.ImageVector, text: Int, colors: AdaptiveColors) = Column(Modifier.fillMaxWidth().padding(36.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null, tint = colors.tertiary, modifier = Modifier.size(30.dp)); Text(stringResource(text), color = colors.tertiary, modifier = Modifier.padding(top = 10.dp)) }

@Composable private fun ConversationStarredMessagesSheet(messages: List<EnhancedMessage>, onDismiss: () -> Unit, onSelect: (String) -> Unit) { AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.conversation_settings_starred)) }, text = { Column { messages.forEach { message -> Text(message.content ?: message.type.raw, modifier = Modifier.fillMaxWidth().clickable { onSelect(message.id) }.padding(vertical = 10.dp)) } } }, confirmButton = { Text(stringResource(R.string.common_cancel), modifier = Modifier.clickable(onClick = onDismiss).padding(16.dp)) }) }

@Composable
private fun ConversationSettingsMediaViewer(media: SharedMedia, onDismiss: () -> Unit, onSendReply: (SharedMedia, String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var reply by remember(media.id) { mutableStateOf("") }
    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
        when (media.type) {
            SharedMedia.Type.IMAGE -> AsyncImage(media.originalUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            SharedMedia.Type.VIDEO -> FeedVideoPage(media.originalUrl, media.thumbnailUrl, "conversation-media-${media.id}", Modifier.fillMaxSize(), showMute = true)
        }
        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.align(Alignment.TopStart).padding(20.dp).size(32.dp).clickable(onClick = onDismiss))
        if (media.allowsSaving) Icon(Icons.Default.Download, stringResource(R.string.conversation_settings_save_media), tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(20.dp).size(30.dp).clickable { saveConversationMedia(context, media) })
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(28.dp)).background(androidx.compose.ui.graphics.Color.Black.copy(.42f)), verticalAlignment = Alignment.CenterVertically) {
            TextField(reply, { reply = it }, placeholder = { Text(stringResource(R.string.conversation_settings_reply)) }, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.conversation_settings_send), color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.clickable(enabled = reply.isNotBlank()) { onSendReply(media, reply); reply = "" }.padding(14.dp))
        }
    }
}

private fun saveConversationMedia(context: android.content.Context, media: SharedMedia) {
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
        val mime = if (media.type == SharedMedia.Type.VIDEO) "video/mp4" else "image/jpeg"
        val collection = if (media.type == SharedMedia.Type.VIDEO) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "moments_${media.id}")
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Moments")
            }
            val destination = context.contentResolver.insert(collection, values) ?: return@runCatching
            val sourceUri = Uri.parse(media.originalUrl)
            val source = if (sourceUri.scheme == "content" || sourceUri.scheme == "file") context.contentResolver.openInputStream(sourceUri) else java.net.URL(media.originalUrl).openStream()
            source.use { input -> context.contentResolver.openOutputStream(destination)?.use { output -> input?.copyTo(output) } }
        }
    }
}

@Composable
private fun ConversationChatPreferencesView(model: ConversationSettingsViewModel, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.conversation_settings_preferences)) },
        text = {
            Column {
                PreferenceSwitch(R.string.conversation_settings_buzz, model.buzzEnabled) { model.buzzEnabled = it; model.persistPreferences(context) }
                PreferenceSwitch(R.string.conversation_settings_preview, model.messagePreviewEnabled) { model.messagePreviewEnabled = it; model.persistPreferences(context) }
                PreferenceSwitch(R.string.conversation_settings_read_receipts, model.readReceiptsEnabled) { model.readReceiptsEnabled = it; model.persistPreferences(context) }
                PreferenceSwitch(R.string.conversation_settings_typing, model.typingIndicatorEnabled) { model.typingIndicatorEnabled = it; model.persistPreferences(context) }
                PreferenceSwitch(R.string.conversation_settings_forwarding, model.forwardingEnabled) { model.forwardingEnabled = it; model.persistPreferences(context) }
                Text(stringResource(R.string.conversation_settings_clear_conversation), color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth().clickable { model.clearConversation(onDismiss) }.padding(vertical = 14.dp))
            }
        },
        confirmButton = { Text(stringResource(R.string.common_cancel), modifier = Modifier.clickable(onClick = onDismiss).padding(16.dp)) },
    )
}

@Composable
private fun PreferenceSwitch(title: Int, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(title), modifier = Modifier.weight(1f))
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun ConversationVanishModeView(model: ConversationSettingsViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.conversation_settings_vanish)) },
        text = {
            Column {
                VanishOption(R.string.conversation_settings_no, !model.vanishModeActive) { model.updateVanish(false, model.vanishTimer); onDismiss() }
                VanishOption(R.string.conversation_settings_once_seen, model.vanishModeActive && model.vanishTimer == VanishMessageTimer.ONCE_SEEN) { model.updateVanish(true, VanishMessageTimer.ONCE_SEEN); onDismiss() }
                VanishOption(R.string.conversation_settings_24_hours, model.vanishModeActive && model.vanishTimer == VanishMessageTimer.HOURS_24) { model.updateVanish(true, VanishMessageTimer.HOURS_24); onDismiss() }
                VanishOption(R.string.conversation_settings_7_days, model.vanishModeActive && model.vanishTimer == VanishMessageTimer.DAYS_7) { model.updateVanish(true, VanishMessageTimer.DAYS_7); onDismiss() }
            }
        },
        confirmButton = { Text(stringResource(R.string.common_cancel), modifier = Modifier.clickable(onClick = onDismiss).padding(16.dp)) },
    )
}

@Composable private fun VanishOption(title: Int, selected: Boolean, onClick: () -> Unit) = Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) { Text(stringResource(title), modifier = Modifier.weight(1f)); androidx.compose.material3.RadioButton(selected, onClick) }

@Composable
fun ConversationSettingsNavigationRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String,
    adaptiveColors: AdaptiveColors,
    action: () -> Unit,
    modifier: Modifier = Modifier,
) = Row(modifier.fillMaxWidth().clickable(onClick = action).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
    Icon(icon, null, tint = adaptiveColors.secondary, modifier = Modifier.size(22.dp))
    Text(title, color = adaptiveColors.primary, modifier = Modifier.padding(start = 12.dp).weight(1f))
    Text(detail, color = adaptiveColors.tertiary)
    Icon(Icons.Default.ChevronRight, null, tint = adaptiveColors.tertiary, modifier = Modifier.padding(start = 8.dp))
}

@Composable
fun ChatInfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String, adaptiveColors: AdaptiveColors, modifier: Modifier = Modifier) = Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Icon(icon, null, tint = adaptiveColors.secondary, modifier = Modifier.size(18.dp))
    Text(title, color = adaptiveColors.secondary, modifier = Modifier.padding(start = 10.dp).weight(1f))
    Text(value, color = adaptiveColors.primary, fontWeight = FontWeight.SemiBold)
}

@Composable
fun SharedMediaThumbnail(media: SharedMedia, fillsGrid: Boolean = false, onTap: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(if (fillsGrid) 0.dp else 16.dp)
    Box(modifier.clip(shape).clickable(onClick = onTap)) {
        AsyncImage(media.thumbnailUrl, null, Modifier.fillMaxWidth().height(if (fillsGrid) 118.dp else 100.dp), contentScale = ContentScale.Crop)
        if (media.type == SharedMedia.Type.VIDEO) Icon(Icons.Default.PlayArrow, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).size(18.dp))
    }
}
