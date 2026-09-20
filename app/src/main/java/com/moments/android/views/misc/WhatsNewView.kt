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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.services.performance.MotionPolicy
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.messaging.components.AttachmentIcon
import com.moments.android.views.messaging.components.AttachmentIconMetrics
import com.moments.android.views.messaging.components.AttachmentIconView
import com.moments.android.views.messaging.groups.GroupChatAvatar
import com.moments.android.views.story.StorySegmentedRing
import com.moments.android.views.story.storyRingGapMask
import kotlinx.coroutines.delay

/** ≡ WhatsNew 1.1.0 — misma extensión title+description que iOS 2.30. */
private sealed class WhatsNewIcon {
    data class Vector(val image: ImageVector) : WhatsNewIcon()
    data class Attachment(val icon: AttachmentIcon) : WhatsNewIcon()
    data object PersonalAndGroupStoryRings : WhatsNewIcon()
}

private data class WhatsNewFeature(
    @StringRes val title: Int,
    @StringRes val description: Int,
    val icon: WhatsNewIcon,
)

@Composable
fun WhatsNewView(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val colors = rememberAdaptiveColors()
    val isDark = isSystemInDarkTheme()
    val reduceMotion = MotionPolicy.reduceMotion
    var appearAnimation by remember { mutableStateOf(reduceMotion) }

    val features = remember {
        listOf(
            WhatsNewFeature(R.string.whats_new_groups_title, R.string.whats_new_groups_description, WhatsNewIcon.Attachment(AttachmentIcon.GROUPS)),
            WhatsNewFeature(R.string.whats_new_for_you_title, R.string.whats_new_for_you_description, WhatsNewIcon.Vector(Icons.Default.Explore)),
            WhatsNewFeature(R.string.whats_new_chat_230_title, R.string.whats_new_chat_230_description, WhatsNewIcon.Vector(Icons.AutoMirrored.Filled.Chat)),
            WhatsNewFeature(R.string.whats_new_echoes_title, R.string.whats_new_echoes_description, WhatsNewIcon.Vector(Icons.Default.GraphicEq)),
            WhatsNewFeature(R.string.whats_new_stories_230_title, R.string.whats_new_stories_230_description, WhatsNewIcon.PersonalAndGroupStoryRings),
            WhatsNewFeature(R.string.whats_new_create_230_title, R.string.whats_new_create_230_description, WhatsNewIcon.Vector(Icons.Default.Crop)),
            WhatsNewFeature(R.string.whats_new_feed_reels_title, R.string.whats_new_feed_reels_description, WhatsNewIcon.Vector(Icons.Default.PlayCircle)),
            WhatsNewFeature(R.string.whats_new_profile_230_title, R.string.whats_new_profile_230_description, WhatsNewIcon.Vector(Icons.Default.Person)),
            WhatsNewFeature(R.string.whats_new_nova_230_title, R.string.whats_new_nova_230_description, WhatsNewIcon.Vector(Icons.Default.AutoAwesome)),
            WhatsNewFeature(R.string.whats_new_offline_230_title, R.string.whats_new_offline_230_description, WhatsNewIcon.Vector(Icons.Default.CloudOff)),
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

            Text(
                stringResource(R.string.whats_new_section_title),
                color = colors.secondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

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

            Text(
                stringResource(R.string.whats_new_note_closing),
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
                    stringResource(R.string.whats_new_button),
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
            when (val icon = feature.icon) {
                is WhatsNewIcon.Vector -> Icon(
                    imageVector = icon.image,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(18.dp),
                )
                is WhatsNewIcon.Attachment -> AttachmentIconView(
                    icon = icon.icon,
                    size = AttachmentIconMetrics.whatsNew,
                    tintColor = colors.primary,
                )
                WhatsNewIcon.PersonalAndGroupStoryRings -> WhatsNewDualStoryRingsIcon()
            }
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

/** Personal atrás (3 audiencias) + grupo delante (1 corte con shift) ≡ iOS. */
@Composable
private fun WhatsNewDualStoryRingsIcon() {
    val ringSize = 18.dp
    val lineWidth = 2.dp
    val overlap = 6.5.dp
    val rowWidth = ringSize * 2 - overlap
    val avatarSize = ringSize - lineWidth * 2 - 1.dp
    val demoAudiences = listOf<String?>(null, "bestfriends", "mutuals")

    Box(
        modifier = Modifier
            .width(rowWidth)
            .height(ringSize),
    ) {
        // Personal (atrás) con cutout donde solapa el de grupo.
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(ringSize)
                .zIndex(0f)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val cutCenter = Offset(
                        x = center.x + (ringSize - overlap).toPx(),
                        y = center.y,
                    )
                    drawCircle(
                        color = androidx.compose.ui.graphics.Color.Black,
                        radius = (ringSize + 3.dp).toPx() / 2f,
                        center = cutCenter,
                        blendMode = BlendMode.Clear,
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            StorySegmentedRing(
                storyCount = 3,
                hasStory = true,
                hasUnseenStory = true,
                storyViewedStatus = listOf(false, false, false),
                storyAudiences = demoAudiences,
                isOwnStory = false,
                ringSize = ringSize,
                lineWidth = lineWidth,
                hapticsEnabled = false,
            )
        }
        // Grupo delante (novedad): 1 corte con shift de las 3 audiencias + avatar.
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = ringSize - overlap)
                .size(ringSize)
                .zIndex(1f),
            contentAlignment = Alignment.Center,
        ) {
            StorySegmentedRing(
                storyCount = 1,
                hasStory = true,
                hasUnseenStory = true,
                storyViewedStatus = listOf(false),
                storyAudiences = listOf(null),
                nestedStoryAudiences = listOf(demoAudiences),
                isOwnStory = false,
                ringSize = ringSize,
                lineWidth = lineWidth,
                hapticsEnabled = false,
                modifier = Modifier.storyRingGapMask(avatarSize = avatarSize),
            )
            GroupChatAvatar(image = "", size = avatarSize)
        }
    }
}
