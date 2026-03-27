import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.view.View;
import android.view.Gravity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.content.Context;

AtomicLong currentStep = new AtomicLong(0);
volatile int currentDay = 0;
volatile boolean linearTargetReached = false;
AtomicLong lastExecutionTime = new AtomicLong(0);
AtomicLong pendingStepUpload = new AtomicLong(0);

volatile long maxStep = 24305;
volatile int minTimeStep = 4;
volatile int maxTimeStep = 12;
volatile long maxMessageStep = 10000;
volatile long minGuaranteedStep = 0;
volatile long targetTimeStep = 0;
volatile int distributionStrategy = 0;
volatile double expRatio = 1.05;
volatile int jitterMax = 2;
volatile boolean timeStepEnabled = false;
volatile boolean messageStepEnabled = false;
volatile boolean logOutputEnabled = false;
volatile boolean isTestMode = false;
volatile boolean pauseAtMaxEnabled = true;

volatile ScheduledExecutorService scheduledExecutor = null;
volatile ScheduledExecutorService guaranteedStepExecutor = null;
volatile boolean isTimerRunning = false;
volatile long totalExecutions = 0;
volatile long totalExecutionTime = 0;
volatile long maxExecutionTime = 0;
volatile long lastPerformanceReport = 0;
volatile int uploadFailureCount = 0;
volatile int configErrorCount = 0;
volatile int timerErrorCount = 0;
volatile long lastErrorReport = 0;
volatile long lastHealthCheck = 0;
volatile long lastMemoryCheck = 0;
volatile long peakMemoryUsage = 0;
volatile long lastLogCleanup = 0;
volatile boolean enablePerformanceReport = false;
int MAX_LOG_FILES = 2;
int CPU_INTENSIVE_INTERVAL = 5000;

String logDirPath = "/storage/emulated/0/Android/media/com.tencent.mm/WAuxiliary/Plugin/AutoStep/";
File logDir = new File(logDirPath);

ReadWriteLock configLock = new ReentrantReadWriteLock();
AtomicBoolean timeTaskRunning = new AtomicBoolean(false);

ThreadLocal stringBuilderCache = new ThreadLocal() {
    protected Object initialValue() {
        return new StringBuilder(256);
    }
};

ThreadLocal dateFormatCache = new ThreadLocal() {
    protected Object initialValue() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    }
};

int ACTIVE_START_HOUR = 7;
int ACTIVE_START_MINUTE = 0;
int ACTIVE_END_HOUR = 22;
int ACTIVE_END_MINUTE = 50;
int TARGET_STEP_CUTOFF_HOUR = 22;
int TARGET_STEP_CUTOFF_MINUTE = 30;
int ACTIVE_MINUTES = 950;
long MINUTE_IN_MILLIS = 60000L;
long TIMEOUT_THRESHOLD = 61000L;
int MAX_MISSED_HOURS = 12;
int GUARANTEED_STEP_RANDOM_RANGE = 1000;
long MAX_LOG_FILE_SIZE = 512 * 1024;

int STRATEGY_LINEAR = 0;
int STRATEGY_EXP = 1;

LocalTime ACTIVE_START_TIME = LocalTime.of(ACTIVE_START_HOUR, ACTIVE_START_MINUTE);
LocalTime ACTIVE_END_TIME = LocalTime.of(ACTIVE_END_HOUR, ACTIVE_END_MINUTE);

int[] STEP_THRESHOLDS = {4000, 8000, 16000};
double[] STEP_MULTIPLIERS = {3.0, 2.0, 1.0, 0.5};

String CMD_TIME_STEP_ON = "/时间步数开";
String CMD_TIME_STEP_OFF = "/时间步数关";
String CMD_MESSAGE_STEP_ON = "/消息步数开";
String CMD_MESSAGE_STEP_OFF = "/消息步数关";
String CMD_STEP_STATUS = "/步数状态";
String CMD_STEP_STATUS_ALL = "/步数状态all";
String CMD_CHANGE_STEP = "/改步数 ";
String CMD_MAX_MESSAGE_STEP = "/最大消息步数 ";
String CMD_MINUTE_RANGE = "/分钟步数范围 ";
String CMD_GUARANTEED_STEP = "/保底步数 ";
String CMD_SET_TIME_TARGET = "/时间步数目标 ";
String CMD_SET_DISTRIBUTION = "/分配策略 ";

