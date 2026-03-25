package com.ckchoi.notitoss.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.media.AudioManager
import android.media.RingtoneManager
import android.telephony.SubscriptionManager
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import com.ckchoi.notitoss.MainActivity
import com.ckchoi.notitoss.R
import com.ckchoi.notitoss.data.DeliveryActionType
import com.ckchoi.notitoss.data.DeliveryHistoryEntity
import com.ckchoi.notitoss.data.DeliveryStatus
import com.ckchoi.notitoss.data.ForwardContent
import com.ckchoi.notitoss.data.ForwardRuleEntity
import com.ckchoi.notitoss.data.AppSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit

class ForwardDispatcher(
    private val context: Context,
    private val settingsStore: AppSettingsStore,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun dispatchRule(
        rule: ForwardRuleEntity,
        content: ForwardContent,
    ): List<DeliveryHistoryEntity> {
        val records = mutableListOf<DeliveryHistoryEntity>()

        rule.actions.forEach { action ->
            when (action) {
                DeliveryActionType.SMS -> {
                    rule.phoneNumbers.forEach { phone ->
                        records += dispatchSingle(
                            actionType = action,
                            target = phone,
                            soundUri = null,
                            cardColorHex = rule.cardColorHex,
                            appliedRuleId = rule.id,
                            appliedRuleName = rule.name,
                            content = content,
                        )
                    }
                }

                DeliveryActionType.WEBHOOK -> {
                    rule.webhookUrls.forEach { url ->
                        records += dispatchSingle(
                            actionType = action,
                            target = url,
                            soundUri = null,
                            cardColorHex = rule.cardColorHex,
                            appliedRuleId = rule.id,
                            appliedRuleName = rule.name,
                            content = content,
                        )
                    }
                }

                DeliveryActionType.TELEGRAM -> {
                    rule.telegramTargets.forEach { target ->
                        records += dispatchSingle(
                            actionType = action,
                            target = target,
                            soundUri = null,
                            cardColorHex = rule.cardColorHex,
                            appliedRuleId = rule.id,
                            appliedRuleName = rule.name,
                            content = content,
                        )
                    }
                }

                DeliveryActionType.SOUND -> {
                    records += dispatchSingle(
                        actionType = action,
                        target = rule.soundUri ?: "default",
                        soundUri = rule.soundUri,
                        cardColorHex = rule.cardColorHex,
                        appliedRuleId = rule.id,
                        appliedRuleName = rule.name,
                        content = content,
                    )
                }
            }
        }

        return records
    }

    suspend fun dispatchSingle(
        actionType: DeliveryActionType,
        target: String,
        soundUri: String?,
        cardColorHex: String?,
        appliedRuleId: Long?,
        appliedRuleName: String?,
        content: ForwardContent,
    ): DeliveryHistoryEntity {
        return when (actionType) {
            DeliveryActionType.SMS -> sendSms(target, cardColorHex, appliedRuleId, appliedRuleName, content)
            DeliveryActionType.WEBHOOK -> sendWebhook(target, cardColorHex, appliedRuleId, appliedRuleName, content)
            DeliveryActionType.TELEGRAM -> sendTelegram(target, cardColorHex, appliedRuleId, appliedRuleName, content)
            DeliveryActionType.SOUND -> playAlert(soundUri, cardColorHex, appliedRuleId, appliedRuleName, content)
        }
    }

    private suspend fun sendSms(
        phoneNumber: String,
        cardColorHex: String?,
        appliedRuleId: Long?,
        appliedRuleName: String?,
        content: ForwardContent,
    ): DeliveryHistoryEntity = withContext(Dispatchers.IO) {
        val message = buildForwardMessage(content)
        val settings = settingsStore.currentSettings()
        try {
            retryDelivery(
                maxAttempts = settings.smsRetryCount,
                retryDelayMillis = settings.smsRetryDelaySeconds * 1_000L,
                shouldRetry = { error -> error !is SecurityException && error !is IllegalArgumentException },
            ) {
                val smsManager = smsManager()
                val parts = smsManager.divideMessage(message)
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            }
            buildHistoryRecord(
                content = content,
                actionType = DeliveryActionType.SMS,
                target = phoneNumber,
                cardColorHex = cardColorHex,
                appliedRuleId = appliedRuleId,
                appliedRuleName = appliedRuleName,
                status = DeliveryStatus.SUCCESS,
            )
        } catch (error: Throwable) {
            error.rethrowIfCancellation()
            buildHistoryRecord(
                content = content,
                actionType = DeliveryActionType.SMS,
                target = phoneNumber,
                cardColorHex = cardColorHex,
                appliedRuleId = appliedRuleId,
                appliedRuleName = appliedRuleName,
                status = DeliveryStatus.FAILED,
                errorMessage = formatErrorMessage(error),
            )
        }
    }

    private suspend fun sendWebhook(
        url: String,
        cardColorHex: String?,
        appliedRuleId: Long?,
        appliedRuleName: String?,
        content: ForwardContent,
    ): DeliveryHistoryEntity = withContext(Dispatchers.IO) {
        val settings = settingsStore.currentSettings()
        val payload = if (isDiscordWebhookUrl(url)) {
            buildDiscordWebhookPayload(content)
        } else {
            buildGenericWebhookPayload(content)
        }

        try {
            retryDelivery(
                maxAttempts = settings.webhookRetryCount,
                retryDelayMillis = settings.webhookRetryDelaySeconds * 1_000L,
                shouldRetry = { error -> error is IOException || error is RetryableDeliveryException },
            ) {
                val request = Request.Builder()
                    .url(url)
                    .post(buildWebhookRequestBody(payload, content))
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val message = extractWebhookErrorMessage(response)
                        if (response.code == 408 || response.code == 429 || response.code >= 500) {
                            throw RetryableDeliveryException(message)
                        }
                        throw IllegalStateException(message)
                    }
                }
            }

            buildHistoryRecord(
                content = content,
                actionType = DeliveryActionType.WEBHOOK,
                target = url,
                cardColorHex = cardColorHex,
                appliedRuleId = appliedRuleId,
                appliedRuleName = appliedRuleName,
                status = DeliveryStatus.SUCCESS,
            )
        } catch (error: Throwable) {
            error.rethrowIfCancellation()
            buildHistoryRecord(
                content = content,
                actionType = DeliveryActionType.WEBHOOK,
                target = url,
                cardColorHex = cardColorHex,
                appliedRuleId = appliedRuleId,
                appliedRuleName = appliedRuleName,
                status = DeliveryStatus.FAILED,
                errorMessage = formatErrorMessage(error),
            )
        }
    }

    private suspend fun sendTelegram(
        telegramTarget: String,
        cardColorHex: String?,
        appliedRuleId: Long?,
        appliedRuleName: String?,
        content: ForwardContent,
    ): DeliveryHistoryEntity = withContext(Dispatchers.IO) {
        val settings = settingsStore.currentSettings()
        val (botToken, chatId) = parseTelegramTarget(telegramTarget)
        val message = buildForwardMessage(content)

        try {
            retryDelivery(
                maxAttempts = settings.webhookRetryCount,
                retryDelayMillis = settings.webhookRetryDelaySeconds * 1_000L,
                shouldRetry = { error -> error is IOException || error is RetryableDeliveryException },
            ) {
                if (content.attachmentUris.isNotEmpty()) {
                    sendTelegramPhoto(botToken, chatId, message, content)
                } else {
                    sendTelegramMessage(botToken, chatId, message)
                }
            }

            buildHistoryRecord(
                content = content,
                actionType = DeliveryActionType.TELEGRAM,
                target = telegramTarget,
                cardColorHex = cardColorHex,
                appliedRuleId = appliedRuleId,
                appliedRuleName = appliedRuleName,
                status = DeliveryStatus.SUCCESS,
            )
        } catch (error: Throwable) {
            error.rethrowIfCancellation()
            buildHistoryRecord(
                content = content,
                actionType = DeliveryActionType.TELEGRAM,
                target = telegramTarget,
                cardColorHex = cardColorHex,
                appliedRuleId = appliedRuleId,
                appliedRuleName = appliedRuleName,
                status = DeliveryStatus.FAILED,
                errorMessage = formatErrorMessage(error),
            )
        }
    }

    private fun buildGenericWebhookPayload(content: ForwardContent): String = JSONObject().apply {
        put("title", content.title)
        put("body", content.body)
        put("sourceApp", content.sourceAppName)
        put("sourcePackage", content.sourcePackageName)
        put("recipientPhoneNumber", content.recipientAddress)
        put("senderPhoneNumber", content.senderAddress)
        put("receivedDate", DateFormats.date(content.receivedAt))
        put("receivedTime", DateFormats.time(content.receivedAt))
        put("imageAttachmentCount", content.attachmentUris.size)
        put(
            "imageAttachments",
            org.json.JSONArray(
                content.attachmentUris.mapIndexed { index, uri ->
                    JSONObject().apply {
                        put("uri", uri)
                        put("mimeType", attachmentMimeType(uri))
                        put("fileName", attachmentFileName(uri, index))
                    }
                },
            ),
        )
    }.toString()

    private fun buildDiscordWebhookPayload(content: ForwardContent): String {
        val embedFields = listOf(
            "수신앱" to content.sourceAppName,
            "수신전화번호" to (content.recipientAddress ?: "-"),
            "발신전화번호" to (content.senderAddress ?: "-"),
            "수신날짜" to DateFormats.date(content.receivedAt),
            "수신시간" to DateFormats.time(content.receivedAt),
            "첨부이미지" to "${content.attachmentUris.size}개",
        )

        val embed = JSONObject().apply {
            put("title", content.title.ifBlank { "제목 없음" })
            put("description", content.body.ifBlank { "-" })
            put("color", 0x1F8B4C)
            put("timestamp", Instant.ofEpochMilli(content.receivedAt).toString())
            content.attachmentUris.firstOrNull()?.let { uri ->
                put(
                    "image",
                    JSONObject().apply {
                        put("url", "attachment://${attachmentFileName(uri, 0)}")
                    },
                )
            }
            put(
                "fields",
                org.json.JSONArray(
                    embedFields.map { (name, value) ->
                        JSONObject().apply {
                            put("name", name)
                            put("value", value.ifBlank { "-" })
                            put("inline", false)
                        }
                    },
                ),
            )
        }

        return JSONObject().apply {
            put("content", "NotifyToss 전달 알림")
            put("embeds", org.json.JSONArray().put(embed))
            put(
                "allowed_mentions",
                JSONObject().apply {
                    put("parse", org.json.JSONArray())
                },
            )
        }.toString()
    }

    private fun sendTelegramMessage(
        botToken: String,
        chatId: String,
        message: String,
    ) {
        val body = JSONObject().apply {
            put("chat_id", chatId)
            put("text", message)
        }.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url("https://api.telegram.org/bot$botToken/sendMessage")
            .post(body)
            .build()

        client.newCall(request).execute().use(::ensureTelegramSuccess)
    }

    private fun sendTelegramPhoto(
        botToken: String,
        chatId: String,
        message: String,
        content: ForwardContent,
    ) {
        val firstUri = content.attachmentUris.firstOrNull()
            ?: return sendTelegramMessage(botToken, chatId, message)
        val bytes = readAttachmentBytes(firstUri)
            ?: return sendTelegramMessage(botToken, chatId, message)

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId)
            .addFormDataPart("caption", message.take(1024))
            .addFormDataPart(
                "photo",
                attachmentFileName(firstUri, 0),
                bytes.toRequestBody(attachmentMimeType(firstUri).toMediaTypeOrFallback()),
            )
            .build()

        val request = Request.Builder()
            .url("https://api.telegram.org/bot$botToken/sendPhoto")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use(::ensureTelegramSuccess)

        if (content.attachmentUris.size > 1) {
            sendTelegramMessage(
                botToken = botToken,
                chatId = chatId,
                message = "추가 첨부이미지 ${content.attachmentUris.size - 1}개가 있습니다.",
            )
        }
    }

    private fun buildWebhookRequestBody(
        payload: String,
        content: ForwardContent,
    ): RequestBody {
        if (content.attachmentUris.isEmpty()) {
            return payload.toRequestBody("application/json; charset=utf-8".toMediaType())
        }

        val multipartBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("payload_json", payload)

        content.attachmentUris.forEachIndexed { index, uri ->
            readAttachmentBytes(uri)?.let { bytes ->
                multipartBuilder.addFormDataPart(
                    "files[$index]",
                    attachmentFileName(uri, index),
                    bytes.toRequestBody(attachmentMimeType(uri).toMediaTypeOrFallback()),
                )
            }
        }

        return multipartBuilder.build()
    }

    private fun readAttachmentBytes(uri: String): ByteArray? {
        return runCatching {
            context.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use { input ->
                input.readBytes()
            }
        }.getOrNull()
    }

    private fun attachmentFileName(
        uri: String,
        index: Int,
    ): String {
        val parsedUri = android.net.Uri.parse(uri)
        val nameFromUri = parsedUri.lastPathSegment?.substringAfterLast('/')
        return nameFromUri?.takeIf { it.isNotBlank() && !it.all(Char::isDigit) }
            ?: "attachment_${index + 1}.${extensionForMimeType(attachmentMimeType(uri))}"
    }

    private fun attachmentMimeType(uri: String): String {
        return context.contentResolver.getType(android.net.Uri.parse(uri)).orEmpty()
            .ifBlank { "application/octet-stream" }
    }

    private fun extensionForMimeType(mimeType: String): String {
        return when (mimeType.lowercase()) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            "image/heic" -> "heic"
            else -> "bin"
        }
    }

    private fun String.toMediaTypeOrFallback() =
        runCatching { toMediaType() }.getOrDefault("application/octet-stream".toMediaType())

    private fun isDiscordWebhookUrl(url: String): Boolean =
        url.contains("discord.com/api/webhooks", ignoreCase = true) ||
            url.contains("discordapp.com/api/webhooks", ignoreCase = true)

    private fun extractWebhookErrorMessage(response: Response): String {
        val responseBody = response.body?.string()?.trim().orEmpty()
        if (responseBody.isEmpty()) {
            return "\uC6F9\uD6C5 \uC751\uB2F5 \uCF54\uB4DC ${response.code}"
        }

        val discordMessage = runCatching { JSONObject(responseBody).optString("message") }
            .getOrNull()
            .orEmpty()
            .trim()
        val suffix = (if (discordMessage.isNotEmpty()) discordMessage else responseBody)
            .replace('\n', ' ')
            .take(180)

        return "\uC6F9\uD6C5 \uC751\uB2F5 \uCF54\uB4DC ${response.code}: $suffix"
    }

    private fun ensureTelegramSuccess(response: Response) {
        if (!response.isSuccessful) {
            throw IllegalStateException(extractTelegramErrorMessage(response))
        }

        val responseBody = response.body?.string()?.trim().orEmpty()
        if (responseBody.isBlank()) return

        val payload = runCatching { JSONObject(responseBody) }.getOrNull()
        if (payload != null && !payload.optBoolean("ok", true)) {
            throw IllegalStateException(
                payload.optString("description").ifBlank { "텔레그램 전송에 실패했습니다." },
            )
        }
    }

    private fun extractTelegramErrorMessage(response: Response): String {
        val responseBody = response.body?.string()?.trim().orEmpty()
        if (responseBody.isBlank()) {
            return "텔레그램 응답 코드 ${response.code}"
        }
        val payload = runCatching { JSONObject(responseBody) }.getOrNull()
        val description = payload?.optString("description").orEmpty().trim()
        val suffix = description.ifBlank { responseBody }.replace('\n', ' ').take(180)
        return "텔레그램 응답 코드 ${response.code}: $suffix"
    }

    private suspend fun playAlert(
        soundUri: String?,
        cardColorHex: String?,
        appliedRuleId: Long?,
        appliedRuleName: String?,
        content: ForwardContent,
    ): DeliveryHistoryEntity = withContext(Dispatchers.Main) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION)

        try {
            audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, maxVolume, 0)
            val ringtone = RingtoneManager.getRingtone(
                context,
                soundUri?.let(android.net.Uri::parse) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
            )
                ?: throw IllegalStateException("\uC120\uD0DD\uD55C \uC54C\uB9BC\uC74C\uC744 \uBD88\uB7EC\uC62C \uC218 \uC5C6\uC2B5\uB2C8\uB2E4.")
            ringtone.play()
            delay(2500)
            ringtone.stop()

            buildHistoryRecord(
                content = content,
                actionType = DeliveryActionType.SOUND,
                target = soundUri ?: "default",
                cardColorHex = cardColorHex,
                appliedRuleId = appliedRuleId,
                appliedRuleName = appliedRuleName,
                status = DeliveryStatus.SUCCESS,
            )
        } catch (error: Throwable) {
            error.rethrowIfCancellation()
            buildHistoryRecord(
                content = content,
                actionType = DeliveryActionType.SOUND,
                target = soundUri ?: "default",
                cardColorHex = cardColorHex,
                appliedRuleId = appliedRuleId,
                appliedRuleName = appliedRuleName,
                status = DeliveryStatus.FAILED,
                errorMessage = formatErrorMessage(error),
            )
        } finally {
            audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, originalVolume, 0)
        }
    }

    fun ensureMmsObserverNotification() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            MMS_OBSERVER_CHANNEL_ID,
            context.getString(R.string.mms_observer_channel_name),
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = context.getString(R.string.mms_observer_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    fun createMmsObserverNotification() = NotificationCompat.Builder(context, MMS_OBSERVER_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_notify_chat)
        .setContentTitle(context.getString(R.string.mms_observer_notification_title))
        .setContentText(context.getString(R.string.mms_observer_notification_body))
        .setContentIntent(
            PendingIntent.getActivity(
                context,
                301,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        .setOngoing(true)
        .build()

    companion object {
        const val MMS_OBSERVER_CHANNEL_ID = "mms-observer"
    }

    private fun parseTelegramTarget(value: String): Pair<String, String> {
        val parts = value.split("|", limit = 2).map { it.trim() }
        require(parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
            "텔레그램 대상은 봇토큰|채팅ID 형식이어야 합니다."
        }
        return parts[0] to parts[1]
    }

    private fun smsManager(): SmsManager {
        check(
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_MESSAGING),
        ) { "이 기기에서는 SMS 전송을 지원하지 않습니다." }

        val defaultManager = context.getSystemService(SmsManager::class.java)
            ?: throw IllegalStateException("SMS 서비스를 불러올 수 없습니다.")
        val subscriptionId = SubscriptionManager.getDefaultSmsSubscriptionId()
        return if (subscriptionId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            defaultManager
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            defaultManager.createForSubscriptionId(subscriptionId)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
        }
    }
}

private class RetryableDeliveryException(
    message: String,
) : IOException(message)

private suspend fun retryDelivery(
    maxAttempts: Int,
    retryDelayMillis: Long,
    shouldRetry: (Throwable) -> Boolean,
    block: suspend () -> Unit,
) {
    var lastError: Throwable? = null

    repeat(maxAttempts) { index ->
        val attempt = index + 1
        try {
            block()
            return
        } catch (error: Throwable) {
            error.rethrowIfCancellation()
            lastError = error
            val hasNextAttempt = attempt < maxAttempts
            if (!hasNextAttempt || !shouldRetry(error)) {
                throw appendAttemptInfo(error, attempt, maxAttempts)
            }
            delay(retryDelayMillis * attempt)
        }
    }

    throw appendAttemptInfo(lastError ?: IllegalStateException("\uC54C \uC218 \uC5C6\uB294 \uC804\uB2EC \uC2E4\uD328"), maxAttempts, maxAttempts)
}

private fun appendAttemptInfo(
    error: Throwable,
    attempt: Int,
    maxAttempts: Int,
): Throwable {
    val baseMessage = error.message?.takeIf { it.isNotBlank() } ?: "\uC804\uB2EC \uC911 \uC624\uB958\uAC00 \uBC1C\uC0DD\uD588\uC2B5\uB2C8\uB2E4."
    val detail = if (attempt >= maxAttempts) {
        "$maxAttempts\uD68C \uC2DC\uB3C4 \uD6C4 \uC2E4\uD328: $baseMessage"
    } else {
        "$attempt/$maxAttempts\uD68C \uC2DC\uB3C4 \uC2E4\uD328: $baseMessage"
    }
    return when (error) {
        is IOException -> IOException(detail, error)
        is IllegalStateException -> IllegalStateException(detail, error)
        else -> RuntimeException(detail, error)
    }
}

private fun formatErrorMessage(error: Throwable): String {
    return error.message?.takeIf { it.isNotBlank() } ?: "\uC804\uB2EC \uC911 \uC624\uB958\uAC00 \uBC1C\uC0DD\uD588\uC2B5\uB2C8\uB2E4."
}

private fun Throwable.rethrowIfCancellation() {
    if (this is CancellationException) throw this
}

private fun buildForwardMessage(content: ForwardContent): String {
    return buildString {
        appendLine("\uC81C\uBAA9: ${content.title}")
        appendLine("\uB0B4\uC6A9: ${content.body}")
        appendLine("\uC218\uC2E0\uC571: ${content.sourceAppName}")
        appendLine("\uC218\uC2E0\uC804\uD654\uBC88\uD638: ${content.recipientAddress ?: "-"}")
        appendLine("\uBC1C\uC2E0\uC804\uD654\uBC88\uD638: ${content.senderAddress ?: "-"}")
        if (content.attachmentUris.isNotEmpty()) {
            appendLine("첨부이미지: ${content.attachmentUris.size}개")
        }
        appendLine("\uC218\uC2E0\uB0A0\uC9DC: ${DateFormats.date(content.receivedAt)}")
        append("\uC218\uC2E0\uC2DC\uAC04: ${DateFormats.time(content.receivedAt)}")
    }
}

private fun buildHistoryRecord(
    content: ForwardContent,
    actionType: DeliveryActionType,
    target: String,
    cardColorHex: String?,
    appliedRuleId: Long?,
    appliedRuleName: String?,
    status: DeliveryStatus,
    errorMessage: String? = null,
): DeliveryHistoryEntity {
    return DeliveryHistoryEntity(
        notificationId = content.notificationId,
        appliedRuleId = appliedRuleId,
        appliedRuleName = appliedRuleName,
        title = content.title,
        body = content.body,
        sourceAppName = content.sourceAppName,
        sourcePackageName = content.sourcePackageName,
        recipientAddress = content.recipientAddress,
        senderAddress = content.senderAddress,
        attachmentUris = content.attachmentUris,
        cardColorHex = cardColorHex,
        receivedAt = content.receivedAt,
        actionType = actionType,
        target = target,
        status = status,
        errorMessage = errorMessage,
    )
}

