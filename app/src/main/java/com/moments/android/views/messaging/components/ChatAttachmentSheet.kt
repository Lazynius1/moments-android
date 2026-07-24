package com.moments.android.views.messaging.components

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.utilities.HapticManager
import com.moments.android.views.messaging.attachments.ChatGiphyPickerContent
import com.moments.android.views.messaging.attachments.ChatGiphyPickerKind
import com.moments.android.views.messaging.attachments.ChatLocationSheetContent
import com.moments.android.views.messaging.models.ChatGiphyAsset
import com.moments.android.views.messaging.models.ChatRecentStickersStore
import com.moments.android.views.messaging.models.ChatStickerAsset
import com.moments.android.views.messaging.models.LiveLocationDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Port de `Views/Messaging/Components/ChatAttachmentSheet.swift`. */
object ChatInputBarLayout {
    val bottomPaddingWithoutKeyboard = 8.dp
    val sheetAboveInputGap = 12.dp
    const val maxMediaSelectionCount = 10

    fun attachmentSheetBottomInset(navigationBarBottom: Dp): Dp =
        navigationBarBottom + sheetAboveInputGap
}

enum class ChatAttachmentSheetKind {
    MENU,
    PHOTOS,
    GIF,
    STICKER,
    LOCATION;

    val isPickerSheet: Boolean
        get() = this == GIF || this == STICKER || this == LOCATION
}

object ChatAttachmentSheetMetrics {
    val horizontalInset = 10.dp
    val cornerRadius = 24.dp
    val menuPopoverGap = 16.dp
    const val heightFraction = 0.58f
    val searchFieldHorizontalInset = 28.dp
    val searchFieldCornerRadius = 16.dp
    val searchOverlayTopPadding = 8.dp
    val searchOverlayHeight = 60.dp
}

private fun chatDeviceSheetCornerRadius(screenWidth: Dp): Dp = when {
    screenWidth >= 430.dp -> 62.dp
    screenWidth >= 428.dp -> 53.33.dp
    screenWidth >= 402.dp -> 62.dp
    screenWidth >= 393.dp -> 55.dp
    screenWidth >= 390.dp -> 47.33.dp
    screenWidth >= 375.dp -> 39.dp
    else -> ChatAttachmentSheetMetrics.cornerRadius
}

private fun attachmentGalleryPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

private fun hasAttachmentGalleryPermission(context: Context): Boolean =
    attachmentGalleryPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

data class ChatAttachmentMediaAsset(
    val id: String,
    val uri: Uri,
    val isVideo: Boolean,
    val durationMillis: Long,
)

/** Android counterpart to `ChatAttachmentScrollUnderSearchLayout`. */
@Composable
fun ChatAttachmentScrollUnderSearchLayout(
    search: @Composable () -> Unit,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Box(Modifier.padding(top = ChatAttachmentSheetMetrics.searchOverlayHeight)) { content() }
        Box(
            Modifier
                .padding(top = ChatAttachmentSheetMetrics.searchOverlayTopPadding)
                .zIndex(1f),
        ) { search() }
    }
}

