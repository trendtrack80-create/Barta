package com.example

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.notification.NotificationHelper
import com.example.ui.screens.MainAppScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.ChatViewModel

class MainActivity : ComponentActivity() {
  private var chatViewModel: ChatViewModel? = null

  private val requestNotificationPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    if (isGranted) {
      Log.d("BartaChat", "Notification permission granted!")
    } else {
      Log.w("BartaChat", "Notification permission denied!")
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // Pre-create notification channel immediately
    NotificationHelper.createNotificationChannel(this)
    checkAndRequestNotificationPermission()

    setContent {
      val vm: ChatViewModel = viewModel()
      chatViewModel = vm
      val isDarkState = vm.isDarkMode.collectAsState()
      
      // Handle potential intent navigation on cold start
      handleNotificationIntent(intent)

      MyApplicationTheme(darkTheme = isDarkState.value) {
        MainAppScreen(
          viewModel = vm,
          modifier = Modifier.fillMaxSize()
        )
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleNotificationIntent(intent)
  }

  private fun handleNotificationIntent(intent: Intent?) {
    val chatPhone = intent?.getStringExtra("chat_phone")
    if (!chatPhone.isNullOrBlank()) {
      Log.d("BartaChat", "Directly opening chat phone: $chatPhone")
      chatViewModel?.openChatByPhone(chatPhone)
    }
  }

  private fun checkAndRequestNotificationPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      if (ContextCompat.checkSelfPermission(
          this,
          android.Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
      ) {
        requestNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
      }
    }
  }

  override fun onStart() {
    super.onStart()
    chatViewModel?.setAppForegrounded(true)
  }

  override fun onStop() {
    super.onStop()
    chatViewModel?.setAppForegrounded(false)
  }
}
