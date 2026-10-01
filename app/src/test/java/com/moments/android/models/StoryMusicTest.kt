package com.moments.android.models

import org.junit.Assert.*
import org.junit.Test

class StoryMusicTest {
    private val track = StoryMusicTrack("42", "Track", "Artist", 120.0, previewURL = "https://example.invalid/signed", waveform = listOf(.1f, .7f))
    @Test fun fragmentFitsInsideTrack() {
        val value = StoryMusicSelection(track, start = 115.0, duration = 30.0).clamped()
        assertEquals(90.0, value.start, 0.0)
        assertEquals(30.0, value.duration, 0.0)
    }
    @Test fun durationChangesKeepTheFragmentValid() {
        val value = StoryMusicSelection(track, start = 90.0, duration = 15.0).clamped(60.0)
        assertEquals(60.0, value.start, 0.0)
        assertEquals(60.0, value.duration, 0.0)
    }
    @Test fun roundTripPreservesSelectionButOmitsEphemeralAudioUrlsAndWaves() {
        val original = StoryMusicSelection(track, 32.0, 20.0, "cover", "BCA6FF", 3, 3.0)
        val encoded = original.toMap()
        val trackMap = encoded["track"] as Map<*, *>
        assertFalse(trackMap.containsKey("previewURL"))
        assertFalse(trackMap.containsKey("waveform"))
        val decoded = checkNotNull(StoryMusicSelection.from(encoded))
        assertEquals(original.start, decoded.start, 0.0)
        assertEquals(original.duration, decoded.duration, 0.0)
        assertEquals(original.style, decoded.style)
        assertEquals(original.colorHex, decoded.colorHex)
        assertEquals(original.rasterScale, decoded.rasterScale)
    }
    @Test fun stickerContractAcceptsMusicCreatedByIos() {
        val selection = StoryMusicSelection(track, 12.0, 15.0, "record")
        val sticker = StickerData.from(mapOf("type" to "generic", "content" to "", "positionX" to .3, "positionY" to .7, "music" to selection.toMap()))
        assertEquals("record", sticker.music?.style)
        assertEquals(.3, sticker.position.x, 0.0)
        assertEquals(12.0, sticker.music!!.start, 0.0)
        assertNotNull(sticker.toMap()["music"])
    }
}
