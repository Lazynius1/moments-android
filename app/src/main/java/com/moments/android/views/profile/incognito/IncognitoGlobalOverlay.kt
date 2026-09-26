package com.moments.android.views.profile.incognito

import android.os.Build
import android.view.RoundedCorner
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moments.android.R
import com.moments.android.extensions.MomentsChromeGlass
import com.moments.android.extensions.MomentsGlassStyle
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.services.incognito.IncognitoModeService
import com.moments.android.utilities.HapticManager
import com.moments.android.utilities.legacyPoppinsSize
import com.moments.android.views.components.MomentsCircularProgressIndicator
import com.moments.android.views.feed.rememberAdaptiveColors

private val BannerCapsule = RoundedCornerShape(percent = 50)

/**
 * Solo el aura de borde (sin pill).
 * La pill vive en [com.moments.android.views.components.InAppBannerView] para unificar
 * el host Dialog encima de `fullScreenDialog` / Nav3.
 */
@Composable
fun IncognitoGlobalOverlay(modifier: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme()
    val density = LocalDensity.current
    val view = LocalView.current
    var rootInsets by remember(view) {
        mutableStateOf<android.view.WindowInsets?>(view.rootWindowInsets)
    }

    val transition = rememberInfiniteTransition(label = "incognitoEdge")
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.52f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(tween(2_200), repeatMode = RepeatMode.Reverse),
        label = "edgePulse",
    )

    BoxWithConstraints(modifier.fillMaxSize()) {
        LaunchedEffect(view, maxWidth, maxHeight) {
            withFrameNanos { }
            rootInsets = view.rootWindowInsets
        }

        fun deviceCornerRadius(position: Int): androidx.compose.ui.unit.Dp {
            val radiusPx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                rootInsets?.getRoundedCorner(position)?.radius
            } else {
                null
            }
            return if (radiusPx != null && radiusPx > 0) {
                with(density) { radiusPx.toDp() }
            } else {
                28.dp
            }
        }

        val shape = RoundedCornerShape(
            topStart = deviceCornerRadius(RoundedCorner.POSITION_TOP_LEFT),
            topEnd = deviceCornerRadius(RoundedCorner.POSITION_TOP_RIGHT),
            bottomEnd = deviceCornerRadius(RoundedCorner.POSITION_BOTTOM_RIGHT),
            bottomStart = deviceCornerRadius(RoundedCorner.POSITION_BOTTOM_LEFT),
        )

        Box(
            Modifier
                .fillMaxSize()
                .padding(1.dp)
                .border(
                    width = 1.15.dp,
                    color = if (isDark) Color.White.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.28f),
                    shape = shape,
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .padding(2.dp)
                .graphicsLayer { alpha = pulseAlpha }
                .border(
                    width = 2.2.dp,
                    color = if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.13f),
                    shape = shape,
                ),
        )
    }
}

/**
 * Píldora Incognito: el chrome compacto lo aporta el host (mensaje↔timer morph).
 * Montada dentro del Dialog de [com.moments.android.views.components.InAppBannerView].
 */
@Composable
fun IncognitoBannerPill(
    modifier: Modifier = Modifier,
    showsCompactOnly: Boolean = false,
    horizontalPadding: androidx.compose.ui.unit.Dp = 20.dp,
    compact: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val colors = rememberAdaptiveColors()
    val isDark = isSystemInDarkTheme()
    val isSyncing by IncognitoModeService.isSyncing.collectAsState()
    val remainingSeconds by IncognitoModeService.remainingSeconds.collectAsState()
    val formattedTime = remember(remainingSeconds) {
        val remaining = maxOf(remainingSeconds, 0)
        "%02d:%02d".format(remaining / 60, remaining % 60)
    }
    var isExpanded by remember { mutableStateOf(false) }
    val titleSp = with(density) { legacyPoppinsSize(context, 14).toSp() }
    val subtitleSp = with(density) { legacyPoppinsSize(context, 12).toSp() }

    LaunchedEffect(showsCompactOnly) {
        if (showsCompactOnly) isExpanded = false
    }

    CompositionLocalProvider(
        LocalContentColor provides MomentsChromeGlass.contentColor(isDark),
    ) {
        Column(
            modifier = modifier.wrapContentWidth().padding(horizontal = horizontalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (compact != null) {
                Box(
                    modifier = if (showsCompactOnly) {
                        Modifier
                    } else {
                        Modifier.clickable {
                            HapticManager.shared.selection()
                            isExpanded = !isExpanded
                        }
                    },
                ) {
                    compact()
                }
            } else {
                Row(
                    modifier = Modifier
                        .width(108.dp)
                        .height(40.dp)
                        .shadow(
                            elevation = 8.dp,
                            shape = BannerCapsule,
                            ambientColor = Color.Black.copy(alpha = 0.08f),
                            spotColor = Color.Black.copy(alpha = 0.08f),
                        )
                        .momentsChromeGlass(
                            shape = BannerCapsule,
                            interactive = true,
                            style = MomentsGlassStyle.NATIVE,
                        )
                        .clickable(enabled = !showsCompactOnly) {
                            HapticManager.shared.selection()
                            isExpanded = !isExpanded
                        }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        Icons.Filled.VisibilityOff,
                        contentDescription = stringResource(R.string.incognito_overlay_label),
                        tint = LocalContentColor.current,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        formattedTime,
                        color = LocalContentColor.current,
                        fontSize = titleSp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded && !showsCompactOnly,
                enter = fadeIn(
                    tween(durationMillis = 150, easing = LinearOutSlowInEasing),
                ) + scaleIn(
                    initialScale = 0.96f,
                    transformOrigin = TransformOrigin(0.5f, 0f),
                    animationSpec = tween(durationMillis = 150, easing = LinearOutSlowInEasing),
                ),
                exit = fadeOut(
                    tween(durationMillis = 90, easing = FastOutLinearInEasing),
                ) + scaleOut(
                    targetScale = 0.98f,
                    transformOrigin = TransformOrigin(0.5f, 0f),
                    animationSpec = tween(durationMillis = 90, easing = FastOutLinearInEasing),
                ),
            ) {
                Column(
                    Modifier
                        .padding(top = 4.dp)
                        .width(230.dp)
                        .heightIn(min = 106.dp)
                        .momentsChromeGlass(
                            shape = RoundedCornerShape(24.dp),
                            interactive = true,
                            style = MomentsGlassStyle.NATIVE,
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        stringResource(R.string.incognito_live_hint_active),
                        color = colors.secondary,
                        fontSize = subtitleSp,
                    )

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(50))
                            .momentsChromeGlass(RoundedCornerShape(50), interactive = !isSyncing)
                            .clickable(enabled = !isSyncing) {
                                HapticManager.shared.mediumImpact()
                                IncognitoModeService.pause()
                            }
                            .padding(vertical = 13.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isSyncing) {
                            MomentsCircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                Icons.Filled.Pause,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(13.dp),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.incognito_cta_pause),
                            color = colors.primary,
                            fontSize = titleSp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}
