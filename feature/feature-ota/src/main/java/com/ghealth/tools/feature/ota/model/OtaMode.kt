package com.ghealth.tools.feature.ota.model

enum class UpgradeRegion { SINGLE, DUAL }

enum class StorageType { INTERNAL, EXTERNAL }

data class OtaConfig(
    val fastMode: Boolean = false,
    val upgradeRegion: UpgradeRegion = UpgradeRegion.SINGLE,
    val copyAddress: Long = 0,
    val copyAddressEnabled: Boolean = false,
    val resourceStartAddress: Long = 0,
    val resourceStorageType: StorageType = StorageType.INTERNAL,
)

/** Nordic SMP 升级模式，与 FirmwareUpgradeManager.Mode 一一对应。 */
enum class SmpUpgradeMode(val label: String) {
    TEST_AND_CONFIRM("测试并确认"),
    CONFIRM_ONLY("仅确认"),
    TEST_ONLY("仅测试"),
    NONE("上传后重启"),
}

/**
 * Nordic SMP 升级参数。
 *
 * windowCapacity 默认 1（保守值，不依赖固件 MCUMGR_BUF_COUNT）；
 * memoryAlignment 默认 1（字节对齐）。
 */
data class SmpUpgradeSettings(
    val estimatedSwapTimeMs: Int = 0,
    val windowCapacity: Int = 1,
    val memoryAlignment: Int = 1,
)

enum class DebugMenuAction(val label: String, val description: String) {
    RAM_READ_WRITE("读写RAM", "读取或写入设备RAM指定地址"),
    FLASH_READ_WRITE("读写Flash", "读取或写入设备Flash指定地址"),
    REGISTER_READ_WRITE("读写寄存器", "读取或写入设备寄存器"),
    READ_EFUSE("读取eFuse", "读取设备eFuse数据"),
    NVDS_READ_WRITE("读写NVDS", "读取或写入NVDS区域"),
    READ_BOOT_INFO("读取BootInfo", "读取设备启动信息"),
}