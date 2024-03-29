/**
 * @file AccountMgr.java
 * @brief This file implement the call kit and RTC management
 * @author xiaohua.lu
 * @email luxiaohua@agora.io
 * @version 1.0.0.1
 * @date 2022-01-26
 * @license Copyright (C) 2021 AgoraIO Inc. All rights reserved.
 */
package io.agora.iotcallkit.sdkimpl;


import android.content.Intent;
import android.graphics.Bitmap;
import android.icu.util.Calendar;
import android.os.Handler;
import android.os.Message;
import android.view.SurfaceView;

import io.agora.iotcallkit.ErrCode;
import io.agora.iotcallkit.IAgoraCallkitSdk;
import io.agora.iotcallkit.ICallkitMgr;
import io.agora.iotcallkit.callkit.AgoraService;
import io.agora.iotcallkit.callkit.CallkitContext;
import io.agora.iotcallkit.logger.ALog;
import io.agora.iotcallkit.rtcsdk.TalkingEngine;
import com.amazonaws.util.Base32;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;


import io.agora.rtc2.Constants;

/*
 * @brief 呼叫系统管理器
 */
public class CallkitMgr implements ICallkitMgr, TalkingEngine.ICallback {


    ////////////////////////////////////////////////////////////////////////
    //////////////////////// Constant Definition ///////////////////////////
    ////////////////////////////////////////////////////////////////////////
    private static final String TAG = "IOTSDK/CallkitMgr";
    private static final long AWS_EVENT_TIMEOUT = 35000;        ///< HTTP请求后，AWS事件超时35秒

    //
    // Request Id
    //
    private static final int HTTP_REQID_DIAL = 1;               ///< 主动呼叫
    private static final int HTTP_REQID_ANSWER = 2;             ///< 应答


    //
    // The mesage Id
    //
    private static final int MSGID_CALL_BASE = 0x3000;
    private static final int MSGID_CALL_PROCESS_AWSEVENT = 0x3001;  ///< 处理AWS端的事件
    private static final int MSGID_CALL_REQ_DIAL = 0x3002;          ///< 发送拨号请求
    private static final int MSGID_CALL_REQ_ANSWER = 0x3003;        ///< 发送应答请求
    private static final int MSGID_CALL_REQ_HANGUP = 0x3004;        ///< 发送挂断请求
    private static final int MSGID_CALL_RTC_PEER_ONLINE = 0x3005;   ///< 对端RTC上线
    private static final int MSGID_CALL_RTC_PEER_OFFLINE = 0x3006;  ///< 对端RTC掉线
    private static final int MSGID_CALL_RTC_PEER_FIRSTVIDEO = 0x3007;  ///< 对端RTC首帧出图
    private static final int MSGID_CALL_AWSEVENT_TIMEOUT = 0x3008;  ///< HTTP请求后, AWS超时无响应

    //
    // Reason code
    //
    private static final int REASON_NONE = 0;            ///< 没有reason字段
    private static final int REASON_LOCAL_HANGUP = 1;    ///< 本地挂断
    private static final int REASON_LOCAL_ANSWER = 2;    ///< 本地应答
    private static final int REASON_PEER_HANGUP = 3;     ///< 对端挂断
    private static final int REASON_PEER_ANSWER = 4;     ///< 对端应答
    private static final int REASON_CALL_TIMEOUT = 5;    ///< 呼叫超时



    ////////////////////////////////////////////////////////////////////////
    //////////////////////// Variable Definition ///////////////////////////
    ////////////////////////////////////////////////////////////////////////
    private ArrayList<ICallkitMgr.ICallback> mCallbackList = new ArrayList<>();
    private AgoraCallkitSdk mSdkInstance;                        ///< 由外部输入的
    private Handler mWorkHandler;                               ///< 工作线程Handler，从SDK获取到

    private static final Object mDataLock = new Object();       ///< 同步访问锁,类中所有变量需要进行加锁处理
    private final Object mReqDialEvent = new Object();
    private final Object mReqHangupEvent = new Object();
    private final Object mReqAnswerEvent = new Object();

    private volatile int mStateMachine = CALLKIT_STATE_IDLE;    ///< 当前呼叫状态机
    private String mAppId;
    private CallkitContext mCallkitCtx;             ///< 当前呼叫的上下文数据
    private String mPeerAccountId;                  ///< 通信的对端账号Id

    private TalkingEngine mTalkEngine;              ///< 通话引擎
    private SurfaceView mPeerVidew;                 ///< 对端视频帧显示控件



    ///////////////////////////////////////////////////////////////////////
    ////////////////////////// Public Methods  ////////////////////////////
    ///////////////////////////////////////////////////////////////////////
    int initialize(AgoraCallkitSdk sdkInstance) {
        mSdkInstance = sdkInstance;
        mWorkHandler = sdkInstance.getWorkHandler();
        mStateMachine = CALLKIT_STATE_IDLE;

        IAgoraCallkitSdk.InitParam sdkInitParam = sdkInstance.getInitParam();
        mAppId = sdkInitParam.mRtcAppId;

        // 初始化通话引擎
        mTalkEngine = new TalkingEngine();
        TalkingEngine.InitParam talkInitParam = mTalkEngine.new InitParam();
        talkInitParam.mContext = sdkInitParam.mContext;
        talkInitParam.mAppId = sdkInitParam.mRtcAppId;
        talkInitParam.mCallback = this;
        talkInitParam.mPublishVideo = sdkInitParam.mPublishVideo;
        talkInitParam.mPublishAudio = sdkInitParam.mPublishAudio;
        talkInitParam.mSubscribeAudio = sdkInitParam.mSubscribeAudio;
        talkInitParam.mSubscribeVideo = sdkInitParam.mSubscribeVideo;
        boolean ret = mTalkEngine.initialize(talkInitParam);
        if (!ret) {
            ALog.getInstance().d(TAG, "<initialize> fail to create talking engine");
            mTalkEngine = null;
            return ErrCode.XERR_CALLKIT_BASE;
        }

        return ErrCode.XOK;
    }

    void release() {
        workThreadClearMessage();

        synchronized (mCallbackList) {
            mCallbackList.clear();
        }

        // 销毁通话引擎
        if (mTalkEngine != null) {
            mTalkEngine.release();
            mTalkEngine = null;
            ALog.getInstance().d(TAG, "<release> done");
        }
    }


