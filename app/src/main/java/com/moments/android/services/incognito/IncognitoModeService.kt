package com.moments.android.services.incognito

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.services.network.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.ceil

/**
 * Port de IncognitoModeService.swift.
 * SharedPreferences sustituye UserDefaults; StateFlow sustituye @Published.
 * Live Activity / WidgetCenter / Haptics omitidos (iOS-only).
 */
object IncognitoModeService {

    enum class LastErrorState {
        OFFLINE, UNAUTHORIZED, EXHAUSTED, UNAVAILABLE, UNKNOWN
    }

    private enum class Action(val endpoint: String) {
        GET("getIncognitoState"),
        ACTIVATE("activateIncognito"),
        PAUSE("pauseIncognito"),
        RESUME("resumeIncognito"),
    }

    private object SharedPrefsKeys {
        const val PREFS = "moments_incognito_mirror"
        const val MIRRORED_STATE = "incognito_mirrored_state"
        const val MIRRORED_IS_ACTIVE = "incognito_mirrored_is_active"
        const val MIRRORED_REMAINING_SECONDS = "incognito_mirrored_remaining_seconds"
        const val PENDING_ACTION = "incognito_pending_widget_action"
        const val PENDING_ACTION_TIMESTAMP = "incognito_pending_widget_action_timestamp"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val iso8601: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    @Volatile private var appContext: Context? = null

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _remainingSeconds = MutableStateFlow(30 * 60)
    val remainingSeconds: StateFlow<Int> = _remainingSeconds.asStateFlow()

    private val _dailyBudgetSeconds = MutableStateFlow(30 * 60)
    val dailyBudgetSeconds: StateFlow<Int> = _dailyBudgetSeconds.asStateFlow()

    private val _lastErrorState = MutableStateFlow<LastErrorState?>(null)
    val lastErrorState: StateFlow<LastErrorState?> = _lastErrorState.asStateFlow()

    val isExhausted: Boolean get() = _remainingSeconds.value <= 0

    val formattedTime: String
        get() {
            val remaining = maxOf(_remainingSeconds.value, 0)
            return "%02d:%02d".format(remaining / 60, remaining % 60)
        }

    val progress: Double
        get() {
            val budget = _dailyBudgetSeconds.value
            if (budget <= 0) return 0.0
            return minOf(maxOf(_remainingSeconds.value.toDouble() / budget, 0.0), 1.0)
        }

    /** Equivalente a IncognitoModeService.isActiveSnapshot de iOS. */
    val isActiveSnapshot: Boolean
        get() = prefs()?.getBoolean(SharedPrefsKeys.MIRRORED_IS_ACTIVE, false) ?: false

    private var countdownJob: Job? = null
    private var sessionExpectedEndTime: Date? = null
    private var lastKnownTimezoneIdentifier = TimeZone.getDefault().id

    fun initialize(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
            hydrateFromMirror()
        }
    }

    fun loadState() {
        scope.launch { perform(Action.GET) }
    }

    fun refresh() {
        scope.launch { perform(Action.GET) }
    }

    fun activate() {
        scope.launch { perform(Action.ACTIVATE) }
    }

    fun pause() {
        scope.launch { perform(Action.PAUSE) }
    }

    fun pauseFromLiveActivity() {
        countdownJob?.cancel()
        countdownJob = null
        _isActive.value = false
        sessionExpectedEndTime = null
        syncMirror(lastUpdatedAt = Date())
        scope.launch { perform(Action.PAUSE) }
    }

    fun resume() {
        scope.launch { perform(Action.RESUME) }
    }

    fun handlePendingAppGroupActionIfNeeded() {
        scope.launch { processPendingAppGroupActionIfNeeded() }
    }

    fun resetForSignedOutUser() {
        countdownJob?.cancel()
        countdownJob = null
        _isLoaded.value = false
        _isSyncing.value = false
        _isActive.value = false
        _remainingSeconds.value = 30 * 60
        _dailyBudgetSeconds.value = 30 * 60
        sessionExpectedEndTime = null
        _lastErrorState.value = null
        prefs()?.edit()
            ?.remove(SharedPrefsKeys.MIRRORED_STATE)
            ?.remove(SharedPrefsKeys.MIRRORED_IS_ACTIVE)
            ?.remove(SharedPrefsKeys.MIRRORED_REMAINING_SECONDS)
            ?.apply()
    }

