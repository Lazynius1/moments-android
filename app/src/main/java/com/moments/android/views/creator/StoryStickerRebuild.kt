package com.moments.android.views.creator

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.moments.android.models.CachedSticker
import com.moments.android.models.StickerData
import com.moments.android.services.storage.storageUploadJpegData
import java.io.ByteArrayOutputStream
import java.io.File

/** Reconstruye [StickerData] desde payload iOS [CachedSticker] (BackgroundStoryUploadService resume). */
internal object StoryStickerRebuild {

    fun rebuildStickers(cachedStickers: List<CachedSticker>, uploadsDir: File): List<StickerData> =
        cachedStickers.mapNotNull { cached ->
            runCatching { toStickerData(cached, uploadsDir) }.getOrNull()
        }

    private fun toStickerData(cached: CachedSticker, uploadsDir: File): StickerData {
        val bitmap = cached.localImageName?.let { name ->
            val file = File(uploadsDir, name)
            if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
        }
        val interaction = cached.interactionData
        return StickerData(
            stickerId = cached.id,
            type = cached.type,
            content = extractContent(cached, bitmap),
            position = cached.position,
            scale = cached.scale,
            rotation = cached.rotationRadians,
            username = interaction?.username,
            userId = interaction?.userId,
            hashtag = interaction?.hashtag,
            location = interaction?.location,
            latitude = interaction?.latitude,
            longitude = interaction?.longitude,
            styleVariant = interaction?.styleVariant,
            questionText = interaction?.questionText,
            pollOptions = interaction?.pollData,
            weatherSymbol = interaction?.weatherSymbol,
            linkURL = interaction?.linkURL,
            linkTitle = interaction?.linkTitle,
            countdownTitle = interaction?.countdownTitle,
            countdownTargetAtMs = interaction?.countdownTargetAtMs,
            sliderEmoji = interaction?.sliderEmoji,
            sliderPrompt = interaction?.sliderPrompt,
            caption = interaction?.caption,
            profileImagePath = interaction?.profileImagePath,
            momentId = interaction?.momentId,
            mediaCount = interaction?.mediaCount,
            quizQuestion = interaction?.quizQuestion,
            quizOptions = interaction?.quizOptions,
            quizCorrectIndex = interaction?.quizCorrectIndex,
            revealType = interaction?.revealType,
            revealPattern = interaction?.revealPattern,
            revealPrimaryColor = interaction?.revealPrimaryColor,
            revealSecondaryColor = interaction?.revealSecondaryColor,
            revealEffectColor = interaction?.revealEffectColor,
            frameStyle = interaction?.frameStyle,
            contentScale = interaction?.contentScale,
            contentOffsetX = interaction?.contentOffsetX,
            contentOffsetY = interaction?.contentOffsetY,
            audioURL = interaction?.audioURL,
            audioDuration = interaction?.audioDuration,
            isAnimated = cached.isAnimated,
            gifURL = cached.gifURL,
            videoURL = cached.videoURL,
        )
    }

    private fun extractContent(cached: CachedSticker, bitmap: Bitmap?): String {
        if (cached.type == "selfie" && bitmap != null) {
            val png = ByteArrayOutputStream().apply {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, this)
            }.toByteArray()
            return Base64.encodeToString(png, Base64.NO_WRAP)
        }
        if (cached.type == "frame" && bitmap != null) {
            bitmap.storageUploadJpegData(compressionQuality = 0.42f, maxPixelDimension = 900)?.let {
                return Base64.encodeToString(it, Base64.NO_WRAP)
            }
        }
        val imageTypes = setOf(
            "generic", "sticker", "emoji", "time", "selfie", "questionResponse",
            "shareMoment", "link", "countdown", "emojiSlider", "frame", "quiz",
        )
        if (cached.type in imageTypes && bitmap != null) {
            bitmap.storageUploadJpegData(compressionQuality = 0.6f, maxPixelDimension = 480)?.let {
                return Base64.encodeToString(it, Base64.NO_WRAP)
            }
        }
        cached.interactionData?.let { data ->
            when (cached.type) {
                "mention" -> return "@${data.username.orEmpty()}"
                "hashtag" -> return "#${data.hashtag.orEmpty()}"
                "location" -> return data.location.orEmpty()
                "question" -> return data.questionText.orEmpty()
                "poll" -> return data.pollData?.joinToString("|").orEmpty()
                "weather" -> return data.weatherSymbol ?: "🌤️"
                "link" -> return data.linkURL.orEmpty()
                "countdown" -> return data.countdownTitle.orEmpty()
                "emojiSlider" -> return data.sliderPrompt.orEmpty()
            }
        }
        if (cached.isAnimated) {
            cached.videoURL?.let { return it }
            cached.gifURL?.let { return it }
        }
        return "sticker_${cached.type}"
    }
}
