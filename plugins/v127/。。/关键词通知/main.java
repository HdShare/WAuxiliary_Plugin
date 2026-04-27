import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import android.widget.CheckBox;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Iterator;
import java.util.UUID;
import me.hd.wauxv.data.bean.info.FriendInfo;
import me.hd.wauxv.data.bean.info.GroupInfo;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.ListView;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.text.TextWatcher;
import android.text.Editable;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import android.view.Gravity;
import android.widget.TextView;
import android.widget.ScrollView;
import java.lang.reflect.Method;
import java.util.regex.Pattern;
import android.widget.RadioGroup;
import android.widget.RadioButton;
import java.util.Arrays;
import android.text.InputType;
import android.content.Context;
import java.util.Random;
import java.io.File;
import java.io.FilenameFilter;
import java.util.Calendar;
import android.widget.TimePicker;
import android.widget.DatePicker;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Objects;
import android.view.MotionEvent;
import java.util.Collections;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.AbsoluteSizeSpan;
import android.os.Build;

// 引入 FastJSON 用于数据存储
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.TypeReference;

// ==========================================
// ========== 🔔 微信关键词通知助手 ==========
// ==========================================

// 全局配置
Map<String, Boolean> keywordMap = new HashMap<>(); // 关键词集合，key=关键词，value=是否全字匹配
Set<String> excludeContactSet = new HashSet<>(); // 排除联系人ID集合
Set<String> includeContactSet = new HashSet<>(); // 仅生效联系人ID集合
boolean filterMode = false; // false=排除模式, true=仅生效模式
boolean enabled = true; // 总开关
boolean notifyEnabled = true; // 通知开关
boolean toastEnabled = true; // Toast开关
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
                keywordsObj = JSON.parseObject(keywordsJson);
            } catch (Exception e) {
                log("parseObject 失败: " + e.getMessage() + "，尝试兼容旧版数组格式");
            }

            if (keywordsObj != null) {
                for (String keyword : keywordsObj.keySet()) {
                    Boolean isWholeWord = keywordsObj.getBoolean(keyword);
                    keywordMap.put(keyword, isWholeWord != null ? isWholeWord : false);
                }
            } else {
                // 兼容旧版格式（纯数组）
                try {
                    JSONArray keywordsArray = JSON.parseArray(keywordsJson);
                    if (keywordsArray != null && !keywordsArray.isEmpty()) {
                        keywordMap.clear();
                        for (int i = 0; i < keywordsArray.size(); i++) {
                            keywordMap.put(keywordsArray.getString(i), false);
                        }
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
                JSONArray excludesArray = JSON.parseArray(excludesJson);
                if (excludesArray != null) {
                    excludeContactSet.clear();
                    for (int i = 0; i < excludesArray.size(); i++) {
                        excludeContactSet.add(excludesArray.getString(i));
                    }
                }
            } catch (Exception e) {
                log("解析排除联系人配置出错，使用默认配置: " + e.getMessage());
            }
        }

        
        // 加载仅生效联系人列表
        String includesJson = getString(CONFIG_KEY, KEY_INCLUDE_CONTACTS, "[]");
        if (includesJson != null && !includesJson.isEmpty()) {
            try {
                JSONArray includesArray = JSON.parseArray(includesJson);
                if (includesArray != null) {
                    includeContactSet.clear();
                    for (int i = 0; i < includesArray.size(); i++) {
                        includeContactSet.add(includesArray.getString(i));
                    }
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
            excludesArray.add(contactId);
        }
        putString(CONFIG_KEY, KEY_EXCLUDE_CONTACTS, excludesArray.toString());
        // 保存仅生效联系人列表
        JSONArray includesArray = new JSONArray();
        for (String contactId : includeContactSet) {
            includesArray.add(contactId);
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
        // 获取消息内容
        String content = "";
        try {
            Method getContentMethod = msgInfoBean.getClass().getMethod("getContent");
            content = (String) getContentMethod.invoke(msgInfoBean);
        } catch (Exception e) {
            return;
        }

        if (TextUtils.isEmpty(content)) return;

        // 检查是否是群聊
        boolean isGroupChat = false;
        try {
            Method isGroupChatMethod = msgInfoBean.getClass().getMethod("isGroupChat");
            isGroupChat = (Boolean) isGroupChatMethod.invoke(msgInfoBean);
        } catch (Exception e) {}

        // 检查是否是自己发的消息
        boolean isSend = false;
        try {
            Method isSendMethod = msgInfoBean.getClass().getMethod("isSend");
            isSend = (Boolean) isSendMethod.invoke(msgInfoBean);
        } catch (Exception e) {}

        if (isSend) return; // 忽略自己发的消息

        // 获取发送者的wxid
        String senderWxid = "";
        try {
            Method getTalkerMethod = msgInfoBean.getClass().getMethod("getTalker");
            senderWxid = (String) getTalkerMethod.invoke(msgInfoBean);
        } catch (Exception e) {}

        // 根据过滤模式判断是否处理该联系人的消息
        if (!TextUtils.isEmpty(senderWxid)) {
            if (filterMode) {
                // 仅生效模式：只处理白名单中的联系人
                if (!includeContactSet.contains(senderWxid)) {
                    return;
                }
            } else {
                // 排除模式：排除黑名单中的联系人
                if (excludeContactSet.contains(senderWxid)) {
                    return;
                }
            }
        }

        // 匹配关键词（如果有关键词）
        if (!keywordMap.isEmpty()) {
            String matchedKeyword = null;
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

            if (matchedKeyword != null) {
                // 获取发送者信息
                String senderInfo = getSenderInfo(msgInfoBean, isGroupChat);

                // 触发通知
                triggerNotification(matchedKeyword, content, senderInfo, senderWxid, isGroupChat);

                // 更新最后匹配时间
                lastMatchTime = System.currentTimeMillis();
                lastMatchedKeyword = matchedKeyword;
                saveConfig();
            }
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

                // 触发通知
                triggerNotification(atType, content, senderInfo, senderWxid, isGroupChat);

                // 更新最后匹配时间
                lastMatchTime = System.currentTimeMillis();
                lastMatchedKeyword = atType;
                saveConfig();
            }
        }
    } catch (Exception e) {
        log("处理消息失败: " + e.getMessage());
    }
}

/**
 * 获取发送者信息
 */
private String getSenderInfo(Object msgInfoBean, boolean isGroupChat) {
    try {
        String talker = "";
        String sendTalker = "";
        String displayName = "";

        // 获取talker
        try {
            Method getTalkerMethod = msgInfoBean.getClass().getMethod("getTalker");
            talker = (String) getTalkerMethod.invoke(msgInfoBean);
        } catch (Exception e) {}

        // 获取发送者ID
        try {
            Method getSendTalkerMethod = msgInfoBean.getClass().getMethod("getSendTalker");
            sendTalker = (String) getSendTalkerMethod.invoke(msgInfoBean);
        } catch (Exception e) {}

        // 优先从反射获取displayName
        try {
            Method getDisplayNameMethod = msgInfoBean.getClass().getMethod("getDisplayName");
            displayName = (String) getDisplayNameMethod.invoke(msgInfoBean);
        } catch (Exception e) {}

        // 如果是群聊，优先尝试获取群成员名称
        if (isGroupChat && !TextUtils.isEmpty(sendTalker)) {
            // 获取群名称
            String groupName = getGroupName(talker);

            // 获取群成员名称
            String memberName = "";
            if (sendTalker.endsWith("@chatroom")) {
                // 发送者是群聊本身
                memberName = groupName;
            } else {
                memberName = getGroupMemberDisplayName(talker, sendTalker);
                if (TextUtils.isEmpty(memberName)) {
                    memberName = getFriendDisplayName(sendTalker);
                }
            }

            return groupName + " | " + memberName;
        } else {
            // 私聊
            if (!TextUtils.isEmpty(displayName)) {
                return displayName;
            }
            return getFriendDisplayName(talker);
        }
    } catch (Exception e) {
        log("获取发送者信息失败: " + e.getMessage());
        return "未知来源";
    }
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
private void triggerNotification(String keyword, String content, String senderInfo, String senderWxid, boolean isGroupChat) {
    // 检查免打扰模式
    if (quietHoursEnabled) {
        int currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        boolean inQuietTime = false;
        if (quietStartHour >= quietEndHour) {
            inQuietTime = currentHour >= quietStartHour || currentHour < quietEndHour;
        } else {
            inQuietTime = currentHour >= quietStartHour && currentHour < quietEndHour;
        }
        if (inQuietTime) {
            return; // 免打扰时间段，不发送通知
        }
    }

    final String finalSenderInfo = senderInfo;
    final String finalContent = content;
    final String finalKeyword = keyword;
    final String finalWxid = senderWxid;
    final boolean finalIsGroupChat = isGroupChat;
    final boolean isAtMe = finalKeyword.equals("@我");
    final boolean isAtAll = finalKeyword.equals("@所有人");

    // 发送系统通知
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

                    // 使用自定义通知文字
                    if (!TextUtils.isEmpty(notifyTitleTemplate)) {
                        // 支持变量替换: %keyword% 关键词, %sender% 发送者, %content% 内容, %type% 类型, %wxid% 发送者ID
                        title = notifyTitleTemplate
                            .replace("%keyword%", finalKeyword)
                            .replace("%sender%", finalSenderInfo)
                            .replace("%wxid%", finalWxid)
                            .replace("%type%", typeStr);
                    } else {
                        title = (isAtMe || isAtAll) ? "🔔 被" + finalKeyword + "通知" : "🔔 命中关键词: " + finalKeyword;
                    }

                    if (!TextUtils.isEmpty(notifyContentTemplate)) {
                        body = notifyContentTemplate
                            .replace("%keyword%", finalKeyword)
                            .replace("%sender%", finalSenderInfo)
                            .replace("%wxid%", finalWxid)
                            .replace("%content%", finalContent)
                            .replace("%type%", typeStr);
                    } else {
                        body = typeStr + " [" + finalSenderInfo + "]: " + finalContent;
                    }

                    notify(title, body);
                } catch (Exception e) {
                    log("发送通知失败: " + e.getMessage());
                }
            }
        });
    }

    // 发送Toast
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
                        // 支持变量替换
                        toastMsg = toastTemplate
                            .replace("%keyword%", finalKeyword)
                            .replace("%sender%", finalSenderInfo)
                            .replace("%wxid%", finalWxid)
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
        String displayName = contactId;
        if (contactId.endsWith("@chatroom")) {
            displayName = "💬 群聊: " + getGroupName(contactId);
        } else {
            displayName = "👤 " + getFriendDisplayName(contactId);
        }
        includeDisplayList.add(displayName);
    }

    includeAdapter = new ArrayAdapter<>(getTopActivity(), android.R.layout.simple_list_item_1, includeDisplayList);
    includeListView.setAdapter(includeAdapter);

    final Runnable refreshListRunnable = new Runnable() {
        public void run() {
            includeContactList.clear();
            includeContactList.addAll(includeContactSet);
            includeDisplayList.clear();
            for (String contactId : includeContactList) {
                String displayName = contactId;
                if (contactId.endsWith("@chatroom")) {
                    displayName = "💬 群聊: " + getGroupName(contactId);
                } else {
                    displayName = "👤 " + getFriendDisplayName(contactId);
                }
                includeDisplayList.add(displayName);
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

    includeListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            if (position < includeContactList.size()) {
                String contactId = includeContactList.get(position);
                String displayName = includeDisplayList.get(position);
                showRemoveIncludeContactDialog(contactId, displayName, refreshListRunnable);
            }
        }
    });

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
            showClearIncludeContactsConfirmDialog();
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
                showRemoveIncludeContactDialog(contactId, displayName, fullRefreshRunnable);
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
        String displayName = contactId;
        if (contactId.endsWith("@chatroom")) {
            displayName = "💬 群聊: " + getGroupName(contactId);
        } else {
            displayName = "👤 " + getFriendDisplayName(contactId);
        }
        excludeDisplayList.add(displayName);
    }

    excludeAdapter = new ArrayAdapter<>(getTopActivity(), android.R.layout.simple_list_item_1, excludeDisplayList);
    excludeListView.setAdapter(excludeAdapter);

    final Runnable refreshListRunnable = new Runnable() {
        public void run() {
            excludeContactList.clear();
            excludeContactList.addAll(excludeContactSet);
            excludeDisplayList.clear();
            for (String contactId : excludeContactList) {
                String displayName = contactId;
                if (contactId.endsWith("@chatroom")) {
                    displayName = "💬 群聊: " + getGroupName(contactId);
                } else {
                    displayName = "👤 " + getFriendDisplayName(contactId);
                }
                excludeDisplayList.add(displayName);
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

    excludeListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            if (position < excludeContactList.size()) {
                String contactId = excludeContactList.get(position);
                String displayName = excludeDisplayList.get(position);
                showRemoveExcludeContactDialog(contactId, displayName, refreshListRunnable);
            }
        }
    });

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
            showClearExcludeContactsConfirmDialog();
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
                showRemoveExcludeContactDialog(contactId, displayName, fullRefreshRunnable);
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

    // 匹配模式选择 - 使用按钮点击切换
    TextView modeLabel = new TextView(getTopActivity());
    modeLabel.setText("匹配模式:");
    modeLabel.setTextSize(14);
    modeLabel.setTextColor(Color.parseColor("#666666"));
    modeLabel.setPadding(0, 24, 0, 12);
    root.addView(modeLabel);

    // 匹配模式按钮容器
    LinearLayout buttonContainer = new LinearLayout(getTopActivity());
    buttonContainer.setOrientation(LinearLayout.HORIZONTAL);
    buttonContainer.setWeightSum(2);
    buttonContainer.setPadding(0, 0, 0, 16);

    // 模糊匹配按钮
    final TextView fuzzyBtn = new TextView(getTopActivity());
    fuzzyBtn.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
    fuzzyBtn.setText("🔍 模糊匹配");
    fuzzyBtn.setTextSize(14);
    fuzzyBtn.setGravity(Gravity.CENTER);
    fuzzyBtn.setPadding(16, 20, 16, 20);
    fuzzyBtn.setBackgroundResource(android.R.drawable.btn_default);
    fuzzyBtn.setBackgroundColor(Color.parseColor("#4CAF50"));
    fuzzyBtn.setTextColor(Color.WHITE);

    // 全字匹配按钮
    final TextView wholeWordBtn = new TextView(getTopActivity());
    wholeWordBtn.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
    wholeWordBtn.setText("📝 全字匹配");
    wholeWordBtn.setTextSize(14);
    wholeWordBtn.setGravity(Gravity.CENTER);
    wholeWordBtn.setPadding(16, 20, 16, 20);
    wholeWordBtn.setBackgroundResource(android.R.drawable.btn_default);
    wholeWordBtn.setBackgroundColor(Color.parseColor("#E0E0E0"));
    wholeWordBtn.setTextColor(Color.parseColor("#333333"));

    // 匹配模式说明
    final TextView modeDesc = new TextView(getTopActivity());
    modeDesc.setText("🔍 模糊匹配 - 关键词包含在消息中即可触发");
    modeDesc.setTextSize(12);
    modeDesc.setTextColor(Color.parseColor("#888888"));
    modeDesc.setPadding(0, 0, 0, 16);

    // 选中模式: 0=模糊匹配, 1=全字匹配
    final int[] selectedMode = {0};

    // 更新按钮样式的函数
    Runnable updateButtonStyle = new Runnable() {
        public void run() {
            if (selectedMode[0] == 0) {
                // 模糊匹配选中
                fuzzyBtn.setBackgroundColor(Color.parseColor("#4CAF50"));
                fuzzyBtn.setTextColor(Color.WHITE);
                wholeWordBtn.setBackgroundColor(Color.parseColor("#E0E0E0"));
                wholeWordBtn.setTextColor(Color.parseColor("#333333"));
                modeDesc.setText("🔍 模糊匹配 - 关键词包含在消息中即可触发");
            } else {
                // 全字匹配选中
                fuzzyBtn.setBackgroundColor(Color.parseColor("#E0E0E0"));
                fuzzyBtn.setTextColor(Color.parseColor("#333333"));
                wholeWordBtn.setBackgroundColor(Color.parseColor("#2196F3"));
                wholeWordBtn.setTextColor(Color.WHITE);
                modeDesc.setText("📝 全字匹配 - 只有完整单词匹配才触发");
            }
        }
    };

    // 模糊匹配按钮点击
    fuzzyBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            selectedMode[0] = 0;
            updateButtonStyle.run();
        }
    });

    // 全字匹配按钮点击
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

    // 匹配模式选择 - 使用按钮点击切换
    TextView modeLabel = new TextView(getTopActivity());
    modeLabel.setText("匹配模式:");
    modeLabel.setTextSize(14);
    modeLabel.setTextColor(Color.parseColor("#666666"));
    modeLabel.setPadding(0, 24, 0, 12);
    root.addView(modeLabel);

    // 匹配模式按钮容器
    LinearLayout buttonContainer = new LinearLayout(getTopActivity());
    buttonContainer.setOrientation(LinearLayout.HORIZONTAL);
    buttonContainer.setWeightSum(2);
    buttonContainer.setPadding(0, 0, 0, 16);

    // 模糊匹配按钮
    final TextView fuzzyBtn = new TextView(getTopActivity());
    fuzzyBtn.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
    fuzzyBtn.setText("🔍 模糊匹配");
    fuzzyBtn.setTextSize(14);
    fuzzyBtn.setGravity(Gravity.CENTER);
    fuzzyBtn.setPadding(16, 20, 16, 20);
    fuzzyBtn.setBackgroundResource(android.R.drawable.btn_default);
    fuzzyBtn.setTextColor(Color.WHITE);

    // 全字匹配按钮
    final TextView wholeWordBtn = new TextView(getTopActivity());
    wholeWordBtn.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
    wholeWordBtn.setText("📝 全字匹配");
    wholeWordBtn.setTextSize(14);
    wholeWordBtn.setGravity(Gravity.CENTER);
    wholeWordBtn.setPadding(16, 20, 16, 20);
    wholeWordBtn.setBackgroundResource(android.R.drawable.btn_default);
    wholeWordBtn.setTextColor(Color.parseColor("#333333"));

    // 匹配模式说明
    final TextView modeDesc = new TextView(getTopActivity());
    modeDesc.setText(currentWholeWord ? "📝 全字匹配 - 只有完整单词匹配才触发" : "🔍 模糊匹配 - 关键词包含在消息中即可触发");
    modeDesc.setTextSize(12);
    modeDesc.setTextColor(Color.parseColor("#888888"));
    modeDesc.setPadding(0, 0, 0, 16);

    // 选中模式: 初始值为当前保存的模式
    final int[] selectedMode = {currentWholeWord ? 1 : 0};

    // 更新按钮样式的函数
    Runnable updateButtonStyle = new Runnable() {
        public void run() {
            if (selectedMode[0] == 0) {
                // 模糊匹配选中
                fuzzyBtn.setBackgroundColor(Color.parseColor("#4CAF50"));
                fuzzyBtn.setTextColor(Color.WHITE);
                wholeWordBtn.setBackgroundColor(Color.parseColor("#E0E0E0"));
                wholeWordBtn.setTextColor(Color.parseColor("#333333"));
                modeDesc.setText("🔍 模糊匹配 - 关键词包含在消息中即可触发");
            } else {
                // 全字匹配选中
                fuzzyBtn.setBackgroundColor(Color.parseColor("#E0E0E0"));
                fuzzyBtn.setTextColor(Color.parseColor("#333333"));
                wholeWordBtn.setBackgroundColor(Color.parseColor("#2196F3"));
                wholeWordBtn.setTextColor(Color.WHITE);
                modeDesc.setText("📝 全字匹配 - 只有完整单词匹配才触发");
            }
        }
    };

    // 初始化按钮样式
    updateButtonStyle.run();

    // 模糊匹配按钮点击
    fuzzyBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            selectedMode[0] = 0;
            updateButtonStyle.run();
        }
    });

    // 全字匹配按钮点击
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
    showLoadingDialog("加载好友列表", "正在获取好友...", new Runnable() {
        public void run() {
            try {
                if (sCachedFriendList == null) sCachedFriendList = getFriendList();

                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    public void run() {
                        List<String> names = new ArrayList<>();
                        List<String> ids = new ArrayList<>();

                        if (sCachedFriendList != null) {
                            for (int i = 0; i < sCachedFriendList.size(); i++) {
                                FriendInfo f = (FriendInfo) sCachedFriendList.get(i);

                                String nickname = TextUtils.isEmpty(f.getNickname()) ? "未知昵称" : f.getNickname();
                                String remark = f.getRemark();
                                String displayName = !TextUtils.isEmpty(remark) ? nickname + " (" + remark + ")" : nickname;
                                String wxid = f.getWxid();

                                // 过滤掉群聊ID和文件助手
                                if (!TextUtils.isEmpty(wxid) && !wxid.endsWith("@chatroom") && !wxid.equals("filehelper")) {
                                    // 显示格式：👤 昵称 (备注) - wxid
                                    names.add("👤 " + displayName + " - " + wxid);
                                    ids.add(wxid);
                                }
                            }
                        }

                        // 使用多选对话框，支持搜索和全选
                        showMultiSelectDialog("👤 选择要排除的好友 (支持搜索)", names, ids, excludeContactSet, "搜索昵称/备注/wxid...", new Runnable() {
                            public void run() {
                                saveConfig();
                                refreshExcludeDisplay();
                                int addedCount = excludeContactSet.size();
                                toast("✅ 已更新排除列表，当前排除 " + addedCount + " 个联系人");
                            }
                        }, null);
                    }
                });
            } catch (Exception e) {
                log("选择好友失败: " + e.getMessage());
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    public void run() {
                        toast("无法获取好友列表");
                    }
                });
            }
        }
    });
}

