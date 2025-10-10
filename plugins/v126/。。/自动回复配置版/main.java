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

// 判断是否需要全选的辅助方法
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

// 更新全选按钮文本的辅助方法
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

// 【新增】动态调整ListView高度的辅助方法（最小50dp/项，最大300dp）
private void adjustListViewHeight(ListView listView, int itemCount) {
    if (itemCount <= 0) {
        listView.getLayoutParams().height = dpToPx(50); // 最小高度，避免完全隐藏
    } else {
        int itemHeight = dpToPx(50); // 假设每个项约50dp
        int calculatedHeight = Math.min(itemCount * itemHeight, dpToPx(300));
        listView.getLayoutParams().height = calculatedHeight;
    }
    listView.requestLayout();
}

// 【优化】改进ListView触摸事件处理，确保直接触摸即可滚动（在ACTION_DOWN时拦截ScrollView）
private void setupListViewTouchForScroll(ListView listView) {
    listView.setOnTouchListener(new View.OnTouchListener() {
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    // 触摸开始时，请求父容器（ScrollView）不要拦截事件
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    // 触摸结束时，允许父容器恢复拦截
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    break;
            }
            return false; // 让ListView处理事件
        }
    });
}

// 自动回复配置相关的key
private final String AUTO_REPLY_RULES_KEY = "auto_reply_rules";
private final String AUTO_REPLY_FRIEND_ENABLED_KEY = "auto_reply_friend_enabled";
private final String AUTO_REPLY_GROUP_ENABLED_KEY = "auto_reply_group_enabled";
private final String AUTO_REPLY_ENABLED_FRIENDS_KEY = "auto_reply_enabled_friends";
private final String AUTO_REPLY_ENABLED_GROUPS_KEY = "auto_reply_enabled_groups";

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

// 【新增】拍一拍触发类型常量
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
private final static int REPLY_TYPE_VIDEO = 6;      // 视频回复 (新增)
private final static int REPLY_TYPE_CARD = 7;       // 名片回复 (新增，支持多选)
private final static int REPLY_TYPE_FILE = 8;       // 文件分享 (新增)
private final static int REPLY_TYPE_ZHILIA_AI = 9;  // 智聊AI自动回复 (新增，共存)

// 自动同意好友/被通过的回复类型常量
private final static int ACCEPT_REPLY_TYPE_TEXT = 0;
private final static int ACCEPT_REPLY_TYPE_IMAGE = 1;
private final static int ACCEPT_REPLY_TYPE_VOICE_FIXED = 2;
private final static int ACCEPT_REPLY_TYPE_VOICE_RANDOM = 3;
private final static int ACCEPT_REPLY_TYPE_EMOJI = 4;
private final static int ACCEPT_REPLY_TYPE_VIDEO = 5; // 新增
private final static int ACCEPT_REPLY_TYPE_CARD = 6;  // 名片 (新增，支持多选)
private final static int ACCEPT_REPLY_TYPE_FILE = 7;  // 文件分享 (新增)

// 用于分隔列表项的特殊字符串
private final String LIST_SEPARATOR = "_#ITEM#_";

// 缓存列表，避免重复获取
private List sCachedFriendList = null;
private List sCachedGroupList = null;
private java.util.Map sCachedGroupMemberCounts = null; // 缓存群成员数量

// 小智AI 功能相关变量
// OkHttp 客户端实例，用于发起网络请求
private final OkHttpClient aiClient = new OkHttpClient.Builder().build();
// 【修改】使用 ConcurrentHashMap 来确保线程安全地管理每个聊天会話的 WebSocket 连接
// Key 是聊天对象 wxid (talker)，Value 是对应的 WebSocket 连接实例
private final java.util.concurrent.ConcurrentMap<String, WebSocket> aiWebSockets = new java.util.concurrent.ConcurrentHashMap<String, WebSocket>();

// 智聊AI 功能相关变量 (移植自旧脚本)
private Map<String, List> zhiliaConversationHistories = new HashMap<>();

// =================================================================================
// =================== START: 小智bot 核心功能代码移植 ===================
// =================================================================================

// --- 设备信息工具方法 ---
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
        log("Error generating MAC: " + e.getMessage());
        return "00:00:00:00:00:00";
    }
}

// --- 网络请求工具 ---
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
        log("AI Request failed: " + e.getMessage());
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

// --- 小智AI 核心处理逻辑 ---
// 【修改】重写AI处理逻辑，以支持多会话并确保线程安全
private void processAIResponse(final Object msgInfoBean) {
    if (msgInfoBean == null) {
        log("processAIResponse: msgInfoBean is null");
        return;
    }
    
    try {
        String content = invokeStringMethod(msgInfoBean, "getContent");
        if (TextUtils.isEmpty(content)) {
            log("processAIResponse: Empty content");
            return;
        }

        final String talker = invokeStringMethod(msgInfoBean, "getTalker");
        if (TextUtils.isEmpty(talker)) {
            log("processAIResponse: Empty talker");
            return;
        }

        // 检查是否在群聊中，如果是，需要特殊处理@消息
        boolean isGroupChat = invokeBooleanMethod(msgInfoBean, "isGroupChat");
        if (isGroupChat) {
           // boolean isAtMe = invokeBooleanMethod(msgInfoBean, "isAtMe");
           // if (!isAtMe) {
              //  log("processAIResponse: Not @ me in group chat, ignoring");
            //    return;
        //    }怕骚扰别人就把这几行代码取消注释
            
            // 移除@信息
            content = content.replaceAll("@[^\\s]+\\s+", "").trim();
            if (TextUtils.isEmpty(content)) {
                log("processAIResponse: Empty content after removing @");
                return;
            }
        }

        // 处理断开连接命令
        if ("#断开".equals(content) || "#断连".equals(content) || "#断线".equals(content)) {
            WebSocket webSocket = aiWebSockets.get(talker);
            if (webSocket != null) {
                webSocket.close(1000, "手动断开");
                // onClosing/onFailure 回调会自动从Map中移除连接
            }
            return;
        }

        final String finalText = content;
        
        // 在后台线程处理AI请求
        new Thread(new Runnable() {
            public void run() {
                try {
                    // 检查当前 talker 是否已有连接
                    WebSocket currentSocket = aiWebSockets.get(talker);
                    if (currentSocket == null) {
                        // 没有连接，则初始化一个新的
                        initializeWebSocketConnection(talker, finalText);
                    } else {
                        // 已有连接，直接发送消息
                        sendMessageToWebSocket(talker, finalText);
                    }
                } catch (Exception e) {
                    log("Error in AI response thread: " + e.getMessage());
                    insertSystemMsg(talker, "小智AI 处理消息时出错: " + e.getMessage(), System.currentTimeMillis());
                }
            }
        }).start();
    } catch (Exception e) {
        log("processAIResponse error: " + e.getMessage());
    }
}

// 【修改】初始化WebSocket连接，为指定的 talker 创建
private void initializeWebSocketConnection(final String talker, final String text) {
    try {
        // 使用 ConcurrentHashMap 的 putIfAbsent 可以原子性地检查并放入，防止重复创建连接
        // 但由于 listener 的创建和 newWebSocket 的调用不是原子操作，这里还是先检查
        if (aiWebSockets.containsKey(talker)) {
            log("WebSocket for " + talker + " is already connecting or connected.");
            return;
        }

        WebSocketListener listener = new WebSocketListener() {
            public void onOpen(WebSocket webSocket, Response response) {
                // 连接成功后，将其存入 Map
                aiWebSockets.put(talker, webSocket);
                log("WebSocket opened for talker: " + talker);
                insertSystemMsg(talker, "小智AI 已连接", System.currentTimeMillis());
                
                // 发送初始化消息
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
                    
                    // 发送实际的第一个消息
                    sendMessageToWebSocket(talker, text);
                } catch (Exception e) {
                    log("Error sending initial WebSocket messages for " + talker + ": " + e.getMessage());
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
                        }
                    }
                } catch (Exception e) {
                    insertSystemMsg(talker, "小智AI 解析响应数据异常\n" + e.getMessage(), System.currentTimeMillis());
                }
            }

            public void onClosing(WebSocket webSocket, int code, String reason) {
                // 连接关闭时，从 Map 中移除
                aiWebSockets.remove(talker);
                log("WebSocket closing for talker: " + talker + ". Reason: " + reason);
                insertSystemMsg(talker, "小智AI 连接已关闭\n" + reason, System.currentTimeMillis());
            }

            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                // 连接失败时，从 Map 中移除
                aiWebSockets.remove(talker);
                log("WebSocket failure for talker: " + talker + ". Error: " + t.getMessage());
                StringBuilder errorInfo = new StringBuilder();
                errorInfo.append("Exception: ").append(t.getClass().getName()).append("\n");
                if (t.getMessage() != null) {
                    errorInfo.append("Message: ").append(t.getMessage()).append("\n");
                }
                insertSystemMsg(talker, "小智AI 连接中断\n" + errorInfo.toString(), System.currentTimeMillis());
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
        
        log("Attempting to create new WebSocket for talker: " + talker);
        // 异步发起连接，结果会在 listener 的 onOpen 或 onFailure 中回调
        aiClient.newWebSocket(requestBuilder.build(), listener);

    } catch (Exception e) {
        log("initializeWebSocketConnection error for " + talker + ": " + e.getMessage());
        insertSystemMsg(talker, "小智AI 连接失败: " + e.getMessage(), System.currentTimeMillis());
    }
}

// 【修改】发送消息到指定 talker 的 WebSocket
private void sendMessageToWebSocket(final String talker, String text) {
    try {
        WebSocket webSocket = aiWebSockets.get(talker);
        if (webSocket != null) {
            JSONObject socketMsg = new JSONObject();
            // 【重要】为每个会话使用独立的 session_id，避免后端混淆上下文
            socketMsg.put("session_id", "session_for_" + talker);
            socketMsg.put("type", "listen");
            socketMsg.put("state", "detect");
            socketMsg.put("text", text);
            webSocket.send(socketMsg.toString());
            log("Message sent to WebSocket for talker: " + talker);
        } else {
            // 如果连接不存在（可能意外断开），尝试重新连接
            log("sendMessageToWebSocket: WebSocket for " + talker + " is null, attempting to reconnect.");
            initializeWebSocketConnection(talker, text);
        }
    } catch (Exception e) {
        log("sendMessageToWebSocket error for " + talker + ": " + e.getMessage());
    }
}

// ===============================================================================
// =================== END: 小智bot 核心功能代码移植 ===================
// ===============================================================================

// ========== 智聊AI 功能模块 (移植自旧脚本) ==========

private void sendZhiliaAiReply(final String talker, String userContent) {
    // 日志入口
    log("=== 智聊AI触发: talker=" + talker + ", content=" + userContent + " ===");
    
    String apiKey = getString(ZHILIA_AI_API_KEY, "");
    String apiUrl = getString(ZHILIA_AI_API_URL, "https://api.siliconflow.cn/v1/chat/completions");
    String modelName = getString(ZHILIA_AI_MODEL_NAME, "deepseek-ai/DeepSeek-V3");
    String systemPrompt = getString(ZHILIA_AI_SYSTEM_PROMPT, "你是个宝宝");
    int contextLimit = getInt(ZHILIA_AI_CONTEXT_LIMIT, 10);

    if (TextUtils.isEmpty(apiKey)) {
        log("智聊AI: API Key 为空，跳过");
        toast("请先在智聊AI参数设置中配置API Key");
        return;
    }
    log("智聊AI: 配置OK - URL=" + apiUrl + ", Model=" + modelName);

    // 获取/创建历史
    List history = zhiliaConversationHistories.get(talker);
    if (history == null) {
        history = new ArrayList();
        log("智聊AI: 新建对话历史 for " + talker);
        if (!TextUtils.isEmpty(systemPrompt)) {
            Map systemMsg = new HashMap();
            systemMsg.put("role", "system");
            systemMsg.put("content", systemPrompt);
            history.add(systemMsg);
        }
        zhiliaConversationHistories.put(talker, history);
    }

    // 添加用户消息（移除@，统一小智逻辑）
    userContent = userContent.replaceAll("@[^\\s]+\\s+", "").trim();
    if (TextUtils.isEmpty(userContent)) {
        log("智聊AI: 用户内容为空后跳过");
        return;
    }
    Map userMsg = new HashMap();
    userMsg.put("role", "user");
    userMsg.put("content", userContent);
    history.add(userMsg);
    log("智聊AI: 添加用户消息，历史长度=" + history.size());

    // 限制上下文
    while (history.size() > contextLimit * 2 + 1) {
        history.remove(1); // 最旧用户
        if (history.size() > 1) history.remove(1); // 最旧AI
    }

    // 构建请求体（JSON）
    JSONObject jsonBody = new JSONObject();
    jsonBody.put("model", modelName);
    jsonBody.put("messages", history);
    jsonBody.put("temperature", 0.7);
    jsonBody.put("stream", false); // 非流式
    String requestData = jsonBody.toString();
    log("智聊AI: 请求体预览: " + requestData.substring(0, Math.min(200, requestData.length())) + "...");

    // 构建请求头
    Map headerMap = new HashMap();
    headerMap.put("Content-Type", "application/json");
    headerMap.put("Authorization", "Bearer " + apiKey);

    // 【核心修复】用 OkHttp 异步发送（绕过插件 post）
    RequestBody body = RequestBody.create(MediaType.parse("application/json"), requestData);
    Request.Builder reqBuilder = new Request.Builder().url(apiUrl).post(body);
    addHeaders(reqBuilder, headerMap); // 用现有工具添加头

    final Request request = reqBuilder.build();
    aiClient.newCall(request).enqueue(new okhttp3.Callback() {
        public void onFailure(okhttp3.Call call, IOException e) {
            log("智聊AI: OkHttp onFailure - " + e.getMessage());
            insertSystemMsg(talker, "智聊AI网络错误: " + e.getMessage(), System.currentTimeMillis());
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                public void run() {
                    toast("智聊AI请求失败: " + e.getMessage());
                }
            });
        }

        public void onResponse(okhttp3.Call call, Response response) throws IOException {
            String responseContent = response.body() != null ? response.body().string() : null;
            log("智聊AI: OkHttp onResponse (code=" + response.code() + "): " + responseContent);

            if (responseContent == null || !responseContent.trim().startsWith("{")) {
                log("智聊AI: 非JSON响应");
                insertSystemMsg(talker, "智聊AI响应无效", System.currentTimeMillis());
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    public void run() {
                        toast("智聊AI响应格式错误(非JSON)");
                    }
                });
                return;
            }

            try {
                JSONObject jsonObj = JSON.parseObject(responseContent);

                if (jsonObj.containsKey("error")) {
                    JSONObject errorObj = jsonObj.getJSONObject("error");
                    String errorMessage = errorObj.getString("message");
                    if (TextUtils.isEmpty(errorMessage)) errorMessage = "未知API错误";
                    log("智聊AI: API错误 - " + errorMessage);
                    insertSystemMsg(talker, "智聊AI API错误: " + errorMessage, System.currentTimeMillis());
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        public void run() {
                            toast("智聊AI请求失败: " + errorMessage);
                        }
                    });
                    return;
                }

                if (!jsonObj.containsKey("choices")) {
                    log("智聊AI: 缺少choices字段");
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        public void run() {
                            toast("智聊AI响应格式不正确");
                        }
                    });
                    return;
                }

                JSONArray choices = jsonObj.getJSONArray("choices");
                if (choices.size() > 0) {
                    JSONObject firstChoice = choices.getJSONObject(0);
                    JSONObject message = firstChoice.getJSONObject("message");
                    String msgContent = message.getString("content");
                    log("智聊AI: 解析成功，内容: " + msgContent);

                    if (!TextUtils.isEmpty(msgContent)) {
                        sendText(talker, msgContent);
                        log("智聊AI: 已发送回复到 " + talker);
                    } else {
                        log("智聊AI: 内容为空，fallback");
                        sendText(talker, "抱歉，我暂时无法回复。");
                    }

                    // 更新历史
                    Map assistantMsg = new HashMap();
                    assistantMsg.put("role", "assistant");
                    assistantMsg.put("content", msgContent != null ? msgContent : "默认回复");
                    history.add(assistantMsg);
                    zhiliaConversationHistories.put(talker, history);
                } else {
                    log("智聊AI: choices为空");
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        public void run() {
                            toast("智聊AI这次好像没想好怎么说。");
                        }
                    });
                    sendText(talker, "（AI思考中...）");
                }
            } catch (JSONException e) {
                log("智聊AI: JSON解析失败 - " + e.getMessage());
                insertSystemMsg(talker, "智聊AI解析错误: " + e.getMessage(), System.currentTimeMillis());
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    public void run() {
                        toast("无法解析智聊AI回复");
                    }
                });
            }
        }
    });
    log("=== 智聊AI OkHttp请求已发送 ===");
}

