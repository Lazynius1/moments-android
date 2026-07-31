package com.moments.android.views.nova.novasections

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SwitchCamera
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.extensions.MomentsGlassButtonTint
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.services.performance.MotionPolicy
import com.moments.android.views.creator.components.CaptureButton
import com.moments.android.views.creator.components.StoryEditorChromeColor
import com.moments.android.views.creator.creatoruikit.CameraPreviewView
import com.moments.android.views.messaging.components.AttachmentIcon
import com.moments.android.views.messaging.components.AttachmentIconPreset
import com.moments.android.views.messaging.components.AttachmentIconView
import com.moments.android.views.nova.novacore.NovaColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Port de `Views/Nova/NovaSections/NovaAttachmentSheet.swift`.
 * Menú popover, overlay medium cámara/fotos y chrome story-style.
 */

enum class NovaAttachmentSheetKind { MENU, CAMERA, PHOTOS }

private object NovaAttachmentSheetMetrics {
    val horizontalInset = 10.dp
    val cornerRadius = 24.dp
    val menuPopoverMinWidth = 168.dp
    val menuPopoverGap = 16.dp
    const val heightFraction = 0.58f
}

// MARK: - Menu popover

@Composable
fun NovaAttachmentMenuPopover(
    activeSheet: NovaAttachmentSheetKind?,
    onSheetChange: (NovaAttachmentSheetKind?) -> Unit,
    plusButtonAnchor: Rect = Rect.Zero,
    modifier: Modifier = Modifier,
) {
    if (activeSheet != NovaAttachmentSheetKind.MENU) return
    val isDark = isSystemInDarkTheme()
    val scrim = Color.Black.copy(alpha = if (isDark) 0.12f else 0.08f)
    var popoverSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .zIndex(40f)
            .background(scrim)
            .clickable { onSheetChange(null) },
    ) {
        val containerW = constraints.maxWidth.toFloat()
        val containerH = constraints.maxHeight.toFloat()
        val popoverWidthPx = if (popoverSize.width > 0) {
            popoverSize.width.toFloat()
        } else {
            with(density) { NovaAttachmentSheetMetrics.menuPopoverMinWidth.toPx() }
        }
        val popoverHeightPx = if (popoverSize.height > 0) {
            popoverSize.height.toFloat()
        } else {
            with(density) { 120.dp.toPx() }
        }
        val gapPx = with(density) { NovaAttachmentSheetMetrics.menuPopoverGap.toPx() }
        val marginPx = with(density) { 16.dp.toPx() }

        val hasAnchor = plusButtonAnchor != Rect.Zero
        val leadingX = if (hasAnchor) {
            val maxLeading = containerW - marginPx - popoverWidthPx
            minOf(maxOf(plusButtonAnchor.left, marginPx), maxOf(0f, maxLeading))
        } else {
            marginPx
        }
        val topY = if (hasAnchor) {
            plusButtonAnchor.top - gapPx - popoverHeightPx
        } else {
            containerH - with(density) { 72.dp.toPx() } - popoverHeightPx
        }.coerceAtLeast(0f)

        Box(
            modifier = Modifier
                .offset { IntOffset(leadingX.roundToInt(), topY.roundToInt()) }
                .onSizeChanged { popoverSize = it }
                .clickable(enabled = false) {},
        ) {
            NovaAttachmentMenuPopoverCard(onSheetChange = onSheetChange)
        }
    }
}

@Composable
private fun NovaAttachmentMenuPopoverCard(onSheetChange: (NovaAttachmentSheetKind?) -> Unit) {
    val isDark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(NovaAttachmentSheetMetrics.cornerRadius)
    val primaryText = if (isDark) Color.White else NovaColors.textPrimary
    val iconFill = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.06f)

    Column(
        modifier = Modifier
            .widthIn(min = NovaAttachmentSheetMetrics.menuPopoverMinWidth)
            .shadow(
                elevation = 24.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = if (isDark) 0.24f else 0.12f),
                spotColor = Color.Black.copy(alpha = if (isDark) 0.24f else 0.12f),
            )
            .momentsChromeGlass(shape, interactive = true)
            .clip(shape)
            .padding(vertical = 10.dp, horizontal = 12.dp),
    ) {
        NovaAttachmentMenuRow(
            icon = AttachmentIcon.CAMERA,
            titleRes = R.string.nova_attach_camera,
            primaryText = primaryText,
            iconFill = iconFill,
        ) { onSheetChange(NovaAttachmentSheetKind.CAMERA) }
        NovaAttachmentMenuRow(
            icon = AttachmentIcon.PHOTOS,
            titleRes = R.string.nova_attach_photos,
            primaryText = primaryText,
            iconFill = iconFill,
        ) { onSheetChange(NovaAttachmentSheetKind.PHOTOS) }
    }
}

