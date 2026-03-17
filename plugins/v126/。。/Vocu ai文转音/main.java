import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;


String generateUrl = "https://v1.vocu.ai/api/tts/generate";
String voiceListUrl = "https://v1.vocu.ai/api/voice";
String accountInfoUrl = "https://v1.vocu.ai/api/account/info";

OkHttpClient client = new OkHttpClient();


boolean onClickSendBtn(String text) {

    if ("语音配置".equals(text.trim())) {
        showConfigDialog();
        return true; 
    }

    String cmdPrefix = getString("vocu_cmd_prefix", "#").trim();
    if (cmdPrefix.isEmpty()) cmdPrefix = "#";

    
    if (text.startsWith(cmdPrefix)) {
        String content = text.substring(cmdPrefix.length()).trim();
        int[] emo = {0, 0, 0, 0, 0}; 
        boolean overrideEmo = false; 
        
        if (content.startsWith("(生气)") || content.startsWith("（生气）")) {
            emo[0] = 8; content = content.substring(4); overrideEmo = true;
        } else if (content.startsWith("(开心)") || content.startsWith("（开心）")) {
            emo[1] = 8; content = content.substring(4); overrideEmo = true;
        } else if (content.startsWith("(平静)") || content.startsWith("（平静）")) {
            emo[2] = 8; content = content.substring(4); overrideEmo = true;
        } else if (content.startsWith("(难过)") || content.startsWith("（难过）")) {
            emo[3] = 8; content = content.substring(4); overrideEmo = true;
        } else if (content.startsWith("(自动)") || content.startsWith("（自动）")) {
            emo[4] = 10; content = content.substring(4); overrideEmo = true;
        }

        content = content.trim();
        if (content.startsWith("：") || content.startsWith(":")) content = content.substring(1).trim();
        if (content.isEmpty()) return false;

        String token = getString("vocu_api_key", "").trim();
        String currentVoiceId = getString("vocu_voice_id", "").trim();

        if (token.isEmpty() || currentVoiceId.isEmpty()) {
            toast("⚠️ 未配置秘钥或音色！请发送【语音配置】打开设置面板。");
            return true;
        }

        int langIndex = getInt("vocu_lang_index", 0);
        String[] langCodes = {"auto", "zh", "yue", "en", "ja", "ko", "fr", "es", "de", "pt"};
        String langCode = (langIndex >= 0 && langIndex < langCodes.length) ? langCodes[langIndex] : "auto";

        int presetIndex = getInt("vocu_preset_index", 0); 
        String[] presetCodes = {"balance", "creative", "stable"};
        String presetCode = (presetIndex >= 0 && presetIndex < presetCodes.length) ? presetCodes[presetIndex] : "balance";
        
        boolean breakClone = getInt("vocu_break_clone", 0) == 0; 
        boolean flash = getInt("vocu_flash", 0) == 1;            
        boolean vivid = getInt("vocu_vivid", 0) == 1;            
        
        float speechRate = 1.0f;
        try { speechRate = Float.parseFloat(getString("vocu_speech_rate", "1.0")); } catch (Exception e) {}
        speechRate = Math.max(0.5f, Math.min(2.0f, speechRate));
        
        int seed = -1;
        try { seed = Integer.parseInt(getString("vocu_seed", "-1")); } catch (Exception e) {}

        String promptId = "default";
        if (content.contains("/")) {
            String[] parts = content.split("/", 2);
            content = parts[0];
            promptId = parts[1].trim();
        }

        JSONObject contentObj = new JSONObject();
        contentObj.put("voiceId", currentVoiceId);
        contentObj.put("text", content);
        contentObj.put("promptId", promptId);
        contentObj.put("language", langCode);
        contentObj.put("preset", presetCode);
        contentObj.put("break_clone", breakClone);
        contentObj.put("vivid", vivid);
        contentObj.put("flash", flash);
        contentObj.put("speechRate", speechRate);
        contentObj.put("seed", seed);
        contentObj.put("stream", false); 
        
        boolean isEmoEnabled = getInt("vocu_emo_enable", 0) == 1; 
        
        if (overrideEmo) {
            JSONArray emoArray = new JSONArray();
            for (int e : emo) { emoArray.put(e); }
            contentObj.put("emo_switch", emoArray);
        } else if (isEmoEnabled) {
            JSONArray emoArray = new JSONArray();
            emoArray.put(getInt("vocu_emo_angry", 0));
            emoArray.put(getInt("vocu_emo_happy", 0));
            emoArray.put(getInt("vocu_emo_neutral", 0));
            emoArray.put(getInt("vocu_emo_sad", 0));
            emoArray.put(getInt("vocu_emo_auto", 0));
            contentObj.put("emo_switch", emoArray);
        }

        JSONArray contentsArray = new JSONArray();
        contentsArray.put(contentObj);
        JSONObject jsonBody = new JSONObject();
        jsonBody.put("contents", contentsArray);

        Request request = new Request.Builder()
                .url(generateUrl)
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(jsonBody.toString(), MediaType.parse("application/json")))
                .build();

        client.newCall(request).enqueue(new Callback() {
            public void onFailure(Call call, IOException e) {
                new Handler(Looper.getMainLooper()).post(() -> toast("网络异常：" + e.getMessage()));
            }
            public void onResponse(Call call, Response response) throws IOException {
                String bodyStr = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful() && !bodyStr.isEmpty()) {
                    try {
                        JSONObject resJson = new JSONObject(bodyStr);
                        if (resJson.optInt("status") == 200) {
                            JSONObject data = resJson.optJSONObject("data");
                            if (data != null) {
                                String taskId = data.optString("id");
                                if ("generated".equals(data.optString("status"))) {
                                    JSONObject meta = data.optJSONObject("metadata");
                                    if (meta != null && meta.has("audio")) downloadAudioAndSend(meta.optString("audio"), getTargetTalker());
                                } else {
                                    new Handler(Looper.getMainLooper()).post(() -> toast("✨ 已加入云端队列，正在生成..."));
                                    pollTaskStatus(taskId, getTargetTalker(), token);
                                }
                                return;
                            }
                        }
                    } catch (Exception e) {}
                }
                new Handler(Looper.getMainLooper()).post(() -> toast("⚠️ 异常返回(请检查APIKey)：" + response.code()));
            }
        });

        toast("🎙️ 正在向 Vocu 提交生成请求……");
        return true;
    }
    return false;
}

