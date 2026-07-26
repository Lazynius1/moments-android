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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.shadow
import com.moments.android.views.messaging.components.AttachmentIcon
import com.moments.android.views.messaging.components.AttachmentIconPreset
import com.moments.android.views.messaging.components.AttachmentIconView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEmotions
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
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
import com.moments.android.views.creator.components.AnimatedGIFView
import com.moments.android.views.creator.components.AudioStickerRecordingView
import com.moments.android.views.creator.components.createGeneratedTimeStickerDraft
import com.moments.android.views.creator.components.createGeneratedWeatherStickerDraft
import com.moments.android.views.creator.components.StickerEmojiSliderPillGlyph
import com.moments.android.views.creator.components.StickerPillFlowLayout
import com.moments.android.views.creator.components.ModernLinkInputView
import com.moments.android.views.creator.components.ModernMentionInputView
import com.moments.android.utilities.momentsPress
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


/** ≡ `pressAnimation()` + clickable con la misma `interactionSource`. */
private fun Modifier.stickerPressClickable(onClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    this
        .momentsPress(interaction)
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
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

/**
 * Port de `StickerPickerView.StickerCategory` — orden ≡ `catalogCategories` iOS.
 * [accent] ≡ `accentColor`; [attachmentIcon] ≡ `attachmentIcon`.
 */
private enum class StickerCatalogCategory(
    val typeKey: String,
    @StringRes val titleRes: Int,
    val icon: ImageVector,
    val accent: Long,
) {
    LOCATION("location", R.string.sticker_category_location, Icons.Filled.LocationOn, 0xFF8752FA),
    MENTION("mention", R.string.sticker_category_mention, Icons.Filled.AlternateEmail, 0xFFFA801F),
    TRENDING_GIF("trending", R.string.sticker_category_gif, Icons.Filled.EmojiEmotions, 0xFF54D670),
    EMOJI("emoji", R.string.sticker_category_emoji, Icons.Filled.EmojiEmotions, 0xFFFFB82E),
    LINK("link", R.string.sticker_category_link, Icons.Filled.Link, 0xFF4AB7FA),
    QUESTION("question", R.string.sticker_category_question, Icons.AutoMirrored.Filled.HelpOutline, 0xFFD942D6),
    POLL("poll", R.string.sticker_category_poll, Icons.Filled.BarChart, 0xFFE840BD),
    QUIZ("quiz", R.string.sticker_category_quiz, Icons.Filled.Checklist, 0xFFFF9800),
    REVEAL("reveal", R.string.sticker_category_reveal, Icons.Filled.VisibilityOff, 0xFF9C27B0),
    AUDIO("audio", R.string.sticker_category_audio, Icons.Filled.Mic, 0xFFFF664D),
    FRAME("frame", R.string.sticker_category_frame, Icons.Filled.Photo, 0xFF6B73FF),
    EMOJI_SLIDER("emojiSlider", R.string.sticker_category_emoji_slider, Icons.Filled.Mood, 0xFFFC8F36),
    HASHTAG("hashtag", R.string.sticker_category_hashtag, Icons.Filled.Tag, 0xFFEB3AE0),
    COUNTDOWN("countdown", R.string.sticker_category_countdown, Icons.Filled.Timer, 0xFF9C57F7),
    WEATHER("weather", R.string.sticker_category_weather, Icons.Filled.WbSunny, 0xFF33C4F2),
    TIME("time", R.string.sticker_category_time, Icons.Filled.AccessTime, 0xFFFF9E33),
    SELFIE("selfie", R.string.sticker_category_selfie, Icons.Filled.CameraAlt, 0xFFFF408C),
    ;

    val accentColor: Color get() = Color(accent)

    /** ≡ `StickerCategory.attachmentIcon`. */
    val attachmentIcon: AttachmentIcon?
        get() = when (this) {
            TRENDING_GIF -> AttachmentIcon.GIF
            LOCATION -> AttachmentIcon.LOCATION
            SELFIE -> AttachmentIcon.CAMERA
            FRAME -> AttachmentIcon.PHOTOS
            else -> null
        }
}

/** iOS `trendingEmojis` (QuickEmojiRow + MomentsEmojiGrid). */
private val trendingEmojis = listOf(
    "😍", "🔥", "💯", "✨", "😂", "🥺", "💕", "🎉", "😎", "🤩", "💀", "🙄",
    "😭", "❤️", "🥳", "😘", "🤝", "👑", "💪", "🌟", "🦋", "🌈", "⚡", "💎",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerPickerView(
    onStickerCreated: (StoryStickerDraft) -> Unit,
    onSelfieRequested: () -> Unit,
    hasRevealSticker: Boolean,
    isVideo: Boolean = false,
    hasAudioSticker: Boolean = false,
    onDismiss: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current
    val bg = if (isDark) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    val fg = if (isDark) Color.White else Color.Black.copy(0.86f)
    val muted = fg.copy(0.55f)
    val chromeFill = if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.04f)
    val chromeStroke = if (isDark) Color.White.copy(0.12f) else Color.Black.copy(0.08f)
    // iOS pillFillTop/Bottom + pillTextColor (cápsulas invertidas)
    val pillFillTop = if (isDark) Color.White else Color(0xFF0B1215)
    val pillFillBottom = if (isDark) Color(0xFFF7F2E8) else Color(0xFF141D22)
    val pillText = if (isDark) Color.Black.copy(0.92f) else Color.White
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(StickerPickerMode.CATALOG) }
    var catalogSearchText by remember { mutableStateOf("") }
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

    /** ≡ `createGiphySticker` — gifURL + fallback bitmap downscale 180. */
    fun emitGiphySticker(gif: com.moments.android.views.creator.components.GiphyGif) {
        val url = gif.preferredStickerUrl ?: return
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    val connection = java.net.URL(url).openConnection()
                    connection.connectTimeout = 8_000
                    connection.readTimeout = 8_000
                    connection.getInputStream().use { BitmapFactory.decodeStream(it) }
                }.getOrNull()?.let { src -> downscaleBitmapIfNeeded(src, maxDimension = 180) }
            }
            val (x, y) = 0.5 + Random.nextDouble(-0.06, 0.06) to 0.42 + Random.nextDouble(-0.06, 0.06)
            emit(
                StoryStickerDraft(
                    type = "sticker",
                    content = url,
                    normalizedX = x,
                    normalizedY = y,
                    gifURL = url,
                    isAnimated = true,
                    image = bitmap,
                ),
            )
        }
    }

    fun filteredCatalogCategories(): List<StickerCatalogCategory> {
        var base = StickerCatalogCategory.entries.toList()
        if (hasRevealSticker) base = base.filter { it != StickerCatalogCategory.REVEAL }
        if (isVideo || hasAudioSticker) base = base.filter { it != StickerCatalogCategory.AUDIO }
        val query = catalogSearchText.trim()
        if (query.isEmpty()) return base
        return base.filter {
            context.getString(it.titleRes).contains(query, ignoreCase = true)
        }
    }

    val framePhotoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
                    ?.let { downscaleBitmapIfNeeded(it, maxDimension = 800) }
            }
            bitmap?.let {
                HapticManager.shared.mediumImpact()
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

    /** iOS `cleanupMemory`. */
    fun cleanupMemory() {
        giphyResults = emptyList()
        isLoadingGiphy = false
        isLoadingMoreGiphy = false
        catalogSearchText = ""
        gifSearchInput = ""
    }

    DisposableEffect(Unit) {
        onDispose { cleanupMemory() }
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

    LaunchedEffect(Unit) {
        if (giphyResults.isEmpty() && !isLoadingGiphy) {
            fetchGiphyPage(append = false)
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

    fun createMentionDraft(user: com.moments.android.models.AppUser): StoryStickerDraft {
        val cleaned = user.username.trim().removePrefix("@")
        return StoryStickerDraft(
            type = "mention",
            content = if (cleaned.isBlank()) "@" else "@$cleaned",
            username = cleaned,
            userId = user.id,
            profileImagePath = user.profileImagePath,
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
        onDismissRequest = {
            cleanupMemory()
            onDismiss()
        },
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
                // iOS: header solo en `.detail` (catálogo = solo handle del sheet)
                if (mode != StickerPickerMode.CATALOG) {
                    Box(
                        Modifier
                            .size(42.dp)
                            .momentsChromeGlass(CircleShape, interactive = true)
                            .stickerPressClickable {
                                HapticManager.shared.lightImpact()
                                mode = StickerPickerMode.CATALOG
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = fg, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.size(8.dp))
                    Text(
                        when (mode) {
                            StickerPickerMode.GIF -> stringResource(R.string.sticker_category_gif)
                            StickerPickerMode.FRAME -> stringResource(R.string.sticker_category_frame)
                            StickerPickerMode.REVEAL -> stringResource(R.string.sticker_category_reveal)
                            StickerPickerMode.AUDIO -> stringResource(R.string.sticker_category_audio)
                            StickerPickerMode.EMOJI -> stringResource(R.string.sticker_category_emoji)
                            StickerPickerMode.MENTION_INPUT -> stringResource(R.string.sticker_category_mention)
                            StickerPickerMode.LINK_INPUT -> stringResource(R.string.sticker_category_link)
                            StickerPickerMode.LOCATION_INPUT -> stringResource(R.string.sticker_category_location)
                            StickerPickerMode.CATALOG -> ""
                        },
                        color = fg,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                    )
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier
                            .size(42.dp)
                            .momentsChromeGlass(CircleShape, interactive = true)
                            .stickerPressClickable {
                                HapticManager.shared.lightImpact()
                                cleanupMemory()
                                onDismiss()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Close, null, tint = fg, modifier = Modifier.size(16.dp))
                    }
                }
            }

            if (mode != StickerPickerMode.CATALOG) {
                Spacer(Modifier.height(12.dp))
            } else {
                Spacer(Modifier.height(4.dp))
            }

            when (mode) {
                StickerPickerMode.CATALOG -> {
                    // Catalog search ≡ CatalogSearchBar
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .momentsChromeGlass(RoundedCornerShape(50), interactive = false)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Filled.Search, null, tint = muted, modifier = Modifier.size(18.dp))
                        BasicTextField(
                            value = catalogSearchText,
                            onValueChange = { catalogSearchText = it.take(40) },
                            singleLine = true,
                            textStyle = TextStyle(color = fg, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                            cursorBrush = SolidColor(fg),
                            modifier = Modifier.weight(1f),
                            decorationBox = { inner ->
                                if (catalogSearchText.isBlank()) {
                                    Text(stringResource(R.string.sticker_search_placeholder), color = muted, fontSize = 15.sp)
                                }
                                inner()
                            },
                        )
                        if (catalogSearchText.isNotBlank()) {
                            Icon(
                                Icons.Filled.Close,
                                null,
                                tint = muted,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { catalogSearchText = "" },
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(22.dp),
                    ) {
                        val filtered = filteredCatalogCategories()
                        if (filtered.isEmpty()) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 44.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    stringResource(R.string.sticker_catalog_empty_title),
                                    color = fg,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp,
                                )
                                Text(
                                    stringResource(R.string.sticker_catalog_empty_subtitle),
                                    color = muted,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                        } else {
                            StickerPillFlowLayout(
                                modifier = Modifier.fillMaxWidth(),
                                spacing = 10.dp,
                                rowSpacing = 10.dp,
                            ) {
                                filtered.forEachIndexed { index, cat ->
                                    val isEmojiSlider = cat == StickerCatalogCategory.EMOJI_SLIDER
                                    Row(
                                        Modifier
                                            .then(
                                                if (isEmojiSlider) {
                                                    Modifier
                                                        .height(44.dp)
                                                        .widthIn(min = 148.dp)
                                                } else {
                                                    Modifier.height(46.dp)
                                                },
                                            )
                                            .rotate(catalogPillTiltDegrees(index))
                                            .offset(y = catalogPillVerticalOffsetDp(index).dp)
                                            .shadow(
                                                elevation = 8.dp,
                                                shape = CircleShape,
                                                ambientColor = Color.Black.copy(if (isDark) 0.10f else 0.06f),
                                                spotColor = Color.Black.copy(if (isDark) 0.10f else 0.06f),
                                            )
                                            .clip(CircleShape)
                                            .background(
                                                Brush.verticalGradient(listOf(pillFillTop, pillFillBottom)),
                                            )
                                            .border(
                                                1.dp,
                                                if (isDark) Color.White.copy(0.80f) else Color.White.copy(0.08f),
                                                CircleShape,
                                            )
                                            .stickerPressClickable {
                                                HapticManager.shared.mediumImpact()
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
                                                    StickerCatalogCategory.MENTION -> mode = StickerPickerMode.MENTION_INPUT
                                                    StickerCatalogCategory.LINK -> mode = StickerPickerMode.LINK_INPUT
                                                    StickerCatalogCategory.POLL -> emit(createPollPlaceholder())
                                                    StickerCatalogCategory.QUESTION -> emit(createQuestionPlaceholder())
                                                    StickerCatalogCategory.QUIZ -> emit(createQuizPlaceholder())
                                                    StickerCatalogCategory.EMOJI_SLIDER -> emit(createEmojiSliderPlaceholder())
                                                    StickerCatalogCategory.COUNTDOWN -> emit(createCountdownPlaceholder())
                                                }
                                            }
                                            .padding(horizontal = if (isEmojiSlider) 10.dp else 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        if (isEmojiSlider) {
                                            StickerEmojiSliderPillGlyph(Modifier.size(width = 122.dp, height = 28.dp))
                                        } else {
                                            // CatalogPillIcon ≡ accent + AttachmentIcon cuando aplica
                                            val attachment = cat.attachmentIcon
                                            if (attachment != null) {
                                                AttachmentIconView(
                                                    icon = attachment,
                                                    preset = AttachmentIconPreset.STICKER_CATALOG_PILL,
                                                    tintColor = cat.accentColor,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            } else {
                                                Icon(
                                                    cat.icon,
                                                    null,
                                                    tint = cat.accentColor,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }
                                            Text(
                                                stringResource(cat.titleRes),
                                                color = pillText,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 15.5.sp,
                                            )
                                        }
                                    }
                                }
                            }

                        // CatalogGifPreviewSection ≡ iOS (bajo mosaic cuando hay pills)
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    stringResource(R.string.sticker_catalog_gifs),
                                    color = fg,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    stringResource(R.string.sticker_catalog_view_all),
                                    color = fg.copy(0.82f),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.5.sp,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(chromeFill)
                                        .border(1.dp, chromeStroke, RoundedCornerShape(50))
                                        .stickerPressClickable {
                                            HapticManager.shared.mediumImpact()
                                            mode = StickerPickerMode.GIF
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                )
                            }
                            when {
                                isLoadingGiphy && giphyResults.isEmpty() -> {
                                    Column(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        androidx.compose.material3.CircularProgressIndicator(color = fg, strokeWidth = 2.dp)
                                        Text(stringResource(R.string.sticker_loading_stickers), color = fg, fontWeight = FontWeight.SemiBold)
                                        Text(stringResource(R.string.sticker_loading_time), color = muted, fontSize = 13.sp)
                                    }
                                }
                                giphyResults.isNotEmpty() -> {
                                    val preview = giphyResults.take(12)
                                    val rows = preview.chunked(3)
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        rows.forEach { row ->
                                            Row(
                                                Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            ) {
                                                row.forEach { gif ->
                                                    val url = gif.images.fixedHeight.url
                                                    Box(
                                                        Modifier
                                                            .weight(1f)
                                                            .aspectRatio(0.88f)
                                                            .clip(RoundedCornerShape(18.dp))
                                                            .background(Color.White.copy(0.06f))
                                                            .border(1.dp, Color.White.copy(0.10f), RoundedCornerShape(18.dp))
                                                            .stickerPressClickable {
                                                                HapticManager.shared.mediumImpact()
                                                                emitGiphySticker(gif)
                                                            },
                                                    ) {
                                                        if (url.isNotBlank()) {
                                                            AnimatedGIFView(
                                                                url = url,
                                                                modifier = Modifier.fillMaxSize(),
                                                            )
                                                        }
                                                    }
                                                }
                                                repeat(3 - row.size) {
                                                    Spacer(Modifier.weight(1f))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        } // end else filtered
                    }
                }

                StickerPickerMode.GIF -> {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .momentsChromeGlass(RoundedCornerShape(50), interactive = false)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Filled.Search, null, tint = muted, modifier = Modifier.size(18.dp))
                        BasicTextField(
                            value = gifSearchInput,
                            onValueChange = { gifSearchInput = it.take(80) },
                            singleLine = true,
                            textStyle = TextStyle(color = fg, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                            cursorBrush = SolidColor(fg),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    HapticManager.shared.lightImpact()
                                    fetchGiphyPage(append = false)
                                },
                            ),
                            modifier = Modifier.weight(1f),
                            decorationBox = { inner ->
                                if (gifSearchInput.isBlank()) {
                                    Text(stringResource(R.string.sticker_gif_search_hint), color = muted, fontSize = 15.sp)
                                }
                                inner()
                            },
                        )
                        if (gifSearchInput.isNotBlank()) {
                            Icon(
                                Icons.Filled.Close,
                                null,
                                tint = muted,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable {
                                        HapticManager.shared.lightImpact()
                                        gifSearchInput = ""
                                        fetchGiphyPage(append = false)
                                    },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    when {
                        isLoadingGiphy && giphyResults.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                androidx.compose.material3.CircularProgressIndicator(color = fg, strokeWidth = 2.dp)
                                Text(stringResource(R.string.sticker_loading_stickers), color = fg, fontWeight = FontWeight.SemiBold)
                                Text(stringResource(R.string.sticker_loading_time), color = muted, fontSize = 13.sp)
                            }
                        }
                        giphyResults.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(stringResource(R.string.sticker_gif_empty), color = muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    stringResource(R.string.sticker_gif_empty_subtitle),
                                    color = muted.copy(0.8f),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 24.dp).padding(top = 6.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                            }
                        }
                        else -> Column(Modifier.fillMaxSize()) {
                            ModernGiphyGridView(
                                gifs = giphyResults,
                                onSelect = { gif ->
                                    HapticManager.shared.mediumImpact()
                                    emitGiphySticker(gif)
                                },
                                onReachEnd = {
                                    if (!isLoadingMoreGiphy) fetchGiphyPage(append = true)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                            )
                            if (isLoadingMoreGiphy) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 20.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    androidx.compose.material3.CircularProgressIndicator(
                                        color = fg,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }
                        }
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
                                .stickerPressClickable {
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
                                .stickerPressClickable {
                                    HapticManager.shared.mediumImpact()
                                    // iOS: position y≈100 (arriba del canvas)
                                    emit(
                                        StoryStickerDraft(
                                            type = "reveal",
                                            normalizedX = 0.5,
                                            normalizedY = 0.12,
                                            revealType = "solid",
                                            revealPattern = "dots",
                                            revealPrimaryColor = "#000000",
                                            revealSecondaryColor = "#000000",
                                            revealEffectColor = "#FFFFFF",
                                        ),
                                    )
                                }
                                .padding(vertical = 15.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }

                StickerPickerMode.AUDIO -> AudioStickerRecordingView(
                    onAdd = { file, duration ->
                        HapticManager.shared.mediumImpact()
                        emit(StoryStickerDraft(type = "audio", audioURL = file.absolutePath, audioDuration = duration))
                    },
                )

                StickerPickerMode.EMOJI -> {
                    // QuickEmojiRow ≡ trendingEmojis.prefix(12)
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(trendingEmojis.take(12), key = { it }) { emoji ->
                            Box(
                                Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(0.06f))
                                    .border(1.dp, Color.White.copy(0.08f), CircleShape)
                                    .stickerPressClickable {
                                        HapticManager.shared.lightImpact()
                                        emit(StoryStickerDraft(type = "emoji", content = emoji))
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(emoji, fontSize = 23.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    // MomentsEmojiGrid ≡ trendingEmojis full grid (6 cols)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(6),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(trendingEmojis, key = { "g_$it" }) { emoji ->
                            Box(
                                Modifier
                                    .height(48.dp)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.White.copy(0.06f))
                                    .border(1.dp, Color.White.copy(0.08f), RoundedCornerShape(50))
                                    .stickerPressClickable {
                                        HapticManager.shared.lightImpact()
                                        emit(StoryStickerDraft(type = "emoji", content = emoji))
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(emoji, fontSize = 30.sp)
                            }
                        }
                    }
                }

                StickerPickerMode.MENTION_INPUT -> ModernMentionInputView(
                    onSelect = { user -> emit(createMentionDraft(user)) },
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


/** iOS `downscaleImageIfNeeded` (default max 800). */
fun downscaleBitmapIfNeeded(src: Bitmap, maxDimension: Int = 800): Bitmap {
    val maxSide = maxOf(src.width, src.height)
    if (maxSide <= maxDimension) return src
    val scale = maxDimension.toFloat() / maxSide
    val w = (src.width * scale).toInt().coerceAtLeast(1)
    val h = (src.height * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(src, w, h, true).also { if (it !== src) src.recycle() }
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

/** iOS `catalogPillTilt(for:)`. */
private fun catalogPillTiltDegrees(index: Int): Float = when (index % 6) {
    0 -> -2f
    1 -> 1.4f
    2 -> -1f
    3 -> 2f
    4 -> -1.6f
    else -> 0.8f
}

/** iOS `catalogPillVerticalOffset(for:)`. */
private fun catalogPillVerticalOffsetDp(index: Int): Float = when (index % 5) {
    0 -> 0f
    1 -> 2f
    2 -> -1f
    3 -> 1f
    else -> -2f
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
