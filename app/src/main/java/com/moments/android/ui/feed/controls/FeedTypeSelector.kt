package com.moments.android.ui.feed.controls

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.ui.feed.FeedInk
import com.moments.android.ui.feed.FeedPurple
import com.moments.android.ui.feed.FeedTeal

/**
 * Píldora glass flotante con 2 segmentos (Para ti / Siguiendo).
 * Equivalente a FloatingGlassFeedToggle de iOS: el segmento activo lleva
 * degradado teal→morado.
 */
@Composable
fun FloatingFeedToggle(following: Boolean, onSelect: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val pill = RoundedCornerShape(50)
    Row(
        modifier
            .shadow(10.dp, pill, clip = false)
            .clip(pill)
            .background(Color.White.copy(alpha = 0.72f))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)), pill)
            .padding(4.dp),
    ) {
        Segment(stringResource(R.string.feed_for_you), selected = !following) { onSelect(false) }
        Segment(stringResource(R.string.feed_following), selected = following) { onSelect(true) }
    }
}

@Composable
private fun Segment(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Text(
        text = label,
        color = if (selected) Color.White else FeedInk.copy(alpha = 0.8f),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(shape)
            .then(
                if (selected) Modifier.background(Brush.linearGradient(listOf(FeedTeal.copy(alpha = 0.9f), FeedPurple.copy(alpha = 0.9f))))
                else Modifier,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
