import org.json.JSONObject;
import java.io.File;
import me.hd.wauxv.plugin.api.callback.PluginCallBack;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

boolean DEBUG_MODE = false; // 设置为true开启调试模式，false关闭调试模式
String KEY_NEWS_TASKS = "daily.news.tasks";
String KEY_SCHEDULER_STARTED = "daily.news.scheduler.started";
String KEY_SCHEDULE_TIME = "daily.news.schedule.time";
String KEY_SCHEDULE_ENABLED = "daily.news.schedule.enabled";
String KEY_GROUP_MODE = "daily.news.group.mode";
String KEY_GROUP_LIST = "daily.news.group.list";
long BOOT_AT_MS = System.currentTimeMillis();
long MAX_LATE_MS = 60L * 1000L; // 增加宽限期到60秒
java.util.Timer scheduleTimer = null;
java.util.TimerTask pendingTask = null;
Object TASK_LOCK = new Object();
AtomicBoolean schedulerStarted = new AtomicBoolean(false);
String cacheDir = "/storage/emulated/0/Android/media/com.tencent.mm/WAuxiliary/Plugin/DailyNews";
String LOG_FILE_PATH = cacheDir + "/daily_news_log.txt";

// 日志记录函数，只有在DEBUG_MODE为true时才写入日志
void logToFile(String message) {
    if (!DEBUG_MODE) return; // 只有在DEBUG_MODE为true时才写入日志
    try {
        File logFile = new File(LOG_FILE_PATH);
        File logDir = logFile.getParentFile();
        if (logDir != null && !logDir.exists()) {
            logDir.mkdirs();
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT);
        sdf.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        String timestamp = sdf.format(new Date());
        BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true));
        buf.append(timestamp).append(" - ").append(message).append("\n");
        buf.close();
    } catch (Exception e) {
        // 如果日志写入失败，则尝试toast提示 (这可能在后台任务中不可见)
        toast("严重: 日志写入失败: " + e.getMessage());
    }
}

// 封装sendText，增加日志和错误处理
void safeSendText(String talker, String text) {
    try {
        sendText(talker, text);
        logToFile("成功发送文本到 " + talker + ": " + text.substring(0, Math.min(50, text.length())) + "...");
    } catch (Exception e) {
        logToFile("发送文本到 " + talker + " 失败: " + e.getMessage());
    }
}

// 封装sendImage，增加日志和错误处理
void safeSendImage(String talker, String imagePath) {
    try {
        sendImage(talker, imagePath);
        logToFile("成功发送图片到 " + talker + ": " + imagePath);
    } catch (Exception e) {
        logToFile("发送图片到 " + talker + " 失败: " + e.getMessage());
    }
}

// 发送成功通知
void sendSuccessNotification(String talker, String messageType) {
    String title = "每日新闻发送成功";
    String text = messageType + "已发送到 " + talker;
    notify(title, text);
    logToFile("发送成功通知: " + title + " - " + text);
}

void sendDailyNews(String talker) {
    sendDailyNewsWithFallback(talker);
}

void sendDailyNewsWithFallback(String talker) {
    String primaryApi = "https://api.52vmy.cn/api/wl/moyu";
    
    // 无论是调试模式还是非调试模式，都先尝试主接口
    callPrimaryApi(primaryApi, talker);
}

void callPrimaryApi(String api, String talker) {
    Map<String, String> headers = new HashMap<String, String>();
    headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
    headers.put("Accept", "image/webp,image/apng,image/*,*/*;q=0.8");
    
    File dir = new File(cacheDir);
    if (!dir.exists()) {
        dir.mkdirs();
    }
    
    String path = cacheDir + "/moyu.png";
    
    logToFile("尝试调用主接口 (摸鱼图片) 到 " + talker);
    download(api, path, headers, new PluginCallBack.DownloadCallback() {
        public void onSuccess(File file) {
            if (file.exists() && file.length() > 0) {
                logToFile("主接口图片下载成功: " + file.getAbsolutePath() + " 到 " + talker);
                safeSendImage(talker, file.getAbsolutePath());
                file.delete(); // 发送后删除
                sendSuccessNotification(talker, "摸鱼图片"); // 成功发送后通知
            } else {
                logToFile("主接口图片下载成功但文件无效到 " + talker + ", 尝试备用接口");
                toast("下载的图片文件无效，尝试备用接口");
                callFallbackApi("https://api.zxki.cn/api/mrzb", talker);
            }
        }
        
        public void onFailure(int errorCode, String errorMsg) {
            logToFile("主接口请求失败到 " + talker + ": " + errorMsg + ", 尝试备用接口");
            toast("下载摸鱼图片失败: " + errorMsg + "，尝试备用接口");
            callFallbackApi("https://api.zxki.cn/api/mrzb", talker);
        }
    });
}

