# BỐI CẢNH DỰ ÁN: OplusRecordSilent (ColorOS 16 / Android 16)

Tài liệu này tổng hợp toàn bộ bối cảnh kỹ thuật, nguyên nhân gốc rễ, kiến trúc giải pháp và trạng thái hiện tại của dự án **OplusRecordSilent** trên **ColorOS 16 (Android 16)** để làm tài liệu bàn giao hoặc sử dụng tiếp tục trong các session phát triển tiếp theo.

- **Thiết bị thử nghiệm**: OPPO Find X7 Ultra (PHY110), ColorOS 16 (Android 16).
- **Môi trường Root**: KernelSU / APatch + Zygisk Next + Vector LSPosed v2.2+.
- **Repository chính thức**: [https://github.com/quyetbkhoa/OplusRecordSilent.git](https://github.com/quyetbkhoa/OplusRecordSilent.git) (nhánh `main`).

---

## 1. MỤC TIÊU DỰ ÁN

1. **Tắt triệt để âm thanh cảnh báo ghi âm cuộc gọi**: Loại bỏ hoàn toàn âm thanh phát ra ("Cuộc gọi này đang được ghi âm" / "The session is being recorded") ở cả 2 đầu đàm thoại khi bắt đầu ghi âm.
2. **Ép buộc tự động ghi âm cuộc gọi VoIP**: Tự động kích hoạt ghi âm cuộc gọi trên các ứng dụng thoại OTT mạng như Facebook Messenger (`com.facebook.orca`), Zalo (`com.zing.zalo`), Telegram (`org.telegram.messenger`), WhatsApp, v.v.
3. **Mở khóa thông báo "Đã lưu bản ghi âm"**: Sửa lỗi ColorOS 16 âm thầm hủy thông báo lưu file ghi âm do kênh thông báo bị khóa `mImportance = 0`.

---

## 2. PHÂN TÍCH KỸ THUẬT & NGUYÊN NHÂN GỐC RỄ

### A. Cơ chế tắt âm thông báo (Announcement Silencing)
- **7 tầng can thiệp trong LSPosed (`XposedInit.java`)**:
  - **Tầng 0 (Source)**: Hook `com.coloros.accessibilityassistant.mixaudio.a` (MixPromptAudioManager):
    - `isNeedMixAudio` (`f`, `g`, `d`, `e`) luôn trả về `false`, khiến hệ thống bỏ qua toàn bộ quy trình phát âm thanh và chuyển thẳng sang `startCallAsr()`.
    - `mixVoipStartPromptAudio` (`n`) và `mixCallAudio` (`h`, `k`) gọi thẳng callback hoàn tất mà không phát audio.
    - Chặn các phương thức `l`, `p`, `o`.
  - **Tầng 1 (Remote Handler)**: Hook `com.coloros.translate.engine.remote.MixAudioEngineHandler.b` - Chặn gọi remote engine và giả lập gọi callback `onPlayComplete`.
  - **Tầng 2 (Engine Impl)**: Hook `com.coloros.translate.engine.mixaudio.engine.c.mixPromptAudio` - Chặn phát âm prompt và giả lập gọi callback.
  - **Tầng 3 (Manager)**: Hook `com.coloros.translate.engine.mixaudio.a.e` và `h`.
  - **Tầng 4 (Worker Player)**: Hook `com.coloros.translate.engine.mixaudio.mix.a.e` - Chặn worker player trực tiếp.
  - **Tầng 5 (PCM Stream)**: Hook `com.coloros.translate.engine.mixaudio.mix.c.d` - Chặn luồng Downlink PCM stream.
  - **Tầng 6 (Safety Net)**: Hook `AudioTrack.play()` - Lưới an toàn lọc Call Stack chặn mọi lệnh `play()` từ các class âm thanh thông báo thuộc `mixaudio` / `translate.engine`.
- **Tầng KernelSU (`service.sh`)**:
  - Làm rỗng (0 bytes) các tệp âm thanh cảnh báo `assets/mix/*.pcm` bên trong `ColorAccessibilityAssistant_silent.apk`.
  - Tự động dò tìm đường dẫn thực tế của `ColorAccessibilityAssistant.apk` trên máy qua `pm path` và duyệt các phân vùng hệ thống (`/my_product`, `/product`, `/system`, `/system_ext`).
  - Dùng `mount -o bind` đè APK này lên trên cả global namespace và toàn bộ namespace của `zygote64`/`zygote`.

### B. Nguyên nhân không lưu file ghi âm VoIP (Missing Audio File)
- Khi cuộc gọi VoIP bắt đầu, dịch vụ âm thanh ColorOS `OplusAtlasService` (`com.oplus.atlas`) gửi broadcast `android.media.ACTION_AUDIO_VOIP_CALL_RECORD_STATE` với `VoiceCallState = true` và `VoiceCallPackage = <tên package>`.
- `VoipCallRecordReceiver.onReceive` tiếp nhận broadcast nhưng thực hiện 6 điều kiện kiểm tra nghiêm ngặt:
  1. `SmartVoiceDataManger.isRegionSupportSmartVoice()`: Kiểm tra mã vùng (bị trượt nếu vùng máy không thuộc danh sách hỗ trợ chính thức).
  2. `SubtitlePrefDb.F()`: Kiểm tra điều khoản bảo mật (`subtitle_statement_v7`/`v8`).
  3. `SmartVoiceDataManger.getAutoSmartVoiceSwitchStatus()`: Kiểm tra công tắc tự động ghi âm.
  4. `SmartVoiceDataManger.getAutoSmartVoiceEnableStatus()`: Kiểm tra tính năng Smart Voice.
  5. `SmartVoiceDataManger.getSmartVoiceAppsAddTT(boolean)`: Danh sách whitelist ứng dụng VoIP (mặc định thiếu Messenger, Zalo...).
  6. `SwitchApp.isChecked()`: Kiểm tra trạng thái kích hoạt của từng ứng dụng.
- **Hậu quả**: Khi một trong các điều kiện trên bị trượt, `VoipCallRecordReceiver` không khởi tạo `AudioFileAACHelper` (`initAudioFileHelper`). Khi kết thúc cuộc gọi (`state = false`), helper không có file đang ghi (`no file need save`), sinh lỗi `event_asr_error` và hoàn toàn không tạo file `.aac`.
- **Giải pháp đã triển khai**: Hook toàn bộ 6 điểm kiểm tra trên trong `XposedInit.java` để luôn trả về `true` và tự động bổ sung mọi package VoIP vào whitelist.

### C. Nguyên nhân thông báo không hiển thị (Notification Blocked)
- Lệnh `dumpsys notification` phát hiện kênh `start_record_channel_id` của `com.coloros.accessibilityassistant` bị đặt `mImportance = 0` (`IMPORTANCE_NONE`), khiến hệ thống Android âm thầm loại bỏ thông báo lưu file.
- **Giải pháp đã triển khai**: Hook `NotificationManager.notify` trong `XposedInit.java` để tự động tạo và chuyển hướng các thông báo lưu file sang kênh mới `call_record_channel_v2` với độ ưu tiên cao (`IMPORTANCE_HIGH`), đảm bảo hiện banner thông báo lên thanh trạng thái.

---

## 3. CẤU TRÚC REPOSITORY HIỆN TẠI

```
OplusRecordSilent/
├── lsposed-module/             # Module LSPosed (SilentAICall)
│   ├── AndroidManifest.xml
│   ├── build.py                # Script biên dịch độc lập APK (javac + d8 + aapt2)
│   ├── README.md               # Tài liệu chi tiết module LSPosed
│   ├── SilentAICall.apk        # APK đã biên dịch sẵn
│   └── src/
│       ├── com/coloros/silentcall/
│       │   └── XposedInit.java # Mã nguồn chính chứa các hooks đa tầng
│       └── de/robv/android/xposed/ # Xposed API Stubs
│
├── ksu-module/                 # Module KernelSU / Magisk (silent_ai_call)
│   ├── module.prop             # Metadata module
│   ├── service.sh              # Script bind mount zygote
│   ├── patch_apk.py            # Script làm rỗng file PCM âm thanh cảnh báo
│   ├── build.py                # Script đóng gói file zip module KSU
│   ├── ColorAccessibilityAssistant_silent.apk # APK đã patch 0-byte PCM
│   └── README.md               # Tài liệu cơ chế root bind mount
│
├── .gitignore
├── README.md                   # Hướng dẫn tổng quan & cài đặt
└── PROJECT_CONTEXT.md          # File tài liệu bàn giao ngữ cảnh dự án
```

---

## 4. TRẠNG THÁI HIỆN TẠI & BƯỚC TIẾP THEO

1. **Trạng thái**:
   - Đã nâng cấp cơ chế tắt âm thành 7 tầng toàn diện, giải quyết triệt để vấn đề âm thanh "The session is being recorded" phát ra từ nguồn `MixPromptAudioManager`.
   - Đã lược bỏ chức năng tự động đổi tên file theo yêu cầu, tập trung cốt lõi vào tắt âm thanh cảnh báo và ép tự động ghi âm cuộc gọi.
   - APK và module ZIP đã được biên dịch sẵn sàng.
2. **Kế hoạch kiểm tra nghiệm thu**:
   - Thực hiện cuộc gọi thử nghiệm (Messenger hoặc Zalo) đàm thoại 2 chiều khoảng 10-15 giây.
   - Kiểm tra các tiêu chí:
     1. Âm thanh cảnh báo ghi âm hoàn toàn im lặng.
     2. File `.aac` tự động xuất hiện tại `/storage/emulated/0/Music/Recordings/Call Recordings/`.
     3. Banner / thông báo "Đã lưu bản ghi âm" xuất hiện trên màn hình.
     4. Bản ghi âm xuất hiện trong danh sách của ứng dụng Ghi âm (Sound Recorder).
