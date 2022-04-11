/**
 * @file PushApplication.java
 * @brief This file implement the application entry
 * @author xiaohua.lu
 * @email luxiaohua@agora.io
 * @version 1.0.0.1
 * @date 2021-10-13
 * @license Copyright (C) 2021 AgoraIO Inc. All rights reserved.
 */
package com.agora.agoracalldemo;

import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Process;
import android.util.Log;
import com.agora.agoracallkit.beans.UidInfoBean;
import com.agora.agoracallkit.callkit.AgoraCallKit;
import com.agora.agoracallkit.callkit.CallKitAccount;
import com.agora.agoracallkit.callkit.ICallKitCallback;
import com.agora.agoracallkit.utils.PreferenceManager;
import com.agora.agoracalldemo.ui.EaseNotifier;
import com.agora.agoracalldemo.ui.ActivityLifecycleCallback;

import java.util.List;



public class PushApplication extends Application implements ICallKitCallback {
    private static final String TAG = "DEMO/PushApplication";
    private static PushApplication instance = null;
    private static ActivityLifecycleCallback mLifeCycleCallbk = new ActivityLifecycleCallback();

    private int mRoleType = UidInfoBean.TYPE_MAP_USER;  ///< 应用运行时的角色,默认是移动应用
    private boolean mDblTalk = false;                   ///< 是否双讲操作
    private Bundle mMetaData = null;
    private volatile boolean mCallKitReady = false;

    private volatile int mAudCodecIndex = 12;   // 12 means opus, it's default value


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
        ActivityManager activityManager = (ActivityManager)context.getSystemService(ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> runningProcList = activityManager.getRunningAppProcesses();

        for (ActivityManager.RunningAppProcessInfo appProcess : runningProcList) {
            if (appProcess.pid == pid) {
                return (pkgName.compareToIgnoreCase(appProcess.processName) == 0);
            }
        }

        return false;
    }

    public void setRoleType(int role)
    {
        mRoleType = role;
    }

    public int getRoleType()
    {
        return mRoleType;
    }

    public void setDblTalk(boolean dblTalk)  {
        mDblTalk = dblTalk;
    }

    public boolean isDblTalk()  {
        return mDblTalk;
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
        if (mCallKitReady) {
            return;
        }

        //初始化呼叫功能
        AgoraCallKit.CallKitInitParam initParam = new AgoraCallKit.CallKitInitParam();
        initParam.mContext = this;
        initParam.mRtcAppId = mMetaData.getString("AGORA_APPID", "");
        initParam.mMetaData = mMetaData;
        //String storageRootPath = Environment.getExternalStorageDirectory().getAbsolutePath();
        //initParam.mLogFilePath = storageRootPath + "/callkit.log";

        initParam.mPublishVideo = false;
        initParam.mPublishAudio = false;
        initParam.mSubscribeAudio = false;
        initParam.mSubscribeVideo = true;
        AgoraCallKit.getInstance().initialize(initParam);

        //初始化离线/后台运行通知
        EaseNotifier.getInstance().init(this, mMetaData);

        //注册呼叫监听
        AgoraCallKit.getInstance().registerListener(this);

        mCallKitReady = true;
    }


    //////////////////////////////////////////////////////////////////////
    /////////////////// Override ICallKitCallback Methods ////////////////
    //////////////////////////////////////////////////////////////////////
    @Override
    public void onPeerIncoming(CallKitAccount account, CallKitAccount peer_account, String attachMsg) {
        //后台状态通知
        if (!mLifeCycleCallbk.isOnForeground()) {
            Log.d(TAG, "<onPeerIncoming> peer_account=" + peer_account.getName());

            Intent activityIntent = new Intent(this, CalledActivity.class);
            activityIntent.putExtra("caller_name", peer_account.getName());
            EaseNotifier.getInstance().notify(activityIntent,
                    "呼叫通知", peer_account.getName() + "正在呼叫，请点击接听....");
        }
    }

    @Override
    public void onPeerCustomizeMessage(CallKitAccount account, String peerMessage) {
        //后台状态通知
        if (!mLifeCycleCallbk.isOnForeground()) {

        }
    }

}