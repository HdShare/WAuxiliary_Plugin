import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.File;

import me.hd.wauxv.plugin.api.callback.PluginCallBack;


import java.util.ArrayList;
import java.util.List;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONPath;
import com.alibaba.fastjson2.JSONArray;


/**
 * 抖音视频无水印下载
 * 1.文本输入分享链接，长按发送按钮，即可下载抖音视频
 * 2.群聊或者私聊或者自己发送抖音视频分享链接，自动下载抖音视频
 */

public static String regex = "https://v\\.douyin\\.com/[^\\s/]+/";
public static Pattern pattern = Pattern.compile(regex);

//是否开启群聊抖音视频下载
boolean isOpenGroup = true;


void sendDouyinVideo(String talker, String douyinUrl) {
    try {
//基于deno的抖音视频无水印下，项目开源地址:https://github.com/pwh-pwh/douyinVd 可fork本项目，自行到deno deploy,cf,vercel或者服务器部署
        //https://cf.eval.qzz.io/
        //"https://douyinvd.deno.dev
        String apiUrl = "https://cf.eval.qzz.io/?data&url=" + douyinUrl;
//        toast("要请求的地址"+apiUrl);
        get(apiUrl, null, new PluginCallBack.HttpCallback() {
            public void onSuccess(int respCode, String respContent) {
//                toast("请求返回结果:"+respContent);

                JSONObject jsonObj = JSON.parseObject(respContent);
                String vType = jsonObj.getString("type");


                String vUrl = jsonObj.getString("video_url");
//                 sendText(talker, vUrl);

                if (vType.equals("video")) {
                    download(vUrl, pluginDir + "/video.mp4", null, new PluginCallBack.DownloadCallback() {
                        public void onSuccess(File file) {
                            sendVideo(talker, file.getAbsolutePath());
                        }

                        public void onError(Exception e) {
                            sendText(talker, "下载异常:" + e.getMessage());
                        }
                    });
                } else {
                    toast("图文消息");
                    JSONArray imageUrlList = jsonObj.getJSONArray("image_url_list");
                    for (int i = 0; i < imageUrlList.size(); i++) {
//                                                 toast(imageUrlList.getString(i));

                        download(imageUrlList.getString(i), pluginDir + "/img" + i + ".jpg", null, new PluginCallBack.DownloadCallback() {
                            public void onSuccess(File file) {
                                sendImage(talker, file.getAbsolutePath());
                            }

                            public void onError(Exception e) {
                                sendText(talker, "图文下载异常:" + e.getMessage());
                            }
                        });


                    }
                }


            }


            public void onError(Exception e) {
                sendText(talker, "[抖音视频]请求解析异常:" + e.getMessage());
            }
        });
    } catch (Exception e) {

    }
}

void onHandleMsg(Object msgInfoBean) {
    if (msgInfoBean.isText()) {
        String content = msgInfoBean.getContent();
        String talker = msgInfoBean.getTalker();
        if (!msgInfoBean.isGroupChat() || isOpenGroup) {
            Matcher matcher = pattern.matcher(content);
            if (matcher.find()) {
                sendDouyinVideo(talker, matcher.group());
            }
        }
    }
}

boolean onLongClickSendBtn(String text) {
    Matcher matcher = pattern.matcher(text);
    if (matcher.find()) {
        sendDouyinVideo(getTargetTalker(), matcher.group());
        return true;
    }
    return false;
}
