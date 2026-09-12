package jp.yagi.safecleaner.ui

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import java.util.Locale

@Composable
fun AppIcon(drawable: Drawable?, contentDescription: String, modifier: Modifier = Modifier) {
    Box(modifier.size(48.dp)) {
        if (drawable == null) {
            Icon(Icons.Outlined.Android, contentDescription, Modifier.size(48.dp))
        } else {
            val bitmap = remember(drawable) { drawable.toBitmap(96, 96).asImageBitmap() }
            Image(bitmap, contentDescription, Modifier.size(48.dp))
        }
    }
}

fun formatBytes(bytes: Long): String {
    val gib = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
    return if (gib >= 1) String.format(Locale.JAPAN, "%.1f GB", gib)
    else String.format(Locale.JAPAN, "%.0f MB", bytes / (1024.0 * 1024.0))
}
