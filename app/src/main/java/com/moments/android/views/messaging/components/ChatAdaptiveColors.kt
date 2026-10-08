package com.moments.android.views.messaging.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.moments.android.views.feed.AdaptiveColors
import kotlin.math.max
import kotlin.math.min

/** Port de `Views/Messaging/Components/ChatAdaptiveColors.swift`. */
val LocalChatOutgoingBubbleColor = staticCompositionLocalOf { Color(0xFF3F6F8F) }
/**
 * Fondo de tarjetas SALIENTES con panel propio (momento, perfil, ubicación, «ver una vez»…):
 * el color de burbuja del chat; `null` en recibidas y fuera del chat (tarjetas neutras).
 * Lo provee `GlassmorphicMessageBubble` solo cuando el mensaje es del usuario actual.
 */
val LocalChatOutgoingCardTint = staticCompositionLocalOf<Color?> { null }
/** Color de textos sueltos sobre fondo personalizado; `null` con el fondo por defecto (≡ iOS `chatFloatingTextColor`). */
val LocalChatFloatingTextColor = staticCompositionLocalOf<Color?> { null }
val LocalChatMessageRowFrame = staticCompositionLocalOf { Rect.Zero }
val LocalChatMessageBubbleFrame = staticCompositionLocalOf { Rect.Zero }
val LocalChatMessageBubbleCornerRadius = staticCompositionLocalOf { 16f }

val AdaptiveColors.chatInputBackground: Color
    get() = controlSurface

val AdaptiveColors.chatNavigationBackground: Color
    get() = controlSurface

val AdaptiveColors.searchBarStroke: Color
    get() = if (isDark) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.2f)

val AdaptiveColors.mediaIconColor: Color
    get() = if (isDark) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.7f)

val AdaptiveColors.recordingIndicator: Color
    get() = if (isDark) Color.White else Color.Black

val AdaptiveColors.messageBubbleBackground: Color
    get() = if (isDark) Color(0xFF2C3235) else Color(0xFFE9E9E6)

/** ≡ iOS: borde casi imperceptible; en claro el relleno ya contrasta con el fondo. */
val AdaptiveColors.messageBubbleStroke: Color
    get() = if (isDark) Color.White.copy(alpha = 0.06f) else Color.Transparent

/** ≡ iOS `mediaBubbleStroke`: borde fino de miniaturas de foto/vídeo (sustituye a la sombra). */
val AdaptiveColors.mediaBubbleStroke: Color
    get() = if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f)

/** Paleta de una tarjeta saliente teñida: fondo = color de burbuja, texto por contraste (blanco/negro). */
data class ChatOutgoingCardPalette(
    val background: Color,
    val primary: Color,
    val secondary: Color,
    val stroke: Color,
)

/** Paleta saliente si la tarjeta debe teñirse (ver [LocalChatOutgoingCardTint]); `null` = mantener neutra. */
@Composable
@ReadOnlyComposable
fun chatOutgoingCardPalette(): ChatOutgoingCardPalette? = LocalChatOutgoingCardTint.current?.let { fill ->
    val on = chatBubbleTextColor(fill)
    ChatOutgoingCardPalette(background = fill, primary = on, secondary = on.copy(alpha = 0.7f), stroke = on.copy(alpha = 0.12f))
}

/** Ratio de contraste WCAG 2.x entre dos colores opacos (1…21). */
fun chatContrastRatio(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
}

/**
 * Acento de mensajes RECIBIDOS (enlaces, menciones, play/onda/velocidad de notas de voz) derivado
 * del color de burbuja del chat. Garantiza contraste ≥ [minContrast] (3:1, umbral WCAG para
 * componentes gráficos y enlaces subrayados) contra `messageBubbleBackground` del modo actual:
 * si no llega, mezcla el color hacia negro (claro) o blanco (oscuro) en pasos del 5 % —en Oklab,
 * conserva el tono— y devuelve el primer resultado que cumple. Si ya cumple, sale intacto.
 * Con el color por defecto 3F6F8F: claro sin cambio (≈4.4:1); oscuro se aclara ~15–20 %.
 */
fun chatReceivedAccentColor(bubbleColor: Color, isDark: Boolean, minContrast: Float = 3f): Color {
    val background = AdaptiveColors(isDark).messageBubbleBackground
    val target = if (isDark) Color.White else Color.Black
    val base = bubbleColor.copy(alpha = 1f)
    for (step in 0..20) {
        val candidate = lerp(base, target, step * 0.05f)
        if (chatContrastRatio(candidate, background) >= minContrast) return candidate
    }
    return target
}

/** [chatReceivedAccentColor] con el color de burbuja del chat actual. */
@Composable
@ReadOnlyComposable
fun chatReceivedAccent(isDark: Boolean): Color = chatReceivedAccentColor(LocalChatOutgoingBubbleColor.current, isDark)

/** ≡ iOS `Color.blue` / systemBlue (no Material `Color.Blue`). */
fun chatSystemBlue(isDark: Boolean): Color = if (isDark) Color(0xFF0A84FF) else Color(0xFF007AFF)

val AdaptiveColors.messageTextColor: Color
    get() = if (isDark) Color.White else Color.Black

val AdaptiveColors.timestampColor: Color
    get() = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.5f)

val AdaptiveColors.dateHeaderColor: Color
    get() = if (isDark) Color.White.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.7f)

val AdaptiveColors.typingIndicatorColor: Color
    get() = if (isDark) Color.White.copy(alpha = 0.82f) else Color.Black.copy(alpha = 0.42f)

val AdaptiveColors.replyBarBackground: Color
    get() = if (isDark) Color(0xFFFAF9F6).copy(alpha = 0.1f) else Color(0xFF0B1215).copy(alpha = 0.05f)

val AdaptiveColors.replyBarText: Color
    get() = if (isDark) Color.White else Color.Black

val AdaptiveColors.replyBarSecondaryText: Color
    get() = if (isDark) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.6f)

val AdaptiveColors.userAccentColor: Color
    get() = Color(0xFF3F6F8F)

val AdaptiveColors.accentColorRed: Color
    get() = Color(0xFFFF3B30)

val AdaptiveColors.receivedAccentColor: Color
    get() = if (isDark) Color.White.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.2f)

// chatBackground vive en AdaptiveColors (mismo hex 3×); no duplicar extensión.

val AdaptiveColors.messagingBackground: List<Color>
    get() = if (isDark) {
        // ≡ Color.blue iOS ≈ system/accent 007AFF (no Material Color.Blue)
        listOf(userAccentColor.copy(alpha = 0.3f), Color(0xFF007AFF).copy(alpha = 0.2f), Color(0xFF0B1215))
    } else {
        listOf(userAccentColor.copy(alpha = 0.1f), Color(0xFFFAF9F6), Color(0xFFFAF9F6))
    }

/** Legibilidad sobre fondos personalizados (≡ iOS `chatFloatingText`): más opaco; con fondo por defecto, `fallback`. */
@Composable
@ReadOnlyComposable
fun chatFloatingTextColor(fallback: Color): Color =
    LocalChatFloatingTextColor.current?.copy(alpha = 0.9f) ?: fallback

/** Sombra suave para textos sueltos sobre fondos personalizados; `null` con el fondo por defecto. */
@Composable
@ReadOnlyComposable
fun chatFloatingTextShadow(): Shadow? = LocalChatFloatingTextColor.current?.let {
    Shadow(color = (if (it == Color.White) Color.Black else Color.White).copy(alpha = 0.45f), offset = Offset(0f, 1f), blurRadius = 4f)
}
