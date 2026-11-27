package com.forensiccybertech.eagleye.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.forensiccybertech.eagleye.EagleEyeApp
import com.forensiccybertech.eagleye.data.LoginResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class LoginUi(val loading: Boolean = false, val error: String? = null)

class LoginViewModel(private val app: EagleEyeApp) : ViewModel() {
    private val _ui = MutableStateFlow(LoginUi())
    val ui: StateFlow<LoginUi> = _ui

    fun login(identifier: String, password: String) {
        if (_ui.value.loading) return
        if (identifier.isBlank() || password.isEmpty()) {
            _ui.value = LoginUi(error = "Enter your email or username and password"); return
        }
        viewModelScope.launch {
            _ui.value = LoginUi(loading = true)
            // On success SessionManager flips to LoggedIn and MainActivity swaps to the dashboard.
            _ui.value = when (val r = app.auth.login(identifier, password)) {
                LoginResult.Success -> LoginUi()
                is LoginResult.Failure -> LoginUi(error = r.message)
            }
        }
    }
}

@Composable
fun LoginScreen(app: EagleEyeApp, sessionExpired: Boolean) {
    val vm: LoginViewModel = viewModel(factory = viewModelFactory { initializer { LoginViewModel(app) } })
    val ui by vm.ui.collectAsStateWithLifecycle()
    var identifier by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }   // cleared on process death; never persisted
    var showPassword by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
    ) {
        Text("EaglEye EDR/XDR", style = MaterialTheme.typography.headlineMedium)
        Text("Sign in with your EaglEye account", style = MaterialTheme.typography.bodyMedium)
        if (sessionExpired) Text("Your session expired. Please sign in again.", color = MaterialTheme.colorScheme.error)
        OutlinedTextField(
            identifier, { identifier = it }, label = { Text("Email or username") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            password, { password = it }, label = { Text("Password") }, singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { vm.login(identifier, password) }),
            trailingIcon = { TextButton({ showPassword = !showPassword }) { Text(if (showPassword) "Hide" else "Show") } },
            modifier = Modifier.fillMaxWidth()
        )
        ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button({ vm.login(identifier, password) }, enabled = !ui.loading, modifier = Modifier.fillMaxWidth()) {
            if (ui.loading) CircularProgressIndicator(Modifier.padding(2.dp), strokeWidth = 2.dp) else Text("Sign in")
        }
    }
}
