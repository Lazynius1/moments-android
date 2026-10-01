package com.moments.android.views.creator.components.music

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.moments.android.models.StoryMusicSelection
import kotlinx.coroutines.*
import java.io.File
import java.net.URL
import java.nio.ByteOrder
import kotlin.math.*

class StoryMusicAudio(context: Context) {
    private val player = ExoPlayer.Builder(context).build()
    var playing by mutableStateOf(false); private set
    var elapsed by mutableDoubleStateOf(0.0); private set
    var failed by mutableStateOf(false); private set
    private var selection: StoryMusicSelection? = null
    init { player.addListener(object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) { failed = true; pause() }
    }) }
    fun load(value: StoryMusicSelection, play: Boolean) {
        selection = value; failed = false
        player.setMediaItem(MediaItem.fromUri(checkNotNull(value.track.previewURL)))
        player.prepare(); seek(value.start, play)
    }
    fun seek(seconds: Double, play: Boolean) { player.seekTo((seconds * 1000).toLong()); player.playWhenReady = play }
    fun update(value: StoryMusicSelection) { selection = value; seek(value.start, player.playWhenReady) }
    fun tick() {
        val value = selection ?: return
        elapsed = (player.currentPosition / 1000.0 - value.start).coerceIn(0.0, value.duration)
        if (player.currentPosition >= ((value.start + value.duration) * 1000).toLong()) player.seekTo((value.start * 1000).toLong())
    }
    fun play() { player.play() }
    fun pause() { player.pause() }
    fun release() { player.release() }
}

val LocalStoryMusicPlaying = staticCompositionLocalOf { true }

@Composable
fun rememberStoryMusicAudio(): StoryMusicAudio {
    val context = androidx.compose.ui.platform.LocalContext.current
    val player = remember(context) { StoryMusicAudio(context) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(player, owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) player.pause() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); player.release() }
    }
    LaunchedEffect(player) { while (isActive) { player.tick(); delay(80) } }
    return player
}

@Composable
fun StoryMusicPlayback(selection: StoryMusicSelection?, storyId: String?, active: Boolean, elapsed: Double): Boolean {
    val audio = rememberStoryMusicAudio()
    var resolved by remember { mutableStateOf<StoryMusicSelection?>(null) }
    val currentActive by rememberUpdatedState(active)
    val currentElapsed by rememberUpdatedState(elapsed)
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(selection?.track?.id, storyId) {
        audio.pause(); resolved = null
        selection ?: return@LaunchedEffect
        try {
            val track = StoryMusicCatalog.resolve(selection.track.id, storyId)
            val value = selection.copy(track = track).clamped()
            if (track.previewURL == null) return@LaunchedEffect
            resolved = value
            audio.load(value, false)
            audio.seek(value.start + currentElapsed.coerceIn(0.0, value.duration), currentActive)
        } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { }
    }
    LaunchedEffect(active, resolved?.track?.id, selection?.start) {
        val value = resolved ?: return@LaunchedEffect
        audio.seek(value.start + elapsed.coerceIn(0.0, value.duration), active && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(owner, active, resolved) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && currentActive) resolved?.let { audio.seek(it.start + currentElapsed.coerceIn(0.0, it.duration), true) }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return active && audio.playing
}

/** Decode actual PCM peaks. No invented placeholder waveform is displayed. */
object StoryMusicWaveformLoader {
    private val cache = android.util.LruCache<String, List<Float>>(12)
    suspend fun load(context: Context, track: com.moments.android.models.StoryMusicTrack): List<Float> = withContext(Dispatchers.IO) {
        cache.get(track.id)?.let { return@withContext it }
        val source = checkNotNull(track.previewURL)
        val file = File.createTempFile("music-wave-", ".audio", context.cacheDir)
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            val connection = URL(source).openConnection().apply { connectTimeout = 20000; readTimeout = 20000 }
            check(connection.contentLengthLong <= 40 * 1024 * 1024)
            connection.getInputStream().use { input -> file.outputStream().use { output ->
                val buffer = ByteArray(65536); var total = 0
                while (true) { ensureActive(); val count = input.read(buffer); if (count < 0) break
                    total += count; check(total <= 40 * 1024 * 1024); output.write(buffer, 0, count) }
            } }
            extractor.setDataSource(file.absolutePath)
            val index = (0 until extractor.trackCount).first { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
            extractor.selectTrack(index)
            val format = extractor.getTrackFormat(index)
            val decoder = MediaCodec.createDecoderByType(checkNotNull(format.getString(MediaFormat.KEY_MIME)))
            codec = decoder; decoder.configure(format, null, null, 0); decoder.start()
            val sums = DoubleArray(1200); val counts = IntArray(1200)
            val info = MediaCodec.BufferInfo(); var endedInput = false; var endedOutput = false
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE); var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var floatPcm = false; var idle = 0
            while (!endedOutput) {
                ensureActive(); check(idle < 1500)
                if (!endedInput) {
                    val bufferId = decoder.dequeueInputBuffer(10000)
                    if (bufferId >= 0) {
                        val buffer = checkNotNull(decoder.getInputBuffer(bufferId)); buffer.clear()
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) { decoder.queueInputBuffer(bufferId, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); endedInput = true }
                        else { decoder.queueInputBuffer(bufferId, 0, size, extractor.sampleTime, 0); extractor.advance() }
                    }
                }
                val output = decoder.dequeueOutputBuffer(info, 10000)
                when {
                    output == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = decoder.outputFormat; sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE); channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        floatPcm = f.containsKey(MediaFormat.KEY_PCM_ENCODING) && f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    }
                    output >= 0 -> {
                        idle = 0
                        val buffer = decoder.getOutputBuffer(output)
                        if (buffer != null && info.size > 0) {
                            buffer.position(info.offset); buffer.limit(info.offset + info.size); buffer.order(ByteOrder.nativeOrder())
                            var sample = 0
                            while (buffer.remaining() >= if (floatPcm) 4 else 2) {
                                val value = if (floatPcm) buffer.float.toDouble() else buffer.short / 32768.0
                                val seconds = info.presentationTimeUs / 1e6 + sample.toDouble() / max(channels * sampleRate, 1)
                                val bucket = floor(seconds / track.duration * sums.size).toInt().coerceIn(0, sums.lastIndex)
                                sums[bucket] += value * value; counts[bucket]++; sample++
                            }
                        }
                        endedOutput = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        decoder.releaseOutputBuffer(output, false)
                    }
                    else -> idle++
                }
            }
            check(counts.any { it > 0 })
            val peaks = sums.indices.map { sqrt(sums[it] / max(counts[it], 1)).toFloat() }
            val maximum = peaks.maxOrNull()?.coerceAtLeast(.00001f) ?: 1f
            peaks.map { (it / maximum).coerceIn(0f, 1f) }.also { cache.put(track.id, it) }
        } finally { runCatching { codec?.stop() }; codec?.release(); extractor.release(); file.delete() }
    }
}
