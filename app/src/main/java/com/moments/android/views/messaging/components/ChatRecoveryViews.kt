package com.moments.android.views.messaging.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.models.ChatAccessState
import com.moments.android.models.ChatRecoveryAttemptState
import com.moments.android.services.messaging.EncryptionService
import com.moments.android.services.messaging.MessageIngestService
import com.moments.android.views.messaging.services.ChatAccessCoordinator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date

/**
 * Port de `ChatRecoveryViews.swift` — puerta de acceso cripto al chat: alta de PIN de recuperación,
 * restauración de identidad en un dispositivo nuevo (con intentos y bloqueo temporal) y ajustes.
 *
 * Reescrito desde el Swift: la versión anterior era un esbozo que además etiquetaba todos los
 * botones con "Reply" y no llamaba a la cripto, así que el PIN nunca restauraba nada.
 */

private data class ChatRecoveryPalette(val isDark: Boolean) {
    val title = if (isDark) Color.White else Color.Black.copy(alpha = 0.88f)
    val body = if (isDark) Color.White.copy(alpha = 0.74f) else Color.Black.copy(alpha = 0.62f)
    val secondary = if (isDark) Color.White.copy(alpha = 0.56f) else Color.Black.copy(alpha = 0.46f)
    val mutedAction = if (isDark) Color.White.copy(alpha = 0.72f) else Color.Black.copy(alpha = 0.64f)
    val error = if (isDark) Color(0xFFFF8787) else Color(0xFFBA2B2B)
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

/**
 * Port de `ChatRecoveryGateView`: envuelve el contenido del chat y sólo lo muestra cuando el acceso
 * cripto está resuelto. Resuelve el estado con [ChatAccessCoordinator] al entrar, como el `.task` de iOS.
 */
@Composable
fun ChatRecoveryGateView(
    onCancel: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val accessState by ChatAccessCoordinator.accessState.collectAsState()
    val scope = rememberCoroutineScope()
    var refreshToken by remember { mutableStateOf(0) }

    LaunchedEffect(refreshToken) { ChatAccessCoordinator.ensureAccess() }

    val reloadState: () -> Unit = { scope.launch { ChatAccessCoordinator.refreshAccess() }; Unit }

    when (val state = accessState) {
        ChatAccessState.Available -> content()
        ChatAccessState.NeedsPinSetup -> CreateChatPINView(onSuccess = reloadState, onCancel = onCancel)
        ChatAccessState.NeedsRestore -> RestoreChatPINView(onSuccess = reloadState, onCancel = onCancel)
        is ChatAccessState.Unavailable -> ChatRecoveryStatusView(
            title = stringResource(R.string.chat_recovery_unavailable_title),
            message = state.reason,
            primaryTitle = stringResource(R.string.chat_recovery_action_retry),
            primaryAction = { refreshToken++; reloadState() },
            secondaryTitle = onCancel?.let { stringResource(R.string.chat_recovery_action_close) },
            secondaryAction = onCancel,
        )
        null -> Box(
            Modifier.fillMaxSize().then(ChatRecoveryBackdropModifier()),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                CircularProgressIndicator()
                Text(stringResource(R.string.chat_recovery_loading), color = ChatRecoveryPalette(isSystemInDarkTheme()).body)
            }
        }
    }
}

/** Port de `CreateChatPINView`: alta (o cambio) del PIN de recuperación. */
@Composable
fun CreateChatPINView(
    isChangeFlow: Boolean = false,
    onSuccess: () -> Unit,
    onCancel: (() -> Unit)? = null,
) {
    val palette = ChatRecoveryPalette(isSystemInDarkTheme())
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var activeField by remember { mutableStateOf(PinFieldKind.PRIMARY) }

    val invalidLength = stringResource(R.string.chat_recovery_error_invalid_length)
    val mismatch = stringResource(R.string.chat_recovery_error_mismatch)

    // Como iOS: al completar el primer PIN, el foco salta a la confirmación.
    LaunchedEffect(pin) {
        if (pin.length == PIN_LENGTH && activeField == PinFieldKind.PRIMARY) activeField = PinFieldKind.CONFIRMATION
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
                onFocus = { activeField = PinFieldKind.PRIMARY },
                palette = palette,
            )
            ChatRecoveryPINField(
                title = stringResource(R.string.chat_recovery_field_confirm_pin),
                subtitle = stringResource(R.string.chat_recovery_field_repeat_six_digits),
                value = confirmPin,
                onValueChange = { confirmPin = filteredPIN(it) },
                kind = PinFieldKind.CONFIRMATION,
                activeField = activeField,
                onFocus = { activeField = PinFieldKind.CONFIRMATION },
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
                palette = palette,
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
                            val result = runCatching { EncryptionService.createRecoveryBundle(trimmed) }
                            isSubmitting = false
                            result
                                .onSuccess { pin = ""; confirmPin = ""; onSuccess() }
                                .onFailure { errorMessage = it.message }
                        }
                    }
                }
            }
            onCancel?.let {
                Text(
                    stringResource(R.string.chat_recovery_action_not_now),
                    color = palette.mutedAction,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clickable(onClick = it).padding(vertical = 6.dp),
                )
            }
        },
    )
}

