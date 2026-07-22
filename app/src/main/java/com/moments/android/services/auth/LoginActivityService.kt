package com.moments.android.services.auth

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.moments.android.models.LoginSession
import kotlinx.coroutines.tasks.await
import java.net.NetworkInterface
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Date
import java.util.Locale

data class LoginActivity(
    val id: String,
    val timestamp: Date,
    val device: String,
    val location: String,
    val ipAddress: String,
    val isSuccessful: Boolean,
    val failureReason: String? = null,
)

/** Port de RealLoginActivityService (LoginActivityService.swift). */
object LoginActivityService : LocationListener {

    private val db get() = FirebaseFirestore.getInstance()

    @Volatile private var appContext: Context? = null
    @Volatile private var currentLocation: Location? = null
    @Volatile private var currentLocationString: String = "Ubicacion no disponible"

    fun initialize(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        setupLocationManager()
    }

    private fun requireContext(): Context =
        appContext ?: error("LoginActivityService.initialize(Context) required")

    fun recordSuccessfulLogin(userId: String, method: String = "email") {
        requestLocationIfPossible()
        val now = Date()
        val deviceInfo = getCurrentDeviceInfo()
        val deviceFingerprint = currentDeviceFingerprint()
        val deviceDocId = hash(deviceFingerprint)
        val normalizedLocation = normalizeLocation(currentLocationString)

        val ref = db.collection("users").document(userId)
            .collection("loginActivity").document(deviceDocId)

        ref.get().addOnSuccessListener { snapshot ->
            val existing = snapshot.data.orEmpty()
            val existed = snapshot.exists()
            val previousLocation = existing["locationNormalized"] as? String ?: "unknown"
            val hasPreviousLocation = previousLocation != "unknown"
            val hasCurrentLocation = normalizedLocation != "unknown"
            val locationChanged = existed && hasPreviousLocation && hasCurrentLocation &&
                previousLocation != normalizedLocation

            val payload = hashMapOf<String, Any>(
                "deviceDocId" to deviceDocId,
                "deviceFingerprint" to deviceFingerprint,
                "device" to deviceInfo,
                "location" to currentLocationString,
                "locationNormalized" to normalizedLocation,
                "ipAddress" to getCurrentIPAddress(),
                "isSuccessful" to true,
                "isActive" to true,
                "loginMethod" to method,
                "lastSeenAt" to Timestamp(now),
                "lastSuccessfulAt" to Timestamp(now),
                "updatedAt" to Timestamp(now),
                "isNewDevice" to !existed,
                "isSuspicious" to locationChanged,
                "coordinates" to getCoordinatesDict(),
            )
            if (!existed) payload["firstSeenAt"] = Timestamp(now)
            if (locationChanged) {
                payload["suspiciousReason"] = "location_change"
                payload["lastLocationChangeAt"] = Timestamp(now)
            } else {
                payload["suspiciousReason"] = FieldValue.delete()
            }
            ref.set(payload, com.google.firebase.firestore.SetOptions.merge())
        }
    }

    fun trackFailedLoginAttempt(email: String, reason: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val deviceDocId = hash(currentDeviceFingerprint())
        val now = Timestamp(Date())
        db.collection("users").document(userId)
            .collection("loginActivity").document(deviceDocId)
            .set(
                mapOf(
                    "failedAttempts" to FieldValue.increment(1),
                    "lastFailureReason" to reason,
                    "lastFailedAt" to now,
                    "updatedAt" to now,
                ),
                com.google.firebase.firestore.SetOptions.merge(),
            )
    }

    suspend fun getCurrentSession(userId: String): LoginSession? {
        val sessions = fetchActiveSessions(userId)
        if (sessions.isEmpty()) return null
        val currentFingerprint = currentDeviceFingerprint()
        return sessions.firstOrNull { it.deviceIdentifier == currentFingerprint } ?: sessions.first()
    }

    suspend fun fetchActiveSessions(userId: String): List<LoginSession> =
        mapDocumentsToSessions(fetchLoginActivityDocuments(userId)).filter { it.isActive }

