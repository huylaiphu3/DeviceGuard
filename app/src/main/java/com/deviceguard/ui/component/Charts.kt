package com.deviceguard.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Biểu đồ vẽ trực tiếp bằng Compose Canvas.
 *
 * Cố ý không dùng thư viện biểu đồ dựng trên View (MPAndroidChart): tránh lớp
 * interop View↔Compose và giữ toàn bộ giao diện trong một mô hình dựng hình duy
 * nhất. Số lượng biểu đồ ở đây ít và dạng đơn giản nên tự vẽ rẻ hơn phụ thuộc.
 */

data class BarDatum(val label: String, val value: Float, val highlight: Boolean = false)

@Composable
fun BarChart(
    data: List<BarDatum>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    highlightColor: Color = MaterialTheme.colorScheme.tertiary,
    height: androidx.compose.ui.unit.Dp = 160.dp,
    valueFormatter: (Float) -> String = { it.toInt().toString() }
) {
    if (data.isEmpty()) {
        EmptyChartPlaceholder(modifier)
        return
    }
    val maxValue = data.maxOf { it.value }.coerceAtLeast(1f)

    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
        ) {
            val slot = size.width / data.size
            val barWidth = slot * 0.6f
            data.forEachIndexed { index, datum ->
                val barHeight = (datum.value / maxValue) * size.height
                drawRect(
                    color = if (datum.highlight) highlightColor else barColor,
                    topLeft = Offset(
                        x = index * slot + (slot - barWidth) / 2f,
                        y = size.height - barHeight
                    ),
                    size = Size(barWidth, barHeight)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            data.forEach { datum ->
                Text(
                    text = datum.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 1
                )
            }
        }
        Text(
            text = "Cao nhất: ${valueFormatter(maxValue)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

data class LinePoint(val x: Long, val y: Float)

@Composable
fun LineChart(
    points: List<LinePoint>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.secondary,
    height: androidx.compose.ui.unit.Dp = 140.dp,
    yRange: ClosedFloatingPointRange<Float>? = null
) {
    if (points.size < 2) {
        EmptyChartPlaceholder(modifier)
        return
    }
    val minY = yRange?.start ?: points.minOf { it.y }
    val maxY = yRange?.endInclusive ?: points.maxOf { it.y }
    val span = (maxY - minY).takeIf { it > 0f } ?: 1f
    val minX = points.minOf { it.x }
    val maxX = points.maxOf { it.x }
    val spanX = (maxX - minX).takeIf { it > 0 } ?: 1L

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val path = Path()
        points.sortedBy { it.x }.forEachIndexed { index, point ->
            val x = ((point.x - minX).toFloat() / spanX) * size.width
            val y = size.height - ((point.y - minY) / span) * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = lineColor, style = Stroke(width = 4f))
    }
}

/** Thanh ngang xếp hạng — dùng cho bảng "ứng dụng dùng nhiều nhất". */
@Composable
fun RankedBar(
    label: String,
    valueText: String,
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            Text(text = valueText, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(4.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
        ) {
            drawRoundRect(
                color = color.copy(alpha = 0.15f),
                size = size,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
            )
            drawRoundRect(
                color = color,
                size = Size(size.width * fraction.coerceIn(0f, 1f), size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
            )
        }
    }
}

@Composable
private fun EmptyChartPlaceholder(modifier: Modifier = Modifier) {
    Text(
        text = "Chưa đủ dữ liệu để vẽ biểu đồ.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(vertical = 12.dp)
    )
}
