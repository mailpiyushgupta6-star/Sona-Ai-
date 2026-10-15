package com.example.androidapps

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean = false,
    val icon: Drawable? = null
) {
    /**
     * Converts the Android Drawable icon into a Compose ImageBitmap safely.
     * Works with AdaptiveIconDrawable, VectorDrawable, BitmapDrawable, or null without crashing.
     */
    fun toImageBitmap(sizePx: Int = 96): ImageBitmap? {
        val d = icon ?: return null
        return try {
            if (d is BitmapDrawable && d.bitmap != null) {
                d.bitmap.asImageBitmap()
            } else {
                val width = if (d.intrinsicWidth > 0) d.intrinsicWidth else sizePx
                val height = if (d.intrinsicHeight > 0) d.intrinsicHeight else sizePx
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                d.setBounds(0, 0, canvas.width, canvas.height)
                d.draw(canvas)
                bitmap.asImageBitmap()
            }
        } catch (_: Throwable) {
            null
        }
    }
}
