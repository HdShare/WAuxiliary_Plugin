import org.json.JSONArray;
import org.json.JSONObject;

void onLoad() {}

void onHandleMsg(Object msgInfoBean) {
    if (!msgInfoBean.isText()) return;

    String talker = msgInfoBean.getTalker();
    String content = msgInfoBean.getContent();

    if (!content.startsWith("油价")) return;

    String region = content.substring("油价".length()).trim();
    if (region.isEmpty()) {
        sendText(talker, "请按格式输入：油价 [地区名]，例如：油价 北京");
        return;
    }

    fetchOilPrice(talker, region);
}

void fetchOilPrice(String talker, String queryRegion) {
    get("https://v2.xxapi.cn/api/oilPrice", null, responseBody -> {
        if (responseBody == null) {
            sendText(talker, "查询油价失败：网络请求异常，请稍后重试。");
            return;
        }
        String reply = parseOilPriceData(responseBody, queryRegion);
        sendText(talker, reply);
    });
}

String parseOilPriceData(String jsonStr, String queryRegion) {
    try {
        JSONObject jsonObject = new JSONObject(jsonStr);
        if (jsonObject.optInt("code", -1) != 200) {
            return "查询失败：" + jsonObject.optString("msg", "未知错误");
        }

        JSONArray dataArray = jsonObject.getJSONArray("data");
        if (dataArray == null || dataArray.length() == 0) return "当前无油价数据。";

        JSONObject targetRegion = null;
        for (int i = 0; i < dataArray.length(); i++) {
            JSONObject regionObj = dataArray.getJSONObject(i);
            String regionName = regionObj.optString("regionName", "");
            if (regionName.toLowerCase().contains(queryRegion.toLowerCase()) ||
                queryRegion.toLowerCase().contains(regionName.toLowerCase())) {
                targetRegion = regionObj;
                break;
            }
        }

        if (targetRegion == null) {
            return "未找到地区 [" + queryRegion + "] 的油价信息。";
        }

        return formatOilPriceMessage(targetRegion);
    } catch (Exception e) {
        return "查询油价失败：数据解析错误。";
    }
}

String formatOilPriceMessage(JSONObject regionObj) {
    String regionName = regionObj.optString("regionName", "未知地区");
    String date = regionObj.optString("date", "未知日期");

    String p92 = regionObj.optDouble("n92", 0) > 0 ? String.format("%.2f 元/升", regionObj.optDouble("n92")) : "暂无数据";
    String p95 = regionObj.optDouble("n95", 0) > 0 ? String.format("%.2f 元/升", regionObj.optDouble("n95")) : "暂无数据";
    String p98 = regionObj.optDouble("n98", 0) > 0 ? String.format("%.2f 元/升", regionObj.optDouble("n98")) : "暂无数据";
    String p0  = regionObj.optDouble("n0", 0) > 0 ? String.format("%.2f 元/升", regionObj.optDouble("n0")) : "暂无数据";
    String p89 = regionObj.optDouble("n89", 0) > 0 ? String.format("%.2f 元/升", regionObj.optDouble("n89")) : null;

    StringBuilder sb = new StringBuilder();
    sb.append("⛽️ ").append(regionName).append(" 油价 (").append(date).append(")\n");
    sb.append("━━━━━━━━━━━━━━\n");
    sb.append("92号汽油  ").append(p92).append("\n");
    sb.append("95号汽油  ").append(p95).append("\n");
    sb.append("98号汽油  ").append(p98).append("\n");
    sb.append("0号柴油   ").append(p0).append("\n");
    if (p89 != null) sb.append("89号汽油  ").append(p89).append("\n");
    sb.append("━━━━━━━━━━━━━━");

    return sb.toString();
}