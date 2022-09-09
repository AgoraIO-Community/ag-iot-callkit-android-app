package io.agora.iotcallkit.callkit;

import io.agora.iotcallkit.ErrCode;
import io.agora.iotcallkit.logger.ALog;
import io.agora.iotcallkit.lowservice.AgoraLowService;
import io.agora.iotcallkit.sdkimpl.AccountMgr;
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;




public class AgoraService {

    ////////////////////////////////////////////////////////////////////////
    //////////////////////// Data Structure Definition /////////////////////
    ////////////////////////////////////////////////////////////////////////
    /*
     * @brief HTTP请求后，服务器回应数据
     */
    private static class ResponseObj {
        public int mErrorCode;              ///< 错误码
        public int mRespCode;               ///< 回应数据包中HTTP代码
        public String mTip;                 ///< 回应数据
        public JSONObject mRespJsonObj;     ///< 回应包中的JSON对象
    }

    ////////////////////////////////////////////////////////////////////////
    //////////////////////// Constant Definition ///////////////////////////
    ////////////////////////////////////////////////////////////////////////
    private static final String TAG = "IOTSDK/AgoraService";
    private static final int HTTP_TIMEOUT = 8000;

    public static final int RESP_CODE_IN_TALKING = 100001;      ///<	对端通话中，无法接听
    public static final int RESP_CODE_ANSWER = 100002;          ///<	未通话，无法接听
    public static final int RESP_CODE_HANGUP = 100003;          ///<	未通话，无法挂断
    public static final int RESP_CODE_ANSWER_TIMEOUT = 100004;  ///< 接听等待超时
    public static final int RESP_CODE_CALL = 100005;            ///< 呼叫中，无法再次呼叫
    public static final int RESP_CODE_INVALID_ANSWER = 100006;  ///< 无效的Answer应答
    public static final int RESP_CODE_PEER_UNREG = 999999;      ///< 被叫端未注册


    ////////////////////////////////////////////////////////////////////////
    //////////////////////// Variable Definition ///////////////////////////
    ////////////////////////////////////////////////////////////////////////
    private static AgoraService mInstance = null;

    ///< 服务器请求站点
    private String mBaseUrl = "";
    private String mCallkitBaseUrl= "http://iot-api-gateway.sh.agoralab.co/api/call-service/v1";
    private String mAuthBaseUrl   = "http://iot-api-gateway.sh.agoralab.co/api/oauth";


    ////////////////////////////////////////////////////////////////////////
    ////////////////////////// Public Methods //////////////////////////////
    ////////////////////////////////////////////////////////////////////////
    public static AgoraService getInstance() {
        if (mInstance == null) {
            synchronized (AgoraService.class) {
                if (mInstance == null) {
                    mInstance = new AgoraService();
                }
            }
        }
        return mInstance;
    }

    public void setBaseUrl(final String baseUrl) {
        mBaseUrl = baseUrl;
        mCallkitBaseUrl= baseUrl + "/call-service/v1";
        mAuthBaseUrl   = baseUrl + "/oauth";
        ALog.getInstance().e(TAG, "<setBaseUrl> mCallkitBaseUrl=" + mCallkitBaseUrl);
        ALog.getInstance().e(TAG, "<setBaseUrl> mAuthBaseUrl=" + mAuthBaseUrl);
    }


    //////////////////////////////////////////////////////////////////////////////////
    ////////////////////////// Methods for Callkit Module ////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////
    /*
     * @brief 发起一个呼叫请求
     * @param appid : Agora AppId，来自于声网开发者平台
     * @param identityId : MQTT的本地client ID，来自于AWS登录账号
     * @param peerId : 呼叫目标client ID
     * @param attachMsg : 附加消息
     * @return 服务端分配的RTC通道信息
     */
    public static class CallReqResult {
        public int mErrCode;
        public CallkitContext mCallkitCtx;
    }

