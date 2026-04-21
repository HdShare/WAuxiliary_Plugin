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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

// === 文件/文件夹浏览与多选 ===
final String DEFAULT_LAST_FOLDER_SP_AUTO = "last_folder_for_media_auto";
final String ROOT_FOLDER = android.os.Environment.getExternalStorageDirectory().getAbsolutePath();

// 回调接口（必须定义在使用之前）
interface MediaSelectionCallback { void onSelected(ArrayList<String> selectedFiles); }

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

// ==========================================
// ========== 🕒 微信定时发送助手 (多任务精确版) ==========
// ==========================================

// 全局变量 - 发送配置
Set<String> massSendTargetWxids = new HashSet<String>();
int massSendType = 0; // 0:文本, 1:图片, 2:视频, 3:文件, 4:表情
String massSendTextContent = "";
List<String> massSendMediaPaths = new ArrayList<String>();
long massSendInterval = 0; // 发送对象间隔(秒)
long massSendMediaInterval = 0; // 多媒体文件间隔(秒)
int massSendRepeatType = 0; // 0:不重复(单次), 1:每天重复, 2:每周重复
boolean massSendWakeOnTime = false; // 是否到点唤醒微信执行

// 定时任务相关 - 改为多任务支持
Map scheduledTasks = new HashMap(); // taskId -> JSONObject
Map scheduledRunnables = new HashMap(); // taskId -> Runnable
boolean isTaskRunning = false;
Handler scheduleHandler = new Handler(Looper.getMainLooper());

// 常量定义
final int SEND_TYPE_TEXT = 0;
final int SEND_TYPE_IMAGE = 1;
final int SEND_TYPE_VIDEO = 2;
final int SEND_TYPE_FILE = 3;
final int SEND_TYPE_EMOJI = 4;
final int SEND_TYPE_VOICE = 5;
final int SEND_TYPE_MOMENTS_TEXT = 6;      // 朋友圈纯文本
final int SEND_TYPE_MOMENTS_IMAGE = 7;     // 朋友圈图文
final int SEND_TYPE_XML = 8;               // XML消息

// 存储Key
final String CONFIG_KEY = "scheduled_send_multi_v2";
final String KEY_LABELS = "saved_target_labels";
final String KEY_TASKS = "scheduled_tasks"; // 存储多任务列表

// 缓存列表
private List sCachedFriendList = null;
private List sCachedGroupList = null;

// ==========================================
// ========== ♻️ 生命周期与核心逻辑 ==========
// ==========================================

/**
 * 插件加载时调用
 * 用于恢复所有未完成的定时任务
 */
public void onLoad() {
    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
        public void run() {
            restoreAllTasks();
        }
    }, 5000);
}

/**
 * 恢复所有定时任务
 */
private void restoreAllTasks() {
    try {
        String tasksJson = getString(CONFIG_KEY, KEY_TASKS, "{}");
        JSONObject allTasks = parseJsonObjectSafe(tasksJson);

        if (allTasks == null || allTasks.length() == 0) return;

        long now = System.currentTimeMillis();
        int restoredCount = 0;

        Iterator keys = allTasks.keys();
        while (keys.hasNext()) {
            String taskId = String.valueOf(keys.next());
            JSONObject task = allTasks.optJSONObject(taskId);
            if (task == null) continue;

            long planTime = task.optLong("planTime", 0);
            if (planTime <= 0) continue;

            scheduledTasks.put(taskId, task);

            if (now >= planTime) {
                long missMs = now - planTime;
                int repeatType = task.optInt("repeatType", 0);

                if (repeatType > 0) {
                    // 循环任务：只要错过就补发一次，避免直接跳到下一轮导致“第二天不执行”的体感问题。
                    task.put("status", "pending");
                    log("检测到错过的循环任务 " + taskId + "（晚点 " + missMs + "ms），准备补发...");
                    notify("定时发送补发", "检测到循环任务 " + taskId + " 即将补发...");
                    scheduleTaskWithPrecision(taskId, task, 1000);
                    restoredCount++;
                } else if (missMs < 10 * 60 * 1000) {
                    log("检测到错过的任务 " + taskId + "（10分钟内），准备补发...");
                    notify("定时发送补发", "检测到任务 " + taskId + " 即将补发...");
                    scheduleTaskWithPrecision(taskId, task, 1000);
                    restoredCount++;
                } else {
                    task.put("status", "expired");
                    log("任务 " + taskId + " 已过期");
                    continue;
                }
            } else {
                long delay = planTime - now;
                scheduleTaskWithPrecision(taskId, task, delay);
                restoredCount++;
            }
        }

        saveAllTasks();

        if (restoredCount > 0) {
            log("已恢复 " + restoredCount + " 个定时任务");
        }
    } catch (Exception e) {
        log("恢复任务失败: " + e.getMessage());
    }
}

/**
 * 【专为语音设计的同步锁】
 */
private void runOnMainSync(final Runnable runnable) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
        runnable.run();
        return;
    }
    final CountDownLatch latch = new CountDownLatch(1);
    new Handler(Looper.getMainLooper()).post(new Runnable() {
        public void run() {
            try {
                runnable.run();
            } catch (Exception e) {
                log("UI线程执行异常: " + e.getMessage());
            } finally {
                latch.countDown();
            }
        }
    });
    try {
        latch.await(15, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
        e.printStackTrace();
    }
}

/**
 * 高精度定时执行（精确到毫秒级，误差<100ms）
 */
private void scheduleTaskWithPrecision(final String taskId, JSONObject task, long delayMillis) {
    cancelTaskTimer(taskId);

    final long targetTime = task.optLong("planTime", 0);
    if (task.optBoolean("wakeOnTime", false)) {
        setAlarmForTask(taskId, targetTime);
    }

    Runnable runnable = new Runnable() {
        public void run() {
            long now = System.currentTimeMillis();
            if (now < targetTime) {
                scheduleHandler.postDelayed(this, Math.max(1, targetTime - now - 100));
                return;
            }
            while (System.currentTimeMillis() < targetTime) {}

            executeTaskSend(taskId);
        }
    };

    scheduledRunnables.put(taskId, runnable);
    scheduleHandler.postDelayed(runnable, Math.max(0, delayMillis));
}

/**
 * 精确执行单个任务
 */
private void executeTaskSend(final String taskId) {
    final JSONObject task = (JSONObject) scheduledTasks.get(taskId);
    if (task == null) return;

    long planTime = task.optLong("planTime", 0);
    long nowAtExecute = System.currentTimeMillis();
    if (planTime > 0 && nowAtExecute > planTime + 30000) {
        long lateMs = nowAtExecute - planTime;
        log("任务 " + taskId + " 晚点执行: " + lateMs + "ms，继续尝试发送");
    }

    final int type = task.optInt("type", 0);
    final String content = task.optString("content", "");
    final long interval = task.optLong("interval", 0);
    final long mediaInterval = task.optLong("mediaInterval", 0);

    JSONArray targetsJson = task.optJSONArray("targets");
    List<String> targets = new ArrayList<String>();
    if (targetsJson != null) {
        for (int i = 0; i < targetsJson.length(); i++) {
            targets.add(targetsJson.optString(i));
        }
    }

    JSONArray mediasJson = task.optJSONArray("medias");
    List<String> mediaPaths = new ArrayList<String>();
    if (mediasJson != null) {
        for (int i = 0; i < mediasJson.length(); i++) {
            mediaPaths.add(mediasJson.optString(i));
        }
    }

    try {
        task.put("status", "running");
    } catch (Exception e) {}
    saveAllTasks();

    notify("定时发送开始", "任务 " + taskId.substring(0, 8) + " 开始执行\n目标数: " + targets.size());

    final List<String> finalTargets = targets;
    final List<String> finalMediaPaths = mediaPaths;
    final JSONObject finalTask = task;

    new Thread(new Runnable() {
        public void run() {
            int success = 0;
            int fail = 0;

            // 朋友圈模式特殊处理
            if (type == SEND_TYPE_MOMENTS_TEXT || type == SEND_TYPE_MOMENTS_IMAGE) {
                try {
                    if (type == SEND_TYPE_MOMENTS_TEXT) {
                        uploadText(content);
                    } else {
                        if (finalMediaPaths.isEmpty()) {
                            uploadText(content);
                        } else {
                            uploadTextAndPicList(content, finalMediaPaths);
                        }
                    }
                    success = 1;
                    notify("朋友圈发送成功", type == SEND_TYPE_MOMENTS_TEXT ? "已发送纯文本" : "已发送图文");
                } catch (Exception e) {
                    fail = 1;
                    log("朋友圈发送失败: " + e.getMessage());
                }
            } else {
                // 原有的群发逻辑
                for (int i = 0; i < finalTargets.size(); i++) {
                    final String target = finalTargets.get(i);
                    final String name = getContactName(target);
                    boolean targetSuccess = true;

                    try {
                        if (type == SEND_TYPE_TEXT) {
                            String contentToSend = content.replace("%friendName%", name);
                            sendText(target, contentToSend);
                        } else if (type == SEND_TYPE_XML) {
                            String xmlToSend = content.replace("%friendName%", name);
                            sendXmlMsg(target, xmlToSend);
                        } else {
                            for (int j = 0; j < finalMediaPaths.size(); j++) {
                                final String path = finalMediaPaths.get(j);
                                final File f = new File(path);
                                if (f.exists()) {
                                    switch (type) {
                                        case SEND_TYPE_IMAGE:
                                            sendImage(target, path);
                                            break;
                                        case SEND_TYPE_VIDEO:
                                            sendVideo(target, path);
                                            break;
                                        case SEND_TYPE_EMOJI:
                                            sendEmoji(target, path);
                                            break;
                                        case SEND_TYPE_FILE:
                                            shareFile(target, f.getName(), path, "");
                                            break;
                                        case SEND_TYPE_VOICE:
                                            runOnMainSync(new Runnable() {
                                                public void run() {
                                                    sendVoice(target, path);
                                                }
                                            });
                                            break;
                                    }
                                    if (j < finalMediaPaths.size() - 1) {
                                        long mInterval = mediaInterval > 0 ? mediaInterval * 1000 : 500;
                                        Thread.sleep(mInterval);
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        targetSuccess = false;
                        log("发送失败: " + target + " - " + e.getMessage());
                    }

                    if (targetSuccess) success++; else fail++;

                    if (i < finalTargets.size() - 1 && interval > 0) {
                        try { Thread.sleep(interval * 1000); } catch (Exception e) {}
                    }
                }
            }

            final int fSuccess = success;
            final int fFail = fail;
            int repeatType = finalTask.optInt("repeatType", 0);
            JSONArray repeatDaysArray = finalTask.optJSONArray("repeatDays");

            try {
                if (repeatType > 0) {
                    long oldPlanTime = finalTask.optLong("planTime", 0);
                    final long newPlanTime = calculateNextPlanTime(oldPlanTime, repeatType, repeatDaysArray);

                    finalTask.put("planTime", newPlanTime);
                    finalTask.put("status", "pending");
                    finalTask.put("lastSuccessCount", fSuccess);
                    finalTask.put("lastFailCount", fFail);
                    finalTask.put("lastExecutedTime", System.currentTimeMillis());
                    saveAllTasks();

                    long delay = newPlanTime - System.currentTimeMillis();
                    scheduleTaskWithPrecision(taskId, finalTask, delay);

                    notify("重复任务完成一轮", "任务 " + taskId.substring(0, 8) + " 已安排下一次: \n" + formatTimeWithSeconds(newPlanTime));

                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        public void run() {
                            toast("✅ 本轮任务执行完毕，已排期下一次\n时间: " + formatTimeWithSeconds(newPlanTime));
                        }
                    });
                } else {
                    finalTask.put("status", "completed");
                    finalTask.put("successCount", fSuccess);
                    finalTask.put("failCount", fFail);
                    finalTask.put("completedTime", System.currentTimeMillis());
                    saveAllTasks();

                    cancelTaskTimer(taskId);
                    scheduledTasks.remove(taskId);

                    final String report = "成功: " + fSuccess + ", 失败: " + fFail;
                    notify("定时发送完成", "任务 " + taskId.substring(0, 8) + " 已完成\n" + report);

                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        public void run() {
                            toast("✅ 任务执行完毕\n" + report);
                        }
                    });
                }
            } catch (Exception e) {
                log("任务完成收尾失败: " + e.getMessage());
            }
        }
    }).start();
}

