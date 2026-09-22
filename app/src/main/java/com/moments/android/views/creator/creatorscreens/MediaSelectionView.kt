package com.moments.android.views.creator.creatorscreens
import android.content.ContentUris
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import com.moments.android.views.components.MomentsCircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.extensions.MomentsChromeGlass
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.views.creator.CreatorAlbumInfo
import com.moments.android.views.creator.CreatorAspectRatio
import com.moments.android.views.creator.CreatorFlow
import com.moments.android.views.creator.CreatorMedia
import com.moments.android.models.MediaItemFeedCrop
import com.moments.android.views.creator.creatoruikit.AssetCropSession
import com.moments.android.views.creator.creatoruikit.MomentFeedCrop
import com.moments.android.views.creator.creatoruikit.MomentFeedCropCanvas
import com.moments.android.views.creator.creatoruikit.creatorNormalizedUp
import com.moments.android.views.creator.creatoruikit.exifOrientation
import com.moments.android.views.creator.creatoruikit.framedFeedCropFromSession
import com.moments.android.views.creator.creatoruikit.orientedDisplaySize
import com.moments.android.views.messaging.components.AttachmentIcon
import com.moments.android.views.messaging.components.AttachmentIconPreset
import com.moments.android.views.messaging.components.AttachmentIconView
import com.moments.android.views.permission.shared.PermissionPrimerGate
import com.moments.android.views.permission.shared.PermissionPrimerGateHost
import com.moments.android.views.permission.shared.PhotoLibraryAccess
import com.moments.android.views.permission.shared.photoLibraryAccess
import com.moments.android.views.shared.MomentsModalSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max

/**
 * Port de `MediaSelectionView.swift`.
 * Fondo AdaptiveColors sólido; preview sin blur de imagen (decisión de plataforma).
 */