    private suspend fun perform(action: Action) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            _lastErrorState.value = LastErrorState.UNAUTHORIZED
            return
        }
        if (!NetworkMonitor.isConnected) {
            _lastErrorState.value = LastErrorState.OFFLINE
            return
        }

        _isSyncing.value = true
        try {
            val token = user.getIdToken(false).await().token ?: throw IllegalStateException("No token")
            val response = callBackend(action, token)
            apply(response.state)
            _lastErrorState.value = if (response.reason == "exhausted") LastErrorState.EXHAUSTED else null
        } catch (e: java.net.UnknownHostException) {
            _lastErrorState.value = LastErrorState.OFFLINE
        } catch (e: java.net.SocketTimeoutException) {
            _lastErrorState.value = LastErrorState.UNAVAILABLE
        } catch (e: java.io.IOException) {
            _lastErrorState.value = if (!NetworkMonitor.isConnected) LastErrorState.OFFLINE else LastErrorState.UNAVAILABLE
        } catch (e: Exception) {
            _lastErrorState.value = LastErrorState.UNKNOWN
        } finally {
            _isSyncing.value = false
        }
    }

    private data class BackendResponse(val success: Boolean, val reason: String?, val state: RemoteState)

    private data class RemoteState(
        val remainingSeconds: Int,
        val isActive: Boolean,
        val lastResetDate: String,
        val sessionStartedAt: Date?,
        val sessionExpectedEndTime: Date?,
        val timezoneIdentifier: String,
        val lastUpdatedAt: Date?,
        val dailyBudgetSeconds: Int,
    )

    private data class MirroredState(
        val isLoaded: Boolean,
        val isActive: Boolean,
        val remainingSeconds: Int,
        val dailyBudgetSeconds: Int,
        val sessionExpectedEndTime: Date?,
        val timezoneIdentifier: String,
        val lastUpdatedAt: Date?,
    )

    private suspend fun callBackend(action: Action, token: String): BackendResponse {
        val projectId = FirebaseApp.getInstance().options.projectId
            ?: throw IllegalStateException("Missing Firebase project ID")
        val url = URL("https://europe-southwest1-$projectId.cloudfunctions.net/${action.endpoint}")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
            doOutput = true
        }
        try {
            val body = JSONObject().put("timezoneIdentifier", TimeZone.getDefault().id).toString()
            connection.outputStream.use { it.write(body.toByteArray()) }
            val code = connection.responseCode
            val raw = (if (code in 200..299 || code == 409) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.readText().orEmpty()
            if (code !in 200..299 && code != 409) throw java.io.IOException("HTTP $code")
            return parseBackendResponse(raw)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseBackendResponse(raw: String): BackendResponse {
        val json = JSONObject(raw)
        val stateJson = json.getJSONObject("state")
        return BackendResponse(
            success = json.optBoolean("success", false),
            reason = json.optString("reason").takeIf { it.isNotEmpty() },
            state = RemoteState(
                remainingSeconds = stateJson.optInt("remainingSeconds", 0),
                isActive = stateJson.optBoolean("isActive", false),
                lastResetDate = stateJson.optString("lastResetDate", ""),
                sessionStartedAt = parseIsoDate(stateJson.optString("sessionStartedAt")),
                sessionExpectedEndTime = parseIsoDate(stateJson.optString("sessionExpectedEndTime")),
                timezoneIdentifier = stateJson.optString("timezoneIdentifier", TimeZone.getDefault().id),
                lastUpdatedAt = parseIsoDate(stateJson.optString("lastUpdatedAt")),
                dailyBudgetSeconds = stateJson.optInt("dailyBudgetSeconds", 30 * 60),
            ),
        )
    }

    private fun apply(state: RemoteState) {
        lastKnownTimezoneIdentifier = state.timezoneIdentifier
        _dailyBudgetSeconds.value = maxOf(state.dailyBudgetSeconds, 1)
        sessionExpectedEndTime = state.sessionExpectedEndTime
        _isLoaded.value = true
        _isActive.value = state.isActive
        _remainingSeconds.value = resolvedRemainingSeconds(state)
        if (_remainingSeconds.value <= 0) {
            _isActive.value = false
            sessionExpectedEndTime = null
        }
        syncMirror(state.lastUpdatedAt)
        updatePresentationTimer()
    }

    private fun resolvedRemainingSeconds(state: RemoteState): Int {
        val endDate = state.sessionExpectedEndTime
        if (state.isActive && endDate != null) {
            return maxOf(ceil((endDate.time - System.currentTimeMillis()) / 1000.0).toInt(), 0)
        }
        return maxOf(state.remainingSeconds, 0)
    }

    private fun updatePresentationTimer() {
        countdownJob?.cancel()
        countdownJob = null
        val endDate = sessionExpectedEndTime ?: return
        if (!_isActive.value) return

        countdownJob = scope.launch {
            while (true) {
                val nextValue = maxOf(ceil((endDate.time - System.currentTimeMillis()) / 1000.0).toInt(), 0)
                if (nextValue != _remainingSeconds.value) {
                    _remainingSeconds.value = nextValue
                    syncMirror(Date())
                }
                if (nextValue <= 0) {
                    _isActive.value = false
                    sessionExpectedEndTime = null
                    _lastErrorState.value = LastErrorState.EXHAUSTED
                    syncMirror(Date())
                    break
                }
                delay(1_000)
            }
        }
    }

    private fun hydrateFromMirror() {
        val raw = prefs()?.getString(SharedPrefsKeys.MIRRORED_STATE, null) ?: return
        runCatching {
            val json = JSONObject(raw)
            val mirrored = MirroredState(
                isLoaded = json.optBoolean("isLoaded", false),
                isActive = json.optBoolean("isActive", false),
                remainingSeconds = json.optInt("remainingSeconds", 30 * 60),
                dailyBudgetSeconds = json.optInt("dailyBudgetSeconds", 30 * 60),
                sessionExpectedEndTime = parseIsoDate(json.optString("sessionExpectedEndTime")),
                timezoneIdentifier = json.optString("timezoneIdentifier", TimeZone.getDefault().id),
                lastUpdatedAt = parseIsoDate(json.optString("lastUpdatedAt")),
            )
            _isLoaded.value = mirrored.isLoaded
            _isActive.value = mirrored.isActive
            _remainingSeconds.value = mirrored.remainingSeconds
            _dailyBudgetSeconds.value = mirrored.dailyBudgetSeconds
            sessionExpectedEndTime = mirrored.sessionExpectedEndTime
            lastKnownTimezoneIdentifier = mirrored.timezoneIdentifier
            updatePresentationTimer()
        }
    }

    private fun syncMirror(lastUpdatedAt: Date?) {
        val mirrored = MirroredState(
            isLoaded = _isLoaded.value,
            isActive = _isActive.value,
            remainingSeconds = _remainingSeconds.value,
            dailyBudgetSeconds = _dailyBudgetSeconds.value,
            sessionExpectedEndTime = sessionExpectedEndTime,
            timezoneIdentifier = lastKnownTimezoneIdentifier,
            lastUpdatedAt = lastUpdatedAt,
        )
        val json = JSONObject().apply {
            put("isLoaded", mirrored.isLoaded)
            put("isActive", mirrored.isActive)
            put("remainingSeconds", mirrored.remainingSeconds)
            put("dailyBudgetSeconds", mirrored.dailyBudgetSeconds)
            mirrored.sessionExpectedEndTime?.let { put("sessionExpectedEndTime", iso8601.format(it)) }
            put("timezoneIdentifier", mirrored.timezoneIdentifier)
            mirrored.lastUpdatedAt?.let { put("lastUpdatedAt", iso8601.format(it)) }
        }
        prefs()?.edit()
            ?.putString(SharedPrefsKeys.MIRRORED_STATE, json.toString())
            ?.putBoolean(SharedPrefsKeys.MIRRORED_IS_ACTIVE, mirrored.isActive)
            ?.putInt(SharedPrefsKeys.MIRRORED_REMAINING_SECONDS, mirrored.remainingSeconds)
            ?.apply()
    }

    private suspend fun processPendingAppGroupActionIfNeeded() {
        val rawAction = prefs()?.getString(SharedPrefsKeys.PENDING_ACTION, null) ?: return
        if (!NetworkMonitor.isConnected) {
            _lastErrorState.value = LastErrorState.OFFLINE
            return
        }
        if (!_isLoaded.value) perform(Action.GET)
        when (rawAction) {
            "pause" -> perform(Action.PAUSE)
            "resume" -> if (!_isActive.value && _remainingSeconds.value > 0) perform(Action.RESUME)
        }
        prefs()?.edit()
            ?.remove(SharedPrefsKeys.PENDING_ACTION)
            ?.remove(SharedPrefsKeys.PENDING_ACTION_TIMESTAMP)
            ?.apply()
    }

    private fun prefs() = appContext?.getSharedPreferences(SharedPrefsKeys.PREFS, Context.MODE_PRIVATE)

    private fun parseIsoDate(value: String?): Date? {
        if (value.isNullOrBlank()) return null
        return runCatching { iso8601.parse(value) }.getOrNull()
            ?: runCatching { Date(value.toLong()) }.getOrNull()
    }
}
