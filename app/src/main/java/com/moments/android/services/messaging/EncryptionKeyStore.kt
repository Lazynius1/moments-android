package com.moments.android.services.messaging

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Almacén de material criptográfico cifrado con una clave maestra en Android Keystore.
 * Equivalente honesto a Keychain (iOS) para claves simétricas e identidad de chat.
 */
internal object EncryptionKeyStore {
    private const val PREFS = "moments_encryption_keystore"
    private const val MASTER_ALIAS = "moments_master_key_v1"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val GCM_TAG_BITS = 128

    @Volatile private var appContext: Context? = null

    fun initialize(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
    }

    fun store(tag: String, data: ByteArray) {
        val master = getOrCreateMasterKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, master)
        val blob = cipher.iv + cipher.doFinal(data)
        prefs().edit().putString(tag, Base64.getEncoder().encodeToString(blob)).apply()
    }

    fun retrieve(tag: String): ByteArray? {
        val encoded = prefs().getString(tag, null) ?: return null
        val blob = runCatching { Base64.getDecoder().decode(encoded) }.getOrNull() ?: return null
        if (blob.size < 12 + 16) return null
        return runCatching {
            val nonce = blob.copyOfRange(0, 12)
            val ciphertext = blob.copyOfRange(12, blob.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateMasterKey(), GCMParameterSpec(GCM_TAG_BITS, nonce))
            cipher.doFinal(ciphertext)
        }.getOrNull()
    }

    fun delete(tag: String) {
        prefs().edit().remove(tag).apply()
    }

    fun contains(tag: String): Boolean = prefs().contains(tag)

    /** Round-trip test for health checks (paridad keychain accessibility iOS). */
    fun selfTest(): Boolean = runCatching {
        val tag = "health_check_keystore"
        val sample = byteArrayOf(0x01, 0x02, 0x03)
        store(tag, sample)
        val ok = retrieve(tag)?.contentEquals(sample) == true
        delete(tag)
        ok
    }.getOrDefault(false)

    private fun prefs() =
        (appContext ?: error("EncryptionKeyStore.initialize required"))
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun getOrCreateMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(MASTER_ALIAS)) {
            val spec = KeyGenParameterSpec.Builder(
                MASTER_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .build()
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply {
                init(spec)
                generateKey()
            }
        }
        return (keyStore.getEntry(MASTER_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }
}
