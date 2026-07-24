package com.moments.android.views.profile.core.sections

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.extensions.ProfileChromeIconButton
import com.moments.android.extensions.momentsChromeGlass
import kotlin.math.max

/** Port de `ProfileSharedComponents.swift`. */
@Composable
fun ModernErrorView(errorMessage: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(Modifier.size(80.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (isSystemInDarkTheme()) Color.White.copy(.08f) else Color.Black.copy(.06f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.ErrorOutline, null, tint = Color.Red.copy(.8f), modifier = Modifier.size(35.dp))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.profile_shared_error_title), color = profileSharedPrimary(), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(errorMessage, color = profileSharedSecondary(), fontSize = 14.sp, textAlign = TextAlign.Center)
        }
        Row(Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(50)).background(Color(0xFF007AFF)).clickable(onClick = onRetry).padding(horizontal = 24.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Refresh, null, tint = Color.White, modifier = Modifier.size(16.dp))
            Text(stringResource(R.string.profile_shared_retry), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun ExpandableBioView(bio: String, modifier: Modifier = Modifier) {
    var expanded by remember(bio) { mutableStateOf(false) }
    val expandable = bio.length > 100 || bio.count { it == '\n' } > 2
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(bio, color = profileSharedSecondary(), fontSize = 14.sp, maxLines = if (expanded) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.animateContentSize())
        if (expandable) Text(stringResource(if (expanded) R.string.profile_shared_see_less else R.string.profile_shared_see_more), color = Color(0xFF007AFF), fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clickable { expanded = !expanded }.padding(vertical = 4.dp))
    }
}

/** Layout de chips equivalente a `ProfileFlowLayout`. */
@Composable
fun ProfileFlowLayout(spacing: Dp = 8.dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables: List<Measurable>, constraints: Constraints ->
        val gap = spacing.roundToPx()
        var x = 0; var y = 0; var line = 0
        val placed = mutableListOf<Pair<Placeable, Pair<Int, Int>>>()
        measurables.forEach { measurable ->
            val child = measurable.measure(Constraints())
            if (x > 0 && x + child.width > constraints.maxWidth) { x = 0; y += line + gap; line = 0 }
            placed += child to (x to y); x += child.width + gap; line = max(line, child.height)
        }
        layout(constraints.maxWidth, y + line) { placed.forEach { (child, point) -> child.placeRelative(point.first, point.second) } }
    }
}

object ProfileAvatarNoteMetrics { const val maxLength = 28; val columnWidth = 96.dp }

@Composable
fun ProfileAvatarNoteView(note: String?, isEditable: Boolean, onSave: ((String) -> Unit)? = null, modifier: Modifier = Modifier) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember(note) { mutableStateOf(note.orEmpty().trim()) }
    val display = note?.trim()?.takeIf { it.isNotEmpty() }
    if (isEditable || display != null) {
        if (editing) TextField(
            value = draft,
            onValueChange = { value -> draft = value.replace("\n", " ").take(ProfileAvatarNoteMetrics.maxLength) },
            placeholder = { Text(stringResource(R.string.profile_shared_avatar_note_placeholder)) },
            modifier = modifier.width(ProfileAvatarNoteMetrics.columnWidth),
            singleLine = true,
        ) else Text(
            text = display ?: stringResource(R.string.profile_shared_avatar_note_placeholder),
            color = if (display == null) profileSharedSecondary().copy(.55f) else profileSharedSecondary(),
            fontSize = 12.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = modifier.width(ProfileAvatarNoteMetrics.columnWidth).clickable(enabled = isEditable) { draft = display.orEmpty(); editing = true },
        )
        if (editing) {
            // Compose no tiene toolbar de teclado equivalente; commit ocurre al perder el editor desde el caller.
            // La pulsación de la nota vuelve a modo lectura y persiste el texto acotado como Swift.
            Text(stringResource(R.string.common_done), color = Color(0xFF007AFF), fontSize = 12.sp, modifier = Modifier.clickable { onSave?.invoke(draft.trim()); editing = false })
        }
    }
}

