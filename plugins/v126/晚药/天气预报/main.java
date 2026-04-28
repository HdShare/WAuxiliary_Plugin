// 导入必要的包
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
import java.util.Collections;
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
import android.view.Gravity;
import android.widget.TextView;
import android.widget.ScrollView;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.MotionEvent;
import android.content.Context;
import android.view.ViewGroup;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONArray;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import java.time.Duration;
import java.io.*;
import java.net.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import me.hd.wauxv.data.bean.info.FriendInfo;
import me.hd.wauxv.data.bean.info.GroupInfo;

// 配置常量
private final String WEATHER_CONFIG_KEY = "weather_config";
private final String WEATHER_UID_KEY = "weather_uid";
private final String WEATHER_API_KEY = "weather_api_key";
private final String WEATHER_TRIGGERS_KEY = "weather_triggers";
private final String DEFAULT_TRIGGERS = "天气";

// 心知天气API基础URL
private final String WEATHER_API_URL = "https://api.seniverse.com/v3/weather/now.json";

private List sCachedFriendList = null;
private List sCachedGroupList = null;

// 添加缺失的HttpClient
private final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(30))
        .callTimeout(Duration.ofSeconds(30))
        .readTimeout(Duration.ofSeconds(30))
        .build();

/**
 * 处理发送按钮点击事件
 */
public boolean onClickSendBtn(String text) {
    String currentChat = getTargetTalker();
    
    // 处理配置指令
    if (text.equals("天气配置")) {
        showWeatherSettingDialog();
        return true;
    }
    
    // 处理开启/关闭天气查询指令
    if (text.equals("开启天气")) {
        putInt(currentChat, "weather_switch", 1);
        insertSystemMsg(currentChat, "该聊天天气查询已开启", System.currentTimeMillis());
        return true;
    } else if (text.equals("关闭天气")) {
        putInt(currentChat, "weather_switch", 0);
        insertSystemMsg(currentChat, "该聊天天气查询已关闭", System.currentTimeMillis());
        return true;
    }
    
    return false;
}

/**
 * 处理接收到的消息
 */
public void onHandleMsg(Object data) {
    // 从消息对象中获取字段 - 修复：直接访问字段
    String text = data.content;
    String chatId = data.talker;
    String sender = data.sendTalker;
    long msgId = data.msgId;
    
    // 过滤条件
    if (isFiltered(data)) return;
    
    // 判断是否为天气查询指令
    if (isWeatherCommand(text)) {
        new Thread(new Runnable() {
            public void run() {
                processWeatherCommand(text, chatId, msgId, sender);
            }
        }).start();
    }
}

/**
 * 判断是否为天气查询指令
 */
private boolean isWeatherCommand(String text) {
    String triggerConfig = getString(WEATHER_CONFIG_KEY, WEATHER_TRIGGERS_KEY, DEFAULT_TRIGGERS);
    String[] triggers = triggerConfig.split(",");
    
    for (String trigger : triggers) {
        trigger = trigger.trim();
        if (!trigger.isEmpty() && text.endsWith(trigger)) {
            return true;
        }
    }
    
    return false;
}

/**
 * 处理天气查询指令
 */
public void processWeatherCommand(String text, String chatId, long msgId, String senderWxid) {
    String triggerConfig = getString(WEATHER_CONFIG_KEY, WEATHER_TRIGGERS_KEY, DEFAULT_TRIGGERS);
    String[] triggers = triggerConfig.split(",");
    
    String location = "";
    boolean matched = false;
    
    // 提取地区名（移除触发词）
    for (String trigger : triggers) {
        trigger = trigger.trim();
        if (!trigger.isEmpty() && text.endsWith(trigger)) {
            location = text.substring(0, text.length() - trigger.length()).trim();
            matched = true;
            break;
        }
    }
    
    if (!matched || location.isEmpty()) {
        sendReplyMsg(chatId, msgId, "请指定地区，例如：北京天气");
        return;
    }
    
    // 获取API配置
    String uid = getString(WEATHER_CONFIG_KEY, WEATHER_UID_KEY, "");
    String apiKey = getString(WEATHER_CONFIG_KEY, WEATHER_API_KEY, "");
    
    if (uid.isEmpty() || apiKey.isEmpty()) {
        sendReplyMsg(chatId, msgId, "请先配置心知天气API信息，发送\"天气配置\"进行设置");
        return;
    }
    
    // 调用天气查询
    getWeather(chatId, location, msgId, senderWxid, uid, apiKey);
}

