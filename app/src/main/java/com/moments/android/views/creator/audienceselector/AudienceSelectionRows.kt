package com.moments.android.views.creator.audienceselector

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.extensions.fromHex
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.views.components.AudienceIconMetrics
import com.moments.android.views.components.AudienceIconView

private val AudienceAccent = Color(0xFF00A896)
private val AudienceBlue = Color(0xFF007AFF)

private fun CustomAudienceList.tint(): Color = Color.fromHex(color ?: "00A896")

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
    AudienceListRowContent(
        list = list,
        isSelected = isSelected,
        onTap = onTap,
        modifier = modifier,
        content = content,
        tint = tint,
        trailing = {
            if (isSelected) Icon(Icons.Filled.CheckCircle, null, tint = tint, modifier = Modifier.size(24.dp))
        },
    )
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
    val content = if (dark) Color.White else Color.Black
    val iconColor = if (audience == ContentAudience.BEST_FRIENDS) Color(0xFF34C759) else content
    val iconSize = if (audience == ContentAudience.ONLY_ME) AudienceIconMetrics.gridCard else AudienceIconMetrics.gridCardEmphasis
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onTap)
            .padding(horizontal = 2.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AudienceIconView(
            audience = audience,
            size = iconSize,
            tintColor = iconColor,
            modifier = Modifier.size(40.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(audience.title, color = content.copy(alpha = if (isSelected) 1f else .82f), fontSize = 16.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium)
            Text(audience.description, color = content.copy(alpha = if (isSelected) .55f else .4f), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (isSelected) {
            Box(
                Modifier.size(26.dp).background(if (dark) Color.White.copy(.14f) else Color.Black.copy(.08f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Check, null, tint = content, modifier = Modifier.size(13.dp)) }
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
    Box(
        modifier
            .width(96.dp)
            .heightIn(min = 116.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onTap)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(48.dp).background(tint.copy(if (isSelected) .2f else .1f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Group, null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Text(list.name, color = content, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${list.members.size} people", color = content.copy(.5f), fontSize = 11.sp)
        }
        if (isSelected) {
            Box(Modifier.align(Alignment.TopEnd).size(22.dp).background(if (dark) Color.White.copy(.14f) else Color.Black.copy(.08f), CircleShape), contentAlignment = Alignment.Center) {
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
    AudienceListRowContent(
        list = list,
        isSelected = isSelected,
        onTap = onTap,
        modifier = modifier,
        content = content,
        tint = tint,
        modern = true,
        trailing = {
            if (isSelected) {
                Icon(Icons.Filled.CheckCircle, null, tint = tint, modifier = Modifier.size(24.dp))
            } else {
                Box(Modifier.size(28.dp).momentsChromeGlass(CircleShape, interactive = true), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = content, modifier = Modifier.size(13.dp))
                }
            }
        },
    )
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
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) AudienceBlue.copy(.1f) else content.copy(.05f))
            .border(1.dp, if (isSelected) AudienceBlue.copy(.5f) else content.copy(.1f), RoundedCornerShape(16.dp))
            .clickable(onClick = onTap)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(48.dp).background(if (isSelected) AudienceBlue.copy(.2f) else content.copy(.1f), CircleShape), contentAlignment = Alignment.Center) {
            AudienceIconView(audience, AudienceIconMetrics.row, if (isSelected) AudienceBlue else content)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(audience.title, color = content, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(customCount?.let { "$it people" } ?: audience.description, color = content.copy(.55f), fontSize = 13.sp)
        }
        if (isSelected) Icon(Icons.Filled.CheckCircle, null, tint = AudienceBlue, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun AudienceListRowContent(
    list: CustomAudienceList,
    isSelected: Boolean,
    onTap: () -> Unit,
    modifier: Modifier,
    content: Color,
    tint: Color,
    modern: Boolean = false,
    trailing: @Composable () -> Unit,
) {
    val background = if (modern) content.copy(.06f) else if (isSelected) tint.copy(.1f) else content.copy(.05f)
    val border = if (isSelected) tint.copy(if (modern) .3f else .5f) else content.copy(.1f)
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .border(1.dp, border, RoundedCornerShape(16.dp))
            .clickable(onClick = onTap)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(48.dp).background(tint.copy(if (isSelected) .2f else .1f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Group, null, tint = tint.copy(if (isSelected) 1f else .8f), modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(list.name, color = content, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Person, null, tint = content.copy(.6f), modifier = Modifier.size(12.dp))
                Text("${list.members.size} people", color = content.copy(.6f), fontSize = 13.sp)
            }
            list.description?.takeIf { it.isNotBlank() }?.let { description ->
                Text(description, color = content.copy(.5f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing()
    }
}
