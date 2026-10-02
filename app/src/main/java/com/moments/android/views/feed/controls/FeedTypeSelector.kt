package com.moments.android.views.feed.controls

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.services.performance.MotionPolicy
import com.moments.android.utilities.HapticManager
import kotlinx.coroutines.delay

/** The wordmark becomes the selected feed; Android owns the anchored menu. */
@Composable
fun FloatingFeedMenu(
    selectedFeedType: FeedType,
    onSelect: (FeedType) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val reduceMotion = MotionPolicy.reduceMotion
    var isShowingBrand by remember { mutableStateOf(!reduceMotion) }
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(reduceMotion) {
        if (!reduceMotion) delay(2_750)
        isShowingBrand = false
    }
    LaunchedEffect(enabled) { if (!enabled) expanded = false }
    Box(modifier, contentAlignment = Alignment.Center) {
        AnimatedContent(
            targetState = isShowingBrand,
            transitionSpec = {
                val duration = if (reduceMotion) 0 else 180
                fadeIn(tween(duration)) togetherWith fadeOut(tween(duration))
            },
            label = "feed-brand-menu",
        ) { showingBrand ->
            Box(Modifier.height(48.dp), contentAlignment = Alignment.Center) {
                if (showingBrand) {
                    Image(
                        painter = painterResource(R.drawable.login_logo_wordmark),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier.width(96.dp).height(20.dp),
                    )
                } else {
                    TextButton(
                        onClick = { expanded = true },
                        enabled = enabled,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(selectedFeedType.title(), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            Icon(Icons.Default.KeyboardArrowDown, null, tint = Color(0xFFAF52DE), modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            FeedType.allCases.forEach { feedType ->
                DropdownMenuItem(
                    text = { Text(feedType.title()) },
                    onClick = {
                        expanded = false
                        if (selectedFeedType != feedType) {
                            onSelect(feedType)
                            HapticManager.shared.selection()
                        }
                    },
                    trailingIcon = {
                        if (selectedFeedType == feedType) Icon(Icons.Default.Check, null)
                    },
                )
            }
        }
    }
}
