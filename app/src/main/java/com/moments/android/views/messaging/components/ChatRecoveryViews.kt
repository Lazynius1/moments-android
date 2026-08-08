package com.moments.android.views.messaging.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.moments.android.R
import com.moments.android.models.ChatAccessState
import com.moments.android.models.ChatRecoveryAttemptState
import com.moments.android.services.messaging.EncryptionService
import com.moments.android.services.messaging.MessageIngestService
import com.moments.android.views.messaging.services.ChatAccessCoordinator
import com.moments.android.views.messaging.services.ChatSessionEngine
import com.moments.android.views.shared.MomentsModalSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date
import kotlin.math.ceil
import kotlin.math.max
/**
 * Port de `ChatRecoveryViews.swift` — puerta de acceso cripto al chat: alta de PIN de recuperación,
 * restauración de identidad (intentos + bloqueo) y ajustes.
 *
 * Fondo: sólido AdaptiveColors / alpha (sin ultraThinMaterial iOS). Sheet de cambio de PIN →
 * [MomentsModalSheet] (≡ `.sheet` iOS).
 */

internal data class ChatRecoveryPalette(val isDark: Boolean) {
    val title = if (isDark) Color.White else Color.Black.copy(alpha = 0.88f)
    val body = if (isDark) Color.White.copy(alpha = 0.74f) else Color.Black.copy(alpha = 0.62f)
    val secondary = if (isDark) Color.White.copy(alpha = 0.56f) else Color.Black.copy(alpha = 0.46f)
    val mutedAction = if (isDark) Color.White.copy(alpha = 0.72f) else Color.Black.copy(alpha = 0.64f)
    val error = if (isDark) Color(1f, 0.53f, 0.53f) else Color(0.73f, 0.17f, 0.17f)
    val digitText = if (isDark) Color.White else Color.Black.copy(alpha = 0.86f)
    val digitFillFilled = if (isDark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.08f)
    val digitFillEmpty = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.04f)
    val digitBorderFocused = if (isDark) Color.White.copy(alpha = 0.72f) else Color.Black.copy(alpha = 0.42f)
    val digitBorderFilled = if (isDark) Color.White.copy(alpha = 0.38f) else Color.Black.copy(alpha = 0.22f)
    val digitBorderEmpty = if (isDark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.10f)
}

private const val PIN_LENGTH = 6

fun filteredPIN(text: String, length: Int = PIN_LENGTH): String = text.filter(Char::isDigit).take(length)

fun isValidPIN(pin: String, length: Int = PIN_LENGTH): Boolean = pin.length == length && pin.all(Char::isDigit)

enum class PinFieldKind { PRIMARY, CONFIRMATION }

/** Port de `ChatRecoveryGateView`. */
@Composable
fun ChatRecoveryGateView(
    onCancel: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val accessState by ChatAccessCoordinator.accessState.collectAsState()
    val scope = rememberCoroutineScope()
    var refreshToken by remember { mutableStateOf(0) }

    LaunchedEffect(refreshToken) { ChatAccessCoordinator.ensureAccess() }

    val reloadState: () -> Unit = {
        scope.launch { ChatAccessCoordinator.refreshAccess() }
    }

    when (val state = accessState) {
        ChatAccessState.Available -> content()
        ChatAccessState.NeedsPinSetup -> CreateChatPINView(onSuccess = reloadState, onCancel = onCancel)
        ChatAccessState.NeedsRestore -> {
            val context = LocalContext.current
            var triedPasswordManager by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                if (triedPasswordManager) return@LaunchedEffect
                triedPasswordManager = true
                val restored = EncryptionService.tryRestoreFromDeviceVault(context = context)
                if (restored) {
                    MessageIngestService.resetAfterIdentityRestore()
                    ChatSessionEngine.invalidateAll()
                    ChatAccessCoordinator.refreshAccess()
                }
            }
            RestoreChatPINView(onSuccess = reloadState, onCancel = onCancel)
        }
        is ChatAccessState.Unavailable -> ChatRecoveryStatusView(
            title = stringResource(R.string.chat_recovery_unavailable_title),
            message = state.reason,
            primaryTitle = stringResource(R.string.chat_recovery_action_retry),
            primaryAction = {
                refreshToken++
                reloadState()
            },
            secondaryTitle = onCancel?.let { stringResource(R.string.chat_recovery_action_close) },
            secondaryAction = onCancel,
        )
        null -> Box(
            Modifier.fillMaxSize().background(chatRecoveryBackdropBrush()),
            contentAlignment = Alignment.Center,
        ) {
            val palette = ChatRecoveryPalette(isSystemInDarkTheme())
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CircularProgressIndicator(color = palette.title)
                Text(stringResource(R.string.chat_recovery_loading), color = palette.body, fontSize = 14.sp)
            }
        }
    }
}

