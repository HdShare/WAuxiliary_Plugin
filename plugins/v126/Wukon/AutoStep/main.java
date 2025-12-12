import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

long maxStep = 24305;
long currentStep = 0;
int currentDay = 0;
ScheduledExecutorService scheduledExecutor = null;
boolean isTimerRunning = false;
boolean timeStepEnabled = false;
boolean messageStepEnabled = false;
int minTimeStep = 6;
int maxTimeStep = 12;
long pendingStepUpload = 0;
long lastExecutionTime = 0;
String logDirPath = "/storage/emulated/0/Android/media/com.tencent.mm/WAuxiliary/Plugin/AutoStep/";
File logDir = new File(logDirPath);
SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
long minGuaranteedStep = 0;
ScheduledExecutorService guaranteedStepExecutor = null;
boolean isExecutingTask = false;
boolean isTestMode = false;
long MAX_LOG_FILE_SIZE = 1 * 1024 * 1024;
boolean logOutputEnabled = false;
long maxMessageStep = 10000;
long maxTimeStepCalculated = 0;

void onLoad() {
    synchronized(this) {
        currentStep = getLong("currentStep", 0);
        currentDay = getInt("currentDay", 0);
        timeStepEnabled = getBoolean("timeStepEnabled", false);
        messageStepEnabled = getBoolean("messageStepEnabled", false);
        maxStep = getLong("maxStep", 24305);
        minTimeStep = getInt("minTimeStep", 6);
        maxTimeStep = getInt("maxTimeStep", 18);
        maxMessageStep = getLong("maxMessageStep", 10000);
        pendingStepUpload = getLong("pendingStepUpload", 0);
        lastExecutionTime = getLong("lastExecutionTime", 0);
        minGuaranteedStep = getLong("minGuaranteedStep", 0);
    }
    
    maxTimeStepCalculated = maxTimeStep * calculateActiveMinutes();
    
    LocalDateTime now = LocalDateTime.now();
    synchronized(this) {
        if (now.getDayOfYear() != currentDay) {
            resetDay(now);
        }
    }
    
    if (pendingStepUpload > 0) {
        uploadPendingSteps();
    }
    
    if (timeStepEnabled) {
        startTimeStepTimer();
    }
    
    if (minGuaranteedStep > 0) {
        startGuaranteedStepTimer();
    }
    
    logToFile("插件加载完成");
}

void onUnLoad() {
    stopTimeStepTimer();
    stopGuaranteedStepTimer();
}

void resetDay(LocalDateTime now) {
    synchronized(this) {
        currentStep = 0;
        currentDay = now.getDayOfYear();
        putInt("currentDay", currentDay);
        putLong("currentStep", currentStep);
        pendingStepUpload = 0;
        putLong("pendingStepUpload", 0);
    }
}

