import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

import me.hd.wauxv.plugin.api.callback.PluginCallBack;

boolean isRunning = false;
int SERVER_PORT = 13333;

void handleClient(java.net.Socket socket) throws IOException {
    try {
        java.io.BufferedReader reader = new java.io.BufferedReader(
            new java.io.InputStreamReader(socket.getInputStream())
        );

        // 解析请求头
        String line;
        boolean isPost = false;
        int contentLength = 0;

        while ((line = reader.readLine()) != null && !line.trim().isEmpty()) {
            if (line.startsWith("POST")) {
                isPost = true;
            } else if (line.toLowerCase().startsWith("content-length")) {
                try {
                    contentLength = Integer.parseInt(line.split(":")[1].trim());
                } catch (Exception ignored) {}
            }
        }

        // 读取 POST Body
        StringBuilder bodyBuilder = new StringBuilder();
        char[] buffer = new char[contentLength];
        int read = reader.read(buffer, 0, contentLength);
        if (read > 0) {
            bodyBuilder.append(buffer, 0, read);
        }

        String body = bodyBuilder.toString();

        if (isPost && !body.isEmpty()) {
            JSONObject json = new JSONObject(body);
            String[] talkerIds = getTalkerIds(json);
            String msg = json.optString("msg");
            int delayMin = json.optInt("delay_min", 3000);
            int delayMax = json.optInt("delay_max", 5000);

            handleIncomingMessage(talkerIds, msg, delayMin, delayMax);
        }

        // 返回响应
        java.io.OutputStream out = socket.getOutputStream();
        String response = "HTTP/1.1 200 OK\r\nContent-Length: 8\r\n\r\nReceived";
        out.write(response.getBytes());
        out.flush();
        socket.close();
    } catch (Exception e) {
        log("处理 HTTP 请求失败: " + e.getMessage());
    }
}

String[] getTalkerIds(JSONObject json) {
    try {
        JSONArray ids = json.getJSONArray("ids");
        String[] talkerIds = new String[ids.length()];
        for (int i = 0; i < ids.length(); i++) {
            talkerIds[i] = ids.getString(i).trim();
        }
        return talkerIds;
    } catch (Exception e) {
        return new String[0];
    }
}

void handleIncomingMessage(String[] talkerIds, String msg, int delayMin, int delayMax) {
    if (talkerIds == null || talkerIds.length == 0 || msg == null || msg.isEmpty()) {
        log("参数无效");
        return;
    }

    log("收到 POST 请求: ids=" + Arrays.toString(talkerIds) + ", msg=" + msg + ", delay_min=" + delayMin + ", delay_max=" + delayMax);

    new Thread(new Runnable() {
        public void run() {
            for (String talkerId : talkerIds) {
                if (talkerId == null || talkerId.isEmpty()) continue;

                try {
                    sendText(talkerId, msg);
                    log("已发送消息给: " + talkerId);

                    // 随机延迟
                    int delay = delayMin + new Random().nextInt(delayMax - delayMin + 1);
                    log("等待 " + delay + " 毫秒");
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    log("发送中断: " + e.getMessage());
                } catch (Exception e) {
                    log("发送失败: " + e.getMessage());
                }
            }
        }
    }).start();
}

void start(int port) {
    if (isRunning) {
        log("服务已在运行");
        return;
    }

    new Thread(new Runnable() {
        public void run() {
            try {
                ServerSocket serverSocket = new java.net.ServerSocket(port);
                isRunning = true;
                log("简易 HTTP Server started at port " + port);

                while (true) {
                    java.net.Socket clientSocket = serverSocket.accept();
                    handleClient(clientSocket);
                }
            } catch (IOException e) {
                log("HTTP Server 启动失败: " + e.getMessage());
            }
        }
    }).start();
}

void startHttpServer() {
    log("启动简易 HTTP Server");
    start(SERVER_PORT);
}

static {
    startHttpServer();
}
