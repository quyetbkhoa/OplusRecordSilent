package com.coloros.silentcall;

import android.app.Notification;
import android.content.Context;
import android.media.AudioTrack;
import android.os.Bundle;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.io.File;
import java.io.InputStream;

public class XposedInit implements IXposedHookLoadPackage {
    private static final String TAG = "[SilentAICall] ";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!"com.coloros.accessibilityassistant".equals(lpparam.packageName)) {
            return;
        }

        XposedBridge.log(TAG + "=== Initializing SilentAICall in com.coloros.accessibilityassistant ===");

        // =========================================================================
        // 1. SILENCE ANNOUNCEMENTS (Multi-Layer Architecture)
        // =========================================================================

        // --- LAYER 0: Source Silencing in AccessibilityAssistant MixPromptAudioManager ---
        // Class: com.coloros.accessibilityassistant.mixaudio.a
        try {
            Class<?> promptMgrClass = XposedHelpers.findClassIfExists(
                "com.coloros.accessibilityassistant.mixaudio.a",
                lpparam.classLoader
            );
            if (promptMgrClass != null) {
                // Hook f(boolean, Boolean) / isNeedMixAudio -> always false
                try {
                    XposedHelpers.findAndHookMethod(
                        promptMgrClass,
                        "f",
                        boolean.class,
                        Boolean.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                XposedBridge.log(TAG + "Layer 0: Blocked isNeedMixAudio (f) -> false");
                                param.setResult(false);
                            }
                        }
                    );
                    XposedBridge.log(TAG + "Layer 0: Successfully hooked mixaudio.a.f -> false");
                } catch (Throwable t) {
                    XposedBridge.log(TAG + "Layer 0 error hooking f: " + t.getMessage());
                }

                // Hook g(a, boolean, Boolean, int, Object) -> always false
                try {
                    XposedHelpers.findAndHookMethod(
                        promptMgrClass,
                        "g",
                        promptMgrClass,
                        boolean.class,
                        Boolean.class,
                        int.class,
                        Object.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                XposedBridge.log(TAG + "Layer 0: Blocked mixaudio.a.g -> false");
                                param.setResult(false);
                            }
                        }
                    );
                    XposedBridge.log(TAG + "Layer 0: Successfully hooked mixaudio.a.g -> false");
                } catch (Throwable t) {
                    XposedBridge.log(TAG + "Layer 0 error hooking g: " + t.getMessage());
                }

                // Hook d() and e() -> always false
                try {
                    XposedHelpers.findAndHookMethod(
                        promptMgrClass,
                        "d",
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                param.setResult(false);
                            }
                        }
                    );
                    XposedHelpers.findAndHookMethod(
                        promptMgrClass,
                        "e",
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                param.setResult(false);
                            }
                        }
                    );
                    XposedBridge.log(TAG + "Layer 0: Successfully hooked mixaudio.a.d and e -> false");
                } catch (Throwable t) {
                    XposedBridge.log(TAG + "Layer 0 error hooking d/e: " + t.getMessage());
                }

                // Hook n(Integer, String, a$a, String) / mixVoipStartPromptAudio -> bypass and call callback directly
                try {
                    Class<?> callbackClass = XposedHelpers.findClassIfExists(
                        "com.coloros.accessibilityassistant.mixaudio.a$a",
                        lpparam.classLoader
                    );
                    XC_MethodHook nHook = new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            XposedBridge.log(TAG + "Layer 0: Blocked mixVoipStartPromptAudio (n)");
                            Object callback = param.args[2];
                            if (callback != null) {
                                try {
                                    XposedHelpers.callMethod(callback, "a");
                                    XposedBridge.log(TAG + "Layer 0: Invoked callback.a() directly");
                                } catch (Throwable t) {
                                    XposedBridge.log(TAG + "Layer 0 callback error: " + t.getMessage());
                                }
                            }
                            param.setResult(null);
                        }
                    };

                    if (callbackClass != null) {
                        XposedHelpers.findAndHookMethod(
                            promptMgrClass,
                            "n",
                            Integer.class,
                            String.class,
                            callbackClass,
                            String.class,
                            nHook
                        );
                    } else {
                        // Fallback hook by method name if inner class not resolved directly
                        for (java.lang.reflect.Method m : promptMgrClass.getDeclaredMethods()) {
                            if ("n".equals(m.getName()) && m.getParameterTypes().length == 4) {
                                XposedBridge.hookMethod(m, nHook);
                                break;
                            }
                        }
                    }
                    XposedBridge.log(TAG + "Layer 0: Successfully hooked mixaudio.a.n");
                } catch (Throwable t) {
                    XposedBridge.log(TAG + "Layer 0 error hooking n: " + t.getMessage());
                }

                // Hook h and k (mixCallAudio) -> bypass and call callback directly
                try {
                    for (java.lang.reflect.Method m : promptMgrClass.getDeclaredMethods()) {
                        String mName = m.getName();
                        if ("h".equals(mName) || "k".equals(mName)) {
                            XposedBridge.hookMethod(m, new XC_MethodHook() {
                                @Override
                                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                    XposedBridge.log(TAG + "Layer 0: Blocked mixCallAudio (" + param.method.getName() + ")");
                                    for (Object arg : param.args) {
                                        if (arg != null) {
                                            try {
                                                XposedHelpers.callMethod(arg, "a");
                                                XposedBridge.log(TAG + "Layer 0: Invoked listener.a()");
                                                break;
                                            } catch (Throwable ignored) {}
                                        }
                                    }
                                    param.setResult(null);
                                }
                            });
                        }
                    }
                    XposedBridge.log(TAG + "Layer 0: Successfully hooked mixaudio.a.h and k");
                } catch (Throwable t) {
                    XposedBridge.log(TAG + "Layer 0 error hooking h/k: " + t.getMessage());
                }

                // Hook l (mixVoipAudio) and p (stopMixVoipAudio) -> block
                try {
                    for (java.lang.reflect.Method m : promptMgrClass.getDeclaredMethods()) {
                        String mName = m.getName();
                        if ("l".equals(mName) || "p".equals(mName) || "o".equals(mName)) {
                            XposedBridge.hookMethod(m, new XC_MethodHook() {
                                @Override
                                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                    XposedBridge.log(TAG + "Layer 0: Suppressed " + param.method.getName());
                                    param.setResult(null);
                                }
                            });
                        }
                    }
                    XposedBridge.log(TAG + "Layer 0: Successfully hooked mixaudio.a.l/p/o");
                } catch (Throwable t) {
                    XposedBridge.log(TAG + "Layer 0 error hooking l/p/o: " + t.getMessage());
                }
            } else {
                XposedBridge.log(TAG + "Layer 0: Class com.coloros.accessibilityassistant.mixaudio.a not found");
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Layer 0 error: " + t.getMessage());
        }

        // --- LAYER 1: Hook Remote Handler: com.coloros.translate.engine.remote.MixAudioEngineHandler ---
        try {
            Class<?> handlerClass = XposedHelpers.findClassIfExists(
                "com.coloros.translate.engine.remote.MixAudioEngineHandler",
                lpparam.classLoader
            );
            if (handlerClass != null) {
                // Hook b(String, boolean, int, IMixAudioListener)
                for (java.lang.reflect.Method m : handlerClass.getDeclaredMethods()) {
                    if ("b".equals(m.getName()) && m.getParameterTypes().length == 4) {
                        XposedBridge.hookMethod(m, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                String path = (String) param.args[0];
                                XposedBridge.log(TAG + "Layer 1: Blocked MixAudioEngineHandler.b for: " + path);
                                Object listener = param.args[3];
                                if (listener != null) {
                                    try {
                                        XposedHelpers.callMethod(listener, "onPlayComplete", path != null ? path : "", 0);
                                        XposedBridge.log(TAG + "Layer 1: Invoked listener.onPlayComplete");
                                    } catch (Throwable t) {
                                        XposedBridge.log(TAG + "Layer 1 listener error: " + t.getMessage());
                                    }
                                }
                                param.setResult(null);
                            }
                        });
                        XposedBridge.log(TAG + "Layer 1: Successfully hooked MixAudioEngineHandler.b");
                        break;
                    }
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Layer 1 error: " + t.getMessage());
        }

        // --- LAYER 2: Hook Engine Implementation: com.coloros.translate.engine.mixaudio.engine.c ---
        try {
            Class<?> engineImplClass = XposedHelpers.findClassIfExists(
                "com.coloros.translate.engine.mixaudio.engine.c",
                lpparam.classLoader
            );
            if (engineImplClass != null) {
                for (java.lang.reflect.Method m : engineImplClass.getDeclaredMethods()) {
                    if ("mixPromptAudio".equals(m.getName())) {
                        XposedBridge.hookMethod(m, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                String path = (String) param.args[0];
                                XposedBridge.log(TAG + "Layer 2: Blocked engine.c.mixPromptAudio for: " + path);
                                Object listener = param.args[3];
                                if (listener != null) {
                                    try {
                                        XposedHelpers.callMethod(listener, "onPlayComplete", path != null ? path : "", 0);
                                        XposedBridge.log(TAG + "Layer 2: Invoked listener.onPlayComplete");
                                    } catch (Throwable t) {
                                        XposedBridge.log(TAG + "Layer 2 listener error: " + t.getMessage());
                                    }
                                }
                                param.setResult(null);
                            }
                        });
                        XposedBridge.log(TAG + "Layer 2: Successfully hooked engine.c.mixPromptAudio");
                        break;
                    }
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Layer 2 error: " + t.getMessage());
        }

        // --- LAYER 3: Hook Manager Entry Point: com.coloros.translate.engine.mixaudio.a.e ---
        try {
            Class<?> managerClass = XposedHelpers.findClassIfExists(
                "com.coloros.translate.engine.mixaudio.a",
                lpparam.classLoader
            );
            if (managerClass != null) {
                for (java.lang.reflect.Method m : managerClass.getDeclaredMethods()) {
                    if ("e".equals(m.getName()) || "h".equals(m.getName())) {
                        XposedBridge.hookMethod(m, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                XposedBridge.log(TAG + "Layer 3: Blocked mixaudio.a." + param.method.getName());
                                for (Object arg : param.args) {
                                    if (arg != null) {
                                        try {
                                            XposedHelpers.callMethod(arg, "onPlayComplete", "", 0);
                                            XposedBridge.log(TAG + "Layer 3: Invoked onPlayComplete");
                                            break;
                                        } catch (Throwable ignored) {}
                                    }
                                }
                                param.setResult(null);
                            }
                        });
                    }
                }
                XposedBridge.log(TAG + "Layer 3: Successfully hooked mixaudio.a.e/h");
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Layer 3 error: " + t.getMessage());
        }

        // --- LAYER 4: Hook Worker Player: com.coloros.translate.engine.mixaudio.mix.a.e ---
        try {
            Class<?> workerClass = XposedHelpers.findClassIfExists(
                "com.coloros.translate.engine.mixaudio.mix.a",
                lpparam.classLoader
            );
            if (workerClass != null) {
                for (java.lang.reflect.Method m : workerClass.getDeclaredMethods()) {
                    if ("e".equals(m.getName())) {
                        XposedBridge.hookMethod(m, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                File f = (File) param.args[0];
                                String path = f != null ? f.getAbsolutePath() : "";
                                XposedBridge.log(TAG + "Layer 4: Blocked mix.a.e for: " + path);
                                try {
                                    Object listener = XposedHelpers.getObjectField(param.thisObject, "f");
                                    if (listener != null) {
                                        XposedHelpers.callMethod(listener, "onPlayComplete", path, 0);
                                        XposedBridge.log(TAG + "Layer 4: Called listener.onPlayComplete");
                                    }
                                } catch (Throwable t) {
                                    XposedBridge.log(TAG + "Layer 4 listener error: " + t.getMessage());
                                }
                                param.setResult(null);
                            }
                        });
                        XposedBridge.log(TAG + "Layer 4: Successfully hooked mix.a.e");
                        break;
                    }
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Layer 4 error: " + t.getMessage());
        }

        // --- LAYER 5: Hook Downlink PCM Player: com.coloros.translate.engine.mixaudio.mix.c.d ---
        try {
            Class<?> downClass = XposedHelpers.findClassIfExists(
                "com.coloros.translate.engine.mixaudio.mix.c",
                lpparam.classLoader
            );
            if (downClass != null) {
                for (java.lang.reflect.Method m : downClass.getDeclaredMethods()) {
                    if ("d".equals(m.getName()) && m.getParameterTypes().length == 1) {
                        XposedBridge.hookMethod(m, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                XposedBridge.log(TAG + "Layer 5: Blocked mix.c.d(InputStream)");
                                param.setResult(null);
                            }
                        });
                        XposedBridge.log(TAG + "Layer 5: Successfully hooked mix.c.d");
                        break;
                    }
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Layer 5 error: " + t.getMessage());
        }

        // --- LAYER 6: Safety Net: AudioTrack.play() ---
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
                                XposedBridge.log(TAG + "Layer 6: Suppressed AudioTrack.play from: " + cls);
                                param.setResult(null);
                                return;
                            }
                        }
                    }
                }
            );
            XposedBridge.log(TAG + "Layer 6: Successfully hooked AudioTrack.play");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Layer 6 error: " + t.getMessage());
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
    }
}
