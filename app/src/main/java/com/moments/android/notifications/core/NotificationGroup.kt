package com.moments.android.notifications.core

import com.moments.android.models.MomentsNotification

/** Port de NotificationGroup.swift */
data class NotificationGroup(
    val id: String,
    val notifications: List<MomentsNotification>,
) {
    val isUnread: Boolean get() = notifications.any { it.isPending }
}
