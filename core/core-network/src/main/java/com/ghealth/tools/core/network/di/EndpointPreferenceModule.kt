package com.ghealth.tools.core.network.di

import com.ghealth.tools.core.network.EndpointPreference
import com.ghealth.tools.core.network.EndpointPreferenceStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class EndpointPreferenceModule {

    @Binds
    @Singleton
    abstract fun bindEndpointPreference(
        store: EndpointPreferenceStore
    ): EndpointPreference
}
