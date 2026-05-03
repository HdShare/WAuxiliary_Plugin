import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.text.*;
import android.text.style.*;
import android.view.*;
import android.widget.*;
import java.text.*;
import java.util.*;
import me.hd.wauxv.data.bean.info.FriendInfo;
import me.hd.wauxv.data.bean.info.GroupInfo;
import java.lang.reflect.Method;
import java.util.regex.Pattern;
import org.json.JSONObject;
import org.json.JSONArray;


// 全局配置
Map<String, Boolean> keywordMap = new HashMap<>(); // 关键词集合，key=关键词，value=是否全字匹配
Set<String> excludeContactSet = new HashSet<>(); // 排除联系人ID集合
Set<String> includeContactSet = new HashSet<>(); // 仅生效联系人ID集合
boolean filterMode = false; // false=排除模式, true=仅生效模式
boolean enabled = true; // 总开关
boolean notifyEnabled = true; // 通知开关
boolean toastEnabled = true; // Toast开关
boolean anyKeywordGroupNotifyEnabled = false; // 任意关键词-群聊通知
boolean anyKeywordPrivateNotifyEnabled = false; // 任意关键词-私聊通知
boolean atMeEnabled = true; // @我通知开关
boolean atAllEnabled = true; // @所有人或群公告通知开关
boolean quietHoursEnabled = false; // 免打扰模式
int quietStartHour = 22; // 免打扰开始时间
int quietEndHour = 8; // 免打扰结束时间
long lastMatchTime = 0; // 上次匹配时间
String lastMatchedKeyword = ""; // 上次匹配的关键词
String customKeywordNotifyTitle = ""; // 关键词自定义通知标题
String customKeywordNotifyContent = ""; // 关键词自定义通知内容
String customKeywordToastText = ""; // 关键词自定义Toast文字
String customAtMeNotifyTitle = ""; // @我自定义通知标题
String customAtMeNotifyContent = ""; // @我自定义通知内容
String customAtMeToastText = ""; // @我自定义Toast文字
String customAtAllNotifyTitle = ""; // @所有人自定义通知标题
String customAtAllNotifyContent = ""; // @所有人自定义通知内容
String customAtAllToastText = ""; // @所有人自定义Toast文字

// 缓存列表
private List sCachedFriendList = null;
private List sCachedGroupList = null;

// UI组件引用
private TextView keywordCountTv = null;
private TextView excludeCountTv = null;
private ListView excludeListView = null;
private ArrayAdapter<String> excludeAdapter = null;
private List<String> excludeContactList = null;
private List<String> excludeDisplayList = null;
private Button excludeClearBtn = null;

private TextView includeCountTv = null;
private ListView includeListView = null;
private ArrayAdapter<String> includeAdapter = null;
private List<String> includeContactList = null;
private List<String> includeDisplayList = null;
private Button includeClearBtn = null;

// 存储Key
final String CONFIG_KEY = "keyword_notifier_v1";
final String KEY_KEYWORDS = "keywords";
final String KEY_EXCLUDE_CONTACTS = "exclude_contacts";
final String KEY_INCLUDE_CONTACTS = "include_contacts";
final String KEY_FILTER_MODE = "filter_mode";
final String KEY_ENABLED = "enabled";
final String KEY_NOTIFY = "notify_enabled";
final String KEY_TOAST = "toast_enabled";
final String KEY_ANY_KEYWORD_GROUP_NOTIFY = "any_keyword_group_notify_enabled";
final String KEY_ANY_KEYWORD_PRIVATE_NOTIFY = "any_keyword_private_notify_enabled";
final String KEY_AT_ME = "at_me_enabled";
final String KEY_AT_ALL = "at_all_enabled";
final String KEY_QUIET = "quiet_hours_enabled";
final String KEY_QUIET_START = "quiet_start";
final String KEY_QUIET_END = "quiet_end";
final String KEY_LAST_TIME = "last_match_time";
final String KEY_LAST_KEYWORD = "last_keyword";
final String KEY_CUSTOM_KEYWORD_NOTIFY_TITLE = "custom_keyword_notify_title";
final String KEY_CUSTOM_KEYWORD_NOTIFY_CONTENT = "custom_keyword_notify_content";
final String KEY_CUSTOM_KEYWORD_TOAST = "custom_keyword_toast";
final String KEY_CUSTOM_AT_ME_NOTIFY_TITLE = "custom_at_me_notify_title";
final String KEY_CUSTOM_AT_ME_NOTIFY_CONTENT = "custom_at_me_notify_content";
final String KEY_CUSTOM_AT_ME_TOAST = "custom_at_me_toast";
final String KEY_CUSTOM_AT_ALL_NOTIFY_TITLE = "custom_at_all_notify_title";
final String KEY_CUSTOM_AT_ALL_NOTIFY_CONTENT = "custom_at_all_notify_content";
final String KEY_CUSTOM_AT_ALL_TOAST = "custom_at_all_toast";

// ==========================================
// ========== ♻️ 生命周期与核心逻辑 ==========
// ==========================================

/**
 * 插件加载时调用
 * 恢复配置
 */
public void onLoad() {
    // 延时一点执行，确保环境就绪
    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
        public void run() {
            loadConfig();
        }
    }, 2000);
}

/**
 * 加载配置
 */
private void loadConfig() {
    try {
        // 加载关键词（新版格式：JSON对象，key=关键词，value=是否全字匹配）
        String keywordsJson = getString(CONFIG_KEY, KEY_KEYWORDS, "{}");
        log("加载的 keywordsJson: " + keywordsJson);  // 添加调试日志，查看实际字符串

        if (keywordsJson != null && !keywordsJson.isEmpty()) {
            JSONObject keywordsObj = null;
            try {
                keywordsObj = new JSONObject(keywordsJson);
            } catch (Exception e) {
                log("parseObject 失败: " + e.getMessage() + "，尝试兼容旧版数组格式");
            }

            if (keywordsObj != null) {
                keywordMap.clear();
                java.util.Iterator<String> keys = keywordsObj.keys();
                while (keys.hasNext()) {
                    String keyword = keys.next();
                    boolean isWholeWord = keywordsObj.optBoolean(keyword, false);
                    keywordMap.put(keyword, isWholeWord);
                }
            } else {
                // 兼容旧版格式（纯数组）
                try {
                    JSONArray keywordsArray = new JSONArray(keywordsJson);
                    keywordMap.clear();
                    for (int i = 0; i < keywordsArray.length(); i++) {
                        String kw = keywordsArray.optString(i, "");
                        if (!TextUtils.isEmpty(kw)) keywordMap.put(kw, false);
                    }
                } catch (Exception e) {
                    log("parseArray 失败: " + e.getMessage() + "，使用默认空配置");
                    keywordMap.clear();
                }
            }
        }

        // 加载其他配置
        enabled = getLong(CONFIG_KEY, KEY_ENABLED, 1) == 1;
        notifyEnabled = getLong(CONFIG_KEY, KEY_NOTIFY, 1) == 1;
        toastEnabled = getLong(CONFIG_KEY, KEY_TOAST, 1) == 1;
        anyKeywordGroupNotifyEnabled = getLong(CONFIG_KEY, KEY_ANY_KEYWORD_GROUP_NOTIFY, 0) == 1;
        anyKeywordPrivateNotifyEnabled = getLong(CONFIG_KEY, KEY_ANY_KEYWORD_PRIVATE_NOTIFY, 0) == 1;
        atMeEnabled = getLong(CONFIG_KEY, KEY_AT_ME, 1) == 1;
        atAllEnabled = getLong(CONFIG_KEY, KEY_AT_ALL, 1) == 1;
        quietHoursEnabled = getLong(CONFIG_KEY, KEY_QUIET, 0) == 1;
        filterMode = getLong(CONFIG_KEY, KEY_FILTER_MODE, 0) == 1;
        quietStartHour = (int) getLong(CONFIG_KEY, KEY_QUIET_START, 22);
        quietEndHour = (int) getLong(CONFIG_KEY, KEY_QUIET_END, 8);
        lastMatchTime = getLong(CONFIG_KEY, KEY_LAST_TIME, 0);
        lastMatchedKeyword = getString(CONFIG_KEY, KEY_LAST_KEYWORD, "");

        // 加载自定义文字配置
        customKeywordNotifyTitle = getString(CONFIG_KEY, KEY_CUSTOM_KEYWORD_NOTIFY_TITLE, "");
        customKeywordNotifyContent = getString(CONFIG_KEY, KEY_CUSTOM_KEYWORD_NOTIFY_CONTENT, "");
        customKeywordToastText = getString(CONFIG_KEY, KEY_CUSTOM_KEYWORD_TOAST, "");
        customAtMeNotifyTitle = getString(CONFIG_KEY, KEY_CUSTOM_AT_ME_NOTIFY_TITLE, "");
        customAtMeNotifyContent = getString(CONFIG_KEY, KEY_CUSTOM_AT_ME_NOTIFY_CONTENT, "");
        customAtMeToastText = getString(CONFIG_KEY, KEY_CUSTOM_AT_ME_TOAST, "");
        customAtAllNotifyTitle = getString(CONFIG_KEY, KEY_CUSTOM_AT_ALL_NOTIFY_TITLE, "");
        customAtAllNotifyContent = getString(CONFIG_KEY, KEY_CUSTOM_AT_ALL_NOTIFY_CONTENT, "");
        customAtAllToastText = getString(CONFIG_KEY, KEY_CUSTOM_AT_ALL_TOAST, "");

        // 加载排除联系人列表
        String excludesJson = getString(CONFIG_KEY, KEY_EXCLUDE_CONTACTS, "[]");
        if (excludesJson != null && !excludesJson.isEmpty()) {
            try {
                JSONArray excludesArray = new JSONArray(excludesJson);
                excludeContactSet.clear();
                for (int i = 0; i < excludesArray.length(); i++) {
                    String id = excludesArray.optString(i, "");
                    if (!TextUtils.isEmpty(id)) excludeContactSet.add(id);
                }
            } catch (Exception e) {
                log("解析排除联系人配置出错，使用默认配置: " + e.getMessage());
            }
        }

        
        // 加载仅生效联系人列表
        String includesJson = getString(CONFIG_KEY, KEY_INCLUDE_CONTACTS, "[]");
        if (includesJson != null && !includesJson.isEmpty()) {
            try {
                JSONArray includesArray = new JSONArray(includesJson);
                includeContactSet.clear();
                for (int i = 0; i < includesArray.length(); i++) {
                    String id = includesArray.optString(i, "");
                    if (!TextUtils.isEmpty(id)) includeContactSet.add(id);
                }
            } catch (Exception e) {
                log("解析仅生效联系人配置出错，使用默认配置: " + e.getMessage());
            }
        }

        log("关键词通知已加载，关键词: " + keywordMap.size() + "，排除联系人: " + excludeContactSet.size() + "，仅生效联系人: " + includeContactSet.size());
    } catch (Exception e) {
        log("加载配置失败: " + e.getMessage());
    }
}

/**
 * 保存配置
 */
private void saveConfig() {
    try {
        putLong(CONFIG_KEY, KEY_ENABLED, enabled ? 1 : 0);
        putLong(CONFIG_KEY, KEY_NOTIFY, notifyEnabled ? 1 : 0);
        putLong(CONFIG_KEY, KEY_TOAST, toastEnabled ? 1 : 0);
        putLong(CONFIG_KEY, KEY_ANY_KEYWORD_GROUP_NOTIFY, anyKeywordGroupNotifyEnabled ? 1 : 0);
        putLong(CONFIG_KEY, KEY_ANY_KEYWORD_PRIVATE_NOTIFY, anyKeywordPrivateNotifyEnabled ? 1 : 0);
        putLong(CONFIG_KEY, KEY_AT_ME, atMeEnabled ? 1 : 0);
        putLong(CONFIG_KEY, KEY_AT_ALL, atAllEnabled ? 1 : 0);
        putLong(CONFIG_KEY, KEY_QUIET, quietHoursEnabled ? 1 : 0);
        putLong(CONFIG_KEY, KEY_FILTER_MODE, filterMode ? 1 : 0);
        putLong(CONFIG_KEY, KEY_QUIET_START, quietStartHour);
        putLong(CONFIG_KEY, KEY_QUIET_END, quietEndHour);
        putLong(CONFIG_KEY, KEY_LAST_TIME, lastMatchTime);
        putString(CONFIG_KEY, KEY_LAST_KEYWORD, lastMatchedKeyword);

        // 保存关键词（新版格式：JSON对象，key=关键词，value=是否全字匹配）
        JSONObject keywordsObj = new JSONObject();
        for (String keyword : keywordMap.keySet()) {
            keywordsObj.put(keyword, keywordMap.get(keyword));
        }
        putString(CONFIG_KEY, KEY_KEYWORDS, keywordsObj.toString());

        // 保存自定义文字配置
        putString(CONFIG_KEY, KEY_CUSTOM_KEYWORD_NOTIFY_TITLE, customKeywordNotifyTitle);
        putString(CONFIG_KEY, KEY_CUSTOM_KEYWORD_NOTIFY_CONTENT, customKeywordNotifyContent);
        putString(CONFIG_KEY, KEY_CUSTOM_KEYWORD_TOAST, customKeywordToastText);
        putString(CONFIG_KEY, KEY_CUSTOM_AT_ME_NOTIFY_TITLE, customAtMeNotifyTitle);
        putString(CONFIG_KEY, KEY_CUSTOM_AT_ME_NOTIFY_CONTENT, customAtMeNotifyContent);
        putString(CONFIG_KEY, KEY_CUSTOM_AT_ME_TOAST, customAtMeToastText);
        putString(CONFIG_KEY, KEY_CUSTOM_AT_ALL_NOTIFY_TITLE, customAtAllNotifyTitle);
        putString(CONFIG_KEY, KEY_CUSTOM_AT_ALL_NOTIFY_CONTENT, customAtAllNotifyContent);
        putString(CONFIG_KEY, KEY_CUSTOM_AT_ALL_TOAST, customAtAllToastText);

        // 保存排除联系人列表
        JSONArray excludesArray = new JSONArray();
        for (String contactId : excludeContactSet) {
            excludesArray.put(contactId);
        }
        putString(CONFIG_KEY, KEY_EXCLUDE_CONTACTS, excludesArray.toString());
        // 保存仅生效联系人列表
        JSONArray includesArray = new JSONArray();
        for (String contactId : includeContactSet) {
            includesArray.put(contactId);
        }
        putString(CONFIG_KEY, KEY_INCLUDE_CONTACTS, includesArray.toString());
    } catch (Exception e) {
        log("保存配置失败: " + e.getMessage());
    }
}

/**
 * 监听收到消息
 */