void onHandleMsg(Object msgInfoBean) {
    if (!msgInfoBean.isSend()) return;
    
    if (msgInfoBean.isText()) {
        String content = msgInfoBean.getContent().trim();
        String talker = msgInfoBean.getTalker();
        
        switch (content) {
            case "/时间步数开":
                enableTimeStep(true);
                return;
            case "/时间步数关":
                enableTimeStep(false);
                return;
            case "/消息步数开":
                enableMessageStep(true);
                return;
            case "/消息步数关":
                enableMessageStep(false);
                return;
            case "/步数状态":
                showStepStatus(talker);
                return;
            case "/步数状态all":
                showStepStatusAll(talker);
                return;
        }
        
        if (content.startsWith("/改步数 ")) {
            String[] parts = content.split(" ", 2);
            if (parts.length == 2) {
                try {
                    long step = Long.parseLong(parts[1].trim());
                    synchronized(this) {
                        if (step >= 0 && step <= maxStep) {
                            currentStep = step;
                            putLong("currentStep", currentStep);
                            safeUploadDeviceStep(currentStep);
                            sendText(talker, "步数已修改为: " + step);
                            logToFile("手动修改步数: " + step);
                        } else {
                            sendText(talker, "步数必须在0-" + maxStep + "之间");
                        }
                    }
                } catch (NumberFormatException e) {
                    sendText(talker, "请输入有效的数字");
                }
            } else {
                sendText(talker, "命令格式: /改步数 数字");
            }
            return;
        }
        
        if (content.startsWith("/最大消息步数 ")) {
            String[] parts = content.split(" ", 2);
            if (parts.length == 2) {
                try {
                    long step = Long.parseLong(parts[1].trim());
                    synchronized(this) {
                        if (step >= 0) {
                            maxMessageStep = step;
                            putLong("maxMessageStep", maxMessageStep);
                            sendText(talker, "最大消息步数已修改为: " + step);
                            logToFile("修改最大消息步数: " + step);
                        } else {
                            sendText(talker, "最大消息步数必须大于等于0");
                        }
                    }
                } catch (NumberFormatException e) {
                    sendText(talker, "请输入有效的数字");
                }
            } else {
                sendText(talker, "命令格式: /最大消息步数 数字");
            }
            return;
        }
        
        if (content.startsWith("/分钟步数范围 ")) {
            String[] parts = content.split(" ", 2);
            if (parts.length == 2) {
                String[] rangeParts = parts[1].trim().split("-");
                if (rangeParts.length == 2) {
                    try {
                        int min = Integer.parseInt(rangeParts[0].trim());
                        int max = Integer.parseInt(rangeParts[1].trim());
                        synchronized(this) {
                            if (min >= 0 && max > min) {
                                minTimeStep = min;
                                maxTimeStep = max;
                                putInt("minTimeStep", minTimeStep);
                                putInt("maxTimeStep", maxTimeStep);
                                
                                maxTimeStepCalculated = maxTimeStep * calculateActiveMinutes();
                                
                                sendText(talker, "每分钟步数范围已修改为: " + min + "-" + max);
                                sendText(talker, "时间步数范围: " + (min * calculateActiveMinutes()) + "-" + (max * calculateActiveMinutes()));
                                
                                logToFile("修改每分钟步数范围: " + min + "-" + max);
                            } else {
                                sendText(talker, "范围无效，最小值必须大于等于0且最大值必须大于最小值");
                            }
                        }
                    } catch (NumberFormatException e) {
                        sendText(talker, "请输入有效的数字范围");
                    }
                } else {
                    sendText(talker, "命令格式: /分钟步数范围 最小值-最大值");
                }
            } else {
                sendText(talker, "命令格式: /分钟步数范围 最小值-最大值");
            }
            return;
        }
        
        if (content.startsWith("/保底步数 ")) {
            String[] parts = content.split(" ", 2);
            if (parts.length == 2) {
                try {
                    long step = Long.parseLong(parts[1].trim());
                    synchronized(this) {
                        if (step >= 0) {
                            if (step > maxStep) {
                                sendText(talker, "保底步数不能大于最大步数 " + maxStep);
                                return;
                            }
                            minGuaranteedStep = step;
                            putLong("minGuaranteedStep", minGuaranteedStep);
                            sendText(talker, "保底步数已修改为: " + step);
                            logToFile("修改保底步数: " + step);
                        } else {
                            sendText(talker, "保底步数必须大于等于0");
                        }
                    }
                    if (minGuaranteedStep > 0) {
                        startGuaranteedStepTimer();
                        toast("保底步数功能已开启");
                        logToFile("保底步数功能已开启");
                    } else {
                        stopGuaranteedStepTimer();
                        toast("保底步数功能已关闭");
                        logToFile("保底步数功能已关闭");
                    }
                } catch (NumberFormatException e) {
                    sendText(talker, "请输入有效的数字");
                }
            } else {
                sendText(talker, "命令格式: /保底步数 数字");
            }
            return;
        }
    }
    
    if (messageStepEnabled) {
        updateStepOnMessage();
    }
}

