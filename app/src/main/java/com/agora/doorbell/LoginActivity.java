package com.agora.doorbell;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.agora.agoracallkit.beans.UidInfoBean;
import com.agora.agoracallkit.callkit.AgoraCallKit;
import com.agora.agoracallkit.callkit.CallKitAccount;
import com.agora.agoracallkit.callkit.ICallKitCallback;
import com.agora.doorbell.base.BaseActivity;
import com.agora.doorbell.databinding.ActivityLoginBinding;


import java.util.List;

import pub.devrel.easypermissions.AppSettingsDialog;
import pub.devrel.easypermissions.EasyPermissions;

public class LoginActivity extends BaseActivity implements ICallKitCallback {
    private final String TAG = "DoorBell/LoginActivity";
    private ActivityLoginBinding mBinding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = ActivityLoginBinding.inflate(getLayoutInflater());
        View view = mBinding.getRoot();
        setContentView(view);

        mActionBar.setTitle("登陆");

        mBinding.btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mPgDigLogShow("正在登录中...");

                String accountName = mBinding.editText.getText().toString().trim();
                CallKitAccount logInAccount = new CallKitAccount(accountName, UidInfoBean.TYPE_MAP_USER);
                int ret = AgoraCallKit.getInstance().accountLogIn(logInAccount);
                if (ret != AgoraCallKit.ERR_NONE) {
                    mPgDigLogHide();
                    mPopupMessage("不能登录, 错误码: " + ret);
                }
            }
        });

        mBinding.btnRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mPgDigLogShow("正在注册中...");

                String accountName = mBinding.editText.getText().toString().trim();
                CallKitAccount logInAccount = new CallKitAccount(accountName, UidInfoBean.TYPE_MAP_USER);
                int ret = AgoraCallKit.getInstance().accountRegister(logInAccount);
                if (ret != AgoraCallKit.ERR_NONE) {
                    mPgDigLogHide();
                    mPopupMessage("不能注册, 错误码: " + ret);
                }

            }
        });
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "<onDestroy>");
        super.onDestroy();
    }

    @Override
    protected void onStart() {
        Log.d(TAG, "<onStart>");
        super.onStart();

        //注册呼叫监听
        AgoraCallKit.getInstance().registerListener(this);
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "<onStop>");
        super.onStop();
        //注销呼叫监听
        AgoraCallKit.getInstance().unregisterListener(this);
    }


    ////////////////////////////////////////////////////////////////////////////
    /////////////////// Override ICallKitCallback Methods //////////////////////
    ////////////////////////////////////////////////////////////////////////////
    @Override
    public void onRegisterDone(CallKitAccount account, int errCode) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mPgDigLogHide();

                if (errCode != AgoraCallKit.ERR_NONE) {
                    mPopupMessage("账号注册失败！");
                } else {
                    mPgDigLogShow("账号注册成功，正在登录中...");

                    String accountName = mBinding.editText.getText().toString().trim();
                    CallKitAccount logInAccount = new CallKitAccount(accountName, UidInfoBean.TYPE_MAP_USER);
                    int ret = AgoraCallKit.getInstance().accountLogIn(logInAccount);
                    if (ret != AgoraCallKit.ERR_NONE) {
                        mPgDigLogHide();
                        mPopupMessage("不能登录, 错误码: " + ret);
                    }
                }
            }
        });
    }


    @Override
    public void onLogInDone(CallKitAccount account, int errCode) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mPgDigLogHide();

                if (account == null) {
                    mPopupMessage("登录失败，没有有效的账号");
                    return;
                }
                if (errCode != AgoraCallKit.ERR_NONE) {
                    mPopupMessage("账号: " + account.getName() + " 登录失败");
                } else {
                    // 切换到登录成功界面
                    Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                    startActivity(intent);
                    finish();
                }
            }
        });
    }



}
