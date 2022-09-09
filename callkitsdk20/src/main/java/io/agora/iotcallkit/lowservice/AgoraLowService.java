package io.agora.iotcallkit.lowservice;


import android.os.Build;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import io.agora.iotcallkit.ErrCode;
import io.agora.iotcallkit.IAccountMgr;
import io.agora.iotcallkit.logger.ALog;

public class AgoraLowService {

    //
    // response err code
    //
    private static final int RESP_OK = 0;                   ///< 没有错误
    private static final int RESP_KNOWN = 1;                ///< 未知错误
    private static final int RESP_PARAM_INVALID = 1001;     ///< 参数错误
    private static final int RESP_SYS_EXCEPTION = 1016;     ///< 系统异常
    private static final int RESP_TOKEN_INVALID = 1010;     ///< Token过期或者失效
    private static final int RESP_NOT_ALLOW_OPT = 1033;     ///< 不允许的操作
    private static final int RESP_VERYCODE_ERR = 1040;      ///< 验证码错误
    private static final int RESP_UPGRADE_INVALID = 2324;   ///< 设备升级信息不存在或无效
    private static final int RESP_DEV_NOT_EXIST = 12011;    ///< 设备找不到
    private static final int RESP_USER_ALREADY_EXIST = 10001;   ///< 账户已经存在
    private static final int RESP_USER_NOT_EXIST = 10002;       ///< 账户不存在
    private static final int RESP_PASSWORD_ERR = 10003;         ///< 密码错误
    private static final int RESP_DEV_ALREADY_SHARED = 12013;   ///< 设备已经共享到同一个账号了




    ////////////////////////////////////////////////////////////////////////
    //////////////////////// Data Structure Definition /////////////////////
    ////////////////////////////////////////////////////////////////////////
    /*
     * @brief HTTP请求后，服务器回应数据
     */
    private static class ResponseObj {
        public int mErrorCode;                ///< 错误码
        public int mRespCode;               ///< 回应数据包中HTTP代码
        public String mTip;                 ///< 回应数据
        public JSONObject mRespJsonObj;     ///< 回应包中的JSON对象
    }

    /*
     * @brief 登录成功后的账号信息
     */
    public static class AccountInfo {
        public String mAccount;                 ///< 账号
        public String mEndpoint;                ///< iot 平台节点
        public String mRegion;                  ///< 节点
        public String mPlatformToken;           ///< 平台凭证
        public int mExpiration;                 ///< 过期时间
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

    }


    /*
     * @brief 内部设备信息
     */
    public static class DevInfo {
        public String mAppUserId;           ///< 用户Id
        public String mUserType;             ///< 用户角色：1--所有者; 2--管理员; 3--成员

        public String mProductId;           ///< 产品Id
        public String mProductKey;        ///< 产品名
        public String mDeviceId;            ///< 设备唯一的Id
        public String mDeviceName;        ///< 设备名
        public String mDeviceMac;         ///< 设备MAC地址

        public long mCreateTime = -1;     ///< 创建时间戳，-1表示未设置
        public long mUpdateTime = -1;     ///< 最后一次更新时间戳，-1表示未设置

        public boolean mConnected = false; ///< 是否在线

        public String mSharer;            ///< 分享人的用户Id，如果自己配网则是 0
        public int mShareCount = -1;      ///< 当前分享个数，-1表示未设置
        public int mShareType = -1;       ///< 共享类型，-1表示未设置
    }



    ////////////////////////////////////////////////////////////////////////
    //////////////////////// Constant Definition ///////////////////////////
    ////////////////////////////////////////////////////////////////////////
    private static final String TAG = "AgoraLowService";
    private static final int HTTP_TIMEOUT = 8000;


    ////////////////////////////////////////////////////////////////////////
    //////////////////////// Variable Definition ///////////////////////////
    ////////////////////////////////////////////////////////////////////////
    private static AgoraLowService mInstance = null;

    ///< 服务器请求站点
    private String mServerBaseUrl = "https://un2nfllop5.execute-api.cn-north-1.amazonaws.com.cn/Prod";
    private String mAppShadowProductKey = "EJImmKSK6m54R5l";  ///< APP影子虚拟设备


