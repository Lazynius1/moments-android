package com.moments.android.views.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.moments.android.views.feed.rememberAdaptiveColors
import kotlinx.coroutines.launch

/**
 * Host de presentación ≡ SwiftUI `.sheet`.
 *
 * - `largeOnly = false` → detents medium/large (`.presentationDetents([.medium, .large])`)
 * - `largeOnly = true` → solo large (`.presentationDetents([.large])`)
 *
 * Superficie opaca AdaptiveColors (sin material/blur de iOS).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MomentsModalSheet(
    onDismissRequest: () -> Unit,
    largeOnly: Boolean = false,
    containerColor: Color = rememberAdaptiveColors().surfaceBackground,
    showDragHandle: Boolean = true,
    /** ≡ iOS `interactiveDismissDisabled` — false bloquea swipe/tap fuera. */
    dismissEnabled: Boolean = true,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = largeOnly,
        confirmValueChange = { newValue ->
            dismissEnabled || newValue != SheetValue.Hidden
        },
    )
    val scope = rememberCoroutineScope()
    val dismissSheet: () -> Unit = {
        scope.launch {
            if (sheetState.currentValue != SheetValue.Hidden) {
                sheetState.hide()
            }
            onDismissRequest()
        }
    }

    ModalBottomSheet(
        onDismissRequest = dismissSheet,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = containerColor,
        tonalElevation = 0.dp,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                // Altura estable: Material 3 mueve la hoja entre sus anclajes.
                // Cambiarla según targetValue hacía que el contenido compitiese
                // con el gesto y producía saltos durante el arrastre.
                .fillMaxHeight(0.92f)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            if (showDragHandle) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .width(32.dp)
                            .height(4.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                RoundedCornerShape(2.dp),
                            ),
                    )
                }
            }
            content(dismissSheet)
        }
    }
}
