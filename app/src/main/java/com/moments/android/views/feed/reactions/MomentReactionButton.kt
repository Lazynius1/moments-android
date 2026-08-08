package com.moments.android.views.feed.reactions

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.moments.android.R
import com.moments.android.extensions.fromHex
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.services.content.FeedMoment
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.performance.MotionPolicy
import com.moments.android.services.social.AffinityInteractionType
import com.moments.android.services.social.AffinityTracker
import com.moments.android.utilities.HapticManager
import com.moments.android.utilities.MomentsFormat
import com.moments.android.utilities.MomentsPressDefaults
import com.moments.android.utilities.MomentsPressSpec
import com.moments.android.utilities.momentsPress
import com.moments.android.views.components.RailCountBadge
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/**
 * Port de `EpicReactionButton` (`MomentReactionButton.swift`).
 * Alias público: [MomentReactionButton] (nombre de archivo Android).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MomentReactionButton(
    moment: FeedMoment,
    showCount: Boolean = true,
    sizeDp: Float = ReactionButtonMetrics.buttonSizeDp,
    emojiSizeSp: Float = ReactionButtonMetrics.emojiSizeSp,
    pickerXOffset: Float = 0f,
    modifier: Modifier = Modifier,
) {
    EpicReactionButton(
        moment = moment,
        showCount = showCount,
        sizeDp = sizeDp,
        emojiSizeSp = emojiSizeSp,
        pickerXOffset = pickerXOffset,
        modifier = modifier,
    )
}

/** Compatibilidad con call sites antiguos. */
@Composable
fun MomentReactionButton(
    momentId: String,
    authorId: String,
    reactionCount: Int,
    hideLikeCounts: Boolean,
    modifier: Modifier = Modifier,
    sizeDp: Float = ReactionButtonMetrics.buttonSizeDp,
    emojiSizeSp: Float = ReactionButtonMetrics.emojiSizeSp,
) {
    MomentReactionButton(
        moment = FeedMoment(
            id = momentId,
            authorId = authorId,
            username = "",
            content = "",
            timestamp = 0L,
            profileImagePath = null,
            location = null,
            mediaItems = emptyList(),
            aspectRatio = null,
            commentCount = 0,
            reactionCount = reactionCount,
            hideLikeCounts = hideLikeCounts,
            disableComments = false,
        ),
        showCount = !hideLikeCounts,
        sizeDp = sizeDp,
        emojiSizeSp = emojiSizeSp,
        modifier = modifier,
    )
}

