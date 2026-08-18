package com.deviceguard.data.analysis

import com.deviceguard.data.local.AppUsageEntity
import com.deviceguard.data.local.DeviceSnapshotEntity
import com.deviceguard.data.local.InstalledAppEntity
import com.deviceguard.data.local.NotificationLogEntity
import com.deviceguard.data.local.UsageEventEntity
import java.util.Calendar
import java.util.concurrent.TimeUnit

data class DailyUsagePoint(val dayStart: Long, val totalMs: Long)

data class HourlyBucket(val hour: Int, val sessions: Int)

data class AppUsageSummary(
    val packageName: String,
    val label: String,
    val totalMs: Long,
    val launchCount: Int,
    val shareOfTotal: Float
)

data class BatteryTrendPoint(val timestamp: Long, val percent: Int, val isCharging: Boolean)

/** Một quan sát đáng chú ý rút ra từ dữ liệu, kèm bằng chứng để người dùng tự kiểm. */
data class Insight(
    val severity: Severity,
    val title: String,
    val detail: String
) {
    enum class Severity { INFO, NOTICE, WARNING }
}

data class UsageAnalysis(
    val dailyUsage: List<DailyUsagePoint>,
    val hourlyDistribution: List<HourlyBucket>,
    val topApps: List<AppUsageSummary>,
    val totalScreenTimeMs: Long,
    val dailyAverageMs: Long,
    val nightUsageRatio: Float,
    val unlockEstimate: Int,
    val insights: List<Insight>
)

/**
 * Khâu 3 – Phân tích.
 *
 * Toàn bộ phép tính chạy trên dữ liệu đã nằm trong Room, không gọi mạng. Mọi kết
 * luận đều là thống kê mô tả có thể kiểm chứng lại từ dữ liệu thô — cố ý tránh
 * suy đoán kiểu "chấm điểm hành vi".
 */
class UsageAnalyzer {

    fun analyze(
        usage: List<AppUsageEntity>,
        events: List<UsageEventEntity>,
        installedApps: List<InstalledAppEntity>,
        notifications: List<NotificationLogEntity>
    ): UsageAnalysis {
        val labels = installedApps.associate { it.packageName to it.label }

        val daily = usage.groupBy { it.dayStart }
            .map { (day, entries) -> DailyUsagePoint(day, entries.sumOf { it.foregroundTimeMs }) }
            .sortedBy { it.dayStart }

        val totalMs = daily.sumOf { it.totalMs }
        val dailyAverage = if (daily.isNotEmpty()) totalMs / daily.size else 0L

        val resumeEvents = events.filter { it.eventType == "resumed" }
        val hourly = (0..23).map { hour ->
            HourlyBucket(hour, resumeEvents.count { hourOf(it.timestamp) == hour })
        }

        val topApps = usage.groupBy { it.packageName }
            .map { (pkg, entries) ->
                val appTotal = entries.sumOf { it.foregroundTimeMs }
                AppUsageSummary(
                    packageName = pkg,
                    label = labels[pkg] ?: pkg,
                    totalMs = appTotal,
                    launchCount = entries.sumOf { it.launchCount },
                    shareOfTotal = if (totalMs > 0) appTotal.toFloat() / totalMs else 0f
                )
            }
            .sortedByDescending { it.totalMs }
            .take(TOP_APP_LIMIT)

        val nightSessions = resumeEvents.count { isNightHour(hourOf(it.timestamp)) }
        val nightRatio = if (resumeEvents.isNotEmpty()) {
            nightSessions.toFloat() / resumeEvents.size
        } else {
            0f
        }

        val unlocks = events.count { it.eventType == "screen_on" }

        return UsageAnalysis(
            dailyUsage = daily,
            hourlyDistribution = hourly,
            topApps = topApps,
            totalScreenTimeMs = totalMs,
            dailyAverageMs = dailyAverage,
            nightUsageRatio = nightRatio,
            unlockEstimate = unlocks,
            insights = buildInsights(
                daily, topApps, nightRatio, installedApps, usage, notifications
            )
        )
    }

