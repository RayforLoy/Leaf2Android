package com.leaf2android.connection

import com.leaf2android.R
import com.leaf2android.localization.*

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.leaf2android.LeafApplication
import com.leaf2android.MainActivity
import com.leaf2android.data.SessionState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

class LeafSessionService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repo get() = (application as LeafApplication).repository
    private var observer: Job? = null
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, tr(R.string.digital_back_shooting_session_ea8bc), NotificationManager.IMPORTANCE_LOW))
        serviceScope.launch {
            L10n.revisions.collectLatest {
                val manager = getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(NotificationChannel(CHANNEL, tr(R.string.digital_back_shooting_session_ea8bc), NotificationManager.IMPORTANCE_LOW))
                if (repo.state.value.active) manager.notify(1, notification(repo.state.value))
            }
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == DISCONNECT) { repo.disconnect(); stopSelf(); return START_NOT_STICKY }
        val address = intent?.getStringExtra("address")
        if (address == null) { stopSelf(); return START_NOT_STICKY }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            repo.showError(msg(R.string.nearby_devices_permission_has_not_been_granted_8fe7f)); stopSelf(); return START_NOT_STICKY
        }
        try {
            ServiceCompat.startForeground(this, 1, notification(repo.state.value), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            val adapter = (application as LeafApplication).discovery.adapter
            if (adapter == null) { repo.showError(msg(R.string.this_device_has_no_bluetooth_adapter_66222)); stopSelf(); return START_NOT_STICKY }
            (application as LeafApplication).discovery.stop()
            repo.connect(address, intent.getStringExtra("name") ?: com.leaf2android.data.BACK_FAMILY, BluetoothTransport(adapter, address, repo.settings.value.insecure))
            observer?.cancel()
            observer = serviceScope.launch {
                repo.state.collectLatest { s ->
                    if (!s.active) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
                    else getSystemService(NotificationManager::class.java).notify(1, notification(s))
                }
            }
        } catch (e: Exception) { repo.showError(msg(R.string.could_not_start_shooting_session_1_s_2cf4f , e.message)); repo.disconnect(); stopSelf() }
        return START_NOT_STICKY
    }
    private fun notification(state: SessionState): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val disconnect = PendingIntent.getService(this, 1, Intent(this, LeafSessionService::class.java).setAction(DISCONNECT), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_leaf)
            .setContentTitle("Leaf · ${state.deviceName}").setContentText(state.operation.resolve().ifEmpty { state.phase.label })
            .setContentIntent(open).setOngoing(true).setSilent(true)
            .addAction(0, tr(R.string.disconnect_eb724), disconnect).build()
    }
    override fun onDestroy() {
        observer?.cancel(); serviceScope.cancel()
        if (repo.state.value.active) repo.disconnect()
        super.onDestroy()
    }
    companion object {
        private const val CHANNEL = "leaf-session"
        private const val DISCONNECT = "com.leaf2android.DISCONNECT"
        fun connect(context: Context, address: String, name: String) {
            ContextCompat.startForegroundService(context, Intent(context, LeafSessionService::class.java).putExtra("address", address).putExtra("name", name))
        }
        fun disconnect(context: Context) {
            (context.applicationContext as LeafApplication).repository.disconnect()
            context.stopService(Intent(context, LeafSessionService::class.java))
        }
    }
}
