package com.wao.iptv

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
private fun RadioRow(label: String, sub: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .background(Slate900, shape)
            .border(1.dp, if (selected) Cyan.copy(alpha = 0.5f) else Slate800, shape)
            .then(if (enabled) Modifier.tvClick(shape, onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .alpha(if (enabled) 1f else 0.5f),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Txt(label, 12, if (enabled) Color.White else Slate400, FontWeight.SemiBold)
            if (sub.isNotBlank()) Txt(sub, 10, Slate500, modifier = Modifier.padding(top = 2.dp))
        }
        RadioButton(
            selected = selected,
            onClick = if (enabled) onClick else null,
            enabled = enabled,
            colors = RadioButtonDefaults.colors(selectedColor = Cyan, unselectedColor = Slate600())
        )
    }
}

private fun Slate600() = Color(0xFF475569)

@Composable
fun SettingsScreen(vm: AppViewModel, nav: NavController) {
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var pinStage by remember { mutableStateOf(0) }
    var firstPin by remember { mutableStateOf("") }
    var showRemovePinDialog by remember { mutableStateOf(false) }

    var switchTarget by remember { mutableStateOf<SavedAccount?>(null) }
    var removeTarget by remember { mutableStateOf<SavedAccount?>(null) }

    var updateChecking by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateResult?>(null) }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val currentId = vm.currentAccountId()

    Column(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Txt("App Settings & Decoder", 18, Color.White, FontWeight.Black)
            Txt("Manage hardware codecs, cache & parental PIN", 11, Slate400, modifier = Modifier.padding(top = 2.dp))
        }
        HLine()

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {

            Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Txt("ACCOUNT", 10, Cyan, FontWeight.Bold, spacing = 0.8f)
                        Txt(vm.account.username.ifBlank { "Guest" }, 15, Color.White, FontWeight.Bold, Modifier.padding(top = 2.dp))
                        val exp = vm.account.expDate
                        if (exp > 0) {
                            val df = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                            Txt("Expires: ${df.format(Date(exp * 1000))}", 11, Slate400, modifier = Modifier.padding(top = 2.dp))
                        }
                        if (vm.account.maxConnections.isNotBlank()) {
                            Txt("Max connections: ${vm.account.maxConnections}", 11, Slate500)
                        }
                    }
                    Pill(vm.account.status.ifBlank { "Active" }, Color(0x3334D399), Emerald)
                }
            }
            Spacer(Modifier.height(16.dp))

            if (vm.savedAccounts.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(16.dp)) {
                    Txt("SAVED ACCOUNTS", 11, Cyan, FontWeight.Bold, spacing = 0.6f)
                    Spacer(Modifier.height(10.dp))
                    vm.savedAccounts.forEach { acc ->
                        val isCurrent = acc.id == currentId
                        val shape = RoundedCornerShape(12.dp)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .background(Slate900, shape)
                                .border(1.dp, if (isCurrent) Cyan.copy(alpha = 0.5f) else Slate800, shape)
                                .then(
                                    if (!isCurrent) Modifier.tvClick(shape) { switchTarget = acc } else Modifier
                                )
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                if (isCurrent) {
                                    Icon(Icons.Filled.CheckCircle, null, tint = Cyan, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                }
                                Txt(acc.label, 12, if (isCurrent) Cyan else Color.White, FontWeight.SemiBold, maxLines = 1)
                            }
                            if (!isCurrent) {
                                Box(
                                    Modifier
                                        .size(24.dp)
                                        .tvClick(RoundedCornerShape(8.dp)) { removeTarget = acc },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.Close, null, tint = Slate500, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                    Txt(
                        "Naya server login karne par woh khud yahan save ho jayega",
                        10, Slate500, modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(16.dp)) {
                Txt("PLAYER ENGINE", 11, Cyan, FontWeight.Bold, spacing = 0.6f)
                Spacer(Modifier.height(10.dp))
                RadioRow("WAO ExoPlayer (HW+ Recommended)", "Media3 hardware decoding", vm.settings.engine == 0) {
                    vm.updateSettings(vm.settings.copy(engine = 0))
                }
                Spacer(Modifier.height(8.dp))
                RadioRow("VLC Internal Engine", "Coming soon", vm.settings.engine == 1, enabled = false) {}
            }
            Spacer(Modifier.height(16.dp))

            Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(16.dp)) {
                Txt("BUFFER / STREAMING MODE", 11, Cyan, FontWeight.Bold, spacing = 0.6f)
                Spacer(Modifier.height(10.dp))
                RadioRow("Low Latency", "Kam buffer, fast start, weak net par ruk sakta hai", vm.settings.bufferMode == 0) {
                    vm.updateSettings(vm.settings.copy(bufferMode = 0))
                }
                Spacer(Modifier.height(8.dp))
                RadioRow("Balanced (Recommended)", "Zyada tar connections ke liye theek", vm.settings.bufferMode == 1) {
                    vm.updateSettings(vm.settings.copy(bufferMode = 1))
                }
                Spacer(Modifier.height(8.dp))
                RadioRow("Smooth (Weak Network)", "Zyada buffer, kam ruk-ruk kar chalega", vm.settings.bufferMode == 2) {
                    vm.updateSettings(vm.settings.copy(bufferMode = 2))
                }
            }
            Spacer(Modifier.height(16.dp))

            Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(16.dp)) {
                Txt("PARENTAL LOCK (4-DIGIT PIN)", 11, Purple, FontWeight.Bold, spacing = 0.6f)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Txt("Master PIN Enforcement", 12, Slate300)
                    Switch(
                        checked = vm.settings.pinEnabled,
                        onCheckedChange = { on ->
                            if (on && vm.settings.pin.isBlank()) { pinStage = 1; firstPin = "" }
                            else if (!on) showRemovePinDialog = true
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Cyan, checkedTrackColor = Cyan.copy(alpha = 0.4f))
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Txt("Adult 18+ Live & VOD", 12, Slate300)
                    Txt(
                        if (vm.settings.pinEnabled) "🔒 Locked" else "🔓 Unlocked",
                        11, if (vm.settings.pinEnabled) RedSoft else Slate500, FontWeight.Bold
                    )
                }
                if (vm.settings.pinEnabled) {
                    Spacer(Modifier.height(10.dp))
                    val shape = RoundedCornerShape(10.dp)
                    Txt(
                        "PIN badlein",
                        11, Cyan, FontWeight.SemiBold,
                        modifier = Modifier.background(Slate900, shape).tvClick(shape) { pinStage = 1; firstPin = "" }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(16.dp)) {
                Txt("APP UPDATE", 11, Cyan, FontWeight.Bold, spacing = 0.6f)
                Spacer(Modifier.height(6.dp))
                Txt("Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})", 11, Slate400)
                Spacer(Modifier.height(10.dp))
                val btnShape = RoundedCornerShape(10.dp)
                Row(
                    Modifier
                        .background(Slate900, btnShape)
                        .border(1.dp, Slate700, btnShape)
                        .tvClick(btnShape) {
                            if (!updateChecking) {
                                updateChecking = true
                                updateResult = null
                                scope.launch {
                                    val r = withContext(Dispatchers.IO) { checkForUpdate(BuildConfig.VERSION_CODE) }
                                    updateResult = r
                                    updateChecking = false
                                }
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (updateChecking) {
                        CircularProgressIndicator(color = Cyan, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(8.dp))
                        Txt("Checking...", 12, Cyan, FontWeight.Bold)
                    } else {
                        Txt("Check for Updates", 12, Cyan, FontWeight.Bold)
                    }
                }

                when (val r = updateResult) {
                    is UpdateResult.Available -> {
                        Spacer(Modifier.height(10.dp))
                        Txt("Naya version ${r.info.versionName} available hai", 12, Emerald, FontWeight.Bold)
                        if (r.info.notes.isNotBlank()) Txt(r.info.notes, 11, Slate400, modifier = Modifier.padding(top = 2.dp))
                        if (r.info.apkUrl.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            val dlShape = RoundedCornerShape(10.dp)
                            Txt(
                                "Download", 12, Slate950, FontWeight.Bold,
                                modifier = Modifier.background(Cyan, dlShape)
                                    .tvClick(dlShape) { uriHandler.openUri(r.info.apkUrl) }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                    UpdateResult.UpToDate -> {
                        Spacer(Modifier.height(10.dp))
                        Txt("Aap latest version use kar rahe hain", 12, Slate400)
                    }
                    is UpdateResult.Failed -> {
                        Spacer(Modifier.height(10.dp))
                        Txt(r.reason, 11, RedSoft)
                    }
                    null -> {}
                }
            }
            Spacer(Modifier.height(16.dp))

            val logoutShape = RoundedCornerShape(14.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0x33DC2626), logoutShape)
                    .border(1.dp, Red.copy(alpha = 0.4f), logoutShape)
                    .tvClick(logoutShape) { showLogoutConfirm = true }
                    .padding(14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Logout, null, tint = RedSoft, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Txt("Logout / Server Change Karein", 12, RedSoft, FontWeight.Bold)
            }
            Spacer(Modifier.height(24.dp))
        }
        BottomNav("settings", vm, nav)
    }

    if (pinStage > 0) {
        PinDialog(if (pinStage == 1) "Naya 4-digit PIN banayein" else "PIN dobara likhein", { pinStage = 0 }) { p ->
            if (p.length != 4) "4 digit ka PIN chahiye"
            else if (pinStage == 1) { firstPin = p; pinStage = 2; null }
            else if (p != firstPin) "PIN match nahi hua, dobara try karein"
            else {
                vm.updateSettings(vm.settings.copy(pinEnabled = true, pin = p))
                pinStage = 0
                null
            }
        }
    }

    if (showRemovePinDialog) {
        AlertDialog(
            onDismissRequest = { showRemovePinDialog = false },
            containerColor = Color(0xFF121727),
            title = { Txt("Parental Lock hatayein?", 15, Color.White, FontWeight.Bold) },
            text = { Txt("Adult categories sab ke liye dikhne lagengi.", 12, Slate400) },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateSettings(vm.settings.copy(pinEnabled = false, pin = ""))
                    vm.unlocked = false
                    showRemovePinDialog = false
                }) { Txt("Hataen", 13, RedSoft, FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showRemovePinDialog = false }) { Txt("Cancel", 13, Slate400) } }
        )
    }

    switchTarget?.let { acc ->
        AlertDialog(
            onDismissRequest = { switchTarget = null },
            containerColor = Color(0xFF121727),
            title = { Txt("Account switch karein?", 15, Color.White, FontWeight.Bold) },
            text = { Txt("\"${acc.label}\" par switch ho jayega.", 12, Slate400) },
            confirmButton = {
                TextButton(onClick = {
                    switchTarget = null
                    vm.switchAccount(acc) {
                        nav.navigate(vm.homeRoute) { popUpTo(0) }
                    }
                }) { Txt("Switch", 13, Cyan, FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { switchTarget = null }) { Txt("Cancel", 13, Slate400) } }
        )
    }

    removeTarget?.let { acc ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            containerColor = Color(0xFF121727),
            title = { Txt("Account hataayein?", 15, Color.White, FontWeight.Bold) },
            text = { Txt("\"${acc.label}\" list se hat jayega.", 12, Slate400) },
            confirmButton = {
                TextButton(onClick = { vm.removeAccount(acc.id); removeTarget = null }) {
                    Txt("Hataen", 13, RedSoft, FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { removeTarget = null }) { Txt("Cancel", 13, Slate400) } }
        )
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            containerColor = Color(0xFF121727),
            title = { Txt("Logout karein?", 15, Color.White, FontWeight.Bold) },
            text = { Txt("Aap dobara server details bhar kar login kar sakte hain.", 12, Slate400) },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirm = false
                    vm.logout()
                    nav.navigate("login") { popUpTo(0) }
                }) { Txt("Logout", 13, RedSoft, FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showLogoutConfirm = false }) { Txt("Cancel", 13, Slate400) } }
        )
    }
}
