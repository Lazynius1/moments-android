package com.moments.android.views.profile.core.sections

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.extensions.ProfileChromeGlassMetrics
import com.moments.android.extensions.ProfileChromeIconButton
import com.moments.android.utilities.legacyPoppinsSize
import kotlin.math.max

/**
 * Port de `ProfileSharedComponents.swift`.
 *
 * Backdrop sticky: canvas AdaptiveColors sólido (sin blur/material iOS) + fade.
 */

@Composable
private fun legacySp(size: Int): androidx.compose.ui.unit.TextUnit {
    val context = LocalContext.current
    val density = LocalDensity.current
    // legacyPoppinsSize → px; toSp() convierte a sp (no usar `.sp` sobre px: infla tipografía).
    return with(density) { legacyPoppinsSize(context, size).toSp() }
}

/** Port de `ModernErrorView`. */
@Composable
fun ModernErrorView(
    errorMessage: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(
            Modifier
                .size(80.dp)
                .background(
                    if (isSystemInDarkTheme()) Color.White.copy(0.08f) else Color.Black.copy(0.06f),
                    CircleShape,
                )
                .border(2.dp, Color.Red.copy(0.3f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.ErrorOutline, null, tint = Color.Red.copy(0.8f), modifier = Modifier.size(35.dp))
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.profile_shared_error_title),
                color = profileSharedPrimary(),
                fontSize = legacySp(18),
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                errorMessage,
                color = profileSharedSecondary(),
                fontSize = legacySp(14),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        Row(
            Modifier
                .background(Color(0xFF007AFF), RoundedCornerShape(50))
                .clickable(onClick = onRetry)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Refresh, null, tint = Color.White, modifier = Modifier.size(16.dp))
            Text(
                stringResource(R.string.profile_shared_retry),
                color = Color.White,
                fontSize = legacySp(14),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** Port de `ExpandableBioView`. */
@Composable
fun ExpandableBioView(bio: String, modifier: Modifier = Modifier) {
    var expanded by remember(bio) { mutableStateOf(false) }
    val needsExpansion = bio.length > 100 || bio.count { it == '\n' } > 2
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            bio,
            color = profileSharedSecondary(),
            fontSize = legacySp(14),
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.animateContentSize(),
        )
        if (needsExpansion) {
            Text(
                stringResource(if (expanded) R.string.profile_shared_see_less else R.string.profile_shared_see_more),
                color = Color(0xFF007AFF),
                fontSize = legacySp(13),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable { expanded = !expanded }.padding(vertical = 4.dp),
            )
        }
    }
}

/** Port de `ProfileFlowLayout`. */
@Composable
fun ProfileFlowLayout(
    spacing: Dp = 8.dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables: List<Measurable>, constraints: Constraints ->
        val gap = spacing.roundToPx()
        var x = 0
        var y = 0
        var line = 0
        val placed = mutableListOf<Pair<Placeable, Pair<Int, Int>>>()
        measurables.forEach { measurable ->
            val child = measurable.measure(Constraints())
            if (x > 0 && x + child.width > constraints.maxWidth) {
                x = 0
                y += line + gap
                line = 0
            }
            placed += child to (x to y)
            x += child.width + gap
            line = max(line, child.height)
        }
        layout(constraints.maxWidth, y + line) {
            placed.forEach { (child, point) -> child.placeRelative(point.first, point.second) }
        }
    }
}

object ProfileAvatarNoteMetrics {
    const val maxLength = 28
    val columnWidth = 96.dp
}

