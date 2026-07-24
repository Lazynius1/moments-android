package com.moments.android.views.creator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.BitmapFactory
import android.text.TextPaint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.utilities.HapticManager
import com.moments.android.views.creator.components.SmartLocationInputView
import com.moments.android.views.creator.components.ModernGiphyGridView
import com.moments.android.views.creator.components.AudioStickerRecordingView
import com.moments.android.views.creator.components.createGeneratedTimeStickerDraft
import com.moments.android.views.creator.components.createGeneratedWeatherStickerDraft
import com.moments.android.views.creator.components.StickerEmojiSliderPillGlyph
import com.moments.android.views.creator.components.StickerPillFlowLayout
import com.moments.android.views.creator.components.ModernLinkInputView
import com.moments.android.views.creator.components.ModernMentionInputView
import com.moments.android.views.messaging.services.ChatGiphyService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/** Port de `makeLiveSelfiePlaceholderImage` de `stickerview.swift`. */
internal fun makeLiveSelfiePlaceholderImage(sizePx: Int = 120): Bitmap =
    Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).also { bitmap ->
        val canvas = Canvas(bitmap)
        val center = sizePx / 2f
        canvas.drawCircle(center, center, center, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xEBFFFFFF.toInt() })
        canvas.drawCircle(center, center, center - 1f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x14000000
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        })
    }

/**
 * Port de `stickerview.swift` / `StickerPickerView`.
 *
 * iOS `handleCatalogSelection`:
 * - Instant (+ inline en canvas): weather/time/hashtag/poll/question/countdown/quiz/…
 * - Detail sheet: location/mention/link/emoji/GIF/…
 * Los `Modern*InputView` de countdown/poll/… existen en Swift pero el catálogo ya no los abre.
 */
private enum class StickerPickerMode {
    CATALOG,
    GIF,
    FRAME,
    REVEAL,
    AUDIO,
    EMOJI,
    MENTION_INPUT,
    LINK_INPUT,
    LOCATION_INPUT,
}