/**
 * 选择群聊添加到排除列表 (改进版:支持搜索、全选)
 */
private void selectGroupToExclude() {
    showLoadingDialog("加载群聊列表", "正在获取群聊...", new Runnable() {
        public void run() {
            try {
                if (sCachedGroupList == null) sCachedGroupList = getGroupList();

                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    public void run() {
                        List<String> names = new ArrayList<>();
                        List<String> ids = new ArrayList<>();

                        if (sCachedGroupList != null) {
                            for (int i = 0; i < sCachedGroupList.size(); i++) {
                                GroupInfo g = (GroupInfo) sCachedGroupList.get(i);
                                String name = !TextUtils.isEmpty(g.getName()) ? g.getName() : "未命名群聊";
                                String roomId = g.getRoomId();

                                if (!TextUtils.isEmpty(roomId)) {
                                    // 显示格式：🏠 群名称 - roomid
                                    names.add("🏠 " + name + " - " + roomId);
                                    ids.add(roomId);
                                }
                            }
                        }

                        // 使用多选对话框，支持搜索和全选
                        showMultiSelectDialog("💬 选择要排除的群聊 (支持搜索)", names, ids, excludeContactSet, "搜索群名/wxid...", new Runnable() {
                            public void run() {
                                saveConfig();
                                refreshExcludeDisplay();
                                int addedCount = excludeContactSet.size();
                                toast("✅ 已更新排除列表，当前排除 " + addedCount + " 个联系人");
                            }
                        }, null);
                    }
                });
            } catch (Exception e) {
                log("选择群聊失败: " + e.getMessage());
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    public void run() {
                        toast("无法获取群聊列表");
                    }
                });
            }
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
    showLoadingDialog("加载好友列表", "正在获取好友...", new Runnable() {
        public void run() {
            try {
                if (sCachedFriendList == null) sCachedFriendList = getFriendList();

                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    public void run() {
                        List<String> names = new ArrayList<>();
                        List<String> ids = new ArrayList<>();

                        if (sCachedFriendList != null) {
                            for (int i = 0; i < sCachedFriendList.size(); i++) {
                                FriendInfo f = (FriendInfo) sCachedFriendList.get(i);

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

                        showMultiSelectDialog("👤 选择仅生效的好友 (支持搜索)", names, ids, includeContactSet, "搜索昵称/备注/wxid...", new Runnable() {
                            public void run() {
                                saveConfig();
                                refreshIncludeDisplay();
                                int addedCount = includeContactSet.size();
                                toast("✅ 已更新仅生效列表，当前 " + addedCount + " 个联系人");
                            }
                        }, null);
                    }
                });
            } catch (Exception e) {
                log("选择好友失败: " + e.getMessage());
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    public void run() {
                        toast("无法获取好友列表");
                    }
                });
            }
        }
    });
}

