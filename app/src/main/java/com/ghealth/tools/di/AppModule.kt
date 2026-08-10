package com.ghealth.tools.di

import android.os.Build
import com.ghealth.tools.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Named("app_version")
    fun provideVersionName(): String = BuildConfig.VERSION_NAME

    @Provides
    @Named("phone_device")
    fun providePhoneDevice(): String {
        val manufacturer = Build.MANUFACTURER.takeIf { it.isNotBlank() }
        val model = Build.MODEL.takeIf { it.isNotBlank() }
        return listOfNotNull(manufacturer, model).joinToString(" ")
    }
}
