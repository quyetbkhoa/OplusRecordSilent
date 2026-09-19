# Silent AI Call - KernelSU / Magisk / APatch Module

Module root (KernelSU / Magisk / APatch) dành cho ColorOS 16 nhằm tắt thông báo âm thanh ghi âm và tự động đổi tên file ghi âm cuộc gọi VoIP.

## Cơ chế hoạt động

1. **Bind Mount Silent APK**:
   - `service.sh` thực hiện mount đè tệp `ColorAccessibilityAssistant_silent.apk` (đã làm rỗng các file PCM phát âm thanh cảnh báo) lên `/product/app/ColorAccessibilityAssistant/ColorAccessibilityAssistant.apk` trên cả global namespace và toàn bộ namespace của zygote (`zygote64`, `zygote`).
2. **Kích hoạt NotificationListenerService**:
   - Cấp quyền `WRITE_SECURE_SETTINGS` và tự động kích hoạt dịch vụ lắng nghe thông báo `UserNameNotificationListenerService` của ColorOS để lấy tên người gọi từ thông báo VoIP (Messenger, Zalo, Telegram...).
3. **Background Watcher (Trình giám sát đổi tên tự động)**:
   - Một tiến trình daemon chạy ngầm theo dõi thư mục `/storage/emulated/0/Music/Recordings/Call Recordings/`.
   - Khi phát hiện tệp ghi âm mới hoàn thành, daemon tự động truy vấn tên người gọi đang đàm thoại từ `dumpsys notification` và đổi tên tệp theo định dạng:
     `[AppName]_[CallerName]_[YYYY-MM-DD_HH-mm-ss].aac`
   - Kích hoạt `MEDIA_SCANNER_SCAN_FILE` để bản ghi âm hiển thị tức thì trong ứng dụng Ghi âm hệ thống.

## Đóng gói module

Để đóng gói thành file zip cài đặt trong KernelSU / Magisk Manager:
```bash
python build.py
```
File đầu ra: `silent_ai_call_ksu.zip`.
