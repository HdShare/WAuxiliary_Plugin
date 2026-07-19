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

    // 过滤:仅启用列表中聊天
    if (isWeatherCommand(content) && !isChatAllowed(talker)) return;

    if (matchCmd(content, "天气 ") || matchCmd(content, "天气查询 ")) {
        var city = getArgAfter(content);
        log("天气Pro 天气: " + city);
        if (city.isEmpty()) { reply(talker, "请输入城市名，如: 天气 北京"); return; }
        if (apiKey.isEmpty()) { reply(talker, "请先设置 API Key。命令: 天气设置 key <Key>\n免费获取: dev.qweather.com"); return; }
        doWeatherQuery(talker, city);
        return;
    }
    if (matchCmd(content, "预报 ") || matchCmd(content, "天气预报 ")) {
        var city = getArgAfter(content);
        log("天气Pro 预报: " + city);
        if (city.isEmpty()) { reply(talker, "请输入城市名，如: 预报 北京"); return; }
        if (apiKey.isEmpty()) { reply(talker, "请先设置 API Key"); return; }
        doForecastQuery(talker, city);
        return;
    }
    if (matchCmd(content, "订阅 ") || matchCmd(content, "订阅天气 ")) {
        var city = getArgAfter(content);
        log("天气Pro 订阅: " + city);
        if (city.isEmpty()) { reply(talker, "请输入城市名，如: 订阅 北京"); return; }
        if (apiKey.isEmpty()) { reply(talker, "请先设置 API Key"); return; }
        doSubscribeCity(talker, city);
        return;
    }

    // 聊天过滤管理
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
        reply(talker, "聊天过滤已开启，仅启用列表中的聊天响应");
        return;
    }
    if (content.equals("天气过滤 关")) {
        filterEnabled = false;
        putBoolean("filter_enabled", false);
        reply(talker, "聊天过滤已关闭，所有聊天均可使用");
        return;
    }

    if (content.equals("取消订阅") || content.equals("取消天气")) {
        log("天气Pro 取消订阅");
        if (subscribedCities.isEmpty()) { reply(talker, "当前没有订阅"); return; }
        subscribedCities = "";
        dailyPushEnabled = false;
        saveConfig();
        reply(talker, "已取消所有订阅");
        return;
    }
    if (content.equals("我的订阅") || content.equals("订阅列表")) {
        log("天气Pro 查看订阅");
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
        var ec = getEnabledChatCount();
        reply(talker, "设置:\nKey: " + sk + "\n推送: " + (dailyPushEnabled ? "开" : "关") + "\n时间: " + pushTime + "\n过滤: " + (filterEnabled ? "开" : "关") + " (" + ec + "个聊天)\n订阅: " + (subscribedCities.isEmpty() ? "0" : ("" + subscribedCities.split(";").length)) + "个城\n\n命令: 天气设置 key/time/推送");
        return;
    }
    if (content.equals("天气帮助") || content.equals("天气Pro") || content.equals("天气pro")) {
        var help = "命令:\n";
        help += "天气 <城市>  查询天气\n";
        help += "预报 <城市>  3日预报\n";
        help += "订阅 <城市>  订阅推送\n";
        help += "天气设置    查看/修改设置\n";
        help += "天气启用    启用当前聊天\n";
        help += "天气停用    停用当前聊天\n";
        help += "天气过滤 开/关  开关过滤\n";
        help += "取消订阅/我的订阅\n";
        help += "天气帮助 帮助";
        reply(talker, help);
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
    log("天气Pro 启用: " + talker + " 共" + chats.size() + "个");
}

void removeEnabledChat(String talker) {
    var chats = getStringSet("enabled_chats", new java.util.HashSet());
    chats.remove(talker);
    putStringSet("enabled_chats", chats);
    log("天气Pro 停用: " + talker + " 共" + chats.size() + "个");
}

