package com.moments.android.services.messaging

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * HKDF-SHA256 + AES-GCM compatible con CryptoKit (combined = nonce 12 + ciphertext + tag 16).
 */
internal object CryptoHelpers {
    private const val GCM_TAG_BITS = 128
    private const val GCM_NONCE_BYTES = 12
    private const val GCM_TAG_BYTES = 16

    fun randomBytes(count: Int): ByteArray {
        val bytes = ByteArray(count)
        SecureRandom().nextBytes(bytes)
        return bytes
    }

    /** RFC 5869 HKDF-SHA256 (extract + expand), compatible con CryptoKit HKDF<SHA256>. */
    fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length > 0)
        val prk = hmacSha256(if (salt.isEmpty()) ByteArray(32) else salt, ikm)
        val okm = ByteArray(length)
        var offset = 0
        var t = ByteArray(0)
        var counter = 1
        while (offset < length) {
            val input = t + info + byteArrayOf(counter.toByte())
            t = hmacSha256(prk, input)
            val copyLen = minOf(t.size, length - offset)
            System.arraycopy(t, 0, okm, offset, copyLen)
            offset += copyLen
            counter++
        }
        return okm
    }

    fun aesGcmSeal(plaintext: ByteArray, key: ByteArray, aad: ByteArray? = null): ByteArray {
        require(key.size in listOf(16, 24, 32)) { "Invalid AES key length" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        aad?.let { cipher.updateAAD(it) }
        val ciphertextAndTag = cipher.doFinal(plaintext)
        return cipher.iv + ciphertextAndTag
    }

    fun aesGcmOpen(combined: ByteArray, key: ByteArray, aad: ByteArray? = null): ByteArray {
        require(key.size in listOf(16, 24, 32)) { "Invalid AES key length" }
        if (combined.size < GCM_NONCE_BYTES + GCM_TAG_BYTES) {
            throw EncryptionService.EncryptionError.DecryptionFailed
        }
        val nonce = combined.copyOfRange(0, GCM_NONCE_BYTES)
        val ciphertextAndTag = combined.copyOfRange(GCM_NONCE_BYTES, combined.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, nonce),
        )
        aad?.let { cipher.updateAAD(it) }
        return try {
            cipher.doFinal(ciphertextAndTag)
        } catch (_: Exception) {
            throw EncryptionService.EncryptionError.DecryptionFailed
        }
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }
}