/**
 * 计算重复任务的下一次执行时间
 */
private long calculateNextPlanTime(long currentPlanTime, int repeatType, JSONArray repeatDaysArray) {
    Calendar cal = Calendar.getInstance();
    cal.setTimeInMillis(currentPlanTime);
    long now = System.currentTimeMillis();

    if (repeatType == 1) {
        do {
            cal.add(Calendar.DAY_OF_MONTH, 1);
        } while (cal.getTimeInMillis() <= now);
    } else if (repeatType == 2) {
        List<Integer> days = new ArrayList<Integer>();
        if (repeatDaysArray != null) {
            for (int i = 0; i < repeatDaysArray.length(); i++) {
                days.add(new Integer(repeatDaysArray.optInt(i)));
            }
        }
        if (days.isEmpty()) {
            cal.add(Calendar.DAY_OF_MONTH, 1);
            return cal.getTimeInMillis();
        }

        int safety = 365;
        do {
            cal.add(Calendar.DAY_OF_MONTH, 1);
            safety--;
        } while (safety > 0 && (!days.contains(new Integer(cal.get(Calendar.DAY_OF_WEEK))) || cal.getTimeInMillis() <= now));
    }

    return cal.getTimeInMillis();
}

/**
 * 格式化周天显示
 */
private String formatRepeatDays(JSONArray arr) {
    if (arr == null || arr.length() == 0) return "未设置";
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < arr.length(); i++) {
        int d = arr.optInt(i);
        switch (d) {
            case Calendar.MONDAY: sb.append("一,"); break;
            case Calendar.TUESDAY: sb.append("二,"); break;
            case Calendar.WEDNESDAY: sb.append("三,"); break;
            case Calendar.THURSDAY: sb.append("四,"); break;
            case Calendar.FRIDAY: sb.append("五,"); break;
            case Calendar.SATURDAY: sb.append("六,"); break;
            case Calendar.SUNDAY: sb.append("日,"); break;
        }
    }
    if (sb.length() > 0) sb.setLength(sb.length() - 1);
    return sb.toString();
}

/**
 * 取消单个任务的定时器
 */
private void cancelTaskTimer(String taskId) {
    Runnable runnable = (Runnable) scheduledRunnables.get(taskId);
    if (runnable != null) {
        scheduleHandler.removeCallbacks(runnable);
        scheduledRunnables.remove(taskId);
    }
    cancelAlarmForTask(taskId);
}

/**
 * 为任务设置系统级闹钟（尽量在后台也能按时唤醒微信）
 */
private void setAlarmForTask(String taskId, long triggerTime) {
    try {
        Context ctx = getBestContext();
        if (ctx == null || triggerTime <= 0) return;

        android.content.Intent it = buildWeChatLaunchIntent(ctx);
        if (it == null) return;

        android.app.PendingIntent pi = buildTaskAlarmPendingIntent(ctx, taskId, it);
        android.app.AlarmManager am = (android.app.AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null || pi == null) return;

        long alarmAt = Math.max(System.currentTimeMillis(), triggerTime - 1000);

        try { am.cancel(pi); } catch (Exception ignored) {}

        try {
            if (Build.VERSION.SDK_INT >= 23) {
                am.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, alarmAt, pi);
            } else if (Build.VERSION.SDK_INT >= 19) {
                am.setExact(android.app.AlarmManager.RTC_WAKEUP, alarmAt, pi);
            } else {
                am.set(android.app.AlarmManager.RTC_WAKEUP, alarmAt, pi);
            }
        } catch (SecurityException se) {
            // Android 12+ 可能因精确闹钟权限限制抛错，退化为非精确闹钟。
            if (Build.VERSION.SDK_INT >= 23) {
                am.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, alarmAt, pi);
            } else {
                am.set(android.app.AlarmManager.RTC_WAKEUP, alarmAt, pi);
            }
        }
    } catch (Exception e) {
        log("设置系统闹钟失败: " + e.getMessage());
    }
}

/**
 * 取消任务对应的系统级闹钟
 */
private void cancelAlarmForTask(String taskId) {
    try {
        Context ctx = getBestContext();
        if (ctx == null) return;

        android.content.Intent it = buildWeChatLaunchIntent(ctx);
        if (it == null) return;

        android.app.PendingIntent pi = buildTaskAlarmPendingIntent(ctx, taskId, it);
        android.app.AlarmManager am = (android.app.AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am != null && pi != null) am.cancel(pi);
    } catch (Exception ignored) {}
}

private Context getBestContext() {
    try {
        Context c = getTopActivity();
        if (c != null) return c;
    } catch (Exception ignored) {}
    try {
        Class at = Class.forName("android.app.ActivityThread");
        java.lang.reflect.Method m = at.getMethod("currentApplication", new Class[0]);
        Object app = m.invoke(null, new Object[0]);
        if (app instanceof Context) return (Context) app;
    } catch (Exception ignored) {}
    return null;
}

private android.content.Intent buildWeChatLaunchIntent(Context ctx) {
    try {
        android.content.Intent it = ctx.getPackageManager().getLaunchIntentForPackage("com.tencent.mm");
        if (it == null) {
            it = new android.content.Intent();
            it.setClassName("com.tencent.mm", "com.tencent.mm.ui.LauncherUI");
            it.setAction(android.content.Intent.ACTION_MAIN);
            it.addCategory(android.content.Intent.CATEGORY_LAUNCHER);
        }
        it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                | android.content.Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return it;
    } catch (Exception e) {
        log("构建微信启动Intent失败: " + e.getMessage());
        return null;
    }
}

private android.app.PendingIntent buildTaskAlarmPendingIntent(Context ctx, String taskId, android.content.Intent launchIntent) {
    int flags = (Build.VERSION.SDK_INT >= 23)
            ? (android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE)
            : android.app.PendingIntent.FLAG_UPDATE_CURRENT;
    return android.app.PendingIntent.getActivity(ctx, taskId.hashCode(), launchIntent, flags);
}

/**
 * 保存所有任务到存储
 */
private void saveAllTasks() {
    try {
        JSONObject root = new JSONObject();
        Iterator it = scheduledTasks.keySet().iterator();
        while (it.hasNext()) {
            String taskId = (String) it.next();
            JSONObject task = (JSONObject) scheduledTasks.get(taskId);
            root.put(taskId, task);
        }
        putString(CONFIG_KEY, KEY_TASKS, root.toString());
    } catch (Exception e) {
        log("保存任务失败: " + e.getMessage());
    }
}