/**
 * 查询天气信息
 */
public void getWeather(String chatId, String location, long msgId, String senderWxid, String uid, String apiKey) {
    try {
        // 构造API请求参数
        long ts = System.currentTimeMillis() / 1000;
        String str = "ts=" + ts + "&uid=" + uid;
        
        // 生成签名
        String sig = generateSignature(str, apiKey);
        String encodedSig = URLEncoder.encode(sig, "UTF-8");
        
        // 构造完整URL
        String url = WEATHER_API_URL + "?location=" + URLEncoder.encode(location, "UTF-8") + 
                    "&" + str + "&sig=" + encodedSig;
        
        // 发送请求
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0");
        headers.put("Accept", "application/json");
        
        String response = get(url, headers);
        if (response == null || response.isEmpty()) {
            sendReplyMsg(chatId, msgId, "获取天气信息失败，请检查网络连接");
            return;
        }
        
        // 解析响应
        JSONObject jsonResponse = JSON.parseObject(response);
        JSONArray results = jsonResponse.getJSONArray("results");
        
        if (results != null && results.size() > 0) {
            JSONObject weatherData = results.getJSONObject(0);
            JSONObject locationInfo = weatherData.getJSONObject("location");
            JSONObject nowInfo = weatherData.getJSONObject("now");
            String lastUpdate = weatherData.getString("last_update");
            
            // 格式化天气信息
            String weatherReport = formatWeatherReport(locationInfo, nowInfo, lastUpdate);
            sendReplyMsg(chatId, msgId, weatherReport);
        } else {
            sendReplyMsg(chatId, msgId, "未找到地区: " + location + " 的天气信息");
        }
        
    } catch (Exception e) {
        sendReplyMsg(chatId, msgId, "查询天气时出错: " + e.getMessage());
    }
}

/**
 * 生成HMAC-SHA1签名
 */
private String generateSignature(String data, String key) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA1");
    SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA1");
    mac.init(secretKeySpec);
    byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    return Base64.getEncoder().encodeToString(hash);
}

/**
 * 格式化天气报告
 */
private String formatWeatherReport(JSONObject locationInfo, JSONObject nowInfo, String lastUpdate) {
    StringBuilder report = new StringBuilder();
    report.append("🌤️ 天气报告 🌤️\n");
    report.append("================\n");
    report.append("📍 地区: ").append(locationInfo.getString("name")).append("\n");
    report.append("🌍 路径: ").append(locationInfo.getString("path")).append("\n");
    report.append("🌡️ 温度: ").append(nowInfo.getString("temperature")).append("°C\n");
    report.append("☁️ 天气: ").append(nowInfo.getString("text")).append("\n");
    report.append("🔄 更新时间: ").append(lastUpdate).append("\n");
    report.append("================\n");
    report.append("心知天气提供数据");
    
    return report.toString();
}

/**
 * 显示天气设置对话框
 */
