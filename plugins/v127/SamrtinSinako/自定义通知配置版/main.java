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

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import me.hd.wauxv.data.bean.info.FriendInfo;
import me.hd.wauxv.data.bean.info.GroupInfo;

import java.io.File;
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
String CFG_NOTIFY_ICON = "jay_cfg_notify_icon_v7";
String CFG_TALKER_CFG_PREFIX = "jay_talker_cfg_v9_";
String JAY_MARK = "is_jay_custom_mark_v7";
String JAY_WECHAT_TALKER = "jay_wechat_talker_v1";
String KEYWORD_NOTIFY_MARK = "is_keyword_notify";
String CUSTOM_CHANNEL_PREFIX = "jay_chn_v9_";
String[] LEGACY_CHANNEL_PREFIXES = new String[]{"jay_chn_v7", "jay_chn_v8"};
String KEYWORD_CHANNEL_PREFIX = "keyword_notify_";

String ACTION_QUICK_REPLY = "jay_action_quick_reply_v10_lock";
String EXTRA_TALKER = "jay_extra_talker_v7";
String EXTRA_NOTIFY_ID = "jay_extra_notify_id_v7";

Map sTalkerUnreadCount = Collections.synchronizedMap(new HashMap());

// ================= 快捷回复去重缓存 =================
Map sRecentQuickReplies = Collections.synchronizedMap(new HashMap());
long QUICK_REPLY_DEDUP_MS = 2000L;

int REQ_PICK_RINGTONE_SYSTEM = 10086;
int REQ_PICK_RINGTONE_FILE = 10087;
int REQ_PICK_ICON = 10088;

String[] WECHAT_NOTIFICATION_ITEM_TALKER_FIELDS = new String[]{"h", "userName", "username", "talker", "talkerUserName"};
String[] WECHAT_NOTIFICATION_ITEM_NOTIFICATION_FIELDS = new String[]{"f", "notification", "mNotification"};
String[] MSG_CONTENT_METHODS = new String[]{"getContent", "getMsgContent", "getText", "getDigest"};
String[] MSG_CONTENT_FIELDS = new String[]{"content", "msgContent", "message", "field_content"};
String[] MSG_TALKER_METHODS = new String[]{"getTalker", "getUsername", "getUserName", "getConversationId", "getChatUser", "getSessionId"};
String[] MSG_ORIGIN_TALKER_METHODS = new String[]{"getTalker", "getUsername", "getUserName", "getConversationId", "getChatUser", "getSessionId", "getFromUserName"};
String[] MSG_TALKER_FIELDS = new String[]{"talker", "username", "userName", "conversationId", "chatUser", "sessionId", "fromUserName"};
String[] MSG_ORIGIN_METHODS = new String[]{"getOriginMsg", "getOriginMessage", "getOrigin", "getOriginContent"};
String[] MSG_ORIGIN_FIELDS = new String[]{"originMsg", "originMessage", "origin", "originContent"};
String[] ORIGIN_TALKER_KEYS = new String[]{"talker", "fromusername", "fromUserName", "chatuser", "chatUser", "conversationId"};
String[] ORIGIN_CONTENT_KEYS = new String[]{"content", "msg", "msgContent", "message"};
String[] GROUP_SENDER_METHODS = new String[]{"getSendTalker", "getSenderUserName", "getSenderWxid", "getFromUser", "getFromUserName", "getRealChatUser", "getSenderId"};
String[] MSG_GROUP_FLAG_METHODS = new String[]{"isGroupChat", "isChatRoom", "isChatroom", "isGroup"};
String[] MSG_AT_ALL_METHODS = new String[]{"isNotifyAll", "isAnnounceAll", "isAtAll", "isAtEveryone", "hasAtAll"};
String[] MSG_AT_ME_METHODS = new String[]{"isAtMe", "isAtMeFromGroup", "isMentioned", "hasAtMe", "needNotifyMe"};

// ================= 内存缓存 =================

// ================= 头像缓存 =================
Map sAvatarCache = Collections.synchronizedMap(new HashMap());


void cacheAvatar(Bitmap bmp, String talker) {
    if (bmp == null || TextUtils.isEmpty(talker)) return;
    sAvatarCache.put(talker, bmp);
}

Bitmap stealAvatar(Notification n) {
    if (n == null) return null;
    try {
        if (Build.VERSION.SDK_INT >= 23) {
            Object icon = n.getLargeIcon();
            if (icon instanceof Bitmap) {
                Bitmap bmp = (Bitmap) icon;
                if (!bmp.isRecycled()) return bmp;
            }
            if (icon instanceof android.graphics.drawable.Icon) {
                Bitmap bmp = ((android.graphics.drawable.Icon) icon).getBitmap();
                if (bmp != null && !bmp.isRecycled()) return bmp;
            }
        }
    } catch (Throwable ignored) {}
    return null;
}

String findAvatarDir() {
    try {
        File mmDir = new File("/data/data/com.tencent.mm/MicroMsg/");
        File[] subs = mmDir.listFiles();
        if (subs == null) return null;
        for (File d : subs) {
            if (!d.isDirectory()) continue;
            String n = d.getName();
            if (n.length() == 32 && n.matches("[0-9a-f]{32}")) {
                File avDir = new File(d, "avatar");
                if (avDir.exists()) return d.getAbsolutePath();
            }
        }
    } catch (Throwable ignored) {}
    return null;
}
boolean cacheVibrate = true;
boolean cacheSound = true;
boolean cacheShowDetail = true;
String cacheNotifyIcon = "";
TextView tvNotifyIcon = null;
String currentChannelId = "jay_chn_init_v7";
boolean cacheMuteTimeEnable = false;
String cacheMuteTimeStart = "23:00";
String cacheMuteTimeEnd = "07:00";
boolean cacheBlockAtAll = false;
boolean cacheBlockAtMe = false;
Map cacheTalkerCfgMap = Collections.synchronizedMap(new HashMap());

volatile Set cacheTargetSet = Collections.emptySet();

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

// 全局联系人缓存
List sCachedFriendNames = null;
List sCachedFriendIds = null;
List sCachedGroupNames = null;
List sCachedGroupIds = null;
boolean sNotifyHookInstalled = false;
boolean sActivityResultHookInstalled = false;
boolean sWechatNotificationItemHookInstalled = false;
long sLastAutoReloadAt = 0L;
Dialog sLoadingDialogRef = null;
int sCustomNotifySeq = 0;
Map sQuickReplyTimestamps = Collections.synchronizedMap(new HashMap());
long sLastGroupCacheLoadAt = 0L;

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
            String talkerChannelId = buildTalkerChannelId(talkerSound, talkerVibrate, talkerRing);

            ensureNotifyChannel(nm, talkerChannelId, talkerVibrate, false, talkerRing);
        }
    } catch (Throwable ignored) {}
}

String normalizeRingtoneUri(String ringtoneUri) {
    return TextUtils.isEmpty(ringtoneUri) ? "" : ringtoneUri;
}

String buildTalkerChannelId(boolean useSound, boolean useVibrate, String ringtoneUri) {
    String ring = normalizeRingtoneUri(ringtoneUri);
    String sTag = useSound ? "M" : "N";
    String vTag = useVibrate ? "V" : "N";
    return CUSTOM_CHANNEL_PREFIX + sTag + "_" + vTag + "_" + ring.hashCode();
}


void onLoad() {
        if (!getHookState()) { toast("请关闭LSPosed调用保护"); return; }

    loadConfigToCache();
    hookSystemNotification();
    hookWechatNotificationItemTalker();
    hookActivityResultForRingtone();
    registerQuickReplyReceiver();

    try {
        sMainHandler.postDelayed(new Runnable() {
            public void run() {
                try {
                    loadConfigToCache();
                } catch (Throwable ignored) {}
            }
        }, 600);
    } catch (Throwable ignored) {}

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
        synchronized (this) {
            if (!cacheTargetSet.isEmpty()) return;
            if (now - sLastAutoReloadAt < 1200L) return;
            sLastAutoReloadAt = now;
            loadConfigToCache();
        }
    } catch (Throwable ignored) {}
}



void unhookAll(List hooks) {
    if (hooks == null) return;
    for (int i = 0; i < hooks.size(); i++) {
        try {
            Object uh = hooks.get(i);
            Method um = uh.getClass().getMethod("unhook", new Class[]{});
            um.invoke(uh, new Object[]{});
        } catch (Throwable ignored) {}
    }
    hooks.clear();
}

void onUnload() {
    unhookAll(notifyUnhooks);
    sNotifyHookInstalled = false;
    sWechatNotificationItemHookInstalled = false;
    unhookAll(resultUnhooks);
    sActivityResultHookInstalled = false;

    sCachedFriendNames = null;
    sCachedFriendIds = null;
    sCachedGroupNames = null;
    sCachedGroupIds = null;
    sLastGroupCacheLoadAt = 0L;
    cacheTargetSet = Collections.emptySet();
    cacheTalkerCfgMap.clear();
    try { sTalkerUnreadCount.clear(); } catch (Throwable ignored) {}
    try { sAvatarCache.clear(); } catch (Throwable ignored) {}
    try { sQuickReplyTimestamps.clear(); } catch (Throwable ignored) {}
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
    clearGlobalRingtonePickState();
    globalSettingActivity = null;
    unregisterQuickReplyReceiver();
}

void openSettings() {
    showSettingsUI();
}

boolean onClickSendBtn(String text) {
    if ("通知设置".equals(text)) {
        showSettingsUI();
        return true;
    }
    return false;
}

// ================= 配置加载 =================

void loadTargetSetFromConfig(String rawTargets) {
    try {
        Set newSet = new HashSet();
        newSet.addAll(parseTargetSet(rawTargets));
        cacheTargetSet = Collections.unmodifiableSet(newSet);
    } catch (Throwable ignored) {
        cacheTargetSet = Collections.emptySet();
    }
}



Set parseTargetSet(String rawTargets) {
    Set out = new HashSet();
    if (TextUtils.isEmpty(rawTargets)) return out;
    try {
        String[] parts = rawTargets.split(",");
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i] == null ? "" : parts[i].trim();
            if (!TextUtils.isEmpty(p)) out.add(p);
        }
    } catch (Throwable ignored) {}
    return out;
}

Map loadAndMigrateTalkerCfg(String talkerId) {
    Map one = new HashMap();
    if (TextUtils.isEmpty(talkerId)) return one;
    try {
        String rawCfg = getString(CFG_TALKER_CFG_PREFIX + talkerId, "");
        one = parseTalkerCfg(rawCfg);
        String oldRing = cfgGet(one, "ringtone", "");
        if (!TextUtils.isEmpty(oldRing)) {
            String newRing = freezeRingtoneUri(oldRing);
            if (!TextUtils.isEmpty(newRing) && !newRing.equals(oldRing)) {
                one.put("ringtone", newRing);
                putString(CFG_TALKER_CFG_PREFIX + talkerId, encodeTalkerCfg(one));
            }
        }
    } catch (Throwable ignored) {}
    return one;
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
    cacheNotifyIcon = getString(CFG_NOTIFY_ICON, "");

    loadTargetSetFromConfig(cacheTargetsStr);
    cacheTalkerCfgMap.clear();
    Object[] targetArr = cacheTargetSet.toArray();
    for (int i = 0; i < targetArr.length; i++) {
        String talkerId = String.valueOf(targetArr[i]);
        cacheTalkerCfgMap.put(talkerId, loadAndMigrateTalkerCfg(talkerId));
    }

    currentChannelId = CUSTOM_CHANNEL_PREFIX + (cacheSound ? "S" : "N") + "_" + (cacheVibrate ? "V" : "N");
    rebuildNotificationChannel();
}

// ================= 快捷回复与已读广播 =================

boolean tryAcquireQuickReplySendLock(String talker, String reply) {
    if (TextUtils.isEmpty(talker) || TextUtils.isEmpty(reply)) return false;

    try {
        long now = System.currentTimeMillis();
        String key = "qr_" + md5(talker + " || " + reply);

        java.io.File baseDir = null;
        try {
            if (hostContext != null) baseDir = hostContext.getCacheDir();
        } catch (Throwable ignored) {}

        if (baseDir == null) {
            try {
                baseDir = new java.io.File("/data/data/com.tencent.mm/cache");
            } catch (Throwable ignored) {}
        }

        if (baseDir == null) return true;

        java.io.File dir = new java.io.File(baseDir, "jay_quick_reply_locks");
        if (!dir.exists()) dir.mkdirs();

        java.io.File lock = new java.io.File(dir, key + ".lock");

        // createNewFile 是原子操作：多个 Receiver 同时抢，只有一个会成功
        if (lock.createNewFile()) {
            try { lock.setLastModified(now); } catch (Throwable ignored) {}
            return true;
        }

        long last = 0L;
        try { last = lock.lastModified(); } catch (Throwable ignored) {}

        // 15 秒内同会话同内容只允许发送一次
        if (last > 0 && now - last < 15000L) {
            return false;
        }

        // 超时旧锁清理后再抢一次
        try { lock.delete(); } catch (Throwable ignored) {}

        if (lock.createNewFile()) {
            try { lock.setLastModified(now); } catch (Throwable ignored) {}
            return true;
        }

        return false;
    } catch (Throwable ignored) {}

    // 异常时放行，避免彻底无法回复
    return true;
}


boolean shouldDropQuickReplyByGlobalDedup(String talker, String reply) {
    if (TextUtils.isEmpty(talker) || TextUtils.isEmpty(reply)) return true;

    try {
        String keyRaw = talker + " || " + reply;
        String key = "qr_" + md5(keyRaw);
        long now = System.currentTimeMillis();

        SharedPreferences sp = hostContext.getSharedPreferences("jay_quick_reply_global_dedup", Context.MODE_PRIVATE);
        long last = sp.getLong(key, 0L);

        // 多实例 / 多 BroadcastReceiver 情况下，8 秒内同一会话同一内容只允许发送一次
        if (last > 0 && now - last < 8000L) {
            return true;
        }

        // 必须同步提交，避免多个 Receiver 几乎同时通过
        sp.edit().putLong(key, now).commit();
        return false;
    } catch (Throwable ignored) {}

    return false;
}

void registerQuickReplyReceiver() {
    try {
        if (quickReplyReceiverRegistered) return;
                                quickReplyReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                try {
                    if (intent == null) return;
                    String action = intent.getAction();

                    if (ACTION_QUICK_REPLY.equals(action)) {
                        final String talker = intent.getStringExtra(EXTRA_TALKER);
                        final int notifyId = intent.getIntExtra(EXTRA_NOTIFY_ID, 0);
                        if (TextUtils.isEmpty(talker)) return;

                        Bundle results = RemoteInput.getResultsFromIntent(intent);
                        CharSequence cs = null;
                        if (results != null) cs = results.getCharSequence("key_reply_content");
                        if (cs == null) cs = intent.getCharSequenceExtra("key_reply_content");
                        final String reply = cs == null ? "" : cs.toString().trim();
                        if (TextUtils.isEmpty(reply)) return;

                        // ====== 全局去重：防止多个 BroadcastReceiver / 多实例导致重复发送 ======
                        if (shouldDropQuickReplyByGlobalDedup(talker, reply)) {
                            return;
                        }

                        // ====== 立即取消通知（主线程）解决转圈 ======
                        try {
                            NotificationManager nm = (NotificationManager) hostContext.getSystemService(Context.NOTIFICATION_SERVICE);
                            if (nm != null) {
                                nm.cancel(notifyId);
                                // 再额外 cancel 一次 notifyId+1 和 notifyId-1，防止 ID 偏差
                                nm.cancel(notifyId + 1);
                                nm.cancel(notifyId - 1);
                            }
                        } catch (Throwable ignored) {}

                        // ====== 延时再取消一次（兜底） ======
                        final int finalNotifyId = notifyId;
                        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                            public void run() {
                                try {
                                    NotificationManager nm = (NotificationManager) hostContext.getSystemService(Context.NOTIFICATION_SERVICE);
                                    if (nm != null) nm.cancel(finalNotifyId);
                                } catch (Throwable ignored) {}
                            }
                        }, 500);

                        if (sQuickReplyExecutor == null || sQuickReplyExecutor.isShutdown()) {
                            sQuickReplyExecutor = Executors.newSingleThreadExecutor();
                        }
                        sQuickReplyExecutor.execute(new Runnable() {
                            public void run() {
                                try {
                                    String dedupKey = talker + " || " + reply;
                                    long now = System.currentTimeMillis();
                                    synchronized (sRecentQuickReplies) {
                                        Long lastSent = (Long) sRecentQuickReplies.get(dedupKey);
                                        if (lastSent != null && now - lastSent < 3000L) {
                                            return;
                                        }
                                        sRecentQuickReplies.put(dedupKey, Long.valueOf(now));
                                    }
                                    if (!tryAcquireQuickReplySendLock(talker, reply)) {
                                        return;
                                    }
                                    sQuickReplyTimestamps.put(talker, Long.valueOf(System.currentTimeMillis()));
                                    sendText(talker, reply);
                                    try { sTalkerUnreadCount.remove(talker); } catch (Throwable ignored) {}
                                } catch (Throwable ignored) {}
                            }
                        });
                        return;
                    }

                } catch (Throwable ignored) {}
            }
        }
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

// ================= 配置解析 =================
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
            String k = cfgUnescape(one.substring(0, p).trim());
            String v = cfgUnescape(one.substring(p + 1).trim());
            if (!TextUtils.isEmpty(k)) m.put(k, v);
        }
    } catch (Throwable ignored) {}
    return m;
}

String cfgEscape(String s) {
    if (s == null) return "";
    try {
        return "~" + Uri.encode(String.valueOf(s));
    } catch (Throwable ignored) {}
    return String.valueOf(s);
}

