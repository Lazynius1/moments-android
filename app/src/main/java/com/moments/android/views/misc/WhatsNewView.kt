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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.AddReaction
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.services.performance.MotionPolicy
import com.moments.android.views.feed.rememberAdaptiveColors
import kotlinx.coroutines.delay

/** ≡ WhatsNew 1.2.0 — título corto + frase breve, icono de Material Icons. */
private data class WhatsNewFeature(
    @StringRes val title: Int,
    @StringRes val description: Int,
    val icon: ImageVector,
)

/** ≡ WhatsNewSection (iOS): cabecera de sección + sus novedades. */
private data class WhatsNewSection(
    @StringRes val title: Int,
    val features: List<WhatsNewFeature>,
)

@Composable
fun WhatsNewView(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val colors = rememberAdaptiveColors()
    val isDark = isSystemInDarkTheme()
    val reduceMotion = MotionPolicy.reduceMotion
    val locale = LocalConfiguration.current.locales[0]
    var appearAnimation by remember { mutableStateOf(reduceMotion) }

    val sections = remember {
        listOf(
            WhatsNewSection(
                title = R.string.whats_new_section_chat_120,
                features = listOf(
                    WhatsNewFeature(R.string.whats_new_chat_style_120_title, R.string.whats_new_chat_style_120_description, Icons.Default.Palette),
                    WhatsNewFeature(R.string.whats_new_reactions_120_title, R.string.whats_new_reactions_120_description, Icons.Default.AddReaction),
                    WhatsNewFeature(R.string.whats_new_replies_120_title, R.string.whats_new_replies_120_description, Icons.AutoMirrored.Filled.Reply),
                    WhatsNewFeature(R.string.whats_new_view_once_120_title, R.string.whats_new_view_once_120_description, Icons.Default.PlayCircle),
                    WhatsNewFeature(R.string.whats_new_requests_120_title, R.string.whats_new_requests_120_description, Icons.Default.MarkEmailUnread),
                    WhatsNewFeature(R.string.whats_new_reliability_120_title, R.string.whats_new_reliability_120_description, Icons.Default.SignalCellularAlt),
                    WhatsNewFeature(R.string.whats_new_notifications_120_title, R.string.whats_new_notifications_120_description, Icons.Default.NotificationsActive),
                ),
            ),
            WhatsNewSection(
                title = R.string.whats_new_section_stories_120,
                features = listOf(
                    WhatsNewFeature(R.string.whats_new_story_publish_120_title, R.string.whats_new_story_publish_120_description, Icons.Default.Tune),
                    WhatsNewFeature(R.string.whats_new_original_audio_120_title, R.string.whats_new_original_audio_120_description, Icons.Default.GraphicEq),
                ),
            ),
            WhatsNewSection(
                title = R.string.whats_new_section_more_120,
                features = listOf(
                    WhatsNewFeature(R.string.whats_new_maps_120_title, R.string.whats_new_maps_120_description, Icons.Default.Map),
                    WhatsNewFeature(R.string.whats_new_banner_120_title, R.string.whats_new_banner_120_description, Icons.Default.Campaign),
                    WhatsNewFeature(R.string.whats_new_comments_120_title, R.string.whats_new_comments_120_description, Icons.AutoMirrored.Filled.Comment),
                ),
            ),
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
        animationSpec = if (reduceMotion) tween(0) else spring(dampingRatio = 0.82f, stiffness = 70f),
        label = "whatsNewHeader",
    )
    val footerAppear by animateFloatAsState(
        targetValue = if (appearAnimation) 1f else 0f,
        animationSpec = if (reduceMotion) tween(0) else spring(dampingRatio = 0.82f, stiffness = 70f),
        label = "whatsNewFooter",
    )

    Column(
        modifier = modifier
            .fillMaxSize()
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
                        stringResource(R.string.whats_new_title_120),
                        color = colors.primary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.whats_new_subtitle_120),
                        color = colors.secondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                sections.forEachIndexed { sectionIndex, section ->
                    val firstRowIndex = sections.take(sectionIndex).sumOf { it.features.size }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            stringResource(section.title).uppercase(locale),
                            color = colors.secondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.6.sp,
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .padding(top = if (sectionIndex == 0) 0.dp else 8.dp)
                                .semantics { heading() }
                                .graphicsLayer { alpha = headerAppear },
                        )
                        section.features.forEachIndexed { featureIndex, feature ->
                            WhatsNewFeatureRow(
                                feature = feature,
                                delayMs = (firstRowIndex + featureIndex) * 40L,
                                reduceMotion = reduceMotion,
                                appearParent = appearAnimation,
                            )
                        }
                    }
                }
            }

            Text(
                stringResource(R.string.whats_new_note_closing_120),
                color = colors.secondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            )

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
                    stringResource(R.string.whats_new_button_120),
                    color = colors.primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
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
        if (reduceMotion) {
            appear = true
            return@LaunchedEffect
        }
        appear = false
        if (appearParent) {
            delay(delayMs)
            appear = true
        }
    }
    val t by animateFloatAsState(
        targetValue = if (appear) 1f else 0f,
        animationSpec = if (reduceMotion) tween(0) else spring(dampingRatio = 0.82f, stiffness = 120f),
        label = "whatsNewRow",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .graphicsLayer {
                translationY = (1f - t) * 18f
                alpha = t
            },
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .momentsChromeGlass(CircleShape, interactive = false),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = feature.icon,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(18.dp),
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
            )
        }
    }
}
