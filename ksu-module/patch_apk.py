"""
Script to create ColorAccessibilityAssistant_silent.apk from stock ColorAccessibilityAssistant.apk
by zeroing out the PCM announcement audio files in assets/mix/.
"""
import sys
import os
import zipfile

def patch_apk(input_apk, output_apk):
    if not os.path.exists(input_apk):
        print(f"Error: Input APK not found: {input_apk}")
        sys.exit(1)

    print(f"Patching {input_apk} -> {output_apk}...")
    pcm_targets = {
        "assets/mix/summary_en_US.pcm",
        "assets/mix/record_en_US.pcm",
        "assets/mix/subtitle_en_US.pcm",
        "assets/mix/summary_zh_CN.pcm",
        "assets/mix/record_zh_CN.pcm",
        "assets/mix/subtitle_zh_CN.pcm"
    }

    with zipfile.ZipFile(input_apk, "r") as zin, zipfile.ZipFile(output_apk, "w") as zout:
        for item in zin.infolist():
            if item.filename in pcm_targets:
                print(f"  Zeroing out audio: {item.filename} (was {item.file_size} bytes)")
                zout.writestr(item.filename, b"")
            else:
                zout.writestr(item, zin.read(item.filename))

    print(f"Done! Patched APK created at {output_apk} ({os.path.getsize(output_apk)} bytes)")

if __name__ == "__main__":
    if len(sys.argv) >= 3:
        patch_apk(sys.argv[1], sys.argv[2])
    else:
        default_in = "ColorAccessibilityAssistant.apk"
        default_out = "ColorAccessibilityAssistant_silent.apk"
        patch_apk(default_in, default_out)
