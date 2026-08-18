package com.deviceguard.data.recovery

import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.annotation.RequiresApi

/**
 * Tầng 1 – Thùng rác MediaStore.
 *
 * Từ Android 11 (API 30), việc "xóa" ảnh/video trong hầu hết ứng dụng thư viện chỉ
 * đặt cờ IS_TRASHED = 1; tệp vẫn nằm nguyên trên phân vùng và hệ thống tự dọn sau
 * khoảng 30 ngày. Đây là trường hợp khôi phục sạch nhất: không cần root, không suy
 * đoán, metadata giữ nguyên.
 *
 * Lưu ý quan trọng cho luận văn: một ứng dụng thường chỉ nhìn thấy mục trong thùng
 * rác do CHÍNH NÓ đưa vào. Muốn liệt kê thùng rác của toàn hệ thống, ứng dụng phải
 * được cấp MANAGE_EXTERNAL_STORAGE (hoặc là ứng dụng thư viện mặc định có
 * MANAGE_MEDIA). Vì vậy quyền này được để ở dạng tùy chọn và giải thích rõ với
 * người dùng trước khi hỏi.
 */
class MediaStoreTrashScanner(private val context: Context) {

    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    @RequiresApi(Build.VERSION_CODES.R)
    fun scan(): List<RecoveryItem> {
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_EXPIRES,
            MediaStore.Files.FileColumns.DATE_MODIFIED
        )
        val queryArgs = Bundle().apply {
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
            putString(
                android.content.ContentResolver.QUERY_ARG_SQL_SORT_ORDER,
                "${MediaStore.Files.FileColumns.DATE_EXPIRES} DESC"
            )
        }

        val items = mutableListOf<RecoveryItem>()
        context.contentResolver.query(collection, projection, queryArgs, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val expiresCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_EXPIRES)
            val modifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                // DATE_EXPIRES là mốc hệ thống sẽ xóa vĩnh viễn (giây). Trừ ngược
                // 30 ngày cho ra thời điểm bị đưa vào thùng rác (xấp xỉ).
                val expiresSec = cursor.getLong(expiresCol)
                val deletedAt = if (expiresSec > 0) {
                    (expiresSec * 1000L) - TRASH_RETENTION_MS
                } else {
                    cursor.getLong(modifiedCol) * 1000L
                }
                items += RecoveryItem(
                    source = RecoverySource.MEDIASTORE_TRASH,
                    displayName = cursor.getString(nameCol) ?: "(không tên)",
                    locator = ContentUris.withAppendedId(collection, id).toString(),
                    mimeType = cursor.getString(mimeCol),
                    sizeBytes = cursor.getLong(sizeCol),
                    deletedAt = deletedAt,
                    confidence = RecoveryConfidence.HIGH,
                    restorable = true
                )
            }
        }
        return items
    }

    /**
     * Trả về [PendingIntent] để Activity khởi chạy hộp thoại xác nhận của hệ thống.
     * Cố tình KHÔNG tự ý gỡ cờ IS_TRASHED: việc khôi phục phải do người dùng bấm
     * xác nhận trên hộp thoại do chính Android vẽ ra.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    fun createRestoreRequest(items: List<RecoveryItem>): PendingIntent? {
        val uris = items.mapNotNull { runCatching { Uri.parse(it.locator) }.getOrNull() }
        if (uris.isEmpty()) return null
        return MediaStore.createTrashRequest(context.contentResolver, uris, false)
    }

    private companion object {
        const val TRASH_RETENTION_MS = 30L * 24 * 60 * 60 * 1000
    }
}