private void showWeatherSettingDialog() {
    try {
        ScrollView scrollView = new ScrollView(getTopActivity());
        LinearLayout rootLayout = new LinearLayout(getTopActivity());
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(24, 24, 24, 24);
        rootLayout.setBackgroundColor(Color.parseColor("#FAFBF9"));
        scrollView.addView(rootLayout);
        
        // API配置卡片
        LinearLayout apiConfigCard = createCardLayout();
        apiConfigCard.addView(createSectionTitle("🔑 心知天气API配置"));
        
        // UID输入
        TextView uidLabel = new TextView(getTopActivity());
        uidLabel.setText("用户ID (UID):");
        uidLabel.setTextSize(14);
        uidLabel.setTextColor(Color.parseColor("#666666"));
        uidLabel.setPadding(0, 0, 0, 8);
        apiConfigCard.addView(uidLabel);
        
        final EditText uidEdit = createStyledEditText("请输入心知天气用户ID", getString(WEATHER_CONFIG_KEY, WEATHER_UID_KEY, ""));
        apiConfigCard.addView(uidEdit);
        
        // API Key输入
        TextView apiKeyLabel = new TextView(getTopActivity());
        apiKeyLabel.setText("API密钥 (KEY):");
        apiKeyLabel.setTextSize(14);
        apiKeyLabel.setTextColor(Color.parseColor("#666666"));
        apiKeyLabel.setPadding(0, 16, 0, 8);
        apiConfigCard.addView(apiKeyLabel);
        
        final EditText apiKeyEdit = createStyledEditText("请输入心知天气API密钥", getString(WEATHER_CONFIG_KEY, WEATHER_API_KEY, ""));
        apiKeyEdit.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        apiConfigCard.addView(apiKeyEdit);
        
        // 示例说明
        TextView exampleText = new TextView(getTopActivity());
        exampleText.setText("示例指令: 北京天气\n\n从心知天气官网(https://www.seniverse.com)获取API密钥");
        exampleText.setTextSize(12);
        exampleText.setTextColor(Color.parseColor("#888888"));
        exampleText.setPadding(0, 16, 0, 0);
        apiConfigCard.addView(exampleText);
        
        rootLayout.addView(apiConfigCard);
        
        // 指令设置卡片
        LinearLayout commandCard = createCardLayout();
        commandCard.addView(createSectionTitle("🎯 指令设置"));
        
        final EditText triggerEdit = createStyledEditText("输入天气指令后缀，多个用逗号隔开", getString(WEATHER_CONFIG_KEY, WEATHER_TRIGGERS_KEY, DEFAULT_TRIGGERS));
        commandCard.addView(triggerEdit);
        
        TextView commandExample = new TextView(getTopActivity());
        commandExample.setText("例如输入: 天气,weather,气候\n则指令为: 北京天气 或 上海weather");
        commandExample.setTextSize(12);
        commandExample.setTextColor(Color.parseColor("#888888"));
        commandExample.setPadding(0, 8, 0, 0);
        commandCard.addView(commandExample);
        
        rootLayout.addView(commandCard);
        
        // 权限管理卡片
        LinearLayout permissionCard = createCardLayout();
        permissionCard.addView(createSectionTitle("🛡️ 权限管理"));
        
        Button friendSwitchButton = new Button(getTopActivity());
        friendSwitchButton.setText("👤 好友天气查询开关管理");
        styleUtilityButton(friendSwitchButton);
        permissionCard.addView(friendSwitchButton);
        
        Button groupSwitchButton = new Button(getTopActivity());
        groupSwitchButton.setText("🏠 群聊天气查询开关管理");
        styleUtilityButton(groupSwitchButton);
        permissionCard.addView(groupSwitchButton);
        
        rootLayout.addView(permissionCard);
        
        // 创建对话框
        final AlertDialog dialog = buildCommonAlertDialog(getTopActivity(), "🌤️ 天气查询设置 🌤️", scrollView, 
                "✅ 保存设置", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // 保存配置
                        String newUid = uidEdit.getText().toString().trim();
                        String newApiKey = apiKeyEdit.getText().toString().trim();
                        String newTriggers = triggerEdit.getText().toString().trim();
                        
                        if (!newUid.isEmpty()) {
                            putString(WEATHER_CONFIG_KEY, WEATHER_UID_KEY, newUid);
                        }
                        
                        if (!newApiKey.isEmpty()) {
                            putString(WEATHER_CONFIG_KEY, WEATHER_API_KEY, newApiKey);
                        }
                        
                        if (!newTriggers.isEmpty()) {
                            putString(WEATHER_CONFIG_KEY, WEATHER_TRIGGERS_KEY, newTriggers);
                        } else {
                            putString(WEATHER_CONFIG_KEY, WEATHER_TRIGGERS_KEY, DEFAULT_TRIGGERS);
                        }
                        
                        toast("天气配置已保存");
                        dialog.dismiss();
                    }
                }, 
                "❌ 取消", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                }, 
                "🔄 测试连接", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        String testUid = uidEdit.getText().toString().trim();
                        String testApiKey = apiKeyEdit.getText().toString().trim();
                        
                        if (testUid.isEmpty() || testApiKey.isEmpty()) {
                            toast("请先填写UID和API Key");
                            return;
                        }
                        
                        // 测试API连接
                        new Thread(new Runnable() {
                            public void run() {
                                try {
                                    long ts = System.currentTimeMillis() / 1000;
                                    String str = "ts=" + ts + "&uid=" + testUid;
                                    String sig = generateSignature(str, testApiKey);
                                    String encodedSig = URLEncoder.encode(sig, "UTF-8");
                                    String testUrl = WEATHER_API_URL + "?location=beijing" + 
                                                   "&" + str + "&sig=" + encodedSig;
                                    
                                    Map<String, String> headers = new HashMap<>();
                                    headers.put("User-Agent", "Mozilla/5.0");
                                    headers.put("Accept", "application/json");
                                    
                                    String response = get(testUrl, headers);
                                    if (response != null && response.contains("\"results\"")) {
                                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                                            public void run() {
                                                toast("API连接测试成功 ✓");
                                            }
                                        });
                                    } else {
                                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                                            public void run() {
                                                toast("API连接失败");
                                            }
                                        });
                                    }
                                    
                                } catch (Exception e) {
                                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                                        public void run() {
                                            toast("测试出错: " + e.getMessage());
                                        }
                                    });
                                }
                            }
                        }).start();
                    }
                });
        
        // 设置按钮点击事件
        friendSwitchButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showWeatherScopeDialog(true);
            }
        });
        
        groupSwitchButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showWeatherScopeDialog(false);
            }
        });
        
        dialog.show();
    } catch (Exception e) {
        e.printStackTrace();
    }
}

