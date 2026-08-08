package com.ghealth.tools.core.storage

import timber.log.Timber

class FileLoggingTree(private val logManager: LogManager) : Timber.Tree() {

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        logManager.logApp(priority, tag ?: "App", message)
        if (t != null) {
            logManager.logApp(priority, tag ?: "App", t.stackTraceToString())
        }
    }
}
