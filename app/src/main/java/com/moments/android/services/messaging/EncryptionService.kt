package com.moments.android.services.messaging

import android.content.Context
import android.util.Base64
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.moments.android.views.messaging.core.ChatMediaPurpose
import com.moments.android.models.ChatAccessState
import com.moments.android.models.ChatIdentityRecord
import com.moments.android.models.ChatRecoveryAttemptState
import com.moments.android.models.ChatRecoveryBundle
import com.moments.android.models.ChatRecoveryMigrationPayload
import com.moments.android.models.ChatRecoveryMigrationSession
import com.google.firebase.Timestamp
import android.net.Uri
import com.google.firebase.firestore.FirebaseFirestoreException
import com.moments.android.views.messaging.core.EncryptedChatMediaMetadata
import com.moments.android.models.WrappedConversationKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Port de `EncryptionService.swift` (contrato crypto + identidad/recuperación + media/Nova).
 * AES-GCM / HKDF / PBKDF2 / X25519 viven en helpers de plataforma
 * (`CryptoHelpers`, `Curve25519Helper`, `EncryptionKeyStore`, `ChatRecoveryCrypto`).
 */
object EncryptionService {

    sealed class EncryptionError(message: String) : Exception(message) {
        object InvalidInput : EncryptionError("invalid input")
        object EncryptionFailed : EncryptionError("encryption failed")
        object DecryptionFailed : EncryptionError("decryption failed")
        object KeyNotFound : EncryptionError("key not found")
        object InvalidPIN : EncryptionError("invalid PIN")
        object PeerKeyUnavailable : EncryptionError("peer key unavailable")
        data class RecoveryLocked(val remainingSeconds: Double) : EncryptionError("recovery locked")
        object MigrationExpired : EncryptionError("migration expired") {
            override val message: String
                get() = appContext?.getString(com.moments.android.R.string.chat_recovery_error_migration_expired)
                    ?: "migration expired"
        }
        object MigrationConsumed : EncryptionError("migration consumed") {
            override val message: String
                get() = appContext?.getString(com.moments.android.R.string.chat_recovery_error_migration_consumed)
                    ?: "migration consumed"
        }
        object MigrationInvalid : EncryptionError("migration invalid") {
            override val message: String
                get() = appContext?.getString(com.moments.android.R.string.chat_recovery_error_migration_invalid)
                    ?: "migration invalid"
        }
    }

    enum class EncryptionStatus {
        READY,
        NEEDS_PIN_SETUP,
        NEEDS_RESTORE,
        UNAVAILABLE,
    }

    private const val USER_KEYS_PREFIX = "user_key_v2_"
    private const val CONVERSATION_KEYS_PREFIX = "conversation_key_v2_"
    private const val CHAT_IDENTITY_KEY_PREFIX = "chat_identity_private_v1_"
    private const val CHAT_IDENTITY_KEY_ID_PREFIX = "chat_identity_key_id_v1_"
    private const val CHAT_RECOVERY_MARKER_PREFIX = "chat_recovery_marker_v1_"
    private const val CHAT_RECOVERY_ATTEMPTS_PREFIX = "chat_recovery_attempts_v1_"
    private const val CHAT_RECOVERY_LOCKOUT_PREFIX = "chat_recovery_lockout_v1_"
    private const val CHAT_IDENTITY_PREFS = "moments_chat_identity"
    private const val CHAT_RECOVERY_MAX_ATTEMPTS = 5
    private const val CHAT_RECOVERY_LOCKOUT_MS = 5 * 60 * 1000L
    private const val NOVA_BLOB_SALT = "moments.nova.blob.salt.v1"
    private const val CHAT_MEDIA_SALT = "moments.chat.media.salt.v1"
    private const val CHAT_WRAP_INFO = "moments.chat.wrap.v1"
    private const val CURVE25519_PRIVATE_KEY_BYTES = 32
    // Firestore paths (mismos strings literales que iOS)
    private const val USERS_COLLECTION = "users"
    private const val CHAT_KEY_FIELD = "chatKey"
    private const val CHAT_RECOVERY_COLLECTION = "chatRecovery"
    private const val CHAT_RECOVERY_DOC = "default"
    private const val CHAT_RECOVERY_MIGRATIONS_COLLECTION = "chatRecoveryMigrations"

    @Volatile private var initialized = false
    @Volatile private var appContext: Context? = null
    private val userKeyCache = ConcurrentHashMap<String, ByteArray>()
    private val conversationKeyCache = ConcurrentHashMap<String, ByteArray>()
    private val conversationKeyMutex = Mutex()
    private val db get() = FirebaseFirestore.getInstance()

    var isEncryptionEnabled: Boolean = true
    var encryptionStatus: EncryptionStatus = EncryptionStatus.READY

    fun initialize(context: Context) {
        appContext = context.applicationContext
        if (!initialized) {
            EncryptionKeyStore.initialize(context)
            initialized = true
        }
    }

    private fun requireInitialized() {
        if (!initialized) error("EncryptionService.initialize required")
    }

    // MARK: - Nova blobs (REAL)

    suspend fun encryptNovaBlob(data: ByteArray, userId: String, purpose: String): ByteArray =
        withContext(Dispatchers.Default) {
            requireInitialized()
            if (!isEncryptionEnabled || userId.isEmpty() || purpose.isEmpty()) {
                throw EncryptionError.InvalidInput
            }
            val userKey = getUserKey(userId)
            val blobKey = deriveNovaBlobKey(userKey, userId, purpose)
            val aad = novaBlobAuthenticatedData(userId, purpose)
            CryptoHelpers.aesGcmSeal(data, blobKey, aad)
        }

    suspend fun decryptNovaBlob(encryptedData: ByteArray, userId: String, purpose: String): ByteArray =
        withContext(Dispatchers.Default) {
            requireInitialized()
            if (!isEncryptionEnabled || userId.isEmpty() || purpose.isEmpty()) {
                throw EncryptionError.InvalidInput
            }
            val userKey = getUserKey(userId)
            val blobKey = deriveNovaBlobKey(userKey, userId, purpose)
            val aad = novaBlobAuthenticatedData(userId, purpose)
            CryptoHelpers.aesGcmOpen(encryptedData, blobKey, aad)
        }

    /** Text counterpart of iOS `encryptNovaData`: AES-GCM combined bytes, Base64 encoded. */
    suspend fun encryptNovaData(text: String, userId: String): String = withContext(Dispatchers.Default) {
        if (!isEncryptionEnabled) return@withContext text
        runCatching {
            val encrypted = CryptoHelpers.aesGcmSeal(text.toByteArray(Charsets.UTF_8), getUserKey(userId))
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
        }.getOrDefault(text)
    }

    /** Text counterpart of iOS `decryptNovaData`, retaining plaintext legacy fallback on failure. */
    suspend fun decryptNovaData(encryptedText: String, userId: String): String = withContext(Dispatchers.Default) {
        if (!isEncryptionEnabled) return@withContext encryptedText
        runCatching {
            val bytes = Base64.decode(encryptedText, Base64.DEFAULT)
            CryptoHelpers.aesGcmOpen(bytes, getUserKey(userId)).toString(Charsets.UTF_8)
        }.getOrDefault(encryptedText)
    }

    // MARK: - Conversation keys

    suspend fun preloadConversationKeys(conversationIds: List<String>) {
        if (conversationIds.isEmpty() || !isEncryptionEnabled) return
        requireInitialized()
        coroutineScope {
            conversationIds.map { id ->
                async {
                    runCatching { getConversationKey(id) }
                }
            }.awaitAll()
        }
    }

    // MARK: - Chat media (REAL cuando hay clave de conversación)

