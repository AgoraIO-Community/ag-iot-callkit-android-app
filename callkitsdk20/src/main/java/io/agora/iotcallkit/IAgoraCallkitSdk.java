/**
 * @file IAgoraIotAppSdk.java
 * @brief This file define the SDK interface for Agora Iot AppSdk 2.0
 * @author xiaohua.lu
 * @email luxiaohua@agora.io
 * @version 1.0.0.1
 * @date 2022-01-26
 * @license Copyright (C) 2021 AgoraIO Inc. All rights reserved.
 */
package io.agora.iotcallkit;


import android.content.Context;
import android.os.Bundle;



/*
 * @brief SDK引擎接口
 */

public interface IAgoraCallkitSdk  {

    //
    // SDK 状态机
    //
    public static final int SDK_STATE_INVALID = 0x0000;             ///< SDK未初始化
    public static final int SDK_STATE_READY = 0x0001;               ///< SDK初始化完成，但还未登录
    public static final int SDK_STATE_LOGINING = 0x0002;            ///< SDK正在登录用户账号
    public static final int SDK_STATE_LOGOUTING = 0x0003;           ///< SDK正在登出用户账号
    public static final int SDK_STATE_RUNNING = 0x0004;             ///< SDK已经登录用户账号，可以正常操作



    /*
     * @brief 初始化参数
     */
    public static class InitParam {
        public Context mContext;
        public String mRtcAppId;                    ///< Agora RTC的 AppId
        public String mProjectId;                   ///< 申请的项目Id
        public String mLogFilePath;                 ///< 日志文件路径，不设置则日志不输出到本地文件
        public boolean mPublishAudio = false;        ///< 通话时是否推流本地音频
        public boolean mPublishVideo = false;        ///< 通话时是否推流本地视频
        public boolean mSubscribeAudio = true;      ///< 通话时是否订阅对端音频
        public boolean mSubscribeVideo = true;      ///< 通话时是否订阅对端视频
        public String mMasterServerUrl;             ///< 提供的第一个BaseUrl
        public String mSlaveServerUrl;              ///< 提供的第二个BaseUrl
        public String mPusherId;                    ///< 离线推送的pusherId
    }

    ////////////////////////////////////////////////////////////////////////
    ////////////////////////// Public Methods //////////////////////////////
    ////////////////////////////////////////////////////////////////////////
    /**
     * @brief 初始化Sdk
     * @param initParam 初始化参数
     * @return 返回错误码
     */
    int initialize(InitParam initParam);

    /**
     * @brief 释放SDK所有资源
     * @return 返回错误码
     */
    void release();

    /**
     * @brief 获取当前状态机
     */
    int getStateMachine();


    /*
     * @brief 获取账号管理接口，可以进行登录、登出等操作
     */
    IAccountMgr getAccountMgr();

    /*
     * @brief 获取呼叫系统接口，可以进行主叫、接听、挂断、变声等控制
     */
    ICallkitMgr getCallkitMgr();


}
