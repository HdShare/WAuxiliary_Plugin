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
import java.util.concurrent.atomic.AtomicBoolean;

long maxStep = 24305;
long currentStep = 0;
int currentDay = 0;
ScheduledExecutorService scheduledExecutor = null;
boolean isTimerRunning = false;
boolean timeStepEnabled = false;
boolean messageStepEnabled = false;
int minTimeStep = 4;
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
long MAX_LOG_FILE_SIZE = 1 * 512 * 1024;
boolean logOutputEnabled = true;
long maxMessageStep = 10000;
long maxTimeStepCalculated = 0;

long targetTimeStep = 0;
boolean linearTargetReached = false;

private static final int STRATEGY_LINEAR = 0;
private static final int STRATEGY_EXP = 1;
int distributionStrategy = STRATEGY_LINEAR; // 默认线性分配
double expRatio = 1.05; // 指数比率（>1，越大越前置）
int jitterMax = 2; // 每分钟平滑抖动最大步数（正负）

private final Object lock = new Object();
private final AtomicBoolean timeTaskRunning = new AtomicBoolean(false);
private final Random random = new Random();

private static final String CMD_TIME_STEP_ON = "/时间步数开";
private static final String CMD_TIME_STEP_OFF = "/时间步数关";
private static final String CMD_MESSAGE_STEP_ON = "/消息步数开";
private static final String CMD_MESSAGE_STEP_OFF = "/消息步数关";
private static final String CMD_STEP_STATUS = "/步数状态";
private static final String CMD_STEP_STATUS_ALL = "/步数状态all";
private static final String CMD_CHANGE_STEP = "/改步数 ";
private static final String CMD_MAX_MESSAGE_STEP = "/最大消息步数 ";
private static final String CMD_MINUTE_RANGE = "/分钟步数范围 ";
private static final String CMD_GUARANTEED_STEP = "/保底步数 ";
private static final String CMD_SET_TIME_TARGET = "/时间步目标 ";
private static final String CMD_SET_DISTRIBUTION = "/分配策略 ";

