package com.moments.android.views.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.models.LoginSession
import com.moments.android.services.auth.LoginActivityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.Date
import java.util.Locale

/**
 * Port de `LoginActivityViewModel` en `LoginActivityView.swift`.
 */
class LoginActivityViewModel {
    var currentSession by mutableStateOf<LoginSession?>(null)
    var otherSessions by mutableStateOf<List<LoginSession>>(emptyList())
    var showLogoutAllAlert by mutableStateOf(false)
    var sessionPendingLogout by mutableStateOf<LoginSession?>(null)
    var showError by mutableStateOf(false)
    var showLogoutSuccess by mutableStateOf(false)
    var logoutSuccessMessage by mutableStateOf("")
    var errorMessage by mutableStateOf("")
    var isLoadingSession by mutableStateOf(false)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun loadLoginActivity(
        notAuthenticatedMessage: String,
        loadErrorPrefix: String,
        completion: () -> Unit,
    ) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            showErrorAlert(notAuthenticatedMessage)
            isLoadingSession = false
            completion()
            return
        }

        isLoadingSession = true
        scope.launch {
            try {
                val sessions = withContext(Dispatchers.IO) {
                    LoginActivityService.fetchActiveSessions(userId)
                }
                applySessions(current = null, activeSessions = sessions)
            } catch (e: Exception) {
                applySessions(current = null, activeSessions = emptyList())
                showErrorAlert("$loadErrorPrefix${e.localizedMessage ?: e.message ?: ""}")
            } finally {
                isLoadingSession = false
                completion()
            }
        }
    }

    suspend fun refreshLoginActivity(
        notAuthenticatedMessage: String,
        refreshErrorPrefix: String,
    ) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            showErrorAlert(notAuthenticatedMessage)
            return
        }
        try {
            val sessions = withContext(Dispatchers.IO) {
                LoginActivityService.fetchActiveSessions(userId)
            }
            applySessions(current = null, activeSessions = sessions)
        } catch (e: Exception) {
            applySessions(current = null, activeSessions = emptyList())
            showErrorAlert("$refreshErrorPrefix${e.localizedMessage ?: e.message ?: ""}")
        }
    }

    fun requestLogout(session: LoginSession) {
        sessionPendingLogout = session
    }

    fun isCurrentDeviceSession(session: LoginSession): Boolean {
        val current = currentSession ?: return false
        return isSameSession(session, current)
    }

    fun confirmLogoutPendingSession(
        notAuthenticatedMessage: String,
        logoutErrorFormat: String,
        singleSuccessMessage: String,
    ) {
        val session = sessionPendingLogout ?: return
        sessionPendingLogout = null
        logoutSession(session, notAuthenticatedMessage, logoutErrorFormat, singleSuccessMessage)
    }

    fun logoutSession(
        session: LoginSession,
        notAuthenticatedMessage: String,
        logoutErrorFormat: String,
        singleSuccessMessage: String,
    ) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            showErrorAlert(notAuthenticatedMessage)
            return
        }

        val signsOutLocally = isCurrentDeviceSession(session)
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    LoginActivityService.invalidateSession(
                        userId = userId,
                        session = session,
                        signOutIfCurrentDevice = signsOutLocally,
                    )
                }
                logoutSuccessMessage = singleSuccessMessage
                showLogoutSuccess = true
                if (signsOutLocally) {
                    currentSession = null
                    otherSessions = emptyList()
                } else {
                    otherSessions = otherSessions.filterNot { isSameSession(it, session) }
                }
            } catch (e: Exception) {
                showErrorAlert(
                    String.format(
                        Locale.getDefault(),
                        logoutErrorFormat,
                        e.localizedMessage ?: e.message ?: "",
                    ),
                )
            }
        }
    }

    fun logoutAllSessions(
        notAuthenticatedMessage: String,
        logoutAllErrorPrefix: String,
        allSuccessMessage: String,
    ) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            showErrorAlert(notAuthenticatedMessage)
            return
        }
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    LoginActivityService.invalidateAllSessions(userId)
                }
                logoutSuccessMessage = allSuccessMessage
                showLogoutSuccess = true
                currentSession = null
                otherSessions = emptyList()
            } catch (e: Exception) {
                showErrorAlert("$logoutAllErrorPrefix${e.localizedMessage ?: e.message ?: ""}")
            }
        }
    }

    private fun applySessions(current: LoginSession?, activeSessions: List<LoginSession>) {
        val dedupedSessions = dedupeSessions(activeSessions)
        var resolvedCurrent = current
        var remaining = dedupedSessions.toMutableList()

        if (resolvedCurrent == null) {
            val currentDeviceId = LoginActivityService.currentDeviceId()
            remaining.firstOrNull {
                (it.deviceIdentifier ?: "").equals(currentDeviceId, ignoreCase = true)
            }?.let { matched ->
                resolvedCurrent = matched
            }
        }

        if (resolvedCurrent == null) {
            remaining.firstOrNull()?.let { resolvedCurrent = it }
        }

        resolvedCurrent?.let { currentResolved ->
            remaining.removeAll { isSameSession(it, currentResolved) }
        }

        if (resolvedCurrent == null) {
            resolvedCurrent = makeLocalCurrentSession()
        }

        currentSession = resolvedCurrent
        otherSessions = remaining.take(12)
    }

    private fun dedupeSessions(sessions: List<LoginSession>): List<LoginSession> {
        val byKey = linkedMapOf<String, LoginSession>()
        for (session in sessions) {
            val key = canonicalSessionKey(session)
            val existing = byKey[key]
            if (existing != null && existing.timestamp >= session.timestamp) continue
            byKey[key] = session
        }
        return byKey.values.sortedByDescending { it.timestamp }
    }

    private fun isSameSession(lhs: LoginSession, rhs: LoginSession): Boolean =
        canonicalSessionKey(lhs) == canonicalSessionKey(rhs)

    private fun canonicalSessionKey(session: LoginSession): String {
        val fingerprint = (session.deviceIdentifier ?: "")
            .trim()
            .lowercase()
        if (fingerprint.isNotEmpty()) return "fingerprint:$fingerprint"

        val device = session.device.trim().lowercase()
        val ip = normalizeIP(session.ipAddress)
        if (ip.isNotEmpty()) return "device_ip:$device|$ip"

        val location = normalizeLocation(session.location)
        return "device_location:$device|$location"
    }

    private fun normalizeIP(ip: String): String {
        val value = ip.trim().lowercase()
        if (value.isEmpty() || value == "no disponible" || value == "n/a" || value == "unknown") {
            return ""
        }
        return value
    }

    private fun normalizeLocation(location: String): String {
        val folded = Normalizer.normalize(location, Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .trim()
            .lowercase()
        if (folded.isEmpty() ||
            folded.contains("ubicacion no disponible") ||
            folded.contains("unknown")
        ) {
            return "unknown"
        }
        return folded
    }

    private fun makeLocalCurrentSession(): LoginSession {
        val timestamp = FirebaseAuth.getInstance().currentUser?.metadata?.lastSignInTimestamp
            ?.let { Date(it) }
            ?: Date()
        return LoginSession(
            id = "local_current_session",
            device = LoginActivityService.currentDeviceDisplayName(),
            location = LoginActivityService.getCurrentLocationString(),
            ipAddress = "No disponible",
            timestamp = timestamp,
            isActive = true,
            deviceIdentifier = LoginActivityService.currentDeviceId(),
            isSuspicious = false,
            isNewDevice = false,
            suspiciousReason = null,
        )
    }

    private fun showErrorAlert(message: String) {
        errorMessage = message
        showError = true
    }
}
