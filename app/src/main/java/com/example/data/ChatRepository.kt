package com.example.data

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class ChatRepository(
    private val contactDao: ContactDao,
    private val messageDao: MessageDao,
    private val userDao: UserDao,
    private val context: Context
) {
    private var firestore: FirebaseFirestore? = null
    private var messageListener: ListenerRegistration? = null
    private val repositoryScope = CoroutineScope(Dispatchers.IO)
    private val sharedPrefs = context.getSharedPreferences("BartaChatPrefs", Context.MODE_PRIVATE)

    init {
        initializeFirebaseIfConfigured()
    }

    fun initializeFirebaseIfConfigured(): Boolean {
        return try {
            val apiKey = sharedPrefs.getString("firebase_api_key", "") ?: ""
            val projectId = sharedPrefs.getString("firebase_project_id", "") ?: ""
            val appId = sharedPrefs.getString("firebase_app_id", "") ?: ""

            if (apiKey.isNotEmpty() && projectId.isNotEmpty() && appId.isNotEmpty()) {
                val app = FirebaseApp.getApps(context).firstOrNull { it.name == "[DEFAULT]" } 
                    ?: FirebaseApp.initializeApp(
                        context,
                        FirebaseOptions.Builder()
                            .setApiKey(apiKey)
                            .setProjectId(projectId)
                            .setApplicationId(appId)
                            .build()
                    )
                firestore = FirebaseFirestore.getInstance(app)
                Log.d("BartaChat", "Firebase initialized successfully programmatically!")
                true
            } else {
                Log.d("BartaChat", "Firebase config missing. Using local-only high-fidelity simulator.")
                firestore = null
                false
            }
        } catch (e: Exception) {
            Log.e("BartaChat", "Error initializing Firebase programmatically", e)
            firestore = null
            false
        }
    }

    fun saveFirebaseConfig(apiKey: String, projectId: String, appId: String) {
        sharedPrefs.edit()
            .putString("firebase_api_key", apiKey.trim())
            .putString("firebase_project_id", projectId.trim())
            .putString("firebase_app_id", appId.trim())
            .apply()
        initializeFirebaseIfConfigured()
    }

    fun getFirebaseConfig(): Triple<String, String, String> {
        val apiKey = sharedPrefs.getString("firebase_api_key", "") ?: ""
        val projectId = sharedPrefs.getString("firebase_project_id", "") ?: ""
        val appId = sharedPrefs.getString("firebase_app_id", "") ?: ""
        return Triple(apiKey, projectId, appId)
    }

    fun clearFirebaseConfig() {
        sharedPrefs.edit()
            .remove("firebase_api_key")
            .remove("firebase_project_id")
            .remove("firebase_app_id")
            .apply()
        firestore = null
    }

    val allContacts: Flow<List<Contact>> = contactDao.getAllContacts()

    fun getMessages(user1: String, user2: String): Flow<List<Message>> {
        return messageDao.getMessagesForChat(user1, user2)
    }

    suspend fun getContact(phone: String): Contact? {
        return contactDao.getContactByPhone(phone)
    }

    suspend fun addContact(contact: Contact) {
        contactDao.insertContact(contact)
        if (firestore != null && !contact.isSimulated) {
            if (contact.isGroup) {
                syncGroupToFirestore(contact)
            } else {
                syncContactToFirestore(contact)
            }
        }
    }

    fun syncGroupToFirestore(contact: Contact) {
        val db = firestore ?: return
        val data = hashMapOf(
            "id" to contact.phone,
            "name" to contact.name,
            "participants" to contact.groupParticipants,
            "isGroup" to true,
            "createdAt" to System.currentTimeMillis()
        )
        db.collection("groups").document(contact.phone).set(data)
    }

    suspend fun updateGroup(groupId: String, newName: String, newParticipants: List<String>) {
        val currentGroup = contactDao.getContactByPhone(groupId) ?: return
        val participantPhones = newParticipants.joinToString(",")
        val updatedGroup = currentGroup.copy(
            name = newName.trim(),
            groupParticipants = participantPhones
        )
        contactDao.updateContact(updatedGroup)
        if (firestore != null) {
            syncGroupToFirestore(updatedGroup)
        }
    }

    suspend fun updateContact(contact: Contact) {
        contactDao.updateContact(contact)
    }

    suspend fun updateTypingStatus(phone: String, status: String) {
        contactDao.updateTypingStatus(phone, status)
    }

    suspend fun updateUnreadCount(phone: String, count: Int) {
        contactDao.updateUnreadCount(phone, count)
    }

    suspend fun sendMessage(
        sender: String,
        receiver: String,
        text: String,
        isSimulatedReceiver: Boolean,
        senderName: String = "",
        mediaUrl: String? = null,
        mediaType: String? = null
    ) {
        val messageId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        val message = Message(
            id = messageId,
            senderId = sender,
            receiverId = receiver,
            text = text,
            timestamp = timestamp,
            isRead = false,
            senderName = senderName,
            mediaUrl = mediaUrl,
            mediaType = mediaType
        )

        messageDao.insertMessage(message)
        contactDao.updateLastMessage(receiver, if (mediaType != null) "[${mediaType.replaceFirstChar { it.uppercase() }}]" else text, timestamp)

        if (isSimulatedReceiver) {
            triggerChatbotResponse(sender, receiver, text)
        } else {
            firestore?.let { db ->
                val chatId = getChatId(sender, receiver)
                val firestoreMsg = hashMapOf(
                    "id" to messageId,
                    "senderId" to sender,
                    "receiverId" to receiver,
                    "text" to text,
                    "timestamp" to timestamp,
                    "isRead" to false,
                    "senderName" to senderName,
                    "mediaUrl" to (mediaUrl ?: ""),
                    "mediaType" to (mediaType ?: ""),
                    "isDeletedForEveryone" to false
                )
                db.collection("chats")
                    .document(chatId)
                    .collection("messages")
                    .document(messageId)
                    .set(firestoreMsg)
                    .addOnSuccessListener {
                        Log.d("BartaChat", "Message synced to Firestore successfully!")
                    }
                    .addOnFailureListener { e ->
                        Log.e("BartaChat", "Error uploading message to Firestore", e)
                    }

                val chatMeta = hashMapOf(
                    "lastMessageText" to (if (mediaType != null) "[${mediaType.replaceFirstChar { it.uppercase() }}]" else text),
                    "lastMessageTime" to timestamp,
                    "lastSender" to sender,
                    "lastSenderName" to senderName
                )
                db.collection("chats")
                    .document(chatId)
                    .set(chatMeta)
            }
        }
    }

    suspend fun deleteMessageForMe(msgId: String) {
        messageDao.markDeletedForMe(msgId)
    }

    suspend fun deleteMessageForEveryone(myNumber: String, peerNumber: String, msgId: String) {
        messageDao.markDeletedForEveryone(msgId)
        firestore?.let { db ->
            val chatId = getChatId(myNumber, peerNumber)
            db.collection("chats")
                .document(chatId)
                .collection("messages")
                .document(msgId)
                .update("isDeletedForEveryone", true)
                .addOnSuccessListener {
                    Log.d("BartaChat", "Message marked deleted for everyone in Firestore")
                }
                .addOnFailureListener { e ->
                    Log.e("BartaChat", "Failed to mark message deleted for everyone", e)
                }
        }
    }

    private var groupsListener: ListenerRegistration? = null

    fun startListeningToGroups(myNumber: String, onGroupUpdated: () -> Unit) {
        groupsListener?.remove()
        val db = firestore ?: return
        
        groupsListener = db.collection("groups")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e("BartaChat", "Firestore groups listen failed", error)
                    return@addSnapshotListener
                }
                
                if (snapshots != null) {
                    repositoryScope.launch {
                        for (doc in snapshots.documentChanges) {
                            val data = doc.document.data
                            val id = data["id"] as? String ?: ""
                            val name = data["name"] as? String ?: ""
                            val participants = data["participants"] as? String ?: ""
                            
                            if (id.isNotEmpty()) {
                                val isParticipant = participants.split(",").contains(myNumber)
                                val currentContact = contactDao.getContactByPhone(id)
                                
                                if (isParticipant) {
                                    if (currentContact == null) {
                                        val newGroup = Contact(
                                            phone = id,
                                            name = name,
                                            isSimulated = false,
                                            isGroup = true,
                                            groupParticipants = participants,
                                            lastMessageText = "গ্রুপ চ্যাট তৈরি হয়েছে! চ্যাট শুরু করুন।",
                                            lastMessageTime = System.currentTimeMillis()
                                        )
                                        contactDao.insertContact(newGroup)
                                    } else {
                                        val updated = currentContact.copy(
                                            name = name,
                                            groupParticipants = participants
                                        )
                                        contactDao.insertContact(updated)
                                    }
                                } else {
                                    if (currentContact != null && currentContact.isGroup) {
                                        contactDao.deleteContact(currentContact)
                                        messageDao.deleteMessagesForChat(myNumber, id)
                                    }
                                }
                            }
                        }
                        withContext(Dispatchers.Main) {
                            onGroupUpdated()
                        }
                    }
                }
            }
    }

    fun stopListeningToGroups() {
        groupsListener?.remove()
        groupsListener = null
    }

    fun startListeningToChat(myNumber: String, peerNumber: String, onNewMessage: () -> Unit) {
        messageListener?.remove()
        
        val db = firestore ?: return
        val chatId = getChatId(myNumber, peerNumber)

        messageListener = db.collection("chats")
            .document(chatId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e("BartaChat", "Firestore listen failed", error)
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    repositoryScope.launch {
                        var hasNew = false
                        for (doc in snapshots.documentChanges) {
                            val data = doc.document.data
                            val id = data["id"] as? String ?: ""
                            val senderId = data["senderId"] as? String ?: ""
                            val receiverId = data["receiverId"] as? String ?: ""
                            val text = data["text"] as? String ?: ""
                            val timestamp = data["timestamp"] as? Long ?: System.currentTimeMillis()
                            val isRead = data["isRead"] as? Boolean ?: false
                            val senderName = data["senderName"] as? String ?: ""
                            val mediaUrl = data["mediaUrl"] as? String ?: ""
                            val mediaType = data["mediaType"] as? String ?: ""
                            val isDeletedForEveryone = data["isDeletedForEveryone"] as? Boolean ?: false

                            if (id.isNotEmpty()) {
                                val message = Message(
                                    id = id,
                                    senderId = senderId,
                                    receiverId = receiverId,
                                    text = text,
                                    timestamp = timestamp,
                                    isRead = isRead,
                                    senderName = senderName,
                                    mediaUrl = mediaUrl.ifEmpty { null },
                                    mediaType = mediaType.ifEmpty { null },
                                    isDeletedForEveryone = isDeletedForEveryone
                                )
                                messageDao.insertMessage(message)
                                
                                val contactPhone = if (peerNumber.startsWith("group_")) peerNumber else (if (senderId == myNumber) receiverId else senderId)
                                val lastMsgText = if (isDeletedForEveryone) "ঐ বার্তাটি মুছে ফেলা হয়েছে" else (if (mediaType.isNotEmpty()) "[$mediaType]" else text)
                                contactDao.updateLastMessage(contactPhone, lastMsgText, timestamp)
                                
                                if (senderId != myNumber) {
                                    val currentContact = contactDao.getContactByPhone(peerNumber)
                                    if (currentContact != null && doc.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                                        hasNew = true
                                    }
                                }
                            }
                        }
                        if (hasNew) {
                            withContext(Dispatchers.Main) {
                                onNewMessage()
                            }
                        }
                    }
                }
            }
    }

    fun stopListeningToChat() {
        messageListener?.remove()
        messageListener = null
    }

    fun getChatId(number1: String, number2: String): String {
        if (number2.startsWith("group_")) return number2
        if (number1.startsWith("group_")) return number1
        return if (number1 < number2) "${number1}_${number2}" else "${number2}_${number1}"
    }

    fun syncOwnProfileToFirestore(phone: String, name: String, status: String, profilePicBase64: String = "") {
        val db = firestore ?: return
        val data = hashMapOf(
            "phone" to phone,
            "name" to name,
            "status" to status,
            "profilePicBase64" to profilePicBase64,
            "isSimulated" to false
        )
        db.collection("users").document(phone).set(data)
    }

    // Local user auth and profile pic
    suspend fun registerLocalUser(phone: String, name: String, passwordHash: String, profilePicBase64: String): Boolean {
        if (getUserByPhone(phone) != null) return false
        val user = LocalUser(phone, name, passwordHash, profilePicBase64)
        userDao.insertUser(user)
        syncOwnProfileToFirestore(phone, name, "বার্তা (Chat) ব্যবহার করছি!", profilePicBase64)
        
        // Also add themselves or simulated bot contact
        return true
    }

    suspend fun getUserByPhone(phone: String): LocalUser? {
        return userDao.getUserByPhone(phone)
    }

    suspend fun searchUsers(query: String): List<LocalUser> {
        return userDao.searchUsersByName(query)
    }

    suspend fun updateUser(user: LocalUser) {
        userDao.updateUser(user)
        syncOwnProfileToFirestore(user.phone, user.name, user.status, user.profilePicBase64)
    }

    private fun syncContactToFirestore(contact: Contact) {
        val db = firestore ?: return
        val data = hashMapOf(
            "phone" to contact.phone,
            "name" to contact.name,
            "isSimulated" to false
        )
        db.collection("users").document(contact.phone).set(data)
    }

    private fun triggerChatbotResponse(userPhone: String, botPhone: String, userMessage: String) {
        repositoryScope.launch {
            contactDao.updateTypingStatus(botPhone, "typing...")
            kotlinx.coroutines.delay(1800)
            
            val userName = sharedPrefs.getString("logged_user_display_name", "")?.trim().let { if (it.isNullOrEmpty()) "বার্তা ব্যবহারকারী" else it }
            val userStatus = sharedPrefs.getString("logged_user_status_message", "বার্তা (Chat) ব্যবহার করছি!")?.trim().let { if (it.isNullOrEmpty()) "বার্তা (Chat) ব্যবহার করছি!" else it }

            val replyText = generateBotReply(userMessage, userName, userStatus)
            val messageId = UUID.randomUUID().toString()
            val timestamp = System.currentTimeMillis()
            val replyMessage = Message(
                id = messageId,
                senderId = botPhone,
                receiverId = userPhone,
                text = replyText,
                timestamp = timestamp,
                isRead = false
            )

            messageDao.insertMessage(replyMessage)
            contactDao.updateLastMessage(botPhone, replyText, timestamp)
            
            val contact = contactDao.getContactByPhone(botPhone)
            if (contact != null) {
                contactDao.updateUnreadCount(botPhone, contact.unreadCount + 1)
            }
            contactDao.updateTypingStatus(botPhone, "")
        }
    }

    private fun generateBotReply(msg: String, userName: String, userStatus: String): String {
        val clean = msg.trim().lowercase()
        return when {
            clean.contains("hello") || clean.contains("hi") || clean.contains("হ্যালো") || clean.contains("হাই") -> {
                "আসসালামু আলাইকুম $userName! কেমন আছেন? আপনার স্ট্যাটাসটি দেখছি: \"$userStatus\"। বার্তা (Chat) অ্যাপে আপনাকে স্বাগত! 🟢"
            }
            clean.contains("নাম") || clean.contains("name") -> {
                "আমি 'বার্তা' সহকারী। আপনার নামও আমি জানি, আপনি হলেন '$userName'! আপনার সাথে চ্যাট করতে পেরে ভালো লাগছে।"
            }
            clean.contains("স্ট্যাটাস") || clean.contains("status") -> {
                "আপনার বর্তমান স্ট্যাটাস মেসেজ হলো: \"$userStatus\"। এটি খুবই চমৎকার স্ট্যাটাস! 💬"
            }
            clean.contains("কেমন") -> {
                "আলহামদুলিল্লাহ $userName, আমি ভালো আছি। আপনার সাথে কথা বলতে দারুণ লাগছে! আপনি কেমন আছেন?"
            }
            clean.contains("ভালো") || clean.contains("valo") -> {
                "শুনে চমৎকার লাগলো $userName! আপনার দিনটি আনন্দময় হোক। 😊"
            }
            clean.contains("firebase") || clean.contains("ফায়ারবেস") -> {
                "হ্যাঁ! Profile এ গিয়ে আপনার নিজস্ব Firebase Config সেভ করলেই রিয়াল-টাইম ডিস্ট্রিবিউটেড মেসেজিং সচল হবে।"
            }
            clean.contains("ব") || clean.contains("বার্তা") || clean.contains("barta") -> {
                "বার্তা (Chat) হলো সম্পূর্ণ নেটিভ অ্যান্ড্রয়েড লাইভ চ্যাট প্ল্যাটফর্ম। 🇧🇩"
            }
            else -> {
                val replies = listOf(
                    "দারুণ কথা $userName! রিয়াল-টাইম মেসেজিং চমৎকার কাজ করছে। 👍",
                    "হোয়াটসঅ্যাপ স্টাইলের এই ইন্টারফেসটি আপনার সুবিধার জন্য বানানো হয়েছে।",
                    "আমি আপনার পাঠানো মেসেজটি পেয়েছি এবং সাথে সাথে উত্তর লিখেছি! 😊",
                    "বার্তা (Chat) অ্যাপটি পুরো রেসপন্সিভ এবং ব্যবহারের জন্য দারুণ সহজ।"
                )
                replies.random()
            }
        }
    }
}
