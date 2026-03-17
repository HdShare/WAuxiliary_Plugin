import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONObject;


// 设置请求参数
String url = "https://api.siliconflow.cn/v1/audio/speech";
// 设置你的apikey
String token = "sk-";
// 语音模型名称
String model = "FunAudioLLM/CosyVoice2-0.5B";
String timbre = "benjamin";

// OkHttp客户端（复用）
OkHttpClient client = new OkHttpClient();

// 内置音色
//        男生音色：
//        沉稳男声: alex
//        低沉男声: benjamin
//        磁性男声: charles
//        欢快男声: david
//        女生音色：
//        沉稳女声: anna
//        激情女声: bella
//        温柔女声: claire
//        欢快女声: diana

boolean onClickSendBtn(String text) {

    if (text.startsWith("语音：") || text.startsWith("语音:")) {

        String prompt = "很自然的语气";
        String content = text.substring(3);
        if ("".equals(content.trim())) {
            return false;
        }

        if (content.contains("/")) {
            String[] parts = content.split("/", 2);
            content = parts[0];
            prompt = parts[1];
        }

        // 构建JSON请求体
        JSONObject jsonBody = new JSONObject();
        jsonBody.put("model", model);
        jsonBody.put("voice", model + ":" + timbre);
        jsonBody.put("input", prompt + "<|endofprompt|>" + content);

        // 构建请求
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(
                        jsonBody.toString(),
                        MediaType.parse("application/json")
                ))
                .build();

        // 异步发送请求
        client.newCall(request).enqueue(new Callback() {

            public void onFailure(Call call, IOException e) {
                insertSystemMsg(getTargetTalker(), "请求失败：" + e.getMessage(), System.currentTimeMillis());
            }

            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    // 保存音频文件
                    File audioFile = saveAudioFile(response.body().byteStream());
                    if (audioFile != null) {
                        // 发送音频文件到聊天
                        String silkPath = pluginDir + "/silk_" + System.currentTimeMillis() + ".silk";
                        mp3ToSilk(audioFile.getAbsolutePath(), silkPath);
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                            sendVoice(getTargetTalker(), silkPath);
                            // 发送完成后删除临时文件
                            new File(silkPath).delete();
                            audioFile.delete();
                        });
                    } else {
                        insertSystemMsg(getTargetTalker(), "保存音频文件失败", System.currentTimeMillis());
                    }
                } else {
                    String errorBody = response.body() != null ? response.body().string() : "无错误信息";
                    insertSystemMsg(getTargetTalker(), "转换失败：" + response.code() + ":" + errorBody, System.currentTimeMillis());
                }
            }
        });

        insertSystemMsg(getTargetTalker(), "语音转换中……", System.currentTimeMillis());
        return true;
    }

    return false;
}

/**
 * 保存音频流到文件
 */
File saveAudioFile(InputStream inputStream) {
    FileOutputStream fos = null;
    try {
        File audioFile = new File(pluginDir + "/voice_" + System.currentTimeMillis() + ".mp3");
        fos = new FileOutputStream(audioFile);

        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            fos.write(buffer, 0, bytesRead);
        }
        fos.flush();

        return audioFile;
    } catch (IOException e) {
        //e.printStackTrace();
        log("出错");
        return null;
    } finally {
        try {
            if (fos != null) fos.close();
            if (inputStream != null) inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
