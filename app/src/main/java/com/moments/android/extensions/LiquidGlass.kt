package com.moments.android.extensions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.utilities.MomentsPressDefaults
import com.moments.android.utilities.momentsPress

// MARK: - Liquid Glass variants

enum class LiquidGlassVariant {
    CLEAR,
    IDENTITY,
    REGULAR,
}

/** Intención del chrome glass — espejo de `MomentsGlassStyle` en iOS. */
enum class MomentsGlassStyle {
    TINTED,
    NATIVE,
    NATIVE_TINTED,
}

object MomentsGlassControlMetrics {
    val navigationControlSize = 40.dp
    val navigationChevronIconSize = 19.dp
    val toolbarControlSize = 38.dp
    val toolbarIconSize = 18.dp
    val compactControlSize = 36.dp
    val compactIconSize = 17.dp
    val controlsClusterSpacing = 2.dp
    val controlsClusterPadding = 4.dp
    val pillBarHeight = 38.dp
    val pillSegmentHeight = 28.dp
    val pillInnerPadding = 4.dp
    val pillLabelSize = 11.sp
    val pillIconSize = 11.sp
    val chromeBackdropFadeTail = 44.dp
    val chromeBackdropMaxBlurFraction = 0.1f
    val feedDetailBlurFadeTail = 28.dp
    val chatChromeBlurFadeTail = 22.dp
    val chatChromeBlurFadeTailExpanded = 30.dp
}

enum class MomentsGlassButtonPreset {
    NAVIGATION_BACK,
    TOOLBAR_ACTION,
    COMPACT_CHROME;

    val controlSize: Dp
        get() = when (this) {
            NAVIGATION_BACK -> MomentsGlassControlMetrics.navigationControlSize
            TOOLBAR_ACTION -> MomentsGlassControlMetrics.toolbarControlSize
            COMPACT_CHROME -> MomentsGlassControlMetrics.compactControlSize
        }

    val iconSize: Dp
        get() = when (this) {
            NAVIGATION_BACK -> MomentsGlassControlMetrics.navigationChevronIconSize
            TOOLBAR_ACTION -> MomentsGlassControlMetrics.toolbarIconSize
            COMPACT_CHROME -> MomentsGlassControlMetrics.compactIconSize
        }
}

object ProfileChromeGlassMetrics {
    val controlSize = MomentsGlassControlMetrics.compactControlSize
    val controlIconSize = MomentsGlassControlMetrics.compactIconSize
    val controlsClusterSpacing = MomentsGlassControlMetrics.controlsClusterSpacing
    val controlsClusterPadding = MomentsGlassControlMetrics.controlsClusterPadding
    val pillBarHeight = MomentsGlassControlMetrics.pillBarHeight
    val pillSegmentHeight = MomentsGlassControlMetrics.pillSegmentHeight
    val pillInnerPadding = MomentsGlassControlMetrics.pillInnerPadding
    val pillLabelSize = MomentsGlassControlMetrics.pillLabelSize
    val pillIconSize = MomentsGlassControlMetrics.pillIconSize
    val chromeBackdropFadeTail = MomentsGlassControlMetrics.chromeBackdropFadeTail
    val chromeBackdropMaxBlurFraction = MomentsGlassControlMetrics.chromeBackdropMaxBlurFraction
    val feedDetailBlurFadeTail = MomentsGlassControlMetrics.feedDetailBlurFadeTail
    val chatChromeBlurFadeTail = MomentsGlassControlMetrics.chatChromeBlurFadeTail
    val chatChromeBlurFadeTailExpanded = MomentsGlassControlMetrics.chatChromeBlurFadeTailExpanded
}

/**
 * Tint de chrome alineado al canvas de la app.
 *
 * Android: fills **opacos** (sin glass/transparencia). El blur/material de iOS
 * se ve mal aquí; usamos el mismo color de superficie sólido en todos los estilos.
 */
object MomentsGlassButtonTint {
    val dark = Color.fromHex("0B1215")
    val light = Color.fromHex("FAF9F6")

    fun canvas(isDark: Boolean): Color = if (isDark) dark else light
}

object MomentsChromeGlass {
    /** Histórico iOS; en Android el tint es siempre opaco (alpha 1). */
    const val defaultTintOpacity = 1f
    const val defaultDarkTintOpacity = 1f
    const val nativeTintedOpacityScale = 1f

    fun canvasTint(isDark: Boolean, opacity: Float = defaultTintOpacity): Color {
        // Ignora opacity: Android chrome = sólido.
        return MomentsGlassButtonTint.canvas(isDark).copy(alpha = 1f)
    }

    fun contentColor(isDark: Boolean): Color =
        if (isDark) Color.White else MomentsGlassButtonTint.dark

    fun underlayOpacity(tintOpacity: Float): Float = 1f
}

object ProfilePillTabPalette {
    fun trackTint(isDark: Boolean): Color = MomentsChromeGlass.canvasTint(isDark)

    fun selectedThumbTint(isDark: Boolean): Color = invertedCanvas(isDark)

    fun selectedLabelColor(isDark: Boolean): Color =
        if (isDark) MomentsGlassButtonTint.dark else MomentsGlassButtonTint.light

    fun unselectedLabelColor(isDark: Boolean): Color =
        if (isDark) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.45f)

    fun selectedShadowColor(isDark: Boolean): Color =
        if (isDark) Color.Black.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.12f)

    private fun invertedCanvas(isDark: Boolean): Color =
        if (isDark) MomentsGlassButtonTint.light else MomentsGlassButtonTint.dark
}

private val CapsuleShape = RoundedCornerShape(50)

