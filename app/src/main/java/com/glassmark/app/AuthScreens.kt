package com.glassmark.app

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

private enum class AuthMode { LOGIN, REGISTER, RESET }

@Composable
fun AuthScreen(vm: AppViewModel) {
    val scale by vm.textScale.collectAsState()
    val baseDensity = androidx.compose.ui.platform.LocalDensity.current
    CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(baseDensity.density, baseDensity.fontScale * scale.multiplier)) {
        AuthContent(vm)
    }
}

@Composable
private fun AuthContent(vm: AppViewModel) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val busy by vm.busy.collectAsState()
    val message by vm.message.collectAsState()
    var mode by remember { mutableStateOf(AuthMode.LOGIN) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var info by remember { mutableStateOf<String?>(null) }

    fun switchTo(m: AuthMode) { mode = m; info = null; vm.message.value = null }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(p.gradient))) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("LINK", color = p.muted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(
                when (mode) { AuthMode.LOGIN -> "Entrar"; AuthMode.REGISTER -> "Criar conta"; AuthMode.RESET -> "Recuperar senha" },
                color = p.text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                when (mode) {
                    AuthMode.LOGIN -> "Acesse sua biblioteca em qualquer aparelho."
                    AuthMode.REGISTER -> "Salve seus links na nuvem e sincronize tudo."
                    AuthMode.RESET -> "Enviaremos um link para você redefinir a senha."
                }, color = p.muted
            )
            Spacer(Modifier.height(20.dp))

            Surface(color = p.glass, shape = RoundedCornerShape(26.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (mode == AuthMode.REGISTER) {
                        AuthField(name, { name = it }, "Nome")
                    }
                    AuthField(email, { email = it }, "E-mail", keyboardType = KeyboardType.Email)
                    if (mode != AuthMode.RESET) {
                        AuthField(password, { password = it }, "Senha", password = true)
                    }
                    message?.let { Text(it, color = p.error, style = MaterialTheme.typography.bodySmall) }
                    info?.let { Text(it, color = p.accent, style = MaterialTheme.typography.bodySmall) }

                    Button(
                        onClick = {
                            when (mode) {
                                AuthMode.LOGIN -> vm.signIn(email, password)
                                AuthMode.REGISTER -> vm.signUp(name, email, password)
                                AuthMode.RESET -> vm.resetPassword(email) {
                                    info = "Se o e-mail estiver cadastrado, enviamos o link de recuperação. Confira também o spam."
                                }
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Text(when (mode) { AuthMode.LOGIN -> "Entrar"; AuthMode.REGISTER -> "Criar conta"; AuthMode.RESET -> "Enviar link" })
                    }

                    if (mode != AuthMode.RESET) {
                        OutlinedButton(
                            onClick = { (context as? Activity)?.let { vm.signInWithGoogle(it) } },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) { Text("Entrar com Google", color = p.text) }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            when (mode) {
                AuthMode.LOGIN -> {
                    TextButton(onClick = { switchTo(AuthMode.RESET) }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Esqueci minha senha") }
                    TextButton(onClick = { switchTo(AuthMode.REGISTER) }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Não tem conta? Criar conta") }
                }
                AuthMode.REGISTER -> TextButton(onClick = { switchTo(AuthMode.LOGIN) }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Já tenho conta") }
                AuthMode.RESET -> TextButton(onClick = { switchTo(AuthMode.LOGIN) }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Voltar para o login") }
            }
            TextButton(onClick = { vm.continueAsGuest() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Continuar como visitante", color = p.muted)
            }
            Text(
                "No modo visitante seus links ficam apenas neste aparelho.",
                color = p.muted, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun AuthField(
    value: String, onChange: (String) -> Unit, label: String,
    password: Boolean = false, keyboardType: KeyboardType = KeyboardType.Text
) {
    val p = LocalPalette.current
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = p.text, unfocusedTextColor = p.text,
            focusedLabelColor = p.accent, unfocusedLabelColor = p.muted,
            focusedBorderColor = p.accent, unfocusedBorderColor = p.muted
        )
    )
}
