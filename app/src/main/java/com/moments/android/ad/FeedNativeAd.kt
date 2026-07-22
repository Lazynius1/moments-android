package com.moments.android.ad

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.google.android.gms.ads.nativead.AdChoicesView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.moments.android.R
import com.moments.android.extensions.fromHex
import com.moments.android.services.auth.AuthService
import com.moments.android.services.performance.MotionPolicy
import kotlinx.coroutines.delay

fun Context.findActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
fun SwiftUiNativeAdView(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val adManager = remember { NativeAdManager() }

    DisposableEffect(Unit) {
        onDispose { adManager.destroy() }
    }

    val isLoading by adManager.isLoading.collectAsState()
    val nativeAd by adManager.nativeAd.collectAsState()
    val hasError by adManager.hasError.collectAsState()

    LaunchedEffect(Unit) {
        adManager.loadAd(context.findActivity())
    }

    Column(modifier = modifier) {
        when {
            isLoading -> ModernAdLoadingView()
            nativeAd != null -> ModernNativeAdCardView(nativeAd = nativeAd!!)
            hasError -> Unit
        }
    }
}

@Composable
fun SmartNativeAdView(
    authService: AuthService = AuthService,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val currentUser by authService.currentUser.collectAsState()
    val adManager = remember { NativeAdManager() }
    var showingPrivacyConsent by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { adManager.destroy() }
    }

    val isLoading by adManager.isLoading.collectAsState()
    val nativeAd by adManager.nativeAd.collectAsState()
    val hasError by adManager.hasError.collectAsState()

    val shouldShowAds = PlusStatusHelper.shouldShowAds(currentUser)

    if (!shouldShowAds) return

    LaunchedEffect(Unit) {
        adManager.loadAd(context.findActivity())
        if (AdMobConfiguration.shouldShowConsentFlow) {
            showingPrivacyConsent = true
        }
    }

    Column(modifier = modifier) {
        when {
            isLoading -> IntegratedAdLoadingView()
            nativeAd != null -> IntegratedNativeAdView(nativeAd = nativeAd!!)
            hasError -> Unit
        }
    }

    if (showingPrivacyConsent) {
        TrackingPermissionDialog(
            onDismiss = { showingPrivacyConsent = false },
            onContinue = {
                showingPrivacyConsent = false
                val activity = context.findActivity()
                if (activity != null) {
                    AdMobConfiguration.startConsentFlow(activity) {
                        adManager.loadAd(activity)
                    }
                }
            },
        )
    }
}

@Composable
fun CleanNativeAdView(
    authService: AuthService = AuthService,
    modifier: Modifier = Modifier,
) {
    val currentUser by authService.currentUser.collectAsState()
    if (PlusStatusHelper.shouldShowAds(currentUser)) {
        SwiftUiNativeAdView(modifier = modifier)
    }
}

@Composable
fun TrackingPermissionDialog(
    onDismiss: () -> Unit,
    onContinue: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.att_pre_alert_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.att_pre_alert_description))
                Text(
                    text = stringResource(R.string.ad_common_ad),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onContinue) {
                Text(stringResource(R.string.att_pre_alert_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.permission_tracking_primer_not_now))
            }
        },
    )
}

@Composable
private fun ShimmerBox(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shimmerAlpha",
    )
    Box(
        modifier = modifier
            .alpha(if (enabled && !MotionPolicy.reduceMotion) alpha else 0.5f)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

@Composable
fun ModernAdLoadingView(modifier: Modifier = Modifier) {
    val animate = !MotionPolicy.reduceMotion
    Column(
        modifier = modifier
            .padding(horizontal = 15.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .padding(bottom = 20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            AdBadge()
            Spacer(Modifier.weight(1f))
        }
        Column(modifier = Modifier.padding(horizontal = 15.dp)) {
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(16.dp)),
                enabled = animate,
            )
            Spacer(Modifier.height(16.dp))
            ShimmerBox(
                modifier = Modifier
                    .width(150.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(4.dp)),
                enabled = animate,
            )
            Spacer(Modifier.height(10.dp))
            ShimmerBox(
                modifier = Modifier
                    .width(220.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp)),
                enabled = animate,
            )
        }
    }
}

@Composable
fun ModernNativeAdCardView(
    nativeAd: NativeAd,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(horizontal = 15.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .padding(bottom = 20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            AdBadge()
            Spacer(Modifier.weight(1f))
        }
        FeedNativeAdMediaView(
            nativeAd = nativeAd,
            modifier = Modifier
                .fillMaxWidth()
                .height(500.dp)
                .padding(horizontal = 15.dp),
        )
    }
}

