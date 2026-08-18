# Kiến trúc hệ thống

## 1. Tổng thể

Kiến trúc ba lớp, một chiều phụ thuộc: `ui → data → core`. Không lớp nào ở dưới
biết về lớp ở trên.

```
┌──────────────────────────────────────────────────────────────┐
│  ui/                                                         │
│  MainActivity → OnboardingScreen | DeviceGuardNavigation     │
│                    ├── DashboardScreen   ┐                   │
│                    ├── UsageScreen       ├─ OverviewViewModel│
│                    ├── AppsScreen        ─── AppsViewModel   │
│                    ├── RecoveryScreen    ─── RecoveryViewModel│
│                    └── SettingsScreen    ─── SettingsViewModel│
└───────────────────────────┬──────────────────────────────────┘
                            │ StateFlow
┌───────────────────────────▼──────────────────────────────────┐
│  data/                                                       │
│  repository/  CollectionRepository   RecoveryRepository      │
│  collector/   DeviceInfo · InstalledApp · UsageStats ·       │
│               NotificationLog · PersonalData                 │
│  recovery/    MediaStoreTrash · ResidualFile · FileCarver    │
│  analysis/    UsageAnalyzer                                  │
│  local/       Room: 6 entity, 5 DAO                          │
└───────────────────────────┬──────────────────────────────────┘
                            │
┌───────────────────────────▼──────────────────────────────────┐
│  core/        ConsentStore (DataStore) · PermissionCatalog    │
└──────────────────────────────────────────────────────────────┘

work/  CollectionWorker ← CollectionScheduler (WorkManager, chu kỳ 6 giờ)
```

`AppContainer` trong `DeviceGuardApp.kt` là container phụ thuộc viết tay. Đề tài
không dùng Hilt: với một đồ thị phụ thuộc nhỏ như thế này, việc để toàn bộ quan
hệ hiện ra ở một tệp duy nhất giúp phần trình bày kiến trúc ngắn và kiểm chứng
được, đổi lại là mất khả năng inject cho unit test — chấp nhận được vì phần lõi
cần test nhất (`FileCarver`, `UsageAnalyzer`) đã được viết thuần Kotlin, không
phụ thuộc `Context`.

## 2. Lớp dữ liệu (Room)

| Entity | Khóa chính | Vai trò |
|---|---|---|
| `DeviceSnapshotEntity` | `id` tự tăng | Ảnh chụp trạng thái máy tại một thời điểm |
| `InstalledAppEntity` | `(packageName, capturedAt)` | Kiểm kê ứng dụng, giữ nhiều thế hệ để so sánh |
| `AppUsageEntity` | `(packageName, dayStart)` | Thời lượng tiền cảnh theo ngày |
| `UsageEventEntity` | `id` tự tăng | Từng sự kiện chuyển tiền cảnh / bật tắt màn hình |
| `NotificationLogEntity` | `id` tự tăng | Nhật ký thông báo (chỉ khi bật công tắc) |
| `RecoveryCandidateEntity` | `id` tự tăng | Ứng viên khôi phục do module Recovery tìm được |

Hai lựa chọn thiết kế đáng nêu trong báo cáo:

**Khóa chính ghép `(packageName, capturedAt)` cho `installed_app`.** Bảng này
không lưu "trạng thái hiện tại" mà lưu **chuỗi ảnh kiểm kê**. Nhờ vậy truy vấn
`observeNewlyInstalled()` phát hiện được ứng dụng mới xuất hiện chỉ bằng SQL
thuần — so sánh tập gói ở lần chụp mới nhất với lần chụp liền trước — mà không
cần bảng lịch sử riêng.

**Lưu lại `UsageEventEntity` vào Room.** `UsageStatsManager.queryEvents` chỉ giữ
lịch sử vài ngày. Muốn dựng chuỗi thời gian dài hơn cho phần phân tích thì phải
tự tích lũy. `CollectionRepository` truy vấn `lastEventTimestamp()` rồi chỉ lấy
sự kiện mới hơn mốc đó, nên các lần chạy chồng nhau không sinh dữ liệu trùng.

## 3. Luồng thu thập

