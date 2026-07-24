package com.moments.android.views.creator.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.extensions.momentsChromeGlass

/** Port de `StoryEditorChromeColor`. */
object StoryEditorChromeColor {
    fun icon(isDark: Boolean): Color = if (isDark) Color.White else Color.Black
}

/** Port de `EditingToolButton`. */
@Composable
fun EditingToolButton(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = title, tint = StoryEditorChromeColor.icon(isDark), modifier = Modifier.size(24.dp))
        Text(title, color = StoryEditorChromeColor.icon(isDark), fontSize = 12.sp)
    }
}

/** Port de `EditingToolIcon`; el icono personalizado se recibe como vector Android. */
@Composable
fun EditingToolIcon(
    icon: ImageVector,
    onClick: () -> Unit,
    isDark: Boolean,
    usesCustomStickerGlyph: Boolean = false,
    customStickerGlyph: ImageVector? = null,
    modifier: Modifier = Modifier,
) {
    val resolved = if (usesCustomStickerGlyph) customStickerGlyph ?: icon else icon
    Icon(
        imageVector = resolved,
        contentDescription = null,
        tint = StoryEditorChromeColor.icon(isDark),
        modifier = modifier
            .size(44.dp)
            .momentsChromeGlass(CircleShape, interactive = true)
            .clickable(onClick = onClick)
            .padding(12.dp),
    )
}

/** Port de `OptionRow`. */
@Composable
fun OptionRow(
    icon: ImageVector,
    title: String,
    value: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Color.Gray.copy(alpha = 0.1f))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.width(30.dp))
        Text(title, color = Color.White)
        Spacer(Modifier.weight(1f))
        value?.let { Text(it, color = Color.Gray, fontSize = 14.sp) }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.Gray, modifier = Modifier.padding(start = 8.dp).size(14.dp))
    }
}

/** Port de `ShareOptionToggle`. */
@Composable
fun ShareOptionToggle(
    platform: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
) {
    var isOn by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Text(platform, color = Color.White, modifier = Modifier.padding(start = 10.dp))
        Spacer(Modifier.weight(1f))
        Switch(checked = isOn, onCheckedChange = { isOn = it })
    }
}
