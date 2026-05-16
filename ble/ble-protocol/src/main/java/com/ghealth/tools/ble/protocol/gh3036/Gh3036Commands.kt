package com.ghealth.tools.ble.protocol.gh3036

const val KEY_EVENT = "Event"
const val KEY_F = "F"
const val KEY_FW = "FW"
const val KEY_F_GET_MODE = "F_GetMode"
const val KEY_F_SET_MODE = "F_SetMode"
const val KEY_G = "G"
const val KEY_GH3X_CHIP_CTRL = "GH3X_ChipCtrl"
const val KEY_GH3X_GET_VERSION = "GH3X_GetVersion"
const val KEY_GH3X_REG_BIT_FIELD_WRITE_CMD = "GH3X_RegBitFieldWriteCmd"
const val KEY_GH3X_REGS_BIT_FIELD_WRITE_CMD = "GH3X_RegsBitFieldWriteCmd"
const val KEY_GH3X_REGS_LIST_WRITE_CMD = "GH3X_RegsListWriteCmd"
const val KEY_GH3X_REGS_READ_CMD = "GH3X_RegsReadCmd"
const val KEY_GH3X_REGS_WRITE_CMD = "GH3X_RegsWriteCmd"
const val KEY_GH3X_SW_FUNCTION_CMD = "GH3X_SwFunctionCmd"
const val KEY_GH_SET_WORK_MODE_CMD = "GHSetWorkModeCmd"
const val KEY_DOWNLOAD_CONFIG = "download_config"
const val KEY_GET_CHIP_LINK_STATUS = "get_chip_link_status"
const val KEY_GH_LOW_POWER_CMD = "gh_low_power_cmd"
const val KEY_GH_TIME_SET = "gh_time_set"
const val KEY_GH_TIMESTAMP_SET = "gh_timestamp_set"

const val FMT_EVENT = "<u8*>"
const val FMT_F = "<u8*><u32>"
const val FMT_FW = "<u8*>"
const val FMT_F_GET_MODE = "<u8>"
const val FMT_F_SET_MODE = "<u8>"
const val FMT_G = "<u8*>"
const val FMT_GH3X_CHIP_CTRL = "<u8>"
const val FMT_GH3X_GET_VERSION = "<u8>"
const val FMT_GH3X_REG_BIT_FIELD_WRITE_CMD = "<u16><u8><u8><u16>"
const val FMT_GH3X_REGS_BIT_FIELD_WRITE_CMD = "<u16*>"
const val FMT_GH3X_REGS_LIST_WRITE_CMD = "<u16*>"
const val FMT_GH3X_REGS_READ_CMD = "<u16><d32>"
const val FMT_GH3X_REGS_WRITE_CMD = "<u16*>"
const val FMT_GH3X_SW_FUNCTION_CMD = "<u32><u8>"
const val FMT_GH_SET_WORK_MODE_CMD = "<u8>"
const val FMT_DOWNLOAD_CONFIG = "<u8>"
const val FMT_GET_CHIP_LINK_STATUS = "<u8>"
const val FMT_GH_LOW_POWER_CMD = "<u32><u8>"
const val FMT_GH_TIME_SET = "<u32><d8>"
const val FMT_GH_TIMESTAMP_SET = "<u32>"

const val RET_GH3X_GET_VERSION = "<u8*>"
const val RET_GH3X_REGS_READ_CMD = "<u16*>"
const val RET_FW = "<u8*>"
const val RET_GET_CHIP_LINK_STATUS = "<d8*>"
const val RET_F_GET_MODE = "<u16*>"

sealed class Command {
    abstract val key: String
    abstract val format: String

    data class Event(val buf: ByteArray) : Command() {
        override val key = KEY_EVENT
        override val format = FMT_EVENT
    }

    data class F(val buf: ByteArray, val fifoId: Int) : Command() {
        override val key = KEY_F
        override val format = FMT_F
    }

    data class Fw(val src: ByteArray) : Command() {
        override val key = KEY_FW
        override val format = FMT_FW
    }

    data class FGetMode(val testMode: Byte) : Command() {
        override val key = KEY_F_GET_MODE
        override val format = FMT_F_GET_MODE
    }

    data class FSetMode(val testMode: Byte) : Command() {
        override val key = KEY_F_SET_MODE
        override val format = FMT_F_SET_MODE
    }

