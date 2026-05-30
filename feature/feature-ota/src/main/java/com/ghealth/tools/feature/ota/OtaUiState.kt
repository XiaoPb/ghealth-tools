package com.ghealth.tools.feature.ota

import com.ghealth.tools.feature.ota.engine.FirmwareInfo
import com.ghealth.tools.feature.ota.engine.OtaState
import com.ghealth.tools.feature.ota.model.DebugMenuAction
import com.ghealth.tools.feature.ota.model.OtaConfig
import com.ghealth.tools.feature.ota.model.StorageType
import com.ghealth.tools.feature.ota.model.UpgradeRegion

data class ConnectedDeviceInfo(
    val address: String,
    val name: String,
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

    val firmwareInfo: FirmwareInfo? = null,
    val isReadingFirmwareInfo: Boolean = false,

    val firmwareFile: FirmwareFileInfo = FirmwareFileInfo(),
    val upgradeRegion: UpgradeRegion = UpgradeRegion.SINGLE,

    val resourceFile: FirmwareFileInfo = FirmwareFileInfo(),
    val resourceStartAddress: Long = 0,
    val resourceStorageType: StorageType = StorageType.INTERNAL,

    val otaConfig: OtaConfig = OtaConfig(),

    val activeDebugActions: List<DebugMenuAction> = emptyList(),
    val showControlPointDialog: Boolean = false,
    val controlPointHex: String = "",

    val debugResults: Map<DebugMenuAction, String> = emptyMap(),

    val otaState: OtaState = OtaState.IDLE,
    val progressPercent: Float = 0f,
    val logLines: List<String> = emptyList(),
    val isUpgrading: Boolean = false,

    val errorMessage: String? = null,
    val successMessage: String? = null,
    val showResultDialog: Boolean = false,
)