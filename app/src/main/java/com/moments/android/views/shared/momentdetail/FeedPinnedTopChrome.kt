package com.moments.android.views.shared.momentdetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.views.feed.rememberAdaptiveColors

/** Paridad iOS `ProfileHeaderCollapseMetrics` (valores usados por detalle feed). */
object ProfileHeaderCollapseMetrics {
    val chromeHeight = 36.dp
    val topChromePadding = 4.dp
    /** iOS: topChromePadding + chromeHeight + 12 */
    val feedStyleDetailTopInset = topChromePadding + chromeHeight + 12.dp
    const val feedDetailChromeBlurFadeTail = 48f
    const val detailScrollFadeLead = 64f

    fun detailScrollChromeBlurProgress(contentMinY: Float, initialContentMinY: Float): Float {
        if (!contentMinY.isFinite() || !initialContentMinY.isFinite()) return 0f
        val upwardTravel = initialContentMinY - contentMinY
        if (upwardTravel <= 0f) return 0f
        return (upwardTravel / detailScrollFadeLead).coerceIn(0f, 1f)
    }
}

/**
 * Port 1:1 de `FeedPinnedTopChrome` (`ProfileSharedComponents.swift`).
 * Chrome fijo: chevron atrás + título centrado.
 */
@Composable
fun FeedPinnedTopChrome(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    Box(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = ProfileHeaderCollapseMetrics.topChromePadding)
            .height(ProfileHeaderCollapseMetrics.chromeHeight)
            .padding(horizontal = 12.dp),
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
            // Trailing spacer paridad iOS navigationBack controlSize
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
