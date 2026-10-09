package com.moments.android.views.messaging.components

import android.content.Context
import android.media.AudioManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.moments.android.R
import com.moments.android.utilities.withMomentsAudioFocus
import com.moments.android.views.feed.AdaptiveColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.io.File

/** Nota de voz que va justo después de otra (reproducción seguida). */
data class ChatVoiceQueueItem(
    val messageId: String,
    val audioUrl: String,
    val duration: Double,
    val senderId: String,
)

/**
 * ≡ iOS `ChatVoicePlaybackController`: un único reproductor para todo el chat. Vive fuera
 * de las burbujas para que la nota siga sonando aunque su fila salga de pantalla al hacer
 * scroll (la LazyColumn la desecha). Las burbujas y la mini barra solo leen su estado.
 */
object ChatVoicePlaybackController {
    var activeMessageId by mutableStateOf<String?>(null)
        private set
    var activeSenderId by mutableStateOf<String?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var currentTime by mutableFloatStateOf(0f)
        private set
    var duration by mutableStateOf(0.0)
        private set
    var playbackRate by mutableFloatStateOf(1f)
        private set
    /** Si la burbuja de la nota activa está en pantalla. La mini barra solo aparece cuando no. */
    var isActiveBubbleVisible by mutableStateOf(true)
        private set

    /** Lo fija el chat: devuelve la nota de voz que va justo después de un mensaje, si la hay. */
    var nextVoiceNoteProvider: ((String) -> ChatVoiceQueueItem?)? = null
    /** Lo fija el chat: se llama al empezar una nota nueva (para preparar la siguiente). */
    var onActiveMessageChanged: ((String) -> Unit)? = null

