
Bitmap createGradientMask(int width, int height) {
    var mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    var maskCanvas = new Canvas(mask)
    var gradient = new LinearGradient(
            0, 0, width, 0,
            new int[]{Color.BLACK, Color.TRANSPARENT},
            new float[]{0f, 1f},
            Shader.TileMode.CLAMP
    )
    var paint = new Paint()
    paint.setShader(gradient)
    maskCanvas.drawRect(0, 0, width, height, paint)
    return mask
}

Bitmap applyGradientToBitmap(Bitmap original, Bitmap gradientMask) {
    var oWidth = original.getWidth()
    var oHeight = original.getHeight()
    var result = Bitmap.createBitmap(oWidth, oHeight, Bitmap.Config.ARGB_8888)
    var newCanvas = new Canvas(result)
    var newPaint = new Paint()
    newCanvas.drawBitmap(original, 0, 0, newPaint)
    newPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN))
    newCanvas.drawBitmap(gradientMask, 0, 0, newPaint)
    newPaint.setXfermode(null)
    return result
}

void saveMsgToFile(String outputPath, String avatarPath, String name, String msg) {
    try {
        // 昵称长度
        var namePaint = new Paint()
        namePaint.setAntiAlias(true)
        namePaint.setColor(Color.BLACK)
        namePaint.setTextSize(32f)

        // 消息长度
        var msgPaint = new Paint()
        msgPaint.setAntiAlias(true)
        msgPaint.setColor(Color.BLACK)
        msgPaint.setTextSize(46f)

        // 画布
        var canvasBitmap = Bitmap.createBitmap(1280, 640, Bitmap.Config.ARGB_8888)
        var canvas = new Canvas(canvasBitmap)
        canvas.drawColor(Color.parseColor("#EDEDED"))

        // 头像
        var avatar = BitmapFactory.decodeFile(avatarPath)
        var scaledAvatar = Bitmap.createScaledBitmap(avatar, 640, 640, true)

        // 渐变蒙版
        var gradientMask = createGradientMask(640, 640)

        // 应用渐变
        var gradientAvatar = applyGradientToBitmap(scaledAvatar, gradientMask)

        canvas.drawBitmap(gradientAvatar, 0, 0, null)

        // 消息
        canvas.drawText(msg, 660, 140, msgPaint)

        // 昵称
        canvas.drawText("—— " + name, 970, 550, namePaint)

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
        if (title.startsWith("/qpro")) {
            var quoteMsgSendTalker = msgInfoBean.getQuoteMsg().getSendTalker()
            var quoteMsgAvatarUrl = getAvatarUrl(quoteMsgSendTalker, true)
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
                sendText(talker, "获取头像异常")
            }
        }
    }
}
