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
}
