package com.example.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.example.data.ChatDatabase
import com.example.data.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {
    private val receiverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return

        val action = intent.action
        val chatPhone = intent.getStringExtra("chat_phone") ?: return
        val notificationId = intent.getIntExtra("notification_id", chatPhone.hashCode())

        val db = ChatDatabase.getInstance(context)
        val repo = ChatRepository(db.contactDao(), db.messageDao(), db.userDao(), db.statusDao(), context)

        when (action) {
            "com.example.ACTION_MARK_READ" -> {
                Log.d("BartaNotification", "ACTION_MARK_READ for chat $chatPhone")
                val pendingResult = goAsync()
                receiverScope.launch {
                    try {
                        repo.updateUnreadCount(chatPhone, 0)
                        NotificationManagerCompat.from(context).cancel(notificationId)
                    } catch (e: Exception) {
                        Log.e("BartaNotification", "Error marking read", e)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            "com.example.ACTION_REPLY" -> {
                val results = RemoteInput.getResultsFromIntent(intent)
                val replyText = results?.getCharSequence("key_text_reply")?.toString()
                val myPhone = intent.getStringExtra("my_phone") ?: ""
                val senderName = intent.getStringExtra("sender_name") ?: ""

                Log.d("BartaNotification", "ACTION_REPLY for chat $chatPhone, text: $replyText")

                if (!replyText.isNullOrBlank() && myPhone.isNotEmpty()) {
                    val pendingResult = goAsync()
                    receiverScope.launch {
                        try {
                            val isSimulated = chatPhone == "01300000000"
                            val contact = db.contactDao().getContactByPhone(chatPhone)
                            val isSimulatedContact = contact?.isSimulated ?: isSimulated

                            repo.sendMessage(
                                sender = myPhone,
                                receiver = chatPhone,
                                text = replyText,
                                isSimulatedReceiver = isSimulatedContact,
                                senderName = senderName
                            )

                            repo.updateUnreadCount(chatPhone, 0)
                            NotificationManagerCompat.from(context).cancel(notificationId)
                        } catch (e: Exception) {
                            Log.e("BartaNotification", "Error sending reply from notification", e)
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }
        }
    }
}
