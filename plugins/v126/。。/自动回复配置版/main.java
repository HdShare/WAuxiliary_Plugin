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
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Objects;
import android.view.MotionEvent;
import java.util.Collections;

// OkHttp3 and Fastjson2 imports for AI functionality
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
import com.alibaba.fastjson2.JSONPath;
import com.alibaba.fastjson2.JSONException;

// DeviceInfo related imports
import android.provider.Settings;
import java.util.UUID;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

// UI related imports from 小智bot
import android.app.Activity;
import android.app.Dialog;
import android.view.Window;
import android.view.WindowManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.UnderlineSpan;
import android.graphics.Typeface;

// === 文件/文件夹浏览与多选 ===
final String DEFAULT_LAST_FOLDER_SP_AUTO = "last_folder_for_media_auto";
final String ROOT_FOLDER = "/storage/emulated/0";

// 回调接口
interface MediaSelectionCallback {
    void onSelected(ArrayList<String> selectedFiles);
}

// 自动回复配置相关的key
private final String AUTO_REPLY_RULES_KEY = "auto_reply_rules";
private final String ENABLE_LOG_KEY = "enable_app_debug_log"; // 新增：日志开关KEY

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

// 缓存列表，避免重复获取
private List sCachedFriendList = null;
private List sCachedGroupList = null;
private java.util.Map sCachedGroupMemberCounts = null; // 缓存群成员数量

// 小智AI 功能相关变量
private final OkHttpClient aiClient = new OkHttpClient.Builder().build();
private final java.util.concurrent.ConcurrentMap<String, WebSocket> aiWebSockets = new java.util.concurrent.ConcurrentHashMap<String, WebSocket>();

// 智聊AI 功能相关变量
private Map<String, List> zhiliaConversationHistories = new HashMap<>();

// =================== START: 新增统一日志打印控制 ===================
private void debugLog(String msg) {
    if (getBoolean(ENABLE_LOG_KEY, true)) {
        log(msg);
    }
}
// =================== END: 新增统一日志打印控制 ===================

void browseFolderForSelectionAuto(final File startFolder, final String wantedExtFilter, final String currentSelection, final MediaSelectionCallback callback, final boolean allowFolderSelect) {
    putString(DEFAULT_LAST_FOLDER_SP_AUTO, startFolder.getAbsolutePath());
    ArrayList<String> names = new ArrayList<String>();
    final ArrayList<Object> items = new ArrayList<Object>();

    if (!startFolder.getAbsolutePath().equals(ROOT_FOLDER)) {
        names.add("⬆ 上一级");
        items.add(startFolder.getParentFile());
    }

    File[] subs = startFolder.listFiles();
    if (subs != null) {
        for (int i = 0; i < subs.length; i++) {
            File f = subs[i];
            if (f.isDirectory()) {
                names.add("📁 " + f.getName());
                items.add(f);
            }
        }
    }

    AlertDialog.Builder builder = new AlertDialog.Builder(getTopActivity());
    builder.setTitle("浏览：" + startFolder.getAbsolutePath());
    final ListView list = new ListView(getTopActivity());
    list.setAdapter(new ArrayAdapter<String>(getTopActivity(), android.R.layout.simple_list_item_1, names));
    builder.setView(list);

    final AlertDialog dialog = builder.create();
    list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
            dialog.dismiss();
            Object selected = items.get(pos);
            if (selected instanceof File) {
                File sel = (File) selected;
                if (sel.isDirectory()) {
                    browseFolderForSelectionAuto(sel, wantedExtFilter, currentSelection, callback, allowFolderSelect);
                }
            }
        }
    });

    builder.setPositiveButton("在此目录选择文件", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface d, int which) {
            d.dismiss();
            scanFilesMulti(startFolder, wantedExtFilter, currentSelection, callback);
        }
    });

    if (allowFolderSelect) {
        builder.setNeutralButton("选择此文件夹", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int which) {
                d.dismiss();
                ArrayList<String> selected = new ArrayList<String>();
                selected.add(startFolder.getAbsolutePath());
                callback.onSelected(selected);
            }
        });
    }

    builder.setNegativeButton("取消", null);
    final AlertDialog finalDialog = builder.create();
    finalDialog.setOnShowListener(new DialogInterface.OnShowListener() {
        public void onShow(DialogInterface d) {
            setupUnifiedDialog(finalDialog);
        }
    });
    finalDialog.show();
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
                            sendText(talker, replyText);
                            debugLog("[小智AI] 发送回复 -> " + replyText);
                        }
                    }
                } catch (Exception e) {
                    debugLog("[异常] 小智AI 数据解析失败: " + e.getMessage());
                }
            }

            public void onClosing(WebSocket webSocket, int code, String reason) {
                aiWebSockets.remove(talker);
                debugLog("[小智AI] WebSocket 连接关闭 -> " + reason);
                insertSystemMsg(talker, "小智AI 连接已关闭: " + reason, System.currentTimeMillis());
            }

            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                aiWebSockets.remove(talker);
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

