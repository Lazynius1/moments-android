package com.moments.android.views.profile.editor

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/** Port de `PhotoCropEditorView.swift`; `Uri` sustituye a `PHAsset`. */
data class ProfilePhotoAsset(val uri: Uri, val album: String)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoCropEditorView(originalUri: Uri, onSave: (Bitmap) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(true) }
    var processing by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var assets by remember { mutableStateOf<List<ProfilePhotoAsset>>(emptyList()) }
    var albums by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedAlbum by remember { mutableStateOf<String?>(null) }
    var albumMenu by remember { mutableStateOf(false) }
    LaunchedEffect(originalUri) { bitmap = loadBitmap(context.contentResolver, originalUri); loading = false }
    LaunchedEffect(selectedAlbum) { assets = loadPhotoAssets(context.contentResolver, selectedAlbum); albums = listOfNotNull(null) + assets.map { it.album }.distinct() }
    val primary = cropPrimary(); val background = cropBackground()
    Box(modifier.fillMaxSize().background(background)) {
        when {
            loading -> CropLoading(R.string.profile_crop_loading, primary)
            bitmap != null -> Column(Modifier.fillMaxSize()) {
                CropHeader(processing, onDismiss) { processing = true; scope.launch { val output = withContext(Dispatchers.Default) { cropBitmap(bitmap!!, scale, offset) }; processing = false; onSave(output); onDismiss() } }
                LazyVerticalGrid(GridCells.Fixed(1), Modifier.weight(1f)) {
                    item { CropCanvas(bitmap!!, scale, offset, { scale = it }, { offset = it }, processing) }
                    item { Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) { Box { Row(Modifier.clip(CircleShape).background(primary.copy(.08f)).clickable { albumMenu = true }.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) { Text(selectedAlbum ?: stringResource(R.string.profile_crop_recent), color = primary, fontSize = 16.sp); Icon(Icons.Filled.KeyboardArrowRight, null, tint = primary, modifier = Modifier.size(18.dp)) }; DropdownMenu(albumMenu, { albumMenu = false }) { albums.forEach { album -> DropdownMenuItem(text = { Text(album) }, onClick = { selectedAlbum = album; albumMenu = false }) } } } } }
                    item { PhotoCropGrid(assets, selectedAlbum) { uri -> loading = true; bitmap = null; scale = 1f; offset = Offset.Zero; scope.launch { bitmap = withContext(Dispatchers.IO) { loadBitmap(context.contentResolver, uri) }; loading = false } } }
                }
            }
        }
        if (processing) CropLoading(R.string.profile_crop_processing, primary, Modifier.background(background.copy(.82f)))
    }
}

@Composable private fun CropHeader(processing: Boolean, onDismiss: () -> Unit, onSave: () -> Unit) = Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Close, stringResource(R.string.common_close), tint = cropPrimary(), modifier = Modifier.size(38.dp).clip(CircleShape).background(cropPrimary().copy(.08f)).padding(10.dp).then(if (!processing) Modifier.clickable { onDismiss() } else Modifier)); Text(stringResource(R.string.profile_crop_move_scale), color = cropPrimary(), fontSize = 17.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center); Icon(Icons.Filled.Check, stringResource(R.string.profile_crop_save), tint = cropPrimary(), modifier = Modifier.size(38.dp).clip(CircleShape).background(cropPrimary().copy(.08f)).padding(10.dp).then(if (!processing) Modifier.clickable { onSave() } else Modifier)) }
@Composable private fun CropLoading(label: Int, tint: Color, modifier: Modifier = Modifier) = Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { CircularProgressIndicator(color = tint); Text(stringResource(label), color = tint, fontSize = 16.sp, modifier = Modifier.padding(top = 20.dp)) }
@Composable private fun CropCanvas(bitmap: Bitmap, scale: Float, offset: Offset, setScale: (Float) -> Unit, setOffset: (Offset) -> Unit, locked: Boolean) = Box(Modifier.fillMaxWidth().height(390.dp).background(Color.Black)) { androidx.compose.foundation.Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }.pointerInput(bitmap, locked) { if (!locked) detectTransformGestures { _, pan, zoom, _ -> val nextScale = (scale * zoom).coerceIn(.5f, 4f); setScale(nextScale); setOffset(limitCropOffset(bitmap, nextScale, Offset(offset.x + pan.x, offset.y + pan.y), 390f)) } }, contentScale = ContentScale.Fit); CropGridOverlay() }
@Composable private fun CropGridOverlay() = Box(Modifier.fillMaxSize()) { repeat(2) { index -> Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(.20f)).align(if (index == 0) Alignment.Center else Alignment.BottomCenter).padding(bottom = if (index == 0) 130.dp else 130.dp)); Box(Modifier.fillMaxSize().width(1.dp).background(Color.White.copy(.20f)).align(if (index == 0) Alignment.CenterStart else Alignment.CenterEnd).padding(start = if (index == 0) 130.dp else 260.dp)) } }
@Composable private fun PhotoCropGrid(assets: List<ProfilePhotoAsset>, album: String?, onSelect: (Uri) -> Unit) = LazyVerticalGrid(GridCells.Fixed(4), Modifier.fillMaxWidth().height(380.dp).padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { itemsIndexed(assets.filter { album == null || it.album == album }) { _, asset -> AsyncImage(asset.uri, null, Modifier.size(92.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp)).clickable { onSelect(asset.uri) }, contentScale = ContentScale.Crop) } }
private fun loadBitmap(resolver: ContentResolver, uri: Uri): Bitmap? = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
private fun loadPhotoAssets(resolver: ContentResolver, album: String?): List<ProfilePhotoAsset> { val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.BUCKET_DISPLAY_NAME); val result = mutableListOf<ProfilePhotoAsset>(); resolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { cursor -> val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID); val albumIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME); while (cursor.moveToNext() && result.size < 200) { val name = cursor.getString(albumIndex) ?: ""; if (album == null || name == album) result += ProfilePhotoAsset(Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getLong(idIndex).toString()), name) } }; return result }
private fun limitCropOffset(bitmap: Bitmap, scale: Float, requested: Offset, side: Float): Offset { val factor = min(side / bitmap.width, side / bitmap.height) * scale; val maxX = max(0f, (bitmap.width * factor - side) / 2); val maxY = max(0f, (bitmap.height * factor - side) / 2); return Offset(requested.x.coerceIn(-maxX, maxX), requested.y.coerceIn(-maxY, maxY)) }
private fun cropBitmap(bitmap: Bitmap, scale: Float, offset: Offset): Bitmap { val output = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888); val canvas = Canvas(output); val paint = Paint(Paint.ANTI_ALIAS_FLAG); canvas.drawColor(AndroidColor.BLACK); val factor = min(400f / bitmap.width, 400f / bitmap.height) * scale; val width = bitmap.width * factor; val height = bitmap.height * factor; canvas.drawBitmap(bitmap, null, android.graphics.RectF((400 - width) / 2 + offset.x * 400 / 390, (400 - height) / 2 + offset.y * 400 / 390, (400 + width) / 2 + offset.x * 400 / 390, (400 + height) / 2 + offset.y * 400 / 390), paint); return output }
@Composable private fun cropBackground() = if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF0B1215) else Color(0xFFFAF9F6)
@Composable private fun cropPrimary() = if (androidx.compose.foundation.isSystemInDarkTheme()) Color.White else Color.Black