    public CallReqResult makeCall(final String token, final String appid, final String identityId,
                                  final String peerId, final String attachMsg) {
        Map<String, String> params = new HashMap();
        JSONObject body = new JSONObject();
        CallReqResult callReqResult = new CallReqResult();

        // 请求URL
        String requestUrl = mCallkitBaseUrl + "/call";

        // body内容
        JSONObject header = new JSONObject();
        try {
            header.put("traceId", appid + "-" + identityId);
            header.put("timestamp", System.currentTimeMillis());
            body.put("header", header);
            JSONObject payload = new JSONObject();
            payload.put("appId", appid);
            payload.put("callerId", identityId);
            JSONArray calleeDeviceIds = new JSONArray();
            calleeDeviceIds.put(peerId);                    // TODO：目前不支持一呼多
            payload.put("calleeIds", calleeDeviceIds);
            payload.put("attachMsg", attachMsg);
            body.put("payload", payload);
        } catch (JSONException e) {
            e.printStackTrace();
            callReqResult.mErrCode = ErrCode.XERR_HTTP_JSON_WRITE;
            return callReqResult;
        }

        AgoraService.ResponseObj responseObj = requestToServer(requestUrl, "POST",
                token, params, body);
        if (responseObj == null) {
            ALog.getInstance().e(TAG, "<makeCall> failure with no response!");
            callReqResult.mErrCode = ErrCode.XERR_HTTP_NO_RESPONSE;
            return callReqResult;
        }
        ALog.getInstance().d(TAG, "<makeCall> responseObj=" + responseObj.toString());
        if (responseObj.mErrorCode != ErrCode.XOK) {
            ALog.getInstance().e(TAG, "<makeCall> failure, mErrorCode=" + responseObj.mErrorCode);
            callReqResult.mErrCode = ErrCode.XERR_HTTP_RESP_DATA;
            return callReqResult;
        }

        if (responseObj.mRespCode == RESP_CODE_IN_TALKING) {
            ALog.getInstance().e(TAG, "<makeCall> bad status IN_TALKING, mRespCode="
                    + responseObj.mRespCode);
            callReqResult.mErrCode = ErrCode.XERR_CALLKIT_PEER_BUSY;
            return callReqResult;

        } else if (responseObj.mRespCode == RESP_CODE_ANSWER) {
            ALog.getInstance().e(TAG, "<makeCall> bad status ANSWER");
            callReqResult.mErrCode = ErrCode.XERR_CALLKIT_ANSWER;
            return callReqResult;

        } else if (responseObj.mRespCode == RESP_CODE_HANGUP) {
            ALog.getInstance().e(TAG, "<makeCall> bad status HANGUP");
            callReqResult.mErrCode = ErrCode.XERR_CALLKIT_HANGUP;
            return callReqResult;

        } else if (responseObj.mRespCode == RESP_CODE_ANSWER_TIMEOUT) {
            ALog.getInstance().e(TAG, "<makeCall> bad status ANSWER_TIMEOUT");
            callReqResult.mErrCode = ErrCode.XERR_CALLKIT_TIMEOUT;
            return callReqResult;

        } else if (responseObj.mRespCode == RESP_CODE_CALL) {
            ALog.getInstance().e(TAG, "<makeCall> bad status CALL");
            callReqResult.mErrCode = ErrCode.XERR_CALLKIT_LOCAL_BUSY;
            return callReqResult;

        } else if (responseObj.mRespCode == RESP_CODE_INVALID_ANSWER) {
            ALog.getInstance().e(TAG, "<makeCall> bad status INVALID_ANSWER");
            callReqResult.mErrCode = ErrCode.XERR_CALLKIT_ERR_OPT;
            return callReqResult;

        } else if (responseObj.mRespCode == RESP_CODE_PEER_UNREG) {
            ALog.getInstance().e(TAG, "<makeCall> bad status PEER_UNREG");
            callReqResult.mErrCode = ErrCode.XERR_CALLKIT_PEER_UNREG;
            return callReqResult;

        } else if (responseObj.mRespCode != ErrCode.XOK) {
            ALog.getInstance().e(TAG, "<makeCall> status failure, mRespCode="
                    + responseObj.mRespCode);
            callReqResult.mErrCode = ErrCode.XERR_HTTP_RESP_CODE;
            return callReqResult;
        }

        // 解析呼叫请求返回结果
        CallkitContext rtcInfo = new CallkitContext();
        try {
            JSONObject dataObj = responseObj.mRespJsonObj.getJSONObject("data");
            rtcInfo.appId = parseJsonStringValue(dataObj, "appId", null);
            rtcInfo.callerId = identityId;
            rtcInfo.calleeId = peerId;
            rtcInfo.channelName = parseJsonStringValue(dataObj, "channelName", null);
            rtcInfo.rtcToken = parseJsonStringValue(dataObj, "rtcToken", null);
            rtcInfo.uid = parseJsonStringValue(dataObj, "uid", null);
            rtcInfo.peerUid = parseJsonStringValue(dataObj, "peerUid", null);
            rtcInfo.deviceAlias = parseJsonStringValue(dataObj, "deviceAlias", null);
            rtcInfo.sessionId = parseJsonStringValue(dataObj, "sessionId", null);
            rtcInfo.callStatus = parseJsonIntValue(dataObj, "callStatus", -1);

            if (rtcInfo.uid != null) {
                rtcInfo.mLocalUid = Integer.valueOf(rtcInfo.uid);
            }
            if (rtcInfo.peerUid != null) {
                rtcInfo.mPeerUid = Integer.valueOf(rtcInfo.peerUid);
            }

            callReqResult.mErrCode = ErrCode.XOK;
            callReqResult.mCallkitCtx = rtcInfo;

        } catch (JSONException e) {
            e.printStackTrace();
            callReqResult.mErrCode =  ErrCode.XERR_HTTP_JSON_PARSE;
            return callReqResult;
        }

        return callReqResult;
    }

