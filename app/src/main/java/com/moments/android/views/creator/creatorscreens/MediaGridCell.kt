package com.moments.android.views.creator.creatorscreens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/**
 * Port de `MediaGridCell.swift`.
 * Thumbnail Fill; borde blanco en single; badge numerado en multi.
 */
@Composable
fun MediaGridCell(
    uri: Uri,
    isVideo: Boolean,
    durationSeconds: Double?,
    isSelected: Boolean,
    isMultiSelect: Boolean,
    selectionNumber: Int?,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isLoading by remember(uri) { mutableStateOf(true) }

    Box(
        modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Color.Gray.copy(alpha = 0.3f))
            .clickable(onClick = onTap),
    ) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            onLoading = { isLoading = true },
            onSuccess = { isLoading = false },
            onError = { isLoading = false },
        )

        if (isLoading) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(24.dp),
            )
        }

        if (isSelected && !isMultiSelect) {
            Box(
                Modifier
                    .fillMaxSize()
                    .border(2.dp, Color.White.copy(alpha = 0.92f)),
            )
        }

        if (isVideo) {
            Text(
                formatMediaDuration(durationSeconds ?: 0.0),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp),
            )
        }

        if (isMultiSelect) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (selectionNumber != null) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color(0xFF0095F6), CircleShape)
                            .border(1.5.dp, Color.White, CircleShape),
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
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.18f), CircleShape)
                            .border(1.5.dp, Color.White.copy(alpha = 0.95f), CircleShape),
                    )
                }
            }
        }
    }
}

/** ≡ celda sin thumbnail (ProgressView iOS). */
@Composable
fun MediaGridCellPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .background(Color.Gray.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
    }
}

internal fun formatMediaDuration(seconds: Double): String {
    val total = seconds.toInt().coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}
