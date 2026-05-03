import android.app.*;
import android.content.*;
import android.content.res.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.media.*;
import android.net.*;
import android.os.*;
import android.text.*;
import android.util.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

boolean getHookState() {
    try {
        de.robv.android.xposed.XposedBridge.class;
        return true;
    } catch (Throwable e) {
        return false;
    }
}

if (!getHookState()) return toast("请关闭LSPosed调用保护");

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import me.hd.wauxv.data.bean.info.FriendInfo;
import me.hd.wauxv.data.bean.info.GroupInfo;

// ================= 配置常量 =================
String CFG_TARGETS = "jay_cfg_targets_v7";
String CFG_VIBRATE = "jay_cfg_vibrate_v7";
String CFG_SOUND = "jay_cfg_sound_v7";
String CFG_SHOW_DETAIL = "jay_cfg_detail_v7";
String CFG_MUTE_TIME_ENABLE = "jay_cfg_mute_time_enable_v7";
String CFG_MUTE_TIME_START = "jay_cfg_mute_time_start_v7";
String CFG_MUTE_TIME_END = "jay_cfg_mute_time_end_v7";
String CFG_BLOCK_AT_ALL = "jay_cfg_block_at_all_v7";
String CFG_BLOCK_AT_ME = "jay_cfg_block_at_me_v7";
String CFG_TALKER_CFG_PREFIX = "jay_talker_cfg_v9_";
String JAY_MARK = "is_jay_custom_mark_v7"; 

String ACTION_QUICK_REPLY = "jay_action_quick_reply_v7";
String EXTRA_TALKER = "jay_extra_talker_v7";
int REQ_PICK_RINGTONE_SYSTEM = 10086;
int REQ_PICK_RINGTONE_FILE = 10087;

// ================= 内存缓存 =================
boolean cacheVibrate = true;
boolean cacheSound = true;
boolean cacheShowDetail = true;
String currentChannelId = "jay_chn_init_v7";
boolean cacheMuteTimeEnable = false;
String cacheMuteTimeStart = "23:00";
String cacheMuteTimeEnd = "07:00";
boolean cacheBlockAtAll = false;
boolean cacheBlockAtMe = false;
Map cacheTalkerCfgMap = Collections.synchronizedMap(new HashMap());

// 【安全优化1】使用 Set 确保精确匹配 O(1) 查询，杜绝 contains 导致的相似字符误杀
Set cacheTargetSet = new HashSet();

List notifyUnhooks = Collections.synchronizedList(new ArrayList());
List resultUnhooks = Collections.synchronizedList(new ArrayList());
Pattern chatroomPattern = Pattern.compile("([A-Za-z0-9_\\-]+@chatroom)");
long lastManualRingAt = 0L;
BroadcastReceiver quickReplyReceiver = null;
boolean quickReplyReceiverRegistered = false;

Map sNoArgMethodCache = new ConcurrentHashMap();
Set sNoArgMethodMissCache = Collections.synchronizedSet(new HashSet());
Map sTitleNormCache = Collections.synchronizedMap(new HashMap());
int sTitleNormCacheMax = 256;
Map sGroupTitleTalkerCache = Collections.synchronizedMap(new HashMap());
long sGroupTitleTalkerCacheTtlMs = 45000L;
int sGroupTitleTalkerCacheMax = 128;
final Handler sMainHandler = new Handler(Looper.getMainLooper());
ExecutorService sQuickReplyExecutor = Executors.newSingleThreadExecutor();

// 全局联系人缓存 (提速)
List sCachedFriendNames = null;
List sCachedFriendIds = null;
List sCachedGroupNames = null;
List sCachedGroupIds = null;
long sLastWarmupAt = 0L;
boolean sNotifyHookInstalled = false;
boolean sActivityResultHookInstalled = false;
String sBootNonce = "";
long sLastAutoReloadAt = 0L;

// ================= 生命周期 =================
void warmupTalkerChannelsOnce() {
    try {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) hostContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (cacheTargetSet == null || cacheTargetSet.isEmpty()) return;

        Object[] ids = cacheTargetSet.toArray();
        for (int i = 0; i < ids.length; i++) {
            String talker = String.valueOf(ids[i]);
            if (TextUtils.isEmpty(talker)) continue;

            Map cfg = getTalkerCfg(talker);
            int talkerMode = cfgGetInt(cfg, "mode", 1);
            if (talkerMode == 0) continue;

            boolean talkerVibrate = cfgGetBool(cfg, "vibrate", cacheVibrate);
            boolean talkerSound = cfgGetBool(cfg, "sound", cacheSound);
            String talkerRing = cfgGet(cfg, "ringtone", "");
            talkerRing = TextUtils.isEmpty(talkerRing) ? "" : talkerRing;

            boolean useManualCustomSound = talkerSound && !TextUtils.isEmpty(talkerRing);
            String sTag = talkerSound ? (useManualCustomSound ? "M" : "S") : "N";
            String vTag = talkerVibrate ? "V" : "N";
            String talkerChannelId = "jay_chn_v9_" + sTag + "_" + vTag + "_" + talkerRing.hashCode();

            ensureNotifyChannel(nm, talkerChannelId, talkerVibrate, useManualCustomSound ? false : talkerSound, talkerRing);
        }
    } catch (Throwable ignored) {}
}

void onLoad() {
    try { sBootNonce = String.valueOf(System.currentTimeMillis()); } catch (Throwable ignored) { sBootNonce = "0"; }
    loadConfigToCache();
    hookSystemNotification();
    hookActivityResultForRingtone();
    registerQuickReplyReceiver();

    // 延后做名称预热，避开插件打开瞬间卡顿
    try {
        sMainHandler.postDelayed(new Runnable() {
            public void run() {
                try {
                    loadConfigToCache();
                } catch (Throwable ignored) {}
            }
        }, 600);
    } catch (Throwable ignored) {}

    // 延后预热已配置会话的通知通道（仅一次，避免重复重建引入竞态）
    try {
        sMainHandler.postDelayed(new Runnable() {
            public void run() {
                warmupTalkerChannelsOnce();
            }
        }, 1200);
    } catch (Throwable ignored) {}
}

void ensureConfigLoadedForRuntime() {
    try {
        if (!cacheTargetSet.isEmpty()) return;
        long now = System.currentTimeMillis();
        if (now - sLastAutoReloadAt < 1200L) return;
        sLastAutoReloadAt = now;
        loadConfigToCache();
    } catch (Throwable ignored) {}
}

void onUnload() {
    for (int i = 0; i < notifyUnhooks.size(); i++) {
        try {
            Object uh = notifyUnhooks.get(i);
            Method um = uh.getClass().getMethod("unhook", new Class[]{});
            um.invoke(uh, new Object[]{});
        } catch (Throwable ignored) {}
    }
    notifyUnhooks.clear();
    for (int i = 0; i < resultUnhooks.size(); i++) {
        try {
            Object uh = resultUnhooks.get(i);
            Method um = uh.getClass().getMethod("unhook", new Class[]{});
            um.invoke(uh, new Object[]{});
        } catch (Throwable ignored) {}
    }
    resultUnhooks.clear();
    
    sCachedFriendNames = null;
    sCachedFriendIds = null;
    sCachedGroupNames = null;
    sCachedGroupIds = null;
    sLastGroupCacheLoadAt = 0L;
    cacheTargetSet.clear();
    cacheTalkerCfgMap.clear();
    sNoArgMethodCache.clear();
    try { sNoArgMethodMissCache.clear(); } catch (Throwable ignored) {}
    try { sTitleNormCache.clear(); } catch (Throwable ignored) {}
    try { sGroupTitleTalkerCache.clear(); } catch (Throwable ignored) {}
    try {
        if (sQuickReplyExecutor != null) {
            sQuickReplyExecutor.shutdownNow();
        }
    } catch (Throwable ignored) {}
    sQuickReplyExecutor = Executors.newSingleThreadExecutor();
    globalRingtoneValueView = null;
    globalRingtoneValueRef = null;
    globalSettingActivity = null;
    unregisterQuickReplyReceiver();
}

boolean onClickSendBtn(String text) {
    if ("通知设置".equals(text)) {
        showSettingsUI();
        return true;
    }
    return false;
}

void loadConfigToCache() {
    String cacheTargetsStr = getString(CFG_TARGETS, "");
    cacheVibrate = getBoolean(CFG_VIBRATE, true);
    cacheSound = getBoolean(CFG_SOUND, true);
    cacheShowDetail = getBoolean(CFG_SHOW_DETAIL, true);
    cacheMuteTimeEnable = getBoolean(CFG_MUTE_TIME_ENABLE, false);
    cacheMuteTimeStart = normalizeTime(getString(CFG_MUTE_TIME_START, "23:00"), "23:00");
    cacheMuteTimeEnd = normalizeTime(getString(CFG_MUTE_TIME_END, "07:00"), "07:00");
    cacheBlockAtAll = getBoolean(CFG_BLOCK_AT_ALL, false);
    cacheBlockAtMe = getBoolean(CFG_BLOCK_AT_ME, false);
    
    cacheTargetSet.clear();
    if (!TextUtils.isEmpty(cacheTargetsStr)) {
        String[] parts = cacheTargetsStr.split(",");
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i].trim();
            if (!p.isEmpty()) cacheTargetSet.add(p);
        }
    }
    cacheTalkerCfgMap.clear();
    Object[] targetArr = cacheTargetSet.toArray();
    for (int i = 0; i < targetArr.length; i++) {
        String talkerId = String.valueOf(targetArr[i]);
        String rawCfg = getString(CFG_TALKER_CFG_PREFIX + talkerId, "");
        Map one = parseTalkerCfg(rawCfg);
        try {
            String oldRing = cfgGet(one, "ringtone", "");
            if (!TextUtils.isEmpty(oldRing)) {
                String newRing = freezeRingtoneUri(oldRing);
                if (!TextUtils.isEmpty(newRing) && !newRing.equals(oldRing)) {
                    one.put("ringtone", newRing);
                    putString(CFG_TALKER_CFG_PREFIX + talkerId, encodeTalkerCfg(one));
                }
            }
        } catch (Throwable ignored) {}
        cacheTalkerCfgMap.put(talkerId, one);
    }
    
    String sTag = cacheSound ? "S" : "N";
    String vTag = cacheVibrate ? "V" : "N";
    currentChannelId = "jay_chn_v9_" + sTag + "_" + vTag;
    rebuildNotificationChannel();
    
}

void registerQuickReplyReceiver() {
    try {
        if (quickReplyReceiverRegistered) return;
        quickReplyReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                try {
                    if (intent == null) return;
                    if (!ACTION_QUICK_REPLY.equals(intent.getAction())) return;
                    final String talker = intent.getStringExtra(EXTRA_TALKER);
                    if (TextUtils.isEmpty(talker)) return;
                    Bundle results = RemoteInput.getResultsFromIntent(intent);
                    CharSequence cs = null;
                    if (results != null) cs = results.getCharSequence("key_reply_content");
                    if (cs == null) cs = intent.getCharSequenceExtra("key_reply_content");
                    final String reply = cs == null ? "" : cs.toString().trim();
                    if (TextUtils.isEmpty(reply)) return;

                    // 异步发送，避免阻塞广播主线程导致卡顿或个别机型闪退
                    if (sQuickReplyExecutor == null || sQuickReplyExecutor.isShutdown()) {
                        sQuickReplyExecutor = Executors.newSingleThreadExecutor();
                    }
                    sQuickReplyExecutor.execute(new Runnable() {
                        public void run() {
                            try { sendText(talker, reply); } catch (Throwable ignored) {}
                            try {
                                NotificationManager nm = (NotificationManager) hostContext.getSystemService(Context.NOTIFICATION_SERVICE);
                                if (nm != null) nm.cancel(talker.hashCode());
                            } catch (Throwable ignored) {}
                        }
                    });
                } catch (Throwable ignored) {}
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_QUICK_REPLY);
        if (Build.VERSION.SDK_INT >= 33) {
            hostContext.registerReceiver(quickReplyReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            hostContext.registerReceiver(quickReplyReceiver, filter);
        }
        quickReplyReceiverRegistered = true;
    } catch (Throwable ignored) {}
}

void unregisterQuickReplyReceiver() {
    try {
        if (!quickReplyReceiverRegistered) return;
        if (quickReplyReceiver != null) hostContext.unregisterReceiver(quickReplyReceiver);
    } catch (Throwable ignored) {}
    quickReplyReceiver = null;
    quickReplyReceiverRegistered = false;
}

Map parseTalkerCfg(String raw) {
    Map m = new HashMap();
    if (TextUtils.isEmpty(raw)) return m;
    try {
        String[] rows = raw.split(";");
        for (int i = 0; i < rows.length; i++) {
            String one = rows[i];
            if (TextUtils.isEmpty(one)) continue;
            int p = one.indexOf("=");
            if (p <= 0 || p >= one.length() - 1) continue;
            String k = one.substring(0, p).trim();
            String v = one.substring(p + 1).trim();
            if (!TextUtils.isEmpty(k)) m.put(k, v);
        }
    } catch (Throwable ignored) {}
    return m;
}

String encodeTalkerCfg(Map m) {
    if (m == null || m.isEmpty()) return "";
    StringBuilder sb = new StringBuilder();
    Object[] keys = m.keySet().toArray();
    for (int i = 0; i < keys.length; i++) {
        String k = String.valueOf(keys[i]);
        String v = String.valueOf(m.get(keys[i]));
        if (TextUtils.isEmpty(k) || TextUtils.isEmpty(v)) continue;
        if (sb.length() > 0) sb.append(";");
        sb.append(k).append("=").append(v);
    }
    return sb.toString();
}

String cfgGet(Map m, String k, String def) {
    if (m == null || TextUtils.isEmpty(k)) return def;
    if (!m.containsKey(k)) return def;
    String v = String.valueOf(m.get(k));
    if (TextUtils.isEmpty(v)) return def;
    return v;
}

int cfgGetInt(Map m, String k, int def) {
    try {
        String v = cfgGet(m, k, "");
        if (TextUtils.isEmpty(v)) return def;
        return Integer.parseInt(v);
    } catch (Throwable e) {
        return def;
    }
}

boolean cfgGetBool(Map m, String k, boolean def) {
    String d = def ? "1" : "0";
    String v = cfgGet(m, k, d);
    return "1".equals(v) || "true".equalsIgnoreCase(v);
}

Map getTalkerCfg(String talker) {
    if (TextUtils.isEmpty(talker)) return new HashMap();
    if (cacheTalkerCfgMap.containsKey(talker)) return (Map) cacheTalkerCfgMap.get(talker);
    try {
        // 冷启动时兜底：按会话ID实时回源读取，避免必须进一次设置页才恢复单聊配置
        String rawCfg = getString(CFG_TALKER_CFG_PREFIX + talker, "");
        Map parsed = parseTalkerCfg(rawCfg);
        cacheTalkerCfgMap.put(talker, parsed);
        return parsed;
    } catch (Throwable ignored) {}
    return new HashMap();
}

void ensureTalkerCfgLoaded(String talker) {
    if (TextUtils.isEmpty(talker)) return;
    try {
        Map cfg = getTalkerCfg(talker);
        if (cfg == null || cfg.isEmpty()) {
            String rawCfg = getString(CFG_TALKER_CFG_PREFIX + talker, "");
            cacheTalkerCfgMap.put(talker, parseTalkerCfg(rawCfg));
        }
    } catch (Throwable ignored) {}
}

boolean isNowInMuteWindowByCfg(Map cfg) {
    boolean en = cfgGetBool(cfg, "muteEnable", cacheMuteTimeEnable);
    if (!en) return false;
    String st = normalizeTime(cfgGet(cfg, "muteStart", cacheMuteTimeStart), cacheMuteTimeStart);
    String et = normalizeTime(cfgGet(cfg, "muteEnd", cacheMuteTimeEnd), cacheMuteTimeEnd);
    int startMinute = parseTimeToMinute(st);
    int endMinute = parseTimeToMinute(et);
    if (startMinute < 0 || endMinute < 0) return false;
    Calendar cal = Calendar.getInstance();
    int now = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
    if (startMinute == endMinute) return true;
    if (startMinute < endMinute) {
        if (now >= startMinute) {
            if (now < endMinute) return true;
        }
        return false;
    }
    if (now >= startMinute) return true;
    if (now < endMinute) return true;
    return false;
}

