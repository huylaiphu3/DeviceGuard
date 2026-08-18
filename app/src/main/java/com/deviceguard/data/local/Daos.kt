package com.deviceguard.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SnapshotDao {
    @Insert
    suspend fun insert(snapshot: DeviceSnapshotEntity): Long

    @Query("SELECT * FROM device_snapshot ORDER BY capturedAt DESC LIMIT 1")
    fun observeLatest(): Flow<DeviceSnapshotEntity?>

    @Query("SELECT * FROM device_snapshot WHERE capturedAt >= :since ORDER BY capturedAt ASC")
    fun observeSince(since: Long): Flow<List<DeviceSnapshotEntity>>

    @Query("SELECT COUNT(*) FROM device_snapshot")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM device_snapshot")
    suspend fun clear()
}

@Dao
interface InstalledAppDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(apps: List<InstalledAppEntity>)

    @Query(
        """
        SELECT * FROM installed_app
        WHERE capturedAt = (SELECT MAX(capturedAt) FROM installed_app)
        ORDER BY label COLLATE NOCASE ASC
        """
    )
    fun observeLatestInventory(): Flow<List<InstalledAppEntity>>

    /** Gói xuất hiện ở lần chụp mới nhất nhưng không có ở lần chụp trước đó. */
    @Query(
        """
        SELECT * FROM installed_app
        WHERE capturedAt = (SELECT MAX(capturedAt) FROM installed_app)
          AND packageName NOT IN (
              SELECT packageName FROM installed_app
              WHERE capturedAt = (
                  SELECT MAX(capturedAt) FROM installed_app
                  WHERE capturedAt < (SELECT MAX(capturedAt) FROM installed_app)
              )
          )
          AND (SELECT COUNT(DISTINCT capturedAt) FROM installed_app) > 1
        ORDER BY label COLLATE NOCASE ASC
        """
    )
    fun observeNewlyInstalled(): Flow<List<InstalledAppEntity>>

    @Query("DELETE FROM installed_app")
    suspend fun clear()
}

@Dao
interface UsageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUsage(usage: List<AppUsageEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvents(events: List<UsageEventEntity>)

    @Query("SELECT * FROM app_usage WHERE dayStart >= :since ORDER BY dayStart ASC")
    fun observeUsageSince(since: Long): Flow<List<AppUsageEntity>>

    @Query(
        """
        SELECT packageName, MIN(dayStart) AS dayStart, SUM(foregroundTimeMs) AS foregroundTimeMs,
               SUM(launchCount) AS launchCount, MAX(lastTimeUsed) AS lastTimeUsed
        FROM app_usage
        WHERE dayStart >= :since
        GROUP BY packageName
        ORDER BY foregroundTimeMs DESC
        LIMIT :limit
        """
    )
    fun observeTopApps(since: Long, limit: Int): Flow<List<AppUsageEntity>>

    @Query("SELECT * FROM usage_event WHERE timestamp >= :since ORDER BY timestamp ASC")
    fun observeEventsSince(since: Long): Flow<List<UsageEventEntity>>

    @Query("SELECT MAX(timestamp) FROM usage_event")
    suspend fun lastEventTimestamp(): Long?

    @Query("DELETE FROM app_usage")
    suspend fun clearUsage()

    @Query("DELETE FROM usage_event")
    suspend fun clearEvents()
}

@Dao
interface NotificationLogDao {
    @Insert
    suspend fun insert(entry: NotificationLogEntity)

    @Query("SELECT * FROM notification_log ORDER BY postedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<NotificationLogEntity>>

    @Query("SELECT * FROM notification_log WHERE postedAt >= :since")
    fun observeSince(since: Long): Flow<List<NotificationLogEntity>>

    @Query("DELETE FROM notification_log")
    suspend fun clear()
}

@Dao
interface RecoveryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(candidates: List<RecoveryCandidateEntity>)

    @Query("SELECT * FROM recovery_candidate ORDER BY discoveredAt DESC, sizeBytes DESC")
    fun observeAll(): Flow<List<RecoveryCandidateEntity>>

    @Query("UPDATE recovery_candidate SET restoredAt = :restoredAt WHERE id = :id")
    suspend fun markRestored(id: Long, restoredAt: Long)

    @Query("DELETE FROM recovery_candidate WHERE restoredAt IS NULL")
    suspend fun clearUnrestored()

    @Query("DELETE FROM recovery_candidate")
    suspend fun clear()
}