```
Người dùng bấm "Thu thập ngay"        WorkManager (6 giờ/lần)
            │                                    │
            └────────────┬───────────────────────┘
                         ▼
          CollectionRepository.collectNow()
                         │
      ┌──────────────────┼──────────────────┐
      ▼                  ▼                  ▼
  đã đồng ý?      DeviceInfoCollector  InstalledAppCollector
  (chưa → thoát)         │                  │
                         ▼                  ▼
                  DeviceSnapshotEntity   InstalledAppEntity[]
                         │                  │
                         └────────┬─────────┘
                                  ▼
                     có quyền UsageStats?
                                  │ có
                                  ▼
                UsageStatsCollector → AppUsageEntity[] + UsageEventEntity[]
                                  │
                                  ▼
                                Room
                                  │
                                  ▼  Flow
                          UsageAnalyzer → UI
```

Nguyên tắc chịu lỗi: thiếu quyền nào thì bỏ qua đúng phần đó, không hủy cả lượt
chạy. Một lượt thu thập luôn ghi được ít nhất một `DeviceSnapshotEntity`, kể cả
khi người dùng chưa cấp quyền nào ngoài mức mặc định.

## 4. Lớp giao diện

- **Jetpack Compose + Material 3**, một `Activity` duy nhất, điều hướng bằng
  `navigation-compose` với `NavigationBar` 5 tab.
- **Biểu đồ tự vẽ bằng `Canvas`** (`ui/component/Charts.kt`) thay vì
  MPAndroidChart. Lý do: MPAndroidChart là thư viện dựng trên `View`, đưa vào sẽ
  kéo theo lớp interop `AndroidView` và một mô hình dựng hình thứ hai trong ứng
  dụng. Số biểu đồ cần dùng chỉ có ba dạng (cột, đường, thanh xếp hạng), tự vẽ
  rẻ hơn phụ thuộc. Nếu báo cáo yêu cầu đúng thư viện đã nêu trong đề cương,
  việc thay thế chỉ động tới `Charts.kt`.
- **`StateFlow` + `stateIn(WhileSubscribed(5s))`**: dữ liệu ngừng chảy khi màn
  hình rời khỏi tiền cảnh, tránh giữ truy vấn Room sống vô ích.

## 5. Mô hình đồng ý và quyền

`core/PermissionCatalog.kt` mô tả mỗi nhóm quyền bằng một `PermissionSpec` gồm
tiêu đề, **mục đích viết cho người dùng đọc**, cơ chế cấp, và danh sách quyền
runtime tương ứng. Cả `OnboardingScreen` lẫn tab Cài đặt đều dựng giao diện từ
đúng danh sách này, nên không thể xảy ra tình trạng ứng dụng xin một quyền không
được mô tả ở đâu cả.

Ba quyền không cấp được bằng hộp thoại runtime, phải mở màn hình Cài đặt hệ
thống — `PermissionCatalog.settingsIntent()` trả về đúng `Intent` cho từng loại:

| Quyền | Intent |
|---|---|
| Thống kê sử dụng | `Settings.ACTION_USAGE_ACCESS_SETTINGS` |
| Nhật ký thông báo | `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS` |
| Quản lý toàn bộ tệp | `Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` |

Trạng thái của chúng cũng phải kiểm tra theo cách riêng, không dùng
`checkSelfPermission` được: qua `AppOpsManager.unsafeCheckOpNoThrow`,
`Settings.Secure.enabled_notification_listeners`, và
`Environment.isExternalStorageManager()`.

## 6. Công cụ xây dựng

| Thành phần | Phiên bản | Ghi chú |
|---|---|---|
| Gradle | 8.12.1 | wrapper kèm trong repo |
| Android Gradle Plugin | 8.8.0 | |
| Kotlin | 2.1.0 | plugin Compose Compiler đi kèm Kotlin |
| compileSdk / targetSdk | 35 | |
| minSdk | 26 | Android 8.0 |
| Room | 2.6.1 | sinh mã bằng KSP |
| WorkManager | 2.10.0 | |

Dự án cố định JDK biên dịch bằng **Java toolchain 17** khai báo trong
`app/build.gradle.kts`, không trỏ cứng `org.gradle.java.home`. Lựa chọn này giải
quyết một vấn đề gặp thật trong quá trình làm: JDK mặc định của máy phát triển là
GraalVM 21, và `JdkImageTransform` của AGP thất bại khi gọi `jlink` trên JDK đó.
Trỏ cứng đường dẫn JBR của Android Studio cũng sửa được lỗi, nhưng làm mã nguồn
không biên dịch được trên máy khác — toolchain giữ được cả hai.