int getInt(String key, int def) {
    try {
        String s = getString(key, "");
        if (TextUtils.isEmpty(s)) return def;
        return Integer.parseInt(s);
    } catch (Throwable e) { return def; }
}

int parseTimeToMinute(String hhmm) {
    if (TextUtils.isEmpty(hhmm)) return -1;
    try {
        String[] parts = hhmm.split(":");
        if (parts.length != 2) return -1;
        int h = Integer.parseInt(parts[0]);
        int m = Integer.parseInt(parts[1]);
        if (h < 0 || h > 23 || m < 0 || m > 59) return -1;
        return h * 60 + m;
    } catch (Throwable e) {
        return -1;
    }
}

String normalizeTime(String v, String def) {
    int t = parseTimeToMinute(v);
    if (t < 0) return def;
    int h = t / 60;
    int m = t % 60;
    return formatHHmm(h, m);
}

String twoDigits(int n) {
    if (n < 10) return "0" + n;
    return String.valueOf(n);
}

String formatHHmm(int h, int m) {
    if (h < 0) h = 0;
    if (h > 23) h = 23;
    if (m < 0) m = 0;
    if (m > 59) m = 59;
    return twoDigits(h) + ":" + twoDigits(m);
}

boolean asBool(Object v) {
    if (v == null) return false;
    try {
        if (v instanceof Boolean) return ((Boolean) v).booleanValue();
        if (v instanceof Number) return ((Number) v).intValue() != 0;
        String s = String.valueOf(v).trim();
        if (TextUtils.isEmpty(s)) return false;
        return "1".equals(s) || "true".equalsIgnoreCase(s) || "yes".equalsIgnoreCase(s);
    } catch (Throwable ignored) {}
    return false;
}

Object safeInvokeAny(Object obj, String[] methodNames) {
    if (obj == null || methodNames == null || methodNames.length == 0) return null;
    for (int i = 0; i < methodNames.length; i++) {
        try {
            Object v = safeInvoke(obj, methodNames[i]);
            if (v != null) return v;
        } catch (Throwable ignored) {}
    }
    return null;
}

Object safeGetFieldAny(Object obj, String[] fieldNames) {
    if (obj == null || fieldNames == null || fieldNames.length == 0) return null;
    for (int i = 0; i < fieldNames.length; i++) {
        String fn = fieldNames[i];
        if (TextUtils.isEmpty(fn)) continue;
        try {
            Object v = XposedHelpers.getObjectField(obj, fn);
            if (v != null) return v;
        } catch (Throwable ignored) {}
        try {
            java.lang.reflect.Field f = obj.getClass().getDeclaredField(fn);
            f.setAccessible(true);
            Object v2 = f.get(obj);
            if (v2 != null) return v2;
        } catch (Throwable ignored2) {}
    }
    return null;
}

boolean hitAtAllByText(String content) {
    if (TextUtils.isEmpty(content)) return false;
    String s = content;
    return s.contains("@所有人") || s.contains("＠所有人") || s.toLowerCase().contains("@all");
}

boolean hitAtMeByText(String content) {
    if (TextUtils.isEmpty(content)) return false;
    String s = content;
    return s.contains("@我") || s.contains("有人@我") || s.contains("提到了你") || s.contains("提及你");
}

Set parseMemberRuleSet(String raw) {
    Set out = new HashSet();
    if (TextUtils.isEmpty(raw)) return out;
    try {
        String norm = raw.replace('\n', ',').replace('，', ',').replace(';', ',').replace('；', ',');
        String[] parts = norm.split(",");
        for (int i = 0; i < parts.length; i++) {
            String one = parts[i] == null ? "" : parts[i].trim();
            if (!TextUtils.isEmpty(one)) out.add(one);
        }
    } catch (Throwable ignored) {}
    return out;
}

String joinMemberRuleSet(Set s) {
    if (s == null || s.isEmpty()) return "";
    StringBuilder sb = new StringBuilder();
    Object[] arr = s.toArray();
    for (int i = 0; i < arr.length; i++) {
        String v = String.valueOf(arr[i]).trim();
        if (TextUtils.isEmpty(v)) continue;
        if (sb.length() > 0) sb.append(",");
        sb.append(v);
    }
    return sb.toString();
}

String getMemberRuleSummary(String raw) {
    Set s = parseMemberRuleSet(raw);
    if (s.isEmpty()) return "未设置 >";
    return "已设置 " + s.size() + " 项 >";
}

String[] extractGroupSenderInfo(Object msg, String talker, String content) {
    String senderId = "";
    String senderName = "";
    String pureContent = TextUtils.isEmpty(content) ? "" : content;
    try {
        Object sid = safeInvokeAny(msg, new String[]{"getSendTalker", "getSenderUserName", "getSenderWxid", "getFromUser", "getFromUserName", "getRealChatUser", "getSenderId"});
        if (sid != null) senderId = String.valueOf(sid).trim();
    } catch (Throwable ignored) {}
    try {
        int p = pureContent.indexOf(":\n");
        if (p > 0) {
            String prefix = pureContent.substring(0, p).trim();
            if (TextUtils.isEmpty(senderId)) senderId = prefix;
            pureContent = pureContent.substring(p + 2);
        }
    } catch (Throwable ignored) {}
    try {
        if (!TextUtils.isEmpty(senderId)) {
            String n = "";
            try { n = getFriendName(senderId, talker); } catch (Throwable ignored) {}
            if (TextUtils.isEmpty(n)) {
                try { n = getFriendName(senderId); } catch (Throwable ignored2) {}
            }
            if (!TextUtils.isEmpty(n) && !senderId.equals(n)) senderName = n.trim();
        }
    } catch (Throwable ignored) {}
    if (TextUtils.isEmpty(senderName)) senderName = senderId;
    return new String[]{senderId, senderName, pureContent};
}

String extractTalkerFromOriginContent(String origin) {
    if (TextUtils.isEmpty(origin)) return "";
    try {
        String[] keys = new String[]{"talker", "fromusername", "fromUserName", "chatuser", "chatUser", "conversationId"};
        for (int i = 0; i < keys.length; i++) {
            String k = keys[i];
            int p = origin.indexOf(k + "=");
            if (p >= 0) {
                int st = p + k.length() + 1;
                int ed = st;
                while (ed < origin.length()) {
                    char c = origin.charAt(ed);
                    if (c == '&' || c == ';' || c == ',' || c == '\n' || c == '\r' || c == '\t' || c == ' ' || c == '\"') break;
                    ed++;
                }
                if (ed > st) return origin.substring(st, ed).trim();
            }
            p = origin.indexOf("\"" + k + "\"");
            if (p >= 0) {
                int c1 = origin.indexOf(":", p);
                if (c1 > 0) {
                    int q1 = origin.indexOf("\"", c1 + 1);
                    int q2 = q1 >= 0 ? origin.indexOf("\"", q1 + 1) : -1;
                    if (q1 >= 0 && q2 > q1) return origin.substring(q1 + 1, q2).trim();
                }
            }
        }
    } catch (Throwable ignored) {}
    try {
        Matcher m = chatroomPattern.matcher(origin);
        if (m.find()) return m.group(1);
    } catch (Throwable ignored) {}
    return "";
}

String extractContentFromOriginText(String origin) {
    if (TextUtils.isEmpty(origin)) return "";
    try {
        String[] keys = new String[]{"content", "msg", "msgContent", "message"};
        for (int i = 0; i < keys.length; i++) {
            String k = keys[i];
            int p = origin.indexOf(k + "=");
            if (p >= 0) {
                int st = p + k.length() + 1;
                int ed = st;
                while (ed < origin.length()) {
                    char c = origin.charAt(ed);
                    if (c == '&' || c == ';' || c == '\n' || c == '\r') break;
                    ed++;
                }
                if (ed > st) {
                    String v = origin.substring(st, ed).trim();
                    if (!TextUtils.isEmpty(v)) return v;
                }
            }
            p = origin.indexOf("\"" + k + "\"");
            if (p >= 0) {
                int c1 = origin.indexOf(":", p);
                if (c1 > 0) {
                    int q1 = origin.indexOf("\"", c1 + 1);
                    int q2 = q1 >= 0 ? origin.indexOf("\"", q1 + 1) : -1;
                    if (q1 >= 0 && q2 > q1) {
                        String v2 = origin.substring(q1 + 1, q2).trim();
                        if (!TextUtils.isEmpty(v2)) return v2;
                    }
                }
            }
        }
    } catch (Throwable ignored) {}
    return "";
}

String resolveMsgContentForNotify(Object msg, String content) {
    if (!TextUtils.isEmpty(content)) return content;
    String c = "";
    try {
        Object v = safeInvokeAny(msg, new String[]{"getContent", "getMsgContent", "getText", "getDigest"});
        if (v != null) c = String.valueOf(v).trim();
    } catch (Throwable ignored) {}
    if (!TextUtils.isEmpty(c)) return c;
    try {
        Object vf = safeGetFieldAny(msg, new String[]{"content", "msgContent", "message", "field_content"});
        if (vf != null) c = String.valueOf(vf).trim();
    } catch (Throwable ignored) {}
    if (!TextUtils.isEmpty(c)) return c;

    Object originObj = null;
    try {
        originObj = safeInvokeAny(msg, new String[]{"getOriginMsg", "getOriginMessage", "getOrigin", "getOriginContent"});
    } catch (Throwable ignored) {}
    if (originObj == null) {
        try { originObj = safeGetFieldAny(msg, new String[]{"originMsg", "originMessage", "origin", "originContent"}); } catch (Throwable ignored) {}
    }
    if (originObj == null) return "";

    try {
        Object v2 = safeInvokeAny(originObj, new String[]{"getContent", "getMsgContent", "getText", "getDigest"});
        if (v2 != null) c = String.valueOf(v2).trim();
    } catch (Throwable ignored) {}
    if (TextUtils.isEmpty(c)) {
        try {
            Object vf2 = safeGetFieldAny(originObj, new String[]{"content", "msgContent", "message", "field_content"});
            if (vf2 != null) c = String.valueOf(vf2).trim();
        } catch (Throwable ignored) {}
    }
    if (!TextUtils.isEmpty(c)) return c;
    try {
        return extractContentFromOriginText(String.valueOf(originObj));
    } catch (Throwable ignored) {}
    return "";
}

String resolveTalkerForMsg(Object msg, String content) {
    String talker = "";
    try {
        Object t = safeInvokeAny(msg, new String[]{"getTalker", "getUsername", "getUserName", "getConversationId", "getChatUser", "getSessionId"});
        if (t != null) talker = String.valueOf(t).trim();
    } catch (Throwable ignored) {}
    if (TextUtils.isEmpty(talker)) {
        try {
            Object tf = safeGetFieldAny(msg, new String[]{"talker", "username", "userName", "conversationId", "chatUser", "sessionId", "fromUserName"});
            if (tf != null) talker = String.valueOf(tf).trim();
        } catch (Throwable ignored) {}
    }
    if (!TextUtils.isEmpty(talker)) return talker;

    Object originObj = null;
    try {
        originObj = safeInvokeAny(msg, new String[]{"getOriginMsg", "getOriginMessage", "getOrigin", "getOriginContent"});
    } catch (Throwable ignored) {}
    if (originObj == null) {
        try {
            originObj = safeGetFieldAny(msg, new String[]{"originMsg", "originMessage", "origin", "originContent"});
        } catch (Throwable ignored) {}
    }

    if (originObj != null) {
        if (originObj instanceof CharSequence) {
            String fromText = extractTalkerFromOriginContent(String.valueOf(originObj));
            if (!TextUtils.isEmpty(fromText)) return fromText;
        } else {
            try {
                Object t2 = safeInvokeAny(originObj, new String[]{"getTalker", "getUsername", "getUserName", "getConversationId", "getChatUser", "getSessionId", "getFromUserName"});
                if (t2 != null) talker = String.valueOf(t2).trim();
            } catch (Throwable ignored) {}
            if (TextUtils.isEmpty(talker)) {
                try {
                    Object tf2 = safeGetFieldAny(originObj, new String[]{"talker", "username", "userName", "conversationId", "chatUser", "sessionId", "fromUserName"});
                    if (tf2 != null) talker = String.valueOf(tf2).trim();
                } catch (Throwable ignored) {}
            }
            if (!TextUtils.isEmpty(talker)) return talker;
            try {
                String fromObjText = extractTalkerFromOriginContent(String.valueOf(originObj));
                if (!TextUtils.isEmpty(fromObjText)) return fromObjText;
            } catch (Throwable ignored) {}
        }
    }

    String fromContent = extractTalkerFromOriginContent(content);
    if (!TextUtils.isEmpty(fromContent)) return fromContent;
    return "";
}

boolean memberRuleMatched(String rawRule, String senderId, String senderName, String pureContent) {
    Set s = parseMemberRuleSet(rawRule);
    if (s.isEmpty()) return false;
    String sid = TextUtils.isEmpty(senderId) ? "" : senderId.toLowerCase();
    String sn = TextUtils.isEmpty(senderName) ? "" : senderName.toLowerCase();
    String sc = TextUtils.isEmpty(pureContent) ? "" : pureContent.toLowerCase();
    Object[] arr = s.toArray();
    for (int i = 0; i < arr.length; i++) {
        String key = String.valueOf(arr[i]).trim();
        if (TextUtils.isEmpty(key)) continue;
        String lk = key.toLowerCase();
        if (!TextUtils.isEmpty(sid) && (sid.equals(lk) || sid.contains(lk) || lk.contains(sid))) return true;
        if (!TextUtils.isEmpty(sn) && (sn.equals(lk) || sn.contains(lk) || lk.contains(sn))) return true;
        if (!TextUtils.isEmpty(sc) && (sc.startsWith(lk + ":") || sc.startsWith(lk + "："))) return true;
    }
    return false;
}

boolean shouldSuppressByRules(Object msg, String talker, String content, Map cfg) {
    try {
        boolean isGroupChat = !TextUtils.isEmpty(talker) && talker.endsWith("@chatroom");
        if (!isGroupChat) {
            Object isGroupObj = safeInvokeAny(msg, new String[]{"isGroupChat", "isChatRoom", "isChatroom", "isGroup"});
            isGroupChat = asBool(isGroupObj);
        }

        boolean blockAtAll = cfgGetBool(cfg, "blockAll", cacheBlockAtAll);
        boolean blockAtMe = cfgGetBool(cfg, "blockMe", cacheBlockAtMe);
        String onlyMembers = cfgGet(cfg, "onlyMembers", "");
        String blockMembers = cfgGet(cfg, "blockMembers", "");

        if (isGroupChat && (!TextUtils.isEmpty(onlyMembers) || !TextUtils.isEmpty(blockMembers))) {
            String[] sender = extractGroupSenderInfo(msg, talker, content);
            String sid = sender[0];
            String sname = sender[1];
            String pure = sender[2];
            if (!TextUtils.isEmpty(onlyMembers) && !memberRuleMatched(onlyMembers, sid, sname, pure)) return true;
            if (!TextUtils.isEmpty(blockMembers) && memberRuleMatched(blockMembers, sid, sname, pure)) return true;
        }

        if (isGroupChat && blockAtAll) {
            Object atAllObj = safeInvokeAny(msg, new String[]{"isNotifyAll", "isAnnounceAll", "isAtAll", "isAtEveryone", "hasAtAll"});
            if (asBool(atAllObj) || hitAtAllByText(content)) return true;
        }

        if (isGroupChat && blockAtMe) {
            Object atMeObj = safeInvokeAny(msg, new String[]{"isAtMe", "isAtMeFromGroup", "isMentioned", "hasAtMe", "needNotifyMe"});
            if (asBool(atMeObj) || hitAtMeByText(content)) return true;
        }
    } catch (Throwable ignored) {}
    return false;
}

