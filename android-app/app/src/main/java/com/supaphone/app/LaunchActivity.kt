package com.supaphone.app

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.supaphone.app.ads.AdConsentManager
import com.supaphone.app.data.SecureStorage
import com.supaphone.app.diagnostics.AppLog
import com.supaphone.app.ui.components.BrandLogo
import com.supaphone.app.ui.theme.SupaPhoneAppTheme
import com.supaphone.app.ui.theme.SupaPhoneTheme
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class LaunchActivity : ComponentActivity() {
    companion object {
        private const val STARTUP_BOOTSTRAP_TIMEOUT_MS = 8_000L
        private const val APP_OPEN_CHECK_INTERVAL_MS = 75L
        private const val APP_OPEN_AD_COOLDOWN_MS = 45 * 60 * 1000L
        private const val MIN_NORMAL_LAUNCHES_BEFORE_APP_OPEN = 3
    }

    private var hasRouted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val useDarkTheme = SecureStorage.getTheme(this) ?: resources.configuration.uiMode.isNightModeActive()
        enableEdgeToEdge(
            statusBarStyle = if (useDarkTheme) {
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
            },
            navigationBarStyle = if (useDarkTheme) {
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
            }
        )

        setContent {
            SupaPhoneAppTheme(darkTheme = useDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = SupaPhoneTheme.colors.bgBase
                ) {
                    LaunchScreen()
                }
            }
        }

        val launchCount = SecureStorage.incrementNormalAppLaunchCount(this)
        lifecycleScope.launch {
            runLaunchGate(launchCount)
        }
    }

    private suspend fun runLaunchGate(launchCount: Int) {
        val application = application as SupaPhoneApplication
        val consentManager = AdConsentManager(this)
        val eligibleForAppOpen = isEligibleForAppOpen(launchCount)
        coroutineScope {
            val startupReady = async {
                completeStartupBootstrap(application, consentManager)
            }
            val appOpenShown = if (eligibleForAppOpen) {
                async { maybeShowAppOpenDuringStartup(application, startupReady) }
            } else {
                null
            }

            val startupCompleted = startupReady.await()
            val didShowAd = appOpenShown?.await() == true

            if (!eligibleForAppOpen) {
                AppLog.i("ADS_APP_OPEN_SKIP", "reason=launch_gate launches=$launchCount")
            } else if (!didShowAd) {
                AppLog.i("ADS_APP_OPEN_SKIP", "reason=not_ready_during_startup")
            }
            if (!startupCompleted) {
                AppLog.w("APP_LAUNCH_BOOTSTRAP_TIMEOUT")
            }
            routeToMain()
        }
    }

    private suspend fun completeStartupBootstrap(
        application: SupaPhoneApplication,
        consentManager: AdConsentManager
    ): Boolean {
        warmUpStartupState()

        val canRequestAds = requestConsentState(consentManager)
        application.updateCanRequestAds(canRequestAds)
        if (!canRequestAds) {
            return true
        }

        val adsInitialized = awaitAdsSdkInitialization(application)
        if (adsInitialized) {
            application.appOpenAdManager.loadAdIfNeeded(applicationContext)
        }
        return adsInitialized
    }

    private fun warmUpStartupState() {
        SecureStorage.getTheme(this)
        SecureStorage.isPaired(this)
        SecureStorage.hasRequestedInitialPermissions(this)
        SecureStorage.shouldHideNotificationContent(this)
        SecureStorage.getLastAppOpenAdShownAt(this)
        SecureStorage.getNormalAppLaunchCount(this)
    }

    private suspend fun requestConsentState(consentManager: AdConsentManager): Boolean {
        return withTimeoutOrNull(STARTUP_BOOTSTRAP_TIMEOUT_MS) {
            suspendCancellableCoroutine<Boolean> { continuation ->
                consentManager.requestConsentUpdate(this@LaunchActivity) { canRequest ->
                    if (continuation.isActive) {
                        continuation.resume(canRequest)
                    }
                }
            }
        } ?: run {
            AppLog.w("ADS_CONSENT_TIMEOUT")
            false
        }
    }

    private suspend fun awaitAdsSdkInitialization(application: SupaPhoneApplication): Boolean {
        if (application.adsSdkInitialized.value) {
            return true
        }

        return withTimeoutOrNull(STARTUP_BOOTSTRAP_TIMEOUT_MS) {
            suspendCancellableCoroutine<Boolean> { continuation ->
                application.initializeAdsSdkIfNeeded {
                    if (continuation.isActive) {
                        continuation.resume(true)
                    }
                }
            }
        } ?: run {
            AppLog.w("ADS_SDK_INIT_TIMEOUT")
            false
        }
    }

    private suspend fun maybeShowAppOpenDuringStartup(
        application: SupaPhoneApplication,
        startupReady: Deferred<Boolean>
    ): Boolean {
        while (!startupReady.isCompleted && !isFinishing && !isDestroyed) {
            if (application.canRequestAds.value &&
                application.adsSdkInitialized.value &&
                application.appOpenAdManager.isAdAvailable()
            ) {
                return suspendCancellableCoroutine { continuation ->
                    application.appOpenAdManager.showAdIfAvailable(this) { shown ->
                        if (shown) {
                            SecureStorage.setLastAppOpenAdShownAt(this, System.currentTimeMillis())
                        }
                        if (continuation.isActive) {
                            continuation.resume(shown)
                        }
                    }
                }
            }
            delay(APP_OPEN_CHECK_INTERVAL_MS)
        }
        return false
    }

    private fun isEligibleForAppOpen(launchCount: Int): Boolean {
        if (launchCount <= MIN_NORMAL_LAUNCHES_BEFORE_APP_OPEN) {
            return false
        }
        val lastShownAt = SecureStorage.getLastAppOpenAdShownAt(this)
        return System.currentTimeMillis() - lastShownAt >= APP_OPEN_AD_COOLDOWN_MS
    }

    private fun routeToMain() {
        if (hasRouted || isFinishing || isDestroyed) {
            return
        }
        hasRouted = true
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        )
        finish()
    }

    private fun Int.isNightModeActive(): Boolean {
        return this and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }
}

@Composable
private fun LaunchScreen() {
    val colors = SupaPhoneTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgBase),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BrandLogo(
            modifier = Modifier
                .fillMaxWidth(0.54f)
                .height(48.dp)
        )
        Spacer(Modifier.height(24.dp))
        CircularProgressIndicator(color = colors.primary)
        Spacer(Modifier.height(18.dp))
        Text(
            text = "Preparing SupaPhone",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = colors.textMain
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Loading your dashboard...",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted
        )
    }
}
