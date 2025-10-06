import java.time.LocalDateTime;
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
int maxTimeStep = 18;
long pendingStepUpload = 0;
long lastExecutionTime = 0;
String logDirPath = "/storage/emulated/0/Android/media/com.tencent.mm/WAuxiliary/Plugin/AutoStep/";
File logDir = new File(logDirPath);
SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
long minGuaranteedStep = 0;
ScheduledExecutorService guaranteedStepExecutor = null;
boolean isExecutingTask = false;
boolean isTestMode = false; // 测试人员开关，默认为false

void onLoad() {
    currentStep = getLong("currentStep", 0);
    currentDay = getInt("currentDay", 0);
    timeStepEnabled = getBoolean("timeStepEnabled", false);
    messageStepEnabled = getBoolean("messageStepEnabled", false);
    maxStep = getLong("maxStep", 24305);
    minTimeStep = getInt("minTimeStep", 6);
    maxTimeStep = getInt("maxTimeStep", 18);
    pendingStepUpload = getLong("pendingStepUpload", 0);
    lastExecutionTime = getLong("lastExecutionTime", 0);
    minGuaranteedStep = getLong("minGuaranteedStep", 0);
    
    LocalDateTime now = LocalDateTime.now();
    if (now.getDayOfYear() != currentDay) {
        currentStep = 0;
        currentDay = now.getDayOfYear();
        putInt("currentDay", currentDay);
        putLong("currentStep", currentStep);
        putLong("pendingStepUpload", 0);
        pendingStepUpload = 0;
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
            case "/步数帮助":
                showHelp(talker);
                return;
        }
        
        if (content.startsWith("/改步数 ")) {
            String[] parts = content.split(" ", 2);
            if (parts.length == 2) {
                try {
                    long step = Long.parseLong(parts[1].trim());
                    if (step >= 0 && step <= maxStep) {
                        currentStep = step;
                        putLong("currentStep", currentStep);
                        safeUploadDeviceStep(currentStep);
                        sendText(talker, "步数已修改为: " + step);
                        logToFile("手动修改步数: " + step);
                    } else {
                        sendText(talker, "步数必须在0-" + maxStep + "之间");
                    }
                } catch (NumberFormatException e) {
                    sendText(talker, "请输入有效的数字");
                }
            } else {
                sendText(talker, "命令格式: /改步数 数字");
            }
            return;
        }
        
        if (content.startsWith("/最大步数 ")) {
            String[] parts = content.split(" ", 2);
            if (parts.length == 2) {
                try {
                    long step = Long.parseLong(parts[1].trim());
                    if (step > 0) {
                        maxStep = step;
                        putLong("maxStep", maxStep);
                        sendText(talker, "最大步数已修改为: " + step);
                        logToFile("修改最大步数: " + step);
                    } else {
                        sendText(talker, "最大步数必须大于0");
                    }
                } catch (NumberFormatException e) {
                    sendText(talker, "请输入有效的数字");
                }
            } else {
                sendText(talker, "命令格式: /最大步数 数字");
            }
            return;
        }
        
        if (content.startsWith("/步数范围 ")) {
            String[] parts = content.split(" ", 2);
            if (parts.length == 2) {
                String[] rangeParts = parts[1].trim().split("-");
                if (rangeParts.length == 2) {
                    try {
                        int min = Integer.parseInt(rangeParts[0].trim());
                        int max = Integer.parseInt(rangeParts[1].trim());
                        if (min >= 0 && max > min) {
                            minTimeStep = min;
                            maxTimeStep = max;
                            putInt("minTimeStep", minTimeStep);
                            putInt("maxTimeStep", maxTimeStep);
                            sendText(talker, "时间步数范围已修改为: " + min + "-" + max);
                            
                            int activeMinutes = calculateActiveMinutes();
                            int dailyMin = minTimeStep * activeMinutes;
                            int dailyMax = maxTimeStep * activeMinutes;
                            sendText(talker, "当日最大步数范围: " + dailyMin + "-" + dailyMax);
                            
                            logToFile("修改时间步数范围: " + min + "-" + max);
                        } else {
                            sendText(talker, "范围无效，最小值必须大于等于0且最大值必须大于最小值");
                        }
                    } catch (NumberFormatException e) {
                        sendText(talker, "请输入有效的数字范围");
                    }
                } else {
                    sendText(talker, "命令格式: /步数范围 最小值-最大值");
                }
            } else {
                sendText(talker, "命令格式: /步数范围 最小值-最大值");
            }
            return;
        }
        
        if (content.startsWith("/保底步数 ")) {
            String[] parts = content.split(" ", 2);
            if (parts.length == 2) {
                try {
                    long step = Long.parseLong(parts[1].trim());
                    if (step >= 0) {
                        minGuaranteedStep = step;
                        putLong("minGuaranteedStep", minGuaranteedStep);
                        sendText(talker, "保底步数已修改为: " + step);
                        logToFile("修改保底步数: " + step);
                        
                        if (minGuaranteedStep > 0) {
                            startGuaranteedStepTimer();
                            toast("保底步数功能已开启");
                            logToFile("保底步数功能已开启");
                        } else {
                            stopGuaranteedStepTimer();
                            toast("保底步数功能已关闭");
                            logToFile("保底步数功能已关闭");
                        }
                    } else {
                        sendText(talker, "保底步数必须大于等于0");
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
    
    if (currentDay == 0) {
        currentStep = getLong("currentStep", 0);
        currentDay = getInt("currentDay", 0);
    }
    
    LocalDateTime now = LocalDateTime.now();
    if (now.getDayOfYear() != currentDay) {
        currentStep = 0;
        currentDay = now.getDayOfYear();
        putInt("currentDay", currentDay);
        putLong("pendingStepUpload", 0);
        pendingStepUpload = 0;
    }
    
    Random random = new Random();
    int step = 50 + random.nextInt(100);
    
    currentStep += calculateStepIncrease(currentStep, step);
    currentStep = Math.min(currentStep, maxStep);
    
    putLong("currentStep", currentStep);
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
    if (isTimerRunning) {
        return;
    }
    
    stopTimeStepTimer();
    
    scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
    scheduledExecutor.scheduleAtFixedRate(new Runnable() {
        public void run() {
            try {
                if (isExecutingTask) {
                    return;
                }
                
                isExecutingTask = true;
                
                if (isRestrictedTime()) {
                    isExecutingTask = false;
                    return;
                }
                
                LocalDateTime now = LocalDateTime.now();
                if (now.getDayOfYear() != currentDay) {
                    currentStep = 0;
                    currentDay = now.getDayOfYear();
                    putInt("currentDay", currentDay);
                    putLong("currentStep", currentStep);
                    putLong("pendingStepUpload", 0);
                    pendingStepUpload = 0;
                }
                
                long currentTime = System.currentTimeMillis();
                
                // 检查是否存在缺失步数
                boolean hasMissedTasks = checkAndExecuteMissedTasks(currentTime);
                
                if (!hasMissedTasks) {
                    // 没有缺失步数，正常增加步数
                    Random random = new Random();
                    int step = minTimeStep + random.nextInt(maxTimeStep - minTimeStep + 1);
                    currentStep += step;
                    
                    if (currentStep > maxStep) {
                        currentStep = maxStep;
                    }
                    
                    putLong("currentStep", currentStep);
                    safeUploadDeviceStep(currentStep);
                    
                    logToFile("时间步数: +" + step + " -> " + currentStep);
                }
                
                // 更新最后执行时间
                lastExecutionTime = currentTime;
                putLong("lastExecutionTime", lastExecutionTime);
                
                isExecutingTask = false;
            } catch (Exception e) {
                logToFile("定时任务异常: " + e.getMessage());
                isExecutingTask = false;
            }
        }
    }, 0, 1, TimeUnit.MINUTES);
    
    isTimerRunning = true;
    logToFile("时间步数定时器启动");
}

void stopTimeStepTimer() {
    if (scheduledExecutor != null) {
        scheduledExecutor.shutdownNow();
        scheduledExecutor = null;
    }
    
    isTimerRunning = false;
    logToFile("时间步数定时器停止");
}

void startGuaranteedStepTimer() {
    stopGuaranteedStepTimer();
    
    guaranteedStepExecutor = Executors.newSingleThreadScheduledExecutor();
    
    LocalDateTime now = LocalDateTime.now();
    // 修改保底步数检查时间为22:50
    LocalDateTime targetTime = now.withHour(22).withMinute(50).withSecond(0).withNano(0);
    
    if (now.isAfter(targetTime)) {
        targetTime = targetTime.plusDays(1);
    }
    
    long initialDelay = java.time.Duration.between(now, targetTime).toMillis();
    
    guaranteedStepExecutor.scheduleAtFixedRate(new Runnable() {
        public void run() {
            try {
                executeGuaranteedStepCheck();
            } catch (Exception e) {
                logToFile("保底步数检查异常: " + e.getMessage());
            }
        }
    }, initialDelay, 24 * 60 * 60 * 1000, TimeUnit.MILLISECONDS);
    
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
    try {
        LocalDateTime now = LocalDateTime.now();
        if (now.getDayOfYear() != currentDay) {
            currentStep = 0;
            currentDay = now.getDayOfYear();
            putInt("currentDay", currentDay);
            putLong("currentStep", currentStep);
        }
        
        Random random = new Random();
        long guaranteedStep = minGuaranteedStep + random.nextInt(1000);
        
        if (currentStep < guaranteedStep) {
            currentStep = guaranteedStep;
            if (currentStep > maxStep) {
                currentStep = maxStep;
            }
            
            putLong("currentStep", currentStep);
            safeUploadDeviceStep(currentStep);
            logToFile("保底步数生效: " + currentStep);
        }
    } catch (Exception e) {
        logToFile("保底步数检查异常: " + e.getMessage());
    }
}

boolean checkAndExecuteMissedTasks(long currentTime) {
    if (lastExecutionTime == 0) {
        return false;
    }
    
    long timeDiff = currentTime - lastExecutionTime;
    
    if (timeDiff > 61 * 1000) {
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
            currentStep += step;
            totalAddedSteps += step;
            
            if (currentStep > maxStep) {
                currentStep = maxStep;
                break;
            }
        }
        
        putLong("currentStep", currentStep);
        safeUploadDeviceStep(currentStep);
        logToFile("补充缺失: " + missedMinutes + "分钟 +" + totalAddedSteps + " -> " + currentStep);
        
        return true;
    }
    
    return false;
}

void enableTimeStep(boolean enable) {
    timeStepEnabled = enable;
    putBoolean("timeStepEnabled", timeStepEnabled);
    
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
    messageStepEnabled = enable;
    putBoolean("messageStepEnabled", messageStepEnabled);
    
    if (enable) {
        toast("消息自动增加步数功能已开启");
        logToFile("消息步数功能已开启");
    } else {
        toast("消息自动增加步数功能已关闭");
        logToFile("消息步数功能已关闭");
    }
}

void showStepStatus(String talker) {
    String status = String.format("当前步数: %d\n时间步数: %s\n消息步数: %s\n保底步数: %s\n步数范围: %d-%d\n保底步数: %d\n今日目标: %d\n进度: %d%%",
                                 currentStep,
                                 timeStepEnabled ? "已开启" : "已关闭",
                                 messageStepEnabled ? "已开启" : "已关闭",
                                 minGuaranteedStep > 0 ? "已开启" : "已关闭",
                                 minTimeStep,
                                 maxTimeStep,
                                 minGuaranteedStep,
                                 maxStep,
                                 currentStep * 100 / maxStep);
    
    sendText(talker, status);
    logToFile("查询步数状态");
}

void showHelp(String talker) {
    String helpText = "微信步数插件使用说明：\n\n" +
                      "【功能控制命令】\n" +
                      "/时间步数开 - 开启定时自动增加步数功能\n" +
                      "/时间步数关 - 关闭定时自动增加步数功能\n" +
                      "/消息步数开 - 开启消息自动增加步数功能\n" +
                      "/消息步数关 - 关闭消息自动增加步数功能\n\n" +
                      "【手动控制命令】\n" +
                      "/改步数 数字 - 手动修改步数为指定数值\n" +
                      "/最大步数 数字 - 修改最大步数限制\n" +
                      "/步数范围 最小值-最大值 - 修改时间步数随机范围\n" +
                      "/保底步数 数字 - 修改保底步数（0表示关闭保底功能）\n\n" +
                      "【查询命令】\n" +
                      "/步数状态 - 查看当前步数和功能状态\n" +
                      "/步数帮助 - 显示本帮助信息\n\n" +
                      "【功能说明】\n" +
                      "1. 时间步数：每分钟自动增加" + minTimeStep + "-" + maxTimeStep + "步\n" +
                      "1. 时间步数：每分钟自动增加" + minTimeStep + "-" + maxTimeStep + "步\n" +
                      "2. 消息步数：每次收发消息时自动增加50-150步\n" +
                      "3. 保底步数：22:50时检查当前步数，若小于保底步数则修改为保底步数\n" +
                      "4. 步数增长幅度会根据当前步数自动调整\n" +
                      "5. 23:00-6:00时间段内不会自动增加步数\n" +
                      "6. 每日0点会自动重置步数\n" +
                      "7. 无网络时步数仍会增加并存储，网络恢复后自动上传";
    
    sendText(talker, helpText);
    logToFile("查询帮助信息");
}

boolean isRestrictedTime() {
    if (isTestMode) {
        return false; // 测试模式下，不限制时间
    }
    
    LocalDateTime now = LocalDateTime.now();
    int hour = now.getHour();
    return hour >= 23 || hour < 6;
}

int calculateActiveMinutes() {
    return 24 * 60 - 60 - 6 * 60;
}

void safeUploadDeviceStep(long step) {
    try {
        uploadDeviceStep(step);
        
        if (pendingStepUpload > 0 && pendingStepUpload != step) {
            uploadPendingSteps();
        }
    } catch (Exception e) {
        logToFile("步数上传失败: " + e.getMessage());
        pendingStepUpload = step;
        putLong("pendingStepUpload", pendingStepUpload);
        logToFile("记录待上传步数: " + pendingStepUpload);
    }
}

void uploadPendingSteps() {
    if (pendingStepUpload > 0) {
        try {
            uploadDeviceStep(pendingStepUpload);
            logToFile("待上传步数上传成功: " + pendingStepUpload);
            pendingStepUpload = 0;
            putLong("pendingStepUpload", 0);
        } catch (Exception e) {
            logToFile("待上传步数上传失败: " + e.getMessage());
        }
    }
}

void logToFile(String message) {
    if (!isTestMode) {
        return;
    }
    
    try {
        String timestamp = dateFormat.format(new Date());
        String logMessage = "[" + timestamp + "] " + message + "\n";
        
        File logFile = new File(logDirPath + "autostep_log.txt");
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