void onLoad() {
    loadConfiguration();
    
    LocalDateTime now = LocalDateTime.now();
    if (now.getDayOfYear() != currentDay) {
        resetDay(now);
    }
    
    long pending = pendingStepUpload.get();
    if (pending > 0) {
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

public boolean onClickSendBtn(String text) {
    if ("步数设置".equals(text) || "步数管理".equals(text) || "自动步数".equals(text)) {
        showMainSettingsDialog();
        return true;
    }
    return false;
}

void checkMemoryUsage() {
    long currentTime = System.currentTimeMillis();
    if (currentTime - lastMemoryCheck < 1800000) return; // 30分钟检查一次
    
    Runtime runtime = Runtime.getRuntime();
    long usedMemory = runtime.totalMemory() - runtime.freeMemory();
    
    peakMemoryUsage = Math.max(peakMemoryUsage, usedMemory);
    
    if (usedMemory > runtime.maxMemory() * 0.8) {
        clearCaches();
        System.gc();
        logToFile("内存使用过高，执行清理操作");
    }
    
    lastMemoryCheck = currentTime;
}

void clearCaches() {
    stringBuilderCache.remove();
    dateFormatCache.remove();
}

void optimizeLogStorage() {
    long currentTime = System.currentTimeMillis();
    if (currentTime - lastLogCleanup < 86400000) return; // 24小时检查一次
    
    try {
        File[] logFiles = logDir.listFiles();
        if (logFiles != null && logFiles.length > MAX_LOG_FILES) {
            java.util.Arrays.sort(logFiles, new java.util.Comparator() {
                public int compare(Object a, Object b) {
                    File f1 = (File) a;
                    File f2 = (File) b;
                    return Long.compare(f1.lastModified(), f2.lastModified());
                }
            });
            
            for (int i = 0; i < logFiles.length - MAX_LOG_FILES; i++) {
                if (logFiles[i].getName().startsWith("autostep_log")) {
                    if (logFiles[i].delete()) {
                        logToFile("删除旧日志文件: " + logFiles[i].getName());
                    }
                }
            }
        }
    } catch (Exception e) {
        logToFile("日志清理异常: " + e.getMessage());
    }
    
    lastLogCleanup = currentTime;
}

void generateResourceReport() {
    StringBuilder report = getStringBuilder();
    
    Runtime runtime = Runtime.getRuntime();
    long usedMemory = runtime.totalMemory() - runtime.freeMemory();
    
    report.append("=== 资源使用报告 ===\n");
    report.append("内存使用: ").append(usedMemory / 1024 / 1024).append("MB\n");
    report.append("峰值内存: ").append(peakMemoryUsage / 1024 / 1024).append("MB\n");
    report.append("平均执行时间: ").append(totalExecutions > 0 ? totalExecutionTime / totalExecutions : 0).append("ms\n");
    report.append("最大执行时间: ").append(maxExecutionTime).append("ms\n");
    report.append("上传失败次数: ").append(uploadFailureCount).append("\n");
    report.append("定时器状态: ").append(isTimerRunning ? "运行中" : "已停止");
    
    logToFile(report.toString());
}

void reportPerformance() {
    if (!enablePerformanceReport) {
        return;
    }
    if (totalExecutions > 0) {
        StringBuilder report = getStringBuilder();
        report.append("=== 性能报告 ===\n");
        report.append("总执行次数: ").append(totalExecutions).append("\n");
        report.append("平均执行时间: ").append(totalExecutionTime / totalExecutions).append("ms\n");
        report.append("最大执行时间: ").append(maxExecutionTime).append("ms\n");
        report.append("上传失败次数: ").append(uploadFailureCount);
        logToFile(report.toString());
    }
}

void performHealthCheck() {
    long currentTime = System.currentTimeMillis();
    if (currentTime - lastHealthCheck < 3600000) return; // 1小时检查一次
    
    StringBuilder issues = getStringBuilder();
    
    configLock.readLock().lock();
    try {
        if (minTimeStep >= maxTimeStep) {
            issues.append("步数范围配置异常;");
        }
        if (currentStep.get() > maxStep * 1.1) {
            issues.append("当前步数超出正常范围;");
        }
        if (timeStepEnabled && !isTimerRunning) {
            issues.append("定时器配置不一致;");
        }
    } finally {
        configLock.readLock().unlock();
    }
    
    if (issues.length() > 0) {
        logToFile("健康检查发现问题: " + issues.toString());
    }
    
    lastHealthCheck = currentTime;
}

void onUnLoad() {
    stopTimeStepTimer();
    stopGuaranteedStepTimer();
}

void loadConfiguration() {
    configLock.writeLock().lock();
    try {
        maxStep = validateStepRange(getLong("maxStep", 24305), 1, Long.MAX_VALUE, 24305);
        minTimeStep = (int) validateStepRange(getInt("minTimeStep", 4), 0, Integer.MAX_VALUE, 4);
        maxTimeStep = (int) validateStepRange(getInt("maxTimeStep", 12), minTimeStep + 1, Integer.MAX_VALUE, 12);
        maxMessageStep = validateStepRange(getLong("maxMessageStep", 10000), 0, Long.MAX_VALUE, 10000);
        minGuaranteedStep = validateStepRange(getLong("minGuaranteedStep", 0), 0, maxStep, 0);
        targetTimeStep = validateStepRange(getLong("targetTimeStep", 0), 0, maxStep, 0);
        distributionStrategy = getInt("distributionStrategy", STRATEGY_LINEAR);
        if (distributionStrategy != STRATEGY_EXP) distributionStrategy = STRATEGY_LINEAR;
        
        double tempExpRatio = getDouble("expRatio", 1.05);
        expRatio = Math.max(1.01, Math.min(tempExpRatio, 2.0));
        
        int tempJitterMax = getInt("jitterMax", 2);
        jitterMax = Math.max(0, Math.min(tempJitterMax, 10));
        
        timeStepEnabled = getBoolean("timeStepEnabled", false);
        messageStepEnabled = getBoolean("messageStepEnabled", false);
        logOutputEnabled = getBoolean("logOutputEnabled", false);
        isTestMode = getBoolean("isTestMode", false);
        pauseAtMaxEnabled = getBoolean("pauseAtMaxEnabled", true);
        
        currentStep.set(getLong("currentStep", 0));
        currentDay = getInt("currentDay", 0);
        linearTargetReached = getBoolean("linearTargetReached", false);
        lastExecutionTime.set(getLong("lastExecutionTime", 0));
        pendingStepUpload.set(getLong("pendingStepUpload", 0));
    } finally {
        configLock.writeLock().unlock();
    }
}

long validateStepRange(long value, long min, long max, long defaultValue) {
    if (value < min || value > max) {
        return defaultValue;
    }
    return value;
}

double getDouble(String key, double defaultValue) {
    try {
        String value = getString(key, String.valueOf(defaultValue));
        return Double.parseDouble(value);
    } catch (Exception e) {
        return defaultValue;
    }
}

StringBuilder getStringBuilder() {
    StringBuilder sb = (StringBuilder) stringBuilderCache.get();
    sb.setLength(0);
    return sb;
}

SimpleDateFormat getDateFormat() {
    return (SimpleDateFormat) dateFormatCache.get();
}

boolean updateStepStateAtomic(long expectedStep, long newStep, long newTime) {
    if (currentStep.compareAndSet(expectedStep, newStep)) {
        lastExecutionTime.set(newTime);
        persistStepState();
        return true;
    }
    return false;
}

void persistStepState() {
    putLong("currentStep", currentStep.get());
    putInt("currentDay", currentDay);
    putBoolean("linearTargetReached", linearTargetReached);
    putLong("lastExecutionTime", lastExecutionTime.get());
    putLong("pendingStepUpload", pendingStepUpload.get());
}

void resetDay(LocalDateTime now) {
    configLock.writeLock().lock();
    try {
        currentStep.set(0);
        currentDay = now.getDayOfYear();
        linearTargetReached = false;
        lastExecutionTime.set(0);
        pendingStepUpload.set(0);
        persistStepState();
    } finally {
        configLock.writeLock().unlock();
    }
    
    if (!now.toLocalTime().isBefore(ACTIVE_START_TIME)) {
        supplementStepsAfterReset(now);
    }
    
    logToFile("新的一天重置完成");
}

private long calculateEffectivePerMinute(long stepsNeeded, long remainingMinutes, boolean isExponentialStrategy, int configuredMinTimeStep, int configuredMaxTimeStep, int configuredJitterMax, double configuredExpRatio, int currentDistributionStrategy) {
    long perMinute = computeAllocation(stepsNeeded, remainingMinutes, isExponentialStrategy, configuredMinTimeStep, configuredMaxTimeStep, configuredExpRatio);

    if (configuredJitterMax > 0) {
        perMinute += (ThreadLocalRandom.current().nextInt(configuredJitterMax * 2 + 1) - configuredJitterMax);
    }

    if (currentDistributionStrategy == STRATEGY_LINEAR) {
        return Math.min(Math.max(0L, perMinute), (long)configuredMaxTimeStep);
    } else {
        return Math.min(Math.max((long)configuredMinTimeStep, perMinute), (long)configuredMaxTimeStep);
    }
}

void supplementStepsAfterReset(LocalDateTime now) {
    try {
        LocalTime currentTime = now.toLocalTime();
        
        if (isTimeInActiveRange(currentTime)) {
            long minutesFrom7AM = java.time.Duration.between(ACTIVE_START_TIME, currentTime).toMinutes();
            
            if (minutesFrom7AM > 0) {
                int stepsToAdd = calculateStepsForMissedMinutes((int) minutesFrom7AM);
                
                long current = currentStep.get();
                long newSteps = Math.min(current + stepsToAdd, maxStep);
                long currentTimeMillis = System.currentTimeMillis();
                
                if (updateStepStateAtomic(current, newSteps, currentTimeMillis)) {
                    safeUploadDeviceStep(newSteps);
                    
                    StringBuilder sb = getStringBuilder();
                    sb.append("重置后补充步数: ").append(minutesFrom7AM)
                      .append("分钟 -> +").append(stepsToAdd)
                      .append(" = ").append(newSteps);
                    logToFile(sb.toString());
                }
            }
        }
    } catch (Exception e) {
        logToFile("重置后补充步数异常: " + e.getMessage());
    }
}

boolean isTimeInActiveRange(LocalTime time) {
    return time.compareTo(ACTIVE_START_TIME) >= 0 && time.compareTo(ACTIVE_END_TIME) < 0;
}

int calculateStepsForMissedMinutes(int missedMinutes) {
    configLock.readLock().lock();
    try {
        if (targetTimeStep > 0) {
            return calculateTargetBasedMissedSteps(missedMinutes);
        } else {
            return calculateRandomMissedSteps(missedMinutes);
        }
    } finally {
        configLock.readLock().unlock();
    }
}

int calculateTargetBasedMissedSteps(int missedMinutes) {
    configLock.readLock().lock();
    try {
        long stepsNeeded = targetTimeStep;
        long remainingMinutes = ACTIVE_MINUTES;
        
        long perMinute = calculateEffectivePerMinute(
            stepsNeeded, remainingMinutes, 
            distributionStrategy == STRATEGY_EXP, 
            minTimeStep, maxTimeStep, jitterMax, expRatio, distributionStrategy
        );
        
        long stepsToAdd = Math.min(perMinute * missedMinutes, stepsNeeded);
        return (int) Math.min(stepsToAdd, maxStep);
    } finally {
        configLock.readLock().unlock();
    }
}

int calculateRandomMissedSteps(int missedMinutes) {
    ThreadLocalRandom random = ThreadLocalRandom.current();
    long totalSteps = 0;
    int min = Math.max(0, minTimeStep);
    int range = Math.max(1, maxTimeStep - min + 1);
    
    for (int i = 0; i < missedMinutes && totalSteps < maxStep; i++) {
        totalSteps += min + random.nextInt(range);
    }
    return (int) Math.min(totalSteps, maxStep);
}

long applyJitter(long baseValue, int jitterMax, int minValue, int maxValue) {
    if (jitterMax <= 0) return baseValue;
    
    ThreadLocalRandom random = ThreadLocalRandom.current();
    int jitter = random.nextInt(jitterMax * 2 + 1) - jitterMax;
    long result = baseValue + jitter;
    return Math.max(minValue, Math.min(result, maxValue));
}

void startTimeStepTimer() {
    synchronized (this) {
        if (isTimerRunning) {
            return;
        }
        shutdownExecutor(scheduledExecutor);
        scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
        isTimerRunning = true;
    }
    
    scheduledExecutor.scheduleAtFixedRate(new Runnable() {
        public void run() {
            executeTimeStepTask();
        }
    }, 0, 1, TimeUnit.MINUTES);
    
    logToFile("时间步数定时器启动");
}

void executeTimeStepTask() {
    if (!timeTaskRunning.compareAndSet(false, true)) {
        return;
    }
    
    long startTime = System.currentTimeMillis();
    
    try {
        if (isRestrictedTime()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.getDayOfYear() != currentDay) {
            resetDay(now);
            return;
        }

        long currentTime = System.currentTimeMillis();
        if (!checkAndExecuteMissedTasks(currentTime)) {
            executeNormalTimeStep(now, currentTime);
        }
        performHealthCheck();
        checkMemoryUsage();
        optimizeLogStorage();
    } catch (Exception e) {
        timerErrorCount++;
        logToFile("定时任务异常: " + e.getMessage());
    } finally {
        long duration = System.currentTimeMillis() - startTime;
        totalExecutions++;
        totalExecutionTime += duration;
        maxExecutionTime = Math.max(maxExecutionTime, duration);
        
        if (startTime - lastPerformanceReport > 3600000) {
            reportPerformance();
            lastPerformanceReport = startTime;
        }
        
        timeTaskRunning.set(false);
    }
}

void executeNormalTimeStep(LocalDateTime now, long currentTime) {
    if (isPluginStepPausedAtMax()) {
        return;
    }
    int step = calculateCurrentStep(now);
    if (step <= 0) return;
    
    long current = currentStep.get();
    long newSteps = current + step;
    
    configLock.readLock().lock();
    try {
        newSteps = Math.min(newSteps, maxStep);
        if (targetTimeStep > 0) {
            newSteps = Math.min(newSteps, targetTimeStep);
        }
    } finally {
        configLock.readLock().unlock();
    }
    
    if (updateStepStateAtomic(current, newSteps, currentTime)) {
        safeUploadDeviceStep(newSteps);
        
        StringBuilder sb = getStringBuilder();
        sb.append("时间步数: +").append(step).append(" -> ").append(newSteps);
        logToFile(sb.toString());
    }
}

int calculateCurrentStep(LocalDateTime now) {
    configLock.readLock().lock();
    try {
        if (targetTimeStep > 0) {
            return calculateTargetBasedStep(now);
        } else {
            return generateRandomStep();
        }
    } finally {
        configLock.readLock().unlock();
    }
}

int calculateTargetBasedStep(LocalDateTime now) {
    LocalDateTime cutoff = now.withHour(TARGET_STEP_CUTOFF_HOUR)
                             .withMinute(TARGET_STEP_CUTOFF_MINUTE)
                             .withSecond(0)
                             .withNano(0);
    
    if (!now.isBefore(cutoff)) {
        handleTargetCutoff();
        return 0;
    }
    
    long minutesRemaining = java.time.Duration.between(now, cutoff).toMinutes();
    if (minutesRemaining <= 0) {
        handleTargetCutoff();
        return 0;
    }
    
    long stepsNeeded = targetTimeStep - currentStep.get();
    if (stepsNeeded <= 0) {
        linearTargetReached = true;
        putBoolean("linearTargetReached", true);
        return 0;
    }
    
    return calculateStepForTarget(stepsNeeded, minutesRemaining);
}

void handleTargetCutoff() {
    long current = currentStep.get();
    if (current < targetTimeStep) {
        long newSteps = Math.min(targetTimeStep, maxStep);
        long currentTimeMillis = System.currentTimeMillis();
        
        if (updateStepStateAtomic(current, newSteps, currentTimeMillis)) {
            safeUploadDeviceStep(newSteps);
            
            StringBuilder sb = getStringBuilder();
            sb.append("到达").append(TARGET_STEP_CUTOFF_HOUR).append(":")
              .append(TARGET_STEP_CUTOFF_MINUTE).append("，设置时间目标步数 -> ").append(newSteps);
            logToFile(sb.toString());
        }
    } else {
        StringBuilder sb = getStringBuilder();
        sb.append("到达").append(TARGET_STEP_CUTOFF_HOUR).append(":")
          .append(TARGET_STEP_CUTOFF_MINUTE).append("，目标已达成，无需增加");
        logToFile(sb.toString());
    }
    
    linearTargetReached = true;
    putBoolean("linearTargetReached", true);
}

// 修改后的 calculateStepForTarget 方法
int calculateStepForTarget(long stepsNeeded, long minutesRemaining) {
    long perMinute = calculateEffectivePerMinute(
        stepsNeeded, minutesRemaining, 
        distributionStrategy == STRATEGY_EXP, 
        minTimeStep, maxTimeStep, jitterMax, expRatio, distributionStrategy
    );
    
    return (int) Math.min(perMinute, stepsNeeded);
}

int generateRandomStep() {
    ThreadLocalRandom random = ThreadLocalRandom.current();
    int min = Math.max(0, minTimeStep);
    int max = Math.max(min + 1, maxTimeStep);
    return min + random.nextInt(max - min + 1);
}

// 修改后的 computeAllocation 方法
long computeAllocation(long stepsNeeded, long remainingMinutes, boolean exponential, int minPerMinute, int maxPerMinute, double currentExpRatio) {
    if (remainingMinutes <= 0 || stepsNeeded <= 0) {
        return minPerMinute;
    }
    if (!exponential) {
        return (long) Math.ceil((double) stepsNeeded / remainingMinutes);
    } else {
        double r = Math.max(1.01, currentExpRatio);
        int M = (int) remainingMinutes;
        double totalWeight;
        try {
            totalWeight = (Math.pow(r, M) - 1.0) / (r - 1.0);
        } catch (Exception e) {
            totalWeight = M;
        }
        double curWeight = Math.pow(r, M - 1);
        double frac = curWeight / Math.max(1e-9, totalWeight);
        long val = (long) Math.ceil(stepsNeeded * frac);
        long linear = (long) Math.ceil((double) stepsNeeded / remainingMinutes);
        return Math.min(Math.max(val, linear), maxPerMinute);
    }
}

void stopTimeStepTimer() {
    synchronized (this) {
        if (scheduledExecutor != null) {
            shutdownExecutor(scheduledExecutor);
            scheduledExecutor = null;
        }
        isTimerRunning = false;
    }
    logToFile("时间步数定时器停止");
}

void shutdownExecutor(ScheduledExecutorService executor) {
    if (executor != null && !executor.isShutdown()) {
        try {
            executor.shutdown();
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    logToFile("线程池无法正常关闭");
                }
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
            logToFile("线程池关闭被中断");
        }
    }
}

void startGuaranteedStepTimer() {
    stopGuaranteedStepTimer();
    
    guaranteedStepExecutor = Executors.newSingleThreadScheduledExecutor();
    
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime targetTime = now.withHour(ACTIVE_END_HOUR).withMinute(ACTIVE_END_MINUTE).withSecond(0).withNano(0);
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
        shutdownExecutor(guaranteedStepExecutor);
        guaranteedStepExecutor = null;
    }
    logToFile("保底步数定时器停止");
}

