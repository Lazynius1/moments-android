package com.moments.android.views.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.views.feed.rememberAdaptiveColors
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * [SheetState] del [MomentsModalSheet] anfitrión — para anclar footers a la zona
 * visible (medium/large) sin cambiar la altura medida del contenido.
 */
@OptIn(ExperimentalMaterial3Api::class)
val LocalMomentsSheetState = staticCompositionLocalOf<SheetState?> { null }

/**
 * Modal bottom sheet Material 3
 * ([m3.material.io/components/bottom-sheets](https://m3.material.io/components/bottom-sheets/overview)).
 *
 * - `largeOnly = false` → PartiallyExpanded + Expanded
 * - `largeOnly = true` → solo Expanded
 *
 * Estilo Moments (unificado):
 * - Canvas = AdaptiveColors.surfaceBackground (handle + contenido mismo color).
 * - Cabecera pegada al handle ([MomentsSheetHeader]); sin chevron de dismiss
 *   (el drag handle / swipe cierra).
 *
 * M3 mide el contenido a altura expanded; en medium solo se ve la franja superior.
 * Footers/inputs: usar [MomentsSheetPinnedFooter] — **no** redimensionar el
 * contenido con [SheetState.requireOffset] (provoca lag y elimina el ancla medium).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MomentsModalSheet(
    onDismissRequest: () -> Unit,
    largeOnly: Boolean = false,
    containerColor: Color = Color.Unspecified,
    shape: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    showDragHandle: Boolean = true,
    /** false ≡ bloquear swipe-to-dismiss y tap en scrim (M3 confirmValueChange). */
    dismissEnabled: Boolean = true,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    val canvas = if (containerColor == Color.Unspecified) {
        rememberAdaptiveColors().surfaceBackground
    } else {
        containerColor
    }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = largeOnly,
        confirmValueChange = { newValue ->
            dismissEnabled || newValue != SheetValue.Hidden
        },
    )
    val scope = rememberCoroutineScope()
    val dismissSheet: () -> Unit = {
        scope.launch {
            sheetState.hide()
            onDismissRequest()
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (dismissEnabled) onDismissRequest()
        },
        sheetState = sheetState,
        shape = shape,
        containerColor = canvas,
        tonalElevation = 0.dp,
        scrimColor = BottomSheetDefaults.ScrimColor,
        dragHandle = if (showDragHandle) {
            { BottomSheetDefaults.DragHandle() }
        } else {
            null
        },
    ) {
        CompositionLocalProvider(LocalMomentsSheetState provides sheetState) {
            content(dismissSheet)
        }
    }
}

/**
 * Cabecera compacta de sheet: título centrado pegado al drag handle.
 * Sin botón chevron/cerrar — dismiss = swipe / scrim.
 */
@Composable
fun MomentsSheetHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleSize: TextUnit = 16.sp,
    trailing: @Composable (() -> Unit)? = null,
) {
    val colors = rememberAdaptiveColors()
    Box(
        modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 8.dp),
    ) {
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                title,
                fontWeight = FontWeight.SemiBold,
                fontSize = titleSize,
                color = colors.primary,
                textAlign = TextAlign.Center,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = colors.secondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (trailing != null) {
            Box(Modifier.align(Alignment.CenterEnd)) {
                trailing()
            }
        }
    }
}

/**
 * Footer/composer anclado al borde inferior *visible* del sheet (medium o large).
 *
 * Debe vivir dentro de un [androidx.compose.foundation.layout.Box] hermano del
 * contenido scrollable. Solo este composable lee el offset del sheet (recomposiciones
 * baratas al arrastrar). No cambia la altura medida del contenido.
 *
 * @return padding inferior recomendado para la lista (altura del footer).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxScope.MomentsSheetPinnedFooter(
    modifier: Modifier = Modifier,
    /** Espacio bajo el drag handle M3 ≈ 48.dp. */
    handleAllowance: Dp = 48.dp,
    content: @Composable ColumnScope.() -> Unit,
): Dp {
    val sheetState = LocalMomentsSheetState.current
    val density = LocalDensity.current
    val windowHeightPx = LocalWindowInfo.current.containerSize.height
    val handleAllowancePx = with(density) { handleAllowance.roundToPx() }
    var footerHeightPx by remember { mutableIntStateOf(0) }

    // Lectura aislada: solo este footer se recompone mientras arrastras el sheet.
    val sheetOffsetPx = if (sheetState != null) {
        runCatching { sheetState.requireOffset() }.getOrDefault(Float.NaN)
    } else {
        Float.NaN
    }
    val yPx = if (sheetOffsetPx.isNaN() || footerHeightPx <= 0) {
        0
    } else {
        val visibleContentPx =
            (windowHeightPx - sheetOffsetPx - handleAllowancePx).coerceAtLeast(0f)
        (visibleContentPx - footerHeightPx).roundToInt().coerceAtLeast(0)
    }

    Column(
        modifier
            .fillMaxWidth()
            .align(Alignment.TopStart)
            .onSizeChanged { footerHeightPx = it.height }
            .offset { IntOffset(0, yPx) },
        content = content,
    )

    return with(density) {
        footerHeightPx.coerceAtLeast(0).toDp().coerceAtLeast(72.dp)
    }
}
