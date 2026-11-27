package com.forensiccybertech.eagleye.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class SessionState { LoggedOut, LoggedIn, Expired }

/** Single source of truth for auth state. Session expiry and logout both wipe the encrypted cookies. */
class SessionManager(private val jar: EncryptedCookieJar) {
    private val _state = MutableStateFlow(if (jar.hasSession()) SessionState.LoggedIn else SessionState.LoggedOut)
    val state: StateFlow<SessionState> = _state

    fun loggedIn() { _state.value = SessionState.LoggedIn }
    fun logout() { jar.clear(); _state.value = SessionState.LoggedOut }
    fun expire() { jar.clear(); _state.value = SessionState.Expired }
}
