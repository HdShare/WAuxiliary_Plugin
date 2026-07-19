String apiKey = "";
String subscribedCities = "";
String pushTime = "08:00";
boolean dailyPushEnabled = false;
String lastPushDate = "";

void onLoad() {
    apiKey = getString("api_key", "");
    subscribedCities = getString("subscribed_cities", "");
    pushTime = getString("push_time", "08:00");
    dailyPushEnabled = getBoolean("daily_push_enabled", false);
    lastPushDate = getString("last_push_date", "");
    log("天气Pro v1.0 已加载 key=" + (apiKey.isEmpty() ? "无" : "有") + " 订阅=" + subscribedCities);
    toast("天气Pro 已加载");
}

void onUnload() {
    log("天气Pro 已卸载");
}

// ==================== 消息处理 ====================

void onHandleMsg(Object msgInfoBean) {
    if (!msgInfoBean.isText()) return;
    var content = msgInfoBean.getContent().trim();
    var talker = msgInfoBean.getTalker();
    log("天气Pro msg=[" + content + "] talker=" + talker + " isSend=" + msgInfoBean.isSend());

    if (matchCmd(content, "天气 ") || matchCmd(content, "天气查询 ")) {
        var city = getArgAfter(content);
        log("天气Pro 天气查询: " + city);
        if (city.isEmpty()) { reply(talker, "请输入城市名，如：天气 北京"); return; }
        if (apiKey.isEmpty()) { reply(talker, "请先设置 API Key。发送「天气设置 key <Key>」免费获取: dev.qweather.com"); return; }
        doWeatherQuery(talker, city);
        return;
    }
    if (matchCmd(content, "预报 ") || matchCmd(content, "天气预报 ")) {
        var city = getArgAfter(content);
        log("天气Pro 预报查询: " + city);
        if (city.isEmpty()) { reply(talker, "请输入城市名，如：预报 北京"); return; }
        if (apiKey.isEmpty()) { reply(talker, "请先设置 API Key"); return; }
        doForecastQuery(talker, city);
        return;
    }
    if (matchCmd(content, "订阅 ") || matchCmd(content, "订阅天气 ")) {
        var city = getArgAfter(content);
        log("天气Pro 订阅城市: " + city);
        if (city.isEmpty()) { reply(talker, "请输入城市名，如：订阅 北京"); return; }
        if (apiKey.isEmpty()) { reply(talker, "请先设置 API Key"); return; }
        doSubscribeCity(talker, city);
        return;
    }
    if (content.equals("取消订阅") || content.equals("取消天气")) {
        log("天气Pro 取消订阅");
        if (subscribedCities.isEmpty()) { reply(talker, "当前没有订阅任何城市"); return; }
        subscribedCities = "";
        dailyPushEnabled = false;
        saveConfig();
        reply(talker, "已取消所有订阅");
        return;
    }
    if (content.equals("我的订阅") || content.equals("订阅列表")) {
        log("天气Pro 查看订阅");
        if (subscribedCities.isEmpty()) { reply(talker, "当前没有订阅。发「订阅 城市名」订阅"); return; }
        var cities = subscribedCities.split(";");
        var sb = "订阅列表(" + cities.length + "):\n";
        for (var i = 0; i < cities.length; i++) sb += (i + 1) + ". " + cities[i].split("\\|")[0] + "\n";
        sb += "推送: " + (dailyPushEnabled ? "开" : "关") + " | 时间: " + pushTime;
        reply(talker, sb);
        return;
    }
    if (content.startsWith("天气设置")) {
        var parts = content.split("\\s+");
        if (parts.length >= 3) {
            if (parts[1].equals("key")) {
                apiKey = parts[2];
                putString("api_key", apiKey);
                reply(talker, "API Key 已设置");
                return;
            }
            if (parts[1].equals("推送时间") || parts[1].equals("时间")) {
                pushTime = parts[2];
                putString("push_time", pushTime);
                reply(talker, "推送时间已设为 " + pushTime);
                return;
            }
            if (parts[1].equals("推送") || parts[1].equals("开关")) {
                dailyPushEnabled = "开,开启,on,1".contains(parts[2]);
                saveConfig();
                reply(talker, "每日推送已" + (dailyPushEnabled ? "开启" : "关闭"));
                return;
            }
        }
        var sk = apiKey.isEmpty() ? "未设置" : apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
        reply(talker, "设置:\nKey: " + sk + "\n推送: " + (dailyPushEnabled ? "开" : "关") + "\n时间: " + pushTime + "\n订阅: " + (subscribedCities.isEmpty() ? "0" : subscribedCities.split(";").length) + "个\n\n命令: 天气设置 key/time/推送");
        return;
    }
    if (content.equals("天气帮助") || content.equals("天气Pro") || content.equals("天气pro")) {
        reply(talker, "命令:\n天气 <城市>  查询天气\n预报 <城市>  3日预报\n订阅 <城市>  订阅推送\n取消订阅  取消\n我的订阅  查看\n天气设置  设置\n天气帮助  帮助");
        return;
    }

    checkDailyPush();
}

