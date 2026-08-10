package com.ghealth.tools.core.storage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Date

class StoragePathTest {

    private fun path(
        phoneDevice: String = "",
        sdkVersion: String? = null,
        hrVersion: String? = null,
        spo2Version: String? = null,
        nadtVersion: String? = null,
        hrvVersion: String? = null
    ) = StoragePath(
        mode = "HR",
        deviceRole = DeviceRole.MASTER,
        scenario = "RESTING",
        tester = "test",
        chip = "gh3220",
        deviceName = "Dev",
        deviceAddress = "AA:BB",
        phoneDevice = phoneDevice,
        appVersion = "0.6.33",
        sdkVersion = sdkVersion,
        hrVersion = hrVersion,
        spo2Version = spo2Version,
        nadtVersion = nadtVersion,
        hrvVersion = hrvVersion,
        date = Date(0)
    )

    @Test
    fun `版本字段缺失时 infoJson 输出 null 而非默认版本`() {
        val json = path().infoJson()
        assertTrue(json.contains("\"SDK-Version\":null"))
        assertTrue(json.contains("\"HR-Version\":null"))
        assertTrue(json.contains("\"SPO2-Version\":null"))
        assertTrue(json.contains("\"NADT-Version\":null"))
        assertTrue(json.contains("\"HRV-Version\":null"))
    }

    @Test
    fun `版本字段有值时 infoJson 输出带引号的字符串`() {
        val json = path(sdkVersion = "V1.2.3", hrVersion = "algorithm GH3036 RPC hr V1.0.0").infoJson()
        assertTrue(json.contains("\"SDK-Version\":\"V1.2.3\""))
        assertTrue(json.contains("\"HR-Version\":\"algorithm GH3036 RPC hr V1.0.0\""))
    }

    @Test
    fun `手机型号填入 infoJson 的 iPhone-device 字段`() {
        val json = path(phoneDevice = "Xiaomi 23049RAD8C").infoJson()
        assertTrue(json.contains("\"iPhone-device\":\"Xiaomi 23049RAD8C\""))
    }

    @Test
    fun `infoJson 保持其余元数据字段`() {
        val json = path().infoJson()
        assertTrue(json.contains("\"chip\":\"gh3220\""))
        assertTrue(json.contains("\"mode\":\"HR\""))
        assertTrue(json.contains("\"device_role\":\"MASTER\""))
        assertEquals("HR", path().mode)
    }
}