    /*
     * @brief 在AWS事件中被调用，对APP端的控制事件
     */
    void onAwsUpdateClient(JSONObject jsonState) {
        //ALog.getInstance().d(TAG, "<onAwsUpdateClient> jsonState=" + jsonState.toString());
        if (mWorkHandler != null) {
            Message msg = new Message();
            msg.what = MSGID_CALL_PROCESS_AWSEVENT;
            msg.obj = jsonState;
            mWorkHandler.sendMessage(msg);   // 所有事件都不要遗漏，全部发送
        }
    }

    void workThreadProcessMessage(Message msg) {
        switch (msg.what) {
            case MSGID_CALL_PROCESS_AWSEVENT:
                DoAwsEventProcess(msg);
                break;

            case MSGID_CALL_REQ_DIAL:
                DoRequestDial(msg);
                break;

            case MSGID_CALL_REQ_ANSWER:
                DoRequestAnswer(msg);
                break;

            case MSGID_CALL_REQ_HANGUP:
                DoRequestHangup(msg);
                break;

            case MSGID_CALL_AWSEVENT_TIMEOUT:
                DoAwsEventTimeout(msg);
                break;

            case MSGID_CALL_RTC_PEER_ONLINE:
                DoRtcPeerOnline(msg);
                break;

            case MSGID_CALL_RTC_PEER_OFFLINE:
                DoRtcPeerOffline(msg);
                break;

            case MSGID_CALL_RTC_PEER_FIRSTVIDEO:
                DoRtcPeerFirstVideo(msg);
                break;
        }
    }

    void workThreadClearMessage() {
        if (mWorkHandler != null) {
            mWorkHandler.removeMessages(MSGID_CALL_PROCESS_AWSEVENT);
            mWorkHandler.removeMessages(MSGID_CALL_REQ_DIAL);
            mWorkHandler.removeMessages(MSGID_CALL_REQ_ANSWER);
            mWorkHandler.removeMessages(MSGID_CALL_REQ_HANGUP);
            mWorkHandler.removeMessages(MSGID_CALL_RTC_PEER_ONLINE);
            mWorkHandler.removeMessages(MSGID_CALL_RTC_PEER_OFFLINE);
            mWorkHandler.removeMessages(MSGID_CALL_AWSEVENT_TIMEOUT);
            mWorkHandler = null;
        }
    }

    void sendMessage(int what, int arg1, int arg2, Object obj) {
        Message msg = new Message();
        msg.what = what;
        msg.arg1 = arg1;
        msg.arg2 = arg2;
        msg.obj = obj;
        if (mWorkHandler != null) {
            mWorkHandler.removeMessages(what);
            mWorkHandler.sendMessage(msg);
        }
    }

    void sendMessageDelay(int what, int arg1, int arg2, Object obj, long delayTime) {
        Message msg = new Message();
        msg.what = what;
        msg.arg1 = arg1;
        msg.arg2 = arg2;
        msg.obj = obj;
        if (mWorkHandler != null) {
            mWorkHandler.removeMessages(what);
            mWorkHandler.sendMessageDelayed(msg, delayTime);
        }
    }


    ///////////////////////////////////////////////////////////////////////
    /////////////////// Override Methods of ICallkitMgr //////////////////
    ///////////////////////////////////////////////////////////////////////
    @Override
    public int getStateMachine() {
        synchronized (mDataLock) {
            return mStateMachine;
        }
    }

    @Override
    public int registerListener(ICallkitMgr.ICallback callback) {
        synchronized (mCallbackList) {
            mCallbackList.add(callback);
        }
        return ErrCode.XOK;
    }

    @Override
    public int unregisterListener(ICallkitMgr.ICallback callback) {
        synchronized (mCallbackList) {
            mCallbackList.remove(callback);
        }
        return ErrCode.XOK;
    }

    @Override
    public int callDial(final String peerAccountId, final String attachMsg) {
        if (mSdkInstance.getStateMachine() != IAgoraCallkitSdk.SDK_STATE_RUNNING) {
            ALog.getInstance().e(TAG, "<callDial> bad state, sdkState="
                    + mSdkInstance.getStateMachine());
            return ErrCode.XERR_BAD_STATE;
        }
        int currState = getStateMachine();
        if (currState != CALLKIT_STATE_IDLE) {
            ALog.getInstance().e(TAG, "<callDial> bad state, currState=" + currState);
            return ErrCode.XERR_BAD_STATE;
        }

        // 发送请求消息
        setStateMachine(CALLKIT_STATE_DIAL_REQING);  // 呼叫请求中
        Object callParams = new Object[] {peerAccountId, attachMsg};
        sendMessage(MSGID_CALL_REQ_DIAL, 0, 0, callParams);

        ALog.getInstance().d(TAG, "<callDial> done, peerAccountId=" + peerAccountId
                + ", attachMsg=" + attachMsg);
        return ErrCode.XOK;
    }

    @Override
    public int callHangup() {
        if (mSdkInstance.getStateMachine() != IAgoraCallkitSdk.SDK_STATE_RUNNING) {
            ALog.getInstance().e(TAG, "<callHangup> bad state, sdkState="
                    + mSdkInstance.getStateMachine());
            return ErrCode.XERR_BAD_STATE;
        }
        int currState = getStateMachine();
        if ((currState != CALLKIT_STATE_DIALING) &&
            (currState != CALLKIT_STATE_TALKING) &&
            (currState != CALLKIT_STATE_INCOMING)) {
            ALog.getInstance().e(TAG, "<callHangup> bad state, currState=" + currState);
            return ErrCode.XERR_BAD_STATE;
        }
        synchronized (mDataLock) {
            if (mCallkitCtx == null) {
                ALog.getInstance().e(TAG, "<callHangup> bad state, mCallkitCtx is NULL");
                return ErrCode.XERR_BAD_STATE;
            }
        }

        // 发送请求消息，同步等待执行完成
        setStateMachine(CALLKIT_STATE_HANGUP_REQING);  // 挂断请求中
        sendMessage(MSGID_CALL_REQ_HANGUP, 0, 0, null);

        ALog.getInstance().d(TAG, "<callHangup> done");
        return ErrCode.XOK;
    }