String cfgUnescape(String s) {
    if (s == null) return "";
    try {
        if (s.startsWith("~")) return Uri.decode(s.substring(1));
    } catch (Throwable ignored) {}
    return s;
}

String encodeTalkerCfg(Map m) {
    if (m == null || m.isEmpty()) return "";
    String result = "";
    Object[] keys = m.keySet().toArray();
    for (int i = 0; i < keys.length; i++) {
        String k = String.valueOf(keys[i]);
        String v = String.valueOf(m.get(keys[i]));
        if (TextUtils.isEmpty(k) || TextUtils.isEmpty(v)) continue;
        if (!TextUtils.isEmpty(result)) result += ";";
        result += cfgEscape(k) + "=" + cfgEscape(v);
    }
    return result;
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

String formatHHmm(int h, int m) {
    if (h < 0) h = 0;
    if (h > 23) h = 23;
    if (m < 0) m = 0;
    if (m > 59) m = 59;
    return (h < 10 ? "0" + h : String.valueOf(h)) + ":" + (m < 10 ? "0" + m : String.valueOf(m));
}

// ================= 通用工具 =================

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

String readStringByAccessors(Object obj, String[] methodNames, String[] fieldNames) {
    if (obj == null) return "";
    try {
        Object v = safeInvokeAny(obj, methodNames);
        if (v != null) {
            String s = String.valueOf(v).trim();
            if (!TextUtils.isEmpty(s)) return s;
        }
    } catch (Throwable ignored) {}
    try {
        Object vf = safeGetFieldAny(obj, fieldNames);
        if (vf != null) {
            String s2 = String.valueOf(vf).trim();
            if (!TextUtils.isEmpty(s2)) return s2;
        }
    } catch (Throwable ignored) {}
    return "";
}

Object resolveOriginObject(Object msg) {
    if (msg == null) return null;
    try {
        Object originObj = safeInvokeAny(msg, MSG_ORIGIN_METHODS);
        if (originObj != null) return originObj;
    } catch (Throwable ignored) {}
    try {
        return safeGetFieldAny(msg, MSG_ORIGIN_FIELDS);
    } catch (Throwable ignored) {}
    return null;
}

boolean isGroupTalkerOrMsg(String talker, Object msg) {
    try {
        if (!TextUtils.isEmpty(talker) && talker.endsWith("@chatroom")) return true;
        return asBool(safeInvokeAny(msg, MSG_GROUP_FLAG_METHODS));
    } catch (Throwable ignored) {}
    return false;
}

// ================= 消息解析 =================

// ================= 消息类型工具（自动插入） =================
int getMsgTypeFromMsg(Object msg) {
    int type = 1;
    try {
        Object typeObj = safeInvokeAny(msg, new String[]{"getType", "getMsgType", "getMessageType"});
        if (typeObj instanceof Number) return ((Number) typeObj).intValue();
        if (typeObj != null) return Integer.parseInt(String.valueOf(typeObj));
    } catch (Throwable ignored) {}
    try {
        Object typeField = safeGetFieldAny(msg, new String[]{"type", "msgType", "field_type", "field_msgType", "messageType"});
        if (typeField instanceof Number) return ((Number) typeField).intValue();
        if (typeField != null) return Integer.parseInt(String.valueOf(typeField));
    } catch (Throwable ignored) {}
    return type;
}

boolean isRawXmlLike(String s) {
    if (TextUtils.isEmpty(s)) return false;
    try {
        String t = s.trim();
        if (TextUtils.isEmpty(t)) return false;
        if (t.startsWith("<msg")) return true;
        if (t.startsWith("<?xml")) return true;
        if (t.startsWith("<appmsg")) return true;
        if (t.startsWith("<sysmsg")) return true;
        if (t.startsWith("<emoji")) return true;
        if (t.startsWith("<voicemsg")) return true;
        if (t.startsWith("<videomsg")) return true;
        if (t.startsWith("<img")) return true;
        if (t.startsWith("<location")) return true;
        if (t.indexOf("<appmsg") >= 0 && t.indexOf("</appmsg>") >= 0) return true;
        if (t.indexOf("<msg>") >= 0 && t.indexOf("</msg>") >= 0) return true;
    } catch (Throwable ignored) {}
    return false;
}

String extractXmlAttr(String raw, String attr) {
    if (TextUtils.isEmpty(raw) || TextUtils.isEmpty(attr)) return "";
    try {
        String key1 = attr + "=\"";
        int p = raw.indexOf(key1);
        if (p >= 0) { int st = p + key1.length(); int ed = raw.indexOf("\"", st); if (ed > st) return raw.substring(st, ed).trim(); }
        String key2 = attr + "='";
        p = raw.indexOf(key2);
        if (p >= 0) { int st2 = p + key2.length(); int ed2 = raw.indexOf("'", st2); if (ed2 > st2) return raw.substring(st2, ed2).trim(); }
    } catch (Throwable ignored) {}
    return "";
}

String extractXmlTagText(String raw, String tag) {
    if (TextUtils.isEmpty(raw) || TextUtils.isEmpty(tag)) return "";
    try {
        String stTag = "<" + tag + ">", edTag = "</" + tag + ">";
        int st = raw.indexOf(stTag), ed = raw.indexOf(edTag);
        if (st >= 0 && ed > st) {
            String v = raw.substring(st + stTag.length(), ed).trim();
            if (v.startsWith("<![CDATA[") && v.endsWith("]]>")) v = v.substring(9, v.length() - 3).trim();
            return v;
        }
        String stTag2 = "<" + tag + "><![CDATA[";
        st = raw.indexOf(stTag2); ed = raw.indexOf("]]></" + tag + ">");
        if (st >= 0 && ed > st) return raw.substring(st + stTag2.length(), ed).trim();
    } catch (Throwable ignored) {}
    return "";
}

String getAppMsgTypeNameFromXml(String raw) {
    if (TextUtils.isEmpty(raw)) return "";
    int appType = -1;
    try { String tt = extractXmlTagText(raw, "type"); if (!TextUtils.isEmpty(tt)) appType = Integer.parseInt(tt); } catch (Throwable ignored) {}
    try { if (appType < 0) { String ta = extractXmlAttr(raw, "type"); if (!TextUtils.isEmpty(ta)) appType = Integer.parseInt(ta); } } catch (Throwable ignored) {}
    switch (appType) {
        case 1: return "[文本分享]"; case 2: return "[图片消息]"; case 3: return "[音乐消息]";
        case 4: return "[视频消息]"; case 5: return "[链接消息]"; case 6: return "[文件消息]";
        case 7: return "[微博消息]"; case 8: return "[表情消息]"; case 15: return "[视频消息]";
        case 19: return "[聊天记录]"; case 24: return "[笔记消息]"; case 33: return "[小程序]";
        case 36: return "[小程序]"; case 44: return "[小游戏]"; case 46: return "[视频号消息]";
        case 48: return "[视频号直播]"; case 51: return "[视频号消息]"; case 53: return "[接龙消息]";
        case 57: return "[引用消息]"; case 63: return "[视频号消息]"; case 87: return "[群公告]";
        case 88: return "[视频号直播]"; case 2000: return "[转账消息]"; case 2001: return "[红包消息]";
    }
    try {
        String t = raw.toLowerCase();
        if (t.indexOf("<refermsg") >= 0 || t.indexOf("<quote") >= 0) return "[引用消息]";
        if (t.indexOf("<recorditem") >= 0) return "[聊天记录]";
        if (t.indexOf("<finderfeed") >= 0 || t.indexOf("<finder") >= 0) return "[视频号消息]";
        if (t.indexOf("<weappinfo") >= 0 || t.indexOf("<appbrand") >= 0) return "[小程序]";
        if (t.indexOf("<wcpayinfo") >= 0) return "[支付消息]";
        if (t.indexOf("<emoji") >= 0) return "[表情消息]";
        if (t.indexOf("<img") >= 0) return "[图片消息]";
        if (t.indexOf("<videomsg") >= 0) return "[视频消息]";
        if (t.indexOf("<voicemsg") >= 0) return "[语音消息]";
        if (t.indexOf("<location") >= 0) return "[位置消息]";
    } catch (Throwable ignored) {}
    return "";
}

String getMsgTypeName(int type) {
    switch (type) {
        case 1: return "[文本消息]"; case 3: return "[图片消息]"; case 34: return "[语音消息]";
        case 37: return "[好友验证]"; case 42: return "[名片消息]"; case 43: return "[视频消息]";
        case 47: return "[表情消息]"; case 48: return "[位置消息]"; case 49: return "[分享消息]";
        case 50: return "[语音/视频通话]"; case 52: return "[微信运动]"; case 53: return "[视频通话]";
        case 62: return "[小视频]"; case 66: return "[企业微信消息]"; case 10000: return "[系统消息]";
        case 10002: return "[撤回消息]"; case 1048625: return "[引用消息]";
        case 16777265: return "[链接消息]"; case 436207665: return "[红包消息]";
        case 436207666: return "[转账消息]"; case 536870961: return "[拍一拍]";
        case 570425393: return "[位置共享]"; case 754974769: return "[视频号消息]";
        case 771751985: return "[视频号直播]"; case 822083633: return "[引用消息]";
        case 922746929: return "[拍一拍]";
    }
    return "[非文本消息]";
}

String getReadableMsgPlaceholder(int type, String rawContent) {
    try {
        if (!TextUtils.isEmpty(rawContent)) {
            String appName = getAppMsgTypeNameFromXml(rawContent);
            if (!TextUtils.isEmpty(appName)) return appName;
            String t = rawContent.trim().toLowerCase();
            if (t.startsWith("<img") || t.indexOf("<img ") >= 0) return "[图片消息]";
            if (t.startsWith("<voicemsg") || t.indexOf("<voicemsg") >= 0) return "[语音消息]";
            if (t.startsWith("<videomsg") || t.indexOf("<videomsg") >= 0) return "[视频消息]";
            if (t.startsWith("<emoji") || t.indexOf("<emoji") >= 0) return "[表情消息]";
            if (t.startsWith("<location") || t.indexOf("<location") >= 0) return "[位置消息]";
            if (t.startsWith("<sysmsg")) return "[系统消息]";
        }
    } catch (Throwable ignored) {}
    return getMsgTypeName(type);
}

String cleanNotifyContentByType(int type, String content) {
    if (TextUtils.isEmpty(content)) return getMsgTypeName(type);
    try {
        String c = content.trim();
        if (isRawXmlLike(c)) return getReadableMsgPlaceholder(type, c);
        int p = c.indexOf(":\\n");
        if (p > 0 && p + 2 < c.length()) {
            String pure = c.substring(p + 2).trim();
            if (isRawXmlLike(pure)) {
                String prefix = c.substring(0, p).trim();
                String label = getReadableMsgPlaceholder(type, pure);
                if (!TextUtils.isEmpty(prefix)) return prefix + ": " + label;
                return label;
            }
        }
        if (type != 1 && c.length() > 240 && c.indexOf("<") >= 0 && c.indexOf(">") >= 0)
            return getReadableMsgPlaceholder(type, c);
        return c;
    } catch (Throwable ignored) {}
    return getMsgTypeName(type);
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
    String result = "";
    Object[] arr = s.toArray();
    for (int i = 0; i < arr.length; i++) {
        String v = String.valueOf(arr[i]).trim();
        if (TextUtils.isEmpty(v)) continue;
        if (!TextUtils.isEmpty(result)) result += ",";
        result += v;
    }
    return result;
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
        Object sid = safeInvokeAny(msg, GROUP_SENDER_METHODS);
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

boolean isSelfSentMsg(Object msg, String talker, String content) {
    try {
        if (asBool(safeInvokeAny(msg, new String[]{"isSend", "isSelf", "isFromSelf", "isMe"}))) return true;
    } catch (Throwable ignored) {}
    try {
        String self = getLoginWxid();
        if (!TextUtils.isEmpty(self)) {
            String sender = readStringByAccessors(msg, GROUP_SENDER_METHODS, new String[]{"sendTalker", "senderUserName", "senderWxid", "fromUser", "fromUserName", "realChatUser", "senderId"});
            if (TextUtils.isEmpty(sender) && isGroupTalkerOrMsg(talker, msg)) {
                String[] s = extractGroupSenderInfo(msg, talker, content);
                sender = s[0];
            }
            if (self.equals(sender)) return true;
            // 也检查消息的 talker 是否是自己
            if (!TextUtils.isEmpty(talker) && self.equals(talker)) return true;
        }
    } catch (Throwable ignored) {}

    // 兜底
    try {
        if ("medianote".equals(talker) || "filehelper".equals(talker)) return true;
        if (!TextUtils.isEmpty(talker) && !TextUtils.isEmpty(content)) {
            String self = getLoginWxid();
            if (!TextUtils.isEmpty(self) && self.equals(talker)) return true;
        }
    } catch (Throwable ignored) {}


    return false;
}

String extractLooseKeyValue(String raw, String key, boolean stopOnSpace) {
    if (TextUtils.isEmpty(raw) || TextUtils.isEmpty(key)) return "";
    try {
        int p = raw.indexOf(key + "=");
        if (p < 0) return "";
        int st = p + key.length() + 1;
        int ed = st;
        while (ed < raw.length()) {
            char c = raw.charAt(ed);
            if (c == '&' || c == ';' || c == '\n' || c == '\r') break;
            if (stopOnSpace && (c == ',' || c == '\t' || c == ' ' || c == '\"')) break;
            ed++;
        }
        if (ed > st) {
            String v = raw.substring(st, ed).trim();
            if (!TextUtils.isEmpty(v)) return v;
        }
    } catch (Throwable ignored) {}
    return "";
}

String extractQuotedJsonValue(String raw, String key) {
    if (TextUtils.isEmpty(raw) || TextUtils.isEmpty(key)) return "";
    try {
        int p = raw.indexOf("\"" + key + "\"");
        if (p < 0) return "";
        int c1 = raw.indexOf(":", p);
        if (c1 > 0) {
            int q1 = raw.indexOf("\"", c1 + 1);
            int q2 = q1 >= 0 ? raw.indexOf("\"", q1 + 1) : -1;
            if (q1 >= 0 && q2 > q1) {
                String v = raw.substring(q1 + 1, q2).trim();
                if (!TextUtils.isEmpty(v)) return v;
            }
        }
    } catch (Throwable ignored) {}
    return "";
}

String extractValueFromLooseText(String raw, String[] keys, boolean stopOnSpace) {
    if (TextUtils.isEmpty(raw) || keys == null) return "";
    for (int i = 0; i < keys.length; i++) {
        String k = keys[i];
        String v = extractLooseKeyValue(raw, k, stopOnSpace);
        if (!TextUtils.isEmpty(v)) return v;
        v = extractQuotedJsonValue(raw, k);
        if (!TextUtils.isEmpty(v)) return v;
    }
    return "";
}

String extractTalkerFromOriginContent(String origin) {
    if (TextUtils.isEmpty(origin)) return "";
    try {
        String v = extractValueFromLooseText(origin, ORIGIN_TALKER_KEYS, true);
        if (!TextUtils.isEmpty(v)) return v;
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
        String v = extractValueFromLooseText(origin, ORIGIN_CONTENT_KEYS, false);
        if (!TextUtils.isEmpty(v)) return v;
    } catch (Throwable ignored) {}
    return "";
}

String resolveMsgContentForNotify(Object msg, String content) {
    int type = getMsgTypeFromMsg(msg);
    if (!TextUtils.isEmpty(content)) return cleanNotifyContentByType(type, content);
    String c = readStringByAccessors(msg, MSG_CONTENT_METHODS, MSG_CONTENT_FIELDS);
    if (!TextUtils.isEmpty(c)) return cleanNotifyContentByType(type, c);
    Object originObj = resolveOriginObject(msg);
    if (originObj == null) return type == 1 ? "" : getMsgTypeName(type);
    c = readStringByAccessors(originObj, MSG_CONTENT_METHODS, MSG_CONTENT_FIELDS);
    if (!TextUtils.isEmpty(c)) return cleanNotifyContentByType(type, c);
    try {
        String originText = String.valueOf(originObj);
        if (!TextUtils.isEmpty(originText)) {
            if (isRawXmlLike(originText)) return getReadableMsgPlaceholder(type, originText);
            String extracted = extractContentFromOriginText(originText);
            if (!TextUtils.isEmpty(extracted)) return cleanNotifyContentByType(type, extracted);
        }
    } catch (Throwable ignored) {}
    return type == 1 ? "" : getMsgTypeName(type);
}

String resolveTalkerForMsg(Object msg, String content) {
    String talker = readStringByAccessors(msg, MSG_TALKER_METHODS, MSG_TALKER_FIELDS);
    if (!TextUtils.isEmpty(talker)) return talker;

    Object originObj = resolveOriginObject(msg);

    if (originObj != null) {
        if (originObj instanceof CharSequence) {
            String fromText = extractTalkerFromOriginContent(String.valueOf(originObj));
            if (!TextUtils.isEmpty(fromText)) return fromText;
        } else {
            talker = readStringByAccessors(originObj, MSG_ORIGIN_TALKER_METHODS, MSG_TALKER_FIELDS);
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
        boolean isGroupChat = isGroupTalkerOrMsg(talker, msg);

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
            Object atAllObj = safeInvokeAny(msg, MSG_AT_ALL_METHODS);
            String c = TextUtils.isEmpty(content) ? "" : content;
            if (asBool(atAllObj) || c.contains("@所有人") || c.contains("＠所有人") || c.toLowerCase().contains("@all")) return true;
        }

        if (isGroupChat && blockAtMe) {
            Object atMeObj = safeInvokeAny(msg, MSG_AT_ME_METHODS);
            String c2 = TextUtils.isEmpty(content) ? "" : content;
            if (asBool(atMeObj) || c2.contains("@我") || c2.contains("有人@我") || c2.contains("提到了你") || c2.contains("提及你")) return true;
        }
    } catch (Throwable ignored) {}
    return false;
}

boolean isSystemMessageLike(Object msg, String talker, String content, int type) {
    try {
        Object sysObj = safeInvokeAny(msg, new String[]{"isSystem", "isSystemMsg", "isSysMsg"});
        if (asBool(sysObj)) return true;
    } catch (Throwable ignored) {}
    try {
        Object patObj = safeInvokeAny(msg, new String[]{"isPat", "isPatMsg"});
        if (asBool(patObj)) return true;
    } catch (Throwable ignored) {}
    try {
        if (type == 10000 || type == 10002) return true;
    } catch (Throwable ignored) {}
    try {
        String tk = TextUtils.isEmpty(talker) ? "" : talker.trim().toLowerCase();
        if ("weixin".equals(tk) || "fmessage".equals(tk) || "medianote".equals(tk)
                || "notifymessage".equals(tk) || "notification_messages".equals(tk)
                || "qqmail".equals(tk) || "weixinreminder".equals(tk)) {
            return true;
        }
    } catch (Throwable ignored) {}
    try {
        String c = TextUtils.isEmpty(content) ? "" : content.toLowerCase();
        if (c.contains("<sysmsg") || c.contains("系统消息") || c.contains("撤回了一条消息")) return true;
        if (c.contains("拍了拍我") || c.contains("拍了拍你") || c.contains("拍了拍")) return true;
    } catch (Throwable ignored) {}
    return false;
}

// ================= 铃声工具 =================
String getRingtoneDisplayName(Context ctx, String uriStr) {
    if (TextUtils.isEmpty(uriStr)) return "跟随系统";
    try {
        Uri uri = Uri.parse(uriStr);
        android.media.Ringtone rt = RingtoneManager.getRingtone(ctx, uri);
        if (rt != null) {
            String title = rt.getTitle(ctx);
            if (!TextUtils.isEmpty(title)) {
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

java.io.File getRingtoneStoreDir() {
    try {
        String pd = String.valueOf(pluginDir);
        if (!TextUtils.isEmpty(pd) && !"null".equalsIgnoreCase(pd)) {
            java.io.File dir = new java.io.File(pd, "ringtones");
            if (!dir.exists()) dir.mkdirs();
            if (dir.exists() && dir.canWrite()) return dir;
        }
    } catch (Throwable ignored) {}
    try {
        if (hostContext != null && Build.VERSION.SDK_INT >= 21) {
            java.io.File[] dirs = hostContext.getExternalMediaDirs();
            if (dirs != null) {
                for (int i = 0; i < dirs.length; i++) {
                    java.io.File base = dirs[i];
                    if (base == null) continue;
                    java.io.File dir = new java.io.File(base, "WAuxiliary/Plugin/ringtones");
                    if (!dir.exists()) dir.mkdirs();
                    if (dir.exists() && dir.canWrite()) return dir;
                }
            }
        }
    } catch (Throwable ignored) {}
    try {
        java.io.File dir = new java.io.File("/storage/emulated/0/Android/media/com.tencent.mm/WAuxiliary/Plugin/ringtones");
        if (!dir.exists()) dir.mkdirs();
        if (dir.exists() && dir.canWrite()) return dir;
    } catch (Throwable ignored) {}
    return null;
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

            java.io.File dir = getRingtoneStoreDir();
            if (dir == null) return rawUri;

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

// ================= 前台检测 =================
boolean shouldBlockNativeByCfg(Map cfg) {
    return true;
}

boolean isWechatProcessVisibleNow() {
    try {
        if (hostContext == null) return false;
        ActivityManager am = (ActivityManager) hostContext.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return false;
        List procs = am.getRunningAppProcesses();
        if (procs == null) return false;

        int myPid = android.os.Process.myPid();
        String hostPkg = hostContext.getPackageName();
        for (int i = 0; i < procs.size(); i++) {
            ActivityManager.RunningAppProcessInfo p = (ActivityManager.RunningAppProcessInfo) procs.get(i);
            if (p == null) continue;

            boolean mine = p.pid == myPid;
            if (!mine && p.pkgList != null && !TextUtils.isEmpty(hostPkg)) {
                for (int j = 0; j < p.pkgList.length; j++) {
                    if (hostPkg.equals(p.pkgList[j])) {
                        mine = true;
                        break;
                    }
                }
            }
            if (!mine) continue;

            return p.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                    || p.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE;
        }
    } catch (Throwable ignored) {}
    return false;
}

boolean isChatActivityVisibleNow() {
    try {
        Activity top = getTopActivity();
        if (top == null) return false;
        try { if (top.isFinishing()) return false; } catch (Throwable ignored) {}
        try { if (Build.VERSION.SDK_INT >= 17 && top.isDestroyed()) return false; } catch (Throwable ignored) {}
        if (!isWechatProcessVisibleNow()) return false;

        String cls = "";
        try { cls = top.getClass().getName(); } catch (Throwable ignored) {}
        boolean chatClass = !TextUtils.isEmpty(cls) && (cls.indexOf("ChattingUI") >= 0 || cls.indexOf(".ui.chatting.") >= 0);
        boolean hasTargetTalker = false;
        try { hasTargetTalker = !TextUtils.isEmpty(getTargetTalker()); } catch (Throwable ignored) {}
        if (!chatClass && !hasTargetTalker) return false;

        try {
            if (top.hasWindowFocus()) return true;
            Window w = top.getWindow();
            View decor = w == null ? null : w.getDecorView();
            if (decor != null && decor.getWindowVisibility() == View.VISIBLE && decor.isShown()) return true;
        } catch (Throwable ignored) {}
    } catch (Throwable ignored) {}
    return false;
}

boolean isCurrentChatTalker(String talker) {
    if (TextUtils.isEmpty(talker)) return false;
    try {
        if (!isChatActivityVisibleNow()) return false;
        String cur = getTargetTalker();
        if (TextUtils.isEmpty(cur)) return false;
        return talker.equals(cur);
    } catch (Throwable ignored) {}
    return false;
}

int nextCustomNotifyId(String talker, boolean enableQuickReply) {
    try {
        sCustomNotifySeq++;
        if (sCustomNotifySeq > 999999) sCustomNotifySeq = 1;
        long seq = (long) (sCustomNotifySeq & 0x000FFFFF);
        long base = talker == null ? 0L : (long) talker.hashCode();
        long raw = 0x4A000000L | ((base & 0x000003FFL) << 20) | seq;
        return (int) (raw & 0x7FFFFFFFL);
    } catch (Throwable ignored) {}
    return (int) (System.currentTimeMillis() & 0x7fffffffL);
}

// ================= 核心 1：原生通知强力拦截器 =================

Bitmap tryGetBetterAvatar(Notification n, String talker) {
    if (n == null || TextUtils.isEmpty(talker)) return null;
    try {
        if (n.extras != null) {
            Object pic = n.extras.get("android.largeIcon");
            if (pic instanceof Bitmap) {
                Bitmap b = (Bitmap) pic;
                if (!b.isRecycled() && b.getWidth() >= 50) return b;
            }
            if (pic instanceof android.graphics.drawable.Icon) {
                Bitmap b = ((android.graphics.drawable.Icon) pic).getBitmap();
                if (b != null && !b.isRecycled() && b.getWidth() >= 50) return b;
            }
            Object icon = n.extras.get("android.icon");
            if (icon instanceof Bitmap) {
                Bitmap b = (Bitmap) icon;
                if (!b.isRecycled() && b.getWidth() >= 50) return b;
            }
        }
    } catch (Throwable ignored) {}
    return getGroupAvatarFromFile(talker);
}



Bitmap getGroupAvatarFromFile(String talkerId) {
    if (TextUtils.isEmpty(talkerId)) return null;
    try {
        String hash = md5(talkerId);
        if (TextUtils.isEmpty(hash)) return null;
        String prefix = hash.substring(0, 2);

        // 收集所有可能的头像根目录
        java.util.ArrayList dirs = new java.util.ArrayList();
        String base = findAvatarDir();
        if (!TextUtils.isEmpty(base)) {
            dirs.add(base + "/avatar/");
        }

        // 补充：遍历所有32位hash子目录，多账号场景更稳
        try {
            java.io.File mmDir = new java.io.File("/data/data/com.tencent.mm/MicroMsg/");
            java.io.File[] subs = mmDir.listFiles();
            if (subs != null) {
                for (int si = 0; si < subs.length; si++) {
                    java.io.File d = subs[si];
                    if (!d.isDirectory()) continue;
                    String n = d.getName();
                    if (n.length() == 32 && n.matches("[0-9a-f]{32}")) {
                        String candidate = d.getAbsolutePath() + "/avatar/";
                        if (!dirs.contains(candidate)) dirs.add(candidate);
                    }
                }
            }
        } catch (Throwable ignored) {}

        String[] tryNames = new String[]{
                "user_" + hash,
                "small_" + hash,
                hash,
                "hd_" + hash,
                "big_" + hash
        };
        String[] exts = new String[]{".png", ".jpg", ""};

        for (int di = 0; di < dirs.size(); di++) {
            String dir = String.valueOf(dirs.get(di));
            java.io.File dirFile = new java.io.File(dir);
            if (!dirFile.exists()) continue;

            // 根目录查找
            for (int ni = 0; ni < tryNames.length; ni++) {
                for (int ei = 0; ei < exts.length; ei++) {
                    String fullPath = dir + tryNames[ni] + exts[ei];
                    java.io.File f = new java.io.File(fullPath);
                    if (f.exists() && f.length() > 100) {
                        Bitmap b = BitmapFactory.decodeFile(fullPath);
                        if (b != null && b.getWidth() >= 50) return b;
                    }
                }
            }

            // prefix 子目录查找，例如 avatar/ab/user_abcd...
            String subDir = dir + prefix + "/";
            java.io.File subDirFile = new java.io.File(subDir);
            if (subDirFile.exists()) {
                for (int ni = 0; ni < tryNames.length; ni++) {
                    for (int ei = 0; ei < exts.length; ei++) {
                        String fullPath = subDir + tryNames[ni] + exts[ei];
                        java.io.File f = new java.io.File(fullPath);
                        if (f.exists() && f.length() > 100) {
                            Bitmap b = BitmapFactory.decodeFile(fullPath);
                            if (b != null && b.getWidth() >= 50) return b;
                        }
                    }
                }
            }
        }
    } catch (Throwable ignored) {}
    return null;
}


// CDN 下载头像，兜底，仅在本地缓存未命中时调用
Bitmap downloadAvatarFromCdn(String talkerId) {
    if (TextUtils.isEmpty(talkerId)) return null;
    try {
        String url = getAvatarUrl(talkerId);
        if (TextUtils.isEmpty(url)) return null;

        java.net.HttpURLConnection conn = null;
        java.io.InputStream is = null;
        try {
            conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(6000);
            conn.setRequestProperty("User-Agent", "MicroMessenger");
            conn.connect();

            if (conn.getResponseCode() != 200) return null;

            is = conn.getInputStream();
            Bitmap b = BitmapFactory.decodeStream(is);
            if (b != null && b.getWidth() >= 50) return b;
        } finally {
            try { if (is != null) is.close(); } catch (Throwable ignored) {}
            try { if (conn != null) conn.disconnect(); } catch (Throwable ignored) {}
        }
    } catch (Throwable ignored) {}
    return null;
}

// 统一头像获取入口：内存缓存 -> 本地文件 -> CDN
Bitmap resolveAvatar(String talkerId) {
    if (TextUtils.isEmpty(talkerId)) return null;

    try {
        Object cached = sAvatarCache.get(talkerId);
        if (cached instanceof Bitmap) {
            Bitmap b = (Bitmap) cached;
            if (!b.isRecycled()) return b;
        }
    } catch (Throwable ignored) {}

    try {
        Bitmap b = getGroupAvatarFromFile(talkerId);
        if (b != null) {
            cacheAvatar(b, talkerId);
            return b;
        }
    } catch (Throwable ignored) {}

    try {
        Bitmap b = downloadAvatarFromCdn(talkerId);
        if (b != null) {
            cacheAvatar(b, talkerId);
            return b;
        }
    } catch (Throwable ignored) {}

    return null;
}





// ================= MD5 工具 =================
String md5(String input) {
    try {
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
        byte[] digest = md.digest(input.getBytes("UTF-8"));
        String result = "";
        for (int i = 0; i < digest.length; i++) {
            result += String.format("%02x", digest[i] & 0xff);
        }
        return result;
    } catch (Throwable e) {
        return "";
    }
}
void hookSystemNotification() {
    try {
        if (sNotifyHookInstalled) return;
        sNotifyHookInstalled = true;

        Method[] methods = NotificationManager.class.getDeclaredMethods();
        for (int i = 0; i < methods.length; i++) {
            try {
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
                            
                            // 偷头像
                            try {
                                String t = resolveTargetTalkerFromNativeNotification(n);
                                if (TextUtils.isEmpty(t)) {
                                    if (n.extras != null) {
                                        CharSequence cs = n.extras.getCharSequence(Notification.EXTRA_TITLE);
                                        if (cs != null) {
                                            String title = cs.toString().trim();
                                            if (!TextUtils.isEmpty(title)) {
                                                Object[] allTargets = cacheTargetSet.toArray();
                                                for (int _i = 0; _i < allTargets.length; _i++) {
                                                    String tid = String.valueOf(allTargets[_i]);
                                                    if (!TextUtils.isEmpty(tid)) {
                                                        String displayName = resolveDisplayNameForTalker(tid);
                                                        if (!TextUtils.isEmpty(displayName) && title.contains(displayName)) {
                                                            t = tid;
                                                            break;
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                if (!TextUtils.isEmpty(t)) {
                                    Bitmap avatarBmp = null;
                                    if (Build.VERSION.SDK_INT >= 23) {
                                        try {
                                            Object icon = n.getLargeIcon();
                                            if (icon instanceof Bitmap) avatarBmp = (Bitmap) icon;
                                            else if (icon instanceof android.graphics.drawable.Icon) avatarBmp = ((android.graphics.drawable.Icon) icon).getBitmap();
                                        } catch (Throwable ignored) {}
                                    }
                                    if (avatarBmp == null && n.extras != null) {
                                        try {
                                            Object pic = n.extras.get("android.largeIcon");
                                            if (pic instanceof Bitmap) avatarBmp = (Bitmap) pic;
                                            else if (pic instanceof android.graphics.drawable.Icon) avatarBmp = ((android.graphics.drawable.Icon) pic).getBitmap();
                                        } catch (Throwable ignored) {}
                                        if (avatarBmp == null) {
                                            try {
                                                Object pic2 = n.extras.get("android.icon");
                                                if (pic2 instanceof Bitmap) avatarBmp = (Bitmap) pic2;
                                            } catch (Throwable ignored) {}
                                        }
                                    }
                                    if (avatarBmp != null && !avatarBmp.isRecycled() && avatarBmp.getWidth() >= 50) {
                                        cacheAvatar(avatarBmp, t);
                                    }
                                }
                            } catch (Throwable ignored) {}

                            if (shouldBlockNativeWechatNotification(n)) {
                                param.setResult(null);
                            }
                        } catch (Throwable ignoredHook) {}
                    }
                });
                if (unhook != null) notifyUnhooks.add(unhook);
            } catch (Throwable ignoredMethod) {}
        }
    } catch (Throwable ignored) {
        sNotifyHookInstalled = false;
    }
}

// ================= 核心 2：自定义通知发送器 =================

void onHandleMsg(Object msg) {
    ensureConfigLoadedForRuntime();
    if (cacheTargetSet.isEmpty()) return;
    try {
        // 先取 talker
        String talker = readStringByAccessors(msg, MSG_TALKER_METHODS, MSG_TALKER_FIELDS);
        if (TextUtils.isEmpty(talker)) {
            talker = readStringByAccessors(msg, new String[]{"getTalker", "getUsername", "getUserName"}, MSG_TALKER_FIELDS);
        }

        int type = getMsgTypeFromMsg(msg);
        if (TextUtils.isEmpty(talker)) talker = resolveTalkerForMsg(msg, "");

        String content = resolveMsgContentForNotify(msg, "");
        if (TextUtils.isEmpty(talker)) talker = resolveTalkerForMsg(msg, content);
        content = cleanNotifyContentByType(type, content);

        if (isSelfSentMsg(msg, talker, content)) {
            return;
        }

        // 快捷回复后短时间内跳过同一会话。
        // 私聊保持 3000ms，群聊缩短为 1000ms，避免误吞群内其他人的消息。
        if (!TextUtils.isEmpty(talker)) {
            Long lastReply = (Long) sQuickReplyTimestamps.get(talker);
            if (lastReply != null) {
                long gap = System.currentTimeMillis() - lastReply.longValue();
                long suppressMs = isGroupTalkerOrMsg(talker, msg) ? 1000L : 3000L;
                if (gap < suppressMs) {
                    return;
                }
            }
        }

        if (isSystemMessageLike(msg, talker, content, type)) return;
        if (!cacheTargetSet.contains(talker)) return;
        if (isCurrentChatTalker(talker)) return;

        ensureTalkerCfgLoaded(talker);
        Map cfg = getTalkerCfg(talker);

        int talkerMode = cfgGetInt(cfg, "mode", 1);
        boolean inMuteWindow = isNowInMuteWindowByCfg(cfg);
        boolean showDetail = cfgGetBool(cfg, "showDetail", cacheShowDetail);
        boolean talkerVibrate = cfgGetBool(cfg, "vibrate", cacheVibrate);
        boolean talkerSound = cfgGetBool(cfg, "sound", cacheSound);
        String talkerRingtone = cfgGet(cfg, "ringtone", "");
        boolean talkerQuickReply = cfgGetBool(cfg, "quickReply", false);

        if (talkerMode == 0) return;
        if (inMuteWindow) return;
        if (shouldSuppressByRules(msg, talker, content, cfg)) return;

        String[] notifyText = buildNotifyTitleAndText(msg, talker, content, type, showDetail);
        String notifyTitle = notifyText[0];
        String displayContent = notifyText[1];
        long msgTimestamp = System.currentTimeMillis();

        // 累计未读计数
        int unreadCount = 1;
        try {
            Object prev = sTalkerUnreadCount.get(talker);
            if (prev instanceof Integer) unreadCount = ((Integer) prev).intValue() + 1;
            sTalkerUnreadCount.put(talker, Integer.valueOf(unreadCount));
        } catch (Throwable ignored) {}

        // 直接发送通知，不再延迟 500ms。
        // 头像在 sendCustomNotification 内部异步补加载。
        sendCustomNotification(
                talker,
                notifyTitle,
                displayContent,
                talkerVibrate,
                talkerSound,
                talkerRingtone,
                talkerQuickReply,
                msgTimestamp
        );
    } catch (Throwable ignored) {}
}



// ================= 通知通道管理 =================
List getNotificationChannelList(NotificationManager nm) {
    if (nm == null || Build.VERSION.SDK_INT < 26) return null;
    try {
        return (List) nm.getClass().getMethod("getNotificationChannels").invoke(nm);
    } catch (Throwable ignored) {}
    return null;
}

void deleteChannelsWithPrefixes(NotificationManager nm, List channels, String[] prefixes) {
    if (nm == null || channels == null || prefixes == null) return;
    try {
        for (int i = 0; i < channels.size(); i++) {
            NotificationChannel ch = (NotificationChannel) channels.get(i);
            if (ch == null) continue;
            String chId = ch.getId();
            if (TextUtils.isEmpty(chId)) continue;
            for (int j = 0; j < prefixes.length; j++) {
                String prefix = prefixes[j];
                if (!TextUtils.isEmpty(prefix) && chId.startsWith(prefix)) {
                    nm.deleteNotificationChannel(chId);
                    break;
                }
            }
        }
    } catch (Throwable ignored) {}
}

void trimCustomChannels(NotificationManager nm, List channels, int keepMax) {
    if (nm == null || channels == null || keepMax <= 0) return;
    try {
        List ids = new ArrayList();
        for (int i = 0; i < channels.size(); i++) {
            NotificationChannel ch = (NotificationChannel) channels.get(i);
            if (ch == null) continue;
            String id = ch.getId();
            if (id != null && id.startsWith(CUSTOM_CHANNEL_PREFIX)) ids.add(id);
        }
        int extra = ids.size() - keepMax;
        if (extra <= 0) return;
        for (int i = 0; i < extra; i++) {
            try { nm.deleteNotificationChannel(String.valueOf(ids.get(i))); } catch (Throwable ignored) {}
        }
    } catch (Throwable ignored) {}
}

void rebuildNotificationChannel() {
    if (Build.VERSION.SDK_INT < 26) return;
    try {
        NotificationManager nm = (NotificationManager) hostContext.getSystemService(Context.NOTIFICATION_SERVICE);
        List channels = getNotificationChannelList(nm);
        deleteChannelsWithPrefixes(nm, channels, LEGACY_CHANNEL_PREFIXES);
        trimCustomChannels(nm, getNotificationChannelList(nm), 100);
        ensureNotifyChannel(nm, currentChannelId, cacheVibrate, cacheSound, "");
    } catch (Throwable ignored) {}
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

    try {
        // channel 已存在就直接复用，绝不 deleteNotificationChannel
        // 避免部分系统删除 channel 时顺带清掉该 channel 下已有通知
        NotificationChannel old = nm.getNotificationChannel(channelId);
        if (old != null) {
            return;
        }
    } catch (Throwable ignored) {}

    try {
        Uri targetSound = resolveNotifySoundUri(useSound, ring);

        NotificationChannel c = new NotificationChannel(
                channelId,
                "接管控制通知",
                NotificationManager.IMPORTANCE_HIGH
        );

        if (useVibrate) {
            c.enableVibration(true);
            c.setVibrationPattern(new long[]{0, 250, 250, 250});
        } else {
            c.enableVibration(false);
            c.setVibrationPattern(new long[]{0});
        }

        if (useSound) {
            c.setSound(targetSound, buildNotifyAudioAttrs());
        } else {
            c.setSound(null, null);
        }

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
            java.io.InputStream in = hostContext.getContentResolver().openInputStream(u);
            if (in == null) return false;
            try { in.close(); } catch (Throwable ignored4) {}
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

// ================= 通知构建 =================
void applyBasicNotificationOptions(Notification.Builder builder, String title, String text, boolean useVibrate, boolean useSound, String talkerRing, boolean useManualCustomSound) {
    if (builder == null) return;
    builder.setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false);

    // 自定义通知图标
    try {
        String iconPath = (cacheNotifyIcon != null && !cacheNotifyIcon.isEmpty())
                ? cacheNotifyIcon
                : "/storage/emulated/0/Android/media/com.tencent.mm/WAuxiliary/Plugin/自定义通知配置版/tongzhi.png";
        android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeFile(iconPath);
        if (bmp != null && Build.VERSION.SDK_INT >= 23) {
            builder.setSmallIcon(android.graphics.drawable.Icon.createWithBitmap(bmp));
        } else {
            builder.setSmallIcon(android.R.drawable.stat_notify_chat);
        }
    } catch (Throwable ignored) {
        builder.setSmallIcon(android.R.drawable.stat_notify_chat);
    }

    try { builder.setCategory(Notification.CATEGORY_MESSAGE); } catch (Throwable ignored) {}
    try { builder.setVisibility(Notification.VISIBILITY_PRIVATE); } catch (Throwable ignored) {}

    long[] vibPattern = useVibrate ? new long[]{0, 250, 250, 250} : new long[]{0};
    try { builder.setVibrate(vibPattern); } catch (Throwable ignored) {}

    Uri soundUri = resolveNotifySoundUri(Build.VERSION.SDK_INT >= 26 ? false : (useManualCustomSound ? false : useSound), talkerRing);
    try { builder.setSound(soundUri); } catch (Throwable ignored) {}
}

void applyLegacyAlertOptions(Notification.Builder builder, boolean useVibrate, boolean useSound, String talkerRing, boolean useManualCustomSound) {
    if (builder == null || Build.VERSION.SDK_INT >= 26) return;
    int defaults = 0;
    if (useVibrate) defaults |= Notification.DEFAULT_VIBRATE;
    if (useSound) {
        if (TextUtils.isEmpty(talkerRing)) defaults |= Notification.DEFAULT_SOUND;
        else if (!useManualCustomSound) {
            try { builder.setSound(Uri.parse(talkerRing)); } catch (Throwable ignored) {}
        }
    }
    builder.setDefaults(defaults);
    try { builder.setPriority(Notification.PRIORITY_HIGH); } catch (Throwable ignored) {}
}

void attachChatOpenIntent(Notification.Builder builder, String talker, int notifyId) {
    if (builder == null) return;
    try {
        Intent[] intents = buildChatOpenIntents(talker);
        if (intents == null || intents.length == 0) return;
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        builder.setContentIntent(PendingIntent.getActivities(hostContext, notifyId, intents, flags));
    } catch (Throwable ignored) {}
}

void attachQuickReplyAction(Notification.Builder builder, String talker, int notifyId, boolean enableQuickReply) {
    if (builder == null || !enableQuickReply) return;
    try {
        RemoteInput remoteInput = new RemoteInput.Builder("key_reply_content")
                .setLabel("输入回复内容...")
                .build();

        Intent replyIntent = new Intent(ACTION_QUICK_REPLY);
        replyIntent.setPackage(hostContext.getPackageName());
        replyIntent.putExtra(EXTRA_TALKER, talker);
        replyIntent.putExtra(EXTRA_NOTIFY_ID, notifyId);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;

        // 使用稳定 requestCode，避免随机 PendingIntent 残留导致重复广播
        int uniqueCode = 0x51000000 ^ notifyId ^ (talker == null ? 0 : talker.hashCode());

        PendingIntent replyPi = PendingIntent.getBroadcast(hostContext, uniqueCode, replyIntent, flags);

        Notification.Action action = new Notification.Action.Builder(
                android.R.drawable.ic_menu_send,
                "快捷回复",
                replyPi
        ).addRemoteInput(remoteInput).build();

        builder.addAction(action);
    } catch (Throwable ignored) {}
}


void playSoundAfterNotify(boolean useSound, String talkerRing, boolean useManualCustomSound) {
    try {
        if (Build.VERSION.SDK_INT >= 26) {
            String playableRing = talkerRing;
            if (!TextUtils.isEmpty(playableRing) && !ensureUriReadable(playableRing)) playableRing = "";
            playNotifySoundByConfig(useSound, playableRing);
        } else if (useManualCustomSound && ensureUriReadable(talkerRing)) {
            playCustomRingtoneFallback(talkerRing);
        }
    } catch (Throwable ignored) {}
}


void sendCustomNotification(String talker, String title, String text, boolean useVibrate, boolean useSound, String ringtoneUri, boolean enableQuickReply, long msgTimestamp) {
    try {
        // 发通知时实时读计数，避免多条消息快速到达时 snapshot 过期导致覆盖
        int unreadCount = 1;
        try {
            Object cur = sTalkerUnreadCount.get(talker);
            if (cur instanceof Integer) unreadCount = ((Integer) cur).intValue();
        } catch (Throwable ignored) {}

        String displayTitle = title;
        String displayText = (unreadCount >= 2) ? ("[" + unreadCount + "]" + text) : text;

        NotificationManager nm = (NotificationManager) hostContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        Notification.Builder builder;
        String talkerChannelId = currentChannelId;
        String talkerRing = normalizeRingtoneUri(ringtoneUri);
        boolean useManualCustomSound = useSound && !TextUtils.isEmpty(talkerRing);

        if (Build.VERSION.SDK_INT >= 26) {
            talkerChannelId = buildTalkerChannelId(useSound, useVibrate, talkerRing);
            ensureNotifyChannel(nm, talkerChannelId, useVibrate, false, talkerRing);
            builder = new Notification.Builder(hostContext, talkerChannelId);
        } else {
            builder = new Notification.Builder(hostContext);
        }

        applyBasicNotificationOptions(builder, displayTitle, displayText, useVibrate, useSound, talkerRing, useManualCustomSound);

        // 只读内存缓存，不在主流程同步读文件，避免主线程卡顿。
        Bitmap _cachedBmp = null;
        try {
            Object cached = sAvatarCache.get(talker);
            if (cached instanceof Bitmap && !((Bitmap) cached).isRecycled()) {
                _cachedBmp = (Bitmap) cached;
            }
        } catch (Throwable ignored) {}

        final Bitmap cachedBmp = _cachedBmp;
        if (cachedBmp != null) {
            try { builder.setLargeIcon(cachedBmp); } catch (Throwable ignored) {}
        }

        if (msgTimestamp > 0) {
            try {
                builder.setWhen(msgTimestamp);
                builder.setShowWhen(true);
            } catch (Throwable ignored) {}
        }

        Bundle extras = new Bundle();
        extras.putBoolean(JAY_MARK, true);
        extras.putString("jay_talker", talker);
        builder.setExtras(extras);

        applyLegacyAlertOptions(builder, useVibrate, useSound, talkerRing, useManualCustomSound);

        int notifyId = getStableNotifyId(talker);

        attachChatOpenIntent(builder, talker, notifyId);
        attachQuickReplyAction(builder, talker, notifyId, enableQuickReply);

        nm.notify(notifyId, builder.build());
        playSoundAfterNotify(useSound, talkerRing, useManualCustomSound);

        // 异步补加载头像：本地文件/CDN 都放到后台线程，加载成功后更新通知。
        if (cachedBmp == null) {
            final String _talker = talker;
            final int _notifyId = notifyId;
            final String _displayTitle = displayTitle;
            final String _displayText = displayText;
            final boolean _useVibrate = useVibrate;
            final boolean _useSound = useSound;
            final String _talkerRing = talkerRing;
            final boolean _useManualCustomSound = useManualCustomSound;
            final String _talkerChannelId = talkerChannelId;
            final long _msgTimestamp = msgTimestamp;
            final boolean _enableQuickReply = enableQuickReply;

            new Thread(new Runnable() {
                public void run() {
                    try {
                        final Bitmap bmp = resolveAvatar(_talker);
                        if (bmp == null || bmp.isRecycled()) return;

                        sMainHandler.post(new Runnable() {
                            public void run() {
                                try {
                                    NotificationManager nm2 = (NotificationManager) hostContext.getSystemService(Context.NOTIFICATION_SERVICE);
                                    if (nm2 == null) return;

                                    Notification.Builder b2;
                                    if (Build.VERSION.SDK_INT >= 26) {
                                        b2 = new Notification.Builder(hostContext, _talkerChannelId);
                                    } else {
                                        b2 = new Notification.Builder(hostContext);
                                    }

                                    applyBasicNotificationOptions(b2, _displayTitle, _displayText, _useVibrate, _useSound, _talkerRing, _useManualCustomSound);
                                    b2.setLargeIcon(bmp);

                                    if (_msgTimestamp > 0) {
                                        b2.setWhen(_msgTimestamp);
                                        b2.setShowWhen(true);
                                    }

                                    Bundle extras2 = new Bundle();
                                    extras2.putBoolean(JAY_MARK, true);
                                    extras2.putString("jay_talker", _talker);
                                    b2.setExtras(extras2);

                                    applyLegacyAlertOptions(b2, _useVibrate, _useSound, _talkerRing, _useManualCustomSound);
                                    attachChatOpenIntent(b2, _talker, _notifyId);
                                    attachQuickReplyAction(b2, _talker, _notifyId, _enableQuickReply);

                                    // 更新头像时不重新提醒
                                    b2.setOnlyAlertOnce(true);
                                    nm2.notify(_notifyId, b2.build());
                                } catch (Throwable ignored) {}
                            }
                        });
                    } catch (Throwable ignored) {}
                }
            }).start();
        }
    } catch (Throwable ignored) {}
}



int getStableNotifyId(String talker) {
    if (TextUtils.isEmpty(talker)) return 0x4A000002;
    try {
        String key = "jay_nid_" + talker;
        SharedPreferences sp = hostContext.getSharedPreferences("jay_notify_ids", Context.MODE_PRIVATE);
        int existing = sp.getInt(key, -1);
        if (existing != -1) {
            return existing;
        }

        // 新分配：用 MD5 派生初始 ID，碰撞时自动 +1 直到不冲突
        String hash = md5(talker);
        int base = 0x4A000003;
        if (!TextUtils.isEmpty(hash)) {
            long v = Long.parseLong(hash.substring(0, 7), 16);
            base = (int) (0x4A000000L | (v & 0x00FFFFFFL));
            if (base == 0x4A000002) base++;
        }

        Map allEntries = sp.getAll();
        Set usedIds = new HashSet();
        Object[] vals = allEntries.values().toArray();
        for (int i = 0; i < vals.length; i++) {
            if (vals[i] instanceof Integer) usedIds.add(vals[i]);
        }

        int id = base;
        while (usedIds.contains(Integer.valueOf(id))) id++;

        sp.edit().putInt(key, id).apply();
        return id;
    } catch (Throwable ignored) {}

    return 0x4A000002;
}


Class findWechatNotificationItemClass() {
    String clsName = "com.tencent.mm.booter.notification.NotificationItem";
    try {
        ClassLoader cl = hostContext == null ? null : hostContext.getClassLoader();
        if (cl != null) return Class.forName(clsName, false, cl);
    } catch (Throwable ignored) {}
    try {
        ClassLoader cl2 = Thread.currentThread().getContextClassLoader();
        if (cl2 != null) return Class.forName(clsName, false, cl2);
    } catch (Throwable ignored) {}
    try {
        List keys = new ArrayList();
        keys.add("id: ");
        keys.add("userName: ");
        keys.add("unreadCount:");
        List clsList = findClassList(keys);
        if (clsList != null && clsList.size() > 0) {
            for (int i = 0; i < clsList.size(); i++) {
                Object c = clsList.get(i);
                if (c instanceof Class) {
                    String n = ((Class) c).getName();
                    if (clsName.equals(n)) return (Class) c;
                    String low = n == null ? "" : n.toLowerCase();
                    if (low.indexOf("com.tencent.mm.booter.notification") >= 0) return (Class) c;
                    if (n != null && n.endsWith(".NotificationItem")) return (Class) c;
                }
            }
        }
    } catch (Throwable ignored) {}
    return null;
}

String findConfiguredTalkerExact(String raw) {
    if (TextUtils.isEmpty(raw) || cacheTargetSet == null || cacheTargetSet.isEmpty()) return "";
    try {
        String v = raw.trim();
        return cacheTargetSet.contains(v) ? v : "";
    } catch (Throwable ignored) {}
    return "";
}

Object firstFieldValueByType(Object obj, Class type) {
    if (obj == null || type == null) return null;
    try {
        java.lang.reflect.Field[] fs = obj.getClass().getDeclaredFields();
        if (fs == null) return null;
        for (int i = 0; i < fs.length; i++) {
            java.lang.reflect.Field f = fs[i];
            if (f == null || !type.isAssignableFrom(f.getType())) continue;
            f.setAccessible(true);
            Object v = f.get(obj);
            if (v != null) return v;
        }
    } catch (Throwable ignored) {}
    return null;
}

String getTalkerFromNotificationItem(Object item) {
    if (item == null) return "";
    try {
        Object h = safeGetFieldAny(item, WECHAT_NOTIFICATION_ITEM_TALKER_FIELDS);
        String hit = findConfiguredTalkerExact(h == null ? "" : String.valueOf(h));
        if (!TextUtils.isEmpty(hit)) return hit;
    } catch (Throwable ignored) {}
    return "";
}

Notification getNotificationFromItem(Object item) {
    if (item == null) return null;
    try {
        Object n = safeGetFieldAny(item, WECHAT_NOTIFICATION_ITEM_NOTIFICATION_FIELDS);
        if (n instanceof Notification) return (Notification) n;
    } catch (Throwable ignored) {}
    try {
        Object n2 = firstFieldValueByType(item, Notification.class);
        if (n2 instanceof Notification) return (Notification) n2;
    } catch (Throwable ignored) {}
    return null;
}

void markWechatNotificationTalker(Object item) {
    try {
        ensureConfigLoadedForRuntime();
        if (cacheTargetSet.isEmpty()) return;
        String talker = getTalkerFromNotificationItem(item);
        if (TextUtils.isEmpty(talker) || !cacheTargetSet.contains(talker)) return;
        Notification n = getNotificationFromItem(item);
        if (n == null) return;
        if (n.extras == null) n.extras = new Bundle();
        n.extras.putString(JAY_WECHAT_TALKER, talker);
    } catch (Throwable ignored) {}
}

void hookWechatNotificationItemTalker() {
    try {
        if (sWechatNotificationItemHookInstalled) return;
        Class cls = findWechatNotificationItemClass();
        if (cls == null) return;
        sWechatNotificationItemHookInstalled = true;

        Set ctorUnhooks = XposedBridge.hookAllConstructors(cls, new XC_MethodHook() {
            protected void afterHookedMethod(de.robv.android.xposed.XC_MethodHook.MethodHookParam param) throws Throwable {
                markWechatNotificationTalker(param.thisObject);
            }
        });
        if (ctorUnhooks != null) notifyUnhooks.addAll(ctorUnhooks);

        Method[] ms = cls.getDeclaredMethods();
        for (int i = 0; i < ms.length; i++) {
            try {
                final Method m = ms[i];
                Class[] ps = m.getParameterTypes();
                if (ps == null || ps.length != 1 || ps[0] != Context.class) continue;
                Object uh = XposedBridge.hookMethod(m, new XC_MethodHook() {
                    protected void beforeHookedMethod(de.robv.android.xposed.XC_MethodHook.MethodHookParam param) throws Throwable {
                        markWechatNotificationTalker(param.thisObject);
                    }
                });
                if (uh != null) notifyUnhooks.add(uh);
            } catch (Throwable ignoredMethod) {}
        }
    } catch (Throwable ignored) {
        sWechatNotificationItemHookInstalled = false;
    }
}

// ================= 通知提取辅助 =================
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

// ================= 标题缓存与群聊标题匹配（1.3.4 保留） =================
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
        return s.replaceAll("\\[[^\\]]*\\]", "");
    } catch (Throwable ignored) {}
    return s;
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
    try {
        Object raw = b.get(JAY_WECHAT_TALKER);
        String hit = findConfiguredTalkerExact(raw == null ? "" : String.valueOf(raw));
        return TextUtils.isEmpty(hit) ? null : hit;
    } catch (Throwable ignored) {}
    return null;
}

String extractTalkerFromNotification(Notification n) {
    if (n == null) return null;
    try {
        String t = scanBundleForTalker(n.extras);
        if (t != null) return t;
    } catch (Throwable ignored) {}
    return null;
}

String resolveTargetTalkerFromNativeNotification(Notification n) {
    if (n == null || cacheTargetSet == null || cacheTargetSet.isEmpty()) return "";
    try {
        String talker = extractTalkerFromNotification(n);
        if (!TextUtils.isEmpty(talker) && cacheTargetSet.contains(talker)) return talker;
    } catch (Throwable ignored) {}
    return "";
}

boolean isPluginOwnedNotification(Notification n) {
    if (n == null) return false;
    try {
        if (n.extras != null && n.extras.getBoolean(JAY_MARK, false)) return true;
        if (n.extras != null && n.extras.getBoolean(KEYWORD_NOTIFY_MARK, false)) return true;
    } catch (Throwable ignored) {}
    try {
        if (Build.VERSION.SDK_INT >= 26) {
            String ch = n.getChannelId();
            if (ch != null && (ch.startsWith(CUSTOM_CHANNEL_PREFIX) || ch.startsWith(KEYWORD_CHANNEL_PREFIX))) return true;
        }
    } catch (Throwable ignored) {}
    return false;
}

boolean shouldBlockNativeWechatNotification(Notification n) {
    if (n == null || isPluginOwnedNotification(n)) return false;
    try {
        String talker = resolveTargetTalkerFromNativeNotification(n);
        if (TextUtils.isEmpty(talker) || !cacheTargetSet.contains(talker)) return false;
        ensureTalkerCfgLoaded(talker);
        return shouldBlockNativeByCfg(getTalkerCfg(talker));
    } catch (Throwable ignored) {}
    return false;
}

String resolveDisplayNameForTalker(String talker) {
    if (TextUtils.isEmpty(talker)) return "";
    try {
        String name = resolveTalkerNameForMatch(talker);
        if (TextUtils.isEmpty(name)) name = getFriendName(talker);
        if (TextUtils.isEmpty(name)) name = talker;
        return name;
    } catch (Throwable ignored) {}
    return talker;
}

String[] buildNotifyTitleAndText(Object msg, String talker, String content, int type, boolean showDetail) {
    String title = resolveDisplayNameForTalker(talker);
    String text = "[收到一条新消息]";
    if (!showDetail) return new String[]{title, text};
    try {
        content = cleanNotifyContentByType(type, content);
        if (!isGroupTalkerOrMsg(talker, msg)) {
            if (TextUtils.isEmpty(content)) return new String[]{title, text};
            if (type != 1 || isRawXmlLike(content)) return new String[]{title, getReadableMsgPlaceholder(type, content)};
            return new String[]{title, content};
        }
        String[] sender = extractGroupSenderInfo(msg, talker, content);
        String sid = sender[0], sname = sender[1], pure = sender[2];
        String senderLabel = !TextUtils.isEmpty(sname) ? sname : sid;
        String groupName = resolveDisplayNameForTalker(talker);
        if (!TextUtils.isEmpty(groupName)) title = groupName;
        pure = cleanNotifyContentByType(type, pure);
        if (TextUtils.isEmpty(pure)) text = "[收到一条新消息]";
        else if (type != 1 || isRawXmlLike(pure)) {
            pure = getReadableMsgPlaceholder(type, pure);
            text = TextUtils.isEmpty(senderLabel) ? pure : senderLabel + ": " + pure;
        } else text = TextUtils.isEmpty(senderLabel) ? pure : senderLabel + ": " + pure;
    } catch (Throwable ignored) {}
    return new String[]{title, text};
}

// ================= onActivityResult Hook =================
Uri getUriExtraCompat(Intent data, String key) {
    if (data == null || TextUtils.isEmpty(key)) return null;
    try {
        Object v = data.getParcelableExtra(key);
        if (v instanceof Uri) return (Uri) v;
    } catch (Throwable ignored) {}
    return null;
}

Uri firstUriInBundle(Bundle b) {
    if (b == null) return null;
    try {
        Set keys = b.keySet();
        if (keys == null) return null;
        Object[] arr = keys.toArray();
        for (int i = 0; i < arr.length; i++) {
            String k = String.valueOf(arr[i]);
            if (TextUtils.isEmpty(k)) continue;
            Object v = null;
            try { v = b.get(k); } catch (Throwable ignored) {}
            if (v instanceof Uri) return (Uri) v;
            if (v instanceof Intent) {
                try {
                    Uri u = ((Intent) v).getData();
                    if (u != null) return u;
                } catch (Throwable ignored) {}
                try {
                    Uri u2 = firstUriInBundle(((Intent) v).getExtras());
                    if (u2 != null) return u2;
                } catch (Throwable ignored) {}
            }
            if (v instanceof Bundle) {
                Uri u3 = firstUriInBundle((Bundle) v);
                if (u3 != null) return u3;
            }
        }
    } catch (Throwable ignored) {}
    return null;
}

Uri extractPickedRingtoneUri(Intent data, int requestCode) {
    if (data == null) return null;
    Uri uri = null;
    if (requestCode == REQ_PICK_RINGTONE_SYSTEM) {
        try { uri = (Uri) data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI); } catch (Throwable ignored) {}
        if (uri != null) return uri;
    }
    try { uri = data.getData(); } catch (Throwable ignored) {}
    if (uri != null) return uri;

    String[] extraKeys = new String[]{Intent.EXTRA_STREAM, "android.intent.extra.STREAM", "selectedRingtoneUri"};
    for (int i = 0; i < extraKeys.length; i++) {
        uri = getUriExtraCompat(data, extraKeys[i]);
        if (uri != null) return uri;
    }
    try {
        uri = firstUriInBundle(data.getExtras());
        if (uri != null) return uri;
    } catch (Throwable ignored) {}

    try {
        ClipData cd = data.getClipData();
        if (cd != null && cd.getItemCount() > 0 && cd.getItemAt(0) != null) {
            uri = cd.getItemAt(0).getUri();
            if (uri != null) return uri;
            Intent itemIntent = cd.getItemAt(0).getIntent();
            if (itemIntent != null) {
                try { uri = itemIntent.getData(); } catch (Throwable ignored) {}
                if (uri != null) return uri;
            }
        }
    } catch (Throwable ignored) {}
    return null;
}

void takeReadPermissionIfPossible(Intent data, Uri uri) {
    if (data == null || uri == null || hostContext == null) return;
    try {
        if (!"content".equalsIgnoreCase(uri.getScheme())) return;
        int grantFlags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        if ((grantFlags & Intent.FLAG_GRANT_READ_URI_PERMISSION) == 0) return;
        int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
        hostContext.getContentResolver().takePersistableUriPermission(uri, takeFlags);
    } catch (Throwable ignored) {}
}

void hookActivityResultForRingtone() {
    try {
        if (sActivityResultHookInstalled) return;
        sActivityResultHookInstalled = true;
        XC_MethodHook resultHook = new XC_MethodHook() {
            protected void beforeHookedMethod(de.robv.android.xposed.XC_MethodHook.MethodHookParam param) throws Throwable {
                int requestCode = (Integer) param.args[0];
                int resultCode = (Integer) param.args[1];
                Intent data = (Intent) param.args[2];

                // 图标选择（1.3.4 保留）
                if (requestCode == REQ_PICK_ICON) {
                    if (resultCode == Activity.RESULT_OK && data != null) {
                        android.net.Uri uri = data.getData();
                        if (uri != null) {
                            try {
                                String iconPath = "/storage/emulated/0/Android/media/com.tencent.mm/WAuxiliary/Plugin/自定义通知配置版/notify_icon.png";
                                java.io.File parentDir = new java.io.File(iconPath).getParentFile();
                                if (parentDir != null && !parentDir.exists()) parentDir.mkdirs();
                                java.io.InputStream in = hostContext.getContentResolver().openInputStream(uri);
                                java.io.FileOutputStream out = new java.io.FileOutputStream(iconPath);
                                byte[] buf = new byte[4096];
                                int len;
                                while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                                in.close(); out.close();
                                cacheNotifyIcon = iconPath;
                                putString(CFG_NOTIFY_ICON, iconPath);
                                if (tvNotifyIcon != null) tvNotifyIcon.setText(iconPath.substring(iconPath.lastIndexOf('/') + 1) + " >");
                                toast("图标已设置");
                            } catch (Throwable e) {
                                toast("图标设置失败: " + e.getMessage());
                            }
                        }
                    }
                    return;
                }

                // 铃声选择
                if (requestCode == REQ_PICK_RINGTONE_SYSTEM || requestCode == REQ_PICK_RINGTONE_FILE) {
                    if (resultCode != Activity.RESULT_OK) return;
                    Uri uri = extractPickedRingtoneUri(data, requestCode);
                    if (uri == null) {
                        try { toast("没有获取到铃声文件"); } catch (Throwable ignored) {}
                        return;
                    }
                    takeReadPermissionIfPossible(data, uri);
                    String ring = uri == null ? "" : uri.toString();
                    if (!TextUtils.isEmpty(ring)) {
                        ring = freezeRingtoneUri(ring);
                    }
                    if (TextUtils.isEmpty(ring)) {
                        try { toast("铃声保存失败"); } catch (Throwable ignored) {}
                        return;
                    }
                    if (globalRingtoneValueRef != null && globalRingtoneValueRef.length > 0) {
                        globalRingtoneValueRef[0] = ring;
                    }
                    Activity top0 = globalSettingActivity;
                    if (top0 == null) {
                        try { top0 = getTopActivity(); } catch (Throwable ignored) {}
                    }
                    final Activity top = top0;
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
    } catch (Throwable ignored) {
        sActivityResultHookInstalled = false;
    }
}

// ================= 通用反射 =================
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

// ================= UI 系统 =================
Activity globalSettingActivity;
TextView globalRingtoneValueView;
String[] globalRingtoneValueRef;

void clearGlobalRingtonePickState() {
    globalRingtoneValueView = null;
    globalRingtoneValueRef = null;
}

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

// ================= UI 工具方法 =================
FrameLayout makeDimMask(Activity ctx) {
    FrameLayout mask = new FrameLayout(ctx);
    mask.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
    mask.setBackgroundColor(Color.parseColor("#66000000"));
    return mask;
}

GradientDrawable makeDialogCardBg(Activity ctx, int radiusDp) {
    GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Color.parseColor("#FFFFFF"), Color.parseColor("#F8FAFC")});
    bg.setCornerRadius(dp(ctx, radiusDp));
    bg.setStroke(dp(ctx, 1), Color.parseColor("#DDE6F2"));
    return bg;
}

void applyFullScreenDialogWindow(Dialog dialog, int softInputMode) {
    try {
        Window w = dialog == null ? null : dialog.getWindow();
        if (w == null) return;
        w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        w.setGravity(Gravity.CENTER);
        w.setDimAmount(0.25f);
        if (softInputMode != 0) w.setSoftInputMode(softInputMode);
    } catch (Throwable ignored) {}
}

void applyFullScreenResizeDialogWindow(Dialog dialog) {
    try {
        Window w = dialog == null ? null : dialog.getWindow();
        if (w == null) return;
        w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        w.setGravity(Gravity.CENTER);
        w.setDimAmount(0.25f);
        w.clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        w.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    } catch (Throwable ignored) {}
}

TextView makeNeutralButton(Activity ctx, String text, int hp, int vp) {
    TextView b = new TextView(ctx);
    b.setText(text);
    b.setTextColor(Color.parseColor("#334155"));
    b.setTextSize(14f);
    b.setPadding(dp(ctx, hp), dp(ctx, vp), dp(ctx, hp), dp(ctx, vp));
    GradientDrawable bg = roundRect(Color.parseColor("#EFF3F8"), dp(ctx, 999));
    bg.setStroke(dp(ctx, 1), Color.parseColor("#DEE7F2"));
    b.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.parseColor("#22000000")), bg, null));
    return b;
}

TextView makePrimaryButton(Activity ctx, String text, int hp, int vp) {
    TextView b = new TextView(ctx);
    b.setText(text);
    b.setTextColor(Color.WHITE);
    b.setTextSize(14f);
    b.setTypeface(null, Typeface.BOLD);
    b.setPadding(dp(ctx, hp), dp(ctx, vp), dp(ctx, hp), dp(ctx, vp));
    GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Color.parseColor("#2563EB"), Color.parseColor("#7C3AED")});
    bg.setCornerRadius(dp(ctx, 999));
    b.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.parseColor("#55FFFFFF")), bg, null));
    return b;
}

TextView makePillButton(Activity ctx, String text, String textColor, String bgColor) {
    TextView b = new TextView(ctx);
    b.setText(text);
    b.setTextColor(Color.parseColor(textColor));
    b.setTextSize(13f);
    b.setPadding(dp(ctx, 10), dp(ctx, 6), dp(ctx, 10), dp(ctx, 6));
    b.setBackground(roundRect(Color.parseColor(bgColor), dp(ctx, 999)));
    return b;
}

TextView makeDialogTitle(Activity ctx, String text) {
    TextView t = new TextView(ctx);
    t.setText(text);
    t.setTextColor(Color.parseColor("#0F172A"));
    t.setTextSize(18f);
    t.setTypeface(null, Typeface.BOLD);
    return t;
}

TextView makeDialogTitleSized(Activity ctx, String text, float sp) {
    TextView t = makeDialogTitle(ctx, text);
    t.setTextSize(sp);
    return t;
}

TextView makeDialogSubText(Activity ctx, String text) {
    TextView t = new TextView(ctx);
    t.setText(text);
    t.setTextColor(Color.parseColor("#64748B"));
    t.setTextSize(13f);
    return t;
}

EditText makeDialogSearchInput(Activity ctx, String hint) {
    EditText et = new EditText(ctx);
    et.setHint(hint);
    et.setSingleLine(true);
    et.setTextSize(14f);
    et.setTextColor(Color.parseColor("#0F172A"));
    et.setHintTextColor(Color.parseColor("#94A3B8"));
    GradientDrawable bg = roundRect(Color.parseColor("#F8FAFC"), dp(ctx, 12));
    bg.setStroke(dp(ctx, 1), Color.parseColor("#E2E8F0"));
    et.setBackground(bg);
    et.setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 12), dp(ctx, 10));
    prepareSearchInput(ctx, et);
    return et;
}

