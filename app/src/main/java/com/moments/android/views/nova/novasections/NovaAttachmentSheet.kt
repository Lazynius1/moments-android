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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Photo
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.views.creator.creatoruikit.CameraPreviewView
import com.moments.android.views.nova.novacore.NovaColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

enum class NovaAttachmentSheetKind { MENU, CAMERA, PHOTOS }

/** Popover anchored above Nova's + button. */
@Composable
fun NovaAttachmentMenuPopover(activeSheet: NovaAttachmentSheetKind?, onSheetChange: (NovaAttachmentSheetKind?) -> Unit, modifier: Modifier = Modifier) {
    if (activeSheet != NovaAttachmentSheetKind.MENU) return
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .12f)).clickable { onSheetChange(null) }) {
        Column(
            modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 72.dp).widthIn(min = 168.dp).clip(RoundedCornerShape(24.dp)).background(NovaColors.materialBackground).clickable(enabled = false) {}.padding(vertical = 10.dp, horizontal = 12.dp),
        ) {
            NovaAttachmentMenuRow(Icons.Default.CameraAlt, R.string.nova_attach_camera) { onSheetChange(NovaAttachmentSheetKind.CAMERA) }
            NovaAttachmentMenuRow(Icons.Default.Photo, R.string.nova_attach_photos) { onSheetChange(NovaAttachmentSheetKind.PHOTOS) }
        }
    }
}

@Composable private fun NovaAttachmentMenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, titleRes: Int, action: () -> Unit) = Row(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = action).padding(horizontal = 6.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
) { Icon(icon, null, tint = NovaColors.textPrimary, modifier = Modifier.size(40.dp).clip(CircleShape).background(NovaColors.secondaryBackground).padding(10.dp)); Spacer(Modifier.width(14.dp)); Text(stringResource(titleRes), color = NovaColors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Medium) }

/** Medium-height camera / photo overlay. It mirrors iOS's drag dismissal and menu back transition. */
@Composable
fun NovaAttachmentSheetOverlay(activeSheet: NovaAttachmentSheetKind?, onSheetChange: (NovaAttachmentSheetKind?) -> Unit, onCaptured: (Bitmap) -> Unit, onAdd: (Bitmap) -> Unit) {
    val kind = activeSheet?.takeIf { it != NovaAttachmentSheetKind.MENU } ?: return
    var dragOffset by remember(kind) { mutableFloatStateOf(0f) }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .24f)).clickable { onSheetChange(null) }) {
        NovaAttachmentSheetSurface(
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 10.dp, vertical = 8.dp).offset { IntOffset(0, dragOffset.roundToInt()) }.pointerInput(kind) {
                detectVerticalDragGestures(
                    onDragStart = { dragOffset = 0f },
                    onDragEnd = { if (dragOffset > 120f) onSheetChange(null) else dragOffset = 0f },
                    onDragCancel = { dragOffset = 0f },
                    onVerticalDrag = { _, delta -> dragOffset = max(0f, dragOffset + delta) },
                )
            },
        ) {
            when (kind) {
                NovaAttachmentSheetKind.CAMERA -> NovaAttachmentCameraSheet(onCaptured) { onSheetChange(NovaAttachmentSheetKind.MENU) }
                NovaAttachmentSheetKind.PHOTOS -> NovaAttachmentPhotoGridSheet(onAdd) { onSheetChange(NovaAttachmentSheetKind.MENU) }
                NovaAttachmentSheetKind.MENU -> Unit
            }
        }
    }
}

@Composable private fun NovaAttachmentSheetSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) = BoxWithConstraints(
    modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(NovaColors.background),
) { Box(Modifier.fillMaxWidth().height(maxHeight * .58f)) { content() } }

@Composable
fun NovaAttachmentCameraSheet(onCaptured: (Bitmap) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var permitted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> permitted = granted }
    LaunchedEffect(Unit) { if (!permitted) permissionLauncher.launch(Manifest.permission.CAMERA) }
    if (!permitted) { NovaAttachmentPermissionPrompt(R.string.nova_attach_camera_permission); return }
    var flash by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var position by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var captureToken by remember { mutableIntStateOf(0) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var toolsOpen by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        CameraPreviewView(position, flash, false, zoom, captureToken, false, true, onRecordingStateChange = {}, onImageCaptured = { uri -> context.decodeBitmap(uri)?.let(onCaptured) }, onVideoCaptured = {}, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTransformGestures { _, _, gestureZoom, _ -> zoom = (zoom * gestureZoom).coerceIn(1f, 5f) } })
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 32.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (position == CameraSelector.LENS_FACING_BACK) Text(stringResource(R.string.nova_attach_zoom, zoom.formatZoom()), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 10.dp).clip(CircleShape).background(Color.Black.copy(alpha = .45f)).padding(horizontal = 10.dp, vertical = 5.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                NovaAttachmentRoundButton(Icons.AutoMirrored.Filled.ArrowBack, R.string.nova_attach_back_accessibility, onBack)
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(72.dp).clip(CircleShape).background(Color.White).clickable { captureToken++ }, contentAlignment = Alignment.Center) { Box(Modifier.size(62.dp).clip(CircleShape).background(Color.White)) }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (toolsOpen) { if (position == CameraSelector.LENS_FACING_BACK) NovaAttachmentRoundButton(flash.icon(), R.string.nova_attach_flash_accessibility) { flash = flash.nextFlash() }; Spacer(Modifier.height(12.dp)); NovaAttachmentRoundButton(Icons.Default.SwitchCamera, R.string.nova_attach_flip_accessibility) { position = if (position == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK; if (position == CameraSelector.LENS_FACING_FRONT) { flash = ImageCapture.FLASH_MODE_OFF; zoom = 1f } }; Spacer(Modifier.height(12.dp)) }
                    NovaAttachmentRoundButton(if (toolsOpen) Icons.Default.Close else Icons.Default.MoreVert, if (toolsOpen) R.string.common_close else R.string.nova_attach_more_accessibility) { toolsOpen = !toolsOpen }
                }
            }
        }
    }
}