    suspend fun decryptChatMediaFile(
        inputFile: File,
        outputFile: File,
        conversationId: String,
        metadata: EncryptedChatMediaMetadata,
        messageId: String,
    ) = withContext(Dispatchers.IO) {
        requireInitialized()
        if (metadata.version != ChatMediaChunkedCipher.METADATA_VERSION ||
            metadata.algorithm != ChatMediaChunkedCipher.ALGORITHM
        ) {
            throw EncryptionError.DecryptionFailed
        }
        val convKey = getConversationKey(conversationId)
        val mediaKey = deriveChatMediaKey(convKey, conversationId, messageId, metadata.purpose)
        val aad = mediaAuthenticatedData(
            conversationId,
            messageId,
            metadata.purpose,
            metadata.contentType,
        )
        ChatMediaChunkedCipher.decryptFile(
            inputFile = inputFile,
            outputFile = outputFile,
            key = mediaKey,
            authenticatedData = aad,
            expectedPlaintextSize = metadata.plaintextSize,
        )
    }

    suspend fun decryptChatMedia(
        encryptedData: ByteArray,
        metadata: EncryptedChatMediaMetadata,
        conversationId: String,
        messageId: String,
    ): ByteArray = withContext(Dispatchers.Default) {
        requireInitialized()
        val convKey = getConversationKey(conversationId)
        val mediaKey = deriveChatMediaKey(convKey, conversationId, messageId, metadata.purpose)
        val aad = mediaAuthenticatedData(
            conversationId,
            messageId,
            metadata.purpose,
            metadata.contentType,
        )
        runCatching {
            CryptoHelpers.aesGcmOpen(encryptedData, mediaKey, aad).also {
                metrics.successfulDecryptions++
            }
        }.getOrElse {
            recordError("decryptChatMedia: ${it.message}")
            throw EncryptionError.DecryptionFailed
        }
    }

    data class EncryptedChatMediaResult(
        val ciphertext: ByteArray,
        val metadata: EncryptedChatMediaMetadata,
    )

    data class EncryptedChatMediaFileResult(
        val ciphertextFile: File,
        val metadata: EncryptedChatMediaMetadata,
    )

    suspend fun encryptChatMedia(
        data: ByteArray,
        conversationId: String,
        messageId: String,
        purpose: ChatMediaPurpose,
        contentType: String,
        fileExtension: String,
    ): EncryptedChatMediaResult = withContext(Dispatchers.Default) {
        requireInitialized()
        val convKey = getConversationKey(conversationId)
        val mediaKey = deriveChatMediaKey(convKey, conversationId, messageId, purpose)
        val aad = mediaAuthenticatedData(conversationId, messageId, purpose, contentType)
        val sealed = CryptoHelpers.aesGcmSeal(data, mediaKey, aad)
        EncryptedChatMediaResult(
            ciphertext = sealed,
            metadata = EncryptedChatMediaMetadata(
                purpose = purpose,
                mediaId = messageId,
                contentType = contentType,
                fileExtension = fileExtension,
                plaintextSize = data.size.toLong(),
            ),
        )
    }

    suspend fun encryptChatMediaFile(
        inputFile: File,
        conversationId: String,
        messageId: String,
        purpose: ChatMediaPurpose,
        contentType: String,
        fileExtension: String,
    ): EncryptedChatMediaFileResult = withContext(Dispatchers.IO) {
        requireInitialized()
        val convKey = getConversationKey(conversationId)
        val mediaKey = deriveChatMediaKey(convKey, conversationId, messageId, purpose)
        val aad = mediaAuthenticatedData(conversationId, messageId, purpose, contentType)
        val outputFile = File.createTempFile("chat-encrypted-", ".bin")
        try {
            val plaintextSize = ChatMediaChunkedCipher.encryptFile(
                inputFile = inputFile,
                outputFile = outputFile,
                key = mediaKey,
                authenticatedData = aad,
            )
            EncryptedChatMediaFileResult(
                ciphertextFile = outputFile,
                metadata = EncryptedChatMediaMetadata(
                    version = ChatMediaChunkedCipher.METADATA_VERSION,
                    algorithm = ChatMediaChunkedCipher.ALGORITHM,
                    purpose = purpose,
                    mediaId = messageId,
                    contentType = contentType,
                    fileExtension = fileExtension,
                    plaintextSize = plaintextSize,
                ),
            )
        } catch (e: Exception) {
            outputFile.delete()
            throw e
        }
    }

    // MARK: - Chat identity and recovery (same Firestore contract as iOS)