TextView addDialogCountText(Activity ctx, LinearLayout card) {
    TextView tv = new TextView(ctx);
    tv.setTextSize(12f);
    tv.setTextColor(Color.parseColor("#64748B"));
    card.addView(tv, topMarginLp(ctx, 8));
    return tv;
}

LinearLayout makeDialogListWrap(Activity ctx, int heightDp, int topMarginDp) {
    LinearLayout wrap = new LinearLayout(ctx);
    wrap.setOrientation(LinearLayout.VERTICAL);
    GradientDrawable bg = roundRect(Color.parseColor("#F8FAFC"), dp(ctx, 12));
    bg.setStroke(dp(ctx, 1), Color.parseColor("#E2E8F0"));
    wrap.setBackground(bg);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(ctx, heightDp));
    lp.topMargin = dp(ctx, topMarginDp);
    wrap.setLayoutParams(lp);
    return wrap;
}

ListView makeDialogListView(Activity ctx, int choiceMode, boolean hideScrollBar) {
    ListView lv = new ListView(ctx);
    lv.setChoiceMode(choiceMode);
    lv.setDivider(new android.graphics.drawable.ColorDrawable(Color.parseColor("#E8EEF5")));
    lv.setDividerHeight(1);
    lv.setSelector(new android.graphics.drawable.ColorDrawable(Color.parseColor("#12000000")));
    lv.setPadding(dp(ctx, 6), dp(ctx, 6), dp(ctx, 6), dp(ctx, 6));
    lv.setClipToPadding(false);
    if (hideScrollBar) lv.setVerticalScrollBarEnabled(false);
    return lv;
}

