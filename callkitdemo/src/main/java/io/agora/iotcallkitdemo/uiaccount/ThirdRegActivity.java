
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
import io.agora.iotcallkitdemo.databinding.ActivityThirdpartyRegisterBinding;
import io.agora.iotcallkitdemo.thirdpartyaccount.ThirdAccountMgr;
import io.agora.iotcallkitdemo.uibase.BaseActivity;


public class ThirdRegActivity extends BaseActivity implements IAccountMgr.ICallback {

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////// Constant Definition /////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    private final String TAG = "IOTAPP20/ThirdRegAct";



    ///////////////////////////////////////////////////////////////////////////
    ///////////////////// Variable Definition /////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    private ActivityThirdpartyRegisterBinding mBinding;           ///< 自动生成的view绑定类


    ///////////////////////////////////////////////////////////////////////////
    ///////////////////// Override Activity Methods ////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "<onCreate>");
        super.onCreate(savedInstanceState);
        mBinding = ActivityThirdpartyRegisterBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());

        String storedAccount = AppStorageUtil.queryValue(AppStorageUtil.KEY_ACCOUNT);
        String storedPassword = AppStorageUtil.queryValue(AppStorageUtil.KEY_PASSWORD);
        if (!storedAccount.isEmpty()) {
            mBinding.etThirdregAccount.setText(storedAccount);
        }
        if (!storedPassword.isEmpty()) {
            mBinding.etThirdregPassword.setText(storedPassword);
        }

        mBinding.btnThirdregDone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBtnRegister();
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


    void onBtnRegister()
    {
        String accountName = mBinding.etThirdregAccount.getText().toString();
        String password = mBinding.etThirdregPassword.getText().toString();

        if (accountName.isEmpty()) {
            popupMessage("请输入注册账号");
            return;
        }
        if (password.isEmpty()) {
            popupMessage("请输入注册密码");
            return;
        }

        //显示进度对话框
        progressShow("注册中...");
        AppStorageUtil.keepShared(AppStorageUtil.KEY_ACCOUNT, accountName);
        AppStorageUtil.keepShared(AppStorageUtil.KEY_PASSWORD, password);

        ThirdAccountMgr.getInstance().register(accountName, password, new ThirdAccountMgr.IRegisterCallback() {
           @Override
           public void onThirdAccountRegisterDone(int errCode, final String errMessage,
                                                  final String account, final String password) {
               runOnUiThread(new Runnable() {
                   @Override
                   public void run() {
                       progressHide();
                       if (errCode != ErrCode.XOK) {
                           if (TextUtils.isEmpty(errMessage)) {
                               popupMessage("第三方账号注册失败, 错误码=" + errCode);
                           } else {
                               popupMessage("第三方账号注册失败, 错误信息=" + errMessage);
                           }

                       } else {
                           popupMessage("第三方账号注册成功!");
                           GotoEntryActivity(mActivity);
                       }

                   }
               });
           }
       });

    }



}