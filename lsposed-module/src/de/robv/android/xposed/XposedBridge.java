package de.robv.android.xposed;

import java.lang.reflect.Member;
import java.util.HashSet;
import java.util.Set;

public class XposedBridge {
    public static void log(String text) {
        android.util.Log.i("LSPosed-SilentCall", text);
    }
    public static void log(Throwable t) {
        android.util.Log.e("LSPosed-SilentCall", "Exception", t);
    }
    public static XC_MethodHook.Unhook hookMethod(Member hookMethod, XC_MethodHook callback) {
        return null;
    }
    public static Set<XC_MethodHook.Unhook> hookAllMethods(Class<?> hookClass, String methodName, XC_MethodHook callback) {
        return new HashSet<>();
    }
}
