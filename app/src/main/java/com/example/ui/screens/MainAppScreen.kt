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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Contact
import com.example.data.LocalUser
import com.example.data.Message
import com.example.viewmodel.ChatViewModel
import com.example.ui.theme.*
import androidx.activity.result.PickVisualMediaRequest
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MainAppScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val isConnected by rememberConnectivityState()
    val loggedInNumber by viewModel.myNumber.collectAsStateWithLifecycle()
    val activeChatContact by viewModel.activeContact.collectAsStateWithLifecycle()
    val showOnboarding by viewModel.showOnboarding.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (!isConnected) {
            OfflineBlockerScreen()
        } else if (loggedInNumber == null) {
            AuthScreen(viewModel = viewModel)
        } else if (showOnboarding) {
            GreetingOnboardingScreen(
                viewModel = viewModel,
                onFinished = {
                    viewModel.showOnboarding.value = false
                }
            )
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
                    contentDescription = "Retry Connection Icon",
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
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(WhatsAppTealVal, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Chat,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "বার্তা (Barta)",
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
                        0 -> OnboardingWelcomePage()
                        1 -> OnboardingFeaturesPage()
                        2 -> OnboardingHighlightsPage()
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
                                text = "পূর্ববর্তী",
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
                            "চ্যাট শুরু করুন 🚀"
                        } else {
                            "পরবর্তী"
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
fun OnboardingWelcomePage() {
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
            text = "বার্তা (Barta) চ্যাটে স্বাগতম! 🎉",
            fontWeight = FontWeight.Bold,
            fontSize = 23.sp,
            color = Color(0xFF1F2937),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "সহজ, নিরাপদ ও বিজ্ঞাপনহীন যোগাযোগের একমাত্র নির্ভরযোগ্য চ্যাট অ্যাপ্লিকেশন। কোনো বাড়তি জটিলতা বা ঝামেলা ছাড়াই আপনার বন্ধুদের সাথে সর্বদা সংযুক্ত থাকুন।",
            fontSize = 15.sp,
            color = Color(0xFF4B5563),
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
    }
}

@Composable
fun OnboardingFeaturesPage() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
    ) {
        Text(
            text = "আমাদের চমৎকার সুবিধাসমূহ ✨",
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
                title = "রিয়েল-টাইম চ্যাট ও গ্রুপ",
                description = "অতি দ্রুত চ্যাট মেসেজ এবং ক্লাস বা প্রজেক্টের আলোচনার জন্য তাৎক্ষণিক গ্রুপ খোলার চমৎকার সুবিধা।"
            )

            OnboardingFeatureCard(
                icon = "📷",
                title = "ছবি ও ২৪ ঘণ্টার চমৎকার স্ট্যাটাস",
                description = "মনের গোপন কথা লিখে স্টোরি ও ছবি বা পছন্দের রঙিন ব্যাকগ্রাউন্ড ডিজাইনে স্ট্যাটাস শেয়ার করুন।"
            )

            OnboardingFeatureCard(
                icon = "🪄",
                title = "মেসেজ এডিট ও ডিলিট ক্ষমতা",
                description = "মেসেজ ভুল হলে তা সরাসরি সংশোধনের সুযোগ অথবা সবার জন্য সম্পূর্ণ মুছে ফেলার (Delete for Everyone) অফুরন্ত স্বাধীনতা।"
            )
        }
    }
}

