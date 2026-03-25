package com.ckchoi.notitoss.service

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.core.content.ContextCompat

object BackgroundRuntimeCoordinator {
    fun hasSmsCorePermissions(context: Context): Boolean {
        val smsPermissions = listOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
        )
        return smsPermissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasPhoneNumberReadPermission(context: Context): Boolean {
        val hasPhoneNumberPermission = listOf(
            Manifest.permission.READ_PHONE_NUMBERS,
            Manifest.permission.READ_PHONE_STATE,
        ).any { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
        return hasPhoneNumberPermission
    }

    fun hasSmsPermissions(context: Context): Boolean {
        return hasSmsCorePermissions(context) && hasPhoneNumberReadPermission(context)
    }

    fun hasNotificationAccess(context: Context): Boolean {
        val enabledListeners = Settings.Secure
            .getString(context.contentResolver, "enabled_notification_listeners")
            .orEmpty()
        return enabledListeners.contains(context.packageName)
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val powerManager = context.getSystemService(PowerManager::class.java) ?: return false
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun sync(context: Context) {
        syncMmsObserverService(context)
        requestNotificationListenerRebind(context)
    }

    fun syncMmsObserverService(context: Context) {
        val intent = Intent(context, MmsObserverService::class.java)
        if (hasSmsCorePermissions(context)) {
            runCatching { ContextCompat.startForegroundService(context, intent) }
        } else {
            context.stopService(intent)
        }
    }

    fun requestNotificationListenerRebind(context: Context) {
        if (!hasNotificationAccess(context)) return
        runCatching {
            NotificationListenerService.requestRebind(
                ComponentName(
                    context.packageName,
                    "${context.packageName}.service.NotificationCaptureService",
                ),
            )
        }
    }
}