String getRingtoneDisplayName(Context ctx, String uriStr) {
    if (TextUtils.isEmpty(uriStr)) return "跟随系统";
    try {
        Uri uri = Uri.parse(uriStr);
        android.media.Ringtone rt = RingtoneManager.getRingtone(ctx, uri);
        if (rt != null) {
            String title = rt.getTitle(ctx);
            if (!TextUtils.isEmpty(title)) {
                // 有些文档 Uri 会返回 primary:... 这类路径串，优先转成文件名显示
                if (title.contains(":") || title.contains("/")) {
                    String fallback = prettyAudioNameFromUri(uri);
                    if (!TextUtils.isEmpty(fallback)) return fallback;
                }
                return title;
            }
        }
        String fallback = prettyAudioNameFromUri(uri);
        if (!TextUtils.isEmpty(fallback)) return fallback;
    } catch (Throwable ignored) {}
    return "自定义铃声";
}

String prettyAudioNameFromUri(Uri uri) {
    if (uri == null) return "";
    try {
        String s = uri.toString();
        String name = "";
        String seg = uri.getLastPathSegment();
        if (!TextUtils.isEmpty(seg)) name = seg;
        if (TextUtils.isEmpty(name) && !TextUtils.isEmpty(s)) {
            int q = s.indexOf("?");
            String pure = q >= 0 ? s.substring(0, q) : s;
            int p = pure.lastIndexOf("/");
            if (p >= 0 && p < pure.length() - 1) name = pure.substring(p + 1);
        }
        if (TextUtils.isEmpty(name)) return "";
        try { name = Uri.decode(name); } catch (Throwable ignored) {}
        int c = name.lastIndexOf(":");
        if (c >= 0 && c < name.length() - 1) name = name.substring(c + 1);
        int s1 = name.lastIndexOf("/");
        if (s1 >= 0 && s1 < name.length() - 1) name = name.substring(s1 + 1);
        name = name.trim();
        if (TextUtils.isEmpty(name)) return "";
        if (name.length() > 42) name = name.substring(0, 42) + "...";
        return name;
    } catch (Throwable ignored) {}
    return "";
}

String freezeRingtoneUri(String rawUri) {
    if (TextUtils.isEmpty(rawUri)) return "";
    try {
        Uri src = Uri.parse(rawUri);
        if (src == null) return rawUri;
        String scheme = src.getScheme();
        if (TextUtils.isEmpty(scheme)) return rawUri;
        if ("file".equalsIgnoreCase(scheme)) return rawUri;
        if (!"content".equalsIgnoreCase(scheme)) return rawUri;
        if (hostContext == null) return rawUri;

        java.io.InputStream in = null;
        java.io.FileOutputStream out = null;
        try {
            in = hostContext.getContentResolver().openInputStream(src);
            if (in == null) return rawUri;

            java.io.File dir = new java.io.File("/storage/emulated/0/Android/media/com.tencent.mm/WAuxiliary/Plugin/ringtones");
            if (!dir.exists()) dir.mkdirs();

            String base = prettyAudioNameFromUri(src);
            if (TextUtils.isEmpty(base)) base = "ringtone_" + System.currentTimeMillis();
            base = base.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
            if (TextUtils.isEmpty(base)) base = "ringtone_" + System.currentTimeMillis();
            String low = base.toLowerCase();
            if (!(low.endsWith(".mp3") || low.endsWith(".m4a") || low.endsWith(".aac") || low.endsWith(".wav") || low.endsWith(".ogg") || low.endsWith(".flac"))) {
                base = base + ".mp3";
            }

            java.io.File dst = new java.io.File(dir, base);
            out = new java.io.FileOutputStream(dst, false);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.flush();
            return Uri.fromFile(dst).toString();
        } finally {
            try { if (in != null) in.close(); } catch (Throwable ignored) {}
            try { if (out != null) out.close(); } catch (Throwable ignored) {}
        }
    } catch (Throwable ignored) {}
    return rawUri;
}

boolean shouldBlockNativeByCfg(Map cfg) {
    int talkerMode = cfgGetInt(cfg, "mode", 1);
    if (talkerMode == 0) return true;
    if (isNowInMuteWindowByCfg(cfg)) return true;
    // 只要配置了群规则，就走插件链路做逐条判定，避免原生通知绕过规则
    boolean blockAtAll = cfgGetBool(cfg, "blockAll", cacheBlockAtAll);
    boolean blockAtMe = cfgGetBool(cfg, "blockMe", cacheBlockAtMe);
    String onlyMembers = cfgGet(cfg, "onlyMembers", "");
    String blockMembers = cfgGet(cfg, "blockMembers", "");
    if (blockAtAll || blockAtMe || !TextUtils.isEmpty(onlyMembers) || !TextUtils.isEmpty(blockMembers)) return true;

    boolean showDetail = cfgGetBool(cfg, "showDetail", cacheShowDetail);
    boolean quickReply = cfgGetBool(cfg, "quickReply", false);
    // showDetail=开 且 quickReply=关 -> 保留原生；其余接管原生
    return (!showDetail) || quickReply;
}

// ================= 核心 1：原生通知强力拦截器 =================
void hookSystemNotification() {
    try {
        if (sNotifyHookInstalled) return;
        sNotifyHookInstalled = true;

        Method[] methods = NotificationManager.class.getDeclaredMethods();
        for (int i = 0; i < methods.length; i++) {
            final Method m = methods[i];
            if (!"notify".equals(m.getName())) continue;

            Class[] ps = m.getParameterTypes();
            if (ps == null || ps.length == 0 || ps[ps.length - 1] != Notification.class) continue;

            Object unhook = XposedBridge.hookMethod(m, new XC_MethodHook() {
                protected void beforeHookedMethod(de.robv.android.xposed.XC_MethodHook.MethodHookParam param) throws Throwable {
                    try {
                        ensureConfigLoadedForRuntime();
                        if (cacheTargetSet.isEmpty()) return;

                        Object[] args = param.args;
                        if (args == null || args.length == 0) return;
                        Notification n = (Notification) args[args.length - 1];
                        if (n == null) return;

                        if (n.extras != null && n.extras.getBoolean(JAY_MARK, false)) {
                            return;
                        }
                        if (Build.VERSION.SDK_INT >= 26 && n.getChannelId() != null && n.getChannelId().startsWith("jay_chn_v9_")) {
                            return;
                        }
                        String talker = extractTalkerFromNotification(n);
                        boolean shouldBlock = false;

                        if (!TextUtils.isEmpty(talker) && cacheTargetSet.contains(talker)) {
                            Map cfg0 = getTalkerCfg(talker);
                            shouldBlock = shouldBlockNativeByCfg(cfg0);
                        }
                        if (!shouldBlock && TextUtils.isEmpty(talker) && n.extras != null) {
                            String title = "";
                            try {
                                CharSequence cs = n.extras.getCharSequence(Notification.EXTRA_TITLE);
                                if (cs != null) title = cs.toString().trim();
                            } catch (Throwable ignored) {}
                            String titleTalker = findTargetTalkerByTitle(title);
                            if (TextUtils.isEmpty(titleTalker)) {
                                try { titleTalker = findTalkerByGroupTitle(title); } catch (Throwable ignored) {}
                            }
                            if (!TextUtils.isEmpty(titleTalker) && cacheTargetSet.contains(titleTalker)) {
                                Map cfg0 = getTalkerCfg(titleTalker);
                                shouldBlock = shouldBlockNativeByCfg(cfg0);
                            }
                        }

                        if (shouldBlock) {
                            param.setResult(null);
                        }

                    } catch (Throwable e) {
                    }
                }
            });
            if (unhook != null) notifyUnhooks.add(unhook);
        }
    } catch (Throwable ignored) {
        sNotifyHookInstalled = false;
    }
}

// ================= 核心 2：自定义通知发送器 =================
void onHandleMsg(Object msg) {
    ensureConfigLoadedForRuntime();
    if (cacheTargetSet.isEmpty()) {
        return;
    }

    try {
        boolean isSend = (boolean) safeInvoke(msg, "isSend");
        if (isSend) {
            return;
        }

        String talker = resolveTalkerForMsg(msg, "");
        String content = (String) safeInvoke(msg, "getContent");
        content = resolveMsgContentForNotify(msg, content);
        if (TextUtils.isEmpty(talker)) talker = resolveTalkerForMsg(msg, content);
        Object typeObj = safeInvoke(msg, "getType");
        int type = (typeObj instanceof Number) ? ((Number) typeObj).intValue() : 1;

        // 【逻辑优化】使用 Set 的精确匹配
        if (!cacheTargetSet.contains(talker)) {
            return;
        }

        Map cfg = getTalkerCfg(talker);
        ensureTalkerCfgLoaded(talker);
        cfg = getTalkerCfg(talker);
        int talkerMode = cfgGetInt(cfg, "mode", 1);
        boolean inMuteWindow = isNowInMuteWindowByCfg(cfg);
        boolean showDetail = cfgGetBool(cfg, "showDetail", cacheShowDetail);
        boolean talkerVibrate = cfgGetBool(cfg, "vibrate", cacheVibrate);
        boolean talkerSound = cfgGetBool(cfg, "sound", cacheSound);
        String talkerRingtone = cfgGet(cfg, "ringtone", "");
        boolean talkerQuickReply = cfgGetBool(cfg, "quickReply", false);
        boolean blockNative = shouldBlockNativeByCfg(cfg);

        if (talkerMode == 0) {
            return;
        }
        if (inMuteWindow) {
            return;
        }
        if (showDetail && !talkerQuickReply && !blockNative) {
            return;
        }

        boolean suppressedByRules = shouldSuppressByRules(msg, talker, content, cfg);
        if (suppressedByRules) {
            return;
        }

        boolean isGroupChat = !TextUtils.isEmpty(talker) && talker.endsWith("@chatroom");
        if (!isGroupChat) {
            Object g = safeInvokeAny(msg, new String[]{"isGroupChat", "isChatroom", "isChatRoom", "isGroup"});
            isGroupChat = asBool(g);
        }

        String notifyTitle = resolveTalkerNameForMatch(talker);
        if (TextUtils.isEmpty(notifyTitle)) notifyTitle = getFriendName(talker);
        if (TextUtils.isEmpty(notifyTitle)) notifyTitle = talker;

        String displayContent = "[收到一条新消息]";
        if (showDetail) {
            if (type == 1) {
                if (isGroupChat) {
                    String[] sender = extractGroupSenderInfo(msg, talker, content);
                    String sid = sender[0];
                    String sname = sender[1];
                    String pure = sender[2];
                    String senderLabel = !TextUtils.isEmpty(sname) ? sname : sid;
                    String groupName = resolveTalkerNameForMatch(talker);
                    if (TextUtils.isEmpty(groupName)) groupName = getFriendName(talker);
                    if (TextUtils.isEmpty(groupName)) groupName = talker;
                    notifyTitle = groupName;
                    if (TextUtils.isEmpty(pure)) displayContent = "[收到一条新消息]";
                    else if (TextUtils.isEmpty(senderLabel)) displayContent = pure;
                    else displayContent = senderLabel + ": " + pure;
                } else {
                    displayContent = TextUtils.isEmpty(content) ? "[收到一条新消息]" : content;
                }
            } else {
                displayContent = "[图片/语音或非文本消息]";
            }
        }

        sendCustomNotification(talker, notifyTitle, displayContent, talkerVibrate, talkerSound, talkerRingtone, talkerQuickReply);
    } catch (Throwable e) {
    }
}

void rebuildNotificationChannel() {
    if (Build.VERSION.SDK_INT >= 26) {
        try {
            NotificationManager nm = (NotificationManager) hostContext.getSystemService(Context.NOTIFICATION_SERVICE);
            
            // 【安全优化3】清道夫机制：清理之前遗留的过期通道，保持系统整洁，防堆积崩溃
            try {
                List channels = (List) nm.getClass().getMethod("getNotificationChannels").invoke(nm);
                if (channels != null) {
                    for (int i = 0; i < channels.size(); i++) {
                        NotificationChannel ch = (NotificationChannel) channels.get(i);
                        String chId = ch.getId();
                        // 只清理 v7 和 v8 的历史遗留通道，不碰 v9 的现役通道
if (chId != null && (chId.startsWith("jay_chn_v7") || chId.startsWith("jay_chn_v8"))) {
    nm.deleteNotificationChannel(chId);
}
                    }
                }
            } catch (Throwable ignored) {}

            // 额外清理：限制 v9 通道总量，避免长期堆积导致系统通知管理变慢
            try {
                List channels2 = (List) nm.getClass().getMethod("getNotificationChannels").invoke(nm);
                if (channels2 != null && channels2.size() > 0) {
                    List v9Ids = new ArrayList();
                    for (int i = 0; i < channels2.size(); i++) {
                        NotificationChannel ch2 = (NotificationChannel) channels2.get(i);
                        if (ch2 == null) continue;
                        String id2 = ch2.getId();
                        if (id2 != null && id2.startsWith("jay_chn_v9_")) v9Ids.add(id2);
                    }
                    int keepMax = 100;
                    int extra = v9Ids.size() - keepMax;
                    if (extra > 0) {
                        for (int i = 0; i < extra; i++) {
                            try { nm.deleteNotificationChannel(String.valueOf(v9Ids.get(i))); } catch (Throwable ignored) {}
                        }
                    }
                }
            } catch (Throwable ignored) {}

            ensureNotifyChannel(nm, currentChannelId, cacheVibrate, cacheSound, "");
        } catch (Throwable ignored) {}
    }
}

android.media.AudioAttributes buildNotifyAudioAttrs() {
    try {
        return new android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
    } catch (Throwable ignored) {
        return Notification.AUDIO_ATTRIBUTES_DEFAULT;
    }
}

Uri resolveNotifySoundUri(boolean useSound, String ring) {
    if (!useSound) return null;
    if (!TextUtils.isEmpty(ring)) {
        try { return Uri.parse(ring); } catch (Throwable ignored) {}
    }
    return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
}

void ensureNotifyChannel(NotificationManager nm, String channelId, boolean useVibrate, boolean useSound, String ring) {
    if (nm == null || Build.VERSION.SDK_INT < 26 || TextUtils.isEmpty(channelId)) return;
    Uri targetSound = resolveNotifySoundUri(useSound, ring);
    boolean needCreate = true;
    try {
        NotificationChannel old = nm.getNotificationChannel(channelId);
        if (old != null) {
            boolean vibOk = old.shouldVibrate() == useVibrate;
            Uri oldSound = old.getSound();
            boolean soundOk = (!useSound && oldSound == null)
                    || (useSound && oldSound != null && oldSound.toString().equals(String.valueOf(targetSound)));
            boolean impOk = old.getImportance() >= NotificationManager.IMPORTANCE_HIGH;
            if (vibOk && soundOk && impOk) {
                needCreate = false;
            } else {
                nm.deleteNotificationChannel(channelId);
            }
        }
    } catch (Throwable ignored) {}

    if (!needCreate) return;
    try {
        NotificationChannel c = new NotificationChannel(channelId, "接管控制通知", NotificationManager.IMPORTANCE_HIGH);
        if (useVibrate) {
            c.enableVibration(true);
            c.setVibrationPattern(new long[]{0, 250, 250, 250});
        } else {
            c.enableVibration(false);
            c.setVibrationPattern(new long[]{0});
        }
        if (useSound) c.setSound(targetSound, buildNotifyAudioAttrs());
        else c.setSound(null, null);
        nm.createNotificationChannel(c);
    } catch (Throwable ignored) {}
}

void playCustomRingtoneFallback(final String uriStr) {
    if (TextUtils.isEmpty(uriStr)) return;
    try {
        long now = System.currentTimeMillis();
        if (now - lastManualRingAt < 1200) return;
        lastManualRingAt = now;
    } catch (Throwable ignored) {}

    sMainHandler.post(new Runnable() {
        public void run() {
            try {
                Uri uri = Uri.parse(uriStr);
                final android.media.Ringtone rt = RingtoneManager.getRingtone(hostContext, uri);
                if (rt == null) return;
                try { rt.setStreamType(android.media.AudioManager.STREAM_NOTIFICATION); } catch (Throwable ignored) {}
                rt.play();
                sMainHandler.postDelayed(new Runnable() {
                    public void run() {
                        try {
                            if (rt.isPlaying()) rt.stop();
                        } catch (Throwable ignored) {}
                    }
                }, 3500);
            } catch (Throwable ignored) {}
        }
    });
}

