# Khâu 2 — Phục hồi dữ liệu: cơ sở kỹ thuật, giới hạn và thực nghiệm

Đây là phần có hàm lượng kỹ thuật cao nhất của đồ án, và cũng là phần dễ viết sai
nhất. Tài liệu này nêu rõ **cái gì làm được, cái gì không, và vì sao** — phần
"vì sao không" có giá trị học thuật ngang phần hiện thực.

## 1. Chuyện gì xảy ra khi một tệp bị "xóa" trên Android

Ba tầng tách biệt, và chúng đã tách xa nhau hơn nhiều so với thời máy tính để bàn:

**Tầng ứng dụng.** Từ Android 11 (API 30), hầu hết ứng dụng thư viện không xóa
tệp mà đặt cờ `IS_TRASHED = 1` trong MediaStore. Tệp vẫn nằm nguyên trên phân
vùng, chỉ bị ẩn khỏi truy vấn thông thường, và hệ thống tự dọn sau khoảng 30 ngày
(`DATE_EXPIRES`). Trên đĩa, MediaStore đổi tên tệp theo quy ước
`.trashed-<epoch hết hạn>-<tên gốc>`.

**Tầng hệ thống tệp.** Khi tệp bị xóa thật, ext4 gỡ mục trong thư mục và giải
phóng inode; các block dữ liệu vẫn giữ nguyên nội dung cho tới khi được cấp phát
lại. Đây chính là khoảng cửa sổ mà kỹ thuật carving khai thác.

**Tầng thiết bị lưu trữ.** Bộ nhớ flash không ghi đè tại chỗ. Lớp FTL (Flash
Translation Layer) trong bộ điều khiển ánh xạ lại địa chỉ logic sang khối vật lý
khác, và lệnh `TRIM`/`discard` báo cho bộ điều khiển biết block nào không còn
dùng — bộ điều khiển có thể xóa sạch ngay để chuẩn bị cho lần ghi sau. Nghĩa là
ngay cả khi hệ thống tệp còn giữ dữ liệu, thiết bị vật lý có thể đã không còn.

## 2. Vì sao không thể carving trực tiếp trên máy Android thường

Ba rào cản độc lập, mỗi rào cản một mình đã đủ chặn:

**(a) Không có quyền truy cập node block.** Đã kiểm chứng trên emulator API 36:

```
$ adb shell ls -la /dev/block/
brw-------  1 root root 254, 0 ... dm-0
brw-------  1 root root 254, 1 ... dm-1
```

Quyền `brw-------` với chủ sở hữu `root` — tiến trình ứng dụng (uid `u0_a215`)
không mở được. Cấp thêm quyền tệp cũng vô ích: SELinux ở chế độ enforcing gắn
nhãn các node này và chính sách không cho miền `untrusted_app` đọc chúng.

**(b) Dữ liệu người dùng đã được mã hóa.** Từ Android 10, mã hóa theo tệp
(File-Based Encryption) là bắt buộc. Trên emulator, `/data` là ext4 nằm trên một
device-mapper node:

```
$ adb shell mount | grep ' /data '
/dev/block/dm-53 on /data type ext4 (rw,seclabel,nosuid,nodev,noatime,...)
```

Đọc được block thô của thiết bị nền cũng chỉ nhận về bản mã. Không có chuỗi
`FF D8 FF` hay `%PDF-` nào xuất hiện để mà nhận diện. Carving trên dữ liệu đã mã
hóa về nguyên tắc là bất khả thi, không phải khó.

**(c) `adb root` không dùng được trên ảnh hệ thống có Play Store.** Đã kiểm chứng:

```
$ adb root
adbd cannot run as root in production builds
```

Muốn `adb root` phải chọn AVD dùng ảnh `google_apis` (không có Play Store) hoặc
`aosp`.

**Kết luận cho báo cáo:** một ứng dụng Android chạy trong sandbox tiêu chuẩn
không thể thực hiện file carving trên phân vùng của chính máy đó. Bất kỳ ứng
dụng nào trên cửa hàng quảng cáo "khôi phục mọi tệp đã xóa, không cần root" đều
chỉ đang làm tầng 1 và tầng 2 dưới đây — đây là một nhận định có thể đưa vào
phần kết luận của luận văn.