@Composable
fun NovaAttachmentPhotoGridSheet(onAdd: (Bitmap) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var assets by remember { mutableStateOf<List<NovaPhotoAsset>>(emptyList()) }; var selectedId by remember { mutableStateOf<String?>(null) }; var loading by remember { mutableStateOf(true) }; var denied by remember { mutableStateOf(false) }
    val permissions = remember { galleryPermissions() }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants -> denied = !grants.values.any { it }; if (!denied) scope.launch { assets = withContext(Dispatchers.IO) { loadNovaPhotoAssets(context) }; loading = false } else loading = false }
    val nativePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { context.decodeBitmap(it)?.let(onAdd) } }
    LaunchedEffect(Unit) { if (hasGalleryPermission(context)) { assets = withContext(Dispatchers.IO) { loadNovaPhotoAssets(context) }; loading = false } else permissionLauncher.launch(permissions) }
    Box(Modifier.fillMaxSize()) {
        when { loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = NovaColors.primary) }; denied -> NovaAttachmentPermissionPrompt(R.string.nova_attach_photos_permission); else -> LazyVerticalGrid(GridCells.Fixed(3), modifier = Modifier.fillMaxSize().padding(bottom = 88.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) { items(assets, key = { it.id }) { asset -> NovaAttachmentPhotoCell(asset, selectedId == asset.id) { selectedId = asset.id } } } }
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { NovaAttachmentRoundButton(Icons.AutoMirrored.Filled.ArrowBack, R.string.nova_attach_back_accessibility) { selectedId = null; onBack() }; Spacer(Modifier.weight(1f)); NovaAttachmentPillButton(if (selectedId == null) R.string.nova_attach_all_photos else R.string.nova_attach_add_to_nova, selectedId != null) { if (selectedId == null) nativePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) else assets.firstOrNull { it.id == selectedId }?.let { context.decodeBitmap(it.uri)?.let(onAdd) } } }
    }
}

@Composable private fun NovaAttachmentPhotoCell(asset: NovaPhotoAsset, selected: Boolean, onTap: () -> Unit) = Box(Modifier.aspectRatio(1f).clickable(onClick = onTap)) { AsyncImage(asset.uri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop); if (selected) Box(Modifier.align(Alignment.BottomEnd).padding(8.dp).size(24.dp).clip(CircleShape).background(Color(0xFF007AFF)), Alignment.Center) { Text(stringResource(R.string.nova_attach_selection_count, 1), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) } }
@Composable private fun NovaAttachmentPermissionPrompt(messageRes: Int) = Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) { Text(stringResource(messageRes), color = NovaColors.textSecondary, fontSize = 14.sp) }
@Composable private fun NovaAttachmentRoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, labelRes: Int, onClick: () -> Unit) = Box(Modifier.size(42.dp).clip(CircleShape).background(NovaColors.materialBackground).clickable(onClick = onClick), Alignment.Center) { Icon(icon, stringResource(labelRes), tint = NovaColors.textPrimary, modifier = Modifier.size(18.dp)) }
@Composable private fun NovaAttachmentPillButton(titleRes: Int, tinted: Boolean, onClick: () -> Unit) = Box(Modifier.clip(CircleShape).background(if (tinted) Color(0xFF007AFF) else NovaColors.materialBackground).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp)) { Text(stringResource(titleRes), color = if (tinted) Color.White else NovaColors.textPrimary, fontSize = 14.sp, fontWeight = if (tinted) FontWeight.SemiBold else FontWeight.Medium) }

private data class NovaPhotoAsset(val id: String, val uri: Uri)
private fun Context.decodeBitmap(uri: Uri): Bitmap? = runCatching { contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream) }.getOrNull()
private fun galleryPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_IMAGES) else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
private fun hasGalleryPermission(context: Context) = galleryPermissions().all { ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED }
private fun loadNovaPhotoAssets(context: Context): List<NovaPhotoAsset> = buildList { context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media._ID), null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { cursor -> val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID); while (cursor.moveToNext() && size < 300) { val id = cursor.getLong(idColumn); add(NovaPhotoAsset(id.toString(), ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id))) } } }
private fun Int.nextFlash() = when (this) { ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON; ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO; else -> ImageCapture.FLASH_MODE_OFF }
private fun Int.icon() = when (this) { ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn; ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto; else -> Icons.Default.FlashOff }
private fun Float.formatZoom() = if (this % 1f == 0f) toInt().toString() else "%.1f".format(this)
