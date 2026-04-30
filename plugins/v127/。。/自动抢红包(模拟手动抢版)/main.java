import android.content.Context;
import android.content.Intent;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import android.os.Bundle;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import android.content.ContentValues;
import com.tencent.wcdb.database.SQLiteDatabase;
import android.view.ViewTreeObserver;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import org.xml.sax.InputSource;
import org.w3c.dom.*;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.util.*;
import java.util.regex.*;
import me.hd.wauxv.data.bean.info.FriendInfo;
import me.hd.wauxv.data.bean.info.GroupInfo;

boolean getHookState() {
    try {
        de.robv.android.xposed.XposedBridge.class;
        return true;
    } catch (Throwable e) {
        return false;
    }
}

if (!getHookState()) return toast("请关闭LSPosed调用保护");

import de.robv.android.xposed.XC_MethodHook.MethodHookParam;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

// ================= 配置键名常量 =================
String KEY_ENABLE = "hb_auto_enable";
String KEY_SKIP_SELF = "hb_skip_self";
String KEY_AUTO_CLOSE = "hb_auto_close";
String KEY_MODE = "hb_auto_mode";
String KEY_WHITELIST = "hb_auto_whitelist";
String KEY_BLACKLIST = "hb_auto_blacklist";
String KEY_DELAY_VALUE = "hb_auto_delay_value";
String KEY_DELAY_UNIT = "hb_auto_delay_unit";
String KEY_REPLY_ENABLE = "hb_reply_enable";
String KEY_REPLY_TEXT = "hb_reply_text";
String KEY_REPLY_RANDOM = "hb_reply_random";
String KEY_REPLY_TEMPLATES = "hb_reply_templates";
String KEY_REPLY_CUSTOM_ENABLE = "hb_reply_custom_enable";
String KEY_REPLY_DELAY_VALUE = "hb_reply_delay_value";
String KEY_REPLY_DELAY_UNIT = "hb_reply_delay_unit";
String KEY_KW_MODE = "hb_kw_mode";
String KEY_KEYWORDS = "hb_keywords";
String KEY_LOG_ENABLE = "hb_log_enable";
String KEY_CHECK_TIMES = "hb_check_times";
String KEY_STATS_COUNT = "hb_stats_count";
String KEY_STATS_AMOUNT = "hb_stats_amount";
String KEY_STATS_FAILED = "hb_stats_failed";
String KEY_STATS_TODAY = "hb_stats_today";

// ================= 全局变量 =================
Set<String> sProcessedRedBags = new HashSet<>();
Set<String> sProcessingRedBags = new HashSet<>();
List sCachedFriendList = null;
List sCachedGroupList = null;
List<Object> allHooks = new ArrayList<>();
Map<String, String> sRedBagReplyMap = new HashMap<>();
Map<Activity, Boolean> sClickedActivities = new WeakHashMap<>();
Map<String, String> sRedBagSenderMap = new HashMap<>();
Map<String, String> sRedBagContentMap = new HashMap<>();
Map<String, String> sRedBagTalkerMap = new HashMap<>();
Deque<String> sRecentRedBagContents = new ArrayDeque<>();
Map<View, Boolean> sClickedViews = new WeakHashMap<>();
int MAX_RECENT_CONTENT = 30;
int failCount = 0;
Random random = new Random();
boolean mLogEnabled = true;

// ================= 日志工具 (带开关) =================
void logx(Object msg) {
    if (mLogEnabled) {
        log(msg);
    }
}

// ================= 统计功能 =================
void incrementStats(String nativeurl) {
    if (TextUtils.isEmpty(nativeurl)) return;
    if (sProcessedRedBags.contains(nativeurl)) {
        logx(">> 统计防重: 该红包已统计过");
        return;
    }
    sProcessedRedBags.add(nativeurl);
    int count = getInt(KEY_STATS_COUNT, 0);
    putInt(KEY_STATS_COUNT, count + 1);
    String today = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    String lastDate = getString(KEY_STATS_TODAY, "");
    if (!today.equals(lastDate)) {
        putString(KEY_STATS_TODAY, today);
        putInt("hb_stats_today_count", 1);
    } else {
        int todayCount = getInt("hb_stats_today_count", 0);
        putInt("hb_stats_today_count", todayCount + 1);
    }
    logx(">> 统计成功: 总计" + (count + 1) + "个");
}

void incrementFailedStats() {
    int failed = getInt(KEY_STATS_FAILED, 0);
    putInt(KEY_STATS_FAILED, failed + 1);
    logx(">> 失败统计+1, 当前失败总数: " + (failed + 1));
}

void recordAmount(String amountStr, String nativeurl) {
    if (TextUtils.isEmpty(nativeurl)) return;
    String amountKey = "hb_amount_" + nativeurl;
    if (getBoolean(amountKey, false)) {
        logx(">> 金额防重: 该红包金额已记录过");
        return;
    }
    try {
        Pattern pattern = Pattern.compile("(\\d+\\.\\d+)");
        Matcher matcher = pattern.matcher(amountStr);
        if (matcher.find()) {
            float amount = Float.parseFloat(matcher.group(1));
            float total = getFloat(KEY_STATS_AMOUNT, 0f);
            putFloat(KEY_STATS_AMOUNT, total + amount);
            putBoolean(amountKey, true);
            logx(">> 记录金额: " + amount + "元");
        }
    } catch (Exception e) {
        logx("ERROR: 记录金额失败: " + e.getMessage());
    }
}

long getDelayValue(String valueKey, String unitKey, int defaultValue, int defaultUnit) {
    int value = getInt(valueKey, defaultValue);
    int unit = getInt(unitKey, defaultUnit);
    if (unit == 1) {
        return value * 1000L;
    }
    return value;
}

// ================= 点击状态辅助方法 =================
boolean isViewClicked(View view) {
    return view != null && Boolean.TRUE.equals(sClickedViews.get(view));
}

void markViewClicked(View view) {
    if (view != null) {
        sClickedViews.put(view, true);
    }
}

boolean isActivityClicked(Activity activity) {
    return activity != null && Boolean.TRUE.equals(sClickedActivities.get(activity));
}

void markActivityClicked(Activity activity) {
    if (activity != null) {
        sClickedActivities.put(activity, true);
    }
}

// ================= 统一过滤逻辑 =================
String getRedBagRejectReason(String sender, String talker, String content, String nativeurl, String exclusiveRecvUser) {
    String myWxid = getLoginWxid();
    if (!TextUtils.isEmpty(exclusiveRecvUser) && !exclusiveRecvUser.equals(myWxid)) {
        return "不是发给我的专属红包";
    }
    boolean isSelfSent = !TextUtils.isEmpty(myWxid) && !TextUtils.isEmpty(sender) && sender.equals(myWxid);
    if (isSelfSent && getBoolean(KEY_SKIP_SELF, false)) {
        return "自己发的红包";
    }
    int listMode = getInt(KEY_MODE, 0);
    boolean isGroup = talker != null && talker.endsWith("@chatroom");
    if (listMode == 1) {
        boolean inWhite = checkUserInList(sender, KEY_WHITELIST);
        if (isGroup) {
            inWhite = inWhite || checkUserInList(talker, KEY_WHITELIST);
        }
        if (!inWhite) {
            return "非白名单用户或群聊";
        }
    } else if (listMode == 2) {
        boolean inBlack = checkUserInList(sender, KEY_BLACKLIST);
        if (isGroup) {
            inBlack = inBlack || checkUserInList(talker, KEY_BLACKLIST);
        }
        if (inBlack) {
            return "黑名单用户或群聊";
        }
    }
    int kwMode = getInt(KEY_KW_MODE, 0);
    String kws = getString(KEY_KEYWORDS, "");
    if (kwMode == 1 && !containsKeyword(content, kws)) {
        return "未包含指定关键词";
    } else if (kwMode == 2 && containsKeyword(content, kws)) {
        return "包含屏蔽关键词";
    }
    return null;
}

// ================= 入口函数 =================
boolean onClickSendBtn(String text) {
    if ("红包设置".equals(text)) {
        showSettingsUI();
        return true;
    }
    return false;
}



