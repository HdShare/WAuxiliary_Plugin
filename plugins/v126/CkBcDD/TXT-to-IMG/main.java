import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

void sendTextImg(String title, String subTitle) {

    // ******************************
    // 相关常量配置
    final float TEXT_SIZE = 50.0f;
    final float PADDING = 40.0f;
    final float SUBTITLE_PADDING = 20.0f;
    final int BACKGROUND_COLOR = Color.BLACK;
    final int TITLE_TEXT_COLOR = Color.WHITE;
    final int SUBTITLE_BG_COLOR = Color.parseColor("#F79817");
    final int SUBTITLE_TEXT_COLOR = Color.BLACK;
    final int IMAGE_QUALITY = 100;
    final String IMAGE_PATH = cacheDir + "/temp.png";
    final String APP_ID = "wxe3ad19e142df87b3";    // 麻豆约拍
    // ******************************

    Bitmap bitmap = null;
    FileOutputStream out = null;
    File imgFile = null;

    try {
        // 图片生成阶段
        log("开始生成图片: 标题=" + title + ", 副标题=" + subTitle);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextSize(TEXT_SIZE);

        // 测量文本长度
        float titleWidth = paint.measureText(title);
        float subTitleWidth = paint.measureText(subTitle);

        // ******************************
        // 以下为绘图相关逻辑，美术精调请重点请关注这里

        // 定义矩形尺寸
        // 确保矩形足够长以容纳副标题文本（添加额外边距）
        int width = (int) (titleWidth + subTitleWidth + PADDING * 3.5f);
        int height = (int) (TEXT_SIZE + PADDING * 2f);

        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        if (bitmap == null) {
            log("错误: 创建位图失败");
            return;
        }

        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(BACKGROUND_COLOR);

        float yOffset = height - PADDING * 0.8f;
        paint.setColor(TITLE_TEXT_COLOR);
        canvas.drawText(title, PADDING, yOffset, paint);

        paint.setColor(SUBTITLE_BG_COLOR);
        float rectStartX = PADDING * 1.2f + titleWidth;

        RectF rect = new RectF(
                rectStartX,
                yOffset - TEXT_SIZE * 1.1f,
                rectStartX + subTitleWidth + SUBTITLE_PADDING * 2,
                yOffset + TEXT_SIZE * 0.3f);
        canvas.drawRoundRect(rect, 8, 8, paint);

        // 在矩形内居中绘制副标题
        paint.setColor(SUBTITLE_TEXT_COLOR);
        canvas.drawText(subTitle, rectStartX + SUBTITLE_PADDING, yOffset, paint);

        // 绘图相关逻辑结束
        // ******************************

        // 图片保存阶段
        log("正在保存图片到: " + IMAGE_PATH);
        out = new FileOutputStream(IMAGE_PATH);
        bitmap.compress(Bitmap.CompressFormat.PNG, IMAGE_QUALITY, out);
        out.flush();

        // 图片发送阶段
        log("正在发送图片...");
        imgFile = new File(IMAGE_PATH);
        if (!imgFile.exists() || imgFile.length() == 0) {
            log("错误: 图片保存失败或文件为空");
            return;
        }

        sendImage(getTargetTalker(), IMAGE_PATH, APP_ID);
        log("图片发送完成");

    } catch (OutOfMemoryError e) {
        log("内存不足错误: " + e.getMessage());
    } catch (IOException e) {
        log("IO错误: " + e.getMessage());
    } catch (Exception e) {
        log("发生未知错误: " + e.getMessage());
    } finally {
        // 资源释放阶段
        try {
            if (out != null) {
                out.close();
            }
        } catch (IOException e) {
            log("关闭文件流错误: " + e.getMessage());
        }

        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
            log("位图资源已回收");
        }

        // 删除临时图片文件
        try {
            File tempFile = new File(IMAGE_PATH);
            if (tempFile.exists()) {
                if (tempFile.delete()) {
                    log("临时图片已删除: " + IMAGE_PATH);
                } else {
                    log("临时图片删除失败: " + IMAGE_PATH);
                }
            }
        } catch (Exception e) {
            log("删除临时图片时发生错误: " + e.getMessage());
        }
    }
}

boolean onLongClickSendBtn(String text) {
    if (text.startsWith("/作图 ")) {
        String str = text.substring(4);
        int index = str.indexOf(" ");
        if (index == -1) {
            log("错误: 输入格式不正确，未找到副标题");
            toast("输入格式不正确，请使用'作图 标题 副标题'格式");
            return false;
        }

        String title = str.substring(0, index);
        String subTitle = str.substring(index + 1);

        // 检测标题和副标题是否为空
        if (title == null || title.trim().isEmpty()) {
            log("错误: 标题不能为空");
            toast("标题不能为空");
            return false;
        }

        if (subTitle == null || subTitle.trim().isEmpty()) {
            log("错误: 副标题不能为空");
            toast("副标题不能为空");
            return false;
        }

        log("解析输入: 标题=" + title + ", 副标题=" + subTitle);
        sendTextImg(title, subTitle);
        return true;
    }
    return false;
}
