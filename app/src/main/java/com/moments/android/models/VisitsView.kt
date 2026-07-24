package com.moments.android.models

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.services.content.ProfileVisitsService
import com.moments.android.utilities.MomentsFormat
import java.util.Calendar
import java.util.Date
import kotlin.math.max

/** Port de `VisitorFrequencyType` (VisitsView.swift). */
enum class VisitorFrequencyType(val rank: Int, val badge: String, @StringRes val messageRes: Int?) {
    NORMAL(0, "", null),
    FREQUENT(1, "👀", R.string.visits_badge_frequent),
    STALKER(2, "🕵️", R.string.visits_badge_interested),
    SUPER_STALKER(3, "🔍👁️", R.string.visits_badge_top_fan);

    val color: Color
        get() = when (this) {
            NORMAL -> Color.Transparent
            FREQUENT -> Color(0xFF007AFF).copy(alpha = 0.6f)
            STALKER -> Color(0xFFFF9500).copy(alpha = 0.6f)
            SUPER_STALKER -> Color(0xFFFF3B30).copy(alpha = 0.6f)
        }

    companion object {
        /** Mismos cortes que `getFrequencyType(for:)` de iOS. */
        fun forVisitsLast24h(count: Int): VisitorFrequencyType = when {
            count >= 11 -> SUPER_STALKER
            count in 6..10 -> STALKER
            count in 3..5 -> FREQUENT
            else -> NORMAL
        }
    }
}

/** Port de `VisitorAnalysis`. */
data class VisitorAnalysis(
    val userId: String,
    val username: String,
    val profileImagePath: String?,
    val totalVisits: Int,
    val visitsLast24h: Int,
    val visitsLastWeek: Int,
    val frequencyType: VisitorFrequencyType,
    val lastVisit: Date,
    val firstVisit: Date,
) {
    val daysSinceFirstVisit: Int
        get() = ((Date().time - firstVisit.time) / 86_400_000L).toInt()
}

/** Port de `VisitsViewModel.analyzeStalkers`: solo visitantes con 3+ visitas y frecuencia no normal. */
object VisitorAnalysisBuilder {
    fun analyze(groupedVisits: List<GroupedVisit>): List<VisitorAnalysis> {
        val now = Date()
        val oneDayAgo = Calendar.getInstance().apply { time = now; add(Calendar.DAY_OF_YEAR, -1) }.time
        val oneWeekAgo = Calendar.getInstance().apply { time = now; add(Calendar.DAY_OF_YEAR, -7) }.time

        return groupedVisits.mapNotNull { grouped ->
            val visits = grouped.visits
            if (visits.size < 3) return@mapNotNull null

            val sorted = visits.sortedBy { it.timestamp }
            val visitsLast24h = visits.count { !it.timestamp.before(oneDayAgo) }
            val frequencyType = VisitorFrequencyType.forVisitsLast24h(visitsLast24h)
            if (frequencyType == VisitorFrequencyType.NORMAL) return@mapNotNull null

            VisitorAnalysis(
                userId = grouped.user.id,
                username = grouped.user.username,
                profileImagePath = grouped.user.profileImagePath,
                totalVisits = visits.size,
                visitsLast24h = visitsLast24h,
                visitsLastWeek = visits.count { !it.timestamp.before(oneWeekAgo) },
                frequencyType = frequencyType,
                lastVisit = sorted.lastOrNull()?.timestamp ?: now,
                firstVisit = sorted.firstOrNull()?.timestamp ?: now,
            )
        }.sortedWith(
            compareByDescending<VisitorAnalysis> { it.frequencyType.rank }
                .thenByDescending { it.visitsLast24h },
        )
    }
}