/** Port de `CreateChatPINView`. */
@Composable
fun CreateChatPINView(
    isChangeFlow: Boolean = false,
    onSuccess: () -> Unit,
    onCancel: (() -> Unit)? = null,
) {
    val palette = ChatRecoveryPalette(isSystemInDarkTheme())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var activeField by remember { mutableStateOf(PinFieldKind.PRIMARY) }

    val invalidLength = stringResource(R.string.chat_recovery_error_invalid_length)
    val mismatch = stringResource(R.string.chat_recovery_error_mismatch)

    LaunchedEffect(Unit) { activeField = PinFieldKind.PRIMARY }
    LaunchedEffect(pin) {
        if (pin.length == PIN_LENGTH && activeField == PinFieldKind.PRIMARY) {
            activeField = PinFieldKind.CONFIRMATION
        }
    }

    ChatRecoveryFormContainer(
        title = stringResource(
            if (isChangeFlow) R.string.chat_recovery_create_change_title else R.string.chat_recovery_create_title,
        ),
        subtitle = stringResource(R.string.chat_recovery_create_subtitle),
        form = {
            ChatRecoveryPINField(
                title = stringResource(R.string.chat_recovery_field_create_pin),
                subtitle = stringResource(R.string.chat_recovery_field_six_digits),
                value = pin,
                onValueChange = { pin = filteredPIN(it) },
                kind = PinFieldKind.PRIMARY,
                activeField = activeField,
                onActivate = { activeField = PinFieldKind.PRIMARY },
                palette = palette,
            )
            ChatRecoveryPINField(
                title = stringResource(R.string.chat_recovery_field_confirm_pin),
                subtitle = stringResource(R.string.chat_recovery_field_repeat_six_digits),
                value = confirmPin,
                onValueChange = { confirmPin = filteredPIN(it) },
                kind = PinFieldKind.CONFIRMATION,
                activeField = activeField,
                onActivate = { activeField = PinFieldKind.CONFIRMATION },
                palette = palette,
            )
        },
        footer = {
            errorMessage?.let {
                Text(it, color = palette.error, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
            }
            ChatRecoveryPrimaryButton(
                title = when {
                    isSubmitting -> stringResource(R.string.chat_recovery_action_saving)
                    isChangeFlow -> stringResource(R.string.chat_recovery_action_update_pin)
                    else -> stringResource(R.string.chat_recovery_action_save_pin)
                },
                enabled = !isSubmitting,
            ) {
                val trimmed = pin.trim()
                val trimmedConfirm = confirmPin.trim()
                when {
                    !isValidPIN(trimmed) -> errorMessage = invalidLength
                    trimmed != trimmedConfirm -> errorMessage = mismatch
                    else -> {
                        errorMessage = null
                        isSubmitting = true
                        scope.launch {
                            val result = runCatching { EncryptionService.createRecoveryBundle(trimmed, context) }
                            isSubmitting = false
                            result
                                .onSuccess {
                                    pin = ""
                                    confirmPin = ""
                                    onSuccess()
                                }
                                .onFailure { errorMessage = it.message }
                        }
                    }
                }
            }
            onCancel?.let { cancel ->
                Text(
                    stringResource(R.string.chat_recovery_action_not_now),
                    color = palette.mutedAction,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = cancel)
                        .padding(vertical = 6.dp),
                )
            }
        },
    )
}