    /*
     * @brief 发送呼叫接听响应
     * @param appid : seesionId，呼叫会话的session ID
     * @param callerId : 呼叫会话的发起方ID
     * @param calleeId : 呼叫会话的被叫方ID
     * @param isAccept : true：接听，false：挂断
     * @return 0：成功，<0：失败
     */
    public int makeAnswer(final String token,
                          final String sessionId, final String callerId, final String calleeId,
                          final String localId, final boolean isAccept)  {
        Map<String, String> params = new HashMap();
        JSONObject body = new JSONObject();

        // 请求URL
        String requestUrl = mCallkitBaseUrl + "/answer";

        // body内容
        JSONObject header = new JSONObject();
        try {
            header.put("traceId", sessionId + "-" + callerId + "-" + calleeId);
            header.put("timestamp", System.currentTimeMillis());
            body.put("header", header);
            JSONObject payload = new JSONObject();
            payload.put("callerId", callerId);
            payload.put("calleeId", calleeId);
            payload.put("localId", localId);
            payload.put("sessionId", sessionId);
            payload.put("answer", isAccept ? 0 : 1);
            body.put("payload", payload);
        } catch (JSONException e) {
            e.printStackTrace();
            return ErrCode.XERR_HTTP_JSON_WRITE;
        }

        AgoraService.ResponseObj responseObj = requestToServer(requestUrl, "POST",
                token, params, body);
        if (responseObj == null) {
            ALog.getInstance().e(TAG, "<makeAnswer> failure with no response!");
            return ErrCode.XERR_HTTP_NO_RESPONSE;
        }
        if (responseObj.mErrorCode != ErrCode.XOK) {
            ALog.getInstance().e(TAG, "<makeAnswer> failure, mErrorCode=" + responseObj.mErrorCode);
            return ErrCode.XERR_CALLKIT_ANSWER;
        }
        if (responseObj.mRespCode != ErrCode.XOK) {
            ALog.getInstance().e(TAG, "<makeAnswer> failure, mRespCode="
                    + responseObj.mRespCode);
            return ErrCode.XERR_CALLKIT_ANSWER;
        }

        return ErrCode.XOK;
    }





