package com.moments.android.views.creator.components.music

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.models.StoryMusicTrack
import com.moments.android.models.StoryMusicSelection
import com.moments.android.views.shared.MomentsModalSheet
import kotlinx.coroutines.*

@Composable
fun StoryMusicTrackSheet(track: StoryMusicTrack, onDismiss: () -> Unit) {
    val library = remember { StoryMusicLibrary() }
    val audio = rememberStoryMusicAudio()
    val scope = rememberCoroutineScope()
    var resolving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { library.load() }
    MomentsModalSheet(onDismissRequest = onDismiss, largeOnly = false) {
        Column(Modifier.fillMaxWidth().heightIn(max = 720.dp).verticalScroll(rememberScrollState()).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Row(Modifier.padding(horizontal = 24.dp).padding(top = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AsyncImage(track.artworkURL, null, Modifier.size(96.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(track.title, style = MaterialTheme.typography.titleLarge)
                    Text(track.artist, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(musicTime(track.duration), style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = {
                    if (audio.playing) audio.pause() else scope.launch {
                        resolving = true; failed = false
                        try {
                            val resolved = StoryMusicCatalog.resolve(track.id)
                            audio.load(StoryMusicSelection(resolved, duration = resolved.duration), true)
                        } catch (cancel: CancellationException) { throw cancel }
                        catch (_: Exception) { failed = true }
                        finally { resolving = false }
                    }
                }, enabled = !resolving, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Icon(if (audio.playing) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.story_music_preview))
                }
                FilledTonalIconButton(onClick = { scope.launch { library.toggle(track) } }, enabled = track.id !in library.busy, modifier = Modifier.size(48.dp)) {
                    Icon(if (library.contains(track.id)) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, stringResource(if (library.contains(track.id)) R.string.story_music_unsave else R.string.story_music_save))
                }
            }
            Text(stringResource(R.string.story_music_posts), Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.story_music_posts_placeholder), Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        repeat(3) { Box(Modifier.weight(1f).aspectRatio(3f / 4f).background(MaterialTheme.colorScheme.surfaceContainerHigh)) }
                    }
                }
            }
            if (failed || library.failed || audio.failed) Text(stringResource(R.string.story_music_unavailable_detail), style = MaterialTheme.typography.bodySmall)
        }
    }
}