## 3. Ba tầng đã hiện thực

### Tầng 1 — Thùng rác MediaStore

`data/recovery/MediaStoreTrashScanner.kt`

Truy vấn với `MediaStore.QUERY_ARG_MATCH_TRASHED = MATCH_ONLY` trên
`MediaStore.Files.getContentUri(VOLUME_EXTERNAL)`. Khôi phục bằng
`MediaStore.createTrashRequest()` — trả về `PendingIntent` để hệ thống tự vẽ hộp
thoại xác nhận. Ứng dụng **cố tình không** tự gỡ cờ `IS_TRASHED`: quyết định khôi
phục phải do người dùng bấm trên giao diện của chính Android.

Giới hạn cần nêu: một ứng dụng chỉ nhìn thấy mục trong thùng rác do **chính nó**
đưa vào. Muốn liệt kê thùng rác toàn hệ thống phải có `MANAGE_EXTERNAL_STORAGE`,
hoặc là ứng dụng thư viện mặc định giữ `MANAGE_MEDIA`. Vì thế
`MANAGE_EXTERNAL_STORAGE` được để ở dạng tùy chọn, giải thích rõ trước khi hỏi,
và ứng dụng vẫn chạy đủ chức năng khác nếu người dùng từ chối.

Độ tin cậy: **Cao** — tệp còn nguyên vẹn, metadata giữ đủ.

### Tầng 2 — Tệp còn sót

`data/recovery/ResidualFileScanner.kt`

| Dấu vết | Nguồn gốc | Độ tin cậy |
|---|---|---|
| `.trashed-<epoch>-<tên>` | Quy ước đặt tên của MediaStore | Cao |
| `LOST.DIR/` | Tiến trình kiểm tra hệ thống tệp gom inode mồ côi sau khi tắt máy đột ngột | Trung bình |
| Thư mục `cache/` của ứng dụng | Bản sao của tệp gốc, thường sống lâu hơn bản gốc | Trung bình |
| `.thumbnails/` | Ảnh thu nhỏ tồn tại độc lập với ảnh gốc | Thấp — chỉ dựng lại được ở độ phân giải thấp |

Phần epoch trong tên `.trashed-` chính là mốc `DATE_EXPIRES`; trừ ngược 30 ngày
suy ra được thời điểm tệp bị đưa vào thùng rác. Đây là một chi tiết forensics
nhỏ nhưng có giá trị: nó cho phép dựng lại **mốc thời gian xóa** từ một tệp mà hệ
thống tệp đã không còn metadata nào khác.

Phạm vi quét phụ thuộc quyền, và giao diện luôn hiển thị phạm vi thực tế đã đạt
tới ("vùng riêng của ứng dụng" hay "toàn bộ bộ nhớ dùng chung") để kết quả thực
nghiệm không bị hiểu nhầm là đã quét toàn máy.

Khôi phục ở tầng này là **sao chép** ra thư mục `files/recovered/`, không ghi đè
lên vị trí gốc — nguyên tắc cơ bản của forensics: không sửa hiện trường.

### Tầng 3 — Cắt tệp theo chữ ký (file carving)

`data/recovery/FileCarver.kt`

Thuật toán bỏ qua hoàn toàn hệ thống tệp, quét tuần tự khối dữ liệu thô và nhận
diện tệp bằng chuỗi byte mở đầu (header), kết thúc bằng chuỗi byte đóng (footer)
nếu định dạng có, hoặc bằng ngưỡng kích thước nếu không.

| Định dạng | Header | Footer | Ngưỡng |
|---|---|---|---|
| JPEG | `FF D8 FF` | `FF D9` | 32 MB |
| PNG | `89 50 4E 47 0D 0A 1A 0A` | `49 45 4E 44 AE 42 60 82` | 32 MB |
| GIF | `GIF89a` | `00 3B` | 16 MB |
| PDF | `%PDF-` | `%%EOF` | 64 MB |
| MP4/3GP | `ftyp` tại offset 4 | — | 512 MB |
| ZIP/DOCX/APK | `50 4B 03 04` | `50 4B 05 06` + 18 byte | 256 MB |
| SQLite | `SQLite format 3\0` | — | 64 MB |

