package com.supaphone.app.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.supaphone.app.BuildConfig
import com.supaphone.app.ads.AdConsentManager
import com.supaphone.app.SupaPhoneApplication
import com.supaphone.app.data.PublicLinks
import com.supaphone.app.data.SecureStorage
import com.supaphone.app.diagnostics.AppLog
import com.supaphone.app.ui.theme.SupaPhoneTheme

private fun openExternalLink(url: String, onFailure: () -> Unit, context: android.content.Context) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }.onFailure { error ->
        AppLog.e("SETTINGS_LINK_OPEN_FAIL", "url=$url reason=${error.message}", error)
        onFailure()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    isDarkTheme: Boolean,
    onToggleTheme: (Boolean) -> Unit,
) {
    val colors = SupaPhoneTheme.colors
    val context = LocalContext.current
    val selectorContainerShape = RoundedCornerShape(12.dp)
    val selectorItemShape = RoundedCornerShape(9.dp)
    val selectorInnerPadding = 4.dp
    val selectorItemSpacing = 4.dp
    val selectorItemHorizontalPadding = 14.dp
    val selectorItemVerticalPadding = 8.dp
    var showHowItWorksDialog by remember { mutableStateOf(false) }
    var showLinkErrorDialog by remember { mutableStateOf(false) }
    var showPrivacyOptionsErrorDialog by remember { mutableStateOf(false) }
    var hideNotificationContent by remember {
        mutableStateOf(SecureStorage.shouldHideNotificationContent(context))
    }
    var allowUnofficialWhatsAppPackages by remember {
        mutableStateOf(SecureStorage.allowUnofficialWhatsAppPackages(context))
    }
    val application = context.applicationContext as? SupaPhoneApplication
    val consentManager = remember(context) {
        (context as? Activity)?.let { AdConsentManager(it) }
    }
    val privacyOptionsRequired by consentManager
        ?.privacyOptionsRequired
        ?.collectAsState()
        ?: remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgBase)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        TopAppBar(
            title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = colors.textMuted)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = colors.bgBase,
                titleContentColor = colors.textMain,
            ),
        )

        Spacer(Modifier.height(8.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(12.dp),
            color = colors.bgSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.bgSurfaceHover),
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Theme",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = colors.textMain,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Light or Dark",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                    )
                }

                Surface(
                    modifier = Modifier.widthIn(min = 210.dp, max = 232.dp),
                    shape = selectorContainerShape,
                    color = colors.bgBase,
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.bgSurfaceHover),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(selectorInnerPadding),
                        horizontalArrangement = Arrangement.spacedBy(selectorItemSpacing)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp),
                            onClick = { onToggleTheme(true) },
                            shape = selectorItemShape,
                            color = if (isDarkTheme) colors.primary else Color.Transparent,
                        ) {
                            Row(
                                modifier = Modifier.padding(
                                    horizontal = selectorItemHorizontalPadding,
                                    vertical = selectorItemVerticalPadding
                                ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.DarkMode,
                                    null,
                                    tint = if (isDarkTheme) Color.White else colors.textMuted,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Dark",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isDarkTheme) Color.White else colors.textMuted,
                                )
                            }
                        }
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp),
                            onClick = { onToggleTheme(false) },
                            shape = selectorItemShape,
                            color = if (!isDarkTheme) colors.primary else Color.Transparent,
                        ) {
                            Row(
                                modifier = Modifier.padding(
                                    horizontal = selectorItemHorizontalPadding,
                                    vertical = selectorItemVerticalPadding
                                ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.LightMode,
                                    null,
                                    tint = if (!isDarkTheme) Color.White else colors.textMuted,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Light",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (!isDarkTheme) Color.White else colors.textMuted,
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (privacyOptionsRequired) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                color = colors.bgSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.bgSurfaceHover),
                onClick = {
                    val activity = context as? Activity
                    val manager = consentManager
                    if (activity == null || manager == null) {
                        showPrivacyOptionsErrorDialog = true
                    } else {
                        manager.showPrivacyOptionsForm(activity) { error ->
                            application?.updateCanRequestAds(manager.canRequestAds.value)
                            if (error != null) {
                                showPrivacyOptionsErrorDialog = true
                            }
                        }
                    }
                },
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Privacy Options",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            color = colors.textMain,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Review ad privacy choices",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                        )
                    }
                    Icon(Icons.Default.Description, null, tint = colors.textMuted, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(12.dp))
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(12.dp),
            color = colors.bgSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.bgSurfaceHover),
            onClick = {
                hideNotificationContent = !hideNotificationContent
                SecureStorage.setHideNotificationContent(context, hideNotificationContent)
            },
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Hide Notification Content",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = colors.textMain,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Show only generic text in Android notifications.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                    )
                }
                Switch(
                    checked = hideNotificationContent,
                    onCheckedChange = { checked ->
                        hideNotificationContent = checked
                        SecureStorage.setHideNotificationContent(context, checked)
                    }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (BuildConfig.ALLOW_UNOFFICIAL_WHATSAPP_PACKAGES) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                color = colors.bgSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.bgSurfaceHover),
                onClick = {
                    allowUnofficialWhatsAppPackages = !allowUnofficialWhatsAppPackages
                    SecureStorage.setAllowUnofficialWhatsAppPackages(context, allowUnofficialWhatsAppPackages)
                },
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Unofficial WhatsApp Apps",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            color = colors.textMain,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Allow the direct build to open unofficial WhatsApp variants. Off by default.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                        )
                    }
                    Switch(
                        checked = allowUnofficialWhatsAppPackages,
                        onCheckedChange = { checked ->
                            allowUnofficialWhatsAppPackages = checked
                            SecureStorage.setAllowUnofficialWhatsAppPackages(context, checked)
                        }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(12.dp),
            color = colors.bgSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.bgSurfaceHover),
            onClick = {
                openExternalLink(
                    url = PublicLinks.privacyPolicyUrl,
                    onFailure = { showLinkErrorDialog = true },
                    context = context
                )
            },
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Privacy Policy",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = colors.textMain,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Open the public privacy page",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                    )
                }
                Icon(Icons.Default.Description, null, tint = colors.textMuted, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(12.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(12.dp),
            color = colors.bgSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.bgSurfaceHover),
            onClick = {
                openExternalLink(
                    url = PublicLinks.termsUrl,
                    onFailure = { showLinkErrorDialog = true },
                    context = context
                )
            },
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Terms of Use",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = colors.textMain,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Open the public terms page",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                    )
                }
                Icon(Icons.Default.Description, null, tint = colors.textMuted, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(12.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(12.dp),
            color = colors.bgSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.bgSurfaceHover),
            onClick = {
                openExternalLink(
                    url = PublicLinks.supportUrl,
                    onFailure = { showLinkErrorDialog = true },
                    context = context
                )
            },
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Support",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = colors.textMain,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Open support and contact details",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                    )
                }
                Icon(Icons.Default.Description, null, tint = colors.textMuted, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(12.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(12.dp),
            color = colors.bgSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.bgSurfaceHover),
            onClick = { showHowItWorksDialog = true },
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "How it works",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = colors.textMain,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Pairing and send flow summary",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                    )
                }
                Icon(Icons.Default.Description, null, tint = colors.textMuted, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.weight(1f))
        Text(
            text = "SupaPhone v1.0.0",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textMuted,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 24.dp),
        )
    }

    if (showHowItWorksDialog) {
        AlertDialog(
            onDismissRequest = { showHowItWorksDialog = false },
            containerColor = colors.bgSurface,
            title = { Text("How SupaPhone Works", color = colors.textMain) },
            text = {
                Text(
                    text = "1. Pair browser and phone using QR or 8-digit code.\n" +
                        "2. Choose a target device from browser context menu.\n" +
                        "3. Send link or number and receive it instantly on phone.\n" +
                        "4. Logs are retained for 24 hours and can be cleared.",
                    color = colors.textMuted
                )
            },
            confirmButton = {
                TextButton(onClick = { showHowItWorksDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showLinkErrorDialog) {
        AlertDialog(
            onDismissRequest = { showLinkErrorDialog = false },
            containerColor = colors.bgSurface,
            title = { Text("Unable to open link", color = colors.textMain) },
            text = {
                Text(
                    text = "The policy or support page could not be opened from this device right now.",
                    color = colors.textMuted
                )
            },
            confirmButton = {
                TextButton(onClick = { showLinkErrorDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showPrivacyOptionsErrorDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyOptionsErrorDialog = false },
            containerColor = colors.bgSurface,
            title = { Text("Unable to open privacy options", color = colors.textMain) },
            text = {
                Text(
                    text = "The consent privacy options are not available right now. Try again later.",
                    color = colors.textMuted
                )
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyOptionsErrorDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

}
