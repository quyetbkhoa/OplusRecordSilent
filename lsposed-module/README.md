# SilentAICall - LSPosed Module for ColorOS 16 / Android 16 (v1.0.1)

Module LSPosed (hỗ trợ Zygisk LSPosed, JingMatrix Vector v2.2+, v.v.) chuyên dụng cho ColorOS 16 (OPPO Find X7 Ultra, OnePlus 12, Realme GT5 Pro...).

## Cơ chế hoạt động (v1.0.1)

Phiên bản 1.0.1 áp dụng triết lý **Audio File Prompt Interception** sạch và an toàn tuyệt đối, không can thiệp sâu vào state machine hay logic nghiệp vụ của ColorOS:

1. **Làm rỗng tệp âm thanh thông báo (Audio Prompt Silencing)**:
   - **AssetManager Interception**: Hook `AssetManager.open()` khi ứng dụng mở các tệp `mix/*.pcm` (`record_en_US.pcm`, `subtitle_en_US.pcm`, `summary_en_US.pcm`), trả về `ByteArrayInputStream` rỗng (0 bytes).
   - **FileInputStream Redirection**: Tự động tạo tệp `silent_prompt.pcm` (0 bytes) trong cache của ứng dụng và chuyển hướng mọi truy vấn đọc tệp từ `/system_ext/etc/recording-prompt/` hoặc `mix/*.pcm` về tệp rỗng này.
   - **AudioFileManager Hook**: Hook các hàm lấy đường dẫn tệp âm thanh trong `AudioFileManager` (`b4.a.a` và `b4.a.b`), luôn trả về tệp rỗng `silent_prompt.pcm`.
   - **AudioMixer Engine Hook**: Chuyển hướng `audioFile` trong `AudioMixer.playAudioData` (`com.coloros.translate.engine.mixaudio.mix.a.e`) về tệp rỗng.
   - **AudioTrack Safety Net**: Hook `AudioTrack.write()` xóa sạch buffer PCM nếu phát hiện luồng phát âm thông báo.

2. **Bật tự động ghi âm an toàn (Safe Auto-Record)**:
   - Hook cấu hình danh sách ứng dụng được hỗ trợ (`support_apps_auto_record`, `support_apps_smart_voice`), tự động chuyển cờ `"isChecked": false` thành `true`.
   - Hook `SwitchApp.isChecked()` luôn trả về `true` cho các ứng dụng VoIP (Messenger, Zalo, Telegram, WhatsApp, v.v.).

## Cách cài đặt

1. Tải về `SilentAICall.apk` từ mục Releases.
2. Cài đặt APK lên thiết bị.
3. Mở LSPosed / Vector -> Kích hoạt module **ColorOS Silent AI Call** -> Chọn scope **Trợ lý tiếp cận (com.coloros.accessibilityassistant)**.
4. Buộc dừng hoặc khởi động lại ứng dụng `com.coloros.accessibilityassistant`.

## Cách biên dịch (Dành cho nhà phát triển)

```bash
python build.py
```
Yêu cầu môi trường có Android SDK (`aapt2`, `d8`, `javac` từ Android Studio / JDK 17+).
File APK đầu ra sẽ được tạo tại `SilentAICall.apk`.
