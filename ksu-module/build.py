import os
import zipfile

script_dir = os.path.dirname(os.path.abspath(__file__))
zip_path = os.path.join(script_dir, "silent_ai_call_ksu.zip")

files_to_include = [
    "module.prop",
    "service.sh",
    "post-fs-data.sh",
    "ColorAccessibilityAssistant_silent.apk"
]

print(f"Creating KernelSU module zip: {zip_path}")
with zipfile.ZipFile(zip_path, "w", compression=zipfile.ZIP_DEFLATED) as z:
    for fname in files_to_include:
        fpath = os.path.join(script_dir, fname)
        if os.path.exists(fpath):
            zinfo = zipfile.ZipInfo(fname)
            zinfo.compress_type = zipfile.ZIP_DEFLATED
            # 0755 for .sh, 0644 for others
            mode = 0o755 if fname.endswith(".sh") else 0o644
            zinfo.external_attr = (0o100000 | mode) << 16
            with open(fpath, "rb") as f:
                z.writestr(zinfo, f.read())
            print(f"  Added: {fname} ({os.path.getsize(fpath)} bytes, mode {oct(mode)})")
        else:
            print(f"  WARNING: {fname} not found!")

print(f"Done! Module zip created at {zip_path} ({os.path.getsize(zip_path)} bytes)")
