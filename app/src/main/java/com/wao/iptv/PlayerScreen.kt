package com.wao.iptv

import android.app.Activity
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.navigation.NavController
import kotlinx.coroutines.delay

private fun hideSystemBars(activity: Activity?) {
    val window = activity?.window ?: return
    WindowCompat.setDecorFitsSystemWindows(window, false)
    val controller = WindowInsetsControllerCompat(window, window.decorView)
    controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
}

private fun showSystemBars(activity: Activity?) {
    val window = activity?.window ?: return
    WindowCompat.setDecorFitsSystemWindows(window, true)
    val controller = WindowInsetsControllerCompat(window, window.decorView)
    controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
}

@Composable
fun PlayerScreen(vm: AppViewModel, nav: NavController) {
    val item = vm.nowPlaying
    val ctx = LocalContext.current
    val activity = remember { ctx.findActivity() }
    val audioManager = remember { ctx.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager }
    val maxVol = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }

    if (item == null) {
        LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }

    val player = remember { buildPlayer(ctx, vm.settings.bufferMode) }
    val fb = remember { LiveFallback(player) }
    var playing by remember { mutableStateOf(true) }
    var buffering by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var resizeMode by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var showControls by remember { mutableStateOf(true) }
    var posMs by remember { mutableStateOf(0L) }
    var durMs by remember { mutableStateOf(0L) }
    var bufferedPct by remember { mutableStateOf(0) }
    var isLandscape by remember { mutableStateOf(false) }

    var brightness by remember { mutableStateOf(activity?.window?.attributes?.screenBrightness?.takeIf { it in 0f..1f } ?: 0.6f) }
    var volume by remember { mutableStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol) }
    var gestureLabel by remember { mutableStateOf<Pair<String, Int>?>(null) }

    val focusRequester = remember { FocusRequester() }

    fun saveHistory() {
        if (durMs > 0 || item.isLive) {
            vm.recordHistory(
                HistoryItem(item.historyKey, item.title, item.subtitle, item.poster, item.url, item.isLive, posMs, durMs)
            )
        }
    }

    // Enter/exit: fullscreen immersive mode + restore orientation on leave
    DisposableEffect(Unit) {
        hideSystemBars(activity)
        onDispose {
            showSystemBars(activity)
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Player listeners + release — set up once for the lifetime of this screen
    DisposableEffect(Unit) {
        fb.onFail = { e: PlaybackException -> errorMsg = "Stream chalane me masla hua: ${e.errorCodeName}" }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) errorMsg = null
            }
        }
        player.addListener(fb)
        player.addListener(listener)
        onDispose {
            player.removeListener(fb)
            player.removeListener(listener)
            player.release()
        }
    }

    // Load media whenever item changes (covers initial load AND Next/Previous)
    LaunchedEffect(item.url) {
        posMs = 0L
        durMs = 0L
        errorMsg = null
        fb.play(item.url, item.startPosition)
    }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            posMs = player.currentPosition.coerceAtLeast(0)
            durMs = player.duration.takeIf { it > 0 } ?: 0L
            bufferedPct = player.bufferedPercentage
        }
    }
    LaunchedEffect(showControls, playing) {
        if (showControls && playing) {
            delay(4000)
            showControls = false
        }
    }
    LaunchedEffect(gestureLabel) {
        if (gestureLabel != null) {
            delay(700)
            gestureLabel = null
        }
    }

    BackHandler { saveHistory(); nav.popBackStack() }

    fun toggleLandscape() {
        isLandscape = !isLandscape
        activity?.requestedOrientation = if (isLandscape)
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    fun togglePlayPause() {
        player.playWhenReady = !player.playWhenReady
        showControls = true
    }

    val canNext = vm.canGoNext()
    val canPrev = vm.canGoPrev()

    fun goNext() { saveHistory(); vm.goNext() }
    fun goPrev() { saveHistory(); vm.goPrev() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_SPACE -> {
                        togglePlayPause(); true
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY -> { player.playWhenReady = true; showControls = true; true }
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> { player.playWhenReady = false; showControls = true; true }
                    KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_CHANNEL_UP -> {
                        if (canNext) goNext(); true
                    }
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                        if (canPrev) goPrev(); true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (item.isLive) {
                            if (canNext) goNext()
                        } else {
                            player.seekTo((player.currentPosition + 10000).coerceAtLeast(0))
                            gestureLabel = "+10s" to 0
                        }
                        showControls = true
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (item.isLive) {
                            if (canPrev) goPrev()
                        } else {
                            player.seekTo((player.currentPosition - 10000).coerceAtLeast(0))
                            gestureLabel = "-10s" to 0
                        }
                        showControls = true
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_VOLUME_UP -> {
                        volume = (volume + 0.1f).coerceIn(0f, 1f)
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (volume * maxVol).toInt(), 0)
                        gestureLabel = "${(volume * 100).toInt()}%" to 2
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                        volume = (volume - 0.1f).coerceIn(0f, 1f)
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (volume * maxVol).toInt(), 0)
                        gestureLabel = "${(volume * 100).toInt()}%" to 2
                        true
                    }
                    KeyEvent.KEYCODE_BACK -> {
                        saveHistory(); nav.popBackStack(); true
                    }
                    else -> false
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { showControls = !showControls },
                    onDoubleTap = { offset ->
                        if (!item.isLive) {
                            val forward = offset.x > size.width / 2
                            val newPos = (player.currentPosition + if (forward) 10000 else -10000).coerceAtLeast(0)
                            player.seekTo(newPos)
                            gestureLabel = (if (forward) "+10s" else "-10s") to 0
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, dragAmount ->
                    change.consume()
                    val delta = -dragAmount / size.height
                    if (change.position.x < size.width / 2) {
                        brightness = (brightness + delta).coerceIn(0.02f, 1f)
                        activity?.window?.attributes = activity?.window?.attributes?.apply { screenBrightness = brightness }
                        gestureLabel = "${(brightness * 100).toInt()}%" to 1
                    } else {
                        volume = (volume + delta).coerceIn(0f, 1f)
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (volume * maxVol).toInt(), 0)
                        gestureLabel = "${(volume * 100).toInt()}%" to 2
                    }
                }
            }
    ) {
        VideoSurface(player, resizeMode, Modifier.fillMaxSize())

        if (buffering) {
            CircularProgressIndicator(color = Cyan, modifier = Modifier.align(Alignment.Center).size(36.dp))
        }

        errorMsg?.let { msg ->
            Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.ErrorOutline, null, tint = RedSoft, modifier = Modifier.size(32.dp))
                Txt(msg, 12, Color.White, modifier = Modifier.padding(top = 8.dp), align = androidx.compose.ui.text.style.TextAlign.Center)
                val btn = RoundedCornerShape(10.dp)
                Row(
                    Modifier.padding(top = 12.dp).background(Cyan, btn)
                        .clickable { errorMsg = null; fb.play(item.url) }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) { Txt("Dobara Try Karein", 12, Slate950, FontWeight.Bold) }
            }
        }

        gestureLabel?.let { (text, kind) ->
            val icon = when (kind) { 1 -> Icons.Filled.LightMode; 2 -> Icons.Filled.VolumeUp; else -> Icons.Filled.FastForward }
            Row(
                Modifier.align(Alignment.Center).background(Color(0xBF0A0E1A), RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null, tint = if (kind == 1) Amber else Cyan, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Txt(text, 14, Color.White, FontWeight.Bold)
            }
        }

        AnimatedVisibility(showControls, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Color(0x66000000))) {
                Row(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Color(0xBF0A0E1A)).padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            Modifier.size(36.dp).background(Color(0x1AFFFFFF), RoundedCornerShape(12.dp))
                                .clickable { saveHistory(); nav.popBackStack() },
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Filled.ArrowBack, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (item.isLive) Pill("LIVE", Red, Color.White)
                                Spacer(Modifier.width(6.dp))
                                Txt(item.title, 13, Color.White, FontWeight.Bold, maxLines = 1)
                            }
                            Txt(item.subtitle, 11, Cyan, maxLines = 1)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(36.dp).background(Color(0x1AFFFFFF), RoundedCornerShape(12.dp))
                                .clickable { toggleLandscape() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (isLandscape) Icons.Filled.StayCurrentPortrait else Icons.Filled.ScreenRotation,
                                null, tint = Cyan, modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Row(
                            Modifier.background(Color(0x1AFFFFFF), RoundedCornerShape(12.dp))
                                .clickable {
                                    resizeMode = if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT)
                                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Fullscreen, null, tint = Cyan, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Txt(if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) "Fit" else "Zoom", 11, Color.White, FontWeight.SemiBold)
                        }
                    }
                }

                Row(
                    Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(52.dp)
                            .background(if (canPrev) Color(0x33FFFFFF) else Color(0x14FFFFFF), CircleShape)
                            .then(if (canPrev) Modifier.clickable { goPrev() } else Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.SkipPrevious, null,
                            tint = if (canPrev) Color.White else Slate700,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Box(
                        Modifier
                            .size(68.dp)
                            .background(Cyan.copy(alpha = 0.2f), CircleShape)
                            .border(1.dp, Cyan.copy(alpha = 0.4f), CircleShape)
                            .clickable { togglePlayPause() }
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            null, tint = Cyan, modifier = Modifier.size(30.dp)
                        )
                    }

                    Box(
                        Modifier
                            .size(52.dp)
                            .background(if (canNext) Color(0x33FFFFFF) else Color(0x14FFFFFF), CircleShape)
                            .then(if (canNext) Modifier.clickable { goNext() } else Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.SkipNext, null,
                            tint = if (canNext) Color.White else Slate700,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xBF0A0E1A)).padding(16.dp)
                ) {
                    if (!item.isLive && durMs > 0) {
                        Slider(
                            value = posMs.toFloat().coerceIn(0f, durMs.toFloat()),
                            onValueChange = { player.seekTo(it.toLong()) },
                            valueRange = 0f..durMs.toFloat(),
                            colors = SliderDefaults.colors(thumbColor = Cyan, activeTrackColor = Cyan, inactiveTrackColor = Slate700),
                            modifier = Modifier.height(24.dp)
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Txt(fmtTime(posMs), 10, Slate300)
                            Txt(fmtTime(durMs), 10, Slate300)
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Txt("HEVC HW+", 11, Cyan, FontWeight.Bold)
                                Txt("  •  Buffer $bufferedPct%", 11, Slate300)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Wifi, null, tint = Emerald, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(4.dp))
                                Txt("Live", 11, Emerald, FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(Cyan.copy(alpha = 0.3f))) {
                            Box(Modifier.fillMaxSize().background(Cyan))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BackHandler(onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onBack)
}