/** Port de `RestoreChatPINView`. */
@Composable
fun RestoreChatPINView(
    onSuccess: () -> Unit,
    onCancel: (() -> Unit)? = null,
) {
    val palette = ChatRecoveryPalette(isSystemInDarkTheme())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var isWiping by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var attemptState by remember { mutableStateOf(ChatRecoveryAttemptState()) }
    var currentTime by remember { mutableStateOf(Date()) }
    var activeField by remember { mutableStateOf(PinFieldKind.PRIMARY) }
    var showForgotConfirm by remember { mutableStateOf(false) }
    var showMigrateTarget by remember { mutableStateOf(false) }
    var showSavePinAfterMigrate by remember { mutableStateOf(false) }

    val enterPin = stringResource(R.string.chat_recovery_error_enter_recovery_pin)

    fun refreshAttemptState() {
        attemptState = EncryptionService.chatRecoveryAttemptState()
    }

    LaunchedEffect(Unit) {
        activeField = PinFieldKind.PRIMARY
        currentTime = Date()
        refreshAttemptState()
        while (true) {
            delay(1_000)
            currentTime = Date()
            val lockedUntil = attemptState.lockedUntil ?: continue
            val remaining = (lockedUntil.time - currentTime.time) / 1000.0
            if (remaining <= 0) refreshAttemptState()
        }
    }

    val countdownRemaining: Double? = attemptState.lockedUntil
        ?.let { (it.time - currentTime.time) / 1000.0 }
        ?.takeIf { it > 0 }

    ChatRecoveryFormContainer(
        title = stringResource(R.string.chat_recovery_restore_title),
        subtitle = stringResource(R.string.chat_recovery_restore_subtitle),
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
            val visibleMessage = countdownRemaining
                ?.let { stringResource(R.string.chat_recovery_error_locked_timer, formattedLockoutDuration(it)) }
                ?: errorMessage
            visibleMessage?.let {
                Text(
                    it,
                    color = palette.error,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        footer = {
            ChatRecoveryPrimaryButton(
                title = when {
                    isSubmitting || isWiping -> stringResource(R.string.chat_recovery_action_restoring)
                    countdownRemaining != null -> stringResource(
                        R.string.chat_recovery_action_try_again_in,
                        formattedLockoutDuration(countdownRemaining),
                    )
                    else -> stringResource(R.string.chat_recovery_action_restore_chats)
                },
                enabled = !isSubmitting && !isWiping && !attemptState.isLocked,
            ) {
                refreshAttemptState()
                if (attemptState.isLocked) return@ChatRecoveryPrimaryButton
                val trimmed = pin.trim()
                if (!isValidPIN(trimmed)) {
                    errorMessage = enterPin
                } else {
                    errorMessage = null
                    isSubmitting = true
                    scope.launch {
                        val result = runCatching { EncryptionService.restoreChatIdentity(trimmed, context) }
                        isSubmitting = false
                        refreshAttemptState()
                        result
                            .onSuccess {
                                MessageIngestService.resetAfterIdentityRestore()
                                ChatSessionEngine.invalidateAll()
                                pin = ""
                                onSuccess()
                            }
                            .onFailure { errorMessage = it.message }
                    }
                }
            }

            Text(
                stringResource(R.string.chat_recovery_restore_from_other_device),
                color = palette.mutedAction,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isSubmitting && !isWiping) { showMigrateTarget = true }
                    .padding(vertical = 6.dp),
            )

            Text(
                stringResource(R.string.chat_recovery_forgot_action),
                color = palette.error.copy(alpha = 0.9f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isSubmitting && !isWiping) { showForgotConfirm = true }
                    .padding(vertical = 6.dp),
            )

            onCancel?.let { cancel ->
                Text(
                    stringResource(R.string.chat_recovery_action_close),
                    color = palette.mutedAction,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = cancel)
                        .padding(vertical = 6.dp),
                )
            }
        },
    )

    if (showForgotConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showForgotConfirm = false },
            title = { Text(stringResource(R.string.chat_recovery_forgot_title)) },
            text = { Text(stringResource(R.string.chat_recovery_forgot_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showForgotConfirm = false
                        isWiping = true
                        errorMessage = null
                        scope.launch {
                            runCatching { EncryptionService.resetRecoveryLosingHistory() }
                                .onSuccess {
                                    MessageIngestService.resetAfterIdentityRestore()
                                    ChatSessionEngine.invalidateAll()
                                    ChatAccessCoordinator.refreshAccess()
                                }
                                .onFailure { errorMessage = it.message }
                            isWiping = false
                        }
                    },
                ) {
                    Text(stringResource(R.string.chat_recovery_forgot_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showForgotConfirm = false }) {
                    Text(stringResource(R.string.chat_recovery_action_close))
                }
            },
        )
    }

    if (showMigrateTarget) {
        MomentsModalSheet(
            onDismissRequest = { showMigrateTarget = false },
            largeOnly = true,
        ) {
            ChatRecoveryMigrateTargetView(
                onSuccess = {
                    showMigrateTarget = false
                    showSavePinAfterMigrate = true
                    onSuccess()
                },
                onCancel = { showMigrateTarget = false },
            )
        }
    }

    if (showSavePinAfterMigrate) {
        MomentsModalSheet(
            onDismissRequest = { showSavePinAfterMigrate = false },
            largeOnly = false,
        ) {
            ChatRecoverySavePINToVaultView(onDone = { showSavePinAfterMigrate = false })
        }
    }
}