// ================= 红包处理逻辑 =================
void handleRedBag(final Object msg) {
    logx(">> 进入handleRedBag");
    if (!getBoolean(KEY_ENABLE, false)) {
        logx(">> 自动抢红包未开启");
        return;
    }
    String content = "";
    try {
        content = msg.getContent();
    } catch (Exception e) {
        logx("ERROR: 读取消息内容失败: " + e.getMessage());
    }
    if (content == null) content = "";
    final String talker = msg.getTalker();
    String sender = "";
    try {
        sender = msg.getSendTalker();
    } catch (Exception e) {
        logx("ERROR: 读取发送者失败: " + e.getMessage());
    }
    if (TextUtils.isEmpty(sender)) sender = talker;
    String nativeurl = getElementContent(content, "wcpayinfo", "nativeurl");
    String username = getElementContent(content, "wcpayinfo", "exclusive_recv_username");
    logx(">> handleRedBag解析: talker=" + talker + ", sender=" + sender + ", nativeurl=" + nativeurl);
    if (!TextUtils.isEmpty(nativeurl)) {
        sRedBagSenderMap.put(nativeurl, sender);
        sRedBagContentMap.put(nativeurl, content);
        sRedBagTalkerMap.put(nativeurl, talker);
    }
    if (!TextUtils.isEmpty(content)) {
        sRecentRedBagContents.addFirst(content);
        while (sRecentRedBagContents.size() > MAX_RECENT_CONTENT) {
            sRecentRedBagContents.removeLast();
        }
    }
    String rejectReason = getRedBagRejectReason(sender, talker, content, nativeurl, username);
    if (rejectReason != null) {
        logx(">> 忽略红包: " + rejectReason);
        return;
    }
    // 检查是否在处理中
    if (!TextUtils.isEmpty(nativeurl) && sProcessingRedBags.contains(nativeurl)) {
        logx(">> 红包已在处理中，跳过");
        return;
    }
    if (!TextUtils.isEmpty(nativeurl)) {
        sProcessingRedBags.add(nativeurl);
    }
    logx(">> 检测到红包 | 来自:" + getDisplayName(sender));
    final long delay = getDelayValue(KEY_DELAY_VALUE, KEY_DELAY_UNIT, 0, 0);
    final String finalNativeurl = nativeurl;
    // 判断是否是自己发的红包
    final boolean isSelfSent = !TextUtils.isEmpty(getLoginWxid()) && !TextUtils.isEmpty(sender) && sender.equals(getLoginWxid());
    if (getBoolean(KEY_REPLY_ENABLE, false) && !TextUtils.isEmpty(finalNativeurl)) {
        sRedBagReplyMap.put(finalNativeurl, talker);
        logx(">> 已记录自动回复目标: " + talker);
    }
    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
        public void run() {
            logx(">> 延迟结束，准备启动红包界面");
            Intent intent = new Intent();
            intent.putExtra("key_native_url", finalNativeurl);
            intent.putExtra("key_username", talker);
            intent.putExtra("key_is_self_sent", isSelfSent);
            startLuckyMoneyActivity(intent);
        }
    }, delay);
}

void startLuckyMoneyActivity(Intent intent) {
    try {
        logx(">> startLuckyMoneyActivity进入");
        String[] possibleClasses = {"nk4.l", "oq4.l", "pn4.l", "qm4.l", "rm4.l", "sm4.l", "tm4.l", "um4.l", "vm4.l", "wl4.l"};
        String[] possibleMethods = {"A", "B", "C", "D"};
        String[] possibleActivities = { ".ui.LuckyMoneyNewReceiveUI", ".ui.LuckyMoneyNotHookReceiveUI", ".ui.LuckyMoneyReceiveUI" };
        boolean success = false;
        for (String className : possibleClasses) {
            for (String methodName : possibleMethods) {
                for (String activity : possibleActivities) {
                    try {
                        Class<?> clazz = XposedHelpers.findClass(className, hostContext.getClassLoader());
                        XposedHelpers.callStaticMethod(clazz, methodName, hostContext, "luckymoney", activity, intent);
                        success = true;
                        logx(">> 成功启动红包界面: " + className + "." + methodName + " -> " + activity);
                        failCount = 0;
                        break;
                    } catch (Throwable ignored) {}
                }
                if (success) break;
            }
            if (success) break;
        }
        if (!success) {
            for (String activity : possibleActivities) {
                try {
                    intent.setClassName(hostContext.getPackageName(), "com.tencent.mm.plugin.luckymoney" + activity);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    hostContext.startActivity(intent);
                    success = true;
                    logx(">> 成功启动红包界面(降级方案): " + activity);
                    failCount = 0;
                    break;
                } catch (Throwable ignored) {}
            }
        }
        if (!success) {
            failCount++;
            logx("ERROR: 启动红包界面失败，失败次数: " + failCount);
            if (failCount > 3) {
                toast("红包页面启动失败，请检查微信版本");
                failCount = 0;
            }
        }
    } catch (Throwable e) {
        logx("ERROR: 启动红包界面异常: " + e.getMessage());
        toast("启动失败: " + e.getMessage());
    }
}

boolean shouldFilterRedBag(String nativeurl) {
    logx(">> shouldFilterRedBag检查, nativeurl=" + nativeurl);
    if (!TextUtils.isEmpty(nativeurl)) {
        String sender = sRedBagSenderMap.get(nativeurl);
        String content = sRedBagContentMap.get(nativeurl);
        String talker = sRedBagTalkerMap.get(nativeurl);
        String exclusiveRecvUser = null;
        if (!TextUtils.isEmpty(content)) {
            exclusiveRecvUser = getElementContent(content, "wcpayinfo", "exclusive_recv_username");
        }
        String rejectReason = getRedBagRejectReason(sender, talker, content, nativeurl, exclusiveRecvUser);
        if (rejectReason != null) {
            logx(">> 实时检查: " + rejectReason + "，当前配置不抢");
            return true;
        }
        logx(">> 实时检查通过，允许自动点击");
        return false;
    }
    int kwMode = getInt(KEY_KW_MODE, 0);
    String kws = getString(KEY_KEYWORDS, "");
    if (!TextUtils.isEmpty(kws) && !sRecentRedBagContents.isEmpty()) {
        boolean anyMatch = false;
        for (String c : sRecentRedBagContents) {
            if (!TextUtils.isEmpty(c) && containsKeyword(c, kws)) {
                anyMatch = true;
                break;
            }
        }
        if (kwMode == 2 && anyMatch) {
            logx(">> 兜底检查: 最近红包内容命中包含则不抢，当前不抢");
            return true;
        }
        if (kwMode == 1 && !anyMatch) {
            logx(">> 兜底检查: 最近红包内容未命中必须包含关键词，当前不抢");
            return true;
        }
    }
    logx(">> shouldFilterRedBag兜底放行");
    return false;
}

// ================= Hook 红包界面 =================
void onLoad() {
    mLogEnabled = getBoolean(KEY_LOG_ENABLE, true);
    logx(">> onLoad开始执行");
    hookDatabaseMethods();
    hookAllReceiveActivities();
    hookAllDetailActivities();
    logx(">> onLoad执行完成");
}

void hookDatabaseMethods() {
    try {
        Object hook = XposedBridge.hookMethod(
            SQLiteDatabase.class.getDeclaredMethod("insertWithOnConflict", new Class[] {String.class, String.class, ContentValues.class, int.class}),
            new XC_MethodHook() {
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    handleDatabaseInsert(param);
                }
            }
        );
        allHooks.add(hook);
        logx(">> 数据库insertWithOnConflict Hook成功");
    } catch (Exception e) {
        logx("ERROR: 数据库insertWithOnConflict Hook失败: " + e.getMessage());
    }
    try {
        Object hook = XposedBridge.hookMethod(
            SQLiteDatabase.class.getDeclaredMethod("insert", new Class[] {String.class, String.class, ContentValues.class}),
            new XC_MethodHook() {
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    handleDatabaseInsert(param);
                }
            }
        );
        allHooks.add(hook);
        logx(">> 数据库insert Hook成功");
    } catch (Exception e) {
        logx("ERROR: 数据库insert Hook失败: " + e.getMessage());
    }
    try {
        Object hook = XposedBridge.hookMethod(
            SQLiteDatabase.class.getDeclaredMethod("replace", new Class[] {String.class, String.class, ContentValues.class}),
            new XC_MethodHook() {
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    handleDatabaseInsert(param);
                }
            }
        );
        allHooks.add(hook);
        logx(">> 数据库replace Hook成功");
    } catch (Exception e) {
        logx("ERROR: 数据库replace Hook失败: " + e.getMessage());
    }
}