// ===============================================================================
// =================== END: 智聊AI 核心功能代码移植 ===================
// ===============================================================================

// 【修复】将AutoReplyRule改为Map<String, Object>结构，避免BeanShell类定义问题
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
    rule.put("compiledPattern", null); // Pattern对象，稍后编译
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
            log("Error compiling regex pattern for keyword: " + keyword + " - " + e.getMessage());
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
        log("Error parsing rule from string: '" + str + "' - " + e.getMessage());
        return null;
    }
    if (rule != null) {
        compileRegexPatternForRule(rule);
    }
    return rule;
}

// 好友回复项数据结构 (通用)
private class AcceptReplyItem {
    public int type;
    public String content;
    public long mediaDelaySeconds;  // 【新增】媒体发送间隔（秒）
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

// 反射工具类
// 完全重写反射工具方法，避免使用BeanShell
private String invokeStringMethod(Object obj, String methodName) {
    if (obj == null) {
        log("invokeStringMethod: obj is null for method: " + methodName);
        return "";
    }
    
    try {
        // 使用更安全的反射方式
        Class<?> clazz = obj.getClass();
        Method method = clazz.getMethod(methodName);
        Object result = method.invoke(obj);
        return result != null ? result.toString() : "";
    } catch (NoSuchMethodException e) {
        log("Method not found: " + methodName + " in class: " + obj.getClass().getName());
        // 尝试使用getField作为备选方案
        try {
            java.lang.reflect.Field field = obj.getClass().getField(methodName);
            Object result = field.get(obj);
            return result != null ? result.toString() : "";
        } catch (Exception ex) {
            log("Field also not found: " + methodName);
            return "";
        }
    } catch (Exception e) {
        log("Error invoking method: " + methodName + " - " + e.getMessage());
        return "";
    }
}

private boolean invokeBooleanMethod(Object obj, String methodName) {
    if (obj == null) {
        log("invokeBooleanMethod: obj is null for method: " + methodName);
        return false;
    }
    
    try {
        Method method = obj.getClass().getMethod(methodName);
        Object result = method.invoke(obj);
        return result != null && Boolean.parseBoolean(result.toString());
    } catch (Exception e) {
        log("Error invoking boolean method: " + methodName + " - " + e.getMessage());
        return false;
    }
}

private long invokeLongMethod(Object obj, String methodName) {
    if (obj == null) {
        log("invokeLongMethod: obj is null for method: " + methodName);
        return 0L;
    }
    
    try {
        Method method = obj.getClass().getMethod(methodName);
        Object result = method.invoke(obj);
        if (result instanceof Long) {
            return (Long) result;
        } else if (result instanceof Integer) {
            return (Integer) result;
        } else if (result != null) {
            try {
                return Long.parseLong(result.toString());
            } catch (NumberFormatException e) {
                return 0L;
            }
        }
        return 0L;
    } catch (Exception e) {
        log("Error invoking long method: " + methodName + " - " + e.getMessage());
        return 0L;
    }
}

public boolean onClickSendBtn(String text) {
    if ("自动回复设置".equals(text)) {
        showAutoReplySettingDialog();
        return true;
    }
    return false;
}

// ========== 核心功能：处理好友请求 ==========
public void onNewFriend(String wxid, String ticket, int scene) {
    if (!getBoolean(AUTO_ACCEPT_FRIEND_ENABLED_KEY, false)) {
        return;
    }

    verifyUser(wxid, ticket, scene);

    final String finalWxid = wxid;
    new Thread(new Runnable() {
        public void run() {
            try {
                long delay = getLong(AUTO_ACCEPT_DELAY_KEY, 2L);
                Thread.sleep(delay * 1000);

                List replyItems = getAutoAcceptReplyItems();

                for (int i = 0; i < replyItems.size(); i++) {
                    AcceptReplyItem item = (AcceptReplyItem) replyItems.get(i);
                    switch (item.type) {
                        case ACCEPT_REPLY_TYPE_TEXT:
                            String friendName = getFriendName(finalWxid);
                            if (friendName == null || friendName.isEmpty()) {
                                friendName = "朋友";
                            }
                            String finalText = item.content.replace("%friendName%", friendName);
                            if (!TextUtils.isEmpty(finalText)) {
                                sendText(finalWxid, finalText);
                            }
                            break;
                        case ACCEPT_REPLY_TYPE_IMAGE:
                        case ACCEPT_REPLY_TYPE_VIDEO:
                        case ACCEPT_REPLY_TYPE_EMOJI:
                        case ACCEPT_REPLY_TYPE_FILE:
                            if (!TextUtils.isEmpty(item.content)) {
                                // 【修改】支持多媒体顺序发送，使用自定义延迟
                                String[] paths = item.content.split(";;;");
                                for (int j = 0; j < paths.length; j++) {
                                    String path = paths[j].trim();
                                    if (!TextUtils.isEmpty(path)) {
                                        File file = new File(path);
                                        if (file.exists() && file.isFile()) {
                                            String fileName = file.getName();
                                            switch (item.type) {
                                                case ACCEPT_REPLY_TYPE_IMAGE:
                                                    sendImage(finalWxid, path);
                                                    break;
                                                case ACCEPT_REPLY_TYPE_VIDEO:
                                                    sendVideo(finalWxid, path);
                                                    break;
                                                case ACCEPT_REPLY_TYPE_EMOJI:
                                                    sendEmoji(finalWxid, path);
                                                    break;
                                                case ACCEPT_REPLY_TYPE_FILE:
                                                    shareFile(finalWxid, fileName, path, "");
                                                    break;
                                            }
                                            if (j < paths.length - 1) {
                                                Thread.sleep(item.mediaDelaySeconds * 1000); // 【新增】使用自定义延迟
                                            }
                                        }
                                    }
                                }
                            }
                            break;
                        case ACCEPT_REPLY_TYPE_VOICE_FIXED:
                            if (!TextUtils.isEmpty(item.content)) {
                                // 【修改】支持多语音顺序发送，使用自定义延迟
                                String[] voicePaths = item.content.split(";;;");
                                for (int j = 0; j < voicePaths.length; j++) {
                                    String voicePath = voicePaths[j].trim();
                                    if (!TextUtils.isEmpty(voicePath)) {
                                        sendVoice(finalWxid, voicePath);
                                        if (j < voicePaths.length - 1) {
                                            Thread.sleep(item.mediaDelaySeconds * 1000); // 【新增】使用自定义延迟
                                        }
                                    }
                                }
                            }
                            break;
                        case ACCEPT_REPLY_TYPE_VOICE_RANDOM:
                            if (!TextUtils.isEmpty(item.content)) {
                                List voiceFiles = getVoiceFilesFromFolder(item.content);
                                if (voiceFiles != null && !voiceFiles.isEmpty()) {
                                    String randomVoicePath = (String) voiceFiles.get(new Random().nextInt(voiceFiles.size()));
                                    sendVoice(finalWxid, randomVoicePath);
                                }
                            }
                            break;
                        case ACCEPT_REPLY_TYPE_CARD:
                            if (!TextUtils.isEmpty(item.content)) {
                                // 【修改】支持多名片顺序发送，使用自定义延迟
                                String[] wxids = item.content.split(";;;");
                                for (int j = 0; j < wxids.length; j++) {
                                    String wxidToShare = wxids[j].trim();
                                    if (!TextUtils.isEmpty(wxidToShare)) {
                                        sendShareCard(finalWxid, wxidToShare);
                                        if (j < wxids.length - 1) {
                                            Thread.sleep(item.mediaDelaySeconds * 1000); // 【新增】使用自定义延迟
                                        }
                                    }
                                }
                            }
                            break;
                    }

                    if (i < replyItems.size() - 1) {
                        Thread.sleep(1000);
                    }
                }
            } catch (Exception e) {
                log("发送好友欢迎消息失败：" + e.toString());
            }
        }
    }).start();
}

// 通用保存回复列表的方法
private void saveReplyItems(List items, String key) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < items.size(); i++) {
        if (i > 0) {
            sb.append(LIST_SEPARATOR);
        }
        sb.append(((AcceptReplyItem)items.get(i)).toString());
    }
    putString(key, sb.toString());
}

// 通用读取回复列表的方法
private List getReplyItems(String key, String defaultReplyText) {
    List items = new ArrayList();
    String savedItemsStr = getString(key, "");

    if (TextUtils.isEmpty(savedItemsStr)) {
        items.add(new AcceptReplyItem(ACCEPT_REPLY_TYPE_TEXT, defaultReplyText));
    } else {
        String[] itemsArray = savedItemsStr.split(LIST_SEPARATOR);
        for (int i = 0; i < itemsArray.length; i++) {
            AcceptReplyItem item = AcceptReplyItem.fromString(itemsArray[i]);
            if (item != null) {
                items.add(item);
            }
        }
    }
    return items;
}

// 获取自动通过好友的回复项列表
private List getAutoAcceptReplyItems() {
    return getReplyItems(AUTO_ACCEPT_REPLY_ITEMS_KEY, "%friendName%✨ 你好，很高兴认识你！");
}

// 保存自动通过好友的回复项列表
private void saveAutoAcceptReplyItems(List items) {
    saveReplyItems(items, AUTO_ACCEPT_REPLY_ITEMS_KEY);
}

// 获取被通过后自动回复的列表
private List getGreetOnAcceptedReplyItems() {
    return getReplyItems(GREET_ON_ACCEPTED_REPLY_ITEMS_KEY, "哈喽，%friendName%！感谢通过好友请求，以后请多指教啦！");
}

// 保存被通过后自动回复的列表
private void saveGreetOnAcceptedReplyItems(List items) {
    saveReplyItems(items, GREET_ON_ACCEPTED_REPLY_ITEMS_KEY);
}