void updateStepOnMessage() {
    if (isRestrictedTime()) {
        return;
    }
    
    synchronized(this) {
        if (currentDay == 0) {
            currentStep = getLong("currentStep", 0);
            currentDay = getInt("currentDay", 0);
        }
    }
    
    LocalDateTime now = LocalDateTime.now();
    synchronized(this) {
        if (now.getDayOfYear() != currentDay) {
            resetDay(now);
        }
    }
    
    Random random = new Random();
    int step = 50 + random.nextInt(100);
    
    synchronized(this) {
        long inc = calculateStepIncrease(currentStep, step);
        currentStep += inc;
        currentStep = Math.min(currentStep, maxStep);
        putLong("currentStep", currentStep);
    }
    
    safeUploadDeviceStep(currentStep);
    logToFile("消息步数: +" + step + " -> " + currentStep);
}

long calculateStepIncrease(long currentStep, int baseStep) {
    if (currentStep < 4000) {
        return baseStep * 3;
    } else if (currentStep < 8000) {
        return baseStep * 2;
    } else if (currentStep < 16000) {
        return baseStep;
    } else {
        return (long) (baseStep * 0.5);
    }
}

void startTimeStepTimer() {
    synchronized(this) {
        if (isTimerRunning) {
            return;
        }
        stopTimeStepTimer();
        scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
        isTimerRunning = true;
    }
    
    scheduledExecutor.scheduleAtFixedRate(new Runnable() {
        public void run() {
            if (isExecutingTask) {
                return;
            }
            isExecutingTask = true;
            try {
                if (isRestrictedTime()) {
                    return;
                }

                LocalDateTime now = LocalDateTime.now();
                synchronized (this) {
                    if (now.getDayOfYear() != currentDay) {
                        resetDay(now);
                    }
                }

                long currentTime = System.currentTimeMillis();

                boolean hasMissedTasks = checkAndExecuteMissedTasks(currentTime);

                if (!hasMissedTasks) {
                    Random random = new Random();
                    int step = minTimeStep + random.nextInt(maxTimeStep - minTimeStep + 1);
                    synchronized (this) {
                        currentStep += step;
                        if (currentStep > maxStep) {
                            currentStep = maxStep;
                        }
                        putLong("currentStep", currentStep);
                    }
                    safeUploadDeviceStep(currentStep);
                    logToFile("时间步数: +" + step + " -> " + currentStep);
                }

                synchronized (this) {
                    lastExecutionTime = currentTime;
                    putLong("lastExecutionTime", lastExecutionTime);
                }
            } catch (Exception e) {
                logToFile("定时任务异常: " + e.getMessage());
            } finally {
                isExecutingTask = false;
            }
        }
    }, 0, 1, TimeUnit.MINUTES);
}

void stopTimeStepTimer() {
    synchronized(this) {
        if (scheduledExecutor != null) {
            scheduledExecutor.shutdownNow();
            scheduledExecutor = null;
        }
        isTimerRunning = false;
    }
    logToFile("时间步数定时器停止");
}

void startGuaranteedStepTimer() {
    stopGuaranteedStepTimer();
    
    guaranteedStepExecutor = Executors.newSingleThreadScheduledExecutor();
    
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime targetTime = now.withHour(22).withMinute(50).withSecond(0).withNano(0);
    
    if (now.isAfter(targetTime)) {
        targetTime = targetTime.plusDays(1);
    }
    
    long initialDelay = java.time.Duration.between(now, targetTime).toMillis();
    long period = 24 * 60 * 60 * 1000L;
    
    guaranteedStepExecutor.scheduleAtFixedRate(new Runnable() {
        public void run() {
            try {
                executeGuaranteedStepCheck();
            } catch (Exception e) {
                logToFile("保底步数检查异常: " + e.getMessage());
            }
        }
    }, initialDelay, period, TimeUnit.MILLISECONDS);
    
    logToFile("保底步数定时器启动");
}

void stopGuaranteedStepTimer() {
    if (guaranteedStepExecutor != null) {
        guaranteedStepExecutor.shutdownNow();
        guaranteedStepExecutor = null;
    }
    logToFile("保底步数定时器停止");
}