/**
 * 选择群聊添加到仅生效列表
 */
private void selectGroupToInclude() {
    showLoadingDialog("加载群聊列表", "正在获取群聊...", new Runnable() {
        public void run() {
            try {
                if (sCachedGroupList == null) sCachedGroupList = getGroupList();

                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    public void run() {
                        List<String> names = new ArrayList<>();
                        List<String> ids = new ArrayList<>();

                        if (sCachedGroupList != null) {
                            for (int i = 0; i < sCachedGroupList.size(); i++) {
                                GroupInfo g = (GroupInfo) sCachedGroupList.get(i);
                                String name = !TextUtils.isEmpty(g.getName()) ? g.getName() : "未命名群聊";
                                String roomId = g.getRoomId();

                                if (!TextUtils.isEmpty(roomId)) {
                                    names.add("🏠 " + name + " - " + roomId);
                                    ids.add(roomId);
                                }
                            }
                        }

                        showMultiSelectDialog("💬 选择仅生效的群聊 (支持搜索)", names, ids, includeContactSet, "搜索群名/wxid...", new Runnable() {
                            public void run() {
                                saveConfig();
                                refreshIncludeDisplay();
                                int addedCount = includeContactSet.size();
                                toast("✅ 已更新仅生效列表，当前 " + addedCount + " 个联系人");
                            }
                        }, null);
                    }
                });
            } catch (Exception e) {
                log("选择群聊失败: " + e.getMessage());
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    public void run() {
                        toast("无法获取群聊列表");
                    }
                });
            }
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

