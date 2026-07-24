package com.moments.android.models.cache

import java.util.Date

// Entidad de caché local (SwiftData @Model en iOS → Room en Android).

// MARK: - CachedSearch
data class CachedSearch(
    val query: String,
    val type: String, // "user" | "hashtag" | "text"
    val targetId: String? = null,
    val timestamp: Date = Date(),
) {
    val id: String get() = "${query}_${type}_${targetId ?: "none"}"
}
