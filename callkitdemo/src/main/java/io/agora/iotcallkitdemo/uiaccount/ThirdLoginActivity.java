
package io.agora.iotcallkitdemo.uiaccount;


import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import io.agora.iotcallkit.ACallkitSdkFactory;
import io.agora.iotcallkit.ErrCode;
import io.agora.iotcallkit.IAccountMgr;
import io.agora.iotcallkitdemo.AppStorageUtil;
import io.agora.iotcallkitdemo.HomePageActivity;
import io.agora.iotcallkitdemo.databinding.ActivityThirdpartyLoginBinding;
import io.agora.iotcallkitdemo.thirdpartyaccount.ThirdAccountMgr;
import io.agora.iotcallkitdemo.uibase.BaseActivity;


public class ThirdLoginActivity extends BaseActivity implements IAccountMgr.ICallback {

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////// Constant Definition /////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    private final String TAG = "IOTAPP20/ThirdLoginAct";



    ///////////////////////////////////////////////////////////////////////////
    ///////////////////// Variable Definition /////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    private ActivityThirdpartyLoginBinding mBinding;           ///< 自动生成的view绑定类


    ///////////////////////////////////////////////////////////////////////////
    ///////////////////// Override Activity Methods ////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "<onCreate>");
        super.onCreate(savedInstanceState);
        mBinding = ActivityThirdpartyLoginBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());



        String storedAccount = AppStorageUtil.queryValue(AppStorageUtil.KEY_ACCOUNT);
        String storedPassword = AppStorageUtil.queryValue(AppStorageUtil.KEY_PASSWORD);
        if (!storedAccount.isEmpty()) {
            mBinding.etThirdloginAccount.setText(storedAccount);
        }
        if (!storedPassword.isEmpty()) {
            mBinding.etThirdloginPassword.setText(storedPassword);
        }

        // 设置测试登录账号
        mBinding.btnThirdloginDone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBtnLogin();
            }
        });
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
        ACallkitSdkFactory.getInstance().getAccountMgr().unregisterListener(this);
    }

    void onBtnLogin()
    {
        String accountName = mBinding.etThirdloginAccount.getText().toString();
        String password = mBinding.etThirdloginPassword.getText().toString();

        if (accountName.isEmpty()) {
            popupMessage("请输入登录账号");
            return;
        }
        if (password.isEmpty()) {
            popupMessage("请输入登录密码");
            return;
        }

        //显示进度对话框
        progressShow("登录中...");
        AppStorageUtil.keepShared(AppStorageUtil.KEY_ACCOUNT, accountName);
        AppStorageUtil.keepShared(AppStorageUtil.KEY_PASSWORD, password);

        ThirdAccountMgr.getInstance().login(accountName, password, new ThirdAccountMgr.ILoginCallback() {
            @Override
            public void onThirdAccountLoginDone(int errCode, final String errMessage,
                                                final String account, final String password,
                                                final IAccountMgr.LoginParam loginParam) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (errCode != ErrCode.XOK) {
                            progressHide();
                            if (TextUtils.isEmpty(errMessage)) {
                                popupMessage("第三方账号登录失败, 错误码=" + errCode);
                            } else {
                                popupMessage("第三方账号登录失败, 错误信息=" + errMessage);
                            }
                            return;
                        }

                        int ret = ACallkitSdkFactory.getInstance().getAccountMgr().login(loginParam);
                        if (ret != ErrCode.XOK) {
                            progressHide();
                            popupMessage("SDK不能登录, 错误码=" + ret);
                            return;
                        }
                    }
                });
            }
        });

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

                if (errCode == ErrCode.XERR_ACCOUNT_NOT_EXIST) {
                    popupMessage("账号: " + account + " 不存在，登录失败");

                } else if (errCode == ErrCode.XERR_ACCOUNT_PASSWORD_ERR) {
                    popupMessage("账号: " + account + " 密码错误，登录失败");

                } else if (errCode != ErrCode.XOK)   {
                    popupMessage("账号: " + account + " 登录失败");

                } else  {
                    // 切换到主页界面
                    Intent intent = new Intent(ThirdLoginActivity.this, HomePageActivity.class);
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