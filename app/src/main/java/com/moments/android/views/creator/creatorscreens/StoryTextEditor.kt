package com.moments.android.views.creator.creatorscreens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass

/**
 * Port mínimo de `StoryTextEditor.swift` + chrome fonts/colors (chunk 3).
 * Motion / effects / HSB / eyedropper: chunks siguientes.
 */
@Composable
fun StoryTextEditor(
    text: String,
    onTextChange: (String) -> Unit,
    selectedStyle: StoryTextStyle,
    onStyleChange: (StoryTextStyle) -> Unit,
    colorHex: String,
    onColorHexChange: (String) -> Unit,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fontFamily = rememberStoryFontFamily(selectedStyle)
    val textColor = parseStoryColorHex(colorHex)

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black.copy(0.35f)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(14.dp)
                .align(Alignment.TopCenter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .momentsChromeGlass(CircleShape, interactive = true)
                    .clickable(onClick = onCancel),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(42.dp)
                    .momentsChromeGlass(CircleShape, interactive = true)
                    .border(1.dp, Color.White.copy(0.2f), CircleShape)
                    .clickable(onClick = onDone),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }

        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            textStyle = TextStyle(
                color = textColor,
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                fontFamily = fontFamily,
            ),
            cursorBrush = SolidColor(textColor),
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.Center) {
                    if (text.isEmpty()) {
                        Text(
                            stringResource(R.string.story_editor_text_placeholder),
                            color = textColor.copy(0.45f),
                            fontSize = 30.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            fontFamily = fontFamily,
                        )
                    }
                    inner()
                }
            },
        )

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Fonts row — iOS StoryMomentsFontRow
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StoryTextStyle.fontPickerStyles.forEach { style ->
                    StoryFontChip(
                        style = style,
                        selected = style == selectedStyle,
                        onClick = {
                            onStyleChange(style)
                            onColorHexChange(style.defaultColorHex)
                        },
                    )
                }
            }

            // Color swatches — iOS StoryColorPickerPanel suggested row (sin wheel)
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StoryTextColorSwatches.presets.forEach { hex ->
                    val selected = hex.equals(colorHex, ignoreCase = true)
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(parseStoryColorHex(hex))
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) Color.White else Color.White.copy(0.35f),
                                shape = CircleShape,
                            )
                            .clickable { onColorHexChange(hex) },
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun StoryFontChip(
    style: StoryTextStyle,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val chipFamily = rememberStoryFontFamily(style)
    Text(
        style.displayName,
        color = if (selected) Color.Black else Color.White,
        fontSize = 15.sp,
        fontFamily = chipFamily,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color.White else Color.White.copy(0.14f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
