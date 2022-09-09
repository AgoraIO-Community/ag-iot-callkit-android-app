/**
 * @file AccountMgr.java
 * @brief This file implement the account management
 * @author xiaohua.lu
 * @email luxiaohua@agora.io
 * @version 1.0.0.1
 * @date 2022-01-26
 * @license Copyright (C) 2021 AgoraIO Inc. All rights reserved.
 */
package io.agora.iotcallkit.sdkimpl;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import io.agora.iotcallkit.ErrCode;
import io.agora.iotcallkit.IAccountMgr;
import io.agora.iotcallkit.IAgoraCallkitSdk;
import io.agora.iotcallkit.aws.AWSUtils;
import io.agora.iotcallkit.callkit.AgoraService;
import io.agora.iotcallkit.logger.ALog;
import io.agora.iotcallkit.lowservice.AgoraLowService;

import java.util.ArrayList;



/*
 * @brief 账号管理器
 */
public class AccountMgr implements IAccountMgr {
    /*
     * @brief 登录成功后的账号信息
     */
    public static class AccountInfo {
        public String mAccount;                 ///< 账号名称
        public String mAnonymosName;            ///< 匿名的账号名称
        public String mEndpoint;                ///< iot 平台节点
        public String mRegion;                  ///< 节点
        public String mPlatformToken;           ///< 平台凭证
        public long mExpiration;                ///< mGranwinToken 过期时间
        public String mRefresh;                 ///< 平台刷新凭证密钥

        public String mPoolIdentifier;          ///< 用户身份
        public String mPoolIdentityId;          ///< 用户身份Id
        public String mPoolToken;               ///< 用户身份凭证
        public String mIdentityPoolId;          ///< 用户身份池标识

        public String mProofAccessKeyId;        ///< IOT 临时账号凭证
        public String mProofSecretKey;          ///< IOT 临时密钥
        public String mProofSessionToken;       ///< IOT 临时Token
        public long mProofSessionExpiration;    ///< 过期时间(时间戳)

        public String mInventDeviceName;        ///< 虚拟设备thing name

        public String mAgoraScope;
        public String mAgoraTokenType;
        public String mAgoraAccessToken;
        public String mAgoraRefreshToken;
        public long mAgoraExpriesIn;

        @Override
        public String toString() {
            String infoText = "{ mAccount=" + mAccount + ", mPoolIdentifier=" + mPoolIdentifier
                    + ", mPoolIdentityId=" + mPoolIdentityId + ", mPoolToken=" + mPoolToken
                    + ", mIdentityPoolId=" + mIdentityPoolId
                    + ", mProofAccessKeyId=" + mProofAccessKeyId
                    + ", mProofSecretKey=" + mProofSecretKey
                    + ", mProofSessionToken=" + mProofSessionToken
                    + ", mInventDeviceName=" + mInventDeviceName
                    + ", mAgoraScope=" + mAgoraScope
                    + ", mAgoraTokenType=" + mAgoraTokenType
                    + ", mAgoraAccessToken=" + mAgoraAccessToken
                    + ", mAgoraRefreshToken=" + mAgoraRefreshToken
                    + ", mAgoraExpriesIn=" + mAgoraExpriesIn + " }";
            return infoText;
        }
    }

    ////////////////////////////////////////////////////////////////////////
    //////////////////////// Constant Definition ///////////////////////////
    ////////////////////////////////////////////////////////////////////////
    private static final String TAG = "IOTSDK/AccountMgr";




    //
    // The mesage Id
    //
    private static final int MSGID_ACCOUNT_BASE = 0x1000;
    private static final int MSGID_ACCOUNT_LOGIN = 0x1003;
    private static final int MSGID_AWSLOGIN_DONE = 0x1004;
    private static final int MSGID_ACCOUNT_LOGOUT = 0x1005;
    private static final int MSGID_ACCOUNT_TOKEN_INVALID = 0x1006;


    ////////////////////////////////////////////////////////////////////////
    //////////////////////// Variable Definition ///////////////////////////
    ////////////////////////////////////////////////////////////////////////
    private ArrayList<IAccountMgr.ICallback> mCallbackList = new ArrayList<>();
    private AgoraCallkitSdk mSdkInstance;                        ///< 由外部输入的
    private Handler mWorkHandler;                               ///< 工作线程Handler，从SDK获取到
    private Bundle mMetaData;

