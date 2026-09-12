package com.moments.android.services.persistence.room

import android.content.Context
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.Transaction
import androidx.sqlite.driver.AndroidSQLiteDriver
import kotlinx.coroutines.Dispatchers

/**
 * El almacén local de Android. Cada fila mantiene el payload existente para que
 * la migración no cambie el modelo de producto, pero sus claves de consulta se
 * guardan como columnas e índices reales de SQLite.
 */
@Entity(
    tableName = "cached_moments",
    primaryKeys = ["momentId", "feedSection"],
    indices = [Index(value = ["feedSection", "timestamp"]), Index(value = ["lastSyncedAt"])],
)
data class MomentEntity(
    val momentId: String,
    val feedSection: String,
    val timestamp: Long,
    val lastSyncedAt: Long,
    val payload: String,
)

@Entity(
    tableName = "cached_stories",
    indices = [Index(value = ["authorId"]), Index(value = ["expirationDate"])],
)
data class StoryEntity(
    @PrimaryKey
    val id: String,
    val authorId: String,
    val timestamp: Long,
    val expirationDate: Long,
    val cachedAt: Long,
    val payload: String,
)

@Entity(
    tableName = "cached_users",
    primaryKeys = ["userId", "cacheSection"],
    indices = [Index(value = ["cacheSection", "lastSyncedAt"])],
)
data class UserEntity(
    val userId: String,
    val cacheSection: String,
    val lastSyncedAt: Long,
    val payload: String,
)

@Entity(
    tableName = "cached_connections",
    primaryKeys = ["userId", "targetId", "type"],
    indices = [Index(value = ["userId", "type"]), Index(value = ["userId", "targetId"])],
)
data class ConnectionEntity(
    val userId: String,
    val targetId: String,
    val type: String,
    val timestamp: Long,
)

@Entity(
    tableName = "pending_actions",
    indices = [Index(value = ["status", "createdAt"])],
)
data class PendingActionEntity(
    @PrimaryKey
    val id: String,
    val type: String,
    val status: String,
    val payload: ByteArray,
    val createdAt: Long,
    val retryCount: Int,
    val lastError: String?,
    val lastAttemptAt: Long?,
)

@Entity(
    tableName = "cached_conversations",
    indices = [Index(value = ["isPinned", "timestamp"]), Index(value = ["lastSyncedAt"])],
)
data class ConversationEntity(
    @PrimaryKey
    val id: String,
    val isPinned: Boolean,
    val timestamp: Long,
    val lastSyncedAt: Long,
    val payload: String,
)

@Entity(
    tableName = "cached_notifications",
    indices = [Index(value = ["timestamp"]), Index(value = ["isPending"])],
)
data class NotificationEntity(
    @PrimaryKey
    val id: String,
    val timestamp: Long,
    val isPending: Boolean,
    val lastSyncedAt: Long,
    val payload: String,
)

@Entity(
    tableName = "cached_searches",
    indices = [Index(value = ["timestamp"])],
)
data class SearchEntity(
    @PrimaryKey
    val id: String,
    val query: String,
    val type: String,
    val targetId: String?,
    val timestamp: Long,
)

@Entity(
    tableName = "story_seen_state",
    primaryKeys = ["viewerId", "authorId"],
    indices = [Index(value = ["lastSeenAt"])],
)
data class StorySeenEntity(
    val viewerId: String,
    val authorId: String,
    val lastSeenAt: Long,
)

@Entity(
    tableName = "cached_messages",
    primaryKeys = ["conversationId", "id"],
    indices = [Index(value = ["conversationId", "timestamp", "id"]), Index(value = ["timestamp"])],
)
data class MessageEntity(
    val conversationId: String,
    val id: String,
    val timestamp: Long,
    val senderId: String,
    val type: String,
    val content: String?,
    val isRead: Boolean,
    val isDeleted: Boolean,
    val isVanishModeMessage: Boolean,
    val payload: String,
)