    suspend fun fetchLoginActivity(userId: String): List<LoginActivity> {
        val docs = fetchLoginActivityDocuments(userId)
        return docs.mapNotNull { doc ->
            val data = doc.data ?: return@mapNotNull null
            val timestamp = (data["lastSeenAt"] as? Timestamp)?.toDate()
                ?: (data["timestamp"] as? Timestamp)?.toDate()
                ?: (data["firstSeenAt"] as? Timestamp)?.toDate()
                ?: Date()
            LoginActivity(
                id = doc.id,
                timestamp = timestamp,
                device = data["device"] as? String ?: "Dispositivo desconocido",
                location = data["location"] as? String ?: "Ubicacion no disponible",
                ipAddress = data["ipAddress"] as? String ?: "No disponible",
                isSuccessful = data["isSuccessful"] as? Boolean ?: true,
                failureReason = data["lastFailureReason"] as? String,
            )
        }.sortedByDescending { it.timestamp }
    }

    suspend fun invalidateSession(
        userId: String,
        session: LoginSession,
        signOutIfCurrentDevice: Boolean,
    ) {
        val documentId = if (session.id == "local_current_session") {
            hash(currentDeviceFingerprint())
        } else {
            session.id
        }
        val now = Timestamp(Date())
        db.collection("users").document(userId)
            .collection("loginActivity").document(documentId)
            .set(
                mapOf(
                    "isActive" to false,
                    "sessionRevokedAt" to now,
                    "sessionRevokedReason" to "user_requested_logout_single",
                    "updatedAt" to now,
                ),
                com.google.firebase.firestore.SetOptions.merge(),
            ).await()
        if (signOutIfCurrentDevice) FirebaseAuth.getInstance().signOut()
    }

    suspend fun invalidateAllSessions(userId: String) {
        val snapshot = db.collection("users").document(userId)
            .collection("loginActivity").get().await()
        if (snapshot.isEmpty) {
            FirebaseAuth.getInstance().signOut()
            return
        }
        val now = Timestamp(Date())
        val batch = db.batch()
        for (doc in snapshot.documents) {
            batch.set(
                doc.reference,
                mapOf(
                    "isActive" to false,
                    "sessionRevokedAt" to now,
                    "sessionRevokedReason" to "user_requested_logout_all",
                    "updatedAt" to now,
                ),
                com.google.firebase.firestore.SetOptions.merge(),
            )
        }
        batch.commit().await()
        FirebaseAuth.getInstance().signOut()
    }

    fun requestLocationPermission() {
        // La UI debe pedir el permiso; aquí solo intentamos leer si ya está concedido.
        requestLocationIfPossible()
    }

    fun getCurrentLocationString(): String = currentLocationString

    fun getCoordinatesDict(): Map<String, Double> {
        val location = currentLocation ?: return emptyMap()
        return mapOf(
            "latitude" to location.latitude,
            "longitude" to location.longitude,
        )
    }

    fun currentDeviceDisplayName(): String = getCurrentDeviceInfo()