void callFallbackApi(String api, String talker) {
    Map<String, String> headers = new HashMap<String, String>();
    headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
    
    logToFile("尝试调用备用接口 (每日早报) 到 " + talker);
    get(api, headers, new PluginCallBack.HttpCallback() {
        public void onSuccess(int respCode, String respContent) {
            try {
                JSONObject json = new JSONObject(respContent);
                int code = json.getInt("code");
                
                if (code == 200) {
                    logToFile("备用接口返回成功到 " + talker + ": " + respContent);
                    
                    JSONObject data = json.getJSONObject("data");
                    String date = data.getString("date");
                    String weiyu = data.getString("weiyu");
                    String headImage = data.getString("head_image");
                    
                    headImage = headImage.replace("\\/", "/");
                    
                    StringBuilder message = new StringBuilder();
                    message.append("📰 每日早报 📰\n\n");
                    message.append("📅 ").append(date).append("\n\n");
                    message.append("📌 今日新闻：\n");
                    org.json.JSONArray newsArray = data.getJSONArray("news");
                    for (int i = 0; i < newsArray.length(); i++) {
                        message.append(newsArray.getString(i)).append("\n\n");
                    }
                    message.append("💭 每日微语：\n");
                    message.append(weiyu);
                    
                    File dir = new File(cacheDir);
                    if (!dir.exists()) {
                        dir.mkdirs();
                    }
                    
                    String path = cacheDir + "/daily_news.png";
                    logToFile("备用接口开始下载图片: " + headImage + " 到 " + talker);
                    download(headImage, path, null, new PluginCallBack.DownloadCallback() {
                        public void onSuccess(File file) {
                            if (file.exists() && file.length() > 0) {
                                logToFile("备用接口图片下载成功: " + file.getAbsolutePath() + " 到 " + talker);
                                safeSendImage(talker, file.getAbsolutePath());
                                safeSendText(talker, message.toString());
                                file.delete(); // 发送后删除
                                sendSuccessNotification(talker, "每日早报"); // 成功发送后通知
                            } else {
                                logToFile("备用接口图片下载成功但文件无效到 " + talker + "，仅发送文本");
                                toast("下载的新闻图片无效，仅发送文本到 " + talker);
                                safeSendText(talker, message.toString());
                            }
                        }
                        
                        public void onFailure(int errorCode, String errorMsg) {
                            logToFile("备用接口图片下载失败到 " + talker + ": " + errorMsg + "，仅发送文本");
                            toast("下载新闻图片失败: " + errorMsg + "，仅发送文本到 " + talker);
                            safeSendText(talker, message.toString());
                        }
                    });
                } else {
                    logToFile("备用接口返回错误码到 " + talker + ": " + code + ", 内容: " + respContent);
                    toast("获取新闻数据失败，错误码: " + code);
                }
            } catch (Exception e) {
                logToFile("备用接口解析异常到 " + talker + ": " + e.getMessage() + ", 响应: " + respContent);
                toast("解析新闻数据失败: " + e.getMessage());
            }
        }
        
        public void onFailure(int respCode, String errorMsg) {
            logToFile("备用接口请求失败到 " + talker + ": " + errorMsg);
            toast("请求新闻API失败: " + errorMsg);
        }
    });
}

String taskLine(String id, String targetId, String targetName, long timeMs, boolean repeat) {
    return id + "|" + targetId + "|" + targetName + "|" + timeMs + "|" + (repeat ? "1" : "0");
}

