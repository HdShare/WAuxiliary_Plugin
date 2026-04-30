import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Calendar;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

String RULES_KEY = "rules";
String DEFAULT_API_URL = "https://91yq.top/api/relay/call";

final int BG_PANEL = Color.parseColor("#2A2A2A");
final int BG_CARD = Color.parseColor("#3A3A3A");
final int BG_INPUT = Color.parseColor("#404040");
final int TEXT_MAIN = Color.parseColor("#FFFFFF");
final int TEXT_SUB = Color.parseColor("#AAAAAA");
final int TEXT_HINT = Color.parseColor("#666666");
final int ACCENT_BLUE = Color.parseColor("#4A9EFF");
final int ACCENT_GREEN = Color.parseColor("#4AFF9E");
final int ACCENT_RED = Color.parseColor("#FF6B6B");
final int ACCENT_GOLD = Color.parseColor("#FFD93D");
final int DIVIDER = Color.parseColor("#444444");

Map<String, Long> paiLastReplyTime = new HashMap<>();
Map<String, Long> voiceLastReplyTime = new HashMap<>();
final int VOICE_COOLDOWN = 10;

ScheduledThreadPoolExecutor timerScheduler = new ScheduledThreadPoolExecutor(2);
Map<String, JSONObject> allTimerTasks = new ConcurrentHashMap<>();
Map<String, ScheduledFuture<?>> timerTaskFutures = new ConcurrentHashMap<>();
final String TIMER_TASKS_KEY = "timer_tasks";

void onLoad() {
    loadTimerTasks();

    String apiKey = getApiKey();
    if (isEmpty(apiKey)) {
        showNoApiKeyDialog();
    }
}

void showNoApiKeyDialog() {
    Activity activity = getTopActivity();
    if (activity == null) {
        toast("请配置元启API Key启用AI回复\nQQ交流群: 883640898");
        return;
    }

    activity.runOnUiThread(new Runnable() {
        public void run() {
            try {
                AlertDialog dialog = new AlertDialog.Builder(activity).create();
                dialog.setCanceledOnTouchOutside(false);

                LinearLayout root = new LinearLayout(activity);
                root.setOrientation(LinearLayout.VERTICAL);
                root.setBackgroundColor(Color.TRANSPARENT);
                root.setPadding(dp(20), dp(50), dp(20), dp(50));

                LinearLayout panel = new LinearLayout(activity);
                panel.setOrientation(LinearLayout.VERTICAL);
                panel.setBackground(createPanelBg(activity));
                panel.setPadding(dp(20), dp(24), dp(20), dp(24));

                TextView title = new TextView(activity);
                title.setText("欢迎使用元启Ai");
                title.setTextSize(20);
                title.setTypeface(null, Typeface.BOLD);
                title.setTextColor(TEXT_MAIN);
                title.setGravity(Gravity.CENTER);
                panel.addView(title);

                TextView message = new TextView(activity);
                message.setText("请配置元启API Key启用AI回复\n\n使用前请先阅读使用说明\nQQ交流群: 883640898");
                message.setTextSize(14);
                message.setTextColor(TEXT_SUB);
                message.setGravity(Gravity.CENTER);
                message.setPadding(0, dp(16), 0, dp(24));
                panel.addView(message);

                TextView joinGroupBtn = new TextView(activity);
                joinGroupBtn.setText("加入QQ群");
                joinGroupBtn.setTextSize(13);
                joinGroupBtn.setTextColor(TEXT_MAIN);
                joinGroupBtn.setBackground(createChipBg(activity, true));
                joinGroupBtn.setPadding(dp(16), dp(10), dp(16), dp(10));
                joinGroupBtn.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        try {
                            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
                            intent.setData(android.net.Uri.parse("https://qun.qq.com/universal-share/share?ac=1&authKey=qnKnEk9fixjZc6NNjEFbLB8gwREGPnfUP23AZYxGqdxw0iiRJDH1zrztUG8%2BdbAE&busi_data=eyJncm91cENvZGUiOiI4ODM2NDA4OTgiLCJ0b2tlbiI6IlFXVEgzNFEyd3lJNU5oQzljZG4yQW5BMFBWK2RENHpFUkR6eEZ4TWl5U3Z6ZGRYb3ViK2xPK3Nva1p2Wjl4d2MiLCJ1aW4iOiI2MTEwNTM2In0%3D&data=TWSZoYEaYUAG0Euuz6NeLt9YAZI87UxI56LHHlNDI8SV2DSkuJMGelcmcyrKRq1py2DZVMvYrgt4m6sgjkhLXA&svctype=4&tempid=h5_group_info"));
                            activity.startActivity(intent);
                        } catch (Throwable e) {
                            toast("无法打开链接，请手动搜索群号: 883640898");
                        }
                    }
                });
                panel.addView(joinGroupBtn);
                root.addView(panel);
                dialog.show();

                Window window = dialog.getWindow();
                if (window != null) {
                    window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                    window.setLayout(dp(300), ViewGroup.LayoutParams.WRAP_CONTENT);
                    window.setContentView(root);
                }

            } catch (Throwable e) {
                toast("请配置元启API Key启用AI回复\nQQ交流群: 883640898");
            }
        }
    });
}

boolean onClickSendBtn(String text) {
    if ("/元启".equals(text) || "/元启配置".equals(text) || "/元启设置".equals(text)) {
        showMainMenu();
        return true;
    }
    return false;
}

void showMainMenu() {
    final Activity activity = getTopActivity();
    if (activity == null) {
        toast("无法获取当前界面");
        return;
    }

    final String targetTalker = getTargetTalker();
    if (isEmpty(targetTalker)) {
        toast("请先进入聊天界面");
        return;
    }

    final boolean isGroup = targetTalker.contains("@chatroom");
    final String scopeKey = isGroup ? "group_" + targetTalker : "private_" + targetTalker;
    final String scopeLabel = isGroup ? "当前群聊 " + targetTalker : "当前私聊 " + targetTalker;

    activity.runOnUiThread(new Runnable() {
        public void run() {
            try {
                AlertDialog dialog = new AlertDialog.Builder(activity).create();
                dialog.setCanceledOnTouchOutside(true);

                LinearLayout root = new LinearLayout(activity);
                root.setOrientation(LinearLayout.VERTICAL);
                root.setBackgroundColor(Color.TRANSPARENT);
                root.setPadding(dp(20), dp(50), dp(20), dp(50));

                LinearLayout panel = new LinearLayout(activity);
                panel.setOrientation(LinearLayout.VERTICAL);
                panel.setBackground(createPanelBg(activity));
                panel.setPadding(dp(20), dp(24), dp(20), dp(24));

                LinearLayout headerRow = new LinearLayout(activity);
                headerRow.setOrientation(LinearLayout.HORIZONTAL);
                headerRow.setGravity(Gravity.CENTER_VERTICAL);

                TextView title = new TextView(activity);
                title.setText("元启Ai");
                title.setTextSize(20);
                title.setTypeface(null, Typeface.BOLD);
                title.setTextColor(TEXT_MAIN);
                title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                headerRow.addView(title);

                TextView slogan = new TextView(activity);
                slogan.setText("执代码为斧，劈智能之蒙昧");
                slogan.setTextSize(9);
                slogan.setTextColor(TEXT_HINT);
                slogan.setPadding(0, 0, dp(8), 0);
                headerRow.addView(slogan);

                TextView settingsBtn = new TextView(activity);
                settingsBtn.setText("⚙");
                settingsBtn.setTextSize(22);
                settingsBtn.setTextColor(TEXT_SUB);
                settingsBtn.setPadding(dp(8), dp(4), dp(8), dp(4));
                settingsBtn.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        showApiKeyDialog(activity);
                    }
                });
                headerRow.addView(settingsBtn);
                panel.addView(headerRow);

                TextView subLabel = new TextView(activity);
                subLabel.setText(scopeLabel);
                subLabel.setTextSize(11);
                subLabel.setTextColor(TEXT_SUB);
                subLabel.setPadding(0, dp(8), 0, dp(16));
                panel.addView(subLabel);

                ScrollView scroll = new ScrollView(activity);
                scroll.setVerticalScrollBarEnabled(false);
                LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(400));
                scroll.setLayoutParams(scrollParams);

                LinearLayout content = new LinearLayout(activity);
                content.setOrientation(LinearLayout.VERTICAL);

                content.addView(createSectionTitle(activity, "作用域管理"));
                content.addView(createScopeManager(activity, scopeKey, isGroup));

                content.addView(createSectionTitle(activity, "触发规则"));
                JSONArray rules = getRules();
                content.addView(createRulesCard(activity, rules, scopeKey, isGroup, dialog));

                content.addView(createSectionTitle(activity, "回复设置"));
                content.addView(createToggleItem(activity, "引用回复", isAutoQuote(scopeKey), "auto_quote_" + scopeKey));
                if (isGroup) {
                    content.addView(createToggleItem(activity, "回复自己消息", isReplySelf(scopeKey), "reply_self_" + scopeKey));
                }
                content.addView(createToggleItem(activity, "拍一拍回复", isPaiReply(scopeKey), "pai_reply_" + scopeKey));
                content.addView(createVoiceReplyItem(activity, scopeKey));

                content.addView(createSectionTitle(activity, "定时任务"));
                content.addView(createTimerTaskEntry(activity, scopeKey, isGroup, dialog));

                scroll.addView(content);
                panel.addView(scroll);

                root.addView(panel);
                dialog.show();

                Window window = dialog.getWindow();
                if (window != null) {
                    window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                    window.setLayout(dp(320), ViewGroup.LayoutParams.WRAP_CONTENT);
                    window.setContentView(root);
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
                    window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
                }

            } catch (Throwable e) {
                log("界面加载失败: " + e.getMessage());
                toast("界面加载失败");
            }
        }
    });
}

View createSectionTitle(Context ctx, String text) {
    TextView tv = new TextView(ctx);
    tv.setText(text);
    tv.setTextSize(12);
    tv.setTextColor(TEXT_SUB);
    tv.setPadding(0, dp(16), 0, dp(8));
    return tv;
}

View createToggleItem(final Context ctx, String label, boolean checked, final String key) {
    LinearLayout item = new LinearLayout(ctx);
    item.setOrientation(LinearLayout.HORIZONTAL);
    item.setGravity(Gravity.CENTER_VERTICAL);
    item.setBackground(createCardBg(ctx));
    item.setPadding(dp(16), dp(14), dp(16), dp(14));
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.bottomMargin = dp(8);
    item.setLayoutParams(params);

    TextView labelTv = new TextView(ctx);
    labelTv.setText(label);
    labelTv.setTextSize(14);
    labelTv.setTextColor(TEXT_MAIN);
    labelTv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    item.addView(labelTv);

    final TextView toggle = new TextView(ctx);
    toggle.setText(checked ? "开" : "关");
    toggle.setTextSize(12);
    toggle.setTextColor(checked ? TEXT_MAIN : TEXT_SUB);
    toggle.setBackground(createChipBg(ctx, checked));
    toggle.setPadding(dp(12), dp(4), dp(12), dp(4));
    item.addView(toggle);

    item.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            boolean newValue = !getBoolean(key, false);
            putBoolean(key, newValue);
            toggle.setText(newValue ? "开" : "关");
            toggle.setTextColor(newValue ? TEXT_MAIN : TEXT_SUB);
            toggle.setBackground(createChipBg(ctx, newValue));
        }
    });

    return item;
}

View createVoiceReplyItem(final Context ctx, final String scopeKey) {
    LinearLayout item = new LinearLayout(ctx);
    item.setOrientation(LinearLayout.VERTICAL);
    item.setBackground(createCardBg(ctx));
    item.setPadding(dp(16), dp(14), dp(16), dp(14));
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.bottomMargin = dp(8);
    item.setLayoutParams(params);

    LinearLayout row = new LinearLayout(ctx);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);

    TextView labelTv = new TextView(ctx);
    labelTv.setText("语音回复");
    labelTv.setTextSize(14);
    labelTv.setTextColor(TEXT_MAIN);
    labelTv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    row.addView(labelTv);

    final String[] options = {"关", "男", "女"};
    final String[] values = {"off", "male", "female"};
    String currentValue = getVoiceReply(scopeKey);
    int selectedIndex = 0;
    for (int i = 0; i < values.length; i++) {
        if (values[i].equals(currentValue)) {
            selectedIndex = i;
            break;
        }
    }

    LinearLayout chipsRow = new LinearLayout(ctx);
    chipsRow.setOrientation(LinearLayout.HORIZONTAL);

    final TextView[] chipTvs = new TextView[3];
    for (int i = 0; i < 3; i++) {
        TextView chip = new TextView(ctx);
        chip.setText(options[i]);
        chip.setTextSize(11);
        chip.setTextColor(i == selectedIndex ? TEXT_MAIN : TEXT_SUB);
        chip.setBackground(createChipBg(ctx, i == selectedIndex));
        chip.setPadding(dp(10), dp(4), dp(10), dp(4));
        chip.setTag(Integer.valueOf(i));
        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (i > 0) chipParams.leftMargin = dp(6);
        chip.setLayoutParams(chipParams);
        chip.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                int clickedIdx = ((Integer) v.getTag()).intValue();
                setVoiceReply(scopeKey, values[clickedIdx]);
                for (int j = 0; j < 3; j++) {
                    chipTvs[j].setTextColor(j == clickedIdx ? TEXT_MAIN : TEXT_SUB);
                    chipTvs[j].setBackground(createChipBg(ctx, j == clickedIdx));
                }
            }
        });
        chipTvs[i] = chip;
        chipsRow.addView(chip);
    }
    row.addView(chipsRow);
    item.addView(row);

    TextView hintTv = new TextView(ctx);
    hintTv.setText("开启语音回复后回复会非常慢，并限速10秒只能回复一个语音");
    hintTv.setTextSize(10);
    hintTv.setTextColor(TEXT_HINT);
    hintTv.setPadding(0, dp(4), 0, 0);
    item.addView(hintTv);

    return item;
}

