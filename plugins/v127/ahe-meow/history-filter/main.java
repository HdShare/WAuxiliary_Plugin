import java.text.SimpleDateFormat;
import java.util.Date;
import java.io.FileWriter;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import android.app.NotificationManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.DialogInterface;
import android.os.Build;
import android.app.AlertDialog;
import android.widget.ScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.RadioGroup;
import android.widget.RadioButton;
import android.widget.CheckBox;

// ==========================================
// ========== 取消任务管理 ==========
// ==========================================

// 使用 ConcurrentHashMap 存储每个任务的取消状态
static ConcurrentHashMap<Integer, AtomicBoolean> cancelFlags = new ConcurrentHashMap<>()

// 追踪每个任务正在执行的查询数量
static ConcurrentHashMap<Integer, AtomicInteger> activeQueries = new ConcurrentHashMap<>()

boolean isCancelled(int notificationId) {
    val flag = cancelFlags.get(notificationId)
    return flag != null && flag.get()
}

int getActiveQueryCount(int notificationId) {
    val counter = activeQueries.get(notificationId)
    return counter != null ? counter.get() : 0
}

void incrementQueryCount(int notificationId) {
    val counter = activeQueries.get(notificationId)
    if (counter != null) {
        counter.incrementAndGet()
    }
}

void decrementQueryCount(int notificationId) {
    val counter = activeQueries.get(notificationId)
    if (counter != null) {
        counter.decrementAndGet()
    }
}

void setCancelled(int notificationId) {
    val flag = cancelFlags.get(notificationId)
    if (flag != null) {
        flag.set(true)
        // 不更新通知，让后台线程自己处理
    }
}

void registerTask(int notificationId) {
    cancelFlags.put(notificationId, new AtomicBoolean(false))
    activeQueries.put(notificationId, new AtomicInteger(0))
}

void unregisterTask(int notificationId) {
    cancelFlags.remove(notificationId)
    activeQueries.remove(notificationId)
}

// ==========================================
// ========== 广播接收器 ==========
// ==========================================

static BroadcastReceiver cancelReceiver = null

