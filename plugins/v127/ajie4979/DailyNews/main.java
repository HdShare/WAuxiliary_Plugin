import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.function.Consumer;

import org.json.JSONArray;
import org.json.JSONObject;

private final String NEWS_API_URL = "https://60s.viki.moe/v2/60s";
private final String TASK_ID = "DAILY_NEWS_SCHEDULE";
private final String TASK_TARGET_ID = "GROUPS";
private final String DEFAULT_TIME = "08:00";
private final String MODE_TEXT = "text";
private final String MODE_IMAGE = "image";
private final String GROUP_SEPARATOR = ";;";
private final long DAY_MS = 24L * 60L * 60L * 1000L;
private final long MAX_DELAY_MS = 60000L;

private final String STORAGE_DIR = "/storage/emulated/0/Android/media/com.tencent.mm/WAuxiliary/Plugin/每日60S新闻";

private final String KEY_TASKS = "daily.news.tasks";
private final String KEY_SCHEDULE_TIME = "daily.news.schedule.time";
private final String KEY_SCHEDULE_ENABLED = "daily.news.schedule.enabled";
private final String KEY_GROUP_LIST = "daily.news.group.list";
private final String KEY_SEND_MODE = "daily.news.send.mode";
private final String KEY_DEBUG = "daily.news.debug.enabled";
private final String KEY_LAST_SUCCESS_DATE = "daily.news.last.success.date";
private final String KEY_LAST_SUCCESS_TIME = "daily.news.last.success.time";
private final String KEY_LAST_ATTEMPT_DATE = "daily.news.last.attempt.date";
private final String KEY_SUCCESS_GROUPS = "daily.news.success.groups";

private static Context appContext = null;
private AlarmManager alarmManager = null;
private PendingIntent alarmPendingIntent = null;
private BroadcastReceiver alarmReceiver = null;
private static final String ALARM_ACTION = "com.dailynews.plugin.ALARM_TRIGGER";
private final Object schedulePushLock = new Object();
private final Object runtimeCheckLock = new Object();
private boolean schedulePushRunning = false;
private boolean contextInitInProgress = false;
private int contextRetryCount = 0;
private final int MAX_CONTEXT_RETRY = 20;
private final long CONTEXT_RETRY_MS = 3000L;
private final long RUNTIME_CHECK_INTERVAL_MS = 60000L;
private final long SETTINGS_CLICK_COOLDOWN_MS = 1000L;
private final long SETTINGS_HEAVY_CLICK_COOLDOWN_MS = 1000L;
private long lastRuntimeCheckMs = 0L;
private long lastSettingsClickMs = 0L;

// ==================== 生命周期 ====================

public void onLoad() {
    writeLog("INFO", "加载", "步骤1/4：插件开始加载");
    ensureDefaults();
    writeLog("INFO", "加载", "步骤2/4：默认配置检查完成");
    contextRetryCount = 0;
    writeLog("INFO", "加载", "步骤3/4：开始初始化运行时（Context/定时）");
    ensureRuntimeReady("onLoad");
    writeLog("INFO", "加载", "步骤4/4：每日60S新闻插件加载流程结束");
}
public void onUnload() {
    writeLog("INFO", "卸载", "步骤1/3：开始卸载插件");
    unregisterAlarmReceiver();
    writeLog("INFO", "卸载", "步骤2/3：已注销定时广播接收器");
    cancelAlarm();
    contextInitInProgress = false;
    contextRetryCount = 0;
    synchronized (runtimeCheckLock) {
        lastRuntimeCheckMs = 0L;
    }
    writeLog("INFO", "卸载", "步骤3/3：已取消闹钟，插件卸载完成");
}

// ==================== 消息处理 ====================

public boolean onClickSendBtn(String text) {
    String command = safeTrim(text);
    if (!command.startsWith("/每日新闻")) return false;

    if ("/每日新闻设置".equals(command)) {
        ensureRuntimeReady("onClickSendBtn");
        writeLog("INFO", "指令", "识别为打开设置");
        showDailyNewsSettingsDialog();
        writeLog("INFO", "指令", "已拦截发送并打开设置界面");
        return true;
    }
    if ("/每日新闻发送".equals(command) || "/每日新闻".equals(command)) {
        ensureRuntimeReady("onClickSendBtn");
        String talker = getTargetTalker();
        writeLog("INFO", "指令", "识别为立即发送，目标会话=" + talker);
        pushNewsToTalker(talker, true);
        writeLog("INFO", "指令", "已拦截发送并启动新闻推送");
        return true;
    }
    if ("/每日新闻测试接口".equals(command)) {
        ensureRuntimeReady("onClickSendBtn");
        String talker = getTargetTalker();
        writeLog("INFO", "指令", "识别为接口测试，会话=" + talker);
        testNewsApi(talker);
        writeLog("INFO", "指令", "已拦截发送并启动接口测试");
        return true;
    }
    writeLog("DEBUG", "指令", "未知的 /每日新闻 指令，放行发送");
    return false;
}

public void onHandleMsg(Object msgInfoBean) {
    try {
        // 仅用于运行时恢复；指令只允许宿主输入框触发，避免群内他人误触/刷屏
        ensureRuntimeReadyThrottled("onHandleMsg");
        if (msgInfoBean == null || !msgInfoBean.isText()) return;
        String content = safeTrim(msgInfoBean.getContent());
        if (content.startsWith("/每日新闻")) {
            writeLog("DEBUG", "消息", "忽略聊天消息中的新闻指令（仅宿主发送按钮可触发）");
        }
    } catch (Throwable e) {
        writeLog("ERROR", "消息", "处理消息异常：" + String.valueOf(e));
    }
}

// ==================== 设置界面 ====================

