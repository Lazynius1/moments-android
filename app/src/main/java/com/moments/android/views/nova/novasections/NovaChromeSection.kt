package com.moments.android.views.nova.novasections

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.services.performance.MotionPolicy
import com.moments.android.utilities.HapticManager
import com.moments.android.utilities.MomentsPressDefaults
import com.moments.android.utilities.momentsPress
import com.moments.android.views.nova.agent.NovaAgent
import com.moments.android.views.nova.novacore.NovaBrandIcon
import com.moments.android.views.nova.novacore.NovaColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.random.Random

/**
 * Port de `Views/Nova/NovaSections/NovaChromeSection.swift`.
 * Header, welcome, cards, partículas, confeti, loading y badge.
 */

// MARK: - Header

@Composable
fun NovaHeader(
    agent: NovaAgent,
    showConversationHistory: (Boolean) -> Unit,
    showSuggestedOptions: (Boolean) -> Unit,
    isShowingMemory: (Boolean) -> Unit,
) {
    var logoTapCount by remember { mutableIntStateOf(0) }
    var showDeveloperEasterEgg by remember { mutableStateOf(false) }
    var lastTapTime by remember { mutableLongStateOf(0L) }
    var logoScale by remember { mutableFloatStateOf(1f) }
    var logoPulse by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val appreciationMessage = stringResource(R.string.nova_easter_egg_appreciation)
    val pulseTransition = rememberInfiniteTransition(label = "novaLogoPulse")
    val pulseFactor by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (logoPulse && !MotionPolicy.reduceMotion) 1.06f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "novaLogoPulseFactor",
    )

    fun resetEasterEgg() {
        logoTapCount = 0
        logoPulse = false
        logoScale = 1f
    }

    fun triggerDeveloperAppreciation() {
        agent.inputText = appreciationMessage
        scope.launch {
            delay(500)
            if (agent.inputText.isNotEmpty()) agent.sendMessage()
        }
    }

    fun handleLogoTap() {
        val now = System.currentTimeMillis()
        logoTapCount = if (now - lastTapTime > 3000L) 1 else logoTapCount + 1
        lastTapTime = now

        val target = if (logoTapCount >= 7) 1f else 1.2f
        logoScale = target
        scope.launch {
            delay(100)
            logoScale = 1f
        }

        when (logoTapCount) {
            3 -> HapticManager.shared.lightImpact()
            4 -> {
                logoPulse = true
                HapticManager.shared.mediumImpact()
            }
            6 -> HapticManager.shared.heavyImpact()
            7 -> {
                showDeveloperEasterEgg = true
                HapticManager.shared.success()
            }
            else -> if (logoTapCount < 7) HapticManager.shared.lightImpact()
        }
    }

    if (showDeveloperEasterEgg) {
        AlertDialog(
            onDismissRequest = {
                showDeveloperEasterEgg = false
                resetEasterEgg()
            },
            title = { Text(stringResource(R.string.nova_easter_egg_title)) },
            text = { Text(stringResource(R.string.nova_easter_egg_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeveloperEasterEgg = false
                    resetEasterEgg()
                }) {
                    Text(stringResource(R.string.nova_easter_egg_primary))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeveloperEasterEgg = false
                    resetEasterEgg()
                    triggerDeveloperAppreciation()
                }) {
                    Text(stringResource(R.string.nova_easter_egg_thanks))
                }
            },
        )
    }

    val logoInteraction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp)
            .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .momentsPress(logoInteraction, MomentsPressDefaults.momentsPress.copy(haptic = MomentsPressDefaults.PressHaptic.NONE))
                .momentsChromeGlass(CircleShape, interactive = true)
                .clickable(interactionSource = logoInteraction, indication = null, onClick = ::handleLogoTap),
            contentAlignment = Alignment.Center,
        ) {
            NovaBrandIcon(
                size = 22.dp,
                modifier = Modifier.scale(logoScale * pulseFactor),
            )
        }

        Column(
            modifier = Modifier.padding(start = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = stringResource(R.string.nova_name),
                color = NovaColors.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.nova_chrome_subtitle),
                color = NovaColors.textSecondary,
                fontSize = 12.sp,
            )
        }

        Spacer(Modifier.weight(1f))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NovaChromeIconButton(
                icon = Icons.Default.Memory,
                contentDescription = stringResource(R.string.nova_memory_title),
                onClick = { isShowingMemory(true) },
            )
            if (agent.conversationHistory.isNotEmpty()) {
                NovaChromeIconButton(
                    icon = Icons.Default.Add,
                    contentDescription = stringResource(R.string.nova_new_conversation),
                    onClick = {
                        agent.startNewConversation()
                        showSuggestedOptions(true)
                    },
                )
            }
            NovaChromeIconButton(
                icon = Icons.Default.History,
                contentDescription = stringResource(R.string.nova_history_title),
                onClick = { showConversationHistory(true) },
            )
        }
    }
}

@Composable
private fun NovaChromeIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(36.dp)
            .momentsPress(interaction, MomentsPressDefaults.momentsPressIcon)
            .momentsChromeGlass(CircleShape, interactive = true)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = NovaColors.textPrimary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
