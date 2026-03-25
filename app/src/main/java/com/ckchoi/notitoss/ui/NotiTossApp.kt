package com.ckchoi.notitoss.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RuleFolder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import com.ckchoi.notitoss.ui.components.ScreenBackground

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NotiTossApp(
    viewModel: MainViewModel,
    hasNotificationAccess: Boolean,
    hasSmsPermissions: Boolean,
    hasPhoneNumberPermission: Boolean,
    isIgnoringBatteryOptimizations: Boolean,
    onRequestEssentialPermissions: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onRequestSmsPermissions: () -> Unit,
    onRequestBatteryOptimizationExemption: () -> Unit,
    onOpenBatteryOptimizationSettings: () -> Unit,
    onRestartBackgroundMonitoring: () -> Unit,
    onPickRingtone: () -> Unit,
) {
    val notifications by viewModel.notifications.collectAsState()
    val rules by viewModel.rules.collectAsState()
    val deliveries by viewModel.deliveries.collectAsState()
    val forwardingSettings by viewModel.forwardingSettings.collectAsState()
    val favoriteNotificationApps by viewModel.favoriteNotificationApps.collectAsState()
    val excludedNotificationApps by viewModel.excludedNotificationApps.collectAsState()
    val notificationListSettings by viewModel.notificationListSettings.collectAsState()
    val runtimeDiagnostics by viewModel.runtimeDiagnostics.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val requiresPermissionGate = !hasNotificationAccess || !hasSmsPermissions || !hasPhoneNumberPermission || !isIgnoringBatteryOptimizations
    val pagerState = rememberPagerState(
        initialPage = viewModel.currentTab.ordinal,
        pageCount = { MainTab.entries.size },
    )

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(viewModel.currentTab) {
        val page = viewModel.currentTab.ordinal
        if (pagerState.currentPage != page) {
            pagerState.animateScrollToPage(page)
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val tab = MainTab.entries[page]
            if (viewModel.currentTab != tab) {
                viewModel.selectTab(tab)
            }
        }
    }

    val exportRuleLauncher = rememberCreateDocumentLauncher { viewModel.exportRules(it) }
    val importRuleLauncher = rememberOpenDocumentLauncher { viewModel.importRules(it) }
    val exportDataLauncher = rememberCreateDocumentLauncher { viewModel.exportData(it) }
    val importDataLauncher = rememberOpenDocumentLauncher { viewModel.importData(it) }

    if (requiresPermissionGate) {
        PermissionConsentScreen(
            hasNotificationAccess = hasNotificationAccess,
            hasSmsPermissions = hasSmsPermissions,
            hasPhoneNumberPermission = hasPhoneNumberPermission,
            isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
            onAgreeAndStart = onRequestEssentialPermissions,
            onOpenNotificationAccess = onOpenNotificationAccess,
            onRequestSmsPermissions = onRequestSmsPermissions,
            onOpenBatteryOptimizationSettings = onOpenBatteryOptimizationSettings,
            snackbarHostState = snackbarHostState,
        )
    } else {

    Scaffold(
        topBar = {
            TopAppBar(
                title = { androidx.compose.material3.Text(viewModel.currentTab.title) },
                actions = {
                    if (viewModel.currentTab == MainTab.Rules) {
                        IconButton(onClick = viewModel::startNewRule) {
                            Icon(Icons.Default.Add, contentDescription = "\uADDC\uCE59 \uCD94\uAC00")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (viewModel.currentTab == MainTab.Rules) {
                FloatingActionButton(onClick = viewModel::startNewRule) {
                    Icon(Icons.Default.Add, contentDescription = "\uADDC\uCE59 \uCD94\uAC00")
                }
            }
        },
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == viewModel.currentTab,
                        onClick = { viewModel.selectTab(tab) },
                        icon = {
                            Icon(
                                imageVector = when (tab) {
                                    MainTab.Notifications -> Icons.Default.Notifications
                                    MainTab.Rules -> Icons.Default.RuleFolder
                                    MainTab.Deliveries -> Icons.AutoMirrored.Filled.Send
                                    MainTab.Settings -> Icons.Default.Settings
                                },
                                contentDescription = tab.title,
                            )
                        },
                        label = { androidx.compose.material3.Text(tab.title) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        ScreenBackground(modifier = Modifier, padding = padding) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (MainTab.entries[page]) {
                    MainTab.Notifications -> NotificationsScreen(
                        notifications = notifications,
                        notificationListSettings = notificationListSettings,
                        favoriteApps = favoriteNotificationApps,
                        onCreateRule = viewModel::prefillRuleFromNotification,
                        onToggleFavorite = viewModel::toggleFavoriteNotificationApp,
                        onSearchQueryChange = viewModel::updateNotificationSearchQuery,
                        onSortChange = viewModel::updateNotificationSort,
                        onSourceFilterChange = viewModel::updateNotificationSourceFilter,
                        onToggleExpandedApp = viewModel::toggleExpandedNotificationApp,
                        onExpandAllApps = viewModel::expandAllNotificationApps,
                        onCollapseAllApps = viewModel::collapseAllNotificationApps,
                    )

                    MainTab.Rules -> RulesScreen(
                        rules = rules,
                        installedApps = viewModel.installedApps,
                        onEdit = viewModel::editRule,
                        onDelete = viewModel::deleteRule,
                        onTest = viewModel::testRule,
                    )

                    MainTab.Deliveries -> DeliveryHistoryScreen(
                        deliveries = deliveries,
                        onDelete = viewModel::deleteDelivery,
                        onRetry = viewModel::retryDelivery,
                        onRedeliver = viewModel::redeliver,
                    )

                    MainTab.Settings -> SettingsScreen(
                        installedApps = viewModel.installedApps,
                        excludedNotificationApps = excludedNotificationApps,
                        forwardingSettings = forwardingSettings,
                        runtimeDiagnostics = runtimeDiagnostics,
                        hasNotificationAccess = hasNotificationAccess,
                        hasSmsPermissions = hasSmsPermissions,
                        hasPhoneNumberPermission = hasPhoneNumberPermission,
                        isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
                        appVersion = viewModel.appVersion(),
                        onOpenNotificationAccess = onOpenNotificationAccess,
                        onRequestSmsPermissions = onRequestSmsPermissions,
                        onRequestBatteryOptimizationExemption = onRequestBatteryOptimizationExemption,
                        onOpenBatteryOptimizationSettings = onOpenBatteryOptimizationSettings,
                        onRestartBackgroundMonitoring = onRestartBackgroundMonitoring,
                        onSmsRetryCountChange = viewModel::updateSmsRetryCount,
                        onSmsRetryDelayChange = viewModel::updateSmsRetryDelaySeconds,
                        onWebhookRetryCountChange = viewModel::updateWebhookRetryCount,
                        onWebhookRetryDelayChange = viewModel::updateWebhookRetryDelaySeconds,
                        onToggleExcludedNotificationApp = viewModel::toggleExcludedNotificationApp,
                        onExportRules = { exportRuleLauncher.launch(viewModel.suggestedBackupName("rules")) },
                        onImportRules = { importRuleLauncher.launch(arrayOf("application/json")) },
                        onExportData = { exportDataLauncher.launch(viewModel.suggestedBackupName("data")) },
                        onImportData = { importDataLauncher.launch(arrayOf("application/json")) },
                        onClearCache = viewModel::clearCache,
                        onClearDataAndCache = viewModel::clearDataAndCache,
                    )
                }
            }
        }
    }
    }

    if (viewModel.showRuleEditor) {
        RuleEditorDialog(
            state = viewModel.ruleEditorState,
            installedApps = viewModel.installedApps,
            onDismiss = viewModel::closeRuleEditor,
            onRuleNameChange = viewModel::updateRuleName,
            onTogglePackage = viewModel::togglePackage,
            onToggleExcludedPackage = viewModel::toggleExcludedPackage,
            onKeywordChange = viewModel::updateKeywordInput,
            onToggleAction = viewModel::toggleAction,
            onPhoneChange = viewModel::updatePhoneInput,
            onAddWebhook = viewModel::addWebhookInput,
            onWebhookChange = viewModel::updateWebhookInput,
            onRemoveWebhook = viewModel::removeWebhookInput,
            onAddTelegram = viewModel::addTelegramTarget,
            onRemoveTelegram = viewModel::removeTelegramTarget,
            onPickRingtone = onPickRingtone,
            onColorChange = viewModel::updateRuleColor,
            onEnabledChange = viewModel::updateRuleEnabled,
            onSave = viewModel::saveRule,
        )
    }
}

@Composable
private fun rememberCreateDocumentLauncher(onResult: (Uri) -> Unit) =
    rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { if (it != null) onResult(it) },
    )

@Composable
private fun rememberOpenDocumentLauncher(onResult: (Uri) -> Unit) =
    rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { if (it != null) onResult(it) },
    )
