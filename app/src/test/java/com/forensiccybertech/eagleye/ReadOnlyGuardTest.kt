package com.forensiccybertech.eagleye

import com.forensiccybertech.eagleye.network.ReadOnlyGuard
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadOnlyGuardTest {
    private val body = "{}".toRequestBody("application/json".toMediaType())

    private fun code(client: OkHttpClient, method: String, path: String, server: MockWebServer): Int =
        client.newCall(Request.Builder().url(server.url(path)).method(method, if (method == "GET") null else body).build())
            .execute().use { it.code }

    @Test fun onlyGetAndAuthPostsAreAllowed() {
        MockWebServer().use { s ->
            // Treat the mock server's host as the auth host.
            val client = OkHttpClient.Builder().addInterceptor(ReadOnlyGuard(s.hostName)).build()
            repeat(4) { s.enqueue(MockResponse().setResponseCode(200)) }
            assertEquals(200, code(client, "GET", "/api/v1/alerts", s))
            assertEquals(200, code(client, "POST", "/api/auth/login", s))
            assertEquals(200, code(client, "POST", "/api/refresh", s))
            assertEquals(200, code(client, "POST", "/api/logout", s))
            for (m in listOf("POST", "PUT", "DELETE", "PATCH")) assertEquals(m, 405, code(client, m, "/api/v1/agents/1/isolate", s))
            assertEquals(4, s.requestCount) // blocked calls never hit the network
        }
    }

    @Test fun authPostsToOtherHostsAreBlocked() {
        MockWebServer().use { s ->
            val client = OkHttpClient.Builder().addInterceptor(ReadOnlyGuard("auth.invalid")).build()
            assertEquals(405, code(client, "POST", "/api/auth/login", s))
            assertEquals(0, s.requestCount)
        }
    }
}
