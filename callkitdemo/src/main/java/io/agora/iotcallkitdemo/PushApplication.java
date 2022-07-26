/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2022 Agora Lab, Inc (http://www.agora.io/)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */
package io.agora.iotcallkitdemo;

import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Process;
import android.util.Log;

//import io.agora.iotcallkitdemo.huanxin.EmAgent;
import io.agora.iotcallkitdemo.uicallkit.CallIncomingActivity;
import io.agora.iotcallkit.ACallkitSdkFactory;
import io.agora.iotcallkit.IAgoraCallkitSdk;
import io.agora.iotcallkit.ICallkitMgr;
import io.agora.iotcallkit.utils.PreferenceManager;
import io.agora.iotcallkitdemo.uibase.ActivityLifecycleCallback;

import java.util.List;



public class PushApplication extends Application  implements ICallkitMgr.ICallback  {
    private static final String TAG = "IOTAPP20/PushApp";
    private static PushApplication instance = null;
    private static ActivityLifecycleCallback mLifeCycleCallbk = new ActivityLifecycleCallback();


    private Bundle mMetaData = null;
    private volatile boolean mIotAppSdkReady = false;       ///< SDK是否已经就绪

    private volatile int mAudCodecIndex = 12;   // 12 means opus, it's default value

    private String mLivingPeerAccountId;    ///< 当前正在通话的对端账号Id

    //////////////////////////////////////////////////////////////////
    ////////////////////// Public Methods ///////////////////////////
    //////////////////////////////////////////////////////////////////

    //获取APP单例对象
    public static PushApplication getInstance()   {
        return instance;
    }

    //获取活动页面生命期回调
    public static ActivityLifecycleCallback getLifecycleCallbacks() {
        return mLifeCycleCallbk;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        instance = this;

        AppStorageUtil.init(this);

        //偏好设置初始化
        PreferenceManager.init(this);

        //仅主进程运行一次
        if (isMainProcess(this)) {
            //获取applicationInfo标签内的数据
            try {
                PackageManager packageManager = this.getPackageManager();
                ApplicationInfo applicationInfo =
                        packageManager.getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
                mMetaData = applicationInfo.metaData;
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
                return;
            }
        }

        //注册Activity回调
        registerActivityLifecycleCallbacks(mLifeCycleCallbk);


//        //
//        // 初始化环信的离线推送
//        //
//        if (mMetaData != null)
//        {
//            EmAgent.EmPushParam  pushParam = new EmAgent.EmPushParam();
//            pushParam.mFcmSenderId = mMetaData.getString("com.fcm.push.senderid", "");
//            pushParam.mMiAppId = mMetaData.getString("com.mi.push.app_id", "");
//            pushParam.mMiAppKey = mMetaData.getString("com.mi.push.api_key", "");
//            pushParam.mMeizuAppId = mMetaData.getString("com.meizu.push.app_id", "");
//            pushParam.mMeizuAppKey =mMetaData.getString("com.meizu.push.api_key", "");
//            pushParam.mOppoAppKey = mMetaData.getString("com.oppo.push.api_key", "");
//            pushParam.mOppoAppSecret = mMetaData.getString("com.oppo.push.app_secret", "");;
//            pushParam.mVivoAppId = String.valueOf(mMetaData.getInt("com.vivo.push.app_id", 0));
//            pushParam.mVivoAppKey = mMetaData.getString("com.vivo.push.api_key", "");
//            pushParam.mHuaweiAppId = mMetaData.getString("com.huawei.hms.client.appid", "");
//            EmAgent.getInstance().initialize(this,  pushParam);
//        }
    }


    @Override
    public void onTerminate() {
        super.onTerminate();
    }

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(base);
        //android.support.multidex.MultiDex.install(this);
    }


    //判断是否在主进程
    private boolean isMainProcess(Context context) {
        int pid = Process.myPid();
        String pkgName = context.getApplicationInfo().packageName;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            String processName = Application.getProcessName();
            return (pkgName.compareToIgnoreCase(processName) == 0);

        } else {
            ActivityManager activityManager = (ActivityManager)context.getSystemService(ACTIVITY_SERVICE);
            List<ActivityManager.RunningAppProcessInfo> runningProcList = activityManager.getRunningAppProcesses();
            for (ActivityManager.RunningAppProcessInfo appProcess : runningProcList) {
                if (appProcess.pid == pid) {
                    return (pkgName.compareToIgnoreCase(appProcess.processName) == 0);
                }
            }
        }

        return false;
    }


    public void setAudioCodecIndex(int index)
    {
        mAudCodecIndex = index;
    }

    public int getAudioCodecIndex()
    {
        return mAudCodecIndex;
    }

     public void initializeEngine() {
        if (mIotAppSdkReady) {
            return;
        }


        //
        // 初始化IotAppSdk2.0 引擎
        //
        IAgoraCallkitSdk.InitParam initParam = new IAgoraCallkitSdk.InitParam();
        initParam.mContext = this;
        initParam.mRtcAppId = mMetaData.getString("AGORA_APPID", "");
        initParam.mProjectId = mMetaData.getString("PROJECT_ID", "");
        initParam.mMasterServerUrl = mMetaData.getString("MASTER_SERVER_URL", "");
        initParam.mSlaveServerUrl = mMetaData.getString("SALVE_SERVER_URL", "");
        //initParam.mPusherId = EmAgent.getInstance().getEid();
        initParam.mPublishVideo = false;
        initParam.mPublishAudio = true;
        initParam.mSubscribeAudio = true;
        initParam.mSubscribeVideo = true;

        //String storageRootPath = Environment.getExternalStorageDirectory().getAbsolutePath();
        //initParam.mLogFilePath = storageRootPath + "/callkit.log";

         int ret = ACallkitSdkFactory.getInstance().initialize(initParam);

         ACallkitSdkFactory.getInstance().getCallkitMgr().registerListener(this);
        mIotAppSdkReady = true;
    }

    public void setLivingPeerAccountId(String peerAccountId) {
        mLivingPeerAccountId = peerAccountId;
    }

    public String getLivingPeerAccountId() {
        return mLivingPeerAccountId;
    }

    @Override
    public void onPeerIncoming(String peerAccountId, String attachMsg) {
        if (!mLifeCycleCallbk.isOnForeground()) {        // 后台状态才通知
            Log.d(TAG, "<onPeerIncoming> peerAccountId=" + peerAccountId + " 正在呼叫，请点击接听....");

            // 设置当前通话的设备信息
            this.setLivingPeerAccountId(peerAccountId);

            // 跳转到来电呼叫界面，带上来电账号信息
            Intent activityIntent = new Intent(this, CallIncomingActivity.class);
            activityIntent.putExtra("attach_msg", attachMsg);
            //startActivity(activityIntent);

//            // 系统通知栏通知
//            EaseNotifier.getInstance().notify(activityIntent, "呼叫通知",
//                    peerAccountId + "正在呼叫，请点击接听....");
        }
    }

}