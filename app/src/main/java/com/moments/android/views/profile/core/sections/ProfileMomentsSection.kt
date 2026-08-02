package com.moments.android.views.profile.core.sections

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.PersonPin
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VideoLibrary
import com.moments.android.views.components.MomentsCircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.models.Moment
import com.moments.android.views.components.ActivityGridAudienceIcon
import com.moments.android.views.creator.audienceselector.ContentAudience
import com.moments.android.views.messaging.components.ChatVideoPlayBadge
import com.moments.android.views.profile.core.GridPreviewThumbnailFrame
import com.moments.android.views.profile.core.gridPreviewSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/** Métricas del grid, equivalente a `ProfileMomentsGridMetrics`. */
object ProfileMomentsGridMetrics {
    const val columns = 3
    val spacing = 1.dp
    /** ≡ iOS `defaultAvailableWidth` fallback (393pt). */
    val defaultAvailableWidth = 393.dp

    fun columnWidth(availableWidth: Dp): Dp = (availableWidth - spacing * (columns - 1)) / columns

    fun tileWidth(kind: BentoTileKind, unitWidth: Dp): Dp = unitWidth * kind.colSpan + spacing * (kind.colSpan - 1)

    fun tileHeight(kind: BentoTileKind, unitWidth: Dp): Dp = unitWidth * kind.rowSpan + spacing * (kind.rowSpan - 1)

    /**
     * ≡ iOS `bentoHeight`: shortest-column en Dp (no unidades enteras),
     * con spacing solo entre tiles apilados.
     */
    fun bentoHeight(tileKinds: List<BentoTileKind>, availableWidth: Dp = defaultAvailableWidth): Dp {
        if (tileKinds.isEmpty()) return 0.dp
        val unit = columnWidth(availableWidth)
        val spacingV = spacing.value
        val heights = FloatArray(columns) { 0f }
        for (kind in tileKinds) {
            val tileH = tileHeight(kind, unit).value
            var bestColumn = 0
            var bestY = Float.POSITIVE_INFINITY
            for (start in 0..(columns - kind.colSpan)) {
                val y = (start until start + kind.colSpan).maxOf { col ->
                    if (heights[col] > 0f) heights[col] + spacingV else 0f
                }
                if (y < bestY || (y == bestY && start < bestColumn)) {
                    bestY = y
                    bestColumn = start
                }
            }
            val newBottom = bestY + tileH
            for (col in bestColumn until bestColumn + kind.colSpan) {
                heights[col] = newBottom
            }
        }
        return heights.max().dp
    }

    /** Frames en Dp para colocar celdas (mismo algoritmo que [bentoHeight]). */
    fun planFrames(tileKinds: List<BentoTileKind>, availableWidth: Dp): List<BentoFrame> {
        if (tileKinds.isEmpty()) return emptyList()
        val unit = columnWidth(availableWidth)
        val spacingV = spacing.value
        val heights = FloatArray(columns) { 0f }
        return tileKinds.mapIndexed { index, kind ->
            val tileW = tileWidth(kind, unit)
            val tileH = tileHeight(kind, unit).value
            var bestColumn = 0
            var bestY = Float.POSITIVE_INFINITY
            for (start in 0..(columns - kind.colSpan)) {
                val y = (start until start + kind.colSpan).maxOf { col ->
                    if (heights[col] > 0f) heights[col] + spacingV else 0f
                }
                if (y < bestY || (y == bestY && start < bestColumn)) {
                    bestY = y
                    bestColumn = start
                }
            }
            val newBottom = bestY + tileH
            for (col in bestColumn until bestColumn + kind.colSpan) {
                heights[col] = newBottom
            }
            BentoFrame(
                index = index,
                kind = kind,
                x = (unit.value + spacingV).dp * bestColumn,
                y = bestY.dp,
                width = tileW,
                height = tileH.dp,
            )
        }
    }
}

data class BentoFrame(
    val index: Int,
    val kind: BentoTileKind,
    val x: Dp,
    val y: Dp,
    val width: Dp,
    val height: Dp,
)