public void onHandleMsg(Object msgInfoBean) {
    if (!enabled) return;

    try {
        // 兼容不同运行环境的消息字段命名
        String content = getFirstNonEmptyMsgText(msgInfoBean, new String[]{
            "getOriginContent", "originContent",
            "getMsgContent", "msgContent",
            "getContent", "content",
            "getText", "text"
        });
        if (TextUtils.isEmpty(content)) return;

        String rawContent = content;
        int msgType = getMsgIntCompat(msgInfoBean, new String[]{"getType", "type", "getMsgType", "msgType"}, -1);

        boolean isTextMsg = invokeMsgBoolean(msgInfoBean, "isText");
        boolean isQuoteMsg = invokeMsgBoolean(msgInfoBean, "isQuote");
        boolean isEmojiMsg = invokeMsgBoolean(msgInfoBean, "isEmoji");
        boolean isImageMsg = invokeMsgBoolean(msgInfoBean, "isImage");

        if (TextUtils.isEmpty(content)) return;
        // 图片消息走轻量路径，避免在部分机型上解析XML引发异常
        if (isImageMsg || msgType == 3 || content.contains("<img")) {
            content = "[图片]";
        } else {
            content = normalizeMessageContent(msgInfoBean, content);
            content = stripGroupSenderPrefix(content);
        }
        if (TextUtils.isEmpty(content)) return;

        // 检查是否是群聊（兼容字段）
        boolean isGroupChat = getMsgBooleanCompat(msgInfoBean, new String[]{"isGroupChat", "groupChat", "isGroup", "isChatroom", "isChatRoom"}, false);

        // 检查是否是自己发的消息（兼容字段）
        boolean isSend = getMsgBooleanCompat(msgInfoBean, new String[]{"isSend", "send", "isSelfSend"}, false);

        if (isSend) return; // 忽略自己发的消息

        // 获取会话ID（群聊一般为 xxx@chatroom）
        String talkerWxid = getFirstNonEmptyMsgText(msgInfoBean, new String[]{
            "getTalker", "talker",
            "getSessionId", "sessionId",
            "getChatId", "chatId"
        });
        // 获取真实发送者ID（群聊成员/私聊对方）
        String realSenderWxid = getFirstNonEmptyMsgText(msgInfoBean, new String[]{
            "getSendTalker", "sendTalker",
            "getSenderWxid", "senderWxid",
            "getSender", "sender",
            "getFromUser", "fromUser"
        });
        String senderWxid = !TextUtils.isEmpty(realSenderWxid) ? realSenderWxid : talkerWxid;
        if (!isGroupChat && !TextUtils.isEmpty(talkerWxid) && talkerWxid.endsWith("@chatroom")) {
            isGroupChat = true;
        }
        if (TextUtils.isEmpty(talkerWxid)) {
            talkerWxid = senderWxid;
        }
        if (isGroupChat && !TextUtils.isEmpty(talkerWxid) && !talkerWxid.endsWith("@chatroom")) {
            talkerWxid = getFirstNonEmptyMsgText(msgInfoBean, new String[]{
                "getTalker", "talker",
                "getSessionId", "sessionId",
                "getChatId", "chatId"
            });
        }
        if (isGroupChat && !TextUtils.isEmpty(realSenderWxid) && realSenderWxid.endsWith("@chatroom")) {
            realSenderWxid = "";
        }
        if (isGroupChat && TextUtils.isEmpty(realSenderWxid)) {
            String parsedSender = extractGroupSenderWxidFromRawContent(rawContent);
            if (!TextUtils.isEmpty(parsedSender) && !parsedSender.endsWith("@chatroom")) {
                realSenderWxid = parsedSender;
            }
        }
        if (isGroupChat && !TextUtils.isEmpty(realSenderWxid)) {
            senderWxid = realSenderWxid;
        } else if (!TextUtils.isEmpty(talkerWxid)) {
            senderWxid = talkerWxid;
        }

        // 根据过滤模式判断是否处理该联系人的消息
        if (!TextUtils.isEmpty(talkerWxid)) {
            if (filterMode) {
                // 仅生效模式：只处理白名单中的联系人
                if (!includeContactSet.contains(talkerWxid)) {
                    return;
                }
            } else {
                // 排除模式：排除黑名单中的联系人
                if (excludeContactSet.contains(talkerWxid)) {
                    return;
                }
            }
        }

        String matchedKeyword = null;
        // 任意关键词通知：仅对纯文字消息或引用回复(type=57)生效
        boolean anyKeywordSceneEnabled = (isGroupChat && anyKeywordGroupNotifyEnabled) || (!isGroupChat && anyKeywordPrivateNotifyEnabled);
        if (anyKeywordSceneEnabled) {
            boolean anyKeywordEligible = isEligibleForAnyKeywordNotify(msgInfoBean, msgType, rawContent, isTextMsg, isQuoteMsg, isEmojiMsg);
            if (anyKeywordEligible) {
                matchedKeyword = "任意关键词";
            }
        } else if (!keywordMap.isEmpty()) {
            for (String keyword : keywordMap.keySet()) {
                Boolean isWholeWord = keywordMap.get(keyword);
                boolean matched = false;

                if (isWholeWord != null && isWholeWord) {
                    // 全字匹配：使用正则表达式匹配完整单词
                    try {
                        String pattern = "\\b" + Pattern.quote(keyword) + "\\b";
                        matched = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(content).find();
                    } catch (Exception e) {
                        // 正则失败时降级为模糊匹配
                        matched = content.contains(keyword);
                    }
                } else {
                    // 模糊匹配：关键词包含在消息中即可
                    matched = content.contains(keyword);
                }

                if (matched) {
                    matchedKeyword = keyword;
                    break;
                }
            }
        }

        if (matchedKeyword != null) {
            // 获取发送者信息
            String senderInfo = getSenderInfo(msgInfoBean, isGroupChat);
            String wxidDisplay = buildWxidDisplay(msgInfoBean, isGroupChat, senderWxid);

            // 触发通知
            triggerNotification(matchedKeyword, content, senderInfo, wxidDisplay, talkerWxid, isGroupChat);

            // 更新最后匹配时间
            lastMatchTime = System.currentTimeMillis();
            lastMatchedKeyword = matchedKeyword;
            saveConfig();
        }

        // 被@通知检查（仅群聊）
        if (isGroupChat) {
            boolean atMe = false;
            try {
                Method isAtMeMethod = msgInfoBean.getClass().getMethod("isAtMe");
                atMe = (Boolean) isAtMeMethod.invoke(msgInfoBean);
            } catch (Exception e) {}

            boolean notifyAll = false;
            try {
                Method isNotifyAllMethod = msgInfoBean.getClass().getMethod("isNotifyAll");
                notifyAll = (Boolean) isNotifyAllMethod.invoke(msgInfoBean);
            } catch (Exception e) {}

            boolean announceAll = false;
            try {
                Method isAnnounceAllMethod = msgInfoBean.getClass().getMethod("isAnnounceAll");
                announceAll = (Boolean) isAnnounceAllMethod.invoke(msgInfoBean);
            } catch (Exception e) {}

            String atType = null;
            if (atMeEnabled && atMe) {
                atType = "@我";
            } else if (atAllEnabled && (notifyAll || announceAll)) {
                atType = "@所有人";
            }

            if (atType != null) {
                // 获取发送者信息
                String senderInfo = getSenderInfo(msgInfoBean, isGroupChat);
                String wxidDisplay = buildWxidDisplay(msgInfoBean, isGroupChat, senderWxid);

                // 触发通知
                triggerNotification(atType, content, senderInfo, wxidDisplay, talkerWxid, isGroupChat);

                // 更新最后匹配时间
                lastMatchTime = System.currentTimeMillis();
                lastMatchedKeyword = atType;
                saveConfig();
            }
        }
    } catch (Throwable e) {
        log("处理消息失败: " + e.toString());
    }
}

private String getFirstNonEmptyMsgText(Object msgInfoBean, String[] names) {
    if (msgInfoBean == null || names == null) return "";
    for (String name : names) {
        try {
            Object v = invokeMsgAny(msgInfoBean, name);
            if (v == null) continue;
            String s = String.valueOf(v).trim();
            if (!TextUtils.isEmpty(s) && !"null".equalsIgnoreCase(s)) return s;
        } catch (Throwable ignored) {}
    }
    return "";
}

private boolean getMsgBooleanCompat(Object msgInfoBean, String[] names, boolean def) {
    if (msgInfoBean == null || names == null) return def;
    for (String name : names) {
        try {
            Object v = invokeMsgAny(msgInfoBean, name);
            if (v == null) continue;
            if (v instanceof Boolean) return (Boolean) v;
            return Boolean.parseBoolean(String.valueOf(v));
        } catch (Throwable ignored) {}
    }
    return def;
}

private int getMsgIntCompat(Object msgInfoBean, String[] names, int def) {
    if (msgInfoBean == null || names == null) return def;
    for (String name : names) {
        try {
            Object v = invokeMsgAny(msgInfoBean, name);
            if (v == null) continue;
            if (v instanceof Number) return ((Number) v).intValue();
            return Integer.parseInt(String.valueOf(v));
        } catch (Throwable ignored) {}
    }
    return def;
}

private Object invokeMsgAny(Object msgInfoBean, String name) {
    if (msgInfoBean == null || TextUtils.isEmpty(name)) return null;
    Class cls = msgInfoBean.getClass();
    try {
        Method m = cls.getMethod(name);
        return m.invoke(msgInfoBean);
    } catch (Throwable ignored) {}
    try {
        java.lang.reflect.Field f = cls.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(msgInfoBean);
    } catch (Throwable ignored) {}
    return null;
}

/**
 * 判断是否可触发任意关键词通知
 * 仅允许：纯文字消息 或 引用回复(type=57)
 */
private boolean isEligibleForAnyKeywordNotify(Object msgInfoBean, int msgType, String rawContent, boolean isTextMsg, boolean isQuoteMsg, boolean isEmojiMsg) {
    if (msgType == 47) return false;
    if (isEmojiMsg) return false;

    boolean isNonTextByApi = invokeMsgBoolean(msgInfoBean, "isImage")
        || invokeMsgBoolean(msgInfoBean, "isVoice")
        || invokeMsgBoolean(msgInfoBean, "isVideo")
        || invokeMsgBoolean(msgInfoBean, "isApp")
        || invokeMsgBoolean(msgInfoBean, "isFile")
        || invokeMsgBoolean(msgInfoBean, "isLink")
        || invokeMsgBoolean(msgInfoBean, "isLocation")
        || invokeMsgBoolean(msgInfoBean, "isShareCard")
        || invokeMsgBoolean(msgInfoBean, "isPat")
        || invokeMsgBoolean(msgInfoBean, "isSystem")
        || invokeMsgBoolean(msgInfoBean, "isVoip")
        || invokeMsgBoolean(msgInfoBean, "isVoipVoice")
        || invokeMsgBoolean(msgInfoBean, "isVoipVideo");
    if (isNonTextByApi && !isQuoteMsg) return false;

    if (isQuoteMsg) return true;
    if (isTextMsg && isLikelyPlainText(rawContent)) return true;
    if (isReferenceReplyMessage(msgType, rawContent)) return true;
    if (msgType == 1 && isLikelyPlainText(rawContent)) return true;
    return false;
}

private boolean isLikelyPlainText(String rawContent) {
    if (TextUtils.isEmpty(rawContent)) return false;
    String c = rawContent.trim();
    if (TextUtils.isEmpty(c)) return false;
    if (c.startsWith("<?xml") || c.contains("<msg") || c.contains("<appmsg") || c.startsWith("<")) return false;
    if ("[动画表情]".equals(c) || "[表情]".equals(c) || "[图片]".equals(c) || "[语音]".equals(c)
        || "[视频]".equals(c) || "[文件]".equals(c) || "[链接]".equals(c)) return false;
    if (c.matches("^(\\[[^\\[\\]\\s]{1,20}\\])+$")) return false;
    return true;
}

private boolean isReferenceReplyMessage(int msgType, String rawContent) {
    if (TextUtils.isEmpty(rawContent)) return false;
    String c = rawContent.trim();
    if (TextUtils.isEmpty(c)) return false;
    if (!(c.startsWith("<?xml") || c.contains("<msg") || c.contains("<appmsg"))) return false;
    String appType = getAppMsgType(c);
    return "57".equals(appType);
}

private String getAppMsgType(String xml) {
    if (TextUtils.isEmpty(xml)) return "";
    String appMsgBlock = extractRegexGroup(xml, "(?is)<appmsg\\b[^>]*>(.*?)</appmsg>");
    if (!TextUtils.isEmpty(appMsgBlock)) {
        String appType = sanitizeXmlText(extractXmlTagValue(appMsgBlock, "type"));
        if (!TextUtils.isEmpty(appType)) return appType;
    }
    return sanitizeXmlText(extractXmlTagValue(xml, "type"));
}

private String normalizeMessageContent(Object msgInfoBean, String rawContent) {
    if (TextUtils.isEmpty(rawContent)) return rawContent;

    try {
        if (invokeMsgBoolean(msgInfoBean, "isQuote")) {
            Method getQuoteMsgMethod = msgInfoBean.getClass().getMethod("getQuoteMsg");
            Object quoteMsg = getQuoteMsgMethod.invoke(msgInfoBean);
            if (quoteMsg != null) {
                String title = "";
                String quoteContent = "";
                try {
                    Method getTitleMethod = quoteMsg.getClass().getMethod("getTitle");
                    Object titleObj = getTitleMethod.invoke(quoteMsg);
                    title = titleObj == null ? "" : String.valueOf(titleObj);
                } catch (Exception e) {}
                try {
                    Method getContentMethod = quoteMsg.getClass().getMethod("getContent");
                    Object contentObj = getContentMethod.invoke(quoteMsg);
                    quoteContent = contentObj == null ? "" : String.valueOf(contentObj);
                } catch (Exception e) {}

                String titleText = sanitizeXmlText(title);
                String referText = sanitizeXmlText(quoteContent);
                if (!TextUtils.isEmpty(titleText) && !TextUtils.isEmpty(referText)) return titleText + " | 引用: " + referText;
                if (!TextUtils.isEmpty(titleText)) return titleText;
                if (!TextUtils.isEmpty(referText)) return referText;
            }
        }
    } catch (Exception e) {}

    String content = rawContent.trim();
    if (TextUtils.isEmpty(content)) return content;
    if (!(content.startsWith("<?xml") || content.contains("<msg") || content.contains("<appmsg"))) return content;

    try {
        String appType = getAppMsgType(content);
        if ("57".equals(appType)) {
            String titleText = sanitizeXmlText(extractXmlTagValue(content, "title"));
            String referBlock = extractRegexGroup(content, "(?is)<refermsg>(.*?)</refermsg>");
            String referText = sanitizeXmlText(extractXmlTagValue(referBlock, "content"));
            if (!TextUtils.isEmpty(titleText) && !TextUtils.isEmpty(referText)) return titleText + " | 引用: " + referText;
            if (!TextUtils.isEmpty(titleText)) return titleText;
            if (!TextUtils.isEmpty(referText)) return referText;
        }

        String titleText = sanitizeXmlText(extractXmlTagValue(content, "title"));
        if (!TextUtils.isEmpty(titleText) && !"null".equalsIgnoreCase(titleText)) return titleText;
        String contentText = sanitizeXmlText(extractXmlTagValue(content, "content"));
        if (!TextUtils.isEmpty(contentText) && !"null".equalsIgnoreCase(contentText)) return contentText;
    } catch (Exception e) {
        log("归一化消息内容失败: " + e.getMessage());
    }
    return content;
}

