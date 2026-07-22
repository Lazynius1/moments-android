package com.moments.android.utilities

import android.content.Context
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import com.moments.android.services.performance.MotionPolicy

/**
 * Haptics + sonidos del sistema. Aproximación Android de UISelectionFeedbackGenerator,
 * UIImpactFeedbackGenerator, UINotificationFeedbackGenerator y AudioServicesPlaySystemSound.
 *
 * Mapeo de sonidos iOS → Android (ToneGenerator / SoundPool):
 * - 1022 buzz received → TONE_CDMA_ALERT_CALL_GUARD (tri-tone aprox.)
 * - 1033 buzz sent → TONE_PROP_ACK (whoosh/sent ack aprox.)
 * - 1113 voice record start → TONE_PROP_BEEP
 * - 1114 voice record end → TONE_PROP_BEEP2
 * - 1004 message sent → TONE_PROP_ACK
 * - 1003 message received → TONE_CDMA_ALERT_NETWORK_LITE
 */
class HapticManager private constructor() {
    private var appContext: Context? = null
    private var vibrator: Vibrator? = null
    private var toneGenerator: ToneGenerator? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        val shared = HapticManager()

        /** Inicializar con Application context antes del primer uso. */
        fun initialize(context: Context) {
            shared.ensureInitialized(context.applicationContext)
        }
    }

    private fun ensureInitialized(context: Context) {
        if (appContext != null) return
        appContext = context
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        runCatching {
            toneGenerator = ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 80)
        }
    }

    /** Play sound alert for incoming chat "zumbidos" (iOS 1022). */
    fun playBuzzReceivedSound() {
        playTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 180)
    }

    /** Play sound whoosh for outgoing chat "zumbidos" (iOS 1033). */
    fun playBuzzSentSound() {
        playTone(ToneGenerator.TONE_PROP_ACK, 120)
    }

    fun playVoiceRecordStartSound() {
        playTone(ToneGenerator.TONE_PROP_BEEP, 80)
    }

    fun playVoiceRecordEndSound() {
        playTone(ToneGenerator.TONE_PROP_BEEP2, 80)
    }

    fun playMessageSentSound() {
        playTone(ToneGenerator.TONE_PROP_ACK, 60)
    }

    fun playMessageReceivedSound() {
        playTone(ToneGenerator.TONE_CDMA_ALERT_NETWORK_LITE, 100)
    }

    /** Tick háptico continuo durante pull de vanish. */
    fun vanishPullStep(view: View? = null) {
        performHaptic(view, HapticFeedbackConstants.CLOCK_TICK)
    }

    /** Umbral alcanzado al completar el arco. */
    fun vanishPullThresholdReached(view: View? = null) {
        performImpact(view, intensity = 0.72f, durationMs = 18L)
    }

    /** Muesca suave al avanzar o retroceder por el recorrido de reply. */
    fun replySwipeStep(view: View? = null) {
        performHaptic(view, HapticFeedbackConstants.CLOCK_TICK)
    }

    /** Cierre más definido al alcanzar la distancia que activa reply. */
    fun replySwipeThresholdReached(view: View? = null) {
        performImpact(view, intensity = 0.68f, durationMs = 16L)
    }

    /** Triggered when the user changes a selection (e.g., Tab Bar). */
    fun selection(view: View? = null) {
        performHaptic(view, HapticFeedbackConstants.CLOCK_TICK)
    }

    /** Light impact for minor actions (e.g., button press). */
    fun lightImpact(view: View? = null) {
        performImpact(view, intensity = 0.45f, durationMs = 12L)
    }

    /** Medium impact for key actions (e.g., Like, Follow). */
    fun mediumImpact(view: View? = null) {
        performImpact(view, intensity = 0.65f, durationMs = 20L)
    }

    /** Heavy impact for main actions or "physical" collisions. */
    fun heavyImpact(view: View? = null) {
        performImpact(view, intensity = 1f, durationMs = 28L)
    }

    enum class NotificationType { SUCCESS, WARNING, ERROR }

    /** Notification feedback (Success, Warning, Error). */
    fun notification(type: NotificationType, view: View? = null) {
        val constant = when (type) {
            NotificationType.SUCCESS -> HapticFeedbackConstants.CONFIRM
            NotificationType.WARNING -> HapticFeedbackConstants.REJECT
            NotificationType.ERROR -> HapticFeedbackConstants.REJECT
        }
        performHaptic(view, constant)
    }

    enum class ImpactStyle { LIGHT, SOFT, MEDIUM, HEAVY, RIGID }

    /** Impact genérico (p. ej. Explore legacy). */
    fun impact(style: ImpactStyle, view: View? = null) {
        when (style) {
            ImpactStyle.LIGHT, ImpactStyle.SOFT -> lightImpact(view)
            ImpactStyle.MEDIUM -> mediumImpact(view)
            ImpactStyle.HEAVY, ImpactStyle.RIGID -> heavyImpact(view)
        }
    }

    /** Long, physical buzz pattern for incoming chat "zumbidos". */
    fun chatBuzzReceived(reduceMotion: Boolean = MotionPolicy.reduceMotion, view: View? = null) {
        heavyImpact(view)
        if (reduceMotion) return

        val pulses = listOf(
            Triple(80L, ImpactStyle.RIGID, 1.0f),
            Triple(160L, ImpactStyle.HEAVY, 0.96f),
            Triple(260L, ImpactStyle.RIGID, 0.9f),
            Triple(380L, ImpactStyle.HEAVY, 0.84f),
            Triple(520L, ImpactStyle.RIGID, 0.76f),
            Triple(680L, ImpactStyle.HEAVY, 0.68f),
            Triple(860L, ImpactStyle.RIGID, 0.58f),
        )

        pulses.forEach { (delayMs, style, intensity) ->
            mainHandler.postDelayed({
                performImpact(view, intensity = intensity, durationMs = 16L)
            }, delayMs)
        }
    }

    fun success(view: View? = null) = notification(NotificationType.SUCCESS, view)
    fun warning(view: View? = null) = notification(NotificationType.WARNING, view)
    fun error(view: View? = null) = notification(NotificationType.ERROR, view)

    private fun playTone(tone: Int, durationMs: Int) {
        runCatching { toneGenerator?.startTone(tone, durationMs) }
    }

    private fun performHaptic(view: View?, constant: Int) {
        if (view?.performHapticFeedback(constant) == true) return
        vibrate(durationMs = 10L, amplitude = 80)
    }

    private fun performImpact(view: View?, intensity: Float, durationMs: Long) {
        val amplitude = (intensity.coerceIn(0f, 1f) * 255).toInt().coerceIn(1, 255)
        if (view?.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) == true) return
        vibrate(durationMs = durationMs, amplitude = amplitude)
    }

    private fun vibrate(durationMs: Long, amplitude: Int) {
        val v = vibrator ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(durationMs)
            }
        }
    }
}
