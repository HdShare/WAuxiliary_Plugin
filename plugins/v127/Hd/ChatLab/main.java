
loadJava("ChatLabJsonBuilder.java")

void writeToFile(String filePath, String content) {
    try (FileWriter writer = new FileWriter(filePath)) {
        writer.write(content)
        toast("文件保存成功: " + filePath)
    } catch (IOException e) {
        e.printStackTrace()
    }
}

void exportGroup(String groupId, long startTime, int count) {
    var msgList = queryHistoryMsg(groupId, startTime, count)

    var builder = new ChatLabJsonBuilder()
            .setChatlab("0.0.2", System.currentTimeMillis() / 1000)
            .setMeta("群聊", "wechat", "group")

    var members = new LinkedHashMap()
    for (var msg : msgList) {
        var talker = msg.getSendTalker()
        var name = getFriendNickName(talker)
        if (!members.containsKey(talker)) {
            members.put(talker, name)
        }
    }
    for (var member : members.entrySet()) {
        builder.addMember(member.getKey(), member.getValue())
    }

    for (var msg : msgList) {
        var talker = msg.getSendTalker()
        var name = getFriendNickName(talker)

        var time = msg.getCreateTime() / 1000
        if (msg.isText()) {
            var content = msg.getContent()
            builder.addMessage(talker, name, time, 0, content)
        } else if (msg.isImage()) {
            builder.addMessage(talker, name, time, 1, "")
        } else if (msg.isVoice()) {
            builder.addMessage(talker, name, time, 2, "")
        } else if (msg.isVideo()) {
            builder.addMessage(talker, name, time, 3, "")
        } else if (msg.isFile()) {
            builder.addMessage(talker, name, time, 4, "")
        } else if (msg.isEmoji()) {
            builder.addMessage(talker, name, time, 5, "")
        } else if (msg.isLink()) {
            builder.addMessage(talker, name, time, 7, "")
        } else if (msg.isLocation()) {
            builder.addMessage(talker, name, time, 8, "")
        } else if (msg.isRedBag()) {
            builder.addMessage(talker, name, time, 20, "")
        } else if (msg.isTransfer()) {
            builder.addMessage(talker, name, time, 21, "")
        } else if (msg.isPat()) {
            builder.addMessage(talker, name, time, 22, "")
        } else if (msg.isVoip()) {
            builder.addMessage(talker, name, time, 23, "")
        } else if (msg.isApp()) {
            builder.addMessage(talker, name, time, 24, "")
        } else if (msg.isQuote()) {
            builder.addMessage(talker, name, time, 25, "")
        } else if (msg.isShareCard()) {
            builder.addMessage(talker, name, time, 27, "")
        } else if (msg.isSystem()) {
            builder.addMessage(talker, name, time, 80, "")
        } else if (msg.isRecalled()) {
            builder.addMessage(talker, name, time, 81, "")
        } else {
            builder.addMessage(talker, name, time, 99, "")
        }
    }

    var json = builder.buildPretty()
    writeToFile("${pluginDir}/chatlab_${groupId}.json", json)
}

void openSettings() {
    var ctx = getTopActivity()

    var layout = new LinearLayout(ctx)
    layout.setOrientation(LinearLayout.VERTICAL)
    layout.setPadding(32, 32, 32, 0)

    var lpEdt = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
    )
    lpEdt.setMargins(0, 16, 0, 16)

    var edtGroupId = new EditText(ctx)
    edtGroupId.setHint("请输入群聊Id")
    layout.addView(edtGroupId, lpEdt)

    var edtStartTime = new EditText(ctx)
    edtStartTime.setHint("请输入开始时间(毫秒)")
    edtStartTime.setText("1767196800000")
    edtStartTime.setInputType(InputType.TYPE_CLASS_NUMBER)
    layout.addView(edtStartTime, lpEdt)

    var edtCount = new EditText(ctx)
    edtCount.setHint("请输入导出数量")
    edtCount.setText("100")
    edtCount.setInputType(InputType.TYPE_CLASS_NUMBER)
    layout.addView(edtCount, lpEdt)

    new AlertDialog.Builder(ctx)
            .setTitle("ChatLab设置")
            .setView(layout)
            .setPositiveButton("导出", (dialog, which) -> {
                var groupId = edtGroupId.getText().toString().trim()
                var startTime = Long.parseLong(edtStartTime.getText().toString())
                var count = Integer.parseInt(edtCount.getText().toString())
                exportGroup(groupId, startTime, count)
            })
            .setNegativeButton("取消", null)
            .show()
}