private boolean invokeMsgBoolean(Object msgInfoBean, String methodName) {
    if (msgInfoBean == null || TextUtils.isEmpty(methodName)) return false;
    try {
        Method method = msgInfoBean.getClass().getMethod(methodName);
        Object val = method.invoke(msgInfoBean);
        if (val instanceof Boolean) return (Boolean) val;
        if (val != null) return Boolean.parseBoolean(String.valueOf(val));
    } catch (Throwable e) {}
    return false;
}

private String extractXmlTagValue(String xml, String tagName) {
    if (TextUtils.isEmpty(xml) || TextUtils.isEmpty(tagName)) return "";
    String regex = "(?is)<" + Pattern.quote(tagName) + ">(?:<!\\[CDATA\\[(.*?)\\]\\]>|(.*?))</" + Pattern.quote(tagName) + ">";
    String cdata = extractRegexGroup(xml, regex, 1);
    if (!TextUtils.isEmpty(cdata)) return cdata;
    String plain = extractRegexGroup(xml, regex, 2);
    return plain == null ? "" : plain;
}

private String extractRegexGroup(String text, String regex) {
    return extractRegexGroup(text, regex, 1);
}

private String extractRegexGroup(String text, String regex, int group) {
    if (TextUtils.isEmpty(text) || TextUtils.isEmpty(regex)) return "";
    try {
        java.util.regex.Matcher matcher = Pattern.compile(regex, Pattern.DOTALL | Pattern.CASE_INSENSITIVE).matcher(text);
        if (matcher.find() && matcher.groupCount() >= group) {
            String val = matcher.group(group);
            return val == null ? "" : val;
        }
    } catch (Throwable e) {}
    return "";
}

private String sanitizeXmlText(String text) {
    if (TextUtils.isEmpty(text)) return "";
    String v = text
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&#10;", "\n")
        .replace("&#13;", "\r")
        .trim();
    v = v.replaceAll("(?is)<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    return v;
}

private String stripGroupSenderPrefix(String content) {
    if (TextUtils.isEmpty(content)) return "";
    try {
        String c = content.trim();
        c = c.replaceFirst("^[A-Za-z0-9_\\-]+:\\n", "");
        return c.trim();
    } catch (Throwable ignored) {}
    return content;
}

/**
 * 获取发送者信息
 */
private String getSenderInfo(Object msgInfoBean, boolean isGroupChat) {
    try {
        String talker = getFirstNonEmptyMsgText(msgInfoBean, new String[]{
            "getTalker", "talker", "getSessionId", "sessionId", "getChatId", "chatId"
        });
        String sendTalker = getFirstNonEmptyMsgText(msgInfoBean, new String[]{
            "getSendTalker", "sendTalker", "getSenderWxid", "senderWxid", "getSender", "sender", "getFromUser", "fromUser"
        });
        String displayName = getFirstNonEmptyMsgText(msgInfoBean, new String[]{"getDisplayName", "displayName"});

        if (isGroupChat || (!TextUtils.isEmpty(talker) && talker.endsWith("@chatroom"))) {
            if (TextUtils.isEmpty(talker) || !talker.endsWith("@chatroom")) {
                String talkerCompat = getFirstNonEmptyMsgText(msgInfoBean, new String[]{
                    "getContentTalker", "contentTalker",
                    "getChatUser", "chatUser",
                    "getRoomId", "roomId"
                });
                if (!TextUtils.isEmpty(talkerCompat) && talkerCompat.endsWith("@chatroom")) {
                    talker = talkerCompat;
                }
            }
            if (TextUtils.isEmpty(talker) || !talker.endsWith("@chatroom")) {
                String rawContentForTalker = getFirstNonEmptyMsgText(msgInfoBean, new String[]{
                    "getOriginContent", "originContent",
                    "getMsgContent", "msgContent",
                    "getContent", "content",
                    "getText", "text"
                });
                String parsedTalker = extractChatroomId(rawContentForTalker);
                if (!TextUtils.isEmpty(parsedTalker)) talker = parsedTalker;
            }

            String groupName = getGroupName(talker);
            if (TextUtils.isEmpty(groupName) || "未知群聊".equals(groupName)) {
                groupName = (!TextUtils.isEmpty(talker) && talker.endsWith("@chatroom")) ? talker : "群聊";
            }

            String rawContentForSender = getFirstNonEmptyMsgText(msgInfoBean, new String[]{
                "getOriginContent", "originContent",
                "getMsgContent", "msgContent",
                "getContent", "content",
                "getText", "text"
            });
            String parsedSender = extractGroupSenderWxidFromRawContent(rawContentForSender);
            if (!TextUtils.isEmpty(parsedSender) && !parsedSender.endsWith("@chatroom")) {
                sendTalker = parsedSender;
            }

            String memberName = "";
            if (!TextUtils.isEmpty(sendTalker) && !sendTalker.endsWith("@chatroom")) {
                memberName = getGroupMemberDisplayName(talker, sendTalker);
                if (TextUtils.isEmpty(memberName)) memberName = getFriendDisplayName(sendTalker);
            }
            if (TextUtils.isEmpty(memberName)) memberName = displayName;
            if (TextUtils.isEmpty(memberName) || "未知好友".equals(memberName)) memberName = sendTalker;
            if (TextUtils.isEmpty(memberName)) memberName = "未知成员";
            return groupName + " | " + memberName;
        }

        if (!TextUtils.isEmpty(displayName)) return displayName;
        String privateName = getFriendDisplayName(talker);
        if (!TextUtils.isEmpty(privateName) && !privateName.equals(talker)) return privateName;
        if (!TextUtils.isEmpty(sendTalker) && !sendTalker.endsWith("@chatroom")) {
            String senderName = getFriendDisplayName(sendTalker);
            if (!TextUtils.isEmpty(senderName)) return senderName;
            return sendTalker;
        }
        return !TextUtils.isEmpty(talker) ? talker : "未知来源";
    } catch (Exception e) {
        log("获取发送者信息失败: " + e.getMessage());
        return "未知来源";
    }
}

private String extractGroupSenderWxidFromRawContent(String rawContent) {
    if (TextUtils.isEmpty(rawContent)) return "";
    try {
        String c = rawContent.trim();
        if (TextUtils.isEmpty(c)) return "";
        java.util.regex.Matcher m = Pattern.compile("^([A-Za-z0-9_\\-]+?):\\n").matcher(c);
        if (m.find()) {
            String sender = m.group(1);
            return TextUtils.isEmpty(sender) ? "" : sender.trim();
        }
    } catch (Throwable ignored) {}
    return "";
}

private String extractChatroomId(String rawContent) {
    if (TextUtils.isEmpty(rawContent)) return "";
    try {
        java.util.regex.Matcher m = Pattern.compile("([A-Za-z0-9_\\-]+@chatroom)").matcher(rawContent);
        if (m.find()) {
            String room = m.group(1);
            return TextUtils.isEmpty(room) ? "" : room.trim();
        }
    } catch (Throwable ignored) {}
    return "";
}

/**
 * 获取群成员显示名称
 */
private String getGroupMemberDisplayName(String groupWxid, String memberWxid) {
    try {
        // 先尝试从群成员列表获取
        List memberList = getGroupMemberList(groupWxid);
        if (memberList != null) {
            for (Object obj : memberList) {
                try {
                    String wxid = "";
                    String displayName = "";

                    Method getWxidMethod = obj.getClass().getMethod("getWxid");
                    wxid = (String) getWxidMethod.invoke(obj);

                    if (wxid.equals(memberWxid)) {
                        // 获取显示名称
                        try {
                            Method getDisplayNameMethod = obj.getClass().getMethod("getDisplayName");
                            displayName = (String) getDisplayNameMethod.invoke(obj);
                            if (!TextUtils.isEmpty(displayName)) {
                                return displayName;
                            }
                        } catch (Exception e) {}

                        // 获取群内昵称
                        try {
                            Method getGroupNickMethod = obj.getClass().getMethod("getGroupNick");
                            String groupNick = (String) getGroupNickMethod.invoke(obj);
                            if (!TextUtils.isEmpty(groupNick)) {
                                return groupNick;
                            }
                        } catch (Exception e) {}

                        break;
                    }
                } catch (Exception e) {}
            }
        }
    } catch (Exception e) {
        log("获取群成员名称失败: " + e.getMessage());
    }
    return "";
}

/**
 * 触发通知
 */
private void triggerNotification(String keyword, String content, String senderInfo, String displayWxid, String openTalker, boolean isGroupChat) {
    if (quietHoursEnabled) {
        int currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        boolean inQuietTime = quietStartHour >= quietEndHour
            ? (currentHour >= quietStartHour || currentHour < quietEndHour)
            : (currentHour >= quietStartHour && currentHour < quietEndHour);
        if (inQuietTime) return;
    }

    final String finalSenderInfo = senderInfo;
    final String finalContent = content;
    final String finalKeyword = keyword;
    final String finalDisplayWxid = displayWxid;
    final String finalOpenTalker = openTalker;
    final boolean finalIsGroupChat = isGroupChat;
    final boolean isAtMe = finalKeyword.equals("@我");
    final boolean isAtAll = finalKeyword.equals("@所有人");

    if (notifyEnabled) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            public void run() {
                try {
                    String typeStr = finalIsGroupChat ? "群消息" : "好友";
                    String title;
                    String body;

                    String notifyTitleTemplate;
                    String notifyContentTemplate;
                    if (isAtMe) {
                        notifyTitleTemplate = customAtMeNotifyTitle;
                        notifyContentTemplate = customAtMeNotifyContent;
                    } else if (isAtAll) {
                        notifyTitleTemplate = customAtAllNotifyTitle;
                        notifyContentTemplate = customAtAllNotifyContent;
                    } else {
                        notifyTitleTemplate = customKeywordNotifyTitle;
                        notifyContentTemplate = customKeywordNotifyContent;
                    }

                    if (!TextUtils.isEmpty(notifyTitleTemplate)) {
                        title = notifyTitleTemplate
                            .replace("%keyword%", finalKeyword)
                            .replace("%sender%", finalSenderInfo)
                            .replace("%wxid%", finalDisplayWxid)
                            .replace("%content%", finalContent)
                            .replace("%type%", typeStr);
                    } else {
                        title = (isAtMe || isAtAll) ? "🔔 被" + finalKeyword + "通知" : "🔔 命中关键词: " + finalKeyword;
                    }

                    if (!TextUtils.isEmpty(notifyContentTemplate)) {
                        body = notifyContentTemplate
                            .replace("%keyword%", finalKeyword)
                            .replace("%sender%", finalSenderInfo)
                            .replace("%wxid%", finalDisplayWxid)
                            .replace("%content%", finalContent)
                            .replace("%type%", typeStr);
                    } else {
                        body = typeStr + " [" + finalSenderInfo + "]: " + finalContent;
                    }

                    sendKeywordNotification(finalOpenTalker, highlightKeywordText(title, finalKeyword), highlightKeywordText(body, finalKeyword), finalIsGroupChat);
                } catch (Exception e) {
                    log("发送通知失败: " + e.getMessage());
                }
            }
        });
    }

    if (toastEnabled) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            public void run() {
                try {
                    String toastMsg;
                    String toastTemplate;
                    if (isAtMe) {
                        toastTemplate = customAtMeToastText;
                    } else if (isAtAll) {
                        toastTemplate = customAtAllToastText;
                    } else {
                        toastTemplate = customKeywordToastText;
                    }
                    if (!TextUtils.isEmpty(toastTemplate)) {
                        toastMsg = toastTemplate
                            .replace("%keyword%", finalKeyword)
                            .replace("%sender%", finalSenderInfo)
                            .replace("%wxid%", finalDisplayWxid)
                            .replace("%content%", finalContent)
                            .replace("%type%", finalIsGroupChat ? "群消息" : "好友");
                    } else {
                        toastMsg = (isAtMe || isAtAll) ? "📢 被" + finalKeyword + "通知" : "📢 关键词: " + finalKeyword;
                    }
                    toast(toastMsg);
                } catch (Exception e) {
                    log("显示Toast失败: " + e.getMessage());
                }
            }
        });
    }
}

private Intent[] buildChatOpenIntents(String talker) {
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

private void ensureKeywordNotifyChannel(NotificationManager nm, String channelId) {
    if (nm == null || Build.VERSION.SDK_INT < 26 || TextUtils.isEmpty(channelId)) return;
    try {
        NotificationChannel channel = nm.getNotificationChannel(channelId);
        if (channel == null) {
            channel = new NotificationChannel(channelId, "关键词通知", NotificationManager.IMPORTANCE_HIGH);
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 250, 250, 250});
            nm.createNotificationChannel(channel);
        }
    } catch (Throwable ignored) {}
}

