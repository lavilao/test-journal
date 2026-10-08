package com.example.ui.components

import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.DeviceFileInfo

/**
 * REAL icons for device content — the recent-apps list and file results used
 * generic Material glyphs, which looked unfinished:
 *  - [AppIconImage] renders the app's actual launcher icon.
 *  - [FileIconImage] renders a type-aware icon, with image thumbnails.
 */
@Composable
fun AppIconImage(
    packageName: String,
    modifier: Modifier = Modifier,
    sizeDp: Int = 36
) {
    val context = LocalContext.current
    val bitmap = remember(packageName) {
        try {
            val pm = context.packageManager
            val drawable: Drawable = pm.getApplicationIcon(packageName)
            drawableToBitmap(drawable, 128)
        } catch (_: Exception) {
            null
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = modifier.size(sizeDp.dp).clip(RoundedCornerShape(8.dp))
        )
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .size(sizeDp.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
        ) {
            Icon(
                Icons.Default.Android,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size((sizeDp * 0.55f).dp)
            )
        }
    }
}

private fun drawableToBitmap(drawable: Drawable, sizePx: Int): android.graphics.Bitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        return drawable.bitmap
    }
    val bmp = android.graphics.Bitmap.createBitmap(sizePx, sizePx, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    drawable.setBounds(0, 0, sizePx, sizePx)
    drawable.draw(canvas)
    return bmp
}

/**
 * Type-aware icon for MediaStore files: thumbnails for images, a distinct
 * glyph + tint per type (PDF red, APK green, audio, video, archives,
 * text) — no more one-size-fits-all "insert drive file" look.
 */
@Composable
fun FileIconImage(
    file: DeviceFileInfo,
    modifier: Modifier = Modifier,
    sizeDp: Int = 36
) {
    val mime = file.mimeType.lowercase()
    val name = file.displayName.lowercase()

    // Images: real thumbnail straight from the content URI.
    if (mime.startsWith("image/")) {
        AsyncImage(
            model = file.uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(sizeDp.dp).clip(RoundedCornerShape(8.dp))
        )
        return
    }

    val (icon, tint) = when {
        mime == "application/pdf" || name.endsWith(".pdf") ->
            Icons.Default.PictureAsPdf to Color(0xFFEA4335)
        mime == "application/vnd.android.package-archive" || name.endsWith(".apk") ->
            Icons.Default.Android to Color(0xFF34A853)
        mime.startsWith("audio/") ->
            Icons.Default.AudioFile to Color(0xFF34A853)
        mime.startsWith("video/") ->
            Icons.Default.PlayCircle to Color(0xFF4285F4)
        mime.contains("zip") || mime.contains("rar") || mime.contains("7z") ||
            name.endsWith(".zip") || name.endsWith(".rar") || name.endsWith(".7z") ->
            Icons.Default.FolderZip to Color(0xFFFB9C0D)
        mime.startsWith("text/") || mime.contains("officedocument") ||
            mime.contains("msword") || name.endsWith(".txt") || name.endsWith(".md") ||
            name.endsWith(".doc") || name.endsWith(".docx") || name.endsWith(".xls") ||
            name.endsWith(".xlsx") || name.endsWith(".ppt") || name.endsWith(".pptx") ->
            Icons.Default.Description to Color(0xFF4285F4)
        else ->
            Icons.Default.InsertDriveFile to Color(0xFF5F6368)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(sizeDp.dp)
            .background(tint.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size((sizeDp * 0.55f).dp)
        )
    }
}