private void showDailyNewsSettingsDialog() {
    try {
        writeLog("INFO", "设置", "步骤1/3：开始打开设置界面");
        ensureRuntimeReady("settings");
        final Activity activity = getTopActivity();
        if (activity == null) {
            toast("无法打开设置：未获取到当前界面");
            return;
        }
        try {
            if (appContext == null) {
                Context ctx = activity.getApplicationContext();
                appContext = ctx != null ? ctx : activity;
            }
        } catch (Throwable ignored) {}
        ensureDefaults();

        ScrollView scrollView = new ScrollView(activity);
        LinearLayout rootLayout = new LinearLayout(activity);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(28, 24, 28, 24);
        scrollView.addView(rootLayout);

        // 状态卡片
        LinearLayout statusCard = createCardLayout();
        statusCard.addView(createSectionTitle("📪 每日60S新闻配置"));
        final TextView statusText = new TextView(activity);
        statusText.setTextSize(14);
        statusText.setTextColor(Color.parseColor("#344054"));
        statusText.setText(buildStatusText());
        statusText.setPadding(4, 8, 4, 8);
        statusCard.addView(statusText);
        rootLayout.addView(statusCard);

        // 发送模式卡片
        LinearLayout modeCard = createCardLayout();
        modeCard.addView(createSectionTitle("📠 发送模式"));
        final RadioGroup modeGroup = new RadioGroup(activity);
        modeGroup.setOrientation(RadioGroup.HORIZONTAL);
        final RadioButton textModeButton = new RadioButton(activity);
        textModeButton.setText("新闻早报（文本）");
        textModeButton.setId(1);
        final RadioButton imageModeButton = new RadioButton(activity);
        imageModeButton.setText("新闻图片");
        imageModeButton.setId(2);
        String currentMode = getString(KEY_SEND_MODE, MODE_TEXT);
        if (MODE_IMAGE.equals(currentMode)) {
            imageModeButton.setChecked(true);
        } else {
            textModeButton.setChecked(true);
        }
        modeGroup.addView(textModeButton);
        modeGroup.addView(imageModeButton);
        modeCard.addView(modeGroup);
        // 切换模式立即生效，无需先点保存
        final boolean[] modeChangeIgnore = new boolean[] { false };
        modeGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (modeChangeIgnore[0]) return;
                if (!acceptSettingsClick("切换发送模式")) {
                    // 回滚到已保存模式，避免 UI 与配置不一致
                    modeChangeIgnore[0] = true;
                    try {
                        String saved = getSendMode();
                        if (MODE_IMAGE.equals(saved)) imageModeButton.setChecked(true);
                        else textModeButton.setChecked(true);
                    } finally {
                        modeChangeIgnore[0] = false;
                    }
                    return;
                }
                String mode = (checkedId == imageModeButton.getId()) ? MODE_IMAGE : MODE_TEXT;
                putString(KEY_SEND_MODE, mode);
                statusText.setText(buildStatusText());
                writeLog("INFO", "设置", "发送模式已切换为：" + (MODE_IMAGE.equals(mode) ? "图片" : "文本") + "（立即生效）");
                toast(MODE_IMAGE.equals(mode) ? "已切换为新闻图片模式" : "已切换为文本早报模式");
            }
        });
        rootLayout.addView(modeCard);

        // 定时设置卡片
        LinearLayout scheduleCard = createCardLayout();
        scheduleCard.addView(createSectionTitle("⏰ 定时发送"));
        final Switch scheduleSwitch = new Switch(activity);
        scheduleSwitch.setText("启用每日定时发送");
        scheduleSwitch.setChecked(getBoolean(KEY_SCHEDULE_ENABLED, false));
        // 仅作状态展示（保持默认开/关配色），实际开关由下方“启用定时/删除定时”控制
        // 不用 setEnabled(false)，否则会整控件发灰，丢失蓝色开启态
        scheduleSwitch.setClickable(false);
        scheduleSwitch.setFocusable(false);
        scheduleSwitch.setLongClickable(false);
        scheduleSwitch.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, android.view.MotionEvent event) {
                return true; // 吞掉触摸，禁止手动切换，但保留默认配色
            }
        });
        scheduleCard.addView(scheduleSwitch);

        final EditText timeEdit = createStyledEditText("输入时间（HH:mm）", getString(KEY_SCHEDULE_TIME, DEFAULT_TIME));
        timeEdit.setInputType(InputType.TYPE_CLASS_DATETIME);
        scheduleCard.addView(timeEdit);

        LinearLayout timeBtnRow = new LinearLayout(activity);
        timeBtnRow.setOrientation(LinearLayout.HORIZONTAL);
        Button enableButton = new Button(activity);
        enableButton.setText("✅ 启用定时");
        styleSmallButton(enableButton, "#E8F5E9", "#2E7D32");
        Button disableButton = new Button(activity);
        disableButton.setText("❌ 删除定时");
        styleSmallButton(disableButton, "#FFF3E0", "#E65100");
        timeBtnRow.addView(enableButton);
        timeBtnRow.addView(disableButton);
        scheduleCard.addView(timeBtnRow);
        rootLayout.addView(scheduleCard);

        // 群管理卡片
        LinearLayout groupCard = createCardLayout();
        groupCard.addView(createSectionTitle("👥 推送群管理"));
        Button addGroupButton = new Button(activity);
        // 群列表（含昵称）
        final TextView groupListText = new TextView(activity);
        groupListText.setTextSize(13);
        groupListText.setTextColor(Color.parseColor("#475467"));
        groupListText.setPadding(4, 8, 4, 12);
        groupListText.setText(buildGroupListText());
        groupCard.addView(groupListText);
        addGroupButton.setText("➕ 添加当前群");
        styleActionButton(addGroupButton, "#E8F5E9", "#2E7D32");
        Button removeGroupButton = new Button(activity);
        removeGroupButton.setText("➖ 移除当前群");
        styleActionButton(removeGroupButton, "#FFF3E0", "#E65100");
        Button clearGroupsButton = new Button(activity);
        clearGroupsButton.setText("🗑 清空群列表");
        styleActionButton(clearGroupsButton, "#FFEBEE", "#C62828");
        Button scheduleTestButton = new Button(activity);
        scheduleTestButton.setText("⏱ 定时推送测试（当前群）");
        styleActionButton(scheduleTestButton, "#E8EAF6", "#283593");
        // 排列：添加 → 移除 → 清空 → 测试
        groupCard.addView(addGroupButton);
        groupCard.addView(removeGroupButton);
        groupCard.addView(clearGroupsButton);
        groupCard.addView(scheduleTestButton);
        rootLayout.addView(groupCard);

        // 调试卡片
        LinearLayout debugCard = createCardLayout();
        debugCard.addView(createSectionTitle("🔧 调试"));
        final Switch debugSwitch = new Switch(activity);
        debugSwitch.setText("开启调试日志");
        debugSwitch.setChecked(getBoolean(KEY_DEBUG, false));
        debugCard.addView(debugSwitch);
        Button testApiButton = new Button(activity);
        testApiButton.setText("🧪 测试接口调用");
        styleActionButton(testApiButton, "#E3F2FD", "#1565C0");
        debugCard.addView(testApiButton);
        rootLayout.addView(debugCard);

        // 对话框
        AlertDialog dialog = new AlertDialog.Builder(activity)
            .setView(scrollView)
            .setPositiveButton("💾 保存设置", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int which) {
                    if (!acceptSettingsClick("保存设置")) return;
                    String inputTime = safeTrim(timeEdit.getText().toString());
                    if (scheduleSwitch.isChecked() && !isValidScheduleTime(inputTime)) {
                        toast("时间格式不正确，请使用 24 小时制（HH:mm）");
                        return;
                    }
                    writeLog("INFO", "设置", "保存开始：模式=" + (imageModeButton.isChecked() ? "图片" : "文本") + "，定时=" + scheduleSwitch.isChecked() + "，时间=" + inputTime + "，调试=" + debugSwitch.isChecked());
                    putString(KEY_SEND_MODE, imageModeButton.isChecked() ? MODE_IMAGE : MODE_TEXT);
                    putString(KEY_SCHEDULE_TIME, inputTime);
                    putBoolean(KEY_SCHEDULE_ENABLED, scheduleSwitch.isChecked());
                    putBoolean(KEY_DEBUG, debugSwitch.isChecked());
                    if (scheduleSwitch.isChecked()) {
                        writeLog("INFO", "设置", "保存步骤：启用定时并重算任务");
                        createOrUpdateScheduleTask(inputTime);
                        ensureRuntimeReady("settings-save");
                        ensureScheduler();
                        writeLog("INFO", "设置", "保存步骤：定时已重新挂载");
                    } else {
                        writeLog("INFO", "设置", "保存步骤：关闭定时并清理任务");
                        putString(KEY_TASKS, "");
                        stopScheduler();
                    }
                    writeLog("INFO", "设置", "保存完成");
                    toast("每日新闻设置已保存");
                }
            })
            .setNegativeButton("✖️ 关闭", null)
            .create();

        enableButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!acceptSettingsClick("启用定时")) return;
                String inputTime = safeTrim(timeEdit.getText().toString());
                if (!isValidScheduleTime(inputTime)) {
                    toast("时间格式不正确，请使用 24 小时制（HH:mm）");
                    return;
                }
                scheduleSwitch.setChecked(true);
                putString(KEY_SEND_MODE, imageModeButton.isChecked() ? MODE_IMAGE : MODE_TEXT);
                putString(KEY_SCHEDULE_TIME, inputTime);
                putBoolean(KEY_SCHEDULE_ENABLED, true);
                putBoolean(KEY_DEBUG, debugSwitch.isChecked());
                writeLog("INFO", "设置", "启用定时：时间=" + inputTime);
                createOrUpdateScheduleTask(inputTime);
                ensureRuntimeReady("settings-enable");
                ensureScheduler();
                statusText.setText(buildStatusText());
                writeLog("INFO", "设置", "启用定时完成");
                toast("已设置每日新闻定时发送时间：" + inputTime);
            }
        });

        disableButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!acceptSettingsClick("删除定时")) return;
                scheduleSwitch.setChecked(false);
                writeLog("INFO", "设置", "关闭定时开始");
                putBoolean(KEY_SCHEDULE_ENABLED, false);
                putString(KEY_TASKS, "");
                stopScheduler();
                statusText.setText(buildStatusText());
                writeLog("INFO", "设置", "关闭定时完成");
                toast("已删除每日新闻定时发送功能");
            }
        });

        addGroupButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!acceptSettingsClick("添加当前群")) return;
                String talker = getTargetTalker();
                writeLog("INFO", "群管理", "尝试添加当前群：" + talker);
                String added = addCurrentGroup(talker);
                if (added != null) {
                    writeLog("INFO", "群管理", "添加成功：" + added);
                    toast("已添加推送群：" + added);
                    statusText.setText(buildStatusText());
                } else {
                    writeLog("WARN", "群管理", "添加失败：当前不在群聊中或群已在列表中");
                    toast("添加失败：当前不在群聊中或群已在列表中");
                }
            }
        });

        removeGroupButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!acceptSettingsClick("移除当前群")) return;
                String talker = getTargetTalker();
                writeLog("INFO", "群管理", "尝试移除当前群：" + talker);
                String removed = removeCurrentGroup(talker);
                if (removed != null) {
                    writeLog("INFO", "群管理", "移除成功：" + removed);
                    toast("已移除推送群：" + removed);
                    statusText.setText(buildStatusText());
                } else {
                    writeLog("WARN", "群管理", "移除失败：当前不在群聊中或群不在列表中");
                    toast("移除失败：当前不在群聊中或群不在列表中");
                }
            }
        });


        scheduleTestButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!acceptSettingsClick("定时推送测试", SETTINGS_HEAVY_CLICK_COOLDOWN_MS)) return;
                String talker = getTargetTalker();
                if (!isGroupTalker(talker)) {
                    toast("请在群聊中使用定时测试");
                    return;
                }
                // 以当前界面选项为准，避免“切了模式但未保存”导致测到旧配置
                String mode = imageModeButton.isChecked() ? MODE_IMAGE : MODE_TEXT;
                putString(KEY_SEND_MODE, mode);
                statusText.setText(buildStatusText());
                writeLog("INFO", "测试", "定时推送测试开始，目标群=" + talker + "，模式=" + (MODE_IMAGE.equals(mode) ? "图片" : "文本"));
                toast("定时测试：正在以" + (MODE_IMAGE.equals(mode) ? "图片" : "文本") + "模式向当前群发送...");
                pushNewsToTalker(talker, true);
                writeLog("INFO", "测试", "定时推送测试已启动发送流程");
            }
        });

        clearGroupsButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!acceptSettingsClick("清空群列表")) return;
                writeLog("INFO", "群管理", "清空全部推送群");
                putString(KEY_GROUP_LIST, "");
                groupListText.setText(buildGroupListText());
                statusText.setText(buildStatusText());
                writeLog("INFO", "群管理", "群列表已清空");
                toast("已清空所有推送群");
            }
        });

        testApiButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!acceptSettingsClick("测试接口", SETTINGS_HEAVY_CLICK_COOLDOWN_MS)) return;
                String talker = getTargetTalker();
                testNewsApi(talker);
            }
        });

        dialog.show();
        styleDialogButtons(dialog);
        writeLog("INFO", "设置", "步骤2/3：设置界面已显示");
        writeLog("INFO", "设置", "步骤3/3：可在界面中修改模式/定时/群列表/调试开关");
    } catch (Throwable e) {
        writeLog("ERROR", "设置", "打开设置界面失败：" + String.valueOf(e));
        try { toast("打开设置失败：" + e.getMessage()); } catch (Throwable ignored) {}
    }
}