void executeGuaranteedStepCheck() {
    LocalDateTime now = LocalDateTime.now();
    if (now.getDayOfYear() != currentDay) {
        resetDay(now);
    }
    
    configLock.readLock().lock();
    long guaranteedStep;
    try {
        guaranteedStep = minGuaranteedStep + ThreadLocalRandom.current().nextInt(GUARANTEED_STEP_RANDOM_RANGE);
        if (guaranteedStep > maxStep) {
            guaranteedStep = maxStep;
        }
    } finally {
        configLock.readLock().unlock();
    }
    
    long current = currentStep.get();
    if (current < guaranteedStep) {
        if (updateStepStateAtomic(current, guaranteedStep, System.currentTimeMillis())) {
            safeUploadDeviceStep(guaranteedStep);
            logToFile("保底步数生效: " + guaranteedStep);
        }
    }
}

boolean checkAndExecuteMissedTasks(long currentTime) {
    long last = lastExecutionTime.get();

    if (last == 0) {
        return false;
    }

    long timeDiff = currentTime - last;
    if (timeDiff <= TIMEOUT_THRESHOLD) {
        return false;
    }

    int missedMinutes = (int) (timeDiff / MINUTE_IN_MILLIS);
    int maxMissedMinutes = MAX_MISSED_HOURS * 60;
    missedMinutes = Math.min(missedMinutes, maxMissedMinutes);

    int validMinutesCounted = 0;
    for (int i = 0; i < missedMinutes; i++) {
        long minuteTime = last + (long) i * MINUTE_IN_MILLIS;
        java.time.Instant instant = java.time.Instant.ofEpochMilli(minuteTime);
        LocalDateTime minuteDateTime = LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault());
        if (isActiveMinute(minuteDateTime.toLocalTime())) {
            validMinutesCounted++;
        }
    }

    if (validMinutesCounted == 0) {
        StringBuilder sb = getStringBuilder();
        sb.append("补充缺失: ").append(missedMinutes)
          .append("分钟(实际有效:0分钟) +0 -> ").append(currentStep.get());
        logToFile(sb.toString());
        return false;
    }

    return processMissedMinutes(missedMinutes, validMinutesCounted, last, currentTime);
}