// ==================== 兼容全角/半角空格 ====================

boolean matchCmd(String content, String prefix) {
    if (content.startsWith(prefix)) return true;
    return content.startsWith(prefix.replace(' ', '\u3000'));
}

String getArgAfter(String content) {
    var idx = content.indexOf(" ");
    if (idx < 0) idx = content.indexOf('\u3000');
    if (idx < 0) return "";
    return content.substring(idx + 1).trim();
}

// ==================== onClickSendBtn ====================

boolean onClickSendBtn(String text) {
    return false;
}

// ==================== 天气查询 ====================

void doWeatherQuery(String talker, String city) {
    var url = "https://geoapi.qweather.com/v2/city/lookup?location=" + encodeURI(city) + "&key=" + apiKey;
    log("天气Pro GEO: " + url);
    get(url, null, geoResp -> {
        try {
            var j = new org.json.JSONObject(geoResp);
            if (!j.optString("code").equals("200")) { reply(talker, "未找到城市: " + city); return; }
            var loc = j.optJSONArray("location").optJSONObject(0);
            var cityId = loc.optString("id");
            var name = loc.optString("name") + "," + loc.optString("adm1");
            var nowUrl = "https://devapi.qweather.com/v7/weather/now?location=" + cityId + "&key=" + apiKey;
            log("天气Pro NOW: " + nowUrl);
            get(nowUrl, null, nowResp -> {
                try {
                    var n = new org.json.JSONObject(nowResp).optJSONObject("now");
                    if (n == null) { reply(talker, "查询失败"); return; }
                    var sb = "[天气] " + name + "\n";
                    sb += n.optString("text") + " " + n.optString("temp") + "°C (体感" + n.optString("feelsLike") + "°C)\n";
                    sb += "风向 " + n.optString("windDir") + " " + n.optString("windScale") + "级 风速" + n.optString("windSpeed") + "km/h\n";
                    sb += "湿度 " + n.optString("humidity") + "% 能见度" + n.optString("vis") + "km 气压" + n.optString("pressure") + "hPa";
                    reply(talker, sb);
                } catch (Exception e) { reply(talker, "查询失败: " + e.getMessage()); }
            });
        } catch (Exception e) { reply(talker, "查询失败: " + e.getMessage()); }
    });
}

// ==================== 多日预报 ====================

void doForecastQuery(String talker, String city) {
    var url = "https://geoapi.qweather.com/v2/city/lookup?location=" + encodeURI(city) + "&key=" + apiKey;
    log("天气Pro GEO: " + url);
    get(url, null, geoResp -> {
        try {
            var j = new org.json.JSONObject(geoResp);
            if (!j.optString("code").equals("200")) { reply(talker, "未找到: " + city); return; }
            var loc = j.optJSONArray("location").optJSONObject(0);
            var name = loc.optString("name") + "," + loc.optString("adm1");
            var fcUrl = "https://devapi.qweather.com/v7/weather/3d?location=" + loc.optString("id") + "&key=" + apiKey;
            get(fcUrl, null, fcResp -> {
                try {
                    var daily = new org.json.JSONObject(fcResp).optJSONArray("daily");
                    if (daily == null || daily.length() == 0) { reply(talker, "预报失败"); return; }
                    var sb = "[3日预报] " + name;
                    for (var i = 0; i < daily.length(); i++) {
                        var d = daily.optJSONObject(i);
                        sb += "\n\n" + d.optString("fxDate") + " " + getWeekDay(d.optString("fxDate"));
                        sb += "\n  白天 " + d.optString("textDay") + " " + d.optString("tempMax") + "°C";
                        sb += "\n  夜间 " + d.optString("textNight") + " " + d.optString("tempMin") + "°C";
                        sb += "\n  " + d.optString("windDirDay") + d.optString("windScaleDay") + "级 湿度" + d.optString("humidity") + "%";
                        sb += "\n  紫外" + d.optString("uvIndex") + " 日出" + d.optString("sunrise") + " 日落" + d.optString("sunset");
                    }
                    reply(talker, sb);
                } catch (Exception e) { reply(talker, "预报失败"); }
            });
        } catch (Exception e) { reply(talker, "查询失败"); }
    });
}

// ==================== 城市订阅 ====================