// ======================= 无界沉浸式美学 UI 引擎 =======================

int dp2px(Activity activity, float dp) {
    return (int) (dp * activity.getResources().getDisplayMetrics().density + 0.5f);
}

GradientDrawable createRoundedBg(String hexColor, int radiusPx) {
    GradientDrawable gd = new GradientDrawable();
    gd.setShape(GradientDrawable.RECTANGLE);
    gd.setColor(Color.parseColor(hexColor));
    gd.setCornerRadius(radiusPx);
    return gd;
}

TextView createSectionTitle(Activity act, String text) {
    TextView tv = new TextView(act);
    tv.setText(text);
    tv.setTextColor(Color.parseColor("#333333"));
    tv.setTextSize(14);
    tv.setTypeface(null, Typeface.BOLD);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.setMargins(dp2px(act, 4), dp2px(act, 16), 0, dp2px(act, 6));
    tv.setLayoutParams(params);
    return tv;
}

TextView createDescriptionText(Activity act, String text) {
    TextView tv = new TextView(act);
    tv.setText(text);
    tv.setTextColor(Color.parseColor("#888888"));
    tv.setTextSize(12);
    tv.setLineSpacing(dp2px(act, 2), 1.1f);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.setMargins(dp2px(act, 4), dp2px(act, 4), dp2px(act, 4), dp2px(act, 8));
    tv.setLayoutParams(params);
    return tv;
}

EditText createModernEditText(Activity act, String hint, String text) {
    EditText input = new EditText(act);
    input.setHint(hint);
    input.setText(text);
    input.setSingleLine(true);
    input.setTextSize(14);
    input.setTextColor(Color.parseColor("#222222"));
    input.setHintTextColor(Color.parseColor("#B0B0B0"));
    input.setBackground(createRoundedBg("#F2F4F7", dp2px(act, 12)));
    input.setPadding(dp2px(act, 16), dp2px(act, 12), dp2px(act, 16), dp2px(act, 12));
    return input;
}

// 【新增】多行文本输入框，用于编写别名映射
EditText createMultiLineEditText(Activity act, String hint, String text) {
    EditText input = new EditText(act);
    input.setHint(hint);
    input.setText(text);
    input.setSingleLine(false);
    input.setMinLines(3);
    input.setMaxLines(6);
    input.setGravity(Gravity.TOP | Gravity.LEFT);
    input.setTextSize(13);
    input.setTextColor(Color.parseColor("#222222"));
    input.setHintTextColor(Color.parseColor("#B0B0B0"));
    input.setBackground(createRoundedBg("#F2F4F7", dp2px(act, 12)));
    input.setPadding(dp2px(act, 16), dp2px(act, 12), dp2px(act, 16), dp2px(act, 12));
    return input;
}

