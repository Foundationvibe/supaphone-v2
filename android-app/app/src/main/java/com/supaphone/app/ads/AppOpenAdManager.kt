package com.supaphone.app.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.supaphone.app.BuildConfig
import com.supaphone.app.diagnostics.AppLog

class AppOpenAdManager {
    companion object {
        private const val MAX_AD_AGE_MS = 4 * 60 * 60 * 1000L
    }

    private var appOpenAd: AppOpenAd? = null
    private var isLoadingAd = false
    private var isShowingAd = false
    private var adLoadTimeMs = 0L

    fun isAdAvailable(): Boolean {
        return appOpenAd != null && (System.currentTimeMillis() - adLoadTimeMs) < MAX_AD_AGE_MS
    }

    fun loadAdIfNeeded(context: Context) {
        if (isLoadingAd || isAdAvailable()) {
            return
        }
        val adUnitId = BuildConfig.ADMOB_APP_OPEN_AD_UNIT_ID.trim()
        if (adUnitId.isBlank()) {
            AppLog.w("ADS_APP_OPEN_LOAD_SKIP", "reason=missing_ad_unit_id")
            return
        }

        isLoadingAd = true
        AppLog.i("ADS_APP_OPEN_LOAD_START")
        AppOpenAd.load(
            context,
            adUnitId,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(loadedAd: AppOpenAd) {
                    appOpenAd = loadedAd
                    adLoadTimeMs = System.currentTimeMillis()
                    isLoadingAd = false
                    AppLog.i("ADS_APP_OPEN_LOAD_OK")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    appOpenAd = null
                    isLoadingAd = false
                    AppLog.w(
                        "ADS_APP_OPEN_LOAD_FAIL",
                        "code=${error.code} message=${error.message}"
                    )
                }
            }
        )
    }

    fun showAdIfAvailable(activity: Activity, onComplete: (Boolean) -> Unit) {
        if (isShowingAd) {
            onComplete(false)
            return
        }
        if (!isAdAvailable()) {
            loadAdIfNeeded(activity.applicationContext)
            onComplete(false)
            return
        }

        val currentAd = appOpenAd ?: run {
            onComplete(false)
            return
        }

        currentAd.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                isShowingAd = true
                AppLog.i("ADS_APP_OPEN_SHOW")
            }

            override fun onAdDismissedFullScreenContent() {
                appOpenAd = null
                isShowingAd = false
                AppLog.i("ADS_APP_OPEN_DISMISS")
                loadAdIfNeeded(activity.applicationContext)
                onComplete(true)
            }

            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                appOpenAd = null
                isShowingAd = false
                AppLog.w(
                    "ADS_APP_OPEN_SHOW_FAIL",
                    "code=${error.code} message=${error.message}"
                )
                loadAdIfNeeded(activity.applicationContext)
                onComplete(false)
            }
        }

        currentAd.show(activity)
    }
}
