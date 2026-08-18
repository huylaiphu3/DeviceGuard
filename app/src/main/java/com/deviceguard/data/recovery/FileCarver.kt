package com.deviceguard.data.recovery

import java.io.File
import java.io.RandomAccessFile

/**
 * Tầng 3 – Cắt tệp theo chữ ký (file carving).
 *
 * Đây là kỹ thuật forensics kinh điển: bỏ qua hoàn toàn hệ thống tệp, đọc tuần tự
 * từng byte của một khối dữ liệu thô và nhận diện tệp bằng chuỗi byte mở đầu
 * (header) — kết thúc bằng chuỗi byte đóng (footer) nếu định dạng có, hoặc bằng
 * một ngưỡng kích thước tối đa nếu không. Khi một tệp bị xóa, mục lục trong hệ
 * thống tệp bị gỡ nhưng các block dữ liệu vẫn còn nguyên cho tới khi bị ghi đè —
 * carving khai thác đúng khoảng thời gian đó.
 *
 * GIỚI HẠN PHẢI NÊU RÕ TRONG LUẬN VĂN
 * -----------------------------------
 * Bộ cắt này KHÔNG thể chạy trực tiếp trên phân vùng của một máy Android thường:
 *
 *  1. Truy cập thô vào `/dev/block/...` cần quyền root; SELinux ở chế độ enforcing
 *     chặn tiến trình ứng dụng mở các node đó dù có được cấp quyền tệp.
 *  2. Từ Android 10, dữ liệu người dùng được mã hóa theo tệp (File-Based
 *     Encryption). Đọc được block thô cũng chỉ nhận về bản mã — không có chữ ký
 *     JPEG/PNG nào xuất hiện để mà cắt.
 *  3. Bộ nhớ flash dùng lớp FTL và lệnh TRIM: block đã giải phóng có thể bị bộ
 *     điều khiển xóa ngay, không chờ ghi đè.
 *
 * Vì vậy [FileCarver] nhận đầu vào là MỘT TỆP ẢNH ĐĨA, và được dùng theo hai kịch
 * bản hợp lệ trong phạm vi đề tài:
 *
 *  a) Ảnh đĩa userdata trích từ máy ảo (emulator) — dựng được kịch bản thực nghiệm
 *     có kiểm soát: tạo tệp, xóa, trích ảnh đĩa, cắt, đối chiếu hash với bản gốc.
 *  b) Bất kỳ khối dữ liệu nào ứng dụng đọc được hợp pháp: tệp cơ sở dữ liệu, tệp
 *     cache lớn, ảnh sao lưu do chính người dùng đưa vào.
 *
 * Cách tạo dữ liệu thực nghiệm ở kịch bản (a) xem docs/03-phuc-hoi-du-lieu.md.
 */
class FileCarver {

    /** Một định dạng nhận diện được: chữ ký mở đầu, chữ ký kết thúc (nếu có). */
    data class Signature(
        val name: String,
        val extension: String,
        val mimeType: String,
        val header: ByteArray,
        val footer: ByteArray?,
        /** Số byte tính thêm sau footer để tệp đủ hợp lệ (vd. EOCD của ZIP). */
        val footerTail: Int = 0,
        val maxSizeBytes: Long,
        /** Header nằm ở vị trí lệch so với đầu tệp (vd. `ftyp` của MP4 ở offset 4). */
        val headerOffset: Int = 0
    ) {
        override fun equals(other: Any?) = this === other
        override fun hashCode() = name.hashCode()
    }

    data class CarvedFile(
        val signature: Signature,
        val offset: Long,
        val sizeBytes: Long,
        val output: File,
        /** true nếu tìm thấy footer; false nghĩa là bị cắt theo ngưỡng kích thước. */
        val complete: Boolean
    )

    /**
     * Quét [image] và ghi các tệp cắt được vào [outputDir].
     *
     * @param onProgress được gọi liên tục để UI vẽ thanh tiến độ.
     * @param maxResults chặn trên số tệp xuất ra, tránh làm đầy bộ nhớ máy.
     */
    fun carve(
        image: File,
        outputDir: File,
        signatures: List<Signature> = DEFAULT_SIGNATURES,
        maxResults: Int = 500,
        onProgress: (ScanProgress) -> Unit = {}
    ): List<CarvedFile> {
        require(image.canRead()) { "Không đọc được ảnh đĩa: ${image.absolutePath}" }
        outputDir.mkdirs()

        val results = mutableListOf<CarvedFile>()
        val totalBytes = image.length()
        val maxHeaderSpan = signatures.maxOf { it.headerOffset + it.header.size }

        RandomAccessFile(image, "r").use { raf ->
            val buffer = ByteArray(CHUNK_SIZE)
            var chunkStart = 0L
            // Vị trí kết thúc của tệp đã cắt gần nhất — không cắt lồng nhau, tránh
            // sinh ra hàng nghìn "tệp" từ ảnh thu nhỏ nhúng bên trong ảnh gốc.
            var consumedUntil = 0L

            while (chunkStart < totalBytes && results.size < maxResults) {
                raf.seek(chunkStart)
                val read = raf.read(buffer)
                if (read <= 0) break

                var i = 0
                while (i < read && results.size < maxResults) {
                    val absolute = chunkStart + i
                    if (absolute < consumedUntil) {
                        i++
                        continue
                    }
                    val signature = signatures.firstOrNull {
                        matchesAt(buffer, read, i, it)
                    }
                    if (signature != null) {
                        val start = absolute - signature.headerOffset
                        if (start >= 0) {
                            val carved = extract(raf, start, signature, totalBytes, outputDir, results.size)
                            if (carved != null) {
                                results += carved
                                consumedUntil = start + carved.sizeBytes
                                onProgress(ScanProgress.Found(carved.toRecoveryItem()))
                                i = (consumedUntil - chunkStart).coerceAtMost(read.toLong()).toInt()
                                continue
                            }
                        }
                    }
                    i++
                }

                onProgress(ScanProgress.Bytes(minOf(chunkStart + read, totalBytes), totalBytes))
                // Lùi lại maxHeaderSpan byte để không bỏ sót chữ ký nằm vắt qua ranh
                // giới hai chunk.
                chunkStart += (read - maxHeaderSpan).coerceAtLeast(1)
            }
        }
        return results
    }