void playNotifySoundByConfig(boolean useSound, String ringtoneUri) {
    if (!useSound) return;
    String ring = TextUtils.isEmpty(ringtoneUri) ? "" : ringtoneUri;
    if (!TextUtils.isEmpty(ring)) {
        playCustomRingtoneFallback(ring);
        return;
    }
    try {
        Uri def = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        if (def != null) playCustomRingtoneFallback(def.toString());
    } catch (Throwable ignored) {}
}

boolean ensureUriReadable(String uriStr) {
    if (TextUtils.isEmpty(uriStr)) return false;
    try {
        Uri u = Uri.parse(uriStr);
        if (u == null) return false;
        String sch = u.getScheme();
        if (TextUtils.isEmpty(sch)) return false;
        if ("file".equalsIgnoreCase(sch)) return true;
        if (!"content".equalsIgnoreCase(sch)) return true;

        if (hostContext == null) return false;
        try {
            if (hostContext.getContentResolver().openInputStream(u) == null) return false;
            return true;
        } catch (Throwable ignored3) {
            return false;
        }
    } catch (Throwable ignored) {}
    return false;
}

Intent[] buildChatOpenIntents(String talker) {
    Intent home = null;
    Intent chat = null;
    try {
        home = new Intent();
        home.setComponent(new ComponentName(hostContext.getPackageName(), "com.tencent.mm.ui.LauncherUI"));
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    } catch (Throwable ignored) {}
    if (home == null) {
        try {
            home = hostContext.getPackageManager().getLaunchIntentForPackage(hostContext.getPackageName());
            if (home != null) {
                home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            }
        } catch (Throwable ignored) {}
    }
    try {
        chat = new Intent();
        chat.setComponent(new ComponentName(hostContext.getPackageName(), "com.tencent.mm.ui.chatting.ChattingUI"));
        chat.putExtra("Chat_User", talker);
        chat.putExtra("Chat_Mode", 1);
        chat.putExtra("finish_direct", true);
        chat.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    } catch (Throwable ignored) {}

    if (home != null && chat != null) return new Intent[]{home, chat};
    if (chat != null) return new Intent[]{chat};
    if (home != null) return new Intent[]{home};
    return null;
}

void sendCustomNotification(String talker, String title, String text, boolean useVibrate, boolean useSound, String ringtoneUri, boolean enableQuickReply) {
    try {
        NotificationManager nm = (NotificationManager) hostContext.getSystemService(Context.NOTIFICATION_SERVICE);
        Notification.Builder builder;
        String talkerChannelId = currentChannelId;
        String talkerRing = TextUtils.isEmpty(ringtoneUri) ? "" : ringtoneUri;
        boolean useManualCustomSound = Build.VERSION.SDK_INT >= 26 && useSound; // 8+ 一律手动播放，规避ROM回写通道声音
        if (Build.VERSION.SDK_INT >= 26) {
            String sTag = useSound ? "M" : "N";
            String vTag = useVibrate ? "V" : "N";
            talkerChannelId = "jay_chn_v9_" + sTag + "_" + vTag + "_" + talkerRing.hashCode();
            // 8+ 通道保持静音，声音由插件手动播放，避免被系统/ROM改回默认声
            ensureNotifyChannel(nm, talkerChannelId, useVibrate, false, talkerRing);
            builder = new Notification.Builder(hostContext, talkerChannelId);
        } else {
            builder = new Notification.Builder(hostContext);
        }

        builder.setContentTitle(title)
               .setContentText(text)
               .setSmallIcon(android.R.drawable.stat_notify_chat)
               .setAutoCancel(true)
               .setOnlyAlertOnce(false);

        try { builder.setCategory(Notification.CATEGORY_MESSAGE); } catch (Throwable ignored) {}
        try { builder.setVisibility(Notification.VISIBILITY_PRIVATE); } catch (Throwable ignored) {}

        long[] vibPattern = useVibrate ? new long[]{0, 250, 250, 250} : new long[]{0};
        try { builder.setVibrate(vibPattern); } catch (Throwable ignored) {}

        Uri soundUri = resolveNotifySoundUri(Build.VERSION.SDK_INT >= 26 ? false : (useManualCustomSound ? false : useSound), talkerRing);
        try { builder.setSound(soundUri); } catch (Throwable ignored) {}

        Bundle extras = new Bundle();
        extras.putBoolean(JAY_MARK, true);
        builder.setExtras(extras);

        if (Build.VERSION.SDK_INT < 26) {
            int defaults = 0;
            if (useVibrate) defaults |= Notification.DEFAULT_VIBRATE;
            if (useSound) {
                if (TextUtils.isEmpty(talkerRing)) defaults |= Notification.DEFAULT_SOUND;
                else builder.setSound(Uri.parse(talkerRing));
            }
            builder.setDefaults(defaults);
            try { builder.setPriority(Notification.PRIORITY_HIGH); } catch (Throwable ignored) {}
        }

        Intent[] intents = buildChatOpenIntents(talker);
        if (intents != null && intents.length > 0) {
            builder.setContentIntent(PendingIntent.getActivities(hostContext, talker.hashCode(), intents, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        }

        if (enableQuickReply) {
            try {
                RemoteInput remoteInput = new RemoteInput.Builder("key_reply_content")
                        .setLabel("输入回复内容...")
                        .build();
                Intent replyIntent = new Intent(ACTION_QUICK_REPLY);
                replyIntent.setPackage(hostContext.getPackageName());
                replyIntent.putExtra(EXTRA_TALKER, talker);
                int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                // RemoteInput requires mutable PendingIntent on Android 12+.
                // On old versions, keep default mutability (do not force IMMUTABLE).
                if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
                PendingIntent replyPi = PendingIntent.getBroadcast(hostContext, talker.hashCode(), replyIntent, flags);
                Notification.Action action = new Notification.Action.Builder(
                        android.R.drawable.ic_menu_send,
                        "快捷回复",
                        replyPi
                ).addRemoteInput(remoteInput).build();
                builder.addAction(action);
            } catch (Throwable ignored) {}
        }

        int notifyId = talker.hashCode();
        nm.notify(notifyId, builder.build()); // 直接覆盖发送即可，系统会完美处理过渡

        // 8+ 声音统一手动播放（含默认通知声/自定义铃声）
        if (Build.VERSION.SDK_INT >= 26) {
            String playableRing = talkerRing;
            if (!TextUtils.isEmpty(playableRing) && !ensureUriReadable(playableRing)) {
                playableRing = "";
            }
            playNotifySoundByConfig(useSound, playableRing);
        } else {
            if (useManualCustomSound) playCustomRingtoneFallback(talkerRing);
        }
    } catch (Throwable ignored) {}
}

void hookActivityResultForRingtone() {
    try {
        XC_MethodHook resultHook = new XC_MethodHook() {
            protected void beforeHookedMethod(de.robv.android.xposed.XC_MethodHook.MethodHookParam param) throws Throwable {
                int requestCode = (Integer) param.args[0];
                if (requestCode == REQ_PICK_RINGTONE_SYSTEM || requestCode == REQ_PICK_RINGTONE_FILE) {
                    Intent data = (Intent) param.args[2];
                    Uri uri = null;
                    if (data != null && requestCode == REQ_PICK_RINGTONE_SYSTEM) {
                        try { uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI); } catch (Throwable ignored) {}
                        try {
                            if (uri != null && "content".equalsIgnoreCase(uri.getScheme())) {
                                final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
                                hostContext.getContentResolver().takePersistableUriPermission(uri, takeFlags);
                            }
                        } catch (Throwable ignored) {}
                    }
                    if (data != null && uri == null && requestCode == REQ_PICK_RINGTONE_FILE) {
                        try { uri = data.getData(); } catch (Throwable ignored) {}
                        try {
                            if (uri != null && "content".equalsIgnoreCase(uri.getScheme())) {
                                final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
                                hostContext.getContentResolver().takePersistableUriPermission(uri, takeFlags);
                            }
                        } catch (Throwable ignored) {}
                    }
                    String ring = uri == null ? "" : uri.toString();
                    if (!TextUtils.isEmpty(ring)) {
                        ring = freezeRingtoneUri(ring);
                    }
                    if (globalRingtoneValueRef != null && globalRingtoneValueRef.length > 0) {
                        globalRingtoneValueRef[0] = ring;
                    }
                    final Activity top = globalSettingActivity;
                    if (top != null && globalRingtoneValueView != null) {
                        final String text = getRingtoneDisplayName(top, ring) + " >";
                        top.runOnUiThread(new Runnable() {
                            public void run() {
                                try { globalRingtoneValueView.setText(text); } catch (Throwable ignored) {}
                            }
                        });
                    }
                }
            }
        };
        Object uh = XposedHelpers.findAndHookMethod(Activity.class, "onActivityResult", int.class, int.class, Intent.class, resultHook);
        resultUnhooks.add(uh);
    } catch (Throwable ignored) {}
}

// ================= 通知提取辅助 =================
String valToString(Object v) {
    if (v == null) return null;
    try { return String.valueOf(v); } catch (Throwable e) { return null; }
}
String findChatroomInString(String s) {
    if (s == null) return null;
    try {
        Matcher m = chatroomPattern.matcher(s);
        if (m.find()) return m.group(1);
    } catch (Throwable ignored) {}
    return null;
}

String normalizeTitleKey(String s) {
    if (s == null) return "";
    try {
        String cacheKey = "k|" + s;
        Object cached = sTitleNormCache.get(cacheKey);
        if (cached != null) return String.valueOf(cached);

        String t = s.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').replace((char) 12288, ' ');
        t = t.replaceAll("\\s+", "").trim().toLowerCase();

        if (sTitleNormCache.size() >= sTitleNormCacheMax) sTitleNormCache.clear();
        sTitleNormCache.put(cacheKey, t);
        return t;
    } catch (Throwable ignored) {}
    return "";
}

String normalizeTitleLooseKey(String s) {
    if (s == null) return "";
    try {
        String cacheKey = "l|" + s;
        Object cached = sTitleNormCache.get(cacheKey);
        if (cached != null) return String.valueOf(cached);

        String t = normalizeTitleKey(s);
        if (TextUtils.isEmpty(t)) return "";
        // 去掉大部分符号/emoji，只保留中文、字母、数字，提升特殊昵称匹配稳定性
        t = t.replaceAll("[^\\u4e00-\\u9fa5a-z0-9]+", "");

        if (sTitleNormCache.size() >= sTitleNormCacheMax) sTitleNormCache.clear();
        sTitleNormCache.put(cacheKey, t);
        return t;
    } catch (Throwable ignored) {}
    return "";
}

String stripBracketTags(String s) {
    if (s == null) return "";
    try {
        // 兼容“[表情][闪烁]”这类通知标题标签
        return s.replaceAll("\\[[^\\]]*\\]", "");
    } catch (Throwable ignored) {}
    return s;
}

boolean titleMaybeMatchName(String title, String name) {
    if (TextUtils.isEmpty(title) || TextUtils.isEmpty(name)) return false;
    try {
        String titleRaw = title;
        String titleBase = stripWechatTitleSuffix(titleRaw);
        String titleNoTag = stripBracketTags(titleBase);
        if (titleRaw.contains(name) || name.contains(titleRaw)) return true;
        if (!TextUtils.isEmpty(titleNoTag) && (titleNoTag.contains(name) || name.contains(titleNoTag))) return true;
        String t1 = normalizeTitleKey(titleNoTag);
        String n1 = normalizeTitleKey(name);
        if (!TextUtils.isEmpty(t1) && !TextUtils.isEmpty(n1)) {
            if (t1.contains(n1) || n1.contains(t1)) return true;
        }
        String t2 = normalizeTitleLooseKey(titleNoTag);
        String n2 = normalizeTitleLooseKey(name);
        if (TextUtils.isEmpty(t2) || TextUtils.isEmpty(n2)) return false;
        return t2.contains(n2) || n2.contains(t2);
    } catch (Throwable ignored) {}
    return false;
}

String stripWechatTitleSuffix(String title) {
    if (title == null) return "";
    try {
        String t = title.trim();
        int p = t.indexOf("[");
        if (p > 0) t = t.substring(0, p).trim();
        p = t.indexOf(":");
        if (p > 0) t = t.substring(0, p).trim();
        return t;
    } catch (Throwable ignored) {}
    return title;
}

String findTalkerByGroupTitle(String title) {
    if (TextUtils.isEmpty(title)) return null;
    String base = stripWechatTitleSuffix(title);
    if (TextUtils.isEmpty(base)) return null;

    String cacheKey = normalizeTitleLooseKey(base);
    if (TextUtils.isEmpty(cacheKey)) cacheKey = normalizeTitleKey(base);
    if (!TextUtils.isEmpty(cacheKey)) {
        try {
            Object cv = sGroupTitleTalkerCache.get(cacheKey);
            if (cv instanceof String[]) {
                String[] pair = (String[]) cv;
                if (pair.length >= 2) {
                    long ts = 0L;
                    try { ts = Long.parseLong(pair[1]); } catch (Throwable ignored) {}
                    if (System.currentTimeMillis() - ts <= sGroupTitleTalkerCacheTtlMs) {
                        String hit = pair[0];
                        if (!TextUtils.isEmpty(hit)) return hit;
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    try {
        if (sCachedGroupIds != null && sCachedGroupNames != null && sCachedGroupIds.size() == sCachedGroupNames.size()) {
            for (int i = 0; i < sCachedGroupIds.size(); i++) {
                String gid = String.valueOf(sCachedGroupIds.get(i));
                String gname = String.valueOf(sCachedGroupNames.get(i));
                if (TextUtils.isEmpty(gid) || TextUtils.isEmpty(gname) || "null".equalsIgnoreCase(gname)) continue;
                if (titleMaybeMatchName(base, gname) || titleMaybeMatchName(title, gname)) {
                    if (!TextUtils.isEmpty(cacheKey)) {
                        if (sGroupTitleTalkerCache.size() >= sGroupTitleTalkerCacheMax) sGroupTitleTalkerCache.clear();
                        sGroupTitleTalkerCache.put(cacheKey, new String[]{gid, String.valueOf(System.currentTimeMillis())});
                    }
                    return gid;
                }
            }
        }
    } catch (Throwable ignored) {}
    try {
        long now = System.currentTimeMillis();
        if (now - sLastGroupCacheLoadAt < 15000L) return null;
        sLastGroupCacheLoadAt = now;
        List groups = getGroupList();
        if (groups != null) {
            List names = new ArrayList();
            List ids = new ArrayList();
            for (int i = 0; i < groups.size(); i++) {
                GroupInfo g = (GroupInfo) groups.get(i);
                if (g == null) continue;
                String gid = g.getRoomId();
                String gname = g.getName();
                if (TextUtils.isEmpty(gid) || TextUtils.isEmpty(gname)) continue;
                ids.add(gid);
                names.add(gname);
                if (titleMaybeMatchName(base, gname) || titleMaybeMatchName(title, gname)) {
                    sCachedGroupIds = ids;
                    sCachedGroupNames = names;
                    if (!TextUtils.isEmpty(cacheKey)) {
                        if (sGroupTitleTalkerCache.size() >= sGroupTitleTalkerCacheMax) sGroupTitleTalkerCache.clear();
                        sGroupTitleTalkerCache.put(cacheKey, new String[]{gid, String.valueOf(System.currentTimeMillis())});
                    }
                    return gid;
                }
            }
            sCachedGroupIds = ids;
            sCachedGroupNames = names;
        }
    } catch (Throwable ignored) {}
    return null;
}
String resolveTalkerNameForMatch(String talkerId) {
    if (TextUtils.isEmpty(talkerId)) return "";
    try {
        String n = getFriendName(talkerId);
        if (!TextUtils.isEmpty(n) && !talkerId.equals(n)) return n;
    } catch (Throwable ignored) {}
    try {
        if (sCachedGroupIds != null && sCachedGroupNames != null) {
            for (int i = 0; i < sCachedGroupIds.size(); i++) {
                if (talkerId.equals(String.valueOf(sCachedGroupIds.get(i)))) {
                    String gn = String.valueOf(sCachedGroupNames.get(i));
                    if (!TextUtils.isEmpty(gn) && !"null".equalsIgnoreCase(gn)) return gn;
                    break;
                }
            }
        }
    } catch (Throwable ignored) {}
    try {
        List groups = getGroupList();
        if (groups != null) {
            for (int i = 0; i < groups.size(); i++) {
                GroupInfo g = (GroupInfo) groups.get(i);
                if (g != null && talkerId.equals(g.getRoomId())) {
                    String gn = g.getName();
                    if (!TextUtils.isEmpty(gn)) return gn;
                    break;
                }
            }
        }
    } catch (Throwable ignored) {}
    return "";
}
String findTalkerByCacheContains(String raw) {
    if (TextUtils.isEmpty(raw) || cacheTargetSet.isEmpty()) return null;
    try {
        Object[] ids = cacheTargetSet.toArray();
        for (int i = 0; i < ids.length; i++) {
            String one = String.valueOf(ids[i]);
            if (TextUtils.isEmpty(one)) continue;
            if (raw.equals(one) || raw.contains(one)) return one;
        }
    } catch (Throwable ignored) {}
    return null;
}

String findTargetTalkerByTitle(String title) {
    if (TextUtils.isEmpty(title) || cacheTargetSet == null || cacheTargetSet.isEmpty()) return null;
    try {
        Object[] ids = cacheTargetSet.toArray();
        for (int i = 0; i < ids.length; i++) {
            String tid = String.valueOf(ids[i]);
            if (TextUtils.isEmpty(tid)) continue;
            String nm = resolveTalkerNameForMatch(tid);
            if (TextUtils.isEmpty(nm)) continue;
            if (titleMaybeMatchName(title, nm)) return tid;
        }
    } catch (Throwable ignored) {}
    return null;
}
String scanBundleForTalker(Bundle b) {
    if (b == null) return null;
    String[] keys = new String[]{"Main_User", "MainUser", "talker", "Talker", "chat_talker", "chat_username", "username", "userName", "wxid", "contact", "conversation_id", "conversationId"};
    for (int i = 0; i < keys.length; i++) {
        try {
            String raw = valToString(b.get(keys[i]));
            String hit = findTalkerByCacheContains(raw);
            if (hit != null) return hit;
            hit = findChatroomInString(raw);
            if (hit != null) return hit;
        } catch (Throwable ignored) {}
    }
    return null;
}
String extractTalkerFromNotification(Notification n) {
    if (n == null) return null;
    try {
        String t = scanBundleForTalker(n.extras);
        if (t != null) return t;
    } catch (Throwable ignored) {}
    try {
        Notification.Action[] actions = n.actions;
        if (actions != null) {
            for (int i = 0; i < actions.length; i++) {
                Notification.Action a = actions[i];
                if (a == null) continue;
                String t = scanBundleForTalker(a.getExtras());
                if (t != null) return t;
            }
        }
    } catch (Throwable ignored) {}

    // 深扫 PendingIntent extras，提升部分 ROM/版本下重启早期 talker 提取成功率
    try {
        PendingIntent[] pis = new PendingIntent[]{n.contentIntent, n.deleteIntent, n.fullScreenIntent};
        for (int i = 0; i < pis.length; i++) {
            PendingIntent pi = pis[i];
            if (pi == null) continue;
            try {
                Intent it = null;
                try { it = (Intent) pi.getClass().getMethod("getIntent").invoke(pi); } catch (Throwable ignored2) {}
                if (it == null) continue;
                String t = scanBundleForTalker(it.getExtras());
                if (t != null) return t;
            } catch (Throwable ignored3) {}
        }
    } catch (Throwable ignored) {}
    return null;
}
Object safeInvoke(Object obj, String methodName) {
    if (obj == null || TextUtils.isEmpty(methodName)) return null;
    try {
        Class c = obj.getClass();
        String key = c.getName() + "#" + methodName;

        if (sNoArgMethodMissCache.contains(key)) return null;

        Method cached = (Method) sNoArgMethodCache.get(key);
        if (cached != null) {
            return cached.invoke(obj);
        }
        Method[] methods = c.getMethods();
        for (int i = 0; i < methods.length; i++) {
            Method m = methods[i];
            if (!m.getName().equals(methodName)) continue;
            if (m.getParameterTypes().length != 0) continue;
            sNoArgMethodCache.put(key, m);
            return m.invoke(obj);
        }

        sNoArgMethodMissCache.add(key);
    } catch (Throwable ignored) {}
    return null;
}

// ================= UI 界面系统 =================
Activity globalSettingActivity;
TextView globalRingtoneValueView;
String[] globalRingtoneValueRef;

void showSettingsUI() {
    globalSettingActivity = getTopActivity();
    if (globalSettingActivity == null) return;
    
    globalSettingActivity.runOnUiThread(new Runnable() {
        public void run() {
            hideSoftInput(globalSettingActivity);
            buildMainUI(globalSettingActivity);
        }
    });
}

void buildMainUI(final Activity ctx) {
    hideSoftInput(ctx);
    try {
        final Dialog dialog = new Dialog(ctx, android.R.style.Theme_Translucent_NoTitleBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        FrameLayout root = new FrameLayout(ctx);
        root.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        root.setBackgroundColor(Color.parseColor("#66000000"));

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardLp.leftMargin = dp(ctx, 20);
        cardLp.rightMargin = dp(ctx, 20);
        cardLp.gravity = Gravity.CENTER;
        card.setLayoutParams(cardLp);
        card.setPadding(dp(ctx, 18), dp(ctx, 18), dp(ctx, 18), dp(ctx, 12));

        GradientDrawable cardBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.parseColor("#FFFFFF"), Color.parseColor("#F8FAFC")}
        );
        cardBg.setCornerRadius(dp(ctx, 20));
        cardBg.setStroke(dp(ctx, 1), Color.parseColor("#DDE6F2"));
        card.setBackground(cardBg);

        TextView title = new TextView(ctx);
        title.setText("通知设置");
        title.setTextColor(Color.parseColor("#0F172A"));
        title.setTextSize(20f);
        title.setTypeface(null, Typeface.BOLD);
        card.addView(title);

        TextView sub = new TextView(ctx);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.topMargin = dp(ctx, 8);
        sub.setLayoutParams(subLp);
        sub.setText("按会话单独配置通知规则");
        sub.setTextColor(Color.parseColor("#64748B"));
        sub.setTextSize(13f);
        card.addView(sub);

        LinearLayout actionCard = new LinearLayout(ctx);
        actionCard.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams acLp = new LinearLayout.LayoutParams(-1, -2);
        acLp.topMargin = dp(ctx, 14);
        actionCard.setLayoutParams(acLp);
        GradientDrawable acBg = roundRect(Color.parseColor("#F8FAFC"), dp(ctx, 14));
        acBg.setStroke(dp(ctx, 1), Color.parseColor("#E2E8F0"));
        actionCard.setBackground(acBg);

        LinearLayout rowEnter = createRowText(ctx, "会话列表与规则", "进入 >", true);
        rowEnter.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                dialog.dismiss();
                showTargetListUI(ctx);
            }
        });
        actionCard.addView(rowEnter);

        card.addView(actionCard);

        LinearLayout actions = new LinearLayout(ctx);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(-1, -2);
        actionsLp.topMargin = dp(ctx, 16);
        actions.setLayoutParams(actionsLp);
        actions.setGravity(Gravity.END);

        TextView btnClose = new TextView(ctx);
        btnClose.setText("关闭");
        btnClose.setTextColor(Color.parseColor("#334155"));
        btnClose.setTextSize(14f);
        btnClose.setPadding(dp(ctx, 16), dp(ctx, 8), dp(ctx, 16), dp(ctx, 8));
        GradientDrawable closeBg = roundRect(Color.parseColor("#EFF3F8"), dp(ctx, 999));
        closeBg.setStroke(dp(ctx, 1), Color.parseColor("#DEE7F2"));
        btnClose.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.parseColor("#22000000")), closeBg, null));
        actions.addView(btnClose);

        card.addView(actions);
        root.addView(card);
        dialog.setContentView(root);

        root.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { dialog.dismiss(); }
        });
        card.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {}
        });

        btnClose.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { dialog.dismiss(); }
        });

        Window w = dialog.getWindow();
        if (w != null) {
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            w.setGravity(Gravity.CENTER);
            w.setDimAmount(0.25f);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        }

        dialog.show();
        card.setAlpha(0f);
        card.setTranslationY(dp(ctx, 20));
        card.animate().alpha(1f).translationY(0).setDuration(180).start();

    } catch (Throwable e) {
        toast("打开设置界面失败");
    }
}