private enum class StickerCatalogCategory(
    val typeKey: String,
    @StringRes val titleRes: Int,
    val icon: ImageVector,
) {
    LOCATION("location", R.string.sticker_category_location, Icons.Filled.LocationOn),
    TRENDING_GIF("trending", R.string.sticker_category_gif, Icons.Filled.EmojiEmotions),
    EMOJI("emoji", R.string.sticker_category_emoji, Icons.Filled.EmojiEmotions),
    TIME("time", R.string.sticker_category_time, Icons.Filled.AccessTime),
    WEATHER("weather", R.string.sticker_category_weather, Icons.Filled.WbSunny),
    SELFIE("selfie", R.string.sticker_category_selfie, Icons.Filled.CameraAlt),
    FRAME("frame", R.string.sticker_category_frame, Icons.Filled.Photo),
    REVEAL("reveal", R.string.sticker_category_reveal, Icons.Filled.VisibilityOff),
    AUDIO("audio", R.string.sticker_category_audio, Icons.Filled.Mic),
    HASHTAG("hashtag", R.string.sticker_category_hashtag, Icons.Filled.Tag),
    MENTION("mention", R.string.sticker_category_mention, Icons.Filled.AlternateEmail),
    LINK("link", R.string.sticker_category_link, Icons.Filled.Link),
    POLL("poll", R.string.sticker_category_poll, Icons.Filled.BarChart),
    QUESTION("question", R.string.sticker_category_question, Icons.Filled.HelpOutline),
    QUIZ("quiz", R.string.sticker_category_quiz, Icons.Filled.Checklist),
    EMOJI_SLIDER("emojiSlider", R.string.sticker_category_emoji_slider, Icons.Filled.Mood),
    COUNTDOWN("countdown", R.string.sticker_category_countdown, Icons.Filled.Timer),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerPickerView(
    onStickerCreated: (StoryStickerDraft) -> Unit,
    onSelfieRequested: () -> Unit,
    hasRevealSticker: Boolean,
    onDismiss: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current
    val bg = if (isDark) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    val fg = if (isDark) Color.White else Color.Black.copy(0.86f)
    val muted = fg.copy(0.55f)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(StickerPickerMode.CATALOG) }
    var emojiCategory by remember { mutableStateOf(StickerEmojiCategory.SMILEYS) }
    var mentionInput by remember { mutableStateOf("") }
    var linkUrlInput by remember { mutableStateOf("") }
    var linkTitleInput by remember { mutableStateOf("") }
    var gifSearchInput by remember { mutableStateOf("") }
    var giphyResults by remember { mutableStateOf(emptyList<com.moments.android.views.creator.components.GiphyGif>()) }
    var isLoadingGiphy by remember { mutableStateOf(false) }
    var isLoadingMoreGiphy by remember { mutableStateOf(false) }
    var hasMoreGiphyPages by remember { mutableStateOf(true) }
    var giphyNextOffset by remember { mutableStateOf(0) }
    var giphyActiveQuery by remember { mutableStateOf("") }

    fun emit(draft: StoryStickerDraft) {
        HapticManager.shared.lightImpact()
        onStickerCreated(draft)
    }

    val framePhotoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
            }
            bitmap?.let {
                emit(
                    StoryStickerDraft(
                        type = "frame",
                        image = it,
                        frameStyle = "classic",
                        contentScale = 1.0,
                        contentOffsetX = 0.0,
                        contentOffsetY = 0.0,
                    ),
                )
            }
        }
    }

    fun jitteredCenter() = 0.5 + Random.nextDouble(-0.06, 0.06) to 0.42 + Random.nextDouble(-0.06, 0.06)

    /** Port de `loadTrendingStickers` / `searchTrendingStickers` / `fetchGiphyPage`. */
    fun fetchGiphyPage(append: Boolean) {
        if (append && (!hasMoreGiphyPages || isLoadingGiphy || isLoadingMoreGiphy)) return
        if (append) isLoadingMoreGiphy = true else {
            isLoadingGiphy = true
            isLoadingMoreGiphy = false
            giphyResults = emptyList()
            giphyNextOffset = 0
            hasMoreGiphyPages = true
        }
        val query = gifSearchInput.trim()
        giphyActiveQuery = query
        val offset = if (append) giphyNextOffset else 0
        scope.launch {
            runCatching {
                ChatGiphyService.fetch(
                    function = ChatGiphyService.FunctionName.STICKERS,
                    mode = if (query.isBlank()) ChatGiphyService.Mode.TRENDING else ChatGiphyService.Mode.SEARCH,
                    query = query.takeIf { it.isNotBlank() },
                    offset = offset,
                    limit = 24,
                )
            }.onSuccess { page ->
                if (append) {
                    val known = giphyResults.mapTo(mutableSetOf()) { it.id }
                    giphyResults = giphyResults + page.items.filter { known.add(it.id) }
                } else if (giphyActiveQuery == query) {
                    giphyResults = page.items
                }
                giphyNextOffset = page.nextOffset
                hasMoreGiphyPages = page.hasMore
            }
            isLoadingGiphy = false
            isLoadingMoreGiphy = false
        }
    }

    LaunchedEffect(mode) {
        if (mode == StickerPickerMode.GIF && giphyResults.isEmpty() && !isLoadingGiphy) {
            fetchGiphyPage(append = false)
        }
    }

    fun createTimeDraft(): StoryStickerDraft {
        val (x, y) = jitteredCenter()
        return createGeneratedTimeStickerDraft(x, y)
    }

    fun createHashtagDraft(raw: String): StoryStickerDraft {
        val cleaned = raw.trim().removePrefix("#")
        return StoryStickerDraft(
            type = "hashtag",
            content = if (cleaned.isBlank()) "#" else "#$cleaned",
            hashtag = cleaned,
        )
    }

    /** iOS `createHashtagPlaceholderSticker`. */
    fun createHashtagPlaceholder(): StoryStickerDraft = StoryStickerDraft(
        type = "hashtag",
        content = "#",
        hashtag = "",
    )

    fun createMentionDraft(raw: String): StoryStickerDraft {
        val cleaned = raw.trim().removePrefix("@")
        return StoryStickerDraft(
            type = "mention",
            content = if (cleaned.isBlank()) "@" else "@$cleaned",
            username = cleaned,
            userId = "",
        )
    }

    fun createPollDraft(q: String, a: String, b: String): StoryStickerDraft {
        val poll = listOf(q.trim(), a.trim(), b.trim())
        return StoryStickerDraft(
            type = "poll",
            content = poll[0].ifBlank { "Poll" },
            pollOptions = poll,
            questionText = poll[0],
        )
    }

    /** iOS `createPollSticker(["", "", ""])`. */
    fun createPollPlaceholder(): StoryStickerDraft = createPollDraft("", "", "")

    fun createQuestionDraft(raw: String): StoryStickerDraft {
        val q = raw.trim()
        return StoryStickerDraft(
            type = "question",
            content = q.ifBlank { "?" },
            questionText = q,
        )
    }

    fun createQuestionPlaceholder(): StoryStickerDraft = createQuestionDraft("")

    fun createLinkDraft(urlRaw: String, titleRaw: String): StoryStickerDraft? {
        val normalized = normalizeStickerUrl(urlRaw) ?: return null
        val host = stickerHostLabel(normalized)
        val title = titleRaw.trim().ifBlank { host }
        return StoryStickerDraft(
            type = "link",
            content = title,
            linkURL = normalized,
            linkTitle = title,
            caption = host,
        )
    }

    fun createLocationDraft(name: String, lat: Double?, lng: Double?): StoryStickerDraft {
        val cleaned = name.trim()
        val (x, y) = jitteredCenter()
        return StoryStickerDraft(
            type = "location",
            content = cleaned,
            normalizedX = x,
            normalizedY = y,
            location = cleaned,
            latitude = lat,
            longitude = lng,
        )
    }

    /** iOS `createCountdownSticker(title: "", targetAtMs: now+86400)`. */
    fun createCountdownPlaceholder(): StoryStickerDraft {
        val (x, y) = jitteredCenter()
        return StoryStickerDraft(
            type = "countdown",
            content = "",
            normalizedX = x,
            normalizedY = y,
            countdownTitle = "",
            countdownTargetAtMs = System.currentTimeMillis() + 86_400_000.0,
        )
    }

    /** iOS `createQuizSticker(question: "", options: ["", "", ""], correctIndex: 0)`. */
    fun createQuizPlaceholder(): StoryStickerDraft {
        val (x, y) = jitteredCenter()
        return StoryStickerDraft(
            type = "quiz",
            content = "",
            normalizedX = x,
            normalizedY = y,
            quizQuestion = "",
            quizOptions = listOf("", "", ""),
            quizCorrectIndex = 0,
        )
    }

    /** iOS `createEmojiSliderSticker(prompt: "", emoji: "😍")`. */
    fun createEmojiSliderPlaceholder(): StoryStickerDraft {
        val (x, y) = jitteredCenter()
        return StoryStickerDraft(
            type = "emojiSlider",
            content = "😍",
            normalizedX = x,
            normalizedY = y,
            sliderEmoji = "😍",
            sliderPrompt = "",
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = bg,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.78f)
                .padding(bottom = 12.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (mode != StickerPickerMode.CATALOG) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .momentsChromeGlass(CircleShape, interactive = true)
                            .clickable { mode = StickerPickerMode.CATALOG },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = fg, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.size(8.dp))
                }
                Text(
                    when (mode) {
                        StickerPickerMode.CATALOG -> stringResource(R.string.sticker_picker_title)
                        StickerPickerMode.GIF -> stringResource(R.string.sticker_category_gif)
                        StickerPickerMode.FRAME -> stringResource(R.string.sticker_category_frame)
                        StickerPickerMode.REVEAL -> stringResource(R.string.sticker_category_reveal)
                        StickerPickerMode.AUDIO -> stringResource(R.string.sticker_category_audio)
                        StickerPickerMode.EMOJI -> stringResource(R.string.sticker_category_emoji)
                        StickerPickerMode.MENTION_INPUT -> stringResource(R.string.sticker_category_mention)
                        StickerPickerMode.LINK_INPUT -> stringResource(R.string.sticker_category_link)
                        StickerPickerMode.LOCATION_INPUT -> stringResource(R.string.sticker_category_location)
                    },
                    color = fg,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                )
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .size(36.dp)
                        .momentsChromeGlass(CircleShape, interactive = true)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Close, null, tint = fg, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(Modifier.height(12.dp))

            when (mode) {
                StickerPickerMode.CATALOG -> {
                    Text(
                        stringResource(R.string.sticker_picker_more_soon),
                        color = muted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        StickerPillFlowLayout(
                            modifier = Modifier.fillMaxWidth(),
                            spacing = 10.dp,
                            rowSpacing = 10.dp,
                        ) {
                            StickerCatalogCategory.entries
                                .filter { it != StickerCatalogCategory.REVEAL || !hasRevealSticker }
                                .forEach { cat ->
                                    Row(
                                        Modifier
                                            .clip(CircleShape)
                                            .background(fg.copy(0.08f))
                                            .clickable {
                                        HapticManager.shared.lightImpact()
                                        // Espejo iOS handleCatalogSelection / insertInstantCategory
                                        when (cat) {
                                            StickerCatalogCategory.LOCATION -> mode = StickerPickerMode.LOCATION_INPUT
                                            StickerCatalogCategory.TRENDING_GIF -> mode = StickerPickerMode.GIF
                                            StickerCatalogCategory.EMOJI -> mode = StickerPickerMode.EMOJI
                                            StickerCatalogCategory.TIME -> emit(createTimeDraft())
                                            StickerCatalogCategory.WEATHER -> scope.launch {
                                                val (x, y) = jitteredCenter()
                                                emit(createGeneratedWeatherStickerDraft(x, y))
                                            }
                                            StickerCatalogCategory.SELFIE -> {
                                                onSelfieRequested()
                                                onDismiss()
                                            }
                                            StickerCatalogCategory.FRAME -> mode = StickerPickerMode.FRAME
                                            StickerCatalogCategory.REVEAL -> mode = StickerPickerMode.REVEAL
                                            StickerCatalogCategory.AUDIO -> mode = StickerPickerMode.AUDIO
                                            StickerCatalogCategory.HASHTAG -> emit(createHashtagPlaceholder())
                                            StickerCatalogCategory.MENTION -> {
                                                mentionInput = ""
                                                mode = StickerPickerMode.MENTION_INPUT
                                            }
                                            StickerCatalogCategory.LINK -> {
                                                linkUrlInput = ""
                                                linkTitleInput = ""
                                                mode = StickerPickerMode.LINK_INPUT
                                            }
                                            StickerCatalogCategory.POLL -> emit(createPollPlaceholder())
                                            StickerCatalogCategory.QUESTION -> emit(createQuestionPlaceholder())
                                            StickerCatalogCategory.QUIZ -> emit(createQuizPlaceholder())
                                            StickerCatalogCategory.EMOJI_SLIDER -> emit(createEmojiSliderPlaceholder())
                                            StickerCatalogCategory.COUNTDOWN -> emit(createCountdownPlaceholder())
                                        }
                                    }
                                            .padding(
                                                horizontal = if (cat == StickerCatalogCategory.EMOJI_SLIDER) 10.dp else 14.dp,
                                                vertical = 12.dp,
                                            ),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        if (cat == StickerCatalogCategory.EMOJI_SLIDER) {
                                            StickerEmojiSliderPillGlyph(Modifier.size(width = 122.dp, height = 28.dp))
                                        } else {
                                            Icon(cat.icon, null, tint = fg, modifier = Modifier.size(18.dp))
                                            Text(
                                                stringResource(cat.titleRes),
                                                color = fg,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 15.sp,
                                            )
                                        }
                                    }
                                }
                            }
                    }
                }

                StickerPickerMode.GIF -> {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .momentsChromeGlass(RoundedCornerShape(14.dp), interactive = false)
                            .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicTextField(
                            value = gifSearchInput,
                            onValueChange = { gifSearchInput = it.take(80) },
                            singleLine = true,
                            textStyle = TextStyle(color = fg, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                            cursorBrush = SolidColor(fg),
                            modifier = Modifier.weight(1f),
                            decorationBox = { inner ->
                                if (gifSearchInput.isBlank()) {
                                    Text(stringResource(R.string.sticker_gif_search_hint), color = muted, fontSize = 15.sp)
                                }
                                inner()
                            },
                        )
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = stringResource(R.string.sticker_gif_search_hint),
                            tint = fg,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .clickable { fetchGiphyPage(append = false) }
                                .padding(8.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    when {
                        isLoadingGiphy && giphyResults.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            androidx.compose.material3.CircularProgressIndicator(color = fg)
                        }
                        giphyResults.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.sticker_gif_empty), color = muted, fontSize = 14.sp)
                        }
                        else -> ModernGiphyGridView(
                            gifs = giphyResults,
                            onSelect = { gif ->
                                val url = gif.preferredStickerUrl ?: return@ModernGiphyGridView
                                val (x, y) = jitteredCenter()
                                emit(
                                    StoryStickerDraft(
                                        type = "sticker",
                                        content = url,
                                        normalizedX = x,
                                        normalizedY = y,
                                        gifURL = url,
                                        isAnimated = true,
                                    ),
                                )
                            },
                            onReachEnd = {
                                if (!isLoadingMoreGiphy) fetchGiphyPage(append = true)
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                StickerPickerMode.FRAME -> {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 30.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        Text(
                            stringResource(R.string.sticker_frame_title),
                            color = fg,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            stringResource(R.string.sticker_frame_subtitle),
                            color = muted,
                            fontSize = 16.sp,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF6B73FF))
                                .clickable {
                                    HapticManager.shared.mediumImpact()
                                    framePhotoPicker.launch("image/*")
                                }
                                .padding(vertical = 15.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Photo, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.size(8.dp))
                            Text(
                                stringResource(R.string.sticker_frame_select_photo),
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                StickerPickerMode.REVEAL -> {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 30.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        Text(stringResource(R.string.sticker_reveal_title), color = fg, fontSize = 24.sp, fontWeight = FontWeight.Black)
                        Text(stringResource(R.string.sticker_reveal_subtitle), color = muted, fontSize = 16.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Text(
                            stringResource(R.string.sticker_reveal_add_layer),
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF7E57C2))
                                .clickable {
                                    emit(StoryStickerDraft(type = "reveal", revealType = "solid", revealPattern = "dots", revealPrimaryColor = "#000000", revealSecondaryColor = "#000000", revealEffectColor = "#FFFFFF"))
                                }
                                .padding(vertical = 15.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }

                StickerPickerMode.AUDIO -> AudioStickerRecordingView(
                    onAdd = { file, duration ->
                        emit(StoryStickerDraft(type = "audio", audioURL = file.absolutePath, audioDuration = duration))
                    },
                )

                StickerPickerMode.EMOJI -> {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StickerEmojiCategory.entries.forEach { cat ->
                            val selected = cat == emojiCategory
                            Text(
                                stringResource(cat.titleRes),
                                color = if (selected) Color.White else fg,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(if (selected) fg.copy(if (isDark) 0.35f else 0.82f) else Color.Transparent)
                                    .border(1.dp, fg.copy(0.22f), RoundedCornerShape(50))
                                    .clickable { emojiCategory = cat }
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    val emojis = remember(emojiCategory) { StickerEmojiCatalog.emojis(emojiCategory) }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 44.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(emojis, key = { it }) { emoji ->
                            Box(
                                Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        emit(
                                            StoryStickerDraft(
                                                type = "emoji",
                                                content = emoji,
                                            ),
                                        )
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(emoji, fontSize = 28.sp)
                            }
                        }
                    }
                }

                StickerPickerMode.MENTION_INPUT -> ModernMentionInputView(
                    onSelect = { username -> emit(createMentionDraft(username)) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                StickerPickerMode.LINK_INPUT -> ModernLinkInputView(
                    onSelect = { url, title -> createLinkDraft(url, title)?.let { emit(it) } },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                StickerPickerMode.LOCATION_INPUT -> {
                    SmartLocationInputView(
                        onSelect = { name, lat, lng ->
                            emit(createLocationDraft(name, lat, lng))
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun StickerTextForm(
    prefix: String,
    hint: String,
    value: String,
    onValueChange: (String) -> Unit,
    fg: Color,
    muted: Color,
    isDark: Boolean,
    onDone: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(hint, color = muted, fontSize = 13.sp)
        Row(
            Modifier
                .fillMaxWidth()
                .momentsChromeGlass(RoundedCornerShape(14.dp), interactive = false)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(prefix, color = fg, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = fg, fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                cursorBrush = SolidColor(fg),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 6.dp),
            )
        }
        DoneChip(fg, isDark, onDone)
    }
}

@Composable
private fun StickerPlainField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    fg: Color,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .momentsChromeGlass(RoundedCornerShape(14.dp), interactive = false)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (value.isEmpty()) {
            Text(placeholder, color = fg.copy(0.4f), fontSize = 15.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = fg, fontSize = 15.sp, fontWeight = FontWeight.Medium),
            cursorBrush = SolidColor(fg),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DoneChip(fg: Color, isDark: Boolean, onDone: () -> Unit) {
    Text(
        stringResource(R.string.common_done),
        color = if (isDark) Color.Black else Color.White,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(fg)
            .clickable(onClick = onDone)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    )
}

/** Rasteriza emoji a PNG como iOS `createEmojiSticker` (200×200). */
fun renderEmojiStickerBitmap(emoji: String, size: Int = 200): Bitmap {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size * 0.75f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT
        color = android.graphics.Color.BLACK
    }
    val fm = paint.fontMetrics
    val y = size / 2f - (fm.ascent + fm.descent) / 2f
    canvas.drawText(emoji, size / 2f, y, paint)
    return bmp
}

/** iOS `normalizedStickerURL(from:)`. */
fun normalizeStickerUrl(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    val withScheme = when {
        trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
        else -> "https://$trimmed"
    }
    return runCatching {
        val uri = android.net.Uri.parse(withScheme)
        if (uri.host.isNullOrBlank()) null else uri.toString()
    }.getOrNull()
}

fun stickerHostLabel(url: String): String {
    val host = android.net.Uri.parse(url).host?.removePrefix("www.") ?: url
    return host.take(40)
}
