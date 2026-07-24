package com.moments.android.views.creator.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectDragGestures
import kotlin.math.PI
import kotlin.math.atan2

/** Port de `StoryColorPickerPanel` de `StoryColorPickerView.swift`. */
@Composable
fun StoryColorPickerPanel(
    selectedColor: Color,
    onSelectedColorChange: (Color) -> Unit,
    swatchColors: List<Color>,
    suggestedColors: List<Color>,
    onPickFromCanvas: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var hue by remember { mutableFloatStateOf(0.58f) }
    var saturation by remember { mutableFloatStateOf(0.85f) }
    var brightness by remember { mutableFloatStateOf(0.95f) }

    fun syncFromSelected() {
        val hsb = FloatArray(3)
        AndroidColor.colorToHSV(selectedColor.toArgb(), hsb)
        hue = hsb[0] / 360f
        saturation = hsb[1]
        brightness = hsb[2]
    }
    fun applyHsb() {
        onSelectedColorChange(
            Color(AndroidColor.HSVToColor(floatArrayOf(hue * 360f, saturation, brightness))),
        )
    }
    LaunchedEffect(selectedColor) { syncFromSelected() }

    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.72f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier
                    .size(52.dp)
                    .pointerInput(Unit) {
                        fun updateHue(position: androidx.compose.ui.geometry.Offset) {
                            val center = size.width / 2f
                            var angle = atan2(position.y - center, position.x - center)
                            if (angle < 0) angle += (2 * PI).toFloat()
                            hue = angle / (2 * PI).toFloat()
                            applyHsb()
                        }
                        detectDragGestures(
                            onDragStart = ::updateHue,
                            onDrag = { change, _ -> updateHue(change.position) },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.matchParentSize()) {
                    drawCircle(Brush.sweepGradient(listOf(
                        Color.Red, Color.Yellow, Color.Green, Color.Cyan,
                        Color.Blue, Color.Magenta, Color.Red,
                    )))
                }
                Box(
                    Modifier
                        .size(28.dp)
                        .background(selectedColor, CircleShape)
                        .clip(CircleShape),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StoryColorSliderRow("S", saturation) {
                    saturation = it
                    applyHsb()
                }
                StoryColorSliderRow("B", brightness) {
                    brightness = it
                    applyHsb()
                }
            }
        }

        if (suggestedColors.isNotEmpty()) {
            StoryColorSuggestedRow(title = "Suggested", colors = suggestedColors, selectedColor, onSelectedColorChange)
        }
        StoryColorSuggestedRow(title = null, colors = swatchColors, selectedColor, onSelectedColorChange)

        onPickFromCanvas?.let { pick ->
            Text(
                text = "Pick from photo",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.14f))
                    .clickable(onClick = pick)
                    .padding(vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun StoryColorSliderRow(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 8.dp),
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            modifier = Modifier.weight(1f).height(24.dp),
        )
    }
}

@Composable
private fun StoryColorSuggestedRow(
    title: String?,
    colors: List<Color>,
    selectedColor: Color,
    onSelectedColorChange: (Color) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        title?.let {
            Text(it, color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            colors.forEach { color ->
                val selected = color.toArgb() == selectedColor.toArgb()
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(color)
                        .clickable { onSelectedColorChange(color) }
                        .then(
                            if (selected) Modifier.background(Color.White.copy(alpha = 0.25f), CircleShape) else Modifier,
                        ),
                )
            }
        }
    }
}
