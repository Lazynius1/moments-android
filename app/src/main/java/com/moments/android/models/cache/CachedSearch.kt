package com.moments.android.models.cache

import java.util.Date

/**
 * Port de `Models/Cache/CachedSearch.swift` (SwiftData @Model → data class / JSON prefs).
 * id compuesto: query_type_targetId
 */
data class CachedSearch(
    val query: String,
    /** `"user"` | `"hashtag"` | `"text"` */
    val type: String,
    val targetId: String? = null,
    val timestamp: Date = Date(),
) {
    val id: String get() = "${query}_${type}_${targetId ?: "none"}"
}
