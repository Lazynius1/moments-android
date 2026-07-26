package com.moments.android.views.shared

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.moments.android.views.feed.rememberAdaptiveColors

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
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = largeOnly)
    val screenH = LocalConfiguration.current.screenHeightDp.dp
    val contentHeight by animateDpAsState(
        targetValue = when {
            largeOnly -> screenH * 0.92f
            sheetState.targetValue == SheetValue.Expanded -> screenH * 0.92f
            else -> screenH * 0.55f
        },
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 400f),
        label = "momentsModalSheetHeight",
    )

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
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
                .height(contentHeight)
                .navigationBarsPadding(),
        ) {
            if (showDragHandle) {
                BottomSheetDefaults.DragHandle(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
            content()
        }
    }
}
