package com.moments.android.views.story

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.Timestamp
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.AppUser
import com.moments.android.models.MediaItem
import com.moments.android.models.Story
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.utilities.MomentsFormat
import com.moments.android.views.story.storyviewer.GlassmorphicEmptyState
import com.moments.android.views.story.storyviewer.GlassmorphicTabSelector
import kotlin.math.max
import kotlin.random.Random
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date

/**
 * Port de `StoryStatsView` + `StoryStatsViewModel` (`archived stories.swift`).
 * Presentación: `.presentationDetents([.medium, .large])` → `MomentsModalSheet(largeOnly = false)`.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StoryStatsView(
    story: Story,
    onDismiss: () -> Unit,
    viewModel: StoryStatsViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val primaryText = if (isDark) Color.White else Color.Black.copy(0.88f)
    val secondaryText = if (isDark) Color.White.copy(0.62f) else Color.Black.copy(0.54f)

    var selectedTab by remember { mutableIntStateOf(0) }
    var viewerSearchText by remember { mutableStateOf("") }
    var reactionSearchText by remember { mutableStateOf("") }
    var reactionUsersById by remember { mutableStateOf<Map<String, AppUser>>(emptyMap()) }
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    val firestore = remember { FirestoreService() }
    val userFallback = stringResource(R.string.archived_stories_user)

    LaunchedEffect(story.id) {
        viewModel.loadStats(story)
    }

    LaunchedEffect(viewModel.reactions) {
        val missing = viewModel.reactions.map { it.userId }.toSet()
            .filter { it !in reactionUsersById }
        if (missing.isEmpty()) return@LaunchedEffect
        val users = runCatching { firestore.fetchUsers(missing) }.getOrDefault(emptyList())
        if (users.isNotEmpty()) {
            reactionUsersById = reactionUsersById + users.associateBy { it.id }
        }
    }

    LaunchedEffect(selectedTab) {
        if (pagerState.currentPage != selectedTab) {
            pagerState.animateScrollToPage(selectedTab)
        }
    }
    LaunchedEffect(pagerState.currentPage) {
        if (selectedTab != pagerState.currentPage) {
            selectedTab = pagerState.currentPage
        }
    }

    val filteredViewers = remember(viewModel.viewers, viewerSearchText) {
        val q = viewerSearchText.trim()
        if (q.isEmpty()) viewModel.viewers
        else viewModel.viewers.filter { viewer ->
            (viewer.username ?: userFallback).contains(q, ignoreCase = true)
        }
    }
    val filteredReactions = remember(viewModel.reactions, reactionSearchText, reactionUsersById) {
        val q = reactionSearchText.trim()
        if (q.isEmpty()) viewModel.reactions
        else viewModel.reactions.filter { reaction ->
            val username = reactionUsersById[reaction.userId]?.username ?: userFallback
            username.contains(q, ignoreCase = true)
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(if (isDark) Color(0xFF0B1215) else Color(0xFFFAF9F6)),
        contentAlignment = Alignment.Center,
    ) {
        if (viewModel.isLoading) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CircularProgressIndicator(color = primaryText, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                Text(
                    stringResource(R.string.archived_stories_loading_stats),
                    color = secondaryText,
                    fontSize = 14.sp,
                )
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                // ≡ statsHeader
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp)
                        .padding(top = 12.dp),
                ) {
                    Text(
                        stringResource(R.string.archived_stories_stats_title),
                        color = primaryText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 2.dp),
                    )
                }

                // ≡ storyMetaSection
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp)
                        .padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.Start,
                ) {
                    Text(
                        stringResource(
                            R.string.archived_stories_story_from,
                            MomentsFormat.smartDate(story.timestamp, MomentsFormat.DateContext.MEDIUM_DATE),
                        ),
                        color = primaryText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(
                            R.string.archived_stories_published_at,
                            MomentsFormat.smartDate(story.timestamp, MomentsFormat.DateContext.TIME_ONLY),
                        ),
                        color = secondaryText,
                        fontSize = 13.sp,
                    )
                    Text(
                        stringResource(
                            if (story.mediaItem.type == MediaItem.MediaType.VIDEO) {
                                R.string.archived_stories_video
                            } else {
                                R.string.archived_stories_photo
                            },
                        ),
                        color = secondaryText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                // ≡ statsMetricsRow
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp)
                        .padding(top = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArchivedStoryStatMetric(
                        value = "${viewModel.viewCount}",
                        title = stringResource(R.string.archived_stories_stats_views),
                        modifier = Modifier.weight(1f),
                    )
                    ArchivedMetricDivider(secondaryText, isDark)
                    ArchivedStoryStatMetric(
                        value = "${viewModel.reactionCount}",
                        title = stringResource(R.string.archived_stories_stats_reactions),
                        modifier = Modifier.weight(1f),
                    )
                    ArchivedMetricDivider(secondaryText, isDark)
                    ArchivedStoryStatMetric(
                        value = "${viewModel.shareCount}",
                        title = stringResource(R.string.archived_stories_stats_shares),
                        modifier = Modifier.weight(1f),
                    )
                    ArchivedMetricDivider(secondaryText, isDark)
                    ArchivedStoryStatMetric(
                        value = "${viewModel.reachCount}",
                        title = stringResource(R.string.archived_stories_stats_reach),
                        modifier = Modifier.weight(1f),
                    )
                }

                GlassmorphicTabSelector(
                    tabs = listOf(
                        stringResource(R.string.stories_activity_viewers_tab, viewModel.viewers.size),
                        stringResource(R.string.stories_activity_reactions_tab, viewModel.reactions.size),
                    ),
                    selectedIndex = selectedTab,
                    onSelected = { index ->
                        selectedTab = index
                        scope.launch { pagerState.animateScrollToPage(index) }
                    },
                    modifier = Modifier
                        .padding(horizontal = 22.dp)
                        .padding(top = 14.dp),
                )

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) { page ->
                    when (page) {
                        0 -> ArchivedStoryViewersTab(
                            viewers = viewModel.viewers,
                            filtered = filteredViewers,
                            searchText = viewerSearchText,
                            onSearchChange = { viewerSearchText = it },
                            primaryText = primaryText,
                            isDark = isDark,
                        )
                        else -> ArchivedStoryReactionsTab(
                            reactions = viewModel.reactions,
                            filtered = filteredReactions,
                            usersById = reactionUsersById,
                            searchText = reactionSearchText,
                            onSearchChange = { reactionSearchText = it },
                            primaryText = primaryText,
                            isDark = isDark,
                            userFallback = userFallback,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchivedMetricDivider(secondaryText: Color, isDark: Boolean) {
    Box(
        Modifier
            .width(1.dp)
            .height(28.dp)
            .background(secondaryText.copy(if (isDark) 0.18f else 0.12f)),
    )
}

@Composable
private fun ArchivedStoryStatMetric(
    value: String,
    title: String,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val primaryText = if (isDark) Color.White else Color.Black.copy(0.88f)
    val secondaryText = if (isDark) Color.White.copy(0.62f) else Color.Black.copy(0.54f)
    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            value,
            color = primaryText,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            title,
            color = secondaryText,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ArchivedStoryViewersTab(
    viewers: List<StoryViewer>,
    filtered: List<StoryViewer>,
    searchText: String,
    onSearchChange: (String) -> Unit,
    primaryText: Color,
    isDark: Boolean,
) {
    if (viewers.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            GlassmorphicEmptyState(
                icon = Icons.Filled.VisibilityOff,
                message = stringResource(R.string.archived_stories_stats_empty_viewers),
            )
        }
    } else {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                horizontal = 22.dp,
                vertical = 18.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item {
                ArchivedStatsSearchBar(
                    text = searchText,
                    onTextChange = onSearchChange,
                    primaryText = primaryText,
                    isDark = isDark,
                )
                Spacer(Modifier.height(16.dp))
            }
            if (filtered.isEmpty()) {
                item {
                    GlassmorphicEmptyState(
                        icon = Icons.Filled.Search,
                        message = stringResource(R.string.stories_activity_search_empty),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }
            } else {
                items(filtered, key = { it.id }) { viewer ->
                    ArchivedStoryViewerRow(viewer = viewer)
                }
            }
            item { Spacer(Modifier.height(28.dp)) }
        }
    }
}

@Composable
private fun ArchivedStoryReactionsTab(
    reactions: List<StoryReaction>,
    filtered: List<StoryReaction>,
    usersById: Map<String, AppUser>,
    searchText: String,
    onSearchChange: (String) -> Unit,
    primaryText: Color,
    isDark: Boolean,
    userFallback: String,
) {
    if (reactions.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            GlassmorphicEmptyState(
                icon = Icons.Filled.FavoriteBorder,
                message = stringResource(R.string.archived_stories_stats_empty_reactions),
            )
        }
    } else {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                horizontal = 22.dp,
                vertical = 18.dp,
            ),
        ) {
            item {
                ArchivedStatsSearchBar(
                    text = searchText,
                    onTextChange = onSearchChange,
                    primaryText = primaryText,
                    isDark = isDark,
                )
                Spacer(Modifier.height(16.dp))
            }
            if (filtered.isEmpty()) {
                item {
                    GlassmorphicEmptyState(
                        icon = Icons.Filled.Search,
                        message = stringResource(R.string.stories_activity_search_empty),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }
            } else {
                items(filtered, key = { it.id }) { reaction ->
                    ArchivedStoryReactionRow(
                        reaction = reaction,
                        user = usersById[reaction.userId],
                        userFallback = userFallback,
                    )
                }
            }
            item { Spacer(Modifier.height(28.dp)) }
        }
    }
}

@Composable
private fun ArchivedStatsSearchBar(
    text: String,
    onTextChange: (String) -> Unit,
    primaryText: Color,
    isDark: Boolean,
) {
    val iconTint = if (isDark) Color.White.copy(0.55f) else Color.Black.copy(0.45f)
    val clearTint = if (isDark) Color.White.copy(0.45f) else Color.Black.copy(0.35f)
    Row(
        Modifier
            .fillMaxWidth()
            .momentsChromeGlass(RoundedCornerShape(percent = 50), interactive = false)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.Search, null, tint = iconTint, modifier = Modifier.size(18.dp))
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            singleLine = true,
            cursorBrush = SolidColor(primaryText),
            textStyle = TextStyle(color = primaryText, fontSize = 14.sp),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (text.isEmpty()) {
                    Text(
                        stringResource(R.string.user_list_view_search_placeholder),
                        color = iconTint,
                        fontSize = 14.sp,
                    )
                }
                inner()
            },
        )
        if (text.isNotEmpty()) {
            IconButton(onClick = { onTextChange("") }, modifier = Modifier.size(20.dp)) {
                Icon(Icons.Filled.Close, null, tint = clearTint, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun ArchivedStoryViewerRow(viewer: StoryViewer) {
    val isDark = isSystemInDarkTheme()
    val primaryText = if (isDark) Color.White else Color.Black.copy(0.88f)
    val secondaryText = if (isDark) Color.White.copy(0.65f) else Color.Black.copy(0.52f)
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StoryRingAvatarView(userId = viewer.userId, size = 48.dp, lineWidth = 2.3.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    viewer.username ?: stringResource(R.string.archived_stories_user),
                    color = primaryText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                viewer.rewatchBadgeText?.let {
                    Text(it, color = primaryText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.weight(1f))
        }
        HorizontalDivider(color = secondaryText.copy(if (isDark) 0.18f else 0.12f))
    }
}

@Composable
private fun ArchivedStoryReactionRow(
    reaction: StoryReaction,
    user: AppUser?,
    userFallback: String,
) {
    val isDark = isSystemInDarkTheme()
    val primaryText = if (isDark) Color.White else Color.Black.copy(0.88f)
    val secondaryText = if (isDark) Color.White.copy(0.65f) else Color.Black.copy(0.52f)
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StoryRingAvatarView(
                userId = user?.id ?: reaction.userId,
                size = 48.dp,
                lineWidth = 2.3.dp,
            )
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    user?.username ?: userFallback,
                    color = primaryText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    MomentsFormat.relativeTime(reaction.timestamp),
                    color = secondaryText,
                    fontSize = 12.sp,
                )
            }
            Text(reaction.reaction, fontSize = 28.sp)
        }
        HorizontalDivider(color = secondaryText.copy(if (isDark) 0.18f else 0.12f))
    }
}

/**
 * Port de `StoryStatsViewModel`.
 * Query: `users/{authorId}/stories/{storyId}/viewers|reactions`.
 * `shareCount` ≡ iOS `Int.random(in: 0...max(1, viewCount / 10))`.
 */