    @Override
    public int callAnswer() {
        if (mSdkInstance.getStateMachine() != IAgoraCallkitSdk.SDK_STATE_RUNNING) {
            ALog.getInstance().e(TAG, "<callAnswer> bad state, sdkState="
                    + mSdkInstance.getStateMachine());
            return ErrCode.XERR_BAD_STATE;
        }
        int currState = getStateMachine();
        if (currState != CALLKIT_STATE_INCOMING) {
            ALog.getInstance().e(TAG, "<callAnswer> bad state, currState=" + currState);
            return ErrCode.XERR_BAD_STATE;
        }
        synchronized (mDataLock) {
            if (mCallkitCtx == null) {
                ALog.getInstance().e(TAG, "<callAnswer> bad state, mCallkitCtx is NULL");
                return ErrCode.XERR_BAD_STATE;
            }
        }

        // 发送请求消息，同步等待执行完成
        setStateMachine(CALLKIT_STATE_ANSWER_REQING);  // 应答请求中
        sendMessage(MSGID_CALL_REQ_ANSWER, 0, 0, null);

        ALog.getInstance().d(TAG, "<callAnswer> done");
        return ErrCode.XOK;
    }


    @Override
    public int setLocalVideoView(final SurfaceView localView) {
        return ErrCode.XOK;
    }

    @Override
    public int setPeerVideoView(final SurfaceView peerView) {
        mPeerVidew = peerView;
        mTalkEngine.setRemoteVideoView(mPeerVidew);
        return ErrCode.XOK;
    }

    @Override
    public int muteLocalVideo(boolean mute) {
        if (mTalkEngine == null) {
            return ErrCode.XERR_BAD_STATE;
        }

        boolean ret = mTalkEngine.muteLocalVideoStream(mute);
        return (ret ? ErrCode.XOK : ErrCode.XERR_UNSUPPORTED);
    }

    @Override
    public int muteLocalAudio(boolean mute) {
        if (mTalkEngine == null) {
            return ErrCode.XERR_BAD_STATE;
        }

        boolean ret = mTalkEngine.muteLocalAudioStream(mute);
        return (ret ? ErrCode.XOK : ErrCode.XERR_UNSUPPORTED);
    }

    @Override
    public int mutePeerVideo(boolean mute) {
        if (mTalkEngine == null) {
            return ErrCode.XERR_BAD_STATE;
        }

        boolean ret = mTalkEngine.mutePeerVideoStream(mute);
        return (ret ? ErrCode.XOK : ErrCode.XERR_UNSUPPORTED);
    }

    @Override
    public int mutePeerAudio(boolean mute) {
        if (mTalkEngine == null) {
            return ErrCode.XERR_BAD_STATE;
        }

        boolean ret = mTalkEngine.mutePeerAudioStream(mute);
        return (ret ? ErrCode.XOK : ErrCode.XERR_UNSUPPORTED);
    }

    @Override
    public int setVolume(int volumeLevel) {
        return ErrCode.XERR_UNSUPPORTED;
    }

    @Override
    public int setAudioEffect(final AudioEffectId effectId) {
        if (mTalkEngine == null) {
            ALog.getInstance().e(TAG, "<setAudioEffect> bad status");
            return ErrCode.XERR_BAD_STATE;
        }

        int voice_changer = Constants.AUDIO_EFFECT_OFF;
        switch (effectId) {
            case OLDMAN:
                voice_changer = Constants.VOICE_CHANGER_EFFECT_OLDMAN;
                break;

            case BABYBOY:
                voice_changer = Constants.VOICE_CHANGER_EFFECT_BOY;
                break;

            case BABYGIRL:
                voice_changer = Constants.VOICE_CHANGER_EFFECT_GIRL;
                break;

            case ZHUBAJIE:
                voice_changer = Constants.VOICE_CHANGER_EFFECT_PIGKING;
                break;

            case ETHEREAL:
                voice_changer = Constants.VOICE_CHANGER_EFFECT_SISTER;
                break;

            case HULK:
                voice_changer = Constants.VOICE_CHANGER_EFFECT_HULK;
                break;
        }

        boolean ret = mTalkEngine.setAudioEffect(voice_changer);
        return (ret ? ErrCode.XOK : ErrCode.XERR_UNSUPPORTED);
    }

    @Override
    public int talkingRecordStart() {
        return ErrCode.XOK;
    }

    @Override
    public int talkingRecordStop() {
        return ErrCode.XOK;
    }

    @Override
    public RtcNetworkStatus getNetworkStatus() {
        if (mTalkEngine == null) {
            ALog.getInstance().e(TAG, "<RtcNetworkStatus> bad status");
            return null;
        }

        RtcNetworkStatus networkStatus = mTalkEngine.getNetworkStatus();
        return networkStatus;
    }

    @Override
    public Bitmap capturePeerVideoFrame() {
        if (mTalkEngine == null) {
            ALog.getInstance().e(TAG, "<capturePeerVideoFrame> bad status");
            return null;
        }

        Bitmap capturedBmp = mTalkEngine.capturePeerVideoFrame();
        return capturedBmp;
    }

    @Override
    public int setRtcPrivateParam(final String privateParam) {
        if (mTalkEngine == null) {
            ALog.getInstance().e(TAG, "<setRtcPrivateParam> bad status");
            return ErrCode.XERR_BAD_STATE;
        }

        int ret = mTalkEngine.setParameters(privateParam);
        return (ret == Constants.ERR_OK) ? ErrCode.XOK : ErrCode.XERR_INVALID_PARAM;
    }



