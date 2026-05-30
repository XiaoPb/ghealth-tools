package com.ghealth.tools.feature.ota.engine

import com.goodix.ble.gr.lib.com.ILogger
import timber.log.Timber

class TimberLogger : ILogger {

    override fun v(tag: String, msg: String) {
        Timber.tag(tag).v(msg)
    }

    override fun d(tag: String, msg: String) {
        Timber.tag(tag).d(msg)
    }

    override fun i(tag: String, msg: String) {
        Timber.tag(tag).i(msg)
    }

    override fun w(tag: String, msg: String) {
        Timber.tag(tag).w(msg)
    }

    override fun e(tag: String, msg: String) {
        Timber.tag(tag).e(msg)
    }

    override fun logRaw(timestamp: Long, level: Int, tag: String, msg: String) {
        Timber.tag(tag).log(level, msg)
    }
}