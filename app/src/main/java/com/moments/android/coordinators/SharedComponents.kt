package com.moments.android.coordinators

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.moments.android.models.MediaItem
import com.moments.android.models.Moment
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.checkIfSaved
import com.moments.android.services.firestore.toggleSaveMoment
import com.moments.android.utilities.MomentsFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun CarouselView(
    mediaItems: List<MediaItem>,
    currentIndex: Int,
    onCurrentIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (mediaItems.isEmpty()) return

    val pagerState = rememberPagerState(
        initialPage = currentIndex.coerceIn(0, mediaItems.lastIndex),
        pageCount = { mediaItems.size },
    )

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != currentIndex) {
            onCurrentIndexChange(pagerState.currentPage)
        }
    }

    LaunchedEffect(currentIndex) {
        if (currentIndex != pagerState.currentPage && currentIndex in mediaItems.indices) {
            pagerState.scrollToPage(currentIndex)
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize(),
    ) { page ->
        val mediaItem = mediaItems[page]
        when {
            mediaItem.type == MediaItem.MediaType.IMAGE && mediaItem.url.isNotBlank() -> {
                SubcomposeAsyncImage(
                    model = mediaItem.url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color.Gray.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = Color.Gray)
                        }
                    },
                    success = { SubcomposeAsyncImageContent(modifier = Modifier.fillMaxSize()) },
                )
            }
            mediaItem.type == MediaItem.MediaType.VIDEO && mediaItem.url.isNotBlank() -> {
                CustomVideoPlayer(
                    url = mediaItem.url,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            else -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF3366FF).copy(alpha = 0.6f),
                                    Color(0xFF9B59B6).copy(alpha = 0.8f),
                                    Color(0xFFFF69B4).copy(alpha = 0.6f),
                                ),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Photo,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun CustomVideoPlayer(
    url: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val exoPlayer = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(ExoMediaItem.fromUri(Uri.parse(url)))
            repeatMode = Player.REPEAT_MODE_ONE
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                player = exoPlayer
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                setOnClickListener {
                    if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                }
            }
        },
        modifier = modifier,
        update = { view -> view.player = exoPlayer },
    )
}

@Composable
fun AsyncProfileImageView(
    userId: String,
    modifier: Modifier = Modifier,
) {
    var profileImagePath by remember(userId) { mutableStateOf<String?>(null) }
    var isLoading by remember(userId) { mutableStateOf(true) }

    DisposableEffect(userId) {
        if (userId.isEmpty()) {
            isLoading = false
            onDispose { }
        } else {
            val db = FirebaseFirestore.getInstance()
            var listener: ListenerRegistration? = null

            db.collection("users").document(userId).get()
                .addOnSuccessListener { document ->
                    isLoading = false
                    profileImagePath = document.data?.get("profileImagePath") as? String
                }
                .addOnFailureListener { isLoading = false }

            listener = db.collection("users").document(userId)
                .addSnapshotListener { snapshot, _ ->
                    val path = snapshot?.data?.get("profileImagePath") as? String
                    if (path != profileImagePath) profileImagePath = path
                    isLoading = false
                }

            onDispose { listener?.remove() }
        }
    }

    Box(
        modifier
            .clip(CircleShape)
            .background(Color.Gray.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center,
    ) {
        when {
            isLoading && profileImagePath.isNullOrEmpty() -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            }
            !profileImagePath.isNullOrBlank() -> {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(profileImagePath)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                    },
                    success = { SubcomposeAsyncImageContent(modifier = Modifier.fillMaxSize()) },
                )
            }
            else -> {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Box(
            Modifier
                .matchParentSize()
                .clip(CircleShape)
                .background(Color.Transparent)
        )
    }
}

@Composable
fun ProfileImageView(
    imagePath: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.Gray.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center,
    ) {
        if (!imagePath.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imagePath)
                    .size(coil.size.Size(40, 40))
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                },
                success = { SubcomposeAsyncImageContent(modifier = Modifier.fillMaxSize()) },
            )
        } else {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
fun ActionSubCardView(
    moment: Moment,
    onComment: () -> Unit,
    firestoreService: FirestoreService = remember { FirestoreService() },
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    var isSaved by remember(moment.id) { mutableStateOf(false) }
    val isDark = isSystemInDarkTheme()
    val heartUsers = moment.reactions["heart"].orEmpty()
    val isLiked = currentUserId != null && heartUsers.contains(currentUserId)

    LaunchedEffect(moment.id, currentUserId) {
        val momentId = moment.id ?: return@LaunchedEffect
        val userId = currentUserId ?: return@LaunchedEffect
        isSaved = runCatching {
            firestoreService.checkIfSaved(userId, momentId)
        }.getOrDefault(false)
    }

    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    if (isDark) {
                        listOf(Color.Black.copy(alpha = 0.8f), Color.Gray.copy(alpha = 0.7f))
                    } else {
                        listOf(Color.Gray.copy(alpha = 0.6f), Color.Gray.copy(alpha = 0.5f))
                    },
                ),
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = {
                val momentId = moment.id ?: return@IconButton
                val userId = currentUserId ?: return@IconButton
                scope.launch(Dispatchers.IO) {
                    firestoreService.addReaction(momentId, "heart", userId, moment.authorId)
                }
            },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
                if (moment.authorId == currentUserId || !moment.hideLikeCounts) {
                    Text(
                        MomentsFormat.count(heartUsers.size, MomentsFormat.CountStyle.SOCIAL_METRIC),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        IconButton(onClick = onComment) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Message, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Text(
                    MomentsFormat.count(moment.commentCount, MomentsFormat.CountStyle.SOCIAL_METRIC),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        IconButton(onClick = { /* share — pendiente de port */ }) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Text("0", color = Color.White, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(Modifier.weight(1f))

        IconButton(
            onClick = {
                val momentId = moment.id ?: return@IconButton
                val userId = currentUserId ?: return@IconButton
                scope.launch(Dispatchers.IO) {
                    runCatching { firestoreService.toggleSaveMoment(userId, momentId) }
                        .onSuccess { isSaved = !isSaved }
                }
            },
        ) {
            Icon(
                if (isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                contentDescription = null,
                tint = if (isSaved) Color(0xFFFFB800) else Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