/** Port de `ModernMomentThumbnail`: imagen/vídeo, crop, chrome y gestures del grid. */
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun ModernMomentThumbnail(
    moment: Moment,
    size: Dp,
    customListNamesById: Map<String, String> = emptyMap(),
    zoomSourceID: String? = null,
    onTap: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    isInteractionEnabled: Boolean = true,
    usesDiscreetAudienceIcon: Boolean = false,
    showsAudienceBadge: Boolean = true,
    gridIndex: Int = 0,
    descriptor: ProfileGridTileDescriptor = ProfileGridTileDescriptor.standard(moment),
    modifier: Modifier = Modifier,
) {
    @Suppress("UNUSED_PARAMETER")
    val unusedLists = customListNamesById
    val coordinator = LocalProfileGridHeroCoordinator.current
    val cellWidth = ProfileMomentsGridMetrics.tileWidth(descriptor.layoutKind, size)
    val cellHeight = ProfileMomentsGridMetrics.tileHeight(descriptor.layoutKind, size)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "profileThumbnailPress")
    val resolvedAudience = resolvedContentAudience(moment)
    val liftedOpacity = coordinator?.liftedGridSourceContentOpacity(moment, gridIndex) ?: 1f
    val dark = isSystemInDarkTheme()
    val hole = if (dark) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    Box(
        modifier = modifier
            .size(cellWidth, cellHeight)
            .clip(RoundedCornerShape(4.dp))
            .background(hole)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = liftedOpacity
            }
            .profileMomentZoomSource(zoomSourceID)
            .profileGridThumbnailFrameReporter(moment, gridIndex, coordinator)
            .then(
                if (isInteractionEnabled && (onTap != null || onLongPress != null)) {
                    Modifier.combinedClickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { onTap?.invoke() },
                        onLongClick = { onLongPress?.invoke() },
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        ProfileThumbnailMedia(moment, size, cellWidth, cellHeight, descriptor)
        if (descriptor.showsPlayCue || descriptor.showsPin || descriptor.showsScheduledCue) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(0.12f),
                                Color.Transparent,
                                Color.Black.copy(if (descriptor.usesPortraitCrop) 0.34f else 0.18f),
                            ),
                        ),
                    ),
            )
        }
        ProfileThumbnailTopChrome(
            moment = moment,
            descriptor = descriptor,
            usesDiscreetAudienceIcon = usesDiscreetAudienceIcon,
            showsAudienceBadge = showsAudienceBadge,
            audience = resolvedAudience,
        )
        if (descriptor.showsPlayCue) {
            ProfileThumbnailVideoChrome(
                moment = moment,
                descriptor = descriptor,
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }
    }
}

private fun resolvedContentAudience(moment: Moment): ContentAudience {
    val audience = ContentAudience.fromAudienceValue(moment.audience)
    return if (moment.customListId != null && audience == ContentAudience.CUSTOM) {
        ContentAudience.CUSTOM_LIST
    } else {
        audience
    }
}

@Composable
private fun ProfileThumbnailMedia(moment: Moment, size: Dp, cellWidth: Dp, cellHeight: Dp, descriptor: ProfileGridTileDescriptor) {
    val primary = moment.primaryVisibleMediaItem
    val image = when {
        primary?.type?.raw == "image" -> primary.url
        primary?.thumbnailUrl?.isNotBlank() == true -> primary.thumbnailUrl
        !moment.previewImageURLString.isNullOrBlank() -> moment.previewImageURLString
        else -> null
    }
    val video = when {
        primary?.type?.raw == "video" -> primary.url
        !moment.previewVideoURLString.isNullOrBlank() -> moment.previewVideoURLString
        else -> null
    }
    when {
        image != null -> ProfileThumbnailImage(image, moment, size, cellWidth, cellHeight, descriptor.usesPortraitCrop)
        video != null -> ProfileThumbnailVideo(video, moment, size, cellWidth, cellHeight, descriptor.usesPortraitCrop)
        else -> ProfileThumbnailEmpty(moment, cellWidth, cellHeight)
    }
}

@Composable
private fun ProfileThumbnailImage(url: String, moment: Moment, size: Dp, cellWidth: Dp, cellHeight: Dp, portrait: Boolean) {
    val image: @Composable (ContentScale) -> Unit = { contentScale ->
        AsyncImage(model = profileThumbnailUrl(url), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = contentScale)
    }
    if (portrait) Box(Modifier.size(cellWidth, cellHeight)) { image(ContentScale.Crop) } else GridPreviewThumbnailFrame(size, moment.gridPreviewSettings, image)
}

@Composable
private fun ProfileThumbnailVideo(url: String, moment: Moment, size: Dp, cellWidth: Dp, cellHeight: Dp, portrait: Boolean) {
    var thumbnail by remember(url) { mutableStateOf<Bitmap?>(null) }
    var loading by remember(url) { mutableStateOf(true) }
    LaunchedEffect(url) {
        thumbnail = withContext(Dispatchers.IO) {
            runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(profileThumbnailUrl(url))
                    retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } finally {
                    retriever.release()
                }
            }.getOrNull()
        }
        loading = false
    }
    val content: @Composable (ContentScale) -> Unit = { contentScale ->
        thumbnail?.let { androidx.compose.foundation.Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = contentScale) }
            ?: ProfileMediaPlaceholder(loading, R.string.profile_thumbnail_video_uploading, R.string.profile_thumbnail_video)
    }
    if (portrait) Box(Modifier.size(cellWidth, cellHeight)) { content(ContentScale.Crop) } else GridPreviewThumbnailFrame(size, moment.gridPreviewSettings, content)
}

@Composable
private fun ProfileMediaPlaceholder(loading: Boolean, loadingLabel: Int, idleLabel: Int) {
    Column(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .16f)), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        MomentsCircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
        )
        Text(stringResource(if (loading) loadingLabel else idleLabel), color = Color.White.copy(.65f), fontSize = 8.sp)
    }
}