    /** Posición guardada de las notas que se pausaron o se cambiaron por otra. */
    private val savedPositions = mutableStateMapOf<String, Float>()
    private val visibleMessageIds = mutableSetOf<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var appContext: Context? = null
    private var player: ExoPlayer? = null
    private var proximityManager: SimpleProximityManager? = null
    private var proximityJob: Job? = null
    private var loadJob: Job? = null
    private var progressJob: Job? = null
    private var activePath: String? = null
    private var activeAudioUrl: String? = null

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED && isPlaying) finishPlayback()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            // Pausa externa (foco de audio, auriculares desconectados…): reflejarla en la UI.
            if (!playWhenReady && isPlaying && player?.playbackState != Player.STATE_ENDED) pause()
        }
    }

    // ── Estado por mensaje ──

    fun isPlayingMessage(messageId: String): Boolean = isPlaying && activeMessageId == messageId

    fun position(messageId: String): Float =
        if (activeMessageId == messageId) currentTime else savedPositions[messageId] ?: 0f

    // ── Controles ──

    fun play(
        context: Context,
        messageId: String,
        audioUrl: String,
        duration: Double,
        senderId: String,
        onFailure: () -> Unit = {},
    ) {
        val player = ensurePlayer(context)
        if (activeMessageId != messageId) switchActiveMessage(messageId, senderId, duration)

        activeAudioUrl = audioUrl
        isPlaying = true
        loadJob?.cancel()
        loadJob = scope.launch {
            val path = activePath ?: resolveVoicePlaybackPath(audioUrl)
            if (activeMessageId != messageId || !isPlaying) return@launch
            if (path == null) {
                stop()
                onFailure()
                return@launch
            }
            if (activePath != path) {
                player.setMediaItem(MediaItem.fromUri(voicePlaybackUri(path)))
                player.prepare()
                activePath = path
            }
            // ≡ iOS configurePlaybackSession(speaker: true) al arrancar
            applyVoicePlaybackRoute(context, player, toEarpiece = false)
            player.setPlaybackSpeed(playbackRate)
            // Reanuda donde quedó (incluido un seek en pausa); al final, desde el inicio.
            val total = this@ChatVoicePlaybackController.duration
            val resumeAt = if (currentTime > 0.01f && (total <= 0 || currentTime < total - 0.05)) currentTime else 0f
            currentTime = resumeAt
            player.seekTo((resumeAt * 1000).toLong())
            player.play()
            proximityManager?.startMonitoring()
            startProgressUpdates()
        }
    }

    /** Reanuda la nota activa (botón de la mini barra, sin burbuja en pantalla). */
    fun resume(context: Context) {
        val messageId = activeMessageId ?: return
        val audioUrl = activeAudioUrl ?: return
        play(context, messageId, audioUrl, duration, activeSenderId.orEmpty())
    }

    fun pause() {
        loadJob?.cancel()
        loadJob = null
        isPlaying = false
        player?.let {
            it.pause()
            currentTime = (it.currentPosition / 1000.0).toFloat()
        }
        progressJob?.cancel()
        proximityManager?.stopMonitoring()
        restoreAudioRoute()
    }

    /** Para y olvida la nota activa (cerrar la mini barra). */
    fun stop() {
        val active = activeMessageId
        val position = player?.currentPosition?.let { (it / 1000.0).toFloat() } ?: currentTime
        if (active != null && position > 0.01f) savedPositions[active] = position
        releaseMedia()
        activeMessageId = null
        activeSenderId = null
        currentTime = 0f
        duration = 0.0
        updateActiveBubbleVisibility()
    }

    /** Al salir del chat: para, olvida las posiciones y suelta el reproductor. */
    fun reset() {
        stop()
        savedPositions.clear()
        proximityJob?.cancel()
        proximityJob = null
        player?.removeListener(playerListener)
        player?.release()
        player = null
    }

    fun seek(messageId: String, seconds: Float) {
        val clamped = seconds.coerceAtLeast(0f)
        if (activeMessageId == messageId) {
            currentTime = if (duration > 0) clamped.coerceAtMost(duration.toFloat()) else clamped
            player?.seekTo((currentTime * 1000).toLong())
        } else {
            savedPositions[messageId] = clamped
        }
    }

    fun cyclePlaybackRate() {
        playbackRate = when (playbackRate) {
            1f -> 1.5f
            1.5f -> 2f
            else -> 1f
        }
        player?.setPlaybackSpeed(playbackRate)
    }

    // ── Visibilidad de burbujas ──

    fun bubbleAppeared(messageId: String) {
        visibleMessageIds.add(messageId)
        updateActiveBubbleVisibility()
    }

    fun bubbleDisappeared(messageId: String) {
        visibleMessageIds.remove(messageId)
        updateActiveBubbleVisibility()
    }

    // ── Privado ──

    private fun ensurePlayer(context: Context): ExoPlayer {
        val app = context.applicationContext
        appContext = app
        if (proximityManager == null) {
            val manager = SimpleProximityManager(app)
            proximityManager = manager
            proximityJob = scope.launch {
                // ≡ iOS onChange(of: proximityManager.isNearEar)
                snapshotFlow { manager.isNearEar }.distinctUntilChanged().collect { near ->
                    val current = player ?: return@collect
                    if (!isPlaying) return@collect
                    val position = current.currentPosition
                    applyVoicePlaybackRoute(app, current, toEarpiece = near)
                    if (position > 0) current.seekTo(position)
                    if (!current.isPlaying) current.play()
                }
            }
        }
        return player ?: ExoPlayer.Builder(app).build().withMomentsAudioFocus(app).also {
            it.addListener(playerListener)
            player = it
        }
    }

    private fun switchActiveMessage(messageId: String, senderId: String, knownDuration: Double) {
        val previous = activeMessageId
        player?.let { if (previous != null) savedPositions[previous] = (it.currentPosition / 1000.0).toFloat() }
        releaseMedia()
        activeMessageId = messageId
        activeSenderId = senderId
        duration = knownDuration
        currentTime = savedPositions.remove(messageId) ?: 0f
        if (duration > 0 && currentTime >= duration - 0.05) currentTime = 0f
        updateActiveBubbleVisibility()
        onActiveMessageChanged?.invoke(messageId)
    }

    private fun releaseMedia() {
        loadJob?.cancel()
        loadJob = null
        progressJob?.cancel()
        progressJob = null
        isPlaying = false
        player?.stop()
        player?.clearMediaItems()
        activePath = null
        activeAudioUrl = null
        proximityManager?.stopMonitoring()
        restoreAudioRoute()
    }

    private fun finishPlayback() {
        val finished = activeMessageId
        if (finished != null) savedPositions.remove(finished)

        // Como WhatsApp: si el siguiente mensaje también es una nota de voz, sigue con ella.
        val context = appContext
        val next = finished?.let { nextVoiceNoteProvider?.invoke(it) }
        if (context != null && next != null && next.messageId != finished) {
            savedPositions.remove(next.messageId)
            releaseMedia()
            play(context, next.messageId, next.audioUrl, next.duration, next.senderId)
            return
        }

        releaseMedia()
        activeMessageId = null
        activeSenderId = null
        currentTime = 0f
        duration = 0.0
        updateActiveBubbleVisibility()
    }

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isPlaying) {
                val current = player ?: break
                currentTime = (current.currentPosition / 1000.0).toFloat()
                if (duration <= 0 && current.duration != C.TIME_UNSET && current.duration > 0) {
                    duration = current.duration / 1000.0
                }
                delay(50)
            }
        }
    }

    private fun updateActiveBubbleVisibility() {
        val visible = activeMessageId?.let { it in visibleMessageIds } ?: true
        if (visible != isActiveBubbleVisible) isActiveBubbleVisible = visible
    }

    private fun restoreAudioRoute() {
        val am = appContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        am.mode = AudioManager.MODE_NORMAL
        @Suppress("DEPRECATION")
        am.isSpeakerphoneOn = false
    }

    private fun voicePlaybackUri(path: String): android.net.Uri = when {
        path.startsWith("content:") || path.startsWith("http") || path.startsWith("file:") ->
            android.net.Uri.parse(path)
        else -> android.net.Uri.fromFile(File(path))
    }
}

