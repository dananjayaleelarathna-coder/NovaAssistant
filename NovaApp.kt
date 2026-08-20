package com.nova.assistant.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nova.assistant.ui.screens.HomeScreen
import com.nova.assistant.ui.screens.MemoryScreen
import com.nova.assistant.ui.screens.PrivacyScreen
import com.nova.assistant.ui.screens.SettingsScreen

object NovaRoutes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val PRIVACY = "privacy"
    const val MEMORY = "memory"
}

@Composable
fun NovaApp() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = NovaRoutes.HOME) {
        composable(NovaRoutes.HOME) {
            HomeScreen(
                onOpenSettings = { navController.navigate(NovaRoutes.SETTINGS) }
            )
        }
        composable(NovaRoutes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenPrivacy = { navController.navigate(NovaRoutes.PRIVACY) },
                onOpenMemory = { navController.navigate(NovaRoutes.MEMORY) }
            )
        }
        composable(NovaRoutes.PRIVACY) {
            PrivacyScreen(onBack = { navController.popBackStack() })
        }
        composable(NovaRoutes.MEMORY) {
            MemoryScreen(onBack = { navController.popBackStack() })
        }
    }
}
