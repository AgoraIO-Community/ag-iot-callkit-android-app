/**
 * @file IAgoraIotAppSdk.java
 * @brief This file define the SDK interface for Agora Iot AppSdk 2.0
 * @author xiaohua.lu
 * @email luxiaohua@agora.io
 * @version 1.0.0.1
 * @date 2022-01-26
 * @license Copyright (C) 2021 AgoraIO Inc. All rights reserved.
 */
package io.agora.iotcallkit.sdkimpl;


import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;


import io.agora.iotcallkit.ErrCode;
import io.agora.iotcallkit.IAccountMgr;
import io.agora.iotcallkit.IAgoraCallkitSdk;
import io.agora.iotcallkit.ICallkitMgr;
import io.agora.iotcallkit.aws.AWSUtils;
import io.agora.iotcallkit.callkit.AgoraService;
import io.agora.iotcallkit.logger.ALog;
import io.agora.iotcallkit.lowservice.AgoraLowService;

import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;



/*
 * @brief SDK引擎接口
 */

public class AgoraCallkitSdk implements IAgoraCallkitSdk {

    ////////////////////////////////////////////////////////////////////////
    //////////////////////// Constant Definition ///////////////////////////
    ////////////////////////////////////////////////////////////////////////
    private static final String TAG = "IOTSDK/AgoraCallkitSdk";
    private static final int EXIT_WAIT_TIMEOUT = 3000;

    //
    // The mesage Id
    //
    private static final int MSGID_WORK_EXIT = 0xFFFF;



    ////////////////////////////////////////////////////////////////////////
    //////////////////////// Variable Definition ///////////////////////////
    ////////////////////////////////////////////////////////////////////////
    private InitParam mInitParam;
    private AccountMgr mAccountMgr;
    private CallkitMgr mCallkitMgr;


    public static final Object mDataLock = new Object();    ///< 同步访问锁,类中所有变量需要进行加锁处理
    private HandlerThread mWorkThread;  ///< 整个SDK的工作线程
    private Handler mWorkHandler;
    private final Object mWorkExitEvent = new Object();

    private volatile int mStateMachine = SDK_STATE_INVALID;     ///< 当前呼叫状态机



    ///////////////////////////////////////////////////////////////////////
    //////////////// Override Methods of IAgoraIotAppSdk //////////////////
    ///////////////////////////////////////////////////////////////////////

    @Override
    public int initialize(InitParam initParam) {
        mInitParam = initParam;

        //
        // 初始化日志系统
        //
        if ((initParam.mLogFilePath != null) && (!initParam.mLogFilePath.isEmpty())) {
            boolean logRet = ALog.getInstance().initialize(initParam.mLogFilePath);
            if (!logRet) {
                Log.e(TAG, "<initialize > [ERROR] fail to initialize logger");
            }
        }

        // 设置基本的BaseUrl
        if (initParam.mSlaveServerUrl != null) {
            AgoraService.getInstance().setBaseUrl(initParam.mSlaveServerUrl);
        }
        if (initParam.mMasterServerUrl != null) {
            AgoraLowService.getInstance().setBaseUrl(initParam.mMasterServerUrl);
        }

        //
        // 启动工作线程
        //
        workThreadCreate();

        //
        // 创建接口实例对象
        //
        mAccountMgr = new AccountMgr();
        mAccountMgr.initialize(this);

        mCallkitMgr = new CallkitMgr();
        mCallkitMgr.initialize(this);


        //
        // 设置AwsUtil的回调
        //
        AWSUtils.getInstance().setAWSListener(new AWSUtils.AWSListener() {
            @Override
            public void onConnectStatusChange(String status) {
                ALog.getInstance().d(TAG, "<onConnectStatusChange> status=" + status);

                // 账号管理系统会做 登录和登出处理
                mAccountMgr.onAwsConnectStatusChange(status);

                if (status.compareToIgnoreCase("Subscribed") == 0) {
                    Map<String, Object> params = new HashMap<String, Object>();
                    params.put("appId", mInitParam.mRtcAppId);
                    params.put("deviceAlias", mAccountMgr.getAccountInfo().mAccount);
                    if ((mInitParam.mPusherId != null) && (mInitParam.mPusherId.length() > 0)) {
                        params.put("pusherId", mInitParam.mPusherId);
                    }
                    params.put("localRecord", 0);
                    AWSUtils.getInstance().updateRtcStatus(params);
                    AWSUtils.getInstance().getRtcStatus();
                    ALog.getInstance().e(TAG, "<onConnectStatusChange> update and get RTC status");

                }  if (status.compareToIgnoreCase("ConnectionLost") == 0) {
                    if (mStateMachine == SDK_STATE_RUNNING) {
                        ALog.getInstance().e(TAG, "<onConnectStatusChange> callback network error");
                        mCallkitMgr.CallbackError(ErrCode.XERR_NETWORK);
                    }
                }
            }

            @Override
            public void onConnectFail(String message) {
                ALog.getInstance().e(TAG, "<onConnectFail> message=" + message);

                // 账号管理系统会做 登录和登出处理
                mAccountMgr.onAwsConnectFail(message);
            }

            @Override
            public void onReceiveShadow(String things_name, JSONObject jsonObject) {
                ALog.getInstance().d(TAG, "<onReceiveShadow> things_name=" + things_name
                        + ", jsonObject=" + jsonObject.toString());
            }

            @Override
            public void onUpdateRtcStatus(JSONObject jsonObject) {
                ALog.getInstance().d(TAG, "<onUpdateClient> jsonObject=" + jsonObject.toString());
                if (mCallkitMgr != null) {
                    mCallkitMgr.onAwsUpdateClient(jsonObject);
                }
            }

            @Override
            public void onDevOnlineChanged(String deviceMac, String deviceId, boolean online) {
                ALog.getInstance().d(TAG, "<onDevOnlineChanged> deviceMac=" + deviceMac
                        + ", deviceId=" + deviceId + ", online=" + online);
            }

            @Override
            public void onDevActionUpdated(String deviceMac, String actionType) {
                ALog.getInstance().d(TAG, "<onDevActionUpdated> deviceMac=" + deviceMac
                        + ", actionType=" + actionType);
            }

            @Override
            public void onDevPropertyUpdated(String deviceMac, String deviceId,
                                             Map<String, Object> properties) {
                ALog.getInstance().d(TAG, "<onDevPropertyUpdated> deviceMac=" + deviceMac
                        + ", deviceId=" + deviceId + ", properties=" + properties);
            }
        });

        //
        // SDK初始化完成
        //
        synchronized (mDataLock) {
            mStateMachine = SDK_STATE_READY;  // 状态机切换到 SDK就绪
        }
        ALog.getInstance().d(TAG, "<initialize> done");
        return ErrCode.XOK;
    }

