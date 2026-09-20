#!/system/bin/sh
MODDIR=${0%/*}

PATCHED_APK="$MODDIR/ColorAccessibilityAssistant_silent.apk"
if [ ! -f "$PATCHED_APK" ]; then
  exit 0
fi

chcon u:object_r:system_file:s0 "$PATCHED_APK" 2>/dev/null
chmod 644 "$PATCHED_APK"

for target in \
  /product/app/ColorAccessibilityAssistant/ColorAccessibilityAssistant.apk \
  /my_product/app/ColorAccessibilityAssistant/ColorAccessibilityAssistant.apk \
  /system_ext/app/ColorAccessibilityAssistant/ColorAccessibilityAssistant.apk \
  /system/product/app/ColorAccessibilityAssistant/ColorAccessibilityAssistant.apk; do
  if [ -f "$target" ]; then
    mount -o bind "$PATCHED_APK" "$target" 2>/dev/null
  fi
done