    data class G(val buf: ByteArray) : Command() {
        override val key = KEY_G
        override val format = FMT_G
    }

    data class Gh3xChipCtrl(val ctrlType: Byte) : Command() {
        override val key = KEY_GH3X_CHIP_CTRL
        override val format = FMT_GH3X_CHIP_CTRL
    }

    data class Gh3xGetVersion(val verType: Byte) : Command() {
        override val key = KEY_GH3X_GET_VERSION
        override val format = FMT_GH3X_GET_VERSION
    }

    data class Gh3xRegBitFieldWriteCmd(
        val regAddr: Int,
        val lsb: Byte,
        val msb: Byte,
        val regVal: Int
    ) : Command() {
        override val key = KEY_GH3X_REG_BIT_FIELD_WRITE_CMD
        override val format = FMT_GH3X_REG_BIT_FIELD_WRITE_CMD
    }

    data class Gh3xRegsBitFieldWriteCmd(val regBits: IntArray) : Command() {
        override val key = KEY_GH3X_REGS_BIT_FIELD_WRITE_CMD
        override val format = FMT_GH3X_REGS_BIT_FIELD_WRITE_CMD
    }

    data class Gh3xRegsListWriteCmd(val regs: IntArray) : Command() {
        override val key = KEY_GH3X_REGS_LIST_WRITE_CMD
        override val format = FMT_GH3X_REGS_LIST_WRITE_CMD
    }

    data class Gh3xRegsReadCmd(val regAddr: Int, val readLen: Int) : Command() {
        override val key = KEY_GH3X_REGS_READ_CMD
        override val format = FMT_GH3X_REGS_READ_CMD
    }

    data class Gh3xRegsWriteCmd(val regs: IntArray) : Command() {
        override val key = KEY_GH3X_REGS_WRITE_CMD
        override val format = FMT_GH3X_REGS_WRITE_CMD
    }

    data class Gh3xSwFunctionCmd(val targetFuncMode: Int, val ctrlType: Byte) : Command() {
        override val key = KEY_GH3X_SW_FUNCTION_CMD
        override val format = FMT_GH3X_SW_FUNCTION_CMD
    }

    data class GhSetWorkModeCmd(val workMode: Byte) : Command() {
        override val key = KEY_GH_SET_WORK_MODE_CMD
        override val format = FMT_GH_SET_WORK_MODE_CMD
    }

    data class DownloadConfig(val stage: Byte) : Command() {
        override val key = KEY_DOWNLOAD_CONFIG
        override val format = FMT_DOWNLOAD_CONFIG
    }

    data class GetChipLinkStatus(val linkType: Byte) : Command() {
        override val key = KEY_GET_CHIP_LINK_STATUS
        override val format = FMT_GET_CHIP_LINK_STATUS
    }

    data class GhLowPowerCmd(val targetFuncMode: Int, val ctrlType: Byte) : Command() {
        override val key = KEY_GH_LOW_POWER_CMD
        override val format = FMT_GH_LOW_POWER_CMD
    }

    data class GhTimeSet(val ts: Int, val hourOffset: Byte) : Command() {
        override val key = KEY_GH_TIME_SET
        override val format = FMT_GH_TIME_SET
    }

    data class GhTimestampSet(val ts: Int) : Command() {
        override val key = KEY_GH_TIMESTAMP_SET
        override val format = FMT_GH_TIMESTAMP_SET
    }
}

sealed class Response {
    data class Gh3xGetVersion(val data: ByteArray) : Response() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Gh3xGetVersion
            return data.contentEquals(other.data)
        }

        override fun hashCode(): Int = data.contentHashCode()
    }

    data class Gh3xRegsReadCmd(val data: IntArray) : Response() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Gh3xRegsReadCmd
            return data.contentEquals(other.data)
        }

        override fun hashCode(): Int = data.contentHashCode()
    }

    data class Fw(val data: ByteArray) : Response() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Fw
            return data.contentEquals(other.data)
        }

        override fun hashCode(): Int = data.contentHashCode()
    }

    data class GetChipLinkStatus(val data: ByteArray) : Response() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as GetChipLinkStatus
            return data.contentEquals(other.data)
        }

        override fun hashCode(): Int = data.contentHashCode()
    }

    data class FGetMode(val data: IntArray) : Response() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as FGetMode
            return data.contentEquals(other.data)
        }

        override fun hashCode(): Int = data.contentHashCode()
    }

    data object Empty : Response()
}
