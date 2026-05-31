import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.json.JSONObject;
import org.json.JSONArray;

// 全局配置对象
Properties config = new Properties();

// 缓存每个聊天窗口（talker）最后几张图片的本地路径列表
Map lastImages = new HashMap();

/**
 * 插件加载时的初始化回调
 */
void onLoad() {
    log("[AI智能绘图] 插件正在加载...");
    loadConfig();
    log("[AI智能绘图] 插件加载完成！");
    log("[AI智能绘图] - 默认AI模型: " + config.getProperty("model", "gemini-3.1-flash-image-preview"));
    log("[AI智能绘图] - 默认分辨率级: " + config.getProperty("resolution_level", "1K"));
    log("[AI智能绘图] - 默认画面比例: " + config.getProperty("aspect_ratio", "自动"));
}

/**
 * 插件卸载时的回调
 */
void onUnload() {
    log("[AI智能绘图] 插件已卸载");
}

/**
 * 点击插件管理中齿轮按钮时触发此回调以打开设置界面
 */
void openSettings() {
    android.app.Activity currentActivity = getCurrentActivity();
    if (currentActivity != null) {
        showSettingsDialog(currentActivity);
    } else {
        toast("⚠️ 抱歉，当前无法获取微信前台窗口，请确保微信已启动并在前台活跃。");
    }
}

/**
 * 加载本地配置文件 config.prop
 */
void loadConfig() {
    File configFile = new File(pluginDir, "config.prop");
    if (configFile.exists()) {
        try {
            FileInputStream fis = new FileInputStream(configFile);
            config.load(fis);
            fis.close();
            log("[AI智能绘图] 成功加载 config.prop 配置文件！");
        } catch (Exception e) {
            log("[AI智能绘图] 加载 config.prop 配置文件失败: " + e.getMessage());
        }
    } else {
        log("[AI智能绘图] 未找到 config.prop 配置文件，请检查插件目录是否完整。");
    }
}

/**
 * 辅助方法：校验某个路径是否为有效的本地物理文件 (排除微信虚拟路径 THUMBNAIL_DIRPATH)
 */
boolean isValidLocalFile(String path) {
    if (path == null || path.trim().isEmpty()) {
        return false;
    }
    if (path.startsWith("THUMBNAIL_DIRPATH://") || path.startsWith("http://") || path.startsWith("https://")) {
        return false;
    }
    try {
        File file = new File(path);
        return file.exists() && file.isFile() && file.length() > 0;
    } catch (Throwable e) {
        return false;
    }
}

/**
 * 辅助方法：清除本地高速缓存目录中的所有临时图片文件，释放存储空间并清空内存队列
 */
void clearCacheDirectory() {
    try {
        File dir = new File(cacheDir);
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                int count = 0;
                for (int i = 0; i < files.length; i++) {
                    if (files[i].isFile()) {
                        if (files[i].delete()) {
                            count++;
                        }
                    }
                }
                log("[AI智能绘图] 已清理缓存目录，成功删除 " + count + " 个临时缓存文件。");
            }
        }
    } catch (Throwable e) {
        log("[AI智能绘图] 清理缓存目录异常: " + e.toString());
    }
}

/**
 * 辅助方法：将新图片路径加入到对应的 talker 队列，上限受 ref_image_count 约束，采取 FIFO 策略
 */
void addCachedImage(String talker, String path) {
    if (talker == null || path == null) return;
    
    int limit = 1;
    try {
        limit = Integer.parseInt(config.getProperty("ref_image_count", "1").trim());
    } catch (Exception e) {
        limit = 1;
    }
    if (limit < 1) limit = 1;
    if (limit > 10) limit = 10;
    
    java.util.List list = (java.util.List) lastImages.get(talker);
    if (list == null) {
        list = new java.util.ArrayList();
        lastImages.put(talker, list);
    }
    
    // 排重，把最新的移到队尾
    list.remove(path);
    list.add(path);
    
    // 强制截断队首，保持在设定上限以内
    while (list.size() > limit) {
        list.remove(0);
    }
    log("[AI智能绘图] 缓存参考图 [" + talker + "] 成功，当前队列长度: " + list.size() + "/" + limit);
}

/**
 * 辅助方法：获取某个 talker 目前所有依然有效存在的本地参考图路径列表
 */
java.util.List getCachedImages(String talker) {
    java.util.List result = new java.util.ArrayList();
    if (talker == null) return result;
    
    java.util.List list = (java.util.List) lastImages.get(talker);
    if (list != null) {
        for (int i = 0; i < list.size(); i++) {
            String path = (String) list.get(i);
            if (isValidLocalFile(path)) {
                result.add(path);
            }
        }
    }
    return result;
}

/**
 * 微信消息监听回调
 * @param msgInfoBean 微信消息数据对象
 */
