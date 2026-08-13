package com.moments.android.views.story.storyviewer

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.fetchCustomListDetails
import com.moments.android.utilities.HapticManager
import com.moments.android.utilities.MomentsFormat
import com.moments.android.views.components.AudienceIconView
import com.moments.android.views.creator.audienceselector.ContentAudience
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.messaging.components.messageBubbleBackground
import com.moments.android.views.messaging.components.messageTextColor
import com.moments.android.views.messaging.components.replyBarSecondaryText
import com.moments.android.views.story.StoryReaction
import com.moments.android.views.story.StoryViewer
import com.moments.android.views.story.latestPerUser
import kotlinx.coroutines.delay

private object StoryAudienceBottomInfo {
    fun normalizedAudience(audience: String?): String =
        audience?.trim()?.lowercase().orEmpty().ifEmpty { "everyone" }
}

/** Port de `StoryReactionsStrip`. */
@Composable
fun StoryReactionsStrip(
    reactions: List<String>,
    showReactions: Boolean,
    onReaction: (String) -> Unit,
    onMoreReactions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val view = LocalView.current
    val primaryText = if (isDark) Color.White else Color.Black
    val capsuleShadow = if (isDark) 0.24f else 0.12f
    // ≡ Color.primary.opacity(dark ? 0.12 : 0.08)
    val plusStroke = if (isDark) Color.White.copy(0.12f) else Color.Black.copy(0.08f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        Text(
            stringResource(R.string.story_context_menu_scroll_reactions),
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 10.sp,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .shadow(
                    elevation = 24.dp,
                    shape = RoundedCornerShape(percent = 50),
                    ambientColor = Color.Black.copy(capsuleShadow),
                    spotColor = Color.Black.copy(capsuleShadow),
                )
                .momentsChromeGlass(RoundedCornerShape(percent = 50), interactive = true)
                .clip(RoundedCornerShape(percent = 50))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            reactions.forEachIndexed { index, reaction ->
                // ≡ .scaleEffect + spring(response: 0.3).delay(index * 0.03)
                var scaleTarget by remember(reaction) { mutableFloatStateOf(if (showReactions) 1f else 0.5f) }
                LaunchedEffect(showReactions, index) {
                    delay(index * 30L)
                    scaleTarget = if (showReactions) 1f else 0.5f
                }
                val scale by animateFloatAsState(
                    targetValue = scaleTarget,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = 438f, // ≈ response 0.3
                    ),
                    label = "reactionScale$index",
                )
                Text(
                    reaction,
                    fontSize = 30.sp,
                    modifier = Modifier
                        .scale(scale)
                        .clickable { onReaction(reaction) },
                )
            }
            // ≡ plus: momentsChromeGlass Circle + stroke + haptic
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .momentsChromeGlass(CircleShape, interactive = true)
                    .border(1.dp, plusStroke, CircleShape)
                    .clickable {
                        HapticManager.shared.lightImpact(view)
                        onMoreReactions()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    tint = primaryText,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** Port de `StoryNoInteractionsNotice`. */
@Composable
fun StoryNoInteractionsNotice(modifier: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme()
    Text(
        stringResource(R.string.stories_no_interactions),
        color = if (isDark) Color.White.copy(0.68f) else Color.Black.copy(0.68f),
        textAlign = TextAlign.Center,
        fontSize = 14.sp,
        modifier = modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

/**
 * Port de `StoryNavigationTouchAreas`.
 * [shouldSuppressNavigationTapAt] recibe el punto local al canvas (px).
 */
@Composable
fun StoryNavigationTouchAreas(
    sideWidthFraction: Float = StoryGestureCoordinator.NAVIGATION_SIDE_WIDTH_FRACTION,
    shouldSuppressNavigationTapAt: (Offset) -> Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val wPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val sidePx = (wPx * sideWidthFraction).coerceAtLeast(1f)
        val sideDp = maxWidth * sideWidthFraction
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .width(sideDp)
                    .fillMaxHeight()
                    .then(
                        if (enabled) {
                            Modifier.pointerInput(sidePx, wPx) {
                                detectTapGestures { local ->
                                    // ≡ value.location en la banda izquierda
                                    if (!shouldSuppressNavigationTapAt(local)) onPrevious()
                                }
                            }
                        } else {
                            Modifier
                        },
                    ),
            )
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .width(sideDp)
                    .fillMaxHeight()
                    .then(
                        if (enabled) {
                            Modifier.pointerInput(sidePx, wPx) {
                                detectTapGestures { local ->
                                    // ≡ geometry.width - sideWidth + value.location
                                    val canvasLocal = Offset(
                                        x = wPx - sidePx + local.x,
                                        y = local.y,
                                    )
                                    if (!shouldSuppressNavigationTapAt(canvasLocal)) onNext()
                                }
                            }
                        } else {
                            Modifier
                        },
                    ),
            )
        }
    }
}

/** Port de `StoryOwnStoryBottomBar`. */
@Composable
fun StoryOwnStoryBottomBar(
    viewers: List<StoryViewer>,
    reactions: List<StoryReaction>,
    audience: String?,
    expirationHours: Int?,
    authorId: String = "",
    customListId: String? = null,
    onViewActivity: () -> Unit,
    onReactionsActivity: () -> Unit,
    showsShare: Boolean = false,
    onShare: () -> Unit = {},
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val adaptive = rememberAdaptiveColors()
    val isDark = isSystemInDarkTheme()
    val messageColor = adaptive.messageTextColor
    val firestore = remember { FirestoreService() }
    // ≡ labelShadowColor: dark black@0.5 / light clear
    val labelShadow = if (isDark) {
        Shadow(color = Color.Black.copy(0.5f), offset = Offset(0f, 1f), blurRadius = 4f)
    } else {
        null
    }
    val labelStyle = TextStyle(shadow = labelShadow)

    var audienceListName by remember { mutableStateOf<String?>(null) }
    val normalized = StoryAudienceBottomInfo.normalizedAudience(audience)
    val isEveryoneAudience = normalized == "everyone"
    val displayAudience = ContentAudience.fromAudienceValue(audience)

    LaunchedEffect(customListId, authorId, normalized) {
        if (normalized == "customlist" && !customListId.isNullOrBlank() && authorId.isNotBlank()) {
            audienceListName = runCatching {
                firestore.fetchCustomListDetails(customListId, authorId).name
            }.getOrNull()
        } else {
            audienceListName = null
        }
    }

    val audienceTitle = when (normalized) {
        "mutuals", "mutual" -> stringResource(R.string.audience_type_mutuals)
        "bestfriends", "best_friends", "best-friends" -> stringResource(R.string.audience_type_best_friends)
        "customlist" -> audienceListName ?: stringResource(R.string.audience_type_custom_list)
        "custom" -> stringResource(R.string.audience_type_custom)
        "onlyme", "only_me", "only-me" -> stringResource(R.string.audience_type_only_me)
        else -> stringResource(R.string.audience_type_everyone)
    }

    val recentViewers = remember(viewers) {
        viewers.sortedByDescending { it.timestamp.time }.take(3)
    }
    val uniqueReactions = remember(reactions) { reactions.latestPerUser() }
    val reactionCount = uniqueReactions.size
    val distinctEmojis = remember(uniqueReactions) {
        val seen = linkedSetOf<String>()
        val result = mutableListOf<String>()
        for (item in uniqueReactions) {
            val emoji = item.reaction.trim()
            if (emoji.isEmpty() || !seen.add(emoji)) continue
            result += emoji
            if (result.size >= 3) break
        }
        result
    }
    val durationHours = if (expirationHours == 48) 48 else 24
    val durationLabel = stringResource(R.string.story_editor_expiration_option, durationHours)

    val activityA11y = when (viewers.size) {
        0 -> stringResource(R.string.stories_own_bottom_no_views)
        1 -> stringResource(R.string.stories_own_bottom_views_one)
        else -> stringResource(R.string.stories_own_bottom_views_many, viewers.size)
    }
    val audienceA11y = stringResource(
        R.string.stories_own_bottom_audience_duration_a11y,
        audienceTitle,
        durationLabel,
    )
    val reactionsA11y = stringResource(R.string.stories_own_bottom_reactions_count, reactionCount)

    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp)
            .padding(top = if (compact) 0.dp else 4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        // Activity
        Column(
            Modifier
                .weight(1f)
                .clickable(onClick = onViewActivity)
                .semantics { contentDescription = activityA11y }
                .padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 6.dp),
        ) {
            if (recentViewers.isNotEmpty()) {
                // ≡ HStack(spacing: -8) + reversedMask círculo solapado
                val density = LocalDensity.current
                Row(
                    Modifier.height(if (compact) 28.dp else 32.dp),
                    horizontalArrangement = Arrangement.spacedBy((-8).dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    recentViewers.forEachIndexed { index, viewer ->
                        Box(
                            Modifier
                                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                .drawWithContent {
                                    drawContent()
                                    if (index < recentViewers.lastIndex) {
                                        val cutR = with(density) { 15.5.dp.toPx() }
                                        val cutX = with(density) { 20.dp.toPx() }
                                        drawCircle(
                                            color = Color.Black,
                                            radius = cutR,
                                            center = Offset(size.width / 2f + cutX, size.height / 2f),
                                            blendMode = BlendMode.Clear,
                                        )
                                    }
                                },
                        ) {
                            ViewerAvatarChip(viewer = viewer)
                        }
                    }
                }
            } else {
                // ≡ Image("StoryActivityEmptyIcon") 36×36 template
                Icon(
                    painter = painterResource(R.drawable.story_activity_empty_icon),
                    contentDescription = null,
                    tint = messageColor,
                    modifier = Modifier
                        .size(if (compact) 28.dp else 36.dp)
                        .then(
                            if (labelShadow != null) {
                                Modifier.shadow(4.dp, ambientColor = Color.Black.copy(0.5f), spotColor = Color.Black.copy(0.5f))
                            } else {
                                Modifier
                            },
                        ),
                )
            }
            Text(
                stringResource(R.string.stories_own_bottom_activity),
                color = messageColor,
                fontWeight = FontWeight.Medium,
                fontSize = if (compact) 10.sp else 12.sp,
                maxLines = 1,
                style = labelStyle,
            )
        }

        // Audience
        Column(
            Modifier
                .weight(1f)
                .semantics { contentDescription = audienceA11y }
                .padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 6.dp),
        ) {
            // Mantener el slot alineado con Actividad, pero con un símbolo de
            // audiencia ópticamente más discreto que el avatar/estado vecino.
            Box(
                modifier = Modifier.height(if (compact) 28.dp else 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                AudienceIconView(
                    audience = displayAudience,
                    size = if (compact) 22.dp else 26.dp,
                    tintColor = messageColor,
                )
            }
            Text(
                audienceTitle,
                color = messageColor,
                fontWeight = FontWeight.Medium,
                fontSize = if (compact) 10.sp else 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 88.dp),
                textAlign = TextAlign.Center,
                style = labelStyle,
            )
            Text(
                durationLabel,
                color = messageColor.copy(0.92f),
                fontSize = if (compact) 9.sp else 11.sp,
                maxLines = 1,
                style = labelStyle,
            )
        }

        if (showsShare && isEveryoneAudience) {
            Column(
                Modifier
                    .weight(1f)
                    .clickable(onClick = onShare)
                    .padding(horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 6.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.stories_own_bottom_share),
                    tint = messageColor,
                    modifier = Modifier
                        .size(if (compact) 19.dp else 22.dp)
                        .height(if (compact) 28.dp else 32.dp),
                )
                Text(
                    stringResource(R.string.stories_own_bottom_share),
                    color = messageColor,
                    fontWeight = FontWeight.Medium,
                    fontSize = if (compact) 10.sp else 12.sp,
                    maxLines = 1,
                    style = labelStyle,
                )
            }
        }

        if (reactionCount > 0) {
            Column(
                Modifier
                    .weight(1f)
                    .clickable(onClick = onReactionsActivity)
                    .semantics { contentDescription = reactionsA11y }
                    .padding(horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 6.dp),
            ) {
                Box(
                    Modifier.height(if (compact) 28.dp else 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        distinctEmojis.isEmpty() -> Text("❤️", fontSize = 22.sp)
                        distinctEmojis.size == 1 -> Text(distinctEmojis[0], fontSize = 22.sp)
                        else -> {
                            Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                                distinctEmojis.forEachIndexed { index, emoji ->
                                    Text(
                                        emoji,
                                        fontSize = if (index == 0) 22.sp else 18.sp,
                                        style = TextStyle(
                                            shadow = if (isDark) {
                                                Shadow(Color.Black.copy(0.5f), Offset(0f, 1f), 3f)
                                            } else {
                                                null
                                            },
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
                Text(
                    MomentsFormat.count(reactionCount, MomentsFormat.CountStyle.SOCIAL_METRIC),
                    color = messageColor,
                    fontWeight = FontWeight.Medium,
                    fontSize = if (compact) 10.sp else 12.sp,
                    style = labelStyle,
                )
            }
        }
    }
}

@Composable
private fun ViewerAvatarChip(
    viewer: StoryViewer,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val adaptive = rememberAdaptiveColors()
    val stroke = if (isDark) Color.Black.copy(0.35f) else Color.Black.copy(0.12f)
    Box(
        modifier
            .size(28.dp)
            .clip(CircleShape)
            .border(1.5.dp, stroke, CircleShape)
            .background(adaptive.messageBubbleBackground),
        contentAlignment = Alignment.Center,
    ) {
        if (!viewer.profileImagePath.isNullOrBlank()) {
            AsyncImage(
                model = viewer.profileImagePath,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = adaptive.replyBarSecondaryText,
                modifier = Modifier.size(16.dp).padding(2.dp),
            )
        }
    }
}
