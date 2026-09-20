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
