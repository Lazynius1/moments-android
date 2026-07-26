package com.moments.android.views.creator.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.AppUser
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.fetchMutuals
import com.moments.android.services.firestore.searchUsers
import com.moments.android.utilities.HapticManager
import com.moments.android.views.components.StickerEmojiSliderCardView
import com.moments.android.views.creator.StickerEmojiPalettePicker
import com.moments.android.views.creator.normalizeStickerUrl
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Port de `StickerInputViews.swift`.
 *
 * Cableado iOS (`stickerview.handleCatalogSelection`):
 * - **Detail (sí se muestran):** Mention, Link (+ Location aparte).
 * - **Catálogo = `insertInstantCategory` (placeholders inline IG):**
 *   Hashtag, Poll, Question, Quiz, Countdown, EmojiSlider.
 *   Los `Modern*InputView` de esos tipos viven en el switch `.detail` pero el
 *   catálogo no abre ese modo — se portan aquí por paridad de archivo / uso futuro.
 */

private val linkAccent = Color(0.29f, 0.72f, 0.98f)
private val countdownAccent = Color(0.61f, 0.34f, 0.97f)
private val emojiSliderAccent = Color(0.99f, 0.56f, 0.21f)
private val hashtagAccent = Color(0xFFFF2D55)
private val pollAccent = Color(0xFF5856D6)

// region Glass helpers ≡ StickerGlassFieldModifier / ActionBackground

@Composable
private fun Modifier.stickerGlassField(
    accentColor: Color,
    isFocused: Boolean,
    cornerRadius: Float = 16f,
): Modifier = this
    .momentsChromeGlass(RoundedCornerShape(cornerRadius.dp), interactive = true)
    .then(
        if (isFocused) {
            Modifier.border(1.dp, accentColor.copy(alpha = 0.18f), RoundedCornerShape(cornerRadius.dp))
        } else {
            Modifier
        },
    )

@Composable
private fun Modifier.stickerGlassAction(isEnabled: Boolean, cornerRadius: Float = 16f): Modifier =
    this
        .momentsChromeGlass(RoundedCornerShape(cornerRadius.dp), interactive = isEnabled)
        .graphicsLayer { alpha = if (isEnabled) 1f else 0.62f }

// endregion

// region Supporting views

@Composable
fun StickerInputSectionHeader(
    title: String,
    accent: Color,
    icon: ImageVector? = null,
) {
    val palette = rememberStickerDetailPalette()
    Row(
        Modifier.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(14.dp))
        } else {
            Box(Modifier.size(8.dp).background(accent, CircleShape))
        }
        Text(title, color = palette.secondaryText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

@Composable
fun StickerUserRowView(user: AppUser, onTap: () -> Unit) {
    val palette = rememberStickerDetailPalette()
    var imageFailed by remember(user.id) { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val path = user.profileImagePath
        if (!path.isNullOrBlank() && !imageFailed) {
            AsyncImage(
                model = path,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { imageFailed = true },
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .border(0.5.dp, palette.divider, CircleShape),
            )
        } else {
            Box(
                Modifier
                    .size(50.dp)
                    .background(
                        Brush.linearGradient(listOf(Color.Blue.copy(0.6f), Color(0xFF9C27B0).copy(0.6f))),
                        CircleShape,
                    )
                    .border(0.5.dp, palette.divider, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    user.username.take(1).uppercase(Locale.getDefault()),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(user.username, color = palette.primaryText, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                if (user.isPlusSubscriber) {
                    Icon(Icons.Filled.Star, null, tint = Color(0xFFFFCC00), modifier = Modifier.size(14.dp))
                }
            }
            user.bio?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = palette.secondaryText, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 1)
            }
            if (user.isPrivate) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, null, tint = palette.tertiaryText, modifier = Modifier.size(10.dp))
                    Text(
                        stringResource(R.string.sticker_private_account),
                        color = palette.tertiaryText,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                    )
                }
            }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = palette.tertiaryText, modifier = Modifier.size(16.dp))
    }
}