/** Port de `RestoreChatPINView`: restaura la identidad en un dispositivo nuevo, con bloqueo por intentos. */
@Composable
fun RestoreChatPINView(
    onSuccess: () -> Unit,
    onCancel: (() -> Unit)? = null,
) {
    val palette = ChatRecoveryPalette(isSystemInDarkTheme())
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var attemptState by remember { mutableStateOf(ChatRecoveryAttemptState()) }
    var currentTime by remember { mutableStateOf(Date()) }

    val enterPin = stringResource(R.string.chat_recovery_error_enter_recovery_pin)

    fun refreshAttemptState() { attemptState = EncryptionService.chatRecoveryAttemptState() }

    LaunchedEffect(Unit) { refreshAttemptState() }

    // Equivalente al Timer de 1s de iOS: refresca la cuenta atrás del bloqueo.
    LaunchedEffect(attemptState.lockedUntil) {
        while (true) {
            delay(1_000)
            currentTime = Date()
            if (!attemptState.isLocked) break
        }
        refreshAttemptState()
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
                activeField = PinFieldKind.PRIMARY,
                onFocus = {},
                palette = palette,
            )
            val visibleMessage = countdownRemaining
                ?.let { stringResource(R.string.chat_recovery_error_locked_timer, formattedLockoutDuration(it)) }
                ?: errorMessage
            visibleMessage?.let {
                Text(it, color = palette.error, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
            }
        },
        footer = {
            ChatRecoveryPrimaryButton(
                title = when {
                    isSubmitting -> stringResource(R.string.chat_recovery_action_restoring)
                    countdownRemaining != null -> stringResource(
                        R.string.chat_recovery_action_try_again_in,
                        formattedLockoutDuration(countdownRemaining),
                    )
                    else -> stringResource(R.string.chat_recovery_action_restore_chats)
                },
                enabled = !isSubmitting && !attemptState.isLocked,
                palette = palette,
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
                        val result = runCatching { EncryptionService.restoreChatIdentity(trimmed) }
                        isSubmitting = false
                        refreshAttemptState()
                        result
                            .onSuccess {
                                // Los mensajes cacheados mientras la identidad era otra se
                                // guardaron en cifrado; hay que tirarlos para que se rebajen
                                // y se descifren con la identidad ya restaurada.
                                MessageIngestService.resetAfterIdentityRestore()
                                pin = ""
                                onSuccess()
                            }
                            .onFailure { errorMessage = it.message }
                    }
                }
            }
            onCancel?.let {
                Text(
                    stringResource(R.string.chat_recovery_action_close),
                    color = palette.mutedAction,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clickable(onClick = it).padding(vertical = 6.dp),
                )
            }
        },
    )
}

/** Port de `ChatRecoverySettingsView`: cambiar PIN y forzar restauración en este dispositivo. */
@Composable
fun ChatRecoverySettingsView(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = ChatRecoveryPalette(isSystemInDarkTheme())
    val scope = rememberCoroutineScope()
    var showChangePin by remember { mutableStateOf(false) }
    var isRemovingLocalKey by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    val updated = stringResource(R.string.chat_recovery_settings_updated)
    val localKeyRemoved = stringResource(R.string.chat_recovery_settings_local_key_removed)

    if (showChangePin) {
        CreateChatPINView(
            isChangeFlow = true,
            onSuccess = { statusMessage = updated; showChangePin = false },
            onCancel = { showChangePin = false },
        )
        return
    }

    Column(modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            stringResource(R.string.chat_recovery_settings_title),
            color = palette.title,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(stringResource(R.string.chat_recovery_settings_description), color = palette.secondary, fontSize = 14.sp)

        Text(
            stringResource(R.string.chat_recovery_settings_change_pin),
            color = palette.title,
            fontSize = 15.sp,
            modifier = Modifier.fillMaxWidth().clickable { showChangePin = true }.padding(vertical = 10.dp),
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
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClose).padding(vertical = 8.dp),
        )
    }
}