int getEnabledChatCount() {
    var chats = getStringSet("enabled_chats", new java.util.HashSet());
    return chats.size();
}

String getEnabledChatNames() {
    var chats = getStringSet("enabled_chats", new java.util.HashSet());
    if (chats.isEmpty()) return "(无)";
    var sb = "";
    for (var wxid : chats) {
        var name = getFriendDisplayName(wxid);
        if (name == null || name.isEmpty()) name = wxid;
        sb += name + "\n";
    }
    return sb;
}

void clearEnabledChats() {
    putStringSet("enabled_chats", new java.util.HashSet());
    log("天气Pro 清空启用列表");
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
                reply(talker, "未找到城市: " + city + "\n提示: 用城市名或城市ID查询");
                return;
            }
            var loc = j.optJSONArray("location").optJSONObject(0);
            var cityId = loc.optString("id");
            var name = loc.optString("name") + "," + loc.optString("adm1");
            var nowUrl = "https://devapi.qweather.com/v7/weather/now?location=" + cityId + "&key=" + apiKey;
            log("天气Pro NOW: " + nowUrl);
            get(nowUrl, null, 60, nowResp -> {
                if (nowResp == null || nowResp.isEmpty()) {
                    reply(talker, "获取天气失败，请重试");
                    return;
                }
                try {
                    var n = new org.json.JSONObject(nowResp).optJSONObject("now");
                    if (n == null) {
                        reply(talker, "天气数据为空，请检查 API Key 是否正确");
                        return;
                    }
                    var sb = "[天气] " + name + "\n";
                    sb += "天气 " + n.optString("text") + "\n";
                    sb += "温度 " + n.optString("temp") + "C (体感" + n.optString("feelsLike") + "C)\n";
                    sb += "风向 " + n.optString("windDir") + " " + n.optString("windScale") + "级\n";
                    sb += "风速 " + n.optString("windSpeed") + "km/h\n";
                    sb += "湿度 " + n.optString("humidity") + "%\n";
                    sb += "能见度 " + n.optString("vis") + "km\n";
                    sb += "气压 " + n.optString("pressure") + "hPa";
                    reply(talker, sb);
                } catch (Exception e) {
                    reply(talker, "解析天气失败: " + e.getMessage());
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
            reply(talker, "网络请求失败，请重试");
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
            reply(talker, "已订阅 " + name + "\n每日 " + pushTime + " 推送天气");
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
    try {
        var scroll = new android.widget.ScrollView(a);
        var root = new android.widget.LinearLayout(a);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));

        var title = new android.widget.TextView(a);
        title.setText("天气Pro 设置 v1.1");
        title.setTextSize(18);
        title.setTextColor(0xFF333333);
        root.addView(title);
        root.addView(gap(8));

        // --- API Key ---
        root.addView(sectionLabel(a, "和风天气 API Key"));
        root.addView(gap(4));
        var keyInput = new android.widget.EditText(a);
        keyInput.setText(apiKey);
        keyInput.setHint("在此输入 API Key");
        keyInput.setSingleLine(true);
        root.addView(keyInput);
        var tip = new android.widget.TextView(a);
        tip.setText("免费注册获取: dev.qweather.com");
        tip.setTextSize(11);
        tip.setTextColor(0xFF888888);
        root.addView(tip);
        root.addView(gap(12));

        // --- 推送设置 ---
        root.addView(sectionLabel(a, "每日天气推送"));
        root.addView(gap(4));
        var pushRow = new android.widget.LinearLayout(a);
        pushRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        var pl = new android.widget.TextView(a);
        pl.setText("开启推送");
        pl.setTextSize(14);
        pushRow.addView(pl, lp(0));
        var pushSwitch = new android.widget.Switch(a);
        pushSwitch.setChecked(dailyPushEnabled);
        pushRow.addView(pushSwitch);
        root.addView(pushRow);
        root.addView(gap(4));

        var timeRow = new android.widget.LinearLayout(a);
        timeRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        var tl = new android.widget.TextView(a);
        tl.setText("推送时间");
        tl.setTextSize(14);
        timeRow.addView(tl, lp(0));
        var timeInput = new android.widget.EditText(a);
        timeInput.setText(pushTime);
        timeInput.setHint("08:00");
        timeInput.setSingleLine(true);
        timeInput.setWidth(dp(80));
        timeRow.addView(timeInput);
        root.addView(timeRow);
        root.addView(gap(4));

        var stext = new android.widget.TextView(a);
        stext.setText("已订阅: " + (subscribedCities.isEmpty() ? "无" : ("" + subscribedCities.split(";").length)) + "个城市");
        stext.setTextSize(11);
        stext.setTextColor(0xFF888888);
        root.addView(stext);
        root.addView(gap(16));

        // --- 聊天过滤 ---
        root.addView(sectionLabel(a, "聊天过滤"));
        root.addView(gap(4));
        var filterRow = new android.widget.LinearLayout(a);
        filterRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        var fl = new android.widget.TextView(a);
        fl.setText("仅启用聊天响应");
        fl.setTextSize(14);
        filterRow.addView(fl, lp(0));
        var filterSwitch = new android.widget.Switch(a);
        filterSwitch.setChecked(filterEnabled);
        filterRow.addView(filterSwitch);
        root.addView(filterRow);
        root.addView(gap(4));

        var enabledLabel = new android.widget.TextView(a);
        enabledLabel.setText("启用聊天列表 (长按编辑):");
        enabledLabel.setTextSize(12);
        enabledLabel.setTextColor(0xFF666666);
        root.addView(enabledLabel);
        root.addView(gap(4));

        var chatInput = new android.widget.EditText(a);
        chatInput.setText(getEnabledChatNames());
        chatInput.setHint("每行一个 wxid 或联系人群组名称");
        chatInput.setMinLines(3);
        chatInput.setGravity(android.view.Gravity.TOP);
        root.addView(chatInput);
        root.addView(gap(4));

        // 快速添加按钮行
        var btnRow = new android.widget.LinearLayout(a);
        btnRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);

        var addCurBtn = new android.widget.Button(a);
        addCurBtn.setText("添加当前");
        addCurBtn.setTextSize(12);
        addCurBtn.setOnClickListener(v -> {
            var t = getTargetTalker();
            if (t == null || t.isEmpty()) { toast("请先打开一个聊天"); return; }
            addEnabledChat(t);
            chatInput.setText(getEnabledChatNames());
            toast("已添加: " + t);
        });
        btnRow.addView(addCurBtn);
        root.addView(gap(4));

        var addFriendBtn = new android.widget.Button(a);
        addFriendBtn.setText("选联系人");
        addFriendBtn.setTextSize(12);
        addFriendBtn.setOnClickListener(v -> showContactPicker(a, chatInput));
        btnRow.addView(addFriendBtn);
        root.addView(gap(4));

        var addGroupBtn = new android.widget.Button(a);
        addGroupBtn.setText("选群聊");
        addGroupBtn.setTextSize(12);
        addGroupBtn.setOnClickListener(v -> showGroupPicker(a, chatInput));
        btnRow.addView(addGroupBtn);
        root.addView(gap(4));

        var clearBtn = new android.widget.Button(a);
        clearBtn.setText("清空");
        clearBtn.setTextSize(12);
        clearBtn.setOnClickListener(v -> {
            clearEnabledChats();
            chatInput.setText("");
            toast("已清空启用列表");
        });
        btnRow.addView(clearBtn);
        root.addView(btnRow);
        root.addView(gap(16));

        // --- 保存 ---
        var saveBtn = new android.widget.Button(a);
        saveBtn.setText("保存设置");
        saveBtn.setTextSize(14);
        saveBtn.setOnClickListener(v -> {
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
            parseAndSaveEnabledChats(chatInput.getText().toString());
            toast("设置已保存");
        });
        root.addView(saveBtn);

        scroll.addView(root);
        var dlg = new android.app.AlertDialog.Builder(a)
            .setView(scroll)
            .setPositiveButton("关闭", null)
            .create();
        dlg.show();
    } catch (Exception e) {
        toast("面板错误: " + e.getMessage());
        log("天气Pro 面板错误: " + e.getMessage());
    }
}