@Composable
fun SkeletonUserRow() {
    val palette = rememberStickerDetailPalette()
    val fill = palette.skeletonFill
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(50.dp).background(fill, CircleShape))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(width = 120.dp, height = 14.dp).background(fill, RoundedCornerShape(4.dp)))
            Box(Modifier.size(width = 80.dp, height = 12.dp).background(fill, RoundedCornerShape(4.dp)))
        }
    }
}

@Composable
fun StickerEmptySearchView(searchQuery: String) {
    val palette = rememberStickerDetailPalette()
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 40.dp, start = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.sticker_no_users_found),
            color = palette.primaryText,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
        )
        Text(
            stringResource(R.string.sticker_try_different_username, searchQuery),
            color = palette.secondaryText,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
        )
    }
}

// endregion

// region Mention (cableado en detail)

@Composable
fun ModernMentionInputView(
    onSelect: (AppUser) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = rememberStickerDetailPalette()
    val context = LocalContext.current
    val firestore = remember { FirestoreService() }
    var searchText by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<AppUser>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var recentUsers by remember { mutableStateOf<List<AppUser>>(emptyList()) }
    var suggestedUsers by remember { mutableStateOf<List<AppUser>>(emptyList()) }
    var suggestionsLoading by remember { mutableStateOf(true) }
    var fieldFocused by remember { mutableStateOf(false) }

    fun prefs() = context.getSharedPreferences("moments_sticker_inputs", android.content.Context.MODE_PRIVATE)

    fun saveRecentUser(user: AppUser) {
        val existing = runCatching {
            JSONArray(prefs().getString("recentMentionedUsersJson", "[]")).let { arr ->
                buildList { for (i in 0 until arr.length()) add(arr.getString(i)) }
            }
        }.getOrDefault(emptyList())
        val next = (listOf(user.id) + existing.filter { it != user.id }).take(10)
        prefs().edit().putString("recentMentionedUsersJson", JSONArray(next).toString()).apply()
        recentUsers = listOf(user) + recentUsers.filterNot { it.id == user.id }
    }

    LaunchedEffect(Unit) {
        val ids = runCatching {
            JSONArray(prefs().getString("recentMentionedUsersJson", "[]")).let { arr ->
                buildList { for (i in 0 until minOf(arr.length(), 5)) add(arr.getString(i)) }
            }
        }.getOrDefault(emptyList())
        val currentId = FirebaseAuth.getInstance().currentUser?.uid
        val loaded = withContext(Dispatchers.IO) {
            val recent = runCatching { firestore.fetchUsers(ids) }.getOrDefault(emptyList())
            val suggested = currentId?.let { runCatching { firestore.fetchMutuals(it) }.getOrDefault(emptyList()) }.orEmpty()
            recent to suggested.filter { it.id != currentId }.take(6)
        }
        recentUsers = loaded.first
        suggestedUsers = loaded.second
        suggestionsLoading = false
    }

    LaunchedEffect(searchText) {
        val q = searchText.trim().removePrefix("@")
        if (q.isBlank()) {
            searchResults = emptyList()
            isSearching = false
            return@LaunchedEffect
        }
        isSearching = true
        delay(300)
        if (searchText.trim().removePrefix("@") != q) return@LaunchedEffect
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        searchResults = runCatching { firestore.searchUsers(q.lowercase(Locale.getDefault()), limit = 15) }
            .getOrDefault(emptyList())
            .filter { it.id != uid }
            .sortedWith(
                compareByDescending<AppUser> { it.isPlusSubscriber }
                    .thenBy { it.username.lowercase(Locale.getDefault()) },
            )
        isSearching = false
    }

    Column(modifier.fillMaxSize()) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(bottom = 20.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.sticker_mention_search_title),
                    color = palette.primaryText,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                )
                Text(
                    stringResource(R.string.sticker_mention_search_subtitle),
                    color = palette.secondaryText,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
                    .border(
                        1.dp,
                        if (fieldFocused) palette.searchIconActive.copy(0.18f) else Color.Transparent,
                        RoundedCornerShape(50),
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    if (searchText.isEmpty()) Icons.Filled.Search else Icons.Filled.Person,
                    null,
                    tint = if (searchText.isEmpty()) palette.searchIcon else palette.searchIconActive,
                    modifier = Modifier.size(18.dp),
                )
                BasicTextField(
                    value = searchText,
                    onValueChange = { searchText = it.take(30) },
                    singleLine = true,
                    textStyle = TextStyle(color = palette.primaryText, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                    cursorBrush = SolidColor(palette.primaryText),
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { fieldFocused = it.isFocused },
                    decorationBox = { inner ->
                        if (searchText.isEmpty()) {
                            Text(
                                stringResource(R.string.sticker_mention_search_placeholder),
                                color = palette.secondaryText,
                                fontSize = 16.sp,
                            )
                        }
                        inner()
                    },
                )
                if (searchText.isNotEmpty()) {
                    Icon(
                        Icons.Filled.Close,
                        null,
                        tint = palette.clearIcon,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable {
                                searchText = ""
                                searchResults = emptyList()
                                isSearching = false
                            },
                    )
                }
            }
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 4.dp)) {
            if (searchText.isEmpty()) {
                if (recentUsers.isNotEmpty()) {
                    item {
                        StickerInputSectionHeader(
                            stringResource(R.string.sticker_mention_recent),
                            Color(0xFFFF9500),
                        )
                    }
                    items(recentUsers, key = { it.id }) { user ->
                        StickerUserRowView(user) { onSelect(user) }
                    }
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 16.dp)
                                .height(1.dp)
                                .background(palette.divider),
                        )
                    }
                }
                item {
                    StickerInputSectionHeader(
                        stringResource(R.string.sticker_mention_suggestions),
                        Color(0xFFAF52DE),
                    )
                }
                if (suggestionsLoading && suggestedUsers.isEmpty()) {
                    items(4) { SkeletonUserRow() }
                } else {
                    items(suggestedUsers, key = { it.id }) { user ->
                        StickerUserRowView(user) { onSelect(user) }
                    }
                }
            } else if (isSearching) {
                item {
                    StickerInputSectionHeader(
                        stringResource(R.string.sticker_mention_searching),
                        Color(0xFF007AFF),
                        Icons.Filled.Search,
                    )
                }
                items(3) { SkeletonUserRow() }
            } else if (searchResults.isEmpty()) {
                item { StickerEmptySearchView(searchText) }
            } else {
                item {
                    val count = searchResults.size
                    StickerInputSectionHeader(
                        if (count == 1) {
                            stringResource(R.string.sticker_mention_results_one, count)
                        } else {
                            stringResource(R.string.sticker_mention_results_other, count)
                        },
                        Color(0xFF34C759),
                    )
                }
                items(searchResults, key = { it.id }) { user ->
                    StickerUserRowView(user) {
                        saveRecentUser(user)
                        onSelect(user)
                    }
                }
            }
        }
    }
}

