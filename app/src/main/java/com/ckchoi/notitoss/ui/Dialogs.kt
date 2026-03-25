@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.ckchoi.notitoss.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ckchoi.notitoss.data.DeliveryActionType
import com.ckchoi.notitoss.data.InstalledAppInfo
import kotlinx.coroutines.delay

@Composable
fun RuleEditorDialog(
    state: RuleEditorState,
    installedApps: List<InstalledAppInfo>,
    onDismiss: () -> Unit,
    onRuleNameChange: (String) -> Unit,
    onTogglePackage: (String) -> Unit,
    onToggleExcludedPackage: (String) -> Unit,
    onKeywordChange: (String) -> Unit,
    onToggleAction: (DeliveryActionType) -> Unit,
    onPhoneChange: (String) -> Unit,
    onAddWebhook: () -> Unit,
    onWebhookChange: (Int, String) -> Unit,
    onRemoveWebhook: (Int) -> Unit,
    onAddTelegram: (String, String) -> Unit,
    onRemoveTelegram: (Int) -> Unit,
    onPickRingtone: () -> Unit,
    onColorChange: (String?) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onSave: () -> Unit,
) {
    var showAppSelector by rememberSaveable { mutableStateOf(false) }
    var showExcludedAppSelector by rememberSaveable { mutableStateOf(false) }
    var showTelegramDialog by rememberSaveable { mutableStateOf(false) }
    var pendingFocusAction by remember { mutableStateOf<DeliveryActionType?>(null) }
    val phoneFocusRequester = remember { FocusRequester() }
    val webhookFocusRequester = remember { FocusRequester() }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize(0.92f),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .navigationBarsPadding(),
            ) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("전달규칙 편집", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Switch(checked = state.enabled, onCheckedChange = onEnabledChange)
                        }
                    }
                    item {
                        OutlinedTextField(
                            value = state.name,
                            onValueChange = onRuleNameChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("규칙 이름") },
                        )
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("알림수신앱 선택", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Button(onClick = { showAppSelector = true }) {
                                Text("설치된 앱 전체 리스트 불러오기")
                            }
                            if (state.selectedPackages.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    state.selectedPackages.forEach { packageName ->
                                        AssistChip(
                                            onClick = { onTogglePackage(packageName) },
                                            label = {
                                                Text(installedApps.firstOrNull { it.packageName == packageName }?.label ?: packageName)
                                            },
                                        )
                                    }
                                }
                            } else {
                                AssistChip(
                                    onClick = { showAppSelector = true },
                                    label = { Text("모든 앱") },
                                )
                            }
                        }
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("예외앱 설정", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Button(onClick = { showExcludedAppSelector = true }) {
                                Text("제외할 앱 선택")
                            }
                            if (state.excludedPackages.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    state.excludedPackages.forEach { packageName ->
                                        AssistChip(
                                            onClick = { onToggleExcludedPackage(packageName) },
                                            label = {
                                                Text(installedApps.firstOrNull { it.packageName == packageName }?.label ?: packageName)
                                            },
                                        )
                                    }
                                }
                            } else {
                                AssistChip(
                                    onClick = { showExcludedAppSelector = true },
                                    label = { Text("없음") },
                                )
                            }
                        }
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("카드 색상", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                ruleColorOptions().forEach { option ->
                                    FilterChip(
                                        selected = state.cardColorHex == option.hex,
                                        onClick = { onColorChange(option.hex) },
                                        label = { Text(option.label) },
                                        leadingIcon = {
                                            ColorDot(
                                                color = option.previewColor,
                                                selected = state.cardColorHex == option.hex,
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                    item {
                        OutlinedTextField(
                            value = state.keywordInput,
                            onValueChange = onKeywordChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("검색어 입력 (콤마 구분)") },
                        )
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("전달 선택", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                DeliveryActionType.entries.forEach { action ->
                                    FilterChip(
                                        selected = state.selectedActions.contains(action),
                                        onClick = {
                                            pendingFocusAction = when {
                                                !state.selectedActions.contains(action) && action == DeliveryActionType.SMS -> DeliveryActionType.SMS
                                                !state.selectedActions.contains(action) && action == DeliveryActionType.WEBHOOK -> DeliveryActionType.WEBHOOK
                                                else -> null
                                            }
                                            onToggleAction(action)
                                        },
                                        label = { Text(actionLabel(action)) },
                                    )
                                }
                            }
                        }
                    }
                    if (state.selectedActions.contains(DeliveryActionType.SMS)) {
                        item {
                            Column {
                                OutlinedTextField(
                                    value = state.phoneInput,
                                    onValueChange = onPhoneChange,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(phoneFocusRequester),
                                    label = { Text("수신받을 전화번호 다중입력") },
                                    supportingText = { Text("예: 01012345678, 01000000000") },
                                )
                                LaunchedEffect(pendingFocusAction, state.selectedActions.contains(DeliveryActionType.SMS)) {
                                    if (
                                        pendingFocusAction == DeliveryActionType.SMS &&
                                        state.selectedActions.contains(DeliveryActionType.SMS)
                                    ) {
                                        delay(120)
                                        runCatching { phoneFocusRequester.requestFocus() }
                                        pendingFocusAction = null
                                    }
                                }
                            }
                        }
                    }
                    if (state.selectedActions.contains(DeliveryActionType.WEBHOOK)) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("웹훅 URL", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    IconButton(onClick = onAddWebhook) {
                                        Icon(Icons.Default.Add, contentDescription = "웹훅 추가")
                                    }
                                }
                                if (state.webhookInputs.isEmpty()) {
                                    OutlinedButton(onClick = onAddWebhook, modifier = Modifier.fillMaxWidth()) {
                                        Text("웹훅 추가")
                                    }
                                } else {
                                    state.webhookInputs.forEachIndexed { index, webhook ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            OutlinedTextField(
                                                value = webhook,
                                                onValueChange = { onWebhookChange(index, it) },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .let { if (index == 0) it.focusRequester(webhookFocusRequester) else it },
                                                label = { Text("웹훅 URL") },
                                                supportingText = { Text("예: https://example.com/hook") },
                                            )
                                            IconButton(
                                                onClick = { onRemoveWebhook(index) },
                                                enabled = state.webhookInputs.size > 1,
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "웹훅 삭제")
                                            }
                                        }
                                    }
                                }
                                LaunchedEffect(
                                    pendingFocusAction,
                                    state.selectedActions.contains(DeliveryActionType.WEBHOOK),
                                    state.webhookInputs.size,
                                ) {
                                    if (
                                        pendingFocusAction == DeliveryActionType.WEBHOOK &&
                                        state.selectedActions.contains(DeliveryActionType.WEBHOOK)
                                    ) {
                                        if (state.webhookInputs.isEmpty()) {
                                            onAddWebhook()
                                        }
                                        delay(120)
                                        runCatching { webhookFocusRequester.requestFocus() }
                                        pendingFocusAction = null
                                    }
                                }
                            }
                        }
                    }
                    if (state.selectedActions.contains(DeliveryActionType.TELEGRAM)) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("텔레그램", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    IconButton(onClick = { showTelegramDialog = true }) {
                                        Icon(Icons.Default.Add, contentDescription = "텔레그램 추가")
                                    }
                                }
                                if (state.telegramTargets.isEmpty()) {
                                    OutlinedButton(
                                        onClick = { showTelegramDialog = true },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text("텔레그램 봇 추가")
                                    }
                                } else {
                                    state.telegramTargets.forEachIndexed { index, target ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            OutlinedTextField(
                                                value = target,
                                                onValueChange = {},
                                                readOnly = true,
                                                modifier = Modifier.weight(1f),
                                                label = { Text("Bot API Token | 채널 ID") },
                                            )
                                            IconButton(onClick = { onRemoveTelegram(index) }) {
                                                Icon(Icons.Default.Delete, contentDescription = "텔레그램 삭제")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (state.selectedActions.contains(DeliveryActionType.SOUND)) {
                        item {
                            GradientCard {
                                Text("소리알림", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (state.soundUri.isNullOrBlank()) "선택된 알림음이 없습니다." else state.soundLabel.ifBlank { state.soundUri },
                                )
                                Button(onClick = onPickRingtone) {
                                    Icon(Icons.Default.Alarm, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("알림음 선택")
                                }
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("취소")
                    }
                    Button(onClick = onSave, modifier = Modifier.weight(1f)) {
                        Text("저장")
                    }
                }
            }
        }
    }

    if (showAppSelector) {
        InstalledAppSelectorDialog(
            installedApps = installedApps,
            title = "설치된 앱 선택",
            selectedPackages = state.selectedPackages,
            onTogglePackage = onTogglePackage,
            onDismiss = { showAppSelector = false },
        )
    }

    if (showExcludedAppSelector) {
        InstalledAppSelectorDialog(
            installedApps = installedApps,
            title = "예외앱 선택",
            selectedPackages = state.excludedPackages,
            onTogglePackage = onToggleExcludedPackage,
            onDismiss = { showExcludedAppSelector = false },
        )
    }

    if (showTelegramDialog) {
        TelegramTargetEditorDialog(
            onDismiss = { showTelegramDialog = false },
            onConfirm = { token, chatId ->
                onAddTelegram(token, chatId)
                showTelegramDialog = false
            },
        )
    }
}

@Composable
private fun TelegramTargetEditorDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var token by rememberSaveable { mutableStateOf("") }
    var chatId by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("텔레그램 봇 추가") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Bot API Token") },
                )
                OutlinedTextField(
                    value = chatId,
                    onValueChange = { chatId = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("채널 ID") },
                    supportingText = { Text("예: -1001234567890") },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(token.trim(), chatId.trim()) },
                enabled = token.isNotBlank() && chatId.isNotBlank(),
            ) {
                Text("저장")
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
private fun ColorDot(
    color: Color,
    selected: Boolean,
    size: Dp = 18.dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(MaterialTheme.shapes.small)
            .background(color)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = MaterialTheme.shapes.small,
            )
            .background(color),
    )
}

