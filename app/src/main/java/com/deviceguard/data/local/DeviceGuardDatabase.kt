package com.deviceguard.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        DeviceSnapshotEntity::class,
        InstalledAppEntity::class,
        AppUsageEntity::class,
        UsageEventEntity::class,
        NotificationLogEntity::class,
        RecoveryCandidateEntity::class
    ],
    version = 2,
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

        /**
         * v1 → v2: bổ sung 3 cột cho module RatDetector. Giữ nguyên dữ liệu đã thu thập,
         * điền giá trị mặc định trung tính cho các ảnh kiểm kê cũ (chưa có 3 tín hiệu này).
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE installed_app ADD COLUMN hasLauncherIcon INTEGER NOT NULL DEFAULT 1"
                )
                db.execSQL(
                    "ALTER TABLE installed_app ADD COLUMN requestedPermissions TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE installed_app ADD COLUMN usesAccessibility INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        fun get(context: Context): DeviceGuardDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    DeviceGuardDatabase::class.java,
                    "deviceguard.db"
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
