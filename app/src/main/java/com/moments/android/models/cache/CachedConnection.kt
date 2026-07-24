package com.moments.android.models.cache

import java.util.Date

// Entidad de caché local (SwiftData @Model en iOS → Room en Android).

// MARK: - CachedConnection
data class CachedConnection(
    val userId: String,
    val targetId: String,
    val type: String, // "follower" | "following"
    val timestamp: Date = Date(),
) {
    val id: String get() = "${userId}_${targetId}_$type"
}
