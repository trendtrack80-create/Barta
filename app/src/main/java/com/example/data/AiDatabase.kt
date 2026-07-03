package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "ai_sessions")
data class AiSession(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val isPinned: Boolean = false
)

@Entity(
    tableName = "ai_messages",
    foreignKeys = [
        ForeignKey(
            entity = AiSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class AiMessage(
    @PrimaryKey val id: String,
    val sessionId: String,
    val sender: String, // "user" or "ai"
    val text: String,
    val timestamp: Long,
    val isLiked: Boolean = false,
    val isDisliked: Boolean = false
)

@Dao
interface AiSessionDao {
    @Query("SELECT * FROM ai_sessions ORDER BY isPinned DESC, createdAt DESC")
    fun getAllSessionsFlow(): Flow<List<AiSession>>

    @Query("SELECT * FROM ai_sessions ORDER BY isPinned DESC, createdAt DESC")
    suspend fun getAllSessions(): List<AiSession>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: AiSession)

    @Update
    suspend fun updateSession(session: AiSession)

    @Query("UPDATE ai_sessions SET title = :title WHERE id = :id")
    suspend fun updateSessionTitle(id: String, title: String)

    @Query("UPDATE ai_sessions SET isPinned = :isPinned WHERE id = :id")
    suspend fun updateSessionPinned(id: String, isPinned: Boolean)

    @Delete
    suspend fun deleteSession(session: AiSession)

    @Query("DELETE FROM ai_sessions")
    suspend fun deleteAllSessions()
}

@Dao
interface AiMessageDao {
    @Query("SELECT * FROM ai_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesForSessionFlow(sessionId: String): Flow<List<AiMessage>>

    @Query("SELECT * FROM ai_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessagesForSession(sessionId: String): List<AiMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: AiMessage)

    @Delete
    suspend fun deleteMessage(message: AiMessage)

    @Query("UPDATE ai_messages SET isLiked = :isLiked, isDisliked = :isDisliked WHERE id = :id")
    suspend fun updateFeedback(id: String, isLiked: Boolean, isDisliked: Boolean)

    @Query("DELETE FROM ai_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesForSession(sessionId: String)
}

@Database(entities = [AiSession::class, AiMessage::class], version = 1, exportSchema = false)
abstract class AiDatabase : RoomDatabase() {
    abstract fun aiSessionDao(): AiSessionDao
    abstract fun aiMessageDao(): AiMessageDao

    companion object {
        @Volatile
        private var INSTANCE: AiDatabase? = null

        fun getDatabase(context: Context): AiDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AiDatabase::class.java,
                    "ai_chat_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
