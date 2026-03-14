package com.supaphone.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.supaphone.app.data.BackendConfig
import com.supaphone.app.data.EdgeFunctionsClient
import com.supaphone.app.data.PairedDevice
import com.supaphone.app.data.SecureStorage
import com.supaphone.app.diagnostics.AppLog
import com.supaphone.app.ui.components.BrandLogo
import com.supaphone.app.ui.components.InlineBannerAd
import com.supaphone.app.ui.theme.SupaPhoneColors
import com.supaphone.app.ui.theme.SupaPhoneTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Locale

private const val MAX_PAIRED_BROWSERS = 5

private data class RegionOption(
    val regionCode: String,
    val displayName: String,
    val countryCallingCode: Int,
) {
    val label: String = "$displayName (+$countryCallingCode)"
}

private fun buildRegionOptions(): List<RegionOption> {
    val phoneUtil = PhoneNumberUtil.getInstance()
    val locale = Locale.getDefault()
    return phoneUtil.getSupportedRegions()
        .asSequence()
        .map { it.uppercase(Locale.ROOT) }
        .filter { it.length == 2 }
        .mapNotNull { code ->
            val callingCode = phoneUtil.getCountryCodeForRegion(code)
            if (callingCode <= 0) return@mapNotNull null
            val countryName = Locale("", code).getDisplayCountry(locale).ifBlank { code }
            RegionOption(
                regionCode = code,
                displayName = countryName,
                countryCallingCode = callingCode
            )
        }
        .sortedBy { it.displayName.lowercase(locale) }
        .toList()
}

