package com.supaphone.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.MaterialTheme
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun BrandLogo(
    modifier: Modifier = Modifier,
    contentDescription: String = "SupaPhone logo"
) {
    val context = LocalContext.current
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val model = remember(isDarkTheme) {
        val logoAssetPath = if (isDarkTheme) {
            "file:///android_asset/branding/main-logo1.png"
        } else {
            "file:///android_asset/branding/main-logo2.png"
        }
        ImageRequest.Builder(context)
            .data(logoAssetPath)
            .crossfade(false)
            .build()
    }

    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}
