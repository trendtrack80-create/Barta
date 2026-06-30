package com.example.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
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
    private var firebaseStorage: com.google.firebase.storage.FirebaseStorage? = null
    private var messageListener: ListenerRegistration? = null
    private val activeMessageListeners = HashMap<String, ListenerRegistration>()
    private var globalChatsListener: ListenerRegistration? = null
    private var activeChatPhone: String? = null
    private val repositoryScope = CoroutineScope(Dispatchers.IO)
    private val sharedPrefs = context.getSharedPreferences("BartaChatPrefs", Context.MODE_PRIVATE)

    fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun syncPendingMessages() {
        if (!isNetworkAvailable()) return
        repositoryScope.launch {
            try {
                val pending = messageDao.getPendingMessages()
                for (msg in pending) {
                    val chatId = getChatId(msg.senderId, msg.receiverId)
                    val firestoreMsg = hashMapOf(
                        "id" to msg.id,
                        "senderId" to msg.senderId,
                        "receiverId" to msg.receiverId,
                        "text" to msg.text,
                        "timestamp" to msg.timestamp,
                        "isRead" to msg.isRead,
                        "senderName" to msg.senderName,
                        "mediaUrl" to (msg.mediaUrl ?: ""),
                        "mediaType" to (msg.mediaType ?: ""),
                        "isDeletedForEveryone" to msg.isDeletedForEveryone
                    )
                    firestore?.collection("chats")
                        ?.document(chatId)
                        ?.collection("messages")
                        ?.document(msg.id)
                        ?.set(firestoreMsg)
                        ?.addOnSuccessListener {
                            repositoryScope.launch {
                                messageDao.markMessageNotPending(msg.id)
                                Log.d("BartaChat", "Synced pending message: ${msg.id}")
                            }
                        }
                }
            } catch (e: Exception) {
                Log.e("BartaChat", "Failed syncing pending messages", e)
            }
        }
    }

    init {
        initializeFirebaseIfConfigured()
    }

    fun initializeFirebaseIfConfigured(): Boolean {
        return try {
            var apiKey = sharedPrefs.getString("firebase_api_key", "") ?: ""
            var projectId = sharedPrefs.getString("firebase_project_id", "") ?: ""
            var appId = sharedPrefs.getString("firebase_app_id", "") ?: ""

            if (apiKey.isEmpty() || projectId.isEmpty() || appId.isEmpty()) {
                apiKey = com.example.BuildConfig.FIREBASE_API_KEY
                projectId = "barta-chat-927ec"
                appId = "1:799684230284:web:3322149ca4ff4c91594fa9"
                
                sharedPrefs.edit()
                    .putString("firebase_api_key", apiKey)
                    .putString("firebase_project_id", projectId)
                    .putString("firebase_app_id", appId)
                    .apply()
                Log.d("BartaChat", "Default custom Firebase config initialized programmatically!")
            }

            if (apiKey.isNotEmpty() && projectId.isNotEmpty() && appId.isNotEmpty()) {
                val builder = FirebaseOptions.Builder()
                    .setApiKey(apiKey)
                    .setProjectId(projectId)
                    .setApplicationId(appId)

                if (projectId == "barta-chat-927ec") {
                    builder.setStorageBucket("barta-chat-927ec.firebasestorage.app")
                    builder.setDatabaseUrl("https://barta-chat-927ec-default-rtdb.asia-southeast1.firebasedatabase.app")
                } else {
                    builder.setStorageBucket("$projectId.appspot.com")
                }

                val app = FirebaseApp.getApps(context).firstOrNull { it.name == "[DEFAULT]" } 
                    ?: FirebaseApp.initializeApp(
                        context,
                        builder.build()
                    )
                firestore = FirebaseFirestore.getInstance(app)
                firebaseStorage = com.google.firebase.storage.FirebaseStorage.getInstance(app)
                Log.d("BartaChat", "Firebase initialized successfully programmatically!")
                true
            } else {
                Log.d("BartaChat", "Firebase config missing. Using local-only high-fidelity simulator.")
                firestore = null
                firebaseStorage = null
                false
            }
        } catch (e: Exception) {
            Log.e("BartaChat", "Error initializing Firebase programmatically", e)
            firestore = null
            firebaseStorage = null
            false
        }
    }

    suspend fun uploadFileToStorage(file: java.io.File): String = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        val storage = firebaseStorage
        if (storage != null) {
            val ref = storage.reference.child("chat_images/${UUID.randomUUID()}.jpg")
            val uri = android.net.Uri.fromFile(file)
            ref.putFile(uri)
                .addOnSuccessListener { taskSnapshot ->
                    ref.downloadUrl.addOnSuccessListener { downloadUri ->
                        if (continuation.isActive) {
                            continuation.resume(downloadUri.toString())
                        }
                    }.addOnFailureListener { e ->
                        if (continuation.isActive) {
                            continuation.resume(uri.toString())
                        }
                    }
                }
                .addOnFailureListener { e ->
                    if (continuation.isActive) {
                        continuation.resume(uri.toString())
                    }
                }
        } else {
            // Fallback to local image representation
            val uriStr = android.net.Uri.fromFile(file).toString()
            if (continuation.isActive) {
                continuation.resume(uriStr)
            }
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
        var apiKey = sharedPrefs.getString("firebase_api_key", "") ?: ""
        var projectId = sharedPrefs.getString("firebase_project_id", "") ?: ""
        var appId = sharedPrefs.getString("firebase_app_id", "") ?: ""
        if (apiKey.isEmpty() || projectId.isEmpty() || appId.isEmpty()) {
            apiKey = com.example.BuildConfig.FIREBASE_API_KEY
            projectId = "barta-chat-927ec"
            appId = "1:799684230284:web:3322149ca4ff4c91594fa9"
            sharedPrefs.edit()
                .putString("firebase_api_key", apiKey)
                .putString("firebase_project_id", projectId)
                .putString("firebase_app_id", appId)
                .apply()
        }
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

        val owner = sharedPrefs.getString("group_owner_" + contact.phone, creator)?.ifEmpty { creator } ?: creator
        val admins = sharedPrefs.getString("group_admins_" + contact.phone, "") ?: ""
        val adminsCanEditInfo = sharedPrefs.getBoolean("group_admins_can_edit_info_" + contact.phone, true)
        val adminsCanPinMessages = sharedPrefs.getBoolean("group_admins_can_pin_messages_" + contact.phone, true)
        val membersCanEditInfo = sharedPrefs.getBoolean("group_members_can_edit_info_" + contact.phone, false)
        val description = sharedPrefs.getString("group_description_" + contact.phone, "") ?: ""
        val pinnedMessageId = sharedPrefs.getString("pinned_message_id_" + contact.phone, "") ?: ""

        val data = hashMapOf(
            "id" to contact.phone,
            "name" to contact.name,
            "participants" to contact.groupParticipants,
            "isGroup" to true,
            "creator" to creator,
            "owner" to owner,
            "admins" to admins,
            "admins_can_edit_info" to adminsCanEditInfo,
            "admins_can_pin_messages" to adminsCanPinMessages,
            "members_can_edit_info" to membersCanEditInfo,
            "description" to description,
            "pinned_message_id" to pinnedMessageId,
            "createdAt" to System.currentTimeMillis()
        )
        db.collection("groups").document(contact.phone).set(data, com.google.firebase.firestore.SetOptions.merge())
    }

    suspend fun updateGroup(
        groupId: String,
        newName: String,
        newParticipants: List<String>,
        profilePicUri: String? = null,
        description: String? = null,
        owner: String? = null,
        admins: String? = null,
        adminsCanEditInfo: Boolean? = null,
        adminsCanPinMessages: Boolean? = null,
        membersCanEditInfo: Boolean? = null
    ) {
        val currentGroup = contactDao.getContactByPhone(groupId) ?: return
        val participantPhones = newParticipants.joinToString(",")

        val editor = sharedPrefs.edit()
        if (description != null) {
            editor.putString("group_description_$groupId", description)
        }
        if (owner != null) {
            editor.putString("group_owner_$groupId", owner)
            editor.putString("group_creator_$groupId", owner)
        }
        if (admins != null) {
            editor.putString("group_admins_$groupId", admins)
        }
        if (adminsCanEditInfo != null) {
            editor.putBoolean("group_admins_can_edit_info_$groupId", adminsCanEditInfo)
        }
        if (adminsCanPinMessages != null) {
            editor.putBoolean("group_admins_can_pin_messages_$groupId", adminsCanPinMessages)
        }
        if (membersCanEditInfo != null) {
            editor.putBoolean("group_members_can_edit_info_$groupId", membersCanEditInfo)
        }
        editor.apply()

        val updatedGroup = currentGroup.copy(
            name = newName.trim(),
            groupParticipants = participantPhones,
            profilePicUri = profilePicUri ?: currentGroup.profilePicUri
        )
        contactDao.updateContact(updatedGroup)
        if (firestore != null) {
            syncGroupToFirestore(updatedGroup)
        }
    }

    suspend fun deleteGroup(groupId: String, myNumber: String) {
        val owner = sharedPrefs.getString("group_owner_$groupId", "")?.ifEmpty {
            sharedPrefs.getString("group_creator_$groupId", "") ?: ""
        } ?: ""
        if (owner.isNotEmpty() && owner != myNumber) {
            return
        }
        val group = contactDao.getContactByPhone(groupId) ?: return
        contactDao.deleteContact(group)
        messageDao.deleteMessagesForChat(myNumber, groupId)
        val db = firestore ?: return
        db.collection("groups").document(groupId).delete()
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
        val isOffline = !isNetworkAvailable()
        val isPendingVal = isOffline && !isSimulatedReceiver

        val message = Message(
            id = messageId,
            senderId = sender,
            receiverId = receiver,
            text = text,
            timestamp = timestamp,
            isRead = false,
            senderName = senderName,
            mediaUrl = mediaUrl,
            mediaType = mediaType,
            isPending = isPendingVal
        )

        messageDao.insertMessage(message)
        contactDao.updateLastMessage(receiver, if (mediaType != null) "[${mediaType.replaceFirstChar { it.uppercase() }}]" else text, timestamp)

        if (isOffline) {
            if (isSimulatedReceiver) {
                // If the user wants to chat with the bot while offline, let the bot reply with a clear offline notice
                triggerChatbotResponse(sender, receiver, text)
            }
            Log.d("BartaChat", "Message saved locally as pending since we are offline.")
            return
        }

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

                val chatParticipants = if (receiver.startsWith("group_")) {
                    val contact = contactDao.getContactByPhone(receiver)
                    val list = contact?.groupParticipants?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: listOf(sender, receiver)
                    (list + sender).distinct()
                } else {
                    listOf(sender, receiver)
                }

                val chatMeta = hashMapOf(
                    "id" to chatId,
                    "lastMessageText" to (if (mediaType != null) "[${mediaType.replaceFirstChar { it.uppercase() }}]" else text),
                    "lastMessageTime" to timestamp,
                    "lastSender" to sender,
                    "lastSenderName" to senderName,
                    "participants" to chatParticipants
                )
                db.collection("chats")
                    .document(chatId)
                    .set(chatMeta, com.google.firebase.firestore.SetOptions.merge())
            }
        }
    }

    suspend fun forwardMessage(
        sender: String,
        receiver: String,
        text: String,
        isSimulatedReceiver: Boolean,
        senderName: String = "",
        mediaUrl: String? = null,
        mediaType: String? = null
    ) {
        val messageId = java.util.UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        val isOffline = !isNetworkAvailable()
        val isPendingVal = isOffline && !isSimulatedReceiver

        val message = Message(
            id = messageId,
            senderId = sender,
            receiverId = receiver,
            text = text,
            timestamp = timestamp,
            isRead = false,
            senderName = senderName,
            mediaUrl = mediaUrl,
            mediaType = mediaType,
            isPending = isPendingVal,
            isForwarded = true
        )

        messageDao.insertMessage(message)
        contactDao.updateLastMessage(receiver, if (mediaType != null) "[${mediaType.replaceFirstChar { it.uppercase() }}] (Forwarded)" else text, timestamp)

        if (isOffline) {
            if (isSimulatedReceiver) {
                triggerChatbotResponse(sender, receiver, text)
            }
            return
        }

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
                    "isDeletedForEveryone" to false,
                    "isForwarded" to true
                )
                db.collection("chats")
                    .document(chatId)
                    .collection("messages")
                    .document(messageId)
                    .set(firestoreMsg)

                val chatParticipants = if (receiver.startsWith("group_")) {
                    val contact = contactDao.getContactByPhone(receiver)
                    val list = contact?.groupParticipants?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: listOf(sender, receiver)
                    (list + sender).distinct()
                } else {
                    listOf(sender, receiver)
                }

                val chatMeta = hashMapOf(
                    "id" to chatId,
                    "lastMessageText" to (if (mediaType != null) "[${mediaType.replaceFirstChar { it.uppercase() }}] (Forwarded)" else text),
                    "lastMessageTime" to timestamp,
                    "lastSender" to sender,
                    "lastSenderName" to senderName,
                    "participants" to chatParticipants
                )
                db.collection("chats")
                    .document(chatId)
                    .set(chatMeta, com.google.firebase.firestore.SetOptions.merge())
            }
        }
    }

    suspend fun deleteMessageForMe(msgId: String) {
        messageDao.markDeletedForMe(msgId)
    }

    suspend fun deleteMessageForEveryone(myNumber: String, peerNumber: String, msgId: String, deleterName: String = "User") {
        val msgText = "Deleted by: $deleterName"
        messageDao.markDeletedForEveryone(msgId)
        messageDao.updateMessageText(msgId, msgText)
        firestore?.let { db ->
            val chatId = getChatId(myNumber, peerNumber)
            db.collection("chats")
                .document(chatId)
                .collection("messages")
                .document(msgId)
                .update(
                    "isDeletedForEveryone", true,
                    "text", msgText
                )
                .addOnSuccessListener {
                    Log.d("BartaChat", "Message marked deleted for everyone in Firestore")
                }
                .addOnFailureListener { e ->
                    Log.e("BartaChat", "Failed to mark message deleted for everyone", e)
                }
        }
    }

    suspend fun editMessage(myNumber: String, peerNumber: String, msgId: String, newText: String) {
        messageDao.updateMessageText(msgId, newText)
        val lastMsgText = newText
        contactDao.updateLastMessage(peerNumber, lastMsgText, System.currentTimeMillis())

        firestore?.let { db ->
            val chatId = getChatId(myNumber, peerNumber)
            val updates = hashMapOf<String, Any>(
                "text" to newText.trim(),
                "isEdited" to true
            )
            db.collection("chats")
                .document(chatId)
                .collection("messages")
                .document(msgId)
                .update(updates)
                .addOnSuccessListener {
                    Log.d("BartaChat", "Message edited successfully in Firestore")
                }
                .addOnFailureListener { e ->
                    Log.e("BartaChat", "Failed to edit message in Firestore", e)
                }

            val chatMeta = hashMapOf<String, Any>(
                "lastMessageText" to lastMsgText,
                "lastMessageTime" to System.currentTimeMillis(),
                "lastSender" to myNumber
            )
            db.collection("chats")
                .document(chatId)
                .set(chatMeta, com.google.firebase.firestore.SetOptions.merge())
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
                                val owner = data["owner"] as? String ?: creator
                                val admins = data["admins"] as? String ?: ""
                                val adminsCanEditInfo = data["admins_can_edit_info"] as? Boolean ?: true
                                val adminsCanPinMessages = data["admins_can_pin_messages"] as? Boolean ?: true
                                val membersCanEditInfo = data["members_can_edit_info"] as? Boolean ?: false
                                val description = data["description"] as? String ?: ""
                                val pinnedMessageId = data["pinned_message_id"] as? String ?: ""

                                sharedPrefs.edit()
                                    .putString("group_owner_$id", owner)
                                    .putString("group_admins_$id", admins)
                                    .putBoolean("group_admins_can_edit_info_$id", adminsCanEditInfo)
                                    .putBoolean("group_admins_can_pin_messages_$id", adminsCanPinMessages)
                                    .putBoolean("group_members_can_edit_info_$id", membersCanEditInfo)
                                    .putString("group_description_$id", description)
                                    .putString("pinned_message_id_$id", pinnedMessageId)
                                    .apply()

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
                            val isEdited = data["isEdited"] as? Boolean ?: false

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
                                    isDeletedForEveryone = isDeletedForEveryone,
                                    isEdited = isEdited
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

    fun setActiveChatPhone(phone: String?) {
        activeChatPhone = phone
    }

    @Synchronized
    fun startListeningToChatMessages(myNumber: String, peerNumber: String, onNewMessage: () -> Unit) {
        val chatId = getChatId(myNumber, peerNumber)
        if (activeMessageListeners.containsKey(chatId)) {
            return
        }

        val db = firestore ?: return
        val listener = db.collection("chats")
            .document(chatId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshots, error ->
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
                            val isEdited = data["isEdited"] as? Boolean ?: false

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
                                    isDeletedForEveryone = isDeletedForEveryone,
                                    isEdited = isEdited
                                )
                                messageDao.insertMessage(message)

                                val contactPhone = if (peerNumber.startsWith("group_")) peerNumber else (if (senderId == myNumber) receiverId else senderId)
                                val lastMsgText = if (isDeletedForEveryone) "ঐ বার্তাটি মুছে ফেলা হয়েছে" else (if (mediaType.isNotEmpty()) "[$mediaType]" else text)
                                contactDao.updateLastMessage(contactPhone, lastMsgText, timestamp)

                                if (senderId != myNumber) {
                                    val currentContact = contactDao.getContactByPhone(peerNumber)
                                    if (currentContact != null && doc.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                                        val existingMsg = messageDao.getMessageById(id)
                                        if (existingMsg == null) {
                                            hasNew = true
                                            if (peerNumber != activeChatPhone) {
                                                contactDao.updateUnreadCount(peerNumber, currentContact.unreadCount + 1)
                                            }
                                        }
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
        activeMessageListeners[chatId] = listener
    }

    fun startGlobalChatsListener(myNumber: String, onNewChatOrMessage: () -> Unit) {
        globalChatsListener?.remove()
        val db = firestore ?: return

        // 1. Instantly start listening to all existing local contacts
        repositoryScope.launch {
            val localContacts = contactDao.getAllContacts().firstOrNull() ?: emptyList()
            for (contact in localContacts) {
                if (!contact.isSimulated) {
                    startListeningToChatMessages(myNumber, contact.phone, onNewChatOrMessage)
                }
            }
        }

        // 2. Listen to all Firestore chats metadata of which we are a participant
        globalChatsListener = db.collection("chats")
            .whereArrayContains("participants", myNumber)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e("BartaChat", "Global chats listen failed", error)
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    repositoryScope.launch {
                        for (doc in snapshots.documentChanges) {
                            val data = doc.document.data
                            val chatId = doc.document.id
                            val lastMsgText = data["lastMessageText"] as? String ?: ""
                            val lastMsgTime = data["lastMessageTime"] as? Long ?: System.currentTimeMillis()

                            val peerNumber = if (chatId.startsWith("group_")) {
                                chatId
                            } else {
                                val parts = chatId.split("_")
                                parts.firstOrNull { it != myNumber } ?: ""
                            }

                            if (peerNumber.isNotEmpty()) {
                                val existingContact = contactDao.getContactByPhone(peerNumber)
                                if (existingContact == null) {
                                    if (!chatId.startsWith("group_")) {
                                        db.collection("users").document(peerNumber).get()
                                            .addOnSuccessListener { userSnapshot ->
                                                val fName = userSnapshot.getString("name") ?: "বার্তা ব্যবহারকারী"
                                                val fPic = userSnapshot.getString("profilePicBase64") ?: ""
                                                repositoryScope.launch {
                                                    val newContact = Contact(
                                                        phone = peerNumber,
                                                        name = fName,
                                                        isSimulated = false,
                                                        isGroup = false,
                                                        lastMessageText = lastMsgText,
                                                        lastMessageTime = lastMsgTime,
                                                        profilePicUri = fPic,
                                                        lastSeen = "online"
                                                    )
                                                    contactDao.insertContact(newContact)
                                                    startListeningToChatMessages(myNumber, peerNumber, onNewChatOrMessage)
                                                }
                                            }
                                            .addOnFailureListener {
                                                repositoryScope.launch {
                                                    val newContact = Contact(
                                                        phone = peerNumber,
                                                        name = "যোগাযোগ ($peerNumber)",
                                                        isSimulated = false,
                                                        isGroup = false,
                                                        lastMessageText = lastMsgText,
                                                        lastMessageTime = lastMsgTime,
                                                        lastSeen = "online"
                                                    )
                                                    contactDao.insertContact(newContact)
                                                    startListeningToChatMessages(myNumber, peerNumber, onNewChatOrMessage)
                                                }
                                            }
                                    } else {
                                        startListeningToChatMessages(myNumber, peerNumber, onNewChatOrMessage)
                                    }
                                } else {
                                    startListeningToChatMessages(myNumber, peerNumber, onNewChatOrMessage)
                                }
                            }
                        }
                    }
                }
            }
    }

    @Synchronized
    fun stopAllMessageListeners() {
        for (listener in activeMessageListeners.values) {
            listener.remove()
        }
        activeMessageListeners.clear()

        globalChatsListener?.remove()
        globalChatsListener = null
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

    suspend fun getAllFirestoreUsers(): List<Map<String, Any>> = suspendCancellableCoroutine { continuation ->
        val db = firestore
        if (db == null) {
            continuation.resume(emptyList())
            return@suspendCancellableCoroutine
        }
        db.collection("users").get()
            .addOnSuccessListener { querySnapshot ->
                val list = querySnapshot.documents.mapNotNull { it.data }
                continuation.resume(list)
            }
            .addOnFailureListener {
                continuation.resume(emptyList())
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

    fun triggerChatbotResponse(userPhone: String, botPhone: String, userMessage: String) {
        repositoryScope.launch {
            try {
                contactDao.updateTypingStatus(botPhone, "typing...")
                
                // Fetch current language settings to respect Bengali / English output context
                val language = sharedPrefs.getString("app_language", "bn") ?: "bn"
                
                // Retrieve last messages for context window pipeline
                val recentMessagesList = try {
                    messageDao.getMessagesForChat(userPhone, botPhone).firstOrNull() ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }

                // Call the secure GeminiService (CORS and fallback automated!)
                val replyText = try {
                    GeminiService.getGeminiResponse(
                        context = context,
                        userPhone = userPhone,
                        userMessage = userMessage,
                        previousMessages = recentMessagesList,
                        language = language
                    )
                } catch (e: Exception) {
                    Log.e("BartaChat", "Gemini error: ${e.message}", e)
                    val errorDetail = e.message ?: e.toString()
                    if (language == "bn") {
                        "দুঃখিত, সংযোগে সমস্যা হয়েছে! (ত্রুটি: $errorDetail)। অনুগ্রহ করে আপনার এআই এপিআই কি (API Key) বা ক্লাউড ফাংশন এবং ইন্টারনেট কানেকশন যাচাই করুন।"
                    } else {
                        "Sorry, an error occurred while connecting to the AI! (Error: $errorDetail). Please verify your AI API Key, Cloud Function, or internet connection."
                    }
                }

                val messageId = UUID.randomUUID().toString()
                val timestamp = System.currentTimeMillis()
                val replyMessage = Message(
                    id = messageId,
                    senderId = botPhone,
                    receiverId = userPhone,
                    text = replyText,
                    timestamp = timestamp,
                    senderName = if (language == "bn") "বার্তা সহকারী (Bot) 🤖" else "Barta Assistant 🤖"
                )
                messageDao.insertMessage(replyMessage)
                contactDao.updateLastMessage(botPhone, replyText, timestamp)

                // Save companion response to Firestore (cloud backend sync for cloud history preservation)
                firestore?.let { db ->
                    val chatId = getChatId(userPhone, botPhone)
                    val firestoreMsg = hashMapOf(
                        "id" to messageId,
                        "senderId" to botPhone,
                        "receiverId" to userPhone,
                        "text" to replyText,
                        "timestamp" to timestamp,
                        "isRead" to false,
                        "senderName" to (if (language == "bn") "বার্তা সহকারী (Bot) 🤖" else "Barta Assistant 🤖"),
                        "mediaUrl" to "",
                        "mediaType" to "",
                        "isDeletedForEveryone" to false
                    )
                    db.collection("chats")
                        .document(chatId)
                        .collection("messages")
                        .document(messageId)
                        .set(firestoreMsg)
                }

            } catch (e: Exception) {
                Log.e("BartaChat", "Exception inside chatbot execution pipeline", e)
            } finally {
                contactDao.updateTypingStatus(botPhone, "")
            }
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

        var finalMediaUrl = mediaUrl
        if (mediaUrl != null && !mediaUrl.startsWith("http://") && !mediaUrl.startsWith("https://")) {
            val file = java.io.File(mediaUrl)
            if (file.exists() && firebaseStorage != null) {
                try {
                    finalMediaUrl = uploadFileToStorage(file)
                } catch (e: Exception) {
                    Log.e("BartaChat", "Error uploading status image, using local path", e)
                }
            }
        }

        val id = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        val status = ChatStatus(
            id = id,
            phone = myPhone,
            name = myName,
            avatar = myAvatar,
            text = text,
            mediaUrl = finalMediaUrl,
            timestamp = timestamp,
            bgColorVal = bgColorVal,
            loves = "",
            viewers = ""
        )

        statusDao.insertStatus(status)

        firestore?.let { db ->
            val data = hashMapOf(
                "id" to id,
                "phone" to myPhone,
                "name" to myName,
                "avatar" to myAvatar,
                "text" to text,
                "mediaUrl" to (finalMediaUrl ?: ""),
                "timestamp" to timestamp,
                "bgColorVal" to bgColorVal,
                "loves" to "",
                "viewers" to ""
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

    fun deleteStatus(statusId: String, onComplete: (Boolean) -> Unit = {}) {
        repositoryScope.launch {
            try {
                statusDao.deleteStatusById(statusId)
                firestore?.let { db ->
                    db.collection("statuses").document(statusId).delete()
                        .addOnSuccessListener {
                            onComplete(true)
                        }
                        .addOnFailureListener {
                            onComplete(false)
                        }
                } ?: onComplete(true)
            } catch (e: Exception) {
                Log.e("BartaChat", "Error deleting status: ${e.message}", e)
                onComplete(false)
            }
        }
    }

    fun toggleLoveStatus(statusId: String, currentLoves: String, onComplete: (Boolean) -> Unit = {}) {
        val myPhone = sharedPrefs.getString("logged_user_phone", "") ?: ""
        val myName = sharedPrefs.getString("logged_user_display_name", "Anonymous") ?: "Anonymous"
        if (myPhone.isEmpty()) return

        val loversList = if (currentLoves.isEmpty()) mutableListOf() else currentLoves.split(",").toMutableList()
        val existingIndex = loversList.indexOfFirst { it.startsWith("$myPhone:") }

        if (existingIndex >= 0) {
            loversList.removeAt(existingIndex)
        } else {
            loversList.add("$myPhone:$myName")
        }

        val newLoves = loversList.joinToString(",")

        repositoryScope.launch {
            try {
                val localStatus = statusDao.getStatusById(statusId)
                if (localStatus != null) {
                    val updated = localStatus.copy(loves = newLoves)
                    statusDao.insertStatus(updated)
                }
                firestore?.let { db ->
                    db.collection("statuses").document(statusId).update("loves", newLoves)
                        .addOnSuccessListener { onComplete(true) }
                        .addOnFailureListener { onComplete(false) }
                } ?: onComplete(true)
            } catch (e: Exception) {
                Log.e("BartaChat", "Error toggling love status", e)
                onComplete(false)
            }
        }
    }

    fun markStatusAsViewed(statusId: String, currentViewers: String) {
        val myPhone = sharedPrefs.getString("logged_user_phone", "") ?: ""
        val myName = sharedPrefs.getString("logged_user_display_name", "Anonymous") ?: "Anonymous"
        if (myPhone.isEmpty()) return

        val viewersList = if (currentViewers.isEmpty()) mutableListOf() else currentViewers.split(",").toMutableList()
        val exists = viewersList.any { it.startsWith("$myPhone:") }

        if (!exists) {
            viewersList.add("$myPhone:$myName")
            val newViewers = viewersList.joinToString(",")
            repositoryScope.launch {
                try {
                    val localStatus = statusDao.getStatusById(statusId)
                    if (localStatus != null) {
                        val updated = localStatus.copy(viewers = newViewers)
                        statusDao.insertStatus(updated)
                    }
                    firestore?.let { db ->
                        db.collection("statuses").document(statusId).update("viewers", newViewers)
                            .addOnSuccessListener { Log.d("BartaChat", "Marked status $statusId as viewed") }
                    }
                } catch (e: Exception) {
                    Log.e("BartaChat", "Error marking status as viewed", e)
                }
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
                            val loves = data["loves"] as? String ?: ""
                            val viewers = data["viewers"] as? String ?: ""

                            if (id.isNotEmpty()) {
                                if (doc.type == com.google.firebase.firestore.DocumentChange.Type.REMOVED) {
                                    statusDao.deleteStatusById(id)
                                } else {
                                    val status = ChatStatus(
                                        id = id,
                                        phone = phone,
                                        name = name,
                                        avatar = avatar,
                                        text = text,
                                        mediaUrl = mediaUrl.ifEmpty { null },
                                        timestamp = timestamp,
                                        bgColorVal = bgColorVal,
                                        loves = loves,
                                        viewers = viewers
                                    )
                                    statusDao.insertStatus(status)
                                }
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

    private var usersPresenceListener: ListenerRegistration? = null

    fun updatePresence(phone: String, isOnline: Boolean) {
        val db = firestore ?: return
        val lastSeenVal = if (isOnline) "online" else System.currentTimeMillis().toString()
        val data = hashMapOf<String, Any>(
            "lastSeen" to lastSeenVal
        )
        db.collection("users").document(phone).set(data, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                Log.d("BartaChat", "Sync presence success for $phone: $lastSeenVal")
            }
            .addOnFailureListener { e ->
                Log.e("BartaChat", "Sync presence failed for $phone", e)
            }
    }

    fun startListeningToUserPresence(onPresenceUpdated: () -> Unit) {
        usersPresenceListener?.remove()
        val db = firestore ?: return

        usersPresenceListener = db.collection("users")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e("BartaChat", "Firestore users presence listen failed", error)
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    repositoryScope.launch {
                        for (doc in snapshots.documentChanges) {
                            val data = doc.document.data
                            val phone = data["phone"] as? String ?: doc.document.id
                            val lastSeenVal = data["lastSeen"] as? String ?: "offline"

                            if (phone.isNotEmpty()) {
                                val currentContact = contactDao.getContactByPhone(phone)
                                if (currentContact != null) {
                                    contactDao.updateContactPresence(phone, lastSeenVal)
                                }
                            }
                        }
                        withContext(Dispatchers.Main) {
                            onPresenceUpdated()
                        }
                    }
                }
            }
    }

    fun stopListeningToUserPresence() {
        usersPresenceListener?.remove()
        usersPresenceListener = null
    }
}
