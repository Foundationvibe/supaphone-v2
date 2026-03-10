package com.supaphone.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.supaphone.app.R
import com.supaphone.app.data.EdgeFunctionsClient
import com.supaphone.app.data.SecureStorage
import com.supaphone.app.diagnostics.AppLog
import com.supaphone.app.notification.NotificationActionIntent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FCMService : FirebaseMessagingService() {

    companion object {
        private const val CHANNEL_ID = "supaphone_push"
        private const val CHANNEL_NAME = "SupaPhone Notifications"

        private const val TYPE_CALL = "call"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        if (!SecureStorage.isPaired(applicationContext)) {
            AppLog.i("PUSH_TOKEN_SKIP", "reason=not_paired")
            return
        }
        registerPushToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val data = message.data
        val type = data["type"]?.trim().orEmpty()
        val payload = data["payload"]?.trim().orEmpty()
        val eventId = data["eventId"]?.trim().orEmpty()
        AppLog.i(
            "PUSH_RECEIVED",
            "type=${if (type.isBlank()) "unknown" else type} payload_len=${payload.length} event=${eventId.ifBlank { "none" }}"
        )

        if (type.isBlank() || payload.isBlank()) {
            AppLog.w("PUSH_IGNORED", "reason=missing_type_or_payload")
            return
        }

        showNotification(type, payload, eventId)
        if (eventId.isNotBlank()) {
            acknowledgeEvent(eventId, status = "delivered", ackMessage = "Delivered to Android device notification tray.")
        }
    }

    private fun registerPushToken(token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val phoneClientId = SecureStorage.getDeviceId(applicationContext).orEmpty()
                if (phoneClientId.isBlank()) {
                    AppLog.i("PUSH_TOKEN_SKIP", "reason=missing_paired_client")
                    return@runCatching
                }
                val phoneClientSecret = SecureStorage.getOrCreateClientAuthSecret(applicationContext)
                val phoneLabel = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
                AppLog.i("PUSH_TOKEN_REGISTER_START", "client=$phoneClientId")
                EdgeFunctionsClient.registerPushToken(
                    phoneClientId = phoneClientId,
                    phoneClientSecret = phoneClientSecret,
                    phoneLabel = phoneLabel,
                    pushToken = token
                ).getOrThrow()
                AppLog.i("PUSH_TOKEN_REGISTER_OK", "client=$phoneClientId")
            }.onFailure { error ->
                AppLog.e("PUSH_TOKEN_REGISTER_FAIL", "reason=${error.message}", error)
            }
        }
    }

    private fun acknowledgeEvent(eventId: String, status: String, ackMessage: String) {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val phoneClientId = SecureStorage.getDeviceId(applicationContext).orEmpty()
                if (phoneClientId.isBlank()) {
                    AppLog.w("PUSH_ACK_SKIP", "reason=missing_paired_client")
                    return@runCatching
                }
                val phoneClientSecret = SecureStorage.getOrCreateClientAuthSecret(applicationContext)
                AppLog.d("PUSH_ACK_START", "event=$eventId status=$status")
                EdgeFunctionsClient.ackPushEvent(
                    eventId = eventId,
                    targetClientId = phoneClientId,
                    targetClientSecret = phoneClientSecret,
                    status = status,
                    ackMessage = ackMessage
                ).getOrThrow()
                AppLog.d("PUSH_ACK_OK", "event=$eventId status=$status")
            }.onFailure { error ->
                AppLog.e("PUSH_ACK_FAIL", "event=$eventId reason=${error.message}", error)
            }
        }
    }

    private fun showNotification(type: String, payload: String, eventId: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(manager)
        val notificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        val useCallActions = type == TYPE_CALL || looksLikePhonePayload(payload)
        val hideNotificationContent = SecureStorage.shouldHideNotificationContent(this)
        val body = if (hideNotificationContent) {
            if (useCallActions) {
                "Tap to choose how to continue in SupaPhone."
            } else {
                "Tap to review this link in SupaPhone."
            }
        } else {
            payload.take(160)
        }
        val publicCategory = if (useCallActions) {
            NotificationCompat.CATEGORY_CALL
        } else {
            NotificationCompat.CATEGORY_MESSAGE
        }
        val publicNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("SupaPhone")
            .setContentText(if (useCallActions) "New call item" else "New link item")
            .setCategory(publicCategory)
            .build()
        AppLog.i(
            "PUSH_NOTIFY_RENDER",
            "notification_id=$notificationId template=${if (useCallActions) "call" else "link"} hidden=$hideNotificationContent"
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicNotification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        builder
            .setContentTitle(if (useCallActions) "Call Received" else "Link Received")
            .setCategory(if (useCallActions) NotificationCompat.CATEGORY_CALL else NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(
                buildProcessingPendingIntent(
                    itemType = if (useCallActions) {
                        NotificationActionIntent.ITEM_TYPE_PHONE
                    } else {
                        NotificationActionIntent.ITEM_TYPE_LINK
                    },
                    payload = payload,
                    eventId = eventId,
                    notificationId = notificationId,
                    requestCode = notificationId
                )
            )

        manager.notify(notificationId, builder.build())
        AppLog.d(
            "PUSH_NOTIFY_SHOWN",
            "notification_id=$notificationId mode=${if (useCallActions) "phone" else "link"} body_tap=app_flow"
        )
    }

    private fun buildProcessingPendingIntent(
        itemType: String,
        payload: String,
        eventId: String,
        notificationId: Int,
        requestCode: Int
    ): PendingIntent {
        val intent = NotificationActionIntent.createIntent(
            context = this,
            itemType = itemType,
            payload = payload,
            notificationId = notificationId,
            eventId = eventId
        )
        return PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun looksLikePhonePayload(payload: String): Boolean {
        val value = payload.trim()
        if (value.isEmpty()) {
            return false
        }
        if (value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true)) {
            return false
        }
        if (value.contains("/") || value.contains("?") || value.contains("#")) {
            return false
        }
        val digitsOnly = value.filter { it.isDigit() }
        if (digitsOnly.length < 6) {
            return false
        }
        return value.all {
            it.isDigit() || it == '+' || it == '(' || it == ')' || it == '-' || it == ' '
        }
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        )
        channel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        manager.createNotificationChannel(channel)
    }
}