/**
 * 移除仅生效联系人确认对话框
 */
private void showRemoveIncludeContactDialog(final String contactId, final String displayName, final Runnable onRemoved) {
    String type = contactId.endsWith("@chatroom") ? "群聊" : "好友";

    AlertDialog dialog = new AlertDialog.Builder(getTopActivity())
        .setTitle("移除仅生效")
        .setMessage("确定要移除仅生效" + type + " [" + displayName + "] 吗?\n移除后，来自该" + type + "的消息将不再触发通知。")
        .setPositiveButton("移除", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                includeContactSet.remove(contactId);
                saveConfig();
                onRemoved.run();
                toast("✅ 已移除仅生效");
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
 * 清空所有仅生效联系人确认对话框
 */
private void showClearIncludeContactsConfirmDialog() {
    AlertDialog dialog = new AlertDialog.Builder(getTopActivity())
        .setTitle("🗑️ 清空仅生效列表")
        .setMessage("确定要清空所有仅生效联系人吗?")
        .setPositiveButton("清空", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                includeContactSet.clear();
                saveConfig();
                refreshIncludeDisplay();
                toast("✅ 已清空仅生效列表");
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
 * 刷新仅生效列表显示
 */
private void refreshIncludeDisplay() {
    try {
        if (includeCountTv != null) {
            includeCountTv.setText("已添加 " + includeContactSet.size() + " 个联系人");
        }
        if (includeListView != null && includeAdapter != null && includeContactList != null && includeDisplayList != null) {
            includeContactList.clear();
            includeContactList.addAll(includeContactSet);
            includeDisplayList.clear();
            for (String contactId : includeContactList) {
                String displayName = contactId;
                if (contactId.endsWith("@chatroom")) {
                    displayName = "💬 群聊: " + getGroupName(contactId);
                } else {
                    displayName = "👤 " + getFriendDisplayName(contactId);
                }
                includeDisplayList.add(displayName);
            }
            includeAdapter.notifyDataSetChanged();

            int itemHeight = dpToPx(48);
            int listHeight = Math.max(Math.min(includeContactList.size() * itemHeight, dpToPx(200)), dpToPx(48));
            LinearLayout.LayoutParams listParams = (LinearLayout.LayoutParams) includeListView.getLayoutParams();
            if (listParams != null) {
                listParams.height = listHeight;
                includeListView.setLayoutParams(listParams);
            }
        }
        if (includeClearBtn != null) {
            includeClearBtn.setVisibility(includeContactSet.size() > 1 ? View.VISIBLE : View.GONE);
        }
    } catch (Exception e) {
        log("刷新仅生效列表显示失败: " + e.getMessage());
    }
}


/**
 * 移除排除联系人确认对话框
 */
private void showRemoveExcludeContactDialog(final String contactId, final String displayName, final Runnable onRemoved) {
    String type = contactId.endsWith("@chatroom") ? "群聊" : "好友";

    AlertDialog dialog = new AlertDialog.Builder(getTopActivity())
        .setTitle("移除排除")
        .setMessage("确定要移除排除" + type + " [" + displayName + "] 吗？\n移除后，来自该" + type + "的消息将正常检查关键词。")
        .setPositiveButton("移除", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                excludeContactSet.remove(contactId);
                saveConfig();
                onRemoved.run();
                toast("✅ 已移除排除");
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
 * 清空所有排除联系人确认对话框
 */
private void showClearExcludeContactsConfirmDialog() {
    AlertDialog dialog = new AlertDialog.Builder(getTopActivity())
        .setTitle("🗑️ 清空排除列表")
        .setMessage("确定要清空所有排除联系人吗？")
        .setPositiveButton("清空", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                excludeContactSet.clear();
                saveConfig();
                refreshExcludeDisplay();
                toast("✅ 已清空排除列表");
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

    // 提示信息
    TextView tip = new TextView(getTopActivity());
    tip.setText("支持变量替换：%keyword% (关键词), %sender% (发送者), %wxid% (发送者ID), %content% (内容), %type% (好友/群消息)\n留空则使用默认文字");
    tip.setTextSize(12);
    tip.setTextColor(Color.parseColor("#666666"));
    tip.setPadding(0, 0, 0, 16);
    root.addView(tip);

    // ========== 关键词部分 ==========
    root.addView(createSectionTitle("🔑 关键词自定义"));

    // 通知标题
    root.addView(createTextView(getTopActivity(), "通知标题模板:", 14, 8));
    final EditText keywordTitleInput = createStyledEditText("例如: 监控提醒", customKeywordNotifyTitle);
    root.addView(keywordTitleInput);
    LinearLayout keywordTitleVarRow = createVariableButtons(new String[]{
        "%keyword%", "%sender%", "%wxid%", "%content%", "%type%"
    }, keywordTitleInput);
    root.addView(keywordTitleVarRow);

    // 通知内容
    root.addView(createTextView(getTopActivity(), "通知内容模板:", 14, 8));
    final EditText keywordContentInput = createStyledEditText("例如: [%sender%] %content%", customKeywordNotifyContent);
    root.addView(keywordContentInput);
    LinearLayout keywordContentVarRow = createVariableButtons(new String[]{
        "%keyword%", "%sender%", "%wxid%", "%content%", "%type%"
    }, keywordContentInput);
    root.addView(keywordContentVarRow);

    // Toast文字
    root.addView(createTextView(getTopActivity(), "Toast文字模板:", 14, 8));
    final EditText keywordToastInput = createStyledEditText("例如: 命中关键词: %keyword%", customKeywordToastText);
    root.addView(keywordToastInput);
    LinearLayout keywordToastVarRow = createVariableButtons(new String[]{
        "%keyword%", "%sender%", "%wxid%", "%content%", "%type%"
    }, keywordToastInput);
    root.addView(keywordToastVarRow);

    // ========== @我部分 ==========
    root.addView(createSectionTitle("@我自定义"));

    // 通知标题
    root.addView(createTextView(getTopActivity(), "通知标题模板:", 14, 8));
    final EditText atMeTitleInput = createStyledEditText("例如: @我提醒", customAtMeNotifyTitle);
    root.addView(atMeTitleInput);
    LinearLayout atMeTitleVarRow = createVariableButtons(new String[]{
        "%keyword%", "%sender%", "%wxid%", "%content%", "%type%"
    }, atMeTitleInput);
    root.addView(atMeTitleVarRow);

    // 通知内容
    root.addView(createTextView(getTopActivity(), "通知内容模板:", 14, 8));
    final EditText atMeContentInput = createStyledEditText("例如: [%sender%] %content%", customAtMeNotifyContent);
    root.addView(atMeContentInput);
    LinearLayout atMeContentVarRow = createVariableButtons(new String[]{
        "%keyword%", "%sender%", "%wxid%", "%content%", "%type%"
    }, atMeContentInput);
    root.addView(atMeContentVarRow);

    // Toast文字
    root.addView(createTextView(getTopActivity(), "Toast文字模板:", 14, 8));
    final EditText atMeToastInput = createStyledEditText("例如: @我通知: %keyword%", customAtMeToastText);
    root.addView(atMeToastInput);
    LinearLayout atMeToastVarRow = createVariableButtons(new String[]{
        "%keyword%", "%sender%", "%wxid%", "%content%", "%type%"
    }, atMeToastInput);
    root.addView(atMeToastVarRow);

    // ========== @所有人部分 ==========
    root.addView(createSectionTitle("@所有人/群公告自定义"));

    // 通知标题
    root.addView(createTextView(getTopActivity(), "通知标题模板:", 14, 8));
    final EditText atAllTitleInput = createStyledEditText("例如: @所有人提醒", customAtAllNotifyTitle);
    root.addView(atAllTitleInput);
    LinearLayout atAllTitleVarRow = createVariableButtons(new String[]{
        "%keyword%", "%sender%", "%wxid%", "%content%", "%type%"
    }, atAllTitleInput);
    root.addView(atAllTitleVarRow);

    // 通知内容
    root.addView(createTextView(getTopActivity(), "通知内容模板:", 14, 8));
    final EditText atAllContentInput = createStyledEditText("例如: [%sender%] %content%", customAtAllNotifyContent);
    root.addView(atAllContentInput);
    LinearLayout atAllContentVarRow = createVariableButtons(new String[]{
        "%keyword%", "%sender%", "%wxid%", "%content%", "%type%"
    }, atAllContentInput);
    root.addView(atAllContentVarRow);

    // Toast文字
    root.addView(createTextView(getTopActivity(), "Toast文字模板:", 14, 8));
    final EditText atAllToastInput = createStyledEditText("例如: @所有人通知: %keyword%", customAtAllToastText);
    root.addView(atAllToastInput);
    LinearLayout atAllToastVarRow = createVariableButtons(new String[]{
        "%keyword%", "%sender%", "%wxid%", "%content%", "%type%"
    }, atAllToastInput);
    root.addView(atAllToastVarRow);

    // 按钮区域
    LinearLayout btnRow = new LinearLayout(getTopActivity());
    btnRow.setOrientation(LinearLayout.HORIZONTAL);
    btnRow.setGravity(Gravity.CENTER);

    // 恢复默认按钮
    Button resetBtn = new Button(getTopActivity());
    resetBtn.setText("恢复默认");
    styleUtilityButton(resetBtn);
    LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
    );
    btnParams.setMargins(8, 24, 8, 8);
    resetBtn.setLayoutParams(btnParams);
    resetBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            keywordTitleInput.setText("");
            keywordContentInput.setText("");
            keywordToastInput.setText("");
            atMeTitleInput.setText("");
            atMeContentInput.setText("");
            atMeToastInput.setText("");
            atAllTitleInput.setText("");
            atAllContentInput.setText("");
            atAllToastInput.setText("");
            toast("已清空自定义文字");
        }
    });
    btnRow.addView(resetBtn);

    // 清除模板按钮
    Button clearBtn = new Button(getTopActivity());
    clearBtn.setText("清除模板");
    styleUtilityButton(clearBtn);
    clearBtn.setLayoutParams(btnParams);
    clearBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            keywordTitleInput.setText("");
            keywordContentInput.setText("");
            keywordToastInput.setText("");
            atMeTitleInput.setText("");
            atMeContentInput.setText("");
            atMeToastInput.setText("");
            atAllTitleInput.setText("");
            atAllContentInput.setText("");
            atAllToastInput.setText("");
            toast("已清除所有输入");
        }
    });
    btnRow.addView(clearBtn);

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

/**
 * 创建变量标签按钮行
 */
private LinearLayout createVariableButtons(String[] variables, final EditText targetEditText) {
    LinearLayout row = new LinearLayout(getTopActivity());
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setWeightSum(variables.length);

    for (int i = 0; i < variables.length; i++) {
        Button btn = createVarButton(variables[i], targetEditText);
        row.addView(btn);
    }

    return row;
}

/**
 * 创建单个变量按钮（每个按钮有独立作用域）
 */
private Button createVarButton(final String variable, final EditText targetEditText) {
    Button btn = new Button(getTopActivity());
    btn.setText(variable);
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

/**
 * 刷新排除列表显示（供添加排除后调用）
 */
private void refreshExcludeDisplay() {
    try {
        if (excludeCountTv != null) {
            excludeCountTv.setText("已排除 " + excludeContactSet.size() + " 个联系人");
        }
        if (excludeListView != null && excludeAdapter != null && excludeContactList != null && excludeDisplayList != null) {
            // 更新数据
            excludeContactList.clear();
            excludeContactList.addAll(excludeContactSet);
            excludeDisplayList.clear();
            for (String contactId : excludeContactList) {
                String displayName = contactId;
                if (contactId.endsWith("@chatroom")) {
                    displayName = "💬 群聊: " + getGroupName(contactId);
                } else {
                    displayName = "👤 " + getFriendDisplayName(contactId);
                }
                excludeDisplayList.add(displayName);
            }
            excludeAdapter.notifyDataSetChanged();

            // 动态调整列表高度
            int itemHeight = dpToPx(48);
            int listHeight = Math.max(Math.min(excludeContactList.size() * itemHeight, dpToPx(200)), dpToPx(48));
            LinearLayout.LayoutParams listParams = (LinearLayout.LayoutParams) excludeListView.getLayoutParams();
            if (listParams != null) {
                listParams.height = listHeight;
                excludeListView.setLayoutParams(listParams);
            }
        }
        // 更新清空按钮可见性
        if (excludeClearBtn != null) {
            excludeClearBtn.setVisibility(excludeContactSet.size() > 1 ? View.VISIBLE : View.GONE);
        }
    } catch (Exception e) {
        log("刷新排除列表显示失败: " + e.getMessage());
    }
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
        JSONObject json = JSON.parseObject(existingData);
        json.put(itemName, value);
        putString(setName, json.toString());
    } catch (Exception e) {
        JSONObject json = new JSONObject();
        json.put(itemName, value);
        putString(setName, json.toString());
    }
}

private String getString(String setName, String itemName, String defaultValue) {
    String data = getString(setName, "{}");
    try {
        JSONObject json = JSON.parseObject(data);
        if (json != null && json.containsKey(itemName)) {
            return json.getString(itemName);
        }
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