    //////////////////////////////////////////////////////////////////////////////////
    ////////////////////////// Methods for Authorize Module ////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////
    /*
     * @brief 向服务器注册用户
     * @param account : 当前用户账号
     * @return 错误码
     */
    public int accountRegister(final String userName)  {
        Map<String, String> params = new HashMap();
        JSONObject body = new JSONObject();
        ALog.getInstance().d(TAG, "<accountRegister> [Enter] userName=" + userName);

        // 请求URL
        String requestUrl = mAuthBaseUrl + "/register";

        // body内容
        try {
            body.put("username", userName);

        } catch (JSONException e) {
            e.printStackTrace();
            ALog.getInstance().e(TAG, "<accountRegister> [Exit] failure with JSON exp!");
            return ErrCode.XERR_HTTP_JSON_WRITE;
        }

        AgoraService.ResponseObj responseObj = requestToServer(requestUrl, "POST",
                null, params, body);
        if (responseObj == null) {
            ALog.getInstance().e(TAG, "<accountRegister> [EXIT] failure with no response!");
            return ErrCode.XERR_HTTP_NO_RESPONSE;
        }
        if (responseObj.mRespCode != ErrCode.XOK) {
            ALog.getInstance().e(TAG, "<accountRegister> [EXIT] failure, mRespCode="
                    + responseObj.mRespCode);
            return ErrCode.XERR_HTTP_RESP_CODE;
        }
        if (responseObj.mErrorCode != ErrCode.XOK) {
            ALog.getInstance().e(TAG, "<accountRegister> [EXIT] failure, mErrorCode="
                    + responseObj.mErrorCode);
            return ErrCode.XERR_HTTP_RESP_DATA;
        }

        ALog.getInstance().d(TAG, "<accountRegister> [EXIT] successful");
        return ErrCode.XOK;
    }

    /*
     * @brief 向服务器匿名登录
     * @param account : 当前用户账号
     * @return AlarmPageResult：包含错误码 和 详细的告警信息
     */
    public static class LoginResult {
        public int mErrCode;

        public String mAccount;                 ///< 账号

        public String mAnonymosName;            ///< 内部分配的匿名名字
        public String mEndpoint;                ///< iot 平台节点
        public String mRegion;                  ///< 节点
        public String mPlatformToken;           ///< 平台凭证
        public long mExpiration;                ///< mGranwinToken 过期时间
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

        @Override
        public String toString() {
            String infoText = "{ mAccount=" + mAccount + ", mPoolIdentifier=" + mPoolIdentifier
                    + ", mPoolIdentityId=" + mPoolIdentityId + ", mPoolToken=" + mPoolToken
                    + ", mIdentityPoolId=" + mIdentityPoolId
                    + ", mProofAccessKeyId=" + mProofAccessKeyId
                    + ", mProofSecretKey=" + mProofSecretKey
                    + ", mProofSessionToken=" + mProofSessionToken
                    + ", mInventDeviceName=" + mInventDeviceName
                    + ", mErrCode=" + mErrCode + " }";
            return infoText;
        }
    }