private void sendKeywordNotification(String talker, CharSequence title, CharSequence text, boolean isGroupChat) {
    try {
        NotificationManager nm = (NotificationManager) hostContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        String channelId = "keyword_notify_v1";
        ensureKeywordNotifyChannel(nm, channelId);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) builder = new Notification.Builder(hostContext, channelId);
        else builder = new Notification.Builder(hostContext);

        builder.setContentTitle(title)
               .setContentText(text)
               .setSmallIcon(android.R.drawable.stat_notify_chat)
               .setAutoCancel(true)
               .setOnlyAlertOnce(false);
        try { builder.setCategory(Notification.CATEGORY_MESSAGE); } catch (Throwable ignored) {}
        try { builder.setVisibility(Notification.VISIBILITY_PRIVATE); } catch (Throwable ignored) {}
        try { builder.setPriority(Notification.PRIORITY_HIGH); } catch (Throwable ignored) {}
        try { builder.setWhen(System.currentTimeMillis()); } catch (Throwable ignored) {}
        try { builder.setShowWhen(true); } catch (Throwable ignored) {}
        try { builder.setColor(Color.parseColor("#70A1B8")); } catch (Throwable ignored) {}
        try {
            builder.setStyle(new Notification.BigTextStyle()
                .bigText(text)
                .setBigContentTitle(title)
                .setSummaryText(isGroupChat ? "群消息" : "好友消息"));
        } catch (Throwable ignored) {}
        try { builder.setSubText(isGroupChat ? "群消息" : "好友消息"); } catch (Throwable ignored) {}

        Intent[] intents = buildChatOpenIntents(talker);
        if (intents != null && intents.length > 0) {
            builder.setContentIntent(PendingIntent.getActivities(hostContext, talker.hashCode(), intents, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        }

        Bundle extras = new Bundle();
        extras.putBoolean("is_keyword_notify", true);
        extras.putString("talker", talker);
        extras.putBoolean("is_group_chat", isGroupChat);
        builder.setExtras(extras);

        int notifyId = ("kw_notify_v1_" + talker).hashCode();
        nm.notify(notifyId, builder.build());
    } catch (Throwable e) {
        log("发送系统通知失败: " + e.getMessage());
    }
}

private CharSequence highlightKeywordText(String text, String keyword) {
    if (TextUtils.isEmpty(text) || TextUtils.isEmpty(keyword)) return text;
    try {
        String raw = String.valueOf(text);
        SpannableStringBuilder ssb = new SpannableStringBuilder(raw);
        int color = Color.parseColor("#FF9800");
        int from = 0;
        while (true) {
            int idx = raw.indexOf(keyword, from);
            if (idx < 0) break;
            ssb.setSpan(new ForegroundColorSpan(color), idx, idx + keyword.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            from = idx + keyword.length();
        }
        return ssb;
    } catch (Throwable ignored) {}
    return text;
}

private String buildWxidDisplay(Object msgInfoBean, boolean isGroupChat, String senderWxid) {
    if (!isGroupChat) return senderWxid;
    String talkerWxid = getFirstNonEmptyMsgText(msgInfoBean, new String[]{
        "getTalker", "talker",
        "getSessionId", "sessionId",
        "getChatId", "chatId",
        "getContentTalker", "contentTalker",
        "getChatUser", "chatUser",
        "getRoomId", "roomId"
    });
    if (TextUtils.isEmpty(talkerWxid) || !talkerWxid.endsWith("@chatroom")) {
        String rawContent = getFirstNonEmptyMsgText(msgInfoBean, new String[]{
            "getOriginContent", "originContent",
            "getMsgContent", "msgContent",
            "getContent", "content",
            "getText", "text"
        });
        String parsedTalker = extractChatroomId(rawContent);
        if (!TextUtils.isEmpty(parsedTalker)) talkerWxid = parsedTalker;
    }
    if (TextUtils.isEmpty(talkerWxid)) return senderWxid;
    if (TextUtils.isEmpty(senderWxid) || senderWxid.endsWith("@chatroom")) return talkerWxid;
    return talkerWxid + "|" + senderWxid;
}

// 入口函数
public boolean onClickSendBtn(String text) {
    if ("关键词通知".equals(text) || "关键词".equals(text) || "关键词监控".equals(text)) {
        showMainDialog();
        return true;
    }
    return false;
}

// ==========================================
// ========== 📱 UI 界面逻辑 ==========
// ==========================================

private void showMainDialog() {
    ScrollView scrollView = new ScrollView(getTopActivity());
    LinearLayout root = new LinearLayout(getTopActivity());
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(24, 24, 24, 24);
    root.setBackgroundColor(Color.parseColor("#FAFBF9"));
    scrollView.addView(root);

    // --- 顶部：状态卡片 ---
    LinearLayout statusCard = createCardLayout();
    statusCard.setBackground(createGradientDrawable("#E3F2FD", 32));
    statusCard.addView(createSectionTitle("📊 监控状态"));

    // 状态文本
    final TextView statusTv = new TextView(getTopActivity());
    StringBuilder statusSb = new StringBuilder();
    statusSb.append("监控状态: ").append(enabled ? "✅ 开启" : "❌ 关闭").append("\n");
    statusSb.append("关键词数量: ").append(keywordMap.size()).append("\n");
    statusSb.append("任意关键词-群聊: ").append(anyKeywordGroupNotifyEnabled ? "✅" : "❌").append("  私聊: ").append(anyKeywordPrivateNotifyEnabled ? "✅" : "❌").append("\n");
    statusSb.append("过滤模式: ").append(filterMode ? "🎯 仅生效模式" : "🚫 排除模式").append("\n");
    if (filterMode) {
        statusSb.append("仅生效联系人: ").append(includeContactSet.size()).append(" 个\n");
    } else {
        statusSb.append("排除联系人: ").append(excludeContactSet.size()).append(" 个\n");
    }
    statusSb.append("━━━━━━━━━━━━━\n");

    if (lastMatchTime > 0) {
        String lastTimeStr = formatTimeWithSeconds(lastMatchTime);
        statusSb.append("上次匹配: ").append(lastTimeStr).append("\n");
        statusSb.append("匹配词: ").append(lastMatchedKeyword);
    } else {
        statusSb.append("暂无匹配记录");
    }

    statusTv.setText(statusSb.toString());
    statusTv.setTextSize(14);
    statusTv.setTextColor(Color.parseColor("#1565C0"));
    statusCard.addView(statusTv);

    // 刷新状态按钮
    Button refreshBtn = new Button(getTopActivity());
    refreshBtn.setText("🔄 刷新状态");
    styleUtilityButton(refreshBtn);
    refreshBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            loadConfig();
            StringBuilder sb = new StringBuilder();
            sb.append("监控状态: ").append(enabled ? "✅ 开启" : "❌ 关闭").append("\n");
            sb.append("关键词数量: ").append(keywordMap.size()).append("\n");
            sb.append("任意关键词-群聊: ").append(anyKeywordGroupNotifyEnabled ? "✅" : "❌").append("  私聊: ").append(anyKeywordPrivateNotifyEnabled ? "✅" : "❌").append("\n");
            sb.append("过滤模式: ").append(filterMode ? "🎯 仅生效模式" : "🚫 排除模式").append("\n");
            if (filterMode) {
                sb.append("仅生效联系人: ").append(includeContactSet.size()).append(" 个\n");
            } else {
                sb.append("排除联系人: ").append(excludeContactSet.size()).append(" 个\n");
            }
            sb.append("━━━━━━━━━━━━━\n");

            if (lastMatchTime > 0) {
                String lastTimeStr = formatTimeWithSeconds(lastMatchTime);
                sb.append("上次匹配: ").append(lastTimeStr).append("\n");
                sb.append("匹配词: ").append(lastMatchedKeyword);
            } else {
                sb.append("暂无匹配记录");
            }

            statusTv.setText(sb.toString());
            toast("状态已刷新");
        }
    });
    statusCard.addView(refreshBtn);
    root.addView(statusCard);

    // --- 1. 关键词管理卡片 ---
    LinearLayout keywordCard = createCardLayout();
    keywordCard.addView(createSectionTitle("🔑 关键词管理"));

    // 关键词列表
    keywordCountTv = new TextView(getTopActivity());
    keywordCountTv.setText("当前关键词: " + keywordMap.size() + " 个");
    keywordCountTv.setTextSize(14);
    keywordCountTv.setTextColor(Color.parseColor("#666666"));
    keywordCountTv.setPadding(0, 0, 0, 16);
    keywordCard.addView(keywordCountTv);

    Button addKeywordBtn = new Button(getTopActivity());
    addKeywordBtn.setText("➕ 添加关键词");
    styleMediaSelectionButton(addKeywordBtn);
    addKeywordBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            showAddKeywordDialog();
        }
    });
    keywordCard.addView(addKeywordBtn);

    Button viewKeywordsBtn = new Button(getTopActivity());
    viewKeywordsBtn.setText("📋 查看/管理关键词");
    styleUtilityButton(viewKeywordsBtn);
    viewKeywordsBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            showKeywordListDialog();
        }
    });
    keywordCard.addView(viewKeywordsBtn);

    Button clearKeywordsBtn = new Button(getTopActivity());
    clearKeywordsBtn.setText("🗑️ 清空所有关键词");
    styleUtilityButton(clearKeywordsBtn);
    clearKeywordsBtn.setTextColor(Color.parseColor("#D32F2F"));
    clearKeywordsBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            showClearKeywordsConfirmDialog();
        }
    });
    keywordCard.addView(clearKeywordsBtn);

    root.addView(keywordCard);
    // --- 1.5 过滤模式切换卡片 ---
    LinearLayout filterModeCard = createCardLayout();
    filterModeCard.addView(createSectionTitle("🎯 过滤模式"));

    final TextView modeTip = new TextView(getTopActivity());
    modeTip.setText(filterMode ? 
        "当前: 仅生效模式 - 只对指定联系人生效\n其他联系人的消息将被忽略" : 
        "当前: 排除模式 - 排除指定联系人\n其他联系人的消息正常检测");
    modeTip.setTextSize(12);
    modeTip.setTextColor(Color.parseColor("#666666"));
    modeTip.setPadding(0, 0, 0, 16);
    filterModeCard.addView(modeTip);

    // 创建一个容器来放置动态切换的联系人管理卡片
    final LinearLayout contactCardContainer = new LinearLayout(getTopActivity());
    contactCardContainer.setOrientation(LinearLayout.VERTICAL);

    final LinearLayout filterModeRow = createSwitchRow(
        filterMode ? "🎯 仅生效模式" : "🚫 排除模式", 
        filterMode, 
        14, 
        new ToggleCallback() {
            public void onToggle(boolean checked) {
                filterMode = checked;
                saveConfig();
                toast(filterMode ? "已切换到仅生效模式" : "已切换到排除模式");
                
                // 更新提示文字
                modeTip.setText(filterMode ? 
                    "当前: 仅生效模式 - 只对指定联系人生效\n其他联系人的消息将被忽略" : 
                    "当前: 排除模式 - 排除指定联系人\n其他联系人的消息正常检测");
                
                // 更新开关文字
                TextView label = (TextView) filterModeRow.getChildAt(0);
                label.setText(filterMode ? "🎯 仅生效模式" : "🚫 排除模式");
                
                // 动态切换联系人管理卡片
                contactCardContainer.removeAllViews();
                if (filterMode) {
                    contactCardContainer.addView(createIncludeContactCard());
                } else {
                    contactCardContainer.addView(createExcludeContactCard());
                }
            }
        }
    );
    filterModeCard.addView(filterModeRow);

    root.addView(filterModeCard);

    // 初始化显示对应的联系人管理卡片
    if (filterMode) {
        contactCardContainer.addView(createIncludeContactCard());
    } else {
        contactCardContainer.addView(createExcludeContactCard());
    }
    root.addView(contactCardContainer);

    // --- 2. 通知设置卡片 ---
    LinearLayout notifyCard = createCardLayout();
    notifyCard.addView(createSectionTitle("🔔 通知设置"));

    // 总开关
    final LinearLayout enableRow = createSwitchRow("启用关键词监控", enabled, 16, new ToggleCallback() {
        public void onToggle(boolean checked) {
            enabled = checked;
            saveConfig();
            toast(enabled ? "监控已开启" : "监控已关闭");
        }
    });
    notifyCard.addView(enableRow);

    // 通知开关
    final LinearLayout notifyRow = createSwitchRow("系统通知", notifyEnabled, 14, new ToggleCallback() {
        public void onToggle(boolean checked) {
            notifyEnabled = checked;
            saveConfig();
        }
    });
    notifyCard.addView(notifyRow);

    // Toast开关
    final LinearLayout toastRow = createSwitchRow("Toast提示", toastEnabled, 14, new ToggleCallback() {
        public void onToggle(boolean checked) {
            toastEnabled = checked;
            saveConfig();
        }
    });
    notifyCard.addView(toastRow);

    // 任意关键词-群聊通知开关
    final LinearLayout anyKeywordGroupRow = createSwitchRow("任意关键词-群聊通知", anyKeywordGroupNotifyEnabled, 14, new ToggleCallback() {
        public void onToggle(boolean checked) {
            anyKeywordGroupNotifyEnabled = checked;
            saveConfig();
            toast("群聊任意关键词通知已" + (checked ? "开启" : "关闭"));
        }
    });
    notifyCard.addView(anyKeywordGroupRow);

    // 任意关键词-私聊通知开关
    final LinearLayout anyKeywordPrivateRow = createSwitchRow("任意关键词-私聊通知", anyKeywordPrivateNotifyEnabled, 14, new ToggleCallback() {
        public void onToggle(boolean checked) {
            anyKeywordPrivateNotifyEnabled = checked;
            saveConfig();
            toast("私聊任意关键词通知已" + (checked ? "开启" : "关闭"));
        }
    });
    notifyCard.addView(anyKeywordPrivateRow);

    // 被@我通知开关
    final LinearLayout atMeRow = createSwitchRow("@我通知", atMeEnabled, 14, new ToggleCallback() {
        public void onToggle(boolean checked) {
            atMeEnabled = checked;
            saveConfig();
        }
    });
    notifyCard.addView(atMeRow);

    // @所有人/群公告通知开关
    final LinearLayout atAllRow = createSwitchRow("@所有人/群公告通知", atAllEnabled, 14, new ToggleCallback() {
        public void onToggle(boolean checked) {
            atAllEnabled = checked;
            saveConfig();
        }
    });
    notifyCard.addView(atAllRow);

    // 自定义文字设置按钮
    Button customTextBtn = new Button(getTopActivity());
    customTextBtn.setText("📝 自定义通知/Toast文字");
    styleUtilityButton(customTextBtn);
    customTextBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            showCustomTextDialog();
        }
    });
    notifyCard.addView(customTextBtn);

    // 显示当前是否使用了自定义文字
    TextView customTextTip = new TextView(getTopActivity());
    boolean hasCustomText = !TextUtils.isEmpty(customKeywordNotifyTitle) || !TextUtils.isEmpty(customKeywordNotifyContent) || !TextUtils.isEmpty(customKeywordToastText) || 
                            !TextUtils.isEmpty(customAtMeNotifyTitle) || !TextUtils.isEmpty(customAtMeNotifyContent) || !TextUtils.isEmpty(customAtMeToastText) ||
                            !TextUtils.isEmpty(customAtAllNotifyTitle) || !TextUtils.isEmpty(customAtAllNotifyContent) || !TextUtils.isEmpty(customAtAllToastText);
    customTextTip.setText(hasCustomText ? "✅ 已设置自定义文字" : "⚪ 使用默认文字");
    customTextTip.setTextSize(12);
    customTextTip.setTextColor(hasCustomText ? Color.parseColor("#4CAF50") : Color.parseColor("#999999"));
    customTextTip.setPadding(0, 8, 0, 16);
    notifyCard.addView(customTextTip);

    root.addView(notifyCard);

    // --- 3. 免打扰设置卡片 ---
    LinearLayout quietCard = createCardLayout();
    quietCard.addView(createSectionTitle("🌙 免打扰设置"));

    final LinearLayout quietRow = createSwitchRow("启用免打扰", quietHoursEnabled, 14, new ToggleCallback() {
        public void onToggle(boolean checked) {
            quietHoursEnabled = checked;
            saveConfig();
        }
    });
    quietCard.addView(quietRow);

    final TextView quietTimeTv = new TextView(getTopActivity());
    quietTimeTv.setText("免打扰时间: " + quietStartHour + ":00 - " + quietEndHour + ":00");
    quietTimeTv.setTextSize(12);
    quietTimeTv.setTextColor(Color.parseColor("#999999"));
    quietTimeTv.setPadding(0, 8, 0, 16);
    quietCard.addView(quietTimeTv);

    Button setQuietTimeBtn = new Button(getTopActivity());
    setQuietTimeBtn.setText("⏰ 设置免打扰时间");
    styleUtilityButton(setQuietTimeBtn);
    setQuietTimeBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            showQuietTimeDialog();
        }
    });
    quietCard.addView(setQuietTimeBtn);

    root.addView(quietCard);

    // --- 底部按钮 ---
    AlertDialog dialog = buildCommonAlertDialog(getTopActivity(), "🔔 关键词通知助手", scrollView, "💾 保存设置", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
            saveConfig();
            toast("✅ 设置已保存");
        }
    }, "关闭", null, null, null);

    dialog.show();
}