private void sendZhiliaAiReply(final String talker, String userContent) {
    String apiKey = getString(ZHILIA_AI_API_KEY, "");
    String apiUrl = getString(ZHILIA_AI_API_URL, "https://api.siliconflow.cn/v1/chat/completions");
    String modelName = getString(ZHILIA_AI_MODEL_NAME, "deepseek-ai/DeepSeek-V3");
    String systemPrompt = getString(ZHILIA_AI_SYSTEM_PROMPT, "你是个宝宝");
    int contextLimit = getInt(ZHILIA_AI_CONTEXT_LIMIT, 10);

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
    
    debugLog("[智聊AI] 触发提问 -> 内容: " + userContent + " (历史长度:" + history.size() + ")");

    while (history.size() > contextLimit * 2 + 1) {
        history.remove(1); 
        if (history.size() > 1) history.remove(1); 
    }

    JSONObject jsonBody = new JSONObject();
    jsonBody.put("model", modelName);
    jsonBody.put("messages", history);
    jsonBody.put("temperature", 0.7);
    jsonBody.put("stream", false); 
    String requestData = jsonBody.toString();

    Map headerMap = new HashMap();
    headerMap.put("Content-Type", "application/json");
    headerMap.put("Authorization", "Bearer " + apiKey);

    RequestBody body = RequestBody.create(MediaType.parse("application/json"), requestData);
    Request.Builder reqBuilder = new Request.Builder().url(apiUrl).post(body);
    addHeaders(reqBuilder, headerMap); 

    aiClient.newCall(reqBuilder.build()).enqueue(new okhttp3.Callback() {
        public void onFailure(okhttp3.Call call, IOException e) {
            debugLog("[异常] 智聊AI 网络请求失败: " + e.getMessage());
            insertSystemMsg(talker, "智聊AI网络错误: " + e.getMessage(), System.currentTimeMillis());
        }

        public void onResponse(okhttp3.Call call, Response response) throws IOException {
            String responseContent = response.body() != null ? response.body().string() : null;

            if (responseContent == null || !responseContent.trim().startsWith("{")) {
                debugLog("[异常] 智聊AI 返回了非JSON格式响应");
                insertSystemMsg(talker, "智聊AI响应无效", System.currentTimeMillis());
                return;
            }

            try {
                JSONObject jsonObj = JSON.parseObject(responseContent);
                if (jsonObj.containsKey("error")) {
                    String errorMessage = jsonObj.getJSONObject("error").getString("message");
                    debugLog("[异常] 智聊AI 接口报错: " + errorMessage);
                    insertSystemMsg(talker, "智聊AI API错误: " + errorMessage, System.currentTimeMillis());
                    return;
                }

                if (!jsonObj.containsKey("choices")) return;

                JSONArray choices = jsonObj.getJSONArray("choices");
                if (choices.size() > 0) {
                    String msgContent = choices.getJSONObject(0).getJSONObject("message").getString("content");
                    debugLog("[智聊AI] 获取回复成功 -> " + msgContent);

                    if (!TextUtils.isEmpty(msgContent)) {
                        sendText(talker, msgContent);
                    } else {
                        sendText(talker, "抱歉，我暂时无法回复。");
                    }

                    Map assistantMsg = new HashMap();
                    assistantMsg.put("role", "assistant");
                    assistantMsg.put("content", msgContent != null ? msgContent : "默认回复");
                    history.add(assistantMsg);
                    zhiliaConversationHistories.put(talker, history);
                }
            } catch (JSONException e) {
                debugLog("[异常] 智聊AI JSON解析失败: " + e.getMessage());
                insertSystemMsg(talker, "智聊AI解析错误", System.currentTimeMillis());
            }
        }
    });
}

