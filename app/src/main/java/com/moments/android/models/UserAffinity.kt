package com.moments.android.models

import java.util.Date

/**
 * Port de `Models/UserAffinity.swift`.
 * Afinidad local hacia otro usuario; nunca sale del dispositivo (feeds/sugerencias).
 * Persistencia vía AffinityTracker (SharedPrefs ≈ SwiftData).
 */
data class UserAffinity(
    val ownerUserId: String,
    val targetUserId: String,
    val score: Double = 0.0,
    val lastInteractionDate: Date = Date(),
    val interactionCounts: Map<String, Int> = emptyMap(),
) {
    /** Clave compuesta: ownerUserId|targetUserId */
    val affinityKey: String get() = makeAffinityKey(ownerUserId, targetUserId)

    companion object {
        fun makeAffinityKey(ownerUserId: String, targetUserId: String): String =
            "$ownerUserId|$targetUserId"
    }
}