    ////////////////////////////////////////////////////////////////////////
    ////////////////////////// Public Methods //////////////////////////////
    ////////////////////////////////////////////////////////////////////////
    public static AgoraLowService getInstance() {
        if(mInstance == null) {
            synchronized (AgoraLowService.class) {
                if(mInstance == null) {
                    mInstance = new AgoraLowService();
                }
            }
        }
        return mInstance;
    }

    public void setBaseUrl(final String baseUrl) {
        mServerBaseUrl = baseUrl;
        ALog.getInstance().e(TAG, "<setBaseUrl> mServerBaseUrl=" + mServerBaseUrl);
    }


    /*
     * @brief 注册用户账号
     * @param clientId : 品牌标识
     * @param account : 要注册的账号
     * @param password : 账号密码
     * @param code : 账号注册验证码
     * @param phoneNumber: 手机号码，必须以+86开头
     * @param eMail: 邮箱，与手机号码 二选一
     * @return 错误码，0表示成功
     */
    public int accountRegister(long clientId, final String account, final String password,
                               final String code,
                               final String phoneNumber, final String eMail) {
        Map<String, String> params = new HashMap();
        JSONObject body = new JSONObject();

        // 请求URL
        String requestUrl = mServerBaseUrl + "/user/register";

        // 请求参数
        try {
            body.put("merchantId", Long.toString(clientId));
            body.put("account", account);
            body.put("password", password);
            if (!code.isEmpty()) {
                body.put("code", code);
            }

            // 手机号码 与 邮箱 只能二选一，并且手机号码必须加"+86"开头
            if ((phoneNumber != null) && (phoneNumber.length() > 4)) {
                String phone = phoneNumber;
                String phoneHead = phoneNumber.substring(0, 3);
                if (phoneHead.compareToIgnoreCase("+86") != 0) {
                    phone = "+86" + phoneNumber;
                }
                body.put("mobilephone", phone);

            } else if ((eMail != null) && (eMail.length() > 2)) {
                body.put("email", eMail);
            }
        } catch (JSONException jsonException) {
            jsonException.printStackTrace();
            ALog.getInstance().e(TAG, "<accountRegister> failure set JSON object!");
            return ErrCode.XERR_HTTP_JSON_WRITE;
        }

        ResponseObj responseObj = requestToServer(requestUrl, "POST", null, params, body);
        if (responseObj == null) {
            ALog.getInstance().e(TAG, "<accountRegister> failure with no response!");
            return ErrCode.XERR_HTTP_NO_RESPONSE;
        }
        if (responseObj.mErrorCode != ErrCode.XOK) {
            ALog.getInstance().e(TAG, "<accountRegister> failure, mErrorCode=" + responseObj.mErrorCode);
            return ErrCode.XERR_ACCOUNT_REGISTER;
        }
        if (responseObj.mRespCode != ErrCode.XOK) {
            ALog.getInstance().e(TAG, "<accountRegister> failure, mRespCode="
                    + responseObj.mRespCode);
            if (responseObj.mRespCode == 1) {  // 该账号已经注册
                return ErrCode.XERR_ACCOUNT_ALREADY_EXIST;
            }
            return mapRespErrCode(responseObj.mRespCode, ErrCode.XERR_ACCOUNT_REGISTER);
        }

        ALog.getInstance().d(TAG, "<accountRegister> successful"
                + ", account=" + account + ", password=" + password);
        return ErrCode.XOK;
    }

    /*
     * @brief 注销用户账号
     * @return 错误码，0表示成功
     */
    public int accountUnregister(final String srvToken) {
        Map<String, String> params = new HashMap();
        JSONObject body = new JSONObject();

        // 请求URL
        String requestUrl = mServerBaseUrl + "/user/cancel";

        // 请求参数

        // 发送HTTP请求
        ResponseObj responseObj = requestToServer(requestUrl, "POST", srvToken, params, body);
        if (responseObj == null) {
            ALog.getInstance().e(TAG, "<accountUnregister> failure with no response!");
            return ErrCode.XERR_HTTP_NO_RESPONSE;
        }
        if (responseObj.mErrorCode != ErrCode.XOK) {
            ALog.getInstance().e(TAG, "<accountUnregister> failure, mErrorCode="
                    + responseObj.mErrorCode);
            return ErrCode.XERR_ACCOUNT_UNREGISTER;
        }
        if (responseObj.mRespCode != ErrCode.XOK) {
            ALog.getInstance().e(TAG, "<accountUnregister> failure, mRespCode="
                    + responseObj.mRespCode);
            return mapRespErrCode(responseObj.mRespCode, ErrCode.XERR_ACCOUNT_UNREGISTER);
        }

        ALog.getInstance().d(TAG, "<accountUnregister> successful");
        return ErrCode.XOK;
    }