    public LoginResult accountLogin(final String userName)  {
        Map<String, String> params = new HashMap();
        JSONObject body = new JSONObject();
        LoginResult loginResult = new LoginResult();
        ALog.getInstance().d(TAG, "<accountLogin> [Enter] userName=" + userName);

        // 请求URL
        String requestUrl = mAuthBaseUrl + "/anonymous-login";

        // param 内容
        params.put("username", userName);

        // 发送请求包
        AgoraService.ResponseObj responseObj = requestToServer(requestUrl, "POST",
                null, params, body);
        if (responseObj == null) {
            ALog.getInstance().e(TAG, "<accountLogin> [EXIT] failure with no response!");
            loginResult.mErrCode = ErrCode.XERR_HTTP_NO_RESPONSE;
            return loginResult;
        }
        if (responseObj.mRespCode != ErrCode.XOK) {
            ALog.getInstance().e(TAG, "<accountLogin> [EXIT] failure, mRespCode="
                    + responseObj.mRespCode);
            loginResult.mErrCode = ErrCode.XERR_HTTP_RESP_CODE;
            return loginResult;
        }
        if (responseObj.mErrorCode != ErrCode.XOK) {
            ALog.getInstance().e(TAG, "<accountLogin> [EXIT] failure, mErrorCode="
                    + responseObj.mErrorCode);
            loginResult.mErrCode = ErrCode.XERR_HTTP_RESP_DATA;
            return loginResult;
        }

        // 解析匿名登录请求返回结果
        try {
            JSONObject dataObj = responseObj.mRespJsonObj.getJSONObject("data");
            if (dataObj == null) {
                ALog.getInstance().e(TAG, "<accountLogin> [EXIT] failure, no dataObj");
                loginResult.mErrCode = ErrCode.XERR_HTTP_RESP_DATA;
                return loginResult;
            }

            loginResult.mAccount = userName;

            JSONObject infoObj = dataObj.getJSONObject("info");
            loginResult.mAnonymosName = parseJsonStringValue(infoObj, "account", null);
            loginResult.mEndpoint = parseJsonStringValue(infoObj, "endpoint", null);
            loginResult.mRegion = parseJsonStringValue(infoObj, "region", null);
            loginResult.mExpiration = parseJsonLongValue(infoObj, "expiration", -1);
            loginResult.mPlatformToken = parseJsonStringValue(infoObj, "granwin_token", null);

            JSONObject poolObj = infoObj.getJSONObject("pool");
            loginResult.mPoolIdentifier = parseJsonStringValue(poolObj, "identifier", null);
            loginResult.mPoolIdentityId = parseJsonStringValue(poolObj,"identityId", null);
            loginResult.mIdentityPoolId = parseJsonStringValue(poolObj,"identityPoolId", null);
            loginResult.mPoolToken = parseJsonStringValue(poolObj,"token", null);

            JSONObject proofObj = infoObj.getJSONObject("proof");
            loginResult.mProofAccessKeyId = parseJsonStringValue(proofObj, "accessKeyId", null);
            loginResult.mProofSecretKey = parseJsonStringValue(proofObj,"secretKey", null);
            loginResult.mProofSessionToken = parseJsonStringValue(proofObj,"sessionToken", null);
            loginResult.mProofSessionExpiration = parseJsonLongValue(proofObj,"sessionExpiration", -1);

            // 拼接user映射的虚拟设备thing name
            loginResult.mInventDeviceName = AgoraLowService.getInstance().queryInventDeviceName(
                                                loginResult.mPlatformToken);

            loginResult.mErrCode = ErrCode.XOK;

        } catch (JSONException e) {
            e.printStackTrace();
            loginResult.mErrCode = ErrCode.XERR_HTTP_JSON_PARSE;
            ALog.getInstance().e(TAG, "<accountLogin> [EXIT] failure, exp=" + e.toString());
            return loginResult;
        }

        ALog.getInstance().d(TAG, "<accountLogin> [EXIT] successful, loginResult="
                + loginResult.toString());
        return loginResult;
    }


    /*
     * @brief 向服务器请求Token信息
     * @param account : 当前用户账号
     * @param queryParam : 查询参数
     * @return AlarmPageResult：包含错误码 和 详细的告警信息
     */
    public static class RetrieveTokenParam {
        public String mGrantType;
        public String mUserName;
        public String mPassword;
        public String mScope;
        public String mClientId;
        public String mSecretKey;

        @Override
        public String toString() {
            String infoText = "{ mGrantType=" + mGrantType
                    + ", mUserName=" + mUserName + ", mPassword=" + mPassword
                    + ", mScope=" + mScope + ", mClientId=" + mClientId
                    + ", mSecretKey=" + mSecretKey + " }";
            return infoText;
        }
    }

    public static class AccountTokenInfo {
        public int mErrCode;
        public String mScope;
        public String mTokenType;
        public String mAccessToken;
        public String mRefreshToken;
        public long mExpriesIn;

        @Override
        public String toString() {
            String infoText = "{ mErrCode=" + mErrCode
                    + ", mScope=" + mScope + ", mTokenType=" + mTokenType
                    + ", mAccessToken=" + mAccessToken + ", mRefreshToken=" + mRefreshToken
                    + ", mExpriesIn=" + mExpriesIn + " }";
            return infoText;
        }
    }