void handleDatabaseInsert(MethodHookParam param) throws Throwable {
    // 移除这里的日志，避免刷屏
    if (!getBoolean(KEY_ENABLE, false)) {
        return;
    }
    
    String tableName = (String) param.args[0];
    ContentValues values = null;
    if (param.args.length >= 3 && param.args[2] instanceof ContentValues) {
        values = (ContentValues) param.args[2];
    }
    if (tableName == null || values == null) return;
    if (!"message".equals(tableName)) return;
    
    String content = values.getAsString("content");
    Integer isSend = values.getAsInteger("isSend");
    String talker = values.getAsString("talker");
    Integer type = values.getAsInteger("type");
    
    // 只处理红包消息
    if (type == null || type != 436207665 || content == null) return;
    
    String nativeurl = getElementContent(content, "wcpayinfo", "nativeurl");
    if (TextUtils.isEmpty(nativeurl)) return;
    
    // ====== 防重复检查 ======
    // 检查该nativeurl是否已经在待处理队列中，避免重复处理
    if (sRedBagContentMap.containsKey(nativeurl)) {
        return; // 已经处理过，不再重复
    }
    // ========================
    
    String username = getElementContent(content, "wcpayinfo", "exclusive_recv_username");
    String myWxid = getLoginWxid();
    String sender = talker;
    if (isSend != null && isSend == 1) sender = myWxid;
    
    // 记录红包信息
    sRedBagSenderMap.put(nativeurl, sender);
    sRedBagContentMap.put(nativeurl, content);
    sRedBagTalkerMap.put(nativeurl, talker);
    
    if (!TextUtils.isEmpty(content)) {
        sRecentRedBagContents.addFirst(content);
        while (sRecentRedBagContents.size() > MAX_RECENT_CONTENT) {
            sRecentRedBagContents.removeLast();
        }
    }
    
    String rejectReason = getRedBagRejectReason(sender, talker, content, nativeurl, username);
    if (rejectReason != null) {
        logx(">> 数据库监听忽略: " + rejectReason);
        return;
    }
    
    if (getBoolean(KEY_REPLY_ENABLE, false)) {
        sRedBagReplyMap.put(nativeurl, talker);
    }
    
    final String finalNativeurl = nativeurl;
    final String finalTalker = talker;
    long delay = getDelayValue(KEY_DELAY_VALUE, KEY_DELAY_UNIT, 0, 0);
    
    logx(">> 数据库检测到新红包: " + finalNativeurl.substring(0, Math.min(30, finalNativeurl.length())) + "...");
    
    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
        public void run() {
            Intent intent = new Intent();
            intent.putExtra("key_native_url", finalNativeurl);
            intent.putExtra("key_username", finalTalker);
            startLuckyMoneyActivity(intent);
        }
    }, delay);
}

Method findMethodInHierarchy(Class<?> clazz, String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
    Class<?> current = clazz;
    while (current != null) {
        try {
            Method method = current.getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ignored) {}
        current = current.getSuperclass();
    }
    throw new NoSuchMethodException(clazz.getName() + "." + methodName + " " + Arrays.toString(parameterTypes));
}

void logMethodCandidates(Class<?> clazz, String methodName) {
    if (clazz == null || TextUtils.isEmpty(methodName)) return;
    try {
        Method[] methods = clazz.getDeclaredMethods();
        for (Method m : methods) {
            if (methodName.equals(m.getName())) {
                logx(">> 候选方法: " + clazz.getName() + "." + m.getName() + " " + Arrays.toString(m.getParameterTypes()));
            }
        }
    } catch (Throwable ignored) {}
}

void hookAllReceiveActivities() {
    String[] activityClasses = {
        "com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyNewReceiveUI",
        "com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyNotHookReceiveUI",
        "com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyReceiveUI"
    };
    for (String className : activityClasses) {
        try {
            Class<?> clazz = XposedHelpers.findClass(className, hostContext.getClassLoader());
            try {
                Method initView = findMethodInHierarchy(clazz, "initView");
                Object hook = XposedBridge.hookMethod(initView, new XC_MethodHook() {
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        logx(">> 领取页initView触发: " + param.thisObject.getClass().getName());
                        if (!getBoolean(KEY_ENABLE, false)) {
                            logx(">> 自动抢红包未开启");
                            return;
                        }
                        Object thisObject = param.thisObject;
                        final Activity activity = (Activity) thisObject;
                        Intent intent = activity.getIntent();
                        final String nativeurl = intent != null ? intent.getStringExtra("key_native_url") : null;
                        final boolean isSelfSent = intent != null && intent.getBooleanExtra("key_is_self_sent", false);
                        logx(">> 领取页当前nativeurl: " + nativeurl);
                        if (shouldFilterRedBag(nativeurl)) {
                            logx(">> 根据当前配置，此红包不应自动点击");
                            return;
                        }
                        if (TextUtils.isEmpty(nativeurl)) {
                            try {
                                String pageUser = intent != null ? intent.getStringExtra("key_username") : null;
                                String myWxid = getLoginWxid();
                                if (getBoolean(KEY_SKIP_SELF, false) && !TextUtils.isEmpty(pageUser) && !TextUtils.isEmpty(myWxid) && myWxid.equals(pageUser)) {
                                    logx(">> 兜底过滤: 页面key_username=自己，跳过自动点击");
                                    return;
                                }
                            } catch (Throwable ignore) {}
                        }
                        tryClickButton(activity, thisObject);
                        boolean autoClose = getBoolean(KEY_AUTO_CLOSE, true);
                        if (autoClose) startContinuousCheck(activity, nativeurl, isSelfSent);
                    }
                });
                allHooks.add(hook);
                logx(">> 成功Hook领取页: " + className + ".initView");
            } catch (Throwable e) {
                logx("ERROR: Hook领取页initView失败: " + className + " | " + e.getMessage());
                logMethodCandidates(clazz, "initView");
            }
            try {
                Method onCreate = findMethodInHierarchy(clazz, "onCreate", Bundle.class);
                Object hook = XposedBridge.hookMethod(onCreate, new XC_MethodHook() {
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        logx(">> 领取页onCreate触发: " + param.thisObject.getClass().getName());
                        if (!getBoolean(KEY_ENABLE, false)) return;
                        if (!getBoolean(KEY_AUTO_CLOSE, true)) return;
                        final Activity activity = (Activity) param.thisObject;
                        Intent intent = activity.getIntent();
                        final String nativeurl = intent != null ? intent.getStringExtra("key_native_url") : null;
                        final boolean isSelfSent = intent != null && intent.getBooleanExtra("key_is_self_sent", false);
                        startContinuousCheck(activity, nativeurl, isSelfSent);
                    }
                });
                allHooks.add(hook);
            } catch (Throwable e) {
                logx("ERROR: Hook领取页onCreate失败: " + className + " | " + e.getMessage());
                logMethodCandidates(clazz, "onCreate");
            }
            try {
                Method onResume = findMethodInHierarchy(clazz, "onResume");
                Object hook = XposedBridge.hookMethod(onResume, new XC_MethodHook() {
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        logx(">> 领取页onResume触发: " + param.thisObject.getClass().getName());
                        if (!getBoolean(KEY_ENABLE, false)) return;
                        if (!getBoolean(KEY_AUTO_CLOSE, true)) return;
                        final Activity activity = (Activity) param.thisObject;
                        if (isActivityClicked(activity)) {
                            logx(">> 当前页面已点击过，onResume跳过");
                            return;
                        }
                        Intent intent = activity.getIntent();
                        final String nativeurl = intent != null ? intent.getStringExtra("key_native_url") : null;
                        final boolean isSelfSent = intent != null && intent.getBooleanExtra("key_is_self_sent", false);
                        logx(">> onResume当前nativeurl: " + nativeurl);
                        if (shouldFilterRedBag(nativeurl)) {
                            logx(">> onResume检测: 当前红包根据最新配置仍不应自动点击");
                            return;
                        }
                        tryClickButton(activity, param.thisObject);
                    }
                });
                allHooks.add(hook);
            } catch (Throwable e) {
                logx("ERROR: Hook领取页onResume失败: " + className + " | " + e.getMessage());
                logMethodCandidates(clazz, "onResume");
            }
            try {
                Method onDestroy = findMethodInHierarchy(clazz, "onDestroy");
                Object hook = XposedBridge.hookMethod(onDestroy, new XC_MethodHook() {
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Activity activity = (Activity) param.thisObject;
                        sClickedActivities.remove(activity);
                        logx(">> 领取页onDestroy触发，清理点击状态");
                    }
                });
                allHooks.add(hook);
            } catch (Throwable e) {
                logx("ERROR: Hook领取页onDestroy失败: " + className + " | " + e.getMessage());
                logMethodCandidates(clazz, "onDestroy");
            }
        } catch (Throwable e) {
            logx("ERROR: 查找领取页类失败: " + className + " | " + e.getMessage());
        }
    }
}

void startContinuousCheck(final Activity activity, final String nativeurl, final boolean isSelfSent) {
    logx(">> startContinuousCheck开始, nativeurl=" + nativeurl);
    final Handler handler = new Handler(activity.getMainLooper());
    final int[] checkCount = {0};
    final int maxChecks = getInt(KEY_CHECK_TIMES, 10);
    final Runnable checkRunnable = new Runnable() {
        public void run() {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            boolean shouldClose = checkAndCloseIfFailed(activity, nativeurl);
            if (!shouldClose && checkCount[0] < maxChecks) {
                checkCount[0]++;
                handler.postDelayed(this, 300);
            }
        }
    };
    handler.postDelayed(checkRunnable, 300);
}

