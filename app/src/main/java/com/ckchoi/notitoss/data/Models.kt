package com.ckchoi.notitoss.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

enum class SourceType {
    APP,
    SMS,
    MMS,
}

@Serializable
enum class DeliveryActionType {
    SMS,
    WEBHOOK,
    TELEGRAM,
    SOUND,
}

@Serializable
enum class DeliveryStatus {
    SUCCESS,
    FAILED,
}

@Serializable
@Entity(tableName = "notification_events")
data class NotificationEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceType: SourceType,
    val sourceAppName: String,
    val sourcePackageName: String,
    val title: String,
    val body: String,
    val recipientAddress: String? = null,
    val senderAddress: String? = null,
    val attachmentUris: List<String> = emptyList(),
    val receivedAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "forward_rules")
data class ForwardRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val appPackages: List<String>,
    val excludedAppPackages: List<String> = emptyList(),
    val keywords: List<String>,
    val actions: List<DeliveryActionType>,
    val phoneNumbers: List<String>,
    val webhookUrls: List<String>,
    val telegramTargets: List<String> = emptyList(),
    val soundUri: String? = null,
    val cardColorHex: String? = null,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "delivery_history")
data class DeliveryHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val notificationId: Long? = null,
    val appliedRuleId: Long? = null,
    val appliedRuleName: String? = null,
    val title: String,
    val body: String,
    val sourceAppName: String,
    val sourcePackageName: String,
    val recipientAddress: String? = null,
    val senderAddress: String? = null,
    val attachmentUris: List<String> = emptyList(),
    val cardColorHex: String? = null,
    val receivedAt: Long,
    val actionType: DeliveryActionType,
    val target: String,
    val status: DeliveryStatus,
    val deliveredAt: Long = System.currentTimeMillis(),
    val errorMessage: String? = null,
)

@Serializable
data class BackupPayload(
    val notifications: List<NotificationEventEntity> = emptyList(),
    val rules: List<ForwardRuleEntity> = emptyList(),
    val deliveries: List<DeliveryHistoryEntity> = emptyList(),
    val exportedAt: Long = System.currentTimeMillis(),
)

data class InstalledAppInfo(
    val packageName: String,
    val label: String,
)

data class ForwardContent(
    val title: String,
    val body: String,
    val sourceAppName: String,
    val sourcePackageName: String,
    val recipientAddress: String?,
    val senderAddress: String?,
    val attachmentUris: List<String> = emptyList(),
    val receivedAt: Long,
    val notificationId: Long? = null,
)

fun NotificationEventEntity.toForwardContent(): ForwardContent = ForwardContent(
    title = title,
    body = body,
    sourceAppName = sourceAppName,
    sourcePackageName = sourcePackageName,
    recipientAddress = recipientAddress,
    senderAddress = senderAddress,
    attachmentUris = attachmentUris,
    receivedAt = receivedAt,
    notificationId = id,
)
