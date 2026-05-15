package com.ghealth.tools.core.model

data class BleDevice(
    val name: String?,
    val address: String,
    val rssi: Int,
    val deviceType: DeviceType? = null,
)