    public AccountTokenInfo accountGetToken(final RetrieveTokenParam retrieveParam)  {
        AccountTokenInfo retreieveResult = new AccountTokenInfo();
        Map<String, String> params = new HashMap();
        JSONObject body = new JSONObject();
        ALog.getInstance().d(TAG, "<accountGetToken> [Enter] param=" + retrieveParam.toString());

        // 请求URL
        String requestUrl = mAuthBaseUrl + "/rest-token";

        // body内容
        try {
            if (retrieveParam.mGrantType != null) {
                body.put("grant_type", retrieveParam.mGrantType);
            }

            if (retrieveParam.mUserName != null) {
                body.put("username", retrieveParam.mUserName);
            }

            if (retrieveParam.mPassword != null) {
                body.put("password", retrieveParam.mPassword);
            }

            if (retrieveParam.mScope != null) {
                body.put("scope", retrieveParam.mScope);
            }

            if (retrieveParam.mClientId != null) {
                body.put("client_id", retrieveParam.mClientId);
            }

            if (retrieveParam.mSecretKey != null) {
                body.put("client_secret", retrieveParam.mSecretKey);
            }

        } catch (JSONException e) {
            e.printStackTrace();
            retreieveResult.mErrCode = ErrCode.XERR_HTTP_JSON_WRITE;
            ALog.getInstance().e(TAG, "<accountGetToken> [Exit] failure with JSON exp!");
            return retreieveResult;
        }

        AgoraService.ResponseObj responseObj = requestToServer(requestUrl, "POST",
                null, params, body);
        if (responseObj == null) {
            ALog.getInstance().e(TAG, "<accountGetToken> [EXIT] failure with no response!");
            retreieveResult.mErrCode = ErrCode.XERR_HTTP_NO_RESPONSE;
            return retreieveResult;
        }
        if (responseObj.mRespCode != ErrCode.XOK) {
            ALog.getInstance().e(TAG, "<accountGetToken> [EXIT] failure, mRespCode="
                    + responseObj.mRespCode);
            retreieveResult.mErrCode = ErrCode.XERR_HTTP_RESP_CODE;
            return retreieveResult;
        }
        if (responseObj.mErrorCode != ErrCode.XOK) {
            ALog.getInstance().e(TAG, "<accountGetToken> [EXIT] failure, mErrorCode="
                    + responseObj.mErrorCode);
            retreieveResult.mErrCode = ErrCode.XERR_HTTP_RESP_DATA;
            return retreieveResult;
        }


        // 解析呼叫请求返回结果
        try {
            JSONObject dataObj = responseObj.mRespJsonObj.getJSONObject("data");
            if (dataObj == null) {
                ALog.getInstance().e(TAG, "<accountGetToken> [EXIT] failure, no dataObj");
                retreieveResult.mErrCode = ErrCode.XERR_HTTP_RESP_DATA;
                return retreieveResult;
            }

            retreieveResult.mScope = parseJsonStringValue(dataObj, "scope", null);
            retreieveResult.mTokenType = parseJsonStringValue(dataObj, "token_type", null);
            retreieveResult.mAccessToken = parseJsonStringValue(dataObj, "access_token", null);
            retreieveResult.mRefreshToken = parseJsonStringValue(dataObj, "refresh_token", null);
            retreieveResult.mExpriesIn = parseJsonLongValue(dataObj, "expires_in", -1);
            retreieveResult.mErrCode = ErrCode.XOK;

        } catch (JSONException e) {
            e.printStackTrace();
            retreieveResult.mErrCode = ErrCode.XERR_HTTP_JSON_PARSE;
            ALog.getInstance().e(TAG, "<accountGetToken> [EXIT] failure, exp=" + e.toString());
            return retreieveResult;
        }

        ALog.getInstance().d(TAG, "<accountGetToken> [EXIT] successful, retreieveResult="
                + retreieveResult.toString());
        return retreieveResult;
    }


