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
import androidx.compose.animation.core.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.asImageBitmap
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
import androidx.compose.ui.window.DialogProperties
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
        if (showOnboarding) {
            GreetingOnboardingScreen(
                viewModel = viewModel,
                onFinished = {
                    viewModel.showOnboarding.value = false
                }
            )
        } else if (loggedInNumber == null) {
            if (!isConnected) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Icon(Icons.Default.WifiOff, contentDescription = null, tint = WhatsAppTealVal, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(txt("ইন্টারনেট সংযোগ নেই", "No Internet Connection"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(txt("লগইন বা একাউন্ট তৈরি করতে দয়া করে ইন্টারনেট অন করুন।", "Please connect to the internet to login or sign up."), color = Color.Gray, fontSize = 14.sp)
                    }
                }
            } else {
                AuthScreen(viewModel = viewModel)
            }
        } else {
            val navigationStack by viewModel.navigationStack.collectAsStateWithLifecycle()
            val canGoBack = activeChatContact != null || navigationStack.size > 1

            BackHandler(enabled = canGoBack) {
                viewModel.navigateBack()
            }

            Scaffold(
                topBar = {
                    if (!isConnected) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFE53935))
                                .padding(vertical = 4.dp, horizontal = 12.dp)
                        ) {
                            Text(
                                text = txt("অফলাইন মোড • শুধু আগের চ্যাটগুলো দেখা যাবে", "Offline Mode • Only previous chats can be viewed"),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                },
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
                }
            }

            // Slide overlay for the chat list
            AnimatedVisibility(
                visible = activeChatContact != null,
                enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
            ) {
                activeChatContact?.let { contact ->
                    if (contact.phone == "01300000000") {
                        AIAssistantChatScreen(
                            viewModel = viewModel,
                            onClose = { viewModel.selectContact(null) }
                        )
                    } else {
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
            .verticalScroll(rememberScrollState())
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
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
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
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedLabelColor = WhatsAppTealVal,
                        unfocusedLabelColor = Color.Gray,
                        focusedPrefixColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedPrefixColor = MaterialTheme.colorScheme.onSurface
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
                                contentDescription = if (showPassword) "Hide" else "Show",
                                tint = Color.Gray
                            )
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
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
    var showCreateGroupDialog by remember { mutableStateOf(false) }

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
                    // New Group Action "+" Button
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(if (isDark) Color(0xFF1E293B) else Color.White, CircleShape)
                            .clickable { showCreateGroupDialog = true }
                            .testTag("chats_create_group_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Group",
                            tint = if (isDark) Color.White else Color(0xFF1E293B),
                            modifier = Modifier.size(20.dp)
                        )
                    }

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

                val context = LocalContext.current
                val bartaChatPrefs = remember { context.getSharedPreferences("BartaChatPrefs", Context.MODE_PRIVATE) }

                // Row of subcategory pills: "সব", "আনপড়া", "প্রিয়", "গ্রুপ"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .horizontalScroll(rememberScrollState()),
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

                    // "আর্কাইভ" Pill
                    val isArchivedActive = activeSubTab == "archived"
                    val archivedCount = remember(individualChats, activeSubTab) {
                        individualChats.count { bartaChatPrefs.getBoolean("archive_${it.phone}", false) }
                    }
                    if (archivedCount > 0) {
                        PillItem(
                            text = txt("আর্কাইভ ($archivedCount)", "Archived ($archivedCount)"),
                            icon = Icons.Default.Archive,
                            isSelected = isArchivedActive,
                            isDark = isDark,
                            onClick = { activeSubTab = "archived" }
                        )
                    }

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
                    val nonArchived = individualChats.filter { !bartaChatPrefs.getBoolean("archive_${it.phone}", false) }
                    when (activeSubTab) {
                        "unread" -> nonArchived.filter { it.unreadCount > 0 }
                        "fav" -> nonArchived.filter { it.name.contains("সুমি") || it.name.contains("রফিক") || it.phone.endsWith("2") || it.phone.endsWith("4") }
                        "archived" -> individualChats.filter { bartaChatPrefs.getBoolean("archive_${it.phone}", false) }
                        else -> nonArchived
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

    if (showCreateGroupDialog) {
        CreateGroupDialog(
            contacts = contactsList.filter { !it.isGroup && it.phone != "01300000000" },
            viewModel = viewModel,
            onDismiss = { showCreateGroupDialog = false },
            onConfirm = { groupName, selected, photo, desc ->
                viewModel.createGroup(groupName, selected, photo, desc)
                showCreateGroupDialog = false
            }
        )
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
            contacts = contactsList.filter { !it.isGroup && it.phone != "01300000000" },
            viewModel = viewModel,
            onDismiss = { showCreateGroupDialog = false },
            onConfirm = { groupName, selected, photo, desc ->
                viewModel.createGroup(groupName, selected, photo, desc)
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
            var showViewersList by remember { mutableStateOf(false) }
            var showDeleteConfirmation by remember { mutableStateOf<String?>(null) }

            // Automatically mark as viewed if it's not my own status
            LaunchedEffect(activeStory.id) {
                if (activeStory.phone != myPhone) {
                    viewModel.markStatusAsViewed(activeStory.id, activeStory.viewers)
                }
            }

            // Reset viewers list visibility on story index change
            LaunchedEffect(activeStoryIndex) {
                showViewersList = false
                showDeleteConfirmation = null
            }

            // Automatically progress each story ticking up to 1f and shifting index
            LaunchedEffect(activeStoryIndex, showViewersList, showDeleteConfirmation) {
                if (showViewersList || showDeleteConfirmation != null) return@LaunchedEffect
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

            val loversList = if (activeStory.loves.isEmpty()) emptyList() else activeStory.loves.split(",")
            val viewersList = if (activeStory.viewers.isEmpty()) emptyList() else activeStory.viewers.split(",")
            val hasLoved = loversList.any { it.startsWith("$myPhone:") }

            Dialog(
                onDismissRequest = { activeStoryList = null },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (activeStory.mediaUrl.isNullOrEmpty()) Color(activeStory.bgColorVal) else Color.Black)
                ) {
                    // 1. Center content (Image or Text)
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
                                    .padding(vertical = 120.dp, horizontal = 16.dp) // padded so it doesn't overlap bottom action bar
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

                    // 2. Hot-zones for clicking previous/next (invisible overlay buttons)
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

                    // 3. Top Indicators & Header controls (Drawn over hot-zones)
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

                        // Left Side Avatar Name, Right Side Delete & Close
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
                            
                            // Delete button for status owner
                            if (activeStory.phone == myPhone) {
                                IconButton(
                                    onClick = {
                                        showDeleteConfirmation = activeStory.id
                                    }
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Status", tint = Color.White)
                                }
                            }

                            IconButton(onClick = { activeStoryList = null }) {
                                Icon(Icons.Default.Close, contentDescription = "Close Story", tint = Color.White)
                            }
                        }
                    }

                    // 4. Bottom action bar (Love, Viewers)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 24.dp, start = 16.dp, end = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(24.dp))
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Left: Love reaction (Yellow Color)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        viewModel.toggleLoveStatus(activeStory.id, activeStory.loves)
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (hasLoved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = "Love Status",
                                        tint = if (hasLoved) Color.Yellow else Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                if (loversList.isNotEmpty()) {
                                    Text(
                                        text = loversList.size.toString(),
                                        color = Color.Yellow,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }

                            // Right: If owner, show "views count" clickable to see who viewed. Else show simple view indicator
                            if (activeStory.phone == myPhone) {
                                Row(
                                    modifier = Modifier
                                        .background(Color.White.copy(alpha = 0.2f), shape = RoundedCornerShape(16.dp))
                                        .clickable { showViewersList = true }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RemoveRedEye,
                                        contentDescription = "Viewers",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "${viewersList.size} ${txt("জন দেখেছেন", "views")}",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DoneAll,
                                        contentDescription = "Seen",
                                        tint = WhatsAppGreenVal,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = txt("পঠিত", "Seen"),
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    // 5. Viewers overlay list
                    if (showViewersList) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.7f))
                                .clickable { showViewersList = false },
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(0.5f)
                                    .clickable(enabled = false) { /* Prevent clicks through to background */ },
                                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                                colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF1E293B) else Color.White)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${txt("কারা দেখেছেন", "Viewers")} (${viewersList.size})",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = if (isDark) Color.White else Color.Black
                                        )
                                        IconButton(onClick = { showViewersList = false }) {
                                            Icon(Icons.Default.Close, contentDescription = "Close Viewers List", tint = if (isDark) Color.White else Color.Black)
                                        }
                                    }

                                    HorizontalDivider(color = if (isDark) Color.Gray.copy(alpha = 0.3f) else Color.LightGray)

                                    if (viewersList.isEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxWidth(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = txt("এখনো কেউ দেখেননি", "No views yet"),
                                                color = Color.Gray,
                                                fontSize = 14.sp
                                            )
                                        }
                                    } else {
                                        LazyColumn(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxWidth(),
                                            contentPadding = PaddingValues(vertical = 8.dp)
                                        ) {
                                            items(viewersList) { viewerInfo ->
                                                val parts = viewerInfo.split(":")
                                                val viewerPhone = parts.getOrNull(0) ?: ""
                                                val viewerName = parts.getOrNull(1) ?: viewerPhone
                                                val isLover = loversList.any { it.startsWith("$viewerPhone:") }

                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        AvatarView(name = viewerName, base64 = "", size = 36)
                                                        Spacer(modifier = Modifier.width(12.dp))
                                                        Column {
                                                            Text(
                                                                text = viewerName,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 14.sp,
                                                                color = if (isDark) Color.White else Color.Black
                                                            )
                                                            Text(
                                                                text = viewerPhone,
                                                                fontSize = 11.sp,
                                                                color = Color.Gray
                                                            )
                                                        }
                                                    }
                                                    
                                                    // Show Yellow Heart if this viewer also liked/loved the status
                                                    if (isLover) {
                                                        Icon(
                                                            imageVector = Icons.Default.Favorite,
                                                            contentDescription = "Loved status",
                                                            tint = Color.Yellow,
                                                            modifier = Modifier
                                                                .size(20.dp)
                                                                .padding(end = 4.dp)
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

                    // 6. Delete confirmation dialog
                    if (showDeleteConfirmation != null) {
                        AlertDialog(
                            onDismissRequest = { showDeleteConfirmation = null },
                            title = {
                                Text(
                                    text = txt("স্ট্যাটাস মুছে ফেলবেন?", "Delete Status?"),
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Color.White else Color.Black
                                )
                            },
                            text = {
                                Text(
                                    text = txt(
                                        "আপনি কি নিশ্চিত যে আপনি এই স্ট্যাটাসটি মুছে ফেলতে চান?",
                                        "Are you sure you want to remove this status?"
                                    ),
                                    color = if (isDark) Color.LightGray else Color.DarkGray
                                )
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        val toDelete = showDeleteConfirmation ?: ""
                                        showDeleteConfirmation = null
                                        viewModel.deleteStatus(toDelete) { success ->
                                            if (success) {
                                                if (activeStoryIndex < storyCount - 1) {
                                                    activeStoryIndex++
                                                } else {
                                                    activeStoryList = null
                                                }
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                                ) {
                                    Text(text = txt("মুছে ফেলুন", "Delete"), color = Color.White)
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = { showDeleteConfirmation = null }
                                ) {
                                    Text(text = txt("বাতিল", "Cancel"), color = if (isDark) Color.LightGray else Color.Gray)
                                }
                            },
                            containerColor = if (isDark) Color(0xFF1E293B) else Color.White
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
    val isConnected by rememberConnectivityState()
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
                    onClick = {
                        if (!isConnected) {
                            Toast.makeText(viewModel.getApplication(), txt("অফলাইন মোডে সেটিংস পরিবর্তন করা যাবে না", "Settings cannot be changed in offline mode"), Toast.LENGTH_SHORT).show()
                        } else {
                            showPasswordChangeDialog = true
                        }
                    },
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
                        .clickable {
                            if (!isConnected) {
                                Toast.makeText(viewModel.getApplication(), txt("অফলাইন মোডে সেটিংস পরিবর্তন করা যাবে না", "Settings cannot be changed in offline mode"), Toast.LENGTH_SHORT).show()
                            } else {
                                showLangMenu = true
                            }
                        }
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
                        TextButton(onClick = {
                            if (!isConnected) {
                                Toast.makeText(viewModel.getApplication(), txt("অফলাইন মোডে সেটিংস পরিবর্তন করা যাবে না", "Settings cannot be changed in offline mode"), Toast.LENGTH_SHORT).show()
                            } else {
                                showLangMenu = true
                            }
                        }) {
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
                        .clickable {
                            if (!isConnected) {
                                Toast.makeText(viewModel.getApplication(), txt("অফলাইন মোডে সেটিংস পরিবর্তন করা যাবে না", "Settings cannot be changed in offline mode"), Toast.LENGTH_SHORT).show()
                            } else {
                                showThemeMenu = true
                            }
                        }
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
                        TextButton(onClick = {
                            if (!isConnected) {
                                Toast.makeText(viewModel.getApplication(), txt("অফলাইন মোডে সেটিংস পরিবর্তন করা যাবে না", "Settings cannot be changed in offline mode"), Toast.LENGTH_SHORT).show()
                            } else {
                                showThemeMenu = true
                            }
                        }) {
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
                val lastSeenChoice by viewModel.lastSeenPrivacy.collectAsStateWithLifecycle()
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
                                        viewModel.updateLastSeenPrivacy(key)
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
    val context = LocalContext.current
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val isBn = appLanguage == "bn"
    fun txt(bn: String, en: String) = if (isBn) bn else en

    val onBartaContacts by viewModel.onBartaContacts.collectAsStateWithLifecycle()
    val inviteContacts by viewModel.inviteContacts.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()

    val searchResultUser by viewModel.searchResultUser.collectAsStateWithLifecycle()
    val isSearchingServer by viewModel.isSearchingServer.collectAsStateWithLifecycle()
    val searchError by viewModel.searchError.collectAsStateWithLifecycle()

    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var searchQueryText by remember { mutableStateOf("") }

    var hasPermission by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_CONTACTS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (isGranted) {
            Toast.makeText(context, txt("কন্ট্যাক্ট পড়ার অনুমতি পাওয়া গেছে!", "Contacts read permission granted!"), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, txt("কন্ট্যাক্ট পারমিশন না দেওয়ায় কন্ট্যাক্ট তালিকায় কিছু দেখানো যাচ্ছে না।", "Permission denied. Unable to show contacts list."), Toast.LENGTH_LONG).show()
        }
    }

    // Auto-sync in background on permission granted or tab view
    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            val fallbackUsers = viewModel.getFirebaseUsers()
            val contacts = getDeviceContactsNatively(context, fallbackUsers)
            viewModel.syncContacts(contacts)
        }
    }

    // Auto refresh when contacts tab becomes active
    LaunchedEffect(Unit) {
        if (hasPermission) {
            val fallbackUsers = viewModel.getFirebaseUsers()
            val contacts = getDeviceContactsNatively(context, fallbackUsers)
            viewModel.syncContacts(contacts)
        }
        viewModel.clearSearchResult()
    }

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
                    if (hasPermission) {
                        IconButton(
                            onClick = {
                                val fallbackUsers = viewModel.getFirebaseUsersForFallback()
                                val contacts = getDeviceContactsNatively(context, fallbackUsers)
                                viewModel.syncContacts(contacts)
                                Toast.makeText(context, txt("পরিচিতি তালিকা সমলয় (সিঙ্ক) হচ্ছে...", "Syncing contacts list..."), Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.testTag("sync_contacts_action")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Sync Contacts",
                                tint = Color.White
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (hasPermission) {
                ExtendedFloatingActionButton(
                    onClick = { showCreateGroupDialog = true },
                    containerColor = WhatsAppTealVal,
                    contentColor = Color.White,
                    icon = { Icon(Icons.Default.Groups, contentDescription = "Create Group") },
                    text = { Text(txt("নতুন গ্রুপ", "New Group")) },
                    modifier = Modifier.testTag("create_group_fab")
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFF0B141A)) // Maintain dark WhatsApp aesthetic
        ) {
            if (!hasPermission) {
                // Access Denied / Onboarding Screen
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2C34)),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(WhatsAppTealVal.copy(alpha = 0.15f), shape = CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Contacts,
                                    contentDescription = "Contacts Permission Required",
                                    tint = WhatsAppTealVal,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = txt("পরিচিতি সমলয় করুন (Sync)", "Synchronize Contacts"),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = txt(
                                    "আপনার ডিভাইসের কন্ট্যাক্ট তালিকা মিলিয়ে দেখে কে কে 'বার্তা' অ্যাপ ব্যবহার করছেন তা সরাসরি আপনার চ্যাট তালিকায় যুক্ত করতে কন্ট্যাক্ট পারমিশন প্রয়োজন। বার্তা অ্যাপ আপনার কন্ট্যাক্ট ডেটা অত্যন্ত নিরাপদে হ্যান্ডেল করে এবং আপনার অনুমতি ছাড়া কাউকে লিস্ট প্রদর্শন করে না।",
                                    "We need contacts access to match saved device phone numbers with Barta Chat registered accounts. We handle your privacy securely and never share your contact list with third parties."
                                ),
                                color = Color.LightGray,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = {
                                    permissionLauncher.launch(android.Manifest.permission.READ_CONTACTS)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreenVal),
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("request_permission_btn")
                            ) {
                                Icon(imageVector = Icons.Default.Sync, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(txt("অনুমতি দিন ও সমলয় করুন", "Allow & Synchronize"), color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                // Permission is granted - show search and dual-section list
                OutlinedTextField(
                    value = searchQueryText,
                    onValueChange = { 
                        searchQueryText = it
                        if (it.isBlank()) {
                            viewModel.clearSearchResult()
                        }
                    },
                    placeholder = {
                        Text(
                            text = txt("নাম বা ফোন নম্বর দিয়ে খুঁজুন...", "Search name or phone number..."),
                            color = Color.LightGray.copy(alpha = 0.6f),
                            fontSize = 14.sp
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Search Icon", tint = Color.LightGray.copy(alpha = 0.6f))
                    },
                    trailingIcon = {
                        if (searchQueryText.isNotEmpty()) {
                            IconButton(onClick = { 
                                searchQueryText = "" 
                                viewModel.clearSearchResult()
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.LightGray)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .testTag("contacts_search_input"),
                    shape = RoundedCornerShape(24.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = WhatsAppTealVal,
                        unfocusedBorderColor = Color(0xFF1F2C34),
                        focusedContainerColor = Color(0xFF1F2C34),
                        unfocusedContainerColor = Color(0xFF1F2C34).copy(alpha = 0.6f)
                    )
                )

                // Filter lists based on searchQueryText
                val filteredOnBarta = remember(onBartaContacts, searchQueryText) {
                    if (searchQueryText.isBlank()) {
                        onBartaContacts
                    } else {
                        onBartaContacts.filter {
                            it.deviceName.contains(searchQueryText, ignoreCase = true) || 
                            it.phone.contains(searchQueryText)
                        }
                    }
                }

                val filteredInvite = remember(inviteContacts, searchQueryText) {
                    if (searchQueryText.isBlank()) {
                        inviteContacts
                    } else {
                        inviteContacts.filter {
                            it.first.contains(searchQueryText, ignoreCase = true) || 
                            it.second.contains(searchQueryText)
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    if (isSyncing && onBartaContacts.isEmpty() && inviteContacts.isEmpty()) {
                        // Initial loading indicator
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = WhatsAppTealVal)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(txt("পরিচিতি সমলয় (সিঙ্ক) হচ্ছে...", "Synchronizing contacts..."), color = Color.Gray, fontSize = 14.sp)
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 80.dp)
                        ) {
                            // 1. SECTION: ON BARTA CHAT
                            if (filteredOnBarta.isNotEmpty()) {
                                item {
                                    Text(
                                        text = txt("বার্তায় আছেন (${filteredOnBarta.size} জন)", "On Barta (Chat) (${filteredOnBarta.size})"),
                                        color = WhatsAppTealVal,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }

                                items(filteredOnBarta) { match ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.initiateChatWithRegisteredUser(match.phone, match.deviceName, match.profilePicBase64) { contact ->
                                                    onContactClick(contact)
                                                }
                                            }
                                            .padding(horizontal = 16.dp, vertical = 12.dp)
                                            .testTag("contact_row_${match.phone}"),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AvatarView(name = match.deviceName, base64 = match.profilePicBase64, size = 44)
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(match.deviceName, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(match.status, color = Color.LightGray, fontSize = 12.sp, maxLines = 1)
                                            Text(match.phone, color = Color.Gray, fontSize = 11.sp)
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
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                    HorizontalDivider(color = Color(0xFF1F2C34), thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 16.dp))
                                }
                            }

                            // 2. SECTION: INVITE TO BARTA CHAT
                            if (filteredInvite.isNotEmpty()) {
                                item {
                                    Text(
                                        text = txt("বার্তা-তে আমন্ত্রণ জানান", "Invite to Barta (Chat)"),
                                        color = WhatsAppGreenVal,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                    )
                                }

                                items(filteredInvite) { (name, phone) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AvatarView(name = name, base64 = "", size = 44)
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(phone, color = Color.Gray, fontSize = 12.sp)
                                        }
                                        Button(
                                            onClick = {
                                                try {
                                                    val smsIntent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                                                        data = android.net.Uri.parse("smsto:$phone")
                                                        putExtra("sms_body", txt("আসসালামু আলাইকুম! বার্তা (Chat) চ্যাটিং অ্যাপ ব্যবহার শুরু করুন এবং আমার সাথে সরাসরি কথা বলুন।", "Hello! Join me on Barta (Chat) messenger app to chat with me instantly."))
                                                    }
                                                    context.startActivity(smsIntent)
                                                } catch (e: Exception) {
                                                    // Fallback standard share sheet
                                                    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                        type = "text/plain"
                                                        putExtra(android.content.Intent.EXTRA_TEXT, txt("আসসালামু আলাইকুম! বার্তা (Chat) চ্যাটিং অ্যাপ ব্যবহার শুরু করুন এবং আমার সাথে সরাসরি কথা বলুন।", "Hello! Join me on Barta (Chat) messenger app to chat with me instantly."))
                                                    }
                                                    context.startActivity(android.content.Intent.createChooser(shareIntent, txt("আমন্ত্রণ পাঠান", "Send Invitation")))
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreenVal),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Text(txt("আমন্ত্রণ", "Invite"), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    HorizontalDivider(color = Color(0xFF1F2C34), thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 16.dp))
                                }
                            }

                            // 3. FALLBACK: NOT FOUND IN CONTACTS -> OPTION TO SEARCH SERVER BY PHONE
                            if (filteredOnBarta.isEmpty() && filteredInvite.isEmpty() && searchQueryText.isNotBlank()) {
                                item {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Contacts,
                                            contentDescription = "Not found",
                                            tint = Color.Gray,
                                            modifier = Modifier.size(56.dp)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = txt("পরিচিতি তালিকায় কোনো মিল পাওয়া যায়নি", "No matching contacts found"),
                                            color = Color.LightGray,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = txt("ফোনের কন্ট্যাক্টে পাওয়া যায়নি। আপনি কি বার্তা সার্ভারে সরাসরি এই নম্বরের কোনো অ্যাকাউন্ট অনুসন্ধান করতে চান?", "Not found in contacts. Would you like to search for a registered account on the server directly?"),
                                            color = Color.Gray,
                                            fontSize = 12.sp,
                                            textAlign = TextAlign.Center,
                                            lineHeight = 16.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp)
                                        )

                                        Spacer(modifier = Modifier.height(16.dp))

                                        // Server Search Actions / Card
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2C34)),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(16.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    text = txt("সার্ভার অনুসন্ধান: $searchQueryText", "Server Search: $searchQueryText"),
                                                    color = Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.height(12.dp))

                                                if (isSearchingServer) {
                                                    CircularProgressIndicator(color = WhatsAppTealVal, modifier = Modifier.size(24.dp))
                                                } else if (searchResultUser != null) {
                                                    val matchedUser = searchResultUser!!
                                                    val uPhone = matchedUser["phone"] as? String ?: ""
                                                    val uName = matchedUser["name"] as? String ?: ""
                                                    val uStatus = matchedUser["status"] as? String ?: txt("বার্তা (Chat) ব্যবহার করছি!", "Using Barta Chat!")
                                                    val uPic = matchedUser["profilePicBase64"] as? String ?: ""

                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .background(Color(0xFF0B141A), RoundedCornerShape(8.dp))
                                                            .padding(8.dp)
                                                    ) {
                                                        AvatarView(name = uName, base64 = uPic, size = 36)
                                                        Spacer(modifier = Modifier.width(10.dp))
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(uName, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                            Text(uStatus, color = Color.LightGray, fontSize = 11.sp, maxLines = 1)
                                                            Text(uPhone, color = Color.Gray, fontSize = 10.sp)
                                                        }
                                                        Button(
                                                            onClick = {
                                                                viewModel.initiateChatWithRegisteredUser(uPhone, uName, uPic) { contact ->
                                                                    onContactClick(contact)
                                                                }
                                                            },
                                                            colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreenVal),
                                                            shape = RoundedCornerShape(12.dp),
                                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                            modifier = Modifier.height(28.dp)
                                                        ) {
                                                            Text(txt("চ্যাট 💬", "Chat 💬"), fontSize = 11.sp, color = Color.White)
                                                        }
                                                    }
                                                } else if (searchError == "not_found") {
                                                    Text(
                                                        text = txt("এই নম্বরটি বার্তা (Chat) ব্যবহার করছে না।", "This number is not using Barta (Chat)."),
                                                        color = Color(0xFFEF4444),
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                } else {
                                                    Button(
                                                        onClick = {
                                                            viewModel.searchUserOnServer(searchQueryText)
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = WhatsAppTealVal),
                                                        shape = RoundedCornerShape(16.dp)
                                                    ) {
                                                        Icon(Icons.Default.Search, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(txt("সার্ভারে অনুসন্ধান করুন 🔍", "Search on Server 🔍"), color = Color.White, fontSize = 12.sp)
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
            }

            if (showCreateGroupDialog) {
                // Ensure a nice filtered participants list for creating groups (only actual registered contacts on Barta)
                val groupParticipants = onBartaContacts.map {
                    Contact(
                        phone = it.phone,
                        name = it.deviceName,
                        isSimulated = false,
                        profilePicUri = it.profilePicBase64
                    )
                }

                CreateGroupDialog(
                    contacts = groupParticipants,
                    viewModel = viewModel,
                    onDismiss = { showCreateGroupDialog = false },
                    onConfirm = { name, selected, photo, desc ->
                        viewModel.createGroup(name, selected, photo, desc)
                        showCreateGroupDialog = false
                    }
                )
            }
        }
    }
}

// Group Chat Creation Dialog (Full Screen Page)
@Composable
fun CreateGroupDialog(
    contacts: List<Contact>,
    viewModel: ChatViewModel,
    onDismiss: () -> Unit,
    onConfirm: (groupName: String, selected: List<Contact>, groupPhoto: String, groupDescription: String) -> Unit
) {
    val context = LocalContext.current
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val isBn = appLanguage == "bn"
    fun txt(bn: String, en: String) = if (isBn) bn else en

    var step by remember { mutableStateOf(1) }
    var groupName by remember { mutableStateOf("") }
    var groupDescription by remember { mutableStateOf("") }
    var groupPhotoBase64 by remember { mutableStateOf("") }
    val selectedContacts = remember { mutableStateListOf<Contact>() }
    var searchQuery by remember { mutableStateOf("") }
    var hasError by remember { mutableStateOf(false) }

    val filteredContacts = remember(contacts, searchQuery) {
        contacts.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.phone.contains(searchQuery)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bytes = inputStream?.readBytes()
                    if (bytes != null) {
                        groupPhotoBase64 = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    )

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF0B141A)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Custom Header / AppBar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1F2C34))
                        .padding(horizontal = 4.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (step == 2) {
                                step = 1
                            } else {
                                onDismiss()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = txt("নতুন গ্রুপ তৈরি করুন", "Create New Group"),
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (step == 1) {
                                txt("ধাপ ১: সদস্য নির্বাচন করুন", "Step 1: Select group members")
                            } else {
                                txt("ধাপ ২: গ্রুপের তথ্য দিন", "Step 2: Enter group details")
                            },
                            color = WhatsAppTealVal,
                            fontSize = 12.sp
                        )
                    }
                }

                // Beautiful step indicator bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(modifier = Modifier.weight(1f).height(4.dp).background(WhatsAppTealVal))
                    Box(modifier = Modifier.weight(1f).height(4.dp).background(if (step >= 2) WhatsAppTealVal else Color.Gray.copy(alpha = 0.3f)))
                }

                // Main body content with scrolling
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    if (step == 1) {
                        // Step 1: Member Selection
                        Text(
                            text = txt("গ্রুপের সদস্যদের সিলেক্ট করুন:", "Select participants for the group:"),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        // Search box
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text(txt("সদস্য খুঁজুন...", "Search contacts..."), color = Color.Gray, fontSize = 14.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp)) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = WhatsAppTealVal,
                                unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f),
                                focusedContainerColor = Color(0xFF1F2C34).copy(alpha = 0.3f),
                                unfocusedContainerColor = Color(0xFF1F2C34).copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        )

                        // Selected contacts chips list
                        if (selectedContacts.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${txt("নির্বাচিত সদস্য", "Selected Members")} (${selectedContacts.size})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WhatsAppTealVal
                                )
                                TextButton(onClick = { selectedContacts.clear() }) {
                                    Text(txt("সব মুছুন", "Clear All"), color = Color.Red, fontSize = 12.sp)
                                }
                            }
                            androidx.compose.foundation.lazy.LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                            ) {
                                items(selectedContacts) { contact ->
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = WhatsAppTealVal.copy(alpha = 0.15f),
                                        border = BorderStroke(1.dp, WhatsAppTealVal),
                                        modifier = Modifier.clickable { selectedContacts.remove(contact) }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            AvatarView(name = contact.name, base64 = contact.profilePicUri, size = 20)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(contact.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.LightGray, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }
                        }

                        // Contact List
                        Text(
                            text = txt("সকল পরিচিতি", "All Contacts"),
                            fontSize = 12.sp,
                            color = Color.Gray,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (filteredContacts.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(txt("কোন সদস্য পাওয়া যায়নি", "No contacts found"), color = Color.Gray, fontSize = 14.sp)
                                    }
                                }
                            } else {
                                items(filteredContacts) { contact ->
                                    val isChecked = selectedContacts.contains(contact)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                if (isChecked) Color.White.copy(alpha = 0.05f) else Color.Transparent,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                if (isChecked) selectedContacts.remove(contact)
                                                else selectedContacts.add(contact)
                                            }
                                            .padding(vertical = 8.dp, horizontal = 12.dp)
                                    ) {
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = {
                                                if (it == true) selectedContacts.add(contact)
                                                else selectedContacts.remove(contact)
                                            },
                                            colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = WhatsAppGreenVal)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        AvatarView(name = contact.name, base64 = contact.profilePicUri, size = 40)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(contact.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                            Text(contact.phone, color = Color.Gray, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Step 2: Group Details Entry
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Spacer(modifier = Modifier.height(24.dp))

                            // Group Photo Picker Button
                            Box(
                                modifier = Modifier
                                    .size(110.dp)
                                    .clickable {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                contentAlignment = Alignment.BottomEnd
                            ) {
                                if (groupPhotoBase64.isNotEmpty()) {
                                    AvatarView(name = groupName.ifEmpty { "Group" }, base64 = groupPhotoBase64, size = 100)
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(100.dp)
                                            .background(Color.White.copy(alpha = 0.07f), shape = CircleShape)
                                            .border(2.dp, WhatsAppTealVal, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Groups, contentDescription = null, tint = WhatsAppTealVal, modifier = Modifier.size(54.dp))
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(WhatsAppGreenVal, shape = CircleShape)
                                        .border(2.dp, Color(0xFF0B141A), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.PhotoCamera, contentDescription = "Add Photo", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            // Group Name Textfield
                            OutlinedTextField(
                                value = groupName,
                                onValueChange = {
                                    groupName = it
                                    hasError = false
                                },
                                label = { Text(txt("গ্রুপের নাম", "Group Name"), color = if (hasError) Color.Red else WhatsAppTealVal) },
                                placeholder = { Text(txt("যেমন: বন্ধুদের আড্ডা", "e.g., Friends Corner"), color = Color.Gray) },
                                singleLine = true,
                                isError = hasError,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = WhatsAppTealVal,
                                    unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f),
                                    errorBorderColor = Color.Red
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (hasError) {
                                Text(
                                    text = txt("গ্রুপের নাম অবশ্যই দিতে হবে!", "Group name is required!"),
                                    color = Color.Red,
                                    fontSize = 12.sp,
                                    modifier = Modifier.align(Alignment.Start).padding(top = 4.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Group Description (Optional)
                            OutlinedTextField(
                                value = groupDescription,
                                onValueChange = { groupDescription = it },
                                label = { Text(txt("গ্রুপের বর্ণনা (ঐচ্ছিক)", "Group Description (Optional)"), color = WhatsAppTealVal) },
                                placeholder = { Text(txt("গ্রুপের উদ্দেশ্য সম্পর্কে কিছু লিখুন...", "Write something about the group..."), color = Color.Gray) },
                                minLines = 3,
                                maxLines = 5,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = WhatsAppTealVal,
                                    unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Sticky Bottom Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1F2C34))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (step == 2) {
                        Button(
                            onClick = { step = 1 },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray.copy(alpha = 0.2f))
                        ) {
                            Text(txt("পিছনে", "Back"), color = Color.White)
                        }
                    } else {
                        TextButton(onClick = onDismiss) {
                            Text(txt("বাতিল", "Cancel"), color = Color.LightGray)
                        }
                    }

                    if (step == 1) {
                        Button(
                            onClick = { step = 2 },
                            enabled = selectedContacts.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = WhatsAppTealVal,
                                disabledContainerColor = Color.Gray.copy(alpha = 0.2f)
                            )
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(txt("পরবর্তী", "Next"), color = if (selectedContacts.isNotEmpty()) Color.White else Color.Gray)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.ArrowForward,
                                    contentDescription = null,
                                    tint = if (selectedContacts.isNotEmpty()) Color.White else Color.Gray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                if (groupName.trim().isEmpty()) {
                                    hasError = true
                                } else {
                                    onConfirm(groupName, selectedContacts.toList(), groupPhotoBase64, groupDescription)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreenVal)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Done, contentDescription = "Create", tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(txt("গ্রুপ তৈরি করুন", "Create Group"), color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
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

@Composable
fun ChatGptThinkingIndicator() {
    val transition = rememberInfiniteTransition(label = "dots")
    val dot1Alpha by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 600
                0.2f at 0
                1.0f at 200
                0.2f at 400
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot1"
    )
    val dot2Alpha by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 600
                0.2f at 150
                1.0f at 350
                0.2f at 550
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot2"
    )
    val dot3Alpha by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 600
                0.2f at 300
                1.0f at 500
                0.2f at 600
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot3"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    ) {
        val dotColor = Color(0xFF00A884)
        Box(modifier = Modifier.size(8.dp).background(dotColor.copy(alpha = dot1Alpha), CircleShape))
        Box(modifier = Modifier.size(8.dp).background(dotColor.copy(alpha = dot2Alpha), CircleShape))
        Box(modifier = Modifier.size(8.dp).background(dotColor.copy(alpha = dot3Alpha), CircleShape))
    }
}

fun exportChatAsText(context: android.content.Context, contactName: String, messages: List<com.example.data.Message>) {
    try {
        val fileName = "Chat_with_${contactName.replace(" ", "_")}.txt"
        val file = java.io.File(context.cacheDir, fileName)
        val writer = java.io.FileWriter(file)
        writer.use { w ->
            w.write("Chat History with $contactName\n")
            w.write("Generated on: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}\n")
            w.write("========================================\n\n")
            messages.forEach { msg ->
                val sender = if (msg.senderId == msg.receiverId) "System" else msg.senderName.ifEmpty { msg.senderId }
                val time = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(msg.timestamp))
                val content = if (msg.isDeletedForEveryone) "[This message was deleted]" else msg.text
                w.write("[$time] $sender: $content\n")
            }
        }
        
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "com.example.fileprovider", file)
        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Chat History (.txt)"))
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Export failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
    }
}

fun exportChatAsPdf(context: android.content.Context, contactName: String, messages: List<com.example.data.Message>) {
    try {
        val fileName = "Chat_with_${contactName.replace(" ", "_")}.pdf"
        val file = java.io.File(context.cacheDir, fileName)
        
        val pdfDocument = android.graphics.pdf.PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        var pageNumber = 1
        
        var pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas
        
        val paint = android.graphics.Paint()
        val textPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 12f
            isAntiAlias = true
        }
        val headerPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#0F766E")
            textSize = 16f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val subHeaderPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.GRAY
            textSize = 10f
            isAntiAlias = true
        }
        
        var y = 50f
        
        // Write Header
        canvas.drawText("Chat History with $contactName", 40f, y, headerPaint)
        y += 20f
        val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        canvas.drawText("Generated on: $dateStr", 40f, y, subHeaderPaint)
        y += 10f
        canvas.drawLine(40f, y, (pageWidth - 40).toFloat(), y, textPaint)
        y += 30f
        
        messages.forEach { msg ->
            if (y > pageHeight - 60) {
                pdfDocument.finishPage(page)
                pageNumber++
                pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                y = 50f
            }
            
            val sender = if (msg.senderId == msg.receiverId) "System" else msg.senderName.ifEmpty { msg.senderId }
            val time = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(msg.timestamp))
            val content = if (msg.isDeletedForEveryone) "[This message was deleted]" else msg.text
            
            val line = "[$time] $sender: $content"
            
            val maxLineWidth = pageWidth - 80
            var tempLine = line
            while (tempLine.isNotEmpty()) {
                var count = textPaint.breakText(tempLine, true, maxLineWidth.toFloat(), null)
                if (count <= 0) break
                val linePart = tempLine.substring(0, count)
                canvas.drawText(linePart, 40f, y, textPaint)
                y += 18f
                
                if (y > pageHeight - 60) {
                    pdfDocument.finishPage(page)
                    pageNumber++
                    pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    y = 50f
                }
                
                tempLine = tempLine.substring(count)
            }
            y += 8f
        }
        
        pdfDocument.finishPage(page)
        
        val fos = java.io.FileOutputStream(file)
        pdfDocument.writeTo(fos)
        pdfDocument.close()
        fos.close()
        
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "com.example.fileprovider", file)
        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Chat History (.pdf)"))
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Export failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
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
    val isDarkTheme by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val txt = getTranslator(viewModel = viewModel)
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    var showGroupManageDialog by remember { mutableStateOf(false) }
    var showPrivateManageDialog by remember { mutableStateOf(false) }
    val dialogContext = LocalContext.current
    var chatWallpaper by remember(contact.phone, showPrivateManageDialog, showGroupManageDialog) {
        mutableStateOf(
            dialogContext.getSharedPreferences("BartaChatPrefs", Context.MODE_PRIVATE)
                .getString("chat_wallpaper_${contact.phone}", "default") ?: "default"
        )
    }

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
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

    // Attachment States
    var showAttachOptions by remember { mutableStateOf(false) }
    var showSelectImageDialog by remember { mutableStateOf(false) }
    var showSelectVideoDialog by remember { mutableStateOf(false) }
    var selectedImageForPreview by remember { mutableStateOf<String?>(null) }
    var selectedVideoForPlayback by remember { mutableStateOf<String?>(null) }
    var messageToForward by remember { mutableStateOf<Message?>(null) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showGenerateImageDialog by remember { mutableStateOf(false) }
    var imageGenerationPrompt by remember { mutableStateOf("") }
    var isGeneratingImage by remember { mutableStateOf(false) }

    if (messageToForward != null) {
        val contactsList by viewModel.contacts.collectAsStateWithLifecycle()
        var selectedRecipients by remember { mutableStateOf(setOf<Contact>()) }
        var forwardSearchQuery by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { messageToForward = null },
            title = {
                Text(
                    text = txt("বার্তা এগিয়ে দিন...", "Forward message..."),
                    fontWeight = FontWeight.Bold,
                    color = WhatsAppTealVal
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                ) {
                    OutlinedTextField(
                        value = forwardSearchQuery,
                        onValueChange = { forwardSearchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        placeholder = { Text(txt("খুঁজুন...", "Search contacts...")) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp)
                    )

                    val filteredContacts = contactsList.filter {
                        it.name.contains(forwardSearchQuery, ignoreCase = true) ||
                        it.phone.contains(forwardSearchQuery)
                    }

                    if (filteredContacts.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = txt("কোনো পরিচিতি পাওয়া যায়নি", "No contacts found"),
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
                            items(filteredContacts) { contact ->
                                val isSelected = selectedRecipients.contains(contact)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedRecipients = if (isSelected) {
                                                selectedRecipients - contact
                                            } else {
                                                selectedRecipients + contact
                                            }
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AvatarView(name = contact.name, base64 = contact.profilePicUri, size = 40)

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = contact.name,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            color = if (isDarkTheme) Color.White else Color.Black
                                        )
                                        Text(
                                            text = if (contact.isGroup) "গ্রুপ (Group)" else contact.phone,
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                    }

                                    androidx.compose.material3.Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = {
                                            selectedRecipients = if (isSelected) {
                                                selectedRecipients - contact
                                            } else {
                                                selectedRecipients + contact
                                            }
                                        },
                                        colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = WhatsAppGreenVal)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val msg = messageToForward
                        if (msg != null && selectedRecipients.isNotEmpty()) {
                            viewModel.forwardMessage(msg, selectedRecipients.toList())
                            Toast.makeText(
                                dialogContext,
                                txt("বার্তা এগিয়ে দেওয়া হয়েছে!", "Message forwarded successfully!"),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        messageToForward = null
                    },
                    enabled = selectedRecipients.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = WhatsAppTealVal)
                ) {
                    Text(
                        text = if (selectedRecipients.isEmpty()) {
                            txt("এগিয়ে দিন", "Forward")
                        } else {
                            "${txt("এগিয়ে দিন", "Forward")} (${selectedRecipients.size})"
                        },
                        color = Color.White
                    )
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { messageToForward = null }) {
                    Text(txt("বাতিল", "Cancel"))
                }
            }
        )
    }

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

    val wallpaperColor = when (chatWallpaper) {
        "teal" -> Color(0xFF005C53)
        "dark" -> Color(0xFF121212)
        "purple" -> Color(0xFF3F0C52)
        "blue" -> Color(0xFF0C245C)
        "green" -> Color(0xFF094A17)
        "amber" -> Color(0xFF4A3409)
        else -> if (isDarkTheme) PremiumBackgroundDark else WhatsAppGrayBackgroundVal
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(wallpaperColor)
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        if (contact.isGroup) {
                            showGroupManageDialog = true
                        } else {
                            showPrivateManageDialog = true
                        }
                    }
                ) {
                    if (contact.isGroup) {
                        if (contact.profilePicUri.isNotEmpty()) {
                            AvatarView(name = contact.name, base64 = contact.profilePicUri, size = 36)
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color.White.copy(alpha = 0.2f), shape = CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Groups, contentDescription = null, tint = Color.White)
                            }
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
                                text = if (contact.typingStatus.isNotEmpty()) {
                                    if (contact.phone == "01300000000" || contact.isSimulated) {
                                        if (appLanguage == "bn") "ভাবছে... 🤖💭" else "thinking... 🤖💭"
                                    } else {
                                        if (appLanguage == "bn") "লিখছে..." else "typing..."
                                    }
                                } else {
                                    formatLastSeenDynamic(contact.lastSeen, appLanguage)
                                },
                                color = if (contact.typingStatus.isNotEmpty()) WhatsAppGreenVal else Color(0xB3FFFFFF),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            },
            actions = {
                var showExportMenu by remember { mutableStateOf(false) }

                IconButton(
                    onClick = { showExportMenu = true },
                    modifier = Modifier.testTag("export_chat_menu_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "রপ্তানি করুন (Export)",
                        tint = Color.White
                    )
                }

                DropdownMenu(
                    expanded = showExportMenu,
                    onDismissRequest = { showExportMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(txt("টেক্সট ফাইল হিসেবে সেভ করুন (.txt)", "Export as Text (.txt)")) },
                        onClick = {
                            showExportMenu = false
                            exportChatAsText(dialogContext, contact.name, messageList)
                        },
                        leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text(txt("পিডিএফ হিসেবে সেভ করুন (.pdf)", "Export as PDF (.pdf)")) },
                        onClick = {
                            showExportMenu = false
                            exportChatAsPdf(dialogContext, contact.name, messageList)
                        },
                        leadingIcon = { Icon(Icons.Default.Print, contentDescription = null) }
                    )
                }

                IconButton(
                    onClick = {
                        if (contact.isGroup) {
                            showGroupManageDialog = true
                        } else {
                            showPrivateManageDialog = true
                        }
                    },
                    modifier = Modifier.testTag(if (contact.isGroup) "group_manage_button" else "private_manage_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = if (contact.isGroup) "গ্রুপ ম্যানেজ করুন" else "চ্যাট ম্যানেজ করুন",
                        tint = Color.White
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = WhatsAppTealVal)
        )

        if (showGroupManageDialog) {
            ManageGroupDialog(
                contact = contact,
                viewModel = viewModel,
                onDismiss = { 
                    showGroupManageDialog = false
                    chatWallpaper = dialogContext.getSharedPreferences("BartaChatPrefs", Context.MODE_PRIVATE)
                        .getString("chat_wallpaper_${contact.phone}", "default") ?: "default"
                }
            )
        }

        if (showPrivateManageDialog) {
            ManagePrivateChatDialog(
                contact = contact,
                viewModel = viewModel,
                onDismiss = {
                    showPrivateManageDialog = false
                    chatWallpaper = dialogContext.getSharedPreferences("BartaChatPrefs", Context.MODE_PRIVATE)
                        .getString("chat_wallpaper_${contact.phone}", "default") ?: "default"
                },
                onCloseChat = onClose
            )
        }

        val sharedPrefs = remember(contact.phone) {
            dialogContext.getSharedPreferences("BartaChatPrefs", Context.MODE_PRIVATE)
        }

        val groupOwner = remember(contact.phone, messageList) {
            sharedPrefs.getString("group_owner_${contact.phone}", "") ?: ""
        }
        val groupAdminsStr = remember(contact.phone, messageList) {
            sharedPrefs.getString("group_admins_${contact.phone}", "") ?: ""
        }
        val adminsCanPinMessages = remember(contact.phone, messageList) {
            sharedPrefs.getBoolean("group_admins_can_pin_messages_${contact.phone}", true)
        }
        
        val isMeOwner = groupOwner == myNumber
        val isMeAdmin = groupAdminsStr.split(",").contains(myNumber)
        val canMePinMessage = isMeOwner || (isMeAdmin && adminsCanPinMessages)

        // Pinned Message Banner
        val pinnedMessageId = remember(contact.phone, messageList) {
            sharedPrefs.getString("pinned_message_id_${contact.phone}", "") ?: ""
        }
        val pinnedMessage = remember(pinnedMessageId, messageList) {
            if (pinnedMessageId.isNotEmpty()) {
                messageList.find { it.id == pinnedMessageId }
            } else {
                null
            }
        }

        if (pinnedMessage != null) {
            val scope = rememberCoroutineScope()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E293B))
                    .border(BorderStroke(0.5.dp, WhatsAppTealVal.copy(alpha = 0.5f)))
                    .clickable {
                        val index = messageList.indexOfFirst { it.id == pinnedMessage.id }
                        if (index >= 0) {
                            scope.launch {
                                listState.animateScrollToItem(index)
                            }
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "Pinned Message",
                        tint = WhatsAppGreenVal,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (appLanguage == "bn") "পিন করা বার্তা" else "Pinned Message",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = WhatsAppTealVal
                        )
                        val previewText = if (pinnedMessage.isDeletedForEveryone) {
                            if (appLanguage == "bn") "ঐ বার্তাটি মুছে ফেলা হয়েছে" else "This message was deleted"
                        } else {
                            pinnedMessage.text.ifEmpty { "[Media]" }
                        }
                        Text(
                            text = previewText,
                            fontSize = 13.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                if (isMeOwner || isMeAdmin) {
                    IconButton(
                        onClick = {
                            viewModel.unpinMessage(contact.phone)
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Unpin Message",
                            tint = Color.LightGray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
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
                    isDark = isDarkTheme,
                    onDeleteForMe = { viewModel.deleteMessageForMe(msg.id) },
                    onDeleteForEveryone = { viewModel.deleteMessageForEveryone(msg.id) },
                    onEditMessage = { msgId, editedTxt -> viewModel.editMessage(msgId, editedTxt) },
                    onImageClick = { selectedImageForPreview = it },
                    onVideoClick = { selectedVideoForPlayback = it },
                    onForward = { messageToForward = it },
                    onRegenerate = if (contact.phone == "01300000000") { { viewModel.regenerateLastAIResponse() } } else null,
                    isAdminOrOwner = isMeOwner || isMeAdmin,
                    canMePinMessage = canMePinMessage,
                    onPinMessage = { viewModel.pinMessage(contact.phone, msg.id) },
                    onReactToMessage = { emoji -> viewModel.reactToMessage(msg.id, emoji) },
                    myPhone = myNumber ?: ""
                )
            }
            if (contact.typingStatus.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp, 12.dp, 12.dp, 0.dp),
                            color = Color(0xFFF0F2F5),
                            shadowElevation = 1.dp,
                            modifier = Modifier.widthIn(max = 280.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = if (contact.phone == "01300000000" || contact.isSimulated) {
                                        if (appLanguage == "bn") "বার্তা সহকারী ভাবছে... 🤖💭" else "Barta Assistant is thinking... 🤖💭"
                                    } else {
                                        if (appLanguage == "bn") "লিখছে..." else "typing..."
                                    },
                                    color = Color(0xFF54656F),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                if (contact.phone == "01300000000" || contact.isSimulated) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    ChatGptThinkingIndicator()
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(10.dp)) }
        }

        // Quick shortcuts
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFF0F2F5))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("আসসালামু আলাইকুম!", "কেমন আছেন?", "ধন্যবাদ 😊", "গ্রুপ চ্যাট 🟢", "বার্তা").forEach { shortcut ->
                Box(
                    modifier = Modifier
                        .background(if (isDarkTheme) Color(0xFF0F172A) else Color.White, shape = RoundedCornerShape(12.dp))
                        .clickable {
                            inputText += shortcut + " "
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(text = shortcut, fontSize = 11.sp, color = if (isDarkTheme) WhatsAppTealVal else WhatsAppTealDarkVal)
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

        val isBlocked = remember(contact.phone, showPrivateManageDialog) {
            dialogContext.getSharedPreferences("BartaChatPrefs", Context.MODE_PRIVATE)
                .getBoolean("is_blocked_${contact.phone}", false)
        }

        // Message input row
        if (isBlocked) {
            Card(
                colors = CardDefaults.cardColors(containerColor = if (isDarkTheme) Color(0xFF450A0A) else Color(0xFFFEE2E2)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (appLanguage == "bn") "আপনি এই ব্যবহারকারীকে ব্লক করেছেন। বার্তা পাঠাতে তাকে আনব্লক করুন।" else "You have blocked this contact. Unblock to send messages.",
                    color = if (isDarkTheme) Color(0xFFFCA5A5) else Color(0xFF991B1B),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .clickable {
                            showPrivateManageDialog = true
                        }
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFF0F2F5))
                    .padding(8.dp)
                    .navigationBarsPadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Text Input Card
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isDarkTheme) Color(0xFF0F172A) else Color.White),
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
                        
                        // Generate Image (Imagen) Button
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
                            modifier = Modifier.size(28.dp).testTag("generate_image_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Brush,
                                contentDescription = "Generate Image",
                                tint = WhatsAppTealVal
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))

                        // Speech-to-Text Microphone Button
                        IconButton(
                            onClick = {
                                try {
                                    val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault())
                                    }
                                    speechRecognizerLauncher.launch(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(dialogContext, "Speech recognition not available", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.size(28.dp).testTag("chat_mic_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Voice Input",
                                tint = WhatsAppTealVal
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
                            textStyle = androidx.compose.ui.text.TextStyle(color = if (isDarkTheme) Color.White else Color.Black, fontSize = 16.sp),
                            colors = TextFieldDefaults.colors(
                                focusedTextColor = if (isDarkTheme) Color.White else Color.Black,
                                unfocusedTextColor = if (isDarkTheme) Color.White else Color.Black,
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

        if (showEmojiPicker) {
            EmojiPickerComponent(
                isBn = appLanguage == "bn",
                onEmojiSelected = { emoji ->
                    inputText += emoji
                }
            )
        }
    }

    // Modal dialogue selecting caricature picture for mock upload
    if (showGenerateImageDialog) {
        AlertDialog(
            onDismissRequest = { if (!isGeneratingImage) showGenerateImageDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Brush, contentDescription = null, tint = WhatsAppTealVal, modifier = Modifier.size(24.dp))
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
                            CircularProgressIndicator(color = WhatsAppTealVal, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(txt("ইমেজ তৈরি হচ্ছে...", "Generating image..."), fontSize = 12.sp, color = WhatsAppTealVal)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (imageGenerationPrompt.isNotBlank()) {
                            isGeneratingImage = true
                            viewModel.generateAndSendImage(imageGenerationPrompt) { status ->
                                if (status == "Success" || status == "Failed") {
                                    isGeneratingImage = false
                                    showGenerateImageDialog = false
                                    inputText = ""
                                }
                            }
                        }
                    },
                    enabled = !isGeneratingImage && imageGenerationPrompt.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = WhatsAppTealVal)
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

    if (showSelectImageDialog) {
        AlertDialog(
            onDismissRequest = { showSelectImageDialog = false },
            title = { Text("ছবি ও মিডিয়া পাঠানো", fontWeight = FontWeight.Bold, color = WhatsAppTealVal) },
            text = {
                Column {
                    Text("নিচের যেকোনো একটি ডামি ছবি স্পর্শ করুন অথবা গ্যালারি বা ক্যামেরা ব্যবহার করুনঃ", fontSize = 12.sp, color = Color.Gray)
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

    // Modal dialogue selecting video capture clip for mock Firebase storage upload
    if (showSelectVideoDialog) {
        AlertDialog(
            onDismissRequest = { showSelectVideoDialog = false },
            title = { Text("ভিডিও পাঠানো (Gallery / Storage)", fontWeight = FontWeight.Bold, color = WhatsAppTealVal) },
            text = {
                Column {
                    Text("আপনার ফোনের গ্যালারি থেকে যেকোনো ভিডিও নির্বাচন করে পাঠান:", fontSize = 12.sp, color = Color.Gray)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatMsgBubble(
    message: Message,
    isMe: Boolean,
    isGroupMsg: Boolean,
    isBengali: Boolean,
    isDark: Boolean,
    onDeleteForMe: () -> Unit,
    onDeleteForEveryone: () -> Unit,
    onEditMessage: (String, String) -> Unit,
    onImageClick: (String) -> Unit,
    onVideoClick: (String) -> Unit,
    onForward: (Message) -> Unit,
    onRegenerate: (() -> Unit)? = null,
    isAdminOrOwner: Boolean = false,
    canMePinMessage: Boolean = false,
    onPinMessage: (() -> Unit)? = null,
    onReactToMessage: ((String) -> Unit)? = null,
    myPhone: String = ""
) {
    val layoutAlign = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
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
    var showReactionSelector by remember { mutableStateOf(false) }
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
                .combinedClickable(
                    onClick = {
                        // Open delete Dialog on click of safety
                        showDeleteConfirmationDialog = true
                    },
                    onLongClick = {
                        showReactionSelector = true
                    }
                )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                if (message.isForwarded) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Reply,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isBengali) "এগিয়ে দেওয়া হয়েছে" else "Forwarded",
                            fontSize = 10.sp,
                            color = Color.Gray,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                }

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
                    val deletionText = if (message.text.startsWith("Deleted by:")) {
                        val deleter = message.text.removePrefix("Deleted by:").trim()
                        if (isBengali) "🚫 এই বার্তাটি মুছে ফেলা হয়েছে ($deleter দ্বারা)" else "🚫 This message was deleted (by $deleter)"
                    } else {
                        if (isBengali) "🚫 এই বার্তাটি মুছে ফেলা হয়েছে" else "🚫 This message was deleted"
                    }
                    Text(
                        text = deletionText,
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
                                .combinedClickable(
                                    onClick = { onImageClick(message.mediaUrl) },
                                    onLongClick = { showDeleteConfirmationDialog = true }
                                )
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
                                .combinedClickable(
                                    onClick = { onVideoClick(message.mediaUrl) },
                                    onLongClick = { showDeleteConfirmationDialog = true }
                                ),
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

                // Reactions display row
                if (message.reactions.isNotEmpty()) {
                    val reactionsMap = message.reactions.split(",").mapNotNull {
                        val parts = it.split(":", limit = 2)
                        if (parts.size == 2) parts[0] to parts[1] else null
                    }.toMap()

                    if (reactionsMap.isNotEmpty()) {
                        val emojiCounts = reactionsMap.values.groupingBy { it }.eachCount()
                        Row(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .background(
                                    if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.05f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            emojiCounts.forEach { (emoji, count) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(text = emoji, fontSize = 12.sp)
                                    if (count > 1) {
                                        Text(
                                            text = count.toString(),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = textColor
                                        )
                                    }
                                }
                            }
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
                        if (message.isPending) {
                            Icon(
                                imageVector = Icons.Default.Pause,
                                contentDescription = "Pending status",
                                tint = Color.Gray,
                                modifier = Modifier.size(14.dp)
                            )
                        } else {
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

                    // Option 2: Delete For Everyone (Only sender has full permission constraints, or Admins/Owners for moderation)
                    if ((isMe || isAdminOrOwner) && !message.isDeletedForEveryone) {
                        Button(
                            modifier = Modifier.fillMaxWidth().testTag("delete_for_everyone_button"),
                            onClick = {
                                onDeleteForEveryone()
                                showDeleteConfirmationDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) {
                            val label = if (isMe) {
                                txt("সবার জন্য মুছুন", "Delete for Everyone")
                            } else {
                                txt("বার্তা মডারেট করুন (সবার জন্য মুছুন) ⚠️", "Moderate Message (Delete for Everyone) ⚠️")
                            }
                            Text(label, color = Color.White)
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

                    // Option 3.5: Pin Message (Admins/Owners with permission, in group chats)
                    if (isGroupMsg && !message.isDeletedForEveryone && canMePinMessage && onPinMessage != null) {
                        Button(
                            modifier = Modifier.fillMaxWidth().testTag("pin_message_button"),
                            onClick = {
                                onPinMessage()
                                showDeleteConfirmationDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B))
                        ) {
                            Text(txt("বার্তা পিন করুন 📌", "Pin Message 📌"), color = Color.White)
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

                    // Option 6: Forward Message (Available for all non-deleted messages)
                    if (!message.isDeletedForEveryone) {
                        Button(
                            modifier = Modifier.fillMaxWidth().testTag("forward_message_button"),
                            onClick = {
                                showDeleteConfirmationDialog = false
                                onForward(message)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F9D58))
                        ) {
                            Text(txt("বার্তা এগিয়ে দিন (Forward)", "Forward Message"), color = Color.White)
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

    if (showReactionSelector) {
        AlertDialog(
            onDismissRequest = { showReactionSelector = false },
            title = { Text(txt("প্রতিক্রিয়া জানান", "Add Reaction"), fontWeight = FontWeight.Bold, color = WhatsAppTealVal) },
            text = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val emojis = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")
                    emojis.forEach { emoji ->
                        Text(
                            text = emoji,
                            fontSize = 28.sp,
                            modifier = Modifier
                                .clickable {
                                    onReactToMessage?.invoke(emoji)
                                    showReactionSelector = false
                                }
                                .padding(4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showReactionSelector = false }) {
                    Text(txt("বাতিল করুন", "Cancel"))
                }
            }
        )
    }
}

@Composable
fun AvatarView(
    name: String,
    base64: String,
    size: Int = 44,
    phone: String? = null
) {
    val isBot = name.contains("Bot") || name.contains("सहকারী") || name.contains("Assistant") || name.contains("chatbot") || base64 == "bot_logo" || phone == "01300000000" || name.contains("বট")

    if (isBot) {
        Box(
            modifier = Modifier
                .size(size.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF8B5CF6), // Deep violet / Indigo
                            Color(0xFF3B82F6), // Electric Blue
                            Color(0xFF06B6D4)  // Radiant Cyan
                        )
                    )
                )
                .border(1.5.dp, Color(0xFF22D3EE), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.SmartToy,
                contentDescription = name,
                tint = Color.White,
                modifier = Modifier.size((size * 0.58f).dp)
            )
        }
    } else if (base64.isNotEmpty()) {
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
    val nonGroupContacts = allContacts.filter { !it.isGroup && it.phone != "01300000000" }
    
    val myNumberState by viewModel.myNumber.collectAsStateWithLifecycle()
    val myNumber = myNumberState ?: ""
    val activeMessages by viewModel.activeMessages.collectAsStateWithLifecycle()
    
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val isBn = appLanguage == "bn"
    fun txt(bn: String, en: String) = if (isBn) bn else en

    var groupName by remember { mutableStateOf(contact.name) }
    var groupDescription by remember {
        mutableStateOf(
            context.getSharedPreferences("BartaChatPrefs", Context.MODE_PRIVATE)
                .getString("group_description_${contact.phone}", "") ?: ""
        )
    }
    
    val selectedParticipantPhones = remember {
        mutableStateListOf<String>().apply {
            addAll(contact.groupParticipants.split(",").filter { it.isNotEmpty() })
        }
    }
    
    val sharedPrefs = remember { context.getSharedPreferences("BartaChatPrefs", Context.MODE_PRIVATE) }
    
    var groupOwner by remember(contact.phone) {
        mutableStateOf(
            sharedPrefs.getString("group_owner_${contact.phone}", "")?.ifEmpty {
                sharedPrefs.getString("group_creator_${contact.phone}", "")?.ifEmpty {
                    contact.groupParticipants.split(",").firstOrNull() ?: ""
                } ?: ""
            } ?: ""
        )
    }

    var groupAdminsStr by remember(contact.phone) {
        mutableStateOf(sharedPrefs.getString("group_admins_${contact.phone}", "") ?: "")
    }

    var adminsCanEditInfo by remember(contact.phone) {
        mutableStateOf(sharedPrefs.getBoolean("group_admins_can_edit_info_${contact.phone}", true))
    }

    var adminsCanPinMessages by remember(contact.phone) {
        mutableStateOf(sharedPrefs.getBoolean("group_admins_can_pin_messages_${contact.phone}", true))
    }

    var membersCanEditInfo by remember(contact.phone) {
        mutableStateOf(sharedPrefs.getBoolean("group_members_can_edit_info_${contact.phone}", false))
    }

    val isMeOwner = groupOwner == myNumber
    val isMeAdmin = (groupOwner == myNumber) || groupAdminsStr.split(",").contains(myNumber)
    
    // Check if current user has permission to edit group info
    val canIEditGroupInfo = isMeOwner || (isMeAdmin && adminsCanEditInfo) || membersCanEditInfo
    val canIAddMembers = isMeAdmin
    
    var activeMainTab by remember { mutableStateOf(1) } // 1: Info/Members, 2: Shared Content, 3: Group Settings
    var activeSharedSubTab by remember { mutableStateOf(1) } // 1: Media, 2: Files, 3: Links

    // Wallpaper and Mute States
    var isMuted by remember { mutableStateOf(sharedPrefs.getBoolean("mute_notifications_${contact.phone}", false)) }
    var currentWallpaper by remember { mutableStateOf(sharedPrefs.getString("chat_wallpaper_${contact.phone}", "default") ?: "default") }

    var groupPhotoBase64 by remember { mutableStateOf(contact.profilePicUri) }
    var showAddMemberDialog by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }

    // Confirmation Dialog States
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var pendingRemovePhone by remember { mutableStateOf<String?>(null) }
    var clickedParticipantPhone by remember { mutableStateOf<String?>(null) }

    // Media & Link Extraction
    val sharedMedia = remember(activeMessages) {
        activeMessages.filter { it.mediaUrl != null && (it.mediaType == "image" || it.mediaType == "video") }
    }
    val sharedFiles = remember(activeMessages) {
        activeMessages.filter { it.mediaUrl != null && it.mediaType != "image" && it.mediaType != "video" }
    }
    val sharedLinks = remember(activeMessages) {
        activeMessages.filter { it.text.contains("http://", ignoreCase = true) || it.text.contains("https://", ignoreCase = true) }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bytes = inputStream?.readBytes()
                    if (bytes != null) {
                        val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
                        groupPhotoBase64 = base64
                        viewModel.updateGroup(
                            groupId = contact.phone,
                            newName = groupName,
                            newParticipants = selectedParticipantPhones.toList(),
                            newPhoto = base64,
                            newDescription = groupDescription
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    )

    BackHandler(onBack = onDismiss)
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0B141A)
    ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header / Top App Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(WhatsAppTealVal)
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = txt("পিছনে", "Back"),
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = txt("গ্রুপের তথ্য ও সেটিংস", "Group Info & Settings"),
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 18.sp
                        )
                        Text(
                            text = groupName,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    // Navigation Tabs
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf(
                            Triple(1, txt("সদস্যবৃন্দ", "Members"), Icons.Default.People),
                            Triple(2, txt("মিডিয়া ও লিংক", "Shared"), Icons.Default.Folder),
                            Triple(3, txt("সেটিংস", "Settings"), Icons.Default.Settings)
                        ).forEach { (tabId, tabName, icon) ->
                            val isSelected = activeMainTab == tabId
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable { activeMainTab = tabId }
                                    .padding(vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = tabName,
                                    tint = if (isSelected) WhatsAppTealVal else Color.Gray,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = tabName,
                                    color = if (isSelected) Color.White else Color.Gray,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .width(42.dp)
                                        .height(2.dp)
                                        .background(if (isSelected) WhatsAppTealVal else Color.Transparent)
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f), modifier = Modifier.padding(bottom = 16.dp))

                    when (activeMainTab) {
                    1 -> {
                        // TAB 1: MEMBERS & INFO
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Large Group Avatar
                            Box(
                                modifier = Modifier
                                    .size(92.dp)
                                    .clickable(enabled = canIEditGroupInfo) {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                contentAlignment = Alignment.BottomEnd
                            ) {
                                if (groupPhotoBase64.isNotEmpty()) {
                                    AvatarView(name = groupName, base64 = groupPhotoBase64, size = 88)
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(88.dp)
                                            .background(Color.White.copy(alpha = 0.1f), shape = CircleShape)
                                            .border(2.dp, WhatsAppTealVal, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Groups, contentDescription = null, tint = WhatsAppTealVal, modifier = Modifier.size(46.dp))
                                    }
                                }
                                if (canIEditGroupInfo) {
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .background(WhatsAppGreenVal, shape = CircleShape)
                                            .border(1.5.dp, Color.Black, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.PhotoCamera, contentDescription = "Edit Photo", tint = Color.White, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Text(groupName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            if (groupDescription.isNotEmpty()) {
                                Text(
                                    groupDescription,
                                    fontSize = 13.sp,
                                    color = Color.LightGray,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            } else {
                                Text(
                                    txt("কোনো বিবরণ যোগ করা হয়নি।", "No description added yet."),
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                                )
                            }

                            // Dynamic Actions Row (Mute, Wallpaper Selector)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White.copy(alpha = 0.05f), shape = RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isMuted) Icons.Default.NotificationsOff else Icons.Default.NotificationsActive,
                                        contentDescription = "Mute",
                                        tint = if (isMuted) Color.LightGray else WhatsAppTealVal,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(txt("মিউট নোটিফিকেশন", "Mute Notifications"), fontSize = 13.sp, color = Color.White)
                                }
                                Switch(
                                    checked = isMuted,
                                    onCheckedChange = {
                                        isMuted = it
                                        sharedPrefs.edit().putBoolean("mute_notifications_${contact.phone}", it).apply()
                                    },
                                    colors = androidx.compose.material3.SwitchDefaults.colors(checkedThumbColor = WhatsAppGreenVal)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Custom Wallpaper Selector Row
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White.copy(alpha = 0.05f), shape = RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                            ) {
                                Text(txt("চ্যাট ওয়ালপেপার থিম:", "Chat Wallpaper Theme:"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WhatsAppTealVal)
                                Spacer(modifier = Modifier.height(8.dp))
                                androidx.compose.foundation.lazy.LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val wallpaperThemes = listOf(
                                        Pair("default", Color.DarkGray),
                                        Pair("teal", Color(0xFF005C53)),
                                        Pair("dark", Color(0xFF121212)),
                                        Pair("purple", Color(0xFF3F0C52)),
                                        Pair("blue", Color(0xFF0C245C)),
                                        Pair("green", Color(0xFF094A17)),
                                        Pair("amber", Color(0xFF4A3409))
                                    )
                                    items(wallpaperThemes) { (themeName, color) ->
                                        val isCurrent = currentWallpaper == themeName
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(color, shape = CircleShape)
                                                .border(if (isCurrent) 2.5.dp else 1.dp, if (isCurrent) WhatsAppGreenVal else Color.Transparent, CircleShape)
                                                .clickable {
                                                    currentWallpaper = themeName
                                                    sharedPrefs.edit().putString("chat_wallpaper_${contact.phone}", themeName).apply()
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isCurrent) {
                                                Icon(Icons.Default.Check, contentDescription = "Active", tint = Color.White, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Member List Title
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = txt("গ্রুপ সদস্যসমূহ (${selectedParticipantPhones.size} জন):", "Group Members (${selectedParticipantPhones.size}):"),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color.Gray
                                )
                                if (canIAddMembers) {
                                    TextButton(onClick = { showAddMemberDialog = true }) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = WhatsAppGreenVal)
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text(txt("সদস্য যোগ করুন", "Add Member"), color = WhatsAppGreenVal, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Sort: Owner first, then Admins, then regular members
                            val sortedParticipantPhones = remember(selectedParticipantPhones.toList(), groupOwner, groupAdminsStr) {
                                selectedParticipantPhones.toList().sortedWith { p1, p2 ->
                                    when {
                                        p1 == groupOwner -> -1
                                        p2 == groupOwner -> 1
                                        groupAdminsStr.split(",").contains(p1) && !groupAdminsStr.split(",").contains(p2) -> -1
                                        !groupAdminsStr.split(",").contains(p1) && groupAdminsStr.split(",").contains(p2) -> 1
                                        else -> p1.compareTo(p2)
                                    }
                                }
                            }

                            // Render Member List Items
                            sortedParticipantPhones.forEach { participantPhone ->
                                val memberContact = allContacts.find { it.phone == participantPhone }
                                val memberName = memberContact?.name ?: participantPhone
                                val isMemberMe = participantPhone == myNumber

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(8.dp))
                                        .then(
                                            if (!isMemberMe) {
                                                Modifier.clickable { clickedParticipantPhone = participantPhone }
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .padding(8.dp)
                                ) {
                                    AvatarView(name = memberName, base64 = memberContact?.profilePicUri ?: "", size = 32)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(memberName, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.padding(top = 2.dp)
                                        ) {
                                            val isParticipantAdmin = (participantPhone == groupOwner) || groupAdminsStr.split(",").contains(participantPhone)
                                            when {
                                                isParticipantAdmin -> {
                                                    Surface(
                                                        color = WhatsAppTealVal.copy(alpha = 0.15f),
                                                        border = BorderStroke(1.dp, WhatsAppTealVal),
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        Text(
                                                            text = txt("এডমিন", "Admin"),
                                                            color = WhatsAppTealVal,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                                else -> {
                                                    Surface(
                                                        color = Color.Gray.copy(alpha = 0.15f),
                                                        border = BorderStroke(1.dp, Color.Gray),
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        Text(
                                                            text = txt("সদস্য", "Member"),
                                                            color = Color.LightGray,
                                                            fontSize = 10.sp,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            
                                            if (isMemberMe) {
                                                Text(txt("(আপনি)", "(You)"), color = Color.Gray, fontSize = 10.sp)
                                            } else {
                                                Text(participantPhone, color = Color.Gray, fontSize = 10.sp)
                                            }
                                        }
                                    }
                                    
                                    // Owner/Admin Context Options for this Member
                                    val isTargetOwner = participantPhone == groupOwner
                                    val isTargetAdmin = groupAdminsStr.split(",").contains(participantPhone)
                                    val canIManageTarget = isMeAdmin && !isTargetOwner

                                    if (canIManageTarget && !isMemberMe) {
                                        var showMemberMenu by remember { mutableStateOf(false) }
                                        Box {
                                            IconButton(onClick = { showMemberMenu = true }, modifier = Modifier.size(24.dp)) {
                                                Icon(Icons.Default.MoreVert, contentDescription = "Member Actions", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                                            }
                                            DropdownMenu(
                                                expanded = showMemberMenu,
                                                onDismissRequest = { showMemberMenu = false }
                                            ) {
                                                if (isTargetAdmin) {
                                                    DropdownMenuItem(
                                                        text = { Text(txt("এডমিন পদ থেকে সরান", "Demote Admin"), fontSize = 13.sp) },
                                                        onClick = {
                                                            showMemberMenu = false
                                                            val updatedAdmins = groupAdminsStr.split(",")
                                                                .filter { it.isNotEmpty() && it != participantPhone }
                                                                .joinToString(",")
                                                            groupAdminsStr = updatedAdmins
                                                            sharedPrefs.edit().putString("group_admins_${contact.phone}", updatedAdmins).apply()
                                                            viewModel.updateGroup(
                                                                groupId = contact.phone,
                                                                newName = groupName,
                                                                newParticipants = selectedParticipantPhones.toList(),
                                                                newPhoto = groupPhotoBase64,
                                                                newDescription = groupDescription,
                                                                admins = updatedAdmins
                                                            )
                                                        }
                                                    )
                                                } else {
                                                    DropdownMenuItem(
                                                        text = { Text(txt("এডমিন বানান", "Make Admin"), fontSize = 13.sp) },
                                                        onClick = {
                                                            showMemberMenu = false
                                                            val updatedAdmins = (groupAdminsStr.split(",").filter { it.isNotEmpty() } + participantPhone).distinct().joinToString(",")
                                                            groupAdminsStr = updatedAdmins
                                                            sharedPrefs.edit().putString("group_admins_${contact.phone}", updatedAdmins).apply()
                                                            viewModel.updateGroup(
                                                                groupId = contact.phone,
                                                                newName = groupName,
                                                                newParticipants = selectedParticipantPhones.toList(),
                                                                newPhoto = groupPhotoBase64,
                                                                newDescription = groupDescription,
                                                                admins = updatedAdmins
                                                            )
                                                        }
                                                    )
                                                }
                                                
                                                DropdownMenuItem(
                                                    text = { Text(txt("গ্রুপ থেকে সরান", "Remove from Group"), color = Color.Red, fontSize = 13.sp) },
                                                    onClick = {
                                                        showMemberMenu = false
                                                        pendingRemovePhone = participantPhone
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        // TAB 2: SHARED CONTENT (MEDIA, FILES, LINKS)
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Sub Tab bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                listOf(
                                    Pair(1, txt("মিডিয়া", "Media") + " (${sharedMedia.size})"),
                                    Pair(2, txt("ফাইল", "Files") + " (${sharedFiles.size})"),
                                    Pair(3, txt("লিংক", "Links") + " (${sharedLinks.size})")
                                ).forEach { (subId, label) ->
                                    val isSelected = activeSharedSubTab == subId
                                    Button(
                                        onClick = { activeSharedSubTab = subId },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isSelected) WhatsAppTealVal else Color.White.copy(alpha = 0.05f)
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text(label, fontSize = 11.sp, color = if (isSelected) Color.White else Color.Gray)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Sub Tab Content
                            when (activeSharedSubTab) {
                                1 -> {
                                    if (sharedMedia.isEmpty()) {
                                        Text(txt("কোনো শেয়ারকৃত ফটো বা ভিডিও নেই", "No shared photos or videos found"), color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(vertical = 12.dp))
                                    } else {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            sharedMedia.take(12).forEach { msg ->
                                                Card(
                                                    modifier = Modifier
                                                        .size(72.dp)
                                                        .clickable { /* Handle preview click if needed */ },
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                                        val bitmap = remember(msg.mediaUrl) {
                                                            try {
                                                                val decodedBytes = android.util.Base64.decode(msg.mediaUrl, android.util.Base64.DEFAULT)
                                                                android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                                                            } catch (e: Exception) {
                                                                null
                                                            }
                                                        }
                                                        if (bitmap != null) {
                                                            androidx.compose.foundation.Image(
                                                                bitmap = bitmap.asImageBitmap(),
                                                                contentDescription = "Shared Image",
                                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                                                modifier = Modifier.fillMaxSize()
                                                            )
                                                        } else {
                                                            Icon(Icons.Default.Image, contentDescription = null, tint = Color.Gray)
                                                        }
                                                        if (msg.mediaType == "video") {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(24.dp)
                                                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(14.dp))
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                2 -> {
                                    if (sharedFiles.isEmpty()) {
                                        Text(txt("কোনো শেয়ারকৃত ফাইল পাওয়া যায়নি", "No shared files found"), color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(vertical = 12.dp))
                                    } else {
                                        sharedFiles.forEach { msg ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp)
                                                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                                                    .padding(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.AttachFile, contentDescription = "File", tint = WhatsAppTealVal, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    msg.text.ifEmpty { "attachment_file" },
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                                3 -> {
                                    if (sharedLinks.isEmpty()) {
                                        Text(txt("কোনো লিংক সম্বলিত বার্তা নেই", "No links found in messages"), color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(vertical = 12.dp))
                                    } else {
                                        sharedLinks.forEach { msg ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp)
                                                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                                                    .padding(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.Link, contentDescription = "Link", tint = WhatsAppGreenVal, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    msg.text,
                                                    color = Color(0xFF0ea5e9),
                                                    fontSize = 12.sp,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.clickable {
                                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                        val clip = android.content.ClipData.newPlainText("Barta Link", msg.text)
                                                        clipboard.setPrimaryClip(clip)
                                                        Toast.makeText(context, txt("লিংক কপি করা হয়েছে!", "Link copied!"), Toast.LENGTH_SHORT).show()
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    3 -> {
                        // TAB 3: SETTINGS (ADMIN AND GROUP GENERAL SETTINGS)
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (canIEditGroupInfo) {
                                Text(txt("গ্রুপের তথ্য পরিবর্তন করুন:", "Modify Group Details:"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WhatsAppTealVal)
                                Spacer(modifier = Modifier.height(10.dp))
                                
                                OutlinedTextField(
                                    value = groupName,
                                    onValueChange = {
                                        groupName = it
                                        hasError = false
                                        viewModel.updateGroup(
                                            groupId = contact.phone,
                                            newName = it,
                                            newParticipants = selectedParticipantPhones.toList(),
                                            newPhoto = groupPhotoBase64,
                                            newDescription = groupDescription
                                        )
                                    },
                                    label = { Text(txt("গ্রুপের নাম", "Group Name")) },
                                    singleLine = true,
                                    isError = hasError,
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

                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedTextField(
                                    value = groupDescription,
                                    onValueChange = {
                                        groupDescription = it
                                        viewModel.updateGroup(
                                            groupId = contact.phone,
                                            newName = groupName,
                                            newParticipants = selectedParticipantPhones.toList(),
                                            newPhoto = groupPhotoBase64,
                                            newDescription = it
                                        )
                                    },
                                    label = { Text(txt("গ্রুপের বর্ণনা", "Group Description")) },
                                    minLines = 2,
                                    maxLines = 3,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedLabelColor = WhatsAppTealVal
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                            }

                            // Admin permission controls
                            if (isMeAdmin) {
                                Text(txt("গ্রুপের পারমিশন ও সেটিংস (এডমিন কন্ট্রোল):", "Permissions & Settings (Admin Control):"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WhatsAppTealVal)
                                Spacer(modifier = Modifier.height(10.dp))
                                
                                // Switch 1: Admins can edit info
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(txt("এডমিনরা গ্রুপ তথ্য পরিবর্তন করতে পারবে", "Admins can edit group info"), fontSize = 12.sp, color = Color.White)
                                    Switch(
                                        checked = adminsCanEditInfo,
                                        onCheckedChange = {
                                            adminsCanEditInfo = it
                                            sharedPrefs.edit().putBoolean("group_admins_can_edit_info_${contact.phone}", it).apply()
                                            viewModel.updateGroup(
                                                groupId = contact.phone,
                                                newName = groupName,
                                                newParticipants = selectedParticipantPhones.toList(),
                                                newPhoto = groupPhotoBase64,
                                                newDescription = groupDescription,
                                                adminsCanEditInfo = it
                                            )
                                        },
                                        colors = androidx.compose.material3.SwitchDefaults.colors(checkedThumbColor = WhatsAppGreenVal)
                                    )
                                }

                                // Switch 2: Admins can pin messages
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(txt("এডমিনরা বার্তা পিন (Pin) করতে পারবে", "Admins can pin messages"), fontSize = 12.sp, color = Color.White)
                                    Switch(
                                        checked = adminsCanPinMessages,
                                        onCheckedChange = {
                                            adminsCanPinMessages = it
                                            sharedPrefs.edit().putBoolean("group_admins_can_pin_messages_${contact.phone}", it).apply()
                                            viewModel.updateGroup(
                                                groupId = contact.phone,
                                                newName = groupName,
                                                newParticipants = selectedParticipantPhones.toList(),
                                                newPhoto = groupPhotoBase64,
                                                newDescription = groupDescription,
                                                adminsCanPinMessages = it
                                            )
                                        },
                                        colors = androidx.compose.material3.SwitchDefaults.colors(checkedThumbColor = WhatsAppGreenVal)
                                    )
                                }

                                // Switch 3: Members can edit info
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(txt("সদস্যরা গ্রুপ তথ্য পরিবর্তন করতে পারবে", "Members can edit group info"), fontSize = 12.sp, color = Color.White)
                                    Switch(
                                        checked = membersCanEditInfo,
                                        onCheckedChange = {
                                            membersCanEditInfo = it
                                            sharedPrefs.edit().putBoolean("group_members_can_edit_info_${contact.phone}", it).apply()
                                            viewModel.updateGroup(
                                                groupId = contact.phone,
                                                newName = groupName,
                                                newParticipants = selectedParticipantPhones.toList(),
                                                newPhoto = groupPhotoBase64,
                                                newDescription = groupDescription,
                                                membersCanEditInfo = it
                                            )
                                        },
                                        colors = androidx.compose.material3.SwitchDefaults.colors(checkedThumbColor = WhatsAppGreenVal)
                                    )
                                }
                                Spacer(modifier = Modifier.height(20.dp))
                            }

                            // LEAVE GROUP BUTTON
                            Button(
                                onClick = {
                                    viewModel.leaveGroup(contact.phone)
                                    onDismiss()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.ExitToApp, contentDescription = "Leave", tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(txt("গ্রুপ ত্যাগ করুন 👋", "Leave Group 👋"), color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }

                            // DELETE GROUP BUTTON (ADMIN ONLY)
                            if (isMeAdmin) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        showDeleteConfirmDialog = true
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                                    border = BorderStroke(1.dp, Color(0xFFDC2626)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(txt("গ্রুপ ডিলিট করুন (শুধুমাত্র এডমিন)", "Delete Group (Admins Only)"), color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // INNER ADD MEMBER DIALOG
    if (showAddMemberDialog) {
        val nonGroupMembers = nonGroupContacts.filter { !selectedParticipantPhones.contains(it.phone) }
        AlertDialog(
            onDismissRequest = { showAddMemberDialog = false },
            title = { Text(txt("সদস্য যুক্ত করুন", "Add Members to Group"), fontWeight = FontWeight.Bold, color = WhatsAppTealVal) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (nonGroupMembers.isEmpty()) {
                        Text(txt("যুক্ত করার মতো কোনো নতুন কন্টাক্ট নেই", "No new contacts available to add"), color = Color.Gray, fontSize = 13.sp)
                    } else {
                        LazyColumn(modifier = Modifier.height(200.dp)) {
                            items(nonGroupMembers) { c ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedParticipantPhones.add(c.phone)
                                            viewModel.updateGroup(
                                                groupId = contact.phone,
                                                newName = groupName,
                                                newParticipants = selectedParticipantPhones.toList(),
                                                newPhoto = groupPhotoBase64,
                                                newDescription = groupDescription
                                            )
                                            showAddMemberDialog = false
                                        }
                                        .padding(vertical = 6.dp)
                                ) {
                                    AvatarView(name = c.name, base64 = c.profilePicUri, size = 32)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(c.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        Text(c.phone, color = Color.Gray, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddMemberDialog = false }) {
                    Text(txt("বন্ধ করুন", "Cancel"), color = Color.Gray)
                }
            }
        )
    }

    // 1. Delete Group Confirm Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text(txt("গ্রুপ ডিলিট নিশ্চিত করুন", "Confirm Group Deletion"), color = Color.Red, fontWeight = FontWeight.Bold) },
            text = { Text(txt("আপনি কি নিশ্চিতভাবে এই গ্রুপটি স্থায়ীভাবে ডিলিট করতে চান? এই কাজটি আর ফিরিয়ে আনা যাবে না।", "Are you sure you want to permanently delete this group? This action cannot be undone.")) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        viewModel.deleteGroup(contact.phone)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text(txt("ডিলিট করুন", "Delete"), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(txt("বাতিল", "Cancel"), color = Color.Gray)
                }
            }
        )
    }

    // 2. Remove Member Confirm Dialog
    if (pendingRemovePhone != null) {
        val removePhone = pendingRemovePhone!!
        val removeContact = allContacts.find { it.phone == removePhone }
        val removeName = removeContact?.name ?: removePhone
        AlertDialog(
            onDismissRequest = { pendingRemovePhone = null },
            title = { Text(txt("সদস্য অপসারণ নিশ্চিত করুন", "Confirm Member Removal"), color = Color.Red, fontWeight = FontWeight.Bold) },
            text = { Text(txt("আপনি কি নিশ্চিতভাবে $removeName কে গ্রুপ থেকে অপসারণ করতে চান?", "Are you sure you want to remove $removeName from the group?")) },
            confirmButton = {
                Button(
                    onClick = {
                        pendingRemovePhone = null
                        selectedParticipantPhones.remove(removePhone)
                        viewModel.updateGroup(
                            groupId = contact.phone,
                            newName = groupName,
                            newParticipants = selectedParticipantPhones.toList(),
                            newPhoto = groupPhotoBase64,
                            newDescription = groupDescription
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text(txt("অপসারণ করুন", "Remove"), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemovePhone = null }) {
                    Text(txt("বাতিল", "Cancel"), color = Color.Gray)
                }
            }
        )
    }

    // 3. Contact Member Direct Chat Option Dialog
    if (clickedParticipantPhone != null) {
        val targetPhone = clickedParticipantPhone!!
        val targetContact = allContacts.find { it.phone == targetPhone }
        val targetName = targetContact?.name ?: targetPhone
        
        AlertDialog(
            onDismissRequest = { clickedParticipantPhone = null },
            title = {
                Text(
                    text = txt("সদস্যের সাথে যোগাযোগ", "Contact Member"),
                    color = WhatsAppTealVal,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AvatarView(name = targetName, base64 = targetContact?.profilePicUri ?: "", size = 64)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = targetName,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = targetPhone,
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = txt(
                            "আপনি কি $targetName-এর সাথে সরাসরি চ্যাট বা বার্তা আদানপ্রদান করতে চান?",
                            "Do you want to start a direct message or chat with $targetName?"
                        ),
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        clickedParticipantPhone = null
                        val contactToOpen = targetContact ?: Contact(
                            phone = targetPhone,
                            name = targetName,
                            profilePicUri = ""
                        )
                        viewModel.insertContactAndSelect(contactToOpen)
                        onDismiss() // Close the group management dialog
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WhatsAppTealVal)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Message, contentDescription = "Message", tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(txt("বার্তা পাঠান", "Send Message"), color = Color.White)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { clickedParticipantPhone = null }) {
                    Text(txt("বাতিল", "Cancel"), color = Color.Gray)
                }
            }
        )
    }
}

@Composable
fun ManagePrivateChatDialog(
    contact: Contact,
    viewModel: ChatViewModel,
    onDismiss: () -> Unit,
    onCloseChat: () -> Unit
) {
    val context = LocalContext.current
    val allContacts by viewModel.contacts.collectAsStateWithLifecycle()
    val activeMessages by viewModel.activeMessages.collectAsStateWithLifecycle()
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val isBn = appLanguage == "bn"
    fun txt(bn: String, en: String) = if (isBn) bn else en

    val sharedPrefs = remember { context.getSharedPreferences("BartaChatPrefs", Context.MODE_PRIVATE) }
    
    // Preferences States
    var isMuted by remember { mutableStateOf(sharedPrefs.getBoolean("mute_notifications_${contact.phone}", false)) }
    var currentWallpaper by remember { mutableStateOf(sharedPrefs.getString("chat_wallpaper_${contact.phone}", "default") ?: "default") }
    var isBlocked by remember { mutableStateOf(sharedPrefs.getBoolean("is_blocked_${contact.phone}", false)) }
    var isArchived by remember { mutableStateOf(sharedPrefs.getBoolean("archive_${contact.phone}", false)) }
    
    var showFullProfilePic by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var reportReason by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showBlockConfirm by remember { mutableStateOf(false) }

    // Navigation Tabs
    var activeMainTab by remember { mutableStateOf(1) } // 1: Profile Info, 2: Shared Content, 3: Wallpaper
    var activeSharedSubTab by remember { mutableStateOf(1) } // 1: Media, 2: Files, 3: Links

    // Extract shared content
    val sharedMedia = remember(activeMessages) {
        activeMessages.filter { it.mediaUrl != null && (it.mediaType == "image" || it.mediaType == "video") }
    }
    val sharedFiles = remember(activeMessages) {
        activeMessages.filter { it.mediaUrl != null && it.mediaType != "image" && it.mediaType != "video" }
    }
    val sharedLinks = remember(activeMessages) {
        activeMessages.filter { it.text.contains("http://", ignoreCase = true) || it.text.contains("https://", ignoreCase = true) }
    }

    val bioText = remember(contact.phone) {
        if (contact.phone == "01300000000") {
            txt("আমি একটি এআই চ্যাট সহকারী রোবট। আপনার যেকোনো প্রশ্নের উত্তর দিতে প্রস্তুত! 🤖✨", "I am an AI Chatbot Assistant. Ready to help with any query! 🤖✨")
        } else {
            sharedPrefs.getString("bio_${contact.phone}", "")?.ifEmpty {
                txt("বার্তা অ্যাপ ব্যবহার করছি! 📱✨", "Busy or sleeping. ✨")
            } ?: txt("বার্তা অ্যাপ ব্যবহার করছি! 📱✨", "Busy or sleeping. ✨")
        }
    }

    BackHandler(onBack = onDismiss)

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF0B141A)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Toolbar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(WhatsAppTealVal)
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = txt("পিছনে", "Back"),
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = txt("ব্যবহারকারীর তথ্য ও সেটিংস", "User Info & Settings"),
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 18.sp
                        )
                        Text(
                            text = contact.name,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    // Navigation Tabs Row
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf(
                            Triple(1, txt("প্রোফাইল তথ্য", "Profile"), Icons.Default.Person),
                            Triple(2, txt("মিডিয়া ও লিংক", "Shared"), Icons.Default.Folder),
                            Triple(3, txt("ওয়ালপেপার", "Wallpaper"), Icons.Default.Settings)
                        ).forEach { (tabId, tabName, icon) ->
                            val isSelected = activeMainTab == tabId
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable { activeMainTab = tabId }
                                    .padding(vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = tabName,
                                    tint = if (isSelected) WhatsAppTealVal else Color.Gray,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = tabName,
                                    color = if (isSelected) Color.White else Color.Gray,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .width(42.dp)
                                        .height(2.dp)
                                        .background(if (isSelected) WhatsAppTealVal else Color.Transparent)
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f), modifier = Modifier.padding(bottom = 16.dp))

                    when (activeMainTab) {
                        1 -> {
                            // TAB 1: PROFILE INFO & CONTROLS
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Profile Pic
                                Box(
                                    modifier = Modifier
                                        .size(110.dp)
                                        .clickable { showFullProfilePic = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    AvatarView(name = contact.name, base64 = contact.profilePicUri, size = 100)
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .align(Alignment.BottomEnd)
                                            .background(WhatsAppGreenVal, CircleShape)
                                            .border(1.5.dp, Color(0xFF0B141A), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.ZoomIn, contentDescription = "Zoom", tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Text(contact.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text(contact.phone, fontSize = 14.sp, color = Color.Gray)

                                // Status / Presence indicator
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                                ) {
                                    val isOnline = contact.lastSeen == "online"
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(if (isOnline) Color.Green else Color.Gray, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isOnline) txt("অনলাইন", "Online") else txt("অফলাইন", "Offline"),
                                        fontSize = 12.sp,
                                        color = if (isOnline) Color.Green else Color.Gray
                                    )
                                }

                                HorizontalDivider(color = Color.Gray.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 12.dp))

                                // About / Bio Section
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Text(
                                            text = txt("সম্পর্কে (About / Bio)", "About / Bio"),
                                            color = WhatsAppTealVal,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = bioText,
                                            color = Color.White,
                                            fontSize = 14.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Controls List
                                // Mute Notifications Toggle
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (isMuted) Icons.Default.NotificationsOff else Icons.Default.NotificationsActive,
                                            contentDescription = null,
                                            tint = if (isMuted) Color.LightGray else WhatsAppTealVal,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(txt("মিউট নোটিফিকেশন", "Mute Notifications"), color = Color.White, fontSize = 14.sp)
                                    }
                                    Switch(
                                        checked = isMuted,
                                        onCheckedChange = {
                                            isMuted = it
                                            sharedPrefs.edit().putBoolean("mute_notifications_${contact.phone}", it).apply()
                                        },
                                        colors = androidx.compose.material3.SwitchDefaults.colors(checkedThumbColor = WhatsAppGreenVal)
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Archive Chat Toggle
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Archive,
                                            contentDescription = null,
                                            tint = if (isArchived) WhatsAppGreenVal else WhatsAppTealVal,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(txt("চ্যাট আর্কাইভ করুন", "Archive Chat"), color = Color.White, fontSize = 14.sp)
                                    }
                                    Switch(
                                        checked = isArchived,
                                        onCheckedChange = {
                                            isArchived = it
                                            sharedPrefs.edit().putBoolean("archive_${contact.phone}", it).apply()
                                            Toast.makeText(
                                                context,
                                                if (it) txt("চ্যাট আর্কাইভ করা হয়েছে", "Chat archived") else txt("আর্কাইভ থেকে সরানো হয়েছে", "Chat unarchived"),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                        colors = androidx.compose.material3.SwitchDefaults.colors(checkedThumbColor = WhatsAppGreenVal)
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Block Contact Option
                                Button(
                                    onClick = { showBlockConfirm = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isBlocked) WhatsAppTealVal else Color(0xFFDC2626)
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth().height(46.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (isBlocked) Icons.Default.CheckCircle else Icons.Default.Block,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (isBlocked) txt("আনব্লক করুন", "Unblock Contact") else txt("ব্লক করুন", "Block Contact"),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Report User Option
                                Button(
                                    onClick = { showReportDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.08f)),
                                    border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.3f)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth().height(46.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Report, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(txt("রিপোর্ট করুন ⚠️", "Report User ⚠️"), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Clear Chat & Delete Chat Buttons
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = { showClearConfirm = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray.copy(alpha = 0.6f)),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f).height(44.dp)
                                    ) {
                                        Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(txt("চ্যাট মুছুন", "Clear Chat"), color = Color.White, fontSize = 13.sp)
                                    }

                                    Button(
                                        onClick = { showDeleteConfirm = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF991B1B)),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f).height(44.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(txt("চ্যাট ডিলিট", "Delete Chat"), color = Color.White, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                        2 -> {
                            // TAB 2: SHARED CONTENT (MEDIA, FILES, LINKS)
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(
                                        Pair(1, txt("মিডিয়া", "Media")),
                                        Pair(2, txt("ফাইল", "Files")),
                                        Pair(3, txt("লিংক", "Links"))
                                    ).forEach { (subId, label) ->
                                        val isSelected = activeSharedSubTab == subId
                                        Button(
                                            onClick = { activeSharedSubTab = subId },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isSelected) WhatsAppTealVal else Color.White.copy(alpha = 0.05f)
                                            ),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                            modifier = Modifier.height(32.dp).weight(1f)
                                        ) {
                                            Text(label, fontSize = 12.sp, color = if (isSelected) Color.White else Color.Gray)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                when (activeSharedSubTab) {
                                    1 -> {
                                        if (sharedMedia.isEmpty()) {
                                            Text(txt("কোনো শেয়ারকৃত ফটো বা ভিডিও নেই", "No shared photos or videos found"), color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp))
                                        } else {
                                            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                                                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.heightIn(max = 400.dp)
                                            ) {
                                                items(sharedMedia.size) { index ->
                                                    val msg = sharedMedia[index]
                                                    Card(
                                                        modifier = Modifier
                                                            .aspectRatio(1f)
                                                            .clickable { /* Handle click to preview */ },
                                                        shape = RoundedCornerShape(8.dp)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                                            val bitmap = remember(msg.mediaUrl) {
                                                                try {
                                                                    val decodedBytes = android.util.Base64.decode(msg.mediaUrl, android.util.Base64.DEFAULT)
                                                                    android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                                                                } catch (e: Exception) {
                                                                    null
                                                                }
                                                            }
                                                            if (bitmap != null) {
                                                                androidx.compose.foundation.Image(
                                                                    bitmap = bitmap.asImageBitmap(),
                                                                    contentDescription = "Shared Media",
                                                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                                                    modifier = Modifier.fillMaxSize()
                                                                )
                                                            } else {
                                                                Icon(Icons.Default.Image, contentDescription = null, tint = Color.Gray)
                                                            }
                                                            if (msg.mediaType == "video") {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .size(24.dp)
                                                                        .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                                                                    contentAlignment = Alignment.Center
                                                                ) {
                                                                    Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(14.dp))
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    2 -> {
                                        if (sharedFiles.isEmpty()) {
                                            Text(txt("কোনো শেয়ারকৃত ফাইল পাওয়া যায়নি", "No shared files found"), color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp))
                                        } else {
                                            sharedFiles.forEach { msg ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 4.dp)
                                                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                                                        .padding(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Default.AttachFile, contentDescription = "File", tint = WhatsAppTealVal, modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        msg.text.ifEmpty { "attachment_file" },
                                                        color = Color.White,
                                                        fontSize = 13.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    3 -> {
                                        if (sharedLinks.isEmpty()) {
                                            Text(txt("কোনো লিংক সম্বলিত বার্তা নেই", "No links found in messages"), color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp))
                                        } else {
                                            sharedLinks.forEach { msg ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 4.dp)
                                                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                                                        .padding(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Default.Link, contentDescription = "Link", tint = WhatsAppTealVal, modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        msg.text,
                                                        color = Color(0xFF38BDF8),
                                                        fontSize = 13.sp,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.clickable {
                                                            try {
                                                                val urlIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(msg.text))
                                                                context.startActivity(urlIntent)
                                                            } catch (e: Exception) {
                                                                Toast.makeText(context, "Invalid link", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        3 -> {
                            // TAB 3: WALLPAPER CUSTOMIZATION
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White.copy(alpha = 0.05f), shape = RoundedCornerShape(12.dp))
                                    .padding(14.dp)
                            ) {
                                Text(txt("চ্যাট ওয়ালপেপার থিম পরিবর্তন করুন:", "Change Chat Wallpaper Theme:"), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = WhatsAppTealVal)
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val wallpaperThemes = listOf(
                                        Pair("default", Color.DarkGray),
                                        Pair("teal", Color(0xFF005C53)),
                                        Pair("dark", Color(0xFF121212)),
                                        Pair("purple", Color(0xFF3F0C52)),
                                        Pair("blue", Color(0xFF0C245C)),
                                        Pair("green", Color(0xFF094A17)),
                                        Pair("amber", Color(0xFF4A3409))
                                    )
                                    wallpaperThemes.forEach { (themeName, color) ->
                                        val isCurrent = currentWallpaper == themeName
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .background(color, shape = CircleShape)
                                                .border(if (isCurrent) 2.5.dp else 1.dp, if (isCurrent) WhatsAppGreenVal else Color.Transparent, CircleShape)
                                                .clickable {
                                                    currentWallpaper = themeName
                                                    sharedPrefs.edit().putString("chat_wallpaper_${contact.phone}", themeName).apply()
                                                    Toast.makeText(context, txt("ওয়ালপেপার পরিবর্তন সফল!", "Wallpaper updated!"), Toast.LENGTH_SHORT).show()
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isCurrent) {
                                                Icon(Icons.Default.Check, contentDescription = "Active", tint = Color.White, modifier = Modifier.size(16.dp))
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
    }

    // Full Profile Pic Viewer Dialog
    if (showFullProfilePic) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showFullProfilePic = false }
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1F2C34)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(contact.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        IconButton(onClick = { showFullProfilePic = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    AvatarView(name = contact.name, base64 = contact.profilePicUri, size = 200)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { showFullProfilePic = false },
                        colors = ButtonDefaults.buttonColors(containerColor = WhatsAppTealVal)
                    ) {
                        Text(txt("বন্ধ করুন", "Close"), color = Color.White)
                    }
                }
            }
        }
    }

    // Report Dialog
    if (showReportDialog) {
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text(txt("রিপোর্ট করার কারণ দিন", "Report User Reason"), color = WhatsAppTealVal, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(txt("কেন আপনি এই ব্যবহারকারীকে রিপোর্ট করছেন?", "Why are you reporting this user?"), color = Color.White, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = reportReason,
                        onValueChange = { reportReason = it },
                        placeholder = { Text(txt("যেমন: ক্ষতিকর কন্টেন্ট, প্রতারণা বা স্প্যাম...", "e.g., Harmful content, fraud or spam...")) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = WhatsAppTealVal
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showReportDialog = false
                        Toast.makeText(context, txt("আপনার রিপোর্টটি জমা দেওয়া হয়েছে। ধন্যবাদ!", "Thank you! Your report has been submitted."), Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WhatsAppTealVal)
                ) {
                    Text(txt("জমা দিন", "Submit"), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) {
                    Text(txt("বাতিল", "Cancel"), color = Color.Gray)
                }
            }
        )
    }

    // Block Confirm Dialog
    if (showBlockConfirm) {
        AlertDialog(
            onDismissRequest = { showBlockConfirm = false },
            title = {
                Text(
                    text = if (isBlocked) txt("আনব্লক করতে চান?", "Unblock user?") else txt("ব্লক করতে চান?", "Block user?"),
                    fontWeight = FontWeight.Bold,
                    color = WhatsAppTealVal
                )
            },
            text = {
                Text(
                    text = if (isBlocked) {
                        txt("আপনি কি নিশ্চিতভাবে এই ব্যবহারকারীকে আনব্লক করতে চান? এর ফলে আপনারা আবার একে অপরকে বার্তা পাঠাতে পারবেন।", "Are you sure you want to unblock this contact? You will be able to send and receive messages again.")
                    } else {
                        txt("আপনি কি নিশ্চিতভাবে এই ব্যবহারকারীকে ব্লক করতে চান? ব্লক করা হলে তারা আপনাকে কোনো বার্তা পাঠাতে পারবে না।", "Are you sure you want to block this contact? They will not be able to send you messages.")
                    },
                    color = Color.LightGray,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBlockConfirm = false
                        val nextBlocked = !isBlocked
                        isBlocked = nextBlocked
                        sharedPrefs.edit().putBoolean("is_blocked_${contact.phone}", nextBlocked).apply()
                        Toast.makeText(
                            context,
                            if (nextBlocked) txt("ব্যবহারকারীকে ব্লক করা হয়েছে", "User blocked successfully") else txt("ব্যবহারকারীকে আনব্লক করা হয়েছে", "User unblocked successfully"),
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (isBlocked) WhatsAppTealVal else Color(0xFFDC2626))
                ) {
                    Text(if (isBlocked) txt("আনব্লক", "Unblock") else txt("ব্লক", "Block"), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockConfirm = false }) {
                    Text(txt("বাতিল", "Cancel"), color = Color.Gray)
                }
            }
        )
    }

    // Clear Chat Confirm Dialog
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(txt("চ্যাট মুছুন?", "Clear entire chat?"), fontWeight = FontWeight.Bold, color = WhatsAppTealVal) },
            text = { Text(txt("আপনি কি নিশ্চিতভাবে এই চ্যাটের সব বার্তা মুছে ফেলতে চান? এই কাজ মুছে যাওয়া বার্তা আর ফিরিয়ে আনা যাবে না।", "Are you sure you want to clear all messages in this chat? This cannot be undone."), color = Color.LightGray) },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirm = false
                        viewModel.clearChat(contact.phone)
                        Toast.makeText(context, txt("সকল বার্তা মুছে ফেলা হয়েছে", "Chat cleared successfully"), Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text(txt("মুছে ফেলুন", "Clear"), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(txt("বাতিল", "Cancel"), color = Color.Gray)
                }
            }
        )
    }

    // Delete Chat Confirm Dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(txt("চ্যাট সম্পূর্ণ ডিলিট করুন?", "Delete chat permanently?"), fontWeight = FontWeight.Bold, color = Color(0xFFDC2626)) },
            text = { Text(txt("আপনি কি নিশ্চিতভাবে এই চ্যাটটি এবং চ্যাটের সব তথ্য চিরতরে মুছে ফেলতে চান? এটি আর ফিরিয়ে আনা সম্ভব হবে না।", "Are you sure you want to delete this chat conversation and the contact permanently? This action cannot be undone."), color = Color.LightGray) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteChat(contact)
                        onDismiss()
                        onCloseChat()
                        Toast.makeText(context, txt("চ্যাট মুছে ফেলা হয়েছে", "Chat deleted successfully"), Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text(txt("ডিলিট করুন", "Delete permanently"), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(txt("বাতিল", "Cancel"), color = Color.Gray)
                }
            }
        )
    }
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
