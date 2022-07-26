/**
 * @file IAccountMgr.java
 * @brief This file define the interface of call kit and RTC management
 * @author xiaohua.lu
 * @email luxiaohua@agora.io
 * @version 1.0.0.1
 * @date 2022-01-26
 * @license Copyright (C) 2021 AgoraIO Inc. All rights reserved.
 */
package io.agora.iotcallkit;


import android.graphics.Bitmap;
import android.view.SurfaceView;



/**
 * @brief 呼叫系统接口
 */
public interface ICallkitMgr {

    //
    // 呼叫系统的状态机
    //
    public static final int CALLKIT_STATE_IDLE = 0x0001;        ///< 当前 空闲状态无通话
    public static final int CALLKIT_STATE_DIALING = 0x0002;     ///< 正在呼叫设备中
    public static final int CALLKIT_STATE_INCOMING = 0x0003;    ///< 正在有来电中
    public static final int CALLKIT_STATE_TALKING = 0x0004;     ///< 正在通话中

    public static final int CALLKIT_STATE_DIAL_REQING = 0x0005;     ///< 正在发送主叫请求中
    public static final int CALLKIT_STATE_DIAL_RSPING = 0x0006;     ///< 正在等待主叫响应中

    public static final int CALLKIT_STATE_ANSWER_REQING = 0x0007;   ///< 正在发送接听请求中
    public static final int CALLKIT_STATE_ANSWER_RSPING = 0x0008;   ///< 正在等待接听响应中

    public static final int CALLKIT_STATE_HANGUP_REQING = 0x0009;   ///< 正在发送挂断请求中


    /**
     * @brief 音效属性设置
     */
    public enum AudioEffectId {
        NORMAL, OLDMAN, BABYBOY, BABYGIRL, ZHUBAJIE, ETHEREAL, HULK
    }


    /**
     * @brief RTC状态信息
     */
    public static class RtcNetworkStatus {
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

    /**
     * @brief 账号管理回调接口
     */
    public static interface ICallback {

        /**
         * @brief 呼叫请求成功事件，服务器正在转接
         * @param errCode : 错误代码
         * @param peerAccountId : 对端账号Id
         */
        default void onDialDone(int errCode, final String peerAccountId) {}

        /**
         * @brief 对端设备来电事件
         * @param peerAccountId : 对端账号Id
         * @param attachMsg: 来电时附带信息
         */
        default void onPeerIncoming(final String peerAccountId, final String attachMsg) {}

        /**
         * @brief 对端设备接听事件
         * @param peerAccountId : 对端账号Id
         */
        default void onPeerAnswer(final String peerAccountId) {}

        /**
         * @brief 对端设备挂断事件
         * @param peerAccountId : 对端账号Id
         */
        default void onPeerHangup(final String peerAccountId) {}

        /*
         * @brief 对端设备超时无人接听事件
         * @param peerAccountId : 对端账号Id
         */
        default void onPeerTimeout(final String peerAccountId) {}

        /**
         * @brief 对端视频首帧出图事件
         * @param peerAccountId : 对端账号Id
         * @param videoWidth : 首帧视频宽度
         * @param videoHeight : 首帧视频高度
         */
        default void onPeerFirstVideo(final String peerAccountId,
                                      int videoWidth, int videoHeight) {}


        /**
         * @brief 错误事件，在呼叫系统中遇到任意错误时发生，触发该事件后，整个呼叫过程全部清除
         * @param errCode : 错误代码
         */
        default void onCallkitError(int errCode) {}
    }


    ////////////////////////////////////////////////////////////////////////
    //////////////////////////// Public Methods ///////////////////////////
    ////////////////////////////////////////////////////////////////////////
    /**
     * @brief 获取当前设备管理状态机
     * @return 返回状态机
     */
    int getStateMachine();

    /**
     * @brief 注册回调接口
     * @param callback : 回调接口
     * @return 错误码
     */
    int registerListener(ICallkitMgr.ICallback callback);

    /**
     * @brief 注销回调接口
     * @param callback : 回调接口
     * @return 错误码
     */
    int unregisterListener(ICallkitMgr.ICallback callback);


    /**
     * @brief 呼叫对端
     * @param peerAccountId : 对端账号Id
     * @param attachMsg : 呼叫时附带的信息
     * @return 错误码
     */
    int callDial(final String peerAccountId, final String attachMsg);

    /**
     * @brief 挂断当前通话或者来电
     * @return 错误码
     */
    int callHangup();

    /**
     * @brief 接听当前来电
     * @return 错误码
     */
    int callAnswer();

    /**
     * @brief 本地推流时设置本地视频显示控件，如果本地视频不推流则不需要设置
     * @param localView: 本地视频显示控件
     * @return 错误码
     */
    int setLocalVideoView(final SurfaceView localView);

    /**
     * @brief 设置对端视频显示控件，如果不设置则不显示对端视频
     * @param peerView: 对端视频显示控件
     * @return 错误码
     */
    int setPeerVideoView(final SurfaceView peerView);

    /**
     * @brief 禁止/启用 本地视频推流到对端
     * @param mute: 是否禁止
     * @return 错误码
     */
    int muteLocalVideo(boolean mute);

    /**
     * @brief 禁止/启用 本地音频推流到对端
     * @param mute: 是否禁止
     * @return 错误码
     */
    int muteLocalAudio(boolean mute);

    /**
     * @brief 禁止/启用 拉流对端视频
     * @param mute: 是否禁止
     * @return 错误码
     */
    int mutePeerVideo(boolean mute);

    /**
     * @brief 禁止/启用 拉流对端音频
     * @param mute: 是否禁止
     * @return 错误码
     */
    int mutePeerAudio(boolean mute);


    /**
     * @brief 设置音频播放的音量
     * @param volumeLevel: 音量级别
     * @return 错误码
     */
    int setVolume(int volumeLevel);


    /**
     * @brief 设置音效效果（通常是变声等音效）
     * @param effectId: 音效Id
     * @return 错误码
     */
    int setAudioEffect(final AudioEffectId effectId);

    /**
     * @brief 开始录制当前通话（包括音视频流），仅在通话状态下才能调用
     * @return 错误码
     */
    int talkingRecordStart();

    /**
     * @brief 停止录制当前通话，仅在通话状态下才能调用
     * @return 错误码
     */
    int talkingRecordStop();


    /**
     * @brief 获取当前网络状态
     * @return 返回RTC网络状态信息
     */
    RtcNetworkStatus getNetworkStatus();

    /**
     * @brief 截屏对端视频帧图像，仅在通话过程中可以被调用
     * @return 抓取到的视频帧图像
     */
    Bitmap capturePeerVideoFrame();

    /**
     * @brief 设置RTC私有参数
     * @param privateParam : 要设置的私参
     * @return 错误码
     */
    int setRtcPrivateParam(final String privateParam);

}
