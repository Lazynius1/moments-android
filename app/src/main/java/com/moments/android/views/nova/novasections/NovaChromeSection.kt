package com.moments.android.views.nova.novasections

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.views.nova.agent.NovaAgent
import com.moments.android.views.nova.novacore.NovaBrandIcon
import com.moments.android.views.nova.novacore.NovaColors
import kotlin.random.Random

@Composable
fun NovaHeader(agent: NovaAgent, showConversationHistory: (Boolean) -> Unit, showSuggestedOptions: (Boolean) -> Unit, isShowingMemory: (Boolean) -> Unit) {
    var taps by remember { mutableIntStateOf(0) }; var lastTap by remember { mutableStateOf(0L) }; var showEgg by remember { mutableStateOf(false) }
    val appreciationMessage = stringResource(R.string.nova_easter_egg_appreciation)
    fun reset() { taps = 0 }
    if (showEgg) AlertDialog(onDismissRequest = { showEgg = false; reset() }, title = { Text(stringResource(R.string.nova_easter_egg_title)) }, text = { Text(stringResource(R.string.nova_easter_egg_message)) }, confirmButton = { Text(stringResource(R.string.nova_easter_egg_primary), modifier = Modifier.clickable { showEgg = false; reset() }.padding(16.dp)) }, dismissButton = { Text(stringResource(R.string.nova_easter_egg_thanks), modifier = Modifier.clickable { showEgg = false; reset(); agent.inputText = appreciationMessage; agent.sendMessage() }.padding(16.dp)) })
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).background(NovaColors.materialBackground, CircleShape).padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clickable { val now = System.currentTimeMillis(); taps = if (now - lastTap > 3000) 1 else taps + 1; lastTap = now; if (taps == 7) showEgg = true }, Alignment.Center) { NovaBrandIcon(22.dp) }
        Column(Modifier.padding(start = 8.dp)) { Text(stringResource(R.string.nova_name), color = NovaColors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold); Text(stringResource(R.string.nova_chrome_subtitle), color = NovaColors.textSecondary, fontSize = 12.sp) }
        Spacer(Modifier.weight(1f)); NovaChromeIcon(Icons.Default.Memory, R.string.nova_memory_title) { isShowingMemory(true) }
        if (agent.conversationHistory.isNotEmpty()) NovaChromeIcon(Icons.Default.Add, R.string.nova_new_conversation) { agent.startNewConversation(); showSuggestedOptions(true) }
        NovaChromeIcon(Icons.Default.History, R.string.nova_history_title) { showConversationHistory(true) }
    }
}

@Composable private fun NovaChromeIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, labelRes: Int, onClick: () -> Unit) = Box(Modifier.padding(start = 8.dp).size(36.dp).background(NovaColors.secondaryBackground, CircleShape).clickable(onClick = onClick), Alignment.Center) { Icon(icon, stringResource(labelRes), tint = NovaColors.textPrimary, modifier = Modifier.size(18.dp)) }
@Composable fun NovaBackground(modifier: Modifier = Modifier) = Box(modifier.fillMaxSize().background(NovaColors.background))

@Composable
fun ModernWelcomeSection(agent: NovaAgent, showSuggestedOptions: Boolean, onSuggestion: (String) -> Unit) = Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
    Text(stringResource(R.string.nova_welcome_eyebrow), color = NovaColors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.background(NovaColors.materialBackground, CircleShape).padding(horizontal = 12.dp, vertical = 8.dp)); Spacer(Modifier.height(24.dp)); Text(stringResource(R.string.nova_hello, agent.currentUserDisplayName), color = NovaColors.textPrimary, fontSize = 34.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center); Text(stringResource(R.string.nova_introduction), color = NovaColors.textSecondary, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp)); Spacer(Modifier.height(10.dp)); Row(Modifier.fillMaxWidth().background(NovaColors.materialBackground, RoundedCornerShape(18.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Memory, null, tint = NovaColors.textSecondary, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.nova_welcome_support), color = NovaColors.textSecondary, fontSize = 13.sp) }; agent.userData?.interests?.take(3)?.takeIf { it.isNotEmpty() }?.let { Text(it.joinToString(" • "), color = NovaColors.textTertiary, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 10.dp)) }; if (showSuggestedOptions) NovaWelcomeSuggestionChips(onSuggestion)
}
@Composable private fun NovaWelcomeSuggestionChips(onSuggestion: (String) -> Unit) = Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(R.string.nova_welcome_suggestion_one, R.string.nova_welcome_suggestion_two, R.string.nova_welcome_suggestion_three).forEach { res -> val title = stringResource(res); Text(title, color = NovaColors.primary, fontSize = 13.sp, modifier = Modifier.background(NovaColors.primary.copy(alpha = .1f), CircleShape).clickable { onSuggestion(title) }.padding(horizontal = 12.dp, vertical = 8.dp)) } }

