package com.wao.iptv

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.view.Gravity
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import java.util.Locale

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

fun isTvMode(ctx: Context, mode: Int): Boolean {
    if (mode == 1) return false
    if (mode == 2) return true
    val ui = ctx.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
    return ui.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
}

fun fmtInt(n: Int): String = String.format(Locale.US, "%,d", n)

fun fmtCount(n: Int): String =
    if (n >= 1000) String.format(Locale.US, "%.1fk", n / 1000.0) else n.toString()

fun fmtTime(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
    else String.format(Locale.US, "%02d:%02d", m, sec)
}

fun buildPlayer(context: Context, bufferMode: Int): ExoPlayer {
    val ds = OkHttpDataSource.Factory(httpClient).setUserAgent(USER_AGENT)
    val cfg = when (bufferMode) {
        0 -> intArrayOf(8_000, 20_000, 1_000, 2_000)
        2 -> intArrayOf(30_000, 90_000, 5_000, 10_000)
        else -> intArrayOf(15_000, 50_000, 2_500, 5_000)
    }
    val lc = DefaultLoadControl.Builder()
        .setBufferDurationsMs(cfg[0], cfg[1], cfg[2], cfg[3])
        .build()
    return ExoPlayer.Builder(context, DefaultRenderersFactory(context).setEnableDecoderFallback(true))
        .setMediaSourceFactory(DefaultMediaSourceFactory(ds))
        .setLoadControl(lc)
        .build()
}

class LiveFallback(private val player: ExoPlayer) : Player.Listener {
    private var currentUrl = ""
    private var tried = false
    var onFail: ((PlaybackException) -> Unit)? = null

    fun play(url: String, startMs: Long = 0L) {
        currentUrl = url
        tried = false
        if (startMs > 0L) player.setMediaItem(MediaItem.fromUri(url), startMs)
        else player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.playWhenReady = true
    }

    override fun onPlayerError(error: PlaybackException) {
        if (!tried && currentUrl.endsWith(".m3u8")) {
            tried = true
            player.setMediaItem(MediaItem.fromUri(currentUrl.removeSuffix(".m3u8") + ".ts"))
            player.prepare()
            player.playWhenReady = true
        } else {
            onFail?.invoke(error)
        }
    }
}

@Composable
fun VideoSurface(player: ExoPlayer, resizeMode: Int, modifier: Modifier = Modifier) {
    val frameRef = remember { arrayOfNulls<AspectRatioFrameLayout>(1) }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.height > 0) {
                    frameRef[0]?.setAspectRatio(
                        videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                    )
                }
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val container = FrameLayout(ctx)
            val frame = AspectRatioFrameLayout(ctx)
            val sv = SurfaceView(ctx)
            frame.addView(
                sv,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
            container.addView(
                frame,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER
                )
            )
            frameRef[0] = frame
            player.setVideoSurfaceView(sv)
            val vs = player.videoSize
            if (vs.height > 0) frame.setAspectRatio(vs.width * vs.pixelWidthHeightRatio / vs.height)
            container
        },
        update = { _ -> frameRef[0]?.resizeMode = resizeMode }
    )
}
