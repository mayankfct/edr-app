package com.forensiccybertech.eagleye.data

import com.forensiccybertech.eagleye.BuildConfig
import com.forensiccybertech.eagleye.network.authPost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import java.io.IOException

sealed interface LoginResult {
    data object Success : LoginResult
    data class Failure(val message: String) : LoginResult
}

/** Native sign-in against the central auth server (same API the web login page uses). No browser involved. */
class AuthRepository(private val http: OkHttpClient, private val session: SessionManager, private val jar: EncryptedCookieJar) {
    private val json = Json { isLenient = true }

    /** [identifier] is the email address or username. */
    suspend fun login(identifier: String, password: String): LoginResult = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("username", identifier.trim())
            put("password", password)
            put("returnTo", "https://${BuildConfig.APP_HOST}/")
        }.toString()
        try {
            http.newCall(authPost("/api/auth/login", body)).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                val obj = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
                when {
                    resp.isSuccessful && obj?.get("forced_change")?.let { (it as? JsonPrimitive)?.booleanOrNull } == true -> {
                        jar.clear()
                        LoginResult.Failure("You must change your password first. Please contact your administrator.")
                    }
                    resp.isSuccessful && jar.hasSession() -> { session.loggedIn(); LoginResult.Success }
                    resp.isSuccessful -> LoginResult.Failure("Sign-in did not return a session. Please try again.")
                    resp.code == 401 -> LoginResult.Failure(serverError(obj) ?: "Invalid username or password")
                    resp.code == 429 -> LoginResult.Failure("Too many attempts. Please wait and try again.")
                    else -> LoginResult.Failure(serverError(obj) ?: "Sign-in failed (${resp.code}).")
                }
            }
        } catch (e: IOException) {
            LoginResult.Failure("Network error. Check your connection and try again.")
        }
    }

    /** Best-effort server-side logout, then always wipe local state. */
    suspend fun logout() {
        withContext(Dispatchers.IO) {
            runCatching { http.newCall(authPost("/api/logout")).execute().close() }
        }
        session.logout()
    }

    private fun serverError(o: JsonObject?) = (o?.get("error") as? JsonPrimitive)?.contentOrNull
}