Map<String, Object> parseTask(String line) {
    try {
        String[] a = line.split("\\|", -1);
        if (a.length < 5) return null;
        Map<String, Object> m = new HashMap<String, Object>();
        m.put("id", a[0]);
        m.put("targetId", a[1]);
        m.put("targetName", a[2]);
        m.put("time", Long.valueOf(a[3]));
        m.put("repeat", "1".equals(a[4]) ? Boolean.TRUE : Boolean.FALSE);
        return m;
    } catch (Throwable e) {
        logToFile("解析任务行失败: " + e.getMessage() + ", 行内容: " + line);
        return null;
    }
}

List<String> readTaskLines() {
    List<String> list = new ArrayList<String>();
    String raw = getString(KEY_NEWS_TASKS, "");
    if (raw == null || raw.trim().length() == 0) return list;
    String[] lines = raw.split("\n");
    for (int i = 0; i < lines.length; i++) {
        String ln = (lines[i] == null ? "" : lines[i].trim());
        if (ln.length() > 0) list.add(ln);
    }
    return list;
}

void writeTaskLines(List<String> lines) {
    if (lines == null || lines.size() == 0) {
        putString(KEY_NEWS_TASKS, "");
        return;
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < lines.size(); i++) {
        if (i > 0) sb.append("\n");
        sb.append(String.valueOf(lines.get(i)));
    }
    putString(KEY_NEWS_TASKS, sb.toString());
}

List<String> readGroupList() {
    List<String> list = new ArrayList<String>();
    String raw = getString(KEY_GROUP_LIST, "");
    if (raw == null || raw.trim().length() == 0) return list;
    String[] groups = raw.split(";;");
    for (int i = 0; i < groups.length; i++) {
        String group = (groups[i] == null ? "" : groups[i].trim());
        if (group.length() > 0) list.add(group);
    }
    return list;
}

void writeGroupList(List<String> list) {
    if (list == null || list.size() == 0) {
        putString(KEY_GROUP_LIST, "");
        return;
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < list.size(); i++) {
        if (i > 0) sb.append(";;");
        sb.append(String.valueOf(list.get(i)));
    }
    putString(KEY_GROUP_LIST, sb.toString());
}

String getGroupMode() {
    return getString(KEY_GROUP_MODE, "whitelist");
}

void setGroupMode(String mode) {
    putString(KEY_GROUP_MODE, mode);
}

long calcNextDaySameTime(long ms) {
    return ms + 24L * 60L * 60L * 1000L;
}

String fmtTime(long ms) {
    try {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT);
        sdf.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        return sdf.format(new Date(ms));
    } catch (Throwable e) {
        return String.valueOf(ms);
    }
}

String fmtTimeOnly(long ms) {
    try {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.ROOT);
        sdf.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        return sdf.format(new Date(ms));
    } catch (Throwable e) {
        return String.valueOf(ms);
    }
}

long parseTime(String s) {
    try {
        String[] pats = new String[]{"yyyy-MM-dd HH:mm", "yyyy-M-d HH:mm", "HH:mm"};
        for (int i = 0; i < pats.length; i++) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(pats[i], Locale.ROOT);
                sdf.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
                Date date = sdf.parse(s);
                
                if (pats[i].equals("HH:mm")) {
                    java.util.Calendar cal = java.util.Calendar.getInstance();
                    java.util.Calendar today = java.util.Calendar.getInstance();
                    java.util.Calendar tempCal = java.util.Calendar.getInstance();
                    tempCal.setTime(date);
                    today.set(java.util.Calendar.HOUR_OF_DAY, tempCal.get(java.util.Calendar.HOUR_OF_DAY));
                    today.set(java.util.Calendar.MINUTE, tempCal.get(java.util.Calendar.MINUTE));
                    today.set(java.util.Calendar.SECOND, 0);
                    today.set(java.util.Calendar.MILLISECOND, 0);
                    
                    if (today.getTimeInMillis() <= System.currentTimeMillis()) {
                        today.add(java.util.Calendar.DAY_OF_MONTH, 1);
                    }
                    
                    return today.getTimeInMillis();
                }
                
                return date.getTime();
            } catch (Exception __ignored) {
            }
        }
        return -1L;
    } catch (Throwable e) {
        logToFile("解析时间失败: " + e.getMessage() + ", 输入: " + s);
        return -1L;
    }
}

void startSchedulerIfNeed() {
    if (schedulerStarted.compareAndSet(false, true)) {
        scheduleTimer = new java.util.Timer("news-schedule-dispatch", true);
        rescheduleDispatcher();
        logToFile("调度器已启动");
    }
}

