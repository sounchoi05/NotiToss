package com.ckchoi.notitoss.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Markunread
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ckchoi.notitoss.ui.components.ScreenBackground

@Composable
fun PermissionConsentScreen(
    hasNotificationAccess: Boolean,
    hasSmsPermissions: Boolean,
    hasPhoneNumberPermission: Boolean,
    isIgnoringBatteryOptimizations: Boolean,
    onAgreeAndStart: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onRequestSmsPermissions: () -> Unit,
    onOpenBatteryOptimizationSettings: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    ScreenBackground(
        modifier = Modifier.fillMaxSize(),
        padding = PaddingValues(0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "접근 권한 동의",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "노티토스가 알림, SMS/MMS, 전달 기능을 안정적으로 실행하려면 아래 권한이 필요합니다.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                "필수 접근 권한",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            PermissionInfoCard(
                title = "알림 접근",
                body = "앱 알림을 실시간으로 읽어 알림내역에 저장하고 전달규칙을 실행합니다.",
                granted = hasNotificationAccess,
                actionText = if (hasNotificationAccess) "허용됨" else "설정 열기",
                onAction = onOpenNotificationAccess,
                icon = { Icon(Icons.Default.NotificationsActive, contentDescription = null) },
            )

            PermissionInfoCard(
                title = "SMS / MMS",
                body = "문자와 MMS를 감지하고 본문, 발신번호, 첨부이미지를 읽어 전달할 때 사용합니다.",
                granted = hasSmsPermissions,
                actionText = if (hasSmsPermissions) "허용됨" else "권한 요청",
                onAction = onRequestSmsPermissions,
                icon = { Icon(Icons.Default.Markunread, contentDescription = null) },
            )

            if (!hasSmsPermissions) {
                SmsRestrictedSettingsNotice()
            }

            PermissionInfoCard(
                title = "전화 정보",
                body = "듀얼 유심을 포함해 어떤 회선으로 수신됐는지 확인하고 수신전화번호를 정확히 기록합니다.",
                granted = hasPhoneNumberPermission,
                actionText = if (hasPhoneNumberPermission) "허용됨" else "권한 요청",
                onAction = onRequestSmsPermissions,
                icon = { Icon(Icons.Default.PhoneAndroid, contentDescription = null) },
            )

            PermissionInfoCard(
                title = "백그라운드 실행 유지",
                body = "배터리 최적화 제한을 줄여 재부팅 후에도 감시 서비스가 오래 유지되고 전달 누락을 줄입니다.",
                granted = isIgnoringBatteryOptimizations,
                actionText = if (isIgnoringBatteryOptimizations) "설정됨" else "설정 열기",
                onAction = onOpenBatteryOptimizationSettings,
                icon = { Icon(Icons.Default.BatteryChargingFull, contentDescription = null) },
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "권한을 거부한 경우",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            GradientCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "거부된 권한 설정 안내",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "앱 사용 중 필요한 권한이 빠져 있으면 알림 수집과 전달이 멈출 수 있습니다. 아래 버튼으로 다시 권한을 요청하거나 설정 화면을 열어 한 번에 완료해 주세요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = onOpenNotificationAccess,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("알림 설정")
                        }
                        OutlinedButton(
                            onClick = onOpenBatteryOptimizationSettings,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("배터리 설정")
                        }
                    }
                }
            }

            Text(
                "앱의 권한은 앱설정에서 언제든 변경할 수 있지만, 필수 권한이 동의되지 않으면 노티토스가 정상적으로 작동하지 않을 수 있습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = onAgreeAndStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Text("동의하고 시작")
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SmsRestrictedSettingsNotice() {
    GradientCard(
        gradientColors = listOf(Color(0xFFFFE2D6), Color(0xFFFFF2EA)),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "SMS/MMS 제한된 설정 안내",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF9A3412),
            )
            Text(
                "권한 허용 중 \"SMS에 대한 앱의 액세스가 거부됨\"이 표시되면, 설정 > 앱 > 노티토스 > 우측 상단 점 세개 > 제한된 설정 허용을 선택한 뒤 앱을 다시 실행해서 다시 동의해 주세요.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF7C2D12),
            )
        }
    }
}

@Composable
private fun PermissionInfoCard(
    title: String,
    body: String,
    granted: Boolean,
    actionText: String,
    onAction: () -> Unit,
    icon: @Composable () -> Unit,
) {
    GradientCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    icon()
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    if (granted) "허용됨" else "필요",
                    color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = onAction,
                enabled = !granted,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(actionText)
            }
        }
    }
}
