package com.moments.android.views.nova.novasections

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MenuBook
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.views.nova.agent.NovaAgent
import com.moments.android.views.nova.agent.NovaWelcomeSuggestion
import com.moments.android.views.nova.novacore.NovaColors

object NovaInputBarLayout {
    val bottomPaddingWithoutKeyboard = 8.dp
    val sheetAboveTabBarGap = 12.dp
    val tabBarClearance = 74.dp
    fun attachmentSheetBottomInset(safeAreaBottom: androidx.compose.ui.unit.Dp) = safeAreaBottom + tabBarClearance + sheetAboveTabBarGap
}

@Composable fun NovaAttachmentPlusButton(isMenuOpen: Boolean, action: () -> Unit) = Box(Modifier.size(44.dp).clip(CircleShape).background(NovaColors.materialBackground).clickable(onClick = action), Alignment.Center) { Icon(Icons.Default.Add, stringResource(R.string.nova_input_attach_accessibility), tint = NovaColors.textPrimary, modifier = Modifier.size(18.dp).rotate(if (isMenuOpen) 45f else 0f)) }

@Composable
fun EnhancedInputBar(agent: NovaAgent, showSuggestedOptions: (Boolean) -> Unit, activeAttachmentSheet: NovaAttachmentSheetKind?, onAttachmentSheetChange: (NovaAttachmentSheetKind?) -> Unit, onFocusChange: ((Boolean) -> Unit)? = null, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }; val hasText = agent.inputText.isNotEmpty(); val menuOpen = activeAttachmentSheet == NovaAttachmentSheetKind.MENU
    Column(modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        agent.selectedImage?.let { bitmap -> Row(Modifier.fillMaxWidth().padding(start = 20.dp, top = 8.dp)) { Box { Image(bitmap.asImageBitmap(), null, Modifier.size(80.dp).clip(RoundedCornerShape(12.dp))); Icon(Icons.Default.Close, stringResource(R.string.nova_input_remove_photo_accessibility), tint = Color.White, modifier = Modifier.align(Alignment.TopEnd).size(24.dp).background(Color.Black.copy(alpha = .5f), CircleShape).clickable { agent.selectedImage = null }.padding(3.dp)) } } }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            NovaAttachmentPlusButton(menuOpen) { onAttachmentSheetChange(if (menuOpen) null else NovaAttachmentSheetKind.MENU) }
            BasicTextField(value = agent.inputText, onValueChange = { agent.inputText = it }, textStyle = TextStyle(color = NovaColors.textPrimary, fontSize = 16.sp), maxLines = 6, modifier = Modifier.weight(1f).padding(start = 10.dp).onFocusChanged { state -> focused = state.isFocused; onFocusChange?.invoke(state.isFocused) }.clip(RoundedCornerShape(22.dp)).background(NovaColors.materialBackground).padding(horizontal = 14.dp, vertical = 12.dp), decorationBox = { inner -> if (agent.inputText.isEmpty()) Text(stringResource(R.string.nova_input_placeholder), color = NovaColors.textSecondary, fontSize = 16.sp); inner() })
            if (hasText) Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.nova_input_send_accessibility), tint = NovaColors.textPrimary, modifier = Modifier.padding(start = 10.dp).size(44.dp).clip(CircleShape).background(NovaColors.materialBackground).clickable { agent.sendMessage(); showSuggestedOptions(false); focused = false; onFocusChange?.invoke(false) }.padding(13.dp))
        }
    }
}

data class SmartSuggestion(val text: String, val icon: String, val action: String? = null)
enum class NovaSuggestionStyle { COMPACT, HERO }
@Composable fun SmartSuggestionChips(agent: NovaAgent, showSuggestedOptions: (Boolean) -> Unit) = Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) { agent.welcomeSuggestions.forEach { suggestion -> val title = stringResource(suggestion.titleRes); val prompt = stringResource(suggestion.promptRes); SmartSuggestionChip(SmartSuggestion(title, suggestion.icon, prompt), NovaSuggestionStyle.HERO) { agent.inputText = prompt; agent.sendMessage(); showSuggestedOptions(false) } } }
@Composable fun SmartSuggestionChip(suggestion: SmartSuggestion, style: NovaSuggestionStyle = NovaSuggestionStyle.COMPACT, action: () -> Unit) = Row(Modifier.fillMaxWidth().clip(if (style == NovaSuggestionStyle.HERO) RoundedCornerShape(18.dp) else CircleShape).background(NovaColors.materialBackground).clickable(onClick = action).padding(horizontal = 16.dp, vertical = if (style == NovaSuggestionStyle.HERO) 16.dp else 10.dp), verticalAlignment = Alignment.CenterVertically) { Icon(suggestion.icon.iconVector(), null, tint = NovaColors.textPrimary, modifier = Modifier.size(if (style == NovaSuggestionStyle.HERO) 15.dp else 14.dp)); Spacer(Modifier.width(if (style == NovaSuggestionStyle.HERO) 12.dp else 8.dp)); Text(suggestion.text, color = NovaColors.textPrimary, fontSize = if (style == NovaSuggestionStyle.HERO) 15.sp else 14.sp, fontWeight = if (style == NovaSuggestionStyle.HERO) FontWeight.SemiBold else FontWeight.Medium); if (style == NovaSuggestionStyle.HERO) { Spacer(Modifier.weight(1f)); Icon(Icons.Default.ArrowOutward, null, tint = NovaColors.textSecondary, modifier = Modifier.size(12.dp)) } }
@Composable fun Modifier.novaShimmer(): Modifier { val transition = rememberInfiniteTransition(); val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart)); return background(NovaColors.materialBackground.copy(alpha = .35f + phase * .15f)) }
private fun String.iconVector() = when (this) { "pencil.line" -> Icons.Default.Edit; "book" -> Icons.Default.MenuBook; "heart" -> Icons.Default.Favorite; "lightbulb" -> Icons.Default.Lightbulb; else -> Icons.Default.Add }
