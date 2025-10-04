import java.time.LocalDateTime;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

long maxStep = 24305;
long currentStep = 0;
int currentDay = 0;
Timer timer = null;
boolean isTimerRunning = false;
boolean timeStepEnabled = false;
boolean messageStepEnabled = false;

void onLoad() {
    currentStep = getLong("currentStep", 0);
    currentDay = getInt("currentDay", 0);
    timeStepEnabled = getBoolean("timeStepEnabled", false);
    messageStepEnabled = getBoolean("messageStepEnabled", false);
    
    LocalDateTime now = LocalDateTime.now();
    if (now.getDayOfYear() != currentDay) {
        currentStep = 0;
        currentDay = now.getDayOfYear();
        putInt("currentDay", currentDay);
        putLong("currentStep", currentStep);
    }
    
    if (timeStepEnabled) {
        startTimeStepTimer();
    }
    
    log("插件加载完成，时间步数功能状态: " + (timeStepEnabled ? "已开启" : "已关闭") + 
        "，消息步数功能状态: " + (messageStepEnabled ? "已开启" : "已关闭"));
}

void onUnLoad() {
    stopTimeStepTimer();
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
    }
    
    if (messageStepEnabled) {
        updateStepOnMessage();
    }
}

void updateStepOnMessage() {
    if (isRestrictedTime()) {
        log("当前时间为23:00-6:00，不增加步数");
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
    }
    
    Random random = new Random();
    int step = 50 + random.nextInt(100);
    
    currentStep += calculateStepIncrease(currentStep, step);
    currentStep = Math.min(currentStep, maxStep);
    
    putLong("currentStep", currentStep);
    uploadDeviceStep(currentStep);
    log("消息触发增加步数: +" + step + "步，当前步数=" + currentStep);
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
    
    timer = new Timer();
    long initialDelay = getNext5MinuteDelay();
    
    timer.schedule(new TimerTask() {
        public void run() {
            try {
                if (isRestrictedTime()) {
                    log("当前时间为23:00-6:00，定时任务不增加步数");
                    return;
                }
                
                currentStep = getLong("currentStep", 0);
                currentDay = getInt("currentDay", 0);
                
                LocalDateTime now = LocalDateTime.now();
                if (now.getDayOfYear() != currentDay) {
                    currentStep = 0;
                    currentDay = now.getDayOfYear();
                    putInt("currentDay", currentDay);
                }
                
                Random random = new Random();
                int step = 20 + random.nextInt(21);
                currentStep += step;
                
                if (currentStep > maxStep) {
                    currentStep = maxStep;
                }
                
                putLong("currentStep", currentStep);
                uploadDeviceStep(currentStep);
                log("定时增加步数: +" + step + "步，当前步数=" + currentStep);
            } catch (Exception e) {
                log("定时任务异常: " + e.getMessage());
                if (timeStepEnabled && !isTimerRunning) {
                    startTimeStepTimer();
                }
            }
        }
    }, initialDelay, 5 * 60 * 1000);
    
    isTimerRunning = true;
    log("步数定时器启动，首次执行延迟: " + initialDelay + "ms");
}

void stopTimeStepTimer() {
    if (timer != null) {
        timer.cancel();
        timer = null;
        isTimerRunning = false;
        log("步数定时器停止");
    }
}

long getNext5MinuteDelay() {
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime next5Minute = now.withMinute((now.getMinute() / 5) * 5)
                                  .withSecond(0)
                                  .withNano(0)
                                  .plusMinutes(5);
    return java.time.Duration.between(now, next5Minute).toMillis();
}

void enableTimeStep(boolean enable) {
    timeStepEnabled = enable;
    putBoolean("timeStepEnabled", timeStepEnabled);
    
    if (enable) {
        startTimeStepTimer();
        toast("时间自动增加步数功能已开启");
        log("用户开启时间步数功能");
    } else {
        stopTimeStepTimer();
        toast("时间自动增加步数功能已关闭");
        log("用户关闭时间步数功能");
    }
}

void enableMessageStep(boolean enable) {
    messageStepEnabled = enable;
    putBoolean("messageStepEnabled", messageStepEnabled);
    
    if (enable) {
        toast("消息自动增加步数功能已开启");
        log("用户开启消息步数功能");
    } else {
        toast("消息自动增加步数功能已关闭");
        log("用户关闭消息步数功能");
    }
}

void showStepStatus(String talker) {
    String status = String.format("当前步数: %d\n时间步数: %s\n消息步数: %s\n今日目标: %d\n进度: %d%%",
                                 currentStep,
                                 timeStepEnabled ? "已开启" : "已关闭",
                                 messageStepEnabled ? "已开启" : "已关闭",
                                 maxStep,
                                 currentStep * 100 / maxStep);
    
    sendText(talker, status);
    log("用户查询步数状态: " + status.replace("\n", " "));
}

void showHelp(String talker) {
    String helpText = "微信步数插件使用说明：\n\n" +
                      "【功能控制命令】\n" +
                      "/时间步数开 - 开启定时自动增加步数功能\n" +
                      "/时间步数关 - 关闭定时自动增加步数功能\n" +
                      "/消息步数开 - 开启消息自动增加步数功能\n" +
                      "/消息步数关 - 关闭消息自动增加步数功能\n\n" +
                      "【查询命令】\n" +
                      "/步数状态 - 查看当前步数和功能状态\n" +
                      "/步数帮助 - 显示本帮助信息\n\n" +
                      "【功能说明】\n" +
                      "1. 时间步数：每5分钟自动增加20-40步\n" +
                      "2. 消息步数：每次收发消息时自动增加50-150步\n" +
                      "3. 步数增长幅度会根据当前步数自动调整\n" +
                      "4. 23:00-6:00时间段内不会自动增加步数\n" +
                      "5. 每日0点会自动重置步数";
    
    sendText(talker, helpText);
    log("用户查询帮助信息");
}

boolean isRestrictedTime() {
    LocalDateTime now = LocalDateTime.now();
    int hour = now.getHour();
    return hour >= 23 || hour < 6;
}

boolean onLongClickSendBtn(String text) {
    return false;
}