boolean processMissedMinutes(int missedMinutes, int validMinutesCounted, long lastTime, long currentTime) {
    if (isPluginStepPausedAtMax()) {
        return false;
    }
    int totalAddedSteps = 0;
    int validMinutesProcessed = 0;

    for (int i = 0; i < missedMinutes && validMinutesProcessed < validMinutesCounted; i++) {
        long minuteTime = lastTime + (long) i * MINUTE_IN_MILLIS;
        java.time.Instant instant = java.time.Instant.ofEpochMilli(minuteTime);
        LocalDateTime minuteDateTime = LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault());
        LocalTime minuteTimeOnly = minuteDateTime.toLocalTime();
 
        if (isActiveMinute(minuteTimeOnly)) {
            if (isPluginStepPausedAtMax()) {
                logToFile("补充缺失: 已达最大步数，跳过插件补偿");
                return true;
            }
            int step = calculateMissedMinuteStep(validMinutesCounted, validMinutesProcessed);
            if (step <= 0) return true;
            
            long current = currentStep.get();
            long newSteps = current + step;
            
            configLock.readLock().lock();
            try {
                newSteps = Math.min(newSteps, maxStep);
            } finally {
                configLock.readLock().unlock();
            }
            
            if (updateStepStateAtomic(current, newSteps, currentTime)) {
                totalAddedSteps += step;
                if (newSteps >= maxStep) {
                    safeUploadDeviceStep(newSteps);
                    logToFile("补充缺失: 达到最大步数 -> " + newSteps);
                    return true;
                }
            }
            validMinutesProcessed++;
        }
    }

    if (totalAddedSteps > 0) {
        safeUploadDeviceStep(currentStep.get());
        StringBuilder sb = getStringBuilder();
        sb.append("补充缺失: ").append(missedMinutes)
          .append("分钟(实际有效:").append(validMinutesCounted)
          .append("分钟) +").append(totalAddedSteps)
          .append(" -> ").append(currentStep.get());
        logToFile(sb.toString());
        return true;
    }

    return false;
}

int calculateMissedMinuteStep(int validMinutesCounted, int validMinutesProcessed) {
    configLock.readLock().lock();
    try {
        int min = Math.max(0, minTimeStep); 
        int max = Math.max(min + 1, maxTimeStep);
        
        if (targetTimeStep > 0) {
            int remainingValid = validMinutesCounted - validMinutesProcessed;
            long stepsNeeded = targetTimeStep - currentStep.get();
            if (stepsNeeded <= 0) {
                linearTargetReached = true;
                putBoolean("linearTargetReached", linearTargetReached);
                return 0;
            }
            
            long perMinute = calculateEffectivePerMinute(
                stepsNeeded, Math.max(1, remainingValid), 
                distributionStrategy == STRATEGY_EXP, 
                minTimeStep, maxTimeStep, jitterMax, expRatio, distributionStrategy
            );
            
            return (int) Math.min(perMinute, stepsNeeded);
        } else {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            return min + random.nextInt(max - min + 1);
        }
    } finally {
        configLock.readLock().unlock();
    }
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
    return time.compareTo(ACTIVE_START_TIME) >= 0 && time.compareTo(ACTIVE_END_TIME) < 0;
}