    @Override
    public void release() {
        //
        // 销毁工作线程
        //
        workThreadDestroy();

        //
        // 销毁接口实例对象
        //
        if (mAccountMgr != null) {
            mAccountMgr.release();
            mAccountMgr = null;
        }

        if (mCallkitMgr != null) {
            mCallkitMgr.release();
            mCallkitMgr = null;
        }

        synchronized (mDataLock) {
            mStateMachine = SDK_STATE_INVALID;  // 状态机切换到 无效状态
        }
        ALog.getInstance().d(TAG, "<release> done");
        ALog.getInstance().release();
    }

    @Override
    public int getStateMachine() {
        synchronized (mDataLock) {
            return mStateMachine;
        }
    }

    @Override
    public IAccountMgr getAccountMgr() {
        return mAccountMgr;
    }

    @Override
    public ICallkitMgr getCallkitMgr() {
        return mCallkitMgr;
    }


    ///////////////////////////////////////////////////////////////////////////
    //////////////////////// Methods for each sub-module ///////////////////////
    //////////////////////////////////////////////////////////////////////////
    /*
     * @brief SDK状态机设置，仅在 AccountMgr 模块中设置
     */
    void setStateMachine(int newState) {
        synchronized (mDataLock) {
            mStateMachine = newState;
        }
    }

    IAgoraCallkitSdk.InitParam getInitParam() {
        return mInitParam;
    }

    Handler getWorkHandler() {
        return mWorkHandler;
    }

    AccountMgr.AccountInfo getAccountInfo() {
        if (mAccountMgr == null) {
            return null;
        }
        return mAccountMgr.getAccountInfo();
    }

    ///////////////////////////////////////////////////////////////////////////
    //////////////////////// Innternal Utility Methods ////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    void workThreadCreate() {
        mWorkThread = new HandlerThread("AppSdk");
        mWorkThread.start();
        mWorkHandler = new Handler(mWorkThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                mAccountMgr.workThreadProcessMessage(msg);
                mCallkitMgr.workThreadProcessMessage(msg);
                workThreadProcessMessage(msg);
            }
        };
    }

    void workThreadDestroy() {
        if (mWorkHandler != null) {

            // 清除所有消息队列中消息
            mAccountMgr.workThreadClearMessage();

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
    }

    void workThreadProcessMessage(Message msg) {
        switch (msg.what) {
            case MSGID_WORK_EXIT:  // 工作线程退出消息
                synchronized (mWorkExitEvent) {
                    mWorkExitEvent.notify();    // 事件通知
                }
                break;
        }
    }

    void sendMessage(int messageId, int arg1, int arg2, Object obj) {
        if (mWorkHandler != null) {
            Message msg = new Message();
            msg.what = messageId;
            msg.arg1 = arg1;
            msg.arg2 = arg2;
            msg.obj = obj;
            mWorkHandler.removeMessages(messageId);
            mWorkHandler.sendMessage(msg);
        }
    }




}