void hookAllDetailActivities() {
    String[] detailClasses = {
        "com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyDetailUI",
        "com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyNewDetailUI",
        "com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyBeforeDetailUI"
    };
    for (String className : detailClasses) {
        try {
            Class<?> clazz = XposedHelpers.findClass(className, hostContext.getClassLoader());
            try {
                Method onCreate = findMethodInHierarchy(clazz, "onCreate", Bundle.class);
                Object hook = XposedBridge.hookMethod(onCreate, new XC_MethodHook() {
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        logx(">> 详情页onCreate触发: " + param.thisObject.getClass().getName());
                        if (!getBoolean(KEY_ENABLE, false)) return;
                        final Activity activity = (Activity) param.thisObject;
                        Intent intent = activity.getIntent();
                        final String nativeurl = intent != null ? intent.getStringExtra("key_native_url") : null;
                        final boolean isSelfSent = intent != null && intent.getBooleanExtra("key_is_self_sent", false);
                        new Handler(activity.getMainLooper()).postDelayed(new Runnable() {
                            public void run() {
                                checkAndCloseIfSuccess(activity, nativeurl, isSelfSent);
                            }
                        }, 100);
                    }
                });
                allHooks.add(hook);
                logx(">> 成功Hook详情页: " + className + ".onCreate");
            } catch (Throwable e) {
                logx("ERROR: Hook详情页onCreate失败: " + className + " | " + e.getMessage());
                logMethodCandidates(clazz, "onCreate");
            }
            try {
                Method onResume = findMethodInHierarchy(clazz, "onResume");
                Object hook = XposedBridge.hookMethod(onResume, new XC_MethodHook() {
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        logx(">> 详情页onResume触发: " + param.thisObject.getClass().getName());
                        if (!getBoolean(KEY_ENABLE, false)) return;
                        final Activity activity = (Activity) param.thisObject;
                        Intent intent = activity.getIntent();
                        final String nativeurl = intent != null ? intent.getStringExtra("key_native_url") : null;
                        final boolean isSelfSent = intent != null && intent.getBooleanExtra("key_is_self_sent", false);
                        new Handler(activity.getMainLooper()).postDelayed(new Runnable() {
                            public void run() {
                                checkAndCloseIfSuccess(activity, nativeurl, isSelfSent);
                            }
                        }, 100);
                    }
                });
                allHooks.add(hook);
            } catch (Throwable e) {
                logx("ERROR: Hook详情页onResume失败: " + className + " | " + e.getMessage());
                logMethodCandidates(clazz, "onResume");
            }
        } catch (Throwable e) {
            logx("ERROR: 查找详情页类失败: " + className + " | " + e.getMessage());
        }
    }
}

void tryClickButton(Activity activity, Object thisObject) {
    logx(">> 进入tryClickButton");
    if (activity == null || thisObject == null) return;
    if (isActivityClicked(activity)) {
        logx(">> 当前页面已执行过点击，跳过重复点击");
        return;
    }
    String[] possibleFields = {"p","q","r","s","t","u","v","w","x","y","z","a","b","c","d","e","f","g","h","i","j","k","l","m","n","o"};
    boolean found = false;
    for (String fieldName : possibleFields) {
        try {
            Object findViewByIdBtn = XposedHelpers.getObjectField(thisObject, fieldName);
            if (findViewByIdBtn instanceof Button) {
                Button button = (Button) findViewByIdBtn;
                if (!isViewClicked(button)) {
                    clickButton(button);
                    markViewClicked(button);
                    found = true;
                    logx(">> 通过字段名找到按钮: " + fieldName);
                    break;
                }
            }
        } catch (Throwable ignored) {}
    }
    if (!found) {
        for (Field field : thisObject.getClass().getDeclaredFields()) {
            try {
                field.setAccessible(true);
                Object obj = field.get(thisObject);
                if (obj instanceof Button) {
                    Button button = (Button) obj;
                    if (isViewClicked(button)) continue;
                    String text = button.getText() != null ? button.getText().toString() : "";
                    if (text.contains("開") || text.contains("拆") || text.contains("领取") || text.isEmpty()) {
                        clickButton(button);
                        markViewClicked(button);
                        found = true;
                        logx(">> 通过遍历字段找到按钮: " + field.getName());
                        break;
                    }
                }
            } catch (Throwable ignored) {}
        }
    }
    if (!found) {
        View rootView = activity.getWindow().getDecorView();
        found = findAndClickButton(rootView);
    }
    if (found) {
        markActivityClicked(activity);
        logx(">> 已标记当前页面为已点击");
    } else {
        logx(">> tryClickButton未找到可点击按钮");
    }
}

boolean checkAndCloseIfFailed(Activity activity, String nativeurl) {
    try {
        View rootView = activity.getWindow().getDecorView();
        boolean shouldClose = checkFailedStatus(rootView);
        if (shouldClose) {
            logx(">> 检测到红包已派完，自动关闭页面");
            incrementFailedStats();
            if (!TextUtils.isEmpty(nativeurl)) {
                sRedBagReplyMap.remove(nativeurl);
                sProcessingRedBags.remove(nativeurl);
                sRedBagSenderMap.remove(nativeurl);
                sRedBagContentMap.remove(nativeurl);
                sRedBagTalkerMap.remove(nativeurl);
            }
            sClickedActivities.remove(activity);
            activity.finish();
            return true;
        }
        return false;
    } catch (Throwable e) {
        logx("ERROR: 检测红包状态异常: " + e.getMessage());
        return false;
    }
}

void checkAndCloseIfSuccess(Activity activity, String nativeurl, boolean isSelfSent) {
    try {
        View rootView = activity.getWindow().getDecorView();
        boolean isSuccess = checkSuccessStatus(rootView);
        if (isSuccess) {
            logx(">> 检测到红包领取成功");
            incrementStats(nativeurl);
            String amountText = extractAmountFromView(rootView);
            if (!TextUtils.isEmpty(amountText)) {
                recordAmount(amountText, nativeurl);
            }
            // 只有不是自己发的红包才自动回复
            if (getBoolean(KEY_REPLY_ENABLE, false) && !isSelfSent && !TextUtils.isEmpty(nativeurl)) {
                String replyTo = sRedBagReplyMap.get(nativeurl);
                if (!TextUtils.isEmpty(replyTo)) {
                    String replyTemplates = getString(KEY_REPLY_TEMPLATES, "");
                    String replyText = "";
                    if (!TextUtils.isEmpty(replyTemplates)) {
                        String[] templates = replyTemplates.split("\\|");
                        replyText = templates[random.nextInt(templates.length)].trim();
                    }
                    if (TextUtils.isEmpty(replyText)) {
                        replyText = getString(KEY_REPLY_TEXT, "谢谢老板");
                    }
                    if (!TextUtils.isEmpty(replyText)) {
                        final String finalReplyTo = replyTo;
                        final String finalReplyText = replyText;
                        long baseDelay = 0;
                        if (getBoolean(KEY_REPLY_CUSTOM_ENABLE, false)) {
                            baseDelay = getDelayValue(KEY_REPLY_DELAY_VALUE, KEY_REPLY_DELAY_UNIT, 1, 1);
                        }
                        long randomDelay = 0;
                        if (getBoolean(KEY_REPLY_RANDOM, true)) {
                            randomDelay = random.nextInt(2000);
                        }
                        final long totalReplyDelay = baseDelay + randomDelay;
                        new Thread(new Runnable() {
                            public void run() {
                                try {
                                    Thread.sleep(totalReplyDelay);
                                    sendText(finalReplyTo, finalReplyText);
                                    logx(">> 已自动回复: " + finalReplyText + " (延迟:" + totalReplyDelay + "ms)");
                                } catch (Exception e) {
                                    logx("ERROR: 自动回复失败: " + e.getMessage());
                                }
                            }
                        }).start();
                    }
                }
            }
            if (!TextUtils.isEmpty(nativeurl)) {
                sRedBagReplyMap.remove(nativeurl);
                sProcessingRedBags.remove(nativeurl);
                sRedBagSenderMap.remove(nativeurl);
                sRedBagContentMap.remove(nativeurl);
                sRedBagTalkerMap.remove(nativeurl);
            }
            if (getBoolean(KEY_AUTO_CLOSE, true)) activity.finish();
        }
    } catch (Throwable e) {
        logx("ERROR: 检测红包详情异常: " + e.getMessage());
    }
}

