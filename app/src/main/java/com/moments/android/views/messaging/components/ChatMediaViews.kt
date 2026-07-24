package com.moments.android.views.messaging.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.R

/** Port de `Views/Messaging/Components/ChatMediaViews.swift`. */
@Composable fun ChatMediaDownloadOverlay(sizeLabel: String?, modifier: Modifier = Modifier) = Box(modifier.fillMaxSize().background(Color.Black.copy(.28f)), contentAlignment = Alignment.Center) { androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Download, null, tint = Color.White, modifier = Modifier.size(32.dp)); Text(sizeLabel ?: stringResource(R.string.chat_media_download), color = Color.White, fontSize = 13.sp) } }
@Composable fun ChatMediaManualDownloadPlaceholder(sizeLabel: String?, showsVideoBadge: Boolean = false, modifier: Modifier = Modifier) = Box(modifier.fillMaxSize().background(Color(0xFF2C2C2E))) { ChatMediaDownloadOverlay(sizeLabel); if (showsVideoBadge) ChatVideoPlayBadge(Modifier.align(Alignment.BottomStart)) }
@Composable fun ChatMediaDownloadProgressOverlay(progress: Double, modifier: Modifier = Modifier) = Box(modifier.fillMaxSize().background(Color.Black.copy(.4f)), contentAlignment = Alignment.Center) { CircularProgressIndicator(progress = progress.toFloat().coerceIn(.03f, 1f), color = Color.White, modifier = Modifier.size(60.dp)) }
@Composable fun GlassmorphicImageMessage(imageUrl: String?, previewThumbnailUrl: String? = null, isSending: Boolean, isResolvingMedia: Boolean = false, isAwaitingManualDownload: Boolean = false, isDownloadingMedia: Boolean = false, downloadProgress: Double? = null, downloadSizeLabel: String? = null, progress: Double?, onTap: () -> Unit, modifier: Modifier = Modifier) = MediaMessageCard(imageUrl, previewThumbnailUrl, false, isSending, isResolvingMedia, isAwaitingManualDownload, isDownloadingMedia, downloadProgress, downloadSizeLabel, progress, onTap, modifier)
@Composable fun GlassmorphicVideoMessage(videoUrl: String?, thumbnailUrl: String?, isSending: Boolean, isResolvingMedia: Boolean = false, isAwaitingManualDownload: Boolean = false, isDownloadingMedia: Boolean = false, downloadProgress: Double? = null, downloadSizeLabel: String? = null, progress: Double?, onTap: () -> Unit, modifier: Modifier = Modifier) = MediaMessageCard(thumbnailUrl ?: videoUrl, thumbnailUrl, true, isSending, isResolvingMedia, isAwaitingManualDownload, isDownloadingMedia, downloadProgress, downloadSizeLabel, progress, onTap, modifier)
@Composable private fun MediaMessageCard(url: String?, preview: String?, video: Boolean, sending: Boolean, resolving: Boolean, awaiting: Boolean, downloading: Boolean, downloadProgress: Double?, sizeLabel: String?, progress: Double?, onTap: () -> Unit, modifier: Modifier) { Box(modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onTap)) { when { resolving -> ChatMediaResolvingPlaceholder(Modifier.fillMaxSize()); awaiting && preview == null -> ChatMediaManualDownloadPlaceholder(sizeLabel, video, Modifier.fillMaxSize()); else -> if (url == null) Box(Modifier.fillMaxSize().background(Color.White.copy(.1f)), contentAlignment = Alignment.Center) { Icon(if (video) Icons.Default.PlayArrow else Icons.Default.Photo, null, tint = Color.White.copy(.5f)) } else AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }; if (awaiting && preview != null) ChatMediaDownloadOverlay(sizeLabel); if (downloading) ChatMediaDownloadProgressOverlay(downloadProgress ?: .03); if (sending) ChatMediaDownloadProgressOverlay(progress ?: .03); if (video && !downloading) ChatVideoPlayBadge(Modifier.align(Alignment.BottomStart)) } }
@Composable fun ChatVideoPlayBadge(modifier: Modifier = Modifier) { Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = modifier.padding(10.dp).size(18.dp)) }
@Composable fun FullScreenImageView(imageUrl: String, onClose: () -> Unit, modifier: Modifier = Modifier) { Box(modifier.fillMaxSize().background(Color.Black)) { AsyncImage(imageUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()); Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(24.dp).size(36.dp).clickable(onClick = onClose)) } }