void executeGuaranteedStepCheck() {
    LocalDateTime now = LocalDateTime.now();
    synchronized(this) {
        if (now.getDayOfYear() != currentDay) {
            resetDay(now);
        }
        
        Random random = new Random();
        long guaranteedStep = minGuaranteedStep + random.nextInt(1000);
        if (guaranteedStep > maxStep) {
            guaranteedStep = maxStep;
        }
        
        if (currentStep < guaranteedStep) {
            currentStep = guaranteedStep;
            putLong("currentStep", currentStep);
            safeUploadDeviceStep(currentStep);
            logToFile("保底步数生效: " + currentStep);
        }
    }
}

boolean checkAndExecuteMissedTasks(long currentTime) {
    long last;
    synchronized (this) {
        last = lastExecutionTime;
    }

    if (last == 0) {
        return false;
    }

    long timeDiff = currentTime - last;
    if (timeDiff <= 61 * 1000) {
        return false;
    }

    int missedMinutes = (int) (timeDiff / (60 * 1000));
    int maxMissedMinutes = 12 * 60;
    missedMinutes = Math.min(missedMinutes, maxMissedMinutes);

    Random random = new Random();
    int totalAddedSteps = 0;

    for (int i = 0; i < missedMinutes; i++) {
        if (isRestrictedTime()) {
            break;
        }
        int step = minTimeStep + random.nextInt(maxTimeStep - minTimeStep + 1);
        synchronized (this) {
            currentStep += step;
            totalAddedSteps += step;
            if (currentStep > maxStep) {
                currentStep = maxStep;
                break;
            }
        }
    }

    if (totalAddedSteps > 0) {
        synchronized (this) {
            putLong("currentStep", currentStep);
        }
        safeUploadDeviceStep(currentStep);
        logToFile("补充缺失: " + missedMinutes + "分钟 +" + totalAddedSteps + " -> " + currentStep);
        return true;
    }
    return false;
}

void enableTimeStep(boolean enable) {
    synchronized(this) {
        timeStepEnabled = enable;
        putBoolean("timeStepEnabled", timeStepEnabled);
    }
    
    if (enable) {
        startTimeStepTimer();
        toast("时间自动增加步数功能已开启");
        logToFile("时间步数功能已开启");
    } else {
        stopTimeStepTimer();
        toast("时间自动增加步数功能已关闭");
        logToFile("时间步数功能已关闭");
    }
}

void enableMessageStep(boolean enable) {
    synchronized(this) {
        messageStepEnabled = enable;
        putBoolean("messageStepEnabled", messageStepEnabled);
    }
    
    if (enable) {
        toast("消息自动增加步数功能已开启");
        logToFile("消息步数功能已开启");
    } else {
        toast("消息自动增加步数功能已关闭");
        logToFile("消息步数功能已关闭");
    }
}

void showStepStatus(String talker) {
    long curStep;
    long curMinG;
    boolean tEnabled;
    boolean mEnabled;
    int minTs;
    int maxTs;
    long curMaxMessageStep;
    long curMaxTimeStep;

    synchronized (this) {
        curStep = currentStep;
        curMinG = minGuaranteedStep;
        tEnabled = timeStepEnabled;
        mEnabled = messageStepEnabled;
        minTs = minTimeStep;
        maxTs = maxTimeStep;
        curMaxMessageStep = maxMessageStep;
        curMaxTimeStep = maxTimeStepCalculated;
    }

    if (!tEnabled && !mEnabled) {
        String status = "步数增加功能未启用，请启用时间步数或消息步数功能。";
        sendText(talker, status);
    } else if (mEnabled) {
        long progress = (curMaxMessageStep == 0) ? 0 : (curStep * 100 / curMaxMessageStep);
        String status = "当前步数: " + curStep + "\n" +
                "今日目标: " + curMaxMessageStep + "\n" +
                "进度: " + progress + "%\n" +
                "保底步数: " + curMinG + "\n" +
                "最大消息步数: " + curMaxMessageStep;
        sendText(talker, status);
    } else if (tEnabled) {
        String status = "当前步数: " + curStep + "\n" +
                "保底步数: " + curMinG + "\n" +
                "时间步数范围: " + (minTs * calculateActiveMinutes()) + "-" + (maxTs * calculateActiveMinutes()) + "\n" +
                "每分钟步数范围: " + minTs + "-" + maxTs;
        sendText(talker, status);
    }
    
    logToFile("查询步数状态");
}