String extractAmountFromView(View view) {
    if (view == null) return null;
    if (view instanceof TextView) {
        TextView tv = (TextView) view;
        String text = tv.getText() != null ? tv.getText().toString() : "";
        Pattern pattern = Pattern.compile("(\\d+\\.\\d+)元");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(0);
        }
    }
    if (view instanceof ViewGroup) {
        ViewGroup vg = (ViewGroup) view;
        for (int i = 0; i < vg.getChildCount(); i++) {
            String result = extractAmountFromView(vg.getChildAt(i));
            if (result != null) return result;
        }
    }
    return null;
}

boolean checkFailedStatus(View view) {
    if (view == null) return false;
    if (view instanceof TextView) {
        TextView tv = (TextView) view;
        String text = tv.getText() != null ? tv.getText().toString() : "";
        if (text.contains("手慢了") || text.contains("红包派完了") || text.contains("已被领完") || text.contains("来晚了") || text.contains("手慢") || text.contains("派完") || text.contains("已抢完") || text.contains("已领完") || text.contains("红包已被抢完") || text.contains("红包已领完") || text.contains("该红包已超过") || text.contains("已过期")) {
            return true;
        }
    }
    if (view instanceof ViewGroup) {
        ViewGroup vg = (ViewGroup) view;
        for (int i = 0; i < vg.getChildCount(); i++) {
            if (checkFailedStatus(vg.getChildAt(i))) return true;
        }
    }
    return false;
}

boolean checkSuccessStatus(View view) {
    if (view == null) return false;
    if (view instanceof TextView) {
        TextView tv = (TextView) view;
        String text = tv.getText() != null ? tv.getText().toString() : "";
        if ((text.contains("已存入") && text.contains("余额")) || text.contains("已存入") || text.matches(".*\\d+\\.\\d+元.*")) {
            return true;
        }
    }
    if (view instanceof ViewGroup) {
        ViewGroup vg = (ViewGroup) view;
        for (int i = 0; i < vg.getChildCount(); i++) {
            if (checkSuccessStatus(vg.getChildAt(i))) return true;
        }
    }
    return false;
}

boolean findAndClickButton(View view) {
    if (view == null) return false;
    if (isViewClicked(view)) return false;
    if (view instanceof Button) {
        Button button = (Button) view;
        String text = button.getText() != null ? button.getText().toString() : "";
        if (text.contains("開") || text.contains("拆") || text.contains("领取") || (view.getContentDescription() != null && view.getContentDescription().toString().contains("開"))) {
            clickButton(button);
            markViewClicked(button);
            logx(">> 通过View树找到按钮");
            return true;
        }
    }
    if (view.isClickable() && view instanceof TextView) {
        TextView tv = (TextView) view;
        String text = tv.getText() != null ? tv.getText().toString() : "";
        if (text.contains("開") || text.contains("拆") || text.contains("领取")) {
            clickView(view);
            markViewClicked(view);
            logx(">> 通过View树找到可点击TextView");
            return true;
        }
    }
    if (view instanceof ViewGroup) {
        ViewGroup vg = (ViewGroup) view;
        for (int i = 0; i < vg.getChildCount(); i++) {
            if (findAndClickButton(vg.getChildAt(i))) {
                return true;
            }
        }
    }
    return false;
}

void clickButton(final Button button) {
    button.setEnabled(true);
    button.post(new Runnable() {
        public void run() {
            button.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                public void onGlobalLayout() {
                    if (button.isEnabled() && button.getVisibility() == View.VISIBLE) {
                        try {
                            button.performClick();
                            logx(">> 按钮已点击");
                        } catch (Throwable e) {
                            logx("ERROR: 按钮点击失败: " + e.getMessage());
                        }
                        button.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    }
                }
            });
        }
    });
}

void clickView(final View view) {
    view.setEnabled(true);
    view.post(new Runnable() {
        public void run() {
            view.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                public void onGlobalLayout() {
                    if (view.isEnabled() && view.getVisibility() == View.VISIBLE) {
                        try {
                            view.performClick();
                            logx(">> View已点击");
                        } catch (Throwable e) {
                            logx("ERROR: View点击失败: " + e.getMessage());
                        }
                        view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    }
                }
            });
        }
    });
}

void onUnload() {
    logx(">> onUnload开始执行");
    for (Object h : allHooks) {
        try {
            if (h != null) {
                XposedBridge.unhookMethod((XC_MethodHook.Unhook) h);
            }
        } catch (Exception e) {
            logx("ERROR: 卸载Hook失败: " + e.getMessage());
        }
    }
    allHooks.clear();
    sRedBagReplyMap.clear();
    sClickedActivities.clear();
    sProcessingRedBags.clear();
    sRedBagSenderMap.clear();
    sRedBagContentMap.clear();
    sRedBagTalkerMap.clear();
    sRecentRedBagContents.clear();
    sClickedViews.clear();
    sProcessedRedBags.clear();
    sCachedFriendList = null;
    sCachedGroupList = null;
    logx(">> onUnload执行完成");
}

boolean checkUserInList(String user, String key) {
    if (TextUtils.isEmpty(user)) return false;
    String listStr = getString(key, "");
    if (TextUtils.isEmpty(listStr)) return false;
    String[] arr = listStr.split(",");
    for (String s : arr) {
        if (s.trim().equals(user)) return true;
    }
    return false;
}

boolean containsKeyword(String text, String kws) {
    if (TextUtils.isEmpty(kws) || TextUtils.isEmpty(text)) return false;
    String[] arr = kws.split("[,，]");
    for (String kw : arr) {
        if (!TextUtils.isEmpty(kw) && text.contains(kw.trim())) return true;
    }
    return false;
}

String getDisplayName(String wxid) {
    try {
        String name = getFriendName(wxid);
        return TextUtils.isEmpty(name) ? wxid : name;
    } catch (Exception e) {
        return wxid;
    }
}

String getElementContent(String xmlString, String tagName) {
    try {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        ByteArrayInputStream input = new ByteArrayInputStream(xmlString.getBytes("UTF-8"));
        Document document = builder.parse(input);
        NodeList elements = document.getElementsByTagName(tagName);
        if (elements.getLength() > 0) return elements.item(0).getTextContent();
    } catch (Exception e) {
        return null;
    }
    return null;
}

String getElementContent(String xmlString, String elementName, String tagName) {
    try {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(new InputSource(new StringReader(xmlString.replaceAll("^[^:]+:\n", ""))));
        NodeList referMsgList = document.getElementsByTagName(elementName);
        if (referMsgList.getLength() > 0) {
            Node referMsgNode = referMsgList.item(0);
            NodeList contentList = referMsgNode.getChildNodes();
            for (int i = 0; i < contentList.getLength(); i++) {
                Node contentNode = contentList.item(i);
                if (contentNode.getNodeName().equalsIgnoreCase(tagName)) {
                    return contentNode.getTextContent();
                }
            }
        }
    } catch (Exception e) {
        return null;
    }
    return null;
}

// ================= UI 构建逻辑 =================
void showSettingsUI() {
    Activity ctx = getTopActivity();
    if (ctx == null) return;
    ctx.runOnUiThread(new Runnable() {
        public void run() {
            try {
                showDialogInternal(ctx);
            } catch (Exception e) {
                toast("UI Error: " + e);
            }
        }
    });
}