void enableTimeStep(boolean enable) {
    configLock.writeLock().lock();
    try {
        timeStepEnabled = enable;
        putBoolean("timeStepEnabled", timeStepEnabled);
    } finally {
        configLock.writeLock().unlock();
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
    configLock.writeLock().lock();
    try {
        messageStepEnabled = enable;
        putBoolean("messageStepEnabled", messageStepEnabled);
    } finally {
        configLock.writeLock().unlock();
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
    if (isPluginStepPausedAtMax()) {
        return;
    }
    
    LocalDateTime now = LocalDateTime.now();
    if (currentDay == 0) {
        loadConfiguration();
    }
    if (now.getDayOfYear() != currentDay) {
        resetDay(now);
    }
    
    ThreadLocalRandom random = ThreadLocalRandom.current();
    int base = 50 + random.nextInt(100);
    long current = currentStep.get();
    long inc = calculateStepIncrease(current, base);
    
    configLock.readLock().lock();
    long newSteps;
    try {
        newSteps = Math.min(current + inc, maxStep);
    } finally {
        configLock.readLock().unlock();
    }
    
    if (updateStepStateAtomic(current, newSteps, System.currentTimeMillis())) {
        safeUploadDeviceStep(newSteps);
        
        StringBuilder sb = getStringBuilder();
        sb.append("消息步数: +").append(inc).append(" -> ").append(newSteps);
        logToFile(sb.toString());
    }
}

long calculateStepIncrease(long currentStep, int baseStep) {
    for (int i = 0; i < STEP_THRESHOLDS.length; i++) {
        if (currentStep < STEP_THRESHOLDS[i]) {
            return (long) (baseStep * STEP_MULTIPLIERS[i]);
        }
    }
    return (long) (baseStep * STEP_MULTIPLIERS[STEP_MULTIPLIERS.length - 1]);
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

    configLock.readLock().lock();
    boolean msgEnabled;
    try {
        msgEnabled = messageStepEnabled;
    } finally {
        configLock.readLock().unlock();
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
    configLock.readLock().lock();
    long maxStepValue;
    try {
        maxStepValue = maxStep;
    } finally {
        configLock.readLock().unlock();
    }
    
    if (step < 0 || step > maxStepValue) {
        sendText(talker, "步数必须在0-" + maxStepValue + "之间");
        return;
    }
    
    long current = currentStep.get();
    if (updateStepStateAtomic(current, step, System.currentTimeMillis())) {
        safeUploadDeviceStep(step);
        sendText(talker, "步数已修改为: " + step);
        logToFile("手动修改步数: " + step);
    }
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
    
    configLock.writeLock().lock();
    try {
        maxMessageStep = step;
        putLong("maxMessageStep", maxMessageStep);
    } finally {
        configLock.writeLock().unlock();
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
        
        configLock.writeLock().lock();
        try {
            minTimeStep = min;
            maxTimeStep = max;
            putInt("minTimeStep", minTimeStep);
            putInt("maxTimeStep", maxTimeStep);
        } finally {
            configLock.writeLock().unlock();
        }
        
        sendText(talker, "每分钟步数范围已修改为: " + min + "-" + max);
        sendText(talker, "时间步数范围: " + (min * ACTIVE_MINUTES) + "-" + (max * ACTIVE_MINUTES));
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
    
    configLock.writeLock().lock();
    try {
        if (step > maxStep) {
            sendText(talker, "保底步数不能大于最大步数 " + maxStep);
            return;
        }
        minGuaranteedStep = step;
        putLong("minGuaranteedStep", minGuaranteedStep);
    } finally {
        configLock.writeLock().unlock();
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
    
    configLock.writeLock().lock();
    try {
        if (step < 0 || step > maxStep) {
            sendText(talker, "时间目标步数必须在0-" + maxStep + "之间");
            return;
        }
        targetTimeStep = step;
        putLong("targetTimeStep", targetTimeStep);
        linearTargetReached = false;
        putBoolean("linearTargetReached", linearTargetReached);
    } finally {
        configLock.writeLock().unlock();
    }
    
    if (step > 0) {
        sendText(talker, "时间目标步数已设置为: " + step);
        logToFile("设置时间目标步数: " + step);
    } else {
        sendText(talker, "已取消时间目标步数");
        logToFile("取消时间目标步数");
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
    
    configLock.writeLock().lock();
    try {
        distributionStrategy = strategy;
        putInt("distributionStrategy", distributionStrategy);
    } finally {
        configLock.writeLock().unlock();
    }
    
    sendText(talker, "分配策略已设置为: " + (strategy == STRATEGY_EXP ? "指数" : "线性"));
    logToFile("设置分配策略: " + distributionStrategy);
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

void showStepStatus(String talker) {
    long curStep = currentStep.get();
    
    configLock.readLock().lock();
    long curMinG;
    boolean tEnabled;
    boolean mEnabled;
    int minTs;
    int maxTs;
    long curMaxMessageStep;
    long curTarget;
    boolean curLinearReached;
    try {
        curMinG = minGuaranteedStep;
        tEnabled = timeStepEnabled;
        mEnabled = messageStepEnabled;
        minTs = minTimeStep;
        maxTs = maxTimeStep;
        curMaxMessageStep = maxMessageStep;
        curTarget = targetTimeStep;
        curLinearReached = linearTargetReached;
    } finally {
        configLock.readLock().unlock();
    }

    String targetInfo = curTarget > 0 ? (String.valueOf(curTarget) + (curLinearReached ? "（已达）" : "（未达）")) : "未设置";

    if (!tEnabled && !mEnabled) {
        String status = "步数增加功能未启用，请启用时间步数或消息步数功能。";
        sendText(talker, status);
    } else if (mEnabled) {
        long progress = (curMaxMessageStep == 0) ? 0 : (curStep * 100 / curMaxMessageStep);
        StringBuilder status = getStringBuilder();
        status.append("当前步数: ").append(curStep).append("\n")
              .append("今日目标: ").append(curMaxMessageStep).append("\n")
              .append("进度: ").append(progress).append("%\n")
              .append("保底步数: ").append(curMinG).append("\n")
              .append("最大消息步数: ").append(curMaxMessageStep).append("\n")
              .append("时间目标步数: ").append(targetInfo);
        sendText(talker, status.toString());
    } else if (tEnabled) {
        StringBuilder status = getStringBuilder();
        status.append("当前步数: ").append(curStep).append("\n")
              .append("保底步数: ").append(curMinG).append("\n")
              .append("时间步数范围: ").append(minTs * ACTIVE_MINUTES).append("-").append(maxTs * ACTIVE_MINUTES).append("\n")
              .append("每分钟步数范围: ").append(minTs).append("-").append(maxTs).append("\n")
              .append("时间目标步数: ").append(targetInfo);
        sendText(talker, status.toString());
    }

    logToFile("查询步数状态");
}

void showStepStatusAll(String talker) {
    long curStep = currentStep.get();
    
    configLock.readLock().lock();
    long curMaxStep;
    long curMinG;
    boolean tEnabled;
    boolean mEnabled;
    int minTs;
    int maxTs;
    long curMaxMessageStep;
    long curTarget;
    boolean curLinearReached;
    try {
        curMaxStep = maxStep;
        curMinG = minGuaranteedStep;
        tEnabled = timeStepEnabled;
        mEnabled = messageStepEnabled;
        minTs = minTimeStep;
        maxTs = maxTimeStep;
        curMaxMessageStep = maxMessageStep;
        curTarget = targetTimeStep;
        curLinearReached = linearTargetReached;
    } finally {
        configLock.readLock().unlock();
    }

    String targetInfo = curTarget > 0 ? (String.valueOf(curTarget) + (curLinearReached ? "（已达）" : "（未达）")) : "未设置";

    long progress = curMaxStep == 0 ? 0 : (curStep * 100 / curMaxStep);
    StringBuilder status = getStringBuilder();
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
        status.append("\n时间步数范围: ").append(minTs * ACTIVE_MINUTES).append("-").append(maxTs * ACTIVE_MINUTES);
        status.append("\n每分钟步数范围: ").append(minTs).append("-").append(maxTs);
    }
    if (mEnabled) {
        status.append("\n消息步数最大值: ").append(curMaxMessageStep);
    }

    sendText(talker, status.toString());
    logToFile("查询步数状态all");
}

void safeUploadDeviceStep(long step) {
    if (isPluginStepPausedAtMax()) {
        pendingStepUpload.set(0);
        putLong("pendingStepUpload", 0);
        logToFile("已达最大步数，跳过插件上传，保留微信自然增长");
        return;
    }
    try {
        uploadDeviceStep(step);
        long pending = pendingStepUpload.get();
        if (pending > 0 && pending != step) {
            uploadPendingSteps();
            pendingStepUpload.set(0);
            putLong("pendingStepUpload", 0);
        }
        
        if (uploadFailureCount > 0) {
            logToFile("上传恢复正常，之前失败次数: " + uploadFailureCount);
            uploadFailureCount = 0;
        }
        
    } catch (Exception e) {
        uploadFailureCount++;
        
        StringBuilder sb = getStringBuilder();
        sb.append("步数上传失败: ").append(e.getMessage());
        
        if (uploadFailureCount >= 5) {
            sb.append(" (连续失败").append(uploadFailureCount).append("次)");
        }
        logToFile(sb.toString());
        
        pendingStepUpload.set(step);
        putLong("pendingStepUpload", step);
        
        sb.setLength(0);
        sb.append("记录待上传步数: ").append(step);
        logToFile(sb.toString());
    }
}

boolean isPluginStepPausedAtMax() {
    configLock.readLock().lock();
    try {
        return pauseAtMaxEnabled && currentStep.get() >= maxStep;
    } finally {
        configLock.readLock().unlock();
    }
}

void uploadPendingSteps() {
    long pending = pendingStepUpload.get();
    if (pending <= 0) return;
    
    try {
        uploadDeviceStep(pending);
        pendingStepUpload.set(0);
        putLong("pendingStepUpload", 0);
        
        StringBuilder sb = getStringBuilder();
        sb.append("补传步数成功: ").append(pending);
        logToFile(sb.toString());
    } catch (Exception e) {
        StringBuilder sb = getStringBuilder();
        sb.append("补传步数失败: ").append(e.getMessage());
        logToFile(sb.toString());
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
        
        SimpleDateFormat dateFormat = getDateFormat();
        String timestamp = dateFormat.format(new Date());
        StringBuilder newLogMessage = getStringBuilder();
        newLogMessage.append("[").append(timestamp).append("] ").append(message).append("\n");
        
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
        
        StringBuilder finalContent = new StringBuilder();
        finalContent.append(newLogMessage.toString()).append(existingContent);
        
        FileWriter writer = new FileWriter(logFile, false);
        writer.write(finalContent.toString());
        writer.close();
        
    } catch (IOException e) {
        System.err.println("日志写入失败: " + e.getMessage());
    }
}

private void showMainSettingsDialog() {
    ScrollView scrollView = new ScrollView(getTopActivity());
    LinearLayout root = new LinearLayout(getTopActivity());
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(32, 32, 32, 32);
    root.setBackgroundColor(Color.parseColor("#FAFBF9"));
    scrollView.addView(root);

    root.addView(uiSectionTitle("📊 运行状态"));

    long curStep = currentStep.get();
    configLock.readLock().lock();
    String statusText;
    try {
        long progress = maxStep == 0 ? 0 : (curStep * 100 / maxStep);
        StringBuilder sb = new StringBuilder();
        sb.append("当前步数: ").append(curStep).append(" / ").append(maxStep)
          .append(" (").append(progress).append("%)\n");
        sb.append("时间步数: ").append(timeStepEnabled ? "✅ 已开启" : "❌ 已关闭").append("\n");
        sb.append("消息步数: ").append(messageStepEnabled ? "✅ 已开启" : "❌ 已关闭").append("\n");
        sb.append("到上限自动暂停: ").append(pauseAtMaxEnabled ? "✅ 已开启" : "❌ 已关闭").append("\n");
        sb.append("保底步数: ").append(minGuaranteedStep).append("\n");
        if (targetTimeStep > 0) {
            sb.append("时间目标: ").append(targetTimeStep)
              .append(linearTargetReached ? " (已达成)" : " (进行中)");
        } else {
            sb.append("时间目标: 未设置");
        }
        statusText = sb.toString();
    } finally {
        configLock.readLock().unlock();
    }

    final TextView statusView = new TextView(getTopActivity());
    statusView.setText(statusText);
    statusView.setTextSize(13);
    statusView.setTextColor(Color.parseColor("#333333"));
    statusView.setPadding(24, 16, 24, 16);
    GradientDrawable statusBg = new GradientDrawable();
    statusBg.setCornerRadius(16);
    statusBg.setColor(Color.parseColor("#F0F7F0"));
    statusBg.setStroke(1, Color.parseColor("#C8E6C9"));
    statusView.setBackground(statusBg);
    LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    );
    statusParams.setMargins(0, 8, 0, 16);
    statusView.setLayoutParams(statusParams);
    root.addView(statusView);

    root.addView(uiSectionTitle("🔧 功能开关"));

    final Button timeToggle = new Button(getTopActivity());
    uiApplyToggleStyle(timeToggle, "⏱️ 时间步数", timeStepEnabled);
    timeToggle.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            boolean newState = !timeStepEnabled;
            enableTimeStep(newState);
            uiApplyToggleStyle(timeToggle, "⏱️ 时间步数", newState);
        }
    });
    root.addView(timeToggle);

    final Button msgToggle = new Button(getTopActivity());
    uiApplyToggleStyle(msgToggle, "💬 消息步数", messageStepEnabled);
    msgToggle.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            boolean newState = !messageStepEnabled;
            enableMessageStep(newState);
            uiApplyToggleStyle(msgToggle, "💬 消息步数", newState);
        }
    });
    root.addView(msgToggle);

    final Button logToggle = new Button(getTopActivity());
    uiApplyToggleStyle(logToggle, "📝 日志输出", logOutputEnabled);
    logToggle.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            configLock.writeLock().lock();
            try {
                logOutputEnabled = !logOutputEnabled;
                putBoolean("logOutputEnabled", logOutputEnabled);
            } finally {
                configLock.writeLock().unlock();
            }
            uiApplyToggleStyle(logToggle, "📝 日志输出", logOutputEnabled);
            toast(logOutputEnabled ? "日志输出已开启" : "日志输出已关闭");
        }
    });
    root.addView(logToggle);

    final Button testToggle = new Button(getTopActivity());
    uiApplyToggleStyle(testToggle, "🧪 测试模式", isTestMode);
    testToggle.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            configLock.writeLock().lock();
            try {
                isTestMode = !isTestMode;
                putBoolean("isTestMode", isTestMode);
            } finally {
                configLock.writeLock().unlock();
            }
            uiApplyToggleStyle(testToggle, "🧪 测试模式", isTestMode);
            toast(isTestMode ? "测试模式已开启" : "测试模式已关闭");
        }
    });
    root.addView(testToggle);

    final Button pauseAtMaxToggle = new Button(getTopActivity());
    uiApplyToggleStyle(pauseAtMaxToggle, "🛑 上限后自动暂停", pauseAtMaxEnabled);
    pauseAtMaxToggle.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            configLock.writeLock().lock();
            try {
                pauseAtMaxEnabled = !pauseAtMaxEnabled;
                putBoolean("pauseAtMaxEnabled", pauseAtMaxEnabled);
            } finally {
                configLock.writeLock().unlock();
            }
            uiApplyToggleStyle(pauseAtMaxToggle, "🛑 上限后自动暂停", pauseAtMaxEnabled);
            toast(pauseAtMaxEnabled ? "已开启：到达最大步数后暂停插件上传/增长" : "已关闭：到达最大步数后保持封顶上传");
        }
    });
    root.addView(pauseAtMaxToggle);

    root.addView(uiSectionTitle("⚙️ 参数设置"));
    root.addView(uiHintText("👇 点击下方选项可修改对应参数"));

    final Button maxStepBtn = uiSettingButton("📈 最大步数: " + maxStep);
    final Button rangeBtn = uiSettingButton("📏 每分钟步数范围: " + minTimeStep + " - " + maxTimeStep);
    final Button maxMsgBtn = uiSettingButton("💬 最大消息步数: " + maxMessageStep);
    final Button guaranteedBtn = uiSettingButton("🛡️ 保底步数: " + minGuaranteedStep);
    final Button targetBtn = uiSettingButton("🎯 时间步数目标: " + (targetTimeStep > 0 ? String.valueOf(targetTimeStep) : "未设置"));
    final Button strategyBtn = uiSettingButton("📊 分配策略: " + (distributionStrategy == STRATEGY_EXP ? "指数" : "线性"));
    final Runnable[] refreshMainUiHolder = new Runnable[1];
    refreshMainUiHolder[0] = new Runnable() {
        public void run() {
            long curStep = currentStep.get();
            configLock.readLock().lock();
            try {
                long progress = maxStep == 0 ? 0 : (curStep * 100 / maxStep);
                StringBuilder sb = new StringBuilder();
                sb.append("当前步数: ").append(curStep).append(" / ").append(maxStep)
                  .append(" (").append(progress).append("%)\n");
                sb.append("时间步数: ").append(timeStepEnabled ? "✅ 已开启" : "❌ 已关闭").append("\n");
                sb.append("消息步数: ").append(messageStepEnabled ? "✅ 已开启" : "❌ 已关闭").append("\n");
                sb.append("到上限自动暂停: ").append(pauseAtMaxEnabled ? "✅ 已开启" : "❌ 已关闭").append("\n");
                sb.append("保底步数: ").append(minGuaranteedStep).append("\n");
                if (targetTimeStep > 0) {
                    sb.append("时间目标: ").append(targetTimeStep)
                      .append(linearTargetReached ? " (已达成)" : " (进行中)");
                } else {
                    sb.append("时间目标: 未设置");
                }
                statusView.setText(sb.toString());
                maxStepBtn.setText("📈 最大步数: " + maxStep);
                rangeBtn.setText("📏 每分钟步数范围: " + minTimeStep + " - " + maxTimeStep);
                maxMsgBtn.setText("💬 最大消息步数: " + maxMessageStep);
                guaranteedBtn.setText("🛡️ 保底步数: " + minGuaranteedStep);
                targetBtn.setText("🎯 时间步数目标: " + (targetTimeStep > 0 ? String.valueOf(targetTimeStep) : "未设置"));
                strategyBtn.setText("📊 分配策略: " + (distributionStrategy == STRATEGY_EXP ? "指数" : "线性"));
            } finally {
                configLock.readLock().unlock();
            }
        }
    };
    maxStepBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            showEditNumberDialog("📈 最大步数", "设置每日最大步数上限", String.valueOf(maxStep), new NumberEditCallback() {
                public void onConfirm(long value) {
                    if (value < 1) { toast("最大步数必须大于0"); return; }
                    configLock.writeLock().lock();
                    try {
                        maxStep = value;
                        putLong("maxStep", maxStep);
                    } finally {
                        configLock.writeLock().unlock();
                    }
                    toast("最大步数已设为: " + value);
                }
            }, refreshMainUiHolder[0]);
        }
    });
    root.addView(maxStepBtn);

    rangeBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            showEditRangeDialog(refreshMainUiHolder[0]);
        }
    });
    root.addView(rangeBtn);

    maxMsgBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            showEditNumberDialog("💬 最大消息步数", "设置消息触发的步数上限", String.valueOf(maxMessageStep), new NumberEditCallback() {
                public void onConfirm(long value) {
                    if (value < 0) { toast("值不能为负数"); return; }
                    configLock.writeLock().lock();
                    try {
                        maxMessageStep = value;
                        putLong("maxMessageStep", maxMessageStep);
                    } finally {
                        configLock.writeLock().unlock();
                    }
                    toast("最大消息步数已设为: " + value);
                }
            }, refreshMainUiHolder[0]);
        }
    });
    root.addView(maxMsgBtn);

    guaranteedBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            showEditNumberDialog("🛡️ 保底步数", "22:50时若步数不足将自动补到此值\n设为 0 表示关闭", String.valueOf(minGuaranteedStep), new NumberEditCallback() {
                public void onConfirm(long value) {
                    if (value < 0) { toast("值不能为负数"); return; }
                    configLock.writeLock().lock();
                    try {
                        if (value > maxStep) {
                            toast("不能大于最大步数 " + maxStep);
                            return;
                        }
                        minGuaranteedStep = value;
                        putLong("minGuaranteedStep", minGuaranteedStep);
                    } finally {
                        configLock.writeLock().unlock();
                    }
                    if (value > 0) {
                        startGuaranteedStepTimer();
                    } else {
                        stopGuaranteedStepTimer();
                    }
                    toast("保底步数已设为: " + value);
                }
            }, refreshMainUiHolder[0]);
        }
    });
    root.addView(guaranteedBtn);

    targetBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            showEditNumberDialog("🎯 时间步数目标", "在截止时间前需达到的步数\n设为 0 表示取消", String.valueOf(targetTimeStep), new NumberEditCallback() {
                public void onConfirm(long value) {
                    if (value < 0) { toast("值不能为负数"); return; }
                    configLock.writeLock().lock();
                    try {
                        if (value > maxStep) {
                            toast("不能大于最大步数 " + maxStep);
                            return;
                        }
                        targetTimeStep = value;
                        putLong("targetTimeStep", targetTimeStep);
                        linearTargetReached = false;
                        putBoolean("linearTargetReached", false);
                    } finally {
                        configLock.writeLock().unlock();
                    }
                    toast(value > 0 ? "时间目标已设为: " + value : "已取消时间目标");
                }
            }, refreshMainUiHolder[0]);
        }
    });
    root.addView(targetBtn);

    strategyBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            showStrategySelectionDialog(refreshMainUiHolder[0]);
        }
    });
    root.addView(strategyBtn);

    root.addView(uiSectionTitle("🔄 快捷操作"));

    Button manualStepBtn = new Button(getTopActivity());
    manualStepBtn.setText("✏️ 手动修改步数");
    uiStyleButton(manualStepBtn);
    manualStepBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            showEditNumberDialog("✏️ 手动修改步数", "将当前步数设置为指定值", String.valueOf(currentStep.get()), new NumberEditCallback() {
                public void onConfirm(long value) {
                    if (value < 0 || value > maxStep) {
                        toast("步数必须在 0-" + maxStep + " 之间");
                        return;
                    }
                    long current = currentStep.get();
                    if (updateStepStateAtomic(current, value, System.currentTimeMillis())) {
                        safeUploadDeviceStep(value);
                        toast("步数已修改为: " + value);
                        logToFile("UI手动修改步数: " + value);
                    }
                }
            }, refreshMainUiHolder[0]);
        }
    });
    root.addView(manualStepBtn);

    Button resetBtn = new Button(getTopActivity());
    resetBtn.setText("🔄 重置今日步数");
    uiStyleButton(resetBtn);
    GradientDrawable resetBg = (GradientDrawable) resetBtn.getBackground();
    resetBg.setColor(Color.parseColor("#FFF3E0"));
    resetBg.setStroke(3, Color.parseColor("#FFE0B2"));
    resetBtn.setTextColor(Color.parseColor("#E65100"));
    resetBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            AlertDialog.Builder confirm = new AlertDialog.Builder(getTopActivity());
            confirm.setTitle("重置确认");
            confirm.setMessage("确定要重置今日步数为 0 吗？\n此操作不可撤销。");
            confirm.setPositiveButton("确定重置", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    resetDay(LocalDateTime.now());
                    safeUploadDeviceStep(0);
                    toast("今日步数已重置");
                    refreshMainUiHolder[0].run();
                }
            });
            confirm.setNegativeButton("取消", null);
            AlertDialog confirmDialog = confirm.create();
            confirmDialog.setOnShowListener(new DialogInterface.OnShowListener() {
                public void onShow(DialogInterface d) {
                    uiStyleDialog((AlertDialog) d);
                }
            });
            confirmDialog.show();
        }
    });
    root.addView(resetBtn);

    root.addView(uiHintText("提示: 修改参数后会在当前界面实时刷新"));

    AlertDialog dialog = uiBuildDialog(getTopActivity(), "⚙️ 自动步数设置", scrollView, "关闭", null, null, null, null, null);
    dialog.show();
}

