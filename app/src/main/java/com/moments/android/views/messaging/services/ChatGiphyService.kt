package com.moments.android.views.messaging.services

import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.views.creator.components.GiphyGif
import com.moments.android.views.creator.components.GiphyResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Port de `Views/Messaging/Services/ChatGiphyService.swift`. */
data class ChatGiphyPage(
    val items: List<GiphyGif>,
    val hasMore: Boolean,
    val nextOffset: Int,
)

object ChatGiphyService {
    enum class FunctionName(val raw: String) {
        GIFS("proxyGiphyGifs"),
        STICKERS("proxyGiphyStickers"),
    }

    enum class Mode(val raw: String) {
        TRENDING("trending"),
        SEARCH("search"),
    }

    private const val functionsRegion = "europe-southwest1"

    suspend fun fetch(
        function: FunctionName,
        mode: Mode,
        query: String? = null,
        offset: Int = 0,
        limit: Int = 24,
    ): ChatGiphyPage = withContext(Dispatchers.IO) {
        val projectId = FirebaseApp.getInstance().options.projectId ?: error("Invalid proxy URL")
        val user = FirebaseAuth.getInstance().currentUser ?: error("Not authenticated")
        val token = user.getIdToken(false).await().token ?: error("No auth token")
        val url = URL("https://$functionsRegion-$projectId.cloudfunctions.net/${function.raw}")
        val body = JSONObject()
            .put("mode", mode.raw)
            .put("limit", limit)
            .put("offset", offset.coerceAtLeast(0))
            .put("rating", "pg")
        query?.takeIf { it.isNotBlank() }?.let { body.put("query", it) }

        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
            connectTimeout = 20_000
            readTimeout = 20_000
            doOutput = true
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            if (connection.responseCode !in 200..299) error("Giphy proxy error ${connection.responseCode}")
            val decoded = GiphyResponse.fromJson(connection.inputStream.bufferedReader().readText())
            val pageOffset = decoded.pagination?.offset ?: offset
            val pageCount = decoded.pagination?.count ?: decoded.data.size
            val hasMore = decoded.pagination?.totalCount?.let { pageOffset + pageCount < it }
                ?: (decoded.data.size >= limit)
            ChatGiphyPage(decoded.data, hasMore, pageOffset + pageCount)
        } finally {
            connection.disconnect()
        }
    }
}
