package com.deviceguard.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        DeviceSnapshotEntity::class,
        InstalledAppEntity::class,
        AppUsageEntity::class,
        UsageEventEntity::class,
        NotificationLogEntity::class,
        RecoveryCandidateEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class DeviceGuardDatabase : RoomDatabase() {

    abstract fun snapshotDao(): SnapshotDao
    abstract fun installedAppDao(): InstalledAppDao
    abstract fun usageDao(): UsageDao
    abstract fun notificationLogDao(): NotificationLogDao
    abstract fun recoveryDao(): RecoveryDao

    companion object {
        @Volatile
        private var instance: DeviceGuardDatabase? = null

        fun get(context: Context): DeviceGuardDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    DeviceGuardDatabase::class.java,
                    "deviceguard.db"
                ).build().also { instance = it }
            }
    }
}
