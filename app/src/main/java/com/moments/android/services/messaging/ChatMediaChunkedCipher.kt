package com.moments.android.services.messaging

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

/**
 * Port de ChatMediaChunkedCipher.swift — formato MCHAT02 con AES-GCM por bloque.
 * Requiere la clave simétrica de conversación (proporcionada por EncryptionService cuando E2E esté portado).
 */
object ChatMediaChunkedCipher {
    const val METADATA_VERSION = "2.0"
    const val ALGORITHM = "AES.GCM.CHUNKED+HKDF-SHA256"

    private val MAGIC = byteArrayOf(0x4D, 0x43, 0x48, 0x41, 0x54, 0x30, 0x32, 0x00) // MCHAT02\0
    private const val DEFAULT_CHUNK_SIZE = 1_048_576
    private const val MAXIMUM_CHUNK_SIZE = 4_194_304
    private const val SEALED_OVERHEAD = 28 // nonce (12) + tag (16)
    private const val GCM_TAG_BITS = 128

    class CipherFormatException(message: String) : Exception(message)

    fun encryptFile(
        inputFile: File,
        outputFile: File,
        key: ByteArray,
        authenticatedData: ByteArray,
        chunkSize: Int = DEFAULT_CHUNK_SIZE,
    ): Long {
        require(key.size in listOf(16, 24, 32)) { "Invalid AES key length" }
        require(chunkSize in (64 * 1024)..MAXIMUM_CHUNK_SIZE) { "Invalid chunk size" }

        val plaintextSize = inputFile.length()
        prepareOutputFile(outputFile)

        FileInputStream(inputFile).use { input ->
            FileOutputStream(outputFile).use { output ->
                output.write(MAGIC)
                output.write(encodedUInt32(chunkSize))
                output.write(encodedUInt64(plaintextSize))

                var chunkIndex = 0L
                val buffer = ByteArray(chunkSize)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    val chunk = if (read == buffer.size) buffer else buffer.copyOf(read)
                    val sealed = sealChunk(chunk, key, chunkAuthenticatedData(authenticatedData, chunkIndex))
                    output.write(encodedUInt32(sealed.size))
                    output.write(sealed)
                    chunkIndex++
                }
                output.fd.sync()
            }
        }
        return plaintextSize
    }

    fun decryptFile(
        inputFile: File,
        outputFile: File,
        key: ByteArray,
        authenticatedData: ByteArray,
        expectedPlaintextSize: Long,
    ) {
        require(key.size in listOf(16, 24, 32)) { "Invalid AES key length" }
        prepareOutputFile(outputFile)

        FileInputStream(inputFile).use { input ->
            FileOutputStream(outputFile).use { output ->
                val magic = readExactly(input, MAGIC.size)
                if (!magic.contentEquals(MAGIC)) throw CipherFormatException("Bad magic")

                val chunkSize = decodeUInt32(readExactly(input, 4))
                val declaredSize = decodeUInt64(readExactly(input, 8)).toLong()
                if (chunkSize !in (64 * 1024)..MAXIMUM_CHUNK_SIZE || declaredSize < 0 || declaredSize != expectedPlaintextSize) {
                    throw CipherFormatException("Header mismatch")
                }

                var written = 0L
                var chunkIndex = 0L
                while (true) {
                    val lengthData = readUpTo(input, 4)
                    if (lengthData.isEmpty()) break
                    if (lengthData.size != 4) throw CipherFormatException("Truncated length")

                    val sealedLength = decodeUInt32(lengthData)
                    if (sealedLength < SEALED_OVERHEAD || sealedLength > chunkSize + SEALED_OVERHEAD) {
                        throw CipherFormatException("Invalid sealed length")
                    }

                    val sealedData = readExactly(input, sealedLength)
                    val plaintext = openChunk(
                        sealedData,
                        key,
                        chunkAuthenticatedData(authenticatedData, chunkIndex),
                    )
                    written += plaintext.size
                    if (written > expectedPlaintextSize) throw CipherFormatException("Overflow")
                    output.write(plaintext)
                    chunkIndex++
                }

                if (written != expectedPlaintextSize) throw CipherFormatException("Size mismatch")
                output.fd.sync()
            }
        }
    }

    private fun sealChunk(plaintext: ByteArray, key: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, javax.crypto.spec.SecretKeySpec(key, "AES"))
        cipher.updateAAD(aad)
        val ciphertextAndTag = cipher.doFinal(plaintext)
        val nonce = cipher.iv
        return nonce + ciphertextAndTag
    }

    private fun openChunk(combined: ByteArray, key: ByteArray, aad: ByteArray): ByteArray {
        if (combined.size < 12 + 16) throw CipherFormatException("Sealed box too short")
        val nonce = combined.copyOfRange(0, 12)
        val ciphertextAndTag = combined.copyOfRange(12, combined.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_BITS, nonce)
        cipher.init(Cipher.DECRYPT_MODE, javax.crypto.spec.SecretKeySpec(key, "AES"), spec)
        cipher.updateAAD(aad)
        return cipher.doFinal(ciphertextAndTag)
    }

    private fun chunkAuthenticatedData(base: ByteArray, index: Long): ByteArray =
        base + encodedUInt64(index)

    private fun prepareOutputFile(file: File) {
        file.parentFile?.mkdirs()
        if (file.exists()) file.delete()
        if (!file.createNewFile()) throw CipherFormatException("Cannot create output")
    }

    private fun readExactly(input: FileInputStream, count: Int): ByteArray {
        val result = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val read = input.read(result, offset, count - offset)
            if (read <= 0) throw CipherFormatException("Unexpected EOF")
            offset += read
        }
        return result
    }

    private fun readUpTo(input: FileInputStream, count: Int): ByteArray {
        val result = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val read = input.read(result, offset, count - offset)
            if (read <= 0) break
            offset += read
        }
        return if (offset == 0) ByteArray(0) else result.copyOf(offset)
    }

    private fun encodedUInt32(value: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(value).array()

    private fun encodedUInt64(value: Long): ByteArray =
        ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(value).array()

    private fun decodeUInt32(data: ByteArray): Int {
        if (data.size != 4) throw CipherFormatException("Bad uint32")
        return ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN).int
    }

    private fun decodeUInt64(data: ByteArray): Long {
        if (data.size != 8) throw CipherFormatException("Bad uint64")
        return ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN).long
    }
}
