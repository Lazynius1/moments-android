package com.moments.android.ui.login

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R

// Paleta de marca del glow de bienvenida (espejo de WelcomeGlow.colors en iOS).
private val AuroraColors = listOf(
    Color(0xFF007AFF),
    Color(0xFFAF52DE),
    Color(0xFFFF375F),
    Color(0xFF02C39A),
)

// MARK: - Halo aurora tras el logo (versión estática de WelcomeAuroraHalo)
@Composable
fun WelcomeAuroraHalo(modifier: Modifier = Modifier, size: Dp = 300.dp) {
    Box(
        modifier = modifier.size(size).blur(50.dp).alpha(0.55f),
        contentAlignment = Alignment.Center,
    ) {
        HaloBlob(AuroraColors[0], (-52).dp, (-30).dp)
        HaloBlob(AuroraColors[1], 44.dp, (-22).dp)
        HaloBlob(AuroraColors[2], (-22).dp, 46.dp)
        HaloBlob(AuroraColors[3], 52.dp, 30.dp)
    }
}

@Composable
private fun HaloBlob(color: Color, dx: Dp, dy: Dp) {
    Box(
        Modifier
            .offset(dx, dy)
            .size(190.dp)
            .background(Brush.radialGradient(listOf(color.copy(alpha = 0.7f), Color.Transparent))),
    )
}

// MARK: - Campo de texto (equivalente a LiquidGlassTextField)
@Composable
fun AuthTextField(
    icon: ImageVector,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(AuthMetrics.fieldCornerRadius)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AuthMetrics.fieldHeight)
            .clip(shape)
            .background(AuthColors.subtle(0.05f))
            .border(BorderStroke(0.75.dp, AuthColors.subtle(if (isFocused) 0.28f else 0.12f)), shape)
            .padding(horizontal = AuthMetrics.fieldHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = AuthColors.secondary(if (isFocused) 0.95f else 0.48f),
            modifier = Modifier.width(AuthMetrics.iconSlotWidth).size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(placeholder, color = AuthColors.secondary(0.52f), fontSize = AuthMetrics.fieldFontSize)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                interactionSource = interactionSource,
                textStyle = TextStyle(color = AuthColors.primary, fontSize = AuthMetrics.fieldFontSize),
                cursorBrush = SolidColor(AuthColors.primary),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, capitalization = capitalization),
            )
        }
    }
}

// MARK: - Campo de contraseña (equivalente a LiquidGlassSecureField)
@Composable
fun AuthSecureField(
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    isVisible: Boolean,
    onToggleVisible: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(AuthMetrics.fieldCornerRadius)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AuthMetrics.fieldHeight)
            .clip(shape)
            .background(AuthColors.subtle(0.05f))
            .border(BorderStroke(0.75.dp, AuthColors.subtle(if (isFocused) 0.28f else 0.12f)), shape)
            .padding(horizontal = AuthMetrics.fieldHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Lock,
            contentDescription = null,
            tint = AuthColors.secondary(if (isFocused) 0.95f else 0.48f),
            modifier = Modifier.width(AuthMetrics.iconSlotWidth).size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(placeholder, color = AuthColors.secondary(0.52f), fontSize = AuthMetrics.fieldFontSize)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                interactionSource = interactionSource,
                visualTransformation = if (isVisible) VisualTransformation.None else PasswordVisualTransformation(),
                textStyle = TextStyle(color = AuthColors.primary, fontSize = AuthMetrics.fieldFontSize),
                cursorBrush = SolidColor(AuthColors.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
        }
        IconButton(onClick = onToggleVisible, modifier = Modifier.size(28.dp)) {
            Icon(
                if (isVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                contentDescription = null,
                tint = AuthColors.secondary(0.48f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

// MARK: - Divisor "o continúa con" (equivalente a EnhancedDividerView)
@Composable
fun AuthDivider(text: String, modifier: Modifier = Modifier) {
    val lineColor = AuthColors.subtle(0.26f)
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.weight(1f).heightIn(min = 1.dp).background(lineColor))
        Text(text, color = AuthColors.secondary(0.72f), fontSize = 13.sp, maxLines = 1)
        Box(Modifier.weight(1f).heightIn(min = 1.dp).background(lineColor))
    }
}

// MARK: - Disclaimer (equivalente a LoginDisclaimerView)
@Composable
fun LoginDisclaimer(modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.login_disclaimer_line1),
            color = AuthColors.secondary(0.42f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        Text(text = disclaimerLine2(), fontSize = 12.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
    }
}

@Composable
private fun disclaimerLine2(): AnnotatedString {
    val prefix = stringResource(R.string.login_disclaimer_line2_prefix)
    val middle = stringResource(R.string.login_disclaimer_line2_middle)
    val suffix = stringResource(R.string.login_disclaimer_line2_suffix)
    return buildAnnotatedString {
        withStyle(SpanStyle(color = AuthColors.secondary(0.42f))) { append(prefix) }
        withStyle(SpanStyle(color = AuthColors.secondary(0.78f))) { append("lazynius") }
        withStyle(SpanStyle(color = AuthColors.secondary(0.42f))) { append(middle) }
        withStyle(SpanStyle(color = AuthColors.secondary(0.78f))) { append("Moments") }
        withStyle(SpanStyle(color = AuthColors.secondary(0.42f))) { append(suffix) }
    }
}

// MARK: - Botón primario con degradado aurora (equivalente a AuroraGlassButton / EnhancedLoginButton)
@Composable
fun AuthPrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    isEnabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(AuthMetrics.buttonCornerRadius)
    val active = isEnabled && !isLoading
    val brush = if (isEnabled) {
        Brush.linearGradient(AuroraColors)
    } else {
        Brush.linearGradient(AuroraColors.map { it.copy(alpha = 0.4f) })
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AuthMetrics.buttonHeight)
            .clip(shape)
            .background(brush)
            .clickable(enabled = active, onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            color = Color.White.copy(alpha = if (isEnabled) 1f else 0.75f),
            fontSize = AuthMetrics.buttonFontSize,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// MARK: - Botón secundario tipo "outline" (equivalente a LiquidGlassButton .secondary)
@Composable
fun AuthOutlineButton(
    text: String,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    isEnabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(AuthMetrics.buttonCornerRadius)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AuthMetrics.buttonHeight)
            .clip(shape)
            .background(AuthColors.subtle(0.05f))
            .border(BorderStroke(0.8.dp, AuthColors.subtle(0.18f)), shape)
            .clickable(enabled = isEnabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            leadingIcon()
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = text,
            color = AuthColors.primary,
            fontSize = AuthMetrics.buttonFontSize,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
