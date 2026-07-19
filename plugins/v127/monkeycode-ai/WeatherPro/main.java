import android.app.AlertDialog;
import android.widget.*;
import android.graphics.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.io.FileWriter;
import org.json.JSONObject;

String apiKey = "";
String subscribedCities = "";
String pushTime = "08:00";
boolean dailyPushEnabled = false;
String lastPushDate = "";
String logFile = "";

void onLoad() {
    apiKey = getString("api_key", "");
    subscribedCities = getString("subscribed_cities", "");
    pushTime = getString("push_time", "08:00");
    dailyPushEnabled = getBoolean("daily_push_enabled", false);
    lastPushDate = getString("last_push_date", "");
    logFile = pluginDir + "/weather_pro.log";
    writeLog("===== 天气Pro v1.0.0 已加载 ===== apiKey=" + (apiKey.isEmpty() ? "未设置" : "已设置") + " 订阅=" + (subscribedCities.isEmpty() ? "0" : String.valueOf(subscribedCities.split(";").length)) + "个 日志文件=" + logFile);
}

void onUnload() {
    writeLog("天气Pro 已卸载");
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

// ==================== 消息处理入口 ====================

void onHandleMsg(Object msgInfoBean) {
    if (msgInfoBean.isSend()) { return; }
    if (!msgInfoBean.isText()) { return; }

    var content = msgInfoBean.getContent().trim();
    var talker = msgInfoBean.getTalker();
    writeLog("天气Pro onHandleMsg: content=[" + content + "] talker=" + talker);

    if (content.startsWith("订阅天气 ") || content.startsWith("订阅 ")) {
        writeLog("天气Pro: 匹配「订阅」命令");
        handleSubscribe(talker, content);
        return;
    }
    if (content.equals("取消订阅") || content.equals("取消天气")) {
        writeLog("天气Pro: 匹配「取消订阅」命令");
        cancelAllSubscriptions(talker);
        return;
    }
    if (content.equals("我的订阅") || content.equals("订阅列表")) {
        writeLog("天气Pro: 匹配「我的订阅」命令");
        showSubscriptionList(talker);
        return;
    }
    if (content.startsWith("预报 ") || content.startsWith("天气预报 ")) {
        writeLog("天气Pro: 匹配「预报」命令");
        handleForecast(talker, content);
        return;
    }
    if (content.startsWith("天气 ") || content.startsWith("天气查询 ")) {
        writeLog("天气Pro: 匹配「天气」命令");
        handleWeatherQuery(talker, content);
        return;
    }
    if (content.startsWith("天气设置")) {
        writeLog("天气Pro: 匹配「天气设置」命令");
        handleSettingsCommand(talker, content);
        return;
    }
    if (content.equals("天气帮助") || content.equals("天气Pro") || content.equals("天气pro")) {
        writeLog("天气Pro: 匹配「天气帮助」命令");
        showHelp(talker);
        return;
    }

    writeLog("天气Pro: 未匹配任何命令，检查每日推送");
    checkDailyPush();
}

// ==================== 天气查询 ====================

void handleWeatherQuery(String talker, String content) {
    var city = content.substring(content.indexOf(" ") + 1).trim();
    writeLog("天气Pro: 查询城市=" + city);
    if (city.isEmpty()) {
        sendText(talker, "[天气Pro] 请输入城市名，如：天气 北京");
        return;
    }
    if (apiKey.isEmpty()) {
        sendText(talker, "[天气Pro] 请先设置和风天气 API Key。免费获取: https://dev.qweather.com\n发送「天气设置 key 你的Key」进行设置");
        return;
    }
    doWeatherQuery(talker, city);
}

void doWeatherQuery(String talker, String city) {
    var geoUrl = "https://geoapi.qweather.com/v2/city/lookup?location=" + encodeURI(city) + "&key=" + apiKey;
    get(geoUrl, null, geoResp -> {
        writeLog("天气Pro: 城市搜索返回 code=" + new JSONObject(geoResp).optString("code"));
        try {
            var geoJson = new JSONObject(geoResp);
            if (!geoJson.optString("code").equals("200")) {
                sendText(talker, "[天气Pro] 未找到城市「" + city + "」");
                return;
            }
            var loc = geoJson.optJSONArray("location").optJSONObject(0);
            var cityId = loc.optString("id");
            var cityFullName = loc.optString("name") + ", " + loc.optString("adm1") + ", " + loc.optString("country");

            var nowUrl = "https://devapi.qweather.com/v7/weather/now?location=" + cityId + "&key=" + apiKey;
            writeLog("天气Pro: 查询天气 cityId=" + cityId);
            get(nowUrl, null, nowResp -> {
                try {
                    var now = new JSONObject(nowResp).optJSONObject("now");
                    if (now == null) { sendText(talker, "[天气Pro] 查询失败"); return; }
                    var sb = "[天气Pro] " + cityFullName + "\n";
                    sb += "天气: " + now.optString("text") + "\n";
                    sb += "温度: " + now.optString("temp") + "°C (体感 " + now.optString("feelsLike") + "°C)\n";
                    sb += "风向: " + now.optString("windDir") + " " + now.optString("windScale") + "级\n";
                    sb += "风速: " + now.optString("windSpeed") + " km/h\n";
                    sb += "湿度: " + now.optString("humidity") + "%\n";
                    sb += "能见度: " + now.optString("vis") + " km\n";
                    sb += "气压: " + now.optString("pressure") + " hPa\n";
                    sb += "云量: " + now.optString("cloud") + "%\n";
                    sb += "发送「预报 " + city + "」查看3日预报";
                    sendText(talker, sb);
                } catch (Exception e) {
                    sendText(talker, "[天气Pro] 查询失败: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            sendText(talker, "[天气Pro] 查询失败: " + e.getMessage());
        }
    });
}

// ==================== 多日预报 ====================

void handleForecast(String talker, String content) {
    var prefix = content.startsWith("预报 ") ? "预报 " : "天气预报 ";
    var city = content.substring(prefix.length()).trim();
    if (city.isEmpty()) {
        sendText(talker, "[天气Pro] 请输入城市名，如：预报 北京");
        return;
    }
    if (apiKey.isEmpty()) {
        sendText(talker, "[天气Pro] 请先设置和风天气 API Key");
        return;
    }
    doForecastQuery(talker, city);
}

void doForecastQuery(String talker, String city) {
    var geoUrl = "https://geoapi.qweather.com/v2/city/lookup?location=" + encodeURI(city) + "&key=" + apiKey;
    get(geoUrl, null, geoResp -> {
        try {
            var geoJson = new JSONObject(geoResp);
            if (!geoJson.optString("code").equals("200")) {
                sendText(talker, "[天气Pro] 未找到城市「" + city + "」");
                return;
            }
            var loc = geoJson.optJSONArray("location").optJSONObject(0);
            var cityId = loc.optString("id");
            var cityFullName = loc.optString("name") + ", " + loc.optString("adm1") + ", " + loc.optString("country");

            var fcUrl = "https://devapi.qweather.com/v7/weather/3d?location=" + cityId + "&key=" + apiKey;
            get(fcUrl, null, fcResp -> {
                try {
                    var daily = new JSONObject(fcResp).optJSONArray("daily");
                    if (daily == null || daily.length() == 0) {
                        sendText(talker, "[天气Pro] 预报数据获取失败");
                        return;
                    }
                    var sb = "[天气Pro] " + cityFullName + " 3 日预报";
                    for (var i = 0; i < daily.length(); i++) {
                        var day = daily.optJSONObject(i);
                        var date = day.optString("fxDate");
                        sb += "\n\n" + date + " " + getWeekDay(date);
                        sb += "\n  白天: " + day.optString("textDay") + " " + day.optString("tempMax") + "°C";
                        sb += "\n  夜间: " + day.optString("textNight") + " " + day.optString("tempMin") + "°C";
                        sb += "\n  风向: " + day.optString("windDirDay") + " " + day.optString("windScaleDay") + "级";
                        sb += "\n  湿度: " + day.optString("humidity") + "% | 降水: " + day.optString("precip") + "mm";
                        sb += "\n  紫外线: " + day.optString("uvIndex") + " | 日出 " + day.optString("sunrise") + " 日落 " + day.optString("sunset");
                    }
                    sendText(talker, sb);
                } catch (Exception e) {
                    sendText(talker, "[天气Pro] 预报查询失败");
                }
            });
        } catch (Exception e) {
            sendText(talker, "[天气Pro] 查询失败: " + e.getMessage());
        }
    });
}

// ==================== 城市订阅管理 ====================

void handleSubscribe(String talker, String content) {
    var prefix = content.startsWith("订阅天气 ") ? "订阅天气 " : "订阅 ";
    var city = content.substring(prefix.length()).trim();
    if (city.isEmpty()) {
        sendText(talker, "[天气Pro] 请输入城市名，如：订阅 北京");
        return;
    }
    if (apiKey.isEmpty()) {
        sendText(talker, "[天气Pro] 请先设置和风天气 API Key");
        return;
    }
    doSubscribeCity(talker, city);
}

void doSubscribeCity(String talker, String city) {
    var geoUrl = "https://geoapi.qweather.com/v2/city/lookup?location=" + encodeURI(city) + "&key=" + apiKey;
    get(geoUrl, null, geoResp -> {
        try {
            var geoJson = new JSONObject(geoResp);
            if (!geoJson.optString("code").equals("200")) {
                sendText(talker, "[天气Pro] 未找到城市「" + city + "」");
                return;
            }
            var loc = geoJson.optJSONArray("location").optJSONObject(0);
            var cityId = loc.optString("id");
            var cityFullName = loc.optString("name") + ", " + loc.optString("adm1") + ", " + loc.optString("country");

            if (subscribedCities.contains(cityId)) {
                sendText(talker, "[天气Pro] 已订阅过 " + cityFullName);
                return;
            }
            var entry = cityFullName + "|" + cityId;
            subscribedCities = subscribedCities.isEmpty() ? entry : subscribedCities + ";" + entry;
            dailyPushEnabled = true;
            saveSubscriptions();
            sendText(talker, "[天气Pro] 已订阅 " + cityFullName + " 每日 " + pushTime + " 推送");
        } catch (Exception e) {
            sendText(talker, "[天气Pro] 订阅失败: " + e.getMessage());
        }
    });
}

void cancelAllSubscriptions(String talker) {
    if (subscribedCities.isEmpty()) {
        sendText(talker, "[天气Pro] 当前没有订阅任何城市");
        return;
    }
    subscribedCities = "";
    dailyPushEnabled = false;
    saveSubscriptions();
    sendText(talker, "[天气Pro] 已取消所有订阅");
}

void showSubscriptionList(String talker) {
    if (subscribedCities.isEmpty()) {
        sendText(talker, "[天气Pro] 当前没有订阅。发送「订阅 城市名」进行订阅");
        return;
    }
    var cities = subscribedCities.split(";");
    var sb = "[天气Pro] 订阅列表 (" + cities.length + "):\n";
    for (var i = 0; i < cities.length; i++) {
        sb += (i + 1) + ". " + cities[i].split("\\|")[0] + "\n";
    }
    sb += "推送时间: " + pushTime + " | 状态: " + (dailyPushEnabled ? "开启" : "关闭");
    sendText(talker, sb);
}

// ==================== 每日定时推送 ====================

void checkDailyPush() {
    if (!dailyPushEnabled) return;
    if (subscribedCities.isEmpty()) return;
    if (apiKey.isEmpty()) return;

    var today = new SimpleDateFormat("yyyyMMdd").format(new Date());
    if (today.equals(lastPushDate)) return;
    if (new SimpleDateFormat("HH:mm").format(new Date()).compareTo(pushTime) < 0) return;

    lastPushDate = today;
    putString("last_push_date", today);

    var cities = subscribedCities.split(";");
    for (var i = 0; i < cities.length; i++) {
        var parts = cities[i].split("\\|");
        if (parts.length < 2) continue;
        doPushSingleCity(parts[0], parts[1]);
    }
    writeLog("天气Pro 每日推送完成，共 " + cities.length + " 个城市");
}

void doPushSingleCity(String cityName, String cityId) {
    var url = "https://devapi.qweather.com/v7/weather/now?location=" + cityId + "&key=" + apiKey;
    get(url, null, resp -> {
        try {
            var now = new JSONObject(resp).optJSONObject("now");
            if (now == null) return;
            var msg = "[天气Pro] " + cityName + "\n";
            msg += now.optString("text") + " " + now.optString("temp") + "°C";
            msg += " | 体感 " + now.optString("feelsLike") + "°C";
            msg += " | " + now.optString("windDir") + " " + now.optString("windScale") + "级";
            msg += "\n湿度 " + now.optString("humidity") + "%";
            msg += " | 能见度 " + now.optString("vis") + "km";
            notify("天气Pro", cityName + " " + now.optString("text") + " " + now.optString("temp") + "°C");
        } catch (Exception e) {
            writeLog("推送失败 " + cityName + ": " + e.getMessage());
        }
    });
}

// ==================== 设置命令 ====================

void handleSettingsCommand(String talker, String content) {
    var parts = content.trim().split("\\s+");

    if (parts.length == 1) {
        showCurrentSettings(talker);
        return;
    }

    var cmd = parts[1];

    if (cmd.equals("key") && parts.length >= 3) {
        apiKey = parts[2].trim();
        putString("api_key", apiKey);
        sendText(talker, "[天气Pro] API Key 已设置");
        return;
    }
    if ((cmd.equals("推送时间") || cmd.equals("时间")) && parts.length >= 3) {
        pushTime = parts[2].trim();
        putString("push_time", pushTime);
        sendText(talker, "[天气Pro] 推送时间已设置为 " + pushTime);
        return;
    }
    if ((cmd.equals("推送") || cmd.equals("开关")) && parts.length >= 3) {
        var val = parts[2];
        dailyPushEnabled = val.equals("开") || val.equals("开启") || val.equals("on") || val.equals("1");
        saveSubscriptions();
        sendText(talker, "[天气Pro] 每日推送已" + (dailyPushEnabled ? "开启" : "关闭"));
        return;
    }

    showCurrentSettings(talker);
}

void showCurrentSettings(String talker) {
    var maskedKey = apiKey.isEmpty() ? "未设置"
        : (apiKey.length() > 8 ? apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4) : "****");
    var subCount = subscribedCities.isEmpty() ? 0 : subscribedCities.split(";").length;
    var sb = "[天气Pro] 当前设置:\n";
    sb += "API Key: " + maskedKey + "\n";
    sb += "每日推送: " + (dailyPushEnabled ? "开启" : "关闭") + "\n";
    sb += "推送时间: " + pushTime + "\n";
    sb += "订阅城市: " + subCount + " 个\n";
    sb += "\n命令:\n";
    sb += "天气设置 key <Key>          设置 API Key\n";
    sb += "天气设置 推送时间 <HH:mm>   设置推送时间\n";
    sb += "天气设置 推送 开/关         开关每日推送";
    sendText(talker, sb);
}

// ==================== 帮助 ====================

void showHelp(String talker) {
    var help = "[天气Pro] 使用帮助:\n\n";
    help += "天气 <城市>     查询实时天气\n";
    help += "预报 <城市>     查看 3 日预报\n";
    help += "订阅 <城市>     订阅每日推送\n";
    help += "取消订阅        取消所有订阅\n";
    help += "我的订阅        查看订阅列表\n";
    help += "天气设置        查看/修改设置\n";
    help += "天气帮助        显示此帮助\n";
    help += "\n免费获取 API Key: https://dev.qweather.com";
    sendText(talker, help);
}

// ==================== UI 设置面板 ====================

void openSettings() {
    writeLog("天气Pro: openSettings 被调用");
    var activity = getTopActivity();
    if (activity == null) { toast("无法打开设置面板"); return; }

    var root = new LinearLayout(activity);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(50, 40, 50, 40);

    var title = new TextView(activity);
    title.setText("天气Pro 设置");
    title.setTextSize(20);
    title.setTextColor(Color.parseColor("#333333"));
    root.addView(title);

    var keyLabel = new TextView(activity);
    keyLabel.setText("和风天气 API Key:");
    keyLabel.setPadding(0, 20, 0, 5);
    keyLabel.setTextColor(Color.parseColor("#666666"));
    root.addView(keyLabel);

    var keyInput = new EditText(activity);
    keyInput.setText(apiKey);
    keyInput.setHint("免费获取: dev.qweather.com");
    root.addView(keyInput);

    var pushLabel = new TextView(activity);
    pushLabel.setText("每日推送:");
    pushLabel.setPadding(0, 20, 0, 5);
    pushLabel.setTextColor(Color.parseColor("#666666"));
    root.addView(pushLabel);

    var pushSwitch = new Switch(activity);
    pushSwitch.setChecked(dailyPushEnabled);
    pushSwitch.setText(dailyPushEnabled ? "已开启" : "已关闭");
    pushSwitch.setOnCheckedChangeListener((btn, checked) -> {
        pushSwitch.setText(checked ? "已开启" : "已关闭");
    });
    root.addView(pushSwitch);

    var timeLabel = new TextView(activity);
    timeLabel.setText("推送时间:");
    timeLabel.setPadding(0, 15, 0, 5);
    timeLabel.setTextColor(Color.parseColor("#666666"));
    root.addView(timeLabel);

    var timeInput = new EditText(activity);
    timeInput.setText(pushTime);
    timeInput.setHint("08:00");
    root.addView(timeInput);

    var subLabel = new TextView(activity);
    subLabel.setText("订阅城市 (逗号分隔):");
    subLabel.setPadding(0, 15, 0, 5);
    subLabel.setTextColor(Color.parseColor("#666666"));
    root.addView(subLabel);

    var subInput = new EditText(activity);
    var cityNames = "";
    if (!subscribedCities.isEmpty()) {
        var cities = subscribedCities.split(";");
        for (var i = 0; i < cities.length; i++) {
            if (i > 0) cityNames += ", ";
            cityNames += cities[i].split("\\|")[0];
        }
    }
    subInput.setText(cityNames);
    subInput.setHint("北京, 上海");
    root.addView(subInput);

    var btnLayout = new LinearLayout(activity);
    btnLayout.setOrientation(LinearLayout.HORIZONTAL);
    btnLayout.setPadding(0, 30, 0, 0);

    var saveBtn = new Button(activity);
    saveBtn.setText("保存设置");
    saveBtn.setOnClickListener(v -> {
        apiKey = keyInput.getText().toString().trim();
        dailyPushEnabled = pushSwitch.isChecked();
        pushTime = timeInput.getText().toString().trim();
        if (pushTime.isEmpty()) pushTime = "08:00";
        putString("api_key", apiKey);
        putBoolean("daily_push_enabled", dailyPushEnabled);
        putString("push_time", pushTime);

        var newCities = subInput.getText().toString().trim();
        if (!newCities.isEmpty() && !newCities.equals(cityNames)) {
            var names = newCities.split(",");
            var entry = "";
            for (var i = 0; i < names.length; i++) {
                var n = names[i].trim();
                if (n.isEmpty()) continue;
                if (entry.length() > 0) entry += ";";
                entry += n + "|" + n;
            }
            subscribedCities = entry;
        }
        saveSubscriptions();
        toast("设置已保存");
    });
    btnLayout.addView(saveBtn);

    root.addView(btnLayout);

    var builder = new AlertDialog.Builder(activity);
    var dialog = builder.create();
    dialog.setView(root);
    dialog.show();
}

// ==================== 工具方法 ====================

void saveSubscriptions() {
    putString("subscribed_cities", subscribedCities);
    putBoolean("daily_push_enabled", dailyPushEnabled);
}

String getWeekDay(String dateStr) {
    try {
        var sdf = new SimpleDateFormat("yyyy-MM-dd");
        var date = sdf.parse(dateStr);
        var cal = java.util.Calendar.getInstance();
        cal.setTime(date);
        var days = new String[]{"周日", "周一", "周二", "周三", "周四", "周五", "周六"};
        return days[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1];
    } catch (Exception e) {
        return "";
    }
}

String encodeURI(String s) {
    if (s == null) return "";
    var sb = "";
    for (var i = 0; i < s.length(); i++) {
        var ch = s.charAt(i);
        if (Character.isLetterOrDigit(ch) || "-_.~".indexOf(ch) >= 0) {
            sb += ch;
        } else {
            try {
                var bytes = ch.toString().getBytes("UTF-8");
                for (var j = 0; j < bytes.length; j++) {
                    sb += "%" + String.format("%02X", bytes[j] & 0xFF);
                }
            } catch (Exception e) {
                sb += ch;
            }
        }
    }
    return sb;
}