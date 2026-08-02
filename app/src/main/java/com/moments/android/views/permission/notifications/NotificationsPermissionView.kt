package com.moments.android.views.permission.notifications

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.moments.android.R
import com.moments.android.views.permission.shared.PermissionPhoneFrame
import com.moments.android.views.permission.shared.PermissionPhoneWallpaper
import com.moments.android.views.permission.shared.PermissionPrimerScaffold
import com.moments.android.views.permission.shared.PermissionPrimerStage
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Port de `NotificationsPermissionView.swift`. */
@Composable
fun NotificationsPermissionView(
    stage: PermissionPrimerStage = PermissionPrimerStage.PRIMER,
    primaryAction: () -> Unit,
    secondaryAction: () -> Unit,
) {
    val denied = stage == PermissionPrimerStage.DENIED
    PermissionPrimerScaffold(
        stage = stage,
        icon = { tint ->
            Icon(
                if (denied) Icons.Default.NotificationsOff else Icons.Default.NotificationsActive,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.fillMaxSize(),
            )
        },
        title = stringResource(
            if (denied) R.string.permission_notifications_denied_title
            else R.string.permission_notifications_primer_title,
        ),
        description = stringResource(
            if (denied) R.string.permission_notifications_denied_subtitle
            else R.string.permission_notifications_primer_subtitle,
        ),
        primaryActionTitle = stringResource(
            if (denied) R.string.permission_notifications_denied_open_settings
            else R.string.permission_notifications_primer_allow,
        ),
        secondaryActionTitle = stringResource(R.string.permission_notifications_primer_not_now),
        primaryAction = primaryAction,
        secondaryAction = secondaryAction,
    ) {
        PermissionPhoneFrame(
            screenBackground = Color(0xFF0A0A0C),
            animated = false,
            showsStatusBarTime = false,
            appliesDeniedChrome = denied,
            screen = { size, _ ->
                NotificationBannerScreen(size = size, isActive = !denied)
            },
            island = { _, _ -> },
        )
    }
}

/**
 * ≡ NotificationBannerScreen — lock clock + banner keyframes iOS
 * (y/scale/opacity cycle ~3.85s).
 */
@Composable
private fun NotificationBannerScreen(size: DpSize, isActive: Boolean) {
    val density = LocalDensity.current
    val w = with(density) { size.width.toPx() }
    val h = with(density) { size.height.toPx() }

    val yOffset = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }
    val opacity = remember { Animatable(if (isActive) 0f else 0.45f) }

    LaunchedEffect(isActive) {
        if (!isActive) {
            yOffset.snapTo(0f)
            scale.snapTo(1f)
            opacity.snapTo(0.45f)
            return@LaunchedEffect
        }
        val cubic = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
        // ≈ .snappy iOS pero sin bounce agresivo
        val settle = spring<Float>(dampingRatio = 0.92f, stiffness = 280f)
        while (true) {
            yOffset.snapTo(0f)
            scale.snapTo(1f)
            opacity.snapTo(0f)
            launch {
                yOffset.animateTo(-h * 0.05f, tween(350, easing = cubic))
            }
            launch {
                scale.animateTo(0.96f, tween(350, easing = cubic))
            }
            opacity.animateTo(1f, tween(350, easing = cubic))
            launch { yOffset.animateTo(0f, settle) }
            scale.animateTo(1f, settle)
            // hold ~2.2s
            kotlinx.coroutines.delay(2200)
            // exit suave
            launch {
                yOffset.animateTo(-h * 0.02f, tween(450, easing = cubic))
            }
            launch {
                scale.animateTo(0.98f, tween(450, easing = cubic))
            }
            opacity.animateTo(0f, tween(400, easing = cubic))
            kotlinx.coroutines.delay(80)
        }
    }

    val dateLabel = remember {
        SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date())
    }

    Box(Modifier.fillMaxSize().clipToBounds()) {
        PermissionPhoneWallpaper(Modifier.fillMaxSize())
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.28f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.35f),
                        ),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = with(density) { (w * 0.045f).toDp() })
                .padding(top = with(density) { (h * 0.09f).toDp() })
                .clipToBounds(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(with(density) { (h * 0.02f).toDp() }),
        ) {
            // Lock-screen clock
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(with(density) { (h * 0.002f).toDp() }),
            ) {
                Text(
                    dateLabel,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = with(density) { (w * 0.042f).toSp() },
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.permission_phone_time),
                    color = Color.White,
                    fontSize = with(density) { (w * 0.22f).toSp() },
                    fontWeight = FontWeight.Thin,
                    maxLines = 1,
                )
            }

            NotificationCard(
                size = size,
                modifier = Modifier.graphicsLayer {
                    translationY = yOffset.value
                    scaleX = scale.value
                    scaleY = scale.value
                    this.alpha = opacity.value
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    clip = true
                },
            )
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun NotificationCard(size: DpSize, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val w = with(density) { size.width.toPx() }
    val h = with(density) { size.height.toPx() }
    val iconSize = with(density) { (w * 0.09f).toDp() }
    val cardRadius = with(density) { (w * 0.055f).toDp() }
    val shape = RoundedCornerShape(cardRadius)

    // Fondo sólido tipo lock-screen Android (no chrome glass: en mock se veía sucio/desbordado)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xE61C1C1E))
            .padding(
                horizontal = with(density) { (w * 0.03f).toDp() },
                vertical = with(density) { (w * 0.026f).toDp() },
            ),
        horizontalArrangement = Arrangement.spacedBy(with(density) { (w * 0.025f).toDp() }),
        verticalAlignment = Alignment.Top,
    ) {
        AppIconImage(
            modifier = Modifier
                .size(iconSize)
                .clip(RoundedCornerShape(iconSize * 0.2237f)),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(with(density) { (h * 0.003f).toDp() }),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.app_name).uppercase(Locale.getDefault()),
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = with(density) { (w * 0.03f).toSp() },
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.3.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.permission_notifications_mock_now),
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = with(density) { (w * 0.028f).toSp() },
                    maxLines = 1,
                )
            }
            Text(
                stringResource(R.string.permission_notifications_mock_sender),
                color = Color.White,
                fontSize = with(density) { (w * 0.036f).toSp() },
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.permission_notifications_mock_body),
                color = Color.White.copy(alpha = 0.78f),
                fontSize = with(density) { (w * 0.034f).toSp() },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** ≡ `appIcon` — icono del paquete; fallback `moments_mark` (≡ Image(.logo)). */
@Composable
private fun AppIconImage(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val px = with(density) { 96.dp.roundToPx().coerceAtLeast(48) }
    val bitmap = remember(context.packageName, px) {
        runCatching {
            context.packageManager
                .getApplicationIcon(context.packageName)
                .toBitmap(px, px)
                .asImageBitmap()
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        Image(
            painter = painterResource(R.drawable.moments_mark),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    }
}
