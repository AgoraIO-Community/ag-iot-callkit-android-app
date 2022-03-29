package com.agora.agoracallkit.service;

import android.util.Log;

import com.agora.agoracallkit.logger.ALog;
import com.huawei.hms.push.HmsMessageService;
import com.huawei.hms.push.RemoteMessage;
import com.hyphenate.chat.EMClient;
import com.hyphenate.util.EMLog;

public class HMSPushService extends HmsMessageService {
    private final String TAG = "CALLKIT/HMSPushService";

    @Override
    public void onNewToken(String token) {
        if(token != null && !token.equals("")){
            //没有失败回调，假定token失败时token为null
            EMLog.d(TAG, "service register huawei hms push token success token:" + token);
            EMClient.getInstance().sendHMSPushTokenToServer(token);
        }else{
            EMLog.e(TAG, "service register huawei hms push token fail!");
        }
    }

    @Override
    public void onMessageReceived(RemoteMessage message) {
        // 判断消息是否为空
        if (message == null) {
            ALog.getInstance().e(TAG, "<onMessag> message is NULL");
            return;
        }

        // 获取消息内容
        ALog.getInstance().i(TAG, "<onMessag> message= " + message.getData()
                + "\n getFrom: " + message.getFrom()
                + "\n getTo: " + message.getTo()
                + "\n getMessageId: " + message.getMessageId()
                + "\n getSendTime: " + message.getSentTime()
                + "\n getDataMap: " + message.getDataOfMap()
                + "\n getMessageType: " + message.getMessageType()
                + "\n getTtl: " + message.getTtl()
                + "\n getToken: " + message.getToken());
    }
}