interface NumberEditCallback {
    void onConfirm(long value);
}

private void showEditNumberDialog(String title, String hint, String defaultValue, final NumberEditCallback callback, final Runnable onUiUpdated) {
    LinearLayout root = new LinearLayout(getTopActivity());
    root.setPadding(32, 32, 32, 32);
    root.setOrientation(LinearLayout.VERTICAL);

    if (hint != null && !hint.isEmpty()) {
        root.addView(uiHintText(hint));
    }

    final EditText editText = uiStyledEditText("请输入数值", defaultValue);
    editText.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
    root.addView(editText);

    AlertDialog dialog = uiBuildDialog(getTopActivity(), title, root, "确定", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
            String text = editText.getText().toString().trim();
            if (text.isEmpty()) {
                toast("请输入数值");
                return;
            }
            try {
                long value = Long.parseLong(text);
                callback.onConfirm(value);
                if (onUiUpdated != null) {
                    onUiUpdated.run();
                }
            } catch (NumberFormatException e) {
                toast("请输入有效数字");
            }
        }
    }, "取消", null, null, null);
    dialog.show();
}

private void showEditRangeDialog(final Runnable onUiUpdated) {
    LinearLayout root = new LinearLayout(getTopActivity());
    root.setPadding(32, 32, 32, 32);
    root.setOrientation(LinearLayout.VERTICAL);

    root.addView(uiHintText("设置时间模式下每分钟增加的步数范围\n预估日步数: " + (minTimeStep * ACTIVE_MINUTES) + " - " + (maxTimeStep * ACTIVE_MINUTES)));

    final EditText minEdit = uiStyledEditText("最小值 (当前: " + minTimeStep + ")", String.valueOf(minTimeStep));
    minEdit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
    root.addView(minEdit);

    final EditText maxEdit = uiStyledEditText("最大值 (当前: " + maxTimeStep + ")", String.valueOf(maxTimeStep));
    maxEdit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
    root.addView(maxEdit);

    AlertDialog dialog = uiBuildDialog(getTopActivity(), "📏 每分钟步数范围", root, "保存", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
            try {
                int min = Integer.parseInt(minEdit.getText().toString().trim());
                int max = Integer.parseInt(maxEdit.getText().toString().trim());
                if (min < 0 || max <= min) {
                    toast("最小值须 ≥ 0，最大值须 > 最小值");
                    return;
                }
                configLock.writeLock().lock();
                try {
                    minTimeStep = min;
                    maxTimeStep = max;
                    putInt("minTimeStep", minTimeStep);
                    putInt("maxTimeStep", maxTimeStep);
                } finally {
                    configLock.writeLock().unlock();
                }
                toast("范围: " + min + "-" + max + " (日预估: " + (min * ACTIVE_MINUTES) + "-" + (max * ACTIVE_MINUTES) + ")");
                if (onUiUpdated != null) {
                    onUiUpdated.run();
                }
            } catch (NumberFormatException e) {
                toast("请输入有效数字");
            }
        }
    }, "取消", null, null, null);
    dialog.show();
}

