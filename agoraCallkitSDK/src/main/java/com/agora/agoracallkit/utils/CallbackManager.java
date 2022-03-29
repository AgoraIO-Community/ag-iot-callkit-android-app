package com.agora.agoracallkit.utils;

import android.util.Log;

import com.agora.agoracallkit.beans.UidInfoBean;
import com.agora.agoracallkit.beans.IotAlarm;
import com.agora.agoracallkit.callkit.AgoraCallKit;
import com.agora.agoracallkit.callkit.AgoraCallNotify;
import com.agora.agoracallkit.callkit.AlarmMessage;
import com.agora.agoracallkit.logger.ALog;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import io.agora.rtm.RtmClientListener;

public class CallbackManager {
    private static final String TAG = "CALLKIT/CallbkMgr";
    private static CallbackManager instance;

    public static final int RTM_LOGIN_SUCCESS = 11;
    public static final int RTM_LOGIN_FAILED = 12;
    public static final int RTM_LOGOUT_FINISHED = 13;

    public static final int EM_LOGIN_SUCCESS = 21;
    public static final int EM_LOGIN_FAILED = 22;
    public static final int EM_LOGOUT_SUCCESS = 23;
    public static final int EM_LOGPUT_FAILED = 24;

    private List<AgoraCallNotify> mListenerList = new ArrayList<>();
    private boolean mRtmLogin = false;
    private boolean mEMLogin = false;

    public static CallbackManager getInstance() {
        if(instance == null) {
            synchronized (CallbackManager.class) {
                if(instance == null) {
                    instance = new CallbackManager();
                }
            }
        }
        return instance;
    }

    //订阅消息
    public synchronized void registerListener(AgoraCallNotify listener) {
        mListenerList.add(listener);
    }

    //取消订阅
    public synchronized void unregisterListener(AgoraCallNotify listener) {
        mListenerList.remove(listener);
    }

    //RTM登录状态回调
    public void onRtmState(int state, String message) {
        switch (state) {
            case RTM_LOGIN_SUCCESS:
                mRtmLogin = true;
                if (mEMLogin) {
                    //如果环信IM已经登录成功，RTM登录成功后可以判定账号登录成功
                    for (AgoraCallNotify listener : mListenerList) {
                        listener.onLoginSuccess();
                    }
                }
                break;
            case RTM_LOGIN_FAILED:
                //账号登录失败，错误原因是RTM登录失败导致
                for (AgoraCallNotify listener : mListenerList) {
                    listener.onLoginFailed(AgoraCallNotify.RTM_LOGIN_FAILED);
                }
                break;
            case RTM_LOGOUT_FINISHED:
                mRtmLogin = false;
                if (!mEMLogin) {
                    //如果环信IM已经成功注销，RTM注销后可以判定账号注销完成
                    for (AgoraCallNotify listener : mListenerList) {
                        listener.onLogoutSuccess();
                    }
                }
                break;
        }
    }

    //环信IM登录状态回调
    public void onEMState(int state, String message) {
        switch (state) {
            case EM_LOGIN_SUCCESS:
                mEMLogin = true;
                if (mRtmLogin) {
                    //如果RTM已经登录成功，IM登录成功后可以判定账号登录成功
                    for (AgoraCallNotify listener : mListenerList) {
                        listener.onLoginSuccess();
                    }
                }
                break;
            case EM_LOGIN_FAILED:
                //账号登录失败，错误原因是IM登录失败导致
                for (AgoraCallNotify listener : mListenerList) {
                    listener.onLoginFailed(AgoraCallNotify.EM_LOGIN_FAILED);
                }
                break;
            case EM_LOGOUT_SUCCESS:
                mEMLogin = false;
                if (!mRtmLogin) {
                    //如果RTM已经注销成功，IM注销后可以判定账号注销完成
                    for (AgoraCallNotify listener : mListenerList) {
                        listener.onLogoutSuccess();
                    }
                }
                break;
            case EM_LOGPUT_FAILED:
                //账号注销失败，错误原因是IM注销失败导致
                for (AgoraCallNotify listener : mListenerList) {
                    listener.onLogoutFailed(AgoraCallNotify.EM_LOGOUT_FAILED);
                }
                break;
        }
    }