/**
 * 显示权限管理对话框
 */
private void showWeatherScopeDialog(final boolean isFriend) {
    String title = isFriend ? "👤 好友天气查询开关" : "🏠 群聊天气查询开关";
    String loadingMsg = isFriend ? "正在加载好友列表..." : "正在加载群聊列表...";
    
    showLoadingDialog(title, loadingMsg, new Runnable() {
        public void run() {
            if (isFriend) {
                if (sCachedFriendList == null) sCachedFriendList = getFriendList();
            } else {
                if (sCachedGroupList == null) sCachedGroupList = getGroupList();
            }
            
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                public void run() {
                    List rawList = isFriend ? sCachedFriendList : sCachedGroupList;
                    if (rawList == null || rawList.isEmpty()) {
                        toast(isFriend ? "没有好友数据" : "没有群聊数据");
                        return;
                    }
                    
                    List<String> names = new ArrayList<>();
                    List<String> ids = new ArrayList<>();
                    final Set<String> enabledIds = new HashSet<>();
                    
                    for (int i = 0; i < rawList.size(); i++) {
                        String name = "";
                        String id = "";
                        
                        if (isFriend) {
                            FriendInfo info = (FriendInfo) rawList.get(i);
                            String nickname = TextUtils.isEmpty(info.getNickname()) ? "未知昵称" : info.getNickname();
                            String remark = info.getRemark();
                            name = !TextUtils.isEmpty(remark) ? nickname + " (" + remark + ")" : nickname;
                            id = info.getWxid();
                        } else {
                            GroupInfo info = (GroupInfo) rawList.get(i);
                            name = TextUtils.isEmpty(info.getName()) ? "未知群聊" : info.getName();
                            id = info.getRoomId();
                        }
                        
                        names.add((isFriend ? "👤 " : "🏠 ") + name + "\nID: " + id);
                        ids.add(id);
                        
                        if (getInt(id, "weather_switch", 0) == 1) {
                            enabledIds.add(id);
                        }
                    }
                    
                    showMultiSelectDialog("✨ " + title + " ✨", names, ids, enabledIds, 
                            "🔍 搜索...", new Runnable() {
                                public void run() {
                                    for (int i = 0; i < ids.size(); i++) {
                                        String currentId = ids.get(i);
                                        int newState = enabledIds.contains(currentId) ? 1 : 0;
                                        int oldState = getInt(currentId, "weather_switch", 0);
                                        
                                        if (newState != oldState) {
                                            putInt(currentId, "weather_switch", newState);
                                        }
                                    }
                                    toast("权限设置已保存");
                                }
                            }, null);
                }
            });
        }
    });
}