public void onHandleMsg(final Object msgInfoBean) {
    log("onHandleMsg: Start processing message.");
    try {
        // --- 处理“我添加好友被通过”的逻辑 ---
        if (getBoolean(GREET_ON_ACCEPTED_ENABLED_KEY, false)
            && invokeBooleanMethod(msgInfoBean, "isText")
            && !invokeBooleanMethod(msgInfoBean, "isSend")) {

            String content = invokeStringMethod(msgInfoBean, "getContent");
            log("onHandleMsg: Received text message. Content: " + content);

            if (FRIEND_ADD_SUCCESS_KEYWORD.equals(content)) {
                log("onHandleMsg: Matched friend acceptance keyword. Processing auto-reply.");
                final String newFriendWxid = invokeStringMethod(msgInfoBean, "getTalker");

                new Thread(new Runnable() {
                    public void run() {
                        try {
                            long delay = getLong(GREET_ON_ACCEPTED_DELAY_KEY, 2L);
                            Thread.sleep(delay * 1000);

                            List replyItems = getGreetOnAcceptedReplyItems();

                            for (int i = 0; i < replyItems.size(); i++) {
                                AcceptReplyItem item = (AcceptReplyItem) replyItems.get(i);
                                switch (item.type) {
                                    case ACCEPT_REPLY_TYPE_TEXT:
                                        String friendName = getFriendName(newFriendWxid);
                                        if (friendName == null || friendName.isEmpty()) {
                                            friendName = "朋友";
                                        }
                                        String finalText = item.content.replace("%friendName%", friendName);
                                        if (!TextUtils.isEmpty(finalText)) {
                                            sendText(newFriendWxid, finalText);
                                        }
                                        break;
                                    case ACCEPT_REPLY_TYPE_IMAGE:
                                    case ACCEPT_REPLY_TYPE_VIDEO:
                                    case ACCEPT_REPLY_TYPE_EMOJI:
                                    case ACCEPT_REPLY_TYPE_FILE:
                                        if (!TextUtils.isEmpty(item.content)) {
                                            // 【修改】支持多媒体顺序发送，使用自定义延迟
                                            String[] paths = item.content.split(";;;");
                                            for (int j = 0; j < paths.length; j++) {
                                                String path = paths[j].trim();
                                                if (!TextUtils.isEmpty(path)) {
                                                    File file = new File(path);
                                                    if (file.exists() && file.isFile()) {
                                                        String fileName = file.getName();
                                                        switch (item.type) {
                                                            case ACCEPT_REPLY_TYPE_IMAGE:
                                                                sendImage(newFriendWxid, path);
                                                                break;
                                                            case ACCEPT_REPLY_TYPE_VIDEO:
                                                                sendVideo(newFriendWxid, path);
                                                                break;
                                                            case ACCEPT_REPLY_TYPE_EMOJI:
                                                                sendEmoji(newFriendWxid, path);
                                                                break;
                                                            case ACCEPT_REPLY_TYPE_FILE:
                                                                shareFile(newFriendWxid, fileName, path, "");
                                                                break;
                                                        }
                                                        if (j < paths.length - 1) {
                                                            Thread.sleep(item.mediaDelaySeconds * 1000); // 【新增】使用自定义延迟
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        break;
                                    case ACCEPT_REPLY_TYPE_VOICE_FIXED:
                                        if (!TextUtils.isEmpty(item.content)) {
                                            // 【修改】支持多语音顺序发送，使用自定义延迟
                                            String[] voicePaths = item.content.split(";;;");
                                            for (int j = 0; j < voicePaths.length; j++) {
                                                String voicePath = voicePaths[j].trim();
                                                if (!TextUtils.isEmpty(voicePath)) {
                                                    sendVoice(newFriendWxid, voicePath);
                                                    if (j < voicePaths.length - 1) {
                                                        Thread.sleep(item.mediaDelaySeconds * 1000); // 【新增】使用自定义延迟
                                                    }
                                                }
                                            }
                                        }
                                        break;
                                    case ACCEPT_REPLY_TYPE_VOICE_RANDOM:
                                        if (!TextUtils.isEmpty(item.content)) {
                                            List voiceFiles = getVoiceFilesFromFolder(item.content);
                                            if (voiceFiles != null && !voiceFiles.isEmpty()) {
                                                String randomVoicePath = (String) voiceFiles.get(new Random().nextInt(voiceFiles.size()));
                                                sendVoice(newFriendWxid, randomVoicePath);
                                            }
                                        }
                                        break;
                                    case ACCEPT_REPLY_TYPE_CARD:
                                        if (!TextUtils.isEmpty(item.content)) {
                                            // 【修改】支持多名片顺序发送，使用自定义延迟
                                            String[] wxids = item.content.split(";;;");
                                            for (int j = 0; j < wxids.length; j++) {
                                                String wxidToShare = wxids[j].trim();
                                                if (!TextUtils.isEmpty(wxidToShare)) {
                                                    sendShareCard(newFriendWxid, wxidToShare);
                                                    if (j < wxids.length - 1) {
                                                        Thread.sleep(item.mediaDelaySeconds * 1000); // 【新增】使用自定义延迟
                                                    }
                                                }
                                            }
                                        }
                                        break;
                                }

                                if (i < replyItems.size() - 1) {
                                    Thread.sleep(1000);
                                }
                            }
                            log("onHandleMsg: Successfully sent all welcome messages to new friend.");
                        } catch (Exception e) {
                            log("发送好友通过欢迎消息失败：" + e.toString());
                        }
                    }
                }).start();
                return;
            }
        }
        // --- 常规关键词自动回复逻辑 ---
        // 【修复】修改过滤条件，允许拍一拍消息通过（即使是系统消息）
        boolean isTextMsg = invokeBooleanMethod(msgInfoBean, "isText");
        boolean isPatMsg = invokeBooleanMethod(msgInfoBean, "isPat");
        if ((!isTextMsg && !isPatMsg) || invokeBooleanMethod(msgInfoBean, "isSend") || invokeBooleanMethod(msgInfoBean, "isSystem")) {
            log("onHandleMsg: Message is not a text or pat, is sent by self, or is a system message. Skipping auto-reply.");
            return;
        }

        String content = invokeStringMethod(msgInfoBean, "getContent");
        String talker = invokeStringMethod(msgInfoBean, "getTalker");
        String senderWxid = invokeStringMethod(msgInfoBean, "getSendTalker");

        log("onHandleMsg: Processing regular auto-reply logic. Content: " + content + ", Talker: " + talker + ", Sender: " + senderWxid);

        if (TextUtils.isEmpty(content) && !isPatMsg) {  // 【新增】对于拍一拍，content可能为空，但允许通过
            log("onHandleMsg: Content is empty and not pat message. Skipping auto-reply.");
            return;
        }
        if (TextUtils.isEmpty(talker) || TextUtils.isEmpty(senderWxid)) {
            log("onHandleMsg: talker or sender is empty. Skipping auto-reply.");
            return;
        }

        if (shouldAutoReply(msgInfoBean)) {
            log("onHandleMsg: shouldAutoReply returned true. Processing reply.");
            processAutoReply(msgInfoBean);
            log("onHandleMsg: Auto-reply process completed.");
        } else {
            log("onHandleMsg: shouldAutoReply returned false. No auto-reply needed.");
        }
    } catch (Exception e) {
        log("自动回复消息处理异常: " + e.getMessage());
        e.printStackTrace();
    }
    log("onHandleMsg: End of message processing.");
}

private boolean shouldAutoReply(Object msgInfoBean) {
    try {
        boolean isPrivateChat = invokeBooleanMethod(msgInfoBean, "isPrivateChat");
        boolean isGroupChat = invokeBooleanMethod(msgInfoBean, "isGroupChat");
        if (isPrivateChat) {
            if (!getBoolean(AUTO_REPLY_FRIEND_ENABLED_KEY, false)) return false;
            Set enabledFriends = getStringSet(AUTO_REPLY_ENABLED_FRIENDS_KEY, new HashSet());
            String senderWxid = invokeStringMethod(msgInfoBean, "getSendTalker");
            if (!enabledFriends.contains(senderWxid)) return false;
        } else if (isGroupChat) {
            if (!getBoolean(AUTO_REPLY_GROUP_ENABLED_KEY, false)) return false;
            Set enabledGroups = getStringSet(AUTO_REPLY_ENABLED_GROUPS_KEY, new HashSet());
            String talker = invokeStringMethod(msgInfoBean, "getTalker");
            if (!enabledGroups.contains(talker)) return false;
        } else {
            return false;
        }
        return true;
    } catch (Exception e) {
        log("判断自动回复条件异常: " + e.getMessage());
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
        log("解析或比较时间范围时出错: " + e.getMessage());
        return true;
    }
}

private void processAutoReply(final Object msgInfoBean) {
    try {
        final String content = invokeStringMethod(msgInfoBean, "getContent");
        final String senderWxid = invokeStringMethod(msgInfoBean, "getSendTalker");
        final String talker = invokeStringMethod(msgInfoBean, "getTalker");
        final boolean isPrivateChat = invokeBooleanMethod(msgInfoBean, "isPrivateChat");
        final boolean isGroupChat = invokeBooleanMethod(msgInfoBean, "isGroupChat");
        final long msgId = invokeLongMethod(msgInfoBean, "getMsgId");
        
        boolean isAtMe = false;
        boolean isNotifyAll = false;
        if (isGroupChat) {
            isAtMe = invokeBooleanMethod(msgInfoBean, "isAtMe");
            isNotifyAll = invokeBooleanMethod(msgInfoBean, "isNotifyAll");
        }

        // 【新增】检查是否被拍一拍
        boolean isPatMe = false;
        String myWxid = getLoginWxid();
        boolean isPatMsg = invokeBooleanMethod(msgInfoBean, "isPat");
        if (isPatMsg) {
            Object patMsgObj = invokeObjectMethod(msgInfoBean, "getPatMsg"); // 假设有getPatMsg方法，需要反射获取
            if (patMsgObj != null) {
                String fromUser = invokeStringMethod(patMsgObj, "getFromUser");
                String pattedUser = invokeStringMethod(patMsgObj, "getPattedUser");
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
                    if (isPrivateChat && targetWxids.contains(senderWxid)) targetMatch = true;
                } else if (targetType == TARGET_TYPE_GROUP) {
                    if (isGroupChat && targetWxids.contains(talker)) targetMatch = true;
                } else if (targetType == TARGET_TYPE_BOTH) {
                    if ((isPrivateChat && targetWxids.contains(senderWxid)) || (isGroupChat && targetWxids.contains(talker))) targetMatch = true;
                }
                if (!targetMatch) continue;
            }

            Set excludedWxids = (Set) rule.get("excludedWxids");
            if (excludedWxids != null && !excludedWxids.isEmpty()) {
                if (isPrivateChat && excludedWxids.contains(senderWxid)) continue;
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

            // 【修复】拍一拍触发检查：如果规则指定被拍一拍，则继续（后续匹配中强制true）
            int patTriggerType = (Integer) rule.get("patTriggerType");
            if (patTriggerType == PAT_TRIGGER_ME && !isPatMe) {
                continue;
            }

            boolean isMatch = false;
            // 【修复】特殊处理拍一拍：如果规则指定被拍一拍触发，则强制匹配（忽略content匹配）
            if (isPatMsg && patTriggerType == PAT_TRIGGER_ME) {
                isMatch = true;
            } else {
                // 原有content匹配逻辑
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

        if (matchedRules.isEmpty()) return;

        for (int i = 0; i < matchedRules.size(); i++) {
            final Map<String, Object> finalRule = (Map<String, Object>) matchedRules.get(i);
            
            Runnable sendReplyTask = new Runnable() {
                public void run() {
                    String replyContent = buildReplyContent((String) finalRule.get("reply"), msgInfoBean);
                    int replyType = (Integer) finalRule.get("replyType");
                    switch (replyType) {
                        case REPLY_TYPE_XIAOZHI_AI:
                            processAIResponse(msgInfoBean);
                            break;
                        case REPLY_TYPE_ZHILIA_AI:
                            sendZhiliaAiReply(talker, content);
                            break;
                        case REPLY_TYPE_IMAGE:
                        case REPLY_TYPE_VIDEO:
                        case REPLY_TYPE_EMOJI:
                        case REPLY_TYPE_FILE:
                            List mediaPaths = (List) finalRule.get("mediaPaths");
                            if (mediaPaths != null && !mediaPaths.isEmpty()) {
                                // 【修改】支持多媒体顺序发送，使用自定义延迟
                                long mediaDelaySeconds = (Long) finalRule.get("mediaDelaySeconds");
                                for (int j = 0; j < mediaPaths.size(); j++) {
                                    String path = (String) mediaPaths.get(j);
                                    File file = new File(path);
                                    if (file.exists() && file.isFile()) {
                                        String fileName = file.getName();
                                        switch (replyType) {
                                            case REPLY_TYPE_IMAGE:
                                                sendImage(talker, path);
                                                break;
                                            case REPLY_TYPE_VIDEO:
                                                sendVideo(talker, path);
                                                break;
                                            case REPLY_TYPE_EMOJI:
                                                sendEmoji(talker, path);
                                                break;
                                            case REPLY_TYPE_FILE:
                                                shareFile(talker, fileName, path, "");
                                                break;
                                        }
                                        if (j < mediaPaths.size() - 1) {
                                            try { Thread.sleep(mediaDelaySeconds * 1000); } catch (Exception e) {} // 【新增】使用自定义延迟
                                        }
                                    }
                                }
                            }
                            break;
                        case REPLY_TYPE_VOICE_FILE_LIST:
                            List mediaPaths2 = (List) finalRule.get("mediaPaths");
                            if (mediaPaths2 != null && !mediaPaths2.isEmpty()) {
                                // 【修改】支持多语音顺序发送（原随机改为顺序），使用自定义延迟
                                long mediaDelaySeconds = (Long) finalRule.get("mediaDelaySeconds");
                                for (int j = 0; j < mediaPaths2.size(); j++) {
                                    String voicePath = (String) mediaPaths2.get(j);
                                    sendVoice(talker, voicePath);
                                    if (j < mediaPaths2.size() - 1) {
                                        try { Thread.sleep(mediaDelaySeconds * 1000); } catch (Exception e) {} // 【新增】使用自定义延迟
                                    }
                                }
                            }
                            break;
                        case REPLY_TYPE_VOICE_FOLDER:
                            List mediaPaths3 = (List) finalRule.get("mediaPaths");
                            if (mediaPaths3 != null && !mediaPaths3.isEmpty()) {
                                String folderPath = (String) mediaPaths3.get(0);
                                List voiceFiles = getVoiceFilesFromFolder(folderPath);
                                if (voiceFiles != null && !voiceFiles.isEmpty()) {
                                    // 【修改】随机发送一个语音文件
                                    String randomVoicePath = (String) voiceFiles.get(new Random().nextInt(voiceFiles.size()));
                                    sendVoice(talker, randomVoicePath);
                                }
                            }
                            break;
                        case REPLY_TYPE_CARD:
                             if (!TextUtils.isEmpty(replyContent)) {
                                // 【修改】支持多名片顺序发送，使用自定义延迟
                                long mediaDelaySeconds = (Long) finalRule.get("mediaDelaySeconds");
                                String[] wxids = replyContent.split(";;;");
                                for (int j = 0; j < wxids.length; j++) {
                                    String wxidToShare = wxids[j].trim();
                                    if (!TextUtils.isEmpty(wxidToShare)) {
                                        sendShareCard(talker, wxidToShare);
                                        if (j < wxids.length - 1) {
                                            try { Thread.sleep(mediaDelaySeconds * 1000); } catch (Exception e) {} // 【新增】使用自定义延迟
                                        }
                                    }
                                }
                            }
                            break;
                        case REPLY_TYPE_TEXT: default:
                            boolean replyAsQuote = (Boolean) finalRule.get("replyAsQuote");
                            if (replyAsQuote) {
                                sendQuoteMsg(talker, msgId, replyContent);
                            } else {
                                sendText(talker, replyContent);
                            }
                            break;
                    }
                }
            };

            long delaySeconds = (Long) finalRule.get("delaySeconds");
            if (delaySeconds > 0) {
                new Handler(Looper.getMainLooper()).postDelayed(sendReplyTask, delaySeconds * 1000L);
            } else {
                sendReplyTask.run();
            }
        }
    } catch (Exception e) {
        log("处理自动回复异常: " + e.getMessage());
        e.printStackTrace();
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
        log("获取好友显示名称异常: " + e.getMessage());
    }
    return getFriendName(friendWxid);
}

private String buildReplyContent(String template, Object msgInfoBean) {
    try {
        String result = template;
        String senderWxid = invokeStringMethod(msgInfoBean, "getSendTalker");
        String senderName = "";
        boolean isPrivateChat = invokeBooleanMethod(msgInfoBean, "isPrivateChat");
        boolean isGroupChat = invokeBooleanMethod(msgInfoBean, "isGroupChat");
        if (isPrivateChat) {
            senderName = getFriendDisplayName(senderWxid);
        } else if (isGroupChat) {
            String talker = invokeStringMethod(msgInfoBean, "getTalker");
            senderName = getFriendName(senderWxid, talker);
        }
        if (TextUtils.isEmpty(senderName)) senderName = "未知用户";
        result = result.replace("%senderName%", senderName).replace("%senderWxid%", senderWxid);
        
        // 【新增】%atSender% 变量：实际@发送者（仅群聊有效，替换为 [AtWx=%senderWxid%]）
        if (isGroupChat) {
            result = result.replace("%atSender%", "[AtWx=" + senderWxid + "]");
        } else {
            result = result.replace("%atSender%", ""); // 私聊时替换为空，避免无效语法
        }
        
        if (isGroupChat) {
            String talker = invokeStringMethod(msgInfoBean, "getTalker");
            String groupName = getGroupName(talker);
            result = result.replace("%groupName%", TextUtils.isEmpty(groupName) ? "未知群聊" : groupName);
        } else {
            result = result.replace("%groupName%", "");
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        result = result.replace("%time%", sdf.format(new Date()));
        return result;
    } catch (Exception e) {
        log("构建回复内容异常: " + e.getMessage());
        return template;
    }
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
    } catch (Exception e) {
        log("获取群聊名称异常: " + e.getMessage());
    }
    return "未知群聊";
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

// 【修改】创建开关：方框+√样式，左侧添加说明文本（颜色更明显：选中#4A90E2，未选中方框更明显）
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
    checkBox.setOnClickListener(listener);
    LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    checkParams.setMargins(16, 0, 0, 0);
    checkBox.setLayoutParams(checkParams);

    // 【修改】点击左侧说明文本也可以切换开关
    label.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            checkBox.toggle();
        }
    });

    // 【新增】点击整个行（任何位置）也可以切换开关
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

// 【新增】通用多选列表对话框
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
        Button friendSwitchButton = new Button(getTopActivity());
        friendSwitchButton.setText("👥 好友消息自动回复开关");
        styleUtilityButton(friendSwitchButton);
        managementCard.addView(friendSwitchButton);
        Button groupSwitchButton = new Button(getTopActivity());
        groupSwitchButton.setText("🏠 群聊消息自动回复开关");
        styleUtilityButton(groupSwitchButton);
        managementCard.addView(groupSwitchButton);
        rootLayout.addView(managementCard);

        // --- 对话框构建 ---
        final AlertDialog dialog = buildCommonAlertDialog(getTopActivity(), "✨ 自动回复统一设置 ✨", scrollView, null, null, "❌ 关闭", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
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

        friendSwitchButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showFriendSwitchDialog();
            }
        });

        groupSwitchButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showGroupSwitchDialog();
            }
        });

        dialog.show();

    } catch (Exception e) {
        toast("打开设置界面失败: " + e.getMessage());
    }
}

