package com.moments.android.views.messaging.components

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.moments.android.R
import com.moments.android.models.ChatRecoveryMigrationSession
import com.moments.android.services.messaging.EncryptionService
import com.moments.android.services.messaging.MessageIngestService
import com.moments.android.views.messaging.services.ChatAccessCoordinator
import com.moments.android.views.messaging.services.ChatSessionEngine
import com.moments.android.views.shared.MomentsModalSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/** Port de `ChatRecoveryMigrateSourceView` — QR sin mostrar el enlace crudo. */
@Composable
fun ChatRecoveryMigrateSourceView(onClose: () -> Unit) {
    val palette = ChatRecoveryPalette(androidx.compose.foundation.isSystemInDarkTheme())
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var session by remember { mutableStateOf<ChatRecoveryMigrationSession?>(null) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var didCopy by remember { mutableStateOf(false) }
    var remainingLabel by remember { mutableStateOf<String?>(null) }
    val expireTemplate = stringResource(R.string.chat_recovery_migrate_expire)
    val expired = stringResource(R.string.chat_recovery_error_migration_expired)

    suspend fun startMigration() {
        isLoading = true
        errorMessage = null
        didCopy = false
        runCatching { EncryptionService.beginDeviceMigration() }
            .onSuccess {
                session = it
                qrBitmap = makeMigrationQrBitmap(it.qrPayload)
            }
            .onFailure {
                session = null
                qrBitmap = null
                errorMessage = it.message
            }
        isLoading = false
    }

    LaunchedEffect(Unit) { startMigration() }
    LaunchedEffect(session) {
        val expiresAt = session?.expiresAt ?: return@LaunchedEffect
        while (true) {
            val remainingMs = expiresAt.time - System.currentTimeMillis()
            remainingLabel = if (remainingMs <= 0) {
                expired
            } else {
                val totalSeconds = ((remainingMs + 999) / 1000).toInt()
                expireTemplate.format("%d:%02d".format(totalSeconds / 60, totalSeconds % 60))
            }
            delay(1_000)
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.chat_recovery_migrate_title),
            color = palette.title,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            stringResource(R.string.chat_recovery_migrate_subtitle),
            color = palette.secondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )

        when {
            isLoading -> CircularProgressIndicator(color = palette.title)
            qrBitmap != null -> {
                Image(
                    bitmap = qrBitmap!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(220.dp),
                )
                remainingLabel?.let {
                    Text(it, color = palette.secondary, fontSize = 13.sp)
                }
                Text(
                    if (didCopy) {
                        stringResource(R.string.chat_recovery_migrate_code_copied)
                    } else {
                        stringResource(R.string.chat_recovery_migrate_copy_code)
                    },
                    color = palette.mutedAction,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable {
                            session?.qrPayload?.let {
                                clipboard.setText(AnnotatedString(it))
                                didCopy = true
                            }
                        }
                        .padding(vertical = 8.dp),
                )
            }
            else -> {
                errorMessage?.let { Text(it, color = palette.error, textAlign = TextAlign.Center) }
                Text(
                    stringResource(R.string.chat_recovery_action_retry),
                    color = palette.title,
                    modifier = Modifier.clickable { scope.launch { startMigration() } },
                )
            }
        }

        Text(
            stringResource(R.string.chat_recovery_action_close),
            color = palette.mutedAction,
            modifier = Modifier
                .clickable(onClick = onClose)
                .padding(vertical = 8.dp),
        )
    }
}

/** Port de `ChatRecoveryMigrateTargetView`. */
@Composable
fun ChatRecoveryMigrateTargetView(
    onSuccess: () -> Unit,
    onCancel: () -> Unit,
) {
    val palette = ChatRecoveryPalette(androidx.compose.foundation.isSystemInDarkTheme())
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showScanner by remember { mutableStateOf(false) }
    val clipboardEmpty = stringResource(R.string.chat_recovery_migrate_clipboard_empty)

    fun submit(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return
        isSubmitting = true
        errorMessage = null
        scope.launch {
            runCatching { EncryptionService.completeDeviceMigration(trimmed) }
                .onSuccess {
                    MessageIngestService.resetAfterIdentityRestore()
                    ChatSessionEngine.invalidateAll()
                    ChatAccessCoordinator.refreshAccess()
                    onSuccess()
                }
                .onFailure { errorMessage = it.message }
            isSubmitting = false
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.chat_recovery_restore_from_other_device),
            color = palette.title,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            stringResource(R.string.chat_recovery_migrate_enter_code),
            color = palette.secondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        errorMessage?.let {
            Text(it, color = palette.error, fontSize = 13.sp, textAlign = TextAlign.Center)
        }

        Button(
            onClick = { showScanner = true },
            enabled = !isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (isSubmitting) {
                    stringResource(R.string.chat_recovery_migrate_completing)
                } else {
                    stringResource(R.string.chat_recovery_migrate_scan)
                },
            )
        }

        TextButton(
            onClick = {
                val pasted = clipboard.getText()?.text?.trim().orEmpty()
                if (pasted.isEmpty()) {
                    errorMessage = clipboardEmpty
                } else {
                    submit(pasted)
                }
            },
            enabled = !isSubmitting,
        ) {
            Text(stringResource(R.string.chat_recovery_migrate_paste))
        }

        Text(
            stringResource(R.string.chat_recovery_action_close),
            color = palette.mutedAction,
            modifier = Modifier
                .clickable(onClick = onCancel)
                .padding(vertical = 8.dp),
        )
    }

    if (showScanner) {
        MomentsModalSheet(
            onDismissRequest = { showScanner = false },
            largeOnly = true,
        ) {
            ChatRecoveryQrScanner(
                onCode = { code ->
                    showScanner = false
                    submit(code)
                },
                onClose = { showScanner = false },
            )
        }
    }
}