// ==================== 补发机制 ====================

private String todayDateKey() {
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
    sdf.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
    return sdf.format(new Date());
}

private String formatDateTime(long timeMs) {
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
    sdf.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
    return sdf.format(new Date(timeMs));
}

private List getTodaySuccessGroups() {
    List groups = new ArrayList();
    String raw = getString(KEY_SUCCESS_GROUPS, "");
    if (TextUtils.isEmpty(raw)) return groups;
    try {
        String[] parts = raw.split("\\|", 2);
        if (parts.length < 1) return groups;
        if (!todayDateKey().equals(parts[0])) return groups;
        if (parts.length < 2 || TextUtils.isEmpty(parts[1])) return groups;
        String[] ids = parts[1].split(GROUP_SEPARATOR);
        for (int i = 0; i < ids.length; i++) {
            String id = safeTrim(ids[i]);
            if (!TextUtils.isEmpty(id) && !groups.contains(id)) groups.add(id);
        }
    } catch (Throwable ignored) {}
    return groups;
}

private void saveTodaySuccessGroups(List groups) {
    StringBuilder sb = new StringBuilder();
    sb.append(todayDateKey()).append("|");
    if (groups != null) {
        for (int i = 0; i < groups.size(); i++) {
            if (i > 0) sb.append(GROUP_SEPARATOR);
            sb.append(String.valueOf(groups.get(i)));
        }
    }
    putString(KEY_SUCCESS_GROUPS, sb.toString());
}

private void addTodaySuccessGroup(String groupId) {
    if (TextUtils.isEmpty(groupId)) return;
    List groups = getTodaySuccessGroups();
    if (!groups.contains(groupId)) {
        groups.add(groupId);
        saveTodaySuccessGroups(groups);
    }
}

private boolean hasLegacyPushedToday() {
    return todayDateKey().equals(getString(KEY_LAST_SUCCESS_DATE, ""));
}

private boolean hasPushedToday() {
    List targets = getGroupList();
    if (targets.isEmpty()) return false;

    List success = getTodaySuccessGroups();
    if (!success.isEmpty()) {
        for (int i = 0; i < targets.size(); i++) {
            if (!success.contains(String.valueOf(targets.get(i)))) return false;
        }
        return true;
    }
    // 兼容旧版：仅有“今日成功日期”、没有分群记录时，视为整日已完成
    return hasLegacyPushedToday();
}

private void markPushSuccess() {
    long now = System.currentTimeMillis();
    putString(KEY_LAST_SUCCESS_DATE, todayDateKey());
    putString(KEY_LAST_SUCCESS_TIME, formatDateTime(now));
    putString(KEY_LAST_ATTEMPT_DATE, todayDateKey());
    List targets = getGroupList();
    List success = getTodaySuccessGroups();
    for (int i = 0; i < targets.size(); i++) {
        String id = String.valueOf(targets.get(i));
        if (!success.contains(id)) success.add(id);
    }
    saveTodaySuccessGroups(success);
}

private void markPushAttempt() {
    putString(KEY_LAST_ATTEMPT_DATE, todayDateKey());
}

private long todayScheduleTimeMs(String scheduleTime) {
    String[] pieces = scheduleTime.split(":");
    Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"), Locale.CHINA);
    cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(pieces[0]));
    cal.set(Calendar.MINUTE, Integer.parseInt(pieces[1]));
    cal.set(Calendar.SECOND, 0);
    cal.set(Calendar.MILLISECOND, 0);
    return cal.getTimeInMillis();
}

private void catchUpMissedSchedule() {
    if (!getBoolean(KEY_SCHEDULE_ENABLED, false)) {
        // 常规跳过不打日志，避免刷屏
        return;
    }
    String scheduleTime = getString(KEY_SCHEDULE_TIME, DEFAULT_TIME);
    if (!isValidScheduleTime(scheduleTime)) scheduleTime = DEFAULT_TIME;
    try {
        long todayScheduled = todayScheduleTimeMs(scheduleTime);
        long nowMs = System.currentTimeMillis();

        // 未到点 / 今日已完成：静默跳过，不写日志
        if (nowMs < todayScheduled) return;
        if (hasPushedToday()) return;

        synchronized (schedulePushLock) {
            if (schedulePushRunning) {
                writeLog("DEBUG", "补发", "已有推送任务执行中，跳过重复补发");
                return;
            }
        }

        long missed = nowMs - todayScheduled;
        writeLog("INFO", "补发", "检测到今日未完成推送（迟到" + (missed / 1000) + "秒），准备补发/续推");
        new Thread(new Runnable() {
            public void run() {
                writeLog("INFO", "补发", "开始执行补发推送");
                executeScheduledPushSync();
            }
        }).start();
    } catch (Throwable e) {
        writeLog("ERROR", "补发", "补发检查异常：" + String.valueOf(e));
    }
}
// ==================== 配置初始化 ====================


private Context resolveContextFromTopActivity() {
    try {
        Activity activity = getTopActivity();
        if (activity != null) {
            Context ctx = activity.getApplicationContext();
            if (ctx != null) return ctx;
            return activity;
        }
    } catch (Throwable e) {
        writeLog("DEBUG", "初始化", "通过顶部界面获取上下文失败：" + String.valueOf(e));
    }
    return null;
}