@Composable
private fun ProfileThumbnailEmpty(moment: Moment, cellWidth: Dp, cellHeight: Dp) {
    Column(Modifier.size(cellWidth, cellHeight).background(Color.Black.copy(.14f)).padding(3.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Filled.ChatBubbleOutline, null, tint = Color.White.copy(.6f), modifier = Modifier.size(16.dp))
        Text(moment.content.ifBlank { stringResource(R.string.profile_thumbnail_empty_content) }.take(12), color = Color.White.copy(.8f), fontSize = 8.sp, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ProfileThumbnailTopChrome(
    moment: Moment,
    descriptor: ProfileGridTileDescriptor,
    usesDiscreetAudienceIcon: Boolean,
    showsAudienceBadge: Boolean,
    audience: ContentAudience,
) {
    Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.Top) {
        if (moment.isCarouselMoment) {
            MomentCarouselIndicatorIcon(size = 17.dp)
        }
        if (showsAudienceBadge && usesDiscreetAudienceIcon) {
            ActivityGridAudienceIcon(
                audience = audience,
                modifier = Modifier.padding(start = 5.dp),
            )
        }
        if (descriptor.showsScheduledCue && moment.authorId == FirebaseAuth.getInstance().currentUser?.uid) {
            Row(
                Modifier
                    .padding(start = 5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(0.52f))
                    .padding(horizontal = 7.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Schedule, null, tint = Color.White, modifier = Modifier.size(8.dp))
                Text(
                    moment.scheduledRemainingText(LocalContext.current),
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        if (descriptor.showsPin) {
            Icon(
                Icons.Filled.PushPin,
                null,
                tint = Color.White,
                modifier = Modifier
                    .size(19.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(0.56f))
                    .padding(5.dp),
            )
        }
    }
}

@Composable
private fun ProfileThumbnailVideoChrome(
    moment: Moment,
    descriptor: ProfileGridTileDescriptor,
    modifier: Modifier,
) {
    val playSize = when (descriptor.layoutKind) {
        BentoTileKind.HERO, BentoTileKind.TALL -> 18.dp
        BentoTileKind.UNIT -> 14.dp
    }
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChatVideoPlayBadge(size = playSize, padding = 8.dp)
        if (descriptor.showsDuration && moment.videoDuration != null) {
            Text(
                formatVideoDuration(moment.videoDuration),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(end = 7.dp),
            )
        }
    }
}

/** Port de `MomentCarouselIndicatorIcon` — asset `CarouselPostIcon`. */
@Composable
fun MomentCarouselIndicatorIcon(size: Dp = 18.dp, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Image(
        painter = painterResource(R.drawable.carousel_post_icon),
        contentDescription = null,
        modifier = modifier.size(size),
    )
}

internal fun profileThumbnailUrl(path: String): String =
    if (path.startsWith("https://")) path else "https://firebasestorage.googleapis.com/v0/b/glowsy-6a40e/o/${Uri.encode(path)}?alt=media"

@Composable
private fun formatVideoDuration(duration: Double): String {
    val seconds = max(kotlin.math.round(duration).toInt(), 0)
    return stringResource(R.string.profile_thumbnail_video_duration, seconds / 60, seconds % 60)
}

@Composable
fun ProfileSectionEmptyState(icon: ProfileSectionEmptyIcon, title: Int, subtitle: Int, modifier: Modifier = Modifier) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val content = if (dark) Color.White else Color(0xFF0B1215)
    val secondary = if (dark) Color.White.copy(.62f) else Color(0xFF52626A)
    Column(modifier.fillMaxWidth().padding(vertical = 56.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(54.dp).clip(CircleShape).background(content.copy(.05f)), contentAlignment = Alignment.Center) {
            Icon(icon.vector, null, tint = secondary.copy(.7f), modifier = Modifier.size(22.dp))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(title), color = content, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(subtitle), color = secondary, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
        }
    }
}

enum class ProfileSectionEmptyIcon(val vector: androidx.compose.ui.graphics.vector.ImageVector) { CAMERA(Icons.Filled.CameraAlt), BOOKMARK(Icons.Filled.Bookmark), TAGGED(Icons.Filled.PersonPin) }

@Composable fun ModernEmptyMomentsView(modifier: Modifier = Modifier) = ProfileSectionEmptyState(ProfileSectionEmptyIcon.CAMERA, R.string.profile_thumbnail_moments_empty_title, R.string.profile_thumbnail_moments_empty_subtitle, modifier)
@Composable fun ProfileSavedPlaceholder(modifier: Modifier = Modifier) = ProfileSectionEmptyState(ProfileSectionEmptyIcon.BOOKMARK, R.string.profile_thumbnail_saved_empty_title, R.string.profile_thumbnail_saved_empty_subtitle, modifier)
