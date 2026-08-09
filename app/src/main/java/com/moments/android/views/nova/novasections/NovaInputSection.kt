package com.moments.android.views.nova.novasections

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.services.performance.MotionPolicy
import com.moments.android.views.nova.agent.NovaAgent
import com.moments.android.views.nova.novacore.NovaColors
import com.moments.android.views.settings.SettingsProfileColors

/**
 * Port de `Views/Nova/NovaSections/NovaInputSection.swift`.
 * Layout insets, plus/attach, input bar, suggestion chips y shimmer.
 */
object NovaInputBarLayout {
    val bottomPaddingWithoutKeyboard: Dp = 8.dp
    /** Aire visible entre sheet y tab bar. */
    val sheetAboveTabBarGap: Dp = 12.dp
    /** Tab bar clearance (pill flotante ≈ iOS 26). */
    val tabBarClearance: Dp = 74.dp

    fun bottomPadding(keyboardHeight: Dp, safeAreaBottom: Dp): Dp =
        if (keyboardHeight > 0.dp) {
            maxOf(
                bottomPaddingWithoutKeyboard,
                keyboardHeight - safeAreaBottom + bottomPaddingWithoutKeyboard,
            )
        } else {
            safeAreaBottom + bottomPaddingWithoutKeyboard
        }

    fun attachmentSheetBottomInset(safeAreaBottom: Dp): Dp =
        safeAreaBottom + tabBarClearance + sheetAboveTabBarGap
}

@Composable
fun NovaAttachmentPlusButton(
    isMenuOpen: Boolean,
    action: () -> Unit,
    modifier: Modifier = Modifier,
    onBoundsInRoot: ((Rect) -> Unit)? = null,
) {
    val rotation by animateFloatAsState(
        targetValue = if (isMenuOpen) 45f else 0f,
        animationSpec = if (MotionPolicy.reduceMotion) {
            tween(0)
        } else {
            spring(dampingRatio = 0.82f, stiffness = 400f)
        },
        label = "novaPlusRotate",
    )
    Box(
        modifier = modifier
            .size(44.dp)
            .onGloballyPositioned { coords ->
                onBoundsInRoot?.invoke(coords.boundsInRoot())
            }
            .clickable(onClick = action),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = stringResource(R.string.nova_input_attach_accessibility),
            tint = NovaColors.textPrimary,
            modifier = Modifier.size(18.dp).rotate(rotation),
        )
    }
}

@Composable
fun EnhancedInputBar(
    agent: NovaAgent,
    showSuggestedOptions: (Boolean) -> Unit,
    activeAttachmentSheet: NovaAttachmentSheetKind?,
    onAttachmentSheetChange: (NovaAttachmentSheetKind?) -> Unit,
    onFocusChange: ((Boolean) -> Unit)? = null,
    onPlusBoundsChange: ((Rect) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val hasText = agent.inputText.isNotEmpty()
    val menuOpen = activeAttachmentSheet == NovaAttachmentSheetKind.MENU
    val fieldShape = RoundedCornerShape(22.dp)
    val inputSurface = SettingsProfileColors.surfaceContainer(isSystemInDarkTheme())

    Column(modifier = modifier.fillMaxWidth().background(NovaColors.background)) {
        agent.selectedImage?.let { bitmap ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, top = 8.dp),
                ) {
                    Box {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(12.dp)),
                        )
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.nova_input_remove_photo_accessibility),
                            tint = Color.White,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                                .clickable { agent.selectedImage = null },
                        )
                    }
                }
            }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 2.dp, end = 4.dp, top = 6.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                NovaAttachmentPlusButton(
                    isMenuOpen = menuOpen,
                    action = { onAttachmentSheetChange(if (menuOpen) null else NovaAttachmentSheetKind.MENU) },
                    onBoundsInRoot = onPlusBoundsChange,
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .padding(horizontal = 2.dp)
                    .clip(fieldShape)
                    .background(inputSurface, fieldShape)
                    .onFocusChanged { state ->
                        focused = state.isFocused
                        onFocusChange?.invoke(state.isFocused)
                    }
                    .padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = agent.inputText,
                    onValueChange = { agent.inputText = it },
                    textStyle = TextStyle(color = NovaColors.textPrimary, fontSize = 16.sp),
                    cursorBrush = SolidColor(NovaColors.textPrimary),
                    maxLines = 6,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (agent.inputText.isNotEmpty()) {
                            agent.sendMessage()
                            showSuggestedOptions(false)
                        }
                    }),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (agent.inputText.isEmpty()) {
                            Text(
                                text = stringResource(R.string.nova_input_placeholder),
                                color = NovaColors.textSecondary,
                                fontSize = 16.sp,
                            )
                        }
                        inner()
                    },
                )
            }

            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                if (hasText) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(inputSurface)
                        .clickable {
                            agent.sendMessage()
                            showSuggestedOptions(false)
                            focused = false
                            onFocusChange?.invoke(false)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.nova_input_send_accessibility),
                        tint = NovaColors.textPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                }
            }
        }
    }
}

