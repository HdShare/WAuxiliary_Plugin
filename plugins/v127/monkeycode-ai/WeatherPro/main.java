String apiKey = "";
String subscribedCities = "";
String pushTime = "08:00";
boolean dailyPushEnabled = false;
String lastPushDate = "";
boolean filterEnabled = false;

void onLoad() {
    apiKey = getString("api_key", "");
    subscribedCities = getString("subscribed_cities", "");
    pushTime = getString("push_time", "08:00");
    dailyPushEnabled = getBoolean("daily_push_enabled", false);
    lastPushDate = getString("last_push_date", "");
    filterEnabled = getBoolean("filter_enabled", false);
    log("天气Pro v1.1 key=" + (apiKey.isEmpty() ? "无" : "有") + " 订阅=" + subscribedCities + " 过滤=" + filterEnabled);
    toast(filterEnabled ? "天气Pro 已加载 (聊天过滤:开)" : "天气Pro 已加载");
}

void onUnload() {
    log("天气Pro 已卸载");
}

// ==================== 消息处理 ====================

void onHandleMsg(Object msgInfoBean) {
    if (!msgInfoBean.isText()) return;
    var content = msgInfoBean.getContent().trim();
    var talker = msgInfoBean.getTalker();
    log("天气Pro msg=[" + content + "] talker=" + talker);

    if (isWeatherCommand(content) && !isChatAllowed(talker)) return;

    if (matchCmd(content, "天气 ") || matchCmd(content, "天气查询 ")) {
        var city = getArgAfter(content);
        if (city.isEmpty()) { reply(talker, "请输入城市名，如: 天气 北京"); return; }
        if (apiKey.isEmpty()) { reply(talker, "请先设置 API Key。命令: 天气设置 key <Key>\n免费获取: dev.qweather.com"); return; }
        doWeatherQuery(talker, city);
        return;
    }
    if (matchCmd(content, "预报 ") || matchCmd(content, "天气预报 ")) {
        var city = getArgAfter(content);
        if (city.isEmpty()) { reply(talker, "请输入城市名，如: 预报 北京"); return; }
        if (apiKey.isEmpty()) { reply(talker, "请先设置 API Key"); return; }
        doForecastQuery(talker, city);
        return;
    }
    if (matchCmd(content, "订阅 ") || matchCmd(content, "订阅天气 ")) {
        var city = getArgAfter(content);
        if (city.isEmpty()) { reply(talker, "请输入城市名，如: 订阅 北京"); return; }
        if (apiKey.isEmpty()) { reply(talker, "请先设置 API Key"); return; }
        doSubscribeCity(talker, city);
        return;
    }

    if (content.equals("天气启用")) {
        addEnabledChat(talker);
        reply(talker, "已启用当前聊天");
        return;
    }
    if (content.equals("天气停用")) {
        removeEnabledChat(talker);
        reply(talker, "已停用当前聊天");
        return;
    }
    if (content.equals("天气过滤 开")) {
        filterEnabled = true;
        putBoolean("filter_enabled", true);
        reply(talker, "聊天过滤已开启");
        return;
    }
    if (content.equals("天气过滤 关")) {
        filterEnabled = false;
        putBoolean("filter_enabled", false);
        reply(talker, "聊天过滤已关闭");
        return;
    }

    if (content.equals("取消订阅") || content.equals("取消天气")) {
        if (subscribedCities.isEmpty()) { reply(talker, "当前没有订阅"); return; }
        subscribedCities = "";
        dailyPushEnabled = false;
        saveConfig();
        reply(talker, "已取消所有订阅");
        return;
    }
    if (content.equals("我的订阅") || content.equals("订阅列表")) {
        if (subscribedCities.isEmpty()) { reply(talker, "无订阅。命令: 订阅 <城市>"); return; }
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
                reply(talker, "推送已" + (dailyPushEnabled ? "开启" : "关闭"));
                return;
            }
        }
        var sk = apiKey.isEmpty() ? "未设置" : apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
        var ec = countEnabledChats();
        reply(talker, "设置:\nKey: " + sk + "\n推送: " + (dailyPushEnabled ? "开" : "关") + "\n时间: " + pushTime + "\n过滤: " + (filterEnabled ? "开" : "关") + " (" + ec + "个聊天)\n订阅: " + (subscribedCities.isEmpty() ? "0" : ("" + subscribedCities.split(";").length)) + "个城");
        return;
    }
    if (content.equals("天气帮助") || content.equals("天气Pro") || content.equals("天气pro")) {
        reply(talker, "命令:\n天气 <城市>  查询天气\n预报 <城市>  3日预报\n订阅 <城市>  订阅推送\n天气设置    查看/修改设置\n天气启用    启用当前聊天\n天气停用    停用当前聊天\n天气过滤 开/关  开关过滤\n取消订阅/我的订阅\n天气帮助 帮助");
        return;
    }

    checkDailyPush();
}

