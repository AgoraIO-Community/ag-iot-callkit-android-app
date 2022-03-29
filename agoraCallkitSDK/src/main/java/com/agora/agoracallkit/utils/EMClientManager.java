package com.agora.agoracallkit.utils;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import com.agora.agoracallkit.beans.IotAlarm;
import com.agora.agoracallkit.logger.ALog;
import com.heytap.msp.push.HeytapPushManager;
import com.hyphenate.EMCallBack;
import com.hyphenate.EMError;
import com.hyphenate.EMMessageListener;
import com.hyphenate.chat.EMClient;
import com.hyphenate.chat.EMMessage;
import com.hyphenate.chat.EMOptions;
import com.hyphenate.chat.EMTextMessageBody;
import com.hyphenate.push.EMPushConfig;
import com.hyphenate.push.EMPushHelper;
import com.hyphenate.push.EMPushType;
import com.hyphenate.push.PushListener;
import com.hyphenate.util.EMLog;

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.util.Date;
import java.util.List;

public class EMClientManager implements EMMessageListener {
    private static final String TAG = "CALLKIT/EMClientMgr";
    private static EMClientManager instance;

    private boolean mIsInited = false;
    private boolean mEnableReceiveMsg = false;
    private long messageExpiredTime = 25;      //25秒前的呼叫消息不再处理

    public static EMClientManager getInstance() {
        if(instance == null) {
            synchronized (EMClientManager.class) {
                if(instance == null) {
                    instance = new EMClientManager();
                }
            }
        }
        return instance;
    }

    public boolean init(Context context, Bundle metaData) {
        if (mIsInited) {
            ALog.getInstance().e(TAG, "<init> EMClientManager was inited.");
            return false;
        }
        initEMPushService(context, metaData);
        mIsInited = true;
        mEnableReceiveMsg = false;
        return true;
    }

    //查询已登录的环信IM账号，转换为UID，主要用于APP启动时自动登录
    public long getLoginUid() {
        //是否已经登录
        if(!EMClient.getInstance().isLoggedInBefore()) {
            ALog.getInstance().e(TAG, "<getLoginUid> IM did not login, please login first!");
            return -1;
        }
        String userName = EMClient.getInstance().getCurrentUser();
        return Long.valueOf(userName);
    }

    private boolean initEMPushService(Context context, Bundle metaData) {
        //离线推送相关配置项
        EMOptions option = new EMOptions();
        option.setUseFCM(metaData.getBoolean("com.fcm.push.enable", false));
        EMPushConfig.Builder builder = new EMPushConfig.Builder(context);
        builder.enableVivoPush()                                                        //VIVO离线推送
                .enableHWPush()                                                             //华为离线推送
                .enableMiPush(                                                              //小米离线推送
                        metaData.getString("com.mi.push.app_id", ""),
                        metaData.getString("com.mi.push.api_key", "")
                )
                .enableOppoPush(                                                            //OPPO离线推送
                        metaData.getString("com.oppo.push.api_key", ""),
                        metaData.getString("com.oppo.push.app_secret", "")
                )
                .enableMeiZuPush(                                                           //魅族离线推送
                        metaData.getString("com.meizu.push.app_id", ""),
                        metaData.getString("com.meizu.push.api_key", "")
                )
                .enableFCM(                                                                 //谷歌原生离线推送
                        metaData.getString("com.fcm.push.senderid", "")
                );
        option.setPushConfig(builder.build());
        //初始化环信SDK
        EMClient.getInstance().init(context, option);
        EMClient.getInstance().setDebugMode(true);
        //接收消息
        EMClient.getInstance().chatManager().addMessageListener(this);
        //初始化推送服务
        initPush(context);
        return true;
    }

    //初始化环信推送服务
    private boolean initPush(Context context) {
        //OPPO SDK升级到2.1.0后需要进行初始化
        HeytapPushManager.init(context, true);
        //设置推送设置相关监听事件
        EMPushHelper.getInstance().setPushListener(new PushListener() {
            @Override
            public void onError(EMPushType pushType, long errorCode) {
                // TODO: 返回的errorCode仅9xx为环信内部错误，可从EMError中查询，其他错误请根据pushType去相应第三方推送网站查询。
                EMLog.e("PushClient", "Push client occur a error: $pushType - $errorCode");
            }
        });
        return true;
    }

