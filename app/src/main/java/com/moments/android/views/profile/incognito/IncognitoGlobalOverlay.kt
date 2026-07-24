package com.moments.android.views.profile.incognito

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moments.android.R
import com.moments.android.services.incognito.IncognitoModeService
import com.moments.android.utilities.HapticManager
import com.moments.android.utilities.legacyPoppinsSize
import com.moments.android.views.feed.rememberAdaptiveColors
import kotlin.math.min

/** Port de `IncognitoGlobalOverlay.swift`: borde con aura + píldora con el contador. */
@Composable
fun IncognitoGlobalOverlay(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val colors = rememberAdaptiveColors()
    val isDark = isSystemInDarkTheme()

    val isActive by IncognitoModeService.isActive.collectAsState()
    val isSyncing by IncognitoModeService.isSyncing.collectAsState()
    var isExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(isActive) {
        if (!isActive) isExpanded = false
    }

    val transition = rememberInfiniteTransition(label = "incognitoEdge")
    val pulse by transition.animateFloat(
        initialValue = 1.5f,
        targetValue = 2.4f,
        animationSpec = infiniteRepeatable(tween(2_200), repeatMode = RepeatMode.Reverse),
        label = "edgePulse",
    )

    BoxWithConstraints(modifier.fillMaxSize()) {
        val cornerRadius = min(maxWidth.value, maxHeight.value) * 0.136f

        // Aura del borde (no intercepta gestos)
        Box(
            Modifier
                .fillMaxSize()
                .padding(1.dp)
                .border(
                    width = 1.15.dp,
                    color = if (isDark) Color.White.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.28f),
                    shape = RoundedCornerShape(cornerRadius.dp),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .padding(1.dp)
                .blur(pulse.dp)
                .border(
                    width = 3.2.dp,
                    color = (if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.13f))
                        .copy(alpha = if (isDark) 0.95f else 0.88f),
                    shape = RoundedCornerShape(cornerRadius.dp),
                ),
        )

        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 38.dp, start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Píldora compacta
            Row(
                Modifier
                    .width(108.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colors.surfaceBackground.copy(alpha = 0.92f))
                    .clickable {
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
                    tint = colors.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    IncognitoModeService.formattedTime,
                    color = colors.primary,
                    fontSize = with(density) { legacyPoppinsSize(context, 14).toSp() },
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                )
            }

            // Panel desplegado
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(
                    Modifier
                        .padding(top = 8.dp)
                        .width(230.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(colors.surfaceBackground.copy(alpha = 0.95f))
                        .border(
                            0.75.dp,
                            Color.White.copy(alpha = if (isDark) 0.10f else 0.08f),
                            RoundedCornerShape(24.dp),
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        stringResource(R.string.incognito_live_hint_active),
                        color = colors.secondary,
                        fontSize = with(density) { legacyPoppinsSize(context, 13).toSp() },
                    )

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(50))
                            .background(colors.primary.copy(alpha = if (isSyncing) 0.04f else 0.08f))
                            .clickable(enabled = !isSyncing) {
                                HapticManager.shared.mediumImpact()
                                IncognitoModeService.pause()
                            }
                            .padding(vertical = 13.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                color = colors.primary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(14.dp),
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
                            fontSize = with(density) { legacyPoppinsSize(context, 14).toSp() },
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}
