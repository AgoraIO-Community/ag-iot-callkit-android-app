/**
 * @file IAccountMgr.java
 * @brief This file define the interface of account management
 * @author xiaohua.lu
 * @email luxiaohua@agora.io
 * @version 1.0.0.1
 * @date 2022-01-26
 * @license Copyright (C) 2021 AgoraIO Inc. All rights reserved.
 */
package io.agora.iotcallkit;


/*
 * @brief 账号管理接口
 */
public interface IAccountMgr  {

    //
    // 账号管理的状态机
    //
    public static final int ACCOUNT_STATE_IDLE = 0x0000;           ///< 当前未登录
    public static final int ACCOUNT_STATE_LOGINING = 0x0001;       ///< 正在登录用户账号
    public static final int ACCOUNT_STATE_LOGOUTING = 0x0002;      ///< 正在登出用户账号
    public static final int ACCOUNT_STATE_RUNNING = 0x0003;        ///< 当前已经有一个账号登录




    /*
     * @brief 账号管理回调接口
     */
    public static interface ICallback {

        /**
         * @brief 账号登录完成事件
         * @param account 当前登录的账号
         */
        default void onLoginDone(int errCode, final String account) {}

        /**
         * @brief 账号登出完成事件
         * @param account : 当前登出的账号
         */
        default void onLogoutDone(int errCode, final String account) {}

        /**
         * @brief 账号在其他设备上登录事件
         * @param account : 当前本地被踢的账号
         */
        default void onLoginOtherDevice(final String account) {}

        /**
         * @brief Token过期的回调事件，只能重新登录处理
         */
        default void onTokenInvalid() {}
    }




    ////////////////////////////////////////////////////////////////////////
    //////////////////////////// Public Methods ///////////////////////////
    ////////////////////////////////////////////////////////////////////////
    /**
     * @brief 获取当前账号管理状态机
     * @return 返回状态机
     */
    int getStateMachine();

    /**
     * @brief 注册回调接口
     * @param callback : 回调接口
     * @return 错误码
     */
    int registerListener(IAccountMgr.ICallback callback);

    /**
     * @brief 注销回调接口
     * @param callback : 回调接口
     * @return 错误码
     */
    int unregisterListener(IAccountMgr.ICallback callback);

    /**
     * @brief 登录一个用户账号，触发 onLoginDone() 回调
     * @param account : 要登录的用户账号
     * @return 错误代码
     */
    int login(final String account);

    /**
     * @brief 登出当前账号，触发 onLogoutDone() 回调
     */
    int logout();


    /**
     * @brief 获取当前已经登录的账号名称，如果未登录则返回null
     *
     */
    String getAccount();

    /**
     * @brief 获取当前已经登录的账号Id，这是由SDK内部生成的唯一Id，如果未登录则返回null
     *
     */
    String getAccountId();



}