void onHandleMsg(Object msgInfoBean) {
    if (msgInfoBean == null) {
        return;
    }
    
    // 1. 如果收到的是图片消息，引导异步延时下载并缓存 (三重策略)
    if (msgInfoBean.isImage()) {
        final String talker = msgInfoBean.getTalker();
        final Object finalMsgInfo = msgInfoBean;
        
        // 同步立即尝试读取 getImgPath (有些版本在回调时已就绪)
        try {
            String immPath = finalMsgInfo.getImgPath();
            log("[AI智能绘图] 同步 getImgPath() = " + immPath);
            if (isValidLocalFile(immPath)) {
                addCachedImage(talker, immPath);
                log("[AI智能绘图] 同步物理路径缓存成功: " + immPath);
            }
        } catch (Throwable e) {
            log("[AI智能绘图] 同步 getImgPath 异常: " + e.toString());
        }
        
        // 启动后台线程，延时 2 秒后执行三重策略
        new Thread(new Runnable() {
            public void run() {
                try {
                    Thread.sleep(2000);
                    String path = null;
                    
                    // 【策略 A】直接调用 getImgPath() (延时后微信可能已下载完成)
                    try {
                        String imgPath = finalMsgInfo.getImgPath();
                        log("[AI智能绘图] 延时策略A getImgPath() = " + imgPath);
                        if (isValidLocalFile(imgPath)) {
                            path = imgPath;
                            log("[AI智能绘图] 策略A成功(物理文件存在): " + path);
                        } else {
                            log("[AI智能绘图] 策略A: 路径非物理路径或物理文件不存在: " + imgPath);
                        }
                    } catch (Throwable e) {
                        log("[AI智能绘图] 策略A异常: " + e.toString());
                    }
                    
                    // 【策略 B】通过 ImageMsg 的 downloadImg 官方通道下载
                    if (path == null) {
                        try {
                            Object imageMsg = finalMsgInfo.getImageMsg();
                            log("[AI智能绘图] 延时策略B getImageMsg() = " + imageMsg);
                            if (imageMsg != null) {
                                String md5 = imageMsg.getMd5();
                                log("[AI智能绘图] 策略B getMd5() = " + md5);
                                if (md5 != null && !md5.trim().isEmpty()) {
                                    String cdnPath = cacheDir + "/" + md5 + ".jpg";
                                    log("[AI智能绘图] 策略B: 调用官方 downloadImg 保存至: " + cdnPath);
                                    downloadImg(imageMsg, cdnPath);
                                    
                                    // 循环等待文件下载并写入成功，最多等待 5 秒
                                    long startTime = System.currentTimeMillis();
                                    boolean downloadOk = false;
                                    while (System.currentTimeMillis() - startTime < 5000) {
                                        if (isValidLocalFile(cdnPath)) {
                                            downloadOk = true;
                                            break;
                                        }
                                        Thread.sleep(200);
                                    }
                                    
                                    if (downloadOk) {
                                        path = cdnPath;
                                        log("[AI智能绘图] 策略B成功(下载完成并存在): " + path);
                                    } else {
                                        log("[AI智能绘图] 策略B: 5秒等待超时，文件未生成或为空");
                                    }
                                }
                            }
                        } catch (Throwable e) {
                            log("[AI智能绘图] 策略B异常: " + e.toString());
                        }
                    }
                    
                    // 【策略 C】通过 ImageMsg 的 getBigImgUrl 网络下载
                    if (path == null) {
                        try {
                            Object imageMsg = finalMsgInfo.getImageMsg();
                            if (imageMsg != null) {
                                String bigUrl = imageMsg.getBigImgUrl();
                                log("[AI智能绘图] 策略C getBigImgUrl() = " + bigUrl);
                                if (bigUrl != null && bigUrl.startsWith("http")) {
                                    String dlPath = cacheDir + "/ai_ref_" + System.currentTimeMillis() + ".jpg";
                                    if (downloadFile(bigUrl, dlPath)) {
                                        if (isValidLocalFile(dlPath)) {
                                            path = dlPath;
                                            log("[AI智能绘图] 策略C成功: " + path);
                                        }
                                    }
                                }
                            }
                        } catch (Throwable e) {
                            log("[AI智能绘图] 策略C异常: " + e.toString());
                        }
                    }
                    
                    // 存入缓存
                    if (path != null) {
                        addCachedImage(talker, path);
                    } else {
                        log("[AI智能绘图] 警告：三重策略全部失败，未获取到图片路径！");
                    }
                } catch (Throwable t) {
                    log("[AI智能绘图] 异步处理异常: " + t.toString());
                }
            }
        }).start();
    }
    
    // 2. 如果收到的是文本消息，检测是否触发指令
    if (msgInfoBean.isText()) {
        String content = msgInfoBean.getContent().trim();
        String talker = msgInfoBean.getTalker();
        
        String trigger = config.getProperty("trigger_word", "#生图");
        String editTrigger = config.getProperty("edit_trigger_word", "#编辑图");
        
        // ==================== 场景 C：触发统一设置界面 ====================
        // 安全检测：仅限主人发送“ai绘图设置”时弹出弹窗，其他人输入忽略
        if (content.equals("ai绘图设置")) {
            if (msgInfoBean.isSend()) {
                android.app.Activity currentActivity = getCurrentActivity();
                if (currentActivity != null) {
                    showSettingsDialog(currentActivity);
                } else {
                    sendText(talker, "【AI设置】未获取到前台活跃窗口，请在微信中点击插件列表的设置齿轮进行配置。");
                }
            } else {
                log("[AI智能绘图] 拦截非主人用户尝试触发设置弹窗。");
            }
            return;
        }
        
        // ==================== 场景 D：触发清除缓存与历史日记 ====================
        if (content.equals("#清除缓存") || content.equals("#清除日志") || content.equals("#清除日记")) {
            if (msgInfoBean.isSend()) {
                lastImages.clear();
                clearCacheDirectory();
                sendText(talker, "【AI智能绘图】缓存与历史记录清理成功。已清空内存参考图队列并删除本地临时图片文件。");
            } else {
                log("[AI智能绘图] 拦截非主人用户尝试清理缓存。");
            }
            return;
        }
        
        // ==================== 场景 A：触发文生图 ====================
        if (content.startsWith(trigger)) {
            String model = config.getProperty("model", "gemini-3.1-flash-image-preview");
            String prompt = "";
            
            // 安全机制：只有当消息是主人自己发送的 (msgInfoBean.isSend() 为 true) 时，才允许动态临时切换模型 (例如 #生图-banana2)
            if (msgInfoBean.isSend() && content.startsWith(trigger + "-")) {
                int firstSpace = content.indexOf(" ");
                if (firstSpace != -1) {
                    model = content.substring(trigger.length() + 1, firstSpace).trim();
                    prompt = content.substring(firstSpace).trim();
                } else {
                    sendText(talker, "【AI绘图】格式错误。动态指定模型时，提示词前必须保留空格。\n正确格式：\n" + trigger + "-[模型名称] [提示词]");
                    return;
                }
            } else {
                if (content.startsWith(trigger + "-")) {
                    int firstSpace = content.indexOf(" ");
                    if (firstSpace != -1) {
                        prompt = content.substring(firstSpace).trim();
                        sendText(talker, "【AI提示】无权自定义模型，已自动使用默认模型 [" + model + "] 进行绘制。");
                    } else {
                        prompt = "";
                    }
                } else {
                    prompt = content.substring(trigger.length()).trim();
                }
            }
            
            if (prompt.isEmpty()) {
                sendText(talker, "【AI绘图】提示词不能为空。\n用法示例：\n" + trigger + " 一只在太空中漂浮的可爱猫咪");
                return;
            }
            
            // 计算画面最终分辨率 (统一设置架构)
            String ratio = config.getProperty("aspect_ratio", "1:1");
            String level = config.getProperty("resolution_level", "1K");
            String size = "";
            if (ratio.startsWith("⚙️")) {
                // 自定义模式
                size = config.getProperty("size", "1024x1024");
            } else {
                // 自动计算，文生图没有参考图，第三个参数传空
                size = calculateResolution(ratio, level, null);
            }
            
            sendText(talker, "【AI绘图】收到请求，正在使用模型 [" + model + "] 绘制中...\n比例/分辨率: " + ratio + " (" + level + " | " + size + ")\n提示词: \"" + prompt + "\"");
            
            final String finalModel = model;
            final String finalPrompt = prompt;
            final String finalSize = size;
            final String finalRatio = resolveAspectRatio(ratio, null);
            final String finalLevel = level;
            new Thread(new Runnable() {
                public void run() {
                    try {
                        loadConfig();
                        String baseUrl = config.getProperty("api_base_url", "https://yunwu.ai");
                        String apiKey = config.getProperty("api_key", "sk-xxxxxxxxxxxxxxxxxxxxxxxx");
                        
                        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.contains("sk-xxxx")) {
                            sendText(talker, "【AI绘图】失败：未检测到有效的中转 API Key！请发送“ai绘图设置”进行快速配置。");
                            return;
                        }
                        
                        // 统一接口调度系统 (内部智能识别 Gemini / Imagen / OpenAI 变图协议)
                        String imageUrl = callUnifiedApi(finalPrompt, baseUrl, apiKey, finalModel, finalSize, finalRatio, finalLevel, null);
                        if (imageUrl == null || imageUrl.trim().isEmpty()) {
                            sendText(talker, "【AI绘图】调用生图接口失败，请检查您的中转 API 配置或余额。");
                            return;
                        }
                        
                        sendAndDownloadImg(imageUrl, talker);
                    } catch (Exception e) {
                        sendText(talker, "【AI绘图】文生图程序异常: " + e.getMessage());
                    }
                }
            }).start();
        }
        
        // ==================== 场景 B：触发图片编辑 / 图生图 ====================
        else if (content.startsWith(editTrigger)) {
            String model = config.getProperty("model", "gemini-3.1-flash-image-preview");
            String prompt = "";
            
            // 安全机制：只有当消息是主人自己发送的 (msgInfoBean.isSend() 为 true) 时，才允许动态临时切换编辑模型
            if (msgInfoBean.isSend() && content.startsWith(editTrigger + "-")) {
                int firstSpace = content.indexOf(" ");
                if (firstSpace != -1) {
                    model = content.substring(editTrigger.length() + 1, firstSpace).trim();
                    prompt = content.substring(firstSpace).trim();
                } else {
                    sendText(talker, "【AI编辑】格式错误。动态指定模型时，指令前必须保留空格。\n正确格式：\n" + editTrigger + "-[模型名称] [指令]");
                    return;
                }
            } else {
                if (content.startsWith(editTrigger + "-")) {
                    int firstSpace = content.indexOf(" ");
                    if (firstSpace != -1) {
                        prompt = content.substring(firstSpace).trim();
                        sendText(talker, "【AI提示】无权自定义编辑模型，已使用默认编辑模型 [" + model + "]。");
                    } else {
                        prompt = "";
                    }
                } else {
                    prompt = content.substring(editTrigger.length()).trim();
                }
            }
            
            if (prompt.isEmpty()) {
                sendText(talker, "【AI编辑】描述词不能为空。\n用法示例：\n" + editTrigger + " 帮这只猫加上一副黑色墨镜");
                return;
            }
            
            java.util.List imagePaths = getCachedImages(talker);
            if (imagePaths.isEmpty()) {
                sendText(talker, "【AI编辑】未在当前窗口找到可用的参考图片。请先发送图片，然后再发送编辑指令。");
                return;
            }
            
            // 使用最新那张参考图提取画面纵横比
            String primaryImagePath = (String) imagePaths.get(imagePaths.size() - 1);
            
            // 计算编辑最终分辨率 (统一设置架构：生图与变图合并使用相同设置参数)
            String ratio = config.getProperty("aspect_ratio", "自动");
            String level = config.getProperty("resolution_level", "1K");
            String size = "";
            if (ratio.startsWith("⚙️")) {
                size = config.getProperty("size", "1024x1024");
            } else {
                size = calculateResolution(ratio, level, primaryImagePath);
            }
            
            sendText(talker, "【AI编辑】收到请求，正在使用模型 [" + model + "] 处理中...\n已装载 " + imagePaths.size() + " 张参考图 | 比例/分辨率: " + ratio + " (" + level + " | " + size + ")\n指令: \"" + prompt + "\"");
            
            final String finalModel = model;
            final String finalPrompt = prompt;
            final String finalSize = size;
            final String finalRatio = resolveAspectRatio(ratio, primaryImagePath);
            final String finalLevel = level;
            final java.util.List finalImagePaths = imagePaths;
            new Thread(new Runnable() {
                public void run() {
                    try {
                        loadConfig();
                        String baseUrl = config.getProperty("api_base_url", "https://yunwu.ai");
                        String apiKey = config.getProperty("api_key", "sk-xxxxxxxxxxxxxxxxxxxxxxxx");
                        
                        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.contains("sk-xxxx")) {
                            sendText(talker, "【AI编辑】失败：未填写有效的中转 API Key！请发送“ai绘图设置”进行快速配置。");
                            return;
                        }
                        
                        // 统一接口调度系统 (内部智能识别 Gemini / Imagen / OpenAI 变图协议)
                        String imageUrl = callUnifiedApi(finalPrompt, baseUrl, apiKey, finalModel, finalSize, finalRatio, finalLevel, finalImagePaths);
                        if (imageUrl == null || imageUrl.trim().isEmpty()) {
                            sendText(talker, "【AI编辑】图片处理接口失败。请检查中转 API 是否支持模型 " + finalModel + " 并且格式正确。");
                            return;
                        }
                        
                        sendAndDownloadImg(imageUrl, talker);
                    } catch (Exception e) {
                        sendText(talker, "【AI编辑】程序异常: " + e.getMessage());
                    }
                }
            }).start();
        }
    }
}

