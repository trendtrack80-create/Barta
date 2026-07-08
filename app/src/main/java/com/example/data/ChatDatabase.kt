package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY lastMessageTime DESC, name ASC")
    fun getAllContacts(): Flow<List<Contact>>

    @Query("SELECT * FROM contacts WHERE phone = :phone LIMIT 1")
    suspend fun getContactByPhone(phone: String): Contact?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: Contact)

    @Update
    suspend fun updateContact(contact: Contact)

    @Query("UPDATE contacts SET lastMessageText = :text, lastMessageTime = :time WHERE phone = :phone")
    suspend fun updateLastMessage(phone: String, text: String, time: Long)

    @Query("UPDATE contacts SET lastSeen = :lastSeen WHERE phone = :phone")
    suspend fun updateContactPresence(phone: String, lastSeen: String)

    @Query("UPDATE contacts SET unreadCount = :count WHERE phone = :phone")
    suspend fun updateUnreadCount(phone: String, count: Int)

    @Query("UPDATE contacts SET typingStatus = :status WHERE phone = :phone")
    suspend fun updateTypingStatus(phone: String, status: String)

    @Delete
    suspend fun deleteContact(contact: Contact)

    @Query("DELETE FROM contacts")
    suspend fun deleteAllContacts()
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE ((senderId = :user1 AND receiverId = :user2) OR (senderId = :user2 AND receiverId = :user1) OR (receiverId = :user2 AND :user2 LIKE 'group_%')) AND isDeletedForMe = 0 ORDER BY timestamp ASC")
    fun getMessagesForChat(user1: String, user2: String): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE ((senderId = :user1 AND receiverId = :user2) OR (senderId = :user2 AND receiverId = :user1) OR (receiverId = :user2 AND :user2 LIKE 'group_%')) AND isDeletedForMe = 0 ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessagesForChat(user1: String, user2: String, limit: Int): List<Message>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: Message)

    @Query("DELETE FROM messages WHERE (senderId = :user1 AND receiverId = :user2) OR (senderId = :user2 AND receiverId = :user1) OR (receiverId = :user2 AND :user2 LIKE 'group_%')")
    suspend fun deleteMessagesForChat(user1: String, user2: String)

    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()

    @Query("UPDATE messages SET isDeletedForMe = 1 WHERE id = :msgId")
    suspend fun markDeletedForMe(msgId: String)

    @Query("UPDATE messages SET isDeletedForEveryone = 1 WHERE id = :msgId")
    suspend fun markDeletedForEveryone(msgId: String)

    @Query("UPDATE messages SET text = :text, isEdited = 1 WHERE id = :msgId")
    suspend fun updateMessageText(msgId: String, text: String)

    @Query("SELECT * FROM messages WHERE id = :msgId LIMIT 1")
    suspend fun getMessageById(msgId: String): Message?

    @Query("SELECT * FROM messages WHERE isPending = 1")
    suspend fun getPendingMessages(): List<Message>

    @Query("UPDATE messages SET isPending = 0 WHERE id = :msgId")
    suspend fun markMessageNotPending(msgId: String)
}

@Dao
interface UserDao {
    @Query("SELECT * FROM local_users WHERE phone = :phone LIMIT 1")
    suspend fun getUserByPhone(phone: String): LocalUser?

    @Query("SELECT * FROM local_users WHERE name LIKE '%' || :name || '%'")
    suspend fun searchUsersByName(name: String): List<LocalUser>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: LocalUser)

    @Update
    suspend fun updateUser(user: LocalUser)

    @Query("DELETE FROM local_users")
    suspend fun deleteAllUsers()
}

@Dao
interface StatusDao {
    @Query("SELECT * FROM statuses WHERE timestamp >= :cutoff ORDER BY timestamp DESC")
    fun getActiveStatuses(cutoff: Long): Flow<List<ChatStatus>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStatus(status: ChatStatus)

    @Query("SELECT * FROM statuses WHERE id = :statusId")
    suspend fun getStatusById(statusId: String): ChatStatus?

    @Query("DELETE FROM statuses WHERE timestamp < :cutoff")
    suspend fun pruneOldStatuses(cutoff: Long)

    @Query("DELETE FROM statuses WHERE id = :statusId")
    suspend fun deleteStatusById(statusId: String)

    @Query("DELETE FROM statuses")
    suspend fun deleteAllStatuses()
}

@Database(entities = [Contact::class, Message::class, LocalUser::class, ChatStatus::class], version = 6, exportSchema = false)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
    abstract fun messageDao(): MessageDao
    abstract fun userDao(): UserDao
    abstract fun statusDao(): StatusDao

    companion object {
        @Volatile
        private var INSTANCE: ChatDatabase? = null

        fun getInstance(context: Context): ChatDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ChatDatabase::class.java,
                    "barta_chat_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
