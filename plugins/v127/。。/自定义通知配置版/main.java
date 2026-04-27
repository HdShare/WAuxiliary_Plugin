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
import java.util.concurrent.CopyOnWriteArrayList;
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
String CFG_MODE = "jay_cfg_mode_v7";
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

// ================= 内存缓存 =================
int cacheMode = 1;
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

// 【安全优化2】采用线程安全的 CopyOnWriteArrayList，防止读写并发导致微信崩溃
List targetNameList = new CopyOnWriteArrayList();

// 全局联系人缓存 (提速)
List sCachedFriendNames = null;
List sCachedFriendIds = null;
List sCachedGroupNames = null;
List sCachedGroupIds = null;

// ================= 生命周期 =================
void onLoad() {
    loadConfigToCache();
    hookSystemNotification();
    hookActivityResultForRingtone();
    registerQuickReplyReceiver();
}

void onUnLoad() {
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
    cacheTargetSet.clear();
    targetNameList.clear();
    cacheTalkerCfgMap.clear();
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
    cacheMode = getInt(CFG_MODE, 1);
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
        cacheTalkerCfgMap.put(talkerId, parseTalkerCfg(rawCfg));
    }
    
    String sTag = cacheSound ? "S" : "N";
    String vTag = cacheVibrate ? "V" : "N";
    currentChannelId = "jay_chn_v9_" + sTag + "_" + vTag;
    rebuildNotificationChannel();
    
    new Thread(new Runnable() {
        public void run() {
            try {
                if (cacheTargetSet.isEmpty()) return;
                List tempNames = new ArrayList();
                Object[] ids = cacheTargetSet.toArray();
                for (int i = 0; i < ids.length; i++) {
                    String id = (String) ids[i];
                    String name = resolveTalkerNameForMatch(id);
                    if (!TextUtils.isEmpty(name)) tempNames.add(name.trim());
                }
                targetNameList.clear();
                targetNameList.addAll(tempNames); // 线程安全写入
            } catch (Throwable ignored) {}
        }
    }).start();
}

void registerQuickReplyReceiver() {
    try {
        if (quickReplyReceiverRegistered) return;
        quickReplyReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                try {
                    if (intent == null) return;
                    if (!ACTION_QUICK_REPLY.equals(intent.getAction())) return;
                    String talker = intent.getStringExtra(EXTRA_TALKER);
                    if (TextUtils.isEmpty(talker)) return;
                    Bundle results = RemoteInput.getResultsFromIntent(intent);
                    CharSequence cs = null;
                    if (results != null) cs = results.getCharSequence("key_reply_content");
                    if (cs == null) cs = intent.getCharSequenceExtra("key_reply_content");
                    String reply = cs == null ? "" : cs.toString().trim();
                    if (TextUtils.isEmpty(reply)) return;
                    sendText(talker, reply);
                    NotificationManager nm = (NotificationManager) hostContext.getSystemService(Context.NOTIFICATION_SERVICE);
                    if (nm != null) nm.cancel(talker.hashCode());
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
    return new HashMap();
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
    return String.format(Locale.getDefault(), "%02d:%02d", h, m);
}

boolean isNowInMuteWindow() {
    if (!cacheMuteTimeEnable) return false;
    int startMinute = parseTimeToMinute(cacheMuteTimeStart);
    int endMinute = parseTimeToMinute(cacheMuteTimeEnd);
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

String[] extractGroupSenderInfo(Object msg, String content) {
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
            String n = getFriendName(senderId);
            if (!TextUtils.isEmpty(n) && !senderId.equals(n)) senderName = n.trim();
        }
    } catch (Throwable ignored) {}
    if (TextUtils.isEmpty(senderName)) senderName = senderId;
    return new String[]{senderId, senderName, pureContent};
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
        if (!TextUtils.isEmpty(sc) && sc.startsWith(lk + ":")) return true;
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
            String[] sender = extractGroupSenderInfo(msg, content);
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
            if (!TextUtils.isEmpty(title)) return title;
        }
    } catch (Throwable ignored) {}
    return "自定义铃声";
}