    /////////////////////////////////////////////////////////////////////////////
    /////////////////////////// APP端发送RESTful请求到服务器 //////////////////////
    /////////////////////////////////////////////////////////////////////////////
    /*
     * @brief 工作线程中运行，发送HTTP呼叫请求
     */
    void DoRequestDial(Message msg) {
        Object[] callParams = (Object[]) (msg.obj);
        String peerAccountId = (String)(callParams[0]);
        String attachMsg = (String)(callParams[1]);
        synchronized (mDataLock) {
            mPeerAccountId = peerAccountId;
        }

        AccountMgr.AccountInfo accountInfo = mSdkInstance.getAccountInfo();
        AgoraService.CallReqResult callReqResult = AgoraService.getInstance().makeCall(
                accountInfo.mAgoraAccessToken, mAppId,
                accountInfo.mInventDeviceName,  peerAccountId, attachMsg);

        if (callReqResult.mErrCode != ErrCode.XOK)   {  // 呼叫失败
            ALog.getInstance().d(TAG, "<DoRequestDial> failure, errCode=" + callReqResult.mErrCode);
            exceptionProcess();
            CallbackCallDialDone(callReqResult.mErrCode, peerAccountId); // 回调主叫拨号失败
            return;
        }


        // 更新呼叫上下文数据
        synchronized (mDataLock) {
            mCallkitCtx = callReqResult.mCallkitCtx;
        }

        // 切换到 等待主叫响应状态
        setStateMachine(CALLKIT_STATE_DIAL_RSPING);

        // 进入频道，准备主叫通话
        talkingPrepare(true, callReqResult.mCallkitCtx.channelName,
                callReqResult.mCallkitCtx.rtcToken,
                mCallkitCtx.mLocalUid,
                mCallkitCtx.mPeerUid);

        // 启动AWS Event超时定时器
        sendMessageDelay(MSGID_CALL_AWSEVENT_TIMEOUT, HTTP_REQID_DIAL, 0, null, AWS_EVENT_TIMEOUT);

        ALog.getInstance().d(TAG, "<DoRequestDial> done, mCallkitCtx=" + mCallkitCtx.toString());
    }

    /*
     * @brief 工作线程中运行，发送HTTP挂断请求
     */
    void DoRequestHangup(Message msg) {
        CallkitContext callkitCtx;
        synchronized (mDataLock) {
            callkitCtx = mCallkitCtx;
        }

        int errCode;
        if ((callkitCtx != null) || (callkitCtx.sessionId != null)) {
            // 发送挂断请求
            AccountMgr.AccountInfo accountInfo = mSdkInstance.getAccountInfo();
            errCode = AgoraService.getInstance().makeAnswer(accountInfo.mAgoraAccessToken,
                    callkitCtx.sessionId, callkitCtx.callerId, callkitCtx.calleeId,
                    accountInfo.mInventDeviceName, false);
        } else {
            ALog.getInstance().e(TAG, "<DoRequestAnswer> bad status, callkit is NULL");
            errCode = ErrCode.XERR_BAD_STATE;
        }

        //
        // 不管前面是否异常状态，总是停止所有处理，清零到空闲状态
        //
        mTalkEngine.leaveChannel();     // 离开频道，结束通话
        synchronized (mDataLock) {      // 清除当前呼叫上下文数据，恢复状态
            mStateMachine = CALLKIT_STATE_IDLE;
            mCallkitCtx = null;
            mPeerAccountId = null;
        }
        if (mWorkHandler != null) {   // 取消AWS Event超时定时器
            mWorkHandler.removeMessages(MSGID_CALL_AWSEVENT_TIMEOUT);
        }

        ALog.getInstance().d(TAG, "<DoRequestHangup> done, errCode=" + errCode);
    }


    /*
     * @brief 工作线程中运行，发送HTTP接听请求
     */
    void DoRequestAnswer(Message msg) {
        CallkitContext callkitCtx;
        synchronized (mDataLock) {
            callkitCtx = mCallkitCtx;
        }

        if ((callkitCtx == null) || (callkitCtx.sessionId == null)) { // 异常状态，直接清除，恢复状态
            ALog.getInstance().e(TAG, "<DoRequestAnswer> bad status, callkit is NULL");
            exceptionProcess();
            CallbackError(ErrCode.XERR_INVALID_PARAM);
            return;
        }

        AccountMgr.AccountInfo accountInfo = mSdkInstance.getAccountInfo();
        int errCode = AgoraService.getInstance().makeAnswer(accountInfo.mAgoraAccessToken,
                callkitCtx.sessionId, callkitCtx.callerId, callkitCtx.calleeId,
                accountInfo.mInventDeviceName, true);

        if (errCode != ErrCode.XOK) {  // 接听失败
            ALog.getInstance().d(TAG, "<DoRequestAnswer> failure, errCode=" + errCode);
            exceptionProcess();         // 直接退出频道和挂断处理
            CallbackError(errCode);  // 回调错误
            return;
        }

        // 切换到 等待接听响应状态
        setStateMachine(CALLKIT_STATE_ANSWER_RSPING);

        // 启动AWS Event超时定时器
        sendMessageDelay(MSGID_CALL_AWSEVENT_TIMEOUT, HTTP_REQID_ANSWER, 0, null, AWS_EVENT_TIMEOUT);

        ALog.getInstance().d(TAG, "<DoRequestAnswer> done");
    }

    /*
     * @brief 工作线程中运行，发送HTTP请求（主叫或者接听）后，超时无AWS事件，进行挂断处理
     *        正常情况下，永远不应该进入这个消息处理
     */
    void DoAwsEventTimeout(Message msg) {
        ALog.getInstance().e(TAG, "<DoAwsEventTimeout> done, from=" + msg.arg1);
        switch (msg.arg1) {
            case HTTP_REQID_DIAL: {  // 发送主叫HTTP请求后，超时无AWS事件响应
                String peerAccountId = mPeerAccountId;
                exceptionProcess();
                CallbackCallDialDone(ErrCode.XERR_TIMEOUT, peerAccountId);  // 回调拨号失败
            } break;

            case HTTP_REQID_ANSWER: {  // 发送接听HTTP请求后，超时无AWS事件响应
                exceptionProcess();
                CallbackError(ErrCode.XERR_CALLKIT_ANSWER);  // 回调错误
            } break;
        }
    }



