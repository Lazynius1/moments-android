package com.moments.android.views.creator.components.music

import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.models.StoryMusicTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object StoryMusicCatalog {
    data class Page(val tracks: List<StoryMusicTrack>, val nextCursor: String?)
    suspend fun search(query: String, cursor: String? = null): Page {
        val result = request("getStoryMusicCatalog", JSONObject().put("query", query).put("limit", 30).apply { cursor?.let { put("cursor", it) } })
        val tracks = result.getJSONArray("tracks")
        return Page((0 until tracks.length()).map { StoryMusicTrack.from(jsonMap(tracks.getJSONObject(it))) }, result.optString("nextCursor").takeIf { it.isNotBlank() && it != "null" })
    }
    data class Playlist(val id: String, val title: String, val artworkURL: String?)
    data class DiscoverPage(val playlists: List<Playlist>, val nextCursor: String?)
    private fun page(result: JSONObject): Page {
        val tracks = result.getJSONArray("tracks")
        return Page((0 until tracks.length()).map { StoryMusicTrack.from(jsonMap(tracks.getJSONObject(it))) }, result.optString("nextCursor").takeIf { it.isNotBlank() && it != "null" })
    }
    suspend fun discover(cursor: String? = null): DiscoverPage {
        val result = request("getStoryMusicDiscover", JSONObject().apply { cursor?.let { put("cursor", it) } })
        val values = result.getJSONArray("playlists")
        return DiscoverPage((0 until values.length()).map { val item = values.getJSONObject(it); Playlist(item.getString("id"), item.getString("title"), item.optString("artworkURL").takeIf { it.isNotBlank() && it != "null" }) }, result.optString("nextCursor").takeIf { it.isNotBlank() && it != "null" })
    }
    suspend fun playlist(id: String, cursor: String? = null): Page = page(request("getStoryMusicDiscover", JSONObject().put("playlistId", id).apply { cursor?.let { put("cursor", it) } }))
    suspend fun saved(): List<StoryMusicTrack> = page(request("getStoryMusicSaved", JSONObject())).tracks
    suspend fun save(id: String, saved: Boolean) { request("setStoryMusicSaved", JSONObject().put("trackId", id).put("saved", saved)) }
    suspend fun resolve(id: String, storyId: String? = null): StoryMusicTrack = StoryMusicTrack.from(jsonMap(
        request("getStoryMusicTrack", JSONObject().put("trackId", id).apply { storyId?.let { put("storyId", it) } })))
    private suspend fun request(endpoint: String, body: JSONObject): JSONObject {
        val token = checkNotNull(FirebaseAuth.getInstance().currentUser).getIdToken(false).await().token
        val project = checkNotNull(FirebaseApp.getInstance().options.projectId)
        return withContext(Dispatchers.IO) {
            val connection = URL("https://europe-southwest1-$project.cloudfunctions.net/$endpoint").openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"; connection.connectTimeout = 20000; connection.readTimeout = 20000
                connection.setRequestProperty("Authorization", "Bearer $token"); connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toString().toByteArray()) }
                check(connection.responseCode in 200..299)
                JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            } finally { connection.disconnect() }
        }
    }
    private fun jsonValue(value: Any?): Any? = when(value) {
        JSONObject.NULL, null -> null
        is JSONObject -> jsonMap(value)
        is org.json.JSONArray -> (0 until value.length()).map { jsonValue(value.opt(it)) }
        else -> value
    }
    private fun jsonMap(json: JSONObject) = json.keys().asSequence().associateWith { jsonValue(json.opt(it)) }
}
