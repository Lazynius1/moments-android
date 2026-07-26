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
import java.nio.charset.StandardCharsets

/**
 * Port de `ChatGiphyService.swift`.
 * GIFs/stickers vía Cloud Functions proxy (`proxyGiphyGifs` / `proxyGiphyStickers`).
 */
data class ChatGiphyPage(
    val items: List<GiphyGif>,
    val hasMore: Boolean,
    val nextOffset: Int,
)

object ChatGiphyService {
    enum class FunctionName(val rawValue: String) {
        GIFS("proxyGiphyGifs"),
        STICKERS("proxyGiphyStickers"),
    }

    enum class Mode(val rawValue: String) {
        TRENDING("trending"),
        SEARCH("search"),
    }

    private const val FUNCTIONS_REGION = "europe-southwest1"
    private const val TIMEOUT_MS = 20_000

    private fun proxyUrl(function: FunctionName): URL? {
        val projectId = FirebaseApp.getInstance().options.projectId ?: return null
        return URL("https://$FUNCTIONS_REGION-$projectId.cloudfunctions.net/${function.rawValue}")
    }

    suspend fun fetch(
        function: FunctionName,
        mode: Mode,
        query: String? = null,
        offset: Int = 0,
        limit: Int = 24,
    ): ChatGiphyPage = withContext(Dispatchers.IO) {
        val url = proxyUrl(function)
            ?: throw IllegalStateException("Invalid proxy URL")
        val user = FirebaseAuth.getInstance().currentUser
            ?: throw IllegalStateException("Not authenticated")
        val token = user.getIdToken(false).await().token
            ?: throw IllegalStateException("Not authenticated")

        val body = JSONObject()
            .put("mode", mode.rawValue)
            .put("limit", limit)
            .put("offset", maxOf(0, offset))
            .put("rating", "pg")
        // ≡ iOS `!query.isEmpty` (permite whitespace-only).
        if (query != null && query.isNotEmpty()) {
            body.put("query", query)
        }

        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doOutput = true
        }
        try {
            connection.outputStream.use { out ->
                out.write(body.toString().toByteArray(StandardCharsets.UTF_8))
            }
            // iOS no mira status HTTP; fallamos solo si el body no decodifica.
            val payload = (if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }).bufferedReader(StandardCharsets.UTF_8).use { it.readText() }

            val decoded = GiphyResponse.fromJson(payload)
            val pageOffset = decoded.pagination?.offset ?: offset
            val pageCount = decoded.pagination?.count ?: decoded.data.size
            val totalCount = decoded.pagination?.totalCount
            val hasMore = if (totalCount != null) {
                pageOffset + pageCount < totalCount
            } else {
                decoded.data.size >= limit
            }
            ChatGiphyPage(
                items = decoded.data,
                hasMore = hasMore,
                nextOffset = pageOffset + pageCount,
            )
        } finally {
            connection.disconnect()
        }
    }
}
