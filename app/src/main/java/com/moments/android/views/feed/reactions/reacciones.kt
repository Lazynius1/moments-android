package com.moments.android.views.feed.reactions

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.extensions.fromHex
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.services.content.FeedMoment
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.utilities.legacyPoppinsSize
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Port de `reacciones.swift` — ReactionType, UserReactionUsageTracker,
 * ModernReactionButton, ReactionPickerView.
 * Helpers Firestore (`removeReaction` / `fetchReactions` / `listenToReactions` /
 * `checkUserReaction`) viven en [FirestoreService].
 */
enum class ReactionType(
    val rawValue: String,
    val icon: String,
    val displayName: String,
    val color: Color,
) {
    Vibe("vibe", "✌🏻", "Vibe", Color.fromHex("007AFF")),
    Fire("fire", "🔥", "Fire", Color.Red),
    Real("real", "✅", "Real", Color(0xFFAF52DE)),
    Mood("mood", "😊", "Mood", Color(0xFFFFCC00)),
    Glow("glow", "✨", "Glow", Color(0xFFFF9500)),
    Feel("feel", "❤️", "Feel", Color(0xFFFF2D55)),
    Love("love", "💕", "Love", Color.Red),
    Wow("wow", "😮", "Wow", Color(0xFF007AFF)),
    Laugh("laugh", "😂", "Laugh", Color(0xFFFFCC00)),
    Cry("cry", "😢", "Cry", Color(0xFF32ADE6)),
    Respect("respect", "🙏🏻", "Respect", Color(0xFF34C759)),
    Power("power", "⚡", "Power", Color(0xFFFF9500)),
    Genius("genius", "🧠", "Genius", Color(0xFF5856D6)),
    Creative("creative", "🎨", "Creative", Color(0xFFAF52DE)),
    Chill("chill", "😌", "Chill", Color(0xFF34C759)),
    Hype("hype", "🎉", "Hype", Color(0xFFFF2D55)),
    ;

    val filledIcon: String get() = icon

    companion object {
        val allCases: List<ReactionType> = entries

        fun fromRaw(raw: String?): ReactionType? =
            entries.firstOrNull { it.rawValue == raw }
    }
}

data class MomentReactionState(
    val type: ReactionType?,
    val count: Int,
    val hasReacted: Boolean,
)

object ReactionButtonMetrics {
    const val buttonSizeDp = 44f
    const val emojiSizeSp = 24f
    const val badgeFontSp = 10f
}

/** Port de `UserReactionUsageTracker` — prefs `reactionUsage_{userId}`. */
class UserReactionUsageTracker(
    context: Context,
    private val userId: String,
) {
    private val prefs = context.applicationContext
        .getSharedPreferences("reaction_usage", Context.MODE_PRIVATE)
    private val key = "reactionUsage_$userId"
    private var reactionUsageCounts: MutableMap<String, Int> = loadUsageCounts()

    private fun loadUsageCounts(): MutableMap<String, Int> {
        val json = prefs.getString(key, null)
        if (json != null) {
            return runCatching {
                val obj = JSONObject(json)
                buildMap {
                    obj.keys().forEach { k -> put(k, obj.getInt(k)) }
                }.toMutableMap()
            }.getOrElse { defaultCounts() }
        }
        return defaultCounts()
    }

    private fun defaultCounts(): MutableMap<String, Int> =
        ReactionType.allCases.associate { it.rawValue to 0 }.toMutableMap()

    fun incrementUsage(forReaction: ReactionType) {
        reactionUsageCounts[forReaction.rawValue] =
            (reactionUsageCounts[forReaction.rawValue] ?: 0) + 1
        saveUsageCounts()
    }

    private fun saveUsageCounts() {
        val obj = JSONObject()
        reactionUsageCounts.forEach { (k, v) -> obj.put(k, v) }
        prefs.edit().putString(key, obj.toString()).apply()
    }

    fun getReactionsOrderedByUsage(): List<ReactionType> =
        ReactionType.allCases.sortedByDescending { reactionUsageCounts[it.rawValue] ?: 0 }
}

/**
 * Port de `ModernReactionButton` (legacy en reacciones.swift).
 * El rail usa [MomentReactionButton] / EpicReactionButton.
 */
