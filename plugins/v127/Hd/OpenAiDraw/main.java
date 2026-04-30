
var host = "https://api.openai.com"
var api = "/v1/images/generations"
var apiModel = "gpt-image-2"
var apiKey = "sk-密钥"

Map getParam(String prompt, String model) {
    var paramMap = new HashMap()
    paramMap.put("prompt", prompt)
    paramMap.put("model", model)
    paramMap.put("n", 1)
    paramMap.put("size", "1024x1536")
    return paramMap
}

Map getHeader(String apiKey) {
    var headerMap = new HashMap()
    headerMap.put("Content-Type", "application/json")
    headerMap.put("Authorization", "Bearer $apiKey")
    return headerMap
}

void saveImage(String base64Str, String outputPath) {
    var bytes = Base64.getDecoder().decode(base64Str)
    try (FileOutputStream fos = new FileOutputStream(outputPath)) {
        fos.write(bytes)
    } catch (IOException e) {
        e.printStackTrace();
    }
}

void sendOpenAiImg(String prompt, String model, String key) {
    post(host + api, getParam(prompt, model), getHeader(key), 2 * 60 * 1000, respContent -> {
        var jsonObj = new JSONObject(respContent)
        var data = jsonObj.optJSONArray("data")
        var imgJsonObj = data.optJSONObject(0)
        var imgBase64 = imgJsonObj.optString("b64_json")
        var path = "$cacheDir/gpt.jpg"
        saveImage(imgBase64, path)
        sendImage(getTargetTalker(), path)
    })
}

boolean onClickSendBtn(String text) {
    var cmd = "/绘图 "
    if (text.startsWith(cmd)) {
        var prompt = text.substring(cmd.length())
        sendOpenAiImg(prompt, apiModel, apiKey)
        return true
    }
    return false
}
