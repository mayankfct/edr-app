package com.forensiccybertech.eagleye

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forensiccybertech.eagleye.data.SessionState
import com.forensiccybertech.eagleye.ui.MainScaffold
import com.forensiccybertech.eagleye.ui.login.LoginScreen
import com.forensiccybertech.eagleye.ui.theme.EagleEyeTheme
import com.forensiccybertech.eagleye.util.onlineFlow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val app = application as EagleEyeApp
        setContent {
            EagleEyeTheme {
                val state by app.session.state.collectAsStateWithLifecycle()
                val online by remember { applicationContext.onlineFlow() }.collectAsStateWithLifecycle(initialValue = true)
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize()) {
                        if (!online) Text(
                            "No internet connection", Modifier.fillMaxWidth().padding(8.dp),
                            color = MaterialTheme.colorScheme.error
                        )
                        when (state) {
                            SessionState.LoggedIn -> MainScaffold(app)
                            else -> LoginScreen(app, sessionExpired = state == SessionState.Expired)
                        }
                    }
                }
            }
        }
    }
}