@Composable
fun ModernReactionButton(
    moment: FeedMoment,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val firestore = remember { FirestoreService() }
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    var showReactionPicker by remember { mutableStateOf(false) }
    var currentReaction by remember(moment.id) { mutableStateOf<ReactionType?>(null) }
    var reactionCount by remember(moment.id) { mutableIntStateOf(moment.reactionCount) }
    var hasReacted by remember(moment.id) { mutableStateOf(false) }
    val accent = Color.fromHex("007AFF")

    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier
                .size(50.dp)
                .shadow(4.dp, CircleShape, clip = false, ambientColor = Color.Black.copy(0.1f), spotColor = Color.Black.copy(0.1f))
                .momentsChromeGlass(CircleShape, interactive = true)
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        if (hasReacted) {
                            listOf(
                                (currentReaction?.color ?: Color.White).copy(0.6f),
                                (currentReaction?.color ?: accent).copy(0.8f),
                            )
                        } else {
                            listOf(Color.White.copy(0.3f), accent.copy(0.3f))
                        },
                    ),
                    shape = CircleShape,
                )
                .clickable {
                    if (hasReacted) {
                        val type = currentReaction ?: return@clickable
                        val previous = type
                        hasReacted = false
                        currentReaction = null
                        reactionCount = (reactionCount - 1).coerceAtLeast(0)
                        val userId = uid ?: return@clickable
                        scope.launch {
                            runCatching {
                                firestore.removeReaction(moment.id, type.rawValue, userId, moment.authorId)
                            }.onFailure {
                                hasReacted = true
                                currentReaction = previous
                                reactionCount += 1
                            }
                        }
                    } else {
                        showReactionPicker = true
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (hasReacted) (currentReaction?.filledIcon ?: "❤️") else "♡",
                style = TextStyle(
                    fontSize = with(density) { legacyPoppinsSize(context, 24).toSp() },
                    fontWeight = FontWeight.Medium,
                    brush = if (hasReacted) {
                        val c = currentReaction?.color ?: Color.Red
                        Brush.linearGradient(listOf(c, c.copy(0.7f)))
                    } else {
                        Brush.linearGradient(
                            listOf(Color.Blue, Color(0xFFAF52DE), Color(0xFFFF2D55)),
                        )
                    },
                ),
            )
        }

        if (reactionCount > 0) {
            Text(
                text = "$reactionCount",
                color = Color.White.copy(0.8f),
                fontSize = with(density) { legacyPoppinsSize(context, 12).toSp() },
                fontWeight = FontWeight.Medium,
            )
        }

        AnimatedVisibility(
            visible = showReactionPicker,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
        ) {
            ReactionPickerView(
                onReactionSelected = { reaction ->
                    val userId = uid ?: return@ReactionPickerView
                    hasReacted = true
                    currentReaction = reaction
                    reactionCount += 1
                    showReactionPicker = false
                    scope.launch {
                        runCatching {
                            firestore.addReaction(moment.id, reaction.rawValue, userId, moment.authorId)
                        }.onFailure {
                            hasReacted = false
                            currentReaction = null
                            reactionCount = (reactionCount - 1).coerceAtLeast(0)
                        }
                    }
                },
                onClose = { showReactionPicker = false },
            )
        }
    }
}

/** Port de `ReactionPickerView` (reacciones.swift). */
@Composable
fun ReactionPickerView(
    onReactionSelected: (ReactionType) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    val usageTracker = remember(uid) { UserReactionUsageTracker(context, uid) }
    val ordered = usageTracker.getReactionsOrderedByUsage()
    val accent = Color.fromHex("007AFF")

    Column(
        modifier
            .offset(y = (-80).dp)
            .shadow(20.dp, RoundedCornerShape(25.dp), clip = false, ambientColor = Color.Black.copy(0.3f), spotColor = Color.Black.copy(0.3f))
            .momentsChromeGlass(RoundedCornerShape(25.dp), interactive = true)
            .border(
                width = 1.5.dp,
                brush = Brush.linearGradient(listOf(Color.White.copy(0.3f), accent.copy(0.5f))),
                shape = RoundedCornerShape(25.dp),
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ordered.forEach { reaction ->
                Column(
                    Modifier.clickable {
                        usageTracker.incrementUsage(reaction)
                        onReactionSelected(reaction)
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .size(50.dp)
                                .blur(8.dp)
                                .background(reaction.color.copy(0.2f), CircleShape),
                        )
                        Box(
                            Modifier
                                .size(44.dp)
                                .shadow(
                                    6.dp,
                                    CircleShape,
                                    clip = false,
                                    ambientColor = reaction.color.copy(0.4f),
                                    spotColor = reaction.color.copy(0.4f),
                                )
                                .momentsChromeGlass(CircleShape, interactive = true)
                                .border(
                                    width = 2.dp,
                                    brush = Brush.linearGradient(
                                        listOf(reaction.color.copy(0.8f), reaction.color),
                                    ),
                                    shape = CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = reaction.filledIcon,
                                style = TextStyle(
                                    fontSize = with(density) { legacyPoppinsSize(context, 22).toSp() },
                                    fontWeight = FontWeight.Bold,
                                    brush = Brush.linearGradient(
                                        listOf(reaction.color, reaction.color.copy(0.7f)),
                                    ),
                                    shadow = Shadow(
                                        color = reaction.color.copy(0.6f),
                                        blurRadius = 2f,
                                    ),
                                ),
                            )
                        }
                    }
                    Text(
                        text = reaction.displayName,
                        color = Color.White,
                        fontSize = with(density) { legacyPoppinsSize(context, 10).toSp() },
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Text(
            text = "Cerrar",
            color = Color.White.copy(0.8f),
            fontSize = with(density) { legacyPoppinsSize(context, 14).toSp() },
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .padding(bottom = 16.dp)
                .clip(RoundedCornerShape(percent = 50))
                .momentsChromeGlass(RoundedCornerShape(percent = 50), interactive = true)
                .border(1.dp, Color.White.copy(0.3f), RoundedCornerShape(percent = 50))
                .clickable(onClick = onClose)
                .padding(horizontal = 24.dp, vertical = 8.dp),
        )
    }
}
