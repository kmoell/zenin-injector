package com.zenin.injector

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZeninTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0D0D12)
                ) {
                    InjectorScreen()
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun InjectorScreen() {
        var key by remember { mutableStateOf("") }
        var showKey by remember { mutableStateOf(false) }
        var busy by remember { mutableStateOf(false) }
        var log by remember { mutableStateOf("Готов. Введи ключ и жми INJECT — открою Standoff 2, дам 15 сек зайти в лобби, потом инжект.\n") }
        val scroll = rememberScrollState()

        fun append(line: String) {
            log += line + "\n"
        }

        LaunchedEffect(log) { scroll.animateScrollTo(scroll.maxValue) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(12.dp))
            Text(
                "ZENIN",
                color = Color(0xFF7C5CFF),
                fontSize = 34.sp
            )
            Text(
                "Internal • Standoff 2",
                color = Color(0xFF8A8A99),
                fontSize = 13.sp
            )
            Spacer(Modifier.height(28.dp))

            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text("Ключ активации") },
                singleLine = true,
                enabled = !busy,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions.Default,
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(
                            if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = null,
                            tint = Color(0xFF8A8A99)
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF7C5CFF),
                    unfocusedBorderColor = Color(0xFF33333F),
                    focusedLabelColor = Color(0xFF7C5CFF),
                    unfocusedLabelColor = Color(0xFF8A8A99),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color(0xFF7C5CFF)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    if (key.isBlank()) {
                        append("[!] Введи ключ.")
                        return@Button
                    }
                    busy = true
                    log = ""
                    append("[*] Открываю Standoff 2...")
                    // Игра открывается СРАЗУ — за 15 сек задержки успеваешь зайти в лобби,
                    // и только потом loader инжектит.
                    launchGame()
                    lifecycleScope.launch {
                        val result = withContext(Dispatchers.IO) {
                            Injector.run(this@MainActivity, key.trim()) { line ->
                                lifecycleScope.launch { append(line) }
                            }
                        }
                        if (!result) {
                            append("[x] Не удалось. Проверь рут и ключ.")
                        }
                        busy = false
                    }
                },
                enabled = !busy,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF7C5CFF),
                    disabledContainerColor = Color(0xFF2A2438)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Icon(Icons.Filled.Bolt, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("INJECT", fontSize = 17.sp, color = Color.White)
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "Лог",
                color = Color(0xFF8A8A99),
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(Modifier.height(6.dp))
            Surface(
                color = Color(0xFF16161F),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Text(
                    log,
                    color = Color(0xFF9FE870),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scroll)
                        .padding(12.dp)
                )
            }
        }
    }

    private fun launchGame() {
        try {
            val intent = packageManager.getLaunchIntentForPackage("com.axlebolt.standoff2")
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
        } catch (_: Exception) {
        }
    }
}