// 入口函数
public boolean onClickSendBtn(String text) {
    massSendTargetWxids.clear();
    massSendTextContent = "";
    massSendMediaPaths.clear();
    if ("群发助手".equals(text) || "定时发送".equals(text)) {
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

    LinearLayout statsCard = createCardLayout();
    statsCard.setBackground(createGradientDrawable("#E8F5E9", 32));
    statsCard.addView(createSectionTitle("📊 任务概览"));

    final TextView statsTv = new TextView(getTopActivity());
    updateStatsText(statsTv);
    statsTv.setTextSize(14);
    statsCard.addView(statsTv);

    Button viewTasksBtn = new Button(getTopActivity());
    viewTasksBtn.setText("📋 查看所有任务");
    styleUtilityButton(viewTasksBtn);
    GradientDrawable btnBg = (GradientDrawable) viewTasksBtn.getBackground();
    btnBg.setColor(Color.parseColor("#C8E6C9"));
    btnBg.setStroke(2, Color.parseColor("#81C784"));
    viewTasksBtn.setTextColor(Color.parseColor("#2E7D32"));
    viewTasksBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            showTaskListDialog();
        }
    });
    statsCard.addView(viewTasksBtn);
    root.addView(statsCard);

    LinearLayout quickCard = createCardLayout();
    quickCard.addView(createSectionTitle("🚀 快速创建定时任务"));
    Button newTaskBtn = new Button(getTopActivity());
    newTaskBtn.setText("➕ 新建定时任务");
    styleMediaSelectionButton(newTaskBtn);
    quickCard.addView(newTaskBtn);
    root.addView(quickCard);

    final LinearLayout targetCard = createCardLayout();
    targetCard.addView(createSectionTitle("👥 发送目标"));
    final TextView targetCountTv = new TextView(getTopActivity());
    updateTargetCountText(targetCountTv);
    targetCountTv.setTextSize(14);
    targetCountTv.setTextColor(Color.parseColor("#666666"));
    targetCountTv.setPadding(0, 0, 0, 16);
    targetCard.addView(targetCountTv);

    Button selectTargetBtn = new Button(getTopActivity());
    selectTargetBtn.setText("👤 手动选择 (好友/群聊)");
    styleUtilityButton(selectTargetBtn);
    selectTargetBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            showTargetCategoryDialog(massSendTargetWxids, new Runnable() {
                public void run() {
                    updateTargetCountText(targetCountTv);
                }
            });
        }
    });
    targetCard.addView(selectTargetBtn);

    Button labelManageBtn = new Button(getTopActivity());
    labelManageBtn.setText("🏷️ 标签分组管理 (新建/导入)");
    styleUtilityButton(labelManageBtn);
    GradientDrawable labelBg = (GradientDrawable) labelManageBtn.getBackground();
    labelBg.setColor(Color.parseColor("#E3F2FD"));
    labelBg.setStroke(2, Color.parseColor("#90CAF9"));
    labelManageBtn.setTextColor(Color.parseColor("#1976D2"));
    labelManageBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            showLabelManagementDialog(new Runnable() {
                public void run() {
                    updateTargetCountText(targetCountTv);
                }
            });
        }
    });
    targetCard.addView(labelManageBtn);

    Button clearTargetBtn = new Button(getTopActivity());
    clearTargetBtn.setText("🗑️ 清空已选");
    styleUtilityButton(clearTargetBtn);
    clearTargetBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            massSendTargetWxids.clear();
            updateTargetCountText(targetCountTv);
            toast("已清空发送目标");
        }
    });
    targetCard.addView(clearTargetBtn);
    root.addView(targetCard);

    LinearLayout contentCard = createCardLayout();
    contentCard.addView(createSectionTitle("📝 编辑发送内容"));

    RadioGroup mainTypeGroup = new RadioGroup(getTopActivity());
    mainTypeGroup.setOrientation(LinearLayout.VERTICAL);
    
    RadioGroup row1Group = new RadioGroup(getTopActivity());
    row1Group.setOrientation(LinearLayout.HORIZONTAL);
    RadioButton rbText = createRadioButton(getTopActivity(), "📝文本");
    RadioButton rbImage = createRadioButton(getTopActivity(), "🖼️图片");
    RadioButton rbVideo = createRadioButton(getTopActivity(), "🎬视频");
    row1Group.addView(rbText);
    row1Group.addView(rbImage);
    row1Group.addView(rbVideo);

    RadioGroup row2Group = new RadioGroup(getTopActivity());
    row2Group.setOrientation(LinearLayout.HORIZONTAL);
    RadioButton rbFile = createRadioButton(getTopActivity(), "📁文件");
    RadioButton rbEmoji = createRadioButton(getTopActivity(), "😊表情");
    RadioButton rbVoice = createRadioButton(getTopActivity(), "🎤语音");
    row2Group.addView(rbFile);
    row2Group.addView(rbEmoji);
    row2Group.addView(rbVoice);

    RadioGroup row3Group = new RadioGroup(getTopActivity());
    row3Group.setOrientation(LinearLayout.HORIZONTAL);
    RadioButton rbMomentsText = createRadioButton(getTopActivity(), "🌟朋友圈(纯文本)");
    RadioButton rbMomentsImage = createRadioButton(getTopActivity(), "🌟朋友圈(图文)");
    row3Group.addView(rbMomentsText);
    row3Group.addView(rbMomentsImage);

    RadioGroup row4Group = new RadioGroup(getTopActivity());
    row4Group.setOrientation(LinearLayout.HORIZONTAL);
    RadioButton rbXml = createRadioButton(getTopActivity(), "🧩XML");
    row4Group.addView(rbXml);

    LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
    );
    rowParams.setMargins(0, 4, 0, 4);
    row1Group.setLayoutParams(rowParams);
    row2Group.setLayoutParams(rowParams);
    row3Group.setLayoutParams(rowParams);
    row4Group.setLayoutParams(rowParams);
    
    mainTypeGroup.addView(row1Group);
    mainTypeGroup.addView(row2Group);
    mainTypeGroup.addView(row3Group);
    mainTypeGroup.addView(row4Group);
    contentCard.addView(mainTypeGroup);

    final LinearLayout contentContainer = new LinearLayout(getTopActivity());
    contentContainer.setOrientation(LinearLayout.VERTICAL);
    contentContainer.setPadding(0, 16, 0, 0);
    contentCard.addView(contentContainer);
    root.addView(contentCard);

    final LinearLayout settingCard = createCardLayout();
    settingCard.addView(createSectionTitle("⚙️ 发送参数设置"));

    settingCard.addView(createTextView(getTopActivity(), "⏰ 循环发送设置:", 14, 8));
    RadioGroup repeatGroup = new RadioGroup(getTopActivity());
    repeatGroup.setOrientation(LinearLayout.HORIZONTAL);
    final RadioButton rbOnce = createRadioButton(getTopActivity(), "单次发送");
    final RadioButton rbDay = createRadioButton(getTopActivity(), "每天重复");
    final RadioButton rbWeek = createRadioButton(getTopActivity(), "每周重复");
    repeatGroup.addView(rbOnce);
    repeatGroup.addView(rbDay);
    repeatGroup.addView(rbWeek);

    final LinearLayout weekDaysLayout = new LinearLayout(getTopActivity());
    weekDaysLayout.setOrientation(LinearLayout.VERTICAL);
    weekDaysLayout.setPadding(0, 16, 0, 16);
    weekDaysLayout.setVisibility(View.GONE);
    weekDaysLayout.setBackground(createGradientDrawable("#F8FAFC", 18));

    final CheckBox[] weekCbs = new CheckBox[7];
    String[] weekNames = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
    int[] weekVals = {Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY};
    LinearLayout weekRow1 = new LinearLayout(getTopActivity());
    weekRow1.setOrientation(LinearLayout.HORIZONTAL);
    LinearLayout weekRow2 = new LinearLayout(getTopActivity());
    weekRow2.setOrientation(LinearLayout.HORIZONTAL);
    for (int i = 0; i < 7; i++) {
        final CheckBox cb = new CheckBox(getTopActivity());
        cb.setText(weekNames[i]);
        cb.setTag(new Integer(weekVals[i]));
        cb.setButtonDrawable(null);
        cb.setGravity(Gravity.CENTER);
        cb.setTextSize(13);
        cb.setMinWidth(dpToPx(62));
        cb.setPadding(18, 12, 18, 12);
        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        );
        chipParams.setMargins(6, 6, 6, 6);
        cb.setLayoutParams(chipParams);
        styleWeekDayChip(cb, false);
        cb.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                if (buttonView instanceof CheckBox) {
                    styleWeekDayChip((CheckBox) buttonView, isChecked);
                }
            }
        });
        weekCbs[i] = cb;
        if (i < 4) weekRow1.addView(cb);
        else weekRow2.addView(cb);
    }
    weekDaysLayout.addView(weekRow1);
    weekDaysLayout.addView(weekRow2);

    if (massSendRepeatType == 1) rbDay.setChecked(true);
    else if (massSendRepeatType == 2) {
        rbWeek.setChecked(true);
        weekDaysLayout.setVisibility(View.VISIBLE);
    } else rbOnce.setChecked(true);

    repeatGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
        public void onCheckedChanged(RadioGroup group, int checkedId) {
            if (checkedId == rbDay.getId()) {
                massSendRepeatType = 1;
                weekDaysLayout.setVisibility(View.GONE);
            } else if (checkedId == rbWeek.getId()) {
                massSendRepeatType = 2;
                weekDaysLayout.setVisibility(View.VISIBLE);
            } else {
                massSendRepeatType = 0;
                weekDaysLayout.setVisibility(View.GONE);
            }
        }
    });
    settingCard.addView(repeatGroup);
    settingCard.addView(weekDaysLayout);

    LinearLayout wakeCard = new LinearLayout(getTopActivity());
    wakeCard.setOrientation(LinearLayout.VERTICAL);
    wakeCard.setPadding(24, 20, 24, 20);
    wakeCard.setBackground(createGradientDrawable("#F4F8FF", 20));
    LinearLayout.LayoutParams wakeCardParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    wakeCardParams.setMargins(0, 8, 0, 12);
    wakeCard.setLayoutParams(wakeCardParams);

    TextView wakeLabel = new TextView(getTopActivity());
    wakeLabel.setText("到点唤醒微信执行");
    wakeLabel.setTextSize(15);
    wakeLabel.setTextColor(Color.parseColor("#1F2937"));
    try { wakeLabel.getPaint().setFakeBoldText(true); } catch (Exception e) {}
    wakeCard.addView(wakeLabel);

    final TextView wakeStateTv = new TextView(getTopActivity());
    wakeStateTv.setTextSize(13);
    wakeStateTv.setPadding(0, 8, 0, 14);
    wakeCard.addView(wakeStateTv);

    final Button wakeToggleBtn = new Button(getTopActivity());
    wakeToggleBtn.setAllCaps(false);
    wakeToggleBtn.setTextSize(14);
    wakeToggleBtn.setPadding(20, 12, 20, 12);
    wakeCard.addView(wakeToggleBtn);

    final Runnable[] refreshWakeUi = new Runnable[1];
    refreshWakeUi[0] = new Runnable() {
        public void run() {
            if (massSendWakeOnTime) {
                wakeStateTv.setText("当前状态：已开启（到点会尝试拉起微信）");
                wakeStateTv.setTextColor(Color.parseColor("#0F766E"));
                GradientDrawable shape = new GradientDrawable();
                shape.setCornerRadius(20);
                shape.setColor(Color.parseColor("#DCFCE7"));
                shape.setStroke(2, Color.parseColor("#4ADE80"));
                wakeToggleBtn.setBackground(shape);
                wakeToggleBtn.setTextColor(Color.parseColor("#166534"));
                wakeToggleBtn.setText("点击关闭唤醒");
            } else {
                wakeStateTv.setText("当前状态：已关闭（不会主动拉起微信）");
                wakeStateTv.setTextColor(Color.parseColor("#9A3412"));
                GradientDrawable shape = new GradientDrawable();
                shape.setCornerRadius(20);
                shape.setColor(Color.parseColor("#FFF7ED"));
                shape.setStroke(2, Color.parseColor("#FDBA74"));
                wakeToggleBtn.setBackground(shape);
                wakeToggleBtn.setTextColor(Color.parseColor("#9A3412"));
                wakeToggleBtn.setText("点击开启唤醒");
            }
        }
    };
    refreshWakeUi[0].run();

    wakeToggleBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            massSendWakeOnTime = !massSendWakeOnTime;
            refreshWakeUi[0].run();
        }
    });

    settingCard.addView(wakeCard);

    final TextView intervalLabel = createTextView(getTopActivity(), "发送对象间隔 (秒):", 14, 8);
    settingCard.addView(intervalLabel);
    final EditText intervalEdit = createStyledEditText("默认 0 秒", String.valueOf(massSendInterval));
    intervalEdit.setInputType(InputType.TYPE_CLASS_NUMBER);
    settingCard.addView(intervalEdit);

    final TextView mediaIntervalLabel = createTextView(getTopActivity(), "多媒体文件间隔 (秒):", 14, 8);
    settingCard.addView(mediaIntervalLabel);
    final EditText mediaIntervalEdit = createStyledEditText("默认 0 秒", String.valueOf(massSendMediaInterval));
    mediaIntervalEdit.setInputType(InputType.TYPE_CLASS_NUMBER);
    settingCard.addView(mediaIntervalEdit);

    root.addView(settingCard);

    final Runnable updateContentUI = new Runnable() {
        public void run() {
            contentContainer.removeAllViews();
            
            // 朋友圈类型仅隐藏目标选择，定时/循环设置仍保留
            boolean isMoments = (massSendType == SEND_TYPE_MOMENTS_TEXT || massSendType == SEND_TYPE_MOMENTS_IMAGE);
            targetCard.setVisibility(isMoments ? View.GONE : View.VISIBLE);
            settingCard.setVisibility(View.VISIBLE);
            intervalLabel.setVisibility(isMoments ? View.GONE : View.VISIBLE);
            intervalEdit.setVisibility(isMoments ? View.GONE : View.VISIBLE);
            mediaIntervalLabel.setVisibility(isMoments ? View.GONE : View.VISIBLE);
            mediaIntervalEdit.setVisibility(isMoments ? View.GONE : View.VISIBLE);
            
            if (massSendType == SEND_TYPE_TEXT || massSendType == SEND_TYPE_MOMENTS_TEXT || massSendType == SEND_TYPE_XML) {
                String hint = "请输入要群发的文本内容...";
                if (massSendType == SEND_TYPE_MOMENTS_TEXT) hint = "请输入朋友圈文本内容...";
                else if (massSendType == SEND_TYPE_XML) hint = "请输入XML内容（如 <msg><appmsg>...</appmsg></msg>）";
                EditText textEdit = createStyledEditText(hint, massSendTextContent);
                textEdit.setMinLines(5);
                textEdit.setGravity(Gravity.TOP);
                textEdit.addTextChangedListener(new TextWatcher() {
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    public void onTextChanged(CharSequence s, int start, int before, int count) {}
                    public void afterTextChanged(Editable s) { massSendTextContent = s.toString(); }
                });
                contentContainer.addView(textEdit);
                
                if (massSendType == SEND_TYPE_TEXT) {
                    contentContainer.addView(createPromptText("支持变量: %friendName% (好友昵称/备注)"));
                } else if (massSendType == SEND_TYPE_XML) {
                    TextView xmlTip = createPromptText("XML将按原文发送给目标会话，支持变量: %friendName%");
                    xmlTip.setTextColor(Color.parseColor("#1565C0"));
                    contentContainer.addView(xmlTip);
                } else {
                    TextView momentsTip = createPromptText("⚠️ 朋友圈模式：将发送到您的朋友圈，无需选择目标");
                    momentsTip.setTextColor(Color.parseColor("#FF9800"));
                    contentContainer.addView(momentsTip);
                }
            } else if (massSendType == SEND_TYPE_MOMENTS_IMAGE) {
                // 朋友圈图文模式
                TextView momentsTip = createPromptText("⚠️ 朋友圈图文：文本+图片（最多9张）");
                momentsTip.setTextColor(Color.parseColor("#FF9800"));
                contentContainer.addView(momentsTip);
                
                EditText textEdit = createStyledEditText("输入朋友圈文字（可为空）", massSendTextContent);
                textEdit.setMinLines(3);
                textEdit.setGravity(Gravity.TOP);
                textEdit.addTextChangedListener(new TextWatcher() {
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    public void onTextChanged(CharSequence s, int start, int before, int count) {}
                    public void afterTextChanged(Editable s) { massSendTextContent = s.toString(); }
                });
                contentContainer.addView(textEdit);
                
                TextView pathTv = new TextView(getTopActivity());
                StringBuilder sb = new StringBuilder();
                if (massSendMediaPaths.isEmpty()) {
                    sb.append("🚫 未选择图片");
                } else {
                    sb.append("✅ 已选 ").append(massSendMediaPaths.size()).append(" 张图片:\n");
                    for (int i = 0; i < Math.min(massSendMediaPaths.size(), 5); i++) {
                        sb.append(new File((String) massSendMediaPaths.get(i)).getName()).append("\n");
                    }
                    if (massSendMediaPaths.size() > 5) sb.append("...等");
                }
                pathTv.setText(sb.toString());
                pathTv.setTextColor(Color.parseColor("#333333"));
                pathTv.setPadding(0, 0, 0, 16);
                contentContainer.addView(pathTv);
                
                Button selMediaBtn = new Button(getTopActivity());
                selMediaBtn.setText("📂 选择图片 (最多9张)");
                styleMediaSelectionButton(selMediaBtn);
                selMediaBtn.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        File lastFolder = new File(getString(DEFAULT_LAST_FOLDER_SP_AUTO, ROOT_FOLDER));
                        browseFolderForSelectionAuto(lastFolder, ".jpg,.png,.jpeg,.gif,.bmp", "", new MediaSelectionCallback() {
                            public void onSelected(ArrayList<String> selectedFiles) {
                                if (selectedFiles.size() > 9) {
                                    toast("朋友圈最多支持9张图片");
                                    massSendMediaPaths.clear();
                                    for (int i = 0; i < 9; i++) {
                                        massSendMediaPaths.add(selectedFiles.get(i));
                                    }
                                } else {
                                    massSendMediaPaths.clear();
                                    massSendMediaPaths.addAll(selectedFiles);
                                }
                                updateContentUI.run();
                            }
                        }, false);
                    }
                });
                contentContainer.addView(selMediaBtn);
                
                if (!massSendMediaPaths.isEmpty()) {
                    Button clearMediaBtn = new Button(getTopActivity());
                    clearMediaBtn.setText("清空已选图片");
                    styleUtilityButton(clearMediaBtn);
                    clearMediaBtn.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) {
                            massSendMediaPaths.clear();
                            updateContentUI.run();
                        }
                    });
                    contentContainer.addView(clearMediaBtn);
                }
            } else {
                TextView pathTv = new TextView(getTopActivity());
                StringBuilder sb = new StringBuilder();
                if (massSendMediaPaths.isEmpty()) sb.append("🚫 未选择文件");
                else {
                    sb.append("✅ 已选 ").append(massSendMediaPaths.size()).append(" 个文件:\n");
                    for (int i = 0; i < Math.min(massSendMediaPaths.size(), 5); i++) {
                        sb.append(new File((String) massSendMediaPaths.get(i)).getName()).append("\n");
                    }
                    if (massSendMediaPaths.size() > 5) sb.append("...等");
                }
                pathTv.setText(sb.toString());
                pathTv.setTextColor(Color.parseColor("#333333"));
                pathTv.setPadding(0, 0, 0, 16);
                contentContainer.addView(pathTv);

                if (massSendType == SEND_TYPE_VOICE) {
                    TextView voiceTip = createPromptText("提示: 语音文件需为 .silk 格式");
                    voiceTip.setTextColor(Color.parseColor("#FF9800"));
                    contentContainer.addView(voiceTip);
                }

                Button selMediaBtn = new Button(getTopActivity());
                selMediaBtn.setText("📂 选择文件 (支持多选)");
                styleMediaSelectionButton(selMediaBtn);
                selMediaBtn.setTag(getMediaSelectTagForMassSend(massSendType));
                selMediaBtn.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        Object[] tag = (Object[]) v.getTag();
                        String extFilter = (String) tag[0];
                        File lastFolder = new File(getString(DEFAULT_LAST_FOLDER_SP_AUTO, ROOT_FOLDER));
                        browseFolderForSelectionAuto(lastFolder, extFilter, "", new MediaSelectionCallback() {
                            public void onSelected(ArrayList<String> selectedFiles) {
                                massSendMediaPaths.clear();
                                massSendMediaPaths.addAll(selectedFiles);
                                updateContentUI.run();
                            }
                        }, false);
                    }
                });
                contentContainer.addView(selMediaBtn);

                if (!massSendMediaPaths.isEmpty()) {
                    Button clearMediaBtn = new Button(getTopActivity());
                    clearMediaBtn.setText("清空已选文件");
                    styleUtilityButton(clearMediaBtn);
                    clearMediaBtn.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) {
                            massSendMediaPaths.clear();
                            updateContentUI.run();
                        }
                    });
                    contentContainer.addView(clearMediaBtn);
                }
            }
        }
    };

    switch (massSendType) {
        case SEND_TYPE_TEXT: rbText.setChecked(true); break;
        case SEND_TYPE_IMAGE: rbImage.setChecked(true); break;
        case SEND_TYPE_VIDEO: rbVideo.setChecked(true); break;
        case SEND_TYPE_FILE: rbFile.setChecked(true); break;
        case SEND_TYPE_EMOJI: rbEmoji.setChecked(true); break;
        case SEND_TYPE_VOICE: rbVoice.setChecked(true); break;
        case SEND_TYPE_MOMENTS_TEXT: rbMomentsText.setChecked(true); break;
        case SEND_TYPE_MOMENTS_IMAGE: rbMomentsImage.setChecked(true); break;
        case SEND_TYPE_XML: rbXml.setChecked(true); break;
    }
    updateContentUI.run();

    final boolean[] isProcessing = {false};
    row1Group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
        public void onCheckedChanged(RadioGroup group, int checkedId) {
            if (isProcessing[0]) return;
            isProcessing[0] = true;
            if (checkedId == rbText.getId()) massSendType = SEND_TYPE_TEXT;
            else if (checkedId == rbImage.getId()) massSendType = SEND_TYPE_IMAGE;
            else if (checkedId == rbVideo.getId()) massSendType = SEND_TYPE_VIDEO;
            row2Group.clearCheck();
            row3Group.clearCheck();
            row4Group.clearCheck();
            massSendMediaPaths.clear();
            updateContentUI.run();
            isProcessing[0] = false;
        }
    });
    
    row2Group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
        public void onCheckedChanged(RadioGroup group, int checkedId) {
            if (isProcessing[0]) return;
            isProcessing[0] = true;
            if (checkedId == rbFile.getId()) massSendType = SEND_TYPE_FILE;
            else if (checkedId == rbEmoji.getId()) massSendType = SEND_TYPE_EMOJI;
            else if (checkedId == rbVoice.getId()) massSendType = SEND_TYPE_VOICE;
            row1Group.clearCheck();
            row3Group.clearCheck();
            row4Group.clearCheck();
            massSendMediaPaths.clear();
            updateContentUI.run();
            isProcessing[0] = false;
        }
    });
    
    row3Group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
        public void onCheckedChanged(RadioGroup group, int checkedId) {
            if (isProcessing[0]) return;
            isProcessing[0] = true;
            if (checkedId == rbMomentsText.getId()) {
                massSendType = SEND_TYPE_MOMENTS_TEXT;
                massSendMediaPaths.clear();
            } else if (checkedId == rbMomentsImage.getId()) {
                massSendType = SEND_TYPE_MOMENTS_IMAGE;
            }
            row1Group.clearCheck();
            row2Group.clearCheck();
            row4Group.clearCheck();
            updateContentUI.run();
            isProcessing[0] = false;
        }
    });
    
    row4Group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
        public void onCheckedChanged(RadioGroup group, int checkedId) {
            if (isProcessing[0]) return;
            isProcessing[0] = true;
            if (checkedId == rbXml.getId()) {
                massSendType = SEND_TYPE_XML;
                massSendMediaPaths.clear();
            }
            row1Group.clearCheck();
            row2Group.clearCheck();
            row3Group.clearCheck();
            updateContentUI.run();
            isProcessing[0] = false;
        }
    });

    final Runnable saveTaskAction = new Runnable() {
        public void run() {
            boolean isMomentsMode = (massSendType == SEND_TYPE_MOMENTS_TEXT || massSendType == SEND_TYPE_MOMENTS_IMAGE);
            if (!isMomentsMode && massSendTargetWxids.isEmpty()) { toast("请选择发送目标！"); return; }
            if ((massSendType == SEND_TYPE_TEXT || massSendType == SEND_TYPE_XML) && TextUtils.isEmpty(massSendTextContent)) { toast("请输入文本内容！"); return; }
            if (massSendType != SEND_TYPE_TEXT && massSendType != SEND_TYPE_XML && massSendType != SEND_TYPE_VOICE && massSendType != SEND_TYPE_MOMENTS_TEXT && massSendType != SEND_TYPE_MOMENTS_IMAGE && massSendMediaPaths.isEmpty()) { toast("请选择发送文件！"); return; }
            if (massSendType == SEND_TYPE_VOICE && massSendMediaPaths.isEmpty()) { toast("请选择语音文件(.silk)！"); return; }

            final List<Integer> selectedDays = new ArrayList<Integer>();
            if (massSendRepeatType == 2) {
                for (int i = 0; i < weekCbs.length; i++) {
                    CheckBox cb = weekCbs[i];
                    if (cb != null && cb.isChecked()) selectedDays.add((Integer) cb.getTag());
                }
                if (selectedDays.isEmpty()) {
                    toast("每周循环必须至少勾选一天！");
                    return;
                }
            }

            try {
                massSendInterval = Long.parseLong(intervalEdit.getText().toString());
                massSendMediaInterval = Long.parseLong(mediaIntervalEdit.getText().toString());
            } catch (Exception e) {}

            showDateTimePickerWithSeconds(new DatePickerCallback() {
                public void onTimeSelected(long timestamp) {
                    createAndSaveTask(timestamp, selectedDays);
                    updateStatsText(statsTv);
                }
            });
        }
    };

    newTaskBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            saveTaskAction.run();
        }
    });

    AlertDialog dialog = buildCommonAlertDialog(getTopActivity(), "🕒 定时发送助手", scrollView, "💾 保存为新任务", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
            saveTaskAction.run();
        }
    }, "立即直接发送", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
            boolean isMomentsMode = (massSendType == SEND_TYPE_MOMENTS_TEXT || massSendType == SEND_TYPE_MOMENTS_IMAGE);
            if (!isMomentsMode && massSendTargetWxids.isEmpty()) { toast("请选择发送目标！"); return; }
            if ((massSendType == SEND_TYPE_TEXT || massSendType == SEND_TYPE_XML) && TextUtils.isEmpty(massSendTextContent)) { toast("请输入文本内容！"); return; }
            if (massSendType != SEND_TYPE_TEXT && massSendType != SEND_TYPE_XML && massSendType != SEND_TYPE_VOICE && massSendType != SEND_TYPE_MOMENTS_TEXT && massSendType != SEND_TYPE_MOMENTS_IMAGE && massSendMediaPaths.isEmpty()) { toast("请选择发送文件！"); return; }
            if (massSendType == SEND_TYPE_VOICE && massSendMediaPaths.isEmpty()) { toast("请选择语音文件！"); return; }

            try {
                massSendInterval = Long.parseLong(intervalEdit.getText().toString());
                massSendMediaInterval = Long.parseLong(mediaIntervalEdit.getText().toString());
            } catch (Exception e) {}

            toast("🚀 开始立即发送...");
            executeImmediateSend();
        }
    }, "关闭", null);
    dialog.show();
}

