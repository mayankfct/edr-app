package com.forensiccybertech.eagleye.network

import com.forensiccybertech.eagleye.BuildConfig
import com.forensiccybertech.eagleye.data.EncryptedCookieJar
import com.forensiccybertech.eagleye.data.SessionManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

val AUTH_ORIGIN = "https://${BuildConfig.AUTH_HOST}"

/** The only POSTs the app may send: sign-in, silent token refresh, sign-out (all on the auth host). */
private val AUTH_POST_PATHS = setOf("/api/auth/login", "/api/refresh", "/api/logout")

/**
 * Enforces the read-only release: GET/HEAD are allowed, and POST only to the three auth endpoints above.
 * Anything else is rejected locally with a synthetic 405 and never leaves the device.
 */
class ReadOnlyGuard(private val authHost: String = BuildConfig.AUTH_HOST) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val ok = req.method == "GET" || req.method == "HEAD" ||
            (req.method == "POST" && req.url.host == authHost && req.url.encodedPath in AUTH_POST_PATHS)
        if (ok) return chain.proceed(req)
        return Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(405)
            .message("Blocked: client is read-only")
            .body("".toResponseBody("text/plain".toMediaType())).build()
    }
}

fun authPost(path: String, jsonBody: String? = null): Request = Request.Builder()
    .url("$AUTH_ORIGIN$path")
    .header("Origin", AUTH_ORIGIN)     // the auth server rejects requests without a same-origin Origin header
    .post((jsonBody ?: "").toRequestBody("application/json".toMediaType()))
    .build()

/**
 * Handles expiry for API calls to the app host:
 *  - 401 {"action":"refresh_required"} -> POST /api/refresh once, then retry the call (same flow as the web console)
 *  - any other 401, or a redirect to login -> session is gone: wipe cookies, go to the login screen
 */
class SessionInterceptor(private val jar: EncryptedCookieJar, private val session: SessionManager) : Interceptor {
    private val lock = Any()
    private val json = Json { isLenient = true }

    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val resp = chain.proceed(req)
        if (req.url.host != BuildConfig.APP_HOST || !jar.hasSession()) return resp
        if (resp.isRedirect) { resp.close(); session.expire(); return resp }
        if (resp.code != 401) return resp

        val action = runCatching {
            (json.parseToJsonElement(resp.peekBody(8192).string()) as JsonObject)["action"]?.jsonPrimitive?.content
        }.getOrNull()
        if (action != "refresh_required" || req.tag(Retried::class.java) != null) { session.expire(); return resp }

        val refreshed = synchronized(lock) {
            runCatching { chain.proceed(authPost("/api/refresh")).use { it.isSuccessful } }.getOrDefault(false)
        }
        if (!refreshed) { session.expire(); return resp }
        resp.close()
        return chain.proceed(req.newBuilder().tag(Retried::class.java, Retried).build())
    }

    private object Retried
}

class ApiClient(jar: EncryptedCookieJar, session: SessionManager) {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false)
        .cookieJar(jar)
        .addInterceptor(ReadOnlyGuard())
        .addInterceptor(SessionInterceptor(jar, session))
        .apply {
            if (BuildConfig.DEBUG) addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
                redactHeader("Cookie"); redactHeader("Set-Cookie")
            })
        }
        .build()

    private val retrofit: Retrofit = Retrofit.Builder().baseUrl(BuildConfig.BASE_URL).client(http).build()

    val alerts: AlertsService = retrofit.create(AlertsService::class.java)
    val stats: StatsService = retrofit.create(StatsService::class.java)
    val hosts: HostsService = retrofit.create(HostsService::class.java)
    val vulns: VulnsService = retrofit.create(VulnsService::class.java)
}
