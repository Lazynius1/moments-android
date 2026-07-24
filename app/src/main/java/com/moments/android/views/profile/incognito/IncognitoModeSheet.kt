package com.moments.android.views.profile.incognito

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MarkChatRead
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WifiOff
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.moments.android.R
import com.moments.android.services.incognito.IncognitoModeService
import com.moments.android.services.incognito.IncognitoModeService.LastErrorState
import com.moments.android.services.network.NetworkMonitor
import com.moments.android.utilities.HapticManager
import com.moments.android.utilities.legacyPoppinsSize
import com.moments.android.views.feed.rememberAdaptiveColors

private const val ONBOARDING_PREFS = "incognito_prefs"
private const val ONBOARDING_SEEN_KEY = "incognito_has_seen_inline_onboarding"

/** Port de `IncognitoModeSheet.swift`. */
@Composable
fun IncognitoModeSheet(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val colors = rememberAdaptiveColors()
    val isDark = isSystemInDarkTheme()

    val isLoaded by IncognitoModeService.isLoaded.collectAsState()
    val isSyncing by IncognitoModeService.isSyncing.collectAsState()
    val isActive by IncognitoModeService.isActive.collectAsState()
    val remainingSeconds by IncognitoModeService.remainingSeconds.collectAsState()
    val dailyBudgetSeconds by IncognitoModeService.dailyBudgetSeconds.collectAsState()
    val lastErrorState by IncognitoModeService.lastErrorState.collectAsState()
    val isConnected by NetworkMonitor.isConnectedFlow.collectAsState()

    val prefs = remember { context.getSharedPreferences(ONBOARDING_PREFS, android.content.Context.MODE_PRIVATE) }
    var hasSeenOnboarding by remember { mutableStateOf(prefs.getBoolean(ONBOARDING_SEEN_KEY, false)) }

    LaunchedEffect(Unit) {
        if (!isLoaded) IncognitoModeService.loadState()
    }

    val isExhausted = IncognitoModeService.isExhausted
    val primaryActionEnabled = !isSyncing && isConnected && !isExhausted

    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 12.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Cabecera
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.incognito_title),
                    color = colors.primary,
                    fontSize = with(density) { legacyPoppinsSize(context, 28).toSp() },
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.incognito_subtitle),
                    color = colors.secondary,
                    fontSize = with(density) { legacyPoppinsSize(context, 15).toSp() },
                    textAlign = TextAlign.Center,
                )
            }

            // Aro de progreso + tiempo restante
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    val progress by animateFloatAsState(
                        targetValue = IncognitoModeService.progress.toFloat().coerceAtLeast(0.0001f),
                        animationSpec = tween(300),
                        label = "incognitoProgress",
                    )
                    Canvas(Modifier.size(156.dp)) {
                        val stroke = 10.dp.toPx()
                        val inset = stroke / 2f
                        val arcSize = Size(size.width - stroke, size.height - stroke)
                        drawArc(
                            color = Color.White.copy(alpha = if (isDark) 0.10f else 0.14f),
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round),
                        )
                        drawArc(
                            brush = Brush.linearGradient(
                                listOf(Color(0xFF3BA4FF), Color(0xFF6E8BFF), Color(0xFF90E0EF)),
                            ),
                            startAngle = -90f,
                            sweepAngle = 360f * progress,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round),
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            if (isActive) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            IncognitoModeService.formattedTime,
                            color = colors.primary,
                            fontSize = with(density) { legacyPoppinsSize(context, 30).toSp() },
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            stringResource(
                                when {
                                    isSyncing -> R.string.incognito_status_syncing
                                    isExhausted -> R.string.incognito_status_exhausted
                                    isActive -> R.string.incognito_status_active
                                    else -> R.string.incognito_status_paused
                                },
                            ),
                            color = colors.secondary,
                            fontSize = with(density) { legacyPoppinsSize(context, 12).toSp() },
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                val hintRes = when {
                    lastErrorState != null -> when (lastErrorState) {
                        LastErrorState.OFFLINE -> R.string.incognito_error_offline
                        LastErrorState.EXHAUSTED -> R.string.incognito_error_exhausted
                        LastErrorState.UNAUTHORIZED -> R.string.incognito_error_unauthorized
                        else -> R.string.incognito_error_unavailable
                    }
                    isActive -> R.string.incognito_live_hint_active
                    remainingSeconds < dailyBudgetSeconds -> R.string.incognito_live_hint_paused
                    else -> null
                }
                hintRes?.let {
                    Text(
                        stringResource(it),
                        color = colors.secondary,
                        fontSize = with(density) { legacyPoppinsSize(context, 13).toSp() },
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // Acción principal
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(colors.primary.copy(alpha = if (primaryActionEnabled) 0.08f else 0.04f))
                    .clickable(enabled = primaryActionEnabled) {
                        when {
                            isActive -> IncognitoModeService.pause()
                            remainingSeconds == dailyBudgetSeconds -> IncognitoModeService.activate()
                            else -> IncognitoModeService.resume()
                        }
                    }
                    .padding(vertical = 15.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        color = colors.primary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp),
                    )
                } else {
                    Icon(
                        when {
                            !isConnected -> Icons.Filled.WifiOff
                            isExhausted -> Icons.Filled.Visibility
                            isActive -> Icons.Filled.VisibilityOff
                            else -> Icons.Filled.Visibility
                        },
                        contentDescription = null,
                        tint = colors.primary.copy(alpha = if (primaryActionEnabled) 1f else 0.5f),
                        modifier = Modifier.size(14.dp),
                    )
                }
                Spacer(Modifier.size(10.dp))
                Text(
                    stringResource(
                        when {
                            isSyncing -> R.string.incognito_cta_syncing
                            !isConnected -> R.string.incognito_cta_offline
                            isExhausted -> R.string.incognito_cta_exhausted
                            isActive -> R.string.incognito_cta_pause
                            remainingSeconds == dailyBudgetSeconds -> R.string.incognito_cta_activate
                            else -> R.string.incognito_cta_resume
                        },
                    ),
                    color = colors.primary.copy(alpha = if (primaryActionEnabled) 1f else 0.5f),
                    fontSize = with(density) { legacyPoppinsSize(context, 15).toSp() },
                    fontWeight = FontWeight.SemiBold,
                )
            }

            // Detalle de lo que oculta
            Column(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                IncognitoDetailRow(
                    Icons.Filled.RadioButtonChecked,
                    R.string.incognito_feature_stories_title,
                    R.string.incognito_feature_stories_body,
                )
                IncognitoDetailRow(
                    Icons.Filled.PersonSearch,
                    R.string.incognito_feature_visits_title,
                    R.string.incognito_feature_visits_body,
                )
                IncognitoDetailRow(
                    Icons.Filled.MarkChatRead,
                    R.string.incognito_feature_read_receipts_title,
                    R.string.incognito_feature_read_receipts_body,
                )
            }
        }

        // Onboarding inline (una sola vez)
        AnimatedVisibility(
            visible = !hasSeenOnboarding,
            enter = fadeIn() + scaleIn(initialScale = 0.985f),
            exit = fadeOut() + scaleOut(targetScale = 0.985f),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(colors.surfaceBackground.copy(alpha = 0.86f))
                    .padding(horizontal = 22.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    Modifier
                        .padding(top = 24.dp)
                        .widthIn(max = 360.dp)
                        .clip(RoundedCornerShape(30.dp))
                        .background(colors.surfaceBackground)
                        .border(
                            1.dp,
                            if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                            RoundedCornerShape(30.dp),
                        )
                        .padding(horizontal = 22.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Filled.VisibilityOff,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        stringResource(R.string.incognito_onboarding_title),
                        color = colors.primary,
                        fontSize = with(density) { legacyPoppinsSize(context, 24).toSp() },
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        stringResource(R.string.incognito_onboarding_body),
                        color = colors.secondary,
                        fontSize = with(density) { legacyPoppinsSize(context, 15).toSp() },
                        textAlign = TextAlign.Center,
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                        IncognitoBullet(R.string.incognito_onboarding_bullet_one)
                        IncognitoBullet(R.string.incognito_onboarding_bullet_two)
                        IncognitoBullet(R.string.incognito_onboarding_bullet_three)
                    }

                    Text(
                        stringResource(R.string.incognito_onboarding_dismiss),
                        color = colors.primary,
                        fontSize = with(density) { legacyPoppinsSize(context, 15).toSp() },
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(50))
                            .background(colors.primary.copy(alpha = 0.08f))
                            .clickable {
                                prefs.edit().putBoolean(ONBOARDING_SEEN_KEY, true).apply()
                                hasSeenOnboarding = true
                                HapticManager.shared.selection()
                            }
                            .padding(vertical = 15.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun IncognitoBullet(textRes: Int) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val colors = rememberAdaptiveColors()
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier
                .padding(top = 6.dp)
                .size(7.dp)
                .clip(CircleShape)
                .background(colors.primary.copy(alpha = 0.9f)),
        )
        Text(
            stringResource(textRes),
            color = colors.secondary,
            fontSize = with(density) { legacyPoppinsSize(context, 14).toSp() },
        )
    }
}

@Composable
private fun IncognitoDetailRow(icon: ImageVector, titleRes: Int, bodyRes: Int) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val colors = rememberAdaptiveColors()
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(titleRes),
                color = colors.primary,
                fontSize = with(density) { legacyPoppinsSize(context, 14).toSp() },
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(bodyRes),
                color = colors.secondary,
                fontSize = with(density) { legacyPoppinsSize(context, 13).toSp() },
            )
        }
    }
}