    private static final Object mDataLock = new Object();       ///< 同步访问锁,类中所有变量需要进行加锁处理
    private volatile int mStateMachine = ACCOUNT_STATE_IDLE;    ///< 当前呼叫状态机
    private AgoraService.AccountTokenInfo mAgoraAccount;        ///< 登录Agora的账号Token信息
    private AgoraLowService.AccountInfo mLoginAccount;          ///< 当前正在登录的底层账号信息
    private AccountInfo mLocalAccount;                          ///< 当前已经登录账号, null表示未登录


    ///////////////////////////////////////////////////////////////////////
    ////////////////////////// Public Methods  ////////////////////////////
    ///////////////////////////////////////////////////////////////////////
    int initialize(AgoraCallkitSdk sdkInstance) {
        mSdkInstance = sdkInstance;
        mWorkHandler = sdkInstance.getWorkHandler();
        mStateMachine = ACCOUNT_STATE_IDLE;

        //获取applicationInfo标签内的数据
        IAgoraCallkitSdk.InitParam initParam = mSdkInstance.getInitParam();
        try {
            PackageManager packageManager = initParam.mContext.getPackageManager();
            ApplicationInfo applicationInfo = packageManager.getApplicationInfo(
                    initParam.mContext.getPackageName(), PackageManager.GET_META_DATA);
            mMetaData = applicationInfo.metaData;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            ALog.getInstance().e(TAG, "<initialize> fail to get meta data");
        }

        return ErrCode.XOK;
    }

    void release() {
        workThreadClearMessage();

        synchronized (mCallbackList) {
            mCallbackList.clear();
        }
    }

    void workThreadProcessMessage(Message msg) {
        switch (msg.what) {
            case MSGID_ACCOUNT_LOGIN: {
                DoAccountLogin(msg);
            } break;

            case MSGID_AWSLOGIN_DONE: {
                DoAwsLoginDone(msg);
            } break;

            case MSGID_ACCOUNT_LOGOUT: {
                DoAccountLogout(msg);
            } break;

              case MSGID_ACCOUNT_TOKEN_INVALID: {
                DoTokenInvalid(msg);
            } break;
        }
    }

