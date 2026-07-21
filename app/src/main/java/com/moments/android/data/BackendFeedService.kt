package com.moments.android.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.gms.tasks.Tasks
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class FeedMediaItem(
    val id: String,
    val type: String,
    val url: String,
    val thumbnailUrl: String?,
    val aspectRatio: String?
)

data class FeedMoment(
    val id: String,
    val authorId: String,
    val username: String,
    val content: String,
    val timestamp: Long,
    val profileImagePath: String?,
    val location: String?,
    val mediaItems: List<FeedMediaItem>,
    val aspectRatio: String?,
    val commentCount: Int,
    val reactionCount: Int,
    val hideLikeCounts: Boolean,
    val disableComments: Boolean
)
data class StoryUser(val id: String, val username: String)

object BackendFeedService {
    fun fetchStoryUsers(onResult: (Result<List<StoryUser>>) -> Unit) {
        val user = FirebaseAuth.getInstance().currentUser ?: return onResult(Result.failure(IllegalStateException("No authenticated user")))
        user.getIdToken(true).addOnSuccessListener { token -> Thread {
            val result = runCatching {
                val c = (URL("https://europe-southwest1-glowsy-6a40e.cloudfunctions.net/getStoryRingPage").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"; connectTimeout = 15_000; readTimeout = 15_000
                    setRequestProperty("Content-Type", "application/json"); setRequestProperty("Authorization", "Bearer ${token.token}"); doOutput = true
                }
                try {
                    c.outputStream.use { it.write(JSONObject().put("limit", 16).toString().toByteArray()) }
                    check(c.responseCode == 200) { "Stories HTTP ${c.responseCode}" }
                    val ids = JSONObject(c.inputStream.bufferedReader().readText()).getJSONArray("items").let { json ->
                        (0 until json.length()).map { json.getJSONObject(it).getString("userId") }.distinct()
                    }
                    ids.map { id -> id to Tasks.await(FirebaseFirestore.getInstance().collection("users").document(id).get()).getString("username") }
                        .map { StoryUser(it.first, it.second ?: "moments") }
                } finally { c.disconnect() }
            }
            Handler(Looper.getMainLooper()).post { onResult(result) }
        }.start() }.addOnFailureListener { onResult(Result.failure(it)) }
    }
    fun fetch(feedType: String, onResult: (Result<List<FeedMoment>>) -> Unit) {
        val user = FirebaseAuth.getInstance().currentUser ?: return onResult(Result.failure(IllegalStateException("No authenticated user")))
        user.getIdToken(true).addOnSuccessListener { token ->
            Thread {
                val result = runCatching {
                    val connection = (URL("https://europe-southwest1-glowsy-6a40e.cloudfunctions.net/getFeedPage").openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"; connectTimeout = 15_000; readTimeout = 15_000
                        setRequestProperty("Content-Type", "application/json"); setRequestProperty("Authorization", "Bearer ${token.token}")
                        doOutput = true
                    }
                    try {
                        connection.outputStream.use { it.write(JSONObject().put("feedType", feedType).put("limit", 40).toString().toByteArray()) }
                        check(connection.responseCode == 200) { "Feed HTTP ${connection.responseCode}" }
                        val items = JSONObject(connection.inputStream.bufferedReader().readText()).getJSONArray("moments")
                        (0 until items.length()).map { index -> items.getJSONObject(index).toMoment() }
                    } finally { connection.disconnect() }
                }
                Handler(Looper.getMainLooper()).post { onResult(result) }
            }.start()
        }.addOnFailureListener { onResult(Result.failure(it)) }
    }

    private fun JSONObject.toMoment(): FeedMoment {
        val structuredMedia = optJSONArray("mediaItems")?.let { items ->
            (0 until items.length()).mapNotNull { index ->
                items.optJSONObject(index)?.let { item ->
                    item.optString("url").takeIf { it.isNotBlank() }?.let { url ->
                        FeedMediaItem(
                            id = item.optString("id", "$index-$url"),
                            type = item.optString("type", "image"),
                            url = url,
                            thumbnailUrl = item.stringOrNull("thumbnailUrl"),
                            aspectRatio = item.stringOrNull("aspectRatio")
                        )
                    }
                }
            }
        }.orEmpty()
        val fallbackMedia = listOfNotNull(
            stringOrNull("imageUrl")?.let { FeedMediaItem("legacy-image", "image", it, null, stringOrNull("aspectRatio")) },
            stringOrNull("videoUrl")?.let { FeedMediaItem("legacy-video", "video", it, stringOrNull("thumbnailUrl"), stringOrNull("aspectRatio")) }
        )
        return FeedMoment(
            id = getString("id"), authorId = getString("authorId"), username = optString("username", "moments"),
            content = optString("content"), timestamp = optLong("timestamp"),
            profileImagePath = stringOrNull("profileImagePath"), location = stringOrNull("location"),
            mediaItems = structuredMedia.ifEmpty { fallbackMedia }, aspectRatio = stringOrNull("aspectRatio"),
            commentCount = optInt("commentCount"),
            reactionCount = optJSONObject("reactions")?.let { map -> map.keys().asSequence().sumOf { map.optJSONArray(it)?.length() ?: 0 } } ?: 0,
            hideLikeCounts = optBoolean("hideLikeCounts"), disableComments = optBoolean("disableComments")
        )
    }

    private fun JSONObject.stringOrNull(name: String): String? = when (val value = opt(name)) {
        null, JSONObject.NULL -> null
        is String -> value.trim().takeUnless { it.isEmpty() || it.equals("null", ignoreCase = true) }
        else -> null
    }
}
