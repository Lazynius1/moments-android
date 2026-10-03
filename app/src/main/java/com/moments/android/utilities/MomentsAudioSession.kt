package com.moments.android.utilities

import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicLong

/** One temporary focus request shared by all audible players and recordings. */
object MomentsAudioSession {
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private var focusGeneration = 0L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val interruptionListeners = mutableSetOf<() -> Unit>()
    internal val coordinator = AudioFocusCoordinator { abandonNativeFocus() }

    fun initialize(context: Context) {
        synchronized(coordinator) {
            if (audioManager == null) {
                val app = context.applicationContext
                audioManager = app.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                ContextCompat.registerReceiver(app, object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) interrupt()
                    }
                }, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
                ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                    override fun onStop(owner: LifecycleOwner) { interrupt() }
                })
            }
        }
    }

    fun lease(onFocusLost: () -> Unit = {}): MomentsAudioSessionLease = MomentsAudioSessionLease(onFocusLost)

    fun addInterruptionListener(listener: () -> Unit): () -> Unit {
        synchronized(coordinator) { interruptionListeners += listener }
        return { synchronized(coordinator) { interruptionListeners -= listener } }
    }

    internal fun requestNativeFocus(usage: Int, contentType: Int): Boolean {
        val manager = audioManager ?: return false
        val generation = ++focusGeneration
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(usage).setContentType(contentType).build())
            .setWillPauseWhenDucked(true)
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener({ change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS ||
                    change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
                    change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                    val callbacks = synchronized(coordinator) {
                        if (generation != focusGeneration) return@setOnAudioFocusChangeListener
                        coordinator.interrupt() + interruptionListeners.toList()
                    }
                    callbacks.forEach { it() }
                }
            }, mainHandler)
            .build()
        val granted = manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (granted) focusRequest = request else manager.abandonAudioFocusRequest(request)
        return granted
    }

    private fun interrupt() {
        val callbacks = synchronized(coordinator) { coordinator.interrupt() + interruptionListeners.toList() }
        callbacks.forEach { it() }
    }

    private fun abandonNativeFocus() {
        ++focusGeneration
        focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        focusRequest = null
    }
}

class MomentsAudioSessionLease internal constructor(private val onFocusLost: () -> Unit) {
    private val generation = AtomicLong()
    fun activate(
        usage: Int = AudioAttributes.USAGE_MEDIA,
        contentType: Int = AudioAttributes.CONTENT_TYPE_MUSIC,
    ): Boolean {
        val current = generation.incrementAndGet()
        return MomentsAudioSession.coordinator.acquire(
            owner = this, generation = current,
            isCurrent = { generation.get() == current },
            requestFocus = { MomentsAudioSession.requestNativeFocus(usage, contentType) },
            onLoss = { if (generation.compareAndSet(current, current + 1)) onFocusLost() },
        )
    }
    fun release() {
        MomentsAudioSession.coordinator.release(this, generation.incrementAndGet())
    }
}