@Composable fun ModernInfoCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) = Column(modifier.background(NovaColors.cardBackground, RoundedCornerShape(16.dp)).padding(20.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = NovaColors.primary); Spacer(Modifier.width(8.dp)); Text(title, color = NovaColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }; Text(value, color = NovaColors.textSecondary, fontSize = 14.sp, maxLines = 3, modifier = Modifier.padding(top = 12.dp)) }
@Composable fun ModernStatCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) = Column(modifier.background(NovaColors.cardBackground, RoundedCornerShape(16.dp)).padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null, tint = NovaColors.secondary); Text(value, color = NovaColors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)); Text(title, color = NovaColors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium) }
@Composable fun ModernSuggestionCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, gradient: List<Color>, action: () -> Unit, modifier: Modifier = Modifier) = Column(modifier.background(NovaColors.cardBackground, RoundedCornerShape(16.dp)).clickable(onClick = action).padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null, tint = gradient.firstOrNull() ?: NovaColors.primary, modifier = Modifier.size(28.dp)); Text(title, color = NovaColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp)) }

private data class Sparkle(val x: Float, val y: Float, val size: Float, val alpha: Float)
@Composable fun PremiumSparkleEmitter(color: Color, modifier: Modifier = Modifier) { val transition = rememberInfiniteTransition(); val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart)); val particles = remember { List(15) { Sparkle(Random.nextFloat() * 80f - 40f, Random.nextFloat() * 80f - 40f, Random.nextFloat() * 4f + 2f, Random.nextFloat() * .6f + .4f) } }; Canvas(modifier) { particles.forEach { p -> drawCircle(color.copy(alpha = p.alpha * (1f - phase)), p.size, Offset(size.width / 2 + p.x, size.height / 2 + p.y - phase * 24f)) } } }
private data class Confetti(val x: Float, val y: Float, val color: Color, val size: Float, val rotation: Float)
@Composable fun ConfettiView(modifier: Modifier = Modifier) { val transition = rememberInfiniteTransition(); val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart)); val colors = listOf(Color.Red, Color.Blue, Color.Green, Color.Yellow, Color.Magenta, Color(0xFFFF9800)); val particles = remember { List(50) { Confetti(Random.nextFloat() * 600f - 300f, Random.nextFloat() * 80f - 50f, colors.random(), Random.nextFloat() * 6f + 6f, Random.nextFloat() * 360f) } }; Canvas(modifier) { particles.forEach { p -> rotate(p.rotation + phase * 360f, Offset(size.width / 2 + p.x, p.y + phase * (size.height + 150))) { drawRect(p.color.copy(alpha = 1f - phase), Offset(size.width / 2 + p.x, p.y + phase * (size.height + 150)), androidx.compose.ui.geometry.Size(p.size, p.size * .6f)) } } } }
@Composable fun ModernLoadingAnimation(statusLabel: String? = null) { val transition = rememberInfiniteTransition(); val pulse by transition.animateFloat(.65f, 1f, infiniteRepeatable(tween(720), RepeatMode.Reverse)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(30.dp).background(NovaColors.materialBackground, CircleShape), Alignment.Center) { NovaBrandIcon(16.dp) }; Column(Modifier.padding(start = 8.dp)) { statusLabel?.takeIf { it.isNotBlank() }?.let { Text(it, color = NovaColors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium) }; Row { repeat(3) { Box(Modifier.padding(horizontal = 2.dp).size((6 * pulse).dp).background(NovaColors.textSecondary.copy(alpha = .65f), CircleShape)) } } } } }
@Composable fun NovaEncryptionBadge() = Row(Modifier.background(NovaColors.materialBackground, CircleShape).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Lock, null, tint = NovaColors.textPrimary, modifier = Modifier.size(10.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.nova_encrypted_data), color = NovaColors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium) }
