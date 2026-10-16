package com.markdrop.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.markdrop.app.data.settings.ThemeSettings
import com.markdrop.app.data.settings.InteractionSettings
import com.markdrop.app.ui.navigation.*
import com.markdrop.app.ui.theme.MarkdropTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        
        val app = applicationContext as MarkdropApplication
        val settingsRepository = app.settingsRepository

        setContent {
            val themeSettings by settingsRepository.themeSettingsFlow.collectAsStateWithLifecycle(
                initialValue = ThemeSettings()
            )
            val interactionSettings by settingsRepository.interactionSettingsFlow.collectAsStateWithLifecycle(
                initialValue = InteractionSettings(hapticsEnabled = true)
            )

            MarkdropTheme(themeSettings = themeSettings) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    
                    val currentRoute = navBackStackEntry?.destination?.route

                    val hazeState = remember { HazeState() }

                    Box(modifier = Modifier.fillMaxSize()) {
                    MarkdropNavHost(
                        navController = navController,
                        modifier = Modifier
                            .fillMaxSize()
                            .hazeSource(hazeState)
                            .padding(bottom = 80.dp)
                    )
                    
                    val selectedDestination = NavDestination.entries.find { 
                        it.route::class.qualifiedName == currentRoute 
                    } ?: NavDestination.entries.firstOrNull()
                    
                    if (selectedDestination != null) {
                        BottomNavBar(
                            destinations = NavDestination.entries,
                            currentDestination = selectedDestination,
                            onNavigate = { dest ->
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            hapticsEnabled = interactionSettings.hapticsEnabled,
                            hazeState = hazeState,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }
    }
}
}
