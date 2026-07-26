package com.moments.android.models.cache

import java.util.Date

/**
 * Port de `Models/Cache/CachedConnection.swift` (SwiftData @Model → data class / JSON prefs).
 * id compuesto: userId_targetId_type
 */
data class CachedConnection(
    val userId: String,
    val targetId: String,
    /** `"follower"` | `"following"` */
    val type: String,
    val timestamp: Date = Date(),
) {
    val id: String get() = "${userId}_${targetId}_$type"
}