void showContactPicker(android.app.Activity a, android.widget.EditText chatInput) {
    try {
        var friends = getFriendList();
        if (friends == null || friends.isEmpty()) { toast("无好友数据"); return; }
        var names = new String[friends.size()];
        for (var i = 0; i < friends.size(); i++) {
            var wxid = ((me.hd.wauxv.plugin.api.PluginStruct.FriendInfo) friends.get(i)).getWxid();
            var name = getFriendDisplayName(wxid);
            names[i] = (name != null && !name.isEmpty() ? name : wxid) + "|" + wxid;
        }
        var builder = new android.app.AlertDialog.Builder(a);
        builder.setTitle("选择联系人");
        builder.setItems(names, (dialog, which) -> {
            var parts = names[which].split("\\|");
            var wxid = parts[parts.length - 1];
            addEnabledChat(wxid);
            chatInput.setText(getEnabledChatNames());
            toast("已添加");
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    } catch (Exception e) {
        toast("获取联系人失败: " + e.getMessage());
    }
}

void showGroupPicker(android.app.Activity a, android.widget.EditText chatInput) {
    try {
        var groups = getGroupList();
        if (groups == null || groups.isEmpty()) { toast("无群聊数据"); return; }
        var names = new String[groups.size()];
        for (var i = 0; i < groups.size(); i++) {
            var g = (me.hd.wauxv.plugin.api.PluginStruct.GroupInfo) groups.get(i);
            names[i] = g.getName() + "|" + g.getWxid();
        }
        var builder = new android.app.AlertDialog.Builder(a);
        builder.setTitle("选择群聊");
        builder.setItems(names, (dialog, which) -> {
            var parts = names[which].split("\\|");
            var wxid = parts[parts.length - 1];
            addEnabledChat(wxid);
            chatInput.setText(getEnabledChatNames());
            toast("已添加");
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    } catch (Exception e) {
        toast("获取群聊失败: " + e.getMessage());
    }
}

void parseAndSaveEnabledChats(String text) {
    if (text == null || text.trim().isEmpty() || text.equals("(无)")) {
        clearEnabledChats();
        return;
    }
    var lines = text.split("\n");
    var chats = new java.util.HashSet();
    for (var i = 0; i < lines.length; i++) {
        var line = lines[i].trim();
        if (line.isEmpty()) continue;
        if (line.startsWith("wxid_")) {
            chats.add(line);
        }
    }
    putStringSet("enabled_chats", chats);
}

// ==================== UI 工具 ====================

android.widget.TextView sectionLabel(android.content.Context ctx, String text) {
    var tv = new android.widget.TextView(ctx);
    tv.setText(text);
    tv.setTextSize(15);
    tv.setTextColor(0xFF1976D2);
    tv.setPadding(0, dp(8), 0, dp(2));
    return tv;
}

android.widget.LinearLayout gap(int dp) {
    var v = new android.widget.LinearLayout(android.app.Activity.getTopActivity());
    v.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
        android.widget.LinearLayout.LayoutParams.MATCH_PARENT, dp));
    return v;
}

android.widget.LinearLayout.LayoutParams lp(int weight) {
    return new android.widget.LinearLayout.LayoutParams(
        0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, weight > 0 ? 1f : 1f);
}

int dp(int px) {
    var density = android.content.res.Resources.getSystem().getDisplayMetrics().density;
    return (int) (px * density);
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