@Composable
fun HomeScreen(
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onAddDevice: () -> Unit,
    onUnpair: () -> Unit,
) {
    val colors = SupaPhoneTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val pairedBrowsers = remember { mutableStateListOf<PairedDevice>() }
    var helperMessage by remember { mutableStateOf("Load paired browsers from backend.") }
    var helperIsError by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var actionInProgress by remember { mutableStateOf(false) }
    var actionMenuDeviceId by remember { mutableStateOf<String?>(null) }
    var renameTargetDevice by remember { mutableStateOf<PairedDevice?>(null) }
    var renameValue by remember { mutableStateOf("") }
    val regionOptions = remember { buildRegionOptions() }
    var regionMenuExpanded by remember { mutableStateOf(false) }
    var selectedRegionCode by remember {
        mutableStateOf(SecureStorage.resolveDefaultRegionCode(context))
    }
    val selectedRegion = regionOptions.firstOrNull { it.regionCode == selectedRegionCode }

    fun refreshPairedBrowsers() {
        if (loading) return
        if (!BackendConfig.isConfigured()) {
            AppLog.w("HOME_REFRESH_SKIP", "reason=backend_not_configured")
            helperIsError = true
            helperMessage = "Backend keys missing. Configure android-app/local.properties."
            pairedBrowsers.clear()
            return
        }

        val phoneClientId = SecureStorage.getDeviceId(context).orEmpty()
        val phoneClientSecret = SecureStorage.getOrCreateClientAuthSecret(context)
        if (phoneClientId.isBlank()) {
            AppLog.w("HOME_REFRESH_SKIP", "reason=phone_not_paired")
            helperIsError = true
            helperMessage = "Phone not paired yet. Complete pairing first."
            pairedBrowsers.clear()
            return
        }

        AppLog.i("HOME_REFRESH_START", "client=$phoneClientId")
        loading = true
        helperIsError = false
        helperMessage = "Refreshing paired browsers..."

        scope.launch {
            val result = EdgeFunctionsClient.fetchPairedDevices(
                clientId = phoneClientId,
                clientSecret = phoneClientSecret,
                clientType = "android"
            )
            loading = false

            result.onSuccess { devices ->
                pairedBrowsers.clear()
                pairedBrowsers.addAll(devices.take(MAX_PAIRED_BROWSERS))
                actionMenuDeviceId = null
                helperIsError = false
                AppLog.i("HOME_REFRESH_OK", "count=${pairedBrowsers.size}")
                helperMessage = if (pairedBrowsers.isEmpty()) {
                    "No paired browsers found yet."
                } else {
                    "Paired browsers updated."
                }
            }.onFailure { error ->
                if (error is CancellationException) {
                    AppLog.d("HOME_REFRESH_CANCELLED", "reason=scope_left_composition")
                    return@onFailure
                }
                AppLog.e("HOME_REFRESH_FAIL", "reason=${error.message}", error)
                pairedBrowsers.clear()
                actionMenuDeviceId = null
                helperIsError = true
                helperMessage = error.message ?: "Unable to fetch paired browsers."
            }
        }
    }

    fun renamePairedBrowser(browser: PairedDevice, newLabel: String) {
        if (actionInProgress) return
        val phoneClientId = SecureStorage.getDeviceId(context).orEmpty()
        val phoneClientSecret = SecureStorage.getOrCreateClientAuthSecret(context)
        if (phoneClientId.isBlank()) {
            helperIsError = true
            helperMessage = "Phone is not paired. Pair first and retry."
            return
        }

        actionInProgress = true
        helperIsError = false
        helperMessage = "Renaming browser..."
        AppLog.i("HOME_RENAME_START", "target=${browser.id}")

        scope.launch {
            val result = EdgeFunctionsClient.renamePairedDevice(
                requesterClientId = phoneClientId,
                requesterClientSecret = phoneClientSecret,
                requesterClientType = "android",
                targetClientId = browser.id,
                newLabel = newLabel
            )
            actionInProgress = false

            result.onSuccess {
                helperIsError = false
                helperMessage = "Browser renamed."
                AppLog.i("HOME_RENAME_OK", "target=${browser.id}")
                refreshPairedBrowsers()
            }.onFailure { error ->
                if (error is CancellationException) {
                    AppLog.d("HOME_RENAME_CANCELLED", "target=${browser.id}")
                    return@onFailure
                }
                AppLog.e("HOME_RENAME_FAIL", "target=${browser.id} reason=${error.message}", error)
                helperIsError = true
                helperMessage = error.message ?: "Unable to rename browser."
            }
        }
    }

    fun removePairedBrowser(browser: PairedDevice) {
        if (actionInProgress) return
        val phoneClientId = SecureStorage.getDeviceId(context).orEmpty()
        val phoneClientSecret = SecureStorage.getOrCreateClientAuthSecret(context)
        if (phoneClientId.isBlank()) {
            helperIsError = true
            helperMessage = "Phone is not paired. Pair first and retry."
            return
        }

        actionInProgress = true
        helperIsError = false
        helperMessage = "Removing browser..."
        AppLog.i("HOME_REMOVE_START", "target=${browser.id}")

        scope.launch {
            val result = EdgeFunctionsClient.removePairedDevice(
                requesterClientId = phoneClientId,
                requesterClientSecret = phoneClientSecret,
                requesterClientType = "android",
                targetClientId = browser.id
            )
            actionInProgress = false

            result.onSuccess {
                helperIsError = false
                helperMessage = "Browser removed."
                AppLog.i("HOME_REMOVE_OK", "target=${browser.id}")
                refreshPairedBrowsers()
            }.onFailure { error ->
                if (error is CancellationException) {
                    AppLog.d("HOME_REMOVE_CANCELLED", "target=${browser.id}")
                    return@onFailure
                }
                AppLog.e("HOME_REMOVE_FAIL", "target=${browser.id} reason=${error.message}", error)
                helperIsError = true
                helperMessage = error.message ?: "Unable to remove browser."
            }
        }
    }

    fun updateSelectedRegion(option: RegionOption) {
        selectedRegionCode = option.regionCode
        SecureStorage.setDefaultRegionCode(context, option.regionCode)
        helperIsError = false
        helperMessage = "WhatsApp region set to ${option.label}."
        AppLog.i("HOME_REGION_SET", "region=${option.regionCode} code=${option.countryCallingCode}")
    }

    fun unpairCurrentPhone() {
        if (actionInProgress || loading) return

        if (!BackendConfig.isConfigured()) {
            helperIsError = true
            helperMessage = "Backend must be reachable to revoke active pairings before unpairing."
            return
        }

        val phoneClientId = SecureStorage.getDeviceId(context).orEmpty()
        val phoneClientSecret = SecureStorage.getOrCreateClientAuthSecret(context)
        if (phoneClientId.isBlank()) {
            SecureStorage.clearPairing(context)
            onUnpair()
            return
        }

        actionInProgress = true
        helperIsError = false
        helperMessage = "Revoking active pairings..."
        AppLog.i("HOME_UNPAIR_START", "client=$phoneClientId")

        scope.launch {
            val pairedDevicesResult = EdgeFunctionsClient.fetchPairedDevices(
                clientId = phoneClientId,
                clientSecret = phoneClientSecret,
                clientType = "android"
            )

            pairedDevicesResult.onSuccess { browsers ->
                for (browser in browsers) {
                    val revokeResult = EdgeFunctionsClient.removePairedDevice(
                        requesterClientId = phoneClientId,
                        requesterClientSecret = phoneClientSecret,
                        requesterClientType = "android",
                        targetClientId = browser.id
                    )
                    revokeResult.onFailure { error ->
                        AppLog.e("HOME_UNPAIR_FAIL", "target=${browser.id} reason=${error.message}", error)
                        actionInProgress = false
                        helperIsError = true
                        helperMessage = error.message
                            ?: "Unable to revoke all pairings. Retry unpairing before removing this device."
                        refreshPairedBrowsers()
                        return@launch
                    }
                }

                SecureStorage.clearPairing(context)
                pairedBrowsers.clear()
                actionMenuDeviceId = null
                actionInProgress = false
                helperIsError = false
                helperMessage = "This phone was unpaired."
                AppLog.i("HOME_UNPAIR_OK", "revoked=${browsers.size}")
                onUnpair()
            }.onFailure { error ->
                if (error is CancellationException) {
                    AppLog.d("HOME_UNPAIR_CANCELLED", "reason=scope_left_composition")
                    actionInProgress = false
                    return@onFailure
                }
                AppLog.e("HOME_UNPAIR_FAIL", "reason=${error.message}", error)
                actionInProgress = false
                helperIsError = true
                helperMessage = error.message
                    ?: "Unable to revoke active pairings. Retry before removing this device."
            }
        }
    }

    LaunchedEffect(Unit) {
        AppLog.i("HOME_SCREEN_OPEN")
        refreshPairedBrowsers()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = colors.bgBase,
        bottomBar = {
            BottomAppBar(
                containerColor = colors.bgSurface,
                contentColor = colors.textMain,
                tonalElevation = 8.dp
            ) {
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, "Settings", tint = colors.textMuted)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onOpenHistory) {
                    Icon(Icons.Default.History, "Recent Pushes", tint = colors.textMuted)
                }
                IconButton(onClick = ::unpairCurrentPhone) {
                    Icon(Icons.AutoMirrored.Filled.Logout, "Unpair", tint = colors.danger)
                }
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            BrandLogo(
                modifier = Modifier
                    .fillMaxWidth(0.54f)
                    .height(40.dp)
            )

            Spacer(Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(colors.success)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Pairing-Only Mode Active",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
            }

            Spacer(Modifier.height(24.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = colors.bgSurface,
                border = BorderStroke(1.dp, colors.bgSurfaceHover),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "WhatsApp Region",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textMain
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Used to format local numbers with the correct country code.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted
                    )
                    Spacer(Modifier.height(10.dp))
                    Box {
                        OutlinedButton(
                            onClick = { regionMenuExpanded = true },
                            enabled = !loading && !actionInProgress,
                            modifier = Modifier.fillMaxWidth(),
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                contentColor = colors.textMain
                            )
                        ) {
                            Text(
                                text = selectedRegion?.label
                                    ?: "${selectedRegionCode.uppercase(Locale.ROOT)} (Unsupported)",
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = colors.textMuted
                            )
                        }
                        DropdownMenu(
                            expanded = regionMenuExpanded,
                            onDismissRequest = { regionMenuExpanded = false }
                        ) {
                            regionOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = {
                                        regionMenuExpanded = false
                                        updateSelectedRegion(option)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            InlineBannerAd(modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Paired Browsers",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textMain,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${pairedBrowsers.size}/$MAX_PAIRED_BROWSERS paired",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textMuted,
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onAddDevice,
                    enabled = !loading && !actionInProgress,
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        contentColor = colors.textMain
                    ),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add Device")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { refreshPairedBrowsers() },
                    enabled = !loading && !actionInProgress,
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        contentColor = colors.textMain
                    ),
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (loading) "Refreshing..." else "Refresh")
                }
            }

            Spacer(Modifier.height(12.dp))

            if (loading || actionInProgress) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.primary,
                    trackColor = colors.bgSurfaceHover
                )
                Spacer(Modifier.height(10.dp))
            }

            if (pairedBrowsers.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = colors.bgSurface,
                    border = BorderStroke(1.dp, colors.bgSurfaceHover),
                ) {
                    Text(
                        text = "No paired browsers. Complete pairing from browser extension, then tap Refresh.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(pairedBrowsers, key = { it.id }) { browser ->
                        BrowserCard(
                            browser = browser,
                            colors = colors,
                            menuExpanded = actionMenuDeviceId == browser.id,
                            menuEnabled = !loading && !actionInProgress,
                            onMenuExpandedChange = { expanded ->
                                actionMenuDeviceId = if (expanded) browser.id else null
                            },
                            onRenameRequest = {
                                actionMenuDeviceId = null
                                renameTargetDevice = browser
                                renameValue = browser.label
                            },
                            onRemoveRequest = {
                                actionMenuDeviceId = null
                                removePairedBrowser(browser)
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = helperMessage,
                style = MaterialTheme.typography.bodySmall,
                color = if (helperIsError) colors.danger else colors.textMuted,
            )
            Spacer(Modifier.height(8.dp))
        }

        renameTargetDevice?.let { browser ->
            AlertDialog(
                onDismissRequest = {
                    if (!actionInProgress) {
                        renameTargetDevice = null
                    }
                },
                title = { Text("Rename Browser") },
                text = {
                    OutlinedTextField(
                        value = renameValue,
                        onValueChange = { renameValue = it },
                        singleLine = true,
                        label = { Text("Device Name") }
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = !actionInProgress,
                        onClick = {
                            val value = renameValue.trim()
                            if (value.length < 2 || value.length > 48) {
                                helperIsError = true
                                helperMessage = "Name must be between 2 and 48 characters."
                                return@TextButton
                            }
                            renameTargetDevice = null
                            renamePairedBrowser(browser, value)
                        }
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = !actionInProgress,
                        onClick = { renameTargetDevice = null }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun BrowserCard(
    browser: PairedDevice,
    colors: SupaPhoneColors,
    menuExpanded: Boolean,
    menuEnabled: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onRenameRequest: () -> Unit,
    onRemoveRequest: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = colors.bgSurface,
        border = BorderStroke(
            1.dp,
            if (browser.online) colors.primary.copy(alpha = 0.3f) else colors.bgSurfaceHover
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(12.dp),
                color = colors.bgElevated,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Computer,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = colors.primary,
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = browser.label,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textMain,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = browser.platform?.takeIf { it.isNotBlank() } ?: "Paired browser",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Box {
                    IconButton(
                        enabled = menuEnabled,
                        onClick = { onMenuExpandedChange(!menuExpanded) }
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Browser actions",
                            tint = colors.textMuted
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { onMenuExpandedChange(false) }
                    ) {
                        DropdownMenuItem(
                            enabled = menuEnabled,
                            text = { Text("Rename") },
                            onClick = {
                                onMenuExpandedChange(false)
                                onRenameRequest()
                            }
                        )
                        DropdownMenuItem(
                            enabled = menuEnabled,
                            text = { Text("Remove", color = colors.danger) },
                            onClick = {
                                onMenuExpandedChange(false)
                                onRemoveRequest()
                            }
                        )
                    }
                }
            }
        }
    }
}