/**
 * 创建并保存新任务
 */
private void createAndSaveTask(long planTime, List<Integer> repeatDays) {
    String taskId = UUID.randomUUID().toString();

    try {
        JSONObject task = new JSONObject();
        task.put("taskId", taskId);
        task.put("planTime", planTime);
        task.put("type", massSendType);
        task.put("content", massSendTextContent);
        task.put("interval", massSendInterval);
        task.put("mediaInterval", massSendMediaInterval);
        task.put("repeatType", massSendRepeatType);
        task.put("wakeOnTime", massSendWakeOnTime);

        if (massSendRepeatType == 2 && repeatDays != null) {
            task.put("repeatDays", listToJsonArray(repeatDays));
        }

        task.put("targets", setToJsonArray(massSendTargetWxids));
        task.put("medias", listToJsonArray(massSendMediaPaths));
        task.put("status", "pending");
        task.put("createdTime", System.currentTimeMillis());

        scheduledTasks.put(taskId, task);
        saveAllTasks();

        long delay = planTime - System.currentTimeMillis();
        if (delay > 0) {
            scheduleTaskWithPrecision(taskId, task, delay);
            String msg = "✅ 任务已排期在:\n" + formatTimeWithSeconds(planTime);
            if (massSendRepeatType == 1) msg += "\n(循环: 每天)";
            else if (massSendRepeatType == 2) {
                JSONArray arr = listToJsonArray(repeatDays);
                msg += "\n(循环: 每周 " + formatRepeatDays(arr) + ")";
            }
            msg += massSendWakeOnTime ? "\n(到点唤醒微信: 开启)" : "\n(到点唤醒微信: 关闭)";
            toast(msg);
        } else {
            toast("⚠️ 时间已过");
            scheduledTasks.remove(taskId);
        }
    } catch (Exception e) {
        toast("创建任务失败: " + e.getMessage());
    }
}

