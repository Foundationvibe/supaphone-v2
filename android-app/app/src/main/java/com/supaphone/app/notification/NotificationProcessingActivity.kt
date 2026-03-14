package com.supaphone.app.notification

import android.Manifest
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.supaphone.app.SupaPhoneApplication
import com.supaphone.app.ads.AdConsentManager
import com.supaphone.app.BuildConfig
import com.supaphone.app.data.EdgeFunctionsClient
import com.supaphone.app.data.SecureStorage
import com.supaphone.app.diagnostics.AppLog
import com.supaphone.app.ui.components.InlineBannerAd
import com.supaphone.app.ui.theme.SupaPhoneAppTheme
import com.supaphone.app.ui.theme.SupaPhoneTheme
import kotlinx.coroutines.launch

class NotificationProcessingActivity : ComponentActivity() {
    private var pendingPhoneToCall: String? = null

    private val callPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val phone = pendingPhoneToCall
        pendingPhoneToCall = null
        if (phone.isNullOrBlank()) {
            finish()
            return@registerForActivityResult
        }

        if (granted) {
            AppLog.i("NOTIFICATION_CALL_PERMISSION", "result=granted")
            startActivity(
                Intent(Intent.ACTION_CALL, Uri.parse("tel:$phone")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } else {
            AppLog.w("NOTIFICATION_CALL_PERMISSION", "result=denied")
            Toast.makeText(
                this,
                "Call permission denied. Opened dialer instead.",
                Toast.LENGTH_SHORT
            ).show()
            openDialer(phone)
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val itemType = intent.getStringExtra(NotificationActionIntent.EXTRA_ITEM_TYPE).orEmpty()
        val payload = intent.getStringExtra(NotificationActionIntent.EXTRA_PAYLOAD).orEmpty()
        val notificationId = intent.getIntExtra(NotificationActionIntent.EXTRA_NOTIFICATION_ID, -1)
        val eventId = intent.getStringExtra(NotificationActionIntent.EXTRA_EVENT_ID).orEmpty()

        cancelNotification(notificationId)
        acknowledgeOpenedEvent(eventId)
        primeInlineAds()

        val savedTheme = SecureStorage.getTheme(this)
        val useDarkTheme = savedTheme ?: resources.configuration.uiMode.isNightModeActive()

        setContent {
            SupaPhoneAppTheme(darkTheme = useDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = SupaPhoneTheme.colors.bgBase
                ) {
                    NotificationFlowScreen(
                        itemType = itemType,
                        payload = payload,
                        onPhoneActionSelected = { action ->
                            handlePhoneAction(action, payload)
                        },
                        onLinkActionSelected = { action ->
                            handleLinkAction(action, payload)
                        },
                        onDismiss = { finish() }
                    )
                }
            }
        }
    }

    private fun acknowledgeOpenedEvent(eventId: String) {
        if (eventId.isBlank()) {
            return
        }
        val phoneClientId = SecureStorage.getDeviceId(this).orEmpty()
        if (phoneClientId.isBlank()) {
            return
        }
        val phoneClientSecret = SecureStorage.getOrCreateClientAuthSecret(this)
        lifecycleScope.launch {
            EdgeFunctionsClient.ackPushEvent(
                eventId = eventId,
                targetClientId = phoneClientId,
                targetClientSecret = phoneClientSecret,
                status = "opened",
                ackMessage = "Opened from notification flow."
            ).onSuccess {
                AppLog.d("NOTIFICATION_EVENT_OPEN_OK", "event=$eventId")
            }.onFailure { error ->
                AppLog.w("NOTIFICATION_EVENT_OPEN_FAIL", "event=$eventId reason=${error.message}")
            }
        }
    }

    private fun primeInlineAds() {
        val app = application as? SupaPhoneApplication ?: return
        val consentManager = AdConsentManager(this)

        val storedCanRequestAds = consentManager.canRequestAds.value
        app.updateCanRequestAds(storedCanRequestAds)
        if (storedCanRequestAds) {
            app.initializeAdsSdkIfNeeded()
        }

        lifecycleScope.launch {
            consentManager.requestConsentUpdate(this@NotificationProcessingActivity) { canRequestAds ->
                app.updateCanRequestAds(canRequestAds)
                if (canRequestAds) {
                    app.initializeAdsSdkIfNeeded()
                }
            }
        }
    }

    private fun handlePhoneAction(action: PhoneAction, rawPayload: String) {
        val normalizedPhone = normalizePhonePayload(rawPayload)
        if (normalizedPhone.isBlank()) {
            Toast.makeText(this, "Invalid phone number.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        when (action) {
            PhoneAction.Call -> {
                val hasPermission = ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CALL_PHONE
                ) == PackageManager.PERMISSION_GRANTED

                if (hasPermission) {
                    startActivity(
                        Intent(Intent.ACTION_CALL, Uri.parse("tel:$normalizedPhone")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                    finish()
                    return
                }

                pendingPhoneToCall = normalizedPhone
                callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
            }

            PhoneAction.OpenDialer -> {
                openDialer(normalizedPhone)
                finish()
            }

            PhoneAction.WhatsApp -> {
                val launchIntent = resolveWhatsAppIntent(normalizedPhone)
                startActivity(launchIntent)
                finish()
            }

            PhoneAction.Share -> {
                sharePhoneNumber(normalizedPhone)
                finish()
            }
        }
    }

    private fun handleLinkAction(action: LinkAction, rawPayload: String) {
        val normalizedUrl = normalizeUrl(rawPayload).replace(" ", "%20")
        when (action) {
            LinkAction.OpenLink -> {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(normalizedUrl)).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val chooser = Intent.createChooser(browserIntent, "Open link with").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val resolved = if (browserIntent.resolveActivity(packageManager) != null) {
                    browserIntent
                } else {
                    chooser
                }
                startActivity(resolved)
                finish()
            }

            LinkAction.CopyLink -> {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("SupaPhone Link", normalizedUrl))
                Toast.makeText(this, "Link copied", Toast.LENGTH_SHORT).show()
                finish()
            }

            LinkAction.ShareLink -> {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, normalizedUrl)
                }
                startActivity(
                    Intent.createChooser(shareIntent, "Share link").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
                finish()
            }
        }
    }

