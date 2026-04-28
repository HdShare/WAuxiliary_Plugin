
void sendTextImg(String title, String subTitle) {
    var textSize = 50.0f
    var padding = 40.0f

    var paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG)
    paint.setTypeface(Typeface.DEFAULT_BOLD)
    paint.setTextSize(textSize)

    var titleWidth = paint.measureText(title)
    var subTitleWidth = paint.measureText(subTitle)

    var width = (int) (titleWidth + subTitleWidth + padding * 2.5f)
    var height = (int) (textSize + padding * 2f)

    var bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    var canvas = new Canvas(bitmap)
    canvas.drawColor(Color.BLACK)

    var yOffset = (float) (height - padding * 2f + textSize * 2f / 3f)

    // 主标题纯白色文本
    paint.setColor(Color.WHITE)
    canvas.drawText(title, padding, yOffset, paint)

    // 副标题橙黄色背景
    paint.setColor(Color.parseColor("#F79817"))
    var rect = new RectF(
            (padding + titleWidth * 1.1f),
            (yOffset - textSize),
            (padding * 1.5f + titleWidth + subTitleWidth),
            (yOffset + textSize * 0.3f)
    )
    canvas.drawRoundRect(rect, 5, 5, paint)

    // 副标题纯黑色文本
    paint.setColor(Color.BLACK)
    canvas.drawText(subTitle, (padding + titleWidth * 1.15f), yOffset, paint)

    try {
        var path = "${cacheDir}/image.png"
        var out = new FileOutputStream(path)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        bitmap.recycle()

        sendImage(getTargetTalker(), path, "wxe3ad19e142df87b3")
    } catch (IOException e) {
    }
}

boolean onClickSendBtn(String text) {
    var cmd = "/作图 "
    if (text.startsWith(cmd)) {
        var str = text.substring(cmd.length())
        int index = str.indexOf(" ")
        if (index == -1) return false
        var title = str.substring(0, index)
        var subTitle = str.substring(index + 1)
        sendTextImg(title, subTitle)
        return true
    }
    return false
}
