package com.moments.android.services.messaging

import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import java.security.SecureRandom

/**
 * Curve25519 / X25519 alineado con CryptoKit `Curve25519.KeyAgreement` de iOS.
 */
internal object Curve25519Helper {
    const val KEY_BYTES = 32

    data class KeyPair(val privateKey: ByteArray, val publicKey: ByteArray)

    fun generateKeyPair(): KeyPair {
        val gen = X25519KeyPairGenerator()
        gen.init(X25519KeyGenerationParameters(SecureRandom()))
        val pair = gen.generateKeyPair()
        val priv = pair.private as X25519PrivateKeyParameters
        val pub = pair.public as X25519PublicKeyParameters
        return KeyPair(priv.encoded, pub.encoded)
    }

    fun publicKeyFromPrivate(privateKey: ByteArray): ByteArray {
        require(privateKey.size == KEY_BYTES)
        return X25519PrivateKeyParameters(privateKey, 0).generatePublicKey().encoded
    }

    /** Shared secret raw (32 bytes) — CryptoKit luego aplica HKDF. */
    fun sharedSecret(privateKey: ByteArray, peerPublicKey: ByteArray): ByteArray {
        require(privateKey.size == KEY_BYTES && peerPublicKey.size == KEY_BYTES)
        val agreement = X25519Agreement()
        agreement.init(X25519PrivateKeyParameters(privateKey, 0))
        val out = ByteArray(agreement.agreementSize)
        agreement.calculateAgreement(X25519PublicKeyParameters(peerPublicKey, 0), out, 0)
        return out
    }
}
