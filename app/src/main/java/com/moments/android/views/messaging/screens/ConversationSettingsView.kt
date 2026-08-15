package com.moments.android.views.messaging.screens

import android.text.format.Formatter
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.moments.android.views.profile.userprofile.UserProfileView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.moments.android.R
import com.moments.android.MomentsApplication
import com.moments.android.coordinators.AsyncProfileImageView
import com.moments.android.models.OnlineStatus
import com.moments.android.services.messaging.OnlineStatusService
import com.moments.android.utilities.MomentsFormat
import com.moments.android.views.messaging.core.Conversation
import com.moments.android.views.messaging.core.EnhancedMessage
import com.moments.android.views.messaging.core.MessageType
import com.moments.android.views.messaging.core.PresenceDisplay
import kotlin.math.roundToInt
import com.moments.android.services.messaging.ChatCacheStore
import com.moments.android.services.messaging.ChatMediaDownloadPolicy
import com.moments.android.services.persistence.LocalPersistenceService
import com.moments.android.services.messaging.VanishMessageTimer
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.cache.UserCacheService
import com.moments.android.services.messaging.MessageCatchUpService
import com.moments.android.views.feed.AdaptiveColors
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.messaging.services.ChatDraftEvent
import com.moments.android.views.messaging.services.ChatDraftEvents
import com.moments.android.views.messaging.services.ChatService
import com.moments.android.views.messaging.services.ChatSessionEngine
import com.moments.android.views.messaging.services.ChatVideoPosterGenerator
import com.moments.android.views.messaging.services.ConversationBuzzPreferenceEvents
import com.moments.android.views.messaging.services.ConversationForwardingPreferenceEvents
import com.moments.android.views.messaging.services.ConversationMuteEvents
import com.moments.android.views.messaging.services.ChatEncryptedMediaResolver
import com.moments.android.views.messaging.services.resolveVideoThumbnail
import com.moments.android.views.messaging.services.setVanishMode
import com.moments.android.views.messaging.services.sendChatNotice
import com.moments.android.views.messaging.services.updateChatNotice
import com.moments.android.views.messaging.components.ClusterGalleryPresentation
import com.moments.android.views.messaging.components.ClusterGalleryScope
import com.moments.android.views.messaging.components.ClusterGalleryTab
import com.moments.android.views.messaging.components.ClusterGalleryView
import com.moments.android.views.messaging.components.LinkPreviewCard
import com.moments.android.utilities.HapticManager
import com.moments.android.views.shared.ChatPreviewPrivacy
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

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
    /** ≡ iOS `conversationCreatedDate`. */
    var conversationCreatedDate by mutableStateOf("")
        private set
    /** ≡ iOS `showSharedGallery` / `sharedGalleryInitialTab`. */
    var showSharedGallery by mutableStateOf(false)
    var sharedGalleryInitialTab by mutableStateOf(ClusterGalleryTab.MEDIA)
        private set
    var downloadProgress by mutableStateOf<Map<String, Double>>(emptyMap())
        private set
    private val downloadingMediaIds = mutableSetOf<String>()
    private val hydratingMediaIds = mutableSetOf<String>()
    private val refreshingMetadataIds = mutableSetOf<String>()
    private val firestoreService = FirestoreService()
    private var privacyMutationVersion = 0L

    fun openSharedGallery(tab: ClusterGalleryTab = ClusterGalleryTab.MEDIA) {
        sharedGalleryInitialTab = tab
        showSharedGallery = true
    }

    fun loadConversationData(value: Conversation, context: android.content.Context? = null) {
        conversation = value
        vanishModeActive = value.vanishModeActive == true
        vanishTimer = VanishMessageTimer.fromStored(value.vanishMessageTimer)
        notificationsEnabled = !value.isMuted(currentUserId)
        conversationMediaBytes = value.id?.let(ChatCacheStore::bytes) ?: 0L
        conversationCreatedDate = MomentsFormat.smartDate(value.timestamp, MomentsFormat.DateContext.MEDIUM_DATE)
        value.id?.let { conversationId ->
            val prefsCtx = context ?: MomentsApplication.instance
            if (prefsCtx != null) {
                messagePreviewEnabled = ChatPreviewPrivacy.isUserPreviewEnabled(prefsCtx, conversationId)
                val local = prefsCtx.getSharedPreferences("conversation_settings", android.content.Context.MODE_PRIVATE)
                fun boolPref(iosKey: String, androidLegacy: String, default: Boolean): Boolean = when {
                    local.contains(iosKey) -> local.getBoolean(iosKey, default)
                    local.contains(androidLegacy) -> local.getBoolean(androidLegacy, default)
                    else -> default
                }
                readReceiptsEnabled = boolPref("chat_read_receipts_enabled_$conversationId", "read_receipts_$conversationId", true)
                forwardingEnabled = boolPref("chat_forwarding_enabled_$conversationId", "forwarding_$conversationId", true)
                typingIndicatorEnabled = boolPref("chat_typing_indicator_enabled_$conversationId", "typing_$conversationId", true)
                buzzEnabled = boolPref("chat_buzz_enabled_$conversationId", "buzz_$conversationId", true)
            }
            processMessages(LocalPersistenceService.loadMessagesFast(conversationId))
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                MessageCatchUpService.sync(conversationId)
                val refreshed = LocalPersistenceService.loadMessagesFast(conversationId)
                withContext(Dispatchers.Main) { processMessages(refreshed) }
            }
            loadPrivacySettings(prefsCtx)
        }
        value.otherParticipantId.takeIf { it.isNotBlank() }?.let { userId ->
            UserCacheService.refreshUser(userId) { user ->
                liveOtherParticipantUsername = user?.username?.trim().orEmpty()
            }
        }
    }

    /** ≡ iOS `loadPrivacySettings` — mute + prefs desde Firestore, con fallback local ya cargado. */
    private fun loadPrivacySettings(context: android.content.Context?) {
        val conversationId = conversation?.id ?: return
        if (currentUserId.isBlank()) return
        val requestVersion = privacyMutationVersion
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            val globalEnabled = runCatching {
                firestoreService.fetchUsersAsync(listOf(currentUserId)).firstOrNull()?.showReadReceipts
            }.getOrNull() ?: true
            val convData = runCatching {
                FirebaseFirestore.getInstance().collection("conversations").document(conversationId).get().await().data
            }.getOrNull()
            withContext(Dispatchers.Main) {
                if (requestVersion != privacyMutationVersion || conversation?.id != conversationId) {
                    return@withContext
                }
                if (convData != null) {
                    @Suppress("UNCHECKED_CAST")
                    val mutedByUserIds = convData["mutedByUserIds"] as? List<String> ?: emptyList()
                    val legacyIsMuted = convData["isMuted"] as? Boolean ?: false
                    val legacyMutedBy = convData["mutedBy"] as? String
                    val isMutedForCurrentUser =
                        currentUserId in mutedByUserIds || (legacyIsMuted && legacyMutedBy == currentUserId)
                    notificationsEnabled = !isMutedForCurrentUser

                    @Suppress("UNCHECKED_CAST")
                    val receiptPrefs = convData["readReceiptPreferences"] as? Map<String, Boolean>
                    readReceiptsEnabled = receiptPrefs?.get(currentUserId) ?: globalEnabled
                    context?.getSharedPreferences("conversation_settings", android.content.Context.MODE_PRIVATE)
                        ?.edit()?.putBoolean("chat_read_receipts_enabled_$conversationId", readReceiptsEnabled)?.apply()

                    @Suppress("UNCHECKED_CAST")
                    val forwardingPrefs = convData["forwardingPreferences"] as? Map<String, Boolean>
                    forwardingPrefs?.get(currentUserId)?.let { preferred ->
                        forwardingEnabled = preferred
                        context?.getSharedPreferences("conversation_settings", android.content.Context.MODE_PRIVATE)
                            ?.edit()?.putBoolean("chat_forwarding_enabled_$conversationId", preferred)?.apply()
                    }

                    @Suppress("UNCHECKED_CAST")
                    val buzzPrefs = convData["buzzPreferences"] as? Map<String, Boolean>
                    buzzPrefs?.get(currentUserId)?.let { preferred ->
                        buzzEnabled = preferred
                        context?.getSharedPreferences("conversation_settings", android.content.Context.MODE_PRIVATE)
                            ?.edit()?.putBoolean("chat_buzz_enabled_$conversationId", preferred)?.apply()
                    }
                } else {
                    notificationsEnabled = true
                    readReceiptsEnabled = globalEnabled
                }
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
            ChatService.deleteMessageWithCleanup(id, message.id)
        }
    }

    fun toggleNotifications() {
        val id = conversation?.id ?: return
        notificationsEnabled = !notificationsEnabled
        val isMuted = !notificationsEnabled
        FirebaseFirestore.getInstance().collection("conversations").document(id).update(
            "mutedByUserIds",
            if (notificationsEnabled) FieldValue.arrayRemove(currentUserId) else FieldValue.arrayUnion(currentUserId),
        ).addOnSuccessListener {
            ConversationMuteEvents.emit(id, isMuted)
        }
    }

    /** ≡ iOS `updateVanishSettings` + post `conversationVanishModeDidChange`. */
    fun updateVanish(active: Boolean, timer: VanishMessageTimer) {
        val id = conversation?.id ?: return
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val result = ChatService.setVanishMode(id, active, currentUserId, timer.takeIf { active })
            if (result.isFailure) return@launch

            withContext(Dispatchers.Main.immediate) {
                vanishModeActive = active
                vanishTimer = timer
                conversation?.vanishModeActive = active
                conversation?.vanishMessageTimer = if (active) timer.raw else null
                // Empuja al chat abierto (sesión cacheada) sin esperar al snapshot.
                ChatSessionEngine.cachedSession(id)?.applyVanishSettingsFromSettings(active, timer)
                ChatDraftEvents.emit(ChatDraftEvent.VanishModeChanged(id, active))
            }

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
        ChatPreviewPrivacy.setUserPreviewEnabled(context, id, messagePreviewEnabled)
        context.getSharedPreferences("conversation_settings", android.content.Context.MODE_PRIVATE).edit()
            .putBoolean("chat_read_receipts_enabled_$id", readReceiptsEnabled)
            .putBoolean("chat_forwarding_enabled_$id", forwardingEnabled)
            .putBoolean("chat_typing_indicator_enabled_$id", typingIndicatorEnabled)
            .putBoolean("chat_buzz_enabled_$id", buzzEnabled)
            .putBoolean("read_receipts_$id", readReceiptsEnabled)
            .putBoolean("forwarding_$id", forwardingEnabled)
            .putBoolean("typing_$id", typingIndicatorEnabled)
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

    /** ≡ iOS `toggleReadReceipts`. */
    fun toggleReadReceipts(context: android.content.Context, previousValue: Boolean) {
        val id = conversation?.id ?: return
        if (currentUserId.isBlank()) return
        privacyMutationVersion += 1L
        val preferences = context.getSharedPreferences("conversation_settings", android.content.Context.MODE_PRIVATE)
        preferences.edit()
            .putBoolean("chat_read_receipts_enabled_$id", readReceiptsEnabled)
            .putBoolean("read_receipts_$id", readReceiptsEnabled)
            .apply()
        val requestedValue = readReceiptsEnabled
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            val error = runCatching {
                FirebaseFirestore.getInstance().collection("conversations").document(id)
                    .update("readReceiptPreferences.$currentUserId", requestedValue)
                    .await()
            }.exceptionOrNull()
            if (error != null) {
                preferences.edit()
                    .putBoolean("chat_read_receipts_enabled_$id", previousValue)
                    .putBoolean("read_receipts_$id", previousValue)
                    .apply()
                withContext(Dispatchers.Main) {
                    if (conversation?.id == id && readReceiptsEnabled == requestedValue) {
                        readReceiptsEnabled = previousValue
                    }
                }
            }
        }
    }

    /** ≡ iOS `toggleForwarding`. */
    fun toggleForwarding(context: android.content.Context) {
        val id = conversation?.id ?: return
        if (currentUserId.isBlank()) return
        context.getSharedPreferences("conversation_settings", android.content.Context.MODE_PRIVATE).edit()
            .putBoolean("chat_forwarding_enabled_$id", forwardingEnabled)
            .putBoolean("forwarding_$id", forwardingEnabled)
            .apply()
        FirebaseFirestore.getInstance().collection("conversations").document(id)
            .update("forwardingPreferences.$currentUserId", forwardingEnabled)
        ConversationForwardingPreferenceEvents.emit(id, currentUserId, forwardingEnabled)
    }

    /** ≡ iOS `toggleTypingIndicator`. */
    fun toggleTypingIndicator(context: android.content.Context) {
        val id = conversation?.id ?: return
        context.getSharedPreferences("conversation_settings", android.content.Context.MODE_PRIVATE).edit()
            .putBoolean("chat_typing_indicator_enabled_$id", typingIndicatorEnabled)
            .putBoolean("typing_$id", typingIndicatorEnabled)
            .apply()
    }

    /** ≡ iOS `toggleMessagePreview`. */
    fun toggleMessagePreview(context: android.content.Context) {
        val id = conversation?.id ?: return
        ChatPreviewPrivacy.setUserPreviewEnabled(context, id, messagePreviewEnabled)
    }

    /** ≡ iOS `toggleBuzzNotifications`. */
    fun toggleBuzzNotifications(context: android.content.Context) {
        val id = conversation?.id ?: return
        if (currentUserId.isBlank()) return
        context.getSharedPreferences("conversation_settings", android.content.Context.MODE_PRIVATE).edit()
            .putBoolean("chat_buzz_enabled_$id", buzzEnabled)
            .putBoolean("buzz_$id", buzzEnabled)
            .apply()
        FirebaseFirestore.getInstance().collection("conversations").document(id)
            .update("buzzPreferences.$currentUserId", buzzEnabled)
        ConversationBuzzPreferenceEvents.emit(id, currentUserId, buzzEnabled)
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
        if (source == null) {
            onResolved(media)
            return
        }
        openMessageMediaForViewing(source, onResolved)
    }

    /** ≡ iOS `openMediaForViewing(_ message:)`. */
    fun openMessageMediaForViewing(message: EnhancedMessage, onResolved: (SharedMedia) -> Unit) {
        if (!message.needsDownloadForPlayback) {
            makeSharedMedia(message)?.let(onResolved)
            return
        }
        if (message.id in downloadingMediaIds) return
        downloadingMediaIds += message.id
        setDownloadProgress(message.id, 0.03)
        prepareMediaForViewing(message, forceDownload = true) { updated ->
            downloadingMediaIds -= message.id
            clearDownloadProgress(message.id)
            makeSharedMedia(updated)?.let(onResolved)
        }
    }

    /** ≡ iOS `hydrateMediaIfNeeded(for:)`. */
    fun hydrateMediaIfNeeded(message: EnhancedMessage) {
        if (message.isMediaAwaitingManualDownload) {
            hydrateThumbnailPreviewIfNeeded(message)
            return
        }
        if (!ChatMediaDownloadPolicy.shouldDownloadAutomatically()) return
        if (message.type == MessageType.VIDEO) {
            hydrateVideoThumbnailIfNeeded(message)
            return
        }
        if (!message.isMediaPendingResolution) {
            if (message.type == MessageType.IMAGE &&
                message.mediaUrl == null &&
                (message.mediaObjectPath == null || message.mediaEncryption == null)
            ) {
                refreshMediaMetadataIfNeeded(message)
            }
            return
        }
        if (message.id in hydratingMediaIds) return
        hydratingMediaIds += message.id
        setDownloadProgress(message.id, 0.03)
        prepareMediaForViewing(message, forceDownload = false) {
            hydratingMediaIds -= message.id
            clearDownloadProgress(message.id)
        }
    }

    fun isDownloadingMedia(messageId: String): Boolean =
        messageId in downloadingMediaIds || messageId in hydratingMediaIds

    fun sharedMediaFrom(message: EnhancedMessage): SharedMedia? = makeSharedMedia(message)

    fun sharedMediaItemsForOverlay(selecting: EnhancedMessage): List<SharedMedia> {
        val items = sharedGalleryMessages.mapNotNull(::makeSharedMedia)
        val selected = makeSharedMedia(selecting) ?: return items
        return if (items.any { it.id == selected.id }) items else items + selected
    }

    private fun updateGalleryMessage(updated: EnhancedMessage) {
        val index = sharedGalleryMessages.indexOfFirst { it.id == updated.id }
        if (index < 0) return
        sharedGalleryMessages = sharedGalleryMessages.toMutableList().also { it[index] = updated }
        makeSharedMedia(updated)?.let { media ->
            sharedMedia = sharedMedia.map { if (it.id == media.id) media else it }.let { list ->
                if (list.any { it.id == media.id }) list else list + media
            }
        }
    }

    private fun setDownloadProgress(messageId: String, progress: Double) {
        downloadProgress = downloadProgress + (messageId to progress)
    }

    private fun clearDownloadProgress(messageId: String) {
        downloadProgress = downloadProgress - messageId
    }

    /** ≡ iOS `refreshMediaMetadataIfNeeded`. */
    private fun refreshMediaMetadataIfNeeded(message: EnhancedMessage) {
        if (message.type != MessageType.IMAGE && message.type != MessageType.VIDEO) return
        val conversationId = conversation?.id?.takeIf { it.isNotBlank() } ?: return
        val missingMain = message.mediaObjectPath == null || message.mediaEncryption == null
        val needsThumb = message.type == MessageType.VIDEO && message.needsVideoThumbnailForDisplay
        val missingThumbMeta = message.thumbnailObjectPath == null || message.thumbnailEncryption == null
        when {
            message.type == MessageType.IMAGE && !missingMain -> return
            !missingMain && !(needsThumb && missingThumbMeta) -> {
                if (needsThumb) hydrateVideoThumbnailIfNeeded(message)
                return
            }
        }
        if (!refreshingMetadataIds.add(message.id)) return
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            val fresh = ChatService.fetchMessage(conversationId, message.id).getOrNull()
            withContext(Dispatchers.Main) {
                refreshingMetadataIds -= message.id
                if (fresh == null) return@withContext
                updateGalleryMessage(fresh)
                if (fresh.type == MessageType.VIDEO) hydrateVideoThumbnailIfNeeded(fresh)
                else hydrateMediaIfNeeded(fresh)
            }
        }
    }

    /** ≡ iOS `hydrateVideoThumbnailIfNeeded`. */
    private fun hydrateVideoThumbnailIfNeeded(message: EnhancedMessage) {
        if (message.type != MessageType.VIDEO || !message.needsVideoThumbnailForDisplay) return
        if (!ChatMediaDownloadPolicy.shouldDownloadAutomatically()) return

        if (message.thumbnailObjectPath != null && message.thumbnailEncryption != null) {
            val thumbnailKey = "thumb_${message.id}"
            if (thumbnailKey in hydratingMediaIds) return
            hydratingMediaIds += thumbnailKey
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                val resolvedThumb = ChatService.resolveVideoThumbnail(message, forceDownload = false)
                withContext(Dispatchers.Main) {
                    hydratingMediaIds -= thumbnailKey
                    if (resolvedThumb.isNullOrBlank()) return@withContext
                    val updated = (sharedGalleryMessages.firstOrNull { it.id == message.id } ?: message)
                        .copy(thumbnailUrl = resolvedThumb)
                    updateGalleryMessage(updated)
                }
            }
            return
        }

        if (!message.mediaUrl.isNullOrBlank()) {
            generateVideoPosterIfPossible(message)
            return
        }

        if (message.mediaObjectPath != null && message.mediaEncryption != null) {
            if (message.id in hydratingMediaIds) return
            hydratingMediaIds += message.id
            setDownloadProgress(message.id, 0.03)
            prepareMediaForViewing(message, forceDownload = false) { updated ->
                hydratingMediaIds -= message.id
                clearDownloadProgress(message.id)
                generateVideoPosterIfPossible(updated)
            }
            return
        }

        refreshMediaMetadataIfNeeded(message)
    }

    /** ≡ iOS `hydrateThumbnailPreviewIfNeeded`. */
    private fun hydrateThumbnailPreviewIfNeeded(message: EnhancedMessage) {
        if (message.thumbnailObjectPath == null || message.thumbnailEncryption == null) return
        if (!message.thumbnailUrl.isNullOrBlank() && !message.hasMissingLocalThumbnail) return
        val previewKey = "thumb_preview_${message.id}"
        if (previewKey in hydratingMediaIds) return
        hydratingMediaIds += previewKey
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            val thumbnail = ChatService.resolveVideoThumbnail(message, forceDownload = false)
            withContext(Dispatchers.Main) {
                hydratingMediaIds -= previewKey
                if (thumbnail == null) return@withContext
                val updated = (sharedGalleryMessages.firstOrNull { it.id == message.id } ?: message)
                    .copy(thumbnailUrl = thumbnail)
                updateGalleryMessage(updated)
            }
        }
    }

    /** ≡ iOS `generateVideoPosterIfPossible`. */
    private fun generateVideoPosterIfPossible(message: EnhancedMessage) {
        if (!message.needsVideoThumbnailForDisplay) return
        val mediaUrl = message.mediaUrl ?: return
        val posterKey = "poster_${message.id}"
        if (posterKey in hydratingMediaIds) return
        hydratingMediaIds += posterKey
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            val poster = ChatVideoPosterGenerator.poster(mediaUrl, message.id)
            withContext(Dispatchers.Main) {
                hydratingMediaIds -= posterKey
                if (poster.isNullOrBlank()) return@withContext
                val updated = (sharedGalleryMessages.firstOrNull { it.id == message.id } ?: message)
                    .copy(thumbnailUrl = poster)
                updateGalleryMessage(updated)
            }
        }
    }

    /** ≡ iOS `prepareMediaForViewing`. */
    private fun prepareMediaForViewing(
        message: EnhancedMessage,
        forceDownload: Boolean,
        completion: (EnhancedMessage) -> Unit,
    ) {
        if (message.hasLocalMediaReadyForViewer && !message.hasMissingLocalMedia) {
            completion(message)
            return
        }
        if (message.mediaObjectPath == null || message.mediaEncryption == null) {
            completion(message)
            return
        }
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                val resolved = ChatEncryptedMediaResolver.resolveForMessage(message, forceDownload = forceDownload)
                withContext(Dispatchers.Main) {
                    if (resolved?.mediaUrl == null) {
                        completion(message)
                        return@withContext
                    }
                    val updated = (sharedGalleryMessages.firstOrNull { it.id == message.id } ?: message).copy(
                        mediaUrl = resolved.mediaUrl,
                        thumbnailUrl = resolved.thumbnailUrl ?: message.thumbnailUrl,
                    )
                    updateGalleryMessage(updated)
                    conversation?.id?.let { conversationId ->
                        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                            LocalPersistenceService.saveMessagesInBackground(listOf(updated), conversationId, sync = false)
                        }
                    }
                    completion(updated)
                }
            } finally {
                withContext(Dispatchers.Main) { clearDownloadProgress(message.id) }
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
    val context = LocalContext.current
    val model = remember(conversation.id) { ConversationSettingsViewModel() }
    var tab by remember { mutableStateOf(SharedContentTab.MEDIA) }
    var clearMediaConfirm by remember { mutableStateOf(false) }
    var showStarred by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showPreferences by remember { mutableStateOf(false) }
    var showVanish by remember { mutableStateOf(false) }
    var showBlockConfirm by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }
    var selectedMedia by remember { mutableStateOf<SharedMedia?>(null) }
    var pendingJumpMessageId by remember { mutableStateOf<String?>(null) }
    var clearConversationConfirm by remember { mutableStateOf(false) }
    // ≡ iOS showingUserProfile → navigationDestination UserProfileView
    var showingUserProfile by remember { mutableStateOf(false) }
    LaunchedEffect(conversation.id) { model.loadConversationData(conversation, context) }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(colors.chatBackground.first()).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = colors.primary) }
                Text(stringResource(R.string.conversation_settings_title), modifier = Modifier.weight(1f), color = colors.primary, fontWeight = FontWeight.SemiBold)
                // ≡ iOS ToolbarItem trailing Menu { Block, Report } + ellipsis
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreHoriz, contentDescription = null, tint = colors.primary)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.conversation_settings_block_user),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = {
                                showMenu = false
                                showBlockConfirm = true
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Block,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.report_action_user),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = {
                                showMenu = false
                                showReport = true
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Flag,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                        )
                    }
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                ConversationSettingsHeader(
                    conversation = conversation,
                    liveUsername = model.liveOtherParticipantUsername,
                    colors = colors,
                    notificationsEnabled = model.notificationsEnabled,
                    onProfile = {
                        val userId = conversation.otherParticipantId.trim()
                        if (userId.isNotEmpty()) {
                            showingUserProfile = true
                        }
                    },
                    onSearch = onSearchRequested,
                    onToggleMute = { model.toggleNotifications() },
                )
                SettingsRows(
                    model = model,
                    colors = colors,
                    onStarred = { showStarred = true },
                    onVanish = { showVanish = true },
                    onPreferences = { showPreferences = true },
                    onOpenGallery = {
                        HapticManager.shared.lightImpact()
                        model.openSharedGallery(ClusterGalleryTab.MEDIA)
                    },
                    onClearMedia = { clearMediaConfirm = true },
                )
                // ≡ iOS: settingsFooter debajo de vaciar media, antes de Media/Links
                SettingsFooter(model, colors)
                SharedContentTabs(tab, { tab = it }, model, colors, onOpenMedia = { model.openMediaForViewing(it) { resolved -> selectedMedia = resolved } })
            }
        }

        // Preferences / Vanish = push full-screen (≡ navigationDestination iOS).
        // Chat edge-to-edge: mismo padding que ChatCamera (status+nav), no solo statusBarsPadding
        // (puede quedar a 0 si un ancestro ya consumió insets).
        if (showPreferences) {
            ConversationChatPreferencesView(
                model = model,
                onDismiss = { showPreferences = false },
                onRequestClearConversation = { clearConversationConfirm = true },
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.navigationBars)),
            )
        }
        if (showVanish) {
            ConversationVanishModeView(
                model = model,
                onDismiss = { showVanish = false },
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.navigationBars)),
            )
        }
        // ≡ navigationDestination(showSharedGallery) → ClusterGalleryView
        if (model.showSharedGallery) {
            ClusterGalleryView(
                messages = model.sharedGalleryMessages.filterNot { it.isDeleted },
                currentUserId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty(),
                scope = ClusterGalleryScope.CONVERSATION_SHARED,
                presentation = ClusterGalleryPresentation.PUSHED,
                initialTab = model.sharedGalleryInitialTab,
                onClose = { model.showSharedGallery = false },
                onOpenMedia = { message ->
                    model.openMessageMediaForViewing(message) { /* download only; user taps again */ }
                },
                onHydrateMedia = model::hydrateMediaIfNeeded,
                isDownloadingMedia = model::isDownloadingMedia,
                downloadProgress = { model.downloadProgress[it] },
                onDeleteForMe = { messages -> messages.forEach(model::deleteForMe) },
                onDeleteForEveryone = { messages -> messages.forEach(model::deleteForEveryone) },
                detail = { selectedMessage, dismissDetail ->
                    // ≡ detail: FullScreenMediaView in ConversationSettingsView
                    val media = model.sharedMediaFrom(selectedMessage)
                    if (media != null) {
                        ConversationFullScreenMediaView(
                            media = media,
                            mediaItems = model.sharedMediaItemsForOverlay(selectedMessage).ifEmpty { listOf(media) },
                            currentUserId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty(),
                            otherParticipantName = model.liveOtherParticipantUsername.ifBlank {
                                conversation.otherParticipantUsername.orEmpty()
                            },
                            onClose = dismissDetail,
                            onSendReply = { item, text, completion ->
                                model.sendReplyToMedia(item, text)
                                completion(Result.success(Unit))
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        selectedMedia?.let { media ->
            ConversationFullScreenMediaView(
                media = media,
                mediaItems = media.sourceMessage?.let(model::sharedMediaItemsForOverlay).orEmpty().ifEmpty { listOf(media) },
                currentUserId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty(),
                otherParticipantName = model.liveOtherParticipantUsername.ifBlank {
                    conversation.otherParticipantUsername.orEmpty()
                },
                onClose = { selectedMedia = null },
                onSendReply = { item, text, completion ->
                    model.sendReplyToMedia(item, text)
                    completion(Result.success(Unit))
                },
            )
        }

        if (showingUserProfile) {
            val profileUserId = conversation.otherParticipantId.trim()
            if (profileUserId.isNotEmpty()) {
                Dialog(
                    onDismissRequest = { showingUserProfile = false },
                    properties = DialogProperties(
                        usePlatformDefaultWidth = false,
                        decorFitsSystemWindows = false,
                    ),
                ) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        UserProfileView(
                            userId = profileUserId,
                            onDismiss = { showingUserProfile = false },
                        )
                    }
                }
            }
        }
    }

    fun consumePendingStarredJump() {
        pendingJumpMessageId?.let { id ->
            pendingJumpMessageId = null
            onJumpToMessage(id)
            onBack()
        }
    }

    if (clearMediaConfirm) AlertDialog(
        onDismissRequest = { clearMediaConfirm = false },
        title = { Text(stringResource(R.string.conversation_settings_clear_media)) },
        text = { Text(stringResource(R.string.conversation_settings_media_reload)) },
        confirmButton = { Text(stringResource(R.string.conversation_settings_clear_media), modifier = Modifier.clickable { model.clearConversationMedia(); clearMediaConfirm = false }.padding(16.dp)) },
        dismissButton = { Text(stringResource(R.string.common_cancel), modifier = Modifier.clickable { clearMediaConfirm = false }.padding(16.dp)) },
    )
    if (showBlockConfirm) AlertDialog(
        onDismissRequest = { showBlockConfirm = false },
        title = { Text(stringResource(R.string.conversation_settings_block)) },
        confirmButton = {
            Text(
                stringResource(R.string.conversation_settings_block),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.clickable {
                    showBlockConfirm = false
                    model.blockOtherParticipant(onBack)
                }.padding(16.dp),
            )
        },
        dismissButton = { Text(stringResource(R.string.common_cancel), modifier = Modifier.clickable { showBlockConfirm = false }.padding(16.dp)) },
    )
    // ≡ confirmationDialog clearConversation (prefs)
    if (clearConversationConfirm) AlertDialog(
        onDismissRequest = { clearConversationConfirm = false },
        title = { Text(stringResource(R.string.conversation_settings_clear_conversation)) },
        confirmButton = {
            Text(
                stringResource(R.string.conversation_settings_clear_conversation),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.clickable {
                    clearConversationConfirm = false
                    HapticManager.shared.mediumImpact()
                    model.clearConversation {
                        showPreferences = false
                        onBack()
                    }
                }.padding(16.dp),
            )
        },
        dismissButton = {
            Text(
                stringResource(R.string.common_cancel),
                modifier = Modifier.clickable { clearConversationConfirm = false }.padding(16.dp),
            )
        },
    )
    // ≡ .sheet starred medium+large
    if (showStarred) {
        com.moments.android.views.shared.MomentsModalSheet(
            onDismissRequest = {
                showStarred = false
                consumePendingStarredJump()
            },
            largeOnly = false,
        ) {
            ConversationStarredMessagesSheet(
                messages = model.starredMessages,
                currentUserId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty(),
                otherParticipantName = model.liveOtherParticipantUsername.ifBlank {
                    conversation.otherParticipantUsername.orEmpty()
                },
                colors = colors,
                onDismiss = { showStarred = false },
                onSelect = { id ->
                    // iOS: solo encola el id; el jump+pop va en onDismiss del sheet
                    pendingJumpMessageId = id
                    showStarred = false
                },
            )
        }
    }
    if (showReport) {
        com.moments.android.reportes.ReportBottomSheet(
            target = com.moments.android.reportes.ReportTarget.UserTarget(
                userId = conversation.otherParticipantId,
                username = model.liveOtherParticipantUsername.ifBlank { conversation.otherParticipantUsername },
            ),
            onDismiss = { showReport = false },
        )
    }
}

@Composable
private fun ConversationSettingsHeader(
    conversation: Conversation,
    liveUsername: String,
    colors: AdaptiveColors,
    notificationsEnabled: Boolean,
    onProfile: (String) -> Unit,
    onSearch: () -> Unit,
    onToggleMute: () -> Unit,
) {
    var presence by remember { mutableStateOf<PresenceDisplay?>(null) }
    DisposableEffect(conversation.otherParticipantId) {
        val stop = OnlineStatusService.shared.observeUserStatus(conversation.otherParticipantId) { status, lastSeen ->
            presence = OnlineStatusService.shared.presenceDisplay(status, lastSeen)
        }
        onDispose { stop() }
    }
    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // ≡ iOS KFImage(path) — en Android resolver por userId (path Storage a menudo no es URL HTTP)
        AsyncProfileImageView(
            userId = conversation.otherParticipantId,
            modifier = Modifier.size(92.dp),
        )
        Text(
            liveUsername.ifBlank { conversation.otherParticipantUsername.orEmpty() },
            color = colors.primary,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            modifier = Modifier.padding(top = 12.dp),
        )
        presence?.let { p ->
            Row(
                Modifier.padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(presenceStatusColor(p.status)))
                Text(p.statusText, color = colors.secondary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                p.supplementalText?.let { Text("• $it", color = colors.tertiary, fontSize = 13.sp) }
            }
        }
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(34.dp)) {
            HeaderAction(Icons.Default.Person, R.string.conversation_settings_quick_action_profile) {
                HapticManager.shared.lightImpact()
                onProfile(conversation.otherParticipantId)
            }
            HeaderAction(Icons.Default.Search, R.string.conversation_settings_quick_action_search) {
                HapticManager.shared.lightImpact()
                onSearch()
            }
            HeaderAction(
                if (notificationsEnabled) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                if (notificationsEnabled) {
                    R.string.conversation_settings_quick_action_mute
                } else {
                    R.string.conversation_settings_quick_action_unmute
                },
            ) {
                HapticManager.shared.lightImpact()
                onToggleMute()
            }
        }
    }
}

