package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.core.animateDpAsState
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Contact
import com.example.data.LocalUser
import com.example.data.Message
import com.example.viewmodel.ChatViewModel
import com.example.ui.theme.*
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.activity.result.PickVisualMediaRequest
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun getTranslator(viewModel: ChatViewModel): (String, String) -> String {
    val lang by viewModel.appLanguage.collectAsStateWithLifecycle()
    return remember(lang) { { bn, en -> if (lang == "bn") bn else en } }
}

@Composable
fun MainAppScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val isConnected by rememberConnectivityState()
    val loggedInNumber by viewModel.myNumber.collectAsStateWithLifecycle()
    val activeChatContact by viewModel.activeContact.collectAsStateWithLifecycle()
    val showOnboarding by viewModel.showOnboarding.collectAsStateWithLifecycle()
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val txt = getTranslator(viewModel = viewModel)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (!isConnected) {
            OfflineBlockerScreen()
        } else if (showOnboarding) {
            GreetingOnboardingScreen(
                viewModel = viewModel,
                onFinished = {
                    viewModel.showOnboarding.value = false
                }
            )
        } else if (loggedInNumber == null) {
            AuthScreen(viewModel = viewModel)
        } else {
            Scaffold(
                bottomBar = {
                    if (activeChatContact == null) {
                        BottomBarNavigation(
                            viewModel = viewModel
                        )
                    }
                }
            ) { innerPadding ->
                val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (currentTab) {
                        "chats" -> ChatsTabScreen(
                            viewModel = viewModel,
                            onChatClick = { contact ->
                                viewModel.selectContact(contact)
                            }
                        )
                        "groups" -> GroupsTabScreen(
                            viewModel = viewModel,
                            onChatClick = { contact ->
                                viewModel.selectContact(contact)
                            }
                        )
                        "status" -> StatusTabScreen(
                            viewModel = viewModel
                        )
                        "contacts" -> ContactsTabScreen(
                            viewModel = viewModel,
                            onContactClick = { contact ->
                                viewModel.selectContact(contact)
                            }
                        )
                        "profile" -> ProfileTabScreen(
                            viewModel = viewModel
                        )
                        "settings" -> SettingsTabScreen(
                            viewModel = viewModel
                        )
                    }

                    // Slide overlay for the chat list
                    AnimatedVisibility(
                        visible = activeChatContact != null,
                        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
                    ) {
                        activeChatContact?.let { contact ->
                            ChatWindowScreen(
                                contact = contact,
                                viewModel = viewModel,
                                onClose = { viewModel.selectContact(null) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun rememberConnectivityState(): State<Boolean> {
    val context = LocalContext.current
    val connectivityManager = remember {
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }
    
    val currentConnected = remember {
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        mutableStateOf(capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true)
    }

    DisposableEffect(connectivityManager) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                currentConnected.value = true
            }

            override fun onLost(network: Network) {
                currentConnected.value = false
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                currentConnected.value = hasInternet
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        
        try {
            connectivityManager.registerNetworkCallback(request, callback)
        } catch (e: Exception) {
            currentConnected.value = true
        }

        onDispose {
            try {
                connectivityManager.unregisterNetworkCallback(callback)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
    return currentConnected
}

@Composable
fun OfflineBlockerScreen() {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF4F6F6))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .background(Color(0xFFE53935).copy(alpha = 0.08f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(Color(0xFFE53935).copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "No Internet Connection Icon",
                        tint = Color(0xFFE53935),
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "ইন্টারনেট সংযোগ নেই 📶❌",
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = Color(0xFF1F2937),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "বার্তা (Barta) চ্যাট উপভোগ করার জন্য আপনার সক্রিয় ইন্টারনেট সংযোগ প্রয়োজন। অনুগ্রহ করে আপনার মোবাইল ডেটা (Mobile Data) অথবা ওয়াই-ফাই (Wi-Fi) চালূ করে পুনরায় চেষ্টা করুন।",
                fontSize = 15.sp,
                color = Color(0xFF4B5563),
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(36.dp))

            Button(
                onClick = {
                    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                    val activeNetwork = connectivityManager.activeNetwork
                    val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
                    val isConnectedNow = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                    if (isConnectedNow) {
                        Toast.makeText(context, "ইন্টারনেট কানেকশন সংযুক্ত হয়েছে! 🎉", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "এখনও ইন্টারনেট সংযোগ পাওয়া যায়নি! অনুগ্রহ করে চেষ্টা করুন।", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = WhatsAppTealVal),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .height(49.dp)
                    .widthIn(min = 180.dp)
                    .testTag("connectivity_retry_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Retry",
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "পুনরায় চেষ্টা করুন",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
fun GreetingOnboardingScreen(
    viewModel: ChatViewModel,
    onFinished: () -> Unit
) {
    val txt = getTranslator(viewModel = viewModel)
    var currentPage by remember { mutableIntStateOf(0) }
    val totalPages = 3

    BackHandler {
        if (currentPage > 0) {
            currentPage--
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF4F6F6))
    ) {
        // Decorative background top glow
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(WhatsAppTealVal.copy(alpha = 0.12f), Color.Transparent)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .statusBarsPadding()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Top Brand
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_app_logo),
                    contentDescription = "App Logo",
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .border(1.dp, WhatsAppTealVal, CircleShape)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = txt("বার্তা (Barta)", "Barta (Chat)"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = WhatsAppTealVal
                )
            }

            // Slide content box with simple horizontal transition based on currentPage
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = currentPage,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> -width } + fadeOut()
                            )
                        } else {
                            (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> width } + fadeOut()
                            )
                        }.using(
                            SizeTransform(clip = false)
                        )
                    },
                    label = "slidePageTransition"
                ) { page ->
                    when (page) {
                        0 -> OnboardingWelcomePage(txt)
                        1 -> OnboardingFeaturesPage(txt)
                        2 -> OnboardingHighlightsPage(txt)
                    }
                }
            }

            // Bottom controls: Indicators and Action buttons
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Dot Indicators
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    for (i in 0 until totalPages) {
                        val isActive = i == currentPage
                        val widthAnim by animateDpAsState(
                            targetValue = if (isActive) 24.dp else 8.dp,
                            label = "dotWidth"
                        )
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(height = 8.dp, width = widthAnim)
                                .background(
                                    color = if (isActive) WhatsAppTealVal else Color.LightGray,
                                    shape = CircleShape
                                )
                        )
                    }
                }

                // Row for Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back button (Invisible or disabled on Page 0 to keep spacing)
                    if (currentPage > 0) {
                        TextButton(
                            onClick = { currentPage-- },
                            modifier = Modifier.testTag("onboarding_back_btn")
                        ) {
                            Text(
                                text = txt("পূর্ববর্তী", "Back"),
                                color = WhatsAppTealVal,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(80.dp))
                    }

                    // Next button or Get Started
                    Button(
                        onClick = {
                            if (currentPage < totalPages - 1) {
                                currentPage++
                            } else {
                                onFinished()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WhatsAppTealVal),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(50.dp)
                            .testTag("onboarding_next_btn")
                    ) {
                        val buttonText = if (currentPage == totalPages - 1) {
                            txt("চ্যাট শুরু করুন 🚀", "Start Chatting 🚀")
                        } else {
                            txt("পরবর্তী", "Next")
                        }
                        Text(
                            text = buttonText,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OnboardingWelcomePage(txt: (String, String) -> String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // Decorative Illustration Avatar
        Box(
            modifier = Modifier
                .size(150.dp)
                .background(WhatsAppTealVal.copy(alpha = 0.08f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .background(WhatsAppTealVal.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🇧🇩",
                    fontSize = 52.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = txt("বার্তা (Barta) চ্যাটে স্বাগতম! 🎉", "Welcome to Barta Chat! 🎉"),
            fontWeight = FontWeight.Bold,
            fontSize = 23.sp,
            color = Color(0xFF1F2937),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = txt("সহজ, নিরাপদ ও বিজ্ঞাপনহীন যোগাযোগের একমাত্র নির্ভরযোগ্য চ্যাট অ্যাপ্লিকেশন। কোনো বাড়তি জটিলতা বা ঝামেলা ছাড়াই আপনার বন্ধুদের সাথে সর্বদা সংযুক্ত থাকুন।", "The ultimate reliable, secure, and ad-free chat application for seamless and direct conversation. Keep connected with your friends instantly without any clutter or complexity."),
            fontSize = 15.sp,
            color = Color(0xFF4B5563),
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
    }
}

@Composable
fun OnboardingFeaturesPage(txt: (String, String) -> String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
    ) {
        Text(
            text = txt("আমাদের চমৎকার সুবিধাসমূহ ✨", "Our Awesome Features ✨"),
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = Color(0xFF1F2937),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OnboardingFeatureCard(
                icon = "⚡",
                title = txt("রিয়েল-টাইম চ্যাট ও গ্রুপ", "Real-Time Chat & Groups"),
                description = txt("অতি দ্রুত চ্যাট মেসেজ এবং ক্লাস বা প্রজেক্টের আলোচনার জন্য তাৎক্ষণিক গ্রুপ খোলার চমৎকার সুবিধা।", "Superfast private messaging and instant student/project discussion groups.")
            )

            OnboardingFeatureCard(
                icon = "📷",
                title = txt("ছবি ও ২৪ ঘণ্টার চমৎকার স্ট্যাটাস", "Photos & 24h Stories"),
                description = txt("মনের গোপন কথা লিখে স্টোরি ও ছবি বা পছন্দের রঙিন ব্যাকগ্রাউন্ড ডিজাইনে স্ট্যাটাস শেয়ার করুন।", "Express yourself freely via personal status messages, rich media uploads, or colorful canvas backdrops.")
            )

            OnboardingFeatureCard(
                icon = "🪄",
                title = txt("মেসেজ এডিট ও ডিলিট ক্ষমতা", "Message Edit & Global Undo"),
                description = txt("মেসেজ ভুল হলে তা সরাসরি সংশোধনের সুযোগ অথবা সবার জন্য সম্পূর্ণ মুছে ফেলার (Delete for Everyone) অফুরন্ত স্বাধীনতা।", "Edit typos inline or erase sent chats completely for all participants using Delete for Everyone.")
            )
        }
    }
}

@Composable
fun OnboardingHighlightsPage(txt: (String, String) -> String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
    ) {
        Text(
            text = txt("স্মার্ট প্রযুক্তি ও সম্পূর্ণ নিয়ন্ত্রণ 🛡️", "Smart Tech & Total Control 🛡️"),
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = Color(0xFF1F2937),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OnboardingFeatureCard(
                icon = "🤖",
                title = txt("স্মার্ট বার্তা সহকারী (AI Bot)", "Smart AI Assistant Bot"),
                description = txt("যেকোনো কঠিন প্রশ্নের উত্তর জানতে বা প্রজেক্ট সাজাতে সাহায্য বা সাধারণ চ্যাট করতে সর্বদা প্রস্তুত আপনার এআই বন্ধু!", "Your helper is always available to resolve assignments, write creative summaries, or guide chats.")
            )

            OnboardingFeatureCard(
                icon = "🟢",
                title = txt("অনলাইন স্ট্যাটাস ও টাইপ অনুভূতি", "Typing Details & Online Presence"),
                description = txt("বন্ধুরা কখন লাইনে আছে বা কি লিখছে তা সরাসরি দেখার চমৎকার অনুভূতি।", "Relish knowing exactly when your peers are active or composing key responses live.")
            )

            OnboardingFeatureCard(
                icon = "🔒",
                title = txt("১০০% বিজ্ঞাপনহীন ও সর্বোচ্চ নিরাপত্তা", "Secure, Private & 100% Ad-Free"),
                description = txt("কোনো বিরক্তিকর বিজ্ঞাপন ছাড়াই চ্যাটিংয়ের পূর্ণ স্বাচ্ছন্দ্যতা ও ব্যবহারকারীর ডেটার সুরক্ষিত নিরাপত্তা।", "Savor high-octane messaging with complete visual tranquility and state-of-the-art storage privacy.")
            )
        }
    }
}

@Composable
fun OnboardingFeatureCard(
    icon: String,
    title: String,
    description: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(WhatsAppTealVal.copy(alpha = 0.08f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = icon, fontSize = 18.sp)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFF111827)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = Color(0xFF6B7280),
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
fun AuthScreen(
    viewModel: ChatViewModel
) {
    val txt = getTranslator(viewModel = viewModel)
    var isSignUp by remember { mutableStateOf(false) }
    var phoneNumber by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var profilePicBase64 by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showPassword by remember { mutableStateOf(false) }
    var showPhotoSelector by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // Android permission request flow
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(context, txt("অনুমতি পাওয়া গেছে! এখন ছবি সিলেক্ট করুন।", "Permission granted! Now select an image."), Toast.LENGTH_SHORT).show()
            showPhotoSelector = true
        } else {
            Toast.makeText(context, txt("ছবি আপলোড করতে স্টোরেজ/ক্যামেরা পারমিশন প্রয়োজন!", "Camera/Storage permission is required to upload profile pictures!"), Toast.LENGTH_LONG).show()
            // Dynamic fallback list triggers anyways for great testing fidelity
            showPhotoSelector = true
        }
    }

    val signUpPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                val localPath = copyUriToLocalFile(context, uri)
                if (localPath != null) {
                    profilePicBase64 = localPath
                    showPhotoSelector = false
                    Toast.makeText(context, txt("গ্যালারি থেকে ছবি সফলভাবে যুক্ত হয়েছে!", "Image successfully imported from gallery!"), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, txt("ছবি লোড করতে সমস্যা হয়েছে!", "Failed to load image!"), Toast.LENGTH_SHORT).show()
                }
            }
        }
    )

    var tempCameraFileSignUp by remember { mutableStateOf<java.io.File?>(null) }
    var tempCameraUriSignUp by remember { mutableStateOf<android.net.Uri?>(null) }

    val cameraLauncherSignUp = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            if (success) {
                val file = tempCameraFileSignUp
                if (file != null && file.exists() && file.length() > 0) {
                    profilePicBase64 = file.absolutePath
                    showPhotoSelector = false
                    Toast.makeText(context, txt("ক্যামেরা থেকে ছবি সফলভাবে যুক্ত হয়েছে!", "Image successfully captured from camera!"), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, txt("ছবি ফাইল তৈরি করা যায়নি!", "Failed to create picture file!"), Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, txt("ছবি ধারণ বাতিল বা ব্যর্থ হয়েছে!", "Camera capture cancelled or failed!"), Toast.LENGTH_SHORT).show()
            }
        }
    )

    val cameraPermissionLauncherSignUp = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                try {
                    val file = java.io.File(context.cacheDir, "signup_profile_${System.currentTimeMillis()}.jpg")
                    val uri = androidx.core.content.FileProvider.getUriForFile(context, "com.example.fileprovider", file)
                    tempCameraFileSignUp = file
                    tempCameraUriSignUp = uri
                    cameraLauncherSignUp.launch(uri)
                } catch (e: Exception) {
                    Toast.makeText(context, txt("ক্যামেরা ছবি প্রস্তুত করতে ব্যর্থ হয়েছে!", "Failed to prepare camera file!"), Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, txt("ক্যামেরা ব্যবহারের অনুমতি প্রয়োজন!", "Camera permission is required!"), Toast.LENGTH_SHORT).show()
            }
        }
    )

    val launchCameraFlowSignUp: () -> Unit = {
        val hasCamPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (hasCamPermission) {
            try {
                val file = java.io.File(context.cacheDir, "signup_profile_${System.currentTimeMillis()}.jpg")
                val uri = androidx.core.content.FileProvider.getUriForFile(context, "com.example.fileprovider", file)
                tempCameraFileSignUp = file
                tempCameraUriSignUp = uri
                cameraLauncherSignUp.launch(uri)
            } catch (e: Exception) {
                Toast.makeText(context, txt("ক্যামেরা ছবি প্রস্তুত করতে ব্যর্থ হয়েছে!", "Failed to prepare camera file!"), Toast.LENGTH_SHORT).show()
            }
        } else {
            cameraPermissionLauncherSignUp.launch(android.Manifest.permission.CAMERA)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .statusBarsPadding()
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_app_logo),
            contentDescription = "App Logo",
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .border(2.5.dp, WhatsAppGreenVal, CircleShape)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = txt("বার্তা (Chat)", "Barta (Chat)"),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = WhatsAppTealVal
        )

        Text(
            text = txt("বাংলাদেশের প্রথম সহজ নিরাপদ চ্যাট প্ল্যাটফর্ম", "The first simple & secure chat platform in Bangladesh"),
            fontSize = 13.sp,
            color = Color.Gray,
            modifier = Modifier.padding(top = 2.dp, bottom = 24.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Sign Up / Login toggle tab
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp)
                        .background(Color(0xFFF0F2F5), shape = RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                color = if (!isSignUp) Color.White else Color.Transparent,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                isSignUp = false
                                errorMessage = null
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = txt("লগইন", "Login"),
                            fontWeight = FontWeight.Bold,
                            color = if (!isSignUp) WhatsAppTealVal else Color.Gray,
                            fontSize = 14.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                color = if (isSignUp) Color.White else Color.Transparent,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                isSignUp = true
                                errorMessage = null
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = txt("একাউন্ট তৈরি", "Sign Up"),
                            fontWeight = FontWeight.Bold,
                            color = if (isSignUp) WhatsAppTealVal else Color.Gray,
                            fontSize = 14.sp
                        )
                    }
                }

                Text(
                    text = if (isSignUp) txt("নতুন একাউন্ট তৈরি করুন", "Create New Account") else txt("লগইন করুন", "Log in to your Account"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Registration photo uploader
                if (isSignUp) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEEEEEE))
                            .clickable {
                                // Request dynamic permissions
                                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                            .border(1.5.dp, WhatsAppGreenVal, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (profilePicBase64.isNotEmpty()) {
                            if (profilePicBase64.startsWith("/") || profilePicBase64.startsWith("content:") || profilePicBase64.startsWith("file:")) {
                                AsyncImage(
                                    model = profilePicBase64,
                                    contentDescription = "Profile Picture",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Image(
                                    imageVector = getAvatarVector(profilePicBase64),
                                    contentDescription = "Profile Picture",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.AddAPhoto, contentDescription = "Upload Picture", tint = Color.Gray, modifier = Modifier.size(24.dp))
                                Text(txt("ছবি দিন", "Photo"), fontSize = 10.sp, color = Color.Gray)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = {
                            nameInput = it
                            errorMessage = null
                        },
                        label = { Text(txt("আপনার নাম (আবশ্যক)", "Your Name (Required)")) },
                        placeholder = { Text(txt("উদা: রাইসা আলম", "e.g. Raisa Alam")) },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = WhatsAppTealVal,
                            unfocusedLabelColor = Color.Gray
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("signup_name_input")
                            .padding(bottom = 12.dp)
                    )
                }

                // Phone Input field
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = {
                        phoneNumber = it.filter { char -> char.isDigit() }
                        errorMessage = null
                    },
                    label = { Text(txt("মোবাইল নাম্বার", "Mobile Number")) },
                    placeholder = { Text("01XXXXXXXXX") },
                    prefix = { Text("+88 ") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = Color.Gray) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Next
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = WhatsAppTealVal,
                        unfocusedLabelColor = Color.Gray,
                        focusedPrefixColor = Color.White,
                        unfocusedPrefixColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login_phone_input")
                        .padding(bottom = 12.dp)
                )

                // Password Input field (Strict 6 to 8 chars check)
                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = {
                        passwordInput = it
                        errorMessage = null
                    },
                    label = { Text(txt("পাসওয়ার্ড (৬-৮ ক্যারেক্টার)", "Password (6-8 characters)")) },
                    placeholder = { Text("******") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Color.Gray) },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (showPassword) "Hide" else "Show"
                            )
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = WhatsAppTealVal,
                        unfocusedLabelColor = Color.Gray
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login_password_input")
                        .padding(bottom = 12.dp)
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = Color.Red,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .align(Alignment.Start)
                            .padding(bottom = 12.dp, start = 4.dp)
                    )
                }

                Button(
                    onClick = {
                        if (isSignUp) {
                            viewModel.register(
                                phone = phoneNumber,
                                name = nameInput,
                                password = passwordInput,
                                profilePic = profilePicBase64
                            ) { error ->
                                if (error != null) {
                                    errorMessage = error
                                } else {
                                    Toast.makeText(context, txt("সফলভাবে একাউন্ট তৈরি এবং লগইন হয়েছে!", "Account successfully created and logged in!"), Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            viewModel.loginWithPassword(phoneNumber, passwordInput) { error ->
                                if (error != null) {
                                    errorMessage = error
                                } else {
                                    Toast.makeText(context, txt("লগইন সফল হয়েছে!", "Login successful!"), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreenVal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("submit_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (isSignUp) txt("একাউন্ট তৈরি করুন", "Create Account") else txt("লগইন করুন", "Log In"),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }

    // Interactive Photo Selector Panel
    if (showPhotoSelector) {
        AlertDialog(
            onDismissRequest = { showPhotoSelector = false },
            title = { Text(txt("প্রোফাইল ছবি নির্বাচন করুন", "Select Profile Picture"), fontWeight = FontWeight.Bold, color = WhatsAppTealVal) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(txt("নিচের যেকোনো একটি সুন্দর কার্টুন ছবি স্পর্শ করে সিলেক্ট করুন:", "Select any of the cartoon avatars below by tapping on it:"), fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    val dummyPics = listOf("pic1", "pic2", "pic3", "pic4", "pic5", "pic6")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        dummyPics.take(3).forEach { p ->
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(CircleShape)
                                    .border(
                                        width = if (profilePicBase64 == p) 3.dp else 1.dp,
                                        color = if (profilePicBase64 == p) WhatsAppGreenVal else Color.LightGray,
                                        shape = CircleShape
                                    )
                                    .clickable { profilePicBase64 = p }
                            ) {
                                Image(imageVector = getAvatarVector(p), contentDescription = null, modifier = Modifier.fillMaxSize())
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        dummyPics.drop(3).forEach { p ->
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(CircleShape)
                                    .border(
                                        width = if (profilePicBase64 == p) 3.dp else 1.dp,
                                        color = if (profilePicBase64 == p) WhatsAppGreenVal else Color.LightGray,
                                        shape = CircleShape
                                    )
                                    .clickable { profilePicBase64 = p }
                            ) {
                                Image(imageVector = getAvatarVector(p), contentDescription = null, modifier = Modifier.fillMaxSize())
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                signUpPhotoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = WhatsAppTealVal),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(txt("গ্যালারি", "Gallery"), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                launchCameraFlowSignUp()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreenVal),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(txt("ক্যামেরা", "Camera"), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showPhotoSelector = false },
                    colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreenVal)
                ) {
                    Text(txt("অনুমোদন দিন", "OK"), color = Color.White)
                }
            }
        )
    }
}

@Composable
fun BottomBarNavigation(
    viewModel: ChatViewModel
) {
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val txt = getTranslator(viewModel = viewModel)

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        NavigationBarItem(
            selected = currentTab == "chats",
            onClick = { viewModel.selectTab("chats") },
            icon = { Icon(Icons.AutoMirrored.Filled.Message, contentDescription = "Chats") },
            label = { Text(txt("চ্যাট", "Chats"), fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = WhatsAppTealVal,
                selectedTextColor = WhatsAppTealVal,
                indicatorColor = WhatsAppTealVal.copy(alpha = 0.12f)
            ),
            modifier = Modifier.testTag("tab_chats")
        )
        NavigationBarItem(
            selected = currentTab == "groups",
            onClick = { viewModel.selectTab("groups") },
            icon = { Icon(Icons.Default.Groups, contentDescription = "Groups") },
            label = { Text(txt("গ্রুপ", "Groups"), fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = WhatsAppTealVal,
                selectedTextColor = WhatsAppTealVal,
                indicatorColor = WhatsAppTealVal.copy(alpha = 0.12f)
            ),
            modifier = Modifier.testTag("tab_groups")
        )
        NavigationBarItem(
            selected = currentTab == "status",
            onClick = { viewModel.selectTab("status") },
            icon = { Icon(Icons.Default.CircleNotifications, contentDescription = "Status") },
            label = { Text(txt("স্ট্যাটাস", "Status"), fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = WhatsAppTealVal,
                selectedTextColor = WhatsAppTealVal,
                indicatorColor = WhatsAppTealVal.copy(alpha = 0.12f)
            ),
            modifier = Modifier.testTag("tab_status")
        )
        NavigationBarItem(
            selected = currentTab == "contacts",
            onClick = { viewModel.selectTab("contacts") },
            icon = { Icon(Icons.Default.People, contentDescription = "Contacts") },
            label = { Text(txt("পরিচিত", "Contacts"), fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = WhatsAppTealVal,
                selectedTextColor = WhatsAppTealVal,
                indicatorColor = WhatsAppTealVal.copy(alpha = 0.12f)
            ),
            modifier = Modifier.testTag("tab_contacts")
        )
        NavigationBarItem(
            selected = currentTab == "profile",
            onClick = { viewModel.selectTab("profile") },
            icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
            label = { Text(txt("প্রোফাইল", "Profile"), fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = WhatsAppTealVal,
                selectedTextColor = WhatsAppTealVal,
                indicatorColor = WhatsAppTealVal.copy(alpha = 0.12f)
            ),
            modifier = Modifier.testTag("tab_profile")
        )
        NavigationBarItem(
            selected = currentTab == "settings",
            onClick = { viewModel.selectTab("settings") },
            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
            label = { Text(txt("সেটিংস", "Settings"), fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = WhatsAppTealVal,
                selectedTextColor = WhatsAppTealVal,
                indicatorColor = WhatsAppTealVal.copy(alpha = 0.12f)
            ),
            modifier = Modifier.testTag("tab_settings")
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsTabScreen(
    viewModel: ChatViewModel,
    onChatClick: (Contact) -> Unit
) {
    val contactsList by viewModel.contacts.collectAsStateWithLifecycle()
    val searchVal by viewModel.searchQuery.collectAsStateWithLifecycle()
    val myPhone by viewModel.myNumber.collectAsStateWithLifecycle()
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val txt = getTranslator(viewModel = viewModel)

    // Filter out group chats for individual conversations
    val individualChats = remember(contactsList) { contactsList.filter { !it.isGroup } }

    // Query on global registered users matching the name
    val searchedGlobalUsers by viewModel.searchedGlobalUsers.collectAsStateWithLifecycle()

    val isDark by viewModel.isDarkMode.collectAsStateWithLifecycle()
    var activeSubTab by remember { mutableStateOf("all") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) Color(0xFF090D16) else Color(0xFFF3FAF8))
    ) {
        // TOP custom header bar containing gradient background, logo, slogan, wavy indicator and theme icon
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = if (isDark) {
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF0E2A28), Color(0xFF0F172A))
                        )
                    } else {
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF05B59E), Color(0xFFA7F3D0))
                        )
                    },
                    shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
                )
                .padding(top = 16.dp, bottom = 26.dp, start = 16.dp, end = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left avatar logo using the premium custom app logo image
                Image(
                    painter = painterResource(id = R.drawable.img_app_logo),
                    contentDescription = "App Logo",
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, Color.White, CircleShape)
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Middle Title + Slogan
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = txt("বার্তা (Chat)", "Chat"),
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else Color(0xFF072C2B),
                        fontSize = 22.sp
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = txt("সংযোগ হোক সহজ, দূরত্ব হোক কম", "Connections simple, distances less"),
                        fontSize = 11.sp,
                        color = if (isDark) Color.White.copy(alpha = 0.7f) else Color(0xFF0B6356),
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    // Cute dynamic canvas wave drawing matching the picture
                    Canvas(modifier = Modifier.width(60.dp).height(4.dp)) {
                        val path = Path().apply {
                            moveTo(0f, 2f)
                            quadraticTo(10f, -1f, 20f, 2f)
                            quadraticTo(30f, 5f, 40f, 2f)
                            quadraticTo(50f, -1f, 60f, 2f)
                        }
                        drawPath(
                            path = path,
                            color = Color(0xFF0EA5E9),
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }

                // Right circular whites (with soft shadow)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Dark mode button
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(if (isDark) Color(0xFF1E293B) else Color.White, CircleShape)
                            .clickable { viewModel.toggleTheme() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isDark) Icons.Default.WbSunny else Icons.Default.DarkMode,
                            contentDescription = "Theme Mode",
                            tint = if (isDark) Color(0xFFFFD700) else Color(0xFF1E293B),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Quick help info toast
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(if (isDark) Color(0xFF1E293B) else Color.White, CircleShape)
                            .clickable {
                                Toast.makeText(viewModel.getApplication(), txt("বার্তা অ্যাপে স্বাগতম!", "Welcome to Barta App!"), Toast.LENGTH_SHORT).show()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menu Options",
                            tint = if (isDark) Color.White else Color(0xFF1E293B),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Parent Container for White/Dark Container of the chats
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .offset(y = (-14).dp), // Beautiful overlap over the gradient bottom curve
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isDark) Color(0xFF090D16) else Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                // Search box row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchVal,
                        onValueChange = { viewModel.searchQuery.value = it },
                        placeholder = {
                            Text(
                                text = txt("মানুষ, গ্রুপ বা বার্তা খুঁজুন...", "Search people, groups or messages..."),
                                color = if (isDark) Color.LightGray.copy(alpha = 0.6f) else Color.Gray,
                                fontSize = 13.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = if (isDark) Color.LightGray else Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchVal.isNotEmpty()) {
                                IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = if (isDark) Color.White else Color.Black, modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = if (isDark) Color.White else Color.Black,
                            unfocusedTextColor = if (isDark) Color.White else Color.Black,
                            focusedContainerColor = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                            unfocusedContainerColor = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedPlaceholderColor = Color.LightGray,
                            unfocusedPlaceholderColor = Color.LightGray
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(22.dp))
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Beside search is the filter tune button with active teal tint
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9), CircleShape)
                            .clickable {
                                Toast.makeText(viewModel.getApplication(), txt("ফিল্টার অপশন শীঘ্রই আসছে!", "Filters coming soon!"), Toast.LENGTH_SHORT).show()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Tune",
                            tint = Color(0xFF26B29E),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Row of subcategory pills: "সব", "আনপড়া", "প্রিয়", "গ্রুপ"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // "সব" Pill
                    val isAllActive = activeSubTab == "all"
                    PillItem(
                        text = txt("সব", "All"),
                        icon = Icons.Default.Apps,
                        isSelected = isAllActive,
                        isDark = isDark,
                        onClick = { activeSubTab = "all" }
                    )

                    // "আনপড়া" Pill
                    val isUnreadActive = activeSubTab == "unread"
                    PillItem(
                        text = txt("আনপড়া", "Unread"),
                        icon = Icons.Default.ChatBubbleOutline,
                        isSelected = isUnreadActive,
                        isDark = isDark,
                        showDotBadge = individualChats.any { it.unreadCount > 0 },
                        onClick = { activeSubTab = "unread" }
                    )

                    // "প্রিয়" Pill
                    val isFavActive = activeSubTab == "fav"
                    PillItem(
                        text = txt("প্রিয়", "Favorites"),
                        icon = Icons.Default.Star,
                        isSelected = isFavActive,
                        isDark = isDark,
                        onClick = { activeSubTab = "fav" }
                    )

                    // "গ্রুপ" Pill (automatically redirects to GroupsTab)
                    PillItem(
                        text = txt("গ্রুপ", "Groups"),
                        icon = Icons.Default.Groups,
                        isSelected = false,
                        isDark = isDark,
                        onClick = {
                            viewModel.selectTab("groups")
                        }
                    )
                }

                // Apply filtering list of chats based on activeSubTab
                val filteredChats = remember(individualChats, activeSubTab) {
                    when (activeSubTab) {
                        "unread" -> individualChats.filter { it.unreadCount > 0 }
                        "fav" -> individualChats.filter { it.name.contains("সুমি") || it.name.contains("রফিক") || it.phone.endsWith("2") || it.phone.endsWith("4") }
                        else -> individualChats
                    }
                }

                // Chats List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    // Global search result - start chatting directly
                    if (searchVal.isNotEmpty() && searchedGlobalUsers.isNotEmpty()) {
                        item {
                            Text(
                                text = txt("সার্চ করা অন্যান্য ব্যবহারকারীরা (অনলাইন):", "Other Searched Users (Online):"),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF26B29E),
                                modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
                            )
                        }

                        items(searchedGlobalUsers) { user ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.addNewContact(user.name, user.phone, false)
                                        viewModel.selectContact(
                                            Contact(
                                                phone = user.phone,
                                                name = user.name,
                                                profilePicUri = user.profilePicBase64,
                                                lastSeen = "online"
                                            )
                                        )
                                    }
                                    .padding(vertical = 10.dp, horizontal = 10.dp)
                                    .background(if (isDark) Color(0xFF1E293B).copy(alpha = 0.5f) else Color(0xFFF8FAFC), RoundedCornerShape(12.dp)),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AvatarView(name = user.name, base64 = user.profilePicBase64, size = 42)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(user.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (isDark) Color.White else Color.Black)
                                    Text(user.phone, fontSize = 11.sp, color = if (isDark) Color.LightGray else Color.Gray)
                                }
                                Spacer(modifier = Modifier.weight(1f))
                                Text(txt("মেসেজ দিন ➡️", "Message ➡️"), color = Color(0xFF26B29E), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }

                    // Existing Chats list
                    if (filteredChats.isNotEmpty()) {
                        item {
                            Text(
                                text = txt("আমার চ্যাটসমূহ (Active Chats):", "My Chats (Active Chats):"),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color.LightGray.copy(alpha = 0.6f) else Color.Gray,
                                modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)
                            )
                        }

                        items(filteredChats) { contact ->
                            ChatRowItem(
                                contact = contact,
                                lang = appLanguage,
                                onClick = { onChatClick(contact) }
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    } else if (searchedGlobalUsers.isEmpty() && searchVal.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 80.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.ChatBubbleOutline,
                                        contentDescription = "Empty",
                                        modifier = Modifier.size(52.dp),
                                        tint = if (isDark) Color.DarkGray else Color.LightGray
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = txt("কোনো চ্যাট পাওয়া যায়নি", "No chats found"),
                                        color = Color.Gray,
                                        fontSize = 13.sp
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsTabScreen(
    viewModel: ChatViewModel,
    onChatClick: (Contact) -> Unit
) {
    val contactsList by viewModel.contacts.collectAsStateWithLifecycle()
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val searchVal by viewModel.searchQuery.collectAsStateWithLifecycle()
    val myPhone by viewModel.myNumber.collectAsStateWithLifecycle()
    val txt = getTranslator(viewModel = viewModel)

    val groupChats = remember(contactsList) { contactsList.filter { it.isGroup } }

    var showCreateGroupDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateGroupDialog = true },
                containerColor = WhatsAppTealVal,
                contentColor = Color.White,
                icon = { Icon(Icons.Default.Groups, contentDescription = "Create Group") },
                text = { Text(txt("নতুন গ্রুপ", "New Group")) },
                modifier = Modifier.testTag("create_group_fab")
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Group header with dynamic subtitle and custom logo
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.img_app_logo),
                            contentDescription = "App Logo",
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .border(1.dp, Color.White, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = txt("গ্রুপ চ্যাট", "Group Chats"),
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "Logged in as ${myPhone ?: ""}",
                                fontSize = 11.sp,
                                color = Color(0xFFC8E6C9)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = WhatsAppTealVal)
            )

            // Custom search box matching WhatsApp layout
            OutlinedTextField(
                value = searchVal,
                onValueChange = { viewModel.searchQuery.value = it },
                placeholder = { Text(txt("গ্রুপ নাম দিয়ে সার্চ করুন...", "Search groups by name..."), color = Color.Gray) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Gray) },
                trailingIcon = {
                    if (searchVal.isNotEmpty()) {
                        IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = WhatsAppDarkIncomingVal,
                    unfocusedContainerColor = WhatsAppDarkIncomingVal,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedPlaceholderColor = Color.LightGray,
                    unfocusedPlaceholderColor = Color.LightGray
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .clip(RoundedCornerShape(24.dp))
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                if (groupChats.isNotEmpty()) {
                    item {
                        Text(
                            text = txt("আমার গ্রুপসমূহ (My Groups):", "My Groups (My Groups):"),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            modifier = Modifier.padding(start = 16.dp, bottom = 4.dp, top = 8.dp)
                        )
                    }

                    items(groupChats) { contact ->
                        ChatRowItem(
                            contact = contact,
                            lang = appLanguage,
                            onClick = { onChatClick(contact) }
                        )
                        HorizontalDivider(
                            color = Color(0xFFF5F5F5),
                            thickness = 1.dp,
                            modifier = Modifier.padding(start = 76.dp)
                        )
                    }
                } else {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Groups,
                                    contentDescription = "Empty",
                                    modifier = Modifier.size(56.dp),
                                    tint = Color.LightGray
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = txt("কোনো গ্রুপ চ্যাট পাওয়া যায়নি", "No group chats found"),
                                    color = Color.Gray,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateGroupDialog) {
        CreateGroupDialog(
            contacts = contactsList.filter { !it.isGroup },
            viewModel = viewModel,
            onDismiss = { showCreateGroupDialog = false },
            onConfirm = { groupName, selected ->
                viewModel.createGroup(groupName, selected)
                showCreateGroupDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusTabScreen(
    viewModel: ChatViewModel
) {
    val isDark by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val activeStatuses by viewModel.activeStatuses.collectAsStateWithLifecycle()
    val myPhoneState by viewModel.myNumber.collectAsStateWithLifecycle()
    val myPhone = myPhoneState ?: ""
    val myName = viewModel.userDisplayName.value
    val myProfilePic = viewModel.userProfilePicBase64.value
    val context = LocalContext.current
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val txt = getTranslator(viewModel = viewModel)

    // Group statuses by phone
    val groupedStatuses = activeStatuses.groupBy { it.phone }
    val myStatuses = groupedStatuses[myPhone] ?: emptyList()
    val otherUsersStatuses = groupedStatuses.filterKeys { it != myPhone }

    // Dialog & post states
    var showAddTextStatusDialog by remember { mutableStateOf(false) }
    var showAddPhotoStatusDialog by remember { mutableStateOf<String?>(null) } // holds localPath on pick
    var showPostChooserDialog by remember { mutableStateOf(false) }

    var textStatusInputText by remember { mutableStateOf("") }
    var photoStatusInputText by remember { mutableStateOf("") }
    var selectedBgColorIndex by remember { mutableStateOf(0) }

    val bgColors = listOf(
        0xFF00897B, // Teal
        0xFF5E35B1, // Purple
        0xFFE53935, // Soft Red
        0xFF1E88E5, // Blue
        0xFF43A047, // Green
        0xFFE65100  // Orange
    )

    // Photo/Image Status picker launcher
    val photoStatusPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                val localPath = copyUriToLocalFile(context, uri)
                if (localPath != null) {
                    showAddPhotoStatusDialog = localPath
                } else {
                    Toast.makeText(context, if (appLanguage == "bn") "ছবি লোড করতে সমস্যা হয়েছে!" else "Failed to load photo!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    )

    // Active story list being played
    var activeStoryList by remember { mutableStateOf<List<com.example.data.ChatStatus>?>(null) }
    var activeStoryIndex by remember { mutableStateOf(0) }

    fun formatTimeAgo(time: Long): String {
        val diff = System.currentTimeMillis() - time
        return when {
            diff < 0 -> if (appLanguage == "bn") "এইমাত্র" else "just now"
            diff < 60000 -> if (appLanguage == "bn") "এইমাত্র" else "just now"
            diff < 3600000 -> {
                val mins = diff / 60000
                if (appLanguage == "bn") "$mins মিনিট আগে" else "$mins mins ago"
            }
            diff < 86400000 -> {
                val hrs = diff / 3600000
                if (appLanguage == "bn") "$hrs ঘণ্টা আগে" else "$hrs hours ago"
            }
            else -> if (appLanguage == "bn") "১ দিন আগে" else "1 day ago"
        }
    }

    Scaffold(
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Small FAB for text status
                FloatingActionButton(
                    onClick = { showAddTextStatusDialog = true },
                    containerColor = if (isDark) Color(0xFF1E293B) else Color(0xFFF0F2F5),
                    contentColor = if (isDark) Color.White else WhatsAppTealVal,
                    modifier = Modifier.size(44.dp).testTag("add_text_status_fab")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Add Text Status", modifier = Modifier.size(20.dp))
                }

                // Main FAB for image status
                FloatingActionButton(
                    onClick = {
                        photoStatusPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    containerColor = WhatsAppGreenVal,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("add_photo_status_fab")
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Add Photo Status")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(if (isDark) Color(0xFF090D16) else Color(0xFFF0F2F5))
        ) {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.img_app_logo),
                            contentDescription = "App Logo",
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .border(1.dp, Color.White, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = txt("স্ট্যাটাস", "Status"),
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "Logged in as $myPhone",
                                fontSize = 11.sp,
                                color = Color(0xFFC8E6C9)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = WhatsAppTealVal)
            )

            // Section 1: My Status Row
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF1E293B) else Color.White),
                shape = RectangleShape
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (myStatuses.isNotEmpty()) {
                                activeStoryList = myStatuses.sortedBy { it.timestamp }
                                activeStoryIndex = 0
                            } else {
                                showPostChooserDialog = true
                            }
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        if (myStatuses.isNotEmpty()) {
                            // Ring around my status avatar indicating active updates
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .border(2.5.dp, WhatsAppGreenVal, CircleShape)
                                    .padding(3.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                AvatarView(name = myName, base64 = myProfilePic, size = 44)
                            }
                        } else {
                            AvatarView(name = myName, base64 = myProfilePic, size = 52)
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(WhatsAppGreenVal, shape = CircleShape)
                                    .border(1.5.dp, if (isDark) Color(0xFF1E293B) else Color.White, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(txt("আমার স্ট্যাটাস", "My Status"), fontWeight = FontWeight.Bold, color = if (isDark) Color.White else Color.Black, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (myStatuses.isEmpty()) txt("স্ট্যাটাস আপডেট করতে টাচ করুন", "Tap to add status update") else {
                                val latest = myStatuses.maxBy { it.timestamp }
                                if (!latest.mediaUrl.isNullOrEmpty()) {
                                    val actType = if (appLanguage == "bn") "📷 ফটো আপডেট • " else "📷 Photo update • "
                                    actType + formatTimeAgo(latest.timestamp)
                                } else {
                                    "${latest.text} • ${formatTimeAgo(latest.timestamp)}"
                                }
                            },
                            color = if (isDark) Color.LightGray else Color.Gray,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Section 2: Recent updates
            Text(
                text = txt("সাম্প্রতিক আপডেটসমূহ (Recent Updates):", "Recent Updates (Recent Updates):"),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color.LightGray else Color.Gray,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp, top = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth().weight(1f),
                colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF1E293B) else Color.White),
                shape = RectangleShape
            ) {
                if (otherUsersStatuses.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = txt("কোনো সাম্প্রতিক স্ট্যাটাস আপডেট নেই", "No recent status updates"),
                            color = if (isDark) Color.LightGray else Color.Gray,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    val entries = otherUsersStatuses.entries.toList()
                    LazyColumn {
                        itemsIndexed(entries) { idx, entry ->
                            val contactPhone = entry.key
                            val contactStatuses = entry.value.sortedBy { it.timestamp }
                            val latestStatus = contactStatuses.last()

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        activeStoryList = contactStatuses
                                        activeStoryIndex = 0
                                    }
                                    .padding(vertical = 12.dp, horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Status ring around avatar (glowing outline for unread story)
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .border(2.5.dp, WhatsAppGreenVal, CircleShape)
                                        .padding(3.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AvatarView(name = latestStatus.name, base64 = latestStatus.avatar, size = 44)
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column {
                                    Text(latestStatus.name, fontWeight = FontWeight.Bold, color = if (isDark) Color.White else Color.Black, fontSize = 15.sp)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (!latestStatus.mediaUrl.isNullOrEmpty()) {
                                            val actType = if (appLanguage == "bn") "📷 ফটো আপডেট • " else "📷 Photo update • "
                                            actType + formatTimeAgo(latestStatus.timestamp)
                                        } else latestStatus.text,
                                        color = if (isDark) Color.LightGray else Color.Gray,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (idx < entries.size - 1) {
                                HorizontalDivider(color = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F1F1), modifier = Modifier.padding(start = 82.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal adding My text Status
    if (showAddTextStatusDialog) {
        val currentBgColor = bgColors[selectedBgColorIndex]
        AlertDialog(
            onDismissRequest = { showAddTextStatusDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(txt("টেক্সট স্ট্যাটাস দিন", "Write Text Status"), fontWeight = FontWeight.Bold, color = WhatsAppTealVal)
                    IconButton(
                        onClick = {
                            selectedBgColorIndex = (selectedBgColorIndex + 1) % bgColors.size
                        }
                    ) {
                        Icon(Icons.Default.ColorLens, contentDescription = txt("রং পরিবর্তন", "Change Background Color"), tint = WhatsAppTealVal)
                    }
                }
            },
            text = {
                Column {
                    Text(txt("আপনার মনের চমৎকার কথাটি টাইপ করুনঃ", "Type your wonderful thoughts:"), fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .background(Color(currentBgColor), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicTextField(
                            value = textStatusInputText,
                            onValueChange = { textStatusInputText = it },
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (textStatusInputText.isEmpty()) {
                            Text(
                                text = txt("এখানে লিখুন...", "Write here..."),
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (textStatusInputText.trim().isNotEmpty()) {
                            viewModel.postStatus(
                                text = textStatusInputText.trim(),
                                mediaUrl = null,
                                bgColorVal = currentBgColor.toLong()
                            )
                            textStatusInputText = ""
                            showAddTextStatusDialog = false
                            Toast.makeText(context, txt("টেক্সট স্ট্যাটাস আপডেট সম্পন্ন হয়েছে!", "Text status updated successfully!"), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, txt("দয়া করে কিছু লিখুন!", "Please type something first!"), Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WhatsAppTealVal)
                ) {
                    Text(txt("আপডেট", "Update"), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTextStatusDialog = false }) {
                    Text(txt("বাতিল", "Cancel"), color = Color.Red)
                }
            }
        )
    }

    // Modal adding My photo Status
    showAddPhotoStatusDialog?.let { localPath ->
        AlertDialog(
            onDismissRequest = { showAddPhotoStatusDialog = null },
            title = { Text(txt("ফটো স্ট্যাটাস দিন", "Add Photo Status"), fontWeight = FontWeight.Bold, color = WhatsAppTealVal) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = localPath,
                            contentDescription = "Selected Photo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = photoStatusInputText,
                        onValueChange = { photoStatusInputText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(txt("ক্যাপশন যোগ করুন (ঐচ্ছিক)...", "Add caption (optional)..."), color = if (isDark) Color.LightGray else Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = if (isDark) Color.White else Color.Black,
                            unfocusedTextColor = if (isDark) Color.White else Color.Black,
                            focusedBorderColor = WhatsAppTealVal,
                            unfocusedBorderColor = if (isDark) Color.Gray else Color.LightGray,
                            focusedContainerColor = if (isDark) Color(0xFF1E293B) else Color.Transparent,
                            unfocusedContainerColor = if (isDark) Color(0xFF1E293B) else Color.Transparent
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.postStatus(
                            text = photoStatusInputText.trim(),
                            mediaUrl = localPath,
                            bgColorVal = 0xFF000000L
                        )
                        photoStatusInputText = ""
                        showAddPhotoStatusDialog = null
                        Toast.makeText(context, txt("ফটো স্ট্যাটাস আপডেট সম্পন্ন হয়েছে!", "Photo status updated successfully!"), Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WhatsAppTealVal)
                ) {
                    Text(txt("আপডেট", "Update"), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddPhotoStatusDialog = null }) {
                    Text(txt("বাতিল", "Cancel"), color = Color.Red)
                }
            }
        )
    }

    // Modal chooser dialog for "আমার স্ট্যাটাস" empty tap
    if (showPostChooserDialog) {
        AlertDialog(
            onDismissRequest = { showPostChooserDialog = false },
            title = { Text(txt("স্ট্যাটাস আপডেট ধরন", "Choose Status Type"), fontWeight = FontWeight.Bold, color = WhatsAppTealVal) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            showPostChooserDialog = false
                            showAddTextStatusDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WhatsAppTealVal),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(txt("মন দিয়ে কিছু লিখুন (Text Status)", "Write status text (Text Status)"), color = Color.White)
                    }

                    Button(
                        onClick = {
                            showPostChooserDialog = false
                            photoStatusPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreenVal),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(txt("ছবি শেয়ার করুন (Photo Status)", "Share Photo (Photo Status)"), color = Color.White)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPostChooserDialog = false }) {
                    Text(txt("বাতিল", "Cancel"), color = Color.Gray)
                }
            }
        )
    }

    // Dynamic Fullscreen Story Player playing the Status list sequentially
    activeStoryList?.let { stories ->
        val storyCount = stories.size
        if (activeStoryIndex in 0 until storyCount) {
            val activeStory = stories[activeStoryIndex]
            var progress by remember { mutableStateOf(0f) }

            // Automatically progress each story ticking up to 1f and shifting index
            LaunchedEffect(activeStoryIndex) {
                progress = 0f
                while (progress < 1.0f) {
                    delay(50) // 5 seconds per story
                    progress += 0.01f
                }
                // Transition logic
                if (activeStoryIndex < storyCount - 1) {
                    activeStoryIndex++
                } else {
                    activeStoryList = null
                }
            }

            Dialog(
                onDismissRequest = { activeStoryList = null },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (activeStory.mediaUrl.isNullOrEmpty()) Color(activeStory.bgColorVal) else Color.Black)
                ) {
                    // Center content
                    if (!activeStory.mediaUrl.isNullOrEmpty()) {
                        // Image status
                        AsyncImage(
                            model = activeStory.mediaUrl,
                            contentDescription = "Status Story Image",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .align(Alignment.Center)
                        )

                        // Caption overlay at bottom
                        if (activeStory.text.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .padding(vertical = 20.dp, horizontal = 16.dp)
                                    .navigationBarsPadding()
                            ) {
                                Text(
                                    text = activeStory.text,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    } else {
                        // Text Status
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = activeStory.text,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // Top Indicators & Header controls
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 20.dp)
                            .statusBarsPadding()
                    ) {
                        // Staggered dashes logic
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            for (i in 0 until storyCount) {
                                val segmentProgress = when {
                                    i < activeStoryIndex -> 1.0f
                                    i == activeStoryIndex -> progress
                                    else -> 0.0f
                                }
                                LinearProgressIndicator(
                                    progress = { segmentProgress },
                                    color = Color.White,
                                    trackColor = Color.White.copy(alpha = 0.3f),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Left Side Avatar Name, Right Side Close
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AvatarView(name = activeStory.name, base64 = activeStory.avatar, size = 40)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(activeStory.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(formatTimeAgo(activeStory.timestamp), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                            }
                            IconButton(onClick = { activeStoryList = null }) {
                                Icon(Icons.Default.Close, contentDescription = "Close Story", tint = Color.White)
                            }
                        }
                    }

                    // Hot-zones for clicking previous/next (invisible overlay buttons)
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Left 1/3: Back trigger
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(1f)
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null
                                ) {
                                    if (activeStoryIndex > 0) {
                                        activeStoryIndex--
                                    } else {
                                        activeStoryList = null
                                    }
                                }
                        )

                        // Right 2/3: Next trigger
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(2f)
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null
                                ) {
                                    if (activeStoryIndex < storyCount - 1) {
                                        activeStoryIndex++
                                    } else {
                                        activeStoryList = null
                                    }
                                }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTabScreen(
    viewModel: ChatViewModel
) {
    val myPhone by viewModel.myNumber.collectAsStateWithLifecycle()
    val myNameState by viewModel.userDisplayName.collectAsStateWithLifecycle()
    val myProfilePic by viewModel.userProfilePicBase64.collectAsStateWithLifecycle()
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val txt = getTranslator(viewModel = viewModel)

    val myName = if (myNameState.trim().isEmpty() || myNameState == "বার্তা ব্যবহারকারী" || myNameState == "Barta User") {
        txt("বার্তা ব্যবহারকারী", "Barta User")
    } else {
        myNameState
    }

    var showPasswordChangeDialog by remember { mutableStateOf(false) }
    var newPasswordInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.img_app_logo),
                        contentDescription = "App Logo",
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .border(1.dp, Color.White, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = txt("সেটিংস", "Settings"),
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 18.sp
                        )
                        Text(
                            text = txt("লগইন মোবাইল নম্বরঃ $myPhone", "Logged in as: $myPhone"),
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = WhatsAppTealVal)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Profile Display Card with Password Change trigger
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AvatarView(name = myName, base64 = myProfilePic, size = 60)
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(myName, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(myPhone ?: "", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
                IconButton(
                    onClick = { showPasswordChangeDialog = true },
                    modifier = Modifier.testTag("change_password_button")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Change Password", tint = WhatsAppTealVal)
                }
            }
        }

        Text(
            text = txt("ভাষা সেটিংস", "Language Settings"),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp, top = 16.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RectangleShape
        ) {
            Column {
                var showLangMenu by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showLangMenu = true }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Language, contentDescription = null, tint = WhatsAppTealVal)
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(txt("অ্যাপের ভাষা", "App Language"), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text(if (appLanguage == "bn") "বাংলা (Bangla)" else "English (English)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }
                    Box {
                        TextButton(onClick = { showLangMenu = true }) {
                            Text(txt("পরিবর্তন করুন", "Change"), color = WhatsAppTealVal)
                        }
                        DropdownMenu(expanded = showLangMenu, onDismissRequest = { showLangMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("বাংলা (Bangla)") },
                                onClick = {
                                    viewModel.setAppLanguage("bn")
                                    showLangMenu = false
                                    Toast.makeText(viewModel.getApplication(), "ভাষা পরিবর্তন সফল হয়েছে!", Toast.LENGTH_SHORT).show()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("English (English)") },
                                onClick = {
                                    viewModel.setAppLanguage("en")
                                    showLangMenu = false
                                    Toast.makeText(viewModel.getApplication(), "Language changed successfully!", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }
        }

        Text(
            text = txt("থিম সেটিংস", "Theme Settings"),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RectangleShape
        ) {
            Column {
                var showThemeMenu by remember { mutableStateOf(false) }
                val isDarkTheme by viewModel.isDarkMode.collectAsStateWithLifecycle()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showThemeMenu = true }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Palette, contentDescription = null, tint = WhatsAppTealVal)
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(txt("অ্যাপের থিম", "App Theme"), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text(if (isDarkTheme) txt("কালো (Black)", "Black") else txt("সাদা (White)", "White"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }
                    Box {
                        TextButton(onClick = { showThemeMenu = true }) {
                            Text(txt("পরিবর্তন করুন", "Change"), color = WhatsAppTealVal)
                        }
                        DropdownMenu(expanded = showThemeMenu, onDismissRequest = { showThemeMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(txt("কালো (Black)", "Black")) },
                                onClick = {
                                    if (!isDarkTheme) {
                                        viewModel.toggleTheme()
                                    }
                                    showThemeMenu = false
                                    Toast.makeText(viewModel.getApplication(), txt("থিম পরিবর্তন সফল হয়েছে!", "Theme changed successfully!"), Toast.LENGTH_SHORT).show()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(txt("সাদা (White)", "White")) },
                                onClick = {
                                    if (isDarkTheme) {
                                        viewModel.toggleTheme()
                                    }
                                    showThemeMenu = false
                                    Toast.makeText(viewModel.getApplication(), txt("থিম পরিবর্তন সফল হয়েছে!", "Theme changed successfully!"), Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }
        }

        Text(
            text = txt("গোপনীয়তা সেটিংস", "Privacy Options"),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RectangleShape
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Last seen Privacy
                var lastSeenChoice by remember { mutableStateOf("Everyone") }
                var showLastSeenMenu by remember { mutableStateOf(false) }

                val displayedLastSeen = when (lastSeenChoice) {
                    "Everyone" -> txt("সবাই", "Everyone")
                    "My Contacts" -> txt("আমার পরিচিত", "My Contacts")
                    "Nobody" -> txt("কেউ না", "Nobody")
                    else -> lastSeenChoice
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(txt("সর্বশেষ সক্রিয়তা", "Last Seen"), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text(displayedLastSeen, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                    Box {
                        TextButton(onClick = { showLastSeenMenu = true }) {
                            Text(txt("বদল করুন", "Change"), color = WhatsAppTealVal)
                        }
                        DropdownMenu(expanded = showLastSeenMenu, onDismissRequest = { showLastSeenMenu = false }) {
                            listOf(
                                "Everyone" to txt("সবাই (Everyone)", "Everyone"),
                                "My Contacts" to txt("আমার পরিচিত (My Contacts)", "My Contacts"),
                                "Nobody" to txt("কেউ না (Nobody)", "Nobody")
                            ).forEach { (key, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        lastSeenChoice = key
                                        showLastSeenMenu = false
                                        Toast.makeText(viewModel.getApplication(), txt("গোপনীয়তা সেটিংস সফলভাবে আপডেট হয়েছে!", "Privacy settings successfully updated!"), Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                // Profile photo visibility
                var photoVisibility by remember { mutableStateOf("Everyone") }
                var showPhotoMenu by remember { mutableStateOf(false) }

                val displayedPhotoVisibility = when (photoVisibility) {
                    "Everyone" -> txt("সবাই", "Everyone")
                    "Nobody" -> txt("কেউ না", "Nobody")
                    else -> photoVisibility
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(txt("প্রোফাইল ফটো", "Profile Photo"), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text(displayedPhotoVisibility, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                    Box {
                        TextButton(onClick = { showPhotoMenu = true }) {
                            Text(txt("বদল করুন", "Change"), color = WhatsAppTealVal)
                        }
                        DropdownMenu(expanded = showPhotoMenu, onDismissRequest = { showPhotoMenu = false }) {
                            listOf(
                                "Everyone" to txt("সবাই (Everyone)", "Everyone"),
                                "Nobody" to txt("কেউ না (Nobody)", "Nobody")
                            ).forEach { (key, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        photoVisibility = key
                                        showPhotoMenu = false
                                        Toast.makeText(viewModel.getApplication(), txt("গোপনীয়তা সেটিংস সফলভাবে আপডেট হয়েছে!", "Privacy settings successfully updated!"), Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                // Read Receipts Switch
                var readReceiptsToggle by remember { mutableStateOf(true) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(txt("পঠিত বার্তা নিশ্চিতকরণ", "Read Receipts"), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text(txt("অন্য কারোর ব্লু টিক চ্যাট দেখতে এটি সাহায্য করে", "Allows you to see read receipts from others"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                    Switch(
                        checked = readReceiptsToggle,
                        onCheckedChange = {
                            readReceiptsToggle = it
                            Toast.makeText(viewModel.getApplication(), txt("পঠিত বার্তা রিসিট আপডেট হয়েছে!", "Read receipts updated!"), Toast.LENGTH_SHORT).show()
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = WhatsAppGreenVal, checkedTrackColor = WhatsAppGreenVal.copy(alpha = 0.4f))
                    )
                }
            }
        }

        Text(
            text = txt("এআই সহকারী কনফিগারেশন", "AI Assistant Configuration"),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RectangleShape
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                val functionsUrl by viewModel.firebaseFunctionsBaseUrl.collectAsStateWithLifecycle()
                var inputUrl by remember { mutableStateOf(functionsUrl) }
                
                LaunchedEffect(functionsUrl) {
                    inputUrl = functionsUrl
                }

                Text(
                    text = txt("ফায়ারবেস ক্লাউড ফাংশন ইউআরএল", "Firebase Cloud Functions URL"),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = txt(
                        "আপনার এআই পিন সুরক্ষিত রাখতে deployed ক্লাউড ফাংশনের বেস ইউআরএল দিন। ফাঁকা রাখলে এটি অ্যাপের ভেতর থেকে সুরক্ষিত সিস্টেমে সরাসরি কাজ করবে।",
                        "Configure your Cloud Function base URL for advanced production security. Leaving it blank triggers secure app-level fallback mode."
                    ),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = inputUrl,
                    onValueChange = { inputUrl = it },
                    placeholder = { Text("https://us-central1-barta-chat-927ec.cloudfunctions.net") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = WhatsAppTealVal,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("functions_url_input")
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        viewModel.saveFirebaseFunctionsUrl(inputUrl)
                        Toast.makeText(viewModel.getApplication(), txt("কনফিগারেশন সফলভাবে সংরক্ষিত হয়েছে!", "Configuration successfully saved!"), Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WhatsAppTealVal),
                    modifier = Modifier.align(Alignment.End).testTag("save_functions_url_button")
                ) {
                    Text(txt("সংরক্ষণ করুন", "Save"), color = Color.White)
                }
            }
        }

        // Logout Area
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RectangleShape
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        viewModel.logout()
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.ExitToApp, contentDescription = null, tint = Color.Red)
                Spacer(modifier = Modifier.width(8.dp))
                Text(txt("লগআউট করুন", "Logout"), color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }

    if (showPasswordChangeDialog) {
        AlertDialog(
            onDismissRequest = { showPasswordChangeDialog = false },
            title = { Text("পাসওয়ার্ড পরিবর্তন", fontWeight = FontWeight.Bold, color = WhatsAppTealVal) },
            text = {
                Column {
                    Text("অনন্য নতুন ৬ থেকে ৮ অক্ষরের পাসওয়ার্ড সতর্কতার সাথে দিনঃ", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newPasswordInput,
                        onValueChange = { newPasswordInput = it },
                        placeholder = { Text("******") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = WhatsAppTealVal,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.changePassword(newPasswordInput) { err ->
                            if (err != null) {
                                Toast.makeText(viewModel.getApplication(), err, Toast.LENGTH_LONG).show()
                            } else {
                                showPasswordChangeDialog = false
                                newPasswordInput = ""
                                Toast.makeText(viewModel.getApplication(), "পাসওয়ার্ড সফলভাবে পরিবর্তিত হয়েছে!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WhatsAppTealVal)
                ) {
                    Text("আপডেট", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordChangeDialog = false }) {
                    Text("বাতিল", color = Color.Red)
                }
            }
        )
    }
}

data class UserStatus(
    val phone: String,
    val name: String,
    val avatar: String,
    val text: String,
    val timeAgo: String,
    val bgColor: Color
)

@Composable
fun ChatRowItem(
    contact: Contact,
    lang: String = "bn",
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 16.dp)
            .testTag("chat_row_${contact.phone}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (contact.isGroup) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(WhatsAppTealVal, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Groups, contentDescription = "Group", tint = Color.White)
            }
        } else {
            AvatarView(name = contact.name, base64 = contact.profilePicUri, size = 44)
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = contact.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (contact.isGroup) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFE8F5E9), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text("গ্রুপ", color = WhatsAppGreenVal, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                
                val isOnline = contact.lastSeen == "online" || contact.lastSeen == "অনলাইন"
                Text(
                    text = if (contact.isGroup) "group" else formatLastSeenDynamic(contact.lastSeen, lang),
                    color = if (isOnline) WhatsAppGreenVal else Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = if (isOnline) FontWeight.Bold else FontWeight.Normal
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (contact.typingStatus.isNotEmpty()) {
                    Text(
                        text = "typing...",
                        color = WhatsAppGreenVal,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    Text(
                        text = contact.lastMessageText ?: "কোনো বার্তা নেই",
                        color = Color.Gray,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (contact.unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .background(WhatsAppGreenVal, shape = CircleShape)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = contact.unreadCount.toString(),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsTabScreen(
    viewModel: ChatViewModel,
    onContactClick: (Contact) -> Unit
) {
    val contactList by viewModel.contacts.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showSyncDialog by remember { mutableStateOf(false) }
    val txt = getTranslator(viewModel = viewModel)

    var activeSubTab by remember { mutableStateOf("local") } // "local" or "firestore"
    var searchQueryText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.img_app_logo),
                            contentDescription = "App Logo",
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .border(1.dp, Color.White, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = txt("নতুন চ্যাট পরিচিতি", "New Conversation"),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = WhatsAppTealVal),
                actions = {
                    IconButton(
                        onClick = { showSyncDialog = true },
                        modifier = Modifier.testTag("sync_contacts_action")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Sync Contacts",
                            tint = Color.White
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (activeSubTab == "local") {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    // Group Create FAB
                    ExtendedFloatingActionButton(
                        onClick = { showCreateGroupDialog = true },
                        containerColor = WhatsAppTealVal,
                        contentColor = Color.White,
                        icon = { Icon(Icons.Default.Groups, contentDescription = "Create Group") },
                        text = { Text(txt("নতুন গ্রুপ", "New Group")) },
                        modifier = Modifier.testTag("create_group_fab")
                    )

                    // Contact Add FAB
                    FloatingActionButton(
                        onClick = { showAddDialog = true },
                        containerColor = WhatsAppGreenVal,
                        contentColor = Color.White,
                        modifier = Modifier.testTag("add_contact_fab")
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = "Add Contact")
                    }
                }
            } else {
                FloatingActionButton(
                    onClick = { viewModel.fetchFirestoreUsers() },
                    containerColor = WhatsAppGreenVal,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("refresh_firestore_users_fab")
                ) {
                    Icon(Icons.Default.Sync, contentDescription = "Refresh Registered Users")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // subTab switcher pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = RoundedCornerShape(24.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                // My local contacts option
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            if (activeSubTab == "local") WhatsAppTealVal else Color.Transparent
                        )
                        .clickable { activeSubTab = "local" }
                        .testTag("subtab_local"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = txt("আমার পরিচিতি", "My Contacts"),
                        color = if (activeSubTab == "local") Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                // Firestore Barta registered users option
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            if (activeSubTab == "firestore") WhatsAppTealVal else Color.Transparent
                        )
                        .clickable { activeSubTab = "firestore" }
                        .testTag("subtab_firestore"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = txt("নিবন্ধিত সব ব্যবহারকারী", "Registered Users"),
                            color = if (activeSubTab == "firestore") Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(Color(0xFF10B981), shape = CircleShape)
                        )
                    }
                }
            }

            // Standalone localized Search text field
            OutlinedTextField(
                value = searchQueryText,
                onValueChange = { searchQueryText = it },
                placeholder = {
                    Text(
                        text = if (activeSubTab == "local") txt("কন্টাক্ট খুঁজুন...", "Search contacts...") else txt("ব্যবহারকারী খুঁজুন...", "Search registered users..."),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        fontSize = 14.sp
                    )
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search Icon", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                },
                trailingIcon = {
                    if (searchQueryText.isNotEmpty()) {
                        IconButton(onClick = { searchQueryText = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("contacts_search_input"),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedBorderColor = WhatsAppTealVal,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier.weight(1f)
            ) {
                if (activeSubTab == "local") {
                    val filteredContacts = remember(contactList, searchQueryText) {
                        if (searchQueryText.isBlank()) {
                            contactList
                        } else {
                            contactList.filter {
                                it.name.contains(searchQueryText, ignoreCase = true) || it.phone.contains(searchQueryText)
                            }
                        }
                    }

                    if (filteredContacts.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = if (searchQueryText.isEmpty()) txt("কোনো পরিচিতি নেই। প্লাস চাপুন!", "No contacts found. Press the add button!") else txt("কোনো মিল পাওয়া যায়নি।", "No matching contacts."),
                                color = Color.Gray
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(filteredContacts) { contact ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onContactClick(contact) }
                                        .padding(16.dp)
                                        .testTag("contact_row_${contact.phone}"),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (contact.isGroup) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .background(WhatsAppTealVal, shape = CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Groups, contentDescription = null, tint = Color.White)
                                        }
                                    } else {
                                        AvatarView(name = contact.name, base64 = contact.profilePicUri, size = 44)
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(contact.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
                                        Text(contact.phone, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                                    }
                                    if (!contact.isGroup) {
                                        Icon(
                                            imageVector = Icons.Default.Chat,
                                            contentDescription = "Chat",
                                            tint = WhatsAppTealVal
                                        )
                                    }
                                }
                                HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f), thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 16.dp))
                            }
                        }
                    }
                } else {
                    val myPhone by viewModel.myNumber.collectAsStateWithLifecycle()
                    val firestoreUsers by viewModel.firestoreUsers.collectAsStateWithLifecycle()
                    val isFetching by viewModel.isFetchingFirestoreUsers.collectAsStateWithLifecycle()

                    LaunchedEffect(Unit) {
                        viewModel.fetchFirestoreUsers()
                    }

                    val filteredFirestoreList = remember(firestoreUsers, myPhone, searchQueryText) {
                        firestoreUsers
                            .filter { userMap ->
                                val uPhone = userMap["phone"] as? String ?: ""
                                uPhone != myPhone
                            }
                            .filter { userMap ->
                                val uName = userMap["name"] as? String ?: ""
                                val uPhone = userMap["phone"] as? String ?: ""
                                searchQueryText.isBlank() || uName.contains(searchQueryText, ignoreCase = true) || uPhone.contains(searchQueryText)
                            }
                    }

                    if (isFetching) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = WhatsAppTealVal)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(txt("সার্ভার থেকে ব্যবহারকারী লোড হচ্ছে...", "Loading registered users..."), color = Color.Gray, fontSize = 14.sp)
                            }
                        }
                    } else if (filteredFirestoreList.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "No users",
                                    tint = Color.LightGray,
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = if (searchQueryText.isNotEmpty()) txt("কোনো মিল পাওয়া যায়নি", "No matching users found") else txt("সার্ভারে কোনো ব্যবহারকারী নথিভুক্ত নেই অথবা ডাটাবেজ অফলাইন", "No registered users or database offline"),
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { viewModel.fetchFirestoreUsers() },
                                    colors = ButtonDefaults.buttonColors(containerColor = WhatsAppTealVal)
                                ) {
                                    Text(txt("পুনরায় লোড করুন 🔄", "Reload Users 🔄"))
                                }
                            }
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(filteredFirestoreList) { userMap ->
                                val uPhone = userMap["phone"] as? String ?: ""
                                val uName = userMap["name"] as? String ?: ""
                                val uStatus = userMap["status"] as? String ?: txt("বার্তা (Chat) ব্যবহার করছি!", "Using Barta Chat!")
                                val uPic = userMap["profilePicBase64"] as? String ?: ""

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.initiateChatWithRegisteredUser(uPhone, uName, uPic) { contact ->
                                                onContactClick(contact)
                                            }
                                        }
                                        .padding(16.dp)
                                        .testTag("firestore_row_$uPhone"),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AvatarView(name = uName, base64 = uPic, size = 44)
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(uName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(uStatus, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), fontSize = 13.sp, maxLines = 1)
                                        Text(uPhone, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .background(WhatsAppTealVal.copy(alpha = 0.15f), shape = RoundedCornerShape(16.dp))
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = txt("চ্যাট 💬", "Chat 💬"),
                                            color = WhatsAppTealVal,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                                HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f), thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 16.dp))
                            }
                        }
                    }
                }
            }

            if (showAddDialog) {
                AddContactDialog(
                    viewModel = viewModel,
                    onDismiss = { showAddDialog = false },
                    onConfirm = { name, phone, autoReply ->
                        viewModel.addNewContact(name, phone, autoReply)
                        showAddDialog = false
                    }
                )
            }

            if (showSyncDialog) {
                SyncContactsDialog(
                    viewModel = viewModel,
                    onDismiss = { showSyncDialog = false }
                )
            }

            if (showCreateGroupDialog) {
                CreateGroupDialog(
                    contacts = contactList.filter { !it.isGroup },
                    viewModel = viewModel,
                    onDismiss = { showCreateGroupDialog = false },
                    onConfirm = { name, selected ->
                        viewModel.createGroup(name, selected)
                        showCreateGroupDialog = false
                    }
                )
            }
        }
    }
}

// Group Chat Creation Dialog
@Composable
fun CreateGroupDialog(
    contacts: List<Contact>,
    viewModel: ChatViewModel,
    onDismiss: () -> Unit,
    onConfirm: (groupName: String, selected: List<Contact>) -> Unit
) {
    val context = LocalContext.current
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val isBn = appLanguage == "bn"
    fun txt(bn: String, en: String) = if (isBn) bn else en

    var groupName by remember { mutableStateOf("") }
    val selectedContacts = remember { mutableStateListOf<Contact>() }
    var hasError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(txt("নতুন গ্রুপ চ্যাট খুলুন", "Create New Group Chat"), fontWeight = FontWeight.Bold, color = WhatsAppTealVal) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = {
                        groupName = it
                        hasError = false
                    },
                    label = { Text(txt("গ্রুপের নাম", "Group Name")) },
                    placeholder = { Text(txt("যেমন: বন্ধুদের আড্ডা", "e.g., Friends Corner")) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = WhatsAppTealVal
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (hasError) {
                    Text(txt("গ্রুপের নাম অবশ্যই দিতে হবে!", "Group name is required!"), color = Color.Red, fontSize = 11.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(txt("সদস্য যুক্ত করুন (পছন্দ করুন):", "Select members to add:"), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(modifier = Modifier.height(180.dp)) {
                    items(contacts) { contact ->
                        val isChecked = selectedContacts.contains(contact)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isChecked) selectedContacts.remove(contact)
                                    else selectedContacts.add(contact)
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = {
                                    if (it == true) selectedContacts.add(contact)
                                    else selectedContacts.remove(contact)
                                }
                            )
                            AvatarView(name = contact.name, base64 = contact.profilePicUri, size = 32)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(contact.name, color = Color.White, fontSize = 14.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (groupName.trim().isEmpty()) {
                        hasError = true
                    } else {
                        onConfirm(groupName, selectedContacts.toList())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreenVal)
            ) {
                Text(txt("তৈরি করুন", "Create"), color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(txt("বাতিল", "Cancel"), color = Color.Gray)
            }
        }
    )
}

@Composable
fun AddContactDialog(
    viewModel: ChatViewModel,
    onDismiss: () -> Unit,
    onConfirm: (name: String, phone: String, simulateReply: Boolean) -> Unit
) {
    val context = LocalContext.current
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val isBn = appLanguage == "bn"
    fun txt(bn: String, en: String) = if (isBn) bn else en

    var contactName by remember { mutableStateOf("") }
    var contactPhone by remember { mutableStateOf("") }
    var autoReplySim by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(txt("নতুন পরিচিতি যোগ করুন", "Add New Contact")) },
        text = {
            Column {
                OutlinedTextField(
                    value = contactName,
                    onValueChange = { contactName = it },
                    label = { Text(txt("নাম", "Name")) },
                    placeholder = { Text(txt("সাকিব চৌধুরী", "Sakib Chowdhury")) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = WhatsAppTealVal,
                        unfocusedLabelColor = Color.Gray,
                        focusedPlaceholderColor = Color.Gray,
                        unfocusedPlaceholderColor = Color.Gray
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_contact_name")
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = contactPhone,
                    onValueChange = {
                        contactPhone = it.filter { ch -> ch.isDigit() }
                        hasError = false
                    },
                    label = { Text(txt("মোবাইল নাম্বার", "Mobile Number")) },
                    placeholder = { Text("01XXXXXXXXX") },
                    prefix = { Text("+88 ") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = WhatsAppTealVal,
                        unfocusedLabelColor = Color.Gray,
                        focusedPrefixColor = Color.White,
                        unfocusedPrefixColor = Color.White,
                        focusedPlaceholderColor = Color.Gray,
                        unfocusedPlaceholderColor = Color.Gray
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_contact_phone")
                )

                if (hasError) {
                    Text(
                        text = txt("১১ ডিজিটের সঠিক বাংলাদেশ মোবাইল নাম্বার দিন!", "Enter a valid 11-digit Bangladeshi mobile number!"),
                        color = Color.Red,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { autoReplySim = !autoReplySim }
                ) {
                    Checkbox(
                        checked = autoReplySim,
                        onCheckedChange = { autoReplySim = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(txt("অনলাইন উত্তরদাতা ও বট সিমুলেশন সচল রাখুন।", "Enable online auto-responder and bot simulation."), fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clean = contactPhone.trim()
                    if (clean.length == 11 && clean.startsWith("01")) {
                        onConfirm(contactName, clean, autoReplySim)
                    } else {
                        hasError = true
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreenVal),
                modifier = Modifier.testTag("dialog_confirm_button")
            ) {
                Text(txt("যোগ করুন", "Add Contact"), color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(txt("বাতিল করুন", "Cancel"), color = Color.Gray)
            }
        }
    )
}

@Composable
fun SyncContactsDialog(
    viewModel: ChatViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val isBn = appLanguage == "bn"
    fun txt(bn: String, en: String) = if (isBn) bn else en

    val syncedContacts by viewModel.syncedContacts.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    var syncTriggered by remember { mutableStateOf(false) }
    var firebaseUsersList by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }

    LaunchedEffect(Unit) {
        firebaseUsersList = viewModel.getFirebaseUsers()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        syncTriggered = true
        val contacts = getDeviceContactsNatively(context, firebaseUsersList)
        viewModel.syncContacts(contacts)
        if (isGranted) {
            Toast.makeText(context, txt("কন্ট্যাক্ট পড়ার অনুমতি পাওয়া গেছে!", "Contacts read permission granted!"), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, txt("কন্ট্যাক্ট পারমিশন না দেওয়ায় ডেমো কন্ট্যাক্ট দিয়ে তুলনা করা হচ্ছে।", "Comparing with demo contacts as contact permission was denied."), Toast.LENGTH_LONG).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                     imageVector = Icons.Default.Sync,
                     contentDescription = null,
                     tint = WhatsAppTealVal,
                     modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(txt("কন্ট্যাক্ট সিঙ্ক (সমলয়)", "Sync Contacts"), color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = txt("আপনার ফোনের কন্ট্যাক্ট তালিকা মিলিয়ে দেখুন এবং আপনার পরিচিত কে কে 'বার্তা' অ্যাপ ব্যবহার করছেন তা সরাসরি চ্যাট তালিকায় যুক্ত করুন।", "Match your phone contact list and directly add acquaintances who use 'Barta' app to your chat list."),
                    color = Color.LightGray,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                if (!syncTriggered) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = {
                                val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.READ_CONTACTS
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                
                                if (hasPermission) {
                                    syncTriggered = true
                                    val contacts = getDeviceContactsNatively(context, firebaseUsersList)
                                    viewModel.syncContacts(contacts)
                                } else {
                                    permissionLauncher.launch(android.Manifest.permission.READ_CONTACTS)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = WhatsAppTealVal),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().testTag("start_sync_btn")
                        ) {
                            Icon(imageVector = Icons.Default.Contacts, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(txt("সিঙ্ক আরম্ভ করুন", "Start Sync"), fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                } else {
                    if (isSyncing) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = WhatsAppTealVal, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(txt("সিঙ্ক করা হচ্ছে, অনুগ্রহ করে অপেক্ষা করুন...", "Syncing, please wait..."), color = Color.Gray, fontSize = 13.sp)
                        }
                    } else {
                        if (syncedContacts.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(txt("কোনো কন্ট্যাক্ট পাওয়া যায়নি যারা অ্যাপ ব্যবহার করছেন!", "No contacts found using Barta!"), color = Color.Gray, fontSize = 14.sp)
                            }
                        } else {
                            Text(
                                text = txt("বার্তা ব্যবহারকারী পাওয়া গেছে (${syncedContacts.size} জন):", "Barta users found (${syncedContacts.size} users):"),
                                color = WhatsAppGreenVal,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 260.dp)
                            ) {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(syncedContacts) { match ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFF263238), RoundedCornerShape(8.dp))
                                                .padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            AvatarView(name = match.deviceName, base64 = match.profilePicBase64, size = 38)
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(match.deviceName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                                Text("${match.appName} • ${match.phone}", color = Color.LightGray, fontSize = 11.sp)
                                                if (match.status.isNotEmpty()) {
                                                    Text(match.status, color = Color.Gray, fontSize = 11.sp, maxLines = 1)
                                                }
                                            }
                                            
                                            if (match.alreadyAdded) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "Added",
                                                    tint = WhatsAppGreenVal,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            } else {
                                                Button(
                                                    onClick = {
                                                        viewModel.addSyncedContact(match)
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreenVal),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                    shape = RoundedCornerShape(16.dp),
                                                    modifier = Modifier.height(30.dp)
                                                ) {
                                                    Text(txt("যুক্ত করুন", "Add"), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(txt("বন্ধ করুন", "Close"), color = WhatsAppTealVal, fontWeight = FontWeight.Bold)
            }
        }
    )
}

fun getDeviceContactsNatively(context: Context, fallbackUsers: List<Map<String, Any>>): List<Pair<String, String>> {
    val contactsList = mutableListOf<Pair<String, String>>()
    val contentResolver = context.contentResolver
    try {
        val cursor = contentResolver.query(
            android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null,
            null,
            null
        )
        cursor?.use {
            val nameIndex = it.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = it.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val name = if (nameIndex >= 0) it.getString(nameIndex) else ""
                val number = if (numberIndex >= 0) it.getString(numberIndex) else ""
                if (number.isNotEmpty()) {
                    contactsList.add(name to number)
                }
            }
        }
    } catch (e: Exception) {
        // Ignored or permission wasn't granted yet
    }

    if (contactsList.isEmpty()) {
        val demoNames = listOf(
            "সাকিব চৌধুরী",
            "তানিম ইকবাল",
            "মুশফিকুর রহমান",
            "মাহমুদুল্লাহ রিয়াদ",
            "আরিফ আহমেদ",
            "আজিম রহমান",
            "নাবিলা ইসলাম",
            "ফারিয়া সুলতানা"
        )
        if (fallbackUsers.isNotEmpty()) {
            fallbackUsers.forEachIndexed { index, userDoc ->
                val phone = userDoc["phone"] as? String ?: ""
                val name = userDoc["name"] as? String ?: "ব্যবহারকারী"
                if (phone.isNotEmpty()) {
                    val demoName = if (index < demoNames.size) "${demoNames[index]} (ডিভাইস কন্ট্যাক্ট)" else "$name (ডিভাইস কন্ট্যাক্ট)"
                    contactsList.add(demoName to phone)
                }
            }
        } else {
            contactsList.add("সাকিব আল হাসান" to "01711111111")
            contactsList.add("আরিফ রহমান" to "01822222222")
            contactsList.add("ফারিয়া সুলতানা" to "01933333333")
        }
    }
    return contactsList
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileTabScreen(
    viewModel: ChatViewModel
) {
    val txt = getTranslator(viewModel = viewModel)
    val myPhone by viewModel.myNumber.collectAsStateWithLifecycle()

    val initialDisplayName by viewModel.userDisplayName.collectAsStateWithLifecycle()
    val initialStatusMessage by viewModel.userStatusMessage.collectAsStateWithLifecycle()
    val initialProfilePic by viewModel.userProfilePicBase64.collectAsStateWithLifecycle()

    var displayNameInput by remember { mutableStateOf("") }
    var statusMessageInput by remember { mutableStateOf("") }
    var profilePicState by remember { mutableStateOf("") }

    var profileSavedAlert by remember { mutableStateOf(false) }
    var showPhotoSelectorProfile by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val galleryPicSuccessMsg = txt("গ্যালারি থেকে প্রোফাইল ছবি নেওয়া হয়েছে!", "Profile picture selected from gallery!")
    val galleryPicErrorMsg = txt("ছবি লোড করতে সমস্যা হয়েছে!", "Failed to load image!")

    val profilePhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                val localPath = copyUriToLocalFile(context, uri)
                if (localPath != null) {
                    profilePicState = localPath
                    showPhotoSelectorProfile = false
                    Toast.makeText(context, galleryPicSuccessMsg, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, galleryPicErrorMsg, Toast.LENGTH_SHORT).show()
                }
            }
        }
    )

    var tempCameraFileProfile by remember { mutableStateOf<java.io.File?>(null) }
    var tempCameraUriProfile by remember { mutableStateOf<android.net.Uri?>(null) }

    val cameraLauncherProfile = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            if (success) {
                val file = tempCameraFileProfile
                if (file != null && file.exists() && file.length() > 0) {
                    profilePicState = file.absolutePath
                    showPhotoSelectorProfile = false
                    Toast.makeText(context, txt("ক্যামেরা থেকে প্রোফাইল ছবি নেওয়া হয়েছে!", "Profile picture selected from camera!"), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, txt("ছবি ফাইল তৈরি করা যায়নি!", "Failed to create picture file!"), Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, txt("ছবি ধারণ বাতিল বা ব্যর্থ হয়েছে!", "Camera capture cancelled or failed!"), Toast.LENGTH_SHORT).show()
            }
        }
    )

    val cameraPermissionLauncherProfile = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                try {
                    val file = java.io.File(context.cacheDir, "profile_${System.currentTimeMillis()}.jpg")
                    val uri = androidx.core.content.FileProvider.getUriForFile(context, "com.example.fileprovider", file)
                    tempCameraFileProfile = file
                    tempCameraUriProfile = uri
                    cameraLauncherProfile.launch(uri)
                } catch (e: Exception) {
                    Toast.makeText(context, txt("ক্যামেরা ছবি প্রস্তুত করতে ব্যর্থ হয়েছে!", "Failed to prepare camera file!"), Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, txt("ক্যামেরা ব্যবহারের অনুমতি প্রয়োজন!", "Camera permission is required!"), Toast.LENGTH_SHORT).show()
            }
        }
    )

    val launchCameraFlowProfile: () -> Unit = {
        val hasCamPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (hasCamPermission) {
            try {
                val file = java.io.File(context.cacheDir, "profile_${System.currentTimeMillis()}.jpg")
                val uri = androidx.core.content.FileProvider.getUriForFile(context, "com.example.fileprovider", file)
                tempCameraFileProfile = file
                tempCameraUriProfile = uri
                cameraLauncherProfile.launch(uri)
            } catch (e: Exception) {
                Toast.makeText(context, txt("ক্যামেরা ছবি প্রস্তুত করতে ব্যর্থ হয়েছে!", "Failed to prepare camera file!"), Toast.LENGTH_SHORT).show()
            }
        } else {
            cameraPermissionLauncherProfile.launch(android.Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(initialDisplayName, initialStatusMessage, initialProfilePic) {
        displayNameInput = initialDisplayName
        statusMessageInput = initialStatusMessage
        profilePicState = initialProfilePic
    }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.img_app_logo),
                        contentDescription = "App Logo",
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .border(1.dp, Color.White, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = txt("প্রোফাইল সংশোধন (Edit Profile)", "Edit Profile"),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = WhatsAppTealVal)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .clip(CircleShape)
                                .clickable { showPhotoSelectorProfile = true }
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(2.5.dp, WhatsAppGreenVal, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (profilePicState.startsWith("/") || profilePicState.startsWith("content:") || profilePicState.startsWith("file:")) {
                                AsyncImage(
                                    model = profilePicState,
                                    contentDescription = "Profile Picture",
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Image(
                                    imageVector = getAvatarVector(profilePicState),
                                    contentDescription = "Profile Picture",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = txt("মোবাইল নাম্বারঃ $myPhone", "Mobile Number: $myPhone"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = WhatsAppTealVal
                        )
                        Text(
                            text = txt("ছবি পরিবর্তন করতে বৃত্তটিতে স্পর্শ করুন", "Tap on circle to change picture"),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = txt("ব্যক্তিগত তথ্যসমূহ", "Personal Information"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        OutlinedTextField(
                            value = displayNameInput,
                            onValueChange = {
                                displayNameInput = it
                                profileSavedAlert = false
                            },
                            label = { Text(txt("ডিসপ্লে নাম / Display Name", "Display Name")) },
                            placeholder = { Text(txt("উদা: আকাশ চৌধুরী", "e.g. John Doe")) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedLabelColor = WhatsAppTealVal,
                                unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_display_name_field")
                        )

                        OutlinedTextField(
                            value = statusMessageInput,
                            onValueChange = {
                                statusMessageInput = it
                                profileSavedAlert = false
                            },
                            label = { Text(txt("স্ট্যাটাস মেসেজ / Status Message", "Status Message")) },
                            placeholder = { Text(txt("উদা: চ্যাট করছি বা ব্যস্ত আছি", "e.g. Chatting or Busy")) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedLabelColor = WhatsAppTealVal,
                                unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_status_message_field")
                        )

                        Button(
                            onClick = {
                                viewModel.saveProfile(displayNameInput, statusMessageInput, profilePicState)
                                profileSavedAlert = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreenVal),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("save_profile_button"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(txt("প্রোফাইল সেভ করুন", "Save Profile"), color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        if (profileSavedAlert) {
                            Text(
                                text = txt("প্রোফাইল সফলভাবে আপডেট করা হয়েছে! 🟢", "Profile updated successfully! 🟢"),
                                color = WhatsAppGreenVal,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPhotoSelectorProfile) {
        AlertDialog(
            onDismissRequest = { showPhotoSelectorProfile = false },
            title = { Text(txt("প্রোফাইল ছবি পরিবর্তন", "Change Profile Photo"), fontWeight = FontWeight.Bold, color = WhatsAppTealVal) },
            text = {
                Column {
                    val dummyPics = listOf("pic1", "pic2", "pic3", "pic4", "pic5", "pic6")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        dummyPics.take(3).forEach { p ->
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(CircleShape)
                                    .border(
                                        width = if (profilePicState == p) 3.dp else 1.dp,
                                        color = if (profilePicState == p) WhatsAppGreenVal else Color.LightGray,
                                        shape = CircleShape
                                    )
                                    .clickable { profilePicState = p }
                            ) {
                                Image(imageVector = getAvatarVector(p), contentDescription = null, modifier = Modifier.fillMaxSize())
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        dummyPics.drop(3).forEach { p ->
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(CircleShape)
                                    .border(
                                        width = if (profilePicState == p) 3.dp else 1.dp,
                                        color = if (profilePicState == p) WhatsAppGreenVal else Color.LightGray,
                                        shape = CircleShape
                                    )
                                    .clickable { profilePicState = p }
                            ) {
                                Image(imageVector = getAvatarVector(p), contentDescription = null, modifier = Modifier.fillMaxSize())
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                profilePhotoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = WhatsAppTealVal),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(txt("গ্যালারি", "Gallery"), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                launchCameraFlowProfile()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreenVal),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(txt("ক্যামেরা", "Camera"), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showPhotoSelectorProfile = false },
                    colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreenVal)
                ) {
                    Text(txt("ঠিক আছে", "OK"), color = Color.White)
                }
            }
        )
    }
}

// WhatsApp style message console with header displaying group participants details + active typing signals
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatWindowScreen(
    contact: Contact,
    viewModel: ChatViewModel,
    onClose: () -> Unit
) {
    val messageList by viewModel.activeMessages.collectAsStateWithLifecycle()
    val myNumber by viewModel.myNumber.collectAsStateWithLifecycle()
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val txt = getTranslator(viewModel = viewModel)
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    var showGroupManageDialog by remember { mutableStateOf(false) }

    // Attachment States
    var showAttachOptions by remember { mutableStateOf(false) }
    var showSelectImageDialog by remember { mutableStateOf(false) }
    var showSelectVideoDialog by remember { mutableStateOf(false) }
    var selectedImageForPreview by remember { mutableStateOf<String?>(null) }
    var selectedVideoForPlayback by remember { mutableStateOf<String?>(null) }
    var showEmojiPicker by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                val localPath = copyUriToLocalFile(context, uri)
                if (localPath != null) {
                    viewModel.sendMediaMessage(localPath, "image")
                    showSelectImageDialog = false
                    Toast.makeText(context, "ছবি সফলভাবে পাঠানো হয়েছে!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "ছবি লোড করতে সমস্যা হয়েছে!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    )

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                val localPath = copyUriToLocalFile(context, uri)
                if (localPath != null) {
                    viewModel.sendMediaMessage(localPath, "video")
                    showSelectVideoDialog = false
                    Toast.makeText(context, "ভিডিও সফলভাবে পাঠানো হয়েছে!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "ভিডিও লোড করতে সমস্যা হয়েছে!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    )

    var tempCameraFile by remember { mutableStateOf<java.io.File?>(null) }
    var tempCameraUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            if (success) {
                val file = tempCameraFile
                if (file != null && file.exists() && file.length() > 0) {
                    viewModel.uploadAndSendImageMessage(file) { sent ->
                        if (sent) {
                            Toast.makeText(context, "ছবি সফলভাবে পাঠানো হয়েছে!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "ছবি পাঠাতে ব্যর্থ হয়েছে!", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(context, "ছবি ফাইল তৈরি করা যায়নি!", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "ছবি ধারণ বাতিল বা ব্যর্থ হয়েছে!", Toast.LENGTH_SHORT).show()
            }
        }
    )

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                try {
                    val file = java.io.File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
                    val uri = androidx.core.content.FileProvider.getUriForFile(context, "com.example.fileprovider", file)
                    tempCameraFile = file
                    tempCameraUri = uri
                    cameraLauncher.launch(uri)
                } catch (e: Exception) {
                    Toast.makeText(context, "ক্যামেরা ছবি প্রস্তুত করতে ব্যর্থ হয়েছে!", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "ক্যামেরা ব্যবহারের অনুমতি প্রয়োজন!", Toast.LENGTH_SHORT).show()
            }
        }
    )

    val launchCameraFlow: () -> Unit = {
        val hasCamPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (hasCamPermission) {
            try {
                val file = java.io.File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
                val uri = androidx.core.content.FileProvider.getUriForFile(context, "com.example.fileprovider", file)
                tempCameraFile = file
                tempCameraUri = uri
                cameraLauncher.launch(uri)
            } catch (e: Exception) {
                Toast.makeText(context, "ক্যামেরা ছবি প্রস্তুত করতে ব্যর্থ হয়েছে!", Toast.LENGTH_SHORT).show()
            }
        } else {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    // Send 'typing...' status to Firestore/Room dynamically as user types
    LaunchedEffect(inputText) {
        viewModel.updateMyTypingStatus(contact.phone, inputText.isNotEmpty())
    }

    // Automatically scroll to latest chat bubble
    LaunchedEffect(messageList.size) {
        if (messageList.isNotEmpty()) {
            listState.animateScrollToItem(messageList.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WhatsAppGrayBackgroundVal)
    ) {
        // Chat Header with details
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = onClose, modifier = Modifier.testTag("chat_back_button")) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (contact.isGroup) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.White.copy(alpha = 0.2f), shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Groups, contentDescription = null, tint = Color.White)
                        }
                    } else {
                        AvatarView(name = contact.name, base64 = contact.profilePicUri, size = 36)
                    }

                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = contact.name,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        
                        // Show participants details if it is a Group Chat
                        if (contact.isGroup) {
                            Text(
                                text = txt("সদস্যরা: ", "Members: ") + contact.groupParticipants.replace("group_", "").take(80),
                                color = Color(0xB3FFFFFF),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Text(
                                text = if (contact.typingStatus.isNotEmpty()) contact.typingStatus else formatLastSeenDynamic(contact.lastSeen, appLanguage),
                                color = if (contact.typingStatus.isNotEmpty()) WhatsAppGreenVal else Color(0xB3FFFFFF),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            },
            actions = {
                if (contact.isGroup) {
                    IconButton(
                        onClick = { showGroupManageDialog = true },
                        modifier = Modifier.testTag("group_manage_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "গ্রুপ ম্যানেজ করুন",
                            tint = Color.White
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = WhatsAppTealVal)
        )

        if (showGroupManageDialog) {
            ManageGroupDialog(
                contact = contact,
                viewModel = viewModel,
                onDismiss = { showGroupManageDialog = false }
            )
        }

        // Messages Box
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(10.dp)) }
            items(messageList) { msg ->
                val isMe = msg.senderId == myNumber
                ChatMsgBubble(
                    message = msg,
                    isMe = isMe,
                    isGroupMsg = contact.isGroup,
                    isBengali = appLanguage == "bn",
                    onDeleteForMe = { viewModel.deleteMessageForMe(msg.id) },
                    onDeleteForEveryone = { viewModel.deleteMessageForEveryone(msg.id) },
                    onEditMessage = { msgId, editedTxt -> viewModel.editMessage(msgId, editedTxt) },
                    onImageClick = { selectedImageForPreview = it },
                    onVideoClick = { selectedVideoForPlayback = it },
                    onRegenerate = if (contact.phone == "01300000000") { { viewModel.regenerateLastAIResponse() } } else null
                )
            }
            item { Spacer(modifier = Modifier.height(10.dp)) }
        }

        // Quick shortcuts
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF0F2F5))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("আসসালামু আলাইকুম!", "কেমন আছেন?", "ধন্যবাদ 😊", "গ্রুপ চ্যাট 🟢", "বার্তা").forEach { shortcut ->
                Box(
                    modifier = Modifier
                        .background(Color.White, shape = RoundedCornerShape(12.dp))
                        .clickable {
                            inputText += shortcut + " "
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(text = shortcut, fontSize = 11.sp, color = WhatsAppTealDarkVal)
                }
            }
        }

        // Attachment floating overlay
        if (showAttachOptions) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.LightGray.copy(alpha = 0.9f))
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Camera Attachment action
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable {
                            showAttachOptions = false
                            launchCameraFlow()
                        }
                        .padding(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color(0xFFE3F2FD), shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = Color(0xFF1E88E5), modifier = Modifier.size(26.dp))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("ক্যামেরা", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.SemiBold)
                }

                // Photo Attachment action
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable {
                            showAttachOptions = false
                            showSelectImageDialog = true
                        }
                        .padding(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color(0xFFE8F5E9), shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, tint = WhatsAppGreenVal, modifier = Modifier.size(26.dp))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("ছবি পাঠান", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.SemiBold)
                }

                // Video Attachment action
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable {
                            showAttachOptions = false
                            showSelectVideoDialog = true
                        }
                        .padding(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color(0xFFFFEBEE), shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PlayCircle, contentDescription = null, tint = Color.Red, modifier = Modifier.size(26.dp))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("ভিডিও পাঠান", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Message input row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF0F2F5))
                .padding(8.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Text Input Card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = if (showEmojiPicker) Icons.Default.Keyboard else Icons.Default.SentimentSatisfiedAlt,
                        contentDescription = "Emoji select",
                        tint = if (showEmojiPicker) WhatsAppTealVal else Color.Gray,
                        modifier = Modifier
                            .clickable {
                                focusManager.clearFocus()
                                showEmojiPicker = !showEmojiPicker
                            }
                            .testTag("emoji_picker_toggle_button")
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = { showAttachOptions = !showAttachOptions },
                        modifier = Modifier.size(28.dp).testTag("chat_attach_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "Attach file",
                            tint = Color.Gray
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    TextField(
                        value = inputText,
                        onValueChange = {
                            inputText = it
                            if (showEmojiPicker) {
                                showEmojiPicker = false
                            }
                        },
                        placeholder = { Text("বার্তা লিখুন...", color = Color.Gray) },
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.Black, fontSize = 16.sp),
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedPlaceholderColor = Color.Gray,
                            unfocusedPlaceholderColor = Color.Gray
                        ),
                        singleLine = false,
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Send
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (inputText.isNotBlank()) {
                                    viewModel.sendMessage(inputText)
                                    inputText = ""
                                }
                            }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_message_input")
                    )
                }
            }

            // Send trigger capsule
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(WhatsAppTealVal, shape = CircleShape)
                    .clickable {
                        if (inputText.isNotBlank()) {
                            viewModel.sendMessage(inputText)
                            inputText = ""
                        }
                    }
                    .testTag("send_message_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (showEmojiPicker) {
            EmojiPickerComponent(
                isBn = appLanguage == "bn",
                onEmojiSelected = { emoji ->
                    inputText += emoji
                }
            )
        }
    }

    // Modal dialogue selecting caricature picture for mock Firebase storage upload
    if (showSelectImageDialog) {
        AlertDialog(
            onDismissRequest = { showSelectImageDialog = false },
            title = { Text("মক ছবি পাঠানো (Firebase Storage)", fontWeight = FontWeight.Bold, color = WhatsAppTealVal) },
            text = {
                Column {
                    Text("অনলাইন ফায়ারবেস ড্রাইভে ছবি আপলোড করে পাঠাতে নিচের যেকোনো একটি ছবি স্পর্শ করুন:", fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(14.dp))
                    val dummyPhotos = listOf("pic1", "pic2", "pic3", "pic4", "pic5", "pic6")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        dummyPhotos.take(3).forEach { p ->
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(CircleShape)
                                    .border(1.dp, Color.LightGray, CircleShape)
                                    .clickable {
                                        viewModel.sendMediaMessage(p, "image")
                                        showSelectImageDialog = false
                                        Toast.makeText(viewModel.getApplication(), "ছবি সফলভাবে আপলোড এবং পাঠানো হয়েছে!", Toast.LENGTH_SHORT).show()
                                    }
                            ) {
                                Image(imageVector = getAvatarVector(p), contentDescription = null, modifier = Modifier.fillMaxSize())
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        dummyPhotos.drop(3).forEach { p ->
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(CircleShape)
                                    .border(1.dp, Color.LightGray, CircleShape)
                                    .clickable {
                                        viewModel.sendMediaMessage(p, "image")
                                        showSelectImageDialog = false
                                        Toast.makeText(viewModel.getApplication(), "ছবি সফলভাবে আপলোড এবং পাঠানো হয়েছে!", Toast.LENGTH_SHORT).show()
                                    }
                            ) {
                                Image(imageVector = getAvatarVector(p), contentDescription = null, modifier = Modifier.fillMaxSize())
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                    Button(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreenVal),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("গ্যালারি থেকে ছবি পাঠান", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            showSelectImageDialog = false
                            launchCameraFlow()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ক্যামেরা দিয়ে ছবি তুলুন", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSelectImageDialog = false }) {
                    Text("বন্ধ করুন", color = Color.Red)
                }
            }
        )
    }

    // Modal dialogue selecting simulated video capture clip for mock Firebase storage upload
    if (showSelectVideoDialog) {
        AlertDialog(
            onDismissRequest = { showSelectVideoDialog = false },
            title = { Text("ভিডিও পাঠানো (Gallery / Storage)", fontWeight = FontWeight.Bold, color = WhatsAppTealVal) },
            text = {
                Column {
                    Text("ফায়ারবেস ড্রাইভ অথবা আপনার ফোনের গ্যালারি থেকে যেকোনো ভিডিও নির্বাচন করে পাঠান:", fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(14.dp))
                    listOf("nature_scenery" to "প্রাকৃতিক দৃশ্য ভিডিও 🌲", "funny_cat" to "বিড়ালের মজার খেলা 🐱", "cooking_clip" to "রান্নার দারুণ রেসিপি ক্লিপ 🍳").forEach { (vId, label) ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    viewModel.sendMediaMessage(vId, "video")
                                    showSelectVideoDialog = false
                                    Toast.makeText(viewModel.getApplication(), "ভিডিও সফলভাবে ফায়ারবেস ক্লাউডে আপলোড ও পাঠানো হয়েছে!", Toast.LENGTH_SHORT).show()
                                },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8E9))
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PlayCircle, contentDescription = null, tint = WhatsAppTealVal)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                    Button(
                        onClick = {
                            videoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreenVal),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("গ্যালারি থেকে ভিডিও পাঠান", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSelectVideoDialog = false }) {
                    Text("বন্ধ করুন", color = Color.Red)
                }
            }
        )
    }

    // Immersive fullscreen Image viewer popup
    selectedImageForPreview?.let { imgId ->
        AlertDialog(
            onDismissRequest = { selectedImageForPreview = null },
            title = { Text("ঐতিহাসিক ছবি ভিউয়ার", fontWeight = FontWeight.Bold, color = WhatsAppTealVal, fontSize = 16.sp) },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .background(Color.White, shape = RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        imageVector = getAvatarVector(imgId),
                        contentDescription = "Zoomed preview",
                        modifier = Modifier.fillMaxSize(0.9f)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { selectedImageForPreview = null },
                    colors = ButtonDefaults.buttonColors(containerColor = WhatsAppTealVal)
                ) {
                    Text("বন্ধ করুন", color = Color.White)
                }
            }
        )
    }

    // Immersive high fidelity mock video Player with dynamic playback slider simulation
    selectedVideoForPlayback?.let { videoId ->
        var isPlaying by remember { mutableStateOf(true) }
        var playSecs by remember { mutableStateOf(1) }

        // Simulated clock progress ticker for the player
        LaunchedEffect(isPlaying) {
            while (isPlaying && playSecs < 38) {
                delay(1000)
                playSecs += 1
            }
        }

        AlertDialog(
            onDismissRequest = { selectedVideoForPlayback = null },
            title = { Text("ভিডিও প্লেয়ার 🎥", fontWeight = FontWeight.Bold, color = WhatsAppTealVal, fontSize = 16.sp) },
            text = {
                Column {
                    if (videoId.startsWith("/") || videoId.startsWith("content://") || videoId.startsWith("file://")) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .background(Color.Black, shape = RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.ui.viewinterop.AndroidView(
                                factory = { ctx ->
                                    android.widget.VideoView(ctx).apply {
                                        val mediaController = android.widget.MediaController(ctx)
                                        mediaController.setAnchorView(this)
                                        setMediaController(mediaController)
                                        setVideoPath(videoId)
                                        start()
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .background(Color.Black, shape = RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clickable { isPlaying = !isPlaying }
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (isPlaying) "প্লে হচ্ছে: ${videoId.replace("_", " ")}" else "থেমে আছে",
                                    fontSize = 12.sp,
                                    color = Color.LightGray
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Player Progress Track
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("0:${playSecs.toString().padStart(2, '0')}", fontSize = 11.sp, color = Color.Black)
                            Slider(
                                value = playSecs.toFloat(),
                                onValueChange = { playSecs = it.toInt() },
                                valueRange = 0f..38f,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = WhatsAppGreenVal,
                                    activeTrackColor = WhatsAppGreenVal
                                )
                            )
                            Text("0:38", fontSize = 11.sp, color = Color.Black)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { selectedVideoForPlayback = null },
                    colors = ButtonDefaults.buttonColors(containerColor = WhatsAppTealVal)
                ) {
                    Text("প্লেয়ার বন্ধ করুন्न", color = Color.White)
                }
            }
        )
    }
}

@Composable
fun ChatMsgBubble(
    message: Message,
    isMe: Boolean,
    isGroupMsg: Boolean,
    isBengali: Boolean,
    onDeleteForMe: () -> Unit,
    onDeleteForEveryone: () -> Unit,
    onEditMessage: (String, String) -> Unit,
    onImageClick: (String) -> Unit,
    onVideoClick: (String) -> Unit,
    onRegenerate: (() -> Unit)? = null
) {
    val layoutAlign = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val bubbleColor = if (isMe) {
        if (isDark) Color(0xFF0F766E) else WhatsAppLightGreenVal
    } else {
        if (isDark) Color(0xFF1E293B) else Color.White
    }
    val textColor = if (isMe) {
        if (isDark) Color.White else Color.Black
    } else {
        if (isDark) Color.White else Color.Black
    }
    val subtextColor = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Gray

    val bubbleShape = if (isMe) {
        RoundedCornerShape(12.dp, 12.dp, 0.dp, 12.dp)
    } else {
        RoundedCornerShape(12.dp, 12.dp, 12.dp, 0.dp)
    }

    var showDeleteConfirmationDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editedTextState by remember { mutableStateOf(message.text) }

    // Keep edited text state updated if message text changes from Firestore
    LaunchedEffect(message.text) {
        editedTextState = message.text
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = layoutAlign
    ) {
        Card(
            shape = bubbleShape,
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clickable {
                    // Open delete Dialog on click of safety
                    showDeleteConfirmationDialog = true
                }
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // Sender name tag (Group chats)
                if (isGroupMsg && !isMe && message.senderName.isNotEmpty()) {
                    Text(
                        text = message.senderName,
                        fontWeight = FontWeight.Bold,
                        color = WhatsAppTealVal,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                // Inner content rendering branch checking Deletion/Images/Video/Text
                if (message.isDeletedForEveryone) {
                    Text(
                        text = "🚫 এই বার্তাটি মুছে ফেলা হয়েছে",
                        fontSize = 14.sp,
                        color = Color.LightGray,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    // Render image if present
                    if (message.mediaType == "image" && message.mediaUrl != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .clickable { onImageClick(message.mediaUrl) }
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (message.mediaUrl.startsWith("/") || message.mediaUrl.startsWith("content://") || message.mediaUrl.startsWith("file://")) {
                                AsyncImage(
                                    model = message.mediaUrl,
                                    contentDescription = "Shared photo",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Image(
                                    imageVector = getAvatarVector(message.mediaUrl),
                                    contentDescription = "Shared pic",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // Render video mock if present
                    if (message.mediaType == "video" && message.mediaUrl != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF263238))
                                .clickable { onVideoClick(message.mediaUrl) },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.PlayCircle,
                                    contentDescription = "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(42.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (message.mediaUrl.startsWith("/") || message.mediaUrl.startsWith("content://") || message.mediaUrl.startsWith("file://")) "গ্যালারি ভিডিও 🎥" else "ভিডিও ক্লিপ (${message.mediaUrl.replace("_", " ")})",
                                    fontSize = 11.sp,
                                    color = Color.LightGray
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // Standard text display
                    Column {
                        Text(
                            text = message.text,
                            fontSize = 15.sp,
                            color = textColor
                        )
                        if (message.isEdited) {
                            Text(
                                text = if (isBengali) "সংশোধিত" else "edited",
                                fontSize = 10.sp,
                                color = subtextColor,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                modifier = Modifier.padding(top = 1.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(2.dp))
                
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    var timeTrigger by remember { mutableStateOf(0L) }
                    LaunchedEffect(message.timestamp) {
                        while (true) {
                            delay(15000)
                            timeTrigger = System.currentTimeMillis()
                        }
                    }
                    val relativeTime = remember(message.timestamp, isBengali, timeTrigger) {
                        formatRelativeTime(message.timestamp, isBengali)
                    }
                    Text(
                        text = "$relativeTime • ${formatTime(message.timestamp)}",
                        fontSize = 10.sp,
                        color = subtextColor
                    )
                    if (isMe) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.DoneAll,
                            contentDescription = "Read status",
                            tint = Color(0xFF34B7F1),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }

    // Interactive message deletion selection sheet dialog
    val txt = { bn: String, en: String -> if (isBengali) bn else en }

    if (showDeleteConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmationDialog = false },
            title = { Text(txt("বার্তা সংশোধন বা মুছুন", "Modify or Delete Message"), fontWeight = FontWeight.Bold, color = WhatsAppTealVal) },
            text = { Text(txt("বার্তাটির নিরাপত্তা নিশ্চিতকরণঃ দয়া করে আপনার কাঙ্ক্ষিত অপশনটি নির্বাচন করুন।", "Message security verification: Please select your desired option."), fontSize = 12.sp, color = Color.Gray) },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Option 1: Delete For Me (Sender + Receiver both have permission)
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            onDeleteForMe()
                            showDeleteConfirmationDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WhatsAppTealVal)
                    ) {
                        Text(txt("আমার থেকে মুছুন", "Delete for Me"), color = Color.White)
                    }

                    // Option 2: Delete For Everyone (Only sender has full permission constraints)
                    if (isMe && !message.isDeletedForEveryone) {
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                onDeleteForEveryone()
                                showDeleteConfirmationDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) {
                            Text(txt("সবার জন্য মুছুন", "Delete for Everyone"), color = Color.White)
                        }
                    }

                    // Option 3: Edit Message (Only sender has edit constraints, only for text messages)
                    if (isMe && !message.isDeletedForEveryone && message.mediaType == null) {
                        Button(
                            modifier = Modifier.fillMaxWidth().testTag("edit_message_button"),
                            onClick = {
                                showDeleteConfirmationDialog = false
                                showEditDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5))
                        ) {
                            Text(txt("বার্তা সংশোধন করুন", "Edit Message"), color = Color.White)
                        }
                    }

                    // Option 4: Copy Message Text (For everyone, for text messages)
                    if (message.mediaType == null && !message.isDeletedForEveryone) {
                        val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                        val toastContext = androidx.compose.ui.platform.LocalContext.current
                        Button(
                            modifier = Modifier.fillMaxWidth().testTag("copy_message_button"),
                            onClick = {
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(message.text))
                                showDeleteConfirmationDialog = false
                                Toast.makeText(toastContext, txt("বার্তা কপি করা হয়েছে!", "Message copied successfully!"), Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4B5563))
                        ) {
                            Text(txt("বার্তা কপি করুন", "Copy Message Text"), color = Color.White)
                        }
                    }

                    // Option 5: Regenerate AI Response (Only for non-deleted chatbot assistant responses)
                    if (onRegenerate != null && !isMe && !message.isDeletedForEveryone) {
                        Button(
                            modifier = Modifier.fillMaxWidth().testTag("regenerate_response_button"),
                            onClick = {
                                onRegenerate()
                                showDeleteConfirmationDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = WhatsAppTealVal)
                        ) {
                            Text(txt("এআই উত্তর পুনরায় তৈরি করুন", "Regenerate Response"), color = Color.White)
                        }
                    }

                    // Cancel option
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth().testTag("cancel_message_options_button"),
                        onClick = { showDeleteConfirmationDialog = false }
                    ) {
                        Text(txt("বাতিল করুন", "Cancel"))
                    }
                }
            }
        )
    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text(txt("বার্তা সংশোধন করুন", "Edit Message"), fontWeight = FontWeight.Bold, color = WhatsAppTealVal) },
            text = {
                Column {
                    Text(txt("আপনার বার্তাটি সংশোধন করে নিচের বক্সে লিখুন:", "Modify your message and write in the box below:"), fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
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
                        if (editedTextState.trim().isNotEmpty()) {
                            onEditMessage(message.id, editedTextState)
                        }
                        showEditDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WhatsAppTealVal)
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
}

@Composable
fun AvatarView(
    name: String,
    base64: String,
    size: Int = 44
) {
    if (base64.isNotEmpty()) {
        Box(
            modifier = Modifier
                .size(size.dp)
                .clip(CircleShape)
                .background(Color.LightGray)
        ) {
            if (base64.startsWith("/") || base64.startsWith("content:") || base64.startsWith("file:")) {
                AsyncImage(
                    model = base64,
                    contentDescription = name,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Image(
                    imageVector = getAvatarVector(base64),
                    contentDescription = name,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    } else {
        val letter = if (name.isNotBlank()) name.first().toString() else "U"
        val backgroundColors = listOf(
            Color(0xFF3F51B5), Color(0xFFE91E63), Color(0xFF009688),
            Color(0xFF4CAF50), Color(0xFFFF9800), Color(0xFF9C27B0)
        )
        val colorIndex = Math.abs(name.hashCode()) % backgroundColors.size
        val randomBg = backgroundColors[colorIndex]

        Box(
            modifier = Modifier
                .size(size.dp)
                .background(randomBg, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = letter,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size / 2.4).sp
            )
        }
    }
}

// Helper vectors to simulate local high-fidelity uploads
@Composable
fun getAvatarVector(key: String): androidx.compose.ui.graphics.vector.ImageVector {
    return when(key) {
        "pic1" -> Icons.Default.Face
        "pic2" -> Icons.Default.SentimentSatisfied
        "pic3" -> Icons.Default.AccountCircle
        "pic4" -> Icons.Default.CoPresent
        "pic5" -> Icons.Default.SupervisedUserCircle
        "pic6" -> Icons.Default.EmojiPeople
        else -> Icons.Default.AccountCircle
    }
}

fun formatTime(timestamp: Long): String {
    val date = java.util.Date(timestamp)
    val sdf = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
    return sdf.format(date)
}

fun formatRelativeTime(timestamp: Long, isBengali: Boolean): String {
    val diff = System.currentTimeMillis() - timestamp
    return if (isBengali) {
        when {
            diff < 0 -> "এইমাত্র"
            diff < 60000 -> "এইমাত্র"
            diff < 3600000 -> "${toBengaliDigits((diff / 60000).toString())}মি আগে"
            diff < 86400000 -> "${toBengaliDigits((diff / 3600000).toString())}ঘণ্টা আগে"
            else -> {
                val days = diff / 86400000
                if (days < 7) {
                    "${toBengaliDigits(days.toString())}দিন আগে"
                } else {
                    val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                    toBengaliDigits(sdf.format(java.util.Date(timestamp)))
                }
            }
        }
    } else {
        when {
            diff < 0 -> "Just now"
            diff < 60000 -> "Just now"
            diff < 3600000 -> "${diff / 60000}m ago"
            diff < 86400000 -> "${diff / 3600000}h ago"
            else -> {
                val days = diff / 86400000
                if (days < 7) {
                    "${days}d ago"
                } else {
                    val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                    sdf.format(java.util.Date(timestamp))
                }
            }
        }
    }
}

@Composable
fun ManageGroupDialog(
    contact: Contact,
    viewModel: ChatViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val allContacts by viewModel.contacts.collectAsStateWithLifecycle()
    val nonGroupContacts = allContacts.filter { !it.isGroup }
    
    val myNumberState by viewModel.myNumber.collectAsStateWithLifecycle()
    val myNumber = myNumberState ?: ""
    var groupName by remember { mutableStateOf(contact.name) }
    
    val selectedParticipantPhones = remember {
        mutableStateListOf<String>().apply {
            addAll(contact.groupParticipants.split(",").filter { it.isNotEmpty() })
        }
    }
    
    val groupCreator = remember(contact.phone, contact.groupParticipants) {
        context.getSharedPreferences("BartaChatPrefs", Context.MODE_PRIVATE)
            .getString("group_creator_${contact.phone}", "")?.ifEmpty {
                contact.groupParticipants.split(",").firstOrNull() ?: ""
            } ?: (contact.groupParticipants.split(",").firstOrNull() ?: "")
    }
    val isGroupAdmin = groupCreator.isEmpty() || groupCreator == myNumber
    
    var hasError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isGroupAdmin) "গ্রুপ সেটিংস ও সদস্য পরিবর্তন" else "গ্রুপের বিবরণ ও সদস্যবৃন্দ", 
                fontWeight = FontWeight.Bold, 
                color = WhatsAppTealVal
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = {
                        if (isGroupAdmin) {
                            groupName = it
                            hasError = false
                        }
                    },
                    label = { Text("গ্রুপের নাম") },
                    singleLine = true,
                    enabled = isGroupAdmin,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = WhatsAppTealVal,
                        unfocusedLabelColor = Color.Gray,
                        disabledTextColor = Color.White,
                        disabledLabelColor = Color.LightGray
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (hasError) {
                    Text("গ্রুপের নাম অবশ্যই দিতে হবে!", color = Color.Red, fontSize = 11.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "সদস্যদের তালিকা (${selectedParticipantPhones.size} জন):",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(modifier = Modifier.height(200.dp)) {
                    items(nonGroupContacts) { c ->
                        val isChecked = selectedParticipantPhones.contains(c.phone)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = isGroupAdmin) {
                                    if (isChecked) {
                                        if (c.phone != myNumber) {
                                            selectedParticipantPhones.remove(c.phone)
                                        }
                                    } else {
                                        selectedParticipantPhones.add(c.phone)
                                    }
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    if (isGroupAdmin) {
                                        if (checked == true) {
                                            selectedParticipantPhones.add(c.phone)
                                        } else {
                                            if (c.phone != myNumber) {
                                                selectedParticipantPhones.remove(c.phone)
                                            }
                                        }
                                    }
                                },
                                enabled = isGroupAdmin && c.phone != myNumber
                            )
                            AvatarView(name = c.name, base64 = c.profilePicUri, size = 32)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(c.name, color = Color.White, fontSize = 14.sp)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val isUserMe = c.phone == myNumber
                                    val isUserCreator = c.phone == groupCreator
                                    if (isUserMe && isUserCreator) {
                                        Text("আপনি (গ্রুপ এডমিন 👑)", color = WhatsAppGreenVal, fontSize = 11.sp)
                                    } else if (isUserCreator) {
                                        Text("গ্রুপ এডমিন 👑", color = WhatsAppTealVal, fontSize = 11.sp)
                                    } else if (isUserMe) {
                                        Text("আপনি", color = Color.Gray, fontSize = 11.sp)
                                    } else {
                                        Text(c.phone, color = Color.Gray, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (isGroupAdmin) {
                Button(
                    onClick = {
                        if (groupName.trim().isEmpty()) {
                            hasError = true
                        } else {
                            viewModel.updateGroup(contact.phone, groupName, selectedParticipantPhones.toList())
                            onDismiss()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreenVal)
                ) {
                    Text("পরিবর্তন সেভ করুন", color = Color.White)
                }
            } else {
                Text(
                    text = "⚠️ আপনি এই গ্রুপের এডমিন নন (Read-only)",
                    color = Color(0xFFFFB300),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("বন্ধ করুন", color = Color.Gray)
            }
        }
    )
}

fun copyUriToLocalFile(context: Context, uri: android.net.Uri): String? {
    return try {
        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(uri)
        val extension = when {
            mimeType == null -> "jpg"
            mimeType.contains("image") -> "jpg"
            mimeType.contains("video") -> "mp4"
            else -> mimeType.substringAfterLast("/")
        }
        val fileName = "picked_media_${System.currentTimeMillis()}.$extension"
        val tempFile = java.io.File(context.cacheDir, fileName)
        
        contentResolver.openInputStream(uri)?.use { inputStream ->
            java.io.FileOutputStream(tempFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        tempFile.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun formatLastSeenDynamic(lastSeen: String, lang: String): String {
    if (lang == "en") {
        if (lastSeen == "online") return "Online"
        if (lastSeen == "offline") return "Inactive"
        val timestamp = lastSeen.toLongOrNull() ?: return lastSeen
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        
        if (diff < 60000) {
            return "Active just now"
        } else if (diff < 3600000) {
            val mins = diff / 60000
            return "Active $mins mins ago"
        }
        
        val sdf = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.US)
        val timeStr = sdf.format(java.util.Date(timestamp))
        val daySdf = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.US)
        val mDateStr = daySdf.format(java.util.Date(timestamp))
        val todayStr = daySdf.format(java.util.Date(now))
        val yesterdayStr = daySdf.format(java.util.Date(now - 24 * 60 * 60 * 1000))
        
        return when (mDateStr) {
            todayStr -> "Today at $timeStr"
            yesterdayStr -> "Yesterday at $timeStr"
            else -> "Active on $mDateStr"
        }
    } else {
        if (lastSeen == "online") return "অনলাইন"
        if (lastSeen == "offline") return "নিষ্ক্রিয়"
        
        val timestamp = lastSeen.toLongOrNull() ?: return lastSeen
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        
        if (diff < 60000) {
            return "এইমাত্র সক্রিয়"
        } else if (diff < 3600000) {
            val mins = diff / 60000
            val minsBn = toBengaliDigits(mins.toString())
            return "$minsBn মিনিট আগে সক্রিয়"
        }
        
        val sdf = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.US)
        val timeStr = sdf.format(java.util.Date(timestamp))
        
        var formattedTime = timeStr
            .replace("AM", "পূর্বাহ্ণ")
            .replace("PM", "অপরাহ্ণ")
        
        formattedTime = toBengaliDigits(formattedTime)
        
        val daySdf = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.US)
        val mDateStr = daySdf.format(java.util.Date(timestamp))
        val todayStr = daySdf.format(java.util.Date(now))
        val yesterdayStr = daySdf.format(java.util.Date(now - 24 * 60 * 60 * 1000))
        
        return when (mDateStr) {
            todayStr -> "আজ $formattedTime এ সক্রিয়"
            yesterdayStr -> "গতকাল $formattedTime এ সক্রিয়"
            else -> "${toBengaliDigits(mDateStr)} তারিখে সক্রিয়"
        }
    }
}

fun toBengaliDigits(english: String): String {
    val englishDigits = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
    val bengaliDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
    var result = english
    for (i in 0..9) {
        result = result.replace(englishDigits[i], bengaliDigits[i])
    }
    return result
}

data class EmojiCategory(
    val icon: String,
    val nameBn: String,
    val nameEn: String,
    val emojis: List<String>
)

@Composable
fun EmojiPickerComponent(
    isBn: Boolean,
    onEmojiSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategoryIndex by remember { mutableStateOf(0) }
    
    val categories = remember {
        listOf(
            EmojiCategory("😃", "হাসি ও অনুভূতি", "Smileys & Emotions", listOf("😊", "😂", "😎", "😍", "🥰", "😘", "😜", "🤔", "😴", "😭", "😡", "😱", "🤯", "🥳", "🙄", "🤫", "😋", "😜", "😏", "😷", "🤠", "🤢")),
            EmojiCategory("👍", "হাত ও সংকেত", "Gestures & Signs", listOf("👍", "👎", "👌", "✌️", "🤝", "👏", "🙌", "🙏", "❤️", "💔", "🔥", "✨", "🎉", "🌟", "💡", "💯", "🦾", "💪", "👊", "✊", "🖐️", "✍️")),
            EmojiCategory("🎈", "বিনোদন ও খাদ্য", "Activities & Food", listOf("⚽", "🏀", "🎮", "☕", "🍕", "🍔", "🍓", "🍎", "🍺", "🥂", "🛸", "🚀", "🚗", "✈️", "🎒", "🎓", "🍕", "🍰", "🍩", "🍪", "🍷", "🥤")),
            EmojiCategory("🐱", "প্রাণী ও প্রকৃতি", "Animals & Nature", listOf("🐱", "🐶", "🐻", "🦁", "🐼", "🐒", "🐔", "🐙", "🐠", "🌸", "🌹", "🍀", "🍁", "🌍", "🌞", "🌙", "🌻", "🌴", "🌲", "🌈", "🔥", "❄️"))
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F2F5)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Category Selection Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFE3E6EB))
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                categories.forEachIndexed { index, category ->
                    val isSelected = selectedCategoryIndex == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedCategoryIndex = index }
                            .padding(vertical = 8.dp)
                            .background(
                                color = if (isSelected) Color.White else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = category.icon,
                            fontSize = 20.sp
                        )
                    }
                }
            }

            // Category Title Label
            Text(
                text = if (isBn) categories[selectedCategoryIndex].nameBn else categories[selectedCategoryIndex].nameEn,
                fontSize = 12.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
            )

            // Emoji Grid
            val currentCategory = categories[selectedCategoryIndex]
            val chunkedEmojis = remember(selectedCategoryIndex) { currentCategory.emojis.chunked(6) }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(chunkedEmojis) { rowEmojis ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        rowEmojis.forEach { emoji ->
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clickable { onEmojiSelected(emoji) }
                                    .testTag("emoji_picker_item_$emoji"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = emoji,
                                    fontSize = 26.sp
                                )
                            }
                        }
                        // Pad empty cells if the last row is incomplete
                        if (rowEmojis.size < 6) {
                            repeat(6 - rowEmojis.size) {
                                Spacer(modifier = Modifier.size(44.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PillItem(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    isDark: Boolean,
    showDotBadge: Boolean = false,
    onClick: () -> Unit
) {
    val containerColor = if (isSelected) {
        Color(0xFF26B29E)
    } else {
        if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)
    }
    val contentColor = if (isSelected) {
        Color.White
    } else {
        if (isDark) Color.LightGray else Color(0xFF475569)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = text,
                    color = contentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (showDotBadge) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(Color(0xFF10B981), CircleShape)
                            .align(Alignment.TopEnd)
                            .offset(x = 6.dp, y = (-4).dp)
                    )
                }
            }
        }
    }
}