@Composable
fun EpicReactionButton(
    moment: FeedMoment,
    showCount: Boolean = true,
    sizeDp: Float = 44f,
    emojiSizeSp: Float = 24f,
    pickerXOffset: Float = 0f,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val scope = rememberCoroutineScope()
    val firestore = remember { FirestoreService() }
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    val ink = Color.fromHex("0B1215")

    var showReactionPicker by remember { mutableStateOf(false) }
    var currentReaction by remember(moment.id) { mutableStateOf<ReactionType?>(null) }
    var reactionCount by remember(moment.id) { mutableIntStateOf(moment.reactionCount) }
    var hasReacted by remember(moment.id) { mutableStateOf(false) }
    var isPressed by remember { mutableStateOf(false) }
    var showParticles by remember { mutableStateOf(false) }
    var pulseScale by remember { mutableFloatStateOf(1f) }
    var rotationAngle by remember { mutableFloatStateOf(0f) }
    var showRipple by remember { mutableStateOf(false) }
    var showReactionsSheet by remember { mutableStateOf(false) }
    var listener by remember { mutableStateOf<ListenerRegistration?>(null) }

    fun updateReactionState(reactions: Map<String, List<String>>) {
        val userId = uid ?: return
        var userReaction: ReactionType? = null
        for ((raw, userIds) in reactions) {
            if (userIds.contains(userId)) {
                userReaction = ReactionType.fromRaw(raw)
                break
            }
        }
        val total = reactions.values.sumOf { it.size }
        hasReacted = userReaction != null
        currentReaction = userReaction
        reactionCount = total
    }

    DisposableEffect(moment.id, moment.authorId) {
        val registration = firestore.listenToReactions(moment.id, moment.authorId) { reactions ->
            scope.launch {
                updateReactionState(reactions)
            }
        }
        listener = registration
        onDispose {
            registration.remove()
            listener = null
        }
    }

    LaunchedEffect(hasReacted) {
        if (!hasReacted || MotionPolicy.reduceMotion) return@LaunchedEffect
        pulseScale = 1.2f
        delay(150)
        pulseScale = 1f
        rotationAngle = 10f
        delay(300)
        rotationAngle = 0f
    }

    fun showPickerWithAnimation() {
        HapticManager.shared.mediumImpact()
        showReactionPicker = true
        showRipple = true
        scope.launch {
            delay(100)
            showRipple = false
        }
    }

    fun hidePickerWithAnimation() {
        showReactionPicker = false
    }

    fun addReactionWithAnimation(reactionType: ReactionType) {
        HapticManager.shared.notification(HapticManager.NotificationType.SUCCESS)
        showReactionPicker = false
        showParticles = true
        scope.launch {
            delay(800)
            showParticles = false
            showRipple = true
            delay(100)
            showRipple = false
        }
        hasReacted = true
        currentReaction = reactionType
        reactionCount += 1
        AffinityTracker.trackInteraction(AffinityInteractionType.MOMENT_REACTION, moment.authorId)
        val userId = uid ?: return
        scope.launch {
            runCatching {
                firestore.addReaction(moment.id, reactionType.rawValue, userId, moment.authorId)
            }
        }
    }

    fun removeReactionWithAnimation() {
        HapticManager.shared.lightImpact()
        val type = currentReaction ?: return
        hasReacted = false
        reactionCount = (reactionCount - 1).coerceAtLeast(0)
        val userId = uid ?: return
        scope.launch {
            runCatching {
                firestore.removeReaction(moment.id, type.rawValue, userId, moment.authorId)
            }
        }
    }

    val accessibilityLabel = if (hasReacted && currentReaction != null) {
        stringResource(
            R.string.feed_reaction_accessibility_selected,
            currentReaction!!.filledIcon,
            reactionCount,
        )
    } else {
        stringResource(R.string.feed_reaction_accessibility_default, reactionCount)
    }
    val accessibilityHint = stringResource(R.string.feed_reaction_accessibility_hint)
    val density = LocalDensity.current
    val pickerPopupOffset = remember(pickerXOffset, density) {
        IntOffset(
            x = with(density) { pickerXOffset.dp.roundToPx() },
            // iOS EpicReactionPickerView.offset(y: -90) sobre el botón.
            y = with(density) { (-90).dp.roundToPx() },
        )
    }

    // Layout fijo sizeDp (como iOS) — sin padding extra que desplace el rail.
    Box(modifier.size(sizeDp.dp)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (showRipple) {
                Box(
                    Modifier
                        .size((sizeDp * 1.6f).dp)
                        .scale(1.5f)
                        .background(
                            (currentReaction?.color ?: Color.White).copy(0.3f),
                            CircleShape,
                        ),
                )
            }

            Box(
                Modifier
                    .size(sizeDp.dp)
                    .scale(if (isPressed) 0.85f else if (hasReacted) 1.15f else 1f)
                    .shadow(
                        elevation = if (hasReacted) 8.dp else 4.dp,
                        shape = CircleShape,
                        clip = false,
                        ambientColor = if (hasReacted) {
                            (currentReaction?.color ?: Color.Black).copy(0.4f)
                        } else {
                            Color.Black.copy(0.1f)
                        },
                        spotColor = if (hasReacted) {
                            (currentReaction?.color ?: Color.Black).copy(0.4f)
                        } else {
                            Color.Black.copy(0.1f)
                        },
                    )
                    .momentsChromeGlass(CircleShape, interactive = true)
                    .combinedClickable(
                        onClick = {
                            if (hasReacted) removeReactionWithAnimation()
                            else showPickerWithAnimation()
                        },
                        onLongClick = { showPickerWithAnimation() },
                        onClickLabel = accessibilityLabel,
                    )
                    .semantics {
                        contentDescription = "$accessibilityLabel. $accessibilityHint"
                        role = Role.Button
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (hasReacted) {
                    val reactionColor = currentReaction?.color ?: Color.Red
                    Text(
                        text = currentReaction?.filledIcon ?: "❤️",
                        style = TextStyle(
                            fontSize = emojiSizeSp.sp,
                            fontWeight = FontWeight.Black,
                            brush = Brush.linearGradient(
                                listOf(reactionColor, reactionColor.copy(0.7f)),
                            ),
                        ),
                        modifier = Modifier
                            .scale(pulseScale)
                            .rotate(rotationAngle),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.FavoriteBorder,
                        contentDescription = null,
                        tint = if (isDark) Color.White else ink,
                        modifier = Modifier.size(emojiSizeSp.dp),
                    )
                }

                if (showParticles) {
                    repeat(6) { index ->
                        ParticleView(
                            color = currentReaction?.color ?: Color.White,
                            angle = index * 60.0,
                            show = showParticles,
                        )
                    }
                }
            }

            if (showCount && reactionCount > 0) {
                // Hit amplio DENTRO del 44dp (no mueve el rail). Esquina = estadísticas.
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(32.dp)
                        .zIndex(2f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                HapticManager.shared.lightImpact()
                                showReactionsSheet = true
                            },
                        ),
                )
                RailCountBadge(
                    text = MomentsFormat.count(reactionCount, MomentsFormat.CountStyle.SOCIAL_METRIC),
                    background = currentReaction?.color ?: Color.Gray.copy(0.6f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .zIndex(3f),
                )
            }
        }

        // Popup fuera del clip del rail chrome (iOS overlay no se clippea igual).
        if (showReactionPicker) {
            Popup(
                alignment = Alignment.BottomCenter,
                offset = pickerPopupOffset,
                onDismissRequest = { hidePickerWithAnimation() },
                properties = PopupProperties(
                    focusable = true,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true,
                ),
            ) {
                EpicReactionPickerView(
                    onReactionSelected = { addReactionWithAnimation(it) },
                    onClose = { hidePickerWithAnimation() },
                )
            }
        }
    }

    if (showReactionsSheet) {
        ReactionsListSheet(
            momentId = moment.id,
            authorId = moment.authorId,
            onDismiss = { showReactionsSheet = false },
        )
    }
}

