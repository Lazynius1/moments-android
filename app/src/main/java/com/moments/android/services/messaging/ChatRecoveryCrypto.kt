package com.moments.android.services.messaging

import com.moments.android.services.messaging.EncryptionService.EncryptionError
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Port de ChatRecoveryCrypto.swift.
 * Deriva claves PBKDF2-HMAC-SHA256 (equivalente a CCKeyDerivationPBKDF en iOS).
 * La clave simétrica real para AES-GCM se obtiene en EncryptionService cuando el port E2E esté completo.
 */
object ChatRecoveryCrypto {
    fun randomSalt(length: Int = 32): ByteArray {
        val salt = ByteArray(length)
        SecureRandom().nextBytes(salt)
        return salt
    }

    fun derivePINKey(
        pin: String,
        salt: ByteArray,
        iterations: Int,
        keyLength: Int,
    ): ByteArray {
        if (pin.isEmpty() || salt.isEmpty() || keyLength <= 0) {
            throw EncryptionError.InvalidInput
        }
        return try {
            val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, keyLength * 8)
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } catch (_: Exception) {
            throw EncryptionError.EncryptionFailed
        }
    }
}