/** Native Android sheets for GIFs, stickers and locations. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatAttachmentPickerSheet(
    kind: ChatAttachmentSheetKind,
    accentColor: androidx.compose.ui.graphics.Color,
    onDismiss: () -> Unit,
    onSelectGif: (ChatGiphyAsset) -> Unit,
    onSelectSticker: (ChatStickerAsset) -> Unit,
    onSendStaticLocation: (latitude: Double, longitude: Double, name: String?, address: String?) -> Unit,
    onStartLive: (LiveLocationDuration) -> Unit,
) {
    if (!kind.isPickerSheet) return
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val recents = remember(kind, context) {
        if (kind == ChatAttachmentSheetKind.STICKER) ChatRecentStickersStore.load(context) else emptyList()
    }
    val isDark = isSystemInDarkTheme()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = if (isDark) androidx.compose.ui.graphics.Color(0xFF0B1215) else androidx.compose.ui.graphics.Color(0xFFFAF9F6),
    ) {
        Box(Modifier.fillMaxWidth().height(560.dp)) {
            when (kind) {
                ChatAttachmentSheetKind.GIF -> ChatGiphyPickerContent(
                    kind = ChatGiphyPickerKind.GIF,
                    accentColor = accentColor,
                    onSelect = { gif ->
                        ChatGiphyAsset.from(gif)?.let(onSelectGif)
                        onDismiss()
                    },
                )
                ChatAttachmentSheetKind.STICKER -> ChatGiphyPickerContent(
                    kind = ChatGiphyPickerKind.STICKER,
                    accentColor = accentColor,
                    onSelect = { gif ->
                        ChatStickerAsset.from(gif)?.let(onSelectSticker)
                        onDismiss()
                    },
                    recents = recents,
                    onSelectRecent = { sticker ->
                        onSelectSticker(sticker)
                        onDismiss()
                    },
                )
                ChatAttachmentSheetKind.LOCATION -> ChatLocationSheetContent(
                    accentColor = accentColor,
                    onSendStatic = { latitude, longitude, name, address ->
                        onSendStaticLocation(latitude, longitude, name, address)
                        onDismiss()
                    },
                    onStartLive = { duration ->
                        onStartLive(duration)
                        onDismiss()
                    },
                )
                else -> Unit
            }
        }
    }
}

/** The same 44pt glass + control, including its bounds for the anchored popover. */
@Composable
fun ChatAttachmentPlusButton(
    isMenuOpen: Boolean,
    onClick: () -> Unit,
    onAnchorBoundsChanged: (IntRect) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val rotation by animateFloatAsState(
        targetValue = if (isMenuOpen) 45f else 0f,
        animationSpec = tween(180),
        label = "attachmentPlusRotation",
    )
    Box(
        modifier = modifier
            .size(44.dp)
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInWindow()
                onAnchorBoundsChanged(
                    IntRect(
                        left = bounds.left.roundToInt(),
                        top = bounds.top.roundToInt(),
                        right = bounds.right.roundToInt(),
                        bottom = bounds.bottom.roundToInt(),
                    ),
                )
            }
            .clip(CircleShape)
            .momentsChromeGlass(CircleShape, interactive = true)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = stringResource(R.string.chat_attachment_accessibility),
            tint = if (isDark) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Black,
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer { rotationZ = rotation },
        )
    }
}

/** Anchored Android equivalent to the Swift popover menu. */
@Composable
fun ChatAttachmentMenuPopover(
    isPresented: ChatAttachmentSheetKind?,
    anchorBounds: IntRect?,
    canSendBuzz: Boolean,
    onDismiss: () -> Unit,
    onOpenCamera: () -> Unit,
    onSendBuzz: () -> Unit,
    onSheetSelected: (ChatAttachmentSheetKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isPresented != ChatAttachmentSheetKind.MENU) return
    val isDark = isSystemInDarkTheme()
    var cardWidth by remember { mutableFloatStateOf(168f) }
    var cardHeight by remember { mutableFloatStateOf(if (canSendBuzz) 372f else 316f) }
    val screenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp
    val density = androidx.compose.ui.platform.LocalDensity.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = if (isDark) 0.12f else 0.08f))
            .clickable(onClick = onDismiss),
    ) {
        if (anchorBounds != null) {
            val widthPx = with(density) { screenWidth.toPx() }
            val x = min(max(anchorBounds.left.toFloat(), 16f), max(0f, widthPx - 16f - cardWidth))
            val y = max(0f, anchorBounds.top - 16f - cardHeight)
            ChatAttachmentMenuPopoverCard(
                canSendBuzz = canSendBuzz,
                onOpenCamera = {
                    onDismiss()
                    onOpenCamera()
                },
                onSendBuzz = {
                    onDismiss()
                    onSendBuzz()
                },
                onSheetSelected = {
                    onSheetSelected(it)
                },
                modifier = Modifier
                    .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                    .onGloballyPositioned { coordinates ->
                        cardWidth = coordinates.size.width.toFloat()
                        cardHeight = coordinates.size.height.toFloat()
                    }
                    .clickable(enabled = false) {},
            )
        }
    }
}

