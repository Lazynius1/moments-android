package com.moments.android.services.video

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.moments.android.services.content.FeedMediaItem
import com.moments.android.services.content.FeedMoment
import com.moments.android.utilities.MomentsAudioSession
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Bridge hasta portar `VideoPlayerManager` (trozo 3 de VideoPlayer.swift).
 * ≡ API que usa `GlobalVideoManager` iOS sobre cada player registrado.
 */
interface RegisteredVideoPlayer {
    val isMuted: Boolean
    fun pauseVideo()
    fun resumeVideo()
    fun setMuted(muted: Boolean, respectSilentMode: Boolean = true)
    fun toggleMute(respectSilentMode: Boolean = true)
    fun setFinishedPlayback(finished: Boolean) {}
    fun replayFromBeginning() {}
}

/**
 * Port de `GlobalVideoManager` (VideoPlayer.swift ~L6–246).
 * Singleton observable: active id, live seconds, mute de sesión, handoffs, registro de players.
 */
object GlobalVideoManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val lock = Any()

    private val allPlayers = ConcurrentHashMap<String, RegisteredVideoPlayer>()
    private val playbackPositionsByMomentId = ConcurrentHashMap<String, Double>()
    private val preservedPlayerConsumerIds = mutableSetOf<String>()
    private val pendingDetailHandoffMomentIds = mutableSetOf<String>()
    private val playbackHoldOwners = ConcurrentHashMap.newKeySet<String>()
    private val finishedPlaybackIds = mutableSetOf<String>()

    private val _activeVideoId = MutableStateFlow<String?>(null)
    val activeVideoId: StateFlow<String?> = _activeVideoId.asStateFlow()

    private val _livePlaybackSeconds = MutableStateFlow<Map<String, Double>>(emptyMap())
    val livePlaybackSeconds: StateFlow<Map<String, Double>> = _livePlaybackSeconds.asStateFlow()

    private val _userHasEnabledSoundInSession = MutableStateFlow(false)
    val userHasEnabledSoundInSession: StateFlow<Boolean> = _userHasEnabledSoundInSession.asStateFlow()

    /** Overlay encima del feed: los posts no deben seguir sonando ni reanudarse por visibilidad. */
    private val _isPlaybackHeld = MutableStateFlow(false)
    val isPlaybackHeld: StateFlow<Boolean> = _isPlaybackHeld.asStateFlow()

    /** Posts cuyo vídeo ha llegado al final (overlay “Ver otra vez”). */
    private val _finishedPlaybackIds = MutableStateFlow<Set<String>>(emptySet())
    val finishedPlaybackIdsFlow: StateFlow<Set<String>> = _finishedPlaybackIds.asStateFlow()

    @Volatile private var initialized = false
    private var appContext: Context? = null
    private var lastMusicVolume: Int = -1
    private var volumeObserver: ContentObserver? = null

    /** ≡ iOS `init` — lifecycle pause + observación de volumen. */
    fun initialize(context: Context) {
        if (initialized) return
        synchronized(lock) {
            if (initialized) return
            appContext = context.applicationContext
            ProcessLifecycleOwner.get().lifecycle.addObserver(
                object : DefaultLifecycleObserver {
                    // ≡ iOS willResignActive → pauseAllVideos (sin resetear mute de sesión)
                    override fun onStop(owner: LifecycleOwner) {
                        pauseAllVideos()
                    }
                },
            )
            startVolumeObservation(context.applicationContext)
            initialized = true
        }
    }

    // MARK: - Registro de players

    fun registerPlayer(playerId: String, manager: RegisteredVideoPlayer) {
        allPlayers[playerId] = manager
        if (_userHasEnabledSoundInSession.value) {
            manager.setMuted(false, respectSilentMode = true)
        }
    }

    fun unregisterPlayer(playerId: String, manager: RegisteredVideoPlayer) {
        if (allPlayers[playerId] === manager) {
            allPlayers.remove(playerId)
            if (_activeVideoId.value == playerId) {
                _activeVideoId.value = null
            }
        }
    }

    fun isRegisteredPlayer(playerId: String, manager: RegisteredVideoPlayer): Boolean =
        allPlayers[playerId] === manager

    // MARK: - Play / pause

    fun playVideo(playerId: String) {
        if (_isPlaybackHeld.value) return
        if (hasFinishedPlayback(playerId)) return
        val currentActive = _activeVideoId.value
        if (currentActive != null && currentActive != playerId) {
            pausePlayer(currentActive)
        }
        _activeVideoId.value = playerId
        resumePlayer(playerId)
    }

    fun pauseVideo(playerId: String) {
        if (_activeVideoId.value == playerId) {
            _activeVideoId.value = null
        }
        pausePlayer(playerId)
    }

    fun pauseAllVideos() {
        _activeVideoId.value = null
        allPlayers.values.forEach { it.pauseVideo() }
        runCatching { SharedVideoPlayerPool.pauseAll() }
    }

    fun pauseAllVideos(except: String) {
        allPlayers.forEach { (id, manager) ->
            if (id != except) manager.pauseVideo()
        }
        _activeVideoId.value = except
    }

    fun markPlaybackFinished(playerId: String) {
        synchronized(lock) { finishedPlaybackIds.add(playerId) }
        _finishedPlaybackIds.value = synchronized(lock) { finishedPlaybackIds.toSet() }
        allPlayers[playerId]?.setFinishedPlayback(true)
    }

    fun clearPlaybackFinished(playerId: String) {
        synchronized(lock) { finishedPlaybackIds.remove(playerId) }
        _finishedPlaybackIds.value = synchronized(lock) { finishedPlaybackIds.toSet() }
        allPlayers[playerId]?.setFinishedPlayback(false)
    }

    fun hasFinishedPlayback(playerId: String): Boolean =
        synchronized(lock) { finishedPlaybackIds.contains(playerId) }

    fun replayFromStart(playerId: String) {
        clearPlaybackFinished(playerId)
        setPlaybackPosition(0.0, playerId)
        val currentActive = _activeVideoId.value
        if (currentActive != null && currentActive != playerId) {
            pausePlayer(currentActive)
        }
        _activeVideoId.value = playerId
        val manager = allPlayers[playerId]
        if (manager != null) {
            manager.replayFromBeginning()
        } else {
            runCatching {
                val exo = SharedVideoPlayerPool.player(playerId)
                exo.seekTo(0)
                exo.play()
            }
        }
    }

    /**
     * Overlay u otra UI encima del feed: pausa el post activo y evita que la
     * visibilidad lo reanude. El owner evita que cerrar un overlay libere el
     * hold de otro que siga presentado.
     */
    fun beginPlaybackHold(owner: String = "legacy") {
        playbackHoldOwners += owner
        _isPlaybackHeld.value = true
        pauseAllVideos()
    }

    fun endPlaybackHold(owner: String = "legacy") {
        playbackHoldOwners -= owner
        _isPlaybackHeld.value = playbackHoldOwners.isNotEmpty()
    }

    // MARK: - Mute de sesión

    /** El usuario activó sonido en Reels u otro reproductor fuera del registro de feed. */
    fun enableSoundForSession() {
        _userHasEnabledSoundInSession.value = true
        scope.launch {
            // ≡ iOS configurePlaybackAudioSession (.playback / .moviePlayback)
            MomentsAudioSession.activate(
                usage = android.media.AudioAttributes.USAGE_MEDIA,
                contentType = android.media.AudioAttributes.CONTENT_TYPE_MOVIE,
            )
            allPlayers.values.forEach { it.setMuted(false, respectSilentMode = true) }
            runCatching { SharedVideoPlayerPool.setAllVolumes(1f) }
        }
    }

    /** Remute simétrico: limpia la preferencia de sesión y mutea todos los players registrados. */
    fun disableSoundForSession() {
        _userHasEnabledSoundInSession.value = false
        allPlayers.values.forEach { it.setMuted(true) }
        runCatching { SharedVideoPlayerPool.setAllVolumes(0f) }
    }

    fun toggleMute(playerId: String) {
        val manager = allPlayers[playerId]
        if (manager != null) {
            val wasMuted = manager.isMuted
            manager.toggleMute(respectSilentMode = true)
            if (wasMuted && !manager.isMuted) {
                enableSoundForSession()
            } else if (!wasMuted && manager.isMuted) {
                disableSoundForSession()
            }
            return
        }
        // Fallback pre–VideoPlayerManager: toggle de sesión vía pool (FeedVideoPage actual)
        if (!_userHasEnabledSoundInSession.value) {
            enableSoundForSession()
        } else {
            disableSoundForSession()
        }
    }

    fun isMuted(playerId: String): Boolean =
        allPlayers[playerId]?.isMuted ?: !_userHasEnabledSoundInSession.value

    fun isSessionMuted(): Boolean = !_userHasEnabledSoundInSession.value

    // MARK: - Playback position / LiveVideoTimeLabel

    fun setPlaybackPosition(seconds: Double, forMomentId: String) {
        if (!seconds.isFinite() || seconds < 0) return
        playbackPositionsByMomentId[forMomentId] = seconds
        _livePlaybackSeconds.value = _livePlaybackSeconds.value + (forMomentId to seconds)
    }

    fun playbackPosition(forMomentId: String): Double =
        playbackPositionsByMomentId[forMomentId] ?: 0.0

    fun resetPlaybackPosition(forMomentId: String) {
        playbackPositionsByMomentId[forMomentId] = 0.0
        _livePlaybackSeconds.value = _livePlaybackSeconds.value + (forMomentId to 0.0)
    }

    /** Captura posición actual del ExoPlayer del pool (ms → s). No crea slots nuevos. */
    fun capturePlaybackPosition(consumerId: String) {
        if (!SharedVideoPlayerPool.hasPlayer(consumerId)) return
        runCatching {
            val player = SharedVideoPlayerPool.player(consumerId)
            setPlaybackPosition(player.currentPosition / 1000.0, consumerId)
        }
    }

    // MARK: - Consumer ids (≡ iOS static helpers)

    fun profileVideoConsumerId(moment: FeedMoment): String =
        moment.id.ifBlank {
            "video_${moment.authorId}_${moment.timestamp / 1000}"
        }

    fun profileVideoConsumerId(moment: FeedMoment, mediaItem: FeedMediaItem): String =
        "${profileVideoConsumerId(moment)}_${mediaItem.id}"

    /** ≡ iOS `profileVideoConsumerId(for: Moment)` — Reels. */
    fun profileVideoConsumerId(moment: com.moments.android.models.Moment): String =
        moment.id?.takeIf { it.isNotBlank() }
            ?: "video_${moment.authorId}_${moment.timestamp.time / 1000}"

    /**
     * `FeedVisibilityCoordinator` publica el moment id; slides del carrusel usan `momentId_mediaId`.
     */
    fun visibilityMatches(activeMomentId: String?, videoConsumerId: String): Boolean {
        if (activeMomentId.isNullOrBlank()) return false
        return videoConsumerId == activeMomentId ||
            videoConsumerId.startsWith(activeMomentId + "_")
    }

    /** Android: unifica id de feed carousel (no está en GlobalVideoManager.swift; lo usan call sites). */
    fun feedVideoConsumerId(
        moment: FeedMoment,
        item: FeedMediaItem,
        prefersUnifiedCarouselFrame: Boolean,
    ): String = if (prefersUnifiedCarouselFrame) {
        profileVideoConsumerId(moment, item)
    } else {
        profileVideoConsumerId(moment)
    }

    // MARK: - Profile hero → detail handoff

    fun markProfileHeroHandoff(forMomentId: String) {
        synchronized(lock) {
            pendingDetailHandoffMomentIds.add(forMomentId)
            preservedPlayerConsumerIds.add(forMomentId)
        }
    }

    fun shouldPreserveSharedPlayer(consumerId: String): Boolean =
        synchronized(lock) { preservedPlayerConsumerIds.contains(consumerId) }

    fun canReuseSharedPlayer(consumerId: String): Boolean {
        if (!shouldPreserveSharedPlayer(consumerId)) return false
        return SharedVideoPlayerPool.hasActiveItem(consumerId)
    }

    fun hasPendingProfileDetailHandoff(forMomentId: String): Boolean =
        synchronized(lock) { pendingDetailHandoffMomentIds.contains(forMomentId) }

    /** Consumido una sola vez al montar el video en detalle tras transición hero. */
    fun consumeProfileDetailHandoff(forMomentId: String): ProfileDetailHandoff? {
        synchronized(lock) {
            if (!pendingDetailHandoffMomentIds.remove(forMomentId)) return null
            preservedPlayerConsumerIds.remove(forMomentId)
        }
        return ProfileDetailHandoff(
            reuseExistingItem = true,
            startAtSeconds = playbackPosition(forMomentId),
        )
    }

    fun clearProfilePlaybackHandoffState() {
        val orphaned: Set<String>
        synchronized(lock) {
            orphaned = preservedPlayerConsumerIds.toSet()
            pendingDetailHandoffMomentIds.clear()
            preservedPlayerConsumerIds.clear()
        }
        orphaned.forEach { SharedVideoPlayerPool.release(it) }
    }

    fun releasePreservedPlayer(consumerId: String) {
        synchronized(lock) { preservedPlayerConsumerIds.remove(consumerId) }
        SharedVideoPlayerPool.release(consumerId)
    }

    // MARK: - Feed → Reels handoff

    fun markReelsFeedHandoff(moment: FeedMoment, mediaItem: FeedMediaItem? = null) {
        val id = if (mediaItem != null) {
            profileVideoConsumerId(moment, mediaItem)
        } else {
            profileVideoConsumerId(moment)
        }
        synchronized(lock) { preservedPlayerConsumerIds.add(id) }
        clearPlaybackFinished(id)
    }

    fun completeReelsFeedHandoff(moment: FeedMoment, mediaItem: FeedMediaItem? = null) {
        val id = if (mediaItem != null) {
            profileVideoConsumerId(moment, mediaItem)
        } else {
            profileVideoConsumerId(moment)
        }
        clearPlaybackFinished(id)
        synchronized(lock) { preservedPlayerConsumerIds.remove(id) }
    }

    /**
     * Port de `Moment.videoPosterURLString(for:)` vía call sites Android
     * (no vive en GlobalVideoManager.swift; helper de Feed).
     */
    fun videoPosterUrl(moment: FeedMoment, item: FeedMediaItem): String? {
        item.thumbnailUrl?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        moment.thumbnailUrl?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        moment.imagePath?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        return null
    }

    // MARK: - Internals

    private fun pausePlayer(playerId: String) {
        allPlayers[playerId]?.pauseVideo()
            ?: runCatching { SharedVideoPlayerPool.player(playerId).pause() }
    }

    private fun resumePlayer(playerId: String) {
        allPlayers[playerId]?.resumeVideo()
        runCatching {
            val exo = SharedVideoPlayerPool.player(playerId)
            if (!exo.isPlaying) {
                exo.play()
            }
        }
    }

    /**
     * Subir volumen físico con vídeo activo → unmute de sesión.
     * ≡ iOS `startVolumeObservation` (AVAudioSession.outputVolume).
     */
    private fun startVolumeObservation(context: Context) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        lastMusicVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                val newVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                val oldVol = lastMusicVolume
                lastMusicVolume = newVol
                if (newVol > oldVol &&
                    _activeVideoId.value != null &&
                    !_userHasEnabledSoundInSession.value
                ) {
                    enableSoundForSession()
                }
            }
        }
        volumeObserver = observer
        runCatching {
            context.contentResolver.registerContentObserver(
                Settings.System.CONTENT_URI,
                true,
                observer,
            )
        }
    }

    data class ProfileDetailHandoff(
        val reuseExistingItem: Boolean,
        val startAtSeconds: Double,
    )
}