private void showStrategySelectionDialog(final Runnable onUiUpdated) {
    String[] options = {"📈 线性分配 — 均匀分配步数到每分钟", "📊 指数分配 — 前期少、后期多"};
    AlertDialog.Builder builder = new AlertDialog.Builder(getTopActivity());
    builder.setTitle("选择分配策略");
    builder.setItems(options, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
            int strategy = (which == 0) ? STRATEGY_LINEAR : STRATEGY_EXP;
            configLock.writeLock().lock();
            try {
                distributionStrategy = strategy;
                putInt("distributionStrategy", distributionStrategy);
            } finally {
                configLock.writeLock().unlock();
            }
            toast("分配策略已设为: " + (strategy == STRATEGY_EXP ? "指数" : "线性"));
            if (onUiUpdated != null) {
                onUiUpdated.run();
            }
        }
    });
    AlertDialog menuDialog = builder.create();
    menuDialog.setOnShowListener(new DialogInterface.OnShowListener() {
        public void onShow(DialogInterface d) {
            uiStyleDialog((AlertDialog) d);
        }
    });
    menuDialog.show();
}

private TextView uiSectionTitle(String text) {
    TextView textView = new TextView(getTopActivity());
    textView.setText(text);
    textView.setTextSize(16);
    textView.setTextColor(Color.parseColor("#333333"));
    try { textView.getPaint().setFakeBoldText(true); } catch (Exception e) {}
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    );
    params.setMargins(0, 16, 0, 12);
    textView.setLayoutParams(params);
    return textView;
}

