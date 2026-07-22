package com.moments.android.services.messaging

import android.content.Context
import android.net.Uri
import com.moments.android.models.ChatMediaPurpose
import com.moments.android.models.EnhancedMessage
import com.moments.android.services.persistence.LocalPersistenceService
import java.io.File
import java.util.Calendar
import java.util.Date

data class ChatStorageBreakdown(
    val messageCount: Int,
    val decryptedMediaBytes: Long,
    val posterBytes: Long,
) {
    val totalMediaBytes: Long get() = decryptedMediaBytes + posterBytes
}

/**
 * Port de ChatCacheStore.swift — almacenamiento local de media descifrada de chat.
 */
object ChatCacheStore {
    private const val PREFS = "chat_cache_store"
    private const val DID_MIGRATE_KEY = "didMigrateChatMediaToAppGroup"
    private const val LEGACY_DECRYPTED = "chat_media_decrypted"
    private const val LEGACY_POSTERS = "chat_video_posters"

    @Volatile private var appContext: Context? = null

    fun initialize(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
            ChatMediaDownloadPolicy.initialize(context)
        }
    }

    private fun context() = appContext ?: error("ChatCacheStore.initialize required")

    private fun chatMediaRoot(): File {
        val dir = File(context().filesDir, "${MessageIngestQueue.SHARED_DIR_NAME}/ChatMedia")
        dir.mkdirs()
        return dir
    }

    private fun decryptedDirectory(): File =
        File(chatMediaRoot(), "decrypted").apply { mkdirs() }

    private fun postersDirectory(): File =
        File(chatMediaRoot(), "posters").apply { mkdirs() }

    fun decryptedMediaFile(
        conversationId: String,
        messageId: String,
        purpose: ChatMediaPurpose,
        fileExtension: String,
    ): File {
        val filename = decryptedFilename(conversationId, messageId, purpose, fileExtension)
        return File(decryptedDirectory(), filename)
    }

    fun decryptedMediaURL(
        conversationId: String,
        messageId: String,
        purpose: ChatMediaPurpose,
        fileExtension: String,
    ): String = Uri.fromFile(decryptedMediaFile(conversationId, messageId, purpose, fileExtension)).toString()

    fun posterFile(messageId: String): File {
        val safeId = messageId.replace("/", "_")
        return File(postersDirectory(), "$safeId.jpg")
    }

    fun ensureDirectories() {
        migrateFromLegacyCachesIfNeeded()
        decryptedDirectory()
        postersDirectory()
    }

    fun writeDecryptedMedia(
        data: ByteArray,
        conversationId: String,
        messageId: String,
        purpose: ChatMediaPurpose,
        fileExtension: String,
    ): File {
        ensureDirectories()
        val file = decryptedMediaFile(conversationId, messageId, purpose, fileExtension)
        file.writeBytes(data)
        return file
    }

    fun copyDecryptedMedia(
        sourceFile: File,
        conversationId: String,
        messageId: String,
        purpose: ChatMediaPurpose,
        fileExtension: String,
    ): File {
        ensureDirectories()
        val destination = decryptedMediaFile(conversationId, messageId, purpose, fileExtension)
        val temp = File(destination.parentFile, ".${System.currentTimeMillis()}.tmp")
        sourceFile.copyTo(temp, overwrite = true)
        if (destination.exists()) destination.delete()
        temp.renameTo(destination)
        return destination
    }

    fun localURLsIfPresent(message: EnhancedMessage): Pair<String?, String?> {
        if (message.isDeleted) return null to null

        var mediaUrl = message.mediaUrl
        var thumbnailUrl = message.thumbnailUrl

        if ((mediaUrl == null || localFileMissing(mediaUrl)) && message.mediaEncryption != null) {
            val enc = message.mediaEncryption
            val cacheFile = decryptedMediaFile(
                message.conversationId, message.id, enc.purpose, enc.fileExtension,
            )
            if (cacheFile.exists()) {
                mediaUrl = Uri.fromFile(cacheFile).toString()
                touchAccessDate(cacheFile)
            }
        }

        if ((thumbnailUrl == null || localFileMissing(thumbnailUrl)) && message.thumbnailEncryption != null) {
            val enc = message.thumbnailEncryption
            val cacheFile = decryptedMediaFile(
                message.conversationId, message.id, enc.purpose, enc.fileExtension,
            )
            if (cacheFile.exists()) {
                thumbnailUrl = Uri.fromFile(cacheFile).toString()
                touchAccessDate(cacheFile)
            }
        }

        return mediaUrl to thumbnailUrl
    }

    fun totalMediaBytes(): Long {
        if (appContext == null) return 0L
        return directoryBytes(decryptedDirectory()) + directoryBytes(postersDirectory())
    }

    fun bytes(conversationId: String): Long {
        val prefix = safeComponent(conversationId) + "_"
        return filesIn(decryptedDirectory())
            .filter { it.name.startsWith(prefix) }
            .sumOf { it.length() }
    }

    fun bytesByConversation(conversationIds: List<String>): Map<String, Long> {
        if (conversationIds.isEmpty()) return emptyMap()
        val scanned = filesIn(decryptedDirectory()).map { it.name to it.length() }
        if (scanned.isEmpty()) return emptyMap()
        val result = mutableMapOf<String, Long>()
        for (conversationId in conversationIds) {
            val prefix = safeComponent(conversationId) + "_"
            val total = scanned.sumOf { (name, size) -> if (name.startsWith(prefix)) size else 0L }
            if (total > 0) result[conversationId] = total
        }
        return result
    }

    fun storageBreakdown(): ChatStorageBreakdown = ChatStorageBreakdown(
        messageCount = LocalPersistenceService.cachedMessageCount(),
        decryptedMediaBytes = directoryBytes(decryptedDirectory()),
        posterBytes = directoryBytes(postersDirectory()),
    )

    fun deleteMessageFiles(conversationId: String, messageId: String) {
        val msgPrefix = safeComponent(conversationId) + "_" + safeComponent(messageId) + "_"
        filesIn(decryptedDirectory()).filter { it.name.startsWith(msgPrefix) }.forEach { it.delete() }
        posterFile(messageId).takeIf { it.exists() }?.delete()
    }

    fun deleteConversation(conversationId: String, messageIds: List<String>) {
        val prefix = safeComponent(conversationId) + "_"
        filesIn(decryptedDirectory()).filter { it.name.startsWith(prefix) }.forEach { it.delete() }
        messageIds.forEach { id -> posterFile(id).takeIf { it.exists() }?.delete() }
    }

    fun clearAllMedia() {
        filesIn(decryptedDirectory()).forEach { it.delete() }
        filesIn(postersDirectory()).forEach { it.delete() }
    }

    private const val QUOTA_PROTECTION_DAYS = 7

    fun enforceQuota() {
        val maxBytes = ChatMediaDownloadPolicy.maxMediaBytes
        var total = totalMediaBytes()
        if (total <= maxBytes) return

        val protectionCutoff = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -QUOTA_PROTECTION_DAYS)
        }.time
        val protectedKeys = LocalPersistenceService.cachedMessageKeys(protectionCutoff)

        val candidates = trackedFiles().sortedBy { it.modificationDate }
        val mutable = candidates.toMutableList()
        while (total > maxBytes) {
            val index = mutable.indexOfFirst { tracked ->
                tracked.messageKey == null || tracked.messageKey !in protectedKeys
            }
            if (index < 0) break
            val oldest = mutable.removeAt(index)
            val size = oldest.file.length()
            oldest.file.delete()
            total -= size
        }
    }

    fun touchAccessDate(file: File) {
        if (!file.exists()) return
        file.setLastModified(System.currentTimeMillis())
    }

    fun enforceRetention() {
        val retentionDays = ChatMediaDownloadPolicy.retentionDays
        if (retentionDays <= 0) return
        val cutoff = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -retentionDays) }.time
        val protectedKeys = LocalPersistenceService.cachedMessageKeys(cutoff)
        trackedFiles().forEach { tracked ->
            if (tracked.modificationDate >= cutoff) return@forEach
            if (tracked.messageKey != null && tracked.messageKey in protectedKeys) return@forEach
            tracked.file.delete()
        }
    }

    fun runMaintenance() {
        migrateFromLegacyCachesIfNeeded()
        enforceRetention()
        enforceQuota()
    }

    private data class TrackedFile(val file: File, val modificationDate: Date, val messageKey: String?)

    private fun decryptedFilename(
        conversationId: String,
        messageId: String,
        purpose: ChatMediaPurpose,
        fileExtension: String,
    ): String = "${safeComponent(conversationId)}_${safeComponent(messageId)}_${purpose.raw}.$fileExtension"

    private fun safeComponent(value: String): String = value.replace("/", "_")

    private fun migrateFromLegacyCachesIfNeeded() {
        val prefs = context().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(DID_MIGRATE_KEY, false)) return
        ensureDirectoriesWithoutMigrationFlag()
        val legacyRoot = File(context().cacheDir, LEGACY_DECRYPTED).parentFile ?: context().cacheDir
        migrateDirectory(File(legacyRoot, LEGACY_DECRYPTED), decryptedDirectory())
        migrateDirectory(File(legacyRoot, LEGACY_POSTERS), postersDirectory())
        prefs.edit().putBoolean(DID_MIGRATE_KEY, true).apply()
    }

    private fun ensureDirectoriesWithoutMigrationFlag() {
        chatMediaRoot()
        decryptedDirectory()
        postersDirectory()
    }

    private fun migrateDirectory(source: File, destination: File) {
        if (!source.exists()) return
        filesIn(source).forEach { file ->
            val target = File(destination, file.name)
            if (!target.exists()) file.copyTo(target)
        }
    }

    private fun filesIn(directory: File): List<File> =
        directory.listFiles()?.filter { it.isFile } ?: emptyList()

    private fun directoryBytes(directory: File): Long =
        filesIn(directory).sumOf { it.length() }

    private fun localFileMissing(urlString: String?): Boolean {
        if (urlString.isNullOrEmpty()) return false
        if (!urlString.startsWith("file://")) return false
        return !File(Uri.parse(urlString).path ?: "").exists()
    }

    private fun trackedFiles(): List<TrackedFile> {
        val results = mutableListOf<TrackedFile>()
        filesIn(decryptedDirectory()).forEach { file ->
            results.add(
                TrackedFile(file, Date(file.lastModified()), messageKeyFromDecryptedFilename(file.name)),
            )
        }
        filesIn(postersDirectory()).forEach { file ->
            results.add(TrackedFile(file, Date(file.lastModified()), null))
        }
        return results
    }

    private fun messageKeyFromDecryptedFilename(filename: String): String? {
        val name = filename.substringBeforeLast('.')
        val parts = name.split('_')
        if (parts.size < 3) return null
        val purposeRaw = parts.last()
        if (ChatMediaPurpose.from(purposeRaw) == null) return null
        val messageId = parts[parts.size - 2]
        val conversationId = parts.dropLast(2).joinToString("_")
        if (conversationId.isEmpty() || messageId.isEmpty()) return null
        return "$conversationId:$messageId"
    }
}
