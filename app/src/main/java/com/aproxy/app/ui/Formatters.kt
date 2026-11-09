package com.aproxy.app.ui

import java.util.Locale

fun formatSpeed(bytesPerSecond: Long): String {
    val value = bytesPerSecond.coerceAtLeast(0L).toDouble()
    return when {
        value < 1024 -> "${bytesPerSecond.coerceAtLeast(0L)} B/s"
        value < 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f KB/s", value / 1024)
        value < 1024L * 1024 * 1024 ->
            String.format(Locale.getDefault(), "%.1f MB/s", value / (1024 * 1024))

        else -> String.format(
            Locale.getDefault(),
            "%.2f GB/s",
            value / (1024L * 1024 * 1024),
        )
    }
}

fun formatBytes(bytes: Long): String {
    val value = bytes.coerceAtLeast(0L).toDouble()
    return when {
        value < 1024 -> "${bytes.coerceAtLeast(0L)} B"
        value < 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f KB", value / 1024)
        value < 1024L * 1024 * 1024 ->
            String.format(Locale.getDefault(), "%.1f MB", value / (1024 * 1024))

        else -> String.format(
            Locale.getDefault(),
            "%.2f GB",
            value / (1024L * 1024 * 1024),
        )
    }
}
