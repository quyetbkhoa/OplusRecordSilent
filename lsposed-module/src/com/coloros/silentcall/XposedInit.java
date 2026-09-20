package com.coloros.silentcall;

import android.content.res.AssetManager;
import android.media.AudioTrack;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;

public class XposedInit implements IXposedHookLoadPackage {
    private static final String TAG = "[SilentAICall] ";
    private static File sEmptyPcmFile = null;

    private static File getOrCreateEmptyPcmFile() {
        if (sEmptyPcmFile != null && sEmptyPcmFile.exists() && sEmptyPcmFile.length() == 0) {
            return sEmptyPcmFile;
        }
        try {
            File cacheDir = new File("/data/data/com.coloros.accessibilityassistant/cache");
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            File emptyFile = new File(cacheDir, "silent_prompt.pcm");
            if (!emptyFile.exists() || emptyFile.length() > 0) {
                emptyFile.delete();
                emptyFile.createNewFile();
            }
            sEmptyPcmFile = emptyFile;
            return emptyFile;
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Error creating silent_prompt.pcm: " + t.getMessage());
            return null;
        }
    }

    private static boolean isPromptPath(String path) {
        if (path == null) return false;
        String lower = path.toLowerCase();
        return lower.contains("recording-prompt") ||
               lower.contains("mix/") ||
               (lower.contains("mix") && lower.endsWith(".pcm")) ||
               lower.endsWith("record_en_us.pcm") ||
               lower.endsWith("summary_en_us.pcm") ||
               lower.endsWith("subtitle_en_us.pcm") ||
               lower.endsWith("record_zh_cn.pcm") ||
               lower.endsWith("summary_zh_cn.pcm") ||
               lower.endsWith("subtitle_zh_cn.pcm") ||
               (lower.contains("start_") && lower.endsWith(".pcm")) ||
               (lower.contains("end_") && lower.endsWith(".pcm"));
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!"com.coloros.accessibilityassistant".equals(lpparam.packageName)) {
            return;
        }

        XposedBridge.log(TAG + "=== Initializing Clean Audio-File Silencing in " + lpparam.packageName + " ===");

        // Pre-create 0-byte silent PCM file
        getOrCreateEmptyPcmFile();

