import os
import subprocess
import zipfile
import shutil

PROJECT_DIR = os.path.dirname(os.path.abspath(__file__))
BUILD_DIR = os.path.join(PROJECT_DIR, "build")
SRC_DIR = os.path.join(PROJECT_DIR, "src")
RES_DIR = os.path.join(PROJECT_DIR, "res")
ASSETS_DIR = os.path.join(PROJECT_DIR, "assets")

SDK_DIR = r"C:\Users\quyet\AppData\Local\Android\Sdk"
BUILD_TOOLS_DIR = os.path.join(SDK_DIR, "build-tools", "35.0.0")
AAPT2 = os.path.join(BUILD_TOOLS_DIR, "aapt2.exe")
D8 = os.path.join(BUILD_TOOLS_DIR, "d8.bat")
APKSIGNER = os.path.join(BUILD_TOOLS_DIR, "apksigner.bat")
ANDROID_JAR = os.path.join(SDK_DIR, "platforms", "android-35", "android.jar")
JAVAC = r"C:\Program Files\Android\Android Studio\jbr\bin\javac.exe"
KEYSTORE = r"C:\Users\quyet\.android\debug.keystore"

os.environ["JAVA_HOME"] = r"C:\Program Files\Android\Android Studio\jbr"
os.environ["PATH"] = r"C:\Program Files\Android\Android Studio\jbr\bin;" + os.environ.get("PATH", "")

# 1. Compile resources with aapt2
os.makedirs(BUILD_DIR, exist_ok=True)
res_zip = os.path.join(BUILD_DIR, "res.zip")
cmd1 = [AAPT2, "compile", "--dir", RES_DIR, "-o", res_zip]
print("Running aapt2 compile...")
subprocess.run(cmd1, check=True)

# 2. Link resources
base_apk = os.path.join(BUILD_DIR, "base.apk")
cmd2 = [
    AAPT2, "link",
    "-I", ANDROID_JAR,
    "--manifest", os.path.join(PROJECT_DIR, "AndroidManifest.xml"),
    res_zip,
    "-o", base_apk
]
print("Running aapt2 link...")
subprocess.run(cmd2, check=True)

# 3. Compile Java sources
classes_dir = os.path.join(BUILD_DIR, "classes")
os.makedirs(classes_dir, exist_ok=True)
java_files = []
for root, dirs, files in os.walk(SRC_DIR):
    for f in files:
        if f.endswith(".java"):
            java_files.append(os.path.join(root, f))

cmd3 = [JAVAC, "-cp", ANDROID_JAR, "-d", classes_dir] + java_files
print("Running javac...")
subprocess.run(cmd3, check=True)

# 4. Dex with D8 (only include classes in com/coloros/silentcall, exclude xposed stubs)
dex_dir = os.path.join(BUILD_DIR, "dex")
os.makedirs(dex_dir, exist_ok=True)

silentcall_classes = []
for root, dirs, files in os.walk(os.path.join(classes_dir, "com", "coloros", "silentcall")):
    for f in files:
        if f.endswith(".class"):
            silentcall_classes.append(os.path.join(root, f))

cmd4 = [D8, "--output", dex_dir] + silentcall_classes
print(f"Running d8 with {len(silentcall_classes)} classes...")
subprocess.run(cmd4, shell=True, check=True)

# 5. Add classes.dex and assets to APK
unaligned_apk = os.path.join(BUILD_DIR, "unaligned.apk")
shutil.copyfile(base_apk, unaligned_apk)

with zipfile.ZipFile(unaligned_apk, "a") as z:
    z.write(os.path.join(dex_dir, "classes.dex"), "classes.dex")
    z.write(os.path.join(ASSETS_DIR, "xposed_init"), "assets/xposed_init")

# 6. Sign APK
output_apk = os.path.join(PROJECT_DIR, "SilentAICall.apk")
cmd6 = [
    APKSIGNER, "sign",
    "--ks", KEYSTORE,
    "--ks-pass", "pass:android",
    "--ks-key-alias", "androiddebugkey",
    "--key-pass", "pass:android",
    "--out", output_apk,
    unaligned_apk
]
print("Signing APK...")
subprocess.run(cmd6, shell=True, check=True)

print("SUCCESS! Output APK:", output_apk, "size:", os.path.getsize(output_apk))