    suspend fun chatAccessState(): ChatAccessState = withContext(Dispatchers.IO) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
            ?: return@withContext ChatAccessState.Unavailable(
                appContext?.getString(com.moments.android.R.string.messaging_error_not_authenticated)
                    ?: "User not authenticated",
            )
        try {
            val hasLocalIdentity = hasLocalChatIdentity(userId)
            val hasRecoveryBundle = hasChatRecoveryBundle(userId)
            val markerKey = CHAT_RECOVERY_MARKER_PREFIX + userId
            val hasRecoveryMarker = identityPrefs().getBoolean(markerKey, false)

            if (hasLocalIdentity && hasRecoveryBundle && !hasRecoveryMarker) {
                deleteLocalChatIdentity(userId)
                identityPrefs().edit().putBoolean(markerKey, true).apply()
                if (tryRestoreFromDeviceVault(userId)) {
                    return@withContext ChatAccessState.Available
                }
                return@withContext ChatAccessState.NeedsRestore
            }
            if (hasLocalIdentity) {
                if (hasRecoveryBundle) {
                    identityPrefs().edit().putBoolean(markerKey, true).apply()
                    ChatAccessState.Available
                } else {
                    ChatAccessState.NeedsPinSetup
                }
            } else if (hasRecoveryBundle) {
                identityPrefs().edit().putBoolean(markerKey, true).apply()
                if (tryRestoreFromDeviceVault(userId)) {
                    ChatAccessState.Available
                } else {
                    ChatAccessState.NeedsRestore
                }
            } else {
                ensureChatIdentity()
                ChatAccessState.NeedsPinSetup
            }
        } catch (error: Exception) {
            ChatAccessState.Unavailable(error.message ?: "Chat identity unavailable")
        }
    }

    suspend fun ensureChatIdentity(): ChatIdentityRecord = withContext(Dispatchers.IO) {
        requireInitialized()
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: throw EncryptionError.KeyNotFound
        val identity = loadOrCreateLocalChatIdentity(userId)
        syncChatIdentityRecord(identity, userId)
        identity
    }

    suspend fun hasChatRecoveryBundle(userId: String): Boolean = withContext(Dispatchers.IO) {
        db.collection(USERS_COLLECTION)
            .document(requireUserId(userId))
            .collection(CHAT_RECOVERY_COLLECTION)
            .document(CHAT_RECOVERY_DOC)
            .get()
            .await()
            .exists()
    }

    fun chatRecoveryAttemptState(userId: String? = FirebaseAuth.getInstance().currentUser?.uid): ChatRecoveryAttemptState {
        if (userId.isNullOrBlank()) return ChatRecoveryAttemptState(maxAttempts = CHAT_RECOVERY_MAX_ATTEMPTS)
        return currentRecoveryAttemptState(userId)
    }

    /** Port de `chatRecoveryLockoutMessage(for:)`. */
    fun chatRecoveryLockoutMessage(state: ChatRecoveryAttemptState): String? {
        val remaining = state.remainingLockoutInterval ?: return null
        val ctx = appContext ?: return null
        return ctx.getString(
            com.moments.android.R.string.chat_recovery_error_locked_timer,
            formatRecoveryLockoutDuration(remaining),
        )
    }

    private fun formatRecoveryLockoutDuration(intervalSeconds: Double): String {
        val totalSeconds = maxOf(1, kotlin.math.ceil(intervalSeconds).toInt())
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }

    suspend fun createRecoveryBundle(pin: String, context: Context? = appContext) = withContext(Dispatchers.IO) {
        requireInitialized()
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: throw EncryptionError.KeyNotFound
        val normalizedPin = validateRecoveryPin(pin)
        val identity = loadOrCreateLocalChatIdentity(userId)
        val kdfParams = com.moments.android.models.ChatRecoveryKDFParams()
        val salt = ChatRecoveryCrypto.randomSalt()
        val pinKey = ChatRecoveryCrypto.derivePINKey(
            pin = normalizedPin,
            salt = salt,
            iterations = kdfParams.iterations,
            keyLength = kdfParams.keyLength,
        )
        val privateKey = EncryptionKeyStore.retrieve(CHAT_IDENTITY_KEY_PREFIX + userId)
            ?: throw EncryptionError.KeyNotFound
        val encryptedPrivateKey = CryptoHelpers.aesGcmSeal(privateKey, pinKey)
        val userKey = getUserKey(userId)
        val encryptedUserKey = CryptoHelpers.aesGcmSeal(userKey, pinKey)
        val bundle = ChatRecoveryBundle(
            keyId = identity.keyId,
            encryptedPrivateKey = Base64.encodeToString(encryptedPrivateKey, Base64.NO_WRAP),
            nonce = Base64.encodeToString(encryptedPrivateKey.copyOfRange(0, 12), Base64.NO_WRAP),
            salt = Base64.encodeToString(salt, Base64.NO_WRAP),
            kdfParams = kdfParams,
            encryptedUserKey = Base64.encodeToString(encryptedUserKey, Base64.NO_WRAP),
        )
        db.collection(USERS_COLLECTION)
            .document(userId)
            .collection(CHAT_RECOVERY_COLLECTION)
            .document(CHAT_RECOVERY_DOC)
            .set(bundle.asFirestoreData(), com.google.firebase.firestore.SetOptions.merge())
            .await()
        syncChatIdentityRecord(identity, userId)
        identityPrefs().edit().putBoolean(CHAT_RECOVERY_MARKER_PREFIX + userId, true).apply()
        clearRecoveryAttemptState(userId)
        ChatRecoveryDeviceVault.savePIN(userId, normalizedPin, context ?: appContext)
    }

    suspend fun restoreChatIdentity(pin: String, context: Context? = appContext) = withContext(Dispatchers.IO) {
        restoreChatIdentity(
            pin = pin,
            registerFailures = true,
            saveToVault = true,
            context = context ?: appContext,
        )
    }

    /** Silent OS-vault restore (Keystore + Google Password Manager when Activity available). */
    suspend fun tryRestoreFromDeviceVault(
        userId: String? = FirebaseAuth.getInstance().currentUser?.uid,
        context: Context? = appContext,
    ): Boolean = withContext(Dispatchers.IO) {
        if (userId.isNullOrBlank()) return@withContext false
        val pin = ChatRecoveryDeviceVault.loadPIN(userId, context ?: appContext) ?: return@withContext false
        runCatching {
            restoreChatIdentity(pin = pin, registerFailures = false, saveToVault = false, context = null)
            MessageIngestService.resetAfterIdentityRestore()
            true
        }.getOrDefault(false)
    }

    suspend fun saveRecoveryPINToDeviceVault(pin: String, context: Context? = appContext) = withContext(Dispatchers.IO) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: throw EncryptionError.KeyNotFound
        val normalizedPin = validateRecoveryPin(pin)
        if (!verifyRecoveryPIN(normalizedPin)) throw EncryptionError.InvalidPIN
        ChatRecoveryDeviceVault.savePIN(userId, normalizedPin, context ?: appContext)
    }

    fun clearRecoveryPINFromDeviceVault() {
        ChatRecoveryDeviceVault.clearCurrentUser()
    }

    /** Wipe local identity and force a new PIN. Old ciphertext stays unreadable. */
    suspend fun resetRecoveryLosingHistory() = withContext(Dispatchers.IO) {
        requireInitialized()
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: throw EncryptionError.KeyNotFound
        ChatRecoveryDeviceVault.clear(userId)
        deleteLocalChatIdentity(userId)
        deleteUserKeys(userId)
        identityPrefs().edit()
            .remove(CHAT_IDENTITY_KEY_ID_PREFIX + userId)
            .remove(CHAT_RECOVERY_MARKER_PREFIX + userId)
            .apply()
        clearRecoveryAttemptState(userId)
        purgeConversationKeys()
        runCatching {
            db.collection(USERS_COLLECTION)
                .document(userId)
                .collection(CHAT_RECOVERY_COLLECTION)
                .document(CHAT_RECOVERY_DOC)
                .delete()
                .await()
        }
        ensureChatIdentity()
        MessageIngestService.resetAfterIdentityRestore()
    }

    suspend fun beginDeviceMigration(): ChatRecoveryMigrationSession = withContext(Dispatchers.IO) {
        requireInitialized()
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: throw EncryptionError.KeyNotFound
        if (!hasLocalChatIdentity(userId)) throw EncryptionError.KeyNotFound
        val privateKey = EncryptionKeyStore.retrieve(CHAT_IDENTITY_KEY_PREFIX + userId)
            ?: throw EncryptionError.KeyNotFound
        val publicKeyBase64 = Base64.encodeToString(
            Curve25519Helper.publicKeyFromPrivate(privateKey),
            Base64.NO_WRAP,
        )
        val keyId = resolveStableChatKeyId(userId, publicKeyBase64)
        val userKeyBase64 = runCatching {
            Base64.encodeToString(getUserKey(userId), Base64.NO_WRAP)
        }.getOrNull()
        val payload = ChatRecoveryMigrationPayload(
            uid = userId,
            keyId = keyId,
            privateKey = Base64.encodeToString(privateKey, Base64.NO_WRAP),
            userKey = userKeyBase64,
        )
        val migrationKey = ChatRecoveryCrypto.randomBytes(32)
        val wrappedPayload = CryptoHelpers.aesGcmSeal(payload.toJsonBytes(), migrationKey)
        val migrationId = UUID.randomUUID().toString()
        val expiresAt = Date(System.currentTimeMillis() + 10 * 60 * 1000L)
        val qrPayload =
            "moments-migrate://v1?uid=$userId&id=$migrationId&k=${ChatRecoveryCrypto.base64URLEncoded(migrationKey)}"

        db.collection(USERS_COLLECTION)
            .document(userId)
            .collection(CHAT_RECOVERY_MIGRATIONS_COLLECTION)
            .document(migrationId)
            .set(
                mapOf(
                    "migrationId" to migrationId,
                    "keyId" to keyId,
                    "scheme" to "aes-gcm-v1",
                    "wrappedPayload" to Base64.encodeToString(wrappedPayload, Base64.NO_WRAP),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "expiresAt" to Timestamp(expiresAt),
                    "consumedAt" to null,
                ),
            )
            .await()

        ChatRecoveryMigrationSession(
            migrationId = migrationId,
            qrPayload = qrPayload,
            expiresAt = expiresAt,
        )
    }

    suspend fun completeDeviceMigration(qrOrCode: String) = withContext(Dispatchers.IO) {
        requireInitialized()
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: throw EncryptionError.KeyNotFound
        val parsed = parseMigrationPayload(qrOrCode) ?: throw EncryptionError.MigrationInvalid
        if (parsed.uid != userId) throw EncryptionError.MigrationInvalid
        val migrationKey = ChatRecoveryCrypto.base64URLDecoded(parsed.keyBase64URL)
            ?.takeIf { it.size == 32 }
            ?: throw EncryptionError.MigrationInvalid

        val docRef = db.collection(USERS_COLLECTION)
            .document(userId)
            .collection(CHAT_RECOVERY_MIGRATIONS_COLLECTION)
            .document(parsed.migrationId)

        val snapshot = docRef.get().await()
        val data = snapshot.data ?: throw EncryptionError.MigrationInvalid
        if (data["consumedAt"] is Timestamp) throw EncryptionError.MigrationConsumed
        val expiresAt = (data["expiresAt"] as? Timestamp)?.toDate()
        if (expiresAt != null && expiresAt.time <= System.currentTimeMillis()) {
            throw EncryptionError.MigrationExpired
        }
        val wrappedPayload = Base64.decode(
            data["wrappedPayload"] as? String ?: throw EncryptionError.MigrationInvalid,
            Base64.DEFAULT,
        )
        val opened = runCatching {
            CryptoHelpers.aesGcmOpen(wrappedPayload, migrationKey)
        }.getOrElse { throw EncryptionError.MigrationInvalid }
        val payload = ChatRecoveryMigrationPayload.fromJsonBytes(opened)
            ?: throw EncryptionError.MigrationInvalid
        if (payload.uid != userId) throw EncryptionError.MigrationInvalid

        try {
            db.runTransaction { transaction ->
                val latest = transaction.get(docRef)
                val latestData = latest.data ?: throw FirebaseFirestoreException(
                    "invalid",
                    FirebaseFirestoreException.Code.ABORTED,
                )
                if (latestData["consumedAt"] is Timestamp) {
                    throw FirebaseFirestoreException("consumed", FirebaseFirestoreException.Code.ABORTED)
                }
                val latestExpires = (latestData["expiresAt"] as? Timestamp)?.toDate()
                if (latestExpires != null && latestExpires.time <= System.currentTimeMillis()) {
                    throw FirebaseFirestoreException("expired", FirebaseFirestoreException.Code.ABORTED)
                }
                transaction.update(docRef, mapOf("consumedAt" to FieldValue.serverTimestamp()))
                null
            }.await()
        } catch (e: FirebaseFirestoreException) {
            when (e.message) {
                "consumed" -> throw EncryptionError.MigrationConsumed
                "expired" -> throw EncryptionError.MigrationExpired
                else -> throw EncryptionError.MigrationInvalid
            }
        }

        val privateKey = Base64.decode(payload.privateKey, Base64.DEFAULT)
        if (privateKey.size != CURVE25519_PRIVATE_KEY_BYTES) throw EncryptionError.MigrationInvalid
        EncryptionKeyStore.store(CHAT_IDENTITY_KEY_PREFIX + userId, privateKey)
        val publicKeyBase64 = Base64.encodeToString(
            Curve25519Helper.publicKeyFromPrivate(privateKey),
            Base64.NO_WRAP,
        )
        val identity = ChatIdentityRecord(
            keyId = resolveStableChatKeyId(userId, publicKeyBase64, payload.keyId),
            publicKeyBase64 = publicKeyBase64,
        )
        syncChatIdentityRecord(identity, userId)
        payload.userKey?.let { encoded ->
            runCatching {
                val userKey = Base64.decode(encoded, Base64.DEFAULT)
                if (userKey.size == 32) {
                    cacheUserKey(userId, userKey)
                    EncryptionKeyStore.store(USER_KEYS_PREFIX + userId, userKey)
                }
            }
        }
        identityPrefs().edit().putBoolean(CHAT_RECOVERY_MARKER_PREFIX + userId, true).apply()
        clearRecoveryAttemptState(userId)
        purgeConversationKeys()
        MessageIngestService.resetAfterIdentityRestore()
    }

    private data class ParsedMigrationLink(
        val uid: String,
        val migrationId: String,
        val keyBase64URL: String,
    )

    private fun parseMigrationPayload(raw: String): ParsedMigrationLink? {
        val trimmed = raw.trim()
        val uri = Uri.parse(trimmed)
        if (uri.scheme != "moments-migrate") return null
        val uid = uri.getQueryParameter("uid") ?: return null
        val id = uri.getQueryParameter("id") ?: return null
        val key = uri.getQueryParameter("k") ?: return null
        if (uid.isBlank() || id.isBlank() || key.isBlank()) return null
        return ParsedMigrationLink(uid = uid, migrationId = id, keyBase64URL = key)
    }

    private suspend fun restoreChatIdentity(
        pin: String,
        registerFailures: Boolean,
        saveToVault: Boolean,
        context: Context? = appContext,
    ) {
        requireInitialized()
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: throw EncryptionError.KeyNotFound
        val attempts = currentRecoveryAttemptState(userId)
        if (attempts.isLocked) throw EncryptionError.RecoveryLocked(attempts.remainingLockout)
        val normalizedPin = validateRecoveryPin(pin)
        val snapshot = db.collection(USERS_COLLECTION)
            .document(userId)
            .collection(CHAT_RECOVERY_COLLECTION)
            .document(CHAT_RECOVERY_DOC)
            .get()
            .await()
        val bundle = snapshot.data?.let { ChatRecoveryBundle.from(it) } ?: throw EncryptionError.KeyNotFound
        try {
            restoreChatIdentityFromBundle(normalizedPin, bundle, userId)
            identityPrefs().edit().putBoolean(CHAT_RECOVERY_MARKER_PREFIX + userId, true).apply()
            clearRecoveryAttemptState(userId)
            purgeConversationKeys()
            if (saveToVault) {
                ChatRecoveryDeviceVault.savePIN(userId, normalizedPin, context)
            }
        } catch (error: EncryptionError.RecoveryLocked) {
            throw error
        } catch (_: Exception) {
            if (registerFailures) {
                registerFailedRecoveryAttempt(userId)
                val updated = currentRecoveryAttemptState(userId)
                if (updated.isLocked) throw EncryptionError.RecoveryLocked(updated.remainingLockout)
            }
            throw EncryptionError.InvalidPIN
        }
    }

    fun hasLocalChatIdentity(userId: String? = FirebaseAuth.getInstance().currentUser?.uid): Boolean {
        if (userId.isNullOrEmpty()) return false
        return EncryptionKeyStore.contains(CHAT_IDENTITY_KEY_PREFIX + userId)
    }

    /**
     * Tira todas las claves de conversación (memoria + keystore) para que se vuelvan a desenvolver
     * desde `wrappedKeys` con la identidad vigente. Se usa tras restaurar identidad.
     */
    fun purgeConversationKeys() {
        conversationKeyCache.clear()
        EncryptionKeyStore.tagsWithPrefix(CONVERSATION_KEYS_PREFIX).forEach(EncryptionKeyStore::delete)
    }

    // MARK: - Limpieza de claves (paridad `LEGACY SUPPORT & CLEANUP` de iOS)

    /** Port de `deleteUserKeys(for:)`. */
    suspend fun deleteUserKeys(userId: String) = withContext(Dispatchers.IO) {
        userKeyCache.remove(userId)
        EncryptionKeyStore.delete(USER_KEYS_PREFIX + userId)
    }

    /**
     * Port de `deleteAllKeys()` — reset total del material criptográfico de este dispositivo.
     * Necesario al cerrar sesión: si no, las claves del usuario anterior se quedan en el aparato.
     */
    suspend fun deleteAllKeys() = withContext(Dispatchers.IO) {
        userKeyCache.clear()
        conversationKeyCache.clear()
        EncryptionKeyStore.deleteAll()
    }

    /**
     * Port de `verifyRecoveryPIN(_:)` — comprueba si el PIN abre el bundle remoto SIN restaurar
     * la identidad ni consumir intentos.
     */
    suspend fun verifyRecoveryPIN(pin: String): Boolean = withContext(Dispatchers.IO) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@withContext false
        val trimmed = pin.trim()
        if (trimmed.length != 6 || !trimmed.all(Char::isDigit)) return@withContext false
        runCatching {
            val snapshot = db.collection(USERS_COLLECTION)
                .document(userId)
                .collection(CHAT_RECOVERY_COLLECTION)
                .document(CHAT_RECOVERY_DOC)
                .get()
                .await()
            val bundle = snapshot.data?.let { ChatRecoveryBundle.from(it) } ?: return@runCatching false
            val salt = Base64.decode(bundle.salt, Base64.DEFAULT)
            val encryptedPrivateKey = Base64.decode(bundle.encryptedPrivateKey, Base64.DEFAULT)
            val pinKey = ChatRecoveryCrypto.derivePINKey(
                pin = trimmed,
                salt = salt,
                iterations = bundle.kdfParams.iterations,
                keyLength = bundle.kdfParams.keyLength,
            )
            CryptoHelpers.aesGcmOpen(encryptedPrivateKey, pinKey)
            true
        }.getOrDefault(false)
    }

    /**
     * Port de `removeLocalChatIdentity()` — borra la identidad de chat de ESTE dispositivo para
     * forzar la restauración con PIN (opción "forzar restauración" de los ajustes de recuperación).
     * No toca el bundle remoto: sin él la restauración sería imposible.
     */
    suspend fun removeLocalChatIdentity() = withContext(Dispatchers.IO) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: throw EncryptionError.KeyNotFound
        EncryptionKeyStore.delete(CHAT_IDENTITY_KEY_PREFIX + userId)
        identityPrefs().edit()
            .remove(CHAT_IDENTITY_KEY_ID_PREFIX + userId)
            .remove(CHAT_RECOVERY_MARKER_PREFIX + userId)
            .apply()
        clearRecoveryAttemptState(userId)
    }

    // MARK: - Text encrypt/decrypt (REAL con clave de conversación)

    suspend fun encryptChatMessage(text: String, conversationId: String): String {
        requireInitialized()
        if (!isEncryptionEnabled) throw EncryptionError.EncryptionFailed
        val key = getConversationKey(conversationId)
        return encryptText(text, key)
    }

    suspend fun decryptChatMessage(encryptedText: String, conversationId: String): String? {
        if (!isEncryptionEnabled) return encryptedText
        return runCatching {
            requireInitialized()
            val key = getConversationKey(conversationId)
            decryptText(encryptedText, key)
        }.getOrDefault(encryptedText)
    }

    // MARK: - Internals

    private suspend fun getUserKey(userId: String): ByteArray {
        if (userId.isEmpty()) throw EncryptionError.InvalidInput
        userKeyCache[userId]?.let { return it }

        val tag = USER_KEYS_PREFIX + userId
        EncryptionKeyStore.retrieve(tag)?.let { stored ->
            userKeyCache[userId] = stored
            return stored
        }

        val newKey = CryptoHelpers.randomBytes(32)
        EncryptionKeyStore.store(tag, newKey)
        userKeyCache[userId] = newKey
        return newKey
    }

    private fun cacheUserKey(userId: String, key: ByteArray) {
        userKeyCache[userId] = key
    }

    private suspend fun getConversationKey(conversationId: String): ByteArray {
        if (conversationId.isEmpty()) throw EncryptionError.InvalidInput
        conversationKeyCache[conversationId]?.let { return it }

        return conversationKeyMutex.withLock {
            conversationKeyCache[conversationId]?.let { return it }
            val key = loadConversationKeyFromStorage(conversationId)
            conversationKeyCache[conversationId] = key
            EncryptionKeyStore.store(CONVERSATION_KEYS_PREFIX + conversationId, key)
            key
        }
    }

    private suspend fun loadConversationKeyFromStorage(conversationId: String): ByteArray =
        withContext(Dispatchers.IO) {
            val tag = CONVERSATION_KEYS_PREFIX + conversationId
            EncryptionKeyStore.retrieve(tag)?.let { return@withContext it }
            getConversationKeyFromFirestore(conversationId)
        }

    @Suppress("UNCHECKED_CAST")
    private suspend fun getConversationKeyFromFirestore(conversationId: String): ByteArray =
        withContext(Dispatchers.IO) {
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
                ?: throw EncryptionError.KeyNotFound

            val snapshot = db.collection("conversations").document(conversationId).get().await()
            val data = snapshot.data ?: throw EncryptionError.KeyNotFound

            val wrappedKeys = data["wrappedKeys"] as? Map<String, Any>
            val wrappedKeyMap = wrappedKeys?.get(currentUserId) as? Map<String, Any>
            if (wrappedKeyMap != null) {
                val wrapped = WrappedConversationKey.from(wrappedKeyMap)
                    ?: throw EncryptionError.DecryptionFailed
                return@withContext unwrapConversationKey(wrapped, currentUserId)
            }

            (data["sharedEncryptionKey"] as? String)?.let { keyB64 ->
                val keyData = Base64.decode(keyB64, Base64.DEFAULT)
                if (keyData.isNotEmpty()) {
                    upgradeLegacyConversationKeyIfPossible(conversationId, data, keyData, currentUserId)
                    return@withContext keyData
                }
            }

            (data["encryptionKey"] as? String)?.let { keyB64 ->
                val keyData = Base64.decode(keyB64, Base64.DEFAULT)
                if (keyData.isNotEmpty()) {
                    upgradeLegacyConversationKeyIfPossible(conversationId, data, keyData, currentUserId)
                    return@withContext keyData
                }
            }

            val participants = (data["participants"] as? List<*>)?.filterIsInstance<String>().orEmpty()
            if (participants.contains(currentUserId)) {
                return@withContext createAndPublishConversationKey(conversationId, participants, currentUserId)
            }

            throw EncryptionError.KeyNotFound
        }

    /**
     * Port de `upgradeLegacyConversationKeyIfPossible`: migra clave en claro → wrappedKeys
     * y borra campos legacy solo cuando todos los participantes quedan cubiertos.
     */
    @Suppress("UNCHECKED_CAST")
    private suspend fun upgradeLegacyConversationKeyIfPossible(
        conversationId: String,
        conversationData: Map<String, Any>,
        conversationKey: ByteArray,
        currentUserId: String,
    ) {
        val participants = (conversationData["participants"] as? List<*>)?.filterIsInstance<String>().orEmpty()
        if (participants.isEmpty() || currentUserId !in participants) return

        val existingWrappedKeys = (conversationData["wrappedKeys"] as? Map<String, Any>).orEmpty()
        val missingParticipants = participants.filter { existingWrappedKeys[it] == null }

        val newWrappedKeys = if (missingParticipants.isNotEmpty()) {
            val built = runCatching {
                buildWrappedConversationKeys(missingParticipants, conversationKey, currentUserId)
            }.getOrNull() ?: return
            if (built.size != missingParticipants.size) return
            built
        } else {
            emptyMap()
        }

        val upgradeData = mutableMapOf<String, Any>(
            "conversationKeyVersion" to 1,
            "encryptionVersion" to "3.0",
            "sharedEncryptionKey" to FieldValue.delete(),
            "encryptionKey" to FieldValue.delete(),
            "encryptionKeyCreatedAt" to FieldValue.delete(),
            "keyMetadata" to FieldValue.delete(),
        )
        if (newWrappedKeys.isNotEmpty()) {
            upgradeData["wrappedKeys"] = newWrappedKeys
        }

        runCatching {
            db.collection("conversations").document(conversationId)
                .set(upgradeData, com.google.firebase.firestore.SetOptions.merge())
                .await()
        }
    }

    /** Port de `buildWrappedConversationKeys` — omite participantes sin identidad (como iOS). */
    suspend fun buildWrappedConversationKeys(
        participantIds: List<String>,
        conversationKey: ByteArray,
        wrappedBy: String,
    ): Map<String, Map<String, Any>> {
        val wrappedKeys = mutableMapOf<String, Map<String, Any>>()
        for (participantId in participantIds) {
            val identity = fetchChatIdentity(participantId) ?: continue
            val wrapped = wrapConversationKey(
                conversationKey,
                identity.publicKeyBase64,
                identity.keyId,
                wrappedBy,
            )
            wrappedKeys[participantId] = wrapped.asFirestoreData()
        }
        return wrappedKeys
    }

    /**
     * Port de `createNewSharedConversationKey`.
     * Sin fallback en claro: si no se puede wrappear para todos → PeerKeyUnavailable.
     */
    private suspend fun createAndPublishConversationKey(
        conversationId: String,
        participants: List<String>,
        currentUserId: String,
    ): ByteArray {
        val conversationKey = CryptoHelpers.randomBytes(32)
        val wrappedMaps = buildWrappedConversationKeys(participants, conversationKey, currentUserId)
        if (wrappedMaps.size != participants.size) {
            throw EncryptionError.PeerKeyUnavailable
        }

        db.collection("conversations").document(conversationId).set(
            mapOf(
                "wrappedKeys" to wrappedMaps,
                "conversationKeyVersion" to 1,
                "encryptionVersion" to "3.0",
            ),
            com.google.firebase.firestore.SetOptions.merge(),
        ).await()

        return conversationKey
    }

    /** Desenvuelve clave de conversación (paridad unwrapConversationKey iOS). */
    private fun unwrapConversationKey(wrappedKey: WrappedConversationKey, userId: String): ByteArray {
        val privateKeyData = EncryptionKeyStore.retrieve(CHAT_IDENTITY_KEY_PREFIX + userId)
            ?: throw EncryptionError.KeyNotFound
        val senderPublicKey = Base64.decode(wrappedKey.senderPublicKey, Base64.DEFAULT)
        val wrappedKeyData = Base64.decode(wrappedKey.wrappedKey, Base64.DEFAULT)
        if (senderPublicKey.size != CURVE25519_PRIVATE_KEY_BYTES || wrappedKeyData.isEmpty()) {
            throw EncryptionError.InvalidInput
        }
        val shared = Curve25519Helper.sharedSecret(privateKeyData, senderPublicKey)
        val wrappingKey = CryptoHelpers.hkdfSha256(
            ikm = shared,
            salt = ByteArray(0),
            info = CHAT_WRAP_INFO.toByteArray(Charsets.UTF_8),
            length = 32,
        )
        return CryptoHelpers.aesGcmOpen(wrappedKeyData, wrappingKey)
    }

    /** Envuelve clave de conversación para un destinatario (paridad wrapConversationKey iOS). */
    fun wrapConversationKey(
        conversationKeyData: ByteArray,
        recipientPublicKeyBase64: String,
        recipientKeyId: String,
        wrappedBy: String,
    ): WrappedConversationKey {
        val recipientPublicKey = Base64.decode(recipientPublicKeyBase64, Base64.DEFAULT)
        if (recipientPublicKey.size != CURVE25519_PRIVATE_KEY_BYTES) {
            throw EncryptionError.InvalidInput
        }
        val ephemeral = Curve25519Helper.generateKeyPair()
        val shared = Curve25519Helper.sharedSecret(ephemeral.privateKey, recipientPublicKey)
        val wrappingKey = CryptoHelpers.hkdfSha256(
            ikm = shared,
            salt = ByteArray(0),
            info = CHAT_WRAP_INFO.toByteArray(Charsets.UTF_8),
            length = 32,
        )
        val sealed = CryptoHelpers.aesGcmSeal(conversationKeyData, wrappingKey)
        return WrappedConversationKey(
            wrappedKey = Base64.encodeToString(sealed, Base64.NO_WRAP),
            senderPublicKey = Base64.encodeToString(ephemeral.publicKey, Base64.NO_WRAP),
            recipientKeyId = recipientKeyId,
            wrappedBy = wrappedBy,
        )
    }

    /** Crea/lee la clave privada local; [ensureChatIdentity] publica el registro canónico. */
    private fun ensureLocalChatIdentity(userId: String): ByteArray {
        requireInitialized()
        val tag = CHAT_IDENTITY_KEY_PREFIX + userId
        EncryptionKeyStore.retrieve(tag)?.let { return it }
        val pair = Curve25519Helper.generateKeyPair()
        EncryptionKeyStore.store(tag, pair.privateKey)
        return pair.privateKey
    }

    /** Lee `users/{uid}.chatKey` (campo canónico iOS). */
    suspend fun fetchChatIdentity(userId: String): ChatIdentityRecord? = withContext(Dispatchers.IO) {
        val normalizedUserId = requireUserId(userId)
        if (normalizedUserId == FirebaseAuth.getInstance().currentUser?.uid) {
            return@withContext ensureChatIdentity()
        }
        val snapshot = db.collection(USERS_COLLECTION)
            .document(normalizedUserId)
            .get()
            .await()
        @Suppress("UNCHECKED_CAST")
        val chatKey = snapshot.data?.get(CHAT_KEY_FIELD) as? Map<String, Any?>
        chatKey?.let(ChatIdentityRecord::from)
    }

    private fun deriveNovaBlobKey(userKey: ByteArray, userId: String, purpose: String): ByteArray =
        CryptoHelpers.hkdfSha256(
            ikm = userKey,
            salt = NOVA_BLOB_SALT.toByteArray(),
            info = "moments.nova.blob.v1|$userId|$purpose".toByteArray(),
            length = 32,
        )

    private fun deriveChatMediaKey(
        conversationKey: ByteArray,
        conversationId: String,
        messageId: String,
        purpose: ChatMediaPurpose,
    ): ByteArray = CryptoHelpers.hkdfSha256(
        ikm = conversationKey,
        salt = CHAT_MEDIA_SALT.toByteArray(),
        info = "moments.chat.media.v1|$conversationId|$messageId|${purpose.raw}".toByteArray(),
        length = 32,
    )

    private fun novaBlobAuthenticatedData(userId: String, purpose: String): ByteArray =
        "moments.nova.blob.aad.v1|$userId|$purpose".toByteArray()

    private fun mediaAuthenticatedData(
        conversationId: String,
        messageId: String,
        purpose: ChatMediaPurpose,
        contentType: String,
    ): ByteArray = "moments.chat.media.aad.v1|$conversationId|$messageId|${purpose.raw}|$contentType".toByteArray()

    private fun encryptText(text: String, key: ByteArray): String {
        val data = text.toByteArray(Charsets.UTF_8)
        val sealed = CryptoHelpers.aesGcmSeal(data, key)
        return Base64.encodeToString(sealed, Base64.NO_WRAP)
    }

    private fun decryptText(encryptedText: String, key: ByteArray): String {
        val encryptedData = try {
            Base64.decode(encryptedText, Base64.DEFAULT)
        } catch (_: IllegalArgumentException) {
            throw EncryptionError.InvalidInput
        }
        val decrypted = CryptoHelpers.aesGcmOpen(encryptedData, key)
        return decrypted.toString(Charsets.UTF_8)
    }

    // MARK: - Health / metrics / key rotation (paridad EncryptionService.swift)

    data class EncryptionMetrics(
        var successfulEncryptions: Int = 0,
        var successfulDecryptions: Int = 0,
        var encryptionErrors: Int = 0,
        var decryptionErrors: Int = 0,
        var cacheHits: Int = 0,
        var keyRotations: Int = 0,
        var lastError: String? = null,
    ) {
        val totalOperations: Int
            get() = successfulEncryptions + successfulDecryptions + encryptionErrors + decryptionErrors
        val successRate: Double
            get() = if (totalOperations == 0) 1.0 else
                (successfulEncryptions + successfulDecryptions).toDouble() / totalOperations
    }

    enum class HealthStatus {
        HEALTHY, DEGRADED, UNHEALTHY, UNKNOWN;

        val isHealthy: Boolean get() = this == HEALTHY
    }

    data class EncryptionHealthReport(
        var masterKeyStatus: HealthStatus = HealthStatus.UNKNOWN,
        var encryptionStatus: HealthStatus = HealthStatus.UNKNOWN,
        var keystoreStatus: HealthStatus = HealthStatus.UNKNOWN,
        var cachePerformanceMs: Double = 0.0,
        var overallHealth: HealthStatus = HealthStatus.UNKNOWN,
        var lastError: String? = null,
    )

    data class DetailedEncryptionInfo(
        val isEnabled: Boolean,
        val status: EncryptionStatus,
        val userKeysCount: Int,
        val conversationKeysCount: Int,
        val hasValidMasterKey: Boolean,
        val metrics: EncryptionMetrics,
    )

    data class DashboardMetrics(
        val encryptionInfo: DetailedEncryptionInfo,
        val recentErrors: List<ErrorEvent>,
        val securityScore: Int,
    )

    data class ErrorEvent(
        val message: String,
        val timestamp: Long = System.currentTimeMillis(),
    )

    enum class KeyRotationReason(val raw: String) {
        MANUAL("manual"),
        USER_LEFT("user_left"),
        SECURITY_BREACH("security_breach"),
        SCHEDULED("scheduled"),
        CORRUPTION("corruption"),
    }

    enum class KeyIntegrityStatus { VALID, CORRUPTED, NOT_FOUND }

    private val metrics = EncryptionMetrics()
    private val recentErrors = ArrayDeque<ErrorEvent>()
    private const val MAX_RECENT_ERRORS = 32

    private fun recordError(message: String) {
        synchronized(recentErrors) {
            if (recentErrors.size >= MAX_RECENT_ERRORS) recentErrors.removeFirst()
            recentErrors.addLast(ErrorEvent(message))
        }
        metrics.encryptionErrors++
        metrics.lastError = message
    }

    fun getRecentErrors(): List<ErrorEvent> = synchronized(recentErrors) { recentErrors.toList() }

    suspend fun performHealthCheck(): EncryptionHealthReport = withContext(Dispatchers.Default) {
        val report = EncryptionHealthReport()
        report.masterKeyStatus = if (initialized) HealthStatus.HEALTHY else HealthStatus.UNHEALTHY
        val testStart = System.nanoTime()
        runCatching {
            val testKey = CryptoHelpers.randomBytes(32)
            encryptText("health_check", testKey)
        }.onSuccess {
            report.cachePerformanceMs = (System.nanoTime() - testStart) / 1_000_000.0
        }.onFailure {
            report.cachePerformanceMs = -1.0
            report.lastError = it.message
            it.message?.let { recordError("healthCheck: $it") }
        }
        runCatching {
            val testKey = CryptoHelpers.randomBytes(32)
            val encrypted = encryptText("health_check", testKey)
            val decrypted = decryptText(encrypted, testKey)
            report.encryptionStatus = if (decrypted == "health_check") HealthStatus.HEALTHY else HealthStatus.UNHEALTHY
        }.onFailure {
            report.encryptionStatus = HealthStatus.UNHEALTHY
            report.lastError = it.message
        }
        report.keystoreStatus = if (EncryptionKeyStore.selfTest()) HealthStatus.HEALTHY else HealthStatus.UNHEALTHY
        report.overallHealth = when {
            listOf(report.masterKeyStatus, report.encryptionStatus, report.keystoreStatus)
                .all { it.isHealthy } && report.cachePerformanceMs in 0.0..1000.0 -> HealthStatus.HEALTHY
            listOf(report.masterKeyStatus, report.encryptionStatus, report.keystoreStatus)
                .any { it == HealthStatus.UNHEALTHY } -> HealthStatus.UNHEALTHY
            else -> HealthStatus.DEGRADED
        }
        report
    }

    suspend fun getDetailedEncryptionInfo(): DetailedEncryptionInfo = DetailedEncryptionInfo(
        isEnabled = isEncryptionEnabled,
        status = encryptionStatus,
        userKeysCount = userKeyCache.size,
        conversationKeysCount = conversationKeyCache.size,
        hasValidMasterKey = initialized,
        metrics = metrics,
    )

    suspend fun getMetricsForDashboard(): DashboardMetrics {
        val info = getDetailedEncryptionInfo()
        var score = 100
        if (!info.isEnabled) score -= 40
        if (info.metrics.successRate < 0.95) score -= 20
        if (!info.hasValidMasterKey) score -= 30
        return DashboardMetrics(
            encryptionInfo = info,
            recentErrors = getRecentErrors(),
            securityScore = score.coerceIn(0, 100),
        )
    }

    suspend fun rotateConversationKey(
        conversationId: String,
        reason: KeyRotationReason = KeyRotationReason.MANUAL,
    ): Boolean = withContext(Dispatchers.IO) {
        requireInitialized()
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: throw EncryptionError.KeyNotFound
        val newKey = CryptoHelpers.randomBytes(32)
        val snapshot = db.collection("conversations").document(conversationId).get().await()
        @Suppress("UNCHECKED_CAST")
        val participants = (snapshot.data?.get("participants") as? List<*>)?.filterIsInstance<String>()
            ?: listOf(currentUserId)
        val wrappedMaps = buildWrappedConversationKeys(participants, newKey, currentUserId)
        if (wrappedMaps.size != participants.size) throw EncryptionError.PeerKeyUnavailable

        db.collection("conversations").document(conversationId).set(
            mapOf(
                "wrappedKeys" to wrappedMaps,
                "conversationKeyVersion" to FieldValue.increment(1),
                "encryptionVersion" to "3.0",
                "sharedEncryptionKey" to FieldValue.delete(),
                "encryptionKey" to FieldValue.delete(),
                "lastKeyRotation" to FieldValue.serverTimestamp(),
                "rotationReason" to reason.raw,
            ),
            com.google.firebase.firestore.SetOptions.merge(),
        ).await()
        conversationKeyCache[conversationId] = newKey
        EncryptionKeyStore.store(CONVERSATION_KEYS_PREFIX + conversationId, newKey)
        metrics.keyRotations += 1
        true
    }

    suspend fun verifyKeyIntegrity(conversationId: String): KeyIntegrityStatus = withContext(Dispatchers.Default) {
        val key = conversationKeyCache[conversationId]
            ?: EncryptionKeyStore.retrieve(CONVERSATION_KEYS_PREFIX + conversationId)
            ?: return@withContext KeyIntegrityStatus.NOT_FOUND
        runCatching {
            val test = "integrity_test_${System.currentTimeMillis()}"
            val encrypted = encryptText(test, key)
            val decrypted = decryptText(encrypted, key)
            if (decrypted == test) KeyIntegrityStatus.VALID else KeyIntegrityStatus.CORRUPTED
        }.getOrDefault(KeyIntegrityStatus.CORRUPTED)
    }

    suspend fun deleteConversationKeys(conversationId: String) {
        conversationKeyCache.remove(conversationId)
        EncryptionKeyStore.delete(CONVERSATION_KEYS_PREFIX + conversationId)
    }

    /** ≡ `SymmetricKey(size: .bits256)` al crear conversación. */
    fun randomConversationKey(): ByteArray = CryptoHelpers.randomBytes(32)

    /** ≡ `cacheConversationKeyLocally(conversationId:key:)`. */
    suspend fun cacheConversationKeyLocally(conversationId: String, key: ByteArray) {
        if (conversationId.isBlank() || key.isEmpty()) return
        conversationKeyCache[conversationId] = key
        withContext(Dispatchers.IO) {
            EncryptionKeyStore.store(CONVERSATION_KEYS_PREFIX + conversationId, key)
        }
    }

    suspend fun toggleEncryption(enabled: Boolean) {
        isEncryptionEnabled = enabled
    }

    private suspend fun loadOrCreateLocalChatIdentity(userId: String): ChatIdentityRecord {
        val privateKey = ensureLocalChatIdentity(userId)
        val publicKey = Base64.encodeToString(Curve25519Helper.publicKeyFromPrivate(privateKey), Base64.NO_WRAP)
        return ChatIdentityRecord(
            keyId = resolveStableChatKeyId(userId, publicKey),
            publicKeyBase64 = publicKey,
        )
    }

    private suspend fun syncChatIdentityRecord(record: ChatIdentityRecord, userId: String) {
        persistLocalChatKeyId(record.keyId, userId)
        db.collection(USERS_COLLECTION)
            .document(requireUserId(userId))
            .set(
                mapOf(CHAT_KEY_FIELD to record.asFirestoreData()),
                com.google.firebase.firestore.SetOptions.merge(),
            )
            .await()
    }

    private suspend fun resolveStableChatKeyId(
        userId: String,
        publicKeyBase64: String,
        preferredKeyId: String? = null,
    ): String {
        preferredKeyId?.takeIf(String::isNotBlank)?.let {
            persistLocalChatKeyId(it, userId)
            return it
        }
        storedLocalChatKeyId(userId)?.takeIf(String::isNotBlank)?.let { return it }

        val userSnapshot = db.collection(USERS_COLLECTION).document(userId).get().await()
        @Suppress("UNCHECKED_CAST")
        val remoteIdentity = (userSnapshot.data?.get(CHAT_KEY_FIELD) as? Map<String, Any?>)
            ?.let(ChatIdentityRecord::from)
        if (remoteIdentity?.publicKeyBase64 == publicKeyBase64) {
            persistLocalChatKeyId(remoteIdentity.keyId, userId)
            return remoteIdentity.keyId
        }

        val recoverySnapshot = db.collection(USERS_COLLECTION)
            .document(userId)
            .collection(CHAT_RECOVERY_COLLECTION)
            .document(CHAT_RECOVERY_DOC)
            .get()
            .await()
        val recoveryKeyId = recoverySnapshot.data
            ?.let(ChatRecoveryBundle::from)
            ?.keyId
            ?.takeIf(String::isNotBlank)
        if (recoveryKeyId != null) {
            persistLocalChatKeyId(recoveryKeyId, userId)
            return recoveryKeyId
        }

        return UUID.randomUUID().toString().also { persistLocalChatKeyId(it, userId) }
    }

    private suspend fun restoreChatIdentityFromBundle(
        pin: String,
        bundle: ChatRecoveryBundle,
        userId: String,
    ) {
        val salt = Base64.decode(bundle.salt, Base64.DEFAULT)
        val encryptedPrivateKey = Base64.decode(bundle.encryptedPrivateKey, Base64.DEFAULT)
        val pinKey = ChatRecoveryCrypto.derivePINKey(
            pin = pin,
            salt = salt,
            iterations = bundle.kdfParams.iterations,
            keyLength = bundle.kdfParams.keyLength,
        )
        val privateKey = CryptoHelpers.aesGcmOpen(encryptedPrivateKey, pinKey)
        if (privateKey.size != CURVE25519_PRIVATE_KEY_BYTES) throw EncryptionError.DecryptionFailed
        // Constructor validation is the Android equivalent of CryptoKit's rawRepresentation initializer.
        val publicKey = Base64.encodeToString(Curve25519Helper.publicKeyFromPrivate(privateKey), Base64.NO_WRAP)
        EncryptionKeyStore.store(CHAT_IDENTITY_KEY_PREFIX + userId, privateKey)
        val identity = ChatIdentityRecord(
            keyId = resolveStableChatKeyId(userId, publicKey, bundle.keyId),
            publicKeyBase64 = publicKey,
        )
        syncChatIdentityRecord(identity, userId)

        bundle.encryptedUserKey?.let { encodedUserKey ->
            runCatching {
                val userKey = CryptoHelpers.aesGcmOpen(Base64.decode(encodedUserKey, Base64.DEFAULT), pinKey)
                if (userKey.size != 32) throw EncryptionError.DecryptionFailed
                cacheUserKey(userId, userKey)
                EncryptionKeyStore.store(USER_KEYS_PREFIX + userId, userKey)
            }
        }
    }

    private fun validateRecoveryPin(pin: String): String {
        val trimmed = pin.trim()
        if (trimmed.length != 6 || !trimmed.all(Char::isDigit)) throw EncryptionError.InvalidPIN
        return trimmed
    }

    private fun requireUserId(userId: String): String = userId.trim().also {
        require(it.isNotEmpty()) { "A chat identity requires a user id" }
    }

    private fun identityPrefs() =
        (appContext ?: error("EncryptionService.initialize required"))
            .getSharedPreferences(CHAT_IDENTITY_PREFS, Context.MODE_PRIVATE)

    private fun storedLocalChatKeyId(userId: String): String? =
        identityPrefs().getString(CHAT_IDENTITY_KEY_ID_PREFIX + userId, null)

    private fun persistLocalChatKeyId(keyId: String, userId: String) {
        identityPrefs().edit().putString(CHAT_IDENTITY_KEY_ID_PREFIX + userId, keyId).apply()
    }

    private fun deleteLocalChatIdentity(userId: String) {
        EncryptionKeyStore.delete(CHAT_IDENTITY_KEY_PREFIX + userId)
    }

    private fun currentRecoveryAttemptState(userId: String): ChatRecoveryAttemptState {
        val prefs = identityPrefs()
        val lockout = prefs.getLong(CHAT_RECOVERY_LOCKOUT_PREFIX + userId, 0L)
        if (lockout > 0L && lockout <= System.currentTimeMillis()) {
            clearRecoveryAttemptState(userId)
            return ChatRecoveryAttemptState(maxAttempts = CHAT_RECOVERY_MAX_ATTEMPTS)
        }
        return ChatRecoveryAttemptState(
            failedAttempts = prefs.getInt(CHAT_RECOVERY_ATTEMPTS_PREFIX + userId, 0),
            maxAttempts = CHAT_RECOVERY_MAX_ATTEMPTS,
            lockedUntil = lockout.takeIf { it > 0L }?.let(::Date),
        )
    }

    private fun registerFailedRecoveryAttempt(userId: String) {
        val prefs = identityPrefs()
        val attemptsKey = CHAT_RECOVERY_ATTEMPTS_PREFIX + userId
        val next = prefs.getInt(attemptsKey, 0) + 1
        prefs.edit().apply {
            if (next >= CHAT_RECOVERY_MAX_ATTEMPTS) {
                putLong(CHAT_RECOVERY_LOCKOUT_PREFIX + userId, System.currentTimeMillis() + CHAT_RECOVERY_LOCKOUT_MS)
                remove(attemptsKey)
            } else {
                putInt(attemptsKey, next)
            }
        }.apply()
    }

    private fun clearRecoveryAttemptState(userId: String) {
        identityPrefs().edit()
            .remove(CHAT_RECOVERY_ATTEMPTS_PREFIX + userId)
            .remove(CHAT_RECOVERY_LOCKOUT_PREFIX + userId)
            .apply()
    }
}
