package com.moments.android.views.creator.components.music

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.moments.android.R
import com.moments.android.models.StoryMusicTrack
import com.moments.android.models.StoryMusicSelection
import com.moments.android.views.creator.components.AudioStickerRecordingView
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class StoryOriginalAudioSource(val ownerId: String, val storyId: String, val stickerId: String) {
    fun body() = JSONObject().put("ownerId", ownerId).put("storyId", storyId).put("stickerId", stickerId)
}
data class StoryOriginalAudioDetail(val track: StoryMusicTrack, val canSave: Boolean, val reason: String?, val saved: Boolean, val creatorId: String? = null)
object StoryOriginalAudioCatalog {
    suspend fun detail(source: StoryOriginalAudioSource): StoryOriginalAudioDetail {
        val data = StoryMusicCatalog.request("getStoryOriginalAudio", source.body())
        return StoryOriginalAudioDetail(StoryMusicTrack.from(StoryMusicCatalog.jsonMap(data.getJSONObject("track"))), data.optBoolean("canSave"),
            data.optString("reason").takeIf { it.isNotBlank() && it != "null" }, data.optBoolean("saved"),
            data.optString("creatorId").takeIf { it.isNotBlank() && it != "null" })
    }
    suspend fun save(source: StoryOriginalAudioSource, id: String, saved: Boolean) {
        StoryMusicCatalog.request("setStoryOriginalAudioSaved", source.body().put("audioId", id).put("saved", saved))
    }
    suspend fun saved(cursor: String?): StoryMusicCatalog.Page {
        val data = StoryMusicCatalog.request("getStoryOriginalAudioSaved", JSONObject().apply { cursor?.let { put("cursor", it) } })
        val tracks = data.getJSONArray("tracks")
        return StoryMusicCatalog.Page((0 until tracks.length()).map { StoryMusicTrack.from(StoryMusicCatalog.jsonMap(tracks.getJSONObject(it))) },
            data.optString("nextCursor").takeIf { it.isNotBlank() && it != "null" })
    }
    suspend fun resolve(id: String): StoryMusicTrack = StoryMusicTrack.from(StoryMusicCatalog.jsonMap(
        StoryMusicCatalog.request("resolveStoryOriginalAudio", JSONObject().put("audioId", id))))
}

@Composable
fun StoryAudioPicker(onRecord: (File, Double) -> Unit, onUse: (File, Double, String) -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val audio = rememberStoryMusicAudio()
    var tab by remember { mutableIntStateOf(0) }
    var tracks by remember { mutableStateOf<List<StoryMusicTrack>>(emptyList()) }
    var cursor by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var usingId by remember { mutableStateOf<String?>(null) }
    var previewId by remember { mutableStateOf<String?>(null) }
    suspend fun load() {
        if (loading || (loaded && cursor == null)) return
        loading = true; failed = false
        try {
            val page = StoryOriginalAudioCatalog.saved(cursor)
            tracks = (tracks + page.tracks).distinctBy { it.id }; cursor = page.nextCursor; loaded = true
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { failed = true }
        finally { loading = false }
    }
    LaunchedEffect(tab) { audio.pause(); if (tab == 1 && !loaded) load() }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.story_audio_record)) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.story_audio_saved)) })
        }
        if (tab == 0) AudioStickerRecordingView(onAdd = onRecord)
        else LazyColumn(Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 480.dp), contentPadding = PaddingValues(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (tracks.isEmpty() && loaded && !failed) item { Text(stringResource(R.string.story_audio_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(tracks, key = { it.id }) { track ->
                if (track.id == tracks.lastOrNull()?.id && cursor != null) LaunchedEffect(cursor) { load() }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { scope.launch {
                        if (audio.playing && previewId == track.id) audio.pause() else {
                            try { audio.pause(); val resolved = StoryOriginalAudioCatalog.resolve(track.id); previewId = track.id; audio.load(StoryMusicSelection(resolved, duration = resolved.duration), true) }
                            catch (cancel: CancellationException) { throw cancel }
                            catch (_: Exception) { failed = true }
                        }
                    } }, enabled = usingId == null) {
                        Icon(if (audio.playing && previewId == track.id) Icons.Default.Pause else Icons.Default.PlayArrow, stringResource(R.string.story_music_preview))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.story_audio_original), style = MaterialTheme.typography.titleMedium)
                        Text(track.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(musicTime(track.duration), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { scope.launch {
                        usingId = track.id; failed = false; audio.pause()
                        var file: File? = null
                        try {
                            val resolved = StoryOriginalAudioCatalog.resolve(track.id)
                            file = withContext(Dispatchers.IO) {
                                val result = File.createTempFile("story_audio_", ".m4a", context.cacheDir)
                                val connection = URL(checkNotNull(resolved.previewURL)).openConnection() as HttpURLConnection
                                try {
                                    connection.connectTimeout = 20000; connection.readTimeout = 20000
                                    check(connection.responseCode == 200)
                                    connection.inputStream.use { input -> result.outputStream().use { output ->
                                        val buffer = ByteArray(8192); var total = 0
                                        while (true) { ensureActive(); val size = input.read(buffer); if (size < 0) break; total += size; check(total <= 12 * 1024 * 1024); output.write(buffer, 0, size) }
                                        check(total > 0)
                                    } }
                                    result
                                } catch (error: Throwable) { result.delete(); throw error }
                                finally { connection.disconnect() }
                            }
                            onUse(checkNotNull(file), resolved.duration, track.id); file = null
                        } catch (cancel: CancellationException) { throw cancel }
                        catch (_: Exception) { failed = true }
                        finally { file?.delete(); usingId = null }
                    } }, enabled = usingId == null) {
                        if (usingId == track.id) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Add, stringResource(R.string.story_audio_use))
                    }
                }
            }
            if (loading) item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) } }
            if (failed) item { Text(stringResource(R.string.story_audio_unavailable), color = MaterialTheme.colorScheme.onSurfaceVariant); TextButton(onClick = { loaded = false; cursor = null; tracks = emptyList(); scope.launch { load() } }) { Text(stringResource(R.string.story_audio_retry)) } }
        }
    }
}
