
import de.robv.android.xposed.*;

var onBeforeHook = null
var onAfterHook = null

void onLoad() {
    var method = com.tencent.mm.ui.MoreTabUI.class.getDeclaredMethod("onResume")

    // 内置Hook方法(hookBefore / hookAfter / hookReplace)
    onBeforeHook = hookBefore(method, param -> {
        log("onResume Before")
    });

    // 原生Hook方法(需关闭 Xposed API 调用保护)
    onAfterHook = XposedBridge.hookMethod(method, new XC_MethodHook() {
        void afterHookedMethod(XC_MethodHook.MethodHookParam param) {
            log("onResume After")
        }
    });

    // 内置DexKit方法(findClassList / findMemberList)
    var keyWord = "doRevokeMsg xmlSrvMsgId=%d talker=%s isGet=%s"
    var classList = findClassList({keyWord})
    log("find class list size: ${classList.size()}")
    var memberList = findMemberList({keyWord})
    log("find member list size: ${memberList.size()}")
}

void onUnload() {
    if (onBeforeHook != null) {
        // 内置Hook卸载
        unhook(onBeforeHook);
        onBeforeHook = null;
    }

    if (onAfterHook != null) {
        // 原生Hook卸载
        onAfterHook.unhook();
        onAfterHook = null;
    }
}