View createRulesCard(final Context ctx, JSONArray rules, final String scopeKey, final boolean isGroup, final AlertDialog mainDialog) {
    LinearLayout card = new LinearLayout(ctx);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setBackground(createCardBg(ctx));
    card.setPadding(dp(16), dp(14), dp(16), dp(14));
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.bottomMargin = dp(8);
    card.setLayoutParams(params);

    LinearLayout header = new LinearLayout(ctx);
    header.setOrientation(LinearLayout.HORIZONTAL);
    header.setGravity(Gravity.CENTER_VERTICAL);

    TextView title = new TextView(ctx);
    title.setText("已配置 " + rules.length() + " 条规则");
    title.setTextSize(12);
    title.setTextColor(TEXT_SUB);
    title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    header.addView(title);

    TextView addBtn = new TextView(ctx);
    addBtn.setText("+ 添加");
    addBtn.setTextSize(12);
    addBtn.setTextColor(ACCENT_BLUE);
    addBtn.setPadding(dp(8), dp(4), dp(8), dp(4));
    header.addView(addBtn);
    card.addView(header);

    if (rules.length() > 0) {
        for (int i = 0; i < rules.length(); i++) {
            JSONObject rule = rules.optJSONObject(i);
            if (rule == null) continue;

            LinearLayout ruleRow = new LinearLayout(ctx);
            ruleRow.setOrientation(LinearLayout.HORIZONTAL);
            ruleRow.setGravity(Gravity.CENTER_VERTICAL);
            ruleRow.setPadding(0, dp(8), 0, 0);

            String mode = rule.optString("mode", "keyword");
            String trigger = rule.optString("trigger", "");

            TextView modeChip = new TextView(ctx);
            modeChip.setText(getModeLabel(mode));
            modeChip.setTextSize(10);
            modeChip.setTextColor(getModeColor(mode));
            modeChip.setBackground(createModeChipBg(ctx, mode));
            modeChip.setPadding(dp(6), dp(2), dp(6), dp(2));
            ruleRow.addView(modeChip);

            TextView triggerTv = new TextView(ctx);
            triggerTv.setText(" " + trigger);
            triggerTv.setTextSize(12);
            triggerTv.setTextColor(TEXT_MAIN);
            triggerTv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            ruleRow.addView(triggerTv);

            final int ruleIndex = i;
            final String sk = scopeKey;
            final boolean ig = isGroup;
            TextView delBtn = new TextView(ctx);
            delBtn.setText("删除");
            delBtn.setTextSize(10);
            delBtn.setTextColor(ACCENT_RED);
            delBtn.setPadding(dp(8), dp(2), dp(8), dp(2));
            delBtn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    deleteRule(ruleIndex);
                    toast("已删除");
                    if (mainDialog != null) {
                        mainDialog.dismiss();
                    }
                    showMainMenu();
                }
            });
            ruleRow.addView(delBtn);

            card.addView(ruleRow);
        }
    }

    final String sk = scopeKey;
    final boolean ig = isGroup;
    final AlertDialog md = mainDialog;
    addBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            showAddRuleDialog((Activity) ctx, sk, ig, md);
        }
    });

    return card;
}

View createScopeManager(final Context ctx, final String scopeKey, final boolean isGroup) {
    LinearLayout card = new LinearLayout(ctx);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setBackground(createCardBg(ctx));
    card.setPadding(dp(16), dp(14), dp(16), dp(14));
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.bottomMargin = dp(8);
    card.setLayoutParams(params);

    if (isGroup) {
        TextView groupLabel = new TextView(ctx);
        groupLabel.setText("当前群聊");
        groupLabel.setTextSize(12);
        groupLabel.setTextColor(TEXT_SUB);
        card.addView(groupLabel);

        LinearLayout groupRow = new LinearLayout(ctx);
        groupRow.setOrientation(LinearLayout.HORIZONTAL);
        groupRow.setGravity(Gravity.CENTER_VERTICAL);
        groupRow.setPadding(0, dp(8), 0, 0);

        String groupId = scopeKey.replace("group_", "");
        TextView groupInfo = new TextView(ctx);
        groupInfo.setText("群ID: " + groupId);
        groupInfo.setTextSize(14);
        groupInfo.setTextColor(TEXT_MAIN);
        groupInfo.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        groupRow.addView(groupInfo);

        final TextView toggle = new TextView(ctx);
        boolean enabled = isScopeEnabled(scopeKey);
        toggle.setText(enabled ? "已开启" : "已关闭");
        toggle.setTextSize(12);
        toggle.setTextColor(enabled ? TEXT_MAIN : TEXT_SUB);
        toggle.setBackground(createChipBg(ctx, enabled));
        toggle.setPadding(dp(12), dp(4), dp(12), dp(4));
        groupRow.addView(toggle);
        card.addView(groupRow);

        toggle.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                boolean enabled = isScopeEnabled(scopeKey);
                setScopeEnabled(scopeKey, !enabled);
                toggle.setText(!enabled ? "已开启" : "已关闭");
                toggle.setTextColor(!enabled ? TEXT_MAIN : TEXT_SUB);
                toggle.setBackground(createChipBg(ctx, !enabled));
            }
        });
    } else {
        TextView privateLabel = new TextView(ctx);
        privateLabel.setText("当前私聊");
        privateLabel.setTextSize(12);
        privateLabel.setTextColor(TEXT_SUB);
        card.addView(privateLabel);

        LinearLayout privateRow = new LinearLayout(ctx);
        privateRow.setOrientation(LinearLayout.HORIZONTAL);
        privateRow.setGravity(Gravity.CENTER_VERTICAL);
        privateRow.setPadding(0, dp(8), 0, 0);

        TextView privateInfo = new TextView(ctx);
        privateInfo.setText("私聊消息AI回复");
        privateInfo.setTextSize(14);
        privateInfo.setTextColor(TEXT_MAIN);
        privateInfo.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        privateRow.addView(privateInfo);

        final TextView toggle = new TextView(ctx);
        boolean enabled = isScopeEnabled(scopeKey);
        toggle.setText(enabled ? "已开启" : "已关闭");
        toggle.setTextSize(12);
        toggle.setTextColor(enabled ? TEXT_MAIN : TEXT_SUB);
        toggle.setBackground(createChipBg(ctx, enabled));
        toggle.setPadding(dp(12), dp(4), dp(12), dp(4));
        privateRow.addView(toggle);
        card.addView(privateRow);

        toggle.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                boolean enabled = isScopeEnabled(scopeKey);
                setScopeEnabled(scopeKey, !enabled);
                toggle.setText(!enabled ? "已开启" : "已关闭");
                toggle.setTextColor(!enabled ? TEXT_MAIN : TEXT_SUB);
                toggle.setBackground(createChipBg(ctx, !enabled));
            }
        });
    }

    return card;
}

