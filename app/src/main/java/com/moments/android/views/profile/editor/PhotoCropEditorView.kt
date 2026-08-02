package com.moments.android.views.profile.editor

import android.Manifest
import android.content.ContentUris
import android.content.ContentResolver
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import com.moments.android.views.components.MomentsCircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.views.creator.creatoruikit.creatorNormalizedUp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/** Asset de galería para el grid del crop (Uri ≡ PHAsset). */
data class ProfilePhotoAsset(
    val uri: Uri,
    val album: String,
    val bucketId: String? = null,
)

private const val CropOutputSide = 400
private const val MinCropScale = 0.5f
private const val MaxCropScale = 4f

/**
 * Port de `PhotoCropEditorView.swift`.
 * Crop circular preview (máscara destinationOut), blur de fondo, pan/zoom,
 * double-tap reset, álbumes MediaStore + grid paginado, salida 400×400.
 */
@Composable
fun PhotoCropEditorView(
    originalUri: Uri,
    onSave: (Bitmap) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val dark = isSystemInDarkTheme()
    val canvas = if (dark) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    val primary = if (dark) Color.White else Color.Black

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoadingImage by remember { mutableStateOf(true) }
    var isProcessing by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    var lastScale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var isDragging by remember { mutableStateOf(false) }
    var isZooming by remember { mutableStateOf(false) }

    var albums by remember { mutableStateOf<List<ProfileAlbumInfo>>(emptyList()) }
    var selectedAlbum by remember { mutableStateOf<ProfileAlbumInfo?>(null) }
    var assets by remember { mutableStateOf<List<ProfilePhotoAsset>>(emptyList()) }
    var isLoadingPhotos by remember { mutableStateOf(false) }
    var visiblePhotoCount by remember { mutableIntStateOf(20) }
    var albumMenu by remember { mutableStateOf(false) }
    var hasMediaPermission by remember {
        mutableStateOf(hasProfileMediaPermission(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        hasMediaPermission = result.values.any { it } || hasProfileMediaPermission(context)
    }

    fun loadImage(uri: Uri) {
        isLoadingImage = true
        bitmap = null
        scale = 1f
        lastScale = 1f
        offset = Offset.Zero
        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                loadNormalizedBitmap(context.contentResolver, context, uri)
            }
            bitmap = loaded
            isLoadingImage = false
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    fun loadLibrary(album: ProfileAlbumInfo?) {
        if (!hasMediaPermission) return
        isLoadingPhotos = true
        visiblePhotoCount = 20
        scope.launch {
            val (nextAlbums, nextAssets) = withContext(Dispatchers.IO) {
                val albumList = loadProfileAlbums(context)
                val photos = loadProfilePhotos(context, album?.bucketId)
                albumList to photos
            }
            albums = nextAlbums
            if (selectedAlbum == null) {
                selectedAlbum = nextAlbums.firstOrNull()
            }
            assets = nextAssets
            isLoadingPhotos = false
        }
    }

    LaunchedEffect(originalUri) { loadImage(originalUri) }

    LaunchedEffect(hasMediaPermission) {
        if (hasMediaPermission) {
            loadLibrary(selectedAlbum)
        } else {
            permissionLauncher.launch(profileMediaPermissions())
        }
    }

    LaunchedEffect(selectedAlbum?.id, hasMediaPermission) {
        if (hasMediaPermission) loadLibrary(selectedAlbum)
    }

    Box(modifier.fillMaxSize().background(canvas)) {
        when {
            isLoadingImage -> CropLoadingState(
                label = R.string.profile_crop_loading,
                primary = primary,
            )
            bitmap != null -> {
                Column(Modifier.fillMaxSize()) {
                    CropHeaderBar(
                        primary = primary,
                        processing = isProcessing,
                        onDismiss = onDismiss,
                        onSave = {
                            val source = bitmap ?: return@CropHeaderBar
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            isProcessing = true
                            scope.launch {
                                val output = withContext(Dispatchers.Default) {
                                    cropSquareImage(
                                        image = source,
                                        scale = scale,
                                        offset = offset,
                                        cropSizePx = cropFrameSidePx(context),
                                    )
                                }
                                isProcessing = false
                                if (output != null) {
                                    onSave(output)
                                    onDismiss()
                                }
                            }
                        },
                    )

                    Column(
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 40.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        CropAreaView(
                            bitmap = bitmap!!,
                            scale = scale,
                            offset = offset,
                            isDragging = isDragging,
                            isZooming = isZooming,
                            processing = isProcessing,
                            dark = dark,
                            primary = primary,
                            onScale = { scale = it; lastScale = it },
                            onOffset = { offset = it },
                            onDragging = { isDragging = it },
                            onZooming = { isZooming = it },
                            onDoubleTap = {
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                scale = 1f
                                lastScale = 1f
                                offset = Offset.Zero
                            },
                            limitOffset = { proposed, imageSize, cropPx ->
                                limitCropOffset(proposed, imageSize, scale, cropPx)
                            },
                        )

                        Box(Modifier.padding(horizontal = 20.dp)) {
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(50))
                                    .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
                                    .clickable { albumMenu = true }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    selectedAlbum?.title
                                        ?: stringResource(R.string.profile_crop_recent),
                                    color = primary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp,
                                )
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    null,
                                    tint = primary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            DropdownMenu(albumMenu, { albumMenu = false }) {
                                albums.forEach { album ->
                                    DropdownMenuItem(
                                        text = {
                                            Text("${album.title} (${album.assetCount})")
                                        },
                                        onClick = {
                                            selectedAlbum = album
                                            albumMenu = false
                                        },
                                    )
                                }
                            }
                        }

                        PhotoCropGridSection(
                            assets = assets.take(visiblePhotoCount),
                            isLoading = isLoadingPhotos,
                            primary = primary,
                            dark = dark,
                            onSelect = { uri ->
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                loadImage(uri)
                            },
                            onNearEnd = {
                                if (visiblePhotoCount < assets.size) {
                                    visiblePhotoCount = min(visiblePhotoCount + 20, assets.size)
                                }
                            },
                        )
                    }
                }
            }
        }

        if (isProcessing) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background((if (dark) Color.Black else Color.White).copy(0.8f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    MomentsCircularProgressIndicator()
                    Text(
                        stringResource(R.string.profile_crop_processing),
                        color = primary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CropHeaderBar(
    primary: Color,
    processing: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Close,
            contentDescription = stringResource(R.string.common_close),
            tint = primary,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .momentsChromeGlass(CircleShape, interactive = !processing)
                .clickable(enabled = !processing, onClick = onDismiss)
                .padding(10.dp),
        )
        Text(
            stringResource(R.string.profile_crop_move_scale),
            color = primary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Filled.Check,
            contentDescription = stringResource(R.string.profile_crop_save),
            tint = primary,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .momentsChromeGlass(CircleShape, interactive = !processing)
                .clickable(enabled = !processing, onClick = onSave)
                .padding(10.dp),
        )
    }
}

@Composable
private fun CropLoadingState(label: Int, primary: Color) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        MomentsCircularProgressIndicator()
        Text(
            stringResource(label),
            color = primary.copy(0.8f),
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            modifier = Modifier.padding(top = 20.dp),
        )
    }
}

@Composable
private fun CropAreaView(
    bitmap: Bitmap,
    scale: Float,
    offset: Offset,
    isDragging: Boolean,
    isZooming: Boolean,
    processing: Boolean,
    dark: Boolean,
    primary: Color,
    onScale: (Float) -> Unit,
    onOffset: (Offset) -> Unit,
    onDragging: (Boolean) -> Unit,
    onZooming: (Boolean) -> Unit,
    onDoubleTap: () -> Unit,
    limitOffset: (Offset, Size, Float) -> Offset,
) {
    val density = LocalDensity.current
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
    ) {
        val cropPx = with(density) { maxWidth.toPx() }
        val imageSize = Size(bitmap.width.toFloat(), bitmap.height.toFloat())
        val base = foregroundDisplaySize(imageSize, cropPx)
        val pressScale = if (isDragging || isZooming) 1.02f else 1f

        // Blurred fill background (≡ image.withBlur + dim)
        androidx.compose.foundation.Image(
            bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(40.dp),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background((if (dark) Color.Black else Color.White).copy(if (dark) 0.18f else 0.08f)),
        )

        // Foreground fit image with pan/zoom
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(0))
                .pointerInput(bitmap, processing, scale, offset) {
                    if (processing) return@pointerInput
                    detectTapGestures(onDoubleTap = { onDoubleTap() })
                }
                .pointerInput(bitmap, processing, scale, offset) {
                    if (processing) return@pointerInput
                    detectTransformGestures { _, pan, zoom, _ ->
                        onDragging(true)
                        onZooming(zoom != 1f)
                        val nextScale = (scale * zoom).coerceIn(MinCropScale, MaxCropScale)
                        onScale(nextScale)
                        onOffset(
                            limitOffset(
                                Offset(offset.x + pan.x, offset.y + pan.y),
                                imageSize,
                                cropPx,
                            ),
                        )
                    }
                }
                .pointerInput(Unit) {
                    // Gesture end approximation: clear drag/zoom flags after idle via transform end
                    // detectTransformGestures doesn't expose onEnd; clear on next frame via parent
                },
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.foundation.Image(
                bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .width(with(density) { (base.width * scale).toDp() })
                    .height(with(density) { (base.height * scale).toDp() })
                    .graphicsLayer {
                        translationX = offset.x
                        translationY = offset.y
                        scaleX = pressScale
                        scaleY = pressScale
                    },
            )
        }

        // Circular mask (destinationOut) + vignette
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawCircle(
                        color = Color.Black,
                        radius = size.minDimension / 2f,
                        center = Offset(size.width / 2f, size.height / 2f),
                        blendMode = BlendMode.Clear,
                    )
                }
                .background((if (dark) Color.Black else Color.White).copy(if (dark) 0.76f else 0.52f))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            (if (dark) Color.Black else Color.White).copy(if (dark) 0.14f else 0.08f),
                            Color.Transparent,
                            (if (dark) Color.Black else Color.White).copy(if (dark) 0.28f else 0.16f),
                        ),
                    ),
                ),
        )

        AnimatedVisibility(
            visible = isDragging || isZooming,
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f),
            modifier = Modifier.fillMaxSize(),
        ) {
            CropHelpGrid(primary = primary, side = maxWidth)
        }

        // Clear drag/zoom when gesture settles: listen via LaunchedEffect on scale/offset churn
        LaunchedEffect(scale, offset) {
            kotlinx.coroutines.delay(120)
            onDragging(false)
            onZooming(false)
        }
    }
}