    private suspend fun fetchLoginActivityDocuments(userId: String): List<DocumentSnapshot> {
        val collection = db.collection("users").document(userId).collection("loginActivity")
        return try {
            collection.orderBy("lastSeenAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(80).get().await().documents
        } catch (_: Exception) {
            collection.orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(80).get().await().documents
        }
    }

    private fun mapDocumentsToSessions(docs: List<DocumentSnapshot>): List<LoginSession> {
        val deduped = linkedMapOf<String, LoginSession>()
        for (doc in docs) {
            val data = doc.data ?: continue
            val deviceIdentifier = data["deviceFingerprint"] as? String
                ?: data["deviceIdentifier"] as? String
                ?: data["deviceId"] as? String
                ?: ""
            val device = data["device"] as? String ?: "Dispositivo desconocido"
            val location = data["location"] as? String ?: "Ubicacion no disponible"
            val ipAddress = data["ipAddress"] as? String ?: "No disponible"
            val timestamp = (data["lastSeenAt"] as? Timestamp)?.toDate()
                ?: (data["timestamp"] as? Timestamp)?.toDate()
                ?: (data["firstSeenAt"] as? Timestamp)?.toDate()
                ?: Date()
            val key = canonicalSessionKey(deviceIdentifier, device, location, ipAddress)
            val candidate = LoginSession(
                id = doc.id,
                device = device,
                location = location,
                ipAddress = ipAddress,
                timestamp = timestamp,
                isActive = data["isActive"] as? Boolean ?: true,
                deviceIdentifier = deviceIdentifier.ifEmpty { null },
                isSuspicious = data["isSuspicious"] as? Boolean ?: false,
                isNewDevice = data["isNewDevice"] as? Boolean ?: false,
                suspiciousReason = data["suspiciousReason"] as? String,
            )
            val existing = deduped[key]
            if (existing != null && existing.timestamp >= candidate.timestamp) continue
            deduped[key] = candidate
        }
        return deduped.values.sortedByDescending { it.timestamp }
    }

    private fun canonicalSessionKey(
        deviceIdentifier: String,
        device: String,
        location: String,
        ipAddress: String,
    ): String {
        val normalizedDeviceIdentifier = deviceIdentifier.trim().lowercase()
        if (normalizedDeviceIdentifier.isNotEmpty()) return "fingerprint:$normalizedDeviceIdentifier"
        val normalizedDevice = device.trim().lowercase()
        val normalizedIp = normalizeIPAddress(ipAddress)
        if (normalizedIp.isNotEmpty()) return "device_ip:$normalizedDevice|$normalizedIp"
        return "device_location:$normalizedDevice|${normalizeLocation(location)}"
    }

    private fun normalizeIPAddress(value: String): String {
        val normalized = value.trim().lowercase()
        if (normalized.isEmpty() || normalized == "no disponible" || normalized == "n/a" || normalized == "unknown") {
            return ""
        }
        return normalized
    }

    @SuppressLint("MissingPermission")
    private fun setupLocationManager() {
        requestLocationIfPossible()
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationIfPossible() {
        val context = appContext ?: return
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val provider = when {
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> null
        } ?: return
        runCatching {
            lm.requestSingleUpdate(provider, this, context.mainLooper)
        }
        lm.getLastKnownLocation(provider)?.let {
            currentLocation = it
            updateLocationString(it)
        }
    }

    override fun onLocationChanged(location: Location) {
        currentLocation = location
        updateLocationString(location)
    }

    private fun updateLocationString(location: Location) {
        val context = appContext ?: return
        if (!Geocoder.isPresent()) {
            currentLocationString = "Ubicacion no disponible"
            return
        }
        runCatching {
            @Suppress("DEPRECATION")
            val placemarks = Geocoder(context, Locale.getDefault())
                .getFromLocation(location.latitude, location.longitude, 1)
            val placemark = placemarks?.firstOrNull()
            if (placemark == null) {
                currentLocationString = "Ubicacion no disponible"
                return
            }
            val components = mutableListOf<String>()
            placemark.locality?.let { components += it }
            placemark.countryName?.let { components += it }
            if (components.isEmpty()) {
                placemark.adminArea?.let { components += it }
                placemark.countryName?.let { components += it }
            }
            currentLocationString = if (components.isEmpty()) {
                "Ubicacion no disponible"
            } else {
                components.joinToString(", ")
            }
        }.onFailure {
            currentLocationString = "Ubicacion no disponible"
        }
    }

    private fun getCurrentDeviceInfo(): String {
        val model = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Android"
        val release = Build.VERSION.RELEASE ?: "?"
        return "$model - Android $release"
    }

    @SuppressLint("HardwareIds")
    private fun currentDeviceFingerprint(): String {
        val context = requireContext()
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        return if (!androidId.isNullOrBlank()) androidId else getCurrentDeviceInfo()
    }

    private fun normalizeLocation(value: String): String {
        val folded = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .trim()
            .lowercase()
        if (folded.isEmpty()) return "unknown"
        val invalid = listOf(
            "ubicacion no disponible",
            "error al obtener ubicacion",
            "permisos de ubicacion denegados",
            "location unavailable",
            "unknown",
        )
        if (invalid.any { folded.contains(it) }) return "unknown"
        return folded
    }

    private fun hash(string: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(string.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun getCurrentIPAddress(): String {
        return try {
            var address = "No disponible"
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return address
            for (intf in interfaces) {
                val name = intf.name ?: continue
                if (name !in listOf("wlan0", "eth0", "rmnet0", "rmnet_data0", "ccmni0")) continue
                for (addr in intf.inetAddresses) {
                    if (!addr.isLoopbackAddress) {
                        val host = addr.hostAddress ?: continue
                        if (host.contains(':')) continue // prefer IPv4
                        address = host
                    }
                }
            }
            // Fallback: active network link
            if (address == "No disponible") {
                val cm = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                @Suppress("DEPRECATION")
                cm.activeNetworkInfo // touch for side-effect-free compile on older APIs
            }
            address
        } catch (_: Exception) {
            "No disponible"
        }
    }
}
