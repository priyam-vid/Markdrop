package com.markdrop.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.markdrop.app.data.settings.ThemeMode
import com.markdrop.app.data.settings.ThemeSettings

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MarkdropTheme(
    themeSettings: ThemeSettings = ThemeSettings(),
    content: @Composable () -> Unit
) {
    val isDark = when (themeSettings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    
    val context = LocalContext.current
    
    var colorScheme = when {
        // Dynamic color on Android 12+
        themeSettings.useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (isDark) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        // Seed-color-based scheme using colorScheme generation
        else -> {
            val seedColor = parseColor(themeSettings.seedColorHex)
            if (isDark) darkColorScheme(primary = seedColor)
            else lightColorScheme(primary = seedColor)
        }
    }
    
    // AMOLED override
    if (isDark && themeSettings.useAmoled) {
        colorScheme = colorScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainer = Color.Black,
            surfaceContainerLow = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerHigh = Color(0xFF1A1A1A),
            surfaceContainerHighest = Color(0xFF2A2A2A),
        )
    }
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = MarkdropTypography,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}

// Helper to parse hex color string to Compose Color
fun parseColor(hex: String): Color {
    return try {
        val colorLong = android.graphics.Color.parseColor(hex).toLong()
        Color(colorLong or 0xFF000000)
    } catch (e: Exception) {
        Color(0xFF6750A4) // Fallback to default primary
    }
}