/**
 * 创建仅生效联系人管理卡片（白名单）
 */
private LinearLayout createIncludeContactCard() {
    LinearLayout includeCard = createCardLayout();
    includeCard.addView(createSectionTitle("🎯 仅生效联系人（白名单）"));

    // 当前已添加数量
    includeCountTv = new TextView(getTopActivity());
    includeCountTv.setText("已添加 " + includeContactSet.size() + " 个联系人");
    includeCountTv.setTextSize(14);
    includeCountTv.setTextColor(Color.parseColor("#666666"));
    includeCountTv.setPadding(0, 0, 0, 12);
    includeCard.addView(includeCountTv);

    // 添加按钮行
    LinearLayout addRow = new LinearLayout(getTopActivity());
    addRow.setOrientation(LinearLayout.HORIZONTAL);
    addRow.setWeightSum(3);

    Button addFriendBtn = new Button(getTopActivity());
    addFriendBtn.setText("👤 好友");
    styleUtilityButton(addFriendBtn);
    addFriendBtn.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
    addFriendBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            selectFriendToInclude();
        }
    });

    Button addGroupBtn = new Button(getTopActivity());
    addGroupBtn.setText("💬 群聊");
    styleUtilityButton(addGroupBtn);
    addGroupBtn.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
    addGroupBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            selectGroupToInclude();
        }
    });

    Button manualBtn = new Button(getTopActivity());
    manualBtn.setText("📝 手动");
    styleUtilityButton(manualBtn);
    manualBtn.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
    manualBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            showManualAddIncludeDialog();
        }
    });

    addRow.addView(addFriendBtn);
    addRow.addView(addGroupBtn);
    addRow.addView(manualBtn);
    includeCard.addView(addRow);

    // 仅生效列表
    TextView listTitle = new TextView(getTopActivity());
    listTitle.setText("仅生效列表（点击可移除）:");
    listTitle.setTextSize(12);
    listTitle.setTextColor(Color.parseColor("#999999"));
    listTitle.setPadding(0, 16, 0, 8);
    includeCard.addView(listTitle);

    includeListView = new ListView(getTopActivity());
    setupListViewTouchForScroll(includeListView);

    includeContactList = new ArrayList<>(includeContactSet);
    includeDisplayList = new ArrayList<>();

    for (String contactId : includeContactList) {
        includeDisplayList.add(buildContactDisplayName(contactId));
    }

    includeAdapter = new ArrayAdapter<>(getTopActivity(), android.R.layout.simple_list_item_1, includeDisplayList);
    includeListView.setAdapter(includeAdapter);

    final Runnable refreshListRunnable = new Runnable() {
        public void run() {
            includeContactList.clear();
            includeContactList.addAll(includeContactSet);
            includeDisplayList.clear();
            for (String contactId : includeContactList) {
                includeDisplayList.add(buildContactDisplayName(contactId));
            }
            includeAdapter.notifyDataSetChanged();
            includeCountTv.setText("已添加 " + includeContactSet.size() + " 个联系人");

            int itemHeight = dpToPx(48);
            int listHeight = Math.max(Math.min(includeContactList.size() * itemHeight, dpToPx(200)), dpToPx(48));
            LinearLayout.LayoutParams listParams = (LinearLayout.LayoutParams) includeListView.getLayoutParams();
            if (listParams == null) {
                listParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, listHeight
                );
                listParams.setMargins(0, 8, 0, 0);
                includeListView.setLayoutParams(listParams);
            } else {
                listParams.height = listHeight;
            }
        }
    };

    int itemHeight = dpToPx(48);
    int listHeight = Math.max(Math.min(includeContactList.size() * itemHeight, dpToPx(200)), dpToPx(48));
    LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, listHeight
    );
    listParams.setMargins(0, 8, 0, 0);
    includeListView.setLayoutParams(listParams);

    includeCard.addView(includeListView);

    includeClearBtn = new Button(getTopActivity());
    includeClearBtn.setText("🗑️ 清空全部");
    styleUtilityButton(includeClearBtn);
    includeClearBtn.setTextColor(Color.parseColor("#D32F2F"));
    includeClearBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
                showClearContactsConfirmDialog(includeContactSet, "🗑️ 清空仅生效列表", "确定要清空所有仅生效联系人吗?", new Runnable() {
                    public void run() {
                        refreshIncludeDisplay();
                    }
                }, "✅ 已清空仅生效列表");
            }
        });
    includeClearBtn.setVisibility(includeContactSet.size() > 1 ? View.VISIBLE : View.GONE);
    includeCard.addView(includeClearBtn);

    final Button finalClearBtn = includeClearBtn;
    Runnable updateClearBtnVisibility = new Runnable() {
        public void run() {
            finalClearBtn.setVisibility(includeContactSet.size() > 1 ? View.VISIBLE : View.GONE);
        }
    };

    Runnable fullRefreshRunnable = new Runnable() {
        public void run() {
            refreshListRunnable.run();
            updateClearBtnVisibility.run();
        }
    };

    includeListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            if (position < includeContactList.size()) {
                String contactId = includeContactList.get(position);
                String displayName = includeDisplayList.get(position);
                showRemoveContactDialog(includeContactSet, "仅生效", contactId, displayName, "✅ 已移除仅生效", "将不再触发通知。", fullRefreshRunnable);
            }
        }
    });

    return includeCard;
}


/**
 * 创建排除联系人管理卡片（黑名单）
 */
private LinearLayout createExcludeContactCard() {
    LinearLayout excludeCard = createCardLayout();
    excludeCard.addView(createSectionTitle("🚫 排除联系人（黑名单）"));

    excludeCountTv = new TextView(getTopActivity());
    excludeCountTv.setText("已排除 " + excludeContactSet.size() + " 个联系人");
    excludeCountTv.setTextSize(14);
    excludeCountTv.setTextColor(Color.parseColor("#666666"));
    excludeCountTv.setPadding(0, 0, 0, 12);
    excludeCard.addView(excludeCountTv);

    LinearLayout addRow = new LinearLayout(getTopActivity());
    addRow.setOrientation(LinearLayout.HORIZONTAL);
    addRow.setWeightSum(3);

    Button addFriendBtn = new Button(getTopActivity());
    addFriendBtn.setText("👤 好友");
    styleUtilityButton(addFriendBtn);
    addFriendBtn.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
    addFriendBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            selectFriendToExclude();
        }
    });

    Button addGroupBtn = new Button(getTopActivity());
    addGroupBtn.setText("💬 群聊");
    styleUtilityButton(addGroupBtn);
    addGroupBtn.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
    addGroupBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            selectGroupToExclude();
        }
    });

    Button manualBtn = new Button(getTopActivity());
    manualBtn.setText("📝 手动");
    styleUtilityButton(manualBtn);
    manualBtn.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
    manualBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            showManualAddExcludeDialog();
        }
    });

    addRow.addView(addFriendBtn);
    addRow.addView(addGroupBtn);
    addRow.addView(manualBtn);
    excludeCard.addView(addRow);

    TextView listTitle = new TextView(getTopActivity());
    listTitle.setText("已排除列表（点击可移除）:");
    listTitle.setTextSize(12);
    listTitle.setTextColor(Color.parseColor("#999999"));
    listTitle.setPadding(0, 16, 0, 8);
    excludeCard.addView(listTitle);

    excludeListView = new ListView(getTopActivity());
    setupListViewTouchForScroll(excludeListView);

    excludeContactList = new ArrayList<>(excludeContactSet);
    excludeDisplayList = new ArrayList<>();

    for (String contactId : excludeContactList) {
        excludeDisplayList.add(buildContactDisplayName(contactId));
    }

    excludeAdapter = new ArrayAdapter<>(getTopActivity(), android.R.layout.simple_list_item_1, excludeDisplayList);
    excludeListView.setAdapter(excludeAdapter);

    final Runnable refreshListRunnable = new Runnable() {
        public void run() {
            excludeContactList.clear();
            excludeContactList.addAll(excludeContactSet);
            excludeDisplayList.clear();
            for (String contactId : excludeContactList) {
                excludeDisplayList.add(buildContactDisplayName(contactId));
            }
            excludeAdapter.notifyDataSetChanged();
            excludeCountTv.setText("已排除 " + excludeContactSet.size() + " 个联系人");

            int itemHeight = dpToPx(48);
            int listHeight = Math.max(Math.min(excludeContactList.size() * itemHeight, dpToPx(200)), dpToPx(48));
            LinearLayout.LayoutParams listParams = (LinearLayout.LayoutParams) excludeListView.getLayoutParams();
            if (listParams == null) {
                listParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, listHeight
                );
                listParams.setMargins(0, 8, 0, 0);
                excludeListView.setLayoutParams(listParams);
            } else {
                listParams.height = listHeight;
            }
        }
    };

    int itemHeight = dpToPx(48);
    int listHeight = Math.max(Math.min(excludeContactList.size() * itemHeight, dpToPx(200)), dpToPx(48));
    LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, listHeight
    );
    listParams.setMargins(0, 8, 0, 0);
    excludeListView.setLayoutParams(listParams);

    excludeCard.addView(excludeListView);

    excludeClearBtn = new Button(getTopActivity());
    excludeClearBtn.setText("🗑️ 清空全部");
    styleUtilityButton(excludeClearBtn);
    excludeClearBtn.setTextColor(Color.parseColor("#D32F2F"));
    excludeClearBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
                showClearContactsConfirmDialog(excludeContactSet, "🗑️ 清空排除列表", "确定要清空所有排除联系人吗？", new Runnable() {
                    public void run() {
                        refreshExcludeDisplay();
                    }
                }, "✅ 已清空排除列表");
            }
        });
    excludeClearBtn.setVisibility(excludeContactSet.size() > 1 ? View.VISIBLE : View.GONE);
    excludeCard.addView(excludeClearBtn);

    final Button finalClearBtn = excludeClearBtn;
    Runnable updateClearBtnVisibility = new Runnable() {
        public void run() {
            finalClearBtn.setVisibility(excludeContactSet.size() > 1 ? View.VISIBLE : View.GONE);
        }
    };

    Runnable fullRefreshRunnable = new Runnable() {
        public void run() {
            refreshListRunnable.run();
            updateClearBtnVisibility.run();
        }
    };

    excludeListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            if (position < excludeContactList.size()) {
                String contactId = excludeContactList.get(position);
                String displayName = excludeDisplayList.get(position);
                showRemoveContactDialog(excludeContactSet, "排除", contactId, displayName, "✅ 已移除排除", "将正常检查关键词。", fullRefreshRunnable);
            }
        }
    });

    return excludeCard;
}

/**
 * 添加关键词对话框
 */
private void showAddKeywordDialog() {
    ScrollView scrollView = new ScrollView(getTopActivity());
    LinearLayout root = new LinearLayout(getTopActivity());
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(32, 32, 32, 32);
    scrollView.addView(root);

    // 关键词输入框
    final EditText input = new EditText(getTopActivity());
    input.setHint("输入要监控的关键词");
    input.setPadding(24, 24, 24, 24);
    root.addView(input);

    // 选中模式: 0=模糊匹配, 1=全字匹配
    final int[] selectedMode = {0};
    addKeywordModeSelector(root, selectedMode, 0);

    AlertDialog dialog = new AlertDialog.Builder(getTopActivity())
        .setTitle("➕ 添加关键词")
        .setView(scrollView)
        .setPositiveButton("添加", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                String keyword = input.getText().toString().trim();
                if (TextUtils.isEmpty(keyword)) {
                    toast("关键词不能为空");
                    return;
                }
                if (keywordMap.containsKey(keyword)) {
                    toast("该关键词已存在");
                    return;
                }
                boolean isWholeWord = (selectedMode[0] == 1);
                keywordMap.put(keyword, isWholeWord);
                saveConfig();
                refreshKeywordCount();
                toast("✅ 已添加关键词: " + keyword + (isWholeWord ? " (全字匹配)" : ""));
            }
        })
        .setNegativeButton("取消", null)
        .create();

    dialog.setOnShowListener(new DialogInterface.OnShowListener() {
        public void onShow(DialogInterface d) {
            setupUnifiedDialog((AlertDialog)d);
        }
    });
    dialog.show();
}

/**
 * 关键词列表对话框
 */
private void showKeywordListDialog() {
    ScrollView scrollView = new ScrollView(getTopActivity());
    LinearLayout root = new LinearLayout(getTopActivity());
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(24, 24, 24, 24);
    root.setBackgroundColor(Color.parseColor("#FAFBF9"));
    scrollView.addView(root);

    root.addView(createSectionTitle("📋 关键词列表 (" + keywordMap.size() + ")"));

    if (keywordMap.isEmpty()) {
        root.addView(createPromptText("暂无关键词，请先添加"));
    } else {
        final ListView keywordListView = new ListView(getTopActivity());
        setupListViewTouchForScroll(keywordListView);

        final List<String> keywordList = new ArrayList<>(keywordMap.keySet());
        final List<String> displayList = new ArrayList<>();
        for (String keyword : keywordList) {
            Boolean isWholeWord = keywordMap.get(keyword);
            String mode = (isWholeWord != null && isWholeWord) ? " [全字]" : " [模糊]";
            displayList.add(keyword + mode);
        }
        final ArrayAdapter<String> adapter = new ArrayAdapter<String>(getTopActivity(), android.R.layout.simple_list_item_1, displayList);
        keywordListView.setAdapter(adapter);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 
            dpToPx(Math.min(keywordList.size() * 70, 350))
        );
        params.setMargins(0, 16, 0, 16);
        keywordListView.setLayoutParams(params);

        // 更新显示列表的方法
        final Runnable updateDisplayList = new Runnable() {
            public void run() {
                displayList.clear();
                keywordList.clear();
                keywordList.addAll(keywordMap.keySet());
                for (String keyword : keywordList) {
                    Boolean isWholeWord = keywordMap.get(keyword);
                    String mode = (isWholeWord != null && isWholeWord) ? " [全字]" : " [模糊]";
                    displayList.add(keyword + mode);
                }
                adapter.notifyDataSetChanged();
            }
        };

        keywordListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                final String keyword = keywordList.get(position);
                String[] options = {"✏️ 编辑关键词", "🗑️ 删除关键词"};

                AlertDialog.Builder builder = new AlertDialog.Builder(getTopActivity());
                builder.setTitle("操作关键词");
                builder.setItems(options, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        if (which == 0) {
                            showEditKeywordDialog(keyword, adapter, keywordList, displayList, updateDisplayList);
                        } else {
                            showDeleteKeywordConfirmDialog(keyword, adapter, keywordList, displayList, updateDisplayList);
                        }
                    }
                });
                builder.show();
            }
        });

        root.addView(keywordListView);
        root.addView(createPromptText("点击关键词可进行编辑或删除"));
    }

    Button addBtn = new Button(getTopActivity());
    addBtn.setText("➕ 添加新关键词");
    styleMediaSelectionButton(addBtn);
    addBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            showAddKeywordDialog();
        }
    });
    root.addView(addBtn);

    AlertDialog dialog = buildCommonAlertDialog(getTopActivity(), "📋 关键词管理", scrollView, "关闭", null, null, null, null, null);
    dialog.show();
}

