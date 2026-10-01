package com.moments.android.models

/** Same persisted contract as StoryMusicSelection in iOS. Preview URLs and waves are ephemeral. */
data class StoryMusicTrack(val id: String, val title: String, val artist: String, val duration: Double,
    val artworkURL: String? = null, val previewURL: String? = null, val lyrics: String? = null,
    val waveform: List<Float>? = null) {
    fun toMap(persisted: Boolean = true): Map<String, Any> = buildMap {
        put("id", id); put("title", title); put("artist", artist); put("duration", duration)
        artworkURL?.let { put("artworkURL", it) }; lyrics?.let { put("lyrics", it) }
        if (!persisted) { previewURL?.let { put("previewURL", it) }; waveform?.let { put("waveform", it) } }
    }
    companion object {
        fun from(data: Map<String, Any?>): StoryMusicTrack = StoryMusicTrack(
            data["id"] as? String ?: "", data["title"] as? String ?: "", data["artist"] as? String ?: "",
            (data["duration"] as? Number)?.toDouble()?.takeIf { it.isFinite() && it > 0 } ?: 15.0,
            data["artworkURL"] as? String, data["previewURL"] as? String, data["lyrics"] as? String,
            (data["waveform"] as? List<*>)?.mapNotNull { (it as? Number)?.toFloat() })
    }
}
data class StoryMusicSelection(val track: StoryMusicTrack, val start: Double = 0.0, val duration: Double = 15.0,
    val style: String = "card", val colorHex: String = "FFFFFF", val cardStyleVariant: Int? = null,
    val rasterScale: Double? = null, val lyricText: String? = null) {
    fun clamped(seconds: Double = duration): StoryMusicSelection {
        val length = seconds.takeIf { it.isFinite() }?.coerceIn(.1, track.duration) ?: minOf(15.0, track.duration)
        return copy(duration = length, start = (start.takeIf { it.isFinite() } ?: 0.0).coerceIn(0.0, (track.duration - length).coerceAtLeast(0.0)))
    }
    fun toMap(): Map<String, Any> = buildMap {
        put("track", track.toMap()); put("start", start); put("duration", duration); put("style", style); put("colorHex", colorHex)
        cardStyleVariant?.let { put("cardStyleVariant", it) }; rasterScale?.let { put("rasterScale", it) }; lyricText?.let { put("lyricText", it) }
    }
    companion object {
        @Suppress("UNCHECKED_CAST") fun from(raw: Any?): StoryMusicSelection? {
            val data = raw as? Map<String, Any?> ?: return null
            val track = data["track"] as? Map<String, Any?> ?: return null
            return StoryMusicSelection(StoryMusicTrack.from(track), (data["start"] as? Number)?.toDouble() ?: 0.0,
                (data["duration"] as? Number)?.toDouble() ?: 15.0, data["style"] as? String ?: "card",
                data["colorHex"] as? String ?: "FFFFFF", (data["cardStyleVariant"] as? Number)?.toInt(),
                (data["rasterScale"] as? Number)?.toDouble(), data["lyricText"] as? String).clamped()
        }
    }
}

internal fun storyMusicJsonMap(value: org.json.JSONObject): Map<String, Any?> = value.keys().asSequence().associateWith { key ->
    fun decode(raw: Any?): Any? = when(raw) {
        null, org.json.JSONObject.NULL -> null
        is org.json.JSONObject -> storyMusicJsonMap(raw)
        is org.json.JSONArray -> (0 until raw.length()).map { decode(raw.opt(it)) }
        else -> raw
    }
    decode(value.opt(key))
}
