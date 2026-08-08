package com.ghealth.tools.core.network.di

import android.content.Context
import com.ghealth.tools.core.network.AuthAuthenticator
import com.ghealth.tools.core.network.AuthInterceptor
import com.ghealth.tools.core.network.EndpointPreference
import com.ghealth.tools.core.network.PrimaryEndpointInterceptor
import com.ghealth.tools.core.network.RetryInterceptor
import com.ghealth.tools.core.network.TokenManager
import com.ghealth.tools.core.network.api.AuthApi
import com.ghealth.tools.core.network.api.DownloadApi
import com.ghealth.tools.core.network.api.GitHubApi
import com.ghealth.tools.core.network.api.ProjectApi
import com.ghealth.tools.core.network.api.UploadApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import timber.log.Timber
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val DEFAULT_BASE_URL = "https://api.xiaopb.cn/api/"
    private const val PRIMARY_AUTH_BASE_URL = "https://api.health.xiaopb.cn:8861/api/"
    private const val GITHUB_BASE_URL = "https://api.github.com/"
    private const val CONNECT_TIMEOUT = 3L
    private const val READ_TIMEOUT = 3L
    private const val WRITE_TIMEOUT = 30L
    private const val GITHUB_CONNECT_TIMEOUT = 15L
    private const val GITHUB_READ_TIMEOUT = 15L
    private const val PRIMARY_AUTH_TIMEOUT = 3L

    @Provides
    @Singleton
    @Named("baseUrl")
    fun provideBaseUrl(): String {
        return DEFAULT_BASE_URL
    }

    @Provides
    @Singleton
    @Named("primaryBaseUrl")
    fun providePrimaryBaseUrl(): String {
        return PRIMARY_AUTH_BASE_URL
    }

    @Provides
    @Singleton
    fun providePrimaryEndpointInterceptor(
        @Named("primaryBaseUrl") primaryBaseUrl: String,
        @Named("baseUrl") fallbackBaseUrl: String,
        endpointPreference: EndpointPreference
    ): PrimaryEndpointInterceptor {
        return PrimaryEndpointInterceptor(
            primaryBaseUrl = primaryBaseUrl.toHttpUrl(),
            fallbackBaseUrl = fallbackBaseUrl.toHttpUrl(),
            endpointPreference = endpointPreference
        )
    }

    @Provides
    @Singleton
    fun provideMoshi(): Moshi {
        return Moshi.Builder()
            .add(Unit::class.java, object : JsonAdapter<Unit>() {
                override fun fromJson(reader: JsonReader): Unit {
                    reader.skipValue()
                    return Unit
                }

                override fun toJson(writer: JsonWriter, value: Unit?) {
                    writer.nullValue()
                }
            })
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    @Provides
    @Singleton
    fun provideTokenManager(
        @ApplicationContext context: Context
    ): TokenManager {
        return TokenManager(context)
    }

    @Provides
    @Singleton
    fun provideAuthInterceptor(
        tokenManager: TokenManager
    ): AuthInterceptor {
        return AuthInterceptor(tokenManager)
    }

    @Provides
    @Singleton
    fun provideAuthAuthenticator(
        tokenManager: TokenManager,
        @Named("baseUrl") baseUrl: String,
        primaryEndpointInterceptor: PrimaryEndpointInterceptor
    ): AuthAuthenticator {
        return AuthAuthenticator(tokenManager, baseUrl, primaryEndpointInterceptor)
    }

    @Provides
    @Singleton
    fun provideDns(): Dns {
        return object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                val addresses = InetAddress.getAllByName(hostname)
                val ipv4Count = addresses.count { it is Inet4Address }
                val ipv6Count = addresses.count { it is Inet6Address }
                Timber.tag("DNS").d("$hostname -> IPv4=$ipv4Count IPv6=$ipv6Count, total=${addresses.size}")

                if (addresses.isEmpty()) {
                    Timber.tag("DNS").w("$hostname 解析结果为空")
                } else if (ipv4Count == 0 && ipv6Count > 0) {
                    Timber.tag("DNS").w("$hostname 仅返回 IPv6 地址，无 IPv4 记录，请在 DNS 配置中添加 A 记录")
                }

                return addresses.toList()
            }
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        authAuthenticator: AuthAuthenticator,
        dns: Dns,
        primaryEndpointInterceptor: PrimaryEndpointInterceptor
    ): OkHttpClient {
        val loggingInterceptor = createApiLoggingInterceptor()

        return OkHttpClient.Builder()
            .dns(dns)
            // Retry 在外层、主端点拦截器在内层：每次重试先试 primary（health），
            // primary 抛 IOException 时立即回退到原始 fallback（xiaopb）地址。
            .addInterceptor(RetryInterceptor(maxRetries = 3))
            .addInterceptor(primaryEndpointInterceptor)
            .addInterceptor(authInterceptor)
            .authenticator(authAuthenticator)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
            .build()
    }

    private fun createApiLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor(
            object : HttpLoggingInterceptor.Logger {
                override fun log(message: String) {
                    if (message.length > 512) {
                        Timber.tag("API").d("${message.take(256)}... [${message.length} chars truncated]")
                    } else {
                        Timber.tag("API").d(message)
                    }
                }
            }
        ).apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
    }

    @Provides
    @Singleton
    @Named("primaryAuthOkHttpClient")
    fun providePrimaryAuthOkHttpClient(dns: Dns): OkHttpClient {
        return OkHttpClient.Builder()
            .dns(dns)
            .addInterceptor(createApiLoggingInterceptor())
            .connectTimeout(PRIMARY_AUTH_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(PRIMARY_AUTH_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @Named("primaryAuthRetrofit")
    fun providePrimaryAuthRetrofit(
        @Named("primaryAuthOkHttpClient") okHttpClient: OkHttpClient,
        moshi: Moshi
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(PRIMARY_AUTH_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Provides
    @Singleton
    @Named("primaryAuthApi")
    fun providePrimaryAuthApi(
        @Named("primaryAuthRetrofit") retrofit: Retrofit
    ): AuthApi {
        return retrofit.create(AuthApi::class.java)
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        moshi: Moshi,
        @Named("baseUrl") baseUrl: String
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi {
        return retrofit.create(AuthApi::class.java)
    }

    @Provides
    @Singleton
    fun provideProjectApi(retrofit: Retrofit): ProjectApi {
        return retrofit.create(ProjectApi::class.java)
    }

    @Provides
    @Singleton
    fun provideUploadApi(retrofit: Retrofit): UploadApi {
        return retrofit.create(UploadApi::class.java)
    }

    @Provides
    @Singleton
    fun provideDownloadApi(retrofit: Retrofit): DownloadApi {
        return retrofit.create(DownloadApi::class.java)
    }

    @Provides
    @Singleton
    @Named("githubOkHttpClient")
    fun provideGitHubOkHttpClient(dns: Dns): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor(
            object : HttpLoggingInterceptor.Logger {
                override fun log(message: String) {
                    Timber.tag("GitHubAPI").d(message)
                }
            }
        ).apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        return OkHttpClient.Builder()
            .dns(dns)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(GITHUB_CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(GITHUB_READ_TIMEOUT, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @Named("githubRetrofit")
    fun provideGitHubRetrofit(
        @Named("githubOkHttpClient") okHttpClient: OkHttpClient,
        moshi: Moshi,
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(GITHUB_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Provides
    @Singleton
    fun provideGitHubApi(
        @Named("githubRetrofit") retrofit: Retrofit
    ): GitHubApi {
        return retrofit.create(GitHubApi::class.java)
    }
}