/**
 * 发送回复消息
 */
private void sendReplyMsg(String talker, long msgId, String content) {
    if (msgId == 0) {
        sendText(talker, content);
    } else {
        sendQuoteMsg(talker, msgId, content);
    }
}

/**
 * 消息过滤条件 - 修复：直接访问data对象的字段
 */
public boolean isFiltered(Object data) {
    // 检查开关状态
    String talker = data.talker;
    boolean switchFlag = getInt(talker, "weather_switch", 1) == 1; // 默认开启
    
    // 排除系统消息和30秒前的消息
    long createTime = data.createTime;
    boolean timeCondition = isHalfMinute(createTime) || data.isSystem;
    
    return !switchFlag || timeCondition;
}

/**
 * 检查消息是否超过30秒
 */
public boolean isHalfMinute(long createTime) {
    long currentTime = System.currentTimeMillis();
    long timeDifference = currentTime - createTime;
    return timeDifference >= 30 * 1000;
}

// ============== 从文档2复用的工具方法 ==============

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
    
    try { 
        layout.setElevation(8); 
    } catch (Exception e) {}
    
    return layout;
}

private TextView createSectionTitle(String text) {
    TextView textView = new TextView(getTopActivity());
    textView.setText(text);
    textView.setTextSize(16);
    textView.setTextColor(Color.parseColor("#333333"));
    
    try { 
        textView.getPaint().setFakeBoldText(true); 
    } catch (Exception e) {}
    
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
    
    return editText;
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

private AlertDialog buildCommonAlertDialog(Context context, String title, View view, 
        String positiveBtnText, DialogInterface.OnClickListener positiveListener,
        String negativeBtnText, DialogInterface.OnClickListener negativeListener,
        String neutralBtnText, DialogInterface.OnClickListener neutralListener) {
    
    AlertDialog.Builder builder = new AlertDialog.Builder(context);
    builder.setTitle(title);
    builder.setView(view);
    
    if (positiveBtnText != null) {
        builder.setPositiveButton(positiveBtnText, positiveListener);
    }
    if (negativeBtnText != null) {
        builder.setNegativeButton(negativeBtnText, negativeListener);
    }
    if (neutralBtnText != null) {
        builder.setNeutralButton(neutralBtnText, neutralListener);
    }
    
    final AlertDialog dialog = builder.create();
    dialog.setOnShowListener(new DialogInterface.OnShowListener() {
        public void onShow(DialogInterface d) {
            setupUnifiedDialog(dialog);
        }
    });
    
    return dialog;
}

private void setupUnifiedDialog(AlertDialog dialog) {
    try {
        GradientDrawable dialogBg = new GradientDrawable();
        dialogBg.setCornerRadius(48);
        dialogBg.setColor(Color.parseColor("#FAFBF9"));
        dialog.getWindow().setBackgroundDrawable(dialogBg);
        styleDialogButtons(dialog);
    } catch (Exception e) {}
}

private void styleDialogButtons(AlertDialog dialog) {
    try {
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
    } catch (Exception e) {}
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
    
    final AlertDialog loadingDialog = buildCommonAlertDialog(getTopActivity(), title, initialLayout, 
            null, null, 
            "❌ 取消", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) { 
                    d.dismiss(); 
                }
            }, 
            null, null);
    
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