void stopScheduler() {
    try {
        if (pendingTask != null) pendingTask.cancel();
    } catch (Exception __ignored) {
    }
    pendingTask = null;
    try {
        if (scheduleTimer != null) scheduleTimer.cancel();
    } catch (Exception __ignored) {
    }
    scheduleTimer = null;
    schedulerStarted.set(false);
    logToFile("调度器已停止");
}

void cancelPending() {
    try {
        if (pendingTask != null) pendingTask.cancel();
    } catch (Exception __ignored) {
    }
    pendingTask = null;
}

long findMinDue() {
    long min = Long.MAX_VALUE;
    long now = System.currentTimeMillis();
    boolean hasOverdue = false;
    try {
        List<String> lines = readTaskLines();
        for (int i = 0; i < lines.size(); i++) {
            String ln = String.valueOf(lines.get(i));
            Map<String, Object> t = parseTask(ln);
            if (t == null) continue;
            long time = ((Long) t.get("time")).longValue();
            if (time > 0) {
                boolean overdue = (now - time) > MAX_LATE_MS;
                if (!overdue && time < min) min = time;
                if (overdue) hasOverdue = true;
            }
        }
    } catch (Exception __ignored) {
        logToFile("查找最小到期时间失败: " + __ignored.getMessage());
    }
    if (min == Long.MAX_VALUE && hasOverdue) {
        return now + 200L;
    }
    return min;
}

void rescheduleDispatcher() {
    try {
        if (scheduleTimer == null) scheduleTimer = new java.util.Timer("news-schedule-dispatch", true);
        cancelPending();
        long minDue = findMinDue();
        if (minDue == Long.MAX_VALUE) {
            logToFile("没有待处理的定时任务");
            return;
        }
        long now = System.currentTimeMillis();
        long delay = minDue - now;
        if (delay < 0) delay = 0;
        
        logToFile("下次调度将在 " + fmtTime(minDue) + " (延迟 " + delay + "ms) 运行");
        
        pendingTask = new java.util.TimerTask() {
            public void run() {
                logToFile("TimerTask.run() 方法已开始执行"); 
                try {
                    dispatchDueTasks();
                } catch (Exception __ignored) {
                    logToFile("执行调度任务失败: " + __ignored.getMessage());
                } finally {
                    try {
                        rescheduleDispatcher();
                    } catch (Exception __ignored) {
                        logToFile("重新调度失败: " + __ignored.getMessage());
                    }
                }
            }
        };
        scheduleTimer.schedule(pendingTask, delay);
    } catch (Throwable e) {
        logToFile("调度器重新调度失败: " + e.getMessage());
    }
}

void sendDailyNewsToGroups(List<String> groupList) {
    if (groupList == null || groupList.size() == 0) {
        logToFile("群列表为空，无法发送定时新闻");
        return;
    }
    
    String mode = getGroupMode();
    List<String> targetGroups = new ArrayList<String>();
    
    if ("whitelist".equals(mode)) {
        targetGroups.addAll(groupList);
    } else if ("blacklist".equals(mode)) {
        // 在黑名单模式下，我们假设groupList是黑名单，
        // 实际发送时需要获取所有群聊并排除黑名单中的群。
        // 由于插件API限制，这里简化处理，仅发送给白名单中的群。
        // 建议用户在黑名单模式下，手动维护一个包含所有目标群的白名单，然后从其中移除黑名单群。
        targetGroups.addAll(groupList); 
        logToFile("黑名单模式下，定时任务仍发送给群列表中存在的群。请确保群列表为白名单以正确使用。");
    }
    
    if (targetGroups.isEmpty()) {
        logToFile("根据群模式，没有目标群可发送定时新闻");
        return;
    }

    logToFile("开始向 " + targetGroups.size() + " 个群发送定时新闻");
    for (String group : targetGroups) {
        try {
            logToFile("向群 " + group + " 发送每日新闻 (通过sendDailyNewsWithFallback)");
            // 定时发送调用sendDailyNewsWithFallback，遵循先主后备逻辑
            sendDailyNewsWithFallback(group); 
        } catch (Exception __ignored) {
            logToFile("向群 " + group + " 发送定时新闻失败: " + __ignored.getMessage());
        }
    }
}

