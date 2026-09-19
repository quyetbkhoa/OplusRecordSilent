package de.robv.android.xposed;
public class XposedBridge {
    public static void log(String text) {
        android.util.Log.i("LSPosed-SilentCall", text);
    }
    public static void log(Throwable t) {
        android.util.Log.e("LSPosed-SilentCall", "Exception", t);
    }
}