void showApiKeyDialog(final Activity activity) {
    AlertDialog dialog = new AlertDialog.Builder(activity).create();
    dialog.setCanceledOnTouchOutside(true);

    LinearLayout root = new LinearLayout(activity);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(Color.TRANSPARENT);
    root.setPadding(dp(20), dp(50), dp(20), dp(50));

    LinearLayout panel = new LinearLayout(activity);
    panel.setOrientation(LinearLayout.VERTICAL);
    panel.setBackground(createPanelBg(activity));
    panel.setPadding(dp(20), dp(24), dp(20), dp(24));

    LinearLayout headerRow = new LinearLayout(activity);
    headerRow.setOrientation(LinearLayout.HORIZONTAL);
    headerRow.setGravity(Gravity.CENTER_VERTICAL);

    TextView title = new TextView(activity);
    title.setText("设置");
    title.setTextSize(20);
    title.setTypeface(null, Typeface.BOLD);
    title.setTextColor(TEXT_MAIN);
    title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    headerRow.addView(title);

    TextView closeBtn = new TextView(activity);
    closeBtn.setText("✕");
    closeBtn.setTextSize(18);
    closeBtn.setTextColor(TEXT_SUB);
    closeBtn.setPadding(dp(8), dp(4), dp(8), dp(4));
    closeBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            dialog.dismiss();
        }
    });
    headerRow.addView(closeBtn);
    panel.addView(headerRow);

    TextView subLabel = new TextView(activity);
    subLabel.setText("配置元启API密钥与默认回复词");
    subLabel.setTextSize(11);
    subLabel.setTextColor(TEXT_SUB);
    subLabel.setPadding(0, dp(8), 0, dp(16));
    panel.addView(subLabel);

    ScrollView scroll = new ScrollView(activity);
    scroll.setVerticalScrollBarEnabled(false);
    LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(400));
    scroll.setLayoutParams(scrollParams);

    LinearLayout content = new LinearLayout(activity);
    content.setOrientation(LinearLayout.VERTICAL);

    LinearLayout apiKeyCard = new LinearLayout(activity);
    apiKeyCard.setOrientation(LinearLayout.VERTICAL);
    apiKeyCard.setBackground(createCardBg(activity));
    apiKeyCard.setPadding(dp(16), dp(14), dp(16), dp(14));
    LinearLayout.LayoutParams cardParams1 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    cardParams1.bottomMargin = dp(12);
    apiKeyCard.setLayoutParams(cardParams1);

    TextView apiKeyLabel = new TextView(activity);
    apiKeyLabel.setText("元启API Key");
    apiKeyLabel.setTextSize(12);
    apiKeyLabel.setTextColor(TEXT_SUB);
    apiKeyCard.addView(apiKeyLabel);

    final EditText apiKeyInput = new EditText(activity);
    apiKeyInput.setText(getApiKey());
    apiKeyInput.setTextColor(TEXT_MAIN);
    apiKeyInput.setHint("请输入元启API Key");
    apiKeyInput.setHintTextColor(TEXT_HINT);
    apiKeyInput.setBackground(createInputBg(activity));
    apiKeyInput.setPadding(dp(16), dp(12), dp(16), dp(12));
    apiKeyInput.setFocusable(true);
    apiKeyInput.setFocusableInTouchMode(true);
    apiKeyInput.setClickable(true);
    apiKeyInput.setLongClickable(true);
    LinearLayout.LayoutParams apiKeyParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    apiKeyParams.topMargin = dp(8);
    apiKeyInput.setLayoutParams(apiKeyParams);
    apiKeyCard.addView(apiKeyInput);
    content.addView(apiKeyCard);

    LinearLayout contextCard = new LinearLayout(activity);
    contextCard.setOrientation(LinearLayout.VERTICAL);
    contextCard.setBackground(createCardBg(activity));
    contextCard.setPadding(dp(16), dp(14), dp(16), dp(14));
    LinearLayout.LayoutParams cardParams2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    cardParams2.bottomMargin = dp(12);
    contextCard.setLayoutParams(cardParams2);

    TextView contextLabel = new TextView(activity);
    contextLabel.setText("上下文轮数");
    contextLabel.setTextSize(12);
    contextLabel.setTextColor(TEXT_SUB);
    contextCard.addView(contextLabel);

    TextView contextHint = new TextView(activity);
    contextHint.setText("AI对话记忆轮数，范围0-20，0表示无记忆");
    contextHint.setTextSize(10);
    contextHint.setTextColor(TEXT_HINT);
    contextHint.setPadding(0, dp(4), 0, dp(8));
    contextCard.addView(contextHint);

    final EditText contextInput = new EditText(activity);
    contextInput.setText(String.valueOf(getContextRounds()));
    contextInput.setTextColor(TEXT_MAIN);
    contextInput.setHint("0-20");
    contextInput.setHintTextColor(TEXT_HINT);
    contextInput.setInputType(InputType.TYPE_CLASS_NUMBER);
    contextInput.setBackground(createInputBg(activity));
    contextInput.setPadding(dp(16), dp(12), dp(16), dp(12));
    contextInput.setFocusable(true);
    contextInput.setFocusableInTouchMode(true);
    contextInput.setClickable(true);
    contextInput.setLongClickable(true);
    contextInput.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    contextCard.addView(contextInput);
    content.addView(contextCard);

    LinearLayout cooldownCard = new LinearLayout(activity);
    cooldownCard.setOrientation(LinearLayout.VERTICAL);
    cooldownCard.setBackground(createCardBg(activity));
    cooldownCard.setPadding(dp(16), dp(14), dp(16), dp(14));
    LinearLayout.LayoutParams cardParams2c = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    cardParams2c.bottomMargin = dp(12);
    cooldownCard.setLayoutParams(cardParams2c);

    TextView cooldownLabel = new TextView(activity);
    cooldownLabel.setText("拍一拍限流间隔");
    cooldownLabel.setTextSize(12);
    cooldownLabel.setTextColor(TEXT_SUB);
    cooldownCard.addView(cooldownLabel);

    TextView cooldownHint = new TextView(activity);
    cooldownHint.setText("同一作用域内两次拍一拍回复的最小间隔秒数，0表示不限流");
    cooldownHint.setTextSize(10);
    cooldownHint.setTextColor(TEXT_HINT);
    cooldownHint.setPadding(0, dp(4), 0, dp(8));
    cooldownCard.addView(cooldownHint);

    final EditText cooldownInput = new EditText(activity);
    cooldownInput.setText(String.valueOf(getPaiCooldown()));
    cooldownInput.setTextColor(TEXT_MAIN);
    cooldownInput.setHint("默认10秒");
    cooldownInput.setHintTextColor(TEXT_HINT);
    cooldownInput.setInputType(InputType.TYPE_CLASS_NUMBER);
    cooldownInput.setBackground(createInputBg(activity));
    cooldownInput.setPadding(dp(16), dp(12), dp(16), dp(12));
    cooldownInput.setFocusable(true);
    cooldownInput.setFocusableInTouchMode(true);
    cooldownInput.setClickable(true);
    cooldownInput.setLongClickable(true);
    cooldownInput.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    cooldownCard.addView(cooldownInput);
    content.addView(cooldownCard);

    LinearLayout voiceSpeedCard = new LinearLayout(activity);
    voiceSpeedCard.setOrientation(LinearLayout.VERTICAL);
    voiceSpeedCard.setBackground(createCardBg(activity));
    voiceSpeedCard.setPadding(dp(16), dp(14), dp(16), dp(14));
    LinearLayout.LayoutParams cardParamsVs = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    cardParamsVs.bottomMargin = dp(12);
    voiceSpeedCard.setLayoutParams(cardParamsVs);

    TextView voiceSpeedLabel = new TextView(activity);
    voiceSpeedLabel.setText("语音回复语速");
    voiceSpeedLabel.setTextSize(12);
    voiceSpeedLabel.setTextColor(TEXT_SUB);
    voiceSpeedCard.addView(voiceSpeedLabel);

    TextView voiceSpeedHint = new TextView(activity);
    voiceSpeedHint.setText("语音回复的播放速度，范围-100到100，负数减速正数加速");
    voiceSpeedHint.setTextSize(10);
    voiceSpeedHint.setTextColor(TEXT_HINT);
    voiceSpeedHint.setPadding(0, dp(4), 0, dp(8));
    voiceSpeedCard.addView(voiceSpeedHint);

    LinearLayout voiceSpeedRow = new LinearLayout(activity);
    voiceSpeedRow.setOrientation(LinearLayout.HORIZONTAL);
    voiceSpeedRow.setGravity(Gravity.CENTER_VERTICAL);

    int currentSpeed = getVoiceSpeed();
    final int[] selectedSign = {currentSpeed >= 0 ? 1 : 0};
    
    final TextView[] signChips = new TextView[2];
    for (int i = 0; i < 2; i++) {
        TextView signChip = new TextView(activity);
        signChip.setText(i == 0 ? "-" : "+");
        signChip.setTextSize(12);
        signChip.setTextColor(i == selectedSign[0] ? TEXT_MAIN : TEXT_SUB);
        signChip.setBackground(createChipBg(activity, i == selectedSign[0]));
        signChip.setPadding(dp(14), dp(6), dp(14), dp(6));
        signChip.setTag(Integer.valueOf(i));
        LinearLayout.LayoutParams signChipParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (i > 0) signChipParams.leftMargin = dp(6);
        signChip.setLayoutParams(signChipParams);
        signChip.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                int clickedIdx = ((Integer) v.getTag()).intValue();
                selectedSign[0] = clickedIdx;
                for (int j = 0; j < 2; j++) {
                    signChips[j].setTextColor(j == clickedIdx ? TEXT_MAIN : TEXT_SUB);
                    signChips[j].setBackground(createChipBg(activity, j == clickedIdx));
                }
            }
        });
        signChips[i] = signChip;
        voiceSpeedRow.addView(signChip);
    }

    final EditText speedInput = new EditText(activity);
    speedInput.setText(String.valueOf(Math.abs(currentSpeed)));
    speedInput.setTextColor(TEXT_MAIN);
    speedInput.setHint("0-100");
    speedInput.setHintTextColor(TEXT_HINT);
    speedInput.setInputType(InputType.TYPE_CLASS_NUMBER);
    speedInput.setBackground(createInputBg(activity));
    speedInput.setPadding(dp(16), dp(12), dp(16), dp(12));
    speedInput.setFocusable(true);
    speedInput.setFocusableInTouchMode(true);
    speedInput.setClickable(true);
    speedInput.setLongClickable(true);
    LinearLayout.LayoutParams speedInputParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    speedInputParams.leftMargin = dp(8);
    speedInput.setLayoutParams(speedInputParams);
    voiceSpeedRow.addView(speedInput);
    
    voiceSpeedCard.addView(voiceSpeedRow);
    content.addView(voiceSpeedCard);

    LinearLayout errorCard = new LinearLayout(activity);
    errorCard.setOrientation(LinearLayout.VERTICAL);
    errorCard.setBackground(createCardBg(activity));
    errorCard.setPadding(dp(16), dp(14), dp(16), dp(14));
    LinearLayout.LayoutParams cardParams3 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    cardParams3.bottomMargin = dp(12);
    errorCard.setLayoutParams(cardParams3);

    TextView errorReplyLabel = new TextView(activity);
    errorReplyLabel.setText("默认回复词");
    errorReplyLabel.setTextSize(12);
    errorReplyLabel.setTextColor(TEXT_SUB);
    errorCard.addView(errorReplyLabel);

    TextView errorHint = new TextView(activity);
    errorHint.setText("AI调用失败时发送此内容，留空则不回复");
    errorHint.setTextSize(10);
    errorHint.setTextColor(TEXT_HINT);
    errorHint.setPadding(0, dp(4), 0, dp(8));
    errorCard.addView(errorHint);

    final EditText errorReplyInput = new EditText(activity);
    errorReplyInput.setText(getErrorReply());
    errorReplyInput.setTextColor(TEXT_MAIN);
    errorReplyInput.setHint("例如：AI服务暂时不可用");
    errorReplyInput.setHintTextColor(TEXT_HINT);
    errorReplyInput.setBackground(createInputBg(activity));
    errorReplyInput.setPadding(dp(16), dp(12), dp(16), dp(12));
    errorReplyInput.setFocusable(true);
    errorReplyInput.setFocusableInTouchMode(true);
    errorReplyInput.setClickable(true);
    errorReplyInput.setLongClickable(true);
    errorReplyInput.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    errorCard.addView(errorReplyInput);
    content.addView(errorCard);

    LinearLayout defaultOnlyCard = new LinearLayout(activity);
    defaultOnlyCard.setOrientation(LinearLayout.VERTICAL);
    defaultOnlyCard.setBackground(createCardBg(activity));
    defaultOnlyCard.setPadding(dp(16), dp(14), dp(16), dp(14));
    LinearLayout.LayoutParams cardParams4 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    cardParams4.bottomMargin = dp(12);
    defaultOnlyCard.setLayoutParams(cardParams4);

    LinearLayout defaultOnlyRow = new LinearLayout(activity);
    defaultOnlyRow.setOrientation(LinearLayout.HORIZONTAL);
    defaultOnlyRow.setGravity(Gravity.CENTER_VERTICAL);

    TextView defaultOnlyLabel = new TextView(activity);
    defaultOnlyLabel.setText("仅使用默认回复");
    defaultOnlyLabel.setTextSize(12);
    defaultOnlyLabel.setTextColor(TEXT_SUB);
    defaultOnlyLabel.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    defaultOnlyRow.addView(defaultOnlyLabel);

    final TextView defaultOnlyToggle = new TextView(activity);
    defaultOnlyToggle.setText(useDefaultReplyOnly() ? "开" : "关");
    defaultOnlyToggle.setTextSize(12);
    defaultOnlyToggle.setTextColor(useDefaultReplyOnly() ? TEXT_MAIN : TEXT_SUB);
    defaultOnlyToggle.setBackground(createChipBg(activity, useDefaultReplyOnly()));
    defaultOnlyToggle.setPadding(dp(12), dp(4), dp(12), dp(4));
    defaultOnlyRow.addView(defaultOnlyToggle);
    defaultOnlyCard.addView(defaultOnlyRow);

    TextView defaultOnlyHint = new TextView(activity);
    defaultOnlyHint.setText("开启后不请求AI，直接回复默认回复词");
    defaultOnlyHint.setTextSize(10);
    defaultOnlyHint.setTextColor(TEXT_HINT);
    defaultOnlyHint.setPadding(0, dp(4), 0, 0);
    defaultOnlyCard.addView(defaultOnlyHint);

    defaultOnlyCard.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            boolean newVal = !useDefaultReplyOnly();
            putBoolean("use_default_reply_only", newVal);
            defaultOnlyToggle.setText(newVal ? "开" : "关");
            defaultOnlyToggle.setTextColor(newVal ? TEXT_MAIN : TEXT_SUB);
            defaultOnlyToggle.setBackground(createChipBg(activity, newVal));
        }
    });
    content.addView(defaultOnlyCard);

    LinearLayout privateKeywordCard = new LinearLayout(activity);
    privateKeywordCard.setOrientation(LinearLayout.VERTICAL);
    privateKeywordCard.setBackground(createCardBg(activity));
    privateKeywordCard.setPadding(dp(16), dp(14), dp(16), dp(14));
    LinearLayout.LayoutParams cardParams4b = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    cardParams4b.bottomMargin = dp(12);
    privateKeywordCard.setLayoutParams(cardParams4b);

    LinearLayout privateKeywordRow = new LinearLayout(activity);
    privateKeywordRow.setOrientation(LinearLayout.HORIZONTAL);
    privateKeywordRow.setGravity(Gravity.CENTER_VERTICAL);

    TextView privateKeywordLabel = new TextView(activity);
    privateKeywordLabel.setText("私聊不忽略关键词");
    privateKeywordLabel.setTextSize(12);
    privateKeywordLabel.setTextColor(TEXT_SUB);
    privateKeywordLabel.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    privateKeywordRow.addView(privateKeywordLabel);

    final TextView privateKeywordToggle = new TextView(activity);
    privateKeywordToggle.setText(privateChatNeedKeyword() ? "开" : "关");
    privateKeywordToggle.setTextSize(12);
    privateKeywordToggle.setTextColor(privateChatNeedKeyword() ? TEXT_MAIN : TEXT_SUB);
    privateKeywordToggle.setBackground(createChipBg(activity, privateChatNeedKeyword()));
    privateKeywordToggle.setPadding(dp(12), dp(4), dp(12), dp(4));
    privateKeywordRow.addView(privateKeywordToggle);
    privateKeywordCard.addView(privateKeywordRow);

    TextView privateKeywordHint = new TextView(activity);
    privateKeywordHint.setText("开启后私聊也需要匹配关键词才回复");
    privateKeywordHint.setTextSize(10);
    privateKeywordHint.setTextColor(TEXT_HINT);
    privateKeywordHint.setPadding(0, dp(4), 0, 0);
    privateKeywordCard.addView(privateKeywordHint);

    privateKeywordCard.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            boolean newVal = !privateChatNeedKeyword();
            putBoolean("private_chat_need_keyword", newVal);
            privateKeywordToggle.setText(newVal ? "开" : "关");
            privateKeywordToggle.setTextColor(newVal ? TEXT_MAIN : TEXT_SUB);
            privateKeywordToggle.setBackground(createChipBg(activity, newVal));
        }
    });
    content.addView(privateKeywordCard);

    LinearLayout knowledgeCard = new LinearLayout(activity);
    knowledgeCard.setOrientation(LinearLayout.VERTICAL);
    knowledgeCard.setBackground(createCardBg(activity));
    knowledgeCard.setPadding(dp(16), dp(14), dp(16), dp(14));
    LinearLayout.LayoutParams cardParams5 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    cardParams5.bottomMargin = dp(12);
    knowledgeCard.setLayoutParams(cardParams5);

    LinearLayout knowledgeRow = new LinearLayout(activity);
    knowledgeRow.setOrientation(LinearLayout.HORIZONTAL);
    knowledgeRow.setGravity(Gravity.CENTER_VERTICAL);

    TextView knowledgeLabel = new TextView(activity);
    knowledgeLabel.setText("启用知识库");
    knowledgeLabel.setTextSize(12);
    knowledgeLabel.setTextColor(TEXT_SUB);
    knowledgeLabel.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    knowledgeRow.addView(knowledgeLabel);

    final TextView knowledgeToggle = new TextView(activity);
    knowledgeToggle.setText(useKnowledgeBase() ? "开" : "关");
    knowledgeToggle.setTextSize(12);
    knowledgeToggle.setTextColor(useKnowledgeBase() ? TEXT_MAIN : TEXT_SUB);
    knowledgeToggle.setBackground(createChipBg(activity, useKnowledgeBase()));
    knowledgeToggle.setPadding(dp(12), dp(4), dp(12), dp(4));
    knowledgeRow.addView(knowledgeToggle);
    knowledgeCard.addView(knowledgeRow);

    TextView knowledgeHint = new TextView(activity);
    knowledgeHint.setText("启用知识库会导致回复变慢");
    knowledgeHint.setTextSize(10);
    knowledgeHint.setTextColor(TEXT_HINT);
    knowledgeHint.setPadding(0, dp(4), 0, 0);
    knowledgeCard.addView(knowledgeHint);

    knowledgeCard.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            boolean newVal = !useKnowledgeBase();
            putBoolean("use_knowledge_base", newVal);
            knowledgeToggle.setText(newVal ? "开" : "关");
            knowledgeToggle.setTextColor(newVal ? TEXT_MAIN : TEXT_SUB);
            knowledgeToggle.setBackground(createChipBg(activity, newVal));
        }
    });
    content.addView(knowledgeCard);

    LinearLayout webSearchCard = new LinearLayout(activity);
    webSearchCard.setOrientation(LinearLayout.VERTICAL);
    webSearchCard.setBackground(createCardBg(activity));
    webSearchCard.setPadding(dp(16), dp(14), dp(16), dp(14));
    LinearLayout.LayoutParams cardParams6 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    cardParams6.bottomMargin = dp(20);
    webSearchCard.setLayoutParams(cardParams6);

    LinearLayout webSearchRow = new LinearLayout(activity);
    webSearchRow.setOrientation(LinearLayout.HORIZONTAL);
    webSearchRow.setGravity(Gravity.CENTER_VERTICAL);

    TextView webSearchLabel = new TextView(activity);
    webSearchLabel.setText("启用联网搜索");
    webSearchLabel.setTextSize(12);
    webSearchLabel.setTextColor(TEXT_SUB);
    webSearchLabel.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    webSearchRow.addView(webSearchLabel);

    final TextView webSearchToggle = new TextView(activity);
    webSearchToggle.setText(useWebSearch() ? "开" : "关");
    webSearchToggle.setTextSize(12);
    webSearchToggle.setTextColor(useWebSearch() ? TEXT_MAIN : TEXT_SUB);
    webSearchToggle.setBackground(createChipBg(activity, useWebSearch()));
    webSearchToggle.setPadding(dp(12), dp(4), dp(12), dp(4));
    webSearchRow.addView(webSearchToggle);
    webSearchCard.addView(webSearchRow);

    TextView webSearchHint = new TextView(activity);
    webSearchHint.setText("联网搜索会导致回复变慢并且可能减弱人设效果");
    webSearchHint.setTextSize(10);
    webSearchHint.setTextColor(TEXT_HINT);
    webSearchHint.setPadding(0, dp(4), 0, 0);
    webSearchCard.addView(webSearchHint);

    webSearchCard.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            boolean newVal = !useWebSearch();
            if (newVal) {
                new AlertDialog.Builder(activity)
                    .setTitle("提示")
                    .setMessage("联网搜索会导致回复变的很慢 并且导致人设效果失效 非必要不建议开启")
                    .setPositiveButton("确定开启", new android.content.DialogInterface.OnClickListener() {
                        public void onClick(android.content.DialogInterface dialog, int which) {
                            putBoolean("enable_web_search", true);
                            webSearchToggle.setText("开");
                            webSearchToggle.setTextColor(TEXT_MAIN);
                            webSearchToggle.setBackground(createChipBg(activity, true));
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
            } else {
                putBoolean("enable_web_search", false);
                webSearchToggle.setText("关");
                webSearchToggle.setTextColor(TEXT_SUB);
                webSearchToggle.setBackground(createChipBg(activity, false));
            }
        }
    });
    content.addView(webSearchCard);

    scroll.addView(content);
    panel.addView(scroll);

    LinearLayout btnRow = new LinearLayout(activity);
    btnRow.setOrientation(LinearLayout.HORIZONTAL);
    btnRow.setGravity(Gravity.END);

    TextView cancelBtn = new TextView(activity);
    cancelBtn.setText("取消");
    cancelBtn.setTextSize(13);
    cancelBtn.setTextColor(TEXT_SUB);
    cancelBtn.setBackground(createChipBg(activity, false));
    cancelBtn.setPadding(dp(16), dp(10), dp(16), dp(10));
    cancelBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            dialog.dismiss();
        }
    });
    btnRow.addView(cancelBtn);

    TextView saveBtn = new TextView(activity);
    saveBtn.setText("保存设置");
    saveBtn.setTextSize(13);
    saveBtn.setTextColor(TEXT_MAIN);
    saveBtn.setBackground(createChipBg(activity, true));
    saveBtn.setPadding(dp(16), dp(10), dp(16), dp(10));
    LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    saveParams.leftMargin = dp(12);
    saveBtn.setLayoutParams(saveParams);
    saveBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            String apiKey = apiKeyInput.getText().toString().trim();
            String errorReply = errorReplyInput.getText().toString().trim();
            String contextStr = contextInput.getText().toString().trim();
            int contextRounds = 0;
            try {
                contextRounds = Integer.parseInt(contextStr);
            } catch (Throwable ignore) {}
            if (contextRounds < 0) contextRounds = 0;
            if (contextRounds > 20) contextRounds = 20;

            String cooldownStr = cooldownInput.getText().toString().trim();
            int cooldown = 10;
            try {
                cooldown = Integer.parseInt(cooldownStr);
            } catch (Throwable ignore) {}
            if (cooldown < 0) cooldown = 0;

            putString("api_key", apiKey);
            putString("error_reply", errorReply);
            putInt("context_rounds", contextRounds);
            putInt("pai_cooldown", cooldown);
            
            String speedStr = speedInput.getText().toString().trim();
            int speedValue = 5;
            try {
                speedValue = Integer.parseInt(speedStr);
            } catch (Throwable ignore) {}
            if (speedValue < 0) speedValue = 0;
            if (speedValue > 100) speedValue = 100;
            int finalSpeed = selectedSign[0] == 0 ? -speedValue : speedValue;
            setVoiceSpeed(finalSpeed);
            
            toast("设置已保存");
            dialog.dismiss();
        }
    });
    btnRow.addView(saveBtn);
    panel.addView(btnRow);

    root.addView(panel);
    dialog.show();

    Window window = dialog.getWindow();
    if (window != null) {
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setLayout(dp(320), ViewGroup.LayoutParams.WRAP_CONTENT);
        window.setContentView(root);
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
    }
}

