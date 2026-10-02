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
    val storagePath: String = "", val dimming: Double = 0.2, val bubbleColorHex: String = "3F6F8F", val presetId: String = "",
) {
    fun fields(): Map<String, Any> = mapOf("kind" to kind, "colorHex" to colorHex,
        "storagePath" to storagePath, "dimming" to dimming, "bubbleColorHex" to bubbleColorHex, "presetId" to presetId, "updatedAt" to FieldValue.serverTimestamp())
    companion object {
        fun from(data: Map<String, Any>?) = ChatWallpaper(
            data?.get("kind") as? String ?: "default", data?.get("colorHex") as? String ?: "DCE8E4",
            data?.get("storagePath") as? String ?: "", (data?.get("dimming") as? Number)?.toDouble()?.coerceIn(0.0, 0.75) ?: 0.2, data?.get("bubbleColorHex") as? String ?: "3F6F8F", data?.get("presetId") as? String ?: "",
        )
    }
}

internal class ChatWallpaperState(context: Context, val uid: String, val conversationId: String) {
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
            "storagePath" to value.storagePath, "dimming" to value.dimming, "bubbleColorHex" to value.bubbleColorHex, "presetId" to value.presetId)).toString()).apply()
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

internal fun decodeWallpaper(bytes: ByteArray): Bitmap? {
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
internal fun rememberWallpaper(conversationId: String): ChatWallpaperState {
    val context = LocalContext.current
    val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    val state = remember(uid, conversationId) { ChatWallpaperState(context, uid, conversationId) }
    DisposableEffect(state) { state.start(); onDispose { state.close() } }
    return state
}
internal fun wallpaperColor(hex: String) = runCatching { Color(android.graphics.Color.parseColor("#$hex")) }.getOrDefault(Color(0xFFDCE8E4))

@Composable
fun ChatWallpaperBackground(conversationId: String, fallback: Color, modifier: Modifier = Modifier) {
    val state = rememberWallpaper(conversationId)
    WallpaperCanvas(state.wallpaper, state.image, fallback, modifier)
}
@Composable
internal fun WallpaperCanvas(value: ChatWallpaper, image: Bitmap?, fallback: Color, modifier: Modifier = Modifier, zoom: Float = 1f, offset: Offset = Offset.Zero) {
    BoxWithConstraints(modifier.background(if (value.kind == "color") wallpaperColor(value.colorHex) else fallback).clip(RoundedCornerShape(0.dp)), contentAlignment = Alignment.Center) {
        if (value.kind == "preset") chatWallpaperPresets.firstOrNull { it.id == value.presetId }?.let { preset ->
            coil.compose.AsyncImage(model = preset.asset, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        if (value.kind == "photo" && image != null) {
            val fill = maxOf(maxWidth.value / image.width, maxHeight.value / image.height) * zoom
            Image(image.asImageBitmap(), null,
                Modifier.requiredSize((image.width * fill).dp, (image.height * fill).dp)
                    .graphicsLayer { translationX = offset.x; translationY = offset.y }, contentScale = ContentScale.FillBounds)
        }
        if (value.kind != "default") Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = value.dimming.toFloat())))
    }
}