void showRingtonePickStyleDialog(final Activity ctx, final String[] tmpRingtone, final TextView tvRing) {
    try {
        final Dialog pickDialog = new Dialog(ctx, android.R.style.Theme_Translucent_NoTitleBar);
        pickDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        pickDialog.setCancelable(true);

        FrameLayout mask = new FrameLayout(ctx);
        mask.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        mask.setBackgroundColor(Color.parseColor("#66000000"));

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(-1, -2);
        cardLp.leftMargin = dp(ctx, 24);
        cardLp.rightMargin = dp(ctx, 24);
        cardLp.gravity = Gravity.CENTER;
        card.setLayoutParams(cardLp);
        card.setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 12));

        GradientDrawable cardBg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Color.parseColor("#FFFFFF"), Color.parseColor("#F8FAFC")});
        cardBg.setCornerRadius(dp(ctx, 18));
        cardBg.setStroke(dp(ctx, 1), Color.parseColor("#DDE6F2"));
        card.setBackground(cardBg);

        TextView title = new TextView(ctx);
        title.setText("选择方式");
        title.setTextColor(Color.parseColor("#0F172A"));
        title.setTextSize(17f);
        title.setTypeface(null, Typeface.BOLD);
        card.addView(title);

        TextView sub = new TextView(ctx);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.topMargin = dp(ctx, 6);
        sub.setLayoutParams(subLp);
        sub.setText("选择系统铃声或从文件夹导入");
        sub.setTextColor(Color.parseColor("#64748B"));
        sub.setTextSize(13f);
        card.addView(sub);

        LinearLayout btnSys = createRowText(ctx, "选择系统铃声", "推荐 >", false);
        btnSys.setBackground(roundRect(Color.parseColor("#F1F5F9"), dp(ctx, 12)));
        LinearLayout.LayoutParams sysLp = new LinearLayout.LayoutParams(-1, -2);
        sysLp.topMargin = dp(ctx, 12);
        card.addView(btnSys, sysLp);
        btnSys.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
                    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
                    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
                    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
                    if (!TextUtils.isEmpty(tmpRingtone[0])) intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(tmpRingtone[0]));
                    ctx.startActivityForResult(intent, REQ_PICK_RINGTONE_SYSTEM);
                } catch (Throwable ignored) {}
                try { pickDialog.dismiss(); } catch (Throwable ignored) {}
            }
        });

        LinearLayout btnFile = createRowText(ctx, "从文件夹选择", "音频文件 >", false);
        btnFile.setBackground(roundRect(Color.parseColor("#F1F5F9"), dp(ctx, 12)));
        LinearLayout.LayoutParams fileLp = new LinearLayout.LayoutParams(-1, -2);
        fileLp.topMargin = dp(ctx, 8);
        card.addView(btnFile, fileLp);
        btnFile.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    Intent fileIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    fileIntent.setType("audio/*");
                    fileIntent.addCategory(Intent.CATEGORY_OPENABLE);
                    fileIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    fileIntent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

                    // 优先直连系统 DocumentsUI，尽量避免厂商文件管理器接管
                    Intent nativeIntent = new Intent(fileIntent);
                    nativeIntent.setPackage("com.android.documentsui");
                    try {
                        ctx.startActivityForResult(nativeIntent, REQ_PICK_RINGTONE_FILE);
                    } catch (Throwable ignoredNative) {
                        // 回退到通用文档选择，确保兼容没有 DocumentsUI 包的机型
                        ctx.startActivityForResult(Intent.createChooser(fileIntent, "选择铃声文件"), REQ_PICK_RINGTONE_FILE);
                    }
                } catch (Throwable ignored) {}
                try { pickDialog.dismiss(); } catch (Throwable ignored) {}
            }
        });

        TextView btnCancel = new TextView(ctx);
        btnCancel.setText("取消");
        btnCancel.setTextColor(Color.parseColor("#64748B"));
        btnCancel.setTextSize(14f);
        btnCancel.setGravity(Gravity.CENTER);
        btnCancel.setPadding(dp(ctx, 12), dp(ctx, 11), dp(ctx, 12), dp(ctx, 8));
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(-1, -2);
        cancelLp.topMargin = dp(ctx, 6);
        card.addView(btnCancel, cancelLp);
        btnCancel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try { pickDialog.dismiss(); } catch (Throwable ignored) {}
            }
        });

        mask.addView(card);
        mask.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try { pickDialog.dismiss(); } catch (Throwable ignored) {}
            }
        });
        card.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            }
        });

        pickDialog.setContentView(mask);
        Window w = pickDialog.getWindow();
        if (w != null) {
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            w.setGravity(Gravity.CENTER);
            w.setDimAmount(0.25f);
        }
        pickDialog.show();
        card.setAlpha(0f);
        card.setTranslationY(dp(ctx, 14));
        card.animate().alpha(1f).translationY(0).setDuration(160).start();
    } catch (Throwable ignored) {}
}

LinearLayout createRowText(final Activity ctx, String left, String right, boolean neutralRight) {
    LinearLayout row = new LinearLayout(ctx);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setPadding(dp(ctx, 14), dp(ctx, 14), dp(ctx, 14), dp(ctx, 14));
    row.setGravity(Gravity.CENTER_VERTICAL);

    TextView l = new TextView(ctx);
    l.setText(left);
    l.setTextColor(Color.parseColor("#0F172A"));
    l.setTextSize(17f);
    row.addView(l, new LinearLayout.LayoutParams(0, -2, 1));

    TextView r = new TextView(ctx);
    r.setText(right);
    r.setTextSize(15f);
    r.setTextColor(neutralRight ? Color.parseColor("#475569") : Color.parseColor("#2563EB"));
    row.addView(r);
    return row;
}

int dp(Activity a, int v) {
    return (int) (v * a.getResources().getDisplayMetrics().density + 0.5f);
}

