package com.ghealth.tools

import android.app.Application
import com.ghealth.tools.core.storage.FileLoggingTree
import com.ghealth.tools.core.storage.LogManager
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class GHealthApp : Application() {

    @Inject lateinit var logManager: LogManager

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        Timber.plant(FileLoggingTree(logManager))
    }
}
