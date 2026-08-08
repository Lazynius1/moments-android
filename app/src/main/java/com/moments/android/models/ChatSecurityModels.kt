package com.moments.android.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import java.util.Date

/**
 * Port de `Models/ChatSecurityModels.swift`.
 * Identidad E2E en `users/{uid}.chatKey` + bundle en `users/{uid}/chatRecovery`
 * (no hay colección `chatIdentities`).
 */

// MARK: - ChatIdentityRecord

data class ChatIdentityRecord(
    val keyId: String,
    val publicKeyBase64: String,
    val algorithm: String = "curve25519",
    val updatedAt: Date? = null,
) {
    /** ≡ iOS `asFirestoreData()`. */
    fun asFirestoreData(): Map<String, Any> = mapOf(
        "keyId" to keyId,
        "publicKeyBase64" to publicKeyBase64,
        "algorithm" to algorithm,
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    companion object {
        /** ≡ iOS `init?(map:)`. */
        fun from(map: Map<String, Any?>): ChatIdentityRecord? {
            val keyId = map["keyId"] as? String ?: return null
            val publicKeyBase64 = map["publicKeyBase64"] as? String ?: return null
            return ChatIdentityRecord(
                keyId = keyId,
                publicKeyBase64 = publicKeyBase64,
                algorithm = map["algorithm"] as? String ?: "curve25519",
                updatedAt = anyToDate(map["updatedAt"]),
            )
        }
    }
}

// MARK: - ChatRecoveryKDFParams

data class ChatRecoveryKDFParams(
    val iterations: Int = 200_000,
    val keyLength: Int = 32,
    val hash: String = "SHA256",
) {
    fun asFirestoreData(): Map<String, Any> = mapOf(
        "iterations" to iterations,
        "keyLength" to keyLength,
        "hash" to hash,
    )

    companion object {
        fun from(map: Map<String, Any?>): ChatRecoveryKDFParams? {
            // Firestore suele devolver Long; Number cubre Int/Long como iOS.
            val iterations = (map["iterations"] as? Number)?.toInt() ?: return null
            val keyLength = (map["keyLength"] as? Number)?.toInt() ?: return null
            val hash = map["hash"] as? String ?: return null
            return ChatRecoveryKDFParams(iterations, keyLength, hash)
        }
    }
}

// MARK: - ChatRecoveryBundle

data class ChatRecoveryBundle(
    val keyId: String? = null,
    val encryptedPrivateKey: String,
    val nonce: String,
    val salt: String,
    val kdf: String = "PBKDF2",
    val kdfParams: ChatRecoveryKDFParams = ChatRecoveryKDFParams(),
    val keyVersion: Int = 1,
    val encryptedUserKey: String? = null,
    val createdAt: Date? = null,
    val updatedAt: Date? = null,
) {
    fun asFirestoreData(): Map<String, Any> {
        val data = mutableMapOf<String, Any>(
            "encryptedPrivateKey" to encryptedPrivateKey,
            "nonce" to nonce,
            "salt" to salt,
            "kdf" to kdf,
            "kdfParams" to kdfParams.asFirestoreData(),
            "keyVersion" to keyVersion,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        keyId?.let { data["keyId"] = it }
        encryptedUserKey?.let { data["encryptedUserKey"] = it }
        data["createdAt"] = createdAt?.let { Timestamp(it) } ?: FieldValue.serverTimestamp()
        return data
    }

    companion object {
        fun from(map: Map<String, Any?>): ChatRecoveryBundle? {
            val encryptedPrivateKey = map["encryptedPrivateKey"] as? String ?: return null
            val nonce = map["nonce"] as? String ?: return null
            val salt = map["salt"] as? String ?: return null
            val kdf = map["kdf"] as? String ?: return null
            val kdfParamsMap = map["kdfParams"] as? Map<String, Any?> ?: return null
            val kdfParams = ChatRecoveryKDFParams.from(kdfParamsMap) ?: return null
            val keyVersion = (map["keyVersion"] as? Number)?.toInt() ?: return null
            return ChatRecoveryBundle(
                keyId = map["keyId"] as? String,
                encryptedPrivateKey = encryptedPrivateKey,
                nonce = nonce,
                salt = salt,
                kdf = kdf,
                kdfParams = kdfParams,
                keyVersion = keyVersion,
                encryptedUserKey = map["encryptedUserKey"] as? String,
                createdAt = anyToDate(map["createdAt"]),
                updatedAt = anyToDate(map["updatedAt"]),
            )
        }
    }
}

// MARK: - ChatRecoveryAttemptState

data class ChatRecoveryAttemptState(
    val failedAttempts: Int = 0,
    val maxAttempts: Int = 5,
    val lockedUntil: Date? = null,
) {
    /** ≡ iOS `isLocked` (`timeIntervalSinceNow > 0`). */
    val isLocked: Boolean
        get() = lockedUntil?.let { it.time > System.currentTimeMillis() } ?: false

    /** ≡ iOS `remainingLockout` (segundos). */
    val remainingLockout: Double
        get() = lockedUntil?.let {
            maxOf(0.0, (it.time - System.currentTimeMillis()) / 1000.0)
        } ?: 0.0

    val remainingLockoutInterval: Double?
        get() = if (isLocked) remainingLockout else null

    val remainingAttempts: Int
        get() = maxOf(0, maxAttempts - failedAttempts)
}

// MARK: - WrappedConversationKey

data class WrappedConversationKey(
    val wrappedKey: String,
    val senderPublicKey: String,
    val recipientKeyId: String,
    val wrappedAt: Date? = null,
    val wrappedBy: String,
) {
    fun asFirestoreData(): Map<String, Any> = mapOf(
        "wrappedKey" to wrappedKey,
        "senderPublicKey" to senderPublicKey,
        "recipientKeyId" to recipientKeyId,
        "wrappedAt" to FieldValue.serverTimestamp(),
        "wrappedBy" to wrappedBy,
    )

    companion object {
        fun from(map: Map<String, Any?>): WrappedConversationKey? {
            val wrappedKey = map["wrappedKey"] as? String ?: return null
            val senderPublicKey = map["senderPublicKey"] as? String ?: return null
            val recipientKeyId = map["recipientKeyId"] as? String ?: return null
            val wrappedBy = map["wrappedBy"] as? String ?: return null
            return WrappedConversationKey(
                wrappedKey = wrappedKey,
                senderPublicKey = senderPublicKey,
                recipientKeyId = recipientKeyId,
                wrappedAt = anyToDate(map["wrappedAt"]),
                wrappedBy = wrappedBy,
            )
        }
    }
}

// MARK: - ChatAccessState

sealed interface ChatAccessState {
    data object Available : ChatAccessState
    data object NeedsPinSetup : ChatAccessState
    data object NeedsRestore : ChatAccessState
    data class Unavailable(val reason: String) : ChatAccessState
}

/** Port de `ChatRecoveryMigrationSession`. */
data class ChatRecoveryMigrationSession(
    val migrationId: String,
    val qrPayload: String,
    val expiresAt: Date,
)

/** Port de `ChatRecoveryMigrationPayload` (JSON keys deben coincidir con iOS Codable). */
data class ChatRecoveryMigrationPayload(
    val v: Int = 1,
    val uid: String,
    val keyId: String,
    val privateKey: String,
    val userKey: String? = null,
) {
    fun toJsonBytes(): ByteArray {
        val json = org.json.JSONObject()
            .put("v", v)
            .put("uid", uid)
            .put("keyId", keyId)
            .put("privateKey", privateKey)
        if (userKey != null) json.put("userKey", userKey)
        return json.toString().toByteArray(Charsets.UTF_8)
    }

    companion object {
        fun fromJsonBytes(bytes: ByteArray): ChatRecoveryMigrationPayload? = runCatching {
            val json = org.json.JSONObject(bytes.toString(Charsets.UTF_8))
            val userKey = if (json.has("userKey") && !json.isNull("userKey")) {
                json.getString("userKey").takeIf { it.isNotEmpty() }
            } else {
                null
            }
            ChatRecoveryMigrationPayload(
                v = json.optInt("v", 1),
                uid = json.getString("uid"),
                keyId = json.getString("keyId"),
                privateKey = json.getString("privateKey"),
                userKey = userKey,
            )
        }.getOrNull()
    }
}

/** ≡ iOS Timestamp/Date en `init?(map:)`. */
private fun anyToDate(value: Any?): Date? = when (value) {
    is Timestamp -> value.toDate()
    is Date -> value
    else -> null
}
