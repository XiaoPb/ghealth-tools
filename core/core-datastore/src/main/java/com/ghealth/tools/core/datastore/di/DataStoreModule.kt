package com.ghealth.tools.core.datastore.di

import android.content.Context
import com.ghealth.tools.core.datastore.BlePreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    fun provideBlePreferences(@ApplicationContext context: Context): BlePreferences =
        BlePreferences(context)
}
