package com.forensiccybertech.eagleye.ui.login

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.forensiccybertech.eagleye.EagleEyeApp
import com.forensiccybertech.eagleye.R
import com.forensiccybertech.eagleye.data.LoginResult
import com.forensiccybertech.eagleye.ui.theme.EagleEyeTheme
import com.forensiccybertech.eagleye.ui.theme.Eagle
import com.forensiccybertech.eagleye.ui.theme.MonoSmall
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

private val Backdrop = Color(0xFF051424)
private val Cyan = Color(0xFF1FB6D6)
private val Sky = Color(0xFF53D6F7)

/** Native version of the "Security officer authentication" design. Always dark, like the web sign-in. */
@Composable
fun LoginScreen(app: EagleEyeApp, sessionExpired: Boolean) = EagleEyeTheme(darkTheme = true) {
    val vm: LoginViewModel = viewModel(factory = viewModelFactory { initializer { LoginViewModel(app) } })
    val ui by vm.ui.collectAsStateWithLifecycle()
    var identifier by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }   // cleared on process death; never persisted
    var showPassword by rememberSaveable { mutableStateOf(false) }
    val e = Eagle.colors
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = e.chip, unfocusedContainerColor = e.chip,
        focusedBorderColor = Cyan, unfocusedBorderColor = Color.Transparent,
    )

    Box(Modifier.fillMaxSize().background(Backdrop)) {
        Canvas(Modifier.fillMaxSize()) {
            fun glow(c: Color, center: Offset, r: Float) = drawCircle(Brush.radialGradient(listOf(c, Color.Transparent), center, r), r, center)
            glow(Cyan.copy(alpha = 0.18f), Offset(size.width / 2, size.height * 0.2f), size.width * 0.7f)
            glow(Color(0xFF6366F1).copy(alpha = 0.10f), Offset(size.width, size.height * 0.75f), size.width * 0.6f)
        }
        Column(
            Modifier.fillMaxSize().statusBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painterResource(R.drawable.eagleye_logo), "EaglEye", contentScale = ContentScale.Fit,
                modifier = Modifier.size(84.dp).background(Color(0xFF010F1F), RoundedCornerShape(20.dp)).border(1.dp, Cyan.copy(alpha = 0.4f), RoundedCornerShape(20.dp)).padding(12.dp)
            )
            Text("EaglEye CyberFusion", Modifier.padding(top = 16.dp), fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFD4E4FA))
            Text("FORENSIC CYBERTECH · VANGUARD MOBILE", Modifier.padding(top = 4.dp, bottom = 24.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Sky, textAlign = TextAlign.Center)

            Column(
                Modifier.widthIn(max = 440.dp).fillMaxWidth().background(Color(0xFF131D31), RoundedCornerShape(24.dp))
                    .border(1.dp, e.border, RoundedCornerShape(24.dp)).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (sessionExpired) Text("Your session expired. Please sign in again.", color = MaterialTheme.colorScheme.error)
                Text("Email or username", fontWeight = FontWeight.SemiBold, color = Color(0xFFD4E4FA))
                OutlinedTextField(
                    identifier, { identifier = it }, singleLine = true, shape = RoundedCornerShape(14.dp), colors = fieldColors,
                    placeholder = { Text("analyst@company.com") }, leadingIcon = { Icon(Icons.Filled.Person, null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Password", fontWeight = FontWeight.SemiBold, color = Color(0xFFD4E4FA), modifier = Modifier.padding(top = 4.dp))
                OutlinedTextField(
                    password, { password = it }, singleLine = true, shape = RoundedCornerShape(14.dp), colors = fieldColors,
                    leadingIcon = { Icon(Icons.Filled.Lock, null) },
                    trailingIcon = { IconButton({ showPassword = !showPassword }) { Icon(if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (showPassword) "Hide password" else "Show password") } },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { vm.login(identifier, password) }),
                    modifier = Modifier.fillMaxWidth()
                )
                ui.error?.let {
                    Text(it, color = Color(0xFFFF8A8A), fontSize = 13.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().background(Color(0x1AFF5252), RoundedCornerShape(10.dp)).padding(10.dp))
                }
                Button(
                    { vm.login(identifier, password) }, enabled = !ui.loading, shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Sky, contentColor = Color(0xFF003641)),
                    modifier = Modifier.fillMaxWidth().height(54.dp).padding(top = 2.dp)
                ) {
                    if (ui.loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Color(0xFF003641))
                    else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Sign in to Console", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Shield, null, Modifier.size(16.dp), tint = Cyan)
                    Text("  Authorized Personnel Only · Read-Only Access", style = MonoSmall, color = Color(0xFF94A3B8))
                }
            }
        }
    }
}