private Context resolveContextFromActivityThread() {
    try {
        Class activityThreadClass = Class.forName("android.app.ActivityThread");
        Object activityThread = null;
        try {
            java.lang.reflect.Method current = activityThreadClass.getMethod("currentActivityThread");
            activityThread = current.invoke(null);
        } catch (Throwable ignored) {}
        if (activityThread == null) {
            try {
                java.lang.reflect.Field sThread = activityThreadClass.getDeclaredField("sCurrentActivityThread");
                sThread.setAccessible(true);
                activityThread = sThread.get(null);
            } catch (Throwable ignored) {}
        }
        if (activityThread == null) return null;

        try {
            java.lang.reflect.Method getApp = activityThreadClass.getMethod("getApplication");
            Object app = getApp.invoke(activityThread);
            if (app instanceof Context) return (Context) app;
        } catch (Throwable ignored) {}

        try {
            java.lang.reflect.Field mInitial = activityThreadClass.getDeclaredField("mInitialApplication");
            mInitial.setAccessible(true);
            Object app = mInitial.get(activityThread);
            if (app instanceof Context) return (Context) app;
        } catch (Throwable ignored) {}

        try {
            java.lang.reflect.Field mBound = activityThreadClass.getDeclaredField("mBoundApplication");
            mBound.setAccessible(true);
            Object app = mBound.get(activityThread);
            if (app instanceof Context) return (Context) app;
        } catch (Throwable ignored) {}
    } catch (Throwable e) {
        writeLog("DEBUG", "初始化", "系统上下文反射失败：" + String.valueOf(e));
    }
    return null;
}

private Context resolveContextFromSystemServices() {
    try {
        Class activityThreadClass = Class.forName("android.app.ActivityThread");
        java.lang.reflect.Method currentApp = activityThreadClass.getMethod("currentApplication");
        Object app = currentApp.invoke(null);
        if (app instanceof Context) return (Context) app;
    } catch (Throwable e) {
        writeLog("DEBUG", "初始化", "系统当前应用上下文获取失败：" + String.valueOf(e));
    }
    return null;
}

private void initAppContext() {
    if (appContext != null) return;
    try {
        Context ctx = resolveContextFromTopActivity();
        if (ctx == null) ctx = resolveContextFromSystemServices();
        if (ctx == null) ctx = resolveContextFromActivityThread();
        if (ctx != null) {
            try {
                Context app = ctx.getApplicationContext();
                appContext = app != null ? app : ctx;
            } catch (Throwable e) {
                appContext = ctx;
            }
            writeLog("INFO", "初始化", "上下文已就绪：" + String.valueOf(appContext.getClass().getName()));
        }
    } catch (Throwable e) {
        writeLog("ERROR", "初始化", "获取上下文失败：" + String.valueOf(e));
    }
}

private boolean ensureRuntimeReady(String reason) {
    boolean firstReady = (appContext == null);
    initAppContext();
    if (appContext != null) {
        contextRetryCount = 0;
        contextInitInProgress = false;
        boolean needBootstrap = firstReady || alarmReceiver == null;
        if (needBootstrap) {
            registerAlarmReceiver();
            ensureScheduler();
            writeLog("INFO", "初始化", "运行时就绪完成，来源=" + reason);
        } else if ("onLoad".equals(reason) || (reason != null && reason.indexOf("retry") >= 0)) {
            // 进程恢复时即使 Receiver 还在，也要重挂闹钟并检查补发
            ensureScheduler();
        }
        // 动态广播在进程被杀后不可靠：每次运行时就绪都检查补发（内部有去重/互斥）
        catchUpMissedSchedule();
        return true;
    }
    scheduleContextRetry(reason);
    return false;
}

private boolean ensureRuntimeReadyThrottled(String reason) {
    long now = System.currentTimeMillis();
    synchronized (runtimeCheckLock) {
        long elapsed = now - lastRuntimeCheckMs;
        if (lastRuntimeCheckMs > 0L && elapsed >= 0L && elapsed < RUNTIME_CHECK_INTERVAL_MS) {
            return appContext != null;
        }
        lastRuntimeCheckMs = now;
    }
    return ensureRuntimeReady(reason);
}

private void scheduleContextRetry(String reason) {
    if (contextInitInProgress) return;
    if (contextRetryCount >= MAX_CONTEXT_RETRY) {
        writeLog("ERROR", "加载", "上下文多次初始化失败（已重试" + contextRetryCount + "次，来源=" + reason + "），请打开微信界面后再操作一次");
        return;
    }
    contextInitInProgress = true;
    contextRetryCount++;
    final int attempt = contextRetryCount;
    writeLog("WARN", "加载", "上下文未就绪，第" + attempt + "次重试（来源=" + reason + "）");
    try {
        delay(CONTEXT_RETRY_MS, new Runnable() {
            public void run() {
                contextInitInProgress = false;
                initAppContext();
                if (appContext != null) {
                    contextRetryCount = 0;
                    registerAlarmReceiver();
                    ensureScheduler();
                    catchUpMissedSchedule();
                    writeLog("INFO", "加载", "延迟初始化成功，定时已启用（第" + attempt + "次重试）");
                } else {
                    scheduleContextRetry("retry#" + attempt);
                }
            }
        });
    } catch (Throwable e) {
        // delay 不可用时回退到主线程 Handler
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            public void run() {
                contextInitInProgress = false;
                initAppContext();
                if (appContext != null) {
                    contextRetryCount = 0;
                    registerAlarmReceiver();
                    ensureScheduler();
                    catchUpMissedSchedule();
                    writeLog("INFO", "加载", "延迟初始化成功（系统延迟回退），定时已启用");
                } else {
                    scheduleContextRetry("handler-retry#" + attempt);
                }
            }
        }, CONTEXT_RETRY_MS);
    }
}

private void ensureDefaults() {
    if (TextUtils.isEmpty(getString(KEY_SEND_MODE, ""))) putString(KEY_SEND_MODE, MODE_TEXT);
    if (TextUtils.isEmpty(getString(KEY_SCHEDULE_TIME, ""))) putString(KEY_SCHEDULE_TIME, DEFAULT_TIME);
    ensureStorageDir();
}

private String buildStatusText() {
    String lastSuccess = getString(KEY_LAST_SUCCESS_TIME, "");
    if (TextUtils.isEmpty(lastSuccess)) lastSuccess = "无";
    int totalGroups = getGroupList().size();
    int successGroups = getTodaySuccessGroups().size();
    if (hasLegacyPushedToday() && successGroups <= 0) successGroups = totalGroups;
    String todayState;
    if (totalGroups <= 0) todayState = "无推送群";
    else if (hasPushedToday()) todayState = "是（" + successGroups + "/" + totalGroups + "）";
    else if (successGroups > 0) todayState = "部分（" + successGroups + "/" + totalGroups + "）";
    else todayState = "否（0/" + totalGroups + "）";
    return "当前模式：" + (MODE_IMAGE.equals(getSendMode()) ? "新闻图片" : "新闻早报（文本）")
        + "\n定时状态：" + (getBoolean(KEY_SCHEDULE_ENABLED, false) ? "已开启" : "未开启")
        + "\n定时时间：" + getString(KEY_SCHEDULE_TIME, DEFAULT_TIME)
        + "\n今日已推送：" + todayState
        + "\n上次成功：" + lastSuccess
        + "\n推送群数：" + totalGroups
        + "\n" + buildGroupListText()
        + "\n缓存目录：" + getStorageDirPath();
}
private String getSendMode() {
    String mode = getString(KEY_SEND_MODE, MODE_TEXT);
    return MODE_IMAGE.equals(mode) ? MODE_IMAGE : MODE_TEXT;
}

private boolean isValidScheduleTime(String time) {
    return time != null && time.matches("^([01][0-9]|2[0-3]):[0-5][0-9]$");
}

// ==================== 定时调度（AlarmManager） ====================

private long nextTimeMs(String scheduleTime) {
    try {
        String[] pieces = scheduleTime.split(":");
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"), Locale.CHINA);
        calendar.set(Calendar.HOUR_OF_DAY, Integer.parseInt(pieces[0]));
        calendar.set(Calendar.MINUTE, Integer.parseInt(pieces[1]));
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long timeMs = calendar.getTimeInMillis();
        long nowMs = System.currentTimeMillis();
        while (timeMs <= nowMs) timeMs += DAY_MS;
        return timeMs;
    } catch (Throwable e) {
        writeLog("ERROR", "定时", "计算下次时间失败：" + e);
        return System.currentTimeMillis() + DAY_MS;
    }
}

