import java.time.LocalDateTime;
import java.util.Random;

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