/**
 * 欧几里得求最大公约数算法
 */
int getGcd(int a, int b) {
    while (b > 0) {
        int temp = b;
        b = a % b;
        a = temp;
    }
    return a;
}

/**
 * 求最小公倍数算法
 */
int getLcm(int a, int b) {
    if (a == 0 || b == 0) return 0;
    return a * (b / getGcd(a, b));
}

/**
 * 核心数学算法 A：计算 ComfyUI Banana2 节点等比例尺寸 (完美移植自 nodes_myproxy.py)
 * 寻找长边大致与 baseEdge 贴合，并且宽和高均被 snap (64) 整除，比例完全吻合的尺寸对
 */
String calculateSize(int baseEdge, String aspectStr, int snap, Integer maxEdge) {
    try {
        String[] parts = aspectStr.split(":");
        int wRatio = Integer.parseInt(parts[0]);
        int hRatio = Integer.parseInt(parts[1]);
        if (wRatio <= 0 || hRatio <= 0) {
            return baseEdge + "x" + baseEdge;
        }
        
        if (baseEdge % snap != 0) {
            baseEdge = Math.max(snap, (baseEdge / snap) * snap);
        }
        
        java.util.List cand = new java.util.ArrayList();
        
        // 尝试以 baseEdge 作为宽度，求符合 snap 对齐的高
        double rawH = (double) baseEdge * hRatio / wRatio;
        int hInt = (int) Math.round(rawH);
        if (Math.abs(rawH - hInt) < 1e-6 && hInt >= snap && (hInt % snap == 0) && (maxEdge == null || hInt <= maxEdge.intValue())) {
            cand.add(new int[]{baseEdge, hInt});
        }
        
        // 尝试以 baseEdge 作为高度，求符合 snap 对齐的宽
        double rawW = (double) baseEdge * wRatio / hRatio;
        int wInt = (int) Math.round(rawW);
        if (Math.abs(rawW - wInt) < 1e-6 && wInt >= snap && (wInt % snap == 0) && (maxEdge == null || wInt <= maxEdge.intValue())) {
            cand.add(new int[]{wInt, baseEdge});
        }
        
        // 如果存在完美契合整除的长宽组合，取面积最大的
        if (!cand.isEmpty()) {
            int maxArea = -1;
            int[] bestPair = null;
            for (int i = 0; i < cand.size(); i++) {
                int[] p = (int[]) cand.get(i);
                int area = p[0] * p[1];
                if (area > maxArea) {
                    maxArea = area;
                    bestPair = p;
                }
            }
            if (bestPair != null) {
                return bestPair[0] + "x" + bestPair[1];
            }
        }
        
        // 降级使用 LCM 算法寻求最逼近的分辨率对 (完全一致移植自 python 节点)
        int kMin = getLcm(snap / getGcd(snap, wRatio), snap / getGcd(snap, hRatio));
        boolean landscape = wRatio >= hRatio;
        int denom = landscape ? wRatio : hRatio;
        double idealK = (double) baseEdge / denom;
        int m0 = Math.max(1, (int) Math.round(idealK / kMin));
        
        int bestScore = Integer.MAX_VALUE;
        int bestArea = -1;
        int bestW = baseEdge;
        int bestH = baseEdge;
        boolean found = false;
        
        int[] mList = new int[]{Math.max(1, m0 - 1), m0, m0 + 1, m0 + 2};
        for (int i = 0; i < mList.length; i++) {
            int m = mList[i];
            int k = m * kMin;
            int w = k * wRatio;
            int h = k * hRatio;
            if (maxEdge != null && Math.max(w, h) > maxEdge.intValue()) {
                continue;
            }
            int primary = landscape ? w : h;
            int score = Math.abs(primary - baseEdge);
            int area = w * h;
            
            if (!found || score < bestScore || (score == bestScore && area > bestArea)) {
                bestScore = score;
                bestArea = area;
                bestW = w;
                bestH = h;
                found = true;
            }
        }
        
        return bestW + "x" + bestH;
    } catch (Exception e) {
        return baseEdge + "x" + baseEdge;
    }
}

/**
 * 核心数学算法 B：计算 4K 巨幅尺寸 (完美移植自 nodes_myproxy.py)
 * 锁死长边为 4800 像素，另一边完全依据比例等比缩放并以 64 对齐
 */
String calculateSize4k(String aspectStr, int longEdge, int snap) {
    try {
        String[] parts = aspectStr.split(":");
        int wRatio = Integer.parseInt(parts[0]);
        int hRatio = Integer.parseInt(parts[1]);
        if (wRatio <= 0 || hRatio <= 0) {
            return longEdge + "x" + longEdge;
        }
        
        int w, h;
        if (wRatio < hRatio) {
            h = longEdge;
            w = (int) (longEdge * wRatio / hRatio);
        } else {
            w = longEdge;
            h = (int) (longEdge * hRatio / wRatio);
        }
        w = Math.max(snap, (w / snap) * snap);
        h = Math.max(snap, (h / snap) * snap);
        return w + "x" + h;
    } catch (Exception e) {
        return longEdge + "x" + longEdge;
    }
}

/**
 * 比例智能推导器 (完美移植自 nodes_myproxy.py)
 * 找到与原图物理比例绝对误差最小的主流支持比例
 */
