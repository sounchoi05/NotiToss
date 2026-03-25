package com.ckchoi.notitoss.service

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Build
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.ckchoi.notitoss.NotiTossApplication
import com.ckchoi.notitoss.data.NotificationEventEntity
import com.ckchoi.notitoss.data.SourceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (
            intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION &&
            intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION
        ) {
            return
        }

        val pendingResult = goAsync()
        receiverScope.launch {
            val repository = (context.applicationContext as NotiTossApplication).container.repository
            try {
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                if (messages.isEmpty()) return@launch

                val body = buildString {
                    messages.forEach { append(it.messageBody.orEmpty()) }
                }
                val firstMessage = messages.firstOrNull()
                val broadcastSender = firstMessage?.displayOriginatingAddress
                    ?.takeIf { it.isNotBlank() }
                    ?: firstMessage?.originatingAddress?.takeIf { it.isNotBlank() }
                val inboxDetails = queryLatestSmsDetails(context, body)
                val sender = inboxDetails?.senderAddress ?: broadcastSender
                val recipient = resolveRecipientAddress(
                    context = context,
                    intent = intent,
                    inboxSubscriptionId = inboxDetails?.subscriptionId,
                    inboxSlotIndex = inboxDetails?.slotIndex,
                )

                Log.d(
                    TAG,
                    "SMS received sender=$sender recipient=$recipient " +
                        "broadcastSender=$broadcastSender inboxSubId=${inboxDetails?.subscriptionId} inboxSlot=${inboxDetails?.slotIndex}",
                )

                val event = NotificationEventEntity(
                    sourceType = SourceType.SMS,
                    sourceAppName = resolveSourceAppName(context),
                    sourcePackageName = Telephony.Sms.getDefaultSmsPackage(context) ?: "sms",
                    title = sender ?: "SMS received",
                    body = body,
                    recipientAddress = recipient,
                    senderAddress = sender,
                )

                repository.recordIncomingSignal(SourceType.SMS, event.receivedAt)
                repository.processIncomingEvent(event)
            } catch (throwable: Throwable) {
                Log.e(TAG, "Failed to process SMS broadcast", throwable)
                repository.recordProcessingError(
                    message = "SMS receiver failure: ${throwable.message ?: throwable.javaClass.simpleName}",
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun resolveRecipientAddress(
        context: Context,
        intent: Intent,
        inboxSubscriptionId: Int?,
        inboxSlotIndex: Int?,
    ): String? {
        if (!hasPhoneNumberReadPermission(context)) return null

        val subscriptionManager = context.getSystemService(SubscriptionManager::class.java) ?: return null
        val telephonyManager = context.getSystemService(TelephonyManager::class.java)

        val explicitSubscriptionId = intent.getIntExtra(
            SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
            SubscriptionManager.INVALID_SUBSCRIPTION_ID,
        )
        val defaultSubscriptionId = SubscriptionManager.getDefaultSmsSubscriptionId()
        val slotIndex = intent.getIntExtra(
            SubscriptionManager.EXTRA_SLOT_INDEX,
            SubscriptionManager.INVALID_SIM_SLOT_INDEX,
        )

        val candidateSubscriptionIds = buildList {
            listOf(
                inboxSubscriptionId ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID,
                explicitSubscriptionId,
                defaultSubscriptionId,
            ).filter { it != SubscriptionManager.INVALID_SUBSCRIPTION_ID }
                .forEach(::add)

            listOf(
                inboxSlotIndex ?: SubscriptionManager.INVALID_SIM_SLOT_INDEX,
                slotIndex,
            ).filter { it != SubscriptionManager.INVALID_SIM_SLOT_INDEX }
                .forEach { candidateSlot ->
                    addAll(resolveSubscriptionIdsForSlot(subscriptionManager, candidateSlot))
                }

            addAll(activeSubscriptionIds(subscriptionManager))
        }.distinct()

        candidateSubscriptionIds.forEach { subscriptionId ->
            resolvePhoneNumberForSubscription(
                subscriptionManager = subscriptionManager,
                telephonyManager = telephonyManager,
                subscriptionId = subscriptionId,
            )?.let { return it }
        }

        return runCatching {
            @Suppress("DEPRECATION")
            telephonyManager?.line1Number
        }.getOrNull()
            ?.let(::normalizePhoneNumber)
            ?.takeIf { it.isNotBlank() }
    }

    @SuppressLint("MissingPermission")
    private fun queryLatestSmsDetails(
        context: Context,
        messageBody: String,
    ): SmsDetails? {
        if (!hasSmsReadPermission(context)) return null

        val since = System.currentTimeMillis() - 2 * 60 * 1_000L
        val cursor = context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            null,
            "${Telephony.Sms.DATE} >= ?",
            arrayOf(since.toString()),
            "${Telephony.Sms.DATE} DESC",
        ) ?: return null

        cursor.use {
            var bestMatch: SmsDetails? = null
            var bestScore = Int.MIN_VALUE
            while (it.moveToNext()) {
                val rowBody = it.getStringOrNull(Telephony.Sms.BODY).orEmpty()
                val score = smsBodyMatchScore(messageBody, rowBody)
                if (score > bestScore) {
                    bestScore = score
                    bestMatch = SmsDetails(
                        senderAddress = it.getStringOrNull(Telephony.Sms.ADDRESS)
                            ?.trim()
                            ?.takeIf { value -> value.isNotBlank() },
                        subscriptionId = firstValidInt(it, "sub_id", "subscription_id", "sim_id"),
                        slotIndex = firstValidInt(it, "slot_id"),
                    )
                }
                if (bestScore >= 3) break
            }
            return bestMatch
        }
    }

    @SuppressLint("MissingPermission")
    private fun resolvePhoneNumberForSubscription(
        subscriptionManager: SubscriptionManager,
        telephonyManager: TelephonyManager?,
        subscriptionId: Int,
    ): String? {
        val activeInfo = runCatching { subscriptionManager.getActiveSubscriptionInfo(subscriptionId) }
            .getOrNull()
        val candidates = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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

    @SuppressLint("MissingPermission")
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

    @SuppressLint("MissingPermission")
    private fun activeSubscriptionIds(subscriptionManager: SubscriptionManager): List<Int> {
        return runCatching {
            subscriptionManager.activeSubscriptionInfoList.orEmpty()
        }.getOrDefault(emptyList())
            .mapNotNull { info ->
                info.subscriptionId.takeIf { it != SubscriptionManager.INVALID_SUBSCRIPTION_ID }
            }
            .distinct()
    }

    private fun smsBodyMatchScore(expected: String, actual: String): Int {
        if (expected.isBlank() || actual.isBlank()) return 0
        return when {
            expected == actual -> 3
            actual.contains(expected) || expected.contains(actual) -> 2
            actual.take(12) == expected.take(12) -> 1
            else -> 0
        }
    }

    private fun firstValidInt(cursor: Cursor, vararg columnNames: String): Int? {
        columnNames.forEach { columnName ->
            val index = cursor.getColumnIndex(columnName)
            if (index >= 0 && !cursor.isNull(index)) {
                val value = cursor.getInt(index)
                if (value >= 0) return value
            }
        }
        return null
    }

    private fun Cursor.getStringOrNull(columnName: String): String? {
        val index = getColumnIndex(columnName)
        if (index < 0 || isNull(index)) return null
        return getString(index)
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

    private fun hasSmsReadPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasPhoneNumberReadPermission(context: Context): Boolean {
        val permissions = listOf(
            Manifest.permission.READ_PHONE_NUMBERS,
            Manifest.permission.READ_PHONE_STATE,
        )
        return permissions.any { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun resolveSourceAppName(context: Context): String {
        val packageName = Telephony.Sms.getDefaultSmsPackage(context).orEmpty()
        if (packageName.isBlank()) return "Messages"

        return runCatching {
            val packageManager = context.packageManager
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(applicationInfo).toString()
        }.getOrDefault("Messages")
    }

    private data class SmsDetails(
        val senderAddress: String?,
        val subscriptionId: Int?,
        val slotIndex: Int?,
    )

    companion object {
        private const val TAG = "SmsReceiver"
    }
}
