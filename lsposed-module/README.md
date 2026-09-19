# SilentAICall - LSPosed Module for ColorOS 16 / Android 16

Module LSPosed (hỗ trợ Zygisk LSPosed, JingMatrix Vector v2.2+, v.v.) chuyên dụng cho ColorOS 16 (OPPO Find X7 Ultra, OnePlus 12, Realme GT5 Pro...).

## Tính năng chính

1. **Tắt hoàn toàn âm thanh thông báo ghi âm (Announcement Silencing - 4 Layers)**:
   - **Layer 1**: Hook `com.coloros.translate.engine.mixaudio.a.e` - Chặn phát âm thanh và giả lập callback `onPlayComplete`.
   - **Layer 2**: Hook `com.coloros.translate.engine.mixaudio.mix.a.e` - Chặn worker player trực tiếp.
   - **Layer 3**: Hook `com.coloros.translate.engine.mixaudio.mix.c.d` - Chặn luồng Downlink PCM player.
   - **Layer 4**: Hook `AudioTrack.play()` - Lưới an toàn lọc theo Call Stack chặn mọi lệnh `play()` từ package `mixaudio` / `translate.engine`.

2. **Bắt buộc tự động ghi âm VoIP (Auto-Record & Bypass All Checks)**:
   - Bypass kiểm tra vùng (`SmartVoiceDataManger.isRegionSupportSmartVoice` -> `true`).
   - Bypass điều khoản bảo mật (`SubtitlePrefDb.F` -> `true`).
   - Ép bật công tắc tự động ghi âm (`auto_record_switch_status` & `SmartVoiceDataManger.getAutoSmartVoiceSwitchStatus` -> `true`).
   - Mở rộng whitelist ứng dụng VoIP (`getSmartVoiceAppsAddTT` & `getSmartVoiceApps` -> bổ sung Facebook Messenger, Zalo, Telegram, WhatsApp, Viber, LINE, Google Meet, Skype, Discord...).
   - Ép trạng thái checked của ứng dụng (`SwitchApp.isChecked` -> `true`).

3. **Mở khóa thông báo "Đã lưu bản ghi âm" (Unblock Saved Recording Notifications)**:
   - Kênh cũ `start_record_channel_id` bị hệ thống khóa (`mImportance = 0`) được tự động chuyển tiếp sang kênh `call_record_channel_v2` với độ ưu tiên cao (`IMPORTANCE_HIGH`), đảm bảo hiển thị banner và thông báo lên thanh trạng thái.

4. **Tự động nhận diện người gọi và đổi tên file ghi âm (Auto-Rename)**:
   - Thu thập tên người gọi từ thông báo VoIP qua `UserNameNotificationListenerService`.
   - Đổi tên file tự động qua `GlobalSummaryInfo.getFormatSaveFileName`:
     `[AppName]_[CallerName]_[YYYY-MM-DD_HH-mm-ss].aac`
     (Ví dụ: `Messenger_Bố_2026-09-19_18-45-00.aac`).

## Cách biên dịch

Chạy lệnh:
```bash
python build.py
```
Yêu cầu môi trường có Android SDK (`aapt2`, `d8`, `javac` từ Android Studio / JDK 17+).
File APK đầu ra sẽ được tạo tại `SilentAICall.apk`.