GradientDrawable roundRect(int color, int radiusPx) {
    GradientDrawable g = new GradientDrawable();
    g.setColor(color);
    g.setCornerRadius(radiusPx);
    return g;
}
void showTargetListUI(final Activity ctx) {
    if (sCachedFriendNames != null && sCachedGroupNames != null) {
        buildListUI(ctx, sCachedFriendNames, sCachedFriendIds, sCachedGroupNames, sCachedGroupIds);
        return;
    }
    final Dialog[] loadingRef = new Dialog[1];
    try {
        if (!ctx.isFinishing() && !ctx.isDestroyed()) {
            Dialog loading = new Dialog(ctx, android.R.style.Theme_Translucent_NoTitleBar);
            loading.requestWindowFeature(Window.FEATURE_NO_TITLE);
            loading.setCancelable(false);

            FrameLayout mask = new FrameLayout(ctx);
            mask.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
            mask.setBackgroundColor(Color.parseColor("#66000000"));

            LinearLayout card = new LinearLayout(ctx);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 14));
            FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(-2, -2);
            cardLp.gravity = Gravity.CENTER;
            card.setLayoutParams(cardLp);

            GradientDrawable cardBg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Color.parseColor("#FFFFFF"), Color.parseColor("#F8FAFC")});
            cardBg.setCornerRadius(dp(ctx, 16));
            cardBg.setStroke(dp(ctx, 1), Color.parseColor("#DDE6F2"));
            card.setBackground(cardBg);

            ProgressBar pb = new ProgressBar(ctx);
            card.addView(pb);

            TextView tv = new TextView(ctx);
            tv.setText("首次加载联系人中，请稍候...");
            tv.setTextColor(Color.parseColor("#0F172A"));
            tv.setTextSize(14f);
            LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(-2, -2);
            tvLp.leftMargin = dp(ctx, 10);
            card.addView(tv, tvLp);

            mask.addView(card);
            loading.setContentView(mask);

            Window w = loading.getWindow();
            if (w != null) {
                w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
                w.setGravity(Gravity.CENTER);
                w.setDimAmount(0.25f);
            }

            loading.show();
            loadingRef[0] = loading;
        }
    } catch (Throwable ignored) {}

    new Thread(new Runnable() {
        public void run() {
            final List friendNames = new ArrayList();
            final List friendIds = new ArrayList();
            final List groupNames = new ArrayList();
            final List groupIds = new ArrayList();

            try {
                List friends = getFriendList();
                if (friends != null) {
                    for (int i = 0; i < friends.size(); i++) {
                        FriendInfo f = (FriendInfo) friends.get(i);
                        String wxid = f.getWxid();
                        String nick = f.getNickname();
                        if (TextUtils.isEmpty(nick)) nick = "未知";

                        String remark = "";
                        try { remark = getFriendRemarkName(wxid); } catch (Throwable ignored) {}
                        if (remark == null) remark = "";
                        remark = remark.trim();

                        String nickNorm = nick == null ? "" : nick.trim();
                        // 某些环境下无备注会返回昵称本身，避免出现“昵称(昵称)”
                        if (!TextUtils.isEmpty(remark) && remark.equals(nickNorm)) {
                            remark = "";
                        }

                        String showName = nick;
                        if (!TextUtils.isEmpty(remark)) {
                            // 有备注：昵称+备注；无备注：仅昵称
                            showName = nick + "(" + remark + ")";
                        }

                        friendNames.add(showName);
                        friendIds.add(wxid);
                    }
                }
                List groups = getGroupList();
                if (groups != null) {
                    for (int i = 0; i < groups.size(); i++) {
                        GroupInfo g = (GroupInfo) groups.get(i);
                        groupNames.add(TextUtils.isEmpty(g.getName()) ? "未知群聊" : g.getName());
                        groupIds.add(g.getRoomId());
                    }
                }
            } catch (Throwable ignored) {}

            new Handler(Looper.getMainLooper()).post(new Runnable() {
                public void run() {
                    try {
                        if (loadingRef[0] != null && loadingRef[0].isShowing()) loadingRef[0].dismiss();
                    } catch (Throwable e) {}
                    if (ctx.isFinishing() || ctx.isDestroyed()) return;
                    
                    sCachedFriendNames = friendNames;
                    sCachedFriendIds = friendIds;
                    sCachedGroupNames = groupNames;
                    sCachedGroupIds = groupIds;
                    
                    buildListUI(ctx, friendNames, friendIds, groupNames, groupIds);
                }
            });
        }
    }).start();
}
void showSingleTimePicker(final Activity ctx, String title, final String[] valueRef, final Runnable onChange) {
    int t = parseTimeToMinute(valueRef[0]);
    int h = t >= 0 ? t / 60 : 0;
    int m = t >= 0 ? t % 60 : 0;
    TimePickerDialog d = new TimePickerDialog(ctx, new TimePickerDialog.OnTimeSetListener() {
        public void onTimeSet(android.widget.TimePicker view, int hourOfDay, int minute) {
            valueRef[0] = formatHHmm(hourOfDay, minute);
            if (onChange != null) onChange.run();
        }
    }, h, m, true);
    d.setTitle(title);
    d.show();
}

void saveSelectedTargets(Set selectedIds) {
    StringBuilder sb = new StringBuilder();
    Object[] arr = selectedIds.toArray();
    for (int i = 0; i < arr.length; i++) {
        if (i > 0) sb.append(",");
        sb.append(arr[i]);
    }
    putString(CFG_TARGETS, sb.toString());
}

void applyDialogSize(AlertDialog d, float widthRatio, float heightRatio) {
    try {
        if (d == null || d.getWindow() == null) return;
        DisplayMetrics dm = new DisplayMetrics();
        d.getWindow().getWindowManager().getDefaultDisplay().getMetrics(dm);
        int w = (int) (dm.widthPixels * widthRatio);
        int h = (int) (dm.heightPixels * heightRatio);
        d.getWindow().setLayout(w, h);
        d.getWindow().setGravity(Gravity.CENTER);
        d.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        d.getWindow().setDimAmount(0f);
        d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    } catch (Throwable ignored) {}
}

void hideSoftInput(Activity ctx) {
    try {
        if (ctx == null) return;
        InputMethodManager imm = (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null) return;
        View focus = ctx.getCurrentFocus();
        if (focus == null) focus = new View(ctx);
        imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
    } catch (Throwable ignored) {}
}

void showSoftInputForView(Activity ctx, View v) {
    try {
        if (ctx == null || v == null) return;
        v.requestFocus();
        InputMethodManager imm = (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null) return;
        imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT);
    } catch (Throwable ignored) {}
}

void prepareSearchInput(final Activity ctx, final EditText et) {
    try {
        if (et == null) return;
        et.setFocusable(true);
        et.setFocusableInTouchMode(true);
        et.setClickable(true);
        et.setCursorVisible(true);
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        et.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showSoftInputForView(ctx, et); }
        });
        et.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) showSoftInputForView(ctx, et);
            }
        });
    } catch (Throwable ignored) {}
}

void showTalkerConfigDialog(final Activity ctx, final String talkerId, final boolean isGroup, final String displayNameRaw, final Set selectedIds, final Runnable onSaved) {
    hideSoftInput(ctx);
    String displayName = displayNameRaw.replace("  ✓", "").replace("  [已配置]", "").replace("  [未配置]", "");
    // 仅在配置弹窗内做昵称可见化处理，避免全换行昵称导致标题不可见
    displayName = displayName.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ');
    displayName = displayName.replaceAll("\\s+", " ").trim();
    if (TextUtils.isEmpty(displayName)) displayName = "（无可见昵称）";

    Map oldCfg = parseTalkerCfg(getString(CFG_TALKER_CFG_PREFIX + talkerId, ""));
    final boolean[] tmpEnable = {selectedIds.contains(talkerId)};
    final int[] tmpMode = {cfgGetInt(oldCfg, "mode", 1)};
    final boolean[] tmpDnd = {tmpMode[0] == 0};
    final boolean[] tmpVibrate = {cfgGetBool(oldCfg, "vibrate", cacheVibrate)};
    final boolean[] tmpSound = {cfgGetBool(oldCfg, "sound", cacheSound)};
    final boolean[] tmpShowDetail = {cfgGetBool(oldCfg, "showDetail", cacheShowDetail)};
    final boolean[] tmpMuteEnable = {cfgGetBool(oldCfg, "muteEnable", cacheMuteTimeEnable)};
    final String[] tmpMuteStart = {normalizeTime(cfgGet(oldCfg, "muteStart", cacheMuteTimeStart), cacheMuteTimeStart)};
    final String[] tmpMuteEnd = {normalizeTime(cfgGet(oldCfg, "muteEnd", cacheMuteTimeEnd), cacheMuteTimeEnd)};
    final boolean[] tmpBlockAll = {cfgGetBool(oldCfg, "blockAll", cacheBlockAtAll)};
    final boolean[] tmpBlockMe = {cfgGetBool(oldCfg, "blockMe", cacheBlockAtMe)};
    final boolean[] tmpQuickReply = {cfgGetBool(oldCfg, "quickReply", false)};
    final String[] tmpRingtone = {cfgGet(oldCfg, "ringtone", "")};
    final String[] tmpOnlyMembers = {cfgGet(oldCfg, "onlyMembers", "")};
    final String[] tmpBlockMembers = {cfgGet(oldCfg, "blockMembers", "")};

    try {
        final Dialog dialog = new Dialog(ctx, android.R.style.Theme_Translucent_NoTitleBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        FrameLayout root = new FrameLayout(ctx);
        root.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        root.setBackgroundColor(Color.parseColor("#66000000"));

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(-1, -2);
        cardLp.leftMargin = dp(ctx, 16);
        cardLp.rightMargin = dp(ctx, 16);
        cardLp.gravity = Gravity.CENTER;
        card.setLayoutParams(cardLp);
        card.setPadding(dp(ctx, 16), dp(ctx, 16), dp(ctx, 16), dp(ctx, 12));

        GradientDrawable cardBg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Color.parseColor("#FFFFFF"), Color.parseColor("#F8FAFC")});
        cardBg.setCornerRadius(dp(ctx, 20));
        cardBg.setStroke(dp(ctx, 1), Color.parseColor("#DDE6F2"));
        card.setBackground(cardBg);

        TextView title = new TextView(ctx);
        title.setText((isGroup ? "群聊通知配置" : "私聊通知配置") + "\n" + displayName);
        title.setTextColor(Color.parseColor("#0F172A"));
        title.setTextSize(18f);
        title.setTypeface(null, Typeface.BOLD);
        card.addView(title);

        ScrollView sv = new ScrollView(ctx);
        LinearLayout body = new LinearLayout(ctx);
        body.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(-1, dp(ctx, 400));
        bodyLp.topMargin = dp(ctx, 12);
        sv.setLayoutParams(bodyLp);

        GradientDrawable bodyBg = roundRect(Color.parseColor("#F8FAFC"), dp(ctx, 14));
        bodyBg.setStroke(dp(ctx, 1), Color.parseColor("#E2E8F0"));
        body.setBackground(bodyBg);

        addDarkSwitchRow(ctx, body, "启用此会话规则", tmpEnable[0], new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean c) { tmpEnable[0] = c; }
        });
        addDarkDivider(ctx, body);

        addDarkSwitchRow(ctx, body, "免打扰(不弹通知)", tmpDnd[0], new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean c) {
                tmpDnd[0] = c;
                tmpMode[0] = c ? 0 : 1;
            }
        });
        addDarkDivider(ctx, body);

        addDarkSwitchRow(ctx, body, "震动", tmpVibrate[0], new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean c) { tmpVibrate[0] = c; }
        });
        addDarkDivider(ctx, body);

        addDarkSwitchRow(ctx, body, "铃声", tmpSound[0], new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean c) { tmpSound[0] = c; }
        });
        addDarkDivider(ctx, body);

        addDarkSwitchRow(ctx, body, "快捷回复", tmpQuickReply[0], new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean c) { tmpQuickReply[0] = c; }
        });
        addDarkDivider(ctx, body);

        final TextView tvRing = addDarkClickRow(ctx, body, "选择铃声", getRingtoneDisplayName(ctx, tmpRingtone[0]) + " >");
        ((View) tvRing.getParent()).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                globalRingtoneValueView = tvRing;
                globalRingtoneValueRef = tmpRingtone;
                showRingtonePickStyleDialog(ctx, tmpRingtone, tvRing);
            }
        });
        addDarkDivider(ctx, body);

        addDarkSwitchRow(ctx, body, "通知显示消息详情", tmpShowDetail[0], new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean c) { tmpShowDetail[0] = c; }
        });
        addDarkDivider(ctx, body);

        addDarkSwitchRow(ctx, body, "开启时段静默", tmpMuteEnable[0], new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean c) { tmpMuteEnable[0] = c; }
        });
        addDarkDivider(ctx, body);

        final TextView[] tvTime = new TextView[2];
        tvTime[0] = addDarkClickRow(ctx, body, "开始时间", tmpMuteStart[0] + " >");
        ((View) tvTime[0].getParent()).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showSingleTimePicker(ctx, "选择开始时间", tmpMuteStart, new Runnable() {
                    public void run() {
                        tvTime[0].setText(tmpMuteStart[0] + " >");
                        tvTime[1].setText(tmpMuteEnd[0] + " >");
                    }
                });
            }
        });
        addDarkDivider(ctx, body);

        tvTime[1] = addDarkClickRow(ctx, body, "结束时间", tmpMuteEnd[0] + " >");
        ((View) tvTime[1].getParent()).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showSingleTimePicker(ctx, "选择结束时间", tmpMuteEnd, new Runnable() {
                    public void run() {
                        tvTime[0].setText(tmpMuteStart[0] + " >");
                        tvTime[1].setText(tmpMuteEnd[0] + " >");
                    }
                });
            }
        });

        if (isGroup) {
            addDarkDivider(ctx, body);
            final TextView[] tvMemberRule = new TextView[2];
            tvMemberRule[0] = addDarkClickRow(ctx, body, "仅显示成员通知", getMemberRuleSummary(tmpOnlyMembers[0]));
            ((View) tvMemberRule[0].getParent()).setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    showGroupMemberPickerDialog(ctx, talkerId, "仅显示成员通知", tmpOnlyMembers, new Runnable() {
                        public void run() { tvMemberRule[0].setText(getMemberRuleSummary(tmpOnlyMembers[0])); }
                    });
                }
            });
            addDarkDivider(ctx, body);
            tvMemberRule[1] = addDarkClickRow(ctx, body, "屏蔽成员通知", getMemberRuleSummary(tmpBlockMembers[0]));
            ((View) tvMemberRule[1].getParent()).setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    showGroupMemberPickerDialog(ctx, talkerId, "屏蔽成员通知", tmpBlockMembers, new Runnable() {
                        public void run() { tvMemberRule[1].setText(getMemberRuleSummary(tmpBlockMembers[0])); }
                    });
                }
            });
            addDarkDivider(ctx, body);
            addDarkSwitchRow(ctx, body, "屏蔽@所有人", tmpBlockAll[0], new CompoundButton.OnCheckedChangeListener() {
                public void onCheckedChanged(CompoundButton b, boolean c) { tmpBlockAll[0] = c; }
            });
            addDarkDivider(ctx, body);
            addDarkSwitchRow(ctx, body, "屏蔽@我", tmpBlockMe[0], new CompoundButton.OnCheckedChangeListener() {
                public void onCheckedChanged(CompoundButton b, boolean c) { tmpBlockMe[0] = c; }
            });
        }

        sv.addView(body);
        card.addView(sv);

        LinearLayout actions = new LinearLayout(ctx);
        LinearLayout.LayoutParams acLp = new LinearLayout.LayoutParams(-1, -2);
        acLp.topMargin = dp(ctx, 14);
        actions.setLayoutParams(acLp);
        actions.setGravity(Gravity.END);

        TextView btnClear = new TextView(ctx);
        btnClear.setText("清除");
        btnClear.setTextColor(Color.parseColor("#334155"));
        btnClear.setTextSize(14f);
        btnClear.setPadding(dp(ctx, 14), dp(ctx, 8), dp(ctx, 14), dp(ctx, 8));
        GradientDrawable clearBg = roundRect(Color.parseColor("#EFF3F8"), dp(ctx, 999));
        clearBg.setStroke(dp(ctx, 1), Color.parseColor("#DEE7F2"));
        btnClear.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.parseColor("#22000000")), clearBg, null));

        TextView btnCancel = new TextView(ctx);
        btnCancel.setText("取消");
        btnCancel.setTextColor(Color.parseColor("#334155"));
        btnCancel.setTextSize(14f);
        btnCancel.setPadding(dp(ctx, 14), dp(ctx, 8), dp(ctx, 14), dp(ctx, 8));
        GradientDrawable cancelBg = roundRect(Color.parseColor("#EFF3F8"), dp(ctx, 999));
        cancelBg.setStroke(dp(ctx, 1), Color.parseColor("#DEE7F2"));
        btnCancel.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.parseColor("#22000000")), cancelBg, null));

        TextView btnSave = new TextView(ctx);
        btnSave.setText("保存");
        btnSave.setTextColor(Color.WHITE);
        btnSave.setTextSize(14f);
        btnSave.setTypeface(null, Typeface.BOLD);
        btnSave.setPadding(dp(ctx, 18), dp(ctx, 8), dp(ctx, 18), dp(ctx, 8));
        GradientDrawable saveBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Color.parseColor("#2563EB"), Color.parseColor("#7C3AED")});
        saveBg.setCornerRadius(dp(ctx, 999));
        btnSave.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.parseColor("#55FFFFFF")), saveBg, null));

        LinearLayout.LayoutParams lpBtn = new LinearLayout.LayoutParams(-2, -2);
        lpBtn.leftMargin = dp(ctx, 10);
        actions.addView(btnClear);
        actions.addView(btnCancel, lpBtn);
        actions.addView(btnSave, lpBtn);

        card.addView(actions);
        root.addView(card);
        dialog.setContentView(root);

        root.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { dialog.dismiss(); }});
        card.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {} });

        btnCancel.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { globalRingtoneValueRef = null; globalRingtoneValueView = null; dialog.dismiss(); }});

        btnClear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                selectedIds.remove(talkerId);
                putString(CFG_TALKER_CFG_PREFIX + talkerId, "");
                saveSelectedTargets(selectedIds);
                loadConfigToCache();
                if (onSaved != null) onSaved.run();
                globalRingtoneValueRef = null;
                globalRingtoneValueView = null;
                dialog.dismiss();
                toast("已清除该会话配置");
            }
        });

        btnSave.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!tmpEnable[0]) {
                    selectedIds.remove(talkerId);
                    putString(CFG_TALKER_CFG_PREFIX + talkerId, "");
                    saveSelectedTargets(selectedIds);
                    loadConfigToCache();
                    if (onSaved != null) onSaved.run();
                    globalRingtoneValueRef = null;
                    globalRingtoneValueView = null;
                    dialog.dismiss();
                    toast("该会话已关闭接管");
                    return;
                }

                Map newCfg = new HashMap();
                int modeToSave = tmpDnd[0] ? 0 : 1;
                newCfg.put("mode", String.valueOf(modeToSave));
                newCfg.put("vibrate", tmpVibrate[0] ? "1" : "0");
                newCfg.put("sound", tmpSound[0] ? "1" : "0");
                newCfg.put("quickReply", tmpQuickReply[0] ? "1" : "0");
                newCfg.put("ringtone", tmpRingtone[0]);
                newCfg.put("showDetail", tmpShowDetail[0] ? "1" : "0");
                newCfg.put("muteEnable", tmpMuteEnable[0] ? "1" : "0");
                newCfg.put("muteStart", normalizeTime(tmpMuteStart[0], "23:00"));
                newCfg.put("muteEnd", normalizeTime(tmpMuteEnd[0], "07:00"));
                if (isGroup) {
                    newCfg.put("blockAll", tmpBlockAll[0] ? "1" : "0");
                    newCfg.put("blockMe", tmpBlockMe[0] ? "1" : "0");
                    newCfg.put("onlyMembers", joinMemberRuleSet(parseMemberRuleSet(tmpOnlyMembers[0])));
                    newCfg.put("blockMembers", joinMemberRuleSet(parseMemberRuleSet(tmpBlockMembers[0])));
                } else {
                    newCfg.put("blockAll", "0");
                    newCfg.put("blockMe", "0");
                    newCfg.put("onlyMembers", "");
                    newCfg.put("blockMembers", "");
                }

                selectedIds.add(talkerId);
                putString(CFG_TALKER_CFG_PREFIX + talkerId, encodeTalkerCfg(newCfg));
                saveSelectedTargets(selectedIds);
                loadConfigToCache();
                if (onSaved != null) onSaved.run();
                globalRingtoneValueRef = null;
                globalRingtoneValueView = null;
                dialog.dismiss();
                toast("会话配置已保存");
            }
        });

        Window w = dialog.getWindow();
        if (w != null) {
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            w.setGravity(Gravity.CENTER);
            w.setDimAmount(0.25f);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        }

        dialog.show();
        card.setAlpha(0f);
        card.setTranslationY(dp(ctx, 20));
        card.animate().alpha(1f).translationY(0).setDuration(180).start();

    } catch (Throwable e) {
        toast("打开会话配置失败");
    }
}

