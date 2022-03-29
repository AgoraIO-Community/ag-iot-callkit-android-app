package com.agora.agoracallkit.utils;

import android.content.Context;
import android.content.Intent;
import android.location.GnssAntennaInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.ArraySet;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.agora.agoracallkit.beans.RtmServerInfoBean;
import com.agora.agoracallkit.beans.UidInfoBean;
import com.agora.agoracallkit.callkit.CallKitAccount;
import com.agora.agoracallkit.logger.ALog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import io.agora.rtm.ErrorInfo;
import io.agora.rtm.ResultCallback;
import io.agora.rtm.RtmClient;
import io.agora.rtm.RtmClientListener;
import io.agora.rtm.RtmFileMessage;
import io.agora.rtm.RtmImageMessage;
import io.agora.rtm.RtmMediaOperationProgress;
import io.agora.rtm.RtmMessage;
import io.agora.rtm.RtmStatusCode;
import io.agora.rtm.SendMessageOptions;

public class AgoraRtmManager {
    private static final int QUERY_WAIT_TIMEOUT = 10000;

    public static final String CALL_KISS_CHOICE_BUSY = "busy";
    public static final String CALL_KISS_CHOICE_ANSER = "answer";
    public static final String CALL_KISS_CHOICE_HANGUP = "hangup";
    public static final String CALL_KISS_CHOICE_TIMEOUT = "timeout";

    private static final String TAG = "CALLKIT/AgoraRtmManager";
    private static AgoraRtmManager instance;

    private RtmClient mRtmClient;                //RTM Client SDK
    private SendMessageOptions mSendMsgOptions;  //RTM消息配置
    private boolean mIsInited = false;
    private String mAppid = "";

    private int[] mRtmPortSequence;
    private int mUseRtmNumber = 0;
    private RtmServerInfoBean mRtmServeInfo = new RtmServerInfoBean();

    public static AgoraRtmManager getInstance() {
        if(instance == null) {
            synchronized (AgoraRtmManager.class) {
                if(instance == null) {
                    instance = new AgoraRtmManager();
                }
            }
        }
        return instance;
    }