// 新增：AI选择对话框
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

// 小智AI配置 (原有)
private void showXiaozhiAIConfigDialog() {
    showAIConfigDialog();
}

// 智聊AI配置 (移植自旧脚本，调整UI风格)
private void showZhiliaAIConfigDialog() {
    try {
        ScrollView scrollView = new ScrollView(getTopActivity());
        LinearLayout layout = new LinearLayout(getTopActivity());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 24, 24, 24);
        layout.setBackgroundColor(Color.parseColor("#FAFBF9"));
        scrollView.addView(layout);

        // --- 卡片1: API配置 ---
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

        // --- 卡片2: 高级设置 ---
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
                    putInt(ZHILIA_AI_CONTEXT_LIMIT, 10); // Default value on error
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

// ========== 通用回复序列设置对话框 ==========
private void showReplySequenceDialog(String title, String enabledKey, String delayKey, String itemsKey, String defaultText, String promptText, String featureName) {
    try {
        ScrollView scrollView = new ScrollView(getTopActivity());
        LinearLayout rootLayout = new LinearLayout(getTopActivity());
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(24, 24, 24, 24);
        rootLayout.setBackgroundColor(Color.parseColor("#FAFBF9"));
        scrollView.addView(rootLayout);

        // --- 卡片1: 核心设置 ---
        LinearLayout coreSettingsCard = createCardLayout();
        coreSettingsCard.addView(createSectionTitle(featureName));
        final LinearLayout enabledSwitchRow = createSwitchRow(getTopActivity(), "启用" + featureName, getBoolean(enabledKey, false), new View.OnClickListener() {
            public void onClick(View v) {}
        });
        coreSettingsCard.addView(enabledSwitchRow);
        TextView prompt = createPromptText(promptText);
        coreSettingsCard.addView(prompt);
        rootLayout.addView(coreSettingsCard);

        // --- 卡片2: 回复序列 ---
        LinearLayout replyCard = createCardLayout();
        replyCard.addView(createSectionTitle("回复消息序列"));
        final ListView replyItemsListView = new ListView(getTopActivity());
        // 【优化】设置触摸事件，确保直接滚动
        setupListViewTouchForScroll(replyItemsListView);
        replyItemsListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        // 【V11】初始高度设为最小，避免空旷，后续动态调整
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

        // --- 卡片3: 延迟设置 ---
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
                    String contentPreview = item.content.length() > 20 ? 
                        item.content.substring(0, 20) + "..." : item.content;
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
                // 【V11】动态调整高度
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
        
        // --- 对话框构建 ---
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

// ========== UI：自动同意好友设置 ==========
private void showAutoAcceptFriendDialog() {
    showReplySequenceDialog("✨ 好友请求自动处理设置 ✨", AUTO_ACCEPT_FRIEND_ENABLED_KEY, AUTO_ACCEPT_DELAY_KEY, AUTO_ACCEPT_REPLY_ITEMS_KEY, 
                            "%friendName%✨ 你好，很高兴认识你！", "⚠️ 勾选后将自动通过所有好友请求，并发送欢迎消息", "自动同意好友");
}

// ========== UI：我添加好友被通过后，自动回复设置 ==========
private void showGreetOnAcceptedDialog() {
    showReplySequenceDialog("✨ 添加好友自动回复设置 ✨", GREET_ON_ACCEPTED_ENABLED_KEY, GREET_ON_ACCEPTED_DELAY_KEY, GREET_ON_ACCEPTED_REPLY_ITEMS_KEY, 
                            "哈喽，%friendName%！感谢通过好友请求，以后请多指教啦！", "⚠️ 勾选后，当好友通过你的请求时，将自动发送欢迎消息", "添加好友回复");
}

// 【新增】更新回复按钮可见性
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

// 【新增】获取回复类型字符串
private String getReplyTypeStr(int type) {
    switch (type) {
        case ACCEPT_REPLY_TYPE_TEXT: return "文本";
        case ACCEPT_REPLY_TYPE_IMAGE: return "图片";
        case ACCEPT_REPLY_TYPE_VOICE_FIXED: return "固定语音";
        case ACCEPT_REPLY_TYPE_VOICE_RANDOM: return "随机语音";
        case ACCEPT_REPLY_TYPE_EMOJI: return "表情";
        case ACCEPT_REPLY_TYPE_VIDEO: return "视频";
        case ACCEPT_REPLY_TYPE_CARD: return "名片"; // 支持多选
        case ACCEPT_REPLY_TYPE_FILE: return "文件";
        default: return "未知";
    }
}

// 通用：编辑回复项对话框（修复编辑逻辑，确保content更新）
private void showEditReplyItemDialog(final AcceptReplyItem item, final List itemsList, 
                                    final Runnable refreshCallback, final int editPosition, String featureName) {
    try {
        // 【修复】为编辑创建可变副本，但直接使用原item引用
        final AtomicReference<AcceptReplyItem> editableItemRef = new AtomicReference<AcceptReplyItem>(item);
        
        ScrollView scrollView = new ScrollView(getTopActivity());
        LinearLayout layout = new LinearLayout(getTopActivity());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 24, 24, 24);
        layout.setBackgroundColor(Color.parseColor("#FAFBF9"));
        scrollView.addView(layout);

        // --- 卡片1: 回复类型 ---
        LinearLayout typeCard = createCardLayout();
        typeCard.addView(createSectionTitle("回复类型"));
        final RadioGroup replyTypeGroup = createRadioGroup(getTopActivity(), LinearLayout.VERTICAL);
        final RadioButton typeTextRadio = createRadioButton(getTopActivity(), "📄文本");
        final RadioButton typeImageRadio = createRadioButton(getTopActivity(), "🖼️图片");
        final RadioButton typeVoiceFixedRadio = createRadioButton(getTopActivity(), "🎤固定语音");
        final RadioButton typeVoiceRandomRadio = createRadioButton(getTopActivity(), "🔀🎤随机语音");
        final RadioButton typeEmojiRadio = createRadioButton(getTopActivity(), "😊表情");
        final RadioButton typeVideoRadio = createRadioButton(getTopActivity(), "🎬视频");
        final RadioButton typeCardRadio = createRadioButton(getTopActivity(), "📇名片"); // 支持多选
        final RadioButton typeFileRadio = createRadioButton(getTopActivity(), "📁文件"); // 新增文件选项
        replyTypeGroup.addView(typeTextRadio);
        replyTypeGroup.addView(typeImageRadio);
        replyTypeGroup.addView(typeVoiceFixedRadio);
        replyTypeGroup.addView(typeVoiceRandomRadio);
        replyTypeGroup.addView(typeEmojiRadio);
        replyTypeGroup.addView(typeVideoRadio);
        replyTypeGroup.addView(typeCardRadio);
        replyTypeGroup.addView(typeFileRadio); // 新增
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
        
        // 【新增】媒体发送延迟设置
        final TextView mediaDelayLabel = new TextView(getTopActivity());
        mediaDelayLabel.setText("媒体发送间隔 (秒):");
        mediaDelayLabel.setTextSize(14);
        mediaDelayLabel.setTextColor(Color.parseColor("#333333"));
        mediaDelayLabel.setPadding(0, 0, 0, 16);
        final EditText mediaDelayEdit = createStyledEditText("默认为1秒", String.valueOf(editableItemRef.get().mediaDelaySeconds));
        mediaDelayEdit.setInputType(InputType.TYPE_CLASS_NUMBER);
        
        // 媒体选择布局
        final LinearLayout mediaLayout = new LinearLayout(getTopActivity());
        mediaLayout.setOrientation(LinearLayout.VERTICAL);
        mediaLayout.setPadding(0, 0, 0, 16);
        final TextView currentPathTv = new TextView(getTopActivity());
        // 【修复】初始显示具体路径列表（\n分隔），而非content的;;;格式
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
        
        // 【修改】媒体列表与顺序管理：使用simple_list_item_multiple_choice布局显示复选框，支持多选
        final LinearLayout mediaOrderLayout = new LinearLayout(getTopActivity());
        mediaOrderLayout.setOrientation(LinearLayout.VERTICAL);
        mediaOrderLayout.setPadding(0, 0, 0, 16);
        final ListView mediaListView = new ListView(getTopActivity());
        // 【修改】使用multiple_choice布局显示复选框
        final ArrayList<String> displayMediaList = new ArrayList<String>();
        mediaListView.setAdapter(new ArrayAdapter<String>(getTopActivity(), android.R.layout.simple_list_item_multiple_choice, displayMediaList));
        mediaListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        // 【优化】设置触摸事件，确保直接滚动
        setupListViewTouchForScroll(mediaListView);
        // 【V11】初始高度设为最小，避免空旷，后续动态调整
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
        
        // 【新增】名片选择布局（类似媒体，但选择好友Wxid）
        final LinearLayout cardLayout = new LinearLayout(getTopActivity());
        cardLayout.setOrientation(LinearLayout.VERTICAL);
        cardLayout.setPadding(0, 0, 0, 16);
        final TextView currentCardTv = new TextView(getTopActivity());
        // 【新增】初始显示选中的Wxid列表（\n分隔）
        StringBuilder initialCardDisplay = new StringBuilder();
        if (!TextUtils.isEmpty(editableItemRef.get().content)) {
            String[] wxidParts = editableItemRef.get().content.split(";;;");
            for (int k = 0; k < wxidParts.length; k++) {
                if (!TextUtils.isEmpty(wxidParts[k].trim())) {
                    initialCardDisplay.append(wxidParts[k].trim()).append("\n");
                }
            }
        }
        currentCardTv.setText(initialCardDisplay.toString().trim().isEmpty() ? "未选择名片" : initialCardDisplay.toString().trim());
        currentCardTv.setTextSize(14);
        currentCardTv.setTextColor(Color.parseColor("#666666"));
        currentCardTv.setPadding(0, 8, 0, 0);
        final Button selectCardBtn = new Button(getTopActivity());
        selectCardBtn.setText("选择名片好友（多选）");
        styleMediaSelectionButton(selectCardBtn);
        cardLayout.addView(currentCardTv);
        cardLayout.addView(selectCardBtn);
        
        // 【修改】名片列表与顺序管理：使用simple_list_item_multiple_choice布局显示复选框，支持多选
        final LinearLayout cardOrderLayout = new LinearLayout(getTopActivity());
        cardOrderLayout.setOrientation(LinearLayout.VERTICAL);
        cardOrderLayout.setPadding(0, 0, 0, 16);
        final ListView cardListView = new ListView(getTopActivity());
        // 【修改】使用multiple_choice布局显示复选框
        final ArrayList<String> displayCardList = new ArrayList<String>();
        cardListView.setAdapter(new ArrayAdapter<String>(getTopActivity(), android.R.layout.simple_list_item_multiple_choice, displayCardList));
        cardListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        // 【优化】设置触摸事件，确保直接滚动
        setupListViewTouchForScroll(cardListView);
        // 【V11】初始高度设为最小，避免空旷，后续动态调整
        LinearLayout.LayoutParams cardListParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(50));
        cardListView.setLayoutParams(cardListParams);
        cardOrderLayout.addView(cardListView);
        TextView cardOrderPrompt = createPromptText("选中名片后，使用下方按钮调整发送顺序（顺序发送，间隔自定义秒）");
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
        final List<String> cardWxids = new ArrayList<String>(); // 【新增】名片Wxid列表
        if (!TextUtils.isEmpty(editableItemRef.get().content)) {
            String[] wxidParts = editableItemRef.get().content.split(";;;");
            for (int k = 0; k < wxidParts.length; k++) {
                String wxid = wxidParts[k].trim();
                if (!TextUtils.isEmpty(wxid)) cardWxids.add(wxid);
            }
        }
        // 【新增】基于内容的选中集
        final Set<String> selectedMediaPaths = new HashSet<String>();
        final Set<String> selectedCardWxids = new HashSet<String>();
        final Runnable updateMediaList = new Runnable() {
            public void run() {
                displayMediaList.clear();
                for (int k = 0; k < mediaPaths.size(); k++) {
                    String path = mediaPaths.get(k);
                    String fileName = new File(path).getName(); // 【V9】只显示文件名
                    String display = (k + 1) + ". " + (fileName.length() > 30 ? fileName.substring(0, 30) + "..." : fileName);
                    displayMediaList.add(display);
                }
                ((ArrayAdapter<String>) mediaListView.getAdapter()).notifyDataSetChanged();
                mediaListView.clearChoices();
                mediaListView.requestLayout(); // 【新增】强制重绘，确保checked状态更新
                // 【V9】更新currentPathTv为文件名列表显示，而非完整路径
                StringBuilder pathDisplay = new StringBuilder();
                for (String path : mediaPaths) {
                    pathDisplay.append(new File(path).getName()).append("\n");
                }
                currentPathTv.setText(pathDisplay.toString().trim().isEmpty() ? "未选择媒体" : pathDisplay.toString().trim());
                editableItemRef.get().content = TextUtils.join(";;;", mediaPaths);
                // 【V11】动态调整高度
                adjustListViewHeight(mediaListView, mediaPaths.size());
                // 重新设置选中状态
                for (int k = 0; k < mediaPaths.size(); k++) {
                    if (selectedMediaPaths.contains(mediaPaths.get(k))) {
                        mediaListView.setItemChecked(k, true);
                    }
                }
                // 更新按钮可见性和启用状态
                updateOrderButtons(mediaListView, orderButtonsLayout, mediaPaths.size(), upButton, downButton, deleteButton);
            }
        };
        final Runnable updateCardList = new Runnable() { // 【新增】更新名片列表
            public void run() {
                displayCardList.clear();
                for (int k = 0; k < cardWxids.size(); k++) {
                    String wxid = cardWxids.get(k);
                    String display = (k + 1) + ". " + (wxid.length() > 30 ? wxid.substring(0, 30) + "..." : wxid);
                    displayCardList.add(display);
                }
                ((ArrayAdapter<String>) cardListView.getAdapter()).notifyDataSetChanged();
                cardListView.clearChoices();
                cardListView.requestLayout(); // 【新增】强制重绘，确保checked状态更新
                // 更新currentCardTv为Wxid列表显示
                StringBuilder cardDisplay = new StringBuilder();
                for (String wxid : cardWxids) {
                    cardDisplay.append(wxid).append("\n");
                }
                currentCardTv.setText(cardDisplay.toString().trim().isEmpty() ? "未选择名片" : cardDisplay.toString().trim());
                editableItemRef.get().content = TextUtils.join(";;;", cardWxids);
                // 【V11】动态调整高度
                adjustListViewHeight(cardListView, cardWxids.size());
                // 重新设置选中状态
                for (int k = 0; k < cardWxids.size(); k++) {
                    if (selectedCardWxids.contains(cardWxids.get(k))) {
                        cardListView.setItemChecked(k, true);
                    }
                }
                // 更新按钮可见性和启用状态
                updateOrderButtons(cardListView, cardOrderButtonsLayout, cardWxids.size(), cardUpButton, cardDownButton, cardDeleteButton);
            }
        };
        updateMediaList.run();
        updateCardList.run(); // 【新增】
        
        final Runnable updateInputs = new Runnable() {
            public void run() {
                int type = editableItemRef.get().type;
                boolean isTextType = (type == ACCEPT_REPLY_TYPE_TEXT);
                boolean isMediaType = !isTextType && (type != ACCEPT_REPLY_TYPE_CARD);
                boolean isCardType = (type == ACCEPT_REPLY_TYPE_CARD);
                contentLabel.setVisibility(isTextType ? View.VISIBLE : View.GONE);
                contentEdit.setVisibility(isTextType ? View.VISIBLE : View.GONE);
                mediaDelayLabel.setVisibility(isMediaType || isCardType ? View.VISIBLE : View.GONE);
                mediaDelayEdit.setVisibility(isMediaType || isCardType ? View.VISIBLE : View.GONE);
                mediaLayout.setVisibility(isMediaType ? View.VISIBLE : View.GONE);
                mediaOrderLayout.setVisibility(isMediaType ? View.VISIBLE : View.GONE);
                cardLayout.setVisibility(isCardType ? View.VISIBLE : View.GONE); // 【新增】
                cardOrderLayout.setVisibility(isCardType ? View.VISIBLE : View.GONE); // 【新增】
                if (type == ACCEPT_REPLY_TYPE_TEXT) {
                    contentLabel.setText("文本内容 (可用 %friendName%):");
                    contentEdit.setHint("输入欢迎文本...");
                } else if (type == ACCEPT_REPLY_TYPE_IMAGE) {
                    contentLabel.setText("图片路径:");
                    contentEdit.setHint("输入图片绝对路径");
                    selectMediaBtn.setText("选择图片文件（多选）");
                } else if (type == ACCEPT_REPLY_TYPE_VOICE_FIXED) {
                    contentLabel.setText("语音文件路径:");
                    contentEdit.setHint("输入语音文件绝对路径");
                    selectMediaBtn.setText("选择语音文件（多选）"); // 【修改】支持多选
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
                } else if (type == ACCEPT_REPLY_TYPE_CARD) { // 【修改】名片改为多选
                    contentLabel.setText("名片 Wxid 列表:");
                    contentEdit.setHint("输入要分享的名片的Wxid（多选用;;;分隔）");
                    selectCardBtn.setText("选择名片好友（多选）");
                } else if (type == ACCEPT_REPLY_TYPE_FILE) {
                    contentLabel.setText("文件路径:");
                    contentEdit.setHint("输入文件绝对路径");
                    selectMediaBtn.setText("选择文件（多选）");
                }
                // 【修复】每次更新时重新设置tag，确保选择按钮可用
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
            case ACCEPT_REPLY_TYPE_FILE: replyTypeGroup.check(typeFileRadio.getId()); break; // 新增
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
                else if (checkedId == typeFileRadio.getId()) editableItemRef.get().type = ACCEPT_REPLY_TYPE_FILE; // 新增
                updateInputs.run();
            }
        });
        
        layout.addView(mediaDelayLabel);
        layout.addView(mediaDelayEdit);
        
        // 媒体选择按钮逻辑
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
        
        // 【新增】名片选择按钮逻辑：多选好友Wxid
        selectCardBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
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
                                    // 【新增】显示ID（完整ID）
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
        });
        
        // 【修改】媒体顺序管理逻辑：支持多选，动态更新按钮
        mediaListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String path = mediaPaths.get(position);
                if (mediaListView.isItemChecked(position)) {
                    selectedMediaPaths.add(path);
                } else {
                    selectedMediaPaths.remove(path);
                }
                // 更新按钮可见性和启用状态
                updateOrderButtons(mediaListView, orderButtonsLayout, mediaPaths.size(), upButton, downButton, deleteButton);
            }
        });
        upButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (selectedMediaPaths.size() == 1) {
                    String selectedPath = selectedMediaPaths.iterator().next();
                    int pos = mediaPaths.indexOf(selectedPath);
                    if (pos > 0) {
                        // 交换位置
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
                        // 交换位置
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
        
        // 【修改】名片顺序管理逻辑：支持多选，动态更新按钮
        cardListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String wxid = cardWxids.get(position);
                if (cardListView.isItemChecked(position)) {
                    selectedCardWxids.add(wxid);
                } else {
                    selectedCardWxids.remove(wxid);
                }
                // 更新按钮可见性和启用状态
                updateOrderButtons(cardListView, cardOrderButtonsLayout, cardWxids.size(), cardUpButton, cardDownButton, cardDeleteButton);
            }
        });
        cardUpButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (selectedCardWxids.size() == 1) {
                    String selectedWxid = selectedCardWxids.iterator().next();
                    int pos = cardWxids.indexOf(selectedWxid);
                    if (pos > 0) {
                        // 交换位置
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
                        // 交换位置
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
        layout.addView(cardLayout); // 【新增】
        layout.addView(cardOrderLayout); // 【新增】
        
        String dialogTitle = (editPosition >= 0) ? "编辑回复项 (" + featureName + ")" : "添加回复项 (" + featureName + ")";
        final AlertDialog dialog = buildCommonAlertDialog(getTopActivity(), dialogTitle, scrollView, "✅ 保存", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                int type = editableItemRef.get().type;
                long mediaDelay = 1L;
                try {
                    mediaDelay = Long.parseLong(mediaDelayEdit.getText().toString().trim());
                } catch (Exception e) {
                    mediaDelay = 1L; // 默认值
                }
                editableItemRef.get().mediaDelaySeconds = mediaDelay;
                
                if (type == ACCEPT_REPLY_TYPE_TEXT) {
                    editableItemRef.get().content = contentEdit.getText().toString().trim();
                    if (TextUtils.isEmpty(editableItemRef.get().content)) {
                        toast("内容不能为空");
                        return;
                    }
                } else if (type == ACCEPT_REPLY_TYPE_CARD) {
                    editableItemRef.get().content = TextUtils.join(";;;", cardWxids);
                    if (cardWxids.isEmpty()) {
                        toast("名片Wxid不能为空");
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
                            type == ACCEPT_REPLY_TYPE_FILE) { // 新增文件检查
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
                
                // 【修复】更新list中的对象引用
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

// 【新增】获取ListView选中位置列表（从大到小排序，便于删除）
private List<Integer> getSelectedPositions(ListView listView) {
    List<Integer> selected = new ArrayList<Integer>();
    for (int i = 0; i < listView.getCount(); i++) {
        if (listView.isItemChecked(i)) {
            selected.add(i);
        }
    }
    // 从大到小排序
    java.util.Collections.sort(selected, java.util.Collections.reverseOrder());
    return selected;
}

// 【新增】更新顺序按钮可见性和启用状态
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

// 【新增】根据类型获取媒体选择tag
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
            isMulti = true; // 【修改】支持多选
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
            extFilter = ""; // 所有文件类型
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

        // --- 卡片1: 规则列表 ---
        LinearLayout rulesCard = createCardLayout();
        rulesCard.addView(createSectionTitle("📝 自动回复规则管理"));
        final ListView rulesListView = new ListView(getTopActivity());
        // 【优化】设置触摸事件，确保直接滚动
        setupListViewTouchForScroll(rulesListView);
        rulesListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        // 【V11】初始高度设为最小，避免空旷，后续动态调整
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
                    String patTriggerStr = getPatTriggerStr(patTriggerType); // 【新增】拍一拍触发字符串
                    Set targetWxids = (Set) rule.get("targetWxids");
                    int targetType = (Integer) rule.get("targetType");
                    String targetInfo = getTargetInfo(targetType, targetWxids);
                    int replyType = (Integer) rule.get("replyType");
                    String replyTypeStr = getReplyTypeStrForRule(replyType);
                    String replyContentPreview = getReplyContentPreview(rule);
                    long delaySeconds = (Long) rule.get("delaySeconds");
                    String delayInfo = (delaySeconds > 0) ? " 延迟" + delaySeconds + "秒" : "";
                    long mediaDelaySeconds = (Long) rule.get("mediaDelaySeconds");
                    String mediaDelayInfo = (mediaDelaySeconds > 1) ? " 媒体间隔" + mediaDelaySeconds + "秒" : ""; // 【新增】显示媒体延迟
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
                // 【V11】动态调整高度
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

        // --- 对话框构建 ---
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

// 【新增】辅助方法：获取拍一拍触发字符串
private String getPatTriggerStr(int patTriggerType) {
    if (patTriggerType == PAT_TRIGGER_ME) return "被拍一拍";
    else return "不限拍一拍";
}

// 【新增】辅助方法：获取匹配类型字符串
private String getMatchTypeStr(int matchType) {
    if (matchType == MATCH_TYPE_EXACT) return "全字";
    else if (matchType == MATCH_TYPE_REGEX) return "正则";
    else if (matchType == MATCH_TYPE_ANY) return "任何消息";
    else return "模糊";
}

// 【新增】辅助方法：获取@触发字符串
private String getAtTriggerStr(int atTriggerType) {
    if (atTriggerType == AT_TRIGGER_ME) return "@我";
    else if (atTriggerType == AT_TRIGGER_ALL) return "@全体";
    else return "不限@";
}

// 【新增】辅助方法：获取目标信息
private String getTargetInfo(int targetType, Set targetWxids) {
    if (targetType == TARGET_TYPE_FRIEND) return " (指定好友: " + (targetWxids != null ? targetWxids.size() : 0) + "人)";
    else if (targetType == TARGET_TYPE_GROUP) return " (指定群聊: " + (targetWxids != null ? targetWxids.size() : 0) + "个)";
    else if (targetType == TARGET_TYPE_BOTH) return " (指定好友/群聊: " + (targetWxids != null ? targetWxids.size() : 0) + "个)";
    return "";
}

// 【新增】辅助方法：获取规则回复类型字符串 (区分小智和智聊AI)
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
        case REPLY_TYPE_CARD: return " [名片]"; // 支持多选
        default: return " [文本]";
    }
}

// 【新增】辅助方法：获取回复内容预览
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
                return " (" + mediaPaths.size() + "个): ..." + path.substring(Math.max(0, path.length() - 20));
            }
            return "未设置路径";
        case REPLY_TYPE_VOICE_FILE_LIST:
            List mediaPaths2 = (List) rule.get("mediaPaths");
            if (mediaPaths2 != null && !mediaPaths2.isEmpty()) {
                String path = (String) mediaPaths2.get(0);
                return " (" + mediaPaths2.size() + "个语音): ..." + path.substring(Math.max(0, path.length() - 20));
            }
            return "未设置语音文件路径";
        case REPLY_TYPE_VOICE_FOLDER:
            List mediaPaths3 = (List) rule.get("mediaPaths");
            if (mediaPaths3 != null && !mediaPaths3.isEmpty()) {
                String path = (String) mediaPaths3.get(0);
                return "文件夹: ..." + path.substring(Math.max(0, path.length() - 20));
            }
            return "未设置语音文件夹路径";
        case REPLY_TYPE_CARD:
            String reply = (String) rule.get("reply");
            if (!TextUtils.isEmpty(reply)) {
                String[] wxids = reply.split(";;;");
                return " (" + wxids.length + "个): " + (reply.length() > 30 ? reply.substring(0, 30) + "..." : reply);
            }
            return "未设置Wxid";
        default: // REPLY_TYPE_TEXT
            String textReply = (String) rule.get("reply");
            return textReply.length() > 20 ? textReply.substring(0, 20) + "..." : textReply;
    }
}

