package com.coloros.silentcall;

import android.app.Notification;
import android.content.res.AssetManager;
import android.media.AudioTrack;
import android.service.notification.StatusBarNotification;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

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

        // =========================================================================
        // 7. AUTOMATIC FILE RENAMING: [AppName]_[CallerName]_[DD.MM.YYYY]_[HH]h[mm].aac
        // =========================================================================

        // A. Capture caller name from notifications when UserNameNotificationListenerService is active
        try {
            Class<?> listenerClass = XposedHelpers.findClassIfExists(
                "com.coloros.translate.UserNameNotificationListenerService",
                lpparam.classLoader
            );
            if (listenerClass != null) {
                try {
                    XposedHelpers.findAndHookMethod(
                        listenerClass,
                        "canUpdateUserName",
                        String.class,
                        StatusBarNotification.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                param.setResult(true);
                            }
                        }
                    );
                } catch (Throwable ignored) {}

                try {
                    XposedHelpers.findAndHookMethod(
                        listenerClass,
                        "updateUserName",
                        StatusBarNotification.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                StatusBarNotification sbn = (StatusBarNotification) param.args[0];
                                if (sbn == null) return;
                                String pkg = sbn.getPackageName();
                                Notification n = sbn.getNotification();
                                if (n == null || n.extras == null) return;

                                CharSequence titleCs = n.extras.getCharSequence(Notification.EXTRA_TITLE);
                                if (titleCs == null) {
                                    titleCs = n.extras.getCharSequence("android.title");
                                }
                                if (titleCs != null) {
                                    String title = titleCs.toString().trim();
                                    if (!title.isEmpty()) {
                                        if (title.contains("Đang gọi") || title.contains("Cuộc gọi") ||
                                            title.contains("Calling") || title.contains("Incoming") ||
                                            title.contains("Voice call") || title.contains("Video call")) {
                                            return;
                                        }
                                        try {
                                            Class<?> mgr = XposedHelpers.findClassIfExists("com.coloros.translate.a", lpparam.classLoader);
                                            if (mgr != null) {
                                                XposedHelpers.callStaticMethod(mgr, "e", pkg, title);
                                                XposedBridge.log(TAG + "Captured VoIP caller from notification: " + pkg + " -> " + title);
                                            }
                                        } catch (Throwable t) {
                                            XposedBridge.log(TAG + "Error storing in ThirdAppUserNameManager: " + t.getMessage());
                                        }
                                    }
                                }
                            }
                        }
                    );
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "UserNameNotificationListenerService hook error: " + t.getMessage());
        }

        // B. Hook GlobalSummaryInfo.getFormatSaveFileName to format as [AppName]_[CallerName]_[DD.MM.YYYY]_[HH]h[mm].aac
        try {
            Class<?> summaryInfoClass = XposedHelpers.findClassIfExists(
                "com.coloros.accessibilityassistant.subtitle.globalsummary.GlobalSummaryInfo",
                lpparam.classLoader
            );
            if (summaryInfoClass != null) {
                XposedHelpers.findAndHookMethod(
                    summaryInfoClass,
                    "getFormatSaveFileName",
                    String.class,
                    long.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                String original = (String) param.getResult();
                                if (original == null) return;

                                String appName = null;
                                String pkgName = null;
                                String nickName = null;
                                try {
                                    appName = (String) XposedHelpers.callMethod(param.thisObject, "getAppName");
                                } catch (Throwable ignored) {}
                                try {
                                    pkgName = (String) XposedHelpers.callMethod(param.thisObject, "getCallPackageName");
                                } catch (Throwable ignored) {}
                                try {
                                    nickName = (String) XposedHelpers.callMethod(param.thisObject, "getNickName");
                                } catch (Throwable ignored) {}

                                // Only customize VoIP calls (where appName is present)
                                if (appName == null || appName.trim().isEmpty()) {
                                    return;
                                }

                                String caller = null;
                                if (pkgName != null && !pkgName.trim().isEmpty()) {
                                    try {
                                        Class<?> mgr = XposedHelpers.findClassIfExists("com.coloros.translate.a", lpparam.classLoader);
                                        if (mgr != null) {
                                            caller = (String) XposedHelpers.callStaticMethod(mgr, "b", pkgName);
                                        }
                                    } catch (Throwable ignored) {}
                                }
                                if (caller == null || caller.trim().isEmpty()) {
                                    if (nickName != null && !nickName.trim().isEmpty()) {
                                        caller = nickName;
                                    }
                                }

                                long timestamp = (Long) param.args[1];
                                if (timestamp <= 0) timestamp = System.currentTimeMillis();

                                SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy_HH'h'mm", Locale.getDefault());
                                String dateStr = sdf.format(new Date(timestamp));
                                String ext = (param.args[0] != null && !((String) param.args[0]).isEmpty())
                                    ? (String) param.args[0] : "aac";

                                String safeApp = sanitize(appName);
                                String newName;
                                if (caller != null && !caller.trim().isEmpty()) {
                                    String safeCaller = sanitize(caller);
                                    newName = safeApp + "_" + safeCaller + "_" + dateStr + "." + ext;
                                } else {
                                    newName = safeApp + "_" + dateStr + "." + ext;
                                }
                                XposedBridge.log(TAG + "getFormatSaveFileName: [" + original + "] -> [" + newName + "]");
                                param.setResult(newName);
                            } catch (Throwable t) {
                                XposedBridge.log(TAG + "getFormatSaveFileName hook error: " + t.getMessage());
                            }
                        }
                    }
                );
                XposedBridge.log(TAG + "Hooked GlobalSummaryInfo.getFormatSaveFileName for custom naming");
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "GlobalSummaryInfo hook error: " + t.getMessage());
        }
    }

    private static String sanitize(String name) {
        if (name == null) return "";
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }
}