Ba chi tiết hiện thực đáng nêu:

1. **Đọc theo chunk có phần chồng lấn.** Quét theo khối 1 MB, mỗi bước lùi lại
   `max(headerOffset + header.size)` byte để chữ ký nằm vắt qua ranh giới hai
   khối không bị bỏ sót.
2. **Không cắt lồng nhau.** Sau khi cắt xong một tệp, con trỏ nhảy tới cuối tệp
   đó. Không có bước này, ảnh thu nhỏ nhúng trong EXIF của một ảnh JPEG sẽ bị
   nhận diện thành một tệp JPEG riêng, và một ảnh đĩa thật sẽ sinh ra hàng nghìn
   kết quả rác.
3. **Header lệch offset.** MP4 có `ftyp` ở byte thứ 4 chứ không phải byte đầu,
   nên `Signature` có trường `headerOffset` để lùi điểm bắt đầu về đúng chỗ.

Đầu vào là **một tệp ảnh đĩa**, không phải node block của máy — vì lý do đã trình
bày ở mục 2. Trong ứng dụng, người dùng chọn ảnh đĩa qua Storage Access Framework.

## 4. Thực nghiệm kiểm chứng thuật toán carving

### 4.1 Kiểm thử tự động (đã chạy, đạt)

`app/src/test/java/com/deviceguard/data/recovery/FileCarverTest.kt` — chạy trên
JVM, không cần thiết bị:

```
./gradlew :app:testDebugUnitTest
```

| Ca kiểm thử | Nội dung | Kết quả |
|---|---|---|
| Cắt đúng số tệp, nội dung khớp hash gốc | Dựng ảnh đĩa gồm dữ liệu ngẫu nhiên xen kẽ 1 JPEG + 1 PNG + 1 PDF, đối chiếu SHA-256 | Đạt — trùng khớp bit-for-bit |
| Nhận diện đúng định dạng từng tệp | Ảnh đĩa hỗn hợp PNG + JPEG | Đạt |
| Không sinh kết quả từ nhiễu thuần túy | 2 MB dữ liệu ngẫu nhiên | Đạt — 0 tệp hoàn chỉnh |
| Tệp bị cắt cụt được đánh dấu chưa hoàn chỉnh | Header JPEG nhưng không có footer | Đạt — `complete = false` |

**Một phát hiện đáng đưa vào báo cáo.** Ca kiểm thử thứ tư ban đầu **thất bại**.
Nguyên nhân: phần thân 100 KB dữ liệu ngẫu nhiên gần như chắc chắn chứa sẵn cặp
byte `FF D9` (kỳ vọng ≈ 100000/65536 ≈ 1,5 lần xuất hiện), và bộ cắt coi đó là
footer thật. Đây không phải lỗi hiện thực mà chính là **tỉ lệ dương tính giả cố
hữu của kỹ thuật carving dựa trên chữ ký**: footer càng ngắn, xác suất trùng
ngẫu nhiên càng cao. Ca kiểm thử đã được sửa để loại `FF D9` khỏi phần thân, và
hiện tượng này được ghi lại ở đây như một kết quả quan sát được, kèm hệ quả thực
tiễn: tệp carving ra cần được kiểm chứng lại bằng bộ giải mã của chính định dạng
đó, không thể tin vào chữ ký là đủ.

### 4.2 Kịch bản thực nghiệm mở rộng (đề xuất cho chương thực nghiệm)

**Cách A — ảnh đĩa dựng trên máy chủ (khuyến nghị, tái lập được).**

```bash
# macOS: tạo một ảnh đĩa FAT32 32 MB
hdiutil create -size 32m -fs MS-DOS -volname CARVETEST -o carve_test
hdiutil attach carve_test.dmg
cp anh_goc_*.jpg tai_lieu.pdf /Volumes/CARVETEST/
shasum -a 256 anh_goc_*.jpg tai_lieu.pdf > hash_goc.txt   # mốc đối chiếu
rm /Volumes/CARVETEST/*                                    # "xóa"
hdiutil detach /Volumes/CARVETEST

# đẩy ảnh đĩa sang thiết bị rồi chọn qua giao diện Phục hồi
adb push carve_test.dmg /sdcard/Download/
```

