import java.time.LocalDate;
import java.io.FileOutputStream;
import java.io.FileInputStream;

void qd(){
//每日签到
    int dayOfMonth = LocalDate.now().getDayOfMonth();
    FileInputStream in = new FileInputStream(pluginDir + "/qd");
    int dayRead = in.read();
    in.close();    
    if (dayRead!=dayOfMonth) {
    sendText("wxid_*********", "签到"); 
    FileOutputStream out = new FileOutputStream(pluginDir + "/qd");
    out.write((byte) dayOfMonth);
    out.close();
    }
}


void onHandleMsg(Object msgInfoBean) {
    if (msgInfoBean.isSend()) return;
    qd();
}
