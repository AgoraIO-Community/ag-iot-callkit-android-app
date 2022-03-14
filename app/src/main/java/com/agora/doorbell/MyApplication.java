/**
 * @file MyApplication.java
 * @brief This file implement video list and video player
 *
 * @author xiaohua.lu
 * @email luxiaohua@agora.io
 * @version 1.0.0.1
 * @date 2021-11-17
 * @license Copyright (C) 2021 AgoraIO Inc. All rights reserved.
 */

package com.agora.doorbell;

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

import com.agora.agoracallkit.callkit.AgoraCallKit;
import com.agora.agoracallkit.callkit.CallKitAccount;
import com.agora.agoracallkit.callkit.ICallKitCallback;
import com.agora.agoracallkit.utils.PreferenceManager;

import java.util.List;

public class MyApplication extends Application implements ICallKitCallback {
    private final String TAG = "DoorBell/MyApplication";


    private Bundle mMetaData;
    private volatile boolean mCallKitReady = false;
    private static ActivityLifecycleCallback mLifeCycleCallbk = new ActivityLifecycleCallback();


    @Override
    public void onCreate() {
        super.onCreate();

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

//        注册Activity回调
        registerActivityLifecycleCallbacks(mLifeCycleCallbk);
    }

    private boolean isMainProcess(Context context) {
        int pid = Process.myPid();
        String pkgName = context.getApplicationInfo().packageName;
        ActivityManager activityManager = (ActivityManager) context.getSystemService(ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> runningProcList = activityManager.getRunningAppProcesses();

        for (ActivityManager.RunningAppProcessInfo appProcess : runningProcList) {
            if (appProcess.pid == pid) {
                return (pkgName.compareToIgnoreCase(appProcess.processName) == 0);
            }
        }

        return false;
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
//        String storageRootPath = Environment.getExternalStorageDirectory().getAbsolutePath();
//        initParam.mLogFilePath = storageRootPath + "/callkit.log";
        initParam.mPublishVideo = false;
        initParam.mPublishAudio = false;
        initParam.mSubscribeAudio = true;
        initParam.mSubscribeVideo = true;

        AgoraCallKit.getInstance().initialize(initParam);


        //初始化离线/后台运行通知
        EaseNotifier.getInstance().init(this, mMetaData);

        //注册呼叫监听
        AgoraCallKit.getInstance().registerListener(this);

        mCallKitReady = true;
    }

    public boolean isEngineReady() {
        return mCallKitReady;
    }


    @Override
    public void onPeerIncoming(CallKitAccount account, CallKitAccount peer_account, String attachMsg) {
        Log.d(TAG, "<onPeerIncoming> peer_account=" + peer_account.getName());

        //后台状态通知
        if (!mLifeCycleCallbk.isOnForeground()) {
            Log.d(TAG, "<onPeerIncoming> Launch to foreground");
            Intent launchIntent = new Intent(this, CalledActivity.class);
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            launchIntent.putExtra("caller_name", peer_account.getName());
            launchIntent.putExtra("attach_msg", attachMsg);
            startActivity(launchIntent);

            Intent activityIntent = new Intent(this, CalledActivity.class);
            activityIntent.putExtra("caller_name", peer_account.getName());
            activityIntent.putExtra("attach_msg", attachMsg);
            EaseNotifier.getInstance().notify(activityIntent,
                    "呼叫通知", peer_account.getName() + "正在呼叫，请点击接听....");
        }
    }
}