// endregion

// region Link (cableado)

@Composable
fun ModernLinkInputView(
    onSelect: (url: String, customTitle: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = rememberStickerDetailPalette()
    var urlString by remember { mutableStateOf("") }
    var customTitle by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf<String?>(null) }
    val valid = normalizeStickerUrl(urlString) != null

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        InputTitleBlock(
            title = stringResource(R.string.sticker_link_add),
            subtitle = stringResource(R.string.sticker_link_hint),
            palette = palette,
        )
        Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
            GlassPrefixedField(
                value = urlString,
                onValueChange = { urlString = it.take(200) },
                placeholder = stringResource(R.string.sticker_link_url),
                icon = Icons.Filled.Link,
                accent = linkAccent,
                palette = palette,
                focused = focused == "url",
                onFocus = { focused = if (it) "url" else null },
                keyboardType = KeyboardType.Uri,
            )
            GlassPrefixedField(
                value = customTitle,
                onValueChange = { customTitle = it.take(48) },
                placeholder = stringResource(R.string.sticker_link_title),
                icon = Icons.Filled.Tag,
                accent = linkAccent,
                palette = palette,
                focused = focused == "title",
                onFocus = { focused = if (it) "title" else null },
            )
            GlassActionButton(
                label = stringResource(R.string.sticker_link_add),
                icon = Icons.Filled.Link,
                enabled = valid,
                palette = palette,
                onClick = { onSelect(urlString, customTitle) },
            )
        }
    }
}

