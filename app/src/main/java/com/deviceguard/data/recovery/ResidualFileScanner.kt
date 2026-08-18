package com.deviceguard.data.recovery

import android.content.Context
import android.os.Build
import android.os.Environment
import android.webkit.MimeTypeMap
import java.io.File

/**
 * Tầng 2 – Tệp còn sót.
 *
 * Xóa một tệp trên Android thường không xóa ngay khỏi phân vùng. Các dấu vết hay
 * gặp và đều đọc được bằng API thường:
 *
 *  - Tệp `.trashed-<epoch>-<tên gốc>`: quy ước đặt tên của MediaStore khi đưa vào
 *    thùng rác; phần epoch chính là mốc hết hạn, suy ra được thời điểm xóa.
 *  - `LOST.DIR`: nơi tiến trình kiểm tra hệ thống tệp của Android gom các inode
 *    mồ côi sau khi tắt máy đột ngột.
 *  - Thư mục cache của ứng dụng thư viện/nhắn tin: bản sao đầy đủ hoặc thu nhỏ của
 *    tệp gốc thường sống lâu hơn tệp gốc.
 *  - `.thumbnails`: ảnh thu nhỏ tồn tại độc lập với ảnh gốc — vẫn dựng lại được
 *    nội dung ở độ phân giải thấp sau khi bản gốc đã biến mất.
 *
 * Phạm vi quét phụ thuộc quyền: không có MANAGE_EXTERNAL_STORAGE thì chỉ quét được
 * vùng riêng của ứng dụng. Kết quả quét luôn ghi rõ phạm vi đã đạt tới để báo cáo
 * thực nghiệm không bị hiểu nhầm là "quét toàn máy".
 */
class ResidualFileScanner(private val context: Context) {

    data class ScanScope(
        val roots: List<File>,
        val hasAllFilesAccess: Boolean
    )

    fun currentScope(): ScanScope {
        val hasAllFiles = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            Environment.isExternalStorageManager()
        val roots = buildList {
            addAll(listOfNotNull(context.externalCacheDir, context.cacheDir, context.filesDir))
            context.getExternalFilesDirs(null).filterNotNull().forEach { add(it) }
            if (hasAllFiles) {
                add(Environment.getExternalStorageDirectory())
            }
        }.filter { it.exists() }.distinctBy { it.absolutePath }
        return ScanScope(roots, hasAllFiles)
    }

    fun scan(
        scope: ScanScope = currentScope(),
        onProgress: (ScanProgress) -> Unit = {}
    ): List<RecoveryItem> {
        val found = mutableListOf<RecoveryItem>()
        scope.roots.forEach { root ->
            onProgress(ScanProgress.Stage("Quét tệp còn sót", root.absolutePath))
            walk(root, depth = 0) { file ->
                classify(file)?.let { item ->
                    found += item
                    onProgress(ScanProgress.Found(item))
                }
            }
        }
        return found.distinctBy { it.locator }
    }

    private fun walk(dir: File, depth: Int, onFile: (File) -> Unit) {
        if (depth > MAX_DEPTH) return
        val children = runCatching { dir.listFiles() }.getOrNull() ?: return
        for (child in children) {
            when {
                child.isDirectory -> walk(child, depth + 1, onFile)
                child.isFile -> onFile(child)
            }
        }
    }

    private fun classify(file: File): RecoveryItem? {
        val name = file.name
        if (file.length() < MIN_SIZE_BYTES) return null
        val path = file.absolutePath

        val (matched, confidence, deletedAt) = when {
            name.startsWith(TRASHED_PREFIX) -> Triple(
                true,
                RecoveryConfidence.HIGH,
                parseTrashedTimestamp(name)
            )

            path.contains("/LOST.DIR/") -> Triple(true, RecoveryConfidence.MEDIUM, null)

            path.contains("/.thumbnails/") -> Triple(true, RecoveryConfidence.LOW, null)

            CACHE_HINTS.any { path.contains(it, ignoreCase = true) } &&
                RECOVERABLE_EXTENSIONS.any { name.endsWith(it, ignoreCase = true) } ->
                Triple(true, RecoveryConfidence.MEDIUM, null)

            else -> Triple(false, RecoveryConfidence.LOW, null)
        }
        if (!matched) return null

        return RecoveryItem(
            source = RecoverySource.RESIDUAL_FILE,
            displayName = name.removePrefix(TRASHED_PREFIX).substringAfter('-', name),
            locator = path,
            mimeType = MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(file.extension.lowercase()),
            sizeBytes = file.length(),
            deletedAt = deletedAt ?: file.lastModified(),
            confidence = confidence,
            restorable = file.canRead()
        )
    }

    /** `.trashed-1728000000-IMG_0042.jpg` → mốc hết hạn (giây) trừ đi 30 ngày. */
    private fun parseTrashedTimestamp(name: String): Long? {
        val expires = name.removePrefix(TRASHED_PREFIX).substringBefore('-').toLongOrNull()
            ?: return null
        return expires * 1000L - 30L * 24 * 60 * 60 * 1000
    }

    /** Sao chép ra thư mục Khôi phục của ứng dụng — không ghi đè lên vị trí gốc. */
    fun restore(item: RecoveryItem): File? {
        val source = File(item.locator)
        if (!source.canRead()) return null
        val outputDir = File(context.getExternalFilesDir(null), RESTORE_DIR).apply { mkdirs() }
        val target = File(outputDir, uniqueName(outputDir, item.displayName))
        return runCatching {
            source.copyTo(target, overwrite = false)
            target
        }.getOrNull()
    }

    private fun uniqueName(dir: File, preferred: String): String {
        var candidate = preferred
        var counter = 1
        while (File(dir, candidate).exists()) {
            val base = preferred.substringBeforeLast('.', preferred)
            val ext = preferred.substringAfterLast('.', "")
            candidate = if (ext.isEmpty()) "${base}_$counter" else "${base}_$counter.$ext"
            counter++
        }
        return candidate
    }

    companion object {
        const val RESTORE_DIR = "recovered"
        private const val TRASHED_PREFIX = ".trashed-"
        private const val MAX_DEPTH = 12
        private const val MIN_SIZE_BYTES = 4 * 1024L

        private val CACHE_HINTS = listOf("/cache/", "/.cache/", "/Media/.Statuses/", "/tmp/")
        private val RECOVERABLE_EXTENSIONS = listOf(
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".heic",
            ".mp4", ".3gp", ".mkv", ".mp3", ".m4a", ".opus", ".ogg",
            ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".zip", ".db"
        )
    }
}
