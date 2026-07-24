package com.moments.android.views.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.views.story.ArchivedStoriesView

/**
 * Port de `UserActivityView.swift`: pantalla raíz de "Tu actividad" con las 5 secciones. Cada
 * categoría empuja su detalle. Navegación interna por estado (como el resto de subpantallas de
 * Settings en el port), sin NavHost.
 *
 * Rutas especiales cableadas: `searches` → SearchHistoryActivityView; `storiesArchive` →
 * ArchivedStoriesView; el resto → [ActivityInteractionDetailView]. `timeSpent` y `accountHistory`
 * aún sin pantalla propia (se abren en el detalle, que muestra su estado vacío) — pendientes de
 * portar `TimeSpentDetailsView`/`AccountHistoryActivityView`.
 */
@Composable
fun UserActivityView(onNavigateBack: () -> Unit = {}) {
    val isDark = isSystemInDarkTheme()
    val background = if (isDark) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    val primary = if (isDark) Color.White else Color.Black

    val summaryVM = remember { ActivitySummaryViewModel() }
    LaunchedEffect(Unit) {
        summaryVM.load()
        summaryVM.autoRefresh()
    }

    var route by remember { mutableStateOf<ActivityRoute?>(null) }

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

    Box(Modifier.fillMaxSize().background(background)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        ) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ActivityHeaderBar(onBack = onNavigateBack, primary = primary)
                    Text(stringResource(R.string.user_activity_headline), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = primary)
                    Text(stringResource(R.string.user_activity_subtitle), fontSize = 14.sp, color = Color.Gray)
                }
            }

            sections.forEach { (titleRes, categories) ->
                item {
                    Text(
                        stringResource(titleRes).uppercase(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Gray.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp, start = 4.dp),
                    )
                }
                itemsIndexedCategories(categories) { index, category ->
                    Box(Modifier.clickable { route = routeFor(category) }) {
                        ActivityInteractionCategoryRow(
                            category = category,
                            summary = summaryVM.summaries[category],
                        )
                    }
                    if (index < categories.lastIndex) {
                        HorizontalDivider(Modifier.padding(start = 62.dp), color = primary.copy(alpha = 0.08f))
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
            ActivityRoute.StoriesArchive -> ArchivedStoriesView(onNavigateBack = close)
            ActivityRoute.Searches -> SearchHistoryActivityView(onNavigateBack = close)
            ActivityRoute.AccountHistory -> AccountHistoryActivityView(onNavigateBack = close)
            ActivityRoute.TimeSpent -> {
                var timeSub by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
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

private sealed interface ActivityRoute {
    data class Detail(
        val category: ActivityInteractionCategory,
        val recentlyDeletedKind: RecentlyDeletedContentKind = RecentlyDeletedContentKind.MOMENTS,
    ) : ActivityRoute
    data object StoriesArchive : ActivityRoute
    data object Searches : ActivityRoute
    data object AccountHistory : ActivityRoute
    data object TimeSpent : ActivityRoute
}

private fun routeFor(category: ActivityInteractionCategory): ActivityRoute = when (category) {
    ActivityInteractionCategory.STORIES_ARCHIVE -> ActivityRoute.StoriesArchive
    ActivityInteractionCategory.SEARCHES -> ActivityRoute.Searches
    ActivityInteractionCategory.ACCOUNT_HISTORY -> ActivityRoute.AccountHistory
    ActivityInteractionCategory.TIME_SPENT -> ActivityRoute.TimeSpent
    else -> ActivityRoute.Detail(category)
}

// Pequeño helper para iterar categorías con índice dentro de LazyColumn sin colisionar keys.
private inline fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexedCategories(
    categories: List<ActivityInteractionCategory>,
    crossinline row: @Composable (Int, ActivityInteractionCategory) -> Unit,
) {
    categories.forEachIndexed { index, category ->
        item(key = category.rawValue) { row(index, category) }
    }
}

@Composable
private fun ActivityHeaderBar(onBack: () -> Unit, primary: Color) {
    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = primary)
        }
        Text(stringResource(R.string.user_activity_title), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = primary)
    }
}
