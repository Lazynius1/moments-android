package com.moments.android.views.creator.creatorscreens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/**
 * Port de `MediaGridCell.swift`.
 */
@Composable
fun MediaGridCell(
    uri: Uri,
    isVideo: Boolean,
    durationSeconds: Double?,
    isSelected: Boolean,
    selectionNumber: Int?,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .clickable(onClick = onTap),
    ) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().background(Color.Gray.copy(0.3f)),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(0.1f)),
                    ),
                ),
        )
        if (isSelected) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xFFFF2D55).copy(0.3f))
                    .border(3.dp, Color(0xFFFF2D55)),
            )
        }
        if (isVideo) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(Color.Black.copy(0.5f), RoundedCornerShape(50))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            ) {
                androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Videocam,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(10.dp),
                    )
                    Text(
                        formatMediaDuration(durationSeconds ?: 0.0),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp),
        ) {
            if (selectionNumber != null) {
                Box(
                    Modifier
                        .size(22.dp)
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF9C27B0), Color(0xFFE91E63))),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$selectionNumber",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            } else {
                Box(
                    Modifier
                        .size(22.dp)
                        .border(2.dp, Color.White, CircleShape),
                )
            }
        }
    }
}

@Composable
fun MediaGridCellPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .background(Color.Gray.copy(0.3f)),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            color = Color(0xFF00A896),
            strokeWidth = 2.dp,
            modifier = Modifier.size(24.dp),
        )
    }
}

fun formatMediaDuration(durationSeconds: Double): String {
    val total = durationSeconds.toInt().coerceAtLeast(0)
    val minutes = total / 60
    val seconds = total % 60
    return "%d:%02d".format(minutes, seconds)
}
