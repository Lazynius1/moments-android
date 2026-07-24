package com.moments.android.views.story

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.models.MediaItem
import com.moments.android.models.Story
import com.moments.android.services.social.ChainStats
import com.moments.android.services.social.StoryChainLimits
import com.moments.android.services.social.StoryChainLimitsService
import com.moments.android.services.social.formattedRemainingTime
import com.moments.android.services.firestore.FirestoreService
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Port de `StoryChainView.swift`: rejilla de la cadena, stats y continuación. */
@Composable
fun StoryChainView(
    chainId: String,
    chainTitle: String,
    canContinueChain: Boolean,
    onDismiss: () -> Unit,
    onOpenStory: (List<Story>, Int) -> Unit,
    onContinueChain: (String, String, Int) -> Unit,
    initialStoryId: String? = null,
    initialChainPosition: Int? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val background = if (isDark) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    val primary = if (isDark) Color.White else Color.Black
    val secondary = if (isDark) Color.White.copy(alpha = 0.68f) else Color.Black.copy(alpha = 0.62f)

    var isLoading by remember(chainId) { mutableStateOf(true) }
    var stories by remember(chainId) { mutableStateOf<List<Story>>(emptyList()) }
    var selectedIndex by remember(chainId) { mutableIntStateOf(0) }
    var didApplyInitialSelection by remember(chainId) { mutableStateOf(false) }
    var chainStats by remember(chainId) { mutableStateOf(ChainStats(0, 0.0, false)) }
    var limitAlertMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(chainId) {
        isLoading = true
        stories = loadChainStories(chainId)
        isLoading = false
        chainStats = runCatching { StoryChainLimitsService.getChainStats(chainId) }
            .getOrDefault(ChainStats(stories.size, 0.0, false))
    }

    LaunchedEffect(stories) {
        if (didApplyInitialSelection || stories.isEmpty()) return@LaunchedEffect
        val resolved = when {
            initialStoryId != null && stories.indexOfFirst { it.id == initialStoryId } >= 0 ->
                stories.indexOfFirst { it.id == initialStoryId }
            initialChainPosition != null && stories.indexOfFirst { it.chainPosition == initialChainPosition } >= 0 ->
                stories.indexOfFirst { it.chainPosition == initialChainPosition }
            else -> 0
        }
        selectedIndex = resolved.coerceIn(0, stories.size - 1)
        didApplyInitialSelection = true
    }

    Box(modifier.fillMaxSize().background(background)) {
        when {
            isLoading -> {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(color = primary)
                    Spacer(Modifier.height(18.dp))
                    Text(stringResource(R.string.story_chains_loading), color = secondary, fontSize = 16.sp)
                }
            }

            stories.isEmpty() -> {
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CloseChip(primary, onDismiss)
                    Spacer(Modifier.height(18.dp))
                    Icon(Icons.Filled.LinkOff, contentDescription = null, tint = primary, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.story_chains_not_found),
                        color = primary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            else -> {
                Column(Modifier.fillMaxSize()) {
                    ChainHeader(
                        chainTitle = chainTitle,
                        stats = chainStats,
                        storyCount = stories.size,
                        selectedIndex = selectedIndex,
                        primary = primary,
                        secondary = secondary,
                        isDark = isDark,
                        onDismiss = onDismiss,
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            top = 18.dp,
                            bottom = if (canContinueChain) 92.dp else 28.dp,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        itemsIndexed(stories, key = { index, story -> story.id ?: index.toString() }) { index, story ->
                            StoryChainGridItemView(
                                story = story,
                                position = story.chainPosition ?: (index + 1),
                                isSelected = selectedIndex == index,
                                isDark = isDark,
                                onTap = {
                                    selectedIndex = index
                                    onOpenStory(stories, index)
                                },
                            )
                        }
                    }
                }

                if (canContinueChain) {
                    Row(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 20.dp, vertical = 22.dp)
                            .background(if (isDark) Color(0xFFFAF9F6) else Color(0xFF0B1215), RoundedCornerShape(50))
                            .clickable {
                                val userId = FirebaseAuth.getInstance().currentUser?.uid
                                if (userId == null) {
                                    limitAlertMessage = context.getString(R.string.story_chains_error_user_not_authorized)
                                } else {
                                    limitAlertMessage = null
                                    scope.launch {
                                        runCatching { StoryChainLimitsService.canContinueChain(chainId, userId) }
                                            .onSuccess {
                                                onDismiss()
                                                onContinueChain(chainId, chainTitle, stories.size + 1)
                                            }
                                            .onFailure { error ->
                                                limitAlertMessage = context.getString(
                                                    R.string.story_chains_error_validation,
                                                    error.localizedMessage ?: error.toString(),
                                                )
                                            }
                                    }
                                }
                            }
                            .padding(horizontal = 24.dp, vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = null,
                            tint = if (isDark) Color(0xFF0B1215) else Color(0xFFFAF9F6),
                            modifier = Modifier.size(17.dp),
                        )
                        Text(
                            stringResource(R.string.story_chains_continue_story),
                            color = if (isDark) Color(0xFF0B1215) else Color(0xFFFAF9F6),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }

    limitAlertMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { limitAlertMessage = null },
            title = { Text(stringResource(R.string.story_chains_chain_limit)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { limitAlertMessage = null }) {
                    Text(stringResource(R.string.story_chains_ok))
                }
            },
        )
    }
}

@Composable
private fun ChainHeader(
    chainTitle: String,
    stats: ChainStats,
    storyCount: Int,
    selectedIndex: Int,
    primary: Color,
    secondary: Color,
    isDark: Boolean,
    onDismiss: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 14.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CloseChip(primary, onDismiss)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.story_chains_chain), color = secondary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    chainTitle,
                    color = primary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.size(38.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val chipTint = if (isDark) Color.White.copy(alpha = 0.86f) else Color.Black.copy(alpha = 0.76f)
            InfoChip(
                icon = Icons.Filled.Add,
                text = stringResource(R.string.story_chains_parts, stats.partCount, StoryChainLimits.MAX_PARTS),
                tint = chipTint,
                isDark = isDark,
            )
            if (stats.isExpired) {
                InfoChip(
                    icon = Icons.Filled.Close,
                    text = stringResource(R.string.story_chains_expired),
                    tint = Color.Red,
                    isDark = isDark,
                )
            } else {
                InfoChip(
                    icon = Icons.Filled.Schedule,
                    text = stats.remainingTimeSeconds.formattedRemainingTime(LocalContext.current),
                    tint = if (stats.remainingTimeSeconds < 3600) Color.Red else chipTint,
                    isDark = isDark,
                )
            }
        }

        if (storyCount > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(storyCount) { index ->
                    val brush = if (index <= selectedIndex) {
                        Brush.horizontalGradient(listOf(Color(0xFF007AFF), Color(0xFFAF52DE), Color(0xFFFF2D55)))
                    } else {
                        Brush.horizontalGradient(
                            listOf(
                                if (isDark) Color.White.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.16f),
                                if (isDark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.10f),
                            ),
                        )
                    }
                    Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(50)).background(brush))
                }
            }
        }
    }
}