private void createOrUpdateScheduleTask(String scheduleTime) {
    long timeMs = nextTimeMs(scheduleTime);
    putString(KEY_TASKS, TASK_ID + "|" + TASK_TARGET_ID + "|群列表|" + timeMs + "|1");
}

private void ensureScheduler() {
    if (!getBoolean(KEY_SCHEDULE_ENABLED, false)) {
        writeLog("DEBUG", "定时", "定时开关关闭，跳过挂载闹钟");
        return;
    }
    writeLog("INFO", "定时", "步骤1/5：开始挂载每日定时");
    cancelAlarm();
    writeLog("INFO", "定时", "步骤2/5：已取消旧闹钟");

    String scheduleTime = getString(KEY_SCHEDULE_TIME, DEFAULT_TIME);
    if (!isValidScheduleTime(scheduleTime)) scheduleTime = DEFAULT_TIME;

    // 始终按当前时间重算下次触发点，避免任务时间回退导致只推一天
    long timeMs = nextTimeMs(scheduleTime);
    putString(KEY_TASKS, TASK_ID + "|" + TASK_TARGET_ID + "|群列表|" + timeMs + "|1");
    writeLog("INFO", "定时", "步骤3/5：已计算下次触发时间=" + formatDateTime(timeMs) + "（设定=" + scheduleTime + "）");

    if (appContext == null) {
        writeLog("ERROR", "定时", "应用上下文未初始化，无法设置定时，转入重试");
        scheduleContextRetry("ensureScheduler");
        return;
    }

    try {
        alarmManager = (AlarmManager) appContext.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(ALARM_ACTION);
        intent.setPackage(appContext.getPackageName());
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        alarmPendingIntent = PendingIntent.getBroadcast(appContext, 10086, intent, flags);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
        writeLog("INFO", "定时", "准备设置系统闹钟：目标=" + scheduleTime
            + "，计算时间=" + sdf.format(new Date(timeMs))
            + "，当前时间=" + sdf.format(new Date())
            + "，延迟=" + ((timeMs - System.currentTimeMillis()) / 1000) + "秒");

        boolean scheduled = false;
        // Android 6+ 优先 setAlarmClock，对 Doze/息屏更可靠
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                AlarmManager.AlarmClockInfo clockInfo = new AlarmManager.AlarmClockInfo(timeMs, alarmPendingIntent);
                alarmManager.setAlarmClock(clockInfo, alarmPendingIntent);
                scheduled = true;
                writeLog("INFO", "定时", "步骤4/5：使用系统精确闹钟设置成功");
            } catch (Throwable e) {
                writeLog("WARN", "定时", "系统精确闹钟失败，回退普通精确闹钟：" + String.valueOf(e));
            }
        }
        if (!scheduled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMs, alarmPendingIntent);
            scheduled = true;
        } else if (!scheduled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, timeMs, alarmPendingIntent);
            scheduled = true;
        } else if (!scheduled) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, timeMs, alarmPendingIntent);
            scheduled = true;
        }

        writeLog("INFO", "定时", "步骤5/5：系统闹钟已设置，设定时间=" + scheduleTime + "，触发时间=" + sdf.format(new Date(timeMs)));
    } catch (Throwable e) {
        writeLog("ERROR", "定时", "系统闹钟设置失败：" + String.valueOf(e));
    }
}


private void rescheduleNextDay() {
    if (getBoolean(KEY_SCHEDULE_ENABLED, false)) {
        createOrUpdateScheduleTask(getString(KEY_SCHEDULE_TIME, DEFAULT_TIME));
        ensureScheduler();
    }
}

private long parseTaskTimeMs(String taskLine) {
    try {
        if (TextUtils.isEmpty(taskLine)) return nextTimeMs(getString(KEY_SCHEDULE_TIME, DEFAULT_TIME));
        String[] pieces = taskLine.split("\\|");
        return Long.parseLong(pieces[3]);
    } catch (Throwable e) {
        return nextTimeMs(getString(KEY_SCHEDULE_TIME, DEFAULT_TIME));
    }
}

private void stopScheduler() {
    cancelAlarm();
}

private boolean sendScheduledToGroup(String groupId, String mode, String text, String imagePath) {
    if (MODE_IMAGE.equals(mode) && !TextUtils.isEmpty(imagePath)) {
        return safeSendImage(groupId, imagePath);
    }
    return safeSendText(groupId, text);
}

private int pushPendingGroups(List groups, List alreadySuccess, String mode, String text, String imagePath) {
    int newSuccessCount = 0;
    for (int i = 0; i < groups.size(); i++) {
        String groupId = String.valueOf(groups.get(i));
        if (alreadySuccess.contains(groupId)) {
            writeLog("INFO", "定时推送", "步骤7/8：群已成功，跳过 " + groupId);
            continue;
        }
        boolean ok = sendScheduledToGroup(groupId, mode, text, imagePath);
        if (ok) {
            addTodaySuccessGroup(groupId);
            alreadySuccess.add(groupId);
            newSuccessCount++;
            writeLog("INFO", "定时推送", "步骤7/8：已发送至 " + groupId);
        } else {
            writeLog("ERROR", "定时推送", "步骤7/8：发送失败 " + groupId);
        }
        try { Thread.sleep(800); } catch (Throwable ignored) {}
    }
    return newSuccessCount;
}

