import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

// ==================== 预编译正则（避免每次调用重复编译） ====================
Pattern WXID_META_PATTERN = Pattern.compile("wxid_[a-zA-Z0-9_]+:\\d+:\\d+:[a-fA-F0-9]{32}");
Pattern STRIP_TAG_PATTERN = Pattern.compile("<[^>]*>");

// ==================== 名称缓存（避免重复查询好友名称） ====================
Map<String, String> nameCache = Collections.synchronizedMap(new HashMap<String, String>());

void onLoad() {}

boolean onClickSendBtn(String text) {
    if (text == null) return false;
    String cmd = text.trim();
    if (isExtractCommand(cmd)) {
        String talker = getTargetTalker();
        handleCommand(talker, cmd);
        return true;
    }
    return false;
}

void onHandleMsg(Object msgInfoBean) {
    try {
        if (msgInfoBean == null) return;
        if (!msgInfoBean.isSend()) return;
        if (!msgInfoBean.isText()) return;

        String content = msgInfoBean.getContent();
        if (content == null) return;
        content = content.trim();
        if (!isExtractCommand(content)) return;

        handleCommand(msgInfoBean.getTalker(), content);
    } catch (Throwable e) {
        log("onHandleMsg error: " + e.getMessage());
    }
}

// ==================== 指令解析 ====================

boolean isExtractCommand(String text) {
    if (text == null || text.isEmpty()) return false;
    return text.equals("/提取") || text.startsWith("/提取 ");
}

void handleCommand(String talker, String cmd) {
    try {
        if (talker == null || talker.isEmpty()) {
            toast("请先进入聊天界面");
            return;
        }

        String format = "txt";
        int count = -2; // 默认：智能模式

        if (!cmd.equals("/提取")) {
            String[] parts = cmd.split("\\s+");
            for (int i = 1; i < parts.length; i++) {
                String p = parts[i].toLowerCase();
                if (p.equals("txt") || p.equals("ai")) {
                    format = p;
                } else if (p.equals("全部")) {
                    count = Integer.MAX_VALUE;
                } else if (p.equals("新增")) {
                    count = -1;
                } else if (p.matches("\\d+")) {
                    count = Integer.parseInt(p);
                }
            }
        }

        streamExtract(talker, count, format);
    } catch (Throwable e) {
        log("handleCommand error: " + e.getMessage());
    }
}

// ==================== 流式提取（new Thread 真异步，分批写入防内存溢出） ====================

void streamExtract(final String talker, final int targetCount, final String format) {
    nameCache.clear();

    new Thread(new Runnable() {
        public void run() {
            FileOutputStream fos = null;
            OutputStreamWriter writer = null;
            File file = null;
            try {
                // --- 1. 准备文件 ---
                File dir = new File("/storage/emulated/0/Documents/WA/");
                if (!dir.exists()) dir.mkdirs();
                String displayName = resolveDisplayName(talker, talker);
                if (displayName == null || displayName.isEmpty()) displayName = talker;
                displayName = displayName.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_");
                String timestamp = new SimpleDateFormat("yyyy年M月d日H点mm分ss秒", Locale.getDefault()).format(new Date());
                String fileExt = format.equals("ai") ? "tsv" : format;
                file = new File(dir, displayName + "_" + timestamp + "." + fileExt);

                fos = new FileOutputStream(file);
                writer = new OutputStreamWriter(fos, "UTF-8");

                // --- 2. 确定查询参数 ---
                long lastTime = getLong("last_time_" + talker, 0L);
                long qStart = 0L;
                int remaining = targetCount;
                boolean isIncremental = false;

                if (targetCount == -2) {
                    if (lastTime > 0L) { qStart = lastTime + 1L; isIncremental = true; }
                    remaining = Integer.MAX_VALUE;
                } else if (targetCount == -1) {
                    if (lastTime <= 0L) { remaining = 100; }
                    else { qStart = lastTime + 1L; isIncremental = true; remaining = Integer.MAX_VALUE; }
                }

                // --- 3. 一次性查询（数据库只锁一次） ---
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                int total = 0;
                long maxTime = 0L;
                List allMsgs = null;

                if (remaining == Integer.MAX_VALUE && !isIncremental) {
                    allMsgs = queryHistoryMsg(talker, 0L, true, 100000);
                } else if (isIncremental) {
                    allMsgs = queryHistoryMsg(talker, qStart, true, 50000);
                } else {
                    allMsgs = queryHistoryMsg(talker, 0L, true, Math.min(remaining, 50000));
                }

                // --- 4. 格式化并写入 ---
                if (allMsgs != null && allMsgs.size() > 0) {
                    List filtered = filterReadable(allMsgs);
                    Collections.sort(filtered, new Comparator() {
                        public int compare(Object a, Object b) {
                            long ta = normalizeTime(a.getCreateTime());
                            long tb = normalizeTime(b.getCreateTime());
                            if (ta == tb) return 0;
                            return ta < tb ? -1 : 1;
                        }
                    });

                    for (int i = 0; i < filtered.size(); i++) {
                        Object msg = filtered.get(i);
                        try {
                            String line = formatMessageLine(msg, format, talker, sdf);
                            if (line != null) {
                                writer.write(line);
                                total++;
                            }
                            long t = normalizeTime(msg.getCreateTime());
                            if (t > maxTime) maxTime = t;
                            if (remaining != Integer.MAX_VALUE) {
                                remaining--;
                                if (remaining <= 0) break;
                            }
                        } catch (Throwable e) {
                            log("formatMessageLine error: " + e.getMessage());
                        }
                        // CPU 分段 yield
                        if (total % 100 == 0) {
                            try { Thread.sleep(1); } catch (Throwable ignored) {}
                        }
                    }

                }

                // --- 5. 收尾 ---
                if (writer != null) {
                    writer.flush();
                    writer.close();
                }
                if (fos != null) fos.close();

                if (total == 0) {
                    file.delete();
                    notify("提取聊天记录", "提取失败：未发现任何有效聊天记录");
                } else {
                    if (maxTime > 0L) putLong("last_time_" + talker, maxTime);
                    notify("提取聊天记录", "提取" + total + "条信息，保存在" + file.getAbsolutePath());
                }
            } catch (Throwable e) {
                notify("提取聊天记录", "提取报错：" + e.getMessage());
                log("streamExtract error: " + e.getMessage());
                try { if (writer != null) writer.close(); } catch (Throwable ignored) {}
                try { if (fos != null) fos.close(); } catch (Throwable ignored) {}
            }
        }
    }).start();
}

