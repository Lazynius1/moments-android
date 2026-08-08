package com.moments.android.services.messaging

import com.moments.android.services.messaging.EncryptionService.EncryptionError
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Port de `ChatRecoveryCrypto.swift`.
 * PBKDF2-HMAC-SHA256 ≡ `CCKeyDerivationPBKDF` (kCCPBKDF2 + kCCPRFHmacAlgSHA256).
 * La clave se usa como AES-GCM en EncryptionService (bundle `users/{uid}/chatRecovery`).
 *
 * La extensión iOS `AES.GCM.Nonce.dataRepresentation` vive en el call site Android
 * (`aesGcmSeal` / primeros 12 bytes del combined).
 */
object ChatRecoveryCrypto {
    fun randomSalt(length: Int = 32): ByteArray {
        val salt = ByteArray(length)
        SecureRandom().nextBytes(salt)
        return salt
    }

    fun randomBytes(count: Int): ByteArray {
        val bytes = ByteArray(count)
        SecureRandom().nextBytes(bytes)
        return bytes
    }

    /**
     * Equivalente a `derivePINKey` → `SymmetricKey`. Devuelve bytes crudos (mismo material).
     * PIN de recuperación es siempre 6 dígitos ASCII → UTF-8 ≡ `PBEKeySpec` chars.
     */
    fun derivePINKey(
        pin: String,
        salt: ByteArray,
        iterations: Int,
        keyLength: Int,
    ): ByteArray {
        // iOS: invalidInput solo si falla `pin.data(using: .utf8)` — en JVM el String siempre es válido.
        return try {
            val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, keyLength * 8)
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
                ?: throw EncryptionError.EncryptionFailed
        } catch (e: EncryptionError) {
            throw e
        } catch (_: Exception) {
            throw EncryptionError.EncryptionFailed
        }
    }

    fun base64URLEncoded(data: ByteArray): String =
        android.util.Base64.encodeToString(data, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
            .trimEnd('=')

    fun base64URLDecoded(string: String): ByteArray? {
        var base64 = string.replace('-', '+').replace('_', '/')
        val remainder = base64.length % 4
        if (remainder > 0) {
            base64 += "=".repeat(4 - remainder)
        }
        return runCatching {
            android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
        }.getOrNull()
    }
}
