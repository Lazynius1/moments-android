package com.moments.android.views.creator.components.music

import androidx.compose.runtime.*
import com.google.firebase.FirebaseApp
import com.moments.android.R
import com.moments.android.notifications.services.InAppActionToast
import com.moments.android.notifications.services.InAppNotificationService
import com.moments.android.models.StoryMusicTrack
import kotlinx.coroutines.CancellationException

@Stable
class StoryMusicLibrary {
    var tracks by mutableStateOf<List<StoryMusicTrack>>(emptyList())
        private set
    var busy by mutableStateOf<Set<String>>(emptySet())
        private set
    var failed by mutableStateOf(false)
        private set
    fun contains(id: String) = tracks.any { it.id == id }
    suspend fun load() {
        try { tracks = StoryMusicCatalog.saved(); failed = false }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { failed = true }
    }
    suspend fun toggle(track: StoryMusicTrack) {
        if (track.id in busy) return
        busy = busy + track.id
        try {
            val saved = !contains(track.id)
            StoryMusicCatalog.save(track.id, saved)
            tracks = if (saved) listOf(track) + tracks.filter { it.id != track.id } else tracks.filter { it.id != track.id }
            failed = false
            val context = FirebaseApp.getInstance().applicationContext
            InAppNotificationService.showActionToast(InAppActionToast.activityDone(
                context.getString(if (saved) R.string.story_music_saved_confirmation else R.string.story_music_unsaved_confirmation),
            ))
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { failed = true }
        finally { busy = busy - track.id }
    }
}
