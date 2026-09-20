# OplusRecordSilent (v1.0.1)

Giải pháp toàn diện loại bỏ âm thanh thông báo ghi âm cuộc gọi ("Cuộc gọi này đang được ghi âm / The session is being recorded") và hỗ trợ tự động ghi âm cuộc gọi trên **ColorOS 16 / Android 16** (OPPO Find X7 Ultra, OnePlus 12, Realme GT5 Pro...).

Dự án cung cấp 2 giải pháp độc lập tùy theo nhu cầu của bạn:
1. **LSPosed Module (`lsposed-module/`)**: Hook runtime không can thiệp tệp hệ thống, hoạt động qua Vector LSPosed / Zygisk LSPosed.
2. **KernelSU / Magisk Module (`ksu-module/`)**: Bind mount APK đã làm rỗng âm thanh PCM ở cấp hệ thống (kernel/root), không cần LSPosed.

---

## Cấu trúc dự án

```
OplusRecordSilent/
├── lsposed-module/             # Module LSPosed (SilentAICall)
│   ├── AndroidManifest.xml
│   ├── build.py                # Script biên dịch APK (javac + d8 + aapt2)
│   ├── README.md               # Tài liệu chi tiết module LSPosed
│   ├── assets/
│   │   └── xposed_init         # Entry point hook
│   ├── res/
│   │   └── values/arrays.xml   # Cấu hình scope LSPosed
│   └── src/
│       ├── com/coloros/silentcall/
│       │   └── XposedInit.java # Mã nguồn hook chính (AssetManager, FileInputStream, AudioFileManager)
│       └── de/robv/android/xposed/ # Xposed API Stubs
│
├── ksu-module/                 # Module KernelSU / Magisk / APatch (silent-ai-call)
│   ├── module.prop             # Metadata của module (v1.0.1)
│   ├── post-fs-data.sh         # Script bind mount sớm trước khi zygote khởi chạy
│   ├── service.sh              # Script bind mount bổ sung & khởi động lại app
│   ├── patch_apk.py            # Script làm rỗng file âm thanh cảnh báo PCM trong APK
│   ├── build.py                # Script đóng gói file zip module
│   ├── ColorAccessibilityAssistant_silent.apk # APK đã được làm rỗng file PCM
│   └── README.md               # Tài liệu chi tiết module KernelSU
│
├── .gitignore
└── README.md
```

---

## 1. Module LSPosed (`lsposed-module/`)

### Đặc điểm & Cơ chế
- Không cần sửa đổi hay mount tệp hệ thống.
- Chặn trực tiếp tại tầng nạp tệp âm thanh thông báo (`AssetManager.open`, `FileInputStream`, `AudioFileManager`, `AudioMixer`, `AudioTrack.write`), không làm hỏng state machine hay gây lỗi "Chức năng không hoạt động trong chế độ hiện tại".
- Tự động bật ghi âm cho các ứng dụng VoIP được cấu hình.

### Cài đặt
1. Tải về `SilentAICall.apk` từ [Releases](https://github.com/quyetbkhoa/OplusRecordSilent/releases).
2. Cài đặt `SilentAICall.apk` lên thiết bị.
3. Mở ứng dụng LSPosed / Vector, kích hoạt module và chọn scope **Trợ lý tiếp cận (com.coloros.accessibilityassistant)**.
4. Buộc dừng hoặc khởi động lại ứng dụng Trợ lý tiếp cận.

---

## 2. Module KernelSU / Magisk (`ksu-module/`)

### Đặc điểm & Cơ chế
- Hoạt động ở tầng kernel / root, hoàn toàn không phụ thuộc vào framework Xposed.
- Sử dụng cơ chế dual-stage bind mount (`post-fs-data.sh` trước khi zygote khởi chạy + `service.sh` hỗ trợ nsenter zygote & app namespaces) đè APK đã làm rỗng các tệp âm thanh PCM lên `/product/app/ColorAccessibilityAssistant/ColorAccessibilityAssistant.apk`.

### Cài đặt
1. Tải về `silent_ai_call_ksu.zip` từ [Releases](https://github.com/quyetbkhoa/OplusRecordSilent/releases).
2. Mở ứng dụng KernelSU / Magisk / APatch -> **Modules** -> **Install from storage** -> chọn file zip.
3. Khởi động lại thiết bị.

---

## Tác giả & Giấy phép

- Phát triển cho ColorOS 16 / Android 16.
- Giấy phép: MIT License.
