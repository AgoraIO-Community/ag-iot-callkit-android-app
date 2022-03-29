/**
 * @file DoorbellCallback.java
 * @brief This file define the account
 * @author xiaohua.lu
 * @email luxiaohua@agora.io
 * @version 1.0.0.1
 * @date 2021-10-21
 * @license Copyright (C) 2021 AgoraIO Inc. All rights reserved.
 */
package com.agora.agoracallkit.callkit;


/*
 * @brief 呼叫系统账号信息，账号有两种类型:
 *        User类型: 账号代表的是 userId
 *        Device类型：账号代表的是 deviceId
 *        两种账号没有本质上的区别，只是区分是用于 设备端 还是 APP端
 */
public class CallKitAccount {

    //
    // Account type: userId / deviceId
    //
    public final static int ACCOUNT_TYPE_DEV = 1;           ///< 账号是 deviceId
    public final static int ACCOUNT_TYPE_USER = 2;          ///< 账号是 userId


    private String mAccountName;                    ///< 账号名
    private int mAccountType = ACCOUNT_TYPE_USER;   ///< 账号类型
    private boolean mAccountValid = true;           ///< 账号是否已经注册
    private long mUid;                              ///< 对应RTC的Uid
    private boolean mOnline = false;                ///< 当前是否在线
    private int mReverseVal;                        ///< 保留字段


    public CallKitAccount(String accountName, int accountType)   {
        mAccountName = accountName;
        mAccountType = accountType;
    }

    public String getName() {
        return mAccountName;
    }

    public void setName(String accountName) {
        mAccountName = accountName;
    }

    public int getType() {
        return mAccountType;
    }

    public void setType(int accountType) {
        mAccountType = accountType;
    }

    public void setAccountValid(boolean valid) {
        mAccountValid = valid;
    }

    public boolean isAccountValid() {
        return mAccountValid;
    }

    public long getUid() {
        return mUid;
    }

    public void setUid(long uid) {
        mUid = uid;
    }

    public void setOnline(boolean online) {
        mOnline = online;
    }

    public boolean getOnline() {
        return mOnline;
    }

    public void setReversed(int reserveVal) {
        mReverseVal = reserveVal;
    }

    public int getReverseVal() {
        return mReverseVal;
    }


    @Override
    public String toString() {
        String temp = "{ account=" + mAccountName + ", type=" + mAccountType + ", uid=" + mUid
                + ", online=" + mOnline + ", valid=" + mAccountValid + " }";
        return temp;
    }
}