Dialog makeBaseDialog(Activity ctx, boolean cancelable) {
    Dialog d = new Dialog(ctx, android.R.style.Theme_Translucent_NoTitleBar);
    d.requestWindowFeature(Window.FEATURE_NO_TITLE);
    d.setCancelable(cancelable);
    return d;
}

LinearLayout makeActionsRow(Activity ctx, int topMarginDp) {
    LinearLayout actions = new LinearLayout(ctx);
    actions.setLayoutParams(topMarginLp(ctx, topMarginDp));
    actions.setGravity(Gravity.END);
    return actions;
}

void addActionButton(Activity ctx, LinearLayout actions, View button, int leftMarginDp) {
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
    lp.leftMargin = dp(ctx, leftMarginDp);
    actions.addView(button, lp);
}

LinearLayout.LayoutParams topMarginLp(Activity ctx, int topMarginDp) {
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.topMargin = dp(ctx, topMarginDp);
    return lp;
}

LinearLayout makeVerticalDialogCard(Activity ctx, int marginDp, int padH, int padTop, int padBottom) {
    LinearLayout card = new LinearLayout(ctx);
    card.setOrientation(LinearLayout.VERTICAL);
    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2);
    lp.leftMargin = dp(ctx, marginDp);
    lp.rightMargin = dp(ctx, marginDp);
    lp.gravity = Gravity.CENTER;
    card.setLayoutParams(lp);
    card.setPadding(dp(ctx, padH), dp(ctx, padTop), dp(ctx, padH), dp(ctx, padBottom));
    return card;
}

