package com.ghealth.tools.feature.ota

import com.ghealth.tools.ble.connection.DeviceRole
import com.ghealth.tools.feature.ota.engine.FirmwareInfo
import com.ghealth.tools.feature.ota.engine.OtaState
import com.ghealth.tools.feature.ota.model.DebugMenuAction
import com.ghealth.tools.feature.ota.model.OtaConfig

data class ConnectedDeviceInfo(
    val address: String,
    val name: String,
    val role: DeviceRole = DeviceRole.MASTER,
)

data class FirmwareFileInfo(
    val uri: String = "",
    val fileName: String = "",
    val fileSize: Long = 0,
    val isValid: Boolean = false,
    val parseError: String? = null,
    val imgInfo: FirmwareInfo? = null,
)

data class OtaUiState(
    val availableDevices: List<ConnectedDeviceInfo> = emptyList(),
    val selectedDevice: ConnectedDeviceInfo? = null,
    val isDfuReady: Boolean = false,

    val firmwareInfo: FirmwareInfo? = null,
    val isReadingFirmwareInfo: Boolean = false,

    val firmwareFile: FirmwareFileInfo = FirmwareFileInfo(),

    val resourceFile: FirmwareFileInfo = FirmwareFileInfo(),

    val otaConfig: OtaConfig = OtaConfig(),

    val activeDebugActions: List<DebugMenuAction> = emptyList(),
    val showControlPointDialog: Boolean = false,
    val controlPointHex: String = "444F4F47",

    val debugResults: Map<DebugMenuAction, String> = emptyMap(),

    val ramAddress: String = "",
    val ramLength: String = "",
    val ramLengthUnit: String = "Byte",
    val ramData: String = "",
    val ramReadData: ByteArray? = null,

    val flashAddress: String = "",
    val flashLength: String = "",
    val flashLengthUnit: String = "Byte",
    val flashData: String = "",
    val flashReadData: ByteArray? = null,

    val registerAddress: String = "",
    val registerData: String = "",

    val nvdsTag: String = "",
    val nvdsData: String = "",

    val efuseResult: String = "",

    val bootInfoData: BootInfoData? = null,

    val showDownloadDialog: Boolean = false,
    val downloadDefaultName: String = "",
    val downloadData: ByteArray? = null,
    val downloadSavePath: String = "",

    val otaState: OtaState = OtaState.IDLE,
    val progressPercent: Float = 0f,
    val logLines: List<String> = emptyList(),
    val isUpgrading: Boolean = false,

    val errorMessage: String? = null,
    val successMessage: String? = null,
    val showResultDialog: Boolean = false,
)

data class BootInfoData(
    val binSize: Int = 0,
    val checksum: Int = 0,
    val loadAddr: Int = 0,
    val runAddr: Int = 0,
    val xqspiXipCmd: Int = 0,
    val xqspiSpeed: Int = 0,
    val codeCopyMode: Int = 0,
    val systemClk: Int = 0,
    val checkImage: Int = 0,
    val bootDelay: Int = 0,
    val isDapBoot: Int = 0,
    val isEncrypted: Boolean = false,
)