data class SmartSuggestion(
    val text: String,
    val icon: String,
    val action: String? = null,
)

enum class NovaSuggestionStyle { COMPACT, HERO }

@Composable
fun SmartSuggestionChips(
    agent: NovaAgent,
    @Suppress("UNUSED_PARAMETER") showSuggestedOptions: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        agent.welcomeSuggestions.forEach { suggestion ->
            val title = stringResource(suggestion.titleRes)
            val prompt = stringResource(suggestion.promptRes)
            SmartSuggestionChip(
                suggestion = SmartSuggestion(title, suggestion.icon, prompt),
                style = NovaSuggestionStyle.HERO,
            ) {
                // iOS: set text + send; no toca showSuggestedOptions en el chip.
                agent.inputText = prompt
                agent.sendMessage()
            }
        }
    }
}

@Composable
fun SmartSuggestionChip(
    suggestion: SmartSuggestion,
    style: NovaSuggestionStyle = NovaSuggestionStyle.COMPACT,
    action: () -> Unit,
) {
    val isHero = style == NovaSuggestionStyle.HERO
    val shape = if (isHero) RoundedCornerShape(18.dp) else CircleShape
    Row(
        modifier = Modifier
            .then(if (isHero) Modifier.fillMaxWidth() else Modifier)
            .then(
                if (isHero) {
                    Modifier
                        .clip(shape)
                        .background(NovaColors.materialBackground)
                        .border(1.dp, NovaColors.borderColor, shape)
                } else {
                    Modifier.momentsChromeGlass(shape, interactive = true)
                },
            )
            .clickable(onClick = action)
            .padding(horizontal = 14.dp, vertical = if (isHero) 11.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (isHero) 10.dp else 8.dp),
    ) {
        Icon(
            imageVector = suggestion.icon.iconVector(),
            contentDescription = null,
            tint = NovaColors.textPrimary,
            modifier = Modifier
                .then(if (isHero) Modifier.width(28.dp) else Modifier)
                .size(if (isHero) 15.dp else 14.dp),
        )
        Text(
            text = suggestion.text,
            color = NovaColors.textPrimary,
            fontSize = if (isHero) 15.sp else 14.sp,
            fontWeight = if (isHero) FontWeight.SemiBold else FontWeight.Medium,
        )
        if (isHero) {
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = Icons.Default.ArrowOutward,
                contentDescription = null,
                tint = NovaColors.textSecondary,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

/** ≡ iOS `View.shimmer()` / `NovaShimmerModifier`. */
@Composable
fun Modifier.novaShimmer(): Modifier {
    if (MotionPolicy.reduceMotion) return this
    val transition = rememberInfiniteTransition(label = "novaShimmer")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "novaShimmerPhase",
    )
    return drawWithContent {
        drawContent()
        val width = size.width
        val offsetX = -width + phase * width * 2f
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.3f), Color.Transparent),
                start = Offset(offsetX, 0f),
                end = Offset(offsetX + width, 0f),
            ),
        )
    }
}

private fun String.iconVector(): ImageVector = when (this) {
    "pencil.line" -> Icons.Default.Edit
    "book" -> Icons.AutoMirrored.Filled.MenuBook
    "heart" -> Icons.Default.Favorite
    "lightbulb" -> Icons.Default.Lightbulb
    else -> Icons.Default.Add
}
