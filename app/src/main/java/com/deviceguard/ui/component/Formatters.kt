package com.deviceguard.ui.component

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private val vietnam = Locale("vi", "VN")
private val dateTimeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", vietnam)
private val dateFormat = SimpleDateFormat("dd/MM", vietnam)

fun formatDateTime(timestamp: Long): String = dateTimeFormat.format(Date(timestamp))

fun formatShortDate(timestamp: Long): String = dateFormat.format(Date(timestamp))

fun formatDuration(millis: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    return when {
        hours > 0 -> "${hours}g ${minutes}p"
        minutes > 0 -> "${minutes}p"
        else -> "${TimeUnit.MILLISECONDS.toSeconds(millis)}s"
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }
    return String.format(vietnam, "%.1f %s", value, units[index])
}

fun formatPercent(fraction: Float): String = String.format(vietnam, "%.0f%%", fraction * 100)
