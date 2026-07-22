package com.moments.android.views.explore

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.moments.android.models.Moment
import com.moments.android.views.shared.momentdetail.SingleMomentDetailView

/**
 * Port MVP de `ExploreMomentDetailView.swift`.
 * Detalle single reutilizando [SingleMomentDetailView]; scroll multi-momento explorer = pulido.
 */
@Composable
fun ExploreMomentDetailView(
    moment: Moment,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleMomentDetailView(
        moment = moment.toExploreFeedMoment(),
        onDismiss = onDismiss,
        modifier = modifier.fillMaxSize(),
    )
}
