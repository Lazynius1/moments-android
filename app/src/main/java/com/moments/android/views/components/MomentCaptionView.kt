package com.moments.android.views.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.utilities.legacyPoppinsSize
import com.moments.android.views.feed.moments.ClickableHashtagsView
import com.moments.android.views.feed.moments.FeedMomentCardLayout

/** Port de `MomentCaptionPresentationStyle` (MomentCaptionView.swift). */
enum class MomentCaptionPresentationStyle {
    Feed,
    Reels,
    Detail,
}

/**
 * Port de `MomentCaptionView.swift` — estilo feed/detail (Reels se añade con ReelsViewer).
 * iOS feed: preview 120 chars / 3 líneas + sheet “ver más”.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MomentCaptionView(
    content: String,
    onHashtagTap: (String) -> Unit,
    style: MomentCaptionPresentationStyle = MomentCaptionPresentationStyle.Feed,
    modifier: Modifier = Modifier,
) {
    val trimmed = content.trim()
    if (trimmed.isEmpty()) return

    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current
    val density = LocalDensity.current
    var showFullCaption by remember { mutableStateOf(false) }

    val maxCharacters = when (style) {
        MomentCaptionPresentationStyle.Feed -> 120
        MomentCaptionPresentationStyle.Reels -> 90
        MomentCaptionPresentationStyle.Detail -> 180
    }
    val newlineCount = trimmed.count { it == '\n' }
    val needsExpansion = trimmed.length > maxCharacters || newlineCount > 1
    val preview = if (needsExpansion) {
        trimmed.take(maxCharacters).trimEnd() + "..."
    } else {
        trimmed
    }

    val secondary = if (isDark) Color.White.copy(alpha = 0.68f) else Color.Black.copy(alpha = 0.58f)

    if (style == MomentCaptionPresentationStyle.Reels) {
        ReelsCaptionBody(trimmed, onHashtagTap, modifier)
        return
    }

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = FeedMomentCardLayout.captionHorizontalPadding)
            .padding(top = if (style == MomentCaptionPresentationStyle.Detail) 0.dp else 2.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ClickableHashtagsView(
            content = preview,
            onHashtagTap = onHashtagTap,
            modifier = Modifier.fillMaxWidth(),
        )

        if (needsExpansion) {
            Row(
                Modifier
                    .momentsChromeGlass(RoundedCornerShape(percent = 50), interactive = true)
                    .clickable { showFullCaption = true }
                    .padding(horizontal = 11.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = stringResource(R.string.feed_see_more),
                    color = secondary,
                    fontSize = with(density) { legacyPoppinsSize(context, 12).toSp() },
                    fontWeight = FontWeight.SemiBold,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.FormatAlignLeft,
                    contentDescription = null,
                    tint = secondary,
                    modifier = Modifier.size(10.dp),
                )
            }
        }
    }

    if (showFullCaption) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        ModalBottomSheet(
            onDismissRequest = { showFullCaption = false },
            sheetState = sheetState,
            containerColor = Color.Transparent,
            dragHandle = null,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .momentsChromeGlass(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ClickableHashtagsView(
                    content = trimmed,
                    onHashtagTap = {
                        onHashtagTap(it)
                        showFullCaption = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Bloque Reels de `MomentCaptionView.swift`: preview corto y expansión con scroll limitado. */
@Composable
private fun ReelsCaptionBody(content: String, onHashtagTap: (String) -> Unit, modifier: Modifier) {
    var expanded by remember(content) { mutableStateOf(false) }
    val lines = content.lines().filter { it.isNotBlank() }
    val needsMore = lines.size >= 2 || content.length > 72
    val collapsed = when {
        !needsMore -> content
        lines.size >= 2 -> (lines.take(2).joinToString("\n")).take(75).trimEnd()
        else -> content.take(75).trimEnd()
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (expanded) {
            Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                ClickableHashtagsView(content, onHashtagTap, Modifier.fillMaxWidth())
            }
            Text(stringResource(R.string.feed_see_less), color = if (isSystemInDarkTheme()) Color.White else Color.Black, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { expanded = false })
        } else {
            ClickableHashtagsView(collapsed, onHashtagTap, Modifier.fillMaxWidth().clickable(enabled = needsMore) { expanded = true })
            if (needsMore) Text(stringResource(R.string.feed_see_more), color = if (isSystemInDarkTheme()) Color.White else Color.Black, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { expanded = true })
        }
    }
}