void showDialogInternal(final Activity ctx) {
    ScrollView scrollView = new ScrollView(ctx);
    scrollView.setBackgroundColor(Color.parseColor("#F5F6F8"));
    LinearLayout root = new LinearLayout(ctx);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(30, 30, 30, 30);
    scrollView.addView(root);

    addStatsCard(ctx, root);

    LinearLayout card1 = createCard(ctx);
    root.addView(card1);
    addSectionTitle(ctx, card1, "🧧 基础设置");
    final Switch swEnable = addSwitch(ctx, card1, "开启自动抢红包", getBoolean(KEY_ENABLE, false));
    final Switch swSkipSelf = addSwitch(ctx, card1, "不抢自己发的红包", getBoolean(KEY_SKIP_SELF, false));
    final Switch swAutoClose = addSwitch(ctx, card1, "自动关闭红包页面", getBoolean(KEY_AUTO_CLOSE, true));
    final Switch swLogEnable = addSwitch(ctx, card1, "开启日志输出", getBoolean(KEY_LOG_ENABLE, true));

    LinearLayout delayCard = createCard(ctx);
    root.addView(delayCard);
    addSectionTitle(ctx, delayCard, "⏱️ 抢红包延迟");
    LinearLayout delayLayout = new LinearLayout(ctx);
    delayLayout.setOrientation(LinearLayout.HORIZONTAL);
    delayLayout.setPadding(0, 10, 0, 10);
    delayCard.addView(delayLayout);
    final EditText etDelayValue = new EditText(ctx);
    etDelayValue.setHint("0");
    etDelayValue.setInputType(InputType.TYPE_CLASS_NUMBER);
    int delayValue = getInt(KEY_DELAY_VALUE, 0);
    int delayUnit = getInt(KEY_DELAY_UNIT, 0);
    etDelayValue.setText(String.valueOf(delayValue));
    etDelayValue.setGravity(Gravity.CENTER);
    delayLayout.addView(etDelayValue, new LinearLayout.LayoutParams(0, -2, 1));
    String[] delayUnits = {"毫秒", "秒"};
    final Spinner spDelayUnit = new Spinner(ctx);
    ArrayAdapter<String> delayUnitAdapter = new ArrayAdapter<>(ctx, android.R.layout.simple_spinner_dropdown_item, delayUnits);
    spDelayUnit.setAdapter(delayUnitAdapter);
    spDelayUnit.setSelection(delayUnit);
    delayLayout.addView(spDelayUnit, new LinearLayout.LayoutParams(-2, -2, 0));

    LinearLayout cardAdvanced = createCard(ctx);
    root.addView(cardAdvanced);
    addSectionTitle(ctx, cardAdvanced, "⚙️ 高级设置");
    final EditText etCheckTimes = addInput(ctx, cardAdvanced, "页面检查次数 (默认10次)", String.valueOf(getInt(KEY_CHECK_TIMES, 10)), InputType.TYPE_CLASS_NUMBER);

    LinearLayout cardReply = createCard(ctx);
    root.addView(cardReply);
    addSectionTitle(ctx, cardReply, "🤖 自动回复");
    final Switch swReply = addSwitch(ctx, cardReply, "抢到红包后自动回复", getBoolean(KEY_REPLY_ENABLE, false));
    final Switch swReplyRandom = addSwitch(ctx, cardReply, "随机延迟回复 (0-2秒)", getBoolean(KEY_REPLY_RANDOM, true));
    final Switch swReplyCustom = addSwitch(ctx, cardReply, "自定义延迟", getBoolean(KEY_REPLY_CUSTOM_ENABLE, false));

    LinearLayout replyDelayLayout = new LinearLayout(ctx);
    replyDelayLayout.setOrientation(LinearLayout.HORIZONTAL);
    replyDelayLayout.setPadding(0, 10, 0, 10);
    cardReply.addView(replyDelayLayout);
    final EditText etReplyDelayValue = new EditText(ctx);
    etReplyDelayValue.setHint("1");
    etReplyDelayValue.setInputType(InputType.TYPE_CLASS_NUMBER);
    int replyDelayValue = getInt(KEY_REPLY_DELAY_VALUE, 1);
    int replyDelayUnit = getInt(KEY_REPLY_DELAY_UNIT, 1);
    etReplyDelayValue.setText(String.valueOf(replyDelayValue));
    etReplyDelayValue.setGravity(Gravity.CENTER);
    replyDelayLayout.addView(etReplyDelayValue, new LinearLayout.LayoutParams(0, -2, 1));
    String[] replyDelayUnits = {"毫秒", "秒"};
    final Spinner spReplyDelayUnit = new Spinner(ctx);
    ArrayAdapter<String> replyDelayUnitAdapter = new ArrayAdapter<>(ctx, android.R.layout.simple_spinner_dropdown_item, replyDelayUnits);
    spReplyDelayUnit.setAdapter(replyDelayUnitAdapter);
    spReplyDelayUnit.setSelection(replyDelayUnit);
    replyDelayLayout.addView(spReplyDelayUnit, new LinearLayout.LayoutParams(-2, -2, 0));

    TextView tvReplyTip = new TextView(ctx);
    tvReplyTip.setText("💡 多个回复用 | 分隔，随机选择");
    tvReplyTip.setTextSize(12);
    tvReplyTip.setTextColor(Color.GRAY);
    tvReplyTip.setPadding(0, 5, 0, 5);
    cardReply.addView(tvReplyTip);

    String templates = getString(KEY_REPLY_TEMPLATES, "");
    if (TextUtils.isEmpty(templates)) {
        templates = getString(KEY_REPLY_TEXT, "谢谢老板");
    }
    final EditText etReplyText = addInput(ctx, cardReply, "回复内容", templates, InputType.TYPE_CLASS_TEXT);

    TextView tvReplySelfTip = new TextView(ctx);
    tvReplySelfTip.setText("💡 自己发的红包不会自动回复");
    tvReplySelfTip.setTextSize(12);
    tvReplySelfTip.setTextColor(Color.GRAY);
    tvReplySelfTip.setPadding(0, 5, 0, 10);
    cardReply.addView(tvReplySelfTip);

    LinearLayout cardList = createCard(ctx);
    root.addView(cardList);
    addSectionTitle(ctx, cardList, "📋 名单策略");
    String[] modes = {"抢所有红包 (默认)", "仅抢白名单", "拒抢黑名单"};
    final Spinner spMode = addSpinner(ctx, cardList, modes, getInt(KEY_MODE, 0));

    LinearLayout btnLayout = new LinearLayout(ctx);
    btnLayout.setOrientation(LinearLayout.HORIZONTAL);
    cardList.addView(btnLayout);
    Button btnWhite = createInlineButton(ctx, "管理白名单", "#4CAF50");
    Button btnBlack = createInlineButton(ctx, "管理黑名单", "#F44336");
    Button btnRefresh = createInlineButton(ctx, "刷新缓存", "#9E9E9E");
    btnLayout.addView(btnWhite, new LinearLayout.LayoutParams(0, -2, 1));
    btnLayout.addView(btnBlack, new LinearLayout.LayoutParams(0, -2, 1));
    btnLayout.addView(btnRefresh, new LinearLayout.LayoutParams(0, -2, 1));
    btnWhite.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            showContactSourceDialog(ctx, "白名单", KEY_WHITELIST);
        }
    });
    btnBlack.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            showContactSourceDialog(ctx, "黑名单", KEY_BLACKLIST);
        }
    });
    btnRefresh.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            sCachedFriendList = null;
            sCachedGroupList = null;
            logx(">> 手动清除缓存");
            toast("缓存已清除");
        }
    });

    LinearLayout cardKw = createCard(ctx);
    root.addView(cardKw);
    addSectionTitle(ctx, cardKw, "🔑 关键词过滤");
    String[] kwModes = {"不启用", "必须包含关键词", "包含则不抢"};
    final Spinner spKw = addSpinner(ctx, cardKw, kwModes, getInt(KEY_KW_MODE, 0));
    final EditText etKw = addInput(ctx, cardKw, "关键词(逗号分隔)", getString(KEY_KEYWORDS, ""), InputType.TYPE_CLASS_TEXT);

    AlertDialog d = new AlertDialog.Builder(ctx)
        .setTitle("红包设置")
        .setView(scrollView)
        .setPositiveButton("保存配置", null)
        .setNegativeButton("关闭", null)
        .setNeutralButton("重置统计", null)
        .create();
    setupUnifiedDialog(d);
    d.show();
    styleDialogButtons(d);

    d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            try {
                logx(">> 开始保存配置");
                putBoolean(KEY_ENABLE, swEnable.isChecked());
                putBoolean(KEY_SKIP_SELF, swSkipSelf.isChecked());
                putBoolean(KEY_AUTO_CLOSE, swAutoClose.isChecked());
                putBoolean(KEY_LOG_ENABLE, swLogEnable.isChecked());
                mLogEnabled = swLogEnable.isChecked();

                String delayValueStr = etDelayValue.getText().toString();
                int delayValueInt = delayValueStr.isEmpty() ? 0 : Integer.parseInt(delayValueStr);
                putInt(KEY_DELAY_VALUE, delayValueInt);
                putInt(KEY_DELAY_UNIT, spDelayUnit.getSelectedItemPosition());

                String checkStr = etCheckTimes.getText().toString();
                putInt(KEY_CHECK_TIMES, checkStr.isEmpty() ? 10 : Integer.parseInt(checkStr));

                putBoolean(KEY_REPLY_ENABLE, swReply.isChecked());
                putBoolean(KEY_REPLY_RANDOM, swReplyRandom.isChecked());
                putBoolean(KEY_REPLY_CUSTOM_ENABLE, swReplyCustom.isChecked());

                String replyDelayValueStr = etReplyDelayValue.getText().toString();
                int replyDelayValueInt = replyDelayValueStr.isEmpty() ? 1 : Integer.parseInt(replyDelayValueStr);
                putInt(KEY_REPLY_DELAY_VALUE, replyDelayValueInt);
                putInt(KEY_REPLY_DELAY_UNIT, spReplyDelayUnit.getSelectedItemPosition());

                String replyContent = etReplyText.getText().toString();
                putString(KEY_REPLY_TEMPLATES, replyContent);
                String[] replyArr = replyContent.split("\\|");
                String firstReply = replyArr.length > 0 ? replyArr[0].trim() : replyContent.trim();
                putString(KEY_REPLY_TEXT, firstReply);

                putInt(KEY_MODE, spMode.getSelectedItemPosition());
                putInt(KEY_KW_MODE, spKw.getSelectedItemPosition());
                putString(KEY_KEYWORDS, etKw.getText().toString());

                logx(">> 保存配置完成: enable=" + swEnable.isChecked() + ", skipSelf=" + swSkipSelf.isChecked() + ", autoClose=" + swAutoClose.isChecked() + ", logEnable=" + swLogEnable.isChecked() + ", mode=" + spMode.getSelectedItemPosition());
                toast("保存成功");
                d.dismiss();
            } catch (Exception e) {
                logx("ERROR: 保存配置失败: " + e.getMessage());
                toast("保存失败:" + e);
            }
        }
    });

    d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            new AlertDialog.Builder(ctx)
                .setTitle("确认重置")
                .setMessage("确定要清空所有统计数据吗？")
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        putInt(KEY_STATS_COUNT, 0);
                        putFloat(KEY_STATS_AMOUNT, 0f);
                        putInt(KEY_STATS_FAILED, 0);
                        putInt("hb_stats_today_count", 0);
                        putString(KEY_STATS_TODAY, "");
                        sProcessedRedBags.clear();
                        logx(">> 已重置统计信息");
                        toast("统计已重置");
                        d.dismiss();
                        showSettingsUI();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
        }
    });
}