String pickClosestRatio(int w, int h, String[] candidates) {
    if (w <= 0 || h <= 0) {
        return "1:1";
    }
    double target = (double) w / (double) h;
    String best = "1:1";
    double bestKey = Double.MAX_VALUE;
    
    for (int i = 0; i < candidates.length; i++) {
        String r = candidates[i];
        if (r.contains("自动") || r.contains("手动")) {
            continue;
        }
        try {
            String[] parts = r.split(":");
            double rw = Double.parseDouble(parts[0]);
            double rh = Double.parseDouble(parts[1]);
            double rr = rw / rh;
            double key = Math.abs(rr - target);
            if (key < bestKey) {
                best = r;
                bestKey = key;
            }
        } catch (Exception e) {
            // 忽略格式解析问题
        }
    }
    return best;
}

/**
 * 纵横比例智能解析器：将自动比例或自定义字符串解析为 Gemini/Imagen 支持的标准纵横比格式，防止 API 报错 400
 */
String resolveAspectRatio(String ratioStr, String imagePath) {
    String[] supportedRatios = new String[]{"1:1", "1:4", "1:8", "2:3", "3:2", "3:4", "4:1", "4:3", "4:5", "5:4", "8:1", "9:16", "16:9", "21:9"};
    
    // 1. 如果是自动比例，且包含“自动”，尝试从物理图片中动态探测比例
    if (ratioStr == null || ratioStr.contains("自动")) {
        if (imagePath != null && new File(imagePath).exists()) {
            try {
                android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                android.graphics.BitmapFactory.decodeFile(imagePath, options);
                if (options.outWidth > 0 && options.outHeight > 0) {
                    String picked = pickClosestRatio(options.outWidth, options.outHeight, supportedRatios);
                    log("[AI智能绘图] 自动比例推导结果: auto -> " + picked + " (原图=" + options.outWidth + "x" + options.outHeight + ")");
                    return picked;
                }
            } catch (Exception e) {
                log("[AI智能绘图] 从物理图片自动提取比例失败: " + e.getMessage());
            }
        }
        return "1:1"; // 没有参考图时，默认降级为 1:1
    }
    
    // 2. 如果是手动自定义像素尺寸，默认返回 1:1
    if (ratioStr.startsWith("⚙️")) {
        return "1:1";
    }
    
    // 3. 校验并提取选择器中的标准比例子串 (支持 "16:9" 或带有中文描述的 "16:9 宽屏" 等)
    for (int i = 0; i < supportedRatios.length; i++) {
        if (ratioStr.equals(supportedRatios[i])) {
            return ratioStr;
        }
    }
    for (int i = 0; i < supportedRatios.length; i++) {
        if (ratioStr.contains(supportedRatios[i])) {
            return supportedRatios[i];
        }
    }
    
    return "1:1";
}

/**
 * 分辨率统合控制中心 (完美对齐 ComfyUI 节点逻辑)
 */
String calculateResolution(String ratioStr, String resLevel, String imagePath) {
    String safeRatio = resolveAspectRatio(ratioStr, imagePath);
    
    // 2. 依据等级映射到具体宽高对
    if (resLevel.equals("1K")) {
        return calculateSize(1024, safeRatio, 64, null);
    } else if (resLevel.equals("2K")) {
        return calculateSize(1792, safeRatio, 64, null);
    } else if (resLevel.equals("4K")) {
        // 4K 模式锁定长边为 4800 像素
        return calculateSize4k(safeRatio, 4800, 64);
    }
    
    return "1024x1024";
}

/**
 * 弹出纯原生 Android 可配置的设置对话框页面
 */
void showSettingsDialog(final android.app.Activity activity) {
    if (activity == null) return;
    
    activity.runOnUiThread(new Runnable() {
        public void run() {
            try {
                // 1. 重新载入最新配置文件数据
                loadConfig();
                
                // 2. 构造 Dialog Builder
                android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(activity);
                
                // 创建标题 TextView
                android.widget.TextView titleView = new android.widget.TextView(activity);
                titleView.setText("AI 智能绘图设置");
                titleView.setTextSize(18);
                titleView.setTextColor(android.graphics.Color.parseColor("#1B1F23"));
                titleView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                titleView.setGravity(android.view.Gravity.CENTER);
                titleView.setPadding(0, 45, 0, 20);
                builder.setCustomTitle(titleView);
                
                // 滚动包裹层，支持超长字段滑动
                android.widget.ScrollView scrollView = new android.widget.ScrollView(activity);
                android.widget.LinearLayout layout = new android.widget.LinearLayout(activity);
                layout.setOrientation(android.widget.LinearLayout.VERTICAL);
                layout.setPadding(50, 10, 50, 20);
                
                // 渲染配置字段列表 (文生图与编辑图合并使用一套极简控制面板)
                final android.widget.EditText etBaseUrl = addField(activity, layout, "🌐 中转 API 地址 (api_base_url):", config.getProperty("api_base_url", "https://yunwu.ai"));
                final android.widget.EditText etApiKey = addField(activity, layout, "🔑 中转 API Key (api_key):", config.getProperty("api_key", "sk-xxxxxxxxxxxxxxxxxxxxxxxx"));
                final android.widget.EditText etModel = addField(activity, layout, "🎨 AI 生成/编辑模型 (model):", config.getProperty("model", "gemini-3.1-flash-image-preview"));
                
                // ==================== 统一的分辨率等级与画面比例选择 ====================
                final android.widget.Spinner spResLevel = addResolutionSelector(activity, layout, "🚀 分辨率等级 (对齐Banana2 2K/4K):", config.getProperty("resolution_level", "1K"));
                final android.widget.EditText etSize = addCustomSizeInput(activity, layout, config.getProperty("size", "1024x1024"));
                final android.widget.Spinner spRatio = addAspectSelector(activity, layout, "📐 默认画面比例 (支持自动比例识别):", config.getProperty("aspect_ratio", "自动"), etSize);
                
                // ==================== 多参考图融合控制 ====================
                final android.widget.Spinner spRefImageCount = addRefImageCountSelector(activity, layout, "🌟 变图参考图缓存上限数 (进行多图融合参考):", config.getProperty("ref_image_count", "1"));
                
                // ==================== 指令触发词设置 ====================
                final android.widget.EditText etTrigger = addField(activity, layout, "💬 文生图触发词 (trigger_word):", config.getProperty("trigger_word", "#生图"));
                final android.widget.EditText etEditTrigger = addField(activity, layout, "💡 变图/编辑触发词 (edit_trigger_word):", config.getProperty("edit_trigger_word", "#编辑图"));
                
                scrollView.addView(layout);
                builder.setView(scrollView);
                
                // 设置保存按钮
                builder.setPositiveButton("💾 保存设置", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        try {
                            config.setProperty("api_base_url", etBaseUrl.getText().toString().trim());
                            config.setProperty("api_key", etApiKey.getText().toString().trim());
                            config.setProperty("model", etModel.getText().toString().trim());
                            config.setProperty("trigger_word", etTrigger.getText().toString().trim());
                            config.setProperty("edit_trigger_word", etEditTrigger.getText().toString().trim());
                            
                            // 读取分辨率级别
                            int resPos = spResLevel.getSelectedItemPosition();
                            String resVal = "1K";
                            if (resPos == 1) resVal = "2K";
                            else if (resPos == 2) resVal = "4K";
                            config.setProperty("resolution_level", resVal);
                            
                            // 读取画面比例
                            config.setProperty("aspect_ratio", spRatio.getSelectedItem().toString());
                            
                            // 读取手动自定义的图片尺寸值
                            config.setProperty("size", etSize.getText().toString().trim());
                            
                            // 读取多参考图数量上限数
                            int countPos = spRefImageCount.getSelectedItemPosition();
                            String countVal = "1";
                            if (countPos == 1) countVal = "2";
                            else if (countPos == 2) countVal = "3";
                            else if (countPos == 3) countVal = "4";
                            else if (countPos == 4) countVal = "5";
                            else if (countPos == 5) countVal = "10";
                            config.setProperty("ref_image_count", countVal);
                            
                            // 保存到本地 config.prop
                            File configFile = new File(pluginDir, "config.prop");
                            FileOutputStream fos = new FileOutputStream(configFile);
                            config.store(fos, "AI Image Generator Plugin Config (Updated via UI)");
                            fos.close();
                            
                            toast("AI 智能绘图设置保存成功，已即时生效。");
                            log("[AI智能绘图] 用户成功通过微信内置 UI 保存了新设置。");
                        } catch (Exception ex) {
                            toast("❌ 保存配置失败: " + ex.getMessage());
                            log("[AI智能绘图] 保存设置发生异常: " + ex.getMessage());
                        }
                    }
                });
                
                // 设置关闭按钮
                builder.setNegativeButton("❌ 关闭", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
                
                // 设置清理缓存/日记按钮 (Neutral Button)
                builder.setNeutralButton("🧹 清除缓存", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        lastImages.clear();
                        clearCacheDirectory();
                        toast("临时缓存与历史记录清理成功。");
                    }
                });
                
                android.app.AlertDialog dialog = builder.create();
                dialog.show();
                
                dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setTextColor(android.graphics.Color.parseColor("#27AE60"));
                dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).setTextColor(android.graphics.Color.parseColor("#E74C3C"));
                dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setTextColor(android.graphics.Color.parseColor("#E67E22"));
            } catch (Exception e) {
                log("[AI智能绘图] 创建设置弹窗发生异常: " + e.getMessage());
            }
        }
    });
}