@Composable
fun ChatRecoverySavePINToVaultView(onDone: () -> Unit) {
    val palette = ChatRecoveryPalette(androidx.compose.foundation.isSystemInDarkTheme())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var activeField by remember { mutableStateOf(PinFieldKind.PRIMARY) }
    val enterPin = stringResource(R.string.chat_recovery_error_enter_recovery_pin)

    ChatRecoveryFormContainer(
        title = stringResource(R.string.chat_recovery_vault_save_title),
        subtitle = stringResource(R.string.chat_recovery_vault_save_subtitle),
        form = {
            ChatRecoveryPINField(
                title = stringResource(R.string.chat_recovery_field_recovery_pin),
                subtitle = stringResource(R.string.chat_recovery_field_six_digits),
                value = pin,
                onValueChange = { pin = filteredPIN(it) },
                kind = PinFieldKind.PRIMARY,
                activeField = activeField,
                onActivate = { activeField = PinFieldKind.PRIMARY },
                palette = palette,
            )
            errorMessage?.let {
                Text(it, color = palette.error, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
            }
        },
        footer = {
            ChatRecoveryPrimaryButton(
                title = if (isSubmitting) {
                    stringResource(R.string.chat_recovery_action_saving)
                } else {
                    stringResource(R.string.chat_recovery_action_save_pin)
                },
                enabled = !isSubmitting,
            ) {
                val trimmed = pin.trim()
                if (!isValidPIN(trimmed)) {
                    errorMessage = enterPin
                } else {
                    isSubmitting = true
                    errorMessage = null
                    scope.launch {
                        runCatching { EncryptionService.saveRecoveryPINToDeviceVault(trimmed, context) }
                            .onSuccess { onDone() }
                            .onFailure { errorMessage = it.message }
                        isSubmitting = false
                    }
                }
            }
            Text(
                stringResource(R.string.chat_recovery_action_not_now),
                color = palette.mutedAction,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onDone)
                    .padding(vertical = 6.dp),
            )
        },
    )
}

@Composable
private fun ChatRecoveryQrScanner(
    onCode: (String) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val emitted = remember { AtomicBoolean(false) }
    val palette = ChatRecoveryPalette(androidx.compose.foundation.isSystemInDarkTheme())

    Column(Modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.chat_recovery_migrate_scan),
            color = palette.title,
            modifier = Modifier
                .padding(16.dp)
                .clickable(onClick = onClose),
        )
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { previewView ->
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener(
                        {
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }
                            val analysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                            val reader = MultiFormatReader().apply {
                                setHints(
                                    mapOf(
                                        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                                        DecodeHintType.CHARACTER_SET to "UTF-8",
                                    ),
                                )
                            }
                            analysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                                if (emitted.get()) {
                                    imageProxy.close()
                                    return@setAnalyzer
                                }
                                val mediaImage = imageProxy.image
                                if (mediaImage != null) {
                                    val yBuffer = mediaImage.planes[0].buffer
                                    val yBytes = ByteArray(yBuffer.remaining())
                                    yBuffer.get(yBytes)
                                    val source = PlanarYUVLuminanceSource(
                                        yBytes,
                                        imageProxy.width,
                                        imageProxy.height,
                                        0,
                                        0,
                                        imageProxy.width,
                                        imageProxy.height,
                                        false,
                                    )
                                    val result = runCatching {
                                        reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
                                    }.getOrNull()
                                    reader.reset()
                                    val text = result?.text
                                    if (text != null && text.contains("moments-migrate://") && emitted.compareAndSet(false, true)) {
                                        onCode(text)
                                    }
                                }
                                imageProxy.close()
                            }
                            runCatching {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    analysis,
                                )
                            }
                        },
                        ContextCompat.getMainExecutor(ctx),
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                ProcessCameraProvider.getInstance(context).get().unbindAll()
            }
        }
    }
}

private fun makeMigrationQrBitmap(content: String, size: Int = 512): Bitmap? = runCatching {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bmp.setPixel(x, y, if (matrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
        }
    }
    bmp
}.getOrNull()
