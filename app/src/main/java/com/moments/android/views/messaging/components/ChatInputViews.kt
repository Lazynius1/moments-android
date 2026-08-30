package com.moments.android.views.messaging.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import com.moments.android.views.components.MomentsCircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import com.moments.android.views.messaging.screens.chat.measureRootKeyboardBottomInsetPx
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import android.media.MediaPlayer
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.extensions.MomentsChromeGlass
import com.moments.android.services.performance.MotionPolicy
import com.moments.android.utilities.HapticManager
import com.moments.android.views.feed.AdaptiveColors
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.messaging.core.EnhancedMessage
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Compositor de chat Android: layout unificado, cromática Moments. */
enum class VoiceRecordingFloatingControlMode { LOCKING, PAUSE, PREPARING, RESUME }

private enum class ComposerTrailingMode { LOCKED_SEND, DRAFT_SEND, EDIT_APPLY, TEXT_SEND, VOICE, EMPTY }

/** Altura estándar del control del compositor. */
private val ComposerControlSize = 44.dp
private val composerFieldShape = RoundedCornerShape(20.dp)
private val unifiedComposerShape = RoundedCornerShape(24.dp)

/**
 * Resalta el contrato raw E2E sin alterar ni ocultar caracteres. Al conservar la
 * longitud exacta, selección, IME, undo y posición del cursor usan OffsetMapping.Identity.
 */
private class ChatComposerMarkupTransformation(
    private val primary: Color,
    private val secondary: Color,
    private val accent: Color,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val styled = AnnotatedString.Builder(text)
        val delimiter = SpanStyle(color = secondary.copy(alpha = 0.48f))

        fun applyDelimited(
            regex: Regex,
            contentStyle: SpanStyle,
            prefixLength: Int,
            suffixLength: Int = prefixLength,
        ) {
            regex.findAll(raw).forEach { match ->
                val start = match.range.first
                val endExclusive = match.range.last + 1
                val contentStart = start + prefixLength
                val contentEnd = endExclusive - suffixLength
                if (contentStart < contentEnd) {
                    styled.addStyle(contentStyle, contentStart, contentEnd)
                    styled.addStyle(delimiter, start, contentStart)
                    styled.addStyle(delimiter, contentEnd, endExclusive)
                }
            }
        }

        applyDelimited(
            Regex("""\*\*([^*]+)\*\*"""),
            SpanStyle(fontWeight = FontWeight.Bold, color = primary),
            prefixLength = 2,
        )
        applyDelimited(
            Regex("""(?<!\*)\*([^*]+)\*(?!\*)"""),
            SpanStyle(fontStyle = FontStyle.Italic, color = primary),
            prefixLength = 1,
        )
        applyDelimited(
            Regex("""~~([^~]+)~~"""),
            SpanStyle(textDecoration = TextDecoration.LineThrough, color = primary),
            prefixLength = 2,
        )
        applyDelimited(
            Regex("""`([^`]+)`"""),
            SpanStyle(fontFamily = FontFamily.Monospace, color = primary),
            prefixLength = 1,
        )
        applyDelimited(
            Regex("""\|\|([^|]+)\|\|"""),
            SpanStyle(
                color = primary,
                background = accent.copy(alpha = 0.16f),
                fontWeight = FontWeight.Medium,
            ),
            prefixLength = 2,
        )

        Regex("""https?://[^\s]+""", RegexOption.IGNORE_CASE).findAll(raw).forEach { match ->
            styled.addStyle(
                SpanStyle(color = accent, textDecoration = TextDecoration.Underline),
                match.range.first,
                match.range.last + 1,
            )
        }
        Regex("""(?<![\p{L}\p{N}_])@[\p{L}\p{N}._]+""").findAll(raw).forEach { match ->
            styled.addStyle(
                SpanStyle(color = accent, fontWeight = FontWeight.SemiBold),
                match.range.first,
                match.range.last + 1,
            )
        }
        Regex("""(?m)^> ?.*$""").findAll(raw).forEach { match ->
            val start = match.range.first
            val endExclusive = match.range.last + 1
            styled.addStyle(
                SpanStyle(color = secondary, fontStyle = FontStyle.Italic),
                start,
                endExclusive,
            )
            val markerEnd = min(endExclusive, start + if (raw.startsWith("> ", start)) 2 else 1)
            styled.addStyle(
                SpanStyle(color = accent, fontWeight = FontWeight.Bold),
                start,
                markerEnd,
            )
        }

        return TransformedText(styled.toAnnotatedString(), OffsetMapping.Identity)
    }
}

