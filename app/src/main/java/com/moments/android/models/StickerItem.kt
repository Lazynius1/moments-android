package com.moments.android.models

import android.graphics.Bitmap
import java.util.UUID

/**
 * Port de `Models/StickerItem.swift`.
 * `UIImage` → [Bitmap]; `CGPoint`/`Angle` → [Point] / radianes.
 * `PHAsset` de [MediaLibraryItem] no se porta (Photos framework iOS).
 */

// MARK: - MediaLibraryItem

data class MediaLibraryItem(
    val id: String,
    val thumbnail: Bitmap,
    val isVideo: Boolean,
    val duration: Double? = null,
    val videoURL: String? = null,
)

// MARK: - Tipo de sticker (taxonomía; la referencia StickerData.type)

enum class StickerType(val raw: String) {
    EMOJI("emoji"),
    STICKER("sticker"),
    MENTION("mention"),
    HASHTAG("hashtag"),
    LOCATION("location"),
    POLL("poll"),
    QUESTION("question"),
    LINK("link"),
    COUNTDOWN("countdown"),
    EMOJI_SLIDER("emojiSlider"),
    QUESTION_RESPONSE("questionResponse"),
    GENERIC("generic"),
    WEATHER("weather"),
    TIME("time"),
    SELFIE("selfie"),
    SHARE_MOMENT("shareMoment"),
    QUIZ("quiz"),
    FRAME("frame"),
    REVEAL("reveal"),
    AUDIO("audio");

    companion object {
        fun from(raw: String?) = entries.firstOrNull { it.raw == raw }
    }
}

// MARK: - Datos de interacción de un sticker

data class StickerInteractionData(
    val username: String? = null,
    val userId: String? = null,
    val hashtag: String? = null,
    val location: String? = null,
    val locationCoordinate: Moment.LocationCoordinate? = null,
    val styleVariant: Int? = null,
    val pollData: List<String>? = null,
    val questionText: String? = null,
    val weatherSymbol: String? = null,
    val linkURL: String? = null,
    val linkTitle: String? = null,
    val countdownTitle: String? = null,
    val countdownTargetAtMs: Double? = null,
    val sliderEmoji: String? = null,
    val sliderPrompt: String? = null,
    val caption: String? = null,
    val profileImagePath: String? = null,
    val momentId: String? = null,
    val mediaCount: Int? = null,
    val quizQuestion: String? = null,
    val quizOptions: List<String>? = null,
    val quizCorrectIndex: Int? = null,
    val revealType: String? = null,
    val revealPattern: String? = null,
    val revealPrimaryColor: String? = null,
    val revealSecondaryColor: String? = null,
    val revealEffectColor: String? = null,
    val frameStyle: String? = null,
    val contentScale: Double? = null,
    val contentOffsetX: Double? = null,
    val contentOffsetY: Double? = null,
    val audioURL: String? = null,
    val audioDuration: Double? = null,
)

// MARK: - StickerItem (estado de editor; espejo de iOS)

data class StickerItem(
    val id: String,
    val image: Bitmap,
    var position: Point,
    var scale: Double = 1.0,
    /** ≡ SwiftUI `Angle` en radianes. */
    var rotationRadians: Double = 0.0,
    var zIndex: Int = 0,
    val gifURL: String? = null,
    val videoURL: String? = null,
    val isAnimated: Boolean,
    val type: StickerType,
    var interactionData: StickerInteractionData? = null,
) {
    companion object {
        /** ≡ init(image:position:type:interactionData:zIndex:videoURL:gifURL:). */
        fun create(
            image: Bitmap,
            position: Point,
            type: StickerType,
            interactionData: StickerInteractionData?,
            zIndex: Int = 0,
            videoURL: String? = null,
            gifURL: String? = null,
        ): StickerItem = StickerItem(
            id = "${type.raw}_${UUID.randomUUID()}",
            image = image,
            position = position,
            zIndex = zIndex,
            type = type,
            gifURL = gifURL,
            videoURL = videoURL,
            isAnimated = videoURL != null || gifURL != null,
            interactionData = interactionData,
        )

        /** ≡ init(image:gifURL:position:type:interactionData:zIndex:). */
        fun createGif(
            image: Bitmap,
            gifURL: String,
            position: Point,
            type: StickerType,
            interactionData: StickerInteractionData?,
            zIndex: Int = 0,
        ): StickerItem = StickerItem(
            id = "${type.raw}_${UUID.randomUUID()}",
            image = image,
            position = position,
            zIndex = zIndex,
            type = type,
            gifURL = gifURL,
            videoURL = null,
            isAnimated = true,
            interactionData = interactionData,
        )

        /** ≡ init(id:image:position:scale:rotation:zIndex:gifURL:videoURL:isAnimated:type:interactionData:). */
        fun restore(
            id: String,
            image: Bitmap,
            position: Point,
            scale: Double,
            rotationRadians: Double,
            zIndex: Int = 0,
            gifURL: String?,
            videoURL: String? = null,
            isAnimated: Boolean,
            type: StickerType,
            interactionData: StickerInteractionData?,
        ): StickerItem = StickerItem(
            id = id,
            image = image,
            position = position,
            scale = scale,
            rotationRadians = rotationRadians,
            zIndex = zIndex,
            gifURL = gifURL,
            videoURL = videoURL,
            isAnimated = isAnimated,
            type = type,
            interactionData = interactionData,
        )
    }
}
