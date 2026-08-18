# DeviceGuard

Công cụ thu thập, phục hồi và phân tích dữ liệu trên **chính thiết bị Android đang
cài ứng dụng**. Đồ án tốt nghiệp — hướng tự khảo sát (self-monitoring), không phải
công cụ giám sát thiết bị khác.

Ứng dụng không có quyền `INTERNET`, không có thành phần nhận lệnh từ xa, luôn hiển
thị icon và tên trong danh sách ứng dụng, và mọi dữ liệu đều nằm trong bộ nhớ máy.

## Tài liệu

| Tệp | Nội dung |
|---|---|
| [`docs/01-de-tai.md`](docs/01-de-tai.md) | Định hình đề tài, nguyên tắc ràng buộc, phạm vi ba khâu |
| [`docs/02-kien-truc.md`](docs/02-kien-truc.md) | Kiến trúc, lược đồ dữ liệu, luồng thu thập |
| [`docs/03-phuc-hoi-du-lieu.md`](docs/03-phuc-hoi-du-lieu.md) | Cơ sở forensics, giới hạn kỹ thuật, kịch bản thực nghiệm |

## Chức năng

**Thu thập** — thông tin hệ thống · pin · RAM · dung lượng · mạng · kiểm kê ứng
dụng kèm quyền nhạy cảm đã cấp · thời lượng và số lần mở từng ứng dụng · nhật ký
thông báo · thống kê danh bạ/cuộc gọi/tin nhắn (chỉ đếm, tùy chọn).

**Phục hồi** — thùng rác MediaStore (Android 11+) · tệp còn sót trong
`.trashed-*`/`LOST.DIR`/cache/`.thumbnails` · cắt tệp theo chữ ký trên ảnh đĩa
(7 định dạng).

**Phân tích** — biểu đồ thời gian dùng theo ngày · phân bố phiên theo giờ · xếp
hạng ứng dụng · diễn biến pin · các quan sát tự động (ứng dụng giữ quyền nhạy cảm
nhưng không được dùng, tỉ lệ dùng máy ban đêm, …).

## Yêu cầu

- Android Studio (JBR đi kèm dùng làm JDK — xem `gradle.properties`)
- Android SDK API 35 trở lên
- Thiết bị hoặc AVD chạy Android 8.0 (API 26) trở lên

## Xây dựng và chạy

```bash
# Biên dịch
./gradlew :app:assembleDebug

# Chạy kiểm thử thuật toán carving (JVM, không cần thiết bị)
./gradlew :app:testDebugUnitTest

# Cài lên thiết bị/emulator đang kết nối
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Dự án khai báo Java toolchain 17 trong `app/build.gradle.kts`, nên không phụ
thuộc vào JDK mặc định của máy. Nếu Gradle báo không tìm thấy JDK 17, cài thêm một
bản JDK 17 bất kỳ (Temurin, Corretto…) hoặc bật auto-provisioning của Gradle.

Khai báo toolchain này cũng xử lý luôn lỗi `jlink` của `JdkImageTransform` (AGP)
gặp phải khi JDK chạy Gradle là GraalVM.

## Cấp quyền để thử đầy đủ chức năng

Một số quyền phải cấp qua Cài đặt hệ thống. Trên emulator có thể cấp nhanh bằng
adb:

```bash
adb shell appops set com.deviceguard android:get_usage_stats allow
adb shell pm grant com.deviceguard android.permission.READ_MEDIA_IMAGES
adb shell pm grant com.deviceguard android.permission.READ_MEDIA_VIDEO
adb shell pm grant com.deviceguard android.permission.POST_NOTIFICATIONS
```

Nhật ký thông báo và quyền quản lý toàn bộ tệp phải bật thủ công trong Cài đặt —
đây là chủ ý: hai quyền này Android bắt buộc phải có thao tác rõ ràng của người
dùng.

## Cấu trúc mã nguồn

```
app/src/main/java/com/deviceguard/
├── core/            ConsentStore, PermissionCatalog
├── data/
│   ├── collector/   5 collector cho khâu Thu thập
│   ├── recovery/    3 tầng của khâu Phục hồi + FileCarver
│   ├── analysis/    UsageAnalyzer (khâu Phân tích)
│   ├── local/       Room: 6 entity, 5 DAO
│   └── repository/  CollectionRepository, RecoveryRepository
├── work/            CollectionWorker, CollectionScheduler
├── ui/              Compose: theme, component, screen, viewmodel, navigation
├── DeviceGuardApp.kt
└── MainActivity.kt
```
