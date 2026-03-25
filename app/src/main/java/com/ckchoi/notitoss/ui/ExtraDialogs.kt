package com.ckchoi.notitoss.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.ckchoi.notitoss.data.DeliveryHistoryEntity
import com.ckchoi.notitoss.data.DeliveryStatus
import com.ckchoi.notitoss.data.ForwardRuleEntity
import com.ckchoi.notitoss.service.DateFormats

@Composable
fun CleanDeliveryDetailDialog(
    delivery: DeliveryHistoryEntity,
    onDismiss: () -> Unit,
) {
    var fullscreenImageUri by rememberSaveable { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("전달내역 상세") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "전달규칙: ${delivery.appliedRuleName?.trim()?.takeIf { it.isNotBlank() } ?: if (delivery.notificationId != null) "규칙 정보 없음" else "직접 전달"}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text("제목: ${delivery.title}")
                Text("내용: ${delivery.body}")
                Text("수신앱: ${delivery.sourceAppName}")
                delivery.recipientAddress?.trim()?.takeIf { it.isNotBlank() }?.let {
                    Text("수신전화번호: $it")
                }
                delivery.senderAddress?.trim()?.takeIf { it.isNotBlank() }?.let {
                    Text("발신전화번호: $it")
                }
                Text("수신날짜: ${DateFormats.date(delivery.receivedAt)}")
                Text("수신시간: ${DateFormats.time(delivery.receivedAt)}")
                Text("전달시간: ${DateFormats.dateTime(delivery.deliveredAt)}")
                Text("전달방식: ${actionLabel(delivery.actionType)}")
                Text("전달대상: ${delivery.target}")
                Text("상태: ${deliveryStatusLabelForDialog(delivery)}")
                if (!delivery.errorMessage.isNullOrBlank()) {
                    Text(
                        "실패 사유: ${delivery.errorMessage}",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (delivery.attachmentUris.isNotEmpty()) {
                    Text(
                        "첨부 이미지",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    AttachmentGallery(
                        uris = delivery.attachmentUris,
                        onImageClick = { fullscreenImageUri = it },
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

    fullscreenImageUri?.let { imageUri ->
        FullscreenAttachmentDialog(
            imageUri = imageUri,
            onDismiss = { fullscreenImageUri = null },
        )
    }
}

@Composable
fun CleanWebhookTestDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var url by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("웹훅 테스트 전송") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("테스트 메시지를 보낼 웹훅 URL을 입력해주세요.")
                Text(
                    "일반 웹훅과 Discord 웹훅을 모두 지원합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("웹훅 URL") },
                    supportingText = {
                        Text("예: https://example.com/webhook 또는 https://discord.com/api/webhooks/...")
                    },
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
fun RuleTestDialog(
    rule: ForwardRuleEntity,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("${rule.name} 테스트") }
    var body by rememberSaveable { mutableStateOf("전달규칙 테스트 메시지입니다.") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("규칙 테스트 실행") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("'${rule.name}' 규칙으로 테스트 전달을 실행합니다.")
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("테스트 제목") },
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    label = { Text("테스트 본문") },
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(title, body) }) {
                Text("실행")
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
fun AttachmentThumbnailRow(
    uris: List<String>,
    onImageClick: (String) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        uris.forEach { uri ->
            AsyncImage(
                model = uri,
                contentDescription = "첨부 이미지",
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onImageClick(uri) },
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
fun AttachmentGallery(
    uris: List<String>,
    onImageClick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        uris.forEach { uri ->
            AsyncImage(
                model = uri,
                contentDescription = "첨부 이미지",
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onImageClick(uri) },
                contentScale = ContentScale.FillWidth,
            )
        }
    }
}

@Composable
fun FullscreenAttachmentDialog(
    imageUri: String,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
                .pointerInput(imageUri) {
                    detectTapGestures(onDoubleTap = { onDismiss() })
                },
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = imageUri,
                contentDescription = "첨부 이미지 전체화면",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentScale = ContentScale.Fit,
            )
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp),
            ) {
                Text("닫기")
            }
            Text(
                text = "이미지를 더블클릭하면 닫힙니다.",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(20.dp),
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun deliveryStatusLabelForDialog(delivery: DeliveryHistoryEntity): String {
    return when (delivery.status) {
        DeliveryStatus.SUCCESS -> "성공"
        DeliveryStatus.FAILED -> "실패"
    }
}