    ///////////////////////////////////////////////////////////////////////
    ///////////////////////////// 处理AWS的事件 ////////////////////////////
    ///////////////////////////////////////////////////////////////////////
    /*
     * @brief 工作线程中运行，处理AWS要求切换到空闲状态事件
     */
    void DoAwsEventToIdle(int reason, JSONObject jsonState) {
        int stateMachine = getStateMachine();
        ALog.getInstance().w(TAG, "<DoAwsEventToIdle> "
                + ", currState=" + getStateMachineTip(stateMachine)
                + ", reason=" + getReasonTip(reason));
        if (stateMachine == CALLKIT_STATE_IDLE || stateMachine == CALLKIT_STATE_HANGUP_REQING) {
            // 当前空闲状态或正在挂断，则立即返回
            return;
        }

        switch (reason)
        {
            case REASON_LOCAL_HANGUP: {  // 本地挂断，不管当前处于什么状态，立即挂断处理
                ALog.getInstance().d(TAG, "<DoAwsEventToIdle> local hangup");
                talkingStop();  // 停止通话，恢复状态机空闲，清除呼叫和对端信息

            } break;

            case REASON_PEER_HANGUP: {  // 对端挂断，不管当前处于什么状态，立即挂断处理
                ALog.getInstance().d(TAG, "<DoAwsEventProcess> peer hangup");
                String callbackAccountId = mPeerAccountId;
                talkingStop();  // 停止通话，恢复状态机空闲，清除呼叫和对端信息
                CallbackPeerHangup(callbackAccountId);    // 回调对端挂断

            } break;

            case REASON_CALL_TIMEOUT: { // 呼叫超时，对端超时无响应，立即挂断处理
                ALog.getInstance().d(TAG, "<DoAwsEventProcess> call timeout during dialing");
                String callbackAccountId = mPeerAccountId;
                talkingStop();  // 停止通话，恢复状态机空闲，清除呼叫和对端信息
                CallbackPeerTimeout(callbackAccountId);   // 回调对端超时
            } break;
        }
    }

    /*
     * @brief 工作线程中运行，处理AWS要求切换到主叫状态事件
     */
    void DoAwsEventToDial(int reason, JSONObject jsonState) {
        int stateMachine = getStateMachine();
        ALog.getInstance().w(TAG, "<DoAwsEventToDial> "
                + ", currState=" + getStateMachineTip(stateMachine)
                + ", reason=" + getReasonTip(reason));

        if (stateMachine != CALLKIT_STATE_DIAL_RSPING) {  // 不是等待呼叫响应，呼叫状态有问题
            ALog.getInstance().e(TAG, "<DoAwsEventToDial> bad state machine, auto hangup");
            exceptionProcess();
            CallbackError(ErrCode.XERR_BAD_STATE);  // 回调状态错误
            return;
        }

        ALog.getInstance().d(TAG, "<DoAwsEventToDial> local dialing success.");
        synchronized (mDataLock) {
            if (mCallkitCtx == null) {
                mCallkitCtx = new CallkitContext();  // 要创建新的呼叫上下文数据
            }
        }
        updateCallContext(jsonState);  // 本地主叫成功，更新上呼叫上下文数据
        String channelName, rtcToken;
        int localUid = 0, peerUid = 0;
        synchronized (mDataLock) {
            mStateMachine = CALLKIT_STATE_DIALING;      // 切换当前状态机
            channelName = mCallkitCtx.channelName;
            rtcToken = mCallkitCtx.rtcToken;
            localUid = mCallkitCtx.mLocalUid;
            peerUid = mCallkitCtx.mPeerUid;
        }

        // 进入频道，准备主叫通话
        talkingPrepare(true, channelName, rtcToken, localUid, peerUid);

        CallbackCallDialDone(ErrCode.XOK, mPeerAccountId); // 回调主叫拨号成功
    }

    /*
     * @brief 工作线程中运行，处理AWS要求切换到被叫状态事件
     */
    void DoAwsEventToIncoming(int reason, JSONObject jsonState) {
        int stateMachine = getStateMachine();
        ALog.getInstance().w(TAG, "<DoAwsEventToIncoming> "
                + ", currState=" + getStateMachineTip(stateMachine)
                + ", reason=" + getReasonTip(reason));

        if (stateMachine != CALLKIT_STATE_IDLE) {    // 不是在空闲状态中来电，呼叫状态有问题
            ALog.getInstance().e(TAG, "<DoAwsEventToIncoming> bad state machine, auto hangup");
            exceptionProcess();
            CallbackError(ErrCode.XERR_BAD_STATE);  // 回调状态错误
            return;
        }

        ALog.getInstance().d(TAG, "<DoAwsEventToIncoming> peer incoming call...");
        updateCallContext(jsonState);  // 更新上呼叫上下文数据

        String channelName, rtcToken;
        int localUid = 0, peerUid = 0;
        synchronized (mDataLock) {
            mStateMachine = CALLKIT_STATE_INCOMING;      // 切换当前状态机到来电
            if (mCallkitCtx.calleeId == null) {   // 如果来电数据没有被呼账号，用本地填充
                AccountMgr.AccountInfo accountInfo = mSdkInstance.getAccountInfo();
                mCallkitCtx.calleeId = accountInfo.mInventDeviceName;
            }

            channelName = mCallkitCtx.channelName;
            rtcToken = mCallkitCtx.rtcToken;
            localUid = mCallkitCtx.mLocalUid;
            peerUid = mCallkitCtx.mPeerUid;

            //  创建一个新对端账号Id
            mPeerAccountId = mCallkitCtx.calleeId;
            ALog.getInstance().e(TAG, "<DoAwsEventToIncoming> cannot found incoming device"
                    + ", callerId=" + mCallkitCtx.callerId);

        }

        // 进入频道，准备被叫通话
        talkingPrepare(false, channelName, rtcToken, localUid, peerUid);

        CallbackPeerIncoming(mPeerAccountId, mCallkitCtx.attachMsg); // 回调对端来电
    }


