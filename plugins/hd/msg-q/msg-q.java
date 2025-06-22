
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Bundle;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import me.hd.wauxv.plugin.api.callback.PluginCallBack;

void saveMsgToFile(String outputPath, String avatarPath, String name, String msg) {
    try {
        // 昵称长度
        Paint namePaint = new Paint();
        namePaint.setAntiAlias(true);
        namePaint.setColor(Color.BLACK);
        namePaint.setTextSize(32f);
        float nameWidth = namePaint.measureText(name);

        // 消息长度
        Paint msgPaint = new Paint();
        msgPaint.setAntiAlias(true);
        msgPaint.setColor(Color.BLACK);
        msgPaint.setTextSize(46f);
        float msgWidth = msgPaint.measureText(msg);

        // 画布
        Bitmap canvasBitmap = Bitmap.createBitmap((int) (174 + 32 + Math.max(nameWidth, msgWidth) + 32 + 12), 180, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(canvasBitmap);
        canvas.drawColor(Color.parseColor("#EDEDED"));

        // 头像
        Bitmap avatar = BitmapFactory.decodeFile(avatarPath);
        Bitmap scaledAvatar = Bitmap.createScaledBitmap(avatar, 108, 108, true);
        Bitmap roundedAvatar = Bitmap.createBitmap(108, 108, Bitmap.Config.ARGB_8888);
        Canvas avatarCanvas = new Canvas(roundedAvatar);
        Paint avatarPaint = new Paint();
        avatarPaint.setAntiAlias(true);
        avatarPaint.setShader(new BitmapShader(scaledAvatar, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        avatarCanvas.drawRoundRect(0, 0, 108, 108, 10f, 10f, avatarPaint);
        canvas.drawBitmap(roundedAvatar, 32, 14, null);

        // 昵称
        canvas.drawText(name, 176, 48, namePaint);

        // 气泡
        Paint bubblePaint = new Paint();
        bubblePaint.setAntiAlias(true);
        bubblePaint.setColor(Color.WHITE);
        canvas.drawRoundRect(174, 64, 32 + 174 + msgWidth + 32, 170, 20, 20, bubblePaint);

        // 消息
        canvas.drawText(msg, 206, 134, msgPaint);

        // 输出
        File outputFile = new File(outputPath);
        FileOutputStream fos = new FileOutputStream(outputFile);
        canvasBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
        fos.flush();
        fos.close();
    } catch (IOException ignored) {
    }
}

void onHandleMsg(Object msgInfoBean) {
    if (msgInfoBean.isSend() && msgInfoBean.isQuote()) {
        String talker = msgInfoBean.getTalker();
        String title = msgInfoBean.getQuoteMsg().getTitle();
        if (title.startsWith("/q")) {
            String quoteMsgSendTalker = msgInfoBean.getQuoteMsg().getSendTalker();
            String quoteMsgAvatarUrl = getAvatarUrl(quoteMsgSendTalker);
            String quoteMsgDisplayName = msgInfoBean.getQuoteMsg().getDisplayName();

            String[] parts = title.split(" ", 2);
            String quoteMsgContent;
            if (parts.length > 1) {
                quoteMsgContent = parts[1];
            } else {
                String quoteMsgType = msgInfoBean.getQuoteMsg().getType();
                quoteMsgContent = quoteMsgType.equals("1") ? msgInfoBean.getQuoteMsg().getContent() : "暂不支持的引用类型";
            }

            if (!quoteMsgAvatarUrl.equals("")) {
                String avatarTmpPath = pluginDir + "/avatar.png";
                String messageTmpPath = pluginDir + "/message.png";
                download(quoteMsgAvatarUrl, avatarTmpPath, null, new PluginCallBack.DownloadCallback() {
                    public void onSuccess(File file) {
                        saveMsgToFile(messageTmpPath, avatarTmpPath, quoteMsgDisplayName, quoteMsgContent);
                        sendImage(talker, messageTmpPath);
                        new File(avatarTmpPath).delete();
                        new File(messageTmpPath).delete();
                    }

                    public void onError(Exception e) {
                        sendText(talker, "下载头像异常:" + e.getMessage());
                    }
                });
            } else {
                sendText(talker, "获取头像异常");
            }
        }
    }
}