@Composable
fun MediaSelectionView(
    selectedMediaItems: List<CreatorMedia>,
    onSelectedMediaItemsChange: (List<CreatorMedia>) -> Unit,
    onCurrentFlowChange: (CreatorFlow) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val canvas = if (isDark) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    val contentColor = MomentsChromeGlass.contentColor(isDark)
    val scope = rememberCoroutineScope()
    val photosGate = remember { PermissionPrimerGate(PermissionPrimerGate.Kind.PHOTOS) }

    var mediaAssets by remember { mutableStateOf<List<GalleryAsset>>(emptyList()) }
    var selectedAssetIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoadingLibrary by remember { mutableStateOf(true) }
    var permissionGranted by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }
    var availableAlbums by remember { mutableStateOf<List<CreatorAlbumInfo>>(emptyList()) }
    var selectedAlbum by remember { mutableStateOf<CreatorAlbumInfo?>(null) }
    var showingAlbumPicker by remember { mutableStateOf(false) }
    var isMultiSelect by remember { mutableStateOf(false) }
    var showingVideoTooLongAlert by remember { mutableStateOf(false) }
    var rejectedVideoDuration by remember { mutableStateOf(0.0) }
    var cropSessions by remember { mutableStateOf<Map<String, AssetCropSession>>(emptyMap()) }
    var cropWindowSizes by remember { mutableStateOf<Map<String, Pair<Float, Float>>>(emptyMap()) }
    var assetPixelSizes by remember { mutableStateOf<Map<String, Pair<Float, Float>>>(emptyMap()) }

    fun ensureCropSession(asset: GalleryAsset) {
        if (cropSessions.containsKey(asset.id)) return
        val stored = assetPixelSizes[asset.id]
        val width = stored?.first ?: asset.pixelWidth.toFloat()
        val height = stored?.second ?: asset.pixelHeight.toFloat()
        if (width > 2f && height > 2f && stored == null) {
            assetPixelSizes = assetPixelSizes + (asset.id to (width to height))
        }
        cropSessions = cropSessions + (
            asset.id to AssetCropSession(
                width.toInt().coerceAtLeast(1),
                height.toInt().coerceAtLeast(1),
            )
        )
    }

    fun autoSelectFirstIfNeeded(assets: List<GalleryAsset>) {
        if (selectedAssetIds.isNotEmpty()) return
        val first = assets.firstOrNull { asset ->
            !asset.isVideo || (asset.durationSeconds ?: 0.0) <= CreatorMedia.MAX_MOMENT_VIDEO_DURATION_SECONDS
        } ?: return
        ensureCropSession(first)
        selectedAssetIds = listOf(first.id)
    }

    fun resolvePixelSize(uri: Uri, isVideo: Boolean): Pair<Float, Float> {
        return runCatching {
            if (isVideo) {
                MediaMetadataRetriever().let { retriever ->
                    try {
                        retriever.setDataSource(context, uri)
                        val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toFloatOrNull() ?: 1f
                        val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toFloatOrNull() ?: 1f
                        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                            ?.toIntOrNull() ?: 0
                        if (rotation == 90 || rotation == 270) h to w.coerceAtLeast(1f) else w to h.coerceAtLeast(1f)
                    } finally {
                        retriever.release()
                    }
                }
            } else {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                orientedDisplaySize(
                    opts.outWidth.toFloat().coerceAtLeast(1f),
                    opts.outHeight.toFloat().coerceAtLeast(1f),
                    uri.exifOrientation(context),
                )
            }
        }.getOrDefault(1f to 1f)
    }

    fun toggleAssetSelection(asset: GalleryAsset) {
        val id = asset.id
        if (
            asset.isVideo &&
            (asset.durationSeconds ?: 0.0) > CreatorMedia.MAX_MOMENT_VIDEO_DURATION_SECONDS &&
            !selectedAssetIds.contains(id)
        ) {
            rejectedVideoDuration = asset.durationSeconds ?: 0.0
            showingVideoTooLongAlert = true
            return
        }
        if (isMultiSelect) {
            if (selectedAssetIds.contains(id)) {
                if (selectedAssetIds.size == 1) return
                selectedAssetIds = selectedAssetIds.filterNot { it == id }
            } else if (selectedAssetIds.size < 20) {
                selectedAssetIds = selectedAssetIds + id
                ensureCropSession(asset)
            }
        } else {
            if (selectedAssetIds == listOf(id)) return
            selectedAssetIds = listOf(id)
            ensureCropSession(asset)
        }
        if (!assetPixelSizes.containsKey(id)) {
            scope.launch {
                val size = withContext(Dispatchers.IO) { resolvePixelSize(asset.uri, asset.isVideo) }
                assetPixelSizes = assetPixelSizes + (id to size)
                val session = (
                    cropSessions[id]
                        ?: AssetCropSession(size.first.toInt().coerceAtLeast(1), size.second.toInt().coerceAtLeast(1))
                    ).applyOrientedSize(size.first, size.second)
                cropSessions = cropSessions + (id to session)
            }
        }
    }

    fun usePickerUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        scope.launch {
            val media = withContext(Dispatchers.IO) {
                uris.take(10).map { uri ->
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }
                    val isVideo = context.contentResolver.getType(uri)?.startsWith("video/") == true
                    val duration = if (isVideo) {
                        runCatching {
                            MediaMetadataRetriever().let { retriever ->
                                try {
                                    retriever.setDataSource(context, uri)
                                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                                        ?.toDoubleOrNull()?.div(1000.0)
                                } finally {
                                    retriever.release()
                                }
                            }
                        }.getOrNull()
                    } else null
                    val aspectRatio = detectAspectRatio(context, uri, isVideo)
                    val cardAspect = CreatorAspectRatio.fromFeedPostRatio(aspectRatio.ratio)
                    CreatorMedia(
                        id = uri.toString(),
                        uri = uri,
                        isVideo = isVideo,
                        durationSeconds = duration,
                        aspectRatio = cardAspect,
                        recommendedAspectRatio = aspectRatio,
                        feedCrop = MediaItemFeedCrop.fullBounds(cardAspect.displayName),
                    )
                }
            }
            onSelectedMediaItemsChange(media)
            val hasImages = media.any { !it.isVideo }
            val hasVideos = media.any { it.isVideo }
            onCurrentFlowChange(
                when {
                    hasVideos && !hasImages -> CreatorFlow.VIDEO_EDITING
                    hasImages && !hasVideos -> CreatorFlow.MEDIA_EDITING
                    else -> CreatorFlow.CAPTION_AND_DETAILS
                },
            )
        }
    }

    val systemPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(10),
        onResult = ::usePickerUris,
    )

    fun loadLibrary(album: CreatorAlbumInfo? = selectedAlbum) {
        scope.launch {
            isLoadingLibrary = true
            val albums = withContext(Dispatchers.IO) { loadAlbums(context) }
            availableAlbums = albums
            val chosen = album ?: albums.firstOrNull()
            selectedAlbum = chosen
            mediaAssets = withContext(Dispatchers.IO) { loadGalleryAssets(context, chosen?.bucketId) }
            permissionGranted = true
            permissionDenied = false
            isLoadingLibrary = false
            autoSelectFirstIfNeeded(mediaAssets)
        }
    }

    fun refreshLibrary(album: CreatorAlbumInfo?) {
        scope.launch {
            isLoadingLibrary = true
            selectedAssetIds = emptyList()
            mediaAssets = withContext(Dispatchers.IO) { loadGalleryAssets(context, album?.bucketId) }
            isLoadingLibrary = false
            autoSelectFirstIfNeeded(mediaAssets)
        }
    }

    // El Photo Picker es permissionless y solo se abre tras una acción explícita.
    // La galería MediaStore interna conserva su flujo de permiso/primer separado.
    LaunchedEffect(Unit) {
        if (photoLibraryAccess(context) != PhotoLibraryAccess.DENIED) {
            loadLibrary()
        } else {
            permissionDenied = true
            isLoadingLibrary = false
        }
    }

    var wasPhotosGatePresenting by remember { mutableStateOf(false) }
    LaunchedEffect(photosGate.isPresenting) {
        if (wasPhotosGatePresenting && !photosGate.isPresenting && !permissionGranted) {
            if (photoLibraryAccess(context) == PhotoLibraryAccess.DENIED) {
                permissionDenied = true
                isLoadingLibrary = false
            }
        }
        wasPhotosGatePresenting = photosGate.isPresenting
    }

    Box(modifier.fillMaxSize().background(canvas)) {
        Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 10.dp, bottom = 12.dp),
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .align(Alignment.CenterStart)
                    .momentsChromeGlass(CircleShape, interactive = true)
                    .clickable { onCurrentFlowChange(CreatorFlow.TYPE_SELECTION) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                stringResource(R.string.creator_new_moment),
                color = contentColor,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center),
            )
            if (selectedAssetIds.isNotEmpty()) {
                Text(
                    stringResource(R.string.creator_next),
                    color = contentColor,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
                        .clickable {
                            scope.launch {
                                val selected = selectedAssetIds.mapNotNull { id ->
                                    mediaAssets.firstOrNull { it.id == id }
                                }
                                var lockAspect: Float? = null
                                val media = withContext(Dispatchers.IO) {
                                    selected.map { asset ->
                                        val size = assetPixelSizes[asset.id]
                                            ?: resolvePixelSize(asset.uri, asset.isVideo).takeIf {
                                                it.first > 2f && it.second > 2f
                                            }
                                            ?: (asset.pixelWidth.toFloat() to asset.pixelHeight.toFloat())
                                        val imgW = size.first.coerceAtLeast(1f)
                                        val imgH = size.second.coerceAtLeast(1f)
                                        val session = cropSessions[asset.id]
                                            ?: AssetCropSession(imgW.toInt(), imgH.toInt())
                                        val previewWindow = cropWindowSizes[asset.id]
                                            ?: (MomentFeedCrop.squareSize to MomentFeedCrop.squareSize)
                                        val targetAspect = lockAspect ?: session.cropAspect
                                        val (feedCrop, framedAspect) = framedFeedCropFromSession(
                                            imgW, imgH, session, previewWindow.first, previewWindow.second, lockAspect,
                                        )
                                        if (lockAspect == null) lockAspect = framedAspect
                                        // ≡ iOS: aspectRatio = fromFeedPostRatio(targetAspect)
                                        val cardAspect = CreatorAspectRatio.fromFeedPostRatio(targetAspect)
                                        val cardUri = if (asset.isVideo) {
                                            asset.uri
                                        } else {
                                            cropCardJpeg(context, asset.uri, feedCrop) ?: asset.uri
                                        }
                                        CreatorMedia(
                                            id = asset.id,
                                            uri = cardUri,
                                            isVideo = asset.isVideo,
                                            durationSeconds = asset.durationSeconds,
                                            aspectRatio = cardAspect,
                                            recommendedAspectRatio = cardAspect,
                                            hasEdits = true,
                                            feedCrop = feedCrop,
                                            immersiveUri = asset.uri,
                                            immersiveAspectRatio = imgW / max(imgH, 1f),
                                        )
                                    }
                                }
                                onSelectedMediaItemsChange(media)
                                onCurrentFlowChange(CreatorFlow.MEDIA_EDITING)
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            } else {
                Spacer(Modifier.size(40.dp).align(Alignment.CenterEnd))
            }
        }

        val previewId = selectedAssetIds.lastOrNull()
        val preview = previewId?.let { id -> mediaAssets.firstOrNull { it.id == id } }
        if (preview != null) {
            LaunchedEffect(preview.id) {
                val size = withContext(Dispatchers.IO) {
                    resolvePixelSize(preview.uri, preview.isVideo)
                }
                assetPixelSizes = assetPixelSizes + (preview.id to size)
                val existing = cropSessions[preview.id]
                if (existing == null) {
                    cropSessions = cropSessions + (
                        preview.id to AssetCropSession(
                            size.first.toInt().coerceAtLeast(1),
                            size.second.toInt().coerceAtLeast(1),
                        )
                    )
                } else {
                    cropSessions = cropSessions + (
                        preview.id to existing.applyOrientedSize(size.first, size.second)
                    )
                }
            }
        }
        val pixel = preview?.let { assetPixelSizes[it.id] }
            ?: preview?.let { it.pixelWidth.toFloat() to it.pixelHeight.toFloat() }
            ?: (1f to 1f)
        val session = preview?.let { cropSessions[it.id] }
            ?: AssetCropSession(pixel.first.toInt().coerceAtLeast(1), pixel.second.toInt().coerceAtLeast(1))
        val sharedCropAspect = selectedAssetIds.firstOrNull()?.let { cropSessions[it]?.cropAspect }
            ?: MomentFeedCrop.squareAspect
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        ) {
            key(previewId) {
                MomentFeedCropCanvas(
                    imageUri = preview?.uri,
                    imageWidth = pixel.first,
                    imageHeight = pixel.second,
                    isVideo = preview?.isVideo == true,
                    videoDurationText = if (preview?.isVideo == true) {
                        formatMediaDuration(preview.durationSeconds ?: 0.0)
                    } else null,
                    session = session,
                    onSessionChange = { next ->
                        previewId?.let { cropSessions = cropSessions + (it to next) }
                    },
                    cropAspect = sharedCropAspect,
                    showsAspectToggle = previewId != null && previewId == selectedAssetIds.first(),
                    onWindowSizeChange = { w, h ->
                        previewId?.let { cropWindowSizes = cropWindowSizes + (it to (w to h)) }
                    },
                    onImageSizeResolved = { w, h ->
                        previewId?.let { id ->
                            assetPixelSizes = assetPixelSizes + (id to (w to h))
                            val existing = cropSessions[id]
                                ?: AssetCropSession(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1))
                            cropSessions = cropSessions + (id to existing.applyOrientedSize(w, h))
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.Gray.copy(0.3f)))
        Row(
            Modifier
                .fillMaxWidth()
                .background(canvas)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.clickable { showingAlbumPicker = true },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    selectedAlbum?.title ?: stringResource(R.string.creator_album_recents),
                    color = contentColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(11.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(32.dp)
                    .background(
                        if (isMultiSelect) Color.White else if (isDark) Color.White.copy(0.18f) else Color.Black.copy(0.08f),
                        CircleShape,
                    )
                    .clickable {
                        isMultiSelect = !isMultiSelect
                        if (!isMultiSelect) {
                            selectedAssetIds.lastOrNull()?.let { selectedAssetIds = listOf(it) }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.FilterNone,
                    contentDescription = stringResource(R.string.creator_multiple),
                    tint = if (isMultiSelect) Color(0xFF0B1215) else contentColor,
                    modifier = Modifier.size(15.dp),
                )
            }
        }

        when {
            isLoadingLibrary -> {
                Column(
                    Modifier.fillMaxSize().weight(1f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    MomentsCircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.creator_gallery_loading), color = Color.Gray, fontSize = 16.sp)
                }
            }
            permissionDenied -> {
                Column(
                    Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(horizontal = 40.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AttachmentIconView(
                        icon = AttachmentIcon.PHOTOS,
                        preset = AttachmentIconPreset.PERMISSION_PROMPT_LARGE,
                        tintColor = Color.Gray.copy(if (isDark) 1f else 0.6f),
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(
                        stringResource(R.string.creator_gallery_permission),
                        color = contentColor,
                        textAlign = TextAlign.Center,
                        fontSize = 16.sp,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.creator_permissions_instructions_title),
                        color = contentColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                    Text(
                        stringResource(R.string.creator_permissions_instructions_path),
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    TextButton(
                        onClick = {
                            systemPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
                            )
                        },
                    ) {
                        Text(stringResource(R.string.creator_select_from_gallery), color = contentColor)
                    }
                    TextButton(
                        onClick = {
                            photosGate.requestAccess(context) { loadLibrary() }
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Brush.horizontalGradient(listOf(Color.Blue, Color(0xFF9C27B0), Color(0xFFE91E63)))),
                    ) {
                        Text(stringResource(R.string.creator_allow_gallery_access), color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                    contentPadding = PaddingValues(bottom = 20.dp),
                ) {
                    items(mediaAssets, key = { it.id }) { asset ->
                        val selectedIndex = selectedAssetIds.indexOf(asset.id)
                        Box(Modifier.aspectRatio(1f)) {
                            MediaGridCell(
                                uri = asset.uri,
                                isVideo = asset.isVideo,
                                durationSeconds = asset.durationSeconds,
                                isSelected = selectedIndex >= 0,
                                isMultiSelect = isMultiSelect,
                                selectionNumber = if (isMultiSelect && selectedIndex >= 0) selectedIndex + 1 else null,
                                onTap = { toggleAssetSelection(asset) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showingAlbumPicker) {
        MomentsModalSheet(
            onDismissRequest = { showingAlbumPicker = false },
            containerColor = canvas,
        ) {
            AlbumPickerView(
                albums = availableAlbums,
                selectedAlbum = selectedAlbum,
                onAlbumSelected = { album ->
                    selectedAlbum = album
                    showingAlbumPicker = false
                    refreshLibrary(album)
                },
                onDismiss = { showingAlbumPicker = false },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (showingVideoTooLongAlert) {
        AlertDialog(
            onDismissRequest = { showingVideoTooLongAlert = false },
            title = { Text(stringResource(R.string.moment_video_too_long_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.moment_video_too_long_message,
                        formatMediaDuration(rejectedVideoDuration),
                        formatMediaDuration(CreatorMedia.MAX_MOMENT_VIDEO_DURATION_SECONDS),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { showingVideoTooLongAlert = false }) {
                    Text(stringResource(R.string.common_understood))
                }
            },
        )
    }

    PermissionPrimerGateHost(gate = photosGate)

    @Suppress("UNUSED_PARAMETER")
    val unusedDismiss = onDismiss
    }
}

private data class GalleryAsset(
    val id: String,
    val uri: Uri,
    val isVideo: Boolean,
    val durationSeconds: Double?,
    val bucketId: String?,
    val pixelWidth: Int,
    val pixelHeight: Int,
)

private fun loadAlbums(context: android.content.Context): List<CreatorAlbumInfo> {
    val recentsTitle = context.getString(R.string.creator_album_recents)
    val buckets = linkedMapOf<String, Pair<String, Int>>()
    val projection = arrayOf(
        MediaStore.Files.FileColumns.BUCKET_ID,
        MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
    )
    val selection = (
        "${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=?"
        )
    val selectionArgs = arrayOf(
        MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
        MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
    )
    var total = 0
    context.contentResolver.query(
        MediaStore.Files.getContentUri("external"),
        projection,
        selection,
        selectionArgs,
        null,
    )?.use { cursor ->
        val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_ID)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
        while (cursor.moveToNext()) {
            val bucketId = cursor.getString(bucketIdCol) ?: continue
            val name = cursor.getString(nameCol) ?: context.getString(R.string.creator_album_default)
            val current = buckets[bucketId]
            buckets[bucketId] = name to ((current?.second ?: 0) + 1)
            total++
        }
    }
    val albums = mutableListOf(
        CreatorAlbumInfo(id = "recents", title = recentsTitle, bucketId = null, assetCount = total),
    )
    buckets.entries
        .sortedByDescending { it.value.second }
        .forEach { (id, pair) ->
            albums += CreatorAlbumInfo(id = id, title = pair.first, bucketId = id, assetCount = pair.second)
        }
    return albums
}

private fun loadGalleryAssets(context: android.content.Context, bucketId: String?): List<GalleryAsset> {
    val assets = mutableListOf<GalleryAsset>()
    val projection = arrayOf(
        MediaStore.Files.FileColumns._ID,
        MediaStore.Files.FileColumns.MEDIA_TYPE,
        MediaStore.Files.FileColumns.DURATION,
        MediaStore.Files.FileColumns.BUCKET_ID,
        MediaStore.Files.FileColumns.WIDTH,
        MediaStore.Files.FileColumns.HEIGHT,
    )
    val selection = buildString {
        append("(${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=?)")
        if (bucketId != null) append(" AND ${MediaStore.Files.FileColumns.BUCKET_ID}=?")
    }
    val selectionArgs = buildList {
        add(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString())
        add(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
        if (bucketId != null) add(bucketId)
    }.toTypedArray()
    val sort = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
    context.contentResolver.query(
        MediaStore.Files.getContentUri("external"),
        projection,
        selection,
        selectionArgs,
        sort,
    )?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
        val typeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
        val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DURATION)
        val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_ID)
        val widthCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.WIDTH)
        val heightCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.HEIGHT)
        var count = 0
        while (cursor.moveToNext() && count < 500) {
            val id = cursor.getLong(idCol)
            val mediaType = cursor.getInt(typeCol)
            val isVideo = mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
            val durationMs = cursor.getLong(durationCol)
            val uri = if (isVideo) {
                ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
            } else {
                ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            }
            val pixelWidth = if (widthCol >= 0) cursor.getInt(widthCol) else 0
            val pixelHeight = if (heightCol >= 0) cursor.getInt(heightCol) else 0
            assets += GalleryAsset(
                id = id.toString(),
                uri = uri,
                isVideo = isVideo,
                durationSeconds = if (isVideo) durationMs / 1000.0 else null,
                bucketId = cursor.getString(bucketCol),
                pixelWidth = pixelWidth.coerceAtLeast(1),
                pixelHeight = pixelHeight.coerceAtLeast(1),
            )
            count++
        }
    }
    return assets
}

/** ≡ iOS `framedMedia` — `oriented.cropped(to: cropRect)` como `item.image`. */
private fun cropCardJpeg(
    context: android.content.Context,
    source: Uri,
    feedCrop: MediaItemFeedCrop,
): Uri? {
    return runCatching {
        val bitmap = context.contentResolver.openInputStream(source)?.use {
            android.graphics.BitmapFactory.decodeStream(it)
        }?.creatorNormalizedUp(context, source) ?: return null
        val cropped = MomentFeedCrop.cropBitmap(bitmap, feedCrop)
        val dir = File(context.cacheDir, "creator_feed_crop").also { it.mkdirs() }
        val file = File(dir, "card_${UUID.randomUUID()}.jpg")
        FileOutputStream(file).use { out ->
            cropped.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out)
        }
        Uri.fromFile(file)
    }.getOrNull()
}

/** Paridad con `detectAspectRatio` de MediaSelectionView.swift. */
private fun detectAspectRatio(
    context: android.content.Context,
    uri: Uri,
    isVideo: Boolean,
): CreatorAspectRatio {
    return runCatching {
        if (isVideo) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toFloatOrNull() ?: return@runCatching CreatorAspectRatio.SQUARE
                val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toFloatOrNull()?.coerceAtLeast(1f) ?: return@runCatching CreatorAspectRatio.SQUARE
                CreatorAspectRatio.fromFeedPostRatio(w / h)
            } finally {
                retriever.release()
            }
        } else {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            val w = opts.outWidth.toFloat()
            val h = opts.outHeight.toFloat().coerceAtLeast(1f)
            if (w <= 0f) CreatorAspectRatio.SQUARE else CreatorAspectRatio.fromFeedPostRatio(w / h)
        }
    }.getOrDefault(CreatorAspectRatio.SQUARE)
}
