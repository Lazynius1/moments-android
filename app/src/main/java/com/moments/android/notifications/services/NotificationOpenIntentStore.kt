package com.moments.android.notifications.services

import com.moments.android.notifications.core.NotificationsViewModel

/**
 * Filtro de pestaña al abrir Notificaciones desde banner, push o deep link.
 * Port de NotificationOpenIntentStore.swift.
 */
object NotificationOpenIntentStore {
    @Volatile
    private var pendingFilter: String? = null

    fun enqueue(filter: String?) {
        pendingFilter = filter
    }

    fun consumeFilter(): String? {
        val filter = pendingFilter
        pendingFilter = null
        return filter
    }

    fun tab(filter: String): NotificationsViewModel.NotificationsTab? = when (filter) {
        "requests" -> NotificationsViewModel.NotificationsTab.REQUESTS
        "reactions" -> NotificationsViewModel.NotificationsTab.REACTIONS
        "comments" -> NotificationsViewModel.NotificationsTab.COMMENTS
        "stories" -> NotificationsViewModel.NotificationsTab.STORY_REACTIONS
        "follows" -> NotificationsViewModel.NotificationsTab.FOLLOWS
        else -> null
    }
}
