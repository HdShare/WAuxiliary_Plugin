import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Random;
import java.util.Arrays;

import me.hd.wauxv.plugin.api.callback.PluginCallBack;

// 全局变量
boolean isRunning = false;
int SERVER_PORT = 13333;

// 消息队列和队列处理线程
ConcurrentLinkedQueue messageQueue = new ConcurrentLinkedQueue();
AtomicBoolean queueProcessorRunning = new AtomicBoolean(false);
Thread queueProcessorThread = null;

// 启动队列处理线程
void startQueueProcessor() {
    if (queueProcessorRunning.compareAndSet(false, true)) {
        queueProcessorThread = new Thread(new Runnable() {
            public void run() {
                processMessageQueue();
            }
        });
        queueProcessorThread.setDaemon(true);
        queueProcessorThread.start();
    }
}

// 队列处理逻辑
void processMessageQueue() {
    log("消息队列处理器已启动");
    while (queueProcessorRunning.get()) {
        try {
            Object taskObj = messageQueue.poll();
            if (taskObj != null) {
                // 使用Map来存储任务信息
                java.util.Map task = (java.util.Map) taskObj;
                long currentTime = System.currentTimeMillis();
                long executeTime = (Long) task.get("executeTime");
                
                if (currentTime >= executeTime) {
                    // 执行发送
                    try {
                        String talkerId = (String) task.get("talkerId");
                        String message = (String) task.get("message");
                        sendText(talkerId, message);
                    } catch (Exception e) {
                        log("队列发送失败: " + e.getMessage());
                        // 重新加入队列
                        messageQueue.offer(task);
                    }
                } else {
                    // 还没到执行时间，重新放回队列
                    messageQueue.offer(task);
                    Thread.sleep(100); // 短暂休眠避免CPU占用过高
                }
            } else {
                Thread.sleep(100); // 队列为空时休眠
            }
        } catch (InterruptedException e) {
            log("队列处理器被中断: " + e.getMessage());
            break;
        } catch (Exception e) {
            log("队列处理器异常: " + e.getMessage());
        }
    }
    log("消息队列处理器已停止");
}

// 停止队列处理线程
void stopQueueProcessor() {
    queueProcessorRunning.set(false);
    if (queueProcessorThread != null) {
        queueProcessorThread.interrupt();
        queueProcessorThread = null;
    }
}

// 添加消息到队列
void addMessageToQueue(String talkerId, String message, long delayMs) {
    long executeTime = System.currentTimeMillis() + delayMs;
    java.util.Map task = new java.util.HashMap();
    task.put("talkerId", talkerId);
    task.put("message", message);
    task.put("executeTime", executeTime);
    messageQueue.offer(task);
}

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

    // 确保队列处理器正在运行
    if (!queueProcessorRunning.get()) {
        startQueueProcessor();
    }

    // 为每个接收者计算延迟时间并加入队列
    long baseDelay = 0;
    for (String talkerId : talkerIds) {
        if (talkerId == null || talkerId.isEmpty()) continue;

        // 基础延迟 + 随机延迟
        int randomDelay = delayMin + new Random().nextInt(delayMax - delayMin + 1);
        long totalDelay = baseDelay + randomDelay;
        
        addMessageToQueue(talkerId, msg, totalDelay);
        
        // 为下一个消息增加3-5秒的随机延迟
        baseDelay += 3000 + new Random().nextInt(2001); // 3000-5000ms
    }
}

void start(int port) {
    if (isRunning) {
        log("服务已在运行");
        return;
    }

    new Thread(new Runnable() {
        public void run() {
            try {
                java.net.ServerSocket serverSocket = new java.net.ServerSocket(port);
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
    start(SERVER_PORT);
    // 启动队列处理器
    startQueueProcessor();
}

static {
    startHttpServer();
}
