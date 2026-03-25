@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.ckchoi.notitoss.ui

import android.graphics.Color as AndroidColor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.ckchoi.notitoss.data.RuntimeDiagnostics
import com.ckchoi.notitoss.data.SourceType
import com.ckchoi.notitoss.service.DateFormats

private enum class DeliveryFilter(val label: String) {
    ALL("\uC804\uCCB4"),
    FAILED("\uC2E4\uD328"),
    SUCCESS("\uC131\uACF5"),
}

@Composable
fun NotificationsScreen(
    notifications: List<NotificationEventEntity>,
    notificationListSettings: NotificationListSettings,
    favoriteApps: Set<String>,
    onCreateRule: (NotificationEventEntity) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSortChange: (NotificationGroupSortOption) -> Unit,
    onSourceFilterChange: (NotificationSourceFilterOption) -> Unit,
    onToggleExpandedApp: (String) -> Unit,
    onExpandAllApps: (Set<String>) -> Unit,
    onCollapseAllApps: () -> Unit,
) {
    var searchFieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(notificationListSettings.searchQuery))
    }
    var searchControlsExpanded by rememberSaveable {
        mutableStateOf(notificationListSettings.searchQuery.isNotBlank())
    }
    LaunchedEffect(notificationListSettings.searchQuery) {
        if (searchFieldValue.text != notificationListSettings.searchQuery) {
            searchFieldValue = TextFieldValue(
                text = notificationListSettings.searchQuery,
                selection = TextRange(notificationListSettings.searchQuery.length),
            )
        }
    }
    val groupedNotifications = remember(notifications) {
        notifications.groupBy { it.sourceAppName }
    }
    val filteredGroupedNotifications = remember(groupedNotifications, notificationListSettings) {
        val normalizedQuery = notificationListSettings.searchQuery.trim()
        groupedNotifications.mapValues { (_, appNotifications) ->
            appNotifications.filter { it.matchesSourceFilter(notificationListSettings.sourceFilter) }
        }.filter { (appName, appNotifications) ->
            appNotifications.isNotEmpty() && (
                normalizedQuery.isBlank() ||
                    appName.contains(normalizedQuery, ignoreCase = true) ||
                    appNotifications.any { event ->
                        event.title.contains(normalizedQuery, ignoreCase = true) ||
                            event.body.contains(normalizedQuery, ignoreCase = true)
                    }
                )
        }
    }
    val sortedNotificationGroups = remember(filteredGroupedNotifications, notificationListSettings.sort, favoriteApps) {
        val entries = filteredGroupedNotifications.entries.toList()
        val comparator = when (notificationListSettings.sort) {
            NotificationGroupSortOption.LATEST -> compareByDescending<Map.Entry<String, List<NotificationEventEntity>>> { entry ->
                favoriteApps.contains(entry.key)
            }.thenByDescending { entry ->
                entry.value.maxOfOrNull { it.receivedAt } ?: 0L
            }

            NotificationGroupSortOption.NAME -> compareByDescending<Map.Entry<String, List<NotificationEventEntity>>> { entry ->
                favoriteApps.contains(entry.key)
            }.thenBy { entry ->
                entry.key.lowercase()
            }
        }
        entries.sortedWith(comparator)
    }
    val appNames = remember(sortedNotificationGroups) { sortedNotificationGroups.map { it.key } }
    val filteredNotificationCount = remember(sortedNotificationGroups) {
        sortedNotificationGroups.sumOf { it.value.size }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (notifications.isEmpty()) {
            item {
                GradientCard {
                    Text("\uC544\uC9C1 \uC218\uC2E0\uB41C \uC54C\uB9BC\uC774 \uC5C6\uC2B5\uB2C8\uB2E4.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("\uC571 \uC54C\uB9BC \uAD8C\uD55C\uACFC SMS/MMS \uAD8C\uD55C\uC744 \uD5C8\uC6A9\uD55C \uB4A4 \uC218\uC2E0 \uB0B4\uC5ED\uC774 \uC5EC\uAE30\uC5D0 \uC313\uC785\uB2C8\uB2E4.")
                }
            }
        }
        if (notifications.isNotEmpty()) {
            item(key = "notification-search-controls") {
                GradientCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { searchControlsExpanded = !searchControlsExpanded },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = if (searchControlsExpanded) "검색 접기" else "검색 열기",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "${sortedNotificationGroups.size}개 앱 / ${filteredNotificationCount}건",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    imageVector = if (searchControlsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (searchControlsExpanded) "검색 접기" else "검색 열기",
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { onExpandAllApps(appNames.toSet()) }) {
                                    Icon(Icons.Default.ExpandMore, contentDescription = "전체 펼치기")
                                }
                                IconButton(onClick = onCollapseAllApps) {
                                    Icon(Icons.Default.ExpandLess, contentDescription = "전체 접기")
                                }
                            }
                        }
                        if (searchControlsExpanded) {
                            OutlinedTextField(
                                value = searchFieldValue,
                                onValueChange = { value ->
                                    searchFieldValue = value
                                    onSearchQueryChange(value.text)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("\uC571 \uC774\uB984 \uB610\uB294 \uC54C\uB9BC \uB0B4\uC6A9 \uAC80\uC0C9") },
                                singleLine = true,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                NotificationGroupSortOption.entries.forEach { item ->
                                    FilterChip(
                                        selected = notificationListSettings.sort == item,
                                        onClick = { onSortChange(item) },
                                        label = { Text(item.label()) },
                                    )
                                }
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                NotificationSourceFilterOption.entries.forEach { item ->
                                    FilterChip(
                                        selected = notificationListSettings.sourceFilter == item,
                                        onClick = { onSourceFilterChange(item) },
                                        label = { Text(item.label()) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (notifications.isNotEmpty() && filteredGroupedNotifications.isEmpty()) {
            item {
                GradientCard {
                    Text("\uAC80\uC0C9 \uACB0\uACFC\uAC00 \uC5C6\uC2B5\uB2C8\uB2E4.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("\uB2E4\uB978 \uC571 \uC774\uB984\uC774\uB098 \uC54C\uB9BC \uBB38\uAD6C\uB85C \uB2E4\uC2DC \uAC80\uC0C9\uD574\uBCF4\uC138\uC694.")
                }
            }
        }
        sortedNotificationGroups.forEach { (appName, appNotifications) ->
            val latestNotification = appNotifications.firstOrNull()
            val preview = latestNotification?.previewText().orEmpty()
            val latestTime = latestNotification?.receivedAt?.let(DateFormats::dateTime).orEmpty()
            item(key = "header-$appName") {
                ExpandableSectionHeader(
                    title = appName,
                    subtitle = "\uC54C\uB9BC ${appNotifications.size}\uAC74",
                    preview = preview,
                    latestTime = latestTime,
                    query = notificationListSettings.searchQuery,
                    favorite = favoriteApps.contains(appName),
                    expanded = notificationListSettings.expandedApps.contains(appName),
                    onToggleFavorite = { onToggleFavorite(appName) },
                    onToggle = { onToggleExpandedApp(appName) },
                )
            }
            if (notificationListSettings.expandedApps.contains(appName)) {
                items(appNotifications, key = { it.id }) { item ->
                    GradientCard {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                HighlightedText(
                                    text = item.title,
                                    query = notificationListSettings.searchQuery,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(DateFormats.dateTime(item.receivedAt), style = MaterialTheme.typography.labelMedium)
                            }
                            HighlightedText(
                                text = item.body,
                                query = notificationListSettings.searchQuery,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            OptionalPhoneNumberText(
                                label = "발신",
                                value = item.senderAddress,
                                style = MaterialTheme.typography.labelMedium,
                            )
                            OptionalPhoneNumberText(
                                label = "수신",
                                value = item.recipientAddress,
                                style = MaterialTheme.typography.labelMedium,
                            )
                            TextButton(onClick = { onCreateRule(item) }) {
                                Text("\uC774 \uC54C\uB9BC\uC73C\uB85C \uADDC\uCE59 \uC0DD\uC131")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RulesScreen(
    rules: List<ForwardRuleEntity>,
    installedApps: List<InstalledAppInfo>,
    onEdit: (ForwardRuleEntity) -> Unit,
    onDelete: (Long) -> Unit,
    onTest: (ForwardRuleEntity, String, String) -> Unit,
) {
    val appLabels = remember(installedApps) { installedApps.associate { it.packageName to it.label } }
    var selectedRule by remember { mutableStateOf<ForwardRuleEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<ForwardRuleEntity?>(null) }
    var testTarget by remember { mutableStateOf<ForwardRuleEntity?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (rules.isEmpty()) {
            item {
                GradientCard {
                    Text("등록된 전달규칙이 없습니다.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("+ 버튼을 눌러 앱, 검색어, 전달 방식을 설정해보세요.")
                }
            }
        }
        items(rules, key = { it.id }) { rule ->
            GradientCard(
                modifier = Modifier.combinedClickable(
                    onClick = {},
                    onLongClick = { selectedRule = rule },
                ),
                gradientColors = rule.cardColorHex.toCardGradientColors(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(rule.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (rule.enabled) "활성화" else "비활성화",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (rule.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    )
                    Text(
                        "대상 앱: ${
                            if (rule.appPackages.isEmpty()) {
                                "모든 앱"
                            } else {
                                rule.appPackages.joinToString { appLabels[it] ?: it }
                            }
                        }",
                    )
                    if (rule.excludedAppPackages.isNotEmpty()) {
                        Text(
                            "예외 앱: ${rule.excludedAppPackages.joinToString { appLabels[it] ?: it }}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text("검색어: ${rule.keywords.joinToString().ifBlank { "전체" }}")
                    Text("전달 방식: ${rule.actions.joinToString { actionLabel(it) }}")
                    if (rule.phoneNumbers.isNotEmpty()) {
                        Text("전화번호: ${rule.phoneNumbers.joinToString()}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (rule.webhookUrls.isNotEmpty()) {
                        Text(
                            "웹훅 URL: ${rule.webhookUrls.joinToString()}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (rule.telegramTargets.isNotEmpty()) {
                        Text(
                            "텔레그램: ${rule.telegramTargets.joinToString()}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (!rule.soundUri.isNullOrBlank()) {
                        AssistChip(onClick = {}, label = { Text("알림음 선택됨") })
                    }
                    if (!rule.cardColorHex.isNullOrBlank()) {
                        Text("카드 색상 적용됨", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }

    if (selectedRule != null) {
        AlertDialog(
            onDismissRequest = { selectedRule = null },
            title = { Text("전달규칙 작업") },
            text = { Text("'${selectedRule!!.name}' 규칙에서 실행할 작업을 선택하세요.") },
            confirmButton = {
                Row {
                    TextButton(
                        onClick = {
                            testTarget = selectedRule
                            selectedRule = null
                        },
                    ) {
                        Text("테스트")
                    }
                    TextButton(
                        onClick = {
                            onEdit(selectedRule!!)
                            selectedRule = null
                        },
                    ) {
                        Text("수정")
                    }
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            deleteTarget = selectedRule
                            selectedRule = null
                        },
                    ) {
                        Text("삭제")
                    }
                    TextButton(onClick = { selectedRule = null }) {
                        Text("닫기")
                    }
                }
            },
        )
    }

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("전달규칙 삭제") },
            text = { Text("'${deleteTarget!!.name}' 규칙을 삭제할까요?") },
            confirmButton = {
                Button(onClick = {
                    onDelete(deleteTarget!!.id)
                    deleteTarget = null
                }) {
                    Text("삭제")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { deleteTarget = null }) {
                    Text("취소")
                }
            },
        )
    }

    if (testTarget != null) {
        RuleTestDialog(
            rule = testTarget!!,
            onDismiss = { testTarget = null },
            onConfirm = { title, body ->
                onTest(testTarget!!, title, body)
                testTarget = null
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DeliveryHistoryScreen(
    deliveries: List<DeliveryHistoryEntity>,
    onDelete: (Long) -> Unit,
    onRetry: (DeliveryHistoryEntity) -> Unit,
    onRedeliver: (DeliveryHistoryEntity, DeliveryActionType, String, String?) -> Unit,
) {
    var detailDelivery by remember { mutableStateOf<DeliveryHistoryEntity?>(null) }
    var selectedDelivery by remember { mutableStateOf<DeliveryHistoryEntity?>(null) }
    var fullscreenImageUri by rememberSaveable { mutableStateOf<String?>(null) }
    var showRedeliverDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(DeliveryFilter.ALL) }
    val filteredDeliveries = remember(deliveries, filter) {
        deliveries.filter { delivery ->
            when (filter) {
                DeliveryFilter.ALL -> true
                DeliveryFilter.FAILED -> delivery.status == DeliveryStatus.FAILED
                DeliveryFilter.SUCCESS -> delivery.status == DeliveryStatus.SUCCESS
            }
        }
    }
    val successCount = remember(deliveries) { deliveries.count { it.status == DeliveryStatus.SUCCESS } }
    val failedCount = remember(deliveries) { deliveries.count { it.status == DeliveryStatus.FAILED } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            GradientCard {
                Text("\uC804\uB2EC \uC694\uC57D", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("\uC804\uCCB4 ${deliveries.size}\uAC74, \uC131\uACF5 $successCount\uAC74, \uC2E4\uD328 $failedCount\uAC74")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DeliveryFilter.entries.forEach { item ->
                        FilterChip(
                            selected = filter == item,
                            onClick = { filter = item },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        }
        if (filteredDeliveries.isEmpty()) {
            item {
                GradientCard {
                    Text(
                        if (deliveries.isEmpty()) "\uC804\uB2EC\uB41C \uB0B4\uC5ED\uC774 \uC5C6\uC2B5\uB2C8\uB2E4." else "'${filter.label}' \uC0C1\uD0DC \uB0B4\uC5ED\uC774 \uC5C6\uC2B5\uB2C8\uB2E4.",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (deliveries.isEmpty()) {
                            "\uADDC\uCE59\uC5D0 \uB9DE\uB294 \uC54C\uB9BC\uC774 \uB4E4\uC5B4\uC624\uBA74 \uC774 \uD654\uBA74\uC5D0 \uAE30\uB85D\uB429\uB2C8\uB2E4."
                        } else {
                            "\uD544\uD130\uB97C \uBC14\uAFB8\uAC70\uB098 \uB2E4\uB978 \uC804\uB2EC\uB0B4\uC5ED\uC744 \uD655\uC778\uD574\uBCF4\uC138\uC694."
                        },
                    )
                }
            }
        }
        items(filteredDeliveries, key = { it.id }) { item ->
            GradientCard(
                modifier = Modifier.combinedClickable(
                    onClick = { detailDelivery = item },
                    onLongClick = { selectedDelivery = item },
                ),
                gradientColors = item.cardColorHex.toCardGradientColors(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "전달규칙: ${item.deliveryRuleLabel()}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            item.title,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(8.dp))
                        StatusBadge(item.status)
                    }
                    Text(item.body)
                    Text("\uC218\uC2E0\uC571: ${item.sourceAppName}", style = MaterialTheme.typography.bodySmall)
                    OptionalPhoneNumberText(
                        label = "수신전화번호",
                        value = item.recipientAddress,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OptionalPhoneNumberText(
                        label = "발신전화번호",
                        value = item.senderAddress,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text("\uC218\uC2E0\uB0A0\uC9DC: ${DateFormats.date(item.receivedAt)}", style = MaterialTheme.typography.bodySmall)
                    Text("\uC218\uC2E0\uC2DC\uAC04: ${DateFormats.time(item.receivedAt)}", style = MaterialTheme.typography.bodySmall)
                    Text("\uC804\uB2EC\uC2DC\uAC04: ${DateFormats.dateTime(item.deliveredAt)}", style = MaterialTheme.typography.bodySmall)
                    Text("\uC804\uB2EC\uBC29\uC2DD: ${actionLabel(item.actionType)} / \uB300\uC0C1: ${item.target}", style = MaterialTheme.typography.bodySmall)
                    if (item.attachmentUris.isNotEmpty()) {
                        AttachmentThumbnailRow(
                            uris = item.attachmentUris,
                            onImageClick = { fullscreenImageUri = it },
                        )
                    }
                    if (!item.errorMessage.isNullOrBlank()) {
                        Text(
                            "\uC2E4\uD328 \uC0AC\uC720: ${item.errorMessage}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }

    if (detailDelivery != null) {
        CleanDeliveryDetailDialog(
            delivery = detailDelivery!!,
            onDismiss = { detailDelivery = null },
        )
    }

    fullscreenImageUri?.let { imageUri ->
        FullscreenAttachmentDialog(
            imageUri = imageUri,
            onDismiss = { fullscreenImageUri = null },
        )
    }

    if (selectedDelivery != null) {
        AlertDialog(
            onDismissRequest = { selectedDelivery = null },
            title = { Text("\uC804\uB2EC\uB0B4\uC5ED \uC791\uC5C5") },
            text = { Text("\uC0AD\uC81C, \uC7AC\uC804\uC1A1, \uB2E4\uC2DC \uC804\uB2EC \uC911 \uD544\uC694\uD55C \uC791\uC5C5\uC744 \uC120\uD0DD\uD558\uC138\uC694.") },
            confirmButton = {
                Row {
                    TextButton(
                        onClick = {
                            onRetry(selectedDelivery!!)
                            selectedDelivery = null
                        },
                    ) {
                        Text("\uC7AC\uC804\uC1A1")
                    }
                    TextButton(onClick = { showRedeliverDialog = true }) {
                        Text("\uB2E4\uC2DC \uC804\uB2EC")
                    }
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            showDeleteConfirm = true
                        },
                    ) {
                        Text("\uC0AD\uC81C")
                    }
                    TextButton(onClick = { selectedDelivery = null }) {
                        Text("\uB2EB\uAE30")
                    }
                }
            },
        )
    }

    if (showDeleteConfirm && selectedDelivery != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("\uC804\uB2EC\uB0B4\uC5ED \uC0AD\uC81C") },
            text = { Text("\uC120\uD0DD\uD55C \uC804\uB2EC\uB0B4\uC5ED\uC744 \uC0AD\uC81C\uD560\uAE4C\uC694?") },
            confirmButton = {
                Button(onClick = {
                    onDelete(selectedDelivery!!.id)
                    showDeleteConfirm = false
                    selectedDelivery = null
                }) {
                    Text("\uC0AD\uC81C")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirm = false }) {
                    Text("\uCDE8\uC18C")
                }
            },
        )
    }

    if (showRedeliverDialog && selectedDelivery != null) {
        RedeliverDialog(
            onDismiss = {
                showRedeliverDialog = false
                selectedDelivery = null
            },
            onConfirm = { actionType, target ->
                onRedeliver(selectedDelivery!!, actionType, target, null)
                showRedeliverDialog = false
                selectedDelivery = null
            },
        )
    }
}

@Composable
fun SettingsScreen(
    installedApps: List<InstalledAppInfo>,
    excludedNotificationApps: Set<String>,
    forwardingSettings: ForwardingSettings,
    runtimeDiagnostics: RuntimeDiagnostics,
    hasNotificationAccess: Boolean,
    hasSmsPermissions: Boolean,
    hasPhoneNumberPermission: Boolean,
    isIgnoringBatteryOptimizations: Boolean,
    appVersion: String,
    onOpenNotificationAccess: () -> Unit,
    onRequestSmsPermissions: () -> Unit,
    onRequestBatteryOptimizationExemption: () -> Unit,
    onOpenBatteryOptimizationSettings: () -> Unit,
    onRestartBackgroundMonitoring: () -> Unit,
    onSmsRetryCountChange: (Int) -> Unit,
    onSmsRetryDelayChange: (Int) -> Unit,
    onWebhookRetryCountChange: (Int) -> Unit,
    onWebhookRetryDelayChange: (Int) -> Unit,
    onToggleExcludedNotificationApp: (String) -> Unit,
    onExportRules: () -> Unit,
    onImportRules: () -> Unit,
    onExportData: () -> Unit,
    onImportData: () -> Unit,
    onClearCache: () -> Unit,
    onClearDataAndCache: () -> Unit,
) {
    var showClearCacheConfirm by remember { mutableStateOf(false) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var showPermissionSettingsDialog by remember { mutableStateOf(false) }
    var showExcludedNotificationAppsDialog by remember { mutableStateOf(false) }
    var showRuntimeDiagnosticsDialog by remember { mutableStateOf(false) }
    var appInfoTapCount by remember { mutableStateOf(0) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            GradientCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("전달 재시도 설정", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    RetrySettingRow(
                        title = "SMS 재시도 횟수",
                        value = forwardingSettings.smsRetryCount,
                        valueLabel = "${forwardingSettings.smsRetryCount}회",
                        minValue = 1,
                        maxValue = 5,
                        onValueChange = onSmsRetryCountChange,
                    )
                    RetrySettingRow(
                        title = "SMS 재시도 간격",
                        value = forwardingSettings.smsRetryDelaySeconds,
                        valueLabel = "${forwardingSettings.smsRetryDelaySeconds}초",
                        minValue = 1,
                        maxValue = 10,
                        onValueChange = onSmsRetryDelayChange,
                    )
                    RetrySettingRow(
                        title = "웹훅 재시도 횟수",
                        value = forwardingSettings.webhookRetryCount,
                        valueLabel = "${forwardingSettings.webhookRetryCount}회",
                        minValue = 1,
                        maxValue = 5,
                        onValueChange = onWebhookRetryCountChange,
                    )
                    RetrySettingRow(
                        title = "웹훅 재시도 간격",
                        value = forwardingSettings.webhookRetryDelaySeconds,
                        valueLabel = "${forwardingSettings.webhookRetryDelaySeconds}초",
                        minValue = 1,
                        maxValue = 10,
                        onValueChange = onWebhookRetryDelayChange,
                    )
                }
            }
        }
        item {
            SettingActionCard(
                icon = Icons.Default.Notifications,
                title = "알림수신제외앱",
                primaryText = "제외앱 설정",
                onPrimary = { showExcludedNotificationAppsDialog = true },
            )
        }
        item {
            SettingActionCard(
                icon = Icons.Default.Save,
                title = "규칙 백업/복원",
                primaryText = "규칙 백업",
                secondaryText = "규칙 복원",
                onPrimary = onExportRules,
                onSecondary = onImportRules,
            )
        }
        item {
            SettingActionCard(
                icon = Icons.Default.Restore,
                title = "데이터 백업/복원",
                primaryText = "데이터 백업",
                secondaryText = "데이터 복원",
                onPrimary = onExportData,
                onSecondary = onImportData,
            )
        }
        item {
            SettingActionCard(
                icon = Icons.Default.CleaningServices,
                title = "데이터&캐시삭제",
                primaryText = "캐시삭제",
                secondaryText = "데이터&캐시삭제",
                onPrimary = { showClearCacheConfirm = true },
                onSecondary = { showClearAllConfirm = true },
            )
        }
        item {
            GradientCard(
                modifier = Modifier.clickable {
                    appInfoTapCount += 1
                    if (appInfoTapCount >= 5) {
                        showRuntimeDiagnosticsDialog = true
                        appInfoTapCount = 0
                    }
                },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("앱정보", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(appVersion)
                    }
                    OutlinedButton(onClick = { showPermissionSettingsDialog = true }) {
                        Text("권한설정")
                    }
                }
            }
        }
    }

    if (showClearCacheConfirm) {
        AlertDialog(
            onDismissRequest = { showClearCacheConfirm = false },
            title = { Text("캐시 삭제") },
            text = { Text("임시 파일과 캐시를 정리할까요?") },
            confirmButton = {
                Button(onClick = {
                    onClearCache()
                    showClearCacheConfirm = false
                }) {
                    Text("삭제")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearCacheConfirm = false }) {
                    Text("취소")
                }
            },
        )
    }

    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text("데이터 & 캐시 삭제") },
            text = { Text("저장된 알림, 규칙, 전달내역과 캐시를 모두 삭제할까요?") },
            confirmButton = {
                Button(onClick = {
                    onClearDataAndCache()
                    showClearAllConfirm = false
                }) {
                    Text("삭제")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearAllConfirm = false }) {
                    Text("취소")
                }
            },
        )
    }

    if (showPermissionSettingsDialog) {
        PermissionSettingsDialog(
            hasNotificationAccess = hasNotificationAccess,
            hasSmsPermissions = hasSmsPermissions,
            hasPhoneNumberPermission = hasPhoneNumberPermission,
            isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
            onDismiss = { showPermissionSettingsDialog = false },
            onOpenNotificationAccess = onOpenNotificationAccess,
            onRequestSmsPermissions = onRequestSmsPermissions,
            onRequestBatteryOptimizationExemption = onRequestBatteryOptimizationExemption,
            onOpenBatteryOptimizationSettings = onOpenBatteryOptimizationSettings,
            onRestartBackgroundMonitoring = onRestartBackgroundMonitoring,
        )
    }

    if (showExcludedNotificationAppsDialog) {
        InstalledAppSelectorDialog(
            installedApps = installedApps,
            title = "알림수신 제외앱 설정",
            selectedPackages = excludedNotificationApps,
            onTogglePackage = onToggleExcludedNotificationApp,
            onDismiss = { showExcludedNotificationAppsDialog = false },
        )
    }

    if (showRuntimeDiagnosticsDialog) {
        RuntimeDiagnosticsDialog(
            runtimeDiagnostics = runtimeDiagnostics,
            onDismiss = { showRuntimeDiagnosticsDialog = false },
        )
    }
}

@Composable
private fun RuntimeDiagnosticsDialog(
    runtimeDiagnostics: RuntimeDiagnostics,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("수신 진단") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("앱알림 마지막 감지: ${runtimeDiagnostics.lastAppDetectedAt.formatDiagnosticTime()}")
                Text("SMS 마지막 감지: ${runtimeDiagnostics.lastSmsDetectedAt.formatDiagnosticTime()}")
                Text("MMS 마지막 감지: ${runtimeDiagnostics.lastMmsDetectedAt.formatDiagnosticTime()}")
                Text(
                    "마지막 저장: ${runtimeDiagnostics.lastStoredAt.formatDiagnosticTime()}" +
                        runtimeDiagnostics.lastStoredSource.takeIf { it.isNotBlank() }?.let {
                            " (${it.diagnosticSourceLabel()})"
                        }.orEmpty(),
                )
                if (runtimeDiagnostics.lastErrorMessage.isNotBlank()) {
                    Text(
                        "마지막 오류: ${runtimeDiagnostics.lastErrorAt.formatDiagnosticTime()} / ${runtimeDiagnostics.lastErrorMessage}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("닫기")
            }
        },
    )
}

@Composable
private fun PermissionSettingsDialog(
    hasNotificationAccess: Boolean,
    hasSmsPermissions: Boolean,
    hasPhoneNumberPermission: Boolean,
    isIgnoringBatteryOptimizations: Boolean,
    onDismiss: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onRequestSmsPermissions: () -> Unit,
    onRequestBatteryOptimizationExemption: () -> Unit,
    onOpenBatteryOptimizationSettings: () -> Unit,
    onRestartBackgroundMonitoring: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("권한 설정") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AccessCard(
                    title = "알림 권한",
                    actionText = if (hasNotificationAccess) "설정 완료" else "설정 열기",
                    actionEnabled = !hasNotificationAccess,
                    onAction = onOpenNotificationAccess,
                )
                AccessCard(
                    title = "SMS/MMS 권한",
                    actionText = if (hasSmsPermissions) "권한 허용됨" else "권한 요청",
                    actionEnabled = !hasSmsPermissions,
                    onAction = onRequestSmsPermissions,
                )
                AccessCard(
                    title = "전화번호 읽기 권한",
                    actionText = if (hasPhoneNumberPermission) "권한 허용됨" else "권한 요청",
                    actionEnabled = !hasPhoneNumberPermission,
                    onAction = onRequestSmsPermissions,
                )
                GradientCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("백그라운드 실행 유지", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (!isIgnoringBatteryOptimizations) {
                                Button(onClick = onRequestBatteryOptimizationExemption) {
                                    Text("최적화 제외 요청")
                                }
                            }
                            OutlinedButton(onClick = onOpenBatteryOptimizationSettings) {
                                Text("배터리 설정 열기")
                            }
                            OutlinedButton(onClick = onRestartBackgroundMonitoring) {
                                Text("서비스 다시 시작")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("닫기")
            }
        },
    )
}

@Composable
private fun AccessCard(
    title: String,
    actionText: String,
    actionEnabled: Boolean,
    onAction: () -> Unit,
) {
    GradientCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = onAction, enabled = actionEnabled) {
                Text(actionText)
            }
        }
    }
}

@Composable
private fun SettingActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    primaryText: String,
    secondaryText: String? = null,
    onPrimary: () -> Unit,
    onSecondary: (() -> Unit)? = null,
) {
    GradientCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = title)
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPrimary) { Text(primaryText) }
                if (secondaryText != null && onSecondary != null) {
                    OutlinedButton(onClick = onSecondary) { Text(secondaryText) }
                }
            }
        }
    }
}

@Composable
private fun RetrySettingRow(
    title: String,
    value: Int,
    valueLabel: String,
    minValue: Int,
    maxValue: Int,
    onValueChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(valueLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onValueChange((value - 1).coerceAtLeast(minValue)) }, enabled = value > minValue) {
                Text("-")
            }
            Text(valueLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = { onValueChange((value + 1).coerceAtMost(maxValue)) }, enabled = value < maxValue) {
                Text("+")
            }
        }
    }
}

@Composable
private fun DeliveryDetailDialog(
    delivery: DeliveryHistoryEntity,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("전달내역 상세") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("제목: ${delivery.title}")
                Text("내용: ${delivery.body}")
                Text("수신앱: ${delivery.sourceAppName}")
                OptionalPhoneNumberText(
                    label = "수신전화번호",
                    value = delivery.recipientAddress,
                    style = MaterialTheme.typography.bodyMedium,
                )
                OptionalPhoneNumberText(
                    label = "발신전화번호",
                    value = delivery.senderAddress,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("수신날짜: ${DateFormats.date(delivery.receivedAt)}")
                Text("수신시간: ${DateFormats.time(delivery.receivedAt)}")
                Text("전달시간: ${DateFormats.dateTime(delivery.deliveredAt)}")
                Text("전달방식: ${actionLabel(delivery.actionType)}")
                Text("전달대상: ${delivery.target}")
                Text("상태: ${deliveryStatusLabel(delivery.status)}")
                if (!delivery.errorMessage.isNullOrBlank()) {
                    Text(
                        "실패 사유: ${delivery.errorMessage}",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("닫기")
            }
        },
    )
}

@Composable
private fun WebhookTestDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("웹훅 테스트 전송") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("테스트 메시지를 보낼 웹훅 URL을 입력해주세요.")
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("웹훅 URL") },
                    supportingText = { Text("예: https://example.com/webhook") },
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(url) }, enabled = url.isNotBlank()) {
                Text("전송")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("취소")
            }
        },
    )
}

@Composable
fun GradientCard(
    modifier: Modifier = Modifier,
    gradientColors: List<Color>? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = gradientColors ?: listOf(
        MaterialTheme.colorScheme.surface,
        MaterialTheme.colorScheme.surfaceVariant,
    )
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Column(
            modifier = Modifier
                .clip(MaterialTheme.shapes.large)
                .background(
                    Brush.linearGradient(
                        colors,
                    ),
                )
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content,
        )
    }
}

