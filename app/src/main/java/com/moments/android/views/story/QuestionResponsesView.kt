package com.moments.android.views.story

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.firebase.firestore.FirebaseFirestore
import com.moments.android.R
import com.moments.android.coordinators.AsyncProfileImageView
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.Point
import com.moments.android.models.QuestionResponse
import com.moments.android.models.StickerData
import com.moments.android.utilities.MomentsFormat
import com.moments.android.views.components.AnimatedMomentsCardStickerHeaderSurface
import com.moments.android.views.components.AnimatedMomentsCardStickerSurface
import com.moments.android.views.components.momentsCardStickerTextColor
import com.moments.android.views.components.momentsStickerInk
import com.moments.android.views.components.momentsStickerInverseInk
import com.moments.android.views.creator.CreatorView
import com.moments.android.views.feed.rememberAdaptiveColors
import kotlinx.coroutines.tasks.await

/** Port de `questionResponseStickerRenderSize`. */
object QuestionResponseStickerLayout {
    val width = 300.dp
    val height = 132.dp
}

/** Port de `QuestionResponseStoryStickerCardView`. */
@Composable
fun QuestionResponseStoryStickerCardView(
    questionText: String,
    styleVariant: Int,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val isLight = styleVariant % 6 == 0
    val bodyInk = momentsCardStickerTextColor(styleVariant, isDark)
    val ink = if (isLight) momentsStickerInk(isDark) else Color.White
    val headerInk = if (isLight) momentsStickerInverseInk(isDark) else Color.White

    Box(
        modifier
            .width(QuestionResponseStickerLayout.width)
            .height(QuestionResponseStickerLayout.height)
            .clip(RoundedCornerShape(24.dp)),
    ) {
        AnimatedMomentsCardStickerSurface(
            styleVariant = styleVariant,
            isDark = isDark,
            modifier = Modifier.matchParentSize(),
        )
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth()) {
                AnimatedMomentsCardStickerHeaderSurface(
                    styleVariant = styleVariant,
                    isDark = isDark,
                    modifier = Modifier.matchParentSize(),
                )
                Text(
                    text = stringResource(R.string.question_responses_anonymous_title),
                    color = headerInk,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(if (isLight) ink.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.14f)),
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = questionText,
                    color = bodyInk,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .background(if (isLight) ink.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.18f))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                )
            }
        }
    }
}

/**
 * Port de `QuestionResponsesView`.
 * Presentar con [com.moments.android.views.shared.MomentsModalSheet]
 * (`largeOnly = false` ≡ `.presentationDetents([.medium, .large])`).
 */
@Composable
fun QuestionResponsesView(
    questionText: String,
    storyId: String,
    userId: String,
    stickerId: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenProfile: (String) -> Unit = {},
) {
    val colors = rememberAdaptiveColors()
    var responses by remember(storyId, stickerId) { mutableStateOf<List<QuestionResponse>>(emptyList()) }
    var isLoading by remember(storyId, stickerId) { mutableStateOf(true) }
    var selectedResponse by remember { mutableStateOf<QuestionResponse?>(null) }
    var showingCreatorView by remember { mutableStateOf(false) }
    val isDetailFlow = selectedResponse != null
    val primary = colors.primary
    val secondary = colors.secondary

    LaunchedEffect(storyId, userId, stickerId) {
        isLoading = true
        responses = runCatching {
            FirebaseFirestore.getInstance()
                .collection("users").document(userId)
                .collection("stories").document(storyId)
                .collection("questionResponses").document(stickerId)
                .collection("responses")
                .orderBy("timestamp")
                .get().await().documents.map { document ->
                    QuestionResponse.from((document.data ?: emptyMap()) + ("id" to document.id))
                }
        }.getOrDefault(emptyList())
        isLoading = false
    }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.surfaceBackground),
    ) {
        // ≡ sheetHeader — momentsChromeGlass Circle
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .momentsChromeGlass(CircleShape, interactive = true)
                    .clip(CircleShape)
                    .clickable {
                        if (isDetailFlow) selectedResponse = null else onDismiss()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isDetailFlow) {
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft
                    } else {
                        Icons.Filled.KeyboardArrowDown
                    },
                    contentDescription = stringResource(R.string.common_close),
                    tint = primary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.weight(1f))
        }

        if (selectedResponse != null) {
            ShareQuestionFlow(
                questionText = questionText,
                response = selectedResponse!!,
                primary = primary,
                secondary = secondary,
                onCreateStory = { showingCreatorView = true },
            )
        } else {
            ReceivedQuestionsFlow(
                questionText = questionText,
                isLoading = isLoading,
                responses = responses,
                primary = primary,
                secondary = secondary,
                onSelect = { selectedResponse = it },
                onOpenProfile = onOpenProfile,
            )
        }
    }

    // ≡ .fullScreenCover CreatorViewWithResponseData
    if (showingCreatorView && selectedResponse != null) {
        Dialog(
            onDismissRequest = {
                showingCreatorView = false
                onDismiss()
            },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            CreatorViewWithResponseData(
                questionText = questionText,
                response = selectedResponse!!,
                onDismiss = {
                    showingCreatorView = false
                    onDismiss()
                },
            )
        }
    }
}

