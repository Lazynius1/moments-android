package com.moments.android.views.story.storystickers

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.moments.android.models.StickerData
import com.moments.android.views.story.StoryDeckGestureGate
import com.moments.android.views.story.QuestionResponsesView
import com.moments.android.views.story.storyviewer.StoryGestureSuppressionScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
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
    ) {
        votes(userId, storyId, stickerId).document(viewerId).set(
            mapOf(
                "userId" to viewerId,
                "option" to option,
                "timestamp" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }
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
    LaunchedEffect(storyId, userId, stickerId, viewerId) {
        if (!isPreview) {
            voteState = runCatching {
                StoryPollVoteStore.load(userId, storyId, stickerId, viewerId)
            }.getOrDefault(voteState)
        }
    }

    val isLight = styleVariant % 6 == 0
    val ink = if (isLight) Color(0xFF161616) else Color.White
    val surface = if (isLight) Color(0xFFF8F8FA) else Color(0xFF101114)
    val header = if (isLight) Color(0xFF161616) else Color(0xFF2B6CFF)
    val title = pollData.getOrNull(0).takeUnless { it.isNullOrBlank() } ?: "Ask a question"

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .width(300.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(surface)
            .padding(bottom = 14.dp),
    ) {
        if (isEditingInline) {
            OutlinedTextField(
                value = pollData.getOrNull(0).orEmpty(),
                onValueChange = { onPollDataChange(pollData.replaceAt(0, it)) },
                placeholder = { Text("Ask a question") },
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = if (isLight) Color.White else Color.White,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    fontSize = 18.sp,
                ),
                modifier = Modifier.fillMaxWidth().background(header).padding(horizontal = 18.dp),
            )
        } else {
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                fontSize = 18.sp,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth().background(header).padding(horizontal = 18.dp, vertical = 16.dp),
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 14.dp),
        ) {
            repeat(2) { index ->
                val optionIndex = index + 1
                if (isEditingInline) {
                    OutlinedTextField(
                        value = pollData.getOrNull(optionIndex).orEmpty(),
                        onValueChange = { onPollDataChange(pollData.replaceAt(optionIndex, it)) },
                        placeholder = { Text("Option ${index + 1}") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    val text = pollData.getOrNull(optionIndex).takeUnless { it.isNullOrBlank() }
                        ?: if (index == 0) "Yes" else "No"
                    InteractivePollOptionButton(
                        text = text,
                        percentage = voteState.percentage(index),
                        isSelected = voteState.selectedOption == index,
                        hasVoted = voteState.hasVoted,
                        lightStyle = isLight,
                        onTap = {
                            if (voteState.hasVoted || viewerId == null || isPreview) return@InteractivePollOptionButton
                            voteState = voteState.copy(selectedOption = index)
                            scope.launch {
                                val submitted = runCatching {
                                    StoryPollVoteStore.submit(userId, storyId, stickerId, viewerId, index)
                                    StoryPollVoteStore.load(userId, storyId, stickerId, viewerId)
                                }.getOrNull()
                                if (submitted != null) voteState = submitted
                            }
                        },
                    )
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
    lightStyle: Boolean,
    onTap: () -> Unit,
) {
    val ink = if (lightStyle) Color(0xFF161616) else Color.White
    val surface = if (lightStyle) Color(0xFFF8F8FA) else Color.Black
    val animatedPercent by animateFloatAsState(percentage / 100f, animationSpec = spring(), label = "pollPercent")
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) ink.copy(alpha = 0.92f) else if (lightStyle) ink.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.18f))
            .clickable(enabled = !hasVoted, onClick = onTap),
    ) {
        if (hasVoted) {
            Box(
                Modifier
                    .fillMaxWidth(animatedPercent.coerceIn(0f, 1f))
                    .height(52.dp)
                    .background(if (isSelected) ink.copy(alpha = 0.92f) else if (lightStyle) ink.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.28f)),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            Text(text, color = if (isSelected) surface else if (lightStyle) ink.copy(alpha = 0.9f) else Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
            Spacer(Modifier.weight(1f))
            if (hasVoted) Text("${percentage.toInt()}%", color = if (isSelected) surface else if (lightStyle) ink.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.72f), fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

/** Port funcional de `InteractivePollOverlay` y `PollVoteView` para uso modal. */
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
 * Mientras se arrastra, bloquea la navegación del deck mediante el mismo gate
 * compartido con el resto de stickers interactivos.
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
    val isAuthor = viewerId == userId
    val scope = rememberCoroutineScope()
    var state by remember(storyId, stickerId, viewerId) { mutableStateOf(EmojiSliderVoteState()) }
    var dragValue by remember(storyId, stickerId) { mutableStateOf<Float?>(null) }
    var interacting by remember(storyId, stickerId) { mutableStateOf(false) }
    val isPreview = userId == "preview" || storyId.isBlank() || storyId == "preview"
    val canVote = !isAuthor && state.submittedValue == null && viewerId != null && !isPreview
    val displayValue = dragValue ?: state.submittedValue ?: if (isAuthor && state.totalVotes > 0) state.averageValue else 0.5f
    val source = "emojiSlider.$storyId.$stickerId"

    LaunchedEffect(storyId, userId, stickerId, viewerId) {
        if (!isPreview) {
            state = runCatching { EmojiSliderVoteStore.load(userId, storyId, stickerId, viewerId) }.getOrDefault(state)
        }
    }
    androidx.compose.runtime.DisposableEffect(source) {
        onDispose { gestureGate?.clearSuppression(source) }
    }

    val isLight = styleVariant % 6 == 0
    val ink = if (isLight) Color(0xFF161616) else Color.White
    val surface = if (isLight) Color(0xFFF8F8FA) else Color(0xFF141519)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .width(280.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(surface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        if (prompt.isNotBlank()) {
            Text(prompt, color = ink, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center, maxLines = 2)
        }
        Text(emoji.ifBlank { "😍" }, fontSize = (28f + displayValue * 14f).sp)
        Slider(
            value = displayValue.coerceIn(0f, 1f),
            onValueChange = { value ->
                if (!canVote) return@Slider
                if (!interacting) {
                    interacting = true
                    gestureGate?.setSuppressionScope(StoryGestureSuppressionScope.SUPPRESS_VIEWER_GESTURES, source)
                }
                dragValue = value
            },
            onValueChangeFinished = {
                val value = dragValue ?: return@Slider
                dragValue = null
                if (interacting) {
                    interacting = false
                    gestureGate?.clearSuppression(source)
                }
                if (viewerId != null && canVote) {
                    scope.launch {
                        runCatching {
                            EmojiSliderVoteStore.submitIfAbsent(userId, storyId, stickerId, viewerId, value)
                            EmojiSliderVoteStore.load(userId, storyId, stickerId, viewerId)
                        }.getOrNull()?.let { state = it }
                    }
                }
            },
            enabled = canVote,
            colors = SliderDefaults.colors(
                thumbColor = ink,
                activeTrackColor = ink.copy(alpha = 0.55f),
                inactiveTrackColor = ink.copy(alpha = 0.16f),
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        if (!isAuthor && state.submittedValue != null && state.totalVotes > 0) {
            Text("Average ${(state.averageValue * 100).toInt()}%", color = ink.copy(alpha = 0.62f), fontSize = 12.sp)
        }
    }
}

/**
 * Port del renderer central `StoryStickerView`.
 * Frame y reveal siguen en `StoryInteractiveStickers.kt`, que conserva sus
 * interacciones físicas específicas; este renderer no los vuelve a dibujar.
 */
@Composable
fun StoryStickerRendererLayer(
    storyId: String,
    userId: String,
    stickers: List<StickerData>,
    gestureGate: StoryDeckGestureGate? = null,
    onPauseStory: () -> Unit = {},
    onResumeStory: () -> Unit = {},
    onMentionTap: (String) -> Unit = {},
    onMomentTap: (momentId: String, authorId: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        stickers
            .filterNot { it.type == "frame" || it.type == "reveal" }
            .sortedBy { it.zIndex ?: 0 }
            .forEach { sticker ->
                Box(
                    Modifier
                        .graphicsLayer {
                            scaleX = sticker.scale.toFloat()
                            scaleY = sticker.scale.toFloat()
                            rotationZ = Math.toDegrees(sticker.rotation).toFloat()
                        }
                        .offset {
                            androidx.compose.ui.unit.IntOffset(
                                (sticker.position.x * widthPx).roundToInt(),
                                (sticker.position.y * heightPx).roundToInt(),
                            )
                        },
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
    when (sticker.type) {
        "poll" -> InteractivePollSticker(
            pollData = sticker.pollOptions?.let { listOf(sticker.questionText.orEmpty()) + it }
                ?: listOf(sticker.content, "", ""),
            storyId = storyId,
            userId = userId,
            stickerId = sticker.stickerId.orEmpty(),
            styleVariant = sticker.styleVariant ?: 0,
            modifier = modifier,
        )
        "emojiSlider" -> InteractiveEmojiSliderSticker(
            prompt = sticker.sliderPrompt.orEmpty(),
            emoji = sticker.sliderEmoji.orEmpty(),
            storyId = storyId,
            userId = userId,
            stickerId = sticker.stickerId.orEmpty(),
            styleVariant = sticker.styleVariant ?: 0,
            gestureGate = gestureGate,
            modifier = modifier,
        )
        "weather" -> AnimatedWeatherSticker(
            weatherSymbol = sticker.weatherSymbol.orEmpty(),
            temperature = sticker.questionText ?: sticker.content,
            modifier = modifier,
        )
        "question" -> InteractiveQuestionSticker(
            questionText = sticker.questionText ?: sticker.content,
            storyId = storyId,
            userId = userId,
            stickerId = sticker.stickerId.orEmpty(),
            styleVariant = sticker.styleVariant ?: 0,
            onPauseStory = onPauseStory,
            onResumeStory = onResumeStory,
            modifier = modifier,
        )
        "mention" -> InteractiveMentionSticker(
            username = sticker.username ?: sticker.content,
            styleVariant = sticker.styleVariant ?: 0,
            onTap = { sticker.userId?.let(onMentionTap) },
            modifier = modifier,
        )
        "hashtag" -> InteractiveHashtagSticker(
            hashtag = sticker.hashtag ?: sticker.content.removePrefix("#"),
            styleVariant = sticker.styleVariant ?: 0,
            onPauseStory = onPauseStory,
            onResumeStory = onResumeStory,
            modifier = modifier,
        )
        "location" -> InteractiveLocationSticker(
            locationName = sticker.location ?: sticker.content,
            styleVariant = sticker.styleVariant ?: 0,
            onPauseStory = onPauseStory,
            onResumeStory = onResumeStory,
            modifier = modifier,
        )
        "link" -> StoryStickerLabel(
            text = sticker.linkTitle?.takeIf { it.isNotBlank() } ?: sticker.linkURL.orEmpty(),
            accent = Color(0xFF0A84FF),
            modifier = modifier,
        )
        "countdown" -> StoryStickerLabel(
            text = sticker.countdownTitle?.takeIf { it.isNotBlank() } ?: sticker.content,
            accent = Color(0xFFFF9500),
            modifier = modifier,
        )
        "time" -> StoryStickerLabel(
            text = sticker.questionText?.takeIf { it.isNotBlank() } ?: sticker.content,
            accent = Color(0xFF1C1C1E),
            modifier = modifier,
        )
        "shareMoment" -> StorySharedMomentSticker(
            sticker = sticker,
            onClick = { sticker.momentId?.let { moment -> sticker.userId?.let { author -> onMomentTap(moment, author) } } },
            modifier = modifier,
        )
        else -> StoryStaticSticker(sticker, modifier)
    }
}

@Composable
private fun StoryStickerLabel(text: String, accent: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(accent.copy(alpha = 0.9f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
private fun StorySharedMomentSticker(sticker: StickerData, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier
            .width(220.dp)
            .height(150.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onClick),
    ) {
        val url = sticker.gifURL ?: sticker.content
        if (url.isNotBlank()) {
            AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        sticker.username?.let { username ->
            Text("@$username", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(10.dp))
        }
        sticker.caption?.takeIf { it.isNotBlank() }?.let { caption ->
            Text(caption, color = Color.White, fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp))
        }
    }
}

@Composable
private fun StoryStaticSticker(sticker: StickerData, modifier: Modifier) {
    val url = sticker.videoURL ?: sticker.gifURL
    when {
        sticker.isAnimated && sticker.videoURL != null -> StickerVideoPlayer(sticker.videoURL, modifier)
        !url.isNullOrBlank() -> AsyncImage(url, null, modifier, contentScale = ContentScale.Fit)
        sticker.content.isNotBlank() -> Text(sticker.content, fontSize = 32.sp, modifier = modifier)
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
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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
    modifier: Modifier = Modifier,
) {
    val viewerId = FirebaseAuth.getInstance().currentUser?.uid
    val isAuthor = viewerId == userId
    val preview = userId == "preview" || storyId.isBlank() || storyId == "preview"
    val scope = rememberCoroutineScope()
    var state by remember(storyId, stickerId, viewerId) { mutableStateOf(QuestionResponseState()) }
    var showInput by remember { mutableStateOf(false) }
    var showResponses by remember { mutableStateOf(false) }
    val isLight = styleVariant % 6 == 0
    val ink = if (isLight) Color(0xFF161616) else Color.White
    val surface = if (isLight) Color(0xFFF8F8FA) else Color(0xFF141519)
    val header = if (isLight) Color(0xFF161616) else Color(0xFF2B6CFF)

    LaunchedEffect(storyId, userId, stickerId, viewerId) {
        if (!preview) state = runCatching {
            StoryQuestionResponseStore.load(userId, storyId, stickerId, viewerId)
        }.getOrDefault(state)
    }

    val subtitle = when {
        isAuthor && state.responseCount > 0 -> "${state.responseCount} responses"
        isAuthor -> "Tap to see responses"
        state.hasResponded -> "Already answered"
        else -> "Tap to answer"
    }
    val actionModifier = if (!isEditingInline) {
        modifier.clickable(enabled = isAuthor || !state.hasResponded) {
            if (isAuthor) showResponses = true else showInput = true
            onPauseStory()
        }
    } else modifier

    Column(
        verticalArrangement = Arrangement.spacedBy(0.dp),
        modifier = actionModifier
            .width(300.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(surface),
    ) {
        if (isEditingInline) {
            OutlinedTextField(
                value = questionText,
                onValueChange = onQuestionChange,
                placeholder = { Text("Ask a question") },
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, fontSize = 18.sp),
                modifier = Modifier.fillMaxWidth().background(header).padding(horizontal = 18.dp),
            )
        } else {
            Text(
                text = questionText.ifBlank { "Ask a question" },
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth().background(header).padding(horizontal = 20.dp, vertical = 16.dp),
            )
        }
        Text(
            subtitle,
            color = ink.copy(alpha = 0.72f),
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
        )
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
        ModalBottomSheet(onDismissRequest = {
            showResponses = false
            onResumeStory()
        }) {
            QuestionResponsesView(
                questionText = questionText,
                storyId = storyId,
                userId = userId,
                stickerId = stickerId,
                onDismiss = {
                    showResponses = false
                    onResumeStory()
                },
            )
        }
    }
}

/** Port Compose de `QuestionResponseInputView`. */
@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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
    ModalBottomSheet(onDismissRequest = onDismiss) {
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
                if (isLoading) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(18.dp))
                else Text("Send answer")
            }
        }
    }
}

/** Port de `InteractiveLocationSticker`; el mapa se presenta dentro del viewer. */
@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun InteractiveLocationSticker(
    locationName: String,
    styleVariant: Int,
    onPauseStory: () -> Unit,
    onResumeStory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showingMap by remember { mutableStateOf(false) }
    Button(
        onClick = {
            showingMap = true
            onPauseStory()
        },
        colors = ButtonDefaults.buttonColors(containerColor = tapCycleColor(styleVariant)),
        modifier = modifier,
    ) {
        Text("📍 ${locationName.uppercase()}", fontWeight = FontWeight.Black, maxLines = 1)
    }
    if (showingMap) {
        ModalBottomSheet(onDismissRequest = {
            showingMap = false
            onResumeStory()
        }) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(locationName, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Location preview")
            }
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
    Button(
        onClick = onTap,
        colors = ButtonDefaults.buttonColors(containerColor = tapCycleColor(styleVariant)),
        modifier = modifier,
    ) {
        Text("@${username.uppercase()}", fontSize = 20.sp, fontWeight = FontWeight.Black)
    }
}

/** Port de `InteractiveHashtagSticker`; pausa mientras presenta la exploración. */
@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun InteractiveHashtagSticker(
    hashtag: String,
    styleVariant: Int,
    onPauseStory: () -> Unit,
    onResumeStory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showingExplore by remember { mutableStateOf(false) }
    Button(
        onClick = {
            showingExplore = true
            onPauseStory()
        },
        colors = ButtonDefaults.buttonColors(containerColor = tapCycleColor(styleVariant)),
        modifier = modifier,
    ) {
        Text("#${hashtag.uppercase()}", fontSize = 18.sp, fontWeight = FontWeight.Black)
    }
    if (showingExplore) {
        ModalBottomSheet(onDismissRequest = {
            showingExplore = false
            onResumeStory()
        }) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("#${hashtag}", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Explore hashtag")
            }
        }
    }
}

private fun tapCycleColor(styleVariant: Int): Color = when ((styleVariant % 6 + 6) % 6) {
    0 -> Color(0xFF161616)
    1 -> Color(0xFF0A84FF)
    2 -> Color(0xFFAF52DE)
    3 -> Color(0xFFFF2D55)
    4 -> Color(0xFFFF9500)
    else -> Color(0xFF30D158)
}
