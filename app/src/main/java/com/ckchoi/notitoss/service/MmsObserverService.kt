package com.ckchoi.notitoss.service

import android.app.Service
import android.content.Intent
import android.database.Cursor
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.net.toUri
import com.ckchoi.notitoss.NotiTossApplication
import com.ckchoi.notitoss.data.NotificationEventEntity
import com.ckchoi.notitoss.data.SourceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MmsObserverService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var observer: ContentObserver? = null
    private var lastMmsMessageId: String? = null
    private var lastSmsMessageId: String? = null

    override fun onCreate() {
        super.onCreate()
        val container = (application as NotiTossApplication).container
        val repository = container.repository
        val forwardDispatcher = container.forwardDispatcher
        forwardDispatcher.ensureMmsObserverNotification()
        startForeground(302, forwardDispatcher.createMmsObserverNotification())

        observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                serviceScope.launch {
                    try {
                        queryLatestSms()
                            ?.takeIf { it.first != lastSmsMessageId }
                            ?.let { (id, event) ->
                                lastSmsMessageId = id
                                repository.recordIncomingSignal(SourceType.SMS, event.receivedAt)
                                repository.processIncomingEvent(event)
                            }
                        queryLatestMms()
                            ?.takeIf { it.first != lastMmsMessageId }
                            ?.let { (id, event) ->
                                lastMmsMessageId = id
                                repository.recordIncomingSignal(SourceType.MMS, event.receivedAt)
                                repository.processIncomingEvent(event)
                            }
                    } catch (error: Throwable) {
                        repository.recordProcessingError(
                            message = "SMS/MMS observer failure: ${error.message ?: error.javaClass.simpleName}",
                        )
                        Log.e(TAG, "Failed to process SMS/MMS content change", error)
                    }
                }
            }
        }

        runCatching {
            lastSmsMessageId = queryLatestSms()?.first
            lastMmsMessageId = queryLatestMms()?.first
        }.onFailure { error ->
            serviceScope.launch {
                repository.recordProcessingError(
                    message = "SMS/MMS observer init failure: ${error.message ?: error.javaClass.simpleName}",
                )
            }
            Log.e(TAG, "Failed to initialize SMS/MMS observer state", error)
        }
        contentResolver.registerContentObserver(Telephony.Sms.Inbox.CONTENT_URI, true, observer!!)
        contentResolver.registerContentObserver(Telephony.Mms.CONTENT_URI, true, observer!!)
    }

    override fun onDestroy() {
        observer?.let { contentResolver.unregisterContentObserver(it) }
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    private fun queryLatestSms(): Pair<String, NotificationEventEntity>? {
        val cursor = contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            null,
            null,
            null,
            "${Telephony.Sms.DATE} DESC",
        ) ?: return null

        cursor.use {
            if (!it.moveToFirst()) return null

            val messageId = it.getString(it.getColumnIndexOrThrow(Telephony.Sms._ID))
            val sender = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)).orEmpty()
            val body = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.BODY)).orEmpty()
            val receivedAt = it.getLong(it.getColumnIndexOrThrow(Telephony.Sms.DATE))
            val subscriptionId = firstValidInt(it, "sub_id", "subscription_id", "sim_id")
            val slotIndex = firstValidInt(it, "slot_id")
            val recipient = resolveRecipientAddress(subscriptionId, slotIndex)

            return messageId to NotificationEventEntity(
                sourceType = SourceType.SMS,
                sourceAppName = resolveSourceAppName(),
                sourcePackageName = Telephony.Sms.getDefaultSmsPackage(this) ?: "sms",
                title = sender.ifBlank { "SMS received" },
                body = body,
                senderAddress = sender.ifBlank { null },
                recipientAddress = recipient,
                receivedAt = receivedAt,
            )
        }
    }

    private fun queryLatestMms(): Pair<String, NotificationEventEntity>? {
        val cursor = contentResolver.query(
            Telephony.Mms.Inbox.CONTENT_URI,
            null,
            null,
            null,
            "${Telephony.Mms.DATE} DESC",
        ) ?: return null

        cursor.use {
            if (!it.moveToFirst()) return null

            val messageId = it.getStringOrNull("_id") ?: return null
            val mmsContent = queryMmsContent(messageId)
            val body = mmsContent.body.ifBlank { "MMS received" }
            val sender = queryMmsAddress(messageId, "137")
            val recipient = resolveRecipientAddress(
                inboxSubscriptionId = firstValidInt(it, "sub_id", "subscription_id", "sim_id", "sub"),
                inboxSlotIndex = firstValidInt(it, "slot_id", "sim_slot", "phone_id"),
            ) ?: queryMmsAddress(messageId, "151")
            val receivedAt = normalizeMmsTimestamp(it.getLongOrNull(Telephony.Mms.DATE))

            return messageId to NotificationEventEntity(
                sourceType = SourceType.MMS,
                sourceAppName = resolveSourceAppName(),
                sourcePackageName = Telephony.Sms.getDefaultSmsPackage(this) ?: "mms",
                title = sender ?: "MMS received",
                body = body,
                senderAddress = sender,
                recipientAddress = recipient,
                attachmentUris = mmsContent.attachmentUris,
                receivedAt = receivedAt,
            )
        }
    }

    private fun queryMmsContent(messageId: String): MmsContent {
        val partsUri = "content://mms/part".toUri()
        val cursor = contentResolver.query(
            partsUri,
            arrayOf("_id", "ct", "text", "name", "cl"),
            "mid = ?",
            arrayOf(messageId),
            null,
        ) ?: return MmsContent()

        cursor.use {
            val builder = StringBuilder()
            val attachmentUris = mutableListOf<String>()
            while (it.moveToNext()) {
                val contentType = it.getString(it.getColumnIndexOrThrow("ct"))
                if (contentType == "text/plain") {
                    builder.append(it.getString(it.getColumnIndexOrThrow("text")).orEmpty())
                } else if (contentType.startsWith("image/")) {
                    val partId = it.getString(it.getColumnIndexOrThrow("_id")).orEmpty()
                    if (partId.isNotBlank()) {
                        attachmentUris += "content://mms/part/$partId"
                    }
                }
            }
            return MmsContent(
                body = builder.toString(),
                attachmentUris = attachmentUris,
            )
        }
    }

    private fun queryMmsAddress(messageId: String, type: String): String? {
        val cursor = contentResolver.query(
            "content://mms/$messageId/addr".toUri(),
            arrayOf("address", "type"),
            "type = ?",
            arrayOf(type),
            null,
        ) ?: return null

        cursor.use {
            return if (it.moveToFirst()) {
                normalizeMmsAddress(it.getString(it.getColumnIndexOrThrow("address")))
            } else {
                null
            }
        }
    }

    private fun Cursor.getStringOrNull(columnName: String): String? {
        val index = getColumnIndex(columnName)
        if (index < 0 || isNull(index)) return null
        return getString(index)
    }

    private fun Cursor.getLongOrNull(columnName: String): Long? {
        val index = getColumnIndex(columnName)
        if (index < 0 || isNull(index)) return null
        return getLong(index)
    }

    private fun firstValidInt(cursor: android.database.Cursor, vararg columnNames: String): Int? {
        columnNames.forEach { columnName ->
            val index = cursor.getColumnIndex(columnName)
            if (index >= 0 && !cursor.isNull(index)) {
                val value = cursor.getInt(index)
                if (value >= 0) return value
            }
        }
        return null
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun resolveRecipientAddress(
        inboxSubscriptionId: Int?,
        inboxSlotIndex: Int?,
    ): String? {
        val subscriptionManager = getSystemService(SubscriptionManager::class.java) ?: return null
        val telephonyManager = getSystemService(TelephonyManager::class.java)

        val candidateSubscriptionIds = buildList {
            listOf(
                inboxSubscriptionId ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID,
                SubscriptionManager.getDefaultSmsSubscriptionId(),
            ).filter { it != SubscriptionManager.INVALID_SUBSCRIPTION_ID }
                .forEach(::add)

            listOf(inboxSlotIndex ?: SubscriptionManager.INVALID_SIM_SLOT_INDEX)
                .filter { it != SubscriptionManager.INVALID_SIM_SLOT_INDEX }
                .forEach { candidateSlot ->
                    addAll(resolveSubscriptionIdsForSlot(subscriptionManager, candidateSlot))
                }

            addAll(activeSubscriptionIds(subscriptionManager))
        }.distinct()

        candidateSubscriptionIds.forEach { subscriptionId ->
            resolvePhoneNumberForSubscription(subscriptionManager, telephonyManager, subscriptionId)
                ?.let { return it }
        }

        return runCatching {
            @Suppress("DEPRECATION")
            telephonyManager?.line1Number
        }.getOrNull()
            ?.let(::normalizePhoneNumber)
            ?.takeIf { it.isNotBlank() }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun resolvePhoneNumberForSubscription(
        subscriptionManager: SubscriptionManager,
        telephonyManager: TelephonyManager?,
        subscriptionId: Int,
    ): String? {
        val activeInfo = runCatching { subscriptionManager.getActiveSubscriptionInfo(subscriptionId) }
            .getOrNull()
        val candidates = buildList {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                add(
                    runCatching { subscriptionManager.getPhoneNumber(subscriptionId) }
                        .getOrNull()
                        .orEmpty(),
                )
            }
            add(
                runCatching {
                    @Suppress("DEPRECATION")
                    activeInfo?.number
                }.getOrNull().orEmpty(),
            )
            add(
                runCatching {
                    @Suppress("DEPRECATION")
                    telephonyManager?.createForSubscriptionId(subscriptionId)?.line1Number
                }.getOrNull().orEmpty(),
            )
        }

        return candidates
            .map(::normalizePhoneNumber)
            .firstOrNull { it.isNotBlank() }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun resolveSubscriptionIdsForSlot(
        subscriptionManager: SubscriptionManager,
        slotIndex: Int,
    ): List<Int> {
        val ids = linkedSetOf<Int>()

        runCatching {
            subscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(slotIndex)
        }.getOrNull()
            ?.subscriptionId
            ?.takeIf { it != SubscriptionManager.INVALID_SUBSCRIPTION_ID }
            ?.let(ids::add)

        runCatching {
            subscriptionManager.activeSubscriptionInfoList.orEmpty()
        }.getOrDefault(emptyList())
            .filter { it.simSlotIndex == slotIndex }
            .mapNotNull { info ->
                info.subscriptionId.takeIf { it != SubscriptionManager.INVALID_SUBSCRIPTION_ID }
            }
            .forEach(ids::add)

        return ids.toList()
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun activeSubscriptionIds(subscriptionManager: SubscriptionManager): List<Int> {
        return runCatching {
            subscriptionManager.activeSubscriptionInfoList.orEmpty()
        }.getOrDefault(emptyList())
            .mapNotNull { info ->
                info.subscriptionId.takeIf { it != SubscriptionManager.INVALID_SUBSCRIPTION_ID }
            }
            .distinct()
    }

    private fun normalizePhoneNumber(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return ""
        return if (trimmed.startsWith("+")) {
            "+" + trimmed.drop(1).filter(Char::isDigit)
        } else {
            trimmed.filter(Char::isDigit)
        }
    }

    private fun normalizeMmsAddress(value: String?): String? {
        val cleaned = value
            ?.substringBefore('/')
            ?.trim()
            ?.takeUnless { it.equals("insert-address-token", ignoreCase = true) }
            ?.takeUnless { it.isBlank() }
            ?: return null

        return normalizePhoneNumber(cleaned).ifBlank { cleaned }
    }

    private fun normalizeMmsTimestamp(value: Long?): Long {
        if (value == null || value <= 0L) return System.currentTimeMillis()
        return if (value < 10_000_000_000L) value * 1000L else value
    }

    private fun resolveSourceAppName(): String {
        val packageName = Telephony.Sms.getDefaultSmsPackage(this).orEmpty()
        if (packageName.isBlank()) return "Messages"

        return runCatching {
            @Suppress("DEPRECATION")
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(applicationInfo).toString()
        }.getOrDefault("Messages")
    }

    companion object {
        private const val TAG = "MmsObserverService"
    }

    private data class MmsContent(
        val body: String = "",
        val attachmentUris: List<String> = emptyList(),
    )
}
