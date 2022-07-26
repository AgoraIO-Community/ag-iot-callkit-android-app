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
package io.agora.iotcallkitdemo.uicallkit;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;

import androidx.annotation.NonNull;

import io.agora.iotcallkitdemo.databinding.ActivityCallIncomingBinding;
import io.agora.iotcallkitdemo.uibase.BaseActivity;
import io.agora.iotcallkitdemo.uibase.PermissionHandler;
import io.agora.iotcallkitdemo.uibase.PermissionItem;
import io.agora.iotcallkit.ACallkitSdkFactory;
import io.agora.iotcallkit.IAccountMgr;
import io.agora.iotcallkit.ICallkitMgr;



public class CallIncomingActivity extends BaseActivity implements
        IAccountMgr.ICallback, ICallkitMgr.ICallback, PermissionHandler.ICallback   {
    private final String TAG = "IOTAPP20/CallIncomeFrag";

    private ActivityCallIncomingBinding mBinding;    ///< 自动生成的view绑定类
    private PermissionHandler mPermHandler;             ///< 权限申请处理

    ////////////////////////////////////////////////////////////////////////////
    ///////////////////////// Override Activity Methods ////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //创建view绑定类的实例，使其成为屏幕上的活动视图
        mBinding = ActivityCallIncomingBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());


        mBinding.nvTitleBar.ivBack.setVisibility(View.INVISIBLE);
        mBinding.nvTitleBar.tvTitle.setText("有人按门铃");
        mBinding.nvTitleBar.tvOption.setVisibility(View.INVISIBLE);

        // 接听按钮
        mBinding.ivAccept.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //选择接听
                Log.d(TAG, "Answer the call");
                onBtnAnswer();

            }
        });

        // 拒绝按钮
        mBinding.ivHangup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("activity", "Refuse the call");
                ACallkitSdkFactory.getInstance().getCallkitMgr().callHangup();
                finish();
            }
        });

        // 设置视频显示View控件
        ACallkitSdkFactory.getInstance().getCallkitMgr().setPeerVideoView(mBinding.svDeivceView);
    }

    @Override
    protected void onStart() {
        super.onStart();

        // 注册 账号、呼叫、设备管理、告警、通知 回调监听
        ACallkitSdkFactory.getInstance().getAccountMgr().registerListener(this);
        ACallkitSdkFactory.getInstance().getCallkitMgr().registerListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

//        //页面恢复时候检查是否在呼叫状态
//        ICallkitMgr.CallState state = AIotAppSdkFactory.getInstance().getCallkitMgr().getCallState();
//
//        if ((ICallkitMgr.CallState.TALKING != state) &&
//                (ICallkitMgr.CallState.DIALING != state) &&
//                (ICallkitMgr.CallState.INCOMING != state)) {
//            popupMessage("呼叫已经失效!");
//            finish();
//        }
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "<onStop>");
        super.onStop();

        // 注销 账号、呼叫、设备管理、告警、通知 回调监听
        ACallkitSdkFactory.getInstance().getAccountMgr().unregisterListener(this);
        ACallkitSdkFactory.getInstance().getCallkitMgr().unregisterListener(this);
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "<onDestory>");
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }


    void onBtnAnswer() {
        //
        // RECORD_AUDIO 权限判断处理
        //
        int[] permIdArray = new int[1];
        permIdArray[0] = PermissionHandler.PERM_ID_RECORD_AUDIO;
        mPermHandler = new PermissionHandler(this, this, permIdArray);
        if (!mPermHandler.isAllPermissionGranted()) {
            Log.d(TAG, "<onBtnAnswer> requesting permission...");
            mPermHandler.requestNextPermission();

        } else {
            Log.d(TAG, "<onBtnAnswer> permission granted, answer incoming call");
            doAnswerCall();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        Log.d(TAG, "<onRequestPermissionsResult> requestCode=" + requestCode);
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (mPermHandler != null) {
            mPermHandler.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    public void onAllPermisonReqDone(boolean allGranted, final PermissionItem[] permItems) {
        Log.d(TAG, "<onAllPermisonReqDone> allGranted = " + allGranted);

        if (allGranted) {
            doAnswerCall();

        } else {
            popupMessage("没有相应的操作权限");
        }
    }

    void doAnswerCall() {
        ACallkitSdkFactory.getInstance().getCallkitMgr().callAnswer();

        // 跳转到 设备通话页面
        Intent activityIntent = new Intent(CallIncomingActivity.this,
                CallLivingActivity.class);
        activityIntent.putExtra("answer", true);
        startActivity(activityIntent);
        finish();
    }

    //////////////////////////////////////////////////////////////////////////////////
    ////// Override ICallback Methods of IAccountMgr & ICallkitMgr.ICallback  ////////
    /////////////////////////////////////////////////////////////////////////////////
    @Override
    public void onLoginOtherDevice(final String account) {
        Log.d(TAG, "<onLoginOtherDevice> account=" + account);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                progressHide();
                popupMessage("账号在其他设备登录,本地立即退出!");
                ACallkitSdkFactory.getInstance().getCallkitMgr().callHangup();  // 本地挂断处理

                // 返回到登录界面，清空Activity堆栈
                GotoEntryActivity(mActivity);
            }
        });
    }


    @Override
    public void onPeerHangup(final String peerAccountId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                popupMessage("对端已经挂断！");
                finish();
            }
        });
    }


    @Override
    public void onPeerTimeout(final String peerAccountId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                popupMessage("超时未接听挂断！");
                finish();
            }
        });
    }

    @Override
    public void onPeerFirstVideo(final String peerAccountId, int videoWidth, int videoHeight) {
        Log.d(TAG, "<onPeerFirstVideo> videoWidth=" + videoWidth
                + ", videoHeight=" + videoHeight);
    }

    @Override
    public void onCallkitError(int errCode) {
        Log.d(TAG, "<onCallkitError> errCode=" + errCode);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                popupMessage("通话出现异常，错误码: " + errCode);
                finish();
            }
        });
    }
}