    public boolean login(String userId) {
        EMClient.getInstance().login(
                userId,
                Integer.toString(userId.hashCode()),
                new EMCallBack() {
                    @Override
                    public void onSuccess() {
                        ALog.getInstance().i(TAG, "<login> success");
                        com.agora.agoracallkit.utils.CallbackManager.getInstance().onEMState(com.agora.agoracallkit.utils.CallbackManager.EM_LOGIN_SUCCESS,
                                "");
                    }

                    @Override
                    public void onError(int code, String error) {
                        ALog.getInstance().i(TAG, "<login> failed: " + code + ", " + error);
                        if (code == EMError.USER_ALREADY_LOGIN) {
                            ALog.getInstance().i(TAG, error);
                            com.agora.agoracallkit.utils.CallbackManager.getInstance().onEMState(com.agora.agoracallkit.utils.CallbackManager.EM_LOGIN_SUCCESS,
                                    "");
                        } else {
                            com.agora.agoracallkit.utils.CallbackManager.getInstance().onEMState(com.agora.agoracallkit.utils.CallbackManager.EM_LOGIN_FAILED,
                                    String.valueOf(code));
                        }
                    }

                    @Override
                    public void onProgress(int progress, String status) {
                    }
                });
        return true;
    }

    public void logout() {
        //退出登录
        EMClient.getInstance().logout(true, new EMCallBack() {
            @Override
            public void onSuccess() {
                ALog.getInstance().i(TAG, "<logout> success");
                com.agora.agoracallkit.utils.CallbackManager.getInstance().onEMState(com.agora.agoracallkit.utils.CallbackManager.EM_LOGOUT_SUCCESS,
                        "");
            }

            @Override
            public void onError(int code, String error) {
                ALog.getInstance().i(TAG, "<logout> failed: " + error);
                com.agora.agoracallkit.utils.CallbackManager.getInstance().onEMState(com.agora.agoracallkit.utils.CallbackManager.EM_LOGPUT_FAILED,
                        String.valueOf(code));
            }

            @Override
            public void onProgress(int progress, String status) {
            }
        });
    }

    //解决离线消息过早被接收，导致未登录状态无法工作的问题，调用该接口后才允许接收消息
    public void startReceiveMessage() {
        mEnableReceiveMsg = true;
    }