/** Port de `ChatRecoveryStatusView`. */
@Composable
fun ChatRecoveryStatusView(
    title: String,
    message: String,
    primaryTitle: String,
    primaryAction: () -> Unit,
    secondaryTitle: String? = null,
    secondaryAction: (() -> Unit)? = null,
) {
    val palette = ChatRecoveryPalette(isSystemInDarkTheme())
    ChatRecoveryFormContainer(
        title = title,
        subtitle = message,
        form = {
            Icon(Icons.Filled.Lock, null, tint = palette.title, modifier = Modifier.size(30.dp))
        },
        footer = {
            ChatRecoveryPrimaryButton(primaryTitle, enabled = true, palette = palette, onClick = primaryAction)
            if (secondaryTitle != null && secondaryAction != null) {
                Text(
                    secondaryTitle,
                    color = palette.mutedAction,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clickable(onClick = secondaryAction).padding(vertical = 6.dp),
                )
            }
        },
    )
}

/** Port de `ChatRecoveryFormContainer`: tarjeta inferior con grabber sobre el backdrop. */
@Composable
private fun ChatRecoveryFormContainer(
    title: String,
    subtitle: String,
    form: @Composable () -> Unit,
    footer: @Composable () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val palette = ChatRecoveryPalette(isDark)
    val cardFill = if (isDark) Color(0xFF101112) else Color.White
    val cardStroke = if (isDark) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.72f)
    val grabber = if (isDark) Color.White.copy(alpha = 0.65f) else Color.Black.copy(alpha = 0.14f)

    Box(
        Modifier.fillMaxSize().then(ChatRecoveryBackdropModifier()),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(cardFill)
                .border(1.dp, cardStroke, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.width(42.dp).height(5.dp).clip(CircleShape).background(grabber))
            Spacer(Modifier.height(2.dp))
            Text(title, color = palette.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(subtitle, color = palette.body, fontSize = 14.sp, textAlign = TextAlign.Center)
            form()
            footer()
            Spacer(Modifier.height(6.dp))
        }
    }
}

/** Port de `ChatRecoveryPINField` + `ChatRecoveryDigitCell`: 6 celdas sobre un campo invisible. */
enum class PinFieldKind { PRIMARY, CONFIRMATION }

@Composable
private fun ChatRecoveryPINField(
    title: String,
    subtitle: String,
    value: String,
    onValueChange: (String) -> Unit,
    kind: PinFieldKind,
    activeField: PinFieldKind,
    onFocus: () -> Unit,
    palette: ChatRecoveryPalette,
) {
    val focusRequester = remember { FocusRequester() }
    val isActive = activeField == kind

    LaunchedEffect(isActive) { if (isActive) runCatching { focusRequester.requestFocus() } }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = palette.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)

        Box {
            // Campo real (transparente) que recibe teclado; las celdas son la representación visual.
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .alpha(0f)
                    .focusRequester(focusRequester),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                colors = TextFieldDefaults.colors(),
            )
            Row(
                Modifier.fillMaxWidth().height(54.dp).clickable { onFocus(); runCatching { focusRequester.requestFocus() } },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(PIN_LENGTH) { index ->
                    val filled = index < value.length
                    val focused = isActive && index == value.length
                    Box(
                        Modifier
                            .weight(1f)
                            .height(50.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (filled) palette.digitFillFilled else palette.digitFillEmpty)
                            .border(
                                width = if (focused) 1.5.dp else 1.dp,
                                color = when {
                                    focused -> palette.digitBorderFocused
                                    filled -> palette.digitBorderFilled
                                    else -> palette.digitBorderEmpty
                                },
                                shape = RoundedCornerShape(12.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (filled) {
                            Text("•", color = palette.digitText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Text(subtitle, color = palette.secondary, fontSize = 12.sp)
    }
}

/** Port de `ChatRecoveryPrimaryButtonStyle`. */
@Composable
private fun ChatRecoveryPrimaryButton(
    title: String,
    enabled: Boolean,
    palette: ChatRecoveryPalette,
    onClick: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isDark) Color.White else Color.Black)
            .alpha(if (enabled) 1f else 0.5f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title,
            color = if (isDark) Color.Black else Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Port de `ChatRecoveryBackdrop`. */
@Composable
private fun ChatRecoveryBackdropModifier(): Modifier {
    val isDark = isSystemInDarkTheme()
    return Modifier.background(if (isDark) Color.Black.copy(alpha = 0.62f) else Color.Black.copy(alpha = 0.35f))
}

private fun formattedLockoutDuration(seconds: Double): String {
    val total = kotlin.math.max(1, kotlin.math.ceil(seconds).toInt())
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}
