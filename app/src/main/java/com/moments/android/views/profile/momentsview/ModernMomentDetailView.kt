package com.moments.android.views.profile.momentsview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moments.android.models.Moment
import com.moments.android.views.explore.toExploreFeedMoment
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.shared.momentdetail.SingleMomentDetailView

/**
 * Port de `ModernMomentDetailView.swift`: pager vertical sobre la rejilla del perfil.
 * El cierre por arrastre es horizontal (igual que en iOS), así que no compite con el pager.
 * Cada página reutiliza `SingleMomentDetailView`, que ya trae menú contextual, edición,
 * comentarios, hashtags, mapa de ubicación y registro de visualización.
 */
@Composable
fun ModernMomentDetailView(
    moments: List<Moment>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialIndex: Int = 0,
    initialMomentId: String? = null,
    topContentInset: Dp = 0.dp,
    chromeTitle: String? = null,
) {
    val colors = rememberAdaptiveColors()

    // Igual que `clampedInitialIndex`: el id manda sobre el índice, y se recorta al rango.
    val resolvedInitialIndex = remember(moments, initialIndex, initialMomentId) {
        val byId = initialMomentId?.let { id -> moments.indexOfFirst { it.id == id } }?.takeIf { it >= 0 }
        val resolved = byId ?: initialIndex
        if (moments.isEmpty()) 0 else resolved.coerceIn(0, moments.size - 1)
    }

    if (moments.isEmpty()) {
        Box(modifier.fillMaxSize().background(colors.surfaceBackground))
        return
    }

    val pagerState = rememberPagerState(
        initialPage = resolvedInitialIndex,
        pageCount = { moments.size },
    )

    VerticalPager(
        state = pagerState,
        modifier = modifier
            .fillMaxSize()
            .background(colors.surfaceBackground),
        contentPadding = PaddingValues(top = topContentInset),
    ) { page ->
        SingleMomentDetailView(
            moment = moments[page].toExploreFeedMoment(),
            onDismiss = onDismiss,
            chromeTitle = chromeTitle,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
