package com.ghealth.tools.core.storage

import timber.log.Timber

class FileLoggingTree(private val logManager: LogManager) : Timber.Tree() {

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val level = when (priority) {
            android.util.Log.VERBOSE -> "V"
            android.util.Log.DEBUG -> "D"
            android.util.Log.INFO -> "I"
            android.util.Log.WARN -> "W"
            android.util.Log.ERROR -> "E"
            else -> "?"
        }
        logManager.logApp(level, tag ?: "App", message)
        if (t != null) {
            logManager.logApp(level, tag ?: "App", t.stackTraceToString())
        }
    }
}