/**
 * 立即执行发送
 */
private void executeImmediateSend() {
    final List<String> targets = new ArrayList<String>(massSendTargetWxids);
    final int type = massSendType;
    final String content = massSendTextContent;
    final List<String> mediaPaths = new ArrayList<String>(massSendMediaPaths);
    final long interval = massSendInterval;
    final long mediaInterval = massSendMediaInterval;

    new Thread(new Runnable() {
        public void run() {
            int success = 0;
            int fail = 0;
            
            // 朋友圈模式特殊处理
            if (type == SEND_TYPE_MOMENTS_TEXT || type == SEND_TYPE_MOMENTS_IMAGE) {
                try {
                    if (type == SEND_TYPE_MOMENTS_TEXT) {
                        uploadText(content);
                    } else {
                        if (mediaPaths.isEmpty()) {
                            uploadText(content);
                        } else {
                            uploadTextAndPicList(content, mediaPaths);
                        }
                    }
                    success = 1;
                    notify("朋友圈发送成功", type == SEND_TYPE_MOMENTS_TEXT ? "已发送纯文本" : "已发送图文");
                } catch (Exception e) {
                    fail = 1;
                    log("朋友圈发送失败: " + e.getMessage());
                }
                
                final String report = "成功: " + success + ", 失败: " + fail;
                notify("发送完成", report);
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    public void run() { toast("✅ 发送完成\n" + report); }
                });
                return;
            }
            
            // 原有群发逻辑
            for (int i = 0; i < targets.size(); i++) {
                final String target = targets.get(i);
                final String name = getContactName(target);
                boolean targetSuccess = true;

                try {
                    if (type == SEND_TYPE_TEXT) {
                        String finalContent = content.replace("%friendName%", name);
                        sendText(target, finalContent);
                    } else if (type == SEND_TYPE_XML) {
                        String xmlToSend = content.replace("%friendName%", name);
                        sendXmlMsg(target, xmlToSend);
                    } else {
                        for (int j = 0; j < mediaPaths.size(); j++) {
                            final String path = mediaPaths.get(j);
                            final File f = new File(path);
                            if (f.exists()) {
                                switch (type) {
                                    case SEND_TYPE_IMAGE: sendImage(target, path); break;
                                    case SEND_TYPE_VIDEO: sendVideo(target, path); break;
                                    case SEND_TYPE_EMOJI: sendEmoji(target, path); break;
                                    case SEND_TYPE_FILE: shareFile(target, f.getName(), path, ""); break;
                                    case SEND_TYPE_VOICE:
                                        runOnMainSync(new Runnable() {
                                            public void run() {
                                                sendVoice(target, path);
                                            }
                                        });
                                        break;
                                }
                                if (mediaInterval > 0 && j < mediaPaths.size() - 1) Thread.sleep(mediaInterval * 1000);
                            }
                        }
                    }
                } catch (Exception e) {
                    targetSuccess = false;
                    log("发送失败: " + target + " - " + e.getMessage());
                }

                if (targetSuccess) success++; else fail++;

                if (i < targets.size() - 1 && interval > 0) {
                    try { Thread.sleep(interval * 1000); } catch (Exception e) {}
                }
            }

            final String report = "成功: " + success + ", 失败: " + fail;
            notify("发送完成", report);
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                public void run() { toast("✅ 发送完成\n" + report); }
            });
        }
    }).start();
}
/**
 * 更新任务统计显示
 */
private void updateStatsText(TextView tv) {
    if (tv == null) return;
    int pending = 0;
    int running = 0;
    int completed = 0;

    Iterator it = scheduledTasks.values().iterator();
    while (it.hasNext()) {
        JSONObject task = (JSONObject) it.next();
        String status = task.optString("status", "");
        if ("pending".equals(status)) pending++;
        else if ("running".equals(status)) running++;
        else if ("completed".equals(status)) completed++;
    }

    StringBuilder sb = new StringBuilder();
    sb.append("待执行/循环中: ").append(pending).append(" 个\n");
    sb.append("执行中: ").append(running).append(" 个\n");
    sb.append("已完成单次: ").append(completed).append(" 个\n");
    sb.append("━━━━━━━━━━━━━\n");
    sb.append("总计任务: ").append(scheduledTasks.size()).append(" 个");
    tv.setText(sb.toString());
    tv.setTextColor(Color.parseColor("#2E7D32"));
}

// ==========================================
// ========== 📋 任务列表管理 ==========
// ==========================================

private void showTaskListDialog() {
    ScrollView scrollView = new ScrollView(getTopActivity());
    LinearLayout root = new LinearLayout(getTopActivity());
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(24, 24, 24, 24);
    root.setBackgroundColor(Color.parseColor("#FAFBF9"));
    scrollView.addView(root);

    root.addView(createSectionTitle("📋 定时任务列表"));

    if (scheduledTasks.isEmpty()) {
        root.addView(createPromptText("暂无定时任务"));
    } else {
        List<JSONObject> sortedTasks = new ArrayList<JSONObject>();
        Iterator it0 = scheduledTasks.values().iterator();
        while (it0.hasNext()) {
            sortedTasks.add((JSONObject) it0.next());
        }

        Collections.sort(sortedTasks, new java.util.Comparator<JSONObject>() {
            public int compare(JSONObject t1, JSONObject t2) {
                long p1 = t1.optLong("planTime", 0);
                long p2 = t2.optLong("planTime", 0);
                if (p1 == p2) return 0;
                return p1 < p2 ? -1 : 1;
            }
        });

        final ListView taskListView = new ListView(getTopActivity());
        setupListViewTouchForScroll(taskListView);

        final List<String> taskIds = new ArrayList<String>();
        final List<String> taskDisplays = new ArrayList<String>();

        for (int i = 0; i < sortedTasks.size(); i++) {
            JSONObject task = sortedTasks.get(i);
            String taskId = task.optString("taskId", "");
            long planTime = task.optLong("planTime", 0);
            String status = task.optString("status", "");
            JSONArray targetsArr = task.optJSONArray("targets");
            int targetCount = targetsArr != null ? targetsArr.length() : 0;
            int type = task.optInt("type", 0);
            int repeatType = task.optInt("repeatType", 0);
            boolean wakeOnTime = task.optBoolean("wakeOnTime", false);

            String typeStr = "";
            switch (type) {
                case SEND_TYPE_TEXT: typeStr = "📝文本"; break;
                case SEND_TYPE_IMAGE: typeStr = "🖼️图片"; break;
                case SEND_TYPE_VIDEO: typeStr = "🎬视频"; break;
                case SEND_TYPE_FILE: typeStr = "📁文件"; break;
                case SEND_TYPE_EMOJI: typeStr = "😊表情"; break;
                case SEND_TYPE_VOICE: typeStr = "🎤语音"; break;
                case SEND_TYPE_MOMENTS_TEXT: typeStr = "🌟朋友圈文本"; break;
                case SEND_TYPE_MOMENTS_IMAGE: typeStr = "🌟朋友圈图文"; break;
                case SEND_TYPE_XML: typeStr = "🧩XML"; break;
            }

            String statusEmoji = "";
            if ("pending".equals(status)) statusEmoji = "⏳";
            else if ("running".equals(status)) statusEmoji = "🔄";
            else if ("completed".equals(status)) statusEmoji = "✅";
            else if ("expired".equals(status)) statusEmoji = "❌";

            String repeatStr = "";
            if (repeatType == 1) repeatStr = " [每天]";
            else if (repeatType == 2) {
                repeatStr = " [每周 " + formatRepeatDays(task.optJSONArray("repeatDays")) + "]";
            }
            if (wakeOnTime) repeatStr += " [唤醒]";

            String display = statusEmoji + " " + formatTimeWithSeconds(planTime) + repeatStr + "\n" +
                    typeStr + " | " + targetCount + " 个目标 | " + status;

            taskIds.add(taskId);
            taskDisplays.add(display);
        }

        final ArrayAdapter<String> adapter = new ArrayAdapter<String>(getTopActivity(),
                android.R.layout.simple_list_item_1, taskDisplays);
        taskListView.setAdapter(adapter);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(Math.min(taskDisplays.size() * 80, 400))
        );
        params.setMargins(0, 16, 0, 16);
        taskListView.setLayoutParams(params);

        taskListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String taskId = taskIds.get(position);
                JSONObject task = (JSONObject) scheduledTasks.get(taskId);
                if (task != null) showTaskOperationMenu(taskId, task, adapter, taskIds, taskDisplays);
            }
        });

        root.addView(taskListView);
    }

    AlertDialog dialog = buildCommonAlertDialog(getTopActivity(), "📋 任务管理", scrollView, "关闭", null, null, null, null, null);
    dialog.show();
}

