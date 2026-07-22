package com.moments.android.views.shared.momentdetail

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.moments.android.views.feed.maps.LocationMomentDetailView

/**
 * Port 1:1 de `MomentDetailContainerView.swift`.
 * Delega en la vista especializada según [MomentDetailContext].
 */
@Composable
fun MomentDetailContainerView(
    context: MomentDetailContext,
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    when (context) {
        is MomentDetailContext.Single -> SingleMomentDetailView(
            moment = context.moment,
            onDismiss = onDismiss,
            modifier = modifier.fillMaxSize(),
        )
        is MomentDetailContext.ProfileCarousel -> {
            // ModernMomentDetailView aún no portado — stub honesto con el primer/índice inicial.
            val index = context.initialIndex.coerceIn(0, (context.moments.size - 1).coerceAtLeast(0))
            val moment = context.moments.getOrNull(index)
            if (moment != null) {
                SingleMomentDetailView(
                    moment = moment,
                    onDismiss = {
                        context.onDismiss()
                        onDismiss()
                    },
                    modifier = modifier.fillMaxSize(),
                )
            }
        }
        is MomentDetailContext.Map -> LocationMomentDetailView(
            moments = context.moments,
            initialIndex = context.initialIndex,
            locationName = context.locationName,
            momentAvailability = context.momentAvailability,
            onDismiss = {
                context.onDismiss()
                onDismiss()
            },
            modifier = modifier.fillMaxSize(),
        )
    }
}
