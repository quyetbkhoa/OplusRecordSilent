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