/**
 * 辅助方法：动态创建文本输入框
 */
android.widget.EditText addField(android.app.Activity activity, android.widget.LinearLayout parent, String labelText, String defaultValue) {
    android.widget.TextView tv = new android.widget.TextView(activity);
    tv.setText(labelText);
    tv.setTextSize(13);
    tv.setTextColor(android.graphics.Color.parseColor("#4B5563"));
    tv.setPadding(0, 20, 0, 8);
    parent.addView(tv);
    
    android.widget.EditText et = new android.widget.EditText(activity);
    et.setText(defaultValue);
    et.setTextSize(14);
    et.setSingleLine(true);
    et.setPadding(20, 15, 20, 15);
    et.setBackgroundColor(android.graphics.Color.parseColor("#F3F4F6"));
    et.setTextColor(android.graphics.Color.parseColor("#111827"));
    parent.addView(et);
    
    return et;
}

/**
 * 高级定制辅助方法：创建分辨率等级下拉选择器
 */
android.widget.Spinner addResolutionSelector(final android.app.Activity activity, android.widget.LinearLayout parent, String labelText, String currentRes) {
    android.widget.TextView tv = new android.widget.TextView(activity);
    tv.setText(labelText);
    tv.setTextSize(13);
    tv.setTextColor(android.graphics.Color.parseColor("#4B5563"));
    tv.setPadding(0, 20, 0, 8);
    parent.addView(tv);
    
    final String[] levels = new String[]{
        "🚀 1K 标准分辨率 (像素密度约 1024x1024)",
        "🚀 2K 高清分辨率 (像素密度约 2048x2048)",
        "🚀 4K 超清分辨率 (像素密度约 4096x4096)"
    };
    
    android.widget.ArrayAdapter adapter = new android.widget.ArrayAdapter(
        activity, 
        android.R.layout.simple_spinner_item, 
        levels
    );
    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    
    android.widget.Spinner spinner = new android.widget.Spinner(activity);
    spinner.setAdapter(adapter);
    spinner.setPadding(10, 10, 10, 10);
    
    android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
    gd.setColor(android.graphics.Color.parseColor("#F3F4F6"));
    gd.setCornerRadius(12);
    spinner.setBackground(gd);
    parent.addView(spinner);
    
    int selectedIndex = 0;
    if (currentRes.equals("2K")) selectedIndex = 1;
    else if (currentRes.equals("4K")) selectedIndex = 2;
    spinner.setSelection(selectedIndex);
    
    return spinner;
}

android.widget.Spinner addRefImageCountSelector(final android.app.Activity activity, android.widget.LinearLayout parent, String labelText, String currentCount) {
    android.widget.TextView tv = new android.widget.TextView(activity);
    tv.setText(labelText);
    tv.setTextSize(13);
    tv.setTextColor(android.graphics.Color.parseColor("#4B5563"));
    tv.setPadding(0, 20, 0, 8);
    parent.addView(tv);
    
    final String[] options = new String[]{
        "1 张 (默认：单图风格编辑/变图)",
        "2 张 (双图融合参考)",
        "3 张 (三图多维度融合)",
        "4 张 (四图对比参考)",
        "5 张 (五图故事上下文)",
        "10 张 (上限极致上下文)"
    };
    
    android.widget.ArrayAdapter adapter = new android.widget.ArrayAdapter(
        activity, 
        android.R.layout.simple_spinner_item, 
        options
    );
    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    
    android.widget.Spinner spinner = new android.widget.Spinner(activity);
    spinner.setAdapter(adapter);
    spinner.setPadding(10, 10, 10, 10);
    
    android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
    gd.setColor(android.graphics.Color.parseColor("#F3F4F6"));
    gd.setCornerRadius(12);
    spinner.setBackground(gd);
    parent.addView(spinner);
    
    int selectedIndex = 0;
    try {
        int count = Integer.parseInt(currentCount.trim());
        if (count == 2) selectedIndex = 1;
        else if (count == 3) selectedIndex = 2;
        else if (count == 4) selectedIndex = 3;
        else if (count == 5) selectedIndex = 4;
        else if (count == 10) selectedIndex = 5;
    } catch (Exception e) {}
    spinner.setSelection(selectedIndex);
    
    return spinner;
}

/**
 * 辅助生成专用于自定义尺寸的手动输入框
 */
android.widget.EditText addCustomSizeInput(android.app.Activity activity, android.widget.LinearLayout parent, String currentSize) {
    android.widget.EditText etSize = new android.widget.EditText(activity);
    etSize.setText(currentSize);
    etSize.setTextSize(14);
    etSize.setSingleLine(true);
    etSize.setPadding(20, 15, 20, 15);
    
    android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
        android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 
        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
    );
    lp.setMargins(0, 12, 0, 0);
    etSize.setLayoutParams(lp);
    parent.addView(etSize);
    
    return etSize;
}

/**
 * 高级定制辅助方法：创建支持全部比例与自动比例的下拉选择器 (Spinner)，并联动自定义输入框
 */
android.widget.Spinner addAspectSelector(final android.app.Activity activity, android.widget.LinearLayout parent, String labelText, String currentRatio, final android.widget.EditText etCustomSize) {
    android.widget.TextView tv = new android.widget.TextView(activity);
    tv.setText(labelText);
    tv.setTextSize(13);
    tv.setTextColor(android.graphics.Color.parseColor("#4B5563"));
    tv.setPadding(0, 20, 0, 8);
    parent.addView(tv);
    
    // 包含“自动识别”与全部主流纵横比例 (完美对齐 nodes_myproxy.py)
    final String[] ratios = new String[]{
        "自动 (依据原图比例智能裁切)", "1:1", "9:16", "16:9", "21:9",
        "2:3", "3:2", "3:4", "4:3", "4:5", "5:4",
        "1:4", "4:1", "1:8", "8:1", "⚙️ 手动输入自定义分辨率/比例"
    };
    
    android.widget.ArrayAdapter adapter = new android.widget.ArrayAdapter(
        activity, 
        android.R.layout.simple_spinner_item, 
        ratios
    );
    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    
    final android.widget.Spinner spinner = new android.widget.Spinner(activity);
    spinner.setAdapter(adapter);
    spinner.setPadding(10, 10, 10, 10);
    
    android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
    gd.setColor(android.graphics.Color.parseColor("#F3F4F6"));
    gd.setCornerRadius(12);
    spinner.setBackground(gd);
    parent.addView(spinner);
    
    int selectedIndex = 0; // 默认自动
    for (int i = 0; i < ratios.length; i++) {
        if (ratios[i].equals(currentRatio)) {
            selectedIndex = i;
            break;
        }
    }
    spinner.setSelection(selectedIndex);
    
    spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
        public void onItemSelected(android.widget.AdapterView parentView, android.view.View view, int position, long id) {
            if (position == 15) {
                etCustomSize.setEnabled(true);
                etCustomSize.setBackgroundColor(android.graphics.Color.parseColor("#F3F4F6"));
                etCustomSize.setTextColor(android.graphics.Color.parseColor("#111827"));
            } else {
                etCustomSize.setEnabled(false);
                etCustomSize.setBackgroundColor(android.graphics.Color.parseColor("#E5E7EB"));
                etCustomSize.setTextColor(android.graphics.Color.parseColor("#9CA3AF"));
                
                if (position == 0) {
                    etCustomSize.setText("自动比例探测中...");
                } else {
                    etCustomSize.setText(ratios[position]);
                }
            }
        }
        public void onNothingSelected(android.widget.AdapterView parentView) {}
    });
    
    return spinner;
}

