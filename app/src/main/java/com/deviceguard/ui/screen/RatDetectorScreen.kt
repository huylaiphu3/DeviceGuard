package com.deviceguard.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deviceguard.data.analysis.RatDetector
import com.deviceguard.ui.component.SectionCard
import com.deviceguard.ui.theme.DeviceGuardColors
import com.deviceguard.ui.viewmodel.RatViewModel

/**
 * Khâu 3 – Phân tích (nhánh an ninh): trình bày kết quả [RatDetector].
 *
 * Chủ ý diễn đạt thận trọng — đây là danh sách GỢI Ý cần rà lại, kèm bằng chứng, chứ
 * không phải phán quyết "đây là mã độc". Người dùng tự đối chiếu và quyết định.
 */
@Composable
fun RatDetectorScreen(viewModel: RatViewModel) {
    val report by viewModel.report.collectAsState()

    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(vertical = 12.dp)
    ) {
        item {
            SectionCard(
                title = "Rà soát dấu hiệu điều khiển từ xa (RAT/spyware)",
                subtitle = "Đối chiếu ${report.scannedApps} ứng dụng người dùng với các đặc trưng " +
                    "của phần mềm theo dõi. Kết quả là gợi ý cần xem lại, không phải kết luận."
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RiskCountChip("Rủi ro cao", report.highCount, DeviceGuardColors.warning)
                    RiskCountChip("Cần chú ý", report.mediumCount, DeviceGuardColors.notice)
                }
            }
        }

        if (report.scannedApps == 0) {
            item {
                SectionCard(title = "Chưa có dữ liệu") {
                    Text("Hãy bấm \"Thu thập ngay\" ở tab Tổng quan để kiểm kê ứng dụng trước.")
                }
            }
        } else if (report.findings.isEmpty()) {
            item {
                SectionCard(title = "Không thấy dấu hiệu đáng ngờ") {
                    Text(
                        "Không ứng dụng người dùng nào trúng tín hiệu RAT nào. Vẫn nên rà lại " +
                            "định kỳ sau mỗi lần cài app mới.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        items(report.findings, key = { it.packageName }) { finding -> FindingCard(finding) }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun RiskCountChip(label: String, count: Int, color: Color) {
    Surface(color = color.copy(alpha = 0.12f), shape = MaterialTheme.shapes.small) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text("$count", style = MaterialTheme.typography.titleLarge, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}

@Composable
private fun FindingCard(finding: RatDetector.Finding) {
    val levelColor = when (finding.level) {
        RatDetector.Finding.Level.HIGH -> DeviceGuardColors.warning
        RatDetector.Finding.Level.MEDIUM -> DeviceGuardColors.notice
        RatDetector.Finding.Level.LOW -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val levelLabel = when (finding.level) {
        RatDetector.Finding.Level.HIGH -> "RỦI RO CAO"
        RatDetector.Finding.Level.MEDIUM -> "CẦN CHÚ Ý"
        RatDetector.Finding.Level.LOW -> "THẤP"
    }

    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    finding.label,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Surface(color = levelColor.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
                    Text(
                        "$levelLabel · ${finding.score}đ",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = levelColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Text(
                finding.packageName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            finding.indicators.forEach { indicator ->
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text(
                        "• ${indicator.title}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = levelColor
                    )
                    Text(
                        indicator.evidence,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }
        }
    }
}
