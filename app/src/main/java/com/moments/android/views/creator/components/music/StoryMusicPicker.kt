package com.moments.android.views.creator.components.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.models.StoryMusicSelection
import com.moments.android.models.StoryMusicTrack
import com.moments.android.views.shared.MomentsModalSheet
import kotlinx.coroutines.*

@Composable
fun StoryMusicPicker(existing: StoryMusicSelection? = null, duration: Double = 15.0, onApply: (StoryMusicSelection) -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val audio = rememberStoryMusicAudio()
    val library = remember { StoryMusicLibrary() }
    var tab by remember { mutableStateOf("songs") }
    var playlists by remember { mutableStateOf<List<StoryMusicCatalog.Playlist>>(emptyList()) }
    var playlist by remember { mutableStateOf<StoryMusicCatalog.Playlist?>(null) }
    var query by remember { mutableStateOf("") }
    var tracks by remember { mutableStateOf<List<StoryMusicTrack>>(emptyList()) }
    var cursor by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var resolving by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf(existing) }
    var revision by remember { mutableIntStateOf(0) }
    var selectedRevision by remember { mutableIntStateOf(0) }
    suspend fun load(reset: Boolean, searchQuery: String = query) {
        if (loading && !reset) return
        loading = true; failed = false
        val ticket = ++revision
        try {
            if (tab == "saved") {
                if (reset) library.load()
                if (ticket != revision) return
                cursor = null; tracks = emptyList(); return
            }
            if (tab == "discover" && playlist == null && searchQuery.isBlank()) {
                val page = StoryMusicCatalog.discover(if (reset) null else cursor)
                if (ticket != revision) return
                playlists = (if (reset) page.playlists else playlists + page.playlists).distinctBy { it.id }
                tracks = emptyList(); cursor = page.nextCursor; return
            }
            val page = if (playlist != null && searchQuery.isBlank()) StoryMusicCatalog.playlist(playlist!!.id, if (reset) null else cursor)
                else StoryMusicCatalog.search(searchQuery, if (reset) null else cursor)
            if (ticket != revision) return
            tracks = (if (reset) page.tracks else tracks + page.tracks).distinctBy { it.id }; cursor = page.nextCursor
        } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { if(ticket == revision) failed = true }
        finally { if(ticket == revision) loading = false }
    }
    suspend fun select(track: StoryMusicTrack) {
        val ticket = ++selectedRevision; resolving = true; failed = false; audio.pause()
        try {
            val resolved = StoryMusicCatalog.resolve(track.id)
            if (ticket != selectedRevision) return
            val value = (existing?.takeIf { it.track.id == resolved.id } ?: StoryMusicSelection(resolved, duration = duration)).copy(track = resolved).clamped()
            checkNotNull(resolved.previewURL)
            selection = value; audio.load(value, true)
        } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { if(ticket == selectedRevision) failed = true }
        finally { if(ticket == selectedRevision) resolving = false }
    }
    LaunchedEffect(query, tab, playlist?.id) { delay(300); cursor = null; tracks = emptyList(); load(true, query) }
    LaunchedEffect(Unit) { library.load(); existing?.let { select(it.track) } }
    MomentsModalSheet(onDismissRequest = onDismiss, largeOnly = false) {
        Box(Modifier.fillMaxWidth().heightIn(min = 360.dp, max = 680.dp)) {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text(stringResource(R.string.story_music_search)) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("songs" to R.string.story_music_tab_songs, "discover" to R.string.story_music_tab_discover, "saved" to R.string.story_music_tab_saved).forEach { (value, label) ->
                        FilterChip(selected = tab == value, onClick = { tab = value; playlist = null }, label = { Text(stringResource(label)) })
                    }
                }
                playlist?.let { value -> TextButton(onClick = { playlist = null }) { Text("‹ " + value.title) } }
                if (library.failed) TextButton(onClick = { scope.launch { library.load() } }) { Text(stringResource(R.string.story_music_retry)) }
                val visibleTracks = if (tab == "saved") library.tracks.filter { query.isBlank() || it.title.contains(query, true) || it.artist.contains(query, true) } else tracks
                LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = if(selection != null) 100.dp else 24.dp)) {
                    if (tab == "discover" && playlist == null && query.isBlank()) items(playlists, key = { "playlist-" + it.id }) { value ->
                        Row(Modifier.fillMaxWidth().clickable { playlist = value }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AsyncImage(value.artworkURL, null, Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                            Text(value.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            Text("›")
                        }
                    }
                    items(visibleTracks, key = { it.id }) { track ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if(selection?.track?.id == track.id) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface).clickable { scope.launch { select(track) } }.padding(vertical = 10.dp, horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AsyncImage(track.artworkURL, null, contentScale = ContentScale.Crop, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)))
                            Column(Modifier.weight(1f)) {
                                Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(track.artist + " · " + musicTime(track.duration), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                            IconButton(onClick = { scope.launch { library.toggle(track) } }, enabled = track.id !in library.busy) {
                                Icon(if (library.contains(track.id)) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, stringResource(if (library.contains(track.id)) R.string.story_music_unsave else R.string.story_music_save))
                            }
                            IconButton(onClick = { if(selection?.track?.id == track.id) { if(audio.playing) audio.pause() else audio.play() } else scope.launch { select(track) } }) { Icon(if(selection?.track?.id == track.id && audio.playing) Icons.Default.Pause else Icons.Default.PlayArrow, stringResource(R.string.story_music_preview)) }
                        }
                    }
                    if (!failed && (loading || cursor != null)) item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
                        LaunchedEffect(cursor) { if(cursor != null) load(false) }
                    }
                    if (failed) item { TextButton(onClick = { scope.launch { load(tracks.isEmpty()) } }) { Text(stringResource(R.string.story_music_retry)) } }
                    if(!loading && visibleTracks.isEmpty() && !failed && (tab != "discover" || query.isNotBlank() || playlist != null || playlists.isEmpty())) item { Text(stringResource(R.string.story_music_empty), Modifier.padding(16.dp)) }
                }
            }
            selection?.let { value ->
                Surface(modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), shape = RoundedCornerShape(50), tonalElevation = 6.dp, shadowElevation = 8.dp) {
                    Row(Modifier.padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(value.track.artworkURL, null, Modifier.size(42.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(value.track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(value.track.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                        IconButton(onClick = { if(audio.playing) audio.pause() else audio.play() }, enabled = !resolving && !audio.failed, modifier = Modifier.size(48.dp)) { Icon(if(audio.playing) Icons.Default.Pause else Icons.Default.PlayArrow, stringResource(R.string.story_music_preview)) }
                        FilledIconButton(onClick = { audio.pause(); onApply(value) }, enabled = !resolving && !audio.failed, colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.onSurface, contentColor = MaterialTheme.colorScheme.surface), modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.story_text_editor_done)) }
                    }
                }
            }
        }
    }
}
fun musicTime(value: Double): String = "%d:%02d".format(java.util.Locale.ROOT, value.toInt() / 60, value.toInt() % 60)