/** Port de `ProfileAvatarNoteView` (`ProfileSharedComponents.swift`). */
@Composable
fun ProfileAvatarNoteView(
    note: String?,
    isEditable: Boolean,
    onSave: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var isEditing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    // Compose dispara unblur al montar; iOS FocusState no. Solo commit tras ganar foco.
    var hasGainedFocus by remember { mutableStateOf(false) }
    // Estado visual local: si el VM recibe un fetch stale/vacío tras el save, no apagar la nota.
    var displayedNote by remember { mutableStateOf(note) }
    LaunchedEffect(note) {
        if (note != null) displayedNote = note
    }

    val displayText = displayedNote?.trim()?.takeIf { it.isNotEmpty() }
    if (!isEditable && displayText == null && note == null) return

    val dark = isSystemInDarkTheme()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val placeholder = stringResource(R.string.profile_shared_avatar_note_placeholder)
    val placeholderColor = if (dark) Color.White.copy(0.38f) else Color.Black.copy(0.32f)
    val noteColor = if (dark) Color.White.copy(0.82f) else Color.Black.copy(0.72f)

    // ≡ Swift `commitEdit`
    fun commitEdit() {
        if (!isEditing) return
        val trimmed = draft.trim()
        displayedNote = trimmed.ifEmpty { null }
        isEditing = false
        hasGainedFocus = false
        onSave?.invoke(trimmed)
        keyboard?.hide()
    }

    Box(
        modifier = modifier.width(ProfileAvatarNoteMetrics.columnWidth),
        contentAlignment = Alignment.Center,
    ) {
        when {
            isEditing -> {
                LaunchedEffect(Unit) {
                    // ≡ onAppear — no reasignar draft en recomposiciones posteriores
                    if (draft.isEmpty()) draft = displayText.orEmpty()
                    hasGainedFocus = false
                    focusRequester.requestFocus()
                    keyboard?.show()
                }
                BasicTextField(
                    value = draft,
                    onValueChange = { newValue ->
                        if (newValue.contains('\n')) {
                            draft = newValue.replace("\n", " ").trim()
                            commitEdit()
                            return@BasicTextField
                        }
                        // Ignorar wipe de golpe del IME al pulsar Done (Compose ≠ UIKit).
                        if (newValue.isEmpty() && draft.length > 1) {
                            commitEdit()
                            return@BasicTextField
                        }
                        draft = if (newValue.length > ProfileAvatarNoteMetrics.maxLength) {
                            newValue.take(ProfileAvatarNoteMetrics.maxLength)
                        } else {
                            newValue
                        }
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = legacySp(12),
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        color = profileSharedPrimary(),
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { commitEdit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { state ->
                            if (state.isFocused) {
                                hasGainedFocus = true
                            } else if (hasGainedFocus && isEditing) {
                                commitEdit()
                            }
                        },
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            if (draft.isEmpty()) {
                                Text(
                                    placeholder,
                                    color = placeholderColor,
                                    fontSize = legacySp(12),
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                )
                            }
                            inner()
                        }
                    },
                )
            }
            displayText != null -> {
                Text(
                    displayText,
                    color = noteColor,
                    fontSize = legacySp(12),
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = isEditable) {
                            draft = displayText
                            isEditing = true
                        },
                )
            }
            isEditable -> {
                Text(
                    placeholder,
                    color = placeholderColor,
                    fontSize = legacySp(12),
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            draft = ""
                            isEditing = true
                        },
                )
            }
        }
    }
}

/**
 * ≡ PreferenceKeys iOS (`ScrollOffset` / `ProfileIdentityMinY` / `ProfileTabsMinY`).
 * En Compose se reporta con [onGloballyPositioned] + [positionInWindow].
 */
fun Modifier.reportProfileIdentityMinY(onMinY: (Float) -> Unit): Modifier =
    onGloballyPositioned { onMinY(it.positionInWindow().y) }

fun Modifier.reportProfileTabsMinY(onMinY: (Float) -> Unit): Modifier =
    onGloballyPositioned { onMinY(it.positionInWindow().y) }

fun Modifier.reportScrollOffset(onOffset: (Float) -> Unit): Modifier =
    onGloballyPositioned { onOffset(it.positionInWindow().y) }

/** Port de `ProfileHeaderCollapseMetrics`. */
object ProfileHeaderCollapseMetrics {
    val chromeHeight = 36.dp
    val topChromePadding = 4.dp
    val identitySectionGap = 28.dp
    val headerTopPadding = 4.dp
    val pinnedTabsHeight = ProfileChromeGlassMetrics.pillBarHeight
    const val fixedLocationChromeBlurProgress = 0.68f
    val feedDetailChromeBlurFadeTail = ProfileChromeGlassMetrics.feedDetailBlurFadeTail
    val locationChromeBlurFadeTail = ProfileChromeGlassMetrics.feedDetailBlurFadeTail
    val topContentInset = chromeHeight + identitySectionGap
    val stickyChromeBlurRegionHeight: Dp
        get() = topChromePadding + chromeHeight + 8.dp + pinnedTabsHeight + 8.dp
    val stickyChromeContentInset: Dp get() = stickyChromeBlurRegionHeight
    val feedStyleDetailTopInset = topChromePadding + chromeHeight + 12.dp
    val locationFeedTopInset: Dp get() = feedStyleDetailTopInset
    val feedStoriesChromeHeight = 88.dp
    const val tabsFadeLead = 96f
    const val detailScrollFadeLead = 64f

    fun feedScrollChromeBlurProgress(contentMinY: Float, contentTopInset: Float): Float {
        if (!contentMinY.isFinite() || contentMinY >= 10_000f) return 0f
        if (contentMinY >= contentTopInset) return 0f
        return ((contentTopInset - contentMinY) / tabsFadeLead).coerceIn(0f, 1f)
    }

    fun detailScrollChromeBlurProgress(
        contentMinY: Float,
        initialContentMinY: Float,
        fadeLead: Float = detailScrollFadeLead,
    ): Float {
        if (!contentMinY.isFinite() || !initialContentMinY.isFinite()) return 0f
        val upward = initialContentMinY - contentMinY
        if (upward <= 0f) return 0f
        return (upward / fadeLead).coerceIn(0f, 1f)
    }