void addStatsCard(Activity ctx, LinearLayout root) {
    LinearLayout cardStats = createCard(ctx);
    root.addView(cardStats);
    addSectionTitle(ctx, cardStats, "📊 统计信息");
    int totalCount = getInt(KEY_STATS_COUNT, 0);
    float totalAmount = getFloat(KEY_STATS_AMOUNT, 0f);
    int failedCount = getInt(KEY_STATS_FAILED, 0);
    int todayCount = getInt("hb_stats_today_count", 0);
    String amountStr = new java.text.DecimalFormat("0.00").format(totalAmount);
    
    TextView tvStats = new TextView(ctx);
    tvStats.setText(
        "✅ 成功抢到: " + totalCount + " 个\n" +
        "💰 累计金额: " + amountStr + " 元\n" +
        "❌ 未抢到: " + failedCount + " 个\n" +
        "📅 今日抢到: " + todayCount + " 个"
    );
    tvStats.setTextSize(14);
    tvStats.setLineSpacing(10, 1);
    cardStats.addView(tvStats);
}


LinearLayout createCard(Activity ctx) {
    LinearLayout card = new LinearLayout(ctx);
    card.setOrientation(LinearLayout.VERTICAL);
    GradientDrawable gd = new GradientDrawable();
    gd.setColor(Color.WHITE);
    gd.setCornerRadius(30);
    card.setBackground(gd);
    card.setPadding(40, 40, 40, 40);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.setMargins(0, 0, 0, 30);
    card.setLayoutParams(lp);
    card.setElevation(5f);
    return card;
}

void addSectionTitle(Activity ctx, LinearLayout parent, String title) {
    TextView tv = new TextView(ctx);
    tv.setText(title);
    tv.setTextSize(16);
    tv.setTextColor(Color.parseColor("#333333"));
    tv.getPaint().setFakeBoldText(true);
    tv.setPadding(0, 0, 0, 20);
    parent.addView(tv);
}

Switch addSwitch(Activity ctx, LinearLayout parent, String text, boolean checked) {
    Switch s = new Switch(ctx);
    s.setText(text);
    s.setChecked(checked);
    s.setPadding(0, 10, 0, 10);
    parent.addView(s);
    return s;
}

EditText addInput(Activity ctx, LinearLayout parent, String hint, String val, int type) {
    EditText et = new EditText(ctx);
    et.setHint(hint);
    et.setText(val);
    et.setInputType(type);
    GradientDrawable gd = new GradientDrawable();
    gd.setColor(Color.parseColor("#F5F5F5"));
    gd.setCornerRadius(15);
    et.setBackground(gd);
    et.setPadding(20, 20, 20, 20);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.setMargins(0, 10, 0, 20);
    et.setLayoutParams(lp);
    parent.addView(et);
    return et;
}

Spinner addSpinner(Activity ctx, LinearLayout parent, String[] items, int sel) {
    Spinner sp = new Spinner(ctx);
    ArrayAdapter<String> adp = new ArrayAdapter<>(ctx, android.R.layout.simple_spinner_dropdown_item, items);
    sp.setAdapter(adp);
    sp.setSelection(sel);
    parent.addView(sp);
    return sp;
}

Button createInlineButton(Activity ctx, String text, String colorHex) {
    Button btn = new Button(ctx);
    btn.setText(text);
    btn.setTextColor(Color.WHITE);
    btn.setTextSize(12);
    GradientDrawable gd = new GradientDrawable();
    gd.setColor(Color.parseColor(colorHex));
    gd.setCornerRadius(15);
    btn.setBackground(gd);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
    lp.setMargins(5, 10, 5, 10);
    btn.setLayoutParams(lp);
    return btn;
}

void setupUnifiedDialog(AlertDialog dialog) {
    try {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(40);
        bg.setColor(Color.parseColor("#F5F6F8"));
        dialog.getWindow().setBackgroundDrawable(bg);
    } catch (Exception e) {}
}

void styleDialogButtons(AlertDialog dialog) {
    try {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.parseColor("#2196F3"));
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.GRAY);
        if (dialog.getButton(AlertDialog.BUTTON_NEUTRAL) != null) {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(Color.parseColor("#FF5722"));
        }
    } catch (Exception e) {}
}

void showContactSourceDialog(final Activity ctx, final String title, final String saveKey) {
    String[] items = { "从好友列表选择", "从群聊列表选择", "手动输入微信ID", "查看当前名单" };
    AlertDialog d = new AlertDialog.Builder(ctx)
        .setTitle("选择添加方式")
        .setItems(items, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) loadAndSelect(ctx, title, saveKey, true);
                else if (which == 1) loadAndSelect(ctx, title, saveKey, false);
                else if (which == 2) showManualInput(ctx, title, saveKey);
                else if (which == 3) showCurrentList(ctx, title, saveKey);
            }
        }).create();
    setupUnifiedDialog(d);
    d.show();
}

void showManualInput(final Activity ctx, final String title, final String saveKey) {
    LinearLayout layout = new LinearLayout(ctx);
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setPadding(40, 20, 40, 20);
    TextView tip = new TextView(ctx);
    tip.setText("请输入微信ID，多个用逗号分隔");
    tip.setTextSize(12);
    tip.setTextColor(Color.GRAY);
    layout.addView(tip);
    final EditText etWxid = new EditText(ctx);
    etWxid.setHint("例如: wxid_abc123, wxid_def456");
    etWxid.setInputType(InputType.TYPE_CLASS_TEXT);
    layout.addView(etWxid);
    AlertDialog d = new AlertDialog.Builder(ctx)
        .setTitle(title + " - 手动输入")
        .setView(layout)
        .setPositiveButton("添加", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                String input = etWxid.getText().toString().trim();
                if (TextUtils.isEmpty(input)) {
                    toast("请输入微信ID");
                    return;
                }
                String existStr = getString(saveKey, "");
                Set<String> existSet = new HashSet<>();
                if (!TextUtils.isEmpty(existStr)) {
                    for (String s : existStr.split(",")) {
                        existSet.add(s.trim());
                    }
                }
                String[] newIds = input.split("[,，]");
                int addCount = 0;
                for (String id : newIds) {
                    String trimId = id.trim();
                    if (!TextUtils.isEmpty(trimId) && !existSet.contains(trimId)) {
                        existSet.add(trimId);
                        addCount++;
                    }
                }
                StringBuilder sb = new StringBuilder();
                for (String s : existSet) {
                    if (sb.length() > 0) sb.append(",");
                    sb.append(s);
                }
                putString(saveKey, sb.toString());
                logx(">> 手动添加名单成功, 新增数量: " + addCount + ", key=" + saveKey);
                toast("成功添加 " + addCount + " 个ID");
            }
        })
        .setNegativeButton("取消", null)
        .create();
    setupUnifiedDialog(d);
    d.show();
    styleDialogButtons(d);
}

