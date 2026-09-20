# Silent AI Call - KernelSU / Magisk / APatch Module (v1.0.1)

Module root (KernelSU / Magisk / APatch) dành cho ColorOS 16 (Android 16) nhằm loại bỏ hoàn toàn âm thanh thông báo cảnh báo ghi âm ("Cuộc gọi này đang được ghi âm / The session is being recorded").

## Cơ chế hoạt động

1. **Patched APK (Zeroed PCM Prompts)**:
   - Tệp `ColorAccessibilityAssistant_silent.apk` được xử lý làm rỗng (0 bytes) toàn bộ các tệp âm thanh thông báo trong `assets/mix/`:
     - `assets/mix/record_en_US.pcm`
     - `assets/mix/subtitle_en_US.pcm`
     - `assets/mix/summary_en_US.pcm`
   - Khi hệ thống thực hiện ghi âm cuộc gọi, trình phát âm thanh `AudioMixer` mở tệp PCM 0-byte và kết thúc ngay lập tức mà không phát ra bất kỳ âm thanh nào.

2. **Dual-Stage Bind Mount**:
   - **Giai đoạn 1 (`post-fs-data.sh`)**: Chạy sớm ngay khi các phân vùng `/product`, `/system` vừa mount, trước khi `zygote` khởi chạy. Thực hiện bind mount sớm để mọi tiến trình con kế thừa namespace tự động.
   - **Giai đoạn 2 (`service.sh`)**: Chạy sau khi máy hoàn tất khởi động (`sys.boot_completed=1`):
     - Tự động nhận diện đường dẫn thực tế của ứng dụng qua `pm path com.coloros.accessibilityassistant` và quét các phân vùng (`/product`, `/my_product`, `/system_ext`...).
     - Áp dụng bind mount trên global namespace.
     - Sử dụng `nsenter` để mount đè vào toàn bộ namespace của zygote (`zygote64`, `zygote`) và namespace tiến trình ứng dụng đang chạy.
     - Khởi động lại `com.coloros.accessibilityassistant` để nạp ngay APK mới.

3. **Background Watcher (Tự động đổi tên file [AppName]_[CallerName]_[DD.MM.YYYY]_[HH]h[mm].aac)**:
   - Một tiến trình daemon chạy ngầm theo dõi thông báo cuộc gọi đang diễn ra từ các ứng dụng VoIP (Messenger, Zalo, Telegram, WhatsApp...) để ghi nhận tên người gọi.
   - Khi cuộc gọi kết thúc, ColorOS lưu file `.aac` vào `/storage/emulated/0/Music/Recordings/Call Recordings/` sau ~2 giây.
   - Daemon phát hiện file mới, đợi dung lượng file ổn định và tự động đổi tên từ `[AppName]-[YYYYMMDDHHmmss].aac` sang:
     `[AppName]_[CallerName]_[DD.MM.YYYY]_[HH]h[mm].aac`
     (Ví dụ: `Messenger_Bố_20.09.2026_08h08.aac`).
   - Kích hoạt `MEDIA_SCANNER_SCAN_FILE` để bản ghi âm hiển thị tức thì trong ứng dụng Ghi âm hệ thống.

## Cài đặt

1. Tải về `silent_ai_call_ksu.zip` từ trang Releases.
2. Mở KernelSU / Magisk / APatch App -> Chọn **Modules** -> **Install from storage** -> chọn file zip.
3. Khởi động lại thiết bị.

## Đóng gói thủ công (Dành cho nhà phát triển)

```bash
python build.py
```
Tệp đầu ra: `silent_ai_call_ksu.zip`.
