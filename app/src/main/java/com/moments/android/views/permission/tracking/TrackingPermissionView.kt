package com.moments.android.views.permission.tracking

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.PanToolAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.moments.android.R
import com.moments.android.views.permission.shared.PermissionPhoneFrame
import com.moments.android.views.permission.shared.PermissionPrimerScaffold
import com.moments.android.views.permission.shared.PermissionPrimerStage
import com.moments.android.views.permission.shared.permissionMockOverflowHeight
import com.moments.android.views.permission.shared.rememberPermissionLoopTimeSeconds
import kotlin.math.max

private val TrackingAccent = Color(0xFF6C5CE7)
private const val CardCount = 5
private const val AccentIndex = 1
private const val LoopDurationMs = 9000

/** Port de `TrackingPermissionView.swift`. */
@Composable
fun TrackingPermissionView(
    stage: PermissionPrimerStage = PermissionPrimerStage.PRIMER,
    primaryAction: () -> Unit,
    secondaryAction: (() -> Unit)? = null,
) {
    val denied = stage == PermissionPrimerStage.DENIED
    PermissionPrimerScaffold(
        stage = stage,
        icon = { tint ->
            Icon(
                // ≡ hand.raised.fill / hand.raised.slash.fill
                if (denied) Icons.Default.PanToolAlt else Icons.Default.PanTool,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.fillMaxSize(),
            )
        },
        title = stringResource(
            if (denied) R.string.permission_tracking_denied_title
            else R.string.att_pre_alert_title,
        ),
        description = stringResource(
            if (denied) R.string.permission_tracking_denied_subtitle
            else R.string.att_pre_alert_description,
        ),
        primaryActionTitle = stringResource(
            if (denied) R.string.permission_tracking_denied_open_settings
            else R.string.att_pre_alert_continue,
        ),
        secondaryActionTitle = if (denied || secondaryAction == null) {
            null
        } else {
            stringResource(R.string.permission_tracking_primer_not_now)
        },
        primaryAction = primaryAction,
        secondaryAction = secondaryAction,
    ) {
        PermissionPhoneFrame(
            screenBackground = Color(0xFF111318),
            animated = false,
            appliesDeniedChrome = denied,
            screen = { size, _ ->
                TrackingFeedScreen(size = size, isActive = !denied)
            },
            island = { _, _ -> },
        )
    }
}

/**
 * ≡ TrackingFeedScreen — 5 cards × 2 sets, scroll loop 9s.
 * Overflow height: sin esto Column comprime las cards al alto del teléfono
 * y el loop no puede ser continuo.
 */
@Composable
private fun TrackingFeedScreen(size: DpSize, isActive: Boolean) {
    val density = LocalDensity.current
    val heightPx = with(density) { size.height.toPx() }
    val spacingPx = heightPx * 0.02f
    val cardHeightPx = heightPx * 0.26f
    // Distancia entre card i y card i+cardCount (incluye spacing entre sets)
    val setHeight = (cardHeightPx + spacingPx) * CardCount

    val t = rememberPermissionLoopTimeSeconds(isActive)
    val loopSec = LoopDurationMs / 1000f
    val progress = if (isActive) {
        (t % loopSec) / loopSec
    } else {
        0.4f
    }
    val scroll = if (isActive) {
        progress * setHeight
    } else {
        max(setHeight - heightPx, 0f) * 0.4f
    }
    val renderedSets = if (isActive) 2 else 1
    val spacing = with(density) { spacingPx.toDp() }
    val cardHeight = with(density) { cardHeightPx.toDp() }
    val contentMinHeight = with(density) {
        (setHeight * renderedSets - spacingPx).toDp()
    }

    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .then(if (isActive) Modifier else Modifier.blur(2.5.dp)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = size.width * 0.05f)
                .permissionMockOverflowHeight(minHeight = contentMinHeight)
                .graphicsLayer { translationY = -scroll },
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            repeat(CardCount * renderedSets) { index ->
                TrackingFeedCard(
                    size = size,
                    highlighted = index % CardCount == AccentIndex,
                    height = cardHeight,
                )
            }
        }
    }
}

@Composable
private fun TrackingFeedCard(
    size: DpSize,
    highlighted: Boolean,
    height: androidx.compose.ui.unit.Dp,
) {
    val density = LocalDensity.current
    val w = with(density) { size.width.toPx() }
    val h = with(density) { size.height.toPx() }
    val corner = with(density) { (w * 0.05f).toDp() }
    val mediaCorner = with(density) { (w * 0.03f).toDp() }
    val avatar = with(density) { (w * 0.1f).toDp() }
    val pad = with(density) { (w * 0.035f).toDp() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(corner))
            .background(Color.White.copy(alpha = 0.05f))
            .then(
                if (highlighted) {
                    Modifier.border(1.5.dp, TrackingAccent.copy(alpha = 0.7f), RoundedCornerShape(corner))
                } else {
                    Modifier
                },
            )
            .padding(pad),
        verticalArrangement = Arrangement.spacedBy(with(density) { (h * 0.012f).toDp() }),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(with(density) { (w * 0.03f).toDp() }),
        ) {
            Box(
                Modifier
                    .size(avatar)
                    .clip(CircleShape)
                    .background(if (highlighted) TrackingAccent else Color.White.copy(alpha = 0.18f)),
            )
            Column(verticalArrangement = Arrangement.spacedBy(with(density) { (h * 0.006f).toDp() })) {
                Box(
                    Modifier
                        .width(with(density) { (w * 0.34f).toDp() })
                        .height(with(density) { (h * 0.012f).toDp() })
                        .background(Color.White.copy(alpha = 0.5f), CircleShape),
                )
                Box(
                    Modifier
                        .width(with(density) { (w * 0.22f).toDp() })
                        .height(with(density) { (h * 0.01f).toDp() })
                        .background(Color.White.copy(alpha = 0.28f), CircleShape),
                )
            }
            Spacer(Modifier.weight(1f))
            if (highlighted) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(TrackingAccent)
                        .padding(
                            horizontal = with(density) { (w * 0.025f).toDp() },
                            vertical = with(density) { (h * 0.005f).toDp() },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(with(density) { (w * 0.01f).toDp() }),
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(with(density) { (w * 0.032f).toDp() }),
                    )
                    Text(
                        stringResource(R.string.permission_tracking_mock_ad),
                        color = Color.White,
                        fontSize = with(density) { (w * 0.032f).toSp() },
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(mediaCorner))
                .background(
                    if (highlighted) {
                        Brush.linearGradient(
                            listOf(
                                TrackingAccent.copy(alpha = 0.85f),
                                Color(0xFF4C8DFF).copy(alpha = 0.7f),
                            ),
                        )
                    } else {
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.1f),
                                Color.White.copy(alpha = 0.05f),
                            ),
                        )
                    },
                ),
        )
    }
}
