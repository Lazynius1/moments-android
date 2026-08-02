package com.moments.android.views.profile.core.sections

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import com.moments.android.views.components.MomentsCircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.Moment
import com.moments.android.utilities.HapticManager
import com.moments.android.views.messaging.components.AttachmentIcon
import com.moments.android.views.messaging.components.AttachmentIconPreset
import com.moments.android.views.messaging.components.AttachmentIconView
import com.moments.android.views.messaging.components.ChatVideoPlayBadge
import com.moments.android.views.shared.ScreenshotProtectedView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ceil

/**
 * Estado que el host (ProfileShell) arma desde `SavedMomentsViewModel`.
 * ≡ campos usados por `ProfileSavedContent` iOS.
 */
data class ProfileSavedContentState(
    val moments: List<Moment> = emptyList(),
    val isLoading: Boolean = false,
    val visibilityByMomentId: Map<String, Boolean> = emptyMap(),
    val isMomentMuted: (Moment) -> Boolean = { false },
)

enum class SavedQuickFilter(val title: Int) {
    ALL(R.string.profile_saved_filter_all),
    VIDEOS(R.string.profile_saved_filter_videos),
    TEXT(R.string.profile_saved_filter_text),
    LOCATION(R.string.profile_saved_filter_location),
    ;

    fun matches(moment: Moment): Boolean = when (this) {
        ALL -> true
        VIDEOS -> moment.primaryVisibleMediaItem?.type?.raw == "video" || !moment.videoUrl.isNullOrBlank()
        TEXT -> moment.content.isNotBlank()
        LOCATION -> !moment.location.isNullOrBlank()
    }
}