void showGroupMemberPickerDialog(final Activity ctx, final String groupId, final String title, final String[] valueRef, final Runnable onChange) {
    hideSoftInput(ctx);
    final ProgressDialog pd = new ProgressDialog(ctx);
    pd.setMessage("加载群成员中...");
    pd.setCancelable(false);
    try {
        if (!ctx.isFinishing() && !ctx.isDestroyed()) pd.show();
    } catch (Throwable ignored) {}

    new Thread(new Runnable() {
        public void run() {
            final List allIds = new ArrayList();
            final List allNames = new ArrayList();
            final List allDisplay = new ArrayList();
            try {
                List members = getGroupMemberList(groupId);
                if (members != null) {
                    for (int i = 0; i < members.size(); i++) {
                        String wxid = String.valueOf(members.get(i));
                        if (TextUtils.isEmpty(wxid)) continue;
                        String name = "";
                        try { name = getFriendName(wxid, groupId); } catch (Throwable ignored) {}
                        if (TextUtils.isEmpty(name)) {
                            try { name = getFriendName(wxid); } catch (Throwable ignored) {}
                        }
                        if (TextUtils.isEmpty(name)) name = wxid;
                        allIds.add(wxid);
                        allNames.add(name);
                        allDisplay.add(name + " (" + wxid + ")");
                    }
                }
            } catch (Throwable ignored) {}

            new Handler(Looper.getMainLooper()).post(new Runnable() {
                public void run() {
                    try { if (pd.isShowing()) pd.dismiss(); } catch (Throwable ignored) {}
                    if (ctx.isFinishing() || ctx.isDestroyed()) return;

                    final Set selected = parseMemberRuleSet(valueRef[0]);
                    final List display = new ArrayList();
                    final List ids = new ArrayList();
                    final List names = new ArrayList();

                    final Dialog dlg = new Dialog(ctx, android.R.style.Theme_Translucent_NoTitleBar);
                    dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
                    dlg.setCancelable(true);

                    FrameLayout mask = new FrameLayout(ctx);
                    mask.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
                    mask.setBackgroundColor(Color.parseColor("#66000000"));

                    LinearLayout card = new LinearLayout(ctx);
                    card.setOrientation(LinearLayout.VERTICAL);
                    FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(-1, -2);
                    cardLp.leftMargin = dp(ctx, 16);
                    cardLp.rightMargin = dp(ctx, 16);
                    cardLp.gravity = Gravity.CENTER;
                    card.setLayoutParams(cardLp);
                    card.setPadding(dp(ctx, 16), dp(ctx, 16), dp(ctx, 16), dp(ctx, 12));

                    GradientDrawable cardBg = new GradientDrawable(
                            GradientDrawable.Orientation.TOP_BOTTOM,
                            new int[]{Color.parseColor("#FFFFFF"), Color.parseColor("#F8FAFC")}
                    );
                    cardBg.setCornerRadius(dp(ctx, 20));
                    cardBg.setStroke(dp(ctx, 1), Color.parseColor("#DDE6F2"));
                    card.setBackground(cardBg);

                    TextView tvTitle = new TextView(ctx);
                    tvTitle.setText(title);
                    tvTitle.setTextSize(18f);
                    tvTitle.setTypeface(null, Typeface.BOLD);
                    tvTitle.setTextColor(Color.parseColor("#0F172A"));
                    card.addView(tvTitle);

                    final EditText etSearch = new EditText(ctx);
                    etSearch.setHint("搜索成员昵称或 wxid");
                    etSearch.setSingleLine(true);
                    etSearch.setTextSize(14f);
                    etSearch.setTextColor(Color.parseColor("#0F172A"));
                    etSearch.setHintTextColor(Color.parseColor("#94A3B8"));
    GradientDrawable searchBg = roundRect(Color.parseColor("#F8FAFC"), dp(ctx, 12));
    searchBg.setStroke(dp(ctx, 1), Color.parseColor("#E2E8F0"));
    etSearch.setBackground(searchBg);
    etSearch.setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 12), dp(ctx, 10));
    prepareSearchInput(ctx, etSearch);
    LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(-1, -2);
                    searchLp.topMargin = dp(ctx, 12);
                    card.addView(etSearch, searchLp);

                    final TextView tvCount = new TextView(ctx);
                    tvCount.setTextSize(12f);
                    tvCount.setTextColor(Color.parseColor("#64748B"));
                    LinearLayout.LayoutParams cntLp = new LinearLayout.LayoutParams(-1, -2);
                    cntLp.topMargin = dp(ctx, 8);
                    card.addView(tvCount, cntLp);

                    LinearLayout listWrap = new LinearLayout(ctx);
                    listWrap.setOrientation(LinearLayout.VERTICAL);
                    GradientDrawable listBg = roundRect(Color.parseColor("#F8FAFC"), dp(ctx, 12));
                    listBg.setStroke(dp(ctx, 1), Color.parseColor("#E2E8F0"));
                    listWrap.setBackground(listBg);
                    LinearLayout.LayoutParams wrapLp = new LinearLayout.LayoutParams(-1, dp(ctx, 360));
                    wrapLp.topMargin = dp(ctx, 8);
                    listWrap.setLayoutParams(wrapLp);

                    final ListView lv = new ListView(ctx);
                    lv.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
                    lv.setDivider(new android.graphics.drawable.ColorDrawable(Color.parseColor("#E8EEF5")));
                    lv.setDividerHeight(1);
                    lv.setSelector(new android.graphics.drawable.ColorDrawable(Color.parseColor("#12000000")));
                    lv.setPadding(dp(ctx, 6), dp(ctx, 6), dp(ctx, 6), dp(ctx, 6));
                    lv.setClipToPadding(false);
                    lv.setVerticalScrollBarEnabled(false);
                    listWrap.addView(lv, new LinearLayout.LayoutParams(-1, -1));
                    card.addView(listWrap, wrapLp);

                    final Runnable update = new Runnable() {
                        public void run() {
                            display.clear();
                            ids.clear();
                            names.clear();
                            String kw = etSearch.getText().toString().trim().toLowerCase();
                            for (int i = 0; i < allIds.size(); i++) {
                                String id = String.valueOf(allIds.get(i));
                                String name = String.valueOf(allNames.get(i));
                                String row = String.valueOf(allDisplay.get(i));
                                String low = row.toLowerCase();
                                if (TextUtils.isEmpty(kw) || low.contains(kw)) {
                                    ids.add(id);
                                    names.add(name);
                                    display.add(row);
                                }
                            }
                            ArrayAdapter ad = new ArrayAdapter(ctx, android.R.layout.simple_list_item_multiple_choice, display);
                            lv.setAdapter(ad);
                            for (int i = 0; i < ids.size(); i++) {
                                String id = String.valueOf(ids.get(i));
                                String name = String.valueOf(names.get(i));
                                boolean checked = selected.contains(id) || selected.contains(name);
                                lv.setItemChecked(i, checked);
                            }
                            tvCount.setText("已选 " + selected.size() + " / 共 " + allIds.size() + " 人");
                        }
                    };

                    lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                        public void onItemClick(AdapterView parent, View view, int position, long id) {
                            String wxid = String.valueOf(ids.get(position));
                            String name = String.valueOf(names.get(position));
                            if (lv.isItemChecked(position)) {
                                selected.add(wxid);
                                selected.remove(name);
                            } else {
                                selected.remove(wxid);
                                selected.remove(name);
                            }
                            tvCount.setText("已选 " + selected.size() + " / 共 " + allIds.size() + " 人");
                        }
                    });

                    etSearch.addTextChangedListener(new TextWatcher() {
                        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                        public void onTextChanged(CharSequence s, int start, int before, int count) {}
                        public void afterTextChanged(Editable s) { update.run(); }
                    });

                    LinearLayout actions = new LinearLayout(ctx);
                    actions.setGravity(Gravity.END);
                    LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(-1, -2);
                    actionsLp.topMargin = dp(ctx, 12);
                    actions.setLayoutParams(actionsLp);

                    TextView btnClear = new TextView(ctx);
                    btnClear.setText("清空");
                    btnClear.setTextColor(Color.parseColor("#334155"));
                    btnClear.setTextSize(14f);
                    btnClear.setPadding(dp(ctx, 14), dp(ctx, 8), dp(ctx, 14), dp(ctx, 8));
                    GradientDrawable clearBg = roundRect(Color.parseColor("#EFF3F8"), dp(ctx, 999));
                    clearBg.setStroke(dp(ctx, 1), Color.parseColor("#DEE7F2"));
                    btnClear.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.parseColor("#22000000")), clearBg, null));

                    TextView btnCancel = new TextView(ctx);
                    btnCancel.setText("取消");
                    btnCancel.setTextColor(Color.parseColor("#334155"));
                    btnCancel.setTextSize(14f);
                    btnCancel.setPadding(dp(ctx, 14), dp(ctx, 8), dp(ctx, 14), dp(ctx, 8));
                    GradientDrawable cancelBg = roundRect(Color.parseColor("#EFF3F8"), dp(ctx, 999));
                    cancelBg.setStroke(dp(ctx, 1), Color.parseColor("#DEE7F2"));
                    btnCancel.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.parseColor("#22000000")), cancelBg, null));

                    TextView btnSave = new TextView(ctx);
                    btnSave.setText("确定");
                    btnSave.setTextColor(Color.WHITE);
                    btnSave.setTextSize(14f);
                    btnSave.setTypeface(null, Typeface.BOLD);
                    btnSave.setPadding(dp(ctx, 18), dp(ctx, 8), dp(ctx, 18), dp(ctx, 8));
                    GradientDrawable saveBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Color.parseColor("#2563EB"), Color.parseColor("#7C3AED")});
                    saveBg.setCornerRadius(dp(ctx, 999));
                    btnSave.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.parseColor("#55FFFFFF")), saveBg, null));

                    LinearLayout.LayoutParams lpBtn = new LinearLayout.LayoutParams(-2, -2);
                    lpBtn.leftMargin = dp(ctx, 10);
                    actions.addView(btnClear);
                    actions.addView(btnCancel, lpBtn);
                    actions.addView(btnSave, lpBtn);
                    card.addView(actions);

                    mask.addView(card);
                    dlg.setContentView(mask);

                    mask.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { dlg.dismiss(); } });
                    card.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {} });
                    btnCancel.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { dlg.dismiss(); } });
                    btnClear.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) {
                            valueRef[0] = "";
                            if (onChange != null) onChange.run();
                            dlg.dismiss();
                        }
                    });
                    btnSave.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) {
                            valueRef[0] = joinMemberRuleSet(selected);
                            if (onChange != null) onChange.run();
                            dlg.dismiss();
                        }
                    });

                    Window w = dlg.getWindow();
                    if (w != null) {
                        w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
                        w.setGravity(Gravity.CENTER);
                        w.setDimAmount(0.25f);
                        w.clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
                        w.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
                        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
                    }

                    dlg.show();
                    etSearch.postDelayed(new Runnable() {
                        public void run() { etSearch.requestFocus(); }
                    }, 120);
                    card.setAlpha(0f);
                    card.setTranslationY(dp(ctx, 20));
                    card.animate().alpha(1f).translationY(0).setDuration(180).start();
                    update.run();
                }
            });
        }
    }).start();
}

void addDarkDivider(Activity ctx, ViewGroup parent) {
    View v = new View(ctx);
    v.setBackgroundColor(Color.parseColor("#E8EEF5"));
    parent.addView(v, new LinearLayout.LayoutParams(-1, 1));
}

void addDarkSwitchRow(Activity ctx, LinearLayout parent, String title, boolean checked, CompoundButton.OnCheckedChangeListener listener) {
    LinearLayout row = new LinearLayout(ctx);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setPadding(dp(ctx, 14), dp(ctx, 14), dp(ctx, 14), dp(ctx, 14));
    row.setGravity(Gravity.CENTER_VERTICAL);

    TextView tv = new TextView(ctx);
    tv.setText(title);
    tv.setTextSize(16f);
    tv.setTextColor(Color.parseColor("#0F172A"));
    row.addView(tv, new LinearLayout.LayoutParams(0, -2, 1));

    final Switch s = new Switch(ctx);
    s.setChecked(checked);
    s.setOnCheckedChangeListener(listener);
    row.addView(s);
    parent.addView(row);
}