private void executeScheduledPushSync() {
    synchronized (schedulePushLock) {
        if (schedulePushRunning) {
            writeLog("DEBUG", "定时推送", "已有推送任务执行中，忽略重复调用");
            return;
        }
        schedulePushRunning = true;
    }
    try {
        if (hasPushedToday()) {
            writeLog("INFO", "定时推送", "步骤1/8：今日目标群均已成功，跳过重复执行");
            return;
        }
        writeLog("INFO", "定时推送", "步骤1/8：开始执行定时推送");

        List groups = getGroupList();
        if (groups.isEmpty()) {
            writeLog("WARN", "定时推送", "步骤2/8：推送群列表为空，跳过执行");
            return;
        }

        List alreadySuccess = getTodaySuccessGroups();
        int pending = 0;
        for (int i = 0; i < groups.size(); i++) {
            if (!alreadySuccess.contains(String.valueOf(groups.get(i)))) pending++;
        }
        writeLog("INFO", "定时推送", "步骤2/8：待推送群数量=" + pending + "，今日已成功=" + alreadySuccess.size());
        if (pending <= 0) {
            markPushSuccess();
            writeLog("INFO", "定时推送", "步骤8/8：无待推送群，已对齐成功标记");
            return;
        }

        markPushAttempt();
        writeLog("INFO", "定时推送", "步骤3/8：开始请求新闻接口");

        String newsBody = syncHttpGet(NEWS_API_URL);
        if (newsBody == null) {
            writeLog("ERROR", "定时推送", "步骤3/8：新闻接口请求失败");
            return;
        }
        writeLog("INFO", "定时推送", "步骤3/8：新闻接口请求成功，开始解析");

        try {
            JSONObject json = new JSONObject(newsBody);
            int code = json.optInt("code", -1);
            if (code != 200) {
                writeLog("ERROR", "定时推送", "步骤4/8：接口错误码=" + code);
                return;
            }
            JSONObject data = json.optJSONObject("data");
            if (data == null) {
                writeLog("ERROR", "定时推送", "步骤4/8：data字段为空");
                return;
            }
            writeLog("INFO", "定时推送", "步骤4/8：数据解析成功");

            String mode = getSendMode();
            writeLog("INFO", "定时推送", "步骤5/8：发送模式=" + (MODE_IMAGE.equals(mode) ? "图片" : "文本"));

            String text = buildNewsText(data);
            String imagePath = "";
            String effectiveMode = MODE_TEXT;

            if (MODE_IMAGE.equals(mode)) {
                String imageUrl = extractImageUrl(data);
                if (TextUtils.isEmpty(imageUrl)) {
                    writeLog("WARN", "定时推送", "步骤6/8：图片地址为空，回退文本模式");
                    effectiveMode = MODE_TEXT;
                } else {
                    imagePath = getStorageDirPath() + "/schedule_image_" + System.currentTimeMillis() + ".png";
                    writeLog("INFO", "定时推送", "步骤6/8：开始下载图片 " + imageUrl);
                    boolean downloaded = syncDownload(imageUrl, imagePath);
                    if (downloaded && new File(imagePath).exists() && new File(imagePath).length() > 0) {
                        effectiveMode = MODE_IMAGE;
                        writeLog("INFO", "定时推送", "步骤6/8：图片下载成功");
                    } else {
                        writeLog("ERROR", "定时推送", "步骤6/8：图片下载失败，回退文本模式");
                        effectiveMode = MODE_TEXT;
                        imagePath = "";
                    }
                }
            } else {
                writeLog("INFO", "定时推送", "步骤6/8：使用文本模式");
            }

            int newSuccess = pushPendingGroups(groups, alreadySuccess, effectiveMode, text, imagePath);

            if (MODE_IMAGE.equals(effectiveMode) && !TextUtils.isEmpty(imagePath)) {
                final String cleanupPath = imagePath;
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    public void run() {
                        try { new File(cleanupPath).delete(); } catch (Throwable e) {}
                    }
                }, 10000);
            }

            if (hasPushedToday()) {
                markPushSuccess();
                writeLog("INFO", "定时推送", "步骤8/8：今日全部目标群推送完成，新增成功=" + newSuccess);
            } else if (newSuccess > 0) {
                writeLog("WARN", "定时推送", "步骤8/8：部分群成功（新增" + newSuccess + "），未完成群后续可补发");
            } else {
                writeLog("ERROR", "定时推送", "步骤8/8：本次无新增成功，未完成群后续可补发");
            }
        } catch (Throwable e) {
            writeLog("ERROR", "定时推送", "数据处理失败：" + String.valueOf(e));
        }
    } finally {
        synchronized (schedulePushLock) {
            schedulePushRunning = false;
        }
    }
}
// 构建新闻文本
private String buildNewsText(JSONObject data) {
    if (data == null) return "新闻数据为空";
    StringBuilder sb = new StringBuilder();
    sb.append("📰  每日60S新闻早报\n");
    sb.append("━━━━━━━━━━━━━━\n");
    sb.append("📅 ").append(data.optString("date", ""));
    String dayOfWeek = data.optString("day_of_week", "");
    String lunarDate = data.optString("lunar_date", "");
    if (!TextUtils.isEmpty(dayOfWeek)) sb.append(" ｜ ").append(dayOfWeek);
    if (!TextUtils.isEmpty(lunarDate)) sb.append(" ｜ ").append(lunarDate);
    sb.append("\n\n");
    sb.append("🔥 今日要闻\n");

    JSONArray news = data.optJSONArray("news");
    if (news != null && news.length() > 0) {
        for (int i = 0; i < news.length(); i++) {
            sb.append(formatNewsIndex(i + 1)).append(" ").append(news.optString(i, "")).append("\n");
            if (i != news.length() - 1) sb.append("\n");
        }
    }

    String tip = data.optString("tip", "");
    if (!TextUtils.isEmpty(tip)) {
        sb.append("━━━━━━━━━━━━━━\n");
        sb.append("💬 每日微语\n");
        sb.append("「").append(tip).append("」");
    }
    return sb.toString();
}

private String formatNewsIndex(int index) {
    String[] nums = new String[] {
        "①", "②", "③", "④", "⑤", "⑥", "⑦", "⑧", "⑨", "⑩",
        "⑪", "⑫", "⑬", "⑭", "⑮", "⑯", "⑰", "⑱", "⑲", "⑳"
    };
    if (index >= 1 && index <= nums.length) return nums[index - 1];
    return String.valueOf(index) + ".";
}
// 同步HTTP GET请求
private String syncHttpGet(String url) {
    java.net.HttpURLConnection conn = null;
    try {
        java.net.URL u = new java.net.URL(url);
        conn = (java.net.HttpURLConnection) u.openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "Daily60SNewsPlugin/1.2.4");
        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            writeLog("ERROR", "网络", "响应码：" + responseCode);
            return null;
        }
        java.io.BufferedReader reader = new java.io.BufferedReader(
            new java.io.InputStreamReader(conn.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();
        return sb.toString();
    } catch (Throwable e) {
        writeLog("ERROR", "网络", "请求失败：" + String.valueOf(e));
        return null;
    } finally {
        if (conn != null) try { conn.disconnect(); } catch (Throwable e) {}
    }
}

// 同步下载文件（支持跳转、UA，并确保目录存在）
private boolean syncDownload(String url, String filePath) {
    java.io.InputStream in = null;
    java.io.FileOutputStream out = null;
    java.net.HttpURLConnection conn = null;
    try {
        if (TextUtils.isEmpty(url) || TextUtils.isEmpty(filePath)) return false;
        File outFile = new File(filePath);
        File parent = outFile.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();

        java.net.URL u = new java.net.URL(url);
        conn = (java.net.HttpURLConnection) u.openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(30000);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "Daily60SNewsPlugin/1.2.5");
        conn.setRequestProperty("Accept", "image/*,*/*");
        int responseCode = conn.getResponseCode();
        // 部分环境需要手动跟随一次跳转
        if (responseCode == 301 || responseCode == 302 || responseCode == 303 || responseCode == 307 || responseCode == 308) {
            String location = conn.getHeaderField("Location");
            conn.disconnect();
            if (TextUtils.isEmpty(location)) {
                writeLog("ERROR", "网络", "图片下载跳转地址为空，code=" + responseCode);
                return false;
            }
            u = new java.net.URL(u, location);
            conn = (java.net.HttpURLConnection) u.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(30000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Daily60SNewsPlugin/1.2.5");
            conn.setRequestProperty("Accept", "image/*,*/*");
            responseCode = conn.getResponseCode();
        }
        if (responseCode != 200) {
            writeLog("ERROR", "网络", "图片下载响应码：" + responseCode + "，url=" + url);
            return false;
        }
        in = conn.getInputStream();
        out = new java.io.FileOutputStream(outFile);
        byte[] buffer = new byte[8192];
        int len;
        long total = 0L;
        while ((len = in.read(buffer)) > 0) {
            out.write(buffer, 0, len);
            total += len;
        }
        out.flush();
        if (total <= 0L || !outFile.exists() || outFile.length() <= 0L) {
            writeLog("ERROR", "网络", "图片下载后文件为空：" + filePath);
            return false;
        }
        writeLog("INFO", "网络", "图片下载成功，大小=" + outFile.length() + "，路径=" + filePath);
        return true;
    } catch (Throwable e) {
        writeLog("ERROR", "网络", "文件下载失败：" + String.valueOf(e) + "，url=" + url);
        return false;
    } finally {
        try { if (in != null) in.close(); } catch (Throwable e) {}
        try { if (out != null) out.close(); } catch (Throwable e) {}
        try { if (conn != null) conn.disconnect(); } catch (Throwable e) {}
    }
}

private String extractImageUrl(JSONObject data) {
    if (data == null) return "";
    String[] keys = new String[] { "image", "image_url", "img", "cover", "pic", "url" };
    for (int i = 0; i < keys.length; i++) {
        String value = safeTrim(data.optString(keys[i], ""));
        if (!TextUtils.isEmpty(value) && (value.startsWith("http://") || value.startsWith("https://"))) {
            return value;
        }
    }
    return "";
}
private void registerAlarmReceiver() {
    if (appContext == null) return;
    unregisterAlarmReceiver();
    alarmReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (context != null && appContext == null) {
                try {
                    Context ctx = context.getApplicationContext();
                    appContext = ctx != null ? ctx : context;
                } catch (Throwable ignored) {}
            }
            if (ALARM_ACTION.equals(intent.getAction())) {
                writeLog("INFO", "定时", "闹钟触发：开始执行定时推送");
                final PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                final PowerManager.WakeLock wl = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK, "每日60S新闻:AlarmWakeLock");
                wl.acquire(300000); // 5分钟，足够同步HTTP+多群发送
                new Thread(new Runnable() {
                    public void run() {
                        try {
                            executeScheduledPushSync();
                        } catch (Throwable e) {
                            writeLog("ERROR", "定时", "执行定时推送异常：" + String.valueOf(e));
                        } finally {
                            try { wl.release(); } catch (Throwable ignored) {}
                            rescheduleNextDay();
                        }
                    }
                }).start();
            }
        }
    };
    IntentFilter filter = new IntentFilter(ALARM_ACTION);
    if (Build.VERSION.SDK_INT >= 33) {
        appContext.registerReceiver(alarmReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    } else {
        appContext.registerReceiver(alarmReceiver, filter);
    }
    writeLog("INFO", "定时", "广播接收器已注册（同步执行模式）");
}
private void unregisterAlarmReceiver() {
    if (alarmReceiver != null && appContext != null) {
        try { appContext.unregisterReceiver(alarmReceiver); } catch (Throwable e) {}
        alarmReceiver = null;
    }
}

