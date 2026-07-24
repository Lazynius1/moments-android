package com.moments.android.views.feed.controls

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.People
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moments.android.R
import com.moments.android.extensions.fromHex
import com.moments.android.utilities.legacyPoppinsSize

/** Port de `feedchange.swift` — enum, prefs y selectores alternativos. */
enum class FeedType(val rawValue: String) {
    Following("following"),
    ForYou("forYou"),
    ;

    @Composable
    fun title(): String = when (this) {
        Following -> stringResource(R.string.feed_following)
        ForYou -> stringResource(R.string.feed_for_you)
    }

    @Composable
    fun description(): String = when (this) {
        Following -> stringResource(R.string.feed_following_description)
        ForYou -> stringResource(R.string.feed_for_you_description)
    }

    fun icon(): ImageVector = when (this) {
        Following -> Icons.Filled.People
        ForYou -> Icons.Filled.AutoAwesome
    }

    companion object {
        val allCases = entries

        fun fromRaw(raw: String?): FeedType =
            entries.firstOrNull { it.rawValue == raw } ?: Following
    }
}

object FeedTypePreferences {
    private const val PREFS = "moments_feed_prefs"
    private const val KEY = "selectedFeedType"

    fun load(context: Context): FeedType {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, FeedType.Following.rawValue)
        return FeedType.fromRaw(raw)
    }

    fun save(context: Context, type: FeedType) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, type.rawValue)
            .apply()
    }

    /** Paridad iOS `UserDefaults.standard.removeObject(forKey: "selectedFeedType")`. */
    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY)
            .apply()
    }
}

@Composable
fun rememberFeedType(): Pair<FeedType, (FeedType) -> Unit> {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(FeedTypePreferences.load(context)) }
    val setter: (FeedType) -> Unit = { type ->
        selected = type
        FeedTypePreferences.save(context, type)
    }
    return selected to setter
}

private val Teal: Color get() = Color.fromHex("00A896")

// MARK: - ExpandableFeedSelector

@Composable
fun ExpandableFeedSelector(
    selectedFeedType: FeedType,
    onSelect: (FeedType) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val density = LocalDensity.current
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "chevron",
    )
    val card = RoundedCornerShape(16.dp)

    Column(
        modifier
            .padding(horizontal = 20.dp)
            .shadow(
                8.dp,
                card,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.1f),
                spotColor = Color.Black.copy(alpha = 0.1f),
            )
            .clip(card)
            .background(Color.White.copy(alpha = 0.18f))
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.2f), Teal.copy(alpha = 0.3f)),
                ),
                shape = card,
            ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FeedTypeIconBadge(icon = selectedFeedType.icon(), size = 32.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    selectedFeedType.title(),
                    color = Color.White,
                    fontSize = with(density) { legacyPoppinsSize(context, 16).toSp() },
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    selectedFeedType.description(),
                    color = Color.Gray.copy(alpha = 0.8f),
                    fontSize = with(density) { legacyPoppinsSize(context, 12).toSp() },
                )
            }
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = Color.Gray.copy(alpha = 0.6f),
                modifier = Modifier.size(14.dp).rotate(chevronRotation),
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FeedType.allCases.forEach { feedType ->
                    FeedOptionRow(
                        feedType = feedType,
                        isSelected = feedType == selectedFeedType,
                        onSelect = {
                            onSelect(feedType)
                            isExpanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedOptionRow(
    feedType: FeedType,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.02f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium),
        label = "optionScale",
    )
    val rowShape = RoundedCornerShape(12.dp)

    Row(
        Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(rowShape)
            .background(if (isSelected) Teal.copy(alpha = 0.1f) else Color.Transparent)
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            listOf(Teal.copy(alpha = 0.4f), Color.White.copy(alpha = 0.2f)),
                        ),
                        shape = rowShape,
                    )
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (isSelected) Teal.copy(alpha = 0.2f) else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                feedType.icon(),
                contentDescription = null,
                tint = if (isSelected) Teal else Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(14.dp),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                feedType.title(),
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f),
                fontSize = with(density) { legacyPoppinsSize(context, 14).toSp() },
                fontWeight = FontWeight.Medium,
            )
            Text(
                feedType.description(),
                color = Color.Gray.copy(alpha = if (isSelected) 0.8f else 0.6f),
                fontSize = with(density) { legacyPoppinsSize(context, 11).toSp() },
            )
        }
        if (isSelected) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Teal, modifier = Modifier.size(16.dp))
        }
    }
}

// MARK: - CompactFeedToggle