    void workThreadClearMessage() {
        if (mWorkHandler != null) {
            mWorkHandler.removeMessages(MSGID_ACCOUNT_LOGIN);
            mWorkHandler.removeMessages(MSGID_AWSLOGIN_DONE);
            mWorkHandler.removeMessages(MSGID_ACCOUNT_LOGOUT);
            mWorkHandler.removeMessages(MSGID_ACCOUNT_TOKEN_INVALID);
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
    /////////////////// Override Methods of IAccountMgr //////////////////
    ///////////////////////////////////////////////////////////////////////
    @Override
    public int getStateMachine() {
        synchronized (mDataLock) {
            return mStateMachine;
        }
    }


    @Override
    public int registerListener(IAccountMgr.ICallback callback) {
        synchronized (mCallbackList) {
            mCallbackList.add(callback);
        }
        return ErrCode.XOK;
    }

    @Override
    public int unregisterListener(IAccountMgr.ICallback callback) {
        synchronized (mCallbackList) {
            mCallbackList.remove(callback);
        }
        return ErrCode.XOK;
    }


    @Override
    public int login(final LoginParam loginParam) {
        if (getStateMachine() != ACCOUNT_STATE_IDLE) {
            ALog.getInstance().e(TAG, "<login> bad state, mStateMachine=" + mStateMachine);
            return ErrCode.XERR_BAD_STATE;
        }

        synchronized (mDataLock) {
            mStateMachine = ACCOUNT_STATE_LOGINING;  // 状态机切换到 正在登录中
            mLoginAccount = null;
            mLocalAccount = null;
        }
        mSdkInstance.setStateMachine(IAgoraCallkitSdk.SDK_STATE_LOGINING);
        sendMessage(MSGID_ACCOUNT_LOGIN, 0, 0, loginParam);
        ALog.getInstance().d(TAG, "<login> loginParam=" + loginParam.toString());
        return ErrCode.XOK;
    }


    @Override
    public int logout() {
        if (getStateMachine() != ACCOUNT_STATE_RUNNING) {
            ALog.getInstance().e(TAG, "<logout> bad state, mStateMachine=" + mStateMachine);
            return ErrCode.XERR_BAD_STATE;
        }

        synchronized (mDataLock) {
            mStateMachine = ACCOUNT_STATE_LOGOUTING;  // 状态机切换到 正在登出中
        }
        mSdkInstance.setStateMachine(IAgoraCallkitSdk.SDK_STATE_LOGOUTING);

        sendMessage(MSGID_ACCOUNT_LOGOUT, 0, 0, null);
        ALog.getInstance().d(TAG, "<logout> account=" + mLocalAccount.mAccount);
        return ErrCode.XOK;
    }

    @Override
    public String getLoggedAccount() {
        synchronized (mDataLock) {
            if (mLocalAccount == null) {
                return null;
            }
            return mLocalAccount.mAccount;
        }
    }

    @Override
    public String getAccountId() {
        synchronized (mDataLock) {
            if (mLocalAccount == null) {
                return null;
            }
            return mLocalAccount.mInventDeviceName;
        }
    }

    public AccountInfo getAccountInfo() {
        synchronized (mDataLock) {
            return mLocalAccount;
        }
    }




    ///////////////////////////////////////////////////////////////////////
    ///////////////////// Methods for Account Login //////////////////////
    ///////////////////////////////////////////////////////////////////////
    /*
     * @brief 工作线程中进行实际的登录操作
     */
    void DoAccountLogin(Message msg) {
        LoginParam loginParam = (LoginParam)(msg.obj);
        IAgoraCallkitSdk.InitParam initParam = mSdkInstance.getInitParam();

        synchronized (mDataLock) {
            mAgoraAccount = new AgoraService.AccountTokenInfo();
            mAgoraAccount.mAccessToken = loginParam.mLsAccessToken;
            mAgoraAccount.mTokenType = loginParam.mLsTokenType;
            mAgoraAccount.mRefreshToken = loginParam.mLsRefreshToken;
            mAgoraAccount.mExpriesIn = loginParam.mLsExpiresIn;
            mAgoraAccount.mScope = loginParam.mLsScope;

            mLoginAccount = new AgoraLowService.AccountInfo();
            mLoginAccount.mAccount = loginParam.mAccount;
            mLoginAccount.mEndpoint = loginParam.mEndpoint;
            mLoginAccount.mRegion = loginParam.mRegion;
            mLoginAccount.mPlatformToken = loginParam.mPlatformToken;
            mLoginAccount.mExpiration = loginParam.mExpiration;
            mLoginAccount.mRefresh = loginParam.mRefresh;
            mLoginAccount.mPoolIdentifier = loginParam.mPoolIdentifier;
            mLoginAccount.mPoolIdentityId = loginParam.mPoolIdentityId;
            mLoginAccount.mPoolToken = loginParam.mPoolToken;
            mLoginAccount.mIdentityPoolId = loginParam.mIdentityPoolId;
            mLoginAccount.mProofAccessKeyId = loginParam.mProofAccessKeyId;
            mLoginAccount.mProofSecretKey = loginParam.mProofSecretKey;
            mLoginAccount.mProofSessionToken = loginParam.mProofSessionToken;
            mLoginAccount.mProofSessionExpiration = loginParam.mProofSessionExpiration;
            mLoginAccount.mInventDeviceName = loginParam.mInventDeviceName;
        }


        //
        // 初始化 AWS 联接
        //
        String aws_account = mLoginAccount.mAccount;
        String aws_endpoint = mLoginAccount.mEndpoint;
        String aws_identityId = mLoginAccount.mPoolIdentityId;
        String aws_token = mLoginAccount.mPoolToken;
        String aws_accountId = mLoginAccount.mPoolIdentifier;
        String aws_identityPoolId = mLoginAccount.mIdentityPoolId;
        String aws_region = mLoginAccount.mRegion;
        String aws_inventDeviceName = mLoginAccount.mInventDeviceName;
        AWSUtils.getInstance().initIoTClient(initParam.mContext,
                aws_identityId, aws_endpoint, aws_token, aws_accountId,
                aws_identityPoolId, aws_region, aws_inventDeviceName);

        ALog.getInstance().d(TAG, "<DoAccountLogin> initAWSIotClient"
                + ", aws_account=" + aws_account
                + ", aws_identityId=" + aws_identityId
                + ", aws_endpoint=" + aws_endpoint
                + ", aws_token=" + aws_token
                + ", aws_accountId=" + aws_accountId
                + ", aws_identityPoolId=" + aws_identityPoolId
                + ", aws_region=" + aws_region);
    }

    /*
     * @brief 在AWS回调中被调用，用于处理AWS登录状态
     */
    void onAwsConnectStatusChange(String status) {
        ALog.getInstance().d(TAG, "<onAwsConnectStatusChange> status=" + status
                + ", mStateMachine=" + mStateMachine);
        if (getStateMachine() != ACCOUNT_STATE_LOGINING) {  // 当前非正在登录，直接忽略
            return;
        }

        if (status.compareToIgnoreCase("Connecting") == 0) {

        } else if (status.compareToIgnoreCase("Connected") == 0) {
            // sendMessage(MSGID_AWSLOGIN_DONE, ErrCode.XOK, 0, null);  // AWS成功

        } else if (status.compareToIgnoreCase("Subscribed") == 0) {
            sendMessage(MSGID_AWSLOGIN_DONE, ErrCode.XOK, 0, null);  // 订阅成功才算完整成功

        } else if (status.compareToIgnoreCase("ConnectionLost") == 0) {
            sendMessage(MSGID_AWSLOGIN_DONE, ErrCode.XERR_ACCOUNT_LOGIN, 0, null);  // AWS失败
        }
    }

    /*
     * @brief 在AWS回调中被调用，用于处理AWS登录状态
     */
    void onAwsConnectFail(String message) {
        ALog.getInstance().d(TAG, "<onAwsConnectFail> message=" + message);
        if (getStateMachine() != ACCOUNT_STATE_LOGINING) {  // 当前非正在登录，直接忽略
            return;
        }

        sendMessage(MSGID_AWSLOGIN_DONE, ErrCode.XERR_ACCOUNT_LOGIN, 0, null);  // AWS失败
    }


    /*
     * @brief 工作线程中进行AWS的初始化完成处理
     */
    void DoAwsLoginDone(Message msg) {
        int errCode = msg.arg1;
        ALog.getInstance().d(TAG, "<DoAwsLoginDone> errCode=" + errCode);

        if (errCode == ErrCode.XOK)   // AWS 登录成功
        {
            // 将eid作为属性值，设置下去
         //   AWSUtils.getInstance().setDeviceStatus();

            synchronized (mDataLock) {
                // 设置当前已经登录账号信息
                mLocalAccount = new AccountInfo();
                mLocalAccount.mAccount = mLoginAccount.mAccount;
                mLocalAccount.mEndpoint = mLoginAccount.mEndpoint;
                mLocalAccount.mRegion = mLoginAccount.mRegion;
                mLocalAccount.mPlatformToken = mLoginAccount.mPlatformToken;
                mLocalAccount.mExpiration = mLoginAccount.mExpiration;
                mLocalAccount.mRefresh = mLoginAccount.mRefresh;
                mLocalAccount.mPoolIdentifier = mLoginAccount.mPoolIdentifier;
                mLocalAccount.mPoolIdentityId = mLoginAccount.mPoolIdentityId;
                mLocalAccount.mPoolToken = mLoginAccount.mPoolToken;
                mLocalAccount.mIdentityPoolId = mLoginAccount.mIdentityPoolId;
                mLocalAccount.mProofAccessKeyId = mLoginAccount.mProofAccessKeyId;
                mLocalAccount.mProofSecretKey = mLoginAccount.mProofSecretKey;
                mLocalAccount.mProofSessionToken = mLoginAccount.mProofSessionToken;
                mLocalAccount.mProofSessionExpiration = mLoginAccount.mProofSessionExpiration;
                mLocalAccount.mInventDeviceName = mLoginAccount.mInventDeviceName;
                mLoginAccount = null;

                if (mAgoraAccount != null) {    // 赋值Agora账号相关信息
                    mLocalAccount.mAgoraScope = mAgoraAccount.mScope;
                    mLocalAccount.mAgoraTokenType = mAgoraAccount.mTokenType;
                    mLocalAccount.mAgoraAccessToken = mAgoraAccount.mAccessToken;
                    mLocalAccount.mAgoraRefreshToken = mAgoraAccount.mRefreshToken;
                    mLocalAccount.mAgoraExpriesIn = mAgoraAccount.mExpriesIn;
                }

                mStateMachine = ACCOUNT_STATE_RUNNING;  // 状态机切换到 已经登录 状态
            }
            mSdkInstance.setStateMachine(IAgoraCallkitSdk.SDK_STATE_RUNNING);
            ALog.getInstance().d(TAG, "<DoAwsLoginDone> finished successful");
            CallbackLogInDone(ErrCode.XOK, mLocalAccount.mAccount);

        }
        else // AWS登录失败
        {
            String account = mLoginAccount.mAccount;
            synchronized (mDataLock) {
                mStateMachine = ACCOUNT_STATE_IDLE;    // 状态机切换回 账号未登录 状态
                mLocalAccount = null;                  // 清空已经登录的本地账号
                mLoginAccount = null;
                mAgoraAccount = null;
            }
            mSdkInstance.setStateMachine(IAgoraCallkitSdk.SDK_STATE_READY);
            ALog.getInstance().e(TAG, "<DoAwsLoginDone> finished with AWS failure");
            CallbackLogInDone(errCode, account);
        }
    }

    void CallbackLogInDone(int errCode, String account) {
        synchronized (mCallbackList) {
            for (IAccountMgr.ICallback listener : mCallbackList) {
                listener.onLoginDone(errCode, account);
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////
    ///////////////////// Methods for Account Logout //////////////////////
    ///////////////////////////////////////////////////////////////////////
    /*
     * @brief 工作线程中进行实际的登出操作，需要等登出结果消息回来
     */
    void DoAccountLogout(Message msg)
    {
        // 断开 AWS 联接
        AWSUtils.getInstance().disConnect();
        String account;
        synchronized (mDataLock) {
            account = mLocalAccount.mAccount;
            mLocalAccount = null;               // 清空本地账号
            mStateMachine = ACCOUNT_STATE_IDLE;    // 状态机切换到 未登录 状态
        }
        mSdkInstance.setStateMachine(IAgoraCallkitSdk.SDK_STATE_READY);
        ALog.getInstance().d(TAG, "<DoAccountLogout> finished with successful");
        CallbackLogoutDone(ErrCode.XOK, account);
    }

    void CallbackLogoutDone(int errCode, String account) {
        synchronized (mCallbackList) {
            for (IAccountMgr.ICallback listener : mCallbackList) {
                listener.onLogoutDone(errCode, account);
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////
    ///////////////////// Methods for Account Token Invalid ////////////////
    ///////////////////////////////////////////////////////////////////////
    /*
     * @brief Token过期的回调处理
     */
    void onTokenInvalid() {
        ALog.getInstance().e(TAG, "<onTokenInvalid> ");

        // 在广云的HTTP请求收到Toke过期错误后，延时一些进行回调，方便应用层处理
        sendMessageDelay(MSGID_ACCOUNT_TOKEN_INVALID, 0, 0, null, 500);
    }

    void DoTokenInvalid(Message msg) {
        // 断开 AWS 联接
        AWSUtils.getInstance().disConnect();
        String account;
        synchronized (mDataLock) {
            account = mLocalAccount.mAccount;
            mLocalAccount = null;               // 清空本地账号
            mStateMachine = ACCOUNT_STATE_IDLE;    // 状态机切换到 未登录 状态
        }
        mSdkInstance.setStateMachine(IAgoraCallkitSdk.SDK_STATE_READY);
        ALog.getInstance().d(TAG, "<DoTokenInvalid> finished with successful");

        synchronized (mCallbackList) {  // 回调给应用层
            for (IAccountMgr.ICallback listener : mCallbackList) {
                listener.onTokenInvalid();
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////
    ///////////////////// Methods for Account Be Preempted ////////////////
    ///////////////////////////////////////////////////////////////////////
    /*
     * @brief 工作线程中处理账号异地登录事件，本地被强制登出结果
     */
    void DoAccountLoginOtherDev(Message msg)
    {
//        synchronized (mDataLock) {
//            mLocalAccount = null;               // 清空本地账号
//            mStateMachine = ACCOUNT_STATE_IDLE;    // 状态机切换到 未登录 状态
//        }
//        ALog.getInstance().d(TAG, "<DoAccountLogoutDone> finished with successful");
//        CallbackLogoutDone(ErrCode.XOK, account);
    }





}