void bindMaskDismiss(View mask, View card, final Dialog dialog) {
    mask.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { dialog.dismiss(); } });
    card.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {} });
}

CompoundButton.OnCheckedChangeListener boolSwitchListener(final boolean[] ref) {
    return new CompoundButton.OnCheckedChangeListener() {
        public void onCheckedChanged(CompoundButton b, boolean c) { ref[0] = c; }
    };
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

// ================= 主设置页 UI =================
void buildMainUI(final Activity ctx) {
    hideSoftInput(ctx);
    try {
        final Dialog dialog = makeBaseDialog(ctx, true);

        FrameLayout root = makeDimMask(ctx);

        LinearLayout card = makeVerticalDialogCard(ctx, 20, 18, 18, 12);

        card.setBackground(makeDialogCardBg(ctx, 20));

        TextView title = makeDialogTitleSized(ctx, "通知设置", 20f);
        card.addView(title);

        TextView sub = makeDialogSubText(ctx, "按会话单独配置通知规则");
        LinearLayout.LayoutParams subLp = topMarginLp(ctx, 8);
        sub.setLayoutParams(subLp);
        card.addView(sub);

        LinearLayout actionCard = new LinearLayout(ctx);
        actionCard.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams acLp = topMarginLp(ctx, 14);
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

        // 通知图标行（1.3.4 保留）
        String iconDisplayName = (cacheNotifyIcon != null && !cacheNotifyIcon.isEmpty())
                ? cacheNotifyIcon.substring(cacheNotifyIcon.lastIndexOf('/') + 1) + " >"
                : "未设置（使用默认）>";
        LinearLayout rowIcon = createRowText(ctx, "通知图标", iconDisplayName, false);
        tvNotifyIcon = (TextView) rowIcon.getChildAt(1);
        rowIcon.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    Intent intent = new Intent(Intent.ACTION_PICK,
                            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                    intent.setType("image/*");
                    ctx.startActivityForResult(Intent.createChooser(intent, "选择通知图标"), REQ_PICK_ICON);
                } catch (Throwable e) {
                    toast("无法打开图片选择器");
                }
            }
        });
        actionCard.addView(rowIcon);

        card.addView(actionCard);

        LinearLayout actions = makeActionsRow(ctx, 16);

        TextView btnClose = makeNeutralButton(ctx, "关闭", 16, 8);
        actions.addView(btnClose);

        card.addView(actions);
        root.addView(card);
        dialog.setContentView(root);

        bindMaskDismiss(root, card, dialog);

        btnClose.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { dialog.dismiss(); }
        });

        applyFullScreenDialogWindow(dialog, WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);

        dialog.show();
        card.setAlpha(0f);
        card.setTranslationY(dp(ctx, 20));
        card.animate().alpha(1f).translationY(0).setDuration(180).start();

    } catch (Throwable e) {
        toast("打开设置界面失败");
    }
}

