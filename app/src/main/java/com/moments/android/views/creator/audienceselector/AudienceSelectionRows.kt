package com.moments.android.views.creator.audienceselector

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moments.android.R
import com.moments.android.extensions.fromHex
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.utilities.MomentsPressDefaults
import com.moments.android.utilities.MomentsPressSpec
import com.moments.android.utilities.legacyPoppinsSize
import com.moments.android.utilities.momentsPress
import com.moments.android.views.components.AudienceIconMetrics
import com.moments.android.views.components.AudienceIconView

private val AudienceBlue = Color(0xFF007AFF)
private val BestFriendsGreen = Color(0xFF34C759)
private val CardShape = RoundedCornerShape(16.dp)

private fun CustomAudienceList.tint(): Color = Color.fromHex(color ?: "00A896")

/** SF Symbol names de `CustomAudienceList.predefinedIcons` → Material. */
internal fun listIconVector(icon: String?): ImageVector = when (icon) {
    "briefcase.fill" -> Icons.Filled.Work
    "house.fill" -> Icons.Filled.Home
    "graduationcap.fill" -> Icons.Filled.School
    "heart.fill" -> Icons.Filled.Favorite
    "star.fill" -> Icons.Filled.Star
    "flag.fill" -> Icons.Filled.Flag
    "bolt.fill" -> Icons.Filled.Bolt
    else -> Icons.Filled.Group // person.3.fill / null
}

@Composable
fun contentAudienceTitle(audience: ContentAudience): String = stringResource(
    when (audience) {
        ContentAudience.EVERYONE -> R.string.audience_type_everyone
        ContentAudience.MUTUALS -> R.string.audience_type_mutuals
        ContentAudience.BEST_FRIENDS -> R.string.audience_type_best_friends
        ContentAudience.CUSTOM -> R.string.audience_type_custom
        ContentAudience.CUSTOM_LIST -> R.string.audience_type_custom_list
        ContentAudience.ONLY_ME -> R.string.audience_type_only_me
    },
)

@Composable
fun contentAudienceDescription(audience: ContentAudience): String = stringResource(
    when (audience) {
        ContentAudience.EVERYONE -> R.string.audience_description_everyone
        ContentAudience.MUTUALS -> R.string.audience_description_mutuals
        ContentAudience.BEST_FRIENDS -> R.string.audience_description_best_friends
        ContentAudience.CUSTOM -> R.string.audience_description_custom
        ContentAudience.CUSTOM_LIST -> R.string.audience_description_custom_list
        ContentAudience.ONLY_ME -> R.string.audience_description_only_me
    },
)