@Composable
private fun String?.toCardGradientColors(): List<Color>? {
    val base = parseCardColor(this) ?: return null
    return listOf(
        lerp(base, MaterialTheme.colorScheme.surface, 0.28f),
        lerp(base, MaterialTheme.colorScheme.surfaceVariant, 0.56f),
    )
}

@Composable
private fun favoriteNotificationGradientColors(): List<Color> {
    val accent = Color(0xFFFFF0B8)
    return listOf(
        lerp(accent, MaterialTheme.colorScheme.surface, 0.35f),
        lerp(accent, MaterialTheme.colorScheme.surfaceVariant, 0.58f),
    )
}

private fun parseCardColor(hex: String?): Color? {
    val normalized = hex?.trim().orEmpty()
    if (normalized.isBlank()) return null
    return runCatching {
        val colorInt = AndroidColor.parseColor(normalized)
        Color(
            red = AndroidColor.red(colorInt) / 255f,
            green = AndroidColor.green(colorInt) / 255f,
            blue = AndroidColor.blue(colorInt) / 255f,
            alpha = AndroidColor.alpha(colorInt) / 255f,
        )
    }.getOrNull()
}

fun actionLabel(action: DeliveryActionType): String {
    return when (action) {
        DeliveryActionType.SMS -> "문자"
        DeliveryActionType.WEBHOOK -> "웹훅"
        DeliveryActionType.TELEGRAM -> "텔레그램"
        DeliveryActionType.SOUND -> "소리"
    }
}

