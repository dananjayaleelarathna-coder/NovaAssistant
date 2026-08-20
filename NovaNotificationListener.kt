package com.nova.assistant.engine

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NotificationSummary(val appLabel: String, val title: String?, val hasContent: Boolean)

/**
 * Only active once the user explicitly grants Notification Access in system settings.
 * Exposes a simple in-memory summary list — titles/sender only by default, since reading
 * full notification bodies aloud is more privacy-sensitive than the user likely intends
 * when they just ask "do I have any messages?".
 */
class NovaNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        refreshSummaries()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        refreshSummaries()
    }

    private fun refreshSummaries() {
        val list = activeNotifications?.map { sbn ->
            val extras = sbn.notification.extras
            NotificationSummary(
                appLabel = sbn.packageName,
                title = extras.getString(android.app.Notification.EXTRA_TITLE),
                hasContent = extras.getCharSequence(android.app.Notification.EXTRA_TEXT) != null
            )
        } ?: emptyList()
        _summaries.value = list
    }

    companion object {
        private val _summaries = MutableStateFlow<List<NotificationSummary>>(emptyList())
        val summaries = _summaries.asStateFlow()
    }
}