@Composable
private fun ReceivedQuestionsFlow(
    questionText: String,
    isLoading: Boolean,
    responses: List<QuestionResponse>,
    primary: Color,
    secondary: Color,
    onSelect: (QuestionResponse) -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Column(
            Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                stringResource(R.string.question_responses_title),
                color = primary,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.question_responses_subtitle),
                color = secondary,
                fontSize = 14.sp,
            )
        }

        Text(
            questionText,
            color = primary,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        when {
            isLoading -> {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(color = primary)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.question_responses_loading),
                        color = secondary,
                        fontSize = 14.sp,
                    )
                }
            }
            responses.isEmpty() -> {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Filled.HelpOutline,
                        contentDescription = null,
                        tint = secondary,
                        modifier = Modifier.size(34.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.question_responses_empty_title),
                        color = primary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.question_responses_empty_subtitle),
                        color = secondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            else -> {
                LazyColumn(Modifier.weight(1f).padding(bottom = 12.dp)) {
                    itemsIndexed(responses, key = { _, item -> item.id }) { index, response ->
                        QuestionResponseRow(
                            response = response,
                            primary = primary,
                            secondary = secondary,
                            onShare = { onSelect(response) },
                            onOpenProfile = onOpenProfile,
                        )
                        if (index < responses.lastIndex) {
                            HorizontalDivider(
                                Modifier.padding(horizontal = 20.dp),
                                color = primary.copy(alpha = 0.08f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareQuestionFlow(
    questionText: String,
    response: QuestionResponse,
    primary: Color,
    secondary: Color,
    onCreateStory: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(
            Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                stringResource(R.string.question_responses_share_response),
                color = primary,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.question_responses_share_subtitle),
                color = secondary,
                fontSize = 14.sp,
            )
        }

        GlassInfoCard(
            label = stringResource(R.string.question_responses_prompt_label),
            body = questionText,
            primary = primary,
            secondary = secondary,
            showPromptIcon = true,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        GlassInfoCard(
            label = stringResource(R.string.question_responses_question_label),
            body = response.response,
            primary = primary,
            secondary = secondary,
            bodyFontWeight = FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        Spacer(Modifier.weight(1f))

        // ≡ createStory button momentsChromeGlass RoundedRect 18
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
                .momentsChromeGlass(RoundedCornerShape(18.dp), interactive = true)
                .clip(RoundedCornerShape(18.dp))
                .clickable(onClick = onCreateStory)
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.question_responses_create_story),
                color = primary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun GlassInfoCard(
    label: String,
    body: String,
    primary: Color,
    secondary: Color,
    modifier: Modifier = Modifier,
    showPromptIcon: Boolean = false,
    bodyFontWeight: FontWeight = FontWeight.SemiBold,
) {
    val cardShape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .fillMaxWidth()
            .momentsChromeGlass(cardShape, interactive = false)
            .clip(cardShape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(if (showPromptIcon) 10.dp else 12.dp),
    ) {
        if (showPromptIcon) {
            // ≡ Label(..., systemImage: "questionmark.bubble.fill")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icons.Filled.HelpOutline,
                    contentDescription = null,
                    tint = secondary,
                    modifier = Modifier.size(14.dp),
                )
                Text(label, color = secondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Text(label, color = secondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(body, color = primary, fontSize = if (showPromptIcon) 17.sp else 16.sp, fontWeight = bodyFontWeight)
    }
}

/** Port de `QuestionResponseRow`. */
@Composable
private fun QuestionResponseRow(
    response: QuestionResponse,
    primary: Color,
    secondary: Color,
    onShare: () -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    var username by remember(response.userId) { mutableStateOf("") }

    LaunchedEffect(response.userId) {
        username = runCatching {
            FirebaseFirestore.getInstance().collection("users").document(response.userId)
                .get().await().getString("username").orEmpty()
        }.getOrDefault("")
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onShare)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncProfileImageView(
            userId = response.userId,
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .clickable(
                    onClick = {
                        if (response.userId.isNotBlank()) onOpenProfile(response.userId)
                    },
                ),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = username.ifEmpty { stringResource(R.string.common_loading) },
                    color = primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    MomentsFormat.relativeTime(response.timestamp),
                    color = secondary,
                    fontSize = 12.sp,
                )
            }
            Text(response.response, color = primary, fontSize = 15.sp)
        }
        Box(
            Modifier
                .padding(top = 2.dp)
                .size(32.dp)
                .momentsChromeGlass(CircleShape, interactive = true)
                .clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = secondary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** Port de `CreatorViewWithResponseData`. */
@Composable
fun CreatorViewWithResponseData(
    @Suppress("UNUSED_PARAMETER") questionText: String,
    response: QuestionResponse,
    onDismiss: () -> Unit,
) {
    var showCreatorView by remember { mutableStateOf(true) }
    var isCreatingStory by remember { mutableStateOf(true) }
    val styleVariant = 0
    // ≡ createResponseStickerImage: questionText del sticker = response.response
    val responseText = response.response
    val sticker = remember(response.id, responseText) {
        StickerData(
            type = "questionResponse",
            content = responseText,
            position = Point(0.5, 0.5),
            scale = 1.0,
            rotation = 0.0,
            questionText = responseText,
            styleVariant = styleVariant,
        )
    }

    CreatorView(
        showCreatorView = showCreatorView,
        onShowCreatorViewChange = { visible ->
            showCreatorView = visible
            if (!visible) onDismiss()
        },
        isCreatingStory = isCreatingStory,
        onIsCreatingStoryChange = { isCreatingStory = it },
        initialSticker = sticker,
        startInCameraWhenOnlySticker = true,
        modifier = Modifier.fillMaxSize(),
    )
}