// TXT/AI 格式无需文件头尾（AI/JSONL 每行自描述）

// ==================== 单条消息格式化（从 List 中解耦出来） ====================

String formatMessageLine(Object msg, String format, String talker, SimpleDateFormat sdf) {
    try {
        if (!isReadableMsg(msg)) return null;

        String sender = msg.getSendTalker();
        String content = buildMessageContent(msg, format);

        int type = msg.getType();
        if ((content == null || content.isEmpty()) && type == 47) {
            content = "[表情]";
        }
        if (content == null || content.isEmpty()) return null;

        String speaker;
        if (sender != null && !sender.isEmpty()) {
            speaker = resolveDisplayName(sender, talker);
            if (speaker == null || speaker.isEmpty()) speaker = sender;
        } else {
            return null;
        }

        long time = normalizeTime(msg.getCreateTime());
        String timeText = time > 0 ? sdf.format(new Date(time)) : "未知时间";

        speaker = stripXmlJunk(speaker);
        content = stripXmlJunk(content);

        if (format.equals("ai")) {
            // 时间\t发送者\t内容 — 最简洁格式，Tab 分隔，AI 省 token
            return timeText + "\t" + speaker + "\t" + content.replace("\t", " ").replace("\r", " ").replace("\n", " ") + "\n";
        } else {
            // TXT
            return "[" + timeText + "] " + speaker + ":\n"
                 + content.trim() + "\n\n";
        }
    } catch (Throwable e) {
        return null;
    }
}

// ==================== 消息类型过滤 ====================

List filterReadable(List source) {
    ArrayList out = new ArrayList();
    if (source == null) return out;
    for (int i = 0; i < source.size(); i++) {
        Object msg = source.get(i);
        if (msg != null && isReadableMsg(msg)) out.add(msg);
    }
    return out;
}

boolean isReadableMsg(Object msg) {
    try {
        if (msg.isText()) return true;
        if (msg.isImage()) return true;
        if (msg.isVoice()) return true;
        if (msg.isVideo()) return true;
        if (msg.isFile()) return true;
        if (msg.isLink()) return true;
        if (msg.isQuote()) return true;
        if (msg.isPat()) return true;
        if (msg.isEmoji()) return true;
        if (msg.isRedBag()) return true;
        if (msg.isTransfer()) return true;
        if (msg.isShareCard()) return true;
        if (msg.isLocation()) return true;
        if (msg.isVoip()) return true;
        if (msg.isRecalled()) return true;
        if (msg.isNote()) return true;
        if (msg.isVideoNumberVideo()) return true;
        if (msg.isApp()) return true;

        int type = msg.getType();
        if (type == 47) return true;

        String content = msg.getContent();
        if (content != null && !content.isEmpty() && (content.contains("<emoji") || content.contains("<msg><emoji"))) {
            return true;
        }
    } catch (Throwable e) {
        log("isReadableMsg error: " + e.getMessage());
    }
    return false;
}

// ==================== 消息内容构建 ====================

