package com.supaphone.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.supaphone.app.ui.screens.HomeScreen
import com.supaphone.app.ui.screens.HistoryScreen
import com.supaphone.app.ui.screens.PairingScreen
import com.supaphone.app.ui.screens.SettingsScreen

object Routes {
    const val PAIRING = "pairing"
    const val ADD_DEVICE_PAIRING = "add_device_pairing"
    const val HOME = "home"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    isPaired: Boolean,
    onPaired: (deviceId: String, secret: String) -> Unit,
    onUnpair: () -> Unit,
    isDarkTheme: Boolean,
    onToggleTheme: (Boolean) -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = if (isPaired) Routes.HOME else Routes.PAIRING,
    ) {
        composable(Routes.PAIRING) {
            PairingScreen(
                onPaired = { deviceId, secret ->
                    onPaired(deviceId, secret)
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.PAIRING) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onAddDevice = { navController.navigate(Routes.ADD_DEVICE_PAIRING) },
                onUnpair = {
                    onUnpair()
                    navController.navigate(Routes.PAIRING) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.ADD_DEVICE_PAIRING) {
            PairingScreen(
                onPaired = { _, _ ->
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.HISTORY) {
            HistoryScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                isDarkTheme = isDarkTheme,
                onToggleTheme = onToggleTheme,
            )
        }
    }
}
