package com.moments.android.views.comments

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.moments.android.services.content.FeedMoment
import com.moments.android.views.feed.rememberAdaptiveColors

/**
 * Presentación sheet de comentarios — paridad iOS de comportamiento:
 * detents medium/large + drag indicator.
 * Surface opaca (Android): sin glass/transparencia.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernCommentsSheet(
    moment: FeedMoment,
    onDismiss: () -> Unit,
    onOpenStory: (userId: String) -> Unit = {},
    onOpenProfile: (userId: String) -> Unit = {},
) {
    val colors = rememberAdaptiveColors()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val screenH = LocalConfiguration.current.screenHeightDp.dp
    val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val contentHeight by animateDpAsState(
        targetValue = when (sheetState.targetValue) {
            SheetValue.Expanded -> screenH * 0.92f
            SheetValue.PartiallyExpanded, SheetValue.Hidden -> screenH * 0.55f
        },
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 400f),
        label = "commentsSheetHeight",
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = sheetShape,
        containerColor = colors.surfaceBackground,
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
            BottomSheetDefaults.DragHandle(modifier = Modifier.align(Alignment.CenterHorizontally))
            ModernCommentsView(
                moment = moment,
                onDismiss = onDismiss,
                onOpenStory = onOpenStory,
                onOpenProfile = onOpenProfile,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}