private TextView uiHintText(String text) {
    TextView tv = new TextView(getTopActivity());
    tv.setText(text);
    tv.setTextSize(13);
    tv.setTextColor(Color.parseColor("#666666"));
    tv.setPadding(0, 0, 0, 12);
    return tv;
}

private EditText uiStyledEditText(String hint, String initialText) {
    EditText editText = new EditText(getTopActivity());
    editText.setHint(hint);
    editText.setText(initialText);
    editText.setPadding(32, 28, 32, 28);
    editText.setTextSize(14);
    editText.setTextColor(Color.parseColor("#333333"));
    editText.setHintTextColor(Color.parseColor("#999999"));
    GradientDrawable shape = new GradientDrawable();
    shape.setCornerRadius(24);
    shape.setColor(Color.parseColor("#FFFFFF"));
    shape.setStroke(2, Color.parseColor("#E0E0E0"));
    editText.setBackground(shape);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    );
    params.setMargins(0, 8, 0, 12);
    editText.setLayoutParams(params);
    return editText;
}

private void uiStyleButton(Button button) {
    button.setTextColor(Color.parseColor("#4A90E2"));
    GradientDrawable shape = new GradientDrawable();
    shape.setCornerRadius(20);
    shape.setStroke(3, Color.parseColor("#BBD7E6"));
    shape.setColor(Color.parseColor("#F5FBFF"));
    button.setBackground(shape);
    button.setAllCaps(false);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    );
    params.setMargins(0, 8, 0, 8);
    button.setLayoutParams(params);
}

private void uiApplyToggleStyle(Button button, String label, boolean enabled) {
    button.setText(label + ": " + (enabled ? "已开启" : "已关闭"));
    GradientDrawable shape = new GradientDrawable();
    shape.setCornerRadius(20);
    if (enabled) {
        shape.setColor(Color.parseColor("#E8F5E9"));
        shape.setStroke(3, Color.parseColor("#A5D6A7"));
        button.setTextColor(Color.parseColor("#2E7D32"));
    } else {
        shape.setColor(Color.parseColor("#FAFAFA"));
        shape.setStroke(3, Color.parseColor("#E0E0E0"));
        button.setTextColor(Color.parseColor("#757575"));
    }
    button.setBackground(shape);
    button.setAllCaps(false);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    );
    params.setMargins(0, 6, 0, 6);
    button.setLayoutParams(params);
}

private Button uiSettingButton(String text) {
    Button button = new Button(getTopActivity());
    button.setText(text);
    button.setTextColor(Color.parseColor("#37474F"));
    button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
    GradientDrawable shape = new GradientDrawable();
    shape.setCornerRadius(16);
    shape.setColor(Color.parseColor("#FFFFFF"));
    shape.setStroke(2, Color.parseColor("#E0E0E0"));
    button.setBackground(shape);
    button.setAllCaps(false);
    button.setPadding(32, 24, 32, 24);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    );
    params.setMargins(0, 4, 0, 4);
    button.setLayoutParams(params);
    return button;
}

private AlertDialog uiBuildDialog(Context context, String title, View view, String positiveBtnText, DialogInterface.OnClickListener positiveListener, String negativeBtnText, DialogInterface.OnClickListener negativeListener, String neutralBtnText, DialogInterface.OnClickListener neutralListener) {
    AlertDialog.Builder builder = new AlertDialog.Builder(context);
    builder.setTitle(title);
    builder.setView(view);
    if (positiveBtnText != null) builder.setPositiveButton(positiveBtnText, positiveListener);
    if (negativeBtnText != null) builder.setNegativeButton(negativeBtnText, negativeListener);
    if (neutralBtnText != null) builder.setNeutralButton(neutralBtnText, neutralListener);
    final AlertDialog dialog = builder.create();
    dialog.setOnShowListener(new DialogInterface.OnShowListener() {
        public void onShow(DialogInterface d) {
            uiStyleDialog(dialog);
        }
    });
    return dialog;
}

private void uiStyleDialog(AlertDialog dialog) {
    try {
        GradientDrawable dialogBg = new GradientDrawable();
        dialogBg.setCornerRadius(48);
        dialogBg.setColor(Color.parseColor("#FAFBF9"));
        dialog.getWindow().setBackgroundDrawable(dialogBg);
    } catch (Exception e) {}
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

boolean onLongClickSendBtn(String text) {
    return false;
}