package com.ckchoi.notitoss.service

import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.ckchoi.notitoss.NotiTossApplication
import com.ckchoi.notitoss.data.NotificationEventEntity
import com.ckchoi.notitoss.data.SourceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NotificationCaptureService : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Notification listener connected")
        serviceScope.launch {
            activeNotifications.orEmpty().forEach { sbn ->
                runCatching { captureNotification(sbn) }
                    .onFailure { error -> Log.e(TAG, "Failed to backfill active notification", error) }
            }
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "Notification listener disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        runCatching {
            captureNotification(sbn)
        }.onFailure { error ->
            Log.e(TAG, "Failed to capture notification", error)
        }
    }

    private fun captureNotification(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        if (sbn.packageName == Telephony.Sms.getDefaultSmsPackage(this)) return

        val extras = sbn.notification.extras
        val title = extras?.getCharSequence("android.title")?.toString().orEmpty()
        val body = extras?.getCharSequence("android.text")?.toString()
            ?: extras?.getCharSequence("android.bigText")?.toString()
            ?: ""

        if (title.isBlank() && body.isBlank()) return

        val appLabel = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        } catch (_: Throwable) {
            sbn.packageName
        }

        val event = NotificationEventEntity(
            sourceType = SourceType.APP,
            sourceAppName = appLabel,
            sourcePackageName = sbn.packageName,
            title = title.ifBlank { appLabel },
            body = body,
        )

        val repository = (application as NotiTossApplication).container.repository
        serviceScope.launch {
            try {
                repository.recordIncomingSignal(SourceType.APP, event.receivedAt)
                repository.processIncomingEvent(event)
            } catch (throwable: Throwable) {
                repository.recordProcessingError(
                    message = "Notification capture failure: ${throwable.message ?: throwable.javaClass.simpleName}",
                )
                throw throwable
            }
        }
    }

    companion object {
        private const val TAG = "NotificationCapture"
    }
}