String getFriendDisplayNameById(String wxid) {
    if (TextUtils.isEmpty(wxid)) return "";
    String n = getFriendName(wxid);
    if (TextUtils.isEmpty(n)) return wxid;
    return n + " (" + wxid + ")";
}

void putInt(String key, int v) {
    putString(key, String.valueOf(v));
}

// ================= 核心 1：原生通知强力拦截器 =================
void hookSystemNotification() {
    try {
        Method[] methods = NotificationManager.class.getDeclaredMethods();
        for (int i = 0; i < methods.length; i++) {
            final Method m = methods[i];
            if (!"notify".equals(m.getName())) continue;

            Class[] ps = m.getParameterTypes();
            if (ps == null || ps.length == 0 || ps[ps.length - 1] != Notification.class) continue;

            Object unhook = XposedBridge.hookMethod(m, new XC_MethodHook() {
                protected void beforeHookedMethod(de.robv.android.xposed.XC_MethodHook.MethodHookParam param) throws Throwable {
                    try {
                        if (cacheTargetSet.isEmpty()) return;

                        Object[] args = param.args;
                        if (args == null || args.length == 0) return;
                        Notification n = (Notification) args[args.length - 1];
                        if (n == null) return;

                        if (n.extras != null && n.extras.getBoolean(JAY_MARK, false)) return;
                        if (Build.VERSION.SDK_INT >= 26 && n.getChannelId() != null && n.getChannelId().startsWith("jay_chn_v9_")) {
                            return;
                        }
                        String talker = extractTalkerFromNotification(n);
                        boolean shouldBlock = false;

                        // 【逻辑优化】使用 Set 的精确匹配
                        if (!TextUtils.isEmpty(talker) && cacheTargetSet.contains(talker)) {
                            shouldBlock = true;
                        } else {
                            String title = "";
                            if (n.extras != null) {
                                CharSequence cs = n.extras.getCharSequence(Notification.EXTRA_TITLE);
                                if (cs != null) title = cs.toString().trim();
                            }
                            if (!TextUtils.isEmpty(title)) {
                                for (int k = 0; k < targetNameList.size(); k++) {
                                    String tName = (String) targetNameList.get(k);
                                    if (title.contains(tName)) {
                                        shouldBlock = true;
                                        break;
                                    }
                                }
                            }
                        }

                        if (shouldBlock) param.setResult(null); 

                    } catch (Throwable ignored) {}
                }
            });
            if (unhook != null) notifyUnhooks.add(unhook);
        }
    } catch (Throwable ignored) {}
}