@Composable
private fun CropHelpGrid(primary: Color, side: Dp) {
    val line = primary.copy(0.2f)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(Modifier.size(side)) {
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Spacer(Modifier.width(1.dp).fillMaxSize().background(Color.Transparent))
                Box(Modifier.width(1.dp).fillMaxSize().background(line))
                Box(Modifier.width(1.dp).fillMaxSize().background(line))
                Spacer(Modifier.width(1.dp).fillMaxSize().background(Color.Transparent))
            }
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                Spacer(Modifier.height(1.dp).fillMaxWidth().background(Color.Transparent))
                Box(Modifier.fillMaxWidth().height(1.dp).background(line))
                Box(Modifier.fillMaxWidth().height(1.dp).background(line))
                Spacer(Modifier.height(1.dp).fillMaxWidth().background(Color.Transparent))
            }
        }
    }
}

@Composable
private fun PhotoCropGridSection(
    assets: List<ProfilePhotoAsset>,
    isLoading: Boolean,
    primary: Color,
    dark: Boolean,
    onSelect: (Uri) -> Unit,
    onNearEnd: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
        val itemSide = (maxWidth - 12.dp) / 4
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (isLoading && assets.isEmpty()) {
                repeat(2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(4) {
                            Box(
                                Modifier
                                    .size(itemSide)
                                    .background(
                                        (if (dark) Color.White else Color.Black).copy(0.08f),
                                        RoundedCornerShape(2.dp),
                                    ),
                            )
                        }
                    }
                }
            } else {
                assets.chunked(4).forEachIndexed { rowIndex, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEachIndexed { colIndex, asset ->
                            val absoluteIndex = rowIndex * 4 + colIndex
                            AsyncImage(
                                asset.uri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(itemSide)
                                    .clip(RoundedCornerShape(2.dp))
                                    .clickable { onSelect(asset.uri) },
                            )
                            if (absoluteIndex >= assets.size - 4) {
                                LaunchedEffect(absoluteIndex) { onNearEnd() }
                            }
                        }
                        repeat(4 - row.size) {
                            Spacer(Modifier.size(itemSide))
                        }
                    }
                }
            }
        }
    }
}

