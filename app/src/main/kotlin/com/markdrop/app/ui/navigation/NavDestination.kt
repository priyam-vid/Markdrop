package com.markdrop.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.PostAdd
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

// Type-safe route objects
@Serializable object ConvertRoute
@Serializable object QueueRoute
@Serializable object LibraryRoute
@Serializable object SettingsRoute

enum class NavDestination(
    val label: String,
    val icon: ImageVector,
    val route: Any,
) {
    CONVERT("Convert", Icons.Rounded.PostAdd, ConvertRoute),
    QUEUE("Queue", Icons.Rounded.FormatListNumbered, QueueRoute),
    LIBRARY("Library", Icons.Rounded.Folder, LibraryRoute),
    SETTINGS("Settings", Icons.Rounded.Settings, SettingsRoute),
}