private void addKeywordModeSelector(LinearLayout root, final int[] selectedMode, int initialMode) {
    selectedMode[0] = initialMode == 1 ? 1 : 0;

    TextView modeLabel = new TextView(getTopActivity());
    modeLabel.setText("匹配模式:");
    modeLabel.setTextSize(14);
    modeLabel.setTextColor(Color.parseColor("#666666"));
    modeLabel.setPadding(0, 24, 0, 12);
    root.addView(modeLabel);

    LinearLayout buttonContainer = new LinearLayout(getTopActivity());
    buttonContainer.setOrientation(LinearLayout.HORIZONTAL);
    buttonContainer.setWeightSum(2);
    buttonContainer.setPadding(0, 0, 0, 16);

    final TextView fuzzyBtn = new TextView(getTopActivity());
    fuzzyBtn.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
    fuzzyBtn.setText("🔍 模糊匹配");
    fuzzyBtn.setTextSize(14);
    fuzzyBtn.setGravity(Gravity.CENTER);
    fuzzyBtn.setPadding(16, 20, 16, 20);
    fuzzyBtn.setBackgroundResource(android.R.drawable.btn_default);

    final TextView wholeWordBtn = new TextView(getTopActivity());
    wholeWordBtn.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
    wholeWordBtn.setText("📝 全字匹配");
    wholeWordBtn.setTextSize(14);
    wholeWordBtn.setGravity(Gravity.CENTER);
    wholeWordBtn.setPadding(16, 20, 16, 20);
    wholeWordBtn.setBackgroundResource(android.R.drawable.btn_default);

    final TextView modeDesc = new TextView(getTopActivity());
    modeDesc.setTextSize(12);
    modeDesc.setTextColor(Color.parseColor("#888888"));
    modeDesc.setPadding(0, 0, 0, 16);

    final Runnable updateButtonStyle = new Runnable() {
        public void run() {
            if (selectedMode[0] == 0) {
                fuzzyBtn.setBackgroundColor(Color.parseColor("#4CAF50"));
                fuzzyBtn.setTextColor(Color.WHITE);
                wholeWordBtn.setBackgroundColor(Color.parseColor("#E0E0E0"));
                wholeWordBtn.setTextColor(Color.parseColor("#333333"));
                modeDesc.setText("🔍 模糊匹配 - 关键词包含在消息中即可触发");
            } else {
                fuzzyBtn.setBackgroundColor(Color.parseColor("#E0E0E0"));
                fuzzyBtn.setTextColor(Color.parseColor("#333333"));
                wholeWordBtn.setBackgroundColor(Color.parseColor("#2196F3"));
                wholeWordBtn.setTextColor(Color.WHITE);
                modeDesc.setText("📝 全字匹配 - 只有完整单词匹配才触发");
            }
        }
    };

    updateButtonStyle.run();

    fuzzyBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            selectedMode[0] = 0;
            updateButtonStyle.run();
        }
    });

    wholeWordBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            selectedMode[0] = 1;
            updateButtonStyle.run();
        }
    });

    buttonContainer.addView(fuzzyBtn);
    buttonContainer.addView(wholeWordBtn);
    root.addView(buttonContainer);
    root.addView(modeDesc);
}

/**
 * 编辑关键词对话框
 */
private void showEditKeywordDialog(final String oldKeyword, final ArrayAdapter<String> adapter, final List<String> keywordList, final List<String> displayList, final Runnable updateDisplayList) {
    ScrollView scrollView = new ScrollView(getTopActivity());
    LinearLayout root = new LinearLayout(getTopActivity());
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(32, 32, 32, 32);
    scrollView.addView(root);

    // 关键词输入框
    final EditText input = new EditText(getTopActivity());
    input.setText(oldKeyword);
    input.setPadding(24, 24, 24, 24);
    root.addView(input);

    // 当前匹配模式
    Boolean isWholeWord = keywordMap.get(oldKeyword);
    final boolean currentWholeWord = (isWholeWord != null && isWholeWord);

    // 选中模式: 初始值为当前保存的模式
    final int[] selectedMode = {currentWholeWord ? 1 : 0};
    addKeywordModeSelector(root, selectedMode, selectedMode[0]);

    AlertDialog dialog = new AlertDialog.Builder(getTopActivity())
        .setTitle("✏️ 编辑关键词")
        .setView(scrollView)
        .setPositiveButton("保存", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                String newKeyword = input.getText().toString().trim();
                if (TextUtils.isEmpty(newKeyword)) {
                    toast("关键词不能为空");
                    return;
                }
                // 如果改名称但名称已存在
                if (!newKeyword.equals(oldKeyword) && keywordMap.containsKey(newKeyword)) {
                    toast("该关键词已存在");
                    return;
                }

                Boolean oldWholeWord = keywordMap.get(oldKeyword);
                boolean isWholeWord = (oldWholeWord != null && oldWholeWord);

                // 判断新模式是否为全字匹配
                boolean newIsWholeWord = (selectedMode[0] == 1);

                // 如果新关键词和旧关键词不同，或者匹配模式改变了
                if (!newKeyword.equals(oldKeyword) || newIsWholeWord != isWholeWord) {
                    keywordMap.remove(oldKeyword);
                    keywordMap.put(newKeyword, newIsWholeWord);
                    saveConfig();
                    refreshKeywordCount();
                }

                updateDisplayList.run();
                toast("✅ 关键词已更新");
            }
        })
        .setNegativeButton("取消", null)
        .create();

    dialog.setOnShowListener(new DialogInterface.OnShowListener() {
        public void onShow(DialogInterface d) {
            setupUnifiedDialog((AlertDialog)d);
        }
    });
    dialog.show();
}

/**
 * 删除关键词确认对话框
 */
private void showDeleteKeywordConfirmDialog(final String keyword, final ArrayAdapter<String> adapter, final List<String> keywordList, final List<String> displayList, final Runnable updateDisplayList) {
    AlertDialog dialog = new AlertDialog.Builder(getTopActivity())
        .setTitle("🗑️ 删除关键词")
        .setMessage("确定要删除关键词 [" + keyword + "] 吗？")
        .setPositiveButton("删除", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                keywordMap.remove(keyword);
                saveConfig();
                refreshKeywordCount();

                int pos = keywordList.indexOf(keyword);
                if (pos >= 0) {
                    keywordList.remove(pos);
                    displayList.remove(pos);
                    adapter.notifyDataSetChanged();
                }
                toast("✅ 关键词已删除");
            }
        })
        .setNegativeButton("取消", null)
        .create();

    dialog.setOnShowListener(new DialogInterface.OnShowListener() {
        public void onShow(DialogInterface d) {
            setupUnifiedDialog((AlertDialog)d);
        }
    });
    dialog.show();
}

/**
 * 清空所有关键词确认对话框
 */
private void showClearKeywordsConfirmDialog() {
    AlertDialog dialog = new AlertDialog.Builder(getTopActivity())
        .setTitle("🗑️ 清空所有关键词")
        .setMessage("确定要清空所有关键词吗？此操作不可恢复。")
        .setPositiveButton("清空", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                keywordMap.clear();
                saveConfig();
                refreshKeywordCount();
                toast("✅ 所有关键词已清空");
            }
        })
        .setNegativeButton("取消", null)
        .create();

    dialog.setOnShowListener(new DialogInterface.OnShowListener() {
        public void onShow(DialogInterface d) {
            setupUnifiedDialog((AlertDialog)d);
        }
    });
    dialog.show();
}

/**
 * 选择好友添加到排除列表 (改进版:支持搜索、全选)
 */
private void selectFriendToExclude() {
    selectContactsWithDialog("👤 选择要排除的好友 (支持搜索)", "加载好友列表", "正在获取好友...", false, excludeContactSet, new Runnable() {
        public void run() {
            refreshExcludeDisplay();
            toast("✅ 已更新排除列表，当前排除 " + excludeContactSet.size() + " 个联系人");
        }
    });
}

/**
 * 选择群聊添加到排除列表 (改进版:支持搜索、全选)
 */
private void selectGroupToExclude() {
    selectContactsWithDialog("💬 选择要排除的群聊 (支持搜索)", "加载群聊列表", "正在获取群聊...", true, excludeContactSet, new Runnable() {
        public void run() {
            refreshExcludeDisplay();
            toast("✅ 已更新排除列表，当前排除 " + excludeContactSet.size() + " 个联系人");
        }
    });
}

/**
 * 手动输入wxid添加排除
 */
private void showManualAddExcludeDialog() {
    ScrollView scrollView = new ScrollView(getTopActivity());
    LinearLayout root = new LinearLayout(getTopActivity());
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(32, 32, 32, 32);
    scrollView.addView(root);

    TextView hint = new TextView(getTopActivity());
    hint.setText("输入要排除的wxid（好友wxid或群聊ID）：");
    hint.setTextSize(14);
    hint.setTextColor(Color.parseColor("#666666"));
    root.addView(hint);

    final EditText input = new EditText(getTopActivity());
    input.setHint("例如: wxid_abc123 或 123456789@chatroom");
    input.setPadding(24, 24, 24, 24);
    root.addView(input);

    AlertDialog dialog = new AlertDialog.Builder(getTopActivity())
        .setTitle("📝 手动添加")
        .setView(scrollView)
        .setPositiveButton("添加", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                String wxid = input.getText().toString().trim();
                if (TextUtils.isEmpty(wxid)) {
                    toast("请输入wxid");
                    return;
                }
                if (excludeContactSet.contains(wxid)) {
                    toast("该wxid已在排除列表中");
                    return;
                }
                excludeContactSet.add(wxid);
                saveConfig();
                refreshExcludeDisplay();
                toast("✅ 已添加排除: " + wxid);
            }
        })
        .setNegativeButton("取消", null)
        .create();

    dialog.setOnShowListener(new DialogInterface.OnShowListener() {
        public void onShow(DialogInterface d) {
            setupUnifiedDialog((AlertDialog)d);
        }
    });
    dialog.show();
}


/**
 * 选择好友添加到仅生效列表
 */
private void selectFriendToInclude() {
    selectContactsWithDialog("👤 选择仅生效的好友 (支持搜索)", "加载好友列表", "正在获取好友...", false, includeContactSet, new Runnable() {
        public void run() {
            refreshIncludeDisplay();
            toast("✅ 已更新仅生效列表，当前 " + includeContactSet.size() + " 个联系人");
        }
    });
}

/**
 * 选择群聊添加到仅生效列表
 */
private void selectGroupToInclude() {
    selectContactsWithDialog("💬 选择仅生效的群聊 (支持搜索)", "加载群聊列表", "正在获取群聊...", true, includeContactSet, new Runnable() {
        public void run() {
            refreshIncludeDisplay();
            toast("✅ 已更新仅生效列表，当前 " + includeContactSet.size() + " 个联系人");
        }
    });
}

/**
 * 手动输入wxid添加到仅生效列表
 */
private void showManualAddIncludeDialog() {
    ScrollView scrollView = new ScrollView(getTopActivity());
    LinearLayout root = new LinearLayout(getTopActivity());
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(32, 32, 32, 32);
    scrollView.addView(root);

    TextView hint = new TextView(getTopActivity());
    hint.setText("输入仅生效的wxid（好友wxid或群聊ID）:");
    hint.setTextSize(14);
    hint.setTextColor(Color.parseColor("#666666"));
    root.addView(hint);

    final EditText input = new EditText(getTopActivity());
    input.setHint("例如: wxid_abc123 或 123456789@chatroom");
    input.setPadding(24, 24, 24, 24);
    root.addView(input);

    AlertDialog dialog = new AlertDialog.Builder(getTopActivity())
        .setTitle("📝 手动添加")
        .setView(scrollView)
        .setPositiveButton("添加", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                String wxid = input.getText().toString().trim();
                if (TextUtils.isEmpty(wxid)) {
                    toast("请输入wxid");
                    return;
                }
                if (includeContactSet.contains(wxid)) {
                    toast("该wxid已在仅生效列表中");
                    return;
                }
                includeContactSet.add(wxid);
                saveConfig();
                refreshIncludeDisplay();
                toast("✅ 已添加仅生效: " + wxid);
            }
        })
        .setNegativeButton("取消", null)
        .create();

    dialog.setOnShowListener(new DialogInterface.OnShowListener() {
        public void onShow(DialogInterface d) {
            setupUnifiedDialog((AlertDialog)d);
        }
    });
    dialog.show();
}

private void showRemoveContactDialog(final Set<String> targetSet, final String sceneLabel, final String contactId, final String displayName, final String removeToast, final String removeEffectMsg, final Runnable onRemoved) {
    String type = contactId.endsWith("@chatroom") ? "群聊" : "好友";
    AlertDialog dialog = new AlertDialog.Builder(getTopActivity())
        .setTitle("移除" + sceneLabel)
        .setMessage("确定要移除" + sceneLabel + type + " [" + displayName + "] 吗？\n移除后，来自该" + type + "的消息" + removeEffectMsg)
        .setPositiveButton("移除", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                targetSet.remove(contactId);
                saveConfig();
                onRemoved.run();
                toast(removeToast);
            }
        })
        .setNegativeButton("取消", null)
        .create();
    dialog.setOnShowListener(new DialogInterface.OnShowListener() {
        public void onShow(DialogInterface d) {
            setupUnifiedDialog((AlertDialog)d);
        }
    });
    dialog.show();
}

private void showClearContactsConfirmDialog(final Set<String> targetSet, String title, String message, final Runnable refreshRunnable, final String successToast) {
    AlertDialog dialog = new AlertDialog.Builder(getTopActivity())
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton("清空", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                targetSet.clear();
                saveConfig();
                refreshRunnable.run();
                toast(successToast);
            }
        })
        .setNegativeButton("取消", null)
        .create();
    dialog.setOnShowListener(new DialogInterface.OnShowListener() {
        public void onShow(DialogInterface d) {
            setupUnifiedDialog((AlertDialog)d);
        }
    });
    dialog.show();
}