/** Port de `CustomListRow`. `onDelete` se conserva para el flujo de gestión de listas. */
@Composable
fun CustomListRow(
    list: CustomAudienceList,
    isSelected: Boolean,
    onTap: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val content = if (dark) Color.White else Color.Black
    val tint = list.tint()
    val context = LocalContext.current
    val density = LocalDensity.current
    val people = stringResource(R.string.audience_people_count, list.members.size)

    Row(
        modifier
            .fillMaxWidth()
            .background(
                if (isSelected) tint.copy(alpha = 0.1f)
                else content.copy(alpha = 0.05f),
                CardShape,
            )
            .border(
                1.dp,
                if (isSelected) tint.copy(alpha = 0.5f) else content.copy(alpha = 0.1f),
                CardShape,
            )
            .clickable(onClick = onTap)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier
                .size(48.dp)
                .background(tint.copy(alpha = if (isSelected) 0.2f else 0.1f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                listIconVector(list.icon),
                contentDescription = null,
                tint = tint.copy(alpha = if (isSelected) 1f else 0.8f),
                modifier = Modifier.size(20.dp),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                list.name,
                color = content,
                fontSize = with(density) { legacyPoppinsSize(context, 16).toSp() },
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Person, null, tint = Color.Gray, modifier = Modifier.size(12.dp))
                Text(
                    people,
                    color = Color.Gray,
                    fontSize = with(density) { legacyPoppinsSize(context, 13).toSp() },
                )
            }
            list.description?.takeIf { it.isNotBlank() }?.let { description ->
                Text(
                    description,
                    color = Color.Gray,
                    fontSize = with(density) { legacyPoppinsSize(context, 12).toSp() },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (isSelected) {
            Icon(Icons.Filled.CheckCircle, null, tint = tint, modifier = Modifier.size(24.dp))
        }
    }
}

/** Port de `AudienceGridCard`: opción plana para las audiencias predefinidas. */
@Composable
fun AudienceGridCard(
    audience: ContentAudience,
    isSelected: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val primaryText = if (dark) Color.White else Color.Black
    val iconColor = if (audience == ContentAudience.BEST_FRIENDS) BestFriendsGreen else primaryText
    val iconSize = when (audience) {
        ContentAudience.ONLY_ME -> AudienceIconMetrics.gridCard
        else -> AudienceIconMetrics.gridCardEmphasis
    }
    val context = LocalContext.current
    val density = LocalDensity.current

    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .padding(horizontal = 2.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            AudienceIconView(
                audience = audience,
                size = iconSize,
                tintColor = iconColor,
                // iOS: .opacity(isSelected ? 1 : 0.42)
                modifier = Modifier.graphicsLayer { alpha = if (isSelected) 1f else 0.42f },
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                contentAudienceTitle(audience),
                color = primaryText.copy(alpha = if (isSelected) 1f else 0.82f),
                fontSize = with(density) { legacyPoppinsSize(context, 16).toSp() },
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            )
            // iOS: foreground 0.55 × opacity(isSelected ? 1 : 0.72)
            Text(
                contentAudienceDescription(audience),
                color = primaryText.copy(alpha = 0.55f * if (isSelected) 1f else 0.72f),
                fontSize = with(density) { legacyPoppinsSize(context, 13).toSp() },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        if (isSelected) {
            Box(
                Modifier
                    .size(26.dp)
                    .background(
                        if (dark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.08f),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, null, tint = primaryText, modifier = Modifier.size(13.dp))
            }
        }
    }
}

/** Port de `CustomListCard` para el carrusel de listas. */
@Composable
fun CustomListCard(
    list: CustomAudienceList,
    isSelected: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val content = if (dark) Color.White else Color.Black
    val tint = list.tint()
    val context = LocalContext.current
    val density = LocalDensity.current
    // iOS hardcodea "personas"; usamos la clave localizada `audience.people.count`.
    val people = stringResource(R.string.audience_people_count, list.members.size)
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier
            .width(96.dp)
            .momentsPress(
                interaction,
                MomentsPressSpec(
                    scale = 0.95f,
                    pressedOpacity = 1f,
                    haptic = MomentsPressDefaults.PressHaptic.NONE,
                ),
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onTap),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            // iOS: opacity isSelected ? 1 : 0.55
            modifier = Modifier.graphicsLayer { alpha = if (isSelected) 1f else 0.55f },
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .background(tint.copy(alpha = if (isSelected) 0.2f else 0.1f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(listIconVector(list.icon), null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    list.name,
                    color = content,
                    fontSize = with(density) { legacyPoppinsSize(context, 14).toSp() },
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    people,
                    color = content.copy(alpha = 0.5f),
                    fontSize = with(density) { legacyPoppinsSize(context, 11).toSp() },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (isSelected) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .size(22.dp)
                    .background(
                        if (dark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.08f),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, null, tint = content, modifier = Modifier.size(11.dp))
            }
        }
    }
}

/** Port de `CustomListRowModern`. */
@Composable
fun CustomListRowModern(
    list: CustomAudienceList,
    isSelected: Boolean,
    onTap: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val content = if (dark) Color.White else Color.Black
    val tint = list.tint()
    val context = LocalContext.current
    val density = LocalDensity.current
    val people = stringResource(R.string.audience_people_count, list.members.size)
    val interaction = remember { MutableInteractionSource() }

    Row(
        modifier
            .fillMaxWidth()
            .momentsPress(
                interaction,
                MomentsPressSpec(
                    scale = 0.98f,
                    pressedOpacity = 1f,
                    haptic = MomentsPressDefaults.PressHaptic.NONE,
                ),
            )
            .momentsChromeGlass(CardShape, interactive = true)
            .border(
                1.dp,
                if (isSelected) tint.copy(alpha = 0.3f) else content.copy(alpha = 0.1f),
                CardShape,
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onTap)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier
                .size(48.dp)
                .background(tint.copy(alpha = if (isSelected) 0.15f else 0.1f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                listIconVector(list.icon),
                null,
                tint = tint.copy(alpha = if (isSelected) 1f else 0.8f),
                modifier = Modifier.size(20.dp),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                list.name,
                color = content,
                fontSize = with(density) { legacyPoppinsSize(context, 16).toSp() },
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Person,
                    null,
                    tint = content.copy(alpha = 0.6f),
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    people,
                    color = content.copy(alpha = 0.6f),
                    fontSize = with(density) { legacyPoppinsSize(context, 13).toSp() },
                )
            }
            list.description?.takeIf { it.isNotBlank() }?.let { description ->
                Text(
                    description,
                    color = content.copy(alpha = 0.5f),
                    fontSize = with(density) { legacyPoppinsSize(context, 12).toSp() },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (isSelected) {
            Icon(Icons.Filled.CheckCircle, null, tint = tint, modifier = Modifier.size(24.dp))
        } else {
            Box(
                Modifier
                    .size(28.dp)
                    .momentsChromeGlass(CircleShape, interactive = true),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    null,
                    tint = content,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
    }
}

/** Port de `AudienceOptionRow`. */
@Composable
fun AudienceOptionRow(
    audience: ContentAudience,
    isSelected: Boolean,
    customCount: Int?,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val content = if (dark) Color.White else Color.Black
    val context = LocalContext.current
    val density = LocalDensity.current
    val subtitle = if (customCount != null) {
        stringResource(R.string.audience_people_count, customCount)
    } else {
        contentAudienceDescription(audience)
    }

    Row(
        modifier
            .fillMaxWidth()
            .background(
                if (isSelected) AudienceBlue.copy(alpha = 0.1f) else content.copy(alpha = 0.05f),
                CardShape,
            )
            .border(
                1.dp,
                if (isSelected) AudienceBlue.copy(alpha = 0.5f) else content.copy(alpha = 0.1f),
                CardShape,
            )
            .clickable(onClick = onTap)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier
                .size(48.dp)
                .background(
                    if (isSelected) AudienceBlue.copy(alpha = 0.2f) else content.copy(alpha = 0.1f),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            AudienceIconView(
                audience = audience,
                size = AudienceIconMetrics.row,
                tintColor = if (isSelected) AudienceBlue else content,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                contentAudienceTitle(audience),
                color = content,
                fontSize = with(density) { legacyPoppinsSize(context, 16).toSp() },
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                subtitle,
                color = Color.Gray,
                fontSize = with(density) { legacyPoppinsSize(context, 13).toSp() },
            )
        }
        if (isSelected) {
            Icon(Icons.Filled.CheckCircle, null, tint = AudienceBlue, modifier = Modifier.size(24.dp))
        }
    }
}