    /*
     * @brief 工作线程中运行，处理AWS要求切换到通话状态事件
     */
    void DoAwsEventToTalking(int reason, JSONObject jsonState) {
        int stateMachine = getStateMachine();
        ALog.getInstance().w(TAG, "<DoAwsEventToTalking> "
                + ", currState=" + getStateMachineTip(stateMachine)
                + ", reason=" + getReasonTip(reason));

        if ((reason == REASON_PEER_ANSWER) && (stateMachine == CALLKIT_STATE_DIALING)) {
            // 主叫时对端接听
            ALog.getInstance().d(TAG, "<DoAwsEventToTalking> enter talk during dialing");
            talkingStart(); // 在频道内推送音频流，开始通话
            CallbackPeerAnswer(ErrCode.XOK, mPeerAccountId); // 回调对端接听，进入通话状态

        } else if ((reason == REASON_LOCAL_ANSWER) && (stateMachine == CALLKIT_STATE_ANSWER_RSPING)) {
            // 被叫时本地接听
            ALog.getInstance().d(TAG, "<DoAwsEventProcess> enter talk during incoming");
            talkingStart(); // 在频道内推送音频流，开始通话

        } else {
            ALog.getInstance().e(TAG, "<DoAwsEventToTalking>  bad state machine, auto hangup");
            exceptionProcess();
            CallbackError(ErrCode.XERR_BAD_STATE);  // 回调状态错误
        }
    }


    /*
     * brief 工作线程中运行，处理AWS的事件，这里提取到的信息
     *       callStatus: 服务器要求APP端切换到的目标状态，
     */
    void DoAwsEventProcess(Message msg) {
        JSONObject jsonState = (JSONObject)(msg.obj);
        if (!jsonState.has("callStatus")) {
            ALog.getInstance().e(TAG, "<DoAwsEventProcess> no field: callStatus");
            return;
        }
        updateCallContext(jsonState);
        int targetState = parseJsonIntValue(jsonState, "callStatus", -1);
        int reason = parseJsonIntValue(jsonState, "reason", REASON_NONE);
        int stateMachine = getStateMachine();
        ALog.getInstance().d(TAG, "<DoAwsEventProcess> "
                + ", targetState=" + getStateMachineTip(targetState)
                + ", currState=" + getStateMachineTip(stateMachine)
                + ", reason=" + getReasonTip(reason));

        if (mWorkHandler != null) {   // 取消 AWS 超时定时器
            mWorkHandler.removeMessages(MSGID_CALL_AWSEVENT_TIMEOUT);
        }

        switch (targetState) {
            case CALLKIT_STATE_IDLE: {  // 要求APP端切换到空闲状态
                DoAwsEventToIdle(reason, jsonState);
            } break;

            case CALLKIT_STATE_DIALING: {   // 要求APP端切换到主叫状态
                DoAwsEventToDial(reason, jsonState);
            } break;

            case CALLKIT_STATE_INCOMING: {  // 要求APP端切换到被叫状态
                DoAwsEventToIncoming(reason, jsonState);
            } break;

            case CALLKIT_STATE_TALKING: {  // 要求APP端切换到通话状态
                DoAwsEventToTalking(reason, jsonState);
            } break;
        }
    }

    /*
     * @brief 主叫或者被叫时准备通话，本地直接推流，订阅对端音视频流
     */
    void talkingPrepare(boolean dial, final String channelName, final String rtcToken,
                        int localUid, int peerUid) {
        if (!mTalkEngine.isInChannel()) {  // 不在频道内时要加入频道进行处理
            IAgoraCallkitSdk.InitParam initParam = mSdkInstance.getInitParam();
            mTalkEngine.setPeerUid(peerUid);
            mTalkEngine.joinChannel(channelName, rtcToken, localUid);
            if (dial) {  // 主叫时
                mTalkEngine.muteLocalVideoStream(!initParam.mPublishVideo);     // 本地推视频流
                mTalkEngine.muteLocalAudioStream(!initParam.mPublishAudio);     // 本地推音频流

            } else { // 被叫时
                mTalkEngine.muteLocalVideoStream(true);     // 本地不推视频流
                mTalkEngine.muteLocalAudioStream(true);     // 本地不推音频流
            }

        }
    }

    /*
     * @brief 应答对方或者对方应答后，奔溃开始推音频流，通话
     */
    void talkingStart() {
        synchronized (mDataLock) {
            mStateMachine = CALLKIT_STATE_TALKING;  // 切换到 通话状态机
        }

        if (mTalkEngine.isInChannel()) {   // 已经在频道内进行处理
            IAgoraCallkitSdk.InitParam initParam = mSdkInstance.getInitParam();
            mTalkEngine.muteLocalVideoStream(!initParam.mPublishVideo);    // 本地推送视频流
            mTalkEngine.muteLocalAudioStream(!initParam.mPublishAudio);    // 本地推送音频流
        } else {
            ALog.getInstance().e(TAG, "<talkingStart> NOT in a channel");
        }
    }

    /*
     * @brief 停止通话，状态机切换到空闲，清除对端设备和peerUid
     */
    void talkingStop() {
        mTalkEngine.leaveChannel();     // 离开频道，结束通话
        synchronized (mDataLock) {      // 清除当前呼叫上下文数据，恢复状态
            mStateMachine = CALLKIT_STATE_IDLE;
            mCallkitCtx = null;
            mPeerAccountId = null;
        }

        if (mWorkHandler != null) {   // 取消AWS Event超时定时器
            mWorkHandler.removeMessages(MSGID_CALL_AWSEVENT_TIMEOUT);
        }
    }


    /*
     * @brief 异常情况下的处理
     *          主动挂断，停止通话，状态机切换到空闲，清除对端设备和peerUid
     */
    void exceptionProcess() {
        // 直接调用本地挂断请求
        CallkitContext callkitCtx;
        synchronized (mDataLock) {
            callkitCtx = mCallkitCtx;
        }
        if ((callkitCtx != null) && (callkitCtx.sessionId != null)) {
            AccountMgr.AccountInfo accountInfo = mSdkInstance.getAccountInfo();
            AgoraService.getInstance().makeAnswer(accountInfo.mAgoraAccessToken, callkitCtx.sessionId,
                    callkitCtx.callerId, callkitCtx.calleeId, accountInfo.mInventDeviceName, false);
        }

        mTalkEngine.leaveChannel();     // 离开频道，结束通话
        synchronized (mDataLock) {      // 清除当前呼叫上下文数据，恢复状态
            mStateMachine = CALLKIT_STATE_IDLE;
            mCallkitCtx = null;
            mPeerAccountId = null;
        }

        if (mWorkHandler != null) {   // 取消AWS Event超时定时器
            mWorkHandler.removeMessages(MSGID_CALL_AWSEVENT_TIMEOUT);
        }

        ALog.getInstance().d(TAG, "<exceptionProcess> done");
    }