void setupCancelReceiver() {
    if (cancelReceiver != null) return

    cancelReceiver = new BroadcastReceiver() {
        void onReceive(Context context, Intent intent) {
            try {
                if (intent == null) return

                val action = intent.getAction()
                if (action == null || !action.equals("com.wauxiliary.CANCEL_FILTER")) return

                val notificationId = intent.getIntExtra("notificationId", -1)
                if (notificationId == -1) return

                // 避免重复处理
                if (isCancelled(notificationId)) return

                setCancelled(notificationId)

            } catch (Throwable t) {
                log("广播接收器异常: " + t.getMessage())
                t.printStackTrace()
            }
        }
    }

    try {
        val filter = new android.content.IntentFilter("com.wauxiliary.CANCEL_FILTER")
        if (Build.VERSION.SDK_INT >= 33) {
            hostContext.registerReceiver(cancelReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            hostContext.registerReceiver(cancelReceiver, filter)
        }
    } catch (Exception e) {
        log("注册广播接收器失败: " + e.getMessage())
        e.printStackTrace()
    }
}

void onLoad() {
    log("历史消息过滤器已加载")
    setupCancelReceiver()
}

void onUnload() {
    log("历史消息过滤器已卸载")

    if (cancelReceiver != null) {
        try {
            hostContext.unregisterReceiver(cancelReceiver)
        } catch (Exception e) {
            log("取消注册广播接收器失败: " + e.getMessage())
        }
        cancelReceiver = null
    }
}

boolean onClickSendBtn(String text) {
    val input = text == null ? "" : text.trim()

    // 立即拦截所有 /filter 命令
    if (!input.startsWith("/filter")) return false

    // 从这里开始，所有路径都必须返回 true 以拦截发送

    if (input.length() == 0) return true

    val talker = getTargetTalker()
    if (talker == null || talker.isEmpty()) {
        toast("请先打开一个聊天窗口")
        log("错误: talker 为空")
        return true
    }

    // 解析命令
    val parts = input.substring(7).trim().split("\\s+")

    if (parts.length == 0 || parts[0].isEmpty()) {
        // 无参数时显示图形化界面
        showFilterConfigDialog(talker)
        return true
    }

    // 初始化过滤条件
    var endTime = System.currentTimeMillis()
    var startTime = endTime - (7 * 24 * 3600 * 1000L)  // 默认最近7天
    var maxCount = 100
    var keyword = ""
    var sender = ""
    var msgTypeFilter = ""
    var isSelfOnly = false
    var isOthersOnly = false
    var saveImages = false
    var imageContextMinutes = 0  // 关键字消息前后的图片上下文时间（分钟）

    // 解析参数
    var i = 0
    while (i < parts.length) {
        val arg = parts[i]

        if (arg.equals("-d") || arg.equals("--days")) {
            if (i + 1 < parts.length) {
                try {
                    val days = Integer.parseInt(parts[i + 1])
                    startTime = endTime - (days * 24 * 3600 * 1000L)
                    i++
                } catch (Exception e) {
                    toast("天数参数无效: ${parts[i + 1]}")
                    return true
                }
            }
        } else if (arg.equals("-h") || arg.equals("--hours")) {
            if (i + 1 < parts.length) {
                try {
                    val hours = Integer.parseInt(parts[i + 1])
                    startTime = endTime - (hours * 3600 * 1000L)
                    i++
                } catch (Exception e) {
                    toast("小时参数无效: ${parts[i + 1]}")
                    return true
                }
            }
        } else if (arg.equals("-n") || arg.equals("--count")) {
            if (i + 1 < parts.length) {
                try {
                    maxCount = Integer.parseInt(parts[i + 1])
                    if (maxCount > 500) maxCount = 500
                    i++
                } catch (Exception e) {
                    toast("数量参数无效: ${parts[i + 1]}")
                    return true
                }
            }
        } else if (arg.equals("-k") || arg.equals("--keyword")) {
            if (i + 1 < parts.length) {
                keyword = parts[i + 1]
                i++
            }
        } else if (arg.equals("-s") || arg.equals("--sender")) {
            if (i + 1 < parts.length) {
                sender = parts[i + 1]
                i++
            }
        } else if (arg.equals("-t") || arg.equals("--type")) {
            if (i + 1 < parts.length) {
                msgTypeFilter = parts[i + 1].toLowerCase()
                i++
            }
        } else if (arg.equals("--self")) {
            isSelfOnly = true
        } else if (arg.equals("--others")) {
            isOthersOnly = true
        } else if (arg.equals("--save")) {
            saveImages = true
        } else if (arg.equals("-ic") || arg.equals("--image-context")) {
            if (i + 1 < parts.length) {
                try {
                    imageContextMinutes = Integer.parseInt(parts[i + 1])
                    i++
                } catch (Exception e) {
                    toast("图片上下文时间参数无效: ${parts[i + 1]}")
                    return true
                }
            }
        } else if (arg.equals("--reload")) {
            toast("正在重载插件...")
            val result = reloadPlugin()
            if (result) {
                toast("✅ 插件重载成功")
            } else {
                toast("❌ 插件重载失败")
            }
            return true
        } else if (arg.equals("--help")) {
            showHelp()
            return true
        }

        i++
    }

    // 将耗时操作放到后台线程执行，避免阻塞 UI
    toast("📋 命令已接收，正在后台处理...")

    val finalTalker = talker
    val finalStartTime = startTime
    val finalEndTime = endTime
    val finalMaxCount = maxCount
    val finalKeyword = keyword
    val finalSender = sender
    val finalMsgTypeFilter = msgTypeFilter
    val finalIsSelfOnly = isSelfOnly
    val finalIsOthersOnly = isOthersOnly
    val finalSaveImages = saveImages
    val finalImageContextMinutes = imageContextMinutes

    new Thread(new Runnable() {
        void run() {
            try {
                executeFilter(finalTalker, finalStartTime, finalEndTime, finalMaxCount,
                    finalKeyword, finalSender, finalMsgTypeFilter, finalIsSelfOnly,
                    finalIsOthersOnly, finalSaveImages, finalImageContextMinutes)
            } catch (Exception e) {
                log("执行过滤时出错: " + e.getMessage())
                e.printStackTrace()
                // 异常情况下使用临时通知ID
                val errorNotificationId = (int) (System.currentTimeMillis() % Integer.MAX_VALUE)
                finishNotification(errorNotificationId, "历史消息过滤", "❌ 执行出错: " + e.getMessage())
            }
        }
    }).start()

    return true
}

void executeFilter(String talker, long startTime, long endTime, int maxCount,
    String keyword, String sender, String msgTypeFilter, boolean isSelfOnly,
    boolean isOthersOnly, boolean saveImages, int imageContextMinutes) {

    // 生成唯一的通知 ID，避免多个任务并发时通知冲突
    val notificationId = (int) (System.currentTimeMillis() % Integer.MAX_VALUE)

    // 注册任务，允许取消
    registerTask(notificationId)

    try {
        // 执行过滤 - 使用分批查询直到找到足够多的匹配消息
        sendNotification(notificationId, "历史消息过滤", "正在搜索历史消息...")

    toast("🔍 正在搜索历史消息...")

    val myWxid = getLoginWxid()
    val filtered = new ArrayList()

    try {
        var currentStart = startTime
        var batchNum = 0
        var totalScanned = 0
        var lastNotificationTime = System.currentTimeMillis()  // 记录上次更新通知的时间

        // 分批查询直到找到足够多的匹配消息
        while (filtered.size() < maxCount && currentStart < endTime) {
            // 第一件事：检查取消（在任何操作之前）
            if (isCancelled(notificationId)) {
                toast("⏹️ 正在取消...")

                // 等待所有正在执行的查询完成
                var waitCount = 0
                while (getActiveQueryCount(notificationId) > 0 && waitCount < 50) {
                    Thread.sleep(100)
                    waitCount++
                }

                // 额外等待1秒，确保查询彻底完成
                Thread.sleep(1000)

                toast("⏹️ 已取消搜索")
                finishNotification(notificationId, "历史消息过滤", "⏹️ 用户已取消")
                return
            }

            batchNum++

            // 减少通知更新频率：每10批或每3秒更新一次
            val currentTime = System.currentTimeMillis()
            val shouldUpdate = (batchNum % 10 == 1) || (currentTime - lastNotificationTime >= 3000)
            if (shouldUpdate) {
                sendProgressNotification(notificationId, "历史消息过滤", "查询中: 已扫描 ${totalScanned} 条，找到 ${filtered.size()} 条 (批次 #${batchNum})", filtered.size(), maxCount)
                lastNotificationTime = currentTime
            }

            // 增加查询计数
            incrementQueryCount(notificationId)

            val batchMessages
            var querySuccess = false
            try {
                batchMessages = queryHistoryMsg(talker, currentStart, 100)
                querySuccess = true
            } catch (Exception e) {
                // 捕获数据库异常并优雅降级
                if (e.getMessage() != null && e.getMessage().contains("already-closed")) {
                    toast("⚠️ 数据库连接已关闭")
                    decrementQueryCount(notificationId)
                    break  // 退出循环
                }
                // 其他异常，减少计数后继续抛出
                decrementQueryCount(notificationId)
                throw e
            } finally {
                // 只有查询成功时才在这里减少计数
                if (querySuccess) {
                    decrementQueryCount(notificationId)
                }
            }

            if (batchMessages == null || batchMessages.size() == 0) {
                break
            }

            totalScanned += batchMessages.size()

            // 找到最新的消息时间
            var latestTime = currentStart

            // 过滤这批消息
            for (var i = 0; i < batchMessages.size(); i++) {
                try {
                    val msg = batchMessages.get(i)
                    if (msg == null) {
                        continue
                    }
                    val msgTime = msg.getCreateTime()

                    // 更新最新时间
                    if (msgTime > latestTime) {
                        latestTime = msgTime
                    }

                    // 如果消息时间超出范围，停止
                    if (msgTime > endTime) {
                        currentStart = endTime + 1
                        break
                    }

                    // 类型过滤
                    if (!msgTypeFilter.isEmpty() && !matchType(msg, msgTypeFilter)) {
                        continue
                    }

                    // 发送者过滤
                    val msgSender = msg.getSendTalker()
                    if (!sender.isEmpty() && !msgSender.contains(sender)) {
                        continue
                    }

                    // 自己/他人过滤
                    if (isSelfOnly && !msgSender.equals(myWxid)) {
                        continue
                    }
                    if (isOthersOnly && msgSender.equals(myWxid)) {
                        continue
                    }

                    // 关键词过滤
                    if (!keyword.isEmpty()) {
                        val content = msg.getContent()
                        if (content == null || !content.contains(keyword)) {
                            continue
                        }
                    }

                    // 符合条件，添加到结果
                    filtered.add(msg)

                    // 如果已经找到足够多的消息，停止
                    if (filtered.size() >= maxCount) {
                        break
                    }

                } catch (Exception e) {
                    log("处理消息时出错: " + e.getMessage())
                }
            }

            // 如果已找到足够多的消息，停止查询
            if (filtered.size() >= maxCount) {
                break
            }

            // 如果这批消息不足100条，说明到头了
            if (batchMessages.size() < 100) {
                break
            }

            // 移动起始时间
            currentStart = latestTime + 1

            // 如果已超出时间范围，停止
            if (currentStart > endTime) {
                break
            }
        }

        sendProgressNotification(notificationId, "历史消息过滤", "✅ 找到 ${filtered.size()} 条匹配消息", filtered.size(), filtered.size())

        if (filtered.size() == 0) {
            toast("未找到符合条件的消息")
            finishNotification(notificationId, "历史消息过滤", "未找到符合条件的消息")
            return
        }

    } catch (Exception e) {
        toast("❌ 查询失败: ${e.getMessage()}")
        log("查询历史消息失败: " + e.getMessage())
        e.printStackTrace()
        finishNotification(notificationId, "历史消息过滤", "❌ 查询失败: ${e.getMessage()}")
        return
    }

    toast("✅ 找到 ${filtered.size()} 条消息")

    // 如果启用了图片上下文功能
    var contextImages = new ArrayList()
    if (imageContextMinutes > 0 && !keyword.isEmpty()) {
        try {
            // 更新通知
            sendProgressNotification(notificationId, "历史消息过滤", "正在收集图片上下文...", 0, filtered.size())

            toast("🔍 正在收集关键字消息前后的图片...")

            val imageSet = new HashSet()  // 用于去重

            var totalContextMessages = 0
            var totalQueries = 0

            for (var msgIndex = 0; msgIndex < filtered.size(); msgIndex++) {
                // 检查是否被用户取消
                if (isCancelled(notificationId)) {
                    toast("⏹️ 已取消图片收集")
                    finishNotification(notificationId, "历史消息过滤", "⏹️ 用户已取消")
                    return
                }

                val msg = filtered.get(msgIndex)
                val msgTime = msg.getCreateTime()
                val msgSender = msg.getSendTalker()
                val contextStart = msgTime - (imageContextMinutes * 60 * 1000L)
                val contextEnd = msgTime + (imageContextMinutes * 60 * 1000L)

                // 每处理一条消息就更新通知
                sendProgressNotification(notificationId, "历史消息过滤", "收集图片: 第 ${msgIndex + 1}/${filtered.size()} 条，已收集 ${contextImages.size()} 张", msgIndex + 1, filtered.size())

                // 分批查询：从 contextStart 开始，每次查询 100 条，直到超过 contextEnd
                var currentStart = contextStart
                var queryBatch = 0

                while (currentStart < contextEnd) {
                    // 第一件事：检查取消（在任何操作之前）
                    if (isCancelled(notificationId)) {
                        toast("⏹️ 正在取消...")

                        // 等待所有正在执行的查询完成
                        var waitCount = 0
                        while (getActiveQueryCount(notificationId) > 0 && waitCount < 50) {
                            Thread.sleep(100)
                            waitCount++
                        }

                        // 额外等待1秒
                        Thread.sleep(1000)

                        toast("⏹️ 已取消图片收集")
                        finishNotification(notificationId, "历史消息过滤", "⏹️ 用户已取消")
                        return
                    }

                    queryBatch++
                    totalQueries++

                    // 增加查询计数
                    incrementQueryCount(notificationId)

                    val batchMessages
                    var querySuccess = false
                    try {
                        batchMessages = queryHistoryMsg(talker, currentStart, 100)
                        querySuccess = true
                    } catch (Exception e) {
                        // 捕获数据库异常并优雅降级
                        if (e.getMessage() != null && e.getMessage().contains("already-closed")) {
                            toast("⚠️ 数据库连接已关闭")
                            decrementQueryCount(notificationId)
                            break  // 退出循环
                        }
                        // 其他异常，减少计数后继续抛出
                        decrementQueryCount(notificationId)
                        throw e
                    } finally {
                        // 只有查询成功时才在这里减少计数
                        if (querySuccess) {
                            decrementQueryCount(notificationId)
                        }
                    }

                    if (batchMessages == null || batchMessages.size() == 0) {
                        break
                    }

                    totalContextMessages += batchMessages.size()

                    // 找到这批消息中的最新时间
                    var latestTime = currentStart

                    for (var contextMsg : batchMessages) {
                        if (contextMsg == null) {
                            continue
                        }
                        val contextMsgTime = contextMsg.getCreateTime()

                        // 更新最新时间
                        if (contextMsgTime > latestTime) {
                            latestTime = contextMsgTime
                        }

                        // 如果消息时间超出范围，停止处理
                        if (contextMsgTime > contextEnd) {
                            currentStart = contextEnd + 1  // 结束外层循环
                            break
                        }

                        // 只收集同一发送者的图片
                        if (!contextMsg.getSendTalker().equals(msgSender)) {
                            continue
                        }

                        // 只收集图片消息
                        if (!contextMsg.isImage()) {
                            continue
                        }

                        // 检查时间范围
                        if (contextMsgTime < contextStart) {
                            continue
                        }

                        // 使用消息ID去重
                        val msgId = contextMsg.getMsgId()
                        if (!imageSet.contains(msgId)) {
                            imageSet.add(msgId)
                            contextImages.add(contextMsg)
                        }
                    }

                    // 如果查询到的消息数少于 100，说明已经到头了
                    if (batchMessages.size() < 100) {
                        break
                    }

                    // 移动起始时间到最新消息的时间 + 1ms，避免重复查询
                    currentStart = latestTime + 1

                    // 如果已经超过结束时间，停止查询
                    if (currentStart > contextEnd) {
                        break
                    }
                }
            }

            // 更新通知为完成状态
            finishNotification(notificationId, "历史消息过滤", "✅ 收集完成: ${contextImages.size()} 张图片")

            toast("✅ 收集到 ${contextImages.size()} 张相关图片")

        } catch (Exception e) {
            log("图片上下文收集失败: " + e.getMessage())
            e.printStackTrace()
            toast("❌ 图片收集失败: " + e.getMessage())
            finishNotification(notificationId, "历史消息过滤", "❌ 图片收集失败")
        }
    }

    // 保存结果
    val timestamp = System.currentTimeMillis()
    val filePath = pluginDir + "/history_" + timestamp + ".txt"

    FileWriter writer = null
    try {
        writer = new FileWriter(filePath)
        writer.write("=== 历史消息过滤结果 ===\n")
        writer.write("会话: " + formatDisplayName(talker) + "\n")
        writer.write("时间范围: " + formatTime(startTime) + " ~ " + formatTime(endTime) + "\n")
        writer.write("总计: " + filtered.size() + " 条消息\n")

        if (imageContextMinutes > 0 && contextImages.size() > 0) {
            writer.write("图片上下文: ±" + imageContextMinutes + " 分钟\n")
            writer.write("相关图片: " + contextImages.size() + " 张\n")
        }

        writer.write("\n")

        for (var msg : filtered) {
            writer.write("----------------------------------------\n")
            writer.write("时间: " + formatTime(msg.getCreateTime()) + "\n")
            writer.write("发送者: " + formatDisplayName(msg.getSendTalker()) + "\n")
            writer.write("类型: " + getMessageType(msg) + "\n")

            val content = msg.getContent()
            if (content != null && !content.isEmpty()) {
                writer.write("内容: " + content + "\n")
            }

            writer.write("\n")
        }

        // 如果有图片上下文，添加图片列表
        if (imageContextMinutes > 0 && contextImages.size() > 0) {
            writer.write("\n========================================\n")
            writer.write("=== 相关图片列表 ===\n")
            writer.write("（关键字消息发送者在前后 " + imageContextMinutes + " 分钟内发送的图片）\n\n")

            for (var img : contextImages) {
                writer.write("时间: " + formatTime(img.getCreateTime()) + "\n")
                writer.write("发送者: " + formatDisplayName(img.getSendTalker()) + "\n")

                val imageMsg = img.getImageMsg()
                if (imageMsg != null) {
                    writer.write("MD5: " + imageMsg.getMd5() + "\n")
                }

                writer.write("\n")
            }
        }

        writer.close()
        writer = null  // 标记已关闭

        // 发送结果到文件传输助手
        var resultMsg = "✅ 找到 ${filtered.size()} 条消息\n结果已保存到:\n${filePath}\n\n--- 预览前10条 ---\n${previewResults(filtered, 10)}"

        if (imageContextMinutes > 0 && contextImages.size() > 0) {
            resultMsg += "\n\n📷 相关图片: ${contextImages.size()} 张\n（关键字消息发送者前后 ${imageContextMinutes} 分钟内的图片）"
        }

        sendText("filehelper", resultMsg)

        // 如果启用了图片保存
        if (saveImages) {
            if (imageContextMinutes > 0 && contextImages.size() > 0) {
                // 下载上下文图片
                saveImageFiles(notificationId, contextImages, timestamp, talker)
            } else {
                // 下载过滤结果中的图片
                saveImageFiles(notificationId, filtered, timestamp, talker)
            }
        }

    } catch (Exception e) {
        toast("❌ 保存失败: ${e.getMessage()}")

        // 即使保存失败，也将预览发送到文件传输助手
        val resultMsg = "❌ 保存失败: ${e.getMessage()}\n\n--- 预览前10条 ---\n${previewResults(filtered, 10)}"
        sendText("filehelper", resultMsg)
    } finally {
        if (writer != null) {
            try {
                writer.close()
            } catch (Exception e) {
                log("关闭文件失败: " + e.getMessage())
            }
        }
    }
    } finally {
        // 延迟注销任务，给数据库操作足够的完成时间
        val finalNotificationId = notificationId
        new Thread(new Runnable() {
            void run() {
                try {
                    Thread.sleep(5000)
                    unregisterTask(finalNotificationId)
                } catch (Exception e) {
                    log("延迟注销失败: " + e.getMessage())
                }
            }
        }).start()
    }
}

boolean matchType(Object msg, String typeFilter) {
    if (typeFilter.equals("text")) return msg.isText()
    if (typeFilter.equals("image")) return msg.isImage()
    if (typeFilter.equals("voice")) return msg.isVoice()
    if (typeFilter.equals("video")) return msg.isVideo()
    if (typeFilter.equals("file")) return msg.isFile()
    if (typeFilter.equals("emoji")) return msg.isEmoji()
    if (typeFilter.equals("link")) return msg.isLink()
    return false
}

String getMessageType(Object msg) {
    if (msg.isText()) return "文本"
    if (msg.isImage()) return "图片"
    if (msg.isVoice()) return "语音"
    if (msg.isVideo()) return "视频"
    if (msg.isFile()) return "文件"
    if (msg.isEmoji()) return "表情"
    if (msg.isLink()) return "链接"
    if (msg.isSystem()) return "系统"
    return "其他"
}

String formatTime(long timestamp) {
    val sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    return sdf.format(new Date(timestamp))
}

String previewResults(List messages, int count) {
    val sb = new StringBuilder()
    val limit = Math.min(count, messages.size())

    for (var i = 0; i < limit; i++) {
        val msg = messages.get(i)
        sb.append((i + 1) + ". ")
        sb.append(formatTime(msg.getCreateTime()))
        sb.append(" [" + getMessageType(msg) + "] ")
        sb.append(msg.getSendTalker())

        val content = msg.getContent()
        if (content != null && !content.isEmpty()) {
            val preview = content.length() > 30 ? content.substring(0, 30) + "..." : content
            sb.append(": " + preview)
        }

        sb.append("\n")
    }

    if (messages.size() > count) {
        sb.append("... 还有 " + (messages.size() - count) + " 条")
    }

    return sb.toString()
}

void saveImageFiles(int notificationId, List messages, long timestamp, String talker) {
    toast("📥 开始下载图片...")
    sendProgressNotification(notificationId, "历史消息过滤", "准备下载图片...", 0, messages.size())

    // 创建保存目录
    val mediaDir = pluginDir + "/media_" + timestamp
    val dir = new File(mediaDir)
    if (!dir.exists()) {
        dir.mkdirs()
    }

    // 第一步：统计总数
    var totalImageCount = 0
    for (var msg : messages) {
        if (msg.isImage()) {
            val imageMsg = msg.getImageMsg()
            if (imageMsg != null) {
                val md5 = imageMsg.getMd5()
                if (md5 != null && !md5.isEmpty()) {
                    totalImageCount++
                }
            }
        }
    }

    toast("📥 开始下载 ${totalImageCount} 张图片...")

    // 第二步：开始下载
    var imageCount = 0  // 成功下载的数量
    var failedCount = 0  // 失败的数量
    var processedCount = 0
    var currentImageIndex = 0  // 当前处理的图片索引
    var lastNotificationTime = System.currentTimeMillis()  // 记录上次更新通知的时间

    for (var msg : messages) {
        try {
            // 检查是否被用户取消
            if (isCancelled(notificationId)) {
                toast("⏹️ 已取消图片下载")
                finishNotification(notificationId, "历史消息过滤", "⏹️ 已取消: 已下载 ${imageCount}/${totalImageCount} 张")
                return
            }

            processedCount++

            if (msg.isImage()) {
                val imageMsg = msg.getImageMsg()
                if (imageMsg != null) {
                    val md5 = imageMsg.getMd5()
                    if (md5 != null && !md5.isEmpty()) {
                        currentImageIndex++
                        val savePath = mediaDir + "/image_" + currentImageIndex + "_" + md5 + ".jpg"

                        // 减少通知更新频率：每10张或每3秒更新一次
                        val currentTime = System.currentTimeMillis()
                        val shouldUpdate = (currentImageIndex % 10 == 0) ||
                                         (currentImageIndex == totalImageCount) ||
                                         (currentTime - lastNotificationTime >= 3000)
                        if (shouldUpdate) {
                            sendProgressNotification(notificationId, "历史消息过滤", "下载图片: ${imageCount}/${totalImageCount} 成功", currentImageIndex, totalImageCount)
                            lastNotificationTime = currentTime
                        }

                        // 使用标准的 downloadImg API
                        try {
                            downloadImg(imageMsg, savePath)

                            // downloadImg 是异步的，等待文件出现
                            var downloaded = false
                            var attempt = 0
                            val maxAttempts = 20  // 最多等待10秒

                            while (!downloaded && attempt < maxAttempts) {
                                Thread.sleep(500)
                                attempt++

                                val file = new File(savePath)
                                if (file.exists() && file.length() > 0) {
                                    imageCount++
                                    downloaded = true
                                }
                            }

                            if (!downloaded) {
                                failedCount++
                            }
                        } catch (Exception e) {
                            failedCount++
                        }

                        // 每下载5张图片休息一下，避免内存溢出
                        if (currentImageIndex % 5 == 0) {
                            try {
                                Thread.sleep(200)
                            } catch (Exception e) {}
                        }
                    }
                }
            }
        } catch (Exception e) {
            log("处理图片时出错: " + e.getMessage())
        }
    }

    // 保存下载信息
    FileWriter infoWriter = null
    try {
        val infoPath = mediaDir + "/download_info.txt"
        infoWriter = new FileWriter(infoPath)
        infoWriter.write("=== 图片下载信息 ===\n")
        infoWriter.write("会话: " + formatDisplayName(talker) + "\n")
        infoWriter.write("时间: " + formatTime(timestamp) + "\n")
        infoWriter.write("尝试下载: " + totalImageCount + " 张\n")
        infoWriter.write("成功下载: " + imageCount + " 张\n")
        infoWriter.write("失败/超时: " + failedCount + " 张\n")
        infoWriter.close()
        infoWriter = null
    } catch (Exception e) {
        log("保存下载信息失败: " + e.getMessage())
    } finally {
        if (infoWriter != null) {
            try {
                infoWriter.close()
            } catch (Exception e) {
                log("关闭下载信息文件失败: " + e.getMessage())
            }
        }
    }

    // 发送通知到文件传输助手
    val summary = "📥 图片下载完成\n\n保存目录:\n${mediaDir}\n\n成功: ${imageCount}/${totalImageCount} 张\n失败: ${failedCount} 张"
    sendText("filehelper", summary)

    finishNotification(notificationId, "历史消息过滤", "✅ 下载完成: ${imageCount}/${totalImageCount} 张")
    toast("✅ 下载完成: 成功 ${imageCount} 张，失败 ${failedCount} 张，共 ${totalImageCount} 张")
}

void showHelp() {
    val help = new AlertDialog.Builder(getTopActivity())
        .setTitle("历史消息过滤器")
        .setMessage(
            "用法: /filter [选项]\n\n" +
            "时间范围:\n" +
            "  -d, --days <天数>     最近N天（默认7天）\n" +
            "  -h, --hours <小时>    最近N小时\n\n" +
            "过滤条件:\n" +
            "  -n, --count <数量>    最多返回N条（默认100）\n" +
            "  -k, --keyword <关键词> 包含关键词的消息\n" +
            "  -s, --sender <发送者>  指定发送者\n" +
            "  -t, --type <类型>     消息类型\n" +
            "      类型: text, image, voice, video, file, emoji, link\n" +
            "  --self                只看自己的消息\n" +
            "  --others              只看别人的消息\n\n" +
            "图片功能:\n" +
            "  --save                下载图片到本地\n" +
            "  -ic, --image-context <分钟>\n" +
            "                        收集关键字消息发送者\n" +
            "                        前后N分钟内的图片\n" +
            "                        （需配合 -k 使用）\n\n" +
            "操作:\n" +
            "  --reload              重载插件\n" +
            "  --help                显示此帮助\n\n" +
            "示例:\n" +
            "  /filter -d 3 -k 会议\n" +
            "  /filter -t image --save\n" +
            "  /filter -k 照片 -ic 10 --save\n" +
            "    (查找包含'照片'的消息，并下载\n" +
            "     发送者前后10分钟内的所有图片)"
        )
        .setPositiveButton("确定", null)
        .create()

    help.show()
}

// ==========================================
// ========== 通知相关方法 ==========
// ==========================================

void sendProgressNotification(int notificationId, String title, String text, int progress, int max) {
    try {
        // 如果任务已取消，不更新通知（避免覆盖取消状态）
        if (isCancelled(notificationId)) {
            return
        }

        val nm = hostContext.getSystemService(Context.NOTIFICATION_SERVICE)
        if (nm == null) return

        val channelId = "history_filter_v1"

        // 创建通知渠道 (Android 8.0+)
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                val channel = nm.getNotificationChannel(channelId)
                if (channel == null) {
                    val newChannel = new android.app.NotificationChannel(
                        channelId,
                        "历史消息过滤",
                        NotificationManager.IMPORTANCE_LOW  // 使用低优先级，避免干扰
                    )
                    newChannel.setSound(null, null)  // 静音
                    nm.createNotificationChannel(newChannel)
                }
            } catch (Exception e) {
                log("创建通知渠道失败: " + e.getMessage())
            }
        }

        // 构建通知
        val builder
        if (Build.VERSION.SDK_INT >= 26) {
            builder = new Notification.Builder(hostContext, channelId)
        } else {
            builder = new Notification.Builder(hostContext)
        }

        builder.setContentTitle(title)
               .setContentText(text)
               .setSmallIcon(android.R.drawable.stat_notify_chat)
               .setOngoing(true)  // 不可滑动删除
               .setAutoCancel(false)

        // 添加进度条
        if (max > 0) {
            builder.setProgress(max, progress, false)
        } else {
            builder.setProgress(0, 0, true)  // 不确定进度
        }

        // 添加取消按钮
        try {
            val cancelIntent = new Intent("com.wauxiliary.CANCEL_FILTER")
            cancelIntent.setPackage(hostContext.getPackageName())
            cancelIntent.putExtra("notificationId", notificationId)

            int uniqueCode = 0x50000000 ^ notificationId

            val flags
            if (Build.VERSION.SDK_INT >= 31) {
                flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
            } else if (Build.VERSION.SDK_INT >= 23) {
                flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            } else {
                flags = PendingIntent.FLAG_UPDATE_CURRENT
            }

            val cancelPendingIntent = PendingIntent.getBroadcast(
                hostContext,
                uniqueCode,
                cancelIntent,
                flags
            )

            Notification.Action action = new Notification.Action.Builder(
                android.R.drawable.ic_delete,
                "取消",
                cancelPendingIntent
            ).build()

            builder.addAction(action)
        } catch (Exception e) {
            log("添加取消按钮失败: " + e.getMessage())
            e.printStackTrace()
        }

        val notification = builder.build()
        nm.notify(notificationId, notification)

    } catch (Exception e) {
        log("发送通知失败: " + e.getMessage())
    }
}

