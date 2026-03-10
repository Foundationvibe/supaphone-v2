package com.supaphone.app

import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.google.firebase.messaging.FirebaseMessaging
import com.supaphone.app.data.BackendConfig
import com.supaphone.app.data.EdgeFunctionsClient
import com.supaphone.app.data.SecureStorage
import com.supaphone.app.diagnostics.AppLog
import com.supaphone.app.navigation.AppNavigation
import com.supaphone.app.ui.theme.SupaPhoneAppTheme
import com.supaphone.app.ui.theme.SupaPhoneTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private fun permissionAlias(permission: String): String {
        return when (permission) {
            Manifest.permission.POST_NOTIFICATIONS -> "notifications"
            Manifest.permission.CALL_PHONE -> "call_phone"
            Manifest.permission.CAMERA -> "camera"
            else -> permission.substringAfterLast('.')
        }
    }

    private fun syncPushTokenToBackend() {
        if (!BackendConfig.isConfigured()) {
            AppLog.w("APP_PUSH_SYNC_SKIP", "reason=backend_not_configured")
            return
        }

        val phoneClientId = SecureStorage.getDeviceId(this).orEmpty()
        if (phoneClientId.isBlank()) {
            AppLog.i("APP_PUSH_SYNC_SKIP", "reason=not_paired")
            return
        }

        AppLog.i("APP_PUSH_SYNC_START")
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                AppLog.e("APP_PUSH_TOKEN_FETCH_FAIL", "reason=firebase_task_failed", task.exception)
                return@addOnCompleteListener
            }
            val token = task.result?.trim().orEmpty()
            if (token.isBlank()) {
                AppLog.w("APP_PUSH_TOKEN_EMPTY")
                return@addOnCompleteListener
            }

            val phoneClientSecret = SecureStorage.getOrCreateClientAuthSecret(this)
            val phoneLabel = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
            AppLog.i("APP_PUSH_REGISTER_START", "client=$phoneClientId")
            lifecycleScope.launch {
                EdgeFunctionsClient.registerPushToken(
                    phoneClientId = phoneClientId,
                    phoneClientSecret = phoneClientSecret,
                    phoneLabel = phoneLabel,
                    pushToken = token
                ).onSuccess {
                    AppLog.i("APP_PUSH_REGISTER_OK", "client=$phoneClientId")
                }.onFailure { error ->
                    AppLog.e("APP_PUSH_REGISTER_FAIL", "client=$phoneClientId reason=${error.message}", error)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.i("APP_BOOT", "activity=MainActivity")
        // Enable edge-to-edge, but we will handle the insets in Compose
        enableEdgeToEdge()

        setContent {
            val context = this

            // Pairing state
            var isPaired by remember { mutableStateOf(SecureStorage.isPaired(context)) }

            // Theme state
            val systemIsDark = isSystemInDarkTheme()
            var isDarkTheme by remember {
                mutableStateOf(SecureStorage.getTheme(context) ?: systemIsDark)
            }
            var hasRequestedInitialPermissions by remember {
                mutableStateOf(SecureStorage.hasRequestedInitialPermissions(context))
            }
            var showInitialPermissionsDisclosure by remember { mutableStateOf(false) }
            var pendingInitialPermissions by remember { mutableStateOf<List<String>>(emptyList()) }

            val initialPermissionsLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { results ->
                if (results.isEmpty()) {
                    AppLog.i("APP_PERM_RESULT", "granted=0 denied=0")
                    return@rememberLauncherForActivityResult
                }
                val granted = results.filterValues { it }.keys.map(::permissionAlias)
                val denied = results.filterValues { !it }.keys.map(::permissionAlias)
                AppLog.i("APP_PERM_RESULT", "granted=${granted.size} denied=${denied.size}")
                if (denied.isNotEmpty()) {
                    AppLog.w("APP_PERM_DENIED", denied.joinToString(","))
                }
            }

            LaunchedEffect(hasRequestedInitialPermissions) {
                if (hasRequestedInitialPermissions) {
                    return@LaunchedEffect
                }

                val permissionsToRequest = buildList {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    add(Manifest.permission.CALL_PHONE)
                }.filter { permission ->
                    ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
                }

                if (permissionsToRequest.isNotEmpty()) {
                    pendingInitialPermissions = permissionsToRequest
                    showInitialPermissionsDisclosure = true
                } else {
                    SecureStorage.setInitialPermissionsRequested(context, true)
                    hasRequestedInitialPermissions = true
                    AppLog.i("APP_PERM_REQUEST_SKIP", "all_granted")
                }
            }

            LaunchedEffect(isPaired) {
                if (isPaired) {
                    syncPushTokenToBackend()
                }
            }

            // Update edge-to-edge system bars when theme changes
            LaunchedEffect(isDarkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = if (isDarkTheme) {
                        androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        androidx.activity.SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    },
                    navigationBarStyle = if (isDarkTheme) {
                        androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        androidx.activity.SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    }
                )
            }

            SupaPhoneAppTheme(darkTheme = isDarkTheme) {
                val navController = rememberNavController()
                val colors = SupaPhoneTheme.colors
                val dialogActionColor = if (isDarkTheme) {
                    androidx.compose.ui.graphics.Color.White
                } else {
                    MaterialTheme.colorScheme.primary
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = colors.bgBase,
                ) {
                    if (showInitialPermissionsDisclosure && pendingInitialPermissions.isNotEmpty()) {
                        AlertDialog(
                            onDismissRequest = {},
                            title = {
                                Text("Allow key permissions")
                            },
                            text = {
                                Text(
                                    "SupaPhone uses notifications for incoming browser sends and call access for one-tap calling. " +
                                        "If you do not allow call access, the app will still open the dialer instead."
                                )
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        val requested = pendingInitialPermissions.map(::permissionAlias).joinToString(",")
                                        AppLog.i("APP_PERM_REQUEST_START", requested)
                                        SecureStorage.setInitialPermissionsRequested(context, true)
                                        hasRequestedInitialPermissions = true
                                        showInitialPermissionsDisclosure = false
                                        initialPermissionsLauncher.launch(pendingInitialPermissions.toTypedArray())
                                    }
                                ) {
                                    Text("Continue", color = dialogActionColor)
                                }
                            }
                        )
                    }

                    AppNavigation(
                        navController = navController,
                        isPaired = isPaired,
                        onPaired = { deviceId, secret ->
                            SecureStorage.savePairing(context, deviceId, secret)
                            isPaired = true
                        },
                        onUnpair = {
                            SecureStorage.clearPairing(context)
                            isPaired = false
                        },
                        isDarkTheme = isDarkTheme,
                        onToggleTheme = { dark ->
                            isDarkTheme = dark
                            SecureStorage.saveTheme(context, dark)
                        },
                    )
                }
            }
        }
    }
}
