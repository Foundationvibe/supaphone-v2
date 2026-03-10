package com.supaphone.app

import android.app.Application
import com.google.android.gms.ads.MobileAds
import com.supaphone.app.ads.AppOpenAdManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SupaPhoneApplication : Application() {
    val appOpenAdManager: AppOpenAdManager by lazy { AppOpenAdManager() }

    private val _adsSdkInitialized = MutableStateFlow(false)
    val adsSdkInitialized: StateFlow<Boolean> = _adsSdkInitialized

    private val _canRequestAds = MutableStateFlow(false)
    val canRequestAds: StateFlow<Boolean> = _canRequestAds

    @Volatile
    private var adsInitializationStarted = false
    private val adsInitCallbacks = mutableListOf<() -> Unit>()

    fun initializeAdsSdkIfNeeded(onComplete: () -> Unit = {}) {
        synchronized(this) {
            if (_adsSdkInitialized.value) {
                onComplete()
                return
            }
            adsInitCallbacks += onComplete
            if (adsInitializationStarted) {
                return
            }
            adsInitializationStarted = true
        }

        MobileAds.initialize(this) {
            val callbacks = synchronized(this) {
                adsInitializationStarted = false
                _adsSdkInitialized.value = true
                adsInitCallbacks.toList().also { adsInitCallbacks.clear() }
            }
            callbacks.forEach { it() }
        }
    }

    fun updateCanRequestAds(canRequest: Boolean) {
        _canRequestAds.value = canRequest
    }
}
