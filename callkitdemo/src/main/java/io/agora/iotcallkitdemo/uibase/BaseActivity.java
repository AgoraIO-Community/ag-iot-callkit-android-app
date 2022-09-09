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
package io.agora.iotcallkitdemo.uibase;


import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Window;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import io.agora.iotcallkitdemo.uiaccount.EntryActivity;


public abstract class BaseActivity extends AppCompatActivity  {

    ////////////////////////////////////////////////////////////////////
    //////////////////////// Constant Definition ///////////////////////
    ////////////////////////////////////////////////////////////////////
    private static final String TAG = "IOTAPP20/BaseActivity";

    public static final int PERM_REQID_RECORD_AUDIO = 0x1001;
    //public static final int PERM_REQID_CAMERA = 0x1002;


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
    private ProgressDialog mProgressDlg;

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////// Override Activity Methods ////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);

        mActivity = this;

        mProgressDlg = new ProgressDialog(this);

    }


    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mProgressDlg != null) {
            mProgressDlg.dismiss();
        }

        initializePermList();
    }


    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
    }


    protected void progressShow(String msg) {
        if (msg == "") {
            mProgressDlg.show();
            return;
        }
        mProgressDlg.setMessage(msg);
        mProgressDlg.show();
    }

    protected void progressHide() {
        mProgressDlg.hide();
    }


    protected void popupMessage(String message)
    {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    protected void popupMessageLongTime(String message)
    {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    ///////////////////////////////////////////////////////////////////////////
    //////////////////////// Methods of Permission ////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    protected void initializePermList() {

        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Log.d(TAG, "<onCreate> have permission in low Buil.version");
            mPermissionArray = new PermissionItem[2];
            for (PermissionItem item : mPermissionArray) {
                item.granted = true;
            }

        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            mPermissionArray = new PermissionItem[1];
            mPermissionArray[0] = new PermissionItem(Manifest.permission.RECORD_AUDIO, PERM_REQID_RECORD_AUDIO);
            //mPermissionArray[1] = new PermissionItem(Manifest.permission.CAMERA, PERM_REQID_CAMERA);
            for (PermissionItem item : mPermissionArray) {
                item.granted = (ContextCompat.checkSelfPermission(this, item.permissionName) == PackageManager.PERMISSION_GRANTED);
            }

        } else {
            mPermissionArray = new PermissionItem[1];
            mPermissionArray[0] = new PermissionItem(Manifest.permission.RECORD_AUDIO, PERM_REQID_RECORD_AUDIO);
            //mPermissionArray[1] = new PermissionItem(Manifest.permission.CAMERA, PERM_REQID_CAMERA);
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

    /*
     * @brief save bitmap to local file
     */
    protected boolean saveBmpToFile(Bitmap bmp, String fileName)
    {
        File f = new File(fileName);
        if (f.exists()) {
            f.delete();
        }
        try {
            FileOutputStream out = new FileOutputStream(f);
            bmp.compress(Bitmap.CompressFormat.JPEG, 100, out);
            out.flush();
            out.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Log.e(TAG, "<saveBmpToFile> file not found: " + fileName);
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "<saveBmpToFile> IO exception");
            return false;
        }

        return true;
    }

    /*
     * @brief 判断本应用是否已经位于最前端：已经位于最前端时，返回 true；否则返回 false
     */
    public boolean isRunningForeground(Context context) {
        ActivityManager activityManager = (ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> appProcessInfoList = activityManager.getRunningAppProcesses();
        for (ActivityManager.RunningAppProcessInfo appProcessInfo : appProcessInfoList) {
            if (appProcessInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                    && appProcessInfo.processName == context.getApplicationInfo().processName) {
                return true;
            }
        }
        return false;
    }

    /*
     * @brief 当本应用位于后台时，则将它切换到最前端
     */
    public void setTopApp(Context context) {
        if (isRunningForeground(context)) {
            return;
        }
        //获取ActivityManager
        ActivityManager activityManager = (ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);

        //获得当前运行的task(任务)
        List<ActivityManager.RunningTaskInfo> taskInfoList = activityManager.getRunningTasks(100);
        for (ActivityManager.RunningTaskInfo taskInfo : taskInfoList) {
            //找到本应用的 task，并将它切换到前台
            if (taskInfo.topActivity.getPackageName() == context.getPackageName()) {
                activityManager.moveTaskToFront(taskInfo.id, 0);
                Log.i(TAG, "<setTopApp> set to front, id=" + taskInfo.id);
                break;
            }
        }
    }

    protected void GotoEntryActivity(Context ctx) {
        new android.os.Handler(Looper.getMainLooper()).postDelayed(
                new Runnable() {
                    public void run() {
                        Intent intent = new Intent(ctx, EntryActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    }
                },
                3000);
    }



}