// 绝对防闪退的内联折叠面板
LinearLayout createAccordion(Activity act, String[] options, String[] descs, int[] holder, TextView descView) {
    LinearLayout wrapper = new LinearLayout(act);
    wrapper.setOrientation(LinearLayout.VERTICAL);
    LinearLayout.LayoutParams wrapParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    wrapper.setLayoutParams(wrapParams);

    TextView triggerView = new TextView(act);
    int initIdx = (holder[0] >= 0 && holder[0] < options.length) ? holder[0] : 0;
    triggerView.setText(options[initIdx] + "  ▼"); 
    triggerView.setTextColor(Color.parseColor("#1A73E8")); 
    triggerView.setTextSize(14);
    triggerView.setTypeface(null, Typeface.BOLD);
    triggerView.setBackground(createRoundedBg("#E8F0FE", dp2px(act, 12))); 
    triggerView.setPadding(dp2px(act, 16), dp2px(act, 12), dp2px(act, 16), dp2px(act, 12));

    if (descView != null && descs != null && initIdx < descs.length) {
        descView.setText(descs[initIdx]);
    }

    LinearLayout listLayout = new LinearLayout(act);
    listLayout.setOrientation(LinearLayout.VERTICAL);
    listLayout.setBackground(createRoundedBg("#F8F9FA", dp2px(act, 12))); 
    LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    listParams.setMargins(0, dp2px(act, 8), 0, 0);
    listLayout.setLayoutParams(listParams);
    listLayout.setVisibility(View.GONE); 
    listLayout.setPadding(0, dp2px(act, 4), 0, dp2px(act, 4));

    for (int i = 0; i < options.length; i++) {
        int currentIdx = i; // 规避 final
        TextView item = new TextView(act);
        item.setText(options[i]);
        item.setTextSize(14);
        item.setTextColor(Color.parseColor("#444444"));
        item.setPadding(dp2px(act, 20), dp2px(act, 12), dp2px(act, 20), dp2px(act, 12));
        
        item.setOnClickListener(v -> {
            holder[0] = currentIdx;
            triggerView.setText(options[currentIdx] + "  ▼");
            listLayout.setVisibility(View.GONE);
            if (descView != null && descs != null && currentIdx < descs.length) {
                descView.setText(descs[currentIdx]);
            }
        });
        
        listLayout.addView(item);

        if (i < options.length - 1) {
            View divider = new View(act);
            divider.setBackgroundColor(Color.parseColor("#EDEDED"));
            divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp2px(act, 1)));
            listLayout.addView(divider);
        }
    }

    triggerView.setOnClickListener(v -> {
        if (listLayout.getVisibility() == View.VISIBLE) {
            listLayout.setVisibility(View.GONE);
            triggerView.setText(options[holder[0]] + "  ▼");
        } else {
            listLayout.setVisibility(View.VISIBLE);
            triggerView.setText(options[holder[0]] + "  ▲");
        }
    });

    wrapper.addView(triggerView);
    wrapper.addView(listLayout);
    return wrapper;
}

EditText createStepperInput(Activity act, int currentVal) {
    EditText et = new EditText(act);
    et.setText(String.valueOf(currentVal));
    et.setInputType(InputType.TYPE_CLASS_NUMBER); 
    et.setTextColor(Color.parseColor("#111111"));
    et.setTextSize(15);
    et.setTypeface(null, Typeface.BOLD);
    et.setGravity(Gravity.CENTER);
    et.setPadding(dp2px(act, 16), 0, dp2px(act, 16), 0);
    et.setMinWidth(dp2px(act, 60));
    et.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); 
    return et;
}

LinearLayout createStepperRow(Activity act, String title, EditText etVal) {
    LinearLayout row = new LinearLayout(act);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(dp2px(act, 12), dp2px(act, 8), dp2px(act, 12), dp2px(act, 8));
    row.setBackground(createRoundedBg("#F2F4F7", dp2px(act, 10)));
    
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.setMargins(0, dp2px(act, 8), 0, 0);
    row.setLayoutParams(params);

    TextView tvTitle = new TextView(act);
    tvTitle.setText(title);
    tvTitle.setTextColor(Color.parseColor("#333333"));
    tvTitle.setTextSize(13);
    tvTitle.setTypeface(null, Typeface.BOLD);
    LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
    row.addView(tvTitle, titleParams);

    LinearLayout controls = new LinearLayout(act);
    controls.setOrientation(LinearLayout.HORIZONTAL);
    controls.setGravity(Gravity.CENTER_VERTICAL);

    TextView btnMinus = new TextView(act);
    btnMinus.setText("－");
    btnMinus.setTextColor(Color.parseColor("#1A73E8"));
    btnMinus.setTextSize(16);
    btnMinus.setTypeface(null, Typeface.BOLD);
    btnMinus.setPadding(dp2px(act, 12), dp2px(act, 4), dp2px(act, 12), dp2px(act, 4));
    btnMinus.setBackground(createRoundedBg("#E8F0FE", dp2px(act, 6)));

    TextView btnPlus = new TextView(act);
    btnPlus.setText("＋");
    btnPlus.setTextColor(Color.parseColor("#1A73E8"));
    btnPlus.setTextSize(16);
    btnPlus.setTypeface(null, Typeface.BOLD);
    btnPlus.setPadding(dp2px(act, 12), dp2px(act, 4), dp2px(act, 12), dp2px(act, 4));
    btnPlus.setBackground(createRoundedBg("#E8F0FE", dp2px(act, 6)));

    btnMinus.setOnClickListener(v -> {
        int val = 0;
        try { val = Integer.parseInt(etVal.getText().toString().trim()); } catch(Exception e){}
        if (val > 0) {
            val--;
            etVal.setText(String.valueOf(val));
            try { etVal.setSelection(etVal.getText().length()); } catch(Exception e){}
        }
    });

    btnPlus.setOnClickListener(v -> {
        int val = 0;
        try { val = Integer.parseInt(etVal.getText().toString().trim()); } catch(Exception e){}
        if (val < 10) {
            val++;
            etVal.setText(String.valueOf(val));
            try { etVal.setSelection(etVal.getText().length()); } catch(Exception e){}
        }
    });

    controls.addView(btnMinus);
    controls.addView(etVal);
    controls.addView(btnPlus);
    
    row.addView(controls);
    return row;
}