@Composable
private fun NovaAttachmentMenuRow(
    icon: AttachmentIcon,
    titleRes: Int,
    primaryText: Color,
    iconFill: Color,
    action: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = action)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconFill),
            contentAlignment = Alignment.Center,
        ) {
            AttachmentIconView(
                icon = icon,
                preset = AttachmentIconPreset.ATTACHMENT_MENU,
                tintColor = primaryText,
            )
        }
        Text(
            text = stringResource(titleRes),
            color = primaryText,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

// MARK: - Overlay

@Composable
fun NovaAttachmentSheetOverlay(
    activeSheet: NovaAttachmentSheetKind?,
    onSheetChange: (NovaAttachmentSheetKind?) -> Unit,
    onCaptured: (Bitmap) -> Unit,
    onAdd: (Bitmap) -> Unit,
) {
    val kind = activeSheet?.takeIf { it != NovaAttachmentSheetKind.MENU } ?: return
    val isDark = isSystemInDarkTheme()
    val density = LocalDensity.current
    val navBottom = WindowInsets.navigationBars.getBottom(density).let { with(density) { it.toDp() } }
    val bottomPadding = NovaInputBarLayout.attachmentSheetBottomInset(navBottom)
    var dragOffset by remember(kind) { mutableFloatStateOf(0f) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(45f),
    ) {
        val sheetHeight = maxHeight * NovaAttachmentSheetMetrics.heightFraction
        val sheetHeightPx = with(density) { sheetHeight.toPx() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (isDark) 0.28f else 0.16f))
                .clickable {
                    dragOffset = 0f
                    onSheetChange(null)
                },
        )

        NovaAttachmentSheetSurface(
            kind = kind,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = NovaAttachmentSheetMetrics.horizontalInset)
                .padding(bottom = bottomPadding)
                .height(sheetHeight)
                .offset { IntOffset(0, dragOffset.roundToInt()) }
                .pointerInput(kind, sheetHeightPx) {
                    detectVerticalDragGestures(
                        onDragStart = { dragOffset = 0f },
                        onDragEnd = {
                            val shouldDismiss = dragOffset > sheetHeightPx * 0.2f
                            if (shouldDismiss) {
                                dragOffset = 0f
                                onSheetChange(null)
                            } else {
                                dragOffset = 0f
                            }
                        },
                        onDragCancel = { dragOffset = 0f },
                        onVerticalDrag = { _, delta -> dragOffset = max(0f, dragOffset + delta) },
                    )
                }
                .clickable(enabled = false) {},
        ) {
            when (kind) {
                NovaAttachmentSheetKind.CAMERA -> NovaAttachmentCameraSheet(
                    onCaptured = onCaptured,
                    onBack = { dragOffset = 0f; onSheetChange(NovaAttachmentSheetKind.MENU) },
                )
                NovaAttachmentSheetKind.PHOTOS -> NovaAttachmentPhotoGridSheet(
                    onAdd = onAdd,
                    onBack = { dragOffset = 0f; onSheetChange(NovaAttachmentSheetKind.MENU) },
                )
                NovaAttachmentSheetKind.MENU -> Unit
            }
        }
    }
}

@Composable
private fun NovaAttachmentSheetSurface(
    kind: NovaAttachmentSheetKind,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(NovaAttachmentSheetMetrics.cornerRadius)
    val bg = when (kind) {
        NovaAttachmentSheetKind.CAMERA -> Color.Black
        else -> MomentsGlassButtonTint.canvas(isSystemInDarkTheme())
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(24.dp, shape, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.18f))
            .clip(shape)
            .background(bg),
    ) {
        content()
    }
}

