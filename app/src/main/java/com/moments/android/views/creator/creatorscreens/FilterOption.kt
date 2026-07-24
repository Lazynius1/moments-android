package com.moments.android.views.creator.creatorscreens
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.services.content.FilterService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Port de `FilterOption.swift`.
 */
@Composable
fun FilterOption(
    sourceUri: Uri,
    filter: FilterService.FilterType,
    isSelected: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var preview by remember(sourceUri, filter) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(sourceUri, filter) {
        preview = withContext(Dispatchers.Default) {
            val base = context.contentResolver.openInputStream(sourceUri)?.use {
                BitmapFactory.decodeStream(it)
            } ?: return@withContext null
            val thumb = Bitmap.createScaledBitmap(base, 100, 120, true)
            if (base !== thumb) base.recycle()
            FilterService.applyFilterToThumbnail(filter, thumb)
        }
    }

    Column(
        modifier.clickable(onClick = onTap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val shape = RoundedCornerShape(12.dp)
        if (preview != null) {
            Image(
                bitmap = preview!!.asImageBitmap(),
                contentDescription = filter.raw,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(80.dp, 100.dp)
                    .clip(shape)
                    .then(
                        if (isSelected) {
                            Modifier.border(
                                2.dp,
                                Brush.linearGradient(
                                    listOf(Color(0xFF9C27B0), Color(0xFFE91E63), Color(0xFFFF9800)),
                                ),
                                shape,
                            )
                        } else {
                            Modifier
                        },
                    ),
            )
        } else {
            Spacer(
                Modifier
                    .size(80.dp, 100.dp)
                    .clip(shape)
                    .background(Color.Gray.copy(0.2f)),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            filter.raw,
            color = if (isSelected) Color.White else Color.Gray,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}
