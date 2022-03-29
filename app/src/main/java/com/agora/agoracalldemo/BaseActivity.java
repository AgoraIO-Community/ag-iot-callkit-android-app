/**
 * @file BaseActivity.java
 * @brief This file implement the base permission request for activity
 * @author xiaohua.lu
 * @email luxiaohua@agora.io
 * @version 1.0.0.1
 * @date 2021-10-27
 * @license Copyright (C) 2021 AgoraIO Inc. All rights reserved.
 */
package com.agora.agoracalldemo;


import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Window;

import java.util.Arrays;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;


public abstract class BaseActivity extends AppCompatActivity  {

    ////////////////////////////////////////////////////////////////////
    //////////////////////// Constant Definition ///////////////////////
    ////////////////////////////////////////////////////////////////////
    private static final String TAG = "Demo/BaseActivity";

    public static final int PERM_REQID_RECORD_AUDIO = 0x1001;
    public static final int PERM_REQID_CAMERA = 0x1002;
    public static final int PERM_REQID_RDSTORAGE = 0x1003;
    public static final int PERM_REQID_WRSTORAGE = 0x1004;
    public static final int PERM_REQID_MGSTORAGE = 0x1005;


    class PermissionItem {
        public String permissionName;           ///< 权限名
        public boolean granted = false;         ///< 是否有权限
        public int requestId;                   ///< 请求Id

        PermissionItem(String name, int reqId) {
            permissionName = name;
            requestId = reqId;
        }
    };

    /////////////////////////////////////////////////////////////////////
    ///////////////////// Variable Definition ///////////////////////////
    /////////////////////////////////////////////////////////////////////
    protected PermissionItem[] mPermissionArray;
    protected Activity mActivity;


    ///////////////////////////////////////////////////////////////////////////
    ///////////////////// Override Activity Methods ////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);

        mActivity = this;
        initializePermList();
    }


    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isFinishing()) {
                    return;
                }

                // 检测是否要动态申请相应的权限
                int reqIndex = requestNextPermission();
                if (reqIndex < 0) {
                    onAllPermissionGranted();
                    return;
                }

            }
        }, 200);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,  @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.d(TAG, "<onRequestPermissionsResult>" + requestCode
                + ", permissions= " + Arrays.toString(permissions)
                + ", grantResults= " + Arrays.toString(grantResults));


        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            setPermGrantedByReqId(requestCode);

        } else { // 拒绝了该权限
            finish();
            return;
        }

        // 检测是否要动态申请相应的权限
        int reqIndex = requestNextPermission();
        if (reqIndex < 0) {
            onAllPermissionGranted();
            return;
        }
    }


    /*
     * @brief 所有权限都允许事件，子类实现该方法，处理获得所有权限后的处理流程
     */
    protected abstract void onAllPermissionGranted();


    ///////////////////////////////////////////////////////////////////////////
    //////////////////////// Methods of Permission ////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    protected void initializePermList() {

        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Log.d(TAG, "<onCreate> have permission in low Buil.version");
            mPermissionArray = new PermissionItem[5];
            for (PermissionItem item : mPermissionArray) {
                item.granted = true;
            }

        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            mPermissionArray = new PermissionItem[4];
            mPermissionArray[0] = new PermissionItem(Manifest.permission.RECORD_AUDIO, PERM_REQID_RECORD_AUDIO);
            mPermissionArray[1] = new PermissionItem(Manifest.permission.CAMERA, PERM_REQID_CAMERA);
            mPermissionArray[2] = new PermissionItem(Manifest.permission.READ_EXTERNAL_STORAGE, PERM_REQID_RDSTORAGE);
            mPermissionArray[3] = new PermissionItem(Manifest.permission.WRITE_EXTERNAL_STORAGE, PERM_REQID_WRSTORAGE);
            for (PermissionItem item : mPermissionArray) {
                item.granted = (ContextCompat.checkSelfPermission(this, item.permissionName) == PackageManager.PERMISSION_GRANTED);
            }

        } else {
            mPermissionArray = new PermissionItem[4];
            mPermissionArray[0] = new PermissionItem(Manifest.permission.RECORD_AUDIO, PERM_REQID_RECORD_AUDIO);
            mPermissionArray[1] = new PermissionItem(Manifest.permission.CAMERA, PERM_REQID_CAMERA);
            mPermissionArray[2] = new PermissionItem(Manifest.permission.READ_EXTERNAL_STORAGE, PERM_REQID_RDSTORAGE);
            mPermissionArray[3] = new PermissionItem(Manifest.permission.WRITE_EXTERNAL_STORAGE, PERM_REQID_WRSTORAGE);
            //mPermissionArray[4] = new PermissionItem(Manifest.permission.MANAGE_EXTERNAL_STORAGE, PERM_REQID_MGSTORAGE);
            for (PermissionItem item : mPermissionArray) {
                item.granted = (ContextCompat.checkSelfPermission(this, item.permissionName) == PackageManager.PERMISSION_GRANTED);
            }
        }
    }

    /*
     * @brief 进行下一个需要的权限申请
     * @param None
     * @return 申请权限的索引, -1表示所有权限都有了，不再需要申请
     */
    protected int requestNextPermission() {
        for (int i = 0; i < mPermissionArray.length; i++) {
            if (!mPermissionArray[i].granted) {
                // 请求相应的权限i
                String permission = mPermissionArray[i].permissionName;
                int requestCode = mPermissionArray[i].requestId;
                ActivityCompat.requestPermissions(mActivity, new String[]{permission}, requestCode);
                return i;
            }
        }

        return -1;
    }

    /*
     * @brief 根据requestId 标记相应的 PermissionItem 权限已经获得
     * @param reqId :  request Id
     * @return 相应的索引, -1表示没有找到 request Id 对应的项
     */
    protected int setPermGrantedByReqId(int reqId) {
        for (int i = 0; i < mPermissionArray.length; i++) {
            if (mPermissionArray[i].requestId == reqId) {
                mPermissionArray[i].granted = true;
                return i;
            }
        }

        Log.d(TAG, "<setPermGrantedByReqId> NOT found reqId=" + reqId);
        return -1;
    }



}