private Map<String, Object> createAutoReplyRuleMap(String keyword, String reply, boolean enabled, int matchType, Set targetWxids, int targetType, int atTriggerType, long delaySeconds, boolean replyAsQuote, int replyType, List mediaPaths, String startTime, String endTime, Set excludedWxids, long mediaDelaySeconds, int patTriggerType) {
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
    rule.put("compiledPattern", null); 
    return rule;
}

private Map<String, Object> createAutoReplyRuleMap(String keyword, String reply, boolean enabled, int matchType, Set targetWxids, int targetType, int atTriggerType, long delaySeconds, boolean replyAsQuote, int replyType, List mediaPaths) {
    return createAutoReplyRuleMap(keyword, reply, enabled, matchType, targetWxids, targetType, atTriggerType, delaySeconds, replyAsQuote, replyType, mediaPaths, "", "", new HashSet(), 1L, PAT_TRIGGER_NONE);
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

    return keyword + "||" + reply + "||" + enabled + "||" + matchType + "||" + wxidsStr + "||" + atTriggerType + "||" + delaySeconds + "||" + targetType + "||" + replyAsQuote + "||" + replyType + "||" + mediaPathsStr + "||" + (startTime != null ? startTime : "") + "||" + (endTime != null ? endTime : "") + "||" + excludedStr + "||" + mediaDelaySeconds + "||" + patTriggerType;
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
        rule = createAutoReplyRuleMap(keyword, reply, enabled, matchType, wxids, targetType, atTriggerType, delaySeconds, replyAsQuote, replyType, parsedMediaPaths, startTime, endTime, excludedWxids, mediaDelaySeconds, patTriggerType);
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

public boolean onClickSendBtn(String text) {
    if ("自动回复设置".equals(text) || "自动回复".equals(text) || "回复设置".equals(text)) {
        try {
            showAutoReplySettingDialog();
            return true;
        } catch (Exception e) {
            debugLog("[异常] 打开自动回复设置菜单失败: " + e.getMessage());
            return false;
        }
    }
    if ("好友请求设置".equals(text) || "自动通过".equals(text)) {
        try {
            showAutoAcceptFriendDialog();
            return true;
        } catch (Exception e) {
            debugLog("[异常] 打开好友请求设置菜单失败: " + e.getMessage());
            return false;
        }
    }
    if ("添加好友回复".equals(text) || "好友通过回复".equals(text)) {
        try {
            showGreetOnAcceptedDialog();
            return true;
        } catch (Exception e) {
            debugLog("[异常] 打开好友通过回复菜单失败: " + e.getMessage());
            return false;
        }
    }
    if ("回复规则".equals(text) || "规则管理".equals(text)) {
        try {
            showAutoReplyRulesDialog();
            return true;
        } catch (Exception e) {
            debugLog("[异常] 打开回复规则菜单失败: " + e.getMessage());
            return false;
        }
    }
    if ("AI配置".equals(text) || "智聊配置".equals(text) || "小智配置".equals(text)) {
        try {
            showAIChoiceDialog();
            return true;
        } catch (Exception e) {
            debugLog("[异常] 打开AI配置菜单失败: " + e.getMessage());
            return false;
        }
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
        final String content = getFieldString(msgInfoBean, "originContent");
        final String senderWxid = getFieldString(msgInfoBean, "sendTalker");
        final String talker = getFieldString(msgInfoBean, "talker");
        final long msgId = getFieldLong(msgInfoBean, "msgId", 0L);
        
        final String effectiveSenderWxid = TextUtils.isEmpty(senderWxid) ? talker : senderWxid;
        final boolean isGroupChat = !TextUtils.isEmpty(talker) && talker.contains("@chatroom");
        final boolean isPrivateChat = !isGroupChat;
        
        boolean isAtMe = false;
        boolean isNotifyAll = false;
        if (isGroupChat) {
            isAtMe = content != null && content.startsWith("@");
            isNotifyAll = content != null && content.contains("@全体成员");
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
            if (targetType != TARGET_TYPE_NONE) {
                boolean targetMatch = false;
                Set targetWxids = (Set) rule.get("targetWxids");
                if (targetType == TARGET_TYPE_FRIEND) {
                    if (isPrivateChat && targetWxids.contains(effectiveSenderWxid)) targetMatch = true;
                } else if (targetType == TARGET_TYPE_GROUP) {
                    if (isGroupChat && targetWxids.contains(talker)) targetMatch = true;
                } else if (targetType == TARGET_TYPE_BOTH) {
                    if ((isPrivateChat && targetWxids.contains(effectiveSenderWxid)) || (isGroupChat && targetWxids.contains(talker))) targetMatch = true;
                }
                if (!targetMatch) continue;
            }

            Set excludedWxids = (Set) rule.get("excludedWxids");
            if (excludedWxids != null && !excludedWxids.isEmpty()) {
                if (isPrivateChat && excludedWxids.contains(effectiveSenderWxid)) continue;
                if (isGroupChat && excludedWxids.contains(talker)) continue;
            }

            int atTriggerType = (Integer) rule.get("atTriggerType");
            if (isGroupChat) {
                int actualAtType = isNotifyAll ? AT_TRIGGER_ALL : (isAtMe ? AT_TRIGGER_ME : AT_TRIGGER_NONE);
                if ((atTriggerType == AT_TRIGGER_ME && actualAtType != AT_TRIGGER_ME) || (atTriggerType == AT_TRIGGER_ALL && actualAtType != AT_TRIGGER_ALL)) {
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
                switch (matchType) {
                    case MATCH_TYPE_ANY: isMatch = true; break;
                    case MATCH_TYPE_EXACT: isMatch = content.equals(keyword); break;
                    case MATCH_TYPE_REGEX:
                        Pattern compiledPattern = (Pattern) rule.get("compiledPattern");
                        if (compiledPattern != null) isMatch = compiledPattern.matcher(content).matches();
                        else isMatch = false;
                        break;
                    case MATCH_TYPE_FUZZY: default: isMatch = content.contains(keyword); break;
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
        switch (replyType) {
            case REPLY_TYPE_XIAOZHI_AI:
                debugLog("[执行回复] 动作: 调用小智AI, 目标: " + talker);
                processAIResponse(msgInfoBean);
                break;
            case REPLY_TYPE_ZHILIA_AI:
                debugLog("[执行回复] 动作: 调用智聊AI, 目标: " + talker);
                sendZhiliaAiReply(talker, content);
                break;
            case REPLY_TYPE_IMAGE:
            case REPLY_TYPE_VIDEO:
            case REPLY_TYPE_EMOJI:
            case REPLY_TYPE_FILE:
                debugLog("[执行回复] 动作: 发送多媒体文件, 目标: " + talker);
                if (mediaPaths != null && !mediaPaths.isEmpty()) {
                    long mediaDelaySeconds = (Long) finalRule.get("mediaDelaySeconds");
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
                            if (j < mediaPaths.size() - 1) {
                                try { Thread.sleep(mediaDelaySeconds * 1000); } catch (Exception e) {}
                            }
                        }
                    }
                }
                break;
            case REPLY_TYPE_VOICE_FILE_LIST:
                debugLog("[执行回复] 动作: 按列表发送语音, 目标: " + talker);
                if (mediaPaths != null && !mediaPaths.isEmpty()) {
                    long mediaDelaySeconds = (Long) finalRule.get("mediaDelaySeconds");
                    for (int j = 0; j < mediaPaths.size(); j++) {
                        String voicePath = (String) mediaPaths.get(j);
                        sendVoice(talker, voicePath);
                        if (j < mediaPaths.size() - 1) {
                            try { Thread.sleep(mediaDelaySeconds * 1000); } catch (Exception e) {}
                        }
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
                    }
                }
                break;
            case REPLY_TYPE_CARD:
                debugLog("[执行回复] 动作: 发送名片, 目标: " + talker);
                if (!TextUtils.isEmpty(replyContent)) {
                    long mediaDelaySeconds = (Long) finalRule.get("mediaDelaySeconds");
                    String[] wxids = replyContent.split(";;;");
                    for (int j = 0; j < wxids.length; j++) {
                        String wxidToShare = wxids[j].trim();
                        if (!TextUtils.isEmpty(wxidToShare)) {
                            sendShareCard(talker, wxidToShare);
                            if (j < wxids.length - 1) {
                                try { Thread.sleep(mediaDelaySeconds * 1000); } catch (Exception e) {}
                            }
                        }
                    }
                }
                break;
            case REPLY_TYPE_INVITE_GROUP:
                debugLog("[执行回复] 动作: 邀请加入群聊, 目标: " + talker);
                if (!TextUtils.isEmpty(replyContent)) {
                    long mediaDelaySeconds = (Long) finalRule.get("mediaDelaySeconds");
                    String[] groupIds = replyContent.split(";;;");
                    String effectiveSenderWxid = getFieldString(msgInfoBean, "sendTalker");
                    if (TextUtils.isEmpty(effectiveSenderWxid)) effectiveSenderWxid = talker;
                    for (int j = 0; j < groupIds.length; j++) {
                        String groupId = groupIds[j].trim();
                        if (!TextUtils.isEmpty(groupId)) {
                            inviteChatroomMember(groupId, effectiveSenderWxid);
                            if (j < groupIds.length - 1) {
                                try { Thread.sleep(mediaDelaySeconds * 1000); } catch (Exception e) {}
                            }
                        }
                    }
                }
                break;
            case REPLY_TYPE_TEXT: default:
                debugLog("[执行回复] 动作: " + (replyAsQuote ? "引用发送文本" : "发送文本") + " -> " + replyContent);
                if (replyAsQuote) {
                    sendQuoteMsg(talker, msgId, replyContent);
                } else {
                    sendText(talker, replyContent);
                }
                break;
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
        String senderWxid = getFieldString(msgInfoBean, "sendTalker");
        String talker = getFieldString(msgInfoBean, "talker");
        if (TextUtils.isEmpty(senderWxid)) {
            senderWxid = talker;
        }
        
        String senderName = "";
        boolean isGroupChat = !TextUtils.isEmpty(talker) && talker.contains("@chatroom");
        boolean isPrivateChat = !isGroupChat;
        if (isPrivateChat) {
            senderName = getFriendDisplayName(senderWxid);
        } else if (isGroupChat) {
            senderName = getFriendName(senderWxid, talker);
        }
        if (TextUtils.isEmpty(senderName)) senderName = "未知用户";
        result = result.replace("%senderName%", senderName).replace("%senderWxid%", senderWxid);
        
        if (isGroupChat) {
            result = result.replace("%atSender%", "[AtWx=" + senderWxid + "]");
        } else {
            result = result.replace("%atSender%", ""); 
        }
        
        if (isGroupChat) {
            String groupName = getGroupName(talker);
            result = result.replace("%groupName%", TextUtils.isEmpty(groupName) ? "未知群聊" : groupName);
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
        ScrollView scrollView = new ScrollView(getTopActivity());
        LinearLayout rootLayout = new LinearLayout(getTopActivity());
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(24, 24, 24, 24);
        rootLayout.setBackgroundColor(Color.parseColor("#FAFBF9"));
        scrollView.addView(rootLayout);

        // --- 卡片1: 主要功能管理 ---
        LinearLayout managementCard = createCardLayout();
        managementCard.addView(createSectionTitle("🤖 自动功能设置"));
        Button autoAcceptButton = new Button(getTopActivity());
        autoAcceptButton.setText("🤝 好友请求自动处理");
        styleUtilityButton(autoAcceptButton);
        managementCard.addView(autoAcceptButton);
        Button greetButton = new Button(getTopActivity());
        greetButton.setText("👋 添加好友自动回复");
        styleUtilityButton(greetButton);
        managementCard.addView(greetButton);
        Button rulesButton = new Button(getTopActivity());
        rulesButton.setText("📝 管理消息回复规则");
        styleUtilityButton(rulesButton);
        managementCard.addView(rulesButton);
        Button aiButton = new Button(getTopActivity());
        aiButton.setText("🧠 AI 配置");
        styleUtilityButton(aiButton);
        managementCard.addView(aiButton);
        
        // 【新增】日志总开关
        final LinearLayout logSwitchRow = createSwitchRow(getTopActivity(), "📝 开启运行日志 (便于排错)", getBoolean(ENABLE_LOG_KEY, true), new View.OnClickListener() {
            public void onClick(View v) {}
        });
        final CheckBox logCheckBox = (CheckBox) logSwitchRow.getChildAt(1);
        managementCard.addView(logSwitchRow);
        
        rootLayout.addView(managementCard);

        // --- 对话框构建 ---
        final AlertDialog dialog = buildCommonAlertDialog(getTopActivity(), "✨ 自动回复统一设置 ✨", scrollView, null, null, "❌ 关闭", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                putBoolean(ENABLE_LOG_KEY, logCheckBox.isChecked()); // 保存日志开关状态
                dialog.dismiss();
            }
        }, null, null);

        autoAcceptButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showAutoAcceptFriendDialog();
            }
        });

        greetButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showGreetOnAcceptedDialog();
            }
        });

        rulesButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showAutoReplyRulesDialog();
            }
        });

        aiButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showAIChoiceDialog();
            }
        });

        dialog.show();

    } catch (Exception e) {
        toast("打开设置界面失败: " + e.getMessage());
    }
}

