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
    val revealType: String? = null,
    val revealPattern: String? = null,
    val revealPrimaryColor: String? = null,
    val revealSecondaryColor: String? = null,
    val revealEffectColor: String? = null,
    val audioURL: String? = null,
    val audioDuration: Double? = null,
)