    /*
     * @brief 向服务器注册用户，并且获取Token信息
     * @param account : 当前用户账号
     * @param queryParam : 查询参数
     * @return AlarmPageResult：包含错误码 和 详细的告警信息
     */
    public static class AnonymousLoginResult {
        public int mErrCode = ErrCode.XOK;
        public AccountMgr.AccountInfo mAccountInfo = new AccountMgr.AccountInfo();
    }
     public AnonymousLoginResult  anonymousLogin(final RetrieveTokenParam retrieveParam)  {
        AnonymousLoginResult anonymousResult = new AnonymousLoginResult();

        // 登录
        LoginResult loginResult = accountLogin(retrieveParam.mUserName);
        if (loginResult.mErrCode != ErrCode.XOK) {
            anonymousResult.mErrCode = loginResult.mErrCode;
            return anonymousResult;
        }

        // 注册
        int errCode = accountRegister(retrieveParam.mUserName);
        if (errCode != ErrCode.XOK) {
            anonymousResult.mErrCode = errCode;
            return anonymousResult;
        }

        // 获取Token
        AccountTokenInfo tokenResult = accountGetToken(retrieveParam);
        if (tokenResult.mErrCode != ErrCode.XOK) {
            anonymousResult.mErrCode = tokenResult.mErrCode;
            return anonymousResult;
        }

         anonymousResult.mAccountInfo.mAccount = loginResult.mAccount;
         anonymousResult.mAccountInfo.mAnonymosName = loginResult.mAnonymosName;
         anonymousResult.mAccountInfo.mEndpoint = loginResult.mEndpoint;
         anonymousResult.mAccountInfo.mRegion = loginResult.mRegion;
         anonymousResult.mAccountInfo.mPlatformToken = loginResult.mPlatformToken;
         anonymousResult.mAccountInfo.mExpiration = loginResult.mExpiration;
         anonymousResult.mAccountInfo.mRefresh = loginResult.mRefresh;
         anonymousResult.mAccountInfo.mPoolIdentifier = loginResult.mPoolIdentifier;
         anonymousResult.mAccountInfo.mPoolIdentityId = loginResult.mPoolIdentityId;
         anonymousResult.mAccountInfo.mPoolToken = loginResult.mPoolToken;
         anonymousResult.mAccountInfo.mIdentityPoolId = loginResult.mIdentityPoolId;
         anonymousResult.mAccountInfo.mProofAccessKeyId = loginResult.mProofAccessKeyId;
         anonymousResult.mAccountInfo.mProofSecretKey = loginResult.mProofSecretKey;
         anonymousResult.mAccountInfo.mProofSessionToken = loginResult.mProofSessionToken;
         anonymousResult.mAccountInfo.mProofSessionExpiration = loginResult.mProofSessionExpiration;
         anonymousResult.mAccountInfo.mInventDeviceName = loginResult.mInventDeviceName;

         anonymousResult.mAccountInfo.mAgoraScope = tokenResult.mScope;
         anonymousResult.mAccountInfo.mAgoraTokenType = tokenResult.mTokenType;
         anonymousResult.mAccountInfo.mAgoraAccessToken = tokenResult.mAccessToken;
         anonymousResult.mAccountInfo.mAgoraRefreshToken = tokenResult.mRefreshToken;
         anonymousResult.mAccountInfo.mAgoraExpriesIn = tokenResult.mExpriesIn;

        return anonymousResult;
    }



    ////////////////////////////////////////////////////////////////////////
    ///////////////////////////// Inner Methods ///////////////////////////
    ////////////////////////////////////////////////////////////////////////
    /*
     * @brief 给服务器发送HTTP请求，并且等待接收回应数据
     *        该函数是阻塞等待调用，因此最好是在工作线程中执行
     */
    private synchronized AgoraService.ResponseObj requestToServer(String baseUrl, String method, String token,
                                                                    Map<String, String> params, JSONObject body) {

        AgoraService.ResponseObj responseObj = new AgoraService.ResponseObj();

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
                connection.setRequestProperty("authorization", "Bearer " + token);
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
                responseObj.mTip = responseObj.mRespJsonObj.getString("timestamp");

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
     * @brief 发送HTTP请求上传文件处理，并且等待接收回应数据
     *        该函数是阻塞等待调用，因此最好是在工作线程中执行
     */
    private synchronized AgoraService.ResponseObj requestFileToServer(String baseUrl,
                                                                      String token,
                                                                      String fileName,
                                                                      String fileDir,
                                                                      boolean rename,
                                                                      byte[] fileContent ) {

        AgoraService.ResponseObj responseObj = new AgoraService.ResponseObj();

        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            responseObj.mErrorCode = ErrCode.XERR_HTTP_URL;
            ALog.getInstance().e(TAG, "<requestFileToServer> Invalid url=" + baseUrl);
            return responseObj;
        }

        // 拼接URL和请求参数生成最终URL
        String realURL = baseUrl;
        ALog.getInstance().d(TAG, "<requestFileToServer> requestUrl=" + realURL);


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
                connection.setRequestProperty("authorization", "Bearer " + token);
            }


