import os
import subprocess
import zipfile
import shutil
import glob

PROJECT_DIR = os.path.dirname(os.path.abspath(__file__))
BUILD_DIR = os.path.join(PROJECT_DIR, "build")
SRC_DIR = os.path.join(PROJECT_DIR, "src")
RES_DIR = os.path.join(PROJECT_DIR, "res")
ASSETS_DIR = os.path.join(PROJECT_DIR, "assets")

# Detect SDK Directory
SDK_DIR = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
if not SDK_DIR or not os.path.exists(SDK_DIR):
    default_sdk = os.path.expanduser(r"~\AppData\Local\Android\Sdk")
    if os.path.exists(default_sdk):
        SDK_DIR = default_sdk
    else:
        raise RuntimeError("Android SDK not found! Please set ANDROID_HOME or ANDROID_SDK_ROOT.")

print(f"Using Android SDK: {SDK_DIR}")

# Detect build-tools
build_tools_dirs = sorted(glob.glob(os.path.join(SDK_DIR, "build-tools", "*")), reverse=True)
if not build_tools_dirs:
    raise RuntimeError("No build-tools found in Android SDK!")
BUILD_TOOLS_DIR = build_tools_dirs[0]
print(f"Using build-tools: {BUILD_TOOLS_DIR}")

AAPT2 = os.path.join(BUILD_TOOLS_DIR, "aapt2.exe") if os.name == 'nt' else os.path.join(BUILD_TOOLS_DIR, "aapt2")
D8 = os.path.join(BUILD_TOOLS_DIR, "d8.bat") if os.name == 'nt' else os.path.join(BUILD_TOOLS_DIR, "d8")
APKSIGNER = os.path.join(BUILD_TOOLS_DIR, "apksigner.bat") if os.name == 'nt' else os.path.join(BUILD_TOOLS_DIR, "apksigner")

# Detect android.jar
platform_dirs = sorted(glob.glob(os.path.join(SDK_DIR, "platforms", "android-*")), reverse=True)
if not platform_dirs:
    raise RuntimeError("No platforms found in Android SDK!")
# Prefer android-34 or 35 if available, else latest
ANDROID_JAR = None
for p in platform_dirs:
    jar = os.path.join(p, "android.jar")
    if os.path.exists(jar):
        ANDROID_JAR = jar
        break
print(f"Using android.jar: {ANDROID_JAR}")

# Detect javac
JAVAC = shutil.which("javac")
if not JAVAC:
    possible_javacs = glob.glob(r"C:\Program Files\Java\*\bin\javac.exe") + glob.glob(r"C:\Program Files\Android\Android Studio\jbr\bin\javac.exe")
    if possible_javacs:
        JAVAC = possible_javacs[-1]
    else:
        raise RuntimeError("javac not found! Please install JDK.")
print(f"Using javac: {JAVAC}")

# Keystore
KEYSTORE = os.path.expanduser(r"~\.android\debug.keystore")
if not os.path.exists(KEYSTORE):
    print("Generating debug keystore...")
    os.makedirs(os.path.dirname(KEYSTORE), exist_ok=True)
    subprocess.run([
        "keytool", "-genkey", "-v", "-keystore", KEYSTORE,
        "-storepass", "android", "-alias", "androiddebugkey",
        "-keypass", "android", "-keyalg", "RSA", "-keysize", "2048",
        "-validity", "10000", "-dname", "CN=Android Debug,O=Android,C=US"
    ], check=True)

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
