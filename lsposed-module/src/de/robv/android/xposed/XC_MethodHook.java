package de.robv.android.xposed;

import java.lang.reflect.Member;

public abstract class XC_MethodHook {
    public static class MethodHookParam {
        public Member method;
        public Object thisObject;
        public Object[] args;
        private Object result;
        private Throwable throwable;
        public void setResult(Object result) { this.result = result; }
        public Object getResult() { return result; }
        public void setThrowable(Throwable throwable) { this.throwable = throwable; }
        public Throwable getThrowable() { return throwable; }
    }

    public static class Unhook {
        public XC_MethodHook getCallback() { return null; }
        public void unhook() {}
    }

    public XC_MethodHook() {}
    public XC_MethodHook(int priority) {}
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {}
    protected void afterHookedMethod(MethodHookParam param) throws Throwable {}
}