private fun presenceStatusColor(status: OnlineStatus): androidx.compose.ui.graphics.Color =
    when (status) {
        OnlineStatus.ONLINE -> androidx.compose.ui.graphics.Color(0xFF34C759)
        OnlineStatus.AWAY -> androidx.compose.ui.graphics.Color(0xFFFFCC00)
        OnlineStatus.BUSY -> androidx.compose.ui.graphics.Color(0xFFFF3B30)
        OnlineStatus.OFFLINE -> androidx.compose.ui.graphics.Color(0xFF8E8E93)
        OnlineStatus.INVISIBLE -> androidx.compose.ui.graphics.Color(0xFF8E8E93)
    }

@Composable
private fun HeaderAction(icon: androidx.compose.ui.graphics.vector.ImageVector, title: Int, action: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = action).padding(6.dp)) {
        Icon(icon, null)
        Text(stringResource(title), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SettingsRows(
    model: ConversationSettingsViewModel,
    colors: AdaptiveColors,
    onStarred: () -> Unit,
    onVanish: () -> Unit,
    onPreferences: () -> Unit,
    onOpenGallery: () -> Unit,
    onClearMedia: () -> Unit,
) {
    val context = LocalContext.current
    Column(Modifier.padding(horizontal = 16.dp)) {
        SettingsRow(Icons.Default.Star, R.string.conversation_settings_starred, model.starredMessages.size.takeIf { it > 0 }?.toString() ?: stringResource(R.string.conversation_settings_starred_none), colors, onStarred)
        SettingsRow(
            Icons.Default.Timer,
            R.string.conversation_settings_vanish,
            when {
                !model.vanishModeActive -> stringResource(R.string.conversation_settings_no)
                model.vanishTimer == VanishMessageTimer.ONCE_SEEN -> stringResource(R.string.conversation_settings_once_seen)
                model.vanishTimer == VanishMessageTimer.HOURS_24 -> stringResource(R.string.conversation_settings_24_hours)
                model.vanishTimer == VanishMessageTimer.DAYS_7 -> stringResource(R.string.conversation_settings_7_days)
                else -> stringResource(R.string.conversation_settings_yes)
            },
            colors,
            onVanish,
        )
        SettingsRow(
            Icons.Default.Tune,
            R.string.conversation_settings_preferences,
            detail = null,
            subtitle = stringResource(R.string.conversation_settings_preferences_desc),
            colors = colors,
            action = onPreferences,
        )
        SettingsRow(
            Icons.Default.Folder,
            R.string.conversation_settings_storage,
            Formatter.formatFileSize(context, model.conversationMediaBytes),
            colors,
            onOpenGallery,
        )
        if (model.conversationMediaBytes > 0) {
            SettingsRow(Icons.Default.Delete, R.string.conversation_settings_clear_media, null, colors, onClearMedia, destructive = true)
        }
    }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: Int,
    detail: String?,
    colors: AdaptiveColors,
    action: () -> Unit,
    destructive: Boolean = false,
    subtitle: String? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = action)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            null,
            tint = if (destructive) MaterialTheme.colorScheme.error else colors.secondary,
            modifier = Modifier.size(24.dp),
        )
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(
                stringResource(title),
                color = if (destructive) MaterialTheme.colorScheme.error else colors.primary,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
            )
            subtitle?.let {
                Text(
                    it,
                    color = colors.tertiary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        detail?.let {
            Text(
                it,
                color = colors.tertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        if (!destructive) {
            Icon(
                Icons.Default.ChevronRight,
                null,
                tint = colors.tertiary,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun SharedContentTabs(
    tab: SharedContentTab,
    onTab: (SharedContentTab) -> Unit,
    model: ConversationSettingsViewModel,
    colors: AdaptiveColors,
    onOpenMedia: (SharedMedia) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // ≡ iOS `.pickerStyle(.segmented)` width 200, centrado — estilo pill perfil
        SharedContentTabPill(selected = tab, onSelect = onTab)
        if (tab == SharedContentTab.MEDIA) {
            if (model.sharedMedia.isEmpty()) {
                EmptyContent(Icons.Default.Folder, R.string.conversation_settings_media_empty, colors)
            } else {
                val rows = model.sharedMedia.chunked(3)
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    rows.forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            row.forEach { media ->
                                SharedMediaThumbnail(
                                    media = media,
                                    fillsGrid = true,
                                    onTap = { onOpenMedia(media) },
                                    modifier = Modifier.weight(1f).height(118.dp),
                                )
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        } else {
            val links = model.sharedLinks()
            if (links.isEmpty()) {
                EmptyContent(Icons.Default.Link, R.string.conversation_settings_links_empty, colors)
            } else {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    links.forEach { message ->
                        val url = Regex("https?://\\S+", RegexOption.IGNORE_CASE).find(message.content.orEmpty())?.value
                        if (url != null) {
                            LinkPreviewCard(url = url, outgoing = false, embedded = true)
                        }
                    }
                }
            }
        }
    }
}

/** ≡ iOS segmented Media/Links + estética `ProfilePillTabs` (thumb invertido). */
@Composable
private fun SharedContentTabPill(
    selected: SharedContentTab,
    onSelect: (SharedContentTab) -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val tabs = SharedContentTab.entries
    val selectedIndex = tabs.indexOf(selected).coerceAtLeast(0)
    val track = if (dark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
    val thumb = if (dark) Color(0xFFFAF9F6) else Color(0xFF0B1215)
    val selectedContent = if (dark) Color(0xFF0B1215) else Color.White
    val unselectedContent = if (dark) Color.White.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.45f)

    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
        BoxWithConstraints(
            Modifier
                .width(200.dp)
                .height(32.dp),
        ) {
            val density = LocalDensity.current
            val insetPx = with(density) { 3.dp.toPx() }
            val segmentPx = ((with(density) { maxWidth.toPx() } - insetPx * 2f) / tabs.size).coerceAtLeast(1f)
            val thumbPx by animateFloatAsState(
                targetValue = selectedIndex * segmentPx,
                animationSpec = tween(durationMillis = 180),
                label = "sharedContentThumb",
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(track, RoundedCornerShape(50)),
            )
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset { IntOffset((insetPx + thumbPx).roundToInt(), 0) }
                    .height(26.dp)
                    .fillMaxWidth(1f / tabs.size)
                    .background(thumb, RoundedCornerShape(50)),
            )
            Row(Modifier.fillMaxSize()) {
                tabs.forEach { item ->
                    val isSelected = item == selected
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clickable {
                                if (!isSelected) {
                                    HapticManager.shared.selection()
                                    onSelect(item)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(
                                when (item) {
                                    SharedContentTab.MEDIA -> R.string.chat_gallery_tab_media
                                    SharedContentTab.LINKS -> R.string.chat_gallery_tab_links
                                },
                            ),
                            color = if (isSelected) selectedContent else unselectedContent,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyContent(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: Int,
    colors: AdaptiveColors,
) = Column(
    Modifier.fillMaxWidth().padding(36.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
) {
    Icon(icon, null, tint = colors.tertiary, modifier = Modifier.size(30.dp))
    Text(stringResource(text), color = colors.tertiary, modifier = Modifier.padding(top = 10.dp))
}

@Composable
private fun SettingsFooter(model: ConversationSettingsViewModel, colors: AdaptiveColors) {
    val sent = stringResource(R.string.conversation_settings_messages_sent)
    val received = stringResource(R.string.conversation_settings_messages_received)
    val createdLabel = stringResource(R.string.conversation_settings_created)
    val messagesLabel = stringResource(R.string.conversation_settings_messages)
    Text(
        "$createdLabel: ${model.conversationCreatedDate}  •  $messagesLabel: ${model.totalMessages} (${model.sentMessagesCount} $sent, ${model.receivedMessagesCount} $received)",
        color = colors.tertiary,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
    )
}

@Composable
private fun ConversationStarredMessagesSheet(
    messages: List<EnhancedMessage>,
    currentUserId: String,
    otherParticipantName: String,
    colors: AdaptiveColors,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            stringResource(R.string.conversation_settings_starred),
            color = colors.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        if (messages.isEmpty()) {
            Text(stringResource(R.string.conversation_settings_starred_none), color = colors.tertiary)
        } else {
            LazyColumn {
                items(messages, key = { it.id }) { message ->
                    StarredMessageRow(
                        message = message,
                        currentUserId = currentUserId,
                        otherParticipantName = otherParticipantName,
                        colors = colors,
                        context = context,
                        onTap = { onSelect(message.id) },
                    )
                }
            }
        }
        Text(
            stringResource(R.string.common_cancel),
            modifier = Modifier
                .align(Alignment.End)
                .clickable(onClick = onDismiss)
                .padding(16.dp),
            color = colors.accent,
        )
    }
}

@Composable
private fun StarredMessageRow(
    message: EnhancedMessage,
    currentUserId: String,
    otherParticipantName: String,
    colors: AdaptiveColors,
    context: android.content.Context,
    onTap: () -> Unit,
) {
    val you = stringResource(R.string.chat_reply_you)
    val sender = if (message.senderId == currentUserId) you else otherParticipantName
    val preview = starredPreviewText(message, context)
    val relative = remember(message.id, message.timestamp) {
        MomentsFormat.relativeTime(message.timestamp)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        StarredMessageIcon(message, colors)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Row {
                Text(sender, color = colors.primary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(relative, color = colors.tertiary, fontSize = 12.sp)
            }
            Text(
                preview,
                color = colors.secondary,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun StarredMessageIcon(message: EnhancedMessage, colors: AdaptiveColors) {
    val thumb = message.thumbnailUrl ?: message.mediaUrl
    when (message.type) {
        MessageType.IMAGE, MessageType.VIEW_ONCE_IMAGE -> {
            if (!thumb.isNullOrBlank()) {
                AsyncImage(thumb, null, Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
            } else {
                StarredIconBadge(Icons.Default.Person, colors)
            }
        }
        MessageType.VIDEO, MessageType.VIEW_ONCE_VIDEO -> {
            Box(Modifier.size(40.dp)) {
                if (!thumb.isNullOrBlank()) {
                    AsyncImage(thumb, null, Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                } else {
                    StarredIconBadge(Icons.Default.PlayArrow, colors)
                }
                Icon(
                    Icons.Default.PlayArrow,
                    null,
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).size(12.dp),
                )
            }
        }
        else -> StarredIconBadge(Icons.Default.Star, colors)
    }
}

@Composable
private fun StarredIconBadge(icon: androidx.compose.ui.graphics.vector.ImageVector, colors: AdaptiveColors) {
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.secondary.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = colors.secondary, modifier = Modifier.size(18.dp))
    }
}

private fun starredPreviewText(message: EnhancedMessage, context: android.content.Context): String {
    if (message.isDeleted) return context.getString(R.string.messaging_message_deleted)
    if (message.type == MessageType.TEXT) {
        val content = message.content?.trim().orEmpty()
        return content.ifEmpty { message.type.displayName(context) }
    }
    return message.type.displayName(context)
}

@Composable
private fun ConversationChatPreferencesView(
    model: ConversationSettingsViewModel,
    onDismiss: () -> Unit,
    onRequestClearConversation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = rememberAdaptiveColors()
    Column(modifier.fillMaxSize().background(colors.chatBackground.first())) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = colors.primary) }
            Text(stringResource(R.string.conversation_settings_preferences), color = colors.primary, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Text(
                stringResource(R.string.conversation_settings_group_notifications),
                color = colors.secondary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
            )
            PreferenceToggleRow(
                title = R.string.conversation_settings_buzz,
                description = R.string.conversation_settings_buzz_desc,
                checked = model.buzzEnabled,
                colors = colors,
            ) {
                model.buzzEnabled = it
                model.toggleBuzzNotifications(context)
            }
            PreferenceDivider(colors)
            PreferenceToggleRow(
                title = R.string.conversation_settings_preview,
                description = R.string.conversation_settings_preview_desc,
                checked = model.messagePreviewEnabled,
                colors = colors,
            ) {
                model.messagePreviewEnabled = it
                model.toggleMessagePreview(context)
            }

            Text(
                stringResource(R.string.conversation_settings_group_privacy),
                color = colors.secondary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 28.dp, bottom = 8.dp),
            )
            PreferenceToggleRow(
                title = R.string.conversation_settings_read_receipts,
                description = R.string.conversation_settings_read_receipts_desc,
                checked = model.readReceiptsEnabled,
                colors = colors,
            ) {
                val previousValue = model.readReceiptsEnabled
                model.readReceiptsEnabled = it
                model.toggleReadReceipts(context, previousValue)
            }
            PreferenceDivider(colors)
            PreferenceToggleRow(
                title = R.string.conversation_settings_typing,
                description = R.string.conversation_settings_typing_desc,
                checked = model.typingIndicatorEnabled,
                colors = colors,
            ) {
                model.typingIndicatorEnabled = it
                model.toggleTypingIndicator(context)
            }
            PreferenceDivider(colors)
            PreferenceToggleRow(
                title = R.string.conversation_settings_forwarding,
                description = R.string.conversation_settings_forwarding_desc,
                checked = model.forwardingEnabled,
                colors = colors,
            ) {
                model.forwardingEnabled = it
                model.toggleForwarding(context)
            }
            Text(
                stringResource(R.string.conversation_settings_clear_conversation),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onRequestClearConversation).padding(vertical = 14.dp),
            )
        }
    }
}

@Composable
private fun PreferenceDivider(colors: AdaptiveColors) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(colors.tertiary.copy(alpha = if (colors.isDark) 0.16f else 0.12f)),
    )
}

@Composable
private fun PreferenceToggleRow(
    title: Int,
    description: Int,
    checked: Boolean,
    colors: AdaptiveColors,
    onChecked: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(stringResource(title), color = colors.primary, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Text(
                stringResource(description),
                color = colors.tertiary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = {
                HapticManager.shared.lightImpact()
                onChecked(it)
            },
        )
    }
}

@Composable
private fun ConversationVanishModeView(
    model: ConversationSettingsViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    Column(modifier.fillMaxSize().background(colors.chatBackground.first())) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = colors.primary) }
            Text(stringResource(R.string.conversation_settings_vanish), color = colors.primary, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(
                stringResource(R.string.conversation_settings_vanish_desc),
                color = colors.tertiary,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            VanishOption(R.string.conversation_settings_no, !model.vanishModeActive) { model.updateVanish(false, model.vanishTimer); onDismiss() }
            VanishOption(R.string.conversation_settings_once_seen, model.vanishModeActive && model.vanishTimer == VanishMessageTimer.ONCE_SEEN) { model.updateVanish(true, VanishMessageTimer.ONCE_SEEN); onDismiss() }
            VanishOption(R.string.conversation_settings_24_hours, model.vanishModeActive && model.vanishTimer == VanishMessageTimer.HOURS_24) { model.updateVanish(true, VanishMessageTimer.HOURS_24); onDismiss() }
            VanishOption(R.string.conversation_settings_7_days, model.vanishModeActive && model.vanishTimer == VanishMessageTimer.DAYS_7) { model.updateVanish(true, VanishMessageTimer.DAYS_7); onDismiss() }
        }
    }
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