@Composable
fun CompactFeedToggle(
    selectedFeedType: FeedType,
    onSelect: (FeedType) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val density = LocalDensity.current
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        label = "compactChevron",
    )
    val capsule = RoundedCornerShape(percent = 50)

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .clip(capsule)
                .background(Color.White.copy(alpha = 0.18f))
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.2f), Teal.copy(alpha = 0.3f)),
                    ),
                    shape = capsule,
                )
                .clickable { isExpanded = !isExpanded }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(selectedFeedType.icon(), null, tint = Teal, modifier = Modifier.size(14.dp))
            Text(
                selectedFeedType.title(),
                color = Color.White,
                fontSize = with(density) { legacyPoppinsSize(context, 14).toSp() },
                fontWeight = FontWeight.SemiBold,
            )
            Icon(
                Icons.Filled.KeyboardArrowDown,
                null,
                tint = Color.Gray.copy(alpha = 0.6f),
                modifier = Modifier.size(10.dp).rotate(chevronRotation),
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = scaleIn(initialScale = 0.8f) + fadeIn(),
            exit = scaleOut(targetScale = 0.8f) + fadeOut(),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FeedType.allCases.filter { it != selectedFeedType }.forEach { feedType ->
                    Row(
                        Modifier
                            .clip(capsule)
                            .background(Color.White.copy(alpha = 0.18f))
                            .clickable {
                                onSelect(feedType)
                                isExpanded = false
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(feedType.icon(), null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(12.dp))
                        Text(
                            feedType.title(),
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = with(density) { legacyPoppinsSize(context, 12).toSp() },
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

// MARK: - SegmentedFeedToggle

@Composable
fun SegmentedFeedToggle(
    selectedFeedType: FeedType,
    onSelect: (FeedType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val isDark = isSystemInDarkTheme()
    val capsule = RoundedCornerShape(percent = 50)
    val primary = if (isDark) Color.White else Color.Black

    Row(
        modifier
            .clip(capsule)
            .background(Color.White.copy(alpha = 0.18f))
            .border(1.dp, primary.copy(alpha = 0.1f), capsule)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FeedType.allCases.forEach { feedType ->
            val selected = selectedFeedType == feedType
            val scale by animateFloatAsState(
                targetValue = if (selected) 1f else 0.95f,
                animationSpec = spring(dampingRatio = 0.75f, stiffness = 200f),
                label = "segScale",
            )
            Row(
                Modifier
                    .scale(scale)
                    .clip(capsule)
                    .background(if (selected) Teal else Color.Transparent)
                    .clickable { onSelect(feedType) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    feedType.icon(),
                    null,
                    tint = if (selected) Color.White else primary.copy(alpha = 0.7f),
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    feedType.title(),
                    color = if (selected) Color.White else primary.copy(alpha = 0.7f),
                    fontSize = with(density) { legacyPoppinsSize(context, 12).toSp() },
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

// MARK: - HeaderFeedChip

@Composable
fun HeaderFeedChip(
    selectedFeedType: FeedType,
    onSelect: (FeedType) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showOptions by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val density = LocalDensity.current
    val chevronRotation by animateFloatAsState(
        targetValue = if (showOptions) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "headerChevron",
    )
    val capsule = RoundedCornerShape(percent = 50)

    Column(modifier, horizontalAlignment = Alignment.Start) {
        Row(
            Modifier
                .shadow(
                    4.dp,
                    capsule,
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.1f),
                    spotColor = Color.Black.copy(alpha = 0.1f),
                )
                .clip(capsule)
                .background(Color.White.copy(alpha = 0.18f))
                .border(0.5.dp, Color.White.copy(alpha = 0.2f), capsule)
                .clickable { showOptions = !showOptions }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(Teal.copy(alpha = 0.8f)))
            Text(
                selectedFeedType.title(),
                color = Color.White,
                fontSize = with(density) { legacyPoppinsSize(context, 13).toSp() },
                fontWeight = FontWeight.SemiBold,
            )
            Icon(
                Icons.Filled.KeyboardArrowDown,
                null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(8.dp).rotate(chevronRotation),
            )
        }

        AnimatedVisibility(
            visible = showOptions,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                Modifier.padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FeedType.allCases.filter { it != selectedFeedType }.forEach { feedType ->
                    Row(
                        Modifier
                            .clip(capsule)
                            .background(Color.White.copy(alpha = 0.18f))
                            .clickable {
                                onSelect(feedType)
                                showOptions = false
                            }
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(Color.Gray.copy(alpha = 0.6f)))
                        Text(
                            feedType.title(),
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = with(density) { legacyPoppinsSize(context, 12).toSp() },
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedTypeIconBadge(icon: ImageVector, size: Dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.18f))
            .border(
                width = 1.5.dp,
                brush = Brush.linearGradient(listOf(Teal.copy(alpha = 0.6f), Color.White.copy(alpha = 0.3f))),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Teal, modifier = Modifier.size(size * 0.5f))
    }
}
