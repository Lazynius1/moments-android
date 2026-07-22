package com.moments.android.views.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.models.Moment
import com.moments.android.views.feed.rememberAdaptiveColors

/**
 * Port simplificado de `ExploreMomentsBentoGrid` — filas de 3 (sin LazyGrid anidado).
 */
@Composable
fun ExploreMomentsGrid(
    moments: List<Moment>,
    onMomentTap: (Moment, Int, List<Moment>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    if (moments.isEmpty()) {
        Box(
            modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.explore_no_moments),
                color = colors.secondary,
                fontSize = 15.sp,
            )
        }
        return
    }

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        moments.chunked(3).forEachIndexed { rowIndex, row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                row.forEachIndexed { colIndex, moment ->
                    val index = rowIndex * 3 + colIndex
                    val thumb = moment.mediaItems?.firstOrNull()?.thumbnailUrl
                        ?: moment.mediaItems?.firstOrNull()?.url
                        ?: moment.thumbnailUrl
                        ?: moment.imagePath
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Gray.copy(0.2f))
                            .clickable { onMomentTap(moment, index, moments) },
                    ) {
                        if (!thumb.isNullOrBlank()) {
                            AsyncImage(
                                model = thumb,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.matchParentSize(),
                            )
                        } else {
                            Text(
                                moment.username.take(1).uppercase(),
                                Modifier.align(Alignment.Center),
                                color = colors.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                repeat(3 - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(80.dp))
    }
}