private void showTaskOperationMenu(final String taskId, final JSONObject task, final ArrayAdapter<String> adapter, final List<String> taskIds, final List<String> taskDisplays) {
    String status = task.optString("status", "");
    int repeatType = task.optInt("repeatType", 0);
    final String[] options;

    if ("pending".equals(status)) {
        if (repeatType > 0 && task.has("lastExecutedTime")) {
            int s = task.optInt("lastSuccessCount", 0);
            int f = task.optInt("lastFailCount", 0);
            options = new String[]{"🛑 终止循环并删除", "▶️ 立即执行一轮", "📊 查看上轮结果 (成功" + s + " 失败" + f + ")"};
        } else {
            options = new String[]{"🛑 取消/删除此任务", "▶️ 立即执行"};
        }
    } else if ("completed".equals(status)) {
        int success = task.optInt("successCount", 0);
        int fail = task.optInt("failCount", 0);
        options = new String[]{"📊 查看结果 (成功" + success + " 失败" + fail + ")", "🗑️ 删除记录"};
    } else {
        options = new String[]{"🗑️ 删除记录"};
    }

    AlertDialog.Builder builder = new AlertDialog.Builder(getTopActivity());
    builder.setTitle("任务操作");
    builder.setItems(options, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
            String sel = options[which];
            if (sel.contains("取消") || sel.contains("终止") || sel.contains("删除记录")) {
                cancelTaskTimer(taskId);
                scheduledTasks.remove(taskId);
                saveAllTasks();
                int pos = taskIds.indexOf(taskId);
                if (pos >= 0) {
                    taskIds.remove(pos);
                    taskDisplays.remove(pos);
                    adapter.notifyDataSetChanged();
                }
                toast("任务已删除/取消");
            } else if (sel.contains("立即执行")) {
                JSONObject t = (JSONObject) scheduledTasks.get(taskId);
                if (t != null) {
                    try {
                        t.put("planTime", System.currentTimeMillis());
                    } catch (Exception e) {}
                    saveAllTasks();
                    executeTaskSend(taskId);
                }
            } else if (sel.contains("结果")) {
                int s = task.optInt(task.has("lastSuccessCount") ? "lastSuccessCount" : "successCount", 0);
                int f = task.optInt(task.has("lastFailCount") ? "lastFailCount" : "failCount", 0);
                long ct = task.optLong(task.has("lastExecutedTime") ? "lastExecutedTime" : "completedTime", 0);
                String msg = "发送结果:\n成功: " + s + "\n失败: " + f + "\n执行时间: " + formatTimeWithSeconds(ct);
                new AlertDialog.Builder(getTopActivity()).setTitle("📊 报告").setMessage(msg).setPositiveButton("确定", null).show();
            }
        }
    });
    AlertDialog menuDialog = builder.create();
    setupUnifiedDialog(menuDialog);
    menuDialog.show();
}

// ==========================================
// ========== 🕐 时间选择器 (支持秒) ==========
// ==========================================

interface DatePickerCallback {
    void onTimeSelected(long timestamp);
}

private void showDateTimePickerWithSeconds(final DatePickerCallback callback) {
    final Calendar calendar = Calendar.getInstance();
    DatePickerDialog dateDialog = new DatePickerDialog(getTopActivity(), new DatePickerDialog.OnDateSetListener() {
        public void onDateSet(DatePicker view, final int year, final int month, final int dayOfMonth) {
            TimePickerDialog timeDialog = new TimePickerDialog(getTopActivity(), new TimePickerDialog.OnTimeSetListener() {
                public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                    showSecondPicker(calendar, year, month, dayOfMonth, hourOfDay, minute, callback);
                }
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true);
            timeDialog.show();
        }
    }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
    dateDialog.show();
}

private void showSecondPicker(final Calendar calendar, final int year, final int month, final int day, final int hour, final int minute, final DatePickerCallback callback) {
    final String[] seconds = new String[60];
    for (int i = 0; i < 60; i++) seconds[i] = String.format("%02d 秒", new Object[]{new Integer(i)});
    AlertDialog.Builder builder = new AlertDialog.Builder(getTopActivity());
    builder.setTitle("选择秒数");
    builder.setItems(seconds, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
            calendar.set(year, month, day, hour, minute, which);
            long ts = calendar.getTimeInMillis();
            if (ts < System.currentTimeMillis()) toast("⚠️ 不能选择过去的时间");
            else callback.onTimeSelected(ts);
        }
    });
    builder.show();
}

private String formatTimeWithSeconds(long ts) {
    if (ts <= 0) return "未设置";
    return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(ts));
}

// ==========================================
// ========== 🏷️ 标签管理功能 ==========
// ==========================================

private void showLabelManagementDialog(final Runnable onUpdateCallback) {
    try {
        String labelsJson = getString(CONFIG_KEY, KEY_LABELS, "{}");
        final JSONObject labelsMap = parseJsonObjectSafe(labelsJson);
        final JSONObject safeLabelsMap = labelsMap != null ? labelsMap : new JSONObject();

        ScrollView scrollView = new ScrollView(getTopActivity());
        LinearLayout root = new LinearLayout(getTopActivity());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);
        scrollView.addView(root);

        final ListView labelList = new ListView(getTopActivity());
        setupListViewTouchForScroll(labelList);

        final List<String> labelNames = jsonObjectKeysToList(safeLabelsMap);
        final ArrayAdapter<String> adapter = new ArrayAdapter<String>(getTopActivity(), android.R.layout.simple_list_item_1, labelNames);
        labelList.setAdapter(adapter);

        int count = labelNames.size();
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(Math.max(count * 50, 100))
        );
        listParams.setMargins(0, 16, 0, 16);
        labelList.setLayoutParams(listParams);

        root.addView(createSectionTitle("➕ 添加新标签"));
        final Set<String> newLabelMembers = new HashSet<String>();
        final TextView countTv = createPromptText("当前已选: 0 人");
        countTv.setTextSize(14);
        countTv.setTextColor(Color.parseColor("#333333"));
        root.addView(countTv);

        LinearLayout btnRow = new LinearLayout(getTopActivity());
        btnRow.setOrientation(LinearLayout.HORIZONTAL);

        Button addFriendBtn = new Button(getTopActivity());
        addFriendBtn.setText("👤 添加好友");
        styleUtilityButton(addFriendBtn);
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        p1.setMargins(0, 0, 8, 0);
        addFriendBtn.setLayoutParams(p1);

        Button addGroupBtn = new Button(getTopActivity());
        addGroupBtn.setText("🏠 添加群聊");
        styleUtilityButton(addGroupBtn);
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        p2.setMargins(8, 0, 0, 0);
        addGroupBtn.setLayoutParams(p2);

        btnRow.addView(addFriendBtn);
        btnRow.addView(addGroupBtn);
        root.addView(btnRow);

        final Runnable updateCount = new Runnable() {
            public void run() {
                countTv.setText("当前已选: " + newLabelMembers.size() + " 人");
            }
        };
        addFriendBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showFriendSelectionDialog(newLabelMembers, updateCount); }
        });
        addGroupBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showGroupSelectionDialog(newLabelMembers, updateCount); }
        });

        final EditText nameEdit = createStyledEditText("输入标签名称 (如: 家人, 客户组)", "");
        root.addView(nameEdit);

        final TextView emptyTip = createPromptText("暂无标签，请先添加。");
        emptyTip.setVisibility(labelNames.isEmpty() ? View.VISIBLE : View.GONE);
        root.addView(emptyTip);

        Button saveLabelBtn = new Button(getTopActivity());
        saveLabelBtn.setText("💾 保存标签");
        styleMediaSelectionButton(saveLabelBtn);
        saveLabelBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String name = nameEdit.getText().toString().trim();
                if (TextUtils.isEmpty(name)) { toast("请输入标签名称"); return; }
                if (newLabelMembers.isEmpty()) { toast("请至少选择一个成员"); return; }
                if (safeLabelsMap.has(name)) { toast("标签名 [" + name + "] 已存在"); return; }
                try {
                    safeLabelsMap.put(name, setToJsonArray(newLabelMembers));
                    putString(CONFIG_KEY, KEY_LABELS, safeLabelsMap.toString());
                    labelNames.add(name);
                    Collections.sort(labelNames);
                    adapter.notifyDataSetChanged();
                    emptyTip.setVisibility(View.GONE);
                    LinearLayout.LayoutParams lpNew = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(Math.max(labelNames.size() * 50, 100))
                    );
                    lpNew.setMargins(0, 16, 0, 16);
                    labelList.setLayoutParams(lpNew);
                    nameEdit.setText("");
                    newLabelMembers.clear();
                    updateCount.run();
                    if (onUpdateCallback != null) onUpdateCallback.run();
                    toast("✅ 标签 [" + name + "] 创建成功！");
                } catch (Exception e) {
                    toast("保存标签失败: " + e.getMessage());
                }
            }
        });
        root.addView(saveLabelBtn);
        root.addView(createPromptText("在当前窗口内直接选择成员并保存，不会再弹出新窗口。"));

        TextView listTitle = createSectionTitle("📂 已有标签列表");
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) listTitle.getLayoutParams();
        lp.setMargins(0, 32, 0, 16);
        root.addView(listTitle);
        root.addView(labelList);

        labelList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, final int position, long id) {
                final String labelName = labelNames.get(position);
                String currentLabelsJson = getString(CONFIG_KEY, KEY_LABELS, "{}");
                JSONObject currentMap = parseJsonObjectSafe(currentLabelsJson);
                final JSONArray labelWxidsJson = currentMap != null ? currentMap.optJSONArray(labelName) : new JSONArray();

                final List<String> labelWxids = new ArrayList<String>();
                if (labelWxidsJson != null) {
                    for (int i = 0; i < labelWxidsJson.length(); i++) labelWxids.add(labelWxidsJson.optString(i));
                }

                String[] options = {"👁️ 管理成员 (查看/删除)", "📥 导入到当前发送列表", "➕ 追加成员 (选择好友/群聊)", "🗑️ 删除此标签"};
                AlertDialog.Builder builder = new AlertDialog.Builder(getTopActivity());
                builder.setTitle("操作标签: " + labelName);
                builder.setItems(options, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0:
                                showLabelMembers(labelName, labelWxids);
                                break;
                            case 1:
                                if (labelWxids.isEmpty()) { toast("此标签没有成员"); return; }
                                int importedCount = 0;
                                for (int i = 0; i < labelWxids.size(); i++) {
                                    String tid = labelWxids.get(i);
                                    if (!massSendTargetWxids.contains(tid)) {
                                        massSendTargetWxids.add(tid);
                                        importedCount++;
                                    }
                                }
                                if (onUpdateCallback != null) onUpdateCallback.run();
                                toast("✅ 已导入 " + importedCount + " 个新目标");
                                break;
                            case 2:
                                final Set<String> tempSet = new HashSet<String>(labelWxids);
                                showTargetCategoryDialog(tempSet, new Runnable() {
                                    public void run() {
                                        try {
                                            safeLabelsMap.put(labelName, setToJsonArray(tempSet));
                                            putString(CONFIG_KEY, KEY_LABELS, safeLabelsMap.toString());
                                            toast("✅ 标签 [" + labelName + "] 更新成功");
                                        } catch (Exception e) {
                                            toast("更新标签失败: " + e.getMessage());
                                        }
                                    }
                                });
                                break;
                            case 3:
                                removeJsonKey(safeLabelsMap, labelName);
                                putString(CONFIG_KEY, KEY_LABELS, safeLabelsMap.toString());
                                toast("标签已删除");
                                labelNames.remove(position);
                                adapter.notifyDataSetChanged();
                                emptyTip.setVisibility(labelNames.isEmpty() ? View.VISIBLE : View.GONE);
                                break;
                        }
                    }
                });
                AlertDialog menuDialog = builder.create();
                setupUnifiedDialog(menuDialog);
                menuDialog.show();
            }
        });

        AlertDialog dialog = buildCommonAlertDialog(getTopActivity(), "🏷️ 标签分组管理", scrollView, "关闭", null, null, null, null, null);
        dialog.show();
    } catch (Exception e) {
        toast("无法打开标签管理: " + e.getMessage());
    }
}