        // =========================================================================
        // 1. FILE-LEVEL SILENCING: Hook AssetManager.open to return empty stream
        // =========================================================================
        try {
            XposedHelpers.findAndHookMethod(
                AssetManager.class,
                "open",
                String.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        String fileName = (String) param.args[0];
                        if (isPromptPath(fileName)) {
                            XposedBridge.log(TAG + "AssetManager.open intercepted: " + fileName + " -> returning empty stream");
                            param.setResult(new ByteArrayInputStream(new byte[0]));
                        }
                    }
                }
            );
            XposedBridge.log(TAG + "Hooked AssetManager.open for silent prompt replacement");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "AssetManager.open hook error: " + t.getMessage());
        }

        // =========================================================================
        // 2. FILE-LEVEL SILENCING: Hook FileInputStream to redirect prompt files to 0-byte file
        // =========================================================================
        try {
            XposedHelpers.findAndHookConstructor(
                FileInputStream.class,
                File.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        File file = (File) param.args[0];
                        if (file != null && isPromptPath(file.getPath())) {
                            File silent = getOrCreateEmptyPcmFile();
                            if (silent != null) {
                                XposedBridge.log(TAG + "FileInputStream(File) redirected: " + file.getPath() + " -> " + silent.getPath());
                                param.args[0] = silent;
                            }
                        }
                    }
                }
            );

            XposedHelpers.findAndHookConstructor(
                FileInputStream.class,
                String.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        String path = (String) param.args[0];
                        if (isPromptPath(path)) {
                            File silent = getOrCreateEmptyPcmFile();
                            if (silent != null) {
                                XposedBridge.log(TAG + "FileInputStream(String) redirected: " + path + " -> " + silent.getPath());
                                param.args[0] = silent.getAbsolutePath();
                            }
                        }
                    }
                }
            );
            XposedBridge.log(TAG + "Hooked FileInputStream for silent prompt redirection");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "FileInputStream hook error: " + t.getMessage());
        }

        // =========================================================================
        // 3. AUDIO-LEVEL SILENCING: Zero out AudioTrack.write buffers
        // =========================================================================
        try {
            XposedHelpers.findAndHookMethod(
                AudioTrack.class,
                "write",
                byte[].class,
                int.class,
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        byte[] data = (byte[]) param.args[0];
                        int offset = (int) param.args[1];
                        int size = (int) param.args[2];
                        if (data != null && size > 0 && offset >= 0 && offset + size <= data.length) {
                            Arrays.fill(data, offset, offset + size, (byte) 0);
                        }
                    }
                }
            );
            XposedBridge.log(TAG + "Hooked AudioTrack.write -> zeros out PCM audio buffer");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "AudioTrack.write hook error: " + t.getMessage());
        }

        // =========================================================================
        // 4. Hook AudioFileManager (b4.a) directly to return 0-byte silent file
        // =========================================================================
        try {
            Class<?> audioFileMgrClass = XposedHelpers.findClassIfExists("b4.a", lpparam.classLoader);
            if (audioFileMgrClass != null) {
                for (java.lang.reflect.Method m : audioFileMgrClass.getDeclaredMethods()) {
                    if (m.getReturnType() == File.class && m.getParameterTypes().length == 1 && m.getParameterTypes()[0] == int.class) {
                        XposedBridge.hookMethod(m, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                File silent = getOrCreateEmptyPcmFile();
                                if (silent != null) {
                                    XposedBridge.log(TAG + "b4.a." + param.method.getName() + "(int) hooked -> returning silent File");
                                    param.setResult(silent);
                                }
                            }
                        });
                        XposedBridge.log(TAG + "Hooked b4.a." + m.getName() + "(int) -> silent File");
                    } else if (m.getReturnType() == String.class && m.getParameterTypes().length == 1 && m.getParameterTypes()[0] == int.class) {
                        XposedBridge.hookMethod(m, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                File silent = getOrCreateEmptyPcmFile();
                                if (silent != null) {
                                    XposedBridge.log(TAG + "b4.a." + param.method.getName() + "(int) hooked -> returning silent String path");
                                    param.setResult(silent.getAbsolutePath());
                                }
                            }
                        });
                        XposedBridge.log(TAG + "Hooked b4.a." + m.getName() + "(int) -> silent String path");
                    }
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "AudioFileManager (b4.a) hook error: " + t.getMessage());
        }

        // =========================================================================
        // 5. Hook AudioMixer playAudioData (com.coloros.translate.engine.mixaudio.mix.a.e)
        // =========================================================================
        try {
            Class<?> mixerClass = XposedHelpers.findClassIfExists(
                "com.coloros.translate.engine.mixaudio.mix.a",
                lpparam.classLoader
            );
            if (mixerClass != null) {
                for (java.lang.reflect.Method m : mixerClass.getDeclaredMethods()) {
                    if ("e".equals(m.getName()) && m.getParameterTypes().length == 1 && m.getParameterTypes()[0] == File.class) {
                        XposedBridge.hookMethod(m, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                File silent = getOrCreateEmptyPcmFile();
                                if (silent != null) {
                                    param.args[0] = silent;
                                    XposedBridge.log(TAG + "AudioMixer.playAudioData audioFile redirected to silent file");
                                }
                            }
                        });
                        XposedBridge.log(TAG + "Hooked AudioMixer.playAudioData(File)");
                    }
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "AudioMixer hook error: " + t.getMessage());
        }

        // =========================================================================
        // 6. SAFE AUTO-RECORD: Enable all apps in support_apps JSON
        // =========================================================================
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.SharedPreferencesImpl",
                lpparam.classLoader,
                "getString",
                String.class,
                String.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        String key = (String) param.args[0];
                        if ("support_apps_auto_record".equals(key) || "support_apps_smart_voice".equals(key)) {
                            String original = (String) param.getResult();
                            if (original != null && original.contains("\"isChecked\":false")) {
                                String modified = original.replace("\"isChecked\":false", "\"isChecked\":true");
                                param.setResult(modified);
                            }
                        }
                    }
                }
            );
            XposedBridge.log(TAG + "Safe Auto-record: Enabled all apps in support_apps JSON");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Auto-record string hook error: " + t.getMessage());
        }

        // Hook SwitchApp.isChecked -> true (safe POJO model)
        try {
            Class<?> switchAppClass = XposedHelpers.findClassIfExists(
                "com.coloros.accessibilityassistant.utils.SwitchApp",
                lpparam.classLoader
            );
            if (switchAppClass != null) {
                XposedHelpers.findAndHookMethod(
                    switchAppClass,
                    "isChecked",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            param.setResult(true);
                        }
                    }
                );
                XposedBridge.log(TAG + "Hooked SwitchApp.isChecked -> true");
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "SwitchApp hook error: " + t.getMessage());
        }
    }
}
