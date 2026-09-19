package com.coloros.silentcall;

import android.app.Notification;
import android.content.Context;
import android.media.AudioTrack;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.io.File;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class XposedInit implements IXposedHookLoadPackage {
    private static final String TAG = "[SilentAICall] ";

    private static String sanitizeFilename(String input) {
        if (input == null) return "";
        String sanitized = input.replaceAll("[\\\\/:*?\"<>|\\r\\n\\t]", "_");
        sanitized = sanitized.replaceAll("_+", "_");
        sanitized = sanitized.replaceAll("^_+|_+$", "").trim();
        return sanitized;
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!"com.coloros.accessibilityassistant".equals(lpparam.packageName)) {
            return;
        }

        XposedBridge.log(TAG + "=== Initializing SilentAICall in com.coloros.accessibilityassistant ===");

        // =========================================================================
        // 1. SILENCE ANNOUNCEMENTS (4 Layers)
        // =========================================================================

        // --- LAYER 1: Hook Manager Entry Point: com.coloros.translate.engine.mixaudio.a.e ---
        try {
            Class<?> managerClass = XposedHelpers.findClassIfExists(
                "com.coloros.translate.engine.mixaudio.a",
                lpparam.classLoader
            );
            if (managerClass != null) {
                XposedHelpers.findAndHookMethod(
                    managerClass,
                    "e",
                    Context.class,
                    boolean.class,
                    String.class,
                    int.class,
                    "com.coloros.translate.engine.mixaudio.engine.e",
                    File.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            File f = (File) param.args[5];
                            String path = f != null ? f.getAbsolutePath() : "";
                            XposedBridge.log(TAG + "Layer 1: Blocked mixaudio.a.e for: " + path);
                            Object listener = param.args[4];
                            if (listener != null) {
                                try {
                                    XposedHelpers.callMethod(listener, "onPlayComplete", path, 0);
                                    XposedBridge.log(TAG + "Layer 1: Called listener.onPlayComplete");
                                } catch (Throwable t) {
                                    XposedBridge.log(TAG + "Layer 1 listener error: " + t.getMessage());
                                }
                            }
                            param.setResult(null); // Stop original execution!
                        }
                    }
                );
                XposedBridge.log(TAG + "Layer 1: Successfully hooked mixaudio.a.e");
            } else {
                XposedBridge.log(TAG + "Layer 1: Class mixaudio.a not found in classLoader");
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Layer 1 error: " + t.getMessage());
        }

        // --- LAYER 2: Hook Worker Player: com.coloros.translate.engine.mixaudio.mix.a.e ---
        try {
            Class<?> workerClass = XposedHelpers.findClassIfExists(
                "com.coloros.translate.engine.mixaudio.mix.a",
                lpparam.classLoader
            );
            if (workerClass != null) {
                XposedHelpers.findAndHookMethod(
                    workerClass,
                    "e",
                    File.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            File f = (File) param.args[0];
                            String path = f != null ? f.getAbsolutePath() : "";
                            XposedBridge.log(TAG + "Layer 2: Blocked mix.a.e for: " + path);
                            try {
                                Object listener = XposedHelpers.getObjectField(param.thisObject, "f");
                                if (listener != null) {
                                    XposedHelpers.callMethod(listener, "onPlayComplete", path, 0);
                                    XposedBridge.log(TAG + "Layer 2: Called listener.onPlayComplete");
                                }
                            } catch (Throwable t) {
                                XposedBridge.log(TAG + "Layer 2 listener error: " + t.getMessage());
                            }
                            param.setResult(null); // Stop original execution!
                        }
                    }
                );
                XposedBridge.log(TAG + "Layer 2: Successfully hooked mix.a.e");
            } else {
                XposedBridge.log(TAG + "Layer 2: Class mix.a not found in classLoader");
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Layer 2 error: " + t.getMessage());
        }

        // --- LAYER 3: Hook Downlink PCM Player: com.coloros.translate.engine.mixaudio.mix.c.d ---
        try {
            Class<?> downClass = XposedHelpers.findClassIfExists(
                "com.coloros.translate.engine.mixaudio.mix.c",
                lpparam.classLoader
            );
            if (downClass != null) {
                XposedHelpers.findAndHookMethod(
                    downClass,
                    "d",
                    InputStream.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            XposedBridge.log(TAG + "Layer 3: Blocked mix.c.d(InputStream)");
                            param.setResult(null); // Stop original execution!
                        }
                    }
                );
                XposedBridge.log(TAG + "Layer 3: Successfully hooked mix.c.d");
            } else {
                XposedBridge.log(TAG + "Layer 3: Class mix.c not found in classLoader");
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Layer 3 error: " + t.getMessage());
        }

        // --- LAYER 4: Safety Net: AudioTrack.play() ---
        try {
            XposedHelpers.findAndHookMethod(
                AudioTrack.class,
                "play",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
                        for (StackTraceElement el : stack) {
                            String cls = el.getClassName();
                            if (cls.contains("mixaudio") || cls.contains("translate.engine")) {
                                XposedBridge.log(TAG + "Layer 4: Suppressed AudioTrack.play from: " + cls);
                                param.setResult(null); // Don't play!
                                return;
                            }
                        }
                    }
                }
            );
            XposedBridge.log(TAG + "Layer 4: Successfully hooked AudioTrack.play");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Layer 4 error: " + t.getMessage());
        }

        // =========================================================================
        // 2. AUTO RECORD HOOKS & VOIP CHECKS BYPASS
        // =========================================================================
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.SharedPreferencesImpl",
                lpparam.classLoader,
                "getBoolean",
                String.class,
                boolean.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        String key = (String) param.args[0];
                        if ("auto_record_switch_status".equals(key)) {
                            param.setResult(true);
                        }
                    }
                }
            );
            XposedBridge.log(TAG + "Auto-record: Forced auto_record_switch_status to true");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Auto-record boolean hook error: " + t.getMessage());
        }

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
                        if ("support_apps_auto_record".equals(key)) {
                            String original = (String) param.getResult();
                            if (original != null && original.contains("\"isChecked\":false")) {
                                String modified = original.replace("\"isChecked\":false", "\"isChecked\":true");
                                param.setResult(modified);
                            }
                        }
                    }
                }
            );
            XposedBridge.log(TAG + "Auto-record: Enabled all apps in support_apps_auto_record");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Auto-record string hook error: " + t.getMessage());
        }

        // --- Bypass Region, Switch Status, and Enable Status in SmartVoiceDataManger ---
        try {
            Class<?> dataMgrClass = XposedHelpers.findClassIfExists(
                "com.coloros.accessibilityassistant.cloud.SmartVoiceDataManger",
                lpparam.classLoader
            );
            if (dataMgrClass != null) {
                // 1. Force isRegionSupportSmartVoice to true
                try {
                    XposedHelpers.findAndHookMethod(
                        dataMgrClass,
                        "isRegionSupportSmartVoice",
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                param.setResult(true);
                            }
                        }
                    );
                    XposedBridge.log(TAG + "Hooked SmartVoiceDataManger.isRegionSupportSmartVoice -> true");
                } catch (Throwable t) {
                    XposedBridge.log(TAG + "Error hooking isRegionSupportSmartVoice: " + t.getMessage());
                }

                // 2. Force getAutoSmartVoiceSwitchStatus to true
                try {
                    XposedHelpers.findAndHookMethod(
                        dataMgrClass,
                        "getAutoSmartVoiceSwitchStatus",
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                param.setResult(true);
                            }
                        }
                    );
                    XposedBridge.log(TAG + "Hooked SmartVoiceDataManger.getAutoSmartVoiceSwitchStatus -> true");
                } catch (Throwable t) {
                    XposedBridge.log(TAG + "Error hooking getAutoSmartVoiceSwitchStatus: " + t.getMessage());
                }

                // 3. Force getAutoSmartVoiceEnableStatus to true
                try {
                    XposedHelpers.findAndHookMethod(
                        dataMgrClass,
                        "getAutoSmartVoiceEnableStatus",
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                param.setResult(true);
                            }
                        }
                    );
                    XposedBridge.log(TAG + "Hooked SmartVoiceDataManger.getAutoSmartVoiceEnableStatus -> true");
                } catch (Throwable t) {
                    XposedBridge.log(TAG + "Error hooking getAutoSmartVoiceEnableStatus: " + t.getMessage());
                }

                // 4. Ensure all common VoIP apps are in getSmartVoiceAppsAddTT whitelist
                try {
                    XC_MethodHook appsHook = new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            Object result = param.getResult();
                            if (result instanceof java.util.List) {
                                @SuppressWarnings("unchecked")
                                java.util.List<Object> list = (java.util.List<Object>) result;
                                String[] commonVoipPkgs = new String[] {
                                    "com.facebook.orca", "com.zing.zalo", "org.telegram.messenger",
                                    "com.whatsapp", "com.viber.voip", "jp.naver.line.android",
                                    "com.google.android.talk", "com.skype.raider", "com.discord"
                                };
                                Class<?> switchAppClass = XposedHelpers.findClassIfExists(
                                    "com.coloros.accessibilityassistant.utils.SwitchApp",
                                    lpparam.classLoader
                                );
                                if (switchAppClass != null) {
                                    for (String pkg : commonVoipPkgs) {
                                        boolean exists = false;
                                        for (Object item : list) {
                                            String p = (String) XposedHelpers.callMethod(item, "getPkgName");
                                            if (pkg.equals(p)) {
                                                exists = true;
                                                try {
                                                    XposedHelpers.callMethod(item, "setChecked", true);
                                                } catch (Throwable ignored) {}
                                                break;
                                            }
                                        }
                                        if (!exists) {
                                            try {
                                                Object newApp = XposedHelpers.newInstance(switchAppClass, pkg);
                                                XposedHelpers.callMethod(newApp, "setChecked", true);
                                                XposedHelpers.callMethod(newApp, "setInstall", true);
                                                list.add(newApp);
                                            } catch (Throwable t) {
                                                XposedBridge.log(TAG + "Error adding " + pkg + " to whitelist: " + t.getMessage());
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    };

                    XposedHelpers.findAndHookMethod(
                        dataMgrClass,
                        "getSmartVoiceAppsAddTT",
                        boolean.class,
                        appsHook
                    );
                    XposedBridge.log(TAG + "Hooked SmartVoiceDataManger.getSmartVoiceAppsAddTT(boolean) to include VoIP apps");

                    try {
                        XposedHelpers.findAndHookMethod(
                            dataMgrClass,
                            "getSmartVoiceApps",
                            boolean.class,
                            boolean.class,
                            appsHook
                        );
                        XposedBridge.log(TAG + "Hooked SmartVoiceDataManger.getSmartVoiceApps(boolean, boolean)");
                    } catch (Throwable ignored) {}
                } catch (Throwable t) {
                    XposedBridge.log(TAG + "Error hooking getSmartVoiceAppsAddTT: " + t.getMessage());
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "SmartVoiceDataManger hooks error: " + t.getMessage());
        }

        // --- Bypass Statement Agreement in SubtitlePrefDb.F ---
        try {
            Class<?> prefDbClass = XposedHelpers.findClassIfExists(
                "com.coloros.accessibilityassistant.repository.SubtitlePrefDb",
                lpparam.classLoader
            );
            if (prefDbClass != null) {
                XposedHelpers.findAndHookMethod(
                    prefDbClass,
                    "F",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            param.setResult(true);
                        }
                    }
                );
                XposedBridge.log(TAG + "Hooked SubtitlePrefDb.F (statement agreement) -> true");
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "SubtitlePrefDb.F hook error: " + t.getMessage());
        }

        // --- Force SwitchApp.isChecked to true ---
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
            XposedBridge.log(TAG + "SwitchApp.isChecked hook error: " + t.getMessage());
        }

        // --- Log VoipCallRecordReceiver.onReceive for diagnosis ---
        try {
            Class<?> receiverClass = XposedHelpers.findClassIfExists(
                "com.coloros.accessibilityassistant.subtitle.callsummary.fluid.VoipCallRecordReceiver",
                lpparam.classLoader
            );
            if (receiverClass != null) {
                XposedHelpers.findAndHookMethod(
                    receiverClass,
                    "onReceive",
                    Context.class,
                    android.content.Intent.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            android.content.Intent intent = (android.content.Intent) param.args[1];
                            if (intent != null) {
                                boolean state = intent.getBooleanExtra("VoiceCallState", false);
                                String pkg = intent.getStringExtra("VoiceCallPackage");
                                XposedBridge.log(TAG + "VoipCallRecordReceiver.onReceive: action=" + intent.getAction() + ", state=" + state + ", pkg=" + pkg);
                            }
                        }
                    }
                );
                XposedBridge.log(TAG + "Hooked VoipCallRecordReceiver.onReceive for diagnostics");
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "VoipCallRecordReceiver hook error: " + t.getMessage());
        }

        // --- Unblock Notification Channel for Saved Call Recordings ---
        try {
            Class<?> nmClass = android.app.NotificationManager.class;
            XC_MethodHook notifyHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Notification n = null;
                    for (Object arg : param.args) {
                        if (arg instanceof Notification) {
                            n = (Notification) arg;
                            break;
                        }
                    }
                    if (n != null) {
                        String chId = n.getChannelId();
                        if ("start_record_channel_id".equals(chId) || chId == null || chId.isEmpty()) {
                            try {
                                android.app.NotificationManager nm = (android.app.NotificationManager) param.thisObject;
                                android.app.NotificationChannel v2 = nm.getNotificationChannel("call_record_channel_v2");
                                if (v2 == null) {
                                    v2 = new android.app.NotificationChannel(
                                        "call_record_channel_v2",
                                        "Ghi âm cuộc gọi",
                                        android.app.NotificationManager.IMPORTANCE_HIGH
                                    );
                                    v2.setDescription("Thông báo bản ghi âm cuộc gọi đã lưu");
                                    v2.enableLights(true);
                                    v2.enableVibration(true);
                                    nm.createNotificationChannel(v2);
                                    XposedBridge.log(TAG + "Created new high-importance channel: call_record_channel_v2");
                                }
                                XposedHelpers.setObjectField(n, "mChannelId", "call_record_channel_v2");
                                XposedBridge.log(TAG + "Re-routed notification from [" + chId + "] to [call_record_channel_v2]");
                            } catch (Throwable t) {
                                XposedBridge.log(TAG + "Error re-routing notification channel: " + t.getMessage());
                            }
                        }
                    }
                }
            };

            XposedHelpers.findAndHookMethod(nmClass, "notify", int.class, Notification.class, notifyHook);
            XposedHelpers.findAndHookMethod(nmClass, "notify", String.class, int.class, Notification.class, notifyHook);
            XposedBridge.log(TAG + "Hooked NotificationManager.notify to unblock saved recording notifications");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "NotificationManager hook error: " + t.getMessage());
        }

        // =========================================================================
        // 3. CALLER NAME CAPTURE FOR ALL VOIP APPS
        // =========================================================================

        try {
            Class<?> listenerClass = XposedHelpers.findClassIfExists(
                "com.coloros.translate.UserNameNotificationListenerService",
                lpparam.classLoader
            );
            if (listenerClass != null) {
                // canUpdateUserName takes (String packageName, StatusBarNotification sbn)
                XposedHelpers.findAndHookMethod(
                    listenerClass,
                    "canUpdateUserName",
                    String.class,
                    StatusBarNotification.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            String pkg = (String) param.args[0];
                            if (pkg != null && (
                                pkg.contains("orca") || pkg.contains("facebook") ||
                                pkg.contains("messenger") || pkg.contains("telegram") ||
                                pkg.contains("whatsapp") || pkg.contains("zalo") ||
                                pkg.contains("instagram") || pkg.contains("line") ||
                                pkg.contains("viber") || pkg.contains("tencent.mm")
                            )) {
                                param.setResult(true);
                                XposedBridge.log(TAG + "canUpdateUserName: Accepted VoIP app: " + pkg);
                            }
                        }
                    }
                );
                XposedBridge.log(TAG + "Hooked canUpdateUserName to support all VoIP apps");

                // Also hook updateUserName directly to guarantee caller name is captured
                XposedHelpers.findAndHookMethod(
                    listenerClass,
                    "updateUserName",
                    StatusBarNotification.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            StatusBarNotification sbn = (StatusBarNotification) param.args[0];
                            if (sbn != null) {
                                String pkg = sbn.getPackageName();
                                Notification n = sbn.getNotification();
                                if (n != null && n.extras != null) {
                                    CharSequence titleCs = n.extras.getCharSequence(Notification.EXTRA_TITLE);
                                    if (titleCs == null) {
                                        titleCs = n.extras.getCharSequence("android.title");
                                    }
                                    if (titleCs != null) {
                                        String title = titleCs.toString().trim();
                                        if (!title.isEmpty()) {
                                            XposedBridge.log(TAG + "Captured VoIP call title: " + title + " for " + pkg);
                                            try {
                                                Class<?> mgr = XposedHelpers.findClassIfExists("com.coloros.translate.a", lpparam.classLoader);
                                                if (mgr != null) {
                                                    XposedHelpers.callStaticMethod(mgr, "e", pkg, title);
                                                    XposedBridge.log(TAG + "Stored in ThirdAppUserNameManager: " + pkg + " -> " + title);
                                                }
                                            } catch (Throwable t) {
                                                XposedBridge.log(TAG + "Error storing in ThirdAppUserNameManager: " + t.getMessage());
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                );
                XposedBridge.log(TAG + "Hooked updateUserName to actively capture notification titles");
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "UserNameNotificationListenerService hook error: " + t.getMessage());
        }

        // =========================================================================
        // 4. AUTOMATIC FILE RENAMING: [AppName]_[CallerName]_[Timestamp].aac
        // =========================================================================

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

                                // ONLY modify for VoIP call recordings (where appName is present)
                                // Do NOT touch standard voice recordings ("Chép lời nói AI")
                                if (appName == null || appName.trim().isEmpty()) {
                                    return;
                                }

                                String caller = null;
                                if (pkgName != null) {
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

                                // If no caller name is found yet, keep original to avoid breaking ColorOS
                                if (caller == null || caller.trim().isEmpty()) {
                                    return;
                                }

                                String postfix = (String) param.args[0];
                                long timestamp = (Long) param.args[1];
                                if (timestamp <= 0) timestamp = System.currentTimeMillis();

                                String safeApp = sanitizeFilename(appName);
                                String safeCaller = sanitizeFilename(caller);
                                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault());
                                String dateStr = sdf.format(new Date(timestamp));
                                String ext = (postfix != null && !postfix.isEmpty()) ? postfix : "aac";

                                String newName = safeApp + "_" + safeCaller + "_" + dateStr + "." + ext;
                                XposedBridge.log(TAG + "Renamed file from [" + original + "] to [" + newName + "]");
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
}
