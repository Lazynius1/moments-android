package com.moments.android.views.messaging.components

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.moments.android.R
import com.moments.android.notifications.services.InAppActionToast
import com.moments.android.notifications.services.InAppNotificationService
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.util.Date
import kotlin.math.pow

internal data class ChatWallpaperPreset(val id: String, val bubble: String, val asset: Int, val title: Int)
internal val chatWallpaperPresets = listOf(
    ChatWallpaperPreset("aurora", "6746A3", R.drawable.chat_wallpaper_aurora, R.string.chat_wallpaper_preset_aurora),
    ChatWallpaperPreset("coast", "007D77", R.drawable.chat_wallpaper_coast, R.string.chat_wallpaper_preset_coast),
    ChatWallpaperPreset("dune", "A44732", R.drawable.chat_wallpaper_dune, R.string.chat_wallpaper_preset_dune),
    ChatWallpaperPreset("forest", "35624C", R.drawable.chat_wallpaper_forest, R.string.chat_wallpaper_preset_forest),
    ChatWallpaperPreset("bloom", "B04A78", R.drawable.chat_wallpaper_bloom, R.string.chat_wallpaper_preset_bloom),
    ChatWallpaperPreset("midnight", "263F63", R.drawable.chat_wallpaper_midnight, R.string.chat_wallpaper_preset_midnight),
    ChatWallpaperPreset("lagoon", "397487", R.drawable.chat_wallpaper_lagoon, R.string.chat_wallpaper_preset_lagoon),
    ChatWallpaperPreset("clay", "705239", R.drawable.chat_wallpaper_clay, R.string.chat_wallpaper_preset_clay),
    ChatWallpaperPreset("cloud", "D3E4EF", R.drawable.chat_wallpaper_cloud, R.string.chat_wallpaper_preset_cloud),
    ChatWallpaperPreset("citrus", "E0B616", R.drawable.chat_wallpaper_citrus, R.string.chat_wallpaper_preset_citrus),
    ChatWallpaperPreset("pearl", "EA2D70", R.drawable.chat_wallpaper_pearl, R.string.chat_wallpaper_preset_pearl),
    ChatWallpaperPreset("paper", "9D6A43", R.drawable.chat_wallpaper_paper, R.string.chat_wallpaper_preset_paper),
    ChatWallpaperPreset("ink", "3C3F45", R.drawable.chat_wallpaper_ink, R.string.chat_wallpaper_preset_ink),
    ChatWallpaperPreset("ripple", "BFA88A", R.drawable.chat_wallpaper_ripple, R.string.chat_wallpaper_preset_ripple),
    ChatWallpaperPreset("prism", "098DDC", R.drawable.chat_wallpaper_prism, R.string.chat_wallpaper_preset_prism),
    ChatWallpaperPreset("orbit", "C256D8", R.drawable.chat_wallpaper_orbit, R.string.chat_wallpaper_preset_orbit),
    ChatWallpaperPreset("brush", "3F6F8F", R.drawable.chat_wallpaper_brush, R.string.chat_wallpaper_preset_brush),
    ChatWallpaperPreset("watercolor", "76A529", R.drawable.chat_wallpaper_watercolor, R.string.chat_wallpaper_preset_watercolor),
    ChatWallpaperPreset("sketch", "00AB8C", R.drawable.chat_wallpaper_sketch, R.string.chat_wallpaper_preset_sketch),
    ChatWallpaperPreset("canvas", "8F394C", R.drawable.chat_wallpaper_canvas, R.string.chat_wallpaper_preset_canvas),
)
internal val chatBubblePalette = listOf(
    "3F6F8F", "D3E4EF", "263F63", "D5DFF0",
    "6746A3", "E5D9F5", "35624C", "D8EDD7",
    "007D77", "CEF0EA", "8F394C", "F8D6DF",
    "A44732", "F9DECF", "705239", "EDE2D0",
    "3C3F45", "E2E3E5", "B04A78", "F8DCEC",
    "746133", "F1E8B7", "397487", "D3EDF3",
    "16A466", "D6FCCF", "6044DA", "E6DFFB",
    "C256D8", "EFD8F3", "E46B50", "FBE1D6",
    "2961EB", "D1E6FB", "098DDC", "D0E1EB",
    "00AB8C", "CFFCEC", "CFC300", "FBF8A9",
    "76A529", "E3FEB9", "EA2D70", "FBD9E5",
    "EE003C", "FAD8DE", "EB351B", "FFDCCB",
    "E0B616", "FCF0D6", "9D6A43", "F2DDD0",
    "BFA88A", "EDE8DD", "004F7A", "D0E2E9"
)
internal val chatBackgroundPalette = listOf("DCE8E4", "C8E7E4", "D9EBF3", "E7DFEE", "F2E3D5", "F3D9DE", "E6E6E6", "F5EDCB", "C1D6C1", "A7C3CF", "253A40", "282033", "171717", "314939", "60413F", "4A5365", "84705B", "E4D9CD")
internal fun chatBubbleTextColor(color: Color): Color {
    fun linear(value: Float) = if (value <= .04045f) value / 12.92f else ((value + .055f) / 1.055f).pow(2.4f)
    return if (.2126f * linear(color.red) + .7152f * linear(color.green) + .0722f * linear(color.blue) > .179f) Color.Black else Color.White
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationWallpaperView(conversationId: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val state = rememberWallpaper(conversationId)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf(state.wallpaper) }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var edited by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    // One route avoids overlapping native sheets when switching from wallpaper to preview.
    var route by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.wallpaper) { if (!edited) draft = state.wallpaper }
    fun error() { InAppNotificationService.showActionToast(InAppActionToast(prefix = context.getString(R.string.chat_wallpaper_error), isError = true)) }
    fun save(value: ChatWallpaper = draft, cropped: Bitmap? = photo) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val bytes = if (value.kind == "photo" && cropped != null) withContext(Dispatchers.Default) {
                    ByteArrayOutputStream().use { stream -> check(cropped.compress(Bitmap.CompressFormat.JPEG, 85, stream)); stream.toByteArray() }
                } else null
                state.save(value, bytes)
                InAppNotificationService.showActionToast(InAppActionToast.activityDone(context.getString(R.string.chat_wallpaper_saved)))
                onDismiss()
            } catch (_: Exception) { error() } finally { busy = false }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                val decoded = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { decodeWallpaper(it.readBytes()) } }
                checkNotNull(decoded)
                photo = decoded; draft = draft.copy(kind = "photo"); edited = true; route = "preview"
            } catch (_: Exception) { error() } finally { busy = false }
        }
    }
    val fallback = MaterialTheme.colorScheme.background
    Surface(modifier.fillMaxSize()) {
        Column {
            TopAppBar(title = { Text(stringResource(R.string.chat_wallpaper_title)) },
                navigationIcon = { IconButton(onClick = onDismiss, enabled = !busy) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.chat_wallpaper_back)) } },
                actions = { TextButton(onClick = { save() }, enabled = !busy && conversationId.isNotEmpty() && (draft.kind != "photo" || (photo ?: state.image) != null)) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.chat_wallpaper_save))
                } })
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.chat_wallpaper_styles), Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.titleSmall)
                BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)) {
                    val cardWidth = (maxWidth - 32.dp - 24.dp) / 3
                    LazyHorizontalGrid(rows = GridCells.Fixed(2), modifier = Modifier.fillMaxWidth().height(332.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item(key = "current") {
                            WallpaperStyleCard(draft, true, stringResource(R.string.chat_wallpaper_current), image = photo ?: state.image, width = cardWidth) { route = "preview" }
                        }
                        item(key = "default") {
                            WallpaperStyleCard(ChatWallpaper(), false, stringResource(R.string.chat_wallpaper_default), width = cardWidth) {
                                edited = true; draft = ChatWallpaper(); photo = null; route = "preview"
                            }
                        }
                        items(chatWallpaperPresets, key = { it.id }) { preset ->
                            WallpaperStyleCard(ChatWallpaper(kind = "preset", presetId = preset.id, bubbleColorHex = preset.bubble, dimming = 0.0),
                                false, stringResource(preset.title), width = cardWidth) {
                                edited = true; photo = null; draft = draft.copy(kind = "preset", presetId = preset.id, bubbleColorHex = preset.bubble, dimming = .12); route = "preview"
                            }
                        }
                    }
                }
                Text(stringResource(R.string.chat_wallpaper_styles_hint), Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.chat_wallpaper_customize), Modifier.padding(start = 20.dp, top = 12.dp), style = MaterialTheme.typography.titleSmall)
                ListItem(headlineContent = { Text(stringResource(R.string.chat_wallpaper_bubble_color)) }, leadingContent = { Icon(Icons.Default.ChatBubbleOutline, null) },
                    trailingContent = { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(26.dp).clip(CircleShape).background(wallpaperColor(draft.bubbleColorHex))); Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) } },
                    modifier = Modifier.clickable(enabled = !busy) { route = "bubbles" })
                ListItem(headlineContent = { Text(stringResource(R.string.chat_wallpaper_background)) }, leadingContent = { Icon(Icons.Default.Photo, null) },
                    trailingContent = { Row(verticalAlignment = Alignment.CenterVertically) { WallpaperCanvas(draft, photo ?: state.image, fallback, Modifier.size(26.dp, 36.dp).clip(RoundedCornerShape(5.dp))); Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) } },
                    modifier = Modifier.clickable(enabled = !busy) { route = "background" })
                ListItem(headlineContent = { Text(stringResource(R.string.chat_wallpaper_preview_title)) }, leadingContent = { Icon(Icons.Default.Visibility, null) }, modifier = Modifier.clickable(enabled = !busy) { route = "preview" })
                Text(stringResource(R.string.chat_wallpaper_private), Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    if (route != null) ModalBottomSheet(onDismissRequest = { if (!busy) route = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), dragHandle = null) {
        when (route) {
            "preview" -> WallpaperPreviewEditor(draft, photo ?: state.image, saving = busy, onDismiss = { route = null }) { value, cropped -> edited = true; draft = value; if (cropped != null) photo = cropped; save(value, cropped ?: photo) }
            "bubbles", "colors" -> WallpaperColorPicker(if (route == "bubbles") chatBubblePalette else chatBackgroundPalette,
                if (route == "bubbles") draft.bubbleColorHex else draft.colorHex,
                if (route == "bubbles") R.string.chat_wallpaper_bubble_color else R.string.chat_wallpaper_color,
                onDismiss = { route = null }) { hex ->
                edited = true
                draft = if (route == "bubbles") draft.copy(bubbleColorHex = hex) else draft.copy(kind = "color", colorHex = hex)
                if (route == "colors") photo = null
                route = null
            }
            "background" -> Column(Modifier.fillMaxHeight(.9f)) {
                TopAppBar(title = { Text(stringResource(R.string.chat_wallpaper_background)) }, navigationIcon = { IconButton(onClick = { route = null }) { Icon(Icons.Default.Close, stringResource(R.string.chat_wallpaper_back)) } })
                ListItem(headlineContent = { Text(stringResource(R.string.chat_wallpaper_photo)) }, leadingContent = { Icon(Icons.Default.PhotoLibrary, null) }, modifier = Modifier.clickable(enabled = !busy) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
                ListItem(headlineContent = { Text(stringResource(R.string.chat_wallpaper_color)) }, leadingContent = { Icon(Icons.Default.Palette, null) }, modifier = Modifier.clickable { route = "colors" })
                ListItem(headlineContent = { Text(stringResource(R.string.chat_wallpaper_default)) }, modifier = Modifier.clickable { edited = true; draft = draft.copy(kind = "default"); photo = null; route = null })
                LazyVerticalGrid(columns = GridCells.Adaptive(90.dp), contentPadding = PaddingValues(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(chatWallpaperPresets, key = { it.id }) { preset ->
                        WallpaperCanvas(ChatWallpaper(kind = "preset", presetId = preset.id, dimming = 0.0), null, fallback,
                            Modifier.aspectRatio(9f / 16f).clip(RoundedCornerShape(16.dp)).clickable { edited = true; draft = draft.copy(kind = "preset", presetId = preset.id); photo = null; route = null })
                    }
                }
            }
        }
    }
}

@Composable
private fun WallpaperStyleCard(value: ChatWallpaper, selected: Boolean, title: String, image: Bitmap? = null, width: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Box(Modifier.width(width).fillMaxHeight().clip(RoundedCornerShape(16.dp)).border(if (selected) 2.dp else .5.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)).clickable(onClick = onClick)) {
        WallpaperCanvas(value, image, MaterialTheme.colorScheme.background, Modifier.fillMaxSize())
        Column(Modifier.fillMaxWidth().align(Alignment.Center).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(48.dp, 24.dp).clip(RoundedCornerShape(10.dp)).background(Color.White))
            Box(Modifier.align(Alignment.End).size(48.dp, 24.dp).clip(RoundedCornerShape(10.dp)).background(wallpaperColor(value.bubbleColorHex)))
        }
        if (selected) Icon(Icons.Default.CheckCircle, title, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp).background(MaterialTheme.colorScheme.surface, CircleShape))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WallpaperColorPicker(colors: List<String>, selected: String, title: Int, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxHeight(.9f)) {
        TopAppBar(title = { Text(stringResource(title)) }, navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, stringResource(R.string.chat_wallpaper_back)) } })
        LazyVerticalGrid(columns = GridCells.Fixed(4), contentPadding = PaddingValues(24.dp), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            items(colors) { hex ->
                Box(Modifier.aspectRatio(1f).border(if (hex == selected) 2.dp else 0.dp, if (hex == selected) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape).padding(5.dp).clip(CircleShape).background(wallpaperColor(hex)).clickable { onSelect(hex) }, contentAlignment = Alignment.Center) {
                    if (hex == selected) Icon(Icons.Default.CheckCircle, stringResource(title), tint = chatBubbleTextColor(wallpaperColor(hex)))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WallpaperPreviewEditor(wallpaper: ChatWallpaper, image: Bitmap?, saving: Boolean, onDismiss: () -> Unit, onApply: (ChatWallpaper, Bitmap?) -> Unit) {
    var draft by remember(wallpaper) { mutableStateOf(wallpaper) }
    var dark by remember { mutableStateOf(false) }
    val systemDark = isSystemInDarkTheme()
    LaunchedEffect(Unit) { dark = systemDark }
    var brightness by remember { mutableStateOf(false) }
    var colors by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var busy by remember { mutableStateOf(false) }
    var canvasSize by remember { mutableStateOf(IntSize(1, 1)) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxHeight(.95f)) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val w = canvasSize.width.toFloat(); val h = canvasSize.height.toFloat()
            fun bounded(position: Offset, scale: Float): Offset {
                if (image == null || draft.kind != "photo") return Offset.Zero
                val fill = maxOf(w / image.width, h / image.height) * scale
                val x = maxOf(0f, (image.width * fill - w) / 2); val y = maxOf(0f, (image.height * fill - h) / 2)
                return Offset(position.x.coerceIn(-x, x), position.y.coerceIn(-y, y))
            }
            Column {
                TopAppBar(title = { Text(stringResource(R.string.chat_wallpaper_preview_title)) }, navigationIcon = { IconButton(enabled = !busy && !saving, onClick = onDismiss) { Icon(Icons.Default.Close, stringResource(R.string.chat_wallpaper_back)) } },
                    actions = { TextButton(enabled = !busy && !saving, onClick = {
                        busy = true; val value = draft; val position = bounded(offset, zoom); val scale = zoom
                        scope.launch {
                            try {
                                val cropped = withContext(Dispatchers.Default) {
                                    if (value.kind != "photo" || image == null) null else {
                                        val height = maxOf(1, (1080f * h / w).toInt())
                                        val output = Bitmap.createBitmap(1080, height, Bitmap.Config.ARGB_8888)
                                        val fill = maxOf(1080f / image.width, height.toFloat() / image.height) * scale
                                        val iw = image.width * fill; val ih = image.height * fill; val factor = 1080f / w
                                        val x = (1080 - iw) / 2 + position.x * factor; val y = (height - ih) / 2 + position.y * factor
                                        Canvas(output).drawBitmap(image, null, RectF(x, y, x + iw, y + ih), Paint(Paint.FILTER_BITMAP_FLAG)); output
                                    }
                                }
                                onApply(value, cropped)
                            } finally { busy = false }
                        }
                    }) { if (busy || saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.chat_wallpaper_save)) } })
                Box(Modifier.weight(1f).fillMaxWidth().onSizeChanged { canvasSize = it }) {
                    WallpaperCanvas(draft, image, com.moments.android.views.feed.AdaptiveColors(dark).chatBackground.first(), Modifier.fillMaxSize().pointerInput(image, draft.kind, busy, w, h) {
                        detectTransformGestures { _, pan, scale, _ -> if (!busy && draft.kind == "photo") { zoom = (zoom * scale).coerceIn(1f, 4f); offset = bounded(offset + pan, zoom) } }
                    }, zoom, offset)
                    val config = Configuration(LocalConfiguration.current).apply { uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO }
                    CompositionLocalProvider(LocalConfiguration provides config, LocalChatOutgoingBubbleColor provides wallpaperColor(draft.bubbleColorHex)) {
                        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            GlassmorphicDateHeader(Date())
                            Row { ChatTextBubbleView(stringResource(R.string.chat_wallpaper_crop), false, onReaction = {}); Spacer(Modifier.width(24.dp)) }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { ChatTextBubbleView(stringResource(R.string.chat_wallpaper_private), true, onReaction = {}) }
                        }
                    }
                    Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(22.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                        FilledIconButton(onClick = { colors = true }, modifier = Modifier.size(52.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Box(Modifier.size(38.dp).clip(CircleShape).background(wallpaperColor(draft.bubbleColorHex)).border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (brightness) Surface(shape = RoundedCornerShape(22.dp)) {
                                Box(Modifier.size(56.dp, 216.dp), contentAlignment = Alignment.Center) {
                                    Slider(value = draft.dimming.toFloat(), onValueChange = { draft = draft.copy(dimming = it.toDouble()) }, valueRange = 0f..0.75f, modifier = Modifier.requiredSize(180.dp, 48.dp).rotate(270f))
                                }
                            }
                            FilledIconButton(onClick = { dark = !dark; brightness = !brightness }, modifier = Modifier.size(52.dp)) { Icon(if (dark) Icons.Default.LightMode else Icons.Default.DarkMode, stringResource(R.string.chat_wallpaper_dimming)) }
                        }
                    }
                }
            }
        }
    }
    if (colors) ModalBottomSheet(onDismissRequest = { colors = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), dragHandle = null) {
        WallpaperColorPicker(chatBubblePalette, draft.bubbleColorHex, R.string.chat_wallpaper_bubble_color, onDismiss = { colors = false }) { draft = draft.copy(bubbleColorHex = it); colors = false }
    }
}
