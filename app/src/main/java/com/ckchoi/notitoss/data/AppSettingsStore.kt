package com.ckchoi.notitoss.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "app_settings")

data class ForwardingSettings(
    val smsRetryCount: Int = AppSettingsStore.DEFAULT_SMS_RETRY_COUNT,
    val smsRetryDelaySeconds: Int = AppSettingsStore.DEFAULT_SMS_RETRY_DELAY_SECONDS,
    val webhookRetryCount: Int = AppSettingsStore.DEFAULT_WEBHOOK_RETRY_COUNT,
    val webhookRetryDelaySeconds: Int = AppSettingsStore.DEFAULT_WEBHOOK_RETRY_DELAY_SECONDS,
)

enum class NotificationGroupSortOption {
    LATEST,
    NAME,
}

enum class NotificationSourceFilterOption {
    ALL,
    APP,
    SMS,
    MMS,
}

data class NotificationListSettings(
    val searchQuery: String = "",
    val sort: NotificationGroupSortOption = NotificationGroupSortOption.LATEST,
    val sourceFilter: NotificationSourceFilterOption = NotificationSourceFilterOption.ALL,
    val expandedApps: Set<String> = emptySet(),
)

data class RuntimeDiagnostics(
    val lastAppDetectedAt: Long? = null,
    val lastSmsDetectedAt: Long? = null,
    val lastMmsDetectedAt: Long? = null,
    val lastStoredAt: Long? = null,
    val lastStoredSource: String = "",
    val lastErrorAt: Long? = null,
    val lastErrorMessage: String = "",
)

