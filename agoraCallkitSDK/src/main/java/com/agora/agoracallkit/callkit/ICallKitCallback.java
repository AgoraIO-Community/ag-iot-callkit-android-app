/**
 * @file ICallKitCallback.java
 * @brief This file define the callback events of AgoraCallKit
 * @author xiaohua.lu
 * @email luxiaohua@agora.io
 * @version 1.0.0.1
 * @date 2021-10-21
 * @license Copyright (C) 2021 AgoraIO Inc. All rights reserved.
 */
package com.agora.agoracallkit.callkit;

import com.agora.agoracallkit.beans.IotAlarm;
import com.agora.agoracallkit.utils.CloudRecordResult;

import java.util.ArrayList;
import java.util.List;

public interface ICallKitCallback {

    /*
     * @breief 账号注册完成事件
     * @param account : 注册的本地账号
     * @param errCode : 返回错误码, 0表示注册成功, 否则表示注册失败的错误码
     */
    default void onRegisterDone(CallKitAccount account, int errCode) {}

    /*
     * @breief 账号登录完成事件
     * @param account : 对应的本地登录账号
     * @param errCode : 返回错误码, 0表示登录成功, 否则表示登录失败的错误码
     */
    default void onLogInDone(CallKitAccount account, int errCode) {}

    /*
     * @breief 账号登出完成事件
     * @param account : 对应的本地登出账号
     * @param errCode : 返回错误码, 0表示登出成功, 否则表示登出失败的错误码
     */
    default void onLogOutDone(CallKitAccount account, int errCode) {}

    /*
     * @breief 该账号在其他机器上登录事件，当前机器上该账号直接登出
     */
    default void onLoginOtherDevice(CallKitAccount account) {}



    /*
     * @breief 绑定指定设备完成事件
     * @param accountId : 对应的本地已经登录的账号
     * @param bindedDevList : 当前账号已经绑定的设备列表, null表示没有剩余绑定设备了
     * @param errCode : 返回错误码, 0表示登出成功, 否则表示登出失败的错误码
     */
    default void onBindDeviceDone(CallKitAccount account, List<CallKitAccount> bindedDevList, int errCode) {}

    /*
     * @breief 解绑指定设备完成事件
     * @param account : 对应的本地已经登录的账号
     * @param bindedDevList : 当前账号剩余已绑定的设备列表, null表示没有剩余绑定设备了
     * @param errCode : 返回错误码, 0表示登出成功, 否则表示登出失败的错误码
     */
    default void onUnbindDeviceDone(CallKitAccount account, List<CallKitAccount> bindedDevList, int errCode) {}

    /*
     * @breief 查询绑定设备列表完成事件
     * @param account : 对应的本地已经登录的用户账号
     * @param bindedDevList : 当前账号已经绑定的设备列表, null表示没有绑定设备
     * @param errCode : 返回错误码, 0表示查询成功
     */
    default void onQueryBindDevListDone(CallKitAccount account, List<CallKitAccount> bindedDevList, int errCode) {}

    /*
     * @breief 查询绑定用户列表完成事件
     * @param account : 对应的本地已经登录的设备账号
     * @param bindedUserList : 当前设备已经绑定的用户列表, null表示没有绑定用户
     * @param errCode : 返回错误码, 0表示查询成功
     */
    default void onQueryBindUserListDone(CallKitAccount account, List<CallKitAccount> bindedUserList, int errCode) {}


    /*
     * @breief 主叫时拨号成功状态，等待对端接听
     * @param account : 本地端z主叫的账号
     * @param dialAccountList : 对端被叫的账号列表
     * @param errCode : 错误代码，< 0 表示拨号错误
     */
    default void onDialDone(CallKitAccount account, List<CallKitAccount> dialAccountList, int errCode) {}

    /*
     * @breief 被叫方时才会收到该消息，表明有远端的呼叫
     * @param account : 本地端被叫的账号
     * @param peer_account : 对端主叫账号
     * @param attachMsg : 呼叫时附带的消息
     */
    default void onPeerIncoming(CallKitAccount account, CallKitAccount peer_account, String attachMsg) {}

    /*
     * @breief 主叫方时才会收到该消息，表明被呼叫方同意接听
     * @param account : 本地端主叫的账号
     * @param peer_account : 对端被叫账号
     */
    default void onPeerAnswer(CallKitAccount account) {}

    /*
     * @breief 主叫方时才会收到该消息，表明被呼叫方正在忙, 本地端会主动挂断
     * @param account : 本地端主叫的账号
     * @param peer_account : 对端被叫账号
     */
    default void onPeerBusy(CallKitAccount account) {}

    /*
     * @breief 主叫方时才会收到该消息，表明对端主动挂断电话，本地端会主动挂断
     * @param account : 本地端主叫的账号
     */
    default void onPeerHangup(CallKitAccount account) {}

    /*
     * @breief 主叫方时才会收到该消息，表明对端无人接听超时，本地端会主动挂断
     * @param account : 本地端主叫的账号
     * @param peer_account : 对端被叫账号
     */
    default void onPeerTimeout(CallKitAccount account) {}


    /*
     * @breief 接收到对端发送的消息
     * @param account : 本地端主叫的账号
     * @param message : 接收到的字符串消息
     */
    default void onPeerCustomizeMessage(CallKitAccount account, String message) {}



    /*
     * @breief 设备端本地开始启动云录制
     * @param account : 本地端主叫的账号
     * @param errCode : 错误代码，< 0 表示错误
     */
    default void onCloudRecordingStart(CallKitAccount account, int errCode) {}

    /*
     * @breief 设备端本地云录制结束
     * @param account : 本地端主叫的账号
     * @param recordResult : 云录制结果，包含了文件列表和状态信息
     * @param errCode : 错误代码，< 0 表示错误
     */
    default void onCloudRecordingStop(CallKitAccount account, CloudRecordResult recordResult,
                                      int errCode) {}

    /*
     * @breief 设备端告警信息已经发送
     * @param account : 本地端主叫的账号
     * @param alarmMsg : 发送的告警消息
     * @param errCode : 错误代码，< 0 表示错误
     */
    default void onAlarmSendDone(CallKitAccount account, String alarmMsg, int errCode) {}

    /*
     * @breief 用户端接收到告警消息
     * @param account : 本地端主叫的账号
     * @param peer_account : 发送告警的对端账号
     * @param timestamp : 消息发送的时间戳
     * @param alarmMsg : 发送的告警消息
     */
    default void onAlarmReceived(CallKitAccount account, CallKitAccount peer_account,
                                 long timestamp, String alarmMsg) {}


    /*
     * @breief 从服务器查询到告警记录
     * @param account : 本地端主叫的账号
     * @param queryParam : 相应的查询参数
     * @param alarmList : 返回查询到的告警记录
     */
    default void onAlarmQueried(CallKitAccount account, AgoraCallKit.AlarmQueryParam queryParam,
                                 ArrayList<IotAlarm> alarmList) {}

    /*
     * @breief 用户端接收到设备端告警信息，针对Apical项目
     * @param account : 本地端主叫的账号
     * @param queryParam : 相应的查询参数
     * @param alarmList : 返回查询到的告警记录
     */
    default void onIotAlarmReceived(CallKitAccount account, CallKitAccount peer_account,
                                IotAlarm iotAlarm) {}
}
