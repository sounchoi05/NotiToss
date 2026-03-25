package com.ckchoi.notitoss.ui

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ckchoi.notitoss.data.DeliveryActionType
import com.ckchoi.notitoss.data.DeliveryHistoryEntity
import com.ckchoi.notitoss.data.DeliveryStatus
import com.ckchoi.notitoss.data.ForwardingSettings
import com.ckchoi.notitoss.data.ForwardRuleEntity
import com.ckchoi.notitoss.data.InstalledAppInfo
import com.ckchoi.notitoss.data.NotificationGroupSortOption
import com.ckchoi.notitoss.data.NotificationListSettings
import com.ckchoi.notitoss.data.NotificationEventEntity
import com.ckchoi.notitoss.data.NotificationSourceFilterOption
import com.ckchoi.notitoss.data.NotiTossRepository
import com.ckchoi.notitoss.data.RuntimeDiagnostics
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class MainTab(val title: String) {
    Notifications("\uC54C\uB9BC\uB0B4\uC5ED"),
    Rules("\uC804\uB2EC\uADDC\uCE59"),
    Deliveries("\uC804\uB2EC\uB0B4\uC5ED"),
    Settings("\uC571\uC124\uC815"),
}

data class RuleEditorState(
    val id: Long? = null,
    val name: String = "",
    val selectedPackages: Set<String> = emptySet(),
    val excludedPackages: Set<String> = emptySet(),
    val keywordInput: String = "",
    val selectedActions: Set<DeliveryActionType> = emptySet(),
    val phoneInput: String = "",
    val webhookInputs: List<String> = emptyList(),
    val telegramTargets: List<String> = emptyList(),
    val soundUri: String? = null,
    val soundLabel: String = "",
    val cardColorHex: String? = null,
    val enabled: Boolean = true,
)

