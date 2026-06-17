package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = WhatsAppTealVal,         // Deep Teal: #0F766E
    secondary = PremiumIndigo,          // Indigo: #4F46E5
    tertiary = PremiumSuccessGreen,    // Success Green: #22C55E
    background = PremiumBackgroundDark, // Slate background: #0F172A
    surface = PremiumSurfaceDark,       // Slate surface card: #1E293B
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFFF8FAFC),
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFF94A3B8)
  )

private val LightColorScheme =
  lightColorScheme(
    primary = WhatsAppTealVal,         // Deep Teal: #0F766E
    secondary = PremiumIndigo,          // Indigo: #4F46E5
    tertiary = PremiumSuccessGreen,    // Success Green: #22C55E
    background = PremiumBackgroundLight,     // Background: #F8FAFC
    surface = PremiumSurfaceLight,        // Surface Cards: #FFFFFF
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = PremiumTextPrimaryLight,   // Text Primary: #0F172A
    onSurface = PremiumTextPrimaryLight,
    surfaceVariant = PremiumDividerLight,  // Light surface/card helper
    onSurfaceVariant = PremiumTextSecondaryLight // Text Secondary: #64748B
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
