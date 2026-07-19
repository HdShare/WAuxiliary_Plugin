import org.json.JSONObject;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

String logFile = "";

void onLoad() {
    logFile = pluginDir + "/weather.log";
    log("===== 天气查询插件 v1.0.0 已加载 =====");
    writeLog("插件已加载，日志文件: " + logFile);
}

void onUnload() {
    writeLog("插件已卸载");
}

void writeLog(String msg) {
    try {
        var now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        var line = "[" + now + "] " + msg + "\n";
        var fw = new FileWriter(logFile, true);
        fw.write(line);
        fw.close();
    } catch (Exception e) {}
    log(msg);
}

void onHandleMsg(Object msgInfoBean) {
    if (msgInfoBean.isSend()) return;
    if (!msgInfoBean.isText()) return;
    var content = msgInfoBean.getContent();
    var talker = msgInfoBean.getTalker();
    writeLog("收到消息: [" + content + "] from=" + talker);

    if (!content.startsWith("天气 ") && !content.startsWith("天气查询 ")) {
        writeLog("未匹配命令，跳过");
        return;
    }
    writeLog("匹配天气查询命令");

    var city = content.substring(content.indexOf(" ") + 1).trim();
    if (city.isEmpty()) {
        sendText(talker, "请输入城市名，如：天气 北京");
        return;
    }

    var url = "https://wttr.in/" + encodeURI(city) + "?format=j1&lang=zh";
    writeLog("查询城市=" + city + " url=" + url);

    get(url, null, respContent -> {
        try {
            writeLog("API 响应已收到");
            var json = new JSONObject(respContent);
            var current = json.optJSONArray("current_condition").optJSONObject(0);

            if (current == null) {
                writeLog("未找到城市数据: " + city);
                sendText(talker, "未查询到「" + city + "」的天气信息，请检查城市名");
                return;
            }

            var temp = current.optString("temp_C");
            var humidity = current.optString("humidity");
            var weatherDesc = current.optJSONArray("weatherDesc").optJSONObject(0).optString("value");
            var windSpeed = current.optString("windspeedKmph");
            var windDir = current.optString("winddir16Point");
            var feelsLike = current.optString("FeelsLikeC");
            var visibility = current.optString("visibility");
            var uvIndex = current.optString("uvIndex");

            var nearest = json.optJSONArray("nearest_area").optJSONObject(0);
            var areaName = nearest.optJSONArray("areaName").optJSONObject(0).optString("value");
            var country = nearest.optJSONArray("country").optJSONObject(0).optString("value");

            var forecast = json.optJSONArray("weather");
            var today = forecast.optJSONObject(0);
            var maxTemp = today.optString("maxtempC");
            var minTemp = today.optString("mintempC");

            var result = "[天气查询] " + areaName + ", " + country + "\n";
            result += "天气: " + weatherDesc + "\n";
            result += "温度: " + temp + "°C (体感 " + feelsLike + "°C)\n";
            result += "最高/最低: " + maxTemp + "°C / " + minTemp + "°C\n";
            result += "湿度: " + humidity + "%\n";
            result += "风速: " + windSpeed + " km/h " + windDir + "\n";
            result += "能见度: " + visibility + " km\n";
            result += "紫外线: " + uvIndex;

            sendText(talker, result);
            writeLog("天气结果已发送 城市=" + areaName + " 温度=" + temp + "°C");
        } catch (Exception e) {
            writeLog("异常: " + e.toString());
            sendText(talker, "天气查询失败，请检查城市名是否正确");
        }
    });
}

void openSettings() {
    writeLog("openSettings 被调用");
    var activity = getTopActivity();
    if (activity == null) {
        toast("无法打开设置");
        return;
    }
    toast("天气查询插件 v1.0.0 - 发送「天气 城市名」查询天气");
}

String encodeURI(String s) {
    if (s == null) return "";
    var encoded = "";
    for (var i = 0; i < s.length(); i++) {
        var ch = s.charAt(i);
        if (Character.isLetterOrDigit(ch) || ch == '-' || ch == '_' || ch == '.' || ch == '~') {
            encoded += ch;
        } else {
            var bytes = ch.toString().getBytes("UTF-8");
            for (var j = 0; j < bytes.length; j++) {
                encoded += "%" + String.format("%02X", bytes[j] & 0xFF);
            }
        }
    }
    return encoded;
}