    fun batteryTrend(snapshots: List<DeviceSnapshotEntity>): List<BatteryTrendPoint> =
        snapshots.sortedBy { it.capturedAt }
            .map { BatteryTrendPoint(it.capturedAt, it.batteryPercent, it.isCharging) }

    private fun buildInsights(
        daily: List<DailyUsagePoint>,
        topApps: List<AppUsageSummary>,
        nightRatio: Float,
        installedApps: List<InstalledAppEntity>,
        usage: List<AppUsageEntity>,
        notifications: List<NotificationLogEntity>
    ): List<Insight> = buildList {
        if (daily.size >= 2) {
            val latest = daily.last().totalMs
            val previousAverage = daily.dropLast(1).map { it.totalMs }.average()
            if (previousAverage > 0 && latest > previousAverage * SPIKE_FACTOR) {
                add(
                    Insight(
                        severity = Insight.Severity.NOTICE,
                        title = "Thời gian dùng máy hôm nay tăng đột biến",
                        detail = "%.1f giờ so với trung bình %.1f giờ của các ngày trước.".format(
                            latest.toHours(), previousAverage.toLong().toHours()
                        )
                    )
                )
            }
        }

        topApps.firstOrNull()?.takeIf { it.shareOfTotal > DOMINANT_SHARE }?.let { app ->
            add(
                Insight(
                    severity = Insight.Severity.INFO,
                    title = "${app.label} chiếm phần lớn thời gian",
                    detail = "%.0f%% tổng thời gian tiền cảnh trong kỳ được ghi nhận.".format(
                        app.shareOfTotal * 100
                    )
                )
            )
        }

        if (nightRatio > NIGHT_RATIO_THRESHOLD) {
            add(
                Insight(
                    severity = Insight.Severity.NOTICE,
                    title = "Nhiều phiên dùng máy vào ban đêm",
                    detail = "%.0f%% số lần mở ứng dụng rơi vào khung 22h–6h.".format(
                        nightRatio * 100
                    )
                )
            )
        }

        // Ứng dụng giữ quyền nguy hiểm nhưng người dùng gần như không mở — đáng để
        // rà lại quyền, không kết luận là độc hại.
        val usedPackages = usage.filter { it.foregroundTimeMs > 0 }.map { it.packageName }.toSet()
        val dormantSensitive = installedApps
            .filter { !it.isSystemApp }
            .filter { it.grantedDangerousPermissions.split(",").count { p -> p.isNotBlank() } >= DORMANT_PERMISSION_MIN }
            .filterNot { it.packageName in usedPackages }
        if (dormantSensitive.isNotEmpty()) {
            add(
                Insight(
                    severity = Insight.Severity.WARNING,
                    title = "${dormantSensitive.size} ứng dụng giữ nhiều quyền nhạy cảm nhưng không được dùng",
                    detail = dormantSensitive.take(3).joinToString(", ") { it.label } +
                        if (dormantSensitive.size > 3) "…" else ""
                )
            )
        }

        if (notifications.isNotEmpty()) {
            val noisiest = notifications.groupingBy { it.packageName }.eachCount()
                .maxByOrNull { it.value }
            if (noisiest != null && noisiest.value >= NOISY_NOTIFICATION_MIN) {
                add(
                    Insight(
                        severity = Insight.Severity.INFO,
                        title = "Nguồn thông báo nhiều nhất",
                        detail = "${noisiest.key}: ${noisiest.value} thông báo trong kỳ."
                    )
                )
            }
        }
    }

    private fun hourOf(timestamp: Long): Int = Calendar.getInstance().apply {
        timeInMillis = timestamp
    }.get(Calendar.HOUR_OF_DAY)

    private fun isNightHour(hour: Int) = hour >= 22 || hour < 6

    private fun Long.toHours(): Double = this.toDouble() / TimeUnit.HOURS.toMillis(1)

    private companion object {
        const val TOP_APP_LIMIT = 10
        const val SPIKE_FACTOR = 1.5
        const val DOMINANT_SHARE = 0.4f
        const val NIGHT_RATIO_THRESHOLD = 0.2f
        const val DORMANT_PERMISSION_MIN = 3
        const val NOISY_NOTIFICATION_MIN = 20
    }
}