/**
 * 统一接口调度器：支持 Gemini Multimodal / Imagen Predict / Standard OpenAI payload formatting.
 * 彻底消除中转服务器由于接口定义不一致返回 500 unsupported model 的痛点！
 */
String callUnifiedApi(String prompt, String apiBaseUrl, String apiKey, String model, String size, String ratio, String resLevel, java.util.List imagePaths) {
    boolean isGemini = model.toLowerCase().contains("gemini");
    boolean isImagen = model.toLowerCase().contains("imagen");
    
    // -------------------------------------------------------------
    // A. 针对 Gemini / Imagen 多模态模型接口处理 (对齐 nodes_myproxy.py)
    // -------------------------------------------------------------
    if (isGemini || isImagen) {
        try {
            // 规范化 Base URL，获取根级中转域名
            String base = apiBaseUrl.trim();
            if (base.endsWith("/v1/images/generations")) {
                base = base.substring(0, base.length() - "/v1/images/generations".length());
            } else if (base.endsWith("/v1/images/edits")) {
                base = base.substring(0, base.length() - "/v1/images/edits".length());
            }
            if (base.endsWith("/")) {
                base = base.substring(0, base.length() - 1);
            }
            
            // 路由动态切换到 Google Beta 级模型通道
            String fullUrl = "";
            if (isGemini) {
                fullUrl = base + "/v1beta/models/" + model + ":generateContent";
            } else {
                fullUrl = base + "/v1beta/models/" + model + ":predict";
            }
            
            URL url = new URL(fullUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setDoOutput(true);
            conn.setConnectTimeout(45000);
            conn.setReadTimeout(120000);
            
            // 读取本地图片进行 base64 转换 (多图压缩融合支持)
            java.util.List b64List = new java.util.ArrayList();
            if (imagePaths != null) {
                for (int i = 0; i < imagePaths.size(); i++) {
                    String p = (String) imagePaths.get(i);
                    if (isValidLocalFile(p)) {
                        String b64 = encodeFileToBase64(p);
                        if (b64 != null && !b64.trim().isEmpty()) {
                            b64List.add(b64);
                        }
                    }
                }
            }
            
            log("[AI智能绘图] 发起 " + (isGemini ? "Gemini" : "Imagen") + " 统一 API 接口调用, 模型: " + model + ", 分辨率级: " + resLevel + ", 比例: " + ratio + ", 已融合参考图: " + b64List.size() + " 张");
            
            JSONObject payload = new JSONObject();
            if (isGemini) {
                // 构建 Google v1beta generateContent JSON 负荷
                JSONArray parts = new JSONArray();
                JSONObject textPart = new JSONObject();
                textPart.put("text", prompt);
                parts.put(textPart);
                
                // 将多张参考图的 Base64 结构逐一注入零件列表 (与 nodes_myproxy.py 一致)
                for (int i = 0; i < b64List.size(); i++) {
                    String b64 = (String) b64List.get(i);
                    JSONObject imgPart = new JSONObject();
                    JSONObject inlineData = new JSONObject();
                    inlineData.put("mime_type", "image/jpeg");
                    inlineData.put("data", b64);
                    imgPart.put("inline_data", inlineData);
                    parts.put(imgPart);
                }
                
                if (!b64List.isEmpty()) {
                    log("[AI智能绘图] 已往 JSON Payload 注入 " + b64List.size() + " 张参考图 (isGemini)");
                } else {
                    log("[AI智能绘图] 未注入参考图数据，将作为常规文生图运行");
                }
                
                JSONArray contents = new JSONArray();
                JSONObject contentObj = new JSONObject();
                contentObj.put("role", "user");
                contentObj.put("parts", parts);
                contents.put(contentObj);
                payload.put("contents", contents);
                
                JSONObject genConfig = new JSONObject();
                JSONArray modalities = new JSONArray();
                modalities.put("TEXT");
                modalities.put("IMAGE");
                genConfig.put("responseModalities", modalities);
                
                JSONObject imgConfig = new JSONObject();
                imgConfig.put("aspectRatio", ratio);
                imgConfig.put("imageSize", resLevel); // 1K, 2K, 4K 档位
                genConfig.put("imageConfig", imgConfig);
                
                payload.put("generationConfig", genConfig);
            } else {
                // 构建 Google v1beta predict (Imagen) 负荷
                JSONArray instances = new JSONArray();
                JSONObject instance = new JSONObject();
                instance.put("prompt", prompt);
                
                if (!b64List.isEmpty()) {
                    JSONArray inputImages = new JSONArray();
                    for (int i = 0; i < b64List.size(); i++) {
                        String b64 = (String) b64List.get(i);
                        JSONObject imgObj = new JSONObject();
                        imgObj.put("mime_type", "image/jpeg");
                        imgObj.put("data", b64);
                        inputImages.put(imgObj);
                    }
                    instance.put("input_images", inputImages);
                    log("[AI智能绘图] 已往 JSON Payload 注入 " + b64List.size() + " 张参考图 (isImagen)");
                } else {
                    log("[AI智能绘图] 未注入参考图数据，将作为常规文生图运行");
                }
                instances.put(instance);
                payload.put("instances", instances);
                
                JSONObject params = new JSONObject();
                params.put("sampleCount", 1);
                params.put("aspectRatio", ratio);
                params.put("imageSize", resLevel);
                payload.put("parameters", params);
            }
            
            String jsonStr = payload.toString();
            OutputStream os = conn.getOutputStream();
            os.write(jsonStr.getBytes("UTF-8"));
            os.flush();
            os.close();
            
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
                br.close();
                
                JSONObject respObj = new JSONObject(response.toString());
                return findImageInJson(respObj);
            } else {
                BufferedReader br = new BufferedReader(new java.io.InputStreamReader(conn.getErrorStream(), "UTF-8"));
                StringBuilder err = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    err.append(line);
                }
                br.close();
                log("[AI智能绘图] Gemini/Imagen 报错 (" + responseCode + "): " + err.toString());
            }
        } catch (Exception e) {
            log("[AI智能绘图] Gemini/Imagen 请求异常: " + e.getMessage());
        }
        return null;
    }
    
    // -------------------------------------------------------------
    // B. 针对标准 OpenAI 兼容生图接口 (如 DALL-E 2 / 3) 调度
    // -------------------------------------------------------------
    if (imagePaths != null && !imagePaths.isEmpty()) {
        // 调用 Multipart/form-data 标准图片修改，使用最新的那张作为主体
        String primary = (String) imagePaths.get(imagePaths.size() - 1);
        return callImageEditApi(primary, prompt, apiBaseUrl, apiKey, model, size);
    } else {
        // 调用标准文生图 JSON
        return callImageGenApi(prompt, apiBaseUrl, apiKey, model, size);
    }
}

/**
 * 辅助方法：将本地图片文件编码为 Base64 字符串 (用于 Google 多模态 API)
 */
