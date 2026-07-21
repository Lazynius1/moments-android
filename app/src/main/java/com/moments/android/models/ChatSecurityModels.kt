package com.moments.android.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import java.util.Date

// MARK: - Identidad de chat (clave pública E2E)
data class ChatIdentityRecord(
    val keyId: String,
    val publicKeyBase64: String,
    val algorithm: String = "curve25519",
    val updatedAt: Date? = null,
) {
    fun asFirestoreData(): Map<String, Any> = mapOf(
        "keyId" to keyId,
        "publicKeyBase64" to publicKeyBase64,
        "algorithm" to algorithm,
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    companion object {
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

// MARK: - Parámetros KDF de recuperación
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
            val iterations = (map["iterations"] as? Number)?.toInt() ?: return null
            val keyLength = (map["keyLength"] as? Number)?.toInt() ?: return null
            val hash = map["hash"] as? String ?: return null
            return ChatRecoveryKDFParams(iterations, keyLength, hash)
        }
    }
}

// MARK: - Bundle de recuperación de clave privada cifrada
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
            val kdfParams = (map["kdfParams"] as? Map<String, Any?>)?.let(ChatRecoveryKDFParams::from) ?: return null
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

// MARK: - Estado de intentos de recuperación (bloqueo)
data class ChatRecoveryAttemptState(
    val failedAttempts: Int = 0,
    val maxAttempts: Int = 5,
    val lockedUntil: Date? = null,
) {
    val isLocked: Boolean get() = lockedUntil?.let { it.time - System.currentTimeMillis() > 0 } ?: false

    /** Segundos restantes de bloqueo. */
    val remainingLockout: Double
        get() = lockedUntil?.let { maxOf(0.0, (it.time - System.currentTimeMillis()) / 1000.0) } ?: 0.0

    val remainingLockoutInterval: Double? get() = if (isLocked) remainingLockout else null

    val remainingAttempts: Int get() = maxOf(0, maxAttempts - failedAttempts)
}

// MARK: - Clave de conversación envuelta
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

// MARK: - Estado de acceso al chat cifrado
sealed interface ChatAccessState {
    data object Available : ChatAccessState
    data object NeedsPinSetup : ChatAccessState
    data object NeedsRestore : ChatAccessState
    data class Unavailable(val reason: String) : ChatAccessState
}

private fun anyToDate(value: Any?): Date? = when (value) {
    is Timestamp -> value.toDate()
    is Date -> value
    else -> null
}