// endregion

// region Hashtag (catálogo = placeholder inline; UI por paridad)

@Composable
fun ModernHashtagInputView(
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = rememberStickerDetailPalette()
    var hashtag by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        InputTitleBlock(
            title = stringResource(R.string.sticker_add_hashtag),
            subtitle = stringResource(R.string.sticker_hashtag_subtitle),
            palette = palette,
        )
        Row(
            Modifier
                .fillMaxWidth()
                .stickerGlassField(hashtagAccent, focused)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("#", color = hashtagAccent, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.width(18.dp))
            BasicTextField(
                value = hashtag,
                onValueChange = { hashtag = it.removePrefix("#").take(40) },
                singleLine = true,
                textStyle = TextStyle(color = palette.primaryText, fontSize = 18.sp, fontWeight = FontWeight.Medium),
                cursorBrush = SolidColor(palette.primaryText),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { focused = it.isFocused },
                decorationBox = { inner ->
                    if (hashtag.isEmpty()) {
                        Text(stringResource(R.string.sticker_hashtag_placeholder), color = palette.secondaryText, fontSize = 18.sp)
                    }
                    inner()
                },
            )
        }
        GlassActionButton(
            label = stringResource(R.string.sticker_add_hashtag),
            icon = Icons.Filled.Tag,
            enabled = hashtag.isNotBlank(),
            palette = palette,
            onClick = { onSelect(hashtag.trim()) },
        )
    }
}

// endregion

// region Countdown / EmojiSlider / Quiz / Poll / Question (catálogo = insertInstant)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernCountdownInputView(
    onSelect: (title: String, targetAtMs: Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = rememberStickerDetailPalette()
    var title by remember { mutableStateOf("") }
    var targetMs by remember {
        mutableLongStateOf(System.currentTimeMillis() + 3_600_000L)
    }
    var focused by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    val valid = title.trim().isNotEmpty() && targetMs > System.currentTimeMillis()
    val fmt = remember { SimpleDateFormat("d MMM · HH:mm", Locale.getDefault()) }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        InputTitleBlock(
            title = stringResource(R.string.sticker_create_countdown),
            subtitle = stringResource(R.string.sticker_countdown_subtitle),
            palette = palette,
        )
        GlassPrefixedField(
            value = title,
            onValueChange = { title = it.take(48) },
            placeholder = stringResource(R.string.sticker_countdown_title_placeholder),
            icon = Icons.Filled.Timer,
            accent = countdownAccent,
            palette = palette,
            focused = focused,
            onFocus = { focused = it },
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.sticker_countdown_ends_label),
                color = palette.secondaryText,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 1.sp,
            )
            Text(
                fmt.format(Date(targetMs)),
                color = palette.primaryText,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .stickerGlassField(countdownAccent, false)
                    .clickable { showDate = true }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            )
        }
        GlassActionButton(
            label = stringResource(R.string.sticker_create_countdown),
            icon = Icons.Filled.Timer,
            enabled = valid,
            palette = palette,
            onClick = { onSelect(title.trim(), targetMs.toDouble()) },
        )
    }

    if (showDate) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = targetMs)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    val day = dateState.selectedDateMillis ?: targetMs
                    val cal = Calendar.getInstance().apply { timeInMillis = targetMs }
                    val picked = Calendar.getInstance().apply { timeInMillis = day }
                    picked.set(Calendar.HOUR_OF_DAY, cal.get(Calendar.HOUR_OF_DAY))
                    picked.set(Calendar.MINUTE, cal.get(Calendar.MINUTE))
                    targetMs = maxOf(picked.timeInMillis, System.currentTimeMillis() + 60_000L)
                    showDate = false
                }) { Text(stringResource(R.string.common_done)) }
            },
            dismissButton = {
                TextButton(onClick = { showDate = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        ) {
            DatePicker(state = dateState)
        }
    }
}