@Composable
private fun StatusBadge(status: DeliveryStatus) {
    val containerColor: Color
    val contentColor: Color
    when (status) {
        DeliveryStatus.SUCCESS -> {
            containerColor = MaterialTheme.colorScheme.primaryContainer
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        }

        DeliveryStatus.FAILED -> {
            containerColor = MaterialTheme.colorScheme.errorContainer
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        }
    }

    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = deliveryStatusLabel(status),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun deliveryStatusLabel(status: DeliveryStatus): String {
    return when (status) {
        DeliveryStatus.SUCCESS -> "\uC131\uACF5"
        DeliveryStatus.FAILED -> "\uC2E4\uD328"
    }
}

@Composable
private fun ExpandableSectionHeader(
    title: String,
    subtitle: String,
    preview: String,
    latestTime: String,
    query: String,
    favorite: Boolean,
    expanded: Boolean,
    onToggleFavorite: () -> Unit,
    onToggle: () -> Unit,
) {
    val headerColors = if (favorite) favoriteNotificationGradientColors() else listOf(
        MaterialTheme.colorScheme.background,
        MaterialTheme.colorScheme.background,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(headerColors))
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            HighlightedText(
                text = title,
                query = query,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            if (latestTime.isNotBlank()) {
                Text(
                    text = "\uCD5C\uC2E0 \uC218\uC2E0: $latestTime",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            if (!expanded && preview.isNotBlank()) {
                HighlightedText(
                    text = preview,
                    query = query,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (favorite) Icons.Default.Star else Icons.Outlined.StarOutline,
                    contentDescription = if (favorite) "\uC990\uACA8\uCC3E\uAE30 \uD574\uC81C" else "\uC990\uACA8\uCC3E\uAE30 \uCD94\uAC00",
                    tint = if (favorite) Color(0xFFFFC107) else MaterialTheme.colorScheme.outline,
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "\uC811\uAE30" else "\uD3BC\uCE58\uAE30",
            )
        }
    }
}

private fun NotificationEventEntity.previewText(): String {
    val bodyPreview = body.trim()
    val titlePreview = title.trim()
    return when {
        bodyPreview.isNotBlank() && titlePreview.isNotBlank() -> "$titlePreview - $bodyPreview"
        bodyPreview.isNotBlank() -> bodyPreview
        titlePreview.isNotBlank() -> titlePreview
        else -> "\uB0B4\uC6A9 \uC5C6\uC74C"
    }
}

private fun DeliveryHistoryEntity.deliveryRuleLabel(): String {
    return appliedRuleName?.trim()?.takeIf { it.isNotBlank() }
        ?: if (notificationId != null) "규칙 정보 없음" else "직접 전달"
}

private fun NotificationEventEntity.matchesSourceFilter(filter: NotificationSourceFilterOption): Boolean {
    return when (filter) {
        NotificationSourceFilterOption.ALL -> true
        NotificationSourceFilterOption.APP -> sourceType == SourceType.APP
        NotificationSourceFilterOption.SMS -> sourceType == SourceType.SMS
        NotificationSourceFilterOption.MMS -> sourceType == SourceType.MMS
    }
}

private fun NotificationGroupSortOption.label(): String {
    return when (this) {
        NotificationGroupSortOption.LATEST -> "\uCD5C\uC2E0\uC21C"
        NotificationGroupSortOption.NAME -> "\uC571\uBA85\uC21C"
    }
}

private fun NotificationSourceFilterOption.label(): String {
    return when (this) {
        NotificationSourceFilterOption.ALL -> "\uC804\uCCB4"
        NotificationSourceFilterOption.APP -> "\uC571\uC54C\uB9BC"
        NotificationSourceFilterOption.SMS -> "SMS"
        NotificationSourceFilterOption.MMS -> "MMS"
    }
}

private fun Long?.formatDiagnosticTime(): String {
    return this?.let(DateFormats::dateTime) ?: "기록 없음"
}

private fun String.diagnosticSourceLabel(): String {
    return when (this) {
        SourceType.APP.name -> "앱알림"
        SourceType.SMS.name -> "SMS"
        SourceType.MMS.name -> "MMS"
        else -> this
    }
}

@Composable
private fun OptionalPhoneNumberText(
    label: String,
    value: String?,
    style: TextStyle,
) {
    val phoneNumber = value?.trim().orEmpty()
    if (phoneNumber.isBlank()) return
    Text("$label: $phoneNumber", style = style)
}

@Composable
private fun HighlightedText(
    text: String,
    query: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isBlank() || text.isBlank()) {
        Text(
            text = text,
            modifier = modifier,
            style = style,
            color = color,
            fontWeight = fontWeight,
            maxLines = maxLines,
            overflow = overflow,
        )
        return
    }

    val annotated = remember(text, normalizedQuery) {
        buildHighlightedAnnotatedString(text, normalizedQuery)
    }

    Text(
        text = annotated,
        modifier = modifier,
        style = style,
        color = color,
        fontWeight = fontWeight,
        maxLines = maxLines,
        overflow = overflow,
    )
}

private fun buildHighlightedAnnotatedString(
    text: String,
    query: String,
) = buildAnnotatedString {
    val lowerText = text.lowercase()
    val lowerQuery = query.lowercase()
    var currentIndex = 0

    while (currentIndex < text.length) {
        val matchIndex = lowerText.indexOf(lowerQuery, currentIndex)
        if (matchIndex < 0) {
            append(text.substring(currentIndex))
            break
        }

        append(text.substring(currentIndex, matchIndex))
        pushStyle(
            SpanStyle(
                background = Color(0xFFFFD54F),
                color = Color(0xFF1F1300),
                fontWeight = FontWeight.Bold,
            ),
        )
        append(text.substring(matchIndex, matchIndex + query.length))
        pop()
        currentIndex = matchIndex + query.length
    }
}

