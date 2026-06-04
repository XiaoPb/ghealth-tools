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
    val sdkVersion: String = "1.0.0",
    val hrVersion: String = "1.0.0",
    val spo2Version: String = "1.0.0",
    val nadtVersion: String = "1.0.0",
    val hrvVersion: String = "1.0.0",
    val projectName: String = "",
    val projectId: Int = 0,
    val username: String = "",
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
            append("\"SDK-Version\":\"$sdkVersion\",")
            append("\"HR-Version\":\"$hrVersion\",")
            append("\"SPO2-Version\":\"$spo2Version\",")
            append("\"NADT-Version\":\"$nadtVersion\",")
            append("\"HRV-Version\":\"$hrvVersion\",")
            append("\"project_name\":\"$projectName\",")
            append("\"project_id\":$projectId,")
            append("\"tester\":\"$tester\",")
            append("\"device_role\":\"${deviceRole.name}\",")
            append("\"mode\":\"$mode\"}")
        }
    }
}
