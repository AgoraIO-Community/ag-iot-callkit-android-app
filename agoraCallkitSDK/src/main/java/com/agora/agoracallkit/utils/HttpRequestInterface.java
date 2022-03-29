package com.agora.agoracallkit.utils;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;

import com.agora.agoracallkit.beans.IotAlarm;
import com.agora.agoracallkit.beans.RtmServerInfoBean;
import com.agora.agoracallkit.beans.UidInfoBean;
import com.agora.agoracallkit.beans.UidInfoBeansMap;
import com.agora.agoracallkit.callkit.CallKitAccount;
import com.agora.agoracallkit.logger.ALog;

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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HttpRequestInterface {
    public static final int RESULT_OK = 0;
    public static final int RESULT_INVALID_URL = -1;            //URL错误
    public static final int RESULT_DISCONNECT = -2;             //Http无连接
    public static final int RESULT_NORESPONSE = -3;             //Http无响应
    public static final int RESULT_RESPONESE_NOSERVICE = -4;    //Http找不到服务器
    public static final int RESULT_REQUST_PARAMES_FAILED = -5;  //Http请求参数非法
    public static final int RESULT_RESPONSE_ERROR = -6;         //Http响应数据包错误

    private static final String TAG = "CALLKIT/HttpReqIf";
    private static HttpRequestInterface instance;

    private String mCloudRecordReqUrl = "https://api.agora.io/v1";  ///< 云录时的基本请求站点
    private String mAgoraServerBaseUrl = "";
    private String mCustomServerBaseUrl = "";
    private String mAlarmServerBaseUrl = "https://iot.sh2.agoralab.co";
    private String mBindingServerBaseUrl = "";
    private String mAgoraAppid = "";
    private String mBindingAppId = "d0177a34373b482a9c4eb4dedcfa586a";
    private boolean mIsInited = false;
    private int mErrCode = RESULT_OK;

    public static HttpRequestInterface getInstance() {
        if(instance == null) {
            synchronized (HttpRequestInterface.class) {
                if(instance == null) {
                    instance = new HttpRequestInterface();
                }
            }
        }
        return instance;
    }

    public boolean init(Context context, Bundle metaData) {
        if (mIsInited) {
            ALog.getInstance().e(TAG, "<init> HttpRequestInterface was inited.");
            return false;
        }
        setCustomServerBaseUrl(metaData.getString("CUSTOM_SERVER_URL", ""));
        setAgoraServerBaseUrl(metaData.getString("AGORA_SERVER_URL", ""));
        setBindingServerBaseUrl(metaData.getString("BINDING_SERVER_URL", ""));
        setAgoraAppid(metaData.getString("AGORA_APPID", ""));
        mIsInited = true;
        mErrCode = RESULT_OK;
        return true;
    }

    private boolean setAgoraServerBaseUrl(String url) {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            mAgoraServerBaseUrl = url;
            return true;
        } else {
            ALog.getInstance().e(TAG, "<setAgoraServerBaseUrl> Invalid URL: " + url);
            return false;
        }
    }

    private boolean setCustomServerBaseUrl(String url) {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            mCustomServerBaseUrl = url;
            return true;
        } else {
            ALog.getInstance().e(TAG, "<setCustomServerBaseUrl> Invalid URL: " + url);
            return false;
        }
    }

    private boolean setBindingServerBaseUrl(String url) {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            mBindingServerBaseUrl = url;
            return true;
        } else {
            ALog.getInstance().e(TAG, "<setBindingServerBaseUrl> Invalid URL: " + url);
            return false;
        }
    }

    private boolean setAgoraAppid(String appid) {
        mAgoraAppid = appid;
        return true;
    }

    public UidInfoBean registerWithUserAccount(String account, int type) {
        long userId = -1;
        //账号是否已经注册过了
        UidInfoBean info = UidInfoBeansMap.getInstance().searchUidInfo(account);
        if (info != null) {
            if (type != info.getType()) {
                ALog.getInstance().i(TAG, "<registerWithUserAccount> already registered, account=" + account
                        + ", type=" + type);
                return null;
            }
            ALog.getInstance().i(TAG, "<registerWithUserAccount> Found uid=" + info.getUid()
                    + " for account=" + account);
            return info;
        }
        Map<String, String> params = new HashMap();
        JSONObject body = new JSONObject();
        //根据type设定URL和参数
        String realUrl = "";
        if (type == UidInfoBean.TYPE_MAP_DEVICE) {
            realUrl = mCustomServerBaseUrl + "/device";
            params.put("deviceId", account);
        } else if (type == UidInfoBean.TYPE_MAP_USER) {
            realUrl = mCustomServerBaseUrl + "/user";
            params.put("customerAccountId", account);
        } else {
            ALog.getInstance().i(TAG, "<registerWithUserAccount> Unknown type: " + type);
            return null;
        }
        params.put("appId", mAgoraAppid);
        //向业务服务器发起注册请求
        try {
            JSONObject response = sendRequestWithHttpUrlConnection(
                    realUrl, "POST", params, body);
            if (response != null) {
                userId = Long.valueOf(response.getString("agoraUid"));
                //得到的UID信息存入缓存
                UidInfoBean uidInfo = new UidInfoBean();
                uidInfo.setAccount(account);
                uidInfo.setType(type);
                uidInfo.setUid(userId);
                UidInfoBeansMap.getInstance().saveUidInfo(uidInfo);
                return uidInfo;
            } else {
                ALog.getInstance().e(TAG, "<registerWithUserAccount> failed!");
                return null;
            }
        } catch (JSONException e) {
            e.printStackTrace();
            ALog.getInstance().e(TAG, "<registerWithUserAccount> exception=" + e.toString());
            mErrCode = RESULT_NORESPONSE;
            return null;
        }
    }

    public UidInfoBean queryUidWithAccount(String account, int type) {
        long userId = -1;
        //账号是否已经查询过了
        UidInfoBean info = UidInfoBeansMap.getInstance().searchUidInfo(account);
        if (info != null) {
            if (type != info.getType()) {
                ALog.getInstance().e(TAG, "<queryUidWithAccount> already registered, account="
                        + account + ", type=" + type + ", but need " + info.getType());
                return null;
            }
            ALog.getInstance().i(TAG, "<queryUidWithAccount> Found uid=" + info.getUid()
                    + ", for account=" + account);
            return info;
        }
        Map<String, String> params = new HashMap();
        JSONObject body = new JSONObject();
        //根据type设定URL和参数
        String realUrl = "";
        if (type == UidInfoBean.TYPE_MAP_DEVICE) {
            realUrl = mCustomServerBaseUrl + "/device";
            params.put("deviceId", account);
        } else if (type == UidInfoBean.TYPE_MAP_USER) {
            realUrl = mCustomServerBaseUrl + "/user";
            params.put("customerAccountId", account);
        } else {
            ALog.getInstance().i(TAG, "<queryUidWithAccount> Unknown type: " + type);
            return null;
        }
        params.put("appId", mAgoraAppid);
        //向业务服务发起查询请求
        try {
            JSONObject response = sendRequestWithHttpUrlConnection(
                    realUrl, "GET", params, body);
            if (response != null) {
                userId = Long.valueOf(response.getString("agoraUid"));
                //得到的UID信息存入缓存
                UidInfoBean uidInfo = new UidInfoBean();
                uidInfo.setAccount(account);
                uidInfo.setType(type);
                uidInfo.setUid(userId);
                UidInfoBeansMap.getInstance().saveUidInfo(uidInfo);
                return uidInfo;
            } else {
                ALog.getInstance().e(TAG, "<queryUidWithAccount> cannot find user account="
                        + account + ", maybe unregistered.");
                return null;
            }
        } catch (JSONException e) {
            e.printStackTrace();
            mErrCode = RESULT_NORESPONSE;
            return null;
        }
    }

    public UidInfoBean queryAccountWithUid(long uid) {
        String account = "";
        String type = "";
        //UID是否已经查询过
        UidInfoBean info = UidInfoBeansMap.getInstance().searchUidInfo(uid);
        if (info != null) {
            ALog.getInstance().i(TAG, "<queryAccountWithUid> Found account=" + info.getAccount()
                    + ", for uid=" + uid);
            return info;
        }
        //向业务服务发起查询请求
        Map<String, String> params = new HashMap();
        JSONObject body = new JSONObject();
        params.put("agoraUid", String.valueOf(uid));
        params.put("appId", mAgoraAppid);
        try {
            JSONObject response = sendRequestWithHttpUrlConnection(
                    mCustomServerBaseUrl + "/register-inf", "GET", params, body);
            if (response != null) {
                account = response.getString("account");
                type = response.getString("type");
                //得到的UID信息存入缓存
                UidInfoBean uidInfo = new UidInfoBean();
                uidInfo.setAccount(account);
                if (type.equals("user")) {
                    uidInfo.setType(UidInfoBean.TYPE_MAP_USER);
                } else if (type.equals("device")) {
                    uidInfo.setType(UidInfoBean.TYPE_MAP_DEVICE);
                }
                uidInfo.setUid(uid);
                UidInfoBeansMap.getInstance().saveUidInfo(uidInfo);
                return uidInfo;
            } else {
                ALog.getInstance().e(TAG, "<queryAccountWithUid> It's an invalid UID: " + uid);
                return null;
            }
        } catch (JSONException e) {
            e.printStackTrace();
            mErrCode = RESULT_NORESPONSE;
            return null;
        }
    }

    public RtmServerInfoBean getAgoraServerRtmPortInfo() {
        RtmServerInfoBean info = new RtmServerInfoBean();
        //向业务服务发起查询请求
        Map<String, String> params = new HashMap();
        params.put("appId", mAgoraAppid);
        JSONObject body = new JSONObject();
        try {
            JSONObject response = sendRequestWithHttpUrlConnection(
                    mAgoraServerBaseUrl + "/rtm-seeds", "GET", params, body);
            if (response != null) {
                info.setNodeCount(response.getInt("rtmCnt"));
                info.setBaseName(response.getString("rtmPrefix"));
                return info;
            } else {
                ALog.getInstance().e(TAG, "<queryAccountWithUid> cannot query Agora server RTM port count.");
                return null;
            }
        } catch (JSONException e) {
            e.printStackTrace();
            mErrCode = RESULT_NORESPONSE;
            return null;
        }
    }

    /*
     * @brief 将多个设备绑定到一个用户账号中
     * @param accountId 要绑定的 accountId
     * @param devList 要绑定的 deviceId列表
     * @return 绑定成功的 deviceId列表, null表示没有一个设备绑定成功
     */
    public ArrayList<CallKitAccount> bindDevicesToAccount(String accountId, List<String> devList) {
        Map<String, String> params = new HashMap();
        JSONObject body = new JSONObject();
        int i;

        // 根据设定URL和参数
        String realUrl = mBindingServerBaseUrl + "/bind-device";
        try {
            body.put("appId", mBindingAppId);
            body.put("customerAccountId", accountId);
            JSONArray devIdArrayObj =new JSONArray();
            for (String deviceId : devList) {
                devIdArrayObj.put(deviceId);
            }
            body.put("deviceIds", devIdArrayObj);

        } catch (JSONException jsonExp) {
            ALog.getInstance().e(TAG, "<bindDevicesToAccount> request JSON exception, error=" + jsonExp);
            jsonExp.printStackTrace();
            mErrCode = RESULT_REQUST_PARAMES_FAILED;
            return null;
        }

        //
        // 向业务服务器发起绑定请求
        //
        try {
            JSONObject response = sendRequestWithHttpUrlConnection(
                    realUrl, "POST", params, body);
            if (response != null) {
                String respAccountId = response.getString("customerAccountId");
                if (accountId.compareToIgnoreCase(respAccountId) != 0) {
                    ALog.getInstance().e(TAG, "<bindDevicesToAccount> respAccountId not right, respAccountId="
                            + respAccountId);
                    mErrCode = RESULT_RESPONSE_ERROR;
                    return null;
                }

                JSONArray idObjArray = response.getJSONArray("deviceIds");
                if (idObjArray == null) {
                    ALog.getInstance().e(TAG, "<bindDevicesToAccount> no deviceIds field");
                    mErrCode = RESULT_RESPONSE_ERROR;
                    return null;
                }
                ArrayList<CallKitAccount> bindedDevList = new ArrayList<>();
                for (i = 0; i < idObjArray.length(); i++) {
                    String deviceId = idObjArray.getString(i);
                    CallKitAccount devAcount = new CallKitAccount(deviceId, CallKitAccount.ACCOUNT_TYPE_DEV);
                    bindedDevList.add(devAcount);
                }
                if (bindedDevList.size() <= 0) {
                    ALog.getInstance().e(TAG, "<bindDevicesToAccount> no binded device");
                    mErrCode = RESULT_RESPONSE_ERROR;
                    return null;
                }

                // 解析deviceId映射的 RtcUid
                JSONObject mapObject = response.getJSONObject("uidMap");
                if (mapObject != null) {
                    for (i = 0; i < bindedDevList.size(); i++) {
                        CallKitAccount devAccount = bindedDevList.get(i);
                        // 如果找到对应的deviceId，则有映射的uid
                        try {
                            int uid = mapObject.getInt(devAccount.getName());
                            devAccount.setUid(uid);
                            bindedDevList.set(i, devAccount);
                        } catch (JSONException jsonExp) {
                            //jsonExp.printStackTrace();
                            ALog.getInstance().e(TAG, "<bindDevicesToAccount> no found uid for: " + devAccount.getName());
                        }
                    }
                }

                return bindedDevList;

            } else {
                ALog.getInstance().e(TAG, "<bindDevicesToAccount> no response return");
                mErrCode = RESULT_NORESPONSE;
                return null;
            }

        } catch (JSONException e) {
            ALog.getInstance().e(TAG, "<bindDevicesToAccount> JSONException, error=" + e);
            e.printStackTrace();
            mErrCode = RESULT_NORESPONSE;
            return null;
        }
    }

    /*
     * @brief 将多个设备从一个用户账号中解绑
     * @param accoutId 要解绑的 accountId
     * @param devList 要解绑的 deviceId列表
     * @return 返回用户账号中剩余的绑定设备, null表示用户账号中已经无绑定的设备了
     */
    public ArrayList<CallKitAccount> unbindDevicesFromAccount(String accountId, List<String> devList) {
        Map<String, String> params = new HashMap();
        JSONObject body = new JSONObject();
        int i;

        // 根据设定URL和参数
        String realUrl = mBindingServerBaseUrl + "/unbind-device";
        try {
            body.put("appId", mBindingAppId);
            body.put("customerAccountId", accountId);
            JSONArray devIdArrayObj =new JSONArray();
            for (String deviceId : devList) {
                devIdArrayObj.put(deviceId);
            }
            body.put("deviceIds", devIdArrayObj);

        } catch (JSONException jsonExp) {
            ALog.getInstance().e(TAG, "<unbindDevicesFromAccount> request JSON exception, error=" + jsonExp);
            jsonExp.printStackTrace();
            mErrCode = RESULT_REQUST_PARAMES_FAILED;
            return null;
        }

        //
        // 向业务服务器发起解绑请求
        //
        try {
            JSONObject response = sendRequestWithHttpUrlConnection(
                    realUrl, "POST", params, body);
            if (response != null) {
                String respAccountId = response.getString("customerAccountId");
                if (accountId.compareToIgnoreCase(respAccountId) != 0) {
                    ALog.getInstance().e(TAG, "<unbindDevicesFromAccount> respAccountId not right, respAccountId="
                            + respAccountId);
                    mErrCode = RESULT_RESPONSE_ERROR;
                    return null;
                }

                mErrCode = RESULT_OK;

                JSONArray idObjArray = response.getJSONArray("deviceIds");
                if (idObjArray == null) {  // 用户账号中还有剩余绑定着的设备
                    ALog.getInstance().e(TAG, "<unbindDevicesFromAccount> no deviceIds field");
                    return null;
                }
                ArrayList<CallKitAccount> remainedDevList = new ArrayList<>();
                for (i = 0; i < idObjArray.length(); i++) {
                    String deviceId = idObjArray.getString(i);
                    CallKitAccount devAcount = new CallKitAccount(deviceId, CallKitAccount.ACCOUNT_TYPE_DEV);
                    remainedDevList.add(devAcount);
                }
                if (remainedDevList.size() <= 0) {
                    ALog.getInstance().e(TAG, "<unbindDevicesFromAccount> no reamained device");
                    return null;
                }

                // 解析deviceId映射的 RtcUid
                JSONObject mapObject = response.getJSONObject("uidMap");
                if (mapObject != null) {
                    for (i = 0; i < remainedDevList.size(); i++) {
                        CallKitAccount devAccount = remainedDevList.get(i);
                        // 如果找到对应的deviceId，则有映射的uid
                        try {
                            int uid = mapObject.getInt(devAccount.getName());
                            devAccount.setUid(uid);
                            remainedDevList.set(i, devAccount);
                        } catch (JSONException jsonExp) {
                            //jsonExp.printStackTrace();
                            ALog.getInstance().e(TAG, "<unbindDevicesFromAccount> no found uid for: " + devAccount.getName());
                        }
                    }
                }
                return remainedDevList;

            } else {
                ALog.getInstance().e(TAG, "<unbindDevicesFromAccount> no response return");
                mErrCode = RESULT_NORESPONSE;
                return null;
            }

        } catch (JSONException e) {
            ALog.getInstance().e(TAG, "<unbindDevicesFromAccount> JSONException, error=" + e);
            e.printStackTrace();
            mErrCode = RESULT_NORESPONSE;
            return null;
        }
    }


    /*
     * @brief 根据 用户账号查询其绑定的设备列表列表，设备和用户账号是 多对多的 绑定关系
     * @param accountId 要查询的账号
     * @return 绑定的 deviceId 列表
     */
    public ArrayList<CallKitAccount> queryBindDevicesByAccount(String accountId) {
        Map<String, String> params = new HashMap();
        JSONObject body = new JSONObject();
        int i;

        // 根据设定URL和参数
        String realUrl = mBindingServerBaseUrl + "/device";
        params.put("appId", mBindingAppId);
        params.put("customerAccountId", accountId);


        //
        // 向业务服务器发起查询请求
        //
        try {
            JSONObject response = sendRequestWithHttpUrlConnection(
                    realUrl, "GET", params, body);
            if (response != null) {
                String respAccountId = response.getString("customerAccountId");
                if (accountId.compareToIgnoreCase(respAccountId) != 0) {
                    ALog.getInstance().e(TAG, "<queryBindDevicesByAccount> respAccountId not right, respAccountId="
                            + respAccountId);
                    mErrCode = RESULT_RESPONSE_ERROR;
                    return null;
                }

                mErrCode = RESULT_OK;

                // 解析设备Id列表
                JSONArray idObjArray = response.getJSONArray("deviceIds");
                if (idObjArray == null) {
                    ALog.getInstance().e(TAG, "<queryBindDevicesByAccount> no deviceIds field");
                    return null;
                }
                ArrayList<CallKitAccount> bindedDevList = new ArrayList<>();
                for (i = 0; i < idObjArray.length(); i++) {
                    String deviceId = idObjArray.getString(i);
                    CallKitAccount devAcount = new CallKitAccount(deviceId, CallKitAccount.ACCOUNT_TYPE_DEV);
                    bindedDevList.add(devAcount);
                }
                if (bindedDevList.size() <= 0) {
                    ALog.getInstance().e(TAG, "<queryBindDevicesByAccount> no binded device");
                    return null;
                }

                // 解析deviceId映射的 RtcUid
                JSONObject mapObject = response.getJSONObject("uidMap");
                if (mapObject != null) {
                    for (i = 0; i < bindedDevList.size(); i++) {
                        CallKitAccount devAccount = bindedDevList.get(i);
                        // 如果找到对应的deviceId，则有映射的uid
                        try {
                            int uid = mapObject.getInt(devAccount.getName());
                            devAccount.setUid(uid);
                            bindedDevList.set(i, devAccount);
                        } catch (JSONException jsonExp) {
                            //jsonExp.printStackTrace();
                            ALog.getInstance().e(TAG, "<queryBindDevicesByAccount> no found uid for: " + devAccount.getName());
                        }
                    }
                }

                return bindedDevList;

            } else {
                ALog.getInstance().e(TAG, "<queryBindDevicesByAccount> no response return");
                mErrCode = RESULT_NORESPONSE;
                return null;
            }

        } catch (JSONException e) {
            ALog.getInstance().e(TAG, "<queryBindDevicesByAccount> JSONException, error=" + e);
            e.printStackTrace();
            mErrCode = RESULT_NORESPONSE;
            return null;
        }
    }

    /*
     * @brief 根据设备Id查询其绑定的用户账号列表，设备和用户账号是 多对多的 绑定关系
     * @param deviceId 要查询的 deviceId
     * @return 绑定的 accountId 列表
     */
    public ArrayList<CallKitAccount> queryBindedAccountsByDevId(String deviceId) {
        Map<String, String> params = new HashMap();
        JSONObject body = new JSONObject();
        int i;

        // 根据设定URL和参数
        String realUrl = mBindingServerBaseUrl + "/account";
        params.put("appId", mAgoraAppid);
        params.put("deviceId", deviceId);

        // 向业务服务器发起查询请求
        try {
            JSONObject response = sendRequestWithHttpUrlConnection(
                    realUrl, "GET", params, body);
            if (response != null) {

                String respDeviceId = response.getString("deviceId");
                if (deviceId.compareToIgnoreCase(respDeviceId) != 0) {
                    ALog.getInstance().e(TAG, "<queryBindedAccountsByDevId> respDeviceId not right, respDeviceId="
                            + respDeviceId);
                    mErrCode = RESULT_RESPONSE_ERROR;
                    return null;
                }

                mErrCode = RESULT_OK;

                // 解析设备Id列表
                JSONArray idObjArray = response.getJSONArray("customerAccountIds");
                if (idObjArray == null) {
                    ALog.getInstance().e(TAG, "<queryBindedAccountsByDevId> no customerAccountIds field");
                    return null;
                }
                ArrayList<CallKitAccount> bindedUserList = new ArrayList<>();
                for (i = 0; i < idObjArray.length(); i++) {
                    String userId = idObjArray.getString(i);
                    CallKitAccount userAcount = new CallKitAccount(userId, CallKitAccount.ACCOUNT_TYPE_USER);
                    bindedUserList.add(userAcount);
                }
                if (bindedUserList.size() <= 0) {
                    ALog.getInstance().e(TAG, "<queryBindedAccountsByDevId> no binded device");
                    return null;
                }

                // 解析deviceId映射的 RtcUid
                JSONObject mapObject = response.getJSONObject("uidMap");
                if (mapObject != null) {
                    for (i = 0; i < bindedUserList.size(); i++) {
                        CallKitAccount userAccount = bindedUserList.get(i);
                        // 如果找到对应的deviceId，则有映射的uid
                        try {
                            int uid = mapObject.getInt(userAccount.getName());
                            userAccount.setUid(uid);
                            bindedUserList.set(i, userAccount);
                        } catch (JSONException jsonExp) {
                            //jsonExp.printStackTrace();
                            ALog.getInstance().e(TAG, "<queryBindedAccountsByDevId> no found uid for: " + userAccount.getName());
                        }
                    }
                }
                return bindedUserList;

            } else {
                ALog.getInstance().e(TAG, "<queryBindedAccountsByDevId> no response return");
                return null;
            }

        } catch (JSONException e) {
            ALog.getInstance().e(TAG, "<queryBindedAccountsByDevId> JSONException, error=" + e);
            e.printStackTrace();
            mErrCode = RESULT_NORESPONSE;
            return null;
        }
    }

    public String getRtmToken(String userId) {
        String token;
        //向业务服务发起查询请求
        Map<String, String> params = new HashMap();
        params.put("appId", mAgoraAppid);
        params.put("userId", userId);
        JSONObject body = new JSONObject();
        try {
            JSONObject response = sendRequestWithHttpUrlConnection(
                    mAgoraServerBaseUrl + "/rtm-token", "GET", params, body);
            if (response != null) {
                token = response.getString("token");
                return token;
            } else {
                ALog.getInstance().e(TAG, "cannot get Agora server RTM token.");
                return null;
            }
        } catch (JSONException e) {
            ALog.getInstance().e(TAG, "<getRtmToken> JSONException, error=" + e);
            e.printStackTrace();
            mErrCode = RESULT_NORESPONSE;
            return null;
        }
    }

    private String getAuthorization() {
        String username = "agoraiotapaas";
        String password = "asbhe7cx2na30a";
        String auth = username+":"+password;
        String res = "";
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            res = Base64.getEncoder().encodeToString(auth.getBytes());
        } else {
            res = android.util.Base64.encodeToString(auth.getBytes(), android.util.Base64.NO_WRAP);
        }
        return res;
    }

    private synchronized JSONObject sendRequestWithHttpUrlConnection(
                                                    String baseUrl,
                                                    String method,
                                                    Map<String, String> params,
                                                    JSONObject body) {
        mErrCode = RESULT_OK;
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            mErrCode = RESULT_INVALID_URL;
            ALog.getInstance().e(TAG, "Invalid url: " + baseUrl);
            return null;
        }
        //拼接URL和请求参数生成最终URL
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
        //支持json格式消息体
        String realBody = String.valueOf(body);
        //开启子线程来发起网络请求
        HttpURLConnection connection = null;
        BufferedReader reader = null;
        StringBuilder response = new StringBuilder();
        //同步方式请求HTTP，这样界面上可以用loading控件来处理异步操作过程中的等待提示
        try {
            java.net.URL url = new URL(realURL);
            connection = (HttpURLConnection) url.openConnection();
            //设置认证
            connection.setRequestProperty("Authorization", "Basic " + getAuthorization());
            switch (method) {
                case "GET":
                    connection.setRequestMethod("GET");
                    break;
                case "POST":
                    connection.setDoOutput(true);
                    connection.setDoInput(true);
                    connection.setRequestMethod("POST");
                    connection.setRequestProperty("Content-Type", "application/json");
                    DataOutputStream os = new DataOutputStream(connection.getOutputStream());
                    os.writeBytes(realBody);
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
                    mErrCode = RESULT_INVALID_URL;
                    return null;
            }
            connection.setReadTimeout(8000);
            connection.setConnectTimeout(8000);
            if(connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                InputStream inputStream = connection.getInputStream();
                reader = new BufferedReader(new InputStreamReader(inputStream));
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                return parseResponse(response.toString());
            } else {
                mErrCode = RESULT_DISCONNECT;
                ALog.getInstance().e(TAG, "Response code: " + connection.getResponseCode());
                ALog.getInstance().e(TAG, "Error message: " + connection.getResponseMessage());
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            mErrCode = RESULT_DISCONNECT;
            return null;
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

    private synchronized JSONObject parseResponse(final String response){
        JSONObject jsonObj = null;
        int ret_code = 0;
        String message = "";
        JSONObject data = null;
        try {
            jsonObj = new JSONObject(response);
            ret_code = jsonObj.getInt("code");
            message = jsonObj.getString("message");
            if (ret_code != 200) {
                ALog.getInstance().e(TAG, "HTTP reponse failed, " + message);
                mErrCode = RESULT_RESPONESE_NOSERVICE;
                if (ret_code == 500) {
                    mErrCode = RESULT_REQUST_PARAMES_FAILED;
                }
                return null;
            }
            data = jsonObj.getJSONObject("data");
            ALog.getInstance().i(TAG, "ret_code = " + ret_code + ", message = " + message + ", data = " + data.toString());
            return data;
        } catch (JSONException e) {
            e.printStackTrace();
            ALog.getInstance().e(TAG, "Invalied json: " + response);
            mErrCode = RESULT_NORESPONSE;
            return null;
        }
    }

    public static class AlarmReqResult {
        public int mErrCode;
        public JSONArray mResponse;
    }

    private synchronized AlarmReqResult sendAlarmRequest(   String baseUrl,
                                                        String method,
                                                        Map<String, String> params,
                                                        String paramExtend,
                                                        JSONObject body) {
        mErrCode = RESULT_OK;
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            mErrCode = RESULT_INVALID_URL;
            ALog.getInstance().e(TAG, "Invalid url: " + baseUrl);
            return null;
        }
        //拼接URL和请求参数生成最终URL
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
        if ((paramExtend != null) && (!paramExtend.isEmpty())) {
            realURL += paramExtend;
        }

        //支持json格式消息体
        String realBody = String.valueOf(body);
        //开启子线程来发起网络请求
        HttpURLConnection connection = null;
        BufferedReader reader = null;
        StringBuilder response = new StringBuilder();
        //同步方式请求HTTP，这样界面上可以用loading控件来处理异步操作过程中的等待提示
        try {
            java.net.URL url = new URL(realURL);
            connection = (HttpURLConnection) url.openConnection();
            //设置认证
            connection.setRequestProperty("Authorization", "Basic " + getAuthorization());
            switch (method) {
                case "GET":
                    connection.setRequestMethod("GET");
                    break;
                case "POST":
                    connection.setDoOutput(true);
                    connection.setDoInput(true);
                    connection.setRequestMethod("POST");
                    connection.setRequestProperty("Content-Type", "application/json");
                    DataOutputStream os = new DataOutputStream(connection.getOutputStream());
                    os.writeBytes(realBody);
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
                    mErrCode = RESULT_INVALID_URL;
                    return null;
            }
            connection.setReadTimeout(8000);
            connection.setConnectTimeout(8000);
            if(connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                InputStream inputStream = connection.getInputStream();
                reader = new BufferedReader(new InputStreamReader(inputStream));
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                return parseAlarmResponse(response.toString());
            } else {
                mErrCode = RESULT_DISCONNECT;
                ALog.getInstance().e(TAG, "Response code: " + connection.getResponseCode());
                ALog.getInstance().e(TAG, "Error message: " + connection.getResponseMessage());
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            mErrCode = RESULT_DISCONNECT;
            return null;
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

    private synchronized AlarmReqResult parseAlarmResponse(final String response){
        JSONObject jsonObj = null;
        int ret_code = 0;
        String message = "";
        JSONArray data = null;
        try {
            jsonObj = new JSONObject(response);
            ret_code = jsonObj.getInt("code");
            message = jsonObj.getString("message");
            if (ret_code != 200) {
                ALog.getInstance().e(TAG, "HTTP reponse failed, " + message);
                mErrCode = RESULT_RESPONESE_NOSERVICE;
                if (ret_code == 500) {
                    mErrCode = RESULT_REQUST_PARAMES_FAILED;
                }
                return null;
            }
            try {
                data = jsonObj.getJSONArray("data");
                ALog.getInstance().i(TAG, "ret_code = " + ret_code + ", message = " + message + ", data = " + data.toString());
            } catch (JSONException e) {
            }
            AlarmReqResult result = new AlarmReqResult();
            result.mErrCode = mErrCode;
            result.mResponse = data;
            return result;

        } catch (JSONException e) {
            e.printStackTrace();
            ALog.getInstance().e(TAG, "Invalied json: " + response);
            mErrCode = RESULT_NORESPONSE;
            return null;
        }
    }


    public int getLastErrorCode() {
        return mErrCode;
    }




    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////// 云端录制请求和响应处理 ////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    /*
     * @brief 获取云录制的ResourceId
     * @param recordChannel : 云录频道
     * @param recordUid ：云录时的uid
     * @return 获取云录的ResourceId
     */
    public String cloudRecordGetResourceId(String recordChannel, long recordUid) {
        ArrayList<String> paramList = new ArrayList<>();
        JSONObject body = new JSONObject();

        // 请求示例：
        //  https://api.agora.io/v1/apps/<yourappid>/cloud_recording/acquire

        // 根据设定URL和参数
        String realUrl = mCloudRecordReqUrl;
        paramList.add("apps");
        paramList.add(mAgoraAppid);
        paramList.add("cloud_recording");
        paramList.add("acquire");

        // 设置Body
        try {
            body.put("cname", recordChannel);
            body.put("uid", String.valueOf(recordUid));

            //
            // clientRequest 结构
            //
            JSONObject clientReqBody = new JSONObject();
            clientReqBody.put("resourceExpiredHour", 24);  // 云录时效24小时
            clientReqBody.put("scene", 0);  // 实时音视频录制
            body.put("clientRequest", clientReqBody);

        } catch (Exception exp) {
            exp.printStackTrace();
            ALog.getInstance().e(TAG, "<cloudRecordGetResourceId> [EXCEPTION] exp=" + exp);
            return null;
        }

        // 向云录服务器发起请求
        try {
            JSONObject response = sendCloudRecordingRequest(
                    realUrl, "POST", paramList, body);
            if (response != null) {
                String recordResourceId = response.getString("resourceId");
                ALog.getInstance().d(TAG, "<cloudRecordGetResourceId> resourceId=" + recordResourceId
                            + ", recordChannel=" + recordChannel
                            + ", recordUid=" + recordUid);
                return recordResourceId;

            } else {
                ALog.getInstance().e(TAG, "<cloudRecordGetResourceId> no response return");
                return null;
            }

        } catch (JSONException e) {
            e.printStackTrace();
            ALog.getInstance().e(TAG, "<cloudRecordGetResourceId> [JSONException], error=" + e);
            mErrCode = RESULT_NORESPONSE;
            return null;
        }
    }

    /*
     * @brief 云录启动参数
     */
    public static class CloudRecordParam {
        public String  mRecordChannel;      ///< 云录时的频道
        public long mRecordUid;             ///< 云录时的Uid
        public String mRecordToken;         ///< 云录的token
        public String mResourceId;          ///< 云录的资源Id

        public int mVideoWidth;             ///< 视频宽度，默认640
        public int mVideoHeight;            ///< 视频高度，默认480
        public int mFrameRate;              ///< 帧率，默认15
        public int mBitrate;                ///< 视频码率，对应宽高默认1000

        public int mVendorId;               ///< 第三方云服务器标识, 2: 表示阿里云
        public int mRegion;
        public String mBucket;
        public String mAccessKey;
        public String mSecretKey;
    }


    /*
     * @brief 启动云录制操作
     * @param recordParam : 云录的参数
     * @return 返回当前云录的sid
     */
    public String cloudRecordStart(CloudRecordParam recordParam) {
        ArrayList<String> paramList = new ArrayList<>();
        JSONObject body = new JSONObject();
        int i;

        // 请求示例：
        //  https://api.agora.io/v1/apps/<yourappid>/cloud_recording/resourceid/<resourceid>/mode/<mode>/start


        // 根据设定URL和参数
        String realUrl = mCloudRecordReqUrl;
        paramList.add("apps");
        paramList.add(mAgoraAppid);
        paramList.add("cloud_recording");
        paramList.add("resourceid");
        paramList.add(recordParam.mResourceId);
        paramList.add("mode");
        paramList.add("mix");    // 混流录制
        paramList.add("start");

        try {
            body.put("cname", recordParam.mRecordChannel);
            body.put("uid", String.valueOf(recordParam.mRecordUid));

            //
            // Token 字段
            //
            JSONObject clientReqBody = new JSONObject();
            if ((recordParam.mRecordToken != null) && (recordParam.mRecordToken.length() > 0)) {
                clientReqBody.put("token", recordParam.mRecordToken);
            }

            //
            //  recordingConfig 结构
            //
            JSONObject rcdCfgBody = new JSONObject();
            rcdCfgBody.put("maxIdleTime", 120);   // 最长空闲2分钟
            rcdCfgBody.put("streamTypes", 2);   // 默认，订阅音视频
            rcdCfgBody.put("channelType", 1);   // 直播场景
            rcdCfgBody.put("videoStreamType", 0);   // 默认，视频流为大流

            JSONObject transcodingCfg = new JSONObject();
            transcodingCfg.put("width", recordParam.mVideoWidth);
            transcodingCfg.put("height", recordParam.mVideoHeight);
            transcodingCfg.put("fps", recordParam.mFrameRate);
            transcodingCfg.put("bitrate", recordParam.mBitrate);
            transcodingCfg.put("mixedVideoLayout", 1);
            rcdCfgBody.put("transcodingConfig", transcodingCfg);

            clientReqBody.put("recordingConfig", rcdCfgBody);

//            //
//            // recordingFileConfig 结构
//            //
//            JSONObject fileCfgBody = new JSONObject();
//            fileCfgBody.put("avFileType", "hls");   // 默认，录制生成 M3U8 和 TS 文件
//            clientReqBody.put("recordingFileConfig", fileCfgBody);

            //
            //  storageConfig 结构
            //
            JSONObject storageCfgBody = new JSONObject();
            storageCfgBody.put("vendor", 2);    // 第三方存储使用阿里云
            storageCfgBody.put("region", 1);
            storageCfgBody.put("bucket", recordParam.mBucket);
            storageCfgBody.put("accessKey", recordParam.mAccessKey);
            storageCfgBody.put("secretKey", recordParam.mSecretKey);
//            JSONArray prefix = new JSONArray();
//            prefix.put("alarm");
//            storageCfgBody.put("fileNamePrefix", prefix);
            clientReqBody.put("storageConfig", storageCfgBody);

            //
            //  clientRequest 结构
            //
            body.put("clientRequest", clientReqBody);

        } catch (Exception exp) {
            exp.printStackTrace();
            ALog.getInstance().e(TAG, "<cloudRecordStart> [EXCEPTION] exp=" + exp);
            return null;
        }

        // 向云录服务器发起请求
        try {
            JSONObject response = sendCloudRecordingRequest(
                    realUrl, "POST", paramList, body);
            if (response != null) {
                String respResId = response.getString("resourceId");
                if (respResId.compareToIgnoreCase(recordParam.mResourceId) != 0) {
                    ALog.getInstance().e(TAG, "<cloudRecordStart> [ERROR] reourceId not match"
                            + ", reqResId=" + recordParam.mResourceId
                            + ", respResId=" + respResId);
                    return null;
                }

                String respSid = response.getString("sid");
                ALog.getInstance().d(TAG, "<cloudRecordStart> respSid=" + respSid);
                return respSid;

            } else {
                ALog.getInstance().e(TAG, "<cloudRecordStart> no response return");
                return null;
            }

        } catch (JSONException e) {
            ALog.getInstance().e(TAG, "<cloudRecordStart> [JSONException], error=" + e);
            e.printStackTrace();
            mErrCode = RESULT_NORESPONSE;
            return null;
        }
    }

    /*
     * @brief 停止云录制操作
     * @param recordChannel : 云录频道
     * @param recordUid ：云录时的uid
     * @return 返回录制的文件列表
     */
    public CloudRecordResult cloudRecordStop(String recordChannel, long recordUid,
                                             String resourceId, String recordSid, boolean asyncStop) {
        ArrayList<String> paramList = new ArrayList<>();
        JSONObject body = new JSONObject();
        int i;

        // 请求示例：
        // https://api.agora.io/v1/apps/<yourappid>/cloud_recording/resourceid/<resourceid>/sid/<sid>/mode/individual/stop


        // 根据设定URL和参数
        String realUrl = mCloudRecordReqUrl;
        paramList.add("apps");
        paramList.add(mAgoraAppid);
        paramList.add("cloud_recording");
        paramList.add("resourceid");
        paramList.add(resourceId);
        paramList.add("sid");
        paramList.add(recordSid);
        paramList.add("mode");
        paramList.add("mix");    // 混流录制
        paramList.add("stop");


        try {
            body.put("cname", recordChannel);
            body.put("uid", String.valueOf(recordUid));

            // clientRequest结构
            JSONObject clientReqBody = new JSONObject();
            //clientReqBody.put("async_stop", asyncStop); // 是否异步停止
            body.put("clientRequest", clientReqBody);

        } catch (Exception exp) {
            exp.printStackTrace();
            ALog.getInstance().e(TAG, "<cloudRecordStop> [EXCEPTION] exp=" + exp);
            return null;
        }

        // 向云录服务器发起请求
        try {
            JSONObject response = sendCloudRecordingRequest(
                    realUrl, "POST", paramList, body);
            if (response != null) {
                String respResId = response.getString("resourceId");
                if (respResId.compareToIgnoreCase(resourceId) != 0) {
                    ALog.getInstance().e(TAG, "<cloudRecordStop> [ERROR] resourceId not match"
                            + ", reqResId=" + resourceId
                            + ", respResId=" + respResId);
                    return null;
                }

                String respSid = response.getString("sid");
                if (respSid.compareToIgnoreCase(recordSid) != 0) {
                    ALog.getInstance().e(TAG, "<cloudRecordStop> [ERROR] sid not match"
                            + ", reqSid=" + recordSid
                            + ", respResId=" + respResId);
                    return null;
                }

                JSONObject serverRespObj = response.getJSONObject("serverResponse");
                String fileListMode = serverRespObj.getString("fileListMode");
                CloudRecordResult recordResult = new CloudRecordResult();

                if (fileListMode.compareToIgnoreCase("string") == 0) {
                    CloudRecordResult.RecordFileInfo recordInfo = new CloudRecordResult.RecordFileInfo();
                    recordResult.mUploadingStatus = serverRespObj.getString("uploadingStatus");

                    recordInfo.mFilePath = serverRespObj.getString("fileList");
                    recordResult.mFileList.add(recordInfo);

                    ALog.getInstance().d(TAG, "<cloudRecordStop> filePath=" + recordInfo.mFilePath
                            + ", uploadingStatus=" + recordResult.mUploadingStatus);

                } else {
                    JSONArray fileListArray = serverRespObj.getJSONArray("fileList");
                    recordResult.mUploadingStatus = serverRespObj.getString("uploadingStatus");
                    int fileCount = fileListArray.length();
                    for (i = 0; i < fileCount; i++) {
                        JSONObject fileObj = fileListArray.getJSONObject(i);
                        CloudRecordResult.RecordFileInfo recordInfo = new CloudRecordResult.RecordFileInfo();
                        recordInfo.mFilePath = fileObj.getString("fileName");
                        recordInfo.mTrackType = fileObj.getString("trackType");
                        recordInfo.mUid = Long.valueOf(fileObj.getString("uid"));
                        recordInfo.mMixedAllUser = fileObj.getBoolean("mixedAllUser");
                        recordInfo.mIsPlayable = fileObj.getBoolean("isPlayable");
                        recordInfo.mSliceStartTime = fileObj.getLong("sliceStartTime");
                        recordResult.mFileList.add(recordInfo);
                    }

                    ALog.getInstance().d(TAG, "<cloudRecordStop> rcdFileCnt=" + recordResult.mFileList.size()
                                + ", uploadingStatus=" + recordResult.mUploadingStatus);
                }
                return recordResult;

            } else {
                ALog.getInstance().e(TAG, "<cloudRecordStop> no response return");
                return null;
            }

        } catch (JSONException e) {
            ALog.getInstance().e(TAG, "<cloudRecordStop> [JSONException], error=" + e);
            e.printStackTrace();
            mErrCode = RESULT_NORESPONSE;
            return null;
        }
    }

    /*
     * @brief 发送云录制请求包
     */
    private synchronized JSONObject sendCloudRecordingRequest(
            String baseUrl,
            String method,
            List<String> paramList,
            JSONObject body) {
        mErrCode = RESULT_OK;
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            mErrCode = RESULT_INVALID_URL;
            ALog.getInstance().e(TAG, "Invalid url: " + baseUrl);
            return null;
        }

        //拼接URL和请求参数生成最终URL
        String realURL = baseUrl;
        for (String paramSeg : paramList) {
            realURL = realURL + "/" + paramSeg;
        }

        //支持json格式消息体
        String realBody = String.valueOf(body);

        HttpURLConnection connection = null;
        BufferedReader reader = null;
        StringBuilder response = new StringBuilder();

        ALog.getInstance().d(TAG, "<sendCloudRecordingRequest>"
                + "\n    realUrl=" + realURL
                + "\n    realBody=" + realBody);

        try {
            java.net.URL url = new URL(realURL);
            connection = (HttpURLConnection) url.openConnection();
            //设置认证
            connection.setRequestProperty("Authorization", "Basic " + getCloudRecordAuthorization());
            // 不能使用缓存
            connection.setUseCaches(false);

            switch (method) {
                case "GET":
                    connection.setRequestMethod("GET");
                    break;
                case "POST":
                    connection.setRequestMethod("POST");
                    connection.setRequestProperty("Content-Type", "application/json;charset=utf-8");
                    //connection.setRequestProperty("Content-Type", "application/json");
                    connection.setDoOutput(true);
                    connection.setDoInput(true);
                    DataOutputStream os = new DataOutputStream(connection.getOutputStream()); // 包含connect了
                    os.writeBytes(realBody);
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
                    mErrCode = RESULT_INVALID_URL;
                    return null;
            }
            connection.setReadTimeout(8000);
            connection.setConnectTimeout(8000);
            int respCode = connection.getResponseCode();
            if(respCode == HttpURLConnection.HTTP_OK) {
                InputStream inputStream = connection.getInputStream();
                reader = new BufferedReader(new InputStreamReader(inputStream));
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                return parseCloudRecordingResponse(response.toString());
            } else {
                mErrCode = RESULT_DISCONNECT;
                ALog.getInstance().e(TAG, "Response code: " + connection.getResponseCode());
                ALog.getInstance().e(TAG, "Error message: " + connection.getResponseMessage());
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            mErrCode = RESULT_DISCONNECT;
            return null;
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
     * @brief 解析云录制响应包
     */
    private synchronized JSONObject parseCloudRecordingResponse(final String response){
        JSONObject jsonObj = null;
        try {
            jsonObj = new JSONObject(response);
            ALog.getInstance().i(TAG, "<parseCloudRecordingResponse> jsonObj=" + jsonObj.toString());
            return jsonObj;

        } catch (JSONException e) {
            e.printStackTrace();
            ALog.getInstance().e(TAG, "<parseCloudRecordingResponse> [JSONExpt] response=" + response);
            mErrCode = RESULT_NORESPONSE;
            return null;
        }
    }

    private String getCloudRecordAuthorization() {
        return "ODYyMGZkNDc5MTQwNDU1Mzg4Zjk5NDIwZmQzMDczNjM6NDkyYzE4ZGNkYjBhNDNjNWJiMTBjYzFjZDIxN2U4MDI=";
    }


    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////// 告警信息上报和查询等处理 ///////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    /*
     * @brief 查询告警信息
     * @param deviceIdList : 要查询的设备Id列表
     * @param year-month-day : 查询的告警信息日期
     * @param pageIndex ：页面Index，分页查询时从 1, 2, 3.....
     * @param pageSize : 每个页面显示记录数
     * @param type : 告警类型；1：有人通过 2：移动侦测 3：语音告警
     * @return 获取云录的ResourceId
     */
    public ArrayList<IotAlarm> alarmQuery(ArrayList<String> deviceIdList, int year, int month, int day,
                                          int pageIndex, int pageSize, int type) {
        Map<String, String> params = new HashMap();
        JSONObject body = new JSONObject();
        ArrayList<IotAlarm> alarmList = new ArrayList<>();


        if (deviceIdList == null || deviceIdList.size() <= 0) { // 没有绑定的设备
            return alarmList;
        }

        // 请求示例：
        //  https://iot.sh2.agoralab.co/super-app/v1/alarm-info
        //

        // 根据设定URL和参数
        String realUrl = mAlarmServerBaseUrl + "/super-app/v1/alarm-info";


        // 设置Body
        try {
            String dataString = String.format(Locale.getDefault(), "%04d-%02d-%02d",
                    year, month, day);
            body.put("appId", mAgoraAppid);

            //body.put("customerAccountId", userAccount);
            JSONArray uidArray = new JSONArray();
            for (int i = 0; i < deviceIdList.size(); i++) {
                uidArray.put(deviceIdList.get(i));
            }
            body.put("deviceIdList", uidArray);

            body.put("date", dataString);
            body.put("page", pageIndex);
            body.put("size", pageSize);
            body.put("type", type);

        } catch (Exception exp) {
            exp.printStackTrace();
            ALog.getInstance().e(TAG, "<alarmQuery> [EXCEPTION] exp=" + exp);
            return alarmList;
        }

        // 向服务器发起请求
        try {
            AlarmReqResult result = sendAlarmRequest( realUrl, "POST", params, null, body);
            if (result == null) {
                return alarmList;
            }
            JSONArray alarmObjArray = result.mResponse;
            if (alarmObjArray != null) {
                for (int i = 0; i < alarmObjArray.length(); i++) {
                    JSONObject alarmObj = alarmObjArray.getJSONObject(i);
                    IotAlarm iotAlarm = new IotAlarm();
                    iotAlarm.mAlarmId = alarmObj.getLong("alarmId");
                    iotAlarm.mType = alarmObj.getInt("alarmType");
                    iotAlarm.mDescription = alarmObj.getString("alarmDescription");
                    iotAlarm.mAttachMsg = alarmObj.getString("attachMsg");
                    iotAlarm.mOccurDate = alarmObj.getString("date");
                    iotAlarm.mTimestamp = alarmObj.getLong("timestamp");
                    iotAlarm.mReaded = alarmObj.getBoolean("read");

                    JSONObject recordInfoObj = alarmObj.getJSONObject("recordInfo");
                    if (recordInfoObj != null) {
                        iotAlarm.mDeviceId = recordInfoObj.getString("deviceId");
                        iotAlarm.mDeviceUid = recordInfoObj.getLong("deviceUid");
                        iotAlarm.mRecordChannel = recordInfoObj.getString("recordChannel");
                        iotAlarm.mRecordSid = recordInfoObj.getString("recordSid");
                    }

                    alarmList.add(iotAlarm);
                }

                return alarmList;

            } else {
                ALog.getInstance().e(TAG, "<alarmQuery> no response return");
                return alarmList;
            }

        } catch (JSONException e) {
            e.printStackTrace();
            ALog.getInstance().e(TAG, "<alarmQuery> [JSONException], error=" + e);
            mErrCode = RESULT_NORESPONSE;
            return alarmList;
        }
    }

    /*
     * @brief 告警信息上报
     * @param userAccount : 用户账号Id
     * @param date : 查询的告警信息日期
     * @param pageIndex ：页面Index，分页查询时从 1, 2, 3.....
     * @param pageSize : 每个页面显示记录数
     * @param type : 告警类型；1：有人通过 2：移动侦测 3：语音告警
     * @param userUidList : 需要通知的用户Uid列表
     * @return 获取云录的ResourceId
     */
    public int alarmReport(long deviceUid, int type, String attachMsg,
                           String recordChannel, String recordSid,
                           ArrayList<Long> userUidList) {
        Map<String, String> params = new HashMap();
        JSONObject body = new JSONObject();

        // 请求示例：
        //  https://iot.sh2.agoralab.co/super-app/v1/alarm-info
        //

        // 根据设定URL和参数
        String realUrl = mAlarmServerBaseUrl + "/super-app/v1/alarm-report";

        params.put("appId", mAgoraAppid);
        params.put("type", Integer.toString(type));
        params.put("attachMsg", attachMsg);
        params.put("deviceUid", Long.toString(deviceUid));
        params.put("recordChannel", recordChannel);
        params.put("recordSid", recordSid);

        String paramExtend = "";
        for (int i = 0; i < userUidList.size(); i++) {
            String segment = "&notifyAccountList=" + String.valueOf(userUidList.get(i));
            paramExtend = paramExtend + segment;
        }


        // 设置Body

        // 向服务器发起请求
        try {
            AlarmReqResult result = sendAlarmRequest(realUrl, "POST", params, paramExtend, body);
            if (result.mErrCode == RESULT_OK) {
                ALog.getInstance().d(TAG, "<alarmReport> successful");
               return RESULT_OK;

            } else {
                ALog.getInstance().e(TAG, "<alarmReport> no response return");
                return RESULT_NORESPONSE;
            }

        } catch (Exception e) {
            e.printStackTrace();
            ALog.getInstance().e(TAG, "<alarmReport> [JSONException], error=" + e);
            mErrCode = RESULT_NORESPONSE;
            return mErrCode;
        }

    }

}