/** Port de `VisitsView`: lista de visitas agrupadas por usuario + tarjetas de visitantes frecuentes. */
@Composable
fun VisitsView(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onUserTap: (String) -> Unit = {},
) {
    val dark = isSystemInDarkTheme()
    val canvas = if (dark) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    val primary = if (dark) Color.White else Color(0xFF0B1215)
    val secondary = if (dark) Color.White.copy(alpha = 0.6f) else Color(0xFF52626A)

    var groupedVisits by remember { mutableStateOf<List<GroupedVisit>>(emptyList()) }
    var analyses by remember { mutableStateOf<List<VisitorAnalysis>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        val grouped = runCatching { ProfileVisitsService.fetchGroupedVisits(userId) }.getOrDefault(emptyList())
        groupedVisits = grouped
        analyses = VisitorAnalysisBuilder.analyze(grouped)
        isLoading = false
    }

    Column(modifier.fillMaxSize().background(canvas)) {
        Row(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.common_close),
                tint = primary,
                modifier = Modifier.size(30.dp).clickable(onClick = onDismiss),
            )
            Text(
                stringResource(R.string.profile_header_visits),
                color = primary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.size(30.dp))
        }

        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = primary)
            }

            groupedVisits.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Filled.Visibility, null, tint = secondary, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.visits_empty_title), color = primary, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.visits_empty_description),
                    color = secondary,
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }

            else -> LazyColumn(Modifier.fillMaxSize()) {
                if (analyses.isNotEmpty()) {
                    items(analyses, key = { "stalker-${it.userId}" }) { analysis ->
                        StalkerCard(analysis = analysis, onTap = { onUserTap(analysis.userId) })
                    }
                }
                items(groupedVisits, key = { it.id }) { grouped ->
                    GroupedVisitRow(grouped = grouped, onTap = { onUserTap(grouped.user.id) })
                }
            }
        }
    }
}

/** Port de `StalkerCard`. */
@Composable
fun StalkerCard(analysis: VisitorAnalysis, onTap: () -> Unit, modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val primary = if (dark) Color.White else Color(0xFF0B1215)
    val secondary = if (dark) Color.White.copy(alpha = 0.6f) else Color(0xFF52626A)

    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(analysis.frequencyType.color.copy(alpha = 0.12f))
            .clickable(onClick = onTap)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(primary.copy(alpha = 0.1f))) {
            if (!analysis.profileImagePath.isNullOrBlank()) {
                AsyncImage(
                    model = analysis.profileImagePath,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(analysis.username, color = primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(analysis.frequencyType.badge, fontSize = 13.sp)
            }
            analysis.frequencyType.messageRes?.let {
                Text(stringResource(it), color = secondary, fontSize = 12.sp)
            }
            Text(
                stringResource(R.string.visits_stalker_alert_message, analysis.visitsLast24h),
                color = secondary,
                fontSize = 11.sp,
            )
        }
    }
}

/** Port de `GroupedVisitRow`. */
@Composable
fun GroupedVisitRow(grouped: GroupedVisit, onTap: () -> Unit, modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val primary = if (dark) Color.White else Color(0xFF0B1215)
    val secondary = if (dark) Color.White.copy(alpha = 0.6f) else Color(0xFF52626A)

    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(primary.copy(alpha = 0.1f))) {
            grouped.user.profileImagePath?.takeIf { it.isNotBlank() }?.let {
                AsyncImage(
                    model = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(grouped.user.username, color = primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(visitRowSubtitle(grouped), color = secondary, fontSize = 12.sp)
        }
        if (grouped.visitCount > 1) {
            Text(
                "×${grouped.visitCount}",
                color = secondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/** Port de `GroupedVisit.rowSubtitle`: relativo hasta una semana, luego fecha corta. */
@Composable
private fun visitRowSubtitle(grouped: GroupedVisit): String {
    val lastVisit = grouped.lastVisit ?: return ""
    val interval = (Date().time - lastVisit.time) / 1000
    return when {
        interval < 3_600 -> stringResource(R.string.visits_time_minutes_ago, max(1, (interval / 60).toInt()))
        interval < 86_400 -> stringResource(R.string.visits_time_hours_ago, max(1, (interval / 3_600).toInt()))
        interval < 604_800 -> stringResource(R.string.visits_time_days_ago, max(1, (interval / 86_400).toInt()))
        else -> MomentsFormat.smartDate(lastVisit, MomentsFormat.DateContext.NUMERIC_DAY_MONTH)
    }
}