TextView addDarkClickRow(Activity ctx, LinearLayout parent, String title, String right) {
    LinearLayout row = new LinearLayout(ctx);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setPadding(dp(ctx, 14), dp(ctx, 14), dp(ctx, 14), dp(ctx, 14));
    row.setGravity(Gravity.CENTER_VERTICAL);

    TextView l = new TextView(ctx);
    l.setText(title);
    l.setTextSize(16f);
    l.setTextColor(Color.parseColor("#0F172A"));
    LinearLayout.LayoutParams lLp = new LinearLayout.LayoutParams(-2, -2);
    row.addView(l, lLp);

    TextView r = new TextView(ctx);
    r.setText(right);
    r.setTextSize(14f);
    r.setTextColor(Color.parseColor("#2563EB"));
    r.setSingleLine(true);
    r.setEllipsize(TextUtils.TruncateAt.END);
    r.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
    LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(0, -2, 1);
    rLp.leftMargin = dp(ctx, 10);
    row.addView(r, rLp);

    parent.addView(row);
    return r;
}

void buildListUI(final Activity ctx, final List fNames, final List fIds, final List gNames, final List gIds) {
    LinearLayout root = new LinearLayout(ctx);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(dp(ctx, 12), dp(ctx, 16), dp(ctx, 12), dp(ctx, 12));

    GradientDrawable rootBg = roundRect(Color.parseColor("#FCFCFD"), dp(ctx, 22));
    rootBg.setStroke(dp(ctx, 1), Color.parseColor("#E6EAF0"));
    root.setBackground(rootBg);

    TextView tvTitle = new TextView(ctx);
    tvTitle.setText("选择会话");
    tvTitle.setTextSize(22f);
    tvTitle.setTypeface(null, Typeface.BOLD);
    tvTitle.setTextColor(Color.parseColor("#0F172A"));
    root.addView(tvTitle);

    final RadioGroup rg = new RadioGroup(ctx);
    rg.setOrientation(RadioGroup.HORIZONTAL);
    rg.setGravity(Gravity.CENTER);
    LinearLayout.LayoutParams rgLp = new LinearLayout.LayoutParams(-1, -2);
    rgLp.topMargin = dp(ctx, 14);
    rg.setLayoutParams(rgLp);

    RadioButton rbFriend = new RadioButton(ctx);
    rbFriend.setText("好友");
    rbFriend.setId(1);
    rbFriend.setTextSize(16f);

    RadioButton rbGroup = new RadioButton(ctx);
    rbGroup.setText("群聊");
    rbGroup.setId(2);
    rbGroup.setTextSize(16f);

    RadioButton rbAll = new RadioButton(ctx);
    rbAll.setText("全部");
    rbAll.setId(3);
    rbAll.setTextSize(16f);

    rg.addView(rbFriend);
    rg.addView(rbGroup);
    rg.addView(rbAll);
    root.addView(rg);

    final EditText etSearch = new EditText(ctx);
    etSearch.setHint("搜索会话");
    etSearch.setTextSize(15f);
    etSearch.setTextColor(Color.parseColor("#334155"));
    etSearch.setHintTextColor(Color.parseColor("#A3AEC0"));
    GradientDrawable searchBg = roundRect(Color.parseColor("#F6F8FB"), dp(ctx, 999));
    searchBg.setStroke(dp(ctx, 1), Color.parseColor("#E5E9F0"));
    etSearch.setBackground(searchBg);
    etSearch.setPadding(dp(ctx, 16), dp(ctx, 10), dp(ctx, 16), dp(ctx, 10));
    prepareSearchInput(ctx, etSearch);
    LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(-1, -2);
    searchLp.topMargin = dp(ctx, 12);
    root.addView(etSearch, searchLp);

    FrameLayout listWrap = new FrameLayout(ctx);
    GradientDrawable listWrapBg = roundRect(Color.parseColor("#F5F7FB"), dp(ctx, 12));
    listWrapBg.setStroke(dp(ctx, 1), Color.parseColor("#EEF2F7"));
    listWrap.setBackground(listWrapBg);
    LinearLayout.LayoutParams wrapLp = new LinearLayout.LayoutParams(-1, 0, 1f);
    wrapLp.topMargin = dp(ctx, 12);

    final ListView lv = new ListView(ctx);
    lv.setChoiceMode(ListView.CHOICE_MODE_NONE);
    lv.setDivider(new android.graphics.drawable.ColorDrawable(Color.parseColor("#E9EDF3")));
    lv.setDividerHeight(1);
    lv.setSelector(new android.graphics.drawable.ColorDrawable(Color.parseColor("#12000000")));
    lv.setPadding(dp(ctx, 8), dp(ctx, 8), dp(ctx, 8), dp(ctx, 8));
    lv.setClipToPadding(false);
    lv.setVerticalScrollBarEnabled(false);
    listWrap.addView(lv, new FrameLayout.LayoutParams(-1, -1));
    root.addView(listWrap, wrapLp);

    LinearLayout actions = new LinearLayout(ctx);
    actions.setOrientation(LinearLayout.HORIZONTAL);
    actions.setGravity(Gravity.END);
    LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(-1, -2);
    actionLp.topMargin = dp(ctx, 14);
    actions.setLayoutParams(actionLp);

    final TextView tvCancel = new TextView(ctx);
    tvCancel.setText("取消");
    tvCancel.setTextSize(16f);
    tvCancel.setTextColor(Color.parseColor("#334155"));
    tvCancel.setPadding(dp(ctx, 20), dp(ctx, 10), dp(ctx, 20), dp(ctx, 10));
    GradientDrawable cancelBg = roundRect(Color.parseColor("#EDF1F6"), dp(ctx, 999));
    cancelBg.setStroke(dp(ctx, 1), Color.parseColor("#DDE3EC"));
    tvCancel.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.parseColor("#22000000")), cancelBg, null));
    actions.addView(tvCancel);

    final TextView tvOk = new TextView(ctx);
    tvOk.setText("完成");
    tvOk.setTextSize(16f);
    tvOk.setTypeface(null, Typeface.BOLD);
    tvOk.setTextColor(Color.WHITE);
    tvOk.setPadding(dp(ctx, 24), dp(ctx, 10), dp(ctx, 24), dp(ctx, 10));
    GradientDrawable okBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Color.parseColor("#2E64F0"), Color.parseColor("#6F3BDE")});
    okBg.setCornerRadius(dp(ctx, 999));
    tvOk.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.parseColor("#55FFFFFF")), okBg, null));
    LinearLayout.LayoutParams okLp = new LinearLayout.LayoutParams(-2, -2);
    okLp.leftMargin = dp(ctx, 10);
    actions.addView(tvOk, okLp);
    root.addView(actions);

    final String existStr = getString(CFG_TARGETS, "");
    final Set selectedIds = new HashSet();
    if (!TextUtils.isEmpty(existStr)) {
        String[] parts = existStr.split(",");
        for (int i = 0; i < parts.length; i++) {
            selectedIds.add(parts[i].trim());
        }
    }

    final List fNameStr = new ArrayList();
    final List fNameLower = new ArrayList();
    final List fIdStr = new ArrayList();
    for (int i = 0; i < fNames.size(); i++) {
        String n = String.valueOf(fNames.get(i));
        String id = String.valueOf(fIds.get(i));
        fNameStr.add(n);
        fNameLower.add(n.toLowerCase());
        fIdStr.add(id);
    }

    final List gNameStr = new ArrayList();
    final List gNameLower = new ArrayList();
    final List gIdStr = new ArrayList();
    for (int i = 0; i < gNames.size(); i++) {
        String n = String.valueOf(gNames.get(i));
        String id = String.valueOf(gIds.get(i));
        gNameStr.add(n);
        gNameLower.add(n.toLowerCase());
        gIdStr.add(id);
    }

    final List currentDisplayNames = new ArrayList();
    final List currentDisplayIds = new ArrayList();
    final List currentDisplayIsGroup = new ArrayList();

    final TextView tvLoading = new TextView(ctx);
    tvLoading.setText("正在加载会话...");
    tvLoading.setTextSize(12f);
    tvLoading.setTextColor(Color.parseColor("#64748B"));
    tvLoading.setPadding(dp(ctx, 12), dp(ctx, 6), dp(ctx, 12), dp(ctx, 6));
    tvLoading.setVisibility(View.GONE);
    root.addView(tvLoading);

    final ArrayAdapter adapter = new ArrayAdapter(ctx, android.R.layout.simple_list_item_1, currentDisplayNames);
    lv.setAdapter(adapter);
    try { lv.setFastScrollEnabled(true); } catch (Throwable ignored) {}

    final Handler uiHandler = new Handler(Looper.getMainLooper());
    final Runnable[] pendingSearch = new Runnable[1];
    final int[] filterVersion = new int[]{0};

    final Runnable updateList = new Runnable() {
        public void run() {
            final String kw = etSearch.getText().toString().toLowerCase();
            final int checkedId = rg.getCheckedRadioButtonId();
            final int version = ++filterVersion[0];

            // 首屏空关键字时先给一批预览，避免“空白2~3秒”
            boolean isEmptyKw = TextUtils.isEmpty(kw);
            if (isEmptyKw) {
                currentDisplayNames.clear();
                currentDisplayIds.clear();
                currentDisplayIsGroup.clear();

                int previewLimit = 120;
                int added = 0;

                if (checkedId == 1 || checkedId == 3) {
                    for (int i = 0; i < fNameStr.size() && added < previewLimit; i++) {
                        String name = String.valueOf(fNameStr.get(i));
                        String talkerId = String.valueOf(fIdStr.get(i));
                        currentDisplayNames.add(selectedIds.contains(talkerId) ? (name + "  [已配置]") : name);
                        currentDisplayIds.add(talkerId);
                        currentDisplayIsGroup.add(Boolean.FALSE);
                        added++;
                    }
                }
                if ((checkedId == 2 || checkedId == 3) && added < previewLimit) {
                    for (int i = 0; i < gNameStr.size() && added < previewLimit; i++) {
                        String name = String.valueOf(gNameStr.get(i));
                        String talkerId = String.valueOf(gIdStr.get(i));
                        currentDisplayNames.add(selectedIds.contains(talkerId) ? (name + "  [已配置]") : name);
                        currentDisplayIds.add(talkerId);
                        currentDisplayIsGroup.add(Boolean.TRUE);
                        added++;
                    }
                }
                sortConfiguredFirst(currentDisplayNames, currentDisplayIds, currentDisplayIsGroup, selectedIds);
                adapter.notifyDataSetChanged();
            }

            tvLoading.setVisibility(View.VISIBLE);
            tvLoading.setText("正在加载会话...");

            new Thread(new Runnable() {
                public void run() {
                    final List tmpNames = new ArrayList();
                    final List tmpIds = new ArrayList();
                    final List tmpIsGroup = new ArrayList();

                    if (checkedId == 1 || checkedId == 3) {
                        for (int i = 0; i < fNameStr.size(); i++) {
                            String nameLower = String.valueOf(fNameLower.get(i));
                            if (kw.isEmpty() || nameLower.contains(kw)) {
                                String name = String.valueOf(fNameStr.get(i));
                                String talkerId = String.valueOf(fIdStr.get(i));
                                tmpNames.add(selectedIds.contains(talkerId) ? (name + "  [已配置]") : name);
                                tmpIds.add(talkerId);
                                tmpIsGroup.add(Boolean.FALSE);
                            }
                        }
                    }
                    if (checkedId == 2 || checkedId == 3) {
                        for (int i = 0; i < gNameStr.size(); i++) {
                            String nameLower = String.valueOf(gNameLower.get(i));
                            if (kw.isEmpty() || nameLower.contains(kw)) {
                                String name = String.valueOf(gNameStr.get(i));
                                String talkerId = String.valueOf(gIdStr.get(i));
                                tmpNames.add(selectedIds.contains(talkerId) ? (name + "  [已配置]") : name);
                                tmpIds.add(talkerId);
                                tmpIsGroup.add(Boolean.TRUE);
                            }
                        }
                    }
                    sortConfiguredFirst(tmpNames, tmpIds, tmpIsGroup, selectedIds);

                    uiHandler.post(new Runnable() {
                        public void run() {
                            if (version != filterVersion[0]) return;
                            if (ctx.isFinishing() || ctx.isDestroyed()) return;

                            currentDisplayNames.clear();
                            currentDisplayIds.clear();
                            currentDisplayIsGroup.clear();

                            currentDisplayNames.addAll(tmpNames);
                            currentDisplayIds.addAll(tmpIds);
                            currentDisplayIsGroup.addAll(tmpIsGroup);
                            adapter.notifyDataSetChanged();

                            tvLoading.setVisibility(View.GONE);
                        }
                    });
                }
            }).start();
        }
    };
    rg.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
        public void onCheckedChanged(RadioGroup group, int checkedId) { updateList.run(); }
    });

    etSearch.addTextChangedListener(new TextWatcher() {
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        public void onTextChanged(CharSequence s, int start, int before, int count) {}
        public void afterTextChanged(Editable s) {
            if (pendingSearch[0] != null) uiHandler.removeCallbacks(pendingSearch[0]);
            pendingSearch[0] = new Runnable() {
                public void run() { updateList.run(); }
            };
            uiHandler.postDelayed(pendingSearch[0], 60);
        }
    });

    lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView parent, View view, int position, long id) {
            final String talkerId = String.valueOf(currentDisplayIds.get(position));
            final boolean isGroup = ((Boolean) currentDisplayIsGroup.get(position)).booleanValue();
            final String displayName = String.valueOf(currentDisplayNames.get(position));
            showTalkerConfigDialog(ctx, talkerId, isGroup, displayName, selectedIds, new Runnable() {
                public void run() { updateList.run(); }
            });
        }
    });

    final AlertDialog listDialog = new AlertDialog.Builder(ctx).create();
    listDialog.show();
    listDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
    if (listDialog.getWindow() != null) {
        listDialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        listDialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        listDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }
    listDialog.setContentView(root);
    applyDialogSize(listDialog, 0.96f, 0.90f);
    etSearch.clearFocus();
    try {
        if (listDialog.getWindow() != null) {
            listDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    } catch (Throwable ignored) {}

    tvCancel.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) { listDialog.dismiss(); }
    });
    tvOk.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            listDialog.dismiss();
            loadConfigToCache();
            toast("会话配置已更新");
        }
    });
    rbAll.setChecked(true);
    updateList.run();
}
void sortConfiguredFirst(List names, List ids, List isGroups, Set selectedIds) {
    if (names == null || ids == null || isGroups == null || selectedIds == null) return;
    int n = ids.size();
    if (n <= 1 || selectedIds.isEmpty()) return;
    if (names.size() != n || isGroups.size() != n) return;

    List idxConfigured = new ArrayList();
    List idxUnconfigured = new ArrayList();
    for (int i = 0; i < n; i++) {
        String talkerId = String.valueOf(ids.get(i));
        if (selectedIds.contains(talkerId)) idxConfigured.add(Integer.valueOf(i));
        else idxUnconfigured.add(Integer.valueOf(i));
    }
    if (idxConfigured.isEmpty() || idxUnconfigured.isEmpty()) return;

    List sortedNames = new ArrayList(n);
    List sortedIds = new ArrayList(n);
    List sortedIsGroups = new ArrayList(n);

    for (int i = 0; i < idxConfigured.size(); i++) {
        int idx = ((Integer) idxConfigured.get(i)).intValue();
        sortedNames.add(names.get(idx));
        sortedIds.add(ids.get(idx));
        sortedIsGroups.add(isGroups.get(idx));
    }
    for (int i = 0; i < idxUnconfigured.size(); i++) {
        int idx = ((Integer) idxUnconfigured.get(i)).intValue();
        sortedNames.add(names.get(idx));
        sortedIds.add(ids.get(idx));
        sortedIsGroups.add(isGroups.get(idx));
    }

    names.clear();
    ids.clear();
    isGroups.clear();
    names.addAll(sortedNames);
    ids.addAll(sortedIds);
    isGroups.addAll(sortedIsGroups);
}