// MARK: - Camera

@Composable
fun NovaAttachmentCameraSheet(onCaptured: (Bitmap) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var permitted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> permitted = granted }
    LaunchedEffect(Unit) {
        if (!permitted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    if (!permitted) {
        NovaAttachmentPermissionPrompt(R.string.nova_attach_camera_permission)
        return
    }

    var flash by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var position by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var captureToken by remember { mutableIntStateOf(0) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var toolsOpen by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        CameraPreviewView(
            cameraPosition = position,
            flashMode = flash,
            isRecording = false,
            zoomLevel = zoom,
            capturePhotoToken = captureToken,
            captureAudio = false,
            prefersMaximumCaptureQuality = true,
            onRecordingStateChange = {},
            onImageCaptured = { uri -> context.decodeBitmap(uri)?.let(onCaptured) },
            onVideoCaptured = {},
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, gestureZoom, _ ->
                        zoom = (zoom * gestureZoom).coerceIn(0.5f, 5f)
                    }
                },
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (position == CameraSelector.LENS_FACING_BACK && abs(zoom - 1f) > 0.08f) {
                Text(
                    text = stringResource(R.string.nova_attach_zoom, zoom.formatZoom()),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(bottom = 10.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                NovaStoryRoundButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.nova_attach_back_accessibility),
                    onClick = onBack,
                )
                Spacer(Modifier.weight(1f))
                CaptureButton(
                    isRecording = false,
                    onTap = { captureToken++ },
                    onLongPressStart = {},
                    onLongPressEnd = {},
                )
                Spacer(Modifier.weight(1f))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AnimatedVisibility(
                        visible = toolsOpen,
                        enter = fadeIn() + scaleIn(initialScale = 0.94f),
                        exit = fadeOut() + scaleOut(targetScale = 0.94f),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (position == CameraSelector.LENS_FACING_BACK) {
                                NovaStoryRoundButton(
                                    icon = flash.icon(),
                                    contentDescription = stringResource(R.string.nova_attach_flash_accessibility),
                                    onClick = { flash = flash.nextFlash() },
                                )
                            }
                            NovaStoryRoundButton(
                                icon = Icons.Default.SwitchCamera,
                                contentDescription = stringResource(R.string.nova_attach_flip_accessibility),
                                onClick = {
                                    position = if (position == CameraSelector.LENS_FACING_BACK) {
                                        CameraSelector.LENS_FACING_FRONT
                                    } else {
                                        CameraSelector.LENS_FACING_BACK
                                    }
                                    if (position == CameraSelector.LENS_FACING_FRONT) {
                                        flash = ImageCapture.FLASH_MODE_OFF
                                        zoom = 1f
                                    }
                                },
                            )
                        }
                    }
                    NovaStoryRoundButton(
                        icon = if (toolsOpen) Icons.Default.Close else Icons.Default.MoreVert,
                        contentDescription = stringResource(
                            if (toolsOpen) R.string.common_close else R.string.nova_attach_more_accessibility,
                        ),
                        onClick = { toolsOpen = !toolsOpen },
                    )
                }
            }
        }
    }
}

// MARK: - Photos

@Composable
fun NovaAttachmentPhotoGridSheet(onAdd: (Bitmap) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var assets by remember { mutableStateOf<List<NovaPhotoAsset>>(emptyList()) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var denied by remember { mutableStateOf(false) }
    val permissions = remember { galleryPermissions() }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        denied = !grants.values.any { it }
        if (!denied) {
            scope.launch {
                assets = withContext(Dispatchers.IO) { loadNovaPhotoAssets(context) }
                loading = false
            }
        } else {
            loading = false
        }
    }
    val nativePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let { context.decodeBitmap(it)?.let(onAdd) } }

    LaunchedEffect(Unit) {
        if (hasGalleryPermission(context)) {
            assets = withContext(Dispatchers.IO) { loadNovaPhotoAssets(context) }
            loading = false
        } else {
            permissionLauncher.launch(permissions)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MomentsGlassButtonTint.canvas(isSystemInDarkTheme())),
    ) {
        when {
            loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = NovaColors.primary)
            }
            denied -> NovaAttachmentPermissionPrompt(R.string.nova_attach_photos_permission)
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize().padding(bottom = 88.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(assets, key = { it.id }) { asset ->
                    NovaAttachmentPhotoCell(
                        asset = asset,
                        selected = selectedId == asset.id,
                        onTap = { selectedId = asset.id },
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            NovaStoryRoundButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.nova_attach_back_accessibility),
                onClick = {
                    selectedId = null
                    onBack()
                },
            )
            Spacer(Modifier.weight(1f))
            if (selectedId == null) {
                NovaStoryPillButton(
                    titleRes = R.string.nova_attach_all_photos,
                    tint = null,
                    onClick = {
                        nativePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                )
            } else {
                NovaStoryPillButton(
                    titleRes = R.string.nova_attach_add_to_nova,
                    tint = Color(0xFF007AFF),
                    onClick = {
                        assets.firstOrNull { it.id == selectedId }
                            ?.let { context.decodeBitmap(it.uri)?.let(onAdd) }
                    },
                )
            }
        }
    }
}