// ================= 铃声选择对话框 =================
void showRingtonePickStyleDialog(final Activity ctx, final String[] tmpRingtone, final TextView tvRing) {
    try {
        final Dialog pickDialog = makeBaseDialog(ctx, true);

        FrameLayout mask = makeDimMask(ctx);

        LinearLayout card = makeVerticalDialogCard(ctx, 24, 16, 14, 12);

        card.setBackground(makeDialogCardBg(ctx, 18));

        TextView title = makeDialogTitleSized(ctx, "选择方式", 17f);
        card.addView(title);

        TextView sub = makeDialogSubText(ctx, "选择系统铃声或从文件夹导入");
        LinearLayout.LayoutParams subLp = topMarginLp(ctx, 6);
        sub.setLayoutParams(subLp);
        card.addView(sub);

        LinearLayout btnSys = createRowText(ctx, "选择系统铃声", "推荐 >", false);
        btnSys.setBackground(roundRect(Color.parseColor("#F1F5F9"), dp(ctx, 12)));
        LinearLayout.LayoutParams sysLp = topMarginLp(ctx, 12);
        card.addView(btnSys, sysLp);
        btnSys.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
                    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
                    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
                    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                    if (!TextUtils.isEmpty(tmpRingtone[0])) intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(tmpRingtone[0]));
                    ctx.startActivityForResult(intent, REQ_PICK_RINGTONE_SYSTEM);
                } catch (Throwable ignored) {}
                try { pickDialog.dismiss(); } catch (Throwable ignored) {}
            }
        });

        LinearLayout btnFile = createRowText(ctx, "从文件夹选择", "音频文件 >", false);
        btnFile.setBackground(roundRect(Color.parseColor("#F1F5F9"), dp(ctx, 12)));
        LinearLayout.LayoutParams fileLp = topMarginLp(ctx, 8);
        card.addView(btnFile, fileLp);
        btnFile.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    Intent fileIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    fileIntent.setType("audio/*");
                    fileIntent.addCategory(Intent.CATEGORY_OPENABLE);
                    fileIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    fileIntent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

                    Intent nativeIntent = new Intent(fileIntent);
                    nativeIntent.setPackage("com.android.documentsui");
                    try {
                        ctx.startActivityForResult(nativeIntent, REQ_PICK_RINGTONE_FILE);
                    } catch (Throwable ignoredNative) {
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
        LinearLayout.LayoutParams cancelLp = topMarginLp(ctx, 6);
        card.addView(btnCancel, cancelLp);
        btnCancel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try { pickDialog.dismiss(); } catch (Throwable ignored) {}
            }
        });

        mask.addView(card);
        bindMaskDismiss(mask, card, pickDialog);

        pickDialog.setContentView(mask);
        applyFullScreenDialogWindow(pickDialog, 0);
        pickDialog.show();
        card.setAlpha(0f);
        card.setTranslationY(dp(ctx, 14));
        card.animate().alpha(1f).translationY(0).setDuration(160).start();
    } catch (Throwable ignored) {}
}

// ================= 会话列表 UI =================
void showTargetListUI(final Activity ctx) {
    if (sCachedFriendNames != null && sCachedGroupNames != null) {
        buildListUI(ctx, sCachedFriendNames, sCachedFriendIds, sCachedGroupNames, sCachedGroupIds);
        return;
    }
    final Dialog[] loadingRef = new Dialog[1];
    try {
        if (!ctx.isFinishing() && !ctx.isDestroyed()) {
            Dialog loading = makeBaseDialog(ctx, false);

            FrameLayout mask = makeDimMask(ctx);

            LinearLayout card = new LinearLayout(ctx);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 14));
            FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(-2, -2);
            cardLp.gravity = Gravity.CENTER;
            card.setLayoutParams(cardLp);

            card.setBackground(makeDialogCardBg(ctx, 16));

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

            applyFullScreenDialogWindow(loading, 0);

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
                        if (!TextUtils.isEmpty(remark) && remark.equals(nickNorm)) {
                            remark = "";
                        }

                        String showName = nick;
                        if (!TextUtils.isEmpty(remark)) {
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

void bindTimePickerRow(final Activity ctx, final TextView tv, final String title, final String[] valueRef) {
    ((View) tv.getParent()).setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            showSingleTimePicker(ctx, title, valueRef, new Runnable() {
                public void run() { tv.setText(valueRef[0] + " >"); }
            });
        }
    });
}

void saveSelectedTargets(Set selectedIds) {
    String result = "";
    Object[] arr = selectedIds.toArray();
    for (int i = 0; i < arr.length; i++) {
        if (i > 0) result += ",";
        result += arr[i];
    }
    putString(CFG_TARGETS, result);
}

void removeTalkerConfig(String talkerId, Set selectedIds, Runnable onSaved, Dialog dialog, String msg) {
    selectedIds.remove(talkerId);
    putString(CFG_TALKER_CFG_PREFIX + talkerId, "");
    saveSelectedTargets(selectedIds);
    loadConfigToCache();
    if (onSaved != null) onSaved.run();
    clearGlobalRingtonePickState();
    dialog.dismiss();
    toast(msg);
}

Map buildBatchTalkerCfg(boolean isGroup, boolean dnd, boolean vibrate, boolean sound, boolean quickReply, boolean showDetail,
                        boolean muteEnable, String muteStart, String muteEnd, String ringtone,
                        boolean blockAll, boolean blockMe) {
    Map newCfg = new HashMap();
    newCfg.put("mode", dnd ? "0" : "1");
    newCfg.put("vibrate", vibrate ? "1" : "0");
    newCfg.put("sound", sound ? "1" : "0");
    newCfg.put("quickReply", quickReply ? "1" : "0");
    newCfg.put("ringtone", TextUtils.isEmpty(ringtone) ? "" : ringtone);
    newCfg.put("showDetail", showDetail ? "1" : "0");
    newCfg.put("muteEnable", muteEnable ? "1" : "0");
    newCfg.put("muteStart", normalizeTime(muteStart, "23:00"));
    newCfg.put("muteEnd", normalizeTime(muteEnd, "07:00"));
    newCfg.put("blockAll", isGroup && blockAll ? "1" : "0");
    newCfg.put("blockMe", isGroup && blockMe ? "1" : "0");
    newCfg.put("onlyMembers", "");
    newCfg.put("blockMembers", "");
    return newCfg;
}

int applyBatchTalkerConfig(List ids, boolean isGroup, Map cfg, Set selectedIds) {
    if (ids == null || cfg == null || selectedIds == null) return 0;
    String encoded = encodeTalkerCfg(cfg);
    int count = 0;
    for (int i = 0; i < ids.size(); i++) {
        String talkerId = String.valueOf(ids.get(i)).trim();
        if (TextUtils.isEmpty(talkerId) || "null".equalsIgnoreCase(talkerId)) continue;
        if (isGroup && !talkerId.endsWith("@chatroom")) continue;
        if (!isGroup && talkerId.endsWith("@chatroom")) continue;
        selectedIds.add(talkerId);
        putString(CFG_TALKER_CFG_PREFIX + talkerId, encoded);
        count++;
    }
    saveSelectedTargets(selectedIds);
    loadConfigToCache();
    return count;
}

// ================= 批量配置对话框 =================
void showBatchConfigDialog(final Activity ctx, final String title, final List targetIds, final boolean isGroup, final Set selectedIds, final Runnable onSaved) {
    if (targetIds == null || targetIds.isEmpty()) {
        toast("没有可批量设置的会话");
        return;
    }
    try {
        final boolean[] tmpDnd = {false};
        final boolean[] tmpVibrate = {cacheVibrate};
        final boolean[] tmpSound = {cacheSound};
        final boolean[] tmpQuickReply = {false};
        final boolean[] tmpShowDetail = {cacheShowDetail};
        final boolean[] tmpMuteEnable = {cacheMuteTimeEnable};
        final String[] tmpMuteStart = {cacheMuteTimeStart};
        final String[] tmpMuteEnd = {cacheMuteTimeEnd};
        final String[] tmpRingtone = {""};
        final boolean[] tmpBlockAll = {cacheBlockAtAll};
        final boolean[] tmpBlockMe = {cacheBlockAtMe};

        final LinearLayout body = new LinearLayout(ctx);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(ctx, 8), dp(ctx, 4), dp(ctx, 8), dp(ctx, 4));
        addDarkSwitchDiv(ctx, body, "免打扰(不弹通知)", tmpDnd[0], boolSwitchListener(tmpDnd));
        addDarkSwitchDiv(ctx, body, "震动", tmpVibrate[0], boolSwitchListener(tmpVibrate));
        addDarkSwitchDiv(ctx, body, "铃声", tmpSound[0], boolSwitchListener(tmpSound));
        addDarkSwitchDiv(ctx, body, "快捷回复", tmpQuickReply[0], boolSwitchListener(tmpQuickReply));
        final TextView tvRing = addDarkClickRow(ctx, body, "选择铃声", getRingtoneDisplayName(ctx, tmpRingtone[0]) + " >");
        ((View) tvRing.getParent()).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                globalRingtoneValueView = tvRing;
                globalRingtoneValueRef = tmpRingtone;
                showRingtonePickStyleDialog(ctx, tmpRingtone, tvRing);
            }
        });
        addDarkDivider(ctx, body);
        addDarkSwitchDiv(ctx, body, "通知显示消息详情", tmpShowDetail[0], boolSwitchListener(tmpShowDetail));
        addDarkSwitchDiv(ctx, body, "开启时段静默", tmpMuteEnable[0], boolSwitchListener(tmpMuteEnable));
        final TextView[] tvTime = new TextView[2];
        tvTime[0] = addDarkClickRow(ctx, body, "开始时间", tmpMuteStart[0] + " >");
        bindTimePickerRow(ctx, tvTime[0], "选择开始时间", tmpMuteStart);
        addDarkDivider(ctx, body);
        tvTime[1] = addDarkClickRow(ctx, body, "结束时间", tmpMuteEnd[0] + " >");
        bindTimePickerRow(ctx, tvTime[1], "选择结束时间", tmpMuteEnd);
        if (isGroup) {
            addDarkDivider(ctx, body);
            addDarkSwitchDiv(ctx, body, "屏蔽@所有人", tmpBlockAll[0], boolSwitchListener(tmpBlockAll));
            addDarkSwitchRow(ctx, body, "屏蔽@我", tmpBlockMe[0], boolSwitchListener(tmpBlockMe));
        }

        ScrollView sv = new ScrollView(ctx);
        LinearLayout.LayoutParams svLp = new LinearLayout.LayoutParams(-1, dp(ctx, isGroup ? 460 : 420));
        svLp.topMargin = dp(ctx, 12);
        sv.setLayoutParams(svLp);
        sv.addView(body);

        final Dialog d = makeBaseDialog(ctx, true);

        FrameLayout mask = makeDimMask(ctx);

        LinearLayout card = makeVerticalDialogCard(ctx, 16, 16, 16, 12);
        card.setBackground(makeDialogCardBg(ctx, 20));

        TextView tvTitle = makeDialogTitle(ctx, title + "\n将修改 " + targetIds.size() + " 个" + (isGroup ? "群聊" : "好友"));
        card.addView(tvTitle);
        card.addView(sv);

        LinearLayout actions = makeActionsRow(ctx, 12);

        TextView btnCancel = makeNeutralButton(ctx, "取消", 14, 8);

        TextView btnSave = makePrimaryButton(ctx, "批量保存", 18, 8);

        actions.addView(btnCancel);
        addActionButton(ctx, actions, btnSave, 10);
        card.addView(actions);

        mask.addView(card);
        d.setContentView(mask);
        bindMaskDismiss(mask, card, d);
        btnCancel.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { d.dismiss(); } });
        btnSave.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                final Map cfg = buildBatchTalkerCfg(isGroup, tmpDnd[0], tmpVibrate[0], tmpSound[0], tmpQuickReply[0], tmpShowDetail[0], tmpMuteEnable[0], tmpMuteStart[0], tmpMuteEnd[0], tmpRingtone[0], tmpBlockAll[0], tmpBlockMe[0]);
                d.dismiss();
                showCustomLoadingDialog(ctx, "批量保存中...");
                new Thread(new Runnable() {
                    public void run() {
                        final int count = applyBatchTalkerConfig(targetIds, isGroup, cfg, selectedIds);
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            public void run() {
                                dismissCustomLoadingDialog();
                                if (onSaved != null) onSaved.run();
                                clearGlobalRingtonePickState();
                                toast("已批量设置 " + count + " 个" + (isGroup ? "群聊" : "好友"));
                            }
                        });
                    }
                }).start();
            }
        });

        applyFullScreenDialogWindow(d, WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        d.show();
    } catch (Throwable e) {
        toast("打开批量设置失败");
    }
}

