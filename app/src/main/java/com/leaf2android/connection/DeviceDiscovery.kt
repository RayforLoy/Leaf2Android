package com.leaf2android.connection

import com.leaf2android.R
import com.leaf2android.localization.*

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class LeafDevice(val address: String, val name: String, val bondState: Int, val rssi: Int? = null)
data class DiscoveryState(val devices: List<LeafDevice> = emptyList(), val scanning: Boolean = false, val error: UiText? = null)

@SuppressLint("MissingPermission")
class DeviceDiscovery(private val context: Context) {
    val adapter: BluetoothAdapter? get() = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val mutable = MutableStateFlow(DiscoveryState())
    val state = mutable.asStateFlow()
    private var registered = false
    val hasPermissions: Boolean get() = listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        .all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!hasPermissions) return
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND, BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    @Suppress("DEPRECATION") val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).takeUnless { it == Short.MIN_VALUE }?.toInt()
                    add(device, rssi)
                    if (intent.action == BluetoothDevice.ACTION_BOND_STATE_CHANGED && device.bondState == BluetoothDevice.BOND_NONE)
                        mutable.update { it.copy(error = msg(R.string.pairing_did_not_complete_or_has_expired_forget_b9880)) }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> mutable.update { it.copy(scanning = false) }
                BluetoothAdapter.ACTION_STATE_CHANGED -> if (adapter?.isEnabled != true) mutable.update { it.copy(scanning = false, error = msg(R.string.phone_bluetooth_has_been_turned_off_f410b)) }
            }
        }
    }

    private fun add(device: BluetoothDevice, rssi: Int? = null) {
        val entry = LeafDevice(device.address, device.name?.takeIf { it.isNotBlank() } ?: tr(R.string.unnamed_bluetooth_device_84852), device.bondState, rssi)
        mutable.update { s -> s.copy(devices = (s.devices.filterNot { it.address == entry.address } + entry)
            .sortedWith(compareByDescending<LeafDevice> { it.bondState == BluetoothDevice.BOND_BONDED }.thenBy { it.name })) }
    }
    fun refresh() {
        if (!hasPermissions) { mutable.update { it.copy(error = msg(R.string.grant_nearby_devices_permission_to_access_bluet_81a22)) }; return }
        if (!registered) {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND); addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED); addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            }
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
            registered = true
        }
        mutable.update { it.copy(error = null, devices = emptyList()) }
        try { adapter?.bondedDevices?.forEach { add(it) } } catch (e: SecurityException) { report(e) }
        if (adapter == null) mutable.update { it.copy(error = msg(R.string.this_device_has_no_bluetooth_adapter_you_can_us_3f9bb)) }
    }
    fun scan() {
        refresh()
        if (!hasPermissions) return
        try {
            val bt = adapter ?: return
            if (!bt.isEnabled) { mutable.update { it.copy(error = msg(R.string.turn_on_phone_bluetooth_first_d2fda)) }; return }
            bt.cancelDiscovery()
            mutable.update { it.copy(scanning = bt.startDiscovery()) }
            if (!mutable.value.scanning) mutable.update { it.copy(error = msg(R.string.discovery_could_not_start_try_again_later_47e9f)) }
        } catch (e: Exception) { report(e) }
    }
    fun pair(address: String) {
        if (!hasPermissions) return
        try {
            if (adapter?.getRemoteDevice(address)?.createBond() != true)
                mutable.update { it.copy(error = msg(R.string.system_pairing_did_not_start_use_system_bluetoo_7fc58)) }
        } catch (e: Exception) { report(e) }
    }
    fun stop() {
        if (hasPermissions) try { adapter?.cancelDiscovery() } catch (_: SecurityException) { }
        mutable.update { it.copy(scanning = false) }
    }
    private fun report(e: Exception) { mutable.update { it.copy(error = errorText(e, msg(R.string.bluetooth_operation_failed_fb602))) } }
}
