#!/system/bin/sh
MODDIR=${0%/*}
log_file="$MODDIR/service.log"
: > "$log_file"

log() {
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*" >> "$log_file"
}

log "Waiting for boot to complete..."
until [ "$(getprop sys.boot_completed)" = 1 ]; do sleep 2; done

PATCHED_APK="$MODDIR/ColorAccessibilityAssistant_silent.apk"

if [ ! -f "$PATCHED_APK" ]; then
  log "Error: $PATCHED_APK not found!"
  exit 1
fi

chcon u:object_r:system_file:s0 "$PATCHED_APK" 2>> "$log_file"
chmod 644 "$PATCHED_APK"

# Detect real target paths of ColorAccessibilityAssistant.apk
TARGET_APKS=""

# 1. Check via package manager
PM_PATH=$(pm path com.coloros.accessibilityassistant 2>/dev/null | head -n 1 | sed 's/package://g' | tr -d '\r\n')
if [ -n "$PM_PATH" ] && [ -f "$PM_PATH" ]; then
  TARGET_APKS="$PM_PATH"
  log "Found target via pm path: $PM_PATH"
fi

# 2. Check all common system partitions
for part in /my_product /product /system /system_ext /vendor /odm; do
  if [ -d "$part" ]; then
    for f in $(find "$part" -name "ColorAccessibilityAssistant.apk" 2>/dev/null); do
      if [ -f "$f" ]; then
        case " $TARGET_APKS " in
          *" $f "*) ;; # already present
          *) TARGET_APKS="$TARGET_APKS $f"
             log "Found target in filesystem: $f" ;;
        esac
      fi
    done
  fi
done

if [ -z "$TARGET_APKS" ]; then
  TARGET_APKS="/product/app/ColorAccessibilityAssistant/ColorAccessibilityAssistant.apk"
  log "Fallback to default target: $TARGET_APKS"
fi

# Apply bind mount for each target APK
for TARGET_APK in $TARGET_APKS; do
  if [ -f "$TARGET_APK" ]; then
    # Global mount
    if mount -o bind "$PATCHED_APK" "$TARGET_APK" 2>> "$log_file"; then
      log "Successfully mounted over $TARGET_APK in global namespace"
    else
      log "Failed to mount over $TARGET_APK in global namespace"
    fi

    # Mount in all zygote namespaces
    for zpid in $(pidof zygote64) $(pidof zygote); do
      if nsenter -t "$zpid" -m -- mount -o bind "$PATCHED_APK" "$TARGET_APK" 2>> "$log_file"; then
        log "Mounted over $TARGET_APK in zygote namespace: $zpid"
      fi
    done

    # Mount in running app namespaces if already started
    for apid in $(pidof com.coloros.accessibilityassistant); do
      if nsenter -t "$apid" -m -- mount -o bind "$PATCHED_APK" "$TARGET_APK" 2>> "$log_file"; then
        log "Mounted over $TARGET_APK in app namespace: $apid"
      fi
    done
  fi
done

# Restart accessibility assistant to reload APK
pkill -f "com.coloros.accessibilityassistant"
log "Restarted com.coloros.accessibilityassistant"

# =========================================================================
# Background Watcher: Real-time Caller Tracker & Auto-Renamer
# Format: [AppName]_[CallerName]_[YYYY-MM-DD_HH-mm-ss].aac
# =========================================================================

watch_and_rename() {
  local REC_DIR="/storage/emulated/0/Music/Recordings/Call Recordings"
  log "Starting Call Recordings watcher daemon on $REC_DIR..."

  local last_caller=""
  local last_caller_app=""
  local last_call_time=0

  while true; do
    # 1. Track active VoIP call notifications
    local call_info=$(dumpsys notification --noredact 2>/dev/null | awk '
      BEGIN { in_pkg=0; in_extras=0; is_call=0; title=""; text=""; found_pkg="" }
      /NotificationRecord/ {
        if (in_pkg && is_call && title != "") {
          print found_pkg "||" title
          exit
        }
        pkg_line = $0
        in_pkg = ($0 ~ /(pkg=com\.facebook\.orca|pkg=com\.zing\.zalo|pkg=org\.telegram\.messenger|pkg=com\.whatsapp|pkg=com\.tencent\.mm|pkg=com\.instagram\.android|pkg=com\.google\.android\.apps\.tachyon|pkg=com\.skype\.raider|pkg=com\.viber\.voip)/)
        if (in_pkg) {
          match(pkg_line, /pkg=[^ ]+/)
          found_pkg = substr(pkg_line, RSTART + 4, RLENGTH - 4)
        }
        is_call = ($0 ~ /category=call/ || $0 ~ /channel=.*(call|voip)/)
        in_extras = 0
        title = ""
        text = ""
      }
      in_pkg && /extras=\{/ { in_extras = 1 }
      in_pkg && in_extras && /android\.title=String \(/ {
        t = $0
        sub(/.*android\.title=String \(/, "", t)
        sub(/\).*/, "", t)
        title = t
      }
      in_pkg && in_extras && /android\.text=String \(/ {
        tx = $0
        sub(/.*android\.text=String \(/, "", tx)
        sub(/\).*/, "", tx)
        text = tx
        if (text ~ /(call|Call|gọi|Gọi|Đang gọi|đang gọi|thoại|Thoại|video|Video)/) {
          is_call = 1
        }
      }
      END {
        if (in_pkg && is_call && title != "") {
          print found_pkg "||" title
        }
      }
    ')

    if [ -n "$call_info" ]; then
      local cur_pkg="${call_info%%||*}"
      local cur_title="${call_info#*||}"

      case "$cur_title" in
        *"Đang gọi"*|*"Cuộc gọi"*|*"Calling"*|*"Incoming"*|*"Voice call"*|*"Video call"*)
          ;;
        *)
          local mapped_app=""
          case "$cur_pkg" in
            "com.facebook.orca") mapped_app="Messenger" ;;
            "com.zing.zalo") mapped_app="Zalo" ;;
            "org.telegram.messenger"*) mapped_app="Telegram" ;;
            "com.whatsapp"*) mapped_app="WhatsApp" ;;
            "com.instagram.android") mapped_app="Instagram" ;;
            "com.tencent.mm") mapped_app="WeChat" ;;
            "com.google.android.apps.tachyon") mapped_app="GoogleMeet" ;;
            "com.skype.raider") mapped_app="Skype" ;;
            "com.viber.voip") mapped_app="Viber" ;;
            *) mapped_app="" ;;
          esac

          if [ -n "$mapped_app" ] && [ -n "$cur_title" ]; then
            cur_title=$(echo "$cur_title" | tr -d '\r\n\t' | sed -e 's/[\\/:*?"<>|]/_/g' -e 's/^[ _]*//' -e 's/[ _]*$//')
            if [ -n "$cur_title" ]; then
              last_caller="$cur_title"
              last_caller_app="$mapped_app"
              last_call_time=$(date +%s)
            fi
          fi
          ;;
      esac
    fi

    # 2. Check for completed VoIP call recordings in Call Recordings directory
    if [ -d "$REC_DIR" ]; then
      for f in "$REC_DIR"/*-*.aac; do
        [ -f "$f" ] || continue

        fname="${f##*/}"
        # Strictly match ColorOS default VoIP format: AppName-YYYYMMDDHHmmss.aac
        case "$fname" in
          *-??????????????.aac)
            app="${fname%%-*}"
            rest="${fname#*-}"
            ts="${rest%.aac}"

            # Ensure ts is exactly 14 digits
            case "$ts" in
              [0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9])
                ;;
              *)
                continue
                ;;
            esac

            # Verify file write is finished (size unchanged after 2s)
            size1=$(stat -c %s "$f" 2>/dev/null || echo 0)
            sleep 2
            size2=$(stat -c %s "$f" 2>/dev/null || echo 0)
            if [ "$size1" -ne "$size2" ] || [ "$size2" -eq 0 ]; then
              continue
            fi

            # Format date: DD.MM.YYYY_HHhMM (e.g. 20.09.2026_08h08)
            y="${ts:0:4}"
            m="${ts:4:2}"
            d="${ts:6:2}"
            H="${ts:8:2}"
            M="${ts:10:2}"
            S="${ts:12:2}"
            formatted_date="${d}.${m}.${y}_${H}h${M}"

            # Determine caller name
            caller=""
            now=$(date +%s)
            if [ "$app" = "$last_caller_app" ] && [ $((now - last_call_time)) -le 120 ]; then
              caller="$last_caller"
            fi

            if [ -n "$caller" ]; then
              new_name="${app}_${caller}_${formatted_date}.aac"
            else
              new_name="${app}_${formatted_date}.aac"
            fi

            target_file="$REC_DIR/$new_name"
            # Prevent collision if another call occurred in the same minute
            if [ -f "$target_file" ] && [ "$f" != "$target_file" ]; then
              if [ -n "$caller" ]; then
                new_name="${app}_${caller}_${formatted_date}_${S}.aac"
              else
                new_name="${app}_${formatted_date}_${S}.aac"
              fi
              target_file="$REC_DIR/$new_name"
            fi
            if [ "$f" != "$target_file" ]; then
              if mv "$f" "$target_file"; then
                log "Auto-renamed: $fname -> $new_name"
                # Update MediaStore so SoundRecorder sees it immediately
                am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "file://$target_file" >/dev/null 2>&1
                am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "file://$f" >/dev/null 2>&1
              else
                log "Failed to rename: $fname -> $new_name"
              fi
            fi
            ;;
        esac
      done
    fi

    sleep 2
  done
}

# Launch background watcher
(watch_and_rename) &
log "Watcher daemon started in background (PID: $!)"
