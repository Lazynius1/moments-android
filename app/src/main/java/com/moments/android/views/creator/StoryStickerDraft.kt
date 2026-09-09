package com.moments.android.views.creator

import android.graphics.Bitmap
import java.util.UUID

/**
 * Draft local de sticker en el editor — espejo mínimo de iOS `StickerItem`.
 * Chunk stickers-1: emoji (+ tipos stub para el catálogo).
 */
data class StoryStickerDraft(
    val id: String = UUID.randomUUID().toString(),
    val type: String = "emoji",
    /** Contenido visible (emoji, texto, etc.). */
    val content: String = "",
    /** Posición normalizada 0..1 relativa al canvas. */
    val normalizedX: Double = 0.5,
    val normalizedY: Double = 0.5,
    val scale: Double = 1.0,
    val rotationRadians: Double = 0.0,
    val zIndex: Int = 0,
    val gifURL: String? = null,
    val videoURL: String? = null,
    val isAnimated: Boolean = false,
    val hashtag: String? = null,
    val weatherSymbol: String? = null,
    val username: String? = null,
    val userId: String? = null,
    /** Avatar mention — ≡ `StickerInteractionData.profileImagePath`. */
    val profileImagePath: String? = null,
    val sharedMediaPath: String? = null,
    /** iOS time sticker: hora en questionText, fecha en caption. */
    val questionText: String? = null,
    val caption: String? = null,
    /** iOS poll: [question, option1, option2]. */
    val pollOptions: List<String>? = null,
    val linkURL: String? = null,
    val linkTitle: String? = null,
    /** iOS location sticker. */
    val location: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** iOS countdown sticker. */
    val countdownTitle: String? = null,
    val countdownTargetAtMs: Double? = null,
    /** iOS quiz sticker. */
    val quizQuestion: String? = null,
    val quizOptions: List<String>? = null,
    val quizCorrectIndex: Int? = null,
    /** iOS emojiSlider sticker. */
    val sliderEmoji: String? = null,
    val sliderPrompt: String? = null,
    /** Bitmap efímero de selfie o marco; se persiste al publicar. */
    val image: Bitmap? = null,
    /** Contrato `StickerInteractionData` para el Polaroid de iOS. */
    val frameStyle: String? = null,
    val contentScale: Double? = null,
    val contentOffsetX: Double? = null,
    val contentOffsetY: Double? = null,
    /** ≡ StickerInteractionData.styleVariant — tipografía/estilo ciclable. */
    val styleVariant: Int? = null,
    val cardLayoutVariant: Int? = null,
    val revealType: String? = null,
    val revealPattern: String? = null,
    val revealPrimaryColor: String? = null,
    val revealSecondaryColor: String? = null,
    val revealEffectColor: String? = null,
    val audioURL: String? = null,
    val audioDuration: Double? = null,
    /** ≡ StickerInteractionData.momentId / mediaCount (shareMoment). */
    val momentId: String? = null,
    val mediaCount: Int? = null,
)

/** ≡ iOS `normalizedChatStickerData` / `StickerData.from` — posiciones ya normalizadas. */
fun StoryStickerDraft.toStickerData(zIndex: Int = this.zIndex): com.moments.android.models.StickerData {
    val encodedImage = image?.takeUnless { it.isRecycled }?.let { bmp ->
        if (type == "emoji") return@let null
        runCatching {
            java.io.ByteArrayOutputStream().use { out ->
                bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
            }
        }.getOrNull()
    }
    return com.moments.android.models.StickerData(
        stickerId = id,
        type = type,
        content = encodedImage ?: content,
        position = com.moments.android.models.Point(normalizedX, normalizedY),
        scale = scale,
        rotation = rotationRadians,
        zIndex = zIndex,
        username = username,
        userId = userId,
        hashtag = hashtag,
        location = location,
        latitude = latitude,
        longitude = longitude,
        styleVariant = styleVariant,
        cardLayoutVariant = cardLayoutVariant,
        questionText = questionText,
        pollOptions = pollOptions,
        weatherSymbol = weatherSymbol,
        linkURL = linkURL,
        linkTitle = linkTitle,
        countdownTitle = countdownTitle,
        countdownTargetAtMs = countdownTargetAtMs,
        sliderEmoji = sliderEmoji,
        sliderPrompt = sliderPrompt,
        caption = caption,
        profileImagePath = profileImagePath,
        sharedMediaPath = sharedMediaPath,
        momentId = momentId,
        mediaCount = mediaCount,
        quizQuestion = quizQuestion,
        quizOptions = quizOptions,
        quizCorrectIndex = quizCorrectIndex,
        revealType = revealType,
        revealPattern = revealPattern,
        revealPrimaryColor = revealPrimaryColor,
        revealSecondaryColor = revealSecondaryColor,
        revealEffectColor = revealEffectColor,
        frameStyle = frameStyle,
        contentScale = contentScale,
        contentOffsetX = contentOffsetX,
        contentOffsetY = contentOffsetY,
        audioURL = audioURL,
        audioDuration = audioDuration,
        isAnimated = isAnimated,
        gifURL = gifURL,
        videoURL = videoURL,
    )
}
