# Đề tài: Nghiên cứu và xây dựng công cụ thu thập, phục hồi và phân tích dữ liệu trên thiết bị Android

## 1. Tên đề tài

**Nghiên cứu và xây dựng công cụ thu thập, phục hồi và phân tích dữ liệu trên thiết bị Android**
(tự khảo sát — self-monitoring)

Chữ "giám sát" đã được bỏ khỏi tên. Lý do không chỉ là câu chữ: nó xác định lại
đối tượng của hệ thống. Một công cụ "giám sát" ngầm định có hai vai — người giám
sát và người bị giám sát — và toàn bộ thiết kế kỹ thuật sẽ xoay quanh việc che
giấu vai thứ nhất khỏi vai thứ hai. Công cụ tự khảo sát chỉ có một vai: người
dùng khảo sát chính thiết bị của mình. Mọi quyết định kiến trúc trong đồ án này
đều bắt nguồn từ đó.

## 2. Bốn nguyên tắc ràng buộc thiết kế

Đây không phải lời hứa suông trong báo cáo — mỗi nguyên tắc đều có điểm neo trong
mã nguồn để hội đồng kiểm chứng được.

| Nguyên tắc | Hiện thực trong mã nguồn |
|---|---|
| Ứng dụng hiển thị icon và tên rõ ràng, không ẩn | `AndroidManifest.xml` chỉ có một `<activity>` `LAUNCHER`, không khai báo `activity-alias` nào để gỡ icon. Ảnh chụp `06-apps.png` cho thấy ứng dụng nằm trong danh sách ứng dụng của hệ thống. |
| Màn hình xin quyền + giải thích mục đích khi cài lần đầu | `ui/screen/OnboardingScreen.kt`; văn bản mục đích của từng nhóm quyền lấy từ `core/PermissionCatalog.kt` (trường `purpose`). |
| Chỉ chạy trên chính thiết bị cài đặt | Không có `<uses-permission android:name="android.permission.INTERNET" />`. Không có `BroadcastReceiver` exported, không có service nhận lệnh. Toàn bộ ghi/đọc đi qua Room cục bộ. |
| Người dùng rút lại được | `ConsentStore.revokeAll()` + `CollectionRepository.wipeAll()`, nối vào nút "Rút lại đồng ý và xóa sạch" trong tab Cài đặt. |

Điểm chặn quan trọng nhất nằm ở `MainActivity`: chừng nào `termsAccepted` còn
`false`, ứng dụng chỉ dựng màn hình Onboarding. Không collector nào chạy trước
thời điểm người dùng bấm đồng ý. `CollectionWorker` và `NotificationLogService`
đều kiểm tra lại cờ này ngay đầu mỗi lần chạy, nên kể cả khi tác vụ nền đã được
xếp lịch từ trước, việc rút đồng ý sẽ làm chúng thoát ngay lập tức.

## 3. Phạm vi ba khâu kỹ thuật

### Khâu 1 — Thu thập (Collection)

| Nhóm dữ liệu | API sử dụng | Tệp |
|---|---|---|
| Thông tin hệ thống, pin, RAM, dung lượng, mạng | `Build`, `BatteryManager`, `ActivityManager`, `StatFs`, `ConnectivityManager` | `data/collector/DeviceInfoCollector.kt` |
| Ứng dụng đã cài + quyền đã cấp | `PackageManager`, `PermissionInfo.protection` | `data/collector/InstalledAppCollector.kt` |
| Thời lượng và số lần mở ứng dụng | `UsageStatsManager.queryUsageStats` / `queryEvents` | `data/collector/UsageStatsCollector.kt` |
| Nhật ký thông báo | `NotificationListenerService` | `data/collector/NotificationLogService.kt` |
| Danh bạ / cuộc gọi / tin nhắn | Content Provider (`ContactsContract`, `CallLog`, `Telephony`) | `data/collector/PersonalDataCollector.kt` |

Nhóm cuối cố ý **chỉ đếm, không đọc nội dung và không ghi vào cơ sở dữ liệu**.
Mục tiêu học thuật ở đây là chứng minh khả năng truy xuất qua Content Provider và
mô hình quyền của Android, không phải sao chép dữ liệu cá nhân. Kết quả hiển thị
tại chỗ rồi biến mất khi đóng màn hình.

### Khâu 2 — Phục hồi (Recovery)

Ba tầng kỹ thuật, xếp theo mức độ can thiệp. Chi tiết đầy đủ và phần thảo luận
giới hạn nằm ở [`03-phuc-hoi-du-lieu.md`](03-phuc-hoi-du-lieu.md).

1. **Thùng rác MediaStore** (`MediaStoreTrashScanner.kt`) — khôi phục nguyên vẹn qua API chính thức.
2. **Tệp còn sót** (`ResidualFileScanner.kt`) — `.trashed-*`, `LOST.DIR`, cache, `.thumbnails`.
3. **Cắt tệp theo chữ ký** (`FileCarver.kt`) — kỹ thuật carving cổ điển, chạy trên ảnh đĩa.

### Khâu 3 — Phân tích (Analysis)

`data/analysis/UsageAnalyzer.kt` tính:

- chuỗi thời gian sử dụng theo ngày,
- phân bố phiên sử dụng theo giờ (24 khung),
- xếp hạng ứng dụng theo thời lượng tiền cảnh,
- tỉ lệ phiên ban đêm (22h–6h),
- diễn biến mức pin giữa các lần chụp trạng thái,
- các **quan sát** (`Insight`) như "ứng dụng giữ nhiều quyền nhạy cảm nhưng không
  được dùng đến".

Toàn bộ là thống kê mô tả, kèm số liệu gốc để người dùng tự kiểm chứng. Không có
"điểm hành vi" hay bất kỳ suy diễn nào về con người — đó là ranh giới giữa phân
tích dữ liệu thiết bị và đánh giá cá nhân.

## 4. Những gì đề tài cố ý KHÔNG làm

Nêu rõ phần này trong báo cáo có giá trị bảo vệ: nó cho thấy các lựa chọn là có
chủ đích, không phải do thiếu năng lực kỹ thuật.

- Không có kênh mạng, không đồng bộ máy chủ, không tài khoản.
- Không có cơ chế nhận lệnh điều khiển từ xa.
- Không ẩn icon, không tự khởi động lại sau khi bị người dùng dừng.
- Không chụp màn hình, không ghi phím, không truy cập máy ảnh/micro.
- Không đọc nội dung tin nhắn hay danh bạ; chỉ đếm bản ghi.
- Không cố vượt qua mã hóa FBE hay yêu cầu root để đọc phân vùng máy thật.

## 5. Môi trường thực nghiệm

Emulator Android Studio là môi trường phát triển tiêu chuẩn, dùng để kiểm thử ứng
dụng trên nhiều phiên bản hệ điều hành. Cấu hình đã dùng cho các kết quả trong
báo cáo:

- AVD `Medium_Phone_API_36.0`, ảnh `google_apis_playstore`, Android 16 (API 36), arm64-v8a.
- Máy chủ: macOS, JDK 21 (JBR đi kèm Android Studio), Gradle 8.12.1, AGP 8.8.0, Kotlin 2.1.0.

Ứng dụng tự nhận diện môi trường máy ảo (`DeviceInfoCollector.detectEmulator`) và
hiển thị cảnh báo trên màn hình Tổng quan, để số liệu phần cứng đo trên emulator
không bị nhầm là số liệu máy thật.