@Composable
fun ModernEmojiSliderInputView(
    onSelect: (prompt: String, emoji: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = rememberStickerDetailPalette()
    val presets = listOf("😍", "🔥", "😂", "🥹", "🤩", "😮", "😢", "👏", "💯", "🤯")
    var prompt by remember { mutableStateOf("") }
    var selectedEmoji by remember { mutableStateOf("😍") }
    var expanded by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val resolvedPrompt = prompt.trim()
    val resolvedEmoji = selectedEmoji.ifBlank { "😍" }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        InputTitleBlock(
            title = stringResource(R.string.sticker_create_emoji_slider),
            subtitle = stringResource(R.string.sticker_emoji_slider_subtitle),
            palette = palette,
        )
        GlassPrefixedField(
            value = prompt,
            onValueChange = { prompt = it.take(60) },
            placeholder = stringResource(R.string.sticker_emoji_slider_prompt_placeholder),
            icon = Icons.Filled.Tag,
            accent = emojiSliderAccent,
            palette = palette,
            focused = focused,
            onFocus = { focused = it },
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            presets.forEach { emoji ->
                val selected = selectedEmoji == emoji
                Box(
                    Modifier
                        .size(48.dp)
                        .momentsChromeGlass(CircleShape, interactive = true)
                        .border(
                            if (selected) 1.8.dp else 0.9.dp,
                            if (selected) emojiSliderAccent else Color.White.copy(0.08f),
                            CircleShape,
                        )
                        .clickable {
                            selectedEmoji = emoji
                            HapticManager.shared.lightImpact()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(emoji, fontSize = 26.sp)
                }
            }
            val plusSelected = expanded || !presets.contains(selectedEmoji)
            Box(
                Modifier
                    .size(48.dp)
                    .momentsChromeGlass(CircleShape, interactive = true)
                    .border(
                        if (plusSelected) 1.8.dp else 0.9.dp,
                        if (plusSelected) emojiSliderAccent else Color.White.copy(0.08f),
                        CircleShape,
                    )
                    .clickable {
                        expanded = !expanded
                        HapticManager.shared.lightImpact()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Add, null, tint = palette.primaryText)
            }
        }
        if (expanded) {
            StickerEmojiPalettePicker(
                selectedEmoji = selectedEmoji,
                onSelectedEmojiChange = { selectedEmoji = it },
                onSelect = { emoji ->
                    selectedEmoji = emoji
                    expanded = false
                    HapticManager.shared.lightImpact()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        StickerEmojiSliderCardView(
            prompt = resolvedPrompt,
            emoji = resolvedEmoji,
            value = 0.5,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 4.dp),
        )
        GlassActionButton(
            label = stringResource(R.string.sticker_create_emoji_slider),
            icon = Icons.Filled.Tag,
            enabled = true,
            palette = palette,
            onClick = { onSelect(resolvedPrompt, resolvedEmoji) },
        )
    }
}

@Composable
fun ModernQuizInputView(
    onSelect: (question: String, options: List<String>, correctIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = rememberStickerDetailPalette()
    val accent = Color(0xFFFF9500)
    var question by remember { mutableStateOf("") }
    var options by remember { mutableStateOf(listOf("", "", "")) }
    var correctIndex by remember { mutableIntStateOf(0) }
    var focused by remember { mutableStateOf<Int?>(null) }
    val filled = options.map { it.trim() }.filter { it.isNotEmpty() }
    val valid = question.isNotBlank() && filled.size >= 2

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        InputTitleBlock(
            title = stringResource(R.string.sticker_quiz_title),
            subtitle = stringResource(R.string.sticker_quiz_subtitle),
            palette = palette,
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BasicTextField(
                value = question,
                onValueChange = { question = it.take(80) },
                textStyle = TextStyle(color = palette.primaryText, fontSize = 18.sp, fontWeight = FontWeight.Medium),
                cursorBrush = SolidColor(palette.primaryText),
                modifier = Modifier
                    .fillMaxWidth()
                    .stickerGlassField(accent, focused == -1)
                    .onFocusChanged { focused = if (it.isFocused) -1 else focused }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                decorationBox = { inner ->
                    if (question.isEmpty()) {
                        Text(stringResource(R.string.sticker_quiz_question_placeholder), color = palette.secondaryText, fontSize = 18.sp)
                    }
                    inner()
                },
            )
            options.forEachIndexed { index, value ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .stickerGlassField(
                            if (correctIndex == index) Color(0xFF34C759) else accent,
                            focused == index || correctIndex == index,
                        )
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicTextField(
                        value = value,
                        onValueChange = { next ->
                            options = options.toMutableList().also { it[index] = next.take(40) }
                        },
                        singleLine = true,
                        textStyle = TextStyle(color = palette.primaryText, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                        cursorBrush = SolidColor(palette.primaryText),
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { focused = if (it.isFocused) index else focused },
                        decorationBox = { inner ->
                            if (value.isEmpty()) {
                                Text(
                                    "${stringResource(R.string.sticker_quiz_option_prompt)} ${index + 1}",
                                    color = palette.secondaryText,
                                    fontSize = 16.sp,
                                )
                            }
                            inner()
                        },
                    )
                    Icon(
                        if (correctIndex == index) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                        null,
                        tint = if (correctIndex == index) Color(0xFF34C759) else Color.Gray.copy(0.5f),
                        modifier = Modifier
                            .size(22.dp)
                            .clickable {
                                correctIndex = index
                                HapticManager.shared.lightImpact()
                            },
                    )
                }
            }
            if (options.size < 4) {
                Row(
                    Modifier
                        .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
                        .clickable {
                            options = options + ""
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Filled.AddCircle, null, tint = palette.primaryText, modifier = Modifier.size(14.dp))
                    Text(
                        stringResource(R.string.sticker_quiz_add_option),
                        color = palette.primaryText,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                    )
                }
            }
        }
        GlassActionButton(
            label = stringResource(R.string.sticker_quiz_done),
            icon = Icons.Filled.CheckCircle,
            enabled = valid,
            palette = palette,
            onClick = {
                onSelect(question.trim(), filled, minOf(correctIndex, filled.lastIndex))
            },
        )
    }
}

@Composable
fun ModernPollInputView(
    onSelect: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = rememberStickerDetailPalette()
    var question by remember { mutableStateOf("") }
    var option1 by remember { mutableStateOf("") }
    var option2 by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf<String?>(null) }
    val valid = question.isNotBlank() && option1.isNotBlank() && option2.isNotBlank()

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        InputTitleBlock(
            title = stringResource(R.string.sticker_create_poll),
            subtitle = stringResource(R.string.sticker_poll_subtitle),
            palette = palette,
        )
        LabeledGlassField(
            label = stringResource(R.string.sticker_label_question),
            value = question,
            onValueChange = { question = it.take(44) },
            placeholder = stringResource(R.string.sticker_poll_placeholder),
            accent = pollAccent,
            palette = palette,
            focused = focused == "q",
            onFocus = { focused = if (it) "q" else null },
        )
        LabeledGlassField(
            label = stringResource(R.string.sticker_label_option1),
            value = option1,
            onValueChange = { option1 = it.take(28) },
            placeholder = stringResource(R.string.sticker_poll_option1_placeholder),
            accent = Color(0xFF007AFF),
            palette = palette,
            focused = focused == "a",
            onFocus = { focused = if (it) "a" else null },
            leadingDot = Color(0xFF007AFF),
        )
        LabeledGlassField(
            label = stringResource(R.string.sticker_label_option2),
            value = option2,
            onValueChange = { option2 = it.take(28) },
            placeholder = stringResource(R.string.sticker_poll_option2_placeholder),
            accent = hashtagAccent,
            palette = palette,
            focused = focused == "b",
            onFocus = { focused = if (it) "b" else null },
            leadingDot = hashtagAccent,
        )
        GlassActionButton(
            label = stringResource(R.string.sticker_create_poll),
            icon = Icons.Filled.AddCircle,
            enabled = valid,
            palette = palette,
            onClick = { onSelect(listOf(question.trim(), option1.trim(), option2.trim())) },
        )
    }
}

@Composable
fun ModernQuestionInputView(
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = rememberStickerDetailPalette()
    val accent = Color(0xFF30B0C7)
    var question by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        InputTitleBlock(
            title = stringResource(R.string.sticker_add_question),
            subtitle = stringResource(R.string.sticker_question_subtitle),
            palette = palette,
        )
        GlassPrefixedField(
            value = question,
            onValueChange = { question = it.take(48) },
            placeholder = stringResource(R.string.sticker_question_placeholder),
            icon = Icons.Filled.Tag,
            accent = accent,
            palette = palette,
            focused = focused,
            onFocus = { focused = it },
        )
        GlassActionButton(
            label = stringResource(R.string.sticker_add_question),
            icon = Icons.Filled.AddCircle,
            enabled = question.isNotBlank(),
            palette = palette,
            onClick = { onSelect(question.trim()) },
        )
    }
}

