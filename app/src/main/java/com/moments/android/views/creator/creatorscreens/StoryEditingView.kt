package com.moments.android.views.creator.creatorscreens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Filter
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.services.content.FilterService
import com.moments.android.services.privacy.ContentAudience
import com.moments.android.utilities.HapticManager
import com.moments.android.views.creator.BackgroundStoryUploadService
import com.moments.android.views.creator.CreatorAspectRatio
import com.moments.android.views.creator.CreatorFlow
import com.moments.android.views.creator.CreatorMedia
import com.moments.android.views.creator.audienceselector.AudienceSelectionView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.roundToInt
import android.graphics.Color as AndroidColor

/**
 * Port de `storyeditor.swift` / `StoryEditingView`.
 * Chunk 1: canvas + Share.
 * Chunk 2: text overlays (crear/editar/arrastrar/borrar) + StoryTextEditor mínimo.
 * Chunk 3: fonts + color swatches en texto.
 * Chunk 4: dibujo (StoryDrawingEditorOverlay).
 * Chunk 5: filtros (StoryFilterSelectorView + FilterService).
 * Stickers / motion: chunks siguientes.
 */
@Composable
fun StoryEditingView(
    selectedMediaItems: List<CreatorMedia>,
    onSelectedMediaItemsChange: (List<CreatorMedia>) -> Unit,
    onCurrentFlowChange: (CreatorFlow) -> Unit,
    startInTextMode: Boolean,
    onStartInTextModeChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = isSystemInDarkTheme()
    val canvas = if (isDark) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    val controlFg = if (isDark) Color.White else Color.Black.copy(0.82f)
    val controlStroke = if (isDark) Color.White.copy(0.12f) else Color.Black.copy(0.08f)
    val shareBg = if (isDark) Color(0xFFFAF9F6) else Color(0xFF0B1215)
    val shareFg = if (isDark) Color.Black.copy(0.9f) else Color.White

    var audience by remember { mutableStateOf(ContentAudience.EVERYONE) }
    var selectedListId by remember { mutableStateOf<String?>(null) }
    var selectedListName by remember { mutableStateOf<String?>(null) }
    var customSelectedUsers by remember { mutableStateOf<List<String>>(emptyList()) }
    var expirationHours by remember { mutableIntStateOf(24) }
    var isPublishing by remember { mutableStateOf(false) }
    var showingAudience by remember { mutableStateOf(false) }
    var pendingTool by remember { mutableStateOf<String?>(null) }

    var activeEditorMode by remember { mutableStateOf(ActiveEditorMode.IDLE) }
    var textOverlays by remember { mutableStateOf<List<StoryTextOverlayDraft>>(emptyList()) }
    var activeTextOverlayId by remember { mutableStateOf<String?>(null) }
    var editorBuffer by remember { mutableStateOf("") }
    var editorStyle by remember { mutableStateOf(StoryTextStyle.MODERN) }
    var editorColorHex by remember { mutableStateOf(StoryTextStyle.MODERN.defaultColorHex) }
    var nextLayerOrder by remember { mutableIntStateOf(0) }
    var deleteArmedId by remember { mutableStateOf<String?>(null) }
    var drawingImage by remember { mutableStateOf<Bitmap?>(null) }
    var selectedFilter by remember { mutableStateOf(FilterService.FilterType.NORMAL) }
    var filterIntensity by remember { mutableDoubleStateOf(1.0) }
    var filteredImage by remember { mutableStateOf<Bitmap?>(null) }
    var filterJob by remember { mutableStateOf<Job?>(null) }
    var showingIntensitySlider by remember { mutableStateOf(false) }

    fun applySelectedFilter() {
        filterJob?.cancel()
        val current = selectedMediaItems.firstOrNull()
        if (current == null || current.isVideo || selectedFilter == FilterService.FilterType.NORMAL) {
            filteredImage = null
            return
        }
        val uri = current.uri
        val type = selectedFilter
        val intensity = filterIntensity
        filterJob = scope.launch {
            delay(45)
            val processed = withContext(Dispatchers.Default) {
                val base = context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it)
                } ?: return@withContext null
                FilterService.applyFilter(type, base, intensity).also {
                    if (it !== base) base.recycle()
                }
            }
            filteredImage = processed
        }
    }

    fun commitActiveTextOverlay() {
        val id = activeTextOverlayId ?: return
        val trimmed = editorBuffer.trim()
        textOverlays = if (trimmed.isEmpty()) {
            textOverlays.filterNot { it.id == id }
        } else {
            textOverlays.map {
                if (it.id != id) it
                else it.copy(
                    text = trimmed,
                    styleRaw = editorStyle.raw,
                    colorHex = editorColorHex,
                    forcesAllCaps = editorStyle.usesAllCaps,
                )
            }
        }
    }

    fun finishTextEditing() {
        commitActiveTextOverlay()
        activeTextOverlayId = null
        editorBuffer = ""
        editorStyle = StoryTextStyle.MODERN
        editorColorHex = StoryTextStyle.MODERN.defaultColorHex
        activeEditorMode = ActiveEditorMode.IDLE
        deleteArmedId = null
    }

    fun beginCreatingTextOverlay() {
        commitActiveTextOverlay()
        val style = StoryTextStyle.MODERN
        val draft = StoryTextOverlayDraft.defaultPlacement().copy(
            layerOrder = nextLayerOrder++,
            styleRaw = style.raw,
            colorHex = style.defaultColorHex,
            forcesAllCaps = style.usesAllCaps,
        )
        textOverlays = textOverlays + draft
        activeTextOverlayId = draft.id
        editorBuffer = ""
        editorStyle = style
        editorColorHex = style.defaultColorHex
        activeEditorMode = ActiveEditorMode.TEXT
    }

    fun beginEditingTextOverlay(id: String) {
        val existing = textOverlays.firstOrNull { it.id == id } ?: return
        commitActiveTextOverlay()
        val brought = existing.copy(layerOrder = nextLayerOrder++)
        textOverlays = textOverlays.map { if (it.id == id) brought else it }
        activeTextOverlayId = id
        editorBuffer = brought.text
        editorStyle = StoryTextStyle.fromRaw(brought.styleRaw)
        editorColorHex = brought.colorHex.ifBlank { editorStyle.defaultColorHex }
        activeEditorMode = ActiveEditorMode.TEXT
        deleteArmedId = null
    }

    LaunchedEffect(startInTextMode) {
        if (startInTextMode) {
            beginCreatingTextOverlay()
            onStartInTextModeChange(false)
        }
    }

    val media = selectedMediaItems.firstOrNull()
    val hasTextOverlays = textOverlays.any { it.isReady } ||
        (activeEditorMode == ActiveEditorMode.TEXT && editorBuffer.trim().isNotEmpty())
    val hasDrawing = drawingImage != null
    val hasContent = media != null || hasTextOverlays || hasDrawing

    if (showingAudience) {
        AudienceSelectionView(
            selectedAudience = audience,
            selectedListId = selectedListId,
            selectedListName = selectedListName,
            customSelectedUsers = customSelectedUsers,
            onSelectedAudienceChange = { audience = it },
            onSelectedListIdChange = { selectedListId = it },
            onSelectedListNameChange = { selectedListName = it },
            onCustomSelectedUsersChange = { customSelectedUsers = it },
            onDismiss = { showingAudience = false },
            modifier = modifier,
        )
        return
    }

    if (pendingTool != null) {
        CreatorFlowPendingScreen(
            iosSource = pendingTool!!,
            onBack = { pendingTool = null },
            onDismiss = onDismiss,
            modifier = modifier,
        )
        return
    }

    fun publishStory() {
        if (isPublishing || FirebaseAuth.getInstance().currentUser == null || !hasContent) return
        finishTextEditing()
        val prepared = textOverlays.filter { it.isReady }
            .sortedBy { it.layerOrder }
            .map { it.toMetadata() }
        if (media == null && prepared.isEmpty() && drawingImage == null) return
        isPublishing = true
        scope.launch {
            val publishMedia = when {
                filteredImage != null &&
                    selectedFilter != FilterService.FilterType.NORMAL &&
                    media != null && !media.isVideo -> {
                    val bmp = filteredImage!!
                    val dir = File(context.cacheDir, "story_filters").also { it.mkdirs() }
                    val file = File(dir, "filter_${UUID.randomUUID()}.jpg")
                    withContext(Dispatchers.IO) {
                        FileOutputStream(file).use { out ->
                            bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
                        }
                    }
                    media.copy(uri = Uri.fromFile(file), hasEdits = true)
                }
                media != null -> media
                else -> {
                    val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(if (isDark) AndroidColor.parseColor("#0B1215") else AndroidColor.parseColor("#FAF9F6"))
                    val dir = File(context.cacheDir, "story_text_only").also { it.mkdirs() }
                    val file = File(dir, "text_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                    }
                    bitmap.recycle()
                    CreatorMedia(
                        uri = Uri.fromFile(file),
                        isVideo = false,
                        aspectRatio = CreatorAspectRatio.NINE_BY_SIXTEEN,
                        recommendedAspectRatio = CreatorAspectRatio.NINE_BY_SIXTEEN,
                    )
                }
            }
            val primary = prepared.firstOrNull()
            val drawingBytes = drawingImage?.let { bmp ->
                ByteArrayOutputStream().use { out ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                    out.toByteArray()
                }
            }
            val actionId = BackgroundStoryUploadService.uploadStory(
                media = publishMedia,
                storyText = primary?.text,
                textPosition = primary?.normalizedPosition,
                selectedTextStyle = primary?.styleRaw,
                textOverlayMetadata = primary,
                textOverlays = prepared.takeIf { it.isNotEmpty() },
                drawingData = drawingBytes,
                audienceSetting = audience.raw,
                customViewers = customSelectedUsers.takeIf { it.isNotEmpty() },
                customListId = selectedListId,
                selectedListName = selectedListName,
                expirationHours = expirationHours,
            )
            if (actionId != null) {
                HapticManager.shared.success()
                onSelectedMediaItemsChange(emptyList())
                textOverlays = emptyList()
                drawingImage?.recycle()
                drawingImage = null
                delay(400)
                onDismiss()
            } else {
                HapticManager.shared.warning()
                isPublishing = false
            }
        }
    }

    Box(modifier.fillMaxSize().background(canvas)) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChromeTool(
                    onClick = {
                        when (activeEditorMode) {
                            ActiveEditorMode.TEXT -> finishTextEditing()
                            ActiveEditorMode.DRAWING -> activeEditorMode = ActiveEditorMode.IDLE
                            ActiveEditorMode.FILTERS -> activeEditorMode = ActiveEditorMode.IDLE
                            else -> onCurrentFlowChange(CreatorFlow.STORY_CAMERA)
                        }
                    },
                    stroke = controlStroke,
                ) {
                    Icon(Icons.Filled.Close, null, tint = controlFg, modifier = Modifier.size(18.dp))
                }
                ChromeTool(
                    onClick = { pendingTool = "StoryEditingView.saveToGallery (pending)" },
                    stroke = controlStroke,
                ) {
                    Icon(Icons.Filled.Download, null, tint = controlFg, modifier = Modifier.size(18.dp))
                }
            }

            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.Black),
            ) {
                val boxW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                val boxH = constraints.maxHeight.toFloat().coerceAtLeast(1f)

                when {
                    media != null && !media.isVideo -> {
                        val preview = filteredImage
                        if (preview != null && selectedFilter != FilterService.FilterType.NORMAL) {
                            Image(
                                bitmap = preview.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            AsyncImage(
                                model = media.uri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    media != null && media.isVideo -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Videocam, null, tint = Color.White.copy(0.7f), modifier = Modifier.size(48.dp))
                            Text(
                                stringResource(R.string.story_editor_video_preview_hint),
                                color = Color.White.copy(0.7f),
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 72.dp),
                            )
                        }
                    }
                    else -> {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(if (isDark) Color(0xFF1A2226) else Color(0xFFE8E4DC)),
                        )
                    }
                }

                // Drawing layer under text overlays
                if (activeEditorMode != ActiveEditorMode.DRAWING) {
                    drawingImage?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                // Idle overlays on canvas
                if (activeEditorMode == ActiveEditorMode.IDLE) {
                    textOverlays.filter { it.isReady }.sortedBy { it.layerOrder }.forEach { overlay ->
                        val xPx = (overlay.normalizedX * boxW).toFloat()
                        val yPx = (overlay.normalizedY * boxH).toFloat()
                        val armed = deleteArmedId == overlay.id
                        Box(
                            Modifier
                                .offset {
                                    IntOffset(xPx.roundToInt() - 80, yPx.roundToInt() - 20)
                                }
                                .pointerInput(overlay.id, boxW, boxH) {
                                    detectDragGestures { change, drag ->
                                        change.consume()
                                        deleteArmedId = null
                                        textOverlays = textOverlays.map { item ->
                                            if (item.id != overlay.id) item
                                            else item.copy(
                                                normalizedX = (item.normalizedX + drag.x / boxW).coerceIn(0.05, 0.95),
                                                normalizedY = (item.normalizedY + drag.y / boxH).coerceIn(0.05, 0.95),
                                            )
                                        }
                                    }
                                }
                                .padding(8.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StoryCanvasTextLabel(
                                    overlay = overlay,
                                    modifier = Modifier
                                        .background(Color.Black.copy(0.25f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                        .clickable {
                                            if (armed) {
                                                textOverlays = textOverlays.filterNot { it.id == overlay.id }
                                                deleteArmedId = null
                                                HapticManager.shared.warning()
                                            } else {
                                                beginEditingTextOverlay(overlay.id)
                                            }
                                        },
                                )
                                Icon(
                                    Icons.Filled.Delete,
                                    null,
                                    tint = if (armed) Color(0xFFE91E63) else Color.White.copy(0.55f),
                                    modifier = Modifier
                                        .padding(start = 6.dp)
                                        .size(18.dp)
                                        .clickable {
                                            if (armed) {
                                                textOverlays = textOverlays.filterNot { it.id == overlay.id }
                                                deleteArmedId = null
                                                HapticManager.shared.warning()
                                            } else {
                                                deleteArmedId = overlay.id
                                            }
                                        },
                                )
                            }
                        }
                    }
                }

                // Side toolbar
                if (activeEditorMode == ActiveEditorMode.IDLE) {
                    Column(
                        Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SideTool(Icons.Filled.TextFields, controlFg, controlStroke) {
                            beginCreatingTextOverlay()
                        }
                        SideTool(Icons.Filled.EmojiEmotions, controlFg, controlStroke) {
                            pendingTool = "stickerview.swift / StickerPickerView"
                        }
                        SideTool(Icons.Filled.Brush, controlFg, controlStroke) {
                            activeEditorMode = ActiveEditorMode.DRAWING
                        }
                        SideTool(Icons.Filled.Filter, controlFg, controlStroke) {
                            if (media != null && !media.isVideo) {
                                activeEditorMode = ActiveEditorMode.FILTERS
                                showingIntensitySlider = selectedFilter != FilterService.FilterType.NORMAL
                                applySelectedFilter()
                            } else {
                                pendingTool = "StoryFilterSelectorView.swift (image only)"
                            }
                        }
                        Box(
                            Modifier
                                .size(44.dp)
                                .momentsChromeGlass(CircleShape, interactive = true)
                                .border(1.dp, controlStroke, CircleShape)
                                .clickable { expirationHours = if (expirationHours == 24) 48 else 24 },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                stringResource(R.string.story_editor_expiration_hours, expirationHours),
                                color = controlFg,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }

                if (activeEditorMode == ActiveEditorMode.TEXT) {
                    StoryTextEditor(
                        text = editorBuffer,
                        onTextChange = { editorBuffer = it },
                        selectedStyle = editorStyle,
                        onStyleChange = { editorStyle = it },
                        colorHex = editorColorHex,
                        onColorHexChange = { editorColorHex = it },
                        onDone = { finishTextEditing() },
                        onCancel = {
                            val id = activeTextOverlayId
                            activeTextOverlayId = null
                            editorBuffer = ""
                            editorStyle = StoryTextStyle.MODERN
                            editorColorHex = StoryTextStyle.MODERN.defaultColorHex
                            if (id != null) {
                                textOverlays = textOverlays.filterNot { it.id == id && it.text.isBlank() }
                            }
                            activeEditorMode = ActiveEditorMode.IDLE
                        },
                    )
                }

                if (activeEditorMode == ActiveEditorMode.DRAWING) {
                    StoryDrawingEditorOverlay(
                        baseDrawing = drawingImage,
                        onCancel = { activeEditorMode = ActiveEditorMode.IDLE },
                        onDone = { result ->
                            val previous = drawingImage
                            drawingImage = result
                            if (previous != null && previous !== result && !previous.isRecycled) {
                                previous.recycle()
                            }
                            activeEditorMode = ActiveEditorMode.IDLE
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            if (activeEditorMode == ActiveEditorMode.FILTERS && media != null && !media.isVideo) {
                Spacer(Modifier.height(10.dp))
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (selectedFilter != FilterService.FilterType.NORMAL && showingIntensitySlider) {
                        Text(
                            "${(filterIntensity * 100).toInt()}%",
                            color = controlFg,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .momentsChromeGlass(RoundedCornerShape(50), interactive = false)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                        Slider(
                            value = filterIntensity.toFloat(),
                            onValueChange = {
                                filterIntensity = it.toDouble()
                                applySelectedFilter()
                            },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = controlFg,
                                activeTrackColor = controlFg,
                                inactiveTrackColor = controlFg.copy(0.25f),
                            ),
                            modifier = Modifier.padding(horizontal = 32.dp),
                        )
                    }
                    StoryFilterSelectorView(
                        selectedFilter = selectedFilter,
                        onFilterChange = { filter ->
                            selectedFilter = filter
                            showingIntensitySlider = filter != FilterService.FilterType.NORMAL
                            if (filter == FilterService.FilterType.NORMAL) {
                                filterIntensity = 1.0
                            }
                            applySelectedFilter()
                        },
                        baseUri = media.uri,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            if (activeEditorMode == ActiveEditorMode.IDLE) {
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        Modifier
                            .weight(1f)
                            .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
                            .clickable { showingAudience = true }
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Filled.People, null, tint = controlFg.copy(0.72f), modifier = Modifier.size(16.dp))
                        Text(
                            when {
                                audience == ContentAudience.CUSTOM_LIST && !selectedListName.isNullOrBlank() ->
                                    selectedListName!!
                                else -> audienceLabel(audience)
                            },
                            color = controlFg,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            maxLines = 1,
                        )
                    }

                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(shareBg.copy(if (hasContent && !isPublishing) 1f else 0.55f))
                            .clickable(enabled = hasContent && !isPublishing) { publishStory() }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (isPublishing) {
                            CircularProgressIndicator(
                                color = shareFg,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp),
                            )
                        } else {
                            Text(
                                stringResource(R.string.story_editor_share),
                                color = shareFg,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                            )
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                null,
                                tint = shareFg,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun StoryCanvasTextLabel(
    overlay: StoryTextOverlayDraft,
    modifier: Modifier = Modifier,
) {
    val style = StoryTextStyle.fromRaw(overlay.styleRaw)
    val fontFamily = rememberStoryFontFamily(style)
    Text(
        style.displayText(overlay.text),
        color = parseStoryColorHex(overlay.colorHex),
        fontSize = overlay.fontSize.toFloat().sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        fontFamily = fontFamily,
        modifier = modifier,
    )
}

@Composable
private fun ChromeTool(
    onClick: () -> Unit,
    stroke: Color,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .size(42.dp)
            .momentsChromeGlass(CircleShape, interactive = true)
            .border(1.dp, stroke, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun SideTool(
    icon: ImageVector,
    tint: Color,
    stroke: Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(44.dp)
            .momentsChromeGlass(CircleShape, interactive = true)
            .border(1.dp, stroke, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun audienceLabel(audience: ContentAudience): String = when (audience) {
    ContentAudience.EVERYONE -> stringResource(R.string.audience_type_everyone)
    ContentAudience.MUTUALS -> stringResource(R.string.audience_type_mutuals)
    ContentAudience.BEST_FRIENDS -> stringResource(R.string.audience_type_best_friends)
    ContentAudience.CUSTOM, ContentAudience.CUSTOM_LIST -> stringResource(R.string.audience_type_custom)
    ContentAudience.ONLY_ME -> stringResource(R.string.audience_type_only_me)
}