void dispatchDueTasks() {
    logToFile("dispatchDueTasks() 方法已开始执行"); 
    List<String> lines;
    synchronized (TASK_LOCK) {
        lines = readTaskLines();
    }
    if (lines.size() == 0) {
        logToFile("没有需要分发的任务");
        return;
    }
    long now = System.currentTimeMillis();
    boolean changed = false;
    List<String> out = new ArrayList<String>();
    for (int i = 0; i < lines.size(); i++) {
        String ln = String.valueOf(lines.get(i));
        Map<String, Object> t = parseTask(ln);
        if (t == null) {
            logToFile("跳过无效任务行: " + ln);
            continue;
        }
        String id = String.valueOf(t.get("id"));
        String targetId = (String) t.get("targetId");
        String targetName = (String) t.get("targetName");
        long time = ((Long) t.get("time")).longValue();
        boolean repeat = ((Boolean) t.get("repeat")).booleanValue();
        
        if (now < time) {
            out.add(ln);
            continue;
        }
        
        logToFile("处理到期任务: ID=" + id + ", 目标=" + targetName + ", 计划时间=" + fmtTime(time));
        
        boolean tooLate = (now - time) > MAX_LATE_MS;
        if (tooLate) {
            logToFile("任务已过期 (当前时间: " + fmtTime(now) + ", 计划时间: " + fmtTime(time) + "), 跳过: ID=" + id);
            if (repeat) {
                long next = calcNextDaySameTime(time);
                while ((now - next) > MAX_LATE_MS) {
                    next = calcNextDaySameTime(next);
                    if (next <= 0) {
                        break;
                    }
                }
                out.add(taskLine(id, targetId, targetName, next, true));
                changed = true;
                logToFile("过期重复任务 " + id + " 已重新安排到 " + fmtTime(next));
            } else {
                changed = true;
            }
            continue;
        }
        
        try {
            if ("GROUPS".equals(targetId)) {
                logToFile("执行群组定时发送任务");
                List<String> groupList = readGroupList();
                sendDailyNewsToGroups(groupList);
            } else {
                logToFile("执行单聊/非群组定时发送任务到 " + targetId);
                sendDailyNews(targetId);
            }
        } catch (Exception __ignored) {
            logToFile("执行任务 " + id + " 失败: " + __ignored.getMessage());
        }
        
        if (repeat) {
            long next = calcNextDaySameTime(time);
            out.add(taskLine(id, targetId, targetName, next, true));
            changed = true;
            logToFile("重复任务 " + id + " 已重新安排到 " + fmtTime(next));
        } else {
            changed = true;
            logToFile("一次性任务 " + id + " 已完成");
        }
    }
    synchronized (TASK_LOCK) {
        if (changed) {
            writeTaskLines(out);
            logToFile("任务列表已更新");
        }
    }
    logToFile("dispatchDueTasks() 方法执行完毕"); 
}

void setScheduleTime(String talker, String timeStr) {
    long timeMs = parseTime(timeStr);
    if (timeMs <= 0) {
        safeSendText(talker, "时间格式不正确，请使用24小时制，例如：08:00 或 20:30");
        return;
    }
    
    putString(KEY_SCHEDULE_TIME, timeStr);
    putBoolean(KEY_SCHEDULE_ENABLED, true);
    
    updateScheduleTask(timeMs);
    
    safeSendText(talker, "已设置每日新闻定时发送时间为：" + timeStr);
    logToFile("定时发送时间已设置为: " + timeStr);
}

void getScheduleTime(String talker) {
    String timeStr = getString(KEY_SCHEDULE_TIME, "08:00");
    boolean enabled = getBoolean(KEY_SCHEDULE_ENABLED, false);
    
    if (enabled) {
        safeSendText(talker, "当前每日新闻定时发送时间为：" + timeStr + "（已启用）");
    } else {
        safeSendText(talker, "当前每日新闻定时发送时间为：" + timeStr + "（已禁用）");
    }
    logToFile("查询定时发送时间: " + timeStr + " (启用: " + enabled + ")");
}

void enableSchedule(String talker) {
    putBoolean(KEY_SCHEDULE_ENABLED, true);
    
    String timeStr = getString(KEY_SCHEDULE_TIME, "08:00");
    long timeMs = parseTime(timeStr);
    updateScheduleTask(timeMs);
    
    safeSendText(talker, "已启用每日新闻定时发送功能");
    logToFile("已启用定时发送功能");
}

