package com.moments.android.views.profile.highlights

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.models.Story
import com.moments.android.utilities.MomentsFormat
import com.moments.android.views.profile.core.ProfileColors
import java.text.SimpleDateFormat
import java.util.Locale

/** Port de `HighlightComponents.swift`. */
@Composable fun HighlightStoryGrid(stories: List<Story>, selectedIds: Set<String>, isLoading: Boolean, isEmpty: Boolean, emptyMessage: Int = R.string.highlight_no_stories, onToggle: (Story) -> Unit, onStoryAppear: (Story) -> Unit, modifier: Modifier = Modifier) { when { isLoading && isEmpty -> Box(modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; isEmpty -> Column(modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Filled.Archive, null, tint = Color.Gray, modifier = Modifier.size(48.dp)); Text(stringResource(emptyMessage), color = Color.Gray, modifier = Modifier.padding(top = 16.dp)) }; else -> LazyVerticalGrid(GridCells.Fixed(3), modifier, horizontalArrangement = Arrangement.spacedBy(1.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) { items(stories, key = { it.id.orEmpty() }) { story -> HighlightSelectableArchiveCard(story, story.id.orEmpty() in selectedIds, { onToggle(story) }); onStoryAppear(story) } } } }
@Composable fun HighlightArchiveStoryCardVisual(story: Story, modifier: Modifier = Modifier) = Box(modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(1.dp)).background(Color.Gray.copy(.22f))) { AsyncImage(story.mediaItem.thumbnailUrl ?: story.mediaItem.url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop); HighlightStoryDateBadge(story.timestamp, Modifier.align(Alignment.TopStart).padding(7.dp)); if (story.mediaItem.type == com.moments.android.models.MediaItem.MediaType.VIDEO) Text(formatVideoDuration(story.duration), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.BottomEnd).padding(7.dp)) }
@Composable fun HighlightStoryDateBadge(date: java.util.Date, modifier: Modifier = Modifier) = Column(modifier.clip(RoundedCornerShape(5.dp)).background(Color.White).padding(horizontal = 5.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(date.date.toString(), color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 17.sp); Text(SimpleDateFormat("MMM", Locale.getDefault()).format(date).lowercase(), color = Color.Black.copy(.75f), fontSize = 10.sp) }
@Composable fun HighlightSelectableArchiveCard(story: Story, isSelected: Boolean, onTap: () -> Unit) = Box(Modifier.clickable(onClick = onTap)) { HighlightArchiveStoryCardVisual(story); if (isSelected) Box(Modifier.fillMaxSize().background(Color.Black.copy(.22f))); Icon(if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.CheckCircle, null, tint = if (isSelected) ProfileColors.accent else Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(24.dp)) }
@Composable fun HighlightEditorBackground(modifier: Modifier = Modifier) = Box(modifier.fillMaxSize().background(if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF0B1215) else Color(0xFFFAF9F6)))
@Composable fun HighlightEditorHeader(title: String, subtitle: String, onClose: () -> Unit, modifier: Modifier = Modifier) = Row(modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(.08f)).padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Close, stringResource(R.string.common_close), tint = highlightPrimary(), modifier = Modifier.size(32.dp).clickable { onClose() }.padding(8.dp)); Column(Modifier.padding(start = 8.dp).weight(1f)) { Text(title, color = highlightPrimary(), fontWeight = FontWeight.SemiBold, fontSize = 16.sp); Text(subtitle, color = highlightSecondary(), fontSize = 11.sp) } }
@Composable fun HighlightViewerTitlePill(title: String) = Text(title, color = highlightPrimary(), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(.08f)).padding(horizontal = 16.dp, vertical = 10.dp))
@Composable fun HighlightEditorBottomBar(title: String, onTitleChange: (String) -> Unit, coverUrl: String?, isSaving: Boolean, actionTitle: Int, isActionEnabled: Boolean, onCoverTap: () -> Unit, onAction: () -> Unit, modifier: Modifier = Modifier) = Column(modifier.clip(RoundedCornerShape(24.dp)).background(Color.White.copy(.08f)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) { AsyncImage(coverUrl, null, Modifier.size(52.dp).clip(CircleShape).clickable { onCoverTap() }, contentScale = ContentScale.Crop); OutlinedTextField(title, onTitleChange, Modifier.weight(1f), label = { Text(stringResource(R.string.highlight_title_label)) }, singleLine = true) }; Text(stringResource(actionTitle), color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(if (isActionEnabled) ProfileColors.accent else Color.Gray).clickable(enabled = isActionEnabled && !isSaving) { onAction() }.padding(vertical = 14.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center); if (isSaving) CircularProgressIndicator(Modifier.size(20.dp).align(Alignment.CenterHorizontally)) }
@Composable fun HighlightCoverPickerSheet(stories: List<Story>, selectedCoverId: String?, onSelect: (Story) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) = Column(modifier.fillMaxSize().background(if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF0B1215) else Color(0xFFFAF9F6))) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.highlight_select_cover), color = highlightPrimary(), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); Icon(Icons.Filled.Close, stringResource(R.string.common_done), tint = highlightPrimary(), modifier = Modifier.size(28.dp).clickable { onDismiss() }) }; LazyVerticalGrid(GridCells.Fixed(3), Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(1.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) { items(stories, key = { it.id.orEmpty() }) { story -> Box(Modifier.clickable { onSelect(story); onDismiss() }) { HighlightArchiveStoryCardVisual(story); if (story.id == selectedCoverId) Icon(Icons.Filled.CheckCircle, null, tint = ProfileColors.accent, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) } } } }
private fun formatVideoDuration(duration: Double): String = "%d:%02d".format((duration.toInt().coerceAtLeast(0)) / 60, (duration.toInt().coerceAtLeast(0)) % 60)
@Composable internal fun highlightPrimary() = if (androidx.compose.foundation.isSystemInDarkTheme()) Color.White else Color.Black
@Composable private fun highlightSecondary() = if (androidx.compose.foundation.isSystemInDarkTheme()) Color.White.copy(.55f) else Color.Black.copy(.55f)
