package com.moments.android.views.shared

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.models.PhotoTag

/** Port de `PhotoTagOverlayView.swift`. */
@Composable
fun PhotoTagOverlayView(
    tags: List<PhotoTag>,
    isVisible: Boolean,
    onTagTap: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        tags.forEach { tag ->
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn() + scaleIn(initialScale = 0.5f),
                exit = fadeOut(),
                modifier = Modifier.offset(
                    x = w * tag.x.toFloat() - 40.dp,
                    y = h * tag.y.toFloat() - 50.dp,
                ),
            ) {
                PhotoTagBubble(
                    username = tag.username,
                    onClick = { onTagTap?.invoke(tag.userId) },
                )
            }
        }
    }
}

@Composable
private fun PhotoTagBubble(
    username: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            Modifier
                .shadow(4.dp, RoundedCornerShape(percent = 50), ambientColor = Color.Black.copy(0.2f), spotColor = Color.Black.copy(0.2f))
                .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(percent = 50))
                .border(0.5.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(percent = 50))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(username, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(0.dp),
            )
        }
        Box(
            Modifier
                .padding(top = 0.dp)
                .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(1.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}
