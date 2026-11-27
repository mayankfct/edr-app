package com.forensiccybertech.eagleye.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

@Serializable
private data class StoredCookie(
    val name: String, val value: String, val expiresAt: Long, val domain: String, val path: String,
    val secure: Boolean, val httpOnly: Boolean, val hostOnly: Boolean,
)

/**
 * Cookie jar whose contents are persisted encrypted (Android Keystore, via [SecureTokenStore]).
 * The auth server sets the session/refresh cookies on login; OkHttp then attaches them to every matching request.
 */
class EncryptedCookieJar(private val store: SecureTokenStore) : CookieJar {
    private val json = Json { ignoreUnknownKeys = true }
    private val cookies = mutableListOf<Cookie>()

    init {
        store.load()?.let { raw ->
            runCatching { json.decodeFromString<List<StoredCookie>>(raw) }.getOrNull()?.forEach { c ->
                val b = Cookie.Builder().name(c.name).value(c.value).path(c.path)
                if (c.hostOnly) b.hostOnlyDomain(c.domain) else b.domain(c.domain)
                if (c.expiresAt != Long.MAX_VALUE) b.expiresAt(c.expiresAt)
                if (c.secure) b.secure()
                if (c.httpOnly) b.httpOnly()
                cookies += b.build()
            }
        }
        pruneExpired()
    }

    private fun Cookie.sameKey(o: Cookie) = name == o.name && domain == o.domain && path == o.path
    private fun pruneExpired() { cookies.removeAll { it.expiresAt < System.currentTimeMillis() } }

    @Synchronized override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        for (c in cookies) this.cookies.removeAll { it.sameKey(c) }
        this.cookies += cookies.filter { it.expiresAt >= System.currentTimeMillis() } // expired == deletion
        persist()
    }

    @Synchronized override fun loadForRequest(url: HttpUrl): List<Cookie> {
        pruneExpired()
        return cookies.filter { it.matches(url) }
    }

    @Synchronized fun hasSession(): Boolean { pruneExpired(); return cookies.isNotEmpty() }

    @Synchronized fun clear() { cookies.clear(); store.clear() }

    private fun persist() {
        if (cookies.isEmpty()) { store.clear(); return }
        store.save(json.encodeToString(cookies.map {
            StoredCookie(it.name, it.value, if (it.persistent) it.expiresAt else Long.MAX_VALUE, it.domain, it.path, it.secure, it.httpOnly, it.hostOnly)
        }))
    }
}
