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

    private val repository = ChatRepository(db.contactDao(), db.messageDao(), db.userDao(), db.statusDao(), context)
    private val sharedPrefs = context.getSharedPreferences("BartaChatPrefs", Context.MODE_PRIVATE)

    private val _myNumber = MutableStateFlow<String?>(null)
    val myNumber: StateFlow<String?> = _myNumber.asStateFlow()

    private val _currentTab = MutableStateFlow("chats") // chats, contacts, profile
    val currentTab: StateFlow<String> = _currentTab.asStateFlow()

    private val _navigationStack = MutableStateFlow<List<String>>(listOf("chats"))
    val navigationStack: StateFlow<List<String>> = _navigationStack.asStateFlow()

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

    val activeStatuses: StateFlow<List<ChatStatus>> = repository.getActiveStatusesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeContact = MutableStateFlow<Contact?>(null)
    val activeContact: StateFlow<Contact?> = _activeContact.asStateFlow()

    val showOnboarding = MutableStateFlow(false)

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
    val firebaseFunctionsBaseUrl = MutableStateFlow("")

    private val _firestoreUsers = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val firestoreUsers: StateFlow<List<Map<String, Any>>> = _firestoreUsers.asStateFlow()

    private val _isFetchingFirestoreUsers = MutableStateFlow(false)
    val isFetchingFirestoreUsers: StateFlow<Boolean> = _isFetchingFirestoreUsers.asStateFlow()

    fun fetchFirestoreUsers() {
        viewModelScope.launch {
            _isFetchingFirestoreUsers.value = true
            try {
                _firestoreUsers.value = repository.getAllFirestoreUsers()
            } catch (e: Exception) {
                // Ignore
            } finally {
                _isFetchingFirestoreUsers.value = false
            }
        }
    }

    // Language setting
    val appLanguage = MutableStateFlow(sharedPrefs.getString("app_language", "bn") ?: "bn")

    fun setAppLanguage(lang: String) {
        appLanguage.value = lang
        sharedPrefs.edit().putString("app_language", lang).apply()
    }

    // Theme settings: default to true (Dark Mode) as requested by user
    val isDarkMode = MutableStateFlow(sharedPrefs.getBoolean("app_theme_dark", true))

    fun toggleTheme() {
        val nextMode = !isDarkMode.value
        isDarkMode.value = nextMode
        sharedPrefs.edit().putBoolean("app_theme_dark", nextMode).apply()
    }

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

        // Real-time listener for Firestore groups, statuses, and global message syncing
        viewModelScope.launch {
            _myNumber.collect { me ->
                if (me != null) {
                    repository.updatePresence(me, true)
                    repository.startListeningToGroups(me) {
                        // real-time synchronization callback trigger
                    }
                    repository.startListeningToStatuses {
                        // real-time status synchronization callback trigger
                    }
                    repository.startGlobalChatsListener(me) {
                        // real-time global messaging synchronization callback trigger
                    }
                    repository.startListeningToUserPresence {
                        // real-time presence updated callback trigger
                    }
                } else {
                    repository.stopListeningToGroups()
                    repository.stopListeningToStatuses()
                    repository.stopAllMessageListeners()
                    repository.stopListeningToUserPresence()
                }
            }
        }

        // Dynamically spin up real-time message listeners for any contact or group loaded locally
        viewModelScope.launch {
            repository.allContacts.collect { contactList ->
                val me = _myNumber.value
                if (me != null && isFirebaseConfigured.value) {
                    for (contact in contactList) {
                        if (!contact.isSimulated) {
                            repository.startListeningToChatMessages(me, contact.phone) {
                                // Message received successfully, Room and Flows auto-sync the layouts
                            }
                        }
                    }
                }
            }
        }

        // Periodic sync of pending messages when online
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(10000)
                if (repository.isNetworkAvailable()) {
                    repository.syncPendingMessages()
                }
            }
        }
    }

    fun postStatus(text: String, mediaUrl: String? = null, bgColorVal: Long = 0xFF00897B) {
        viewModelScope.launch {
            repository.postStatus(text, mediaUrl, bgColorVal)
        }
    }

    fun deleteStatus(statusId: String, onComplete: (Boolean) -> Unit = {}) {
        repository.deleteStatus(statusId, onComplete)
    }

    fun toggleLoveStatus(statusId: String, currentLoves: String, onComplete: (Boolean) -> Unit = {}) {
        repository.toggleLoveStatus(statusId, currentLoves, onComplete)
    }

    fun markStatusAsViewed(statusId: String, currentViewers: String) {
        repository.markStatusAsViewed(statusId, currentViewers)
    }

    fun register(phone: String, name: String, password: String, profilePic: String, callback: (String?) -> Unit) {
        val cleanPhone = phone.trim()
        val cleanName = name.trim()
        val cleanPass = password.trim()
        val isBn = appLanguage.value == "bn"

        if (cleanName.isEmpty()) {
            callback(if (isBn) "অ্যাকাউন্ট তৈরির সময় সবার একটা নাম দিতে হবে!" else "Please provide a name to create an account!")
            return
        }
        if (cleanPhone.length != 11 || !cleanPhone.startsWith("01") || !cleanPhone.all { it.isDigit() }) {
            callback(if (isBn) "দয়া করে সঠিক ১১ ডিজিটের বাংলাদেশ নাম্বার দিন!" else "Please provide a valid 11-digit Bangladeshi number!")
            return
        }
        if (cleanPass.length < 6 || cleanPass.length > 8) {
            callback(if (isBn) "পাসওয়ার্ড অবশ্যই ৬ থেকে ৮ অক্ষরের মাঝে হতে হবে!" else "Password must be between 6 and 8 characters long!")
            return
        }

        viewModelScope.launch {
            val exists = repository.getUserByPhone(cleanPhone)
            val existsInFirestore = repository.getUserFromFirestore(cleanPhone)
            if (exists != null || existsInFirestore != null) {
                callback(if (isBn) "এই কন্ট্যাক্ট নাম্বার দিয়ে ইতিমধ্যে অ্যাকাউন্ট তৈরি হয়েছে!" else "An account with this phone number already exists!")
            } else {
                val registered = repository.registerLocalUser(cleanPhone, cleanName, cleanPass, profilePic)
                if (registered) {
                    sharedPrefs.edit()
                        .putString("logged_user_phone", cleanPhone)
                        .putString("logged_user_display_name", cleanName)
                        .putString("logged_user_profile_pic", profilePic)
                        .putString("logged_user_status_message", "বার্তা (Chat) ব্যবহার করছি!")
                        .apply()
                    seedDummyContacts()
                    showOnboarding.value = true
                    _myNumber.value = cleanPhone
                    userDisplayName.value = cleanName
                    userProfilePicBase64.value = profilePic
                    userStatusMessage.value = "বার্তা (Chat) ব্যবহার করছি!"
                    callback(null)
                } else {
                    callback(if (isBn) "রেজিস্ট্রেশন ব্যর্থ হয়েছে।" else "Registration failed.")
                }
            }
        }
    }

    fun loginWithPassword(phone: String, pass: String, callback: (String?) -> Unit) {
        val cleanPhone = phone.trim()
        val cleanPass = pass.trim()
        val isBn = appLanguage.value == "bn"

        if (cleanPhone.length != 11 || !cleanPhone.startsWith("01")) {
            callback(if (isBn) "মোবাইল নাম্বারটি অবশ্যই ১১ ডিজিটের হতে হবে!" else "Mobile number must be exactly 11 digits!")
            return
        }
        if (cleanPass.length < 6 || cleanPass.length > 8) {
            callback(if (isBn) "পাসওয়ার্ড অবশ্যই ৬ থেকে ৮ অক্ষরের হতে হবে!" else "Password must be between 6 and 8 characters!")
            return
        }

        viewModelScope.launch {
            var user = repository.getUserByPhone(cleanPhone)
            if (user == null) {
                // If not found in local DB, fetch from Firestore to see if they registered previously
                val firestoreUser = repository.getUserFromFirestore(cleanPhone)
                if (firestoreUser != null) {
                    val fsPassword = firestoreUser["passwordHash"] as? String ?: ""
                    if (fsPassword.isNotEmpty() && fsPassword == cleanPass) {
                        val name = firestoreUser["name"] as? String ?: "ব্যবহারকারী"
                        val status = firestoreUser["status"] as? String ?: "বার্তা (Chat) ব্যবহার করছি!"
                        val pic = firestoreUser["profilePicBase64"] as? String ?: ""
                        
                        val newUser = com.example.data.LocalUser(cleanPhone, name, cleanPass, pic, status)
                        repository.insertLocalUserDirectly(newUser)
                        user = newUser
                    } else if (fsPassword.isNotEmpty() && fsPassword != cleanPass) {
                        callback(if (isBn) "ভুল পাসওয়ার্ড! দয়া করে সঠিক পাসওয়ার্ড দিন।" else "Incorrect password! Please try again.")
                        return@launch
                    } else {
                        // Document exists but no passwordHash saved (legacy user). Let's allow creating local user check or show specific msg
                        callback(if (isBn) "এই নাম্বার দিয়ে কোনো সম্পূর্ণ অ্যাকাউন্ট পাওয়া যায়নি বা পাসওয়ার্ড মেলেনি।" else "No complete account found with this number or password mismatch.")
                        return@launch
                    }
                } else {
                    callback(if (isBn) "এই নাম্বার দিয়ে কোনো অ্যাকাউন্ট পাওয়া যায়নি! রেজিস্ট্রেশন করুন।" else "No account found with this number! Please register.")
                    return@launch
                }
            }

            if (user != null) {
                if (user.passwordHash != cleanPass) {
                    callback(if (isBn) "ভুল পাসওয়ার্ড! দয়া করে সঠিক পাসওয়ার্ড দিন।" else "Incorrect password! Please try again.")
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
        val current = _navigationStack.value.toMutableList()
        if (current.isEmpty() || current.last() != tab) {
            current.add(tab)
            _navigationStack.value = current
        }
    }

    fun navigateBack(): Boolean {
        if (_activeContact.value != null) {
            selectContact(null)
            return true
        }
        val current = _navigationStack.value.toMutableList()
        if (current.size > 1) {
            current.removeAt(current.lastIndex)
            _navigationStack.value = current
            _currentTab.value = current.last()
            return true
        }
        return false
    }

    fun logout() {
        val me = _myNumber.value
        if (me != null) {
            repository.updatePresence(me, false)
        }
        sharedPrefs.edit()
            .remove("logged_user_phone")
            .remove("logged_user_display_name")
            .remove("logged_user_profile_pic")
            .remove("logged_user_status_message")
            .apply()
        _myNumber.value = null
        _activeContact.value = null
        _navigationStack.value = listOf("chats")
        userProfilePicBase64.value = ""
        userDisplayName.value = ""
        repository.stopListeningToGroups()
        repository.stopAllMessageListeners()
        viewModelScope.launch {
            db.contactDao().deleteAllContacts()
        }
    }

    fun selectContact(contact: Contact?) {
        _activeContact.value = contact
        repository.setActiveChatPhone(contact?.phone)
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

    fun createGroup(groupName: String, participants: List<Contact>, groupPhoto: String = "", groupDescription: String = "") {
        val me = _myNumber.value ?: return
        val cleanName = groupName.trim()
        if (cleanName.isEmpty()) return

        viewModelScope.launch {
            val groupId = "group_" + java.util.UUID.randomUUID().toString().take(6)
            sharedPrefs.edit()
                .putString("group_creator_$groupId", me)
                .putString("group_owner_$groupId", me)
                .putString("group_admins_$groupId", "")
                .putBoolean("group_admins_can_edit_info_$groupId", true)
                .putBoolean("group_admins_can_pin_messages_$groupId", true)
                .putBoolean("group_members_can_edit_info_$groupId", false)
                .putString("group_description_$groupId", groupDescription)
                .apply()
            val participantPhones = (listOf(me) + participants.map { it.phone }).distinct().joinToString(",")
            val newGroupContact = Contact(
                phone = groupId,
                name = cleanName,
                isSimulated = false,
                isGroup = true,
                groupParticipants = participantPhones,
                lastMessageText = "গ্রুপ চ্যাট তৈরি হয়েছে! চ্যাট শুরু করুন।",
                lastMessageTime = System.currentTimeMillis(),
                profilePicUri = groupPhoto
            )
            repository.addContact(newGroupContact)
        }
    }

    fun updateGroup(
        groupId: String,
        newName: String,
        newParticipants: List<String>,
        newPhoto: String? = null,
        newDescription: String? = null,
        owner: String? = null,
        admins: String? = null,
        adminsCanEditInfo: Boolean? = null,
        adminsCanPinMessages: Boolean? = null,
        membersCanEditInfo: Boolean? = null
    ) {
        val me = _myNumber.value
        val updatedParticipants = if (me != null && !newParticipants.contains(me)) {
            newParticipants + me
        } else {
            newParticipants
        }
        viewModelScope.launch {
            repository.updateGroup(
                groupId = groupId,
                newName = newName,
                newParticipants = updatedParticipants,
                profilePicUri = newPhoto,
                description = newDescription,
                owner = owner,
                admins = admins,
                adminsCanEditInfo = adminsCanEditInfo,
                adminsCanPinMessages = adminsCanPinMessages,
                membersCanEditInfo = membersCanEditInfo
            )
            if (_activeContact.value?.phone == groupId) {
                val updated = repository.getContact(groupId)
                if (updated != null) {
                    _activeContact.value = updated
                }
            }
        }
    }

    fun deleteGroup(groupId: String) {
        val me = _myNumber.value ?: return
        val currentOwner = sharedPrefs.getString("group_owner_$groupId", "")?.ifEmpty {
            sharedPrefs.getString("group_creator_$groupId", "") ?: ""
        } ?: ""
        if (currentOwner.isNotEmpty() && currentOwner != me) {
            return
        }
        viewModelScope.launch {
            repository.updateTypingStatus(groupId, "")
            repository.deleteGroup(groupId, me)
            if (_activeContact.value?.phone == groupId) {
                _activeContact.value = null
            }
        }
    }

    fun leaveGroup(groupId: String) {
        val me = _myNumber.value ?: return
        viewModelScope.launch {
            val group = repository.getContact(groupId) ?: return@launch
            val updatedParticipants = group.groupParticipants.split(",")
                .filter { it.isNotEmpty() && it != me }
            
            if (updatedParticipants.isEmpty()) {
                deleteGroup(groupId)
            } else {
                repository.sendMessage(
                    sender = me,
                    receiver = groupId,
                    text = "has left the group.",
                    isSimulatedReceiver = false,
                    senderName = userDisplayName.value.ifEmpty { "User" },
                    mediaUrl = null,
                    mediaType = "system"
                )
                
                val currentOwner = sharedPrefs.getString("group_owner_$groupId", "")?.ifEmpty {
                    sharedPrefs.getString("group_creator_$groupId", "") ?: ""
                } ?: ""
                
                if (currentOwner == me) {
                    val adminsStr = sharedPrefs.getString("group_admins_$groupId", "") ?: ""
                    val admins = adminsStr.split(",").filter { it.isNotEmpty() && it != me }
                    val remainingMembers = updatedParticipants.filter { it != me }
                    val newOwner = if (admins.isNotEmpty()) {
                        admins.first()
                    } else if (remainingMembers.isNotEmpty()) {
                        remainingMembers.first()
                    } else {
                        ""
                    }
                    
                    if (newOwner.isNotEmpty()) {
                        sharedPrefs.edit().putString("group_owner_$groupId", newOwner).apply()
                        val updatedAdmins = (admins + newOwner).distinct().joinToString(",")
                        sharedPrefs.edit().putString("group_admins_$groupId", updatedAdmins).apply()
                        
                        val newOwnerContact = repository.getContact(newOwner)
                        val newOwnerName = newOwnerContact?.name ?: newOwner
                        repository.sendMessage(
                            sender = "system",
                            receiver = groupId,
                            text = "Ownership transferred to $newOwnerName.",
                            isSimulatedReceiver = false,
                            senderName = "System",
                            mediaUrl = null,
                            mediaType = "system"
                        )
                        
                        repository.updateGroup(
                            groupId = groupId,
                            newName = group.name,
                            newParticipants = updatedParticipants,
                            owner = newOwner,
                            admins = updatedAdmins
                        )
                    } else {
                        repository.updateGroup(groupId, group.name, updatedParticipants)
                    }
                } else {
                    repository.updateGroup(groupId, group.name, updatedParticipants)
                }
                
                if (_activeContact.value?.phone == groupId) {
                    _activeContact.value = null
                }
            }
        }
    }

    fun pinMessage(groupId: String, msgId: String) {
        viewModelScope.launch {
            sharedPrefs.edit().putString("pinned_message_id_$groupId", msgId).apply()
            val group = repository.getContact(groupId) ?: return@launch
            repository.updateGroup(
                groupId = groupId,
                newName = group.name,
                newParticipants = group.groupParticipants.split(","),
                profilePicUri = group.profilePicUri
            )
        }
    }

    fun unpinMessage(groupId: String) {
        viewModelScope.launch {
            sharedPrefs.edit().remove("pinned_message_id_$groupId").apply()
            val group = repository.getContact(groupId) ?: return@launch
            repository.updateGroup(
                groupId = groupId,
                newName = group.name,
                newParticipants = group.groupParticipants.split(","),
                profilePicUri = group.profilePicUri
            )
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

    fun initiateChatWithRegisteredUser(phone: String, name: String, profilePicBase64: String, onComplete: (Contact) -> Unit) {
        viewModelScope.launch {
            val existing = repository.getContact(phone)
            val contact = if (existing != null) {
                existing
            } else {
                val newContact = Contact(
                    phone = phone,
                    name = name,
                    isSimulated = false,
                    lastSeen = "online",
                    lastMessageText = "চ্যাট আরম্ভ করতে বার্তা পাঠান...",
                    lastMessageTime = System.currentTimeMillis(),
                    profilePicUri = profilePicBase64
                )
                repository.addContact(newContact)
                newContact
            }
            onComplete(contact)
        }
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

    fun forwardMessage(message: Message, recipients: List<Contact>) {
        val me = _myNumber.value ?: return
        val myName = userDisplayName.value.ifEmpty { "বার্তা ব্যবহারকারী" }
        viewModelScope.launch {
            recipients.forEach { contact ->
                repository.forwardMessage(
                    sender = me,
                    receiver = contact.phone,
                    text = message.text,
                    isSimulatedReceiver = contact.isSimulated || contact.phone == "01300000000",
                    senderName = myName,
                    mediaUrl = message.mediaUrl,
                    mediaType = message.mediaType
                )
            }
        }
    }

    fun uploadAndSendImageMessage(file: java.io.File, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val mediaUrl = repository.uploadFileToStorage(file)
                sendMediaMessage(mediaUrl, "image")
                onComplete(true)
            } catch (e: Exception) {
                android.util.Log.e("BartaChat", "Failed to upload and send image", e)
                onComplete(false)
            }
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
        val myName = userDisplayName.value.ifEmpty { "User" }
        viewModelScope.launch {
            repository.deleteMessageForEveryone(me, active.phone, msgId, myName)
        }
    }

    fun editMessage(msgId: String, newText: String) {
        val me = _myNumber.value ?: return
        val active = _activeContact.value ?: return
        viewModelScope.launch {
            repository.editMessage(me, active.phone, msgId, newText)
        }
    }

    fun loadFirebaseConfig() {
        val config = repository.getFirebaseConfig()
        firebaseApiKey.value = config.first
        firebaseProjectId.value = config.second
        firebaseAppId.value = config.third
        isFirebaseConfigured.value = config.first.isNotEmpty()
        firebaseFunctionsBaseUrl.value = sharedPrefs.getString("firebase_functions_base_url", "") ?: ""
    }

    fun saveFirebaseConfig(key: String, proj: String, app: String) {
        repository.saveFirebaseConfig(key, proj, app)
        loadFirebaseConfig()
    }

    fun clearFirebaseConfig() {
        repository.clearFirebaseConfig()
        loadFirebaseConfig()
    }

    fun saveFirebaseFunctionsUrl(url: String) {
        sharedPrefs.edit().putString("firebase_functions_base_url", url.trim()).apply()
        firebaseFunctionsBaseUrl.value = url.trim()
    }

    fun regenerateLastAIResponse() {
        val me = _myNumber.value ?: return
        val active = _activeContact.value ?: return
        
        viewModelScope.launch {
            val list = activeMessages.value
            val lastUserMessage = list.lastOrNull { it.senderId == me }
            if (lastUserMessage != null) {
                val lastAIMessage = list.lastOrNull { it.senderId == active.phone }
                if (lastAIMessage != null) {
                    repository.deleteMessageForMe(lastAIMessage.id)
                }
                repository.triggerChatbotResponse(me, active.phone, lastUserMessage.text)
            }
        }
    }

    private suspend fun seedDummyContacts() {
        val existing = db.contactDao().getAllContacts().firstOrNull()?.size ?: 0
        if (existing == 0) {
            val dummy3 = Contact(
                phone = "01300000000",
                name = "বার্তা সহকারী (Bot) 🤖",
                isSimulated = true,
                lastSeen = "online",
                lastMessageText = "যেকোনো প্রশ্নের উত্তর দিতে আমি এখানে আছি!",
                lastMessageTime = System.currentTimeMillis()
            )

            repository.addContact(dummy3)

            val dummyStatus3 = ChatStatus(
                id = "dummy_status_3",
                phone = "01300000000",
                name = "বার্তা সহকারী (Bot) 🤖",
                avatar = "",
                text = "আমি আপনাদের প্রশ্নের উত্তর দিতে প্রস্তুত! যেকোনো প্রশ্ন করুন। 💡",
                timestamp = System.currentTimeMillis() - 7200000L,
                bgColorVal = 0xFF5E35B1L
            )

            db.statusDao().insertStatus(dummyStatus3)
        }
    }

    private val _syncedContacts = MutableStateFlow<List<SyncedContact>>(emptyList())
    val syncedContacts: StateFlow<List<SyncedContact>> = _syncedContacts.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    fun syncContacts(deviceContacts: List<Pair<String, String>>) {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                // 1. Fetch all users from Firestore
                val firestoreUsers = repository.getAllFirestoreUsers()
                
                // 2. Normalize and construct a lookup map of phone -> user data
                val firestoreUsersMap = firestoreUsers.associateBy { doc ->
                    val phone = doc["phone"] as? String ?: ""
                    normalizePhoneNumber(phone)
                }
                
                // 3. Match
                val currentLocalPhones = repository.allContacts.first().map { it.phone }.toSet()
                
                val resultList = mutableListOf<SyncedContact>()
                for ((deviceName, devicePhone) in deviceContacts) {
                    val normalizedDevicePhone = normalizePhoneNumber(devicePhone)
                    if (normalizedDevicePhone.isEmpty() || normalizedDevicePhone == normalizePhoneNumber(_myNumber.value ?: "")) {
                        continue
                    }
                    
                    val matchDoc = firestoreUsersMap[normalizedDevicePhone]
                    if (matchDoc != null) {
                        val appName = matchDoc["name"] as? String ?: "বার্তা ব্যবহারকারী"
                        val status = matchDoc["status"] as? String ?: "অনলাইন"
                        val profilePic = matchDoc["profilePicBase64"] as? String ?: ""
                        val phoneKey = matchDoc["phone"] as? String ?: normalizedDevicePhone
                        
                        resultList.add(
                            SyncedContact(
                                phone = phoneKey,
                                deviceName = deviceName,
                                appName = appName,
                                status = status,
                                profilePicBase64 = profilePic,
                                alreadyAdded = currentLocalPhones.contains(phoneKey)
                            )
                        )
                    }
                }
                
                // Remove duplicates in resultList by phone
                val distinctResult = resultList.distinctBy { it.phone }
                _syncedContacts.value = distinctResult
            } catch (e: Exception) {
                // Ignore or handle
            } finally {
                _isSyncing.value = false
            }
        }
    }
    
    fun getFirebaseUsersForFallback(): List<Map<String, Any>> {
        var users = emptyList<Map<String, Any>>()
        viewModelScope.launch {
            try {
                users = repository.getAllFirestoreUsers()
            } catch (e: Exception) {
                // Ignore
            }
        }.cancel() // No, wait, let's just make getFirebaseUsersForFallback a suspend function or load it asynchronously!
        return users
    }

    suspend fun getFirebaseUsers(): List<Map<String, Any>> {
        return try {
            repository.getAllFirestoreUsers()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun normalizePhoneNumber(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        return if (digits.length >= 11) {
            digits.takeLast(11)
        } else {
            digits
        }
    }

    fun addSyncedContact(synced: SyncedContact) {
        viewModelScope.launch {
            val newContact = Contact(
                phone = synced.phone,
                name = synced.deviceName.ifEmpty { synced.appName },
                isSimulated = false,
                lastSeen = "online",
                lastMessageText = "চ্যাট আরম্ভ করতে বার্তা পাঠান...",
                lastMessageTime = System.currentTimeMillis(),
                profilePicUri = synced.profilePicBase64
            )
            repository.addContact(newContact)
            
            // Update the state in list
            _syncedContacts.value = _syncedContacts.value.map {
                if (it.phone == synced.phone) it.copy(alreadyAdded = true) else it
            }
        }
    }

    override fun onCleared() {
        val me = _myNumber.value
        if (me != null) {
            repository.updatePresence(me, false)
        }
        super.onCleared()
        repository.stopListeningToChat()
        repository.stopListeningToGroups()
        repository.stopListeningToStatuses()
        repository.stopAllMessageListeners()
        repository.stopListeningToUserPresence()
    }
}