private void showMultiSelectDialog(String title, List<String> allItems, List<String> idList, 
        Set<String> selectedIds, String searchHint, final Runnable onConfirm, final Runnable updateList) {
    try {
        final Set<String> tempSelected = new HashSet<>(selectedIds);
        
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
        
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 
            dpToPx(50)
        );
        listView.setLayoutParams(listParams);
        mainLayout.addView(listView);
        
        final List<String> currentFilteredIds = new ArrayList<>();
        final List<String> currentFilteredNames = new ArrayList<>();
        
        final Runnable updateListRunnable = new Runnable() {
            public void run() {
                String searchText = searchEditText.getText().toString().toLowerCase();
                currentFilteredIds.clear();
                currentFilteredNames.clear();
                
                for (int i = 0; i < allItems.size(); i++) {
                    String id = idList.get(i);
                    String name = allItems.get(i);
                    
                    if (searchText.isEmpty() || 
                        name.toLowerCase().contains(searchText) || 
                        id.toLowerCase().contains(searchText)) {
                        currentFilteredIds.add(id);
                        currentFilteredNames.add(name);
                    }
                }
                
                ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    getTopActivity(), 
                    android.R.layout.simple_list_item_multiple_choice, 
                    currentFilteredNames
                );
                listView.setAdapter(adapter);
                listView.clearChoices();
                
                for (int j = 0; j < currentFilteredIds.size(); j++) {
                    listView.setItemChecked(j, tempSelected.contains(currentFilteredIds.get(j)));
                }
                
                adjustListViewHeight(listView, currentFilteredIds.size());
                
                if (updateList != null) {
                    updateList.run();
                }
                
                final AlertDialog currentDialog = (AlertDialog) searchEditText.getTag();
                if (currentDialog != null) {
                    updateSelectAllButton(currentDialog, currentFilteredIds, tempSelected);
                }
            }
        };
        
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
                String selected = currentFilteredIds.get(pos);
                if (listView.isItemChecked(pos)) {
                    tempSelected.add(selected);
                } else {
                    tempSelected.remove(selected);
                }
                
                if (updateList != null) {
                    updateList.run();
                }
                
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
                if (searchRunnable != null) {
                    searchHandler.removeCallbacks(searchRunnable);
                }
            }
            public void afterTextChanged(Editable s) { 
                searchHandler.postDelayed(searchRunnable, 300); 
            }
        });
        
        final DialogInterface.OnClickListener fullSelectListener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                boolean shouldSelectAll = shouldSelectAll(currentFilteredIds, tempSelected);
                
                for (int i = 0; i < currentFilteredIds.size(); i++) {
                    if (shouldSelectAll) {
                        tempSelected.add(currentFilteredIds.get(i));
                    } else {
                        tempSelected.remove(currentFilteredIds.get(i));
                    }
                    listView.setItemChecked(i, shouldSelectAll);
                }
                
                listView.getAdapter().notifyDataSetChanged();
                listView.requestLayout();
                updateSelectAllButton((AlertDialog) dialog, currentFilteredIds, tempSelected);
            }
        };
        
        final AlertDialog dialog = buildCommonAlertDialog(getTopActivity(), title, scrollView, 
                "✅ 保存设置", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        selectedIds.clear();
                        selectedIds.addAll(tempSelected);
                        
                        if (onConfirm != null) {
                            onConfirm.run();
                        }
                        
                        dialog.dismiss();
                    }
                }, 
                "❌ 取消", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) { 
                        dialog.dismiss(); 
                    }
                }, 
                "全选", fullSelectListener);
        
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
        e.printStackTrace();
    }
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