fun NovaBackground(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(NovaColors.background))
}

// MARK: - Welcome

@Composable
fun ModernWelcomeSection(
    agent: NovaAgent,
    showSuggestedOptions: Boolean,
    onShowSuggestedOptionsChange: (Boolean) -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.heightIn(min = 28.dp).weight(1f, fill = true))

        Column(
            modifier = Modifier.widthIn(max = 380.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                text = stringResource(R.string.nova_welcome_eyebrow),
                color = NovaColors.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(NovaColors.materialBackground)
                    .border(1.dp, NovaColors.borderColor, CircleShape)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.nova_hello, agent.currentUserDisplayName),
                    color = NovaColors.textPrimary,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.nova_introduction),
                    color = NovaColors.textSecondary,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(NovaColors.materialBackground)
                        .border(1.dp, NovaColors.borderColor, RoundedCornerShape(18.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = null,
                        tint = NovaColors.textSecondary,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = stringResource(R.string.nova_welcome_support),
                        color = NovaColors.textSecondary,
                        fontSize = 13.sp,
                    )
                }

                val interests = agent.userData?.interests.orEmpty()
                if (interests.isNotEmpty()) {
                    Text(
                        text = interests.take(3).joinToString(" • "),
                        color = NovaColors.textTertiary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            if (showSuggestedOptions) {
                SmartSuggestionChips(agent, onShowSuggestedOptionsChange)
            }
        }

        Spacer(Modifier.heightIn(min = 24.dp).weight(1f, fill = true))
    }
}

// MARK: - Cards

@Composable
fun ModernInfoCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .shadow(10.dp, shape, ambientColor = NovaColors.shadowColor, spotColor = NovaColors.shadowColor)
            .clip(shape)
            .background(NovaColors.cardBackground)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(NovaColors.borderColor, NovaColors.primary.copy(alpha = 0.3f)),
                ),
                shape = shape,
            )
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = NovaColors.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, color = NovaColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
        }
        Text(value, color = NovaColors.textSecondary, fontSize = 14.sp, maxLines = 3)
    }
}

@Composable
fun ModernStatCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp, shape, ambientColor = NovaColors.shadowColor, spotColor = NovaColors.shadowColor)
            .clip(shape)
            .background(NovaColors.cardBackground)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(NovaColors.borderColor, NovaColors.secondary.copy(alpha = 0.3f)),
                ),
                shape = shape,
            )
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, tint = NovaColors.secondary, modifier = Modifier.size(24.dp))
        Text(value, color = NovaColors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(title, color = NovaColors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ModernSuggestionCard(
    title: String,
    icon: ImageVector,
    gradient: List<Color>,
    action: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    val interaction = remember { MutableInteractionSource() }
    val colors = gradient.ifEmpty { listOf(NovaColors.primary) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .momentsPress(interaction, MomentsPressSpecScale095)
            .shadow(8.dp, shape, ambientColor = NovaColors.shadowColor, spotColor = NovaColors.shadowColor)
            .clip(shape)
            .background(NovaColors.cardBackground)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(colors.map { it.copy(alpha = 0.4f) }),
                shape = shape,
            )
            .clickable(interactionSource = interaction, indication = null, onClick = action)
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.first(),
            modifier = Modifier.size(28.dp),
        )
        Text(
            text = title,
            color = NovaColors.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

private val MomentsPressSpecScale095 = MomentsPressDefaults.momentsPress.copy(
    scale = 0.95f,
    haptic = MomentsPressDefaults.PressHaptic.NONE,
)

// MARK: - Sparkles

private data class PremiumSparkleParticle(
    var x: Double,
    var y: Double,
    var size: Double,
    var opacity: Double,
    var speedX: Double,
    var speedY: Double,
    val creationMs: Long,
)

private class PremiumSparkleSystem {
    val particles = mutableListOf<PremiumSparkleParticle>()
    private val maxParticles = 15
    private var lastUpdate = 0.0

    fun update(nowMs: Long) {
        val now = nowMs / 1000.0
        if (now - lastUpdate < 0.016) return
        lastUpdate = now

        particles.removeAll { nowMs - it.creationMs > 1500L }
        for (i in particles.indices) {
            val p = particles[i]
            p.x += p.speedX
            p.y += p.speedY
            p.opacity -= 0.01
        }
        if (particles.size < maxParticles) addParticle(nowMs)
    }

    private fun addParticle(nowMs: Long) {
        particles += PremiumSparkleParticle(
            x = Random.nextDouble(-40.0, 40.0),
            y = Random.nextDouble(-40.0, 40.0),
            size = Random.nextDouble(2.0, 6.0),
            opacity = Random.nextDouble(0.4, 1.0),
            speedX = Random.nextDouble(-0.2, 0.2),
            speedY = Random.nextDouble(-0.5, -0.1),
            creationMs = nowMs,
        )
    }
}

@Composable
fun PremiumSparkleEmitter(color: Color, modifier: Modifier = Modifier) {
    if (MotionPolicy.reduceMotion) {
        Box(modifier)
        return
    }
    val system = remember { PremiumSparkleSystem() }
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis { frame = it }
        }
    }
    Canvas(modifier) {
        @Suppress("UNUSED_EXPRESSION")
        frame
        system.update(System.currentTimeMillis())
        val cx = size.width / 2f
        val cy = size.height / 2f
        for (p in system.particles) {
            drawCircle(
                color = color.copy(alpha = max(0f, p.opacity.toFloat())),
                radius = (p.size / 2.0).toFloat(),
                center = Offset(cx + p.x.toFloat(), cy + p.y.toFloat()),
            )
        }
    }
}

// MARK: - Confetti

private data class ConfettiParticle(
    var x: Double,
    var y: Double,
    val color: Color,
    val size: Double,
    var rotation: Double,
    var speedX: Double,
    var speedY: Double,
    val rotationSpeed: Double,
    var opacity: Double = 1.0,
)

private class ConfettiSystem {
    val particles = mutableListOf<ConfettiParticle>()
    private val colors = listOf(
        Color.Red, Color.Blue, Color.Green, Color.Yellow,
        Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFFFF9800),
    )
    private var lastUpdate = 0.0

    init {
        repeat(50) { addParticle(burst = true) }
    }

    fun update(nowMs: Long, height: Float) {
        val now = nowMs / 1000.0
        if (now - lastUpdate < 0.016) return
        lastUpdate = now

        for (i in particles.indices) {
            val p = particles[i]
            p.x += p.speedX
            p.y += p.speedY
            p.rotation += p.rotationSpeed
            p.speedY += 0.1
            if (p.y > height) p.opacity -= 0.02
        }
        particles.removeAll { it.y > height + 100 || it.opacity <= 0 }
        if (particles.size < 100) addParticle(burst = false)
    }

    private fun addParticle(burst: Boolean) {
        particles += ConfettiParticle(
            x = if (burst) Random.nextDouble(-50.0, 50.0) else Random.nextDouble(-300.0, 300.0),
            y = if (burst) Random.nextDouble(-50.0, 50.0) else -50.0,
            color = colors.random(),
            size = Random.nextDouble(6.0, 12.0),
            rotation = Random.nextDouble(0.0, 360.0),
            speedX = Random.nextDouble(-2.0, 2.0),
            speedY = if (burst) Random.nextDouble(-10.0, -2.0) else Random.nextDouble(2.0, 8.0),
            rotationSpeed = Random.nextDouble(-5.0, 5.0),
        )
    }
}

