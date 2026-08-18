package com.deviceguard.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.deviceguard.ui.component.BarChart
import com.deviceguard.ui.component.BarDatum
import com.deviceguard.ui.component.RankedBar
import com.deviceguard.ui.component.SectionCard
import com.deviceguard.ui.component.formatDuration
import com.deviceguard.ui.component.formatPercent
import com.deviceguard.ui.component.formatShortDate
import com.deviceguard.ui.theme.DeviceGuardColors
import com.deviceguard.ui.viewmodel.OverviewViewModel
import java.util.concurrent.TimeUnit

/** Khâu 3 – Phân tích, trình bày dưới dạng biểu đồ. */
@Composable
fun UsageScreen(viewModel: OverviewViewModel) {
    val analysis by viewModel.analysis.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp)
    ) {
        if (!viewModel.hasUsageAccess) {
            SectionCard(
                title = "Chưa có quyền thống kê sử dụng",
                subtitle = "Vào tab Cài đặt → Truy cập thống kê sử dụng để bật."
            ) {
                Text(
                    "Không có quyền này, Android không cung cấp thời lượng dùng ứng dụng, " +
                        "nên toàn bộ biểu đồ trong màn hình này sẽ trống.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        val result = analysis
        if (result == null) {
            SectionCard(title = "Đang tính toán…") { Text("Chưa có dữ liệu.") }
            return@Column
        }

        SectionCard(
            title = "Thời gian dùng theo ngày",
            subtitle = "Đơn vị: giờ tiền cảnh"
        ) {
            BarChart(
                data = result.dailyUsage.map { point ->
                    BarDatum(
                        label = formatShortDate(point.dayStart),
                        value = point.totalMs.toFloat() / TimeUnit.HOURS.toMillis(1),
                        highlight = point == result.dailyUsage.lastOrNull()
                    )
                },
                valueFormatter = { "%.1f giờ".format(it) }
            )
        }

        SectionCard(
            title = "Phân bố theo giờ trong ngày",
            subtitle = "Số lần đưa ứng dụng ra tiền cảnh, gộp cả kỳ"
        ) {
            BarChart(
                data = result.hourlyDistribution.map { bucket ->
                    BarDatum(
                        label = if (bucket.hour % 3 == 0) "${bucket.hour}" else "",
                        value = bucket.sessions.toFloat(),
                        highlight = bucket.hour >= 22 || bucket.hour < 6
                    )
                },
                valueFormatter = { "${it.toInt()} lần" }
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Cột màu nhấn là khung 22h–6h.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SectionCard(
            title = "Ứng dụng dùng nhiều nhất",
            subtitle = "Xếp theo tổng thời gian tiền cảnh trong 7 ngày"
        ) {
            if (result.topApps.isEmpty()) {
                Text("Chưa ghi nhận được thời lượng sử dụng nào.")
            } else {
                result.topApps.forEachIndexed { index, app ->
                    RankedBar(
                        label = app.label,
                        valueText = "${formatDuration(app.totalMs)} • ${formatPercent(app.shareOfTotal)}",
                        fraction = app.shareOfTotal,
                        color = DeviceGuardColors.chartSeries[index % DeviceGuardColors.chartSeries.size]
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