@Dao
interface MomentsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMoments(items: List<MomentEntity>)

    @Query("SELECT * FROM cached_moments WHERE feedSection = :section ORDER BY timestamp DESC")
    suspend fun momentsIn(section: String): List<MomentEntity>

    @Query("SELECT * FROM cached_moments")
    suspend fun allMoments(): List<MomentEntity>

    @Query("DELETE FROM cached_moments WHERE feedSection = :section")
    suspend fun deleteMomentsIn(section: String)

    @Query("DELETE FROM cached_moments WHERE momentId = :momentId")
    suspend fun deleteMoment(momentId: String)

    @Query("DELETE FROM cached_moments")
    suspend fun deleteAllMoments()

    @Query("DELETE FROM cached_moments WHERE lastSyncedAt < :cutoff")
    suspend fun deleteMomentsBefore(cutoff: Long)

    @Transaction
    suspend fun replaceMomentsIn(section: String, items: List<MomentEntity>) {
        deleteMomentsIn(section)
        if (items.isNotEmpty()) upsertMoments(items)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStories(items: List<StoryEntity>)

    @Query("SELECT * FROM cached_stories WHERE authorId = :authorId ORDER BY timestamp ASC")
    suspend fun storiesByAuthor(authorId: String): List<StoryEntity>

    @Query("SELECT * FROM cached_stories")
    suspend fun allStories(): List<StoryEntity>

    @Query("DELETE FROM cached_stories")
    suspend fun deleteAllStories()

    @Query("DELETE FROM cached_stories WHERE expirationDate < :now")
    suspend fun deleteExpiredStories(now: Long)

    @Query("DELETE FROM cached_stories WHERE id = :storyId")
    suspend fun deleteStory(storyId: String)

    @Query("DELETE FROM cached_stories WHERE authorId = :authorId")
    suspend fun deleteStoriesByAuthor(authorId: String)

    @Transaction
    suspend fun replaceStories(items: List<StoryEntity>) {
        deleteAllStories()
        if (items.isNotEmpty()) upsertStories(items)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUsers(items: List<UserEntity>)

    @Query("SELECT * FROM cached_users WHERE userId = :userId ORDER BY lastSyncedAt DESC")
    suspend fun usersById(userId: String): List<UserEntity>

    @Query("SELECT * FROM cached_users WHERE cacheSection = 'currentUser' ORDER BY lastSyncedAt DESC LIMIT 1")
    suspend fun currentUser(): UserEntity?

    @Query("SELECT * FROM cached_users WHERE cacheSection != 'currentUser'")
    suspend fun cachedNonCurrentUsers(): List<UserEntity>

    @Query("DELETE FROM cached_users WHERE userId = :userId AND cacheSection = :section")
    suspend fun deleteUser(userId: String, section: String)

    @Query("DELETE FROM cached_users WHERE cacheSection != 'currentUser' AND lastSyncedAt < :cutoff")
    suspend fun deleteUsersBefore(cutoff: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConnections(items: List<ConnectionEntity>)

    @Query("SELECT * FROM cached_connections WHERE userId = :userId")
    suspend fun connectionsFor(userId: String): List<ConnectionEntity>

    @Query("DELETE FROM cached_connections WHERE userId = :userId")
    suspend fun deleteConnectionsFor(userId: String)

    @Query("DELETE FROM cached_connections")
    suspend fun deleteAllConnections()

    @Transaction
    suspend fun replaceConnectionsFor(userId: String, items: List<ConnectionEntity>) {
        deleteConnectionsFor(userId)
        if (items.isNotEmpty()) upsertConnections(items)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertActions(items: List<PendingActionEntity>)

    @Query("SELECT * FROM pending_actions ORDER BY createdAt ASC")
    suspend fun allActions(): List<PendingActionEntity>

    @Query("DELETE FROM pending_actions")
    suspend fun deleteAllActions()

    @Transaction
    suspend fun replaceActions(items: List<PendingActionEntity>) {
        deleteAllActions()
        if (items.isNotEmpty()) upsertActions(items)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversations(items: List<ConversationEntity>)

    @Query("SELECT * FROM cached_conversations ORDER BY isPinned DESC, timestamp DESC")
    suspend fun allConversations(): List<ConversationEntity>

    @Query("DELETE FROM cached_conversations")
    suspend fun deleteAllConversations()

    @Transaction
    suspend fun replaceConversations(items: List<ConversationEntity>) {
        deleteAllConversations()
        if (items.isNotEmpty()) upsertConversations(items)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNotifications(items: List<NotificationEntity>)

    @Query("SELECT * FROM cached_notifications ORDER BY timestamp DESC")
    suspend fun allNotifications(): List<NotificationEntity>

    @Query("DELETE FROM cached_notifications")
    suspend fun deleteAllNotifications()

    @Transaction
    suspend fun replaceNotifications(items: List<NotificationEntity>) {
        deleteAllNotifications()
        if (items.isNotEmpty()) upsertNotifications(items)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSearches(items: List<SearchEntity>)

    @Query("SELECT * FROM cached_searches ORDER BY timestamp DESC")
    suspend fun allSearches(): List<SearchEntity>

    @Query("DELETE FROM cached_searches")
    suspend fun deleteAllSearches()

    @Transaction
    suspend fun replaceSearches(items: List<SearchEntity>) {
        deleteAllSearches()
        if (items.isNotEmpty()) upsertSearches(items)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStorySeen(items: List<StorySeenEntity>)

    @Query("SELECT * FROM story_seen_state")
    suspend fun allStorySeen(): List<StorySeenEntity>

    @Query("DELETE FROM story_seen_state")
    suspend fun deleteAllStorySeen()

    @Transaction
    suspend fun replaceStorySeen(items: List<StorySeenEntity>) {
        deleteAllStorySeen()
        if (items.isNotEmpty()) upsertStorySeen(items)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessages(items: List<MessageEntity>)

    @Query("SELECT * FROM cached_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC, id ASC")
    suspend fun messagesIn(conversationId: String): List<MessageEntity>

    @Query("""
        SELECT * FROM cached_messages
        WHERE conversationId = :conversationId
          AND (:cutoff IS NULL OR timestamp > :cutoff)
        ORDER BY timestamp DESC, id DESC
        LIMIT :limit
    """)
    suspend fun recentMessagesIn(conversationId: String, cutoff: Long?, limit: Int): List<MessageEntity>

    @Query("""
        SELECT * FROM cached_messages
        WHERE conversationId = :conversationId
          AND (:cutoff IS NULL OR timestamp > :cutoff)
          AND (timestamp < :timestamp OR (timestamp = :timestamp AND id < :messageId))
        ORDER BY timestamp DESC, id DESC
        LIMIT :limit
    """)
    suspend fun messagesBefore(
        conversationId: String,
        timestamp: Long,
        messageId: String,
        cutoff: Long?,
        limit: Int,
    ): List<MessageEntity>

    @Query("""
        SELECT * FROM cached_messages
        WHERE conversationId = :conversationId
          AND (:cutoff IS NULL OR timestamp > :cutoff)
          AND (timestamp > :timestamp OR (timestamp = :timestamp AND id >= :messageId))
        ORDER BY timestamp ASC, id ASC
        LIMIT :limit
    """)
    suspend fun messagesAfter(
        conversationId: String,
        timestamp: Long,
        messageId: String,
        cutoff: Long?,
        limit: Int,
    ): List<MessageEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM cached_messages WHERE conversationId = :conversationId AND id = :messageId)")
    suspend fun containsMessage(conversationId: String, messageId: String): Boolean

    @Query("SELECT * FROM cached_messages WHERE conversationId = :conversationId ORDER BY timestamp DESC, id DESC LIMIT 1")
    suspend fun latestMessageIn(conversationId: String): MessageEntity?

    @Query("SELECT MAX(timestamp) FROM cached_messages WHERE conversationId = :conversationId")
    suspend fun latestMessageTimestampIn(conversationId: String): Long?

    @Query("""
        SELECT id FROM cached_messages
        WHERE conversationId = :conversationId
          AND timestamp < :cutoff
          AND id NOT IN (
              SELECT id FROM cached_messages
              WHERE conversationId = :conversationId
              ORDER BY timestamp DESC, id DESC
              LIMIT :keepCount
          )
    """)
    suspend fun staleMessageIdsOutsideRecentWindow(
        conversationId: String,
        cutoff: Long,
        keepCount: Int,
    ): List<String>

    @Query("""
        DELETE FROM cached_messages
        WHERE conversationId = :conversationId
          AND timestamp < :cutoff
          AND id NOT IN (
              SELECT id FROM cached_messages
              WHERE conversationId = :conversationId
              ORDER BY timestamp DESC, id DESC
              LIMIT :keepCount
          )
    """)
    suspend fun deleteStaleMessagesOutsideRecentWindow(
        conversationId: String,
        cutoff: Long,
        keepCount: Int,
    )

    @Query("""
        SELECT COUNT(*) FROM cached_messages
        WHERE conversationId = :conversationId
          AND senderId != :currentUserId
          AND isRead = 0
          AND (:lastReadAt IS NULL OR timestamp > :lastReadAt)
    """)
    suspend fun unreadCount(conversationId: String, currentUserId: String, lastReadAt: Long?): Int

    @Query("SELECT DISTINCT conversationId FROM cached_messages")
    suspend fun conversationIdsWithMessages(): List<String>

    @Query("SELECT * FROM cached_messages")
    suspend fun allMessages(): List<MessageEntity>

    @Query("DELETE FROM cached_messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesIn(conversationId: String)

    @Query("DELETE FROM cached_messages")
    suspend fun deleteAllMessages()

    @Transaction
    suspend fun clearAllCache() {
        deleteAllMoments()
        deleteAllStories()
        deleteAllActions()
        deleteAllConversations()
        deleteAllNotifications()
        deleteAllSearches()
        deleteAllStorySeen()
        deleteAllMessages()
        cachedNonCurrentUsers().forEach { deleteUser(it.userId, it.cacheSection) }
        currentUser()?.let { deleteUser(it.userId, it.cacheSection) }
        deleteAllConnections()
    }

    @Transaction
    suspend fun replaceMessagesIn(conversationId: String, items: List<MessageEntity>) {
        deleteMessagesIn(conversationId)
        if (items.isNotEmpty()) upsertMessages(items)
    }
}

@Database(
    entities = [
        MomentEntity::class,
        StoryEntity::class,
        UserEntity::class,
        ConnectionEntity::class,
        PendingActionEntity::class,
        ConversationEntity::class,
        NotificationEntity::class,
        SearchEntity::class,
        StorySeenEntity::class,
        MessageEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class MomentsRoomDatabase : RoomDatabase() {
    abstract fun momentsDao(): MomentsDao
}

object MomentsRoomStore {
    private const val DATABASE_NAME = "moments_cache.db"

    @Volatile
    private var database: MomentsRoomDatabase? = null

    fun initialize(context: Context) {
        if (database != null) return
        synchronized(this) {
            if (database == null) {
                database = Room.databaseBuilder<MomentsRoomDatabase>(
                    context.applicationContext,
                    DATABASE_NAME,
                )
                    .setDriver(AndroidSQLiteDriver())
                    .setQueryCoroutineContext(Dispatchers.IO)
                    .build()
            }
        }
    }

    fun dao(): MomentsDao = database?.momentsDao()
        ?: error("MomentsRoomStore.initialize required")
}
