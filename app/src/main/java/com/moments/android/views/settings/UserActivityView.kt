package com.moments.android.views.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.extensions.MomentsGlassButtonPreset
import com.moments.android.views.story.ArchivedStoriesView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Port 1:1 de `UserActivityView.swift` (266 líneas).
 * 5 secciones + destinos (incl. ArchivedActivityView / RecentlyDeletedActivityView).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserActivityView(onNavigateBack: () -> Unit = {}) {
    val summaryVM = remember { ActivitySummaryViewModel() }
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    var route by remember { mutableStateOf<ActivityRoute?>(null) }

    LaunchedEffect(Unit) {
        summaryVM.load()
        summaryVM.autoRefresh()
    }

    val sections = listOf(
        R.string.user_activity_section_interactions to listOf(
            ActivityInteractionCategory.REACTIONS,
            ActivityInteractionCategory.COMMENTS,
            ActivityInteractionCategory.TAGS,
            ActivityInteractionCategory.STICKER_REPLIES,
        ),
        R.string.user_activity_section_content to listOf(
            ActivityInteractionCategory.ARCHIVED,
            ActivityInteractionCategory.STORIES_ARCHIVE,
            ActivityInteractionCategory.RECENTLY_DELETED,
        ),
        R.string.user_activity_section_shared_content to listOf(
            ActivityInteractionCategory.MOMENTS,
            ActivityInteractionCategory.REELS,
            ActivityInteractionCategory.ECHOES,
        ),
        R.string.user_activity_section_history to listOf(
            ActivityInteractionCategory.FOLLOWERS,
            ActivityInteractionCategory.VISITS,
        ),
        R.string.user_activity_section_usage to listOf(
            ActivityInteractionCategory.TIME_SPENT,
            ActivityInteractionCategory.SEARCHES,
            ActivityInteractionCategory.ACCOUNT_HISTORY,
        ),
    )

    SettingsSubsectionWrapper(
        title = stringResource(R.string.user_activity_title),
        onNavigateBack = onNavigateBack,
    ) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    summaryVM.load()
                    delay(400)
                    isRefreshing = false
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, top = 16.dp, end = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            stringResource(R.string.user_activity_headline),
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSystemInDarkTheme()) Color.White else Color.Black,
                        )
                        Text(
                            stringResource(R.string.user_activity_subtitle),
                            fontSize = 14.sp,
                            color = Color.Gray,
                        )
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(32.dp)) {
                        sections.forEach { (titleRes, categories) ->
                            ActivitySection(
                                title = stringResource(titleRes),
                                categories = categories,
                                summaries = summaryVM.summaries,
                                onOpen = { route = routeFor(it) },
                            )
                        }
                    }
                }
            }
        }
    }

    route?.let { current ->
        val close = { route = null }
        when (current) {
            is ActivityRoute.Detail -> ActivityInteractionDetailView(
                category = current.category,
                recentlyDeletedKind = current.recentlyDeletedKind,
                onBack = close,
            )
            is ActivityRoute.Archived -> ArchivedActivityView(
                onNavigateBack = close,
                initialKind = current.initialKind,
            )
            ActivityRoute.RecentlyDeleted -> RecentlyDeletedActivityView(onNavigateBack = close)
            ActivityRoute.Searches -> SearchHistoryActivityView(onNavigateBack = close)
            ActivityRoute.AccountHistory -> AccountHistoryActivityView(onNavigateBack = close)
            ActivityRoute.TimeSpent -> {
                var timeSub by remember { mutableStateOf<String?>(null) }
                when (timeSub) {
                    "daily_limit" -> DailyLimitView(onNavigateBack = { timeSub = null })
                    "rest_mode" -> RestModeView(onNavigateBack = { timeSub = null })
                    else -> TimeSpentDetailsView(
                        onNavigateBack = close,
                        onOpenDailyLimit = { timeSub = "daily_limit" },
                        onOpenRestMode = { timeSub = "rest_mode" },
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivitySection(
    title: String,
    categories: List<ActivityInteractionCategory>,
    summaries: Map<ActivityInteractionCategory, ActivityCategorySummary>,
    onOpen: (ActivityInteractionCategory) -> Unit,
) {
    val primary = if (isSystemInDarkTheme()) Color.White else Color.Black
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            title.uppercase(),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.Gray.copy(alpha = 0.8f),
            modifier = Modifier.padding(start = 4.dp),
        )
        Column {
            categories.forEachIndexed { index, category ->
                Box(Modifier.clickable { onOpen(category) }) {
                    ActivityInteractionCategoryRow(
                        category = category,
                        summary = summaries[category],
                    )
                }
                if (index < categories.lastIndex) {
                    HorizontalDivider(
                        Modifier.padding(start = 62.dp),
                        color = primary.copy(alpha = 0.08f),
                    )
                }
            }
        }
    }
}

/** ≡ iOS `RecentlyDeletedActivityView` — menú Moments / Stories. */
@Composable
fun RecentlyDeletedActivityView(onNavigateBack: () -> Unit = {}) {
    val isDark = isSystemInDarkTheme()
    val primary = if (isDark) Color.White else Color.Black
    var selectedKind by remember { mutableStateOf(RecentlyDeletedContentKind.MOMENTS) }
    var menuOpen by remember { mutableStateOf(false) }

    val momentsTitle = stringResource(R.string.profile_tab_moments)
    val storiesTitle = stringResource(R.string.notifications_tab_stories)
    val currentTitle = if (selectedKind == RecentlyDeletedContentKind.MOMENTS) momentsTitle else storiesTitle

    Column(Modifier.fillMaxSize()) {
        ActivityKindMenuBar(
            currentTitle = currentTitle,
            primary = primary,
            menuOpen = menuOpen,
            onMenuOpenChange = { menuOpen = it },
            onBack = onNavigateBack,
            momentsTitle = momentsTitle,
            storiesTitle = storiesTitle,
            momentsSelected = selectedKind == RecentlyDeletedContentKind.MOMENTS,
            onSelectMoments = { selectedKind = RecentlyDeletedContentKind.MOMENTS },
            onSelectStories = { selectedKind = RecentlyDeletedContentKind.STORIES },
        )
        Box(Modifier.fillMaxSize()) {
            key(selectedKind) {
                ActivityInteractionDetailView(
                    category = ActivityInteractionCategory.RECENTLY_DELETED,
                    recentlyDeletedKind = selectedKind,
                    suppressInlineNavigationTitle = true,
                    onBack = onNavigateBack,
                )
            }
        }
    }
}

/** ≡ iOS `ArchivedActivityView` — menú moments archivados / ArchiveView (stories). */
@Composable
fun ArchivedActivityView(
    onNavigateBack: () -> Unit = {},
    initialKind: ArchivedContentKind = ArchivedContentKind.MOMENTS,
) {
    val isDark = isSystemInDarkTheme()
    val primary = if (isDark) Color.White else Color.Black
    var selectedKind by remember { mutableStateOf(initialKind) }
    var menuOpen by remember { mutableStateOf(false) }

    val momentsTitle = stringResource(R.string.user_activity_archived_header_title)
    val storiesTitle = stringResource(R.string.archived_stories_header_title)
    val currentTitle = if (selectedKind == ArchivedContentKind.MOMENTS) momentsTitle else storiesTitle

    Column(Modifier.fillMaxSize()) {
        ActivityKindMenuBar(
            currentTitle = currentTitle,
            primary = primary,
            menuOpen = menuOpen,
            onMenuOpenChange = { menuOpen = it },
            onBack = onNavigateBack,
            momentsTitle = momentsTitle,
            storiesTitle = storiesTitle,
            momentsSelected = selectedKind == ArchivedContentKind.MOMENTS,
            onSelectMoments = { selectedKind = ArchivedContentKind.MOMENTS },
            onSelectStories = { selectedKind = ArchivedContentKind.STORIES },
        )
        Box(Modifier.fillMaxSize()) {
            key(selectedKind) {
                when (selectedKind) {
                    ArchivedContentKind.MOMENTS -> ActivityInteractionDetailView(
                        category = ActivityInteractionCategory.ARCHIVED,
                        suppressInlineNavigationTitle = true,
                        onBack = onNavigateBack,
                    )
                    ArchivedContentKind.STORIES -> ArchivedStoriesView(
                        onNavigateBack = onNavigateBack,
                        showTopBar = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivityKindMenuBar(
    currentTitle: String,
    primary: Color,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    momentsTitle: String,
    storiesTitle: String,
    momentsSelected: Boolean,
    onSelectMoments: () -> Unit,
    onSelectStories: () -> Unit,
) {
    val controlSize = MomentsGlassButtonPreset.NAVIGATION_BACK.controlSize
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsToolbarBackButton(onNavigateBack = onBack)
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Row(
                Modifier.clickable { onMenuOpenChange(true) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(currentTitle, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = primary)
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier.size(14.dp),
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenuOpenChange(false) }) {
                DropdownMenuItem(
                    text = { Text(momentsTitle) },
                    onClick = {
                        onSelectMoments()
                        onMenuOpenChange(false)
                    },
                    trailingIcon = if (momentsSelected) {
                        { Text("✓", color = primary) }
                    } else {
                        null
                    },
                )
                DropdownMenuItem(
                    text = { Text(storiesTitle) },
                    onClick = {
                        onSelectStories()
                        onMenuOpenChange(false)
                    },
                    trailingIcon = if (!momentsSelected) {
                        { Text("✓", color = primary) }
                    } else {
                        null
                    },
                )
            }
        }
        Spacer(Modifier.size(controlSize))
    }
}

private sealed interface ActivityRoute {
    data class Detail(
        val category: ActivityInteractionCategory,
        val recentlyDeletedKind: RecentlyDeletedContentKind = RecentlyDeletedContentKind.MOMENTS,
    ) : ActivityRoute

    data class Archived(val initialKind: ArchivedContentKind) : ActivityRoute
    data object RecentlyDeleted : ActivityRoute
    data object Searches : ActivityRoute
    data object AccountHistory : ActivityRoute
    data object TimeSpent : ActivityRoute
}

private fun routeFor(category: ActivityInteractionCategory): ActivityRoute = when (category) {
    ActivityInteractionCategory.ARCHIVED -> ActivityRoute.Archived(ArchivedContentKind.MOMENTS)
    ActivityInteractionCategory.STORIES_ARCHIVE -> ActivityRoute.Archived(ArchivedContentKind.STORIES)
    ActivityInteractionCategory.RECENTLY_DELETED -> ActivityRoute.RecentlyDeleted
    ActivityInteractionCategory.SEARCHES -> ActivityRoute.Searches
    ActivityInteractionCategory.ACCOUNT_HISTORY -> ActivityRoute.AccountHistory
    ActivityInteractionCategory.TIME_SPENT -> ActivityRoute.TimeSpent
    else -> ActivityRoute.Detail(category)
}

/** ≡ iOS `ActivityTimeRange` (compat). */
enum class ActivityTimeRange(val rawValue: String, val titleRes: Int) {
    WEEK("week", R.string.user_activity_range_week),
    MONTH("month", R.string.user_activity_range_month),
    YEAR("year", R.string.user_activity_range_year),
}
