package com.ghealth.tools.core.storage

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class StoragePath(
    val mode: String,
    val status: String = "extra",
    val scenario: String = "default",
    val tester: String = "unknown",
    val chip: String,
    val date: Date = Date()
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US)

    fun serverPath(): String {
        val dateStr = dateFormat.format(date)
        return "server/$mode/${status}_${scenario}_${tester}_${chip}_${mode}_$dateStr.csv"
    }

    fun recordsPath(): String {
        val dateStr = dateFormat.format(date)
        return "records/$mode/${status}_${scenario}_${tester}_${chip}_${mode}_records_$dateStr.csv"
    }
}