String buildMessageContent(Object msg, String format) {
    try {
        String result = "";

        int type = msg.getType();
        if (type == 47 || msg.isEmoji()) {
            return "[表情]";
        }

        String content = msg.getContent();

        if (content != null && !content.isEmpty() && (content.contains("<emoji") || content.contains("<msg><emoji"))) {
            return "[表情]";
        }

        if (msg.isText()) {
            return content;
        }

        // 引用消息
        if (msg.isQuote()) {
            if (content != null && content.contains("<refermsg>")) {
                String myReply = extractXmlValue(content, "title");
                String displayName = extractXmlValue(content, "displayname");
                String rawRefContent = extractXmlValue(content, "content");
                String refContent = cleanRefermsgContent(rawRefContent);
                String createTimeStr = extractXmlValue(content, "createtime");
                String formattedRefTime = "未知时间";
                if (createTimeStr != null && !createTimeStr.isEmpty()) {
                    try {
                        long refTimeMs = Long.parseLong(createTimeStr) * 1000L;
                        SimpleDateFormat refSdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                        formattedRefTime = refSdf.format(new Date(refTimeMs));
                    } catch (Throwable ignored) {}
                }
                if (myReply == null || myReply.isEmpty()) myReply = "（发送了引用消息）";
                myReply = stripXmlJunk(myReply);
                displayName = stripXmlJunk(displayName);
                refContent = stripXmlJunk(refContent);

                return "[引用 " + formattedRefTime + " " + displayName + "]: " + refContent + "\n" + myReply;
            }

            var quote = msg.getQuoteMsg();
            String title = quote == null ? "" : quote.getTitle();
            String quoteContent = quote == null ? "" : quote.getContent();
            String fallback = stripXmlJunk(cleanRefermsgContent(firstNotEmpty(content, title, quoteContent)));
            return "[引用] " + fallback;
        }

        // 拍一拍
        if (msg.isPat()) {
            var patMsg = msg.getPatMsg();
            if (patMsg != null) {
                String fromUser = patMsg.getFromUser();
                String pattedUser = patMsg.getPattedUser();
                String fromName = (fromUser != null) ? resolveDisplayName(fromUser, msg.getTalker()) : "未知";
                String pattedName = (pattedUser != null) ? resolveDisplayName(pattedUser, msg.getTalker()) : "未知";
                result = fromName + " 拍了拍 " + pattedName;
            } else {
                result = "[拍一拍]";
            }
            return result;
        }

        // 红包
        if (msg.isRedBag()) {
            return parsePaymentContent(content, "红包");
        }

        // 转账
        if (msg.isTransfer()) {
            return parsePaymentContent(content, "转账");
        }

        // 文件（含大小）
        if (msg.isFile()) {
            var fileMsg = msg.getFileMsg();
            String title = fileMsg == null ? "" : fileMsg.getTitle();
            long size = fileMsg == null ? 0L : fileMsg.getSize();
            String sizeStr = formatFileSize(size);
            if (title != null && !title.isEmpty()) {
                result = "[文件] " + title + (sizeStr.isEmpty() ? "" : " (" + sizeStr + ")");
            } else {
                result = "[文件]" + (sizeStr.isEmpty() ? "" : " (" + sizeStr + ")");
            }
        }
        else if (msg.isImage()) result = "[图片]";
        else if (msg.isVoice()) result = "[语音]";
        else if (msg.isVideo()) result = "[视频]";
        else if (msg.isShareCard()) result = "[名片]";
        else if (msg.isLocation()) result = "[位置]";
        else if (msg.isVoipVoice()) result = "[语音通话]";
        else if (msg.isVoipVideo()) result = "[视频通话]";
        else if (msg.isVoip()) result = "[通话]";
        else if (msg.isRecalled()) result = "[消息已撤回]";
        else if (msg.isNote()) result = "[接龙]";
        else if (msg.isVideoNumberVideo()) result = "[视频号]";
        else if (msg.isApp()) result = parsePaymentContent(content, "应用");
        else if (msg.isLink()) result = "[链接] " + (content != null ? content : "");

        return result;

    } catch (Throwable e) {
        log("buildMessageContent error: " + e.getMessage());
    }
    return "";
}

// ==================== 联系人名称解析（带缓存） ====================

String resolveDisplayName(String wxid, String talker) {
    if (wxid == null || wxid.isEmpty()) return "";
    String cacheKey = wxid + "@" + talker;
    String cached = nameCache.get(cacheKey);
    if (cached != null) return cached;

    String result = wxid;
    try {
        String name = getFriendDisplayName(wxid, talker);
        if (name != null && !name.isEmpty()) result = name;
    } catch (Throwable ignored) {}
    if (result.equals(wxid)) {
        try {
            String name2 = getFriendRemarkName(wxid);
            if (name2 != null && !name2.isEmpty()) result = name2;
        } catch (Throwable ignored) {}
    }
    if (result.equals(wxid)) {
        try {
            String name3 = getFriendNickName(wxid);
            if (name3 != null && !name3.isEmpty()) result = name3;
        } catch (Throwable ignored) {}
    }
    nameCache.put(cacheKey, result);
    return result;
}

