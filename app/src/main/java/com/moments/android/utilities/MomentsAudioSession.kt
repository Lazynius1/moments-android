package com.moments.android.utilities

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Centraliza gestión de audio fuera del hilo principal. Equivalente de AVAudioSession.
 *
 * AudioFocusRequest exige [OnAudioFocusChangeListener] si se usa delayed focus gain
 * o pause-on-duck — sin listener, `build()` lanza IllegalStateException
 * ("Can't use delayed focus or pause on duck without a listener").
 */
object MomentsAudioSession {
    private var appContext: Context? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private var savedMode: Int? = null
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Listener obligatorio para delayed focus; iOS no tiene equivalente de callbacks. */
    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { /* no-op */ }

    fun initialize(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
            audioManager = appContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        }
    }

    /** Desactiva la sesión sin bloquear al llamante. */
    fun deactivate() {
        val manager = audioManager ?: return
        ioScope.launch {
            abandonFocus(manager)
        }
    }

    /** Restaura el modo que había antes y abandona el foco, fuera del main thread. */
    fun restore(mode: Int? = null) {
        val manager = audioManager ?: return
        ioScope.launch {
            mode?.let { manager.mode = it }
            savedMode?.let { manager.mode = it }
            abandonFocus(manager)
        }
    }

    /**
     * Solicita foco de audio. Hay que esperarla antes de reproducir/grabar.
     */
    suspend fun activate(
        usage: Int = AudioAttributes.USAGE_MEDIA,
        contentType: Int = AudioAttributes.CONTENT_TYPE_MUSIC,
        legacyStreamType: Int = AudioManager.STREAM_MUSIC,
    ): Boolean = withContext(Dispatchers.IO) {
        val manager = audioManager ?: return@withContext false
        savedMode = manager.mode

        return@withContext if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val attrs = AudioAttributes.Builder()
                .setUsage(usage)
                .setContentType(contentType)
                .build()
            // Listener + delayed focus: requerido por AudioFocusRequest.Builder.build()
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener(focusChangeListener, mainHandler)
                .setAcceptsDelayedFocusGain(true)
                .build()
            focusRequest = request
            manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            manager.requestAudioFocus(
                focusChangeListener,
                legacyStreamType,
                AudioManager.AUDIOFOCUS_GAIN,
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonFocus(manager: AudioManager) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            focusRequest?.let { manager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            manager.abandonAudioFocus(focusChangeListener)
        }
    }
}