/**
 * Port de `ChatRecoverySettingsView`.
 * Cambio de PIN: iOS `.sheet` → [MomentsModalSheet] (no sustituye la pantalla entera).
 */
@Composable
fun ChatRecoverySettingsView(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = ChatRecoveryPalette(isSystemInDarkTheme())
    val scope = rememberCoroutineScope()
    var showChangePin by remember { mutableStateOf(false) }
    var showMigrate by remember { mutableStateOf(false) }
    var isRemovingLocalKey by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    val updated = stringResource(R.string.chat_recovery_settings_updated)
    val localKeyRemoved = stringResource(R.string.chat_recovery_settings_local_key_removed)

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(R.string.chat_recovery_settings_title),
            color = palette.title,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            stringResource(R.string.chat_recovery_settings_description),
            color = palette.secondary,
            fontSize = 14.sp,
        )

        Text(
            stringResource(R.string.chat_recovery_settings_change_pin),
            color = palette.title,
            fontSize = 15.sp,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showChangePin = true }
                .padding(vertical = 10.dp),
        )

        Text(
            stringResource(R.string.chat_recovery_settings_migrate),
            color = palette.title,
            fontSize = 15.sp,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showMigrate = true }
                .padding(vertical = 10.dp),
        )

        Text(
            stringResource(
                if (isRemovingLocalKey) R.string.chat_recovery_settings_removing_local_key
                else R.string.chat_recovery_settings_force_restore,
            ),
            color = palette.title,
            fontSize = 15.sp,
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (isRemovingLocalKey) 0.6f else 1f)
                .clickable(enabled = !isRemovingLocalKey) {
                    isRemovingLocalKey = true
                    scope.launch {
                        runCatching { EncryptionService.removeLocalChatIdentity() }
                            .onSuccess {
                                statusMessage = localKeyRemoved
                                ChatAccessCoordinator.refreshAccess()
                            }
                            .onFailure { statusMessage = it.message }
                        isRemovingLocalKey = false
                    }
                }
                .padding(vertical = 10.dp),
        )

        statusMessage?.let { Text(it, color = palette.secondary, fontSize = 13.sp) }

        Text(
            stringResource(R.string.chat_recovery_action_close),
            color = palette.mutedAction,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClose)
                .padding(vertical = 8.dp),
        )
    }

    if (showChangePin) {
        MomentsModalSheet(
            onDismissRequest = { showChangePin = false },
            largeOnly = true,
            containerColor = Color.Transparent,
            showDragHandle = false,
        ) {
            CreateChatPINView(
                isChangeFlow = true,
                onSuccess = {
                    statusMessage = updated
                    showChangePin = false
                },
                onCancel = { showChangePin = false },
            )
        }
    }

    if (showMigrate) {
        MomentsModalSheet(
            onDismissRequest = { showMigrate = false },
            largeOnly = true,
        ) {
            ChatRecoveryMigrateSourceView(onClose = { showMigrate = false })
        }
    }
}