    public void init(Context context, Bundle metaData) {
        if (mIsInited) {
            ALog.getInstance().e(TAG, "<init> already initialized");
            return;
        }
        mRtmServeInfo.setNodeCount(0);
        mRtmServeInfo.setBaseName("");
        mUseRtmNumber = 0;
        mAppid = metaData.getString ("AGORA_APPID", "");
        try {
            //创建RTM Client
            mRtmClient = RtmClient.createInstance(context, mAppid, new RtmClientListener() {
                @Override
                public void onConnectionStateChanged(int state, int reason) {   //连接状态改变
                    ALog.getInstance().d(TAG, "<init.onConnectionStateChanged> state" + state
                            + ", reason: " + reason);
                    if (RtmStatusCode.ConnectionState.CONNECTION_STATE_ABORTED == state) {
                        //用户在其他设备登录
                        if (RtmStatusCode.ConnectionChangeReason.CONNECTION_CHANGE_REASON_REMOTE_LOGIN == reason) {
                            com.agora.agoracallkit.utils.CallbackManager.getInstance().onUserLoginOnOtherDevice();
                        }
                    }
                }

                @Override
                public void onMessageReceived(RtmMessage rtmMessage, String peerId) {   //收到RTM消息
                    String strMessage = new String(rtmMessage.getRawMessage(), StandardCharsets.UTF_8);
                    ALog.getInstance().d(TAG, "<init.onConnectionStateChanged> from=" + peerId
                                + ", message=" + strMessage);
                    try {
                        JSONObject message = new JSONObject(strMessage);
                        String type = message.getString("type");
                        if (type.equals("call_resp")) {
                            //主叫服务器分配channel核token信息通知
                            onCallResp(message, peerId);
                        } else if (type.equals("call_notice")) {
                            //被叫通知，作为device登录时候才会收到
                            onCallNotice(message, peerId);
                        } else if (type.equals("kiss_choice")) {
                            //收到对端发送的接听、挂断等通知
                            onKissChoice(message, peerId);
                        } else if (type.equals("customize")) {
                            //收到对端发送的定制的消息
                            onCustomize(message, peerId);
                        } else if (type.equals("record_resp")) {
                            // 收到服务器回应的录制信息
                            onRecordResp(message, peerId);
                        } else {
                            ALog.getInstance().e(TAG, "<init.onConnectionStateChanged> Unknown RTM message type: " + type);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onImageMessageReceivedFromPeer(final RtmImageMessage rtmImageMessage, final String peerId) {    //收到图像RTM消息
                }

                @Override
                public void onFileMessageReceivedFromPeer(RtmFileMessage rtmFileMessage, String s) {    //收到文件RTM消息
                }

                @Override
                public void onMediaUploadingProgress(RtmMediaOperationProgress rtmMediaOperationProgress, long l) {
                }

                @Override
                public void onMediaDownloadingProgress(RtmMediaOperationProgress rtmMediaOperationProgress, long l) {
                }

                @Override
                public void onTokenExpired() {  //token过期，需要刷新RTM token
                }

                @Override
                public void onPeersOnlineStatusChanged(Map<String, Integer> status) {   //对端用户在线状态改变
                }
            });
            String log_file_path = Environment.getExternalStorageDirectory()
                    + File.separator + "agorartm.log";
            mRtmClient.setLogFile(log_file_path);
            mRtmClient.setLogFileSize(1024 * 2);
            mRtmClient.setLogFilter(RtmClient.LOG_FILTER_OFF);
        } catch (Exception e) {
            ALog.getInstance().e(TAG, Log.getStackTraceString(e));
            throw new RuntimeException("NEED TO check rtm sdk init fatal error\n" + Log.getStackTraceString(e));
        }

        // Global option, mainly used to determine whether
        // to support offline messages now.
        mSendMsgOptions = new SendMessageOptions();

        //仅初始化一次
        mIsInited = true;
    }

    //处理主叫服务器分配channel核token信息通知
    private void onCallResp(JSONObject message, String peerId) {
        try {
            JSONObject header = message.getJSONObject("header");
            long session = header.getLong("session_id");
            if (session > 0) {
                JSONObject body = message.getJSONObject("body");
                String channel = body.getString("channel");
                String token = body.getString("token");
                com.agora.agoracallkit.utils.CallbackManager.getInstance().onReceiveCallParams(
                        session, channel, token);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    //处理被叫通知，作为device登录时候才会收到
    private void onCallNotice(JSONObject message, String peerId) {
        try {
            JSONObject header = message.getJSONObject("header");
            long session = header.getLong("session_id");
            if (session > 0) {
                JSONObject body = message.getJSONObject("body");
                String channel = body.getString("channel");
                String token = body.getString("token");
                long caller = body.getLong("caller");
                long listener = body.getLong("listener");
                String attachMsg = "";
                if (!body.isNull("attach_msg")) {
                    attachMsg = body.getString("attach_msg");
                }

                com.agora.agoracallkit.utils.CallbackManager.getInstance().onReceiveCallMessage(
                        session, caller, listener, channel, token, attachMsg);
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    //处理收到对端发送的接听、挂断等通知
    private void onKissChoice(JSONObject message, String peerId) {
        try {
            JSONObject header = message.getJSONObject("header");
            long session = header.getLong("session_id");
            if (session > 0) {
                JSONObject body = message.getJSONObject("body");
                String operation = body.getString("operation");
                com.agora.agoracallkit.utils.CallbackManager.getInstance().onReceiveCallKissChoice(
                        session, Long.valueOf(peerId), operation);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    // 处理接收到对端的定制化消息
    private  void onCustomize(JSONObject message, String peerId) {
        try {
            JSONObject header = message.getJSONObject("header");
            long session = header.getLong("session_id");
            JSONObject body = message.getJSONObject("body");
            String customizeMsg = body.getString("message");
            com.agora.agoracallkit.utils.CallbackManager.getInstance().onReceiveCustomizeMsg(
                    session, customizeMsg);

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    // 处理服务器回应的云录制信息
    private void onRecordResp(JSONObject message, String peerId)
    {
        try {
            JSONObject header = message.getJSONObject("header");
            long session = header.getLong("session_id");  // 固定为0

            JSONObject body = message.getJSONObject("body");
            String recordChannel = body.getString("channel");       // 云录制使用的频道号
            String deviceToken = body.getString("device_token");    // 设备端录制token
            String cloudToken = body.getString("cloud_token");      // 服务器端token
            long cloudUid = body.getLong("cloud_uid");          // 服务端云录制Uid

            com.agora.agoracallkit.utils.CallbackManager.getInstance().onReceiveCloudRecordResp(
                    recordChannel, deviceToken, cloudToken, cloudUid);

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public boolean login(String userId) {
        if (!mIsInited) {
            ALog.getInstance().e(TAG, "<login> RTM client was not inited.");
            return false;
        }
        //获取业务服务RTM接口数量
        RtmServerInfoBean info = com.agora.agoracallkit.utils.HttpRequestInterface.getInstance().getAgoraServerRtmPortInfo();
        if (info == null) {
            ALog.getInstance().e(TAG, "<login> cannot link with Agora server.");
            return false;
        }
        mRtmServeInfo.updateInfo(info);
        ALog.getInstance().d(TAG, "<login> Got Agora server RTM port count: " + info.getNodeCount());
        //创建随机乱序接口，RTM通信实现负载均衡
        createRtmPortSequence(info.getNodeCount());
        mUseRtmNumber = 0;
        //获取RTM Token
        String token = HttpRequestInterface.getInstance().getRtmToken(userId);
        if (token == null) {
            ALog.getInstance().e(TAG, "<login> cannot get RTM token.");
            return false;
        }
        //登录RTM账号
        mRtmClient.login(token, userId, new ResultCallback<Void>() {
            @Override
            public void onSuccess(Void responseInfo) {
                ALog.getInstance().i(TAG, "<login> login success");
                com.agora.agoracallkit.utils.CallbackManager.getInstance().onRtmState(com.agora.agoracallkit.utils.CallbackManager.RTM_LOGIN_SUCCESS,
                        "");
            }

            @Override
            public void onFailure(ErrorInfo errorInfo) {
                ALog.getInstance().i(TAG, "<login> login failed: " + errorInfo.getErrorCode()
                        + ", " + errorInfo.getErrorDescription());
                //用户已登录
                if (errorInfo.getErrorCode() == RtmStatusCode.LoginError.LOGIN_ERR_ALREADY_LOGIN) {
                    Log.i(TAG, errorInfo.getErrorDescription());
                    com.agora.agoracallkit.utils.CallbackManager.getInstance().onRtmState(com.agora.agoracallkit.utils.CallbackManager.RTM_LOGIN_SUCCESS,
                            "");
                } else {
                    com.agora.agoracallkit.utils.CallbackManager.getInstance().onRtmState(com.agora.agoracallkit.utils.CallbackManager.RTM_LOGIN_FAILED,
                            String.valueOf(errorInfo.getErrorCode()));
                }
            }
        });
        return true;
    }

    public void logout() {
        if (mIsInited) {
            mRtmClient.logout(null);
            ALog.getInstance().i(TAG, "<login> logout finished");
            com.agora.agoracallkit.utils.CallbackManager.getInstance().onRtmState(com.agora.agoracallkit.utils.CallbackManager.RTM_LOGOUT_FINISHED,
                    "");
        }
    }

    private void createRtmPortSequence(int rtmCount) {
        mRtmPortSequence = new int[rtmCount];
        for(int i = 0; i < rtmCount; i++){
            mRtmPortSequence[i] = i;
        }
        Random random = new Random(System.currentTimeMillis());
        for(int i = 0; i < rtmCount; i++){
            int p = random.nextInt(rtmCount);
            int tmp = mRtmPortSequence[i];
            mRtmPortSequence[i] = mRtmPortSequence[p];
            mRtmPortSequence[p] = tmp;
        }
    }

    private boolean sendMessageToServer(JSONObject message) {
        //获取端口数量失败则无法连接服务器
        if (mRtmServeInfo.getNodeCount() <= 0) {
            ALog.getInstance().e(TAG, "<sendMessageToServer> cannot link with Agora server.");
            return false;
        }
        //随机获取一个连接端口
        mUseRtmNumber = (mUseRtmNumber + 1) % mRtmServeInfo.getNodeCount();
        String serverName = mRtmServeInfo.getBaseName() + mRtmPortSequence[mUseRtmNumber];
        RtmMessage rtmMsg = mRtmClient.createMessage();;
        rtmMsg.setRawMessage(String.valueOf(message).getBytes());
        sendPeerMessage(serverName, rtmMsg);
        return true;
    }

    private boolean sendMessageToPeer(long peerUid, JSONObject message) {
        RtmMessage rtmMsg = mRtmClient.createMessage();;
        rtmMsg.setRawMessage(String.valueOf(message).getBytes());
        sendPeerMessage(String.valueOf(peerUid), rtmMsg);
        return true;
    }

    private synchronized void sendPeerMessage(String peerId, final RtmMessage message) {
        ALog.getInstance().d(TAG, "<sendPeerMessage> send message: "
                + new String(message.getRawMessage(), StandardCharsets.UTF_8) + " to " + peerId);
        mRtmClient.sendMessageToPeer(peerId, message, mSendMsgOptions, new ResultCallback<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                // do nothing
            }

            @Override
            public void onFailure(ErrorInfo errorInfo) {
                final int errorCode = errorInfo.getErrorCode();
                switch (errorCode) {
                    case RtmStatusCode.PeerMessageError.PEER_MESSAGE_ERR_TIMEOUT:
                        break;
                    case RtmStatusCode.PeerMessageError.PEER_MESSAGE_ERR_FAILURE:
                        break;
                    case RtmStatusCode.PeerMessageError.PEER_MESSAGE_ERR_PEER_UNREACHABLE:
                        break;
                    case RtmStatusCode.PeerMessageError.PEER_MESSAGE_ERR_CACHED_BY_SERVER:
                        break;
                }
            }
        });
    }

    //发送呼叫通知
    public boolean sendNewCallMessage(List<UidInfoBean> uidBeans, String attachMessage) {
        //创建一个新的呼叫会话
        long sessionId = com.agora.agoracallkit.utils.CallStateManager.getInstance().createNewCallSession(uidBeans);
        if (sessionId <= 0) {
            ALog.getInstance().e(TAG, "<sendNewCallMessage> cannot create new call, maybe calling or UID is failed.");
            return false;
        }
        try {
            //发送呼叫请求给服务器
            JSONObject message = new JSONObject();
            message.put("type", "call_request");
            JSONObject header = new JSONObject();
            header.put("session_id", sessionId);
            message.put("header", header);
            JSONObject body = new JSONObject();
            body.put("caller_uid", com.agora.agoracallkit.utils.CallStateManager.getInstance().getLocalUser().getUid());
            body.put("app_id", mAppid);
            body.put("attach_msg", attachMessage);
            JSONArray listeners = new JSONArray();
            for (int i = 0; i < uidBeans.size(); i++) {
                listeners.put(uidBeans.get(i).getUid());
            }
            body.put("listener_uid", listeners);
            message.put("body", body);
            return sendMessageToServer(message);
        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }
    }

    //发送呼叫应答选择消息给呼叫方
    public boolean sendCallKissChoiceMessage(long sessionId, long caller, String choice) {
        ALog.getInstance().d(TAG, "<sendCallKissChoiceMessage> sessionId=" + sessionId
                + " , caller=" + caller);
        try {
            //如果在忙状态，通知呼叫放忙
            JSONObject message = new JSONObject();
            message.put("type", "kiss_choice");
            JSONObject header = new JSONObject();
            header.put("session_id", sessionId);
            message.put("header", header);
            JSONObject body = new JSONObject();
            body.put("operation", choice);
            message.put("body", body);
            return sendMessageToPeer(caller, message);
        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }
    }


    // 发送应用层定制消息给对端
    public boolean sendCustomizeMessage( long peer_uid, String strMsg) {
        ALog.getInstance().d(TAG, "<sendCustomizeMessage> peer_uid=" + peer_uid + ", strMsg=" + strMsg);
        try {

            JSONObject message = new JSONObject();
            message.put("type", "customize");
            JSONObject header = new JSONObject();
            header.put("session_id", 0);  // 不用管sseionId，固定为0
            message.put("header", header);
            JSONObject body = new JSONObject();
            body.put("message", strMsg);
            message.put("body", body);
            return sendMessageToPeer(peer_uid, message);
        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }
    }

    // 查询对端账号在线状态
    private final Object mQueryPeerDoneEvent = new Object();
    public boolean queryPeerStatus(ArrayList<CallKitAccount> peerList) {
        if (peerList == null || peerList.size() <= 0) {
            return false;
        }

        LinkedHashSet peerSet = new LinkedHashSet<>();
        for (CallKitAccount peerAccount : peerList) {
            long uid = peerAccount.getUid();
            peerSet.add(String.valueOf(uid));
        }

        mRtmClient.queryPeersOnlineStatus(peerSet, new ResultCallback<Map<String, Boolean>>() {
            @Override
            public void onSuccess(Map<String, Boolean> status) {
                Iterator<Map.Entry<String, Boolean>> itor = status.entrySet().iterator();
                while (itor.hasNext()) {
                    Map.Entry<String, Boolean> entry = itor.next();

                    for (CallKitAccount peerAccount : peerList) {
                        if (peerAccount.getUid() == Integer.valueOf(entry.getKey())) {
                            peerAccount.setOnline(entry.getValue());
                            ALog.getInstance().d(TAG, "<queryPeerStatus.onSuccess> name=" + peerAccount.getName()
                                    + ", uid=" + peerAccount.getUid() + ", online=" + entry.getValue());
                            break;
                        }
                    }
                }
                synchronized (mQueryPeerDoneEvent) {
                    mQueryPeerDoneEvent.notify();    // 事件通知
                }
            }

            @Override
            public void onFailure(ErrorInfo errorInfo) {
                ALog.getInstance().e(TAG, "<queryPeerStatus.onFailure> error=" + errorInfo.toString());
                synchronized (mQueryPeerDoneEvent) {
                    mQueryPeerDoneEvent.notify();    // 事件通知
                }
            }
        });

        synchronized (mQueryPeerDoneEvent) {
            try {
                mQueryPeerDoneEvent.wait(QUERY_WAIT_TIMEOUT);
            } catch (InterruptedException e) {
                e.printStackTrace();
                ALog.getInstance().e(TAG, "<queryPeerStatus> exception=" + e.getMessage());
            }
        }
        ALog.getInstance().d(TAG, "<queryPeerStatus> done");
        return true;
    }

    /*
     * @brief 发送设备端告警云录请求给服务器
     */
    public boolean sendCloudRecordingRequest(long deviceUid) {
        ALog.getInstance().d(TAG, "<sendCloudRecordingRequest> deviceUid=" + deviceUid);
        try {
            JSONObject message = new JSONObject();
            message.put("type", "record_request");

            JSONObject header = new JSONObject();
            header.put("session_id", 0);  // 不用管sseionId，固定为0
            message.put("header", header);

            JSONObject body = new JSONObject();
            body.put("device_uid", deviceUid);
            body.put("app_id", mAppid);
            message.put("body", body);

            return sendMessageToServer(message);

        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }
    }

    /*
     * @brief 发送告警消息到服务器
     */
    public boolean sendAlarmRequest(long deviceUid, String recordChannel, String recordSid,
                                    List<Long> listenerList,
                                    String warnMesage) {
        ALog.getInstance().d(TAG, "<sendAlarmRequest> deviceUid=" + deviceUid + ", recordChannel=" + recordChannel
                + ", recordSid=" +recordSid + ", warnMesage=" + warnMesage);
        if (listenerList.size() <= 0) {
            ALog.getInstance().e(TAG, "<sendAlarmRequest> no listeners");
            return false;
        }

        try {
            JSONObject message = new JSONObject();
            message.put("type", "warning_request");

            JSONObject header = new JSONObject();
            header.put("session_id", 0);  // 不用管sseionId，固定为0
            message.put("header", header);

            JSONObject body = new JSONObject();
            body.put("device_uid", deviceUid);

            JSONArray listenerObj = new JSONArray();
            for (int i = 0; i < listenerList.size(); i++) {
                listenerObj.put(listenerList.get(i));
            }
            body.putOpt("listener_uid", listenerObj);

            body.put("record_channel", recordChannel);
            body.put("record_sid", recordSid);
            body.put("app_id", mAppid);
            body.put("attach_msg", warnMesage);
            message.put("body", body);

            return sendMessageToServer(message);

        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }
    }
}