    @Override
    public void onMessageReceived(List<EMMessage> messages) {
        ALog.getInstance().i(TAG, "<onMessageReceived> messages.size = " + messages.size());
        for (int i = 0; i < 100; i++) {
            if (mEnableReceiveMsg) {
                parseMessages(messages);
                break;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onCmdMessageReceived(List<EMMessage> messages) {
        ALog.getInstance().i(TAG, "<onCmdMessageReceived> messages.size = " + messages.size());
    }

    @Override
    public void onMessageRead(List<EMMessage> messages) {

    }

    @Override
    public void onMessageDelivered(List<EMMessage> messages) {

    }

    @Override
    public void onMessageRecalled(List<EMMessage> messages) {

    }

    @Override
    public void onMessageChanged(EMMessage message, Object change) {

    }

    private void parseMessages(List<EMMessage> messages) {
        EMMessage[] msgs = messages.toArray(new EMMessage[messages.size()]);
        for (EMMessage msg : msgs) {
            ALog.getInstance().d(TAG, "<parseMessages> type=" + msg.getType()
                + ", chatType=" + msg.getChatType() + ", msgTime=" + msg.getMsgTime()
                + ", from=" + msg.getFrom() + ", msgId=" + msg.getMsgId());
            if (callMessageFilter(msg)) {
                ALog.getInstance().i(TAG, "<parseMessages> Body=" + ((EMTextMessageBody) msg.getBody()).getMessage());
                try {
                    JSONObject object = new JSONObject(((EMTextMessageBody) msg.getBody()).getMessage());
                    String request = object.getString("type");
                    if (request.equals("call_notice")) {  // 来电呼叫通知
                        JSONObject header = object.getJSONObject("header");
                        long session = header.getLong("session_id");
                        if (session > 0) {
                            JSONObject body = object.getJSONObject("body");
                            String channel = body.getString("channel");
                            String token = body.getString("token");
                            String attachMsg = "";
                            if (!body.isNull("attach_msg")) {
                                attachMsg = body.getString("attach_msg");
                            }

                            long caller = body.getLong("caller");
                            long listener = body.getLong("listener");
                            ALog.getInstance().i(TAG, "<parseMessages> session_id: " + session
                                    + ", caller_uid: " + caller
                                    + ", listener_uid: " + listener
                                    + ", channel: " + channel
                                    + ", token: " + token);
                            //这里响应第一个有效的呼叫会话，响应后状态应该置为忙，这样其他剩下的呼叫会话就会通知忙状态
                            com.agora.agoracallkit.utils.CallbackManager.getInstance().onReceiveCallMessage(
                                    session, caller, listener, channel, token, attachMsg);
                        }

                    } else if (request.equals("warning_notify")) {  // 报警消息通知
                        JSONObject header = object.getJSONObject("header");
                        long session = header.getLong("session_id");  // 固定为0，暂时不同管

                        JSONObject body = object.getJSONObject("body");
                        long alarmDevUid = body.getLong("device_uid"); // 相应设备的Uid
                        String alarmChannel = body.getString("record_channel"); // 云录制频道号
                        String alarmSid = body.getString("record_sid"); // 云录制Sid号
                        String alarmToken = body.getString("token");
                        String alarmMessage = body.getString("attach_msg");

                         com.agora.agoracallkit.utils.CallbackManager.getInstance().onReceiveAlaramMessage(
                                 msg.getMsgTime(), session, alarmDevUid, alarmChannel, alarmSid, alarmToken, alarmMessage);

                    } else if (request.equals("device_warning_notify")) { // 设备告警消息通知,针对Apical新增协议
                        JSONObject header = object.getJSONObject("header");
                        long session = header.getLong("session_id");  // 固定为0，暂时不同管

                        JSONObject body = object.getJSONObject("body");
                        IotAlarm iotAlarm = new IotAlarm();

                        iotAlarm.mOccurDate = body.getString("date");
                        iotAlarm.mDescription = body.getString("alarmDescription");
                        iotAlarm.mType = body.getInt("alarmType");
                        iotAlarm.mReaded = body.getBoolean("read");
                        iotAlarm.mAlarmId = body.getLong("alarmId");
                        iotAlarm.mAttachMsg = body.getString("attachMsg");
                        iotAlarm.mTimestamp = body.getLong("timestamp");

                        JSONObject rcdInfoBoj = body.getJSONObject("recordInfo");
                        iotAlarm.mRecordSid = rcdInfoBoj.getString("recordSid");
                        iotAlarm.mRecordChannel = rcdInfoBoj.getString("recordChannel");
                        iotAlarm.mDeviceUid = rcdInfoBoj.getLong("deviceUid");
                        iotAlarm.mDeviceId = rcdInfoBoj.getString("deviceId");

                        CallbackManager.getInstance().onReceiveIotAlarm(iotAlarm);

                    } else {
                        ALog.getInstance().i(TAG, "<callMessageFilter> It's not a call mesaage, request is " + request);
                    }
                } catch (Exception e) {
                    ALog.getInstance().e(TAG, "<callMessageFilter> agora call message parse faild.");
                    e.getStackTrace();
                }
            }
        }
    }

    //设置消息过期时间，应当比等待接听超时时间更短，减少不必要的回复和接听冲突逻辑
    public long setExpiredTime(int time) {
        if (time > 60) {
            ALog.getInstance().e(TAG, "<callMessageFilter> Expired Time cannot exceed 60 seconds.");
            return -1;
        }
        messageExpiredTime = time;
        return messageExpiredTime;
    }

    private boolean callMessageFilter(@NotNull EMMessage msg) {
        long sendTime = msg.getMsgTime();
        long curTime = new Date().getTime();
        //检查消息是否已经过期
        if ((curTime - sendTime) > (messageExpiredTime * 1000)) {
            ALog.getInstance().i(TAG, "<callMessageFilter> Expired message, current time is " + curTime);
            return false;
        }
        //检查是否文本消息
        if (msg.getType() != EMMessage.Type.TXT) {
            ALog.getInstance().i(TAG, "<callMessageFilter> Not TXT message.");
            return false;
        }
        return true;
    }
}