    /*
     * @brief  根据账号和密码 登录用户账号
     * @param account : 要登录的账号
     * @param password : 要登录的账号密码
     * @return 账号信息，如果返回null表示登录失败
     */
    public static class LoginResult {
        public int mErrCode = ErrCode.XOK;
        public AccountInfo mAccountInfo;
    }
    public LoginResult accountLogin(final String account, final String password) {
        Map<String, String> params = new HashMap();
        JSONObject body = new JSONObject();
        LoginResult result = new LoginResult();

        // 请求URL
        String requestUrl = mServerBaseUrl + "/user/login";

        // 请求参数
        try {
            body.put("account", account);
            body.put("password", password);
        } catch (JSONException jsonException) {
            jsonException.printStackTrace();
            ALog.getInstance().e(TAG, "<accountLogin> failure set JSON object!");
            result.mErrCode = ErrCode.XERR_HTTP_JSON_WRITE;
            return result;
        }

        ResponseObj responseObj = requestToServer(requestUrl, "POST", null, params, body);
        if (responseObj == null) {
            ALog.getInstance().e(TAG, "<accountLogin> failure with no response!");
            result.mErrCode = ErrCode.XERR_HTTP_NO_RESPONSE;
            return result;
        }
        if (responseObj.mErrorCode != ErrCode.XOK) {
            ALog.getInstance().e(TAG, "<accountLogin> failure!");
            result.mErrCode = ErrCode.XERR_ACCOUNT_LOGIN;
            return result;
        }
        if (responseObj.mRespCode != ErrCode.XOK) {
            ALog.getInstance().e(TAG, "<accountLogin> failure, mRespCode="
                    + responseObj.mRespCode);
            result.mErrCode = mapRespErrCode(responseObj.mRespCode, ErrCode.XERR_ACCOUNT_LOGIN);
            return result;
        }
        if (responseObj.mRespJsonObj == null) {
            ALog.getInstance().e(TAG, "<accountLogin> NO response object!");
            result.mErrCode = ErrCode.XERR_HTTP_NO_RESPONSE;
            return result;
        }

        // 解析账号信息
        result.mAccountInfo = new AccountInfo();
        try {
            JSONObject infoObj = responseObj.mRespJsonObj.getJSONObject("info");
            result.mAccountInfo.mAccount = infoObj.getString("account");
            result.mAccountInfo.mEndpoint = infoObj.getString("endpoint");
            result.mAccountInfo.mRegion = infoObj.getString("region");
            result.mAccountInfo.mExpiration = infoObj.getInt("expiration");
            result.mAccountInfo.mPlatformToken= infoObj.getString("granwin_token");

            JSONObject poolObj = infoObj.getJSONObject("pool");
            result.mAccountInfo.mPoolIdentifier = poolObj.getString("identifier");
            result.mAccountInfo.mPoolIdentityId = poolObj.getString("identityId");
            result.mAccountInfo.mIdentityPoolId = poolObj.getString("identityPoolId");
            result.mAccountInfo.mPoolToken = poolObj.getString("token");

            JSONObject proofObj = infoObj.getJSONObject("proof");
            result.mAccountInfo.mProofAccessKeyId = proofObj.getString("accessKeyId");
            result.mAccountInfo.mProofSecretKey = proofObj.getString("secretKey");
            result.mAccountInfo.mProofSessionToken = proofObj.getString("sessionToken");
            result.mAccountInfo.mProofSessionExpiration = proofObj.getLong("sessionExpiration");

            // 拼接user映射的虚拟设备thing name
            result.mAccountInfo.mInventDeviceName = queryInventDeviceName(result.mAccountInfo.mPlatformToken);

            result.mErrCode = ErrCode.XOK;

        } catch (JSONException e) {
            e.printStackTrace();
            ALog.getInstance().e(TAG, "<accountLogin> [JSONException], error=" + e);
            result.mErrCode = ErrCode.XERR_HTTP_JSON_PARSE;
            return result;
        }

        ALog.getInstance().d(TAG, "<accountLogin> successful"
                + ", account=" + account + ", password=" + password);
        return result;
    }



