package com.supaphone.app.notification

import android.content.Context
import android.content.Intent

object NotificationActionIntent {
    const val EXTRA_ITEM_TYPE = "notification_item_type"
    const val EXTRA_PAYLOAD = "notification_payload"
    const val EXTRA_NOTIFICATION_ID = "notification_id"
    const val EXTRA_EVENT_ID = "notification_event_id"

    const val ITEM_TYPE_PHONE = "phone"
    const val ITEM_TYPE_LINK = "link"

    fun createIntent(
        context: Context,
        itemType: String,
        payload: String,
        notificationId: Int,
        eventId: String
    ): Intent {
        return Intent(context, NotificationProcessingActivity::class.java).apply {
            putExtra(EXTRA_ITEM_TYPE, itemType)
            putExtra(EXTRA_PAYLOAD, payload)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(EXTRA_EVENT_ID, eventId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
    }
}
