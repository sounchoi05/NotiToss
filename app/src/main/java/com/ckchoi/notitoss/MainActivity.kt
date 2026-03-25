package com.ckchoi.notitoss

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.ckchoi.notitoss.service.BackgroundRuntimeCoordinator
import com.ckchoi.notitoss.ui.MainViewModel
import com.ckchoi.notitoss.ui.NotiTossApp
import com.ckchoi.notitoss.ui.theme.NotifyTossTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        MainViewModel.factory((application as NotiTossApplication).container.repository)
    }

    private var hasNotificationAccess by mutableStateOf(false)
    private var hasSmsPermissions by mutableStateOf(false)
    private var hasPhoneNumberPermission by mutableStateOf(false)
    private var isIgnoringBatteryOptimizations by mutableStateOf(true)
    private var continueEssentialPermissionFlow by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        refreshAccessState()
        syncBackgroundServices()
        continueEssentialPermissionFlowIfNeeded()
    }

    private val ringtonePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri: android.net.Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, android.net.Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI) as? android.net.Uri
            }
            val label = uri?.let { RingtoneManager.getRingtone(this, it)?.getTitle(this) }.orEmpty()
            viewModel.updateSound(uri?.toString(), label.ifBlank { "\uC120\uD0DD\uD55C \uC54C\uB9BC\uC74C" })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshAccessState()

        setContent {
            NotifyTossTheme {
                NotiTossApp(
                    viewModel = viewModel,
                    hasNotificationAccess = hasNotificationAccess,
                    hasSmsPermissions = hasSmsPermissions,
                    hasPhoneNumberPermission = hasPhoneNumberPermission,
                    isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
                    onRequestEssentialPermissions = ::requestEssentialPermissions,
                    onOpenNotificationAccess = {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                    onRequestSmsPermissions = {
                        permissionLauncher.launch(requiredRuntimePermissions().toTypedArray())
                    },
                    onRequestBatteryOptimizationExemption = ::requestBatteryOptimizationExemption,
                    onOpenBatteryOptimizationSettings = ::openBatteryOptimizationSettings,
                    onRestartBackgroundMonitoring = ::syncBackgroundServices,
                    onPickRingtone = {
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        }
                        ringtonePickerLauncher.launch(intent)
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshAccessState()
        syncBackgroundServices()
        continueEssentialPermissionFlowIfNeeded()
    }

    private fun refreshAccessState() {
        hasNotificationAccess = BackgroundRuntimeCoordinator.hasNotificationAccess(this)
        hasSmsPermissions = BackgroundRuntimeCoordinator.hasSmsPermissions(this)
        hasPhoneNumberPermission = BackgroundRuntimeCoordinator.hasPhoneNumberReadPermission(this)
        isIgnoringBatteryOptimizations = BackgroundRuntimeCoordinator.isIgnoringBatteryOptimizations(this)
    }

    private fun syncBackgroundServices() {
        BackgroundRuntimeCoordinator.sync(this)
    }

    private fun requestEssentialPermissions() {
        continueEssentialPermissionFlow = true
        val missingPermissions = requiredRuntimePermissions().filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            continueEssentialPermissionFlowIfNeeded()
        }
    }

    private fun continueEssentialPermissionFlowIfNeeded() {
        if (!continueEssentialPermissionFlow) return

        when {
            !hasNotificationAccess -> {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }

            !isIgnoringBatteryOptimizations -> {
                continueEssentialPermissionFlow = false
                requestBatteryOptimizationExemption()
            }

            else -> {
                continueEssentialPermissionFlow = false
            }
        }
    }

    private fun requiredRuntimePermissions(): List<String> {
        val permissions = mutableListOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_PHONE_NUMBERS,
            Manifest.permission.READ_PHONE_STATE,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        return permissions
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            openBatteryOptimizationSettings()
        }
    }

    private fun openBatteryOptimizationSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        }
    }
}