    private fun openDialer(phone: String) {
        startActivity(
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    private fun sharePhoneNumber(phone: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, phone)
        }
        startActivity(
            Intent.createChooser(shareIntent, "Share number").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    private fun resolveWhatsAppIntent(phone: String): Intent {
        val phoneDigits = phone.filter { it.isDigit() }
        val sanitized = phoneDigits.ifBlank { "0" }
        val allowUnofficialPackages = BuildConfig.ALLOW_UNOFFICIAL_WHATSAPP_PACKAGES &&
            SecureStorage.allowUnofficialWhatsAppPackages(this)
        val supportedPackages = if (allowUnofficialPackages) {
            listOf(
                "com.whatsapp",
                "com.whatsapp.w4b",
                "com.gbwhatsapp",
                "com.yowhatsapp",
                "com.fmwhatsapp",
                "com.whatsapp.plus"
            )
        } else {
            listOf("com.whatsapp", "com.whatsapp.w4b")
        }

        val deepLinkUris = listOf(
            Uri.parse("whatsapp://send?phone=$sanitized"),
            Uri.parse("https://wa.me/$sanitized"),
            Uri.parse("https://api.whatsapp.com/send?phone=$sanitized")
        )

        for (packageName in supportedPackages) {
            for (uri in deepLinkUris) {
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    `package` = packageName
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (intent.resolveActivity(packageManager) != null) {
                    return intent
                }
            }
        }

        if (allowUnofficialPackages) {
            for (uri in deepLinkUris) {
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (intent.resolveActivity(packageManager) != null) {
                    return intent
                }
            }
        }

        return Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$sanitized")).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun normalizePhonePayload(payload: String): String {
        val trimmed = payload.trim()
        if (trimmed.isEmpty()) {
            return trimmed
        }

        val regionCode = SecureStorage.resolveDefaultRegionCode(this)
        val phoneUtil = PhoneNumberUtil.getInstance()
        val e164Value = runCatching {
            val parsed = phoneUtil.parse(trimmed, regionCode)
            if (!phoneUtil.isPossibleNumber(parsed)) {
                return@runCatching null
            }
            phoneUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)
                .takeIf { value ->
                    val digits = value.filter { it.isDigit() }
                    value.startsWith("+") && digits.length in 8..15
                }
        }.getOrNull()

        if (!e164Value.isNullOrBlank()) {
            return e164Value
        }

        val fallback = buildString {
            trimmed.forEachIndexed { index, char ->
                when {
                    char.isDigit() -> append(char)
                    char == '+' && index == 0 -> append(char)
                }
            }
        }
        return if (fallback.isNotBlank()) fallback else trimmed
    }

    private fun normalizeUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            return trimmed
        }
        return "https://$trimmed"
    }

    private fun cancelNotification(notificationId: Int) {
        if (notificationId < 0) {
            return
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(notificationId)
    }

    private fun Int.isNightModeActive(): Boolean {
        val mask = android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return this and mask == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
}

private enum class PhoneAction {
    Call,
    OpenDialer,
    WhatsApp,
    Share
}

private enum class LinkAction {
    OpenLink,
    CopyLink,
    ShareLink
}

@Composable
private fun NotificationFlowScreen(
    itemType: String,
    payload: String,
    onPhoneActionSelected: (PhoneAction) -> Unit,
    onLinkActionSelected: (LinkAction) -> Unit,
    onDismiss: () -> Unit
) {
    ActionChooserScreen(
        itemType = itemType,
        payload = payload,
        onPhoneActionSelected = onPhoneActionSelected,
        onLinkActionSelected = onLinkActionSelected,
        onDismiss = onDismiss
    )
}

@Composable
private fun ActionChooserScreen(
    itemType: String,
    payload: String,
    onPhoneActionSelected: (PhoneAction) -> Unit,
    onLinkActionSelected: (LinkAction) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = SupaPhoneTheme.colors
    val isPhone = itemType == NotificationActionIntent.ITEM_TYPE_PHONE
    val heading = if (isPhone) {
        "Choose how to continue"
    } else {
        "Choose what to do with this link"
    }
    val subheading = if (isPhone) payload else payload.take(120)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgBase)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 116.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = colors.textMain
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = heading,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textMain,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = subheading,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(18.dp))
            InlineBannerAd(
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(18.dp))

            if (isPhone) {
                ActionOptionCard(
                    label = "Call",
                    description = "Place the call directly",
                    onClick = { onPhoneActionSelected(PhoneAction.Call) }
                )
                Spacer(Modifier.height(10.dp))
                ActionOptionCard(
                    label = "Open in dialer",
                    description = "Review the number before calling",
                    onClick = { onPhoneActionSelected(PhoneAction.OpenDialer) }
                )
                Spacer(Modifier.height(10.dp))
                ActionOptionCard(
                    label = "WhatsApp",
                    description = "Open the number in WhatsApp",
                    onClick = { onPhoneActionSelected(PhoneAction.WhatsApp) }
                )
                Spacer(Modifier.height(10.dp))
                ActionOptionCard(
                    label = "Share",
                    description = "Share the number with another app",
                    onClick = { onPhoneActionSelected(PhoneAction.Share) }
                )
            } else {
                ActionOptionCard(
                    label = "Open link",
                    description = "Launch the link in your browser",
                    onClick = { onLinkActionSelected(LinkAction.OpenLink) }
                )
                Spacer(Modifier.height(10.dp))
                ActionOptionCard(
                    label = "Copy link",
                    description = "Copy the URL to clipboard",
                    onClick = { onLinkActionSelected(LinkAction.CopyLink) }
                )
                Spacer(Modifier.height(10.dp))
                ActionOptionCard(
                    label = "Share link",
                    description = "Share the URL to another app",
                    onClick = { onLinkActionSelected(LinkAction.ShareLink) }
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            InlineBannerAd(
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ActionOptionCard(
    label: String,
    description: String,
    onClick: () -> Unit
) {
    val colors = SupaPhoneTheme.colors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = colors.bgSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.bgSurfaceHover),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textMain
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted
            )
        }
    }
}
