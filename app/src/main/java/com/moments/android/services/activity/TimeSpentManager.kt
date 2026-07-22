package com.moments.android.services.activity

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.moments.android.R
import com.moments.android.models.MomentsNotification
import com.moments.android.models.NotificationType
import com.moments.android.notifications.services.NotificationPresentationCoordinator
import com.moments.android.notifications.services.NotificationPresentationSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Port 1:1 de `Services/Activity/TimeSpentManager.swift`.
 * Mide tiempo en foreground, límite diario y avisos (local + banner in-app).
 */
class TimeSpentManager private constructor(
    private val app: Application,
) : DefaultLifecycleObserver {

    private val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _dailySeconds = MutableStateFlow<Map<String, Double>>(emptyMap())
    val dailySeconds: StateFlow<Map<String, Double>> = _dailySeconds.asStateFlow()

    private val _dailyLimitSeconds = MutableStateFlow<Double?>(null)
    val dailyLimitSeconds: StateFlow<Double?> = _dailyLimitSeconds.asStateFlow()

    private var sessionStartTime: Date? = null
    private var hasNotifiedToday: Boolean = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private val flushRunnable = object : Runnable {
        override fun run() {
            flushCurrentSessionInterval()
            checkDailyLimit()
            mainHandler.postDelayed(this, TimeUnit.SECONDS.toMillis(60))
        }
    }

    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    init {
        loadData()
        ensureNotificationChannel()
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        mainHandler.post {
            // Equivalente a arrancar si la app ya está active al init.
            startSession()
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        startSession()
    }

    override fun onStop(owner: LifecycleOwner) {
        endSession()
    }

    private fun startSession() {
        if (sessionStartTime == null) {
            sessionStartTime = Date()
            val lastNotifiedDate = prefs.getString(LAST_NOTIFICATION_DATE_KEY, null)
            hasNotifiedToday = lastNotifiedDate == dateKey(Date())
            startTimer()
        }
    }

    fun endSession() {
        val start = sessionStartTime ?: return
        val duration = (Date().time - start.time) / 1000.0
        addTime(duration = duration, forDate = start)
        sessionStartTime = null
        stopTimer()
    }

    private fun startTimer() {
        stopTimer()
        mainHandler.postDelayed(flushRunnable, TimeUnit.SECONDS.toMillis(60))
    }

    private fun stopTimer() {
        mainHandler.removeCallbacks(flushRunnable)
    }

    private fun checkDailyLimit() {
        val limit = _dailyLimitSeconds.value ?: return

        val todayKey = dateKey(Date())
        val lastNotifiedDate = prefs.getString(LAST_NOTIFICATION_DATE_KEY, null)
        hasNotifiedToday = lastNotifiedDate == todayKey
        if (hasNotifiedToday) return

        val todaySeconds = getSeconds(Date())
        var currentSessionSeconds = 0.0
        sessionStartTime?.let { start ->
            currentSessionSeconds = (Date().time - start.time) / 1000.0
        }

        val totalToday = todaySeconds + currentSessionSeconds
        if (totalToday >= limit) {
            sendDailyLimitInAppBanner()
            sendDailyLimitNotification()
            hasNotifiedToday = true
            prefs.edit().putString(LAST_NOTIFICATION_DATE_KEY, dateKey(Date())).apply()
        }
    }

    private fun sendDailyLimitNotification() {
        val title = app.getString(R.string.user_activity_time_spent_limit_reached_title)
        val body = app.getString(R.string.user_activity_time_spent_limit_reached_body)
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_moments)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        // POST_NOTIFICATIONS se pedirá desde UI/Permissions; aquí se intenta publicar.
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    private fun sendDailyLimitInAppBanner() {
        val title = app.getString(R.string.user_activity_time_spent_limit_reached_in_app_title)
        val body = app.getString(R.string.user_activity_time_spent_limit_reached_body)
        val bannerNotification = MomentsNotification(
            id = UUID.randomUUID().toString(),
            type = NotificationType.MESSAGE,
            senderId = "system_time_limit",
            senderUsername = title,
            timestamp = Date(),
            isPending = true,
            reaction = body,
        )
        NotificationPresentationCoordinator.present(
            bannerNotification,
            NotificationPresentationSource.LOCAL,
        )
    }

    private fun addTime(duration: Double, forDate: Date) {
        if (duration <= 0) return
        val key = dateKey(forDate)
        _dailySeconds.update { current ->
            current.toMutableMap().apply {
                this[key] = (this[key] ?: 0.0) + duration
            }
        }
        saveData()
    }

    private fun flushCurrentSessionInterval() {
        val start = sessionStartTime ?: return
        val now = Date()
        val elapsed = (now.time - start.time) / 1000.0
        addTime(duration = elapsed, forDate = start)
        sessionStartTime = now
    }

    private fun loadData() {
        val raw = prefs.getString(DEFAULTS_KEY, null)
        if (raw != null) {
            runCatching {
                val json = JSONObject(raw)
                val map = mutableMapOf<String, Double>()
                json.keys().forEach { key ->
                    map[key] = json.getDouble(key)
                }
                _dailySeconds.value = map
            }
        }
        if (prefs.contains(LIMIT_KEY)) {
            _dailyLimitSeconds.value = prefs.getFloat(LIMIT_KEY, 0f).toDouble()
        }
    }

    private fun saveData() {
        val json = JSONObject()
        _dailySeconds.value.forEach { (key, value) -> json.put(key, value) }
        prefs.edit().putString(DEFAULTS_KEY, json.toString()).apply()
    }

    fun setDailyLimit(seconds: Double?) {
        _dailyLimitSeconds.value = seconds
        if (seconds != null) {
            prefs.edit().putFloat(LIMIT_KEY, seconds.toFloat()).apply()
            checkDailyLimit()
        } else {
            prefs.edit().remove(LIMIT_KEY).apply()
        }
    }

    private fun dateKey(date: Date): String = dateFormatter.format(date)

    fun getSeconds(date: Date): Double =
        _dailySeconds.value[dateKey(date)] ?: 0.0

    /** Últimos 7 días incluyendo hoy. Orden: [Hoy-6, …, Hoy]. */
    fun getLast7DaysData(): List<Pair<Date, Double>> {
        val result = mutableListOf<Pair<Date, Double>>()
        val cal = Calendar.getInstance()
        val today = Date()
        for (i in 6 downTo 0) {
            cal.time = today
            cal.add(Calendar.DAY_OF_YEAR, -i)
            val target = cal.time
            result.add(target to getSeconds(target))
        }
        return result
    }

    fun getWeeklyAverage(): Double {
        val data = getLast7DaysData()
        val total = data.sumOf { it.second }
        return total / 7.0
    }

    /** Fuerza flush de la sesión activa (para refrescar UI). */
    fun updateCurrentSession() {
        flushCurrentSessionInterval()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Daily limit",
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val PREFS_NAME = "moments_time_spent"
        private const val DEFAULTS_KEY = "Glowsy_TimeSpentData"
        private const val LIMIT_KEY = "Glowsy_DailyLimitSettings"
        private const val LAST_NOTIFICATION_DATE_KEY = "Glowsy_LastNotificationDate"
        private const val CHANNEL_ID = "moments_daily_limit"
        private const val NOTIFICATION_ID = 41001

        @Volatile
        private var instance: TimeSpentManager? = null

        val shared: TimeSpentManager
            get() = instance ?: error("TimeSpentManager.initialize(Application) must be called first")

        fun initialize(app: Application) {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        instance = TimeSpentManager(app)
                    }
                }
            }
        }
    }
}
