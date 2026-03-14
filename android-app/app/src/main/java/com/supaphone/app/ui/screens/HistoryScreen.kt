package com.supaphone.app.ui.screens

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.supaphone.app.data.BackendConfig
import com.supaphone.app.data.EdgeFunctionsClient
import com.supaphone.app.data.RecentPush
import com.supaphone.app.data.SecureStorage
import com.supaphone.app.diagnostics.AppLog
import com.supaphone.app.notification.NotificationActionIntent
import com.supaphone.app.ui.components.InlineBannerAd
import com.supaphone.app.ui.theme.SupaPhoneColors
import com.supaphone.app.ui.theme.SupaPhoneTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class HistoryItem(
    val id: String,
    val type: String,
    val content: String,
    val time: String,
)

private fun normalizeUrl(rawUrl: String): String {
    val trimmed = rawUrl.trim()
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        return trimmed
    }
    return "https://$trimmed"
}

private fun looksLikeLink(raw: String): Boolean {
    val value = raw.trim()
    if (value.isEmpty()) return false
    if (value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true)) {
        return true
    }
    if (value.contains(" ")) {
        return false
    }

    val candidate = if (value.contains("://")) value else "https://$value"
    return runCatching {
        val uri = Uri.parse(candidate)
        val host = uri.host?.trim().orEmpty()
        if (host.isBlank()) {
            return@runCatching false
        }
        val hostLower = host.lowercase()
        val isIpv4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$").matches(hostLower)
        val looksLikeDomain = hostLower.contains(".") || hostLower == "localhost" || isIpv4
        val hasPathOrQuery = !uri.path.isNullOrBlank() || !uri.query.isNullOrBlank() || !uri.fragment.isNullOrBlank()
        looksLikeDomain || hasPathOrQuery
    }.getOrDefault(false)
}

private fun formatTimestamp(iso: String?): String {
    if (iso.isNullOrBlank()) return "Unknown time"
    return try {
        val instant = Instant.parse(iso)
        val formatter = DateTimeFormatter.ofPattern("dd MMM, HH:mm").withZone(ZoneId.systemDefault())
        formatter.format(instant)
    } catch (_: Exception) {
        "Unknown time"
    }
}

private fun toHistoryItem(push: RecentPush): HistoryItem {
    val inferredType = when {
        looksLikeLink(push.content) -> "link"
        push.type == "call" -> "call"
        else -> "link"
    }
    return HistoryItem(
        id = push.id,
        type = inferredType,
        content = push.content,
        time = formatTimestamp(push.createdAt)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val colors = SupaPhoneTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val history = remember { mutableStateListOf<HistoryItem>() }
    var helperMessage by remember { mutableStateOf("Fetching recent pushes...") }
    var helperIsError by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }

    fun loadHistory() {
        if (loading) return
        if (!BackendConfig.isConfigured()) {
            AppLog.w("HISTORY_REFRESH_SKIP", "reason=backend_not_configured")
            helperIsError = true
            helperMessage = "Backend keys missing. Configure android-app/local.properties."
            history.clear()
            return
        }

        val phoneClientId = SecureStorage.getDeviceId(context).orEmpty()
        val phoneClientSecret = SecureStorage.getOrCreateClientAuthSecret(context)
        if (phoneClientId.isBlank()) {
            AppLog.w("HISTORY_REFRESH_SKIP", "reason=phone_not_paired")
            helperIsError = true
            helperMessage = "Phone not paired yet."
            history.clear()
            return
        }

        AppLog.i("HISTORY_REFRESH_START", "client=$phoneClientId")
        loading = true
        helperIsError = false
        helperMessage = "Refreshing recent pushes..."

        scope.launch {
            val result = EdgeFunctionsClient.fetchRecentPushes(
                clientId = phoneClientId,
                clientSecret = phoneClientSecret
            )
            loading = false

            result.onSuccess { pushes ->
                history.clear()
                history.addAll(pushes.map(::toHistoryItem))
                helperIsError = false
                AppLog.i("HISTORY_REFRESH_OK", "count=${history.size}")
                helperMessage = if (history.isEmpty()) "No recent pushes yet." else "Recent pushes updated."
            }.onFailure { error ->
                if (error is CancellationException) {
                    AppLog.d("HISTORY_REFRESH_CANCELLED", "reason=scope_left_composition")
                    return@onFailure
                }
                AppLog.e("HISTORY_REFRESH_FAIL", "reason=${error.message}", error)
                helperIsError = true
                helperMessage = error.message ?: "Unable to load recent pushes."
                history.clear()
            }
        }
    }

    fun openHistoryChooser(item: HistoryItem) {
        val isLink = item.type == "link" || looksLikeLink(item.content)
        val normalizedPayload = if (isLink) {
            normalizeUrl(item.content)
        } else {
            item.content.trim()
        }
        val itemType = if (isLink) {
            NotificationActionIntent.ITEM_TYPE_LINK
        } else {
            NotificationActionIntent.ITEM_TYPE_PHONE
        }

        AppLog.i("HISTORY_OPEN_CHOOSER", "type=$itemType")
        context.startActivity(
            NotificationActionIntent.createIntent(
                context = context,
                itemType = itemType,
                payload = normalizedPayload,
                notificationId = -1,
                eventId = ""
            )
        )
    }

    LaunchedEffect(Unit) {
        AppLog.i("HISTORY_SCREEN_OPEN")
        loadHistory()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgBase)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        TopAppBar(
            title = { Text("Recent Pushes", fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = colors.textMuted)
                }
            },
            actions = {
                IconButton(onClick = { loadHistory() }, enabled = !loading) {
                    Icon(Icons.Default.Refresh, "Refresh", tint = colors.textMuted)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = colors.bgBase,
                titleContentColor = colors.textMain,
            ),
        )

        if (loading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = colors.primary,
                trackColor = colors.bgSurfaceHover
            )
        }

        if (history.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(helperMessage, color = if (helperIsError) colors.danger else colors.textMuted)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = helperMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (helperIsError) colors.danger else colors.textMuted,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                InlineBannerAd(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(history, key = { it.id }) { item ->
                        HistoryCard(
                            item = item,
                            colors = colors,
                            onCardClick = { openHistoryChooser(item) },
                            onIconClick = { openHistoryChooser(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(
    item: HistoryItem,
    colors: SupaPhoneColors,
    onCardClick: () -> Unit,
    onIconClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCardClick),
        shape = RoundedCornerShape(12.dp),
        color = colors.bgSurface,
        border = BorderStroke(1.dp, colors.bgSurfaceHover),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(10.dp),
                color = if (item.type == "link") colors.primary.copy(alpha = 0.1f)
                else colors.success.copy(alpha = 0.1f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (item.type == "link") Icons.Default.Link else Icons.Default.PhoneInTalk,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (item.type == "link") colors.primary else colors.success,
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.content,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = colors.textMain,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = item.time,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted,
                )
            }

            IconButton(onClick = onIconClick, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Open actions",
                    modifier = Modifier.size(16.dp),
                    tint = colors.textMuted,
                )
            }
        }
    }
}
