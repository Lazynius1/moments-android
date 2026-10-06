package com.moments.android.models

import com.moments.android.models.cache.CachedStory
import java.util.Date
import org.junit.Assert.*
import org.junit.Test

class StoryInteractionSettingsTest {
    private fun story(settings: Map<String, Boolean>? = null) = Story(
        id = "story", authorId = "creator", username = "creator", duration = 60.0,
        timestamp = Date(1000), expirationDate = Date(86401000), expirationHours = 24,
        mediaItem = MediaItem(type = MediaItem.MediaType.IMAGE, url = "https://example.invalid/image.jpg"),
        interactionSettings = settings,
    )

    @Test fun publishedStoryPreservesDisabledAndEnabledInteractions() {
        val settings = mapOf("allowStoryMessages" to false, "allowStoryReactions" to true, "allowStoryEphemeralPhotos" to false)
        val decoded = checkNotNull(Story.from("story", story(settings).toMap()))
        assertEquals(settings, decoded.interactionSettings)
    }

    @Test fun legacyStoryKeepsAccountFallbackInsteadOfAnExplicitOverride() {
        val encoded = story().toMap()
        assertFalse(encoded.containsKey("interactionSettings"))
        assertNull(checkNotNull(Story.from("story", encoded)).interactionSettings)
    }

    @Test fun cachedStoryPreservesInteractionsAndLongPhotoDuration() {
        val original = story(mapOf("allowStoryReactions" to false))
        val cached = checkNotNull(CachedStory.fromStory(original))
        val restored = cached.toStory()
        assertEquals(original.interactionSettings, restored.interactionSettings)
        assertEquals(60.0, restored.duration, 0.0)
    }
    @Test fun reusedAudioPreservesOriginalIdentityAcrossPlatforms() {
        val encoded = mapOf<String, Any?>("type" to "audio", "stickerId" to "clip", "content" to "", "audioURL" to "https://example.invalid/audio", "audioDuration" to 60.0, "originalAudioId" to "a".repeat(64))
        val clip = StickerData.from(encoded)
        val restored = StickerData.from(clip.toMap())
        assertEquals("a".repeat(64), restored.originalAudioId)
        assertEquals(60.0, restored.audioDuration!!, 0.0)
        assertNull(StickerData.from(encoded - "originalAudioId").originalAudioId)
    }
}