// ================= 批量选择对话框 =================
void showBatchTalkerPickerDialog(final Activity ctx, final String title, final List names, final List ids, final boolean isGroup, final Set selectedIds, final Runnable onSaved) {
    if (ids == null || ids.isEmpty()) {
        toast("没有可选择的会话");
        return;
    }
    try {
        final Dialog dlg = makeBaseDialog(ctx, true);

        FrameLayout mask = makeDimMask(ctx);

        LinearLayout card = makeVerticalDialogCard(ctx, 16, 16, 16, 12);
        card.setBackground(makeDialogCardBg(ctx, 20));

        TextView tvTitle = makeDialogTitle(ctx, title);
        card.addView(tvTitle);

        final EditText etSearch = makeDialogSearchInput(ctx, "搜索后勾选要批量修改的会话");
        card.addView(etSearch, topMarginLp(ctx, 12));

        final TextView tvCount = addDialogCountText(ctx, card);

        LinearLayout quickActions = new LinearLayout(ctx);
        quickActions.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams quickLp = topMarginLp(ctx, 8);
        final TextView btnSelectAll = makePillButton(ctx, "全选当前", "#2563EB", "#EFF6FF");
        final TextView btnInvert = makePillButton(ctx, "反选当前", "#6D28D9", "#F5F3FF");
        final TextView btnClearPicked = makePillButton(ctx, "清空", "#334155", "#EFF3F8");
        quickActions.addView(btnSelectAll);
        addActionButton(ctx, quickActions, btnInvert, 8);
        addActionButton(ctx, quickActions, btnClearPicked, 8);
        card.addView(quickActions, quickLp);

        LinearLayout listWrap = makeDialogListWrap(ctx, 360, 8);
        final ListView lv = makeDialogListView(ctx, ListView.CHOICE_MODE_MULTIPLE, false);
        listWrap.addView(lv, new LinearLayout.LayoutParams(-1, -1));
        card.addView(listWrap);

        final Set picked = new HashSet();
        final List showNames = new ArrayList();
        final List showIds = new ArrayList();
        final int displayLimit = 400;
        final int[] matchCount = {0};
        final ArrayAdapter adapter = new ArrayAdapter(ctx, android.R.layout.simple_list_item_multiple_choice, showNames);
        lv.setAdapter(adapter);
        final Runnable update = new Runnable() {
            public void run() {
                showNames.clear();
                showIds.clear();
                matchCount[0] = 0;
                String kw = etSearch.getText().toString().trim().toLowerCase();
                for (int i = 0; i < ids.size(); i++) {
                    String id = String.valueOf(ids.get(i));
                    String name = names != null && i < names.size() ? String.valueOf(names.get(i)) : id;
                    String low = (name + " " + id).toLowerCase();
                    if (TextUtils.isEmpty(kw) || low.contains(kw)) {
                        matchCount[0]++;
                        if (showNames.size() < displayLimit) {
                            showNames.add(name);
                            showIds.add(id);
                        }
                    }
                }
                lv.clearChoices();
                adapter.notifyDataSetChanged();
                for (int i = 0; i < showIds.size(); i++) lv.setItemChecked(i, picked.contains(String.valueOf(showIds.get(i))));
                String tail = matchCount[0] > showIds.size() ? ("，已显示前 " + showIds.size() + " 个，请搜索缩小范围") : "";
                tvCount.setText("已选 " + picked.size() + " / 匹配 " + matchCount[0] + " / 共 " + ids.size() + " 个" + tail);
            }
        };
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView parent, View view, int position, long id) {
                String talkerId = String.valueOf(showIds.get(position));
                if (lv.isItemChecked(position)) picked.add(talkerId);
                else picked.remove(talkerId);
                tvCount.setText("已选 " + picked.size() + " / 匹配 " + matchCount[0] + " / 共 " + ids.size() + " 个");
            }
        });
        final Handler searchHandler = new Handler(Looper.getMainLooper());
        final Runnable[] pendingSearch = new Runnable[1];
        etSearch.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            public void afterTextChanged(Editable s) {
                if (pendingSearch[0] != null) searchHandler.removeCallbacks(pendingSearch[0]);
                pendingSearch[0] = new Runnable() { public void run() { update.run(); } };
                searchHandler.postDelayed(pendingSearch[0], 120);
            }
        });
        btnSelectAll.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                for (int i = 0; i < showIds.size(); i++) picked.add(String.valueOf(showIds.get(i)));
                update.run();
            }
        });
        btnInvert.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                for (int i = 0; i < showIds.size(); i++) {
                    String one = String.valueOf(showIds.get(i));
                    if (picked.contains(one)) picked.remove(one);
                    else picked.add(one);
                }
                update.run();
            }
        });
        btnClearPicked.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                picked.clear();
                update.run();
            }
        });

        LinearLayout actions = makeActionsRow(ctx, 12);

        TextView btnCancel = makeNeutralButton(ctx, "取消", 14, 8);

        TextView btnNext = makePrimaryButton(ctx, "下一步", 18, 8);

        actions.addView(btnCancel);
        addActionButton(ctx, actions, btnNext, 10);
        card.addView(actions);

        mask.addView(card);
        dlg.setContentView(mask);
        bindMaskDismiss(mask, card, dlg);
        btnCancel.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { dlg.dismiss(); } });
        btnNext.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (picked.isEmpty()) {
                    toast("请先选择要批量修改的会话");
                    return;
                }
                List pickedIds = new ArrayList();
                pickedIds.addAll(picked);
                dlg.dismiss();
                showBatchConfigDialog(ctx, title, pickedIds, isGroup, selectedIds, onSaved);
            }
        });

        applyFullScreenResizeDialogWindow(dlg);
        dlg.show();
        update.run();
    } catch (Throwable e) {
        toast("打开批量选择失败");
    }
}

// ================= Loading 对话框 =================
void showCustomLoadingDialog(Activity ctx, String msg) {
    try {
        Dialog d = makeBaseDialog(ctx, false);

        FrameLayout mask = makeDimMask(ctx);

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 14));
        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(-2, -2);
        cardLp.gravity = Gravity.CENTER;
        card.setLayoutParams(cardLp);
        card.setBackground(makeDialogCardBg(ctx, 16));

        ProgressBar pb = new ProgressBar(ctx);
        card.addView(pb);

        TextView tv = new TextView(ctx);
        tv.setText(msg);
        tv.setTextColor(Color.parseColor("#0F172A"));
        tv.setTextSize(14f);
        LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(-2, -2);
        tvLp.leftMargin = dp(ctx, 10);
        card.addView(tv, tvLp);

        mask.addView(card);
        d.setContentView(mask);

        applyFullScreenDialogWindow(d, 0);
        d.show();
        sLoadingDialogRef = d;
    } catch (Throwable ignored) {}
}

void dismissCustomLoadingDialog() {
    try {
        if (sLoadingDialogRef != null && sLoadingDialogRef.isShowing()) {
            ((Dialog) sLoadingDialogRef).dismiss();
        }
    } catch (Throwable ignored) {}
    sLoadingDialogRef = null;
}

// ================= 标签好友选择 =================
void showLabelFriendPickerDialog(final Activity ctx, final Set selectedIds, final Runnable onSaved) {
    showCustomLoadingDialog(ctx, "加载标签中...");
    new Thread(new Runnable() {
        public void run() {
            final List labelNames = new ArrayList();
            final List labelIds = new ArrayList();
            try {
                List labels = getContactLabelList();
                if (labels != null) {
                    for (int i = 0; i < labels.size(); i++) {
                        Object label = labels.get(i);
                        String name = readStringByAccessors(label, new String[]{"getLabelName", "getName", "getLabel", "getDisplayName"}, new String[]{"labelName", "name", "label", "displayName"});
                        if (TextUtils.isEmpty(name) && label != null) name = String.valueOf(label);
                        String id = readStringByAccessors(label, new String[]{"getLabelId", "getId", "getLabelID"}, new String[]{"labelId", "id", "labelID"});
                        if (TextUtils.isEmpty(name) || "null".equalsIgnoreCase(name)) name = TextUtils.isEmpty(id) ? ("标签" + (i + 1)) : ("标签" + id);
                        labelNames.add(name);
                        labelIds.add(id);
                    }
                }
            } catch (Throwable ignored) {}
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                public void run() {
                    dismissCustomLoadingDialog();
                    if (ctx.isFinishing() || ctx.isDestroyed()) return;
                    if (labelNames.isEmpty()) {
                        toast("没有读取到好友标签");
                        return;
                    }
                    final Dialog dlg = makeBaseDialog(ctx, true);

                    FrameLayout mask = makeDimMask(ctx);

                    LinearLayout card = makeVerticalDialogCard(ctx, 16, 16, 16, 12);
                    card.setBackground(makeDialogCardBg(ctx, 20));

                    TextView tvTitle = makeDialogTitle(ctx, "选择好友标签");
                    card.addView(tvTitle);

                    TextView tvSub = makeDialogSubText(ctx, "选择一个标签后，将进入好友多选界面");
                    LinearLayout.LayoutParams subLp = topMarginLp(ctx, 6);
                    card.addView(tvSub, subLp);

                    LinearLayout listWrap = makeDialogListWrap(ctx, 420, 12);
                    final ListView lv = makeDialogListView(ctx, ListView.CHOICE_MODE_NONE, false);
                    ArrayAdapter ad = new ArrayAdapter(ctx, android.R.layout.simple_list_item_1, labelNames);
                    lv.setAdapter(ad);
                    listWrap.addView(lv, new LinearLayout.LayoutParams(-1, -1));
                    card.addView(listWrap);

                    LinearLayout actions = makeActionsRow(ctx, 12);
                    TextView btnCancel = makeNeutralButton(ctx, "取消", 14, 8);
                    actions.addView(btnCancel);
                    card.addView(actions);

                    mask.addView(card);
                    dlg.setContentView(mask);
                    bindMaskDismiss(mask, card, dlg);
                    btnCancel.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { dlg.dismiss(); } });
                    lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                        public void onItemClick(AdapterView parent, View view, int which, long id) {
                            final String labelName = String.valueOf(labelNames.get(which));
                            final String labelId = String.valueOf(labelIds.get(which));
                            try { dlg.dismiss(); } catch (Throwable ignored) {}
                            showCustomLoadingDialog(ctx, "读取标签好友中...");
                            new Thread(new Runnable() {
                                public void run() {
                                    List loadedIds = new ArrayList();
                                    try {
                                        if (!TextUtils.isEmpty(labelId)) {
                                            List byId = getContactByLabelId(labelId);
                                            if (byId != null && !byId.isEmpty()) loadedIds = byId;
                                        }
                                    } catch (Throwable ignored) {}
                                    try {
                                        if (loadedIds.isEmpty() && !TextUtils.isEmpty(labelName)) {
                                            List byName = getContactByLabelName(labelName);
                                            if (byName != null) loadedIds = byName;
                                        }
                                    } catch (Throwable ignored) {}
                                    final List ids = loadedIds;
                                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                                        public void run() {
                                            dismissCustomLoadingDialog();
                                            if (ids == null || ids.isEmpty()) {
                                                toast("该标签下没有好友");
                                                return;
                                            }
                                            List labelNames2 = new ArrayList();
                                            for (int i = 0; i < ids.size(); i++) {
                                                String wxid = String.valueOf(ids.get(i));
                                                String nm = "";
                                                try { nm = getFriendName(wxid); } catch (Throwable ignored) {}
                                                if (TextUtils.isEmpty(nm)) nm = wxid;
                                                labelNames2.add(nm);
                                            }
                                            showBatchTalkerPickerDialog(ctx, "标签好友：" + labelName, labelNames2, ids, false, selectedIds, onSaved);
                                        }
                                    });
                                }
                            }).start();
                        }
                    });

                    applyFullScreenDialogWindow(dlg, 0);
                    dlg.show();
                }
            });
        }
    }).start();
}

// ================= 软键盘工具 =================
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

// ================= 会话配置对话框 =================
void showTalkerConfigDialog(final Activity ctx, final String talkerId, final boolean isGroup, final String displayNameRaw, final Set selectedIds, final Runnable onSaved) {
    hideSoftInput(ctx);
    String displayName = displayNameRaw.replace("  ✓", "").replace("  [已配置]", "").replace("  [未配置]", "");
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
        final Dialog dialog = makeBaseDialog(ctx, true);

        FrameLayout root = makeDimMask(ctx);

        LinearLayout card = makeVerticalDialogCard(ctx, 16, 16, 16, 12);

        card.setBackground(makeDialogCardBg(ctx, 20));

        TextView title = makeDialogTitle(ctx, (isGroup ? "群聊通知配置" : "私聊通知配置") + "\n" + displayName);
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

        addDarkSwitchDiv(ctx, body, "启用此会话规则", tmpEnable[0], boolSwitchListener(tmpEnable));
        addDarkSwitchDiv(ctx, body, "免打扰(不弹通知)", tmpDnd[0], new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean c) {
                tmpDnd[0] = c;
                tmpMode[0] = c ? 0 : 1;
            }
        });
        addDarkSwitchDiv(ctx, body, "震动", tmpVibrate[0], boolSwitchListener(tmpVibrate));
        addDarkSwitchDiv(ctx, body, "铃声", tmpSound[0], boolSwitchListener(tmpSound));
        addDarkSwitchDiv(ctx, body, "快捷回复", tmpQuickReply[0], boolSwitchListener(tmpQuickReply));

        final TextView tvRing = addDarkClickRow(ctx, body, "选择铃声", getRingtoneDisplayName(ctx, tmpRingtone[0]) + " >");
        ((View) tvRing.getParent()).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                globalRingtoneValueView = tvRing;
                globalRingtoneValueRef = tmpRingtone;
                showRingtonePickStyleDialog(ctx, tmpRingtone, tvRing);
            }
        });
        addDarkDivider(ctx, body);

        addDarkSwitchDiv(ctx, body, "通知显示消息详情", tmpShowDetail[0], boolSwitchListener(tmpShowDetail));
        addDarkSwitchDiv(ctx, body, "开启时段静默", tmpMuteEnable[0], boolSwitchListener(tmpMuteEnable));
        final TextView[] tvTime = new TextView[2];
        tvTime[0] = addDarkClickRow(ctx, body, "开始时间", tmpMuteStart[0] + " >");
        bindTimePickerRow(ctx, tvTime[0], "选择开始时间", tmpMuteStart);
        addDarkDivider(ctx, body);

        tvTime[1] = addDarkClickRow(ctx, body, "结束时间", tmpMuteEnd[0] + " >");
        bindTimePickerRow(ctx, tvTime[1], "选择结束时间", tmpMuteEnd);

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
            addDarkSwitchDiv(ctx, body, "屏蔽@所有人", tmpBlockAll[0], boolSwitchListener(tmpBlockAll));
            addDarkSwitchRow(ctx, body, "屏蔽@我", tmpBlockMe[0], boolSwitchListener(tmpBlockMe));
        }

        sv.addView(body);
        card.addView(sv);

        LinearLayout actions = makeActionsRow(ctx, 14);

        TextView btnClear = makeNeutralButton(ctx, "清除", 14, 8);

        TextView btnCancel = makeNeutralButton(ctx, "取消", 14, 8);

        TextView btnSave = makePrimaryButton(ctx, "保存", 18, 8);

        actions.addView(btnClear);
        addActionButton(ctx, actions, btnCancel, 10);
        addActionButton(ctx, actions, btnSave, 10);

        card.addView(actions);
        root.addView(card);
        dialog.setContentView(root);

        bindMaskDismiss(root, card, dialog);

        btnCancel.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { clearGlobalRingtonePickState(); dialog.dismiss(); }});

        btnClear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                removeTalkerConfig(talkerId, selectedIds, onSaved, dialog, "已清除该会话配置");
            }
        });

        btnSave.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!tmpEnable[0]) {
                    removeTalkerConfig(talkerId, selectedIds, onSaved, dialog, "该会话已关闭接管");
                    return;
                }

                Map newCfg = buildBatchTalkerCfg(isGroup, tmpDnd[0], tmpVibrate[0], tmpSound[0], tmpQuickReply[0], tmpShowDetail[0], tmpMuteEnable[0], tmpMuteStart[0], tmpMuteEnd[0], tmpRingtone[0], tmpBlockAll[0], tmpBlockMe[0]);
                if (isGroup) {
                    newCfg.put("onlyMembers", joinMemberRuleSet(parseMemberRuleSet(tmpOnlyMembers[0])));
                    newCfg.put("blockMembers", joinMemberRuleSet(parseMemberRuleSet(tmpBlockMembers[0])));
                }

                selectedIds.add(talkerId);
                putString(CFG_TALKER_CFG_PREFIX + talkerId, encodeTalkerCfg(newCfg));
                saveSelectedTargets(selectedIds);
                loadConfigToCache();
                if (onSaved != null) onSaved.run();
                clearGlobalRingtonePickState();
                dialog.dismiss();
                toast("会话配置已保存");
            }
        });

        applyFullScreenDialogWindow(dialog, WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);

        dialog.show();
        card.setAlpha(0f);
        card.setTranslationY(dp(ctx, 20));
        card.animate().alpha(1f).translationY(0).setDuration(180).start();

    } catch (Throwable e) {
        toast("打开会话配置失败");
    }
}

