package com.moments.android.views.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Port de `CommentRowSkeletonView.swift`: avatar, autor y una o dos líneas de comentario. */
@Composable
fun CommentRowSkeletonView(
    textLineCount: Int = 2,
    modifier: Modifier = Modifier,
) {
    val base = if (isSystemInDarkTheme()) Color.White.copy(.08f) else Color.Black.copy(.06f)
    val shimmer = rememberCommentSkeletonBrush(base)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(32.dp).background(shimmer, CircleShape))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.width(84.dp).height(11.dp).background(shimmer, RoundedCornerShape(4.dp)))
            repeat(textLineCount.coerceAtLeast(0)) { index ->
                val line = Modifier.height(12.dp).background(shimmer, RoundedCornerShape(4.dp))
                Box(if (index == textLineCount - 1) line.width(140.dp) else line.fillMaxWidth())
            }
        }
    }
}

/** Lista vertical de filas de comentario en carga, equivalente a `CommentRowSkeletonList`. */
@Composable
fun CommentRowSkeletonList(
    rows: Int = 3,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        repeat(rows.coerceAtLeast(0)) { CommentRowSkeletonView() }
    }
}

@Composable
private fun rememberCommentSkeletonBrush(base: Color): Brush {
    val transition = rememberInfiniteTransition(label = "commentSkeletonShimmer")
    val phase = transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_150, easing = LinearEasing), RepeatMode.Restart),
        label = "commentSkeletonPhase",
    ).value
    return Brush.linearGradient(
        colors = listOf(base.copy(alpha = base.alpha * .72f), base.copy(alpha = (base.alpha * 1.45f).coerceAtMost(1f)), base.copy(alpha = base.alpha * .72f)),
        start = androidx.compose.ui.geometry.Offset(phase * 500f, 0f),
        end = androidx.compose.ui.geometry.Offset((phase + 1f) * 500f, 500f),
    )
}
