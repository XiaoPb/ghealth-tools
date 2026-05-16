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

    fun infoJson(): String {
        return buildString {
            append("{")
            append("\"mode\":\"$mode\",")
            append("\"role\":\"${rolePrefix()}\",")
            append("\"status\":\"${statusPrefix()}\",")
            append("\"scenario\":\"$scenario\",")
            append("\"tester\":\"$tester\",")
            append("\"chip\":\"$chip\",")
            append("\"deviceName\":\"$deviceName\",")
            append("\"deviceAddress\":\"$deviceAddress\",")
            append("\"date\":\"${dateFormat.format(date)}\"")
            append("}")
        }
    }
}
