package com.moments.android.views.messaging.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.ImageDecoder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.moments.android.R
import com.moments.android.notifications.services.InAppActionToast
import com.moments.android.notifications.services.InAppNotificationService
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.UUID

/** Shared contract with iOS. Private user data, never part of the conversation document. */
data class ChatWallpaper(
    val kind: String = "default", val colorHex: String = "DCE8E4",
    val storagePath: String = "", val dimming: Double = 0.2,
) {
    fun fields(): Map<String, Any> = mapOf("kind" to kind, "colorHex" to colorHex,
        "storagePath" to storagePath, "dimming" to dimming, "updatedAt" to FieldValue.serverTimestamp())
    companion object {
        fun from(data: Map<String, Any>?) = ChatWallpaper(
            data?.get("kind") as? String ?: "default", data?.get("colorHex") as? String ?: "DCE8E4",
            data?.get("storagePath") as? String ?: "", (data?.get("dimming") as? Number)?.toDouble()?.coerceIn(0.0, 0.75) ?: 0.2,
        )
    }
}

private class ChatWallpaperState(context: Context, val uid: String, val conversationId: String) {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var listener: ListenerRegistration? = null
    private var imageJob: Job? = null
    private var loadedPath = ""
    var wallpaper by mutableStateOf(ChatWallpaper()); private set
    var image by mutableStateOf<Bitmap?>(null); private set
    private val key = "$uid:$conversationId"
    private val prefs = app.getSharedPreferences("chat_wallpapers", Context.MODE_PRIVATE)
    fun start() {
        if (uid.isEmpty() || conversationId.isEmpty()) return
        runCatching {
            val obj = JSONObject(prefs.getString(key, "{}")!!)
            apply(ChatWallpaper.from(obj.keys().asSequence().associateWith { obj.get(it) }))
        }
        listener = FirebaseFirestore.getInstance().collection("users").document(uid)
            .collection("chatWallpapers").document(conversationId).addSnapshotListener { snapshot, _ ->
                if (snapshot != null && (!snapshot.metadata.isFromCache || snapshot.exists())) apply(ChatWallpaper.from(snapshot.data))
            }
    }
    private fun cache(path: String): File = File(app.cacheDir, "ChatWallpapers").apply { mkdirs() }
        .resolve(path.replace('/', '_'))
    private fun apply(value: ChatWallpaper) {
        wallpaper = value
        prefs.edit().putString(key, JSONObject(mapOf("kind" to value.kind, "colorHex" to value.colorHex,
            "storagePath" to value.storagePath, "dimming" to value.dimming)).toString()).apply()
        val path = if (value.kind == "photo") value.storagePath else ""
        if (loadedPath == path) return
        loadedPath = path
        imageJob?.cancel(); image = null
        if (!path.startsWith("users/$uid/chatWallpapers/")) return
        imageJob = scope.launch {
            runCatching {
                val bytes = withContext(Dispatchers.IO) {
                    val file = cache(path)
                    if (file.exists()) file.readBytes() else FirebaseStorage.getInstance().reference.child(path)
                        .getBytes(8L * 1024 * 1024).await().also { file.writeBytes(it) }
                }
                val bitmap = withContext(Dispatchers.Default) { decodeWallpaper(bytes) }
                ensureActive()
                if (loadedPath == path) image = bitmap
            }
        }
    }
    suspend fun save(draft: ChatWallpaper, bytes: ByteArray?) {
        check(uid.isNotEmpty() && conversationId.isNotEmpty())
        val previous = wallpaper.storagePath
        var value = draft
        var uploaded: String? = null
        if (value.kind == "photo" && bytes != null) {
            val path = "users/$uid/chatWallpapers/$conversationId/${UUID.randomUUID()}.jpg"
            FirebaseStorage.getInstance().reference.child(path).putBytes(bytes,
                StorageMetadata.Builder().setContentType("image/jpeg").build()).await()
            value = value.copy(storagePath = path); uploaded = path
        }
        if (value.kind != "photo") value = value.copy(storagePath = "")
        try {
            FirebaseFirestore.getInstance().collection("users").document(uid)
                .collection("chatWallpapers").document(conversationId).set(value.fields()).await()
        } catch (error: Exception) {
            uploaded?.let { runCatching { FirebaseStorage.getInstance().reference.child(it).delete().await() } }
            throw error
        }
        apply(value)
        if (previous.isNotEmpty() && previous != value.storagePath && previous.startsWith("users/$uid/chatWallpapers/")) {
            runCatching { FirebaseStorage.getInstance().reference.child(previous).delete().await() }
        }
    }
    fun close() { listener?.remove(); scope.cancel() }
}

