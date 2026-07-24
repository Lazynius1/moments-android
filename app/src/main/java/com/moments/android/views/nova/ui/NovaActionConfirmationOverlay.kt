package com.moments.android.views.nova.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.moments.android.R
import com.moments.android.views.nova.agent.NovaPendingAction

/** Port de `NovaActionConfirmationOverlay.swift`. */
@Composable
fun NovaActionConfirmationOverlay(
    action: NovaPendingAction,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val primaryText = if (isDark) Color.White else Color.Black.copy(alpha = 0.88f)
    val secondaryText = if (isDark) Color.White.copy(alpha = 0.82f) else Color.Black.copy(alpha = 0.62f)
    val scrim = Color.Black.copy(alpha = if (isDark) 0.45f else 0.20f)
    val cardShape = RoundedCornerShape(24.dp)
    val buttonShape = RoundedCornerShape(16.dp)

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(scrim).clickable(onClick = onCancel),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 360.dp)
                    .fillMaxWidth()
                    .clip(cardShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .semantics { dialog() }
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = action.title,
                        color = primaryText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(R.string.nova_confirm_subtitle),
                        color = secondaryText,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                    )
                }
                action.previewImage?.let { preview ->
                    androidx.compose.foundation.Image(
                        bitmap = preview.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(120.dp).clip(buttonShape),
                    )
                }
                Text(
                    text = action.detail,
                    color = secondaryText,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NovaConfirmationButton(
                        text = stringResource(R.string.common_cancel),
                        textColor = primaryText,
                        shape = buttonShape,
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                    )
                    NovaConfirmationButton(
                        text = stringResource(R.string.nova_confirm_approve),
                        textColor = primaryText,
                        shape = buttonShape,
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun NovaConfirmationButton(
    text: String,
    textColor: Color,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = textColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
