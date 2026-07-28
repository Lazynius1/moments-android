package com.moments.android.views.shared.momentdetail

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moments.android.views.feed.maps.LocationMomentDetailView
import com.moments.android.views.profile.momentsview.ModernMomentDetailView

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
            ModernMomentDetailView(
                moments = context.moments,
                onDismiss = {
                    context.onDismiss()
                    onDismiss()
                },
                initialIndex = context.initialIndex,
                initialMomentId = context.initialMomentId,
                topContentInset = context.topContentInsetDp.dp,
                restrictPlaybackToInitialIndex = context.restrictPlaybackToInitialIndex,
                modifier = modifier.fillMaxSize(),
            )
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