void sendNotification(int notificationId, String title, String text) {
    sendProgressNotification(notificationId, title, text, 0, 0)
}

void updateNotification(int notificationId, String title, String text) {
    sendProgressNotification(notificationId, title, text, 0, 0)
}

void finishNotification(int notificationId, String title, String text) {
    try {
        val nm = hostContext.getSystemService(Context.NOTIFICATION_SERVICE)
        if (nm == null) return

        val channelId = "history_filter_v1"

        val builder
        if (Build.VERSION.SDK_INT >= 26) {
            builder = new Notification.Builder(hostContext, channelId)
        } else {
            builder = new Notification.Builder(hostContext)
        }

        builder.setContentTitle(title)
               .setContentText(text)
               .setSmallIcon(android.R.drawable.stat_notify_chat)
               .setOngoing(false)  // 可以滑动删除
               .setAutoCancel(true)

        val notification = builder.build()
        nm.notify(notificationId, notification)

    } catch (Exception e) {
        log("发送通知失败: " + e.getMessage())
    }
}

void cancelNotification() {
    try {
        val nm = hostContext.getSystemService(Context.NOTIFICATION_SERVICE)
        if (nm != null) {
            nm.cancel(notificationId)
        }
    } catch (Exception e) {
        log("取消通知失败: " + e.getMessage())
    }
}