private fun foregroundDisplaySize(imageSize: Size, cropPx: Float): Size {
    if (imageSize.width <= 0f || imageSize.height <= 0f) return Size(cropPx, cropPx)
    val fit = min(cropPx / imageSize.width, cropPx / imageSize.height)
    return Size(imageSize.width * fit, imageSize.height * fit)
}

private fun limitCropOffset(
    proposed: Offset,
    imageSize: Size,
    scale: Float,
    cropPx: Float,
): Offset {
    val base = foregroundDisplaySize(imageSize, cropPx)
    val scaledW = base.width * scale
    val scaledH = base.height * scale
    val maxX = max(0f, (scaledW - cropPx) / 2f)
    val maxY = max(0f, (scaledH - cropPx) / 2f)
    return Offset(
        proposed.x.coerceIn(-maxX, maxX),
        proposed.y.coerceIn(-maxY, maxY),
    )
}

private fun cropSquareImage(
    image: Bitmap,
    scale: Float,
    offset: Offset,
    cropSizePx: Float,
): Bitmap? {
    if (image.width <= 0 || image.height <= 0 || cropSizePx <= 0f) return null
    val imageSize = Size(image.width.toFloat(), image.height.toFloat())
    val base = foregroundDisplaySize(imageSize, cropSizePx)
    val scaledW = base.width * scale
    val scaledH = base.height * scale
    val outputFactor = CropOutputSide / cropSizePx
    val finalX = ((cropSizePx - scaledW) / 2f + offset.x) * outputFactor
    val finalY = ((cropSizePx - scaledH) / 2f + offset.y) * outputFactor
    val finalW = scaledW * outputFactor
    val finalH = scaledH * outputFactor

    val output = Bitmap.createBitmap(CropOutputSide, CropOutputSide, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    val blurBg = approximateBlur(image)
    canvas.drawBitmap(blurBg, null, RectF(0f, 0f, CropOutputSide.toFloat(), CropOutputSide.toFloat()), paint)
    canvas.drawColor(AndroidColor.argb(77, 0, 0, 0)) // ≈ 0.3 black overlay
    canvas.drawBitmap(
        image,
        null,
        RectF(finalX, finalY, finalX + finalW, finalY + finalH),
        paint,
    )
    if (blurBg !== image) blurBg.recycle()
    return output
}

/** Blur barato (downscale/upscale) ≈ `withBlur(radius: 25)` del export iOS. */
private fun approximateBlur(source: Bitmap): Bitmap {
    val w = max(1, source.width / 12)
    val h = max(1, source.height / 12)
    val small = Bitmap.createScaledBitmap(source, w, h, true)
    return Bitmap.createScaledBitmap(small, source.width, source.height, true).also {
        if (small !== source) small.recycle()
    }
}

private fun loadNormalizedBitmap(
    resolver: ContentResolver,
    context: android.content.Context,
    uri: Uri,
): Bitmap? {
    val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
    return decoded.creatorNormalizedUp(context, uri)
}

private fun loadProfileAlbums(context: android.content.Context): List<ProfileAlbumInfo> {
    val recents = context.getString(R.string.profile_crop_recent)
    val buckets = linkedMapOf<String, Pair<String, Int>>()
    val projection = arrayOf(
        MediaStore.Images.Media.BUCKET_ID,
        MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
    )
    var total = 0
    context.contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        projection,
        null,
        null,
        null,
    )?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
        while (cursor.moveToNext()) {
            val bucketId = cursor.getString(idCol) ?: continue
            val name = cursor.getString(nameCol)
                ?: context.getString(R.string.creator_album_default)
            val current = buckets[bucketId]
            buckets[bucketId] = name to ((current?.second ?: 0) + 1)
            total++
        }
    }
    val albums = mutableListOf(
        ProfileAlbumInfo(id = "recents", title = recents, assetCount = total, bucketId = null),
    )
    buckets.entries
        .sortedByDescending { it.value.second }
        .forEach { (id, pair) ->
            albums += ProfileAlbumInfo(
                id = id,
                title = pair.first,
                assetCount = pair.second,
                bucketId = id,
            )
        }
    return albums
}

