# OplusRecordSilent

Giải pháp toàn diện loại bỏ âm thanh thông báo ghi âm cuộc gọi ("Cuộc gọi này đang được ghi âm / The session is being recorded") và ép buộc tự động ghi âm cuộc gọi VoIP (Facebook Messenger, Zalo, Telegram, WhatsApp, v.v.) trên **ColorOS 16 / Android 16** (OPPO Find X7 Ultra, OnePlus 12, Realme GT5 Pro...).

Dự án cung cấp 2 giải pháp độc lập hoặc có thể kết hợp cùng nhau:
1. **LSPosed Module (`lsposed-module/`)**: Hook runtime qua Vector LSPosed / Zygisk LSPosed.
2. **KernelSU / Magisk Module (`ksu-module/`)**: Can thiệp cấp hệ thống bằng bind mount qua KernelSU / Magisk / APatch.

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
│       │   └── XposedInit.java # Mã nguồn hook chính
│       └── de/robv/android/xposed/ # Xposed API Stubs
│
├── ksu-module/                 # Module KernelSU / Magisk (silent_ai_call)
│   ├── module.prop             # Metadata của module
│   ├── service.sh              # Script khởi động bind mount
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
- Không cần sửa đổi tệp hệ thống (`/product/app` hay `/my_product/app`).
- Can thiệp trực tiếp vào tiến trình `com.coloros.accessibilityassistant` khi ứng dụng khởi chạy.
- Vô hiệu hóa triệt để âm thanh cảnh báo tại nhiều tầng: từ tầng logic kiểm tra (`MixPromptAudioManager`) đến tầng phát âm (`MixAudioEngineHandler`, `AudioTrack`).

### Các can thiệp chính
1. **Tắt âm thanh thông báo ghi âm đa tầng**:
   - **Tầng 0 (Source)**: Hook `MixPromptAudioManager` (`isNeedMixAudio` / `f`, `g`, `d`, `e`) luôn trả về `false`, bỏ qua hoàn toàn việc phát âm thông báo và chuyển thẳng sang khởi động ghi âm. Hook `mixVoipStartPromptAudio` (`n`) và `mixCallAudio` (`h`, `k`) gọi thẳng callback hoàn tất mà không phát audio.
   - **Tầng 1 (Remote Handler)**: Chặn `MixAudioEngineHandler.b` và giả lập gọi `onPlayComplete`.
   - **Tầng 2 (Engine Impl)**: Chặn `engine.c.mixPromptAudio` và gọi `onPlayComplete`.
   - **Tầng 3 (Manager)**: Chặn `com.coloros.translate.engine.mixaudio.a.e` và `h`.
   - **Tầng 4 (Worker)**: Chặn worker player `com.coloros.translate.engine.mixaudio.mix.a.e`.
   - **Tầng 5 (PCM Stream)**: Chặn Downlink PCM stream `com.coloros.translate.engine.mixaudio.mix.c.d`.
   - **Tầng 6 (Safety Net)**: Lọc stack trace chặn `AudioTrack.play()` từ các class âm thanh thông báo.
2. **Bypass toàn bộ điều kiện chặn tự động ghi âm VoIP**:
   - `SmartVoiceDataManger.isRegionSupportSmartVoice` -> `true` (vượt qua kiểm tra mã vùng).
   - `SubtitlePrefDb.F` -> `true` (vượt qua điều khoản bảo mật v7/v8).
   - `SmartVoiceDataManger.getAutoSmartVoiceSwitchStatus` & `getAutoSmartVoiceEnableStatus` -> `true`.
   - Mở rộng whitelist `getSmartVoiceAppsAddTT` & `getSmartVoiceApps` cho mọi ứng dụng VoIP (Messenger, Zalo, Telegram, WhatsApp...).
   - `SwitchApp.isChecked` -> `true`.
3. **Mở khóa thông báo đã lưu bản ghi âm**:
   - Chuyển hướng thông báo từ kênh bị khóa `start_record_channel_id` sang kênh mới `call_record_channel_v2` (`IMPORTANCE_HIGH`), đảm bảo hiển thị banner và thông báo lên thanh trạng thái.

### Cài đặt
1. Chạy `python lsposed-module/build.py` để tạo `SilentAICall.apk`.
2. Cài đặt `SilentAICall.apk` lên điện thoại.
3. Kích hoạt module trong LSPosed / Vector và chọn scope là **Trợ lý tiếp cận (com.coloros.accessibilityassistant)**.
4. Khởi động lại ứng dụng hoặc khởi động lại máy.

---

## 2. Phần 2: KernelSU Module (`ksu-module/`)

### Đặc điểm
- Hoạt động ở tầng kernel / root, không phụ thuộc vào framework Xposed.
- Tự động nhận diện đường dẫn cài đặt của `ColorAccessibilityAssistant.apk` trên hệ thống (`pm path` và quét các phân vùng `/my_product`, `/product`, `/system`...).
- Sử dụng cơ chế `mount -o bind` đè tệp APK đã làm rỗng âm thanh PCM lên tệp hệ thống trên mọi zygote namespace.

### Cài đặt
1. Chạy `python ksu-module/build.py` để đóng gói `silent_ai_call_ksu.zip`.
2. Mở KernelSU / Magisk / APatch App -> Chọn Modules -> Install from storage -> chọn `silent_ai_call_ksu.zip`.
3. Khởi động lại thiết bị.

---

## Tác giả & Giấy phép

- Phát triển cho ColorOS 16 / Android 16.
- Giấy phép: MIT License.
