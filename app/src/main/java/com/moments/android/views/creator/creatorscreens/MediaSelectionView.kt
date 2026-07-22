package com.moments.android.views.creator.creatorscreens

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.views.creator.CreatorAlbumInfo
import com.moments.android.views.creator.CreatorAspectRatio
import com.moments.android.views.creator.CreatorFlow
import com.moments.android.views.creator.CreatorMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever

/**
 * Port de `MediaSelectionView.swift`.
 * CameraCapture / PermissionPrimerGate: pendientes; cámara abre stub honesto.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val contentColor = if (isDark) Color.White else Color.Black
    val scope = rememberCoroutineScope()

    var mediaAssets by remember { mutableStateOf<List<GalleryAsset>>(emptyList()) }
    var selectedAssetIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoadingLibrary by remember { mutableStateOf(true) }
    var permissionGranted by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }
    var availableAlbums by remember { mutableStateOf<List<CreatorAlbumInfo>>(emptyList()) }
    var selectedAlbum by remember { mutableStateOf<CreatorAlbumInfo?>(null) }
    var showingAlbumPicker by remember { mutableStateOf(false) }
    var showingCameraPending by remember { mutableStateOf(false) }
    var showingVideoTooLongAlert by remember { mutableStateOf(false) }
    var rejectedVideoDuration by remember { mutableStateOf(0.0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val granted = results.values.any { it }
        permissionGranted = granted
        permissionDenied = !granted
        if (granted) {
            scope.launch {
                isLoadingLibrary = true
                val albums = loadAlbums(context)
                availableAlbums = albums
                selectedAlbum = albums.firstOrNull()
                mediaAssets = loadGalleryAssets(context, selectedAlbum?.bucketId)
                isLoadingLibrary = false
            }
        } else {
            isLoadingLibrary = false
        }
    }

    fun refreshLibrary(album: CreatorAlbumInfo?) {
        scope.launch {
            isLoadingLibrary = true
            selectedAssetIds = emptyList()
            mediaAssets = loadGalleryAssets(context, album?.bucketId)
            isLoadingLibrary = false
        }
    }

    LaunchedEffect(Unit) {
        val perms = galleryPermissions()
        val allGranted = perms.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            permissionGranted = true
            val albums = withContext(Dispatchers.IO) { loadAlbums(context) }
            availableAlbums = albums
            selectedAlbum = albums.firstOrNull()
            mediaAssets = withContext(Dispatchers.IO) { loadGalleryAssets(context, selectedAlbum?.bucketId) }
            isLoadingLibrary = false
        } else {
            permissionLauncher.launch(perms)
        }
    }

    if (showingCameraPending) {
        CreatorFlowPendingScreen(
            iosSource = "CameraCapture.swift",
            onBack = { showingCameraPending = false },
            onDismiss = onDismiss,
            modifier = modifier,
        )
        return
    }

    Column(
        modifier
            .fillMaxSize()
            .background(canvas),
    ) {
        // iOS headerView: H 16 / top 10 / bottom 12; back 40; title centered
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
                Row(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .clip(RoundedCornerShape(50))
                        .background(Brush.horizontalGradient(listOf(Color(0xFF9C27B0), Color(0xFFE91E63))))
                        .clickable {
                            val selected = selectedAssetIds.mapNotNull { id ->
                                mediaAssets.firstOrNull { it.id == id }
                            }
                            val media = selected.map {
                                val detected = detectAspectRatio(context, it.uri, it.isVideo)
                                CreatorMedia(
                                    id = it.id,
                                    uri = it.uri,
                                    isVideo = it.isVideo,
                                    durationSeconds = it.durationSeconds,
                                    aspectRatio = detected,
                                    recommendedAspectRatio = detected,
                                )
                            }
                            onSelectedMediaItemsChange(media)
                            val hasImages = media.any { !it.isVideo }
                            val hasVideos = media.any { it.isVideo }
                            onCurrentFlowChange(
                                when {
                                    hasVideos && !hasImages -> CreatorFlow.VIDEO_EDITING
                                    hasImages && !hasVideos -> CreatorFlow.MEDIA_EDITING
                                    hasImages && hasVideos -> CreatorFlow.CAPTION_AND_DETAILS
                                    else -> CreatorFlow.MEDIA_EDITING
                                },
                            )
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.creator_next),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            } else {
                Spacer(Modifier.size(40.dp).align(Alignment.CenterEnd))
            }
        }

        // mainPreviewSection
        if (selectedAssetIds.isNotEmpty()) {
            val previewId = selectedAssetIds.last()
            val preview = mediaAssets.firstOrNull { it.id == previewId }
            if (preview != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = preview.uri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().alpha(0.35f),
                    )
                    AsyncImage(
                        model = preview.uri,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .padding(vertical = 10.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                            .size(28.dp)
                            .clickable {
                                selectedAssetIds = selectedAssetIds.filterNot { it == preview.id }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = null,
                            tint = Color.White.copy(0.8f),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    if (preview.isVideo) {
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(12.dp)
                                .background(Color.Black.copy(0.5f), RoundedCornerShape(50))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                formatMediaDuration(preview.durationSeconds ?: 0.0),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.background(canvas.copy(if (isDark) 0.92f else 0.98f)),
                ) {
                    items(selectedAssetIds, key = { it }) { id ->
                        val asset = mediaAssets.firstOrNull { it.id == id } ?: return@items
                        AsyncImage(
                            model = asset.uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    2.dp,
                                    if (id == selectedAssetIds.last()) Color(0xFFFF2D55) else Color.White.copy(0.3f),
                                    RoundedCornerShape(8.dp),
                                )
                                .clickable {
                                    selectedAssetIds = selectedAssetIds.filterNot { it == id } + id
                                },
                        )
                    }
                }
            }
        }

        // mediaGridSection
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.Gray.copy(0.3f)))
        Row(
            Modifier
                .fillMaxWidth()
                .background(canvas)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isDark) Color.White.copy(0.1f) else Color.Black.copy(0.05f))
                    .clickable { showingAlbumPicker = true }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    selectedAlbum?.title ?: stringResource(R.string.creator_album_recents),
                    color = contentColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = contentColor.copy(0.7f),
                    modifier = Modifier.size(10.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(Color(0xFF9C27B0), Color(0xFFE91E63))))
                    .clickable { showingCameraPending = true }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Text(
                    stringResource(R.string.creator_camera),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
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
                    CircularProgressIndicator(color = Color(0xFF007AFF))
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
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
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
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                },
                            )
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Brush.horizontalGradient(listOf(Color.Blue, Color(0xFF9C27B0), Color(0xFFE91E63)))),
                    ) {
                        Text(stringResource(R.string.creator_permissions_open_settings), color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
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
                                selectionNumber = if (selectedIndex >= 0) selectedIndex + 1 else null,
                                onTap = {
                                    if (selectedAssetIds.contains(asset.id)) {
                                        selectedAssetIds = selectedAssetIds.filterNot { it == asset.id }
                                    } else {
                                        if (asset.isVideo &&
                                            (asset.durationSeconds ?: 0.0) > CreatorMedia.MAX_MOMENT_VIDEO_DURATION_SECONDS
                                        ) {
                                            rejectedVideoDuration = asset.durationSeconds ?: 0.0
                                            showingVideoTooLongAlert = true
                                            return@MediaGridCell
                                        }
                                        if (selectedAssetIds.size < 10) {
                                            selectedAssetIds = selectedAssetIds + asset.id
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showingAlbumPicker) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showingAlbumPicker = false },
            sheetState = sheetState,
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

    // selectedMediaItems param reserved for restore; suppress unused until editors round-trip.
    @Suppress("UNUSED_VARIABLE")
    val keep = selectedMediaItems
}

@Composable
private fun AlbumPickerView(
    albums: List<CreatorAlbumInfo>,
    selectedAlbum: CreatorAlbumInfo?,
    onAlbumSelected: (CreatorAlbumInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val contentColor = if (isDark) Color.White else Color.Black
    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Text(
            stringResource(R.string.creator_album_select),
            color = contentColor,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
        albums.forEach { album ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onAlbumSelected(album) }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(album.title, color = contentColor, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text("${album.assetCount}", color = Color.Gray, fontSize = 13.sp)
                if (selectedAlbum?.id == album.id) {
                    Text(" ✓", color = Color(0xFFE91E63))
                }
            }
        }
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.login_close), color = contentColor)
        }
    }
}

private data class GalleryAsset(
    val id: String,
    val uri: Uri,
    val isVideo: Boolean,
    val durationSeconds: Double?,
    val bucketId: String?,
)

private fun galleryPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

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
            assets += GalleryAsset(
                id = id.toString(),
                uri = uri,
                isVideo = isVideo,
                durationSeconds = if (isVideo) durationMs / 1000.0 else null,
                bucketId = cursor.getString(bucketCol),
            )
            count++
        }
    }
    return assets
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
                CreatorAspectRatio.fromRatio(w / h)
            } finally {
                retriever.release()
            }
        } else {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            val w = opts.outWidth.toFloat()
            val h = opts.outHeight.toFloat().coerceAtLeast(1f)
            if (w <= 0f) CreatorAspectRatio.SQUARE else CreatorAspectRatio.fromRatio(w / h)
        }
    }.getOrDefault(CreatorAspectRatio.SQUARE)
}
