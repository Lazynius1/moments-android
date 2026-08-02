package com.moments.android.views.profile.highlights

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.CircularProgressIndicator
import com.moments.android.views.components.MomentsCircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.MediaItem
import com.moments.android.models.Story
import com.moments.android.utilities.MomentsFormat
import com.moments.android.views.profile.core.ProfileColors
import java.util.Calendar
import java.util.Date

/** Port de `HighlightStoryGrid`. */
@Composable
fun HighlightStoryGrid(
    stories: List<Story>,
    selectedIds: Set<String>,
    isLoading: Boolean,
    isEmpty: Boolean,
    emptyMessage: Int = R.string.highlighted_stories_no_stories_to_select,
    onToggle: (Story) -> Unit,
    onStoryAppear: (Story) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        isLoading && isEmpty -> Column(
            modifier.fillMaxWidth().padding(top = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MomentsCircularProgressIndicator()
            Text(stringResource(R.string.common_loading), color = highlightSecondary(), fontSize = 14.sp)
        }
        isEmpty -> Column(
            modifier.fillMaxWidth().padding(top = 48.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(Icons.Filled.Archive, null, tint = Color.Gray.copy(alpha = 0.4f), modifier = Modifier.size(48.dp))
            Text(
                stringResource(emptyMessage),
                color = highlightSecondary(),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
        }
        else -> LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            items(stories, key = { it.id.orEmpty() }) { story ->
                HighlightSelectableArchiveCard(
                    story = story,
                    isSelected = story.id.orEmpty() in selectedIds,
                    onTap = { onToggle(story) },
                )
                onStoryAppear(story)
            }
        }
    }
}

/** Port de `HighlightArchiveStoryCardVisual` (9:16). */
@Composable
fun HighlightArchiveStoryCardVisual(story: Story, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(1.dp))
            .background(Color.Gray.copy(alpha = 0.22f)),
    ) {
        val url = story.mediaItem.thumbnailUrl ?: story.mediaItem.url
        if (url.isNotBlank()) {
            AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Photo, null, tint = Color.Gray.copy(alpha = 0.5f))
            }
        }
        HighlightStoryDateBadge(
            date = story.timestamp,
            modifier = Modifier.align(Alignment.TopStart).padding(7.dp),
        )
        if (story.mediaItem.type == MediaItem.MediaType.VIDEO) {
            Text(
                formatVideoDuration(story.duration),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.BottomEnd).padding(7.dp),
            )
        }
    }
}

/** Port de `HighlightStoryDateBadge`. */
@Composable
fun HighlightStoryDateBadge(date: Date, modifier: Modifier = Modifier) {
    val day = Calendar.getInstance().apply { time = date }.get(Calendar.DAY_OF_MONTH).toString()
    val month = MomentsFormat.smartDate(date, MomentsFormat.DateContext.MONTH_ABBREVIATED).lowercase()
    Column(
        modifier
            .clip(RoundedCornerShape(5.dp))
            .background(Color.White)
            .padding(horizontal = 5.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(day, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1)
        Text(month, color = Color.Black.copy(alpha = 0.75f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Port de `HighlightSelectableArchiveCard`. */
@Composable
fun HighlightSelectableArchiveCard(story: Story, isSelected: Boolean, onTap: () -> Unit) {
    Box(Modifier.clickable(onClick = onTap)) {
        HighlightArchiveStoryCardVisual(story)
        if (isSelected) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.22f)))
        }
        Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            if (isSelected) {
                Box(
                    Modifier.size(24.dp).clip(CircleShape).background(ProfileColors.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(11.dp))
                }
            } else {
                Box(
                    Modifier
                        .size(24.dp)
                        .border(2.dp, Color.White, CircleShape),
                )
            }
        }
    }
}

/** Port de `HighlightEditorBackground` — canvas sólido (sin material iOS). */
@Composable
fun HighlightEditorBackground(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .background(if (isSystemInDarkTheme()) Color(0xFF0B1215) else Color(0xFFFAF9F6)),
    )
}

