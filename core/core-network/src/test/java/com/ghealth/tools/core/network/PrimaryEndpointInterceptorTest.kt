package com.ghealth.tools.core.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class PrimaryEndpointInterceptorTest {

    private lateinit var primary: MockWebServer
    private lateinit var fallback: MockWebServer

    @BeforeEach
    fun setUp() {
        primary = MockWebServer()
        fallback = MockWebServer()
        primary.start()
        fallback.start()
    }

    @AfterEach
    fun tearDown() {
        primary.shutdown()
        fallback.shutdown()
    }

    private fun newClient(preference: EndpointPreference = FakeEndpointPreference()): OkHttpClient {
        val interceptor = PrimaryEndpointInterceptor(
            primaryBaseUrl = primary.url("/api/"),
            fallbackBaseUrl = fallback.url("/api/"),
            endpointPreference = preference
        )
        return OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()
    }

    @Test
    fun `requests targeting fallback host are rewritten to primary host`() {
        primary.enqueue(MockResponse().setResponseCode(200).setBody("primary-ok"))
        fallback.enqueue(MockResponse().setResponseCode(200).setBody("fallback-ok"))

        val response = newClient().newCall(
            Request.Builder().url(fallback.url("/api/projects/")).build()
        ).execute()

        assertEquals("primary-ok", response.body?.string())
        assertEquals(1, primary.requestCount)
        assertEquals(0, fallback.requestCount)
        assertEquals("/api/projects/", primary.takeRequest().path)
    }

    @Test
    fun `falls back to original URL when primary request fails with IOException`() {
        primary.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        fallback.enqueue(MockResponse().setResponseCode(200).setBody("fallback-ok"))

        val response = newClient().newCall(
            Request.Builder().url(fallback.url("/api/projects/")).build()
        ).execute()

        assertEquals("fallback-ok", response.body?.string())
        assertEquals(1, primary.requestCount)
        assertEquals(1, fallback.requestCount)
        assertEquals("/api/projects/", fallback.takeRequest().path)
    }

    @Test
    fun `login requests pass through to fallback without rewrite`() {
        fallback.enqueue(MockResponse().setResponseCode(200).setBody("login-ok"))

        val response = newClient().newCall(
            Request.Builder().url(fallback.url("/api/login/")).build()
        ).execute()

        assertEquals("login-ok", response.body?.string())
        assertEquals(0, primary.requestCount)
        assertEquals(1, fallback.requestCount)
    }

    @Test
    fun `requests to non-fallback hosts pass through unchanged`() {
        val other = MockWebServer()
        other.start()
        try {
            other.enqueue(MockResponse().setResponseCode(200).setBody("other-ok"))

            val response = newClient().newCall(
                Request.Builder().url(other.url("/v1/ping")).build()
            ).execute()

            assertEquals("other-ok", response.body?.string())
            assertEquals(0, primary.requestCount)
            assertEquals(0, fallback.requestCount)
        } finally {
            other.shutdown()
        }
    }

    @Test
    fun `does not fall back when primary returns an HTTP error`() {
        primary.enqueue(MockResponse().setResponseCode(500))
        fallback.enqueue(MockResponse().setResponseCode(200).setBody("fallback-ok"))

        val response = newClient().newCall(
            Request.Builder().url(fallback.url("/api/projects/")).build()
        ).execute()

        assertEquals(500, response.code)
        assertEquals(1, primary.requestCount)
        assertEquals(0, fallback.requestCount)
    }

    @Test
    fun `propagates IOException when primary and fallback both fail`() {
        primary.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        fallback.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        val call = newClient().newCall(
            Request.Builder().url(fallback.url("/api/projects/")).build()
        )

        org.junit.jupiter.api.Assertions.assertThrows(java.io.IOException::class.java) {
            call.execute().close()
        }
    }

    @Test
    fun `uses primary directly when preference is primary without probing fallback`() {
        primary.enqueue(MockResponse().setResponseCode(200).setBody("primary-ok"))

        val response = newClient(FakeEndpointPreference(value = true)).newCall(
            Request.Builder().url(fallback.url("/api/projects/")).build()
        ).execute()

        assertEquals("primary-ok", response.body?.string())
        assertEquals(1, primary.requestCount)
        assertEquals(0, fallback.requestCount)
    }

    @Test
    fun `does not fall back to fallback when preference is primary and primary fails`() {
        primary.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        fallback.enqueue(MockResponse().setResponseCode(200).setBody("fallback-ok"))

        val call = newClient(FakeEndpointPreference(value = true)).newCall(
            Request.Builder().url(fallback.url("/api/projects/")).build()
        )

        org.junit.jupiter.api.Assertions.assertThrows(java.io.IOException::class.java) {
            call.execute().close()
        }
        assertEquals(0, fallback.requestCount)
    }

    @Test
    fun `uses fallback directly when preference is fallback without probing primary`() {
        fallback.enqueue(MockResponse().setResponseCode(200).setBody("fallback-ok"))

        val response = newClient(FakeEndpointPreference(value = false)).newCall(
            Request.Builder().url(fallback.url("/api/projects/")).build()
        ).execute()

        assertEquals("fallback-ok", response.body?.string())
        assertEquals(0, primary.requestCount)
        assertEquals(1, fallback.requestCount)
    }

    private class FakeEndpointPreference(
        @Volatile var value: Boolean? = null
    ) : EndpointPreference {
        override fun usePrimary(): Boolean? = value
        override suspend fun setUsePrimary(usePrimary: Boolean) {
            value = usePrimary
        }
    }
}
