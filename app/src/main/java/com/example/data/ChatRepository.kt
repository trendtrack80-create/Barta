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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.util.UUID

class ChatRepository(
    private val contactDao: ContactDao,
    private val messageDao: MessageDao,
    private val userDao: UserDao,
    private val statusDao: StatusDao,
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

    suspend fun searchUsers(query: String): List<LocalUser> {
        return userDao.searchUsersByName(query)
    }

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
        val me = sharedPrefs.getString("logged_user_phone", "") ?: ""
        val creator = sharedPrefs.getString("group_creator_" + contact.phone, "")?.ifEmpty { me } ?: me
        
        if (creator.isNotEmpty()) {
            sharedPrefs.edit().putString("group_creator_" + contact.phone, creator).apply()
        }

        val data = hashMapOf(
            "id" to contact.phone,
            "name" to contact.name,
            "participants" to contact.groupParticipants,
            "isGroup" to true,
            "creator" to creator,
            "createdAt" to System.currentTimeMillis()
        )
        db.collection("groups").document(contact.phone).set(data, com.google.firebase.firestore.SetOptions.merge())
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
                            val creator = data["creator"] as? String ?: ""
                            
                            if (id.isNotEmpty()) {
                                if (creator.isNotEmpty()) {
                                    sharedPrefs.edit().putString("group_creator_$id", creator).apply()
                                }
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

    fun syncOwnProfileToFirestore(phone: String, name: String, status: String, profilePicBase64: String = "", passwordHash: String = "") {
        val db = firestore ?: return
        val data = hashMapOf<String, Any>(
            "phone" to phone,
            "name" to name,
            "status" to status,
            "profilePicBase64" to profilePicBase64,
            "isSimulated" to false
        )
        if (passwordHash.isNotEmpty()) {
            data["passwordHash"] = passwordHash
        }
        db.collection("users").document(phone).set(data, com.google.firebase.firestore.SetOptions.merge())
    }

    suspend fun getUserFromFirestore(phone: String): Map<String, Any>? = suspendCancellableCoroutine { continuation ->
        val db = firestore
        if (db == null) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        db.collection("users").document(phone).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    continuation.resume(document.data)
                } else {
                    continuation.resume(null)
                }
            }
            .addOnFailureListener {
                continuation.resume(null)
            }
    }

    suspend fun insertLocalUserDirectly(user: LocalUser) {
        userDao.insertUser(user)
    }

    // Local user auth and profile pic
    suspend fun registerLocalUser(phone: String, name: String, passwordHash: String, profilePicBase64: String): Boolean {
        if (getUserByPhone(phone) != null) return false
        val user = LocalUser(phone, name, passwordHash, profilePicBase64)
        userDao.insertUser(user)
        syncOwnProfileToFirestore(phone, name, "বার্তা (Chat) ব্যবহার করছি!", profilePicBase64, passwordHash)
        
        // Also add themselves or simulated bot contact
        return true
    }

    suspend fun getUserByPhone(phone: String): LocalUser? {
        return userDao.getUserByPhone(phone)
    }

    suspend fun updateUser(user: LocalUser) {
        userDao.updateUser(user)
        syncOwnProfileToFirestore(user.phone, user.name, user.status, user.profilePicBase64, user.passwordHash)
    }

    private fun syncContactToFirestore(contact: Contact) {
        val db = firestore ?: return
        val data = hashMapOf(
            "phone" to contact.phone,
            "name" to contact.name,
            "profilePicUri" to contact.profilePicUri,
            "isSimulated" to contact.isSimulated
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
                senderName = "বার্তা সহকারী (Bot) 🤖"
            )
            messageDao.insertMessage(replyMessage)
            contactDao.updateLastMessage(botPhone, replyText, timestamp)
            contactDao.updateTypingStatus(botPhone, "")
        }
    }

    private fun generateBotReply(msg: String, userName: String, userStatus: String): String {
        val clean = msg.trim()
            .replace("?", "")
            .replace("।", "")
            .replace(",", "")
            .replace("!", "")
            .replace("-", "")
            .replace(".", "")
            .replace("\r", "")
            .replace("\n", " ")
            .lowercase()

        val noSpaces = clean.replace(" ", "")

        return when {
            // 1. অ্যাপের নাম কী?
            noSpaces.contains("অ্যাপেরনাম") || noSpaces.contains("অ্যাপটারনাম") || noSpaces.contains("অ্যাপটিরনাম") ||
            clean.contains("অ্যাপের নাম") || clean.contains("অ্যাপটার নাম") || clean.contains("অ্যাপটির নাম") || 
            (clean.contains("নাম") && (clean.contains("কী") || clean.contains("কি"))) -> {
                "আমার অ্যাপের নাম বার্তা (Chat)। এটা একটা সহজ এবং স্মার্ট চ্যাট অ্যাপ।"
            }

            // 13. এই অ্যাপটি কেমন আছে? (Check this before other general greeters)
            noSpaces.contains("অ্যাপটিকেমনি") || noSpaces.contains("অ্যাপটাকেমনি") || 
            noSpaces.contains("কেমনআছে") || clean.contains("কেমন আছে") -> {
                if (clean.contains("কেমন আছো") || clean.contains("কেমন আছ") || clean.contains("কেমন আছেন")) {
                    "আসসালামু আলাইকুম $userName! কেমন আছেন? আমি বার্তা AI সহকারী। বার্তা (Chat) অ্যাপে আপনাকে স্বাগত! আমি মূলত এই অ্যাপ সংক্রান্ত প্রশ্নের উত্তর দিতে পারি। দয়া করে অ্যাপটি সম্পর্কে কোনো প্রশ্ন থাকলে আমাকে জিজ্ঞাসা করুন।"
                } else {
                    "এই অ্যাপটা ইয়সির আরাফাত সৌখিন এবং অন্নদাশঙ্কর উৎসব মিলে তৈরি করেছে। অ্যাপটার নাম বার্তা (Chat)। তারা চেয়েছিলো যেন স্টুডেন্টরা সহজে, distraction ছাড়া এবং নিরাপদভাবে চ্যাট করতে পারে। অ্যাপটায় রিয়াল-টাইম মেসেজিং, ছবি-ভিডিও পাঠানো, গ্রুপ তৈরি করা এবং মেসেজ ডিলিট করার সুবিধা আছে। এটাকে তারা শুধু একটা প্রজেক্ট হিসেবে না দেখে, একটা ছোট স্টার্টআপ আইডিয়া হিসেবে দেখছে।"
                }
            }

            // 12. এই অ্যাপটা কে বানিয়েছে?
            noSpaces.contains("কেবানিয়েছে") || noSpaces.contains("কেবানিয়েছে") || 
            noSpaces.contains("কারাবানিয়েছে") || noSpaces.contains("কারাবানিয়েছে") || 
            clean.contains("কেবানিয়েছে") || clean.contains("কেবানিয়েছে") ||
            clean.contains("কে বানিয়েছে") || clean.contains("কে বানিয়েছে") || clean.contains("কে বানাল") || 
            clean.contains("কারা বানিয়েছে") || clean.contains("কারা বানিয়েছে") || clean.contains("কারা তৈরি") || 
            clean.contains("কে তৈরি") || clean.contains("কারা বানাল") -> {
                "এই অ্যাপটা আমি ইয়সির আরাফাত সৌখিন এবং অন্নদাশঙ্কর উৎসব মিলে যৌথভাবে তৈরি করেছে।"
            }

            // 11. এই অ্যাপটা কেন বানালে?
            noSpaces.contains("কেনবানালে") || noSpaces.contains("কেনবানিয়েছ") || noSpaces.contains("কেনবানিয়েছো") ||
            clean.contains("কেন বানালে") || clean.contains("কেন বানিয়েছ") || clean.contains("কেন বানালেন") || 
            clean.contains("কেন বানিয়েছো") || clean.contains("কেন তৈরি") || clean.contains("উদ্দেশ্য") -> {
                "তারা দেখতে চেয়েছিলো যেন স্টুডেন্টরা সহজে এবং distraction ছাড়া চ্যাট করতে পারে। তাই এই অ্যাপটা বানিয়েছে।"
            }

            // 10. এই প্রজেক্টে তুমি কী শিখেছো?
            noSpaces.contains("কীশিখেছ") || noSpaces.contains("কিশিখেছ") || 
            clean.contains("কী শিখেছো") || clean.contains("কী শিখেছ") || clean.contains("কি শিখেছ") || 
            clean.contains("কি শিখেছো") || clean.contains("কি শিখলে") || clean.contains("কি শিখলা") || 
            clean.contains("শিখেছ") || clean.contains("শিখেছো") || clean.contains("learn") -> {
                "আমি শিখেছি কীভাবে AI টুল ব্যবহার করে দ্রুত একটা পূর্ণাঙ্গ অ্যাপ বানানো যায় এবং Firebase দিয়ে রিয়েল-টাইম চ্যাট সিস্টেম তৈরি করা যায়।"
            }

            // 9. ভবিষ্যতে আর কী কী ফিচার যোগ করা হবে?
            noSpaces.contains("ভবিষ্যতে") || noSpaces.contains("ভবিষ্যত") ||
            clean.contains("ভবিষ্যতে") || clean.contains("ভবিষ্যত") || clean.contains("আপডেট") || clean.contains("update") -> {
                "ভবিষ্যতে আমরা যোগ করতে চাই ভয়েস মেসেজ, স্ট্যাটাস ফিচার এবং স্টুডেন্টদের জন্য আরও ভালো স্টাডি গ্রুপের সুবিধা।"
            }

            // 8. এটা কি ফ্রি?
            noSpaces.contains("ফ্রি") || noSpaces.contains("free") || clean.contains("ফ্রি") || clean.contains("free") ||
            clean.contains("টাকা") || clean.contains("পয়সা") || clean.contains("মূল্য") || clean.contains("খরচ") || clean.contains("পেইড") -> {
                "হ্যাঁ, এই অ্যাপটা সম্পূর্ণ ফ্রি। কোনো টাকা লাগবে না। শুধু মোবাইল নম্বর দিয়ে লগইন করলেই চলবে।"
            }

            // 7. মেসেজ ডিলিট করা যায়?
            noSpaces.contains("ডিলিটকরা") || noSpaces.contains("deleteকরা") ||
            clean.contains("ডিলিট") || clean.contains("delete") || clean.contains("মুছে") || clean.contains("ডেলিট") -> {
                "হ্যাঁ। তুমি যে মেসেজ পাঠিয়েছো, সেটা Delete for me এবং Delete for everyone — দুটোই করতে পারবে। আর অন্যের পাঠানো মেসেজ শুধু Delete for me করা যাবে।"
            }

            // 6. গ্রুপ চ্যাটে কী সুবিধা আছে?
            noSpaces.contains("গ্রুপচ্যাট") || (clean.contains("গ্রুপ") && (clean.contains("সুবিধা") || clean.contains("ফিচার") || clean.contains("চ্যাট") || clean.contains("কমিউনিটি"))) -> {
                "তুমি গ্রুপ তৈরি করতে পারবে। যে প্রথমে গ্রুপ তৈরি করবে সে অ্যাডমিন হবে। অ্যাডমিন গ্রুপের নাম, ছবি, মেম্বার যোগ-বিয়োগ করতে পারবে।"
            }

            // 5. এটা কারা ব্যবহার করতে পারে?
            noSpaces.contains("কারাব্যবহার") || noSpaces.contains("কাদেরজন্য") ||
            clean.contains("কারা ব্যবহার") || clean.contains("কাদের জন্য") || clean.contains("কারা ইউজ") || clean.contains("কারা ব্যবহার করতে পারে") -> {
                "যেকোনো বয়সের মানুষ ব্যবহার করতে পারে। তবে বিশেষ করে স্কুল-কলেজের স্টুডেন্ট, গ্রুপ স্টাডি, প্রজেক্ট ও ক্লাসের আলোচনার জন্য এটা খুব ভালো।"
            }

            // 4. এটা Facebook বা WhatsApp থেকে আলাদা কেন?
            noSpaces.contains("facebook") || noSpaces.contains("whatsapp") || noSpaces.contains("আলাদাকেন") ||
            clean.contains("facebook") || clean.contains("whatsapp") || clean.contains("ফেসবুক") || 
            clean.contains("ফেইসবুক") || clean.contains("হোয়াটসঅ্যাপ") || clean.contains("হোয়াটসঅ্যাপ") || clean.contains("আলাদা") -> {
                "Facebook-এ অনেক distraction থাকে। WhatsApp-এ অনেক ফিচার আছে। আমাদের অ্যাপ শুধু সহজ চ্যাটের উপর ফোকাস করেছে। এতে কোনো অপ্রয়োজনীয় ফিচার নেই, তাই স্টুডেন্টরা সহজে ব্যবহার করতে পারবে।"
            }

            // 3. এটা ব্যবহার করে কী কী সুবিধা পেতে পারি?
            noSpaces.contains("সুবিধাপেতে") || noSpaces.contains("সুবিধাপাব") ||
            clean.contains("সুবিধা") || clean.contains("উপকার") || clean.contains("বেনিফিট") || clean.contains("benefit") -> {
                "এই অ্যাপ ব্যবহার করে তুমি distraction-free চ্যাট করতে পারবে, কম ইন্টারнеটে ভালো কাজ করবে, গ্রুপ স্টাডি ও প্রজেক্টের জন্য গ্রুপ চ্যাট করতে পারবে এবং সহজ ইন্টারফেসে ব্যবহার করতে পারবে। বিশেষ করে স্টুডেন্টদের জন্য খুব উপযোগী।"
            }

            // 2. অ্যাপের ফিচারস কী কী?
            noSpaces.contains("ফিচার") || noSpaces.contains("feature") ||
            clean.contains("ফিচার") || clean.contains("feature") -> {
                "বার্তা অ্যাপে আছে রিয়েল-টাইম মেসেজিং, ছবি ও ভিডিও পাঠানো, গ্রুপ তৈরি করা, মেসেজ ডিলিট করা (Delete for me এবং Delete for everyone), প্রোফাইল এডিট করা এবং সহজ চ্যাট করার সুবিধা।"
            }

            // Greeting fallback check
            clean.contains("hello") || clean.contains("hi") || clean.contains("হ্যালো") || clean.contains("হাই") || 
            clean.contains("কেমন আছ") || clean.contains("কেমন আছো") || clean.contains("কেমন আছেন") -> {
                "আসসালামু আলাইকুম $userName! কেমন আছেন? আমি বার্তা AI সহকারী। বার্তা (Chat) অ্যাপে আপনাকে স্বাগত! আমি মূলত এই অ্যাপ সংক্রান্ত প্রশ্নের উত্তর দিতে পারি। দয়া করে অ্যাপটি সম্পর্কে কোনো প্রশ্ন থাকলে আমাকে জিজ্ঞাসা করুন।"
            }

            else -> {
                "আমি শুধুমাত্র বার্তা অ্যাপ সংক্রান্ত প্রশ্নের উত্তর দিতে পারি। দয়া করে অ্যাপটি সম্পর্কে কোনো প্রশ্ন থাকলে আমাকে জিজ্ঞাসা করো।"
            }
        }
    }

    fun getActiveStatusesFlow(): Flow<List<ChatStatus>> {
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000
        repositoryScope.launch {
            statusDao.pruneOldStatuses(cutoff)
        }
        return statusDao.getActiveStatuses(cutoff)
    }

    suspend fun postStatus(text: String, mediaUrl: String?, bgColorVal: Long) {
        val myPhone = sharedPrefs.getString("logged_user_phone", "") ?: ""
        val myName = sharedPrefs.getString("logged_user_display_name", "") ?: ""
        val myAvatar = sharedPrefs.getString("logged_user_profile_pic", "") ?: ""

        if (myPhone.isEmpty()) return

        val id = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        val status = ChatStatus(
            id = id,
            phone = myPhone,
            name = myName,
            avatar = myAvatar,
            text = text,
            mediaUrl = mediaUrl,
            timestamp = timestamp,
            bgColorVal = bgColorVal
        )

        statusDao.insertStatus(status)

        firestore?.let { db ->
            val data = hashMapOf(
                "id" to id,
                "phone" to myPhone,
                "name" to myName,
                "avatar" to myAvatar,
                "text" to text,
                "mediaUrl" to (mediaUrl ?: ""),
                "timestamp" to timestamp,
                "bgColorVal" to bgColorVal
            )
            db.collection("statuses").document(id).set(data)
                .addOnSuccessListener {
                    Log.d("BartaChat", "Status synchronized to Firestore successfully!")
                }
                .addOnFailureListener { e ->
                    Log.e("BartaChat", "Failed to upload status to Firestore", e)
                }
        }
    }

    private var statusListenerRegistration: ListenerRegistration? = null

    fun startListeningToStatuses(onStatusChanged: () -> Unit) {
        statusListenerRegistration?.remove()
        val db = firestore ?: return

        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000
        statusListenerRegistration = db.collection("statuses")
            .whereGreaterThan("timestamp", cutoff)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e("BartaChat", "Firestore statuses listen failed", error)
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    repositoryScope.launch {
                        for (doc in snapshots.documentChanges) {
                            val data = doc.document.data
                            val id = data["id"] as? String ?: ""
                            val phone = data["phone"] as? String ?: ""
                            val name = data["name"] as? String ?: ""
                            val avatar = data["avatar"] as? String ?: ""
                            val text = data["text"] as? String ?: ""
                            val mediaUrl = data["mediaUrl"] as? String ?: ""
                            val timestamp = data["timestamp"] as? Long ?: System.currentTimeMillis()
                            val bgColorVal = data["bgColorVal"] as? Long ?: 0xFF00897BL

                            if (id.isNotEmpty()) {
                                val status = ChatStatus(
                                    id = id,
                                    phone = phone,
                                    name = name,
                                    avatar = avatar,
                                    text = text,
                                    mediaUrl = mediaUrl.ifEmpty { null },
                                    timestamp = timestamp,
                                    bgColorVal = bgColorVal
                                )
                                statusDao.insertStatus(status)
                            }
                        }
                        withContext(Dispatchers.Main) {
                            onStatusChanged()
                        }
                    }
                }
            }
    }

    fun stopListeningToStatuses() {
        statusListenerRegistration?.remove()
        statusListenerRegistration = null
    }
}