/**
 * ≡ iOS `ChatVoiceMiniPlayerBar`: barra compacta arriba del chat mientras suena (o está en
 * pausa) una nota de voz cuya burbuja no está en pantalla. Tocarla lleva al mensaje.
 */
@Composable
fun ChatVoiceMiniPlayerHost(
    adaptiveColors: AdaptiveColors,
    senderName: (String?) -> String,
    onJump: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val playback = ChatVoicePlaybackController
    val activeId = playback.activeMessageId
    AnimatedVisibility(
        visible = activeId != null && !playback.isActiveBubbleVisible,
        modifier = modifier,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
    ) {
        ChatVoiceMiniPlayerBar(
            adaptiveColors = adaptiveColors,
            senderName = senderName(playback.activeSenderId),
            onTap = { playback.activeMessageId?.let(onJump) },
        )
    }
}

@Composable
private fun ChatVoiceMiniPlayerBar(
    adaptiveColors: AdaptiveColors,
    senderName: String,
    onTap: () -> Unit,
) {
    val context = LocalContext.current
    val playback = ChatVoicePlaybackController
    val shape = RoundedCornerShape(18.dp)
    val progress = if (playback.duration > 0) {
        (playback.currentTime / playback.duration.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val speedLabel = when (playback.playbackRate) {
        1.5f -> "1.5×"
        2f -> "2×"
        else -> "1×"
    }
    val playLabel = stringResource(if (playback.isPlaying) R.string.chat_voice_pause else R.string.chat_voice_play)
    val jumpLabel = stringResource(R.string.chat_voice_mini_player_jump)
    val closeLabel = stringResource(R.string.chat_voice_mini_player_close)

    Box(
        Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .shadow(10.dp, shape)
            .clip(shape)
            .background(adaptiveColors.messageBubbleBackground)
            .border(0.5.dp, adaptiveColors.messageBubbleStroke, shape),
    ) {
        Row(
            Modifier.padding(start = 6.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable {
                        if (playback.isPlaying) playback.pause() else playback.resume(context)
                    }
                    .semantics { contentDescription = playLabel },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = adaptiveColors.userAccentColor,
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .clickable(onClick = onTap)
                    .semantics { contentDescription = jumpLabel },
            ) {
                Text(
                    senderName,
                    color = adaptiveColors.messageTextColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${formatMiniPlayerTime(playback.currentTime.toDouble())} / ${formatMiniPlayerTime(playback.duration)}",
                    color = adaptiveColors.timestampColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(adaptiveColors.messageTextColor.copy(alpha = 0.12f))
                    .clickable { playback.cyclePlaybackRate() }
                    .width(38.dp)
                    .padding(vertical = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(speedLabel, color = adaptiveColors.messageTextColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Box(
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { playback.stop() }
                    .semantics { contentDescription = closeLabel },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = null,
                    tint = adaptiveColors.messageTextColor.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        // Progreso fino en el borde inferior.
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 14.dp)
                .fillMaxWidth(progress)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(adaptiveColors.userAccentColor),
        )
    }
}

private fun formatMiniPlayerTime(seconds: Double): String {
    val total = seconds.coerceAtLeast(0.0).toInt()
    return "%d:%02d".format(total / 60, total % 60)
}