Sau khi cắt, đối chiếu `shasum -a 256` của tệp thu được với `hash_goc.txt`. Đây
là phương pháp chuẩn để thẩm định công cụ forensics (cùng nguyên lý với các bộ
dữ liệu thử nghiệm carving của DFRWS).

**Cách B — ảnh đĩa userdata trích từ emulator.**

Yêu cầu AVD dùng ảnh `google_apis` hoặc `aosp` (**không** dùng
`google_apis_playstore`, vì `adb root` bị chặn):

```bash
adb root
adb shell "dd if=/dev/block/by-name/userdata of=/data/local/tmp/userdata.img bs=4M"
adb pull /data/local/tmp/userdata.img
```

Lưu ý khi báo cáo: ảnh này vẫn chịu FBE, nên tỉ lệ cắt được sẽ thấp. Chính con số
thấp đó là kết quả thực nghiệm có giá trị — nó định lượng được ảnh hưởng của mã
hóa toàn thiết bị lên khả năng phục hồi dữ liệu, và là bằng chứng cho luận điểm ở
mục 2.

## 5. Kiểm chứng tầng 1 và tầng 2 trên emulator (đã chạy, đạt)

Kịch bản đã thực hiện trên AVD `Medium_Phone_API_36.0`:

```bash
DIR=/sdcard/Android/data/com.deviceguard/files
adb push blob1.bin "$DIR/.trashed-1800000000-IMG_0042.jpg"   # 120 000 byte
adb push blob2.bin "$DIR/cache/VID_20250101.mp4"             #  90 000 byte
adb push blob2.bin "$DIR/.thumbnails/thumb_991.jpg"          #  90 000 byte
```

Kết quả quét (giao diện tab Phục hồi): **3 mục trong 1330 ms**, phân loại đúng cả
ba mức độ tin cậy:

| Tệp | Nguồn | Độ tin cậy | Thời điểm xóa suy ra |
|---|---|---|---|
| `IMG_0042.jpg` | `.trashed-` | Cao | 16/12/2026 15:00 |
| `VID_20250101.mp4` | cache | Trung bình | (lấy `lastModified`) |
| `thumb_991.jpg` | `.thumbnails` | Thấp | (lấy `lastModified`) |

Tên tệp được khôi phục đúng về `IMG_0042.jpg` từ chuỗi
`.trashed-1800000000-IMG_0042.jpg`, và mốc thời gian xóa được suy ra đúng từ
epoch `1800000000` trừ 30 ngày.

Kiểm chứng khôi phục — sao chép rồi đối chiếu MD5:

```
c19ac901075d78f089d2a97bd4666b69  .../files/.trashed-1800000000-IMG_0042.jpg
c19ac901075d78f089d2a97bd4666b69  .../files/recovered/IMG_0042.jpg
```

Trùng khớp: tệp khôi phục giống hệt bản gốc, và bản gốc **không bị thay đổi hay
di chuyển**.

## 6. Tóm tắt khả năng và giới hạn

| Tình huống | Khôi phục được? | Tầng |
|---|---|---|
| Ảnh vừa xóa trong ứng dụng Thư viện (Android 11+) | Có, nguyên vẹn | 1 |
| Tệp còn bản sao trong cache của ứng dụng | Có, có thể mất metadata | 2 |
| Ảnh gốc đã mất nhưng còn thumbnail | Một phần, độ phân giải thấp | 2 |
| Tệp xóa vĩnh viễn, máy **chưa root** | Không | — |
| Tệp xóa vĩnh viễn, máy **đã root**, dữ liệu mã hóa FBE | Gần như không | 3 |
| Tệp xóa trên ảnh đĩa/thẻ nhớ **không mã hóa** | Có, nếu chưa bị ghi đè | 3 |