LinearLayout createCard(Activity act) {
    LinearLayout card = new LinearLayout(act);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setBackground(createRoundedBg("#FFFFFF", dp2px(act, 16)));
    card.setPadding(dp2px(act, 16), dp2px(act, 8), dp2px(act, 16), dp2px(act, 24));
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.setMargins(0, 0, 0, dp2px(act, 16));
    card.setLayoutParams(params);
    return card;
}

TextView createBigCategoryLabel(Activity act, String text) {
    TextView tv = new TextView(act);
    tv.setText(text);
    tv.setTextColor(Color.parseColor("#1A73E8")); 
    tv.setTextSize(16);
    tv.setTypeface(null, Typeface.BOLD);
    tv.setPadding(dp2px(act, 4), dp2px(act, 12), 0, dp2px(act, 8));
    return tv;
}


void showConfigDialog() {
    Activity topActivity = getTopActivity();
    if (topActivity == null) return;
    String token = getString("vocu_api_key", "").trim();
    if (token.isEmpty()) {
        new Handler(Looper.getMainLooper()).post(() -> buildDialogUI(topActivity, null, null, null));
        return;
    }
    toast("正在同步云端数据...");
    Request accRequest = new Request.Builder().url(accountInfoUrl).header("Authorization", "Bearer " + token).get().build();
    client.newCall(accRequest).enqueue(new Callback() {
        public void onFailure(Call call, IOException e) { fetchVoiceList(topActivity, token, null); }
        public void onResponse(Call call, Response response) throws IOException {
            JSONObject userInfo = null;
            if (response.isSuccessful() && response.body() != null) {
                try { userInfo = new JSONObject(response.body().string()).optJSONObject("user"); } catch (Exception e) {}
            }
            fetchVoiceList(topActivity, token, userInfo);
        }
    });
}


void fetchVoiceList(Activity topActivity, String token, JSONObject userInfo) {
    Request request = new Request.Builder().url(voiceListUrl).header("Authorization", "Bearer " + token).header("Accept-Language", "zh-CN").get().build();
    client.newCall(request).enqueue(new Callback() {
        public void onFailure(Call call, IOException e) {
            new Handler(Looper.getMainLooper()).post(() -> buildDialogUI(topActivity, null, null, userInfo));
        }
        public void onResponse(Call call, Response response) throws IOException {
            if (response.isSuccessful() && response.body() != null) {
                try {
                    JSONObject resJson = new JSONObject(response.body().string());
                    if (resJson.optInt("status") == 200) {
                        JSONArray data = resJson.optJSONArray("data");
                        List<String> names = new ArrayList<>();
                        List<String> ids = new ArrayList<>();
                        if (data != null) {
                            // 1. 解析用户保存的别名映射文本
                            String aliasesStr = getString("vocu_voice_aliases", "");
                            Map<String, String> aliasMap = new HashMap<>();
                            if (!aliasesStr.isEmpty()) {
                                String[] lines = aliasesStr.split("\n");
                                for (String line : lines) {
                                    if (line.contains("=")) {
                                        String[] parts = line.split("=", 2);
                                        aliasMap.put(parts[0].trim(), parts[1].trim());
                                    }
                                }
                            }

                            // 2. 遍历构建菜单
                            for (int i = 0; i < data.length(); i++) {
                                JSONObject voice = data.optJSONObject(i);
                                if (voice != null) {
                                    String rawName = voice.optString("name", "未知");
                                    String vId = voice.optString("id");

                                    // 别名替换逻辑 (优先匹配原名，次级匹配ID)
                                    if (aliasMap.containsKey(rawName)) {
                                        rawName = aliasMap.get(rawName);
                                    } else if (aliasMap.containsKey(vId)) {
                                        rawName = aliasMap.get(vId);
                                    }

                                    String rawStatus = voice.optString("status", "");
                                    String zhStatus = "已收录";
                                    if ("published".equals(rawStatus)) zhStatus = "官方";
                                    else if ("pending".equals(rawStatus)) zhStatus = "瞬时克隆";
                                    else if ("lora-pending".equals(rawStatus)) zhStatus = "专业克隆中";
                                    else if ("lora-success".equals(rawStatus)) zhStatus = "专业克隆";
                                    
                                    names.add(rawName + " [" + zhStatus + "]");
                                    ids.add(vId);
                                }
                            }
                        }
                        new Handler(Looper.getMainLooper()).post(() -> buildDialogUI(topActivity, names, ids, userInfo));
                        return;
                    }
                } catch (Exception e) {}
            }
            new Handler(Looper.getMainLooper()).post(() -> buildDialogUI(topActivity, null, null, userInfo));
        }
    });
}


