
void saveMsgToFile(String outputPath, String avatarPath, String name, String msg) {
    try {
        // 昵称长度
        var namePaint = new Paint()
        namePaint.setAntiAlias(true)
        namePaint.setColor(Color.BLACK)
        namePaint.setTextSize(32f)
        var nameWidth = namePaint.measureText(name)

        // 消息长度
        var msgPaint = new Paint()
        msgPaint.setAntiAlias(true)
        msgPaint.setColor(Color.BLACK)
        msgPaint.setTextSize(46f)
        var msgWidth = msgPaint.measureText(msg)

        // 画布
        var canvasBitmap = Bitmap.createBitmap((int) (174 + 32 + Math.max(nameWidth, msgWidth) + 32 + 12), 180, Bitmap.Config.ARGB_8888)
        var canvas = new Canvas(canvasBitmap)
        canvas.drawColor(Color.parseColor("#EDEDED"))

        // 头像
        var avatar = BitmapFactory.decodeFile(avatarPath)
        var scaledAvatar = Bitmap.createScaledBitmap(avatar, 108, 108, true)
        var roundedAvatar = Bitmap.createBitmap(108, 108, Bitmap.Config.ARGB_8888)
        var avatarCanvas = new Canvas(roundedAvatar)
        var avatarPaint = new Paint()
        avatarPaint.setAntiAlias(true)
        avatarPaint.setShader(new BitmapShader(scaledAvatar, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
        avatarCanvas.drawRoundRect(0f, 0f, 108f, 108f, 10f, 10f, avatarPaint)
        canvas.drawBitmap(roundedAvatar, 32, 14, null)

        // 昵称
        canvas.drawText(name, 176, 48, namePaint)

        // 气泡
        var bubblePaint = new Paint()
        bubblePaint.setAntiAlias(true)
        bubblePaint.setColor(Color.WHITE)
        canvas.drawRoundRect(174f, 64f, (float) (32 + 174 + msgWidth + 32), 170f, 20f, 20f, bubblePaint)

        // 消息
        canvas.drawText(msg, 206, 134, msgPaint)

        // 输出
        var outputFile = new File(outputPath)
        var fos = new FileOutputStream(outputFile)
        canvasBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
        fos.flush()
        fos.close()
    } catch (IOException ignored) {
    }
}

void onHandleMsg(Object msgInfoBean) {
    if (msgInfoBean.isSend() && msgInfoBean.isQuote()) {
        var talker = msgInfoBean.getTalker()
        var title = msgInfoBean.getQuoteMsg().getTitle()
        if (title.startsWith("/q")) {
            var quoteMsgSendTalker = msgInfoBean.getQuoteMsg().getSendTalker()
            var quoteMsgAvatarUrl = getAvatarUrl(quoteMsgSendTalker)
            var quoteMsgDisplayName = msgInfoBean.getQuoteMsg().getDisplayName()

            var parts = title.split(" ", 2)
            var quoteMsgContent = (parts.length > 1) ? parts[1] : msgInfoBean.getQuoteMsg().getContent()

            if (!quoteMsgAvatarUrl.equals("")) {
                var avatarTmpPath = "${cacheDir}/avatar.png"
                var messageTmpPath = "${cacheDir}/message.png"
                download(quoteMsgAvatarUrl, avatarTmpPath, null, file -> {
                    saveMsgToFile(messageTmpPath, avatarTmpPath, quoteMsgDisplayName, quoteMsgContent)
                    sendImage(talker, messageTmpPath)
                })
            } else {
                sendText(talker, "[语录插件]获取异常")
            }
        }
    }
}