private data class RuleColorOption(
    val label: String,
    val hex: String?,
    val previewColor: Color,
)

private fun ruleColorOptions(): List<RuleColorOption> = listOf(
    RuleColorOption("기본", null, Color(0xFFE1E6ED)),
    RuleColorOption("하늘", "#D8EEFF", Color(0xFFD8EEFF)),
    RuleColorOption("민트", "#DBF7EE", Color(0xFFDBF7EE)),
    RuleColorOption("노랑", "#FFF2C9", Color(0xFFFFF2C9)),
    RuleColorOption("피치", "#FFE1D2", Color(0xFFFFE1D2)),
    RuleColorOption("핑크", "#F8DCEE", Color(0xFFF8DCEE)),
    RuleColorOption("라벤더", "#E7E0FF", Color(0xFFE7E0FF)),
    RuleColorOption("슬레이트", "#DFE8F4", Color(0xFFDFE8F4)),
)

@Composable
fun InstalledAppSelectorDialog(
    installedApps: List<InstalledAppInfo>,
    title: String,
    selectedPackages: Set<String>,
    onTogglePackage: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filteredApps = remember(installedApps, query) {
        if (query.isBlank()) {
            installedApps
        } else {
            installedApps.filter {
                it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize(0.86f),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("앱 검색") },
                )
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = selectedPackages.contains(app.packageName),
                                onCheckedChange = { onTogglePackage(app.packageName) },
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(app.label, fontWeight = FontWeight.SemiBold)
                                Text(app.packageName, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("선택 완료")
                }
            }
        }
    }
}

@Composable
fun RedeliverDialog(
    onDismiss: () -> Unit,
    onConfirm: (DeliveryActionType, String) -> Unit,
) {
    var actionType by rememberSaveable { mutableStateOf(DeliveryActionType.WEBHOOK) }
    var target by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("다시 전달") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(DeliveryActionType.SMS, DeliveryActionType.WEBHOOK, DeliveryActionType.TELEGRAM).forEach { action ->
                        FilterChip(
                            selected = actionType == action,
                            onClick = { actionType = action },
                            label = { Text(actionLabel(action)) },
                        )
                    }
                }
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(
                            when (actionType) {
                                DeliveryActionType.SMS -> "전화번호 입력"
                                DeliveryActionType.WEBHOOK -> "웹훅 URL 입력"
                                DeliveryActionType.TELEGRAM -> "봇토큰|채팅ID 입력"
                                DeliveryActionType.SOUND -> "대상 입력"
                            },
                        )
                    },
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(actionType, target.trim()) }, enabled = target.isNotBlank()) {
                Text("전달")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("취소")
            }
        },
    )
}