class MainViewModel(
    private val repository: NotiTossRepository,
) : ViewModel() {
    val notifications: StateFlow<List<NotificationEventEntity>> = repository.observeNotifications()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val rules: StateFlow<List<ForwardRuleEntity>> = repository.observeRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val deliveries: StateFlow<List<DeliveryHistoryEntity>> = repository.observeDeliveries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val forwardingSettings: StateFlow<ForwardingSettings> = repository.observeForwardingSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ForwardingSettings())

    val favoriteNotificationApps: StateFlow<Set<String>> = repository.observeFavoriteNotificationApps()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val excludedNotificationApps: StateFlow<Set<String>> = repository.observeExcludedNotificationApps()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val notificationListSettings: StateFlow<NotificationListSettings> = repository.observeNotificationListSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotificationListSettings())

    val runtimeDiagnostics: StateFlow<RuntimeDiagnostics> = repository.observeRuntimeDiagnostics()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RuntimeDiagnostics())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    var currentTab by mutableStateOf(MainTab.Notifications)
        private set

    var installedApps by mutableStateOf<List<InstalledAppInfo>>(emptyList())
        private set

    var showRuleEditor by mutableStateOf(false)
        private set

    var ruleEditorState by mutableStateOf(RuleEditorState())
        private set

    init {
        refreshInstalledApps()
    }

    fun selectTab(tab: MainTab) {
        currentTab = tab
    }

    fun refreshInstalledApps() {
        installedApps = repository.getInstalledApps()
    }

    fun startNewRule() {
        refreshInstalledApps()
        showRuleEditor = true
        ruleEditorState = RuleEditorState()
        currentTab = MainTab.Rules
    }

    fun editRule(rule: ForwardRuleEntity) {
        refreshInstalledApps()
        showRuleEditor = true
        ruleEditorState = RuleEditorState(
            id = rule.id,
            name = rule.name,
            selectedPackages = rule.appPackages.toSet(),
            excludedPackages = rule.excludedAppPackages.toSet(),
            keywordInput = rule.keywords.joinToString(", "),
            selectedActions = rule.actions.toSet(),
            phoneInput = rule.phoneNumbers.joinToString(", "),
            webhookInputs = rule.webhookUrls,
            telegramTargets = rule.telegramTargets,
            soundUri = rule.soundUri,
            soundLabel = rule.soundUri.orEmpty(),
            cardColorHex = rule.cardColorHex,
            enabled = rule.enabled,
        )
        currentTab = MainTab.Rules
    }

    fun prefillRuleFromNotification(item: NotificationEventEntity) {
        refreshInstalledApps()
        val keywords = (item.title + " " + item.body)
            .split(Regex("[\\s,./:_-]+"))
            .map { it.trim() }
            .filter { it.length >= 2 }
            .distinct()
            .take(3)

        showRuleEditor = true
        ruleEditorState = RuleEditorState(
            name = "${item.sourceAppName} \uC804\uB2EC",
            selectedPackages = setOf(item.sourcePackageName),
            keywordInput = keywords.joinToString(", "),
        )
        currentTab = MainTab.Rules
    }

    fun closeRuleEditor() {
        showRuleEditor = false
    }

    fun updateRuleName(value: String) {
        ruleEditorState = ruleEditorState.copy(name = value)
    }

    fun togglePackage(packageName: String) {
        val next = ruleEditorState.selectedPackages.toMutableSet()
        if (!next.add(packageName)) {
            next.remove(packageName)
        }
        ruleEditorState = ruleEditorState.copy(selectedPackages = next)
    }

    fun toggleExcludedPackage(packageName: String) {
        val next = ruleEditorState.excludedPackages.toMutableSet()
        if (!next.add(packageName)) {
            next.remove(packageName)
        }
        ruleEditorState = ruleEditorState.copy(excludedPackages = next)
    }

    fun updateKeywordInput(value: String) {
        ruleEditorState = ruleEditorState.copy(keywordInput = value)
    }

    fun toggleAction(action: DeliveryActionType) {
        val next = ruleEditorState.selectedActions.toMutableSet()
        if (!next.add(action)) {
            next.remove(action)
        }
        ruleEditorState = ruleEditorState.copy(
            selectedActions = next,
            webhookInputs = if (
                action == DeliveryActionType.WEBHOOK &&
                next.contains(DeliveryActionType.WEBHOOK) &&
                ruleEditorState.webhookInputs.isEmpty()
            ) {
                listOf("")
            } else {
                ruleEditorState.webhookInputs
            },
        )
    }

    fun updatePhoneInput(value: String) {
        ruleEditorState = ruleEditorState.copy(phoneInput = value)
    }

    fun addWebhookInput() {
        ruleEditorState = ruleEditorState.copy(
            webhookInputs = ruleEditorState.webhookInputs + "",
        )
    }

    fun updateWebhookInput(index: Int, value: String) {
        val items = ruleEditorState.webhookInputs.toMutableList()
        if (index in items.indices) {
            items[index] = value
            ruleEditorState = ruleEditorState.copy(webhookInputs = items)
        }
    }

    fun removeWebhookInput(index: Int) {
        val items = ruleEditorState.webhookInputs.toMutableList()
        if (index in items.indices) {
            items.removeAt(index)
            ruleEditorState = ruleEditorState.copy(webhookInputs = items)
        }
    }

    fun addTelegramTarget(token: String, chatId: String) {
        val normalizedToken = token.trim()
        val normalizedChatId = chatId.trim()
        if (normalizedToken.isBlank() || normalizedChatId.isBlank()) return
        val value = "$normalizedToken|$normalizedChatId"
        ruleEditorState = ruleEditorState.copy(
            telegramTargets = ruleEditorState.telegramTargets + value,
        )
    }

    fun removeTelegramTarget(index: Int) {
        val items = ruleEditorState.telegramTargets.toMutableList()
        if (index in items.indices) {
            items.removeAt(index)
            ruleEditorState = ruleEditorState.copy(telegramTargets = items)
        }
    }

    fun updateSound(uri: String?, label: String) {
        ruleEditorState = ruleEditorState.copy(soundUri = uri, soundLabel = label)
    }

    fun updateRuleColor(cardColorHex: String?) {
        ruleEditorState = ruleEditorState.copy(cardColorHex = cardColorHex)
    }

    fun updateRuleEnabled(enabled: Boolean) {
        ruleEditorState = ruleEditorState.copy(enabled = enabled)
    }

    fun saveRule() {
        val state = ruleEditorState
        val phoneNumbers = parseCsv(state.phoneInput)
        val webhookUrls = state.webhookInputs.map { it.trim() }.filter { it.isNotEmpty() }
        val telegramTargets = state.telegramTargets.map { it.trim() }.filter { it.isNotEmpty() }

        if (state.selectedActions.isEmpty()) {
            _messages.tryEmit("\uC804\uB2EC \uBC29\uC2DD\uC744 \uD558\uB098 \uC774\uC0C1 \uC120\uD0DD\uD574\uC8FC\uC138\uC694.")
            return
        }
        if (state.selectedActions.contains(DeliveryActionType.SMS) && phoneNumbers.isEmpty()) {
            _messages.tryEmit("SMS \uC804\uB2EC\uC744 \uC120\uD0DD\uD55C \uACBD\uC6B0 \uC804\uD654\uBC88\uD638\uB97C \uC785\uB825\uD574\uC8FC\uC138\uC694.")
            return
        }
        if (phoneNumbers.any(::isInvalidPhoneNumber)) {
            _messages.tryEmit("\uC804\uD654\uBC88\uD638 \uD615\uC2DD\uC744 \uD655\uC778\uD574\uC8FC\uC138\uC694.")
            return
        }
        if (state.selectedActions.contains(DeliveryActionType.WEBHOOK) && webhookUrls.isEmpty()) {
            _messages.tryEmit("\uC6F9\uD6C5 \uC804\uB2EC\uC744 \uC120\uD0DD\uD55C \uACBD\uC6B0 URL\uC744 \uC785\uB825\uD574\uC8FC\uC138\uC694.")
            return
        }
        if (webhookUrls.any(::isInvalidWebhookUrl)) {
            _messages.tryEmit("\uC6F9\uD6C5 URL\uC740 http:// \uB610\uB294 https:// \uD615\uC2DD\uC73C\uB85C \uC785\uB825\uD574\uC8FC\uC138\uC694.")
            return
        }
        if (state.selectedActions.contains(DeliveryActionType.TELEGRAM) && telegramTargets.isEmpty()) {
            _messages.tryEmit("\uD154\uB808\uADF8\uB7A8 \uC804\uB2EC\uC744 \uC120\uD0DD\uD55C \uACBD\uC6B0 \uBD07\uD1A0\uD070|\uCC44\uD305ID \uAC12\uC744 \uC785\uB825\uD574\uC8FC\uC138\uC694.")
            return
        }
        if (telegramTargets.any(::isInvalidTelegramTarget)) {
            _messages.tryEmit("\uD154\uB808\uADF8\uB7A8 \uB300\uC0C1\uC740 \uBD07\uD1A0\uD070|\uCC44\uD305ID \uD615\uC2DD\uC73C\uB85C \uC785\uB825\uD574\uC8FC\uC138\uC694.")
            return
        }
        if (state.selectedActions.contains(DeliveryActionType.SOUND) && state.soundUri.isNullOrBlank()) {
            _messages.tryEmit("\uC18C\uB9AC\uC54C\uB9BC\uC744 \uC120\uD0DD\uD55C \uACBD\uC6B0 \uC54C\uB9BC\uC74C\uC744 \uC9C0\uC815\uD574\uC8FC\uC138\uC694.")
            return
        }

        val rule = ForwardRuleEntity(
            id = state.id ?: 0,
            name = state.name.ifBlank { "\uC0C8 \uC804\uB2EC\uADDC\uCE59" },
            appPackages = state.selectedPackages.toList(),
            excludedAppPackages = state.excludedPackages.toList(),
            keywords = parseCsv(state.keywordInput),
            actions = state.selectedActions.toList(),
            phoneNumbers = phoneNumbers,
            webhookUrls = webhookUrls,
            telegramTargets = telegramTargets,
            soundUri = state.soundUri,
            cardColorHex = state.cardColorHex,
            enabled = state.enabled,
        )

        viewModelScope.launch {
            repository.saveRule(rule)
            showRuleEditor = false
            _messages.tryEmit("\uC804\uB2EC\uADDC\uCE59\uC744 \uC800\uC7A5\uD588\uC2B5\uB2C8\uB2E4.")
        }
    }

    fun deleteRule(ruleId: Long) {
        viewModelScope.launch {
            repository.deleteRule(ruleId)
            _messages.tryEmit("\uC804\uB2EC\uADDC\uCE59\uC744 \uC0AD\uC81C\uD588\uC2B5\uB2C8\uB2E4.")
        }
    }

    fun testRule(
        rule: ForwardRuleEntity,
        title: String,
        body: String,
    ) {
        val normalizedTitle = title.trim().ifBlank { "\uD14C\uC2A4\uD2B8 \uC81C\uBAA9" }
        val normalizedBody = body.trim().ifBlank { "\uC804\uB2EC\uADDC\uCE59 \uD14C\uC2A4\uD2B8 \uBCF8\uBB38\uC785\uB2C8\uB2E4." }
        val sourcePackageName = rule.appPackages.firstOrNull().orEmpty()
        val sourceAppName = installedApps.firstOrNull { it.packageName == sourcePackageName }?.label
            ?: sourcePackageName.ifBlank { "NotifyToss" }

        viewModelScope.launch {
            val results = repository.testRule(
                rule = rule,
                title = normalizedTitle,
                body = normalizedBody,
                sourceAppName = sourceAppName,
                sourcePackageName = sourcePackageName.ifBlank { "com.ckchoi.notitoss.test" },
            )
            val successCount = results.count { it.status == DeliveryStatus.SUCCESS }
            val failedCount = results.count { it.status == DeliveryStatus.FAILED }
            _messages.tryEmit("\uADDC\uCE59 \uD14C\uC2A4\uD2B8 \uC644\uB8CC: \uC131\uACF5 ${successCount}\uAC74, \uC2E4\uD328 ${failedCount}\uAC74")
        }
    }

    fun deleteDelivery(deliveryId: Long) {
        viewModelScope.launch {
            repository.deleteDelivery(deliveryId)
            _messages.tryEmit("\uC804\uB2EC\uB0B4\uC5ED\uC744 \uC0AD\uC81C\uD588\uC2B5\uB2C8\uB2E4.")
        }
    }

    fun retryDelivery(delivery: DeliveryHistoryEntity) {
        viewModelScope.launch {
            repository.retryDelivery(delivery)
            _messages.tryEmit("\uB3D9\uC77C \uB300\uC0C1\uC73C\uB85C \uC7AC\uC804\uC1A1\uD588\uC2B5\uB2C8\uB2E4.")
        }
    }

    fun redeliver(
        delivery: DeliveryHistoryEntity,
        actionType: DeliveryActionType,
        target: String,
        soundUri: String? = null,
    ) {
        val normalizedTarget = target.trim()
        when {
            normalizedTarget.isBlank() -> {
                _messages.tryEmit("\uB2E4\uC2DC \uC804\uB2EC\uD560 \uB300\uC0C1\uC744 \uC785\uB825\uD574\uC8FC\uC138\uC694.")
                return
            }

            actionType == DeliveryActionType.SMS && isInvalidPhoneNumber(normalizedTarget) -> {
                _messages.tryEmit("\uC804\uD654\uBC88\uD638 \uD615\uC2DD\uC744 \uD655\uC778\uD574\uC8FC\uC138\uC694.")
                return
            }

            actionType == DeliveryActionType.WEBHOOK && isInvalidWebhookUrl(normalizedTarget) -> {
                _messages.tryEmit("\uC6F9\uD6C5 URL\uC740 http:// \uB610\uB294 https:// \uD615\uC2DD\uC73C\uB85C \uC785\uB825\uD574\uC8FC\uC138\uC694.")
                return
            }

            actionType == DeliveryActionType.TELEGRAM && isInvalidTelegramTarget(normalizedTarget) -> {
                _messages.tryEmit("\uD154\uB808\uADF8\uB7A8 \uB300\uC0C1\uC740 \uBD07\uD1A0\uD070|\uCC44\uD305ID \uD615\uC2DD\uC73C\uB85C \uC785\uB825\uD574\uC8FC\uC138\uC694.")
                return
            }
        }
        viewModelScope.launch {
            repository.redeliver(delivery, actionType, normalizedTarget, soundUri)
            _messages.tryEmit("\uB2E4\uC2DC \uC804\uB2EC\uC744 \uC644\uB8CC\uD588\uC2B5\uB2C8\uB2E4.")
        }
    }

    fun exportRules(uri: Uri) {
        viewModelScope.launch {
            repository.exportRules(uri)
            _messages.tryEmit("\uADDC\uCE59 \uBC31\uC5C5\uC744 \uC800\uC7A5\uD588\uC2B5\uB2C8\uB2E4.")
        }
    }

    fun importRules(uri: Uri) {
        viewModelScope.launch {
            repository.importRules(uri)
            _messages.tryEmit("\uADDC\uCE59 \uBCF5\uC6D0\uC744 \uC644\uB8CC\uD588\uC2B5\uB2C8\uB2E4.")
        }
    }

    fun exportData(uri: Uri) {
        viewModelScope.launch {
            repository.exportData(uri)
            _messages.tryEmit("\uB370\uC774\uD130 \uBC31\uC5C5\uC744 \uC800\uC7A5\uD588\uC2B5\uB2C8\uB2E4.")
        }
    }

    fun importData(uri: Uri) {
        viewModelScope.launch {
            repository.importData(uri)
            _messages.tryEmit("\uB370\uC774\uD130 \uBCF5\uC6D0\uC744 \uC644\uB8CC\uD588\uC2B5\uB2C8\uB2E4.")
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            repository.clearCache()
            _messages.tryEmit("\uCE90\uC2DC\uB97C \uC0AD\uC81C\uD588\uC2B5\uB2C8\uB2E4.")
        }
    }

    fun clearDataAndCache() {
        viewModelScope.launch {
            repository.clearDataAndCache()
            _messages.tryEmit("\uB370\uC774\uD130\uC640 \uCE90\uC2DC\uB97C \uC0AD\uC81C\uD588\uC2B5\uB2C8\uB2E4.")
        }
    }

    fun updateSmsRetryCount(value: Int) {
        viewModelScope.launch {
            repository.updateSmsRetryCount(value)
        }
    }

    fun updateSmsRetryDelaySeconds(value: Int) {
        viewModelScope.launch {
            repository.updateSmsRetryDelaySeconds(value)
        }
    }

    fun updateWebhookRetryCount(value: Int) {
        viewModelScope.launch {
            repository.updateWebhookRetryCount(value)
        }
    }

    fun updateWebhookRetryDelaySeconds(value: Int) {
        viewModelScope.launch {
            repository.updateWebhookRetryDelaySeconds(value)
        }
    }

    fun toggleFavoriteNotificationApp(appName: String) {
        viewModelScope.launch {
            repository.toggleFavoriteNotificationApp(appName)
        }
    }

    fun toggleExcludedNotificationApp(packageName: String) {
        viewModelScope.launch {
            repository.toggleExcludedNotificationApp(packageName)
        }
    }

    fun updateNotificationSearchQuery(query: String) {
        viewModelScope.launch {
            repository.updateNotificationSearchQuery(query)
        }
    }

    fun updateNotificationSort(sort: NotificationGroupSortOption) {
        viewModelScope.launch {
            repository.updateNotificationSort(sort)
        }
    }

    fun updateNotificationSourceFilter(filter: NotificationSourceFilterOption) {
        viewModelScope.launch {
            repository.updateNotificationSourceFilter(filter)
        }
    }

    fun toggleExpandedNotificationApp(appName: String) {
        viewModelScope.launch {
            repository.toggleExpandedNotificationApp(appName)
        }
    }

    fun expandAllNotificationApps(appNames: Set<String>) {
        viewModelScope.launch {
            repository.setExpandedNotificationApps(appNames)
        }
    }

    fun collapseAllNotificationApps() {
        viewModelScope.launch {
            repository.setExpandedNotificationApps(emptySet())
        }
    }

    fun resetNotificationFilters() {
        viewModelScope.launch {
            repository.resetNotificationFilters()
        }
    }

    fun runWebhookTest(url: String) {
        val normalizedUrl = url.trim()
        if (normalizedUrl.isBlank()) {
            _messages.tryEmit("\uD14C\uC2A4\uD2B8\uD560 \uC6F9\uD6C5 URL\uC744 \uC785\uB825\uD574\uC8FC\uC138\uC694.")
            return
        }
        if (isInvalidWebhookUrl(normalizedUrl)) {
            _messages.tryEmit("\uC6F9\uD6C5 URL\uC740 http:// \uB610\uB294 https:// \uD615\uC2DD\uC73C\uB85C \uC785\uB825\uD574\uC8FC\uC138\uC694.")
            return
        }
        viewModelScope.launch {
            val result = repository.runWebhookTest(normalizedUrl)
            if (result.status == DeliveryStatus.SUCCESS) {
                _messages.tryEmit("\uC6F9\uD6C5 \uD14C\uC2A4\uD2B8 \uC804\uC1A1\uC744 \uC644\uB8CC\uD588\uC2B5\uB2C8\uB2E4.")
            } else {
                _messages.tryEmit(result.errorMessage ?: "\uC6F9\uD6C5 \uD14C\uC2A4\uD2B8 \uC804\uC1A1\uC5D0 \uC2E4\uD328\uD588\uC2B5\uB2C8\uB2E4.")
            }
        }
    }

    fun appVersion(): String = repository.getAppVersion()

    fun suggestedBackupName(prefix: String): String = repository.getBackupSuggestionFileName(prefix)

    private fun parseCsv(value: String): List<String> {
        return value.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun isInvalidPhoneNumber(value: String): Boolean {
        val digitsOnly = value.filter { it.isDigit() }
        return digitsOnly.length !in 8..15 || value.any { !it.isDigit() && it !in "+- ()" }
    }

    private fun isInvalidWebhookUrl(value: String): Boolean {
        val uri = Uri.parse(value)
        val scheme = uri.scheme?.lowercase()
        return uri.host.isNullOrBlank() || (scheme != "http" && scheme != "https")
    }

    private fun isInvalidTelegramTarget(value: String): Boolean {
        val parts = value.split("|", limit = 2).map { it.trim() }
        if (parts.size != 2) return true
        val token = parts[0]
        val chatId = parts[1]
        return token.isBlank() ||
            chatId.isBlank() ||
            !token.contains(":") ||
            chatId.any { it.isWhitespace() }
    }

    companion object {
        fun factory(repository: NotiTossRepository): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MainViewModel(repository) as T
                }
            }
        }
    }
}
