package com.leaf2android.connection

import com.leaf2android.R
import com.leaf2android.localization.*

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import com.leaf2android.protocol.LeafTransport
import kotlinx.coroutines.*
import java.io.IOException
import java.util.UUID

@SuppressLint("MissingPermission") // UI grants SCAN/CONNECT before starting the session service.
class BluetoothTransport(
    private val adapter: BluetoothAdapter,
    private val address: String,
    private val insecure: Boolean,
) : LeafTransport {
    @Volatile private var socket: BluetoothSocket? = null
    override suspend fun connect() = coroutineScope {
        if (!adapter.isEnabled) throw LocalizedIOException(msg(R.string.phone_bluetooth_is_off_48cd0))
        adapter.cancelDiscovery()
        val device = adapter.getRemoteDevice(address)
        val next = if (insecure) device.createInsecureRfcommSocketToServiceRecord(SPP)
                   else device.createRfcommSocketToServiceRecord(SPP)
        socket = next
        val attempt = async(Dispatchers.IO) { next.connect() }
        try { withTimeout(35_000) { attempt.await() } }
        catch (e: Throwable) { close(); attempt.cancel(); throw e }
    }
    override suspend fun read(buffer: ByteArray): Int = withContext(Dispatchers.IO) {
        (socket ?: throw LocalizedIOException(msg(R.string.the_bluetooth_connection_is_closed_fffa9))).inputStream.read(buffer)
    }
    override suspend fun write(bytes: ByteArray) = withContext(Dispatchers.IO) {
        val output = (socket ?: throw LocalizedIOException(msg(R.string.the_bluetooth_connection_is_closed_fffa9))).outputStream
        output.write(bytes); output.flush()
    }
    override fun close() { try { socket?.close() } catch (_: IOException) { }; socket = null }
    companion object { val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb") }
}
