package com.moments.android.views.creator

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.AppUser
import com.moments.android.models.PhotoTag
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.searchUsers
import com.moments.android.utilities.HapticManager
import com.moments.android.views.components.UserRowSkeletonList
import com.moments.android.views.shared.MomentsSheetHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Port de `PhotoTagSelectionView.swift` — tap → TagUserSearchOverlay → PhotoTag espacial.
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
            // Sheet Android: sin chevron; título pegado al handle + Done
            MomentsSheetHeader(
                title = stringResource(R.string.creator_tag_people),
                titleSize = 18.sp,
                trailing = {
                    Text(
                        stringResource(R.string.creator_tag_done),
                        color = fg,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .momentsChromeGlass(RoundedCornerShape(percent = 50), interactive = true)
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                },
            )

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
                        .shadow(8.dp, RoundedCornerShape(12.dp), ambientColor = Color.Black.copy(0.2f))
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
                                    HapticManager.shared.lightImpact()
                                }
                            },
                    )

                    if (imageSize.width > 0 && imageSize.height > 0) {
                        tags.forEach { tag ->
                            PhotoTagView(
                                tag = tag,
                                isSelected = selectedTagId == tag.id,
                                containerSize = imageSize,
                                onTap = { selectedTagId = tag.id },
                                onDelete = {
                                    commitTags(tags.filterNot { it.id == tag.id })
                                    selectedTagId = null
                                    HapticManager.shared.mediumImpact()
                                },
                                fg = fg,
                            )
                        }

                        pendingLocation?.let { loc ->
                            PendingTagMarker(location = loc, containerSize = imageSize)
                        }
                    }
                }
            }

            Text(
                stringResource(R.string.creator_tag_instructions),
                color = fg.copy(alpha = 0.55f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 40.dp),
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
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                TagUserSearchOverlay(
                    onSelect = { user ->
                        val loc = pendingLocation
                        if (loc != null) {
                            commitTags(
                                tags + PhotoTag(
                                    userId = user.id,
                                    username = user.username,
                                    x = loc.x.toDouble(),
                                    y = loc.y.toDouble(),
                                ),
                            )
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

/** Port de `PendingTagMarker`. */
@Composable
private fun PendingTagMarker(location: Offset, containerSize: IntSize) {
    Box(
        Modifier
            .offset {
                IntOffset(
                    (location.x * containerSize.width).roundToInt() - 14,
                    (location.y * containerSize.height).roundToInt() - 14,
                )
            }
            .size(28.dp)
            .border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape)
            .shadow(6.dp, CircleShape, ambientColor = Color.Black.copy(0.2f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(Color.White))
    }
}

/** Port de `TagView` — `.position(x, y - 35)` centrado como en Swift. */
@Composable
private fun PhotoTagView(
    tag: PhotoTag,
    isSelected: Boolean,
    containerSize: IntSize,
    onTap: () -> Unit,
    onDelete: () -> Unit,
    fg: Color,
) {
    val density = LocalDensity.current
    val offsetYPx = with(density) { 35.dp.toPx() }
    var tagSize by remember { mutableStateOf(IntSize.Zero) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .onSizeChanged { tagSize = it }
            .offset {
                IntOffset(
                    (tag.x * containerSize.width).roundToInt() - tagSize.width / 2,
                    (tag.y * containerSize.height).roundToInt() - offsetYPx.roundToInt() - tagSize.height / 2,
                )
            }
            .clickable(onClick = onTap),
    ) {
        Row(
            Modifier
                .scale(if (isSelected) 1.05f else 1f)
                .momentsChromeGlass(RoundedCornerShape(12.dp), interactive = true)
                .shadow(4.dp, RoundedCornerShape(12.dp), ambientColor = Color.Black.copy(0.15f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                tag.username,
                color = fg,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (isSelected) {
                Box(
                    Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(fg.copy(alpha = 0.1f))
                        .clickable(onClick = onDelete),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Close, null, tint = fg, modifier = Modifier.size(10.dp))
                }
            }
        }
        // ≡ triangle.fill rotado 180° (puntero)
        Canvas(Modifier.size(width = 10.dp, height = 8.dp).offset(y = (-3).dp)) {
            val path = Path().apply {
                moveTo(size.width / 2f, size.height)
                lineTo(0f, 0f)
                lineTo(size.width, 0f)
                close()
            }
            drawPath(path, color = Color.Gray.copy(alpha = 0.55f))
        }
    }
}

/**
 * Port de `TagUserSearchOverlay` (no reutilizar CommentMentionSearchOverlay —
 * UI y flujo distintos: panel bottom, plus, skeleton).
 */
@Composable
private fun TagUserSearchOverlay(
    onSelect: (AppUser) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val fg = if (isDark) Color.White else Color.Black
    val focusRequester = remember { FocusRequester() }
    var searchText by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<AppUser>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    val shouldShowResultsPanel = isSearching || searchText.isNotEmpty()
    val resultsPanelHeight = (minOf(maxOf(searchResults.size, 1), 3) * 67).dp

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    LaunchedEffect(searchText) {
        val query = searchText.trim()
        if (query.isEmpty()) {
            searchResults = emptyList()
            isSearching = false
            return@LaunchedEffect
        }
        isSearching = true
        searchResults = withContext(Dispatchers.IO) {
            runCatching { FirestoreService().searchUsers(query, limit = 10) }.getOrDefault(emptyList())
        }
        isSearching = false
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .momentsChromeGlass(RoundedCornerShape(percent = 50), interactive = true)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, null, tint = fg.copy(0.55f), modifier = Modifier.size(18.dp))
            BasicTextField(
                value = searchText,
                onValueChange = { searchText = it },
                singleLine = true,
                textStyle = TextStyle(color = fg, fontSize = 16.sp),
                cursorBrush = SolidColor(fg),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp)
                    .focusRequester(focusRequester),
                decorationBox = { inner ->
                    Box {
                        if (searchText.isEmpty()) {
                            Text(
                                stringResource(R.string.creator_tag_search),
                                color = fg.copy(0.55f),
                                fontSize = 16.sp,
                            )
                        }
                        inner()
                    }
                },
            )
            if (searchText.isNotEmpty()) {
                Icon(
                    Icons.Filled.Close,
                    null,
                    tint = fg.copy(0.55f),
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { searchText = "" },
                )
                Spacer(Modifier.width(8.dp))
            }
            Box(
                Modifier
                    .size(30.dp)
                    .momentsChromeGlass(CircleShape, interactive = true)
                    .clickable(onClick = onCancel),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, null, tint = fg, modifier = Modifier.size(14.dp))
            }
        }

        if (shouldShowResultsPanel) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .momentsChromeGlass(RoundedCornerShape(24.dp), interactive = false)
                    .border(1.dp, fg.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                    .padding(vertical = 8.dp),
            ) {
                when {
                    isSearching -> {
                        UserRowSkeletonList(
                            rows = 2,
                            avatarSize = 42.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp)
                                .height(88.dp),
                        )
                    }
                    searchResults.isEmpty() -> {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 88.dp)
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                stringResource(R.string.common_no_results),
                                color = fg.copy(0.55f),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                    else -> {
                        LazyColumn(Modifier.height(resultsPanelHeight)) {
                            items(searchResults, key = { it.id }) { user ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelect(user) }
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(Color.Gray.copy(0.3f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (!user.profileImagePath.isNullOrBlank()) {
                                            AsyncImage(
                                                model = user.profileImagePath,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop,
                                            )
                                        } else {
                                            Icon(Icons.Filled.Person, null, tint = Color.Gray)
                                        }
                                    }
                                    Text(
                                        user.username,
                                        color = fg,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 12.dp),
                                    )
                                    Box(
                                        Modifier
                                            .size(28.dp)
                                            .momentsChromeGlass(CircleShape, interactive = true),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(Icons.Filled.Add, null, tint = fg, modifier = Modifier.size(14.dp))
                                    }
                                }
                                if (user.id != searchResults.lastOrNull()?.id) {
                                    HorizontalDivider(color = fg.copy(alpha = 0.25f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
