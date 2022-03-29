package com.agora.agoracallkit.callkit;

import com.agora.agoracallkit.beans.IotAlarm;
import com.agora.agoracallkit.beans.UidInfoBean;

public interface AgoraCallNotify {
    public static final int RTM_LOGIN_FAILED = -1;      //RTM服务登录失败
    public static final int EM_LOGIN_FAILED = -2;       //离线推送服务登录失败
    public static final int EM_LOGOUT_FAILED = -3;      //离线推送服务退出失败

    //账号登录状态通知
    default void onLoginSuccess() {}
    default void onLoginFailed(int errorCode) {}

    //账号注销状态通知
    default void onLogoutSuccess() {}
    default void onLogoutFailed(int errorCode) {}

    //账号在其他设备登录
    default void onLoginOtherDevice() {}

    //收到被叫信息，可以从参数中获取主叫方的设备类型、account，本次通话分配的channel和token参数
    default void onReceiveCall(int caller_type, String caller_account,
                               String channel, String token, long uid, String attachMsg) {}

    //作为主叫方时才会收到该消息，可以根据本次通话分配的channel和token参数提前加入通道
    // 等待对端接听后快速通话，也可以忽略这个事件，等待对端接听后再加入通话，节约通话时长
    default void onCallDialing(String channel, String token, long uid) {}

    //作为主叫方时才会收到该消息，表明被呼叫方同意接听
    default void onCallAnswer(String channel, String token, long uid, UidInfoBean peerInfo) {}

    //作为主叫方时才会收到该消息，表明被呼叫方正在通话中
    default void onCallBusy() {}

    //作为主叫被叫均会收到该消息，表明对方挂断了通话，应该退出本次通话
    default void onCallHangup() {}

    //作为主叫被叫均会收到该消息，表明等待接听超时挂断
    default void onCallTimeout() {}

    // 接收到对端的定制化消息
    default void onReceiveCustomizeMessage(String customizeMsg) { }

    // 接收到云录制回应
    default void onReceiveCloudRecordResp(String recordChannel, String deviceToken, String cloudToken,
                                          long cloudUid) { }

    // 接收到设备告警信息
    default void onReceiveAlarmMessage(AlarmMessage alarmMessage) { }

    // 接收到设备告警信息，针对Apical项目新增回调
    default void onReceiveIotAlarm(IotAlarm iotAlarm) { }
}