private void selectContactsWithDialog(final String dialogTitle, final String loadingTitle, final String loadingMsg, final boolean isGroup, final Set<String> targetSet, final Runnable onSaved) {
    showLoadingDialog(loadingTitle, loadingMsg, new Runnable() {
        public void run() {
            try {
                if (isGroup) {
                    if (sCachedGroupList == null) sCachedGroupList = getGroupList();
                } else {
                    if (sCachedFriendList == null) sCachedFriendList = getFriendList();
                }

                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    public void run() {
                        List<String> names = new ArrayList<>();
                        List<String> ids = new ArrayList<>();

                        List list = isGroup ? sCachedGroupList : sCachedFriendList;
                        if (list != null) {
                            for (int i = 0; i < list.size(); i++) {
                                if (isGroup) {
                                    GroupInfo g = (GroupInfo) list.get(i);
                                    String name = !TextUtils.isEmpty(g.getName()) ? g.getName() : "未命名群聊";
                                    String roomId = g.getRoomId();
                                    if (!TextUtils.isEmpty(roomId)) {
                                        names.add("🏠 " + name + " - " + roomId);
                                        ids.add(roomId);
                                    }
                                } else {
                                    FriendInfo f = (FriendInfo) list.get(i);
                                    String nickname = TextUtils.isEmpty(f.getNickname()) ? "未知昵称" : f.getNickname();
                                    String remark = f.getRemark();
                                    String displayName = !TextUtils.isEmpty(remark) ? nickname + " (" + remark + ")" : nickname;
                                    String wxid = f.getWxid();
                                    if (!TextUtils.isEmpty(wxid) && !wxid.endsWith("@chatroom") && !wxid.equals("filehelper")) {
                                        names.add("👤 " + displayName + " - " + wxid);
                                        ids.add(wxid);
                                    }
                                }
                            }
                        }

                        showMultiSelectDialog(dialogTitle, names, ids, targetSet, isGroup ? "搜索群名/wxid..." : "搜索昵称/备注/wxid...", new Runnable() {
                            public void run() {
                                saveConfig();
                                onSaved.run();
                            }
                        }, null);
                    }
                });
            } catch (Exception e) {
                log("加载联系人列表失败: " + e.getMessage());
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    public void run() {
                        toast("无法获取列表");
                    }
                });
            }
        }
    });
}

private void refreshContactDisplay(Set<String> contactSet, TextView countTv, String countPrefix, ListView listView, ArrayAdapter<String> adapter, List<String> contactList, List<String> displayList, Button clearBtn, String logLabel) {
    try {
        if (countTv != null) {
            countTv.setText(countPrefix + contactSet.size() + " 个联系人");
        }
        if (listView != null && adapter != null && contactList != null && displayList != null) {
            contactList.clear();
            contactList.addAll(contactSet);
            displayList.clear();
            for (String contactId : contactList) {
                displayList.add(buildContactDisplayName(contactId));
            }
            adapter.notifyDataSetChanged();

            int itemHeight = dpToPx(48);
            int listHeight = Math.max(Math.min(contactList.size() * itemHeight, dpToPx(200)), dpToPx(48));
            LinearLayout.LayoutParams listParams = (LinearLayout.LayoutParams) listView.getLayoutParams();
            if (listParams != null) {
                listParams.height = listHeight;
                listView.setLayoutParams(listParams);
            }
        }
        if (clearBtn != null) {
            clearBtn.setVisibility(contactSet.size() > 1 ? View.VISIBLE : View.GONE);
        }
    } catch (Exception e) {
        log("刷新" + logLabel + "显示失败: " + e.getMessage());
    }
}

/**
 * 刷新仅生效列表显示
 */
private void refreshIncludeDisplay() {
    refreshContactDisplay(includeContactSet, includeCountTv, "已添加 ", includeListView, includeAdapter, includeContactList, includeDisplayList, includeClearBtn, "仅生效列表");
}


/**
 * 刷新关键词数量显示
 */
private void refreshKeywordCount() {
    if (keywordCountTv != null) {
        keywordCountTv.setText("当前关键词: " + keywordMap.size() + " 个");
    }
}

/**
 * 免打扰时间设置对话框
 */
private void showQuietTimeDialog() {
    String[] hours = new String[24];
    for (int i = 0; i < 24; i++) {
        hours[i] = String.format("%02d:00", i);
    }

    final String[] selectedStart = {String.format("%02d:00", quietStartHour)};
    final String[] selectedEnd = {String.format("%02d:00", quietEndHour)};

    AlertDialog.Builder builder = new AlertDialog.Builder(getTopActivity());
    builder.setTitle("⏰ 设置免打扰时间");
    builder.setMessage("开始时间: " + selectedStart[0] + "\n结束时间: " + selectedEnd[0]);
    builder.setPositiveButton("开始时间", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
            showHourPicker("选择开始时间", quietStartHour, new HourPickerCallback() {
                public void onHourSelected(int hour) {
                    quietStartHour = hour;
                    selectedStart[0] = String.format("%02d:00", hour);
                    saveConfig();
                    showQuietTimeDialog();
                }
            });
        }
    });
    builder.setNeutralButton("结束时间", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
            showHourPicker("选择结束时间", quietEndHour, new HourPickerCallback() {
                public void onHourSelected(int hour) {
                    quietEndHour = hour;
                    selectedEnd[0] = String.format("%02d:00", hour);
                    saveConfig();
                    showQuietTimeDialog();
                }
            });
        }
    });
    builder.setNegativeButton("关闭", null);

    AlertDialog dialog = builder.create();
    dialog.setOnShowListener(new DialogInterface.OnShowListener() {
        public void onShow(DialogInterface d) {
            setupUnifiedDialog((AlertDialog)d);
        }
    });
    dialog.show();
}

/**
 * 自定义通知文字设置对话框
 */
private void showCustomTextDialog() {
    ScrollView scrollView = new ScrollView(getTopActivity());
    LinearLayout root = new LinearLayout(getTopActivity());
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(32, 32, 32, 32);
    scrollView.addView(root);

    TextView tip = new TextView(getTopActivity());
    tip.setText("支持变量替换：关键词、发送者、发送者ID、内容、消息类型（点击下方中文按钮插入）\n留空则使用默认文字");
    tip.setTextSize(12);
    tip.setTextColor(Color.parseColor("#666666"));
    tip.setPadding(0, 0, 0, 16);
    root.addView(tip);

    root.addView(createSectionTitle("🔑 关键词自定义"));
    final EditText keywordTitleInput = addCustomTextField(root, "通知标题模板:", "例如: 监控提醒", customKeywordNotifyTitle);
    final EditText keywordContentInput = addCustomTextField(root, "通知内容模板:", "例如: [%sender%] %content%", customKeywordNotifyContent);
    final EditText keywordToastInput = addCustomTextField(root, "Toast文字模板:", "例如: 命中关键词: %keyword%", customKeywordToastText);

    root.addView(createSectionTitle("@我自定义"));
    final EditText atMeTitleInput = addCustomTextField(root, "通知标题模板:", "例如: @我提醒", customAtMeNotifyTitle);
    final EditText atMeContentInput = addCustomTextField(root, "通知内容模板:", "例如: [%sender%] %content%", customAtMeNotifyContent);
    final EditText atMeToastInput = addCustomTextField(root, "Toast文字模板:", "例如: @我通知: %keyword%", customAtMeToastText);

    root.addView(createSectionTitle("@所有人/群公告自定义"));
    final EditText atAllTitleInput = addCustomTextField(root, "通知标题模板:", "例如: @所有人提醒", customAtAllNotifyTitle);
    final EditText atAllContentInput = addCustomTextField(root, "通知内容模板:", "例如: [%sender%] %content%", customAtAllNotifyContent);
    final EditText atAllToastInput = addCustomTextField(root, "Toast文字模板:", "例如: @所有人通知: %keyword%", customAtAllToastText);

    LinearLayout btnRow = new LinearLayout(getTopActivity());
    btnRow.setOrientation(LinearLayout.HORIZONTAL);
    btnRow.setGravity(Gravity.CENTER);

    Button resetBtn = new Button(getTopActivity());
    resetBtn.setText("清空全部");
    styleUtilityButton(resetBtn);
    LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
    );
    btnParams.setMargins(8, 24, 8, 8);
    resetBtn.setLayoutParams(btnParams);
    resetBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            clearCustomTextFields(
                keywordTitleInput, keywordContentInput, keywordToastInput,
                atMeTitleInput, atMeContentInput, atMeToastInput,
                atAllTitleInput, atAllContentInput, atAllToastInput
            );
            toast("已清空自定义文字");
        }
    });
    btnRow.addView(resetBtn);

    root.addView(btnRow);

    AlertDialog dialog = new AlertDialog.Builder(getTopActivity())
        .setTitle("自定义通知/Toast文字")
        .setView(scrollView)
        .setPositiveButton("保存", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                customKeywordNotifyTitle = keywordTitleInput.getText().toString();
                customKeywordNotifyContent = keywordContentInput.getText().toString();
                customKeywordToastText = keywordToastInput.getText().toString();
                customAtMeNotifyTitle = atMeTitleInput.getText().toString();
                customAtMeNotifyContent = atMeContentInput.getText().toString();
                customAtMeToastText = atMeToastInput.getText().toString();
                customAtAllNotifyTitle = atAllTitleInput.getText().toString();
                customAtAllNotifyContent = atAllContentInput.getText().toString();
                customAtAllToastText = atAllToastInput.getText().toString();
                saveConfig();
                toast("自定义文字已保存");
            }
        })
        .setNegativeButton("取消", null)
        .create();

    dialog.setOnShowListener(new DialogInterface.OnShowListener() {
        public void onShow(DialogInterface d) {
            setupUnifiedDialog((AlertDialog)d);
        }
    });
    dialog.show();
}

private EditText addCustomTextField(LinearLayout root, String label, String hint, String initialText) {
    root.addView(createTextView(getTopActivity(), label, 14, 8));
    final EditText input = createStyledEditText(hint, initialText);
    root.addView(input);
    root.addView(createVariableButtons(new String[]{"%keyword%", "%sender%", "%wxid%", "%content%", "%type%"}, input));
    return input;
}

private void clearCustomTextFields(EditText... fields) {
    if (fields == null) return;
    for (int i = 0; i < fields.length; i++) {
        if (fields[i] != null) fields[i].setText("");
    }
}

/**
 * 创建变量标签按钮行
 */
private LinearLayout createVariableButtons(String[] variables, final EditText targetEditText) {
    LinearLayout row = new LinearLayout(getTopActivity());
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setWeightSum(variables.length);

    for (int i = 0; i < variables.length; i++) {
        Button btn = createVarButton(variables[i], getVariableCnName(variables[i]), targetEditText);
        row.addView(btn);
    }

    return row;
}

/**
 * 创建单个变量按钮（每个按钮有独立作用域）
 */
private Button createVarButton(final String variable, String variableCnName, final EditText targetEditText) {
    Button btn = new Button(getTopActivity());
    btn.setText(variableCnName);
    btn.setTextSize(11);
    btn.setAllCaps(false);

    // 样式
    GradientDrawable shape = new GradientDrawable();
    shape.setCornerRadius(12);
    shape.setColor(Color.parseColor("#E3F2FD"));
    shape.setStroke(1, Color.parseColor("#90CAF9"));
    btn.setBackground(shape);
    btn.setTextColor(Color.parseColor("#1976D2"));

    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
    );
    params.setMargins(4, 4, 4, 4);
    btn.setLayoutParams(params);

    btn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            int cursor = targetEditText.getSelectionStart();
            String text = targetEditText.getText().toString();
            String newText = text.substring(0, cursor) + variable + text.substring(cursor);
            targetEditText.setText(newText);
            targetEditText.setSelection(cursor + variable.length());
        }
    });

    return btn;
}

private String getVariableCnName(String variable) {
    if ("%keyword%".equals(variable)) return "关键词";
    if ("%sender%".equals(variable)) return "发送者";
    if ("%wxid%".equals(variable)) return "发送者ID";
    if ("%content%".equals(variable)) return "内容";
    if ("%type%".equals(variable)) return "消息类型";
    return variable;
}

interface HourPickerCallback {
    void onHourSelected(int hour);
}

private void showHourPicker(String title, int currentHour, final HourPickerCallback callback) {
    String[] hours = new String[24];
    for (int i = 0; i < 24; i++) {
        hours[i] = String.format("%02d:00", i);
    }

    AlertDialog.Builder builder = new AlertDialog.Builder(getTopActivity());
    builder.setTitle(title);
    builder.setSingleChoiceItems(hours, currentHour, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
            callback.onHourSelected(which);
            dialog.dismiss();
        }
    });
    builder.setNegativeButton("取消", null);
    builder.show();
}

// ==========================================
// ========== 👤 联系人和群聊辅助功能 ==========
// ==========================================

/**
 * 获取好友显示名称（优先备注）
 */
private String getFriendDisplayName(String friendWxid) {
    try {
        if (sCachedFriendList == null) sCachedFriendList = getFriendList();
        if (sCachedFriendList != null) {
            for (int i = 0; i < sCachedFriendList.size(); i++) {
                FriendInfo friendInfo = (FriendInfo) sCachedFriendList.get(i);
                if (friendWxid.equals(friendInfo.getWxid())) {
                    String remark = friendInfo.getRemark();
                    if (!TextUtils.isEmpty(remark)) return remark;
                    String nickname = friendInfo.getNickname();
                    return TextUtils.isEmpty(nickname) ? friendWxid : nickname;
                }
            }
        }
    } catch (Exception e) {
    }
    return getFriendName(friendWxid);
}

/**
 * 获取群聊名称
 */
private String getGroupName(String groupWxid) {
    try {
        if (sCachedGroupList == null) sCachedGroupList = getGroupList();
        if (sCachedGroupList != null) {
            for (int i = 0; i < sCachedGroupList.size(); i++) {
                GroupInfo groupInfo = (GroupInfo) sCachedGroupList.get(i);
                if (groupWxid.equals(groupInfo.getRoomId())) return groupInfo.getName();
            }
        }
    } catch (Exception e) {
    }
    return "未知群聊";
}

private String buildContactDisplayName(String contactId) {
    if (TextUtils.isEmpty(contactId)) return "";
    if (contactId.endsWith("@chatroom")) {
        return "💬 群聊: " + getGroupName(contactId);
    }
    return "👤 " + getFriendDisplayName(contactId);
}

/**
 * 刷新排除列表显示（供添加排除后调用）
 */
private void refreshExcludeDisplay() {
    refreshContactDisplay(excludeContactSet, excludeCountTv, "已排除 ", excludeListView, excludeAdapter, excludeContactList, excludeDisplayList, excludeClearBtn, "排除列表");
}

// ==========================================
// ========== 🎨 UI 样式方法 ==========
// ==========================================

private LinearLayout createCardLayout() {
    LinearLayout layout = new LinearLayout(getTopActivity());
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setPadding(32, 32, 32, 32);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    );
    params.setMargins(0, 16, 0, 16);
    layout.setLayoutParams(params);
    layout.setBackground(createGradientDrawable("#FFFFFF", 32));
    try { layout.setElevation(8); } catch (Exception e) {}
    return layout;
}

private GradientDrawable createGradientDrawable(String colorStr, int radius) {
    GradientDrawable shape = new GradientDrawable();
    shape.setCornerRadius(radius);
    shape.setColor(Color.parseColor(colorStr));
    return shape;
}

// 开关回调接口
interface ToggleCallback {
    void onToggle(boolean checked);
}

/**
 * 创建iOS风格圆形开关行
 * 左侧文字，右侧圆形开关
 * 关闭：灰色背景+白色圆点
 * 开启：绿色背景+白色圆点
 */
