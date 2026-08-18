package com.deviceguard.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.deviceguard.data.analysis.Insight
import com.deviceguard.ui.component.IconStat
import com.deviceguard.ui.component.InsightRow
import com.deviceguard.ui.component.LineChart
import com.deviceguard.ui.component.LinePoint
import com.deviceguard.ui.component.SectionCard
import com.deviceguard.ui.component.StatRow
import com.deviceguard.ui.component.formatBytes
import com.deviceguard.ui.component.formatDateTime
import com.deviceguard.ui.component.formatDuration
import com.deviceguard.ui.viewmodel.OverviewViewModel

@Composable
fun DashboardScreen(viewModel: OverviewViewModel) {
    val snapshot by viewModel.latestSnapshot.collectAsState()
    val history by viewModel.snapshotHistory.collectAsState()
    val analysis by viewModel.analysis.collectAsState()
    val collecting by viewModel.collecting.collectAsState()
    val info = viewModel.staticDeviceInfo

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp)
    ) {
        SectionCard(
            title = "Thiết bị",
            subtitle = if (info.isEmulator) "Máy ảo (emulator) — số liệu phần cứng không phản ánh máy thật" else null
        ) {
            StatRow("Model", "${info.manufacturer} ${info.model}")
            StatRow("Android", "${info.androidRelease} (API ${info.sdkInt})")
            StatRow("Bản vá bảo mật", info.securityPatch)
            StatRow("Kiến trúc", info.supportedAbis.firstOrNull() ?: "—")
        }

        SectionCard(
            title = "Ảnh trạng thái mới nhất",
            subtitle = snapshot?.let { "Ghi lúc ${formatDateTime(it.capturedAt)}" }
                ?: "Chưa có dữ liệu — bấm nút bên dưới để thu thập lần đầu"
        ) {
            snapshot?.let { s ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    IconStat(Icons.Default.BatteryFull, "Pin", "${s.batteryPercent}%")
                    IconStat(
                        Icons.Default.Storage,
                        "Còn trống",
                        formatBytes(s.storageFreeBytes)
                    )
                    IconStat(
                        Icons.Default.Memory,
                        "RAM trống",
                        formatBytes(s.ramAvailableBytes)
                    )
                    IconStat(Icons.Default.Apps, "Ứng dụng", "${s.userAppCount}")
                }
                Spacer(Modifier.height(8.dp))
                StatRow("Trạng thái pin", s.batteryStatus)
                StatRow("Nhiệt độ pin", "%.1f °C".format(s.batteryTempC))
                StatRow("Kết nối", "${s.networkType}${if (s.isMetered) " (tính phí)" else ""}")
                StatRow(
                    "Dung lượng",
                    "${formatBytes(s.storageTotalBytes - s.storageFreeBytes)} / ${formatBytes(s.storageTotalBytes)}"
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = viewModel::collectNow,
                enabled = !collecting,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (collecting) {
                    CircularProgressIndicator(Modifier.height(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.height(0.dp))
                    Text("  Đang thu thập…")
                } else {
                    Text("Thu thập ngay")
                }
            }
        }

        if (history.size >= 2) {
            SectionCard(
                title = "Mức pin theo thời gian",
                subtitle = "${history.size} ảnh trạng thái trong 7 ngày gần nhất"
            ) {
                LineChart(
                    points = history.map { LinePoint(it.capturedAt, it.batteryPercent.toFloat()) },
                    yRange = 0f..100f
                )
            }
        }

        analysis?.let { result ->
            SectionCard(
                title = "Tổng quan sử dụng (7 ngày)",
                subtitle = if (!viewModel.hasUsageAccess) {
                    "Chưa cấp quyền truy cập thống kê sử dụng — mục này sẽ trống"
                } else {
                    null
                }
            ) {
                StatRow("Tổng thời gian tiền cảnh", formatDuration(result.totalScreenTimeMs))
                StatRow("Trung bình mỗi ngày", formatDuration(result.dailyAverageMs))
                StatRow("Số lần bật màn hình (ước lượng)", "${result.unlockEstimate}")
                StatRow("Tỉ lệ phiên ban đêm (22h–6h)", "%.0f%%".format(result.nightUsageRatio * 100))
            }

            if (result.insights.isNotEmpty()) {
                SectionCard(
                    title = "Quan sát đáng chú ý",
                    subtitle = "Suy ra từ dữ liệu đã thu thập — không phải đánh giá con người"
                ) {
                    result.insights.forEach { InsightRow(it) }
                }
            } else {
                SectionCard(title = "Quan sát đáng chú ý") {
                    InsightRow(
                        Insight(
                            Insight.Severity.INFO,
                            "Chưa phát hiện bất thường",
                            "Cần ít nhất vài ngày dữ liệu để các phép so sánh có ý nghĩa."
                        )
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