/** Métricas y curvas del chrome sticky, compartidas por perfil y detalle. */
object ProfileHeaderCollapseMetrics {
    val chromeHeight = 36.dp; val topChromePadding = 4.dp; val identitySectionGap = 28.dp; val headerTopPadding = 4.dp
    val pinnedTabsHeight = 36.dp; const val fixedLocationChromeBlurProgress = .68f; const val feedDetailChromeBlurFadeTail = 48f
    val topContentInset = chromeHeight + identitySectionGap; val stickyChromeContentInset = topChromePadding + chromeHeight + 8.dp + pinnedTabsHeight + 8.dp; val feedStyleDetailTopInset = topChromePadding + chromeHeight + 12.dp
    const val tabsFadeLead = 96f; const val detailScrollFadeLead = 64f
    fun feedScrollChromeBlurProgress(contentMinY: Float, contentTopInset: Float): Float = if (!contentMinY.isFinite() || contentMinY >= contentTopInset) 0f else ((contentTopInset - contentMinY) / tabsFadeLead).coerceIn(0f, 1f)
    fun detailScrollChromeBlurProgress(contentMinY: Float, initialContentMinY: Float, fadeLead: Float = detailScrollFadeLead): Float = if (!contentMinY.isFinite() || !initialContentMinY.isFinite()) 0f else ((initialContentMinY - contentMinY) / fadeLead).coerceIn(0f, 1f)
    val tabsPinY = topChromePadding.value + chromeHeight.value + 8f
    fun progress(tabsMinY: Float): Float = if (!tabsMinY.isFinite()) 0f else (((tabsPinY + tabsFadeLead) - tabsMinY) / tabsFadeLead).coerceIn(0f, 1f)
    fun tabsArePinned(tabsMinY: Float): Boolean = tabsMinY.isFinite() && tabsMinY <= tabsPinY + .5f
}

@Composable
fun ProfileProgressiveChromeBackdrop(progress: Float, fadeTail: Dp = 48.dp, glassOnly: Boolean = false, blurOnly: Boolean = false, modifier: Modifier = Modifier) {
    val alpha = progress.coerceIn(0f, 1f)
    Box(modifier.fillMaxWidth().height(ProfileHeaderCollapseMetrics.stickyChromeContentInset).then(if (blurOnly) Modifier.blur(18.dp * alpha) else Modifier).background(if (glassOnly) Color.White.copy(alpha * .08f) else Color(if (isSystemInDarkTheme()) 0xFF0B1215 else 0xFFFAF9F6).copy(alpha * .86f)))
}

@Composable
fun ProfileStickyChromeContainer(blurProgress: Float, tabsArePinned: Boolean, chrome: @Composable () -> Unit, pinnedTabs: @Composable () -> Unit = {}, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth()) {
        ProfileProgressiveChromeBackdrop(blurProgress)
        Column(Modifier.fillMaxWidth().padding(top = ProfileHeaderCollapseMetrics.topChromePadding, start = 20.dp, end = 20.dp, bottom = if (tabsArePinned) 8.dp else 0.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { chrome(); if (tabsArePinned) pinnedTabs() }
    }
}

@Composable
fun StickyChromeBarLayout(leading: @Composable () -> Unit, center: @Composable () -> Unit, trailing: @Composable () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(ProfileHeaderCollapseMetrics.chromeHeight)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) { leading(); Spacer(Modifier.weight(1f)); trailing() }
        Box(Modifier.align(Alignment.Center).padding(horizontal = 56.dp), contentAlignment = Alignment.Center) { center() }
    }
}

@Composable
fun FeedPinnedTopChrome(title: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    StickyChromeBarLayout(
        leading = { ProfileChromeIconButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, onDismiss) },
        center = { Text(title, color = profileSharedPrimary(), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        trailing = { Spacer(Modifier.size(36.dp)) }, modifier = modifier,
    )
}

@Composable private fun profileSharedPrimary() = if (isSystemInDarkTheme()) Color.White else Color(0xFF0B1215)
@Composable private fun profileSharedSecondary() = if (isSystemInDarkTheme()) Color.White.copy(.64f) else Color(0xFF52626A)