// 【新增】辅助方法：获取时间信息
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
        
        // --- 卡片1: 关键词设置 ---
        LinearLayout keywordCard = createCardLayout();
        keywordCard.addView(createSectionTitle("关键词"));
        final EditText keywordEdit = createStyledEditText("输入触发关键词...", (String) rule.get("keyword"));
        keywordCard.addView(keywordEdit);
        layout.addView(keywordCard);
        
        // --- 卡片2: 回复类型 ---
        LinearLayout typeCard = createCardLayout();
        typeCard.addView(createSectionTitle("回复类型"));
        final RadioGroup replyTypeGroup = createRadioGroup(getTopActivity(), LinearLayout.VERTICAL);
        final RadioButton replyTypeXiaozhiAIRadio = createRadioButton(getTopActivity(), "🤖 小智AI 回复(回复快,能联网)");
        final RadioButton replyTypeZhiliaAIRadio = createRadioButton(getTopActivity(), "🧠 智聊AI 回复(回复慢,不能联网,可以用deepseek官方key官方配置即可联网)"); // 新增智聊AI选项
        final RadioButton replyTypeTextRadio = createRadioButton(getTopActivity(), "📄文本");
        final RadioButton replyTypeImageRadio = createRadioButton(getTopActivity(), "🖼️图片");
        final RadioButton replyTypeEmojiRadio = createRadioButton(getTopActivity(), "😊表情");
        final RadioButton replyTypeVideoRadio = createRadioButton(getTopActivity(), "🎬视频");
        final RadioButton replyTypeCardRadio = createRadioButton(getTopActivity(), "📇名片"); // 支持多选
        final RadioButton replyTypeVoiceFileListRadio = createRadioButton(getTopActivity(), "🎤语音(文件列表)");
        final RadioButton replyTypeVoiceFolderRadio = createRadioButton(getTopActivity(), "🔀🎤语音(文件夹随机)");
        final RadioButton replyTypeFileRadio = createRadioButton(getTopActivity(), "📁文件"); // 新增文件选项
        replyTypeGroup.addView(replyTypeXiaozhiAIRadio);
        replyTypeGroup.addView(replyTypeZhiliaAIRadio); // 新增
        replyTypeGroup.addView(replyTypeTextRadio);
        replyTypeGroup.addView(replyTypeImageRadio);
        replyTypeGroup.addView(replyTypeEmojiRadio);
        replyTypeGroup.addView(replyTypeVideoRadio);
        replyTypeGroup.addView(replyTypeCardRadio);
        replyTypeGroup.addView(replyTypeVoiceFileListRadio);
        replyTypeGroup.addView(replyTypeVoiceFolderRadio);
        replyTypeGroup.addView(replyTypeFileRadio); // 新增
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
        
        // 【新增】媒体发送延迟设置
        final TextView mediaDelayLabel = new TextView(getTopActivity());
        mediaDelayLabel.setText("媒体发送间隔 (秒):");
        mediaDelayLabel.setTextSize(14);
        mediaDelayLabel.setTextColor(Color.parseColor("#333333"));
        mediaDelayLabel.setPadding(0, 0, 0, 16);
        final EditText mediaDelayEdit = createStyledEditText("默认为1秒", String.valueOf(rule.get("mediaDelaySeconds")));
        mediaDelayEdit.setInputType(InputType.TYPE_CLASS_NUMBER);
        
        // 媒体选择布局
        final LinearLayout mediaLayout = new LinearLayout(getTopActivity());
        mediaLayout.setOrientation(LinearLayout.VERTICAL);
        mediaLayout.setPadding(0, 0, 0, 16);
        final TextView currentMediaTv = new TextView(getTopActivity());
        // 【修复】初始显示具体路径列表（\n分隔），而非mediaPaths的;;;格式 + null检查
        StringBuilder initialMediaDisplay = new StringBuilder();
        Object mediaObj = rule.get("mediaPaths");
        List mediaPathsInit = (mediaObj instanceof List) ? (List) mediaObj : null;
        if (mediaPathsInit != null && !mediaPathsInit.isEmpty()) {
            for (int i = 0; i < mediaPathsInit.size(); i++) {
                Object pObj = mediaPathsInit.get(i);
                if (pObj instanceof String) {
                    String p = (String) pObj;
                    if (!TextUtils.isEmpty(p)) {
                        initialMediaDisplay.append(new File(p).getName()).append("\n"); // 【V9】只显示文件名
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
        
        // 【修改】媒体列表与顺序管理：使用simple_list_item_multiple_choice布局显示复选框，支持多选
        final LinearLayout mediaOrderLayout = new LinearLayout(getTopActivity());
        mediaOrderLayout.setOrientation(LinearLayout.VERTICAL);
        mediaOrderLayout.setPadding(0, 0, 0, 16);
        final ListView mediaListView = new ListView(getTopActivity());
        // 【修改】使用multiple_choice布局显示复选框
        final ArrayList<String> displayMediaList = new ArrayList<String>();
        mediaListView.setAdapter(new ArrayAdapter<String>(getTopActivity(), android.R.layout.simple_list_item_multiple_choice, displayMediaList));
        mediaListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        // 【优化】设置触摸事件，确保直接滚动
        setupListViewTouchForScroll(mediaListView);
        // 【V11】初始高度设为最小，避免空旷，后续动态调整
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
        
        // 【新增】名片选择布局（类似媒体，但选择好友Wxid）
        final LinearLayout cardLayout = new LinearLayout(getTopActivity());
        cardLayout.setOrientation(LinearLayout.VERTICAL);
        cardLayout.setPadding(0, 0, 0, 16);
        final TextView currentCardTv = new TextView(getTopActivity());
        // 【新增】初始显示选中的Wxid列表（\n分隔）
        StringBuilder initialCardDisplay = new StringBuilder();
        String replyStr = (String) rule.get("reply");
        if (!TextUtils.isEmpty(replyStr)) {
            String[] wxidParts = replyStr.split(";;;");
            for (int k = 0; k < wxidParts.length; k++) {
                if (!TextUtils.isEmpty(wxidParts[k].trim())) {
                    initialCardDisplay.append(wxidParts[k].trim()).append("\n");
                }
            }
        }
        currentCardTv.setText(initialCardDisplay.toString().trim().isEmpty() ? "未选择名片" : initialCardDisplay.toString().trim());
        currentCardTv.setTextSize(14);
        currentCardTv.setTextColor(Color.parseColor("#666666"));
        currentCardTv.setPadding(0, 8, 0, 0);
        final Button selectCardBtn = new Button(getTopActivity());
        selectCardBtn.setText("选择名片好友（多选）");
        styleMediaSelectionButton(selectCardBtn);
        cardLayout.addView(currentCardTv);
        cardLayout.addView(selectCardBtn);
        
        // 【修改】名片列表与顺序管理：使用simple_list_item_multiple_choice布局显示复选框，支持多选
        final LinearLayout cardOrderLayout = new LinearLayout(getTopActivity());
        cardOrderLayout.setOrientation(LinearLayout.VERTICAL);
        cardOrderLayout.setPadding(0, 0, 0, 16);
        final ListView cardListView = new ListView(getTopActivity());
        // 【修改】使用multiple_choice布局显示复选框
        final ArrayList<String> displayCardList = new ArrayList<String>();
        cardListView.setAdapter(new ArrayAdapter<String>(getTopActivity(), android.R.layout.simple_list_item_multiple_choice, displayCardList));
        cardListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        // 【优化】设置触摸事件，确保直接滚动
        setupListViewTouchForScroll(cardListView);
        // 【V11】初始高度设为最小，避免空旷，后续动态调整
        LinearLayout.LayoutParams cardListParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(50));
        cardListView.setLayoutParams(cardListParams);
        cardOrderLayout.addView(cardListView);
        TextView cardOrderPrompt = createPromptText("选中名片后，使用下方按钮调整发送顺序（顺序发送，间隔自定义秒）");
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
        
        // 【修复】null检查 + 强制空列表
        Object mediaPathsObj = rule.get("mediaPaths");
        final List<String> mediaPaths = (mediaPathsObj instanceof List) ? new ArrayList<String>((List<String>) mediaPathsObj) : new ArrayList<String>();
        // 【新增】基于内容的选中集
        final Set<String> selectedMediaPaths = new HashSet<String>();
        final Runnable updateMediaList = new Runnable() {
            public void run() {
                displayMediaList.clear();
                for (int k = 0; k < mediaPaths.size(); k++) {
                    String path = mediaPaths.get(k);
                    String fileName = new File(path).getName(); // 【V9】只显示文件名
                    String display = (k + 1) + ". " + (fileName.length() > 30 ? fileName.substring(0, 30) + "..." : fileName);
                    displayMediaList.add(display);
                }
                ((ArrayAdapter<String>) mediaListView.getAdapter()).notifyDataSetChanged();
                mediaListView.clearChoices();
                mediaListView.requestLayout(); // 【新增】强制重绘，确保checked状态更新
                // 【V9】更新currentMediaTv为文件名列表显示，而非完整路径
                StringBuilder mediaDisplay = new StringBuilder();
                for (String path : mediaPaths) {
                    mediaDisplay.append(new File(path).getName()).append("\n");
                }
                currentMediaTv.setText(mediaDisplay.toString().trim().isEmpty() ? "未选择媒体" : mediaDisplay.toString().trim());
                rule.put("mediaPaths", new ArrayList<String>(mediaPaths)); // 更新规则的mediaPaths
                // 【V11】动态调整高度
                adjustListViewHeight(mediaListView, mediaPaths.size());
                // 重新设置选中状态
                for (int k = 0; k < mediaPaths.size(); k++) {
                    if (selectedMediaPaths.contains(mediaPaths.get(k))) {
                        mediaListView.setItemChecked(k, true);
                    }
                }
                // 更新按钮可见性和启用状态
                updateOrderButtons(mediaListView, orderButtonsLayout, mediaPaths.size(), upButton, downButton, deleteButton);
            }
        };
        final List<String> cardWxids = new ArrayList<String>(); // 【新增】名片Wxid列表
        String replyStrForCard = (String) rule.get("reply");
        if (!TextUtils.isEmpty(replyStrForCard)) {
            String[] wxidParts = replyStrForCard.split(";;;");
            for (int k = 0; k < wxidParts.length; k++) {
                String wxid = wxidParts[k].trim();
                if (!TextUtils.isEmpty(wxid)) cardWxids.add(wxid);
            }
        }
        // 【新增】基于内容的选中集 for card
        final Set<String> selectedCardWxids = new HashSet<String>();
        final Runnable updateCardList = new Runnable() { // 【新增】更新名片列表
            public void run() {
                displayCardList.clear();
                for (int k = 0; k < cardWxids.size(); k++) {
                    String wxid = cardWxids.get(k);
                    String display = (k + 1) + ". " + (wxid.length() > 30 ? wxid.substring(0, 30) + "..." : wxid);
                    displayCardList.add(display);
                }
                ((ArrayAdapter<String>) cardListView.getAdapter()).notifyDataSetChanged();
                cardListView.clearChoices();
                cardListView.requestLayout(); // 【新增】强制重绘，确保checked状态更新
                // 更新currentCardTv为Wxid列表显示
                StringBuilder cardDisplay = new StringBuilder();
                for (String wxid : cardWxids) {
                    cardDisplay.append(wxid).append("\n");
                }
                currentCardTv.setText(cardDisplay.toString().trim().isEmpty() ? "未选择名片" : cardDisplay.toString().trim());
                rule.put("reply", TextUtils.join(";;;", cardWxids)); // 【修复】更新规则的reply为Wxid列表
                // 【V11】动态调整高度
                adjustListViewHeight(cardListView, cardWxids.size());
                // 重新设置选中状态
                for (int k = 0; k < cardWxids.size(); k++) {
                    if (selectedCardWxids.contains(cardWxids.get(k))) {
                        cardListView.setItemChecked(k, true);
                    }
                }
                // 更新按钮可见性和启用状态
                updateOrderButtons(cardListView, cardOrderButtonsLayout, cardWxids.size(), cardUpButton, cardDownButton, cardDeleteButton);
            }
        };
        updateMediaList.run();
        updateCardList.run(); // 【新增】
        
        // 【修复】初始 tag 设置，确保媒体类型加载时 tag 已就位
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
                boolean isMediaType = !isTextType && (type != REPLY_TYPE_XIAOZHI_AI && type != REPLY_TYPE_ZHILIA_AI && type != REPLY_TYPE_CARD);
                boolean isCardType = (type == REPLY_TYPE_CARD);
                
                replyContentLabel.setVisibility(isTextType ? View.VISIBLE : View.GONE);
                replyEdit.setVisibility(isTextType ? View.VISIBLE : View.GONE);
                mediaDelayLabel.setVisibility(isMediaType || isCardType ? View.VISIBLE : View.GONE);
                mediaDelayEdit.setVisibility(isMediaType || isCardType ? View.VISIBLE : View.GONE);
                mediaLayout.setVisibility(isMediaType ? View.VISIBLE : View.GONE);
                mediaOrderLayout.setVisibility(isMediaType ? View.VISIBLE : View.GONE);
                cardLayout.setVisibility(isCardType ? View.VISIBLE : View.GONE); // 【新增】控制名片布局可见性
                cardOrderLayout.setVisibility(isCardType ? View.VISIBLE : View.GONE); // 【新增】控制名片顺序布局可见性
                
                final LinearLayout replyAsQuoteSwitchRow = (LinearLayout) layout.findViewWithTag("replyAsQuoteSwitchRow");
                if (replyAsQuoteSwitchRow != null) {
                    replyAsQuoteSwitchRow.setVisibility(type == REPLY_TYPE_TEXT ? View.VISIBLE : View.GONE);
                }
                final TextView quotePrompt = (TextView) layout.findViewWithTag("quotePrompt");
                if (quotePrompt != null) {
                    quotePrompt.setVisibility(type == REPLY_TYPE_TEXT ? View.VISIBLE : View.GONE);
                }
                
                if (type == REPLY_TYPE_CARD) { // 【修改】名片改为多选
                    replyContentLabel.setText("名片 Wxid 列表:");
                    replyEdit.setHint("输入要分享的名片的Wxid（多选用;;;分隔）");
                    selectCardBtn.setText("选择名片好友（多选）");
                } else if (type == REPLY_TYPE_XIAOZHI_AI || type == REPLY_TYPE_ZHILIA_AI) { // AI类型不显示输入
                    replyContentLabel.setVisibility(View.GONE);
                    replyEdit.setVisibility(View.GONE);
                    mediaLayout.setVisibility(View.GONE);
                    mediaOrderLayout.setVisibility(View.GONE);
                    mediaDelayLabel.setVisibility(View.GONE);
                    mediaDelayEdit.setVisibility(View.GONE);
                    cardLayout.setVisibility(View.GONE); // 【新增】AI类型隐藏名片
                    cardOrderLayout.setVisibility(View.GONE); // 【新增】AI类型隐藏名片顺序
                } else { // TEXT
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
                        extFilter = ""; // 所有文件
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
                // 【修复】每次更新时重新设置tag，确保选择按钮可用
                Object[] tag = new Object[]{extFilter, isFolder, allowFolder, isMulti};
                selectMediaBtn.setTag(tag);
                
                // 更新显示
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
            case REPLY_TYPE_ZHILIA_AI: replyTypeGroup.check(replyTypeZhiliaAIRadio.getId()); break; // 新增
            case REPLY_TYPE_IMAGE: replyTypeGroup.check(replyTypeImageRadio.getId()); break;
            case REPLY_TYPE_EMOJI: replyTypeGroup.check(replyTypeEmojiRadio.getId()); break;
            case REPLY_TYPE_VIDEO: replyTypeGroup.check(replyTypeVideoRadio.getId()); break;
            case REPLY_TYPE_CARD: replyTypeGroup.check(replyTypeCardRadio.getId()); break;
            case REPLY_TYPE_VOICE_FILE_LIST: replyTypeGroup.check(replyTypeVoiceFileListRadio.getId()); break;
            case REPLY_TYPE_VOICE_FOLDER: replyTypeGroup.check(replyTypeVoiceFolderRadio.getId()); break;
            case REPLY_TYPE_FILE: replyTypeGroup.check(replyTypeFileRadio.getId()); break; // 新增
            default: replyTypeGroup.check(replyTypeTextRadio.getId());
        }
        updateReplyInputVisibility.run();
        
        layout.addView(replyContentLabel);
        layout.addView(replyEdit);
        layout.addView(mediaDelayLabel);
        layout.addView(mediaDelayEdit);
        layout.addView(mediaLayout);
        layout.addView(mediaOrderLayout);
        layout.addView(cardLayout); // 【新增】
        layout.addView(cardOrderLayout); // 【新增】
        
        replyTypeGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == replyTypeXiaozhiAIRadio.getId()) rule.put("replyType", REPLY_TYPE_XIAOZHI_AI);
                else if (checkedId == replyTypeZhiliaAIRadio.getId()) rule.put("replyType", REPLY_TYPE_ZHILIA_AI); // 新增
                else if (checkedId == replyTypeTextRadio.getId()) rule.put("replyType", REPLY_TYPE_TEXT);
                else if (checkedId == replyTypeImageRadio.getId()) rule.put("replyType", REPLY_TYPE_IMAGE);
                else if (checkedId == replyTypeEmojiRadio.getId()) rule.put("replyType", REPLY_TYPE_EMOJI);
                else if (checkedId == replyTypeVideoRadio.getId()) rule.put("replyType", REPLY_TYPE_VIDEO);
                else if (checkedId == replyTypeCardRadio.getId()) rule.put("replyType", REPLY_TYPE_CARD);
                else if (checkedId == replyTypeVoiceFileListRadio.getId()) rule.put("replyType", REPLY_TYPE_VOICE_FILE_LIST);
                else if (checkedId == replyTypeVoiceFolderRadio.getId()) rule.put("replyType", REPLY_TYPE_VOICE_FOLDER);
                else if (checkedId == replyTypeFileRadio.getId()) rule.put("replyType", REPLY_TYPE_FILE); // 新增
                
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
        
        // 媒体选择按钮逻辑
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
                                    display.append(new File(path).getName()); // 【V9】只显示文件名
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
                                    display.append(new File(p).getName()).append("\n"); // 【V9】只显示文件名
                                }
                                currentMediaTv.setText(display.toString().trim());
                            } else {
                                if (!selectedFiles.isEmpty()) {
                                    mediaPaths.add(selectedFiles.get(0));
                                    currentMediaTv.setText(new File(selectedFiles.get(0)).getName()); // 【V9】只显示文件名
                                }
                            }
                            updateMediaList.run();
                        }
                    }, allowFolder);
                }
            }
        });
        
        // 【新增】名片选择按钮逻辑：多选好友Wxid
        selectCardBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
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
                                    // 【新增】显示ID（完整ID）
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
        });
        
        // 【修改】媒体顺序管理逻辑：支持多选，动态更新按钮
        mediaListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String path = mediaPaths.get(position);
                if (mediaListView.isItemChecked(position)) {
                    selectedMediaPaths.add(path);
                } else {
                    selectedMediaPaths.remove(path);
                }
                // 更新按钮可见性和启用状态
                updateOrderButtons(mediaListView, orderButtonsLayout, mediaPaths.size(), upButton, downButton, deleteButton);
            }
        });
        upButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (selectedMediaPaths.size() == 1) {
                    String selectedPath = selectedMediaPaths.iterator().next();
                    int pos = mediaPaths.indexOf(selectedPath);
                    if (pos > 0) {
                        // 交换位置
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
                        // 交换位置
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
        
        // 【修改】名片顺序管理逻辑：支持多选，动态更新按钮
        // 【修复】修复onItemClickListener签名和内容
        cardListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String wxid = cardWxids.get(position);
                if (cardListView.isItemChecked(position)) {
                    selectedCardWxids.add(wxid);
                } else {
                    selectedCardWxids.remove(wxid);
                }
                // 更新按钮可见性和启用状态
                updateOrderButtons(cardListView, cardOrderButtonsLayout, cardWxids.size(), cardUpButton, cardDownButton, cardDeleteButton);
            }
        });
        cardUpButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (selectedCardWxids.size() == 1) {
                    String selectedWxid = selectedCardWxids.iterator().next();
                    int pos = cardWxids.indexOf(selectedWxid);
                    if (pos > 0) {
                        // 交换位置
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
                        // 交换位置
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
            public void onClick(View v) {
                // Toggle已内嵌
            }
        });
        replyAsQuoteSwitchRow.setTag("replyAsQuoteSwitchRow");
        // 【修改】为引用开关添加提示
        TextView quotePrompt = createPromptText("⚠️ 勾选后将引用原消息回复");
        quotePrompt.setTag("quotePrompt");
        layout.addView(replyAsQuoteSwitchRow);
        layout.addView(quotePrompt);
        
        // --- 卡片3: 匹配方式 ---
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
        
        // --- 卡片4: @触发 ---
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

        // 【新增】卡片：拍一拍触发
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
        
        // --- 卡片5: 延迟设置 ---
        LinearLayout delayCard = createCardLayout();
        delayCard.addView(createSectionTitle("延迟回复 (秒)"));
        final EditText delayEdit = createStyledEditText("输入延迟秒数 (0为立即回复)", String.valueOf(rule.get("delaySeconds")));
        delayEdit.setInputType(InputType.TYPE_CLASS_NUMBER);
        delayCard.addView(delayEdit);
        layout.addView(delayCard);
        
        // --- 卡片6: 时间段设置 ---
        LinearLayout timeCard = createCardLayout();
        timeCard.addView(createSectionTitle("生效时间段 (留空则不限制)"));
        LinearLayout timeLayout = new LinearLayout(getTopActivity());
        timeLayout.setOrientation(LinearLayout.HORIZONTAL);
        timeLayout.setGravity(Gravity.CENTER_VERTICAL);
        final EditText startTimeEdit = createStyledEditText("开始 HH:mm", (String) rule.get("startTime"));
        startTimeEdit.setFocusable(false);
        // 【修复】设置权重布局，确保起始时间不挤占全部空间
        LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        startParams.setMargins(0, 8, 4, 16);  // 轻微右边距
        startTimeEdit.setLayoutParams(startParams);
        final EditText endTimeEdit = createStyledEditText("结束 HH:mm", (String) rule.get("endTime"));
        endTimeEdit.setFocusable(false);
        // 【修复】设置权重布局，确保结束时间等宽显示
        LinearLayout.LayoutParams endParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        endParams.setMargins(4, 8, 0, 16);  // 轻微左边距
        endTimeEdit.setLayoutParams(endParams);
        startTimeEdit.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showTimePickerDialog(startTimeEdit); } });
        endTimeEdit.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showTimePickerDialog(endTimeEdit); } });
        timeLayout.addView(startTimeEdit);
        TextView dashText = new TextView(getTopActivity());
        dashText.setText("  -  ");
        dashText.setTextSize(16);
        // 【优化】dash 文本使用 WRAP_CONTENT，避免影响两侧
        LinearLayout.LayoutParams dashParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dashText.setLayoutParams(dashParams);
        timeLayout.addView(dashText);
        timeLayout.addView(endTimeEdit);
        timeCard.addView(timeLayout);
        layout.addView(timeCard);
        
        // --- 卡片7: 生效目标 ---
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
        
        // --- 卡片8: 启用开关 ---
        LinearLayout switchCard = createCardLayout();
        final LinearLayout enabledSwitchRow = createSwitchRow(getTopActivity(), "启用此规则", (Boolean) rule.get("enabled"), new View.OnClickListener() {
            public void onClick(View v) {
                // Toggle已内嵌
            }
        });
        // 【修改】为规则开关添加提示
        TextView ruleEnabledPrompt = createPromptText("⚠️ 勾选后启用此规则");
        switchCard.addView(enabledSwitchRow);
        switchCard.addView(ruleEnabledPrompt);
        layout.addView(switchCard);
        
        // --- 卡片9: 变量帮助 ---
        LinearLayout helpCard = createCardLayout();
        TextView helpText = new TextView(getTopActivity());
        helpText.setText("可用变量 (仅文本回复):\n%senderName% - 发送者昵称(优先显示备注)\n%senderWxid% - 发送者wxid\n%groupName% - 群名称(仅群聊)\n%time% - 当前时间\n%atSender% - @发送者 (仅群聊)");
        helpText.setTextSize(12);
        helpText.setTextColor(Color.parseColor("#666666"));
        helpCard.addView(helpText);
        layout.addView(helpCard);
        
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

        // 获取引用开关
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
                } else if (replyType == REPLY_TYPE_CARD) {
                    rule.put("reply", TextUtils.join(";;;", cardWxids));
                    if (cardWxids.isEmpty()) { toast("名片Wxid不能为空"); return; }
                } else if (replyType != REPLY_TYPE_XIAOZHI_AI && replyType != REPLY_TYPE_ZHILIA_AI) { // AI类型不检查
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
                // 【优化】放宽验证：允许仅设置开始或结束（但如果两者都不空，则视为范围）
                // 如果仅一个不空，toast 提醒但不阻塞保存
                if ((!startTime.isEmpty() && endTime.isEmpty()) || (startTime.isEmpty() && !endTime.isEmpty())) {
                    toast("建议同时设置开始和结束时间，否则视为单点时间（非范围）");
                    // 不 return，继续保存
                }
                rule.put("keyword", keyword);
                rule.put("enabled", enabledCheckBox.isChecked());
                rule.put("matchType", matchType);
                
                int atTriggerType;
                if (atTriggerGroup.getCheckedRadioButtonId() == atTriggerMeRadio.getId()) atTriggerType = AT_TRIGGER_ME;
                else if (atTriggerGroup.getCheckedRadioButtonId() == atTriggerAllRadio.getId()) atTriggerType = AT_TRIGGER_ALL;
                else atTriggerType = AT_TRIGGER_NONE;
                rule.put("atTriggerType", atTriggerType);

                // 【新增】拍一拍触发类型
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
                // 【新增】保存媒体延迟
                try {
                    rule.put("mediaDelaySeconds", Long.parseLong(mediaDelayEdit.getText().toString().trim()));
                } catch (NumberFormatException e) {
                    rule.put("mediaDelaySeconds", 1L); // 默认值
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
                        // 【新增】显示ID（完整ID）
                        names.add("👤 " + displayName + "\nID: " + friendInfo.getWxid());
                        ids.add(friendInfo.getWxid());
                    }
                    showMultiSelectDialog("✨ 选择生效好友 ✨", names, ids, currentSelectedWxids, "🔍 搜索好友(昵称/备注)...", updateButtonCallback, new Runnable() {
                        public void run() {
                            updateSelectAllButton((AlertDialog) null, null, null); // 简化，实际在通用方法中处理
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
                        // 【新增】显示ID（完整ID）
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
                        // 【新增】显示ID（完整ID）
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
                        // 【新增】显示ID（完整ID）
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

private void showFriendSwitchDialog() {
    showLoadingDialog("👥 好友自动回复开关", "  正在加载好友列表...", new Runnable() {
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
                    final Set<String> originalEnabledFriends = getStringSet(AUTO_REPLY_ENABLED_FRIENDS_KEY, new HashSet<String>());
                    final Set<String> tempEnabledFriends = new HashSet<String>(originalEnabledFriends);
                    final boolean globalFriendEnabled = getBoolean(AUTO_REPLY_FRIEND_ENABLED_KEY, false);
                    ScrollView scrollView = new ScrollView(getTopActivity());
                    LinearLayout mainLayout = new LinearLayout(getTopActivity());
                    mainLayout.setOrientation(LinearLayout.VERTICAL);
                    mainLayout.setPadding(24, 24, 24, 24);
                    mainLayout.setBackgroundColor(Color.parseColor("#FAFBF9"));
                    scrollView.addView(mainLayout);
                    final LinearLayout globalSwitchRow = createSwitchRow(getTopActivity(), "启用好友自动回复", globalFriendEnabled, new View.OnClickListener() {
                        public void onClick(View v) {}
                    });
                    mainLayout.addView(globalSwitchRow);
                    TextView friendPrompt = createPromptText("⚠️ 全局开关控制所有好友的自动回复，下面可指定具体好友");
                    mainLayout.addView(friendPrompt);
                    final EditText searchEditText = createStyledEditText("🔍 搜索好友(昵称/备注)...", "");
                    searchEditText.setSingleLine(true);
                    mainLayout.addView(searchEditText);
                    final ListView friendListView = new ListView(getTopActivity());
                    setupListViewTouchForScroll(friendListView);
                    friendListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
                    LinearLayout.LayoutParams friendListParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(50));
                    friendListView.setLayoutParams(friendListParams);
                    mainLayout.addView(friendListView);
                    final List currentFilteredFriendIds = new ArrayList();
                    final List currentFilteredFriendNames = new ArrayList();
                    final Runnable updateListRunnable = new Runnable() {
                        public void run() {
                            String searchText = searchEditText.getText().toString().toLowerCase();
                            currentFilteredFriendIds.clear();
                            currentFilteredFriendNames.clear();
                            for (int i = 0; i < names.size(); i++) {
                                String id = (String) ids.get(i);
                                String name = (String) names.get(i);
                                if (searchText.isEmpty() || name.toLowerCase().contains(searchText) || id.toLowerCase().contains(searchText)) {
                                    currentFilteredFriendIds.add(id);
                                    currentFilteredFriendNames.add(name);
                                }
                            }
                            ArrayAdapter adapter = new ArrayAdapter(getTopActivity(), android.R.layout.simple_list_item_multiple_choice, currentFilteredFriendNames);
                            friendListView.setAdapter(adapter);
                            friendListView.clearChoices();
                            for (int j = 0; j < currentFilteredFriendIds.size(); j++) {
                                friendListView.setItemChecked(j, tempEnabledFriends.contains(currentFilteredFriendIds.get(j)));
                            }
                            adjustListViewHeight(friendListView, currentFilteredFriendIds.size());
                            final AlertDialog currentDialog = (AlertDialog) searchEditText.getTag();
                            if (currentDialog != null) {
                                updateSelectAllButton(currentDialog, currentFilteredFriendIds, tempEnabledFriends);
                            }
                        }
                    };
                    friendListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                            String selectedId = (String) currentFilteredFriendIds.get(position);
                            if (friendListView.isItemChecked(position)) tempEnabledFriends.add(selectedId);
                            else tempEnabledFriends.remove(selectedId);
                            final AlertDialog currentDialog = (AlertDialog) searchEditText.getTag();
                            if (currentDialog != null) {
                                updateSelectAllButton(currentDialog, currentFilteredFriendIds, tempEnabledFriends);
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
                    
                    final CheckBox globalCheckBox = (CheckBox) globalSwitchRow.getChildAt(1);
                    
                    final DialogInterface.OnClickListener fullSelectListener = new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            boolean shouldSelectAll = shouldSelectAll(currentFilteredFriendIds, tempEnabledFriends);
                            for (int i = 0; i < currentFilteredFriendIds.size(); i++) {
                                String id = (String) currentFilteredFriendIds.get(i);
                                if (shouldSelectAll) {
                                    tempEnabledFriends.add(id);
                                } else {
                                    tempEnabledFriends.remove(id);
                                }
                                friendListView.setItemChecked(i, shouldSelectAll);
                            }
                            friendListView.getAdapter().notifyDataSetChanged();
                            friendListView.requestLayout();
                            updateSelectAllButton((AlertDialog) dialog, currentFilteredFriendIds, tempEnabledFriends);
                        }
                    };

                    final AlertDialog dialog = buildCommonAlertDialog(getTopActivity(), "✨ 好友自动回复开关 ✨", scrollView, "✅ 保存", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            putBoolean(AUTO_REPLY_FRIEND_ENABLED_KEY, globalCheckBox.isChecked());
                            putStringSet(AUTO_REPLY_ENABLED_FRIENDS_KEY, tempEnabledFriends);
                            toast("好友自动回复设置已保存");
                        }
                    }, "❌ 取消", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    }, "全选", fullSelectListener);

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
                    searchEditText.setTag(dialog);

                    dialog.show();
                    updateListRunnable.run();
                }
            });
        }
    });
}

