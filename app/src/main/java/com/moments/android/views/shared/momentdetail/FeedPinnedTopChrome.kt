package com.moments.android.views.shared.momentdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.views.feed.rememberAdaptiveColors

/**
 * Métricas de chrome en detalles de momento — criterio **Android**
 * (edge-to-edge + header sólido). No copiar toolbar translúcida iOS.
 */
object ProfileHeaderCollapseMetrics {
    val chromeHeight = 36.dp
    val topChromePadding = 4.dp
    /** Username + subtítulo (ProfileMomentDetailChrome). */
    val profileDetailChromeBodyHeight = chromeHeight + 14.dp
    /** Aire entre el borde inferior del header sólido y la primera card. */
    val contentGapBelowChrome = 16.dp
    /**
     * Inset de lista **sin** status bar (solo cuerpo chrome + gap).
     * Preferir [rememberMomentDetailContentTopInset].
     */
    val feedStyleDetailTopInset = topChromePadding + chromeHeight + contentGapBelowChrome
    const val feedDetailChromeBlurFadeTail = 48f
    const val locationChromeBlurFadeTail = feedDetailChromeBlurFadeTail
    const val detailScrollFadeLead = 64f

    fun detailScrollChromeBlurProgress(contentMinY: Float, initialContentMinY: Float): Float {
        if (!contentMinY.isFinite() || !initialContentMinY.isFinite()) return 0f
        val upwardTravel = initialContentMinY - contentMinY
        if (upwardTravel <= 0f) return 0f
        return (upwardTravel / detailScrollFadeLead).coerceIn(0f, 1f)
    }
}

/**
 * Top inset del LazyColumn: statusBars + padding chrome + altura del cuerpo + gap.
 * Así el primer momento no arranca debajo/debajo-del del header sólido.
 */
@Composable
fun rememberMomentDetailContentTopInset(
    chromeBodyHeight: Dp = ProfileHeaderCollapseMetrics.chromeHeight,
): Dp {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    return remember(statusTop, chromeBodyHeight) {
        statusTop +
            ProfileHeaderCollapseMetrics.topChromePadding +
            chromeBodyHeight +
            ProfileHeaderCollapseMetrics.contentGapBelowChrome
    }
}

/**
 * Host de header sólido (canvas AdaptiveColors) + status bar.
 * El contenido del chrome **no** debe aplicar otro [statusBarsPadding].
 */
@Composable
fun MomentDetailSolidTopChrome(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = rememberAdaptiveColors()
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.surfaceBackground)
            .statusBarsPadding()
            .padding(top = ProfileHeaderCollapseMetrics.topChromePadding)
            .padding(bottom = 4.dp),
    ) {
        content()
    }
}

/**
 * Chrome fijo: chevron atrás + título centrado.
 *
 * @param applySafeAreaTop true si flota solo (legacy). Preferir
 * [MomentDetailSolidTopChrome] + `applySafeAreaTop = false`.
 */
@Composable
fun FeedPinnedTopChrome(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    applySafeAreaTop: Boolean = true,
) {
    val colors = rememberAdaptiveColors()
    Box(
        modifier
            .fillMaxWidth()
            .then(
                if (applySafeAreaTop) {
                    Modifier.statusBarsPadding()
                } else {
                    Modifier
                },
            )
            .height(ProfileHeaderCollapseMetrics.chromeHeight)
            .padding(horizontal = if (applySafeAreaTop) 12.dp else 12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().height(ProfileHeaderCollapseMetrics.chromeHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .momentsChromeGlass(CircleShape, interactive = true)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = null,
                    tint = colors.primary,
                )
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(36.dp))
        }
        Text(
            text = title,
            color = colors.primary,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            softWrap = true,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 56.dp)
                .fillMaxWidth(),
        )
    }
}
