package com.moments.android.views.profile.core.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.Moment
import java.util.Date

/** Estado que expone `SavedMomentsViewModel` al portarse su archivo correspondiente. */
data class ProfileSavedContentState(
    val moments: List<Moment> = emptyList(),
    val isLoading: Boolean = false,
    val visibilityByMomentId: Map<String, Boolean> = emptyMap(),
    val isMomentMuted: (Moment) -> Boolean = { false },
)

enum class SavedQuickFilter(val title: Int) {
    ALL(R.string.profile_saved_filter_all), VIDEOS(R.string.profile_saved_filter_videos), TEXT(R.string.profile_saved_filter_text), LOCATION(R.string.profile_saved_filter_location);
    fun matches(moment: Moment): Boolean = when (this) {
        ALL -> true
        VIDEOS -> moment.primaryVisibleMediaItem?.type?.raw == "video" || !moment.videoUrl.isNullOrBlank()
        TEXT -> moment.content.isNotBlank()
        LOCATION -> !moment.location.isNullOrBlank()
    }
}

/** Port de `ProfileSavedContent`. El padre conserva navegación, refresh de visibilidad y eliminación. */
@Composable
fun ProfileSavedContent(
    state: ProfileSavedContentState,
    onOpenSavedManager: () -> Unit,
    onOpenDetail: (moments: List<Moment>, initialIndex: Int) -> Unit,
    onRefreshVisibility: (Moment, (Boolean) -> Unit) -> Unit,
    onRemoveMoment: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedFilter by remember { mutableStateOf(SavedQuickFilter.ALL) }
    var restrictedMoment by remember { mutableStateOf<Moment?>(null) }
    val filtered = remember(state.moments, selectedFilter) { state.moments.filter(selectedFilter::matches) }
    val preview = filtered.take(12)
    val recent = remember(state.moments) { state.moments.sortedByDescending(Moment::timestamp).take(8) }
    when {
        state.isLoading -> Column(modifier.fillMaxWidth().padding(vertical = 50.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator()
            Text(stringResource(R.string.profile_saved_loading), color = profileSecondaryColor(), fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        state.moments.isEmpty() -> ProfileSavedPlaceholder(Modifier.padding(horizontal = 20.dp))
        else -> Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SavedQuickFilter.entries.forEach { filter ->
                        val selected = filter == selectedFilter
                        Text(stringResource(filter.title), Modifier.clip(RoundedCornerShape(50)).momentsChromeGlass(RoundedCornerShape(50), interactive = true).clickable { selectedFilter = filter }.padding(horizontal = 12.dp, vertical = 8.dp), color = if (selected) profileContentColor() else profileSecondaryColor(), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(Modifier.width(10.dp))
                Row(Modifier.clip(RoundedCornerShape(50)).momentsChromeGlass(RoundedCornerShape(50), interactive = true).clickable(onClick = onOpenSavedManager).padding(horizontal = 10.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.profile_saved_open_all), color = profileContentColor(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Icon(Icons.Filled.ArrowOutward, null, tint = profileContentColor(), modifier = Modifier.size(14.dp))
                }
            }
            if (preview.isEmpty()) ProfileSavedFilteredEmptyState()
            else BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                val gap = 4.dp
                val item = (maxWidth - gap * 2) / 3
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    preview.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            row.forEachIndexed { index, moment ->
                                val restricted = moment.id?.let { state.visibilityByMomentId[it] == false } ?: true
                                ProfileSavedMomentThumbnail(moment, item, restricted, restricted && state.isMomentMuted(moment), Modifier.weight(1f)) {
                                    handleSavedMomentTap(moment, filtered, index, state, onRefreshVisibility, onOpenDetail) { restrictedMoment = it }
                                }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.size(item)) }
                        }
                    }
                }
            }
            if (recent.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.profile_saved_recent), color = profileContentColor(), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        recent.forEachIndexed { index, moment ->
                            val restricted = moment.id?.let { state.visibilityByMomentId[it] == false } ?: true
                            ProfileSavedMomentThumbnail(moment, 92.dp, restricted, restricted && state.isMomentMuted(moment)) {
                                handleSavedMomentTap(moment, recent, index, state, onRefreshVisibility, onOpenDetail) { restrictedMoment = it }
                            }
                        }
                    }
                }
            }
        }
    }
    restrictedMoment?.let { moment ->
        val muted = state.isMomentMuted(moment)
        AlertDialog(
            onDismissRequest = { restrictedMoment = null },
            title = { Text(stringResource(R.string.profile_saved_remove_title)) },
            text = { Text(stringResource(if (muted) R.string.profile_saved_remove_message_muted else R.string.profile_saved_remove_message_restricted)) },
            dismissButton = { TextButton({ restrictedMoment = null }) { Text(stringResource(R.string.profile_saved_cancel)) } },
            confirmButton = { TextButton({ moment.id?.let(onRemoveMoment); restrictedMoment = null }) { Text(stringResource(R.string.profile_saved_remove_confirm)) } },
        )
    }
}

