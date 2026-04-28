
import de.robv.android.xposed.*;

var onResumeHook = null

void onLoad() {
    var onResumeMethod = com.tencent.mm.ui.MoreTabUI.class.getDeclaredMethod("onResume", (Object[]) {})
    onResumeHook = XposedBridge.hookMethod(onResumeMethod, new XC_MethodHook() {
        void afterHookedMethod(XC_MethodHook.MethodHookParam param) {
            toast("onResume")
        }
    });
}

void onUnload() {
    if (onResumeHook != null) {
        onResumeHook.unhook();
        onResumeHook = null;
    }
}
