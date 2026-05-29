package com.ghealth.tools.feature.ota.model

enum class UpgradeRegion { SINGLE, DUAL }

enum class StorageType { INTERNAL, EXTERNAL }

data class OtaConfig(
    val fastMode: Boolean = false,
    val upgradeRegion: UpgradeRegion = UpgradeRegion.SINGLE,
    val copyAddress: Long = 0,
    val resourceStartAddress: Long = 0,
    val resourceStorageType: StorageType = StorageType.INTERNAL,
)

enum class DebugMenuAction(val label: String, val description: String) {
    RAM_READ_WRITE("读写RAM", "读取或写入设备RAM指定地址"),
    FLASH_READ_WRITE("读写Flash", "读取或写入设备Flash指定地址"),
    REGISTER_READ_WRITE("读写寄存器", "读取或写入设备寄存器"),
    READ_EFUSE("读取eFuse", "读取设备eFuse数据"),
    NVDS_READ_WRITE("读写NVDS", "读取或写入NVDS区域"),
    READ_BOOT_INFO("读取BootInfo", "读取设备启动信息"),
}