@Composable
fun GlassmorphicInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    isRecordingVoice: Boolean,
    isVoiceRecordingLocked: Boolean,
    recordingSeconds: Double,
    recordingInteractionId: String?,
    voiceRecordingDraft: VoiceRecordingDraft?,
    isPreparingVoiceRecordingPreview: Boolean,
    replyingTo: EnhancedMessage? = null,
    editingMessage: EnhancedMessage? = null,
    otherParticipantName: String = "",
    voiceGestureState: VoiceRecordingGestureState,
    isKeyboardVisible: Boolean = false,
    isVanishModeActive: Boolean = false,
    allowsAttachments: Boolean = true,
    allowsVoiceRecording: Boolean = true,
    isAttachmentMenuOpen: Boolean = false,
    onSend: () -> Unit,
    onCancelReply: () -> Unit = {},
    onCancelEdit: () -> Unit = {},
    onOpenAttachments: () -> Unit,
    onAttachmentPlusAnchorBoundsChanged: (androidx.compose.ui.unit.IntRect) -> Unit = {},
    onVoiceButtonAnchorBoundsChanged: (androidx.compose.ui.unit.IntRect) -> Unit = {},
    onStartVoiceRecording: (interactionId: String, startsLocked: Boolean) -> Unit,
    onFinishVoiceRecording: (interactionId: String, action: VoiceRecordingFinishAction) -> Unit,
    onVoiceRecordingTrimChanged: (ClosedFloatingPointRange<Double>) -> Unit = {},
    onLockChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    val isDark = isSystemInDarkTheme()
    val audioPower by AudioRecordingManager.shared.audioPower.collectAsState()
    val showingDraft = voiceRecordingDraft != null || isPreparingVoiceRecordingPreview
    val composerAccent = colors.userAccentColor
    val composerMarkupTransformation = remember(colors.primary, colors.secondary, composerAccent) {
        ChatComposerMarkupTransformation(colors.primary, colors.secondary, composerAccent)
    }
    val panelBg = colors.chatInputBackground
    val fieldFill = if (isDark) Color(0xFF0B1215) else Color(0xFFF4F5F5)
    val vanishStroke = if (isDark) Color.White.copy(alpha = 0.28f) else Color.Black.copy(alpha = 0.22f)
    val focusRequester = remember { FocusRequester() }
    val focusInteractionSource = remember { MutableInteractionSource() }
    var isTextFieldFocused by remember { mutableStateOf(false) }
    var hadActiveVoiceRecording by remember { mutableStateOf(isRecordingVoice) }
    val scope = rememberCoroutineScope()
    val separatesCancelTrashCircle =
        voiceGestureState.playDeleteAnimation || voiceGestureState.isTrashMorphingToPlus
    val separatesLeadingControl = showingDraft || separatesCancelTrashCircle
    val usesCompactKeyboardChrome = isKeyboardVisible ||
        voiceGestureState.preserveKeyboardElevation
    val returnsToUnifiedComposerAfterTrash =
        isTextFieldFocused || isKeyboardVisible || text.isNotEmpty() ||
            voiceGestureState.preserveKeyboardElevation
    val showsLeadingPlusButton = !isRecordingVoice && editingMessage == null && allowsAttachments &&
        !voiceGestureState.playDeleteAnimation && !voiceGestureState.isTrashMorphingToPlus
    val showsComposerTextInput = !showingDraft
    val keyboardController = LocalSoftwareKeyboardController.current
    val rootView = LocalView.current
    fun trashToPlusMorphOffset(): Dp {
        if (!separatesCancelTrashCircle || !returnsToUnifiedComposerAfterTrash) return 0.dp
        // La fila fusionada recupera el hueco del control separado; el centro final
        // del + solo conserva los 4dp de padding interior.
        return 4.dp
    }
    val chromeFill = MomentsChromeGlass.canvasTint(isDark)
    val chromeStroke = MomentsChromeGlass.strokeColor(isDark)
    val trailingMode = when {
        isVoiceRecordingLocked -> ComposerTrailingMode.LOCKED_SEND
        showingDraft -> ComposerTrailingMode.DRAFT_SEND
        editingMessage != null -> ComposerTrailingMode.EDIT_APPLY
        text.isNotBlank() -> ComposerTrailingMode.TEXT_SEND
        allowsAttachments && allowsVoiceRecording -> ComposerTrailingMode.VOICE
        else -> ComposerTrailingMode.EMPTY
    }

    fun sendCurrentContent() {
        if (recordingInteractionId != null && (voiceRecordingDraft != null || isRecordingVoice)) {
            onFinishVoiceRecording(recordingInteractionId, VoiceRecordingFinishAction.SEND)
        } else {
            onSend()
        }
    }

    fun cancelVoiceRecording() {
        recordingInteractionId?.let { id ->
            if (showingDraft) {
                voiceGestureState.trashAnimationCompletionHandler = null
                voiceGestureState.playDeleteAnimation = true
            }
            onFinishVoiceRecording(id, VoiceRecordingFinishAction.CANCEL)
        }
    }

    LaunchedEffect(voiceGestureState.preserveKeyboardElevation) {
        if (voiceGestureState.preserveKeyboardElevation) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    LaunchedEffect(isRecordingVoice, voiceGestureState.preserveKeyboardElevation) {
        if (voiceGestureState.preserveKeyboardElevation) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
        // No limpiar durante los 150 ms del long-press: en ese intervalo el gesto ya
        // ha fijado el IME pero el recorder todavia no ha publicado isRecording=true.
        if (hadActiveVoiceRecording && !isRecordingVoice) {
            voiceGestureState.preserveKeyboardElevation = false
            voiceGestureState.pinnedKeyboardBottomPx = 0
        }
        hadActiveVoiceRecording = isRecordingVoice
    }

    LaunchedEffect(editingMessage?.id) {
        if (editingMessage != null) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    // Franja unificada: controles 44dp, campo plano al centro.
    Column(
        modifier
            .fillMaxWidth()
            .background(panelBg),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.06f)),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 14.dp,
                    vertical = if (usesCompactKeyboardChrome) 2.dp else 4.dp,
                ),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(if (separatesLeadingControl) 10.dp else 0.dp),
        ) {
            if (separatesLeadingControl) {
                Box(
                    Modifier
                        .size(ComposerControlSize)
                        .zIndex(2f),
                    contentAlignment = Alignment.Center,
                ) {
                    if (separatesCancelTrashCircle) {
                        VoiceRecordingTrashIndicator(
                            morphProgress = voiceGestureState.trashMorphProgress,
                            morphOffsetX = trashToPlusMorphOffset(),
                            onLottieFinished = {
                                voiceGestureState.startTrashMorphToPlus(scope) {
                                    voiceGestureState.trashAnimationCompletionHandler?.invoke()
                                    voiceGestureState.trashAnimationCompletionHandler = null
                                }
                            },
                        )
                    } else {
                        InputCircleButton(
                            icon = Icons.Default.Delete,
                            tint = Color(0xFFFF3B30),
                            contentDescription = stringResource(R.string.common_cancel),
                            onClick = ::cancelVoiceRecording,
                        )
                    }
                }
            }

            Row(
                Modifier
                    .weight(1f)
                    .clip(unifiedComposerShape)
                    .background(chromeFill, unifiedComposerShape)
                    .border(
                        MomentsChromeGlass.strokeWidth,
                        chromeStroke,
                        unifiedComposerShape,
                    )
                    .padding(if (usesCompactKeyboardChrome) 0.dp else 2.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                if (!separatesLeadingControl && showsLeadingPlusButton) {
                    Box(
                        Modifier
                            .size(ComposerControlSize)
                            .zIndex(2f),
                        contentAlignment = Alignment.Center,
                    ) {
                        ChatAttachmentPlusButton(
                            isMenuOpen = isAttachmentMenuOpen,
                            onClick = onOpenAttachments,
                            onAnchorBoundsChanged = onAttachmentPlusAnchorBoundsChanged,
                            flat = true,
                        )
                    }
                }

                Column(
                    Modifier
                        .weight(1f)
                        .zIndex(1f)
                        .heightIn(min = ComposerControlSize)
                        .clip(composerFieldShape)
                        .background(
                            if (isVanishModeActive) fieldFill
                            else Color.Transparent,
                            composerFieldShape,
                        )
                        .border(
                            MomentsChromeGlass.strokeWidth,
                            if (isVanishModeActive) chromeStroke else Color.Transparent,
                            composerFieldShape,
                        )
                        .then(
                            if (isVanishModeActive) {
                                Modifier.drawWithContent {
                                    drawContent()
                                    drawRoundRect(
                                        color = vanishStroke,
                                        cornerRadius = CornerRadius(20.dp.toPx()),
                                        style = Stroke(
                                            width = 1.2.dp.toPx(),
                                            pathEffect = PathEffect.dashPathEffect(
                                                floatArrayOf(5.dp.toPx(), 4.dp.toPx()),
                                            ),
                                        ),
                                    )
                                }
                            } else {
                                Modifier
                            },
                        )
                        .animateContentSize(),
                    content = {
                        val contextMessage = editingMessage ?: replyingTo
                        contextMessage?.let { message ->
                            ChatComposerReplyHeader(
                                message = message,
                                otherParticipantName = otherParticipantName,
                                mode = if (editingMessage != null) {
                                    ChatComposerContextMode.Edit
                                } else {
                                    ChatComposerContextMode.Reply
                                },
                                onCancel = if (editingMessage != null) onCancelEdit else onCancelReply,
                            )
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(0.5.dp)
                                    .background(if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.07f)),
                            )
                        }

                        Box(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = ComposerControlSize)
                                .clickable(
                                    interactionSource = focusInteractionSource,
                                    indication = null,
                                    enabled = !isRecordingVoice && !showingDraft,
                                ) {
                                    focusRequester.requestFocus()
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (showsComposerTextInput) {
                                BasicTextField(
                                    value = text,
                                    // Mantener el input connection vivo durante la grabacion.
                                    // Telegram oculta el editor bajo su record panel, pero no lo
                                    // vuelve read-only: hacerlo cerraba el IME y bajaba el composer.
                                    onValueChange = { updated ->
                                        if (!isRecordingVoice) onTextChange(updated)
                                    },
                                    textStyle = TextStyle(color = colors.primary, fontSize = 16.sp),
                                    visualTransformation = composerMarkupTransformation,
                                    cursorBrush = SolidColor(composerAccent),
                                    maxLines = 6,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .alpha(if (isRecordingVoice) 0f else 1f)
                                        .focusRequester(focusRequester)
                                        .onFocusChanged { isTextFieldFocused = it.isFocused },
                                    decorationBox = { inner ->
                                        if (text.isBlank() && !isRecordingVoice) {
                                            Text(
                                                stringResource(
                                                    if (isVanishModeActive) R.string.chat_input_vanish_placeholder
                                                    else R.string.chat_input_placeholder,
                                                ),
                                                color = colors.secondary.copy(alpha = 0.65f),
                                                fontSize = 16.sp,
                                            )
                                        }
                                        inner()
                                    },
                                )
                            }

                            if (isRecordingVoice) {
                                VoiceRecordingHeldStatus(
                                    isLocked = isVoiceRecordingLocked,
                                    recordingSeconds = recordingSeconds.toLong(),
                                    cancelDragOffsetPx = voiceGestureState.cancelDragOffset,
                                    cancelProgress = voiceGestureState.cancelProgress,
                                    colors = colors,
                                    onCancel = ::cancelVoiceRecording,
                                )
                            } else if (showingDraft) {
                                VoiceRecordingDraftPreview(
                                    draft = voiceRecordingDraft,
                                    fallbackDurationSeconds = recordingSeconds,
                                    isPreparing = isPreparingVoiceRecordingPreview,
                                    colors = colors,
                                    onTrimChanged = onVoiceRecordingTrimChanged,
                                )
                            }
                        }
                    },
                )

                Box(
                    Modifier
                        .size(ComposerControlSize)
                        .zIndex(2f),
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedContent(
                        targetState = trailingMode,
                        transitionSpec = {
                            (fadeIn(tween(140)) + scaleIn(tween(180), initialScale = 0.78f))
                                .togetherWith(
                                    fadeOut(tween(110)) + scaleOut(tween(140), targetScale = 0.78f),
                                )
                        },
                        label = "chatComposerTrailingControl",
                    ) { mode ->
                        when (mode) {
                            ComposerTrailingMode.LOCKED_SEND -> VoiceRecordingLockedSendButton(
                                accent = composerAccent,
                                onClick = ::sendCurrentContent,
                            )
                            ComposerTrailingMode.DRAFT_SEND -> ComposerFlatSendButton(
                                accent = composerAccent,
                                enabled = !isPreparingVoiceRecordingPreview,
                                dimmed = isPreparingVoiceRecordingPreview,
                                onClick = ::sendCurrentContent,
                            )
                            ComposerTrailingMode.EDIT_APPLY -> ComposerFlatApplyButton(
                                accent = composerAccent,
                                enabled = text.isNotBlank(),
                                onClick = ::sendCurrentContent,
                            )
                            ComposerTrailingMode.TEXT_SEND -> ComposerFlatSendButton(
                                accent = composerAccent,
                                onClick = ::sendCurrentContent,
                            )
                            ComposerTrailingMode.VOICE -> VoiceRecordingGestureButton(
                                tint = colors.mediaIconColor,
                                isRecording = isRecordingVoice,
                                activeInteractionId = recordingInteractionId,
                                isLocked = isVoiceRecordingLocked,
                                gestureState = voiceGestureState,
                                glassInteractive = false,
                                standaloneChrome = false,
                                audioPower = audioPower,
                                onStart = onStartVoiceRecording,
                                onFinish = onFinishVoiceRecording,
                                onLockChanged = onLockChanged,
                                onPressBegan = {
                                    // Solo elevar si el teclado ya está visible; foco sin IME
                                    // no debe abrir teclado ni subir el compositor al grabar abajo.
                                    if (!isKeyboardVisible) return@VoiceRecordingGestureButton
                                    val keyboardInsetPx = measureRootKeyboardBottomInsetPx(rootView)
                                    voiceGestureState.preserveKeyboardElevation = true
                                    voiceGestureState.pinnedKeyboardBottomPx = keyboardInsetPx
                                    focusRequester.requestFocus()
                                    keyboardController?.show()
                                },
                                onAnchorBoundsChanged = onVoiceButtonAnchorBoundsChanged,
                            )
                            ComposerTrailingMode.EMPTY -> Unit
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComposerFlatIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit,
    contentDescription: String,
) {
    Box(
        Modifier
            .size(ComposerControlSize)
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun ComposerFlatSendButton(
    accent: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
    dimmed: Boolean = false,
) {
    val sendLabel = stringResource(R.string.notification_action_send)
    Box(
        Modifier
            .size(ComposerControlSize)
            .graphicsLayer { alpha = if (dimmed) 0.45f else 1f }
            .clip(CircleShape)
            .background(accent)
            .semantics { contentDescription = sendLabel }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.Send,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun ComposerFlatApplyButton(
    accent: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val applyLabel = stringResource(R.string.chat_editing_title)
    Box(
        Modifier
            .size(ComposerControlSize)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .clip(CircleShape)
            .background(accent)
            .semantics { contentDescription = applyLabel }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.Check,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(19.dp),
        )
    }
}

@Composable
fun VoiceRecordingFloatingControlHost(
    isRecording: Boolean,
    isLocked: Boolean,
    isPreparing: Boolean,
    hasDraft: Boolean,
    hasActiveInteraction: Boolean,
    gestureState: VoiceRecordingGestureState,
    primaryTint: Color,
    accentTint: Color,
    onPause: () -> Unit,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mode = when {
        isLocked -> VoiceRecordingFloatingControlMode.PAUSE
        isRecording -> VoiceRecordingFloatingControlMode.LOCKING
        isPreparing && hasActiveInteraction -> VoiceRecordingFloatingControlMode.PREPARING
        hasDraft && hasActiveInteraction -> VoiceRecordingFloatingControlMode.RESUME
        else -> null
    }
    Box(
        modifier.size(width = 44.dp, height = 72.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        if (mode != null) {
            // ≡ iOS rideAlongOffsetY solo en locking (px → offset)
            VoiceRecordingFloatingControl(
                mode = mode,
                lockProgress = gestureState.lockProgress,
                primaryTint = primaryTint,
                accentTint = accentTint,
                onPause = onPause,
                onResume = onResume,
                modifier = Modifier.offset {
                    val y = if (mode == VoiceRecordingFloatingControlMode.LOCKING) {
                        gestureState.followY.roundToInt()
                    } else {
                        0
                    }
                    IntOffset(0, y)
                },
            )
        }
    }
}

@Composable
fun VoiceRecordingFloatingControl(
    mode: VoiceRecordingFloatingControlMode,
    primaryTint: Color,
    accentTint: Color,
    onPause: () -> Unit,
    onResume: () -> Unit,
    lockProgress: Float = 1f,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val interactive = mode == VoiceRecordingFloatingControlMode.PAUSE || mode == VoiceRecordingFloatingControlMode.RESUME
    val progress = lockProgress.coerceIn(0f, 1f)
    val height = if (mode == VoiceRecordingFloatingControlMode.LOCKING) {
        (72f - progress * 28f).dp
    } else {
        44.dp
    }
    val tint = if (mode == VoiceRecordingFloatingControlMode.RESUME) accentTint else primaryTint
    val a11y = when (mode) {
        VoiceRecordingFloatingControlMode.LOCKING -> stringResource(R.string.chat_voice_record_locked)
        VoiceRecordingFloatingControlMode.PAUSE -> stringResource(R.string.chat_voice_record_pause)
        VoiceRecordingFloatingControlMode.PREPARING -> stringResource(R.string.common_loading)
        VoiceRecordingFloatingControlMode.RESUME -> stringResource(R.string.chat_voice_record_resume)
    }
    // Lenguaje Android propio: surface tonal opaca y elevada. La geometría sigue
    // el control dibujado de Telegram, pero sin copiar el glass/aurora de iOS.
    val baseSurface = MomentsChromeGlass.canvasTint(isDark)
    val targetFill = when (mode) {
        VoiceRecordingFloatingControlMode.LOCKING ->
            tint.copy(alpha = 0.10f + progress * 0.08f).compositeOver(baseSurface)
        VoiceRecordingFloatingControlMode.PAUSE,
        VoiceRecordingFloatingControlMode.RESUME -> tint.copy(alpha = 0.14f).compositeOver(baseSurface)
        VoiceRecordingFloatingControlMode.PREPARING ->
            primaryTint.copy(alpha = 0.08f).compositeOver(baseSurface)
    }
    val fill by animateColorAsState(
        targetValue = targetFill,
        animationSpec = tween(180),
        label = "voiceFloatingControlFill",
    )
    val controlShape = RoundedCornerShape(50)
    Box(
        modifier
            .width(44.dp)
            .height(height)
            .shadow(
                elevation = if (interactive) 4.dp else 2.dp,
                shape = controlShape,
                clip = false,
            )
            .clip(controlShape)
            .background(fill, controlShape)
            .border(
                MomentsChromeGlass.strokeWidth,
                MomentsChromeGlass.strokeColor(isDark),
                controlShape,
            )
            .semantics { contentDescription = a11y }
            .clickable(enabled = interactive) {
                when (mode) {
                    VoiceRecordingFloatingControlMode.PAUSE -> onPause()
                    VoiceRecordingFloatingControlMode.RESUME -> onResume()
                    else -> Unit
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = mode,
            transitionSpec = {
                (fadeIn(tween(150)) + scaleIn(tween(190), initialScale = 0.72f))
                    .togetherWith(
                        fadeOut(tween(110)) + scaleOut(tween(150), targetScale = 0.72f),
                    )
            },
            label = "voiceFloatingControlMode",
        ) { displayedMode ->
            when (displayedMode) {
                VoiceRecordingFloatingControlMode.LOCKING -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(Icons.Default.Lock, null, tint = tint, modifier = Modifier.size(17.dp))
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        null,
                        tint = tint.copy(alpha = max(0f, 0.9f - progress)),
                        modifier = Modifier.size(12.dp),
                    )
                }
                VoiceRecordingFloatingControlMode.PAUSE -> Icon(
                    Icons.Default.Pause,
                    null,
                    tint = tint,
                    modifier = Modifier.size(17.dp),
                )
                VoiceRecordingFloatingControlMode.RESUME -> Icon(
                    Icons.Default.Mic,
                    null,
                    tint = tint,
                    modifier = Modifier.size(17.dp),
                )
                VoiceRecordingFloatingControlMode.PREPARING -> CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = tint,
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}

/** Android locked send: círculo sólido accent + send (no aura/mesh iOS). */
@Composable
private fun VoiceRecordingLockedSendButton(
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sendLabel = stringResource(R.string.notification_action_send)
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(accent)
            .semantics { contentDescription = sendLabel }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.Send,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun VoiceRecordingRecordDot(modifier: Modifier = Modifier) {
    val reduceMotion = MotionPolicy.reduceMotion
    var pulseLow by remember { mutableStateOf(false) }
    val dotAlpha by animateFloatAsState(
        targetValue = if (reduceMotion || !pulseLow) 1f else 0.35f,
        animationSpec = tween(500),
        label = "voiceRecordDotPulse",
    )
    LaunchedEffect(reduceMotion) {
        pulseLow = false
        if (reduceMotion) return@LaunchedEffect
        while (isActive) {
            pulseLow = false
            delay(500)
            pulseLow = true
            delay(500)
        }
    }
    Box(
        modifier
            .size(9.dp)
            .clip(CircleShape)
            .background(Color.Red.copy(alpha = dotAlpha)),
    )
}

@Composable
private fun VoiceRecordingSlideToCancelHint(
    cancelDragOffsetPx: Float,
    cancelProgress: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val reduceMotion = MotionPolicy.reduceMotion
    var bounceOffset by remember { mutableFloatStateOf(0f) }
    val slideOffset = with(density) { (cancelDragOffsetPx * 0.55f + bounceOffset).toDp() }
    val slideOpacity = (1f - cancelProgress * 1.15f).coerceAtLeast(0f)
    LaunchedEffect(cancelProgress, slideOpacity, reduceMotion) {
        if (reduceMotion || cancelProgress > 0.2f || slideOpacity < 0.5f) {
            bounceOffset = 0f
            return@LaunchedEffect
        }
        while (isActive) {
            bounceOffset = with(density) { 6.dp.toPx() }
            delay(750)
            bounceOffset = with(density) { (-6).dp.toPx() }
            delay(750)
        }
    }
    Row(
        modifier
            .offset(x = slideOffset)
            .graphicsLayer { alpha = slideOpacity },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            Icons.Default.KeyboardArrowLeft,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(10.dp),
        )
        Text(
            stringResource(R.string.chat_voice_slide_to_cancel),
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
fun VoiceRecordingHeldStatus(
    isLocked: Boolean,
    recordingSeconds: Long,
    colors: AdaptiveColors,
    onCancel: () -> Unit,
    cancelDragOffsetPx: Float = 0f,
    cancelProgress: Float = 0f,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(end = 46.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (!isLocked) {
            VoiceRecordingRecordDot()
        }
        Text(
            formatVoiceTime(recordingSeconds),
            color = colors.primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.weight(1f))
        if (isLocked) {
            Text(
                stringResource(R.string.common_cancel),
                color = colors.userAccentColor,
                modifier = Modifier.clickable(onClick = onCancel),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        } else {
            VoiceRecordingSlideToCancelHint(
                cancelDragOffsetPx = cancelDragOffsetPx,
                cancelProgress = cancelProgress,
                color = colors.timestampColor,
            )
        }
    }
}

@Composable
private fun InputCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    contentDescription: String? = null,
    filled: Boolean = false,
    enabled: Boolean = true,
    preparingDim: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(ComposerControlSize)
            .graphicsLayer { alpha = if (preparingDim) 0.45f else 1f }
            .clip(CircleShape)
            .then(if (filled) Modifier.background(tint) else Modifier.momentsChromeGlass(CircleShape, interactive = enabled))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription,
            tint = if (filled) Color.White else tint,
            modifier = Modifier.size(18.dp),
        )
    }
}

private fun formatVoiceTime(seconds: Long): String = "%02d:%02d".format(seconds / 60, seconds % 60)

@Composable
fun VoiceRecordingDraftPreview(
    draft: VoiceRecordingDraft?,
    fallbackDurationSeconds: Double,
    isPreparing: Boolean,
    onTrimChanged: (ClosedFloatingPointRange<Double>) -> Unit,
    colors: AdaptiveColors = rememberAdaptiveColors(),
    modifier: Modifier = Modifier,
) {
    val fullDuration = draft?.fullDuration?.takeIf { it > 0 } ?: fallbackDurationSeconds
    var workingTrim by remember(draft?.trimRangeSeconds, fullDuration) {
        mutableStateOf(draft?.normalizedTrimRange ?: (0.0..max(fullDuration, 0.0)))
    }
    var dragOrigin by remember { mutableStateOf<ClosedFloatingPointRange<Double>?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val player = remember { MediaPlayer() }
    val onTrimChangedState = rememberUpdatedState(onTrimChanged)

    DisposableEffect(Unit) {
        onDispose {
            runCatching { player.stop() }
            runCatching { player.release() }
        }
    }

    LaunchedEffect(draft?.recording?.data) {
        val data = draft?.recording?.data ?: return@LaunchedEffect
        runCatching {
            val file = File.createTempFile("voice_draft_", ".m4a")
            file.writeBytes(data)
            player.reset()
            player.setDataSource(file.absolutePath)
            player.prepare()
            file.delete()
        }
    }

    LaunchedEffect(isPlaying, workingTrim) {
        if (!isPlaying) return@LaunchedEffect
        while (isActive && isPlaying) {
            val startMs = (workingTrim.start * 1000).toInt()
            val endMs = (workingTrim.endInclusive * 1000).toInt()
            val current = player.currentPosition
            val durationMs = player.duration.coerceAtLeast(1)
            if (current >= endMs) {
                player.pause()
                player.seekTo(startMs)
                isPlaying = false
                // ≡ iOS: progress = currentTime / duration (absoluto)
                progress = startMs.toFloat() / durationMs
                break
            }
            progress = (current.toFloat() / durationMs).coerceIn(0f, 1f)
            delay(50)
        }
    }

    fun displaySeconds(): Int {
        // ≡ iOS displayTime: elapsed en trim si playing/progress; si no, duración del draft
        val duration = draft?.durationSeconds ?: fallbackDurationSeconds
        val elapsed = if (isPlaying || progress > 0f) {
            val currentSec = progress * (player.duration.takeIf { it > 0 }?.div(1000.0) ?: fullDuration)
            max(0.0, currentSec - workingTrim.start)
        } else {
            duration
        }
        return max(0, elapsed.toInt())
    }

    Row(
        modifier.fillMaxWidth().heightIn(min = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(colors.timestampColor.copy(alpha = 0.10f))
                .clickable(enabled = !isPreparing && draft?.recording != null) {
                    if (isPlaying) {
                        player.pause()
                        isPlaying = false
                    } else {
                        player.seekTo((workingTrim.start * 1000).toInt())
                        player.start()
                        isPlaying = true
                    }
                }
                .padding(horizontal = 6.dp)
                .height(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (isPreparing || draft?.recording == null) {
                MomentsCircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                )
            } else {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = stringResource(if (isPlaying) R.string.chat_voice_pause else R.string.chat_voice_play),
                    tint = colors.primary.copy(alpha = 0.82f),
                    modifier = Modifier.size(12.dp),
                )
            }
            val secs = displaySeconds()
            Text(
                stringResource(R.string.chat_voice_duration, secs / 60, secs % 60),
                color = colors.primary.copy(alpha = 0.82f),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
            )
        }

        BoxWithConstraints(Modifier.weight(1f).height(26.dp)) {
            val widthPx = with(density) { maxWidth.toPx() }
            fun fraction(time: Double): Float =
                if (fullDuration <= 0) 0f else (time / fullDuration).toFloat().coerceIn(0f, 1f)

            val lowerX = fraction(workingTrim.start) * widthPx
            val upperX = fraction(workingTrim.endInclusive) * widthPx
            val samples = remember(draft?.waveform, widthPx) {
                val source = draft?.waveform?.takeIf { it.isNotEmpty() } ?: List(16) { 0.22f }
                ChatVoiceWaveformSamples.resampled(
                    source,
                    max(18, min(64, (widthPx / 4.5f).toInt())),
                )
            }

            VisualWaveformView(
                levels = samples,
                color = colors.timestampColor.copy(alpha = 0.45f),
                activeColor = colors.primary.copy(alpha = 0.82f),
                progress = progress,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(fullDuration, workingTrim) {
                        detectDragGestures(
                            onDrag = { change, _ ->
                                change.consume()
                                if (player.duration <= 0 || fullDuration <= 0) return@detectDragGestures
                                val frac = (change.position.x / widthPx).coerceIn(0f, 1f)
                                val requested = player.duration * frac
                                val lo = (workingTrim.start * 1000).toInt()
                                val hi = (workingTrim.endInclusive * 1000).toInt()
                                val clamped = requested.toInt().coerceIn(lo, hi)
                                player.seekTo(clamped)
                                progress = clamped.toFloat() / player.duration
                            },
                        )
                    },
            )

            Box(
                Modifier
                    .fillMaxHeight()
                    .width(with(density) { lowerX.toDp() })
                    .background(colors.background.copy(alpha = 0.58f))
                    .align(Alignment.CenterStart),
            )
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(with(density) { (widthPx - upperX).coerceAtLeast(0f).toDp() })
                    .background(colors.background.copy(alpha = 0.58f))
                    .align(Alignment.CenterEnd),
            )

            Canvas(Modifier.fillMaxSize()) {
                drawRoundRect(
                    color = colors.primary.copy(alpha = 0.58f),
                    topLeft = Offset(lowerX, 0f),
                    size = androidx.compose.ui.geometry.Size(max(1f, upperX - lowerX), size.height),
                    cornerRadius = CornerRadius(5.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx()),
                )
            }

            listOf(true to lowerX, false to upperX).forEach { (isLeading, x) ->
                Box(
                    Modifier
                        .offset(x = with(density) { x.toDp() } - 1.5.dp)
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(colors.primary.copy(alpha = 0.88f), RoundedCornerShape(50))
                        .pointerInput(fullDuration, isLeading) {
                            detectDragGestures(
                                onDragStart = {
                                    dragOrigin = workingTrim
                                    if (isPlaying) {
                                        player.pause()
                                        isPlaying = false
                                    }
                                },
                                onDragEnd = {
                                    onTrimChangedState.value(workingTrim)
                                    dragOrigin = null
                                    HapticManager.shared.selection()
                                },
                                onDragCancel = { dragOrigin = null },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val origin = dragOrigin ?: return@detectDragGestures
                                    if (fullDuration <= 0) return@detectDragGestures
                                    val proposed = (change.position.x / widthPx * fullDuration)
                                        .coerceIn(0.0, fullDuration)
                                    val minDur = min(2.0, fullDuration)
                                    workingTrim = if (isLeading) {
                                        val lower = min(origin.endInclusive - minDur, proposed)
                                        lower..origin.endInclusive
                                    } else {
                                        val upper = max(origin.start + minDur, proposed)
                                        origin.start..upper
                                    }
                                },
                            )
                        },
                )
            }
        }
    }
}