/**
 * 辅助方法：将本地图片文件解码、智能等比下采样及高质量 JPEG 压缩，最后编码为 Base64 字符串
 * 完美移植自 nodes_myproxy.py，杜绝 OutOfMemoryError (OOM) 并极大提升网络发送速度
 */
String encodeFileToBase64(String filePath) {
    try {
        File file = new File(filePath);
        if (!file.exists()) {
            log("[AI智能绘图] 编码参考图失败：文件不存在 " + filePath);
            return "";
        }
        log("[AI智能绘图] 开始读取并压缩参考图: " + filePath + ", 原始文件大小: " + file.length() + " 字节");
        
        // 1. 获取参考图的宽高
        android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeFile(filePath, options);
        int width = options.outWidth;
        int height = options.outHeight;
        if (width <= 0 || height <= 0) {
            log("[AI智能绘图] 警告：无法获取参考图尺寸");
            return "";
        }
        
        // 2. 计算最合适内存的采样率 (inSampleSize)
        int maxDim = 2048; // 与 python node 最大长边 2048 像素一致
        int inSampleSize = 1;
        if (width > maxDim || height > maxDim) {
            int halfWidth = width / 2;
            int halfHeight = height / 2;
            while ((halfWidth / inSampleSize) >= maxDim && (halfHeight / inSampleSize) >= maxDim) {
                inSampleSize *= 2;
            }
        }
        
        // 3. 按下采样率解码 Bitmap (大图仅需极微小的内存)
        options.inJustDecodeBounds = false;
        options.inSampleSize = inSampleSize;
        android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(filePath, options);
        if (bitmap == null) {
            log("[AI智能绘图] 错误：解码参考图 Bitmap 失败");
            return "";
        }
        
        // 4. 精确缩放以保证长边不大于 2048
        int currentW = bitmap.getWidth();
        int currentH = bitmap.getHeight();
        if (currentW > maxDim || currentH > maxDim) {
            float scale = (float) maxDim / Math.max(currentW, currentH);
            int newW = Math.round(currentW * scale);
            int newH = Math.round(currentH * scale);
            android.graphics.Bitmap scaledBitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, newW, newH, true);
            if (scaledBitmap != bitmap) {
                bitmap.recycle();
                bitmap = scaledBitmap;
            }
        }
        
        // 5. 压缩为高质量 JPEG (80% 质量，完美平衡画质与传输大小)
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, baos);
        byte[] bytes = baos.toByteArray();
        baos.close();
        bitmap.recycle();
        
        // 6. 转换成 Base64
        String b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP);
        log("[AI智能绘图] 参考图编码成功！压缩后大小: " + bytes.length + " 字节, Base64 文本长度: " + b64.length());
        return b64;
    } catch (Throwable e) {
        log("[AI智能绘图] 编码参考图发生严重异常: " + e.toString());
    }
    return "";
}

/**
 * 辅助方法：将 API 直接传回的 Base64 图片写入本地高速缓存文件，避免二次网络下载
 */
boolean saveBase64ToFile(String base64Str, String savePath) {
    try {
        byte[] bytes = android.util.Base64.decode(base64Str, android.util.Base64.DEFAULT);
        FileOutputStream fos = new FileOutputStream(savePath);
        fos.write(bytes);
        fos.flush();
        fos.close();
        return true;
    } catch (Exception e) {
        log("[AI智能绘图] 解码并保存 Base64 文件失败: " + e.getMessage());
    }
    return false;
}

/**
 * 高级智能递归探测器：深度扫描并挖掘 JSON 响应包中符合图片的各类参数 (支持 b64_json, inline_data, url 等多版本)
 */
String findImageInJson(Object obj) {
    if (obj instanceof JSONObject) {
        JSONObject json = (JSONObject) obj;
        if (json.has("inlineData")) {
            JSONObject id = json.optJSONObject("inlineData");
            if (id != null && id.has("data")) {
                return "base64:" + id.optString("data");
            }
        }
        if (json.has("inline_data")) {
            JSONObject id = json.optJSONObject("inline_data");
            if (id != null && id.has("data")) {
                return "base64:" + id.optString("data");
            }
        }
        if (json.has("b64_json")) {
            return "base64:" + json.optString("b64_json");
        }
        if (json.has("url")) {
            String url = json.optString("url");
            if (url.startsWith("http")) {
                return "url:" + url;
            }
        }
        // 深度递归检索所有 Key 结点
        java.util.Iterator keys = json.keys();
        while (keys.hasNext()) {
            String key = (String) keys.next();
            Object child = json.opt(key);
            String res = findImageInJson(child);
            if (res != null) {
                return res;
            }
        }
    } else if (obj instanceof JSONArray) {
        JSONArray array = (JSONArray) obj;
        for (int i = 0; i < array.length(); i++) {
            Object child = array.opt(i);
            String res = findImageInJson(child);
            if (res != null) {
                return res;
            }
        }
    }
    return null;
}

/**
 * 辅助下载网络图片并发送到微信窗口 (整合 Base64 极速直存和 HTTP 文件下载双通道)
 */
void sendAndDownloadImg(String resultStr, String talker) {
    if (resultStr == null || resultStr.trim().isEmpty()) {
        sendText(talker, "【AI绘图】生图接口调用失败，返回结果为空。");
        return;
    }
    
    String fileName = "ai_draw_" + System.currentTimeMillis() + ".png";
    String savePath = cacheDir + "/" + fileName;
    
    boolean success = false;
    if (resultStr.startsWith("base64:")) {
        String base64Data = resultStr.substring("base64:".length());
        log("[AI智能绘图] 检测到 Base64 直发图片数据，正在高速保存本地...");
        success = saveBase64ToFile(base64Data, savePath);
    } else if (resultStr.startsWith("url:")) {
        String url = resultStr.substring("url:".length());
        log("[AI智能绘图] 检测到 URL 网络链接，正在发起流式下载: " + url);
        success = downloadFile(url, savePath);
    } else {
        log("[AI智能绘图] 未识别协议前缀，默认作为常规 URL 下载: " + resultStr);
        success = downloadFile(resultStr, savePath);
    }
    
    if (success) {
        log("[AI智能绘图] 图片保存成功，正在发送到微信聊天窗口: " + talker);
        sendImage(talker, savePath);
        new File(savePath).deleteOnExit(); // 退出时自动删除
    } else {
        sendText(talker, "【AI绘图】保存图片失败。\n原始结果: " + (resultStr.length() > 200 ? resultStr.substring(0, 200) + "..." : resultStr));
    }
}

/**
 * 调用 OpenAI 兼容的第三方中转生图 API 获取图片 URL (文生图)
 */
String callImageGenApi(String prompt, String apiBaseUrl, String apiKey, String model, String size) {
    try {
        String fullUrl = apiBaseUrl.trim();
        if (!fullUrl.endsWith("/v1/images/generations")) {
            if (fullUrl.endsWith("/")) {
                fullUrl += "v1/images/generations";
            } else {
                fullUrl += "/v1/images/generations";
            }
        }
        
        URL url = new URL(fullUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setDoOutput(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(120000);
        
        JSONObject jsonBody = new JSONObject();
        jsonBody.put("model", model);
        jsonBody.put("prompt", prompt);
        jsonBody.put("n", 1);
        jsonBody.put("size", size);
        jsonBody.put("response_format", "url");
        
        String jsonStr = jsonBody.toString();
        
        OutputStream os = conn.getOutputStream();
        os.write(jsonStr.getBytes("UTF-8"));
        os.flush();
        os.close();
        
        int responseCode = conn.getResponseCode();
        if (responseCode == 200) {
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line);
            }
            br.close();
            
            JSONObject respObj = new JSONObject(response.toString());
            JSONArray dataArray = respObj.getJSONArray("data");
            if (dataArray.length() > 0) {
                return "url:" + dataArray.getJSONObject(0).getString("url");
            }
        } else {
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "UTF-8"));
            StringBuilder err = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                err.append(line);
            }
            br.close();
            log("[AI智能绘图] API 报错 (" + responseCode + "): " + err.toString());
        }
    } catch (Exception e) {
        log("[AI智能绘图] 网络请求异常: " + e.getMessage());
    }
    return null;
}

