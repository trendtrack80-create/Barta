package com.example.notification

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.example.data.ChatDatabase
import com.example.data.ChatRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MyFirebaseMessagingService : FirebaseMessagingService() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("BartaFCM", "New token generated: $token")
        
        val sharedPrefs = getSharedPreferences("BartaChatPrefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("fcm_token", token).apply()

        val myNumber = sharedPrefs.getString("logged_phone", null)
        if (myNumber != null) {
            serviceScope.launch {
                try {
                    val db = ChatDatabase.getInstance(applicationContext)
                    val repo = ChatRepository(db.contactDao(), db.messageDao(), db.userDao(), db.statusDao(), applicationContext)
                    repo.initializeFirebaseIfConfigured()
                    
                    val firestore = FirebaseFirestore.getInstance()
                    val data = hashMapOf<String, Any>("fcmToken" to token)
                    firestore.collection("users").document(myNumber)
                        .set(data, com.google.firebase.firestore.SetOptions.merge())
                        .addOnSuccessListener {
                            Log.d("BartaFCM", "Successfully uploaded FCM token on token refresh")
                        }
                } catch (e: Exception) {
                    Log.e("BartaFCM", "Failed to upload FCM token on refresh", e)
                }
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d("BartaFCM", "Message received from FCM: ${remoteMessage.data}")

        val data = remoteMessage.data
        if (data.isEmpty()) return

        val sharedPrefs = getSharedPreferences("BartaChatPrefs", Context.MODE_PRIVATE)
        val myPhone = sharedPrefs.getString("logged_phone", null) ?: return

        val senderId = data["senderId"] ?: return
        val receiverId = data["receiverId"] ?: return
        val text = data["text"] ?: ""
        val senderName = data["senderName"] ?: senderId
        val chatId = data["chatId"] ?: senderId
        val isGroup = data["isGroup"]?.toBoolean() ?: chatId.startsWith("group_")
        val groupName = data["groupName"]
        val senderProfilePic = data["senderProfilePic"]

        val chatPhone = if (isGroup) chatId else senderId

        val activeChatPhone = sharedPrefs.getString("active_chat_phone", null)
        if (activeChatPhone == chatPhone) {
            Log.d("BartaFCM", "Suppressing notification because user is actively chatting in $chatPhone")
            return
        }

        val isSenderBlocked = sharedPrefs.getBoolean("is_blocked_$senderId", false)
        if (isSenderBlocked) {
            Log.d("BartaFCM", "Blocked sender $senderId. Suppressing notification.")
            return
        }

        serviceScope.launch {
            try {
                NotificationHelper.showNotification(
                    context = applicationContext,
                    myPhone = myPhone,
                    chatPhone = chatPhone,
                    senderPhone = senderId,
                    senderName = senderName,
                    messageText = text,
                    isGroup = isGroup,
                    groupName = groupName,
                    senderProfilePic = senderProfilePic
                )
            } catch (e: Exception) {
                Log.e("BartaFCM", "Error displaying FCM notification", e)
            }
        }
    }
}
