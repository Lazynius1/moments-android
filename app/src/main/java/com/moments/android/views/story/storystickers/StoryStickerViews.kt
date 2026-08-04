package com.moments.android.views.story.storystickers

import android.content.Intent
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import com.moments.android.views.components.MomentsCircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.moments.android.R
import com.moments.android.coordinators.AsyncProfileImageView
import com.moments.android.models.StickerData
import com.moments.android.utilities.MomentsFormat
import com.moments.android.views.components.InteractiveAudioStickerView
import com.moments.android.views.components.StickerCountdownCardView
import com.moments.android.views.components.StickerEmojiSliderCardView
import com.moments.android.views.components.StickerHashtagCardView
import com.moments.android.views.components.StickerLinkCardView
import com.moments.android.views.components.StickerLocationCardView
import com.moments.android.views.components.StickerMentionCardView
import com.moments.android.views.components.StickerTimeCardView
import com.moments.android.views.components.emojiSliderHasPrompt
import com.moments.android.views.components.emojiSliderRenderingSize
import com.moments.android.views.components.emojiSliderTrackFrame
import com.moments.android.views.components.emojiSliderTrackMetrics
import com.moments.android.views.components.normalizedStickerURL
import com.moments.android.views.components.stickerHostLabel
import com.moments.android.views.explore.ExploreView
import com.moments.android.views.feed.maps.LocationMapView
import com.moments.android.views.feed.moments.FeedMomentCardLayout
import com.moments.android.views.shared.MomentsModalSheet
import com.moments.android.views.story.InteractiveQuizSticker
import com.moments.android.views.story.QuestionResponseStoryStickerCardView
import com.moments.android.views.story.QuestionResponsesView
import com.moments.android.views.story.StoryDeckGestureGate
import com.moments.android.views.story.storyviewer.LocalStoryStickerHitTesting
import com.moments.android.views.story.storyviewer.StoryGestureSuppressionScope
import com.moments.android.views.story.storyviewer.StoryViewerLayoutHelpers
import com.moments.android.views.story.storyviewer.emojiSliderVotePan
import com.moments.android.views.story.storyviewer.storyDeckInteractionExclusion
import com.moments.android.views.components.AnimatedMomentsCardStickerHeaderSurface
import com.moments.android.views.components.AnimatedMomentsCardStickerSurface
import com.moments.android.views.components.momentsStickerInk
import com.moments.android.views.components.momentsStickerInverseInk
import com.moments.android.views.components.momentsStickerSurface
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date
import kotlin.math.max
import kotlin.math.roundToInt

/** Port de `InteractivePollData`. */
data class InteractivePollData(
    val pollData: List<String>,
    val storyId: String,
    val stickerId: String,
)

private data class PollVoteState(
    val selectedOption: Int? = null,
    val counts: Map<Int, Int> = mapOf(0 to 0, 1 to 0),
) {
    val hasVoted: Boolean get() = selectedOption != null
    val totalVotes: Int get() = counts.values.sum()
    fun percentage(option: Int): Float =
        if (totalVotes == 0) 0f else (counts[option] ?: 0).toFloat() / totalVotes * 100f
}

/** Misma colección Firestore de votos que el viewer de iOS. */
private object StoryPollVoteStore {
    private val db get() = FirebaseFirestore.getInstance()

    private fun votes(userId: String, storyId: String, stickerId: String) = db
        .collection("users").document(userId)
        .collection("stories").document(storyId)
        .collection("pollVotes").document(stickerId)
        .collection("votes")

    suspend fun load(userId: String, storyId: String, stickerId: String, viewerId: String?): PollVoteState {
        val counts = mutableMapOf(0 to 0, 1 to 0)
        val documents = votes(userId, storyId, stickerId).get().await().documents
        documents.forEach { document ->
            val option = (document.get("option") as? Number)?.toInt()
            if (option != null && option in 0..1) counts[option] = (counts[option] ?: 0) + 1
        }
        val selected = viewerId?.let { viewer ->
            (votes(userId, storyId, stickerId).document(viewer).get().await().get("option") as? Number)?.toInt()
        }
        return PollVoteState(selectedOption = selected, counts = counts)
    }

    suspend fun submit(
        userId: String,
        storyId: String,
        stickerId: String,
        viewerId: String,
        option: Int,
    ): Boolean {
        val reference = votes(userId, storyId, stickerId).document(viewerId)
        // ≡ iOS handlePollVote: si ya existe, no reescribe
        if (reference.get().await().exists()) return false
        reference.set(
            mapOf(
                "userId" to viewerId,
                "option" to option,
                "timestamp" to FieldValue.serverTimestamp(),
            ),
        ).await()
        return true
    }
}

/**
 * Misma fórmula que el título del poll / iOS `multilineTextAlignment(.center)`:
 * ancho completo + [TextAlign.Center] (tanto placeholder [Text] como el valor tipado).
 */
@Composable
private fun StickerCenteredTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    color: Color,
    placeholderColor: Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    val style = TextStyle(
        color = color,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        fontSize = fontSize,
    )
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = style,
        cursorBrush = SolidColor(color),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                // Misma fórmula que el título no editable: Text + fillMaxWidth + Center
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        color = placeholderColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = fontSize,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                innerTextField()
            }
        },
    )
}

/**
 * Port de `InteractivePollSticker`: tarjeta inline y voto persistido por usuario.
 * `onPollDataChange` cubre el modo de edición que Swift recibe mediante Binding.
 */
