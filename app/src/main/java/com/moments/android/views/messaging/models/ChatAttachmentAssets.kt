package com.moments.android.views.messaging.models

import android.content.Context
import com.moments.android.views.creator.components.GiphyGif
import org.json.JSONArray
import org.json.JSONObject

/** Port de `Views/Messaging/Models/ChatAttachmentAssets.swift`. */
data class ChatGiphyAsset(
    val id: String,
    val url: String,
    val width: Int = 0,
    val height: Int = 0,
) {
    companion object {
        fun from(gif: GiphyGif): ChatGiphyAsset? {
            val image = gif.images.fixedHeight
            val url = image.url.takeIf { it.isNotBlank() } ?: return null
            return ChatGiphyAsset(
                id = gif.id,
                url = url,
                width = image.width.toIntOrNull() ?: 0,
                height = image.height.toIntOrNull() ?: 0,
            )
        }
    }
}

data class ChatStickerAsset(
    val id: String,
    val url: String,
    val width: Int = 0,
    val height: Int = 0,
) {
    companion object {
        fun from(gif: GiphyGif): ChatStickerAsset? {
            val image = gif.images.fixedHeight
            val url = image.url.takeIf { it.isNotBlank() } ?: return null
            return ChatStickerAsset(
                id = gif.id,
                url = url,
                width = image.width.toIntOrNull() ?: 0,
                height = image.height.toIntOrNull() ?: 0,
            )
        }
    }
}

/** Persistencia local equivalente a `ChatRecentStickersStore`. */
object ChatRecentStickersStore {
    private const val PREFS = "chat_attachment_assets"
    private const val KEY = "chat.recentStickers.v1"
    private const val MAX_COUNT = 8

    fun load(context: Context): List<ChatStickerAsset> {
        val encoded = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)
            ?: return emptyList()
        return runCatching {
            val array = JSONArray(encoded)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id")
                    val url = item.optString("url")
                    if (id.isNotBlank() && url.isNotBlank()) {
                        add(
                            ChatStickerAsset(
                                id = id,
                                url = url,
                                width = item.optInt("width"),
                                height = item.optInt("height"),
                            ),
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun add(context: Context, sticker: ChatStickerAsset) {
        val current = buildList {
            add(sticker)
            addAll(load(context).filter { it.id != sticker.id })
        }.take(MAX_COUNT)
        val encoded = JSONArray().apply {
            current.forEach { stickerAsset ->
                put(
                    JSONObject().apply {
                        put("id", stickerAsset.id)
                        put("url", stickerAsset.url)
                        put("width", stickerAsset.width)
                        put("height", stickerAsset.height)
                    },
                )
            }
        }
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, encoded.toString())
            .apply()
    }
}

data class ChatLocationPayload(
    val lat: Double,
    val lng: Double,
    val name: String? = null,
    val address: String? = null,
) {
    fun encodedJSON(): String? = runCatching {
        JSONObject().apply {
            put("lat", lat)
            put("lng", lng)
            put("name", name)
            put("address", address)
        }.toString()
    }.getOrNull()

    companion object {
        fun decode(json: String): ChatLocationPayload? = runCatching {
            val objectValue = JSONObject(json)
            ChatLocationPayload(
                lat = objectValue.getDouble("lat"),
                lng = objectValue.getDouble("lng"),
                name = objectValue.optString("name").takeIf { it.isNotBlank() },
                address = objectValue.optString("address").takeIf { it.isNotBlank() },
            )
        }.getOrNull()
    }
}

enum class LiveLocationDuration(
    val firestoreValue: String,
    val timeIntervalMillis: Long,
    @androidx.annotation.StringRes val titleRes: Int,
) {
    FIFTEEN_MINUTES("15m", 15 * 60 * 1000L, com.moments.android.R.string.chat_location_live_15m),
    ONE_HOUR("1h", 60 * 60 * 1000L, com.moments.android.R.string.chat_location_live_1h),
    EIGHT_HOURS("8h", 8 * 60 * 60 * 1000L, com.moments.android.R.string.chat_location_live_8h);

    companion object {
        fun from(firestoreValue: String?): LiveLocationDuration? =
            entries.firstOrNull { it.firestoreValue == firestoreValue }
    }
}