void showFilterConfigDialog(String talker) {
    val activity = getTopActivity()
    if (activity == null) {
        toast("无法获取当前Activity")
        return
    }

    // 创建自定义布局
    val scrollView = new ScrollView(activity)
    val layout = new LinearLayout(activity)
    layout.setOrientation(LinearLayout.VERTICAL)
    layout.setPadding(50, 30, 50, 30)

    // 标题
    val titleView = new TextView(activity)
    titleView.setText("📋 历史消息过滤器")
    titleView.setTextSize(18)
    titleView.setTextColor(android.graphics.Color.parseColor("#333333"))
    titleView.setPadding(0, 0, 0, 30)
    layout.addView(titleView)

    // ========== 时间范围 ==========
    val timeLabel = new TextView(activity)
    timeLabel.setText("⏰ 时间范围")
    timeLabel.setTextSize(14)
    timeLabel.setTextColor(android.graphics.Color.parseColor("#666666"))
    timeLabel.setPadding(0, 10, 0, 10)
    layout.addView(timeLabel)

    val timeLayout = new LinearLayout(activity)
    timeLayout.setOrientation(LinearLayout.HORIZONTAL)

    val daysInput = new EditText(activity)
    daysInput.setHint("天数（默认7天）")
    daysInput.setTextSize(14)
    daysInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER)
    val daysParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1)
    daysParams.setMargins(0, 0, 10, 0)
    daysInput.setLayoutParams(daysParams)
    timeLayout.addView(daysInput)

    val hoursInput = new EditText(activity)
    hoursInput.setHint("小时（优先于天数）")
    hoursInput.setTextSize(14)
    hoursInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER)
    val hoursParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1)
    hoursParams.setMargins(10, 0, 0, 0)
    hoursInput.setLayoutParams(hoursParams)
    timeLayout.addView(hoursInput)

    layout.addView(timeLayout)

    // ========== 过滤条件 ==========
    val filterLabel = new TextView(activity)
    filterLabel.setText("🔍 过滤条件")
    filterLabel.setTextSize(14)
    filterLabel.setTextColor(android.graphics.Color.parseColor("#666666"))
    filterLabel.setPadding(0, 20, 0, 10)
    layout.addView(filterLabel)

    val keywordInput = new EditText(activity)
    keywordInput.setHint("关键词（可选）")
    keywordInput.setTextSize(14)
    keywordInput.setPadding(20, 20, 20, 20)
    layout.addView(keywordInput)

    val senderInput = new EditText(activity)
    senderInput.setHint("发送者（可选，如：张三）")
    senderInput.setTextSize(14)
    senderInput.setPadding(20, 20, 20, 20)
    layout.addView(senderInput)

    val countInput = new EditText(activity)
    countInput.setHint("最大数量（默认100）")
    countInput.setTextSize(14)
    countInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER)
    countInput.setPadding(20, 20, 20, 20)
    layout.addView(countInput)

    // ========== 消息类型 ==========
    val typeLabel = new TextView(activity)
    typeLabel.setText("📝 消息类型")
    typeLabel.setTextSize(14)
    typeLabel.setTextColor(android.graphics.Color.parseColor("#666666"))
    typeLabel.setPadding(0, 20, 0, 10)
    layout.addView(typeLabel)

    val typeSpinner = new Spinner(activity)
    val typeAdapter = new ArrayAdapter(activity, android.R.layout.simple_spinner_item,
        new String[]{"全部类型", "文本", "图片", "语音", "视频", "文件", "表情", "链接"})
    typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    typeSpinner.setAdapter(typeAdapter)
    layout.addView(typeSpinner)

    // ========== 发送者筛选 ==========
    val senderTypeLabel = new TextView(activity)
    senderTypeLabel.setText("👤 发送者筛选")
    senderTypeLabel.setTextSize(14)
    senderTypeLabel.setTextColor(android.graphics.Color.parseColor("#666666"))
    senderTypeLabel.setPadding(0, 20, 0, 10)
    layout.addView(senderTypeLabel)

    val senderRadioGroup = new RadioGroup(activity)
    senderRadioGroup.setOrientation(RadioGroup.HORIZONTAL)

    val radioAll = new RadioButton(activity)
    radioAll.setText("全部")
    radioAll.setTextSize(14)
    radioAll.setId(1)
    radioAll.setChecked(true)
    val radioAllParams = new RadioGroup.LayoutParams(RadioGroup.LayoutParams.WRAP_CONTENT, RadioGroup.LayoutParams.WRAP_CONTENT)
    radioAll.setLayoutParams(radioAllParams)
    senderRadioGroup.addView(radioAll)

    val radioSelf = new RadioButton(activity)
    radioSelf.setText("仅自己")
    radioSelf.setTextSize(14)
    radioSelf.setId(2)
    val radioSelfParams = new RadioGroup.LayoutParams(RadioGroup.LayoutParams.WRAP_CONTENT, RadioGroup.LayoutParams.WRAP_CONTENT)
    radioSelf.setLayoutParams(radioSelfParams)
    senderRadioGroup.addView(radioSelf)

    val radioOthers = new RadioButton(activity)
    radioOthers.setText("仅他人")
    radioOthers.setTextSize(14)
    radioOthers.setId(3)
    val radioOthersParams = new RadioGroup.LayoutParams(RadioGroup.LayoutParams.WRAP_CONTENT, RadioGroup.LayoutParams.WRAP_CONTENT)
    radioOthers.setLayoutParams(radioOthersParams)
    senderRadioGroup.addView(radioOthers)

    layout.addView(senderRadioGroup)

    // ========== 图片功能 ==========
    val imageLabel = new TextView(activity)
    imageLabel.setText("🖼️ 图片功能")
    imageLabel.setTextSize(14)
    imageLabel.setTextColor(android.graphics.Color.parseColor("#666666"))
    imageLabel.setPadding(0, 20, 0, 10)
    layout.addView(imageLabel)

    val imageContextLayout = new LinearLayout(activity)
    imageContextLayout.setOrientation(LinearLayout.HORIZONTAL)

    val contextLabel = new TextView(activity)
    contextLabel.setText("图片上下文（分钟）：")
    contextLabel.setTextSize(14)
    contextLabel.setPadding(0, 0, 10, 0)
    imageContextLayout.addView(contextLabel)

    val imageContextInput = new EditText(activity)
    imageContextInput.setHint("默认0，不收集")
    imageContextInput.setTextSize(14)
    imageContextInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER)
    val contextParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1)
    imageContextInput.setLayoutParams(contextParams)
    imageContextLayout.addView(imageContextInput)

    layout.addView(imageContextLayout)

    // 复选框布局
    val checkboxLayout = new LinearLayout(activity)
    checkboxLayout.setOrientation(LinearLayout.HORIZONTAL)

    val saveImagesCheckbox = new CheckBox(activity)
    saveImagesCheckbox.setText("")
    saveImagesCheckbox.setScaleX(0.8f)
    saveImagesCheckbox.setScaleY(0.8f)
    val checkboxParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    saveImagesCheckbox.setLayoutParams(checkboxParams)
    checkboxLayout.addView(saveImagesCheckbox)

    val checkboxLabel = new TextView(activity)
    checkboxLabel.setText("下载图片到本地")
    checkboxLabel.setTextSize(14)
    checkboxLabel.setPadding(10, 0, 0, 0)
    val labelParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    checkboxLabel.setLayoutParams(labelParams)
    checkboxLayout.addView(checkboxLabel)

    val checkboxLayoutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    checkboxLayoutParams.setMargins(0, 20, 0, 20)
    checkboxLayout.setLayoutParams(checkboxLayoutParams)
    layout.addView(checkboxLayout)

    scrollView.addView(layout)

    // 创建对话框
    val dialog = new AlertDialog.Builder(activity)
        .setView(scrollView)
        .setPositiveButton("🚀 开始筛选", new DialogInterface.OnClickListener() {
            void onClick(DialogInterface d, int which) {
                // 获取所有参数
                var endTime = System.currentTimeMillis()
                var startTime = endTime

                // 时间范围（小时优先）
                val hoursStr = hoursInput.getText().toString().trim()
                val daysStr = daysInput.getText().toString().trim()

                if (!hoursStr.isEmpty()) {
                    try {
                        val hours = Integer.parseInt(hoursStr)
                        startTime = endTime - (hours * 3600 * 1000L)
                    } catch (Exception e) {
                        toast("小时参数无效")
                        return
                    }
                } else if (!daysStr.isEmpty()) {
                    try {
                        val days = Integer.parseInt(daysStr)
                        startTime = endTime - (days * 24 * 3600 * 1000L)
                    } catch (Exception e) {
                        toast("天数参数无效")
                        return
                    }
                } else {
                    startTime = endTime - (7 * 24 * 3600 * 1000L)
                }

                // 关键词
                val keyword = keywordInput.getText().toString().trim()

                // 发送者
                val sender = senderInput.getText().toString().trim()

                // 数量
                var maxCount = 100
                val countStr = countInput.getText().toString().trim()
                if (!countStr.isEmpty()) {
                    try {
                        maxCount = Integer.parseInt(countStr)
                    } catch (Exception e) {
                        toast("数量参数无效")
                        return
                    }
                }

                // 消息类型
                var msgTypeFilter = ""
                val typePosition = typeSpinner.getSelectedItemPosition()
                if (typePosition == 1) msgTypeFilter = "text"
                else if (typePosition == 2) msgTypeFilter = "image"
                else if (typePosition == 3) msgTypeFilter = "voice"
                else if (typePosition == 4) msgTypeFilter = "video"
                else if (typePosition == 5) msgTypeFilter = "file"
                else if (typePosition == 6) msgTypeFilter = "emoji"
                else if (typePosition == 7) msgTypeFilter = "link"

                // 发送者类型
                var isSelfOnly = radioSelf.isChecked()
                var isOthersOnly = radioOthers.isChecked()

                // 图片功能
                var imageContextMinutes = 0
                val contextStr = imageContextInput.getText().toString().trim()
                if (!contextStr.isEmpty()) {
                    try {
                        imageContextMinutes = Integer.parseInt(contextStr)
                    } catch (Exception e) {
                        toast("图片上下文参数无效")
                        return
                    }
                }

                var saveImages = saveImagesCheckbox.isChecked()

                // 直接调用 executeFilter
                toast("📋 开始筛选，请稍候...")

                new Thread(new Runnable() {
                    void run() {
                        try {
                            executeFilter(talker, startTime, endTime, maxCount, keyword, sender,
                                msgTypeFilter, isSelfOnly, isOthersOnly, saveImages, imageContextMinutes)
                        } catch (Exception e) {
                            log("执行过滤时出错: " + e.getMessage())
                            e.printStackTrace()
                            toast("❌ 执行出错: " + e.getMessage())
                        }
                    }
                }).start()
            }
        })
        .setNegativeButton("❌ 取消", null)
        .create()

    dialog.show()
}

// 格式化显示名称：昵称/聊天名称（ID）
String formatDisplayName(String wxid) {
    if (wxid == null || wxid.isEmpty()) {
        return "未知"
    }

    try {
        // 使用 getFriendNickName 获取昵称（支持个人和群聊）
        String displayName = getFriendNickName(wxid)

        // 如果有显示名称，返回"名称（ID）"格式
        if (displayName != null && !displayName.isEmpty() && !displayName.equals(wxid)) {
            return displayName + "（" + wxid + "）"
        } else {
            // 没有显示名称，只返回 ID
            return wxid
        }
    } catch (Exception e) {
        // 出错时返回原始 ID
        return wxid
    }
}
