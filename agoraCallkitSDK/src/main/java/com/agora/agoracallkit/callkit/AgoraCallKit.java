/**
 * @file AgoraCallKit.java
 * @brief This file implement the Agora CallKit SDK
 *        The process of communication protocol and RtcEngine should be running in work thread
 * @author xiaohua.lu
 * @email luxiaohua@agora.io
 * @version 1.0.0.1
 * @date 2021-10-21
 * @license Copyright (C) 2021 AgoraIO Inc. All rights reserved.
 */
package com.agora.agoracallkit.callkit;

import android.accounts.Account;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceView;

import com.agora.agoracallkit.beans.IotAlarm;
import com.agora.agoracallkit.beans.UidInfoBean;
import com.agora.agoracallkit.beans.UidInfoBeansMap;
import com.agora.agoracallkit.database.AlarmDbMgr;
import com.agora.agoracallkit.logger.ALog;
import com.agora.agoracallkit.model.TalkingEngine;
import com.agora.agoracallkit.utils.AgoraRtmManager;
import com.agora.agoracallkit.utils.CallStateManager;
import com.agora.agoracallkit.utils.CallbackManager;
import com.agora.agoracallkit.utils.CloudRecordResult;
import com.agora.agoracallkit.utils.EMClientManager;
import com.agora.agoracallkit.utils.HttpRequestInterface;
import com.agora.agoracallkit.utils.ImageConvert;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.agora.rtc2.Constants;


