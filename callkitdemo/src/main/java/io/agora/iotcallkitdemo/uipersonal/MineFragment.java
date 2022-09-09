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
package io.agora.iotcallkitdemo.uipersonal;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import io.agora.iotcallkitdemo.AppStorageUtil;
import io.agora.iotcallkitdemo.R;
import io.agora.iotcallkitdemo.databinding.FragmentMineBinding;
import io.agora.iotcallkitdemo.thirdpartyaccount.ThirdAccountMgr;
import io.agora.iotcallkitdemo.uibase.BaseFragment;
import io.agora.iotcallkit.ACallkitSdkFactory;
import io.agora.iotcallkit.ErrCode;
import io.agora.iotcallkit.IAccountMgr;


public class MineFragment  extends BaseFragment implements IAccountMgr.ICallback {
    private final String TAG = "IOTAPP20/MineFragment";
    private FragmentMineBinding mBinding;
    private boolean mUnregistering = false;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "<onCreateView>");
        mBinding = mBinding.inflate(inflater, container, false);
        View rootView = mBinding.getRoot();
        return rootView;
    }


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "<onViewCreated>");
        super.onViewCreated(view, savedInstanceState);

        mBinding.setting.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

            }
        });
        mBinding.setting.setVisibility(View.INVISIBLE);

        mBinding.btnLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onBtnLogout();
            }
        });

        mBinding.btnUnregister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onBtnUnregister();
            }
        });

        String accountName = ThirdAccountMgr.getInstance().getLoginAccountName();
        if (accountName != null) {
            mBinding.tvAccount.setText("当前账号: " + accountName);
        }

    }

    @Override
    public void onDestroyView() {
        Log.d(TAG, "<onDestroyView>");
        super.onDestroyView();
    }

    @Override
    public void onStart() {
        Log.d(TAG, "<onStart>");
        super.onStart();

        // 注册账号管理监听
        ACallkitSdkFactory.getInstance().getAccountMgr().registerListener(this);
    }

    @Override
    public void onStop() {
        Log.d(TAG, "<onStop>");
        super.onStop();

        // 注销账号管理监听
        ACallkitSdkFactory.getInstance().getAccountMgr().unregisterListener(this);
    }

    ////////////////////////////////////////////////////////////////////////////
    /////////////////// Message or Button Events Handler ///////////////////////
    ////////////////////////////////////////////////////////////////////////////

    void onBtnLogout() {
        // 显示进度条
        progressShow("账号登出中...");

        // 登出操作
        int ret = ACallkitSdkFactory.getInstance().getAccountMgr().logout();
        if (ret != ErrCode.XOK) {
            progressHide();
            popupMessage("不能登出, 错误码: " + ret);
            return;
        }
    }


    void onBtnUnregister() {
        // 显示进度条
        progressShow("第三方账号注销中...");

        // 先要进行SDK登出操作
        // 登出操作
        mUnregistering = true;
        int ret = ACallkitSdkFactory.getInstance().getAccountMgr().logout();


        // 注销操作
        String accountName = AppStorageUtil.queryValue(AppStorageUtil.KEY_ACCOUNT);
        String password = AppStorageUtil.queryValue(AppStorageUtil.KEY_PASSWORD);
        ThirdAccountMgr.getInstance().unregister(accountName, password, new ThirdAccountMgr.IUnregisterCallback() {
            @Override
            public void onThirdAccountUnregisterDone(int errCode, final String errMessage,
                                                     final String account, final String password) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressHide();
                        if (errCode != ErrCode.XOK) {
                            if (TextUtils.isEmpty(errMessage)) {
                                popupMessage("第三方账号注销失败, 错误码=" + errCode);
                            } else {
                                popupMessage("第三方账号注销失败, 错误信息=" + errMessage);
                            }

                        } else {
                            popupMessage("第三方账号注销成功!");
                            AppStorageUtil.deleteAllValue();

                            // 登出成功后直接退出当前界面，返回到入口界面，清空Activity堆栈
                            GotoEntryActivity();
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
    public void onLogoutDone(int errCode, String account) {
        Log.d(TAG, "<onLogoutDone> errCode=" + errCode + ", account=" + account);
        if (mUnregistering) {  // 注销的登录操作不用处理
            return;
        }

        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (errCode != ErrCode.XOK)   {
                    popupMessage("账号: " + account + " 登出失败");

                } else  {
                    // 登出成功后直接退出当前界面，返回到入口界面，清空Activity堆栈
                    GotoEntryActivity();
                }
            }
        });
    }

    @Override
    public void onLoginOtherDevice(String account) {
        Log.d(TAG, "<onLoginOtherDevice> account=" + account);

        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                popupMessage("账号: " + account + " 已经在其他设备上登录!");

                // 返回到登录界面，清空Activity堆栈
                GotoEntryActivity();
            }
        });
    }

}