            final String NEWLINE = "\r\n";
            final String PREFIX = "--";
            final String BOUNDARY = "########";


            // 调用HttpURLConnection对象setDoOutput(true)、setDoInput(true)、setRequestMethod("POST")；
            connection.setDoInput(true);
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setRequestMethod("POST");

            // 设置Http请求头信息；（Accept、Connection、Accept-Encoding、Cache-Control、Content-Type、User-Agent）
            connection.setRequestProperty("Connection", "Keep-Alive");
            connection.setRequestProperty("Accept", "*/*");
            connection.setRequestProperty("Accept-Encoding", "gzip, deflate");
            connection.setRequestProperty("Cache-Control", "no-cache");
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + BOUNDARY);

            // 调用HttpURLConnection对象的connect()方法，建立与服务器的真实连接；
            connection.connect();

            // 调用HttpURLConnection对象的getOutputStream()方法构建输出流对象；
            DataOutputStream os = new DataOutputStream(connection.getOutputStream());

            //
            // 写入文件头的键值信息
            //
            String fileKey = "file";
            String fileContentType = "image/jpeg";
            String fileHeader = "Content-Disposition: form-data; name=\"" + fileKey
                                + "\"; filename=\"" + fileName + "\"" + NEWLINE;
            String contentType = "Content-Type: " + fileContentType + NEWLINE;
            String encodingType = "Content-Transfer-Encoding: binary" + NEWLINE;
            os.writeBytes(PREFIX + BOUNDARY + NEWLINE);
            os.writeBytes(fileHeader);
            os.writeBytes(contentType);
            os.writeBytes(encodingType);
            os.writeBytes(NEWLINE);

            //
            // 写入文件内容
            //
            os.write(fileContent);
            os.writeBytes(NEWLINE);

            //
            // 写入其他参数数据
            //
            Map<String, String> params = new HashMap<String, String>();
            params.put("fileName", fileName);
            params.put("fileDir", fileDir);
            params.put("renameFile", (rename ? "true" : "false"));

            for (Map.Entry<String, String> entry : params.entrySet()) {
                String key = entry.getKey();
                String value = params.get(key);

                os.writeBytes(PREFIX + BOUNDARY + NEWLINE);
                os.writeBytes("Content-Disposition: form-data; name=\"" + key + "\"" + NEWLINE);
                os.writeBytes(NEWLINE);

                os.write(value.getBytes());
                os.writeBytes(NEWLINE);
            }

            //
            // 写入整体结束所有的数据
            //
            os.writeBytes(PREFIX + BOUNDARY + PREFIX + NEWLINE);
            os.flush();
            os.close();

            connection.setReadTimeout(HTTP_TIMEOUT);
            connection.setConnectTimeout(HTTP_TIMEOUT);
            responseObj.mRespCode = connection.getResponseCode();
            if (responseObj.mRespCode != HttpURLConnection.HTTP_OK) {
                responseObj.mErrorCode = ErrCode.XERR_HTTP_RESP_CODE + responseObj.mRespCode;
                ALog.getInstance().e(TAG, "<requestFileToServer> Error response code="
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
                responseObj.mTip = responseObj.mRespJsonObj.getString("timestamp");

            } catch (JSONException e) {
                e.printStackTrace();
                ALog.getInstance().e(TAG, "<requestFileToServer> Invalied json=" + response);
                responseObj.mErrorCode = ErrCode.XERR_HTTP_RESP_DATA;
                responseObj.mRespJsonObj = null;
            }

            ALog.getInstance().d(TAG, "<requestFileToServer> finished, response="  + response.toString());
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

    boolean parseJsonBoolValue(JSONObject jsonState, String fieldName, boolean defVal) {
        try {
            boolean value = jsonState.getBoolean(fieldName);
            return value;

        } catch (JSONException e) {
            ALog.getInstance().e(TAG, "<parseJsonBoolValue> "
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

}
