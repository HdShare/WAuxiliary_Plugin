import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Looper;
import android.widget.CheckBox;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Objects;
import android.view.MotionEvent;
import java.util.Collections;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONException;
import android.provider.Settings;
import java.util.UUID;
import java.security.MessageDigest;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.AbsoluteSizeSpan;

// OkHttp3 and Fastjson2 imports for AI functionality

// DeviceInfo related imports

// UI related imports from 小智bot

// === 文件/文件夹浏览与多选 ===
final String DEFAULT_LAST_FOLDER_SP_AUTO = "last_folder_for_media_auto";
final String ROOT_FOLDER = android.os.Environment.getExternalStorageDirectory().getAbsolutePath();

// 回调接口（必须定义在使用之前）
interface MediaSelectionCallback {
    void onSelected(ArrayList<String> selectedFiles);
}

/* ========== 单例模式文件夹浏览器全局变量 ========== */
AlertDialog gFolderDialogAuto = null;
ArrayAdapter gFolderAdapterAuto = null;
ArrayList gFolderNamesAuto = new ArrayList();
ArrayList gFolderFilesAuto = new ArrayList();
File gCurrentFolderAuto = null;
String gWantedExtFilterAuto = "";
String gCurrentSelectionAuto = "";
MediaSelectionCallback gMediaCallbackAuto = null;
boolean gAllowFolderSelectAuto = false;

// 自动回复配置相关的key
private final String AUTO_REPLY_RULES_KEY = "auto_reply_rules";
private final String ENABLE_LOG_KEY = "enable_app_debug_log"; // 新增：日志开关KEY
// === PATCH_AUTO_REPLY_NOTIFY_KEYS_START ===
// 自动回复成功通知相关key
private final String AUTO_REPLY_SUCCESS_TOAST_KEY = "auto_reply_success_toast";
private final String AUTO_REPLY_SUCCESS_NOTIFY_KEY = "auto_reply_success_notify";
private final String AUTO_REPLY_SUCCESS_SHOW_CONTENT_KEY = "auto_reply_success_show_content";
private final String AUTO_REPLY_SUCCESS_TEMPLATE_KEY = "auto_reply_success_template";
// === PATCH_AUTO_REPLY_NOTIFY_KEYS_END ===


// 自动同意好友请求相关的key
private final String AUTO_ACCEPT_FRIEND_ENABLED_KEY = "auto_accept_friend_enabled";
private final String AUTO_ACCEPT_DELAY_KEY = "auto_accept_delay";
private final String AUTO_ACCEPT_REPLY_ITEMS_KEY = "auto_accept_reply_items_v2";

// 我添加好友被通过后，自动回复相关的key
private final String GREET_ON_ACCEPTED_ENABLED_KEY = "greet_on_accepted_enabled";
private final String GREET_ON_ACCEPTED_DELAY_KEY = "greet_on_accepted_delay";
private final String GREET_ON_ACCEPTED_REPLY_ITEMS_KEY = "greet_on_accepted_reply_items_v2";
private final String FRIEND_ADD_SUCCESS_KEYWORD = "我通过了你的朋友验证请求，现在我们可以开始聊天了";

// 小智AI 配置相关的key
private final String XIAOZHI_CONFIG_KEY = "xiaozhi_ai_config";
private final String XIAOZHI_SERVE_KEY = "xiaozhi_serve_url";
private final String XIAOZHI_OTA_KEY = "xiaozhi_ota_url";
private final String XIAOZHI_CONSOLE_KEY = "xiaozhi_console_url";

// 智聊AI 配置相关的key (移植自旧脚本)
private final String ZHILIA_AI_API_KEY = "zhilia_ai_api_key";
private final String ZHILIA_AI_API_URL = "zhilia_ai_api_url";
private final String ZHILIA_AI_MODEL_NAME = "zhilia_ai_model_name";
private final String ZHILIA_AI_SYSTEM_PROMPT = "zhilia_ai_system_prompt";
private final String ZHILIA_AI_CONTEXT_LIMIT = "zhilia_ai_context_limit";
private final String ZHILIA_MULTI_CONFIGS_KEY = "zhilia_multi_configs_v1";
private final String ZHILIA_ACTIVE_CONFIG_NAME_KEY = "zhilia_active_config_name_v1";
private final String ZHILIA_AI_STREAM_ENABLED_KEY = "zhilia_ai_stream_enabled_v1";
private final String ZHILIA_MODEL_FAVORITES_KEY = "zhilia_model_favorites_v1";

// 匹配类型常量
private final static int MATCH_TYPE_FUZZY = 0;      // 模糊匹配
private final static int MATCH_TYPE_EXACT = 1;      // 全字匹配
private final static int MATCH_TYPE_REGEX = 2;      // 正则匹配
private final static int MATCH_TYPE_ANY = 3;        // 任何消息都匹配

// @触发类型常量
private final static int AT_TRIGGER_NONE = 0;       // 不限@触发
private final static int AT_TRIGGER_ME = 1;         // @我触发
private final static int AT_TRIGGER_ALL = 2;        // @全体触发

// 拍一拍触发类型常量
private final static int PAT_TRIGGER_NONE = 0;      // 不限拍一拍触发
private final static int PAT_TRIGGER_ME = 1;        // 被拍一拍触发

// 规则生效目标类型常量
private final static int TARGET_TYPE_NONE = 0;      // 不指定
private final static int TARGET_TYPE_FRIEND = 1;    // 指定好友
private final static int TARGET_TYPE_GROUP = 2;     // 指定群聊
private final static int TARGET_TYPE_BOTH = 3;      // 同时指定好友和群聊

// 消息回复类型常量
private final static int REPLY_TYPE_TEXT = 0;       // 文本回复
private final static int REPLY_TYPE_IMAGE = 1;      // 图片回复
private final static int REPLY_TYPE_VOICE_FILE_LIST = 2; // 语音回复 (从文件列表随机)
private final static int REPLY_TYPE_VOICE_FOLDER = 3; // 语音回复 (从文件夹随机)
private final static int REPLY_TYPE_EMOJI = 4;      // 表情回复
private final static int REPLY_TYPE_XIAOZHI_AI = 5; // 小智AI自动回复
private final static int REPLY_TYPE_VIDEO = 6;      // 视频回复
private final static int REPLY_TYPE_CARD = 7;       // 名片回复 (支持多选)
private final static int REPLY_TYPE_FILE = 8;       // 文件分享
private final static int REPLY_TYPE_ZHILIA_AI = 9;  // 智聊AI自动回复 (共存)
private final static int REPLY_TYPE_INVITE_GROUP = 10; // 邀请群聊

// 自动同意好友/被通过的回复类型常量
private final static int ACCEPT_REPLY_TYPE_TEXT = 0;
private final static int ACCEPT_REPLY_TYPE_IMAGE = 1;
private final static int ACCEPT_REPLY_TYPE_VOICE_FIXED = 2;
private final static int ACCEPT_REPLY_TYPE_VOICE_RANDOM = 3;
private final static int ACCEPT_REPLY_TYPE_EMOJI = 4;
private final static int ACCEPT_REPLY_TYPE_VIDEO = 5;
private final static int ACCEPT_REPLY_TYPE_CARD = 6;
private final static int ACCEPT_REPLY_TYPE_FILE = 7;
private final static int ACCEPT_REPLY_TYPE_INVITE_GROUP = 8;

// 用于分隔列表项的特殊字符串
private final String LIST_SEPARATOR = "_#ITEM#_";
// 备份功能相关key
private final String BACKUP_AUTO_REPLY_ENABLED_KEY = "backup_auto_reply_enabled";
private final String BACKUP_AUTO_ACCEPT_ENABLED_KEY = "backup_auto_accept_enabled";
private final String BACKUP_GREET_ACCEPTED_ENABLED_KEY = "backup_greet_accepted_enabled";
private final String BACKUP_XIAOZHI_ENABLED_KEY = "backup_xiaozhi_enabled";
private final String BACKUP_ZHILIA_ENABLED_KEY = "backup_zhilia_enabled";
private final String BACKUP_LAST_PATH_KEY = "backup_last_path";

// 备份文件相关常量
private final String BACKUP_FILE_EXTENSION = ".wauxvbackup";
private final String BACKUP_MAGIC_HEADER = "WAUXV_BACKUP_V1";
private final int BACKUP_VERSION = 1;
// 缓存列表，避免重复获取
private List sCachedFriendList = null;
private List sCachedGroupList = null;
private java.util.Map sCachedGroupMemberCounts = null; // 缓存群成员数量

// 小智AI 功能相关变量
private final OkHttpClient aiClient = new OkHttpClient.Builder().build();
private final java.util.concurrent.ConcurrentMap<String, WebSocket> aiWebSockets = new java.util.concurrent.ConcurrentHashMap<String, WebSocket>();
// 小智AI 引用回复上下文（按会话）
private final java.util.concurrent.ConcurrentMap<String, Long> aiQuoteMsgIdMap = new java.util.concurrent.ConcurrentHashMap<String, Long>();
private final java.util.concurrent.ConcurrentMap<String, Boolean> aiQuoteFlagMap = new java.util.concurrent.ConcurrentHashMap<String, Boolean>();


// 智聊AI 功能相关变量
private Map<String, List> zhiliaConversationHistories = new HashMap<>();
//备份配置
private class BackupData {
    public String magic = BACKUP_MAGIC_HEADER;
    public int version = BACKUP_VERSION;
    public long backupTime = System.currentTimeMillis();
    public String backupInfo = "";
    public BackupConfig config = new BackupConfig();
}

private class BackupConfig {
    // 自动回复
    public List<String> autoReplyRules;
    public boolean autoReplyEnabled;

    // 自动同意好友
    public boolean autoAcceptEnabled;
    public int autoAcceptDelay;
    public List<String> autoAcceptReplyItems;

    // 添加好友被通过后回复
    public boolean greetOnAcceptedEnabled;
    public int greetOnAcceptedDelay;
    public List<String> greetOnAcceptedReplyItems;

    // 小智AI
    public String xiaozhiServeUrl;
    public String xiaozhiOtaUrl;
    public String xiaozhiConsoleUrl;

    // 智聊AI
    public String zhiliaApiKey;
    public String zhiliaApiUrl;
    public String zhiliaModelName;
    public String zhiliaSystemPrompt;
    public int zhiliaContextLimit;

    // 日志
    public boolean logEnabled;
}
// =================== START: 新增统一日志打印控制 ===================
private void debugLog(String msg) {
    // 保持你原有日志开关逻辑
    if (!getBoolean(ENABLE_LOG_KEY, true)) return;

    // 1) 原日志输出（控制台/框架log）
    try {
        log(msg);
    } catch (Exception ignore) {}

    // 2) 统一写入查看日志文件
    try {
        Activity act = getTopActivity();
        if (act == null) return;

        File logDir = new File(act.getExternalFilesDir(null), "logs");
        if (!logDir.exists()) {
            logDir.mkdirs();
        }

        // 统一文件名：查看日志只读这个
        File logFile = new File(logDir, "auto_reply_log.txt");

        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        java.io.FileWriter fw = new java.io.FileWriter(logFile, true);
        fw.write("[" + time + "] " + msg + "\n");
        fw.close();
    } catch (Exception e) {
        try { log("[日志写入失败] " + e.getMessage()); } catch (Exception ignore) {}
    }
}

private String getRuleDisplayName(Map<String, Object> rule) {
    try {
        if (rule == null) return "未命名规则";
        String keyword = rule.get("keyword") == null ? "" : String.valueOf(rule.get("keyword"));
        int matchType = rule.get("matchType") == null ? MATCH_TYPE_FUZZY : (Integer) rule.get("matchType");
        int replyType = rule.get("replyType") == null ? REPLY_TYPE_TEXT : (Integer) rule.get("replyType");

        String matchName = getReadableMatchType(matchType);
        String replyName = getReadableReplyType(replyType);

        if (TextUtils.isEmpty(keyword)) {
            keyword = "任意消息";
        }
        return "[" + matchName + "] " + keyword + " -> " + replyName;
    } catch (Exception e) {
        return "规则";
    }
}

private String buildAutoReplyNotifyText(String talker, Object msgInfoBean, Map<String, Object> rule, String actualReplyText) {
    try {
        boolean isGroupChat = !TextUtils.isEmpty(talker) && talker.contains("@chatroom");

        String senderWxid = resolveRealSenderWxid(msgInfoBean);

        String senderName = "";
        String targetName = "";

        if (isGroupChat) {
            targetName = getGroupName(talker);
            if (TextUtils.isEmpty(targetName) || "未知群聊".equals(targetName)) targetName = talker;

            senderName = getFriendName(senderWxid, talker);
            if (TextUtils.isEmpty(senderName)) senderName = getFriendDisplayName(senderWxid);
            if (TextUtils.isEmpty(senderName)) senderName = senderWxid;
        } else {
            targetName = getFriendDisplayName(talker);
            if (TextUtils.isEmpty(targetName)) targetName = talker;
            senderName = targetName;
        }

        String ruleName = getRuleDisplayName(rule);

        String replyTypeName = "文本";
        int matchType = MATCH_TYPE_FUZZY;
        if (rule != null) {
            if (rule.get("replyType") != null) {
                replyTypeName = getReadableReplyType((Integer) rule.get("replyType"));
            }
            if (rule.get("matchType") != null) {
                matchType = (Integer) rule.get("matchType");
            }
        }
        String matchTypeName = getReadableMatchType(matchType);

        String contentPreview = actualReplyText;
        if (TextUtils.isEmpty(contentPreview) && rule != null && rule.get("reply") != null) {
            contentPreview = String.valueOf(rule.get("reply"));
        }
        if (!TextUtils.isEmpty(contentPreview) && contentPreview.length() > 80) {
            contentPreview = contentPreview.substring(0, 80) + "...";
        }

        String timeValue = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());

        String defaultTemplate =
            "群名：%groupName%\n" +
            "发送者：%senderName%\n" +
            "命中规则：%ruleName%\n" +
            "匹配方式：%matchType%\n" +
            "回复类型：%replyType%\n" +
            "回复内容：%replyContent%\n" +
            "时间：%time%";

        String tpl = getString(AUTO_REPLY_SUCCESS_TEMPLATE_KEY, defaultTemplate);
        if (TextUtils.isEmpty(tpl)) tpl = defaultTemplate;

        String groupNameValue = isGroupChat ? targetName : "";
        String senderNameValue = TextUtils.isEmpty(senderName) ? "" : senderName;
        String ruleNameValue = TextUtils.isEmpty(ruleName) ? "" : ruleName;
        String replyTypeValue = TextUtils.isEmpty(replyTypeName) ? "" : replyTypeName;
        String replyContentValue = TextUtils.isEmpty(contentPreview) ? "" : contentPreview;
        String talkerValue = TextUtils.isEmpty(talker) ? "" : talker;
        String senderWxidValue = TextUtils.isEmpty(senderWxid) ? "" : senderWxid;
        String matchTypeValue = TextUtils.isEmpty(matchTypeName) ? "" : matchTypeName;

        String result = tpl;
        result = result.replace("%groupName%", groupNameValue);
        result = result.replace("%senderName%", senderNameValue);
        result = result.replace("%ruleName%", ruleNameValue);
        result = result.replace("%replyType%", replyTypeValue);
        result = result.replace("%replyContent%", replyContentValue);
        result = result.replace("%talker%", talkerValue);
        result = result.replace("%senderWxid%", senderWxidValue);
        result = result.replace("%time%", timeValue);
        result = result.replace("%matchType%", matchTypeValue);

        String[] lines = result.split("\n");
        StringBuilder cleaned = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line == null) continue;
            String trimmed = line.trim();

            if (TextUtils.isEmpty(trimmed)) continue;
            if (trimmed.endsWith("：")) continue;
            if (trimmed.endsWith(":")) continue;
            if ("群名：".equals(trimmed) || "群名:".equals(trimmed)) continue;
            if ("发送者：".equals(trimmed) || "发送者:".equals(trimmed)) continue;
            if ("命中规则：".equals(trimmed) || "命中规则:".equals(trimmed)) continue;
            if ("匹配方式：".equals(trimmed) || "匹配方式:".equals(trimmed)) continue;
            if ("回复类型：".equals(trimmed) || "回复类型:".equals(trimmed)) continue;
            if ("回复内容：".equals(trimmed) || "回复内容:".equals(trimmed)) continue;
            if ("时间：".equals(trimmed) || "时间:".equals(trimmed)) continue;

            if (cleaned.length() > 0) cleaned.append("\n");
            cleaned.append(line);
        }

        return cleaned.toString().trim();
    } catch (Exception e) {
        return "自动回复成功";
    }
}


private void notifyAutoReplySuccess(String talker, Object msgInfoBean, Map<String, Object> rule, String actualReplyText) {
    try {
        boolean enableToast = getBoolean(AUTO_REPLY_SUCCESS_TOAST_KEY, true);
        boolean enableNotify = getBoolean(AUTO_REPLY_SUCCESS_NOTIFY_KEY, false);
        boolean showContent = getBoolean(AUTO_REPLY_SUCCESS_SHOW_CONTENT_KEY, true);

        if (!enableToast && !enableNotify) return;

        String msg = buildAutoReplyNotifyText(talker, msgInfoBean, rule, showContent ? actualReplyText : "");

        if (enableToast) {
            toast(msg);
        }
        if (enableNotify) {
            notify("自动回复成功", msg);
        }

        debugLog("[自动回复成功提示] " + msg.replace("\n", " | "));
    } catch (Exception e) {
        debugLog("[异常] notifyAutoReplySuccess失败: " + e.getMessage());
    }
}

private void showAutoReplyNotifySettingDialog() {
    try {
        final Activity a = getTopActivity();
        if (a == null) {
            toast("无法获取窗口");
            return;
        }

        ScrollView scrollView = new ScrollView(a);
        LinearLayout root = newRootContainer(a);
        scrollView.addView(root);

        LinearLayout switchCard = newCardWithTitle("提醒开关");

        final LinearLayout toastNotifyRow = createSwitchRow(
            a,
            "🔔 自动回复成功后弹Toast",
            getBoolean(AUTO_REPLY_SUCCESS_TOAST_KEY, true),
            new View.OnClickListener() { public void onClick(View v) {} }
        );
        switchCard.addView(toastNotifyRow);

        final LinearLayout systemNotifyRow = createSwitchRow(
            a,
            "📢 自动回复成功后发通知",
            getBoolean(AUTO_REPLY_SUCCESS_NOTIFY_KEY, false),
            new View.OnClickListener() { public void onClick(View v) {} }
        );
        switchCard.addView(systemNotifyRow);

        final LinearLayout showContentRow = createSwitchRow(
            a,
            "🧾 提示中显示回复内容",
            getBoolean(AUTO_REPLY_SUCCESS_SHOW_CONTENT_KEY, true),
            new View.OnClickListener() { public void onClick(View v) {} }
        );
        switchCard.addView(showContentRow);

        root.addView(switchCard);

        LinearLayout templateCard = newCardWithTitle("通知文案模板");
        addCardText(templateCard, "点击下方变量可自动插入到输入框：", 13);

        final EditText notifyTemplateEdit = createStyledEditText(
            "自定义通知文案",
            getString(
                AUTO_REPLY_SUCCESS_TEMPLATE_KEY,
                "群名：%groupName%\n发送者：%senderName%\n命中规则：%ruleName%\n匹配方式：%matchType%\n回复类型：%replyType%\n回复内容：%replyContent%\n时间：%time%"
            )
        );
        notifyTemplateEdit.setMinLines(6);
        notifyTemplateEdit.setGravity(Gravity.TOP);
        templateCard.addView(notifyTemplateEdit);

        LinearLayout varRow1 = new LinearLayout(a);
        varRow1.setOrientation(LinearLayout.HORIZONTAL);
        varRow1.addView(createVariableChip("%groupName%", "群名", notifyTemplateEdit));
        varRow1.addView(createVariableChip("%senderName%", "发送者名称", notifyTemplateEdit));

        LinearLayout varRow2 = new LinearLayout(a);
        varRow2.setOrientation(LinearLayout.HORIZONTAL);
        varRow2.addView(createVariableChip("%ruleName%", "命中规则名", notifyTemplateEdit));
        varRow2.addView(createVariableChip("%matchType%", "匹配方式", notifyTemplateEdit));

        LinearLayout varRow3 = new LinearLayout(a);
        varRow3.setOrientation(LinearLayout.HORIZONTAL);
        varRow3.addView(createVariableChip("%replyType%", "回复类型", notifyTemplateEdit));
        varRow3.addView(createVariableChip("%replyContent%", "回复内容", notifyTemplateEdit));

        LinearLayout varRow4 = new LinearLayout(a);
        varRow4.setOrientation(LinearLayout.HORIZONTAL);
        varRow4.addView(createVariableChip("%time%", "当前时间", notifyTemplateEdit));
        varRow4.addView(createVariableChip("%talker%", "会话ID", notifyTemplateEdit));

        LinearLayout varRow5 = new LinearLayout(a);
        varRow5.setOrientation(LinearLayout.HORIZONTAL);
        varRow5.addView(createVariableChip("%senderWxid%", "发送者Wxid", notifyTemplateEdit));

        templateCard.addView(varRow1);
        templateCard.addView(varRow2);
        templateCard.addView(varRow3);
        templateCard.addView(varRow4);
        templateCard.addView(varRow5);

        root.addView(templateCard);

        LinearLayout previewCard = newCardWithTitle("预览");
        final TextView previewTv = new TextView(a);
        previewTv.setTextSize(13);
        previewTv.setTextColor(Color.parseColor("#333333"));
        previewTv.setPadding(16, 16, 16, 16);
        previewCard.addView(previewTv);
        root.addView(previewCard);

        final Runnable refreshPreview = new Runnable() {
            public void run() {
                try {
                    String tpl = notifyTemplateEdit.getText().toString();
                    if (TextUtils.isEmpty(tpl)) {
                        tpl = "群名：%groupName%\n发送者：%senderName%\n命中规则：%ruleName%\n匹配方式：%matchType%\n回复类型：%replyType%\n回复内容：%replyContent%\n时间：%time%";
                    }
                    String s = tpl;
                    s = s.replace("%groupName%", "测试群聊");
                    s = s.replace("%senderName%", "张三");
                    s = s.replace("%ruleName%", "[模糊匹配] 在吗 -> 文本");
                    s = s.replace("%replyType%", "文本");
                    s = s.replace("%replyContent%", "在的，请说");
                    s = s.replace("%talker%", "123456@chatroom");
                    s = s.replace("%senderWxid%", "wxid_test123");
                    previewTv.setText(s);
                } catch (Exception e) {
                    previewTv.setText("预览失败: " + e.getMessage());
                }
            }
        };

        notifyTemplateEdit.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            public void afterTextChanged(Editable s) {
                refreshPreview.run();
            }
        });

        refreshPreview.run();

        final CheckBox finalToastNotifyCheck = (CheckBox) toastNotifyRow.getChildAt(1);
        final CheckBox finalSystemNotifyCheck = (CheckBox) systemNotifyRow.getChildAt(1);
        final CheckBox finalShowContentCheck = (CheckBox) showContentRow.getChildAt(1);

        AlertDialog dialog = buildCommonAlertDialog(
            a,
            "🔔 回复成功提醒设置",
            scrollView,
            "保存",
            new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int which) {
                    putBoolean(AUTO_REPLY_SUCCESS_TOAST_KEY, finalToastNotifyCheck.isChecked());
                    putBoolean(AUTO_REPLY_SUCCESS_NOTIFY_KEY, finalSystemNotifyCheck.isChecked());
                    putBoolean(AUTO_REPLY_SUCCESS_SHOW_CONTENT_KEY, finalShowContentCheck.isChecked());
                    putString(AUTO_REPLY_SUCCESS_TEMPLATE_KEY, notifyTemplateEdit.getText().toString());
                    toast("回复成功提醒设置已保存");
                }
            },
            "取消",
            null,
            "测试提醒",
            null
        );

        dialog.show();

        Button neutralBtn = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        if (neutralBtn != null) {
            neutralBtn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    try {
                        String testMsg = notifyTemplateEdit.getText().toString();
                        if (TextUtils.isEmpty(testMsg)) {
                            testMsg = "群名：%groupName%\n发送者：%senderName%\n命中规则：%ruleName%\n匹配方式：%matchType%\n回复类型：%replyType%\n回复内容：%replyContent%\n时间：%time%";
                        }
                        testMsg = testMsg.replace("%groupName%", "测试群聊");
                        testMsg = testMsg.replace("%senderName%", "张三");
                        testMsg = testMsg.replace("%ruleName%", "[模糊匹配] 在吗 -> 文本");
                        testMsg = testMsg.replace("%matchType%", "模糊匹配");
                        testMsg = testMsg.replace("%replyType%", "文本");
                        testMsg = testMsg.replace("%replyContent%", "在的，请说");
                        testMsg = testMsg.replace("%time%", "12:34:56");
                        testMsg = testMsg.replace("%talker%", "123456@chatroom");
                        testMsg = testMsg.replace("%senderWxid%", "wxid_test123");

                        if (finalToastNotifyCheck.isChecked()) {
                            toast(testMsg);
                        }
                        if (finalSystemNotifyCheck.isChecked()) {
                            notify("自动回复成功", testMsg);
                        }
                    } catch (Exception e) {
                        toast("测试失败: " + e.getMessage());
                    }
                }
            });
        }
    } catch (Exception e) {
        toast("打开回复成功提醒设置失败: " + e.getMessage());
        debugLog("[异常] showAutoReplyNotifySettingDialog失败: " + e.getMessage());
    }
}

private boolean isMainWechat() {
    try {
        String p = android.os.Environment.getExternalStorageDirectory().getAbsolutePath();
        return "/storage/emulated/0".equals(p) || "/storage/emulated/0/".equals(p);
    } catch (Exception e) {
        return true;
    }
}

private String getWechatTypeDesc() {
    return isMainWechat() ? "微信主体" : "微信分身";
}

private String getStoragePathPrefix() {
    try {
        return android.os.Environment.getExternalStorageDirectory().getAbsolutePath();
    } catch (Exception e) {
        return "/storage/emulated/0";
    }
}

private String getWechatSpecificBackupPath() {
    String storagePrefix = getStoragePathPrefix();
    if (isMainWechat()) {
        return getString(BACKUP_LAST_PATH_KEY, storagePrefix);
    } else {
        String wxid = getLoginWxid();
        String clonePathKey = "backup_clone_path_" + (TextUtils.isEmpty(wxid) ? "unknown" : wxid);
        String saved = getString(clonePathKey, "");
        if (!TextUtils.isEmpty(saved)) return saved;
        return storagePrefix + "/WauxvBackup_" + (TextUtils.isEmpty(wxid) ? "Clone" : wxid);
    }
}

private void saveWechatSpecificBackupPath(String path) {
    if (!isMainWechat()) {
        String wxid = getLoginWxid();
        String clonePathKey = "backup_clone_path_" + (TextUtils.isEmpty(wxid) ? "unknown" : wxid);
        putString(clonePathKey, path);
    }
    putString(BACKUP_LAST_PATH_KEY, path);
}

private void showBackupMenuDialog() {
    Activity activity = getTopActivity();
    if (activity == null) {
        toast("无法获取当前窗口");
        return;
    }

    LinearLayout root = newRootContainer(activity);

    TextView titleView = new TextView(activity);
    titleView.setText("备份管理");
    titleView.setTextSize(20);
    titleView.setTextColor(Color.parseColor("#333333"));
    titleView.setGravity(Gravity.CENTER);
    titleView.setPadding(0, 0, 0, 24);
    root.addView(titleView);

    LinearLayout wechatTypeCard = createCardLayout();
    boolean isMain = isMainWechat();
    String storagePath = getStoragePathPrefix();
    String wechatTypeText = isMain ? "📱 当前: 微信主体" : "📱 当前: 微信分身";
    wechatTypeText += "\n存储路径: " + storagePath;
    wechatTypeText += "\n账号: " + getLoginWxid();

    TextView wechatTypeView = new TextView(activity);
    wechatTypeView.setText(wechatTypeText);
    wechatTypeView.setTextSize(14);
    wechatTypeView.setTextColor(isMain ? Color.parseColor("#4CAF50") : Color.parseColor("#FF9800"));
    wechatTypeView.setPadding(16, 16, 16, 16);
    wechatTypeCard.addView(wechatTypeView);
    root.addView(wechatTypeCard);

    LinearLayout infoCard = newCardWithTitle("功能说明");
    addCardText(infoCard, "备份功能可以保存您的所有规则配置和AI设置，", 13);
    addCardText(infoCard, "方便您在换机或重装后快速恢复所有设置。", 13);
    addCardText(infoCard, "备份文件使用中文格式，方便手动编辑。", 13);
    root.addView(infoCard);

    LinearLayout optionsCard = newCardWithTitle("备份选项");
    final ListView optionsListView = new ListView(activity);
    setupListViewTouchForScroll(optionsListView);
    optionsListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
    LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    );
    optionsListView.setLayoutParams(listParams);

    final ArrayList optionNames = new ArrayList();
    optionNames.add("自动回复规则");
    optionNames.add("自动同意好友配置");
    optionNames.add("好友通过回复配置");
    optionNames.add("小智AI配置");
    optionNames.add("智聊AI配置");

    final ArrayList optionKeys = new ArrayList();
    optionKeys.add(BACKUP_AUTO_REPLY_ENABLED_KEY);
    optionKeys.add(BACKUP_AUTO_ACCEPT_ENABLED_KEY);
    optionKeys.add(BACKUP_GREET_ACCEPTED_ENABLED_KEY);
    optionKeys.add(BACKUP_XIAOZHI_ENABLED_KEY);
    optionKeys.add(BACKUP_ZHILIA_ENABLED_KEY);

    ArrayAdapter optionsAdapter = new ArrayAdapter(activity, android.R.layout.simple_list_item_multiple_choice, optionNames);
    optionsListView.setAdapter(optionsAdapter);

    for (int i = 0; i < optionKeys.size(); i++) {
        optionsListView.setItemChecked(i, getBoolean((String) optionKeys.get(i), true));
    }

    adjustListViewHeight(optionsListView, optionNames.size());
    optionsCard.addView(optionsListView);
    root.addView(optionsCard);

    LinearLayout buttonCard = createCardLayout();

    buttonCard.addView(newActionButton("📤 导出备份", new View.OnClickListener() {
        public void onClick(View v) {
            for (int i = 0; i < optionKeys.size(); i++) {
                putBoolean((String) optionKeys.get(i), optionsListView.isItemChecked(i));
            }
            showBackupInfoDialog(true);
        }
    }));

    buttonCard.addView(newActionButton("📥 导入备份", new View.OnClickListener() {
        public void onClick(View v) {
            showImportFileSelectDialog();
        }
    }));

    root.addView(buttonCard);

    AlertDialog dialog = buildCommonAlertDialog(
        activity,
        "💾 备份与恢复",
        wrapInScroll(activity, root),
        "关闭",
        null,
        null,
        null,
        null,
        null
    );
    dialog.show();
}

/**
 * 显示备份信息输入对话框
 */
private void showBackupInfoDialog(final boolean isExport) {
    Activity activity = getTopActivity();
    if (activity == null) return;

    LinearLayout root = newRootContainer(activity);

    TextView descView = new TextView(activity);
    descView.setText(isExport ? "请输入备份说明（可选），用于标识此备份的内容：" : "请确认要导入的备份文件：");
    descView.setTextSize(14);
    descView.setTextColor(Color.parseColor("#666666"));
    descView.setPadding(0, 0, 0, 16);
    root.addView(descView);

    final EditText infoEdit = createStyledEditText("备份备注（选填，方便识别）", "");
    root.addView(infoEdit);

    LinearLayout previewCard = newCardWithTitle("将备份的内容：");
    if (getBoolean(BACKUP_AUTO_REPLY_ENABLED_KEY, true)) addCardText(previewCard, "• 自动回复规则", 13);
    if (getBoolean(BACKUP_AUTO_ACCEPT_ENABLED_KEY, true)) addCardText(previewCard, "• 自动同意好友配置", 13);
    if (getBoolean(BACKUP_GREET_ACCEPTED_ENABLED_KEY, true)) addCardText(previewCard, "• 好友通过回复配置", 13);
    if (getBoolean(BACKUP_XIAOZHI_ENABLED_KEY, true)) addCardText(previewCard, "• 小智AI配置", 13);
    if (getBoolean(BACKUP_ZHILIA_ENABLED_KEY, true)) addCardText(previewCard, "• 智聊AI配置", 13);
    root.addView(previewCard);

    AlertDialog dialog = buildCommonAlertDialog(
        activity,
        "💾 导出备份",
        wrapInScroll(activity, root),
        "确认导出",
        new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int which) {
                if (isExport) {
                    String backupInfo = infoEdit.getText().toString().trim();
                    performExportBackup(backupInfo);
                }
            }
        },
        "取消",
        null,
        null,
        null
    );
    dialog.show();
}

/**
 * 显示导入文件选择对话框
 */
private void showImportFileSelectDialog() {
    String defaultPath = getWechatSpecificBackupPath();
    File lastFolder = new File(defaultPath);

    if (!lastFolder.exists()) {
        lastFolder = new File(getStoragePathPrefix());
    }

    browseFolderForSelectionAuto(lastFolder, BACKUP_FILE_EXTENSION, "", new MediaSelectionCallback() {
    public void onSelected(ArrayList<String> selectedFiles) {
        if (selectedFiles == null || selectedFiles.isEmpty()) {
            toast("未选择任何文件");
            return;
        }
        String filePath = selectedFiles.get(0);
        if (!filePath.endsWith(BACKUP_FILE_EXTENSION)) {
            toast("请选择" + BACKUP_FILE_EXTENSION + "格式的备份文件");
            return;
        }

        // 记住当前位置（导入）
        try {
            File f = new File(filePath);
            File p = f.getParentFile();
            if (p != null) saveWechatSpecificBackupPath(p.getAbsolutePath());
        } catch (Exception ignore) {}

        performImportBackup(filePath);
    }
}, false);
}

// =================== END: 备份功能入口 ===================

// =================== START: 备份核心逻辑 ===================

/**
 * 执行导出备份操作（使用中文键名，易读格式）
 */

private String escapeJsonString(String str) {
    if (str == null) return "";
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < str.length(); i++) {
        char c = str.charAt(i);
        switch (c) {
            case '"': sb.append("\\\""); break;
            case '\\': sb.append("\\\\"); break;
            case '\n': sb.append("\\n"); break;
            case '\r': sb.append("\\r"); break;
            case '\t': sb.append("\\t"); break;
            default: sb.append(c);
        }
    }
    return sb.toString();
}

private Map<String, Object> readableJsonToRuleMap(JSONObject obj) {
    try {
        // 1) 优先兼容：如果有原始规则串，直接走原解析，最稳
        String raw = obj.getString("原始规则串");
        if (!TextUtils.isEmpty(raw)) {
            Map<String, Object> parsed = ruleFromString(raw);
            if (parsed != null) return parsed;
        }

        // 2) 兜底：按中文字段解析
        String keyword = obj.getString("关键词");
        String reply = obj.getString("文本回复内容");
        boolean enabled = obj.getBooleanValue("是否启用");

        int matchType = MATCH_TYPE_FUZZY;
        String matchTypeStr = obj.getString("匹配方式");
        if ("全字匹配".equals(matchTypeStr)) matchType = MATCH_TYPE_EXACT;
        else if ("正则匹配".equals(matchTypeStr)) matchType = MATCH_TYPE_REGEX;
        else if ("任意消息".equals(matchTypeStr)) matchType = MATCH_TYPE_ANY;

        int replyType = REPLY_TYPE_TEXT;
        String replyTypeStr = obj.getString("回复类型");
        if ("图片".equals(replyTypeStr)) replyType = REPLY_TYPE_IMAGE;
        else if ("语音(列表)".equals(replyTypeStr)) replyType = REPLY_TYPE_VOICE_FILE_LIST;
        else if ("语音(文件夹随机)".equals(replyTypeStr)) replyType = REPLY_TYPE_VOICE_FOLDER;
        else if ("表情".equals(replyTypeStr)) replyType = REPLY_TYPE_EMOJI;
        else if ("小智AI".equals(replyTypeStr)) replyType = REPLY_TYPE_XIAOZHI_AI;
        else if ("视频".equals(replyTypeStr)) replyType = REPLY_TYPE_VIDEO;
        else if ("名片".equals(replyTypeStr)) replyType = REPLY_TYPE_CARD;
        else if ("文件".equals(replyTypeStr)) replyType = REPLY_TYPE_FILE;
        else if ("智聊AI".equals(replyTypeStr)) replyType = REPLY_TYPE_ZHILIA_AI;
        else if ("邀请群聊".equals(replyTypeStr)) replyType = REPLY_TYPE_INVITE_GROUP;

        int targetType = TARGET_TYPE_NONE;
        String targetTypeStr = obj.getString("生效目标");
        if ("指定好友".equals(targetTypeStr)) targetType = TARGET_TYPE_FRIEND;
        else if ("指定群聊".equals(targetTypeStr)) targetType = TARGET_TYPE_GROUP;
        else if ("好友和群聊".equals(targetTypeStr)) targetType = TARGET_TYPE_BOTH;

        int atTriggerType = AT_TRIGGER_NONE;
        String atStr = obj.getString("@触发");
        if ("@我触发".equals(atStr)) atTriggerType = AT_TRIGGER_ME;
        else if ("@全体触发".equals(atStr)) atTriggerType = AT_TRIGGER_ALL;

        int patTriggerType = PAT_TRIGGER_NONE;
        String patStr = obj.getString("拍一拍触发");
        if ("被拍一拍触发".equals(patStr)) patTriggerType = PAT_TRIGGER_ME;

        long delaySeconds = obj.getLongValue("延迟回复秒");
        long mediaDelaySeconds = obj.getLongValue("媒体发送间隔秒");
        if (mediaDelaySeconds <= 0) mediaDelaySeconds = 1L;

        boolean replyAsQuote = obj.getBooleanValue("是否引用回复");
        String startTime = obj.getString("开始时间");
        String endTime = obj.getString("结束时间");

        Set targetWxids = new HashSet();
        JSONArray targetArr = obj.getJSONArray("指定目标Wxid");
        if (targetArr != null) {
            for (int i = 0; i < targetArr.size(); i++) {
                String v = targetArr.getString(i);
                if (!TextUtils.isEmpty(v)) targetWxids.add(v);
            }
        }

        Set excludedWxids = new HashSet();
        JSONArray exArr = obj.getJSONArray("排除目标Wxid");
        if (exArr != null) {
            for (int i = 0; i < exArr.size(); i++) {
                String v = exArr.getString(i);
                if (!TextUtils.isEmpty(v)) excludedWxids.add(v);
            }
        }

        Set excludedGroupMemberWxids = new HashSet();
        JSONArray exMemArr = obj.getJSONArray("排除群成员Wxid");
        if (exMemArr != null) {
            for (int i = 0; i < exMemArr.size(); i++) {
                String v = exMemArr.getString(i);
                if (!TextUtils.isEmpty(v)) excludedGroupMemberWxids.add(v);
            }
        }

        Set includedGroupMemberWxids = new HashSet();
        JSONArray inMemArr = obj.getJSONArray("指定群成员Wxid");
        if (inMemArr != null) {
            for (int i = 0; i < inMemArr.size(); i++) {
                String v = inMemArr.getString(i);
                if (!TextUtils.isEmpty(v)) includedGroupMemberWxids.add(v);
            }
        }

        Set excludedGroupIdsForMemberFilter = new HashSet();
        JSONArray exGArr = obj.getJSONArray("排除成员过滤群ID");
        if (exGArr != null) {
            for (int i = 0; i < exGArr.size(); i++) {
                String v = exGArr.getString(i);
                if (!TextUtils.isEmpty(v)) excludedGroupIdsForMemberFilter.add(v);
            }
        }

        Set includedGroupIdsForMemberFilter = new HashSet();
        JSONArray inGArr = obj.getJSONArray("指定成员过滤群ID");
        if (inGArr != null) {
            for (int i = 0; i < inGArr.size(); i++) {
                String v = inGArr.getString(i);
                if (!TextUtils.isEmpty(v)) includedGroupIdsForMemberFilter.add(v);
            }
        }

        List mediaPaths = new ArrayList();
        JSONArray mediaArr = obj.getJSONArray("媒体路径列表");
        if (mediaArr != null) {
            for (int i = 0; i < mediaArr.size(); i++) {
                String v = mediaArr.getString(i);
                if (!TextUtils.isEmpty(v)) mediaPaths.add(v);
            }
        }

        Map<String, Object> rule = createAutoReplyRuleMap(
            keyword == null ? "" : keyword,
            reply == null ? "" : reply,
            enabled,
            matchType,
            targetWxids,
            targetType,
            atTriggerType,
            delaySeconds,
            replyAsQuote,
            replyType,
            mediaPaths,
            startTime == null ? "" : startTime,
            endTime == null ? "" : endTime,
            excludedWxids,
            mediaDelaySeconds,
            patTriggerType,
            excludedGroupMemberWxids,
            includedGroupMemberWxids,
            excludedGroupIdsForMemberFilter,
            includedGroupIdsForMemberFilter
        );
        compileRegexPatternForRule(rule);
        return rule;
    } catch (Exception e) {
        debugLog("[异常] readableJsonToRuleMap 解析失败: " + e.getMessage());
        return null;
    }
}

private Object readableJsonToAcceptReplyItem(JSONObject obj) {
    try {
        // 优先兼容原始项
        String raw = obj.getString("原始项");
        if (!TextUtils.isEmpty(raw)) {
            Object p = AcceptReplyItem.fromString(raw);
            if (p != null) return p;
        }

        int type = obj.getIntValue("类型");
        String content = obj.getString("内容");
        long mediaDelay = obj.getLongValue("媒体间隔秒");
        if (mediaDelay <= 0) mediaDelay = 1L;
        return new AcceptReplyItem(type, content == null ? "" : content, mediaDelay);
    } catch (Exception e) {
        debugLog("[异常] readableJsonToAcceptReplyItem 解析失败: " + e.getMessage());
        return null;
    }
}

private String getReadableMatchType(int matchType) {
    if (matchType == MATCH_TYPE_EXACT) return "全字匹配";
    if (matchType == MATCH_TYPE_REGEX) return "正则匹配";
    if (matchType == MATCH_TYPE_ANY) return "任意消息";
    return "模糊匹配";
}

private String getReadableReplyType(int replyType) {
    switch (replyType) {
        case REPLY_TYPE_IMAGE: return "图片";
        case REPLY_TYPE_VOICE_FILE_LIST: return "语音(列表)";
        case REPLY_TYPE_VOICE_FOLDER: return "语音(文件夹随机)";
        case REPLY_TYPE_EMOJI: return "表情";
        case REPLY_TYPE_XIAOZHI_AI: return "小智AI";
        case REPLY_TYPE_VIDEO: return "视频";
        case REPLY_TYPE_CARD: return "名片";
        case REPLY_TYPE_FILE: return "文件";
        case REPLY_TYPE_ZHILIA_AI: return "智聊AI";
        case REPLY_TYPE_INVITE_GROUP: return "邀请群聊";
        default: return "文本";
    }
}

private String getReadableTargetType(int targetType) {
    if (targetType == TARGET_TYPE_FRIEND) return "指定好友";
    if (targetType == TARGET_TYPE_GROUP) return "指定群聊";
    if (targetType == TARGET_TYPE_BOTH) return "好友和群聊";
    return "不指定";
}

private String getReadableAtType(int atType) {
    if (atType == AT_TRIGGER_ME) return "@我触发";
    if (atType == AT_TRIGGER_ALL) return "@全体触发";
    return "不限@触发";
}

private String getReadablePatType(int patType) {
    if (patType == PAT_TRIGGER_ME) return "被拍一拍触发";
    return "不限拍一拍";
}

private JSONObject ruleMapToReadableJsonSafe(Map<String, Object> rule) {
    JSONObject o = new JSONObject();

    String keyword = (String) rule.get("keyword");
    String reply = (String) rule.get("reply");
    boolean enabled = (Boolean) rule.get("enabled");
    int matchType = (Integer) rule.get("matchType");
    int targetType = (Integer) rule.get("targetType");
    int atType = (Integer) rule.get("atTriggerType");
    int patType = (Integer) rule.get("patTriggerType");
    long delay = (Long) rule.get("delaySeconds");
    long mediaDelay = (Long) rule.get("mediaDelaySeconds");
    boolean quote = (Boolean) rule.get("replyAsQuote");
    int replyType = (Integer) rule.get("replyType");
    String startTime = (String) rule.get("startTime");
    String endTime = (String) rule.get("endTime");

    Set targetWxids = (Set) rule.get("targetWxids");
    Set excludedWxids = (Set) rule.get("excludedWxids");
    Set excludedGroupMemberWxids = (Set) rule.get("excludedGroupMemberWxids");
    Set includedGroupMemberWxids = (Set) rule.get("includedGroupMemberWxids");
    Set excludedGroupIdsForMemberFilter = (Set) rule.get("excludedGroupIdsForMemberFilter");
    Set includedGroupIdsForMemberFilter = (Set) rule.get("includedGroupIdsForMemberFilter");
    List mediaPaths = (List) rule.get("mediaPaths");

    o.put("是否启用", enabled);
    o.put("关键词", keyword);
    o.put("匹配方式", getReadableMatchType(matchType));
    o.put("回复类型", getReadableReplyType(replyType));
    o.put("文本回复内容", reply);
    o.put("生效目标", getReadableTargetType(targetType));
    o.put("@触发", getReadableAtType(atType));
    o.put("拍一拍触发", getReadablePatType(patType));
    o.put("延迟回复秒", delay);
    o.put("媒体发送间隔秒", mediaDelay);
    o.put("是否引用回复", quote);
    o.put("开始时间", startTime);
    o.put("结束时间", endTime);
    o.put("媒体路径列表", mediaPaths == null ? new JSONArray() : mediaPaths);
    o.put("指定目标Wxid", targetWxids == null ? new JSONArray() : targetWxids);
    o.put("排除目标Wxid", excludedWxids == null ? new JSONArray() : excludedWxids);
    o.put("排除群成员Wxid", excludedGroupMemberWxids == null ? new JSONArray() : excludedGroupMemberWxids);
    o.put("指定群成员Wxid", includedGroupMemberWxids == null ? new JSONArray() : includedGroupMemberWxids);
    o.put("排除成员过滤群ID", excludedGroupIdsForMemberFilter == null ? new JSONArray() : excludedGroupIdsForMemberFilter);
    o.put("指定成员过滤群ID", includedGroupIdsForMemberFilter == null ? new JSONArray() : includedGroupIdsForMemberFilter);

    // 保留原始串，方便兼容回滚

    return o;
}

private void performExportBackup(String backupInfo) {
    try {
        BackupData backupData = new BackupData();
        backupData.backupInfo = backupInfo;
        BackupConfig config = backupData.config;

        if (getBoolean(BACKUP_AUTO_REPLY_ENABLED_KEY, true)) {
            config.autoReplyEnabled = getBoolean(AUTO_REPLY_RULES_KEY + "_enabled", false);
            Set rulesSet = getStringSet(AUTO_REPLY_RULES_KEY, new HashSet());
            config.autoReplyRules = new ArrayList<String>();
            for (Object s : rulesSet) config.autoReplyRules.add((String) s);
        }

        if (getBoolean(BACKUP_AUTO_ACCEPT_ENABLED_KEY, true)) {
            config.autoAcceptEnabled = getBoolean(AUTO_ACCEPT_FRIEND_ENABLED_KEY, false);
            config.autoAcceptDelay = getInt(AUTO_ACCEPT_DELAY_KEY, 0);
            String items = getString(AUTO_ACCEPT_REPLY_ITEMS_KEY, "");
            config.autoAcceptReplyItems = new ArrayList<String>();
            if (!TextUtils.isEmpty(items)) {
                String[] arr = items.split(LIST_SEPARATOR);
                for (String it : arr) if (!TextUtils.isEmpty(it.trim())) config.autoAcceptReplyItems.add(it.trim());
            }
        }

        if (getBoolean(BACKUP_GREET_ACCEPTED_ENABLED_KEY, true)) {
            config.greetOnAcceptedEnabled = getBoolean(GREET_ON_ACCEPTED_ENABLED_KEY, false);
            config.greetOnAcceptedDelay = getInt(GREET_ON_ACCEPTED_DELAY_KEY, 0);
            String items = getString(GREET_ON_ACCEPTED_REPLY_ITEMS_KEY, "");
            config.greetOnAcceptedReplyItems = new ArrayList<String>();
            if (!TextUtils.isEmpty(items)) {
                String[] arr = items.split(LIST_SEPARATOR);
                for (String it : arr) if (!TextUtils.isEmpty(it.trim())) config.greetOnAcceptedReplyItems.add(it.trim());
            }
        }

        if (getBoolean(BACKUP_XIAOZHI_ENABLED_KEY, true)) {
            config.xiaozhiServeUrl = getString(XIAOZHI_CONFIG_KEY, XIAOZHI_SERVE_KEY, "wss://api.tenclass.net/xiaozhi/v1/");
            config.xiaozhiOtaUrl = getString(XIAOZHI_CONFIG_KEY, XIAOZHI_OTA_KEY, "https://api.tenclass.net/xiaozhi/ota/");
            config.xiaozhiConsoleUrl = getString(XIAOZHI_CONFIG_KEY, XIAOZHI_CONSOLE_KEY, "https://xiaozhi.me/console/agents");
        }

        if (getBoolean(BACKUP_ZHILIA_ENABLED_KEY, true)) {
            config.zhiliaApiKey = getString(ZHILIA_AI_API_KEY, "");
            config.zhiliaApiUrl = getString(ZHILIA_AI_API_URL, "https://api.siliconflow.cn/v1/chat/completions");
            config.zhiliaModelName = getString(ZHILIA_AI_MODEL_NAME, "deepseek-ai/DeepSeek-V3");
            config.zhiliaSystemPrompt = getString(ZHILIA_AI_SYSTEM_PROMPT, "你是个宝宝");
            config.zhiliaContextLimit = getInt(ZHILIA_AI_CONTEXT_LIMIT, 10);
        }

        config.logEnabled = getBoolean(ENABLE_LOG_KEY, true);

        JSONObject root = new JSONObject();
        root.put("文件标识", backupData.magic);
        root.put("备份版本", backupData.version);
        root.put("备份时间戳", backupData.backupTime);
        root.put("备份时间", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(backupData.backupTime)));
        root.put("备份说明", backupData.backupInfo);
        root.put("微信账号", getLoginWxid());
        root.put("微信类型", getWechatTypeDesc());
        root.put("存储路径", getStoragePathPrefix());

        JSONObject 内容 = new JSONObject();

        if (config.autoReplyRules != null) {
            JSONObject 自动回复 = new JSONObject();
            自动回复.put("是否启用", config.autoReplyEnabled);

            JSONArray 规则列表 = new JSONArray();
            List rules = loadAutoReplyRules();
            for (int i = 0; i < rules.size(); i++) {
                Map<String, Object> rule = (Map<String, Object>) rules.get(i);
                规则列表.add(ruleMapToReadableJsonSafe(rule));
            }
            自动回复.put("规则列表", 规则列表);
            内容.put("自动回复", 自动回复);
        }

        if (config.autoAcceptReplyItems != null || getBoolean(BACKUP_AUTO_ACCEPT_ENABLED_KEY, true)) {
            JSONObject 自动同意好友 = new JSONObject();
            自动同意好友.put("是否启用", config.autoAcceptEnabled);
            自动同意好友.put("延迟秒数", config.autoAcceptDelay);

            JSONArray 回复内容 = new JSONArray();
            if (config.autoAcceptReplyItems != null) {
                for (int i = 0; i < config.autoAcceptReplyItems.size(); i++) {
                    AcceptReplyItem item = AcceptReplyItem.fromString(config.autoAcceptReplyItems.get(i));
                    if (item != null) {
                        JSONObject itemJson = new JSONObject();
                        itemJson.put("类型", item.type);
                        itemJson.put("内容", item.content);
                        itemJson.put("媒体间隔秒", item.mediaDelaySeconds);
                        itemJson.put("原始项", item.toString());
                        回复内容.add(itemJson);
                    }
                }
            }
            自动同意好友.put("回复内容", 回复内容);
            内容.put("自动同意好友", 自动同意好友);
        }

        if (config.greetOnAcceptedReplyItems != null || getBoolean(BACKUP_GREET_ACCEPTED_ENABLED_KEY, true)) {
            JSONObject 好友通过回复 = new JSONObject();
            好友通过回复.put("是否启用", config.greetOnAcceptedEnabled);
            好友通过回复.put("延迟秒数", config.greetOnAcceptedDelay);

            JSONArray 回复内容 = new JSONArray();
            if (config.greetOnAcceptedReplyItems != null) {
                for (int i = 0; i < config.greetOnAcceptedReplyItems.size(); i++) {
                    AcceptReplyItem item = AcceptReplyItem.fromString(config.greetOnAcceptedReplyItems.get(i));
                    if (item != null) {
                        JSONObject itemJson = new JSONObject();
                        itemJson.put("类型", item.type);
                        itemJson.put("内容", item.content);
                        itemJson.put("媒体间隔秒", item.mediaDelaySeconds);
                        itemJson.put("原始项", item.toString());
                        回复内容.add(itemJson);
                    }
                }
            }
            好友通过回复.put("回复内容", 回复内容);
            内容.put("好友通过回复", 好友通过回复);
        }

        if (config.xiaozhiServeUrl != null) {
            JSONObject 小智AI = new JSONObject();
            小智AI.put("服务地址", config.xiaozhiServeUrl);
            小智AI.put("OTA地址", config.xiaozhiOtaUrl);
            小智AI.put("控制台地址", config.xiaozhiConsoleUrl);
            内容.put("小智AI", 小智AI);
        }

        if (config.zhiliaApiKey != null) {
            JSONObject 智聊AI = new JSONObject();
            智聊AI.put("API密钥", config.zhiliaApiKey);
            智聊AI.put("API地址", config.zhiliaApiUrl);
            智聊AI.put("模型名称", config.zhiliaModelName);
            智聊AI.put("系统提示", config.zhiliaSystemPrompt);
            智聊AI.put("上下文限制", config.zhiliaContextLimit);
            内容.put("智聊AI", 智聊AI);

        // 额外导出：智聊AI多配置
        try {
            ensureZhiliaDefaultMigrated();
            JSONObject 多配置 = getZhiliaAllConfigs();
            内容.put("智聊AI配置列表", 多配置);
            内容.put("智聊AI当前配置名", getString(ZHILIA_ACTIVE_CONFIG_NAME_KEY, "默认配置"));
        } catch (Exception ignore) {}
        }

        JSONObject 日志设置 = new JSONObject();
        日志设置.put("是否启用", config.logEnabled);
        内容.put("日志设置", 日志设置);

        root.put("配置内容", 内容);

        // 强制漂亮分行
        String pretty = JSON.toJSONString(root, com.alibaba.fastjson2.JSONWriter.Feature.PrettyFormat);

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileName = "wauxv_backup_" + timestamp + BACKUP_FILE_EXTENSION;
        String savePath = getWechatSpecificBackupPath();
        final String fullPath = new File(savePath, fileName).getAbsolutePath();

        showExportPathConfirmDialog(fullPath, pretty);

    } catch (Exception e) {
        toast("导出失败: " + e.getMessage());
        debugLog("[备份] 导出失败: " + e.getMessage());
    }
}

private void showExportPathConfirmDialog(final String filePath, final String backupContent) {
    Activity activity = getTopActivity();
    if (activity == null) return;

    AlertDialog.Builder builder = new AlertDialog.Builder(activity);
    builder.setTitle("💾 确认导出");

    LinearLayout contentLayout = new LinearLayout(activity);
    contentLayout.setOrientation(LinearLayout.VERTICAL);
    contentLayout.setPadding(32, 24, 32, 24);

    TextView wechatTypeLabel = new TextView(activity);
    wechatTypeLabel.setText("当前微信: " + getWechatTypeDesc());
    wechatTypeLabel.setTextSize(13);
    wechatTypeLabel.setTextColor(isMainWechat() ? Color.parseColor("#4CAF50") : Color.parseColor("#FF9800"));
    wechatTypeLabel.setPadding(0, 0, 0, 8);
    contentLayout.addView(wechatTypeLabel);

    TextView storageLabel = new TextView(activity);
    storageLabel.setText("存储路径: " + getStoragePathPrefix());
    storageLabel.setTextSize(12);
    storageLabel.setTextColor(Color.parseColor("#888888"));
    storageLabel.setPadding(0, 0, 0, 8);
    contentLayout.addView(storageLabel);

    TextView pathLabel = new TextView(activity);
    pathLabel.setText("保存路径：");
    pathLabel.setTextSize(14);
    pathLabel.setTextColor(Color.parseColor("#666666"));
    contentLayout.addView(pathLabel);

    TextView pathView = new TextView(activity);
    pathView.setText(filePath);
    pathView.setTextSize(13);
    pathView.setTextColor(Color.parseColor("#333333"));
    pathView.setPadding(0, 8, 0, 16);
    contentLayout.addView(pathView);

    builder.setView(contentLayout);

    builder.setPositiveButton("确认保存", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
            saveBackupToFile(filePath, backupContent);
            saveWechatSpecificBackupPath(new File(filePath).getParent());
        }
    });

    builder.setNeutralButton("选择其他位置", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
            String defaultPath = getWechatSpecificBackupPath();
            File lastFolder = new File(defaultPath);
            if (!lastFolder.exists()) {
                lastFolder = new File(getStoragePathPrefix());
            }

            browseFolderForSelectionAuto(lastFolder, "", "", new MediaSelectionCallback() {
                public void onSelected(ArrayList<String> selectedFiles) {
                    if (!selectedFiles.isEmpty()) {
                        String newPath = selectedFiles.get(0);
                        saveWechatSpecificBackupPath(newPath);

                        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                        String fileName = "wauxv_backup_" + timestamp + BACKUP_FILE_EXTENSION;
                        String fullPath = newPath + "/" + fileName;

                        showExportPathConfirmDialog(fullPath, backupContent);
                    }
                }
            }, true);
        }
    });

    builder.setNegativeButton("取消", null);

    final AlertDialog dialog = builder.create();
    dialog.setOnShowListener(new DialogInterface.OnShowListener() {
        public void onShow(DialogInterface d) {
            setupUnifiedDialog(dialog);
        }
    });
    dialog.show();
}
/**
 * 执行导入备份操作（支持易读格式和旧格式）
 */
private void performImportBackup(String filePath) {
    try {
        File file = new File(filePath);
        if (!file.exists()) {
            toast("备份文件不存在");
            return;
        }

        java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(file));
        StringBuilder content = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            content.append(line).append("\n");
        }
        reader.close();

        JSONObject backupJson = JSON.parseObject(content.toString());

        String magic = backupJson.getString("文件标识");
        if (magic == null) {
            magic = backupJson.getString("magic");
        }
        if (!BACKUP_MAGIC_HEADER.equals(magic)) {
            toast("无效的备份文件格式");
            return;
        }

        int version = backupJson.getIntValue("备份版本");
        if (version == 0) {
            version = backupJson.getIntValue("version");
        }
        if (version > BACKUP_VERSION) {
            toast("备份文件版本过高，无法导入");
            return;
        }

        long backupTime = 0;
        if (backupJson.containsKey("备份时间戳")) {
            backupTime = backupJson.getLongValue("备份时间戳");
        }
        if (backupTime == 0 && backupJson.containsKey("backupTime")) {
            backupTime = backupJson.getLongValue("backupTime");
        }
        if (backupTime == 0) {
            String timeStr = backupJson.getString("备份时间");
            if (!TextUtils.isEmpty(timeStr)) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                    Date date = sdf.parse(timeStr);
                    if (date != null) {
                        backupTime = date.getTime();
                    }
                } catch (Exception e) {
                    debugLog("[异常] 解析备份时间失败: " + e.getMessage());
                }
            }
        }

        String backupInfo = backupJson.getString("备份说明");
        if (backupInfo == null) {
            backupInfo = backupJson.getString("backupInfo");
        }
        String backupTimeStr = backupTime > 0 ?
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(backupTime)) :
            "未知时间";

        showImportConfirmDialog(filePath, backupJson, backupTimeStr, backupInfo);

    } catch (Exception e) {
        debugLog("[异常] 读取备份文件失败: " + e.getMessage());
        toast("读取备份文件失败: " + e.getMessage());
    }
}

private void showImportConfirmDialog(final String filePath, final JSONObject backupJson,
                                      String backupTime, String backupInfo) {
    Activity activity = getTopActivity();
    if (activity == null) return;

    ScrollView scrollView = new ScrollView(activity);
    LinearLayout layout = new LinearLayout(activity);
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setPadding(24, 24, 24, 24);
    layout.setBackgroundColor(Color.parseColor("#FAFBF9"));
    scrollView.addView(layout);

    LinearLayout infoCard = createCardLayout();
    infoCard.addView(createSectionTitle("备份信息"));
    infoCard.addView(createTextView(activity, "备份时间: " + backupTime, 13, 0));
    if (!TextUtils.isEmpty(backupInfo)) {
        infoCard.addView(createTextView(activity, "备份说明: " + backupInfo, 13, 0));
    }
    String backupWxid = backupJson.getString("微信账号");
    String backupWechatType = backupJson.getString("微信类型");
    String backupStorage = backupJson.getString("存储路径");
    if (!TextUtils.isEmpty(backupWxid)) {
        infoCard.addView(createTextView(activity, "备份账号: " + backupWxid, 13, 0));
    }
    if (!TextUtils.isEmpty(backupWechatType)) {
        infoCard.addView(createTextView(activity, "账号类型: " + backupWechatType, 13, 0));
    }
    if (!TextUtils.isEmpty(backupStorage)) {
        infoCard.addView(createTextView(activity, "存储路径: " + backupStorage, 13, 0));
    }
    layout.addView(infoCard);

    JSONObject configJson = backupJson.getJSONObject("配置内容");
    if (configJson == null) {
        configJson = backupJson.getJSONObject("config");
    }
    LinearLayout previewCard = createCardLayout();
    previewCard.addView(createSectionTitle("备份内容："));

    if (configJson != null) {
        if (configJson.containsKey("自动回复") || configJson.containsKey("autoReplyRules")) {
            int ruleCount = 0;
            if (configJson.containsKey("自动回复")) {
                Object rulesObj = configJson.get("自动回复");
                if (rulesObj instanceof JSONObject) {
                    JSONArray rulesArray = ((JSONObject) rulesObj).getJSONArray("规则列表");
                    if (rulesArray != null) ruleCount = rulesArray.size();
                }
            } else {
                JSONArray rulesArray = configJson.getJSONArray("autoReplyRules");
                if (rulesArray != null) ruleCount = rulesArray.size();
            }
            previewCard.addView(createTextView(activity, "• 自动回复规则 (" + ruleCount + "条)", 13, 0));
        }
        if (configJson.containsKey("自动同意好友") || configJson.containsKey("autoAcceptEnabled")) {
            previewCard.addView(createTextView(activity, "• 自动同意好友配置", 13, 0));
        }
        if (configJson.containsKey("好友通过回复") || configJson.containsKey("greetOnAcceptedEnabled")) {
            previewCard.addView(createTextView(activity, "• 好友通过回复配置", 13, 0));
        }
        if (configJson.containsKey("小智AI") || configJson.containsKey("xiaozhiServeUrl")) {
            previewCard.addView(createTextView(activity, "• 小智AI配置", 13, 0));
        }
        if (configJson.containsKey("智聊AI") || configJson.containsKey("zhiliaApiKey")) {
            previewCard.addView(createTextView(activity, "• 智聊AI配置", 13, 0));
        }
    }

    layout.addView(previewCard);

    LinearLayout warningCard = createCardLayout();
    warningCard.setBackgroundColor(Color.parseColor("#FFF3E0"));
    TextView warningView = new TextView(activity);
    warningView.setText("⚠️ 导入备份将覆盖现有配置，请确认是否继续？");
    warningView.setTextSize(13);
    warningView.setTextColor(Color.parseColor("#E65100"));
    warningView.setPadding(16, 16, 16, 16);
    warningCard.addView(warningView);
    layout.addView(warningCard);

    LinearLayout optionsCard = createCardLayout();
    optionsCard.addView(createSectionTitle("导入选项"));

    final ListView optionsListView = new ListView(activity);
    setupListViewTouchForScroll(optionsListView);
    optionsListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
    LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    );
    optionsListView.setLayoutParams(listParams);

    final ArrayList<String> optionNames = new ArrayList<String>();
    optionNames.add("覆盖自动回复规则");
    optionNames.add("覆盖自动同意好友配置");
    optionNames.add("覆盖好友通过回复配置");
    optionNames.add("覆盖小智AI配置");
    optionNames.add("覆盖智聊AI配置");

    ArrayAdapter<String> optionsAdapter = new ArrayAdapter<String>(activity,
        android.R.layout.simple_list_item_multiple_choice, optionNames);
    optionsListView.setAdapter(optionsAdapter);

    for (int i = 0; i < optionNames.size(); i++) {
        optionsListView.setItemChecked(i, true);
    }

    adjustListViewHeight(optionsListView, optionNames.size());

    optionsCard.addView(optionsListView);
    layout.addView(optionsCard);

    final AlertDialog dialog = buildCommonAlertDialog(
        activity,
        "💾 确认导入",
        scrollView,
        "确认导入",
        new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                doImportBackup(backupJson, filePath,
                    optionsListView.isItemChecked(0),
                    optionsListView.isItemChecked(1),
                    optionsListView.isItemChecked(2),
                    optionsListView.isItemChecked(3),
                    optionsListView.isItemChecked(4));
            }
        },
        "取消",
        null,
        null,
        null
    );

    dialog.show();
}

/**
 * 执行实际的导入操作（支持易读格式和旧格式）
 */
private void doImportBackup(JSONObject backupJson, String filePath,
                            boolean importAutoReply,
                            boolean importAutoAccept,
                            boolean importGreetAccepted,
                            boolean importXiaozhi,
                            boolean importZhilia) {
    try {
        JSONObject configJson = backupJson.getJSONObject("配置内容");
        if (configJson == null) {
            configJson = backupJson.getJSONObject("config");
        }

        int importedCount = 0;

        // 导入自动回复规则
        if (importAutoReply && configJson != null) {
            boolean autoReplyEnabled = false;
            List rules = new ArrayList();

            // 尝试中文格式
            if (configJson.containsKey("自动回复")) {
                Object autoReplyObj = configJson.get("自动回复");
                if (autoReplyObj instanceof JSONObject) {
                    JSONObject autoReplyJson = (JSONObject) autoReplyObj;
                    autoReplyEnabled = autoReplyJson.getBooleanValue("是否启用");
                    JSONArray rulesArray = autoReplyJson.getJSONArray("规则列表");
                    if (rulesArray != null) {
                        for (int i = 0; i < rulesArray.size(); i++) {
                            Object ruleObj = rulesArray.get(i);
                            if (ruleObj instanceof JSONObject) {
                                // 易读格式
                                Map<String, Object> rule = readableJsonToRuleMap((JSONObject) ruleObj);
                                if (rule != null) rules.add(rule);
                            } else if (ruleObj instanceof String) {
                                // 旧格式字符串
                                Map<String, Object> rule = ruleFromString((String) ruleObj);
                                if (rule != null) rules.add(rule);
                            }
                        }
                    }
                }
            }
            // 尝试英文格式（旧格式）
            else if (configJson.containsKey("autoReplyRules")) {
                autoReplyEnabled = configJson.getBooleanValue("autoReplyEnabled");
                JSONArray rulesArray = configJson.getJSONArray("autoReplyRules");
                if (rulesArray != null) {
                    for (int i = 0; i < rulesArray.size(); i++) {
                        Map<String, Object> rule = ruleFromString(rulesArray.getString(i));
                        if (rule != null) rules.add(rule);
                    }
                }
            }

            if (!rules.isEmpty()) {
                saveAutoReplyRules(rules);
                putBoolean(AUTO_REPLY_RULES_KEY + "_enabled", autoReplyEnabled);
                importedCount++;
                debugLog("[备份] 导入自动回复规则: " + rules.size() + "条");
            }
        }

        // 导入自动同意好友配置
        if (importAutoAccept && configJson != null) {
            if (configJson.containsKey("自动同意好友")) {
                Object autoAcceptObj = configJson.get("自动同意好友");
                if (autoAcceptObj instanceof JSONObject) {
                    JSONObject autoAcceptJson = (JSONObject) autoAcceptObj;
                    putBoolean(AUTO_ACCEPT_FRIEND_ENABLED_KEY, autoAcceptJson.getBooleanValue("是否启用"));
                    putInt(AUTO_ACCEPT_DELAY_KEY, autoAcceptJson.getIntValue("延迟秒数"));

                    JSONArray itemsArray = autoAcceptJson.getJSONArray("回复内容");
                    if (itemsArray != null) {
                        StringBuilder itemsStr = new StringBuilder();
                        for (int i = 0; i < itemsArray.size(); i++) {
                            Object itemObj = itemsArray.get(i);
                            if (itemObj instanceof JSONObject) {
                                // 易读格式
                                Object item = readableJsonToAcceptReplyItem((JSONObject) itemObj);
                                if (item != null) {
                                    if (i > 0) itemsStr.append(LIST_SEPARATOR);
                                    itemsStr.append(item.toString());
                                }
                            } else if (itemObj instanceof String) {
                                // 旧格式
                                if (i > 0) itemsStr.append(LIST_SEPARATOR);
                                itemsStr.append((String) itemObj);
                            }
                        }
                        putString(AUTO_ACCEPT_REPLY_ITEMS_KEY, itemsStr.toString());
                    }
                }
            } else if (configJson.containsKey("autoAcceptEnabled")) {
                putBoolean(AUTO_ACCEPT_FRIEND_ENABLED_KEY, configJson.getBooleanValue("autoAcceptEnabled"));
                putInt(AUTO_ACCEPT_DELAY_KEY, configJson.getIntValue("autoAcceptDelay"));
                if (configJson.containsKey("autoAcceptReplyItems")) {
                    JSONArray itemsArray = configJson.getJSONArray("autoAcceptReplyItems");
                    StringBuilder itemsStr = new StringBuilder();
                    for (int i = 0; i < itemsArray.size(); i++) {
                        if (i > 0) itemsStr.append(LIST_SEPARATOR);
                        itemsStr.append(itemsArray.getString(i));
                    }
                    putString(AUTO_ACCEPT_REPLY_ITEMS_KEY, itemsStr.toString());
                }
            }
            importedCount++;
            debugLog("[备份] 导入自动同意好友配置");
        }

        // 导入好友通过回复配置
        if (importGreetAccepted && configJson != null) {
            if (configJson.containsKey("好友通过回复")) {
                Object greetObj = configJson.get("好友通过回复");
                if (greetObj instanceof JSONObject) {
                    JSONObject greetJson = (JSONObject) greetObj;
                    putBoolean(GREET_ON_ACCEPTED_ENABLED_KEY, greetJson.getBooleanValue("是否启用"));
                    putInt(GREET_ON_ACCEPTED_DELAY_KEY, greetJson.getIntValue("延迟秒数"));

                    JSONArray itemsArray = greetJson.getJSONArray("回复内容");
                    if (itemsArray != null) {
                        StringBuilder itemsStr = new StringBuilder();
                        for (int i = 0; i < itemsArray.size(); i++) {
                            Object itemObj = itemsArray.get(i);
                            if (itemObj instanceof JSONObject) {
                                Object item = readableJsonToAcceptReplyItem((JSONObject) itemObj);
                                if (item != null) {
                                    if (i > 0) itemsStr.append(LIST_SEPARATOR);
                                    itemsStr.append(item.toString());
                                }
                            } else if (itemObj instanceof String) {
                                if (i > 0) itemsStr.append(LIST_SEPARATOR);
                                itemsStr.append((String) itemObj);
                            }
                        }
                        putString(GREET_ON_ACCEPTED_REPLY_ITEMS_KEY, itemsStr.toString());
                    }
                }
            } else if (configJson.containsKey("greetOnAcceptedEnabled")) {
                putBoolean(GREET_ON_ACCEPTED_ENABLED_KEY, configJson.getBooleanValue("greetOnAcceptedEnabled"));
                putInt(GREET_ON_ACCEPTED_DELAY_KEY, configJson.getIntValue("greetOnAcceptedDelay"));
                if (configJson.containsKey("greetOnAcceptedReplyItems")) {
                    JSONArray itemsArray = configJson.getJSONArray("greetOnAcceptedReplyItems");
                    StringBuilder itemsStr = new StringBuilder();
                    for (int i = 0; i < itemsArray.size(); i++) {
                        if (i > 0) itemsStr.append(LIST_SEPARATOR);
                        itemsStr.append(itemsArray.getString(i));
                    }
                    putString(GREET_ON_ACCEPTED_REPLY_ITEMS_KEY, itemsStr.toString());
                }
            }
            importedCount++;
            debugLog("[备份] 导入好友通过回复配置");
        }

        // 导入小智AI配置
        if (importXiaozhi && configJson != null) {
            if (configJson.containsKey("小智AI")) {
                Object xiaozhiObj = configJson.get("小智AI");
                if (xiaozhiObj instanceof JSONObject) {
                    JSONObject xiaozhiJson = (JSONObject) xiaozhiObj;
                    putString(XIAOZHI_CONFIG_KEY, XIAOZHI_SERVE_KEY, xiaozhiJson.getString("服务地址"));
                    putString(XIAOZHI_CONFIG_KEY, XIAOZHI_OTA_KEY, xiaozhiJson.getString("OTA地址"));
                    putString(XIAOZHI_CONFIG_KEY, XIAOZHI_CONSOLE_KEY, xiaozhiJson.getString("控制台地址"));
                }
            } else if (configJson.containsKey("xiaozhiServeUrl")) {
                putString(XIAOZHI_CONFIG_KEY, XIAOZHI_SERVE_KEY, configJson.getString("xiaozhiServeUrl"));
                putString(XIAOZHI_CONFIG_KEY, XIAOZHI_OTA_KEY, configJson.getString("xiaozhiOtaUrl"));
                putString(XIAOZHI_CONFIG_KEY, XIAOZHI_CONSOLE_KEY, configJson.getString("xiaozhiConsoleUrl"));
            }
            importedCount++;
            debugLog("[备份] 导入小智AI配置");
        }

        // 导入智聊AI多配置（优先）
        if (importZhilia && configJson != null) {
            try {
                JSONObject multi = configJson.getJSONObject("智聊AI配置列表");
                String activeName = configJson.getString("智聊AI当前配置名");
                if (multi != null && !multi.isEmpty()) {
                    saveZhiliaAllConfigs(multi);
                    if (!TextUtils.isEmpty(activeName)) {
                        putString(ZHILIA_ACTIVE_CONFIG_NAME_KEY, activeName);
                    } else {
                        for (String k : multi.keySet()) {
                            putString(ZHILIA_ACTIVE_CONFIG_NAME_KEY, k);
                            break;
                        }
                    }
                    JSONObject act = getActiveZhiliaConfig();
                    syncLegacyZhiliaKeysFromConfig(act);
                    importedCount++;
                    debugLog("[备份] 导入智聊AI多配置");
                }
            } catch (Exception e) {
                debugLog("[异常] 导入智聊AI多配置失败: " + e.getMessage());
            }
        }

        // 导入智聊AI配置
        if (importZhilia && configJson != null) {
            if (configJson.containsKey("智聊AI")) {
                Object zhiliaObj = configJson.get("智聊AI");
                if (zhiliaObj instanceof JSONObject) {
                    JSONObject zhiliaJson = (JSONObject) zhiliaObj;
                    putString(ZHILIA_AI_API_KEY, zhiliaJson.getString("API密钥"));
                    putString(ZHILIA_AI_API_URL, zhiliaJson.getString("API地址"));
                    putString(ZHILIA_AI_MODEL_NAME, zhiliaJson.getString("模型名称"));
                    putString(ZHILIA_AI_SYSTEM_PROMPT, zhiliaJson.getString("系统提示"));
                    putInt(ZHILIA_AI_CONTEXT_LIMIT, zhiliaJson.getIntValue("上下文限制"));
                }
            } else if (configJson.containsKey("zhiliaApiKey")) {
                putString(ZHILIA_AI_API_KEY, configJson.getString("zhiliaApiKey"));
                putString(ZHILIA_AI_API_URL, configJson.getString("zhiliaApiUrl"));
                putString(ZHILIA_AI_MODEL_NAME, configJson.getString("zhiliaModelName"));
                putString(ZHILIA_AI_SYSTEM_PROMPT, configJson.getString("zhiliaSystemPrompt"));
                putInt(ZHILIA_AI_CONTEXT_LIMIT, configJson.getIntValue("zhiliaContextLimit"));
            }
            importedCount++;
            debugLog("[备份] 导入智聊AI配置");
        }

        // 导入日志开关
        if (configJson != null) {
            if (configJson.containsKey("日志设置")) {
                Object logObj = configJson.get("日志设置");
                if (logObj instanceof JSONObject) {
                    putBoolean(ENABLE_LOG_KEY, ((JSONObject) logObj).getBooleanValue("是否启用"));
                }
            } else if (configJson.containsKey("logEnabled")) {
                putBoolean(ENABLE_LOG_KEY, configJson.getBooleanValue("logEnabled"));
            }
        }

        toast("导入成功！共导入 " + importedCount + " 项配置");
        debugLog("[备份] 导入完成: " + filePath + ", 导入 " + importedCount + " 项");

    } catch (Exception e) {
        debugLog("[异常] 导入备份失败: " + e.getMessage());
        toast("导入失败: " + e.getMessage());
    }
}

// =================== END: 备份核心逻辑 ===================

private void saveBackupToFile(String filePath, String content) {
    try {
        File file = new File(filePath);
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            boolean mk = parentDir.mkdirs();
            debugLog("[备份] 创建目录: " + parentDir.getAbsolutePath() + " -> " + mk);
        }

        // 再校验一次目录
        if (parentDir != null && !parentDir.exists()) {
            throw new RuntimeException("目录创建失败: " + parentDir.getAbsolutePath());
        }

        java.io.FileWriter writer = new java.io.FileWriter(file);
        writer.write(content);
        writer.close();

        toast("备份已保存到:\n" + file.getAbsolutePath());
        debugLog("[备份] 导出成功: " + file.getAbsolutePath());

    } catch (Exception e) {
        debugLog("[异常] 保存备份文件失败: " + e.getMessage());
        toast("保存失败: " + e.getMessage());
    }
}

// =================== END: 新增统一日志打印控制 ===================

/* ========== 打开文件夹浏览器（单例模式） ========== */
void browseFolderForSelectionAuto(final File startFolder,
                                  final String wantedExtFilter,
                                  final String currentSelection,
                                  final MediaSelectionCallback callback,
                                  final boolean allowFolderSelect) {
    // 保存全局参数
    gWantedExtFilterAuto = wantedExtFilter;
    gCurrentSelectionAuto = currentSelection;
    gMediaCallbackAuto = callback;
    gAllowFolderSelectAuto = allowFolderSelect;
    gCurrentFolderAuto = startFolder;

    if (startFolder != null && startFolder.exists() && startFolder.isDirectory()) {
        putString(DEFAULT_LAST_FOLDER_SP_AUTO, startFolder.getAbsolutePath());
    }

    // 关闭旧弹窗
    if (gFolderDialogAuto != null && gFolderDialogAuto.isShowing()) {
        gFolderDialogAuto.dismiss();
        gFolderDialogAuto = null;
    }

    // 刷新数据
    refreshFolderListAuto(startFolder);

    AlertDialog.Builder builder = new AlertDialog.Builder(getTopActivity());
    builder.setTitle("浏览：" + startFolder.getAbsolutePath());

    gFolderAdapterAuto = new ArrayAdapter(getTopActivity(), android.R.layout.simple_list_item_1, gFolderNamesAuto);
    final ListView list = new ListView(getTopActivity());
    list.setAdapter(gFolderAdapterAuto);
    builder.setView(list);

    list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
            Object obj = gFolderFilesAuto.get(pos);
            if (!(obj instanceof File)) return;

            File selected = (File) obj;
            // 占位提示项不处理
            if (selected.equals(gCurrentFolderAuto) && gFolderNamesAuto.get(pos).toString().startsWith("⚠")) {
                toast("该目录不可读，请使用“手动输入路径”");
                return;
            }

            if (selected.isDirectory()) {
                gCurrentFolderAuto = selected;
                putString(DEFAULT_LAST_FOLDER_SP_AUTO, selected.getAbsolutePath());
                refreshFolderListAuto(selected);
                gFolderAdapterAuto.notifyDataSetChanged();
                if (gFolderDialogAuto != null) {
                    gFolderDialogAuto.setTitle("浏览：" + selected.getAbsolutePath());
                }
            }
        }
    });

    // 在当前目录选择文件
    builder.setPositiveButton("在此目录选择文件", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface d, int which) {
            d.dismiss();
            gFolderDialogAuto = null;
            scanFilesMulti(gCurrentFolderAuto, gWantedExtFilterAuto, gCurrentSelectionAuto, gMediaCallbackAuto);
        }
    });

    // 选择当前文件夹
    if (allowFolderSelect) {
        builder.setNeutralButton("选择此文件夹", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int which) {
                d.dismiss();
                gFolderDialogAuto = null;
                ArrayList<String> selected = new ArrayList<String>();
                selected.add(gCurrentFolderAuto.getAbsolutePath());
                gMediaCallbackAuto.onSelected(selected);
            }
        });
    }

    // 统一兜底：手动输入路径（关键）
    builder.setNegativeButton("手动输入路径", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface d, int which) {
            d.dismiss();
            gFolderDialogAuto = null;
            showManualPathDialogForBrowser(gWantedExtFilterAuto, gCurrentSelectionAuto, gMediaCallbackAuto, gAllowFolderSelectAuto);
        }
    });

    gFolderDialogAuto = builder.create();
    gFolderDialogAuto.setOnShowListener(new DialogInterface.OnShowListener() {
        public void onShow(DialogInterface d) {
            setupUnifiedDialog(gFolderDialogAuto);
        }
    });
    gFolderDialogAuto.show();
}

private void showQuickJumpDialog(final String wantedExtFilter, final String currentSelection,
                                 final MediaSelectionCallback callback, final boolean allowFolderSelect) {
    final Activity act = getTopActivity();
    if (act == null) return;

    final String currentRoot = android.os.Environment.getExternalStorageDirectory().getAbsolutePath();
    final String altRoot = currentRoot.contains("/999") ? "/storage/emulated/0" : "/storage/emulated/999";

    final String[] items = new String[] {
        "当前根目录: " + currentRoot,
        "切换到另一目录: " + altRoot,
        "手动输入路径"
    };

    AlertDialog.Builder b = new AlertDialog.Builder(act);
    b.setTitle("选择跳转位置");
    b.setItems(items, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
            if (which == 0) {
                File f = new File(currentRoot);
                if (f.exists()) browseFolderForSelectionAuto(f, wantedExtFilter, currentSelection, callback, allowFolderSelect);
                else toast("目录不存在: " + currentRoot);
            } else if (which == 1) {
                File f = new File(altRoot);
                if (f.exists()) browseFolderForSelectionAuto(f, wantedExtFilter, currentSelection, callback, allowFolderSelect);
                else toast("目录不存在: " + altRoot);
            } else {
                showManualJumpPathDialog(wantedExtFilter, currentSelection, callback, allowFolderSelect);
            }
        }
    });
    b.setNegativeButton("取消", null);
    AlertDialog d = b.create();
    d.setOnShowListener(new DialogInterface.OnShowListener() {
        public void onShow(DialogInterface dialog) { setupUnifiedDialog(d); }
    });
    d.show();
}

private void showManualJumpPathDialog(final String wantedExtFilter, final String currentSelection,
                                      final MediaSelectionCallback callback, final boolean allowFolderSelect) {
    Activity act = getTopActivity();
    if (act == null) return;

    LinearLayout layout = new LinearLayout(act);
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setPadding(24,24,24,24);
    final EditText edit = createStyledEditText("输入完整路径", ROOT_FOLDER);
    layout.addView(edit);

    AlertDialog d = buildCommonAlertDialog(act, "手动跳转路径", layout,
        "跳转", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                String p = edit.getText().toString().trim();
                File f = new File(p);
                if (f.exists() && f.isDirectory()) {
                    browseFolderForSelectionAuto(f, wantedExtFilter, currentSelection, callback, allowFolderSelect);
                } else {
                    toast("路径无效或不可访问");
                }
            }
        },
        "取消", null, null, null);
    d.show();
}
/* ========== 刷新文件夹列表数据（单例模式） ========== */
void refreshFolderListAuto(File folder) {
    gFolderNamesAuto.clear();
    gFolderFilesAuto.clear();

    if (folder == null || !folder.exists() || !folder.isDirectory()) {
        gFolderNamesAuto.add("⚠ 路径无效或不可访问");
        gFolderFilesAuto.add(gCurrentFolderAuto);
        return;
    }

    String abs = folder.getAbsolutePath();

    // 只要有父目录就允许上一级（避免卡死）
    if (folder.getParentFile() != null) {
        gFolderNamesAuto.add("⬆ 上一级");
        gFolderFilesAuto.add(folder.getParentFile());
    }

    File[] subs = null;
    try {
        subs = folder.listFiles();
    } catch (Exception e) {
        subs = null;
    }

    if (subs == null) {
        gFolderNamesAuto.add("⚠ 当前目录不可读，请点“手动输入路径”");
        gFolderFilesAuto.add(folder);
        return;
    }

    // 目录优先排序
    java.util.Arrays.sort(subs, new java.util.Comparator<File>() {
        public int compare(File a, File b) {
            if (a.isDirectory() && !b.isDirectory()) return -1;
            if (!a.isDirectory() && b.isDirectory()) return 1;
            return a.getName().compareToIgnoreCase(b.getName());
        }
    });

    boolean hasDir = false;
    for (int i = 0; i < subs.length; i++) {
        File f = subs[i];
        if (f.isDirectory()) {
            hasDir = true;
            gFolderNamesAuto.add("📁 " + f.getName());
            gFolderFilesAuto.add(f);
        }
    }

    if (!hasDir) {
        gFolderNamesAuto.add("（此目录无子文件夹，可点“在此目录选择文件”）");
        gFolderFilesAuto.add(folder);
    }
}

private void showManualPathDialogForBrowser(final String wantedExtFilter,
                                            final String currentSelection,
                                            final MediaSelectionCallback callback,
                                            final boolean allowFolderSelect) {
    Activity act = getTopActivity();
    if (act == null) return;

    LinearLayout layout = new LinearLayout(act);
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setPadding(24, 24, 24, 24);

    final EditText pathEdit = createStyledEditText(
        "输入目录路径（如 /storage/emulated/0/Download）",
        getString(DEFAULT_LAST_FOLDER_SP_AUTO, ROOT_FOLDER)
    );
    layout.addView(pathEdit);

    AlertDialog dialog = buildCommonAlertDialog(
        act,
        "手动输入路径",
        layout,
        "跳转",
        new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int w) {
                String p = pathEdit.getText().toString().trim();
                File f = new File(p);
                if (f.exists() && f.isDirectory()) {
                    browseFolderForSelectionAuto(f, wantedExtFilter, currentSelection, callback, allowFolderSelect);
                } else {
                    toast("路径无效或不可访问");
                }
            }
        },
        "取消", null, null, null
    );
    dialog.show();
}

void scanFilesMulti(final File folder, final String extFilter, final String currentSelection, final MediaSelectionCallback callback) {
    final ArrayList<String> names = new ArrayList<String>();
    final ArrayList<File> files = new ArrayList<File>();

    File[] list = folder.listFiles();
    if (list != null) {
        String[] exts = TextUtils.isEmpty(extFilter) ? new String[0] : extFilter.split(",");
        for (int i = 0; i < list.length; i++) {
            File f = list[i];
            if (f.isFile()) {
                boolean matches = exts.length == 0;
                for (int j = 0; j < exts.length; j++) {
                    String e = exts[j];
                    if (f.getName().toLowerCase().endsWith(e.trim().toLowerCase())) {
                        matches = true;
                        break;
                    }
                }
                if (matches) {
                    names.add(f.getName());
                    files.add(f);
                }
            }
        }
    }

    if (names.isEmpty()) {
        toast("该目录无匹配文件");
        return;
    }

    final Set<String> selectedPathsSet = new HashSet<String>();
    if (!TextUtils.isEmpty(currentSelection)) {
        String[] parts = currentSelection.split(";;;");
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (!TextUtils.isEmpty(p.trim())) selectedPathsSet.add(p.trim());
        }
    }

    AlertDialog.Builder builder = new AlertDialog.Builder(getTopActivity());
    builder.setTitle("选择文件（可多选）：" + folder.getAbsolutePath());
    final ListView listView = new ListView(getTopActivity());
    listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
    listView.setAdapter(new ArrayAdapter<String>(getTopActivity(), android.R.layout.simple_list_item_multiple_choice, names));
    builder.setView(listView);

    for (int i = 0; i < files.size(); i++) {
        if (selectedPathsSet.contains(files.get(i).getAbsolutePath())) {
            listView.setItemChecked(i, true);
        }
    }

    builder.setPositiveButton("确认选择", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface d, int which) {
            ArrayList<String> selectedPaths = new ArrayList<String>();
            for (int i = 0; i < names.size(); i++) {
                if (listView.isItemChecked(i)) {
                    selectedPaths.add(files.get(i).getAbsolutePath());
                }
            }
            callback.onSelected(selectedPaths);
        }
    });

    builder.setNegativeButton("取消", null);
    final AlertDialog finalDialog = builder.create();
    finalDialog.setOnShowListener(new DialogInterface.OnShowListener() {
        public void onShow(DialogInterface d) {
            setupUnifiedDialog(finalDialog);
        }
    });
    finalDialog.show();
}

private String joinMediaPaths(ArrayList<String> paths, boolean isMultiList) {
    if (paths == null || paths.isEmpty()) return "";
    if (!isMultiList) return paths.get(0);
    return TextUtils.join(";;;", paths);
}

private boolean shouldSelectAll(List currentFilteredIds, Set selectedIds) {
    int selectableCount = currentFilteredIds.size();
    int checkedCount = 0;
    for (int i = 0; i < selectableCount; i++) {
        String id = (String) currentFilteredIds.get(i);
        if (selectedIds.contains(id)) {
            checkedCount++;
        }
    }
    return selectableCount > 0 && checkedCount < selectableCount;
}

private void updateSelectAllButton(AlertDialog dialog, List currentFilteredIds, Set selectedIds) {
    Button neutralButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
    if (neutralButton != null) {
        if (shouldSelectAll(currentFilteredIds, selectedIds)) {
            neutralButton.setText("全选");
        } else {
            neutralButton.setText("取消全选");
        }
    }
}

private void adjustListViewHeight(ListView listView, int itemCount) {
    if (itemCount <= 0) {
        listView.getLayoutParams().height = dpToPx(50);
    } else {
        int itemHeight = dpToPx(50);
        int calculatedHeight = Math.min(itemCount * itemHeight, dpToPx(300));
        listView.getLayoutParams().height = calculatedHeight;
    }
    listView.requestLayout();
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

// =================================================================================
// =================== START: 小智bot 核心功能代码移植 ===================
// =================================================================================

private String getDeviceUUID(Context ctx) {
    if (ctx == null) return "unknown-uuid-due-to-null-context";
    String androidId = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ANDROID_ID);
    if (androidId == null) androidId = "default_android_id";
    return UUID.nameUUIDFromBytes(androidId.getBytes()).toString();
}

private String getDeviceMac(Context ctx) {
    if (ctx == null) return "00:00:00:00:00:00";
    try {
        UUID uuid = UUID.fromString(getDeviceUUID(ctx));
        byte[] uuidBytes = new byte[16];
        long mostSigBits = uuid.getMostSignificantBits();
        long leastSigBits = uuid.getLeastSignificantBits();
        for (int i = 0; i < 8; i++) {
            uuidBytes[i] = (byte)((mostSigBits >>> (8 * (7 - i))) & 0xFF);
        }
        for (int i = 8; i < 16; i++) {
            uuidBytes[i] = (byte)((leastSigBits >>> (8 * (15 - i))) & 0xFF);
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(uuidBytes);
        byte[] fakeMacBytes = new byte[6];
        System.arraycopy(hashBytes, 0, fakeMacBytes, 0, 6);
        char[] hexChars = {'0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f'};
        StringBuilder macBuilder = new StringBuilder();
        for (int i = 0; i < fakeMacBytes.length; i++) {
            int v = fakeMacBytes[i] & 0xFF;
            macBuilder.append(hexChars[v >>> 4]);
            macBuilder.append(hexChars[v & 0x0F]);
            if (i < fakeMacBytes.length - 1) {
                macBuilder.append(':');
            }
        }
        return macBuilder.toString();
    } catch (Exception e) {
        debugLog("[异常] 生成MAC地址错误: " + e.getMessage());
        return "00:00:00:00:00:00";
    }
}

private void addHeaders(Request.Builder builder, Map header) {
    if (header != null) {
        for (Object key : header.keySet()) {
            builder.addHeader((String)key, (String)header.get(key));
        }
    }
}

private String executeRequest(Request.Builder builder) {
    try {
        Response response = aiClient.newCall(builder.build()).execute();
        if (response.isSuccessful() && response.body() != null) {
            return response.body().string();
        }
        return null;
    } catch (IOException e) {
        debugLog("[异常] AI 网络请求失败: " + e.getMessage());
        return null;
    }
}

private String httpGet(String url, Map header) {
    Request.Builder builder = new Request.Builder().url(url).get();
    addHeaders(builder, header);
    return executeRequest(builder);
}

private String httpPost(String url, String data, Map header) {
    String mediaType = (header != null && header.containsKey("Content-Type")) ?
        (String)header.get("Content-Type") : "application/json";
    RequestBody body = RequestBody.create(MediaType.parse(mediaType), data);
    Request.Builder builder = new Request.Builder().url(url).post(body);
    addHeaders(builder, header);
    return executeRequest(builder);
}

private void processAIResponse(final Object msgInfoBean) {
    if (msgInfoBean == null) return;
    try {
        String content = getFieldString(msgInfoBean, "originContent");
        final String talker = getFieldString(msgInfoBean, "talker");
        if (TextUtils.isEmpty(content) || TextUtils.isEmpty(talker)) return;

        boolean isGroupChat = talker.contains("@chatroom");
        if (isGroupChat) {
            content = content.replaceAll("@[^\\s]+\\s+", "").trim();
            if (TextUtils.isEmpty(content)) return;
        }

        if ("#断开".equals(content) || "#断连".equals(content) || "#断线".equals(content)) {
            WebSocket webSocket = aiWebSockets.get(talker);
            if (webSocket != null) {
                webSocket.close(1000, "手动断开");
                debugLog("[小智AI] 已手动断开 WebSocket 连接");
            }
            return;
        }

        final String finalText = content;
        new Thread(new Runnable() {
            public void run() {
                try {
                    WebSocket currentSocket = aiWebSockets.get(talker);
                    if (currentSocket == null) {
                        initializeWebSocketConnection(talker, finalText);
                    } else {
                        sendMessageToWebSocket(talker, finalText);
                    }
                } catch (Exception e) {
                    debugLog("[异常] 小智AI 处理线程错误: " + e.getMessage());
                    insertSystemMsg(talker, "小智AI出错: " + e.getMessage(), System.currentTimeMillis());
                }
            }
        }).start();
    } catch (Exception e) {
        debugLog("[异常] 处理小智AI过程出错: " + e.getMessage());
    }
}

private void initializeWebSocketConnection(final String talker, final String text) {
    try {
        if (aiWebSockets.containsKey(talker)) return;

        WebSocketListener listener = new WebSocketListener() {
            public void onOpen(WebSocket webSocket, Response response) {
                aiWebSockets.put(talker, webSocket);
                debugLog("[小智AI] WebSocket 连接建立成功 -> " + talker);
                insertSystemMsg(talker, "小智AI 已连接", System.currentTimeMillis());

                try {
                    JSONObject helloMsg = new JSONObject();
                    helloMsg.put("type", "hello");
                    helloMsg.put("version", 1);
                    helloMsg.put("transport", "websocket");

                    JSONObject audioParams = new JSONObject();
                    audioParams.put("format", "opus");
                    audioParams.put("sample_rate", 16000);
                    audioParams.put("channels", 1);
                    audioParams.put("frame_duration", 60);
                    helloMsg.put("audio_params", audioParams);

                    webSocket.send(helloMsg.toString());
                    sendMessageToWebSocket(talker, text);
                } catch (Exception e) {
                    debugLog("[异常] 小智AI 初始化消息发送失败: " + e.getMessage());
                }
            }

            public void onMessage(WebSocket webSocket, String result) {
                try {
                    JSONObject resultObj = JSON.parseObject(result);
                    String type = resultObj.getString("type");
                    String state = resultObj.getString("state");
                    if ("tts".equals(type) && "sentence_start".equals(state)) {
                        if (resultObj.containsKey("text")) {
                            String replyText = resultObj.getString("text");
                            boolean q = aiQuoteFlagMap.containsKey(talker) ? aiQuoteFlagMap.get(talker) : false;
                            Long qid = aiQuoteMsgIdMap.get(talker);
                            if (q && qid != null && qid.longValue() > 0L) {
                                sendQuoteMsg(talker, qid.longValue(), replyText);
                            } else {
                                sendText(talker, replyText);
                            }
                            debugLog("[小智AI] 发送回复 -> " + replyText);
                        }
                    }
                } catch (Exception e) {
                    debugLog("[异常] 小智AI 数据解析失败: " + e.getMessage());
                }
            }

            public void onClosing(WebSocket webSocket, int code, String reason) {
                aiWebSockets.remove(talker);
                aiQuoteMsgIdMap.remove(talker);
                aiQuoteFlagMap.remove(talker);
                debugLog("[小智AI] WebSocket 连接关闭 -> " + reason);
                insertSystemMsg(talker, "小智AI 连接已关闭: " + reason, System.currentTimeMillis());
            }

            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                aiWebSockets.remove(talker);
                aiQuoteMsgIdMap.remove(talker);
                aiQuoteFlagMap.remove(talker);
                debugLog("[异常] 小智AI WebSocket 异常断开: " + t.getMessage());
                insertSystemMsg(talker, "小智AI 连接中断: " + t.getMessage(), System.currentTimeMillis());
            }
        };

        Map<String, String> header = new HashMap<String, String>();
        header.put("Authorization", "Bearer test-token");
        header.put("Device-Id", getDeviceMac(hostContext));
        header.put("Client-Id", getDeviceUUID(hostContext));
        header.put("Protocol-Version", "1");

        String serveUrl = getString(XIAOZHI_CONFIG_KEY, XIAOZHI_SERVE_KEY, "wss://api.tenclass.net/xiaozhi/v1/");
        Request.Builder requestBuilder = new Request.Builder().url(serveUrl);
        addHeaders(requestBuilder, header);

        debugLog("[小智AI] 正在创建新的 WebSocket 连接...");
        aiClient.newWebSocket(requestBuilder.build(), listener);

    } catch (Exception e) {
        debugLog("[异常] 初始化 WebSocket 失败: " + e.getMessage());
        insertSystemMsg(talker, "小智AI 连接失败: " + e.getMessage(), System.currentTimeMillis());
    }
}

private void sendMessageToWebSocket(final String talker, String text) {
    try {
        WebSocket webSocket = aiWebSockets.get(talker);
        if (webSocket != null) {
            JSONObject socketMsg = new JSONObject();
            socketMsg.put("session_id", "session_for_" + talker);
            socketMsg.put("type", "listen");
            socketMsg.put("state", "detect");
            socketMsg.put("text", text);
            webSocket.send(socketMsg.toString());
            debugLog("[小智AI] 消息已投递至服务器: " + text);
        } else {
            debugLog("[小智AI] 连接丢失，尝试重连...");
            initializeWebSocketConnection(talker, text);
        }
    } catch (Exception e) {
        debugLog("[异常] 小智AI 消息发送失败: " + e.getMessage());
    }
}

// ========== 智聊AI 功能模块 ==========



private String extractSseData(String line) {
    if (line == null) return "";
    line = line.trim();
    if (!line.startsWith("data:")) return "";
    String data = line.substring(5).trim();
    return data;
}

private String callZhiliaNonStreamOnce(String apiUrl, String apiKey, String modelName, List history) {
    try {
        JSONObject jsonBody = new JSONObject();
        jsonBody.put("model", modelName);
        jsonBody.put("messages", history);
        jsonBody.put("temperature", 0.7);
        jsonBody.put("stream", false);

        RequestBody body = RequestBody.create(MediaType.parse("application/json"), jsonBody.toString());
        Request.Builder reqBuilder = new Request.Builder().url(apiUrl).post(body);
        reqBuilder.addHeader("Content-Type", "application/json");
        reqBuilder.addHeader("Authorization", "Bearer " + apiKey);

        Response response = aiClient.newCall(reqBuilder.build()).execute();
        String responseContent = response.body() != null ? response.body().string() : null;
        if (TextUtils.isEmpty(responseContent) || !responseContent.trim().startsWith("{")) return null;

        JSONObject jsonObj = JSON.parseObject(responseContent);
        if (jsonObj.containsKey("error")) return null;
        if (!jsonObj.containsKey("choices")) return null;

        JSONArray choices = jsonObj.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) return null;
        return choices.getJSONObject(0).getJSONObject("message").getString("content");
    } catch (Exception e) {
        debugLog("[异常] 非流式请求失败: " + e.getMessage());
        return null;
    }
}

private String callZhiliaStreamOnce(String apiUrl, String apiKey, String modelName, List history) {
    java.io.BufferedReader br = null;
    try {
        JSONObject jsonBody = new JSONObject();
        jsonBody.put("model", modelName);
        jsonBody.put("messages", history);
        jsonBody.put("temperature", 0.7);
        jsonBody.put("stream", true);

        RequestBody body = RequestBody.create(MediaType.parse("application/json"), jsonBody.toString());
        Request request = new Request.Builder()
            .url(apiUrl)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer " + apiKey)
            .post(body)
            .build();

        Response response = aiClient.newCall(request).execute();
        if (!response.isSuccessful() || response.body() == null) {
            return null;
        }

        br = new java.io.BufferedReader(new java.io.InputStreamReader(response.body().byteStream(), "UTF-8"));
        String line;
        StringBuilder out = new StringBuilder();

        while ((line = br.readLine()) != null) {
            String data = extractSseData(line);
            if (TextUtils.isEmpty(data)) continue;
            if ("[DONE]".equals(data)) break;

            try {
                JSONObject obj = JSON.parseObject(data);
                JSONArray choices = obj.getJSONArray("choices");
                if (choices == null || choices.isEmpty()) continue;
                JSONObject c0 = choices.getJSONObject(0);

                JSONObject delta = c0.getJSONObject("delta");
                if (delta != null) {
                    String piece = delta.getString("content");
                    if (!TextUtils.isEmpty(piece)) out.append(piece);
                } else {
                    JSONObject msg = c0.getJSONObject("message");
                    if (msg != null) {
                        String piece2 = msg.getString("content");
                        if (!TextUtils.isEmpty(piece2)) out.append(piece2);
                    }
                }
            } catch (Exception ignore) {}
        }

        String result = out.toString().trim();
        return TextUtils.isEmpty(result) ? null : result;
    } catch (Exception e) {
        debugLog("[异常] 流式请求失败: " + e.getMessage());
        return null;
    } finally {
        try { if (br != null) br.close(); } catch (Exception ignore) {}
    }
}


private void sendZhiliaAiReply(final String talker, String userContent, final boolean replyAsQuote, final long quoteMsgId) {
    String apiKey = getString(ZHILIA_AI_API_KEY, "");
    String apiUrl = getString(ZHILIA_AI_API_URL, "https://api.siliconflow.cn/v1/chat/completions");
    String modelName = getString(ZHILIA_AI_MODEL_NAME, "deepseek-ai/DeepSeek-V3");
    String systemPrompt = getString(ZHILIA_AI_SYSTEM_PROMPT, "你是个宝宝");
    int contextLimit = getInt(ZHILIA_AI_CONTEXT_LIMIT, 10);
    boolean streamEnabled = getBoolean(ZHILIA_AI_STREAM_ENABLED_KEY, false);

    if (TextUtils.isEmpty(apiKey)) {
        toast("请先在智聊AI参数设置中配置API Key");
        return;
    }

    List history = zhiliaConversationHistories.get(talker);
    if (history == null) {
        history = new ArrayList();
        if (!TextUtils.isEmpty(systemPrompt)) {
            Map systemMsg = new HashMap();
            systemMsg.put("role", "system");
            systemMsg.put("content", systemPrompt);
            history.add(systemMsg);
        }
        zhiliaConversationHistories.put(talker, history);
    }

    userContent = userContent.replaceAll("@[^\\s]+\\s+", "").trim();
    if (TextUtils.isEmpty(userContent)) return;

    Map userMsg = new HashMap();
    userMsg.put("role", "user");
    userMsg.put("content", userContent);
    history.add(userMsg);

    while (history.size() > contextLimit * 2 + 1) {
        history.remove(1);
        if (history.size() > 1) history.remove(1);
    }

    debugLog("[智聊AI] 触发提问 -> 内容: " + userContent + " (历史长度:" + history.size() + ", stream=" + streamEnabled + ")");

    final List finalHistory = history;
    final boolean finalStreamEnabled = streamEnabled;
    final String finalApiUrl = apiUrl;
    final String finalApiKey = apiKey;
    final String finalModelName = modelName;

    new Thread(new Runnable() {
        public void run() {
            try {
                String msgContent = null;
                String modeUsed = "";

                if (finalStreamEnabled) {
                    msgContent = callZhiliaStreamOnce(finalApiUrl, finalApiKey, finalModelName, finalHistory);
                    modeUsed = "stream";
                    if (TextUtils.isEmpty(msgContent)) {
                        debugLog("[智聊AI] 流式失败，自动回退非流式");
                        msgContent = callZhiliaNonStreamOnce(finalApiUrl, finalApiKey, finalModelName, finalHistory);
                        modeUsed = TextUtils.isEmpty(msgContent) ? "stream+fallback_failed" : "non_stream_fallback";
                    }
                } else {
                    msgContent = callZhiliaNonStreamOnce(finalApiUrl, finalApiKey, finalModelName, finalHistory);
                    modeUsed = "non_stream";
                    if (TextUtils.isEmpty(msgContent)) {
                        debugLog("[智聊AI] 非流式失败，自动回退流式");
                        msgContent = callZhiliaStreamOnce(finalApiUrl, finalApiKey, finalModelName, finalHistory);
                        modeUsed = TextUtils.isEmpty(msgContent) ? "non_stream+fallback_failed" : "stream_fallback";
                    }
                }

                if (TextUtils.isEmpty(msgContent)) {
                    debugLog("[异常] 智聊AI 两种模式均失败");
                    insertSystemMsg(talker, "智聊AI响应失败（流式/非流式都不可用）", System.currentTimeMillis());
                    sendText(talker, "抱歉，我暂时无法回复。");
                    return;
                }

                debugLog("[智聊AI] 获取回复成功(" + modeUsed + ") -> " + msgContent);
                if (replyAsQuote) {
                    sendQuoteMsg(talker, quoteMsgId, msgContent);
                } else {
                    sendText(talker, msgContent);
                }

                Map assistantMsg = new HashMap();
                assistantMsg.put("role", "assistant");
                assistantMsg.put("content", msgContent);
                finalHistory.add(assistantMsg);
                zhiliaConversationHistories.put(talker, finalHistory);

            } catch (Exception e) {
                debugLog("[异常] 智聊AI 请求失败: " + e.getMessage());
                insertSystemMsg(talker, "智聊AI错误: " + e.getMessage(), System.currentTimeMillis());
            }
        }
    }).start();
}


private Map<String, Object> createAutoReplyRuleMap(String keyword, String reply, boolean enabled, int matchType, Set targetWxids, int targetType, int atTriggerType, long delaySeconds, boolean replyAsQuote, int replyType, List mediaPaths, String startTime, String endTime, Set excludedWxids, long mediaDelaySeconds, int patTriggerType, Set excludedGroupMemberWxids, Set includedGroupMemberWxids, Set excludedGroupIdsForMemberFilter, Set includedGroupIdsForMemberFilter) {
    Map<String, Object> rule = new HashMap<String, Object>();
    rule.put("keyword", keyword);
    rule.put("reply", reply);
    rule.put("enabled", enabled);
    rule.put("matchType", matchType);
    rule.put("targetWxids", targetWxids != null ? targetWxids : new HashSet());
    rule.put("targetType", targetType);
    rule.put("atTriggerType", atTriggerType);
    rule.put("delaySeconds", delaySeconds);
    rule.put("replyAsQuote", replyAsQuote);
    rule.put("replyType", replyType);
    rule.put("mediaPaths", mediaPaths != null ? mediaPaths : new ArrayList());
    rule.put("startTime", startTime);
    rule.put("endTime", endTime);
    rule.put("excludedWxids", excludedWxids != null ? excludedWxids : new HashSet());
    rule.put("mediaDelaySeconds", mediaDelaySeconds);
    rule.put("patTriggerType", patTriggerType);
    rule.put("excludedGroupMemberWxids", excludedGroupMemberWxids != null ? excludedGroupMemberWxids : new HashSet());
    rule.put("includedGroupMemberWxids", includedGroupMemberWxids != null ? includedGroupMemberWxids : new HashSet());
    rule.put("excludedGroupIdsForMemberFilter", excludedGroupIdsForMemberFilter != null ? excludedGroupIdsForMemberFilter : new HashSet());
    rule.put("includedGroupIdsForMemberFilter", includedGroupIdsForMemberFilter != null ? includedGroupIdsForMemberFilter : new HashSet());
    rule.put("compiledPattern", null);
    return rule;
}

private Map<String, Object> createAutoReplyRuleMap(String keyword, String reply, boolean enabled, int matchType, Set targetWxids, int targetType, int atTriggerType, long delaySeconds, boolean replyAsQuote, int replyType, List mediaPaths) {
    return createAutoReplyRuleMap(keyword, reply, enabled, matchType, targetWxids, targetType, atTriggerType, delaySeconds, replyAsQuote, replyType, mediaPaths, "", "", new HashSet(), 1L, PAT_TRIGGER_NONE, new HashSet(), new HashSet(), new HashSet(), new HashSet());
}

private void compileRegexPatternForRule(Map<String, Object> rule) {
    int matchType = (Integer) rule.get("matchType");
    String keyword = (String) rule.get("keyword");
    if (matchType == MATCH_TYPE_REGEX && !TextUtils.isEmpty(keyword)) {
        try {
            Pattern pattern = Pattern.compile(keyword);
            rule.put("compiledPattern", pattern);
        } catch (Exception e) {
            debugLog("[异常] 编译正则关键词出错: " + keyword + " -> " + e.getMessage());
            rule.put("compiledPattern", null);
        }
    } else {
        rule.put("compiledPattern", null);
    }
}

private String ruleMapToString(Map<String, Object> rule) {
    String keyword = (String) rule.get("keyword");
    String reply = (String) rule.get("reply");
    boolean enabled = (Boolean) rule.get("enabled");
    int matchType = (Integer) rule.get("matchType");
    Set targetWxids = (Set) rule.get("targetWxids");
    int atTriggerType = (Integer) rule.get("atTriggerType");
    long delaySeconds = (Long) rule.get("delaySeconds");
    int targetType = (Integer) rule.get("targetType");
    boolean replyAsQuote = (Boolean) rule.get("replyAsQuote");
    int replyType = (Integer) rule.get("replyType");
    List mediaPaths = (List) rule.get("mediaPaths");
    String startTime = (String) rule.get("startTime");
    String endTime = (String) rule.get("endTime");
    Set excludedWxids = (Set) rule.get("excludedWxids");
    long mediaDelaySeconds = (Long) rule.get("mediaDelaySeconds");
    int patTriggerType = (Integer) rule.get("patTriggerType");

    String wxidsStr = "";
    if (targetWxids != null && !targetWxids.isEmpty()) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Object wxidObj : targetWxids) {
            String wxid = (String) wxidObj;
            if (!first) sb.append(",");
            sb.append(wxid);
            first = false;
        }
        wxidsStr = sb.toString();
    }

    String mediaPathsStr = "";
    if (mediaPaths != null && !mediaPaths.isEmpty()) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (int i = 0; i < mediaPaths.size(); i++) {
            String path = (String) mediaPaths.get(i);
            if (!first) sb.append(";;;");
            sb.append(path);
            first = false;
        }
        mediaPathsStr = sb.toString();
    }

    String excludedStr = "";
    if (excludedWxids != null && !excludedWxids.isEmpty()) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Object wxidObj : excludedWxids) {
            String wxid = (String) wxidObj;
            if (!first) sb.append(",");
            sb.append(wxid);
            first = false;
        }
        excludedStr = sb.toString();
    }

    Set excludedGroupMembers = (Set) rule.get("excludedGroupMemberWxids");
    Set includedGroupMembers = (Set) rule.get("includedGroupMemberWxids");
    Set excludedGroupIds = (Set) rule.get("excludedGroupIdsForMemberFilter");
    Set includedGroupIds = (Set) rule.get("includedGroupIdsForMemberFilter");

    String excludedGroupMembersStr = "";
    if (excludedGroupMembers != null && !excludedGroupMembers.isEmpty()) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Object o : excludedGroupMembers) {
            if (!first) sb.append(",");
            sb.append((String)o);
            first = false;
        }
        excludedGroupMembersStr = sb.toString();
    }

    String includedGroupMembersStr = "";
    if (includedGroupMembers != null && !includedGroupMembers.isEmpty()) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Object o : includedGroupMembers) {
            if (!first) sb.append(",");
            sb.append((String)o);
            first = false;
        }
        includedGroupMembersStr = sb.toString();
    }

    String excludedGroupIdsStr = "";
    if (excludedGroupIds != null && !excludedGroupIds.isEmpty()) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Object o : excludedGroupIds) {
            if (!first) sb.append(",");
            sb.append((String)o);
            first = false;
        }
        excludedGroupIdsStr = sb.toString();
    }

    String includedGroupIdsStr = "";
    if (includedGroupIds != null && !includedGroupIds.isEmpty()) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Object o : includedGroupIds) {
            if (!first) sb.append(",");
            sb.append((String)o);
            first = false;
        }
        includedGroupIdsStr = sb.toString();
    }

    return keyword + "||" + reply + "||" + enabled + "||" + matchType + "||" + wxidsStr + "||" + atTriggerType + "||" + delaySeconds + "||" + targetType + "||" + replyAsQuote + "||" + replyType + "||" + mediaPathsStr + "||" + (startTime != null ? startTime : "") + "||" + (endTime != null ? endTime : "") + "||" + excludedStr + "||" + mediaDelaySeconds + "||" + patTriggerType + "||" + excludedGroupMembersStr + "||" + includedGroupMembersStr + "||" + excludedGroupIdsStr + "||" + includedGroupIdsStr;
}

private Map<String, Object> ruleFromString(String str) {
    Map<String, Object> rule = null;
    try {
        String[] parts = str.split("\\|\\|");
        String keyword = parts.length > 0 ? parts[0] : "";
        String reply = parts.length > 1 ? parts[1] : "";
        boolean enabled = parts.length > 2 ? Boolean.parseBoolean(parts[2]) : true;
        int matchType = parts.length > 3 ? Integer.parseInt(parts[3]) : MATCH_TYPE_FUZZY;
        Set wxids = new HashSet();
        if (parts.length > 4 && !TextUtils.isEmpty(parts[4])) {
            String[] wxidArray = parts[4].split(",");
            for (String w : wxidArray) {
                if (!TextUtils.isEmpty(w.trim())) wxids.add(w.trim());
            }
        }
        int atTriggerType = parts.length > 5 ? Integer.parseInt(parts[5]) : AT_TRIGGER_NONE;
        long delaySeconds = parts.length > 6 ? Long.parseLong(parts[6]) : 0;
        int targetType = parts.length > 7 ? Integer.parseInt(parts[7]) : TARGET_TYPE_NONE;
        boolean replyAsQuote = parts.length > 8 ? Boolean.parseBoolean(parts[8]) : false;
        int replyType = parts.length > 9 ? Integer.parseInt(parts[9]) : REPLY_TYPE_TEXT;
        List parsedMediaPaths = new ArrayList();
        if (parts.length > 10 && !TextUtils.isEmpty(parts[10])) {
            String[] pathArray = parts[10].split(";;;");
            for (String p : pathArray) {
                if (!TextUtils.isEmpty(p.trim())) parsedMediaPaths.add(p.trim());
            }
        }
        String startTime = parts.length > 11 ? parts[11] : "";
        String endTime = parts.length > 12 ? parts[12] : "";
        Set excludedWxids = new HashSet();
        if (parts.length > 13 && !TextUtils.isEmpty(parts[13])) {
            String[] excludedArray = parts[13].split(",");
            for (String w : excludedArray) {
                if (!TextUtils.isEmpty(w.trim())) excludedWxids.add(w.trim());
            }
        }
        long mediaDelaySeconds = parts.length > 14 ? Long.parseLong(parts[14]) : 1L;
        int patTriggerType = parts.length > 15 ? Integer.parseInt(parts[15]) : PAT_TRIGGER_NONE;

        Set excludedGroupMemberWxids = new HashSet();
        if (parts.length > 16 && !TextUtils.isEmpty(parts[16])) {
            String[] arr = parts[16].split(",");
            for (String w : arr) if (!TextUtils.isEmpty(w.trim())) excludedGroupMemberWxids.add(w.trim());
        }

        Set includedGroupMemberWxids = new HashSet();
        if (parts.length > 17 && !TextUtils.isEmpty(parts[17])) {
            String[] arr2 = parts[17].split(",");
            for (String w2 : arr2) if (!TextUtils.isEmpty(w2.trim())) includedGroupMemberWxids.add(w2.trim());
        }

        Set excludedGroupIdsForMemberFilter = new HashSet();
        if (parts.length > 18 && !TextUtils.isEmpty(parts[18])) {
            String[] arr3 = parts[18].split(",");
            for (String g : arr3) if (!TextUtils.isEmpty(g.trim())) excludedGroupIdsForMemberFilter.add(g.trim());
        }

        Set includedGroupIdsForMemberFilter = new HashSet();
        if (parts.length > 19 && !TextUtils.isEmpty(parts[19])) {
            String[] arr4 = parts[19].split(",");
            for (String g2 : arr4) if (!TextUtils.isEmpty(g2.trim())) includedGroupIdsForMemberFilter.add(g2.trim());
        }

        rule = createAutoReplyRuleMap(keyword, reply, enabled, matchType, wxids, targetType, atTriggerType, delaySeconds, replyAsQuote, replyType, parsedMediaPaths, startTime, endTime, excludedWxids, mediaDelaySeconds, patTriggerType, excludedGroupMemberWxids, includedGroupMemberWxids, excludedGroupIdsForMemberFilter, includedGroupIdsForMemberFilter);
    } catch (Exception e) {
        debugLog("[异常] 从字符串解析规则失败: '" + str + "' -> " + e.getMessage());
        return null;
    }
    if (rule != null) {
        compileRegexPatternForRule(rule);
    }
    return rule;
}

private class AcceptReplyItem {
    public int type;
    public String content;
    public long mediaDelaySeconds;
    public AcceptReplyItem(int type, String content, long mediaDelaySeconds) {
        this.type = type;
        this.content = content;
        this.mediaDelaySeconds = mediaDelaySeconds;
    }
    public AcceptReplyItem(int type, String content) {
        this(type, content, 1L);
    }
    public String toString() {
        return type + "||" + content + "||" + mediaDelaySeconds;
    }
    public static AcceptReplyItem fromString(String str) {
        String[] parts = str.split("\\|\\|");
        if (parts.length < 2) return null;
        try {
            int type = Integer.parseInt(parts[0]);
            String content = parts[1];
            long mediaDelaySeconds = parts.length > 2 ? Long.parseLong(parts[2]) : 1L;
            return new AcceptReplyItem(type, content, mediaDelaySeconds);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AcceptReplyItem that = (AcceptReplyItem) o;
        return type == that.type && Objects.equals(content, that.content) && mediaDelaySeconds == that.mediaDelaySeconds;
    }

    public int hashCode() {
        return Objects.hash(type, content, mediaDelaySeconds);
    }
}

private boolean containsAny(String src, String[] keys) {
    if (src == null || keys == null) return false;
    for (int i = 0; i < keys.length; i++) {
        if (keys[i] != null && keys[i].equals(src)) return true;
    }
    return false;
}

public boolean onClickSendBtn(String text) {
    try {
        if (containsAny(text, new String[]{"自动回复设置", "自动回复", "回复设置"})) {
            showAutoReplySettingDialog();
            return true;
        }
        if (containsAny(text, new String[]{"好友请求设置", "自动通过"})) {
            showAutoAcceptFriendDialog();
            return true;
        }
        if (containsAny(text, new String[]{"添加好友回复", "好友通过回复"})) {
            showGreetOnAcceptedDialog();
            return true;
        }
        if (containsAny(text, new String[]{"回复规则", "规则管理"})) {
            showAutoReplyRulesDialog();
            return true;
        }
        if (containsAny(text, new String[]{"AI配置", "智聊配置", "小智配置"})) {
            showAIChoiceDialog();
            return true;
        }
    } catch (Exception e) {
        debugLog("[异常] onClickSendBtn 路由失败: " + e.getMessage());
        return false;
    }
    return false;
}

private final static int MSG_TYPE_TEXT = 1;
private final static int MSG_TYPE_IMAGE = 3;
private final static int MSG_TYPE_VOICE = 34;
private final static int MSG_TYPE_EMOJI = 47;
private final static int MSG_TYPE_VIDEO = 43;
private final static int MSG_TYPE_SYSTEM = 10000;
private final static int MSG_TYPE_PAT = 10007;
private final static int MSG_TYPE_LOCATION = 48;
private final static int MSG_TYPE_SHARE_CARD = 42;
private final static int MSG_TYPE_FILE = 87;
private final static int MSG_TYPE_APP = 49;

private String getFieldString(Object obj, String fieldName) {
    if (obj == null) return "";
    try {
        java.lang.reflect.Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        Object result = field.get(obj);
        return result != null ? result.toString() : "";
    } catch (Exception e) {
        return "";
    }
}

private int getFieldInt(Object obj, String fieldName, int defaultValue) {
    if (obj == null) return defaultValue;
    try {
        java.lang.reflect.Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        Object result = field.get(obj);
        if (result instanceof Integer) return (Integer) result;
        if (result instanceof Long) return ((Long) result).intValue();
        return defaultValue;
    } catch (Exception e) {
        return defaultValue;
    }
}

private long getFieldLong(Object obj, String fieldName, long defaultValue) {
    if (obj == null) return defaultValue;
    try {
        java.lang.reflect.Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        Object result = field.get(obj);
        if (result instanceof Long) return (Long) result;
        if (result instanceof Integer) return ((Integer) result).longValue();
        return defaultValue;
    } catch (Exception e) {
        return defaultValue;
    }
}

private boolean getFieldBoolean(Object obj, String fieldName, boolean defaultValue) {
    if (obj == null) return defaultValue;
    try {
        java.lang.reflect.Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        Object result = field.get(obj);
        if (result instanceof Boolean) return (Boolean) result;
        return defaultValue;
    } catch (Exception e) {
        return defaultValue;
    }
}


private boolean callBoolMethod(Object obj, String methodName, boolean defValue) {
    if (obj == null) return defValue;
    try {
        java.lang.reflect.Method m = obj.getClass().getMethod(methodName);
        m.setAccessible(true);
        Object r = m.invoke(obj);
        if (r instanceof Boolean) return ((Boolean) r).booleanValue();
        return defValue;
    } catch (Exception e) {
        return defValue;
    }
}

private Object getFieldObject(Object obj, String fieldName) {
    if (obj == null) return null;
    try {
        java.lang.reflect.Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(obj);
    } catch (Exception e) {
        return null;
    }
}

private boolean isTextMessage(Object msgInfoBean) {
    int msgType = getFieldInt(msgInfoBean, "type", -1);
    if (msgType == MSG_TYPE_TEXT) return true;
    String content = getFieldString(msgInfoBean, "originContent");
    return msgType == -1 && !TextUtils.isEmpty(content);
}

private boolean isPatMessage(Object msgInfoBean) {
    int msgType = getFieldInt(msgInfoBean, "type", -1);
    if (msgType == MSG_TYPE_PAT) return true;
    return false;
}

private boolean isPrivateChat(Object msgInfoBean) {
    String talker = getFieldString(msgInfoBean, "talker");
    if (!TextUtils.isEmpty(talker) && talker.contains("@chatroom")) return false;
    return true;
}

private boolean isGroupChat(Object msgInfoBean) {
    String talker = getFieldString(msgInfoBean, "talker");
    return !TextUtils.isEmpty(talker) && talker.contains("@chatroom");
}

private boolean isSelfMessage(Object msgInfoBean) {
    String talker = getFieldString(msgInfoBean, "talker");
    String senderWxid = getFieldString(msgInfoBean, "sendTalker");
    String selfWxid = getLoginWxid();
    if (!TextUtils.isEmpty(selfWxid)) {
        boolean equalsTalker = selfWxid.equals(talker);
        boolean equalsSender = !TextUtils.isEmpty(senderWxid) && selfWxid.equals(senderWxid);
        return equalsTalker || equalsSender;
    }
    return false;
}

private boolean isSystemMessage(Object msgInfoBean) {
    int msgType = getFieldInt(msgInfoBean, "type", -1);
    if (msgType >= 10000) {
        if (msgType == MSG_TYPE_FILE || msgType == MSG_TYPE_LOCATION ||
            msgType == MSG_TYPE_SHARE_CARD || msgType == MSG_TYPE_APP) {
            return false;
        }
        return true;
    }
    return msgType < 0;
}

private boolean isAtMe(Object msgInfoBean) {
    String content = getFieldString(msgInfoBean, "originContent");
    if (!TextUtils.isEmpty(content) && content.startsWith("@")) {
        String myWxid = getLoginWxid();
        String myAlias = getLoginAlias();
        return content.contains(myWxid) || (!TextUtils.isEmpty(myAlias) && content.contains("@" + myAlias));
    }
    return false;
}

private boolean isNotifyAll(Object msgInfoBean) {
    String content = getFieldString(msgInfoBean, "originContent");
    return content != null && content.contains("@全体成员");
}

private String getPattedUser(Object msgInfoBean) {
    Object patMsg = getFieldObject(msgInfoBean, "patMsg");
    if (patMsg != null) {
        return getFieldString(patMsg, "pattedUser");
    }
    return "";
}

// 统一提取的回复序列执行器（供好友通过/被通过调用）
private void executeReplySequence(final String targetWxid, final List replyItems, final long initialDelay) {
    new Thread(new Runnable() {
        public void run() {
            try {
                if (initialDelay > 0) Thread.sleep(initialDelay * 1000);

                for (int i = 0; i < replyItems.size(); i++) {
                    AcceptReplyItem item = (AcceptReplyItem) replyItems.get(i);
                    switch (item.type) {
                        case ACCEPT_REPLY_TYPE_TEXT:
                            String friendName = getFriendName(targetWxid);
                            if (friendName == null || friendName.isEmpty()) friendName = "朋友";
                            String finalText = item.content.replace("%friendName%", friendName);
                            if (!TextUtils.isEmpty(finalText)) sendText(targetWxid, finalText);
                            break;
                        case ACCEPT_REPLY_TYPE_IMAGE:
                        case ACCEPT_REPLY_TYPE_VIDEO:
                        case ACCEPT_REPLY_TYPE_EMOJI:
                        case ACCEPT_REPLY_TYPE_FILE:
                            if (!TextUtils.isEmpty(item.content)) {
                                String[] paths = item.content.split(";;;");
                                for (int j = 0; j < paths.length; j++) {
                                    String path = paths[j].trim();
                                    if (!TextUtils.isEmpty(path)) {
                                        File file = new File(path);
                                        if (file.exists() && file.isFile()) {
                                            String fileName = file.getName();
                                            switch (item.type) {
                                                case ACCEPT_REPLY_TYPE_IMAGE: sendImage(targetWxid, path); break;
                                                case ACCEPT_REPLY_TYPE_VIDEO: sendVideo(targetWxid, path); break;
                                                case ACCEPT_REPLY_TYPE_EMOJI: sendEmoji(targetWxid, path); break;
                                                case ACCEPT_REPLY_TYPE_FILE: shareFile(targetWxid, fileName, path, ""); break;
                                            }
                                            if (j < paths.length - 1) Thread.sleep(item.mediaDelaySeconds * 1000);
                                        }
                                    }
                                }
                            }
                            break;
                        case ACCEPT_REPLY_TYPE_VOICE_FIXED:
                            if (!TextUtils.isEmpty(item.content)) {
                                String[] voicePaths = item.content.split(";;;");
                                for (int j = 0; j < voicePaths.length; j++) {
                                    String voicePath = voicePaths[j].trim();
                                    if (!TextUtils.isEmpty(voicePath)) {
                                        sendVoice(targetWxid, voicePath);
                                        if (j < voicePaths.length - 1) Thread.sleep(item.mediaDelaySeconds * 1000);
                                    }
                                }
                            }
                            break;
                        case ACCEPT_REPLY_TYPE_VOICE_RANDOM:
                            if (!TextUtils.isEmpty(item.content)) {
                                List voiceFiles = getVoiceFilesFromFolder(item.content);
                                if (voiceFiles != null && !voiceFiles.isEmpty()) {
                                    String randomVoicePath = (String) voiceFiles.get(new Random().nextInt(voiceFiles.size()));
                                    sendVoice(targetWxid, randomVoicePath);
                                }
                            }
                            break;
                        case ACCEPT_REPLY_TYPE_CARD:
                            if (!TextUtils.isEmpty(item.content)) {
                                String[] wxids = item.content.split(";;;");
                                for (int j = 0; j < wxids.length; j++) {
                                    String wxidToShare = wxids[j].trim();
                                    if (!TextUtils.isEmpty(wxidToShare)) {
                                        sendShareCard(targetWxid, wxidToShare);
                                        if (j < wxids.length - 1) Thread.sleep(item.mediaDelaySeconds * 1000);
                                    }
                                }
                            }
                            break;
                        case ACCEPT_REPLY_TYPE_INVITE_GROUP:
                            if (!TextUtils.isEmpty(item.content)) {
                                String[] groupIds = item.content.split(";;;");
                                for (int j = 0; j < groupIds.length; j++) {
                                    String groupId = groupIds[j].trim();
                                    if (!TextUtils.isEmpty(groupId)) {
                                        inviteChatroomMember(groupId, targetWxid);
                                        if (j < groupIds.length - 1) Thread.sleep(item.mediaDelaySeconds * 1000);
                                    }
                                }
                            }
                            break;
                    }
                    if (i < replyItems.size() - 1) Thread.sleep(1000);
                }
                debugLog("[动作完毕] 回复序列已执行完毕。");
            } catch (Exception e) {
                debugLog("[异常] 发送序列消息失败：" + e.toString());
            }
        }
    }).start();
}

public void onNewFriend(String wxid, String ticket, int scene) {
    try {
        if (getBoolean(AUTO_ACCEPT_FRIEND_ENABLED_KEY, false)) {
            debugLog("[新好友申请] 自动同意已开启，正在同意请求: " + wxid);
            verifyUser(wxid, ticket, scene);

            long delay = getLong(AUTO_ACCEPT_DELAY_KEY, 2L);
            List replyItems = getReplyItems(AUTO_ACCEPT_REPLY_ITEMS_KEY, "");
            if (replyItems != null && !replyItems.isEmpty()) {
                executeReplySequence(wxid, replyItems, delay);
            }
        }
    } catch (Exception e) {
        debugLog("[异常] 处理新好友申请失败: " + e.getMessage());
    }
}

public void onHandleMsg(final Object msgInfoBean) {
    try {
        if (isSelfMessage(msgInfoBean)) return; // 忽略自己的消息，不打印日志

        int msgType = getFieldInt(msgInfoBean, "type", -1);
        boolean isTextMsg = (msgType == 1);
        boolean isSendMsg = getFieldBoolean(msgInfoBean, "isSend", false);
        String content = getFieldString(msgInfoBean, "originContent");

        if (getBoolean(GREET_ON_ACCEPTED_ENABLED_KEY, false) && isTextMsg && !isSendMsg) {
            if (FRIEND_ADD_SUCCESS_KEYWORD.equals(content)) {
                debugLog("[对方通过验证] 检测到好友通过了验证，准备发送欢迎序列...");
                final String newFriendWxid = getFieldString(msgInfoBean, "talker");
                long delay = getLong(GREET_ON_ACCEPTED_DELAY_KEY, 2L);
                List replyItems = getGreetOnAcceptedReplyItems();
                if (replyItems != null && !replyItems.isEmpty()) {
                    executeReplySequence(newFriendWxid, replyItems, delay);
                }
                return;
            }
        }

        String talker = getFieldString(msgInfoBean, "talker");
        String senderWxid = getFieldString(msgInfoBean, "sendTalker");
        String selfWxid = getLoginWxid();

        boolean isPatMsg = content != null && (content.contains("拍了拍") || content.contains("patted"));
        int isSendIntValue = getFieldInt(msgInfoBean, "isSendInt", 0);
        boolean isSelfMsg = (isSendIntValue > 0);
        if (!isSelfMsg && !TextUtils.isEmpty(senderWxid) && !TextUtils.isEmpty(selfWxid)) {
            isSelfMsg = selfWxid.equals(senderWxid);
        }

        boolean isSystemMsg = (msgType < 0) || (msgType >= 10000 && !isPatMsg);

        // 核心拦截：非文本且非拍一拍、自己发的、系统消息、空消息均直接放行，不留痕迹
        if (!isTextMsg && !isPatMsg) return;
        if (isSelfMsg) return;
        if (isSystemMsg) return;
        if (TextUtils.isEmpty(content) && !isPatMsg) return;
        if (TextUtils.isEmpty(talker)) return;

        if (TextUtils.isEmpty(senderWxid)) senderWxid = talker;

        // 通过核心拦截后，打印一行简要日志
        boolean isGroupChat = talker.contains("@chatroom");
        debugLog("[收到消息] 来自: " + senderWxid + (isGroupChat ? " (群聊)" : " (私聊)") + " -> " + content);

        if (shouldAutoReply(msgInfoBean, talker, senderWxid)) {
            processAutoReply(msgInfoBean);
        }
    } catch (Exception e) {
        debugLog("[异常] 消息处理主流程出错: " + e.getMessage());
    }
}

private boolean shouldAutoReply(Object msgInfoBean, String talker, String senderWxid) {
    try {
        String selfWxid = getLoginWxid();
        if (!TextUtils.isEmpty(selfWxid)) {
            if (selfWxid.equals(talker) || selfWxid.equals(senderWxid)) return false;
        }
        boolean isGroupChat = false;
        if (!TextUtils.isEmpty(talker) && talker.contains("@chatroom")) {
            isGroupChat = true;
        } else {
            String content = getFieldString(msgInfoBean, "originContent");
            if (!TextUtils.isEmpty(content) && content.startsWith("@") && talker != null && talker.length() > 20) {
                isGroupChat = true;
            }
        }
        boolean isPrivateChat = !isGroupChat;
        if (isPrivateChat || isGroupChat) return true;
        return false;
    } catch (Exception e) {
        return false;
    }
}

private boolean isCurrentTimeInRuleRange(Map<String, Object> rule) {
    String startTime = (String) rule.get("startTime");
    String endTime = (String) rule.get("endTime");
    if (TextUtils.isEmpty(startTime) || TextUtils.isEmpty(endTime)) {
        return true;
    }
    try {
        String[] startParts = startTime.split(":");
        int startHour = Integer.parseInt(startParts[0]);
        int startMinute = Integer.parseInt(startParts[1]);
        String[] endParts = endTime.split(":");
        int endHour = Integer.parseInt(endParts[0]);
        int endMinute = Integer.parseInt(endParts[1]);
        Calendar now = Calendar.getInstance();
        int currentHour = now.get(Calendar.HOUR_OF_DAY);
        int currentMinute = now.get(Calendar.MINUTE);
        int startTimeInMinutes = startHour * 60 + startMinute;
        int endTimeInMinutes = endHour * 60 + endMinute;
        int currentTimeInMinutes = currentHour * 60 + currentMinute;
        if (endTimeInMinutes < startTimeInMinutes) {
            return currentTimeInMinutes >= startTimeInMinutes || currentTimeInMinutes < endTimeInMinutes;
        } else {
            return currentTimeInMinutes >= startTimeInMinutes && currentTimeInMinutes < endTimeInMinutes;
        }
    } catch (Exception e) {
        return true;
    }
}

private void processAutoReply(final Object msgInfoBean) {
    try {
        final String rawContent = getFieldString(msgInfoBean, "originContent");
        final String talker = getFieldString(msgInfoBean, "talker");
        final long msgId = getFieldLong(msgInfoBean, "msgId", 0L);
        final boolean isGroupChat = !TextUtils.isEmpty(talker) && talker.contains("@chatroom");

        // 部分框架在群聊中 sendTalker 为空，真实发送者 wxid 以 "wxid_xxx:\n" 前缀拼在 originContent 里
        // 例如：rawContent = "wxid_bb6iic43o86m22:\n1"
        // 需要从 content 里解析出真实 senderWxid，并还原干净的消息内容
        String parsedSenderFromContent = "";
        final String content;
        if (isGroupChat && !TextUtils.isEmpty(rawContent)) {
            int colonNlIdx = rawContent.indexOf(":\n");
            if (colonNlIdx > 0) {
                String prefix = rawContent.substring(0, colonNlIdx);
                // 简单校验：前缀不含空格且长度合理（wxid一般10~50字符），认为是发送者wxid
                if (!prefix.contains(" ") && prefix.length() >= 5 && prefix.length() <= 60) {
                    parsedSenderFromContent = prefix;
                    content = rawContent.substring(colonNlIdx + 2); // 跳过 ":\n"
                } else {
                    content = rawContent;
                }
            } else {
                content = rawContent;
            }
        } else {
            content = rawContent;
        }

        // 按优先级获取 senderWxid：sendTalker > fromUser > content前缀解析 > talker
        String senderWxidRaw = getFieldString(msgInfoBean, "sendTalker");
        if (TextUtils.isEmpty(senderWxidRaw)) senderWxidRaw = getFieldString(msgInfoBean, "fromUser");
        if (TextUtils.isEmpty(senderWxidRaw)) senderWxidRaw = parsedSenderFromContent;
        final String senderWxid = senderWxidRaw;

        final String finalSenderWxid = TextUtils.isEmpty(senderWxid) ? talker : senderWxid;
        final boolean isPrivateChat = !isGroupChat;

        boolean isAtMe = false;
        boolean isNotifyAll = false;
        if (isGroupChat) {
            // 优先使用框架原生字段（最准）
            isAtMe = callBoolMethod(msgInfoBean, "isAtMe", false);
            isNotifyAll = callBoolMethod(msgInfoBean, "isNotifyAll", false);

            // 兜底：极少数机型字段异常时再用内容判断
            if (!isAtMe) {
                String myAlias = getLoginAlias();
                if (!TextUtils.isEmpty(myAlias) && !TextUtils.isEmpty(content)) {
                    isAtMe = content.startsWith("@" + myAlias + " ");
                }
            }
            if (!isNotifyAll && !TextUtils.isEmpty(content)) {
                isNotifyAll = content.contains("@全体成员");
            }
        }

        boolean isPatMe = false;
        String myWxid = getLoginWxid();
        boolean isPatMsg = content != null && (content.contains("拍了拍") || content.contains("patted"));
        if (isPatMsg) {
            Object patMsgObj = getFieldObject(msgInfoBean, "patMsg");
            if (patMsgObj != null) {
                String fromUser = getFieldString(patMsgObj, "fromUser");
                String pattedUser = getFieldString(patMsgObj, "pattedUser");
                if (!TextUtils.isEmpty(fromUser) && !TextUtils.isEmpty(pattedUser) && !fromUser.equals(myWxid) && pattedUser.equals(myWxid)) {
                    isPatMe = true;
                }
            }
        }

        List rules = loadAutoReplyRules();
        List matchedRules = new ArrayList();

        for (int i = 0; i < rules.size(); i++) {
            Map<String, Object> rule = (Map<String, Object>) rules.get(i);
            boolean enabled = (Boolean) rule.get("enabled");
            if (!enabled) continue;
            if (!isCurrentTimeInRuleRange(rule)) continue;

            int targetType = (Integer) rule.get("targetType");

            // 在群聊中，使用senderWxid作为实际的发送者Wxid
            // Bug1修复：senderWxid为空时（部分机型群聊sendTalker为空），fallback到finalSenderWxid，避免排除/白名单逻辑失效
            String actualSenderWxid = isGroupChat
                ? (TextUtils.isEmpty(senderWxid) ? finalSenderWxid : senderWxid)
                : finalSenderWxid;

            debugLog("[调试-目标检查] talker=" + talker + ", senderWxid=" + senderWxid + ", actualSenderWxid=" + actualSenderWxid + ", targetType=" + targetType);

            if (targetType != TARGET_TYPE_NONE) {
                boolean targetMatch = false;
                Set targetWxids = (Set) rule.get("targetWxids");
                debugLog("[调试-目标匹配] targetWxids=" + (targetWxids != null ? targetWxids.size() : 0) + ", contains talker=" + (targetWxids != null && targetWxids.contains(talker)));

                if (targetType == TARGET_TYPE_FRIEND) {
                    if (isPrivateChat && targetWxids.contains(actualSenderWxid)) targetMatch = true;
                } else if (targetType == TARGET_TYPE_GROUP) {
                    if (isGroupChat && targetWxids.contains(talker)) targetMatch = true;
                } else if (targetType == TARGET_TYPE_BOTH) {
                    // 严格模式：
                    // 1) 只要配置了任一“指定目标”（好友/群/群成员），就必须命中其一才回复
                    // 2) 三者都没配置时，不回复任何人（避免误全局）
                    Set includedGroupMembers = (Set) rule.get("includedGroupMemberWxids");
                    Set includedGroupIds = (Set) rule.get("includedGroupIdsForMemberFilter");

                    boolean hasFriendTarget = false;
                    boolean hasGroupTarget = false;
                    boolean hasMemberTarget = (includedGroupMembers != null && !includedGroupMembers.isEmpty());

                    if (targetWxids != null && !targetWxids.isEmpty()) {
                        for (Object wxidObj : targetWxids) {
                            String wxidStr = (String) wxidObj;
                            if (TextUtils.isEmpty(wxidStr)) continue;
                            if (wxidStr.endsWith("@chatroom")) hasGroupTarget = true;
                            else hasFriendTarget = true;
                        }
                    }

                    boolean hasAnySpecificTarget = hasFriendTarget || hasGroupTarget || hasMemberTarget;

                    // 没有任何指定目标 -> 不匹配（防止全量回复）
                    if (!hasAnySpecificTarget) {
                        debugLog("[调试-跳过] targetType=BOTH 但未配置任何指定目标（好友/群聊/群成员）");
                        targetMatch = false;
                    } else {
                        if (isPrivateChat) {
                            // 私聊：必须命中指定好友
                            targetMatch = hasFriendTarget && targetWxids != null && targetWxids.contains(actualSenderWxid);
                        } else if (isGroupChat) {
                            boolean groupMatched = hasGroupTarget && targetWxids != null && targetWxids.contains(talker);

                            boolean memberScopeMatched = false;
                            if (hasMemberTarget) {
                                // 若配置了“成员过滤群ID”，则当前群必须在这个范围内
                                boolean groupInMemberScope = (includedGroupIds == null || includedGroupIds.isEmpty() || includedGroupIds.contains(talker));
                                if (groupInMemberScope) {
                                    memberScopeMatched = includedGroupMembers.contains(actualSenderWxid);
                                }
                            }

                            // 群聊命中：指定群命中 或 指定成员命中
                            targetMatch = groupMatched || memberScopeMatched;
                        } else {
                            targetMatch = false;
                        }
                    }
                }
                debugLog("[调试-目标匹配] targetMatch=" + targetMatch);
                if (!targetMatch) continue;
            } else {
                // 不指定模式下，执行排除好友/群聊逻辑
                Set excludedWxids = (Set) rule.get("excludedWxids");
                if (excludedWxids != null && !excludedWxids.isEmpty()) {
                    if (isPrivateChat && excludedWxids.contains(actualSenderWxid)) continue;
                    if (isGroupChat && excludedWxids.contains(talker)) continue;
                }
            }

            if (isGroupChat) {
                if (targetType != TARGET_TYPE_BOTH) {
                    // 不指定模式：执行排除群成员逻辑
                    Set excludedGroupMembers = (Set) rule.get("excludedGroupMemberWxids");
                    if (excludedGroupMembers != null && !excludedGroupMembers.isEmpty()) {
                        if (excludedGroupMembers.contains(actualSenderWxid)) {
                            continue;
                        }
                    }
                } else {
                    // BOTH 模式下命中已在 targetMatch 阶段严格判定，这里不再改写结果
                    debugLog("[调试-群成员检查] targetType=BOTH 已在目标匹配阶段完成严格校验");
                }
            }

            int atTriggerType = (Integer) rule.get("atTriggerType");
            debugLog("[调试-@触发] atTriggerType=" + atTriggerType + ", isAtMe=" + isAtMe + ", isNotifyAll=" + isNotifyAll);
            if (isGroupChat) {
                int actualAtType = isNotifyAll ? AT_TRIGGER_ALL : (isAtMe ? AT_TRIGGER_ME : AT_TRIGGER_NONE);
                debugLog("[调试-@触发] actualAtType=" + actualAtType);
                if ((atTriggerType == AT_TRIGGER_ME && actualAtType != AT_TRIGGER_ME) || (atTriggerType == AT_TRIGGER_ALL && actualAtType != AT_TRIGGER_ALL)) {
                    debugLog("[调试-跳过] @触发条件不满足");
                    continue;
                }
            } else {
                if (atTriggerType != AT_TRIGGER_NONE) continue;
            }

            int patTriggerType = (Integer) rule.get("patTriggerType");
            if (patTriggerType == PAT_TRIGGER_ME && !isPatMe) {
                continue;
            }

            boolean isMatch = false;
            if (isPatMsg && patTriggerType == PAT_TRIGGER_ME) {
                isMatch = true;
            } else {
                int matchType = (Integer) rule.get("matchType");
                String keyword = (String) rule.get("keyword");
                debugLog("[调试-关键词匹配] matchType=" + matchType + ", keyword=" + keyword + ", content=" + content);
                switch (matchType) {
                    case MATCH_TYPE_ANY:
                        isMatch = true;
                        debugLog("[调试-关键词匹配] 匹配类型=ANY");
                        break;
                    case MATCH_TYPE_EXACT:
                        isMatch = content.equals(keyword);
                        debugLog("[调试-关键词匹配] 匹配类型=EXACT, isMatch=" + isMatch);
                        break;
                    case MATCH_TYPE_REGEX:
                        Pattern compiledPattern = (Pattern) rule.get("compiledPattern");
                        if (compiledPattern != null) isMatch = compiledPattern.matcher(content).matches();
                        else isMatch = false;
                        debugLog("[调试-关键词匹配] 匹配类型=REGEX, isMatch=" + isMatch);
                        break;
                    case MATCH_TYPE_FUZZY: default:
                        isMatch = content.contains(keyword);
                        debugLog("[调试-关键词匹配] 匹配类型=FUZZY, isMatch=" + isMatch);
                        break;
                }
            }

            if (isMatch) {
                matchedRules.add(rule);
            }
        }

        if (matchedRules.isEmpty()) return; // 没命中规则就不说话

        debugLog("[规则匹配] 命中 " + matchedRules.size() + " 条规则，准备执行。");

        for (int i = 0; i < matchedRules.size(); i++) {
            final Map<String, Object> finalRule = (Map<String, Object>) matchedRules.get(i);
            long delaySeconds = (Long) finalRule.get("delaySeconds");
            String replyContent = buildReplyContent((String) finalRule.get("reply"), msgInfoBean);
            int replyType = (Integer) finalRule.get("replyType");
            boolean replyAsQuote = (Boolean) finalRule.get("replyAsQuote");
            List mediaPaths = (List) finalRule.get("mediaPaths");

            if (delaySeconds > 0) {
                debugLog("[动作计划] 规则延迟 " + delaySeconds + " 秒后执行...");
                final String finalReplyContent = replyContent;
                final int finalReplyType = replyType;
                final boolean finalReplyAsQuote_flag = replyAsQuote;
                final List finalMediaPaths = mediaPaths;
                final Object finalMsgInfoBean = msgInfoBean;
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    public void run() {
                        sendReplyDirectly(finalRule, finalReplyContent, finalReplyType, finalReplyAsQuote_flag, finalMediaPaths, talker, content, msgId, finalMsgInfoBean);
                    }
                }, delaySeconds * 1000L);
            } else {
                sendReplyDirectly(finalRule, replyContent, replyType, replyAsQuote, mediaPaths, talker, content, msgId, msgInfoBean);
            }
        }
    } catch (Exception e) {
        debugLog("[异常] 规则匹配/执行时出错: " + e.getMessage());
    }
}

private void sendReplyDirectly(Map<String, Object> finalRule, String replyContent, int replyType, boolean replyAsQuote, List mediaPaths, String talker, String content, long msgId, Object msgInfoBean) {
    try {
        boolean sent = false;
        String notifyContent = replyContent;

        switch (replyType) {
            case REPLY_TYPE_XIAOZHI_AI:
                debugLog("[执行回复] 动作: 调用小智AI, 目标: " + talker);
                aiQuoteMsgIdMap.put(talker, msgId);
                aiQuoteFlagMap.put(talker, replyAsQuote);
                processAIResponse(msgInfoBean);
                sent = true;
                notifyContent = "小智AI已触发";
                break;
            case REPLY_TYPE_ZHILIA_AI:
                debugLog("[执行回复] 动作: 调用智聊AI, 目标: " + talker);
                sendZhiliaAiReply(talker, content, replyAsQuote, msgId);
                sent = true;
                notifyContent = "智聊AI已触发";
                break;
            case REPLY_TYPE_IMAGE:
            case REPLY_TYPE_VIDEO:
            case REPLY_TYPE_EMOJI:
            case REPLY_TYPE_FILE:
                debugLog("[执行回复] 动作: 发送多媒体文件, 目标: " + talker);
                if (mediaPaths != null && !mediaPaths.isEmpty()) {
                    long mediaDelaySeconds = (Long) finalRule.get("mediaDelaySeconds");
                    int successCount = 0;
                    for (int j = 0; j < mediaPaths.size(); j++) {
                        String path = (String) mediaPaths.get(j);
                        File file = new File(path);
                        if (file.exists() && file.isFile()) {
                            String fileName = file.getName();
                            switch (replyType) {
                                case REPLY_TYPE_IMAGE: sendImage(talker, path); break;
                                case REPLY_TYPE_VIDEO: sendVideo(talker, path); break;
                                case REPLY_TYPE_EMOJI: sendEmoji(talker, path); break;
                                case REPLY_TYPE_FILE: shareFile(talker, fileName, path, ""); break;
                            }
                            successCount++;
                            if (j < mediaPaths.size() - 1) {
                                try { Thread.sleep(mediaDelaySeconds * 1000); } catch (Exception e) {}
                            }
                        }
                    }
                    if (successCount > 0) {
                        sent = true;
                        notifyContent = "已发送" + successCount + "个媒体文件";
                    }
                }
                break;
            case REPLY_TYPE_VOICE_FILE_LIST:
                debugLog("[执行回复] 动作: 按列表发送语音, 目标: " + talker);
                if (mediaPaths != null && !mediaPaths.isEmpty()) {
                    long mediaDelaySeconds = (Long) finalRule.get("mediaDelaySeconds");
                    int successCount = 0;
                    for (int j = 0; j < mediaPaths.size(); j++) {
                        String voicePath = (String) mediaPaths.get(j);
                        File file = new File(voicePath);
                        if (file.exists() && file.isFile()) {
                            sendVoice(talker, voicePath);
                            successCount++;
                            if (j < mediaPaths.size() - 1) {
                                try { Thread.sleep(mediaDelaySeconds * 1000); } catch (Exception e) {}
                            }
                        }
                    }
                    if (successCount > 0) {
                        sent = true;
                        notifyContent = "已发送" + successCount + "条语音";
                    }
                }
                break;
            case REPLY_TYPE_VOICE_FOLDER:
                debugLog("[执行回复] 动作: 随机发送文件夹内语音, 目标: " + talker);
                if (mediaPaths != null && !mediaPaths.isEmpty()) {
                    String folderPath = (String) mediaPaths.get(0);
                    List voiceFiles = getVoiceFilesFromFolder(folderPath);
                    if (voiceFiles != null && !voiceFiles.isEmpty()) {
                        String randomVoicePath = (String) voiceFiles.get(new Random().nextInt(voiceFiles.size()));
                        sendVoice(talker, randomVoicePath);
                        sent = true;
                        notifyContent = "随机语音: " + new File(randomVoicePath).getName();
                    }
                }
                break;
            case REPLY_TYPE_CARD:
                debugLog("[执行回复] 动作: 发送名片, 目标: " + talker);
                if (!TextUtils.isEmpty(replyContent)) {
                    long mediaDelaySeconds = (Long) finalRule.get("mediaDelaySeconds");
                    String[] wxids = replyContent.split(";;;");
                    int successCount = 0;
                    for (int j = 0; j < wxids.length; j++) {
                        String wxidToShare = wxids[j].trim();
                        if (!TextUtils.isEmpty(wxidToShare)) {
                            sendShareCard(talker, wxidToShare);
                            successCount++;
                            if (j < wxids.length - 1) {
                                try { Thread.sleep(mediaDelaySeconds * 1000); } catch (Exception e) {}
                            }
                        }
                    }
                    if (successCount > 0) {
                        sent = true;
                        notifyContent = "已发送" + successCount + "张名片";
                    }
                }
                break;
            case REPLY_TYPE_INVITE_GROUP:
                debugLog("[执行回复] 动作: 邀请加入群聊, 目标: " + talker);
                if (!TextUtils.isEmpty(replyContent)) {
                    long mediaDelaySeconds = (Long) finalRule.get("mediaDelaySeconds");
                    String[] groupIds = replyContent.split(";;;");
                    String effectiveSenderWxid = resolveRealSenderWxid(msgInfoBean);
                    if (TextUtils.isEmpty(effectiveSenderWxid)) effectiveSenderWxid = talker;

                    int successCount = 0;
                    for (int j = 0; j < groupIds.length; j++) {
                        String groupId = groupIds[j].trim();
                        if (!TextUtils.isEmpty(groupId)) {
                            inviteChatroomMember(groupId, effectiveSenderWxid);
                            successCount++;
                            if (j < groupIds.length - 1) {
                                try { Thread.sleep(mediaDelaySeconds * 1000); } catch (Exception e) {}
                            }
                        }
                    }
                    if (successCount > 0) {
                        sent = true;
                        notifyContent = "已邀请加入" + successCount + "个群";
                    }
                }
                break;
            case REPLY_TYPE_TEXT:
            default:
                debugLog("[执行回复] 动作: " + (replyAsQuote ? "引用发送文本" : "发送文本") + " -> " + replyContent);
                if (replyAsQuote) {
                    sendQuoteMsg(talker, msgId, replyContent);
                } else {
                    sendText(talker, replyContent);
                }
                sent = true;
                notifyContent = replyContent;
                break;
        }

        if (sent) {
            notifyAutoReplySuccess(talker, msgInfoBean, finalRule, notifyContent);
        }
    } catch (Exception e) {
        debugLog("[异常] 实际发出回复时出错: " + e.getMessage());
    }
}

private List getVoiceFilesFromFolder(String folderPath) {
    List voiceFiles = new ArrayList();
    File folder = new File(folderPath);
    if (!folder.exists() || !folder.isDirectory()) return voiceFiles;
    FilenameFilter audioFilter = new FilenameFilter() {
        public boolean accept(File dir, String name) {
            String lowerCaseName = name.toLowerCase();
            return lowerCaseName.endsWith(".mp3") || lowerCaseName.endsWith(".wav") || lowerCaseName.endsWith(".ogg") || lowerCaseName.endsWith(".aac")  || lowerCaseName.endsWith(".silk");
        }
    };
    File[] files = folder.listFiles(audioFilter);
    if (files != null) {
        for (int i = 0; i < files.length; i++) {
            if (files[i].isFile()) voiceFiles.add(files[i].getAbsolutePath());
        }
    }
    return voiceFiles;
}

private String getGroupName(String groupWxid) {
    try {
        if (sCachedGroupList == null) sCachedGroupList = getGroupList();
        if (sCachedGroupList != null) {
            for (int i = 0; i < sCachedGroupList.size(); i++) {
                GroupInfo groupInfo = (GroupInfo) sCachedGroupList.get(i);
                if (groupWxid.equals(groupInfo.getRoomId())) return groupInfo.getName();
            }
        }
    } catch (Exception e) {}
    return "未知群聊";
}

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
        return friendWxid;
    }
    return friendWxid;
}

private String resolveRealSenderWxid(Object msgInfoBean) {
    try {
        String talker = getFieldString(msgInfoBean, "talker");
        String rawContent = getFieldString(msgInfoBean, "originContent");
        boolean isGroupChat = !TextUtils.isEmpty(talker) && talker.contains("@chatroom");

        String senderWxid = getFieldString(msgInfoBean, "sendTalker");
        if (TextUtils.isEmpty(senderWxid)) senderWxid = getFieldString(msgInfoBean, "fromUser");

        if (isGroupChat && TextUtils.isEmpty(senderWxid) && !TextUtils.isEmpty(rawContent)) {
            int colonNlIdx = rawContent.indexOf(":\n");
            if (colonNlIdx > 0) {
                String prefix = rawContent.substring(0, colonNlIdx);
                if (!prefix.contains(" ") && prefix.length() >= 5 && prefix.length() <= 60) {
                    senderWxid = prefix;
                }
            }
        }

        if (TextUtils.isEmpty(senderWxid) && !isGroupChat) {
            senderWxid = talker;
        }

        return TextUtils.isEmpty(senderWxid) ? "" : senderWxid;
    } catch (Exception e) {
        return "";
    }
}


// 统一解析 Wxid 为展示名称（智能区分群聊/好友）
private String getDisplayNameForWxid(String wxid) {
    if (TextUtils.isEmpty(wxid)) return "";
    if (wxid.endsWith("@chatroom")) {
        String groupName = getGroupName(wxid);
        return (TextUtils.isEmpty(groupName) || "未知群聊".equals(groupName)) ? wxid : groupName;
    } else {
        String friendName = getFriendDisplayName(wxid);
        return (TextUtils.isEmpty(friendName) || friendName.equals(wxid)) ? wxid : friendName;
    }
}

private String buildReplyContent(String template, Object msgInfoBean) {
    try {
        String result = template;

        String talker = getFieldString(msgInfoBean, "talker");
        boolean isGroupChat = !TextUtils.isEmpty(talker) && talker.contains("@chatroom");
        boolean isPrivateChat = !isGroupChat;

        String senderWxid = resolveRealSenderWxid(msgInfoBean);

        String senderName = "";
        if (isPrivateChat) {
            senderName = getFriendDisplayName(senderWxid);
        } else if (isGroupChat) {
            senderName = getFriendName(senderWxid, talker);
            if (TextUtils.isEmpty(senderName)) {
                senderName = getFriendDisplayName(senderWxid);
            }
        }

        if (TextUtils.isEmpty(senderName)) senderName = senderWxid;
        if (TextUtils.isEmpty(senderName)) senderName = "未知用户";

        result = result.replace("%senderName%", senderName);
        result = result.replace("%senderWxid%", TextUtils.isEmpty(senderWxid) ? "" : senderWxid);

        if (isGroupChat && !TextUtils.isEmpty(senderWxid) && !senderWxid.endsWith("@chatroom")) {
            result = result.replace("%atSender%", "[AtWx=" + senderWxid + "]");
        } else {
            result = result.replace("%atSender%", "");
        }

        if (isGroupChat) {
            String groupName = getGroupName(talker);
            if (TextUtils.isEmpty(groupName) || "未知群聊".equals(groupName)) {
                groupName = talker;
            }
            result = result.replace("%groupName%", groupName);
        } else {
            result = result.replace("%groupName%", "");
        }

        result = result.replace("%time%", new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
        return result;
    } catch (Exception e) {
        debugLog("[异常] 构建回复模板内容时出错: " + e.getMessage());
        return template;
    }
}


// === UI 美化与布局构建 ===
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
    GradientDrawable shape = new GradientDrawable();
    shape.setCornerRadius(32);
    shape.setColor(Color.parseColor("#FFFFFF"));
    layout.setBackground(shape);
    try { layout.setElevation(8); } catch (Exception e) {}
    return layout;
}

private LinearLayout newRootContainer(Activity a) {
    LinearLayout root = new LinearLayout(a);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(24, 24, 24, 24);
    root.setBackgroundColor(Color.parseColor("#FAFBF9"));
    return root;
}

private ScrollView wrapInScroll(Activity a, View child) {
    ScrollView sv = new ScrollView(a);
    sv.addView(child);
    return sv;
}

private Button newActionButton(String text, View.OnClickListener l) {
    Button b = new Button(getTopActivity());
    b.setText(text);
    styleUtilityButton(b);
    if (l != null) b.setOnClickListener(l);
    return b;
}

private void addCardTitle(LinearLayout card, String title) {
    card.addView(createSectionTitle(title));
}

private void addCardText(LinearLayout card, String text, int size) {
    card.addView(createTextView(getTopActivity(), text, size, 0));
}

private LinearLayout newCardWithTitle(String title) {
    LinearLayout card = createCardLayout();
    addCardTitle(card, title);
    return card;
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
    editText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
        public void onFocusChange(View v, boolean hasFocus) {
            GradientDrawable bg = (GradientDrawable) v.getBackground();
            bg.setStroke(hasFocus ? 3 : 2, Color.parseColor(hasFocus ? "#7AA6C2" : "#E6E9EE"));
        }
    });
    return editText;
}

private LinearLayout horizontalRow(View left, View right) {
    LinearLayout row = new LinearLayout(getTopActivity());
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    );
    params.setMargins(0, 8, 0, 8);
    row.setLayoutParams(params);
    LinearLayout.LayoutParams lpLeft = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    LinearLayout.LayoutParams lpRight = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    left.setLayoutParams(lpLeft);
    right.setLayoutParams(lpRight);
    row.addView(left);
    row.addView(right);
    return row;
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
    Button neutralButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
    if (neutralButton != null) {
        neutralButton.setTextColor(Color.parseColor("#4A90E2"));
        neutralButton.setBackgroundColor(Color.TRANSPARENT);
        neutralButton.setAllCaps(false);
    }
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

private TextView createPromptText(String text) {
    TextView tv = new TextView(getTopActivity());
    tv.setText(text);
    tv.setTextSize(12);
    tv.setTextColor(Color.parseColor("#666666"));
    tv.setPadding(0, 0, 0, 16);
    return tv;
}

// 【新增】创建点击插入变量的小标签
private TextView createVariableChip(final String text, final String desc, final EditText targetEdit) {
    TextView chip = new TextView(getTopActivity());
    chip.setText(text + " (" + desc + ")");
    chip.setTextSize(12);
    chip.setTextColor(Color.parseColor("#4A90E2"));
    chip.setPadding(20, 12, 20, 12);

    GradientDrawable bg = new GradientDrawable();
    bg.setColor(Color.parseColor("#EAF2FA"));
    bg.setCornerRadius(16);
    chip.setBackground(bg);

    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    lp.setMargins(0, 0, 16, 16);
    chip.setLayoutParams(lp);

    chip.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            if (targetEdit != null && targetEdit.getVisibility() == View.VISIBLE) {
                int start = Math.max(targetEdit.getSelectionStart(), 0);
                int end = Math.max(targetEdit.getSelectionEnd(), 0);
                targetEdit.getText().replace(Math.min(start, end), Math.max(start, end), text, 0, text.length());
                targetEdit.requestFocus();
                toast("已插入变量: " + text);
            } else {
                toast("当前输入模式不支持插入变量");
            }
        }
    });
    return chip;
}

// --- UI 辅助方法 ---
private LinearLayout createLinearLayout(Context context, int orientation, int padding) {
    LinearLayout layout = new LinearLayout(context);
    layout.setOrientation(orientation);
    layout.setPadding(padding, padding, padding, padding);
    return layout;
}

private TextView createTextView(Context context, String text, int textSize, int paddingBottom) {
    TextView textView = new TextView(context);
    textView.setText(text);
    if (textSize > 0) textView.setTextSize(textSize);
    textView.setPadding(0, 0, 0, paddingBottom);
    return textView;
}

private EditText createEditText(Context context, String hint, String text, int minLines, int inputType) {
    EditText editText = new EditText(context);
    editText.setHint(hint);
    if (text != null) editText.setText(text);
    if (minLines > 0) editText.setMinLines(minLines);
    if (inputType != 0) editText.setInputType(inputType);
    return editText;
}

private Button createButton(Context context, String text, View.OnClickListener listener) {
    Button button = new Button(context);
    button.setText(text);
    button.setOnClickListener(listener);
    return button;
}

private LinearLayout createSwitchRow(Context context, String labelText, boolean isChecked, View.OnClickListener listener) {
    LinearLayout row = new LinearLayout(context);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(0, 16, 0, 16);

    TextView label = new TextView(context);
    label.setText(labelText);
    label.setTextSize(16);
    label.setTextColor(Color.parseColor("#333333"));
    LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    label.setLayoutParams(labelParams);

    CheckBox checkBox = new CheckBox(context);
    checkBox.setChecked(isChecked);
    // Bind the listener directly to the checkbox state changes if preferred, or keep as OnClickListener
    checkBox.setOnClickListener(listener);
    LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    checkParams.setMargins(16, 0, 0, 0);
    checkBox.setLayoutParams(checkParams);

    label.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            checkBox.toggle();
        }
    });

    row.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            checkBox.toggle();
        }
    });

    row.addView(label);
    row.addView(checkBox);
    return row;
}

private RadioGroup createRadioGroup(Context context, int orientation) {
    RadioGroup radioGroup = new RadioGroup(context);
    radioGroup.setOrientation(orientation);
    return radioGroup;
}

private RadioButton createRadioButton(Context context, String text) {
    RadioButton radioButton = new RadioButton(context);
    radioButton.setText(text);
    radioButton.setId(View.generateViewId());
    return radioButton;
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

private int dpToPx(int dp) {
    return (int) (dp * getTopActivity().getResources().getDisplayMetrics().density);
}

private void showMultiSelectDialog(String title, List allItems, List idList, Set selectedIds, String searchHint, final Runnable onConfirm, final Runnable updateList) {
    try {
        final Set tempSelected = new HashSet(selectedIds);
        ScrollView scrollView = new ScrollView(getTopActivity());
        LinearLayout mainLayout = new LinearLayout(getTopActivity());
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(24, 24, 24, 24);
        mainLayout.setBackgroundColor(Color.parseColor("#FAFBF9"));
        scrollView.addView(mainLayout);
        final EditText searchEditText = createStyledEditText(searchHint, "");
        searchEditText.setSingleLine(true);
        mainLayout.addView(searchEditText);
        final ListView listView = new ListView(getTopActivity());
        setupListViewTouchForScroll(listView);
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(50));
        listView.setLayoutParams(listParams);
        mainLayout.addView(listView);
        final List currentFilteredIds = new ArrayList();
        final List currentFilteredNames = new ArrayList();
        final Runnable updateListRunnable = new Runnable() {
            public void run() {
                String searchText = searchEditText.getText().toString().toLowerCase();
                currentFilteredIds.clear();
                currentFilteredNames.clear();
                for (int i = 0; i < allItems.size(); i++) {
                    String id = (String) idList.get(i);
                    String name = (String) allItems.get(i);
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
                adjustListViewHeight(listView, currentFilteredIds.size());
                if (updateList != null) updateList.run();
                final AlertDialog currentDialog = (AlertDialog) searchEditText.getTag();
                if (currentDialog != null) {
                    updateSelectAllButton(currentDialog, currentFilteredIds, tempSelected);
                }
            }
        };
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
                String selected = (String) currentFilteredIds.get(pos);
                if (listView.isItemChecked(pos)) tempSelected.add(selected);
                else tempSelected.remove(selected);
                if (updateList != null) updateList.run();
                final AlertDialog currentDialog = (AlertDialog) searchEditText.getTag();
                if (currentDialog != null) {
                    updateSelectAllButton(currentDialog, currentFilteredIds, tempSelected);
                }
            }
        });
        final Handler searchHandler = new Handler(Looper.getMainLooper());
        final Runnable searchRunnable = new Runnable() {
            public void run() {
                updateListRunnable.run();
            }
        };
        searchEditText.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (searchRunnable != null) searchHandler.removeCallbacks(searchRunnable);
            }
            public void afterTextChanged(Editable s) {
                searchHandler.postDelayed(searchRunnable, 300);
            }
        });

        final DialogInterface.OnClickListener fullSelectListener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                boolean shouldSelectAll = shouldSelectAll(currentFilteredIds, tempSelected);
                for (int i = 0; i < currentFilteredIds.size(); i++) {
                    String id = (String) currentFilteredIds.get(i);
                    if (shouldSelectAll) {
                        tempSelected.add(id);
                    } else {
                        tempSelected.remove(id);
                    }
                    listView.setItemChecked(i, shouldSelectAll);
                }
                listView.getAdapter().notifyDataSetChanged();
                listView.requestLayout();
                updateSelectAllButton((AlertDialog) dialog, currentFilteredIds, tempSelected);
            }
        };

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
        }, "全选", fullSelectListener);
        searchEditText.setTag(dialog);

        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            public void onShow(DialogInterface dialogInterface) {
                setupUnifiedDialog((AlertDialog) dialogInterface);
                Button neutralBtn = ((AlertDialog) dialogInterface).getButton(AlertDialog.BUTTON_NEUTRAL);
                if (neutralBtn != null) {
                    neutralBtn.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) {
                            fullSelectListener.onClick(dialog, AlertDialog.BUTTON_NEUTRAL);
                        }
                    });
                }
            }
        });
        dialog.show();
        updateListRunnable.run();
    } catch (Exception e) {
        toast("弹窗失败: " + e.getMessage());
        e.printStackTrace();
    }
}

private void showAutoReplySettingDialog() {
    try {
        final Activity a = getTopActivity();
        if (a == null) { toast("无法获取窗口"); return; }

        LinearLayout root = newRootContainer(a);

        LinearLayout managementCard = newCardWithTitle("🤖 自动功能设置");

        managementCard.addView(newActionButton("🤝 好友请求自动处理", new View.OnClickListener() {
            public void onClick(View v) { showAutoAcceptFriendDialog(); }
        }));
        managementCard.addView(newActionButton("👋 添加好友自动回复", new View.OnClickListener() {
            public void onClick(View v) { showGreetOnAcceptedDialog(); }
        }));
        managementCard.addView(newActionButton("📝 管理消息回复规则", new View.OnClickListener() {
            public void onClick(View v) { showAutoReplyRulesDialog(); }
        }));
        managementCard.addView(newActionButton("🧠 AI 配置", new View.OnClickListener() {
            public void onClick(View v) { showAIChoiceDialog(); }
        }));
        managementCard.addView(newActionButton("📋 查看运行日志", new View.OnClickListener() {
            public void onClick(View v) { showLogDialog(); }
        }));
        managementCard.addView(newActionButton("🔔 回复成功提醒设置", new View.OnClickListener() {
            public void onClick(View v) { showAutoReplyNotifySettingDialog(); }
        }));
        managementCard.addView(newActionButton("💾 备份与恢复", new View.OnClickListener() {
            public void onClick(View v) { showBackupMenuDialog(); }
        }));

        LinearLayout switchRow = createSwitchRow(
            a,
            "📝 开启运行日志 (便于排错)",
            getBoolean(ENABLE_LOG_KEY, true),
            new View.OnClickListener() { public void onClick(View v) {} }
        );
        managementCard.addView(switchRow);
        final CheckBox finalLogCheck = (CheckBox) switchRow.getChildAt(1);

        root.addView(managementCard);

        AlertDialog dialog = buildCommonAlertDialog(
            a,
            "✨ 自动回复统一设置 ✨",
            wrapInScroll(a, root),
            null, null,
            "❌ 关闭",
            new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    putBoolean(ENABLE_LOG_KEY, finalLogCheck.isChecked());
                    d.dismiss();
                }
            },
            null, null
        );
        dialog.show();

    } catch (Exception e) {
        toast("打开设置界面失败: " + e.getMessage());
    }
}

private void showAIChoiceDialog() {
    Activity a = getTopActivity();
    if (a == null) {
        toast("无法获取窗口");
        return;
    }

    LinearLayout root = newRootContainer(a);

    root.addView(newActionButton("小智AI 配置", new View.OnClickListener() {
        public void onClick(View v) {
            AlertDialog d = (AlertDialog) v.getTag();
            if (d != null) d.dismiss();
            showXiaozhiAIConfigDialog();
        }
    }));

    root.addView(newActionButton("智聊AI 配置", new View.OnClickListener() {
        public void onClick(View v) {
            AlertDialog d = (AlertDialog) v.getTag();
            if (d != null) d.dismiss();
            showZhiliaAIConfigDialog();
        }
    }));

    final AlertDialog choiceDialog = buildCommonAlertDialog(
        a,
        "🧠 选择AI配置",
        wrapInScroll(a, root),
        null, null,
        "❌ 取消", null,
        null, null
    );

    // 给按钮挂 dialog 引用，避免再写重复闭包变量
    for (int i = 0; i < root.getChildCount(); i++) {
        View child = root.getChildAt(i);
        if (child instanceof Button) {
            child.setTag(choiceDialog);
        }
    }

    choiceDialog.show();
}

private void showLogDialog() {
    try {
        final Activity act = getTopActivity();
        if (act == null) {
            toast("无法获取窗口");
            return;
        }

        final StringBuilder logContent = new StringBuilder();
        logContent.append("=== 自动回复运行日志 ===\n");
        logContent.append("时间: ")
                .append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()))
                .append("\n\n");

        try {
            File logDir = new File(act.getExternalFilesDir(null), "logs");
            File logFile = new File(logDir, "auto_reply_log.txt");
            if (logFile.exists()) {
                java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(logFile));
                String line;
                int count = 0;
                while ((line = br.readLine()) != null && count < 2000) {
                    logContent.append(line).append("\n");
                    count++;
                }
                br.close();
            } else {
                logContent.append("暂无日志文件。\n");
            }
        } catch (Exception e) {
            logContent.append("读取日志失败: ").append(e.getMessage()).append("\n");
        }

        final ScrollView sv = new ScrollView(act);
        sv.setVerticalScrollBarEnabled(true);
        sv.setScrollbarFadingEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_ALWAYS);

        final TextView tv = new TextView(act);
        tv.setText(logContent.toString());
        tv.setTextSize(12);
        tv.setTextIsSelectable(true);
        tv.setPadding(24, 24, 24, 24);
        sv.addView(tv);

        // 右侧可拖动条（不旋转，避免坐标异常）
        final View track = new View(act);
        track.setBackgroundColor(Color.parseColor("#E0E0E0"));

        final View thumb = new View(act);
        thumb.setBackgroundColor(Color.parseColor("#70A1B8"));

        final LinearLayout trackWrap = new LinearLayout(act);
        trackWrap.setOrientation(LinearLayout.VERTICAL);
        trackWrap.setBackgroundColor(Color.parseColor("#F5F5F5"));
        trackWrap.setPadding(8, 8, 8, 8);

        final LinearLayout.LayoutParams thumbLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(48)
        );

        trackWrap.addView(track, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ));
        trackWrap.addView(thumb, thumbLp);

        // 右侧滑动区域隐藏（不可见）但保留触摸功能
        track.setAlpha(0f);
        thumb.setAlpha(0f);
        trackWrap.setAlpha(0f);

        // 可选：降低无障碍干扰
        track.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        thumb.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        LinearLayout rootWrap = new LinearLayout(act);
        rootWrap.setOrientation(LinearLayout.HORIZONTAL);
        rootWrap.setPadding(8, 8, 8, 8);

        rootWrap.addView(sv, new LinearLayout.LayoutParams(0, dpToPx(520), 1f));

        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(dpToPx(40), dpToPx(520));
        barLp.setMargins(dpToPx(6), 0, 0, 0);
        rootWrap.addView(trackWrap, barLp);

        final boolean[] dragging = new boolean[]{false};

        // 手动拖动右侧条 -> 滚动日志
        trackWrap.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                if (tv.getHeight() <= sv.getHeight()) return true;

                int h = v.getHeight();
                float y = event.getY();
                if (y < 0) y = 0;
                if (y > h) y = h;

                float ratio = y / (float) h;
                int maxScroll = tv.getHeight() - sv.getHeight();
                int targetY = (int) (ratio * maxScroll);
                if (targetY < 0) targetY = 0;
                if (targetY > maxScroll) targetY = maxScroll;

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        dragging[0] = true;
                        sv.scrollTo(0, targetY);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        sv.scrollTo(0, targetY);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        dragging[0] = false;
                        return true;
                }
                return false;
            }
        });

        // 滚动日志 -> 同步右侧thumb位置
        sv.getViewTreeObserver().addOnScrollChangedListener(new android.view.ViewTreeObserver.OnScrollChangedListener() {
            public void onScrollChanged() {
                if (dragging[0]) return;
                if (tv.getHeight() <= sv.getHeight()) return;

                int maxScroll = tv.getHeight() - sv.getHeight();
                int scrollY = sv.getScrollY();
                float ratio = maxScroll == 0 ? 0f : (scrollY / (float) maxScroll);

                int trackH = trackWrap.getHeight();
                int thumbH = thumb.getHeight();
                int moveRange = Math.max(1, trackH - thumbH - dpToPx(16));
                int top = (int) (ratio * moveRange);

                thumb.setTranslationY(top);
            }
        });

        final Runnable copyAll = new Runnable() {
            public void run() {
                try {
                    android.content.ClipboardManager cm =
                            (android.content.ClipboardManager) act.getSystemService(Context.CLIPBOARD_SERVICE);
                    android.content.ClipData clip =
                            android.content.ClipData.newPlainText("auto_reply_log", logContent.toString());
                    cm.setPrimaryClip(clip);
                    toast("已复制全部日志");
                } catch (Exception e) {
                    toast("复制失败: " + e.getMessage());
                }
            }
        };

        AlertDialog dialog = buildCommonAlertDialog(
                act,
                "📋 查看运行日志",
                rootWrap,
                "关闭",
                null,
                "复制全部",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        copyAll.run();
                    }
                },
                "清空日志",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        try {
                            Activity a = getTopActivity();
                            if (a != null) {
                                File logDir = new File(a.getExternalFilesDir(null), "logs");
                                File logFile = new File(logDir, "auto_reply_log.txt");
                                if (logFile.exists()) {
                                    java.io.FileWriter fw = new java.io.FileWriter(logFile, false);
                                    fw.write("");
                                    fw.close();
                                    toast("日志已清空");
                                }
                            }
                        } catch (Exception e) {
                            toast("清空失败: " + e.getMessage());
                        }
                    }
                }
        );
        dialog.show();

        try {
            Button copyBtn = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (copyBtn != null) {
                copyBtn.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        copyAll.run();
                    }
                });
            }
        } catch (Exception ignore) {}

    } catch (Exception e) {
        toast("打开日志失败: " + e.getMessage());
    }
}



private void showXiaozhiAIConfigDialog() {
    showAIConfigDialog();
}

private JSONObject getZhiliaAllConfigs() {
    try {
        String s = getString(ZHILIA_MULTI_CONFIGS_KEY, "");
        if (!TextUtils.isEmpty(s)) {
            JSONObject obj = JSON.parseObject(s);
            if (obj != null) return obj;
        }
    } catch (Exception e) {}
    return new JSONObject();
}

private void saveZhiliaAllConfigs(JSONObject all) {
    putString(ZHILIA_MULTI_CONFIGS_KEY, all.toJSONString());
}

private void ensureZhiliaDefaultMigrated() {
    try {
        JSONObject all = getZhiliaAllConfigs();
        if (all == null || all.isEmpty()) {
            JSONObject one = new JSONObject();
            one.put("apiKey", getString(ZHILIA_AI_API_KEY, ""));
            one.put("apiUrl", getString(ZHILIA_AI_API_URL, "https://api.siliconflow.cn/v1/chat/completions"));
            one.put("modelName", getString(ZHILIA_AI_MODEL_NAME, "deepseek-ai/DeepSeek-V3"));
            one.put("systemPrompt", getString(ZHILIA_AI_SYSTEM_PROMPT, "你是个宝宝"));
            one.put("contextLimit", getInt(ZHILIA_AI_CONTEXT_LIMIT, 10));
            all = new JSONObject();
            all.put("默认配置", one);
            saveZhiliaAllConfigs(all);
            if (TextUtils.isEmpty(getString(ZHILIA_ACTIVE_CONFIG_NAME_KEY, ""))) {
                putString(ZHILIA_ACTIVE_CONFIG_NAME_KEY, "默认配置");
            }
        }
    } catch (Exception ignore) {}
}


private void syncLegacyZhiliaKeysFromConfig(JSONObject cfg) {
    try {
        if (cfg == null) return;

        String apiKey = cfg.getString("apiKey");
        String apiUrl = cfg.getString("apiUrl");
        String modelName = cfg.getString("modelName");
        String systemPrompt = cfg.getString("systemPrompt");
        int contextLimit = cfg.getIntValue("contextLimit");

        if (apiKey == null) apiKey = "";
        if (TextUtils.isEmpty(apiUrl)) apiUrl = "https://api.siliconflow.cn/v1/chat/completions";
        if (TextUtils.isEmpty(modelName)) modelName = "deepseek-ai/DeepSeek-V3";
        if (systemPrompt == null) systemPrompt = "你是个宝宝";
        if (contextLimit <= 0) contextLimit = 10;

        putString(ZHILIA_AI_API_KEY, apiKey);
        putString(ZHILIA_AI_API_URL, apiUrl);
        putString(ZHILIA_AI_MODEL_NAME, modelName);
        putString(ZHILIA_AI_SYSTEM_PROMPT, systemPrompt);
        putInt(ZHILIA_AI_CONTEXT_LIMIT, contextLimit);

        debugLog("[智聊AI] 已同步旧键配置: model=" + modelName + ", context=" + contextLimit);
    } catch (Exception e) {
        debugLog("[异常] syncLegacyZhiliaKeysFromConfig失败: " + e.getMessage());
    }
}

private JSONObject getActiveZhiliaConfig() {
    ensureZhiliaDefaultMigrated();
    JSONObject all = getZhiliaAllConfigs();
    String activeName = getString(ZHILIA_ACTIVE_CONFIG_NAME_KEY, "默认配置");
    JSONObject cfg = all.getJSONObject(activeName);
    if (cfg == null) {
        for (String k : all.keySet()) {
            cfg = all.getJSONObject(k);
            putString(ZHILIA_ACTIVE_CONFIG_NAME_KEY, k);
            break;
        }
    }
    if (cfg == null) {
        cfg = new JSONObject();
        cfg.put("apiKey", "");
        cfg.put("apiUrl", "https://api.siliconflow.cn/v1/chat/completions");
        cfg.put("modelName", "deepseek-ai/DeepSeek-V3");
        cfg.put("systemPrompt", "你是个宝宝");
        cfg.put("contextLimit", 10);
    }
    return cfg;
}

private void showZhiliaConfigListDialog(final Runnable onSwitched) {
    try {
        Activity act = getTopActivity();
        if (act == null) {
            toast("无法获取窗口");
            return;
        }

        ensureZhiliaDefaultMigrated();
        final JSONObject all = getZhiliaAllConfigs();
        final ArrayList<String> keys = new ArrayList<String>();
        final ArrayList<String> names = new ArrayList<String>();
        final String active = getString(ZHILIA_ACTIVE_CONFIG_NAME_KEY, "默认配置");

        for (String k : all.keySet()) {
            JSONObject one = all.getJSONObject(k);
            String model = one != null ? one.getString("modelName") : "";
            keys.add(k);
            names.add((k.equals(active) ? "✅ " : "   ") + k + (TextUtils.isEmpty(model) ? "" : ("  (" + model + ")")));
        }

        if (keys.isEmpty()) {
            toast("暂无配置");
            return;
        }

        final int[] selectedIndex = new int[]{-1};

        AlertDialog.Builder b = new AlertDialog.Builder(act);
        b.setTitle("智聊配置列表（当前: " + active + "）");
        b.setSingleChoiceItems(names.toArray(new String[0]), -1, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                selectedIndex[0] = which;
            }
        });

        // 切换
        b.setPositiveButton("切换", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                try {
                    if (selectedIndex[0] < 0 || selectedIndex[0] >= keys.size()) {
                        toast("请先选择一个配置");
                        return;
                    }
                    String k = keys.get(selectedIndex[0]);
                    putString(ZHILIA_ACTIVE_CONFIG_NAME_KEY, k);
                    toast("已切换到: " + k);
                    if (onSwitched != null) onSwitched.run();
                } catch (Exception e) {
                    toast("切换失败: " + e.getMessage());
                }
            }
        });

        // 更多（重命名/复制/删除）
        b.setNeutralButton("更多", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                try {
                    if (selectedIndex[0] < 0 || selectedIndex[0] >= keys.size()) {
                        toast("请先选择一个配置");
                        return;
                    }
                    final String chosen = keys.get(selectedIndex[0]);
                    final String[] actions = new String[]{"重命名", "复制", "删除"};

                    AlertDialog.Builder mb = new AlertDialog.Builder(getTopActivity());
                    mb.setTitle("操作: " + chosen);
                    mb.setItems(actions, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d, int w) {
                            if (w == 0) {
                                // 重命名
                                final EditText et = createStyledEditText("输入新配置名称", chosen);
                                AlertDialog rd = buildCommonAlertDialog(
                                    getTopActivity(),
                                    "重命名配置",
                                    et,
                                    "确定",
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface d2, int w2) {
                                            try {
                                                String newName = et.getText().toString().trim();
                                                if (TextUtils.isEmpty(newName)) {
                                                    toast("名称不能为空");
                                                    return;
                                                }
                                                if (newName.equals(chosen)) {
                                                    toast("名称未变化");
                                                    return;
                                                }
                                                JSONObject all2 = getZhiliaAllConfigs();
                                                if (all2.containsKey(newName)) {
                                                    toast("名称已存在");
                                                    return;
                                                }
                                                JSONObject obj = all2.getJSONObject(chosen);
                                                all2.remove(chosen);
                                                all2.put(newName, obj);
                                                saveZhiliaAllConfigs(all2);

                                                String nowActive = getString(ZHILIA_ACTIVE_CONFIG_NAME_KEY, "");
                                                if (chosen.equals(nowActive)) {
                                                    putString(ZHILIA_ACTIVE_CONFIG_NAME_KEY, newName);
                                                }
                                                toast("已重命名为: " + newName);
                                                if (onSwitched != null) onSwitched.run();
                                            } catch (Exception e) {
                                                toast("重命名失败: " + e.getMessage());
                                            }
                                        }
                                    },
                                    "取消", null,
                                    null, null
                                );
                                rd.show();
                            } else if (w == 1) {
                                // 复制
                                try {
                                    JSONObject all2 = getZhiliaAllConfigs();
                                    JSONObject src = all2.getJSONObject(chosen);
                                    if (src == null) {
                                        toast("源配置不存在");
                                        return;
                                    }
                                    String base = chosen + "_副本";
                                    String newName = base;
                                    int idx = 2;
                                    while (all2.containsKey(newName)) {
                                        newName = base + idx;
                                        idx++;
                                    }
                                    JSONObject cp = JSON.parseObject(src.toJSONString());
                                    all2.put(newName, cp);
                                    saveZhiliaAllConfigs(all2);
                                    toast("已复制为: " + newName);
                                    if (onSwitched != null) onSwitched.run();
                                } catch (Exception e) {
                                    toast("复制失败: " + e.getMessage());
                                }
                            } else {
                                // 删除
                                try {
                                    JSONObject all2 = getZhiliaAllConfigs();
                                    all2.remove(chosen);

                                    if (all2.isEmpty()) {
                                        toast("至少保留一个配置");
                                        return;
                                    }

                                    saveZhiliaAllConfigs(all2);

                                    String activeNow = getString(ZHILIA_ACTIVE_CONFIG_NAME_KEY, "");
                                    if (chosen.equals(activeNow)) {
                                        for (String first : all2.keySet()) {
                                            putString(ZHILIA_ACTIVE_CONFIG_NAME_KEY, first);
                                            break;
                                        }
                                    }
                                    toast("已删除: " + chosen);
                                    if (onSwitched != null) onSwitched.run();
                                } catch (Exception e) {
                                    toast("删除失败: " + e.getMessage());
                                }
                            }
                        }
                    });
                    mb.setNegativeButton("关闭", null);
                    AlertDialog md = mb.create();
                    md.setOnShowListener(new DialogInterface.OnShowListener() {
                        public void onShow(DialogInterface dd) {
                            setupUnifiedDialog((AlertDialog) dd);
                        }
                    });
                    md.show();
                } catch (Exception e) {
                    toast("操作失败: " + e.getMessage());
                }
            }
        });

        b.setNegativeButton("关闭", null);

        AlertDialog d = b.create();
        d.setOnShowListener(new DialogInterface.OnShowListener() {
            public void onShow(DialogInterface dialog) {
                setupUnifiedDialog((AlertDialog) dialog);
            }
        });
        d.show();

    } catch (Exception e) {
        toast("打开配置列表失败: " + e.getMessage());
        debugLog("[异常] showZhiliaConfigListDialog(onSwitched): " + e.getMessage());
    }
}

private void showZhiliaConfigListDialog() {
    showZhiliaConfigListDialog(null);
}


private String httpJsonGetSync(String url, String apiKey) {
    java.io.BufferedReader br = null;
    try {
        Request.Builder rb = new Request.Builder().url(url).get();
        rb.addHeader("Content-Type", "application/json");
        if (!TextUtils.isEmpty(apiKey)) rb.addHeader("Authorization", "Bearer " + apiKey);
        Response resp = aiClient.newCall(rb.build()).execute();
        if (!resp.isSuccessful() || resp.body() == null) return null;
        return resp.body().string();
    } catch (Exception e) {
        debugLog("[异常] httpJsonGetSync失败: " + e.getMessage());
        return null;
    } finally {
        try { if (br != null) br.close(); } catch (Exception ignore) {}
    }
}

private List<String> fetchModelListByApi(String apiUrl, String apiKey) {
    List<String> out = new ArrayList<String>();
    if (TextUtils.isEmpty(apiUrl)) return out;

    try {
        String base = apiUrl.trim();
        // 例如: https://xx/v1/chat/completions -> https://xx/v1/models
        int p = base.indexOf("/chat/completions");
        String modelsUrl;
        if (p > 0) {
            modelsUrl = base.substring(0, p) + "/models";
        } else {
            // 若本身不是chat/completions，尝试拼接/models
            if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
            if (base.endsWith("/models")) modelsUrl = base;
            else modelsUrl = base + "/models";
        }

        String body = httpJsonGetSync(modelsUrl, apiKey);
        if (TextUtils.isEmpty(body)) {
            // 再尝试一版：去掉末尾 /v1 再拼 /v1/models
            try {
                String alt = modelsUrl;
                if (alt.endsWith("/models")) {
                    String t = alt.substring(0, alt.length() - "/models".length());
                    if (t.endsWith("/v1")) {
                        alt = t.substring(0, t.length() - 3) + "/v1/models";
                    }
                }
                if (!alt.equals(modelsUrl)) body = httpJsonGetSync(alt, apiKey);
            } catch (Exception ignore) {}
        }
        if (TextUtils.isEmpty(body)) return out;

        JSONObject obj = JSON.parseObject(body);
        if (obj == null) return out;

        // OpenAI兼容: {"data":[{"id":"xxx"}]}
        JSONArray data = obj.getJSONArray("data");
        if (data != null) {
            for (int i = 0; i < data.size(); i++) {
                JSONObject one = data.getJSONObject(i);
                if (one == null) continue;
                String id = one.getString("id");
                if (!TextUtils.isEmpty(id) && !out.contains(id)) out.add(id);
            }
        }

        // 某些站点: {"models":[...]} 或 {"result":[...]}
        if (out.isEmpty()) {
            JSONArray arr = obj.getJSONArray("models");
            if (arr == null) arr = obj.getJSONArray("result");
            if (arr != null) {
                for (int i = 0; i < arr.size(); i++) {
                    Object it = arr.get(i);
                    if (it instanceof JSONObject) {
                        String id = ((JSONObject) it).getString("id");
                        if (TextUtils.isEmpty(id)) id = ((JSONObject) it).getString("name");
                        if (!TextUtils.isEmpty(id) && !out.contains(id)) out.add(id);
                    } else if (it instanceof String) {
                        String id2 = (String) it;
                        if (!TextUtils.isEmpty(id2) && !out.contains(id2)) out.add(id2);
                    }
                }
            }
        }

        Collections.sort(out);
        return out;
    } catch (Exception e) {
        debugLog("[异常] fetchModelListByApi失败: " + e.getMessage());
        return out;
    }
}



private String getApiFavKey(String apiUrl) {
    if (apiUrl == null) apiUrl = "";
    return apiUrl.trim().toLowerCase(Locale.getDefault());
}

private Set<String> getFavoriteModelsForApi(String apiUrl) {
    try {
        JSONObject all = getJsonObjSafe(ZHILIA_MODEL_FAVORITES_KEY);
        String key = getApiFavKey(apiUrl);
        String csv = all.getString(key);
        Set<String> set = new HashSet<String>();
        if (!TextUtils.isEmpty(csv)) {
            String[] arr = csv.split(";;;");
            for (int i = 0; i < arr.length; i++) {
                String s = arr[i] == null ? "" : arr[i].trim();
                if (!TextUtils.isEmpty(s)) set.add(s);
            }
        }
        return set;
    } catch (Exception e) {
        return new HashSet<String>();
    }
}

private void saveFavoriteModelsForApi(String apiUrl, Set<String> favSet) {
    try {
        JSONObject all = getJsonObjSafe(ZHILIA_MODEL_FAVORITES_KEY);
        String key = getApiFavKey(apiUrl);
        if (favSet == null || favSet.isEmpty()) {
            all.remove(key);
        } else {
            List<String> list = new ArrayList<String>();
            for (String s : favSet) if (!TextUtils.isEmpty(s)) list.add(s);
            Collections.sort(list);
            all.put(key, TextUtils.join(";;;", list));
        }
        putJsonObjSafe(ZHILIA_MODEL_FAVORITES_KEY, all);
    } catch (Exception e) {
        debugLog("[异常] saveFavoriteModelsForApi失败: " + e.getMessage());
    }
}

private List<String> sortModelsWithFavoritesTop(List<String> models, Set<String> favSet) {
    List<String> fav = new ArrayList<String>();
    List<String> normal = new ArrayList<String>();
    if (models == null) return normal;
    for (int i = 0; i < models.size(); i++) {
        String m = models.get(i);
        if (favSet != null && favSet.contains(m)) fav.add(m);
        else normal.add(m);
    }
    Collections.sort(fav);
    Collections.sort(normal);
    List<String> out = new ArrayList<String>();
    out.addAll(fav);
    out.addAll(normal);
    return out;
}

private List<String> filterModelsByKeyword(List<String> models, String keyword) {
    List<String> out = new ArrayList<String>();
    if (models == null) return out;
    String k = keyword == null ? "" : keyword.trim().toLowerCase(Locale.getDefault());
    if (TextUtils.isEmpty(k)) {
        out.addAll(models);
        return out;
    }
    for (int i = 0; i < models.size(); i++) {
        String m = models.get(i);
        if (!TextUtils.isEmpty(m) && m.toLowerCase(Locale.getDefault()).contains(k)) out.add(m);
    }
    return out;
}

private void showModelPickerDialogWithSearchAndFav(final String apiUrl, final List<String> models, final EditText modelNameEdit) {
    try {
        Activity act = getTopActivity();
        if (act == null) {
            toast("无法获取窗口");
            return;
        }

        final Set<String> favSet = getFavoriteModelsForApi(apiUrl);
        final List<String> sortedAll = sortModelsWithFavoritesTop(models, favSet);

        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);

        final EditText searchEdit = createStyledEditText("搜索模型（关键字）", "");
        root.addView(searchEdit);

        final ListView lv = new ListView(act);
        lv.setVerticalScrollBarEnabled(true);
        lv.setScrollbarFadingEnabled(false);
        lv.setFastScrollEnabled(true);
        lv.setFastScrollAlwaysVisible(true);
        lv.setVerticalScrollBarEnabled(true);
        lv.setScrollbarFadingEnabled(false);
        lv.setFastScrollEnabled(true);
        lv.setFastScrollAlwaysVisible(true);
        setupListViewTouchForScroll(lv);
        lv.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
        lv.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
        lv.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

        final ArrayList<String> display = new ArrayList<String>();
        final ArrayList<String> real = new ArrayList<String>();
        final ArrayAdapter<String> ad = new ArrayAdapter<String>(act, android.R.layout.simple_list_item_single_choice, display);
        lv.setAdapter(ad);
        root.addView(lv);

        final Runnable refresh = new Runnable() {
            public void run() {
                String kw = searchEdit.getText().toString();
                List<String> filtered = filterModelsByKeyword(sortedAll, kw);

                display.clear();
                real.clear();
                for (int i = 0; i < filtered.size(); i++) {
                    String m = filtered.get(i);
                    boolean fav = favSet.contains(m);
                    display.add((fav ? "★ " : "   ") + m);
                    real.add(m);
                }
                ad.notifyDataSetChanged();
                adjustListViewHeight(lv, Math.max(1, Math.min(display.size(), 14)));
                lv.getLayoutParams().height = dpToPx(520);
                lv.requestLayout();
            }
        };
        refresh.run();

        searchEdit.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            public void afterTextChanged(Editable s) { refresh.run(); }
        });

        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < real.size()) {
                    String model = real.get(position);
                    modelNameEdit.setText(model);
                    toast("已选择模型: " + model);
                }
            }
        });

        AlertDialog.Builder b = new AlertDialog.Builder(act);
        b.setTitle("选择模型（支持搜索/收藏置顶）");
        b.setView(root);

        b.setPositiveButton("确定", null);
        b.setNeutralButton("收藏/取消收藏", null);
        b.setNegativeButton("关闭", null);

        final AlertDialog d = b.create();
        d.setOnShowListener(new DialogInterface.OnShowListener() {
            public void onShow(DialogInterface dialog) {
                setupUnifiedDialog(d);

                Button okBtn = d.getButton(AlertDialog.BUTTON_POSITIVE);
                if (okBtn != null) {
                    okBtn.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) {
                            int p = lv.getCheckedItemPosition();
                            if (p >= 0 && p < real.size()) {
                                String model = real.get(p);
                                modelNameEdit.setText(model);
                                toast("已选择模型: " + model);
                                d.dismiss();
                            } else {
                                toast("请先选择一个模型");
                            }
                        }
                    });
                }

                Button favBtn = d.getButton(AlertDialog.BUTTON_NEUTRAL);
                if (favBtn != null) {
                    favBtn.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) {
                            int p = lv.getCheckedItemPosition();
                            if (p < 0 || p >= real.size()) {
                                toast("请先选中一个模型再收藏");
                                return;
                            }
                            String model = real.get(p);
                            if (favSet.contains(model)) {
                                favSet.remove(model);
                                toast("已取消收藏: " + model);
                            } else {
                                favSet.add(model);
                                toast("已收藏: " + model);
                            }
                            saveFavoriteModelsForApi(apiUrl, favSet);

                            List<String> reSorted = sortModelsWithFavoritesTop(models, favSet);
                            sortedAll.clear();
                            sortedAll.addAll(reSorted);

                            refresh.run();
                        }
                    });
                }
            }
        });
        d.show();
    } catch (Exception e) {
        toast("打开模型选择失败: " + e.getMessage());
        debugLog("[异常] showModelPickerDialogWithSearchAndFav: " + e.getMessage());
    }
}



private void showModelPickerForTestOnly(final String apiUrl, final List<String> models, final String currentModel, final java.util.concurrent.atomic.AtomicReference<String> outModelRef, final Runnable onPicked) {
    try {
        Activity act = getTopActivity();
        if (act == null) {
            toast("无法获取窗口");
            return;
        }

        final Set<String> favSet = getFavoriteModelsForApi(apiUrl);
        final List<String> sortedAll = new ArrayList<String>(sortModelsWithFavoritesTop(models, favSet));

        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);

        final EditText searchEdit = createStyledEditText("搜索模型（关键字）", "");
        root.addView(searchEdit);

        final ListView lv = new ListView(act);
        lv.setVerticalScrollBarEnabled(true);
        lv.setScrollbarFadingEnabled(false);
        lv.setFastScrollEnabled(true);
        lv.setFastScrollAlwaysVisible(true);
        setupListViewTouchForScroll(lv);
        lv.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
        lv.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

        final ArrayList<String> display = new ArrayList<String>();
        final ArrayList<String> real = new ArrayList<String>();
        final ArrayAdapter<String> ad = new ArrayAdapter<String>(act, android.R.layout.simple_list_item_single_choice, display);
        lv.setAdapter(ad);
        root.addView(lv);

        final Runnable refresh = new Runnable() {
            public void run() {
                String kw = searchEdit.getText().toString();
                List<String> filtered = filterModelsByKeyword(sortedAll, kw);

                display.clear();
                real.clear();
                for (int i = 0; i < filtered.size(); i++) {
                    String m = filtered.get(i);
                    boolean fav = favSet.contains(m);
                    display.add((fav ? "★ " : "   ") + m);
                    real.add(m);
                }
                ad.notifyDataSetChanged();
                adjustListViewHeight(lv, Math.max(1, Math.min(display.size(), 14)));
                lv.getLayoutParams().height = dpToPx(520);
                lv.requestLayout();

                if (!TextUtils.isEmpty(currentModel)) {
                    for (int i = 0; i < real.size(); i++) {
                        if (currentModel.equals(real.get(i))) {
                            lv.setItemChecked(i, true);
                            break;
                        }
                    }
                }
            }
        };
        refresh.run();

        searchEdit.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            public void afterTextChanged(Editable s) { refresh.run(); }
        });

        AlertDialog.Builder b = new AlertDialog.Builder(act);
        b.setTitle("测试专用模型选择（不影响主配置）");
        b.setView(root);
        b.setPositiveButton("确定", null);
        b.setNeutralButton("收藏/取消收藏", null);
        b.setNegativeButton("取消", null);

        final AlertDialog d = b.create();
        d.setOnShowListener(new DialogInterface.OnShowListener() {
            public void onShow(DialogInterface dialog) {
                setupUnifiedDialog(d);

                Button okBtn = d.getButton(AlertDialog.BUTTON_POSITIVE);
                if (okBtn != null) {
                    okBtn.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) {
                            int p = lv.getCheckedItemPosition();
                            if (p < 0 || p >= real.size()) {
                                toast("请先选择一个模型");
                                return;
                            }
                            String m = real.get(p);
                            outModelRef.set(m);
                            toast("测试模型已选择: " + m);
                            d.dismiss();
                            if (onPicked != null) onPicked.run();
                        }
                    });
                }

                Button favBtn = d.getButton(AlertDialog.BUTTON_NEUTRAL);
                if (favBtn != null) {
                    favBtn.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) {
                            int p = lv.getCheckedItemPosition();
                            if (p < 0 || p >= real.size()) {
                                toast("请先选中一个模型再收藏");
                                return;
                            }
                            String model = real.get(p);
                            if (favSet.contains(model)) {
                                favSet.remove(model);
                                toast("已取消收藏: " + model);
                            } else {
                                favSet.add(model);
                                toast("已收藏: " + model);
                            }
                            saveFavoriteModelsForApi(apiUrl, favSet);
                            sortedAll.clear();
                            sortedAll.addAll(sortModelsWithFavoritesTop(models, favSet));
                            refresh.run();
                        }
                    });
                }
            }
        });
        d.show();
    } catch (Exception e) {
        toast("测试模型选择失败: " + e.getMessage());
        debugLog("[异常] showModelPickerForTestOnly: " + e.getMessage());
    }
}

private void runZhiliaConnectivityTest(final String key, final String url, final String model) {
    if (TextUtils.isEmpty(key) || TextUtils.isEmpty(url) || TextUtils.isEmpty(model)) {
        toast("请先填写 API Key / URL / 模型");
        return;
    }

    final AlertDialog loading = buildCommonAlertDialog(
        getTopActivity(), "测试中", createTextView(getTopActivity(), "正在请求，请稍候...", 14, 0),
        null, null, "取消", null, null, null
    );
    loading.show();

    new Thread(new Runnable() {
        public void run() {
            String nonStreamResult;
            String streamResult;
            boolean nonOk = false;
            boolean streamOk = false;

            try {
                JSONObject body1 = new JSONObject();
                body1.put("model", model);
                JSONArray msgs1 = new JSONArray();
                JSONObject sys1 = new JSONObject();
                sys1.put("role", "system");
                sys1.put("content", "你是测试助手");
                msgs1.add(sys1);
                JSONObject user1 = new JSONObject();
                user1.put("role", "user");
                user1.put("content", "只回复: OK");
                msgs1.add(user1);
                body1.put("messages", msgs1);
                body1.put("temperature", 0.1);
                body1.put("stream", false);

                Request req1 = new Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Bearer " + key)
                    .post(RequestBody.create(MediaType.parse("application/json"), body1.toString()))
                    .build();

                Response r1 = aiClient.newCall(req1).execute();
                String t1 = r1.body() != null ? r1.body().string() : "";
                nonOk = r1.isSuccessful() && t1 != null && t1.trim().startsWith("{");
                nonStreamResult = (nonOk ? "✅ 非流式可用" : "❌ 非流式不可用") + " (HTTP " + r1.code() + ")";
            } catch (Exception ex1) {
                nonStreamResult = "❌ 非流式异常: " + ex1.getMessage();
            }

            java.io.BufferedReader br = null;
            try {
                JSONObject body2 = new JSONObject();
                body2.put("model", model);
                JSONArray msgs2 = new JSONArray();
                JSONObject sys2 = new JSONObject();
                sys2.put("role", "system");
                sys2.put("content", "你是测试助手");
                msgs2.add(sys2);
                JSONObject user2 = new JSONObject();
                user2.put("role", "user");
                user2.put("content", "只回复: OK");
                msgs2.add(user2);
                body2.put("messages", msgs2);
                body2.put("temperature", 0.1);
                body2.put("stream", true);

                Request req2 = new Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Bearer " + key)
                    .post(RequestBody.create(MediaType.parse("application/json"), body2.toString()))
                    .build();

                Response r2 = aiClient.newCall(req2).execute();
                int code2 = r2.code();
                StringBuilder agg = new StringBuilder();
                if (r2.isSuccessful() && r2.body() != null) {
                    br = new java.io.BufferedReader(new java.io.InputStreamReader(r2.body().byteStream(), "UTF-8"));
                    String line;
                    int safe = 0;
                    while ((line = br.readLine()) != null && safe < 300) {
                        safe++;
                        line = line.trim();
                        if (!line.startsWith("data:")) continue;
                        String data = line.substring(5).trim();
                        if ("[DONE]".equals(data)) break;
                        try {
                            JSONObject obj = JSON.parseObject(data);
                            JSONArray ch = obj.getJSONArray("choices");
                            if (ch != null && !ch.isEmpty()) {
                                JSONObject c0 = ch.getJSONObject(0);
                                JSONObject delta = c0.getJSONObject("delta");
                                if (delta != null) {
                                    String p = delta.getString("content");
                                    if (!TextUtils.isEmpty(p)) agg.append(p);
                                } else {
                                    JSONObject msg = c0.getJSONObject("message");
                                    if (msg != null) {
                                        String p2 = msg.getString("content");
                                        if (!TextUtils.isEmpty(p2)) agg.append(p2);
                                    }
                                }
                            }
                        } catch (Exception ignore) {}
                    }
                }
                streamOk = agg.length() > 0;
                streamResult = (streamOk ? "✅ 流式可用" : "❌ 流式不可用") + " (HTTP " + code2 + ")";
            } catch (Exception ex2) {
                streamResult = "❌ 流式异常: " + ex2.getMessage();
            } finally {
                try { if (br != null) br.close(); } catch (Exception ignore) {}
            }

            final String summary;
            if (nonOk && streamOk) summary = "总结：流式、非流式都可用";
            else if (nonOk) summary = "总结：仅非流式可用";
            else if (streamOk) summary = "总结：仅流式可用";
            else summary = "总结：流式、非流式都不可用";

            final String resultText = nonStreamResult + "\n" + streamResult + "\n\n" + summary;

            new Handler(Looper.getMainLooper()).post(new Runnable() {
                public void run() {
                    try { loading.dismiss(); } catch (Exception ignore) {}
                    AlertDialog d = buildCommonAlertDialog(
                        getTopActivity(),
                        "测试结果",
                        createTextView(getTopActivity(), resultText, 13, 0),
                        "知道了", null, null, null, null, null
                    );
                    d.show();
                }
            });
        }
    }).start();
}

private void showZhiliaAIConfigDialog() {
    try {
        ensureZhiliaDefaultMigrated();
        JSONObject activeCfg = getActiveZhiliaConfig();
        String activeName = getString(ZHILIA_ACTIVE_CONFIG_NAME_KEY, "默认配置");

        ScrollView scrollView = new ScrollView(getTopActivity());
        LinearLayout layout = new LinearLayout(getTopActivity());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 24, 24, 24);
        layout.setBackgroundColor(Color.parseColor("#FAFBF9"));
        scrollView.addView(layout);

        LinearLayout apiCard = createCardLayout();
        apiCard.addView(createSectionTitle("智聊AI 多模型配置"));

        final TextView current = new TextView(getTopActivity());
        current.setText("当前启用: " + activeName);
        current.setTextSize(13);
        current.setTextColor(Color.parseColor("#666666"));
        apiCard.addView(current);

        apiCard.addView(createTextView(getTopActivity(), "配置名称:", 14, 0));
        final EditText cfgNameEdit = createStyledEditText("例如：DeepSeek主账号", activeName);
        apiCard.addView(cfgNameEdit);

        apiCard.addView(createTextView(getTopActivity(), "API Key:", 14, 0));
        final EditText apiKeyEdit = createStyledEditText("请输入你的API Key", activeCfg.getString("apiKey"));
        apiCard.addView(apiKeyEdit);

        apiCard.addView(createTextView(getTopActivity(), "API URL:", 14, 0));
        final EditText apiUrlEdit = createStyledEditText("默认为官方API", activeCfg.getString("apiUrl"));
        apiCard.addView(apiUrlEdit);

        apiCard.addView(createTextView(getTopActivity(), "模型名称:", 14, 0));
        final EditText modelNameEdit = createStyledEditText("例如 deepseek-ai/DeepSeek-V3", activeCfg.getString("modelName"));
        apiCard.addView(modelNameEdit);
        layout.addView(apiCard);

        LinearLayout advancedCard = createCardLayout();
        advancedCard.addView(createSectionTitle("高级设置"));
        advancedCard.addView(createTextView(getTopActivity(), "上下文轮次 (建议5-10):", 14, 0));
        final EditText contextLimitEdit = createStyledEditText("数字越大越消耗Token", String.valueOf(activeCfg.getIntValue("contextLimit")));
        contextLimitEdit.setInputType(InputType.TYPE_CLASS_NUMBER);
        advancedCard.addView(contextLimitEdit);
        final LinearLayout streamSwitchRow = createSwitchRow(
            getTopActivity(),
            "启用流式响应(失败自动回退非流式)",
            getBoolean(ZHILIA_AI_STREAM_ENABLED_KEY, false),
            new View.OnClickListener() { public void onClick(View v) {} }
        );
        advancedCard.addView(streamSwitchRow);



        advancedCard.addView(createTextView(getTopActivity(), "系统指令 (AI角色设定):", 14, 0));
        final EditText systemPromptEdit = createStyledEditText("设定AI的身份和回复风格", activeCfg.getString("systemPrompt"));
        systemPromptEdit.setMinLines(3);
        systemPromptEdit.setGravity(Gravity.TOP);
        advancedCard.addView(systemPromptEdit);
        layout.addView(advancedCard);

        LinearLayout btnCard = createCardLayout();

        final Runnable refreshActiveToViews = new Runnable() {
            public void run() {
                try {
                    JSONObject now = getActiveZhiliaConfig();
                    String nowName = getString(ZHILIA_ACTIVE_CONFIG_NAME_KEY, "默认配置");
                    current.setText("当前启用: " + nowName);
                    cfgNameEdit.setText(nowName);
                    apiKeyEdit.setText(now.getString("apiKey"));
                    apiUrlEdit.setText(now.getString("apiUrl"));
                    modelNameEdit.setText(now.getString("modelName"));
                    contextLimitEdit.setText(String.valueOf(now.getIntValue("contextLimit")));
                    systemPromptEdit.setText(now.getString("systemPrompt"));
                } catch (Exception e) {
                    toast("刷新配置失败: " + e.getMessage());
                }
            }
        };

        Button listBtn = new Button(getTopActivity());
        listBtn.setText("📚 查看配置列表");
        styleUtilityButton(listBtn);
        listBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showZhiliaConfigListDialog(refreshActiveToViews);
            }
        });
        btnCard.addView(listBtn);

        Button addCfgBtn = new Button(getTopActivity());
        addCfgBtn.setText("➕ 新增模型配置");
        styleUtilityButton(addCfgBtn);
        addCfgBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                final EditText nameEdit = createStyledEditText("输入新配置名称", "新配置");
                AlertDialog d = buildCommonAlertDialog(
                    getTopActivity(),
                    "新增智聊配置",
                    nameEdit,
                    "按当前配置复制新增",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            try {
                                String newName = nameEdit.getText().toString().trim();
                                if (TextUtils.isEmpty(newName)) {
                                    toast("配置名称不能为空");
                                    return;
                                }

                                JSONObject all = getZhiliaAllConfigs();
                                if (all.containsKey(newName)) {
                                    toast("配置名称已存在");
                                    return;
                                }

                                JSONObject one = new JSONObject();
                                one.put("apiKey", apiKeyEdit.getText().toString().trim());
                                one.put("apiUrl", apiUrlEdit.getText().toString().trim());
                                one.put("modelName", modelNameEdit.getText().toString().trim());
                                one.put("systemPrompt", systemPromptEdit.getText().toString().trim());

                                int limit = 10;
                                try { limit = Integer.parseInt(contextLimitEdit.getText().toString().trim()); } catch (Exception e) {}
                                one.put("contextLimit", limit);

                                all.put(newName, one);
                                saveZhiliaAllConfigs(all);
                                putString(ZHILIA_ACTIVE_CONFIG_NAME_KEY, newName);
                                syncLegacyZhiliaKeysFromConfig(one);

                                current.setText("当前启用: " + newName);
                                cfgNameEdit.setText(newName);

                                toast("已新增并切换到: " + newName);
                            } catch (Exception e) {
                                toast("新增失败: " + e.getMessage());
                            }
                        }
                    },
                    "用默认值新增",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            try {
                                String newName = nameEdit.getText().toString().trim();
                                if (TextUtils.isEmpty(newName)) {
                                    toast("配置名称不能为空");
                                    return;
                                }

                                JSONObject all = getZhiliaAllConfigs();
                                if (all.containsKey(newName)) {
                                    toast("配置名称已存在");
                                    return;
                                }

                                JSONObject one = new JSONObject();
                                one.put("apiKey", "");
                                one.put("apiUrl", "");
                                one.put("modelName", "");
                                one.put("systemPrompt", "");
                                one.put("contextLimit", 0);

                                all.put(newName, one);
                                saveZhiliaAllConfigs(all);
                                putString(ZHILIA_ACTIVE_CONFIG_NAME_KEY, newName);
                                syncLegacyZhiliaKeysFromConfig(one);

                                current.setText("当前启用: " + newName);
                                cfgNameEdit.setText(newName);
                                apiKeyEdit.setText("");
                                apiUrlEdit.setText("");
                                modelNameEdit.setText("");
                                systemPromptEdit.setText("");
                                contextLimitEdit.setText("");

                                toast("已按默认值新增并切换: " + newName);
                            } catch (Exception e) {
                                toast("新增失败: " + e.getMessage());
                            }
                        }
                    },
                    null,
                    null
                );
                d.show();
            }
        });
        btnCard.addView(addCfgBtn);



        
        Button testBtn = new Button(getTopActivity());
        testBtn.setText("🧪 测试连通性");
        styleUtilityButton(testBtn);
        testBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                final String key = apiKeyEdit.getText().toString().trim();
                final String url = apiUrlEdit.getText().toString().trim();
                final String model = modelNameEdit.getText().toString().trim();

                if (TextUtils.isEmpty(key) || TextUtils.isEmpty(url)) {
                    toast("请先填写 API Key / URL");
                    return;
                }

                // 弹出选择：直接测试当前模型 / 先拉取搜索选择再测试
                AlertDialog.Builder b = new AlertDialog.Builder(getTopActivity());
                b.setTitle("连通测试");
                b.setItems(new String[]{"直接测试当前模型", "先拉取模型并搜索选择后测试"}, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            if (TextUtils.isEmpty(model)) {
                                toast("当前模型为空，请先填写或拉取选择");
                                return;
                            }
                            runZhiliaConnectivityTest(key, url, model);
                        } else {
                            final AlertDialog loading = buildCommonAlertDialog(
                                getTopActivity(), "拉取中", createTextView(getTopActivity(), "正在获取模型列表，请稍候...", 14, 0),
                                null, null, "取消", null, null, null
                            );
                            loading.show();

                            new Thread(new Runnable() {
                                public void run() {
                                    final List<String> models = fetchModelListByApi(url, key);
                                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                                        public void run() {
                                            try { loading.dismiss(); } catch (Exception ignore) {}
                                            if (models == null || models.isEmpty()) {
                                                toast("未获取到模型，请检查 URL/Key/站点兼容性");
                                                return;
                                            }

                                            final java.util.concurrent.atomic.AtomicReference<String> selectedModelForTest =
                                                new java.util.concurrent.atomic.AtomicReference<String>(model);

                                            showModelPickerForTestOnly(url, models, model, selectedModelForTest, new Runnable() {
                                                public void run() {
                                                    String m2 = selectedModelForTest.get();
                                                    if (TextUtils.isEmpty(m2)) {
                                                        toast("请先选择测试模型");
                                                        return;
                                                    }
                                                    runZhiliaConnectivityTest(key, url, m2);
                                                }
                                            });
                                        }
                                    });
                                }
                            }).start();
                        }
                    }
                });
                b.setNegativeButton("取消", null);
                AlertDialog d = b.create();
                d.setOnShowListener(new DialogInterface.OnShowListener() {
                    public void onShow(DialogInterface dialog) { setupUnifiedDialog((AlertDialog) dialog); }
                });
                d.show();
            }
        });
        
        Button fetchModelsBtn = new Button(getTopActivity());
        fetchModelsBtn.setText("📥 拉取模型列表");
        styleUtilityButton(fetchModelsBtn);
        fetchModelsBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                final String key = apiKeyEdit.getText().toString().trim();
                final String url = apiUrlEdit.getText().toString().trim();
                if (TextUtils.isEmpty(url)) {
                    toast("请先填写 API URL");
                    return;
                }
                if (TextUtils.isEmpty(key)) {
                    toast("请先填写 API Key");
                    return;
                }

                final AlertDialog loading = buildCommonAlertDialog(
                    getTopActivity(), "拉取中", createTextView(getTopActivity(), "正在获取模型列表，请稍候...", 14, 0),
                    null, null, "取消", null, null, null
                );
                loading.show();

                new Thread(new Runnable() {
                    public void run() {
                        final List<String> models = fetchModelListByApi(url, key);
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            public void run() {
                                try { loading.dismiss(); } catch (Exception ignore) {}
                                if (models == null || models.isEmpty()) {
                                    toast("未获取到模型，请检查 URL/Key/站点兼容性");
                                    return;
                                }

                                showModelPickerDialogWithSearchAndFav(url, models, modelNameEdit);
                            }
                        });
                    }
                }).start();
            }
        });
        btnCard.addView(fetchModelsBtn);

btnCard.addView(testBtn);


        layout.addView(btnCard);

        final AlertDialog dialog = buildCommonAlertDialog(getTopActivity(), "🧠 智聊AI 多配置", scrollView, "✅ 保存并设为当前", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                String cfgName = cfgNameEdit.getText().toString().trim();
                if (TextUtils.isEmpty(cfgName)) { toast("配置名称不能为空"); return; }

                JSONObject all = getZhiliaAllConfigs();
                JSONObject one = new JSONObject();
                one.put("apiKey", apiKeyEdit.getText().toString().trim());
                one.put("apiUrl", apiUrlEdit.getText().toString().trim());
                one.put("modelName", modelNameEdit.getText().toString().trim());
                one.put("systemPrompt", systemPromptEdit.getText().toString().trim());

                int limit = 10;
                try { limit = Integer.parseInt(contextLimitEdit.getText().toString().trim()); } catch (Exception e) {}
                one.put("contextLimit", limit);

                all.put(cfgName, one);
                saveZhiliaAllConfigs(all);
                putString(ZHILIA_ACTIVE_CONFIG_NAME_KEY, cfgName);
                syncLegacyZhiliaKeysFromConfig(one);
                CheckBox streamCheck = (CheckBox) streamSwitchRow.getChildAt(1);
                putBoolean(ZHILIA_AI_STREAM_ENABLED_KEY, streamCheck != null && streamCheck.isChecked());

                
                String checkKey = getString(ZHILIA_AI_API_KEY, "");
                String checkUrl = getString(ZHILIA_AI_API_URL, "");
                String checkModel = getString(ZHILIA_AI_MODEL_NAME, "");
                int checkCtx = getInt(ZHILIA_AI_CONTEXT_LIMIT, -1);
                debugLog("[智聊AI] 保存后回读: keyLen=" + (checkKey == null ? -1 : checkKey.length())
                    + ", url=" + checkUrl + ", model=" + checkModel + ", ctx=" + checkCtx);
                current.setText("当前启用: " + cfgName);
                toast("已保存并切换到配置: " + cfgName);
                dialog.dismiss();
            }
        }, "❌ 取消", null, null, null);

        dialog.show();

    } catch (Exception e) {
        toast("打开智聊AI设置失败: " + e.getMessage());
        e.printStackTrace();
    }
}

private void showReplySequenceDialog(String title, String enabledKey, String delayKey, String itemsKey, String defaultText, String promptText, String featureName) {
    try {
        ScrollView scrollView = new ScrollView(getTopActivity());
        LinearLayout rootLayout = new LinearLayout(getTopActivity());
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(24, 24, 24, 24);
        rootLayout.setBackgroundColor(Color.parseColor("#FAFBF9"));
        scrollView.addView(rootLayout);

        LinearLayout coreSettingsCard = createCardLayout();
        coreSettingsCard.addView(createSectionTitle(featureName));
        final LinearLayout enabledSwitchRow = createSwitchRow(getTopActivity(), "启用" + featureName, getBoolean(enabledKey, false), new View.OnClickListener() {
            public void onClick(View v) {}
        });
        coreSettingsCard.addView(enabledSwitchRow);
        TextView prompt = createPromptText(promptText);
        coreSettingsCard.addView(prompt);
        rootLayout.addView(coreSettingsCard);

        LinearLayout replyCard = createCardLayout();
        replyCard.addView(createSectionTitle("回复消息序列"));
        final ListView replyItemsListView = new ListView(getTopActivity());
        setupListViewTouchForScroll(replyItemsListView);
        replyItemsListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        LinearLayout.LayoutParams replyListParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(50));
        replyItemsListView.setLayoutParams(replyListParams);
        final ArrayAdapter replyItemsAdapter = new ArrayAdapter(getTopActivity(), android.R.layout.simple_list_item_multiple_choice);
        replyItemsListView.setAdapter(replyItemsAdapter);
        replyCard.addView(replyItemsListView);
        TextView replyPrompt = createPromptText("点击列表项选择，然后使用下面的按钮添加/编辑/删除回复项");
        replyCard.addView(replyPrompt);

        LinearLayout buttonsLayout = new LinearLayout(getTopActivity());
        buttonsLayout.setOrientation(LinearLayout.HORIZONTAL);
        Button addButton = new Button(getTopActivity());
        addButton.setText("➕ 添加");
        styleUtilityButton(addButton);
        Button editButton = new Button(getTopActivity());
        editButton.setText("✏️ 编辑");
        styleUtilityButton(editButton);
        Button delButton = new Button(getTopActivity());
        delButton.setText("🗑️ 删除");
        styleUtilityButton(delButton);
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        addButton.setLayoutParams(buttonParams);
        editButton.setLayoutParams(buttonParams);
        delButton.setLayoutParams(buttonParams);
        buttonsLayout.addView(addButton);
        buttonsLayout.addView(editButton);
        buttonsLayout.addView(delButton);
        replyCard.addView(buttonsLayout);
        rootLayout.addView(replyCard);

        LinearLayout delayCard = createCardLayout();
        delayCard.addView(createSectionTitle("延迟发送消息 (秒)"));
        final EditText delayEdit = createStyledEditText("默认为2秒", String.valueOf(getLong(delayKey, 2L)));
        delayEdit.setInputType(InputType.TYPE_CLASS_NUMBER);
        delayCard.addView(delayEdit);
        rootLayout.addView(delayCard);

        final Set<AcceptReplyItem> selectedItems = new HashSet<AcceptReplyItem>();
        final List replyItems = getReplyItems(itemsKey, defaultText);
        final Runnable refreshList = new Runnable() {
            public void run() {
                replyItemsAdapter.clear();
                for (int i = 0; i < replyItems.size(); i++) {
                    AcceptReplyItem item = (AcceptReplyItem) replyItems.get(i);
                    String typeStr = getReplyTypeStr(item.type);
                    String contentPreview = item.content;

                    if (item.type == ACCEPT_REPLY_TYPE_CARD || item.type == ACCEPT_REPLY_TYPE_INVITE_GROUP) {
                        if (!TextUtils.isEmpty(item.content)) {
                            String[] items = item.content.split(";;;");
                            StringBuilder previewNames = new StringBuilder();
                            for(int j=0; j<Math.min(2, items.length); j++) {
                                if(j>0) previewNames.append(",");
                                previewNames.append(getDisplayNameForWxid(items[j].trim()));
                            }
                            if(items.length > 2) previewNames.append("...");
                            contentPreview = previewNames.toString();
                        }
                    } else if (item.type != ACCEPT_REPLY_TYPE_TEXT) {
                        if (!TextUtils.isEmpty(item.content)) {
                            String[] paths = item.content.split(";;;");
                            if(paths.length > 0) contentPreview = new File(paths[0]).getName();
                            if(paths.length > 1) contentPreview += " 等" + paths.length + "个文件";
                        }
                    }

                    if (contentPreview.length() > 20) contentPreview = contentPreview.substring(0, 20) + "...";
                    replyItemsAdapter.add((i + 1) + ". [" + typeStr + "] " + contentPreview);
                }
                replyItemsAdapter.notifyDataSetChanged();
                replyItemsListView.clearChoices();
                for (int i = 0; i < replyItems.size(); i++) {
                    AcceptReplyItem item = (AcceptReplyItem) replyItems.get(i);
                    if (selectedItems.contains(item)) {
                        replyItemsListView.setItemChecked(i, true);
                    }
                }
                adjustListViewHeight(replyItemsListView, replyItems.size());
                updateReplyButtonsVisibility(editButton, delButton, selectedItems.size());
            }
        };
        refreshList.run();

        replyItemsListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                AcceptReplyItem item = (AcceptReplyItem) replyItems.get(position);
                if (replyItemsListView.isItemChecked(position)) {
                    selectedItems.add(item);
                } else {
                    selectedItems.remove(item);
                }
                updateReplyButtonsVisibility(editButton, delButton, selectedItems.size());
            }
        });

        addButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                AcceptReplyItem newItem = new AcceptReplyItem(ACCEPT_REPLY_TYPE_TEXT, "");
                showEditReplyItemDialog(newItem, replyItems, refreshList, -1, featureName);
            }
        });

        editButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (selectedItems.size() == 1) {
                    AcceptReplyItem editItem = selectedItems.iterator().next();
                    showEditReplyItemDialog(editItem, replyItems, refreshList, -1, featureName);
                } else {
                    toast("编辑时只能选择一个回复项");
                }
            }
        });

        delButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!selectedItems.isEmpty()) {
                    replyItems.removeAll(selectedItems);
                    selectedItems.clear();
                    refreshList.run();
                    toast("选中的回复项已删除");
                } else {
                    toast("请先选择要删除的回复项");
                }
            }
        });

        final CheckBox enabledCheckBox = (CheckBox) enabledSwitchRow.getChildAt(1);

        final AlertDialog dialog = buildCommonAlertDialog(getTopActivity(), title, scrollView, "✅ 保存", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                try {
                    putBoolean(enabledKey, enabledCheckBox.isChecked());
                    if (itemsKey.equals(AUTO_ACCEPT_REPLY_ITEMS_KEY)) {
                        saveAutoAcceptReplyItems(replyItems);
                    } else if (itemsKey.equals(GREET_ON_ACCEPTED_REPLY_ITEMS_KEY)) {
                        saveGreetOnAcceptedReplyItems(replyItems);
                    }

                    long delay = 2L;
                    try {
                        delay = Long.parseLong(delayEdit.getText().toString());
                    } catch (Exception e) { /* ignore */ }
                    putLong(delayKey, delay);

                    toast("设置已保存");
                    dialog.dismiss();
                } catch (Exception e) {
                    toast("保存失败: " + e.getMessage());
                }
            }
        }, "❌ 取消", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        }, null, null);

        dialog.show();

    } catch (Exception e) {
        toast("弹窗失败: " + e.getMessage());
        e.printStackTrace();
    }
}

private void showAutoAcceptFriendDialog() {
    showReplySequenceDialog("✨ 好友请求自动处理设置 ✨", AUTO_ACCEPT_FRIEND_ENABLED_KEY, AUTO_ACCEPT_DELAY_KEY, AUTO_ACCEPT_REPLY_ITEMS_KEY,
                            "%friendName%✨ 你好，很高兴认识你！", "⚠️ 勾选后将自动通过所有好友请求，并发送欢迎消息", "自动同意好友");
}

private void showGreetOnAcceptedDialog() {
    showReplySequenceDialog("✨ 添加好友自动回复设置 ✨", GREET_ON_ACCEPTED_ENABLED_KEY, GREET_ON_ACCEPTED_DELAY_KEY, GREET_ON_ACCEPTED_REPLY_ITEMS_KEY,
                            "哈喽，%friendName%！感谢通过好友请求，以后请多指教啦！", "⚠️ 勾选后，当好友通过你的请求时，将自动发送欢迎消息", "添加好友回复");
}

private void updateReplyButtonsVisibility(Button editButton, Button delButton, int selectedCount) {
    if (selectedCount == 1) {
        editButton.setVisibility(View.VISIBLE);
        delButton.setVisibility(View.VISIBLE);
    } else if (selectedCount > 1) {
        editButton.setVisibility(View.GONE);
        delButton.setVisibility(View.VISIBLE);
    } else {
        editButton.setVisibility(View.GONE);
        delButton.setVisibility(View.GONE);
    }
}

private String getReplyTypeStr(int type) {
    switch (type) {
        case ACCEPT_REPLY_TYPE_TEXT: return "文本";
        case ACCEPT_REPLY_TYPE_IMAGE: return "图片";
        case ACCEPT_REPLY_TYPE_VOICE_FIXED: return "固定语音";
        case ACCEPT_REPLY_TYPE_VOICE_RANDOM: return "随机语音";
        case ACCEPT_REPLY_TYPE_EMOJI: return "表情";
        case ACCEPT_REPLY_TYPE_VIDEO: return "视频";
        case ACCEPT_REPLY_TYPE_CARD: return "名片";
        case ACCEPT_REPLY_TYPE_FILE: return "文件";
        case ACCEPT_REPLY_TYPE_INVITE_GROUP: return "邀请群聊";
        default: return "未知";
    }
}

private void showEditReplyItemDialog(final AcceptReplyItem item, final List itemsList,
                                    final Runnable refreshCallback, final int editPosition, String featureName) {
    try {
        final AtomicReference<AcceptReplyItem> editableItemRef = new AtomicReference<AcceptReplyItem>(item);

        ScrollView scrollView = new ScrollView(getTopActivity());
        LinearLayout layout = new LinearLayout(getTopActivity());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 24, 24, 24);
        layout.setBackgroundColor(Color.parseColor("#FAFBF9"));
        scrollView.addView(layout);

        LinearLayout typeCard = createCardLayout();
        typeCard.addView(createSectionTitle("回复类型"));
        final RadioGroup replyTypeGroup = createRadioGroup(getTopActivity(), LinearLayout.VERTICAL);
        final RadioButton typeTextRadio = createRadioButton(getTopActivity(), "📄文本");
        final RadioButton typeImageRadio = createRadioButton(getTopActivity(), "🖼️图片");
        final RadioButton typeVoiceFixedRadio = createRadioButton(getTopActivity(), "🎤固定语音");
        final RadioButton typeVoiceRandomRadio = createRadioButton(getTopActivity(), "🔀🎤随机语音");
        final RadioButton typeEmojiRadio = createRadioButton(getTopActivity(), "😊表情");
        final RadioButton typeVideoRadio = createRadioButton(getTopActivity(), "🎬视频");
        final RadioButton typeCardRadio = createRadioButton(getTopActivity(), "📇名片");
        final RadioButton typeFileRadio = createRadioButton(getTopActivity(), "📁文件");
        final RadioButton typeInviteGroupRadio = createRadioButton(getTopActivity(), "💌邀请群聊");
        replyTypeGroup.addView(typeTextRadio);
        replyTypeGroup.addView(typeImageRadio);
        replyTypeGroup.addView(typeVoiceFixedRadio);
        replyTypeGroup.addView(typeVoiceRandomRadio);
        replyTypeGroup.addView(typeEmojiRadio);
        replyTypeGroup.addView(typeVideoRadio);
        replyTypeGroup.addView(typeCardRadio);
        replyTypeGroup.addView(typeFileRadio);
        replyTypeGroup.addView(typeInviteGroupRadio);
        typeCard.addView(replyTypeGroup);
        layout.addView(typeCard);

        final TextView contentLabel = new TextView(getTopActivity());
        contentLabel.setText("内容:");
        contentLabel.setTextSize(14);
        contentLabel.setTextColor(Color.parseColor("#333333"));
        contentLabel.setPadding(0, 0, 0, 16);
        final EditText contentEdit = createStyledEditText("请输入内容", editableItemRef.get().content);
        contentEdit.setMinLines(3);
        contentEdit.setGravity(Gravity.TOP);
        layout.addView(contentLabel);
        layout.addView(contentEdit);

        // 【新增】快捷点击插入变量卡片
        final LinearLayout helpCard = createCardLayout();
        helpCard.addView(createSectionTitle("点击变量快捷插入"));
        LinearLayout row1 = new LinearLayout(getTopActivity());
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.addView(createVariableChip("%friendName%", "好友昵称", contentEdit));
        helpCard.addView(row1);
        layout.addView(helpCard);

        final TextView mediaDelayLabel = new TextView(getTopActivity());
        mediaDelayLabel.setText("媒体发送间隔 (秒):");
        mediaDelayLabel.setTextSize(14);
        mediaDelayLabel.setTextColor(Color.parseColor("#333333"));
        mediaDelayLabel.setPadding(0, 0, 0, 16);
        final EditText mediaDelayEdit = createStyledEditText("默认为1秒", String.valueOf(editableItemRef.get().mediaDelaySeconds));
        mediaDelayEdit.setInputType(InputType.TYPE_CLASS_NUMBER);

        final LinearLayout mediaLayout = new LinearLayout(getTopActivity());
        mediaLayout.setOrientation(LinearLayout.VERTICAL);
        mediaLayout.setPadding(0, 0, 0, 16);
        final TextView currentPathTv = new TextView(getTopActivity());
        StringBuilder initialPathDisplay = new StringBuilder();
        if (!TextUtils.isEmpty(editableItemRef.get().content)) {
            String[] parts = editableItemRef.get().content.split(";;;");
            for (int k = 0; k < parts.length; k++) {
                if (!TextUtils.isEmpty(parts[k].trim())) {
                    initialPathDisplay.append(new File(parts[k].trim()).getName()).append("\n");
                }
            }
        }
        currentPathTv.setText(initialPathDisplay.toString().trim().isEmpty() ? "未选择媒体" : initialPathDisplay.toString().trim());
        currentPathTv.setTextSize(14);
        currentPathTv.setTextColor(Color.parseColor("#666666"));
        currentPathTv.setPadding(0, 8, 0, 0);
        final Button selectMediaBtn = new Button(getTopActivity());
        selectMediaBtn.setText("选择媒体文件/文件夹");
        styleMediaSelectionButton(selectMediaBtn);
        mediaLayout.addView(currentPathTv);
        mediaLayout.addView(selectMediaBtn);

        final LinearLayout mediaOrderLayout = new LinearLayout(getTopActivity());
        mediaOrderLayout.setOrientation(LinearLayout.VERTICAL);
        mediaOrderLayout.setPadding(0, 0, 0, 16);
        final ListView mediaListView = new ListView(getTopActivity());
        final ArrayList<String> displayMediaList = new ArrayList<String>();
        mediaListView.setAdapter(new ArrayAdapter<String>(getTopActivity(), android.R.layout.simple_list_item_multiple_choice, displayMediaList));
        mediaListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        setupListViewTouchForScroll(mediaListView);
        LinearLayout.LayoutParams mediaListParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(50));
        mediaListView.setLayoutParams(mediaListParams);
        mediaOrderLayout.addView(mediaListView);
        TextView orderPrompt = createPromptText("选中媒体后，使用下方按钮调整发送顺序（顺序发送，间隔自定义秒）");
        mediaOrderLayout.addView(orderPrompt);
        final LinearLayout orderButtonsLayout = new LinearLayout(getTopActivity());
        orderButtonsLayout.setOrientation(LinearLayout.HORIZONTAL);
        Button upButton = new Button(getTopActivity());
        upButton.setText("⬆ 上移");
        styleUtilityButton(upButton);
        upButton.setEnabled(false);
        Button downButton = new Button(getTopActivity());
        downButton.setText("⬇ 下移");
        styleUtilityButton(downButton);
        downButton.setEnabled(false);
        Button deleteButton = new Button(getTopActivity());
        deleteButton.setText("🗑️ 删除");
        styleUtilityButton(deleteButton);
        deleteButton.setEnabled(false);
        LinearLayout.LayoutParams orderBtnParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        upButton.setLayoutParams(orderBtnParams);
        downButton.setLayoutParams(orderBtnParams);
        deleteButton.setLayoutParams(orderBtnParams);
        orderButtonsLayout.addView(upButton);
        orderButtonsLayout.addView(downButton);
        orderButtonsLayout.addView(deleteButton);
        mediaOrderLayout.addView(orderButtonsLayout);

        // 名片和群聊复用相同的卡片UI
        final LinearLayout cardLayout = new LinearLayout(getTopActivity());
        cardLayout.setOrientation(LinearLayout.VERTICAL);
        cardLayout.setPadding(0, 0, 0, 16);
        final TextView currentCardTv = new TextView(getTopActivity());
        StringBuilder initialCardDisplay = new StringBuilder();
        if (!TextUtils.isEmpty(editableItemRef.get().content)) {
            String[] wxidParts = editableItemRef.get().content.split(";;;");
            for (int k = 0; k < wxidParts.length; k++) {
                if (!TextUtils.isEmpty(wxidParts[k].trim())) {
                    initialCardDisplay.append(getDisplayNameForWxid(wxidParts[k].trim())).append("\n");
                }
            }
        }
        currentCardTv.setText(initialCardDisplay.toString().trim().isEmpty() ? "未选择内容" : initialCardDisplay.toString().trim());
        currentCardTv.setTextSize(14);
        currentCardTv.setTextColor(Color.parseColor("#666666"));
        currentCardTv.setPadding(0, 8, 0, 0);
        final Button selectCardBtn = new Button(getTopActivity());
        selectCardBtn.setText("选择名片/群聊（多选）");
        styleMediaSelectionButton(selectCardBtn);
        cardLayout.addView(currentCardTv);
        cardLayout.addView(selectCardBtn);

        final LinearLayout cardOrderLayout = new LinearLayout(getTopActivity());
        cardOrderLayout.setOrientation(LinearLayout.VERTICAL);
        cardOrderLayout.setPadding(0, 0, 0, 16);
        final ListView cardListView = new ListView(getTopActivity());
        final ArrayList<String> displayCardList = new ArrayList<String>();
        cardListView.setAdapter(new ArrayAdapter<String>(getTopActivity(), android.R.layout.simple_list_item_multiple_choice, displayCardList));
        cardListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        setupListViewTouchForScroll(cardListView);
        LinearLayout.LayoutParams cardListParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(50));
        cardListView.setLayoutParams(cardListParams);
        cardOrderLayout.addView(cardListView);
        TextView cardOrderPrompt = createPromptText("选中项后，使用下方按钮调整发送顺序（顺序发送，间隔自定义秒）");
        cardOrderLayout.addView(cardOrderPrompt);
        final LinearLayout cardOrderButtonsLayout = new LinearLayout(getTopActivity());
        cardOrderButtonsLayout.setOrientation(LinearLayout.HORIZONTAL);
        Button cardUpButton = new Button(getTopActivity());
        cardUpButton.setText("⬆ 上移");
        styleUtilityButton(cardUpButton);
        cardUpButton.setEnabled(false);
        Button cardDownButton = new Button(getTopActivity());
        cardDownButton.setText("⬇ 下移");
        styleUtilityButton(cardDownButton);
        cardDownButton.setEnabled(false);
        Button cardDeleteButton = new Button(getTopActivity());
        cardDeleteButton.setText("🗑️ 删除");
        styleUtilityButton(cardDeleteButton);
        cardDeleteButton.setEnabled(false);
        LinearLayout.LayoutParams cardOrderBtnParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        cardUpButton.setLayoutParams(cardOrderBtnParams);
        cardDownButton.setLayoutParams(cardOrderBtnParams);
        cardDeleteButton.setLayoutParams(cardOrderBtnParams);
        cardOrderButtonsLayout.addView(cardUpButton);
        cardOrderButtonsLayout.addView(cardDownButton);
        cardOrderButtonsLayout.addView(cardDeleteButton);
        cardOrderLayout.addView(cardOrderButtonsLayout);

        final List<String> mediaPaths = new ArrayList<String>();
        if (!TextUtils.isEmpty(editableItemRef.get().content)) {
            String[] parts = editableItemRef.get().content.split(";;;");
            for (int k = 0; k < parts.length; k++) {
                String p = parts[k].trim();
                if (!TextUtils.isEmpty(p)) mediaPaths.add(p);
            }
        }
        final List<String> cardWxids = new ArrayList<String>();
        if (!TextUtils.isEmpty(editableItemRef.get().content)) {
            String[] wxidParts = editableItemRef.get().content.split(";;;");
            for (int k = 0; k < wxidParts.length; k++) {
                String wxid = wxidParts[k].trim();
                if (!TextUtils.isEmpty(wxid)) cardWxids.add(wxid);
            }
        }
        final Set<String> selectedMediaPaths = new HashSet<String>();
        final Set<String> selectedCardWxids = new HashSet<String>();
        final Runnable updateMediaList = new Runnable() {
            public void run() {
                displayMediaList.clear();
                for (int k = 0; k < mediaPaths.size(); k++) {
                    String path = mediaPaths.get(k);
                    String fileName = new File(path).getName();
                    String display = (k + 1) + ". " + (fileName.length() > 30 ? fileName.substring(0, 30) + "..." : fileName);
                    displayMediaList.add(display);
                }
                ((ArrayAdapter<String>) mediaListView.getAdapter()).notifyDataSetChanged();
                mediaListView.clearChoices();
                mediaListView.requestLayout();
                StringBuilder pathDisplay = new StringBuilder();
                for (String path : mediaPaths) {
                    pathDisplay.append(new File(path).getName()).append("\n");
                }
                currentPathTv.setText(pathDisplay.toString().trim().isEmpty() ? "未选择媒体" : pathDisplay.toString().trim());
                editableItemRef.get().content = TextUtils.join(";;;", mediaPaths);
                adjustListViewHeight(mediaListView, mediaPaths.size());
                for (int k = 0; k < mediaPaths.size(); k++) {
                    if (selectedMediaPaths.contains(mediaPaths.get(k))) {
                        mediaListView.setItemChecked(k, true);
                    }
                }
                updateOrderButtons(mediaListView, orderButtonsLayout, mediaPaths.size(), upButton, downButton, deleteButton);
            }
        };
        final Runnable updateCardList = new Runnable() {
            public void run() {
                displayCardList.clear();
                for (int k = 0; k < cardWxids.size(); k++) {
                    String wxid = cardWxids.get(k);
                    String name = getDisplayNameForWxid(wxid);
                    String display = (k + 1) + ". " + (name.length() > 30 ? name.substring(0, 30) + "..." : name);
                    displayCardList.add(display);
                }
                ((ArrayAdapter<String>) cardListView.getAdapter()).notifyDataSetChanged();
                cardListView.clearChoices();
                cardListView.requestLayout();
                StringBuilder cardDisplay = new StringBuilder();
                for (String wxid : cardWxids) {
                    cardDisplay.append(getDisplayNameForWxid(wxid)).append("\n");
                }
                currentCardTv.setText(cardDisplay.toString().trim().isEmpty() ? "未选择内容" : cardDisplay.toString().trim());
                editableItemRef.get().content = TextUtils.join(";;;", cardWxids);
                adjustListViewHeight(cardListView, cardWxids.size());
                for (int k = 0; k < cardWxids.size(); k++) {
                    if (selectedCardWxids.contains(cardWxids.get(k))) {
                        cardListView.setItemChecked(k, true);
                    }
                }
                updateOrderButtons(cardListView, cardOrderButtonsLayout, cardWxids.size(), cardUpButton, cardDownButton, cardDeleteButton);
            }
        };
        updateMediaList.run();
        updateCardList.run();

        final Runnable updateInputs = new Runnable() {
            public void run() {
                int type = editableItemRef.get().type;
                boolean isTextType = (type == ACCEPT_REPLY_TYPE_TEXT);
                boolean isCardOrGroupType = (type == ACCEPT_REPLY_TYPE_CARD || type == ACCEPT_REPLY_TYPE_INVITE_GROUP);
                boolean isMediaType = !isTextType && !isCardOrGroupType;

                contentLabel.setVisibility(isTextType ? View.VISIBLE : View.GONE);
                contentEdit.setVisibility(isTextType ? View.VISIBLE : View.GONE);
                helpCard.setVisibility(isTextType ? View.VISIBLE : View.GONE); // 变量面板跟随文本框
                mediaDelayLabel.setVisibility(isMediaType || isCardOrGroupType ? View.VISIBLE : View.GONE);
                mediaDelayEdit.setVisibility(isMediaType || isCardOrGroupType ? View.VISIBLE : View.GONE);
                mediaLayout.setVisibility(isMediaType ? View.VISIBLE : View.GONE);
                mediaOrderLayout.setVisibility(isMediaType ? View.VISIBLE : View.GONE);
                cardLayout.setVisibility(isCardOrGroupType ? View.VISIBLE : View.GONE);
                cardOrderLayout.setVisibility(isCardOrGroupType ? View.VISIBLE : View.GONE);
                if (type == ACCEPT_REPLY_TYPE_TEXT) {
                    contentLabel.setText("文本内容:");
                    contentEdit.setHint("输入欢迎文本...");
                } else if (type == ACCEPT_REPLY_TYPE_IMAGE) {
                    contentLabel.setText("图片路径:");
                    contentEdit.setHint("输入图片绝对路径");
                    selectMediaBtn.setText("选择图片文件（多选）");
                } else if (type == ACCEPT_REPLY_TYPE_VOICE_FIXED) {
                    contentLabel.setText("语音文件路径:");
                    contentEdit.setHint("输入语音文件绝对路径");
                    selectMediaBtn.setText("选择语音文件（多选）");
                } else if (type == ACCEPT_REPLY_TYPE_VOICE_RANDOM) {
                    contentLabel.setText("语音文件夹路径:");
                    contentEdit.setHint("输入语音文件夹绝对路径");
                    selectMediaBtn.setText("选择语音文件夹");
                } else if (type == ACCEPT_REPLY_TYPE_EMOJI) {
                    contentLabel.setText("表情文件路径:");
                    contentEdit.setHint("输入表情文件绝对路径");
                    selectMediaBtn.setText("选择表情文件（多选）");
                } else if (type == ACCEPT_REPLY_TYPE_VIDEO) {
                    contentLabel.setText("视频文件路径:");
                    contentEdit.setHint("输入视频绝对路径");
                    selectMediaBtn.setText("选择视频文件（多选）");
                } else if (type == ACCEPT_REPLY_TYPE_FILE) {
                    contentLabel.setText("文件路径:");
                    contentEdit.setHint("输入文件绝对路径");
                    selectMediaBtn.setText("选择文件（多选）");
                } else if (type == ACCEPT_REPLY_TYPE_CARD) {
                    contentLabel.setText("名片 Wxid 列表:");
                    contentEdit.setHint("输入要分享的名片的Wxid（多选用;;;分隔）");
                    selectCardBtn.setText("选择名片好友（多选）");
                } else if (type == ACCEPT_REPLY_TYPE_INVITE_GROUP) {
                    contentLabel.setText("群聊 ID 列表:");
                    contentEdit.setHint("输入要邀请的群聊ID（多选用;;;分隔）");
                    selectCardBtn.setText("选择要邀请的群聊（多选）");
                }
                Object[] tag = getMediaSelectTag(type);
                selectMediaBtn.setTag(tag);
            }
        };

        switch (editableItemRef.get().type) {
            case ACCEPT_REPLY_TYPE_IMAGE: replyTypeGroup.check(typeImageRadio.getId()); break;
            case ACCEPT_REPLY_TYPE_VOICE_FIXED: replyTypeGroup.check(typeVoiceFixedRadio.getId()); break;
            case ACCEPT_REPLY_TYPE_VOICE_RANDOM: replyTypeGroup.check(typeVoiceRandomRadio.getId()); break;
            case ACCEPT_REPLY_TYPE_EMOJI: replyTypeGroup.check(typeEmojiRadio.getId()); break;
            case ACCEPT_REPLY_TYPE_VIDEO: replyTypeGroup.check(typeVideoRadio.getId()); break;
            case ACCEPT_REPLY_TYPE_CARD: replyTypeGroup.check(typeCardRadio.getId()); break;
            case ACCEPT_REPLY_TYPE_FILE: replyTypeGroup.check(typeFileRadio.getId()); break;
            case ACCEPT_REPLY_TYPE_INVITE_GROUP: replyTypeGroup.check(typeInviteGroupRadio.getId()); break;
            default: replyTypeGroup.check(typeTextRadio.getId());
        }
        updateInputs.run();

        replyTypeGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == typeTextRadio.getId()) editableItemRef.get().type = ACCEPT_REPLY_TYPE_TEXT;
                else if (checkedId == typeImageRadio.getId()) editableItemRef.get().type = ACCEPT_REPLY_TYPE_IMAGE;
                else if (checkedId == typeVoiceFixedRadio.getId()) editableItemRef.get().type = ACCEPT_REPLY_TYPE_VOICE_FIXED;
                else if (checkedId == typeVoiceRandomRadio.getId()) editableItemRef.get().type = ACCEPT_REPLY_TYPE_VOICE_RANDOM;
                else if (checkedId == typeEmojiRadio.getId()) editableItemRef.get().type = ACCEPT_REPLY_TYPE_EMOJI;
                else if (checkedId == typeVideoRadio.getId()) editableItemRef.get().type = ACCEPT_REPLY_TYPE_VIDEO;
                else if (checkedId == typeCardRadio.getId()) editableItemRef.get().type = ACCEPT_REPLY_TYPE_CARD;
                else if (checkedId == typeFileRadio.getId()) editableItemRef.get().type = ACCEPT_REPLY_TYPE_FILE;
                else if (checkedId == typeInviteGroupRadio.getId()) editableItemRef.get().type = ACCEPT_REPLY_TYPE_INVITE_GROUP;
                updateInputs.run();
            }
        });

        layout.addView(mediaDelayLabel);
        layout.addView(mediaDelayEdit);

        selectMediaBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                int type = editableItemRef.get().type;
                String current = editableItemRef.get().content;
                Object[] tag = (Object[]) selectMediaBtn.getTag();
                String extFilter = (String) tag[0];
                boolean isFolder = (Boolean) tag[1];
                boolean allowFolder = (Boolean) tag[2];
                final boolean isMulti = (Boolean) tag[3];
                File lastFolder = new File(getString(DEFAULT_LAST_FOLDER_SP_AUTO, ROOT_FOLDER));
                if (isFolder) {
                    browseFolderForSelectionAuto(lastFolder, "", current, new MediaSelectionCallback() {
                        public void onSelected(ArrayList<String> selectedFiles) {
                            if (selectedFiles.size() == 1) {
                                String path = selectedFiles.get(0);
                                File f = new File(path);
                                if (f.isDirectory()) {
                                    mediaPaths.clear();
                                    mediaPaths.add(path);
                                    updateMediaList.run();
                                } else {
                                    toast("请选择文件夹");
                                }
                            }
                        }
                    }, allowFolder);
                } else {
                    browseFolderForSelectionAuto(lastFolder, extFilter, current, new MediaSelectionCallback() {
                        public void onSelected(ArrayList<String> selectedFiles) {
                            if (isMulti) {
                                mediaPaths.clear();
                                mediaPaths.addAll(selectedFiles);
                            } else {
                                mediaPaths.clear();
                                if (!selectedFiles.isEmpty()) {
                                    mediaPaths.add(selectedFiles.get(0));
                                }
                            }
                            updateMediaList.run();
                        }
                    }, allowFolder);
                }
            }
        });

        selectCardBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (editableItemRef.get().type == ACCEPT_REPLY_TYPE_INVITE_GROUP) {
                    showLoadingDialog("选择群聊", "  正在加载群聊列表...", new Runnable() {
                        public void run() {
                            if (sCachedGroupList == null) sCachedGroupList = getGroupList();
                            new Handler(Looper.getMainLooper()).post(new Runnable() {
                                public void run() {
                                    if (sCachedGroupList == null || sCachedGroupList.isEmpty()) {
                                        toast("未获取到群聊列表"); return;
                                    }
                                    List names = new ArrayList();
                                    List ids = new ArrayList();
                                    for (int i = 0; i < sCachedGroupList.size(); i++) {
                                        GroupInfo groupInfo = (GroupInfo) sCachedGroupList.get(i);
                                        String groupName = TextUtils.isEmpty(groupInfo.getName()) ? "未知群聊" : groupInfo.getName();
                                        names.add("🏠 " + groupName + "\nID: " + groupInfo.getRoomId());
                                        ids.add(groupInfo.getRoomId());
                                    }
                                    final Set<String> tempSelected = new HashSet<String>(cardWxids);
                                    showMultiSelectDialog("✨ 选择要邀请的群聊 ✨", names, ids, tempSelected, "🔍 搜索群聊...", new Runnable() {
                                        public void run() {
                                            cardWxids.clear();
                                            cardWxids.addAll(tempSelected);
                                            updateCardList.run();
                                        }
                                    }, null);
                                }
                            });
                        }
                    });
                } else {
                    showLoadingDialog("选择名片好友", "  正在加载好友列表...", new Runnable() {
                        public void run() {
                            if (sCachedFriendList == null) sCachedFriendList = getFriendList();
                            new Handler(Looper.getMainLooper()).post(new Runnable() {
                                public void run() {
                                    if (sCachedFriendList == null || sCachedFriendList.isEmpty()) {
                                        toast("未获取到好友列表"); return;
                                    }
                                    List names = new ArrayList();
                                    List ids = new ArrayList();
                                    for (int i = 0; i < sCachedFriendList.size(); i++) {
                                        FriendInfo friendInfo = (FriendInfo) sCachedFriendList.get(i);
                                        String nickname = TextUtils.isEmpty(friendInfo.getNickname()) ? "未知昵称" : friendInfo.getNickname();
                                        String remark = friendInfo.getRemark();
                                        String displayName = !TextUtils.isEmpty(remark) ? nickname + " (" + remark + ")" : nickname;
                                        names.add("👤 " + displayName + "\nID: " + friendInfo.getWxid());
                                        ids.add(friendInfo.getWxid());
                                    }
                                    final Set<String> tempSelectedWxids = new HashSet<String>(cardWxids);
                                    showMultiSelectDialog("✨ 选择名片好友 ✨", names, ids, tempSelectedWxids, "🔍 搜索好友(昵称/备注)...", new Runnable() {
                                        public void run() {
                                            cardWxids.clear();
                                            cardWxids.addAll(tempSelectedWxids);
                                            updateCardList.run();
                                        }
                                    }, null);
                                }
                            });
                        }
                    });
                }
            }
        });

        mediaListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String path = mediaPaths.get(position);
                if (mediaListView.isItemChecked(position)) {
                    selectedMediaPaths.add(path);
                } else {
                    selectedMediaPaths.remove(path);
                }
                updateOrderButtons(mediaListView, orderButtonsLayout, mediaPaths.size(), upButton, downButton, deleteButton);
            }
        });
        upButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (selectedMediaPaths.size() == 1) {
                    String selectedPath = selectedMediaPaths.iterator().next();
                    int pos = mediaPaths.indexOf(selectedPath);
                    if (pos > 0) {
                        Collections.swap(mediaPaths, pos, pos - 1);
                        updateMediaList.run();
                    }
                }
            }
        });
        downButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (selectedMediaPaths.size() == 1) {
                    String selectedPath = selectedMediaPaths.iterator().next();
                    int pos = mediaPaths.indexOf(selectedPath);
                    if (pos < mediaPaths.size() - 1) {
                        Collections.swap(mediaPaths, pos, pos + 1);
                        updateMediaList.run();
                    }
                }
            }
        });
        deleteButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!selectedMediaPaths.isEmpty()) {
                    mediaPaths.removeAll(selectedMediaPaths);
                    selectedMediaPaths.clear();
                    updateMediaList.run();
                }
            }
        });

        cardListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String wxid = cardWxids.get(position);
                if (cardListView.isItemChecked(position)) {
                    selectedCardWxids.add(wxid);
                } else {
                    selectedCardWxids.remove(wxid);
                }
                updateOrderButtons(cardListView, cardOrderButtonsLayout, cardWxids.size(), cardUpButton, cardDownButton, cardDeleteButton);
            }
        });
        cardUpButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (selectedCardWxids.size() == 1) {
                    String selectedWxid = selectedCardWxids.iterator().next();
                    int pos = cardWxids.indexOf(selectedWxid);
                    if (pos > 0) {
                        Collections.swap(cardWxids, pos, pos - 1);
                        updateCardList.run();
                    }
                }
            }
        });
        cardDownButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (selectedCardWxids.size() == 1) {
                    String selectedWxid = selectedCardWxids.iterator().next();
                    int pos = cardWxids.indexOf(selectedWxid);
                    if (pos < cardWxids.size() - 1) {
                        Collections.swap(cardWxids, pos, pos + 1);
                        updateCardList.run();
                    }
                }
            }
        });
        cardDeleteButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!selectedCardWxids.isEmpty()) {
                    cardWxids.removeAll(selectedCardWxids);
                    selectedCardWxids.clear();
                    updateCardList.run();
                }
            }
        });

        layout.addView(mediaLayout);
        layout.addView(mediaOrderLayout);
        layout.addView(cardLayout);
        layout.addView(cardOrderLayout);

        String dialogTitle = (editPosition >= 0) ? "编辑回复项 (" + featureName + ")" : "添加回复项 (" + featureName + ")";
        final AlertDialog dialog = buildCommonAlertDialog(getTopActivity(), dialogTitle, scrollView, "✅ 保存", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                int type = editableItemRef.get().type;
                long mediaDelay = 1L;
                try {
                    mediaDelay = Long.parseLong(mediaDelayEdit.getText().toString().trim());
                } catch (Exception e) {
                    mediaDelay = 1L;
                }
                editableItemRef.get().mediaDelaySeconds = mediaDelay;

                if (type == ACCEPT_REPLY_TYPE_TEXT) {
                    editableItemRef.get().content = contentEdit.getText().toString().trim();
                    if (TextUtils.isEmpty(editableItemRef.get().content)) {
                        toast("内容不能为空");
                        return;
                    }
                } else if (type == ACCEPT_REPLY_TYPE_CARD || type == ACCEPT_REPLY_TYPE_INVITE_GROUP) {
                    editableItemRef.get().content = TextUtils.join(";;;", cardWxids);
                    if (cardWxids.isEmpty()) {
                        toast(type == ACCEPT_REPLY_TYPE_CARD ? "名片Wxid不能为空" : "群聊ID不能为空");
                        return;
                    }
                } else {
                    editableItemRef.get().content = TextUtils.join(";;;", mediaPaths);
                    if (mediaPaths.isEmpty()) {
                        toast("路径不能为空");
                        return;
                    }
                    for (String path : mediaPaths) {
                        File file = new File(path);
                        if (type == ACCEPT_REPLY_TYPE_IMAGE ||
                            type == ACCEPT_REPLY_TYPE_VOICE_FIXED ||
                            type == ACCEPT_REPLY_TYPE_EMOJI ||
                            type == ACCEPT_REPLY_TYPE_VIDEO ||
                            type == ACCEPT_REPLY_TYPE_FILE) {
                            if (!file.exists()) {
                                toast("文件不存在: " + path);
                                return;
                            }
                        } else if (type == ACCEPT_REPLY_TYPE_VOICE_RANDOM) {
                            if (!file.exists() || !file.isDirectory()) {
                                toast("文件夹不存在");
                                return;
                            }
                        }
                    }
                }

                if (editPosition >= 0 && editPosition < itemsList.size()) {
                    itemsList.set(editPosition, editableItemRef.get());
                } else {
                    itemsList.add(editableItemRef.get());
                }

                refreshCallback.run();
                toast("已保存");
            }
        }, "❌ 取消", null, null, null);

        dialog.show();
    } catch (Exception e) {
        toast("弹窗失败: " + e.getMessage());
        e.printStackTrace();
    }
}

private List<Integer> getSelectedPositions(ListView listView) {
    List<Integer> selected = new ArrayList<Integer>();
    for (int i = 0; i < listView.getCount(); i++) {
        if (listView.isItemChecked(i)) {
            selected.add(i);
        }
    }
    java.util.Collections.sort(selected, java.util.Collections.reverseOrder());
    return selected;
}

private void updateOrderButtons(ListView listView, LinearLayout buttonsLayout, int itemCount, Button upButton, Button downButton, Button deleteButton) {
    List<Integer> selectedPositions = getSelectedPositions(listView);
    int selectedCount = selectedPositions.size();
    if (selectedCount == 0) {
        upButton.setVisibility(View.GONE);
        downButton.setVisibility(View.GONE);
        deleteButton.setVisibility(View.GONE);
    } else if (selectedCount == 1) {
        int pos = selectedPositions.get(0);
        upButton.setVisibility(View.VISIBLE);
        upButton.setEnabled(pos > 0);
        downButton.setVisibility(View.VISIBLE);
        downButton.setEnabled(pos < itemCount - 1);
        deleteButton.setVisibility(View.VISIBLE);
        deleteButton.setEnabled(true);
    } else {
        upButton.setVisibility(View.GONE);
        downButton.setVisibility(View.GONE);
        deleteButton.setVisibility(View.VISIBLE);
        deleteButton.setEnabled(true);
    }
}

private Object[] getMediaSelectTag(int type) {
    String extFilter = "";
    boolean isFolder = false;
    boolean allowFolder = false;
    boolean isMulti = false;
    switch (type) {
        case ACCEPT_REPLY_TYPE_IMAGE:
            extFilter = "";
            isMulti = true;
            break;
        case ACCEPT_REPLY_TYPE_VOICE_FIXED:
            extFilter = "";
            isMulti = true;
            break;
        case ACCEPT_REPLY_TYPE_VOICE_RANDOM:
            isFolder = true;
            allowFolder = true;
            isMulti = false;
            break;
        case ACCEPT_REPLY_TYPE_EMOJI:
            extFilter = "";
            isMulti = true;
            break;
        case ACCEPT_REPLY_TYPE_VIDEO:
            extFilter = "";
            isMulti = true;
            break;
        case ACCEPT_REPLY_TYPE_FILE:
            extFilter = "";
            isMulti = true;
            break;
    }
    return new Object[]{extFilter, isFolder, allowFolder, isMulti};
}

private void showAutoReplyRulesDialog() {
    try {
        final List rules = loadAutoReplyRules();
        ScrollView scrollView = new ScrollView(getTopActivity());
        LinearLayout rootLayout = new LinearLayout(getTopActivity());
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(24, 24, 24, 24);
        rootLayout.setBackgroundColor(Color.parseColor("#FAFBF9"));
        scrollView.addView(rootLayout);

        LinearLayout rulesCard = createCardLayout();
        rulesCard.addView(createSectionTitle("📝 自动回复规则管理"));
        final ListView rulesListView = new ListView(getTopActivity());
        setupListViewTouchForScroll(rulesListView);
        rulesListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        LinearLayout.LayoutParams rulesListParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(50));
        rulesListView.setLayoutParams(rulesListParams);
        final ArrayAdapter rulesAdapter = new ArrayAdapter(getTopActivity(), android.R.layout.simple_list_item_multiple_choice);
        rulesListView.setAdapter(rulesAdapter);
        rulesCard.addView(rulesListView);
        TextView rulesPrompt = createPromptText("点击列表项选择，然后使用下面的按钮添加/编辑/删除规则");
        rulesCard.addView(rulesPrompt);

        LinearLayout buttonsLayout = new LinearLayout(getTopActivity());
        buttonsLayout.setOrientation(LinearLayout.HORIZONTAL);
        Button addButton = new Button(getTopActivity());
        addButton.setText("➕ 添加");
        styleUtilityButton(addButton);
        Button editButton = new Button(getTopActivity());
        editButton.setText("✏️ 编辑");
        styleUtilityButton(editButton);
        Button delButton = new Button(getTopActivity());
        delButton.setText("🗑️ 删除");
        styleUtilityButton(delButton);
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        addButton.setLayoutParams(buttonParams);
        editButton.setLayoutParams(buttonParams);
        delButton.setLayoutParams(buttonParams);
        buttonsLayout.addView(addButton);
        buttonsLayout.addView(editButton);
        buttonsLayout.addView(delButton);
        rulesCard.addView(buttonsLayout);
        rootLayout.addView(rulesCard);

        final Set<Map<String, Object>> selectedRules = new HashSet<Map<String, Object>>();
        final Runnable refreshRulesList = new Runnable() {
            public void run() {
                rulesAdapter.clear();
                for (int i = 0; i < rules.size(); i++) {
                    Map<String, Object> rule = (Map<String, Object>) rules.get(i);
                    boolean enabled = (Boolean) rule.get("enabled");
                    String status = enabled ? "✅" : "❌";
                    int matchType = (Integer) rule.get("matchType");
                    String matchTypeStr = getMatchTypeStr(matchType);
                    int atTriggerType = (Integer) rule.get("atTriggerType");
                    String atTriggerStr = getAtTriggerStr(atTriggerType);
                    int patTriggerType = (Integer) rule.get("patTriggerType");
                    String patTriggerStr = getPatTriggerStr(patTriggerType);
                    Set targetWxids = (Set) rule.get("targetWxids");
                    Set excludedWxids = (Set) rule.get("excludedWxids");
                    Set excludedGroupMemberWxids = (Set) rule.get("excludedGroupMemberWxids");
                    Set includedGroupMemberWxids = (Set) rule.get("includedGroupMemberWxids");
                    int targetType = (Integer) rule.get("targetType");
                    String targetInfo = getTargetInfo(targetType, targetWxids, excludedWxids, excludedGroupMemberWxids, includedGroupMemberWxids);
                    int replyType = (Integer) rule.get("replyType");
                    String replyTypeStr = getReplyTypeStrForRule(replyType);
                    String replyContentPreview = getReplyContentPreview(rule);
                    long delaySeconds = (Long) rule.get("delaySeconds");
                    String delayInfo = (delaySeconds > 0) ? " 延迟" + delaySeconds + "秒" : "";
                    long mediaDelaySeconds = (Long) rule.get("mediaDelaySeconds");
                    String mediaDelayInfo = (mediaDelaySeconds > 1) ? " 媒体间隔" + mediaDelaySeconds + "秒" : "";
                    boolean replyAsQuote = (Boolean) rule.get("replyAsQuote");
                    String quoteInfo = replyAsQuote ? " [引用]" : "";
                    String startTime = (String) rule.get("startTime");
                    String endTime = (String) rule.get("endTime");
                    String timeInfo = getTimeInfo(startTime, endTime);
                    String keyword = (String) rule.get("keyword");
                    rulesAdapter.add((i + 1) + ". " + status + " [" + matchTypeStr + "] [" + atTriggerStr + "] [" + patTriggerStr + "] " + (matchType == MATCH_TYPE_ANY ? "(任何消息)" : keyword) + " → " + replyTypeStr + replyContentPreview + targetInfo + delayInfo + mediaDelayInfo + quoteInfo + timeInfo);
                }
                rulesAdapter.notifyDataSetChanged();
                rulesListView.clearChoices();
                for (int i = 0; i < rules.size(); i++) {
                    Map<String, Object> rule = (Map<String, Object>) rules.get(i);
                    if (selectedRules.contains(rule)) {
                        rulesListView.setItemChecked(i, true);
                    }
                }
                adjustListViewHeight(rulesListView, rules.size());
                updateReplyButtonsVisibility(editButton, delButton, selectedRules.size());
            }
        };
        refreshRulesList.run();

        rulesListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Map<String, Object> item = (Map<String, Object>) rules.get(position);
                if (rulesListView.isItemChecked(position)) {
                    selectedRules.add(item);
                } else {
                    selectedRules.remove(item);
                }
                updateReplyButtonsVisibility(editButton, delButton, selectedRules.size());
            }
        });

        addButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Map<String, Object> newRule = createAutoReplyRuleMap("", "", true, MATCH_TYPE_FUZZY, new HashSet(), TARGET_TYPE_NONE, AT_TRIGGER_NONE, 0, false, REPLY_TYPE_TEXT, new ArrayList());
                showEditRuleDialog(newRule, rules, refreshRulesList);
            }
        });

        editButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (selectedRules.size() == 1) {
                    Map<String, Object> editRule = selectedRules.iterator().next();
                    showEditRuleDialog(editRule, rules, refreshRulesList);
                } else {
                    toast("编辑时只能选择一个规则");
                }
            }
        });

        delButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!selectedRules.isEmpty()) {
                    rules.removeAll(selectedRules);
                    selectedRules.clear();
                    refreshRulesList.run();
                    toast("选中的规则已删除");
                } else {
                    toast("请先选择要删除的规则");
                }
            }
        });

        final AlertDialog dialog = buildCommonAlertDialog(getTopActivity(), "✨ 自动回复规则管理 ✨", scrollView, "✅ 保存", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                saveAutoReplyRules(rules);
                toast("规则已保存");
                dialog.dismiss();
            }
        }, "❌ 关闭", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                saveAutoReplyRules(rules);
                dialog.dismiss();
            }
        }, null, null);

        dialog.show();
    } catch (Exception e) {
        toast("弹窗失败: " + e.getMessage());
        e.printStackTrace();
    }
}

private String getPatTriggerStr(int patTriggerType) {
    if (patTriggerType == PAT_TRIGGER_ME) return "被拍一拍";
    else return "不限拍一拍";
}

private String getMatchTypeStr(int matchType) {
    if (matchType == MATCH_TYPE_EXACT) return "全字";
    else if (matchType == MATCH_TYPE_REGEX) return "正则";
    else if (matchType == MATCH_TYPE_ANY) return "任何消息";
    else return "模糊";
}

private String getAtTriggerStr(int atTriggerType) {
    if (atTriggerType == AT_TRIGGER_ME) return "@我";
    else if (atTriggerType == AT_TRIGGER_ALL) return "@全体";
    else return "不限@";
}

private String getTargetInfo(int targetType, Set targetWxids, Set excludedWxids, Set excludedGroupMemberWxids, Set includedGroupMemberWxids) {
    StringBuilder sb = new StringBuilder();
    if (targetType == TARGET_TYPE_FRIEND) {
        sb.append(" (指定好友: ").append(targetWxids != null ? targetWxids.size() : 0).append("人)");
    } else if (targetType == TARGET_TYPE_GROUP) {
        sb.append(" (指定群聊: ").append(targetWxids != null ? targetWxids.size() : 0).append("个)");
    } else if (targetType == TARGET_TYPE_BOTH) {
        sb.append(" (指定好友/群聊: ").append(targetWxids != null ? targetWxids.size() : 0).append("个)");
        // 显示指定群聊成员
        if (includedGroupMemberWxids != null && !includedGroupMemberWxids.isEmpty()) {
            sb.append(" 限成员:").append(includedGroupMemberWxids.size()).append("人");
        }
    } else {
        // 不指定模式，显示排除信息
        if (excludedWxids != null && !excludedWxids.isEmpty()) {
            sb.append(" (排除好友/群聊: ").append(excludedWxids.size()).append("个)");
        }
        if (excludedGroupMemberWxids != null && !excludedGroupMemberWxids.isEmpty()) {
            sb.append(" 排除成员:").append(excludedGroupMemberWxids.size()).append("人");
        }
    }
    return sb.toString();
}

private String getReplyTypeStrForRule(int replyType) {
    switch (replyType) {
        case REPLY_TYPE_XIAOZHI_AI: return " [小智AI]";
        case REPLY_TYPE_ZHILIA_AI: return " [智聊AI]";
        case REPLY_TYPE_IMAGE: return " [图片]";
        case REPLY_TYPE_VOICE_FILE_LIST: return " [语音(文件列表)]";
        case REPLY_TYPE_VOICE_FOLDER: return " [语音(文件夹随机)]";
        case REPLY_TYPE_EMOJI: return " [表情]";
        case REPLY_TYPE_VIDEO: return " [视频]";
        case REPLY_TYPE_FILE: return " [文件]";
        case REPLY_TYPE_CARD: return " [名片]";
        case REPLY_TYPE_INVITE_GROUP: return " [邀请群聊]";
        default: return " [文本]";
    }
}

private String getReplyContentPreview(Map<String, Object> rule) {
    int replyType = (Integer) rule.get("replyType");
    switch (replyType) {
        case REPLY_TYPE_XIAOZHI_AI:
        case REPLY_TYPE_ZHILIA_AI:
            return "智能聊天";
        case REPLY_TYPE_IMAGE:
        case REPLY_TYPE_EMOJI:
        case REPLY_TYPE_VIDEO:
        case REPLY_TYPE_FILE:
            List mediaPaths = (List) rule.get("mediaPaths");
            if (mediaPaths != null && !mediaPaths.isEmpty()) {
                String path = (String) mediaPaths.get(0);
                return " (" + mediaPaths.size() + "个): ..." + new File(path).getName();
            }
            return "未设置路径";
        case REPLY_TYPE_VOICE_FILE_LIST:
            List mediaPaths2 = (List) rule.get("mediaPaths");
            if (mediaPaths2 != null && !mediaPaths2.isEmpty()) {
                String path = (String) mediaPaths2.get(0);
                return " (" + mediaPaths2.size() + "个语音): ..." + new File(path).getName();
            }
            return "未设置语音文件路径";
        case REPLY_TYPE_VOICE_FOLDER:
            List mediaPaths3 = (List) rule.get("mediaPaths");
            if (mediaPaths3 != null && !mediaPaths3.isEmpty()) {
                String path = (String) mediaPaths3.get(0);
                return "文件夹: ..." + new File(path).getName();
            }
            return "未设置语音文件夹路径";
        case REPLY_TYPE_CARD:
        case REPLY_TYPE_INVITE_GROUP:
            String reply = (String) rule.get("reply");
            if (!TextUtils.isEmpty(reply)) {
                String[] items = reply.split(";;;");
                StringBuilder previewNames = new StringBuilder();
                for(int j=0; j<Math.min(2, items.length); j++) {
                    if(j>0) previewNames.append(",");
                    previewNames.append(getDisplayNameForWxid(items[j].trim()));
                }
                if(items.length > 2) previewNames.append("...");
                return " (" + items.length + "个): " + previewNames.toString();
            }
            return "未设置目标ID";
        default:
            String textReply = (String) rule.get("reply");
            return textReply != null && textReply.length() > 20 ? textReply.substring(0, 20) + "..." : (textReply != null ? textReply : "");
    }
}

private String getTimeInfo(String startTime, String endTime) {
    String timeInfo = "";
    if (!TextUtils.isEmpty(startTime)) {
        timeInfo += " 🕒开始" + startTime;
    }
    if (!TextUtils.isEmpty(endTime)) {
        timeInfo += (timeInfo.isEmpty() ? " 🕒结束" + endTime : " - " + endTime);
    }
    if (!timeInfo.isEmpty()) {
        timeInfo += " ";
    }
    return timeInfo;
}

private void showEditRuleDialog(final Map<String, Object> rule, final List rules, final Runnable refreshCallback) {
    try {
        ScrollView scrollView = new ScrollView(getTopActivity());
        LinearLayout layout = new LinearLayout(getTopActivity());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 24, 24, 24);
        layout.setBackgroundColor(Color.parseColor("#FAFBF9"));
        scrollView.addView(layout);

        LinearLayout keywordCard = createCardLayout();
        keywordCard.addView(createSectionTitle("关键词"));
        final EditText keywordEdit = createStyledEditText("输入触发关键词...", (String) rule.get("keyword"));
        keywordCard.addView(keywordEdit);
        layout.addView(keywordCard);

        LinearLayout typeCard = createCardLayout();
        typeCard.addView(createSectionTitle("回复类型"));
        final RadioGroup replyTypeGroup = createRadioGroup(getTopActivity(), LinearLayout.VERTICAL);
        final RadioButton replyTypeXiaozhiAIRadio = createRadioButton(getTopActivity(), "🤖 小智AI回复");
        final RadioButton replyTypeZhiliaAIRadio = createRadioButton(getTopActivity(), "🧠 智聊AI回复");
        final RadioButton replyTypeTextRadio = createRadioButton(getTopActivity(), "📄文本");
        final RadioButton replyTypeImageRadio = createRadioButton(getTopActivity(), "🖼️图片");
        final RadioButton replyTypeEmojiRadio = createRadioButton(getTopActivity(), "😊表情");
        final RadioButton replyTypeVideoRadio = createRadioButton(getTopActivity(), "🎬视频");
        final RadioButton replyTypeCardRadio = createRadioButton(getTopActivity(), "📇名片");
        final RadioButton replyTypeVoiceFileListRadio = createRadioButton(getTopActivity(), "🎤语音(文件列表)");
        final RadioButton replyTypeVoiceFolderRadio = createRadioButton(getTopActivity(), "🔀🎤语音(文件夹随机)");
        final RadioButton replyTypeFileRadio = createRadioButton(getTopActivity(), "📁文件");
        final RadioButton replyTypeInviteGroupRadio = createRadioButton(getTopActivity(), "💌邀请群聊");
        replyTypeGroup.addView(replyTypeXiaozhiAIRadio);
        replyTypeGroup.addView(replyTypeZhiliaAIRadio);
        replyTypeGroup.addView(replyTypeTextRadio);
        replyTypeGroup.addView(replyTypeImageRadio);
        replyTypeGroup.addView(replyTypeEmojiRadio);
        replyTypeGroup.addView(replyTypeVideoRadio);
        replyTypeGroup.addView(replyTypeCardRadio);
        replyTypeGroup.addView(replyTypeVoiceFileListRadio);
        replyTypeGroup.addView(replyTypeVoiceFolderRadio);
        replyTypeGroup.addView(replyTypeFileRadio);
        replyTypeGroup.addView(replyTypeInviteGroupRadio);
        typeCard.addView(replyTypeGroup);
        layout.addView(typeCard);

        final TextView replyContentLabel = new TextView(getTopActivity());
        replyContentLabel.setText("回复内容:");
        replyContentLabel.setTextSize(14);
        replyContentLabel.setTextColor(Color.parseColor("#333333"));
        replyContentLabel.setPadding(0, 0, 0, 16);
        final EditText replyEdit = createStyledEditText("输入自动回复内容...", (String) rule.get("reply"));
        replyEdit.setMinLines(3);
        replyEdit.setGravity(Gravity.TOP);

        // 【新增】快捷插入变量面板（动态显示）
        final LinearLayout helpCard = createCardLayout();
        helpCard.addView(createSectionTitle("点击变量插入到回复内容"));
        LinearLayout row1 = new LinearLayout(getTopActivity());
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.addView(createVariableChip("%senderName%", "发送者昵称", replyEdit));
        row1.addView(createVariableChip("%senderWxid%", "发送者wxid", replyEdit));
        LinearLayout row2 = new LinearLayout(getTopActivity());
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.addView(createVariableChip("%groupName%", "群名称", replyEdit));
        row2.addView(createVariableChip("%time%", "当前时间", replyEdit));
        LinearLayout row3 = new LinearLayout(getTopActivity());
        row3.setOrientation(LinearLayout.HORIZONTAL);
        row3.addView(createVariableChip("%atSender%", "@发送者", replyEdit));
        helpCard.addView(row1);
        helpCard.addView(row2);
        helpCard.addView(row3);

        final TextView mediaDelayLabel = new TextView(getTopActivity());
        mediaDelayLabel.setText("媒体发送间隔 (秒):");
        mediaDelayLabel.setTextSize(14);
        mediaDelayLabel.setTextColor(Color.parseColor("#333333"));
        mediaDelayLabel.setPadding(0, 0, 0, 16);
        final EditText mediaDelayEdit = createStyledEditText("默认为1秒", String.valueOf(rule.get("mediaDelaySeconds")));
        mediaDelayEdit.setInputType(InputType.TYPE_CLASS_NUMBER);

        final LinearLayout mediaLayout = new LinearLayout(getTopActivity());
        mediaLayout.setOrientation(LinearLayout.VERTICAL);
        mediaLayout.setPadding(0, 0, 0, 16);
        final TextView currentMediaTv = new TextView(getTopActivity());
        StringBuilder initialMediaDisplay = new StringBuilder();
        Object mediaObj = rule.get("mediaPaths");
        List mediaPathsInit = (mediaObj instanceof List) ? (List) mediaObj : null;
        if (mediaPathsInit != null && !mediaPathsInit.isEmpty()) {
            for (int i = 0; i < mediaPathsInit.size(); i++) {
                Object pObj = mediaPathsInit.get(i);
                if (pObj instanceof String) {
                    String p = (String) pObj;
                    if (!TextUtils.isEmpty(p)) {
                        initialMediaDisplay.append(new File(p).getName()).append("\n");
                    }
                }
            }
        }
        currentMediaTv.setText(initialMediaDisplay.toString().trim().isEmpty() ? "未选择媒体" : initialMediaDisplay.toString().trim());
        currentMediaTv.setTextSize(14);
        currentMediaTv.setTextColor(Color.parseColor("#666666"));
        currentMediaTv.setPadding(0, 8, 0, 0);
        final Button selectMediaBtn = new Button(getTopActivity());
        selectMediaBtn.setText("选择媒体文件/文件夹");
        styleMediaSelectionButton(selectMediaBtn);
        mediaLayout.addView(currentMediaTv);
        mediaLayout.addView(selectMediaBtn);

        final LinearLayout mediaOrderLayout = new LinearLayout(getTopActivity());
        mediaOrderLayout.setOrientation(LinearLayout.VERTICAL);
        mediaOrderLayout.setPadding(0, 0, 0, 16);
        final ListView mediaListView = new ListView(getTopActivity());
        final ArrayList<String> displayMediaList = new ArrayList<String>();
        mediaListView.setAdapter(new ArrayAdapter<String>(getTopActivity(), android.R.layout.simple_list_item_multiple_choice, displayMediaList));
        mediaListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        setupListViewTouchForScroll(mediaListView);
        LinearLayout.LayoutParams mediaListParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(50));
        mediaListView.setLayoutParams(mediaListParams);
        mediaOrderLayout.addView(mediaListView);
        TextView orderPrompt = createPromptText("选中媒体后，使用下方按钮调整发送顺序（顺序发送，间隔自定义秒）");
        mediaOrderLayout.addView(orderPrompt);
        final LinearLayout orderButtonsLayout = new LinearLayout(getTopActivity());
        orderButtonsLayout.setOrientation(LinearLayout.HORIZONTAL);
        Button upButton = new Button(getTopActivity());
        upButton.setText("⬆ 上移");
        styleUtilityButton(upButton);
        upButton.setEnabled(false);
        Button downButton = new Button(getTopActivity());
        downButton.setText("⬇ 下移");
        styleUtilityButton(downButton);
        downButton.setEnabled(false);
        Button deleteButton = new Button(getTopActivity());
        deleteButton.setText("🗑️ 删除");
        styleUtilityButton(deleteButton);
        deleteButton.setEnabled(false);
        LinearLayout.LayoutParams orderBtnParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        upButton.setLayoutParams(orderBtnParams);
        downButton.setLayoutParams(orderBtnParams);
        deleteButton.setLayoutParams(orderBtnParams);
        orderButtonsLayout.addView(upButton);
        orderButtonsLayout.addView(downButton);
        orderButtonsLayout.addView(deleteButton);
        mediaOrderLayout.addView(orderButtonsLayout);

        // 名片和群聊复用相同的卡片UI
        final LinearLayout cardLayout = new LinearLayout(getTopActivity());
        cardLayout.setOrientation(LinearLayout.VERTICAL);
        cardLayout.setPadding(0, 0, 0, 16);
        final TextView currentCardTv = new TextView(getTopActivity());
        StringBuilder initialCardDisplay = new StringBuilder();
        String replyStrForCard = (String) rule.get("reply");
        if (!TextUtils.isEmpty(replyStrForCard)) {
            String[] wxidParts = replyStrForCard.split(";;;");
            for (int k = 0; k < wxidParts.length; k++) {
                if (!TextUtils.isEmpty(wxidParts[k].trim())) {
                    initialCardDisplay.append(getDisplayNameForWxid(wxidParts[k].trim())).append("\n");
                }
            }
        }
        currentCardTv.setText(initialCardDisplay.toString().trim().isEmpty() ? "未选择内容" : initialCardDisplay.toString().trim());
        currentCardTv.setTextSize(14);
        currentCardTv.setTextColor(Color.parseColor("#666666"));
        currentCardTv.setPadding(0, 8, 0, 0);
        final Button selectCardBtn = new Button(getTopActivity());
        selectCardBtn.setText("选择名片/群聊（多选）");
        styleMediaSelectionButton(selectCardBtn);
        cardLayout.addView(currentCardTv);
        cardLayout.addView(selectCardBtn);

        final LinearLayout cardOrderLayout = new LinearLayout(getTopActivity());
        cardOrderLayout.setOrientation(LinearLayout.VERTICAL);
        cardOrderLayout.setPadding(0, 0, 0, 16);
        final ListView cardListView = new ListView(getTopActivity());
        final ArrayList<String> displayCardList = new ArrayList<String>();
        cardListView.setAdapter(new ArrayAdapter<String>(getTopActivity(), android.R.layout.simple_list_item_multiple_choice, displayCardList));
        cardListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        setupListViewTouchForScroll(cardListView);
        LinearLayout.LayoutParams cardListParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(50));
        cardListView.setLayoutParams(cardListParams);
        cardOrderLayout.addView(cardListView);
        TextView cardOrderPrompt = createPromptText("选中项后，使用下方按钮调整发送顺序（顺序发送，间隔自定义秒）");
        cardOrderLayout.addView(cardOrderPrompt);
        final LinearLayout cardOrderButtonsLayout = new LinearLayout(getTopActivity());
        cardOrderButtonsLayout.setOrientation(LinearLayout.HORIZONTAL);
        Button cardUpButton = new Button(getTopActivity());
        cardUpButton.setText("⬆ 上移");
        styleUtilityButton(cardUpButton);
        cardUpButton.setEnabled(false);
        Button cardDownButton = new Button(getTopActivity());
        cardDownButton.setText("⬇ 下移");
        styleUtilityButton(cardDownButton);
        cardDownButton.setEnabled(false);
        Button cardDeleteButton = new Button(getTopActivity());
        cardDeleteButton.setText("🗑️ 删除");
        styleUtilityButton(cardDeleteButton);
        cardDeleteButton.setEnabled(false);
        LinearLayout.LayoutParams cardOrderBtnParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        cardUpButton.setLayoutParams(cardOrderBtnParams);
        cardDownButton.setLayoutParams(cardOrderBtnParams);
        cardDeleteButton.setLayoutParams(cardOrderBtnParams);
        cardOrderButtonsLayout.addView(cardUpButton);
        cardOrderButtonsLayout.addView(cardDownButton);
        cardOrderButtonsLayout.addView(cardDeleteButton);
        cardOrderLayout.addView(cardOrderButtonsLayout);

        Object mediaPathsObj = rule.get("mediaPaths");
        final List<String> mediaPaths = (mediaPathsObj instanceof List) ? new ArrayList<String>((List<String>) mediaPathsObj) : new ArrayList<String>();
        final Set<String> selectedMediaPaths = new HashSet<String>();
        final Runnable updateMediaList = new Runnable() {
            public void run() {
                displayMediaList.clear();
                for (int k = 0; k < mediaPaths.size(); k++) {
                    String path = mediaPaths.get(k);
                    String fileName = new File(path).getName();
                    String display = (k + 1) + ". " + (fileName.length() > 30 ? fileName.substring(0, 30) + "..." : fileName);
                    displayMediaList.add(display);
                }
                ((ArrayAdapter<String>) mediaListView.getAdapter()).notifyDataSetChanged();
                mediaListView.clearChoices();
                mediaListView.requestLayout();
                StringBuilder mediaDisplay = new StringBuilder();
                for (String path : mediaPaths) {
                    mediaDisplay.append(new File(path).getName()).append("\n");
                }
                currentMediaTv.setText(mediaDisplay.toString().trim().isEmpty() ? "未选择媒体" : mediaDisplay.toString().trim());
                rule.put("mediaPaths", new ArrayList<String>(mediaPaths));
                adjustListViewHeight(mediaListView, mediaPaths.size());
                for (int k = 0; k < mediaPaths.size(); k++) {
                    if (selectedMediaPaths.contains(mediaPaths.get(k))) {
                        mediaListView.setItemChecked(k, true);
                    }
                }
                updateOrderButtons(mediaListView, orderButtonsLayout, mediaPaths.size(), upButton, downButton, deleteButton);
            }
        };
        final List<String> cardWxids = new ArrayList<String>();
        String replyStrForCard = (String) rule.get("reply");
        if (!TextUtils.isEmpty(replyStrForCard)) {
            String[] wxidParts = replyStrForCard.split(";;;");
            for (int k = 0; k < wxidParts.length; k++) {
                String wxid = wxidParts[k].trim();
                if (!TextUtils.isEmpty(wxid)) cardWxids.add(wxid);
            }
        }
        final Set<String> selectedCardWxids = new HashSet<String>();
        final Runnable updateCardList = new Runnable() {
            public void run() {
                displayCardList.clear();
                for (int k = 0; k < cardWxids.size(); k++) {
                    String wxid = cardWxids.get(k);
                    String name = getDisplayNameForWxid(wxid);
                    String display = (k + 1) + ". " + (name.length() > 30 ? name.substring(0, 30) + "..." : name);
                    displayCardList.add(display);
                }
                ((ArrayAdapter<String>) cardListView.getAdapter()).notifyDataSetChanged();
                cardListView.clearChoices();
                cardListView.requestLayout();
                StringBuilder cardDisplay = new StringBuilder();
                for (String wxid : cardWxids) {
                    cardDisplay.append(getDisplayNameForWxid(wxid)).append("\n");
                }
                currentCardTv.setText(cardDisplay.toString().trim().isEmpty() ? "未选择内容" : cardDisplay.toString().trim());
                rule.put("reply", TextUtils.join(";;;", cardWxids));
                adjustListViewHeight(cardListView, cardWxids.size());
                for (int k = 0; k < cardWxids.size(); k++) {
                    if (selectedCardWxids.contains(cardWxids.get(k))) {
                        cardListView.setItemChecked(k, true);
                    }
                }
                updateOrderButtons(cardListView, cardOrderButtonsLayout, cardWxids.size(), cardUpButton, cardDownButton, cardDeleteButton);
            }
        };
        updateMediaList.run();
        updateCardList.run();

        int initialReplyType = (Integer) rule.get("replyType");
        String initialExtFilter = "";
        boolean initialIsFolder = false;
        boolean initialAllowFolder = false;
        boolean initialIsMulti = false;
        switch (initialReplyType) {
            case REPLY_TYPE_IMAGE:
            case REPLY_TYPE_EMOJI:
            case REPLY_TYPE_VIDEO:
            case REPLY_TYPE_FILE:
                initialIsMulti = true;
                break;
            case REPLY_TYPE_VOICE_FILE_LIST:
                initialIsMulti = true;
                break;
            case REPLY_TYPE_VOICE_FOLDER:
                initialIsFolder = true;
                initialAllowFolder = true;
                initialIsMulti = false;
                break;
        }
        Object[] initialTag = new Object[]{initialExtFilter, initialIsFolder, initialAllowFolder, initialIsMulti};
        selectMediaBtn.setTag(initialTag);

        final Runnable updateReplyInputVisibility = new Runnable() {
            public void run() {
                int type = (Integer) rule.get("replyType");
                boolean isTextType = (type == REPLY_TYPE_TEXT);
                boolean isCardOrGroupType = (type == REPLY_TYPE_CARD || type == REPLY_TYPE_INVITE_GROUP);
                boolean isMediaType = !isTextType && !isCardOrGroupType && (type != REPLY_TYPE_XIAOZHI_AI && type != REPLY_TYPE_ZHILIA_AI);

                replyContentLabel.setVisibility(isTextType ? View.VISIBLE : View.GONE);
                replyEdit.setVisibility(isTextType ? View.VISIBLE : View.GONE);
                helpCard.setVisibility(isTextType ? View.VISIBLE : View.GONE); // 变量面板仅文本模式可见

                mediaDelayLabel.setVisibility(isMediaType || isCardOrGroupType ? View.VISIBLE : View.GONE);
                mediaDelayEdit.setVisibility(isMediaType || isCardOrGroupType ? View.VISIBLE : View.GONE);
                mediaLayout.setVisibility(isMediaType ? View.VISIBLE : View.GONE);
                mediaOrderLayout.setVisibility(isMediaType ? View.VISIBLE : View.GONE);
                cardLayout.setVisibility(isCardOrGroupType ? View.VISIBLE : View.GONE);
                cardOrderLayout.setVisibility(isCardOrGroupType ? View.VISIBLE : View.GONE);

                final LinearLayout replyAsQuoteSwitchRow = (LinearLayout) layout.findViewWithTag("replyAsQuoteSwitchRow");
                if (replyAsQuoteSwitchRow != null) {
                    replyAsQuoteSwitchRow.setVisibility(type == REPLY_TYPE_TEXT ? View.VISIBLE : View.GONE);
                }
                final TextView quotePrompt = (TextView) layout.findViewWithTag("quotePrompt");
                if (quotePrompt != null) {
                    quotePrompt.setVisibility(type == REPLY_TYPE_TEXT ? View.VISIBLE : View.GONE);
                }

                if (type == REPLY_TYPE_CARD) {
                    replyContentLabel.setText("名片 Wxid 列表:");
                    replyEdit.setHint("输入要分享的名片的Wxid（多选用;;;分隔）");
                    selectCardBtn.setText("选择名片好友（多选）");
                } else if (type == REPLY_TYPE_INVITE_GROUP) {
                    replyContentLabel.setText("群聊 ID 列表:");
                    replyEdit.setHint("输入要邀请的群聊ID（多选用;;;分隔）");
                    selectCardBtn.setText("选择要邀请的群聊（多选）");
                } else if (type == REPLY_TYPE_XIAOZHI_AI || type == REPLY_TYPE_ZHILIA_AI) {
                    replyContentLabel.setVisibility(View.GONE);
                    replyEdit.setVisibility(View.GONE);
                    mediaLayout.setVisibility(View.GONE);
                    mediaOrderLayout.setVisibility(View.GONE);
                    mediaDelayLabel.setVisibility(View.GONE);
                    mediaDelayEdit.setVisibility(View.GONE);
                    cardLayout.setVisibility(View.GONE);
                    cardOrderLayout.setVisibility(View.GONE);
                } else {
                    replyContentLabel.setText("回复内容:");
                    replyEdit.setHint("输入自动回复内容...");
                }

                String btnText = "选择媒体文件/文件夹";
                String extFilter = "";
                boolean isFolder = false;
                boolean allowFolder = false;
                final boolean isMulti = (type == REPLY_TYPE_IMAGE || type == REPLY_TYPE_EMOJI || type == REPLY_TYPE_VIDEO || type == REPLY_TYPE_FILE || type == REPLY_TYPE_VOICE_FILE_LIST);
                switch (type) {
                    case REPLY_TYPE_IMAGE:
                        extFilter = "";
                        btnText = "选择图片文件（多选）";
                        break;
                    case REPLY_TYPE_EMOJI:
                        extFilter = "";
                        btnText = "选择表情文件（多选）";
                        break;
                    case REPLY_TYPE_VIDEO:
                        extFilter = "";
                        btnText = "选择视频文件（多选）";
                        break;
                    case REPLY_TYPE_FILE:
                        extFilter = "";
                        btnText = "选择文件（多选）";
                        break;
                    case REPLY_TYPE_VOICE_FILE_LIST:
                        extFilter = "";
                        btnText = "选择语音文件列表（多选）";
                        break;
                    case REPLY_TYPE_VOICE_FOLDER:
                        isFolder = true;
                        allowFolder = true;
                        btnText = "选择语音文件夹";
                        break;
                }
                selectMediaBtn.setText(btnText);
                Object[] tag = new Object[]{extFilter, isFolder, allowFolder, isMulti};
                selectMediaBtn.setTag(tag);

                StringBuilder display = new StringBuilder();
                if (mediaPaths != null) {
                    for (int i = 0; i < mediaPaths.size(); i++) {
                        String p = mediaPaths.get(i);
                        display.append(new File(p).getName()).append("\n");
                    }
                }
                currentMediaTv.setText(display.toString().trim());
            }
        };

        int currentReplyType = (Integer) rule.get("replyType");
        switch(currentReplyType) {
            case REPLY_TYPE_XIAOZHI_AI: replyTypeGroup.check(replyTypeXiaozhiAIRadio.getId()); break;
            case REPLY_TYPE_ZHILIA_AI: replyTypeGroup.check(replyTypeZhiliaAIRadio.getId()); break;
            case REPLY_TYPE_IMAGE: replyTypeGroup.check(replyTypeImageRadio.getId()); break;
            case REPLY_TYPE_EMOJI: replyTypeGroup.check(replyTypeEmojiRadio.getId()); break;
            case REPLY_TYPE_VIDEO: replyTypeGroup.check(replyTypeVideoRadio.getId()); break;
            case REPLY_TYPE_CARD: replyTypeGroup.check(replyTypeCardRadio.getId()); break;
            case REPLY_TYPE_VOICE_FILE_LIST: replyTypeGroup.check(replyTypeVoiceFileListRadio.getId()); break;
            case REPLY_TYPE_VOICE_FOLDER: replyTypeGroup.check(replyTypeVoiceFolderRadio.getId()); break;
            case REPLY_TYPE_FILE: replyTypeGroup.check(replyTypeFileRadio.getId()); break;
            case REPLY_TYPE_INVITE_GROUP: replyTypeGroup.check(replyTypeInviteGroupRadio.getId()); break;
            default: replyTypeGroup.check(replyTypeTextRadio.getId());
        }
        updateReplyInputVisibility.run();

        layout.addView(replyContentLabel);
        layout.addView(replyEdit);
        layout.addView(helpCard); // 将变量卡片添加进主视图
        layout.addView(mediaDelayLabel);
        layout.addView(mediaDelayEdit);
        layout.addView(mediaLayout);
        layout.addView(mediaOrderLayout);
        layout.addView(cardLayout);
        layout.addView(cardOrderLayout);

        replyTypeGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == replyTypeXiaozhiAIRadio.getId()) rule.put("replyType", REPLY_TYPE_XIAOZHI_AI);
                else if (checkedId == replyTypeZhiliaAIRadio.getId()) rule.put("replyType", REPLY_TYPE_ZHILIA_AI);
                else if (checkedId == replyTypeTextRadio.getId()) rule.put("replyType", REPLY_TYPE_TEXT);
                else if (checkedId == replyTypeImageRadio.getId()) rule.put("replyType", REPLY_TYPE_IMAGE);
                else if (checkedId == replyTypeEmojiRadio.getId()) rule.put("replyType", REPLY_TYPE_EMOJI);
                else if (checkedId == replyTypeVideoRadio.getId()) rule.put("replyType", REPLY_TYPE_VIDEO);
                else if (checkedId == replyTypeCardRadio.getId()) rule.put("replyType", REPLY_TYPE_CARD);
                else if (checkedId == replyTypeVoiceFileListRadio.getId()) rule.put("replyType", REPLY_TYPE_VOICE_FILE_LIST);
                else if (checkedId == replyTypeVoiceFolderRadio.getId()) rule.put("replyType", REPLY_TYPE_VOICE_FOLDER);
                else if (checkedId == replyTypeFileRadio.getId()) rule.put("replyType", REPLY_TYPE_FILE);
                else if (checkedId == replyTypeInviteGroupRadio.getId()) rule.put("replyType", REPLY_TYPE_INVITE_GROUP);

                final LinearLayout replyAsQuoteSwitchRow = (LinearLayout) layout.findViewWithTag("replyAsQuoteSwitchRow");
                if (replyAsQuoteSwitchRow != null) {
                    replyAsQuoteSwitchRow.setVisibility((Integer) rule.get("replyType") == REPLY_TYPE_TEXT ? View.VISIBLE : View.GONE);
                }
                final TextView quotePrompt = (TextView) layout.findViewWithTag("quotePrompt");
                if (quotePrompt != null) {
                    quotePrompt.setVisibility((Integer) rule.get("replyType") == REPLY_TYPE_TEXT ? View.VISIBLE : View.GONE);
                }
                if ((Integer) rule.get("replyType") != REPLY_TYPE_TEXT) {
                    final CheckBox quoteCheckBox = (CheckBox) ((replyAsQuoteSwitchRow != null) ? replyAsQuoteSwitchRow.getChildAt(1) : null);
                    if (quoteCheckBox != null) {
                        quoteCheckBox.setChecked(false);
                    }
                }
                updateReplyInputVisibility.run();
            }
        });

        selectMediaBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Object[] tag = (Object[]) selectMediaBtn.getTag();
                String extFilter = (String) tag[0];
                boolean isFolder = (Boolean) tag[1];
                boolean allowFolder = (Boolean) tag[2];
                boolean isMulti = (Boolean) tag[3];
                String current = "";
                List mediaPathsCurrent = (List) rule.get("mediaPaths");
                if (mediaPathsCurrent != null && !mediaPathsCurrent.isEmpty()) {
                    current = TextUtils.join(";;;", mediaPathsCurrent);
                }
                File lastFolder = new File(getString(DEFAULT_LAST_FOLDER_SP_AUTO, ROOT_FOLDER));
                if (isFolder) {
                    browseFolderForSelectionAuto(lastFolder, "", current, new MediaSelectionCallback() {
                        public void onSelected(ArrayList<String> selectedFiles) {
                            if (selectedFiles.size() == 1) {
                                String path = selectedFiles.get(0);
                                File f = new File(path);
                                if (f.isDirectory()) {
                                    mediaPaths.clear();
                                    mediaPaths.add(path);
                                    StringBuilder display = new StringBuilder();
                                    display.append(new File(path).getName());
                                    currentMediaTv.setText(display.toString());
                                    updateMediaList.run();
                                } else {
                                    toast("请选择文件夹");
                                }
                            }
                        }
                    }, allowFolder);
                } else {
                    browseFolderForSelectionAuto(lastFolder, extFilter, current, new MediaSelectionCallback() {
                        public void onSelected(ArrayList<String> selectedFiles) {
                            if (selectedFiles.isEmpty()) {
                                toast("未选择任何文件");
                                return;
                            }
                            mediaPaths.clear();
                            if (isMulti) {
                                mediaPaths.addAll(selectedFiles);
                                StringBuilder display = new StringBuilder();
                                for (int i = 0; i < selectedFiles.size(); i++) {
                                    String p = selectedFiles.get(i);
                                    display.append(new File(p).getName()).append("\n");
                                }
                                currentMediaTv.setText(display.toString().trim());
                            } else {
                                if (!selectedFiles.isEmpty()) {
                                    mediaPaths.add(selectedFiles.get(0));
                                    currentMediaTv.setText(new File(selectedFiles.get(0)).getName());
                                }
                            }
                            updateMediaList.run();
                        }
                    }, allowFolder);
                }
            }
        });

        selectCardBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                int type = (Integer) rule.get("replyType");
                if (type == REPLY_TYPE_INVITE_GROUP) {
                    showLoadingDialog("选择群聊", "  正在加载群聊列表...", new Runnable() {
                        public void run() {
                            if (sCachedGroupList == null) sCachedGroupList = getGroupList();
                            new Handler(Looper.getMainLooper()).post(new Runnable() {
                                public void run() {
                                    if (sCachedGroupList == null || sCachedGroupList.isEmpty()) {
                                        toast("未获取到群聊列表"); return;
                                    }
                                    List names = new ArrayList();
                                    List ids = new ArrayList();
                                    for (int i = 0; i < sCachedGroupList.size(); i++) {
                                        GroupInfo groupInfo = (GroupInfo) sCachedGroupList.get(i);
                                        String groupName = TextUtils.isEmpty(groupInfo.getName()) ? "未知群聊" : groupInfo.getName();
                                        names.add("🏠 " + groupName + "\nID: " + groupInfo.getRoomId());
                                        ids.add(groupInfo.getRoomId());
                                    }
                                    final Set<String> tempSelected = new HashSet<String>(cardWxids);
                                    showMultiSelectDialog("✨ 选择要邀请的群聊 ✨", names, ids, tempSelected, "🔍 搜索群聊...", new Runnable() {
                                        public void run() {
                                            cardWxids.clear();
                                            cardWxids.addAll(tempSelected);
                                            updateCardList.run();
                                        }
                                    }, null);
                                }
                            });
                        }
                    });
                } else {
                    showLoadingDialog("选择名片好友", "  正在加载好友列表...", new Runnable() {
                        public void run() {
                            if (sCachedFriendList == null) sCachedFriendList = getFriendList();
                            new Handler(Looper.getMainLooper()).post(new Runnable() {
                                public void run() {
                                    if (sCachedFriendList == null || sCachedFriendList.isEmpty()) {
                                        toast("未获取到好友列表");
                                        return;
                                    }
                                    List names = new ArrayList();
                                    List ids = new ArrayList();
                                    for (int i = 0; i < sCachedFriendList.size(); i++) {
                                        FriendInfo friendInfo = (FriendInfo) sCachedFriendList.get(i);
                                        String nickname = TextUtils.isEmpty(friendInfo.getNickname()) ? "未知昵称" : friendInfo.getNickname();
                                        String remark = friendInfo.getRemark();
                                        String displayName = !TextUtils.isEmpty(remark) ? nickname + " (" + remark + ")" : nickname;
                                        names.add("👤 " + displayName + "\nID: " + friendInfo.getWxid());
                                        ids.add(friendInfo.getWxid());
                                    }
                                    final Set<String> tempSelectedWxids = new HashSet<String>(cardWxids);
                                    showMultiSelectDialog("✨ 选择名片好友 ✨", names, ids, tempSelectedWxids, "🔍 搜索好友(昵称/备注)...", new Runnable() {
                                        public void run() {
                                            cardWxids.clear();
                                            cardWxids.addAll(tempSelectedWxids);
                                            updateCardList.run();
                                        }
                                    }, null);
                                }
                            });
                        }
                    });
                }
            }
        });

        mediaListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String path = mediaPaths.get(position);
                if (mediaListView.isItemChecked(position)) {
                    selectedMediaPaths.add(path);
                } else {
                    selectedMediaPaths.remove(path);
                }
                updateOrderButtons(mediaListView, orderButtonsLayout, mediaPaths.size(), upButton, downButton, deleteButton);
            }
        });
        upButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (selectedMediaPaths.size() == 1) {
                    String selectedPath = selectedMediaPaths.iterator().next();
                    int pos = mediaPaths.indexOf(selectedPath);
                    if (pos > 0) {
                        Collections.swap(mediaPaths, pos, pos - 1);
                        updateMediaList.run();
                    }
                }
            }
        });
        downButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (selectedMediaPaths.size() == 1) {
                    String selectedPath = selectedMediaPaths.iterator().next();
                    int pos = mediaPaths.indexOf(selectedPath);
                    if (pos < mediaPaths.size() - 1) {
                        Collections.swap(mediaPaths, pos, pos + 1);
                        updateMediaList.run();
                    }
                }
            }
        });
        deleteButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!selectedMediaPaths.isEmpty()) {
                    mediaPaths.removeAll(selectedMediaPaths);
                    selectedMediaPaths.clear();
                    updateMediaList.run();
                }
            }
        });

        cardListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String wxid = cardWxids.get(position);
                if (cardListView.isItemChecked(position)) {
                    selectedCardWxids.add(wxid);
                } else {
                    selectedCardWxids.remove(wxid);
                }
                updateOrderButtons(cardListView, cardOrderButtonsLayout, cardWxids.size(), cardUpButton, cardDownButton, cardDeleteButton);
            }
        });
        cardUpButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (selectedCardWxids.size() == 1) {
                    String selectedWxid = selectedCardWxids.iterator().next();
                    int pos = cardWxids.indexOf(selectedWxid);
                    if (pos > 0) {
                        Collections.swap(cardWxids, pos, pos - 1);
                        updateCardList.run();
                    }
                }
            }
        });
        cardDownButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (selectedCardWxids.size() == 1) {
                    String selectedWxid = selectedCardWxids.iterator().next();
                    int pos = cardWxids.indexOf(selectedWxid);
                    if (pos < cardWxids.size() - 1) {
                        Collections.swap(cardWxids, pos, pos + 1);
                        updateCardList.run();
                    }
                }
            }
        });
        cardDeleteButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!selectedCardWxids.isEmpty()) {
                    cardWxids.removeAll(selectedCardWxids);
                    selectedCardWxids.clear();
                    updateCardList.run();
                }
            }
        });

        final LinearLayout replyAsQuoteSwitchRow = createSwitchRow(getTopActivity(), "引用原消息回复", (Boolean) rule.get("replyAsQuote"), new View.OnClickListener() {
            public void onClick(View v) {}
        });
        replyAsQuoteSwitchRow.setTag("replyAsQuoteSwitchRow");
        TextView quotePrompt = createPromptText("⚠️ 勾选后将引用原消息回复");
        quotePrompt.setTag("quotePrompt");
        layout.addView(replyAsQuoteSwitchRow);
        layout.addView(quotePrompt);

        LinearLayout matchCard = createCardLayout();
        matchCard.addView(createSectionTitle("匹配方式"));
        final RadioGroup matchTypeGroup = createRadioGroup(getTopActivity(), LinearLayout.HORIZONTAL);
        final RadioButton partialMatchRadio = createRadioButton(getTopActivity(), "模糊");
        final RadioButton fullMatchRadio = createRadioButton(getTopActivity(), "全字");
        final RadioButton regexMatchRadio = createRadioButton(getTopActivity(), "正则");
        final RadioButton anyMatchRadio = createRadioButton(getTopActivity(), "任何消息");
        matchTypeGroup.addView(partialMatchRadio);
        matchTypeGroup.addView(fullMatchRadio);
        matchTypeGroup.addView(regexMatchRadio);
        matchTypeGroup.addView(anyMatchRadio);
        matchCard.addView(matchTypeGroup);
        layout.addView(matchCard);

        matchTypeGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == anyMatchRadio.getId()) {
                    keywordEdit.setEnabled(false);
                    keywordEdit.setText("");
                    keywordEdit.setHint("已禁用（匹配任何消息）");
                } else {
                    keywordEdit.setEnabled(true);
                    keywordEdit.setHint("输入触发关键词...");
                }
            }
        });

        int currentMatchType = (Integer) rule.get("matchType");
        if (currentMatchType == MATCH_TYPE_EXACT) matchTypeGroup.check(fullMatchRadio.getId());
        else if (currentMatchType == MATCH_TYPE_REGEX) matchTypeGroup.check(regexMatchRadio.getId());
        else if (currentMatchType == MATCH_TYPE_ANY) {
            matchTypeGroup.check(anyMatchRadio.getId());
            keywordEdit.setEnabled(false);
            keywordEdit.setText("");
            keywordEdit.setHint("已禁用（匹配任何消息）");
        } else matchTypeGroup.check(partialMatchRadio.getId());

        LinearLayout atCard = createCardLayout();
        atCard.addView(createSectionTitle("@触发"));
        final RadioGroup atTriggerGroup = createRadioGroup(getTopActivity(), LinearLayout.HORIZONTAL);
        final RadioButton atTriggerNoneRadio = createRadioButton(getTopActivity(), "不限");
        final RadioButton atTriggerMeRadio = createRadioButton(getTopActivity(), "@我");
        final RadioButton atTriggerAllRadio = createRadioButton(getTopActivity(), "@全体");
        atTriggerGroup.addView(atTriggerNoneRadio);
        atTriggerGroup.addView(atTriggerMeRadio);
        atTriggerGroup.addView(atTriggerAllRadio);
        int currentAtTriggerType = (Integer) rule.get("atTriggerType");
        if (currentAtTriggerType == AT_TRIGGER_ME) atTriggerGroup.check(atTriggerMeRadio.getId());
        else if (currentAtTriggerType == AT_TRIGGER_ALL) atTriggerGroup.check(atTriggerAllRadio.getId());
        else atTriggerGroup.check(atTriggerNoneRadio.getId());
        atCard.addView(atTriggerGroup);
        layout.addView(atCard);

        LinearLayout patCard = createCardLayout();
        patCard.addView(createSectionTitle("拍一拍触发"));
        final RadioGroup patTriggerGroup = createRadioGroup(getTopActivity(), LinearLayout.HORIZONTAL);
        final RadioButton patTriggerNoneRadio = createRadioButton(getTopActivity(), "不限");
        final RadioButton patTriggerMeRadio = createRadioButton(getTopActivity(), "被拍一拍");
        patTriggerGroup.addView(patTriggerNoneRadio);
        patTriggerGroup.addView(patTriggerMeRadio);
        int currentPatTriggerType = (Integer) rule.get("patTriggerType");
        if (currentPatTriggerType == PAT_TRIGGER_ME) patTriggerGroup.check(patTriggerMeRadio.getId());
        else patTriggerGroup.check(patTriggerNoneRadio.getId());
        patCard.addView(patTriggerGroup);
        layout.addView(patCard);

        LinearLayout delayCard = createCardLayout();
        delayCard.addView(createSectionTitle("延迟回复 (秒)"));
        final EditText delayEdit = createStyledEditText("输入延迟秒数 (0为立即回复)", String.valueOf(rule.get("delaySeconds")));
        delayEdit.setInputType(InputType.TYPE_CLASS_NUMBER);
        delayCard.addView(delayEdit);
        layout.addView(delayCard);

        LinearLayout timeCard = createCardLayout();
        timeCard.addView(createSectionTitle("生效时间段 (留空则不限制)"));
        LinearLayout timeLayout = new LinearLayout(getTopActivity());
        timeLayout.setOrientation(LinearLayout.HORIZONTAL);
        timeLayout.setGravity(Gravity.CENTER_VERTICAL);
        final EditText startTimeEdit = createStyledEditText("开始 HH:mm", (String) rule.get("startTime"));
        startTimeEdit.setFocusable(false);
        LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        startParams.setMargins(0, 8, 4, 16);
        startTimeEdit.setLayoutParams(startParams);
        final EditText endTimeEdit = createStyledEditText("结束 HH:mm", (String) rule.get("endTime"));
        endTimeEdit.setFocusable(false);
        LinearLayout.LayoutParams endParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        endParams.setMargins(4, 8, 0, 16);
        endTimeEdit.setLayoutParams(endParams);
        startTimeEdit.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showTimePickerDialog(startTimeEdit); } });
        endTimeEdit.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showTimePickerDialog(endTimeEdit); } });
        timeLayout.addView(startTimeEdit);
        TextView dashText = new TextView(getTopActivity());
        dashText.setText("  -  ");
        dashText.setTextSize(16);
        LinearLayout.LayoutParams dashParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dashText.setLayoutParams(dashParams);
        timeLayout.addView(dashText);
        timeLayout.addView(endTimeEdit);
        timeCard.addView(timeLayout);
        layout.addView(timeCard);

        LinearLayout targetCard = createCardLayout();
        targetCard.addView(createSectionTitle("生效目标"));
        final RadioGroup targetTypeGroup = createRadioGroup(getTopActivity(), LinearLayout.HORIZONTAL);
        final RadioButton targetTypeNoneRadio = createRadioButton(getTopActivity(), "不指定");
        final RadioButton targetTypeBothRadio = createRadioButton(getTopActivity(), "好友和群聊");
        targetTypeGroup.addView(targetTypeNoneRadio);
        targetTypeGroup.addView(targetTypeBothRadio);
        targetCard.addView(targetTypeGroup);
        layout.addView(targetCard);

        final Button selectIncludeGroupMembersButton = new Button(getTopActivity());
        selectIncludeGroupMembersButton.setPadding(0, 20, 0, 0);
        layout.addView(selectIncludeGroupMembersButton);

        final Button selectExcludeGroupMembersButton = new Button(getTopActivity());
        selectExcludeGroupMembersButton.setPadding(0, 20, 0, 0);
        layout.addView(selectExcludeGroupMembersButton);

        final Button selectFriendsButton = new Button(getTopActivity());
        selectFriendsButton.setPadding(0, 20, 0, 0);
        layout.addView(selectFriendsButton);
        final Button selectGroupsButton = new Button(getTopActivity());
        selectGroupsButton.setPadding(0, 20, 0, 0);
        layout.addView(selectGroupsButton);
        final Button selectExcludeFriendsButton = new Button(getTopActivity());
        selectExcludeFriendsButton.setPadding(0, 20, 0, 0);
        layout.addView(selectExcludeFriendsButton);
        final Button selectExcludeGroupsButton = new Button(getTopActivity());
        selectExcludeGroupsButton.setPadding(0, 20, 0, 0);
        layout.addView(selectExcludeGroupsButton);

        final Runnable updateSelectTargetsButton = new Runnable() {
            public void run() {
                int targetType = (Integer) rule.get("targetType");

                selectIncludeGroupMembersButton.setVisibility(View.GONE);
                selectExcludeGroupMembersButton.setVisibility(View.GONE);
                selectFriendsButton.setVisibility(View.GONE);
                selectGroupsButton.setVisibility(View.GONE);
                selectExcludeFriendsButton.setVisibility(View.GONE);
                selectExcludeGroupsButton.setVisibility(View.GONE);

                if (targetType == TARGET_TYPE_BOTH) {
                    // 好友和群聊模式：显示指定功能，隐藏排除功能
                    Set targetWxids = (Set) rule.get("targetWxids");
                    selectFriendsButton.setText("👤 指定生效好友 (" + getFriendCountInTargetWxids(targetWxids) + "人)");
                    styleUtilityButton(selectFriendsButton);
                    selectFriendsButton.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showSelectTargetFriendsDialog(targetWxids, updateSelectTargetsButton); } });
                    selectFriendsButton.setVisibility(View.VISIBLE);

                    selectGroupsButton.setText("🏠 指定生效群聊 (" + getGroupCountInTargetWxids(targetWxids) + "个)");
                    styleUtilityButton(selectGroupsButton);
                    selectGroupsButton.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showSelectTargetGroupsDialog(targetWxids, updateSelectTargetsButton); } });
                    selectGroupsButton.setVisibility(View.VISIBLE);

                    final Set inG = (Set) rule.get("includedGroupIdsForMemberFilter");
                    final Set inM = (Set) rule.get("includedGroupMemberWxids");
                    selectIncludeGroupMembersButton.setText("👥 指定群聊成员生效 (" + (inM != null ? inM.size() : 0) + "人)");
                    styleUtilityButton(selectIncludeGroupMembersButton);
                    selectIncludeGroupMembersButton.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) {
                            Set gSet = (Set) rule.get("includedGroupIdsForMemberFilter");
                            if (gSet == null) gSet = new HashSet();
                            Set mSet = (Set) rule.get("includedGroupMemberWxids");
                            if (mSet == null) mSet = new HashSet();
                            final Set fg = gSet;
                            final Set fm = mSet;
                            showSelectGroupThenMembersSimpleDialog("选择成员", fg, fm, new Runnable() {
                                public void run() {
                                    rule.put("includedGroupIdsForMemberFilter", fg);
                                    rule.put("includedGroupMemberWxids", fm);
                                    updateSelectTargetsButton.run();
                                }
                            });
                        }
                    });
                    selectIncludeGroupMembersButton.setVisibility(View.VISIBLE);

                    // 清空排除相关数据
                    rule.put("excludedGroupIdsForMemberFilter", new HashSet());
                    rule.put("excludedGroupMemberWxids", new HashSet());
                    rule.put("excludedWxids", new HashSet());

                } else {
                    // 不指定模式：显示排除功能
                    rule.put("targetWxids", new HashSet());

                    final Set exG = (Set) rule.get("excludedGroupIdsForMemberFilter");
                    final Set exM = (Set) rule.get("excludedGroupMemberWxids");
                    selectExcludeGroupMembersButton.setText("👥 排除群聊成员 (" + (exM != null ? exM.size() : 0) + "人)");
                    styleUtilityButton(selectExcludeGroupMembersButton);
                    selectExcludeGroupMembersButton.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) {
                            Set gSet = (Set) rule.get("excludedGroupIdsForMemberFilter");
                            if (gSet == null) gSet = new HashSet();
                            Set mSet = (Set) rule.get("excludedGroupMemberWxids");
                            if (mSet == null) mSet = new HashSet();
                            final Set fg = gSet;
                            final Set fm = mSet;
                            showSelectGroupThenMembersSimpleDialog("选择成员", fg, fm, new Runnable() {
                                public void run() {
                                    rule.put("excludedGroupIdsForMemberFilter", fg);
                                    rule.put("excludedGroupMemberWxids", fm);
                                    updateSelectTargetsButton.run();
                                }
                            });
                        }
                    });
                    selectExcludeGroupMembersButton.setVisibility(View.VISIBLE);

                    // 显示排除好友和排除群聊
                    final Set excludedWxids = (Set) rule.get("excludedWxids");
                    selectExcludeFriendsButton.setText("👤 排除好友 (" + getFriendCountInTargetWxids(excludedWxids) + "人)");
                    styleUtilityButton(selectExcludeFriendsButton);
                    selectExcludeFriendsButton.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showSelectExcludeFriendsDialog(excludedWxids, updateSelectTargetsButton); } });
                    selectExcludeFriendsButton.setVisibility(View.VISIBLE);

                    selectExcludeGroupsButton.setText("🏠 排除群聊 (" + getGroupCountInTargetWxids(excludedWxids) + "个)");
                    styleUtilityButton(selectExcludeGroupsButton);
                    selectExcludeGroupsButton.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showSelectExcludeGroupsDialog(excludedWxids, updateSelectTargetsButton); } });
                    selectExcludeGroupsButton.setVisibility(View.VISIBLE);

                    rule.put("includedGroupIdsForMemberFilter", new HashSet());
                    rule.put("includedGroupMemberWxids", new HashSet());
                }
            }
        };

        targetTypeGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                rule.put("targetType", (checkedId == targetTypeBothRadio.getId()) ? TARGET_TYPE_BOTH : TARGET_TYPE_NONE);
                updateSelectTargetsButton.run();
            }
        });

        int currentTargetType = (Integer) rule.get("targetType");
        if (currentTargetType == TARGET_TYPE_BOTH) targetTypeGroup.check(targetTypeBothRadio.getId());
        else targetTypeGroup.check(targetTypeNoneRadio.getId());
        updateSelectTargetsButton.run();

        LinearLayout switchCard = createCardLayout();
        final LinearLayout enabledSwitchRow = createSwitchRow(getTopActivity(), "启用此规则", (Boolean) rule.get("enabled"), new View.OnClickListener() {
            public void onClick(View v) {}
        });
        TextView ruleEnabledPrompt = createPromptText("⚠️ 勾选后启用此规则");
        switchCard.addView(enabledSwitchRow);
        switchCard.addView(ruleEnabledPrompt);
        layout.addView(switchCard);

        String keyword = (String) rule.get("keyword");
        String dialogTitle = keyword.isEmpty() ? "➕ 添加规则" : "✏️ 编辑规则";
        String neutralButtonText = keyword.isEmpty() ? null : "🗑️ 删除";
        DialogInterface.OnClickListener neutralListener = keyword.isEmpty() ? null : new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                rules.remove(rule);
                refreshCallback.run();
                saveAutoReplyRules(rules);
                toast("规则已删除");
            }
        };

        final CheckBox enabledCheckBox = (CheckBox) enabledSwitchRow.getChildAt(1);
        final CheckBox quoteCheckBox = (CheckBox) replyAsQuoteSwitchRow.getChildAt(1);

        final AlertDialog dialog = buildCommonAlertDialog(getTopActivity(), dialogTitle, scrollView, "✅ 保存", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                String keyword = keywordEdit.getText().toString().trim();
                String reply = replyEdit.getText().toString().trim();

                int matchType;
                if (matchTypeGroup.getCheckedRadioButtonId() == fullMatchRadio.getId()) matchType = MATCH_TYPE_EXACT;
                else if (matchTypeGroup.getCheckedRadioButtonId() == regexMatchRadio.getId()) matchType = MATCH_TYPE_REGEX;
                else if (matchTypeGroup.getCheckedRadioButtonId() == anyMatchRadio.getId()) matchType = MATCH_TYPE_ANY;
                else matchType = MATCH_TYPE_FUZZY;

                if (matchType == MATCH_TYPE_ANY) keyword = "";
                else if (keyword.isEmpty()) { toast("关键词不能为空"); return; }

                int replyType = (Integer) rule.get("replyType");
                if (replyType == REPLY_TYPE_TEXT) {
                    if (reply.isEmpty()) { toast("内容不能为空"); return; }
                    rule.put("reply", reply);
                } else if (replyType == REPLY_TYPE_CARD || replyType == REPLY_TYPE_INVITE_GROUP) {
                    rule.put("reply", TextUtils.join(";;;", cardWxids));
                    if (cardWxids.isEmpty()) { toast(replyType == REPLY_TYPE_CARD ? "名片Wxid不能为空" : "群聊ID不能为空"); return; }
                } else if (replyType != REPLY_TYPE_XIAOZHI_AI && replyType != REPLY_TYPE_ZHILIA_AI) {
                    if (mediaPaths.isEmpty()) { toast("媒体文件路径不能为空"); return; }
                    for (String path : mediaPaths) {
                        File file = new File(path);
                        if (replyType == REPLY_TYPE_VOICE_FOLDER) {
                            if (!file.exists() || !file.isDirectory()) { toast("指定的语音文件夹无效或不存在！"); return; }
                        } else if (replyType == REPLY_TYPE_FILE) {
                            if (!file.exists() || !file.isFile()) { toast("指定的文件无效或不存在！"); return; }
                        } else {
                            if (!file.exists() || !file.isFile()) { toast("指定的媒体文件无效或不存在！"); return; }
                        }
                    }
                }
                String startTime = startTimeEdit.getText().toString().trim();
                String endTime = endTimeEdit.getText().toString().trim();
                if ((!startTime.isEmpty() && endTime.isEmpty()) || (startTime.isEmpty() && !endTime.isEmpty())) {
                    toast("建议同时设置开始和结束时间，否则视为单点时间（非范围）");
                }
                rule.put("keyword", keyword);
                rule.put("enabled", enabledCheckBox.isChecked());
                rule.put("matchType", matchType);

                int atTriggerType;
                if (atTriggerGroup.getCheckedRadioButtonId() == atTriggerMeRadio.getId()) atTriggerType = AT_TRIGGER_ME;
                else if (atTriggerGroup.getCheckedRadioButtonId() == atTriggerAllRadio.getId()) atTriggerType = AT_TRIGGER_ALL;
                else atTriggerType = AT_TRIGGER_NONE;
                rule.put("atTriggerType", atTriggerType);

                int patTriggerType;
                if (patTriggerGroup.getCheckedRadioButtonId() == patTriggerMeRadio.getId()) patTriggerType = PAT_TRIGGER_ME;
                else patTriggerType = PAT_TRIGGER_NONE;
                rule.put("patTriggerType", patTriggerType);

                try { rule.put("delaySeconds", Long.parseLong(delayEdit.getText().toString().trim())); }
                catch (NumberFormatException e) { rule.put("delaySeconds", 0L); }
                rule.put("replyAsQuote", quoteCheckBox.isChecked());
                rule.put("startTime", startTime);
                rule.put("endTime", endTime);
                rule.put("mediaPaths", new ArrayList<String>(mediaPaths));
                try {
                    rule.put("mediaDelaySeconds", Long.parseLong(mediaDelayEdit.getText().toString().trim()));
                } catch (NumberFormatException e) {
                    rule.put("mediaDelaySeconds", 1L);
                }
                compileRegexPatternForRule(rule);
                if (!rules.contains(rule)) rules.add(rule);
                refreshCallback.run();
                saveAutoReplyRules(rules);
                toast("规则已保存");
            }
        }, "❌ 取消", null, neutralButtonText, neutralListener);

        dialog.show();
    } catch (Exception e) {
        toast("弹窗失败: " + e.getMessage());
        e.printStackTrace();
    }
}

private int getFriendCountInTargetWxids(Set targetWxids) {
    if (targetWxids == null || targetWxids.isEmpty()) return 0;
    int count = 0;
    if (sCachedFriendList == null) sCachedFriendList = getFriendList();
    if (sCachedFriendList != null) {
        for (Object wxidObj : targetWxids) {
            String wxid = (String) wxidObj;
            for (int i = 0; i < sCachedFriendList.size(); i++) {
                if (wxid.equals(((FriendInfo) sCachedFriendList.get(i)).getWxid())) {
                    count++;
                    break;
                }
            }
        }
    }
    return count;
}

private int getGroupCountInTargetWxids(Set targetWxids) {
    if (targetWxids == null || targetWxids.isEmpty()) return 0;
    int count = 0;
    if (sCachedGroupList == null) sCachedGroupList = getGroupList();
    if (sCachedGroupList != null) {
        for (Object wxidObj : targetWxids) {
            String wxid = (String) wxidObj;
            for (int i = 0; i < sCachedGroupList.size(); i++) {
                if (wxid.equals(((GroupInfo) sCachedGroupList.get(i)).getRoomId())) {
                    count++;
                    break;
                }
            }
        }
    }
    return count;
}

private void showSelectTargetFriendsDialog(final Set currentSelectedWxids, final Runnable updateButtonCallback) {
    showLoadingDialog("👤 选择生效好友", "  正在加载好友列表...", new Runnable() {
        public void run() {
            if (sCachedFriendList == null) sCachedFriendList = getFriendList();
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                public void run() {
                    if (sCachedFriendList == null || sCachedFriendList.isEmpty()) {
                        toast("未获取到好友列表");
                        return;
                    }
                    List names = new ArrayList();
                    List ids = new ArrayList();
                    for (int i = 0; i < sCachedFriendList.size(); i++) {
                        FriendInfo friendInfo = (FriendInfo) sCachedFriendList.get(i);
                        String nickname = TextUtils.isEmpty(friendInfo.getNickname()) ? "未知昵称" : friendInfo.getNickname();
                        String remark = friendInfo.getRemark();
                        String displayName = !TextUtils.isEmpty(remark) ? nickname + " (" + remark + ")" : nickname;
                        names.add("👤 " + displayName + "\nID: " + friendInfo.getWxid());
                        ids.add(friendInfo.getWxid());
                    }
                    showMultiSelectDialog("✨ 选择生效好友 ✨", names, ids, currentSelectedWxids, "🔍 搜索好友(昵称/备注)...", updateButtonCallback, new Runnable() {
                        public void run() {
                            updateSelectAllButton((AlertDialog) null, null, null);
                        }
                    });
                }
            });
        }
    });
}

private void showSelectTargetGroupsDialog(final Set currentSelectedWxids, final Runnable updateButtonCallback) {
    showLoadingDialog("🏠 选择生效群聊", "  正在加载群聊列表...", new Runnable() {
        public void run() {
            if (sCachedGroupList == null) sCachedGroupList = getGroupList();
            if (sCachedGroupMemberCounts == null) {
                sCachedGroupMemberCounts = new HashMap();
                if (sCachedGroupList != null) {
                    for (int i = 0; i < sCachedGroupList.size(); i++) {
                        String groupId = ((GroupInfo) sCachedGroupList.get(i)).getRoomId();
                        if (groupId != null) sCachedGroupMemberCounts.put(groupId, new Integer(getGroupMemberCount(groupId)));
                    }
                }
            }
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                public void run() {
                    if (sCachedGroupList == null ||sCachedGroupList.isEmpty()) {
                        toast("未获取到群聊列表");
                        return;
                    }
                    List names = new ArrayList();
                    List ids = new ArrayList();
                    for (int i = 0; i < sCachedGroupList.size(); i++) {
                        GroupInfo groupInfo = (GroupInfo) sCachedGroupList.get(i);
                        String groupName = TextUtils.isEmpty(groupInfo.getName()) ? "未知群聊" : groupInfo.getName();
                        String groupId = groupInfo.getRoomId();
                        Integer memberCount = (Integer) sCachedGroupMemberCounts.get(groupId);
                        names.add("🏠 " + groupName + " (" + (memberCount != null ? memberCount.intValue() : 0) + "人)" + "\nID: " + groupId);
                        ids.add(groupId);
                    }
                    showMultiSelectDialog("✨ 选择生效群聊 ✨", names, ids, currentSelectedWxids, "🔍 搜索群聊...", updateButtonCallback, null);
                }
            });
        }
    });
}

private void showSelectExcludeFriendsDialog(final Set currentSelectedWxids, final Runnable updateButtonCallback) {
    showLoadingDialog("👤 选择排除好友", "  正在加载好友列表...", new Runnable() {
        public void run() {
            if (sCachedFriendList == null) sCachedFriendList = getFriendList();
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                public void run() {
                    if (sCachedFriendList == null || sCachedFriendList.isEmpty()) {
                        toast("未获取到好友列表");
                        return;
                    }
                    List names = new ArrayList();
                    List ids = new ArrayList();
                    for (int i = 0; i < sCachedFriendList.size(); i++) {
                        FriendInfo friendInfo = (FriendInfo) sCachedFriendList.get(i);
                        String nickname = TextUtils.isEmpty(friendInfo.getNickname()) ? "未知昵称" : friendInfo.getNickname();
                        String remark = friendInfo.getRemark();
                        String displayName = !TextUtils.isEmpty(remark) ? nickname + " (" + remark + ")" : nickname;
                        names.add("👤 " + displayName + "\nID: " + friendInfo.getWxid());
                        ids.add(friendInfo.getWxid());
                    }
                    showMultiSelectDialog("✨ 选择排除好友 ✨", names, ids, currentSelectedWxids, "🔍 搜索好友(昵称/备注)...", updateButtonCallback, null);
                }
            });
        }
    });
}

private void showSelectExcludeGroupsDialog(final Set currentSelectedWxids, final Runnable updateButtonCallback) {
    showLoadingDialog("🏠 选择排除群聊", "  正在加载群聊列表...", new Runnable() {
        public void run() {
            if (sCachedGroupList == null) sCachedGroupList = getGroupList();
            if (sCachedGroupMemberCounts == null) {
                sCachedGroupMemberCounts = new HashMap();
                if (sCachedGroupList != null) {
                    for (int i = 0; i < sCachedGroupList.size(); i++) {
                        String groupId = ((GroupInfo) sCachedGroupList.get(i)).getRoomId();
                        if (groupId != null) sCachedGroupMemberCounts.put(groupId, new Integer(getGroupMemberCount(groupId)));
                    }
                }
            }
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                public void run() {
                    if (sCachedGroupList == null || sCachedGroupList.isEmpty()) {
                        toast("未获取到群聊列表");
                        return;
                    }
                    List names = new ArrayList();
                    List ids = new ArrayList();
                    for (int i = 0; i < sCachedGroupList.size(); i++) {
                        GroupInfo groupInfo = (GroupInfo) sCachedGroupList.get(i);
                        String groupName = TextUtils.isEmpty(groupInfo.getName()) ? "未知群聊" : groupInfo.getName();
                        String groupId = groupInfo.getRoomId();
                        Integer memberCount = (Integer) sCachedGroupMemberCounts.get(groupId);
                        names.add("🏠 " + groupName + " (" + (memberCount != null ? memberCount.intValue() : 0) + "人)" + "\nID: " + groupId);
                        ids.add(groupId);
                    }
                    showMultiSelectDialog("✨ 选择排除群聊 ✨", names, ids, currentSelectedWxids, "🔍 搜索群聊...", updateButtonCallback, null);
                }
            });
        }
    });
}

private void showSelectGroupThenMembersSimpleDialog(final String title, final Set selectedGroupIds, final Set selectedMemberWxids, final Runnable doneCallback) {
    showLoadingDialog("选择群聊", "  正在加载群聊列表...", new Runnable() {
        public void run() {
            if (sCachedGroupList == null) sCachedGroupList = getGroupList();
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                public void run() {
                    if (sCachedGroupList == null || sCachedGroupList.isEmpty()) {
                        toast("未获取到群聊列表");
                        return;
                    }

                    List names = new ArrayList();
                    List ids = new ArrayList();
                    for (int i = 0; i < sCachedGroupList.size(); i++) {
                        GroupInfo g = (GroupInfo) sCachedGroupList.get(i);
                        String gName = TextUtils.isEmpty(g.getName()) ? "未知群聊" : g.getName();
                        names.add("🏠 " + gName + "\nID: " + g.getRoomId());
                        ids.add(g.getRoomId());
                    }

                    final Set tempGroupSet = new HashSet(selectedGroupIds);
                    showMultiSelectDialog("选择群聊", names, ids, tempGroupSet, "🔍 搜索群聊...", new Runnable() {
                        public void run() {
                            List memberNames = new ArrayList();
                            List memberIds = new ArrayList();

                            for (int i = 0; i < sCachedGroupList.size(); i++) {
                                GroupInfo g = (GroupInfo) sCachedGroupList.get(i);
                                String gid = g.getRoomId();
                                if (!tempGroupSet.contains(gid)) continue;
                                List members = getGroupMemberList(gid);
                                if (members != null) {
                                    for (int j = 0; j < members.size(); j++) {
                                        String mw = (String) members.get(j);
                                        // 群内昵称
                                        String groupNick = getFriendName(mw, gid);

                                        // 微信昵称/备注：先用全局好友显示名，拿不到再用 getFriendName(mw) 兜底（参考进退群脚本思路）
                                        String wxNick = getFriendDisplayName(mw);
                                        if (TextUtils.isEmpty(wxNick) || mw.equals(wxNick)) {
                                            String fallbackWxNick = getFriendName(mw);
                                            if (!TextUtils.isEmpty(fallbackWxNick) && !"未设置".equals(fallbackWxNick)) {
                                                wxNick = fallbackWxNick;
                                            }
                                        }

                                        if (TextUtils.isEmpty(groupNick)) groupNick = "未设置群昵称";
                                        if (TextUtils.isEmpty(wxNick)) wxNick = mw;

                                        String showName;
                                        if (groupNick.equals(wxNick)) {
                                            showName = "👤 " + groupNick + "\nID: " + mw;
                                        } else {
                                            showName = "👤 群内: " + groupNick + " | 微信: " + wxNick + "\nID: " + mw;
                                        }
                                        memberNames.add(showName);
                                        memberIds.add(gid + "|" + mw);
                                    }
                                }
                            }

                            final Set tempMemberKeys = new HashSet();
                            for (int i = 0; i < memberIds.size(); i++) {
                                String k = (String) memberIds.get(i);
                                String[] arr = k.split("\\|", 2);
                                if (arr.length == 2) {
                                    String gid = arr[0];
                                    String mw = arr[1];
                                    if (selectedGroupIds.contains(gid) && selectedMemberWxids.contains(mw)) {
                                        tempMemberKeys.add(k);
                                    }
                                }
                            }

                            showMultiSelectDialog(title, memberNames, memberIds, tempMemberKeys, "🔍 搜索成员...", new Runnable() {
                                public void run() {
                                    selectedGroupIds.clear();
                                    selectedGroupIds.addAll(tempGroupSet);

                                    selectedMemberWxids.clear();
                                    for (Object o : tempMemberKeys) {
                                        String k = (String) o;
                                        String[] arr = k.split("\\|", 2);
                                        if (arr.length == 2) selectedMemberWxids.add(arr[1]);
                                    }

                                    if (doneCallback != null) doneCallback.run();
                                }
                            }, null);
                        }
                    }, null);
                }
            });
        }
    });
}

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

private List loadAutoReplyRules() {
    Set rulesSet = getStringSet(AUTO_REPLY_RULES_KEY, new HashSet());
    List rules = new ArrayList();
    for (Object ruleStr : rulesSet) {
        Map<String, Object> rule = ruleFromString((String) ruleStr);
        if (rule != null) rules.add(rule);
    }
    // 已根据要求删除自动生成“你好”、“在吗”等默认规则的代码块
    return rules;
}

private void saveAutoReplyRules(List rules) {
    Set rulesSet = new HashSet();
    for (int i = 0; i < rules.size(); i++) {
        rulesSet.add(ruleMapToString((Map<String, Object>) rules.get(i)));
    }
    putStringSet(AUTO_REPLY_RULES_KEY, rulesSet);
}

// =================================================================================
// ========================== START: AI 配置 UI ==========================
// =================================================================================

private void showAIConfigDialog() {
    Activity activity = getTopActivity();
    if (activity == null) {
        toast("无法获取到当前窗口，无法显示AI配置");
        return;
    }

    ScrollView scrollView = new ScrollView(activity);
    LinearLayout layout = new LinearLayout(activity);
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setPadding(24, 24, 24, 24);
    layout.setBackgroundColor(Color.parseColor("#FAFBF9"));
    scrollView.addView(layout);

    LinearLayout configCard = createCardLayout();
    configCard.addView(createSectionTitle("服务配置"));
    configCard.addView(createTextView(activity, "WS地址:", 14, 0));
    final EditText wsEdit = createStyledEditText("WebSocket Server URL", getString(XIAOZHI_CONFIG_KEY, XIAOZHI_SERVE_KEY, "wss://api.tenclass.net/xiaozhi/v1/"));
    configCard.addView(wsEdit);
    configCard.addView(createTextView(activity, "OTA地址:", 14, 0));
    final EditText otaEdit = createStyledEditText("OTA Server URL", getString(XIAOZHI_CONFIG_KEY, XIAOZHI_OTA_KEY, "https://api.tenclass.net/xiaozhi/ota/"));
    configCard.addView(otaEdit);
    configCard.addView(createTextView(activity, "控制台地址:", 14, 0));
    final EditText consoleEdit = createStyledEditText("Console URL", getString(XIAOZHI_CONFIG_KEY, XIAOZHI_CONSOLE_KEY, "https://xiaozhi.me/console/agents"));
    configCard.addView(consoleEdit);
    layout.addView(configCard);

    LinearLayout deviceCard = createCardLayout();
    deviceCard.addView(createSectionTitle("设备信息"));
    TextView macText = new TextView(activity);
    macText.setText("MAC地址: " + getDeviceMac(activity));
    macText.setTextSize(14);
    macText.setTextColor(Color.parseColor("#333333"));
    deviceCard.addView(macText);
    TextView uuidText = new TextView(activity);
    uuidText.setText("UUID: " + getDeviceUUID(activity));
    uuidText.setTextSize(14);
    uuidText.setTextColor(Color.parseColor("#333333"));
    deviceCard.addView(uuidText);
    layout.addView(deviceCard);

    LinearLayout buttonCard = createCardLayout();
    Button bindButton = new Button(activity);
    bindButton.setText("绑定设备");
    styleUtilityButton(bindButton);
    bindButton.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            showBindDialog();
        }
    });
    buttonCard.addView(bindButton);
    layout.addView(buttonCard);

    final AlertDialog dialog = buildCommonAlertDialog(activity, "✨ 小智AI 配置 ✨", scrollView, "✅ 保存", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
            putString(XIAOZHI_CONFIG_KEY, XIAOZHI_SERVE_KEY, wsEdit.getText().toString());
            putString(XIAOZHI_CONFIG_KEY, XIAOZHI_OTA_KEY, otaEdit.getText().toString());
            putString(XIAOZHI_CONFIG_KEY, XIAOZHI_CONSOLE_KEY, consoleEdit.getText().toString());
            toast("小智AI配置已保存");
        }
    }, "❌ 取消", null, null, null);

    dialog.show();
}

private void showBindDialog() {
    final Activity activity = getTopActivity();
    if (activity == null) {
        toast("无法获取到当前窗口，无法显示绑定对话框");
        return;
    }

    ScrollView scrollView = new ScrollView(activity);
    final TextView messageView = new TextView(activity);
    messageView.setPadding(57, 20, 57, 20);
    messageView.setTextIsSelectable(true);
    messageView.setText("正在获取设备信息...");
    messageView.setTextSize(14);
    messageView.setTextColor(Color.parseColor("#333333"));
    scrollView.addView(messageView);

    final AlertDialog dialog = buildCommonAlertDialog(activity, "✨ 绑定设备 ✨", scrollView, null, null, "❌ 关闭", null, null, null);
    dialog.show();

    new Thread(new Runnable() {
        public void run() {
            try {
                String uuid = getDeviceUUID(activity);
                String mac = getDeviceMac(activity);

                final SpannableStringBuilder initialMessage = new SpannableStringBuilder();
                addStyledText(initialMessage, "UUID: ", "#3860AF", 14);
                addStyledText(initialMessage, uuid + "\n", "#777168", 13);
                addStyledText(initialMessage, "MAC: ", "#3860AF", 14);
                addStyledText(initialMessage, mac, "#777168", 13);

                activity.runOnUiThread(new Runnable() {
                    public void run() {
                        messageView.setText(initialMessage);
                    }
                });

                Map header = new HashMap();
                header.put("client-id", uuid);
                header.put("device-id", mac);

                String otaUrl = getString(XIAOZHI_CONFIG_KEY, XIAOZHI_OTA_KEY, "https://api.tenclass.net/xiaozhi/ota/");
                String jsonData = httpPost(otaUrl, "{\"application\":{\"name\":\"xiaozhi-web-test\",\"version\":\"1.0.0\",\"idf_version\":\"1.0.0\"},\"ota\":{\"label\":\"xiaozhi-web\"},\"mac_address\":\"" + mac + "\"}", header);

                if (jsonData == null) {
                     activity.runOnUiThread(new Runnable() {
                         public void run() {
                             messageView.append("\n\n请求失败，请检查网络或OTA地址。");
                         }
                     });
                     return;
                }

                JSONObject jsonObj = JSON.parseObject(jsonData);
                final SpannableStringBuilder updatedMessage = new SpannableStringBuilder(initialMessage);

                if (jsonObj.containsKey("activation")) {
                    addStyledText(updatedMessage, "\n\n正在获取验证码...", "#8C8C8C", 18);
                    JSONObject activationObj = jsonObj.getJSONObject("activation");
                    String code = activationObj.getString("code");
                    addStyledText(updatedMessage, "\n验证码: ", "#3860AF", 14);
                    addStyledText(updatedMessage, code, "#409EFF", 17);
                    addStyledText(updatedMessage, "\n\n验证码已获取", "#8C8C8C", 18);
                    addStyledText(updatedMessage, "\n前往控制台绑定设备:\n", "#3860AF", 14);
                    String consoleUrl = getString(XIAOZHI_CONFIG_KEY, XIAOZHI_CONSOLE_KEY, "https://xiaozhi.me/console/agents");
                    addStyledText(updatedMessage, consoleUrl, "#2F923D", 15);
                } else if (jsonObj.containsKey("error")) {
                    String error = jsonObj.getString("error");
                    addStyledText(updatedMessage, "\n\n出现错误: ", "#E53935", 14);
                    addStyledText(updatedMessage, error, "#777168", 13);
                } else if (jsonObj.containsKey("firmware")) {
                    JSONObject firmwareObj = jsonObj.getJSONObject("firmware");
                    String version = firmwareObj.getString("version");
                    addStyledText(updatedMessage, "\n\n设备已绑定", "#8C8C8C", 18);
                    addStyledText(updatedMessage, "\n固件版本: ", "#3860AF", 14);
                    addStyledText(updatedMessage, version, "#777168", 15);
                }

                activity.runOnUiThread(new Runnable() {
                    public void run() {
                        messageView.setText(updatedMessage);
                    }
                });
            } catch (Exception e) {
                final String errorMsg = "出现错误: " + e.getMessage();
                activity.runOnUiThread(new Runnable() {
                    public void run() {
                        messageView.setText(errorMsg);
                    }
                });
            }
        }
    }).start();
}

private void addStyledText(SpannableStringBuilder builder, String text, String color, int textSize) {
    int start = builder.length();
    builder.append(text);
    int end = builder.length();
    builder.setSpan(new ForegroundColorSpan(Color.parseColor(color)), start, end, 0);
    builder.setSpan(new AbsoluteSizeSpan(textSize, true), start, end, 0);
}

private void showTimePickerDialog(final EditText timeEdit) {
    final AlertDialog timeDialog = new AlertDialog.Builder(getTopActivity()).create();
    LinearLayout timeLayout = new LinearLayout(getTopActivity());
    timeLayout.setOrientation(LinearLayout.VERTICAL);
    timeLayout.setPadding(32, 32, 32, 32);
    TimePicker timePicker = new TimePicker(getTopActivity());
    timePicker.setIs24HourView(true);
    timeLayout.addView(timePicker);
    timeDialog.setView(timeLayout);
    timeDialog.setButton(AlertDialog.BUTTON_POSITIVE, "确定", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
            int hour = timePicker.getCurrentHour();
            int minute = timePicker.getCurrentMinute();
            String timeStr = String.format(Locale.getDefault(), "%02d:%02d", hour, minute);
            timeEdit.setText(timeStr);
        }
    });
    timeDialog.setOnShowListener(new DialogInterface.OnShowListener() {
        public void onShow(DialogInterface d) {
            setupUnifiedDialog(timeDialog);
        }
    });
    timeDialog.show();
}

private List getReplyItems(String itemsKey, String defaultText) {
    List replyItems = new ArrayList();
    String itemsStr = getString(itemsKey, "");

    if (TextUtils.isEmpty(itemsStr)) {
        if (!TextUtils.isEmpty(defaultText)) {
            replyItems.add(new AcceptReplyItem(ACCEPT_REPLY_TYPE_TEXT, defaultText));
        }
    } else {
        String[] itemArray = itemsStr.split(LIST_SEPARATOR);
        for (String itemStr : itemArray) {
            if (!TextUtils.isEmpty(itemStr.trim())) {
                AcceptReplyItem item = AcceptReplyItem.fromString(itemStr.trim());
                if (item != null) {
                    replyItems.add(item);
                }
            }
        }
    }
    return replyItems;
}

private List getGreetOnAcceptedReplyItems() {
    return getReplyItems(GREET_ON_ACCEPTED_REPLY_ITEMS_KEY, "哈喽，%friendName%！感谢通过好友请求，以后请多指教啦！");
}

private void saveReplyItemsByKey(String key, List replyItems) {
    if (replyItems == null || replyItems.isEmpty()) {
        putString(key, "");
        return;
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < replyItems.size(); i++) {
        if (i > 0) sb.append(LIST_SEPARATOR);
        sb.append(replyItems.get(i).toString());
    }
    putString(key, sb.toString());
}

private void saveAutoAcceptReplyItems(List replyItems) {
    saveReplyItemsByKey(AUTO_ACCEPT_REPLY_ITEMS_KEY, replyItems);
}

private void saveGreetOnAcceptedReplyItems(List replyItems) {
    saveReplyItemsByKey(GREET_ON_ACCEPTED_REPLY_ITEMS_KEY, replyItems);
}

private void setupUnifiedDialog(AlertDialog dialog) {
    GradientDrawable dialogBg = new GradientDrawable();
    dialogBg.setCornerRadius(48);
    dialogBg.setColor(Color.parseColor("#FAFBF9"));
    dialog.getWindow().setBackgroundDrawable(dialogBg);
    styleDialogButtons(dialog);
}

private void putString(String setName, String itemName, String value) {
    JSONObject json = getJsonObjSafe(setName);
    json.put(itemName, value);
    putJsonObjSafe(setName, json);
}

private String getString(String setName, String itemName, String defaultValue) {
    JSONObject json = getJsonObjSafe(setName);
    if (json.containsKey(itemName)) {
        return json.getString(itemName);
    }
    return defaultValue;
}

private JSONObject getJsonObjSafe(String setName) {
    String raw = getString(setName, "{}");
    try {
        JSONObject json = JSON.parseObject(raw);
        if (json == null) return new JSONObject();
        return json;
    } catch (Exception e) {
        return new JSONObject();
    }
}

private void putJsonObjSafe(String setName, JSONObject obj) {
    if (obj == null) obj = new JSONObject();
    putString(setName, obj.toString());
}
