import java.time.LocalDateTime;
import java.util.Random;

/// 每次收发消息时自动增加微信步数
/// 增长幅度有三个等级，简而言之，步数越高，增长幅度会越小
/// ！和当天实际微信步数无关；如果当天实际步数大于插件增加的步数，会以实际步数为准

long maxStep = 57305; //最大步數
long currentStep = 0; //當前步數
int currentDay = 0;

void onHandleMsg(Object msgInfoBean) {
    currentStep = getLong("currentStep", 0);
    currentDay = getInt("currentDay", 0);
    LocalDateTime now = LocalDateTime.now();
    if (now.getDayOfYear() != currentDay) currentStep = 0; //新的一天重置步數
    currentDay = now.getDayOfYear();
    putInt("currentDay", currentDay);
    Random random = new Random();
    int step = 50 + random.nextInt(100); // 隨機步數
    if (currentStep < 10000) {
        currentStep += (step * 3);
    } else if (currentStep < 20000) {
        currentStep += (step * 2);
    } else if (currentStep < 30000) {
        currentStep += (step);
    } else {
        currentStep += (long) (step * 0.5);
    }
    putLong("currentStep", currentStep);
    if (currentStep <= maxStep) uploadDeviceStep(currentStep);
}