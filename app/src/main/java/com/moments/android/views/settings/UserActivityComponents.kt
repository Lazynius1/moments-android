package com.moments.android.views.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.services.cache.VideoThumbnailCache
import com.moments.android.views.components.EchoesIconMetrics
import com.moments.android.views.components.EchoesIconView
import com.moments.android.views.messaging.components.AttachmentIcon
import com.moments.android.views.messaging.components.AttachmentIconPreset
import com.moments.android.views.messaging.components.AttachmentIconView
import com.moments.android.views.shared.ScreenshotProtectedView
import com.moments.android.views.story.StoryRingAvatarView

/**
 * Port 1:1 de `UserActivityComponents.swift` (220 líneas).
 * Fila de categoría, `StripThumbCell`, `AuthorFilterSheet`.
 */

@Composable
fun ActivityInteractionCategoryRow(
    category: ActivityInteractionCategory,
    summary: ActivityCategorySummary?,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val primary = if (isDark) Color.White else Color.Black
    val secondary = Color.Gray

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            when (category) {
                ActivityInteractionCategory.REACTIONS -> AnimatedReactionIcon()
                ActivityInteractionCategory.COMMENTS -> AnimatedCommentIcon()
                ActivityInteractionCategory.ECHOES -> EchoesIconView(
                    size = EchoesIconMetrics.categoryRow,
                    tintColor = primary,
                )
                ActivityInteractionCategory.TAGS -> AttachmentIconView(
                    icon = AttachmentIcon.TAGGED,
                    preset = AttachmentIconPreset.ACTIVITY_CATEGORY_ROW,
                    tintColor = primary,
                )
                else -> Icon(
                    imageVector = category.icon,
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(category.titleRes),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primary,
                )

                val count = summary?.count ?: 0
                if (count > 0) {
                    // ≡ Capsule + accentColor
                    Text(
                        text = "$count",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(category.accentColor)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            Text(
                text = stringResource(category.subtitleRes),
                fontSize = 12.sp,
                color = secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = secondary.copy(alpha = 0.5f),
            modifier = Modifier.size(12.dp),
        )
    }
}

/**
 * Celda 52×52: URL / thumbnail de vídeo / placeholder; blur+candado si `!canView`;
 * `ScreenshotProtectedView` si `isProtected`.
 */
@Composable
fun StripThumbCell(
    thumb: ThumbInfo,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val size = 52.dp
    val placeholderColor = (if (isDark) Color.White else Color.Black).copy(alpha = 0.07f)
    var generatedThumbnail by remember(thumb.videoUrl) {
        mutableStateOf<android.graphics.Bitmap?>(null)
    }

    LaunchedEffect(thumb.videoUrl, thumb.url) {
        val videoUrl = thumb.videoUrl
        if (thumb.url.isEmpty() && videoUrl != null && generatedThumbnail == null) {
            generatedThumbnail = VideoThumbnailCache.thumbnail(videoUrl)
        }
    }

    ScreenshotProtectedView(isProtected = thumb.isProtected && thumb.canView) {
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
                .background(placeholderColor),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .then(if (thumb.canView) Modifier else Modifier.blur(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    thumb.url.isNotEmpty() -> AsyncImage(
                        model = thumb.url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )

                    thumb.videoUrl != null -> {
                        generatedThumbnail?.let { bitmap ->
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.PlayCircle,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            if (!thumb.canView) {
                // ≡ ultraThinMaterial overlay (canvas sólido, sin blur de sheet iOS)
                Box(
                    Modifier
                        .fillMaxSize()
                        .background((if (isDark) Color.Black else Color.White).copy(alpha = 0.35f)),
                )
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(13.dp),
                )
            }
        }
    }
}

/**
 * Filtro por autor ≡ iOS NavigationStack + searchable + close.
 * El host presenta el sheet (`MomentsModalSheet` / ModalBottomSheet).
 */
@Composable
fun AuthorFilterSheet(
    selectedAuthorId: String?,
    availableAuthorIds: List<String>,
    authorUsernameMap: Map<String, String>,
    onSelect: (String?) -> Unit,
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val primary = if (isDark) Color.White else Color.Black
    val accent = SettingsProfileColors.accent(isDark)
    var searchText by remember { mutableStateOf("") }

    val filteredAuthorIds = remember(searchText, availableAuthorIds, authorUsernameMap) {
        val term = searchText.trim().lowercase()
        if (term.isEmpty()) {
            availableAuthorIds
        } else {
            availableAuthorIds.filter { authorId ->
                authorUsernameMap[authorId]?.lowercase()?.contains(term) == true
            }
        }
    }

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.user_activity_author_sheet_title),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = primary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.user_activity_common_close),
                fontSize = 16.sp,
                color = accent,
                modifier = Modifier.clickable(onClick = onClose),
            )
        }

        SettingsSearchField(
            value = searchText,
            onValueChange = { searchText = it },
            placeholder = stringResource(R.string.user_activity_author_search),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )

        LazyColumn(Modifier.fillMaxWidth()) {
            items(filteredAuthorIds, key = { it }) { authorId ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelect(if (selectedAuthorId == authorId) null else authorId)
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StoryRingAvatarView(userId = authorId, size = 36.dp, lineWidth = 2.3.dp)

                    Text(
                        text = authorUsernameMap[authorId]
                            ?: stringResource(R.string.user_activity_status_unknown),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = primary,
                        modifier = Modifier.weight(1f),
                    )

                    if (selectedAuthorId == authorId) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = accent,
                        )
                    }
                }
            }
        }
    }
}
