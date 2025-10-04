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
int minTimeStep = 6;
int maxTimeStep = 18;

void onLoad() {
    currentStep = getLong("currentStep", 0);
    currentDay = getInt("currentDay", 0);
    timeStepEnabled = getBoolean("timeStepEnabled", false);
    messageStepEnabled = getBoolean("messageStepEnabled", false);
    maxStep = getLong("maxStep", 24305);
    minTimeStep = getInt("minTimeStep", 6);
    maxTimeStep = getInt("maxTimeStep", 18);
    
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
        
        if (content.startsWith("/改步数 ")) {
            String[] parts = content.split(" ");
            if (parts.length == 2) {
                try {
                    long step = Long.parseLong(parts[1]);
                    if (step >= 0 && step <= maxStep) {
                        currentStep = step;
                        putLong("currentStep", currentStep);
                        uploadDeviceStep(currentStep);
                        sendText(talker, "步数已修改为: " + step);
                        log("用户手动修改步数为: " + step);
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
            String[] parts = content.split(" ");
            if (parts.length == 2) {
                try {
                    long step = Long.parseLong(parts[1]);
                    if (step > 0) {
                        maxStep = step;
                        putLong("maxStep", maxStep);
                        sendText(talker, "最大步数已修改为: " + step);
                        log("用户修改最大步数为: " + step);
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
            String[] parts = content.split(" ");
            if (parts.length == 2) {
                String[] rangeParts = parts[1].split("-");
                if (rangeParts.length == 2) {
                    try {
                        int min = Integer.parseInt(rangeParts[0]);
                        int max = Integer.parseInt(rangeParts[1]);
                        if (min >= 0 && max > min) {
                            minTimeStep = min;
                            maxTimeStep = max;
                            putInt("minTimeStep", minTimeStep);
                            putInt("maxTimeStep", maxTimeStep);
                            sendText(talker, "时间步数范围已修改为: " + min + "-" + max);
                            
                            // 计算并提示当日最大步数-最小步数范围
                            int activeMinutes = calculateActiveMinutes();
                            int dailyMin = minTimeStep * activeMinutes;
                            int dailyMax = maxTimeStep * activeMinutes;
                            sendText(talker, "当日最大步数范围: " + dailyMin + "-" + dailyMax);
                            
                            log("用户修改时间步数范围为: " + min + "-" + max);
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
    long initialDelay = getNext1MinuteDelay();
    
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
                int step = minTimeStep + random.nextInt(maxTimeStep - minTimeStep + 1);
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
    }, initialDelay, 60 * 1000);
    
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

long getNext1MinuteDelay() {
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime next1Minute = now.withSecond(0)
                                   .withNano(0)
                                   .plusMinutes(1);
    return java.time.Duration.between(now, next1Minute).toMillis();
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
    String status = String.format("当前步数: %d\n时间步数: %s\n消息步数: %s\n步数范围: %d-%d\n今日目标: %d\n进度: %d%%",
                                 currentStep,
                                 timeStepEnabled ? "已开启" : "已关闭",
                                 messageStepEnabled ? "已开启" : "已关闭",
                                 minTimeStep,
                                 maxTimeStep,
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
                      "【手动控制命令】\n" +
                      "/改步数 数字 - 手动修改步数为指定数值\n" +
                      "/最大步数 数字 - 修改最大步数限制\n" +
                      "/步数范围 最小值-最大值 - 修改时间步数随机范围\n\n" +
                      "【查询命令】\n" +
                      "/步数状态 - 查看当前步数和功能状态\n" +
                      "/步数帮助 - 显示本帮助信息\n\n" +
                      "【功能说明】\n" +
                      "1. 时间步数：每分钟自动增加" + minTimeStep + "-" + maxTimeStep + "步\n" +
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

int calculateActiveMinutes() {
    return 24 * 60 - 60 - 6 * 60;
}

boolean onLongClickSendBtn(String text) {
    return false;
}