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
package io.agora.iotcallkitdemo.uiaccount;


import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import android.view.View;

import io.agora.iotcallkitdemo.AppStorageUtil;
import io.agora.iotcallkitdemo.PushApplication;
import io.agora.iotcallkitdemo.databinding.ActivityLoginBinding;
import io.agora.iotcallkitdemo.uibase.BaseActivity;
import io.agora.iotcallkitdemo.HomePageActivity;
import io.agora.iotcallkit.ACallkitSdkFactory;
import io.agora.iotcallkit.ErrCode;
import io.agora.iotcallkit.IAccountMgr;
import io.agora.iotcallkit.IAgoraCallkitSdk;


public class LoginActivity extends BaseActivity implements IAccountMgr.ICallback {

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////// Constant Definition /////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    private final String TAG = "IOTAPP20/LoginActivity";



    ///////////////////////////////////////////////////////////////////////////
    ///////////////////// Variable Definition /////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    private ActivityLoginBinding mBinding;           ///< 自动生成的view绑定类

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////// Override Activity Methods ////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "<onCreate>");
        super.onCreate(savedInstanceState);
        mBinding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());


        String storedAccount = AppStorageUtil.queryValue(AppStorageUtil.KEY_ACCOUNT);
        if (!storedAccount.isEmpty()) {
            mBinding.etLoginAccount.setText(storedAccount);
        }

        mBinding.btnLoginDone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBtnLogin();
            }
        });

        // 在获得权限后才能初始化引擎，内部已经做了多次初始化处理
        PushApplication appInstance = (PushApplication) getApplication();
        appInstance.initializeEngine();

        //
        // 当前已经登录，直接跳转到主页界面
        //
        String accountId = ACallkitSdkFactory.getInstance().getAccountMgr().getAccountId();
        if ((accountId != null) && (!accountId.isEmpty())) {
            Log.d(TAG, "<onCreate> account already logined, goto HomePage activity");
            new android.os.Handler(Looper.getMainLooper()).postDelayed(
                    new Runnable() {
                        public void run() {
                            Intent intent = new Intent(LoginActivity.this, HomePageActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK); // 清除Activity堆栈
                            startActivity(intent);
                        }
                    },
                    200); // 延时显示当前界面
            return;
        }

    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "<onDestory>");
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "<onResume>");
        super.onResume();
    }


    @Override
    protected void onStart() {
        Log.d(TAG, "<onStart>");
        super.onStart();

        // 注册账号管理监听
        ACallkitSdkFactory.getInstance().getAccountMgr().registerListener(this);
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "<onStop>");
        super.onStop();

        // 注销账号管理监听
        IAccountMgr accountMgr = ACallkitSdkFactory.getInstance().getAccountMgr();
        if (accountMgr != null) {
            accountMgr.unregisterListener(this);
        }
    }

    void onBtnLogin()
    {
        String accountName = mBinding.etLoginAccount.getText().toString();
        if (accountName.isEmpty()) {
            popupMessage("请输入登录账号");
            return;
        }

        //显示进度对话框
        progressShow("登录中...");
        AppStorageUtil.keepShared(AppStorageUtil.KEY_ACCOUNT, accountName);
        int ret = ACallkitSdkFactory.getInstance().getAccountMgr().login(accountName);
        if (ret != ErrCode.XOK) {
            progressHide();
            popupMessage("不能登录, 错误码: " + ret);
            return;
        }
    }


    ////////////////////////////////////////////////////////////////////////////
    /////////////////// Override IAccountMgr.ICallback Methods /////////////////
    ////////////////////////////////////////////////////////////////////////////
    @Override
    public void onLoginDone(int errCode, String account) {
        Log.d(TAG, "<onLoginDone> errCode=" + errCode
                + ", account=" + account );

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                progressHide();

                if (account == null) {
                    popupMessage("登录失败，没有有效的账号");
                    return;
                }

                if (errCode != ErrCode.XOK)   {
                    popupMessage("账号: " + account + " 登录失败");

                } else  {
                    // 切换到主页界面
                    Intent intent = new Intent(LoginActivity.this, HomePageActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK); // 清除Activity堆栈
                    startActivity(intent);
                }
            }
        });
    }

    @Override
    public void onLogoutDone(int errCode, String account) {
        Log.d(TAG, "<onLogoutDone> errCode=" + errCode + ", account=" + account);
    }

    @Override
    public void onLoginOtherDevice(String account) {
        Log.d(TAG, "<onLoginOtherDevice> account=" + account);
    }


}