void showAddRuleDialog(final Activity activity, final String scopeKey, boolean isGroup, final AlertDialog mainDialog) {
    AlertDialog dialog = new AlertDialog.Builder(activity).create();
    dialog.setCanceledOnTouchOutside(true);

    LinearLayout root = new LinearLayout(activity);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(Color.TRANSPARENT);
    root.setPadding(dp(20), dp(50), dp(20), dp(50));

    LinearLayout panel = new LinearLayout(activity);
    panel.setOrientation(LinearLayout.VERTICAL);
    panel.setBackground(createPanelBg(activity));
    panel.setPadding(dp(20), dp(24), dp(20), dp(24));

    LinearLayout headerRow = new LinearLayout(activity);
    headerRow.setOrientation(LinearLayout.HORIZONTAL);
    headerRow.setGravity(Gravity.CENTER_VERTICAL);

    TextView title = new TextView(activity);
    title.setText("添加触发规则");
    title.setTextSize(20);
    title.setTypeface(null, Typeface.BOLD);
    title.setTextColor(TEXT_MAIN);
    title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    headerRow.addView(title);

    TextView closeBtn = new TextView(activity);
    closeBtn.setText("✕");
    closeBtn.setTextSize(18);
    closeBtn.setTextColor(TEXT_SUB);
    closeBtn.setPadding(dp(8), dp(4), dp(8), dp(4));
    closeBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            dialog.dismiss();
        }
    });
    headerRow.addView(closeBtn);
    panel.addView(headerRow);

    TextView modeLabelTv = new TextView(activity);
    modeLabelTv.setText("触发方式");
    modeLabelTv.setTextSize(12);
    modeLabelTv.setTextColor(TEXT_SUB);
    modeLabelTv.setPadding(0, dp(16), 0, dp(8));
    panel.addView(modeLabelTv);

    LinearLayout modeRow = new LinearLayout(activity);
    modeRow.setOrientation(LinearLayout.HORIZONTAL);
    modeRow.setPadding(0, 0, 0, dp(8));

    final int[] selectedMode = {0};

    final TextView btnKeyword = new TextView(activity);
    btnKeyword.setText("关键词");
    btnKeyword.setTextSize(11);
    btnKeyword.setTextColor(TEXT_MAIN);
    btnKeyword.setBackground(createSelectableChipBg(activity, true, "keyword"));
    btnKeyword.setPadding(dp(10), dp(6), dp(10), dp(6));
    LinearLayout.LayoutParams btnParams1 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    btnParams1.rightMargin = dp(8);
    btnKeyword.setLayoutParams(btnParams1);
    modeRow.addView(btnKeyword);

    final TextView btnRegex = new TextView(activity);
    btnRegex.setText("正则");
    btnRegex.setTextSize(11);
    btnRegex.setTextColor(TEXT_SUB);
    btnRegex.setBackground(createSelectableChipBg(activity, false, "regex"));
    btnRegex.setPadding(dp(10), dp(6), dp(10), dp(6));
    LinearLayout.LayoutParams btnParams2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    btnParams2.rightMargin = dp(8);
    btnRegex.setLayoutParams(btnParams2);
    modeRow.addView(btnRegex);

    final TextView btnAt = new TextView(activity);
    btnAt.setText("艾特");
    btnAt.setTextSize(11);
    btnAt.setTextColor(TEXT_SUB);
    btnAt.setBackground(createSelectableChipBg(activity, false, "at"));
    btnAt.setPadding(dp(10), dp(6), dp(10), dp(6));
    LinearLayout.LayoutParams btnParams3 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    btnParams3.rightMargin = dp(8);
    btnAt.setLayoutParams(btnParams3);

    if (isGroup) {
        modeRow.addView(btnAt);
    }

    panel.addView(modeRow);

    final EditText triggerInput = new EditText(activity);
    triggerInput.setHint("触发词/正则表达式（艾特触发无需填写）");
    triggerInput.setTextColor(TEXT_MAIN);
    triggerInput.setHintTextColor(TEXT_HINT);
    triggerInput.setBackground(createInputBg(activity));
    triggerInput.setPadding(dp(16), dp(12), dp(16), dp(12));
    triggerInput.setFocusable(true);
    triggerInput.setFocusableInTouchMode(true);
    triggerInput.setClickable(true);
    triggerInput.setLongClickable(true);
    panel.addView(triggerInput);

    LinearLayout btnRow = new LinearLayout(activity);
    btnRow.setOrientation(LinearLayout.HORIZONTAL);
    btnRow.setGravity(Gravity.END);
    btnRow.setPadding(0, dp(16), 0, 0);

    TextView cancelBtn = new TextView(activity);
    cancelBtn.setText("取消");
    cancelBtn.setTextSize(13);
    cancelBtn.setTextColor(TEXT_SUB);
    cancelBtn.setBackground(createChipBg(activity, false));
    cancelBtn.setPadding(dp(16), dp(10), dp(16), dp(10));
    cancelBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            dialog.dismiss();
        }
    });
    btnRow.addView(cancelBtn);

    TextView addBtn = new TextView(activity);
    addBtn.setText("添加");
    addBtn.setTextSize(13);
    addBtn.setTextColor(TEXT_MAIN);
    addBtn.setBackground(createChipBg(activity, true));
    addBtn.setPadding(dp(16), dp(10), dp(16), dp(10));
    LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    addParams.leftMargin = dp(12);
    addBtn.setLayoutParams(addParams);
    btnRow.addView(addBtn);
    panel.addView(btnRow);

    btnKeyword.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            selectedMode[0] = 0;
            btnKeyword.setTextColor(TEXT_MAIN);
            btnKeyword.setBackground(createSelectableChipBg(activity, true, "keyword"));
            btnRegex.setTextColor(TEXT_SUB);
            btnRegex.setBackground(createSelectableChipBg(activity, false, "regex"));
            btnAt.setTextColor(TEXT_SUB);
            btnAt.setBackground(createSelectableChipBg(activity, false, "at"));
        }
    });

    btnRegex.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            selectedMode[0] = 1;
            btnKeyword.setTextColor(TEXT_SUB);
            btnKeyword.setBackground(createSelectableChipBg(activity, false, "keyword"));
            btnRegex.setTextColor(TEXT_MAIN);
            btnRegex.setBackground(createSelectableChipBg(activity, true, "regex"));
            btnAt.setTextColor(TEXT_SUB);
            btnAt.setBackground(createSelectableChipBg(activity, false, "at"));
        }
    });

    btnAt.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            selectedMode[0] = 2;
            btnKeyword.setTextColor(TEXT_SUB);
            btnKeyword.setBackground(createSelectableChipBg(activity, false, "keyword"));
            btnRegex.setTextColor(TEXT_SUB);
            btnRegex.setBackground(createSelectableChipBg(activity, false, "regex"));
            btnAt.setTextColor(TEXT_MAIN);
            btnAt.setBackground(createSelectableChipBg(activity, true, "at"));
        }
    });

    addBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            String trigger = triggerInput.getText().toString().trim();
            if (selectedMode[0] != 2 && isEmpty(trigger)) {
                toast("请输入触发词");
                return;
            }

            if (selectedMode[0] == 1) {
                try {
                    Pattern.compile(trigger);
                } catch (Throwable e) {
                    toast("正则表达式无效");
                    return;
                }
            }

            String[] modes = {"keyword", "regex", "at"};
            addRule(modes[selectedMode[0]], selectedMode[0] == 2 ? "@我" : trigger);
            toast("已添加");
            if (mainDialog != null) {
                mainDialog.dismiss();
            }
            showMainMenu();
        }
    });

    root.addView(panel);
    dialog.show();

    Window window = dialog.getWindow();
    if (window != null) {
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setLayout(dp(320), ViewGroup.LayoutParams.WRAP_CONTENT);
        window.setContentView(root);
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
    }
}

void onHandleMsg(Object msgInfoBean) {
    try {
        if (msgInfoBean == null) return;

        boolean isSend = getBoolean(msgInfoBean, "isSend");
        boolean isPrivateChat = getBoolean(msgInfoBean, "isPrivateChat");
        boolean isGroupChat = getBoolean(msgInfoBean, "isGroupChat");
        boolean isText = getBoolean(msgInfoBean, "isText");
        boolean isPat = getBoolean(msgInfoBean, "isPat");

        if (isPat) {
            handlePatMessage(msgInfoBean);
            return;
        }

        if (!isText) return;

        String talker = getString(msgInfoBean, "getTalker");
        String sender = getString(msgInfoBean, "getSendTalker");
        String content = getString(msgInfoBean, "getContent").trim();
        long msgId = getLong(msgInfoBean, "getMsgId");
        boolean isAtMe = getBoolean(msgInfoBean, "isAtMe");

        if (isEmpty(talker) || isEmpty(content)) return;

        String scopeKey = isGroupChat ? "group_" + talker : "private_" + talker;

        if (!isScopeEnabled(scopeKey)) return;

        if (isSend) {
            if (isPrivateChat) return;
            if (!isReplySelf(scopeKey)) return;
        }

        boolean matched = isPrivateChat && !privateChatNeedKeyword();

        if (!isPrivateChat || privateChatNeedKeyword()) {
            JSONArray rules = getRules();
            for (int i = 0; i < rules.length(); i++) {
                JSONObject rule = rules.optJSONObject(i);
                if (rule == null) continue;
                if (!rule.optBoolean("enabled", true)) continue;

                String mode = rule.optString("mode", "keyword");
                String trigger = rule.optString("trigger", "");

                if ("at".equals(mode)) {
                    if (isAtMe) {
                        matched = true;
                        break;
                    }
                } else if ("keyword".equals(mode)) {
                    if (!isEmpty(trigger) && content.contains(trigger)) {
                        matched = true;
                        break;
                    }
                } else if ("regex".equals(mode)) {
                    if (!isEmpty(trigger)) {
                        try {
                            if (Pattern.compile(trigger).matcher(content).find()) {
                                matched = true;
                                break;
                            }
                        } catch (Throwable ignore) {}
                    }
                }
            }
        }

        if (!matched) return;

        String cleanText = content.replaceAll("@[^\\s]+\\s*", "").trim();
        if (isEmpty(cleanText)) return;

        String reply;
        if (useDefaultReplyOnly()) {
            reply = getErrorReply();
            if (isEmpty(reply)) return;
        } else {
            reply = callAI(cleanText, null, sender);
            if (isEmpty(reply)) {
                String errorReply = getErrorReply();
                if (isEmpty(errorReply)) return;
                reply = errorReply;
            }
        }

        if (trySendVoiceReply(scopeKey, talker, reply)) {
            return;
        }

        if (isGroupChat) {
            if (isAutoQuote(scopeKey) && msgId > 0) {
                sendQuoteMsg(talker, msgId, reply);
            } else {
                sendText(talker, reply);
            }
        } else {
            sendText(talker, reply);
        }

    } catch (Throwable e) {
        log("消息处理异常: " + e.getMessage());
    }
}

