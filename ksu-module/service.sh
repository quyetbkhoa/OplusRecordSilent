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
  local LAST_CALLER_FILE="/data/local/tmp/last_caller.txt"
  log "Starting Call Recordings watcher daemon on $REC_DIR..."

  local last_caller=""
  local last_caller_app=""
  local last_call_time=0

  while true; do
    # 1. Track active VoIP call notifications using verified awk parser
    local notif_info=$(dumpsys notification --noredact 2>/dev/null | awk '
      BEGIN {
        in_list = 0
        in_rec = 0
        in_extras = 0
        pkg = ""
        app = ""
        is_call = 0
        title = ""
        text = ""
        convo = ""
      }
      /Notification List:/ { in_list = 1 }
      /mStatsArrays/ { in_list = 0 }
      in_list && /NotificationRecord\(/ {
        if (in_rec && is_call && pkg != "") {
          print pkg "||" app "||" title "||" text "||" convo
        }
        in_rec = 0
        in_extras = 0
        pkg = ""
        app = ""
        is_call = 0
        title = ""
        text = ""
        convo = ""
        line = $0
        if (line ~ /pkg=com\.facebook\.orca/) { pkg = "com.facebook.orca"; app = "Messenger"; in_rec = 1 }
        else if (line ~ /pkg=com\.zing\.zalo/) { pkg = "com.zing.zalo"; app = "Zalo"; in_rec = 1 }
        else if (line ~ /pkg=org\.telegram\.messenger/) { pkg = "org.telegram.messenger"; app = "Telegram"; in_rec = 1 }
        else if (line ~ /pkg=com\.whatsapp/) { pkg = "com.whatsapp"; app = "WhatsApp"; in_rec = 1 }
        else if (line ~ /pkg=com\.tencent\.mm/) { pkg = "com.tencent.mm"; app = "WeChat"; in_rec = 1 }
        else if (line ~ /pkg=com\.viber\.voip/) { pkg = "com.viber.voip"; app = "Viber"; in_rec = 1 }
        else if (line ~ /pkg=com\.skype\.raider/) { pkg = "com.skype.raider"; app = "Skype"; in_rec = 1 }
        else if (line ~ /pkg=com\.google\.android\.apps\.tachyon/) { pkg = "com.google.android.apps.tachyon"; app = "Meet"; in_rec = 1 }
        if (in_rec && (line ~ /flags=.*(ONGOING_EVENT|FOREGROUND_SERVICE)/ || line ~ /category=call/)) {
          is_call = 1
        }
      }
      in_rec && /flags=.*(ONGOING_EVENT|FOREGROUND_SERVICE)/ { is_call = 1 }
      in_rec && /category=call/ { is_call = 1 }
      in_rec && /extras=\{/ { in_extras = 1 }
      in_rec && in_extras && /android\.title=String \(/ {
        t = $0
        sub(/.*android\.title=String \(/, "", t)
        sub(/\)[ ]*$/, "", t)
        title = t
      }
      in_rec && in_extras && /android\.text=(String|SpannableString) \(/ {
        tx = $0
        sub(/.*android\.text=(String|SpannableString) \(/, "", tx)
        sub(/\)[ ]*$/, "", tx)
        text = tx
      }
      in_rec && in_extras && /android\.conversationTitle=String \(/ {
        c = $0
        sub(/.*android\.conversationTitle=String \(/, "", c)
        sub(/\)[ ]*$/, "", c)
        convo = c
      }
      END {
        if (in_rec && is_call && pkg != "") {
          print pkg "||" app "||" title "||" text "||" convo
        }
      }
    ')

    if [ -n "$notif_info" ]; then
      local n_pkg=$(echo "$notif_info" | awk -F '||' '{print $1}')
      local n_app=$(echo "$notif_info" | awk -F '||' '{print $2}')
      local n_title=$(echo "$notif_info" | awk -F '||' '{print $3}')
      local n_text=$(echo "$notif_info" | awk -F '||' '{print $4}')
      local n_convo=$(echo "$notif_info" | awk -F '||' '{print $5}')

      # Extract caller name by checking candidates in order
      local extracted=""
      for cand in "$n_title" "$n_convo" "$n_text"; do
        [ -z "$cand" ] && continue
        local c="$cand"
        # Strip common call prefixes
        case "$c" in
          [Đđ]"ang gọi cho "*) c="${c#*ang gọi cho }" ;;
          [Đđ]"ang gọi "*) c="${c#*ang gọi }" ;;
          [Cc]"uộc gọi đến từ "*) c="${c#*uộc gọi đến từ }" ;;
          [Cc]"uộc gọi đến: "*) c="${c#*uộc gọi đến: }" ;;
          [Cc]"uộc gọi đến "*) c="${c#*uộc gọi đến }" ;;
          [Cc]"uộc gọi từ "*) c="${c#*uộc gọi từ }" ;;
          [Cc]"uộc gọi với "*) c="${c#*uộc gọi với }" ;;
          [Cc]"uộc gọi "*) c="${c#*uộc gọi }" ;;
          "Calling "*) c="${c#Calling }" ;;
          "Call with "*) c="${c#Call with }" ;;
          "Incoming call from "*) c="${c#Incoming call from }" ;;
          "Incoming call: "*) c="${c#Incoming call: }" ;;
        esac

        c=$(echo "$c" | tr -d '\r\n\t' | sed -e 's/[\\/:*?"<>|]/_/g' -e 's/^[ _]*//' -e 's/[ _]*$//')
        local c_lower=$(echo "$c" | tr '[:upper:]' '[:lower:]')
        case "$c_lower" in
          ""|"$n_app"|"messenger"|"zalo"|"telegram"|"whatsapp"|"wechat"|"viber"|"skype"|"meet"|\
          "cuộc gọi"|"cuộc gọi đến"|"cuộc gọi đi"|"cuộc gọi thoại"|"cuộc gọi video"|\
          "đang gọi"|"calling"|"incoming call"|"outgoing call"|"ongoing call"|\
          "voice call"|"video call"|"tin nhắn"|"chat")
            ;;
          *)
            extracted="$c"
            break
            ;;
        esac
      done

      if [ -n "$extracted" ] && [ -n "$n_app" ]; then
        last_caller="$extracted"
        last_caller_app="$n_app"
        last_call_time=$(date +%s)
        # Write to shared tmp file for LSPosed to read immediately
        echo "${n_app}|${extracted}|$(date +%s000)" > "$LAST_CALLER_FILE"
        chmod 666 "$LAST_CALLER_FILE" 2>/dev/null
        log "Active VoIP call: app=$n_app, caller=$extracted"
      fi
    fi

    # 2. Check for completed VoIP call recordings in Call Recordings directory
    if [ -d "$REC_DIR" ]; then
      local now=$(date +%s)

      # Case A: Default ColorOS format: AppName-YYYYMMDDHHmmss.aac
      for f in "$REC_DIR"/*-??????????????.aac; do
        [ -f "$f" ] || continue
        fname="${f##*/}"
        app="${fname%%-*}"
        rest="${fname#*-}"
        ts="${rest%.aac}"

        case "$ts" in
          [0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]) ;;
          *) continue ;;
        esac

        # Verify file write is finished
        size1=$(stat -c %s "$f" 2>/dev/null || echo 0)
        sleep 2
        size2=$(stat -c %s "$f" 2>/dev/null || echo 0)
        if [ "$size1" -ne "$size2" ] || [ "$size2" -eq 0 ]; then
          continue
        fi

        y="${ts:0:4}"; m="${ts:4:2}"; d="${ts:6:2}"; H="${ts:8:2}"; M="${ts:10:2}"; S="${ts:12:2}"
        formatted_date="${d}.${m}.${y}_${H}h${M}"

        caller=""
        if [ "$app" = "$last_caller_app" ] && [ $((now - last_call_time)) -le 180 ]; then
          caller="$last_caller"
        elif [ -f "$LAST_CALLER_FILE" ]; then
          f_caller=$(cut -d'|' -f2 "$LAST_CALLER_FILE" 2>/dev/null)
          [ -n "$f_caller" ] && caller="$f_caller"
        fi

        if [ -n "$caller" ]; then
          new_name="${app}_${caller}_${formatted_date}.aac"
        else
          new_name="${app}_${formatted_date}.aac"
        fi

        target_file="$REC_DIR/$new_name"
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
            am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "file://$target_file" >/dev/null 2>&1
            am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "file://$f" >/dev/null 2>&1
          fi
        fi
      done

      # Case B: LSPosed format without caller: AppName_DD.MM.YYYY_HHhMM.aac
      for f in "$REC_DIR"/*_??.??.????_??h??.aac; do
        [ -f "$f" ] || continue
        fname="${f##*/}"
        # Check if there is only one underscore before date (i.e. no caller)
        # Format: App_DD.MM.YYYY_HHhMM.aac
        app="${fname%%_*}"
        rest="${fname#*_}"
        formatted_date="${rest%.aac}"

        caller=""
        if [ "$app" = "$last_caller_app" ] && [ $((now - last_call_time)) -le 180 ]; then
          caller="$last_caller"
        elif [ -f "$LAST_CALLER_FILE" ]; then
          f_caller=$(cut -d'|' -f2 "$LAST_CALLER_FILE" 2>/dev/null)
          [ -n "$f_caller" ] && caller="$f_caller"
        fi

        if [ -n "$caller" ]; then
          new_name="${app}_${caller}_${formatted_date}.aac"
          target_file="$REC_DIR/$new_name"
          if [ "$f" != "$target_file" ]; then
            if mv "$f" "$target_file"; then
              log "Auto-renamed (added caller): $fname -> $new_name"
              am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "file://$target_file" >/dev/null 2>&1
              am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "file://$f" >/dev/null 2>&1
            fi
          fi
        fi
      done
    fi

    sleep 1
  done
}

# Launch background watcher
(watch_and_rename) &
log "Watcher daemon started in background (PID: $!)"