/**
 * 通过 Multipart Form-Data 发送原图和 Prompt 进行图片编辑 (图生图)
 */
String callImageEditApi(String localPath, String prompt, String apiBaseUrl, String apiKey, String model, String size) {
    String lineEnd = "\r\n";
    String twoHyphens = "--";
    String boundary = "----WebKitFormBoundary" + Long.toHexString(System.currentTimeMillis());
    
    try {
        String fullUrl = apiBaseUrl.trim();
        if (!fullUrl.endsWith("/v1/images/edits")) {
            if (fullUrl.endsWith("/")) {
                fullUrl += "v1/images/edits";
            } else {
                fullUrl += "/v1/images/edits";
            }
        }
        
        URL url = new URL(fullUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setUseCaches(false);
        conn.setDoOutput(true);
        conn.setDoInput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Connection", "Keep-Alive");
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setConnectTimeout(45000);
        conn.setReadTimeout(120000);
        
        OutputStream os = conn.getOutputStream();
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(os, "UTF-8"), true);
        
        // 1. 写入 prompt 参数
        writer.append(twoHyphens).append(boundary).append(lineEnd);
        writer.append("Content-Disposition: form-data; name=\"prompt\"").append(lineEnd);
        writer.append("Content-Type: text/plain; charset=UTF-8").append(lineEnd).append(lineEnd);
        writer.append(prompt).append(lineEnd);
        
        // 2. 写入 model 参数
        writer.append(twoHyphens).append(boundary).append(lineEnd);
        writer.append("Content-Disposition: form-data; name=\"model\"").append(lineEnd);
        writer.append("Content-Type: text/plain; charset=UTF-8").append(lineEnd).append(lineEnd);
        writer.append(model).append(lineEnd);
        
        // 3. 写入 size 参数
        writer.append(twoHyphens).append(boundary).append(lineEnd);
        writer.append("Content-Disposition: form-data; name=\"size\"").append(lineEnd);
        writer.append("Content-Type: text/plain; charset=UTF-8").append(lineEnd).append(lineEnd);
        writer.append(size).append(lineEnd);
        
        // 4. 写入原图图片文件
        File file = new File(localPath);
        writer.append(twoHyphens).append(boundary).append(lineEnd);
        writer.append("Content-Disposition: form-data; name=\"image\"; filename=\"").append(file.getName()).append("\"").append(lineEnd);
        String contentType = "image/png";
        if (file.getName().toLowerCase().endsWith(".jpg") || file.getName().toLowerCase().endsWith(".jpeg")) {
            contentType = "image/jpeg";
        }
        writer.append("Content-Type: ").append(contentType).append(lineEnd).append(lineEnd);
        writer.flush();
        
        // 读取并写入本地图片的二进制流
        FileInputStream fis = new FileInputStream(file);
        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = fis.read(buffer)) != -1) {
            os.write(buffer, 0, bytesRead);
        }
        os.flush();
        fis.close();
        
        writer.append(lineEnd);
        
        // 结束 Multipart 块
        writer.append(twoHyphens).append(boundary).append(twoHyphens).append(lineEnd);
        writer.close();
        
        int responseCode = conn.getResponseCode();
        if (responseCode == 200) {
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line);
            }
            br.close();
            
            JSONObject respObj = new JSONObject(response.toString());
            JSONArray dataArray = respObj.getJSONArray("data");
            if (dataArray.length() > 0) {
                return "url:" + dataArray.getJSONObject(0).getString("url");
            }
        } else {
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "UTF-8"));
            StringBuilder err = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                err.append(line);
            }
            br.close();
            log("[AI智能绘图] 变图 API 报错 (" + responseCode + "): " + err.toString());
        }
    } catch (Exception e) {
        log("[AI智能绘图] 变图请求异常: " + e.getMessage());
    }
    return null;
}

/**
 * 经典的 Java 纯流式下载图片函数
 */
boolean downloadFile(String fileUrl, String savePath) {
    try {
        URL url = new URL(fileUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(60000);
        int responseCode = conn.getResponseCode();
        if (responseCode == 200) {
            InputStream is = conn.getInputStream();
            FileOutputStream fos = new FileOutputStream(savePath);
            byte[] buffer = new byte[4096];
            int len;
            while ((len = is.read(buffer)) != -1) {
                fos.write(buffer, 0, len);
            }
            fos.flush();
            fos.close();
            is.close();
            return true;
        } else {
            log("[AI智能绘图] 下载图片失败，状态码: " + responseCode);
        }
    } catch (Exception e) {
        log("[AI智能绘图] 下载图片发生异常: " + e.getMessage());
    }
    return false;
}

/**
 * 诊断函数：通过反射直接将对象的所有声明方法格式化并分段发送到微信窗口，实现远程零死角结构探测
 */
void logMethodsAndSend(Object obj, String talker) {
    if (obj == null) return;
    try {
        java.lang.reflect.Method[] methods = obj.getClass().getDeclaredMethods();
        StringBuilder sb = new StringBuilder();
        sb.append("📋 [WAuxiliary 结构探测] 方法列表:\n");
        int count = 0;
        for (int i = 0; i < methods.length; i++) {
            String methodStr = methods[i].toString();
            methodStr = methodStr.replace("public final ", "")
                                 .replace("public ", "")
                                 .replace("me.hd.wauxv.data.bean.", "");
            sb.append(methodStr).append("\n");
            count++;
            
            if (count >= 25) {
                sendText(talker, sb.toString());
                sb = new StringBuilder();
                sb.append("📋 [续]:\n");
                count = 0;
                Thread.sleep(200);
            }
        }
        if (count > 0) {
            sendText(talker, sb.toString());
        }
    } catch (Exception e) {
        sendText(talker, "❌ 探测方法失败: " + e.getMessage());
    }
}

/**
 * 诊断函数：通过反射及降级备份打印对象的所有声明方法，辅助跨版本微信/WAuxiliary的开发调试
 */
void logMethods(Object obj) {
    if (obj == null) return;
    try {
        java.lang.reflect.Method[] methods = obj.getClass().getDeclaredMethods();
        log("[AI智能绘图] --- 反射打印对象方法: " + obj.getClass().getName() + " ---");
        for (int i = 0; i < methods.length; i++) {
            log("[AI智能绘图] " + methods[i].toString());
        }
    } catch (Exception e) {
        log("[AI智能绘图] 反射获取方法失败: " + e.getMessage());
    }
}

/**
 * 高级反射工具：在没有可用 Activity 的宿主沙箱环境下，深度探查微信当前的 Foreground 活跃 Activity
 */
android.app.Activity getCurrentActivity() {
    try {
        Class activityThreadClass = Class.forName("android.app.ActivityThread");
        Object activityThread = activityThreadClass.getMethod("currentActivityThread").invoke(null);
        java.lang.reflect.Field activitiesField = activityThreadClass.getDeclaredField("mActivities");
        activitiesField.setAccessible(true);
        Map activities = (Map) activitiesField.get(activityThread);
        
        android.app.Activity fallback = null;
        for (Object activityRecord : activities.values()) {
            Class activityRecordClass = activityRecord.getClass();
            java.lang.reflect.Field activityField = activityRecordClass.getDeclaredField("activity");
            activityField.setAccessible(true);
            android.app.Activity activity = (android.app.Activity) activityField.get(activityRecord);
            
            if (activity != null) {
                fallback = activity;
                java.lang.reflect.Field pausedField = activityRecordClass.getDeclaredField("paused");
                pausedField.setAccessible(true);
                if (!pausedField.getBoolean(activityRecord)) {
                    return activity;
                }
            }
        }
        if (fallback != null) {
            return fallback;
        }
    } catch (Exception e) {
        log("[AI智能绘图] 高级反射捕获 Activity 失败: " + e.getMessage());
    }
    return null;
}