private fun loadProfilePhotos(
    context: android.content.Context,
    bucketId: String?,
    limit: Int = 200,
): List<ProfilePhotoAsset> {
    val result = mutableListOf<ProfilePhotoAsset>()
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
        MediaStore.Images.Media.BUCKET_ID,
    )
    val selection = if (bucketId != null) "${MediaStore.Images.Media.BUCKET_ID}=?" else null
    val args = if (bucketId != null) arrayOf(bucketId) else null
    context.contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        projection,
        selection,
        args,
        "${MediaStore.Images.Media.DATE_ADDED} DESC",
    )?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
        val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
        while (cursor.moveToNext() && result.size < limit) {
            val id = cursor.getLong(idCol)
            val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            result += ProfilePhotoAsset(
                uri = uri,
                album = cursor.getString(nameCol).orEmpty(),
                bucketId = cursor.getString(bucketCol),
            )
        }
    }
    return result
}

/** Primera foto reciente (≡ `fetchMostRecentAsset` iOS). */
internal fun fetchMostRecentProfileImageUri(context: android.content.Context): Uri? =
    loadProfilePhotos(context, bucketId = null, limit = 1).firstOrNull()?.uri

/** Miniatura de álbum (primera foto del bucket). */
internal fun fetchAlbumThumbnailUri(
    context: android.content.Context,
    bucketId: String?,
): Uri? = loadProfilePhotos(context, bucketId = bucketId, limit = 1).firstOrNull()?.uri

private fun cropFrameSidePx(context: android.content.Context): Float =
    context.resources.displayMetrics.widthPixels.toFloat()

private fun profileMediaPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

private fun hasProfileMediaPermission(context: android.content.Context): Boolean =
    profileMediaPermissions().any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