/** ≡ `ModernEmojiGridView` — no cableado en catálogo iOS actual (emoji usa MomentsEmojiGrid). */
@Composable
fun ModernEmojiGridView(
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val emojis = listOf(
        "😀", "😍", "🥳", "😎", "🤩", "😂", "🥺", "😭",
        "😡", "🤯", "🥶", "🤗", "🙄", "😴", "🤔", "💀",
        "❤️", "💔", "💯", "🔥", "⭐", "✨", "🎉", "🎈",
        "👍", "👎", "👏", "🙏", "💪", "✌️", "🤟", "👌",
    )
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        emojis.chunked(5).forEach { row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                row.forEach { emoji ->
                    Text(
                        emoji,
                        fontSize = 35.sp,
                        modifier = Modifier
                            .size(55.dp)
                            .clickable { onSelect(emoji) },
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

// endregion

// region Shared field chrome

@Composable
private fun InputTitleBlock(title: String, subtitle: String, palette: StickerDetailPalette) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = palette.primaryText, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        Text(subtitle, color = palette.secondaryText, fontWeight = FontWeight.Medium, fontSize = 13.sp)
    }
}

@Composable
private fun GlassPrefixedField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    icon: ImageVector,
    accent: Color,
    palette: StickerDetailPalette,
    focused: Boolean,
    onFocus: (Boolean) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .stickerGlassField(accent, focused)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = palette.primaryText, fontSize = 18.sp, fontWeight = FontWeight.Medium),
            cursorBrush = SolidColor(palette.primaryText),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { onFocus(it.isFocused) },
            decorationBox = { inner ->
                if (value.isEmpty()) Text(placeholder, color = palette.secondaryText, fontSize = 18.sp)
                inner()
            },
        )
    }
}

@Composable
private fun LabeledGlassField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    accent: Color,
    palette: StickerDetailPalette,
    focused: Boolean,
    onFocus: (Boolean) -> Unit,
    leadingDot: Color? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = palette.secondaryText, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)
        Row(
            Modifier
                .fillMaxWidth()
                .stickerGlassField(accent, focused)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (leadingDot != null) {
                Box(Modifier.size(10.dp).background(leadingDot, CircleShape))
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = palette.primaryText, fontSize = 18.sp, fontWeight = FontWeight.Medium),
                cursorBrush = SolidColor(palette.primaryText),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { onFocus(it.isFocused) },
                decorationBox = { inner ->
                    if (value.isEmpty()) Text(placeholder, color = palette.secondaryText, fontSize = 18.sp)
                    inner()
                },
            )
        }
    }
}

@Composable
private fun GlassActionButton(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    palette: StickerDetailPalette,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .stickerGlassAction(enabled)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            null,
            tint = if (enabled) palette.primaryText else palette.secondaryText,
            modifier = Modifier
                .padding(end = 10.dp)
                .size(18.dp),
        )
        Text(
            label,
            color = if (enabled) palette.primaryText else palette.secondaryText,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
        )
    }
}

// endregion
