package com.agora.agoracallkit.callkit;

public interface ResultCallback<T> {
    public static final int RESULT_OK = 0;
    public static final int RESULT_SERVER_FAILED = -1;
    public static final int RESULT_SAME_ACCOUNT = -2;
    public static final int RESULT_NO_ACCOUNT = -3;
    public static final int RESULT_LOGIN_FAILED = -4;
    public static final int RESULT_LOGOUT_FAILED = -5;
    public static final int RESULT_NEW_CALL_FAILED = -6;
    public static final int RESULT_HAVENOT_INITED = -7;         //未初始化
    public static final int RESULT_INVALID_PARAME = -8;         //参数错误
    public static final int RESULT_NO_USER_LOGIN = -9;          //无已登录用户
    public static final int RESULT_INVALID_UID = -10;           //无效的UID

    void onSuccess(T arg);

    void onFailure(int errCode, String errMessage);
}
