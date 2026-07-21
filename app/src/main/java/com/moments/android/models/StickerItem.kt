package com.moments.android.models

/**
 * Del StickerItem.swift de iOS se portan aquí las partes de DATOS.
 * El objeto de UI `StickerItem` (lleva UIImage/Angle) y `MediaLibraryItem`
 * (UIImage/PHAsset) van al módulo Creator con equivalentes Android (Bitmap, MediaStore).
 */

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

    companion object { fun from(raw: String?) = entries.firstOrNull { it.raw == raw } }
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