void doSubscribeCity(String talker, String city) {
    var url = "https://geoapi.qweather.com/v2/city/lookup?location=" + encodeURI(city) + "&key=" + apiKey;
    get(url, null, geoResp -> {
        try {
            var j = new org.json.JSONObject(geoResp);
            if (!j.optString("code").equals("200")) { reply(talker, "未找到: " + city); return; }
            var loc = j.optJSONArray("location").optJSONObject(0);
            var cityId = loc.optString("id");
            var name = loc.optString("name") + "," + loc.optString("adm1");
            if (subscribedCities.contains(cityId)) { reply(talker, "已订阅 " + name); return; }
            subscribedCities = subscribedCities.isEmpty() ? name + "|" + cityId : subscribedCities + ";" + name + "|" + cityId;
            dailyPushEnabled = true;
            saveConfig();
            reply(talker, "已订阅 " + name + " 每日" + pushTime + "推送");
        } catch (Exception e) { reply(talker, "订阅失败"); }
    });
}

// ==================== 每日推送 ====================

void checkDailyPush() {
    if (!dailyPushEnabled || subscribedCities.isEmpty() || apiKey.isEmpty()) return;
    var today = new java.text.SimpleDateFormat("yyyyMMdd").format(new java.util.Date());
    if (today.equals(lastPushDate)) return;
    if (new java.text.SimpleDateFormat("HH:mm").format(new java.util.Date()).compareTo(pushTime) < 0) return;
    lastPushDate = today;
    putString("last_push_date", today);
    var cities = subscribedCities.split(";");
    for (var i = 0; i < cities.length; i++) {
        var parts = cities[i].split("\\|");
        if (parts.length < 2) continue;
        doPushOne(parts[0], parts[1]);
    }
    log("每日推送完成 " + cities.length + "城");
}

void doPushOne(String name, String cityId) {
    var url = "https://devapi.qweather.com/v7/weather/now?location=" + cityId + "&key=" + apiKey;
    get(url, null, resp -> {
        try {
            var n = new org.json.JSONObject(resp).optJSONObject("now");
            if (n == null) return;
            notify("天气Pro", name + " " + n.optString("text") + " " + n.optString("temp") + "°C");
        } catch (Exception e) {}
    });
}

// ==================== UI 面板 ====================

void openSettings() {
    var a = getTopActivity();
    if (a == null) { toast("无法打开面板"); return; }
    try {
        var root = new android.widget.LinearLayout(a);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setPadding(50, 40, 50, 40);

        var keyInput = new android.widget.EditText(a);
        keyInput.setText(apiKey);
        keyInput.setHint("API Key");

        var pushSwitch = new android.widget.Switch(a);
        pushSwitch.setChecked(dailyPushEnabled);

        var timeInput = new android.widget.EditText(a);
        timeInput.setText(pushTime);

        var title = new android.widget.TextView(a);
        title.setText("天气Pro 设置");
        title.setTextSize(18);
        root.addView(title);

        var kl = new android.widget.TextView(a);
        kl.setText("和风天气 API Key:");
        root.addView(kl);
        root.addView(keyInput);

        var pl = new android.widget.TextView(a);
        pl.setText("每日推送:");
        root.addView(pl);
        root.addView(pushSwitch);

        var tl = new android.widget.TextView(a);
        tl.setText("推送时间 (HH:mm):");
        root.addView(tl);
        root.addView(timeInput);

        var saveBtn = new android.widget.Button(a);
        saveBtn.setText("保存");
        saveBtn.setOnClickListener(v -> {
            apiKey = keyInput.getText().toString().trim();
            dailyPushEnabled = pushSwitch.isChecked();
            pushTime = timeInput.getText().toString().trim();
            if (pushTime.isEmpty()) pushTime = "08:00";
            putString("api_key", apiKey);
            putBoolean("daily_push_enabled", dailyPushEnabled);
            putString("push_time", pushTime);
            saveConfig();
            toast("已保存");
        });
        root.addView(saveBtn);

        var dlg = new android.app.AlertDialog.Builder(a).create();
        dlg.setView(root);
        dlg.show();
    } catch (Exception e) { toast("面板错误: " + e.getMessage()); }
}

// ==================== 工具方法 ====================

void reply(String talker, String msg) {
    if (talker != null && !talker.isEmpty()) sendText(talker, msg);
    else toast(msg);
}

void saveConfig() {
    putString("subscribed_cities", subscribedCities);
    putBoolean("daily_push_enabled", dailyPushEnabled);
}

String getWeekDay(String dateStr) {
    try {
        var sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
        var cal = java.util.Calendar.getInstance();
        cal.setTime(sdf.parse(dateStr));
        var days = new String[]{"周日","周一","周二","周三","周四","周五","周六"};
        return days[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1];
    } catch (Exception e) { return ""; }
}

String encodeURI(String s) {
    if (s == null) return "";
    var sb = "";
    for (var i = 0; i < s.length(); i++) {
        var ch = s.charAt(i);
        if (Character.isLetterOrDigit(ch) || "-_.~".indexOf(ch) >= 0) sb += ch;
        else {
            try {
                var bytes = ch.toString().getBytes("UTF-8");
                for (var j = 0; j < bytes.length; j++) sb += "%" + String.format("%02X", bytes[j] & 0xFF);
            } catch (Exception e2) { sb += ch; }
        }
    }
    return sb;
}