@Composable
fun InteractivePollSticker(
    pollData: List<String>,
    storyId: String,
    userId: String,
    stickerId: String,
    styleVariant: Int = 0,
    isEditingInline: Boolean = false,
    onPollDataChange: (List<String>) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val viewerId = FirebaseAuth.getInstance().currentUser?.uid
    val scope = rememberCoroutineScope()
    var voteState by remember(storyId, stickerId, viewerId) { mutableStateOf(PollVoteState()) }
    val isPreview = storyId.isBlank() || storyId == "preview" || userId == "preview"
    val isDark = isSystemInDarkTheme()
    val isLight = styleVariant % 6 == 0
    val ink = if (isLight) momentsStickerInk(isDark) else Color.White
    val headerInk = if (isLight) momentsStickerInverseInk(isDark) else Color.White
    val title = pollData.getOrNull(0).takeUnless { it.isNullOrBlank() }
        ?: stringResource(R.string.stickerview_poll_placeholder)

    LaunchedEffect(storyId, userId, stickerId, viewerId) {
        if (!isPreview) {
            voteState = runCatching {
                StoryPollVoteStore.load(userId, storyId, stickerId, viewerId)
            }.getOrDefault(voteState)
        }
    }

    Box(
        modifier = modifier
            .width(300.dp)
            .height(172.dp) // ≡ StickerOverlayView.swift poll frame
            .clip(RoundedCornerShape(24.dp)),
    ) {
        AnimatedMomentsCardStickerSurface(
            styleVariant = styleVariant,
            isDark = isDark,
            modifier = Modifier.matchParentSize(),
        )
        Column(Modifier.fillMaxSize()) {
            // Header pregunta — centrado H+V (≡ iOS multilineTextAlignment .center)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedMomentsCardStickerHeaderSurface(
                    styleVariant = styleVariant,
                    isDark = isDark,
                    modifier = Modifier.matchParentSize(),
                )
                if (isEditingInline) {
                    StickerCenteredTextField(
                        value = pollData.getOrNull(0).orEmpty(),
                        onValueChange = { onPollDataChange(pollData.replaceAt(0, it)) },
                        placeholder = stringResource(R.string.story_editor_poll_question_prompt),
                        color = headerInk,
                        placeholderColor = headerInk.copy(alpha = 0.45f),
                        fontSize = 18.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp),
                    )
                } else {
                    Text(
                        text = title,
                        color = headerInk,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        fontSize = 18.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp),
                    )
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                repeat(2) { index ->
                    val optionIndex = index + 1
                    if (isEditingInline) {
                        val option = pollData.getOrNull(optionIndex).orEmpty()
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (isLight) ink.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.18f),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            StickerCenteredTextField(
                                value = option,
                                onValueChange = { onPollDataChange(pollData.replaceAt(optionIndex, it)) },
                                placeholder = stringResource(
                                    if (index == 0) {
                                        R.string.stickerview_poll_option1_placeholder
                                    } else {
                                        R.string.stickerview_poll_option2_placeholder
                                    },
                                ),
                                color = if (isLight) ink.copy(alpha = 0.9f) else Color.White,
                                placeholderColor = if (isLight) {
                                    ink.copy(alpha = 0.45f)
                                } else {
                                    Color.White.copy(alpha = 0.45f)
                                },
                                fontSize = 15.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                            )
                        }
                    } else {
                        val text = pollData.getOrNull(optionIndex).takeUnless { it.isNullOrBlank() }
                            ?: if (index == 0) "Yes" else "No"
                        InteractivePollOptionButton(
                            text = text,
                            percentage = voteState.percentage(index),
                            isSelected = voteState.selectedOption == index,
                            hasVoted = voteState.hasVoted,
                            styleVariant = styleVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            onTap = {
                                if (voteState.hasVoted || viewerId == null || isPreview) return@InteractivePollOptionButton
                                voteState = voteState.copy(selectedOption = index)
                                scope.launch {
                                    runCatching {
                                        StoryPollVoteStore.submit(userId, storyId, stickerId, viewerId, index)
                                        StoryPollVoteStore.load(userId, storyId, stickerId, viewerId)
                                    }.getOrNull()?.let { voteState = it }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Port de `InteractivePollOptionButton`; conserva el progreso porcentual tras votar. */
@Composable
private fun InteractivePollOptionButton(
    text: String,
    percentage: Float,
    isSelected: Boolean,
    hasVoted: Boolean,
    styleVariant: Int,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val isLight = styleVariant % 6 == 0
    val ink = if (isLight) momentsStickerInk(isDark) else Color.White
    val surface = if (isLight) momentsStickerSurface(isDark) else Color.Black
    val animatedPercent by animateFloatAsState(
        targetValue = percentage / 100f,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "pollPercent",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isSelected) ink.copy(alpha = 0.92f)
                else if (isLight) ink.copy(alpha = 0.08f)
                else Color.White.copy(alpha = 0.18f),
            )
            .clickable(enabled = !hasVoted && LocalStoryStickerHitTesting.current, onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        if (hasVoted) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .fillMaxWidth(animatedPercent.coerceIn(0f, 1f))
                    .background(
                        if (isSelected) ink.copy(alpha = 0.92f)
                        else if (isLight) ink.copy(alpha = 0.16f)
                        else Color.White.copy(alpha = 0.28f),
                    ),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (hasVoted) Arrangement.Start else Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text,
                color = if (isSelected) surface else if (isLight) ink.copy(alpha = 0.9f) else Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // Sin voto: misma fórmula que el título (centrado). Con voto: leading + %.
                modifier = if (hasVoted) Modifier.weight(1f) else Modifier,
                textAlign = if (hasVoted) TextAlign.Start else TextAlign.Center,
            )
            if (hasVoted) {
                Text(
                    "${percentage.toInt()}%",
                    color = if (isSelected) surface
                    else if (isLight) ink.copy(alpha = 0.72f)
                    else Color.White.copy(alpha = 0.72f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

/** Dead code iOS (sin call sites). Alias al sticker inline por compat. */
@Composable
fun InteractivePollOverlay(
    pollData: List<String>,
    storyId: String,
    userId: String,
    stickerId: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.7f)).clickable(onClick = onDismiss),
    ) {
        Box(Modifier.clickable(enabled = false) {}) {
            InteractivePollSticker(pollData, storyId, userId, stickerId)
        }
    }
}

@Composable
fun PollVoteView(
    pollData: List<String>,
    storyId: String,
    userId: String,
    stickerId: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) = InteractivePollOverlay(pollData, storyId, userId, stickerId, onDismiss, modifier)

private fun List<String>.replaceAt(index: Int, value: String): List<String> {
    val mutable = toMutableList()
    while (mutable.size <= index) mutable += ""
    mutable[index] = value
    return mutable
}

private data class EmojiSliderVoteState(
    val submittedValue: Float? = null,
    val averageValue: Float = 0.5f,
    val totalVotes: Int = 0,
)

/** Contrato Firestore de `emojiSliders/{stickerId}/votes` usado por Swift. */
private object EmojiSliderVoteStore {
    private val db get() = FirebaseFirestore.getInstance()

    private fun votes(userId: String, storyId: String, stickerId: String) = db
        .collection("users").document(userId)
        .collection("stories").document(storyId)
        .collection("emojiSliders").document(stickerId)
        .collection("votes")

    suspend fun load(userId: String, storyId: String, stickerId: String, viewerId: String?): EmojiSliderVoteState {
        val documents = votes(userId, storyId, stickerId).get().await().documents
        val values = documents.mapNotNull { (it.get("value") as? Number)?.toFloat() }
        val selected = viewerId?.let { viewer ->
            (votes(userId, storyId, stickerId).document(viewer).get().await().get("value") as? Number)?.toFloat()
        }
        return EmojiSliderVoteState(
            submittedValue = selected,
            averageValue = if (values.isEmpty()) 0.5f else values.average().toFloat(),
            totalVotes = values.size,
        )
    }

    suspend fun submitIfAbsent(
        userId: String,
        storyId: String,
        stickerId: String,
        viewerId: String,
        value: Float,
    ) {
        val reference = votes(userId, storyId, stickerId).document(viewerId)
        if (reference.get().await().get("value") is Number) return
        reference.set(
            mapOf("userId" to viewerId, "value" to value, "timestamp" to FieldValue.serverTimestamp()),
        ).await()
    }
}

/**
 * Port de `InteractiveEmojiSliderSticker`.
 * Card nativa + pan overlay (≡ `EmojiSliderVotePanOverlay`); bloquea gestos del deck al arrastrar.
 */
@Composable
fun InteractiveEmojiSliderSticker(
    prompt: String,
    emoji: String,
    storyId: String,
    userId: String,
    stickerId: String,
    styleVariant: Int = 0,
    gestureGate: StoryDeckGestureGate? = null,
    modifier: Modifier = Modifier,
) {
    val viewerId = FirebaseAuth.getInstance().currentUser?.uid
    val allowsHitTesting = LocalStoryStickerHitTesting.current
    val isAuthor = viewerId == userId
    val scope = rememberCoroutineScope()
    var state by remember(storyId, stickerId, viewerId) { mutableStateOf(EmojiSliderVoteState()) }
    var dragValue by remember(storyId, stickerId) { mutableStateOf<Float?>(null) }
    var interacting by remember(storyId, stickerId) { mutableStateOf(false) }
    val isPreview = userId == "preview" || storyId.isBlank() || storyId == "preview"
    val canVote = allowsHitTesting && !isAuthor && state.submittedValue == null && viewerId != null && !isPreview
    val displayValue = dragValue ?: state.submittedValue ?: if (isAuthor && state.totalVotes > 0) state.averageValue else 0.5f
    val displayAverage = if (!isAuthor && state.submittedValue != null && state.totalVotes > 0) {
        state.averageValue.toDouble()
    } else {
        null
    }
    val source = "emojiSlider.$storyId.$stickerId"
    val size = emojiSliderRenderingSize(prompt)
    val showsPrompt = emojiSliderHasPrompt(prompt)
    val totalW = size.width.value
    val totalH = size.height.value
    val metrics = emojiSliderTrackMetrics(totalW)
    val trackFrame = emojiSliderTrackFrame(totalW, totalH, showsPrompt)

    LaunchedEffect(storyId, userId, stickerId, viewerId) {
        if (!isPreview) {
            state = runCatching { EmojiSliderVoteStore.load(userId, storyId, stickerId, viewerId) }.getOrDefault(state)
        }
    }
    DisposableEffect(source) {
        onDispose { gestureGate?.clearSuppression(source) }
    }

    fun endInteraction() {
        interacting = false
        gestureGate?.clearSuppression(source)
    }

    Box(
        modifier = modifier
            .size(size.width, size.height)
            .emojiSliderVotePan(
                enabled = canVote,
                trackFrame = trackFrame,
                trackLeadingDp = metrics.leading,
                trackWidthDp = metrics.width,
                onBegan = {
                    if (!interacting) {
                        interacting = true
                        gestureGate?.setSuppressionScope(
                            StoryGestureSuppressionScope.SUPPRESS_VIEWER_GESTURES,
                            source,
                        )
                    }
                },
                onChanged = { dragValue = it },
                onEnded = { value ->
                    endInteraction()
                    dragValue = null
                    if (viewerId != null) {
                        scope.launch {
                            runCatching {
                                EmojiSliderVoteStore.submitIfAbsent(userId, storyId, stickerId, viewerId, value)
                                EmojiSliderVoteStore.load(userId, storyId, stickerId, viewerId)
                            }.getOrNull()?.let { state = it }
                        }
                    }
                },
                onCancelled = {
                    endInteraction()
                    dragValue = null
                },
            ),
    ) {
        StickerEmojiSliderCardView(
            prompt = prompt,
            emoji = emoji.ifBlank { "😍" },
            value = displayValue.toDouble(),
            averageValue = displayAverage,
            styleVariant = styleVariant,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Port del renderer central `StoryStickerView`.
 * Frame y reveal siguen en `StoryInteractiveStickers.kt`; el quiz también
 * (`InteractiveQuizSticker`) se renderiza desde aquí.
 */
@Composable
fun StoryStickerRendererLayer(
    storyId: String,
    userId: String,
    stickers: List<StickerData>,
    gestureGate: StoryDeckGestureGate? = null,
    reportsDeckInteractionExclusion: Boolean = true,
    onPauseStory: () -> Unit = {},
    onResumeStory: () -> Unit = {},
    onMentionTap: (String) -> Unit = {},
    onMomentTap: (momentId: String, authorId: String) -> Unit = { _, _ -> },
    /** Si se pasa, no hay Box fillMaxSize intermedio → zIndex interleave con texto. */
    containerWidthPx: Float? = null,
    containerHeightPx: Float? = null,
    modifier: Modifier = Modifier,
) {
    if (containerWidthPx != null && containerHeightPx != null) {
        StoryStickerRendererContent(
            storyId = storyId,
            userId = userId,
            stickers = stickers,
            widthPx = containerWidthPx,
            heightPx = containerHeightPx,
            gestureGate = gestureGate,
            reportsDeckInteractionExclusion = reportsDeckInteractionExclusion,
            onPauseStory = onPauseStory,
            onResumeStory = onResumeStory,
            onMentionTap = onMentionTap,
            onMomentTap = onMomentTap,
        )
    } else {
        BoxWithConstraints(modifier) {
            StoryStickerRendererContent(
                storyId = storyId,
                userId = userId,
                stickers = stickers,
                widthPx = constraints.maxWidth.toFloat(),
                heightPx = constraints.maxHeight.toFloat(),
                gestureGate = gestureGate,
                reportsDeckInteractionExclusion = reportsDeckInteractionExclusion,
                onPauseStory = onPauseStory,
                onResumeStory = onResumeStory,
                onMentionTap = onMentionTap,
                onMomentTap = onMomentTap,
            )
        }
    }
}

@Composable
private fun StoryStickerRendererContent(
    storyId: String,
    userId: String,
    stickers: List<StickerData>,
    widthPx: Float,
    heightPx: Float,
    gestureGate: StoryDeckGestureGate?,
    reportsDeckInteractionExclusion: Boolean,
    onPauseStory: () -> Unit,
    onResumeStory: () -> Unit,
    onMentionTap: (String) -> Unit,
    onMomentTap: (momentId: String, authorId: String) -> Unit,
) {
    val density = LocalDensity.current.density
    stickers
        .filterNot { it.type == "frame" || it.type == "reveal" }
        .sortedBy { it.zIndex ?: 0 }
        .forEach { sticker ->
            // ≡ iOS StoryMediaOverlayRendererView.stickerForDisplay + .position (centro)
            val (centerX, centerY) = StoryViewerLayoutHelpers.stickerDisplayPosition(
                sticker.position,
                widthPx,
                heightPx,
            )
            val displayScale = StoryViewerLayoutHelpers.stickerDisplayScale(
                sticker.scale,
                widthPx,
                density,
            )
            android.util.Log.d(
                "StoryStickerScale",
                "view type=${sticker.type} firestoreScale=${sticker.scale} " +
                    "canvasPx=$widthPx density=$density " +
                    "widthDp=${StoryViewerLayoutHelpers.canvasWidthDp(widthPx, density)} " +
                    "displayScale=$displayScale",
            )
            var contentWidthPx by remember(sticker.stickerId, sticker.content) { mutableFloatStateOf(0f) }
            var contentHeightPx by remember(sticker.stickerId, sticker.content) { mutableFloatStateOf(0f) }
            val exclusionId = "sticker.$storyId.${sticker.stickerId.orEmpty()}"
            Box(
                Modifier
                    .zIndex((sticker.zIndex ?: 0).toFloat())
                    .onSizeChanged {
                        contentWidthPx = it.width.toFloat()
                        contentHeightPx = it.height.toFloat()
                    }
                    .offset {
                        IntOffset(
                            (centerX - contentWidthPx / 2f).roundToInt(),
                            (centerY - contentHeightPx / 2f).roundToInt(),
                        )
                    }
                    .graphicsLayer {
                        scaleX = displayScale
                        scaleY = displayScale
                        rotationZ = Math.toDegrees(sticker.rotation).toFloat()
                        transformOrigin = TransformOrigin.Center
                    }
                    .storyDeckInteractionExclusion(
                        id = exclusionId,
                        gate = gestureGate,
                        enabled = reportsDeckInteractionExclusion && sticker.needsInteractionRegion(),
                    ),
            ) {
                StoryStickerView(
                    sticker = sticker,
                    storyId = storyId,
                    userId = userId,
                    gestureGate = gestureGate,
                    onPauseStory = onPauseStory,
                    onResumeStory = onResumeStory,
                    onMentionTap = onMentionTap,
                    onMomentTap = onMomentTap,
                )
            }
        }
}

/** ≡ iOS `StoryStickerView.needsInteractionRegion`. */
private fun StickerData.needsInteractionRegion(): Boolean = when (type) {
    "poll", "question", "questionResponse", "quiz", "emojiSlider",
    "mention", "link", "location", "shareMoment", "hashtag",
    -> true
    else -> false
}

/** Contraparte Compose de la rama `interactiveStickerBody` de Swift. */
@Composable
fun StoryStickerView(
    sticker: StickerData,
    storyId: String,
    userId: String,
    gestureGate: StoryDeckGestureGate? = null,
    onPauseStory: () -> Unit = {},
    onResumeStory: () -> Unit = {},
    onMentionTap: (String) -> Unit = {},
    onMomentTap: (momentId: String, authorId: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val allowsHit = LocalStoryStickerHitTesting.current
    val gatedMentionTap: (String) -> Unit = { id -> if (allowsHit) onMentionTap(id) }
    when {
        sticker.type == "shareMoment" -> StorySharedMomentSticker(
            sticker = sticker,
            onClick = {
                if (!allowsHit) return@StorySharedMomentSticker
                sticker.momentId?.let { moment ->
                    sticker.userId?.let { author -> onMomentTap(moment, author) }
                }
            },
            modifier = modifier,
        )
        sticker.isAnimated && !sticker.videoURL.isNullOrBlank() -> StoryAnimatedVideoSticker(
            sticker = sticker,
            onClick = {
                if (!allowsHit) return@StoryAnimatedVideoSticker
                sticker.momentId?.let { moment ->
                    sticker.userId?.let { author -> onMomentTap(moment, author) }
                }
            },
            modifier = modifier,
        )
        sticker.isAnimated && !sticker.gifURL.isNullOrBlank() -> {
            // ≡ iOS AnimatedStickerView: frame = sticker.image.size (Base64 de tamaño en content).
            val sizeMod = remember(sticker.content, sticker.stickerId) {
                val decoded = decodeShareMomentBitmap(sticker.content)
                if (decoded != null && decoded.width > 0 && decoded.height > 0) {
                    Modifier.size(decoded.width.dp, decoded.height.dp)
                } else {
                    Modifier.size(180.dp)
                }
            }
            StoryGifSticker(
                gifURL = sticker.gifURL,
                modifier = modifier.then(sizeMod),
            )
        }
        sticker.type == "poll" -> InteractivePollSticker(
            pollData = sticker.pollOptions?.let { listOf(sticker.questionText.orEmpty()) + it }
                ?: listOf(sticker.content, "", ""),
            storyId = storyId,
            userId = userId,
            stickerId = sticker.stickerId.orEmpty(),
            styleVariant = sticker.styleVariant ?: 0,
            modifier = modifier.width(300.dp),
        )
        sticker.type == "emojiSlider" -> InteractiveEmojiSliderSticker(
            prompt = sticker.sliderPrompt.orEmpty(),
            emoji = sticker.sliderEmoji.orEmpty(),
            storyId = storyId,
            userId = userId,
            stickerId = sticker.stickerId.orEmpty(),
            styleVariant = sticker.styleVariant ?: 0,
            gestureGate = gestureGate,
            modifier = modifier,
        )
        sticker.type == "weather" -> AnimatedWeatherSticker(
            weatherSymbol = sticker.weatherSymbol.orEmpty(),
            temperature = sticker.questionText ?: sticker.content,
            modifier = modifier.width(140.dp).height(50.dp),
        )
        sticker.type == "question" -> InteractiveQuestionSticker(
            questionText = sticker.questionText ?: sticker.content,
            storyId = storyId,
            userId = userId,
            stickerId = sticker.stickerId.orEmpty(),
            styleVariant = sticker.styleVariant ?: 0,
            onPauseStory = onPauseStory,
            onResumeStory = onResumeStory,
            onOpenProfile = gatedMentionTap,
            modifier = modifier.width(300.dp),
        )
        sticker.type == "questionResponse" -> QuestionResponseStoryStickerCardView(
            questionText = sticker.questionText ?: sticker.content,
            styleVariant = sticker.styleVariant ?: 0,
            modifier = modifier,
        )
        sticker.type == "mention" -> InteractiveMentionSticker(
            username = sticker.username ?: sticker.content,
            styleVariant = sticker.styleVariant ?: 0,
            onTap = { if (allowsHit) sticker.userId?.let(onMentionTap) },
            modifier = modifier,
        )
        sticker.type == "hashtag" -> InteractiveHashtagSticker(
            hashtag = sticker.hashtag ?: sticker.content.removePrefix("#"),
            styleVariant = sticker.styleVariant ?: 0,
            onPauseStory = if (allowsHit) onPauseStory else ({}),
            onResumeStory = if (allowsHit) onResumeStory else ({}),
            modifier = modifier,
        )
        sticker.type == "location" -> InteractiveLocationSticker(
            locationName = sticker.location ?: sticker.content,
            latitude = sticker.latitude,
            longitude = sticker.longitude,
            styleVariant = sticker.styleVariant ?: 0,
            onPauseStory = if (allowsHit) onPauseStory else ({}),
            onResumeStory = if (allowsHit) onResumeStory else ({}),
            modifier = modifier,
        )
        sticker.type == "quiz" -> {
            val question = sticker.quizQuestion
            val options = sticker.quizOptions
            if (question != null && !options.isNullOrEmpty()) {
                InteractiveQuizSticker(
                    storyId = storyId,
                    userId = userId,
                    stickerId = sticker.stickerId.orEmpty(),
                    question = question,
                    options = options,
                    correctIndex = sticker.quizCorrectIndex ?: 0,
                    styleVariant = sticker.styleVariant ?: 0,
                    modifier = modifier.width(300.dp),
                )
            }
        }
        sticker.type == "link" -> {
            val linkURL = sticker.linkURL.orEmpty()
            val title = sticker.linkTitle?.takeIf { it.isNotBlank() }
                ?: stickerHostLabel(linkURL)
            Box(
                modifier = modifier.clickable(enabled = allowsHit) {
                    normalizedStickerURL(linkURL)?.let { uri ->
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        }
                    }
                },
            ) {
                StickerLinkCardView(
                    title = title,
                    styleVariant = sticker.styleVariant ?: 0,
                )
            }
        }
        sticker.type == "countdown" -> {
            val title = sticker.countdownTitle?.takeIf { it.isNotBlank() } ?: sticker.content
            val target = sticker.countdownTargetAtMs
            if (target != null) {
                StickerCountdownCardView(
                    title = title,
                    targetAtMs = target,
                    styleVariant = sticker.styleVariant ?: 0,
                    modifier = modifier,
                )
            }
        }
        sticker.type == "time" -> StickerTimeCardView(
            timeText = sticker.questionText?.takeIf { it.isNotBlank() }
                ?: MomentsFormat.smartDate(Date(), MomentsFormat.DateContext.TIME_ONLY),
            dateText = sticker.caption?.takeIf { it.isNotBlank() }
                ?: MomentsFormat.smartDate(Date(), MomentsFormat.DateContext.NUMERIC_DATE),
            styleVariant = sticker.styleVariant ?: 0,
            // Same intrinsic layout as the editor. 56 dp cannot contain both text
            // lines plus the card's 28 dp vertical padding and compressed the viewer.
            modifier = modifier,
        )
        sticker.type == "audio" -> {
            val url = sticker.audioURL
            if (!url.isNullOrBlank()) {
                InteractiveAudioStickerView(
                    audioURL = url,
                    duration = sticker.audioDuration ?: 15.0,
                    modifier = modifier,
                )
            }
        }
        else -> StoryStaticSticker(sticker, modifier)
    }
}

@Composable
private fun StorySharedMomentSticker(sticker: StickerData, onClick: () -> Unit, modifier: Modifier) {
    val density = LocalDensity.current
    val corner = FeedMomentCardLayout.mediaCornerRadius
    val decoded = remember(sticker.content, sticker.stickerId) {
        decodeShareMomentBitmap(sticker.content)
    }
    val widthDp = decoded?.let { with(density) { it.width.toDp() } } ?: 260.dp
    val heightDp = decoded?.let { with(density) { it.height.toDp() } } ?: 340.dp

    Box(
        modifier
            .width(widthDp)
            .height(heightDp)
            .clip(RoundedCornerShape(corner))
            .clickable(enabled = LocalStoryStickerHitTesting.current, onClick = onClick),
    ) {
        when {
            decoded != null -> Image(
                bitmap = decoded.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            else -> {
                val baseUrl = sticker.gifURL ?: sticker.content.takeIf { it.startsWith("http") }
                if (!baseUrl.isNullOrBlank()) {
                    AsyncImage(baseUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
                }
            }
        }
        sticker.videoURL?.takeIf { it.isNotBlank() }?.let { url ->
            StickerVideoPlayer(url, Modifier.fillMaxSize())
        }

        // Header ≡ ultraThinMaterial mask iOS
        Box(
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.22f),
                            Color.White.copy(alpha = 0.10f),
                            Color.Transparent,
                        ),
                    ),
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val profileUid = sticker.userId
                if (!profileUid.isNullOrBlank()) {
                    AsyncProfileImageView(
                        userId = profileUid,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .border(
                                width = 1.dp,
                                brush = Brush.linearGradient(
                                    listOf(Color.White.copy(0.5f), Color.Transparent),
                                ),
                                shape = CircleShape,
                            ),
                    )
                } else {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(34.dp),
                    )
                }
                Text(
                    text = sticker.username ?: "User",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if ((sticker.mediaCount ?: 0) > 1) {
            Icon(
                Icons.Filled.FilterNone,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 54.dp, end = 12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.18f))
                    .padding(6.dp)
                    .size(11.dp),
            )
        }

        sticker.caption?.takeIf { it.isNotBlank() }?.let { caption ->
            Text(
                text = caption,
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Color.White.copy(alpha = 0.18f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

private fun decodeShareMomentBitmap(content: String): android.graphics.Bitmap? {
    if (content.isBlank() || content.startsWith("http") || content.startsWith("sticker_")) return null
    return runCatching {
        val bytes = android.util.Base64.decode(content, android.util.Base64.DEFAULT)
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}

/** Rama iOS `isAnimated` + videoURL (no shareMoment). */
@Composable
private fun StoryAnimatedVideoSticker(
    sticker: StickerData,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val corner = FeedMomentCardLayout.mediaCornerRadius
    Box(
        modifier
            .width(220.dp)
            .height(280.dp)
            .clip(RoundedCornerShape(corner))
            .clickable(enabled = LocalStoryStickerHitTesting.current, onClick = onClick),
    ) {
        val baseUrl = sticker.gifURL ?: sticker.content.takeIf { it.startsWith("http") }
        if (!baseUrl.isNullOrBlank()) {
            AsyncImage(baseUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        sticker.videoURL?.let { StickerVideoPlayer(it, Modifier.fillMaxSize()) }
        sticker.username?.let { username ->
            Text(
                username,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp),
            )
        }
        sticker.caption?.takeIf { it.isNotBlank() }?.let { caption ->
            Text(
                caption,
                color = Color.White,
                fontSize = 9.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
                    .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

/** Rama iOS `AnimatedStickerView` (GIF). El tamaño lo fija el caller (≡ sticker.image.size). */
@Composable
private fun StoryGifSticker(gifURL: String, modifier: Modifier) {
    val context = LocalContext.current
    val request = ImageRequest.Builder(context)
        .data(gifURL)
        .crossfade(false)
        .decoderFactory(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoderDecoder.Factory()
            } else {
                GifDecoder.Factory()
            },
        )
        .build()
    SubcomposeAsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier,
        loading = { Box(Modifier.fillMaxSize()) },
        error = { Box(Modifier.fillMaxSize()) },
        success = { SubcomposeAsyncImageContent(modifier = Modifier.fillMaxSize()) },
    )
}

@Composable
private fun StoryStaticSticker(sticker: StickerData, modifier: Modifier) {
    val corner = FeedMomentCardLayout.mediaCornerRadius
    val shaped = modifier.clip(RoundedCornerShape(corner))
    // Estándar chat/stories: glyph nativo del SO desde caption (Unicode).
    // Base64 solo fallback legacy sin caption.
    if (sticker.type == "emoji") {
        val glyph = sticker.caption?.takeIf { it.isNotBlank() && it.length <= 8 }
            ?: sticker.content.takeIf { it.length <= 8 }
        if (glyph != null) {
            // ≡ iOS createEmojiGlyphImage: canvas 200×200, font ~150
            Box(shaped.size(200.dp), contentAlignment = Alignment.Center) {
                Text(glyph, fontSize = 150.sp)
            }
            return
        }
        val decodedEmoji = remember(sticker.content, sticker.stickerId) {
            decodeShareMomentBitmap(sticker.content)
        }
        if (decodedEmoji != null) {
            Image(
                bitmap = decodedEmoji.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = shaped.size(decodedEmoji.width.dp, decodedEmoji.height.dp),
            )
            return
        }
    }
    // ≡ iOS else: Image(uiImage) — selfie/generic guardan PNG/JPEG en Base64.
    val decoded = remember(sticker.content, sticker.stickerId) {
        decodeShareMomentBitmap(sticker.content)
    }
    when {
        !sticker.videoURL.isNullOrBlank() -> StickerVideoPlayer(sticker.videoURL, shaped)
        !sticker.gifURL.isNullOrBlank() -> {
            val sizeMod = if (decoded != null && decoded.width > 0 && decoded.height > 0) {
                Modifier.size(decoded.width.dp, decoded.height.dp)
            } else {
                Modifier.size(180.dp)
            }
            StoryGifSticker(sticker.gifURL, shaped.then(sizeMod))
        }
        decoded != null -> Image(
            bitmap = decoded.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = shaped.size(decoded.width.dp, decoded.height.dp),
        )
        sticker.content.startsWith("http") -> AsyncImage(
            sticker.content,
            null,
            shaped.size(180.dp),
            contentScale = ContentScale.Fit,
        )
        // Solo texto corto; nunca volcar Base64/payload binario.
        sticker.content.isNotBlank() && sticker.content.length <= 16 -> Text(
            sticker.content,
            fontSize = 32.sp,
            modifier = shaped,
        )
    }
}

data class QuestionResponseState(
    val responseCount: Int = 0,
    val hasResponded: Boolean = false,
)

/** Contrato Firestore de `questionResponses/{stickerId}/responses`. */
private object StoryQuestionResponseStore {
    private val db get() = FirebaseFirestore.getInstance()

    private fun responses(userId: String, storyId: String, stickerId: String) = db
        .collection("users").document(userId)
        .collection("stories").document(storyId)
        .collection("questionResponses").document(stickerId)
        .collection("responses")

    suspend fun load(userId: String, storyId: String, stickerId: String, viewerId: String?): QuestionResponseState {
        val documents = responses(userId, storyId, stickerId).get().await().documents
        return QuestionResponseState(
            responseCount = documents.size,
            hasResponded = viewerId != null && documents.any { it.getString("userId") == viewerId },
        )
    }

    suspend fun submit(
        userId: String,
        storyId: String,
        stickerId: String,
        viewerId: String,
        response: String,
    ) {
        responses(userId, storyId, stickerId).document(java.util.UUID.randomUUID().toString()).set(
            mapOf(
                "userId" to viewerId,
                "response" to response,
                "timestamp" to FieldValue.serverTimestamp(),
                "isAnonymous" to true,
            ),
        ).await()
    }
}

/** Port de `InteractiveQuestionSticker`; la lista de respuestas vive en su archivo Swift propio. */
@Composable
fun InteractiveQuestionSticker(
    questionText: String,
    storyId: String,
    userId: String,
    stickerId: String,
    styleVariant: Int = 0,
    isEditingInline: Boolean = false,
    onQuestionChange: (String) -> Unit = {},
    onPauseStory: () -> Unit = {},
    onResumeStory: () -> Unit = {},
    onOpenProfile: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val viewerId = FirebaseAuth.getInstance().currentUser?.uid
    val isAuthor = viewerId == userId
    val preview = userId == "preview" || storyId.isBlank() || storyId == "preview"
    val scope = rememberCoroutineScope()
    var state by remember(storyId, stickerId, viewerId) { mutableStateOf(QuestionResponseState()) }
    var showInput by remember { mutableStateOf(false) }
    var showResponses by remember { mutableStateOf(false) }
    val isDark = isSystemInDarkTheme()
    val isLight = styleVariant % 6 == 0
    val ink = if (isLight) momentsStickerInk(isDark) else Color.White
    val headerInk = if (isLight) momentsStickerInverseInk(isDark) else Color.White

    LaunchedEffect(storyId, userId, stickerId, viewerId) {
        if (!preview) state = runCatching {
            StoryQuestionResponseStore.load(userId, storyId, stickerId, viewerId)
        }.getOrDefault(state)
    }

    val subtitle = when {
        isAuthor && state.responseCount > 0 ->
            stringResource(R.string.question_responses, state.responseCount)
        isAuthor -> stringResource(R.string.question_tap_to_see)
        state.hasResponded -> stringResource(R.string.question_already_asked)
        else -> stringResource(R.string.question_tap_to_answer)
    }
    val actionModifier = if (!isEditingInline) {
        modifier.clickable(enabled = LocalStoryStickerHitTesting.current && (isAuthor || !state.hasResponded)) {
            if (isAuthor) showResponses = true else showInput = true
            onPauseStory()
        }
    } else modifier

    // ≡ StickerOverlayView.swift `.frame(width: 300, height: 132)` — contenido debe CABER
    Box(
        modifier = actionModifier
            .width(300.dp)
            .height(132.dp)
            .clip(RoundedCornerShape(24.dp)),
    ) {
        AnimatedMomentsCardStickerSurface(
            styleVariant = styleVariant,
            isDark = isDark,
            modifier = Modifier.matchParentSize(),
        )
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedMomentsCardStickerHeaderSurface(
                    styleVariant = styleVariant,
                    isDark = isDark,
                    modifier = Modifier.matchParentSize(),
                )
                if (isEditingInline) {
                    StickerCenteredTextField(
                        value = questionText,
                        onValueChange = onQuestionChange,
                        placeholder = stringResource(R.string.question_answer_title),
                        color = headerInk,
                        placeholderColor = headerInk.copy(alpha = 0.45f),
                        fontSize = 17.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                    )
                } else {
                    Text(
                        text = questionText.ifBlank { stringResource(R.string.question_answer_title) },
                        color = headerInk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                    )
                }
            }
            // ≡ iOS Capsule subtitle — padding exterior + cápsula; cabe en 132
            Text(
                subtitle,
                color = if (isLight) ink.copy(alpha = 0.72f) else Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 4.dp, bottom = 12.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(
                        if (isLight) ink.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.18f),
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }

    if (showInput) {
        QuestionResponseInputView(
            questionText = questionText,
            storyId = storyId,
            userId = userId,
            stickerId = stickerId,
            onDismiss = {
                showInput = false
                onResumeStory()
            },
            onResponseSubmitted = {
                state = it
                showInput = false
                onResumeStory()
            },
        )
    }
    if (showResponses) {
        MomentsModalSheet(
            onDismissRequest = {
                showResponses = false
                onResumeStory()
            },
            largeOnly = false,
        ) {
            QuestionResponsesView(
                questionText = questionText,
                storyId = storyId,
                userId = userId,
                stickerId = stickerId,
                onDismiss = {
                    showResponses = false
                    onResumeStory()
                },
                onOpenProfile = { profileUserId ->
                    if (profileUserId.isBlank()) return@QuestionResponsesView
                    // Cerrar sheet para que el perfil del viewer quede visible (≡ navigation destination iOS)
                    showResponses = false
                    onOpenProfile(profileUserId)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Port Compose de `QuestionResponseInputView`. */
@Composable
fun QuestionResponseInputView(
    questionText: String,
    storyId: String,
    userId: String,
    stickerId: String,
    onDismiss: () -> Unit,
    onResponseSubmitted: (QuestionResponseState) -> Unit,
) {
    val viewerId = FirebaseAuth.getInstance().currentUser?.uid
    val scope = rememberCoroutineScope()
    var responseText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    // ≡ iOS `.presentationDetents([.medium, .large])`
    MomentsModalSheet(onDismissRequest = onDismiss, largeOnly = false) {
        Column(
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp).padding(bottom = 28.dp),
        ) {
            Text("Answer", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Text(questionText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = responseText,
                onValueChange = { responseText = it },
                label = { Text("Your answer") },
                minLines = 3,
                maxLines = 6,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    val clean = responseText.trim()
                    if (viewerId == null || clean.isBlank() || isLoading) return@Button
                    isLoading = true
                    scope.launch {
                        val result = runCatching {
                            StoryQuestionResponseStore.submit(userId, storyId, stickerId, viewerId, clean)
                            StoryQuestionResponseStore.load(userId, storyId, stickerId, viewerId)
                        }.getOrNull()
                        isLoading = false
                        if (result != null) onResponseSubmitted(result)
                    }
                },
                enabled = responseText.trim().isNotEmpty() && !isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isLoading) {
                    MomentsCircularProgressIndicator(
                        modifier = Modifier.height(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Send answer")
                }
            }
        }
    }
}

/** Port de `InteractiveLocationSticker`; fullScreen ≡ `LocationMapView`. */
@Composable
fun InteractiveLocationSticker(
    locationName: String,
    latitude: Double? = null,
    longitude: Double? = null,
    styleVariant: Int,
    onPauseStory: () -> Unit,
    onResumeStory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showingMap by remember { mutableStateOf(false) }
    Box(
        modifier = modifier.clickable(enabled = LocalStoryStickerHitTesting.current) {
            showingMap = true
            onPauseStory()
        },
    ) {
        StickerLocationCardView(
            locationName = locationName,
            styleVariant = styleVariant,
        )
    }
    if (showingMap) {
        Dialog(
            onDismissRequest = {
                showingMap = false
                onResumeStory()
            },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            LocationMapView(
                locationName = locationName,
                latitude = latitude,
                longitude = longitude,
                onDismiss = {
                    showingMap = false
                    onResumeStory()
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Port de `InteractiveMentionSticker`. */
@Composable
fun InteractiveMentionSticker(
    username: String,
    styleVariant: Int,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.clickable(enabled = LocalStoryStickerHitTesting.current, onClick = onTap)) {
        StickerMentionCardView(
            username = username,
            styleVariant = styleVariant,
        )
    }
}

/** Port de `InteractiveHashtagSticker`; fullScreen ≡ `ExploreView(initialSearchQuery:)`. */
@Composable
fun InteractiveHashtagSticker(
    hashtag: String,
    styleVariant: Int,
    onPauseStory: () -> Unit,
    onResumeStory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showingExplore by remember { mutableStateOf(false) }
    Box(
        modifier = modifier.clickable(enabled = LocalStoryStickerHitTesting.current) {
            showingExplore = true
            onPauseStory()
        },
    ) {
        StickerHashtagCardView(
            hashtag = hashtag,
            styleVariant = styleVariant,
        )
    }
    if (showingExplore) {
        Dialog(
            onDismissRequest = {
                showingExplore = false
                onResumeStory()
            },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            ExploreView(
                initialSearchQuery = "#$hashtag",
                isDismissable = true,
                onDismiss = {
                    showingExplore = false
                    onResumeStory()
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