/** Port de `ProfileSavedContent`. Navegación / remove / visibility viven en el host. */
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
        state.isLoading -> {
            Column(
                modifier.fillMaxWidth().padding(vertical = 50.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                MomentsCircularProgressIndicator()
                Text(
                    stringResource(R.string.profile_saved_loading),
                    color = profileSecondaryColor(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        state.moments.isEmpty() -> ProfileSavedPlaceholder(modifier.padding(horizontal = 20.dp))
        else -> Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SavedQuickFilter.entries.forEach { filter ->
                        val selected = filter == selectedFilter
                        Text(
                            stringResource(filter.title),
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
                                .clickable { selectedFilter = filter }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            color = if (selected) profileContentColor() else profileSecondaryColor(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
                        .profileMomentZoomSource(
                            sourceID = ProfileMomentZoomNavigation.profileSavedManagerZoomSourceID,
                            cornerRadius = 16.dp,
                        )
                        .clickable(onClick = onOpenSavedManager)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.profile_saved_open_all),
                        color = profileContentColor(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Icon(Icons.Filled.ArrowOutward, null, tint = profileContentColor(), modifier = Modifier.size(14.dp))
                }
            }

            if (preview.isEmpty()) {
                ProfileSavedFilteredEmptyState()
            } else {
                BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    val gap = 4.dp
                    // ≡ iOS: availableWidth − 56 outer/inner → max(88, (w − 8) / 3)
                    val item = maxOf(88.dp, (maxWidth - gap * 2) / 3)
                    val gridHeight = calculateSavedGridHeight(preview.size, item, gap)
                    Column(
                        Modifier.height(gridHeight),
                        verticalArrangement = Arrangement.spacedBy(gap),
                    ) {
                        preview.chunked(3).forEachIndexed { rowIndex, row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                                row.forEachIndexed { colIndex, moment ->
                                    val index = rowIndex * 3 + colIndex
                                    val restricted = isMomentRestricted(moment, state)
                                    ScreenshotProtectedView(
                                        isProtected = (moment.audience?.lowercase() ?: "") != "everyone",
                                    ) {
                                        ProfileSavedMomentThumbnail(
                                            moment = moment,
                                            size = item,
                                            isRestricted = restricted,
                                            isMutedRestriction = restricted && state.isMomentMuted(moment),
                                            zoomSourceID = ProfileMomentZoomNavigation.sourceID(
                                                moment,
                                                index,
                                                "saved",
                                            ),
                                            onTap = {
                                                handleSavedMomentTap(
                                                    moment = moment,
                                                    source = filtered,
                                                    fallbackIndex = index,
                                                    state = state,
                                                    refresh = onRefreshVisibility,
                                                    open = onOpenDetail,
                                                    restricted = { restrictedMoment = it },
                                                )
                                            },
                                        )
                                    }
                                }
                                repeat(3 - row.size) { Spacer(Modifier.size(item)) }
                            }
                        }
                    }
                }
            }

            if (recent.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.profile_saved_recent),
                        color = profileContentColor(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        recent.forEachIndexed { index, moment ->
                            val restricted = isMomentRestricted(moment, state)
                            ScreenshotProtectedView(
                                isProtected = (moment.audience?.lowercase() ?: "") != "everyone",
                            ) {
                                ProfileSavedMomentThumbnail(
                                    moment = moment,
                                    size = 92.dp,
                                    isRestricted = restricted,
                                    isMutedRestriction = restricted && state.isMomentMuted(moment),
                                    zoomSourceID = ProfileMomentZoomNavigation.sourceID(
                                        moment,
                                        index,
                                        "saved-recent",
                                    ),
                                    onTap = {
                                        handleSavedMomentTap(
                                            moment = moment,
                                            source = recent,
                                            fallbackIndex = index,
                                            state = state,
                                            refresh = onRefreshVisibility,
                                            open = onOpenDetail,
                                            restricted = { restrictedMoment = it },
                                        )
                                    },
                                )
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
            text = {
                Text(
                    stringResource(
                        if (muted) R.string.profile_saved_remove_message_muted
                        else R.string.profile_saved_remove_message_restricted,
                    ),
                )
            },
            dismissButton = {
                TextButton(onClick = { restrictedMoment = null }) {
                    Text(stringResource(R.string.profile_saved_cancel))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        moment.id?.let(onRemoveMoment)
                        restrictedMoment = null
                    },
                ) {
                    Text(stringResource(R.string.profile_saved_remove_confirm), color = Color.Red)
                }
            },
        )
    }
}

private fun isMomentRestricted(moment: Moment, state: ProfileSavedContentState): Boolean {
    val id = moment.id ?: return true
    return !(state.visibilityByMomentId[id] ?: true)
}

private fun calculateSavedGridHeight(itemCount: Int, item: Dp, gap: Dp): Dp {
    if (itemCount <= 0) return 0.dp
    val rows = ceil(itemCount / 3.0).toInt()
    return item * rows + gap * (rows - 1)
}

private fun handleSavedMomentTap(
    moment: Moment,
    source: List<Moment>,
    fallbackIndex: Int,
    state: ProfileSavedContentState,
    refresh: (Moment, (Boolean) -> Unit) -> Unit,
    open: (List<Moment>, Int) -> Unit,
    restricted: (Moment) -> Unit,
) {
    val id = moment.id ?: return
    val visible = state.visibilityByMomentId[id]
    if (visible == false) {
        restricted(moment)
        return
    }
    fun openVisible() {
        val accessible = source.filter { candidate ->
            val cid = candidate.id ?: return@filter false
            state.visibilityByMomentId[cid] ?: true
        }
        if (accessible.isEmpty()) return
        val resolved = accessible.indexOfFirst { it.id == id }
            .takeIf { it >= 0 }
            ?: fallbackIndex.coerceAtMost((accessible.size - 1).coerceAtLeast(0))
        open(accessible, resolved)
    }
    if (visible == null) {
        refresh(moment) { canView ->
            if (canView) openVisible() else HapticManager.shared.warning()
        }
    } else {
        openVisible()
    }
}

/** Port de `ProfileSavedMomentThumbnail`. */
@Composable
fun ProfileSavedMomentThumbnail(
    moment: Moment,
    size: Dp,
    isRestricted: Boolean,
    isMutedRestriction: Boolean,
    zoomSourceID: String? = null,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
) {
    val isVideo = moment.primaryVisibleMediaItem?.type?.raw == "video" || !moment.videoUrl.isNullOrBlank()
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .profileMomentZoomSource(zoomSourceID, cornerRadius = 8.dp)
            .clickable(onClick = onTap),
    ) {
        Box(Modifier.fillMaxSize().then(if (isRestricted) Modifier.blur(14.dp) else Modifier)) {
            ProfileSavedThumbnailMedia(moment = moment, size = size)
        }
        if (isRestricted) {
            ProfileSavedRestrictedOverlay(
                muted = isMutedRestriction,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            if (isVideo) {
                ChatVideoPlayBadge(
                    size = 14.dp,
                    padding = 8.dp,
                    modifier = Modifier.align(Alignment.BottomStart),
                )
            }
            if (moment.isCarouselMoment) {
                MomentCarouselIndicatorIcon(
                    size = 16.dp,
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                )
            }
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF007AFF).copy(alpha = 0.8f))
                    .padding(4.dp),
            ) {
                AttachmentIconView(
                    icon = AttachmentIcon.BOOKMARK,
                    preset = AttachmentIconPreset.GRID_SAVED_BADGE,
                    tintColor = Color.White,
                )
            }
        }
    }
}