private void showLabelMembers(final String labelName, final List<String> wxids) {
    showLoadingDialog("正在加载成员...", "请稍候...", new Runnable() {
        public void run() {
            if (sCachedFriendList == null) sCachedFriendList = getFriendList();
            if (sCachedGroupList == null) sCachedGroupList = getGroupList();

            new Handler(Looper.getMainLooper()).post(new Runnable() {
                public void run() {
                    LinearLayout layout = new LinearLayout(getTopActivity());
                    layout.setOrientation(LinearLayout.VERTICAL);
                    layout.setPadding(32, 32, 32, 32);

                    if (wxids.isEmpty()) {
                        layout.addView(createPromptText("此标签暂无成员"));
                    } else {
                        TextView tip = createPromptText("👇 点击成员可将其移除");
                        tip.setTextColor(Color.parseColor("#FF9800"));
                        layout.addView(tip);

                        final ListView listView = new ListView(getTopActivity());
                        setupListViewTouchForScroll(listView);

                        final List<String> displayList = new ArrayList<String>();
                        final List<String> idList = new ArrayList<String>(wxids);
                        for (int i = 0; i < idList.size(); i++) {
                            displayList.add(formatMemberDisplay(idList.get(i)));
                        }

                        final ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                                getTopActivity(), android.R.layout.simple_list_item_1, displayList
                        );
                        listView.setAdapter(adapter);

                        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                            public void onItemClick(AdapterView<?> parent, View view, final int position, long id) {
                                final String wxidToRemove = idList.get(position);
                                String name = getContactName(wxidToRemove);
                                AlertDialog.Builder confirm = new AlertDialog.Builder(getTopActivity());
                                confirm.setTitle("删除成员?");
                                confirm.setMessage("确定要从标签 [" + labelName + "] 中移除:\n" + name + " 吗?");
                                confirm.setPositiveButton("🗑️ 移除", new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface d, int w) {
                                        idList.remove(position);
                                        displayList.remove(position);
                                        adapter.notifyDataSetChanged();
                                        try {
                                            String jsonStr = getString(CONFIG_KEY, KEY_LABELS, "{}");
                                            JSONObject map = parseJsonObjectSafe(jsonStr);
                                            if (map != null) {
                                                map.put(labelName, listToJsonArray(idList));
                                                putString(CONFIG_KEY, KEY_LABELS, map.toString());
                                                toast("已移除");
                                            }
                                        } catch (Exception e) {}
                                    }
                                });
                                confirm.setNegativeButton("取消", null);
                                AlertDialog confirmDialog = confirm.create();
                                setupUnifiedDialog(confirmDialog);
                                confirmDialog.show();
                            }
                        });

                        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(400)
                        );
                        listView.setLayoutParams(listParams);
                        layout.addView(listView);
                    }

                    AlertDialog dialog = buildCommonAlertDialog(getTopActivity(), "👁️ [" + labelName + "] 成员列表", layout, "关闭", null, null, null, null, null);
                    dialog.show();
                }
            });
        }
    });
}

private String formatMemberDisplay(String wxid) {
    if (wxid.endsWith("@chatroom")) return "🏠 " + getGroupName(wxid);
    String fName = "未知";
    String fRemark = "";
    boolean found = false;
    if (sCachedFriendList != null) {
        for (int i = 0; i < sCachedFriendList.size(); i++) {
            FriendInfo f = (FriendInfo) sCachedFriendList.get(i);
            if (f.getWxid().equals(wxid)) {
                fName = TextUtils.isEmpty(f.getNickname()) ? "未知昵称" : f.getNickname();
                fRemark = f.getRemark();
                found = true;
                break;
            }
        }
    }
    if (!found) fName = getFriendDisplayName(wxid);
    return "👤 " + (!TextUtils.isEmpty(fRemark) ? fName + " (" + fRemark + ")" : fName);
}

// ==========================================
// ========== 👥 目标选择功能 ==========
// ==========================================

private void showTargetCategoryDialog(final Set<String> targetSet, final Runnable onFinish) {
    String[] items = new String[]{"👤 选择好友", "🏠 选择群聊"};
    AlertDialog.Builder builder = new AlertDialog.Builder(getTopActivity());
    builder.setTitle("请选择目标类型");
    builder.setItems(items, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
            if (which == 0) showFriendSelectionDialog(targetSet, onFinish);
            else showGroupSelectionDialog(targetSet, onFinish);
        }
    });
    AlertDialog dialog = builder.create();
    dialog.setOnShowListener(new DialogInterface.OnShowListener() {
        public void onShow(DialogInterface d) { setupUnifiedDialog((AlertDialog) d); }
    });
    dialog.show();
}

private void showFriendSelectionDialog(final Set<String> targetSet, final Runnable onFinish) {
    showLoadingDialog("加载好友列表", "正在获取好友...", new Runnable() {
        public void run() {
            if (sCachedFriendList == null) sCachedFriendList = getFriendList();
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                public void run() {
                    List<String> names = new ArrayList<String>();
                    List<String> ids = new ArrayList<String>();
                    if (sCachedFriendList != null) {
                        for (int i = 0; i < sCachedFriendList.size(); i++) {
                            FriendInfo f = (FriendInfo) sCachedFriendList.get(i);
                            String nickname = TextUtils.isEmpty(f.getNickname()) ? "未知" : f.getNickname();
                            String displayName = !TextUtils.isEmpty(f.getRemark()) ? nickname + " (" + f.getRemark() + ")" : nickname;
                            names.add("👤 " + displayName);
                            ids.add(f.getWxid());
                        }
                    }
                    showMultiSelectDialog("选择好友", names, ids, targetSet, "搜索昵称/备注...", new Runnable() {
                        public void run() {
                            if (onFinish != null) onFinish.run();
                        }
                    }, null);
                }
            });
        }
    });
}

private void showGroupSelectionDialog(final Set<String> targetSet, final Runnable onFinish) {
    showLoadingDialog("加载群聊列表", "正在获取群聊...", new Runnable() {
        public void run() {
            if (sCachedGroupList == null) sCachedGroupList = getGroupList();
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                public void run() {
                    List<String> names = new ArrayList<String>();
                    List<String> ids = new ArrayList<String>();
                    if (sCachedGroupList != null) {
                        for (int i = 0; i < sCachedGroupList.size(); i++) {
                            GroupInfo g = (GroupInfo) sCachedGroupList.get(i);
                            names.add("🏠 " + (!TextUtils.isEmpty(g.getName()) ? g.getName() : "未命名群聊"));
                            ids.add(g.getRoomId());
                        }
                    }
                    showMultiSelectDialog("选择群聊", names, ids, targetSet, "搜索群名...", new Runnable() {
                        public void run() {
                            if (onFinish != null) onFinish.run();
                        }
                    }, null);
                }
            });
        }
    });
}