/** Port de `ChatRecoveryStatusView` (tarjeta propia, no FormContainer). */
@Composable
fun ChatRecoveryStatusView(
    title: String,
    message: String,
    primaryTitle: String,
    primaryAction: () -> Unit,
    secondaryTitle: String? = null,
    secondaryAction: (() -> Unit)? = null,
) {
    val isDark = isSystemInDarkTheme()
    val palette = ChatRecoveryPalette(isDark)
    // iOS: siempre black.opacity(0.55) + material → fill sólido.
    val cardFill = Color.Black.copy(alpha = 0.55f)
    val cardStroke = Color.White.copy(alpha = 0.18f)

    Box(
        Modifier
            .fillMaxSize()
            .background(chatRecoveryBackdropBrush()),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(cardFill)
                .border(1.dp, cardStroke, RoundedCornerShape(32.dp))
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                Modifier
                    .width(42.dp)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.65f)),
            )
            Text(
                title,
                color = palette.title,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                message,
                color = palette.body,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
            ChatRecoveryPrimaryButton(title = primaryTitle, enabled = true, onClick = primaryAction)
            if (secondaryTitle != null && secondaryAction != null) {
                Text(
                    secondaryTitle,
                    color = palette.mutedAction,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = secondaryAction)
                        .padding(vertical = 4.dp),
                )
            }
        }
    }
}

/** Port de `ChatRecoveryFormContainer`. Material iOS → fill sólido (base fill iOS). */
@Composable
internal fun ChatRecoveryFormContainer(
    title: String,
    subtitle: String,
    form: @Composable () -> Unit,
    footer: @Composable () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val palette = ChatRecoveryPalette(isDark)
    // ≡ iOS cardBaseFill (sin ultraThinMaterial).
    val cardFill = if (isDark) Color.Black.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.82f)
    val cardStroke = if (isDark) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.72f)
    val cardShadow = if (isDark) Color.Black.copy(alpha = 0.28f) else Color.Black.copy(alpha = 0.14f)
    val grabber = if (isDark) Color.White.copy(alpha = 0.65f) else Color.Black.copy(alpha = 0.14f)
    val lockGradient = Brush.verticalGradient(
        if (isDark) {
            listOf(Color.White.copy(alpha = 0.96f), Color.White.copy(alpha = 0.68f))
        } else {
            listOf(Color.Black.copy(alpha = 0.82f), Color.Black.copy(alpha = 0.52f))
        },
    )

    // Dialogs fullscreen suelen nacer con decorFitsSystemWindows=true → IME inset = 0
    // y la card queda detrás del teclado. Forzar edge-to-edge + adjustResize en ese window.
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val previousMode = window.attributes.softInputMode
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            onDispose { window.setSoftInputMode(previousMode) }
        } else {
            onDispose { }
        }
    }

    // Backdrop a pantalla completa; el área útil se encoge con imePadding para que
    // BottomCenter ancle la card encima del teclado (≡ keyboard avoidance iOS).
    Box(
        Modifier
            .fillMaxSize()
            .background(chatRecoveryBackdropBrush()),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .imePadding(),
            contentAlignment = Alignment.BottomCenter,
        ) {
        Column(
            Modifier
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .shadow(24.dp, RoundedCornerShape(32.dp), ambientColor = cardShadow, spotColor = cardShadow)
                .clip(RoundedCornerShape(32.dp))
                .background(cardFill)
                .border(1.dp, cardStroke, RoundedCornerShape(32.dp))
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .padding(top = 12.dp)
                    .width(42.dp)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(grabber),
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
                    .padding(top = 18.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // ≡ iOS lock.fill + LinearGradient
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .size(30.dp)
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithCache {
                                onDrawWithContent {
                                    drawContent()
                                    drawRect(brush = lockGradient, blendMode = BlendMode.SrcIn)
                                }
                            },
                    )
                    Text(
                        title,
                        color = palette.title,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        subtitle,
                        color = palette.body,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    form()
                }
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    footer()
                }
            }
        }
        }
    }
}

