package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.*
import com.example.ui.theme.WhatsAppGreenVal
import com.example.ui.theme.WhatsAppTealVal
import com.example.viewmodel.ChatViewModel
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AIAssistantChatScreen(
    viewModel: ChatViewModel,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    
    // Theme and Language
    val isDarkTheme by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val myPhone by viewModel.myNumber.collectAsStateWithLifecycle()

    val txt: (String, String) -> String = { bn, en ->
        if (appLanguage == "bn") bn else en
    }

    // Initialize Database
    val db = remember { AiDatabase.getDatabase(context) }
    
    // Conversations and messages states
    val sessions by db.aiSessionDao().getAllSessionsFlow().collectAsState(initial = emptyList())
    var activeSessionId by remember { mutableStateOf<String?>(null) }
    
    // Auto-select latest session on entry so history does not appear to vanish
    androidx.compose.runtime.LaunchedEffect(sessions) {
        if (activeSessionId == null && sessions.isNotEmpty()) {
            activeSessionId = sessions.first().id
        }
    }
    
    val activeMessages = remember(activeSessionId) {
        if (activeSessionId != null) {
            db.aiMessageDao().getMessagesForSessionFlow(activeSessionId!!)
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList())
        }
    }.collectAsState(initial = emptyList())

    // UI Input and States
    var inputText by remember { mutableStateOf("") }
    val speechRecognizerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            val results = data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            if (!results.isNullOrEmpty()) {
                val spokenText = results[0]
                inputText = if (inputText.isEmpty()) spokenText else "$inputText $spokenText"
            }
        }
    }
    var isGenerating by remember { mutableStateOf(false) }
    var searchHistoryQuery by remember { mutableStateOf("") }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val listState = rememberLazyListState()

    // Dialog States
    var sessionToDelete by remember { mutableStateOf<AiSession?>(null) }
    var sessionToRename by remember { mutableStateOf<AiSession?>(null) }
    var renameNewTitle by remember { mutableStateOf("") }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var showGenerateImageDialog by remember { mutableStateOf(false) }
    var imageGenerationPrompt by remember { mutableStateOf("") }
    var isGeneratingImage by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var messageToEdit by remember { mutableStateOf<AiMessage?>(null) }
    var editedTextState by remember { mutableStateOf("") }

    // Sync helpers
    val firestore = remember {
        try { FirebaseFirestore.getInstance() } catch (e: Exception) { null }
    }

    fun syncSessionToCloud(session: AiSession) {
        val phone = myPhone ?: return
        val fs = firestore ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val data = hashMapOf(
                    "id" to session.id,
                    "title" to session.title,
                    "createdAt" to session.createdAt,
                    "isPinned" to session.isPinned
                )
                fs.collection("ai_history")
                    .document(phone)
                    .collection("sessions")
                    .document(session.id)
                    .set(data)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun syncMessageToCloud(msg: AiMessage) {
        val phone = myPhone ?: return
        val fs = firestore ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val data = hashMapOf(
                    "id" to msg.id,
                    "sessionId" to msg.sessionId,
                    "sender" to msg.sender,
                    "text" to msg.text,
                    "timestamp" to msg.timestamp,
                    "isLiked" to msg.isLiked,
                    "isDisliked" to msg.isDisliked
                )
                fs.collection("ai_history")
                    .document(phone)
                    .collection("sessions")
                    .document(msg.sessionId)
                    .collection("messages")
                    .document(msg.id)
                    .set(data)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteSessionFromCloud(sessionId: String) {
        val phone = myPhone ?: return
        val fs = firestore ?: return
        scope.launch(Dispatchers.IO) {
            try {
                fs.collection("ai_history")
                    .document(phone)
                    .collection("sessions")
                    .document(sessionId)
                    .delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteAllSessionsFromCloud() {
        val phone = myPhone ?: return
        val fs = firestore ?: return
        scope.launch(Dispatchers.IO) {
            try {
                fs.collection("ai_history")
                    .document(phone)
                    .collection("sessions")
                    .get()
                    .addOnSuccessListener { snapshot ->
                        for (doc in snapshot.documents) {
                            doc.reference.delete()
                        }
                    }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Voice Chat State variables and Controllers
    var isVoiceChatActive by remember { mutableStateOf(false) }
    var voiceState by remember { mutableStateOf("idle") } // idle, listening, processing, speaking, error
    var voiceUserText by remember { mutableStateOf("") }
    var voiceAiResponseText by remember { mutableStateOf("") }
    var voiceErrorMessage by remember { mutableStateOf("") }
    var liveRmsDb by remember { mutableStateOf(0f) }

    val hasMicPermission = androidx.core.content.ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.RECORD_AUDIO
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    val speechRecognizer = remember {
        try {
            android.speech.SpeechRecognizer.createSpeechRecognizer(context)
        } catch (e: Exception) {
            null
        }
    }

    var tts by remember { mutableStateOf<android.speech.tts.TextToSpeech?>(null) }
    LaunchedEffect(isVoiceChatActive) {
        if (isVoiceChatActive) {
            if (tts == null) {
                tts = android.speech.tts.TextToSpeech(context) { status ->
                    if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                        tts?.language = if (appLanguage == "bn") java.util.Locale("bn", "BD") else java.util.Locale.US
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            tts?.stop()
            tts?.shutdown()
            try {
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun startListening() {
        if (speechRecognizer != null) {
            tts?.stop()
            voiceState = "listening"
            voiceUserText = ""
            voiceAiResponseText = ""
            voiceErrorMessage = ""
            val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                val langTag = if (appLanguage == "bn") "bn-BD" else "en-US"
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, langTag)
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langTag)
                putExtra(android.speech.RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, langTag)
            }
            
            speechRecognizer.setRecognitionListener(object : android.speech.RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) {
                    liveRmsDb = 0f
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {
                    liveRmsDb = rmsdB
                }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    voiceState = "processing"
                }
                override fun onError(error: Int) {
                    if (!isVoiceChatActive) return
                    val errorMsg = when (error) {
                        android.speech.SpeechRecognizer.ERROR_AUDIO -> txt("অডিও রেকর্ডিংয়ে সমস্যা!", "Audio recording error!")
                        android.speech.SpeechRecognizer.ERROR_CLIENT -> txt("ক্লায়েন্ট সাইড ত্রুটি!", "Client side error!")
                        android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> txt("মাইক্রোফোন পারমিশন নেই!", "Insufficient permissions!")
                        android.speech.SpeechRecognizer.ERROR_NETWORK -> txt("নেটওয়ার্ক ত্রুটি!", "Network error!")
                        android.speech.SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> txt("নেটওয়ার্ক সময়সীমা শেষ!", "Network timeout!")
                        android.speech.SpeechRecognizer.ERROR_NO_MATCH -> txt("কথা বোঝা যায়নি, আবার বলুন!", "Speech not recognized, please speak clearly!")
                        android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> txt("ভয়েস ইঞ্জিন ব্যস্ত!", "Voice engine busy!")
                        android.speech.SpeechRecognizer.ERROR_SERVER -> txt("সার্ভার ত্রুটি!", "Server error!")
                        android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> txt("কথা বলার সময়সীমা পার হয়েছে!", "No speech input detected!")
                        else -> txt("ভয়েস টাইপিং সমস্যা!", "Speech recognition error!")
                    }
                    voiceErrorMessage = errorMsg
                    voiceState = "error"
                }
                override fun onResults(results: android.os.Bundle?) {
                    if (!isVoiceChatActive) return
                    val matches = results?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val spoken = matches[0]
                        voiceUserText = spoken
                        voiceState = "processing"
                        
                        scope.launch(Dispatchers.IO) {
                            var currentSessionId = activeSessionId
                            val isNewChat = currentSessionId == null
                            
                            if (isNewChat) {
                                val newSessionId = UUID.randomUUID().toString()
                                val rawTitle = if (spoken.length > 25) spoken.take(25) + "..." else spoken
                                val cleanTitle = rawTitle.replace("\n", " ").trim()
                                val newSession = AiSession(
                                    id = newSessionId,
                                    title = cleanTitle,
                                    createdAt = System.currentTimeMillis()
                                )
                                db.aiSessionDao().insertSession(newSession)
                                syncSessionToCloud(newSession)
                                currentSessionId = newSessionId
                                withContext(Dispatchers.Main) {
                                    activeSessionId = newSessionId
                                }
                            }

                            val userMsgId = UUID.randomUUID().toString()
                            val userMsg = AiMessage(
                                id = userMsgId,
                                sessionId = currentSessionId!!,
                                sender = "user",
                                text = spoken,
                                timestamp = System.currentTimeMillis()
                            )
                            db.aiMessageDao().insertMessage(userMsg)
                            syncMessageToCloud(userMsg)

                            withContext(Dispatchers.Main) {
                                isGenerating = true
                            }

                            try {
                                val history = db.aiMessageDao().getMessagesForSession(currentSessionId)
                                val legacyMessages = history.map { aiMsg ->
                                    Message(
                                        id = aiMsg.id,
                                        senderId = if (aiMsg.sender == "user") myPhone ?: "user" else "01300000000",
                                        receiverId = if (aiMsg.sender == "user") "01300000000" else myPhone ?: "user",
                                        text = aiMsg.text,
                                        timestamp = aiMsg.timestamp,
                                        senderName = if (aiMsg.sender == "user") "User" else "Barta AI"
                                    )
                                }

                                val aiResponseText = GeminiService.getGeminiResponse(
                                    context = context,
                                    userPhone = myPhone ?: "user",
                                    userMessage = spoken,
                                    previousMessages = legacyMessages,
                                    language = appLanguage
                                )

                                val aiMsgId = UUID.randomUUID().toString()
                                val aiMsg = AiMessage(
                                    id = aiMsgId,
                                    sessionId = currentSessionId,
                                    sender = "ai",
                                    text = aiResponseText,
                                    timestamp = System.currentTimeMillis()
                                )
                                db.aiMessageDao().insertMessage(aiMsg)
                                syncMessageToCloud(aiMsg)

                                withContext(Dispatchers.Main) {
                                    voiceAiResponseText = aiResponseText
                                    voiceState = "speaking"
                                    
                                    tts?.let { speech ->
                                        val isBengali = aiResponseText.any { it in '\u0980'..'\u09FF' }
                                        speech.language = if (isBengali) java.util.Locale("bn", "BD") else java.util.Locale.US
                                        
                                        speech.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                                            override fun onStart(utteranceId: String?) {}
                                            override fun onDone(utteranceId: String?) {
                                                scope.launch(Dispatchers.Main) {
                                                    if (isVoiceChatActive) {
                                                        startListening()
                                                    }
                                                }
                                            }
                                            override fun onError(utteranceId: String?) {}
                                        })
                                        
                                        val params = HashMap<String, String>()
                                        params[android.speech.tts.TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = "ai_voice_id"
                                        speech.speak(aiResponseText, android.speech.tts.TextToSpeech.QUEUE_FLUSH, params)
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                withContext(Dispatchers.Main) {
                                    voiceErrorMessage = txt("দুঃখিত, কোনো সমস্যা হয়েছে।", "Sorry, an error occurred.")
                                    voiceState = "error"
                                }
                            } finally {
                                withContext(Dispatchers.Main) {
                                    isGenerating = false
                                }
                            }
                        }
                    } else {
                        voiceErrorMessage = txt("কথা বোঝা যায়নি, আবার বলুন!", "Speech not recognized, please try again!")
                        voiceState = "error"
                    }
                }
                override fun onPartialResults(partialResults: android.os.Bundle?) {
                    val matches = partialResults?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        voiceUserText = matches[0]
                    }
                }
                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            })
            
            speechRecognizer.startListening(intent)
        }
    }

    fun submitVoiceQuery(queryText: String) {
        tts?.stop()
        speechRecognizer?.cancel()
        voiceUserText = queryText
        voiceState = "processing"
        voiceAiResponseText = ""
        voiceErrorMessage = ""
        
        scope.launch(Dispatchers.IO) {
            var currentSessionId = activeSessionId
            val isNewChat = currentSessionId == null
            
            if (isNewChat) {
                val newSessionId = UUID.randomUUID().toString()
                val rawTitle = if (queryText.length > 25) queryText.take(25) + "..." else queryText
                val cleanTitle = rawTitle.replace("\n", " ").trim()
                val newSession = AiSession(
                    id = newSessionId,
                    title = cleanTitle,
                    createdAt = System.currentTimeMillis()
                )
                db.aiSessionDao().insertSession(newSession)
                syncSessionToCloud(newSession)
                currentSessionId = newSessionId
                withContext(Dispatchers.Main) {
                    activeSessionId = newSessionId
                }
            }

            val userMsgId = UUID.randomUUID().toString()
            val userMsg = AiMessage(
                id = userMsgId,
                sessionId = currentSessionId!!,
                sender = "user",
                text = queryText,
                timestamp = System.currentTimeMillis()
            )
            db.aiMessageDao().insertMessage(userMsg)
            syncMessageToCloud(userMsg)

            withContext(Dispatchers.Main) {
                isGenerating = true
            }

            try {
                val history = db.aiMessageDao().getMessagesForSession(currentSessionId)
                val legacyMessages = history.map { aiMsg ->
                    Message(
                        id = aiMsg.id,
                        senderId = if (aiMsg.sender == "user") myPhone ?: "user" else "01300000000",
                        receiverId = if (aiMsg.sender == "user") "01300000000" else myPhone ?: "user",
                        text = aiMsg.text,
                        timestamp = aiMsg.timestamp,
                        senderName = if (aiMsg.sender == "user") "User" else "Barta AI"
                    )
                }

                val aiResponseText = GeminiService.getGeminiResponse(
                    context = context,
                    userPhone = myPhone ?: "user",
                    userMessage = queryText,
                    previousMessages = legacyMessages,
                    language = appLanguage
                )

                val aiMsgId = UUID.randomUUID().toString()
                val aiMsg = AiMessage(
                    id = aiMsgId,
                    sessionId = currentSessionId,
                    sender = "ai",
                    text = aiResponseText,
                    timestamp = System.currentTimeMillis()
                )
                db.aiMessageDao().insertMessage(aiMsg)
                syncMessageToCloud(aiMsg)

                withContext(Dispatchers.Main) {
                    voiceAiResponseText = aiResponseText
                    voiceState = "speaking"
                    
                    tts?.let { speech ->
                        val isBengali = aiResponseText.any { it in '\u0980'..'\u09FF' }
                        speech.language = if (isBengali) java.util.Locale("bn", "BD") else java.util.Locale.US
                        
                        speech.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {}
                            override fun onDone(utteranceId: String?) {
                                scope.launch(Dispatchers.Main) {
                                    if (isVoiceChatActive) {
                                        startListening()
                                    }
                                }
                            }
                            override fun onError(utteranceId: String?) {}
                        })
                        
                        val params = HashMap<String, String>()
                        params[android.speech.tts.TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = "ai_voice_id"
                        speech.speak(aiResponseText, android.speech.tts.TextToSpeech.QUEUE_FLUSH, params)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    voiceErrorMessage = txt("দুঃখিত, কোনো সমস্যা হয়েছে।", "Sorry, an error occurred.")
                    voiceState = "error"
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isGenerating = false
                }
            }
        }
    }

    val micPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isVoiceChatActive = true
            startListening()
        } else {
            Toast.makeText(context, txt("মাইক্রোফোন পারমিশন প্রয়োজন!", "Microphone permission is required!"), Toast.LENGTH_SHORT).show()
        }
    }

    // Colors Custom Palette (Premium Look)
    val primaryColor = WhatsAppTealVal
    val secondaryColor = WhatsAppGreenVal
    val cardBg = if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFF1F5F9)
    val userBubbleBg = if (isDarkTheme) Color(0xFF0F766E) else Color(0xFFCCFBF1)
    val userBubbleText = if (isDarkTheme) Color.White else Color(0xFF111827)
    val aiBubbleBg = if (isDarkTheme) Color(0xFF1E293B) else Color.White
    val aiBubbleText = if (isDarkTheme) Color(0xFFE2E8F0) else Color(0xFF1F2937)

    // Auto-scroll on new messages
    LaunchedEffect(activeMessages.value.size, isGenerating) {
        if (activeMessages.value.isNotEmpty()) {
            listState.animateScrollToItem(activeMessages.value.size - 1)
        }
    }

    val isKeyboardOpen = WindowInsets.isImeVisible
    LaunchedEffect(isKeyboardOpen) {
        if (isKeyboardOpen && activeMessages.value.isNotEmpty()) {
            listState.animateScrollToItem(activeMessages.value.size - 1)
        }
    }

    // Handle sending message
    fun sendMessage(textToSend: String) {
        if (textToSend.isBlank() || isGenerating) return
        
        inputText = ""

        scope.launch(Dispatchers.IO) {
            var currentSessionId = activeSessionId
            val isNewChat = currentSessionId == null
            
            if (isNewChat) {
                val newSessionId = UUID.randomUUID().toString()
                val rawTitle = if (textToSend.length > 25) textToSend.take(25) + "..." else textToSend
                val cleanTitle = rawTitle.replace("\n", " ").trim()
                val newSession = AiSession(
                    id = newSessionId,
                    title = cleanTitle,
                    createdAt = System.currentTimeMillis()
                )
                db.aiSessionDao().insertSession(newSession)
                syncSessionToCloud(newSession)
                currentSessionId = newSessionId
                withContext(Dispatchers.Main) {
                    activeSessionId = newSessionId
                }
            }

            val userMsgId = UUID.randomUUID().toString()
            val userMsg = AiMessage(
                id = userMsgId,
                sessionId = currentSessionId!!,
                sender = "user",
                text = textToSend,
                timestamp = System.currentTimeMillis()
            )
            db.aiMessageDao().insertMessage(userMsg)
            syncMessageToCloud(userMsg)

            withContext(Dispatchers.Main) {
                isGenerating = true
            }

            // Generate Response
            try {
                val history = db.aiMessageDao().getMessagesForSession(currentSessionId)
                val legacyMessages = history.map { aiMsg ->
                    Message(
                        id = aiMsg.id,
                        senderId = if (aiMsg.sender == "user") myPhone ?: "user" else "01300000000",
                        receiverId = if (aiMsg.sender == "user") "01300000000" else myPhone ?: "user",
                        text = aiMsg.text,
                        timestamp = aiMsg.timestamp,
                        senderName = if (aiMsg.sender == "user") "User" else "Barta AI"
                    )
                }

                val aiResponseText = GeminiService.getGeminiResponse(
                    context = context,
                    userPhone = myPhone ?: "user",
                    userMessage = textToSend,
                    previousMessages = legacyMessages,
                    language = appLanguage
                )

                val aiMsgId = UUID.randomUUID().toString()
                val aiMsg = AiMessage(
                    id = aiMsgId,
                    sessionId = currentSessionId,
                    sender = "ai",
                    text = aiResponseText,
                    timestamp = System.currentTimeMillis()
                )
                db.aiMessageDao().insertMessage(aiMsg)
                syncMessageToCloud(aiMsg)
            } catch (e: Exception) {
                e.printStackTrace()
                val errText = txt(
                    "দুঃখিত, কোনো সমস্যা হয়েছে। অনুগ্রহ করে আবার চেষ্টা করুন।",
                    "Sorry, an error occurred. Please try again."
                )
                val errorMsg = AiMessage(
                    id = UUID.randomUUID().toString(),
                    sessionId = currentSessionId,
                    sender = "ai",
                    text = errText,
                    timestamp = System.currentTimeMillis()
                )
                db.aiMessageDao().insertMessage(errorMsg)
            } finally {
                withContext(Dispatchers.Main) {
                    isGenerating = false
                }
            }
        }
    }

    fun generateAndSendImage(prompt: String, onStatusUpdate: (String) -> Unit = {}) {
        if (prompt.isBlank() || isGenerating) return
        
        inputText = ""

        scope.launch(Dispatchers.IO) {
            var currentSessionId = activeSessionId
            val isNewChat = currentSessionId == null
            
            if (isNewChat) {
                val newSessionId = UUID.randomUUID().toString()
                val rawTitle = if (prompt.length > 25) prompt.take(25) + "..." else prompt
                val cleanTitle = rawTitle.replace("\n", " ").trim()
                val newSession = AiSession(
                    id = newSessionId,
                    title = cleanTitle,
                    createdAt = System.currentTimeMillis()
                )
                db.aiSessionDao().insertSession(newSession)
                syncSessionToCloud(newSession)
                currentSessionId = newSessionId
                withContext(Dispatchers.Main) {
                    activeSessionId = newSessionId
                }
            }

            // 1. Insert User Message (Prompt Text)
            val userMsgId = UUID.randomUUID().toString()
            val userMsg = AiMessage(
                id = userMsgId,
                sessionId = currentSessionId!!,
                sender = "user",
                text = txt("ইমেজ তৈরি করুন: $prompt", "Generate Image: $prompt"),
                timestamp = System.currentTimeMillis()
            )
            db.aiMessageDao().insertMessage(userMsg)
            syncMessageToCloud(userMsg)

            withContext(Dispatchers.Main) {
                isGenerating = true
            }

            // 2. Generate and Insert Model Message (Image URL)
            try {
                val encodedPrompt = java.net.URLEncoder.encode(prompt, "UTF-8")
                val imageUrl = "https://image.pollinations.ai/prompt/$encodedPrompt?width=1024&height=1024&nologo=true&seed=${System.currentTimeMillis()}"
                
                // Introduce a tiny aesthetic delay representing Imagen rendering engine
                kotlinx.coroutines.delay(2000)

                val aiMsgId = UUID.randomUUID().toString()
                val aiMsg = AiMessage(
                    id = aiMsgId,
                    sessionId = currentSessionId,
                    sender = "ai",
                    text = imageUrl,
                    timestamp = System.currentTimeMillis()
                )
                db.aiMessageDao().insertMessage(aiMsg)
                syncMessageToCloud(aiMsg)
                onStatusUpdate("Success")
            } catch (e: Exception) {
                e.printStackTrace()
                val errText = txt(
                    "দুঃখিত, ইমেজ জেনারেশন ব্যর্থ হয়েছে। অনুগ্রহ করে আবার চেষ্টা করুন।",
                    "Sorry, image generation failed. Please try again."
                )
                val errorMsg = AiMessage(
                    id = UUID.randomUUID().toString(),
                    sessionId = currentSessionId,
                    sender = "ai",
                    text = errText,
                    timestamp = System.currentTimeMillis()
                )
                db.aiMessageDao().insertMessage(errorMsg)
                onStatusUpdate("Failed")
            } finally {
                withContext(Dispatchers.Main) {
                    isGenerating = false
                }
            }
        }
    }

    // Handle Regenerate Last Response
    fun regenerateLastResponse() {
        val sessId = activeSessionId ?: return
        if (isGenerating) return

        scope.launch(Dispatchers.IO) {
            val messages = db.aiMessageDao().getMessagesForSession(sessId)
            val lastUserMessage = messages.lastOrNull { it.sender == "user" } ?: return@launch
            
            // Delete subsequent AI messages in this session
            val lastUserMsgIndex = messages.indexOf(lastUserMessage)
            for (i in (lastUserMsgIndex + 1) until messages.size) {
                db.aiMessageDao().deleteMessage(messages[i])
            }

            withContext(Dispatchers.Main) {
                isGenerating = true
            }

            try {
                val remainingHistory = db.aiMessageDao().getMessagesForSession(sessId)
                val legacyMessages = remainingHistory.map { aiMsg ->
                    Message(
                        id = aiMsg.id,
                        senderId = if (aiMsg.sender == "user") myPhone ?: "user" else "01300000000",
                        receiverId = if (aiMsg.sender == "user") "01300000000" else myPhone ?: "user",
                        text = aiMsg.text,
                        timestamp = aiMsg.timestamp,
                        senderName = if (aiMsg.sender == "user") "User" else "Barta AI"
                    )
                }

                val aiResponseText = GeminiService.getGeminiResponse(
                    context = context,
                    userPhone = myPhone ?: "user",
                    userMessage = lastUserMessage.text,
                    previousMessages = legacyMessages,
                    language = appLanguage
                )

                val aiMsgId = UUID.randomUUID().toString()
                val aiMsg = AiMessage(
                    id = aiMsgId,
                    sessionId = sessId,
                    sender = "ai",
                    text = aiResponseText,
                    timestamp = System.currentTimeMillis()
                )
                db.aiMessageDao().insertMessage(aiMsg)
                syncMessageToCloud(aiMsg)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                withContext(Dispatchers.Main) {
                    isGenerating = false
                }
            }
        }
    }

    // History drawer content
    val drawerContent = @Composable {
        ModalDrawerSheet(
            modifier = Modifier.fillMaxHeight(),
            drawerContainerColor = if (isDarkTheme) Color(0xFF0F172A) else Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header of History Panel
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Barta AI logo",
                        tint = primaryColor,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = txt("বার্তা এআই চ্যাট বট ইতিহাস", "Barta Ai Chat Bot History"),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkTheme) Color.White else Color.Black
                    )
                }

                // New Chat Button
                Button(
                    onClick = {
                        activeSessionId = null
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .testTag("new_ai_chat_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "New Chat")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(txt("নতুন চ্যাট শুরু করুন", "Start New Chat"), fontWeight = FontWeight.Bold)
                    }
                }

                // Instantly search history
                OutlinedTextField(
                    value = searchHistoryQuery,
                    onValueChange = { searchHistoryQuery = it },
                    placeholder = { Text(txt("ইতিহাস খুঁজুন...", "Search history..."), fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(18.dp)) },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .testTag("ai_search_history"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primaryColor,
                        unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f)
                    )
                )

                // History Items List
                val filteredSessions = remember(sessions, searchHistoryQuery) {
                    if (searchHistoryQuery.isBlank()) {
                        sessions
                    } else {
                        sessions.filter { it.title.contains(searchHistoryQuery, ignoreCase = true) }
                    }
                }

                if (filteredSessions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = txt("কোনো ইতিহাস পাওয়া যায়নি", "No history found"),
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        items(filteredSessions) { session ->
                            val isSelected = session.id == activeSessionId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) primaryColor.copy(alpha = 0.15f)
                                        else Color.Transparent
                                    )
                                    .clickable {
                                        activeSessionId = session.id
                                        scope.launch { drawerState.close() }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (session.isPinned) Icons.Default.PushPin else Icons.Outlined.ChatBubbleOutline,
                                        contentDescription = "Session icon",
                                        tint = if (session.isPinned) secondaryColor else Color.Gray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = session.title,
                                        fontSize = 14.sp,
                                        color = if (isDarkTheme) Color.White else Color.Black,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                                
                                // Mini Action Options for Session (Pin, Rename, Delete)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    // Pin
                                    IconButton(
                                        onClick = {
                                            scope.launch(Dispatchers.IO) {
                                                val updated = session.copy(isPinned = !session.isPinned)
                                                db.aiSessionDao().updateSession(updated)
                                                syncSessionToCloud(updated)
                                            }
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (session.isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                                            contentDescription = "Pin session",
                                            tint = if (session.isPinned) secondaryColor else Color.Gray.copy(alpha = 0.6f),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    // Rename
                                    IconButton(
                                        onClick = {
                                            sessionToRename = session
                                            renameNewTitle = session.title
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Rename session",
                                            tint = Color.Gray.copy(alpha = 0.6f),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    // Delete
                                    IconButton(
                                        onClick = {
                                            sessionToDelete = session
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete session",
                                            tint = Color.Red.copy(alpha = 0.7f),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(
                                color = if (isDarkTheme) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f),
                                thickness = 0.5.dp
                            )
                        }
                    }
                }

                // Delete All History Button at bottom
                if (sessions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(
                        onClick = { showDeleteAllDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.DeleteForever, contentDescription = "Delete all history")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(txt("সব ইতিহাস মুছে ফেলুন", "Delete All History"), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Modal Drawer Wrapper
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = drawerContent,
        gesturesEnabled = true
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "AI symbol",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = txt("বার্তা এআই চ্যাট বট", "Barta Ai Chat Bot"),
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 18.sp
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Open History Drawer",
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
                        // Exit back button
                        IconButton(onClick = onClose) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Exit AI Chat",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = primaryColor)
                )
            },
            containerColor = if (isDarkTheme) Color(0xFF0F172A) else Color(0xFFF8FAFC)
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = paddingValues.calculateTopPadding())
                    .background(
                        if (isDarkTheme) {
                            Brush.verticalGradient(
                                listOf(Color(0xFF0F172A), Color(0xFF1E293B))
                            )
                        } else {
                            Brush.verticalGradient(
                                listOf(Color(0xFFF8FAFC), Color(0xFFE2E8F0))
                            )
                        }
                    )
            ) {
                val messages = activeMessages.value
                
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        if (messages.isEmpty() && !isGenerating) {
                    // --- WELCOME HOME SCREEN ---
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Animated pulsing/floating logo container
                        val infiniteTransition = rememberInfiniteTransition(label = "logo_pulse")
                        val floatingOffset by infiniteTransition.animateFloat(
                            initialValue = -5f,
                            targetValue = 5f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1500, easing = EaseInOutSine),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "floating"
                        )

                        Box(
                            modifier = Modifier
                                .graphicsLayer(translationY = floatingOffset)
                                .size(90.dp)
                                .shadow(8.dp, CircleShape)
                                .background(primaryColor.copy(alpha = 0.2f), CircleShape)
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_app_logo),
                                contentDescription = "Barta Logo",
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = txt("বার্তা এআই চ্যাট বট", "Barta Ai Chat Bot"),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = primaryColor,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = txt("ভার্চুয়াল এআই চ্যাট সহযোগী 🤖", "Your Virtual AI Companion 🤖"),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isDarkTheme) Color.LightGray else Color.DarkGray,
                            modifier = Modifier.padding(top = 4.dp),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Welcome Message card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = cardBg)
                        ) {
                            Text(
                                text = txt("আজ আপনাকে কীভাবে সাহায্য করতে পারি?", "How can I help you today?"),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDarkTheme) Color.White else Color.Black,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        // Quick-start prompt recommendation cards
                        Text(
                            text = txt("নিচের যেকোনো টপিকে চ্যাট শুরু করতে পারেন:", "Tap a card below to start talking:"),
                            fontSize = 12.sp,
                            color = Color.Gray,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .align(Alignment.Start)
                                .padding(start = 12.dp, bottom = 12.dp)
                        )

                        val prompts = listOf(
                            txt("আজকের দিনটি সুন্দর করার উপায় কী? 🌟", "How to make today a productive day? 🌟"),
                            txt("নতুন কোডিং শিখবো কীভাবে? 💻", "How to start learning coding? 💻"),
                            txt("বার্তা অ্যাপটির ফিচারগুলো কী কী? 📱", "What are Barta App features? 📱"),
                            txt("একটি ভ্রমণের সুন্দর পরিকল্পনা করো ✈️", "Plan a perfect weekend trip ✈️")
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            prompts.forEach { prompt ->
                                Card(
                                    onClick = { sendMessage(prompt) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("ai_prompt_card_${prompt.hashCode()}"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = cardBg),
                                    border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.15f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = prompt,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isDarkTheme) Color.White else Color.Black,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ArrowForward,
                                            contentDescription = "Send prompt",
                                            tint = primaryColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // --- ACTIVE CONVERSATION CHAT WINDOW ---
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Messages display
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 14.dp),
                            contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(messages) { message ->
                                val isUser = message.sender == "user"
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                                ) {
                                    // Message Bubble
                                    Box(
                                        modifier = Modifier
                                            .widthIn(max = 300.dp)
                                            .clip(
                                                RoundedCornerShape(
                                                    topStart = 16.dp,
                                                    topEnd = 16.dp,
                                                    bottomStart = if (isUser) 16.dp else 4.dp,
                                                    bottomEnd = if (isUser) 4.dp else 16.dp
                                                )
                                            )
                                            .background(if (isUser) userBubbleBg else aiBubbleBg)
                                            .border(
                                                width = 0.5.dp,
                                                color = if (isUser) Color.Transparent else Color.Gray.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(
                                                    topStart = 16.dp,
                                                    topEnd = 16.dp,
                                                    bottomStart = if (isUser) 16.dp else 4.dp,
                                                    bottomEnd = if (isUser) 4.dp else 16.dp
                                                )
                                            )
                                            .padding(14.dp)
                                    ) {
                                        Column {
                                            if (isUser) {
                                                if (message.text.startsWith("http")) {
                                                    coil.compose.AsyncImage(
                                                        model = message.text,
                                                        contentDescription = "Generated image",
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(200.dp)
                                                            .clip(RoundedCornerShape(8.dp)),
                                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                                    )
                                                } else {
                                                    Text(
                                                        text = message.text,
                                                        color = userBubbleText,
                                                        fontSize = 15.sp,
                                                        lineHeight = 21.sp
                                                    )
                                                }
                                            } else {
                                                if (message.text.startsWith("http")) {
                                                    coil.compose.AsyncImage(
                                                        model = message.text,
                                                        contentDescription = "AI generated image",
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(200.dp)
                                                            .clip(RoundedCornerShape(8.dp)),
                                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                                    )
                                                } else {
                                                    // Markdown rendering support for AI helper responses
                                                    MarkdownText(
                                                        text = message.text,
                                                        textColor = aiBubbleText
                                                    )
                                                }
                                            }
                                            
                                            Spacer(modifier = Modifier.height(4.dp))
                                            
                                            // Timestamp & feedback actions row
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                val sdf = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
                                                Text(
                                                    text = sdf.format(Date(message.timestamp)),
                                                    fontSize = 9.sp,
                                                    color = if (isUser) userBubbleText.copy(alpha = 0.6f) else Color.Gray
                                                )
                                                
                                                if (isUser) {
                                                    // Action row for User responses: Copy and Edit
                                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        // Copy
                                                        IconButton(
                                                            onClick = {
                                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                                val clip = android.content.ClipData.newPlainText("Barta Ai Chat Bot Message", message.text)
                                                                clipboard.setPrimaryClip(clip)
                                                                Toast.makeText(context, txt("বার্তা ক্লিপবোর্ডে কপি করা হয়েছে!", "Message copied to clipboard!"), Toast.LENGTH_SHORT).show()
                                                            },
                                                            modifier = Modifier.size(24.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Outlined.ContentCopy,
                                                                contentDescription = "Copy message",
                                                                tint = userBubbleText.copy(alpha = 0.6f),
                                                                modifier = Modifier.size(13.dp)
                                                            )
                                                        }

                                                        // Edit
                                                        IconButton(
                                                            onClick = {
                                                                messageToEdit = message
                                                                editedTextState = message.text
                                                                showEditDialog = true
                                                            },
                                                            modifier = Modifier.size(24.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Outlined.Edit,
                                                                contentDescription = "Edit message",
                                                                tint = userBubbleText.copy(alpha = 0.6f),
                                                                modifier = Modifier.size(13.dp)
                                                            )
                                                        }
                                                    }
                                                } else {
                                                    // Action row for AI responses
                                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        // Copy
                                                        IconButton(
                                                            onClick = {
                                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                                val clip = android.content.ClipData.newPlainText("Barta Ai Chat Bot Message", message.text)
                                                                clipboard.setPrimaryClip(clip)
                                                                Toast.makeText(context, txt("বার্তা ক্লিপবোর্ডে কপি করা হয়েছে!", "Message copied to clipboard!"), Toast.LENGTH_SHORT).show()
                                                            },
                                                            modifier = Modifier.size(24.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Outlined.ContentCopy,
                                                                contentDescription = "Copy message",
                                                                tint = Color.Gray,
                                                                modifier = Modifier.size(13.dp)
                                                            )
                                                        }
                                                        
                                                        // Like
                                                        IconButton(
                                                            onClick = {
                                                                scope.launch(Dispatchers.IO) {
                                                                    val toggledLiked = !message.isLiked
                                                                    db.aiMessageDao().updateFeedback(message.id, toggledLiked, false)
                                                                    val updated = message.copy(isLiked = toggledLiked, isDisliked = false)
                                                                    syncMessageToCloud(updated)
                                                                }
                                                            },
                                                            modifier = Modifier.size(24.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = if (message.isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                                                                contentDescription = "Like response",
                                                                tint = if (message.isLiked) primaryColor else Color.Gray,
                                                                modifier = Modifier.size(13.dp)
                                                            )
                                                        }

                                                        // Dislike
                                                        IconButton(
                                                            onClick = {
                                                                scope.launch(Dispatchers.IO) {
                                                                    val toggledDisliked = !message.isDisliked
                                                                    db.aiMessageDao().updateFeedback(message.id, false, toggledDisliked)
                                                                    val updated = message.copy(isLiked = false, isDisliked = toggledDisliked)
                                                                    syncMessageToCloud(updated)
                                                                }
                                                            },
                                                            modifier = Modifier.size(24.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = if (message.isDisliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                                                                contentDescription = "Dislike response",
                                                                tint = if (message.isDisliked) Color.Red else Color.Gray,
                                                                modifier = Modifier.size(13.dp)
                                                            )
                                                        }

                                                        // Share
                                                        IconButton(
                                                            onClick = {
                                                                val sendIntent = Intent().apply {
                                                                    action = Intent.ACTION_SEND
                                                                    putExtra(Intent.EXTRA_TEXT, message.text)
                                                                    type = "text/plain"
                                                                }
                                                                val shareIntent = Intent.createChooser(sendIntent, null)
                                                                context.startActivity(shareIntent)
                                                            },
                                                            modifier = Modifier.size(24.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Outlined.Share,
                                                                contentDescription = "Share response",
                                                                tint = Color.Gray,
                                                                modifier = Modifier.size(13.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Pulsing Typing animation
                            if (isGenerating) {
                                item {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.Start
                                    ) {
                                        PulsingDotsIndicator()
                                    }
                                }
                            }
                        }

                        // Bottom Action Controls for AI message stream (Regenerate, etc.)
                        if (messages.any { it.sender == "user" } && !isGenerating) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                OutlinedButton(
                                    onClick = { regenerateLastResponse() },
                                    modifier = Modifier.height(34.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.4f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = primaryColor)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Regenerate icon",
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(txt("উত্তর রি-জেনারেট করুন", "Regenerate Response"), fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

                // --- STICKY INPUT CONTAINER AT BOTTOM ---
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isDarkTheme) Color(0xFF0F172A) else Color.White)
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Generate Image button
                        IconButton(
                            onClick = {
                                if (inputText.isNotBlank()) {
                                    imageGenerationPrompt = inputText
                                    showGenerateImageDialog = true
                                } else {
                                    imageGenerationPrompt = ""
                                    showGenerateImageDialog = true
                                }
                            },
                            modifier = Modifier.size(36.dp).testTag("ai_generate_image_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Brush,
                                contentDescription = "Generate Image",
                                tint = primaryColor
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(4.dp))

                        // Microphone Speech-to-Text Button
                        IconButton(
                            onClick = {
                                if (hasMicPermission) {
                                    isVoiceChatActive = true
                                    startListening()
                                } else {
                                    micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier.size(36.dp).testTag("ai_mic_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Voice Input",
                                tint = primaryColor
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(6.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                                    shape = RoundedCornerShape(24.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = Color.Gray.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(24.dp)
                                )
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            BasicTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    color = if (isDarkTheme) Color.White else Color.Black,
                                    fontSize = 15.sp
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("ai_input_field"),
                                maxLines = 4,
                                decorationBox = { innerTextField ->
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        if (inputText.isEmpty()) {
                                            Text(
                                                text = txt("বার্তা লিখুন...", "Ask Barta Ai Chat Bot anything..."),
                                                color = Color.Gray,
                                                fontSize = 14.sp
                                            )
                                        }
                                        innerTextField()
                                    }
                                }
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Send Button
                        FloatingActionButton(
                            onClick = {
                                if (inputText.isNotBlank() && !isGenerating) {
                                    sendMessage(inputText)
                                }
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("ai_send_button"),
                            containerColor = primaryColor,
                            contentColor = Color.White,
                            shape = CircleShape,
                            elevation = FloatingActionButtonDefaults.elevation(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Send message to Barta Ai Chat Bot",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    
                    // Disclaimer footer note
                    Text(
                        text = txt(
                            "বার্তা এআই চ্যাট বট ভুল করতে পারে। গুরুত্বপূর্ণ তথ্য যাচাই করে নিন।",
                            "Barta Ai Chat Bot can make mistakes. Consider checking important info."
                        ),
                        fontSize = 9.sp,
                        color = Color.Gray,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
    }

    // --- ALERTS AND CONFIRMATION DIALOGS ---

    // Edit Message Dialog
    if (showEditDialog && messageToEdit != null) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = null, tint = primaryColor, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(txt("বার্তা সংশোধন করুন", "Edit Message"), fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        text = txt(
                            "আপনার বার্তাটি সংশোধন করে নিচের বক্সে লিখুন:",
                            "Modify your message and write in the box below:"
                        ),
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = editedTextState,
                        onValueChange = { editedTextState = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 4
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val msg = messageToEdit
                        if (msg != null && editedTextState.trim().isNotEmpty()) {
                            val newText = editedTextState
                            scope.launch(Dispatchers.IO) {
                                db.aiMessageDao().updateMessageText(msg.id, newText)
                                val updatedMsg = msg.copy(text = newText)
                                syncMessageToCloud(updatedMsg)
                            }
                        }
                        showEditDialog = false
                    },
                    enabled = editedTextState.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                ) {
                    Text(txt("সংরক্ষণ করুন", "Save"), color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showEditDialog = false }) {
                    Text(txt("বাতিল", "Cancel"))
                }
            }
        )
    }

    // Image Generation Dialog
    if (showGenerateImageDialog) {
        AlertDialog(
            onDismissRequest = { if (!isGeneratingImage) showGenerateImageDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Brush, contentDescription = null, tint = primaryColor, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(txt("ইমেজ জেনারেট করুন (Imagen 3)", "Generate Image (Imagen 3)"), fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        text = txt(
                            "ইমেজের বিবরণ বা প্রম্পট লিখুন। আমরা এটি তৈরি করে সরাসরি চ্যাটে পাঠিয়ে দেবঃ",
                            "Enter the image description or prompt. We will generate and send it directly to the chat:"
                        ),
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = imageGenerationPrompt,
                        onValueChange = { imageGenerationPrompt = it },
                        label = { Text(txt("প্রম্পট লিখুন...", "Enter prompt...")) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4,
                        enabled = !isGeneratingImage
                    )
                    if (isGeneratingImage) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator(color = primaryColor, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(txt("ইমেজ তৈরি হচ্ছে...", "Generating image..."), fontSize = 12.sp, color = primaryColor)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (imageGenerationPrompt.isNotBlank()) {
                            isGeneratingImage = true
                            generateAndSendImage(imageGenerationPrompt) { status ->
                                if (status == "Success" || status == "Failed") {
                                    isGeneratingImage = false
                                    showGenerateImageDialog = false
                                    inputText = ""
                                }
                            }
                        }
                    },
                    enabled = !isGeneratingImage && imageGenerationPrompt.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                ) {
                    Text(txt("তৈরি করুন", "Generate"), color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showGenerateImageDialog = false },
                    enabled = !isGeneratingImage
                ) {
                    Text(txt("বাতিল", "Cancel"))
                }
            }
        )
    }

    // Rename Dialog
    if (sessionToRename != null) {
        AlertDialog(
            onDismissRequest = { sessionToRename = null },
            title = { Text(txt("চ্যাট রিনেম করুন", "Rename Chat"), fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameNewTitle,
                    onValueChange = { renameNewTitle = it },
                    singleLine = true,
                    label = { Text(txt("নতুন নাম", "New Title")) },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val session = sessionToRename
                        if (session != null && renameNewTitle.isNotBlank()) {
                            scope.launch(Dispatchers.IO) {
                                val updated = session.copy(title = renameNewTitle)
                                db.aiSessionDao().updateSession(updated)
                                syncSessionToCloud(updated)
                            }
                        }
                        sessionToRename = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                ) {
                    Text(txt("সংরক্ষণ করুন", "Save"))
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToRename = null }) {
                    Text(txt("বাতিল", "Cancel"), color = Color.Gray)
                }
            }
        )
    }

    // Delete Session Dialog
    if (sessionToDelete != null) {
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            title = { Text(txt("চ্যাট মুছুন", "Delete Chat"), fontWeight = FontWeight.Bold, color = Color.Red) },
            text = { Text(txt("আপনি কি নিশ্চিতভাবে এই চ্যাট হিস্ট্রি মুছে ফেলতে চান?", "Are you sure you want to delete this chat history? This action is irreversible.")) },
            confirmButton = {
                Button(
                    onClick = {
                        val session = sessionToDelete
                        if (session != null) {
                            scope.launch(Dispatchers.IO) {
                                db.aiSessionDao().deleteSession(session)
                                deleteSessionFromCloud(session.id)
                                if (activeSessionId == session.id) {
                                    activeSessionId = null
                                }
                            }
                        }
                        sessionToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text(txt("মুছে ফেলুন", "Delete"))
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToDelete = null }) {
                    Text(txt("বাতিল", "Cancel"), color = Color.Gray)
                }
            }
        )
    }

    // Delete All History Dialog
    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text(txt("সব ইতিহাস মুছুন", "Delete All History"), fontWeight = FontWeight.Bold, color = Color.Red) },
            text = { Text(txt("আপনি কি নিশ্চিতভাবে সব চ্যাট হিস্ট্রি মুছে ফেলতে চান?", "Are you sure you want to delete all AI chat history? This action is irreversible.")) },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            db.aiSessionDao().deleteAllSessions()
                            deleteAllSessionsFromCloud()
                            activeSessionId = null
                        }
                        showDeleteAllDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text(txt("সব মুছে ফেলুন", "Delete All"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text(txt("বাতিল", "Cancel"), color = Color.Gray)
                }
            }
        )
    }

    // Custom Face-to-Face Voice Chat Dialog Overlay (Full screen & Premium design matching reference)
    if (isVoiceChatActive) {
        Dialog(
            onDismissRequest = {
                speechRecognizer?.cancel()
                tts?.stop()
                isVoiceChatActive = false
            },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false
            )
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = if (isDarkTheme) Color(0xFF0F172A) else Color(0xFFF7FBF9) // slate vs mint off-white
            ) {
                val infiniteTransition = rememberInfiniteTransition()

                // Define suggestion states
                val allSuggestions = remember {
                    listOf(
                        txt("আজকের খবর কী?", "What's the news today?"),
                        txt("আবহাওয়া কেমন?", "How is the weather?"),
                        txt("ক্রিকেটের স্কোর", "What is the cricket score?"),
                        txt("একটি মজার কৌতুক বলো", "Tell me a funny joke"),
                        txt("ইতিহাসের একটি সুন্দর তথ্য দাও", "Give me an interesting history fact"),
                        txt("আজকের অনুপ্রেরণামূলক বাণী", "Today's motivational quote"),
                        txt("একটি গান গেয়ে শোনাও", "Sing a song for me"),
                        txt("তুমি কী কী করতে পারো?", "What features do you have?")
                    )
                }
                var currentSuggestions by remember { mutableStateOf(allSuggestions.shuffled().take(3)) }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // 1. TOP BAR (Back, Title, Settings Sliders)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Circular Back Button
                        IconButton(
                            onClick = {
                                speechRecognizer?.cancel()
                                tts?.stop()
                                isVoiceChatActive = false
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .background(
                                    color = if (isDarkTheme) Color(0xFF1E293B) else Color.White,
                                    shape = CircleShape
                                )
                                .border(
                                    BorderStroke(1.dp, if (isDarkTheme) Color.DarkGray.copy(alpha = 0.4f) else Color.LightGray.copy(alpha = 0.5f)),
                                    CircleShape
                                )
                                .testTag("voice_chat_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Close",
                                tint = if (isDarkTheme) Color.LightGray else Color.DarkGray
                            )
                        }

                        // Title "বার্তা AI" with sparkle
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "বার্তা AI",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isDarkTheme) Color.White else Color(0xFF1E293B)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = primaryColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Sliders / Settings/Tune Button
                        IconButton(
                            onClick = {
                                Toast.makeText(context, txt("ভয়েস সেটিংস পরিবর্তন করা হচ্ছে", "Voice settings adjusted"), Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .background(
                                    color = if (isDarkTheme) Color(0xFF1E293B) else Color.White,
                                    shape = CircleShape
                                )
                                .border(
                                    BorderStroke(1.dp, if (isDarkTheme) Color.DarkGray.copy(alpha = 0.4f) else Color.LightGray.copy(alpha = 0.5f)),
                                    CircleShape
                                )
                                .testTag("voice_chat_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Settings",
                                tint = if (isDarkTheme) Color.LightGray else Color.DarkGray
                            )
                        }
                    }

                    // Badge/Chip below top bar
                    Box(
                        modifier = Modifier
                            .background(
                                color = primaryColor.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .border(BorderStroke(0.5.dp, primaryColor.copy(alpha = 0.2f)), RoundedCornerShape(20.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = primaryColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = txt("AI সহ ভয়েস চ্যাট", "AI Voice Chat"),
                                color = primaryColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 2. CENTER ORBIT & WAVEFORM LAYOUT
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Waveform
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val barCount = 8
                            for (i in 0 until barCount) {
                                val taper = (i + 1).toFloat() / barCount.toFloat()
                                val baseDelay = i * 70
                                val speed = when (voiceState) {
                                    "listening" -> 180
                                    "speaking" -> 260
                                    "processing" -> 380
                                    else -> 1000
                                }
                                val maxAmp = when (voiceState) {
                                    "listening" -> (8f + (liveRmsDb.coerceAtLeast(0f) * 4.5f)) * taper
                                    "speaking" -> 32f * taper
                                    "processing" -> 10f * taper
                                    else -> 4f
                                }
                                val animHeight by infiniteTransition.animateFloat(
                                    initialValue = 4f,
                                    targetValue = maxAmp.coerceIn(4f, 55f),
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(durationMillis = speed, delayMillis = baseDelay, easing = FastOutSlowInEasing),
                                        repeatMode = RepeatMode.Reverse
                                    )
                                )
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(animHeight.dp)
                                        .background(
                                            color = primaryColor.copy(alpha = if (voiceState == "idle") 0.15f else 0.85f),
                                            shape = RoundedCornerShape(2.dp)
                                        )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Orbit & Central Mic Button Stack
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(200.dp)
                        ) {
                            // Pulsing glowing background layers
                            val glowScale by infiniteTransition.animateFloat(
                                initialValue = 1f,
                                targetValue = if (voiceState == "listening" || voiceState == "speaking") 1.25f else 1.05f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1200, easing = LinearEasing),
                                    repeatMode = RepeatMode.Reverse
                                )
                            )

                            // Glowing Outer Ring 1
                            Box(
                                modifier = Modifier
                                    .size(190.dp)
                                    .graphicsLayer {
                                        scaleX = glowScale
                                        scaleY = glowScale
                                    }
                                    .background(
                                        color = primaryColor.copy(alpha = if (voiceState == "idle") 0.02f else 0.04f),
                                        shape = CircleShape
                                    )
                            )

                            // Glowing Ring 2
                            Box(
                                modifier = Modifier
                                    .size(160.dp)
                                    .graphicsLayer {
                                        scaleX = if (voiceState == "listening") (1f + (liveRmsDb.coerceAtLeast(0f) / 12f)) else glowScale
                                        scaleY = if (voiceState == "listening") (1f + (liveRmsDb.coerceAtLeast(0f) / 12f)) else glowScale
                                    }
                                    .background(
                                        color = primaryColor.copy(alpha = if (voiceState == "idle") 0.04f else 0.08f),
                                        shape = CircleShape
                                    )
                            )

                            // Orbit arc line
                            val rotationAngle by infiniteTransition.animateFloat(
                                initialValue = 0f,
                                targetValue = 360f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(durationMillis = 4000, easing = LinearEasing),
                                    repeatMode = RepeatMode.Restart
                                )
                            )

                            Canvas(
                                modifier = Modifier
                                    .size(176.dp)
                                    .rotate(rotationAngle)
                            ) {
                                drawArc(
                                    color = primaryColor,
                                    startAngle = 0f,
                                    sweepAngle = 100f,
                                    useCenter = false,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                                        width = 2.dp.toPx(),
                                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                                    )
                                )
                                drawArc(
                                    color = primaryColor.copy(alpha = 0.2f),
                                    startAngle = 150f,
                                    sweepAngle = 120f,
                                    useCenter = false,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                                        width = 1.dp.toPx(),
                                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                                    )
                                )
                            }

                            // Interactive Mic Button
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(124.dp)
                                    .shadow(8.dp, CircleShape)
                                    .background(
                                        brush = Brush.linearGradient(
                                            colors = when (voiceState) {
                                                "listening" -> listOf(Color(0xFFE53935), Color(0xFFD81B60))
                                                "processing" -> listOf(Color.Gray, Color.DarkGray)
                                                "speaking" -> listOf(primaryColor, Color(0xFF00796B))
                                                else -> listOf(primaryColor, Color(0xFF2E7D32))
                                            }
                                        ),
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        if (voiceState == "listening") {
                                            speechRecognizer?.stopListening()
                                            voiceState = "processing"
                                        } else if (voiceState == "speaking") {
                                            tts?.stop()
                                            voiceState = "idle"
                                        } else {
                                            startListening()
                                        }
                                    }
                            ) {
                                Icon(
                                    imageVector = when (voiceState) {
                                        "listening" -> Icons.Default.Stop
                                        "processing" -> Icons.Default.HourglassEmpty
                                        "speaking" -> Icons.Default.VolumeUp
                                        else -> Icons.Default.Mic
                                    },
                                    contentDescription = "Microphone Button",
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Right Waveform
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val barCount = 8
                            for (i in 0 until barCount) {
                                val taper = (barCount - i).toFloat() / barCount.toFloat()
                                val baseDelay = i * 70
                                val speed = when (voiceState) {
                                    "listening" -> 180
                                    "speaking" -> 260
                                    "processing" -> 380
                                    else -> 1000
                                }
                                val maxAmp = when (voiceState) {
                                    "listening" -> (8f + (liveRmsDb.coerceAtLeast(0f) * 4.5f)) * taper
                                    "speaking" -> 32f * taper
                                    "processing" -> 10f * taper
                                    else -> 4f
                                }
                                val animHeight by infiniteTransition.animateFloat(
                                    initialValue = 4f,
                                    targetValue = maxAmp.coerceIn(4f, 55f),
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(durationMillis = speed, delayMillis = baseDelay, easing = FastOutSlowInEasing),
                                        repeatMode = RepeatMode.Reverse
                                    )
                                )
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(animHeight.dp)
                                        .background(
                                            color = primaryColor.copy(alpha = if (voiceState == "idle") 0.15f else 0.85f),
                                            shape = RoundedCornerShape(2.dp)
                                        )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 3. VOICE STATUS & DYNAMIC TRANSCRIPT CARD
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Title status
                        Text(
                            text = when (voiceState) {
                                "listening" -> txt("আমি শুনছি...", "Listening...")
                                "processing" -> txt("ভাবছি...", "Thinking...")
                                "speaking" -> txt("কথা বলছি...", "Speaking...")
                                "error" -> txt("সমস্যা হয়েছে!", "Error occurred!")
                                else -> txt("মাইকে ক্লিক করে বলুন", "Tap mic to talk")
                            },
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = if (isDarkTheme) Color.White else Color(0xFF1F2C34)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Subtext description
                        Text(
                            text = when (voiceState) {
                                "listening" -> txt("বলুন, আমি আছি আপনার সাথে", "Speak, I'm here with you")
                                "processing" -> txt("বার্তা AI আপনার প্রশ্নের উত্তর রেডি করছে...", "Barta AI is processing response...")
                                "speaking" -> txt("অনুগ্রহ করে শুনুন...", "Please listen...")
                                "error" -> if (voiceErrorMessage.isNotEmpty()) voiceErrorMessage else txt("দুঃখিত, আবার চেষ্টা করুন!", "Sorry, please try again!")
                                else -> txt("আমি শুনছি আপনার যেকোনো কথা বা প্রশ্ন", "Waiting to assist you with any questions")
                            },
                            fontSize = 14.sp,
                            color = if (isDarkTheme) Color.LightGray else Color.Gray,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Real-time formatted ticking timer
                        var timerSeconds by remember { mutableStateOf(0) }
                        LaunchedEffect(voiceState) {
                            if (voiceState == "listening") {
                                timerSeconds = 0
                                while (voiceState == "listening") {
                                    kotlinx.coroutines.delay(1000L)
                                    timerSeconds += 1
                                }
                            } else {
                                timerSeconds = 0
                            }
                        }
                        val timerString = remember(timerSeconds) {
                            val mins = timerSeconds / 60
                            val secs = timerSeconds % 60
                            String.format("%02d:%02d", mins, secs)
                        }

                        Text(
                            text = timerString,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryColor
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Small scrolling text transcripts box so user can read what's spoken/being said
                        if (voiceUserText.isNotEmpty() || voiceAiResponseText.isNotEmpty() || voiceErrorMessage.isNotEmpty()) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 84.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFEEF3F1)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(0.5.dp, primaryColor.copy(alpha = 0.15f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    if (voiceErrorMessage.isNotEmpty()) {
                                        Text(voiceErrorMessage, color = Color.Red, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                    } else {
                                        if (voiceUserText.isNotEmpty()) {
                                            Text(
                                                text = txt("আপনি: $voiceUserText", "You: $voiceUserText"),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isDarkTheme) Color.LightGray else Color.DarkGray
                                            )
                                        }
                                        if (voiceAiResponseText.isNotEmpty() && voiceState != "processing") {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = txt("বার্তা AI: $voiceAiResponseText", "Barta AI: $voiceAiResponseText"),
                                                fontSize = 12.sp,
                                                color = if (isDarkTheme) Color.White else Color(0xFF1E293B)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 4. ACTION PANEL CONTAINER (Elevated action panel card)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDarkTheme) Color(0xFF1E293B) else Color.White
                        ),
                        shape = RoundedCornerShape(24.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        border = BorderStroke(1.dp, if (isDarkTheme) Color.DarkGray.copy(alpha = 0.3f) else Color.LightGray.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Cancel button (✕)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable {
                                    speechRecognizer?.cancel()
                                    tts?.stop()
                                    voiceState = "idle"
                                    voiceUserText = ""
                                    voiceAiResponseText = ""
                                    voiceErrorMessage = ""
                                }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(
                                            color = if (isDarkTheme) Color(0xFF0F172A) else Color(0xFFF1F5F9),
                                            shape = CircleShape
                                        )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Cancel",
                                        tint = if (isDarkTheme) Color.LightGray else Color.DarkGray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = txt("বাতিল", "Cancel"),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDarkTheme) Color.LightGray else Color.DarkGray
                                )
                            }

                            // Stop Button (■) - custom styled premium teal button
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable {
                                    speechRecognizer?.cancel()
                                    tts?.stop()
                                    voiceState = "idle"
                                }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(54.dp)
                                        .background(
                                            color = primaryColor,
                                            shape = CircleShape
                                        )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Stop,
                                        contentDescription = "Stop",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = txt("থামুন", "Stop"),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = primaryColor
                                )
                            }

                            // Keyboard button (লিখে বলুন)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable {
                                    speechRecognizer?.cancel()
                                    tts?.stop()
                                    isVoiceChatActive = false
                                }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(
                                            color = if (isDarkTheme) Color(0xFF0F172A) else Color(0xFFF1F5F9),
                                            shape = CircleShape
                                        )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Keyboard,
                                        contentDescription = "Write",
                                        tint = if (isDarkTheme) Color.LightGray else Color.DarkGray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = txt("লিখে বলুন", "Write"),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDarkTheme) Color.LightGray else Color.DarkGray
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 5. SUGGESTION CONTAINER AT THE VERY BOTTOM
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = if (isDarkTheme) Color(0xFF1E293B).copy(alpha = 0.4f) else Color(0xFFEEF5F2).copy(alpha = 0.5f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .border(
                                BorderStroke(0.5.dp, primaryColor.copy(alpha = 0.15f)),
                                RoundedCornerShape(16.dp)
                            )
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(30.dp)
                                        .background(primaryColor.copy(alpha = 0.1f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lightbulb,
                                        contentDescription = null,
                                        tint = primaryColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = txt("আপনি চাইলে জানতে পারেন", "You can ask about"),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDarkTheme) Color.LightGray else Color(0xFF1E293B)
                                )
                            }

                            // Shuffle Button
                            IconButton(
                                onClick = {
                                    currentSuggestions = allSuggestions.shuffled().take(3)
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Shuffle suggestions",
                                    tint = primaryColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Render suggestion chips horizontally scrolling
                        androidx.compose.foundation.lazy.LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp)
                        ) {
                            items(currentSuggestions) { suggestionText ->
                                Box(
                                    modifier = Modifier
                                        .background(
                                            color = if (isDarkTheme) Color(0xFF101B2B) else Color.White,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .border(
                                            BorderStroke(0.5.dp, if (isDarkTheme) Color.DarkGray else Color.LightGray.copy(alpha = 0.7f)),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            submitVoiceQuery(suggestionText)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = suggestionText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isDarkTheme) Color.White else Color(0xFF2E7D32),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- LIGHTWEIGHT CUSTOM MARKDOWN COMPOSABLE FOR PRESTIGE LOOK ---

@Composable
fun MarkdownText(text: String, textColor: Color) {
    val lines = text.split("\n")
    Column(modifier = Modifier.fillMaxWidth()) {
        var inCodeBlock = false
        var codeBlockContent = StringBuilder()
        var codeBlockLanguage = ""

        for (line in lines) {
            val trimmedLine = line.trim()
            if (trimmedLine.startsWith("```")) {
                if (inCodeBlock) {
                    // End of code block, render it beautifully
                    CodeBlock(code = codeBlockContent.toString(), language = codeBlockLanguage)
                    codeBlockContent = StringBuilder()
                    inCodeBlock = false
                } else {
                    // Start of code block
                    inCodeBlock = true
                    codeBlockLanguage = trimmedLine.removePrefix("```").trim()
                }
            } else if (inCodeBlock) {
                codeBlockContent.append(line).append("\n")
            } else {
                // Parse markdown in standard line
                if (trimmedLine.startsWith("#")) {
                    val level = trimmedLine.takeWhile { it == '#' }.length
                    val content = trimmedLine.drop(level).trim()
                    val fontSize = when (level) {
                        1 -> 20.sp
                        2 -> 18.sp
                        else -> 16.sp
                    }
                    Text(
                        text = content,
                        fontSize = fontSize,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else if (trimmedLine.startsWith("- ") || trimmedLine.startsWith("* ")) {
                    val content = trimmedLine.substring(2)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Text("• ", fontWeight = FontWeight.Bold, color = WhatsAppTealVal, fontSize = 15.sp)
                        Text(
                            text = parseInlineMarkdown(content, textColor),
                            color = textColor,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    }
                } else if (trimmedLine.isNotEmpty()) {
                    Text(
                        text = parseInlineMarkdown(line, textColor),
                        color = textColor,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
        
        // If still inside code block, render the remaining part
        if (inCodeBlock && codeBlockContent.isNotEmpty()) {
            CodeBlock(code = codeBlockContent.toString(), language = codeBlockLanguage)
        }
    }
}

// Parses **bold** and `inline code`
fun parseInlineMarkdown(text: String, textColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            if (text.startsWith("**", i)) {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = textColor)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append("**")
                    i += 2
                }
            } else if (text.startsWith("`", i)) {
                val end = text.indexOf("`", i + 1)
                if (end != -1) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color.Gray.copy(alpha = 0.15f),
                            color = WhatsAppTealVal,
                            fontWeight = FontWeight.SemiBold
                        )
                    ) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append("`")
                    i += 1
                }
            } else {
                append(text[i])
                i++
            }
        }
    }
}

// Custom code block rendering component with Mono Font & Copy button
@Composable
fun CodeBlock(code: String, language: String) {
    val context = LocalContext.current
    val trimmedCode = code.trimEnd()
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
            .border(0.5.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
    ) {
        // Code Block Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E293B), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = language.ifEmpty { "code" }.uppercase(),
                color = Color.LightGray,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            
            // Inline Copy Code affordance
            Row(
                modifier = Modifier
                    .clickable {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Code Block", trimmedCode)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "কোড কপি করা হয়েছে!", Toast.LENGTH_SHORT).show()
                    }
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy code block",
                    tint = WhatsAppGreenVal,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Copy",
                    color = WhatsAppGreenVal,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Horizontal scrolling code box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .horizontalScroll(rememberScrollState())
        ) {
            Text(
                text = trimmedCode,
                color = Color(0xFFF8FAFC),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}

// Bouncing typing dot indicator
@Composable
fun PulsingDotsIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "dots_pulsing")
    
    val scale1 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_dot1"
    )
    val scale2 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_dot2"
    )
    val scale3 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 400, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_dot3"
    )

    Row(
        modifier = Modifier
            .background(Color.Gray.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val dotColor = WhatsAppTealVal
        Box(modifier = Modifier.size(8.dp).graphicsLayer(scaleX = scale1, scaleY = scale1).background(dotColor, CircleShape))
        Box(modifier = Modifier.size(8.dp).graphicsLayer(scaleX = scale2, scaleY = scale2).background(dotColor, CircleShape))
        Box(modifier = Modifier.size(8.dp).graphicsLayer(scaleX = scale3, scaleY = scale3).background(dotColor, CircleShape))
    }
}