// ================= 核心 2：自定义通知发送器 =================
void onHandleMsg(Object msg) {
    if (cacheTargetSet.isEmpty()) return; 

    try {
        boolean isSend = (boolean) safeInvoke(msg, "isSend");
        if (isSend) return;

        String talker = (String) safeInvoke(msg, "getTalker");
        
        // 【逻辑优化】使用 Set 的精确匹配
        if (!cacheTargetSet.contains(talker)) return;
        Map cfg = getTalkerCfg(talker);
        int talkerMode = cfgGetInt(cfg, "mode", cacheMode);
        if (talkerMode == 0) return;
        if (isNowInMuteWindowByCfg(cfg)) return;

        String content = (String) safeInvoke(msg, "getContent");
        if (shouldSuppressByRules(msg, talker, content, cfg)) return;
        Object typeObj = safeInvoke(msg, "getType");
int type = (typeObj instanceof Number) ? ((Number) typeObj).intValue() : 1;

        String senderName = getFriendName(talker);
        String displayContent = "[收到一条新消息]";
        boolean showDetail = cfgGetBool(cfg, "showDetail", cacheShowDetail);
        boolean talkerVibrate = cfgGetBool(cfg, "vibrate", cacheVibrate);
        boolean talkerSound = cfgGetBool(cfg, "sound", cacheSound);
        String talkerRingtone = cfgGet(cfg, "ringtone", "");
        boolean talkerQuickReply = cfgGetBool(cfg, "quickReply", false);
        if (showDetail) {
            if (type == 1) displayContent = content;
            else displayContent = "[图片/语音或非文本消息]";
        }

        sendCustomNotification(talker, senderName, displayContent, talkerVibrate, talkerSound, talkerRingtone, talkerQuickReply);
    } catch (Throwable ignored) {}
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

    new Handler(Looper.getMainLooper()).post(new Runnable() {
        public void run() {
            try {
                Uri uri = Uri.parse(uriStr);
                final android.media.Ringtone rt = RingtoneManager.getRingtone(hostContext, uri);
                if (rt == null) return;
                try { rt.setStreamType(android.media.AudioManager.STREAM_NOTIFICATION); } catch (Throwable ignored) {}
                rt.play();
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
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

void sendCustomNotification(String talker, String title, String text, boolean useVibrate, boolean useSound, String ringtoneUri, boolean enableQuickReply) {
    try {
        NotificationManager nm = (NotificationManager) hostContext.getSystemService(Context.NOTIFICATION_SERVICE);
        Notification.Builder builder;
        String talkerChannelId = currentChannelId;
        String talkerRing = TextUtils.isEmpty(ringtoneUri) ? "" : ringtoneUri;
        boolean useManualCustomSound = Build.VERSION.SDK_INT >= 26 && useSound && !TextUtils.isEmpty(talkerRing);
        if (Build.VERSION.SDK_INT >= 26) {
            String sTag = useSound ? (useManualCustomSound ? "M" : "S") : "N";
            String vTag = useVibrate ? "V" : "N";
            talkerChannelId = "jay_chn_v9_" + sTag + "_" + vTag + "_" + talkerRing.hashCode();
            ensureNotifyChannel(nm, talkerChannelId, useVibrate, useManualCustomSound ? false : useSound, talkerRing);
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

        Uri soundUri = resolveNotifySoundUri(useManualCustomSound ? false : useSound, talkerRing);
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

        Intent intent = hostContext.getPackageManager().getLaunchIntentForPackage(hostContext.getPackageName());
        if (intent != null) {
            builder.setContentIntent(PendingIntent.getActivity(hostContext, talker.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
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

        if (useManualCustomSound) playCustomRingtoneFallback(talkerRing);
    } catch (Throwable ignored) {}
}

void hookActivityResultForRingtone() {
    try {
        XC_MethodHook resultHook = new XC_MethodHook() {
            protected void beforeHookedMethod(de.robv.android.xposed.XC_MethodHook.MethodHookParam param) throws Throwable {
                int requestCode = (Integer) param.args[0];
                if (requestCode == 10086) {
                    Intent data = (Intent) param.args[2];
                    Uri uri = null;
                    if (data != null) {
                        try { uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI); } catch (Throwable ignored) {}
                    }
                    String ring = uri == null ? "" : uri.toString();
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
    return null;
}
Object safeInvoke(Object obj, String methodName) {
    if (obj == null || TextUtils.isEmpty(methodName)) return null;
    try {
        Method[] methods = obj.getClass().getMethods();
        for (int i = 0; i < methods.length; i++) {
            Method m = methods[i];
            if (m.getName().equals(methodName) && m.getParameterTypes().length == 0) {
                return m.invoke(obj);
            }
        }
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
                        friendNames.add(TextUtils.isEmpty(f.getNickname()) ? "未知" : f.getNickname());
                        friendIds.add(f.getWxid());
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
void showTimeRangeDialog(final Activity ctx, final String[] startRef, final String[] endRef, final Runnable onChange) {
    int st = parseTimeToMinute(startRef[0]);
    int et = parseTimeToMinute(endRef[0]);
    final int sh = st >= 0 ? st / 60 : 23;
    final int sm = st >= 0 ? st % 60 : 0;
    final int eh = et >= 0 ? et / 60 : 7;
    final int em = et >= 0 ? et % 60 : 0;

    TimePickerDialog pickStart = new TimePickerDialog(ctx, new TimePickerDialog.OnTimeSetListener() {
        public void onTimeSet(android.widget.TimePicker view, final int hourOfDay, final int minute) {
            TimePickerDialog pickEnd = new TimePickerDialog(ctx, new TimePickerDialog.OnTimeSetListener() {
                public void onTimeSet(android.widget.TimePicker view2, int hourOfDay2, int minute2) {
                    startRef[0] = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute);
                    endRef[0] = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay2, minute2);
                    if (onChange != null) onChange.run();
                }
            }, eh, em, true);
            pickEnd.setTitle("选择结束时间");
            pickEnd.show();
        }
    }, sh, sm, true);
    pickStart.setTitle("选择开始时间");
    pickStart.show();
}

void showSingleTimePicker(final Activity ctx, String title, final String[] valueRef, final Runnable onChange) {
    int t = parseTimeToMinute(valueRef[0]);
    int h = t >= 0 ? t / 60 : 0;
    int m = t >= 0 ? t % 60 : 0;
    TimePickerDialog d = new TimePickerDialog(ctx, new TimePickerDialog.OnTimeSetListener() {
        public void onTimeSet(android.widget.TimePicker view, int hourOfDay, int minute) {
            valueRef[0] = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute);
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
        d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
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

void styleTabChip(TextView tv, boolean active) {
    if (tv == null) return;
    GradientDrawable bg = new GradientDrawable();
    bg.setCornerRadius(999);
    if (active) {
        bg.setColor(Color.parseColor("#2563EB"));
        tv.setTextColor(Color.WHITE);
        tv.getPaint().setFakeBoldText(true);
    } else {
        bg.setColor(Color.parseColor("#EEF2F7"));
        bg.setStroke(1, Color.parseColor("#D7E0EA"));
        tv.setTextColor(Color.parseColor("#475569"));
        tv.getPaint().setFakeBoldText(false);
    }
    tv.setBackground(bg);
}
void showTalkerConfigDialog(final Activity ctx, final String talkerId, final boolean isGroup, final String displayNameRaw, final Set selectedIds, final Runnable onSaved) {
    hideSoftInput(ctx);
    final String displayName = displayNameRaw.replace("  ✓", "").replace("  [已配置]", "").replace("  [未配置]", "");

    Map oldCfg = parseTalkerCfg(getString(CFG_TALKER_CFG_PREFIX + talkerId, ""));
    final boolean[] tmpEnable = {selectedIds.contains(talkerId)};
    final int[] tmpMode = {cfgGetInt(oldCfg, "mode", cacheMode)};
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

        addDarkSwitchRow(ctx, body, "免打扰(不弹通知)", tmpMode[0] == 0, new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean c) { tmpMode[0] = c ? 0 : 1; }
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
                Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
                if (!TextUtils.isEmpty(tmpRingtone[0])) intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(tmpRingtone[0]));
                ctx.startActivityForResult(intent, 10086);
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
                newCfg.put("mode", String.valueOf(tmpMode[0]));
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

void showMemberRuleInputDialog(final Activity ctx, String title, String hint, final String[] valueRef, final Runnable onChange) {
    final EditText et = new EditText(ctx);
    et.setHint(hint);
    et.setMinLines(3);
    et.setText(TextUtils.isEmpty(valueRef[0]) ? "" : valueRef[0]);
    et.setSelection(et.getText().length());
    et.setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 12), dp(ctx, 10));

    new AlertDialog.Builder(ctx)
            .setTitle(title)
            .setMessage("示例：wxid_aaa,张三,李四")
            .setView(et)
            .setNegativeButton("取消", null)
            .setNeutralButton("清空", new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface dialog, int which) {
                    valueRef[0] = "";
                    if (onChange != null) onChange.run();
                }
            })
            .setPositiveButton("确定", new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface dialog, int which) {
                    valueRef[0] = joinMemberRuleSet(parseMemberRuleSet(et.getText().toString()));
                    if (onChange != null) onChange.run();
                }
            }).show();
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
                        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
                    }

                    dlg.show();
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

    Switch s = new Switch(ctx);
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
    row.addView(l, new LinearLayout.LayoutParams(0, -2, 1));

    TextView r = new TextView(ctx);
    r.setText(right);
    r.setTextSize(14f);
    r.setTextColor(Color.parseColor("#2563EB"));
    row.addView(r);

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

    final List currentDisplayNames = new ArrayList();
    final List currentDisplayIds = new ArrayList();
    final List currentDisplayIsGroup = new ArrayList();

    final Runnable updateList = new Runnable() {
        public void run() {
            currentDisplayNames.clear();
            currentDisplayIds.clear();
            currentDisplayIsGroup.clear();
            String kw = etSearch.getText().toString().toLowerCase();
            int checkedId = rg.getCheckedRadioButtonId();

            if (checkedId == 1 || checkedId == 3) {
                for (int i = 0; i < fNames.size(); i++) {
                    String name = String.valueOf(fNames.get(i));
                    if (kw.isEmpty() || name.toLowerCase().contains(kw)) {
                        String talkerId = String.valueOf(fIds.get(i));
                        currentDisplayNames.add(selectedIds.contains(talkerId) ? (name + "  [已配置]") : name);
                        currentDisplayIds.add(talkerId);
                        currentDisplayIsGroup.add(Boolean.FALSE);
                    }
                }
            }
            if (checkedId == 2 || checkedId == 3) {
                for (int i = 0; i < gNames.size(); i++) {
                    String name = String.valueOf(gNames.get(i));
                    if (kw.isEmpty() || name.toLowerCase().contains(kw)) {
                        String talkerId = String.valueOf(gIds.get(i));
                        currentDisplayNames.add(selectedIds.contains(talkerId) ? (name + "  [已配置]") : name);
                        currentDisplayIds.add(talkerId);
                        currentDisplayIsGroup.add(Boolean.TRUE);
                    }
                }
            }

            ArrayAdapter adapter = new ArrayAdapter(ctx, android.R.layout.simple_list_item_1, currentDisplayNames);
            lv.setAdapter(adapter);
        }
    };
    rg.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
        public void onCheckedChanged(RadioGroup group, int checkedId) { updateList.run(); }
    });

    etSearch.addTextChangedListener(new TextWatcher() {
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        public void onTextChanged(CharSequence s, int start, int before, int count) {}
        public void afterTextChanged(Editable s) { updateList.run(); }
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
    listDialog.setContentView(root);
    applyDialogSize(listDialog, 0.96f, 0.90f);

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
void addDivider(Activity ctx, ViewGroup parent) {
    View v = new View(ctx);
    v.setBackgroundColor(Color.parseColor("#EAF0F6"));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 1);
    lp.setMargins(16, 0, 16, 0);
    parent.addView(v, lp);
}

void addSwitchRow(Activity ctx, LinearLayout parent, String title, boolean checked, CompoundButton.OnCheckedChangeListener listener) {
    LinearLayout row = new LinearLayout(ctx);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setPadding(20, 16, 20, 16);
    row.setGravity(Gravity.CENTER_VERTICAL);
    
    TextView tv = new TextView(ctx);
    tv.setText(title);
    tv.setTextSize(15.5f);
    tv.setTextColor(Color.parseColor("#0F172A"));
    row.addView(tv, new LinearLayout.LayoutParams(0, -2, 1));
    
    Switch s = new Switch(ctx);
    s.setChecked(checked);
    s.setOnCheckedChangeListener(listener);
    try { s.setScaleX(0.92f); s.setScaleY(0.92f); } catch (Throwable ignored) {}
    row.addView(s);
    
    parent.addView(row);
}

LinearLayout addClickableRowCustom(Activity ctx, LinearLayout parent, String title, View rightView) {
    LinearLayout row = new LinearLayout(ctx);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setPadding(20, 16, 20, 16);
    row.setGravity(Gravity.CENTER_VERTICAL);
    
    TextView tv = new TextView(ctx);
    tv.setText(title);
    tv.setTextSize(15.5f);
    tv.setTextColor(Color.parseColor("#0F172A"));
    row.addView(tv, new LinearLayout.LayoutParams(0, -2, 1));
    row.addView(rightView);
    parent.addView(row);
    return row;
}

LinearLayout addClickableRow(Activity ctx, LinearLayout parent, String title, String value) {
    TextView tvVal = new TextView(ctx);
    tvVal.setText(value);
    tvVal.setTextColor(Color.GRAY);
    return addClickableRowCustom(ctx, parent, title, tvVal);
}
