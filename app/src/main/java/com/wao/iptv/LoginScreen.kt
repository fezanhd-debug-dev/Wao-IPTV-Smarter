package com.wao.iptv

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

@Composable
fun BootScreen(vm: AppViewModel, nav: NavController) {
    LaunchedEffect(Unit) {
        val s = vm.session
        if (s == null) {
            nav.navigate("login") { popUpTo("boot") { inclusive = true } }
        } else {
            vm.connect(
                s,
                onFail = { nav.navigate("login") { popUpTo("boot") { inclusive = true } } }
            ) {
                nav.navigate(vm.homeRoute) { popUpTo("boot") { inclusive = true } }
            }
        }
    }
    Box(Modifier.fillMaxSize().background(Bg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BigLogo()
            Spacer(Modifier.height(24.dp))
            CircularProgressIndicator(color = Cyan, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(12.dp))
            Txt("Channels load ho rahe hain...", 12, Slate400)
        }
    }
}

@Composable
private fun LoginField(
    label: String,
    icon: ImageVector,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(Slate950.copy(alpha = 0.6f), shape)
            .border(1.dp, if (focused) NeonCyan else Slate800, shape)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Txt(label.uppercase(), 10, Slate400, FontWeight.Bold, spacing = 0.8f)
        Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Cyan, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                cursorBrush = SolidColor(Cyan),
                keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboardType),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                modifier = Modifier.weight(1f).onFocusChanged { focused = it.isFocused },
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) Text(placeholder, color = Color(0xFF475569), fontSize = 14.sp, maxLines = 1)
                        inner()
                    }
                }
            )
        }
    }
}

@Composable
private fun ModeTab(text: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .background(if (selected) Cyan else Color.Transparent, shape)
            .tvClick(shape, onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (selected) Slate950 else Slate400, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(6.dp))
        Txt(text, 12, if (selected) Slate950 else Slate400, FontWeight.Bold)
    }
}

@Composable
fun LoginScreen(vm: AppViewModel, nav: NavController) {
    var mode by remember { mutableStateOf("xtream") }
    var server by remember { mutableStateOf(vm.lastServer) }
    var user by remember { mutableStateOf(vm.lastUser) }
    var pass by remember { mutableStateOf("") }
    var m3u by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    fun submit() {
        localError = null
        val s: Session
        if (mode == "xtream") {
            if (server.isBlank() || user.isBlank() || pass.isBlank()) {
                localError = "Server URL, username aur password teeno bharein"
                return
            }
            s = Session("xtream", normalizeServer(server), user.trim(), pass.trim())
        } else {
            if (m3u.isBlank()) {
                localError = "M3U link paste karein"
                return
            }
            s = Session("m3u", m3uUrl = m3u.trim())
        }
        vm.connect(s) {
            nav.navigate(vm.homeRoute) { popUpTo("login") { inclusive = true } }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0xFF17233F), Bg), center = Offset(500f, 350f), radius = 1400f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .widthIn(max = 448.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BigLogo()
            Spacer(Modifier.height(16.dp))
            Txt("Wao IPTV Smarter", 26, Color.White, FontWeight.Black, spacing = 1f)
            Spacer(Modifier.height(4.dp))
            Txt("STREAM ANYTIME • ANYWHERE", 12, Cyan, FontWeight.SemiBold, spacing = 2f)
            Spacer(Modifier.height(28.dp))

            Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(24.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Slate900.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ModeTab("Xtream Codes", Icons.Filled.Person, mode == "xtream", Modifier.weight(1f)) { mode = "xtream" }
                    ModeTab("M3U Playlist", Icons.Filled.Link, mode == "m3u", Modifier.weight(1f)) { mode = "m3u" }
                }
                Spacer(Modifier.height(24.dp))

                if (mode == "xtream") {
                    LoginField("Server URL", Icons.Filled.Public, server, { server = it }, "http://my-iptv-server.com:8080", KeyboardType.Uri)
                    Spacer(Modifier.height(16.dp))
                    LoginField("Username", Icons.Filled.Person, user, { user = it }, "Enter Xtream username")
                    Spacer(Modifier.height(16.dp))
                    LoginField("Password", Icons.Filled.Lock, pass, { pass = it }, "••••••••", password = true)
                } else {
                    LoginField("M3U Playlist URL", Icons.Filled.Link, m3u, { m3u = it }, "https://provider.com/get.php?username=...", KeyboardType.Uri)
                }
                Spacer(Modifier.height(16.dp))

                val btnShape = RoundedCornerShape(16.dp)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(Brush.horizontalGradient(listOf(Cyan, Blue)), btnShape)
                        .tvClick(btnShape) { if (!vm.loading) submit() },
                    contentAlignment = Alignment.Center
                ) {
                    if (vm.loading) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = Slate950, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Txt("LOADING CHANNELS...", 14, Slate950, FontWeight.Black, spacing = 1f)
                        }
                    } else {
                        Txt(
                            if (mode == "xtream") "CONNECT & LOAD CHANNELS" else "LOAD M3U PLAYLIST",
                            14, Slate950, FontWeight.Black, spacing = 1f
                        )
                    }
                }

                val err = localError ?: vm.error
                if (err != null) {
                    Spacer(Modifier.height(12.dp))
                    Txt(err, 12, RedSoft, FontWeight.SemiBold, Modifier.fillMaxWidth(), align = TextAlign.Center)
                }

                Spacer(Modifier.height(24.dp))
                HLine()
                Spacer(Modifier.height(16.dp))
                Txt("Powered by MediaCodec HW+ & ExoPlayer", 12, Slate400, modifier = Modifier.fillMaxWidth(), align = TextAlign.Center)
            }
        }
    }
}
