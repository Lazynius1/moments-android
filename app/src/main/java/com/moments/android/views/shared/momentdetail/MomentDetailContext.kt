package com.moments.android.views.shared.momentdetail

import com.moments.android.models.Moment
import com.moments.android.services.content.FeedMoment

/**
 * Port 1:1 de `MomentDetailContext.swift`.
 * Contexto de presentación unificado para detalle de momento.
 */
sealed class MomentDetailContext {
    data class Single(val moment: FeedMoment) : MomentDetailContext()

    data class ProfileCarousel(
        val moments: List<FeedMoment>,
        val initialIndex: Int,
        val initialMomentId: String? = null,
        val topContentInsetDp: Float = 0f,
        val restrictPlaybackToInitialIndex: Boolean = false,
        val onDismiss: () -> Unit = {},
    ) : MomentDetailContext()

    data class Map(
        val moments: List<Moment>,
        val initialIndex: Int,
        val locationName: String,
        // FQN: nested `Map` shadowing kotlin.collections.Map
        val momentAvailability: kotlin.collections.Map<String, Boolean>,
        val onDismiss: () -> Unit,
    ) : MomentDetailContext()
}