private fun handleSavedMomentTap(moment: Moment, source: List<Moment>, fallbackIndex: Int, state: ProfileSavedContentState, refresh: (Moment, (Boolean) -> Unit) -> Unit, open: (List<Moment>, Int) -> Unit, restricted: (Moment) -> Unit) {
    val id = moment.id ?: return restricted(moment)
    val visible = state.visibilityByMomentId[id]
    if (visible == false) return restricted(moment)
    fun openVisible() {
        val accessible = source.filter { candidate -> candidate.id?.let { state.visibilityByMomentId[it] ?: true } == true }
        val resolved = accessible.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: fallbackIndex.coerceAtMost((accessible.size - 1).coerceAtLeast(0))
        if (accessible.isNotEmpty()) open(accessible, resolved)
    }
    if (visible == null) refresh(moment) { if (it) openVisible() } else openVisible()
}

/** Port de `ProfileSavedMomentThumbnail`, incluido el overlay de contenido restringido. */
@Composable
fun ProfileSavedMomentThumbnail(moment: Moment, size: androidx.compose.ui.unit.Dp, isRestricted: Boolean, isMutedRestriction: Boolean, modifier: Modifier = Modifier, onTap: () -> Unit) {
    Box(modifier.size(size).clip(RoundedCornerShape(8.dp)).clickable(onClick = onTap)) {
        Box(Modifier.matchParentSize().then(if (isRestricted) Modifier.blur(14.dp) else Modifier)) {
            val image = moment.previewImageURLString
            if (image != null) AsyncImage(profileThumbnailUrl(image), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            else Box(Modifier.matchParentSize().background(Brush.linearGradient(listOf(Color(0xFF007AFF), Color(0xFF6B73FF)))), contentAlignment = Alignment.Center) { Text(moment.content, color = Color.White, fontSize = 10.sp, textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(6.dp)) }
        }
        if (isRestricted) ProfileSavedRestrictedOverlay(isMutedRestriction, Modifier.matchParentSize())
        else {
            if (!moment.previewVideoURLString.isNullOrBlank()) Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).size(14.dp))
            if (moment.isCarouselMoment) Icon(Icons.Filled.FilterList, null, tint = Color.White, modifier = Modifier.align(Alignment.TopStart).padding(6.dp).size(16.dp))
            Icon(Icons.Filled.Bookmark, null, tint = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(16.dp).clip(CircleShape).background(Color(0xFF007AFF).copy(.8f)).padding(3.dp))
        }
    }
}

@Composable private fun ProfileSavedRestrictedOverlay(muted: Boolean, modifier: Modifier) = Column(modifier.wrapContentHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
    Icon(Icons.Filled.Lock, null, tint = Color.White, modifier = Modifier.size(14.dp))
    Text(stringResource(if (muted) R.string.profile_saved_restricted_muted_title else R.string.profile_saved_restricted_title), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2)
    Text(stringResource(if (muted) R.string.profile_saved_restricted_muted_subtitle else R.string.profile_saved_restricted_subtitle), color = Color.White.copy(.84f), fontSize = 8.sp, textAlign = TextAlign.Center, maxLines = 2)
}

@Composable private fun ProfileSavedFilteredEmptyState() = Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) { Icon(Icons.Filled.FilterList, null, tint = profileSecondaryColor(), modifier = Modifier.size(30.dp)); Text(stringResource(R.string.profile_saved_filtered_empty), color = profileSecondaryColor(), fontSize = 13.sp, fontWeight = FontWeight.Medium) }
@Composable private fun profileContentColor() = if (androidx.compose.foundation.isSystemInDarkTheme()) Color.White else Color(0xFF0B1215)
@Composable private fun profileSecondaryColor() = if (androidx.compose.foundation.isSystemInDarkTheme()) Color.White.copy(.62f) else Color(0xFF52626A)