// ================= 群成员选择对话框 =================
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

                    final Dialog dlg = makeBaseDialog(ctx, true);

                    FrameLayout mask = makeDimMask(ctx);

                    LinearLayout card = makeVerticalDialogCard(ctx, 16, 16, 16, 12);

                    card.setBackground(makeDialogCardBg(ctx, 20));

                    TextView tvTitle = makeDialogTitle(ctx, title);
                    card.addView(tvTitle);

                    final EditText etSearch = makeDialogSearchInput(ctx, "搜索成员昵称或 wxid");
                    card.addView(etSearch, topMarginLp(ctx, 12));

                    final TextView tvCount = addDialogCountText(ctx, card);

                    LinearLayout listWrap = makeDialogListWrap(ctx, 360, 8);
                    final ListView lv = makeDialogListView(ctx, ListView.CHOICE_MODE_MULTIPLE, true);
                    listWrap.addView(lv, new LinearLayout.LayoutParams(-1, -1));
                    card.addView(listWrap);

                    final int displayLimit = 400;
                    final int[] matchCount = new int[]{0};
                    final ArrayAdapter ad = new ArrayAdapter(ctx, android.R.layout.simple_list_item_multiple_choice, display);
                    lv.setAdapter(ad);

                    final Runnable update = new Runnable() {
                        public void run() {
                            display.clear();
                            ids.clear();
                            names.clear();
                            matchCount[0] = 0;
                            String kw = etSearch.getText().toString().trim().toLowerCase();
                            for (int i = 0; i < allIds.size(); i++) {
                                String id = String.valueOf(allIds.get(i));
                                String name = String.valueOf(allNames.get(i));
                                String row = String.valueOf(allDisplay.get(i));
                                String low = row.toLowerCase();
                                if (TextUtils.isEmpty(kw) || low.contains(kw)) {
                                    matchCount[0]++;
                                    if (display.size() < displayLimit) {
                                        ids.add(id);
                                        names.add(name);
                                        display.add(row);
                                    }
                                }
                            }
                            lv.clearChoices();
                            ad.notifyDataSetChanged();
                            for (int i = 0; i < ids.size(); i++) {
                                String id = String.valueOf(ids.get(i));
                                String name = String.valueOf(names.get(i));
                                boolean checked = selected.contains(id) || selected.contains(name);
                                lv.setItemChecked(i, checked);
                            }
                            String tail = matchCount[0] > display.size() ? ("，已显示前 " + display.size() + " 人，请搜索缩小范围") : "";
                            tvCount.setText("已选 " + selected.size() + " / 匹配 " + matchCount[0] + " / 共 " + allIds.size() + " 人" + tail);
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

                    attachDebouncedSearch(etSearch, new Handler(Looper.getMainLooper()), new Runnable[1], update);

                    LinearLayout actions = makeActionsRow(ctx, 12);

                    TextView btnClear = makeNeutralButton(ctx, "清空", 14, 8);

                    TextView btnCancel = makeNeutralButton(ctx, "取消", 14, 8);

                    TextView btnSave = makePrimaryButton(ctx, "确定", 18, 8);

                    actions.addView(btnClear);
                    addActionButton(ctx, actions, btnCancel, 10);
                    addActionButton(ctx, actions, btnSave, 10);
                    card.addView(actions);

                    mask.addView(card);
                    dlg.setContentView(mask);

                    bindMaskDismiss(mask, card, dlg);
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

                    applyFullScreenResizeDialogWindow(dlg);

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

// ================= 列表 UI 辅助 =================
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

void addDarkSwitchDiv(Activity ctx, LinearLayout parent, String title, boolean checked, CompoundButton.OnCheckedChangeListener listener) {
    addDarkSwitchRow(ctx, parent, title, checked, listener);
    addDarkDivider(ctx, parent);
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

void fillStringListPair(List srcNames, List srcIds, List outNames, List outNameLower, List outIds) {
    if (srcNames == null || srcIds == null || outNames == null || outNameLower == null || outIds == null) return;
    int n = Math.min(srcNames.size(), srcIds.size());
    for (int i = 0; i < n; i++) {
        String name = String.valueOf(srcNames.get(i));
        String id = String.valueOf(srcIds.get(i));
        outNames.add(name);
        outNameLower.add(name.toLowerCase());
        outIds.add(id);
    }
}

void appendDisplayItem(List names, List ids, List isGroups, String name, String talkerId, boolean isGroup, Set selectedIds) {
    if (names == null || ids == null || isGroups == null) return;
    names.add(selectedIds != null && selectedIds.contains(talkerId) ? name + "  [已配置]" : name);
    ids.add(talkerId);
    isGroups.add(isGroup ? Boolean.TRUE : Boolean.FALSE);
}

int appendPreviewItems(List srcNames, List srcIds, boolean isGroup, Set selectedIds, List outNames, List outIds, List outIsGroup, int added, int limit) {
    if (srcNames == null || srcIds == null || added >= limit) return added;
    int n = Math.min(srcNames.size(), srcIds.size());
    for (int i = 0; i < n && added < limit; i++) {
        appendDisplayItem(outNames, outIds, outIsGroup, String.valueOf(srcNames.get(i)), String.valueOf(srcIds.get(i)), isGroup, selectedIds);
        added++;
    }
    return added;
}

void fillFilteredDisplayList(List srcNames, List srcNameLower, List srcIds, boolean isGroup, String kw, Set selectedIds, List outNames, List outIds, List outIsGroup, int limit) {
    if (srcNames == null || srcNameLower == null || srcIds == null) return;
    int n = Math.min(srcNames.size(), Math.min(srcNameLower.size(), srcIds.size()));
    for (int i = 0; i < n; i++) {
        if (limit > 0 && outNames != null && outNames.size() >= limit) return;
        String nameLower = String.valueOf(srcNameLower.get(i));
        if (!TextUtils.isEmpty(kw) && !nameLower.contains(kw)) continue;
        appendDisplayItem(outNames, outIds, outIsGroup, String.valueOf(srcNames.get(i)), String.valueOf(srcIds.get(i)), isGroup, selectedIds);
    }
}

void attachDebouncedSearch(EditText etSearch, final Handler uiHandler, final Runnable[] pendingSearch, final Runnable updateList) {
    if (etSearch == null || uiHandler == null || pendingSearch == null || updateList == null) return;
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
}

void attachListItemConfigClick(final Activity ctx, ListView lv, final List displayNames, final List displayIds, final List displayIsGroup, final Set selectedIds, final Runnable updateList) {
    if (ctx == null || lv == null) return;
    lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView parent, View view, int position, long id) {
            final String talkerId = String.valueOf(displayIds.get(position));
            final boolean isGroup = ((Boolean) displayIsGroup.get(position)).booleanValue();
            final String displayName = String.valueOf(displayNames.get(position));
            showTalkerConfigDialog(ctx, talkerId, isGroup, displayName, selectedIds, updateList);
        }
    });
}

void configureListDialogWindow(AlertDialog dialog, View root, EditText etSearch) {
    if (dialog == null) return;
    dialog.show();
    Window w = dialog.getWindow();
    if (w != null) {
        w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        w.clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        w.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }
    dialog.setContentView(root);
    if (w != null) {
        DisplayMetrics dm = new DisplayMetrics();
        w.getWindowManager().getDefaultDisplay().getMetrics(dm);
        w.setLayout((int) (dm.widthPixels * 0.96f), (int) (dm.heightPixels * 0.90f));
        w.setGravity(Gravity.CENTER);
        w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        w.setDimAmount(0f);
    }
    try { if (etSearch != null) etSearch.clearFocus(); } catch (Throwable ignored) {}
}

void attachBatchEntryClicks(final Activity ctx, View rowBatchFriend, View rowBatchGroup, View rowLabelFriend,
                            final List fNameStr, final List fIdStr, final List gNameStr, final List gIdStr,
                            final Set selectedIds, final Runnable updateList) {
    final Runnable refresh = new Runnable() { public void run() { updateList.run(); } };
    if (rowBatchFriend != null) {
        rowBatchFriend.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showBatchTalkerPickerDialog(ctx, "批量修改私聊", fNameStr, fIdStr, false, selectedIds, refresh);
            }
        });
    }
    if (rowBatchGroup != null) {
        rowBatchGroup.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showBatchTalkerPickerDialog(ctx, "批量修改群聊", gNameStr, gIdStr, true, selectedIds, refresh);
            }
        });
    }
    if (rowLabelFriend != null) {
        rowLabelFriend.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showLabelFriendPickerDialog(ctx, selectedIds, refresh);
            }
        });
    }
}

void fillPreviewDisplay(int checkedId, Set selectedIds, List fNameStr, List fIdStr, List gNameStr, List gIdStr,
                        List outNames, List outIds, List outIsGroup, int previewLimit) {
    if (outNames == null || outIds == null || outIsGroup == null) return;
    outNames.clear();
    outIds.clear();
    outIsGroup.clear();
    int added = 0;
    if (checkedId == 1 || checkedId == 3) {
        added = appendPreviewItems(fNameStr, fIdStr, false, selectedIds, outNames, outIds, outIsGroup, added, previewLimit);
    }
    if ((checkedId == 2 || checkedId == 3) && added < previewLimit) {
        appendPreviewItems(gNameStr, gIdStr, true, selectedIds, outNames, outIds, outIsGroup, added, previewLimit);
    }
    sortConfiguredFirst(outNames, outIds, outIsGroup, selectedIds);
}

void fillFullDisplay(int checkedId, String kw, Set selectedIds, List fNameStr, List fNameLower, List fIdStr,
                     List gNameStr, List gNameLower, List gIdStr, List outNames, List outIds, List outIsGroup, int limit) {
    if (checkedId == 1 || checkedId == 3) {
        fillFilteredDisplayList(fNameStr, fNameLower, fIdStr, false, kw, selectedIds, outNames, outIds, outIsGroup, limit);
    }
    if (checkedId == 2 || checkedId == 3) {
        fillFilteredDisplayList(gNameStr, gNameLower, gIdStr, true, kw, selectedIds, outNames, outIds, outIsGroup, limit);
    }
    sortConfiguredFirst(outNames, outIds, outIsGroup, selectedIds);
}

Object[] createBatchActionCard(Activity ctx) {
    LinearLayout batchCard = new LinearLayout(ctx);
    batchCard.setOrientation(LinearLayout.VERTICAL);
    GradientDrawable batchBg = roundRect(Color.parseColor("#F8FAFC"), dp(ctx, 12));
    batchBg.setStroke(dp(ctx, 1), Color.parseColor("#E2E8F0"));
    batchCard.setBackground(batchBg);

    String[][] rows = new String[][]{{"批量修改私聊", "多选好友 >"}, {"批量修改群聊", "多选群聊 >"}, {"标签好友", "按标签批量 >"}};
    LinearLayout[] views = new LinearLayout[3];
    for (int i = 0; i < rows.length; i++) {
        views[i] = createRowText(ctx, rows[i][0], rows[i][1], true);
        if (i > 0) addDarkDivider(ctx, batchCard);
        batchCard.addView(views[i]);
    }
    return new Object[]{batchCard, views[0], views[1], views[2]};
}

ListView addConversationListView(Activity ctx, LinearLayout root) {
    FrameLayout listWrap = new FrameLayout(ctx);
    GradientDrawable listWrapBg = roundRect(Color.parseColor("#F5F7FB"), dp(ctx, 12));
    listWrapBg.setStroke(dp(ctx, 1), Color.parseColor("#EEF2F7"));
    listWrap.setBackground(listWrapBg);
    LinearLayout.LayoutParams wrapLp = new LinearLayout.LayoutParams(-1, 0, 1f);
    wrapLp.topMargin = dp(ctx, 12);

    ListView lv = new ListView(ctx);
    lv.setChoiceMode(ListView.CHOICE_MODE_NONE);
    lv.setDivider(new android.graphics.drawable.ColorDrawable(Color.parseColor("#E9EDF3")));
    lv.setDividerHeight(1);
    lv.setSelector(new android.graphics.drawable.ColorDrawable(Color.parseColor("#12000000")));
    lv.setPadding(dp(ctx, 8), dp(ctx, 8), dp(ctx, 8), dp(ctx, 8));
    lv.setClipToPadding(false);
    lv.setVerticalScrollBarEnabled(false);
    listWrap.addView(lv, new FrameLayout.LayoutParams(-1, -1));
    root.addView(listWrap, wrapLp);
    return lv;
}

// ================= 会话列表 UI =================
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
    String[] tabLabels = new String[]{"好友", "群聊", "全部"};
    for (int i = 0; i < tabLabels.length; i++) {
        RadioButton rb = new RadioButton(ctx);
        rb.setText(tabLabels[i]);
        rb.setId(i + 1);
        rb.setTextSize(16f);
        rg.addView(rb);
    }
    root.addView(rg, topMarginLp(ctx, 14));

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
    root.addView(etSearch, topMarginLp(ctx, 12));

    Object[] batchViews = createBatchActionCard(ctx);
    LinearLayout batchCard = (LinearLayout) batchViews[0];
    final LinearLayout rowBatchFriend = (LinearLayout) batchViews[1];
    final LinearLayout rowBatchGroup = (LinearLayout) batchViews[2];
    final LinearLayout rowLabelFriend = (LinearLayout) batchViews[3];
    LinearLayout.LayoutParams batchLp = topMarginLp(ctx, 10);
    root.addView(batchCard, batchLp);

    final ListView lv = addConversationListView(ctx, root);

    LinearLayout actions = makeActionsRow(ctx, 14);
    actions.setOrientation(LinearLayout.HORIZONTAL);
    final TextView tvCancel = makeNeutralButton(ctx, "取消", 20, 10);
    final TextView tvOk = makePrimaryButton(ctx, "完成", 24, 10);
    tvCancel.setTextSize(16f);
    tvOk.setTextSize(16f);
    actions.addView(tvCancel);
    addActionButton(ctx, actions, tvOk, 10);
    root.addView(actions);

    final String existStr = getString(CFG_TARGETS, "");
    final Set selectedIds = parseTargetSet(existStr);

    final List fNameStr = new ArrayList();
    final List fNameLower = new ArrayList();
    final List fIdStr = new ArrayList();
    fillStringListPair(fNames, fIds, fNameStr, fNameLower, fIdStr);

    final List gNameStr = new ArrayList();
    final List gNameLower = new ArrayList();
    final List gIdStr = new ArrayList();
    fillStringListPair(gNames, gIds, gNameStr, gNameLower, gIdStr);

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
    final int displayLimit = 500;

    final Runnable updateList = new Runnable() {
        public void run() {
            final String kw = etSearch.getText().toString().toLowerCase();
            final int checkedId = rg.getCheckedRadioButtonId();
            final int version = ++filterVersion[0];

            boolean isEmptyKw = TextUtils.isEmpty(kw);
            if (isEmptyKw) {
                fillPreviewDisplay(checkedId, selectedIds, fNameStr, fIdStr, gNameStr, gIdStr, currentDisplayNames, currentDisplayIds, currentDisplayIsGroup, 120);
                adapter.notifyDataSetChanged();
            }

            tvLoading.setVisibility(View.VISIBLE);
            tvLoading.setText("正在加载会话...");

            new Thread(new Runnable() {
                public void run() {
                    final List tmpNames = new ArrayList();
                    final List tmpIds = new ArrayList();
                    final List tmpIsGroup = new ArrayList();

                    fillFullDisplay(checkedId, kw, selectedIds, fNameStr, fNameLower, fIdStr, gNameStr, gNameLower, gIdStr, tmpNames, tmpIds, tmpIsGroup, displayLimit);

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
                            if (tmpNames.size() >= displayLimit) {
                                tvLoading.setText("仅显示前 " + displayLimit + " 个结果，请搜索缩小范围");
                                tvLoading.setVisibility(View.VISIBLE);
                            }
                        }
                    });
                }
            }).start();
        }
    };
    rg.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
        public void onCheckedChanged(RadioGroup group, int checkedId) { updateList.run(); }
    });

    attachDebouncedSearch(etSearch, uiHandler, pendingSearch, updateList);
    attachListItemConfigClick(ctx, lv, currentDisplayNames, currentDisplayIds, currentDisplayIsGroup, selectedIds, updateList);

    final AlertDialog listDialog = new AlertDialog.Builder(ctx).create();
    configureListDialogWindow(listDialog, root, etSearch);
    attachBatchEntryClicks(ctx, rowBatchFriend, rowBatchGroup, rowLabelFriend, fNameStr, fIdStr, gNameStr, gIdStr, selectedIds, updateList);

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
    rg.check(3);
    updateList.run();
}

void sortConfiguredFirst(List names, List ids, List isGroups, Set selectedIds) {
    if (names == null || ids == null || isGroups == null || selectedIds == null) return;
    int n = ids.size();
    if (n <= 1 || selectedIds.isEmpty()) return;
    if (names.size() != n || isGroups.size() != n) return;

    List sortedNames = new ArrayList(n);
    List sortedIds = new ArrayList(n);
    List sortedIsGroups = new ArrayList(n);
    for (int pass = 0; pass < 2; pass++) {
        boolean wantConfigured = pass == 0;
        for (int i = 0; i < n; i++) {
            boolean configured = selectedIds.contains(String.valueOf(ids.get(i)));
            if (configured != wantConfigured) continue;
            sortedNames.add(names.get(i));
            sortedIds.add(ids.get(i));
            sortedIsGroups.add(isGroups.get(i));
        }
    }

    names.clear();
    ids.clear();
    isGroups.clear();
    names.addAll(sortedNames);
    ids.addAll(sortedIds);
    isGroups.addAll(sortedIsGroups);
}