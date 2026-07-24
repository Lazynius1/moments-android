package com.moments.android.views.messaging.screens.chat

import android.content.Context
import android.location.LocationManager
import com.moments.android.models.EnhancedMessage
import com.moments.android.models.MessageStatus
import com.moments.android.models.MessageType
import com.moments.android.views.messaging.media.CameraPickerMediaType
import com.moments.android.views.messaging.media.ChatMediaOverlayPayload
import com.moments.android.views.messaging.models.ChatGiphyAsset
import com.moments.android.views.messaging.models.ChatRecentStickersStore
import com.moments.android.views.messaging.models.ChatStickerAsset
import com.moments.android.views.messaging.models.LiveLocationDuration
import com.moments.android.views.messaging.services.ChatService
import com.moments.android.views.messaging.services.LiveLocationSharingService
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Port de `Views/Messaging/Screens/Chat/MomentsChatViewModel+Media.swift`. */
private val chatMediaScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

fun MomentsChatViewModel.sendImageMessageForReply(data: ByteArray, replyTo: String?) {
    if (data.isEmpty()) return
    if (conversationId.isBlank()) {
        ensureConversationExists { id -> if (!id.isNullOrBlank()) sendImageMessageForReply(data, replyTo) }
        return
    }
    trackMediaMessageSent()
    sendMediaMessage(
        data = data,
        type = MessageType.IMAGE,
        fileName = "image_${UUID.randomUUID()}.jpg",
        replyTo = replyTo,
    )
}

fun MomentsChatViewModel.sendViewOnceMessage(
    data: ByteArray,
    mediaType: CameraPickerMediaType,
    allowReplay: Boolean = false,
    replyTo: String? = null,
    overlayPayload: ChatMediaOverlayPayload? = null,
) {
    if (data.isEmpty() || conversationId.isBlank()) return
    val messageId = UUID.randomUUID().toString()
    val type = if (mediaType == CameraPickerMediaType.IMAGE) MessageType.VIEW_ONCE_IMAGE else MessageType.VIEW_ONCE_VIDEO
    appendOutgoingMessage(
        EnhancedMessage(
            id = messageId,
            conversationId = conversationId,
            senderId = currentUserId,
            type = type,
            status = MessageStatus.SENDING,
            replyTo = replyTo,
            isViewed = false,
            textOverlayLive = overlayPayload?.textOverlayLive,
            textOverlays = overlayPayload?.textOverlays,
            stickers = overlayPayload?.stickers,
            drawingData = overlayPayload?.drawingData,
            allowReplay = allowReplay.takeIf { it },
            isVanishModeMessage = marksOutgoingAsVanish,
        ),
    )
    trackMediaMessageSent()
    chatMediaScope.launch {
        ChatService.sendViewOnceMessage(
            conversationId = conversationId,
            senderId = currentUserId,
            mediaData = data,
            isImage = mediaType == CameraPickerMediaType.IMAGE,
            messageId = messageId,
            isVanishModeMessage = marksOutgoingAsVanish,
            allowReplay = allowReplay,
            replyTo = replyTo,
            overlayPayload = overlayPayload,
        ).onSuccess { sent ->
            applyOutgoingMessageUpdate(messageId, sent.status, sent.mediaUrl, sent.thumbnailUrl)
        }.onFailure {
            applyOutgoingMessageUpdate(messageId, MessageStatus.FAILED)
        }
    }
}

fun MomentsChatViewModel.sendGif(asset: ChatGiphyAsset, replyTo: String? = null) {
    if (conversationId.isBlank()) {
        ensureConversationExists { id -> if (!id.isNullOrBlank()) sendGif(asset, replyTo) }
        return
    }
    val messageId = UUID.randomUUID().toString()
    appendOutgoingMessage(
        EnhancedMessage(
            id = messageId,
            conversationId = conversationId,
            senderId = currentUserId,
            type = MessageType.GIF,
            mediaUrl = asset.url,
            mediaWidth = asset.width.takeIf { it > 0 },
            mediaHeight = asset.height.takeIf { it > 0 },
            status = MessageStatus.SENDING,
            replyTo = replyTo,
            isVanishModeMessage = marksOutgoingAsVanish,
        ),
    )
    trackMediaMessageSent()
    chatMediaScope.launch {
        ChatService.sendGiphyReferenceMessage(
            conversationId, currentUserId, MessageType.GIF, asset.id, asset.url, asset.width, asset.height,
            messageId, marksOutgoingAsVanish, replyTo,
        ).onSuccess { sent -> applyOutgoingMessageUpdate(messageId, sent.status, sent.mediaUrl) }
            .onFailure { applyOutgoingMessageUpdate(messageId, MessageStatus.FAILED) }
    }
}