void showCurrentList(final Activity ctx, final String title, final String saveKey) {
    String listStr = getString(saveKey, "");
    if (TextUtils.isEmpty(listStr)) {
        toast("当前名单为空");
        return;
    }
    final String[] ids = listStr.split(",");
    final List<String> displayList = new ArrayList<>();
    final List<String> idList = new ArrayList<>();
    for (String id : ids) {
        String trimId = id.trim();
        if (!TextUtils.isEmpty(trimId)) {
            String displayName = getDisplayName(trimId);
            displayList.add(displayName + "\n(" + trimId + ")");
            idList.add(trimId);
        }
    }
    if (displayList.isEmpty()) {
        toast("当前名单为空");
        return;
    }
    ScrollView sv = new ScrollView(ctx);
    LinearLayout layout = new LinearLayout(ctx);
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setPadding(20, 20, 20, 20);
    sv.addView(layout);
    TextView tvCount = new TextView(ctx);
    tvCount.setText("共 " + displayList.size() + " 个");
    tvCount.setTextSize(14);
    tvCount.setTextColor(Color.GRAY);
    tvCount.setPadding(0, 0, 0, 10);
    layout.addView(tvCount);
    final ListView lv = new ListView(ctx);
    lv.setLayoutParams(new LinearLayout.LayoutParams(-1, 800));
    ArrayAdapter<String> adapter = new ArrayAdapter<>(ctx, android.R.layout.simple_list_item_1, displayList);
    lv.setAdapter(adapter);
    layout.addView(lv);
    lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> parent, View view, final int position, long id) {
            new AlertDialog.Builder(ctx)
                .setTitle("删除确认")
                .setMessage("确定要删除 " + displayList.get(position) + " 吗？")
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        idList.remove(position);
                        StringBuilder sb = new StringBuilder();
                        for (String s : idList) {
                            if (sb.length() > 0) sb.append(",");
                            sb.append(s);
                        }
                        putString(saveKey, sb.toString());
                        logx(">> 删除名单项成功, key=" + saveKey + ", index=" + position);
                        toast("已删除");
                        showCurrentList(ctx, title, saveKey);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
        }
    });
    AlertDialog d = new AlertDialog.Builder(ctx)
        .setTitle(title + " - 当前名单")
        .setView(sv)
        .setPositiveButton("清空全部", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                new AlertDialog.Builder(ctx)
                    .setTitle("清空确认")
                    .setMessage("确定要清空所有名单吗？")
                    .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            putString(saveKey, "");
                            logx(">> 已清空名单, key=" + saveKey);
                            toast("已清空");
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
            }
        })
        .setNegativeButton("关闭", null)
        .create();
    setupUnifiedDialog(d);
    d.show();
    styleDialogButtons(d);
}

void loadAndSelect(final Activity ctx, final String title, final String saveKey, final boolean isFriend) {
    final ProgressDialog loading = new ProgressDialog(ctx);
    loading.setMessage("正在加载列表，请稍候...");
    loading.setCancelable(false);
    loading.show();
    new Thread(new Runnable() {
        public void run() {
            final List<String> names = new ArrayList<>();
            final List<String> ids = new ArrayList<>();
            try {
                if (isFriend) {
                    if (sCachedFriendList == null) sCachedFriendList = getFriendList();
                    if (sCachedFriendList != null) {
                        for (int i = 0; i < sCachedFriendList.size(); i++) {
                            FriendInfo f = (FriendInfo) sCachedFriendList.get(i);
                            if (f != null) {
                                String nickname = TextUtils.isEmpty(f.getNickname()) ? "未知昵称" : f.getNickname();
                                String remark = f.getRemark();
                                String name = !TextUtils.isEmpty(remark) ? nickname + " (" + remark + ")" : nickname;
                                String id = f.getWxid();
                                names.add(name);
                                ids.add(id);
                            }
                        }
                    }
                } else {
                    if (sCachedGroupList == null) sCachedGroupList = getGroupList();
                    if (sCachedGroupList != null) {
                        for (int i = 0; i < sCachedGroupList.size(); i++) {
                            GroupInfo g = (GroupInfo) sCachedGroupList.get(i);
                            if (g != null) {
                                String name = TextUtils.isEmpty(g.getName()) ? "未知群聊" : g.getName();
                                String id = g.getRoomId();
                                names.add(name);
                                ids.add(id);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logx("ERROR: 加载联系人/群列表失败: " + e.getMessage());
            } finally {
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    public void run() {
                        try {
                            if (loading.isShowing()) loading.dismiss();
                        } catch (Exception e) {}
                        if (names.isEmpty()) {
                            logx(">> 列表为空或加载失败");
                            toast("列表为空或加载失败！");
                        } else {
                            logx(">> 加载列表成功, 数量=" + names.size() + ", key=" + saveKey);
                            showMultiSelect(ctx, title + (isFriend ? "-好友" : "-群聊"), names, ids, saveKey);
                        }
                    }
                });
            }
        }
    }).start();
}

void showMultiSelect(Activity ctx, String title, final List<String> names, final List<String> ids, final String saveKey) {
    String existStr = getString(saveKey, "");
    final Set<String> selectedSet = new HashSet<>();
    if (!TextUtils.isEmpty(existStr)) {
        for (String s : existStr.split(",")) selectedSet.add(s.trim());
    }
    ScrollView sv = new ScrollView(ctx);
    LinearLayout layout = new LinearLayout(ctx);
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setPadding(20, 20, 20, 20);
    sv.addView(layout);
    final EditText etSearch = new EditText(ctx);
    etSearch.setHint("搜索...");
    GradientDrawable searchBg = new GradientDrawable();
    searchBg.setColor(Color.parseColor("#F5F5F5"));
    searchBg.setCornerRadius(15);
    etSearch.setBackground(searchBg);
    etSearch.setPadding(20, 20, 20, 20);
    layout.addView(etSearch);
    final TextView tvCount = new TextView(ctx);
    tvCount.setTextSize(12);
    tvCount.setTextColor(Color.GRAY);
    tvCount.setPadding(10, 10, 10, 10);
    layout.addView(tvCount);
    final ListView lv = new ListView(ctx);
    lv.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
    lv.setLayoutParams(new LinearLayout.LayoutParams(-1, 800));
    layout.addView(lv);
    final List<String> dNames = new ArrayList<>();
    final List<String> dIds = new ArrayList<>();
    final Set<String> tempSet = new HashSet<>(selectedSet);
    final Runnable refresh = new Runnable() {
        public void run() {
            String kw = etSearch.getText().toString().toLowerCase();
            dNames.clear();
            dIds.clear();
            for (int i = 0; i < names.size(); i++) {
                if (kw.isEmpty() || names.get(i).toLowerCase().contains(kw)) {
                    dNames.add(names.get(i));
                    dIds.add(ids.get(i));
                }
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(ctx, android.R.layout.simple_list_item_multiple_choice, dNames);
            lv.setAdapter(adapter);
            for (int i = 0; i < dIds.size(); i++) {
                if (tempSet.contains(dIds.get(i))) {
                    lv.setItemChecked(i, true);
                }
            }
            tvCount.setText("已选择: " + tempSet.size() + " 个 | 显示: " + dNames.size() + " 个");
        }
    };
    etSearch.addTextChangedListener(new TextWatcher() {
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        public void onTextChanged(CharSequence s, int start, int before, int count) {}
        public void afterTextChanged(Editable s) {
            refresh.run();
        }
    });
    lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
            String rid = dIds.get(pos);
            if (lv.isItemChecked(pos)) tempSet.add(rid);
            else tempSet.remove(rid);
            tvCount.setText("已选择: " + tempSet.size() + " 个 | 显示: " + dNames.size() + " 个");
        }
    });
    refresh.run();
    AlertDialog d = new AlertDialog.Builder(ctx)
        .setTitle(title)
        .setView(sv)
        .setPositiveButton("确定", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                StringBuilder sb = new StringBuilder();
                for (String s : tempSet) {
                    if (sb.length() > 0) sb.append(",");
                    sb.append(s);
                }
                putString(saveKey, sb.toString());
                logx(">> 名单更新成功, key=" + saveKey + ", 数量=" + tempSet.size());
                toast("名单更新: " + tempSet.size() + "个");
            }
        })
        .setNegativeButton("取消", null)
        .setNeutralButton("全选/反选", null)
        .create();
    setupUnifiedDialog(d);
    d.show();
    styleDialogButtons(d);
    d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            if (tempSet.size() == dIds.size()) {
                tempSet.clear();
                for (int i = 0; i < lv.getCount(); i++) {
                    lv.setItemChecked(i, false);
                }
            } else {
                tempSet.clear();
                tempSet.addAll(dIds);
                for (int i = 0; i < lv.getCount(); i++) {
                    lv.setItemChecked(i, true);
                }
            }
            tvCount.setText("已选择: " + tempSet.size() + " 个 | 显示: " + dNames.size() + " 个");
        }
    });
}

// ================= 辅助方法 =================
float getFloat(String key, float defValue) {
    try {
        String val = getString(key, String.valueOf(defValue));
        return Float.parseFloat(val);
    } catch (Exception e) {
        return defValue;
    }
}

void putFloat(String key, float value) {
    putString(key, String.valueOf(value));
}
