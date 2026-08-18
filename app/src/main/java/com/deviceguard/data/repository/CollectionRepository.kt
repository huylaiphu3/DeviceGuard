package com.deviceguard.data.repository

import android.content.Context
import com.deviceguard.core.ConsentStore
import com.deviceguard.core.PermissionCatalog
import com.deviceguard.data.collector.DeviceInfoCollector
import com.deviceguard.data.collector.InstalledAppCollector
import com.deviceguard.data.collector.UsageStatsCollector
import com.deviceguard.data.local.DeviceGuardDatabase
import com.deviceguard.data.local.DeviceSnapshotEntity
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/** Kết quả một lượt thu thập — dùng cho thông báo và nhật ký chạy nền. */
data class CollectionResult(
    val snapshotId: Long,
    val appCount: Int,
    val usageRowCount: Int,
    val eventCount: Int,
    val skippedReason: String? = null
)

/**
 * Điều phối khâu 1 – Thu thập.
 *
 * Kiểm tra đồng ý TRƯỚC mọi thao tác đọc dữ liệu; thiếu quyền nào thì bỏ qua đúng
 * phần đó chứ không dừng cả lượt chạy.
 */
class CollectionRepository(
    private val context: Context,
    private val database: DeviceGuardDatabase,
    private val consentStore: ConsentStore
) {

    private val deviceInfoCollector = DeviceInfoCollector(context)
    private val installedAppCollector = InstalledAppCollector(context)

    suspend fun collectNow(): CollectionResult {
        if (!consentStore.hasAcceptedTerms.first()) {
            return CollectionResult(-1, 0, 0, 0, "Người dùng chưa đồng ý điều khoản")
        }

        val capturedAt = System.currentTimeMillis()
        val state = deviceInfoCollector.currentState()
        val apps = installedAppCollector.collect(capturedAt)

        val snapshotId = database.snapshotDao().insert(
            DeviceSnapshotEntity(
                capturedAt = capturedAt,
                batteryPercent = state.batteryPercent,
                batteryStatus = state.batteryStatus,
                batteryTempC = state.batteryTempC,
                isCharging = state.isCharging,
                storageTotalBytes = state.storageTotalBytes,
                storageFreeBytes = state.storageFreeBytes,
                ramTotalBytes = state.ramTotalBytes,
                ramAvailableBytes = state.ramAvailableBytes,
                networkType = state.networkType,
                isMetered = state.isMetered,
                installedAppCount = apps.size,
                userAppCount = apps.count { !it.isSystemApp }
            )
        )
        database.installedAppDao().insertAll(apps)

        var usageRows = 0
        var eventRows = 0
        if (PermissionCatalog.hasUsageStatsAccess(context)) {
            val usageCollector = UsageStatsCollector(context)
            val usage = usageCollector.collectDailyUsage()
            database.usageDao().upsertUsage(usage)
            usageRows = usage.size

            val lastEvent = database.usageDao().lastEventTimestamp()
            val since = lastEvent?.plus(1)
                ?: (capturedAt - TimeUnit.DAYS.toMillis(EVENT_BACKFILL_DAYS))
            val events = usageCollector.collectEvents(since)
            database.usageDao().insertEvents(events)
            eventRows = events.size
        }

        return CollectionResult(snapshotId, apps.size, usageRows, eventRows)
    }

    /** Xóa sạch dữ liệu đã thu thập — gắn với nút "Xóa toàn bộ dữ liệu" trong Cài đặt. */
    suspend fun wipeAll() {
        database.snapshotDao().clear()
        database.installedAppDao().clear()
        database.usageDao().clearUsage()
        database.usageDao().clearEvents()
        database.notificationLogDao().clear()
        database.recoveryDao().clear()
    }

    private companion object {
        const val EVENT_BACKFILL_DAYS = 3L
    }
}