@Composable
private fun ChatAttachmentMenuPopoverCard(
    canSendBuzz: Boolean,
    onOpenCamera: () -> Unit,
    onSendBuzz: () -> Unit,
    onSheetSelected: (ChatAttachmentSheetKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val textColor = if (isDark) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Black
    val circleFill = if (isDark) androidx.compose.ui.graphics.Color.White.copy(alpha = .10f) else androidx.compose.ui.graphics.Color.Black.copy(alpha = .06f)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(ChatAttachmentSheetMetrics.cornerRadius))
            .momentsChromeGlass(RoundedCornerShape(ChatAttachmentSheetMetrics.cornerRadius), interactive = true)
            .padding(vertical = 10.dp, horizontal = 12.dp),
    ) {
        ChatAttachmentMenuRow(AttachmentIcon.CAMERA, R.string.chat_attachment_camera, textColor, circleFill, onOpenCamera)
        ChatAttachmentMenuRow(AttachmentIcon.PHOTOS, R.string.chat_attachment_photos, textColor, circleFill, onClick = { onSheetSelected(ChatAttachmentSheetKind.PHOTOS) })
        if (canSendBuzz) {
            ChatAttachmentMenuRow(AttachmentIcon.BUZZ, R.string.chat_attachment_buzz, textColor, circleFill, onSendBuzz)
        }
        ChatAttachmentMenuRow(AttachmentIcon.GIF, R.string.chat_attachment_gif, textColor, circleFill, onClick = { onSheetSelected(ChatAttachmentSheetKind.GIF) })
        ChatAttachmentMenuRow(
            icon = null,
            customIconRes = R.drawable.moments_sticker_tool,
            titleRes = R.string.chat_attachment_sticker,
            textColor = textColor,
            circleFill = circleFill,
            onClick = { onSheetSelected(ChatAttachmentSheetKind.STICKER) },
        )
        ChatAttachmentMenuRow(AttachmentIcon.LOCATION, R.string.chat_attachment_location, textColor, circleFill, onClick = { onSheetSelected(ChatAttachmentSheetKind.LOCATION) })
    }
}

@Composable
private fun ChatAttachmentMenuRow(
    icon: AttachmentIcon?,
    titleRes: Int,
    textColor: androidx.compose.ui.graphics.Color,
    circleFill: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    customIconRes: Int? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(circleFill),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                AttachmentIconView(icon, AttachmentIconPreset.ATTACHMENT_MENU, textColor)
            } else if (customIconRes != null) {
                androidx.compose.foundation.Image(
                    painter = painterResource(customIconRes),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(textColor),
                    modifier = Modifier.size(AttachmentIconMetrics.attachmentMenu),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(stringResource(titleRes), color = textColor, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(20.dp))
    }
}

/** Overlay version of the iOS media sheet; the callbacks retain the two source pathways. */
@Composable
fun ChatAttachmentMediaSheetOverlay(
    activeSheet: ChatAttachmentSheetKind?,
    accentColor: androidx.compose.ui.graphics.Color,
    onPickerUris: (List<Uri>) -> Unit,
    onConfirmAssets: (List<ChatAttachmentMediaAsset>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    val isDark = isSystemInDarkTheme()
    AnimatedVisibility(visible = activeSheet == ChatAttachmentSheetKind.PHOTOS, modifier = modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val sheetHeight = maxHeight * ChatAttachmentSheetMetrics.heightFraction
            val sheetHeightPx = with(density) { sheetHeight.toPx() }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = if (isDark) .28f else .16f))
                    .clickable(onClick = onDismiss),
            )
            ChatAttachmentSheetSurface(
                height = sheetHeight,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = ChatAttachmentSheetMetrics.horizontalInset)
                    .padding(bottom = ChatInputBarLayout.sheetAboveInputGap)
                    .offset { IntOffset(0, dragOffsetPx.roundToInt()) }
                    .pointerInput(sheetHeightPx) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { _, delta -> dragOffsetPx = max(0f, dragOffsetPx + delta) },
                            onDragEnd = {
                                if (dragOffsetPx > sheetHeightPx * .20f) onDismiss() else dragOffsetPx = 0f
                            },
                        )
                    },
            ) {
                ChatAttachmentMediaGridSheet(
                    accentColor = accentColor,
                    onPickerUris = onPickerUris,
                    onConfirmAssets = onConfirmAssets,
                    onBack = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun ChatAttachmentSheetSurface(
    height: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(
        chatDeviceSheetCornerRadius(androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp),
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .shadow(24.dp, shape, ambientColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = .18f), spotColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = .18f))
            .clip(shape)
            .background(if (isSystemInDarkTheme()) androidx.compose.ui.graphics.Color(0xFF0B1215) else androidx.compose.ui.graphics.Color(0xFFFAF9F6)),
    ) { content() }
}

