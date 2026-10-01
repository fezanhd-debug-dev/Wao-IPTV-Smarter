package com.wao.iptv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        CrashHandler.install(this)
        super.onCreate(savedInstanceState)
        setContent {
            WaoTheme {
                val ctx = LocalContext.current
                var crashText by remember { mutableStateOf(CrashHandler.readAndClear(ctx)) }

                if (crashText != null) {
                    CrashScreen(crashText!!) { crashText = null }
                } else {
                    val tv = remember { isTvMode(ctx, vm.settings.uiMode) }
                    vm.homeRoute = if (tv) "tv_home" else "home"
                    val nav = rememberNavController()

                    NavHost(navController = nav, startDestination = "boot") {
                        composable("boot") { BootScreen(vm, nav) }
                        composable("login") { LoginScreen(vm, nav) }
                        composable("home") { HomeScreen(vm, nav) }
                        composable("live") { LiveScreen(vm, nav) }
                        composable("vod") { VodScreen(vm, nav) }
                        composable("settings") { SettingsScreen(vm, nav) }
                        composable("player") { PlayerScreen(vm, nav) }
                        composable("tv_home") { TvHomeScreen(vm, nav) }
                        composable("tv_quad") { TvQuadViewScreen(vm, nav) }
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun CrashScreen(text: String, onDismiss: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0E1A))
            .padding(16.dp)
    ) {
        Text("App crash ho gayi thi. Yeh error neeche hai — screenshot karke bhej dein.", color = Color.White)
        Spacer(Modifier.height(12.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            SelectionContainer {
                Text(text, color = Color(0xFFF87171))
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onDismiss) { Text("Continue") }
    }
}
