package com.moments.android.models

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.moments.android.R
import java.util.Calendar
import java.util.Date

data class Visit(
    val id: String? = null,
    val visitorId: String,
    val timestamp: Date,
)

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
        /** ≡ iOS `getFrequencyType(for:)` / init de `GroupedVisit`. */
        fun forVisitsLast24h(count: Int): VisitorFrequencyType = when {
            count >= 11 -> SUPER_STALKER
            count in 6..10 -> STALKER
            count in 3..5 -> FREQUENT
            else -> NORMAL
        }
    }
}

data class GroupedVisit(
    val id: String,
    val user: AppUser,
    val visits: List<Visit>,
) {
    val visitCount: Int get() = visits.size
    val lastVisit: Date? get() = visits.maxOfOrNull { it.timestamp }

    /** Última visita en los últimos 30 minutos (`GroupedVisit.isRecent` iOS). */
    val isRecent: Boolean
        get() {
            val last = lastVisit ?: return false
            return Date().time - last.time <= 30L * 60L * 1000L
        }

    /** Frecuencia en las últimas 24h — mismo switch que el init iOS. */
    val frequencyType: VisitorFrequencyType
        get() {
            val oneDayAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }.time
            val recent = visits.count { !it.timestamp.before(oneDayAgo) }
            return VisitorFrequencyType.forVisitsLast24h(recent)
        }
}

object VisitGrouping {
    fun uniqueVisitorIds(from: List<Visit>): List<String> =
        from.map { it.visitorId }.distinct()

    fun build(visits: List<Visit>, users: List<AppUser>): List<GroupedVisit> {
        val byId = users.associateBy { it.id }
        return visits.groupBy { it.visitorId }.mapNotNull { (visitorId, group) ->
            val user = byId[visitorId] ?: return@mapNotNull null
            GroupedVisit(
                id = visitorId,
                user = user,
                visits = group.sortedByDescending { it.timestamp },
            )
        }.sortedByDescending { it.lastVisit ?: Date(0) }
    }
}