void onLoad() {
    synchronized (lock) {
        currentStep = getLong("currentStep", 0);
        currentDay = getInt("currentDay", 0);
        timeStepEnabled = getBoolean("timeStepEnabled", false);
        messageStepEnabled = getBoolean("messageStepEnabled", false);
        maxStep = getLong("maxStep", 24305);
        minTimeStep = getInt("minTimeStep", 4);
        maxTimeStep = getInt("maxTimeStep", 12);
        maxMessageStep = getLong("maxMessageStep", 10000);
        pendingStepUpload = getLong("pendingStepUpload", 0);
        lastExecutionTime = getLong("lastExecutionTime", 0);
        minGuaranteedStep = getLong("minGuaranteedStep", 0);
        targetTimeStep = getLong("targetTimeStep", 0);
        linearTargetReached = getBoolean("linearTargetReached", false);
        distributionStrategy = getInt("distributionStrategy", STRATEGY_LINEAR);
        maxTimeStepCalculated = maxTimeStep * calculateActiveMinutes();
    }
    
    LocalDateTime now = LocalDateTime.now();
    synchronized (lock) {
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
    synchronized (lock) {
        currentStep = 0;
        currentDay = now.getDayOfYear();
        pendingStepUpload = 0;
        lastExecutionTime = 0;
        putInt("currentDay", currentDay);
        putLong("currentStep", currentStep);
        putLong("pendingStepUpload", 0);
        putLong("lastExecutionTime", 0);
    }
    logToFile("新的一天重置完成");
}

void startTimeStepTimer() {
    synchronized (lock) {
        if (isTimerRunning) {
            return;
        }
        if (scheduledExecutor != null) {
            scheduledExecutor.shutdownNow();
        }
        scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
        isTimerRunning = true;
    }
    
    scheduledExecutor.scheduleAtFixedRate(new Runnable() {
        public void run() {
            if (!timeTaskRunning.compareAndSet(false, true)) {
                return;
            }
            try {
                if (isRestrictedTime()) {
                    return;
                }
 
                LocalDateTime now = LocalDateTime.now();
                synchronized (lock) {
                    if (now.getDayOfYear() != currentDay) {
                        resetDay(now);
                    }
                }
 
                long currentTime = System.currentTimeMillis();
                boolean hasMissedTasks = checkAndExecuteMissedTasks(currentTime);
 
                if (!hasMissedTasks) {
                    int step = 0;
                    synchronized (lock) {
                        if (targetTimeStep > 0) {
                            LocalDateTime cutoff = now.withHour(22).withMinute(30).withSecond(0).withNano(0);
                            if (!now.isBefore(cutoff)) {
                                if (currentStep < targetTimeStep) {
                                    currentStep = Math.min(targetTimeStep, maxStep);
                                    putLong("currentStep", currentStep);
                                    lastExecutionTime = currentTime;
                                    putLong("lastExecutionTime", lastExecutionTime);
                                    safeUploadDeviceStep(currentStep);
                                    logToFile("到达22:30，设置时间目标步数 -> " + currentStep);
                                } else {
                                    logToFile("到达22:30，目标已达成，无需增加");
                                }
                                linearTargetReached = true;
                                putBoolean("linearTargetReached", linearTargetReached);
                                return;
                            } else {
                                long minutesRemaining = java.time.Duration.between(now, cutoff).toMinutes();
                                if (minutesRemaining <= 0) {
                                    if (currentStep < targetTimeStep) {
                                        currentStep = Math.min(targetTimeStep, maxStep);
                                    }
                                    linearTargetReached = true;
                                    putBoolean("linearTargetReached", linearTargetReached);
                                    putLong("currentStep", currentStep);
                                    lastExecutionTime = currentTime;
                                    putLong("lastExecutionTime", lastExecutionTime);
                                    safeUploadDeviceStep(currentStep);
                                    logToFile("临界处理：设置时间目标步数 -> " + currentStep);
                                    return;
                                } else {
                                    long stepsNeeded = targetTimeStep - currentStep;
                                    if (stepsNeeded <= 0) {
                                        linearTargetReached = true;
                                        putBoolean("linearTargetReached", linearTargetReached);
                                        return;
                                    }
                                    // 根据分配策略计算每分钟应分配的步数，并加入小幅抖动以平滑
                                    boolean useExp = (distributionStrategy == STRATEGY_EXP);
                                    long perMinute = computeAllocation(stepsNeeded, minutesRemaining, useExp, minTimeStep, maxTimeStep);
                                    int jitter = jitterMax > 0 ? (random.nextInt(jitterMax * 2 + 1) - jitterMax) : 0;
                                    perMinute = Math.max(perMinute + jitter, minTimeStep);
                                    perMinute = Math.min(perMinute, maxTimeStep);
                                    step = (int) Math.min(perMinute, stepsNeeded);
                                }
                            }
                        } else {
                            int min = Math.max(0, minTimeStep);
                            int max = Math.max(min + 1, maxTimeStep);
                            step = min + random.nextInt(max - min + 1);
                        }
 
                        currentStep += step;
                        if (currentStep > maxStep) {
                            currentStep = maxStep;
                        }
                        if (targetTimeStep > 0 && currentStep > targetTimeStep) {
                            currentStep = targetTimeStep;
                        }
                        putLong("currentStep", currentStep);
                        lastExecutionTime = currentTime;
                        putLong("lastExecutionTime", lastExecutionTime);
                    }
                    if (step > 0) {
                        safeUploadDeviceStep(currentStep);
                        logToFile("时间步数: +" + step + " -> " + currentStep);
                    }
                }
            } catch (Exception e) {
                logToFile("定时任务异常: " + e.getMessage());
            } finally {
                timeTaskRunning.set(false);
            }
        }
    }, 0, 1, TimeUnit.MINUTES);
    
    logToFile("时间步数定时器启动");
}

void stopTimeStepTimer() {
    synchronized (lock) {
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
    synchronized (lock) {
        if (now.getDayOfYear() != currentDay) {
            resetDay(now);
        }
        
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
    synchronized (lock) {
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

    int validMinutesCounted = 0;
    for (int i = 0; i < missedMinutes; i++) {
        long minuteTime = last + (long) i * 60 * 1000;
        java.time.Instant instant = java.time.Instant.ofEpochMilli(minuteTime);
        LocalDateTime minuteDateTime = LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault());
        if (isActiveMinute(minuteDateTime.toLocalTime())) {
            validMinutesCounted++;
        }
    }

    if (validMinutesCounted == 0) {
        logToFile("补充缺失: " + missedMinutes + "分钟(实际有效:0分钟) +0 -> " + currentStep);
        return false;
    }

    int totalAddedSteps = 0;
    int validMinutesProcessed = 0;

    for (int i = 0; i < missedMinutes && validMinutesProcessed < validMinutesCounted; i++) {
        long minuteTime = last + (long) i * 60 * 1000;
        java.time.Instant instant = java.time.Instant.ofEpochMilli(minuteTime);
        LocalDateTime minuteDateTime = LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault());
        LocalTime minuteTimeOnly = minuteDateTime.toLocalTime();
 
        if (isActiveMinute(minuteTimeOnly)) {
            int step = 0;
            synchronized (lock) {
                int min = Math.max(0, minTimeStep);
                int max = Math.max(min + 1, maxTimeStep);
                if (targetTimeStep > 0) {
                    int remainingValid = validMinutesCounted - validMinutesProcessed;
                    long stepsNeeded = targetTimeStep - currentStep;
                    if (stepsNeeded <= 0) {
                        linearTargetReached = true;
                        putBoolean("linearTargetReached", linearTargetReached);
                        return true;
                    }
                    long perMinute = computeAllocation(stepsNeeded, Math.max(1, remainingValid), distributionStrategy == STRATEGY_EXP, min, max);
                    int jitter = jitterMax > 0 ? (random.nextInt(jitterMax * 2 + 1) - jitterMax) : 0;
                    perMinute = Math.max(perMinute + jitter, min);
                    perMinute = Math.min(perMinute, max);
                    step = (int) Math.min(perMinute, stepsNeeded);
                } else {
                    step = min + random.nextInt(max - min + 1);
                }
 
                currentStep += step;
                totalAddedSteps += step;
                if (currentStep > maxStep) {
                    currentStep = maxStep;
                    putLong("currentStep", currentStep);
                    safeUploadDeviceStep(currentStep);
                    logToFile("补充缺失: 达到最大步数 -> " + currentStep);
                    return true;
                }
            }
            validMinutesProcessed++;
        }
    }

    if (totalAddedSteps > 0) {
        synchronized (lock) {
            putLong("currentStep", currentStep);
            lastExecutionTime = currentTime;
            putLong("lastExecutionTime", lastExecutionTime);
        }
        safeUploadDeviceStep(currentStep);
        logToFile("补充缺失: " + missedMinutes + "分钟(实际有效:" + validMinutesCounted + "分钟) +" + totalAddedSteps + " -> " + currentStep);
        return true;
    }

    return false;
}

boolean isRestrictedTime() {
    if (isTestMode) {
        return false;
    }
    try {
        LocalTime time = LocalDateTime.now().toLocalTime();
        return !isActiveMinute(time);
    } catch (Exception e) {
        return true;
    }
}

boolean isActiveMinute(LocalTime time) {
    LocalTime startTime = LocalTime.of(7, 0);
    LocalTime endTime = LocalTime.of(22, 50);
    return time.compareTo(startTime) >= 0 && time.compareTo(endTime) < 0;
}

int calculateActiveMinutes() {
    return 15 * 60 + 50;
}

void enableTimeStep(boolean enable) {
    synchronized (lock) {
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
    synchronized (lock) {
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

void updateStepOnMessage() {
    if (isRestrictedTime()) {
        return;
    }
    
    LocalDateTime now = LocalDateTime.now();
    synchronized (lock) {
        if (currentDay == 0) {
            currentStep = getLong("currentStep", 0);
            currentDay = getInt("currentDay", 0);
        }
        if (now.getDayOfYear() != currentDay) {
            resetDay(now);
        }
    }
    
    int base = 50 + random.nextInt(100);
    long inc;
    long cur;
    
    synchronized (lock) {
        inc = calculateStepIncrease(currentStep, base);
        currentStep += inc;
        if (currentStep > maxStep) {
            currentStep = maxStep;
        }
        putLong("currentStep", currentStep);
        cur = currentStep;
    }
    
    safeUploadDeviceStep(cur);
    logToFile("消息步数: +" + inc + " -> " + cur);
}

long calculateStepIncrease(long currentStep, int baseStep) {
    if (currentStep < 4000) {
        return baseStep * 3L;
    } else if (currentStep < 8000) {
        return baseStep * 2L;
    } else if (currentStep < 16000) {
        return baseStep;
    } else {
        return (long) (baseStep * 0.5);
    }
}

void onHandleMsg(Object msgInfoBean) {
    if (msgInfoBean == null || !msgInfoBean.isSend()) {
        return;
    }
    if (!msgInfoBean.isText()) {
        return;
    }

    String content = msgInfoBean.getContent().trim();
    String talker = msgInfoBean.getTalker();

    switch (content) {
        case CMD_TIME_STEP_ON:
            enableTimeStep(true);
            return;
        case CMD_TIME_STEP_OFF:
            enableTimeStep(false);
            return;
        case CMD_MESSAGE_STEP_ON:
            enableMessageStep(true);
            return;
        case CMD_MESSAGE_STEP_OFF:
            enableMessageStep(false);
            return;
        case CMD_STEP_STATUS:
            showStepStatus(talker);
            return;
        case CMD_STEP_STATUS_ALL:
            showStepStatusAll(talker);
            return;
    }

    if (content.startsWith(CMD_CHANGE_STEP)) {
        handleChangeStepCmd(content, talker);
        return;
    }
 
    if (content.startsWith(CMD_MAX_MESSAGE_STEP)) {
        handleMaxMessageStepCmd(content, talker);
        return;
    }
 
    if (content.startsWith(CMD_MINUTE_RANGE)) {
        handleMinuteStepRangeCmd(content, talker);
        return;
    }
 
    if (content.startsWith(CMD_GUARANTEED_STEP)) {
        handleMinGuaranteedStepCmd(content, talker);
        return;
    }
 
    if (content.startsWith(CMD_SET_TIME_TARGET)) {
        handleSetTimeTargetCmd(content, talker);
        return;
    }

    if (content.startsWith(CMD_SET_DISTRIBUTION)) {
        handleSetDistributionCmd(content, talker);
        return;
    }

    boolean msgEnabled;
    synchronized (lock) {
        msgEnabled = messageStepEnabled;
    }
    if (msgEnabled) {
        updateStepOnMessage();
    }
}

void handleChangeStepCmd(String content, String talker) {
    Long value = parseLongArg(content, CMD_CHANGE_STEP);
    if (value == null) {
        sendText(talker, "命令格式: /改步数 数字");
        return;
    }
    long step = value;
    synchronized (lock) {
        if (step < 0 || step > maxStep) {
            sendText(talker, "步数必须在0-" + maxStep + "之间");
            return;
        }
        currentStep = step;
        putLong("currentStep", currentStep);
    }
    safeUploadDeviceStep(step);
    sendText(talker, "步数已修改为: " + step);
    logToFile("手动修改步数: " + step);
}

void handleMaxMessageStepCmd(String content, String talker) {
    Long value = parseLongArg(content, CMD_MAX_MESSAGE_STEP);
    if (value == null) {
        sendText(talker, "命令格式: /最大消息步数 数字");
        return;
    }
    long step = value;
    if (step < 0) {
        sendText(talker, "最大消息步数必须大于等于0");
        return;
    }
    synchronized (lock) {
        maxMessageStep = step;
        putLong("maxMessageStep", maxMessageStep);
    }
    sendText(talker, "最大消息步数已修改为: " + step);
    logToFile("修改最大消息步数: " + step);
}

void handleMinuteStepRangeCmd(String content, String talker) {
    String arg = content.substring(CMD_MINUTE_RANGE.length()).trim();
    String[] parts = arg.split("-");
    if (parts.length != 2) {
        sendText(talker, "命令格式: /分钟步数范围 最小值-最大值");
        return;
    }
    try {
        int min = Integer.parseInt(parts[0].trim());
        int max = Integer.parseInt(parts[1].trim());
        if (min < 0 || max <= min) {
            sendText(talker, "范围无效，最小值必须大于等于0且最大值必须大于最小值");
            return;
        }
        synchronized (lock) {
            minTimeStep = min;
            maxTimeStep = max;
            maxTimeStepCalculated = maxTimeStep * calculateActiveMinutes();
            putInt("minTimeStep", minTimeStep);
            putInt("maxTimeStep", maxTimeStep);
        }
        sendText(talker, "每分钟步数范围已修改为: " + min + "-" + max);
        sendText(talker, "时间步数范围: " + (min * calculateActiveMinutes()) + "-" + (max * calculateActiveMinutes()));
        logToFile("修改每分钟步数范围: " + min + "-" + max);
    } catch (NumberFormatException e) {
        sendText(talker, "请输入有效的数字范围");
    }
}

void handleMinGuaranteedStepCmd(String content, String talker) {
    Long value = parseLongArg(content, CMD_GUARANTEED_STEP);
    if (value == null) {
        sendText(talker, "命令格式: /保底步数 数字");
        return;
    }
    long step = value;
    if (step < 0) {
        sendText(talker, "保底步数必须大于等于0");
        return;
    }
    synchronized (lock) {
        if (step > maxStep) {
            sendText(talker, "保底步数不能大于最大步数 " + maxStep);
            return;
        }
        minGuaranteedStep = step;
        putLong("minGuaranteedStep", minGuaranteedStep);
    }
    sendText(talker, "保底步数已修改为: " + step);
    logToFile("修改保底步数: " + step);
 
    if (step > 0) {
        startGuaranteedStepTimer();
        toast("保底步数功能已开启");
        logToFile("保底步数功能已开启");
    } else {
        stopGuaranteedStepTimer();
        toast("保底步数功能已关闭");
        logToFile("保底步数功能已关闭");
    }
}
 
void handleSetTimeTargetCmd(String content, String talker) {
    Long value = parseLongArg(content, CMD_SET_TIME_TARGET);
    if (value == null) {
        sendText(talker, "命令格式: /时间步数目标 数字 (0表示取消)");
        return;
    }
    long step = value;
    synchronized (lock) {
        if (step < 0 || step > maxStep) {
            sendText(talker, "时间目标步数必须在0-" + maxStep + "之间");
            return;
        }
        targetTimeStep = step;
        putLong("targetTimeStep", targetTimeStep);
        linearTargetReached = false;
        putBoolean("linearTargetReached", linearTargetReached);
    }
    if (step > 0) {
        sendText(talker, "时间目标步数已设置为: " + step);
        logToFile("设置时间目标步数: " + step);
    } else {
        sendText(talker, "已取消时间目标步数");
        logToFile("取消时间目标步数");
    }
}
 
Long parseLongArg(String content, String prefix) {
    if (!content.startsWith(prefix)) {
        return null;
    }
    String arg = content.substring(prefix.length()).trim();
    if (arg.isEmpty()) {
        return null;
    }
    try {
        return Long.parseLong(arg);
    } catch (NumberFormatException e) {
        return null;
    }
}

void handleSetDistributionCmd(String content, String talker) {
    String arg = content.substring(CMD_SET_DISTRIBUTION.length()).trim();
    if (arg.isEmpty()) {
        sendText(talker, "命令格式: /分配策略 线性|指数");
        return;
    }
    int strategy = STRATEGY_LINEAR;
    if ("线性".equalsIgnoreCase(arg) || "linear".equalsIgnoreCase(arg) || "0".equals(arg)) {
        strategy = STRATEGY_LINEAR;
    } else if ("指数".equalsIgnoreCase(arg) || "exp".equalsIgnoreCase(arg) || "1".equals(arg)) {
        strategy = STRATEGY_EXP;
    } else {
        sendText(talker, "未知策略: " + arg + "。支持: 线性, 指数");
        return;
    }
    synchronized (lock) {
        distributionStrategy = strategy;
        putInt("distributionStrategy", distributionStrategy);
    }
    sendText(talker, "分配策略已设置为: " + (strategy == STRATEGY_EXP ? "指数" : "线性"));
    logToFile("设置分配策略: " + distributionStrategy);
}

long computeAllocation(long stepsNeeded, long remainingMinutes, boolean exponential, int minPerMinute, int maxPerMinute) {
    if (remainingMinutes <= 0 || stepsNeeded <= 0) {
        return minPerMinute;
    }
    if (!exponential) {
        return (long) Math.ceil((double) stepsNeeded / remainingMinutes);
    } else {
        double r = Math.max(1.01, expRatio);
        int M = (int) remainingMinutes;
        double totalWeight;
        try {
            totalWeight = (Math.pow(r, M) - 1.0) / (r - 1.0);
        } catch (Exception e) {
            totalWeight = M; // 退化为线性
        }
        double curWeight = Math.pow(r, M - 1);
        double frac = curWeight / Math.max(1e-9, totalWeight);
        long val = (long) Math.ceil(stepsNeeded * frac);
        long linear = (long) Math.ceil((double) stepsNeeded / remainingMinutes);
        return Math.min(Math.max(val, linear), maxPerMinute);
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
    long curTarget;
    boolean curLinearReached;

    synchronized (lock) {
        curStep = currentStep;
        curMinG = minGuaranteedStep;
        tEnabled = timeStepEnabled;
        mEnabled = messageStepEnabled;
        minTs = minTimeStep;
        maxTs = maxTimeStep;
        curMaxMessageStep = maxMessageStep;
        curMaxTimeStep = maxTimeStepCalculated;
        curTarget = targetTimeStep;
        curLinearReached = linearTargetReached;
    }

    String targetInfo = curTarget > 0 ? (String.valueOf(curTarget) + (curLinearReached ? "（已达）" : "（未达）")) : "未设置";

    if (!tEnabled && !mEnabled) {
        String status = "步数增加功能未启用，请启用时间步数或消息步数功能。";
        sendText(talker, status);
    } else if (mEnabled) {
        long progress = (curMaxMessageStep == 0) ? 0 : (curStep * 100 / curMaxMessageStep);
        StringBuilder status = new StringBuilder();
        status.append("当前步数: ").append(curStep).append("\n")
              .append("今日目标: ").append(curMaxMessageStep).append("\n")
              .append("进度: ").append(progress).append("%\n")
              .append("保底步数: ").append(curMinG).append("\n")
              .append("最大消息步数: ").append(curMaxMessageStep).append("\n")
              .append("时间目标步数: ").append(targetInfo);
        sendText(talker, status.toString());
    } else if (tEnabled) {
        StringBuilder status = new StringBuilder();
        status.append("当前步数: ").append(curStep).append("\n")
              .append("保底步数: ").append(curMinG).append("\n")
              .append("时间步数范围: ").append(minTs * calculateActiveMinutes()).append("-").append(maxTs * calculateActiveMinutes()).append("\n")
              .append("每分钟步数范围: ").append(minTs).append("-").append(maxTs).append("\n")
              .append("时间目标步数: ").append(targetInfo);
        sendText(talker, status.toString());
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
    long curTarget;
    boolean curLinearReached;

    synchronized (lock) {
        curStep = currentStep;
        curMaxStep = maxStep;
        curMinG = minGuaranteedStep;
        tEnabled = timeStepEnabled;
        mEnabled = messageStepEnabled;
        minTs = minTimeStep;
        maxTs = maxTimeStep;
        curMaxMessageStep = maxMessageStep;
        curMaxTimeStep = maxTimeStepCalculated;
        curTarget = targetTimeStep;
        curLinearReached = linearTargetReached;
    }

    String targetInfo = curTarget > 0 ? (String.valueOf(curTarget) + (curLinearReached ? "（已达）" : "（未达）")) : "未设置";

    long progress = curMaxStep == 0 ? 0 : (curStep * 100 / curMaxStep);
    StringBuilder status = new StringBuilder();
    status.append("当前步数: ").append(curStep).append("\n")
          .append("时间步数: ").append(tEnabled ? "已开启" : "已关闭").append("\n")
          .append("消息步数: ").append(mEnabled ? "已开启" : "已关闭").append("\n")
          .append("保底步数状态: ").append(curMinG > 0 ? "已开启" : "已关闭").append("\n")
          .append("步数范围: ").append(minTs).append("-").append(maxTs).append("\n")
          .append("保底步数: ").append(curMinG).append("\n")
          .append("今日目标: ").append(curMaxStep).append("\n")
          .append("时间目标步数: ").append(targetInfo).append("\n")
          .append("进度: ").append(progress).append("%");

    if (tEnabled) {
        status.append("\n时间步数范围: ").append(minTs * calculateActiveMinutes()).append("-").append(maxTs * calculateActiveMinutes());
        status.append("\n每分钟步数范围: ").append(minTs).append("-").append(maxTs);
    }
    if (mEnabled) {
        status.append("\n消息步数最大值: ").append(curMaxMessageStep);
    }

    sendText(talker, status.toString());
    logToFile("查询步数状态all");
}

void safeUploadDeviceStep(long step) {
    try {
        uploadDeviceStep(step);
        synchronized (lock) {
            if (pendingStepUpload > 0 && pendingStepUpload != step) {
                uploadPendingSteps();
                pendingStepUpload = 0;
                putLong("pendingStepUpload", 0);
            }
        }
    } catch (Exception e) {
        synchronized (lock) {
            logToFile("步数上传失败: " + e.getMessage());
            pendingStepUpload = step;
            putLong("pendingStepUpload", pendingStepUpload);
            logToFile("记录待上传步数: " + pendingStepUpload);
        }
    }
}

void uploadPendingSteps() {
    try {
        if (pendingStepUpload > 0) {
            uploadDeviceStep(pendingStepUpload);
            logToFile("补传步数成功: " + pendingStepUpload);
        }
    } catch (Exception e) {
        logToFile("补传步数失败: " + e.getMessage());
    }
}

void logToFile(String message) {
    if (!logOutputEnabled) {
        return;
    }
    
    try {
        if (!logDir.exists()) {
            boolean ok = logDir.mkdirs();
            if (!ok) {
                System.err.println("日志目录创建失败: " + logDir.getAbsolutePath());
                return;
            }
        }

        File logFile = new File(logDirPath + "autostep_log.txt");
        
        String timestamp = dateFormat.format(new Date());
        String newLogMessage = "[" + timestamp + "] " + message + "\n";
        
        String existingContent = "";
        if (logFile.exists() && logFile.length() > 0) {
            if (logFile.length() > MAX_LOG_FILE_SIZE) {
                existingContent = "";
            } else {
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(logFile));
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
                reader.close();
                existingContent = content.toString();
            }
        }
        
        String finalContent = newLogMessage + existingContent;
        
        FileWriter writer = new FileWriter(logFile, false);
        writer.write(finalContent);
        writer.close();
        
    } catch (IOException e) {
        System.err.println("日志写入失败: " + e.getMessage());
    }
}

boolean onLongClickSendBtn(String text) {
    return false;
}