private void showAIChoiceDialog() {
    LinearLayout layout = new LinearLayout(getTopActivity());
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setPadding(24, 24, 24, 24);
    layout.setBackgroundColor(Color.parseColor("#FAFBF9"));

    Button xiaozhiButton = new Button(getTopActivity());
    xiaozhiButton.setText("小智AI 配置");
    styleUtilityButton(xiaozhiButton);
    layout.addView(xiaozhiButton);

    Button zhiliaButton = new Button(getTopActivity());
    zhiliaButton.setText("智聊AI 配置");
    styleUtilityButton(zhiliaButton);
    layout.addView(zhiliaButton);

    final AlertDialog choiceDialog = buildCommonAlertDialog(getTopActivity(), "🧠 选择AI配置", layout, null, null, "❌ 取消", null, null, null);

    xiaozhiButton.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            choiceDialog.dismiss();
            showXiaozhiAIConfigDialog();
        }
    });

    zhiliaButton.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            choiceDialog.dismiss();
            showZhiliaAIConfigDialog();
        }
    });

    choiceDialog.show();
}

private void showXiaozhiAIConfigDialog() {
    showAIConfigDialog();
}

private void showZhiliaAIConfigDialog() {
    try {
        ScrollView scrollView = new ScrollView(getTopActivity());
        LinearLayout layout = new LinearLayout(getTopActivity());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 24, 24, 24);
        layout.setBackgroundColor(Color.parseColor("#FAFBF9"));
        scrollView.addView(layout);

        LinearLayout apiCard = createCardLayout();
        apiCard.addView(createSectionTitle("智聊AI 参数设置"));
        apiCard.addView(createTextView(getTopActivity(), "API Key:", 14, 0));
        final EditText apiKeyEdit = createStyledEditText("请输入你的API Key", getString(ZHILIA_AI_API_KEY, ""));
        apiCard.addView(apiKeyEdit);
        apiCard.addView(createTextView(getTopActivity(), "API URL:", 14, 0));
        final EditText apiUrlEdit = createStyledEditText("默认为官方API", getString(ZHILIA_AI_API_URL, "https://api.siliconflow.cn/v1/chat/completions"));
        apiCard.addView(apiUrlEdit);
        apiCard.addView(createTextView(getTopActivity(), "模型名称:", 14, 0));
        final EditText modelNameEdit = createStyledEditText("例如 deepseek-ai/DeepSeek-V2-Chat", getString(ZHILIA_AI_MODEL_NAME, "deepseek-ai/DeepSeek-V3"));
        apiCard.addView(modelNameEdit);
        layout.addView(apiCard);

        LinearLayout advancedCard = createCardLayout();
        advancedCard.addView(createSectionTitle("高级设置"));
        advancedCard.addView(createTextView(getTopActivity(), "上下文轮次 (建议5-10):", 14, 0));
        final EditText contextLimitEdit = createStyledEditText("数字越大越消耗Token", String.valueOf(getInt(ZHILIA_AI_CONTEXT_LIMIT, 10)));
        contextLimitEdit.setInputType(InputType.TYPE_CLASS_NUMBER);
        advancedCard.addView(contextLimitEdit);
        advancedCard.addView(createTextView(getTopActivity(), "系统指令 (AI角色设定):", 14, 0));
        final EditText systemPromptEdit = createStyledEditText("设定AI的身份和回复风格", getString(ZHILIA_AI_SYSTEM_PROMPT, "你是个宝宝"));
        systemPromptEdit.setMinLines(3);
        systemPromptEdit.setGravity(Gravity.TOP);
        advancedCard.addView(systemPromptEdit);
        layout.addView(advancedCard);

        final AlertDialog dialog = buildCommonAlertDialog(getTopActivity(), "🧠 智聊AI 参数设置", scrollView, "✅ 保存", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                String apiKey = apiKeyEdit.getText().toString().trim();
                if (TextUtils.isEmpty(apiKey)) {
                    toast("API Key 不能为空！");
                    return;
                }
                putString(ZHILIA_AI_API_KEY, apiKey);
                putString(ZHILIA_AI_API_URL, apiUrlEdit.getText().toString().trim());
                putString(ZHILIA_AI_MODEL_NAME, modelNameEdit.getText().toString().trim());
                putString(ZHILIA_AI_SYSTEM_PROMPT, systemPromptEdit.getText().toString().trim());
                try {
                    putInt(ZHILIA_AI_CONTEXT_LIMIT, Integer.parseInt(contextLimitEdit.getText().toString().trim()));
                } catch (Exception e) {
                    putInt(ZHILIA_AI_CONTEXT_LIMIT, 10); 
                }
                toast("智聊AI 设置已保存");
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
                    int targetType = (Integer) rule.get("targetType");
                    String targetInfo = getTargetInfo(targetType, targetWxids);
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
                    Set excludedWxids = (Set) rule.get("excludedWxids");
                    String excludeInfo = (excludedWxids != null && !excludedWxids.isEmpty()) ? " (排除:" + excludedWxids.size() + ")" : "";
                    String keyword = (String) rule.get("keyword");
                    rulesAdapter.add((i + 1) + ". " + status + " [" + matchTypeStr + "] [" + atTriggerStr + "] [" + patTriggerStr + "] " + (matchType == MATCH_TYPE_ANY ? "(任何消息)" : keyword) + " → " + replyTypeStr + replyContentPreview + targetInfo + delayInfo + mediaDelayInfo + quoteInfo + timeInfo + excludeInfo);
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

private String getTargetInfo(int targetType, Set targetWxids) {
    if (targetType == TARGET_TYPE_FRIEND) return " (指定好友: " + (targetWxids != null ? targetWxids.size() : 0) + "人)";
    else if (targetType == TARGET_TYPE_GROUP) return " (指定群聊: " + (targetWxids != null ? targetWxids.size() : 0) + "个)";
    else if (targetType == TARGET_TYPE_BOTH) return " (指定好友/群聊: " + (targetWxids != null ? targetWxids.size() : 0) + "个)";
    return "";
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
        final RadioButton replyTypeXiaozhiAIRadio = createRadioButton(getTopActivity(), "🤖 小智AI 回复(回复快,能联网)");
        final RadioButton replyTypeZhiliaAIRadio = createRadioButton(getTopActivity(), "🧠 智聊AI 回复(回复慢,不能联网,可以用deepseek官方key官方配置即可联网)"); 
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
                if (targetType == TARGET_TYPE_BOTH) {
                    Set targetWxids = (Set) rule.get("targetWxids");
                    selectFriendsButton.setText("👤 指定生效好友 (" + getFriendCountInTargetWxids(targetWxids) + "人)");
                    styleUtilityButton(selectFriendsButton);
                    selectFriendsButton.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showSelectTargetFriendsDialog(targetWxids, updateSelectTargetsButton); } });
                    selectFriendsButton.setVisibility(View.VISIBLE);
                    selectGroupsButton.setText("🏠 指定生效群聊 (" + getGroupCountInTargetWxids(targetWxids) + "个)");
                    styleUtilityButton(selectGroupsButton);
                    selectGroupsButton.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showSelectTargetGroupsDialog(targetWxids, updateSelectTargetsButton); } });
                    selectGroupsButton.setVisibility(View.VISIBLE);
                } else {
                    selectFriendsButton.setVisibility(View.GONE);
                    selectGroupsButton.setVisibility(View.GONE);
                    rule.put("targetWxids", new HashSet());
                }
            }
        };

        final Runnable updateSelectExcludedButtons = new Runnable() {
            public void run() {
                Set excludedWxids = (Set) rule.get("excludedWxids");
                selectExcludeFriendsButton.setText("👤 排除好友 (" + getFriendCountInTargetWxids(excludedWxids) + "人)");
                styleUtilityButton(selectExcludeFriendsButton);
                selectExcludeFriendsButton.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showSelectExcludeFriendsDialog(excludedWxids, updateSelectExcludedButtons); } });
                selectExcludeGroupsButton.setText("🏠 排除群聊 (" + getGroupCountInTargetWxids(excludedWxids) + "个)");
                styleUtilityButton(selectExcludeGroupsButton);
                selectExcludeGroupsButton.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showSelectExcludeGroupsDialog(excludedWxids, updateSelectExcludedButtons); } });
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
        updateSelectExcludedButtons.run();
        
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