/** MediaStore equivalent of the Photos/PHAsset multi-selection grid. */
@Composable
private fun ChatAttachmentMediaGridSheet(
    accentColor: androidx.compose.ui.graphics.Color,
    onPickerUris: (List<Uri>) -> Unit,
    onConfirmAssets: (List<ChatAttachmentMediaAsset>) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = isSystemInDarkTheme()
    var assets by remember { mutableStateOf<List<ChatAttachmentMediaAsset>>(emptyList()) }
    var selectedIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isConfirming by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        permissionDenied = !grants.values.any { it }
        if (!permissionDenied) {
            scope.launch {
                isLoading = true
                assets = withContext(Dispatchers.IO) { loadAttachmentGalleryAssets(context) }
                isLoading = false
            }
        } else {
            isLoading = false
        }
    }
    val nativePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(ChatInputBarLayout.maxMediaSelectionCount),
    ) { uris ->
        if (uris.isNotEmpty()) onPickerUris(uris.take(ChatInputBarLayout.maxMediaSelectionCount))
    }

    LaunchedEffect(Unit) {
        if (hasAttachmentGalleryPermission(context)) {
            assets = withContext(Dispatchers.IO) { loadAttachmentGalleryAssets(context) }
            isLoading = false
        } else {
            permissionLauncher.launch(attachmentGalleryPermissions())
        }
    }

    Box(Modifier.fillMaxSize()) {
        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = accentColor)
            }
            permissionDenied -> ChatAttachmentPermissionPrompt(R.string.chat_attachment_photos_permission)
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(assets, key = { it.id }) { asset ->
                    ChatAttachmentMediaCell(
                        asset = asset,
                        selectionIndex = selectedIds.indexOf(asset.id).takeIf { it >= 0 }?.plus(1),
                        onClick = {
                            selectedIds = if (asset.id in selectedIds) {
                                selectedIds - asset.id
                            } else if (selectedIds.size < ChatInputBarLayout.maxMediaSelectionCount) {
                                selectedIds + asset.id
                            } else {
                                HapticManager.shared.lightImpact()
                                selectedIds
                            }
                        },
                    )
                }
            }
        }

        ChatAttachmentFooter(
            selectedCount = selectedIds.size,
            accentColor = accentColor,
            isConfirming = isConfirming,
            onBack = {
                selectedIds = emptyList()
                onBack()
            },
            onAllPhotos = {
                nativePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
            },
            onConfirm = {
                if (selectedIds.isEmpty() || isConfirming) return@ChatAttachmentFooter
                isConfirming = true
                onConfirmAssets(selectedIds.mapNotNull { id -> assets.firstOrNull { it.id == id } })
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun ChatAttachmentMediaCell(
    asset: ChatAttachmentMediaAsset,
    selectionIndex: Int?,
    onClick: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = asset.uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (selectionIndex != null) {
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = if (isDark) .42f else .28f)))
        }
        if (asset.isVideo) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = .55f))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(10.dp))
                Spacer(Modifier.width(4.dp))
                Text(formatAttachmentDuration(asset.durationMillis), color = androidx.compose.ui.graphics.Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }
        if (selectionIndex != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(androidx.compose.ui.graphics.Color(0xFF007AFF)),
                contentAlignment = Alignment.Center,
            ) {
                Text(selectionIndex.toString(), color = androidx.compose.ui.graphics.Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ChatAttachmentFooter(
    selectedCount: Int,
    accentColor: androidx.compose.ui.graphics.Color,
    isConfirming: Boolean,
    onBack: () -> Unit,
    onAllPhotos: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChatAttachmentRoundButton(onClick = onBack)
        Spacer(Modifier.weight(1f))
        if (selectedCount == 0) {
            ChatAttachmentPillButton(R.string.chat_attachment_all_photos, null, false, onAllPhotos)
        } else {
            ChatAttachmentPillButton(
                titleRes = if (selectedCount == 1) R.string.chat_attachment_send_one else R.string.chat_attachment_send_many,
                tint = accentColor,
                disabled = isConfirming,
                onClick = onConfirm,
                formatArg = selectedCount,
            )
        }
    }
}

@Composable
private fun ChatAttachmentRoundButton(onClick: () -> Unit) {
    val isDark = isSystemInDarkTheme()
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .momentsChromeGlass(CircleShape, interactive = true)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = stringResource(R.string.chat_attachment_back_accessibility),
            tint = if (isDark) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Black,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun ChatAttachmentPillButton(
    titleRes: Int,
    tint: androidx.compose.ui.graphics.Color?,
    disabled: Boolean,
    onClick: () -> Unit,
    formatArg: Int? = null,
) {
    val isDark = isSystemInDarkTheme()
    val label = if (formatArg == null) stringResource(titleRes) else stringResource(titleRes, formatArg)
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .clip(shape)
            .momentsChromeGlass(shape, interactive = !disabled, tint = tint?.copy(alpha = if (disabled) .35f else .92f))
            .clickable(enabled = !disabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = tint?.let { androidx.compose.ui.graphics.Color.White } ?: if (isDark) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Black,
            fontSize = 14.sp,
            fontWeight = if (tint == null) FontWeight.Medium else FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ChatAttachmentPermissionPrompt(messageRes: Int) {
    val isDark = isSystemInDarkTheme()
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            stringResource(messageRes),
            color = if (isDark) androidx.compose.ui.graphics.Color.White.copy(alpha = .7f) else androidx.compose.ui.graphics.Color.Black.copy(alpha = .6f),
            fontSize = 15.sp,
        )
    }
}

@Composable
fun ChatAttachmentSearchField(
    placeholderRes: Int,
    text: String,
    onTextChange: (String) -> Unit,
    onClear: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val primaryText = if (isDark) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Black
    val secondaryText = if (isDark) androidx.compose.ui.graphics.Color.White.copy(alpha = .6f) else androidx.compose.ui.graphics.Color.Black.copy(alpha = .5f)
    val shape = RoundedCornerShape(ChatAttachmentSheetMetrics.searchFieldCornerRadius)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ChatAttachmentSheetMetrics.searchFieldHorizontalInset)
            .padding(bottom = 10.dp)
            .clip(shape)
            .momentsChromeGlass(shape, interactive = true)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Search, null, tint = secondaryText)
        Spacer(Modifier.width(8.dp))
        androidx.compose.foundation.text.BasicTextField(
            value = text,
            onValueChange = onTextChange,
            textStyle = TextStyle(color = primaryText, fontSize = 16.sp),
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (text.isEmpty()) Text(stringResource(placeholderRes), color = secondaryText, fontSize = 16.sp)
                inner()
            },
        )
        if (text.isNotEmpty()) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.chat_attachment_clear_accessibility),
                tint = secondaryText.copy(alpha = .85f),
                modifier = Modifier.clickable {
                    onTextChange("")
                    onClear?.invoke()
                },
            )
        }
    }
}

