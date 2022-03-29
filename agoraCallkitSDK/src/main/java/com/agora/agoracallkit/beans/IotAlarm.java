/**
 * @file IotAlarm.java
 * @brief This file define the data structure of alarm information
 * @author xiaohua.lu
 * @email luxiaohua@agora.io
 * @version 1.0.0.1
 * @date 2022-01-26
 * @license Copyright (C) 2021 AgoraIO Inc. All rights reserved.
 */
package com.agora.agoracallkit.beans;


import java.util.Date;

/*
 * @brief 告警信息
 */
public class IotAlarm {

    private static final String OSS_PATH = "http://agora-iot-oss-test.oss-cn-shanghai.aliyuncs.com/";

    public long mAlarmId;           ///< 告警信息Id，是告警信息唯一标识
    public int mType;               ///< 告警类型，1：有人通过 2：移动侦测 3：语音告警
    public String mDescription;     ///< 告警事件描述
    public String mAttachMsg;       ///< 告警附加消息
    public String mOccurDate;       ///< 告警发生时间
    public long mTimestamp;         ///< 告警发送的时间戳
    public String mDeviceId;        ///< 设备Id
    public long mDeviceUid;         ///< 设备内部Uid
    public String mRecordChannel;   ///< 云录频道号
    public String mRecordSid;       ///< 云录的Sid
    public boolean mReaded;         ///< 是否已经阅读过


    public String getRecordVideoUrl() {
        String url = OSS_PATH + mRecordSid + "_" + mRecordChannel + ".m3u8";
        return url;
    }

    @Override
    public String toString() {
        String infoText = "{ time=" + mTimestamp + ", devId=" + mDeviceId
                + ", uid=" + mDeviceUid + ", chnl=" + mRecordChannel + ", sid=" + mRecordSid
                + ", description=" + mDescription + ", attachMsg=" + mAttachMsg
                + ", occurDate=" + mOccurDate + " }";
        return infoText;
    }

}