@Composable
fun IntegratedAdLoadingView(modifier: Modifier = Modifier) {
    val animate = !MotionPolicy.reduceMotion
    val (isPersonalized, _) = remember { AdMobConfiguration.getAdPersonalizationStatus() }
    Column(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShimmerBox(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                enabled = animate,
            )
            Spacer(Modifier.width(12.dp))
            Column {
                ShimmerBox(
                    modifier = Modifier
                        .width(120.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    enabled = animate,
                )
                Spacer(Modifier.height(4.dp))
                ShimmerBox(
                    modifier = Modifier
                        .width(80.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    enabled = animate,
                )
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(R.string.ad_common_ad),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Text(
                    text = if (isPersonalized) "Personalizado" else "No personalizado",
                    fontSize = 8.sp,
                    color = if (isPersonalized) Color(0xFF4CAF50) else Color(0xFFFF9800),
                )
            }
        }
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp),
            enabled = animate,
        )
    }
}

@Composable
fun IntegratedNativeAdView(
    nativeAd: NativeAd,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Column(
        modifier = modifier
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (isDark) Color.fromHex("#121212") else Color.White),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            nativeAd.icon?.drawable?.let {
                AsyncImage(
                    model = nativeAd.icon?.uri,
                    contentDescription = null,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape),
                )
            } ?: Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = nativeAd.advertiser ?: stringResource(R.string.ad_common_sponsored),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
                Text(
                    text = stringResource(R.string.ad_common_sponsored),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
        IntegratedAdMediaView(
            nativeAd = nativeAd,
            modifier = Modifier
                .fillMaxWidth()
                .height(380.dp)
                .clip(RoundedCornerShape(16.dp)),
        )
    }
}

@Composable
private fun AdBadge() {
    Text(
        text = stringResource(R.string.ad_common_ad),
        fontSize = 12.sp,
        color = Color.Gray.copy(alpha = 0.8f),
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

@Composable
fun FeedNativeAdMediaView(
    nativeAd: NativeAd,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            buildFeedNativeAdView(ctx, nativeAd, headlineSizeSp = 18f, bodySizeSp = 15f, mediaHeightDp = 300)
        },
        update = { view ->
            view.setNativeAd(nativeAd)
        },
    )
}

@Composable
fun IntegratedAdMediaView(
    nativeAd: NativeAd,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            buildFeedNativeAdView(ctx, nativeAd, headlineSizeSp = 16f, bodySizeSp = 14f, mediaHeightDp = 300)
        },
        update = { view ->
            view.setNativeAd(nativeAd)
        },
    )
}

private fun buildFeedNativeAdView(
    context: Context,
    nativeAd: NativeAd,
    headlineSizeSp: Float,
    bodySizeSp: Float,
    mediaHeightDp: Int,
): NativeAdView {
    val density = context.resources.displayMetrics.density
    val mediaHeightPx = (mediaHeightDp * density).toInt()

    return NativeAdView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )

        val mediaView = MediaView(context).apply {
            setMediaContent(nativeAd.mediaContent)
            nativeAd.mediaContent?.videoController?.let { controller ->
                controller.mute(true)
            }
        }
        this.mediaView = mediaView

        val headlineLabel = TextView(context).apply {
            text = nativeAd.headline ?: context.getString(R.string.ad_common_ad)
            textSize = headlineSizeSp
            setTextColor(android.graphics.Color.WHITE)
        }
        headlineView = headlineLabel

        val bodyLabel = TextView(context).apply {
            text = nativeAd.body ?: ""
            textSize = bodySizeSp
            setTextColor(android.graphics.Color.argb(230, 255, 255, 255))
        }
        bodyView = bodyLabel

        val adChoicesView = AdChoicesView(context)
        this.adChoicesView = adChoicesView

        addView(mediaView)
        addView(headlineLabel)
        addView(bodyLabel)
        addView(adChoicesView)

        mediaView.layoutParams = android.widget.FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            mediaHeightPx,
        ).apply {
            topMargin = (8 * density).toInt()
            marginStart = (8 * density).toInt()
            marginEnd = (8 * density).toInt()
        }

        adChoicesView.layoutParams = android.widget.FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = mediaHeightPx + (20 * density).toInt()
            marginEnd = (8 * density).toInt()
        }

        headlineLabel.layoutParams = android.widget.FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = mediaHeightPx + (12 * density).toInt()
            marginStart = (8 * density).toInt()
            marginEnd = (120 * density).toInt()
        }

        bodyLabel.layoutParams = android.widget.FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = mediaHeightPx + (48 * density).toInt()
            marginStart = (8 * density).toInt()
            marginEnd = (8 * density).toInt()
        }

        setNativeAd(nativeAd)
    }
}

private fun Color.luminance(): Float =
    0.299f * red + 0.587f * green + 0.114f * blue
