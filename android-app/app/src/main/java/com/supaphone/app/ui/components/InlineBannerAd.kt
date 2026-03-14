package com.supaphone.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.supaphone.app.BuildConfig
import com.supaphone.app.SupaPhoneApplication

@Composable
fun InlineBannerAd(
    modifier: Modifier = Modifier,
    adUnitId: String = BuildConfig.ADMOB_BANNER_AD_UNIT_ID
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val application = context.applicationContext as? SupaPhoneApplication ?: return
    val canRequestAds by application.canRequestAds.collectAsState()
    val adsInitialized by application.adsSdkInitialized.collectAsState()

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val widthDp = maxWidth.value.toInt().coerceAtLeast(320)
        val adSize = remember(widthDp) {
            AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp)
        }
        val reservedHeight = remember(widthDp, density) {
            with(density) {
                adSize.getHeightInPixels(context).toDp()
            }
        }
        val slotModifier = Modifier
            .fillMaxWidth()
            .height(reservedHeight)

        if (!adsInitialized || !canRequestAds || adUnitId.isBlank()) {
            Box(modifier = slotModifier)
            return@BoxWithConstraints
        }

        val adView = remember(adUnitId) {
            AdView(context).apply {
                this.adUnitId = adUnitId
            }
        }

        DisposableEffect(adView) {
            onDispose { adView.destroy() }
        }

        var loadedWidthDp by remember { mutableIntStateOf(0) }

        AndroidView(
            modifier = slotModifier,
            factory = { adView },
            update = { view ->
                if (loadedWidthDp != widthDp) {
                    loadedWidthDp = widthDp
                    view.setAdSize(adSize)
                    view.loadAd(AdRequest.Builder().build())
                }
            }
        )
    }
}
