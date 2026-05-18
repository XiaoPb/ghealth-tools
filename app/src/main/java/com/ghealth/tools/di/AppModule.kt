package com.ghealth.tools.di

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
}
