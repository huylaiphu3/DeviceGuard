package com.deviceguard.data.collector

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.deviceguard.data.local.AppUsageEntity
import com.deviceguard.data.local.UsageEventEntity
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Khâu 1 – Thu thập: nhật ký sử dụng ứng dụng qua [UsageStatsManager].
 *
 * Hai nguồn bổ sung cho nhau:
 *  - queryUsageStats: tổng thời lượng tiền cảnh theo khoảng, dùng cho bảng xếp hạng.
 *  - queryEvents: từng sự kiện chuyển tiền cảnh kèm mốc thời gian, dùng để dựng
 *    biểu đồ phân bố theo giờ và đếm số lần mở thực tế.
 */
class UsageStatsCollector(private val context: Context) {

    private val usm: UsageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    /** Tổng hợp theo ngày cho [days] ngày gần nhất. */
    fun collectDailyUsage(days: Int = DEFAULT_DAYS): List<AppUsageEntity> {
        val result = mutableListOf<AppUsageEntity>()
        var dayStart = startOfDay(System.currentTimeMillis()) - TimeUnit.DAYS.toMillis((days - 1).toLong())
        val now = System.currentTimeMillis()

        while (dayStart <= now) {
            val dayEnd = minOf(dayStart + TimeUnit.DAYS.toMillis(1), now)
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, dayStart, dayEnd)
            val launchCounts = countLaunches(dayStart, dayEnd)

            stats?.filter { it.totalTimeInForeground > 0 }
                ?.groupBy { it.packageName }
                ?.forEach { (pkg, entries) ->
                    result += AppUsageEntity(
                        packageName = pkg,
                        dayStart = dayStart,
                        foregroundTimeMs = entries.sumOf { it.totalTimeInForeground },
                        launchCount = launchCounts[pkg] ?: 0,
                        lastTimeUsed = entries.maxOf { it.lastTimeUsed }
                    )
                }
            dayStart += TimeUnit.DAYS.toMillis(1)
        }
        return result
    }

    /**
     * Sự kiện thô kể từ [since]. Hệ thống chỉ giữ lịch sử sự kiện vài ngày,
     * nên ứng dụng lưu lại vào Room để dựng chuỗi thời gian dài hơn.
     */
    fun collectEvents(since: Long): List<UsageEventEntity> {
        val now = System.currentTimeMillis()
        if (since >= now) return emptyList()

        val events = usm.queryEvents(since, now)
        val out = mutableListOf<UsageEventEntity>()
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val type = when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> "resumed"
                UsageEvents.Event.ACTIVITY_PAUSED -> "paused"
                UsageEvents.Event.SCREEN_INTERACTIVE -> "screen_on"
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> "screen_off"
                else -> continue
            }
            out += UsageEventEntity(
                packageName = event.packageName ?: continue,
                timestamp = event.timeStamp,
                eventType = type
            )
        }
        return out
    }

    private fun countLaunches(from: Long, to: Long): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        val events = usm.queryEvents(from, to)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                val pkg = event.packageName ?: continue
                counts[pkg] = (counts[pkg] ?: 0) + 1
            }
        }
        return counts
    }

    private fun startOfDay(timestamp: Long): Long = Calendar.getInstance().apply {
        timeInMillis = timestamp
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    companion object {
        const val DEFAULT_DAYS = 7
    }
}