void buildDialogUI(Activity topActivity, List<String> voiceNames, List<String> voiceIds, JSONObject userInfo) {
    try {
        LinearLayout rootLayout = new LinearLayout(topActivity);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackground(createRoundedBg("#F7F8FA", dp2px(topActivity, 22)));
        rootLayout.setPadding(dp2px(topActivity, 20), dp2px(topActivity, 24), dp2px(topActivity, 20), dp2px(topActivity, 24));

        TextView customTitle = new TextView(topActivity);
        customTitle.setText("Vocu 控制台");
        customTitle.setGravity(Gravity.CENTER);
        customTitle.setTextColor(Color.parseColor("#111111"));
        customTitle.setTextSize(20);
        customTitle.setTypeface(null, Typeface.BOLD);
        customTitle.setPadding(0, 0, 0, dp2px(topActivity, 20));
        rootLayout.addView(customTitle);

        ScrollView scrollView = new ScrollView(topActivity);
        LinearLayout mainLayout = new LinearLayout(topActivity);
        mainLayout.setOrientation(LinearLayout.VERTICAL);

       
        if (userInfo != null) {
            String uName = userInfo.optString("name", "未知用户");
            String rawId = userInfo.optString("id", "未知");
            String shortId = rawId.length() > 8 ? rawId.substring(0, 8) : rawId;
            String email = userInfo.optString("email", "");
            String phone = userInfo.optString("phone", "");
            String notif = userInfo.optString("notifications", "0");
            double credits = userInfo.optDouble("credits", 0.0);
            boolean isPaid = userInfo.optBoolean("isPaid", false);
            String role = userInfo.optString("role", "user");
            
            String roleStr = "普通用户";
            if ("admin".equals(role)) roleStr = "高级管理员";
            if ("enterprise".equals(role)) roleStr = "企业专属账户";
            if ("banned".equals(role)) roleStr = "已封禁";
            
            LinearLayout vipCard = new LinearLayout(topActivity);
            vipCard.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable vipBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{Color.parseColor("#252830"), Color.parseColor("#15161A")});
            vipBg.setCornerRadius(dp2px(topActivity, 16));
            vipCard.setBackground(vipBg);
            vipCard.setPadding(dp2px(topActivity, 20), dp2px(topActivity, 20), dp2px(topActivity, 20), dp2px(topActivity, 20));
            
            LinearLayout.LayoutParams vipParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            vipParams.setMargins(0, 0, 0, dp2px(topActivity, 16));
            vipCard.setLayoutParams(vipParams);

            TextView tvName = new TextView(topActivity);
            tvName.setText("👤 " + uName + " (" + roleStr + ")");
            tvName.setTextColor(Color.WHITE);
            tvName.setTextSize(16);
            tvName.setTypeface(null, Typeface.BOLD);
            
            TextView tvStatus = new TextView(topActivity);
            tvStatus.setText(isPaid ? "👑 尊贵PRO会员" : "🆓 免费体验账户");
            tvStatus.setTextColor(Color.parseColor(isPaid ? "#F6D365" : "#A0A0A0"));
            tvStatus.setTextSize(13);
            tvStatus.setPadding(0, dp2px(topActivity, 6), 0, 0);

            TextView tvInfo = new TextView(topActivity);
            tvInfo.setText(
                "🆔 账户短码: " + shortId + "\n" +
                "📱 绑定手机: " + (phone.isEmpty() ? "未绑定" : phone) + "\n" +
                "📧 绑定邮箱: " + (email.isEmpty() ? "未绑定" : email) + "\n" +
                "🔔 系统通知: " + notif + " 条未读"
            );
            tvInfo.setTextColor(Color.parseColor("#A0A5B5")); 
            tvInfo.setTextSize(12);
            tvInfo.setLineSpacing(dp2px(topActivity, 4), 1.2f);
            tvInfo.setPadding(0, dp2px(topActivity, 12), 0, 0);
            
            TextView tvCredits = new TextView(topActivity);
            tvCredits.setText("💰 剩余点数: " + credits + " Tokens");
            tvCredits.setTextColor(Color.parseColor("#61AFEF")); 
            tvCredits.setTextSize(14);
            tvCredits.setTypeface(null, Typeface.BOLD);
            tvCredits.setPadding(0, dp2px(topActivity, 14), 0, 0);

            vipCard.addView(tvName);
            vipCard.addView(tvStatus);
            vipCard.addView(tvInfo);
            vipCard.addView(tvCredits);
            mainLayout.addView(vipCard);
        }

        // --- 卡片 1: 核心配置与角色别名映射 ---
        LinearLayout card1 = createCard(topActivity);
        card1.addView(createSectionTitle(topActivity, "API Key 秘钥"));
        EditText apiKeyInput = createModernEditText(topActivity, "sk-...", getString("vocu_api_key", ""));
        card1.addView(apiKeyInput);

        int[] voiceHolder = { 0 };
        EditText voiceIdInput = null;
        if (voiceNames != null && !voiceNames.isEmpty()) {
            card1.addView(createSectionTitle(topActivity, "语音角色"));
            String savedVoiceId = getString("vocu_voice_id", "");
            for (int i = 0; i < voiceIds.size(); i++) { if (voiceIds.get(i).equals(savedVoiceId)) { voiceHolder[0] = i; break; } }
            card1.addView(createAccordion(topActivity, voiceNames.toArray(new String[0]), null, voiceHolder, null));
        } else {
            card1.addView(createSectionTitle(topActivity, "语音角色 ID"));
            voiceIdInput = createModernEditText(topActivity, "xxxxxxxx-xxxx...", getString("vocu_voice_id", ""));
            card1.addView(voiceIdInput);
        }

        card1.addView(createSectionTitle(topActivity, "自定义触发指令"));
        EditText cmdInput = createModernEditText(topActivity, "如: 语音", getString("vocu_cmd_prefix", "#"));
        card1.addView(cmdInput);

        // 新增：自定义别名配置
        card1.addView(createSectionTitle(topActivity, "自定义角色名称 (别名映射)"));
        EditText aliasInput = createMultiLineEditText(topActivity, "格式：原英文名=我的中文名\n如：\nAlloy=阿洛伊 (温柔女声)\nEcho=回声 (沉稳男声)", getString("vocu_voice_aliases", ""));
        card1.addView(aliasInput);
        card1.addView(createDescriptionText(topActivity, "在这里为难记的英文角色起个中文名吧！每行写一个，格式为：原名=自定义名字。保存后重新打开本面板即可生效。"));

        mainLayout.addView(card1);

        // --- 常规设置 ---
        mainLayout.addView(createBigCategoryLabel(topActivity, "常规设置"));
        LinearLayout cardRoutine = createCard(topActivity);
        
        cardRoutine.addView(createSectionTitle(topActivity, "语言"));
        String[] langOptions = {"自动检测", "中文 (含方言)", "粤语", "英文", "日语", "韩语", "法语", "西班牙语", "德语", "葡萄牙语"};
        String[] langDescs = {
            "自动检测内容语言（暂无法检测粤语，请手动选择）",
            "将尝试使用中文进行语音生成，若样本为方言，则会尝试使用中文方言进行语音生成",
            "将尝试使用粤语进行语音生成",
            "将尝试使用英文进行语音生成",
            "将尝试使用日语进行语音生成",
            "将尝试使用韩语进行语音生成",
            "将尝试使用法语进行语音生成",
            "将尝试使用西班牙语进行语音生成",
            "将尝试使用德语进行语音生成",
            "将尝试使用葡萄牙语进行语音生成"
        };
        int[] langHolder = {getInt("vocu_lang_index", 0)};
        TextView langDescView = createDescriptionText(topActivity, "");
        cardRoutine.addView(createAccordion(topActivity, langOptions, langDescs, langHolder, langDescView));
        cardRoutine.addView(langDescView);

        cardRoutine.addView(createSectionTitle(topActivity, "语速"));
        EditText rateInput = createModernEditText(topActivity, "1", getString("vocu_speech_rate", "1"));
        cardRoutine.addView(rateInput);
        cardRoutine.addView(createDescriptionText(topActivity, "控制生成语音的速度，值越大，语速越快，应为0.5x到2x之间的数值，1为正常语速"));

        mainLayout.addView(cardRoutine);

        // --- 高级设置 ---
        mainLayout.addView(createBigCategoryLabel(topActivity, "高级设置"));
        LinearLayout cardAdvanced = createCard(topActivity);

        cardAdvanced.addView(createSectionTitle(topActivity, "生成预设"));
        String[] presetOptions = {"均衡", "创意", "稳定"}; 
        String[] presetDescs = {
            "默认推荐 | 平衡了稳定性与多样性的生成预设，提升了对角色风格的还原度，但部分声音角色的表现力可能有所下降",
            "多样性最高，且限制相对最小的生成预设，通常可以发挥模型的全部表现力，但稳定性可能有所降低",
            "更为稳定的生成预设，各项参数限制相对较大，可能会导致部分声音角色的表现力以及音色相似度下降"
        };
        int savedPreset = getInt("vocu_preset_index", 0);
        int[] presetHolder = {savedPreset};
        TextView presetDescView = createDescriptionText(topActivity, "");
        cardAdvanced.addView(createAccordion(topActivity, presetOptions, presetDescs, presetHolder, presetDescView));
        cardAdvanced.addView(presetDescView);

        cardAdvanced.addView(createSectionTitle(topActivity, "情感风格"));
        String[] bcOptions = {"偏向文本", "偏向角色"};
        String[] bcDescs = {
            "将会更偏向在生成时产生更贴合文本情感和语义的结果，但对角色和风格的还原度可能有所下降",
            "将会更偏向在生成时产生更还原角色和风格特征的结果，但对不同文本内容的贴合度可能有所下降"
        };
        int[] breakCloneHolder = {getInt("vocu_break_clone", 0)};
        TextView bcDescView = createDescriptionText(topActivity, "");
        cardAdvanced.addView(createAccordion(topActivity, bcOptions, bcDescs, breakCloneHolder, bcDescView));
        cardAdvanced.addView(bcDescView);

        cardAdvanced.addView(createSectionTitle(topActivity, "生成种子"));
        EditText seedInput = createModernEditText(topActivity, "-1", getString("vocu_seed", "-1"));
        cardAdvanced.addView(seedInput);
        cardAdvanced.addView(createDescriptionText(topActivity, "控制生成的随机性，相同的种子会生成相似的结果；应为1到2147483647的整数，-1为完全随机"));

        mainLayout.addView(cardAdvanced);

        // --- 实验设置 ---
        mainLayout.addView(createBigCategoryLabel(topActivity, "实验设置"));
        LinearLayout cardExp = createCard(topActivity);

        cardExp.addView(createSectionTitle(topActivity, "生成模式 [实验性]"));
        String[] flashOptions = {"高品质模式", "低延迟模式"};
        String[] flashDescs = {
            "最高质量的生成模式，但通常需要较长的等待时间才可播放",
            "可在提交生成后即时播放的生成模式，但质量可能略有下降"
        };
        int[] flashHolder = {getInt("vocu_flash", 0)};
        TextView flashDescView = createDescriptionText(topActivity, "");
        cardExp.addView(createAccordion(topActivity, flashOptions, flashDescs, flashHolder, flashDescView));
        cardExp.addView(flashDescView);

        cardExp.addView(createSectionTitle(topActivity, "生动表达 [实验性]"));
        String[] switchOptions = {"关闭", "开启"};
        int[] vividHolder = {getInt("vocu_vivid", 0)};
        cardExp.addView(createAccordion(topActivity, switchOptions, null, vividHolder, null));
        cardExp.addView(createDescriptionText(topActivity, "启用更生动自然的语音表达，但可能会导致生成结果不稳定"));

        cardExp.addView(createSectionTitle(topActivity, "情感控制 [实验性]"));
        int[] emoEnableHolder = {getInt("vocu_emo_enable", 0)};
        cardExp.addView(createAccordion(topActivity, switchOptions, null, emoEnableHolder, null));
        cardExp.addView(createDescriptionText(topActivity, "自由混合多种情感的强度比例(0-10)，实现对生成语音情感的详细控制"));

        EditText etAngry = createStepperInput(topActivity, getInt("vocu_emo_angry", 0));
        cardExp.addView(createStepperRow(topActivity, "生气", etAngry));

        EditText etHappy = createStepperInput(topActivity, getInt("vocu_emo_happy", 0));
        cardExp.addView(createStepperRow(topActivity, "开心", etHappy));

        EditText etNeutral = createStepperInput(topActivity, getInt("vocu_emo_neutral", 0));
        cardExp.addView(createStepperRow(topActivity, "平淡", etNeutral));

        EditText etSad = createStepperInput(topActivity, getInt("vocu_emo_sad", 0));
        cardExp.addView(createStepperRow(topActivity, "难过", etSad));

        EditText etAuto = createStepperInput(topActivity, getInt("vocu_emo_auto", 0));
        cardExp.addView(createStepperRow(topActivity, "匹配上下文", etAuto));

        mainLayout.addView(cardExp);

        scrollView.addView(mainLayout);
        
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        scrollView.setLayoutParams(scrollParams);
        rootLayout.addView(scrollView);

        // ======================= 底部按钮区 =======================
        LinearLayout btnRow = new LinearLayout(topActivity);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(0, dp2px(topActivity, 16), 0, 0);

        TextView btnCancel = new TextView(topActivity);
        btnCancel.setText("关闭");
        btnCancel.setTextColor(Color.parseColor("#555555"));
        btnCancel.setTextSize(15);
        btnCancel.setTypeface(null, Typeface.BOLD);
        btnCancel.setGravity(Gravity.CENTER);
        btnCancel.setBackground(createRoundedBg("#EAECEF", dp2px(topActivity, 14))); 
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(0, dp2px(topActivity, 48), 1.0f);
        cancelParams.setMargins(0, 0, dp2px(topActivity, 12), 0);
        btnCancel.setLayoutParams(cancelParams);

        TextView btnSave = new TextView(topActivity);
        btnSave.setText("保存并生效");
        btnSave.setTextColor(Color.WHITE);
        btnSave.setTextSize(15);
        btnSave.setTypeface(null, Typeface.BOLD);
        btnSave.setGravity(Gravity.CENTER);
        btnSave.setBackground(createRoundedBg("#1A73E8", dp2px(topActivity, 14))); 
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(0, dp2px(topActivity, 48), 1.0f);
        btnSave.setLayoutParams(saveParams);

        btnRow.addView(btnCancel);
        btnRow.addView(btnSave);
        rootLayout.addView(btnRow);

        AlertDialog[] theDialog = new AlertDialog[1];
        AlertDialog dialog = new AlertDialog.Builder(topActivity).setView(rootLayout).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); 
        }
        theDialog[0] = dialog;

        EditText finalVoiceIdInput = voiceIdInput;

        btnCancel.setOnClickListener(v -> { 
            try { if (theDialog[0] != null) theDialog[0].dismiss(); } catch(Throwable t){} 
        });
        
        btnSave.setOnClickListener(v -> {
            try {
                putString("vocu_api_key", apiKeyInput.getText().toString().trim());
                if (voiceNames != null && !voiceNames.isEmpty()) { putString("vocu_voice_id", voiceIds.get(voiceHolder[0])); } 
                else { putString("vocu_voice_id", finalVoiceIdInput.getText().toString().trim()); }
                
                String newCmd = cmdInput.getText().toString().trim();
                if (newCmd.isEmpty()) newCmd = "语音"; 
                putString("vocu_cmd_prefix", newCmd);
                
                // 保存别名配置
                putString("vocu_voice_aliases", aliasInput.getText().toString().trim());

                try {
                    int angry = Integer.parseInt(etAngry.getText().toString().trim());
                    putInt("vocu_emo_angry", Math.max(0, Math.min(10, angry)));
                } catch(Exception e) { putInt("vocu_emo_angry", 0); }

                try {
                    int happy = Integer.parseInt(etHappy.getText().toString().trim());
                    putInt("vocu_emo_happy", Math.max(0, Math.min(10, happy)));
                } catch(Exception e) { putInt("vocu_emo_happy", 0); }

                try {
                    int neutral = Integer.parseInt(etNeutral.getText().toString().trim());
                    putInt("vocu_emo_neutral", Math.max(0, Math.min(10, neutral)));
                } catch(Exception e) { putInt("vocu_emo_neutral", 0); }

                try {
                    int sad = Integer.parseInt(etSad.getText().toString().trim());
                    putInt("vocu_emo_sad", Math.max(0, Math.min(10, sad)));
                } catch(Exception e) { putInt("vocu_emo_sad", 0); }

                try {
                    int auto = Integer.parseInt(etAuto.getText().toString().trim());
                    putInt("vocu_emo_auto", Math.max(0, Math.min(10, auto)));
                } catch(Exception e) { putInt("vocu_emo_auto", 0); }

                putInt("vocu_emo_enable", emoEnableHolder[0]);
                putInt("vocu_lang_index", langHolder[0]);
                putInt("vocu_preset_index", presetHolder[0]);
                
                putString("vocu_speech_rate", rateInput.getText().toString().trim());
                putString("vocu_seed", seedInput.getText().toString().trim());
                putInt("vocu_break_clone", breakCloneHolder[0]);
                putInt("vocu_vivid", vividHolder[0]);
                putInt("vocu_flash", flashHolder[0]);
                
                toast("✨ 设置已保存！触发口令: " + newCmd);
                if (theDialog[0] != null) theDialog[0].dismiss();
            } catch(Throwable t){}
        });

        dialog.setCancelable(false);
        dialog.show();
                
    } catch (Throwable t) {
        toast("配置面板加载失败");
    }
}