fun MomentsChatViewModel.sendSticker(context: Context, asset: ChatStickerAsset, replyTo: String? = null) {
    if (conversationId.isBlank()) {
        ensureConversationExists { id -> if (!id.isNullOrBlank()) sendSticker(context, asset, replyTo) }
        return
    }
    ChatRecentStickersStore.add(context, asset)
    val messageId = UUID.randomUUID().toString()
    appendOutgoingMessage(
        EnhancedMessage(
            id = messageId,
            conversationId = conversationId,
            senderId = currentUserId,
            type = MessageType.STICKER,
            mediaUrl = asset.url,
            mediaWidth = asset.width.takeIf { it > 0 },
            mediaHeight = asset.height.takeIf { it > 0 },
            status = MessageStatus.SENDING,
            replyTo = replyTo,
            isVanishModeMessage = marksOutgoingAsVanish,
        ),
    )
    trackMediaMessageSent()
    chatMediaScope.launch {
        ChatService.sendGiphyReferenceMessage(
            conversationId, currentUserId, MessageType.STICKER, asset.id, asset.url, asset.width, asset.height,
            messageId, marksOutgoingAsVanish, replyTo,
        ).onSuccess { sent -> applyOutgoingMessageUpdate(messageId, sent.status, sent.mediaUrl) }
            .onFailure { applyOutgoingMessageUpdate(messageId, MessageStatus.FAILED) }
    }
}

fun MomentsChatViewModel.sendStaticLocation(latitude: Double, longitude: Double, name: String?, address: String?) {
    if (conversationId.isBlank()) return
    val messageId = UUID.randomUUID().toString()
    appendOutgoingMessage(
        EnhancedMessage(
            id = messageId, conversationId = conversationId, senderId = currentUserId,
            type = MessageType.LOCATION, latitude = latitude, longitude = longitude,
            locationName = name, locationAddress = address, isLiveLocation = false,
            timestamp = Date(), status = MessageStatus.SENDING, isVanishModeMessage = marksOutgoingAsVanish,
        ),
    )
    trackMediaMessageSent()
    chatMediaScope.launch {
        ChatService.sendStaticLocationMessage(conversationId, currentUserId, latitude, longitude, name, address, messageId, marksOutgoingAsVanish)
            .onSuccess { sent -> applyOutgoingMessageUpdate(messageId, sent.status) }
            .onFailure { applyOutgoingMessageUpdate(messageId, MessageStatus.FAILED) }
    }
}

fun MomentsChatViewModel.startLiveLocation(context: Context, duration: LiveLocationDuration) {
    if (conversationId.isBlank()) return
    val manager = context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
    val location = runCatching {
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { manager.getLastKnownLocation(it) }
            .maxByOrNull { it.time }
    }.getOrNull() ?: return
    val messageId = UUID.randomUUID().toString()
    val sessionId = UUID.randomUUID().toString()
    val expiresAt = Date(Date().time + duration.timeIntervalMillis)
    appendOutgoingMessage(
        EnhancedMessage(
            id = messageId, conversationId = conversationId, senderId = currentUserId,
            type = MessageType.LOCATION, latitude = location.latitude, longitude = location.longitude,
            isLiveLocation = true, liveLocationExpiresAt = expiresAt, liveLocationDuration = duration.firestoreValue,
            liveLocationSessionId = sessionId, locationUpdatedAt = Date(), timestamp = Date(),
            status = MessageStatus.SENDING, isVanishModeMessage = marksOutgoingAsVanish,
        ),
    )
    trackMediaMessageSent()
    chatMediaScope.launch {
        ChatService.sendLiveLocationMessage(
            conversationId, currentUserId, location.latitude, location.longitude, null, null, duration,
            sessionId, expiresAt, messageId, marksOutgoingAsVanish,
        ).onSuccess { sent ->
            applyOutgoingMessageUpdate(messageId, sent.status)
            LiveLocationSharingService.startSession(conversationId, messageId, sessionId, duration, expiresAt)
        }.onFailure { applyOutgoingMessageUpdate(messageId, MessageStatus.FAILED) }
    }
}

fun MomentsChatViewModel.stopLiveLocation(messageId: String) {
    if (conversationId.isBlank()) return
    LiveLocationSharingService.stopSharing(messageId, conversationId)
    messages.value.firstOrNull { it.id == messageId }?.let { message ->
        appendOrReplaceMessage(message.copy(liveLocationStoppedAt = Date()))
    }
}
