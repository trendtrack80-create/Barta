package com.example.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Base64
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.graphics.drawable.IconCompat
import com.example.MainActivity
import com.example.data.ChatDatabase
import com.example.data.Message
import com.example.data.Contact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object NotificationHelper {
    private const val CHANNEL_ID = "barta_chats_channel"
    private const val GROUP_KEY_CHATS = "com.example.BARTA_CHATS_GROUP"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "বার্তা কথোপকথন (Chats)"
            val descriptionText = "নতুন বার্তার নোটিফিকেশন"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableLights(true)
                lightColor = 0xFF25D366.toInt()
                enableVibration(true)
                vibrationPattern = longArrayOf(100, 200, 300, 400)
                setShowBadge(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
            }

            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                .build()
            channel.setSound(soundUri, audioAttributes)

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun decodeBase64ToBitmap(base64Str: String?): Bitmap? {
        if (base64Str.isNullOrBlank()) return null
        return try {
            val cleaned = if (base64Str.contains(",")) base64Str.substringAfter(",") else base64Str
            val decodedBytes = Base64.decode(cleaned, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun showNotification(
        context: Context,
        myPhone: String,
        chatPhone: String,
        senderPhone: String,
        senderName: String,
        messageText: String,
        isGroup: Boolean,
        groupName: String? = null,
        senderProfilePic: String? = null
    ) {
        createNotificationChannel(context)

        val db = ChatDatabase.getInstance(context)
        val sharedPrefs = context.getSharedPreferences("BartaChatPrefs", Context.MODE_PRIVATE)
        val appLanguage = sharedPrefs.getString("app_language", "en") ?: "en"
        val isBn = appLanguage == "bn"

        val hideContentOnLockScreen = sharedPrefs.getBoolean("hide_notification_content_lockscreen", false)

        val recentMsgs = withContext(Dispatchers.IO) {
            db.messageDao().getRecentMessagesForChat(myPhone, chatPhone, 6).reversed()
        }

        val unreadCount = withContext(Dispatchers.IO) {
            db.contactDao().getContactByPhone(chatPhone)?.unreadCount ?: 1
        }

        val chatTitle = if (isGroup) (groupName ?: "Group") else senderName

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("chat_phone", chatPhone)
            putExtra("is_group", isGroup)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            chatPhone.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val remoteInput = RemoteInput.Builder("key_text_reply")
            .setLabel(if (isBn) "উত্তর লিখুন..." else "Type a reply...")
            .build()

        val replyIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = "com.example.ACTION_REPLY"
            putExtra("chat_phone", chatPhone)
            putExtra("my_phone", myPhone)
            putExtra("sender_name", senderName)
            putExtra("notification_id", chatPhone.hashCode())
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            chatPhone.hashCode(),
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            if (isBn) "উত্তর দিন" else "Reply",
            replyPendingIntent
        ).addRemoteInput(remoteInput).build()

        val markReadIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = "com.example.ACTION_MARK_READ"
            putExtra("chat_phone", chatPhone)
            putExtra("notification_id", chatPhone.hashCode())
        }
        val markReadPendingIntent = PendingIntent.getBroadcast(
            context,
            chatPhone.hashCode() + 1,
            markReadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val markReadAction = NotificationCompat.Action.Builder(
            android.R.drawable.checkbox_on_background,
            if (isBn) "পঠিত হিসেবে চিহ্নিত করুন" else "Mark as Read",
            markReadPendingIntent
        ).build()

        val myBitmap = decodeBase64ToBitmap(sharedPrefs.getString("logged_user_profile_pic", null))
        val myIcon = if (myBitmap != null) IconCompat.createWithBitmap(myBitmap) else null
        val selfUser = Person.Builder()
            .setName(if (isBn) "আমি" else "Me")
            .setIcon(myIcon)
            .setKey(myPhone)
            .build()

        val peerBitmap = decodeBase64ToBitmap(senderProfilePic)
        val peerIcon = if (peerBitmap != null) IconCompat.createWithBitmap(peerBitmap) else null
        val peerUser = Person.Builder()
            .setName(senderName)
            .setIcon(peerIcon)
            .setKey(senderPhone)
            .build()

        val style = NotificationCompat.MessagingStyle(selfUser)
            .setConversationTitle(if (isGroup) chatTitle else null)
            .setGroupConversation(isGroup)

        if (recentMsgs.isNotEmpty() && !hideContentOnLockScreen) {
            for (msg in recentMsgs) {
                val isMe = msg.senderId == myPhone
                val user = if (isMe) selfUser else {
                    if (isGroup) {
                        Person.Builder()
                            .setName(msg.senderName.ifEmpty { msg.senderId })
                            .setKey(msg.senderId)
                            .build()
                    } else {
                        peerUser
                    }
                }
                val text = if (msg.mediaType != null) {
                    "[${msg.mediaType.replaceFirstChar { it.uppercase() }}]"
                } else {
                    msg.text
                }
                style.addMessage(text, msg.timestamp, user)
            }
        } else {
            val previewText = if (hideContentOnLockScreen) {
                if (isBn) "নতুন বার্তা এসেছে" else "New message received"
            } else {
                messageText
            }
            style.addMessage(previewText, System.currentTimeMillis(), peerUser)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle(chatTitle)
            .setContentText(messageText)
            .setStyle(style)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setGroup(GROUP_KEY_CHATS)
            .setNumber(unreadCount)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .addAction(replyAction)
            .addAction(markReadAction)

        if (peerBitmap != null) {
            builder.setLargeIcon(peerBitmap)
        }

        if (hideContentOnLockScreen) {
            builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        } else {
            builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        }

        withContext(Dispatchers.Main) {
            try {
                val notificationManager = NotificationManagerCompat.from(context)
                notificationManager.notify(chatPhone.hashCode(), builder.build())
                showGroupSummaryNotification(context, isBn, hideContentOnLockScreen)
            } catch (e: SecurityException) {
                // Ignore missing notification permission
            }
        }
    }

    private fun showGroupSummaryNotification(context: Context, isBn: Boolean, hideContentOnLockScreen: Boolean) {
        val summaryBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle(if (isBn) "বার্তা (Chat)" else "Barta (Chat)")
            .setContentText(if (isBn) "নতুন কথপোকথন বার্তা সমূহ" else "New messages in conversation")
            .setGroup(GROUP_KEY_CHATS)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        if (hideContentOnLockScreen) {
            summaryBuilder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        } else {
            summaryBuilder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        }

        try {
            NotificationManagerCompat.from(context).notify(0, summaryBuilder.build())
        } catch (e: SecurityException) {
            // Ignore
        }
    }

    fun dismissNotification(context: Context, chatPhone: String) {
        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.cancel(chatPhone.hashCode())
        } catch (e: Exception) {
            // Ignore
        }
    }
}