    /*
     * @brief 根据JSON数据更当前 呼叫上下文数据
     */
    void updateCallContext( JSONObject jsonState) {
        CallkitContext newCallkitCtx = new CallkitContext();
        newCallkitCtx.appId = parseJsonStringValue(jsonState,"appId", null);
        newCallkitCtx.channelName = parseJsonStringValue(jsonState,"channelName", null);
        newCallkitCtx.rtcToken = parseJsonStringValue(jsonState,"rtcToken", null);
        newCallkitCtx.uid = parseJsonStringValue(jsonState,"uid", null);
        newCallkitCtx.peerUid = parseJsonStringValue(jsonState,"peerUid", null);
        newCallkitCtx.sessionId = parseJsonStringValue(jsonState,"sessionId", null);
        newCallkitCtx.callerId = parseJsonStringValue(jsonState,"callerId", null);
        newCallkitCtx.calleeId = parseJsonStringValue(jsonState,"calleeId", null);
        newCallkitCtx.attachMsg = parseJsonStringValue(jsonState,"attachMsg", null);
        newCallkitCtx.deviceAlias = parseJsonStringValue(jsonState,"deviceAlias", null);
        newCallkitCtx.cloudRcdStatus = parseJsonIntValue(jsonState,"cloudRcdStatus", -1);
        newCallkitCtx.callStatus = parseJsonIntValue(jsonState,"callStatus", -1);
        newCallkitCtx.reason = parseJsonIntValue(jsonState,"reason", -1);

        synchronized (mDataLock) {  // 更新呼叫上下文数据
            if (mCallkitCtx == null) {
                mCallkitCtx = new CallkitContext();
            }

            if (newCallkitCtx.appId != null) {
                mCallkitCtx.appId = newCallkitCtx.appId;
            }
            if (newCallkitCtx.channelName != null) {
                mCallkitCtx.channelName = newCallkitCtx.channelName;
            }
            if (newCallkitCtx.rtcToken != null) {
                mCallkitCtx.rtcToken = newCallkitCtx.rtcToken;
            }
            if (newCallkitCtx.uid != null) {
                mCallkitCtx.uid = newCallkitCtx.uid;
            }

            if (newCallkitCtx.sessionId != null) {
                mCallkitCtx.sessionId = newCallkitCtx.sessionId;
            }
            if (newCallkitCtx.callerId != null) {
                mCallkitCtx.callerId = newCallkitCtx.callerId;
            }
            if (newCallkitCtx.calleeId != null) {
                mCallkitCtx.calleeId = newCallkitCtx.calleeId;
            }

            if (newCallkitCtx.attachMsg != null) {
                mCallkitCtx.attachMsg = newCallkitCtx.attachMsg;
            }
            if (newCallkitCtx.deviceAlias != null) {
                mCallkitCtx.deviceAlias = newCallkitCtx.deviceAlias;
            }
            if (newCallkitCtx.cloudRcdStatus >= 0) {
                mCallkitCtx.cloudRcdStatus = newCallkitCtx.cloudRcdStatus;
            }

            if (newCallkitCtx.callStatus >= 0) {
                mCallkitCtx.callStatus = newCallkitCtx.callStatus;
            }
            if (newCallkitCtx.reason >= 0) {
                mCallkitCtx.reason = newCallkitCtx.reason;
            }

            if (newCallkitCtx.uid != null && !newCallkitCtx.uid.isEmpty()) {   // 这里的uid是本地localUid
                mCallkitCtx.mLocalUid = Integer.valueOf(newCallkitCtx.uid);
            }
            if (newCallkitCtx.peerUid != null && !newCallkitCtx.peerUid.isEmpty()) {    // 这里的uid是对端的peerUid
                mCallkitCtx.mPeerUid = Integer.valueOf(newCallkitCtx.peerUid);
            }
        }

        ALog.getInstance().d(TAG, "<updateCallContext> mCallkitCtx=" + mCallkitCtx.toString());
    }

    int parseJsonIntValue(JSONObject jsonState, String fieldName, int defVal) {
        try {
            int value = jsonState.getInt(fieldName);
            return value;

        } catch (JSONException e) {
            ALog.getInstance().e(TAG, "<parseJsonIntValue> "
                    + ", fieldName=" + fieldName + ", exp=" + e.toString());
            return defVal;
        }
    }

    String parseJsonStringValue(JSONObject jsonState, String fieldName, String defVal) {
        try {
            String value = jsonState.getString(fieldName);
            return value;

        } catch (JSONException e) {
            ALog.getInstance().e(TAG, "<parseJsonIntValue> "
                    + ", fieldName=" + fieldName + ", exp=" + e.toString());
            return defVal;
        }
    }


    /////////////////////////////////////////////////////////////////////////////
    //////////////////// TalkingEngine.ICallback 回调处理 ////////////////////////
    /////////////////////////////////////////////////////////////////////////////
    @Override
    public void onTalkingPeerJoined(int localUid, int peerUid) {
        int stateMachine = getStateMachine();
        ALog.getInstance().d(TAG, "<onTalkingPeerJoined> localUid=" + localUid
                + ", peerUid=" + peerUid
                + ", stateMachine=" + stateMachine);

        // 发送对端RTC上线事件
        sendMessage(MSGID_CALL_RTC_PEER_ONLINE, localUid, peerUid, null);
    }

    @Override
    public void onTalkingPeerLeft(int localUid, int peerUid) {
        int stateMachine = getStateMachine();
        ALog.getInstance().d(TAG, "<onTalkingPeerLeft> localUid=" + localUid
                + ", peerUid=" + peerUid
                + ", stateMachine=" + stateMachine);

        // 发送对端RTC掉线事件
        sendMessage(MSGID_CALL_RTC_PEER_OFFLINE, localUid, peerUid, null);
    }

    @Override
    public void onPeerFirstVideoDecoded(int peerUid, int videoWidth, int videoHeight) {
        int stateMachine = getStateMachine();
        ALog.getInstance().d(TAG, "<onPeerFirstVideoDecoded> peerUid=" + peerUid
                + ", videoWidth=" + videoWidth
                + ", videoHeight=" + videoHeight);

        // 发送对端RTC首帧出图事件
        sendMessage(MSGID_CALL_RTC_PEER_FIRSTVIDEO, videoWidth, videoHeight, null);
    }