class AppSettingsStore(
    private val context: Context,
) {
    val settings: Flow<ForwardingSettings> = context.dataStore.data
        .map(::toForwardingSettings)

    val favoriteNotificationApps: Flow<Set<String>> = context.dataStore.data
        .map { preferences -> preferences[Keys.FAVORITE_NOTIFICATION_APPS] ?: emptySet() }

    val excludedNotificationApps: Flow<Set<String>> = context.dataStore.data
        .map { preferences -> preferences[Keys.EXCLUDED_NOTIFICATION_APPS] ?: emptySet() }

    val notificationListSettings: Flow<NotificationListSettings> = context.dataStore.data
        .map(::toNotificationListSettings)

    val runtimeDiagnostics: Flow<RuntimeDiagnostics> = context.dataStore.data
        .map(::toRuntimeDiagnostics)

    suspend fun currentSettings(): ForwardingSettings = settings.first()

    suspend fun currentExcludedNotificationApps(): Set<String> = excludedNotificationApps.first()

    suspend fun updateSmsRetryCount(value: Int) {
        updateIntPreference(Keys.SMS_RETRY_COUNT, value.coerceIn(RETRY_COUNT_RANGE))
    }

    suspend fun updateSmsRetryDelaySeconds(value: Int) {
        updateIntPreference(Keys.SMS_RETRY_DELAY_SECONDS, value.coerceIn(RETRY_DELAY_RANGE_SECONDS))
    }

    suspend fun updateWebhookRetryCount(value: Int) {
        updateIntPreference(Keys.WEBHOOK_RETRY_COUNT, value.coerceIn(RETRY_COUNT_RANGE))
    }

    suspend fun updateWebhookRetryDelaySeconds(value: Int) {
        updateIntPreference(Keys.WEBHOOK_RETRY_DELAY_SECONDS, value.coerceIn(RETRY_DELAY_RANGE_SECONDS))
    }

    suspend fun toggleFavoriteNotificationApp(appName: String) {
        val normalized = appName.trim()
        if (normalized.isBlank()) return

        context.dataStore.edit { preferences ->
            val current = preferences[Keys.FAVORITE_NOTIFICATION_APPS] ?: emptySet()
            preferences[Keys.FAVORITE_NOTIFICATION_APPS] = if (current.contains(normalized)) {
                current - normalized
            } else {
                current + normalized
            }
        }
    }

    suspend fun toggleExcludedNotificationApp(packageName: String) {
        val normalized = packageName.trim()
        if (normalized.isBlank()) return

        context.dataStore.edit { preferences ->
            val current = preferences[Keys.EXCLUDED_NOTIFICATION_APPS] ?: emptySet()
            preferences[Keys.EXCLUDED_NOTIFICATION_APPS] = if (current.contains(normalized)) {
                current - normalized
            } else {
                current + normalized
            }
        }
    }

    suspend fun updateNotificationSearchQuery(query: String) {
        context.dataStore.edit { preferences ->
            preferences[Keys.NOTIFICATION_SEARCH_QUERY] = query
        }
    }

    suspend fun updateNotificationSort(sort: NotificationGroupSortOption) {
        context.dataStore.edit { preferences ->
            preferences[Keys.NOTIFICATION_SORT] = sort.name
        }
    }

    suspend fun updateNotificationSourceFilter(filter: NotificationSourceFilterOption) {
        context.dataStore.edit { preferences ->
            preferences[Keys.NOTIFICATION_SOURCE_FILTER] = filter.name
        }
    }

    suspend fun setExpandedNotificationApps(appNames: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[Keys.EXPANDED_NOTIFICATION_APPS] = appNames
        }
    }

    suspend fun toggleExpandedNotificationApp(appName: String) {
        val normalized = appName.trim()
        if (normalized.isBlank()) return

        context.dataStore.edit { preferences ->
            val current = preferences[Keys.EXPANDED_NOTIFICATION_APPS] ?: emptySet()
            preferences[Keys.EXPANDED_NOTIFICATION_APPS] = if (current.contains(normalized)) {
                current - normalized
            } else {
                current + normalized
            }
        }
    }

    suspend fun resetNotificationFilters() {
        context.dataStore.edit { preferences ->
            preferences[Keys.NOTIFICATION_SEARCH_QUERY] = ""
            preferences[Keys.NOTIFICATION_SORT] = NotificationGroupSortOption.LATEST.name
            preferences[Keys.NOTIFICATION_SOURCE_FILTER] = NotificationSourceFilterOption.ALL.name
        }
    }

    suspend fun recordIncomingSignal(sourceType: SourceType, at: Long = System.currentTimeMillis()) {
        context.dataStore.edit { preferences ->
            when (sourceType) {
                SourceType.APP -> preferences[Keys.LAST_APP_DETECTED_AT] = at
                SourceType.SMS -> preferences[Keys.LAST_SMS_DETECTED_AT] = at
                SourceType.MMS -> preferences[Keys.LAST_MMS_DETECTED_AT] = at
            }
        }
    }

    suspend fun recordStoredEvent(sourceType: SourceType, at: Long = System.currentTimeMillis()) {
        context.dataStore.edit { preferences ->
            preferences[Keys.LAST_STORED_AT] = at
            preferences[Keys.LAST_STORED_SOURCE] = sourceType.name
        }
    }

    suspend fun recordProcessingError(message: String, at: Long = System.currentTimeMillis()) {
        context.dataStore.edit { preferences ->
            preferences[Keys.LAST_ERROR_AT] = at
            preferences[Keys.LAST_ERROR_MESSAGE] = message.take(300)
        }
    }

    private suspend fun updateIntPreference(
        key: Preferences.Key<Int>,
        value: Int,
    ) {
        context.dataStore.edit { preferences ->
            preferences[key] = value
        }
    }

    private fun toForwardingSettings(preferences: Preferences): ForwardingSettings {
        return ForwardingSettings(
            smsRetryCount = (preferences[Keys.SMS_RETRY_COUNT] ?: DEFAULT_SMS_RETRY_COUNT).coerceIn(RETRY_COUNT_RANGE),
            smsRetryDelaySeconds = (preferences[Keys.SMS_RETRY_DELAY_SECONDS] ?: DEFAULT_SMS_RETRY_DELAY_SECONDS)
                .coerceIn(RETRY_DELAY_RANGE_SECONDS),
            webhookRetryCount = (preferences[Keys.WEBHOOK_RETRY_COUNT] ?: DEFAULT_WEBHOOK_RETRY_COUNT)
                .coerceIn(RETRY_COUNT_RANGE),
            webhookRetryDelaySeconds = (preferences[Keys.WEBHOOK_RETRY_DELAY_SECONDS] ?: DEFAULT_WEBHOOK_RETRY_DELAY_SECONDS)
                .coerceIn(RETRY_DELAY_RANGE_SECONDS),
        )
    }

    private fun toNotificationListSettings(preferences: Preferences): NotificationListSettings {
        return NotificationListSettings(
            searchQuery = preferences[Keys.NOTIFICATION_SEARCH_QUERY].orEmpty(),
            sort = preferences[Keys.NOTIFICATION_SORT]
                ?.let { value -> NotificationGroupSortOption.entries.firstOrNull { it.name == value } }
                ?: NotificationGroupSortOption.LATEST,
            sourceFilter = preferences[Keys.NOTIFICATION_SOURCE_FILTER]
                ?.let { value -> NotificationSourceFilterOption.entries.firstOrNull { it.name == value } }
                ?: NotificationSourceFilterOption.ALL,
            expandedApps = preferences[Keys.EXPANDED_NOTIFICATION_APPS] ?: emptySet(),
        )
    }

    private fun toRuntimeDiagnostics(preferences: Preferences): RuntimeDiagnostics {
        return RuntimeDiagnostics(
            lastAppDetectedAt = preferences[Keys.LAST_APP_DETECTED_AT],
            lastSmsDetectedAt = preferences[Keys.LAST_SMS_DETECTED_AT],
            lastMmsDetectedAt = preferences[Keys.LAST_MMS_DETECTED_AT],
            lastStoredAt = preferences[Keys.LAST_STORED_AT],
            lastStoredSource = preferences[Keys.LAST_STORED_SOURCE].orEmpty(),
            lastErrorAt = preferences[Keys.LAST_ERROR_AT],
            lastErrorMessage = preferences[Keys.LAST_ERROR_MESSAGE].orEmpty(),
        )
    }

    private object Keys {
        val SMS_RETRY_COUNT = intPreferencesKey("sms_retry_count")
        val SMS_RETRY_DELAY_SECONDS = intPreferencesKey("sms_retry_delay_seconds")
        val WEBHOOK_RETRY_COUNT = intPreferencesKey("webhook_retry_count")
        val WEBHOOK_RETRY_DELAY_SECONDS = intPreferencesKey("webhook_retry_delay_seconds")
        val FAVORITE_NOTIFICATION_APPS = stringSetPreferencesKey("favorite_notification_apps")
        val EXCLUDED_NOTIFICATION_APPS = stringSetPreferencesKey("excluded_notification_apps")
        val NOTIFICATION_SEARCH_QUERY = stringPreferencesKey("notification_search_query")
        val NOTIFICATION_SORT = stringPreferencesKey("notification_sort")
        val NOTIFICATION_SOURCE_FILTER = stringPreferencesKey("notification_source_filter")
        val EXPANDED_NOTIFICATION_APPS = stringSetPreferencesKey("expanded_notification_apps")
        val LAST_APP_DETECTED_AT = longPreferencesKey("last_app_detected_at")
        val LAST_SMS_DETECTED_AT = longPreferencesKey("last_sms_detected_at")
        val LAST_MMS_DETECTED_AT = longPreferencesKey("last_mms_detected_at")
        val LAST_STORED_AT = longPreferencesKey("last_stored_at")
        val LAST_STORED_SOURCE = stringPreferencesKey("last_stored_source")
        val LAST_ERROR_AT = longPreferencesKey("last_error_at")
        val LAST_ERROR_MESSAGE = stringPreferencesKey("last_error_message")
    }

    companion object {
        val RETRY_COUNT_RANGE = 1..5
        val RETRY_DELAY_RANGE_SECONDS = 1..10

        const val DEFAULT_SMS_RETRY_COUNT = 3
        const val DEFAULT_SMS_RETRY_DELAY_SECONDS = 1
        const val DEFAULT_WEBHOOK_RETRY_COUNT = 3
        const val DEFAULT_WEBHOOK_RETRY_DELAY_SECONDS = 2
    }
}