    //收到呼叫请求
    public void onReceiveCallMessage(long session_id, long caller_uid, long listener_uid,
                                     String channel, String token, String attachMsg) {
        //是否是登录用户的消息
        if (listener_uid != com.agora.agoracallkit.utils.CallStateManager.getInstance().getLocalUser().getUid()) {
            ALog.getInstance().e(TAG, "<onReceiveCallMessage> It's not login user's UID: " + listener_uid);
            return;
        }
        //查询用户account
        UidInfoBean info = com.agora.agoracallkit.utils.HttpRequestInterface.getInstance().queryAccountWithUid(caller_uid);
        if (info == null) {
            ALog.getInstance().e(TAG, "<onReceiveCallMessage> cannot found caller UID: " + caller_uid);
            return;
        }
        //呼叫会话决策处理
        if (com.agora.agoracallkit.utils.CallStateManager.getInstance().receiveCallRequest(session_id, info, channel, token)) {
            ALog.getInstance().i(TAG, "<onReceiveCallMessage> New call from " + info.getAccount());
            //如果是需要响应的呼叫，通知响应监听者
            for (AgoraCallNotify listener : mListenerList) {
                listener.onReceiveCall(info.getType(), info.getAccount(), channel, token, info.getUid(), attachMsg);
            }
        } else {
            com.agora.agoracallkit.utils.AgoraRtmManager.getInstance().sendCallKissChoiceMessage(
                    session_id, caller_uid, com.agora.agoracallkit.utils.AgoraRtmManager.CALL_KISS_CHOICE_BUSY);
        }
    }

    //收到业务服务的呼叫参数回复，主动呼叫时才会收到
    public void onReceiveCallParams(long session_id, String channel, String token) {
        if (com.agora.agoracallkit.utils.CallStateManager.getInstance().updateNewCallResponse(session_id, channel, token)) {
            //通知收到了服务器分配的channel和token参数
            for (AgoraCallNotify listener : mListenerList) {
                listener.onCallDialing(channel, token, com.agora.agoracallkit.utils.CallStateManager.getInstance().getLocalUser().getUid());
            }
        }
    }

    //收到对端接听选择回复，呼叫和通话过程中均会收到
    public void onReceiveCallKissChoice(long session_id, long operator, String operation) {
        if (operation.equals(com.agora.agoracallkit.utils.AgoraRtmManager.CALL_KISS_CHOICE_ANSER)) {
            if (com.agora.agoracallkit.utils.CallStateManager.getInstance().receiverCallAnswer(session_id, operator)) {
                //主动一对多呼叫时，一个对端选择接听后其他端都自动主动挂断
                List<UidInfoBean> users = com.agora.agoracallkit.utils.CallStateManager.getInstance().getNoResponseRemoteUsers();
                for (int i = 0; i < users.size(); i++) {
                    com.agora.agoracallkit.utils.AgoraRtmManager.getInstance().sendCallKissChoiceMessage(
                            session_id, users.get(i).getUid(), com.agora.agoracallkit.utils.AgoraRtmManager.CALL_KISS_CHOICE_HANGUP);
                }
                //主动挂断未结束反馈的对端后可以清空未响应列表了
                com.agora.agoracallkit.utils.CallStateManager.getInstance().clearNoResponseRemoteUsers();
                //有效会话回调接听
                for (AgoraCallNotify listener : mListenerList) {
                    listener.onCallAnswer(com.agora.agoracallkit.utils.CallStateManager.getInstance().getCurCallChannel(),
                            com.agora.agoracallkit.utils.CallStateManager.getInstance().getCurCallToken(),
                            com.agora.agoracallkit.utils.CallStateManager.getInstance().getLocalUser().getUid(),
                            com.agora.agoracallkit.utils.CallStateManager.getInstance().getRemoteUser());
                }
            } else {
                //无效会话需要通知对端已挂断
                com.agora.agoracallkit.utils.AgoraRtmManager.getInstance().sendCallKissChoiceMessage(
                        session_id, operator, com.agora.agoracallkit.utils.AgoraRtmManager.CALL_KISS_CHOICE_HANGUP);
            }
        } else if (operation.equals(com.agora.agoracallkit.utils.AgoraRtmManager.CALL_KISS_CHOICE_BUSY)) {
            //一个被呼叫端忙的情况下，不影响其他端
            if (com.agora.agoracallkit.utils.CallStateManager.getInstance().receiveCallBusy(session_id, operator)) {
                for (AgoraCallNotify listener : mListenerList) {
                    listener.onCallBusy();
                }
            }
        } else if (operation.equals(com.agora.agoracallkit.utils.AgoraRtmManager.CALL_KISS_CHOICE_HANGUP)) {
            List<UidInfoBean> users = com.agora.agoracallkit.utils.CallStateManager.getInstance().getNoResponseRemoteUsers();
            if (com.agora.agoracallkit.utils.CallStateManager.getInstance().receiveCallRefuse(session_id, operator)) {
                //主动一对多呼叫时，一个对端选择挂断后其他端都自动主动挂断
                for (int i = 0; i < users.size(); i++) {
                    com.agora.agoracallkit.utils.AgoraRtmManager.getInstance().sendCallKissChoiceMessage(
                            session_id, users.get(i).getUid(), com.agora.agoracallkit.utils.AgoraRtmManager.CALL_KISS_CHOICE_HANGUP);
                }
                for (AgoraCallNotify listener : mListenerList) {
                    listener.onCallHangup();
                }
            }
        } else if (operation.equals(com.agora.agoracallkit.utils.AgoraRtmManager.CALL_KISS_CHOICE_TIMEOUT)) {
            //超时等同于自动挂断，只有被呼状态会收到事件，不需要处理其他呼叫端
            if (com.agora.agoracallkit.utils.CallStateManager.getInstance().receiveCallRefuse(session_id, operator)) {
                for (AgoraCallNotify listener : mListenerList) {
                    listener.onCallTimeout();
                }
            }
        } else {
            ALog.getInstance().e(TAG, "<onReceiveCallKissChoice> Unknown operation: " + operation);
        }
    }