private fun loadAttachmentGalleryAssets(context: Context): List<ChatAttachmentMediaAsset> {
    val assets = mutableListOf<ChatAttachmentMediaAsset>()
    val projection = arrayOf(
        MediaStore.Files.FileColumns._ID,
        MediaStore.Files.FileColumns.MEDIA_TYPE,
        MediaStore.Files.FileColumns.DURATION,
    )
    val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=?"
    val selectionArgs = arrayOf(
        MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
        MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
    )
    context.contentResolver.query(
        MediaStore.Files.getContentUri("external"),
        projection,
        selection,
        selectionArgs,
        "${MediaStore.Files.FileColumns.DATE_ADDED} DESC",
    )?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
        val typeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
        val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DURATION)
        var count = 0
        while (cursor.moveToNext() && count < 300) {
            val id = cursor.getLong(idColumn)
            val isVideo = cursor.getInt(typeColumn) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
            val uri = if (isVideo) {
                ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
            } else {
                ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            }
            assets += ChatAttachmentMediaAsset(
                id = id.toString(),
                uri = uri,
                isVideo = isVideo,
                durationMillis = cursor.getLong(durationColumn),
            )
            count++
        }
    }
    return assets
}

private fun formatAttachmentDuration(durationMillis: Long): String {
    val totalSeconds = (durationMillis / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}
