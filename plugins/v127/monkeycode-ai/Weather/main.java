import org.json.JSONObject;

void onLoad() {
    log("===== 天气查询插件 v1.0.0 已加载 =====");
}

void onHandleMsg(Object msgInfoBean) {
    log("天气插件 onHandleMsg 触发");
    if (msgInfoBean.isSend()) { log("天气插件: 跳过自己发送的消息"); return; }
    if (!msgInfoBean.isText()) { log("天气插件: 跳过非文本消息"); return; }
    var content = msgInfoBean.getContent();
    var talker = msgInfoBean.getTalker();
    log("天气插件: 收到消息 = [" + content + "] from=" + talker);

    if (content.startsWith("天气 ")) {
        log("天气插件: 匹配到「天气 」命令");
    } else if (content.startsWith("天气查询 ")) {
        log("天气插件: 匹配到「天气查询 」命令");
    } else {
        log("天气插件: 未匹配任何命令，跳过");
        return;
    }

    var city = content.substring(content.indexOf(" ") + 1).trim();
    if (city.isEmpty()) {
        sendText(talker, "请输入城市名，如：天气 北京");
        return;
    }

    var url = "https://wttr.in/" + encodeURI(city) + "?format=j1&lang=zh";
    log("天气插件: 查询城市=" + city + " url=" + url);

    get(url, null, respContent -> {
        log("天气插件: 收到 wttr.in 响应");
        try {
            var json = new JSONObject(respContent);
            var current = json.optJSONArray("current_condition").optJSONObject(0);

            if (current == null) {
                log("天气插件: 未找到城市数据");
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
            log("天气插件: 发送天气结果成功");
        } catch (Exception e) {
            log("天气插件: 解析失败 " + e.toString());
            sendText(talker, "天气查询失败，请检查城市名是否正确");
        }
    });
}

void openSettings() {
    log("天气插件: openSettings 被调用");
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