package com.wao.iptv

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

// Device ID ka QR (andar sirf ID likhi hoti hai)
fun makeQrBitmap(text: String, size: Int): Bitmap {
    val hints = mapOf<EncodeHintType, Any>(EncodeHintType.MARGIN to 2, EncodeHintType.CHARACTER_SET to "UTF-8")
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
    val px = IntArray(size * size)
    for (y in 0 until size) for (x in 0 until size) {
        px[y * size + x] = if (m.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    }
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    bmp.setPixels(px, 0, size, 0, 0, size, size)
    return bmp
}

// WhatsApp par bhejne wali image: QR + neeche ID ka text
private fun makeShareBitmap(id: String): Bitmap {
    val qr = makeQrBitmap(id, 640)
    val out = Bitmap.createBitmap(640, 760, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    c.drawColor(0xFFFFFFFF.toInt())
    c.drawBitmap(qr, 0f, 0f, null)
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF000000.toInt()
        textSize = 44f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    c.drawText(id, 320f, 700f, p)
    return out
}

private fun saveToGallery(ctx: Context, bmp: Bitmap, id: String): Uri? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
    return try {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "WAO_DeviceID_$id.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/WaoIPTV")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        ctx.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        uri
    } catch (e: Exception) { null }
}

fun shareDeviceIdQr(ctx: Context, id: String) {
    try {
        val uri = saveToGallery(ctx, makeShareBitmap(id), id)
        val send = Intent(Intent.ACTION_SEND).apply {
            if (uri != null) {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                type = "text/plain"
            }
            putExtra(Intent.EXTRA_TEXT, "Wao IPTV Device ID: $id")
        }
        try {
            ctx.startActivity(Intent(send).setPackage("com.whatsapp").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            ctx.startActivity(Intent.createChooser(send, "Share Device ID").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    } catch (e: Exception) { }
}

@Composable
fun DeviceQrCard(deviceId: String) {
    val ctx = LocalContext.current
    val bmp = remember(deviceId) { runCatching { makeQrBitmap(deviceId, 520) }.getOrNull() }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Device ID QR",
                modifier = Modifier.size(190.dp).clip(RoundedCornerShape(14.dp))
            )
            Spacer(Modifier.height(12.dp))
        }
        val shape = RoundedCornerShape(14.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 46.dp)
                .background(Brush.horizontalGradient(listOf(Emerald, Cyan)), shape)
                .tvClick(shape) { shareDeviceIdQr(ctx, deviceId) }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Share, null, tint = Slate950, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Txt("SHARE QR ON WHATSAPP", 12, Slate950, FontWeight.Black)
        }
    }
}