void handlePatMessage(Object msgInfoBean) {
    try {
        Object patMsg = callMethod(msgInfoBean, "getPatMsg");
        if (patMsg == null) return;

        String talker = callMethod(patMsg, "getTalker");
        String fromUser = callMethod(patMsg, "getFromUser");
        String pattedUser = callMethod(patMsg, "getPattedUser");

        if (isEmpty(talker) || isEmpty(fromUser) || isEmpty(pattedUser)) return;

        boolean isGroupChat = getBoolean(msgInfoBean, "isGroupChat");
        String scopeKey = isGroupChat ? "group_" + talker : "private_" + fromUser;

        if (!isScopeEnabled(scopeKey)) return;
        if (!isPaiReply(scopeKey)) return;
        if (!canPaiReply(scopeKey)) return;

        boolean isSelfPai = fromUser.equals(pattedUser);
        if (isSelfPai && !isReplySelf(scopeKey)) return;

        String reply = callAI("[拍了你一下]", null, fromUser);
        if (isEmpty(reply)) {
            String errorReply = getErrorReply();
            if (isEmpty(errorReply)) return;
            reply = errorReply;
        }

        updatePaiReplyTime(scopeKey);

        String targetTalker = isGroupChat ? talker : fromUser;
        if (trySendVoiceReply(scopeKey, targetTalker, reply)) {
            return;
        }

        if (isGroupChat) {
            sendPat(talker, fromUser);
            sendText(talker, reply);
        } else {
            sendText(fromUser, reply);
        }

    } catch (Throwable e) {
        log("拍一拍处理异常: " + e.getMessage());
    }
}

String callAI(String message, String imageUrl, String sender) {
    String apiUrl = getApiUrl();
    String apiKey = getApiKey();

    if (isEmpty(apiUrl) || isEmpty(apiKey)) return null;

    HttpURLConnection conn = null;
    try {
        URL url = new URL(apiUrl);
        conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-API-Key", apiKey);
        conn.setDoOutput(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(60000);

        JSONObject body = new JSONObject();
        body.put("message", message);
        if (!isEmpty(imageUrl)) body.put("imageUrl", imageUrl);
        if (!isEmpty(sender)) body.put("sender", sender);
        body.put("contextRounds", getContextRounds());
        body.put("useKnowledgeBase", useKnowledgeBase());
        body.put("enableWebSearch", useWebSearch());

        OutputStream os = conn.getOutputStream();
        os.write(body.toString().getBytes("UTF-8"));
        os.close();

        if (conn.getResponseCode() != 200) return null;

        InputStream inputStream = conn.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();

        String response = sb.toString().trim();
        if (isEmpty(response)) return null;

        JSONObject json = new JSONObject(response);
        if (json.optInt("code", -1) != 200) return null;

        Object dataObj = json.opt("data");
        if (dataObj == null) return null;

        return dataObj instanceof String ? (String) dataObj : dataObj.toString();

    } catch (Throwable e) {
        log("AI调用异常: " + e.getMessage());
        return null;
    } finally {
        if (conn != null) try { conn.disconnect(); } catch (Throwable ignore) {}
    }
}

String getApiUrl() {
    return DEFAULT_API_URL;
}

String getApiKey() {
    return getString("api_key", "");
}

String getErrorReply() {
    return getString("error_reply", "");
}

int getContextRounds() {
    int rounds = getInt("context_rounds", 0);
    if (rounds < 0) return 0;
    if (rounds > 20) return 20;
    return rounds;
}

JSONArray getRules() {
    try {
        String raw = getString(RULES_KEY, "[]");
        return new JSONArray(raw);
    } catch (Throwable e) {
        return new JSONArray();
    }
}

void saveRules(JSONArray rules) {
    putString(RULES_KEY, rules == null ? "[]" : rules.toString());
}

void addRule(String mode, String trigger) {
    JSONArray rules = getRules();
    JSONObject rule = new JSONObject();
    try {
        rule.put("mode", mode);
        rule.put("trigger", trigger);
        rule.put("enabled", true);
        rules.put(rule);
        saveRules(rules);
    } catch (Throwable e) {
        log("添加规则失败: " + e.getMessage());
    }
}

void deleteRule(int index) {
    JSONArray rules = getRules();
    JSONArray newRules = new JSONArray();
    for (int i = 0; i < rules.length(); i++) {
        if (i != index) newRules.put(rules.optJSONObject(i));
    }
    saveRules(newRules);
}

boolean isScopeEnabled(String scopeKey) {
    return getBoolean("enabled_" + scopeKey, false);
}

void setScopeEnabled(String scopeKey, boolean enabled) {
    putBoolean("enabled_" + scopeKey, enabled);
}

boolean isAutoQuote(String scopeKey) {
    return getBoolean("auto_quote_" + scopeKey, true);
}

boolean isIgnoreReply(String scopeKey) {
    return getBoolean("ignore_reply_" + scopeKey, true);
}

boolean isReplySelf(String scopeKey) {
    return getBoolean("reply_self_" + scopeKey, false);
}

boolean isPaiReply(String scopeKey) {
    return getBoolean("pai_reply_" + scopeKey, false);
}

String getVoiceReply(String scopeKey) {
    String value = getString("voice_reply_" + scopeKey, "off");
    return isEmpty(value) ? "off" : value;
}

void setVoiceReply(String scopeKey, String value) {
    putString("voice_reply_" + scopeKey, value);
}

boolean useKnowledgeBase() {
    return getBoolean("use_knowledge_base", false);
}

boolean useDefaultReplyOnly() {
    return getBoolean("use_default_reply_only", false);
}

boolean privateChatNeedKeyword() {
    return getBoolean("private_chat_need_keyword", false);
}

boolean useWebSearch() {
    return getBoolean("enable_web_search", false);
}

int getPaiCooldown() {
    int cd = getInt("pai_cooldown", 10);
    return cd < 0 ? 0 : cd;
}

boolean canPaiReply(String scopeKey) {
    Long lastTime = paiLastReplyTime.get(scopeKey);
    if (lastTime == null) return true;
    int cooldown = getPaiCooldown();
    if (cooldown <= 0) return true;
    return (System.currentTimeMillis() - lastTime) > (cooldown * 1000);
}

void updatePaiReplyTime(String scopeKey) {
    paiLastReplyTime.put(scopeKey, System.currentTimeMillis());
}

int getVoiceSpeed() {
    int speed = getInt("voice_speed", -5);
    if (speed < -100) speed = -100;
    if (speed > 100) speed = 100;
    return speed;
}

void setVoiceSpeed(int speed) {
    if (speed < -100) speed = -100;
    if (speed > 100) speed = 100;
    putInt("voice_speed", speed);
}

String getVoiceSpeedParam() {
    int speed = getVoiceSpeed();
    String sign = speed >= 0 ? "%2B" : "%2D";
    return sign + Math.abs(speed) + "%";
}

boolean canVoiceReply(String scopeKey) {
    Long lastTime = voiceLastReplyTime.get(scopeKey);
    if (lastTime == null) return true;
    return (System.currentTimeMillis() - lastTime) > (VOICE_COOLDOWN * 1000);
}

void updateVoiceReplyTime(String scopeKey) {
    voiceLastReplyTime.put(scopeKey, System.currentTimeMillis());
}

boolean getBoolean(Object obj, String methodName) {
    Object result = callMethod(obj, methodName);
    return result != null && (Boolean) result;
}

String getString(Object obj, String methodName) {
    Object result = callMethod(obj, methodName);
    return result == null ? "" : result.toString();
}

long getLong(Object obj, String methodName) {
    Object result = callMethod(obj, methodName);
    if (result != null && result instanceof Number) {
        return ((Number) result).longValue();
    }
    return 0;
}

Object callMethod(Object obj, String methodName) {
    try {
        if (obj == null) return null;
        return obj.getClass().getMethod(methodName).invoke(obj);
    } catch (Exception e) {
        return null;
    }
}

boolean isEmpty(String s) {
    return s == null || s.trim().isEmpty();
}

int dp(float value) {
    return (int) (value * hostContext.getResources().getDisplayMetrics().density + 0.5f);
}

GradientDrawable createPanelBg(Context ctx) {
    GradientDrawable gd = new GradientDrawable();
    gd.setColor(BG_PANEL);
    gd.setCornerRadius(dp(20));
    return gd;
}

GradientDrawable createCardBg(Context ctx) {
    GradientDrawable gd = new GradientDrawable();
    gd.setColor(BG_CARD);
    gd.setCornerRadius(dp(12));
    return gd;
}

GradientDrawable createInputBg(Context ctx) {
    GradientDrawable gd = new GradientDrawable();
    gd.setColor(BG_INPUT);
    gd.setCornerRadius(dp(8));
    return gd;
}

GradientDrawable createChipBg(Context ctx, boolean active) {
    GradientDrawable gd = new GradientDrawable();
    if (active) {
        gd.setColor(Color.parseColor("#1A3A2A"));
        gd.setStroke(dp(1), ACCENT_GREEN);
    } else {
        gd.setColor(BG_INPUT);
    }
    gd.setCornerRadius(dp(99));
    return gd;
}

String downloadTtsVoice(String text, String voiceModel) {
    try {
        String encodedText = java.net.URLEncoder.encode(text, "UTF-8");
        String speedParam = getVoiceSpeedParam();
        String ttsUrl = "https://91yq.top/tts?text=" + encodedText + "&voice=" + voiceModel + "&rate=" + speedParam;
        
        String mp3Path = pluginDir + "/tts_" + System.currentTimeMillis() + ".mp3";
        
        HttpURLConnection conn = null;
        InputStream inputStream = null;
        java.io.FileOutputStream fos = null;
        
        try {
            URL url = new URL(ttsUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);
            
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                log("TTS响应码: " + responseCode);
                return null;
            }
            
            String contentType = conn.getContentType();
            if (contentType == null || !contentType.contains("audio")) {
                log("TTS非音频响应: " + contentType);
                return null;
            }
            
            inputStream = conn.getInputStream();
            fos = new java.io.FileOutputStream(mp3Path);
            
            byte[] buffer = new byte[4096];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                fos.write(buffer, 0, len);
            }
            fos.flush();
            
            String silkPath = mp3ToSilkPath(mp3Path);
            
            try {
                new java.io.File(mp3Path).delete();
            } catch (Throwable ignore) {}
            
            return silkPath;
        } finally {
            if (inputStream != null) try { inputStream.close(); } catch (Throwable ignore) {}
            if (fos != null) try { fos.close(); } catch (Throwable ignore) {}
            if (conn != null) try { conn.disconnect(); } catch (Throwable ignore) {}
        }
    } catch (Throwable e) {
        log("TTS异常: " + e.getMessage());
        return null;
    }
}

void sendVoiceAndDelete(String talker, String silkPath) {
    try {
        Activity activity = getTopActivity();
        if (activity != null) {
            activity.runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        sendVoice(talker, silkPath);
                    } catch (Throwable e) {
                        log("UI线程发送语音失败: " + e.getMessage());
                    }
                }
            });
        } else {
            sendVoice(talker, silkPath);
        }
        
        new Thread(new Runnable() {
            public void run() {
                try {
                    Thread.sleep(1000);
                    new java.io.File(silkPath).delete();
                } catch (Throwable ignore) {}
            }
        }).start();
    } catch (Throwable e) {
        log("发送语音失败: " + e.getMessage());
    }
}

boolean trySendVoiceReply(String scopeKey, String talker, String text) {
    String voiceSetting = getVoiceReply(scopeKey);
    if ("off".equals(voiceSetting)) {
        return false;
    }
    
    if (!canVoiceReply(scopeKey)) {
        return false;
    }
    
    String voiceModel = "male".equals(voiceSetting) ? "zh-CN-YunxiNeural" : "zh-CN-XiaoxiaoNeural";
    
    String silkPath = downloadTtsVoice(text, voiceModel);
    if (silkPath == null) {
        return false;
    }
    
    updateVoiceReplyTime(scopeKey);
    sendVoiceAndDelete(talker, silkPath);
    return true;
}

GradientDrawable createSelectableChipBg(Context ctx, boolean selected, String mode) {
    GradientDrawable gd = new GradientDrawable();
    if (selected) {
        int color = getModeColor(mode);
        gd.setColor(Color.parseColor("#1A2A3A"));
        gd.setStroke(dp(1), color);
    } else {
        gd.setColor(BG_INPUT);
        gd.setStroke(dp(1), DIVIDER);
    }
    gd.setCornerRadius(dp(8));
    return gd;
}

GradientDrawable createModeChipBg(Context ctx, String mode) {
    GradientDrawable gd = new GradientDrawable();
    gd.setColor(BG_INPUT);
    gd.setCornerRadius(dp(6));
    gd.setStroke(dp(1), getModeColor(mode));
    return gd;
}

int getModeColor(String mode) {
    if ("keyword".equals(mode)) return ACCENT_BLUE;
    if ("regex".equals(mode)) return ACCENT_GOLD;
    if ("at".equals(mode)) return ACCENT_GREEN;
    return TEXT_SUB;
}

String getModeLabel(String mode) {
    if ("keyword".equals(mode)) return "关键词";
    if ("regex".equals(mode)) return "正则";
    if ("at".equals(mode)) return "艾特";
    return mode;
}

// ==================== 定时任务核心函数 ====================

