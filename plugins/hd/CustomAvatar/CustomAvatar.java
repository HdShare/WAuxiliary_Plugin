
String onGetAvatarPath(String wxid) {
    if (wxid.equals("weixin")) {
        return pluginDir + "/avatar.png";
    }
    return "";
}
