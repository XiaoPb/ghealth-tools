package com.ghealth.tools

import android.app.Application
import com.ghealth.tools.core.storage.DefaultConfigInstaller
import com.ghealth.tools.core.storage.FileLoggingTree
import com.ghealth.tools.core.storage.LogManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class GHealthApp : Application() {

    @Inject lateinit var logManager: LogManager
    @Inject lateinit var defaultConfigInstaller: DefaultConfigInstaller

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        Timber.plant(FileLoggingTree(logManager))
        appScope.launch {
            defaultConfigInstaller.install()
        }
    }
}