    ////////////////////////////////////////////////////////////////////////
    ///////////////////////////// Inner Methods ///////////////////////////
    ////////////////////////////////////////////////////////////////////////
    /*
     * @brief 给服务器发送HTTP请求，并且等待接收回应数据
     *        该函数是阻塞等待调用，因此最好是在工作线程中执行
     */
    private synchronized ResponseObj requestToServer(String baseUrl, String method, String token,
                                                     Map<String, String> params, JSONObject body) {

        ResponseObj responseObj = new ResponseObj();

        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            responseObj.mErrorCode = ErrCode.XERR_HTTP_URL;
            ALog.getInstance().e(TAG, "<requestToServer> Invalid url=" + baseUrl);
            return responseObj;
        }

        // 拼接URL和请求参数生成最终URL
        String realURL = baseUrl;
        if (!params.isEmpty()) {
            Iterator<Map.Entry<String, String>> it = params.entrySet().iterator();
            Map.Entry<String, String> entry =  it.next();
            realURL += "?" + entry.getKey() + "=" + entry.getValue();
            while (it.hasNext()) {
                entry =  it.next();
                realURL += "&" + entry.getKey() + "=" + entry.getValue();
            }
        }

        // 支持json格式消息体
        String realBody = String.valueOf(body);

        ALog.getInstance().d(TAG, "<requestToServer> requestUrl=" + realURL
                + ", requestBody="  + realBody.toString());

        //开启子线程来发起网络请求
        HttpURLConnection connection = null;
        BufferedReader reader = null;
        StringBuilder response = new StringBuilder();


        //同步方式请求HTTP，因此请求操作最好放在工作线程中进行
        try {
            java.net.URL url = new URL(realURL);
            connection = (HttpURLConnection) url.openConnection();
            // 设置token
            if ((token != null) && (!token.isEmpty())) {
                connection.setRequestProperty("token", token);
            }

            switch (method) {
                case "GET":
                    connection.setRequestMethod("GET");
                    break;

                case "POST":
                    connection.setDoOutput(true);
                    connection.setDoInput(true);
                    connection.setRequestMethod("POST");
                    connection.setRequestProperty("Content-Type", "application/json;charset=utf-8");
                    DataOutputStream os = new DataOutputStream(connection.getOutputStream());
                    os.write(realBody.getBytes());  // 必须是原始数据流，否则中文乱码
                    os.flush();
                    os.close();
                    break;

                case "SET":
                    connection.setRequestMethod("SET");
                    break;

                case "DELETE":
                    connection.setRequestMethod("DELETE");
                    break;

                default:
                    ALog.getInstance().e(TAG, "<requestToServer> Invalid method=" + method);
                    responseObj.mErrorCode = ErrCode.XERR_HTTP_METHOD;
                    return responseObj;
            }
            connection.setReadTimeout(HTTP_TIMEOUT);
            connection.setConnectTimeout(HTTP_TIMEOUT);
            responseObj.mRespCode = connection.getResponseCode();
            if (responseObj.mRespCode != HttpURLConnection.HTTP_OK) {
                responseObj.mErrorCode = ErrCode.XERR_HTTP_RESP_CODE + responseObj.mRespCode;
                ALog.getInstance().e(TAG, "<requestToServer> Error response code="
                        + responseObj.mRespCode + ", errMessage=" + connection.getResponseMessage());
                return responseObj;
            }

            // 读取回应数据包
            InputStream inputStream = connection.getInputStream();
            reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }

            JSONObject data = null;
            try {
                responseObj.mRespJsonObj = new JSONObject(response.toString());
                responseObj.mRespCode = responseObj.mRespJsonObj.getInt("code");
                responseObj.mTip = responseObj.mRespJsonObj.getString("tip");

            } catch (JSONException e) {
                e.printStackTrace();
                ALog.getInstance().e(TAG, "<requestToServer> Invalied json=" + response);
                responseObj.mErrorCode = ErrCode.XERR_HTTP_RESP_DATA;
                responseObj.mRespJsonObj = null;
            }

