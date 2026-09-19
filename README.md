# OplusRecordSilent

Giải pháp toàn diện loại bỏ âm thanh thông báo ghi âm cuộc gọi ("Cuộc gọi này đang được ghi âm / The session is being recorded"), ép buộc tự động ghi âm cuộc gọi VoIP (Facebook Messenger, Zalo, Telegram, WhatsApp, v.v.), mở khóa thông báo lưu file và tự động đổi tên file theo định dạng chuẩn trên **ColorOS 16 / Android 16** (OPPO Find X7 Ultra, OnePlus 12, Realme GT5 Pro...).

Dự án cung cấp 2 giải pháp độc lập hoặc có thể kết hợp cùng nhau:
1. **LSPosed Module (`lsposed-module/`)**: Hook runtime qua Vector LSPosed / Zygisk LSPosed.
2. **KernelSU / Magisk Module (`ksu-module/`)**: Can thiệp cấp hệ thống bằng bind mount và background daemon watcher qua KernelSU / Magisk / APatch.

---

## Cấu trúc dự án

```
OplusRecordSilent/
├── lsposed-module/             # Module LSPosed (SilentAICall)
│   ├── AndroidManifest.xml
│   ├── build.py                # Script biên dịch độc lập APK (javac + d8 + aapt2)
│   ├── README.md               # Tài liệu chi tiết module LSPosed
│   ├── assets/
│   │   └── xposed_init         # Entry point hook
│   ├── res/
│   │   └── values/arrays.xml   # Cấu hình scope LSPosed
│   └── src/
│       ├── com/coloros/silentcall/
│       │   └── XposedInit.java # Mã nguồn hook chính (18 hooks)
│       └── de/robv/android/xposed/ # Xposed API Stubs
│
├── ksu-module/                 # Module KernelSU / Magisk (silent_ai_call)
│   ├── module.prop             # Metadata của module
│   ├── service.sh              # Script khởi động bind mount & daemon watcher
│   ├── patch_apk.py            # Script làm rỗng file âm thanh cảnh báo PCM
│   ├── build.py                # Script đóng gói file zip module
│   ├── ColorAccessibilityAssistant_silent.apk # APK đã được làm rỗng file PCM
│   └── README.md               # Tài liệu chi tiết module KernelSU
│
├── .gitignore
└── README.md
```

---

## 1. Phần 1: LSPosed Module (`lsposed-module/`)

### Đặc điểm
- Không cần sửa đổi tệp hệ thống (`/product/app`).
- Can thiệp trực tiếp vào tiến trình `com.coloros.accessibilityassistant` khi ứng dụng khởi chạy.
- Tự động bắt tên người gọi từ thông báo VoIP và lưu tên tệp ngay lúc ghi âm hoàn thành.

### Các can thiệp chính (18 Hooks)
1. **Tắt âm thanh thông báo ghi âm 4 lớp**:
   - Chặn entry point `com.coloros.translate.engine.mixaudio.a.e` và gọi giả lập `onPlayComplete`.
   - Chặn worker player `com.coloros.translate.engine.mixaudio.mix.a.e`.
   - Chặn Downlink PCM stream `com.coloros.translate.engine.mixaudio.mix.c.d`.
   - Lọc stack trace chặn `AudioTrack.play()` từ các class âm thanh thông báo.
2. **Bypass toàn bộ điều kiện chặn tự động ghi âm VoIP**:
   - `SmartVoiceDataManger.isRegionSupportSmartVoice` -> `true` (vượt qua kiểm tra mã vùng).
   - `SubtitlePrefDb.F` -> `true` (vượt qua điều khoản bảo mật v7/v8).
   - `SmartVoiceDataManger.getAutoSmartVoiceSwitchStatus` & `getAutoSmartVoiceEnableStatus` -> `true`.
   - Mở rộng whitelist `getSmartVoiceAppsAddTT` & `getSmartVoiceApps` cho mọi ứng dụng VoIP.
   - `SwitchApp.isChecked` -> `true`.
3. **Mở khóa thông báo đã lưu bản ghi âm**:
   - Chuyển hướng thông báo từ kênh bị khóa `start_record_channel_id` (`mImportance = 0`) sang kênh mới `call_record_channel_v2` (`IMPORTANCE_HIGH`), đảm bảo hiển thị banner và thông báo lên thanh trạng thái.
4. **Tự động đổi tên file**:
   - Cấu trúc: `[AppName]_[CallerName]_[YYYY-MM-DD_HH-mm-ss].aac`
   - Ví dụ: `Messenger_Bố_2026-09-19_18-45-00.aac`.

### Cài đặt
1. Chạy `python lsposed-module/build.py` để tạo `SilentAICall.apk`.
2. Cài đặt `SilentAICall.apk` lên điện thoại.
3. Kích hoạt module trong LSPosed / Vector và chọn scope là **Trợ lý tiếp cận (com.coloros.accessibilityassistant)**.
4. Khởi động lại ứng dụng hoặc khởi động lại máy.

---

## 2. Phần 2: KernelSU Module (`ksu-module/`)

### Đặc điểm
- Hoạt động ở tầng kernel / root, không phụ thuộc vào framework Xposed.
- Sử dụng cơ chế `mount -o bind` đè tệp APK đã làm rỗng âm thanh PCM lên tệp hệ thống trên mọi zygote namespace.
- Chạy một background daemon giám sát thư mục `/storage/emulated/0/Music/Recordings/Call Recordings/` để tự động đổi tên tệp theo người gọi đang đàm thoại.

### Cài đặt
1. Chạy `python ksu-module/build.py` để đóng gói `silent_ai_call_ksu.zip`.
2. Mở KernelSU / Magisk / APatch App -> Chọn Modules -> Install from storage -> chọn `silent_ai_call_ksu.zip`.
3. Khởi động lại thiết bị.

---

## Tác giả & Giấy phép

- Phát triển cho ColorOS 16 / Android 16.
- Giấy phép: MIT License.
