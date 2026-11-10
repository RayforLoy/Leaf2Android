package com.leaf2android

import com.leaf2android.R
import com.leaf2android.localization.*

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.leaf2android.data.UiStyle
import com.leaf2android.connection.LeafSessionService
import com.leaf2android.ui.LeafApp

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        L10n.refresh()
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        val app = application as LeafApplication
        setContent {
            val appearance by app.repository.settings.collectAsStateWithLifecycle()
            val pdaAppearance = appearance.style == UiStyle.PDA
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = pdaAppearance
                    isAppearanceLightNavigationBars = pdaAppearance
                }
                window.isNavigationBarContrastEnforced = false
            }
            var pendingConnection by remember { mutableStateOf<Pair<String, String>?>(null) }
            val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
            val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
                if (result.values.all { it }) {
                    app.discovery.refresh()
                    pendingConnection?.let { (address, name) ->
                        try { LeafSessionService.connect(this, address, name) } catch (e: Exception) { app.repository.showError(errorText(e, msg(R.string.session_could_not_be_started_dcb5e))) }
                    }
                    if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else app.repository.showError(msg(R.string.nearby_devices_permission_is_needed_to_discover_e17c1))
                pendingConnection = null
            }
            val enableBluetooth = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { app.discovery.refresh() }
            LeafApp(
                repository = app.repository,
                discovery = app.discovery,
                requestPermissions = { permissions.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)) },
                enableBluetooth = {
                    if (app.discovery.hasPermissions) enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                    else permissions.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT))
                },
                connect = { address, name ->
                    if (!app.discovery.hasPermissions) {
                        pendingConnection = address to name
                        permissions.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT))
                    } else {
                        try { LeafSessionService.connect(this, address, name) } catch (e: Exception) { app.repository.showError(errorText(e, msg(R.string.session_could_not_be_started_dcb5e))) }
                        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                disconnect = { LeafSessionService.disconnect(this) },
                keepAwake = { keep ->
                    if (keep) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                },
                shareFile = { file ->
                    val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    startActivity(Intent.createChooser(send, tr(R.string.export_back_session_diagnostics_5563b)))
                },
            )
        }
    }
}
