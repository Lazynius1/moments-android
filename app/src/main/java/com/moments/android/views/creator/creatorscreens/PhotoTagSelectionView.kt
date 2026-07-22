package com.moments.android.views.creator.creatorscreens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.PhotoTag
import com.moments.android.utilities.HapticManager
import com.moments.android.views.comments.CommentMentionSearchOverlay
import com.moments.android.views.creator.CreatorMedia
import kotlin.math.roundToInt

/**
 * Port de `PhotoTagSelectionView.swift` — tap → búsqueda usuario → PhotoTag espacial.
 */
@Composable
fun PhotoTagSelectionView(
    mediaItem: CreatorMedia,
    onMediaItemChange: (CreatorMedia) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val canvas = if (isDark) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    val fg = if (isDark) Color.White else Color.Black

    var tags by remember(mediaItem.id) { mutableStateOf(mediaItem.tags) }
    var pendingLocation by remember { mutableStateOf<Offset?>(null) }
    var selectedTagId by remember { mutableStateOf<String?>(null) }
    var showingSearch by remember { mutableStateOf(false) }
    var imageSize by remember { mutableStateOf(IntSize.Zero) }

    fun commitTags(next: List<PhotoTag>) {
        tags = next
        onMediaItemChange(mediaItem.copy(tags = next))
    }

    Box(modifier.fillMaxSize().background(canvas)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .momentsChromeGlass(CircleShape, interactive = true)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.KeyboardArrowDown, null, tint = fg, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.creator_tag_people),
                    color = fg,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                )
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        stringResource(R.string.common_done),
                        color = fg,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    )
                }
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .onSizeChanged { imageSize = it },
                ) {
                    AsyncImage(
                        model = mediaItem.uri,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    val w = size.width.toFloat().coerceAtLeast(1f)
                                    val h = size.height.toFloat().coerceAtLeast(1f)
                                    pendingLocation = Offset(
                                        (offset.x / w).coerceIn(0f, 1f),
                                        (offset.y / h).coerceIn(0f, 1f),
                                    )
                                    showingSearch = true
                                    selectedTagId = null
                                }
                            },
                    )

                    if (imageSize.width > 0 && imageSize.height > 0) {
                        tags.forEach { tag ->
                            val selected = selectedTagId == tag.id
                            Box(
                                Modifier
                                    .offset {
                                        IntOffset(
                                            (tag.x * imageSize.width).roundToInt() - 40,
                                            (tag.y * imageSize.height).roundToInt() - 16,
                                        )
                                    }
                                    .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
                                    .border(
                                        1.dp,
                                        if (selected) Color(0xFFE91E63) else Color.White.copy(0.2f),
                                        RoundedCornerShape(50),
                                    )
                                    .clickable { selectedTagId = tag.id }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "@${tag.username}",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    if (selected) {
                                        Icon(
                                            Icons.Filled.Close,
                                            null,
                                            tint = Color.White,
                                            modifier = Modifier
                                                .padding(start = 6.dp)
                                                .size(14.dp)
                                                .clickable {
                                                    commitTags(tags.filterNot { it.id == tag.id })
                                                    selectedTagId = null
                                                    HapticManager.shared.warning()
                                                },
                                        )
                                    }
                                }
                            }
                        }

                        pendingLocation?.let { loc ->
                            Box(
                                Modifier
                                    .offset {
                                        IntOffset(
                                            (loc.x * imageSize.width).roundToInt() - 8,
                                            (loc.y * imageSize.height).roundToInt() - 8,
                                        )
                                    }
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE91E63)),
                            )
                        }
                    }
                }
            }

            Text(
                stringResource(R.string.creator_tag_instructions),
                color = fg.copy(0.55f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 32.dp),
            )
        }

        if (showingSearch) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(if (isDark) 0.18f else 0.08f))
                    .clickable {
                        showingSearch = false
                        pendingLocation = null
                    },
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                CommentMentionSearchOverlay(
                    onSelect = { user ->
                        val loc = pendingLocation
                        if (loc != null) {
                            val tag = PhotoTag(
                                userId = user.id,
                                username = user.username,
                                x = loc.x.toDouble(),
                                y = loc.y.toDouble(),
                            )
                            commitTags(tags + tag)
                            HapticManager.shared.success()
                        }
                        showingSearch = false
                        pendingLocation = null
                    },
                    onCancel = {
                        showingSearch = false
                        pendingLocation = null
                    },
                )
            }
        }
    }
}
