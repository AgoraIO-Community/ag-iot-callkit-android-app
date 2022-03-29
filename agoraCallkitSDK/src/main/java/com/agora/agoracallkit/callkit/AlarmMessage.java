/**
 * @file AlarmMessage.java
 * @brief This file implement the alarm message
 * @author xiaohua.lu
 * @email luxiaohua@agora.io
 * @version 1.0.0.1
 * @date 2021-12-03
 * @license Copyright (C) 2021 AgoraIO Inc. All rights reserved.
 */
package com.agora.agoracallkit.callkit;


import com.agora.agoracallkit.beans.UidInfoBean;


/*
 * @brief 告警消息
 */
public class AlarmMessage{
    public static final String OSS_PATH = "http://agora-iot-oss-test.oss-cn-shanghai.aliyuncs.com/";

    private long mTimestamp;        ///< 告警时刻点
    private UidInfoBean mDevInfo;   ///< 设备相关信息
    private String mRecordChannel;  ///< 录制的频道号
    private String mRecordSid;      ///< 录制的Sid
    private String mRecordToken;    ///< 录制的Tokens
    private String mMessage;        ///< 告警消息数据

    private String mUrlPath;        ///< 对应的视频文件URL


    ////////////////////////////////////////////////////////////////////////
    ////////////////////////// Public Methods //////////////////////////////
    ////////////////////////////////////////////////////////////////////////
    public AlarmMessage(long timestamp, UidInfoBean devInfo,
                        String channel, String sid, String token, String message ) {
        mTimestamp = timestamp;
        mDevInfo = devInfo;
        mRecordChannel = channel;
        mRecordSid = sid;
        mRecordToken = token;
        mMessage = message;

        // 例如：http://agora-iot-oss-test.oss-cn-shanghai.aliyuncs.com/18e12695164239fc4655aa84d58ec9ba_96_1.m3u8
        mUrlPath = OSS_PATH + mRecordSid + "_" + mRecordChannel + ".m3u8";
    }

    public long getTimestamp() {
        return mTimestamp;
    }

    public String getDeviceId() {
        return mDevInfo.getAccount();
    }

    public long getDeviceUid() {
        return mDevInfo.getUid();
    }

    public int getType() {
        return 1;
    }

    public int getPriority() {
        return 1;
    }

    public String getMessage() {
        return mMessage;
    }

    public String getVideoUrl() {
        return mUrlPath;
    }


    public String toString() {
        String infoText = "{ time=" + mTimestamp + ", devId=" + mDevInfo.getAccount()
                + ", uid=" + mDevInfo.getUid() + ", chnl=" + mRecordChannel + ", sid=" + mRecordSid
                + ", token=" + mRecordToken + ", message=" + mMessage + " }";
        return infoText;
    }





}