// ==================== 聊天过滤 ====================

boolean isWeatherCommand(String content) {
    return content.startsWith("天气") || content.startsWith("天气预报")
        || content.startsWith("预报") || content.startsWith("订阅")
        || content.equals("取消订阅") || content.equals("取消天气")
        || content.equals("我的订阅") || content.equals("订阅列表");
}

boolean isChatAllowed(String talker) {
    if (!filterEnabled) return true;
    var chats = getStringSet("enabled_chats", new java.util.HashSet());
    return chats.contains(talker);
}

void addEnabledChat(String talker) {
    var chats = getStringSet("enabled_chats", new java.util.HashSet());
    chats.add(talker);
    putStringSet("enabled_chats", chats);
    log("天气Pro 启用: " + talker);
}

void removeEnabledChat(String talker) {
    var chats = getStringSet("enabled_chats", new java.util.HashSet());
    chats.remove(talker);
    putStringSet("enabled_chats", chats);
    log("天气Pro 停用: " + talker);
}

int countEnabledChats() {
    var chats = getStringSet("enabled_chats", new java.util.HashSet());
    return chats.size();
}

void clearEnabledChats() {
    putStringSet("enabled_chats", new java.util.HashSet());
}

// ==================== 全角/半角空格兼容 ====================

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

boolean onClickSendBtn(String text) {
    return false;
}

// ==================== 天气查询 ====================

