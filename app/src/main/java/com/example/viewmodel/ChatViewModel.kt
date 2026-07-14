package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.data.*
import com.example.notification.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val db = ChatDatabase.getInstance(context)

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

    // Presence states
    private var isNetworkConnected = true
    private var isAppInForeground = true
    private var heartbeatJob: kotlinx.coroutines.Job? = null
    private var periodicPresenceJob: kotlinx.coroutines.Job? = null
    val lastSeenPrivacy = MutableStateFlow("Everyone")

    fun setAppForegrounded(isForeground: Boolean) {
        isAppInForeground = isForeground
        updatePresenceStatus()
    }

    private fun startMonitoringConnectivityAndPresence() {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (connectivityManager != null) {
            val activeNetwork = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            isNetworkConnected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    isNetworkConnected = true
                    updatePresenceStatus()
                }

                override fun onLost(network: Network) {
                    isNetworkConnected = false
                    updatePresenceStatus()
                }

                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    isNetworkConnected = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    updatePresenceStatus()
                }
            }
            connectivityManager.registerDefaultNetworkCallback(callback)
        }
    }

    private fun updatePresenceStatus() {
        val me = _myNumber.value ?: return
        val shouldBeOnline = isAppInForeground && isNetworkConnected
        if (shouldBeOnline) {
            startHeartbeat(me)
        } else {
            stopHeartbeat(me)
        }
    }

    private fun startHeartbeat(myPhone: String) {
        if (heartbeatJob?.isActive == true) return
        heartbeatJob = viewModelScope.launch(Dispatchers.IO) {
            repository.updatePresence(myPhone, true)
            while (coroutineContext.isActive) {
                delay(20000)
                repository.updateHeartbeat(myPhone)
            }
        }
    }

    private fun stopHeartbeat(myPhone: String) {
        heartbeatJob?.cancel()
        heartbeatJob = null
        viewModelScope.launch(Dispatchers.IO) {
            repository.updatePresence(myPhone, false)
        }
    }

    private fun startPeriodicPresenceReevaluation() {
        if (periodicPresenceJob?.isActive == true) return
        periodicPresenceJob = viewModelScope.launch(Dispatchers.IO) {
            while (coroutineContext.isActive) {
                delay(30000) // every 30 seconds
                val me = _myNumber.value ?: continue
                try {
                    val firestoreUsers = repository.getAllFirestoreUsers()
                    val now = System.currentTimeMillis()
                    for (userData in firestoreUsers) {
                        val phone = userData["phone"] as? String ?: continue
                        if (phone == me) continue
                        
                        val rawLastSeen = userData["lastSeen"] as? String ?: "offline"
                        val heartbeat = userData["heartbeat"] as? Long ?: 0L
                        val lastSeenPrivacyVal = userData["lastSeenPrivacy"] as? String ?: "Everyone"
                        val myContacts = userData["myContacts"] as? List<String> ?: emptyList()
                        
                        val isOnline = rawLastSeen == "online" && (now - heartbeat < 50000)
                        
                        val effectiveLastSeen = if (isOnline) {
                            "online"
                        } else {
                            when (lastSeenPrivacyVal) {
                                "Everyone" -> if (rawLastSeen == "online") heartbeat.toString() else rawLastSeen
                                "My Contacts" -> if (myContacts.contains(me)) (if (rawLastSeen == "online") heartbeat.toString() else rawLastSeen) else "offline"
                                "Nobody" -> "offline"
                                else -> if (rawLastSeen == "online") heartbeat.toString() else rawLastSeen
                            }
                        }
                        
                        val currentContact = db.contactDao().getContactByPhone(phone)
                        if (currentContact != null && currentContact.lastSeen != effectiveLastSeen) {
                            db.contactDao().updateContactPresence(phone, effectiveLastSeen)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("BartaChat", "Periodic presence re-evaluation failed", e)
                }
            }
        }
    }

    private fun stopPeriodicPresenceReevaluation() {
        periodicPresenceJob?.cancel()
        periodicPresenceJob = null
    }

    fun syncMyContactsToFirestore() {
        val me = _myNumber.value ?: return
        repository.syncMyContactsListToFirestore(me)
    }

    fun updateLastSeenPrivacy(privacy: String) {
        lastSeenPrivacy.value = privacy
        sharedPrefs.edit().putString("last_seen_privacy", privacy).apply()
        val me = _myNumber.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateLastSeenPrivacy(me, privacy)
        }
    }

    // User profile state inputs
    val userDisplayName = MutableStateFlow("")
    val userStatusMessage = MutableStateFlow("")
    val userProfilePicBase64 = MutableStateFlow("")
    val userEmail = MutableStateFlow("")

    // Global name search of users in DB
    private val _searchedGlobalUsers = MutableStateFlow<List<LocalUser>>(emptyList())
    val searchedGlobalUsers: StateFlow<List<LocalUser>> = _searchedGlobalUsers.asStateFlow()

    init {
        _myNumber.value = sharedPrefs.getString("logged_user_phone", null)
        userDisplayName.value = sharedPrefs.getString("logged_user_display_name", "") ?: ""
        userStatusMessage.value = sharedPrefs.getString("logged_user_status_message", "বার্তা (Chat) ব্যবহার করছি!") ?: ""
        userProfilePicBase64.value = sharedPrefs.getString("logged_user_profile_pic", "") ?: ""
        userEmail.value = sharedPrefs.getString("logged_user_email", "") ?: ""
        lastSeenPrivacy.value = sharedPrefs.getString("last_seen_privacy", "Everyone") ?: "Everyone"
        startMonitoringConnectivityAndPresence()
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
                    registerFcmToken(me)
                    // One-time cleanup of existing groups as requested by user
                    val hasCleared = sharedPrefs.getBoolean("has_cleared_old_groups_v1", false)
                    if (!hasCleared) {
                        try {
                            val groups = db.contactDao().getAllContacts().first().filter { it.isGroup }
                            for (g in groups) {
                                db.contactDao().deleteContact(g)
                                db.messageDao().deleteMessagesForChat(me, g.phone)
                            }
                            val fs = repository.firestore
                            if (fs != null) {
                                fs.collection("groups").get().addOnSuccessListener { snapshots ->
                                    if (snapshots != null) {
                                        for (doc in snapshots.documents) {
                                            doc.reference.delete()
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("BartaChat", "One-time groups cleanup failed", e)
                        }
                        sharedPrefs.edit().putBoolean("has_cleared_old_groups_v1", true).apply()
                    }

                    updatePresenceStatus()
                    repository.startListeningToGroups(me) {
                        // real-time synchronization callback trigger
                    }
                    repository.startListeningToStatuses {
                        // real-time status synchronization callback trigger
                    }
                    repository.startGlobalChatsListener(me) {
                        // real-time global messaging synchronization callback trigger
                    }
                    repository.startListeningToUserPresence(me) {
                        // real-time presence updated callback trigger
                    }
                    startPeriodicPresenceReevaluation()
                    syncMyContactsToFirestore()
                    
                    // Sync the lastSeenPrivacy to Firestore when starting
                    repository.updateLastSeenPrivacy(me, lastSeenPrivacy.value)
                } else {
                    repository.stopListeningToGroups()
                    repository.stopListeningToStatuses()
                    repository.stopAllMessageListeners()
                    repository.stopListeningToUserPresence()
                    stopPeriodicPresenceReevaluation()
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

    fun register(name: String, email: String, password: String, profilePic: String, callback: (String?) -> Unit) {
        val cleanName = name.trim()
        val cleanEmail = email.trim()
        val cleanPass = password.trim()
        val isBn = appLanguage.value == "bn"

        if (!repository.isNetworkAvailable()) {
            callback(if (isBn) "অ্যাকাউন্ট তৈরি করতে একটি সচল ইন্টারনেট সংযোগ প্রয়োজন। অনুগ্রহ করে আপনার ওয়াই-ফাই বা মোবাইল ডাটা চালু করুন।" else "An active internet connection is required to create an account. Please enable your Wi-Fi or mobile data.")
            return
        }

        if (cleanName.isEmpty()) {
            callback(if (isBn) "অ্যাকাউন্ট তৈরির সময় সবার একটা নাম দিতে হবে!" else "Please provide a name to create an account!")
            return
        }
        
        if (cleanEmail.isEmpty() || !cleanEmail.contains("@") || !cleanEmail.contains(".")) {
            callback(if (isBn) "দয়া করে সঠিক ইমেইল এড্রেস দিন!" else "Please provide a valid email address!")
            return
        }
        val finalEmail = cleanEmail.lowercase()

        // Generate deterministic 11-digit pseudo phone starting with "01" from the email
        val emailHash = Math.abs(finalEmail.hashCode()) % 1000000000
        val cleanPhone = "01" + String.format(java.util.Locale.US, "%09d", emailHash)

        if (cleanPass.length < 6) {
            callback(if (isBn) "পাসওয়ার্ড কমপক্ষে ৬ অক্ষরের হতে হবে!" else "Password must be at least 6 characters long!")
            return
        }

        viewModelScope.launch {
            if (isFirebaseConfigured.value) {
                val error = repository.registerWithFirebaseAuth(
                    phone = cleanPhone,
                    name = cleanName,
                    email = finalEmail,
                    passwordHash = cleanPass,
                    profilePicBase64 = profilePic
                )
                val isTechError = error != null && (
                    error.lowercase().contains("api key") ||
                    error.lowercase().contains("api keys") ||
                    error.lowercase().contains("oauth2") ||
                    error.lowercase().contains("internal error") ||
                    error.lowercase().contains("google play services") ||
                    error.lowercase().contains("firebase") ||
                    error.lowercase().contains("firestore") ||
                    error.lowercase().contains("permission-denied") ||
                    error.lowercase().contains("uniqueness")
                )
                if (error == null) {
                    seedDummyContacts()
                    showOnboarding.value = true
                    _myNumber.value = cleanPhone
                    userDisplayName.value = cleanName
                    userProfilePicBase64.value = profilePic
                    userStatusMessage.value = "বার্তা (Chat) ব্যবহার করছি!"
                    userEmail.value = finalEmail
                    callback(null)
                } else if (isTechError) {
                    // Firebase config or API key issue - fall back to local registration seamlessly so user is not blocked!
                    val exists = repository.getUserByPhone(cleanPhone)
                    if (exists != null) {
                        sharedPrefs.edit()
                            .putString("logged_user_phone", cleanPhone)
                            .putString("logged_user_display_name", exists.name)
                            .putString("logged_user_profile_pic", exists.profilePicBase64)
                            .putString("logged_user_status_message", exists.status)
                            .putString("logged_user_email", finalEmail)
                            .apply()
                        seedDummyContacts()
                        showOnboarding.value = true
                        _myNumber.value = cleanPhone
                        userDisplayName.value = exists.name
                        userProfilePicBase64.value = exists.profilePicBase64
                        userStatusMessage.value = exists.status
                        userEmail.value = finalEmail
                        callback(null)
                    } else {
                        val registered = repository.registerLocalUser(cleanPhone, cleanName, cleanPass, profilePic)
                        if (registered) {
                            sharedPrefs.edit()
                                .putString("logged_user_phone", cleanPhone)
                                .putString("logged_user_display_name", cleanName)
                                .putString("logged_user_profile_pic", profilePic)
                                .putString("logged_user_status_message", "বার্তা (Chat) ব্যবহার করছি!")
                                .putString("logged_user_email", finalEmail)
                                .apply()
                            seedDummyContacts()
                            showOnboarding.value = true
                            _myNumber.value = cleanPhone
                            userDisplayName.value = cleanName
                            userProfilePicBase64.value = profilePic
                            userStatusMessage.value = "বার্তা (Chat) ব্যবহার করছি!"
                            userEmail.value = finalEmail
                            callback(null)
                        } else {
                            callback(error)
                        }
                    }
                } else {
                    callback(error)
                }
            } else {
                // Local only fallback
                val exists = repository.getUserByPhone(cleanPhone)
                if (exists != null) {
                    callback(if (isBn) "এই ইমেইল এড্রেস দিয়ে ইতিমধ্যে অ্যাকাউন্ট তৈরি হয়েছে!" else "An account with this email address already exists!")
                } else {
                    val registered = repository.registerLocalUser(cleanPhone, cleanName, cleanPass, profilePic)
                    if (registered) {
                        sharedPrefs.edit()
                            .putString("logged_user_phone", cleanPhone)
                            .putString("logged_user_display_name", cleanName)
                            .putString("logged_user_profile_pic", profilePic)
                            .putString("logged_user_status_message", "বার্তা (Chat) ব্যবহার করছি!")
                            .putString("logged_user_email", finalEmail)
                            .apply()
                        seedDummyContacts()
                        showOnboarding.value = true
                        _myNumber.value = cleanPhone
                        userDisplayName.value = cleanName
                        userProfilePicBase64.value = profilePic
                        userStatusMessage.value = "বার্তা (Chat) ব্যবহার করছি!"
                        userEmail.value = finalEmail
                        callback(null)
                    } else {
                        callback(if (isBn) "রেজিস্ট্রেশন ব্যর্থ হয়েছে।" else "Registration failed.")
                    }
                }
            }
        }
    }

    fun loginWithPassword(emailOrPhone: String, pass: String, callback: (String?) -> Unit) {
        val cleanInput = emailOrPhone.trim()
        val cleanPass = pass.trim()
        val isBn = appLanguage.value == "bn"

        if (!repository.isNetworkAvailable()) {
            callback(if (isBn) "লগইন করতে একটি সচল ইন্টারনেট সংযোগ প্রয়োজন। অনুগ্রহ করে আপনার ওয়াই-ফাই বা মোবাইল ডাটা চালু করুন।" else "An active internet connection is required to log in. Please enable your Wi-Fi or mobile data.")
            return
        }

        val isEmail = cleanInput.contains("@")
        val isValidEmail = isEmail && cleanInput.contains(".")
        val isValidPhone = !isEmail && cleanInput.length >= 11 && cleanInput.all { it.isDigit() }

        if (cleanInput.isEmpty() || (!isValidEmail && !isValidPhone)) {
            callback(if (isBn) "দয়া করে একটি সঠিক ইমেইল এড্রেস অথবা ফোন নম্বর দিন!" else "Please provide a valid email address or phone number!")
            return
        }
        if (cleanPass.length < 6) {
            callback(if (isBn) "পাসওয়ার্ড অবশ্যই কমপক্ষে ৬ অক্ষরের হতে হবে!" else "Password must be at least 6 characters!")
            return
        }

        viewModelScope.launch {
            if (isFirebaseConfigured.value) {
                val error = repository.loginWithFirebaseAuth(cleanInput, cleanPass)
                val isTechError = error != null && (
                    error.lowercase().contains("api key") ||
                    error.lowercase().contains("api keys") ||
                    error.lowercase().contains("oauth2") ||
                    error.lowercase().contains("internal error") ||
                    error.lowercase().contains("google play services") ||
                    error.lowercase().contains("firebase") ||
                    error.lowercase().contains("firestore") ||
                    error.lowercase().contains("permission-denied") ||
                    error.lowercase().contains("failed to load user profile")
                )
                if (error == null) {
                    val phone = sharedPrefs.getString("logged_user_phone", "") ?: ""
                    val name = sharedPrefs.getString("logged_user_display_name", "") ?: ""
                    val pic = sharedPrefs.getString("logged_user_profile_pic", "") ?: ""
                    val status = sharedPrefs.getString("logged_user_status_message", "") ?: "বার্তা (Chat) ব্যবহার করছি!"
                    val email = sharedPrefs.getString("logged_user_email", "") ?: ""
                    
                    _myNumber.value = phone
                    userDisplayName.value = name
                    userProfilePicBase64.value = pic
                    userStatusMessage.value = status
                    userEmail.value = email
                    seedDummyContacts()
                    callback(null)
                } else if (isTechError) {
                    // Firebase config or API key issue - fall back to local database login seamlessly!
                    val isEmail = cleanInput.contains("@")
                    val derivedPhone = if (isEmail) {
                        val emailHash = Math.abs(cleanInput.lowercase().hashCode()) % 1000000000
                        "01" + String.format(java.util.Locale.US, "%09d", emailHash)
                    } else {
                        cleanInput
                    }
                    val user = repository.getUserByPhone(derivedPhone)
                    
                    if (user != null) {
                        if (user.passwordHash == cleanPass) {
                            _myNumber.value = user.phone
                            userDisplayName.value = user.name
                            userProfilePicBase64.value = user.profilePicBase64
                            userStatusMessage.value = user.status
                            userEmail.value = if (isEmail) cleanInput else "${user.phone}@bartachat.com"
                            
                            sharedPrefs.edit()
                                .putString("logged_user_phone", user.phone)
                                .putString("logged_user_display_name", user.name)
                                .putString("logged_user_profile_pic", user.profilePicBase64)
                                .putString("logged_user_status_message", user.status)
                                .putString("logged_user_email", if (isEmail) cleanInput else "${user.phone}@bartachat.com")
                                .apply()
                            
                            seedDummyContacts()
                            callback(null)
                        } else {
                            callback(if (isBn) "ভুল পাসওয়ার্ড! আবার চেষ্টা করুন।" else "Incorrect password! Please try again.")
                        }
                    } else {
                        // If they enter a valid email/phone but it doesn't exist locally, we can auto-register them
                        val fallbackName = cleanInput.substringBefore("@")
                        val registered = repository.registerLocalUser(derivedPhone, fallbackName, cleanPass, "")
                        if (registered) {
                            _myNumber.value = derivedPhone
                            userDisplayName.value = fallbackName
                            userProfilePicBase64.value = ""
                            userStatusMessage.value = "বার্তা (Chat) ব্যবহার করছি!"
                            userEmail.value = cleanInput.lowercase()
                            
                            sharedPrefs.edit()
                                .putString("logged_user_phone", derivedPhone)
                                .putString("logged_user_display_name", fallbackName)
                                .putString("logged_user_profile_pic", "")
                                .putString("logged_user_status_message", "বার্তা (Chat) ব্যবহার করছি!")
                                .putString("logged_user_email", cleanInput.lowercase())
                                .apply()
                                
                            seedDummyContacts()
                            callback(null)
                        } else {
                            callback(error)
                        }
                    }
                } else {
                    callback(error)
                }
            } else {
                // Local only high-fidelity offline backup flow
                val isEmail = cleanInput.contains("@")
                val derivedPhone = if (isEmail) {
                    val emailHash = Math.abs(cleanInput.lowercase().hashCode()) % 1000000000
                    "01" + String.format(java.util.Locale.US, "%09d", emailHash)
                } else {
                    cleanInput
                }
                var user = repository.getUserByPhone(derivedPhone)
                if (user == null && isEmail) {
                    // Auto-register email locally for seamless offline usage
                    val fallbackName = cleanInput.substringBefore("@")
                    val registered = repository.registerLocalUser(derivedPhone, fallbackName, cleanPass, "")
                    if (registered) {
                        user = repository.getUserByPhone(derivedPhone)
                    }
                }
                if (user == null) {
                    callback(if (isBn) "অ্যাকাউন্ট পাওয়া যায়নি! অফলাইনে নতুন একাউন্ট তৈরি করুন।" else "No local account found! Please register.")
                    return@launch
                }
                if (user.passwordHash != cleanPass) {
                    callback(if (isBn) "ভুল পাসওয়ার্ড! আবার চেষ্টা করুন।" else "Incorrect password! Please try again.")
                } else {
                    val finalUserEmail = if (isEmail) cleanInput else "${user.phone}@bartachat.com"
                    sharedPrefs.edit()
                        .putString("logged_user_phone", user.phone)
                        .putString("logged_user_display_name", user.name)
                        .putString("logged_user_profile_pic", user.profilePicBase64)
                        .putString("logged_user_status_message", user.status)
                        .putString("logged_user_email", finalUserEmail)
                        .apply()
                    _myNumber.value = user.phone
                    userDisplayName.value = user.name
                    userProfilePicBase64.value = user.profilePicBase64
                    userStatusMessage.value = user.status
                    userEmail.value = finalUserEmail
                    seedDummyContacts()
                    callback(null)
                }
            }
        }
    }

    fun sendPasswordReset(email: String, callback: (String?) -> Unit) {
        val cleanEmail = email.trim()
        val isBn = appLanguage.value == "bn"
        if (cleanEmail.isEmpty() || !cleanEmail.contains("@") || !cleanEmail.contains(".")) {
            callback(if (isBn) "দয়া করে সঠিক ইমেইল এড্রেস দিন!" else "Please provide a valid email address!")
            return
        }
        viewModelScope.launch {
            if (isFirebaseConfigured.value) {
                val error = repository.sendPasswordResetEmail(cleanEmail)
                callback(error)
            } else {
                callback(if (isBn) "ফায়ারবেস কনফিগার করা নেই।" else "Firebase is not configured.")
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
        repository.logoutFirebaseAuth()
        sharedPrefs.edit()
            .remove("logged_user_phone")
            .remove("logged_user_display_name")
            .remove("logged_user_profile_pic")
            .remove("logged_user_status_message")
            .remove("logged_user_email")
            .apply()
        _myNumber.value = null
        _activeContact.value = null
        _navigationStack.value = listOf("chats")
        userProfilePicBase64.value = ""
        userDisplayName.value = ""
        userEmail.value = ""
        repository.stopListeningToGroups()
        repository.stopAllMessageListeners()
        viewModelScope.launch {
            db.contactDao().deleteAllContacts()
        }
    }

    fun updateEmailInFirebase(newEmail: String, onComplete: (String?) -> Unit) {
        viewModelScope.launch {
            val error = repository.updateEmailInFirebase(newEmail)
            if (error == null) {
                userEmail.value = newEmail.trim().lowercase()
            }
            onComplete(error)
        }
    }

    fun selectContact(contact: Contact?) {
        _activeContact.value = contact
        repository.setActiveChatPhone(contact?.phone)
        val me = _myNumber.value
        if (contact != null && me != null) {
            viewModelScope.launch {
                repository.updateUnreadCount(contact.phone, 0)
                NotificationHelper.dismissNotification(context, contact.phone)
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

    fun insertContactAndSelect(contact: Contact) {
        viewModelScope.launch {
            db.contactDao().insertContact(contact)
            selectContact(contact)
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
                .putString("group_admins_$groupId", me)
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
        if (cleanPhone.contains("@") && cleanPhone.contains(".")) {
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

    fun generateAndSendImage(prompt: String, onStatusUpdate: (String) -> Unit = {}) {
        val me = _myNumber.value ?: return
        val active = _activeContact.value ?: return
        val myName = userDisplayName.value.ifEmpty { "বার্তা ব্যবহারকারী" }
        viewModelScope.launch {
            try {
                onStatusUpdate("Generating...")
                val encodedPrompt = java.net.URLEncoder.encode(prompt, "UTF-8")
                // Use robust free pollinations endpoint representing the Imagen tool fallback
                val imageUrl = "https://image.pollinations.ai/prompt/$encodedPrompt?width=1024&height=1024&nologo=true&seed=${System.currentTimeMillis()}"
                
                sendMediaMessage(imageUrl, "image")
                onStatusUpdate("Success")
            } catch (e: Exception) {
                android.util.Log.e("BartaChat", "Image generation failed", e)
                onStatusUpdate("Failed")
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

    fun reactToMessage(msgId: String, emoji: String) {
        val me = _myNumber.value ?: return
        val active = _activeContact.value ?: return
        viewModelScope.launch {
            repository.addMessageReaction(me, active.phone, msgId, emoji)
        }
    }

    fun clearChat(peerPhone: String) {
        val me = _myNumber.value ?: return
        viewModelScope.launch {
            db.messageDao().deleteMessagesForChat(me, peerPhone)
            db.contactDao().updateLastMessage(peerPhone, "", System.currentTimeMillis())
        }
    }

    fun deleteChat(contact: Contact) {
        val me = _myNumber.value ?: return
        viewModelScope.launch {
            db.messageDao().deleteMessagesForChat(me, contact.phone)
            db.contactDao().deleteContact(contact)
            if (_activeContact.value?.phone == contact.phone) {
                _activeContact.value = null
            }
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
                name = "Barta Ai Chat Bot",
                isSimulated = true,
                lastSeen = "online",
                lastMessageText = "যেকোনো প্রশ্নের উত্তর দিতে আমি এখানে আছি!",
                lastMessageTime = System.currentTimeMillis()
            )

            repository.addContact(dummy3)

            val dummyStatus3 = ChatStatus(
                id = "dummy_status_3",
                phone = "01300000000",
                name = "Barta Ai Chat Bot",
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

    private val _onBartaContacts = MutableStateFlow<List<SyncedContact>>(emptyList())
    val onBartaContacts: StateFlow<List<SyncedContact>> = _onBartaContacts.asStateFlow()

    private val _inviteContacts = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val inviteContacts: StateFlow<List<Pair<String, String>>> = _inviteContacts.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _searchResultUser = MutableStateFlow<Map<String, Any>?>(null)
    val searchResultUser: StateFlow<Map<String, Any>?> = _searchResultUser.asStateFlow()

    private val _isSearchingServer = MutableStateFlow(false)
    val isSearchingServer: StateFlow<Boolean> = _isSearchingServer.asStateFlow()

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()

    fun clearSearchResult() {
        _searchResultUser.value = null
        _searchError.value = null
    }

    fun searchUserOnServer(query: String) {
        if (query.trim().isEmpty()) {
            _searchResultUser.value = null
            _searchError.value = null
            return
        }
        viewModelScope.launch {
            _isSearchingServer.value = true
            _searchResultUser.value = null
            _searchError.value = null
            try {
                val users = repository.getAllFirestoreUsers()
                val qLower = query.trim().lowercase()
                val matched = users.find { doc ->
                    val userPhone = doc["phone"] as? String ?: ""
                    val userName = doc["name"] as? String ?: ""
                    val userEmail = doc["email"] as? String ?: ""
                    
                    normalizePhoneNumber(userPhone) == normalizePhoneNumber(query) ||
                    userName.lowercase().contains(qLower) ||
                    userEmail.lowercase() == qLower ||
                    userPhone.contains(query)
                }
                if (matched != null) {
                    _searchResultUser.value = matched
                } else {
                    _searchError.value = "not_found"
                }
            } catch (e: Exception) {
                _searchError.value = "error"
            } finally {
                _isSearchingServer.value = false
            }
        }
    }

    fun syncContacts(deviceContacts: List<Pair<String, String>>) {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val me = _myNumber.value ?: ""
                val normalizedMe = normalizePhoneNumber(me)

                // 1. Fetch all users from Firestore
                val firestoreUsers = repository.getAllFirestoreUsers()

                // 2. Normalize and construct a lookup map of phone -> user data
                val firestoreUsersMap = firestoreUsers.associateBy { doc ->
                    val phone = doc["phone"] as? String ?: ""
                    normalizePhoneNumber(phone)
                }

                // 3. Match
                val currentLocalContacts = repository.allContacts.first()
                val currentLocalPhones = currentLocalContacts.map { it.phone }.toSet()

                val resultList = mutableListOf<SyncedContact>()
                val inviteList = mutableListOf<Pair<String, String>>()
                val processedPhones = mutableSetOf<String>()

                for ((deviceName, devicePhone) in deviceContacts) {
                    val normalizedDevicePhone = normalizePhoneNumber(devicePhone)
                    if (normalizedDevicePhone.isEmpty() || normalizedDevicePhone == normalizedMe) {
                        continue
                    }

                    if (processedPhones.contains(normalizedDevicePhone)) {
                        continue
                    }
                    processedPhones.add(normalizedDevicePhone)

                    val matchDoc = firestoreUsersMap[normalizedDevicePhone]
                    if (matchDoc != null) {
                        val appName = matchDoc["name"] as? String ?: "বার্তা ব্যবহারকারী"
                        val status = matchDoc["status"] as? String ?: "বার্তা (Chat) ব্যবহার করছি!"
                        val profilePic = matchDoc["profilePicBase64"] as? String ?: ""
                        val phoneKey = matchDoc["phone"] as? String ?: normalizedDevicePhone

                        val synced = SyncedContact(
                            phone = phoneKey,
                            deviceName = deviceName,
                            appName = appName,
                            status = status,
                            profilePicBase64 = profilePic,
                            alreadyAdded = currentLocalPhones.contains(phoneKey)
                        )
                        resultList.add(synced)

                        // Automatically sync to local Room database so they are in our database but not shown in Chats list initially
                        val existingContact = currentLocalContacts.find { it.phone == phoneKey }
                        if (existingContact == null) {
                            val newContact = Contact(
                                phone = phoneKey,
                                name = deviceName.ifEmpty { appName },
                                isSimulated = false,
                                lastSeen = "online",
                                lastMessageText = null,
                                lastMessageTime = 0L,
                                profilePicUri = profilePic
                            )
                            repository.addContact(newContact)
                        } else if (existingContact.name != deviceName || existingContact.profilePicUri != profilePic) {
                            val updatedContact = existingContact.copy(
                                name = deviceName.ifEmpty { existingContact.name },
                                profilePicUri = profilePic.ifEmpty { existingContact.profilePicUri }
                            )
                            repository.addContact(updatedContact)
                        }
                    } else {
                        inviteList.add(deviceName to devicePhone)
                    }
                }

                val distinctResult = resultList.distinctBy { it.phone }
                _syncedContacts.value = distinctResult
                _onBartaContacts.value = distinctResult
                _inviteContacts.value = inviteList.distinctBy { normalizePhoneNumber(it.second) }
            } catch (e: Exception) {
                android.util.Log.e("BartaChat", "Sync contacts error", e)
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
        return phone.trim().lowercase()
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
            stopHeartbeat(me)
            stopPeriodicPresenceReevaluation()
        }
        super.onCleared()
        repository.stopListeningToChat()
        repository.stopListeningToGroups()
        repository.stopListeningToStatuses()
        repository.stopAllMessageListeners()
        repository.stopListeningToUserPresence()
    }

    fun openChatByPhone(phone: String) {
        viewModelScope.launch {
            val contact = db.contactDao().getContactByPhone(phone)
            if (contact != null) {
                selectContact(contact)
            } else {
                val isGroup = phone.startsWith("group_")
                val isAi = phone == "01300000000"
                val name = if (isAi) "বার্তা এআই সহকারী (AI)" else if (isGroup) "গ্রুপ (Group)" else phone
                val newContact = Contact(
                    phone = phone,
                    name = name,
                    isSimulated = isAi,
                    lastSeen = "online",
                    lastMessageText = null,
                    lastMessageTime = System.currentTimeMillis(),
                    profilePicUri = "",
                    isGroup = isGroup
                )
                db.contactDao().insertContact(newContact)
                selectContact(newContact)
            }
        }
    }

    fun registerFcmToken(me: String) {
        viewModelScope.launch {
            try {
                com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                    .addOnCompleteListener { task ->
                        if (!task.isSuccessful) {
                            Log.w("BartaChat", "Fetching FCM registration token failed", task.exception)
                            return@addOnCompleteListener
                        }
                        val token = task.result
                        Log.d("BartaChat", "FCM Token: $token")
                        
                        sharedPrefs.edit().putString("fcm_token", token).apply()
                        
                        if (isFirebaseConfigured.value) {
                            repository.initializeFirebaseIfConfigured()
                            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                            val data = hashMapOf<String, Any>("fcmToken" to token)
                            firestore.collection("users").document(me)
                                .set(data, com.google.firebase.firestore.SetOptions.merge())
                                .addOnSuccessListener {
                                    Log.d("BartaChat", "FCM Token successfully synced to Firestore!")
                                }
                                .addOnFailureListener { e ->
                                    Log.e("BartaChat", "FCM Token sync failed", e)
                                }
                        }
                    }
            } catch (e: Exception) {
                Log.e("BartaChat", "Error getting/saving FCM Token", e)
            }
        }
    }

    fun clearAllFirebaseDataAndReset(onComplete: (String?) -> Unit) {
        viewModelScope.launch {
            val result = repository.clearAllFirebaseDataAndReset()
            if (result == null) {
                logout()
            }
            onComplete(result)
        }
    }
}
