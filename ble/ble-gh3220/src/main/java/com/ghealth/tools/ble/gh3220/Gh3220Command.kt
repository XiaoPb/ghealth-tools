package com.ghealth.tools.ble.gh3220

/** GH3220 命令 ID（协议文档 §2 完整列表）。 */
object Gh3220Cmd {
    const val NOP = 0x00
    const val ACK = 0x01
    const val GET_STATUS = 0x02
    const val REG_RW = 0x03
    const val IMPEDANCE = 0x04          // 略：raw passthrough
    const val PACKAGE_TEST = 0x05
    const val READ_OTP = 0x07           // 无格式说明：raw passthrough
    const val RAWDATA = 0x08
    const val RAWDATA_ZIP_EVEN = 0x09
    const val RAWDATA_ZIP_ODD = 0x0A
    const val RAWDATA_NEW = 0x0B
    const val START_CTRL = 0x0C
    const val CURRENT_BATTERY = 0x0D
    const val ECG_VOLTAGE = 0x0E        // 略：raw passthrough
    const val FW_UPGRADE = 0x0F
    const val WORK_MODE = 0x10
    const val GSENSOR_SET = 0x11
    const val FIFO_THR = 0x12
    const val EVENT_SET = 0x13
    const val DEVICE_EVENT = 0x14
    const val FUNC_MAP = 0x15
    const val CHIP_EVENT_REPORT = 0x16
    const val CHIP_CTRL = 0x17
    const val CURRENT_CALIBRATE = 0x18
    const val GET_VER = 0x19
    const val CONN_STATUS = 0x1A
    const val SAMPLE_RATE = 0x1B
    const val SLOT_EN = 0x1C
    const val ECG_CTRL = 0x1D
    const val WORK_MODE_SET = 0x1E
    const val DRV_CFG = 0x1F
    const val APP_MODULE = 0x20
    const val SLAVE_LOG = 0x21
    const val LEAD_DET_FREQ = 0x22      // 略：raw passthrough
    const val DUMP_MODE = 0x23          // 略：raw passthrough
    const val SW_AGC = 0x24             // 略：raw passthrough
    const val SAMPLING_STATUS = 0x25    // 略：raw passthrough
    const val RTC_TIME = 0x26           // 略：raw passthrough
    const val ECG_PATCH_TIME = 0x28     // 心电贴在线/离线时间（文档无格式：raw passthrough）
    const val RAWDATA_FIFO = 0x2A
    const val SPI_FLASH_TEST = 0x2D     // 略：raw passthrough
    const val SWITCH_CHIP = 0x2E
    const val REG_ARRAY_WRITE = 0xA1
    const val DEBUG_STATUS = 0xA2       // 无格式说明：raw passthrough
}