void loadTimerTasks() {
    try {
        for (ScheduledFuture<?> future : timerTaskFutures.values()) {
            if (future != null) future.cancel(false);
        }
        timerTaskFutures.clear();
        allTimerTasks.clear();

        String content = getString(TIMER_TASKS_KEY, "");
        if (isEmpty(content)) return;

        JSONObject data = new JSONObject(content);
        JSONArray tasks = data.optJSONArray("tasks");
        if (tasks == null) return;

        for (int i = 0; i < tasks.length(); i++) {
            JSONObject task = tasks.optJSONObject(i);
            if (task == null) continue;

            String id = task.optString("id", "");
            if (isEmpty(id)) continue;

            allTimerTasks.put(id, task);

            if (task.optBoolean("enabled", true)) {
                scheduleTimerTask(task);
            }
        }
    } catch (Throwable e) {
        log("加载定时任务失败: " + e.getMessage());
    }
}

void saveTimerTasks() {
    try {
        JSONObject data = new JSONObject();
        JSONArray tasks = new JSONArray();
        for (JSONObject task : allTimerTasks.values()) {
            tasks.put(task);
        }
        data.put("tasks", tasks);
        putString(TIMER_TASKS_KEY, data.toString());
    } catch (Throwable e) {
        log("保存定时任务失败: " + e.getMessage());
    }
}

long calculateNextExecTime(String startTime, int intervalMinutes) {
    String[] parts = startTime.split(":");
    int hour = Integer.parseInt(parts[0]);
    int minute = Integer.parseInt(parts[1]);

    Calendar now = Calendar.getInstance();
    Calendar next = Calendar.getInstance();
    next.set(Calendar.HOUR_OF_DAY, hour);
    next.set(Calendar.MINUTE, minute);
    next.set(Calendar.SECOND, 0);
    next.set(Calendar.MILLISECOND, 0);

    while (next.getTimeInMillis() <= now.getTimeInMillis()) {
        next.add(Calendar.MINUTE, intervalMinutes);
    }

    return next.getTimeInMillis();
}

String formatInterval(int minutes) {
    if (minutes < 60) return minutes + "分钟";
    if (minutes < 1440) return (minutes / 60) + "小时";
    if (minutes % 1440 == 0) return (minutes / 1440) + "天";
    return (minutes / 60) + "小时" + (minutes % 60) + "分钟";
}

void scheduleTimerTask(JSONObject task) {
    String id = task.optString("id", "");
    if (isEmpty(id)) return;

    if (timerTaskFutures.containsKey(id)) {
        ScheduledFuture<?> future = timerTaskFutures.get(id);
        if (future != null) future.cancel(false);
        timerTaskFutures.remove(id);
    }

    long nextExecTime = task.optLong("nextExecTime", 0);
    long now = System.currentTimeMillis();
    long delay = nextExecTime - now;

    if (delay < 0) {
        nextExecTime = calculateNextExecTime(
            task.optString("startTime", "08:00"),
            task.optInt("intervalMinutes", 1440)
        );
        task.put("nextExecTime", nextExecTime);
        delay = nextExecTime - now;
    }

    if (delay < 0) delay = 0;

    final JSONObject taskRef = task;
    Runnable runnable = new Runnable() {
        public void run() {
            executeTimerTask(taskRef);
        }
    };

    ScheduledFuture<?> future = timerScheduler.schedule(runnable, delay / 1000, TimeUnit.SECONDS);
    timerTaskFutures.put(id, future);
}

void executeTimerTask(JSONObject task) {
    try {
        String id = task.optString("id", "");

        if (!allTimerTasks.containsKey(id)) {
            return;
        }

        if (!task.optBoolean("enabled", true)) {
            return;
        }

        String target = task.optString("target", "");
        String targetType = task.optString("targetType", "group");
        String actionType = task.optString("actionType", "message");
        String actionContent = task.optString("actionContent", "");
        int intervalMinutes = task.optInt("intervalMinutes", 1440);

        task.put("lastExecTime", System.currentTimeMillis());

        if ("message".equals(actionType)) {
            sendText(target, actionContent);
        } else if ("ai".equals(actionType)) {
            String reply = callAI(actionContent, null, getLoginWxid());
            if (!isEmpty(reply)) {
                sendText(target, reply);
            }
        }

        if (!allTimerTasks.containsKey(id)) {
            return;
        }

        if (!task.optBoolean("enabled", true)) {
            return;
        }

        long nextExecTime = task.optLong("nextExecTime", 0) + intervalMinutes * 60 * 1000L;
        task.put("nextExecTime", nextExecTime);

        if (task.optBoolean("enabled", true)) {
            scheduleTimerTask(task);
        }

        saveTimerTasks();

    } catch (Throwable e) {
        log("执行定时任务失败: " + e.getMessage());
    }
}

String addTimerTask(String target, String targetType, String name,
                    String startTime, int intervalMinutes,
                    String actionType, String actionContent) {
    try {
        String id = "timer_" + UUID.randomUUID().toString().substring(0, 8);

        JSONObject task = new JSONObject();
        task.put("id", id);
        task.put("target", target);
        task.put("targetType", targetType);
        task.put("name", name);
        task.put("startTime", startTime);
        task.put("intervalMinutes", intervalMinutes);
        task.put("actionType", actionType);
        task.put("actionContent", actionContent);
        task.put("enabled", true);
        task.put("createTime", System.currentTimeMillis());
        task.put("lastExecTime", 0);

        long nextExecTime = calculateNextExecTime(startTime, intervalMinutes);
        task.put("nextExecTime", nextExecTime);

        allTimerTasks.put(id, task);
        scheduleTimerTask(task);
        saveTimerTasks();

        return id;
    } catch (Throwable e) {
        log("添加定时任务失败: " + e.getMessage());
        return null;
    }
}

void updateTimerTask(String id, String name, String startTime, int intervalMinutes,
                     String actionType, String actionContent) {
    JSONObject task = allTimerTasks.get(id);
    if (task == null) return;

    try {
        task.put("name", name);
        task.put("startTime", startTime);
        task.put("intervalMinutes", intervalMinutes);
        task.put("actionType", actionType);
        task.put("actionContent", actionContent);

        long nextExecTime = calculateNextExecTime(startTime, intervalMinutes);
        task.put("nextExecTime", nextExecTime);

        if (task.optBoolean("enabled", true)) {
            scheduleTimerTask(task);
        }

        saveTimerTasks();
    } catch (Throwable e) {
        log("更新定时任务失败: " + e.getMessage());
    }
}

void toggleTimerTask(String id) {
    JSONObject task = allTimerTasks.get(id);
    if (task == null) return;

    try {
        boolean enabled = !task.optBoolean("enabled", true);
        task.put("enabled", enabled);

        if (enabled) {
            long nextExecTime = calculateNextExecTime(
                task.optString("startTime", "08:00"),
                task.optInt("intervalMinutes", 1440)
            );
            task.put("nextExecTime", nextExecTime);
            scheduleTimerTask(task);
        } else {
            ScheduledFuture<?> future = timerTaskFutures.get(id);
            if (future != null) future.cancel(false);
            timerTaskFutures.remove(id);
        }

        saveTimerTasks();
    } catch (Throwable e) {
        log("切换定时任务状态失败: " + e.getMessage());
    }
}

void deleteTimerTask(String id) {
    ScheduledFuture<?> future = timerTaskFutures.get(id);
    if (future != null) future.cancel(false);
    timerTaskFutures.remove(id);
    allTimerTasks.remove(id);
    saveTimerTasks();
}

JSONArray getTimerTasksForTarget(String target) {
    JSONArray result = new JSONArray();
    for (JSONObject task : allTimerTasks.values()) {
        if (isEmpty(target) || target.equals(task.optString("target", ""))) {
            result.put(task);
        }
    }
    return result;
}

int getTimerTaskCount(String target) {
    int count = 0;
    for (JSONObject task : allTimerTasks.values()) {
        if (isEmpty(target) || target.equals(task.optString("target", ""))) {
            count++;
        }
    }
    return count;
}

// ==================== 定时任务弹窗界面 ====================

void showTimerTasksDialog(final Activity activity, final String target, final String targetType, final String targetName) {
    if (activity == null) return;

    activity.runOnUiThread(new Runnable() {
        public void run() {
            try {
                AlertDialog dialog = new AlertDialog.Builder(activity).create();
                dialog.setCanceledOnTouchOutside(true);

                LinearLayout root = new LinearLayout(activity);
                root.setOrientation(LinearLayout.VERTICAL);
                root.setBackgroundColor(Color.TRANSPARENT);
                root.setPadding(dp(20), dp(50), dp(20), dp(50));

                LinearLayout panel = new LinearLayout(activity);
                panel.setOrientation(LinearLayout.VERTICAL);
                panel.setBackground(createPanelBg(activity));
                panel.setPadding(dp(20), dp(24), dp(20), dp(24));

                LinearLayout headerRow = new LinearLayout(activity);
                headerRow.setOrientation(LinearLayout.HORIZONTAL);
                headerRow.setGravity(Gravity.CENTER_VERTICAL);

                TextView title = new TextView(activity);
                title.setText("定时任务");
                title.setTextSize(20);
                title.setTypeface(null, Typeface.BOLD);
                title.setTextColor(TEXT_MAIN);
                title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                headerRow.addView(title);

                TextView closeBtn = new TextView(activity);
                closeBtn.setText("✕");
                closeBtn.setTextSize(18);
                closeBtn.setTextColor(TEXT_SUB);
                closeBtn.setPadding(dp(8), dp(4), dp(8), dp(4));
                closeBtn.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        dialog.dismiss();
                    }
                });
                headerRow.addView(closeBtn);
                panel.addView(headerRow);

                TextView subLabel = new TextView(activity);
                subLabel.setText(targetName);
                subLabel.setTextSize(11);
                subLabel.setTextColor(TEXT_SUB);
                subLabel.setPadding(0, dp(8), 0, dp(16));
                panel.addView(subLabel);

                ScrollView scroll = new ScrollView(activity);
                scroll.setVerticalScrollBarEnabled(false);
                LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(350));
                scroll.setLayoutParams(scrollParams);

                LinearLayout content = new LinearLayout(activity);
                content.setOrientation(LinearLayout.VERTICAL);

                JSONArray tasks = getTimerTasksForTarget(target);

                if (tasks.length() == 0) {
                    TextView emptyHint = new TextView(activity);
                    emptyHint.setText("暂无定时任务\n点击下方按钮添加");
                    emptyHint.setTextSize(13);
                    emptyHint.setTextColor(TEXT_HINT);
                    emptyHint.setGravity(Gravity.CENTER);
                    emptyHint.setPadding(0, dp(40), 0, dp(40));
                    content.addView(emptyHint);
                } else {
                    for (int i = 0; i < tasks.length(); i++) {
                        JSONObject task = tasks.optJSONObject(i);
                        if (task == null) continue;
                        content.addView(createTimerTaskItem(activity, task, dialog, target, targetType, targetName));
                    }
                }

                scroll.addView(content);
                panel.addView(scroll);

                TextView addBtn = new TextView(activity);
                addBtn.setText("+ 添加定时任务");
                addBtn.setTextSize(13);
                addBtn.setTextColor(TEXT_MAIN);
                addBtn.setBackground(createChipBg(activity, true));
                addBtn.setGravity(Gravity.CENTER);
                addBtn.setPadding(dp(16), dp(12), dp(16), dp(12));
                LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                addParams.topMargin = dp(12);
                addBtn.setLayoutParams(addParams);

                final String t = target;
                final String tt = targetType;
                final String tn = targetName;
                final AlertDialog d = dialog;
                addBtn.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        showAddTimerTaskDialog(activity, t, tt, tn, d);
                    }
                });
                panel.addView(addBtn);

                root.addView(panel);
                dialog.show();

                Window window = dialog.getWindow();
                if (window != null) {
                    window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                    window.setLayout(dp(320), ViewGroup.LayoutParams.WRAP_CONTENT);
                    window.setContentView(root);
                }

            } catch (Throwable e) {
                toast("界面加载失败");
            }
        }
    });
}

