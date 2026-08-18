package com.deviceguard.data.recovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest
import kotlin.random.Random

/**
 * Thực nghiệm kiểm chứng khâu 2 – Phục hồi, tầng 3 (cắt tệp theo chữ ký).
 *
 * Kịch bản mô phỏng đúng tình huống forensics thật:
 *  1. Dựng một "ảnh đĩa" gồm dữ liệu ngẫu nhiên (mô phỏng block đã bị ghi đè hoặc
 *     chưa dùng) xen kẽ các tệp thật đã bị "xóa" — tức là không còn mục lục nào
 *     trỏ tới chúng, chỉ còn chuỗi byte nằm trên đĩa.
 *  2. Chạy [FileCarver] trên ảnh đĩa đó.
 *  3. Đối chiếu SHA-256 của tệp cắt được với tệp gốc.
 *
 * Đây là bài kiểm tra chạy trên JVM, không cần thiết bị — [FileCarver] cố ý được
 * viết không phụ thuộc API Android để phần lõi thuật toán kiểm chứng được độc lập.
 */
class FileCarverTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val random = Random(20250818)

    @Test
    fun `cat duoc dung so tep va noi dung khop hash goc`() {
        val jpeg = fakeJpeg(sizeBytes = 40_000)
        val png = fakePng(sizeBytes = 25_000)
        val pdf = fakePdf(sizeBytes = 12_000)
        val embedded = listOf(jpeg, png, pdf)

        val image = temporaryFolder.newFile("userdata.img")
        image.outputStream().buffered().use { out ->
            out.write(randomBytes(64 * 1024))     // vùng trống đầu đĩa
            out.write(jpeg)
            out.write(randomBytes(16 * 1024))     // khoảng trống giữa hai tệp
            out.write(png)
            out.write(randomBytes(8 * 1024))
            out.write(pdf)
            out.write(randomBytes(32 * 1024))     // đuôi đĩa
        }

        val outputDir = temporaryFolder.newFolder("carved")
        val carved = FileCarver().carve(image, outputDir)

        assertEquals("Phải cắt được đúng 3 tệp", 3, carved.size)
        assertTrue("Cả 3 tệp đều phải tìm thấy footer", carved.all { it.complete })

        val originalHashes = embedded.map { sha256(it) }.toSet()
        val carvedHashes = carved.map { sha256(it.output.readBytes()) }.toSet()
        assertEquals(
            "Nội dung tệp cắt được phải trùng khớp bit-for-bit với bản gốc",
            originalHashes,
            carvedHashes
        )
    }

    @Test
    fun `nhan dien dung dinh dang cua tung tep`() {
        val image = temporaryFolder.newFile("mixed.img")
        image.outputStream().buffered().use { out ->
            out.write(randomBytes(4096))
            out.write(fakePng(sizeBytes = 9_000))
            out.write(randomBytes(4096))
            out.write(fakeJpeg(sizeBytes = 9_000))
        }

        val carved = FileCarver().carve(image, temporaryFolder.newFolder("out"))

        assertEquals(listOf("PNG", "JPEG"), carved.map { it.signature.name })
        assertEquals(listOf("png", "jpg"), carved.map { it.output.extension })
    }

    @Test
    fun `khong sinh ket qua tu du lieu ngau nhien thuan tuy`() {
        val image = temporaryFolder.newFile("noise.img")
        image.writeBytes(randomBytes(2 * 1024 * 1024))

        val carved = FileCarver().carve(image, temporaryFolder.newFolder("out"))

        // Chữ ký ngắn vẫn có thể xuất hiện ngẫu nhiên, nhưng gần như không bao giờ
        // kèm footer đúng chỗ — nên số tệp "hoàn chỉnh" phải bằng 0.
        assertEquals(0, carved.count { it.complete })
    }

    @Test
    fun `tep bi cat cut khi khong tim thay footer van duoc danh dau chua hoan chinh`() {
        val header = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        val eoi = byteArrayOf(0xFF.toByte(), 0xD9.toByte())
        val image = temporaryFolder.newFile("truncated.img")
        image.outputStream().buffered().use { out ->
            out.write(header)
            // Loại sạch FFD9 khỏi phần thân: một chuỗi ngẫu nhiên 100 KB gần như
            // chắc chắn chứa sẵn cặp byte này (kỳ vọng ~1,5 lần xuất hiện), và bộ
            // cắt sẽ coi đó là footer thật — đúng bản chất dương tính giả của kỹ
            // thuật carving, nhưng ở đây ta cần đúng kịch bản "không có footer".
            out.write(randomBytesWithout(100_000, eoi))
        }

        val carved = FileCarver().carve(image, temporaryFolder.newFolder("out"))

        assertTrue("Vẫn phải cứu được phần dữ liệu đọc dở", carved.isNotEmpty())
        assertTrue("Phải bị đánh dấu là chưa hoàn chỉnh", carved.none { it.complete })
    }

    /** Chuỗi byte hợp lệ về mặt chữ ký: SOI … EOI. */
    private fun fakeJpeg(sizeBytes: Int): ByteArray {
        val body = randomBytesWithout(sizeBytes - 5, byteArrayOf(0xFF.toByte(), 0xD9.toByte()))
        return byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) +
            body +
            byteArrayOf(0xFF.toByte(), 0xD9.toByte())
    }

    private fun fakePng(sizeBytes: Int): ByteArray {
        val signature = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        )
        val iend = byteArrayOf(
            0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(), 0x42, 0x60, 0x82.toByte()
        )
        return signature + randomBytesWithout(sizeBytes - 16, iend) + iend
    }

    private fun fakePdf(sizeBytes: Int): ByteArray {
        val header = "%PDF-1.7\n".toByteArray(Charsets.US_ASCII)
        val footer = "%%EOF".toByteArray(Charsets.US_ASCII)
        return header +
            randomBytesWithout(sizeBytes - header.size - footer.size, footer) +
            footer
    }

    private fun randomBytes(size: Int) = ByteArray(size).also { random.nextBytes(it) }

    /**
     * Sinh dữ liệu ngẫu nhiên nhưng loại bỏ mọi lần xuất hiện của [forbidden] —
     * tránh việc phần thân tệp vô tình chứa footer và làm bộ cắt dừng sớm.
     */
    private fun randomBytesWithout(size: Int, forbidden: ByteArray): ByteArray {
        val bytes = randomBytes(size)
        var i = 0
        while (i <= bytes.size - forbidden.size) {
            if (forbidden.indices.all { bytes[i + it] == forbidden[it] }) {
                bytes[i] = (bytes[i] + 1).toByte()
            }
            i++
        }
        return bytes
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