private void showGroupSwitchDialog() {
    showLoadingDialog("🏠 群聊自动回复开关", "  正在加载群聊列表...", new Runnable() {
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
                    final Set<String> originalEnabledGroups = getStringSet(AUTO_REPLY_ENABLED_GROUPS_KEY, new HashSet<String>());
                    final Set<String> tempEnabledGroups = new HashSet<String>(originalEnabledGroups);
                    final boolean globalGroupEnabled = getBoolean(AUTO_REPLY_GROUP_ENABLED_KEY, false);
                    ScrollView scrollView = new ScrollView(getTopActivity());
                    LinearLayout mainLayout = new LinearLayout(getTopActivity());
                    mainLayout.setOrientation(LinearLayout.VERTICAL);
                    mainLayout.setPadding(24, 24, 24, 24);
                    mainLayout.setBackgroundColor(Color.parseColor("#FAFBF9"));
                    scrollView.addView(mainLayout);
                    final LinearLayout globalSwitchRow = createSwitchRow(getTopActivity(), "启用群聊自动回复", globalGroupEnabled, new View.OnClickListener() {
                        public void onClick(View v) {}
                    });
                    mainLayout.addView(globalSwitchRow);
                    TextView groupPrompt = createPromptText("⚠️ 全局开关控制所有群聊的自动回复，下面可指定具体群聊");
                    mainLayout.addView(groupPrompt);
                    final EditText searchEditText = createStyledEditText("🔍 搜索群聊...", "");
                    searchEditText.setSingleLine(true);
                    mainLayout.addView(searchEditText);
                    final ListView groupListView = new ListView(getTopActivity());
                    setupListViewTouchForScroll(groupListView);
                    groupListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
                    LinearLayout.LayoutParams groupListParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(50));
                    groupListView.setLayoutParams(groupListParams);
                    mainLayout.addView(groupListView);
                    final List currentFilteredGroupIds = new ArrayList();
                    final List currentFilteredGroupNames = new ArrayList();
                    final Runnable updateListRunnable = new Runnable() {
                        public void run() {
                            String searchText = searchEditText.getText().toString().toLowerCase();
                            currentFilteredGroupIds.clear();
                            currentFilteredGroupNames.clear();
                            for (int i = 0; i < names.size(); i++) {
                                String id = (String) ids.get(i);
                                String name = (String) names.get(i);
                                if (searchText.isEmpty() || name.toLowerCase().contains(searchText) || id.toLowerCase().contains(searchText)) {
                                    currentFilteredGroupIds.add(id);
                                    currentFilteredGroupNames.add(name);
                                }
                            }
                            ArrayAdapter adapter = new ArrayAdapter(getTopActivity(), android.R.layout.simple_list_item_multiple_choice, currentFilteredGroupNames);
                            groupListView.setAdapter(adapter);
                            groupListView.clearChoices();
                            for (int j = 0; j < currentFilteredGroupIds.size(); j++) {
                                groupListView.setItemChecked(j, tempEnabledGroups.contains(currentFilteredGroupIds.get(j)));
                            }
                            adjustListViewHeight(groupListView, currentFilteredGroupIds.size());
                            final AlertDialog currentDialog = (AlertDialog) searchEditText.getTag();
                            if (currentDialog != null) {
                                updateSelectAllButton(currentDialog, currentFilteredGroupIds, tempEnabledGroups);
                            }
                        }
                    };
                    groupListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                            String selectedId = (String) currentFilteredGroupIds.get(position);
                            if (groupListView.isItemChecked(position)) tempEnabledGroups.add(selectedId);
                            else tempEnabledGroups.remove(selectedId);
                            final AlertDialog currentDialog = (AlertDialog) searchEditText.getTag();
                            if (currentDialog != null) {
                                updateSelectAllButton(currentDialog, currentFilteredGroupIds, tempEnabledGroups);
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
                    
                    final CheckBox globalCheckBox = (CheckBox) globalSwitchRow.getChildAt(1);
                    
                    final DialogInterface.OnClickListener fullSelectListener = new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            boolean shouldSelectAll = shouldSelectAll(currentFilteredGroupIds, tempEnabledGroups);
                            for (int i = 0; i < currentFilteredGroupIds.size(); i++) {
                                String id = (String) currentFilteredGroupIds.get(i);
                                if (shouldSelectAll) {
                                    tempEnabledGroups.add(id);
                                } else {
                                    tempEnabledGroups.remove(id);
                                }
                                groupListView.setItemChecked(i, shouldSelectAll);
                            }
                            groupListView.getAdapter().notifyDataSetChanged();
                            groupListView.requestLayout();
                            updateSelectAllButton((AlertDialog) dialog, currentFilteredGroupIds, tempEnabledGroups);
                        }
                    };

                    final AlertDialog dialog = buildCommonAlertDialog(getTopActivity(), "✨ 群聊自动回复开关 ✨", scrollView, "✅ 保存", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            putBoolean(AUTO_REPLY_GROUP_ENABLED_KEY, globalCheckBox.isChecked());
                            putStringSet(AUTO_REPLY_ENABLED_GROUPS_KEY, tempEnabledGroups);
                            toast("群聊自动回复设置已保存");
                        }
                    }, "❌ 取消", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    }, "全选", fullSelectListener);

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
                    searchEditText.setTag(dialog);
                    
                    dialog.show();
                    updateListRunnable.run();
                }
            });
        }
    });
}