View createTimerTaskItem(final Context ctx, final JSONObject task, final AlertDialog parentDialog,
                         final String target, final String targetType, final String targetName) {
    LinearLayout item = new LinearLayout(ctx);
    item.setOrientation(LinearLayout.VERTICAL);
    item.setBackground(createCardBg(ctx));
    item.setPadding(dp(16), dp(14), dp(16), dp(14));
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.bottomMargin = dp(8);
    item.setLayoutParams(params);

    LinearLayout headerRow = new LinearLayout(ctx);
    headerRow.setOrientation(LinearLayout.HORIZONTAL);
    headerRow.setGravity(Gravity.CENTER_VERTICAL);

    final String taskId = task.optString("id", "");
    final boolean isEnabled = task.optBoolean("enabled", true);

    TextView statusChip = new TextView(ctx);
    statusChip.setText(isEnabled ? "启用" : "禁用");
    statusChip.setTextSize(10);
    statusChip.setTextColor(isEnabled ? TEXT_MAIN : TEXT_SUB);
    statusChip.setBackground(createChipBg(ctx, isEnabled));
    statusChip.setPadding(dp(8), dp(4), dp(8), dp(4));
    headerRow.addView(statusChip);

    TextView nameTv = new TextView(ctx);
    nameTv.setText(" " + task.optString("name", "未命名"));
    nameTv.setTextSize(14);
    nameTv.setTextColor(TEXT_MAIN);
    nameTv.setTypeface(null, Typeface.BOLD);
    nameTv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    headerRow.addView(nameTv);
    item.addView(headerRow);

    LinearLayout infoRow = new LinearLayout(ctx);
    infoRow.setOrientation(LinearLayout.HORIZONTAL);
    infoRow.setGravity(Gravity.CENTER_VERTICAL);
    infoRow.setPadding(0, dp(8), 0, 0);

    String actionType = task.optString("actionType", "message");
    TextView typeChip = new TextView(ctx);
    typeChip.setText("message".equals(actionType) ? "发消息" : "问AI");
    typeChip.setTextSize(10);
    typeChip.setTextColor("message".equals(actionType) ? ACCENT_BLUE : ACCENT_GOLD);
    typeChip.setBackground(createModeChipBg(ctx, actionType));
    typeChip.setPadding(dp(6), dp(2), dp(6), dp(2));
    infoRow.addView(typeChip);

    TextView timeInfo = new TextView(ctx);
    String startTime = task.optString("startTime", "08:00");
    int interval = task.optInt("intervalMinutes", 1440);
    timeInfo.setText("  " + startTime + " 起，每" + formatInterval(interval));
    timeInfo.setTextSize(11);
    timeInfo.setTextColor(TEXT_SUB);
    infoRow.addView(timeInfo);
    item.addView(infoRow);

    TextView contentPreview = new TextView(ctx);
    String content = task.optString("actionContent", "");
    String preview = content.length() > 30 ? content.substring(0, 30) + "..." : content;
    contentPreview.setText(preview);
    contentPreview.setTextSize(11);
    contentPreview.setTextColor(TEXT_HINT);
    contentPreview.setPadding(0, dp(4), 0, dp(8));
    item.addView(contentPreview);

    LinearLayout btnRow = new LinearLayout(ctx);
    btnRow.setOrientation(LinearLayout.HORIZONTAL);
    btnRow.setGravity(Gravity.END);

    final Activity activity = (Activity) ctx;
    final JSONObject taskRef = task;

    TextView toggleBtn = new TextView(ctx);
    toggleBtn.setText(isEnabled ? "禁用" : "启用");
    toggleBtn.setTextSize(11);
    toggleBtn.setTextColor(isEnabled ? ACCENT_GOLD : ACCENT_GREEN);
    toggleBtn.setPadding(dp(12), dp(6), dp(12), dp(6));
    toggleBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            toggleTimerTask(taskId);
            parentDialog.dismiss();
            showTimerTasksDialog(activity, target, targetType, targetName);
        }
    });
    btnRow.addView(toggleBtn);

    TextView editBtn = new TextView(ctx);
    editBtn.setText("编辑");
    editBtn.setTextSize(11);
    editBtn.setTextColor(ACCENT_BLUE);
    editBtn.setPadding(dp(12), dp(6), dp(12), dp(6));
    editBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            showEditTimerTaskDialog(activity, taskRef, target, targetType, targetName, parentDialog);
        }
    });
    btnRow.addView(editBtn);

    TextView deleteBtn = new TextView(ctx);
    deleteBtn.setText("删除");
    deleteBtn.setTextSize(11);
    deleteBtn.setTextColor(ACCENT_RED);
    deleteBtn.setPadding(dp(12), dp(6), dp(12), dp(6));
    deleteBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            new AlertDialog.Builder(activity)
                .setTitle("确认删除")
                .setMessage("确定要删除这个定时任务吗？")
                .setPositiveButton("删除", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        deleteTimerTask(taskId);
                        toast("已删除");
                        parentDialog.dismiss();
                        showTimerTasksDialog(activity, target, targetType, targetName);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
        }
    });
    btnRow.addView(deleteBtn);
    item.addView(btnRow);

    return item;
}

void showAddTimerTaskDialog(final Activity activity, final String target, final String targetType,
                            final String targetName, final AlertDialog parentDialog) {
    if (activity == null) return;

    activity.runOnUiThread(new Runnable() {
        public void run() {
            try {
                AlertDialog dialog = new AlertDialog.Builder(activity).create();
                dialog.setCanceledOnTouchOutside(true);

                LinearLayout root = new LinearLayout(activity);
                root.setOrientation(LinearLayout.VERTICAL);
                root.setBackgroundColor(Color.TRANSPARENT);
                root.setPadding(dp(20), dp(50), dp(20), dp(50));

                LinearLayout panel = new LinearLayout(activity);
                panel.setOrientation(LinearLayout.VERTICAL);
                panel.setBackground(createPanelBg(activity));
                panel.setPadding(dp(20), dp(24), dp(20), dp(24));

                TextView title = new TextView(activity);
                title.setText("添加定时任务");
                title.setTextSize(20);
                title.setTypeface(null, Typeface.BOLD);
                title.setTextColor(TEXT_MAIN);
                title.setPadding(0, 0, 0, dp(16));
                panel.addView(title);

                ScrollView scroll = new ScrollView(activity);
                scroll.setVerticalScrollBarEnabled(false);
                LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(400));
                scroll.setLayoutParams(scrollParams);

                LinearLayout content = new LinearLayout(activity);
                content.setOrientation(LinearLayout.VERTICAL);

                LinearLayout nameCard = new LinearLayout(activity);
                nameCard.setOrientation(LinearLayout.VERTICAL);
                nameCard.setBackground(createCardBg(activity));
                nameCard.setPadding(dp(16), dp(14), dp(16), dp(14));
                LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                cardParams.bottomMargin = dp(12);
                nameCard.setLayoutParams(cardParams);

                TextView nameLabel = new TextView(activity);
                nameLabel.setText("任务名称");
                nameLabel.setTextSize(12);
                nameLabel.setTextColor(TEXT_SUB);
                nameCard.addView(nameLabel);

                final EditText nameInput = new EditText(activity);
                nameInput.setHint("例如：早安问候");
                nameInput.setTextColor(TEXT_MAIN);
                nameInput.setHintTextColor(TEXT_HINT);
                nameInput.setBackground(createInputBg(activity));
                nameInput.setPadding(dp(16), dp(12), dp(16), dp(12));
                nameInput.setFocusable(true);
                nameInput.setFocusableInTouchMode(true);
                nameCard.addView(nameInput);
                content.addView(nameCard);

                LinearLayout timeCard = new LinearLayout(activity);
                timeCard.setOrientation(LinearLayout.VERTICAL);
                timeCard.setBackground(createCardBg(activity));
                timeCard.setPadding(dp(16), dp(14), dp(16), dp(14));
                timeCard.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                TextView timeLabel = new TextView(activity);
                timeLabel.setText("开始时间 (HH:mm)");
                timeLabel.setTextSize(12);
                timeLabel.setTextColor(TEXT_SUB);
                timeCard.addView(timeLabel);

                LinearLayout timeInputRow = new LinearLayout(activity);
                timeInputRow.setOrientation(LinearLayout.HORIZONTAL);
                timeInputRow.setGravity(Gravity.CENTER_VERTICAL);

                final EditText hourInput = new EditText(activity);
                hourInput.setText("08");
                hourInput.setTextColor(TEXT_MAIN);
                hourInput.setInputType(InputType.TYPE_CLASS_NUMBER);
                hourInput.setBackground(createInputBg(activity));
                hourInput.setPadding(dp(16), dp(12), dp(16), dp(12));
                hourInput.setGravity(Gravity.CENTER);
                hourInput.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                timeInputRow.addView(hourInput);

                TextView colon = new TextView(activity);
                colon.setText(" : ");
                colon.setTextSize(18);
                colon.setTextColor(TEXT_SUB);
                timeInputRow.addView(colon);

                final EditText minuteInput = new EditText(activity);
                minuteInput.setText("00");
                minuteInput.setTextColor(TEXT_MAIN);
                minuteInput.setInputType(InputType.TYPE_CLASS_NUMBER);
                minuteInput.setBackground(createInputBg(activity));
                minuteInput.setPadding(dp(16), dp(12), dp(16), dp(12));
                minuteInput.setGravity(Gravity.CENTER);
                minuteInput.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                timeInputRow.addView(minuteInput);
                timeCard.addView(timeInputRow);
                content.addView(timeCard);

                LinearLayout intervalCard = new LinearLayout(activity);
                intervalCard.setOrientation(LinearLayout.VERTICAL);
                intervalCard.setBackground(createCardBg(activity));
                intervalCard.setPadding(dp(16), dp(14), dp(16), dp(14));
                intervalCard.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                TextView intervalLabel = new TextView(activity);
                intervalLabel.setText("执行间隔（分钟）");
                intervalLabel.setTextSize(12);
                intervalLabel.setTextColor(TEXT_SUB);
                intervalCard.addView(intervalLabel);

                final EditText intervalInput = new EditText(activity);
                intervalInput.setText("1440");
                intervalInput.setTextColor(TEXT_MAIN);
                intervalInput.setInputType(InputType.TYPE_CLASS_NUMBER);
                intervalInput.setBackground(createInputBg(activity));
                intervalInput.setPadding(dp(16), dp(12), dp(16), dp(12));
                intervalInput.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                intervalCard.addView(intervalInput);
                content.addView(intervalCard);

                LinearLayout typeCard = new LinearLayout(activity);
                typeCard.setOrientation(LinearLayout.VERTICAL);
                typeCard.setBackground(createCardBg(activity));
                typeCard.setPadding(dp(16), dp(14), dp(16), dp(14));
                typeCard.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                TextView typeLabel = new TextView(activity);
                typeLabel.setText("执行类型");
                typeLabel.setTextSize(12);
                typeLabel.setTextColor(TEXT_SUB);
                typeCard.addView(typeLabel);

                LinearLayout typeRow = new LinearLayout(activity);
                typeRow.setOrientation(LinearLayout.HORIZONTAL);
                typeRow.setPadding(0, dp(8), 0, 0);

                final int[] selectedType = {0};

                final TextView btnMessage = new TextView(activity);
                btnMessage.setText("发送消息");
                btnMessage.setTextSize(11);
                btnMessage.setTextColor(TEXT_MAIN);
                btnMessage.setBackground(createModeChipBg(activity, "message"));
                btnMessage.setPadding(dp(12), dp(8), dp(12), dp(8));
                typeRow.addView(btnMessage);

                final TextView btnAI = new TextView(activity);
                btnAI.setText("询问AI");
                btnAI.setTextSize(11);
                btnAI.setTextColor(TEXT_SUB);
                btnAI.setBackground(createModeChipBg(activity, "ai"));
                btnAI.setPadding(dp(12), dp(8), dp(12), dp(8));
                typeRow.addView(btnAI);
                typeCard.addView(typeRow);
                content.addView(typeCard);

                btnMessage.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        selectedType[0] = 0;
                        btnMessage.setTextColor(TEXT_MAIN);
                        btnAI.setTextColor(TEXT_SUB);
                    }
                });

                btnAI.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        selectedType[0] = 1;
                        btnMessage.setTextColor(TEXT_SUB);
                        btnAI.setTextColor(TEXT_MAIN);
                    }
                });

                LinearLayout contentCard = new LinearLayout(activity);
                contentCard.setOrientation(LinearLayout.VERTICAL);
                contentCard.setBackground(createCardBg(activity));
                contentCard.setPadding(dp(16), dp(14), dp(16), dp(14));
                contentCard.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                TextView contentLabel = new TextView(activity);
                contentLabel.setText("内容");
                contentLabel.setTextSize(12);
                contentLabel.setTextColor(TEXT_SUB);
                contentCard.addView(contentLabel);

                final EditText contentInput = new EditText(activity);
                contentInput.setHint("请输入内容");
                contentInput.setTextColor(TEXT_MAIN);
                contentInput.setHintTextColor(TEXT_HINT);
                contentInput.setBackground(createInputBg(activity));
                contentInput.setPadding(dp(16), dp(12), dp(16), dp(12));
                contentInput.setMinLines(3);
                contentInput.setGravity(Gravity.TOP | Gravity.START);
                contentInput.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                contentCard.addView(contentInput);
                content.addView(contentCard);

                scroll.addView(content);
                panel.addView(scroll);

                LinearLayout btnRow = new LinearLayout(activity);
                btnRow.setOrientation(LinearLayout.HORIZONTAL);
                btnRow.setGravity(Gravity.END);
                btnRow.setPadding(0, dp(16), 0, 0);

                TextView cancelBtn = new TextView(activity);
                cancelBtn.setText("取消");
                cancelBtn.setTextSize(13);
                cancelBtn.setTextColor(TEXT_SUB);
                cancelBtn.setBackground(createChipBg(activity, false));
                cancelBtn.setPadding(dp(16), dp(10), dp(16), dp(10));
                cancelBtn.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        dialog.dismiss();
                    }
                });
                btnRow.addView(cancelBtn);

                TextView saveBtn = new TextView(activity);
                saveBtn.setText("添加");
                saveBtn.setTextSize(13);
                saveBtn.setTextColor(TEXT_MAIN);
                saveBtn.setBackground(createChipBg(activity, true));
                saveBtn.setPadding(dp(16), dp(10), dp(16), dp(10));

                final AlertDialog d = dialog;
                final String t = target;
                final String tt = targetType;
                final String tn = targetName;
                final AlertDialog pd = parentDialog;
                saveBtn.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        String name = nameInput.getText().toString().trim();
                        String hourStr = hourInput.getText().toString().trim();
                        String minuteStr = minuteInput.getText().toString().trim();
                        String intervalStr = intervalInput.getText().toString().trim();
                        String actionContent = contentInput.getText().toString().trim();

                        if (isEmpty(name)) {
                            toast("请输入任务名称");
                            return;
                        }

                        int hour = 0, minute = 0, interval = 1440;
                        try {
                            hour = Integer.parseInt(hourStr);
                            minute = Integer.parseInt(minuteStr);
                            interval = Integer.parseInt(intervalStr);
                        } catch (Throwable e) {
                            toast("时间格式错误");
                            return;
                        }

                        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                            toast("时间范围错误");
                            return;
                        }

                        if (interval <= 0) {
                            toast("间隔必须大于0");
                            return;
                        }

                        if (isEmpty(actionContent)) {
                            toast("请输入内容");
                            return;
                        }

                        String startTime = String.format("%02d:%02d", hour, minute);
                        String actionType = selectedType[0] == 0 ? "message" : "ai";

                        String id = addTimerTask(t, tt, name, startTime, interval, actionType, actionContent);
                        if (!isEmpty(id)) {
                            toast("添加成功");
                            d.dismiss();
                            if (pd != null) pd.dismiss();
                            showTimerTasksDialog(activity, t, tt, tn);
                        } else {
                            toast("添加失败");
                        }
                    }
                });
                btnRow.addView(saveBtn);
                panel.addView(btnRow);

                root.addView(panel);
                dialog.show();

                Window window = dialog.getWindow();
                if (window != null) {
                    window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                    window.setLayout(dp(320), ViewGroup.LayoutParams.WRAP_CONTENT);
                    window.setContentView(root);
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
                    window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
                }

            } catch (Throwable e) {
                toast("界面加载失败");
            }
        }
    });
}