/** Port de `ChatRecoveryPINField` + `ChatRecoveryDigitCell`. */
@Composable
internal fun ChatRecoveryPINField(
    title: String,
    subtitle: String,
    value: String,
    onValueChange: (String) -> Unit,
    kind: PinFieldKind,
    activeField: PinFieldKind,
    onActivate: () -> Unit,
    palette: ChatRecoveryPalette,
) {
    val focusRequester = remember { FocusRequester() }
    val isActive = activeField == kind

    LaunchedEffect(isActive) {
        if (isActive) runCatching { focusRequester.requestFocus() }
    }

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = palette.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = palette.secondary, fontSize = 12.sp)
        }

        Box(
            Modifier
                .fillMaxWidth()
                .clickable {
                    onActivate()
                    runCatching { focusRequester.requestFocus() }
                },
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .size(1.dp)
                    .alpha(0.01f)
                    .focusRequester(focusRequester),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                textStyle = TextStyle(color = Color.Transparent),
                cursorBrush = SolidColor(Color.Transparent),
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(PIN_LENGTH) { index ->
                    val filled = index < value.length
                    val focused = isActive && index == value.length
                    Box(
                        // ≡ iOS ChatRecoveryDigitCell 48×60
                        Modifier
                            .width(48.dp)
                            .height(60.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (filled) palette.digitFillFilled else palette.digitFillEmpty)
                            .border(
                                width = if (focused) 2.dp else 1.dp,
                                color = when {
                                    focused -> palette.digitBorderFocused
                                    filled -> palette.digitBorderFilled
                                    else -> palette.digitBorderEmpty
                                },
                                shape = RoundedCornerShape(16.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (filled) {
                            Text(
                                "*",
                                color = palette.digitText,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Port de `ChatRecoveryPrimaryButtonStyle` (siempre blanco → texto oscuro; press 0.99). */
@Composable
internal fun ChatRecoveryPrimaryButton(
    title: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fill = Brush.linearGradient(
        if (pressed) {
            listOf(Color.White.copy(alpha = 0.78f), Color.White.copy(alpha = 0.58f))
        } else {
            listOf(Color.White.copy(alpha = 0.92f), Color.White.copy(alpha = 0.72f))
        },
    )
    Box(
        Modifier
            .fillMaxWidth()
            .scale(if (pressed) 0.99f else 1f)
            .clip(RoundedCornerShape(14.dp))
            .background(fill)
            .alpha(if (enabled) 1f else 0.5f)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title,
            color = Color.Black.copy(alpha = 0.88f),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun chatRecoveryBackdropBrush(): Brush {
    // ≡ iOS LinearGradient 0.38→0.2 (ultraThinMaterial omitido).
    return Brush.verticalGradient(
        listOf(Color.Black.copy(alpha = 0.38f), Color.Black.copy(alpha = 0.2f)),
    )
}

private fun formattedLockoutDuration(seconds: Double): String {
    val total = max(1, ceil(seconds).toInt())
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}