// ======================= 网络轮询辅助 =======================

void pollTaskStatus(String taskId, String talker, String validToken) {
    new Thread(() -> {
        int maxRetries = 20; 
        while(maxRetries-- > 0) {
            try {
                Thread.sleep(3000); 
                Request request = new Request.Builder().url("https://v1.vocu.ai/api/tts/generate/" + taskId).header("Authorization", "Bearer " + validToken).get().build();
                Response response = client.newCall(request).execute();
                if (response.isSuccessful() && response.body() != null) {
                    JSONObject resJson = new JSONObject(response.body().string());
                    if (resJson.optInt("status") == 200) {
                        JSONObject data = resJson.optJSONObject("data");
                        if (data != null) {
                            String status = data.optString("status");
                            if ("generated".equals(status)) {
                                JSONObject metadata = data.optJSONObject("metadata");
                                if (metadata != null && metadata.has("audio")) {
                                    downloadAudioAndSend(metadata.optString("audio"), talker);
                                    return;
                                }
                            } else if ("failed".equals(status)) {
                                new Handler(Looper.getMainLooper()).post(() -> toast("❌ 任务失败！请检查内容。"));
                                return;
                            }
                        }
                    }
                }
            } catch (Exception e) {}
        }
        new Handler(Looper.getMainLooper()).post(() -> toast("⚠️ 极速生成超时，请稍后再试！"));
    }).start();
}