class StoryStatsViewModel : ViewModel() {
    var isLoading by mutableStateOf(true)
        private set
    var viewCount by mutableIntStateOf(0)
        private set
    var reactionCount by mutableIntStateOf(0)
        private set
    var shareCount by mutableIntStateOf(0)
        private set
    var reachCount by mutableIntStateOf(0)
        private set
    var viewers by mutableStateOf<List<StoryViewer>>(emptyList())
        private set
    var reactions by mutableStateOf<List<StoryReaction>>(emptyList())
        private set

    private val firestore = FirestoreService()

    fun loadStats(story: Story) {
        val storyId = story.id ?: return
        viewModelScope.launch {
            isLoading = true
            val viewersDeferred = async {
                runCatching {
                    val snapshot = firestore.db
                        .collection("users").document(story.authorId)
                        .collection("stories").document(storyId)
                        .collection("viewers")
                        .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                        .get()
                        .await()
                    snapshot.documents.mapNotNull { doc ->
                        @Suppress("UNCHECKED_CAST")
                        StoryViewer.from(doc.id, doc.data as? Map<String, Any?> ?: return@mapNotNull null)
                    }
                }.getOrDefault(emptyList())
            }
            val reactionsDeferred = async {
                runCatching {
                    val snapshot = firestore.db
                        .collection("users").document(story.authorId)
                        .collection("stories").document(storyId)
                        .collection("reactions")
                        .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                        .get()
                        .await()
                    snapshot.documents.mapNotNull { doc ->
                        val data = doc.data ?: return@mapNotNull null
                        val userId = data["userId"] as? String ?: return@mapNotNull null
                        val reaction = data["reaction"] as? String ?: return@mapNotNull null
                        val timestamp = (data["timestamp"] as? Timestamp)?.toDate() ?: return@mapNotNull null
                        StoryReaction(id = doc.id, userId = userId, reaction = reaction, timestamp = timestamp)
                    }.latestPerUser()
                }.getOrDefault(emptyList())
            }
            val loadedViewers = viewersDeferred.await()
            val loadedReactions = reactionsDeferred.await()
            viewers = loadedViewers
            viewCount = loadedViewers.size
            reachCount = loadedViewers.map { it.userId }.toSet().size
            reactions = loadedReactions
            reactionCount = loadedReactions.size
            // ≡ Int.random(in: 0...max(1, viewCount / 10))
            shareCount = Random.nextInt(0, max(1, viewCount / 10) + 1)
            isLoading = false
        }
    }
}