public class AgoraCallKit implements com.agora.agoracallkit.callkit.AgoraCallNotify,
         TalkingEngine.ICallback {

    ////////////////////////////////////////////////////////////////////////
    //////////////////////// Data Structure Definition /////////////////////
    ////////////////////////////////////////////////////////////////////////
    /*
     * @brief CallKit初始化参数
     */
    public static class CallKitInitParam {
        public Context mContext;
        public String mRtcAppId;
        public Bundle mMetaData;
        public String mLogFilePath;                 ///< 日志文件路径，不设置则日志不输出到本地文件
        public boolean mPublishAudio = true;        ///< 通话时是否推流本地音频
        public boolean mPublishVideo = true;        ///< 通话时是否推流本地视频
        public boolean mSubscribeAudio = true;      ///< 通话时是否订阅对端音频
        public boolean mSubscribeVideo = true;      ///< 通话时是否订阅对端视频
    }

    /*
     * @brief 通话参数配置
     */
    public static class TalkingParam {
        public boolean mPublishAudio = true;        ///< 通话时是否推流本地音频
        public boolean mPublishVideo = true;        ///< 通话时是否推流本地视频
        public boolean mSubscribeAudio = true;      ///< 通话时是否订阅对端音频
        public boolean mSubscribeVideo = true;      ///< 通话时是否订阅对端视频
    }

    /*
     * @brief RTC状态信息
     */
    public static class NetworkStatus {
        public int totalDuration;
        public int txBytes;
        public int rxBytes;
        public int txKBitRate;
        public int txAudioBytes;
        public int rxAudioBytes;
        public int txVideoBytes;
        public int rxVideoBytes;
        public int rxKBitRate;
        public int txAudioKBitRate;
        public int rxAudioKBitRate;
        public int txVideoKBitRate;
        public int rxVideoKBitRate;
        public int lastmileDelay;
        public double cpuTotalUsage;
        public double cpuAppUsage;
        public int users;
        public int connectTimeMs;
        public int txPacketLossRate;
        public int rxPacketLossRate;
        public double memoryAppUsageRatio;
        public double memoryTotalUsageRatio;
        public int memoryAppUsageInKbytes;
    }

    /*
     * @brief 变声属性设置
     */
    public enum VoiceType {
        NORMAL, OLDMAN, BABYBOY, BABYGIRL, ZHUBAJIE, ETHEREAL, HULK
    }

    /*
     * @brief 告警记录
     */
    public static class AlarmRecord {
        public long mRecordId;      ///< 记录唯一标识，由数据库自增实现
        public long mTimestamp;     ///< 告警时刻点
        public String mDeviceId;    ///< 设备Id
        public long mUid;           ///< 设备对应RTC的Uid
        public int mType;           ///< 告警类型
        public int mPriority;       ///< 告警等级
        public String mVideoUrl;    ///< 回看视频的URL
        public String mMessage;     ///< 告警消息

        public String toString() {
            String infoText = "{ devId=" + mDeviceId + ", uid=" + mUid
                    + ", type=" + mType + ", priority=" + mPriority
                    + ", url=" + mVideoUrl + ", message=" + mMessage + " }";
            return infoText;
        }
    }

    /*
     * @brief 告警信息查询参数
     */
    public static class AlarmQueryParam {
        public int mYear;          ///< 要查询的年
        public int mMonth;         ///< 要查询的年
        public int mDay;           ///< 要查询的年
        public int mPageIndex;     ///< 查询页面索引，从1开始，1、2、3......
        public int mPageSize;      ///< 每个页面的记录数量
        public int mType;          ///< 查询的消息类型
    };

    ////////////////////////////////////////////////////////////////////////
    //////////////////////// Constant Definition ///////////////////////////
    ////////////////////////////////////////////////////////////////////////
    private static final String TAG = "CALLKIT/AgoraCallKit";
    private static final String ALARM_DBNAME = "DeviceAlarm";
    private static final int EXIT_WAIT_TIMEOUT = 3000;
    private static final long LOGIN_TIMEOUT = 10000;            ///< 登录超时10秒
    private static final long LOGOUT_TIMEOUT = 10000;           ///< 登出超时10秒
    private static final long DIALING_TIMEOUT = 30000;          ///< 主动呼叫超时30秒
    private static final long INCOMING_OPT_TIMEOUT = 32000;     ///< 来电时不接听和挂断超时30秒
    private static final long SYNCOPT_TIMEOUT = 3000;           ///< 同步操作超时3秒
    private static final long CLOUD_RECORD_LENGTH = 20000;      ///< 云录的时长20秒

    //
    // 云录制的时候第三方服务器配置信息
    //
    private static final int CLOUD_RECORD_VENDER_ID = 2;        ///< 第三方云服务器标识, 2: 表示阿里云
    private static final int CLOUD_RECORD_REGION = 1;           ///< 区域信息
    private static final String CLOUD_RECORD_BUCKET = "agora-iot-oss-test";
    private static final String CLOUD_RECORD_ACCESSKEY = "LTAI5t9JUNPgxDqsH5KAYgfx";
    private static final String CLOUD_RECORD_SECRETKEY = "b3iKLg94tyLBGmC3TeaOPg3BwCmzLD";


    //
    // The error code of this SDK public methods
    //
    public static final int ERR_NONE = 0;                       ///< 没有错误
    public static final int ERR_INVALID_PARAM = -1;             ///< 调用参数错误
    public static final int ERR_BAD_STATE = -2;                 ///< 当前状态不正确
    public static final int ERR_UNSUPPORTED = -3;               ///< 当前状态操作不支持
    public static final int ERR_TIMEOUT = -4;                   ///< 当前操作超时
    public static final int ERR_NOT_COMPLETED = -5;             ///< 当前操作未完成
    public static final int ERR_NO_BIND_ACCOUNT = -6;           ///< 没有绑定的账号
    public static final int ERR_HTTP_INVALID_URL = -1001;       ///< URL错误
    public static final int ERR_HTTP_DISCONNECT = -1002;        ///< Http无连接
    public static final int ERR_HTTP_NORESPONSE = -1003;        ///< Http无响应
    public static final int ERR_HTTP_NOSERVICE = -1004;         ///< Http找不到服务器
    public static final int ERR_HTTP_PARAMES_FAILED = -1005;    ///< Http请求参数非法
    public static final int ERR_HTTP_RESPONSE_ERROR = -1006;    ///< Http响应数据包错误
    public static final int ERR_SERVER_FAILED = -20011;
    public static final int ERR_SAME_ACCOUNT = -2002;
    public static final int ERR_NO_ACCOUNT = -2003;
    public static final int ERR_LOGIN_FAILED = -2004;
    public static final int ERR_LOGOUT_FAILED = -2005;
    public static final int ERR_NEW_CALL_FAILED = -2006;
    public static final int ERR_HAVENOT_INITED = -2007;         ///< 未初始化
    public static final int ERR_INVALID_PARAME = -2008;         ///< 参数错误
    public static final int ERR_NO_USER_LOGIN = -2009;          ///< 无已登录用户
    public static final int ERR_INVALID_UID = -2010;            ///< 无效的UID
    public static final int ERR_CALL_BUSY = -2011;              ///< 对方忙
    public static final int ERR_CALL_HANGUP = -2012;            ///< 对方挂断


    //
    // The state machine of SDK
    //
    public static final int STATE_INVALID = 0x0000;         ///< SDK还未初始化
    public static final int STATE_SDK_READY = 0x0001;       ///< SDK已经初始化,但还未登录
    public static final int STATE_REGISTERING = 0x0002;     ///< 正在通过UserId或者DeviceId进行注册
    public static final int STATE_LOGIN_ONGOING = 0x0003;   ///< 正在登录
    public static final int STATE_LOGOUT_ONGOING = 0x0004;  ///< 正在登出
    public static final int STATE_LOGGED_IDLE = 0x0005;     ///< 当前 用户/设备 已经登录,但是空闲状态
    public static final int STATE_DEVICE_BINDING = 0x0006;  ///< 正在绑定某个设备
    public static final int STATE_DEVICE_UNBINDING = 0x0007;///< 正在解绑某个设备
    public static final int STATE_DEVICE_QUERYING = 0x0008; ///< 正在查询绑定的设备列表
    public static final int STATE_USER_QUERYING = 0x0009;   ///< 正在查询绑定的用户列表
    public static final int STATE_CALL_DIALING = 0x000A;    ///< 正在主动呼叫对端的 设备/用户
    public static final int STATE_CALL_INCOMING = 0x000B;   ///< 接收到来电呼叫
    public static final int STATE_CALL_TALKING = 0x000C;    ///< 正常通话过程
    public static final int STATE_ACCOUNT_QUERYING = 0x000D;///< 正在查询账号状态
    public static final int STATE_CLOUDRCD_REQUESTING = 0x000E; ///< 正在进行请求云录制
    public static final int STATE_CLOUDRCD_RECORDING = 0x000F;  ///< 正在进行云录制
    public static final int STATE_ALARMS_QUERYING = 0x0010;  ///< 正在从服务器查询告警记录

    //
    // The mesage Id
    //
    private static final int MSGID_ACCOUNT_REGISTER = 0x1001;
    private static final int MSGID_ACCOUNT_AUTOLOGIN = 0x1002;
    private static final int MSGID_ACCOUNT_LOGIN = 0x1003;
    private static final int MSGID_ACCOUNT_LOGOUT = 0x1004;
    private static final int MSGID_LOGIN_DONE = 0x1005;
    private static final int MSGID_LOGOUT_DONE = 0x1006;
    private static final int MSGID_LOGIN_OTHERDEV = 0x1007;

    private static final int MSGID_DEVICE_BIND = 0x2001;
    private static final int MSGID_DEVICE_UNBIND = 0x2002;
    private static final int MSGID_DEVICE_QUERY = 0x2003;
    private static final int MSGID_USER_QUERY = 0x2004;

    private static final int MSGID_CALL_DIAL = 0x3001;
    private static final int MSGID_CALL_DIALRSP_START = 0x3002;
    private static final int MSGID_CALL_DIALRSP_ANSWER = 0x3003;
    private static final int MSGID_CALL_DIALRSP_BUSY = 0x3004;
    private static final int MSGID_CALL_DIALRSP_HANGUP = 0x3005;
    private static final int MSGID_CALL_DIALRSP_TIMEOUT = 0x3006;
    private static final int MSGID_CALL_HANGUP = 0x3007;
    private static final int MSGID_CALL_ANSWER = 0x3008;
    private static final int MSGID_CALL_TALKHANGUP = 0x3009;    ///< 通话过程中挂断
    private static final int MSGID_CALL_INCOMING = 0x300A;      ///< 通话过程中挂断
    private static final int MSGID_CALL_INCOMETIMEOUT = 0x300B; ///< 被叫过程中超时
    private static final int MSGID_CALL_INCOME_OPTTIMEOUT = 0x300C; ///< 被叫过程中不接听和挂断超时
    private static final int MSGID_CALL_PEER_INTERRUPT = 0x300D;    ///< 主叫或被叫或通话过程中对端中断

    private static final int MSGID_SETVIEW_LOCAL = 0x4001;      ///< 设置本地视频显示控件
    private static final int MSGID_SETVIEW_PEER = 0x4002;       ///< 设置对端视频显示控件
    private static final int MSGID_PEER_MESSAGE = 0x5001;       ///< 对端有消息过来
    private static final int MSGID_SEND_MESSAGE = 0x5002;       ///< 发送消息到对端
    private static final int MSGID_CLOUDRECORD_REQUEST = 0x6001;///< 请求云录制
    private static final int MSGID_CLOUDRECORD_START = 0x6002;  ///< 启动云录制
    private static final int MSGID_CLOUDRECORD_STOP = 0x6003;   ///< 停止云录制

    private static final int MSGID_ALARM_RECEIVED = 0x7001;     ///< 接收到对端告警消息
    private static final int MSGID_ALARM_QUERYING = 0x7002;     ///< 正在从服务器查询告警记录
    private static final int MSGID_IOTALARM_RECVED = 0x7003;    ///< 接收到设备警消息，针对Apical项目

    private static final int MSGID_WORK_EXIT = 0xFFFF;


    ////////////////////////////////////////////////////////////////////////
    //////////////////////// Variable Definition ///////////////////////////
    ////////////////////////////////////////////////////////////////////////
    private static AgoraCallKit instance;
    private CallKitInitParam mInitParam;
    public static final Object mDataLock = new Object();    ///< 同步访问锁,类中所有变量需要进行加锁处理

    private HandlerThread mWorkThread;
    private Handler mWorkHandler;
    private final Object mWorkExitEvent = new Object();
    private final Object mLocalHangupEvent = new Object();
    private final Object mLocalAnswerEvent = new Object();

    private List<com.agora.agoracallkit.callkit.ICallKitCallback> mListenerList = new ArrayList<>();

    private volatile int mStateMachine = STATE_INVALID;     ///< 当前呼叫状态机
    private com.agora.agoracallkit.callkit.CallKitAccount mLocalAccount;    ///< 本地端已经登录的账号
    private com.agora.agoracallkit.callkit.CallKitAccount mPeerAccount;     ///< 呼叫或者来电的对端账号
    private com.agora.agoracallkit.callkit.CallKitAccount mLoginAccount;    ///< 本地正在登录的账号

    private TalkingEngine mTalkEngine;
    private SurfaceView mLocalVideoView;        ///< 本地视频显示的控件
    private SurfaceView mPeerVideoView;         ///< 对端视频显示的控件

    private AlarmDbMgr mAlarmDbMgr;             ///< 告警记录数据库管理
    private String mTriggerAlarmMsg;            ///< 触发的告警信息


    ////////////////////////////////////////////////////////////////////////
    ////////////////////////// Public Methods //////////////////////////////
    ////////////////////////////////////////////////////////////////////////
    public static AgoraCallKit getInstance() {
        if(instance == null) {
            synchronized (AgoraCallKit.class) {
                if(instance == null) {
                    instance = new AgoraCallKit();
                }
            }
        }
        return instance;
    }


    /*
     * @brief 初始化SDK
     * @param context : 相关的上下文信息
     * @return error code
     */
    public int initialize(CallKitInitParam initParam) {
        mInitParam = initParam;

        if ((initParam.mLogFilePath != null) && (!initParam.mLogFilePath.isEmpty())) {
            boolean logRet = ALog.getInstance().initialize(initParam.mLogFilePath);
            if (!logRet) {
                Log.e(TAG, "<initialize > [ERROR] fail to initialize logger");
            }
        }


        // 为了加载动态库
        ImageConvert.getInstance();

        // 清空UID和account的映射关系记录
        UidInfoBeansMap.getInstance().clearUidInfoMap();

        // 注册事件通知回调
        CallbackManager.getInstance().registerListener(this);

        // 初始化业务服务Http请求接口
        HttpRequestInterface.getInstance().init(mInitParam.mContext, mInitParam.mMetaData);

        // 初始化环信IM服务
        EMClientManager.getInstance().init(mInitParam.mContext, mInitParam.mMetaData);

        // 初始化RTM服务
        AgoraRtmManager.getInstance().init(mInitParam.mContext, mInitParam.mMetaData);

        // 启动工作线程
        mWorkThread = new HandlerThread("AgoraCallKit");
        mWorkThread.start();
        mWorkHandler = new Handler(mWorkThread.getLooper() ){
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                processWorkMessage(msg);
            }
        };

        // 初始化通话引擎
        mTalkEngine = new TalkingEngine();
        TalkingEngine.InitParam talkInitParam = mTalkEngine.new InitParam();
        talkInitParam.mContext = mInitParam.mContext;
        talkInitParam.mAppId = mInitParam.mRtcAppId;
        talkInitParam.mCallback = this;
        talkInitParam.mPublishVideo = mInitParam.mPublishVideo;
        talkInitParam.mPublishAudio = mInitParam.mPublishAudio;
        talkInitParam.mSubscribeAudio = mInitParam.mSubscribeAudio;
        talkInitParam.mSubscribeVideo = mInitParam.mPublishVideo;
        mTalkEngine.initialize(talkInitParam);

        // 初始化告警数据库管理
        mAlarmDbMgr = new AlarmDbMgr();
        mAlarmDbMgr.initialize(mInitParam.mContext, ALARM_DBNAME);


        synchronized (mDataLock) {
            mStateMachine = STATE_SDK_READY;  // 状态机切换到 SDK就绪
        }
        ALog.getInstance().d(TAG, "<initialize> done");

        return ERR_NONE;
    }

    /*
     * @brief SDK释放操作
     */
    public void release()   {
        // 注销内部Sdk的回调
        CallbackManager.getInstance().unregisterListener(this);

        if (mWorkHandler != null) {
            mWorkHandler.removeMessages(MSGID_ACCOUNT_REGISTER);
            mWorkHandler.removeMessages(MSGID_ACCOUNT_AUTOLOGIN);
            mWorkHandler.removeMessages(MSGID_ACCOUNT_LOGIN);
            mWorkHandler.removeMessages(MSGID_ACCOUNT_LOGOUT);
            mWorkHandler.removeMessages(MSGID_LOGIN_DONE);
            mWorkHandler.removeMessages(MSGID_LOGOUT_DONE);
            mWorkHandler.removeMessages(MSGID_LOGIN_OTHERDEV);
            mWorkHandler.removeMessages(MSGID_DEVICE_BIND);
            mWorkHandler.removeMessages(MSGID_DEVICE_UNBIND);
            mWorkHandler.removeMessages(MSGID_DEVICE_QUERY);
            mWorkHandler.removeMessages(MSGID_USER_QUERY);
            mWorkHandler.removeMessages(MSGID_CALL_DIAL);
            mWorkHandler.removeMessages(MSGID_CALL_DIALRSP_START);
            mWorkHandler.removeMessages(MSGID_CALL_DIALRSP_ANSWER);
            mWorkHandler.removeMessages(MSGID_CALL_DIALRSP_BUSY);
            mWorkHandler.removeMessages(MSGID_CALL_DIALRSP_HANGUP);
            mWorkHandler.removeMessages(MSGID_CALL_DIALRSP_TIMEOUT);
            mWorkHandler.removeMessages(MSGID_CALL_HANGUP);
            mWorkHandler.removeMessages(MSGID_CALL_INCOMETIMEOUT);
            mWorkHandler.removeMessages(MSGID_CALL_INCOME_OPTTIMEOUT);
            mWorkHandler.removeMessages(MSGID_CALL_PEER_INTERRUPT);
            mWorkHandler.removeMessages(MSGID_SETVIEW_LOCAL);
            mWorkHandler.removeMessages(MSGID_SETVIEW_PEER);
            mWorkHandler.removeMessages(MSGID_PEER_MESSAGE);
            mWorkHandler.removeMessages(MSGID_SEND_MESSAGE);
            mWorkHandler.removeMessages(MSGID_CLOUDRECORD_REQUEST);
            mWorkHandler.removeMessages(MSGID_CLOUDRECORD_START);
            mWorkHandler.removeMessages(MSGID_CLOUDRECORD_STOP);
            mWorkHandler.removeMessages(MSGID_ALARM_RECEIVED);
            mWorkHandler.removeMessages(MSGID_IOTALARM_RECVED);

            // 同步等待线程中所有任务处理完成后，才能正常退出线程
            mWorkHandler.sendEmptyMessage(MSGID_WORK_EXIT);
            synchronized (mWorkExitEvent) {
                try {
                    mWorkExitEvent.wait(EXIT_WAIT_TIMEOUT);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    ALog.getInstance().e(TAG, "<release> exception=" + e.getMessage());
                }
            }

            mWorkHandler = null;
        }

        if (mWorkThread != null) {
            mWorkThread.quit();
            mWorkThread = null;
        }

        // 释放通话引擎
        if (mTalkEngine != null) {
            mTalkEngine.release();
            mTalkEngine = null;
        }

        // 释放告警数据库管理
        if (mAlarmDbMgr != null) {
            mAlarmDbMgr.release();
            mAlarmDbMgr = null;
            ALog.getInstance().d(TAG, "<release> done");
        }

        synchronized (mDataLock) {
            mStateMachine = STATE_INVALID;  // 状态机切换到 无效状态
        }

        ALog.getInstance().release();
    }

    /*
     * @brief 获取Sdk当前状态
     * @return 返回状态机值
     */
    public int getState() {
        synchronized (mDataLock) {
            return mStateMachine;
        }
    }

    /*
     * @brief 获取当前网络状态
     * @return NetworkStatus
     */
    public NetworkStatus getNetworkStatus() {
        return mTalkEngine.getNetworkStatus();
    }

    /*
     * @brief 订阅回调事件
     * @param 要订阅的回调接口
     * @return None
     */
    public void registerListener(com.agora.agoracallkit.callkit.ICallKitCallback callback) {
        synchronized (mDataLock) {
            if (mListenerList.indexOf(callback) >= 0) { // callback already registered
                return;
            }
            mListenerList.add(callback);
        }
    }

    /*
     * @brief 取消事件回调订阅
     * @param 要取消的回调接口
     * @return None
     */
    public void unregisterListener(com.agora.agoracallkit.callkit.ICallKitCallback callback) {
        synchronized (mDataLock) {
            mListenerList.remove(callback);
        }
    }

    /*
     * @brief 设置本地视频显示的控件，通常在初始化后调用该接口
     * @param localView 用于显示本地视频的控件
     */
    public void setLocalVideoView(SurfaceView localView)
    {
        int localUid = 0;
        synchronized (mDataLock) {
            mLocalVideoView = localView;
            if (mLocalAccount != null) {
                localUid = (int)mLocalAccount.getUid();
            }
        }

        if (localUid != 0)
        {
            // 发送消息设置本地video控件
            Message msg = new Message();
            msg.what = MSGID_SETVIEW_LOCAL;
            msg.arg1 = localUid;
            mWorkHandler.removeMessages(MSGID_SETVIEW_LOCAL);
            mWorkHandler.sendMessage(msg);

        }

    }

    /*
     * @brief 设置对端视频显示的控件，通常在初始化后调用该接口
     * @param peerView 用于显示对端视频的控件
     */
    public void setPeerVideoView(SurfaceView peerView)
    {
        int peerUid = 0;
        synchronized (mDataLock) {
            mPeerVideoView = peerView;
            if (mPeerAccount != null) {
                peerUid = (int)mPeerAccount.getUid();
            }
        }

        if (peerUid != 0)
        {
            // 发送消息设置对端video控件
            Message msg = new Message();
            msg.what = MSGID_SETVIEW_PEER;
            msg.arg1 = peerUid;
            mWorkHandler.removeMessages(MSGID_SETVIEW_PEER);
            mWorkHandler.sendMessage(msg);
        }
    }


    /*
     * @brief 根据 accountId (对应的UserId或者DeviceId) 来查询相应的账号信息
     *        仅在 STATE_SDK_READY 和 STATE_LOGGED_IDLE 状态下才能调用, 调用后变成 STATE_QUERYING 状态,
     *        查询成功或者失败后，状态切换回 原先状态
     * @param accountId  : 需要查询的 accountId
     * @param type       : 是作为设备注册 还是作为 账号注册
     * @return 已经查询到的账号，如果没有查询到则返回null
     */
    public CallKitAccount accountQuery(String accountId, int accountType) {
        synchronized (mDataLock) {
            if ((mStateMachine != STATE_SDK_READY) || (mStateMachine != STATE_LOGGED_IDLE)) {
                ALog.getInstance().e(TAG, "<accountQuery> bad state, mStateMachine=" + mStateMachine);
                return null;
            }
        }

        // 状态机切换到 正在查询中
        int oldStateMachine;
        synchronized (mDataLock) {
            oldStateMachine = mStateMachine;
            mStateMachine = STATE_ACCOUNT_QUERYING;
        }

        // 查询account对应的UID值
        CallKitAccount queriedAccount = null;
        UidInfoBean info = HttpRequestInterface.getInstance().queryUidWithAccount(accountId, accountType);
        if(info == null)  {
            int req_ret = HttpRequestInterface.getInstance().getLastErrorCode();
            if (HttpRequestInterface.RESULT_REQUST_PARAMES_FAILED == req_ret) {
                ALog.getInstance().e(TAG, "<accountQuery> invalid accountId=" + accountId);
            } else {
                ALog.getInstance().e(TAG, "<accountQuery> Cannot query user info now, please try again later.");
            }

        } else {
            queriedAccount = new CallKitAccount(accountId, accountType);
            queriedAccount.setUid(info.getUid());
            ALog.getInstance().d(TAG, "<accountQuery> queriedAccount=" + queriedAccount.toString());
        }

        // 状态机切换到 原先状态
        synchronized (mDataLock) {
            mStateMachine = oldStateMachine;
        }

        return queriedAccount;
    }


    /*
     * @brief 注册一个用户或者设备Id, 异步调用, 注册结果由callback回调通知
     *        仅在 STATE_SDK_READY 状态下才能调用, 调用后变成 STATE_REGISTERING 状态,
     *        注册成功或者失败后，状态切换回 STATE_SDK_READY
     * @param account  : 注册的 UserId/Device
     * @param type     : 是作为设备注册 还是作为 账号注册
     * @return error code
     */
    public int accountRegister(com.agora.agoracallkit.callkit.CallKitAccount account) {
        synchronized (mDataLock) {
            if (mStateMachine != STATE_SDK_READY) {
                ALog.getInstance().e(TAG, "<accountRegister> bad state, mStateMachine=" + mStateMachine);
                return ERR_BAD_STATE;
            }
        }

        synchronized (mDataLock) {
            mStateMachine = STATE_REGISTERING;  // 状态机切换到 正在注册中
        }
        Message msg = new Message();
        msg.what = MSGID_ACCOUNT_REGISTER;
        msg.obj = account;
        mWorkHandler.removeMessages(MSGID_ACCOUNT_REGISTER);
        mWorkHandler.sendMessage(msg);

        ALog.getInstance().d(TAG, "<accountRegister> account=" + account.getName());
        return ERR_NONE;
    }

    /*
     * @brief 使用最后一次登录的账号, 尝试自动登录, 异步调用, 登录结果通过 onLogInDone()事件回调通知
     *        仅在 STATE_SDK_READY 状态下才能调用，调用后变成 STATE_LOGIN_ONGOING状态，
     *        登录成功后切换成 STATE_LOGGED_IDLE状态； 如果登录失败则切换回 STATE_SDK_READY 状态
     * @return error code
     */
    public int accountAutoLogin() {
        synchronized (mDataLock) {
            if (mStateMachine == STATE_LOGGED_IDLE) {
                ALog.getInstance().e(TAG, "<accountAutoLogin> already login, localAccount=" + mLocalAccount.getName());
                return ERR_BAD_STATE;
            }
            if (mStateMachine != STATE_SDK_READY) {
                ALog.getInstance().e(TAG, "<accountAutoLogin> bad state, mStateMachine=" + mStateMachine);
                return ERR_BAD_STATE;
            }
        }

        synchronized (mDataLock) {
            mStateMachine = STATE_LOGIN_ONGOING;  // 状态机切换到 正在登录中
        }
        Message msg = new Message();
        msg.what = MSGID_ACCOUNT_AUTOLOGIN;
        mWorkHandler.removeMessages(MSGID_ACCOUNT_AUTOLOGIN);
        mWorkHandler.sendMessage(msg);

        ALog.getInstance().d(TAG, "<accountAutoLogin> ");
        return ERR_NONE;
    }

    /*
     * @brief 用户或者设备账号登录, 异步调用, 登录结果通过 onLogInDone() 事件回调通知
     *        仅在 STATE_SDK_READY 状态下才能调用，调用后变成 STATE_LOGIN_ONGOING状态，
     *        登录成功后切换成 STATE_LOGGED_IDLE状态； 如果登录失败则切换回 STATE_SDK_READY 状态
     * @param account  : 要登录的本地端账号
     * @return error code
     */
    public int accountLogIn(CallKitAccount account) {
        synchronized (mDataLock) {
            if (mStateMachine == STATE_LOGGED_IDLE) {
                ALog.getInstance().e(TAG, "<accountLogIn> already login, localAccount=" + mLocalAccount.getName());
                return ERR_BAD_STATE;
            }
            if (mStateMachine != STATE_SDK_READY) {
                ALog.getInstance().e(TAG, "<accountLogIn> bad state, mStateMachine=" + mStateMachine);
                return ERR_BAD_STATE;
            }
        }

        synchronized (mDataLock) {
            mStateMachine = STATE_LOGIN_ONGOING;  // 状态机切换到 正在登录中
        }
        Message msg = new Message();
        msg.what = MSGID_ACCOUNT_LOGIN;
        msg.obj = account;
        mWorkHandler.removeMessages(MSGID_ACCOUNT_LOGIN);
        mWorkHandler.sendMessage(msg);

        ALog.getInstance().d(TAG, "<accountLogIn> account=" + account.getName());
        return ERR_NONE;
    }

    /*
     * @brief 登出当前账号, 异步调用, 登出结果通过 onLogOutDone() 事件回调通知
     *        仅在 STATE_LOGGED_IDLE 状态下才能调用，调用后变成 STATE_LOGOUT_ONGOING状态，
     *        登出成功后切换成 STATE_SDK_READY 状态； 如果登出失败则切换回 STATE_LOGGED_IDLE 状态
     * @return true/false
     */
    public int accountLogOut() {
        synchronized (mDataLock) {
            if (mStateMachine == STATE_SDK_READY) {
                ALog.getInstance().e(TAG, "<accountAutoLogin> already logout");
                return ERR_BAD_STATE;
            }
            if (mStateMachine != STATE_LOGGED_IDLE) {
                ALog.getInstance().e(TAG, "<accountLogOut> bad state, mStateMachine=" + mStateMachine);
                return ERR_BAD_STATE;
            }
        }
        assert (mLocalAccount != null);

        synchronized (mDataLock) {
            mStateMachine = STATE_LOGOUT_ONGOING;  // 状态机切换到 正在登出中
        }
        Message msg = new Message();
        msg.what = MSGID_ACCOUNT_LOGOUT;
        mWorkHandler.removeMessages(MSGID_ACCOUNT_LOGOUT);
        mWorkHandler.sendMessage(msg);

        ALog.getInstance().d(TAG, "<accountLogOut> localAccount=" + mLocalAccount.getName());
        return ERR_NONE;
    }

    /*
     * @brief 获取本地当前登录账号
     * @return 如果是null表示本地账号未登录
     */
    public CallKitAccount getLocalAccount() {
        synchronized (mDataLock) {
            return mLocalAccount;
        }
    }



    /*
     * @brief 绑定多个设备到当前已经登录的账号，异步调用，通过 onBindDeviceDone() 事件来回调通知
     *        仅在 STATE_LOGGED_IDLE 状态下才能调用，调用后变成 STATE_DEVICE_BINGDING状态，
     *        绑定结束后切换回 STATE_LOGGED_IDLE 状态
     * @param deviceIdList : 要绑定的 deviceId列表
     * @return error code
     */
    public int bindDevice(List<String> deviceIdList) {
        synchronized (mDataLock) {
            if (mStateMachine != STATE_LOGGED_IDLE) {
                ALog.getInstance().e(TAG, "<bindDevice> bad state, mStateMachine=" + mStateMachine);
                return ERR_BAD_STATE;
            }
        }

        synchronized (mDataLock) {
            mStateMachine = STATE_DEVICE_BINDING;  // 状态机切换到 正在绑定中
        }
        Message msg = new Message();
        msg.what = MSGID_DEVICE_BIND;
        msg.obj = deviceIdList;
        mWorkHandler.removeMessages(MSGID_DEVICE_BIND);
        mWorkHandler.sendMessage(msg);

        ALog.getInstance().d(TAG, "<bindDevice> devices= " + combineText(deviceIdList));
        return ERR_NONE;
    }

    /*
     * @brief 从当前账号中解绑定多个设备，异步调用，通过 onUnbindDeviceDone() 事件来回调通知
     *        仅在 STATE_LOGGED_IDLE 状态下才能调用，调用后变成 STATE_DEVICE_UNBINGDING 状态，
     *        绑定结束后切换回 STATE_LOGGED_IDLE 状态
     * @param deviceIdList : 要解绑的 deviceId列表
     * @return error code
     */
    public int unbindDevice(List<String> deviceIdList) {
        synchronized (mDataLock) {
            if (mStateMachine != STATE_LOGGED_IDLE) {
                ALog.getInstance().e(TAG, "<unbindDevice> bad state, mStateMachine=" + mStateMachine);
                return ERR_BAD_STATE;
            }
        }

        synchronized (mDataLock) {
            mStateMachine = STATE_DEVICE_UNBINDING;  // 状态机切换到 正在解绑中
        }
        Message msg = new Message();
        msg.what = MSGID_DEVICE_UNBIND;
        msg.obj = deviceIdList;
        mWorkHandler.removeMessages(MSGID_DEVICE_UNBIND);
        mWorkHandler.sendMessage(msg);

        ALog.getInstance().d(TAG, "<unbindDevice> devices= " + combineText(deviceIdList));
        return ERR_NONE;
    }

    /*
     * @brief 获取当前设备已经绑定的账号列表
     *        仅在 STATE_LOGGED_IDLE 状态下才能调用, 调用后变成 STATE_DEVICE_QUERYING 状态机
     *        查询结束后切换回 STATE_LOGGED_IDLE 状态
     * @return error code
     */
    public int queryBindedDevList()    {
        synchronized (mDataLock) {
            if (mStateMachine != STATE_LOGGED_IDLE) {
                Log.e(TAG, "<queryBindedDevList> bad state, mStateMachine=" + mStateMachine);
                return ERR_BAD_STATE;
            }
        }

        synchronized (mDataLock) {
            mStateMachine = STATE_DEVICE_QUERYING;  // 状态机切换到 正在查询中
        }
        Message msg = new Message();
        msg.what = MSGID_DEVICE_QUERY;
        mWorkHandler.removeMessages(MSGID_DEVICE_QUERY);
        mWorkHandler.sendMessage(msg);

        ALog.getInstance().d(TAG, "<queryBindedDevList> ");
        return ERR_NONE;
    }

    /*
     * @brief 获取当前账号已经绑定的设备列表
     *        仅在 STATE_LOGGED_IDLE 状态下才能调用, 调用后变成 STATE_USER_QUERYING 状态机
     *        查询结束后切换回 STATE_LOGGED_IDLE 状态
     * @return error code
     */
    public int queryBindedUserList()    {
        synchronized (mDataLock) {
            if (mStateMachine != STATE_LOGGED_IDLE) {
                ALog.getInstance().e(TAG, "<queryBindedUserList> bad state, mStateMachine=" + mStateMachine);
                return ERR_BAD_STATE;
            }
        }

        synchronized (mDataLock) {
            mStateMachine = STATE_USER_QUERYING;  // 状态机切换到 正在查询中
        }
        Message msg = new Message();
        msg.what = MSGID_USER_QUERY;
        mWorkHandler.removeMessages(MSGID_USER_QUERY);
        mWorkHandler.sendMessage(msg);

        ALog.getInstance().d(TAG, "<queryBindedUserList> ");
        return ERR_NONE;
    }

    /*
     * @brief 主动发起新的呼叫, 异步调用，根据对端不同的响应，本地会接收到
     *          onPeerAnswer() / onPeerBusy() / onPeerTimeout() 响应回调事件
     *         仅在 STATE_LOGGED_IDLE 状态下才能调用，调用后变成 STATE_CALL_DIALING 状态
     *         对端如果正常接听，回调 onPeerAnswer() 事件，本地切换到 STATE_CALL_TALKING 状态
     *         对端如果直接拒绝，回调 onPeerBusy() 事件，本地挂断并切换回 STATE_LOGGED_IDLE状态；
     *         对端如果超时无响应，回调 onPeerTimeout() 事件，本地挂断并切换回 STATE_LOGGED_IDLE状态；
     * @param accountList : 要呼叫的对端账号列表 （支持同时呼叫多个对端，但是只有一个对端会接听）
     * @param attachMsg : 呼叫时附带的消息数据，如果不需要直接传空字符即可
     * @return error code
     */
    public int callDial(List<CallKitAccount> accountList, String attachMsg) {
        synchronized (mDataLock) {
            if (mStateMachine != STATE_LOGGED_IDLE) {
                ALog.getInstance().e(TAG, "<callDial> bad state, mStateMachine=" + mStateMachine);
                return ERR_BAD_STATE;
            }
        }

        synchronized (mDataLock) {
            mStateMachine = STATE_CALL_DIALING;  // 状态机切换到 正在拨号中
        }

        // 清除来电后操作超时
        mWorkHandler.removeMessages(MSGID_CALL_INCOME_OPTTIMEOUT);

        // 等待接听超时
        Message timeoutMsg = new Message();
        timeoutMsg.what = MSGID_CALL_DIALRSP_TIMEOUT;
        mWorkHandler.removeMessages(MSGID_CALL_DIALRSP_TIMEOUT);
        mWorkHandler.sendMessageDelayed(timeoutMsg, DIALING_TIMEOUT);

        // 发送主动拨号消息
        CallDialingInfo dialingInfo = new CallDialingInfo();
        dialingInfo.mAccountList = accountList;
        dialingInfo.mAttachMsg = attachMsg;
        Message msg = new Message();
        msg.what = MSGID_CALL_DIAL;
        msg.obj = dialingInfo;
        mWorkHandler.removeMessages(MSGID_CALL_DIAL);
        mWorkHandler.sendMessage(msg);

        ALog.getInstance().d(TAG, "<callDial> peer_account=" + accountList.get(0).getName());
        return ERR_NONE;
    }

    /*
     * @brief 挂断当前 主动呼叫/来电/通话，该调用是同步调用
     *         仅在 STATE_CALL_DIALING 或 STATE_CALL_INCOMING 或者 STATE_CALL_TALKING 状态下才能调用，
     *         调用后变成 STATE_LOGGED_IDLE 状态
     *         在主动拨号 (STATE_CALL_DIALING) 状态下，该方法就是直接挂断当前拨打电话
     *         在来电状态下 (STATE_CALL_INCOMING) ，该方法就是拒绝接听
     *         在通话状态下 (STATE_CALL_TALKING)，该方法就是本地直接挂断电话
     * @return error code
     */
    public int callHangup() {
        synchronized (mDataLock) {
            if ((mStateMachine != STATE_CALL_DIALING) &&
                (mStateMachine != STATE_CALL_TALKING) &&
                (mStateMachine != STATE_CALL_INCOMING))
            {
                ALog.getInstance().e(TAG, "<callHangup> bad state, mStateMachine=" + mStateMachine);
                return ERR_BAD_STATE;
            }
        }

        // 清除来电后操作超时
        mWorkHandler.removeMessages(MSGID_CALL_INCOME_OPTTIMEOUT);

        // 发送主动 Hangup消息
        ALog.getInstance().d(TAG, "<callHangup> begin, mStateMachine=" + mStateMachine);
        Message msg = new Message();
        msg.what = MSGID_CALL_HANGUP;
        mWorkHandler.removeMessages(MSGID_CALL_HANGUP);
        mWorkHandler.sendMessage(msg);

        // 这里需要同步等待 hangup 消息执行完
        synchronized (mLocalHangupEvent) {
            try {
                mLocalHangupEvent.wait(SYNCOPT_TIMEOUT);
            } catch (InterruptedException e) {
                e.printStackTrace();
                ALog.getInstance().e(TAG, "<callHangup> exception=" + e.getMessage());
            }
        }
        synchronized (mDataLock) {
            mStateMachine = STATE_LOGGED_IDLE;
        }

        ALog.getInstance().d(TAG, "<callHangup> end, mStateMachine=" + mStateMachine);
        return ERR_NONE;
    }

    /*
     * @brief 接听来电呼叫，该调用是同步调用
     *         仅在 STATE_CALL_INCOMING 状态下才能调用，调用后变成 STATE_CALL_TALKING 状态
     * @return error code
     */
    public int callAnswer() {
        synchronized (mDataLock) {
            if (mStateMachine != STATE_CALL_INCOMING)
            {
                ALog.getInstance().e(TAG, "<callAnswer> bad state, mStateMachine=" + mStateMachine);
                return ERR_BAD_STATE;
            }
        }

        // 清除来电后操作超时
        mWorkHandler.removeMessages(MSGID_CALL_INCOME_OPTTIMEOUT);

        // 发送主动 接听处理消息
        ALog.getInstance().d(TAG, "<callAnswer> begin, mStateMachine=" + mStateMachine);
        Message msg = new Message();
        msg.what = MSGID_CALL_ANSWER;
        mWorkHandler.removeMessages(MSGID_CALL_ANSWER);
        mWorkHandler.sendMessage(msg);

        // 这里需要同步等待 接听处理 消息执行完
        synchronized (mLocalAnswerEvent) {
            try {
                mLocalAnswerEvent.wait(SYNCOPT_TIMEOUT);
            } catch (InterruptedException e) {
                e.printStackTrace();
                ALog.getInstance().e(TAG, "<callAnswer> exception=" + e.getMessage());
            }
        }
        synchronized (mDataLock) {
            mStateMachine = STATE_CALL_TALKING;
        }

        ALog.getInstance().d(TAG, "<callAnswer> end, mStateMachine=" + mStateMachine);
        return ERR_NONE;
    }


    /*
     * @brief 设置通话时的音视频参数
     * @return error code
     */
    public int setTalkParam(TalkingParam talkingParam) {
        synchronized (mDataLock) {
            mInitParam.mSubscribeVideo = talkingParam.mSubscribeVideo;
            mInitParam.mSubscribeAudio = talkingParam.mSubscribeAudio;
            mInitParam.mPublishVideo = talkingParam.mPublishVideo;
            mInitParam.mPublishAudio = talkingParam.mPublishAudio;
            mTalkEngine.setTalkingParam(talkingParam);
        }

        ALog.getInstance().d(TAG, "<setTalkParam> end, subVideo=" + talkingParam.mSubscribeVideo
                + ", subAudio=" + talkingParam.mSubscribeAudio
                + ", pubVideo=" + talkingParam.mPublishVideo
                + ", pubAudio=" + talkingParam.mPublishAudio  );
        return ERR_NONE;
    }

    /*
     * @brief 设置通话时的私有参数
     * @return error code
     */
    public int setTalkPrivateParam(String privateParam) {
        int ret = 0;
        synchronized (mDataLock) {
            ret = mTalkEngine.setParameters(privateParam);
        }

        return (ret == Constants.ERR_OK) ? ERR_NONE : ERR_INVALID_PARAM;
    }

    /*
     * @brief 获取通话时的音视频参数
     * @return 音视频参数
     */
    public TalkingParam getTalkParam() {
        TalkingParam talkingParam = new TalkingParam();
        synchronized (mDataLock) {
            talkingParam.mSubscribeVideo = mInitParam.mSubscribeVideo;
            talkingParam.mSubscribeAudio = mInitParam.mSubscribeAudio;
            talkingParam.mPublishVideo = mInitParam.mPublishVideo;
            talkingParam.mPublishAudio = mInitParam.mPublishAudio;
        }

        return talkingParam;
    }

    /*
     * @brief 控制本地端视频是否推流
     * @return true/false
     */
    public boolean muteLocalVideoStream(boolean mute) {
        return mTalkEngine.muteLocalVideoStream(mute);
    }

    /*
     * @brief 控制本地端音频是否推流
     * @return true/false
     */
    public boolean muteLocalAudioStream(boolean mute) {
        return mTalkEngine.muteLocalAudioStream(mute);
    }

    /*
     * @brief 控制对端视频是否推流
     * @return true/false
     */
    public boolean mutePeerVideoStream(boolean mute) {
        return mTalkEngine.mutePeerVideoStream(mute);
    }

    /*
     * @brief 控制对端音频是否推流
     * @return true/false
     */
    public boolean mutePeerAudioStream(boolean mute) {
        return mTalkEngine.mutePeerAudioStream(mute);
    }

    /*
     * @brief 本地端音频变声控制
     * @return true/false
     */
    public boolean setLocalVoiceType(VoiceType voice_type) {
       return mTalkEngine.setLocalVoiceType(voice_type);
    }

    /*
     * @brief 截屏对端视频帧图像
     * @return 抓取到的视频帧图像
     */
    public Bitmap capturePeerVideoFrame() {
        return mTalkEngine.capturePeerVideoFrame();
    }

    /*
     * @brief 发送消息到对端
     * @return true/false
     */
    public int sendCustomizeMessage(String message) {
        synchronized (mDataLock) {
            if (mStateMachine < STATE_LOGGED_IDLE)
            {
                ALog.getInstance().e(TAG, "<sendCustomizeMessage> bad state, mStateMachine=" + mStateMachine);
                return ERR_BAD_STATE;
            }
        }

        // 发送定制化消息
        Message msg = new Message();
        msg.what = MSGID_SEND_MESSAGE;
        msg.obj = message;
        mWorkHandler.removeMessages(MSGID_SEND_MESSAGE);
        mWorkHandler.sendMessage(msg);

        ALog.getInstance().d(TAG, "<sendCustomizeMessage> end, message=" + message);
        return ERR_NONE;
    }


    /*
     * @brief 触发一个告警信息
     * @param alarmMessage : 告警信息内容
     * @return 错误码
     */
    public int triggerAlarm(String alarmMessage) {
        synchronized (mDataLock) {
            if (mStateMachine != STATE_LOGGED_IDLE)
            {
                ALog.getInstance().e(TAG, "<triggerAlarm> bad state, mStateMachine=" + mStateMachine);
                return ERR_BAD_STATE;
            }
        }

        // 发送云录制请求
        Message msg = new Message();
        msg.what = MSGID_CLOUDRECORD_REQUEST;
        msg.obj = alarmMessage;
        mWorkHandler.removeMessages(MSGID_CLOUDRECORD_REQUEST);
        mWorkHandler.sendMessage(msg);

        synchronized (mDataLock) {
            mStateMachine = STATE_CLOUDRCD_REQUESTING;
        }

        ALog.getInstance().d(TAG, "<triggerAlarm> end, alarmMessage=" + alarmMessage);
        return ERR_NONE;
    }

    /*
     * @brief 根据设备名查询相应的告警记录信息
     * @param deviceId : 要查询的 deviceId
     * @return 返回查询到的记录列表
     */
    public List<AlarmRecord> queryAlarmByDeviceId(String deviceId) {
        List<AlarmRecord> recordList = mAlarmDbMgr.queryByDeviceId(deviceId);
        return recordList;
    }

    /*
     * @brief 查询所有的告警记录信息
     * @param None
     * @return 返回查询到的记录列表
     */
    public List<AlarmRecord> queryAllAlarm() {
        List<AlarmRecord> recordList = mAlarmDbMgr.queryAll();
        return recordList;
    }

    /*
     * @brief 从服务器上查询所有告警信息，针对IoTSdk2.0新增协议接口
     * @param None
     * @return 返回查询到的记录列表
     */
    public int queryAlarmsFromServer(AlarmQueryParam queryParam) {
        synchronized (mDataLock) {
            if (mStateMachine != STATE_LOGGED_IDLE)
            {
                ALog.getInstance().e(TAG, "<queryAlarmsFromServer> bad state, mStateMachine=" + mStateMachine);
                return ERR_BAD_STATE;
            }
        }

        // 发送 告警查询 消息
        ALog.getInstance().d(TAG, "<queryAlarmsFromServer> begin, mStateMachine=" + mStateMachine);
        Message msg = new Message();
        msg.what = MSGID_ALARM_QUERYING;
        msg.obj = queryParam;
        mWorkHandler.removeMessages(MSGID_ALARM_QUERYING);
        mWorkHandler.sendMessage(msg);

        synchronized (mDataLock) {
            mStateMachine = STATE_ALARMS_QUERYING;
        }

        ALog.getInstance().d(TAG, "<queryAlarmsFromServer> end, mStateMachine=" + mStateMachine);
        return ERR_NONE;
    }


    /*
     * @brief 根据recordId来删除告警记录
     * @param deviceId : 需要删除的 deviceId
     * @return 删除记录的个数
     */
    public int deleteAlarmByDeviceId(String deviceId) {
        int deletedCount = mAlarmDbMgr.deleteByDeviceId(deviceId);
        return deletedCount;
    }

    /*
     * @brief 根据recordId来删除告警记录
     * @param recordIdList : 需要删除的 recordId列表
     * @return 删除记录的个数
     */
    public int deleteAlarmByRecordIdList(List<Long> recordIdList) {
        int deletedCount = mAlarmDbMgr.deleteByRecordIdList(recordIdList);
        return deletedCount;
    }

    ////////////////////////////////////////////////////////////////////////
    ////////////////////////// Work Thread Methods  ////////////////////////
    ////////////////////////////////////////////////////////////////////////
    void processWorkMessage(Message msg)
    {
        switch (msg.what)
        {
            case MSGID_ACCOUNT_REGISTER:
                DoAccountRegister((CallKitAccount)(msg.obj));
                break;

            case MSGID_ACCOUNT_AUTOLOGIN:
                DoAccountAutoLogIn();
                break;

            case MSGID_ACCOUNT_LOGIN:
                DoAccountLogIn((CallKitAccount)(msg.obj));
                break;

            case MSGID_LOGIN_DONE:
                DoAccountLogInDone(msg.arg1);
                break;

            case MSGID_ACCOUNT_LOGOUT:
                DoAccountLogOut();
                break;

            case MSGID_LOGOUT_DONE:
                DoAccountLogOutDone(msg.arg1);
                break;

            case MSGID_LOGIN_OTHERDEV:
                DoAccountLogInOtherDev();
                break;

            case MSGID_DEVICE_BIND:
                DoDeviceBind((List<String>)(msg.obj));
                break;

            case MSGID_DEVICE_UNBIND:
                DoDeviceUnbind((List<String>)(msg.obj));
                break;

            case MSGID_DEVICE_QUERY:
                DoDeviceQuery();
                break;

            case MSGID_USER_QUERY:
                DoUserQuery();
                break;

            case MSGID_CALL_DIAL:
                DoCallDial((CallDialingInfo)(msg.obj));
                break;

            case MSGID_CALL_DIALRSP_START:
                DoCallDialStart((CallResponseInfo)msg.obj);
                break;
            case MSGID_CALL_DIALRSP_ANSWER:
                DoCallDialPeerAnswer((CallResponseInfo)msg.obj);
                break;

            case MSGID_CALL_DIALRSP_BUSY:
                DoCallDialPeerBusy();
                break;

            case MSGID_CALL_DIALRSP_HANGUP:
                DoCallDialPeerHangup();
                break;

            case MSGID_CALL_DIALRSP_TIMEOUT:
                DoCallDialPeerTimeout();
                break;

            case MSGID_CALL_HANGUP:
                DoCallHangup();
                break;

            case MSGID_CALL_TALKHANGUP:
                DoTalkingHangup();
                break;

            case MSGID_CALL_INCOMING:
                DoCallIncoming((CallIncomingInfo)(msg.obj));
                break;

            case MSGID_CALL_INCOME_OPTTIMEOUT:
                DoCallIncomeOptTimeout((CallIncomingInfo)(msg.obj));
                break;

            case MSGID_CALL_PEER_INTERRUPT:
                DoCallPeerInterrupt();
                break;

            case MSGID_CALL_ANSWER:
                DoCallAnswer();
                break;

            case MSGID_CALL_INCOMETIMEOUT:
                DoInCallTimeout();
                break;

            case MSGID_SETVIEW_LOCAL:
                DoSetupLocalView(msg.arg1);
                break;

            case MSGID_SETVIEW_PEER:
                DoSetupPeerView(msg.arg1);
                break;

            case MSGID_PEER_MESSAGE:
                DoPeerMessage((String)msg.obj);
                break;

            case MSGID_SEND_MESSAGE:
                DoSendMessage((String)msg.obj);
                break;

            case MSGID_CLOUDRECORD_REQUEST:
                DoCloudRecordRequest((String)msg.obj);
                break;

            case MSGID_CLOUDRECORD_START:
                DoCloudRecordStart((CloudRecordInfo)msg.obj);
                break;

            case MSGID_CLOUDRECORD_STOP:
                DoCloudRecordStop((CloudRecordInfo)msg.obj);
                break;

            case MSGID_ALARM_RECEIVED:
                DoAlarmReceived((AlarmRecord)msg.obj);
                break;

            case MSGID_IOTALARM_RECVED:
                DoIotAlarmReceived((IotAlarm) msg.obj);
                break;

            case MSGID_ALARM_QUERYING:
                DoAlarmQuery((AlarmQueryParam)msg.obj);
                break;

            case MSGID_WORK_EXIT:  // 工作线程退出消息
                synchronized (mWorkExitEvent) {
                    mWorkExitEvent.notify();    // 事件通知
                }
                break;

            default:
                break;
        }
    }

    /*
     * @brief 工作线程中进行实际的注册操作
     */
    void DoAccountRegister(CallKitAccount account)
    {
        HttpRequestInterface httpReq = HttpRequestInterface.getInstance();
        UidInfoBean info = httpReq.registerWithUserAccount(account.getName(), account.getType());
        int errCode = ERR_NONE;
        if (info == null) {
            errCode = ERR_HTTP_NOSERVICE;
        }

        int ret = httpReq.getLastErrorCode();
        if (HttpRequestInterface.RESULT_OK != errCode) {
            errCode = ret - 1000;
        }

        synchronized (mDataLock) {
            mStateMachine = STATE_SDK_READY;  // 状态机切换到 SDK就绪 状态
        }

        ALog.getInstance().d(TAG, "<DoAccountRegister> account=" + account.getName()
                + ", errCode=" + errCode);
        CallbackRegisterDone(account, errCode);
    }

    /*
     * @brief 工作线程中进行实际的自动登录操作，需要等登录结果消息回来
     */
    void DoAccountAutoLogIn()
    {
        //查看是否有已登录用户
        long userId = EMClientManager.getInstance().getLoginUid();
        if (userId <= 0) {
            ALog.getInstance().e(TAG, "<DoAccountAutoLogIn> No user have login, or user was logout");
            synchronized (mDataLock) {
                mStateMachine = STATE_SDK_READY;  // 状态机切换回 SDK就绪 状态
            }
            CallbackLogInDone(mLocalAccount, ERR_NO_USER_LOGIN);
            return;
        }

        //查询登录用户account信息
        int errCode = ERR_NONE;
        UidInfoBean info = HttpRequestInterface.getInstance().queryAccountWithUid(userId);
        if(info == null)  {
            int req_ret = HttpRequestInterface.getInstance().getLastErrorCode();
            if (HttpRequestInterface.RESULT_REQUST_PARAMES_FAILED == req_ret) {
                ALog.getInstance().e(TAG, "<DoAccountAutoLogIn> invalid uid=" + userId);
                errCode = ERR_INVALID_UID;
            } else {
                ALog.getInstance().e(TAG, "<DoAccountAutoLogIn> Cannot query user info now, please try again later.");
                errCode = ERR_SERVER_FAILED;
            }

            synchronized (mDataLock) {
                mStateMachine = STATE_SDK_READY;  // 状态机切换回 SDK就绪 状态
            }
            CallbackLogInDone(mLocalAccount, errCode);
            return;
        }

        // 设置正在登录的账号
        synchronized (mDataLock) {
            mLoginAccount = new CallKitAccount(info.getAccount(), info.getType());
            mLoginAccount.setUid(info.getUid());
        }


        //等待登录结果回调
        Message msg = new Message();
        msg.what = MSGID_LOGIN_DONE;
        msg.arg1 = ERR_TIMEOUT;
        mWorkHandler.removeMessages(MSGID_LOGIN_DONE);
        mWorkHandler.sendMessageDelayed(msg, LOGIN_TIMEOUT);

        // 登录环信IM
        EMClientManager.getInstance().login(String.valueOf(userId));

        // 登录RTM
        AgoraRtmManager.getInstance().login(String.valueOf(userId));

        ALog.getInstance().d(TAG, "<DoAccountAutoLogIn> finished");
    }

    /*
     * @brief 工作线程中进行实际的登录操作，需要等登录结果消息回来
     */
    void DoAccountLogIn(CallKitAccount account)
    {
        //查询account对应的UID值
        int errCode = ERR_NONE;
        UidInfoBean info = HttpRequestInterface.getInstance()
                .queryUidWithAccount(account.getName(), account.getType());
        if(info == null)  {
            int req_ret = HttpRequestInterface.getInstance().getLastErrorCode();
            if (HttpRequestInterface.RESULT_REQUST_PARAMES_FAILED == req_ret) {
                ALog.getInstance().e(TAG, "<DoAccountLogIn> invalid uid=" + account.getName());
                errCode = ERR_INVALID_UID;
            } else {
                ALog.getInstance().e(TAG, "<DoAccountLogIn> Cannot query user info now, please try again later.");
                errCode = ERR_SERVER_FAILED;
            }

            synchronized (mDataLock) {
                mStateMachine = STATE_SDK_READY;  // 状态机切换回 SDK就绪 状态
            }
            CallbackLogInDone(mLocalAccount, errCode);
            return;
        }


        // 设置正在登录的账号
        synchronized (mDataLock) {
            mLoginAccount = new CallKitAccount(info.getAccount(), info.getType());
            mLoginAccount.setUid(info.getUid());
        }
        long userId = info.getUid();

        //等待登录结果回调
        Message msg = new Message();
        msg.what = MSGID_LOGIN_DONE;
        msg.arg1 = ERR_TIMEOUT;
        mWorkHandler.removeMessages(MSGID_LOGIN_DONE);
        mWorkHandler.sendMessageDelayed(msg, LOGIN_TIMEOUT);

        // 登录环信IM
        EMClientManager.getInstance().login(String.valueOf(userId));

        // 登录RTM
        AgoraRtmManager.getInstance().login(String.valueOf(userId));

        ALog.getInstance().d(TAG, "<DoAccountLogIn> finished");
    }

    /*
     * @brief 工作线程中处理登录结果
     */
    void DoAccountLogInDone(int errCode)
    {
        synchronized (mDataLock) {
            if (ERR_NONE == errCode) {
                mLocalAccount = mLoginAccount;      // 设置当前已经登录的本地账号
                mLocalAccount.setOnline(true);
                CallStateManager.getInstance().updateLocalUserInfo(
                        mLocalAccount.getUid(), mLocalAccount.getName(), mLocalAccount.getType());
                EMClientManager.getInstance().startReceiveMessage(); //登录成功后才允许接收离线消息
                mStateMachine = STATE_LOGGED_IDLE;  // 状态机切换到 登录后空闲 状态

            } else {
                mStateMachine = STATE_SDK_READY;    // 状态机切换回 SDK就绪 状态
                mLocalAccount = null;               // 清空已经登录的本地账号
            }
        }

        ALog.getInstance().d(TAG, "<DoAccountLogInDone> finished");
        CallbackLogInDone(mLocalAccount, errCode);
    }

    /*
     * @brief 工作线程中进行实际的登出操作，需要等登出结果消息回来
     */
    void DoAccountLogOut()
    {
        ALog.getInstance().d(TAG, "<DoAccountLogOut> start");

        //等待登出结果回调
        Message msg = new Message();
        msg.what = MSGID_LOGOUT_DONE;
        msg.arg1 = ERR_TIMEOUT;
        mWorkHandler.removeMessages(MSGID_LOGOUT_DONE);
        mWorkHandler.sendMessageDelayed(msg, LOGOUT_TIMEOUT);

        // 登出环信IM
        EMClientManager.getInstance().logout();

        // 登出RTM
        AgoraRtmManager.getInstance().logout();

        ALog.getInstance().d(TAG, "<DoAccountLogOut> done");
    }

    /*
     * @brief 工作线程中处理登出结果
     */
    void DoAccountLogOutDone(int errCode)
    {
        CallKitAccount logoutAccount = null;
        synchronized (mDataLock) {
            logoutAccount = mLocalAccount;

            if (ERR_NONE == errCode) {
                mLocalAccount = null;               // 清空本地账号
                mStateMachine = STATE_SDK_READY;    // 状态机切换到 SDK就绪 状态
                CallStateManager.getInstance().clearLocalUserInfo();  // 清空当前登录用户信息

            } else {
                mStateMachine = STATE_LOGGED_IDLE;  // 状态机切换回 登录空闲 状态
            }
        }

        ALog.getInstance().d(TAG, "<DoAccountLogOutDone> done");
        CallbackLogOutDone(logoutAccount, errCode);
    }

    /*
     * @brief 工作线程中处理账号异地登录事件，本地被强制登出结果
     */
    void DoAccountLogInOtherDev()
    {
        CallKitAccount logoutAccount = null;
        synchronized (mDataLock) {
            logoutAccount = mLocalAccount;
            mLocalAccount = null;               // 清空本地账号
            mStateMachine = STATE_SDK_READY;    // 状态机切换到 SDK就绪 状态
        }
        CallStateManager.getInstance().clearLocalUserInfo();  // 清空当前登录用户信息

        ALog.getInstance().d(TAG, "<DoAccountLogInOtherDev> done");
        CallbackLoginOtherDevice(logoutAccount);
    }

    /*
     * @brief 查询指定设备状态
     * @param devIdList 输入要查询的deviceId列表
     * @return 返回各个设备查询状态
     */
    ArrayList<CallKitAccount> queryDevicesStatus(List<String> devIdList) {
        //
        // 查询每个设备的uid
        //
        ArrayList<CallKitAccount> bindedDevList = new ArrayList<>();
        for (String deviceId : devIdList) {
            CallKitAccount queriedAccount = new CallKitAccount(deviceId, CallKitAccount.ACCOUNT_TYPE_DEV);
            // 查询account对应的UID值
            UidInfoBean info = HttpRequestInterface.getInstance().
                    queryUidWithAccount(deviceId, CallKitAccount.ACCOUNT_TYPE_DEV);
            if (info == null)  {
                int req_ret = HttpRequestInterface.getInstance().getLastErrorCode();
                if (HttpRequestInterface.RESULT_REQUST_PARAMES_FAILED == req_ret) {
                    ALog.getInstance().e(TAG, "<queryDevicesStatus> invalid deviceId: " + deviceId);
                    queriedAccount.setAccountValid(false);
                } else {
                    ALog.getInstance().e(TAG, "<queryDevicesStatus> fail to query device: " + deviceId);
                }
                queriedAccount.setUid(0);
            } else {
                queriedAccount.setUid(info.getUid());
                ALog.getInstance().d(TAG, "<queryDevicesStatus> device=" + deviceId + ", uid=" + info.getUid());
            }
            bindedDevList.add(queriedAccount);
        }


        //
        // 将uid转换成String类型，通过RTM查询每个设备在线状态
        //
        ArrayList<String> peerList = new ArrayList<>();
        Map<String, Boolean> peerStatus = new HashMap<>();
        AgoraRtmManager.getInstance().queryPeerStatus(bindedDevList);

        return bindedDevList;
    }




    /*
     * @brief 工作线程中处理设备绑定,这里暂时存储到本地，实际要与服务器进行交互
     */
    void DoDeviceBind(List<String> deviceIdList)
    {
        String accountId;
        synchronized (mDataLock) {
            accountId = mLocalAccount.getName();
        }

        int errCode = ERR_NONE;
        HttpRequestInterface httpReq = HttpRequestInterface.getInstance();
        ArrayList<CallKitAccount> bindedDevList = httpReq.bindDevicesToAccount(accountId, deviceIdList);
        if (bindedDevList == null) {
            errCode = ERR_HTTP_NOSERVICE;
        }
        if (bindedDevList == null) {
            bindedDevList = new ArrayList<>();
        }
        int ret = httpReq.getLastErrorCode();
        if (HttpRequestInterface.RESULT_OK != ret) {
            errCode = ret - 1000;
        }

        // 查询绑定设备的各个状态
        AgoraRtmManager.getInstance().queryPeerStatus(bindedDevList);

        synchronized (mDataLock) {
            mStateMachine = STATE_LOGGED_IDLE;  // 状态机切换到 登录空闲 状态
        }

        ALog.getInstance().d(TAG, "<DoDeviceBind> accountId =" + accountId + ", bindedCnt=" + bindedDevList.size());
        CallbackBindDeviceDone(mLocalAccount, bindedDevList, errCode);
    }

    /*
     * @brief 工作线程中处理设备解绑,这里暂时存储到本地，实际要与服务器进行交互
     */
    void DoDeviceUnbind(List<String> deviceIdList)
    {
        String accountId;
        synchronized (mDataLock) {
            accountId = mLocalAccount.getName();
        }

        int errCode = ERR_NONE;
        HttpRequestInterface httpReq = HttpRequestInterface.getInstance();
        ArrayList<CallKitAccount> bindedDevList = httpReq.unbindDevicesFromAccount(accountId, deviceIdList);
        if (bindedDevList == null) {
            bindedDevList = new ArrayList<>();
        }
        int ret = httpReq.getLastErrorCode();
        if (HttpRequestInterface.RESULT_OK != ret) {
            errCode = ret - 1000;
        }

        // 查询绑定设备的各个状态
        AgoraRtmManager.getInstance().queryPeerStatus(bindedDevList);

        synchronized (mDataLock) {
            mStateMachine = STATE_LOGGED_IDLE;  // 状态机切换到 登录空闲 状态
        }

        ALog.getInstance().d(TAG, "<DoDeviceUnbind> accountId =" + accountId + ", bindedCnt=" + bindedDevList.size());
        CallbackUnbindDeviceDone(mLocalAccount, bindedDevList, errCode);
    }

    /*
     * @brief 工作线程中处理查询当前账号绑定的设备列表
     */
    void DoDeviceQuery()
    {
        String accountId;
        synchronized (mDataLock) {
            accountId = mLocalAccount.getName();
        }

        int errCode = ERR_NONE;
        HttpRequestInterface httpReq = HttpRequestInterface.getInstance();
        ArrayList<CallKitAccount> bindedDevList = httpReq.queryBindDevicesByAccount(accountId);
        if (bindedDevList == null) {
            bindedDevList = new ArrayList<>();
        }
        int ret = httpReq.getLastErrorCode();
        if (HttpRequestInterface.RESULT_OK != ret) {
            errCode = ret - 1000;
        }
        ALog.getInstance().d(TAG, "<DoDeviceQuery> devIdList=" + combineAccountText(bindedDevList) );

        // 查询绑定设备的各个状态
        AgoraRtmManager.getInstance().queryPeerStatus(bindedDevList);

        synchronized (mDataLock) {
            mStateMachine = STATE_LOGGED_IDLE;  // 状态机切换到 登录空闲 状态
        }

        ALog.getInstance().d(TAG, "<DoDeviceQuery> accountId =" + accountId + ", bindedCnt=" + bindedDevList.size());
        CallbackQueryDevListDone(mLocalAccount, bindedDevList, errCode);
    }

    /*
     * @brief 工作线程中处理查询当前设备绑定的账号列表
     */
    void DoUserQuery()
    {
        String deviceId;
        synchronized (mDataLock) {
            deviceId = mLocalAccount.getName();
        }

        int errCode = ERR_NONE;
        HttpRequestInterface httpReq = HttpRequestInterface.getInstance();
        ArrayList<CallKitAccount> bindedUserList = httpReq.queryBindedAccountsByDevId(deviceId);
        if (bindedUserList == null) {
            bindedUserList = new ArrayList<>();
        }
        int ret = httpReq.getLastErrorCode();
        if (HttpRequestInterface.RESULT_OK != ret) {
            errCode = ret - 1000;
        }
        ALog.getInstance().d(TAG, "<DoUserQuery> devIdList=" + combineAccountText(bindedUserList) );

        // 查询绑定用户的各个状态
        AgoraRtmManager.getInstance().queryPeerStatus(bindedUserList);

        synchronized (mDataLock) {
            mStateMachine = STATE_LOGGED_IDLE;  // 状态机切换到 登录空闲 状态
        }

        ALog.getInstance().d(TAG, "<DoUserQuery> deivceId =" + deviceId + ", bindedCnt=" + bindedUserList.size());
        CallbackQueryUserListDone(mLocalAccount, bindedUserList, errCode);
    }

    /*
     * @brief 工作线程中进行主动呼叫处理
     */
    void DoCallDial(CallDialingInfo dialingInfo)
    {
        // 查询待呼叫UID信息
        List<UidInfoBean> uidList = queryAccountUid(dialingInfo.mAccountList);
        if ((uidList == null) || (uidList.isEmpty())) {
            ALog.getInstance().e(TAG, "<DoCallDial> fail to query account uid");
            synchronized (mDataLock) {
                mStateMachine = STATE_LOGGED_IDLE;  // 状态机切换回 登录空闲 状态
            }
            CallbackDialDone(mLocalAccount, dialingInfo.mAccountList, ERR_INVALID_UID);
            return;
        }

        // 发送呼叫请求
        if (!AgoraRtmManager.getInstance().sendNewCallMessage(uidList, dialingInfo.mAttachMsg)) {
            ALog.getInstance().e(TAG, "<DoCallDial> fail to sendNewCallMessage()");
            synchronized (mDataLock) {
                mStateMachine = STATE_LOGGED_IDLE;  // 状态机切换回 登录空闲 状态
            }
            CallbackDialDone(mLocalAccount, dialingInfo.mAccountList, ERR_NEW_CALL_FAILED);
            return;
        }

        // 等待对方接听
        ALog.getInstance().d(TAG, "<DoCallDial> done");
        CallbackDialDone(mLocalAccount, dialingInfo.mAccountList, ERR_NONE);
    }


    /*
     * @brief 呼叫响应 刚开始
     */
    void DoCallDialStart(CallResponseInfo responseInfo)
    {
        ALog.getInstance().d(TAG, "<DoCallDialStart>");

        // 注意: 进入频道是要以本地 localUid身份进入，这个调用会耗时
        mTalkEngine.joinChannel(responseInfo.mChannel, responseInfo.mToken,
                (int)(mLocalAccount.getUid()));
    }


    /*
     * @brief 呼叫响应 对端接听
     */
    void DoCallDialPeerAnswer(CallResponseInfo responseInfo)
    {
        ALog.getInstance().d(TAG, "<DoCallDialPeerAnswer>");

        // 删除呼叫超时消息
        mWorkHandler.removeMessages(MSGID_CALL_DIALRSP_TIMEOUT);

        mTalkEngine.setPeerUid((int)(mPeerAccount.getUid()));
        mTalkEngine.startTalking();  // 开始通话推流

        synchronized (mDataLock) {
            mStateMachine = STATE_CALL_TALKING;  // 状态机切换到 正常通话 状态
        }

        // 回调正常接通
        CallbackPeerAnswer(mLocalAccount);
    }

    /*
     * @brief 呼叫响应 对端忙
     */
    void DoCallDialPeerBusy()
    {
        ALog.getInstance().d(TAG, "<DoCallDialPeerBusy>");

        // 删除呼叫超时消息
        mWorkHandler.removeMessages(MSGID_CALL_DIALRSP_TIMEOUT);

        mTalkEngine.stopTalking();  // 退出频道并且结束通话
        mTalkEngine.leaveChannel();
        synchronized (mDataLock) {
            mStateMachine = STATE_LOGGED_IDLE;  // 状态机切换回 登录空闲 状态
        }

        // 回调对端忙音
        CallbackPeerBusy(mLocalAccount);
    }

    /*
     * @brief 呼叫响应 对端直接挂断
     */
    void DoCallDialPeerHangup()
    {
        ALog.getInstance().d(TAG, "<DoCallDialPeerHangup>");

        // 删除呼叫超时消息
        mWorkHandler.removeMessages(MSGID_CALL_DIALRSP_TIMEOUT);


        mTalkEngine.stopTalking();  // 退出频道并且结束通话
        mTalkEngine.leaveChannel();
        synchronized (mDataLock) {
            mStateMachine = STATE_LOGGED_IDLE;  // 状态机切换回 登录空闲 状态
        }

        // 回调对端挂断
        CallbackPeerHangup(mLocalAccount);
    }

    /*
     * @brief 呼叫响应 对端超时无响应
     */
    void DoCallDialPeerTimeout()
    {
        ALog.getInstance().d(TAG, "<DoCallDialPeerTimeout> ");

        // 删除呼叫超时消息
        mWorkHandler.removeMessages(MSGID_CALL_DIALRSP_TIMEOUT);

        // Callback中超时处理
        CallbackManager.getInstance().onReceiveCallTimeout();

        mTalkEngine.stopTalking();  // 退出频道并且结束通话
        mTalkEngine.leaveChannel();
        synchronized (mDataLock) {
            mStateMachine = STATE_LOGGED_IDLE;  // 状态机切换回 登录空闲 状态
        }

        // 回调对端挂断
        CallbackPeerTimeout(mLocalAccount);
    }

    /*
     * @brief 工作线程中进行被叫超时处理
     */
    void DoInCallTimeout()
    {
        ALog.getInstance().d(TAG, "<DoInCallTimeout> ");

        mTalkEngine.stopTalking();  // 退出频道并且结束通话
        mTalkEngine.leaveChannel();

        synchronized (mDataLock) {
            mStateMachine = STATE_LOGGED_IDLE;  // 状态机切换回 登录空闲 状态
        }

        // 回调对端挂断
        CallbackPeerTimeout(mLocalAccount);
    }

    /*
     * @brief 工作线程中进行挂断处理
     */
    void DoCallHangup() {
        long session = CallStateManager.getInstance().getCurSessionId();
        long uid = CallStateManager.getInstance().getRemoteUser().getUid();
        List<UidInfoBean> users = CallStateManager.getInstance().getNoResponseRemoteUsers();
        ALog.getInstance().d(TAG, "<DoCallHangup> remoteUid=" + uid + ", noRespUsers=" + users.size() );

        if (CallStateManager.getInstance().refuseCall()) {
            if (uid > 0) {
                //发送拒绝或取消通话消息给通话对端
                AgoraRtmManager.getInstance().sendCallKissChoiceMessage(
                        session, uid, AgoraRtmManager.CALL_KISS_CHOICE_HANGUP);
            } else {
                //如果还没有等到接听主动挂断一对多呼叫中还未响应的对端
                ALog.getInstance().d(TAG, "users : " + users.size());
                for (int i = 0; i < users.size(); i++) {
                    ALog.getInstance().d(TAG, "user " + i + " UID : " + users.get(i).getUid());
                    AgoraRtmManager.getInstance().sendCallKissChoiceMessage(
                            session, users.get(i).getUid(), AgoraRtmManager.CALL_KISS_CHOICE_HANGUP);
                }
            }
        }

        mTalkEngine.stopTalking();  // 退出频道并且结束通话
        mTalkEngine.leaveChannel();

        synchronized (mDataLock) {
            mStateMachine = STATE_LOGGED_IDLE;  // 状态机切换回 登录空闲 状态
        }
        synchronized (mLocalHangupEvent) {
            mLocalHangupEvent.notify();    // 事件通知
        }

        ALog.getInstance().d(TAG, "<DoCallHangup> done");
    }

    /*
     * @brief 工作线程中进行接听处理
     */
    void DoCallAnswer()
    {
        ALog.getInstance().d(TAG, "<DoCallAnswer> enter");
        if (CallStateManager.getInstance().answerCall()) {
            //发送接听通话消息给通话对端
            AgoraRtmManager.getInstance().sendCallKissChoiceMessage(
                    CallStateManager.getInstance().getCurSessionId(),
                    CallStateManager.getInstance().getRemoteUser().getUid(),
                    AgoraRtmManager.CALL_KISS_CHOICE_ANSER);

        }
        mTalkEngine.startTalking();  // 开始通话推流，此前来电时应该已经进入频道了


        synchronized (mDataLock) {
            mStateMachine = STATE_CALL_TALKING;  // 状态机切换到 正常通话 状态
        }
        synchronized (mLocalAnswerEvent) {
            mLocalAnswerEvent.notify();    // 事件通知
        }
        ALog.getInstance().d(TAG, "<DoCallAnswer> exit");
    }

    /*
     * @brief 工作线程中 处理通话过程中的挂断处理
     */
    void DoTalkingHangup()
    {
        mTalkEngine.stopTalking();  // 退出频道并且结束通话
        mTalkEngine.leaveChannel();

        synchronized (mDataLock) {
            mStateMachine = STATE_LOGGED_IDLE;  // 状态机切换回 登录 空闲状态
        }

        ALog.getInstance().d(TAG, "<DoTalkingHangup> done");
        CallbackPeerHangup(mLocalAccount);
    }

    /*
     * @brief 工作线程中 处理来电呼叫
     */
    void DoCallIncoming(CallIncomingInfo incomingInfo)
    {
        // 注意: 本地端要以 localUid主动加入频道
        mTalkEngine.joinChannel(incomingInfo.mChannel, incomingInfo.mToken,
                        (int)(mLocalAccount.getUid()));
        mTalkEngine.setPeerUid((int)(incomingInfo.mUid));

        synchronized (mDataLock) {
            mStateMachine = STATE_CALL_INCOMING;  // 状态机切换到 来电呼叫 状态
        }

        mPeerAccount = new CallKitAccount(incomingInfo.mAccountName, incomingInfo.mAccountType);
        mPeerAccount.setUid(incomingInfo.mUid);
        mPeerAccount.setOnline(true);


        // 发送来电后操作超时处理
        Message msg = new Message();
        msg.what = MSGID_CALL_INCOME_OPTTIMEOUT;
        msg.obj = incomingInfo;
        mWorkHandler.removeMessages(MSGID_CALL_INCOME_OPTTIMEOUT);
        mWorkHandler.sendMessageDelayed(msg, INCOMING_OPT_TIMEOUT);

        ALog.getInstance().d(TAG, "<DoCallIncoming> done");
        CallbackPeerIncoming(mLocalAccount, mPeerAccount, incomingInfo.mAttachMsg);
    }

    /*
     * @brief 工作线程中 来电后超时未接听、本地挂断、对端挂断
     */
    void DoCallIncomeOptTimeout(CallIncomingInfo incomingInfo) {
        // 不管对端是否在线，总是发送挂断消息给对端
        long session = CallStateManager.getInstance().getCurSessionId();
        long uid = CallStateManager.getInstance().getRemoteUser().getUid();
        List<UidInfoBean> users = CallStateManager.getInstance().getNoResponseRemoteUsers();
          if (CallStateManager.getInstance().refuseCall()) {
            if (uid > 0) {
                //发送拒绝或取消通话消息给通话对端
                AgoraRtmManager.getInstance().sendCallKissChoiceMessage(
                        session, uid, AgoraRtmManager.CALL_KISS_CHOICE_HANGUP);
            } else {
                //如果还没有等到接听主动挂断一对多呼叫中还未响应的对端
                for (int i = 0; i < users.size(); i++) {
                    AgoraRtmManager.getInstance().sendCallKissChoiceMessage(
                            session, users.get(i).getUid(), AgoraRtmManager.CALL_KISS_CHOICE_HANGUP);
                }
            }
        }

        mTalkEngine.stopTalking();  // 退出频道并且结束通话
        mTalkEngine.leaveChannel();

        synchronized (mDataLock) {
            mStateMachine = STATE_LOGGED_IDLE;  // 状态机切换回 登录空闲 状态
        }

        ALog.getInstance().d(TAG, "<DoCallIncomeOptTimeout> done");

        // 回调对端挂断
        CallbackPeerTimeout(mLocalAccount);
    }

    /*
     * @brief 工作线程中 主叫 或者 被叫 或者 通话 过程中对端RTC离线，直接挂断处理
     */
    void DoCallPeerInterrupt() {
        // 不管对端是否在线，总是发送挂断消息给对端
        long session = CallStateManager.getInstance().getCurSessionId();
        long uid = CallStateManager.getInstance().getRemoteUser().getUid();
        List<UidInfoBean> users = CallStateManager.getInstance().getNoResponseRemoteUsers();
        if (CallStateManager.getInstance().refuseCall()) {
            if (uid > 0) {
                //发送拒绝或取消通话消息给通话对端
                AgoraRtmManager.getInstance().sendCallKissChoiceMessage(
                        session, uid, AgoraRtmManager.CALL_KISS_CHOICE_HANGUP);
            } else {
                //如果还没有等到接听主动挂断一对多呼叫中还未响应的对端
                for (int i = 0; i < users.size(); i++) {
                    AgoraRtmManager.getInstance().sendCallKissChoiceMessage(
                            session, users.get(i).getUid(), AgoraRtmManager.CALL_KISS_CHOICE_HANGUP);
                }
            }
        }

        mTalkEngine.stopTalking();  // 退出频道并且结束通话
        mTalkEngine.leaveChannel();

        synchronized (mDataLock) {
            mStateMachine = STATE_LOGGED_IDLE;  // 状态机切换回 登录空闲 状态
        }

        ALog.getInstance().d(TAG, "<DoCallPeerInterrupt> done");

        // 回调对端挂断
        CallbackPeerHangup(mLocalAccount);
    }

    /*
     * @brief 工作线程中 设置本地视频显示控件
     */
    void DoSetupLocalView(int localUid) {
        SurfaceView localView;
        synchronized (mDataLock) {
            localView = mLocalVideoView;
        }

        mTalkEngine.setLocalVideoView(localView, localUid);
        ALog.getInstance().d(TAG, "<DoSetupLocalView> done");

//        synchronized (mSetLocalViewEvent) {
//            mSetLocalViewEvent.notify();    // 事件通知
//        }
    }

    /*
     * @brief 工作线程中 设置对端视频显示控件
     */
    void DoSetupPeerView(int peerUid) {
        SurfaceView peerVidew;
        synchronized (mDataLock) {
            peerVidew = mPeerVideoView;
        }

        mTalkEngine.setRemoteVideoView(peerVidew, peerUid);
        ALog.getInstance().d(TAG, "<DoSetupPeerView> done");

//        synchronized (mSetPeerViewEvent) {
//            mSetPeerViewEvent.notify();    // 事件通知
//        }
    }

    /*
     * @brief 工作线程中 接收到对端的消息
     */
    void DoPeerMessage(String message) {
        ALog.getInstance().d(TAG, "<DoPeerMessage> message=" + message);
        CallbackPeerMessage(mLocalAccount, message);
    }

    /*
     * @brief 工作线程中 发送消息到对端
     */
    void DoSendMessage(String message) {
        long caller = CallStateManager.getInstance().getRemoteUser().getUid();

        if (!AgoraRtmManager.getInstance().sendCustomizeMessage(caller, message)) {
            ALog.getInstance().e(TAG, "<DoSendMessage> fail to sendCustomizeMessage()");
            return;
        }

        ALog.getInstance().d(TAG, "<DoSendMessage> message=" + message);
    }

    /*
     * @brief 工作线程中 请求云录制，响应数据会同步异步回调接口过来
     */
    void DoCloudRecordRequest(String alarmMessage) {

        mTriggerAlarmMsg = alarmMessage;

        if (!AgoraRtmManager.getInstance().sendCloudRecordingRequest(mLocalAccount.getUid())) {
            ALog.getInstance().e(TAG, "<DoCloudRecordRequest> fail to sendCloudRecordingRequest()");
            synchronized (mDataLock) {
                mStateMachine = STATE_LOGGED_IDLE;  // 状态机切换回 登录空闲 状态
            }
            return;
        }

        ALog.getInstance().d(TAG, "<DoCloudRecordRequest> alarmMessage=" + alarmMessage);
    }


    /*
     * @brief 工作线程中 启动云录制
     */
    void  DoCloudRecordStart(CloudRecordInfo recordInfo) {
        synchronized (mDataLock) {
            mStateMachine = STATE_CLOUDRCD_RECORDING;  // 状态机切换到 云录制正在进行 状态
        }

        //
        // 本地端要以 (devicTok+localUid) 加入云录频道
        //
        boolean ret = mTalkEngine.joinChannel(recordInfo.mRecordChannel, recordInfo.mDeviceToken,
                                                (int)(mLocalAccount.getUid()));
        if (!ret)    {
            ALog.getInstance().e(TAG, "<DoCloudRecordStart> fail to join record channel"
                    + ", mRecordChannel=" + recordInfo.mRecordChannel
                    + ", mCloudToken=" + recordInfo.mCloudToken
                    + ", mCloudUid=" + recordInfo.mCloudUid);
            synchronized (mDataLock) {
                mStateMachine = STATE_LOGGED_IDLE;  // 状态机切换回 登录空闲 状态
            }
            // 回调云录制开始失败
            CallbackCloudRecordingStart(mLocalAccount, ERR_SERVER_FAILED);
            return;
        }
        mTalkEngine.setPeerUid(0);


        //
        // 请求获取云录制的resourceId
        //
        HttpRequestInterface httpReq = HttpRequestInterface.getInstance();
        recordInfo.mRecordResId = httpReq.cloudRecordGetResourceId(recordInfo.mRecordChannel,
                                                                    recordInfo.mCloudUid);
        if (recordInfo.mRecordResId == null || recordInfo.mRecordResId.length() <= 0) {
            ALog.getInstance().e(TAG, "<DoCloudRecordStart> fail to get resourceId"
                    + ", mRecordChannel=" + recordInfo.mRecordChannel
                    + ", mCloudUid=" + recordInfo.mCloudUid);
            mTalkEngine.leaveChannel();
            synchronized (mDataLock) {
                mStateMachine = STATE_LOGGED_IDLE;  // 状态机切换回 登录空闲 状态
            }
            // 回调云录制开始失败
            CallbackCloudRecordingStart(mLocalAccount, ERR_SERVER_FAILED);
            return;
        }

        //
        // 请求云录制开始，返回云录的Sid
        //
        HttpRequestInterface.CloudRecordParam recordParam = new HttpRequestInterface.CloudRecordParam();
        recordParam.mRecordChannel = recordInfo.mRecordChannel;
        recordParam.mRecordUid = recordInfo.mCloudUid;
        recordParam.mRecordToken = recordInfo.mCloudToken;
        recordParam.mResourceId = recordInfo.mRecordResId;
        recordParam.mVideoWidth = mTalkEngine.getVideoWidth();
        recordParam.mVideoHeight = mTalkEngine.getVideoHeight();
        recordParam.mFrameRate = mTalkEngine.getFrameRate();
        recordParam.mBitrate = mTalkEngine.getBitrate();
        recordParam.mVendorId = CLOUD_RECORD_VENDER_ID;
        recordParam.mRegion = CLOUD_RECORD_REGION;
        recordParam.mBucket = CLOUD_RECORD_BUCKET;
        recordParam.mAccessKey = CLOUD_RECORD_ACCESSKEY;
        recordParam.mSecretKey = CLOUD_RECORD_SECRETKEY;
        recordInfo.mRecordSid = httpReq.cloudRecordStart(recordParam);
        if (recordInfo.mRecordSid == null || recordInfo.mRecordSid.length() <= 0) {
            ALog.getInstance().e(TAG, "<DoCloudRecordStart> fail to start recording"
                    + ", mRecordChannel=" + recordInfo.mRecordChannel
                    + ", mCloudUid=" + recordInfo.mCloudUid);
            mTalkEngine.leaveChannel();  // 退出云录频道
            synchronized (mDataLock) {
                mStateMachine = STATE_LOGGED_IDLE;  // 状态机切换回 登录空闲 状态
            }
            // 回调云录制开始失败
            CallbackCloudRecordingStart(mLocalAccount, ERR_SERVER_FAILED);
            return;
        }

        //
        // 获取本地设备绑定的用户账号
        //
        long deviceUid = mLocalAccount.getUid();
        String warnMessage = mTriggerAlarmMsg;
        ArrayList<CallKitAccount> bindedUserList = httpReq.queryBindedAccountsByDevId(mLocalAccount.getName());
        if (bindedUserList != null && bindedUserList.size() > 0) {
            // 获取所有绑定账号的uid
            ArrayList<Long> listenIdList = new ArrayList<>();
            for (int i = 0; i < bindedUserList.size(); i++) {
                listenIdList.add(bindedUserList.get(i).getUid());
            }

            //
            // 发送告警消息并且回调发送结果
            //
            int errCode = ERR_NONE;
            if (!AgoraRtmManager.getInstance().sendAlarmRequest(deviceUid, recordInfo.mRecordChannel,
                    recordInfo.mRecordSid, listenIdList, warnMessage)) {
                ALog.getInstance().d(TAG, "<DoCloudRecordStart> fail to sendAlarmRequest()");
                errCode = ERR_SERVER_FAILED;
            }

            //
            // 给服务器发送告警信息
            //
            int reportRet = httpReq.alarmReport(deviceUid, 1, warnMessage, recordInfo.mRecordChannel,
                    recordInfo.mRecordSid, listenIdList);

            CallbackAlarmSendDone(mLocalAccount, mTriggerAlarmMsg, errCode);
        }


        // 20秒后停止云录制
        Message msg = new Message();
        msg.what = MSGID_CLOUDRECORD_STOP;
        msg.obj = recordInfo;
        mWorkHandler.removeMessages(MSGID_CLOUDRECORD_STOP);
        mWorkHandler.sendMessageDelayed(msg, CLOUD_RECORD_LENGTH);
        ALog.getInstance().d(TAG, "<DoCloudRecordStart>");

        // 回调云录制开始
        CallbackCloudRecordingStart(mLocalAccount, ERR_NONE);
    }

    /*
     * @brief 工作线程中 停止云录制
     */
    void  DoCloudRecordStop(CloudRecordInfo recordInfo) {
        //
        // 请求云录制结束，返回云录的文件列表
        //
        HttpRequestInterface httpReq = HttpRequestInterface.getInstance();
        int errCode = ERR_NONE;

        CloudRecordResult recordResult = httpReq.cloudRecordStop(recordInfo.mRecordChannel,
                recordInfo.mCloudUid, recordInfo.mRecordResId, recordInfo.mRecordSid, false);
        if (recordResult == null) {
            ALog.getInstance().e(TAG, "<DoCloudRecordStop> fail to stop resourceId"
                    + ", mRecordChannel=" + recordInfo.mRecordChannel
                    + ", mCloudUid=" + recordInfo.mCloudUid);
            errCode = ERR_SERVER_FAILED;
          }
        mTalkEngine.leaveChannel(); // 退出云录频道
        synchronized (mDataLock) {
            mStateMachine = STATE_LOGGED_IDLE;  // 状态机切换回 登录空闲 状态
        }

        // 回调云录制结束
        CallbackCloudRecordingStop(mLocalAccount, recordResult, errCode);
        ALog.getInstance().d(TAG, "<DoCloudRecordStop> done");
    }

    /*
     * @brief 工作线程中 接收到设备端的告警消息
     */
    void DoAlarmReceived(AlarmRecord alarmRecord) {
        String timeText = getTimeText(alarmRecord.mTimestamp);
        ALog.getInstance().d(TAG, "<AlarmRecord> peerDevId=" + alarmRecord.mDeviceId
            + ", peeruid=" + alarmRecord.mUid
            + ", message=" + alarmRecord.mMessage
            + ", time=" + timeText);

        // 回调接收到告警消息
        CallKitAccount peerAccount = new CallKitAccount(alarmRecord.mDeviceId, CallKitAccount.ACCOUNT_TYPE_DEV );
        peerAccount.setUid(alarmRecord.mUid);
        CallbackAlarmReceived(mLocalAccount, peerAccount, alarmRecord.mTimestamp, alarmRecord.mMessage);
    }

    /*
     * @brief 工作线程中 接收到设备端的告警消息，针对Apical项目
     */
    void DoIotAlarmReceived(IotAlarm iotAlarm) {

        ALog.getInstance().d(TAG, "<DoIotAlarmReceived> iotAlarm=" + iotAlarm.toString());


        // 回调接收到告警消息
        CallKitAccount peerAccount = new CallKitAccount(iotAlarm.mDeviceId, CallKitAccount.ACCOUNT_TYPE_DEV );
        peerAccount.setUid(iotAlarm.mDeviceUid);
        CallbackIotAlarmReceived(mLocalAccount, peerAccount, iotAlarm);
    }


    /*
     * @brief 工作线程中 从服务器查询告警信息
     */
    void DoAlarmQuery(AlarmQueryParam queryParam) {
        //
        // 先从服务器查询绑定的设备Uid
        //
        ArrayList<String> devIdList = new ArrayList<>();

        String accountId;
        synchronized (mDataLock) {
            accountId = mLocalAccount.getName();
        }
        HttpRequestInterface httpReq = HttpRequestInterface.getInstance();
        ArrayList<CallKitAccount> bindedDevList = httpReq.queryBindDevicesByAccount(accountId);
        if (bindedDevList != null) {
            for (int i = 0; i < bindedDevList.size(); i++) {
                CallKitAccount account = bindedDevList.get(i);
                devIdList.add(account.getName());
            }
        }

        //
        // 从服务器请求告警记录
        //
        ArrayList<IotAlarm> alarmList = httpReq.alarmQuery(devIdList,
                queryParam.mYear, queryParam.mMonth, queryParam.mDay,
                queryParam.mPageIndex, queryParam.mPageSize, queryParam.mType);
        ALog.getInstance().d(TAG, "<DoAlarmQuery> "
                + ", date=" + queryParam.mYear + "-" + queryParam.mMonth + "-" + queryParam.mDay
                + ", pageIndex=" + queryParam.mPageIndex
                + ", pageSize=" + queryParam.mPageSize
                + ", type=" + queryParam.mType
                + ", queriedCount=" + alarmList.size());

        synchronized (mDataLock) {
            mStateMachine = STATE_LOGGED_IDLE;  // 状态机切换回 登录空闲 状态
        }


        // 回调告警查询结果
        CallbackAlarmQueried(mLocalAccount, queryParam, alarmList);
    }


    /*
     * @brief 根据 设备/用户账号 同步查询uid
     */
    private List<UidInfoBean> queryAccountUid(List<CallKitAccount> accountList) {
        List<UidInfoBean> uidList = new ArrayList<UidInfoBean>();
        int count = accountList.size();
        int i;

        for (i = 0; i < count; i++)
        {
            CallKitAccount account = accountList.get(i);

            //查询account对应的UID值
            UidInfoBean info = HttpRequestInterface.getInstance()
                    .queryUidWithAccount(account.getName(), account.getType());
            if (info == null) {
                ALog.getInstance().e(TAG, "<queryAccountUid> ");
                return null;
            }

            // 去除重复 Uid
            if (queryUidIndexInList(uidList, info.getUid()) < 0) {
                uidList.add(info);
            }
        }

        return uidList;
    }

    /*
     * @brief 查询UID在列表中的顺序
     * @param list : 查询的列表
     * @param uid : 要查询的uid
     * @return 查询到的索引位置，-1表示没有查询到
     */
    private int queryUidIndexInList(List<UidInfoBean> list, long uid) {
        for (int i = 0; i < list.size(); i++) {
            if (uid == list.get(i).getUid()) {
                return i;
            }
        }
        return -1;
    }

    ////////////////////////////////////////////////////////////////////////////
    ////////////////////// Override AgoraCallNotify Methods ////////////////////
    ////////////////////////////////////////////////////////////////////////////
    @Override
    public void onLoginSuccess() {
        synchronized (mDataLock) {
            if (mStateMachine != STATE_LOGIN_ONGOING) {
                ALog.getInstance().e(TAG, "<onLoginSuccess> bad state, mStateMachine=" + mStateMachine);
            }
        }

        ALog.getInstance().d(TAG, "<onLoginSuccess>");
        Message msg = new Message();
        msg.what = MSGID_LOGIN_DONE;
        msg.arg1 = ERR_NONE;
        mWorkHandler.removeMessages(MSGID_LOGIN_DONE);
        mWorkHandler.sendMessage(msg);
    }

    @Override
    public void onLoginFailed(int errorCode) {
        synchronized (mDataLock) {
            if (mStateMachine != STATE_LOGIN_ONGOING) {
                ALog.getInstance().e(TAG, "<onLoginFailed> bad state, mStateMachine=" + mStateMachine);
            }
        }

        ALog.getInstance().d(TAG, "<onLoginFailed> errorCode=" + errorCode);
        Message msg = new Message();
        msg.what = MSGID_LOGIN_DONE;
        msg.arg1 = errorCode;
        mWorkHandler.removeMessages(MSGID_LOGIN_DONE);
        mWorkHandler.sendMessage(msg);
    }

    @Override
    public void onLogoutSuccess() {
        synchronized (mDataLock) {
            if (mStateMachine != STATE_LOGOUT_ONGOING) {
                ALog.getInstance().e(TAG, "<onLogoutSuccess> bad state, mStateMachine=" + mStateMachine);
                return;
            }
        }

        ALog.getInstance().d(TAG, "<onLogoutSuccess>");
        Message msg = new Message();
        msg.what = MSGID_LOGOUT_DONE;
        msg.arg1 = ERR_NONE;
        mWorkHandler.removeMessages(MSGID_LOGOUT_DONE);
        mWorkHandler.sendMessage(msg);
    }

    @Override
    public void onLogoutFailed(int errorCode) {
        synchronized (mDataLock) {
            if (mStateMachine != STATE_LOGOUT_ONGOING) {
                ALog.getInstance().e(TAG, "<onLogoutFailed> bad state, mStateMachine=" + mStateMachine);
                return;
            }
        }

        ALog.getInstance().d(TAG, "<onLogoutFailed> errorCode=" + errorCode);
        Message msg = new Message();
        msg.what = MSGID_LOGOUT_DONE;
        msg.arg1 = errorCode;
        mWorkHandler.removeMessages(MSGID_LOGOUT_DONE);
        mWorkHandler.sendMessage(msg);
    }

    @Override
    public void onLoginOtherDevice() {
        ALog.getInstance().d(TAG, "<onLoginOtherDevice> ");

        // 通话状态设置为挂断
        if (CallStateManager.getInstance().refuseCall()) {
            // mExitCallWaiting = true;
        }

        // 注销RTM
        AgoraRtmManager.getInstance().logout();

        // 注销IM
        EMClientManager.getInstance().logout();

        Message msg = new Message();
        msg.what = MSGID_LOGIN_OTHERDEV;
        mWorkHandler.removeMessages(MSGID_LOGIN_OTHERDEV);
        mWorkHandler.sendMessage(msg);
    }

    @Override
    public void onCallDialing(String channel, String token, long uid) {
        ALog.getInstance().d(TAG, "<onCallDialing> channel=" + channel
                + ", token=" + token + ", uid=" + uid);
        synchronized (mDataLock) {
            if (mStateMachine != STATE_CALL_DIALING) {  // 当前正常应该在拨号状态
                ALog.getInstance().e(TAG, "<onCallAnswer> bad state, mStateMachine=" + mStateMachine);
                return;
            }
        }

        // 发送主动呼叫的回应
        Message msg = new Message();
        msg.what = MSGID_CALL_DIALRSP_START;
        msg.obj = new CallResponseInfo(channel, token, uid);
        mWorkHandler.removeMessages(MSGID_CALL_DIALRSP_START);
        mWorkHandler.sendMessage(msg);
    }

    @Override
    public void onCallAnswer(String channel, String token, long uid, UidInfoBean peerInfo) {
        ALog.getInstance().d(TAG, "<onCallAnswer> channel=" + channel
                    + ", token=" + token + ", uid=" + uid + ", peerInfo=" + peerInfo.toString());
        synchronized (mDataLock) {
            if (mStateMachine != STATE_CALL_DIALING) {  // 当前正常应该在拨号状态
                ALog.getInstance().e(TAG, "<onCallAnswer> bad state, mStateMachine=" + mStateMachine);
                return;
            }

            // 设置对端账号信息
            mPeerAccount = new CallKitAccount(peerInfo.getAccount(), peerInfo.getType());
            mPeerAccount.setUid((int)peerInfo.getUid());
            mPeerAccount.setOnline(true);
        }

        // 发送对端接听的回应
        Message msg = new Message();
        msg.what = MSGID_CALL_DIALRSP_ANSWER;
        msg.obj = new CallResponseInfo(channel, token, uid);
        mWorkHandler.removeMessages(MSGID_CALL_DIALRSP_ANSWER);
        mWorkHandler.sendMessage(msg);
    }

    @Override
    public void onCallBusy() {
        ALog.getInstance().d(TAG, "<onCallBusy>");
        synchronized (mDataLock) {
            if (mStateMachine != STATE_CALL_DIALING) {  // 当前正常应该在拨号状态
                ALog.getInstance().e(TAG, "<onCallBusy> bad state, mStateMachine=" + mStateMachine);
                return;
            }
        }

        // 发送对端忙的回应
        Message msg = new Message();
        msg.what = MSGID_CALL_DIALRSP_BUSY;
        mWorkHandler.removeMessages(MSGID_CALL_DIALRSP_BUSY);
        mWorkHandler.sendMessage(msg);
    }

    @Override
    public void onCallHangup() {
        int currStateMachine;
        synchronized (mDataLock) {
            currStateMachine = mStateMachine;
        }
        ALog.getInstance().d(TAG, "<onCallHangup> currStateMachine=" + currStateMachine);

        // 清除来电后操作超时
        mWorkHandler.removeMessages(MSGID_CALL_INCOME_OPTTIMEOUT);

        switch (currStateMachine)
        {
            case STATE_CALL_DIALING:    // 当前正常拨号中，对方挂断拒绝
            {
                // 发送对端挂断拒绝的回应
                Message msg = new Message();
                msg.what = MSGID_CALL_DIALRSP_HANGUP;
                mWorkHandler.removeMessages(MSGID_CALL_DIALRSP_HANGUP);
                mWorkHandler.sendMessage(msg);
            } break;

            case STATE_CALL_TALKING:    // 当前正在通话中挂断了
            case STATE_CALL_INCOMING:   // 当前在来电中挂断了
            {
                // 发送通话中挂断的消息
                Message msg = new Message();
                msg.what = MSGID_CALL_TALKHANGUP;
                mWorkHandler.removeMessages(MSGID_CALL_TALKHANGUP);
                mWorkHandler.sendMessage(msg);
            } break;

            default:                    // 其他状态下不用关注
                break;
        }

    }


    @Override
    public void onCallTimeout() {
        int currStateMachine;
        synchronized (mDataLock) {
            currStateMachine = mStateMachine;
        }
        ALog.getInstance().d(TAG, "<onCallTimeout> currStateMachine=" + currStateMachine);

        // 清除来电后操作超时
        mWorkHandler.removeMessages(MSGID_CALL_INCOME_OPTTIMEOUT);

        // 发送来电超时处理
        if (STATE_CALL_INCOMING == currStateMachine) {
            Message msg = new Message();
            msg.what = MSGID_CALL_INCOMETIMEOUT;
            mWorkHandler.removeMessages(MSGID_CALL_INCOMETIMEOUT);
            mWorkHandler.sendMessage(msg);
        }
    }

    @Override
    public void onReceiveCall(int caller_type, String caller_account,
                              String channel, String token, long uid, String attachMsg) {
        ALog.getInstance().d(TAG, "<onReceiveCall> caller_type=" + caller_type
            + ", caller_account=" + caller_account + ", channel=" + channel
            + ", token=" + token + ", uid=" + uid + ", attachMsg=" + attachMsg );
        synchronized (mDataLock) {
            if (mStateMachine != STATE_LOGGED_IDLE) {  // 仅在登录空闲状态下处理
                ALog.getInstance().e(TAG, "<onReceiveCall> bad state, mStateMachine=" + mStateMachine);
                return;
            }
        }

        // 清除来电后操作超时
        mWorkHandler.removeMessages(MSGID_CALL_INCOME_OPTTIMEOUT);

        CallIncomingInfo incomingInfo = new CallIncomingInfo();
        incomingInfo.mAccountName = caller_account;
        incomingInfo.mAccountType = caller_type;
        incomingInfo.mUid = uid;
        incomingInfo.mChannel = channel;
        incomingInfo.mToken = token;
        incomingInfo.mAttachMsg = attachMsg;

        // 发送来电呼叫消息
        Message msg = new Message();
        msg.what = MSGID_CALL_INCOMING;
        msg.obj = incomingInfo;
        mWorkHandler.removeMessages(MSGID_CALL_INCOMING);
        mWorkHandler.sendMessage(msg);
    }

    @Override
    public void onReceiveCustomizeMessage(String customizeMsg) {
        ALog.getInstance().d(TAG, "<onReceiveCustomizeMessage> customizeMsg=" + customizeMsg);

        // 发送对端短消息事件
        Message msg = new Message();
        msg.what = MSGID_PEER_MESSAGE;
        msg.obj = customizeMsg;
        mWorkHandler.removeMessages(MSGID_PEER_MESSAGE);
        mWorkHandler.sendMessage(msg);
    }

    @Override
    public void onReceiveCloudRecordResp(String recordChannel, String deviceToken, String cloudToken,
                                   long cloudUid) {
        ALog.getInstance().d(TAG, "<onReceiveCloudRecordResp> recordChannel=" + recordChannel
                + ", deviceToken=" + deviceToken + ", cloudToken=" + cloudToken
                + ", couldUid=" + cloudUid  );

        synchronized (mDataLock) {
            if (STATE_CLOUDRCD_REQUESTING != mStateMachine) {
                ALog.getInstance().e(TAG, "<onReceiveCloudRecordResp> bad state, mStateMachine=" + mStateMachine);
                return;
            }
        }

        CloudRecordInfo recordInfo = new CloudRecordInfo();
        recordInfo.mRecordChannel = recordChannel;
        recordInfo.mDeviceToken = deviceToken;
        recordInfo.mCloudToken = cloudToken;
        recordInfo.mCloudUid = cloudUid;

        // 发送启动云录制消息
        Message msg = new Message();
        msg.what = MSGID_CLOUDRECORD_START;
        msg.obj = recordInfo;
        mWorkHandler.removeMessages(MSGID_CLOUDRECORD_START);
        mWorkHandler.sendMessage(msg);
    }

    @Override
    public void onReceiveAlarmMessage(AlarmMessage alarmMessage) {
        ALog.getInstance().d(TAG, "<onReceiveAlarmMessage> alarmMessage=" + alarmMessage.toString());

        //
        // 直接保存到数据库记录中，不影响当前状态机
        //
        AlarmRecord newRecord = new AlarmRecord();
        newRecord.mTimestamp = alarmMessage.getTimestamp();
        newRecord.mDeviceId = alarmMessage.getDeviceId();
        newRecord.mUid = alarmMessage.getDeviceUid();
        newRecord.mType = alarmMessage.getType();
        newRecord.mPriority = alarmMessage.getPriority();
        newRecord.mVideoUrl = alarmMessage.getVideoUrl();
        newRecord.mMessage = alarmMessage.getMessage();

        ArrayList<AlarmRecord> recordList = new ArrayList<>();
        recordList.add(newRecord);
        mAlarmDbMgr.insertRecords(recordList);

        // 发送接收到远程告警消息
        Message msg = new Message();
        msg.what = MSGID_ALARM_RECEIVED;
        msg.obj = newRecord;
        mWorkHandler.removeMessages(MSGID_ALARM_RECEIVED);
        mWorkHandler.sendMessage(msg);
    }

    @Override
    public void onReceiveIotAlarm(IotAlarm iotAlarm) {
        ALog.getInstance().d(TAG, "<onReceiveDeviceAlarm>");

        // 发送接收到设备端告警消息
        Message msg = new Message();
        msg.what = MSGID_IOTALARM_RECVED;
        msg.obj = iotAlarm;
        mWorkHandler.removeMessages(MSGID_IOTALARM_RECVED);
        mWorkHandler.sendMessage(msg);
    }


    ////////////////////////////////////////////////////////////////////////////
    /////////////// Override TalkingEngine.ICallback Methods ////////////////////
    ////////////////////////////////////////////////////////////////////////////
    @Override
    public void onTalkingPeerJoined(int localUid, int peerUid) {
        ALog.getInstance().d(TAG, "<onTalkingPeerJoined> localUid=" + localUid + ", peerUid=" + peerUid);
    }

    @Override
    public void onTalkingPeerLeft(int localUid, int peerUid) {
        int currStateMachine;
        synchronized (mDataLock) {
            currStateMachine = mStateMachine;
        }
        ALog.getInstance().d(TAG, "<onTalkingPeerLeft> localUid=" + localUid + ", peerUid=" + peerUid
            + ", currStateMachine=" + currStateMachine);

        if (currStateMachine == STATE_CALL_DIALING ||
            currStateMachine == STATE_CALL_INCOMING ||
            currStateMachine == STATE_CALL_TALKING) {

            // 发送对端RTC掉线事件
            Message msg = new Message();
            msg.what = MSGID_CALL_PEER_INTERRUPT;
            mWorkHandler.removeMessages(MSGID_CALL_PEER_INTERRUPT);
            mWorkHandler.sendMessage(msg);
        }
    }


    ////////////////////////////////////////////////////////////
    ////////////////////// Callback Methods ////////////////////
    ////////////////////////////////////////////////////////////
    void CallbackRegisterDone(com.agora.agoracallkit.callkit.CallKitAccount account, int errCode) {
        synchronized (mDataLock) {
            for (com.agora.agoracallkit.callkit.ICallKitCallback listener : mListenerList) {
                listener.onRegisterDone(account, errCode);
            }
        }
    }

    void CallbackLogInDone(com.agora.agoracallkit.callkit.CallKitAccount account, int errCode) {
        synchronized (mDataLock) {
            for (com.agora.agoracallkit.callkit.ICallKitCallback listener : mListenerList) {
                listener.onLogInDone(account, errCode);
            }
        }
    }

    void CallbackLogOutDone(com.agora.agoracallkit.callkit.CallKitAccount account, int errCode) {
        synchronized (mDataLock) {
            for (com.agora.agoracallkit.callkit.ICallKitCallback listener : mListenerList) {
                listener.onLogOutDone(account, errCode);
            }
        }
    }

    void CallbackLoginOtherDevice(com.agora.agoracallkit.callkit.CallKitAccount account) {
        synchronized (mDataLock) {
            for (com.agora.agoracallkit.callkit.ICallKitCallback listener : mListenerList) {
                listener.onLoginOtherDevice(account);
            }
        }
    }

    void CallbackBindDeviceDone(com.agora.agoracallkit.callkit.CallKitAccount account, List<CallKitAccount> bindDevList, int errCode) {
        synchronized (mDataLock) {
            for (com.agora.agoracallkit.callkit.ICallKitCallback listener : mListenerList) {
                listener.onBindDeviceDone(account, bindDevList, errCode);
            }
        }
    }

    void CallbackUnbindDeviceDone(com.agora.agoracallkit.callkit.CallKitAccount account, List<CallKitAccount> bindDevList, int errCode) {
        synchronized (mDataLock) {
            for (com.agora.agoracallkit.callkit.ICallKitCallback listener : mListenerList) {
                listener.onUnbindDeviceDone(account, bindDevList, errCode);
            }
        }
    }

    void CallbackQueryDevListDone(com.agora.agoracallkit.callkit.CallKitAccount account, List<CallKitAccount> bindedDevList, int errCode) {
        synchronized (mDataLock) {
            for (com.agora.agoracallkit.callkit.ICallKitCallback listener : mListenerList) {
                listener.onQueryBindDevListDone(account, bindedDevList, errCode);
            }
        }
    }

    void CallbackQueryUserListDone(com.agora.agoracallkit.callkit.CallKitAccount account, List<CallKitAccount> bindedUserList, int errCode) {
        synchronized (mDataLock) {
            for (com.agora.agoracallkit.callkit.ICallKitCallback listener : mListenerList) {
                listener.onQueryBindUserListDone(account, bindedUserList, errCode);
            }
        }
    }

    void CallbackDialDone(com.agora.agoracallkit.callkit.CallKitAccount account, List<com.agora.agoracallkit.callkit.CallKitAccount> dialAccountList, int errCode)
    {
        synchronized (mDataLock) {
            for (com.agora.agoracallkit.callkit.ICallKitCallback listener : mListenerList) {
                listener.onDialDone(account, dialAccountList, errCode);
            }
        }
    }

    void CallbackPeerIncoming(com.agora.agoracallkit.callkit.CallKitAccount account,
                              com.agora.agoracallkit.callkit.CallKitAccount peer_account,
                              String attachMsg) {
        synchronized (mDataLock) {
            for (com.agora.agoracallkit.callkit.ICallKitCallback listener : mListenerList) {
                listener.onPeerIncoming(account, peer_account, attachMsg);
            }
        }
    }

    void CallbackPeerAnswer(com.agora.agoracallkit.callkit.CallKitAccount account) {
        synchronized (mDataLock) {
            for (com.agora.agoracallkit.callkit.ICallKitCallback listener : mListenerList) {
                listener.onPeerAnswer(account);
            }
        }
    }

    void CallbackPeerBusy(com.agora.agoracallkit.callkit.CallKitAccount account) {
        synchronized (mDataLock) {
            for (com.agora.agoracallkit.callkit.ICallKitCallback listener : mListenerList) {
                listener.onPeerBusy(account);
            }
        }
    }

    void CallbackPeerHangup(com.agora.agoracallkit.callkit.CallKitAccount account) {
        synchronized (mDataLock) {
            for (com.agora.agoracallkit.callkit.ICallKitCallback listener : mListenerList) {
                listener.onPeerHangup(account);
            }
        }
    }

    void CallbackPeerTimeout(com.agora.agoracallkit.callkit.CallKitAccount account) {
        synchronized (mDataLock) {
            for (com.agora.agoracallkit.callkit.ICallKitCallback listener : mListenerList) {
                listener.onPeerTimeout(account);
            }
        }
    }

    void CallbackPeerMessage(com.agora.agoracallkit.callkit.CallKitAccount account, String message) {
        synchronized (mDataLock) {
            for (com.agora.agoracallkit.callkit.ICallKitCallback listener : mListenerList) {
                listener.onPeerCustomizeMessage(account, message);
            }
        }
    }

    void CallbackCloudRecordingStart(CallKitAccount account, int errCode) {
        synchronized (mDataLock) {
            for (com.agora.agoracallkit.callkit.ICallKitCallback listener : mListenerList) {
                listener.onCloudRecordingStart(account, errCode);
            }
        }
    }

    void CallbackCloudRecordingStop(CallKitAccount account,
                                    CloudRecordResult recordResult, int errCode) {
        synchronized (mDataLock) {
            for (com.agora.agoracallkit.callkit.ICallKitCallback listener : mListenerList) {
                listener.onCloudRecordingStop(account, recordResult, errCode);
            }
        }
    }

    void CallbackAlarmSendDone(CallKitAccount account,
                               String alarmMsg, int errCode) {
        synchronized (mDataLock) {
            for (com.agora.agoracallkit.callkit.ICallKitCallback listener : mListenerList) {
                listener.onAlarmSendDone(account, alarmMsg, errCode);
            }
        }
    }

    void CallbackAlarmReceived(CallKitAccount account,
                               com.agora.agoracallkit.callkit.CallKitAccount peerAccount,
                               long timestamp,
                               String alarmMsg) {
        synchronized (mDataLock) {
            for (com.agora.agoracallkit.callkit.ICallKitCallback listener : mListenerList) {
                listener.onAlarmReceived(account, peerAccount, timestamp, alarmMsg);
            }
        }
    }

    void CallbackIotAlarmReceived(CallKitAccount account,  CallKitAccount peerAccount,
                               IotAlarm iotAlarm) {
        synchronized (mDataLock) {
            for (com.agora.agoracallkit.callkit.ICallKitCallback listener : mListenerList) {
                listener.onIotAlarmReceived(account, peerAccount, iotAlarm);
            }
        }
    }

    void CallbackAlarmQueried(CallKitAccount account,
                              AlarmQueryParam queryParam,
                              ArrayList<IotAlarm> alarmList ) {
        synchronized (mDataLock) {
            for (com.agora.agoracallkit.callkit.ICallKitCallback listener : mListenerList) {
                listener.onAlarmQueried(account, queryParam, alarmList);
            }
        }
    }

    /////////////////////////////////////////////////////////////////////////////
    ////////////////////////////// Internal Methods /////////////////////////////
    /////////////////////////////////////////////////////////////////////////////
    String combineAccountText(List<CallKitAccount> accountList) {
        if (accountList == null || accountList.size() <= 0) {
            return " { } ";
        }
        int count = accountList.size();
        String combine = " { ";

        for (int i = 0; i < (count-1); i++) {
            combine = combine + accountList.get(i).getName() + ", ";
        }

        combine = combine + accountList.get(count-1) + " } ";
        return combine;
    }

    String combineText(List<String> textList) {
        if (textList == null || textList.size() <= 0) {
            return " { } ";
        }
        int count = textList.size();
        String combine = " { ";

        for (int i = 0; i < (count-1); i++) {
            combine = combine + textList.get(i) + ", ";
        }

        combine = combine + textList.get(count-1) + " } ";
        return combine;
    }

    public String getTimeText(long timestamp) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String strTime = dateFormat.format(new Date(timestamp));
        return strTime;
    }

    /*
     * @brief call dialing response information
     */
    private class CallResponseInfo {
        public String mChannel;
        public String mToken;
        public long mUid;

        CallResponseInfo(String channel, String token, long uid) {
            mChannel = channel;
            mToken = token;
            mUid = uid;
        }
    };

    /*
     * @brief call incoming information
     */
    private class CallIncomingInfo {
        public String mAccountName;
        public int mAccountType;
        public long mUid;
        public String mChannel;
        public String mToken;
        public String mAttachMsg;
    };

    /*
     * @brief call dialing information
     */
    private class CallDialingInfo {
        List<CallKitAccount> mAccountList;
        String mAttachMsg;
    };

    /*
     * @brief Cloud recording information
     */
    private class CloudRecordInfo {
        String mRecordChannel;
        String mDeviceToken;
        String mCloudToken;
        long mCloudUid;
        String mRecordResId;
        String mRecordSid;
    };

}