    private fun matchesAt(buffer: ByteArray, limit: Int, index: Int, signature: Signature): Boolean {
        val header = signature.header
        if (index + header.size > limit) return false
        for (k in header.indices) {
            if (buffer[index + k] != header[k]) return false
        }
        return true
    }

    private fun extract(
        raf: RandomAccessFile,
        start: Long,
        signature: Signature,
        totalBytes: Long,
        outputDir: File,
        index: Int
    ): CarvedFile? {
        val limit = minOf(start + signature.maxSizeBytes, totalBytes)
        val footer = signature.footer

        var end = limit
        var complete = false

        if (footer != null) {
            val position = findFooter(raf, start + signature.header.size, limit, footer)
            if (position >= 0) {
                end = position + footer.size + signature.footerTail
                complete = true
            }
        }

        val size = end - start
        if (size < MIN_CARVED_SIZE) return null

        val output = File(outputDir, "carved_%04d_%d.%s".format(index, start, signature.extension))
        raf.seek(start)
        output.outputStream().buffered().use { out ->
            var remaining = size
            val buffer = ByteArray(COPY_BUFFER)
            while (remaining > 0) {
                val toRead = minOf(remaining, COPY_BUFFER.toLong()).toInt()
                val read = raf.read(buffer, 0, toRead)
                if (read <= 0) break
                out.write(buffer, 0, read)
                remaining -= read
            }
        }
        return CarvedFile(signature, start, output.length(), output, complete)
    }

    private fun findFooter(
        raf: RandomAccessFile,
        from: Long,
        limit: Long,
        footer: ByteArray
    ): Long {
        val buffer = ByteArray(CHUNK_SIZE)
        var position = from
        while (position < limit) {
            raf.seek(position)
            val toRead = minOf(CHUNK_SIZE.toLong(), limit - position).toInt()
            val read = raf.read(buffer, 0, toRead)
            if (read <= 0) return -1
            for (i in 0..read - footer.size) {
                var match = true
                for (k in footer.indices) {
                    if (buffer[i + k] != footer[k]) {
                        match = false
                        break
                    }
                }
                if (match) return position + i
            }
            position += (read - footer.size + 1).coerceAtLeast(1)
        }
        return -1
    }

    companion object {
        private const val CHUNK_SIZE = 1 shl 20
        private const val COPY_BUFFER = 64 * 1024
        private const val MIN_CARVED_SIZE = 512L

        private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
        private fun ascii(text: String) = text.toByteArray(Charsets.US_ASCII)

        /**
         * Bộ chữ ký mặc định. Cố ý giữ gọn và chỉ gồm các định dạng có chữ ký ổn
         * định — thêm chữ ký yếu chỉ làm tăng tỉ lệ dương tính giả.
         */
        val DEFAULT_SIGNATURES: List<Signature> = listOf(
            Signature(
                name = "JPEG",
                extension = "jpg",
                mimeType = "image/jpeg",
                header = bytes(0xFF, 0xD8, 0xFF),
                footer = bytes(0xFF, 0xD9),
                maxSizeBytes = 32L * 1024 * 1024
            ),
            Signature(
                name = "PNG",
                extension = "png",
                mimeType = "image/png",
                header = bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A),
                footer = bytes(0x49, 0x45, 0x4E, 0x44, 0xAE, 0x42, 0x60, 0x82),
                maxSizeBytes = 32L * 1024 * 1024
            ),
            Signature(
                name = "GIF",
                extension = "gif",
                mimeType = "image/gif",
                header = ascii("GIF89a"),
                footer = bytes(0x00, 0x3B),
                maxSizeBytes = 16L * 1024 * 1024
            ),
            Signature(
                name = "PDF",
                extension = "pdf",
                mimeType = "application/pdf",
                header = ascii("%PDF-"),
                footer = ascii("%%EOF"),
                maxSizeBytes = 64L * 1024 * 1024
            ),
            Signature(
                name = "MP4/3GP",
                extension = "mp4",
                mimeType = "video/mp4",
                header = ascii("ftyp"),
                footer = null,
                headerOffset = 4,
                maxSizeBytes = 512L * 1024 * 1024
            ),
            Signature(
                name = "ZIP/DOCX/APK",
                extension = "zip",
                mimeType = "application/zip",
                header = bytes(0x50, 0x4B, 0x03, 0x04),
                footer = bytes(0x50, 0x4B, 0x05, 0x06),
                footerTail = 18,
                maxSizeBytes = 256L * 1024 * 1024
            ),
            Signature(
                name = "SQLite",
                extension = "db",
                mimeType = "application/vnd.sqlite3",
                header = ascii("SQLite format 3") + byteArrayOf(0),
                footer = null,
                maxSizeBytes = 64L * 1024 * 1024
            )
        )
    }
}

fun FileCarver.CarvedFile.toRecoveryItem() = RecoveryItem(
    source = RecoverySource.CARVED,
    displayName = output.name,
    locator = output.absolutePath,
    mimeType = signature.mimeType,
    sizeBytes = sizeBytes,
    deletedAt = null,
    confidence = if (complete) RecoveryConfidence.MEDIUM else RecoveryConfidence.LOW,
    restorable = true
)