    val tabsPinY: Float
        get() = topChromePadding.value + chromeHeight.value + 8f

    fun progress(tabsMinY: Float): Float {
        if (!tabsMinY.isFinite() || tabsMinY >= 10_000f) return 0f
        val start = tabsPinY + tabsFadeLead
        if (tabsMinY >= start) return 0f
        return ((start - tabsMinY) / tabsFadeLead).coerceIn(0f, 1f)
    }

    fun tabsArePinned(tabsMinY: Float): Boolean =
        tabsMinY.isFinite() && tabsMinY <= tabsPinY + 0.5f
}

/** Tipografía del título sticky (≡ `StickyChromeTitleTypography`). */
object StickyChromeTitleTypography {
    val fontSize = 17.sp
    val fontWeight = FontWeight.SemiBold
}

/**
 * ≡ `ProfileProgressiveChromeBackdrop` — canvas AdaptiveColors + máscara fade.
 * Sin blur/Liquid Glass (política Android de fondos).
 */
@Composable
fun ProfileProgressiveChromeBackdrop(
    progress: Float,
    fadeTail: Dp = ProfileChromeGlassMetrics.chromeBackdropFadeTail,
    glassOnly: Boolean = false,
    blurOnly: Boolean = false,
    modifier: Modifier = Modifier,
) {
    @Suppress("UNUSED_PARAMETER")
    val unusedBlurOnly = blurOnly
    val alpha = progress.coerceIn(0f, 1f)
    val canvas = if (isSystemInDarkTheme()) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    val base = if (glassOnly) Color.White.copy(alpha * 0.08f) else canvas.copy(alpha = alpha * 0.86f)
    Box(
        modifier
            .fillMaxWidth()
            .height(ProfileHeaderCollapseMetrics.stickyChromeContentInset + fadeTail)
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to base,
                        0.25f to base,
                        0.6f to base.copy(alpha = base.alpha * 0.55f),
                        0.85f to base.copy(alpha = base.alpha * 0.2f),
                        1f to Color.Transparent,
                    ),
                ),
            ),
    )
}

/** Port de `ProfileStickyChromeContainer`. */
@Composable
fun ProfileStickyChromeContainer(
    blurProgress: Float,
    tabsArePinned: Boolean,
    chrome: @Composable () -> Unit,
    pinnedTabs: @Composable () -> Unit = {},
    blurFadeTail: Dp = ProfileChromeGlassMetrics.chromeBackdropFadeTail,
    glassOnly: Boolean = false,
    blurOnly: Boolean = false,
    tintOpacity: Float = 0f,
    horizontalPadding: Dp = 20.dp,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth()) {
        ProfileProgressiveChromeBackdrop(
            progress = blurProgress,
            fadeTail = blurFadeTail,
            glassOnly = glassOnly,
            blurOnly = blurOnly,
        )
        if (tintOpacity > 0f) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(ProfileHeaderCollapseMetrics.stickyChromeContentInset + blurFadeTail)
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.White.copy(tintOpacity),
                                0.28f to Color.White.copy(tintOpacity),
                                0.68f to Color.White.copy(tintOpacity * 0.45f),
                                1f to Color.Transparent,
                            ),
                        ),
                    ),
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(
                    top = ProfileHeaderCollapseMetrics.topChromePadding,
                    start = horizontalPadding,
                    end = horizontalPadding,
                    bottom = if (tabsArePinned) 8.dp else 0.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            chrome()
            if (tabsArePinned) pinnedTabs()
        }
    }
}

/** Port de `StickyChromeBarLayout`. */
@Composable
fun StickyChromeBarLayout(
    leading: @Composable () -> Unit,
    center: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().height(ProfileHeaderCollapseMetrics.chromeHeight)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading()
            Spacer(Modifier.weight(1f))
            trailing()
        }
        Box(
            Modifier.align(Alignment.Center).padding(horizontal = 56.dp),
            contentAlignment = Alignment.Center,
        ) {
            center()
        }
    }
}

/** Port de `FeedPinnedTopChrome`. */
@Composable
fun FeedPinnedTopChrome(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StickyChromeBarLayout(
        leading = { ProfileChromeIconButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, onDismiss) },
        center = {
            Text(
                title,
                color = profileSharedPrimary(),
                fontSize = StickyChromeTitleTypography.fontSize,
                fontWeight = StickyChromeTitleTypography.fontWeight,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailing = { Spacer(Modifier.size(ProfileChromeGlassMetrics.controlSize)) },
        modifier = modifier,
    )
}

@Composable
private fun profileSharedPrimary() =
    if (isSystemInDarkTheme()) Color.White else Color(0xFF0B1215)

@Composable
private fun profileSharedSecondary() =
    if (isSystemInDarkTheme()) Color.White.copy(0.64f) else Color(0xFF52626A)