@Composable
private fun CloseChip(tint: Color, onDismiss: () -> Unit) {
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.08f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Close, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun InfoChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    tint: Color,
    isDark: Boolean,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(11.dp))
        Text(text, color = tint, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** Port de `StoryChainGridItemView`: miniatura 9:16 con username, avatar y número de parte. */
@Composable
private fun StoryChainGridItemView(
    story: Story,
    position: Int,
    isSelected: Boolean,
    isDark: Boolean,
    onTap: () -> Unit,
) {
    val thumbnail = if (story.mediaItem.type == MediaItem.MediaType.VIDEO) {
        story.mediaItem.thumbnailUrl ?: story.mediaItem.url
    } else {
        story.mediaItem.url
    }
    val borderBrush = if (isSelected) {
        Brush.linearGradient(listOf(Color(0xFF007AFF), Color(0xFFAF52DE), Color(0xFFFF2D55)))
    } else {
        Brush.linearGradient(
            listOf(
                if (isDark) Color.White.copy(alpha = 0.24f) else Color.Black.copy(alpha = 0.2f),
                if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.12f),
            ),
        )
    }

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f))
            .clickable(onClick = onTap),
    ) {
        AsyncImage(
            model = thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)))),
        )

        Row(
            Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                story.username,
                color = Color.White.copy(alpha = 0.96f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.34f))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            )

            Box(Modifier.size(30.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                if (!story.profileImagePath.isNullOrEmpty()) {
                    AsyncImage(
                        model = story.profileImagePath,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(Color.Gray.copy(alpha = 0.7f), Color.Black.copy(alpha = 0.8f)),
                                ),
                            ),
                    )
                }
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
                Text("$position", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .border(
                    width = if (isSelected) 1.6.dp else 0.8.dp,
                    brush = borderBrush,
                    shape = RoundedCornerShape(14.dp),
                ),
        )
    }
}

/** Equivalente al `collectionGroup("stories").whereField("chainId")` de `StoryChainViewModel`. */
private suspend fun loadChainStories(chainId: String): List<Story> = runCatching {
    val snapshot = FirestoreService().db.collectionGroup("stories")
        .whereEqualTo("chainId", chainId)
        .orderBy("chainPosition")
        .get()
        .await()
    snapshot.documents.mapNotNull { doc ->
        @Suppress("UNCHECKED_CAST")
        Story.from(doc.id, doc.data as? Map<String, Any?> ?: return@mapNotNull null)
    }
}.getOrDefault(emptyList())

