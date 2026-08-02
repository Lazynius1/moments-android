package com.moments.android.views.messaging.screens.chat

import android.content.Context
import android.location.LocationManager
import com.moments.android.MomentsApplication
import com.moments.android.R
import com.moments.android.views.messaging.core.EnhancedMessage
import com.moments.android.views.messaging.core.MessageStatus
import com.moments.android.views.messaging.core.MessageType
import com.moments.android.views.messaging.media.CameraPickerMediaType
import com.moments.android.views.messaging.media.ChatMediaOverlayPayload
import com.moments.android.views.messaging.models.ChatGiphyAsset
import com.moments.android.views.messaging.models.ChatRecentStickersStore
import com.moments.android.views.messaging.models.ChatStickerAsset
import com.moments.android.views.messaging.models.LiveLocationDuration
import com.moments.android.views.messaging.services.ChatService
import com.moments.android.views.messaging.services.LiveLocationSharingService
import com.moments.android.views.messaging.services.sendViewOnceMessage
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Port de `Views/Messaging/Screens/Chat/MomentsChatViewModel+Media.swift`.
 *
 * `sendImageMessage` / `sendAudioMessage` viven en [com.moments.android.views.messaging.core.ChatViewModel]
 * (mismo comportamiento; aquí solo reply + view-once / gif / sticker / location).
 */
private val chatMediaScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

/** ≡ iOS `sendImageMessage(_:replyTo:)` para respuesta con foto. */
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
    if (data.isEmpty()) return
    if (conversationId.isBlank()) {
        val app = MomentsApplication.instance
        reportError(app?.getString(R.string.chat_error_invalid_conversation_view_once))
        return
    }
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
            // ≡ iOS temp: outgoingVanishMessageFlag; servicio: marksOutgoingAsVanish
            isVanishModeMessage = outgoingVanishMessageFlag == true,
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
        }.onFailure { error ->
            applyOutgoingMessageUpdate(messageId, MessageStatus.FAILED)
            reportError(error.message)
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
            isVanishModeMessage = outgoingVanishMessageFlag == true,
        ),
    )
    trackMediaMessageSent()
    chatMediaScope.launch {
        ChatService.sendGiphyReferenceMessage(
            conversationId, currentUserId, MessageType.GIF, asset.id, asset.url, asset.width, asset.height,
            messageId, marksOutgoingAsVanish, replyTo,
        ).onSuccess { sent ->
            applyOutgoingMessageUpdate(messageId, sent.status, sent.mediaUrl ?: asset.url)
        }.onFailure { error ->
            applyOutgoingMessageUpdate(messageId, MessageStatus.FAILED)
            reportError(error.message)
        }
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
            isVanishModeMessage = outgoingVanishMessageFlag == true,
        ),
    )
    trackMediaMessageSent()
    chatMediaScope.launch {
        ChatService.sendGiphyReferenceMessage(
            conversationId, currentUserId, MessageType.STICKER, asset.id, asset.url, asset.width, asset.height,
            messageId, marksOutgoingAsVanish, replyTo,
        ).onSuccess { sent ->
            applyOutgoingMessageUpdate(messageId, sent.status, sent.mediaUrl ?: asset.url)
        }.onFailure { error ->
            applyOutgoingMessageUpdate(messageId, MessageStatus.FAILED)
            reportError(error.message)
        }
    }
}

fun MomentsChatViewModel.sendStaticLocation(latitude: Double, longitude: Double, name: String?, address: String?) {
    if (conversationId.isBlank()) {
        val app = MomentsApplication.instance
        reportError(app?.getString(R.string.chat_error_invalid_conversation_text))
        return
    }
    val messageId = UUID.randomUUID().toString()
    appendOutgoingMessage(
        EnhancedMessage(
            id = messageId, conversationId = conversationId, senderId = currentUserId,
            type = MessageType.LOCATION, latitude = latitude, longitude = longitude,
            locationName = name, locationAddress = address, isLiveLocation = false,
            timestamp = Date(), status = MessageStatus.SENDING,
            isVanishModeMessage = outgoingVanishMessageFlag == true,
        ),
    )
    trackMediaMessageSent()
    chatMediaScope.launch {
        ChatService.sendStaticLocationMessage(
            conversationId, currentUserId, latitude, longitude, name, address, messageId, marksOutgoingAsVanish,
        ).onSuccess { sent -> applyOutgoingMessageUpdate(messageId, sent.status) }
            .onFailure { error ->
                applyOutgoingMessageUpdate(messageId, MessageStatus.FAILED)
                reportError(error.message)
            }
    }
}

fun MomentsChatViewModel.startLiveLocation(context: Context, duration: LiveLocationDuration) {
    if (conversationId.isBlank()) {
        reportError(context.getString(R.string.chat_error_invalid_conversation_text))
        return
    }
    val manager = context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    val location = runCatching {
        manager?.let { mgr ->
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .mapNotNull { mgr.getLastKnownLocation(it) }
                .maxByOrNull { it.time }
        }
    }.getOrNull()
    if (location == null) {
        // ≡ chat.location.permissionNeeded — el sheet ya gatea permiso; aquí falta fix GPS
        reportError(context.getString(R.string.chat_location_permission_needed))
        return
    }
    val messageId = UUID.randomUUID().toString()
    val sessionId = UUID.randomUUID().toString()
    val expiresAt = Date(Date().time + duration.timeIntervalMillis)
    appendOutgoingMessage(
        EnhancedMessage(
            id = messageId, conversationId = conversationId, senderId = currentUserId,
            type = MessageType.LOCATION, latitude = location.latitude, longitude = location.longitude,
            isLiveLocation = true, liveLocationExpiresAt = expiresAt, liveLocationDuration = duration.firestoreValue,
            liveLocationSessionId = sessionId, locationUpdatedAt = Date(), timestamp = Date(),
            status = MessageStatus.SENDING, isVanishModeMessage = outgoingVanishMessageFlag == true,
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
        }.onFailure { error ->
            applyOutgoingMessageUpdate(messageId, MessageStatus.FAILED)
            reportError(error.message)
        }
    }
}

fun MomentsChatViewModel.stopLiveLocation(messageId: String) {
    if (conversationId.isBlank()) return
    LiveLocationSharingService.stopSharing(messageId, conversationId)
    messages.value.firstOrNull { it.id == messageId }?.let { message ->
        appendOrReplaceMessage(message.copy(liveLocationStoppedAt = Date()))
    }
}