void disableSchedule(String talker) {
    putBoolean(KEY_SCHEDULE_ENABLED, false);
    
    removeScheduleTask();
    
    safeSendText(talker, "已禁用每日新闻定时发送功能");
    logToFile("已禁用定时发送功能");
}

void updateScheduleTask(long timeMs) {
    removeScheduleTask();
    
    String taskId = "DAILY_NEWS_SCHEDULE";
    String taskLine = taskLine(taskId, "GROUPS", "每日新闻群列表", timeMs, true);
    
    List<String> tasks = readTaskLines();
    tasks.add(taskLine);
    writeTaskLines(tasks);
    
    if (schedulerStarted.get()) {
        rescheduleDispatcher();
    }
    logToFile("定时任务已更新/创建，下次发送时间: " + fmtTime(timeMs));
}

void removeScheduleTask() {
    List<String> tasks = readTaskLines();
    List<String> newTasks = new ArrayList<String>();
    
    boolean removed = false;
    for (int i = 0; i < tasks.size(); i++) {
        String ln = String.valueOf(tasks.get(i));
        Map<String, Object> t = parseTask(ln);
        if (t == null) {
            newTasks.add(ln); // Keep invalid tasks for now, or remove them explicitly
            continue;
        }
        
        String id = String.valueOf(t.get("id"));
        if (!"DAILY_NEWS_SCHEDULE".equals(id)) {
            newTasks.add(ln);
        } else {
            removed = true;
        }
    }
    
    writeTaskLines(newTasks);
    
    if (schedulerStarted.get()) {
        rescheduleDispatcher();
    }
    if (removed) {
        logToFile("已移除定时任务");
    }
}

void initDefaultTask() {
    if (!getBoolean(KEY_SCHEDULE_ENABLED, false)) {
        logToFile("定时发送功能未启用，不初始化默认任务");
        return;
    }
    
    List<String> tasks = readTaskLines();
    boolean hasScheduleTask = false;
    
    for (int i = 0; i < tasks.size(); i++) {
        String ln = String.valueOf(tasks.get(i));
        Map<String, Object> t = parseTask(ln);
        if (t == null) continue;
        
        String id = String.valueOf(t.get("id"));
        if ("DAILY_NEWS_SCHEDULE".equals(id)) {
            hasScheduleTask = true;
            break;
        }
    }
    
    if (!hasScheduleTask) {
        String timeStr = getString(KEY_SCHEDULE_TIME, "08:00");
        long timeMs = parseTime(timeStr);
        if (timeMs <= 0) {
             logToFile("默认定时时间解析失败，不创建默认任务");
             return;
        }
        updateScheduleTask(timeMs);
        logToFile("已初始化默认定时任务到 " + timeStr);
    } else {
        logToFile("已存在定时任务，无需初始化");
    }
}

void addGroup(String talker, String groupId) {
    List<String> groups = readGroupList();
    if (groups.contains(groupId)) {
        safeSendText(talker, "该群已在列表中");
        logToFile("尝试添加群 " + groupId + "，但已存在");
        return;
    }
    
    groups.add(groupId);
    writeGroupList(groups);
    safeSendText(talker, "已添加群到列表: " + groupId);
    logToFile("已添加群 " + groupId + " 到列表");
}

void removeGroup(String talker, String groupId) {
    List<String> groups = readGroupList();
    if (!groups.contains(groupId)) {
        safeSendText(talker, "该群不在列表中");
        logToFile("尝试移除群 " + groupId + "，但不在列表中");
        return;
    }
    
    groups.remove(groupId);
    writeGroupList(groups);
    safeSendText(talker, "已从列表中移除群: " + groupId);
    logToFile("已从列表中移除群 " + groupId);
}

void showGroupList(String talker) {
    List<String> groups = readGroupList();
    String mode = getGroupMode();
    
    StringBuilder sb = new StringBuilder();
    sb.append("当前群列表模式: ").append(mode).append("\n");
    sb.append("群列表:\n");
    
    if (groups.size() == 0) {
        sb.append("(空)");
    } else {
        for (int i = 0; i < groups.size(); i++) {
            sb.append(i + 1).append(". ").append(groups.get(i)).append("\n");
        }
    }
    
    safeSendText(talker, sb.toString());
    logToFile("显示群列表 (模式: " + mode + ", 数量: " + groups.size() + ")");
}