private void showMultiSelectDialog(final String title, final List allItems, final List idList, final Set selectedIds, String searchHint, final Runnable onConfirm, final Runnable updateList) {
    try {
        final Set tempSelected = new HashSet(selectedIds);
        ScrollView scrollView = new ScrollView(getTopActivity());
        LinearLayout mainLayout = new LinearLayout(getTopActivity());
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(24, 24, 24, 24);
        scrollView.addView(mainLayout);

        final EditText searchEditText = createStyledEditText(searchHint, "");
        searchEditText.setSingleLine(true);
        mainLayout.addView(searchEditText);

        final ListView listView = new ListView(getTopActivity());
        setupListViewTouchForScroll(listView);
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(300));
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
                    if (searchText.length() == 0 || name.toLowerCase().contains(searchText) || id.toLowerCase().contains(searchText)) {
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

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
                String selected = (String) currentFilteredIds.get(pos);
                if (listView.isItemChecked(pos)) tempSelected.add(selected);
                else tempSelected.remove(selected);
                if (updateList != null) updateList.run();
            }
        });

        searchEditText.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            public void afterTextChanged(Editable s) { updateListRunnable.run(); }
        });

        final DialogInterface.OnClickListener fullSelectListener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                boolean allSelected = true;
                for (int i = 0; i < currentFilteredIds.size(); i++) {
                    Object idObj = currentFilteredIds.get(i);
                    if (!tempSelected.contains(idObj)) {
                        allSelected = false;
                        break;
                    }
                }
                if (allSelected) {
                    for (int i = 0; i < currentFilteredIds.size(); i++) tempSelected.remove(currentFilteredIds.get(i));
                } else {
                    for (int i = 0; i < currentFilteredIds.size(); i++) tempSelected.add(currentFilteredIds.get(i));
                }
                updateListRunnable.run();
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
            public void onClick(DialogInterface dialog, int which) { dialog.dismiss(); }
        }, "全选/反选", fullSelectListener);

        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            public void onShow(DialogInterface d) {
                setupUnifiedDialog((AlertDialog) d);
                Button neutral = ((AlertDialog) d).getButton(AlertDialog.BUTTON_NEUTRAL);
                if (neutral != null) {
                    neutral.setOnClickListener(new View.OnClickListener() {
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

// ==========================================
// ========== 📁 媒体选择功能（单例模式） ==========
// ==========================================

void browseFolderForSelectionAuto(final File startFolder, final String wantedExtFilter, final String currentSelection, final MediaSelectionCallback callback, final boolean allowFolderSelect) {
    gWantedExtFilterAuto = wantedExtFilter;
    gCurrentSelectionAuto = currentSelection;
    gMediaCallbackAuto = callback;
    gAllowFolderSelectAuto = allowFolderSelect;
    gCurrentFolderAuto = startFolder;

    if (startFolder != null && startFolder.exists() && startFolder.isDirectory()) {
        putString(DEFAULT_LAST_FOLDER_SP_AUTO, startFolder.getAbsolutePath());
    }

    if (gFolderDialogAuto != null && gFolderDialogAuto.isShowing()) {
        gFolderDialogAuto.dismiss();
        gFolderDialogAuto = null;
    }

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
            if (selected.equals(gCurrentFolderAuto) && gFolderNamesAuto.get(pos).toString().startsWith("⚠")) {
                toast("该目录不可读，请使用手动输入路径");
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

    builder.setPositiveButton("在此目录选择文件", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface d, int which) {
            d.dismiss();
            gFolderDialogAuto = null;
            scanFilesMulti(gCurrentFolderAuto, gWantedExtFilterAuto, gCurrentSelectionAuto, gMediaCallbackAuto);
        }
    });

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

    builder.setNegativeButton("手动输入路径", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface d, int which) {
            d.dismiss();
            gFolderDialogAuto = null;
            showManualPathDialogAuto(gWantedExtFilterAuto, gCurrentSelectionAuto, gMediaCallbackAuto, gAllowFolderSelectAuto);
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

void refreshFolderListAuto(File folder) {
    gFolderNamesAuto.clear();
    gFolderFilesAuto.clear();

    if (folder == null || !folder.exists() || !folder.isDirectory()) {
        gFolderNamesAuto.add("⚠ 路径无效或不可访问");
        gFolderFilesAuto.add(gCurrentFolderAuto);
        return;
    }

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
        gFolderNamesAuto.add("⚠ 当前目录不可读，请点手动输入路径");
        gFolderFilesAuto.add(folder);
        return;
    }

    Arrays.sort(subs, new java.util.Comparator<File>() {
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
        gFolderNamesAuto.add("（此目录无子文件夹，可点在此目录选择文件）");
        gFolderFilesAuto.add(folder);
    }
}

private void showManualPathDialogAuto(final String wantedExtFilter, final String currentSelection, final MediaSelectionCallback callback, final boolean allowFolderSelect) {
    android.app.Activity act = getTopActivity();
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
        Arrays.sort(list);
        String[] exts = TextUtils.isEmpty(extFilter) ? new String[0] : extFilter.split(",");
        for (int i = 0; i < list.length; i++) {
            File f = list[i];
            if (f.isFile()) {
                boolean matches = exts.length == 0;
                for (int k = 0; k < exts.length; k++) {
                    String e = exts[k];
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

    AlertDialog.Builder builder = new AlertDialog.Builder(getTopActivity());
    builder.setTitle("选择文件（可多选）");
    final ListView listView = new ListView(getTopActivity());
    listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
    listView.setAdapter(new ArrayAdapter<String>(getTopActivity(), android.R.layout.simple_list_item_multiple_choice, names));
    builder.setView(listView);

    builder.setPositiveButton("确认选择", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface d, int which) {
            ArrayList<String> selectedPaths = new ArrayList<String>();
            for (int i = 0; i < names.size(); i++) {
                if (listView.isItemChecked(i)) selectedPaths.add(files.get(i).getAbsolutePath());
            }
            callback.onSelected(selectedPaths);
        }
    });
    builder.setNegativeButton("取消", null);
    final AlertDialog finalDialog = builder.create();
    finalDialog.setOnShowListener(new DialogInterface.OnShowListener() {
        public void onShow(DialogInterface d) { setupUnifiedDialog(finalDialog); }
    });
    finalDialog.show();
}

private Object[] getMediaSelectTagForMassSend(int type) {
    String extFilter = "";
    switch (type) {
        case SEND_TYPE_IMAGE: extFilter = ".jpg,.png,.jpeg,.gif,.bmp"; break;
        case SEND_TYPE_VIDEO: extFilter = ".mp4"; break;
        case SEND_TYPE_EMOJI: extFilter = ".gif"; break;
        case SEND_TYPE_FILE: extFilter = ""; break;
        case SEND_TYPE_VOICE: extFilter = ".silk"; break;
        case SEND_TYPE_MOMENTS_IMAGE: extFilter = ".jpg,.png,.jpeg,.gif,.bmp"; break;
    }
    return new Object[]{extFilter, false, false, true};
}

// ==========================================
// ========== 👤 联系人和群聊辅助功能 ==========
// ==========================================

private String getContactName(String wxid) {
    try {
        if (wxid.endsWith("@chatroom")) {
            if (sCachedGroupList == null) sCachedGroupList = getGroupList();
            for (int i = 0; i < sCachedGroupList.size(); i++) {
                GroupInfo g = (GroupInfo) sCachedGroupList.get(i);
                if (g.getRoomId().equals(wxid)) return g.getName();
            }
        } else {
            return getFriendDisplayName(wxid);
        }
    } catch (Exception e) {}
    return wxid;
}

private String getFriendDisplayName(String friendWxid) {
    try {
        if (sCachedFriendList == null) sCachedFriendList = getFriendList();
        if (sCachedFriendList != null) {
            for (int i = 0; i < sCachedFriendList.size(); i++) {
                FriendInfo f = (FriendInfo) sCachedFriendList.get(i);
                if (friendWxid.equals(f.getWxid())) {
                    String remark = f.getRemark();
                    if (!TextUtils.isEmpty(remark)) return remark;
                    String nickname = f.getNickname();
                    return TextUtils.isEmpty(nickname) ? friendWxid : nickname;
                }
            }
        }
    } catch (Exception e) {}
    return getFriendName(friendWxid);
}

private String getGroupName(String groupWxid) {
    try {
        if (sCachedGroupList == null) sCachedGroupList = getGroupList();
        if (sCachedGroupList != null) {
            for (int i = 0; i < sCachedGroupList.size(); i++) {
                GroupInfo g = (GroupInfo) sCachedGroupList.get(i);
                if (groupWxid.equals(g.getRoomId())) return g.getName();
            }
        }
    } catch (Exception e) {}
    return "未知群聊";
}

private void updateTargetCountText(TextView tv) {
    if (tv != null) tv.setText("当前已选: " + massSendTargetWxids.size() + " 个目标 (好友/群聊混合)");
}

// ==========================================
// ========== 🎨 UI 样式方法 ==========
// ==========================================

private LinearLayout createCardLayout() {
    LinearLayout layout = new LinearLayout(getTopActivity());
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setPadding(32, 32, 32, 32);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
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

private TextView createSectionTitle(String text) {
    TextView textView = new TextView(getTopActivity());
    textView.setText(text);
    textView.setTextSize(16);
    textView.setTextColor(Color.parseColor("#333333"));
    try { textView.getPaint().setFakeBoldText(true); } catch (Exception e) {}
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
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
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
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
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
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

private TextView createTextView(Context context, String text, int textSize, int paddingBottom) {
    TextView textView = new TextView(context);
    textView.setText(text);
    if (textSize > 0) textView.setTextSize(textSize);
    textView.setPadding(0, 0, 0, paddingBottom);
    return textView;
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

private void styleWeekDayChip(CheckBox cb, boolean selected) {
    GradientDrawable shape = new GradientDrawable();
    shape.setCornerRadius(18);
    if (selected) {
        shape.setColor(Color.parseColor("#DCFCE7"));
        shape.setStroke(2, Color.parseColor("#4ADE80"));
        cb.setTextColor(Color.parseColor("#166534"));
    } else {
        shape.setColor(Color.parseColor("#FFFFFF"));
        shape.setStroke(2, Color.parseColor("#D1D5DB"));
        cb.setTextColor(Color.parseColor("#374151"));
    }
    cb.setBackground(shape);
}

private AlertDialog buildCommonAlertDialog(Context context, String title, View view, String posBtn, DialogInterface.OnClickListener posLsn, String negBtn, DialogInterface.OnClickListener negLsn, String neuBtn, DialogInterface.OnClickListener neuLsn) {
    AlertDialog.Builder builder = new AlertDialog.Builder(context);
    builder.setTitle(title);
    builder.setView(view);
    if (posBtn != null) builder.setPositiveButton(posBtn, posLsn);
    if (negBtn != null) builder.setNegativeButton(negBtn, negLsn);
    if (neuBtn != null) builder.setNeutralButton(neuBtn, neuLsn);
    final AlertDialog dialog = builder.create();
    dialog.setOnShowListener(new DialogInterface.OnShowListener() {
        public void onShow(DialogInterface d) { setupUnifiedDialog(dialog); }
    });
    return dialog;
}

private void setupUnifiedDialog(AlertDialog dialog) {
    GradientDrawable dialogBg = new GradientDrawable();
    dialogBg.setCornerRadius(48);
    dialogBg.setColor(Color.parseColor("#FAFBF9"));
    if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(dialogBg);
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

private void showLoadingDialog(String title, String message, final Runnable dataLoadTask) {
    LinearLayout initialLayout = new LinearLayout(getTopActivity());
    initialLayout.setOrientation(LinearLayout.HORIZONTAL);
    initialLayout.setPadding(50, 50, 50, 50);
    initialLayout.setGravity(Gravity.CENTER_VERTICAL);
    ProgressBar progressBar = new ProgressBar(getTopActivity());
    initialLayout.addView(progressBar);
    TextView loadingText = new TextView(getTopActivity());
    loadingText.setText(message);
    loadingText.setPadding(20, 0, 0, 0);  // ← 修复这里
    initialLayout.addView(loadingText);
    final AlertDialog loadingDialog = buildCommonAlertDialog(getTopActivity(), title, initialLayout, null, null, "❌ 取消", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface d, int w) { d.dismiss(); }
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
                        try { loadingDialog.dismiss(); } catch (Exception e) {}
                    }
                });
            }
        }
    }).start();
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

// ==========================================
// ========== 💾 配置读写方法 ==========
// ==========================================

private void putString(String setName, String itemName, String value) {
    String existingData = getString(setName, "{}");
    try {
        JSONObject json = parseJsonObjectSafe(existingData);
        if (json == null) json = new JSONObject();
        json.put(itemName, value);
        putString(setName, json.toString());
    } catch (Exception e) {
        try {
            JSONObject json = new JSONObject();
            json.put(itemName, value);
            putString(setName, json.toString());
        } catch (Exception ex) {}
    }
}

private String getString(String setName, String itemName, String defaultValue) {
    String data = getString(setName, "{}");
    try {
        JSONObject json = parseJsonObjectSafe(data);
        if (json != null && json.has(itemName)) return json.optString(itemName, defaultValue);
    } catch (Exception e) {}
    return defaultValue;
}

// ==========================================
// ========== 🧩 JSON辅助方法 ==========
// ==========================================

private JSONObject parseJsonObjectSafe(String text) {
    try {
        if (TextUtils.isEmpty(text)) return new JSONObject();
        return new JSONObject(text);
    } catch (Exception e) {
        return new JSONObject();
    }
}

private JSONArray listToJsonArray(List list) {
    JSONArray arr = new JSONArray();
    if (list == null) return arr;
    for (int i = 0; i < list.size(); i++) {
        try { arr.put(list.get(i)); } catch (Exception e) {}
    }
    return arr;
}

private JSONArray setToJsonArray(Set set) {
    JSONArray arr = new JSONArray();
    if (set == null) return arr;
    Iterator it = set.iterator();
    while (it.hasNext()) {
        try { arr.put(it.next()); } catch (Exception e) {}
    }
    return arr;
}

private List<String> jsonObjectKeysToList(JSONObject obj) {
    List<String> list = new ArrayList<String>();
    if (obj == null) return list;
    Iterator it = obj.keys();
    while (it.hasNext()) {
        list.add(String.valueOf(it.next()));
    }
    return list;
}

private void removeJsonKey(JSONObject obj, String key) {
    if (obj == null || key == null) return;
    try {
        if (Build.VERSION.SDK_INT >= 19) {
            obj.remove(key);
            return;
        }
    } catch (Exception e) {}

    try {
        JSONObject newObj = new JSONObject();
        Iterator it = obj.keys();
        while (it.hasNext()) {
            String k = String.valueOf(it.next());
            if (!key.equals(k)) {
                newObj.put(k, obj.opt(k));
            }
        }

        Iterator it2 = obj.keys();
        ArrayList<String> oldKeys = new ArrayList<String>();
        while (it2.hasNext()) oldKeys.add(String.valueOf(it2.next()));
        for (int i = 0; i < oldKeys.size(); i++) {
            try { obj.remove(oldKeys.get(i)); } catch (Exception e) {}
        }

        Iterator it3 = newObj.keys();
        while (it3.hasNext()) {
            String k = String.valueOf(it3.next());
            obj.put(k, newObj.opt(k));
        }
    } catch (Exception e) {}
}
