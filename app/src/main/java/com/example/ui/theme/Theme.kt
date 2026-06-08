package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Futuristic Cyber Cosmic Theme Scheme
private val CyberDarkColorScheme = darkColorScheme(
    primary = CyberCyan,
    secondary = CyberPurple,
    tertiary = CyberAmber,
    background = SpaceNavyBg,
    surface = SpaceCardBg,
    onPrimary = DeepSpaceBlack,
    onSecondary = StarlightWhite,
    onTertiary = DeepSpaceBlack,
    onBackground = StarlightWhite,
    onSurface = StarlightWhite,
    surfaceVariant = SpaceCardSelectedBg,
    onSurfaceVariant = StarlightSilver,
    outline = SpaceOutline,
    outlineVariant = SpaceOutline.copy(alpha = 0.6f),
    error = CyberPink
)

// Sophisticated Ultra-Clean Futuristic Light Scheme
private val CyberLightColorScheme = lightColorScheme(
    primary = Color(0xFF007A8A), // Clear deeper ocean cyan
    secondary = Color(0xFF7B2CBF), // Deep cyber violet
    tertiary = Color(0xFFFF9E00), // Rich amber
    background = Color(0xFFF8FAFC), // Cool slate light background
    surface = Color(0xFFFFFFFF), // White shiny panels
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF0F172A), // Dark slate text
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0), // Cool gray accents
    onSurfaceVariant = Color(0xFF64748B), // Slate gray subtitles
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFD00000)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force dark theme by default for a true immersive "very futuristic" look!
    dynamicColor: Boolean = false, // Disable dynamic colors to preserve our specialized neon futuristic theme
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) CyberDarkColorScheme else CyberLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
