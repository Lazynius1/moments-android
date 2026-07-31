package com.moments.android.views.misc

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.services.performance.MotionPolicy
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.shared.ScreenshotProtectedView
import kotlinx.coroutines.delay

/**
 * Bienvenida Android 1.0 — misma estructura de chrome que `WhatsNewView.swift`
 * (sheet + filas + CTA), copy propio de lanzamiento (no changelog iOS 2.18).
 */
private data class WhatsNewFeature(
    @StringRes val title: Int,
    @StringRes val description: Int,
    val icon: ImageVector,
)

@Composable
fun WhatsNewView(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val colors = rememberAdaptiveColors()
    val isDark = isSystemInDarkTheme()
    val reduceMotion = MotionPolicy.reduceMotion
    var appearAnimation by remember { mutableStateOf(reduceMotion) }

    val features = remember {
        listOf(
            WhatsNewFeature(R.string.whats_new_feed_title, R.string.whats_new_feed_description, Icons.Default.Public),
            WhatsNewFeature(R.string.whats_new_stories_title, R.string.whats_new_stories_description, Icons.Default.PhotoCamera),
            WhatsNewFeature(R.string.whats_new_chat_title, R.string.whats_new_chat_description, Icons.AutoMirrored.Filled.Chat),
            WhatsNewFeature(R.string.whats_new_map_title, R.string.whats_new_map_description, Icons.Default.Explore),
            WhatsNewFeature(R.string.whats_new_privacy_title, R.string.whats_new_privacy_description, Icons.Default.Lock),
            WhatsNewFeature(R.string.whats_new_nova_title, R.string.whats_new_nova_description, Icons.Default.AutoAwesome),
        )
    }

    LaunchedEffect(reduceMotion) {
        if (reduceMotion) {
            appearAnimation = true
            return@LaunchedEffect
        }
        appearAnimation = false
        delay(16)
        appearAnimation = true
    }

    val headerAppear by animateFloatAsState(
        targetValue = if (appearAnimation) 1f else 0f,
        animationSpec = if (reduceMotion) {
            tween(0)
        } else {
            spring(dampingRatio = 0.82f, stiffness = 70f)
        },
        label = "whatsNewHeader",
    )
    val footerAppear by animateFloatAsState(
        targetValue = if (appearAnimation) 1f else 0f,
        animationSpec = if (reduceMotion) {
            tween(0)
        } else {
            spring(dampingRatio = 0.82f, stiffness = 70f)
        },
        label = "whatsNewFooter",
    )

    ScreenshotProtectedView(isProtected = true) {
        Column(
            modifier = modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 22.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 10.dp)
                    .graphicsLayer {
                        val scale = 0.96f + 0.04f * headerAppear
                        scaleX = scale
                        scaleY = scale
                        alpha = headerAppear
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Image(
                    painter = painterResource(
                        if (isDark) R.drawable.login_logo else R.drawable.whatsnew,
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(54.dp),
                    contentScale = ContentScale.Fit,
                )
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        stringResource(R.string.whats_new_title),
                        color = colors.primary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.whats_new_subtitle),
                        color = colors.secondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                features.forEachIndexed { index, feature ->
                    WhatsNewFeatureRow(
                        feature = feature,
                        delayMs = index * 40L,
                        reduceMotion = reduceMotion,
                        appearParent = appearAnimation,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .graphicsLayer {
                        translationY = (1f - footerAppear) * 18f
                        alpha = footerAppear
                    }
                    .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.whats_new_button),
                    color = colors.primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun WhatsNewFeatureRow(
    feature: WhatsNewFeature,
    delayMs: Long,
    reduceMotion: Boolean,
    appearParent: Boolean,
) {
    val colors = rememberAdaptiveColors()
    var appear by remember { mutableStateOf(reduceMotion) }
    LaunchedEffect(appearParent, reduceMotion) {
        if (!appearParent) {
            appear = false
            return@LaunchedEffect
        }
        if (reduceMotion) {
            appear = true
            return@LaunchedEffect
        }
        appear = false
        delay(delayMs)
        appear = true
    }
    val progress by animateFloatAsState(
        targetValue = if (appear) 1f else 0f,
        animationSpec = if (reduceMotion) {
            tween(0)
        } else {
            spring(dampingRatio = 0.82f, stiffness = 140f)
        },
        label = "whatsNewRow",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .graphicsLayer {
                translationY = (1f - progress) * 18f
                alpha = progress
            },
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .momentsChromeGlass(CircleShape, interactive = false),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                feature.icon,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(17.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                stringResource(feature.title),
                color = colors.primary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(feature.description),
                color = colors.secondary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        }
    }
}