@Composable
private fun ParticleView(
    color: Color,
    angle: Double,
    show: Boolean,
) {
    val offsetAnim = remember { Animatable(0f) }
    val opacityAnim = remember { Animatable(1f) }
    val scaleAnim = remember { Animatable(1f) }

    LaunchedEffect(show) {
        if (show) {
            offsetAnim.snapTo(0f)
            opacityAnim.snapTo(1f)
            scaleAnim.snapTo(1f)
            launch { offsetAnim.animateTo(30f, tween(800, easing = FastOutSlowInEasing)) }
            launch { opacityAnim.animateTo(0f, tween(800)) }
            launch { scaleAnim.animateTo(0.3f, tween(800)) }
        } else {
            offsetAnim.snapTo(0f)
            opacityAnim.snapTo(1f)
            scaleAnim.snapTo(1f)
        }
    }

    val rad = Math.toRadians(angle)
    Box(
        Modifier
            .offset(
                x = (cos(rad) * offsetAnim.value).dp,
                y = (sin(rad) * offsetAnim.value).dp,
            )
            .size(6.dp)
            .scale(scaleAnim.value)
            .background(color.copy(alpha = opacityAnim.value), CircleShape),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FloatingReactionItemView(
    reaction: ReactionType,
    index: Int,
    onClick: () -> Unit,
) {
    val infinite = rememberInfiniteTransition(label = "floatingReaction")
    val floatY by infinite.animateFloat(
        initialValue = 4f,
        targetValue = if (MotionPolicy.reduceMotion) 4f else -4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = (1400 + index * 80), easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "floatY",
    )

    Text(
        text = reaction.filledIcon,
        fontSize = 32.sp,
        style = TextStyle(
            shadow = Shadow(color = Color.Black.copy(0.18f), blurRadius = 3f),
        ),
        modifier = Modifier
            .offset(y = floatY.dp)
            .momentsPress(
                MomentsPressSpec(scale = 0.82f, haptic = MomentsPressDefaults.PressHaptic.NONE),
            )
            .combinedClickable(
                onClick = {
                    HapticManager.shared.lightImpact()
                    onClick()
                },
            )
            .padding(2.dp),
    )
}

/** Port de `EpicReactionPickerView`. */
@Composable
fun EpicReactionPickerView(
    onReactionSelected: (ReactionType) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    @Suppress("UNUSED_PARAMETER")
    val unusedClose = onClose
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    val usageTracker = remember(uid) { UserReactionUsageTracker(context, uid) }
    val ordered = remember { usageTracker.getReactionsOrderedByUsage() }
    var appearScales by remember { mutableStateOf(List(16) { 0.3f }) }

    LaunchedEffect(Unit) {
        ordered.indices.forEach { index ->
            delay(index * 20L)
            appearScales = appearScales.toMutableList().also { it[index] = 1f }
        }
    }

    Row(
        modifier
            .width(280.dp)
            .shadow(
                elevation = 24.dp,
                shape = RoundedCornerShape(percent = 50),
                clip = false,
                ambientColor = Color.Black.copy(if (isDark) 0.24f else 0.12f),
                spotColor = Color.Black.copy(if (isDark) 0.24f else 0.12f),
            )
            .momentsChromeGlass(RoundedCornerShape(percent = 50), interactive = true)
            .clip(RoundedCornerShape(percent = 50))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ordered.forEachIndexed { index, reaction ->
            Box(Modifier.scale(appearScales.getOrElse(index) { 1f })) {
                FloatingReactionItemView(reaction = reaction, index = index) {
                    usageTracker.incrementUsage(reaction)
                    onReactionSelected(reaction)
                }
            }
        }
    }
}