private void saveAutoAcceptReplyItems(List replyItems) {
    if (replyItems == null || replyItems.isEmpty()) {
        putString(AUTO_ACCEPT_REPLY_ITEMS_KEY, "");
        return;
    }
    
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < replyItems.size(); i++) {
        if (i > 0) sb.append(LIST_SEPARATOR);
        sb.append(replyItems.get(i).toString());
    }
    putString(AUTO_ACCEPT_REPLY_ITEMS_KEY, sb.toString());
}

private void saveGreetOnAcceptedReplyItems(List replyItems) {
    if (replyItems == null || replyItems.isEmpty()) {
        putString(GREET_ON_ACCEPTED_REPLY_ITEMS_KEY, "");
        return;
    }
    
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < replyItems.size(); i++) {
        if (i > 0) sb.append(LIST_SEPARATOR);
        sb.append(replyItems.get(i).toString());
    }
    putString(GREET_ON_ACCEPTED_REPLY_ITEMS_KEY, sb.toString());
}

private void setupUnifiedDialog(AlertDialog dialog) {
    GradientDrawable dialogBg = new GradientDrawable();
    dialogBg.setCornerRadius(48);
    dialogBg.setColor(Color.parseColor("#FAFBF9"));
    dialog.getWindow().setBackgroundDrawable(dialogBg);
    styleDialogButtons(dialog);
}

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
        if (json.containsKey(itemName)) {
            return json.getString(itemName);
        }
    } catch (Exception e) {
    }
    return defaultValue;
}
