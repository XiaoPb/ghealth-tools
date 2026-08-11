package com.ghealth.tools.core.storage

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DeviceRole {
    MASTER, SLAVE, COMPARE
}

enum class UploadStatus {
    UPLOADED, EXTRA
}

data class StoragePath(
    val mode: String,
    val deviceRole: DeviceRole = DeviceRole.MASTER,
    val uploadStatus: UploadStatus = UploadStatus.EXTRA,
    val scenario: String = "default",
    val tester: String = "unknown",
    val chip: String = "gh3036",
    val deviceName: String = "",
    val deviceAddress: String = "",
    val phoneDevice: String = "",
    val appVersion: String = "1.0.0",
    val sdkVersion: String? = null,
    val hrVersion: String? = null,
    val spo2Version: String? = null,
    val nadtVersion: String? = null,
    val hrvVersion: String? = null,
    val projectName: String = "",
    val projectId: Int = 0,
    val username: String = "",
    val compareDeviceNames: List<String> = emptyList(),
    val date: Date = Date()
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US)

    private fun rolePrefix(): String = when (deviceRole) {
        DeviceRole.MASTER -> "master"
        DeviceRole.SLAVE -> "slave"
        DeviceRole.COMPARE -> "compare"
    }

    private fun statusPrefix(): String = when (uploadStatus) {
        UploadStatus.UPLOADED -> "uploaded"
        UploadStatus.EXTRA -> "extra"
    }

    fun serverPath(): String {
        val dateStr = dateFormat.format(date)
        val role = rolePrefix()
        val status = statusPrefix()
        return "server/$mode/${status}_${role}_${scenario}_${tester}_${chip}_${mode}_$dateStr.csv"
    }

    fun recordsPath(): String {
        val dateStr = dateFormat.format(date)
        val role = rolePrefix()
        val status = statusPrefix()
        return "records/$mode/${status}_${role}_${scenario}_${tester}_${chip}_${mode}_records_$dateStr.csv"
    }

    fun recordsPathForMode(): String {
        val dateStr = dateFormat.format(date)
        val status = statusPrefix()
        return "records/$mode/${status}_records_${mode}_$dateStr.csv"
    }

    private fun jsonStringOrNull(value: String?): String =
        if (value == null) "null" else "\"$value\""

    fun infoJson(): String {
        return buildString {
            append("{\"MAC\":\"$deviceAddress\",")
            append("\"NAME\":\"$deviceName\",")
            append("\"App-version\":\"$appVersion\",")
            append("\"username\":\"$username\",")
            append("\"name\":\"$tester\",")
            append("\"scenario\":\"$scenario\",")
            append("\"chip\":\"$chip\",")
            append("\"iPhone-device\":\"$phoneDevice\",")
            append("\"SDK-Version\":${jsonStringOrNull(sdkVersion)},")
            append("\"HR-Version\":${jsonStringOrNull(hrVersion)},")
            append("\"SPO2-Version\":${jsonStringOrNull(spo2Version)},")
            append("\"NADT-Version\":${jsonStringOrNull(nadtVersion)},")
            append("\"HRV-Version\":${jsonStringOrNull(hrvVersion)},")
            append("\"project_name\":\"$projectName\",")
            append("\"project_id\":$projectId,")
            append("\"tester\":\"$tester\",")
            append("\"device_role\":\"${deviceRole.name}\",")
            val refResultMapping = compareDeviceNames.mapIndexedNotNull { index, name ->
                if (name.isNotBlank()) "\"REF_RESULT$index\":\"$name\"" else null
            }
            if (refResultMapping.isNotEmpty()) {
                append("\"ref_result_devices\":{${refResultMapping.joinToString(",")}},")
            }
            append("\"mode\":\"$mode\"}")
        }
    }
}