void showEditTimerTaskDialog(final Activity activity, final JSONObject task, final String target,
                             final String targetType, final String targetName, final AlertDialog parentDialog) {
    if (activity == null || task == null) return;

    activity.runOnUiThread(new Runnable() {
        public void run() {
            try {
                AlertDialog dialog = new AlertDialog.Builder(activity).create();
                dialog.setCanceledOnTouchOutside(true);

                LinearLayout root = new LinearLayout(activity);
                root.setOrientation(LinearLayout.VERTICAL);
                root.setBackgroundColor(Color.TRANSPARENT);
                root.setPadding(dp(20), dp(50), dp(20), dp(50));

                LinearLayout panel = new LinearLayout(activity);
                panel.setOrientation(LinearLayout.VERTICAL);
                panel.setBackground(createPanelBg(activity));
                panel.setPadding(dp(20), dp(24), dp(20), dp(24));

                TextView title = new TextView(activity);
                title.setText("编辑定时任务");
                title.setTextSize(20);
                title.setTypeface(null, Typeface.BOLD);
                title.setTextColor(TEXT_MAIN);
                title.setPadding(0, 0, 0, dp(16));
                panel.addView(title);

                ScrollView scroll = new ScrollView(activity);
                scroll.setVerticalScrollBarEnabled(false);
                LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(400));
                scroll.setLayoutParams(scrollParams);

                LinearLayout content = new LinearLayout(activity);
                content.setOrientation(LinearLayout.VERTICAL);

                String taskId = task.optString("id", "");
                String taskName = task.optString("name", "");
                String startTime = task.optString("startTime", "08:00");
                int interval = task.optInt("intervalMinutes", 1440);
                String actionType = task.optString("actionType", "message");
                String actionContent = task.optString("actionContent", "");

                String[] timeParts = startTime.split(":");
                String hourStr = timeParts.length > 0 ? timeParts[0] : "08";
                String minuteStr = timeParts.length > 1 ? timeParts[1] : "00";

                LinearLayout nameCard = new LinearLayout(activity);
                nameCard.setOrientation(LinearLayout.VERTICAL);
                nameCard.setBackground(createCardBg(activity));
                nameCard.setPadding(dp(16), dp(14), dp(16), dp(14));
                nameCard.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                TextView nameLabel = new TextView(activity);
                nameLabel.setText("任务名称");
                nameLabel.setTextSize(12);
                nameLabel.setTextColor(TEXT_SUB);
                nameCard.addView(nameLabel);

                final EditText nameInput = new EditText(activity);
                nameInput.setText(taskName);
                nameInput.setTextColor(TEXT_MAIN);
                nameInput.setBackground(createInputBg(activity));
                nameInput.setPadding(dp(16), dp(12), dp(16), dp(12));
                nameInput.setFocusable(true);
                nameInput.setFocusableInTouchMode(true);
                nameCard.addView(nameInput);
                content.addView(nameCard);

                LinearLayout timeCard = new LinearLayout(activity);
                timeCard.setOrientation(LinearLayout.VERTICAL);
                timeCard.setBackground(createCardBg(activity));
                timeCard.setPadding(dp(16), dp(14), dp(16), dp(14));
                timeCard.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                TextView timeLabel = new TextView(activity);
                timeLabel.setText("开始时间 (HH:mm)");
                timeLabel.setTextSize(12);
                timeLabel.setTextColor(TEXT_SUB);
                timeCard.addView(timeLabel);

                LinearLayout timeInputRow = new LinearLayout(activity);
                timeInputRow.setOrientation(LinearLayout.HORIZONTAL);
                timeInputRow.setGravity(Gravity.CENTER_VERTICAL);

                final EditText hourInput = new EditText(activity);
                hourInput.setText(hourStr);
                hourInput.setTextColor(TEXT_MAIN);
                hourInput.setInputType(InputType.TYPE_CLASS_NUMBER);
                hourInput.setBackground(createInputBg(activity));
                hourInput.setPadding(dp(16), dp(12), dp(16), dp(12));
                hourInput.setGravity(Gravity.CENTER);
                hourInput.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                timeInputRow.addView(hourInput);

                TextView colon = new TextView(activity);
                colon.setText(" : ");
                colon.setTextSize(18);
                colon.setTextColor(TEXT_SUB);
                timeInputRow.addView(colon);

                final EditText minuteInput = new EditText(activity);
                minuteInput.setText(minuteStr);
                minuteInput.setTextColor(TEXT_MAIN);
                minuteInput.setInputType(InputType.TYPE_CLASS_NUMBER);
                minuteInput.setBackground(createInputBg(activity));
                minuteInput.setPadding(dp(16), dp(12), dp(16), dp(12));
                minuteInput.setGravity(Gravity.CENTER);
                minuteInput.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                timeInputRow.addView(minuteInput);
                timeCard.addView(timeInputRow);
                content.addView(timeCard);

                LinearLayout intervalCard = new LinearLayout(activity);
                intervalCard.setOrientation(LinearLayout.VERTICAL);
                intervalCard.setBackground(createCardBg(activity));
                intervalCard.setPadding(dp(16), dp(14), dp(16), dp(14));
                intervalCard.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                TextView intervalLabel = new TextView(activity);
                intervalLabel.setText("执行间隔（分钟）");
                intervalLabel.setTextSize(12);
                intervalLabel.setTextColor(TEXT_SUB);
                intervalCard.addView(intervalLabel);

                final EditText intervalInput = new EditText(activity);
                intervalInput.setText(String.valueOf(interval));
                intervalInput.setTextColor(TEXT_MAIN);
                intervalInput.setInputType(InputType.TYPE_CLASS_NUMBER);
                intervalInput.setBackground(createInputBg(activity));
                intervalInput.setPadding(dp(16), dp(12), dp(16), dp(12));
                intervalInput.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                intervalCard.addView(intervalInput);
                content.addView(intervalCard);

                LinearLayout typeCard = new LinearLayout(activity);
                typeCard.setOrientation(LinearLayout.VERTICAL);
                typeCard.setBackground(createCardBg(activity));
                typeCard.setPadding(dp(16), dp(14), dp(16), dp(14));
                typeCard.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                TextView typeLabel = new TextView(activity);
                typeLabel.setText("执行类型");
                typeLabel.setTextSize(12);
                typeLabel.setTextColor(TEXT_SUB);
                typeCard.addView(typeLabel);

                LinearLayout typeRow = new LinearLayout(activity);
                typeRow.setOrientation(LinearLayout.HORIZONTAL);
                typeRow.setPadding(0, dp(8), 0, 0);

                final int[] selectedType = {"ai".equals(actionType) ? 1 : 0};

                final TextView btnMessage = new TextView(activity);
                btnMessage.setText("发送消息");
                btnMessage.setTextSize(11);
                btnMessage.setTextColor(selectedType[0] == 0 ? TEXT_MAIN : TEXT_SUB);
                btnMessage.setBackground(createModeChipBg(activity, "message"));
                btnMessage.setPadding(dp(12), dp(8), dp(12), dp(8));
                typeRow.addView(btnMessage);

                final TextView btnAI = new TextView(activity);
                btnAI.setText("询问AI");
                btnAI.setTextSize(11);
                btnAI.setTextColor(selectedType[0] == 1 ? TEXT_MAIN : TEXT_SUB);
                btnAI.setBackground(createModeChipBg(activity, "ai"));
                btnAI.setPadding(dp(12), dp(8), dp(12), dp(8));
                typeRow.addView(btnAI);
                typeCard.addView(typeRow);
                content.addView(typeCard);

                btnMessage.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        selectedType[0] = 0;
                        btnMessage.setTextColor(TEXT_MAIN);
                        btnAI.setTextColor(TEXT_SUB);
                    }
                });

                btnAI.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        selectedType[0] = 1;
                        btnMessage.setTextColor(TEXT_SUB);
                        btnAI.setTextColor(TEXT_MAIN);
                    }
                });

                LinearLayout contentCard = new LinearLayout(activity);
                contentCard.setOrientation(LinearLayout.VERTICAL);
                contentCard.setBackground(createCardBg(activity));
                contentCard.setPadding(dp(16), dp(14), dp(16), dp(14));
                contentCard.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                TextView contentLabel = new TextView(activity);
                contentLabel.setText("内容");
                contentLabel.setTextSize(12);
                contentLabel.setTextColor(TEXT_SUB);
                contentCard.addView(contentLabel);

                final EditText contentInput = new EditText(activity);
                contentInput.setText(actionContent);
                contentInput.setTextColor(TEXT_MAIN);
                contentInput.setBackground(createInputBg(activity));
                contentInput.setPadding(dp(16), dp(12), dp(16), dp(12));
                contentInput.setMinLines(3);
                contentInput.setGravity(Gravity.TOP | Gravity.START);
                contentInput.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                contentCard.addView(contentInput);
                content.addView(contentCard);

                scroll.addView(content);
                panel.addView(scroll);

                LinearLayout btnRow = new LinearLayout(activity);
                btnRow.setOrientation(LinearLayout.HORIZONTAL);
                btnRow.setGravity(Gravity.END);
                btnRow.setPadding(0, dp(16), 0, 0);

                TextView cancelBtn = new TextView(activity);
                cancelBtn.setText("取消");
                cancelBtn.setTextSize(13);
                cancelBtn.setTextColor(TEXT_SUB);
                cancelBtn.setBackground(createChipBg(activity, false));
                cancelBtn.setPadding(dp(16), dp(10), dp(16), dp(10));
                cancelBtn.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        dialog.dismiss();
                    }
                });
                btnRow.addView(cancelBtn);

                TextView saveBtn = new TextView(activity);
                saveBtn.setText("保存");
                saveBtn.setTextSize(13);
                saveBtn.setTextColor(TEXT_MAIN);
                saveBtn.setBackground(createChipBg(activity, true));
                saveBtn.setPadding(dp(16), dp(10), dp(16), dp(10));

                final AlertDialog d = dialog;
                final String t = target;
                final String tt = targetType;
                final String tn = targetName;
                final AlertDialog pd = parentDialog;
                saveBtn.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        String name = nameInput.getText().toString().trim();
                        String hourStr = hourInput.getText().toString().trim();
                        String minuteStr = minuteInput.getText().toString().trim();
                        String intervalStr = intervalInput.getText().toString().trim();
                        String newContent = contentInput.getText().toString().trim();

                        if (isEmpty(name)) {
                            toast("请输入任务名称");
                            return;
                        }

                        int hour = 0, minute = 0, newInterval = 1440;
                        try {
                            hour = Integer.parseInt(hourStr);
                            minute = Integer.parseInt(minuteStr);
                            newInterval = Integer.parseInt(intervalStr);
                        } catch (Throwable e) {
                            toast("时间格式错误");
                            return;
                        }

                        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                            toast("时间范围错误");
                            return;
                        }

                        if (newInterval <= 0) {
                            toast("间隔必须大于0");
                            return;
                        }

                        if (isEmpty(newContent)) {
                            toast("请输入内容");
                            return;
                        }

                        String newStartTime = String.format("%02d:%02d", hour, minute);
                        String newActionType = selectedType[0] == 0 ? "message" : "ai";

                        updateTimerTask(taskId, name, newStartTime, newInterval, newActionType, newContent);
                        toast("保存成功");
                        d.dismiss();
                        if (pd != null) pd.dismiss();
                        showTimerTasksDialog(activity, t, tt, tn);
                    }
                });
                btnRow.addView(saveBtn);
                panel.addView(btnRow);

                root.addView(panel);
                dialog.show();

                Window window = dialog.getWindow();
                if (window != null) {
                    window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                    window.setLayout(dp(320), ViewGroup.LayoutParams.WRAP_CONTENT);
                    window.setContentView(root);
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
                    window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
                }

            } catch (Throwable e) {
                toast("界面加载失败");
            }
        }
    });
}

View createTimerTaskEntry(final Context ctx, final String scopeKey, final boolean isGroup, final AlertDialog mainDialog) {
    LinearLayout card = new LinearLayout(ctx);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setBackground(createCardBg(ctx));
    card.setPadding(dp(16), dp(14), dp(16), dp(14));
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.bottomMargin = dp(8);
    card.setLayoutParams(params);

    String target = scopeKey.replace("group_", "").replace("private_", "");
    String targetType = isGroup ? "group" : "private";
    String targetName = isGroup ? "当前群聊" : "当前私聊";
    int taskCount = getTimerTaskCount(target);

    LinearLayout header = new LinearLayout(ctx);
    header.setOrientation(LinearLayout.HORIZONTAL);
    header.setGravity(Gravity.CENTER_VERTICAL);

    TextView title = new TextView(ctx);
    title.setText("定时任务管理");
    title.setTextSize(14);
    title.setTextColor(TEXT_MAIN);
    title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    header.addView(title);

    TextView countChip = new TextView(ctx);
    countChip.setText(taskCount + "个任务");
    countChip.setTextSize(11);
    countChip.setTextColor(TEXT_SUB);
    countChip.setBackground(createChipBg(ctx, false));
    countChip.setPadding(dp(8), dp(4), dp(8), dp(4));
    header.addView(countChip);
    card.addView(header);

    TextView hint = new TextView(ctx);
    hint.setText("设置定时发送消息或询问AI");
    hint.setTextSize(10);
    hint.setTextColor(TEXT_HINT);
    hint.setPadding(0, dp(4), 0, 0);
    card.addView(hint);

    final Activity activity = (Activity) ctx;
    final String t = target;
    final String tt = targetType;
    final String tn = targetName;
    final AlertDialog md = mainDialog;
    card.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            if (md != null) md.dismiss();
            showTimerTasksDialog(activity, t, tt, tn);
        }
    });

    return card;
}
