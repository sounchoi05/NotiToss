package com.ckchoi.notitoss.data

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Telephony
import androidx.room.withTransaction
import com.ckchoi.notitoss.service.ForwardDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotiTossRepository(
    private val application: Application,
    private val database: AppDatabase,
    private val dispatcher: ForwardDispatcher,
    private val settingsStore: AppSettingsStore,
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun observeNotifications(): Flow<List<NotificationEventEntity>> = combine(
        database.notificationDao().observeAll(),
        settingsStore.excludedNotificationApps,
    ) { items, excludedPackages ->
        items.filterNot { event -> shouldHideNotificationEvent(event, excludedPackages) }
    }

    fun observeRules(): Flow<List<ForwardRuleEntity>> = database.ruleDao().observeAll()

    fun observeDeliveries(): Flow<List<DeliveryHistoryEntity>> = database.deliveryDao()
        .observeAll()
        .map { items -> items.filterNot(::shouldHideDeliveryHistory) }

    fun observeForwardingSettings(): Flow<ForwardingSettings> = settingsStore.settings

    fun observeFavoriteNotificationApps(): Flow<Set<String>> = settingsStore.favoriteNotificationApps

    fun observeExcludedNotificationApps(): Flow<Set<String>> = settingsStore.excludedNotificationApps

    fun observeNotificationListSettings(): Flow<NotificationListSettings> = settingsStore.notificationListSettings

    fun observeRuntimeDiagnostics(): Flow<RuntimeDiagnostics> = settingsStore.runtimeDiagnostics

    suspend fun processIncomingEvent(event: NotificationEventEntity) {
        if (shouldSkipIncomingEvent(event)) return

        val (savedEvent, insertedNew) = withContext(Dispatchers.IO) {
            val duplicate = if (event.sourceType == SourceType.APP) {
                null
            } else {
                database.notificationDao().findRecentDuplicate(
                    sourceType = event.sourceType,
                    sourcePackageName = event.sourcePackageName,
                    body = event.body,
                    windowStart = event.receivedAt - DUPLICATE_EVENT_WINDOW_MILLIS,
                    windowEnd = event.receivedAt + DUPLICATE_EVENT_WINDOW_MILLIS,
                )
            }

            if (duplicate != null) {
                val merged = duplicate.mergeWith(event)
                if (merged != duplicate) {
                    database.notificationDao().update(merged)
                }
                merged to false
            } else {
                val insertedId = database.notificationDao().insert(event)
                pruneNotificationHistory(event.sourceType)
                event.copy(id = insertedId) to true
            }
        }
        if (insertedNew) {
            settingsStore.recordStoredEvent(savedEvent.sourceType, savedEvent.receivedAt)
            dispatchMatchingRules(savedEvent)
        }
    }

    suspend fun recordIncomingSignal(sourceType: SourceType, at: Long = System.currentTimeMillis()) {
        settingsStore.recordIncomingSignal(sourceType, at)
    }

    suspend fun recordProcessingError(message: String, at: Long = System.currentTimeMillis()) {
        settingsStore.recordProcessingError(message, at)
    }

    suspend fun saveRule(rule: ForwardRuleEntity) {
        withContext(Dispatchers.IO) {
            database.ruleDao().upsert(rule)
        }
    }

    suspend fun deleteRule(ruleId: Long) {
        withContext(Dispatchers.IO) {
            database.ruleDao().deleteById(ruleId)
        }
    }

    suspend fun deleteDelivery(deliveryId: Long) {
        withContext(Dispatchers.IO) {
            database.deliveryDao().deleteById(deliveryId)
        }
    }

    suspend fun retryDelivery(delivery: DeliveryHistoryEntity) {
        val content = ForwardContent(
            title = delivery.title,
            body = delivery.body,
            sourceAppName = delivery.sourceAppName,
            sourcePackageName = delivery.sourcePackageName,
            recipientAddress = delivery.recipientAddress,
            senderAddress = delivery.senderAddress,
            attachmentUris = delivery.attachmentUris,
            receivedAt = delivery.receivedAt,
            notificationId = delivery.notificationId,
        )
        val replay = dispatcher.dispatchSingle(
            actionType = delivery.actionType,
            target = delivery.target,
            soundUri = delivery.retrySoundUri(),
            cardColorHex = delivery.cardColorHex,
            appliedRuleId = delivery.appliedRuleId,
            appliedRuleName = delivery.appliedRuleName,
            content = content,
        )
        withContext(Dispatchers.IO) {
            database.deliveryDao().insert(replay)
            pruneDeliveryHistory()
        }
    }

    suspend fun redeliver(
        delivery: DeliveryHistoryEntity,
        actionType: DeliveryActionType,
        target: String,
        soundUri: String? = null,
    ) {
        val content = ForwardContent(
            title = delivery.title,
            body = delivery.body,
            sourceAppName = delivery.sourceAppName,
            sourcePackageName = delivery.sourcePackageName,
            recipientAddress = delivery.recipientAddress,
            senderAddress = delivery.senderAddress,
            attachmentUris = delivery.attachmentUris,
            receivedAt = delivery.receivedAt,
            notificationId = delivery.notificationId,
        )
        val replay = dispatcher.dispatchSingle(
            actionType = actionType,
            target = target,
            soundUri = soundUri,
            cardColorHex = delivery.cardColorHex,
            appliedRuleId = delivery.appliedRuleId,
            appliedRuleName = delivery.appliedRuleName,
            content = content,
        )
        withContext(Dispatchers.IO) {
            database.deliveryDao().insert(replay)
            pruneDeliveryHistory()
        }
    }

    suspend fun exportRules(uri: Uri) {
        withContext(Dispatchers.IO) {
            val payload = json.encodeToString(
                ListSerializer(ForwardRuleEntity.serializer()),
                database.ruleDao().getAll(),
            )
            application.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                it.write(payload)
            }
        }
    }

    suspend fun importRules(uri: Uri) {
        withContext(Dispatchers.IO) {
            val input = application.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (input.isBlank()) return@withContext
            val rules = json.decodeFromString(ListSerializer(ForwardRuleEntity.serializer()), input)
            database.withTransaction {
                database.ruleDao().clearAll()
                database.ruleDao().insertAll(rules)
            }
        }
    }

    suspend fun exportData(uri: Uri) {
        withContext(Dispatchers.IO) {
            val payload = BackupPayload(
                notifications = database.notificationDao().getAll(),
                rules = database.ruleDao().getAll(),
                deliveries = database.deliveryDao().getAll(),
            )
            application.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                it.write(json.encodeToString(BackupPayload.serializer(), payload))
            }
        }
    }

    suspend fun importData(uri: Uri) {
        withContext(Dispatchers.IO) {
            val input = application.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (input.isBlank()) return@withContext
            val payload = json.decodeFromString(BackupPayload.serializer(), input)
            database.withTransaction {
                database.notificationDao().clearAll()
                database.ruleDao().clearAll()
                database.deliveryDao().clearAll()
                database.notificationDao().insertAll(payload.notifications)
                database.ruleDao().insertAll(payload.rules)
                database.deliveryDao().insertAll(payload.deliveries)
                pruneNotificationHistory(SourceType.APP)
                pruneNotificationHistory(SourceType.SMS)
                pruneNotificationHistory(SourceType.MMS)
                pruneDeliveryHistory()
            }
        }
    }

    suspend fun clearCache() {
        withContext(Dispatchers.IO) {
            application.cacheDir.deleteRecursively()
            application.cacheDir.mkdirs()
        }
    }

    suspend fun clearDataAndCache() {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                database.notificationDao().clearAll()
                database.ruleDao().clearAll()
                database.deliveryDao().clearAll()
            }
            application.cacheDir.deleteRecursively()
            application.cacheDir.mkdirs()
        }
    }

    fun getInstalledApps(): List<InstalledAppInfo> {
        val packageManager = application.packageManager
        return packageManager.getInstalledApplications(PackageManager.MATCH_ALL)
            .asSequence()
            .map { applicationInfo ->
                val label = runCatching {
                    packageManager.getApplicationLabel(applicationInfo).toString()
                }.getOrNull().orEmpty()
                InstalledAppInfo(
                    packageName = applicationInfo.packageName,
                    label = label.ifBlank { applicationInfo.packageName },
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
            .toList()
    }

    fun getAppVersion(): String {
        val packageInfo = application.packageManager.getPackageInfo(application.packageName, 0)
        return packageInfo.versionName ?: "1.0.0"
    }

    suspend fun updateSmsRetryCount(value: Int) {
        settingsStore.updateSmsRetryCount(value)
    }

    suspend fun updateSmsRetryDelaySeconds(value: Int) {
        settingsStore.updateSmsRetryDelaySeconds(value)
    }

    suspend fun updateWebhookRetryCount(value: Int) {
        settingsStore.updateWebhookRetryCount(value)
    }

    suspend fun updateWebhookRetryDelaySeconds(value: Int) {
        settingsStore.updateWebhookRetryDelaySeconds(value)
    }

    suspend fun toggleFavoriteNotificationApp(appName: String) {
        settingsStore.toggleFavoriteNotificationApp(appName)
    }

    suspend fun toggleExcludedNotificationApp(packageName: String) {
        settingsStore.toggleExcludedNotificationApp(packageName)
    }

    suspend fun updateNotificationSearchQuery(query: String) {
        settingsStore.updateNotificationSearchQuery(query)
    }

    suspend fun updateNotificationSort(sort: NotificationGroupSortOption) {
        settingsStore.updateNotificationSort(sort)
    }

    suspend fun updateNotificationSourceFilter(filter: NotificationSourceFilterOption) {
        settingsStore.updateNotificationSourceFilter(filter)
    }

    suspend fun setExpandedNotificationApps(appNames: Set<String>) {
        settingsStore.setExpandedNotificationApps(appNames)
    }

    suspend fun toggleExpandedNotificationApp(appName: String) {
        settingsStore.toggleExpandedNotificationApp(appName)
    }

    suspend fun resetNotificationFilters() {
        settingsStore.resetNotificationFilters()
    }

    suspend fun runWebhookTest(url: String): DeliveryHistoryEntity {
        val content = ForwardContent(
            title = "NotifyToss 테스트",
            body = "웹훅 전달 설정이 정상적으로 동작하는지 확인하기 위한 테스트 메시지입니다.",
            sourceAppName = "NotifyToss",
            sourcePackageName = application.packageName,
            recipientAddress = null,
            senderAddress = null,
            attachmentUris = emptyList(),
            receivedAt = System.currentTimeMillis(),
            notificationId = null,
        )
        val record = dispatcher.dispatchSingle(
            actionType = DeliveryActionType.WEBHOOK,
            target = url,
            soundUri = null,
            cardColorHex = null,
            appliedRuleId = null,
            appliedRuleName = null,
            content = content,
        )
        withContext(Dispatchers.IO) {
            database.deliveryDao().insert(record)
            pruneDeliveryHistory()
        }
        return record
    }

    suspend fun testRule(
        rule: ForwardRuleEntity,
        title: String,
        body: String,
        sourceAppName: String,
        sourcePackageName: String,
    ): List<DeliveryHistoryEntity> {
        val content = ForwardContent(
            title = title,
            body = body,
            sourceAppName = sourceAppName,
            sourcePackageName = sourcePackageName,
            recipientAddress = null,
            senderAddress = null,
            attachmentUris = emptyList(),
            receivedAt = System.currentTimeMillis(),
            notificationId = null,
        )
        val records = dispatcher.dispatchRule(rule, content)
        withContext(Dispatchers.IO) {
            records.forEach { database.deliveryDao().insert(it) }
            pruneDeliveryHistory()
        }
        return records
    }

    fun getBackupSuggestionFileName(prefix: String): String {
        val formatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        return "$prefix-${formatter.format(Date())}.json"
    }

    private suspend fun dispatchMatchingRules(event: NotificationEventEntity) {
        val rules = withContext(Dispatchers.IO) { database.ruleDao().getEnabledRules() }
        val selectedAppLabelsByRule = resolveRuleAppLabels(rules)
        val matchedRules = rules.filter { rule ->
            val appMatches = rule.appPackages.isEmpty() ||
                rule.appPackages.contains(event.sourcePackageName) ||
                selectedAppLabelsByRule[rule.id].orEmpty().any { label ->
                    label.equals(event.sourceAppName, ignoreCase = true)
                }
            val excluded = rule.excludedAppPackages.contains(event.sourcePackageName)
            val keywordMatches = rule.keywords.isEmpty() || rule.keywords.any { keyword ->
                val normalized = keyword.trim()
                normalized.isNotEmpty() && (
                    event.title.contains(normalized, ignoreCase = true) ||
                        event.body.contains(normalized, ignoreCase = true)
                    )
            }
            appMatches && !excluded && keywordMatches
        }

        matchedRules.forEach { rule ->
            val records = dispatcher.dispatchRule(rule, event.toForwardContent())
            withContext(Dispatchers.IO) {
                records.forEach { database.deliveryDao().insert(it) }
                pruneDeliveryHistory()
            }
        }
    }

    private fun DeliveryHistoryEntity.retrySoundUri(): String? {
        return if (actionType == DeliveryActionType.SOUND && target != "default") target else null
    }

    private fun resolveRuleAppLabels(rules: List<ForwardRuleEntity>): Map<Long, Set<String>> {
        val packageManager = application.packageManager
        return rules.associate { rule ->
            val labels = rule.appPackages.mapNotNull { packageName ->
                runCatching {
                    val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
                    packageManager.getApplicationLabel(applicationInfo).toString()
                }.getOrNull()?.takeIf { it.isNotBlank() }
            }.toSet()
            rule.id to labels
        }
    }

    private companion object {
        const val DUPLICATE_EVENT_WINDOW_MILLIS = 15_000L
        const val APP_NOTIFICATION_LIMIT = 1_000
        const val MESSAGE_NOTIFICATION_LIMIT = 200
        const val DELIVERY_HISTORY_LIMIT = 1_000
    }

    private suspend fun pruneNotificationHistory(sourceType: SourceType) {
        val limit = when (sourceType) {
            SourceType.APP -> APP_NOTIFICATION_LIMIT
            SourceType.SMS, SourceType.MMS -> MESSAGE_NOTIFICATION_LIMIT
        }
        database.notificationDao().pruneBySourceType(sourceType, limit)
    }

    private suspend fun pruneDeliveryHistory() {
        database.deliveryDao().pruneToRecent(DELIVERY_HISTORY_LIMIT)
    }

    private suspend fun shouldSkipIncomingEvent(event: NotificationEventEntity): Boolean {
        if (event.sourceType != SourceType.APP) {
            return false
        }
        val excludedPackages = settingsStore.currentExcludedNotificationApps()
        return isDefaultMessagingPackage(event.sourcePackageName) ||
            excludedPackages.contains(event.sourcePackageName)
    }

    private fun shouldHideNotificationEvent(
        event: NotificationEventEntity,
        excludedPackages: Set<String>,
    ): Boolean {
        return (
            event.sourceType == SourceType.APP &&
                (isDefaultMessagingPackage(event.sourcePackageName) || excludedPackages.contains(event.sourcePackageName))
            ) ||
            (
                event.sourceType != SourceType.APP &&
                    event.senderAddress.isNullOrBlank() &&
                    event.recipientAddress.isNullOrBlank()
                )
    }

    private fun shouldHideDeliveryHistory(item: DeliveryHistoryEntity): Boolean {
        return isDefaultMessagingPackage(item.sourcePackageName) &&
            item.senderAddress.isNullOrBlank() &&
            item.recipientAddress.isNullOrBlank()
    }

    private fun isDefaultMessagingPackage(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(application).orEmpty()
        return packageName == defaultSmsPackage || packageName == "sms" || packageName == "mms"
    }
}

private fun NotificationEventEntity.mergeWith(newer: NotificationEventEntity): NotificationEventEntity {
    fun chooseString(current: String, incoming: String): String {
        return when {
            current.isBlank() && incoming.isNotBlank() -> incoming
            current.equals("SMS received", ignoreCase = true) && incoming.isNotBlank() -> incoming
            current.equals("MMS received", ignoreCase = true) && incoming.isNotBlank() -> incoming
            else -> current
        }
    }

    fun chooseNullable(current: String?, incoming: String?): String? {
        return current?.takeIf { it.isNotBlank() } ?: incoming?.takeIf { it.isNotBlank() }
    }

    return copy(
        sourceAppName = chooseString(sourceAppName, newer.sourceAppName),
        sourcePackageName = chooseString(sourcePackageName, newer.sourcePackageName),
        title = chooseString(title, newer.title),
        body = chooseString(body, newer.body),
        recipientAddress = chooseNullable(recipientAddress, newer.recipientAddress),
        senderAddress = chooseNullable(senderAddress, newer.senderAddress),
        attachmentUris = if (attachmentUris.isNotEmpty()) attachmentUris else newer.attachmentUris,
    )
}
