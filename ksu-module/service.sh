#!/system/bin/sh
MODDIR=${0%/*}
log_file="$MODDIR/service.log"
: > "$log_file"

log() {
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*" >> "$log_file"
}

log "Waiting for boot to complete..."
until [ "$(getprop sys.boot_completed)" = 1 ]; do sleep 2; done

TARGET_APK="/product/app/ColorAccessibilityAssistant/ColorAccessibilityAssistant.apk"
PATCHED_APK="$MODDIR/ColorAccessibilityAssistant_silent.apk"

# 1. Mount silent APK
if [ -f "$PATCHED_APK" ] && [ -f "$TARGET_APK" ]; then
  chcon u:object_r:system_file:s0 "$PATCHED_APK" 2>> "$log_file"
  chmod 644 "$PATCHED_APK"
  
  # Global mount
  if mount -o bind "$PATCHED_APK" "$TARGET_APK" 2>> "$log_file"; then
    log "Successfully mounted over $TARGET_APK in global namespace"
  else
    log "Failed to mount in global namespace"
  fi

  # Mount in all zygote namespaces
  for zpid in $(pidof zygote64) $(pidof zygote); do
    if nsenter -t "$zpid" -m mount -o bind "$PATCHED_APK" "$TARGET_APK" 2>/dev/null; then
      log "Mounted in zygote namespace: $zpid"
    fi
  done

  # Restart accessibility assistant
  pkill -f "com.coloros.accessibilityassistant"
  log "Restarted com.coloros.accessibilityassistant"
fi

# 2. Grant permissions and enable NotificationListener for VoIP caller capture
pm grant com.coloros.accessibilityassistant android.permission.WRITE_SECURE_SETTINGS 2>/dev/null
target_listener="com.coloros.accessibilityassistant/com.coloros.translate.UserNameNotificationListenerService"
current_listeners=$(settings get secure enabled_notification_listeners 2>/dev/null)
case "$current_listeners" in
  *"$target_listener"*)
    log "Notification listener already enabled"
    ;;
  *)
    if [ -z "$current_listeners" ] || [ "$current_listeners" = "null" ]; then
      settings put secure enabled_notification_listeners "$target_listener"
    else
      settings put secure enabled_notification_listeners "$current_listeners:$target_listener"
    fi
    log "Added $target_listener to enabled_notification_listeners"
    ;;
esac

# 3. Helper: Extract caller name from active VoIP notifications
get_caller_name() {
  local target_pkg="$1"
  local dumpsys_out=$(dumpsys notification --noredact 2>/dev/null)
  local title=""

  if [ -n "$target_pkg" ]; then
    title=$(echo "$dumpsys_out" | awk -v pkg="$target_pkg" '
      $0 ~ "pkg=" pkg { in_pkg=1; next }
      in_pkg && $0 ~ "StatusBarNotification" { in_pkg=0 }
      in_pkg && $0 ~ "android.title=String \\(" {
        sub(/.*android\.title=String \(/, "");
        sub(/\).*/, "");
        print;
        exit;
      }
    ')
  fi

  if [ -z "$title" ]; then
    title=$(echo "$dumpsys_out" | awk '
      $0 ~ "category=call" || $0 ~ "channel=.*(voip|call)" { in_call=1; next }
      in_call && $0 ~ "StatusBarNotification" { in_call=0 }
      in_call && $0 ~ "android.title=String \\(" {
        sub(/.*android\.title=String \(/, "");
        sub(/\).*/, "");
        print;
        exit;
      }
    ')
  fi

  # Filter out generic titles
  case "$title" in
    *"Đang gọi"*|*"Cuộc gọi"*|*"Calling"*|*"Incoming"*|*"Voice call"*|*"Video call"*)
      title=""
      ;;
  esac

  # Sanitize illegal filename characters
  title=$(echo "$title" | tr -d '\r\n\t' | sed -e 's/[\\/:*?"<>|]/_/g' -e 's/^[ _]*//' -e 's/[ _]*$//')
  echo "$title"
}

# 4. Background Watcher: Automatically rename completed call recordings
watch_recordings() {
  local REC_DIR="/storage/emulated/0/Music/Recordings/Call Recordings"
  log "Starting Call Recordings watcher on $REC_DIR"

  while true; do
    if [ -d "$REC_DIR" ]; then
      for f in "$REC_DIR"/*-*.aac; do
        [ -f "$f" ] || continue
        
        fname="${f##*/}"
        # Match pattern: AppName-YYYYMMDDHHmmss.aac
        case "$fname" in
          *-??????????????.aac)
            app="${fname%%-*}"
            rest="${fname#*-}"
            ts="${rest%.aac}"

            # Check if file is still being written (wait for size to stabilize)
            size1=$(stat -c %s "$f" 2>/dev/null || echo 0)
            sleep 2
            size2=$(stat -c %s "$f" 2>/dev/null || echo 0)
            if [ "$size1" -ne "$size2" ] || [ "$size2" -eq 0 ]; then
              # Still recording or empty, check on next iteration
              continue
            fi

            # Format date: YYYY-MM-DD_HH-mm-ss
            y="${ts:0:4}"
            m="${ts:4:2}"
            d="${ts:6:2}"
            H="${ts:8:2}"
            M="${ts:10:2}"
            S="${ts:12:2}"
            formatted_date="${y}-${m}-${d}_${H}-${M}-${S}"

            # Map app to package for targeted notification lookup
            case "$app" in
              "Messenger"|"Facebook"|"FB"|"fb")
                target_pkg="com.facebook.orca"
                ;;
              "Zalo"|"zalo")
                target_pkg="com.zing.zalo"
                ;;
              "Telegram"|"telegram")
                target_pkg="org.telegram.messenger"
                ;;
              "WhatsApp"|"whatsapp")
                target_pkg="com.whatsapp"
                ;;
              "Instagram"|"instagram")
                target_pkg="com.instagram.android"
                ;;
              "WeChat"|"wechat")
                target_pkg="com.tencent.mm"
                ;;
              *)
                target_pkg=""
                ;;
            esac

            caller=$(get_caller_name "$target_pkg")

            if [ -n "$caller" ]; then
              new_name="${app}_${caller}_${formatted_date}.aac"
            else
              new_name="${app}_${formatted_date}.aac"
            fi

            target_file="$REC_DIR/$new_name"
            if [ "$f" != "$target_file" ]; then
              if mv "$f" "$target_file"; then
                log "Renamed: $fname -> $new_name"
                # Update MediaScanner so it immediately appears in system apps
                am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "file://$target_file" >/dev/null 2>&1
              else
                log "Failed to rename $fname -> $new_name"
              fi
            fi
            ;;
        esac
      done
    fi
    sleep 3
  done
}

# Start watcher daemon in background
(watch_recordings) &
log "Watcher daemon started in background (PID: $!)"