private int dpToPx(int dp) {
    return (int) (getTopActivity().getResources().getDisplayMetrics().density * dp);
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

private boolean shouldSelectAll(List<String> currentFilteredIds, Set<String> selectedIds) {
    int selectableCount = currentFilteredIds.size();
    int checkedCount = 0;
    
    for (int i = 0; i < selectableCount; i++) {
        if (selectedIds.contains(currentFilteredIds.get(i))) {
            checkedCount++;
        }
    }
    
    return selectableCount > 0 && checkedCount < selectableCount;
}

private void updateSelectAllButton(AlertDialog dialog, List<String> currentFilteredIds, Set<String> selectedIds) {
    if (dialog == null) return;
    
    Button neutralButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
    if (neutralButton != null) {
        if (shouldSelectAll(currentFilteredIds, selectedIds)) {
            neutralButton.setText("全选");
        } else {
            neutralButton.setText("取消全选");
        }
    }
}

// ============== HTTP请求方法 ==============

private void addHeaders(Request.Builder builder, Map<String, String> header) {
    if (header != null) {
        for (Map.Entry<String, String> entry : header.entrySet()) {
            builder.addHeader(entry.getKey(), entry.getValue());
        }
    }
}

private String executeRequest(Request.Builder builder) {
    try {
        Response response = client.newCall(builder.build()).execute();
        return response.body().string();
    } catch (IOException e) {
        return null;
    }
}

public String get(String url, Map<String, String> header) {
    Request.Builder builder = new Request.Builder().url(url).get();
    addHeaders(builder, header);
    return executeRequest(builder);
}

// ============== 配置存储方法 ==============
private static final String JavaPath = pluginDir.getAbsolutePath();

public void NewFile(String Path) {
    File file = new File(Path);
    if (!file.exists()) {
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
        try {
            file.createNewFile();
        } catch (IOException e) {
        }
    }
}

public String Read(String Path) {
    String Text = "";
    try {
        File file = new File(Path);
        if (!file.exists()) {
            return null;
        }
        BufferedReader bf = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
        String str;
        while ((str = bf.readLine()) != null) {
            Text += "\n" + str;
        }
        bf.close();
        if (Text.isEmpty()) {
            return null;
        }
        return Text.substring(1);
    } catch (IOException ioe) {
        return null;
    }
}

public void Write(String Path, String WriteData) {
    try {
        File file = new File(Path);
        if (!file.exists()) {
            file.createNewFile();
        }
        FileOutputStream fos = new FileOutputStream(file);
        OutputStreamWriter osw = new OutputStreamWriter(fos, "UTF-8");
        osw.write(WriteData);
        osw.flush();
        osw.close();
    } catch (IOException e) {
    }
}

public void putString(String setName, String itemName, String value) {
    try {
        String filePath = JavaPath + "/data/" + pluginId + "/" + setName;
        NewFile(filePath);
        String userData = Read(filePath);
        JSONObject json = JSONObject.parseObject(userData == null ? "{}" : userData);
        json.put(itemName, value);
        Write(filePath, JSON.toJSONString(json));
    } catch (Exception e) {
    }
}

public String getString(String setName, String itemName, String defaultValue) {
    try {
        String filePath = JavaPath + "/data/" + pluginId + "/" + setName;
        String userData = Read(filePath);
        if (userData == null) return defaultValue;
        JSONObject userDataJson = JSONObject.parseObject(userData);
        return userDataJson.getString(itemName) != null ? userDataJson.getString(itemName) : defaultValue;
    } catch (Exception e) {
        return defaultValue;
    }
}

public void putInt(String setName, String itemName, int value) {
    try {
        String filePath = JavaPath + "/data/" + pluginId + "/" + setName;
        NewFile(filePath);
        String userData = Read(filePath);
        JSONObject json = JSONObject.parseObject(userData == null ? "{}" : userData);
        json.put(itemName, value);
        Write(filePath, JSON.toJSONString(json));
    } catch (Exception e) {
    }
}

public int getInt(String setName, String itemName, int defaultValue) {
    try {
        String filePath = JavaPath + "/data/" + pluginId + "/" + setName;
        String userData = Read(filePath);
        if (userData == null) return defaultValue;
        JSONObject userDataJson = JSONObject.parseObject(userData);
        return userDataJson.getInteger(itemName) != null ? userDataJson.getInteger(itemName) : defaultValue;
    } catch (Exception e) {
        return defaultValue;
    }
}

/**
 * 插件加载时执行
 */
public void onLoad() {
    System.out.println("天气查询插件已加载");
}

/**
 * 插件卸载时执行
 */
public void onUnload() {
    System.out.println("天气查询插件已卸载");
}