private LinearLayout createSwitchRow(String text, boolean initialChecked, int textSize, final ToggleCallback callback) {
    final boolean[] isChecked = {initialChecked};

    LinearLayout row = new LinearLayout(getTopActivity());
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(android.view.Gravity.CENTER_VERTICAL);
    row.setPadding(0, 12, 0, 12);

    // 文字标签
    TextView label = new TextView(getTopActivity());
    label.setText(text);
    label.setTextSize(textSize);
    label.setTextColor(Color.parseColor("#333333"));
    LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
    );
    label.setLayoutParams(labelParams);
    row.addView(label);

    // 开关容器
    android.widget.FrameLayout switchContainer = new android.widget.FrameLayout(getTopActivity());
    LinearLayout.LayoutParams switchParams = new LinearLayout.LayoutParams(
        dpToPx(52), dpToPx(30)
    );
    switchContainer.setLayoutParams(switchParams);

    // 开关背景
    final GradientDrawable switchBg = new GradientDrawable();
    switchBg.setShape(GradientDrawable.OVAL);
    switchBg.setCornerRadius(dpToPx(15));
    switchBg.setColor(Color.parseColor(isChecked[0] ? "#4CAF50" : "#E0E0E0"));
    switchContainer.setBackground(switchBg);

    // 开关圆点
    final android.widget.ImageView thumb = new android.widget.ImageView(getTopActivity());
    int thumbSize = dpToPx(26);
    android.widget.FrameLayout.LayoutParams thumbParams = new android.widget.FrameLayout.LayoutParams(
        thumbSize, thumbSize
    );
    thumbParams.gravity = android.view.Gravity.CENTER_VERTICAL;
    // 根据状态设置圆点位置
    int margin = isChecked[0] ? dpToPx(22) : dpToPx(2);
    thumbParams.setMargins(margin, 0, 0, 0);
    thumb.setLayoutParams(thumbParams);

    GradientDrawable thumbBg = new GradientDrawable();
    thumbBg.setShape(GradientDrawable.OVAL);
    thumbBg.setColor(Color.WHITE);
    thumbBg.setCornerRadius(dpToPx(13));
    thumb.setImageDrawable(thumbBg);
    thumb.setElevation(2);

    switchContainer.addView(thumb);
    row.addView(switchContainer);

    // 点击切换状态
    row.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            isChecked[0] = !isChecked[0];

            // 动画效果
            int targetMargin = isChecked[0] ? dpToPx(22) : dpToPx(2);
            switchBg.setColor(Color.parseColor(isChecked[0] ? "#4CAF50" : "#E0E0E0"));

            // 更新圆点位置
            android.widget.FrameLayout.LayoutParams tp = (android.widget.FrameLayout.LayoutParams) thumb.getLayoutParams();
            tp.setMargins(targetMargin, 0, 0, 0);
            thumb.setLayoutParams(tp);

            callback.onToggle(isChecked[0]);
        }
    });

    return row;
}

private TextView createSectionTitle(String text) {
    TextView textView = new TextView(getTopActivity());
    textView.setText(text);
    textView.setTextSize(16);
    textView.setTextColor(Color.parseColor("#333333"));
    try { textView.getPaint().setFakeBoldText(true); } catch (Exception e) {}
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    );
    params.setMargins(0, 0, 0, 24);
    textView.setLayoutParams(params);
    return textView;
}

private TextView createPromptText(String text) {
    TextView tv = new TextView(getTopActivity());
    tv.setText(text);
    tv.setTextSize(12);
    tv.setTextColor(Color.parseColor("#666666"));
    tv.setPadding(0, 0, 0, 16);
    return tv;
}

private void styleUtilityButton(Button button) {
    button.setTextColor(Color.parseColor("#4A90E2"));
    GradientDrawable shape = new GradientDrawable();
    shape.setCornerRadius(20);
    shape.setStroke(3, Color.parseColor("#BBD7E6"));
    shape.setColor(Color.TRANSPARENT);
    button.setBackground(shape);
    button.setAllCaps(false);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    );
    params.setMargins(0, 16, 0, 8);
    button.setLayoutParams(params);
}

private void styleMediaSelectionButton(Button button) {
    button.setTextColor(Color.parseColor("#3B82F6"));
    GradientDrawable shape = new GradientDrawable();
    shape.setCornerRadius(20);
    shape.setColor(Color.parseColor("#EFF6FF"));
    shape.setStroke(2, Color.parseColor("#BFDBFE"));
    button.setBackground(shape);
    button.setAllCaps(false);
    button.setPadding(20, 12, 20, 12);
}

private AlertDialog buildCommonAlertDialog(Context context, String title, View view, String positiveBtnText, DialogInterface.OnClickListener positiveListener, String negativeBtnText, DialogInterface.OnClickListener negativeListener, String neutralBtnText, DialogInterface.OnClickListener neutralListener) {
    AlertDialog.Builder builder = new AlertDialog.Builder(context);
    builder.setTitle(title);
    builder.setView(view);
    if (positiveBtnText != null) builder.setPositiveButton(positiveBtnText, positiveListener);
    if (negativeBtnText != null) builder.setNegativeButton(negativeBtnText, negativeListener);
    if (neutralBtnText != null) builder.setNeutralButton(neutralBtnText, neutralListener);
    final AlertDialog dialog = builder.create();
    dialog.setOnShowListener(new DialogInterface.OnShowListener() {
        public void onShow(DialogInterface d) {
            setupUnifiedDialog(dialog);
        }
    });
    return dialog;
}

private void setupUnifiedDialog(AlertDialog dialog) {
    GradientDrawable dialogBg = new GradientDrawable();
    dialogBg.setCornerRadius(48);
    dialogBg.setColor(Color.parseColor("#FAFBF9"));
    dialog.getWindow().setBackgroundDrawable(dialogBg);
    styleDialogButtons(dialog);
}

private void styleDialogButtons(AlertDialog dialog) {
    Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
    if (positiveButton != null) {
        positiveButton.setTextColor(Color.WHITE);
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(20);
        shape.setColor(Color.parseColor("#70A1B8"));
        positiveButton.setBackground(shape);
        positiveButton.setAllCaps(false);
    }
    Button negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
    if (negativeButton != null) {
        negativeButton.setTextColor(Color.parseColor("#333333"));
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(20);
        shape.setColor(Color.parseColor("#F1F3F5"));
        negativeButton.setBackground(shape);
        negativeButton.setAllCaps(false);
    }
}

private int dpToPx(int dp) {
    return (int) (dp * getTopActivity().getResources().getDisplayMetrics().density);
}

private void setupListViewTouchForScroll(ListView listView) {
    listView.setOnTouchListener(new View.OnTouchListener() {
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    break;
            }
            return false;
        }
    });
}

private String formatTimeWithSeconds(long ts) {
    if (ts <= 0) return "未设置";
    return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(ts));
}

// ==========================================
// ========== 💾 配置存储方法 ==========
// ==========================================

private void putString(String setName, String itemName, String value) {
    String existingData = getString(setName, "{}");
    try {
        JSONObject json = new JSONObject(existingData);
        json.put(itemName, value);
        putString(setName, json.toString());
    } catch (Exception e) {
        try {
            JSONObject json = new JSONObject();
            json.put(itemName, value);
            putString(setName, json.toString());
        } catch (Exception ignored) {}
    }
}

private String getString(String setName, String itemName, String defaultValue) {
    String data = getString(setName, "{}");
    try {
        JSONObject json = new JSONObject(data);
        if (json.has(itemName)) return json.optString(itemName, defaultValue);
    } catch (Exception e) {
        // ignore
    }
    return defaultValue;
}

private long getLong(String setName, String itemName, long defaultValue) {
    try {
        String val = getString(setName, itemName, String.valueOf(defaultValue));
        return Long.parseLong(val);
    } catch(Exception e) {
        return defaultValue;
    }
}

private void putLong(String setName, String itemName, long value) {
    putString(setName, itemName, String.valueOf(value));
}

/**
 * 通用多选列表对话框 (支持搜索、全选/反选)
 * 移植自定时群发脚本
 */
private void showMultiSelectDialog(String title, List allItems, List idList, Set selectedIds, String searchHint, final Runnable onConfirm, final Runnable updateList) {
    try {
        final Set tempSelected = new HashSet(selectedIds);
        ScrollView scrollView = new ScrollView(getTopActivity());
        LinearLayout mainLayout = new LinearLayout(getTopActivity());
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(24, 24, 24, 24);
        mainLayout.setBackgroundColor(Color.parseColor("#FAFBF9"));
        scrollView.addView(mainLayout);

        // 搜索框
        final EditText searchEditText = createStyledEditText(searchHint, "");
        searchEditText.setSingleLine(true);
        mainLayout.addView(searchEditText);

        // 列表
        final ListView listView = new ListView(getTopActivity());
        setupListViewTouchForScroll(listView);
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(300));
        listView.setLayoutParams(listParams);
        mainLayout.addView(listView);

        // 当前过滤后的数据
        final List currentFilteredIds = new ArrayList();
        final List currentFilteredNames = new ArrayList();

        // 更新列表的Runnable
        final Runnable updateListRunnable = new Runnable() {
            public void run() {
                String searchText = searchEditText.getText().toString().toLowerCase();
                currentFilteredIds.clear();
                currentFilteredNames.clear();
                for (int i = 0; i < allItems.size(); i++) {
                    String id = (String) idList.get(i);
                    String name = (String) allItems.get(i);
                    // 支持按名称和wxid搜索
                    if (searchText.isEmpty() || name.toLowerCase().contains(searchText) || id.toLowerCase().contains(searchText)) {
                        currentFilteredIds.add(id);
                        currentFilteredNames.add(name);
                    }
                }
                ArrayAdapter adapter = new ArrayAdapter(getTopActivity(), android.R.layout.simple_list_item_multiple_choice, currentFilteredNames);
                listView.setAdapter(adapter);
                listView.clearChoices();
                for (int j = 0; j < currentFilteredIds.size(); j++) {
                    listView.setItemChecked(j, tempSelected.contains(currentFilteredIds.get(j)));
                }
                if (updateList != null) updateList.run();
            }
        };

        // 点击列表项切换选择状态
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
                String selected = (String) currentFilteredIds.get(pos);
                if (listView.isItemChecked(pos)) tempSelected.add(selected);
                else tempSelected.remove(selected);
                if (updateList != null) updateList.run();
            }
        });

        // 搜索框文字变化时更新列表
        searchEditText.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            public void afterTextChanged(Editable s) {
                updateListRunnable.run();
            }
        });

        // 全选/反选按钮点击事件
        final DialogInterface.OnClickListener fullSelectListener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                boolean allSelected = true;
                for (Object id : currentFilteredIds) {
                    if (!tempSelected.contains(id)) {
                        allSelected = false;
                        break;
                    }
                }

                if (allSelected) {
                    // 当前全部选中，则取消全选
                    for (Object id : currentFilteredIds) {
                        tempSelected.remove(id);
                    }
                } else {
                    // 当前未全部选中，则全选
                    for (Object id : currentFilteredIds) {
                        tempSelected.add(id);
                    }
                }
                updateListRunnable.run();
            }
        };

        // 创建对话框
        final AlertDialog dialog = buildCommonAlertDialog(getTopActivity(), title, scrollView, "✅ 确定", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                selectedIds.clear();
                selectedIds.addAll(tempSelected);
                if (onConfirm != null) onConfirm.run();
                dialog.dismiss();
            }
        }, "❌ 取消", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        }, "全选/反选", fullSelectListener);

        // 设置中性按钮的点击事件
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            public void onShow(DialogInterface d) {
                setupUnifiedDialog((AlertDialog)d);
                Button neutral = ((AlertDialog)d).getButton(AlertDialog.BUTTON_NEUTRAL);
                neutral.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        fullSelectListener.onClick(dialog, AlertDialog.BUTTON_NEUTRAL);
                    }
                });
            }
        });

        dialog.show();
        updateListRunnable.run();
    } catch (Exception e) {
        toast("弹窗失败: " + e.getMessage());
        e.printStackTrace();
    }
}

/**
 * 使用安卓原生 Toast，避免运行环境封装 Toast 显示插件名
 */
private void toast(final String msg) {
    if (msg == null || msg.length() == 0) return;
    new Handler(Looper.getMainLooper()).post(new Runnable() {
        public void run() {
            try {
                Object ctxObj = getToastContext();
                if (ctxObj == null) return;
                Toast.makeText((Context) ctxObj, msg, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                log("原生Toast显示失败: " + e.getMessage());
            }
        }
    });
}

/**
 * 获取可用于Toast的Context
 * 前台优先使用Activity，后台回退到Application
 */
private Context getToastContext() {
    try {
        Object activity = getTopActivity();
        if (activity != null) {
            return ((Context) activity).getApplicationContext();
        }
    } catch (Exception e) {
    }

    try {
        Class activityThreadClass = Class.forName("android.app.ActivityThread");
        Method currentApplication = activityThreadClass.getDeclaredMethod("currentApplication", new Class[0]);
        Object application = currentApplication.invoke(null, new Object[0]);
        if (application != null) {
            return ((Context) application).getApplicationContext();
        }
    } catch (Exception e) {
    }

    return null;
}

/**
 * 创建带搜索框的样式化EditText
 */
private EditText createStyledEditText(String hint, String initialText) {
    EditText editText = new EditText(getTopActivity());
    editText.setHint(hint);
    editText.setText(initialText);
    editText.setPadding(32, 28, 32, 28);
    editText.setTextSize(14);
    editText.setTextColor(Color.parseColor("#555555"));
    editText.setHintTextColor(Color.parseColor("#999999"));
    GradientDrawable shape = new GradientDrawable();
    shape.setCornerRadius(24);
    shape.setColor(Color.parseColor("#F8F9FA"));
    shape.setStroke(2, Color.parseColor("#E6E9EE"));
    editText.setBackground(shape);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    );
    params.setMargins(0, 8, 0, 16);
    editText.setLayoutParams(params);
    return editText;
}

/**
 * 显示加载对话框
 * 移植自定时群发脚本
 */
private void showLoadingDialog(String title, String message, final Runnable dataLoadTask) {
    LinearLayout initialLayout = new LinearLayout(getTopActivity());
    initialLayout.setOrientation(LinearLayout.HORIZONTAL);
    initialLayout.setPadding(50, 50, 50, 50);
    initialLayout.setGravity(Gravity.CENTER_VERTICAL);
    ProgressBar progressBar = new ProgressBar(getTopActivity());
    initialLayout.addView(progressBar);
    TextView loadingText = new TextView(getTopActivity());
    loadingText.setText(message);
    loadingText.setPadding(20, 0, 0, 0);
    initialLayout.addView(loadingText);
    final AlertDialog loadingDialog = buildCommonAlertDialog(getTopActivity(), title, initialLayout, null, null, "❌ 取消", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface d, int w) {
            d.dismiss();
        }
    }, null, null);
    loadingDialog.setCancelable(false);
    loadingDialog.show();
    new Thread(new Runnable() {
        public void run() {
            try {
                dataLoadTask.run();
            } finally {
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    public void run() {
                        loadingDialog.dismiss();
                    }
                });
            }
        }
    }).start();
}

/**
 * 创建文本标签
 */
private TextView createTextView(Context context, String text, int textSize, int paddingBottom) {
    TextView textView = new TextView(context);
    textView.setText(text);
    if (textSize > 0) textView.setTextSize(textSize);
    textView.setPadding(0, 0, 0, paddingBottom);
    return textView;
}
