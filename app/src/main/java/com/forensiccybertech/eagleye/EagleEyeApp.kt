package com.forensiccybertech.eagleye

import android.app.Application
import com.forensiccybertech.eagleye.data.AuthRepository
import com.forensiccybertech.eagleye.data.EncryptedCookieJar
import com.forensiccybertech.eagleye.data.SecureTokenStore
import com.forensiccybertech.eagleye.data.SessionManager
import com.forensiccybertech.eagleye.network.ApiClient

/** Manual DI container: small app, avoids a DI framework dependency. */
class EagleEyeApp : Application() {
    lateinit var session: SessionManager; private set
    lateinit var api: ApiClient; private set
    lateinit var auth: AuthRepository; private set

    override fun onCreate() {
        super.onCreate()
        val jar = EncryptedCookieJar(SecureTokenStore(this))
        session = SessionManager(jar)
        api = ApiClient(jar, session)
        auth = AuthRepository(api.http, session, jar)
    }
}