// MARK: - Modifiers (superficie opaca + borde sutil; sin material/glass)

fun Modifier.liquidGlass(
    shape: Shape,
    variant: LiquidGlassVariant = LiquidGlassVariant.REGULAR,
    @Suppress("UNUSED_PARAMETER") interactive: Boolean = false,
    tint: Color? = null,
): Modifier = composed {
    val isDark = isSystemInDarkTheme()
    val fill = (tint ?: MomentsChromeGlass.canvasTint(isDark)).copy(alpha = 1f)
    this
        .clip(shape)
        .background(fill, shape)
        .then(
            if (variant == LiquidGlassVariant.REGULAR) {
                Modifier.border(0.5.dp, Color.Black.copy(alpha = if (isDark) 0.12f else 0.08f), shape)
            } else {
                Modifier
            },
        )
}

fun Modifier.momentsChromeGlass(
    shape: Shape,
    @Suppress("UNUSED_PARAMETER") interactive: Boolean = true,
    @Suppress("UNUSED_PARAMETER") style: MomentsGlassStyle = MomentsGlassStyle.NATIVE,
    tintOpacity: Float = MomentsChromeGlass.defaultTintOpacity,
    tint: Color? = null,
): Modifier = composed {
    val isDark = isSystemInDarkTheme()
    // Todos los estilos → fill opaco del canvas (Android no usa ultraThinMaterial).
    val fill = (tint ?: MomentsChromeGlass.canvasTint(isDark, tintOpacity)).copy(alpha = 1f)
    this
        .clip(shape)
        .background(fill, shape)
        .border(
            width = 0.5.dp,
            color = Color.Black.copy(alpha = if (isDark) 0.14f else 0.10f),
            shape = shape,
        )
}

// MARK: - Profile chrome controls

@Composable
fun ProfileChromeControlsCluster(
    modifier: Modifier = Modifier,
    spacing: Dp = ProfileChromeGlassMetrics.controlsClusterSpacing,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .padding(ProfileChromeGlassMetrics.controlsClusterPadding)
            .clip(RoundedCornerShape(50))
            .momentsChromeGlass(CircleShape, interactive = true),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

enum class ChromeIconDescription {
    BACK,
    CLOSE,
    NEW_CONVERSATION,
    DEFAULT,
}

@Composable
fun ProfileChromeIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    foregroundColor: Color? = null,
    preset: MomentsGlassButtonPreset? = null,
    size: Dp = ProfileChromeGlassMetrics.controlSize,
    iconSize: Dp = ProfileChromeGlassMetrics.controlIconSize,
    standaloneGlass: Boolean = true,
    tint: Color? = null,
    accessibilityLabel: String? = null,
    contentDescriptionKey: ChromeIconDescription = ChromeIconDescription.DEFAULT,
) {
    val isDark = isSystemInDarkTheme()
    val resolvedForeground = foregroundColor ?: MomentsChromeGlass.contentColor(isDark)
    val resolvedSize = preset?.controlSize ?: size
    val resolvedIconSize = preset?.iconSize ?: iconSize
    val resolvedLabel = accessibilityLabel ?: resolveChromeAccessibilityLabel(contentDescriptionKey)
    val interactionSource = remember { MutableInteractionSource() }

    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(resolvedSize)
            .semantics { contentDescription = resolvedLabel }
            .momentsPress(interactionSource, MomentsPressDefaults.momentsPressIcon)
            .then(
                if (standaloneGlass) {
                    Modifier.momentsChromeGlass(CircleShape, interactive = true, tint = tint)
                } else {
                    Modifier
                },
            ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = resolvedForeground,
            modifier = Modifier.size(resolvedIconSize),
        )
    }
}

@Composable
private fun resolveChromeAccessibilityLabel(key: ChromeIconDescription): String = when (key) {
    ChromeIconDescription.BACK -> stringResource(R.string.common_back)
    ChromeIconDescription.CLOSE -> stringResource(R.string.common_close)
    ChromeIconDescription.NEW_CONVERSATION -> stringResource(R.string.messaging_new_conversation)
    ChromeIconDescription.DEFAULT -> ""
}

@Composable
fun ProfileGlassPillTrack(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(CapsuleShape)
            .momentsChromeGlass(CapsuleShape, interactive = false)
            .padding(ProfileChromeGlassMetrics.pillInnerPadding),
    ) {
        content()
    }
}

@Composable
fun ProfileGlassPillThumb(
    width: Dp,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    Box(
        modifier = modifier
            .size(width = width, height = ProfileChromeGlassMetrics.pillSegmentHeight)
            .shadow(
                elevation = 5.dp,
                shape = CapsuleShape,
                ambientColor = Color.Black.copy(alpha = if (isDark) 0.2f else 0.07f),
                spotColor = Color.Black.copy(alpha = if (isDark) 0.2f else 0.07f),
            )
            .clip(CapsuleShape)
            .background(ProfilePillTabPalette.selectedThumbTint(isDark)),
    )
}

/** Fondo chrome unificado para la tab bar principal. */
@Composable
fun MomentsTabBarChromeBackground(modifier: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme()
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.Transparent,
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .momentsChromeGlass(RectangleShape, interactive = false),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f),
                    ),
            )
        }
    }
}

@Composable
fun ProfilePillTabLabel(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val color = if (selected) {
        ProfilePillTabPalette.selectedLabelColor(isDark)
    } else {
        ProfilePillTabPalette.unselectedLabelColor(isDark)
    }
    Text(
        text = text,
        fontSize = ProfileChromeGlassMetrics.pillLabelSize,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        color = color,
        modifier = modifier,
    )
}