// ==================== 红包/转账/应用消息 XML 解析 ====================

String parsePaymentContent(String xmlContent, String type) {
    if (xmlContent == null || xmlContent.isEmpty()) {
        return "[" + type + "]";
    }
    try {
        String unescaped = xmlContent.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
        String title = extractXmlValue(unescaped, "title");
        String des = extractXmlValue(unescaped, "des");

        if ("红包".equals(type)) {
            String senderDes = firstNotEmpty(
                extractXmlValue(unescaped, "sendertitle"),
                extractXmlValue(unescaped, "wishing"),
                extractXmlValue(unescaped, "hbcontent")
            );
            if (senderDes != null && !senderDes.isEmpty()) {
                des = senderDes;
            }
        }

        boolean hasTitle = title != null && !title.isEmpty();
        boolean hasDes = des != null && !des.isEmpty();

        if (hasTitle && hasDes) {
            return "[" + type + "] " + title + " — " + des;
        } else if (hasTitle) {
            return "[" + type + "] " + title;
        } else if (hasDes) {
            return "[" + type + "] " + des;
        }
        String stripped = unescaped.replaceAll("<[^>]*>", "").trim();
        if (!stripped.isEmpty() && !stripped.equals(xmlContent)) {
            return "[" + type + "] " + stripped;
        }
    } catch (Throwable ignored) {}
    return "[" + type + "]";
}

// ==================== XML 工具方法 ====================

String extractXmlValue(String xml, String tag) {
    try {
        String startTag = "<" + tag + ">";
        String endTag = "</" + tag + ">";
        int start = xml.indexOf(startTag);
        int end = xml.indexOf(endTag);
        if (start != -1 && end != -1 && end > start) {
            String result = xml.substring(start + startTag.length(), end);
            return result.replace("<![CDATA[", "").replace("]]>", "").trim();
        }
    } catch (Throwable ignored) {}
    return "";
}

boolean isWeChatMetaJunk(String text) {
    if (text == null || text.isEmpty()) return false;
    return text.contains(":") && WXID_META_PATTERN.matcher(text).find();
}

String cleanRefermsgContent(String rawContent) {
    if (rawContent == null || rawContent.isEmpty()) return "";
    if (isWeChatMetaJunk(rawContent)) return "[表情]";
    String lower = rawContent.toLowerCase();
    if (lower.contains("voicemsg")) return "[语音]";
    if (lower.contains("img")) return "[图片]";
    if (lower.contains("videomsg")) return "[视频]";
    if (lower.contains("filemsg")) return "[文件]";
    if (lower.contains("patmsg")) return "[拍一拍]";
    if (lower.contains("emoji") || lower.contains("sticker")) return "[表情]";
    return rawContent;
}

String stripXmlJunk(String text) {
    if (text == null || text.isEmpty()) return "";
    String unescaped = text.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&").trim();

    if (isWeChatMetaJunk(unescaped)) return "[表情]";
    if (unescaped.contains("<emoji") || unescaped.contains("emoji") || unescaped.contains("<msg><emoji")) return "[表情]";

    // 仅剥离微信内部协议 XML（<?xml>/<msg>/<appmsg>），不处理用户发出的 HTML 标签
    if (unescaped.contains("<?xml") || unescaped.contains("<msg>") || unescaped.contains("<appmsg>")) {

        String title = extractXmlValue(unescaped, "title");
        if (title != null && !title.isEmpty()) return cleanRefermsgContent(title);

        String contentVal = extractXmlValue(unescaped, "content");
        if (contentVal != null && !contentVal.isEmpty()) return cleanRefermsgContent(contentVal);

        String stripped = STRIP_TAG_PATTERN.matcher(unescaped).replaceAll("").trim();
        if (stripped != null && !stripped.isEmpty()) return cleanRefermsgContent(stripped);
    }
    return text;
}

// ==================== 通用工具 ====================

String firstNotEmpty(String a, String b, String c) {
    if (a != null && !a.isEmpty()) return a;
    if (b != null && !b.isEmpty()) return b;
    if (c != null && !c.isEmpty()) return c;
    return "";
}

String formatFileSize(long bytes) {
    if (bytes <= 0) return "";
    if (bytes < 1024) return bytes + " B";
    if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
    if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
    return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
}

long normalizeTime(long time) {
    if (time <= 0) return time;
    if (time > 100000000000000000L) return time / 1000000L; // 纳秒→毫秒
    if (time > 10000000000000L) return time / 1000L;         // 微秒→毫秒
    if (time < 1000000000000L) return time * 1000L;           // 秒→毫秒
    return time;
}

