package com.moments.android.views.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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
import androidx.compose.ui.res.painterResource
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
import com.moments.android.views.messaging.components.AttachmentIconView
import com.moments.android.views.shared.ScreenshotProtectedView
import com.moments.android.views.story.StoryRingAvatarView

/** Port de `UserActivityComponents.swift`. */

@Composable
fun ActivityInteractionCategoryRow(
    category: ActivityInteractionCategory,
    summary: ActivityCategorySummary?,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.onSurface
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant

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
                    size = 24.dp,
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
                    Text(
                        text = "$count",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier
                            .clip(CircleShape)
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
            modifier = Modifier.size(16.dp),
        )
    }
}

/**
 * Celda de la tira de thumbnails. Igual que iOS: si no hay thumbnail estático pero sí vídeo, se
 * genera y cachea el frame; `canView == false` va con blur + candado, y `isProtected` envuelve en
 * la vista anti-capturas.
 */
@Composable
fun StripThumbCell(
    thumb: ThumbInfo,
    modifier: Modifier = Modifier,
) {
    val size = 52.dp
    val placeholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    var generatedThumbnail by remember(thumb.videoUrl) {
        mutableStateOf<android.graphics.Bitmap?>(null)
    }

    LaunchedEffect(thumb.videoUrl, thumb.url) {
        val videoUrl = thumb.videoUrl
        if (thumb.url.isEmpty() && videoUrl != null && generatedThumbnail == null) {
            generatedThumbnail = VideoThumbnailCache.thumbnail(videoUrl)
        }
    }

    ScreenshotProtectedView(isProtected = thumb.isProtected) {
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
 * Hoja de filtro por autor. iOS usa `.searchable`; aquí un `TextField` simple con el mismo
 * criterio de filtrado (contiene, case-insensitive, solo sobre el username conocido).
 */
@Composable
fun AuthorFilterSheet(
    selectedAuthorId: String?,
    availableAuthorIds: List<String>,
    authorUsernameMap: Map<String, String>,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchText by remember { mutableStateOf("") }
    val primary = MaterialTheme.colorScheme.onSurface

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
        TextField(
            value = searchText,
            onValueChange = { searchText = it },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.user_activity_author_search)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        LazyColumn(Modifier.fillMaxWidth()) {
            items(filteredAuthorIds, key = { it }) { authorId ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(if (selectedAuthorId == authorId) null else authorId) }
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
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}