@Composable
private fun ProfileSavedThumbnailMedia(moment: Moment, size: Dp) {
    val primary = moment.primaryVisibleMediaItem
    when {
        primary != null && primary.type.raw == "video" -> {
            val thumb = primary.thumbnailUrl?.takeIf { it.isNotBlank() }
            if (thumb != null) {
                AsyncImage(
                    model = profileThumbnailUrl(thumb),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                ProfileSavedVideoFrame(videoUrl = primary.url, size = size)
            }
        }
        primary != null && primary.url.isNotBlank() -> {
            AsyncImage(
                model = profileThumbnailUrl(primary.url),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        !moment.imagePath.isNullOrBlank() -> {
            AsyncImage(
                model = profileThumbnailUrl(moment.imagePath!!),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        !moment.videoUrl.isNullOrBlank() -> {
            val thumb = moment.thumbnailUrl?.takeIf { it.isNotBlank() }
                ?: moment.previewImageURLString
            if (thumb != null) {
                AsyncImage(
                    model = profileThumbnailUrl(thumb),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                ProfileSavedVideoFrame(videoUrl = moment.videoUrl!!, size = size)
            }
        }
        else -> {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF007AFF).copy(0.8f), Color(0xFF6B73FF).copy(0.6f)),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    moment.content,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(6.dp),
                )
            }
        }
    }
}

@Composable
private fun ProfileSavedVideoFrame(videoUrl: String, size: Dp) {
    var thumbnail by remember(videoUrl) { mutableStateOf<Bitmap?>(null) }
    var loading by remember(videoUrl) { mutableStateOf(true) }
    LaunchedEffect(videoUrl) {
        thumbnail = withContext(Dispatchers.IO) {
            runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(profileThumbnailUrl(videoUrl))
                    retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } finally {
                    retriever.release()
                }
            }.getOrNull()
        }
        loading = false
    }
    when {
        thumbnail != null -> {
            androidx.compose.foundation.Image(
                bitmap = thumbnail!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        else -> {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(Icons.Filled.VideoLibrary, null, tint = Color.White.copy(0.5f), modifier = Modifier.size(14.dp))
                }
            }
        }
    }
    @Suppress("UNUSED_PARAMETER")
    val unusedSize = size
}

@Composable
private fun ProfileSavedRestrictedOverlay(muted: Boolean, modifier: Modifier) {
    Box(modifier.background(Color.Black.copy(0.25f)), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.padding(6.dp),
        ) {
            Icon(Icons.Filled.Lock, null, tint = Color.White.copy(0.95f), modifier = Modifier.size(14.dp))
            Text(
                stringResource(
                    if (muted) R.string.profile_saved_restricted_muted_title
                    else R.string.profile_saved_restricted_title,
                ),
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            Text(
                stringResource(
                    if (muted) R.string.profile_saved_restricted_muted_subtitle
                    else R.string.profile_saved_restricted_subtitle,
                ),
                color = Color.White.copy(0.84f),
                fontSize = 8.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun ProfileSavedFilteredEmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 28.dp).padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Filled.FilterList, null, tint = profileSecondaryColor(), modifier = Modifier.size(30.dp))
        Text(
            stringResource(R.string.profile_saved_filtered_empty),
            color = profileSecondaryColor(),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun profileContentColor() =
    if (androidx.compose.foundation.isSystemInDarkTheme()) Color.White else Color(0xFF0B1215)

@Composable
private fun profileSecondaryColor() =
    if (androidx.compose.foundation.isSystemInDarkTheme()) Color.White.copy(0.62f) else Color(0xFF52626A)
