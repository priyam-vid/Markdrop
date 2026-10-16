package com.markdrop.app.ui.navigation

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.markdrop.app.ui.screens.convert.ConvertScreen
import com.markdrop.app.ui.screens.library.LibraryScreen
import com.markdrop.app.ui.screens.queue.QueueScreen
import com.markdrop.app.ui.screens.settings.SettingsScreen

private val ExpressiveMotionEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
private const val ExpressiveMotionDuration = 450

private fun NavBackStackEntry.routeIndex(): Int = when {
    destination.hasRoute<ConvertRoute>() -> 0
    destination.hasRoute<QueueRoute>() -> 1
    destination.hasRoute<LibraryRoute>() -> 2
    destination.hasRoute<SettingsRoute>() -> 3
    else -> 0
}

@Composable
fun MarkdropNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = ConvertRoute,
        modifier = modifier,
        enterTransition = {
            val fromIndex = initialState.routeIndex()
            val toIndex = targetState.routeIndex()
            val forward = toIndex >= fromIndex
            slideInHorizontally(
                initialOffsetX = { fullWidth -> if (forward) (fullWidth * 0.25f).toInt() else (-fullWidth * 0.25f).toInt() },
                animationSpec = tween(ExpressiveMotionDuration, easing = ExpressiveMotionEasing)
            ) + fadeIn(
                animationSpec = tween(ExpressiveMotionDuration, easing = ExpressiveMotionEasing)
            )
        },
        exitTransition = {
            val fromIndex = initialState.routeIndex()
            val toIndex = targetState.routeIndex()
            val forward = toIndex >= fromIndex
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> if (forward) (-fullWidth * 0.25f).toInt() else (fullWidth * 0.25f).toInt() },
                animationSpec = tween(ExpressiveMotionDuration, easing = ExpressiveMotionEasing)
            ) + fadeOut(
                animationSpec = tween(ExpressiveMotionDuration, easing = ExpressiveMotionEasing)
            )
        }
    ) {
        composable<ConvertRoute> {
            ConvertScreen(
                onNavigateToSettings = {
                    navController.navigate(SettingsRoute) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
        composable<QueueRoute> {
            QueueScreen(
                onNavigateToConvert = {
                    navController.navigate(ConvertRoute) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
        composable<LibraryRoute> { LibraryScreen() }
        composable<SettingsRoute> { SettingsScreen() }
    }
}