            ALog.getInstance().d(TAG, "<requestToServer> finished, response="  + response.toString());
            return responseObj;

        } catch (Exception e) {
            e.printStackTrace();
            responseObj.mErrorCode = ErrCode.XERR_HTTP_CONNECT;
            return responseObj;

        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }


    /*
     * @brief  查询user对应的虚拟设备（首次调用创建虚拟设备）
     * @param accountInfo : 登录的账号信息
     * @return 获取到的虚拟设备thing name
     */
    public String queryInventDeviceName(final String srvToken) {
        Map<String, String> params = new HashMap();
        JSONObject body = new JSONObject();

        // 请求URL
        String requestUrl = mServerBaseUrl + "/device/invent/certificate/get";

        ResponseObj responseObj = requestToServer(requestUrl, "POST",
                srvToken, params, body);
        if (responseObj.mErrorCode != ErrCode.XOK) {
            ALog.getInstance().e(TAG, "<queryInventDeviceName> failure"
                    + ", toke=" + srvToken);
            return null;
        }

        // 解析虚拟设备名称
        String deviceName = "";
        try {
            JSONObject infoObj = responseObj.mRespJsonObj.getJSONObject("info");
            deviceName = infoObj.getString("thingName");
        } catch (JSONException e) {
            e.printStackTrace();
            ALog.getInstance().e(TAG, "<queryInventDeviceName> [JSONException], error=" + e);
            return null;
        }

        ALog.getInstance().d(TAG, "<queryInventDeviceName> done"
                + ", toke=" + srvToken
                + ", device name =" + deviceName);
        return deviceName;
    }


    int parseJsonIntValue(JSONObject jsonState, String fieldName, int defVal) {
        try {
            int value = jsonState.getInt(fieldName);
            return value;

        } catch (JSONException e) {
            ALog.getInstance().e(TAG, "<parseJsonIntValue> "
                    + ", fieldName=" + fieldName + ", exp=" + e.toString());
            return defVal;
        }
    }

    long parseJsonLongValue(JSONObject jsonState, String fieldName, long defVal) {
        try {
            long value = jsonState.getLong(fieldName);
            return value;

        } catch (JSONException e) {
            ALog.getInstance().e(TAG, "<parseJsonLongValue> "
                    + ", fieldName=" + fieldName + ", exp=" + e.toString());
            return defVal;
        }
    }

    String parseJsonStringValue(JSONObject jsonState, String fieldName, String defVal) {
        try {
            String value = jsonState.getString(fieldName);
            return value;

        } catch (JSONException e) {
            ALog.getInstance().e(TAG, "<parseJsonIntValue> "
                    + ", fieldName=" + fieldName + ", exp=" + e.toString());
            return defVal;
        }
    }

    Boolean parseJsonBoolValue(JSONObject jsonState, String fieldName, boolean defVal) {
        try {
            Boolean value = jsonState.getBoolean(fieldName);
            return value;

        } catch (JSONException e) {
            ALog.getInstance().e(TAG, "<parseJsonBoolValue> "
                    + ", fieldName=" + fieldName + ", exp=" + e.toString());
            return defVal;
        }
    }


    /*
     * @brief 将response code映射成返回给应用层的错误代码
     */
    int mapRespErrCode(int respCode, int defErrCode) {
        int retCode = defErrCode;

        switch (respCode) {
            case RESP_OK:
                retCode = ErrCode.XOK;
                break;

            case RESP_PARAM_INVALID:
                retCode = ErrCode.XERR_INVALID_PARAM;
                break;

            case RESP_SYS_EXCEPTION:
                retCode = ErrCode.XERR_SYSTEM;
                break;

            case RESP_TOKEN_INVALID:
                retCode = ErrCode.XERR_TOKEN_INVALID;
                break;

            case RESP_VERYCODE_ERR:
                retCode = ErrCode.XERR_ACCOUNT_VERYCODE;
                break;

            case RESP_USER_ALREADY_EXIST:
                retCode = ErrCode.XERR_ACCOUNT_ALREADY_EXIST;
                break;

            case RESP_USER_NOT_EXIST:
                retCode = ErrCode.XERR_ACCOUNT_NOT_EXIST;
                break;

            case RESP_PASSWORD_ERR:
                retCode = ErrCode.XERR_ACCOUNT_PASSWORD_ERR;
                break;

            case RESP_DEV_ALREADY_SHARED:
                retCode = ErrCode.XERR_DEVMGR_ALREADY_SHARED;
                break;
        }

        return retCode;
    }
}