void showStepStatusAll(String talker) {
    long curStep;
    long curMaxStep;
    long curMinG;
    boolean tEnabled;
    boolean mEnabled;
    int minTs;
    int maxTs;
    long curMaxMessageStep;
    long curMaxTimeStep;

    synchronized (this) {
        curStep = currentStep;
        curMaxStep = maxStep;
        curMinG = minGuaranteedStep;
        tEnabled = timeStepEnabled;
        mEnabled = messageStepEnabled;
        minTs = minTimeStep;
        maxTs = maxTimeStep;
        curMaxMessageStep = maxMessageStep;
        curMaxTimeStep = maxTimeStepCalculated;
    }

    long progress = curMaxStep == 0 ? 0 : (curStep * 100 / curMaxStep);

    String status = "当前步数: " + curStep + "\n" +
            "时间步数: " + (tEnabled ? "已开启" : "已关闭") + "\n" +
            "消息步数: " + (mEnabled ? "已开启" : "已关闭") + "\n" +
            "保底步数状态: " + (curMinG > 0 ? "已开启" : "已关闭") + "\n" +
            "步数范围: " + minTs + "-" + maxTs + "\n" +
            "保底步数: " + curMinG + "\n" +
            "今日目标: " + curMaxStep + "\n" +
            "进度: " + progress + "%";

    if (tEnabled) {
        status += "\n时间步数范围: " + (minTs * calculateActiveMinutes()) + "-" + (maxTs * calculateActiveMinutes());
        status += "\n每分钟步数范围: " + minTs + "-" + maxTs;
    }
    if (mEnabled) {
        status += "\n消息步数最大值: " + curMaxMessageStep;
    }

    sendText(talker, status);
    logToFile("查询步数状态all");
}

boolean isRestrictedTime() {
    if (isTestMode) {
        return false;
    }
    
    LocalDateTime now = LocalDateTime.now();
    LocalTime time = now.toLocalTime();
    
    LocalTime startTime = LocalTime.of(7, 0);
    LocalTime endTime = LocalTime.of(22, 50);
    
    return time.isBefore(startTime) || !time.isBefore(endTime);
}

int calculateActiveMinutes() {
    return 15 * 60 + 50;
}

void safeUploadDeviceStep(long step) {
    try {
        uploadDeviceStep(step);
        synchronized(this) {
            if (pendingStepUpload > 0 && pendingStepUpload != step) {
                uploadPendingSteps();
                pendingStepUpload = 0;
                putLong("pendingStepUpload", 0);
            }
        }
    } catch (Exception e) {
        synchronized(this) {
            logToFile("步数上传失败: " + e.getMessage());
            pendingStepUpload = step;
            putLong("pendingStepUpload", pendingStepUpload);
            logToFile("记录待上传步数: " + pendingStepUpload);
        }
    }
}

void logToFile(String message) {
    // 检查日志输出开关
    if (!logOutputEnabled) {
        return;
    }
    
    try {
        if (!logDir.exists()) {
            logDir.mkdirs();
        }

        File logFile = new File(logDirPath + "autostep_log.txt");

        if (logFile.exists() && logFile.length() > MAX_LOG_FILE_SIZE) {
            FileWriter clearWriter = new FileWriter(logFile, false);
            clearWriter.write("");
            clearWriter.close();
        }

        String timestamp = dateFormat.format(new Date());
        String logMessage = "[" + timestamp + "] " + message + "\n";

        FileWriter writer = new FileWriter(logFile, true);
        writer.append(logMessage);
        writer.close();
    } catch (IOException e) {
        log("日志写入失败: " + e.getMessage());
    }
}

boolean onLongClickSendBtn(String text) {
    return false;
}