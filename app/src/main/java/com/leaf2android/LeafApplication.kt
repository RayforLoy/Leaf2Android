package com.leaf2android

import android.app.Application
import com.leaf2android.localization.L10n
import com.leaf2android.connection.DeviceDiscovery
import com.leaf2android.data.LeafRepository

class LeafApplication : Application() {
    lateinit var repository: LeafRepository; private set
    lateinit var discovery: DeviceDiscovery; private set
    override fun onCreate() {
        super.onCreate()
        L10n.initialize(this)
        repository = LeafRepository(this)
        discovery = DeviceDiscovery(this)
    }
}