private void cancelAlarm() {
    try {
        if (appContext != null) {
            AlarmManager am = alarmManager;
            if (am == null) {
                am = (AlarmManager) appContext.getSystemService(Context.ALARM_SERVICE);
            }
            // 兼容旧版 requestCode=0 与新版 10086
            int[] requestCodes = new int[] { 10086, 0 };
            for (int i = 0; i < requestCodes.length; i++) {
                Intent intent = new Intent(ALARM_ACTION);
                intent.setPackage(appContext.getPackageName());
                int flags = PendingIntent.FLAG_NO_CREATE;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    flags |= PendingIntent.FLAG_IMMUTABLE;
                }
                PendingIntent existing = PendingIntent.getBroadcast(appContext, requestCodes[i], intent, flags);
                if (existing != null) {
                    if (am != null) am.cancel(existing);
                    existing.cancel();
                }
            }
        } else if (alarmManager != null && alarmPendingIntent != null) {
            alarmManager.cancel(alarmPendingIntent);
        }
    } catch (Throwable e) {
        writeLog("DEBUG", "定时", "取消闹钟时异常：" + String.valueOf(e));
    }
    alarmPendingIntent = null;
    alarmManager = null;
}

// ==================== 新闻推送 ====================

private void pushNewsToTalker(final String talker, final boolean showToast) {
    writeLog("INFO", "新闻推送", "步骤1/5：准备向会话发送新闻，目标=" + talker);
    if (TextUtils.isEmpty(talker)) {
        writeLog("WARN", "新闻推送", "目标会话为空，终止发送");
        if (showToast) toast("当前不在有效聊天中");
        return;
    }

    writeLog("INFO", "新闻推送", "步骤2/5：开始请求新闻接口");
    get(NEWS_API_URL, null, 30L, new Consumer() {
        public void accept(Object value) {
            try {
                String body = value == null ? "" : String.valueOf(value);
                if (TextUtils.isEmpty(body)) {
                    if (showToast) toast("新闻接口请求超时 / 失败");
                    writeLog("ERROR", "新闻推送", "步骤2/5：接口返回空");
                    return;
                }
                writeLog("INFO", "新闻推送", "步骤2/5：接口请求成功，开始解析");

                JSONObject json = new JSONObject(body);
                int code = json.optInt("code", -1);
                if (code != 200) {
                    if (showToast) toast("新闻接口调用失败（错误码：" + code + "）");
                    writeLog("ERROR", "新闻推送", "步骤3/5：接口错误码=" + code);
                    return;
                }

                JSONObject data = json.optJSONObject("data");
                if (data == null) {
                    if (showToast) toast("新闻数据解析失败");
                    writeLog("ERROR", "新闻推送", "步骤3/5：data字段为空");
                    return;
                }
                writeLog("INFO", "新闻推送", "步骤3/5：数据解析成功");

                String mode = getSendMode();
                writeLog("INFO", "新闻推送", "步骤4/5：发送模式=" + (MODE_IMAGE.equals(mode) ? "图片" : "文本"));
                if (MODE_IMAGE.equals(mode)) {
                    downloadAndSendImage(talker, data, showToast);
                } else {
                    parseAndSendText(talker, data, showToast);
                }
                writeLog("INFO", "新闻推送", "步骤5/5：发送流程已提交");
            } catch (Throwable e) {
                writeLog("ERROR", "新闻推送", "处理异常：" + String.valueOf(e));
                if (showToast) toast("新闻数据处理异常");
            }
        }
    });
}

private void parseAndSendText(String talker, JSONObject data, boolean showToast) {
    try {
        String text = buildNewsText(data);
        safeSendText(talker, text);
        writeLog("INFO", "新闻推送", "文本发送成功，目标=" + talker);
        if (showToast) toast("新闻早报已发送");
    } catch (Throwable e) {
        writeLog("ERROR", "新闻推送", "文本解析失败：" + String.valueOf(e));
        if (showToast) toast("新闻数据解析失败");
    }
}
private void downloadAndSendImage(final String talker, final JSONObject data, final boolean showToast) {
    try {
        final String imageUrl = extractImageUrl(data);
        if (TextUtils.isEmpty(imageUrl)) {
            writeLog("WARN", "图片", "接口未返回图片地址，回退文本，目标=" + talker);
            if (showToast) toast("图片地址为空，已回退文本发送");
            parseAndSendText(talker, data, showToast);
            return;
        }

        // 与定时推送共用同步下载逻辑，避免宿主 download 回调不稳定导致“下载失败”
        new Thread(new Runnable() {
            public void run() {
                final String imagePath = getStorageDirPath() + "/image_" + System.currentTimeMillis() + ".png";
                writeLog("INFO", "图片", "开始下载：" + imageUrl + " -> " + imagePath);
                boolean downloaded = syncDownload(imageUrl, imagePath);
                File file = new File(imagePath);
                if (!downloaded || !file.exists() || file.length() == 0) {
                    writeLog("ERROR", "图片", "图片下载失败，回退文本，目标=" + talker);
                    if (showToast) {
                        try { toast("新闻图片下载失败，已回退文本发送"); } catch (Throwable ignored) {}
                    }
                    parseAndSendText(talker, data, showToast);
                    return;
                }
                try {
                    boolean ok = safeSendImage(talker, file.getAbsolutePath());
                    if (ok) {
                        writeLog("INFO", "图片", "图片发送完成，目标=" + talker + "，大小=" + file.length());
                        if (showToast) {
                            try { toast("新闻图片已发送"); } catch (Throwable ignored) {}
                        }
                    } else {
                        writeLog("ERROR", "图片", "图片发送失败，回退文本，目标=" + talker);
                        if (showToast) {
                            try { toast("图片发送失败，已回退文本发送"); } catch (Throwable ignored) {}
                        }
                        parseAndSendText(talker, data, false);
                    }
                } catch (Throwable e) {
                    writeLog("ERROR", "图片", "图片发送异常：" + String.valueOf(e));
                    if (showToast) {
                        try { toast("图片发送异常，已回退文本发送"); } catch (Throwable ignored) {}
                    }
                    parseAndSendText(talker, data, false);
                } finally {
                    final String cleanupPath = imagePath;
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        public void run() {
                            try { new File(cleanupPath).delete(); } catch (Throwable e) {}
                        }
                    }, 10000);
                }
            }
        }).start();
    } catch (Throwable e) {
        writeLog("ERROR", "图片", "图片处理异常：" + String.valueOf(e));
        if (showToast) toast("新闻图片处理失败，尝试文本发送");
        try { parseAndSendText(talker, data, showToast); } catch (Throwable ignored) {}
    }
}

// ==================== 群组管理 ====================

private List getGroupList() {
    List groups = new ArrayList();
    String raw = getString(KEY_GROUP_LIST, "");
    if (!TextUtils.isEmpty(raw)) {
        String[] parts = raw.split(GROUP_SEPARATOR);
        for (String part : parts) {
            String trimmed = safeTrim(part);
            if (!TextUtils.isEmpty(trimmed)) {
                groups.add(trimmed);
            }
        }
    }
    return groups;
}

private String addCurrentGroup(String talker) {
    if (!isGroupTalker(talker)) {
        toast("当前不在群聊中");
        return null;
    }
    List groups = getGroupList();
    if (groups.contains(talker)) {
        toast("当前群已在列表中");
        return null;
    }
    groups.add(talker);
    saveGroupList(groups);
    return talker;
}