/** Port de `HighlightEditorHeader`. */
@Composable
fun HighlightEditorHeader(
    title: String,
    subtitle: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .momentsChromeGlass(RoundedCornerShape(50), interactive = false)
            .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(50))
            .padding(start = 8.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Filled.Close,
            stringResource(R.string.common_close),
            tint = highlightPrimary(),
            modifier = Modifier.size(32.dp).clickable(onClick = onClose).padding(8.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = highlightPrimary(), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1)
            Text(subtitle, color = highlightSecondary(), fontSize = 11.sp, maxLines = 1)
        }
    }
}

/** Port de `HighlightViewerTitlePill`. */
@Composable
fun HighlightViewerTitlePill(title: String, modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    Text(
        title,
        color = if (dark) Color.White else Color.Black.copy(alpha = 0.88f),
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .momentsChromeGlass(RoundedCornerShape(50), interactive = false)
            .border(
                0.5.dp,
                if (dark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.12f),
                RoundedCornerShape(50),
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

/** Port de `HighlightEditorBottomBar`. */
@Composable
fun HighlightEditorBottomBar(
    title: String,
    onTitleChange: (String) -> Unit,
    coverUrl: String?,
    isSaving: Boolean,
    actionTitle: String,
    isActionEnabled: Boolean,
    onCoverTap: () -> Unit,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .momentsChromeGlass(RoundedCornerShape(24.dp), interactive = false)
            .border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onCoverTap)
                    .border(2.dp, ProfileColors.accent.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (!coverUrl.isNullOrBlank()) {
                    AsyncImage(coverUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Box(Modifier.fillMaxSize().background(Color.Gray.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Photo, null, tint = highlightSecondary())
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.highlighted_stories_title_label),
                    color = highlightSecondary(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
                BasicTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    singleLine = true,
                    textStyle = TextStyle(color = highlightPrimary(), fontSize = 15.sp, fontWeight = FontWeight.Medium),
                    cursorBrush = SolidColor(highlightPrimary()),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .momentsChromeGlass(RoundedCornerShape(14.dp), interactive = true)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    decorationBox = { inner ->
                        if (title.isEmpty()) {
                            Text(
                                stringResource(R.string.highlighted_stories_title_placeholder),
                                color = highlightSecondary(),
                                fontSize = 15.sp,
                            )
                        }
                        inner()
                    },
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(if (isActionEnabled) ProfileColors.accent else Color.Gray.copy(alpha = 0.35f))
                .clickable(enabled = isActionEnabled && !isSaving, onClick = onAction)
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (isSaving) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                Text(actionTitle, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }
    }
}

/** Port de `HighlightCoverPickerSheet` — sheet medium/large vía `MomentsModalSheet` en el call site. */
@Composable
fun HighlightCoverPickerSheet(
    stories: List<Story>,
    selectedCoverId: String?,
    onSelect: (Story) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(if (isSystemInDarkTheme()) Color(0xFF0B1215) else Color(0xFFFAF9F6)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.highlighted_stories_select_cover),
                color = highlightPrimary(),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.common_done),
                color = highlightPrimary(),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable(onClick = onDismiss).padding(8.dp),
            )
        }
        if (stories.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(stories, key = { "rail-${it.id}" }) { story ->
                    Box(Modifier.width(56.dp).clickable { onSelect(story); onDismiss() }) {
                        HighlightArchiveStoryCardVisual(story)
                        if (story.id == selectedCoverId) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                null,
                                tint = ProfileColors.accent,
                                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp),
                            )
                        }
                    }
                }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            items(stories, key = { it.id.orEmpty() }) { story ->
                Box(Modifier.clickable { onSelect(story); onDismiss() }) {
                    HighlightArchiveStoryCardVisual(story)
                    if (story.id == selectedCoverId) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            null,
                            tint = ProfileColors.accent,
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(22.dp),
                        )
                    }
                }
            }
        }
    }
}

fun formatVideoDuration(duration: Double): String {
    val total = duration.toInt().coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

@Composable
internal fun highlightPrimary() = if (isSystemInDarkTheme()) Color.White else Color.Black

@Composable
internal fun highlightSecondary() =
    if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.55f)
