package com.moments.android.views.profile.userprofile.sections

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.models.Moment
import com.moments.android.utilities.legacyPoppinsSize
import com.moments.android.views.components.MomentHashtagText
import com.moments.android.views.feed.rememberAdaptiveColors

/** Port de `StatItem`: métrica no tapeable del perfil. */
@Composable
fun StatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val colors = rememberAdaptiveColors()

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Text(
            text = value,
            color = colors.primary,
            fontSize = with(density) { legacyPoppinsSize(context, 20).toSp() },
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            color = colors.secondary,
            fontSize = with(density) { legacyPoppinsSize(context, 12).toSp() },
            textAlign = TextAlign.Center,
        )
    }
}

/** Port de `UserModernBackgroundView`: foto de perfil desenfocada + degradados de legibilidad. */
@Composable
fun UserModernBackgroundView(
    profileImagePath: String?,
    scrollOffset: Float,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    val isDark = colors.isDark
    val density = LocalDensity.current

    Box(modifier.fillMaxSize().background(colors.surfaceBackground)) {
        if (!profileImagePath.isNullOrBlank()) {
            AsyncImage(
                model = profileImagePath,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(30.dp)
                    .scale(1.2f)
                    .offset(y = with(density) { (scrollOffset * 0.2f).toDp() }),
                alpha = if (isDark) 0.15f else 0.08f,
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        if (isDark) {
                            listOf(
                                Color.Black.copy(alpha = 0.3f),
                                Color.Black.copy(alpha = 0.5f),
                                Color.Black.copy(alpha = 0.7f),
                            )
                        } else {
                            listOf(
                                Color.White.copy(alpha = 0.2f),
                                Color.White.copy(alpha = 0.4f),
                                Color.White.copy(alpha = 0.6f),
                            )
                        },
                    ),
                ),
        )
    }
}

/** Port de `UserMomentPreviewView`. */
@Composable
fun UserMomentPreviewView(
    moment: Moment,
    onHashtagTap: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceBackground.copy(alpha = 0.92f))
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Gray.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            if (!moment.imagePath.isNullOrBlank()) {
                AsyncImage(
                    model = moment.imagePath,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = Color.Gray)
            }
        }

        UserExpandableContentView(
            content = moment.content,
            onHashtagTap = onHashtagTap,
        )
    }
}

/** Port de `UserExpandableContentView`: recorta a 15 caracteres y despliega. */
@Composable
fun UserExpandableContentView(
    content: String,
    onHashtagTap: (String) -> Unit,
    modifier: Modifier = Modifier,
    onMentionTap: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var isExpanded by remember(content) { mutableStateOf(false) }
    val needsExpansion = content.length > MAX_PREVIEW_CHARACTERS

    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MomentHashtagText(
            content = if (isExpanded) {
                content
            } else {
                content.take(MAX_PREVIEW_CHARACTERS) + if (needsExpansion) "..." else ""
            },
            onHashtagTap = onHashtagTap,
            onMentionTap = onMentionTap,
            baseColor = Color.White.copy(alpha = 0.95f),
            mentionColor = Color(0xFF007AFF),
            fontSize = 14.sp,
            textAlignment = TextAlign.Center,
            shadow = Shadow(
                color = Color.Black.copy(alpha = 0.8f),
                offset = Offset(0f, 1f),
                blurRadius = 3f,
            ),
        )

        if (needsExpansion) {
            Text(
                text = stringResource(
                    if (isExpanded) R.string.user_profile_see_less else R.string.user_profile_see_more,
                ),
                color = UserProfileAccent,
                fontSize = with(density) { legacyPoppinsSize(context, 13).toSp() },
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(UserProfileAccent.copy(alpha = 0.1f))
                    .clickable { isExpanded = !isExpanded }
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

/** Port de `ProfileImageViewer`: foto de perfil a pantalla completa. */
@Composable
fun ProfileImageViewer(
    profileImagePath: String?,
    username: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val colors = rememberAdaptiveColors()

    Box(
        modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(30.dp))
            .background(colors.surfaceBackground.copy(alpha = 0.96f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        if (!profileImagePath.isNullOrBlank()) {
            AsyncImage(
                model = profileImagePath,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .aspectRatio(1f)
                    .clip(CircleShape),
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    Modifier
                        .size(300.dp)
                        .clip(CircleShape)
                        .background(colors.secondary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        tint = colors.secondary,
                        modifier = Modifier.size(120.dp),
                    )
                }
                Text(
                    text = username,
                    color = colors.primary,
                    fontSize = with(density) { legacyPoppinsSize(context, 18).toSp() },
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** Port de `UserModernRefreshIndicator`. */
@Composable
fun UserModernRefreshIndicator(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val colors = rememberAdaptiveColors()
    val transition = rememberInfiniteTransition(label = "userProfileRefresh")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1_500, easing = LinearEasing)),
        label = "rotation",
    )
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(1_000), repeatMode = RepeatMode.Reverse),
        label = "pulse",
    )

    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(colors.surfaceBackground.copy(alpha = 0.9f))
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(colors.secondary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = null,
                tint = UserProfileAccent,
                modifier = Modifier
                    .size(16.dp)
                    .rotate(rotation)
                    .scale(pulse),
            )
        }
        Text(
            text = stringResource(R.string.user_profile_updating),
            color = colors.secondary,
            fontSize = with(density) { legacyPoppinsSize(context, 14).toSp() },
            fontWeight = FontWeight.Medium,
        )
    }
}

internal val UserProfileAccent = Color(0xFF00A896)

private const val MAX_PREVIEW_CHARACTERS = 15
