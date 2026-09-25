package com.wao.iptv

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

val Bg = Color(0xFF0A0E1A)
val TvBg = Color(0xFF060911)
val Glass = Color(0xB3121727)
val GlassBorder = Color(0x14FFFFFF)
val Cyan = Color(0xFF22D3EE)
val NeonCyan = Color(0xFF00E5FF)
val Blue = Color(0xFF3B82F6)
val Purple = Color(0xFFC084FC)
val PurpleDeep = Color(0xFF9333EA)
val Pink = Color(0xFFF472B6)
val Emerald = Color(0xFF34D399)
val Amber = Color(0xFFFBBF24)
val Red = Color(0xFFDC2626)
val RedSoft = Color(0xFFF87171)
val Slate950 = Color(0xFF020617)
val Slate900 = Color(0xFF0F172A)
val Slate800 = Color(0xFF1E293B)
val Slate700 = Color(0xFF334155)
val Slate500 = Color(0xFF64748B)
val Slate400 = Color(0xFF94A3B8)
val Slate300 = Color(0xFFCBD5E1)
val Slate200 = Color(0xFFE2E8F0)

@Composable
fun WaoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Cyan,
            onPrimary = Slate950,
            background = Bg,
            surface = Bg,
            onSurface = Color.White,
            onBackground = Color.White
        ),
        content = content
    )
}

fun Modifier.glass(shape: Shape = RoundedCornerShape(16.dp)): Modifier =
    this.background(Glass, shape).border(1.dp, GlassBorder, shape)

@Composable
fun Modifier.tvClick(
    shape: Shape = RoundedCornerShape(16.dp),
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
): Modifier {
    var focused by remember { mutableStateOf(false) }
    return this
        .graphicsLayer {
            scaleX = if (focused) 1.05f else 1f
            scaleY = if (focused) 1.05f else 1f
        }
        .then(if (focused) Modifier.border(3.dp, NeonCyan, shape) else Modifier)
        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
        .onFocusChanged { focused = it.isFocused }
        .clickable(onClick = onClick)
}

@Composable
fun Txt(
    text: String,
    size: Int,
    color: Color = Color.White,
    weight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    spacing: Float = 0f,
    align: TextAlign? = null
) {
    Text(
        text = text,
        color = color,
        fontSize = size.sp,
        fontWeight = weight,
        modifier = modifier,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        letterSpacing = spacing.sp,
        textAlign = align
    )
}

@Composable
fun Pill(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier, size: Int = 10) {
    Text(
        text = text,
        color = fg,
        fontSize = size.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 0.5.sp,
        modifier = modifier
            .background(bg, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

@Composable
fun HLine() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Slate800))
}

@Composable
fun BigLogo(size: Dp = 80.dp) {
    Box(
        Modifier
            .size(size)
            .background(Brush.linearGradient(listOf(Cyan, Blue, PurpleDeep)), RoundedCornerShape(24.dp))
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.fillMaxSize().background(Bg, RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.PlayArrow, null, tint = Cyan, modifier = Modifier.size(size * 0.45f))
        }
    }
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(Slate900, shape)
            .border(1.dp, if (focused) Cyan else Slate800, shape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Search, null, tint = Slate500, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
            cursorBrush = SolidColor(Cyan),
            modifier = Modifier.weight(1f).onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(placeholder, color = Slate500, fontSize = 12.sp, maxLines = 1)
                    inner()
                }
            }
        )
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, locked: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Text(
        text = (if (locked) "🔒 " else "") + text,
        color = if (selected) Slate950 else Slate300,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = Modifier
            .background(if (selected) Cyan else Slate800, shape)
            .tvClick(shape, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
fun CategoryChips(cats: List<Category>, selected: String, lockedIds: Set<String>, onSelect: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Chip("All", selected == "", false) { onSelect("") } }
        items(cats.size) { i ->
            val c = cats[i]
            Chip(c.name, selected == c.id, c.id in lockedIds) { onSelect(c.id) }
        }
    }
}

private data class NavEntry(val route: String, val label: String, val icon: ImageVector)

@Composable
fun BottomNav(current: String, vm: AppViewModel, nav: NavController) {
    val entries = listOf(
        NavEntry(vm.homeRoute, "Home", Icons.Filled.Home),
        NavEntry("live", "Live TV", Icons.Filled.LiveTv),
        NavEntry("vod", "VOD", Icons.Filled.Movie),
        NavEntry("settings", "Settings", Icons.Filled.Settings)
    )
    Column(Modifier.fillMaxWidth().background(Bg)) {
        HLine()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            entries.forEach { e ->
                val active = e.route == current
                Column(
                    Modifier
                        .tvClick(RoundedCornerShape(12.dp)) {
                            nav.navigate(e.route) {
                                popUpTo(vm.homeRoute) { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(e.icon, null, tint = if (active) Cyan else Slate400, modifier = Modifier.size(22.dp))
                    Txt(e.label, 10, if (active) Cyan else Slate400, FontWeight.Bold, Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}

@Composable
fun PinDialog(title: String, onDismiss: () -> Unit, onConfirm: (String) -> String?) {
    var pin by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF121727),
        title = { Txt(title, 16, Color.White, FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { v -> if (v.length <= 4 && v.all { c -> c.isDigit() }) pin = v },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Cyan,
                        unfocusedBorderColor = Slate700,
                        cursorColor = Cyan
                    )
                )
                err?.let { Txt(it, 12, RedSoft, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            TextButton(onClick = { err = onConfirm(pin) }) { Txt("OK", 14, Cyan, FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Txt("Cancel", 14, Slate400) }
        }
    )
}
