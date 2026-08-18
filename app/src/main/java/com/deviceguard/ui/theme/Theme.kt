package com.deviceguard.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Navy = Color(0xFF0B3D5C)
private val NavyLight = Color(0xFF3E6A8B)
private val Teal = Color(0xFF00796B)
private val Amber = Color(0xFFB26A00)

private val LightColors = lightColorScheme(
    primary = Navy,
    secondary = Teal,
    tertiary = Amber
)

private val DarkColors = darkColorScheme(
    primary = NavyLight,
    secondary = Color(0xFF4DB6AC),
    tertiary = Color(0xFFFFB74D)
)

/** Màu ngữ nghĩa dùng chung cho biểu đồ và nhãn mức độ tin cậy. */
object DeviceGuardColors {
    val positive = Color(0xFF2E7D32)
    val notice = Color(0xFFB26A00)
    val warning = Color(0xFFC62828)
    val chartSeries = listOf(
        Color(0xFF0B3D5C),
        Color(0xFF00796B),
        Color(0xFFB26A00),
        Color(0xFF6A1B9A),
        Color(0xFF00838F),
        Color(0xFF827717)
    )
}

@Composable
fun DeviceGuardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