void clearGroupList(String talker) {
    writeGroupList(new ArrayList<String>());
    safeSendText(talker, "已清空群列表");
    logToFile("已清空群列表");
}

void setGroupMode(String talker, String mode) {
    if (!"whitelist".equals(mode) && !"blacklist".equals(mode)) {
        safeSendText(talker, "模式必须是 'whitelist' 或 'blacklist'");
        logToFile("设置群模式失败，无效模式: " + mode);
        return;
    }
    
    setGroupMode(mode);
    safeSendText(talker, "已设置群列表模式为: " + mode);
    logToFile("已设置群列表模式为: " + mode);
}

void addCurrentGroup(String talker) {
    if (!talker.endsWith("@chatroom")) {
        safeSendText(talker, "当前不在群聊中");
        logToFile("尝试添加当前群失败，当前不在群聊");
        return;
    }
    
    List<String> groups = readGroupList();
    if (groups.contains(talker)) {
        safeSendText(talker, "当前群已在列表中");
        logToFile("尝试添加当前群 " + talker + "，但已存在");
        return;
    }
    
    groups.add(talker);
    writeGroupList(groups);
    safeSendText(talker, "已添加当前群到列表: " + talker);
    logToFile("已添加当前群 " + talker + " 到列表");
}

void removeCurrentGroup(String talker) {
    if (!talker.endsWith("@chatroom")) {
        safeSendText(talker, "当前不在群聊中");
        logToFile("尝试移除当前群失败，当前不在群聊");
        return;
    }
    
    List<String> groups = readGroupList();
    if (!groups.contains(talker)) {
        safeSendText(talker, "当前群不在列表中");
        logToFile("尝试移除当前群 " + talker + "，但不在列表中");
        return;
    }
    
    groups.remove(talker);
    writeGroupList(groups);
    safeSendText(talker, "已从列表中移除当前群: " + talker);
    logToFile("已从列表中移除当前群 " + talker);
}

void onHandleMsg(Object msgInfoBean) {
    if (msgInfoBean.isText()) {
        String content = msgInfoBean.getContent();
        String talker = msgInfoBean.getTalker();
        
        if (content.startsWith("/每日新闻 设置定时时间 ")) {
            String timeStr = content.substring("/每日新闻 设置定时时间 ".length()).trim();
            setScheduleTime(talker, timeStr);
        } else if (content.equals("/每日新闻 查看定时时间")) {
            getScheduleTime(talker);
        } else if (content.equals("/每日新闻 启用定时发送")) {
            enableSchedule(talker);
        } else if (content.equals("/每日新闻 禁用定时发送")) {
            disableSchedule(talker);
        } else if (content.startsWith("/每日新闻 添加群 ")) {
            String groupId = content.substring("/每日新闻 添加群 ".length()).trim();
            addGroup(talker, groupId);
        } else if (content.startsWith("/每日新闻 删除群 ")) {
            String groupId = content.substring("/每日新闻 删除群 ".length()).trim();
            removeGroup(talker, groupId);
        } else if (content.equals("/每日新闻 添加当前群")) {
            addCurrentGroup(talker);
        } else if (content.equals("/每日新闻 删除当前群")) {
            removeCurrentGroup(talker);
        } else if (content.equals("/每日新闻 查看群列表")) {
            showGroupList(talker);
        } else if (content.equals("/每日新闻 清空群列表")) {
            clearGroupList(talker);
        } else if (content.startsWith("/每日新闻 设置群模式 ")) {
            String mode = content.substring("/每日新闻 设置群模式 ".length()).trim();
            setGroupMode(talker, mode);
        } else if (content.equals("/每日新闻")) {
            sendDailyNews(talker);
        }
    }
}

void onLoad() {
    File dir = new File(cacheDir);
    if (!dir.exists()) {
        dir.mkdirs();
    }
    initDefaultTask();
    startSchedulerIfNeed();
    logToFile("插件已加载");
}

void onUnload() {
    stopScheduler();
    logToFile("插件已卸载");
}