package com.moments.android.notifications.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.utilities.legacyPoppinsSize
import com.moments.android.views.components.rememberMomentsSkeletonColor
import com.moments.android.views.components.shimmer
import com.moments.android.views.feed.FeedCanvas
import com.moments.android.views.feed.FeedInk

/** Port de NotificationSharedViews.swift */

/** ≡ NotificationDateHeaderView — claves New/This Week/… → strings 8 locales. */
@Composable
fun NotificationDateHeader(dateString: String, isDark: Boolean) {
    val label = when (dateString) {
        "New" -> stringResource(R.string.notifications_section_new)
        "This Week" -> stringResource(R.string.notifications_section_this_week)
        "This Month" -> stringResource(R.string.notifications_section_this_month)
        "Earlier" -> stringResource(R.string.notifications_section_earlier)
        else -> dateString
    }
    // iOS: dark 0B1215 / light FAF9F6 ≡ FeedInk / FeedCanvas
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isDark) FeedInk else FeedCanvas)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = label.uppercase(),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isDark) Color.White.copy(alpha = 0.62f) else Color.Black.copy(alpha = 0.6f),
        )
    }
}

/** ≡ NotificationSkeletonRow + .shimmer */
@Composable
fun NotificationSkeletonRow(isDark: Boolean) {
    val fill = rememberMomentsSkeletonColor()
    val onSurface = MaterialTheme.colorScheme.onSurface
    val stroke = onSurface.copy(alpha = if (isDark) 0.1f else 0.05f)
    val card = onSurface.copy(alpha = if (isDark) 0.06f else 0.04f)
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp)
            .clip(shape)
            .background(card, shape)
            .border(0.5.dp, stroke, shape)
            .shimmer(isAnimating = true),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(52.dp).clip(CircleShape).background(fill))
            Spacer(modifier = Modifier.width(15.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(fill),
                )
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(fill),
                )
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(fill),
            )
        }
    }
}

/** ≡ NotificationDeletionUndoToast + momentsChromeGlass */
@Composable
fun NotificationDeletionUndoToast(
    deletedCount: Int,
    isDark: Boolean,
    onUndo: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val fontSp = with(density) { legacyPoppinsSize(context, 14).toSp() }
    val message = if (deletedCount > 1) {
        stringResource(R.string.notifications_deleted_toast_plural)
    } else {
        stringResource(R.string.notifications_deleted_toast)
    }
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .momentsChromeGlass(shape, interactive = false)
            .padding(horizontal = 18.dp, vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            modifier = Modifier.weight(1f),
            fontSize = fontSp,
            fontWeight = FontWeight.SemiBold,
            color = if (isDark) Color.White else Color.Black,
            maxLines = 1,
        )
        Text(
            text = stringResource(R.string.notifications_deleted_undo),
            fontSize = fontSp,
            fontWeight = FontWeight.SemiBold,
            color = if (isDark) Color.White else Color.Black,
            modifier = Modifier.clickable(onClick = onUndo),
        )
    }
}

/**
 * ≡ GlassmorphicButtonStyle — gradiente, stroke, shadow, scale al press.
 * Nombre Android: [GlassmorphicActionButton] (usado por Follow/Trailing).
 */
@Composable
fun GlassmorphicActionButton(
    text: String,
    color: Color,
    isDark: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val fontSp = with(density) { legacyPoppinsSize(context, 12).toSp() }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "glassBtnScale",
    )
    val stroke = if (isDark) Color.White.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.2f)
    Text(
        text = text,
        fontSize = fontSp,
        fontWeight = FontWeight.SemiBold,
        color = Color.White,
        modifier = Modifier
            .scale(scale)
            .shadow(4.dp, CircleShape, ambientColor = color.copy(alpha = 0.3f), spotColor = color.copy(alpha = 0.3f))
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.8f))))
            .border(1.dp, stroke, CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
