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
        Modifier.fillMaxWidth().background(Slate900, shape)
            .border(1.dp, if (selected) Cyan.copy(alpha = 0.5f) else Slate800, shape)
            .then(if (enabled) Modifier.tvClick(shape, onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp).alpha(if (enabled) 1f else 0.5f),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Txt(label, 12, if (enabled) Color.White else Slate400, FontWeight.SemiBold)
            if (sub.isNotBlank()) Txt(sub, 10, Slate500, modifier = Modifier.padding(top = 2.dp))
        }
        RadioButton(
            selected = selected, onClick = if (enabled) onClick else null, enabled = enabled,
            colors = RadioButtonDefaults.colors(selectedColor = Cyan, unselectedColor = Slate600())
        )
    }
}

private fun Slate600() = Color(0xFF475569)

@Composable
fun SettingsScreen(vm: AppViewModel, nav: NavController) {
    val lang = vm.settings.lang
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
    val daysLeft = remember(vm.account) { vm.daysUntilExpiry() }

    Column(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Txt(tr(lang, "settings_title"), 18, Color.White, FontWeight.Black)
            Txt(tr(lang, "settings_subtitle"), 11, Slate400, modifier = Modifier.padding(top = 2.dp))
        }
        HLine()

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {

            Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(16.dp)) {
                Txt(tr(lang, "select_language"), 11, Cyan, FontWeight.Bold, spacing = 0.6f)
                Spacer(Modifier.height(10.dp))
                LanguagePicker(lang, onSelect = { code -> vm.updateSettings(vm.settings.copy(lang = code)) }, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(16.dp))

            Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Txt(tr(lang, "account").uppercase(), 10, Cyan, FontWeight.Bold, spacing = 0.8f)
                        Txt(vm.account.username.ifBlank { tr(lang, "guest_label") }, 15, Color.White, FontWeight.Bold, Modifier.padding(top = 2.dp))
                        val exp = vm.account.expDate
                        if (exp > 0) {
                            val df = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                            val expColor = if (daysLeft != null && daysLeft <= 3) Amber else Slate400
                            Txt("Expires: ${df.format(Date(exp * 1000))}", 11, expColor, modifier = Modifier.padding(top = 2.dp))
                            if (daysLeft != null && daysLeft <= 3) {
                                val label = when {
                                    daysLeft < 0 -> tr(lang, "err_account_expired")
                                    daysLeft == 0 -> tr(lang, "err_account_expires_today")
                                    daysLeft == 1 -> tr(lang, "err_account_expires_tomorrow")
                                    else -> tr(lang, "err_account_expires_days").replace("{days}", daysLeft.toString())
                                }
                                Pill(label, Color(0x33FBBF24), Amber, Modifier.padding(top = 4.dp))
                            }
                        }
                        if (vm.account.maxConnections.isNotBlank()) {
                            Txt("Max connections: ${vm.account.maxConnections}", 11, Slate500)
                        }
                    }
                    Pill(vm.account.status.ifBlank { "Active" }, Color(0x3334D399), Emerald)
                }

                if (vm.session?.type == "xtream") {
                    Spacer(Modifier.height(12.dp))
                    HLine()
                    Spacer(Modifier.height(12.dp))
                    val btnShape = RoundedCornerShape(10.dp)
                    Row(
                        Modifier.background(Slate900, btnShape).border(1.dp, Slate700, btnShape)
                            .tvClick(btnShape) { vm.testConnection() }.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (vm.testingConnection) {
                            CircularProgressIndicator(color = Cyan, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(8.dp))
                            Txt("...", 12, Cyan, FontWeight.Bold)
                        } else {
                            Txt(tr(lang, "test_connection"), 12, Cyan, FontWeight.Bold)
                        }
                    }
                    vm.connectionTestResult?.let { r ->
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Pill(if (r.ok) "OK" else "FAILED", if (r.ok) Color(0x3334D399) else Color(0x33DC2626), if (r.ok) Emerald else RedSoft)
                            Spacer(Modifier.width(8.dp))
                            Txt("${r.responseMs} ms", 11, Slate400, FontWeight.SemiBold)
                        }
                        Txt(r.message, 11, Slate400, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            if (vm.savedAccounts.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(16.dp)) {
                    Txt(tr(lang, "saved_accounts_title"), 11, Cyan, FontWeight.Bold, spacing = 0.6f)
                    Spacer(Modifier.height(10.dp))
                    vm.savedAccounts.forEach { acc ->
                        val isCurrent = acc.id == currentId
                        val shape = RoundedCornerShape(12.dp)
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 8.dp).background(Slate900, shape)
                                .border(1.dp, if (isCurrent) Cyan.copy(alpha = 0.5f) else Slate800, shape)
                                .then(if (!isCurrent) Modifier.tvClick(shape) { switchTarget = acc } else Modifier)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
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
                                    Modifier.size(24.dp).tvClick(RoundedCornerShape(8.dp)) { removeTarget = acc },
                                    contentAlignment = Alignment.Center
                                ) { Icon(Icons.Filled.Close, null, tint = Slate500, modifier = Modifier.size(14.dp)) }
                            }
                        }
                    }
                    Txt(tr(lang, "saved_accounts_note"), 10, Slate500, modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(Modifier.height(16.dp))
            }

            Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(16.dp)) {
                Txt(tr(lang, "player_engine").uppercase(), 11, Cyan, FontWeight.Bold, spacing = 0.6f)
                Spacer(Modifier.height(10.dp))
                RadioRow(tr(lang, "engine_exo_title"), tr(lang, "engine_exo_desc"), vm.settings.engine == 0) {
                    vm.updateSettings(vm.settings.copy(engine = 0))
                }
                Spacer(Modifier.height(8.dp))
                RadioRow(tr(lang, "engine_vlc_title"), tr(lang, "coming_soon"), vm.settings.engine == 1, enabled = false) {}
            }
            Spacer(Modifier.height(16.dp))

            Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(16.dp)) {
                Txt(tr(lang, "buffer_mode").uppercase(), 11, Cyan, FontWeight.Bold, spacing = 0.6f)
                Spacer(Modifier.height(10.dp))
                RadioRow(tr(lang, "buffer_low_title"), tr(lang, "buffer_low_desc"), vm.settings.bufferMode == 0) {
                    vm.updateSettings(vm.settings.copy(bufferMode = 0))
                }
                Spacer(Modifier.height(8.dp))
                RadioRow(tr(lang, "buffer_balanced_title"), tr(lang, "buffer_balanced_desc"), vm.settings.bufferMode == 1) {
                    vm.updateSettings(vm.settings.copy(bufferMode = 1))
                }
                Spacer(Modifier.height(8.dp))
                RadioRow(tr(lang, "buffer_smooth_title"), tr(lang, "buffer_smooth_desc"), vm.settings.bufferMode == 2) {
                    vm.updateSettings(vm.settings.copy(bufferMode = 2))
                }
            }
            Spacer(Modifier.height(16.dp))

            Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(16.dp)) {
                Txt(tr(lang, "parental_lock").uppercase(), 11, Purple, FontWeight.Bold, spacing = 0.6f)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Txt(tr(lang, "pin_master_enforcement"), 12, Slate300)
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
                    Txt(tr(lang, "pin_adult_label"), 12, Slate300)
                    Txt(
                        if (vm.settings.pinEnabled) tr(lang, "locked_label") else tr(lang, "unlocked_label"),
                        11, if (vm.settings.pinEnabled) RedSoft else Slate500, FontWeight.Bold
                    )
                }
                if (vm.settings.pinEnabled) {
                    Spacer(Modifier.height(10.dp))
                    val shape = RoundedCornerShape(10.dp)
                    Txt(
                        tr(lang, "change_pin"), 11, Cyan, FontWeight.SemiBold,
                        modifier = Modifier.background(Slate900, shape).tvClick(shape) { pinStage = 1; firstPin = "" }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(16.dp)) {
                Txt(tr(lang, "app_update").uppercase(), 11, Cyan, FontWeight.Bold, spacing = 0.6f)
                Spacer(Modifier.height(6.dp))
                Txt("Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})", 11, Slate400)
                Spacer(Modifier.height(10.dp))
                val btnShape = RoundedCornerShape(10.dp)
                Row(
                    Modifier.background(Slate900, btnShape).border(1.dp, Slate700, btnShape)
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
                        Txt(tr(lang, "checking_for_update"), 12, Cyan, FontWeight.Bold)
                    } else {
                        Txt(tr(lang, "check_for_updates"), 12, Cyan, FontWeight.Bold)
                    }
                }

                when (val r = updateResult) {
                    is UpdateResult.Available -> {
                        Spacer(Modifier.height(10.dp))
                        Txt(tr(lang, "new_version_available").replace("{v}", r.info.versionName), 12, Emerald, FontWeight.Bold)
                        if (r.info.notes.isNotBlank()) Txt(r.info.notes, 11, Slate400, modifier = Modifier.padding(top = 2.dp))
                        if (r.info.apkUrl.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            val dlShape = RoundedCornerShape(10.dp)
                            Txt(
                                tr(lang, "download"), 12, Slate950, FontWeight.Bold,
                                modifier = Modifier.background(Cyan, dlShape)
                                    .tvClick(dlShape) { uriHandler.openUri(r.info.apkUrl) }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                    UpdateResult.UpToDate -> {
                        Spacer(Modifier.height(10.dp))
                        Txt(tr(lang, "up_to_date"), 12, Slate400)
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
                Modifier.fillMaxWidth().background(Color(0x33DC2626), logoutShape)
                    .border(1.dp, Red.copy(alpha = 0.4f), logoutShape)
                    .tvClick(logoutShape) { showLogoutConfirm = true }.padding(14.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Logout, null, tint = RedSoft, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Txt(tr(lang, "logout"), 12, RedSoft, FontWeight.Bold)
            }
            Spacer(Modifier.height(24.dp))
        }
        BottomNav("settings", vm, nav)
    }

    if (pinStage > 0) {
        PinDialog(if (pinStage == 1) tr(lang, "pin_create_title") else tr(lang, "pin_reenter_title"), { pinStage = 0 }) { p ->
            if (p.length != 4) tr(lang, "pin_need_4_digits")
            else if (pinStage == 1) { firstPin = p; pinStage = 2; null }
            else if (p != firstPin) tr(lang, "pin_mismatch")
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
            title = { Txt(tr(lang, "remove_pin_title"), 15, Color.White, FontWeight.Bold) },
            text = { Txt(tr(lang, "remove_pin_desc"), 12, Slate400) },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateSettings(vm.settings.copy(pinEnabled = false, pin = ""))
                    vm.unlocked = false
                    showRemovePinDialog = false
                }) { Txt(tr(lang, "remove"), 13, RedSoft, FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showRemovePinDialog = false }) { Txt(tr(lang, "cancel"), 13, Slate400) } }
        )
    }

    switchTarget?.let { acc ->
        AlertDialog(
            onDismissRequest = { switchTarget = null },
            containerColor = Color(0xFF121727),
            title = { Txt(tr(lang, "switch_account_title"), 15, Color.White, FontWeight.Bold) },
            text = { Txt(tr(lang, "switch_account_desc").replace("{label}", acc.label), 12, Slate400) },
            confirmButton = {
                TextButton(onClick = {
                    switchTarget = null
                    vm.switchAccount(acc) { nav.navigate(vm.homeRoute) { popUpTo(0) } }
                }) { Txt(tr(lang, "switch"), 13, Cyan, FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { switchTarget = null }) { Txt(tr(lang, "cancel"), 13, Slate400) } }
        )
    }

    removeTarget?.let { acc ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            containerColor = Color(0xFF121727),
            title = { Txt(tr(lang, "remove_account_title"), 15, Color.White, FontWeight.Bold) },
            text = { Txt(tr(lang, "remove_account_desc").replace("{label}", acc.label), 12, Slate400) },
            confirmButton = {
                TextButton(onClick = { vm.removeAccount(acc.id); removeTarget = null }) { Txt(tr(lang, "remove"), 13, RedSoft, FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { removeTarget = null }) { Txt(tr(lang, "cancel"), 13, Slate400) } }
        )
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            containerColor = Color(0xFF121727),
            title = { Txt(tr(lang, "logout_title"), 15, Color.White, FontWeight.Bold) },
            text = { Txt(tr(lang, "logout_desc"), 12, Slate400) },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirm = false
                    vm.logout()
                    nav.navigate("login") { popUpTo(0) }
                }) { Txt(tr(lang, "logout"), 13, RedSoft, FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showLogoutConfirm = false }) { Txt(tr(lang, "cancel"), 13, Slate400) } }
        )
    }
}
