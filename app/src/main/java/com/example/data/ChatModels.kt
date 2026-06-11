package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class Contact(
    @PrimaryKey val phone: String,
    val name: String,
    val isSimulated: Boolean = false,
    val lastSeen: String = "online",
    val unreadCount: Int = 0,
    val typingStatus: String = "",
    val lastMessageText: String? = null,
    val lastMessageTime: Long = 0L,
    val isGroup: Boolean = false,
    val groupParticipants: String = "", // serialized comma-separated phone numbers
    val profilePicUri: String = "" // base64 or contentURI
)

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey val id: String,
    val senderId: String,
    val receiverId: String,
    val text: String,
    val timestamp: Long,
    val isRead: Boolean = false,
    val senderName: String = "", // useful for displaying sender names in group chats
    val mediaUrl: String? = null,
    val mediaType: String? = null, // "image" or "video"
    val isDeletedForMe: Boolean = false,
    val isDeletedForEveryone: Boolean = false
)

@Entity(tableName = "local_users")
data class LocalUser(
    @PrimaryKey val phone: String,
    val name: String,
    val passwordHash: String,
    val profilePicBase64: String = "",
    val status: String = "online"
)