void downloadAudioAndSend(String audioUrl, String talker) {
    Request request = new Request.Builder().url(audioUrl).build();
    client.newCall(request).enqueue(new Callback() {
        public void onFailure(Call call, IOException e) {
            new Handler(Looper.getMainLooper()).post(() -> toast("❌ 文件断开：" + e.getMessage()));
        }
        public void onResponse(Call call, Response response) throws IOException {
            if (response.isSuccessful() && response.body() != null) {
                File audioFile = saveAudioFile(response.body().byteStream());
                if (audioFile != null) {
                    String silkPath = pluginDir + "/silk_" + System.currentTimeMillis() + ".silk";
                    mp3ToSilk(audioFile.getAbsolutePath(), silkPath);
                    new Handler(Looper.getMainLooper()).post(() -> {
                        sendVoice(talker, silkPath);
                        new File(silkPath).delete();
                        audioFile.delete();
                    });
                } else {
                    new Handler(Looper.getMainLooper()).post(() -> toast("❌ 音频写入失败"));
                }
            } else {
                new Handler(Looper.getMainLooper()).post(() -> toast("⚠️ 错误码：" + response.code()));
            }
        }
    });
}

File saveAudioFile(InputStream inputStream) {
    FileOutputStream fos = null;
    try {
        File audioFile = new File(pluginDir + "/voice_" + System.currentTimeMillis() + ".mp3");
        fos = new FileOutputStream(audioFile);
        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) { fos.write(buffer, 0, bytesRead); }
        fos.flush();
        return audioFile;
    } catch (IOException e) {
        return null;
    } finally {
        try { if (fos != null) fos.close(); if (inputStream != null) inputStream.close(); } catch (IOException e) { e.printStackTrace(); }
    }
}