@Composable
fun OnboardingHighlightsPage() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
    ) {
        Text(
            text = "স্মার্ট প্রযুক্তি ও সম্পূর্ণ নিয়ন্ত্রণ 🛡️",
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
                title = "স্মার্ট বার্তা সহকারী (AI Bot)",
                description = "যেকোনো কঠিন প্রশ্নের উত্তর জানতে বা প্রজেক্ট সাজাতে সাহায্য বা সাধারণ চ্যাট করতে সর্বদা প্রস্তুত আপনার এআই বন্ধু!"
            )

            OnboardingFeatureCard(
                icon = "🟢",
                title = "অনলাইন স্ট্যাটাস ও টাইপ অনুভূতি",
                description = "বন্ধুরা কখন লাইনে আছে বা কি লিখছে তা সরাসরি দেখার চমৎকার অনুভূতি।"
            )

            OnboardingFeatureCard(
                icon = "🔒",
                title = "১০০% বিজ্ঞাপনহীন ও সর্বোচ্চ নিরাপত্তা",
                description = "কোনো বিরক্তিকর বিজ্ঞাপন ছাড়াই চ্যাটিংয়ের পূর্ণ স্বাচ্ছন্দ্যতা ও ব্যবহারকারীর ডেটার সুরক্ষিত নিরাপত্তা।"
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
            Toast.makeText(context, "অনুমতি পাওয়া গেছে! এখন ছবি সিলেক্ট করুন।", Toast.LENGTH_SHORT).show()
            showPhotoSelector = true
        } else {
            Toast.makeText(context, "ছবি আপলোড করতে স্টোরেজ/ক্যামেরা পারমিশন প্রয়োজন!", Toast.LENGTH_LONG).show()
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
                    Toast.makeText(context, "গ্যালারি থেকে ছবি সফলভাবে যুক্ত হয়েছে!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "ছবি লোড করতে সমস্যা হয়েছে!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .statusBarsPadding()
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(90.dp)
                .background(Color(0xFFE8F5E9), shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Chat,
                contentDescription = "Logo",
                tint = WhatsAppGreenVal,
                modifier = Modifier.size(54.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "বার্তা (Chat)",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = WhatsAppTealVal
        )

        Text(
            text = "বাংলাদেশের প্রথম সহজ নিরাপদ চ্যাট প্ল্যাটফর্ম",
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
                            text = "লগইন",
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
                            text = "একাউন্ট তৈরি",
                            fontWeight = FontWeight.Bold,
                            color = if (isSignUp) WhatsAppTealVal else Color.Gray,
                            fontSize = 14.sp
                        )
                    }
                }

                Text(
                    text = if (isSignUp) "নতুন একাউন্ট তৈরি করুন" else "লগইন করুন",
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
                            Image(
                                imageVector = getAvatarVector(profilePicBase64),
                                contentDescription = "Profile Picture",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.AddAPhoto, contentDescription = "Upload Picture", tint = Color.Gray, modifier = Modifier.size(24.dp))
                                Text("ছবি দিন", fontSize = 10.sp, color = Color.Gray)
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
                        label = { Text("আপনার নাম (আবশ্যক)") },
                        placeholder = { Text("উদা: রাইসা আলম") },
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
                    label = { Text("মোবাইল নাম্বার") },
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
                    label = { Text("পাসওয়ার্ড (৬-৮ ক্যারেক্টার)") },
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
                                    Toast.makeText(context, "সফলভাবে একাউন্ট তৈরি এবং লগইন হয়েছে!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            viewModel.loginWithPassword(phoneNumber, passwordInput) { error ->
                                if (error != null) {
                                    errorMessage = error
                                } else {
                                    Toast.makeText(context, "লগইন সফল হয়েছে!", Toast.LENGTH_SHORT).show()
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
                        text = if (isSignUp) "একাউন্ট তৈরি করুন" else "লগইন করুন",
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
            title = { Text("প্রোফাইল ছবি নির্বাচন করুন", fontWeight = FontWeight.Bold, color = WhatsAppTealVal) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("নিচের যেকোনো একটি সুন্দর কার্টুন ছবি স্পর্শ করে সিলেক্ট করুন:", fontSize = 12.sp, color = Color.Gray)
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
                    Button(
                        onClick = {
                            signUpPhotoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WhatsAppTealVal),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("গ্যালারি থেকে ছবি নিন", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showPhotoSelector = false },
                    colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreenVal)
                ) {
                    Text("অনুমোদন দিন", color = Color.White)
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

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        NavigationBarItem(
            selected = currentTab == "chats",
            onClick = { viewModel.selectTab("chats") },
            icon = { Icon(Icons.AutoMirrored.Filled.Message, contentDescription = "Chats") },
            label = { Text("চ্যাট", fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = WhatsAppTealVal,
                selectedTextColor = WhatsAppTealVal,
                indicatorColor = Color(0xFFE8F5E9)
            ),
            modifier = Modifier.testTag("tab_chats")
        )
        NavigationBarItem(
            selected = currentTab == "groups",
            onClick = { viewModel.selectTab("groups") },
            icon = { Icon(Icons.Default.Groups, contentDescription = "Groups") },
            label = { Text("গ্রুপ", fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = WhatsAppTealVal,
                selectedTextColor = WhatsAppTealVal,
                indicatorColor = Color(0xFFE8F5E9)
            ),
            modifier = Modifier.testTag("tab_groups")
        )
        NavigationBarItem(
            selected = currentTab == "status",
            onClick = { viewModel.selectTab("status") },
            icon = { Icon(Icons.Default.CircleNotifications, contentDescription = "Status") },
            label = { Text("স্ট্যাটাস", fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = WhatsAppTealVal,
                selectedTextColor = WhatsAppTealVal,
                indicatorColor = Color(0xFFE8F5E9)
            ),
            modifier = Modifier.testTag("tab_status")
        )
        NavigationBarItem(
            selected = currentTab == "contacts",
            onClick = { viewModel.selectTab("contacts") },
            icon = { Icon(Icons.Default.People, contentDescription = "Contacts") },
            label = { Text("পরিচিত", fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = WhatsAppTealVal,
                selectedTextColor = WhatsAppTealVal,
                indicatorColor = Color(0xFFE8F5E9)
            ),
            modifier = Modifier.testTag("tab_contacts")
        )
        NavigationBarItem(
            selected = currentTab == "profile",
            onClick = { viewModel.selectTab("profile") },
            icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
            label = { Text("প্রোফাইল", fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = WhatsAppTealVal,
                selectedTextColor = WhatsAppTealVal,
                indicatorColor = Color(0xFFE8F5E9)
            ),
            modifier = Modifier.testTag("tab_profile")
        )
        NavigationBarItem(
            selected = currentTab == "settings",
            onClick = { viewModel.selectTab("settings") },
            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
            label = { Text("সেটিংস", fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = WhatsAppTealVal,
                selectedTextColor = WhatsAppTealVal,
                indicatorColor = Color(0xFFE8F5E9)
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

    // Filter out group chats for individual conversations
    val individualChats = remember(contactsList) { contactsList.filter { !it.isGroup } }

    // Query on global registered users matching the name
    val searchedGlobalUsers by viewModel.searchedGlobalUsers.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // WhatsApp themed Header
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = "বার্তা (Chat)",
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
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = WhatsAppTealVal)
        )

        // Custom search box matching WhatsApp layout
        OutlinedTextField(
            value = searchVal,
            onValueChange = { viewModel.searchQuery.value = it },
            placeholder = { Text("নাম অথবা মোবাইল দিয়ে সার্চ করুন...", color = Color.Gray) },
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
            // Global search result - start chatting directly
            if (searchVal.isNotEmpty() && searchedGlobalUsers.isNotEmpty()) {
                item {
                    Text(
                        text = "সার্চ করা অন্যান্য ব্যবহারকারীরা (অনলাইন):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = WhatsAppTealVal,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
                    )
                }

                items(searchedGlobalUsers) { user ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // Add user to contacts instantly and chat
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
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AvatarView(name = user.name, base64 = user.profilePicBase64, size = 42)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(user.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                            Text(user.phone, fontSize = 12.sp, color = Color.LightGray)
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Text("মেসেজ দিন ➡️", color = WhatsAppGreenVal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider(color = Color(0xFF202C33))
                }
                
                item { Spacer(modifier = Modifier.height(12.dp)) }
            }

            // Existing Chats Header
            if (individualChats.isNotEmpty()) {
                item {
                    Text(
                        text = "আমার চ্যাটসমূহ (Active Chats):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray,
                        modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
                    )
                }

                items(individualChats) { contact ->
                    ChatRowItem(
                        contact = contact,
                        onClick = { onChatClick(contact) }
                    )
                    HorizontalDivider(
                        color = Color(0xFFF5F5F5),
                        thickness = 1.dp,
                        modifier = Modifier.padding(start = 76.dp)
                    )
                }
            } else if (searchedGlobalUsers.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.ChatBubbleOutline,
                                contentDescription = "Empty",
                                modifier = Modifier.size(56.dp),
                                tint = Color.LightGray
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "কোনো চ্যাট পাওয়া যায়নি",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsTabScreen(
    viewModel: ChatViewModel,
    onChatClick: (Contact) -> Unit
) {
    val contactsList by viewModel.contacts.collectAsStateWithLifecycle()
    val searchVal by viewModel.searchQuery.collectAsStateWithLifecycle()
    val myPhone by viewModel.myNumber.collectAsStateWithLifecycle()

    val groupChats = remember(contactsList) { contactsList.filter { it.isGroup } }

    var showCreateGroupDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateGroupDialog = true },
                containerColor = WhatsAppTealVal,
                contentColor = Color.White,
                icon = { Icon(Icons.Default.Groups, contentDescription = "Create Group") },
                text = { Text("নতুন গ্রুপ") },
                modifier = Modifier.testTag("create_group_fab")
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Group header with dynamic subtitle
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "গ্রুপ চ্যাট (Groups)",
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
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = WhatsAppTealVal)
            )

            // Custom search box matching WhatsApp layout
            OutlinedTextField(
                value = searchVal,
                onValueChange = { viewModel.searchQuery.value = it },
                placeholder = { Text("গ্রুপ নাম দিয়ে সার্চ করুন...", color = Color.Gray) },
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
                            text = "আমার গ্রুপসমূহ (My Groups):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            modifier = Modifier.padding(start = 16.dp, bottom = 4.dp, top = 8.dp)
                        )
                    }

                    items(groupChats) { contact ->
                        ChatRowItem(
                            contact = contact,
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
                                    text = "কোনো গ্রুপ চ্যাট পাওয়া যায়নি",
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
    val activeStatuses by viewModel.activeStatuses.collectAsStateWithLifecycle()
    val myPhoneState by viewModel.myNumber.collectAsStateWithLifecycle()
    val myPhone = myPhoneState ?: ""
    val myName = viewModel.userDisplayName.value
    val myProfilePic = viewModel.userProfilePicBase64.value
    val context = LocalContext.current

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
                    Toast.makeText(context, "ছবি লোড করতে সমস্যা হয়েছে!", Toast.LENGTH_SHORT).show()
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
            diff < 0 -> "এইমাত্র"
            diff < 60000 -> "এইমাত্র"
            diff < 3600000 -> "${diff / 60000} মিনিট আগে"
            diff < 86400000 -> "${diff / 3600000} ঘণ্টা আগে"
            else -> "১ দিন আগে"
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
                    containerColor = Color(0xFFF0F2F5),
                    contentColor = WhatsAppTealVal,
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
                .background(Color(0xFFF0F2F5))
        ) {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "স্ট্যাটাস (Status)",
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
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = WhatsAppTealVal)
            )

            // Section 1: My Status Row
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
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
                                    .border(1.5.dp, Color.White, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text("আমার স্ট্যাটাস", fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (myStatuses.isEmpty()) "স্ট্যাটাস আপডেট করতে টাচ করুন" else {
                                val latest = myStatuses.maxBy { it.timestamp }
                                if (!latest.mediaUrl.isNullOrEmpty()) "📷 ফটো আপডেট • ${formatTimeAgo(latest.timestamp)}" else "${latest.text} • ${formatTimeAgo(latest.timestamp)}"
                            },
                            color = Color.Gray,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Section 2: Recent updates
            Text(
                text = "সাম্প্রতিক আপডেটসমূহ (Recent Updates):",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp, top = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth().weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RectangleShape
            ) {
                if (otherUsersStatuses.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "কোনো সাম্প্রতিক স্ট্যাটাস আপডেট নেই",
                            color = Color.Gray,
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
                                    Text(latestStatus.name, fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 15.sp)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (!latestStatus.mediaUrl.isNullOrEmpty()) "📷 ফটো আপডেট • ${formatTimeAgo(latestStatus.timestamp)}" else latestStatus.text,
                                        color = Color.Gray,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (idx < entries.size - 1) {
                                HorizontalDivider(color = Color(0xFFF1F1F1), modifier = Modifier.padding(start = 82.dp))
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
                    Text("টেক্সট স্ট্যাটাস দিন", fontWeight = FontWeight.Bold, color = WhatsAppTealVal)
                    IconButton(
                        onClick = {
                            selectedBgColorIndex = (selectedBgColorIndex + 1) % bgColors.size
                        }
                    ) {
                        Icon(Icons.Default.ColorLens, contentDescription = "রং পরিবর্তন", tint = WhatsAppTealVal)
                    }
                }
            },
            text = {
                Column {
                    Text("আপনার মনের চমৎকার কথাটি টাইপ করুনঃ", fontSize = 12.sp, color = Color.Gray)
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
                                text = "এখানে লিখুন...",
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
                            Toast.makeText(context, "টেক্সট স্ট্যাটাস আপডেট সম্পন্ন হয়েছে!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "দয়া করে কিছু লিখুন!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WhatsAppTealVal)
                ) {
                    Text("আপডেট", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTextStatusDialog = false }) {
                    Text("বাতিল", color = Color.Red)
                }
            }
        )
    }

    // Modal adding My photo Status
    showAddPhotoStatusDialog?.let { localPath ->
        AlertDialog(
            onDismissRequest = { showAddPhotoStatusDialog = null },
            title = { Text("ফটো স্ট্যাটাস দিন", fontWeight = FontWeight.Bold, color = WhatsAppTealVal) },
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
                        placeholder = { Text("ক্যাপশন যোগ করুন (ঐচ্ছিক)...") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black,
                            focusedBorderColor = WhatsAppTealVal
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
                        Toast.makeText(context, "ফটো স্ট্যাটাস আপডেট সম্পন্ন হয়েছে!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WhatsAppTealVal)
                ) {
                    Text("আপডেট", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddPhotoStatusDialog = null }) {
                    Text("বাতিল", color = Color.Red)
                }
            }
        )
    }

    // Modal chooser dialog for "আমার স্ট্যাটাস" empty tap
    if (showPostChooserDialog) {
        AlertDialog(
            onDismissRequest = { showPostChooserDialog = false },
            title = { Text("স্ট্যাটাস আপডেট ধরন", fontWeight = FontWeight.Bold, color = WhatsAppTealVal) },
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
                        Text("মন দিয়ে কিছু লিখুন (Text Status)", color = Color.White)
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
                        Text("ছবি শেয়ার করুন (Photo Status)", color = Color.White)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPostChooserDialog = false }) {
                    Text("বাতিল", color = Color.Gray)
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
    val myName = viewModel.userDisplayName.value
    val myProfilePic = viewModel.userProfilePicBase64.value

    var showPasswordChangeDialog by remember { mutableStateOf(false) }
    var newPasswordInput by remember { mutableStateOf("") }

    // Firebase states
    val firebaseApiKey = viewModel.firebaseApiKey.collectAsStateWithLifecycle()
    val firebaseProjectId = viewModel.firebaseProjectId.collectAsStateWithLifecycle()
    val firebaseAppId = viewModel.firebaseAppId.collectAsStateWithLifecycle()

    var editingApiKey by remember { mutableStateOf(firebaseApiKey.value) }
    var editingProjectId by remember { mutableStateOf(firebaseProjectId.value) }
    var editingAppId by remember { mutableStateOf(firebaseAppId.value) }

    // Sync editing states if database values change
    LaunchedEffect(firebaseApiKey.value, firebaseProjectId.value, firebaseAppId.value) {
        editingApiKey = firebaseApiKey.value
        editingProjectId = firebaseProjectId.value
        editingAppId = firebaseAppId.value
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF0F2F5))
            .verticalScroll(rememberScrollState())
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = "সেটিংস (Settings)",
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
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = WhatsAppTealVal)
        )

        // Profile quick navigation card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RectangleShape
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        viewModel.selectTab("profile")
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AvatarView(name = myName, base64 = myProfilePic, size = 56)
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(myName, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.Black)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = myPhone ?: "", color = Color.Gray, fontSize = 13.sp)
                }
                Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = Color.Gray)
            }
        }

        Text(
            text = "অ্যাকাউন্ট ও নিরাপত্তা (Account & Security)",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Gray,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RectangleShape
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showPasswordChangeDialog = true }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = WhatsAppTealVal)
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text("পাসওয়ার্ড পরিবর্তন করুন", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.Black)
                        Text("আপনার ৬-৮ ডিজিটের পাসওয়ার্ড আপডেট করুন", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
        }

        Text(
            text = "গোপনীয়তা সেটিংস (Privacy Options)",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Gray,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RectangleShape
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Last seen Privacy
                var lastSeenChoice by remember { mutableStateOf("সবাই (Everyone)") }
                var showLastSeenMenu by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("সর্বশেষ সক্রিয়তা (Last Seen)", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.Black)
                        Text(lastSeenChoice, fontSize = 12.sp, color = Color.Gray)
                    }
                    Box {
                        TextButton(onClick = { showLastSeenMenu = true }) {
                            Text("বদল করুন", color = WhatsAppTealVal)
                        }
                        DropdownMenu(expanded = showLastSeenMenu, onDismissRequest = { showLastSeenMenu = false }) {
                            listOf("সবাই (Everyone)", "আমার পরিচিত (My Contacts)", "কেউ না (Nobody)").forEach { choice ->
                                DropdownMenuItem(
                                    text = { Text(choice) },
                                    onClick = {
                                        lastSeenChoice = choice
                                        showLastSeenMenu = false
                                        Toast.makeText(viewModel.getApplication(), "গোপনীয়তা সেটিংস সফলভাবে আপডেট হয়েছে!", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFFF1F1F1))

                // Profile photo visibility
                var photoVisibility by remember { mutableStateOf("সবাই (Everyone)") }
                var showPhotoMenu by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("প্রোফাইল ফটো (Profile Photo)", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.Black)
                        Text(photoVisibility, fontSize = 12.sp, color = Color.Gray)
                    }
                    Box {
                        TextButton(onClick = { showPhotoMenu = true }) {
                            Text("বদল করুন", color = WhatsAppTealVal)
                        }
                        DropdownMenu(expanded = showPhotoMenu, onDismissRequest = { showPhotoMenu = false }) {
                            listOf("সবাই (Everyone)", "কেউ না (Nobody)").forEach { choice ->
                                DropdownMenuItem(
                                    text = { Text(choice) },
                                    onClick = {
                                        photoVisibility = choice
                                        showPhotoMenu = false
                                        Toast.makeText(viewModel.getApplication(), "গোপনীয়তা সেটিংস সফলভাবে আপডেট হয়েছে!", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFFF1F1F1))

                // Read Receipts Switch
                var readReceiptsToggle by remember { mutableStateOf(true) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("পঠিত বার্তা নিশ্চিতকরণ (Read Receipts)", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.Black)
                        Text("অন্য কারোর ব্লু টিক চ্যাট দেখতে এটি সাহায্য করে", fontSize = 12.sp, color = Color.Gray)
                    }
                    Switch(
                        checked = readReceiptsToggle,
                        onCheckedChange = {
                            readReceiptsToggle = it
                            Toast.makeText(viewModel.getApplication(), "পঠিত বার্তা রিসিট আপডেট হয়েছে!", Toast.LENGTH_SHORT).show()
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = WhatsAppGreenVal, checkedTrackColor = WhatsAppGreenVal.copy(alpha = 0.4f))
                    )
                }
            }
        }

        Text(
            text = "ফায়ারবেস ক্লাউড কানেকশন (Firebase Cloud Connection)",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Gray,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RectangleShape
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("রিয়েল টাইম সিংক্রোনাইজেশন ক্লাউড ডাটাবেস সেটিংস এখানে পরিবর্তন করুন:", fontSize = 12.sp, color = Color.Gray)

                OutlinedTextField(
                    value = editingApiKey,
                    onValueChange = { editingApiKey = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        focusedLabelColor = WhatsAppTealVal
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = editingProjectId,
                    onValueChange = { editingProjectId = it },
                    label = { Text("Project ID") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        focusedLabelColor = WhatsAppTealVal
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = editingAppId,
                    onValueChange = { editingAppId = it },
                    label = { Text("App ID") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        focusedLabelColor = WhatsAppTealVal
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            viewModel.saveFirebaseConfig(editingApiKey, editingProjectId, editingAppId)
                            Toast.makeText(viewModel.getApplication(), "ক্লাউড কনফিগারেশন সেভ হয়েছে!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreenVal)
                    ) {
                        Text("সেভ করুন (Save)", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            editingApiKey = ""
                            editingProjectId = ""
                            editingAppId = ""
                            viewModel.saveFirebaseConfig("", "", "")
                            Toast.makeText(viewModel.getApplication(), "ক্লাউড কনফিগারেশন মুছে ফেলা হয়েছে!", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("মুছুন")
                    }
                }
            }
        }

        // Logout Area
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
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
                Text("লগআউট করুন (Logout)", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }

    if (showPasswordChangeDialog) {
        AlertDialog(
            onDismissRequest = { showPasswordChangeDialog = false },
            title = { Text("পাসওয়ার্ড পরিবর্তন", fontWeight = FontWeight.Bold, color = WhatsAppTealVal) },
            text = {
                Column {
                    Text("অনন্য নতুন ৬ থেকে ৮ অক্ষরের পাসওয়ার্ড সতর্কতার সাথে দিনঃ", fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newPasswordInput,
                        onValueChange = { newPasswordInput = it },
                        placeholder = { Text("******") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black,
                            focusedBorderColor = WhatsAppTealVal
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
                        color = Color.White
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
                
                Text(
                    text = if (contact.isGroup) "group" else contact.lastSeen,
                    color = if (contact.lastSeen == "online") WhatsAppGreenVal else Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = if (contact.lastSeen == "online") FontWeight.Bold else FontWeight.Normal
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("নতুন পরিচিত", color = Color.White, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = WhatsAppTealVal)
            )
        },
        floatingActionButton = {
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
                    text = { Text("নতুন গ্রুপ") },
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
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (contactList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("কোনো কন্ট্যাক্ট নেই। প্লাস চাপুন!", color = Color.Gray)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(contactList) { contact ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onContactClick(contact) }
                                .padding(16.dp),
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
                            Column {
                                Text(contact.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                                Text(contact.phone, color = Color.LightGray, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            if (showAddDialog) {
                AddContactDialog(
                    onDismiss = { showAddDialog = false },
                    onConfirm = { name, phone, autoReply ->
                        viewModel.addNewContact(name, phone, autoReply)
                        showAddDialog = false
                    }
                )
            }

            if (showCreateGroupDialog) {
                CreateGroupDialog(
                    contacts = contactList.filter { !it.isGroup },
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
    onDismiss: () -> Unit,
    onConfirm: (groupName: String, selected: List<Contact>) -> Unit
) {
    var groupName by remember { mutableStateOf("") }
    val selectedContacts = remember { mutableStateListOf<Contact>() }
    var hasError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("নতুন গ্রুপ চ্যাট খুলুন", fontWeight = FontWeight.Bold, color = WhatsAppTealVal) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = {
                        groupName = it
                        hasError = false
                    },
                    label = { Text("গ্রুপের নাম") },
                    placeholder = { Text("যেমন: বন্ধুদের আড্ডা") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = WhatsAppTealVal
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (hasError) {
                    Text("গ্রুপের নাম অবশ্যই দিতে হবে!", color = Color.Red, fontSize = 11.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("সদস্য যুক্ত করুন (পছন্দ করুন):", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
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
                Text("তৈরি করুন", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("বাতিল", color = Color.Gray)
            }
        }
    )
}

@Composable
fun AddContactDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, phone: String, simulateReply: Boolean) -> Unit
) {
    var contactName by remember { mutableStateOf("") }
    var contactPhone by remember { mutableStateOf("") }
    var autoReplySim by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("নতুন পরিচিতি যোগ করুন") },
        text = {
            Column {
                OutlinedTextField(
                    value = contactName,
                    onValueChange = { contactName = it },
                    label = { Text("নাম") },
                    placeholder = { Text("সাকিব চৌধুরী") },
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
                    label = { Text("মোবাইল নাম্বার") },
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
                        text = "১১ ডিজিটের সঠিক বাংলাদেশ মোবাইল নাম্বার দিন!",
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
                    Text("অনলাইন উত্তরদাতা ও বট সিমুলেশন সচল রাখুন।", fontSize = 12.sp)
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
                Text("যোগ করুন", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("বাতিল করুন", color = Color.Gray)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileTabScreen(
    viewModel: ChatViewModel
) {
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
    val profilePhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                val localPath = copyUriToLocalFile(context, uri)
                if (localPath != null) {
                    profilePicState = localPath
                    showPhotoSelectorProfile = false
                    Toast.makeText(context, "গ্যালারি থেকে প্রোফাইল ছবি নেওয়া হয়েছে!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "ছবি লোড করতে সমস্যা হয়েছে!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    )

    LaunchedEffect(initialDisplayName, initialStatusMessage, initialProfilePic) {
        displayNameInput = initialDisplayName
        statusMessageInput = initialStatusMessage
        profilePicState = initialProfilePic
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF0F2F5))
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "প্রোফাইল সংশোধন (Edit Profile)",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
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
                    colors = CardDefaults.cardColors(containerColor = Color.White),
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
                                .background(Color(0xFFEEEEEE))
                                .border(2.5.dp, WhatsAppGreenVal, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                imageVector = getAvatarVector(profilePicState),
                                contentDescription = "Profile Picture",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "মোবাইল নাম্বারঃ $myPhone",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = WhatsAppTealVal
                        )
                        Text(
                            text = "ছবি পরিবর্তন করতে বৃত্তটিতে স্পর্শ করুন",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "ব্যক্তিগত তথ্যসমূহ",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.Black
                        )

                        OutlinedTextField(
                            value = displayNameInput,
                            onValueChange = {
                                displayNameInput = it
                                profileSavedAlert = false
                            },
                            label = { Text("ডিসপ্লে নাম / Display Name") },
                            placeholder = { Text("উদা: আকাশ চৌধুরী") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.Black,
                                unfocusedTextColor = Color.Black,
                                focusedLabelColor = WhatsAppTealVal,
                                unfocusedLabelColor = Color.Gray
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
                            label = { Text("স্ট্যাটাস মেসেজ / Status Message") },
                            placeholder = { Text("উদা: চ্যাট করছি বা ব্যস্ত আছি") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.Black,
                                unfocusedTextColor = Color.Black,
                                focusedLabelColor = WhatsAppTealVal,
                                unfocusedLabelColor = Color.Gray
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
                            Text("প্রোফাইল সেভ করুন", color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        if (profileSavedAlert) {
                            Text(
                                text = "প্রোফাইল সফলভাবে আপডেট করা হয়েছে! 🟢",
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
            title = { Text("প্রোফাইল ছবি পরিবর্তন", fontWeight = FontWeight.Bold, color = WhatsAppTealVal) },
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
                    Button(
                        onClick = {
                            profilePhotoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WhatsAppTealVal),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("গ্যালারি থেকে ছবি নিন", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showPhotoSelectorProfile = false },
                    colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreenVal)
                ) {
                    Text("ঠিক আছে", color = Color.White)
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
                                text = "সদস্যরা: ${contact.groupParticipants.replace("group_", "").take(80)}",
                                color = Color(0xB3FFFFFF),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Text(
                                text = if (contact.typingStatus.isNotEmpty()) contact.typingStatus else contact.lastSeen,
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
                    onDeleteForMe = { viewModel.deleteMessageForMe(msg.id) },
                    onDeleteForEveryone = { viewModel.deleteMessageForEveryone(msg.id) },
                    onImageClick = { selectedImageForPreview = it },
                    onVideoClick = { selectedVideoForPlayback = it }
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
                        imageVector = Icons.Default.SentimentSatisfiedAlt,
                        contentDescription = "Emoji select",
                        tint = Color.Gray,
                        modifier = Modifier.clickable {
                            inputText += "😊 "
                        }
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
                        onValueChange = { inputText = it },
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
    onDeleteForMe: () -> Unit,
    onDeleteForEveryone: () -> Unit,
    onImageClick: (String) -> Unit,
    onVideoClick: (String) -> Unit
) {
    val layoutAlign = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (isMe) WhatsAppLightGreenVal else Color.White
    val bubbleShape = if (isMe) {
        RoundedCornerShape(12.dp, 12.dp, 0.dp, 12.dp)
    } else {
        RoundedCornerShape(12.dp, 12.dp, 12.dp, 0.dp)
    }

    var showDeleteConfirmationDialog by remember { mutableStateOf(false) }

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
                    Text(
                        text = message.text,
                        fontSize = 15.sp,
                        color = Color.Black
                    )
                }
                
                Spacer(modifier = Modifier.height(2.dp))
                
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTime(message.timestamp),
                        fontSize = 10.sp,
                        color = Color.Gray
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
    if (showDeleteConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmationDialog = false },
            title = { Text("বার্তা সংশোধন বা মুছুন", fontWeight = FontWeight.Bold, color = WhatsAppTealVal) },
            text = { Text("বার্তাটির নিরাপত্তা নিশ্চিতকরণঃ দয়া করে আপনার কাঙ্ক্ষিত অপশনটি নির্বাচন করুন।", fontSize = 12.sp, color = Color.Gray) },
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
                        Text("আমার থেকে মুছুন (Delete for Me)", color = Color.White)
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
                            Text("সবার জন্য মুছুন (Delete for Everyone)", color = Color.White)
                        }
                    }

                    // Cancel option
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { showDeleteConfirmationDialog = false }
                    ) {
                        Text("বাতিল করুন")
                    }
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
            Image(
                imageVector = getAvatarVector(base64),
                contentDescription = name,
                modifier = Modifier.fillMaxSize()
            )
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

@Composable
fun ManageGroupDialog(
    contact: Contact,
    viewModel: ChatViewModel,
    onDismiss: () -> Unit
) {
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
    
    var hasError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("গ্রুপ সেটিংস ও সদস্য পরিবর্তন", fontWeight = FontWeight.Bold, color = WhatsAppTealVal)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = {
                        groupName = it
                        hasError = false
                    },
                    label = { Text("গ্রুপের নাম") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = WhatsAppTealVal,
                        unfocusedLabelColor = Color.Gray
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
                                .clickable {
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
                                    if (checked == true) {
                                        selectedParticipantPhones.add(c.phone)
                                    } else {
                                        if (c.phone != myNumber) {
                                            selectedParticipantPhones.remove(c.phone)
                                        }
                                    }
                                },
                                enabled = c.phone != myNumber
                            )
                            AvatarView(name = c.name, base64 = c.profilePicUri, size = 32)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(c.name, color = Color.White, fontSize = 14.sp)
                                if (c.phone == myNumber) {
                                    Text("আপনি (Admin)", color = WhatsAppGreenVal, fontSize = 11.sp)
                                } else {
                                    Text(c.phone, color = Color.Gray, fontSize = 11.sp)
                                }
                            }
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
                        viewModel.updateGroup(contact.phone, groupName, selectedParticipantPhones.toList())
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreenVal)
            ) {
                Text("পরিবর্তন সেভ করুন", color = Color.White)
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