private List loadAutoReplyRules() {
    Set rulesSet = getStringSet(AUTO_REPLY_RULES_KEY, new HashSet());
    List rules = new ArrayList();
    for (Object ruleStr : rulesSet) {
        Map<String, Object> rule = ruleFromString((String) ruleStr);
        if (rule != null) rules.add(rule);
    }
    if (rules.isEmpty()) {
        rules.add(createAutoReplyRuleMap("你好", "您好！我现在不在，稍后回复您。", true, MATCH_TYPE_FUZZY, new HashSet(), TARGET_TYPE_NONE, AT_TRIGGER_NONE, 0, false, REPLY_TYPE_TEXT, new ArrayList()));
        rules.add(createAutoReplyRuleMap("在吗", "我暂时不在，有事请留言。", true, MATCH_TYPE_FUZZY, new HashSet(), TARGET_TYPE_NONE, AT_TRIGGER_NONE, 0, false, REPLY_TYPE_TEXT, new ArrayList()));
    }
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
    
    // --- 卡片1: 服务配置 ---
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

    // --- 卡片2: 设备信息 ---
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

    // --- 卡片3: 操作按钮 ---
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
// 【新增】反射获取Object方法（用于PatMsg）
private Object invokeObjectMethod(Object obj, String methodName) {
    if (obj == null) return null;
    try {
        Method method = obj.getClass().getMethod(methodName);
        return method.invoke(obj);
    } catch (Exception e) {
        log("Error invoking object method: " + methodName + " - " + e.getMessage());
        return null;
    }
}

// 【新增】时间选择器对话框
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

// 【新增】统一设置对话框样式
private void setupUnifiedDialog(AlertDialog dialog) {
    GradientDrawable dialogBg = new GradientDrawable();
    dialogBg.setCornerRadius(48);
    dialogBg.setColor(Color.parseColor("#FAFBF9"));
    dialog.getWindow().setBackgroundDrawable(dialogBg);
    styleDialogButtons(dialog);
}

// --- 新增的配置读写方法 ---
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
        // ignore
    }
    return defaultValue;
}