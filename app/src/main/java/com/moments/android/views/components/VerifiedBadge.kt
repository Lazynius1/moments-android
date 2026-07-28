package com.moments.android.views.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Port de `VerifiedBadge` (VerifiedBadge.swift) — checkmark.seal.fill con gradiente. */
@Composable
fun VerifiedBadge(
    size: Dp = 16.dp,
    modifier: Modifier = Modifier,
    gradient: Brush = Brush.linearGradient(listOf(Color(0xFF007AFF), Color(0xFFAF52DE), Color(0xFFFF2D55))),
) {
    val painter = rememberVectorPainter(Icons.Filled.Verified)
    Box(modifier.size(size).graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.paint(painter).drawWithCache {
        onDrawWithContent {
            drawContent()
            drawRect(brush = gradient, blendMode = BlendMode.SrcIn)
        }
    })
}

/** Port de `VerifiedUsernameView`. */
@Composable
fun VerifiedUsernameView(
    username: String,
    isVerified: Boolean,
    usernameColor: Color = LocalContentColor.current,
    badgeSize: Dp = 16.dp,
    @Suppress("UNUSED_PARAMETER") badgeColor: Color = Color(0xFF007AFF),
    spacing: Dp = 4.dp,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(username, color = usernameColor)
        if (isVerified) {
            androidx.compose.foundation.layout.Spacer(Modifier.width(spacing))
            VerifiedBadge(size = badgeSize)
        }
    }
}

/** Port de `VerifiedUsernameGradientView`: nombre en gradiente y sello con su gradiente por defecto. */
@Composable
fun VerifiedUsernameGradientView(
    username: String,
    isVerified: Boolean,
    gradient: Brush,
    badgeSize: Dp = 16.dp,
    spacing: Dp = 4.dp,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = username,
            modifier = Modifier
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithCache {
                    onDrawWithContent {
                        drawContent()
                        drawRect(brush = gradient, blendMode = BlendMode.SrcIn)
                    }
                },
        )
        if (isVerified) {
            androidx.compose.foundation.layout.Spacer(Modifier.width(spacing))
            VerifiedBadge(size = badgeSize)
        }
    }
}

/** Port de `VerifiedBadgeView` (StoryModels.swift) — reexport para call sites existentes. */
@Composable
fun VerifiedBadgeView(
    userId: String,
    size: Dp = 14.dp,
    modifier: Modifier = Modifier,
) {
    com.moments.android.views.story.VerifiedBadgeView(
        userId = userId,
        size = size,
        modifier = modifier,
    )
}

/** Port de `CurrentUserVerifiedBadge` (StoryModels.swift) — reexport. */
@Composable
fun CurrentUserVerifiedBadge(
    size: Dp = 14.dp,
    modifier: Modifier = Modifier,
) {
    com.moments.android.views.story.CurrentUserVerifiedBadge(
        size = size,
        modifier = modifier,
    )
}
