package com.moments.android.views.creator.components.music

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
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
import com.moments.android.views.components.VerifiedBadgeView
import kotlinx.coroutines.*

@Composable
fun StoryMusicTrackSheet(track: StoryMusicTrack, originalAudio: StoryOriginalAudioSource? = null, onCreatorTap: ((String) -> Unit)? = null, onDismiss: () -> Unit) {
    val library = remember { StoryMusicLibrary() }
    val audio = rememberStoryMusicAudio()
    val scope = rememberCoroutineScope()
    var originalDetail by remember { mutableStateOf<StoryOriginalAudioDetail?>(null) }
    var originalBusy by remember { mutableStateOf(false) }
    val shownTrack = originalDetail?.track ?: track
    val isSaved = if (originalAudio == null) library.contains(track.id) else originalDetail?.saved == true
    var resolving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (originalAudio == null) library.load() else {
            try { originalDetail = StoryOriginalAudioCatalog.detail(originalAudio) }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { failed = true }
        }
    }
    MomentsModalSheet(onDismissRequest = onDismiss, largeOnly = false) {
        Column(Modifier.fillMaxWidth().heightIn(max = 720.dp).verticalScroll(rememberScrollState()).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Row(Modifier.padding(horizontal = 24.dp).padding(top = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AsyncImage(shownTrack.artworkURL, null, Modifier.size(96.dp).clip(if (originalAudio == null) RoundedCornerShape(16.dp) else CircleShape), contentScale = ContentScale.Crop)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (originalAudio == null) track.title else stringResource(R.string.story_audio_original), style = MaterialTheme.typography.titleLarge)
                    val creatorId = originalDetail?.creatorId
                    if (creatorId != null && onCreatorTap != null) {
                        Row(
                            Modifier.heightIn(min = 48.dp).clickable(role = androidx.compose.ui.semantics.Role.Button) {
                                audio.pause()
                                onCreatorTap(creatorId)
                            },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(shownTrack.artist, Modifier.weight(1f, fill = false), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            VerifiedBadgeView(userId = creatorId, size = 14.dp)
                        }
                    } else {
                        Text(shownTrack.artist, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(musicTime(shownTrack.duration), style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = {
                    if (audio.playing) audio.pause() else scope.launch {
                        resolving = true; failed = false
                        try {
                            val resolved = if (originalAudio == null) StoryMusicCatalog.resolve(track.id)
                                else StoryOriginalAudioCatalog.detail(originalAudio).also { originalDetail = it }.track
                            audio.load(StoryMusicSelection(resolved, duration = resolved.duration), true)
                        } catch (cancel: CancellationException) { throw cancel }
                        catch (_: Exception) { failed = true }
                        finally { resolving = false }
                    }
                }, enabled = !resolving, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Icon(if (audio.playing) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.story_music_preview))
                }
                FilledTonalIconButton(onClick = { scope.launch {
                    if (originalAudio == null) library.toggle(track) else originalDetail?.let { detail ->
                        originalBusy = true; failed = false
                        try {
                            StoryOriginalAudioCatalog.save(originalAudio, detail.track.id, !detail.saved)
                            originalDetail = detail.copy(saved = !detail.saved)
                            val context = com.google.firebase.FirebaseApp.getInstance().applicationContext
                            com.moments.android.notifications.services.InAppNotificationService.showActionToast(
                                com.moments.android.notifications.services.InAppActionToast.activityDone(context.getString(if (detail.saved) R.string.story_audio_unsaved_confirmation else R.string.story_audio_saved_confirmation)))
                        } catch (cancel: CancellationException) { throw cancel }
                        catch (_: Exception) { failed = true }
                        finally { originalBusy = false }
                    }
                } }, enabled = track.id !in library.busy && !originalBusy && (originalAudio == null || originalDetail?.canSave == true || isSaved), modifier = Modifier.size(48.dp)) {
                    Icon(if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, stringResource(if (isSaved) R.string.story_music_unsave else R.string.story_music_save))
                }
            }
            originalDetail?.takeIf { !it.canSave }?.let {
                Text(stringResource(if (it.reason == "audience") R.string.story_audio_restricted_audience else R.string.story_audio_restricted_creator), Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
