package com.moments.android.models

import java.util.Date

data class Visit(
    val id: String? = null,
    val visitorId: String,
    val timestamp: Date,
)

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
