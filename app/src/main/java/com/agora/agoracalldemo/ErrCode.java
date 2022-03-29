/**
 * @file ErrCode.java
 * @brief This file define the common error code
 * @author xiaohua.lu
 * @email luxiaohua@agora.io
 * @version 1.0.0.1
 * @date 2021-12-17
 * @license Copyright (C) 2021 AgoraIO Inc. All rights reserved.
 */
package com.agora.agoracalldemo;


public class ErrCode{

    // 0: 表示正确
    public static final int XERR_NONE = 0;
    public static final int XOK = 0;

    // 通用错误码
    public static final int XERR_BASE = -1;
    public static final int XERR_UNKNOWN = -1;                  ///< 未知错误
    public static final int XERR_INVALID_PARAM = -2;            ///< 参数错误
    public static final int XERR_UNSUPPORTED = -3;              ///< 当前操作不支持
    public static final int XERR_NO_MEMORY = -4;                ///< 内存不足
    public static final int XERR_BAD_STATE = -5;                ///< 当前操作状态不正确
    public static final int XERR_BUFFER_OVERFLOW = -6;          ///< 缓冲区中数据不足
    public static final int XERR_BUFFER_UNDERFLOW = -7;         ///< 缓冲区中数据过多放不下
    public static final int XERR_COMPONENT_NOT_EXIST = -8;      ///< 相应的组件模块不存在
    public static final int XERR_TIMEOUT = -9;                  ///< 操作超时
    public static final int XERR_HW_NOT_FOUND = -10;            ///< 未找硬件设备
    public static final int XERR_NETWORK = -11;                 ///< 网络错误
    public static final int XERR_SERVICE = -12;                 ///< 服务错误
    public static final int XERR_EOF = -13;                     ///< 已经结束


    // 文件操作错误码
    public static final int XERR_FILE_BASE = -10000;
    public static final int XERR_FILE_NOT_EXIST = -10001;
    public static final int XERR_FILE_ALREADY_EXIST = -10002;
    public static final int XERR_FILE_OPEN = -10003;
    public static final int XERR_FILE_EOF = -10004;
    public static final int XERR_FILE_FULL = -10005;
    public static final int XERR_FILE_SEEK = -10006;
    public static final int XERR_FILE_READ = -10007;
    public static final int XERR_FILE_WRITE = -10008;




}