    /////////////////////////////////////////////////////////////////////////////
    //////////////////////////// RTC Engine异常相关处理 //////////////////////////
    /////////////////////////////////////////////////////////////////////////////
    /*
     * @brief 工作线程中运行，对端RTC上线
     */
    void DoRtcPeerOnline(Message msg) {
        int localUid = msg.arg1;
        int peerUid = msg.arg2;
        int stateMachine = getStateMachine();
        ALog.getInstance().d(TAG, "<DoRtcPeerOnline> localUid=" + localUid
                + ", peerUid=" + peerUid
                + ", stateMachine=" + stateMachine);

        if (mPeerVidew != null) {
            mTalkEngine.setRemoteVideoView(mPeerVidew);
        }
    }

    /*
     * @brief 工作线程中运行，对端RTC下线
     */
    void DoRtcPeerOffline(Message msg) {
        int localUid = msg.arg1;
        int peerUid = msg.arg2;
        int stateMachine = getStateMachine();
        ALog.getInstance().d(TAG, "<DoRtcPeerOffline> localUid=" + localUid
                + ", peerUid=" + peerUid
                + ", stateMachine=" + stateMachine);

        if (stateMachine == CALLKIT_STATE_INCOMING ||
            stateMachine == CALLKIT_STATE_ANSWER_REQING ||
            stateMachine == CALLKIT_STATE_TALKING)  {
            String callbackAccountId = mPeerAccountId;
            exceptionProcess();
            CallbackPeerHangup(callbackAccountId);   // 回调对端挂断
        }
    }

    /*
     * @brief 工作线程中运行，对端RTC首帧出图
     */
    void DoRtcPeerFirstVideo(Message msg) {
        int width = msg.arg1;
        int height = msg.arg2;
        int stateMachine = getStateMachine();
        ALog.getInstance().d(TAG, "<DoRtcPeerFirstVideo> width=" + width
                + ", height=" + height);

        if ((stateMachine != CALLKIT_STATE_IDLE) && (stateMachine != CALLKIT_STATE_HANGUP_REQING)) {
            String callbackAccountId = mPeerAccountId;

            // 回调对端首帧出图
            synchronized (mCallbackList) {
                for (ICallkitMgr.ICallback listener : mCallbackList) {
                    listener.onPeerFirstVideo(callbackAccountId, width, height);
                }
            }
        }
    }


    /////////////////////////////////////////////////////////////////////////////
    /////////////////////////////// 所有的对上层回调处理 //////////////////////////
    /////////////////////////////////////////////////////////////////////////////
    void CallbackCallDialDone(int errCode, String peerAccountId) {
        synchronized (mCallbackList) {
            for (ICallkitMgr.ICallback listener : mCallbackList) {
                listener.onDialDone(errCode, peerAccountId);
            }
        }
    }

    void CallbackPeerIncoming(String peerAccountId, String attachMsg) {
        synchronized (mCallbackList) {
            for (ICallkitMgr.ICallback listener : mCallbackList) {
                listener.onPeerIncoming(peerAccountId, attachMsg);
            }
        }
    }

    void CallbackPeerAnswer(int errCode, String peerAccountId) {
        synchronized (mCallbackList) {
            for (ICallkitMgr.ICallback listener : mCallbackList) {
                listener.onPeerAnswer(peerAccountId);
            }
        }
    }

    void CallbackPeerHangup(String peerAccountId) {
        synchronized (mCallbackList) {
            for (ICallkitMgr.ICallback listener : mCallbackList) {
                listener.onPeerHangup(peerAccountId);
            }
        }
    }

    void CallbackPeerTimeout(String peerAccountId) {
        synchronized (mCallbackList) {
            for (ICallkitMgr.ICallback listener : mCallbackList) {
                listener.onPeerTimeout(peerAccountId);
            }
        }
    }

    void CallbackError(int errCode) {
        synchronized (mCallbackList) {
            for (ICallkitMgr.ICallback listener : mCallbackList) {
                listener.onCallkitError(errCode);
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////
    ////////////////////////////// Inner Methods //////////////////////////
    ///////////////////////////////////////////////////////////////////////
    void setStateMachine(int newStateMachine) {
        synchronized (mDataLock) {
            mStateMachine = newStateMachine;
        }
    }

    String getStateMachineTip(int callStatus) {
        if (callStatus == CALLKIT_STATE_IDLE) {
            return "1(Idle)";
        } else if (callStatus == CALLKIT_STATE_DIALING) {
            return "2(Dial)";
        } else if (callStatus == CALLKIT_STATE_INCOMING) {
            return "3(Incoming)";
        } else if (callStatus == CALLKIT_STATE_TALKING) {
            return "4(Talking)";
        } else if (callStatus == CALLKIT_STATE_DIAL_REQING) {
            return "5(Dial_Requesting)";
        } else if (callStatus == CALLKIT_STATE_DIAL_RSPING) {
            return "6(Dial_Responsing)";
        } else if (callStatus == CALLKIT_STATE_ANSWER_REQING) {
            return "7(Answer_Requesting)";
        } else if (callStatus == CALLKIT_STATE_ANSWER_RSPING) {
            return "8(Answer_Responsing)";
        } else if (callStatus == CALLKIT_STATE_HANGUP_REQING) {
            return "9(Hangup_Requesting)";
        }

        return (callStatus + "(Unknown)");
    }

    String getReasonTip(int reason) {
        if (reason == REASON_LOCAL_HANGUP) {
            return "1(Local Hangup)";
        } else if (reason == REASON_LOCAL_ANSWER) {
            return "2(Local Answer)";
        } else if (reason == REASON_PEER_HANGUP) {
            return "3(Peer Hangup)";
        } else if (reason == REASON_PEER_ANSWER) {
            return "4(Peer Answer)";
        } else if (reason == REASON_CALL_TIMEOUT) {
            return "5(Peer Timeout)";
        } else if (reason == REASON_NONE) {
            return "0(None)";
        }
        return (reason + "(Unknown)");
    }

}