void doWeatherQuery(String talker, String city) {
    var url = "https://geoapi.qweather.com/v2/city/lookup?location=" + urlEncode(city) + "&key=" + apiKey;
    log("天气Pro GEO: " + url);
    get(url, null, 60, geoResp -> {
        if (geoResp == null || geoResp.isEmpty()) {
            reply(talker, "网络请求失败，请检查网络后重试");
            return;
        }
        try {
            var j = new org.json.JSONObject(geoResp);
            if (!"200".equals(j.optString("code"))) {
                reply(talker, "未找到城市: " + city);
                return;
            }
            var loc = j.optJSONArray("location").optJSONObject(0);
            var cityId = loc.optString("id");
            var name = loc.optString("name") + "," + loc.optString("adm1");
            var nowUrl = "https://devapi.qweather.com/v7/weather/now?location=" + cityId + "&key=" + apiKey;
            get(nowUrl, null, 60, nowResp -> {
                if (nowResp == null || nowResp.isEmpty()) {
                    reply(talker, "获取天气失败，请重试");
                    return;
                }
                try {
                    var n = new org.json.JSONObject(nowResp).optJSONObject("now");
                    if (n == null) {
                        reply(talker, "天气数据为空，请检查 API Key");
                        return;
                    }
                    var sb = "[天气] " + name + "\n";
                    sb += n.optString("text") + " " + n.optString("temp") + "C (体感" + n.optString("feelsLike") + "C)\n";
                    sb += "风向 " + n.optString("windDir") + " " + n.optString("windScale") + "级 风速" + n.optString("windSpeed") + "km/h\n";
                    sb += "湿度 " + n.optString("humidity") + "% 能见度" + n.optString("vis") + "km 气压" + n.optString("pressure") + "hPa";
                    reply(talker, sb);
                } catch (Exception e) {
                    reply(talker, "解析失败: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            reply(talker, "查询失败: " + e.getMessage());
        }
    });
}

// ==================== 多日预报 ====================

void doForecastQuery(String talker, String city) {
    var url = "https://geoapi.qweather.com/v2/city/lookup?location=" + urlEncode(city) + "&key=" + apiKey;
    get(url, null, 60, geoResp -> {
        if (geoResp == null || geoResp.isEmpty()) {
            reply(talker, "网络请求失败");
            return;
        }
        try {
            var j = new org.json.JSONObject(geoResp);
            if (!"200".equals(j.optString("code"))) {
                reply(talker, "未找到: " + city);
                return;
            }
            var loc = j.optJSONArray("location").optJSONObject(0);
            var name = loc.optString("name") + "," + loc.optString("adm1");
            var fcUrl = "https://devapi.qweather.com/v7/weather/3d?location=" + loc.optString("id") + "&key=" + apiKey;
            get(fcUrl, null, 60, fcResp -> {
                if (fcResp == null || fcResp.isEmpty()) {
                    reply(talker, "获取预报失败");
                    return;
                }
                try {
                    var daily = new org.json.JSONObject(fcResp).optJSONArray("daily");
                    if (daily == null || daily.length() == 0) {
                        reply(talker, "预报数据为空");
                        return;
                    }
                    var sb = "[3日预报] " + name;
                    for (var i = 0; i < daily.length(); i++) {
                        var d = daily.optJSONObject(i);
                        sb += "\n\n" + d.optString("fxDate") + " " + getWeekDay(d.optString("fxDate"));
                        sb += "\n  白天 " + d.optString("textDay") + " " + d.optString("tempMax") + "C";
                        sb += "\n  夜间 " + d.optString("textNight") + " " + d.optString("tempMin") + "C";
                        sb += "\n  " + d.optString("windDirDay") + d.optString("windScaleDay") + "级 湿度" + d.optString("humidity") + "%";
                        sb += "\n  紫外" + d.optString("uvIndex") + " 日出" + d.optString("sunrise") + " 日落" + d.optString("sunset");
                    }
                    reply(talker, sb);
                } catch (Exception e) { reply(talker, "解析预报失败"); }
            });
        } catch (Exception e) { reply(talker, "查询失败"); }
    });
}

// ==================== 城市订阅 ====================

void doSubscribeCity(String talker, String city) {
    var url = "https://geoapi.qweather.com/v2/city/lookup?location=" + urlEncode(city) + "&key=" + apiKey;
    get(url, null, 60, geoResp -> {
        if (geoResp == null || geoResp.isEmpty()) {
            reply(talker, "网络请求失败");
            return;
        }
        try {
            var j = new org.json.JSONObject(geoResp);
            if (!"200".equals(j.optString("code"))) {
                reply(talker, "未找到: " + city);
                return;
            }
            var loc = j.optJSONArray("location").optJSONObject(0);
            var cityId = loc.optString("id");
            var name = loc.optString("name") + "," + loc.optString("adm1");
            if (subscribedCities.contains(cityId)) {
                reply(talker, "已订阅 " + name);
                return;
            }
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
    log("每日推送 " + cities.length + "城");
}

void doPushOne(String name, String cityId) {
    var url = "https://devapi.qweather.com/v7/weather/now?location=" + cityId + "&key=" + apiKey;
    get(url, null, 60, resp -> {
        if (resp == null || resp.isEmpty()) return;
        try {
            var n = new org.json.JSONObject(resp).optJSONObject("now");
            if (n == null) return;
            notify("天气Pro", name + " " + n.optString("text") + " " + n.optString("temp") + "C");
        } catch (Exception e) {}
    });
}

// ==================== UI 设置面板 ====================

void openSettings() {
    var a = getTopActivity();
    if (a == null) { toast("无法打开设置面板"); return; }

    var scroll = new android.widget.ScrollView(a);
    var root = new android.widget.LinearLayout(a);
    root.setOrientation(1); // VERTICAL
    root.setPadding(40, 30, 40, 30);

    var title = new android.widget.TextView(a);
    title.setText("天气Pro 设置 v1.1");
    title.setTextSize(18);
    root.addView(title);

    addPad(root, 20);

    // === API Key ===
    var kl = new android.widget.TextView(a);
    kl.setText("API Key (和风天气):");
    kl.setTextSize(14);
    kl.setTextColor(0xFF333333);
    root.addView(kl);
    addPad(root, 4);

    var keyInput = new android.widget.EditText(a);
    keyInput.setText(apiKey);
    keyInput.setHint("免费注册: dev.qweather.com");
    keyInput.setSingleLine(true);
    root.addView(keyInput);

    addPad(root, 20);

    // === 推送设置 ===
    var pl = new android.widget.TextView(a);
    pl.setText("每日推送:");
    pl.setTextSize(14);
    pl.setTextColor(0xFF333333);
    root.addView(pl);
    addPad(root, 4);

    var pushRow = new android.widget.LinearLayout(a);
    pushRow.setOrientation(0); // HORIZONTAL
    pushRow.setGravity(16); // CENTER_VERTICAL

    var pushSwitch = new android.widget.Switch(a);
    pushSwitch.setChecked(dailyPushEnabled);
    pushRow.addView(pushSwitch);

    var timeInput = new android.widget.EditText(a);
    timeInput.setText(pushTime);
    timeInput.setHint("08:00");
    timeInput.setSingleLine(true);
    timeInput.setWidth(120);
    var tl = new android.widget.LinearLayout.LayoutParams(-2, -2); // WRAP_CONTENT
    tl.leftMargin = 16;
    pushRow.addView(timeInput, tl);
    root.addView(pushRow);

    addPad(root, 20);

    // === 聊天过滤 ===
    var fl = new android.widget.TextView(a);
    fl.setText("聊天过滤:");
    fl.setTextSize(14);
    fl.setTextColor(0xFF333333);
    root.addView(fl);
    addPad(root, 4);

    var filterRow = new android.widget.LinearLayout(a);
    filterRow.setOrientation(0);
    filterRow.setGravity(16);

    var filterSwitch = new android.widget.Switch(a);
    filterSwitch.setChecked(filterEnabled);
    filterRow.addView(filterSwitch);

    var flHint = new android.widget.TextView(a);
    flHint.setText("  开启后仅启用列表中的聊天响应");
    flHint.setTextSize(12);
    flHint.setTextColor(0xFF888888);
    filterRow.addView(flHint);
    root.addView(filterRow);

    addPad(root, 8);

    var cl = new android.widget.TextView(a);
    cl.setText("启用聊天列表 (每行一个 wxid):");
    cl.setTextSize(12);
    cl.setTextColor(0xFF666666);
    root.addView(cl);
    addPad(root, 4);

    var chatInput = new android.widget.EditText(a);
    var chats = getStringSet("enabled_chats", new java.util.HashSet());
    var csb = "";
    for (var wxid : chats) csb += wxid + "\n";
    chatInput.setText(csb);
    chatInput.setMinLines(4);
    chatInput.setGravity(48); // TOP
    root.addView(chatInput);

    addPad(root, 12);

    // 快速添加按钮行
    var btnRow = new android.widget.LinearLayout(a);
    btnRow.setOrientation(0);

    var addCurBtn = new android.widget.Button(a);
    addCurBtn.setText("添加当前聊天");
    addCurBtn.setTextSize(12);
    addCurBtn.setOnClickListener(new android.view.View.OnClickListener() {
        public void onClick(android.view.View v) {
            var t = getTargetTalker();
            if (t == null || t.isEmpty()) { toast("请先打开一个聊天"); return; }
            addEnabledChat(t);
            var c2 = getStringSet("enabled_chats", new java.util.HashSet());
            var s2 = "";
            for (var w : c2) s2 += w + "\n";
            chatInput.setText(s2);
            toast("已添加: " + t);
        }
    });
    btnRow.addView(addCurBtn);

    addPadH(btnRow, 8);

    var clearBtn = new android.widget.Button(a);
    clearBtn.setText("清空列表");
    clearBtn.setTextSize(12);
    clearBtn.setOnClickListener(new android.view.View.OnClickListener() {
        public void onClick(android.view.View v) {
            clearEnabledChats();
            chatInput.setText("");
            toast("已清空");
        }
    });
    btnRow.addView(clearBtn);
    root.addView(btnRow);

    addPad(root, 24);

    // === 保存 ===
    var saveBtn = new android.widget.Button(a);
    saveBtn.setText("保存设置");
    saveBtn.setTextSize(14);
    saveBtn.setOnClickListener(new android.view.View.OnClickListener() {
        public void onClick(android.view.View v) {
            apiKey = keyInput.getText().toString().trim();
            dailyPushEnabled = pushSwitch.isChecked();
            pushTime = timeInput.getText().toString().trim();
            if (pushTime.isEmpty()) pushTime = "08:00";
            filterEnabled = filterSwitch.isChecked();
            putString("api_key", apiKey);
            putBoolean("daily_push_enabled", dailyPushEnabled);
            putString("push_time", pushTime);
            putBoolean("filter_enabled", filterEnabled);
            saveConfig();

            var text = chatInput.getText().toString().trim();
            var newChats = new java.util.HashSet();
            if (!text.isEmpty()) {
                var lines = text.split("\n");
                for (var i = 0; i < lines.length; i++) {
                    var line = lines[i].trim();
                    if (!line.isEmpty()) newChats.add(line);
                }
            }
            putStringSet("enabled_chats", newChats);
            toast("设置已保存");
        }
    });
    root.addView(saveBtn);

    scroll.addView(root);

    var listener = new android.content.DialogInterface.OnClickListener() {
        public void onClick(android.content.DialogInterface dialog, int which) {}
    };
    new android.app.AlertDialog.Builder(a)
        .setView(scroll)
        .setPositiveButton("关闭", listener)
        .show();
}

void addPad(android.widget.LinearLayout parent, int h) {
    var v = new android.widget.TextView(parent.getContext());
    v.setHeight(h);
    parent.addView(v);
}

void addPadH(android.widget.LinearLayout parent, int w) {
    var v = new android.widget.TextView(parent.getContext());
    v.setWidth(w);
    parent.addView(v);
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

String urlEncode(String s) {
    if (s == null) return "";
    try {
        return java.net.URLEncoder.encode(s, "UTF-8");
    } catch (Exception e) {
        return s;
    }
}