private String removeCurrentGroup(String talker) {
    if (!isGroupTalker(talker)) {
        toast("当前不在群聊中");
        return null;
    }
    List groups = getGroupList();
    if (!groups.contains(talker)) {
        toast("当前群不在列表中");
        return null;
    }
    groups.remove(talker);
    saveGroupList(groups);
    return talker;
}


private boolean isGroupTalker(String talker) {
    return talker != null && talker.endsWith("@chatroom");
}

private void saveGroupList(List groups) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < groups.size(); i++) {
        if (i > 0) sb.append(GROUP_SEPARATOR);
        sb.append(groups.get(i));
    }
    putString(KEY_GROUP_LIST, sb.toString());
}

private String buildGroupListText() {
    List groups = getGroupList();
    if (groups.isEmpty()) return "推送群：无";
    StringBuilder sb = new StringBuilder("推送群：");
    for (int i = 0; i < groups.size(); i++) {
        String groupId = String.valueOf(groups.get(i));
        String name = getGroupNickname(groupId);
        if (i > 0) sb.append("、");
        sb.append(name);
    }
    return sb.toString();
}

private String getGroupNickname(String groupId) {
    try {
        String name = getFriendNickName(groupId);
        if (!TextUtils.isEmpty(name)) return name;
    } catch (Throwable e) {}
    return groupId;
}
// ==================== 接口测试 ====================

private void testNewsApi(final String talker) {
    writeLog("INFO", "测试", "步骤1/4：开始测试新闻接口，来源会话=" + talker);
    toast("正在测试新闻接口...");
    writeLog("INFO", "测试", "步骤2/4：发起接口请求");
    get(NEWS_API_URL, null, 30L, new Consumer() {
        public void accept(Object value) {
            try {
                String body = value == null ? "" : String.valueOf(value);
                if (TextUtils.isEmpty(body)) {
                    toast("接口请求失败：返回为空");
                    writeLog("ERROR", "测试", "步骤3/4：接口返回为空");
                    return;
                }
                writeLog("INFO", "测试", "步骤3/4：收到接口响应，开始解析");
                JSONObject json = new JSONObject(body);
                int code = json.optInt("code", -1);
                JSONObject data = json.optJSONObject("data");
                JSONArray newsArr = data != null ? data.optJSONArray("news") : null;
                int newsCount = newsArr != null ? newsArr.length() : 0;
                toast("接口测试成功！code=" + code + "，新闻条数=" + newsCount);
                writeLog("INFO", "测试", "步骤4/4：接口测试成功，code=" + code + "，新闻条数=" + newsCount);
            } catch (Throwable e) {
                toast("接口测试异常：" + e.getMessage());
                writeLog("ERROR", "测试", "接口测试异常：" + String.valueOf(e));
            }
        }
    });
}

// ==================== 存储 ====================

private String getStorageDirPath() {
    if (appContext != null) {
        try {
            File dir = appContext.getExternalFilesDir("每日60S新闻");
            if (dir == null) {
                dir = new File(appContext.getFilesDir(), "每日60S新闻");
            }
            if (!dir.exists() && !dir.mkdirs()) {
                dir = new File(appContext.getFilesDir(), "每日60S新闻");
                dir.mkdirs();
            }
            if (dir.exists() && dir.canWrite()) return dir.getAbsolutePath();
        } catch (Throwable e) {}
    }

    try {
        File dir = new File(STORAGE_DIR);
        if (!dir.exists()) dir.mkdirs();
        if (dir.exists() && dir.canWrite()) return dir.getAbsolutePath();
    } catch (Throwable e) {}

    File fallback = new File(cacheDir, "每日60S新闻");
    try { if (!fallback.exists()) fallback.mkdirs(); } catch (Throwable e) {}
    return fallback.getAbsolutePath();
}

private void ensureStorageDir() {
    getStorageDirPath();
}

// ==================== 安全发送封装 ====================

private boolean safeSendText(String talker, String content) {
    try {
        sendText(talker, content);
        return true;
    } catch (Throwable e) {
        writeLog("ERROR", "发送", "文本发送失败：" + String.valueOf(e));
        return false;
    }
}

private boolean safeSendImage(String talker, String path) {
    try {
        sendImage(talker, path);
        return true;
    } catch (Throwable e) {
        writeLog("ERROR", "发送", "图片发送失败：" + String.valueOf(e));
        return false;
    }
}

private String safeTrim(String value) {
    return value == null ? "" : value.trim();
}

// ==================== 日志 ====================

private String toChineseLevel(String level) {
    if ("ERROR".equals(level)) return "错误";
    if ("WARN".equals(level)) return "警告";
    if ("DEBUG".equals(level)) return "调试";
    return "信息";
}

private void writeLog(String level, String module, String message) {
    try {
        if (!getBoolean(KEY_DEBUG, false)) return;
        log("[每日60S新闻][" + toChineseLevel(level) + "][" + module + "] " + message);
    } catch (Throwable ignored) {}
}

// ==================== UI 辅助方法 ====================

/** 设置页按钮防抖：防止连续点击刷屏 */
private boolean acceptSettingsClick(String action) {
    return acceptSettingsClick(action, SETTINGS_CLICK_COOLDOWN_MS);
}

private boolean acceptSettingsClick(String action, long cooldownMs) {
    long now = System.currentTimeMillis();
    if (now - lastSettingsClickMs < cooldownMs) {
        try { toast("操作过快，请稍后再试"); } catch (Throwable ignored) {}
        writeLog("DEBUG", "设置", "按钮防抖拦截：" + action + "（间隔" + (now - lastSettingsClickMs) + "ms）");
        return false;
    }
    lastSettingsClickMs = now;
    return true;
}

private LinearLayout createCardLayout() {
    LinearLayout layout = new LinearLayout(getTopActivity());
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setPadding(24, 20, 24, 20);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    );
    params.setMargins(0, 0, 0, 20);
    layout.setLayoutParams(params);
    GradientDrawable bg = new GradientDrawable();
    bg.setColor(Color.WHITE);
    bg.setCornerRadius(28);
    bg.setStroke(1, Color.parseColor("#E4E7EC"));
    layout.setBackground(bg);
    return layout;
}

private TextView createSectionTitle(String text) {
    TextView textView = new TextView(getTopActivity());
    textView.setText(text);
    textView.setTextSize(18);
    textView.setTextColor(Color.parseColor("#101828"));
    textView.setGravity(Gravity.CENTER_VERTICAL);
    textView.setPadding(0, 0, 0, 14);
    return textView;
}

private EditText createStyledEditText(String hint, String initialText) {
    EditText editText = new EditText(getTopActivity());
    editText.setHint(hint);
    editText.setText(initialText);
    editText.setTextSize(15);
    editText.setSingleLine(true);
    editText.setPadding(18, 12, 18, 12);
    GradientDrawable bg = new GradientDrawable();
    bg.setColor(Color.parseColor("#F9FAFB"));
    bg.setCornerRadius(18);
    bg.setStroke(1, Color.parseColor("#D0D5DD"));
    editText.setBackground(bg);
    return editText;
}

private void styleActionButton(Button button, String bgColor, String textColor) {
    button.setTextSize(15);
    button.setTextColor(Color.parseColor(textColor));
    GradientDrawable bg = new GradientDrawable();
    bg.setColor(Color.parseColor(bgColor));
    bg.setCornerRadius(18);
    button.setBackground(bg);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    );
    params.setMargins(0, 10, 0, 0);
    button.setLayoutParams(params);
}

private void styleSmallButton(Button button, String bgColor, String textColor) {
    button.setTextSize(13);
    button.setTextColor(Color.parseColor(textColor));
    GradientDrawable bg = new GradientDrawable();
    bg.setColor(Color.parseColor(bgColor));
    bg.setCornerRadius(14);
    button.setBackground(bg);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f
    );
    params.setMargins(4, 8, 4, 0);
    button.setLayoutParams(params);
}

private void styleDialogButtons(AlertDialog dialog) {
    Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
    if (positive != null) {
        positive.setTextColor(Color.parseColor("#2E7D32"));
        positive.setTextSize(15);
    }
    Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
    if (negative != null) {
        negative.setTextColor(Color.parseColor("#667085"));
        negative.setTextSize(15);
    }
}