@Composable
fun ConfettiView(modifier: Modifier = Modifier) {
    if (MotionPolicy.reduceMotion) {
        Box(modifier)
        return
    }
    val system = remember { ConfettiSystem() }
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis { frame = it }
        }
    }
    Canvas(modifier = modifier) {
        @Suppress("UNUSED_EXPRESSION")
        frame
        system.update(System.currentTimeMillis(), size.height)
        val cx = size.width / 2f
        val cy = size.height / 2f
        for (p in system.particles) {
            val useBurstOrigin = p.speedY < 0
            val px = cx + p.x.toFloat()
            val py = if (useBurstOrigin) cy + p.y.toFloat() else p.y.toFloat()
            rotate(p.rotation.toFloat(), pivot = Offset(px, py)) {
                drawRect(
                    color = p.color.copy(alpha = max(0f, p.opacity.toFloat())),
                    topLeft = Offset(px, py),
                    size = Size(p.size.toFloat(), (p.size * 0.6).toFloat()),
                )
            }
        }
    }
}

// MARK: - Loading

@Composable
fun ModernLoadingAnimation(statusLabel: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .momentsChromeGlass(RoundedCornerShape(50), interactive = false)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .momentsChromeGlass(CircleShape, interactive = false),
                contentAlignment = Alignment.Center,
            ) {
                NovaBrandIcon(size = 16.dp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                statusLabel?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        color = NovaColors.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(3) { index ->
                        LoadingDot(delayMs = index * 150)
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingDot(delayMs: Int) {
    val scale = remember { Animatable(0.65f) }
    LaunchedEffect(Unit) {
        if (MotionPolicy.reduceMotion) {
            scale.snapTo(1f)
            return@LaunchedEffect
        }
        delay(delayMs.toLong())
        while (true) {
            scale.animateTo(1f, tween(720, easing = FastOutSlowInEasing))
            scale.animateTo(0.65f, tween(720, easing = FastOutSlowInEasing))
        }
    }
    Box(
        modifier = Modifier
            .size(6.dp)
            .scale(scale.value)
            .clip(CircleShape)
            .background(NovaColors.textSecondary.copy(alpha = 0.65f)),
    )
}

// MARK: - Encryption badge

@Composable
fun NovaEncryptionBadge() {
    Row(
        modifier = Modifier
            .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = NovaColors.textPrimary,
            modifier = Modifier.size(10.dp),
        )
        Text(
            text = stringResource(R.string.nova_encrypted_data),
            color = NovaColors.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
