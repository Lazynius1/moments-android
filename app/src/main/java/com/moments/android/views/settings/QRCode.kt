package com.moments.android.views.settings

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.moments.android.R
import com.moments.android.models.AppUser
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.fetchUser
import com.moments.android.utilities.HapticManager
import com.moments.android.utilities.MomentsPressDefaults
import com.moments.android.utilities.momentsPress
import com.moments.android.views.messaging.components.AttachmentIcon
import com.moments.android.views.messaging.components.AttachmentIconPreset
import com.moments.android.views.messaging.components.AttachmentIconView
import com.moments.android.views.permission.shared.PermissionPrimerGate
import com.moments.android.views.permission.shared.PermissionPrimerGateHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Port de `QRCodeView.swift` + `QRCodeViewModel`.
 *
 * Deep link: `glowsy://profile/{username}` (igual que iOS).
 * Share URL: `https://glowsy.app/{username}`.
 */

private val QrAccent = Color(0xFF007AFF) // ≡ ProfileColors.accent

@Composable
fun QRCodeView(
    /** ≡ iOS `targetUser` — si null, carga el usuario actual. */
    user: AppUser? = null,
    onNavigateBack: () -> Unit = {},
) {
    val isDark = isSystemInDarkTheme()
    val textColor = if (isDark) Color.White else Color.Black
    val cardBg = if (isDark) Color(0xFF1A1A1A) else Color.White
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val firestoreService = remember { FirestoreService() }
    val photosSaveGate = remember { PermissionPrimerGate(PermissionPrimerGate.Kind.PHOTOS_SAVE) }

    var loadedUser by remember { mutableStateOf<AppUser?>(null) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val username = user?.username ?: loadedUser?.username.orEmpty()
    val showActions = user == null

    LaunchedEffect(Unit) {
        if (user == null) {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
            runCatching { loadedUser = firestoreService.fetchUser(uid) }
        }
    }

    LaunchedEffect(username) {
        if (username.isBlank()) {
            qrBitmap = null
            return@LaunchedEffect
        }
        qrBitmap = withContext(Dispatchers.Default) {
            generateQrBitmap("glowsy://profile/$username")
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.qr_code_title),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.padding(top = 20.dp, bottom = 20.dp),
        )

        Column(
            Modifier
                .padding(bottom = 30.dp)
                .shadow(10.dp, RoundedCornerShape(24.dp), ambientColor = Color.Black.copy(0.1f))
                .clip(RoundedCornerShape(24.dp))
                .background(cardBg)
                .padding(30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(
                Modifier.size(200.dp),
                contentAlignment = Alignment.Center,
            ) {
                val bmp = qrBitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(200.dp),
                    )
                } else {
                    CircularProgressIndicator(color = QrAccent)
                }
            }

            Text(
                "@$username",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = QrAccent,
            )
        }

        if (showActions) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val shareInteraction = remember { MutableInteractionSource() }
                Row(
                    Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(50))
                        .background(QrAccent)
                        .momentsPress(shareInteraction, MomentsPressDefaults.momentsPressSubtle)
                        .clickable(
                            interactionSource = shareInteraction,
                            indication = null,
                            onClick = {
                                val bmp = qrBitmap ?: return@clickable
                                scope.launch { shareQr(context, bmp, username) }
                            },
                        ),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AttachmentIconView(
                        icon = AttachmentIcon.SHARE,
                        preset = AttachmentIconPreset.SHARE_INLINE,
                        tintColor = Color.White,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        stringResource(R.string.qr_code_share),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                val saveInteraction = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(if (isDark) Color.Black.copy(0.95f) else Color.White.copy(0.95f))
                        .border(1.dp, textColor.copy(0.2f), CircleShape)
                        .momentsPress(saveInteraction, MomentsPressDefaults.momentsPressSubtle)
                        .clickable(
                            interactionSource = saveInteraction,
                            indication = null,
                            onClick = {
                                val bmp = qrBitmap ?: return@clickable
                                photosSaveGate.requestAccess(context) {
                                    scope.launch {
                                        saveQrToGallery(context, bmp)
                                        HapticManager.shared.mediumImpact()
                                    }
                                }
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.FileDownload,
                        contentDescription = stringResource(R.string.qr_code_save_to_photos),
                        tint = textColor,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        } else {
            Spacer(Modifier.height(20.dp))
        }
    }

    PermissionPrimerGateHost(gate = photosSaveGate)
}

private fun generateQrBitmap(content: String, size: Int = 800): Bitmap? {
    return try {
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(
                    x,
                    y,
                    if (matrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE,
                )
            }
        }
        bmp
    } catch (_: Exception) {
        null
    }
}

private suspend fun shareQr(context: Context, bitmap: Bitmap, username: String) {
    val uri = withContext(Dispatchers.IO) {
        insertQrImage(context, bitmap, "share_qr_$username.png")
    } ?: return
    withContext(Dispatchers.Main) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "https://glowsy.app/$username")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, null))
    }
}

private suspend fun saveQrToGallery(context: Context, bitmap: Bitmap) {
    withContext(Dispatchers.IO) {
        insertQrImage(context, bitmap, "moments_qr_${System.currentTimeMillis()}.png")
    }
}

private fun insertQrImage(context: Context, bitmap: Bitmap, displayName: String) =
    runCatching {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return@runCatching null
        resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        uri
    }.getOrNull()
