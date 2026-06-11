package com.example.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.data.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val db = Room.databaseBuilder(
        context,
        ChatDatabase::class.java,
        "barta_chat_database"
    ).fallbackToDestructiveMigration().build()

    private val repository = ChatRepository(db.contactDao(), db.messageDao(), db.userDao(), context)
    private val sharedPrefs = context.getSharedPreferences("BartaChatPrefs", Context.MODE_PRIVATE)

    private val _myNumber = MutableStateFlow<String?>(null)
    val myNumber: StateFlow<String?> = _myNumber.asStateFlow()

    private val _currentTab = MutableStateFlow("chats") // chats, contacts, profile
    val currentTab: StateFlow<String> = _currentTab.asStateFlow()

    val searchQuery = MutableStateFlow("")

    val contacts: StateFlow<List<Contact>> = combine(repository.allContacts, searchQuery) { list, query ->
        if (query.isBlank()) {
            list
        } else {
            list.filter {
                it.name.contains(query, ignoreCase = true) || it.phone.contains(query)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeContact = MutableStateFlow<Contact?>(null)
    val activeContact: StateFlow<Contact?> = _activeContact.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeMessages: StateFlow<List<Message>> = _activeContact.flatMapLatest { contact ->
        val me = _myNumber.value
        if (contact == null || me == null) {
            flowOf(emptyList())
        } else {
            repository.getMessages(me, contact.phone)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Firebase state inputs
    val firebaseApiKey = MutableStateFlow("")
    val firebaseProjectId = MutableStateFlow("")
    val firebaseAppId = MutableStateFlow("")
    val isFirebaseConfigured = MutableStateFlow(false)

    // User profile state inputs
    val userDisplayName = MutableStateFlow("")
    val userStatusMessage = MutableStateFlow("")
    val userProfilePicBase64 = MutableStateFlow("")

    // Global name search of users in DB
    private val _searchedGlobalUsers = MutableStateFlow<List<LocalUser>>(emptyList())
    val searchedGlobalUsers: StateFlow<List<LocalUser>> = _searchedGlobalUsers.asStateFlow()

    init {
        _myNumber.value = sharedPrefs.getString("logged_user_phone", null)
        userDisplayName.value = sharedPrefs.getString("logged_user_display_name", "") ?: ""
        userStatusMessage.value = sharedPrefs.getString("logged_user_status_message", "বার্তা (Chat) ব্যবহার করছি!") ?: ""
        userProfilePicBase64.value = sharedPrefs.getString("logged_user_profile_pic", "") ?: ""
        loadFirebaseConfig()
        
        // Seed some starter chatbot contacts if user is logged in
        if (_myNumber.value != null) {
            viewModelScope.launch {
                seedDummyContacts()
            }
        }

        // Active lookup of other registered users on name matching
        viewModelScope.launch {
            searchQuery.collect { query ->
                val me = _myNumber.value
                if (query.trim().length >= 1 && me != null) {
                    val results = repository.searchUsers(query.trim()).filter { it.phone != me }
                    _searchedGlobalUsers.value = results
                } else {
                    _searchedGlobalUsers.value = emptyList()
                }
            }
        }

        // Real-time listener for Firestore groups of which the user is a participant
        viewModelScope.launch {
            _myNumber.collect { me ->
                if (me != null) {
                    repository.startListeningToGroups(me) {
                        // real-time synchronization callback trigger
                    }
                } else {
                    repository.stopListeningToGroups()
                }
            }
        }
    }

    fun register(phone: String, name: String, password: String, profilePic: String, callback: (String?) -> Unit) {
        val cleanPhone = phone.trim()
        val cleanName = name.trim()
        val cleanPass = password.trim()

        if (cleanName.isEmpty()) {
            callback("অ্যাকাউন্ট তৈরির সময় সবার একটা নাম দিতে হবে!")
            return
        }
        if (cleanPhone.length != 11 || !cleanPhone.startsWith("01") || !cleanPhone.all { it.isDigit() }) {
            callback("দয়া করে সঠিক ১১ ডিজিটের বাংলাদেশ নাম্বার দিন!")
            return
        }
        if (cleanPass.length < 6 || cleanPass.length > 8) {
            callback("পাসওয়ার্ড অবশ্যই ৬ থেকে ৮ অক্ষরের মাঝে হতে হবে!")
            return
        }

        viewModelScope.launch {
            val exists = repository.getUserByPhone(cleanPhone)
            if (exists != null) {
                callback("এই কন্ট্যাক্ট নাম্বার দিয়ে ইতিমধ্যে অ্যাকাউন্ট তৈরি হয়েছে!")
            } else {
                val registered = repository.registerLocalUser(cleanPhone, cleanName, cleanPass, profilePic)
                if (registered) {
                    sharedPrefs.edit()
                        .putString("logged_user_phone", cleanPhone)
                        .putString("logged_user_display_name", cleanName)
                        .putString("logged_user_profile_pic", profilePic)
                        .putString("logged_user_status_message", "বার্তা (Chat) ব্যবহার করছি!")
                        .apply()
                    _myNumber.value = cleanPhone
                    userDisplayName.value = cleanName
                    userProfilePicBase64.value = profilePic
                    userStatusMessage.value = "বার্তা (Chat) ব্যবহার করছি!"
                    seedDummyContacts()
                    callback(null)
                } else {
                    callback("রেজিস্ট্রেশন ব্যর্থ হয়েছে।")
                }
            }
        }
    }

    fun loginWithPassword(phone: String, pass: String, callback: (String?) -> Unit) {
        val cleanPhone = phone.trim()
        val cleanPass = pass.trim()

        if (cleanPhone.length != 11 || !cleanPhone.startsWith("01")) {
            callback("মোবাইল নাম্বারটি অবশ্যই ১১ ডিজিটের হতে হবে!")
            return
        }
        if (cleanPass.length < 6 || cleanPass.length > 8) {
            callback("পাসওয়ার্ড অবশ্যই ৬ থেকে ৮ অক্ষরের হতে হবে!")
            return
        }

        viewModelScope.launch {
            val user = repository.getUserByPhone(cleanPhone)
            if (user == null) {
                callback("এই নাম্বার দিয়ে কোনো অ্যাকাউন্ট পাওয়া যায়নি! রেজিস্ট্রেশন করুন।")
            } else if (user.passwordHash != cleanPass) {
                callback("ভুল পাসওয়ার্ড! দয়া করে সঠিক পাসওয়ার্ড দিন।")
            } else {
                sharedPrefs.edit()
                    .putString("logged_user_phone", cleanPhone)
                    .putString("logged_user_display_name", user.name)
                    .putString("logged_user_profile_pic", user.profilePicBase64)
                    .putString("logged_user_status_message", user.status)
                    .apply()
                _myNumber.value = cleanPhone
                userDisplayName.value = user.name
                userProfilePicBase64.value = user.profilePicBase64
                userStatusMessage.value = user.status
                seedDummyContacts()
                callback(null)
            }
        }
    }

    fun saveProfile(name: String, status: String, profilePic: String = "") {
        val cleanName = name.trim()
        val cleanStatus = status.trim()
        userDisplayName.value = cleanName
        userStatusMessage.value = cleanStatus
        val pic = profilePic.ifEmpty { userProfilePicBase64.value }
        userProfilePicBase64.value = pic

        sharedPrefs.edit()
            .putString("logged_user_display_name", cleanName)
            .putString("logged_user_status_message", cleanStatus)
            .putString("logged_user_profile_pic", pic)
            .apply()
        
        val me = _myNumber.value
        if (me != null) {
            viewModelScope.launch {
                val loadedUser = repository.getUserByPhone(me)
                if (loadedUser != null) {
                    val updated = LocalUser(me, cleanName, loadedUser.passwordHash, pic, cleanStatus)
                    repository.updateUser(updated)
                } else {
                    val newUser = LocalUser(me, cleanName, "123456", pic, cleanStatus)
                    repository.updateUser(newUser)
                }
            }
        }
    }

    fun changePassword(newPass: String, callback: (String?) -> Unit) {
        val me = _myNumber.value ?: return
        val cleanPass = newPass.trim()
        if (cleanPass.length < 6 || cleanPass.length > 8) {
            callback("পাসওয়ার্ড অবশ্যই ৬ থেকে ৮ অক্ষরের হতে হবে!")
            return
        }
        viewModelScope.launch {
            val loadedUser = repository.getUserByPhone(me)
            if (loadedUser != null) {
                val updated = loadedUser.copy(passwordHash = cleanPass)
                repository.updateUser(updated)
                callback(null)
            } else {
                callback("ইউজার রেকর্ড খুঁজে পাওয়া যায়নি!")
            }
        }
    }

    fun selectTab(tab: String) {
        _currentTab.value = tab
    }

    fun logout() {
        sharedPrefs.edit()
            .remove("logged_user_phone")
            .remove("logged_user_display_name")
            .remove("logged_user_profile_pic")
            .remove("logged_user_status_message")
            .apply()
        _myNumber.value = null
        _activeContact.value = null
        userProfilePicBase64.value = ""
        userDisplayName.value = ""
        repository.stopListeningToGroups()
        viewModelScope.launch {
            db.contactDao().deleteAllContacts()
        }
    }

    fun selectContact(contact: Contact?) {
        _activeContact.value = contact
        val me = _myNumber.value
        if (contact != null && me != null) {
            viewModelScope.launch {
                repository.updateUnreadCount(contact.phone, 0)
            }
            if (!contact.isSimulated && isFirebaseConfigured.value) {
                repository.startListeningToChat(me, contact.phone) {
                    // Alert layout sync trigger callback
                }
            } else {
                repository.stopListeningToChat()
            }
        } else {
            repository.stopListeningToChat()
        }
    }

    fun createGroup(groupName: String, participants: List<Contact>) {
        val me = _myNumber.value ?: return
        val cleanName = groupName.trim()
        if (cleanName.isEmpty()) return

        viewModelScope.launch {
            val groupId = "group_" + java.util.UUID.randomUUID().toString().take(6)
            val participantPhones = (participants.map { it.phone } + me).joinToString(",")
            val newGroupContact = Contact(
                phone = groupId,
                name = cleanName,
                isSimulated = false,
                isGroup = true,
                groupParticipants = participantPhones,
                lastMessageText = "গ্রুপ চ্যাট তৈরি হয়েছে! চ্যাট শুরু করুন।",
                lastMessageTime = System.currentTimeMillis()
            )
            repository.addContact(newGroupContact)
        }
    }

    fun updateGroup(groupId: String, newName: String, newParticipants: List<String>) {
        val me = _myNumber.value
        val updatedParticipants = if (me != null && !newParticipants.contains(me)) {
            newParticipants + me
        } else {
            newParticipants
        }
        viewModelScope.launch {
            repository.updateGroup(groupId, newName, updatedParticipants)
            if (_activeContact.value?.phone == groupId) {
                val updated = repository.getContact(groupId)
                if (updated != null) {
                    _activeContact.value = updated
                }
            }
        }
    }

    fun updateMyTypingStatus(contactPhone: String, isTyping: Boolean) {
        viewModelScope.launch {
            repository.updateTypingStatus(contactPhone, if (isTyping) "typing..." else "")
        }
    }

    fun addNewContact(name: String, phone: String, simulateReply: Boolean): Boolean {
        val cleanPhone = phone.trim()
        val cleanName = name.trim()
        if (cleanPhone.length == 11 && cleanPhone.startsWith("01") && cleanPhone.all { it.isDigit() }) {
            viewModelScope.launch {
                val newContact = Contact(
                    phone = cleanPhone,
                    name = cleanName.ifEmpty { "যোগাযোগ ($cleanPhone)" },
                    isSimulated = simulateReply,
                    lastSeen = if (simulateReply) "online" else "offline",
                    lastMessageText = "চ্যাট আরম্ভ করতে বার্তা পাঠান...",
                    lastMessageTime = System.currentTimeMillis()
                )
                repository.addContact(newContact)
            }
            return true
        }
        return false
    }

    fun sendMessage(text: String) {
        val me = _myNumber.value ?: return
        val active = _activeContact.value ?: return
        val msgText = text.trim()
        if (msgText.isEmpty()) return

        val myName = userDisplayName.value.ifEmpty { "বার্তা ব্যবহারকারী" }

        viewModelScope.launch {
            repository.sendMessage(me, active.phone, msgText, active.isSimulated, myName)
        }
    }

    fun sendMediaMessage(mediaUrl: String, mediaType: String) {
        val me = _myNumber.value ?: return
        val active = _activeContact.value ?: return
        val myName = userDisplayName.value.ifEmpty { "বার্তা ব্যবহারকারী" }
        viewModelScope.launch {
            repository.sendMessage(
                sender = me,
                receiver = active.phone,
                text = "Media $mediaType",
                isSimulatedReceiver = active.isSimulated,
                senderName = myName,
                mediaUrl = mediaUrl,
                mediaType = mediaType
            )
        }
    }

    fun deleteMessageForMe(msgId: String) {
        viewModelScope.launch {
            repository.deleteMessageForMe(msgId)
        }
    }

    fun deleteMessageForEveryone(msgId: String) {
        val me = _myNumber.value ?: return
        val active = _activeContact.value ?: return
        viewModelScope.launch {
            repository.deleteMessageForEveryone(me, active.phone, msgId)
        }
    }

    fun loadFirebaseConfig() {
        val config = repository.getFirebaseConfig()
        firebaseApiKey.value = config.first
        firebaseProjectId.value = config.second
        firebaseAppId.value = config.third
        isFirebaseConfigured.value = config.first.isNotEmpty()
    }

    fun saveFirebaseConfig(key: String, proj: String, app: String) {
        repository.saveFirebaseConfig(key, proj, app)
        loadFirebaseConfig()
    }

    fun clearFirebaseConfig() {
        repository.clearFirebaseConfig()
        loadFirebaseConfig()
    }

    private suspend fun seedDummyContacts() {
        val existing = db.contactDao().getAllContacts().firstOrNull()?.size ?: 0
        if (existing == 0) {
            val dummy1 = Contact(
                phone = "01711122233",
                name = "সাকিব আল হাসান 🟢",
                isSimulated = true,
                lastSeen = "online",
                lastMessageText = "আসসালামু আলাইকুম! কেমন আছেন?",
                lastMessageTime = System.currentTimeMillis() - 7200000
            )
            val dummy2 = Contact(
                phone = "01944455566",
                name = "রাফি চৌধুরী 💬",
                isSimulated = true,
                lastSeen = "last seen today at 2:15 PM",
                lastMessageText = "বার্তা অ্যাপের নতুন আপডেটটি জাস্ট অসাধারণ!",
                lastMessageTime = System.currentTimeMillis() - 3600000
            )
            val dummy3 = Contact(
                phone = "01300000000",
                name = "বার্তা সহকারী (Bot) 🤖",
                isSimulated = true,
                lastSeen = "online",
                lastMessageText = "যেকোনো প্রশ্নের উত্তর দিতে আমি এখানে আছি!",
                lastMessageTime = System.currentTimeMillis()
            )

            repository.addContact(dummy1)
            repository.addContact(dummy2)
            repository.addContact(dummy3)
        }
    }

    override fun onCleared() {
        super.onCleared()
        repository.stopListeningToChat()
    }
}