// MARK: - Shared cells / prompts / chrome

@Composable
private fun NovaAttachmentPhotoCell(
    asset: NovaPhotoAsset,
    selected: Boolean,
    onTap: () -> Unit,
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(onClick = onTap),
    ) {
        var loaded by remember(asset.id) { mutableStateOf(false) }
        AsyncImage(
            model = asset.uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            onSuccess = { loaded = true },
        )
        if (!loaded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NovaColors.materialBackground),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = NovaColors.primary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF007AFF)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.nova_attach_selection_count, 1),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun NovaAttachmentPermissionPrompt(messageRes: Int) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AttachmentIconView(
            icon = AttachmentIcon.PHOTOS,
            preset = AttachmentIconPreset.PERMISSION_PROMPT_MEDIUM,
            tintColor = NovaColors.textSecondary,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(messageRes),
            color = NovaColors.textSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun NovaStoryRoundButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 42.dp,
) {
    val isDark = isSystemInDarkTheme()
    val stroke = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)
    Box(
        modifier = Modifier
            .size(size)
            .shadow(4.dp, CircleShape, ambientColor = Color.Black.copy(alpha = if (isDark) 0.1f else 0.08f))
            .momentsChromeGlass(CircleShape, interactive = true)
            .border(1.dp, stroke, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = StoryEditorChromeColor.icon(isDark),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun NovaStoryPillButton(
    titleRes: Int,
    tint: Color?,
    onClick: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val stroke = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)
    val shape = RoundedCornerShape(50)
    Text(
        text = stringResource(titleRes),
        color = if (tint == null) StoryEditorChromeColor.icon(isDark) else Color.White,
        fontSize = 14.sp,
        fontWeight = if (tint == null) FontWeight.Medium else FontWeight.SemiBold,
        modifier = Modifier
            .momentsChromeGlass(
                shape = shape,
                interactive = true,
                tint = tint?.copy(alpha = 0.92f),
            )
            .border(1.dp, stroke, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

// MARK: - Helpers

private data class NovaPhotoAsset(val id: String, val uri: Uri)

private fun Context.decodeBitmap(uri: Uri): Bitmap? =
    runCatching { contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream) }.getOrNull()

private fun galleryPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

private fun hasGalleryPermission(context: Context): Boolean =
    galleryPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

private fun loadNovaPhotoAssets(context: Context): List<NovaPhotoAsset> = buildList {
    context.contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        arrayOf(MediaStore.Images.Media._ID),
        null,
        null,
        "${MediaStore.Images.Media.DATE_ADDED} DESC",
    )?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        while (cursor.moveToNext() && size < 300) {
            val id = cursor.getLong(idColumn)
            add(
                NovaPhotoAsset(
                    id.toString(),
                    ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id),
                ),
            )
        }
    }
}

private fun Int.nextFlash(): Int = when (this) {
    ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
    ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
    else -> ImageCapture.FLASH_MODE_OFF
}

private fun Int.icon(): ImageVector = when (this) {
    ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
    ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
    else -> Icons.Default.FlashOff
}

private fun Float.formatZoom(): String =
    if (this % 1f == 0f) toInt().toString() else "%.1f".format(this)