private fun decodeWallpaper(bytes: ByteArray): Bitmap? {
    if (Build.VERSION.SDK_INT >= 28) return ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val scale = minOf(1f, 1920f / maxOf(info.size.width, info.size.height))
        decoder.setTargetSize(maxOf(1, (info.size.width * scale).toInt()), maxOf(1, (info.size.height * scale).toInt()))
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val options = BitmapFactory.Options().apply { inSampleSize = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / 1920) }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}

@Composable
private fun rememberWallpaper(conversationId: String): ChatWallpaperState {
    val context = LocalContext.current
    val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    val state = remember(uid, conversationId) { ChatWallpaperState(context, uid, conversationId) }
    DisposableEffect(state) { state.start(); onDispose { state.close() } }
    return state
}
private fun wallpaperColor(hex: String) = runCatching { Color(android.graphics.Color.parseColor("#$hex")) }.getOrDefault(Color(0xFFDCE8E4))

@Composable
fun ChatWallpaperBackground(conversationId: String, fallback: Color, modifier: Modifier = Modifier) {
    val state = rememberWallpaper(conversationId)
    WallpaperCanvas(state.wallpaper, state.image, fallback, modifier)
}
@Composable
private fun WallpaperCanvas(value: ChatWallpaper, image: Bitmap?, fallback: Color, modifier: Modifier = Modifier, zoom: Float = 1f, offset: Offset = Offset.Zero) {
    BoxWithConstraints(modifier.background(if (value.kind == "color") wallpaperColor(value.colorHex) else fallback).clip(RoundedCornerShape(0.dp)), contentAlignment = Alignment.Center) {
        if (value.kind == "photo" && image != null) {
            val fill = maxOf(maxWidth.value / image.width, maxHeight.value / image.height) * zoom
            Image(image.asImageBitmap(), null,
                Modifier.requiredSize((image.width * fill).dp, (image.height * fill).dp)
                    .graphicsLayer { translationX = offset.x; translationY = offset.y }, contentScale = ContentScale.FillBounds)
        }
        if (value.kind != "default") Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = value.dimming.toFloat())))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationWallpaperView(conversationId: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val state = rememberWallpaper(conversationId)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf(state.wallpaper) }
    var edited by remember { mutableStateOf(false) }
    LaunchedEffect(state.wallpaper) { if (!edited) draft = state.wallpaper }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val previewWidth = with(density) { 198.dp.toPx() }
    val previewHeight = with(density) { 352.dp.toPx() }
    fun clamp(value: Offset, scale: Float): Offset {
        val image = photo ?: state.image ?: return Offset.Zero
        val fill = maxOf(previewWidth / image.width, previewHeight / image.height)
        val x = maxOf(0f, (image.width * fill * scale - previewWidth) / 2)
        val y = maxOf(0f, (image.height * fill * scale - previewHeight) / 2)
        return Offset(value.x.coerceIn(-x, x), value.y.coerceIn(-y, y))
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                val decoded = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { decodeWallpaper(it.readBytes()) } }
                checkNotNull(decoded)
                photo = decoded; draft = draft.copy(kind = "photo"); edited = true; zoom = 1f; offset = Offset.Zero
            } catch (_: Exception) { failed = true } finally { busy = false }
        }
    }
    val fallback = MaterialTheme.colorScheme.background
    Surface(modifier.fillMaxSize(), color = fallback) {
        Column {
            TopAppBar(title = { Text(stringResource(R.string.chat_wallpaper_title)) },
                navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.chat_wallpaper_back)) } },
                actions = {
                    TextButton(enabled = !busy && conversationId.isNotEmpty() && (draft.kind != "photo" || photo != null || state.image != null), onClick = {
                        busy = true
                        val image = photo ?: state.image.takeIf { zoom != 1f || offset != Offset.Zero }; val value = draft; val cropZoom = zoom; val cropOffset = offset
                        scope.launch {
                            try {
                                val bytes = withContext(Dispatchers.Default) {
                                    if (value.kind != "photo" || image == null) null else {
                                        val output = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
                                        val fill = maxOf(1080f / image.width, 1920f / image.height) * cropZoom
                                        val w = image.width * fill; val h = image.height * fill
                                        val factor = 1080f / previewWidth
                                        val x = (1080f - w) / 2 + cropOffset.x * factor; val y = (1920f - h) / 2 + cropOffset.y * factor
                                        Canvas(output).drawBitmap(image, null, RectF(x, y, x + w, y + h), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
                                        ByteArrayOutputStream().use { stream -> output.compress(Bitmap.CompressFormat.JPEG, 85, stream); output.recycle(); stream.toByteArray() }
                                    }
                                }
                                state.save(value, bytes)
                                InAppNotificationService.showActionToast(InAppActionToast.activityDone(context.getString(R.string.chat_wallpaper_saved)))
                                onDismiss()
                            } catch (_: Exception) { failed = true } finally { busy = false }
                        }
                    }) { if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.chat_wallpaper_save)) }
                })
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.chat_wallpaper_private), style = MaterialTheme.typography.bodySmall)
                Box(Modifier.align(Alignment.CenterHorizontally).size(198.dp, 352.dp).clip(RoundedCornerShape(20.dp))
                    .pointerInput(photo, state.image, draft.kind) { detectTransformGestures { _, pan, zoomChange, _ ->
                        if ((photo ?: state.image) != null && draft.kind == "photo") {
                            edited = true
                            zoom = (zoom * zoomChange).coerceIn(1f, 3f)
                            offset = clamp(offset + pan, zoom)
                        }
                    } }) {
                    WallpaperCanvas(draft, photo ?: state.image, fallback, Modifier.fillMaxSize(), zoom, offset)
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.chat_wallpaper_preview), Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp)).padding(12.dp), fontSize = 16.sp)
                        Text("Moments", Modifier.align(Alignment.End).background(Color(0xFF3E718D), RoundedCornerShape(20.dp)).padding(12.dp), color = Color.White, fontSize = 16.sp)
                    }
                }
                OutlinedButton(enabled = !busy, onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text(stringResource(R.string.chat_wallpaper_photo)) }
                Text(stringResource(R.string.chat_wallpaper_color), style = MaterialTheme.typography.titleSmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("DCE8E4", "E7DFEE", "DAE7F1", "F2E3D5", "E6E6E6", "253A40", "282033", "171717").forEach { hex ->
                        Box(Modifier.size(36.dp).clip(CircleShape).background(wallpaperColor(hex)).clickable { edited = true; draft = draft.copy(kind = "color", colorHex = hex) }, contentAlignment = Alignment.Center) {
                            if (draft.kind == "color" && draft.colorHex == hex) Icon(Icons.Default.Check, null, tint = if (hex.startsWith("1") || hex.startsWith("2")) Color.White else Color.Black)
                        }
                    }
                }
                if (draft.kind == "photo" && (photo ?: state.image) != null) {
                    Text(stringResource(R.string.chat_wallpaper_crop), style = MaterialTheme.typography.bodySmall)
                    Slider(value = zoom, onValueChange = { edited = true; zoom = it; offset = clamp(offset, zoom) }, valueRange = 1f..3f)
                }
                if (draft.kind != "default") {
                    Text(stringResource(R.string.chat_wallpaper_dimming))
                    Slider(value = draft.dimming.toFloat(), onValueChange = { edited = true; draft = draft.copy(dimming = it.toDouble()) }, valueRange = 0f..0.75f)
                }
                TextButton(onClick = { edited = true; draft = draft.copy(kind = "default", storagePath = ""); photo = null; zoom = 1f; offset = Offset.Zero }) { Text(stringResource(R.string.chat_wallpaper_default)) }
            }
        }
    }
    LaunchedEffect(failed) {
        if (failed) {
            InAppNotificationService.showActionToast(InAppActionToast(prefix = context.getString(R.string.chat_wallpaper_error), isError = true))
            failed = false
        }
    }
}