    //主叫端超时检测线程超时通知
    public void onReceiveCallTimeout() {
        long session = com.agora.agoracallkit.utils.CallStateManager.getInstance().getCurSessionId();
        List<UidInfoBean> users = com.agora.agoracallkit.utils.CallStateManager.getInstance().getNoResponseRemoteUsers();
        if (com.agora.agoracallkit.utils.CallStateManager.getInstance().refuseCall()) {
            //主动一对多呼叫时，超时发送取消呼叫
            for (int i = 0; i < users.size(); i++) {
                com.agora.agoracallkit.utils.AgoraRtmManager.getInstance().sendCallKissChoiceMessage(
                        session, users.get(i).getUid(), com.agora.agoracallkit.utils.AgoraRtmManager.CALL_KISS_CHOICE_TIMEOUT);
            }
            for (AgoraCallNotify listener : mListenerList) {
                listener.onCallTimeout();
            }
        }
    }

    //用户在其他设备上登录
    public void onUserLoginOnOtherDevice() {
        com.agora.agoracallkit.utils.CallStateManager.getInstance().refuseCall();
        com.agora.agoracallkit.utils.CallStateManager.getInstance().clearLocalUserInfo();
        for (AgoraCallNotify listener : mListenerList) {
            listener.onLoginOtherDevice();
        }
    }

    // 接收到对端的定制化消息
    public void onReceiveCustomizeMsg(long session_id, String customizeMsg) {
        for (AgoraCallNotify listener : mListenerList) {
            listener.onReceiveCustomizeMessage(customizeMsg);
        }
    }

    /*
     * @brief  从服务器收到云录制回应
     * @param recordChannel : 云录制使用的频道号
     * @param deviceToken : 设备端录制token
     * @param cloudToken : 服务器端token
     * @param cloudUid : 服务端云录制Uid
     * @return None
     */
    public void onReceiveCloudRecordResp(String recordChannel, String deviceToken, String cloudToken,
                                         long cloudUid) {

        for (AgoraCallNotify listener : mListenerList) {
            listener.onReceiveCloudRecordResp(recordChannel, deviceToken, cloudToken, cloudUid);
        }

    }

    /*
     * @brief  从服务器收到告警消息
     * @param timestamp : 接收消息时间戳
     * @param sessionId : 当前固定为0
     * @param deviceUid : 告警设备的Uid
     * @param recordChannel : 告警设备录制的频道号
     * @param recordSid : 告警设备录制的Sid号
     * @param recordToken : 告警设备录制的token
     * @param message : 告警信息
     * @return None
     */
    public void onReceiveAlaramMessage(long timestamp, long sessionId, long deviceUid, String recordChannel,
                                        String recordSid, String recordToken, String message) {

        // 查询设备account
        UidInfoBean info = com.agora.agoracallkit.utils.HttpRequestInterface.getInstance().queryAccountWithUid(deviceUid);
        if (info == null) {
            ALog.getInstance().e(TAG, "<onReceiveAlaramMessage> cannot found deviceUid=" + deviceUid);
            return;
        }

        for (AgoraCallNotify listener : mListenerList) {
            AlarmMessage alarmMessage = new AlarmMessage(timestamp, info,
                    recordChannel, recordSid, recordToken, message);
            listener.onReceiveAlarmMessage(alarmMessage);
        }
    }


    /*
     * @brief  从服务器收到告警消息，针对Apical项目新增的回调
     * @param alarmMsg : 接收到的告警消息
     * @return None
     */
    public void onReceiveIotAlarm(IotAlarm iotAlarm) {
        for (AgoraCallNotify listener : mListenerList) {
            listener.onReceiveIotAlarm(iotAlarm);
        }
    }
}
