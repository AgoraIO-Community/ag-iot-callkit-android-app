/**
 * @file MyFragment.java
 * @brief This file display the user account information
 * @author xiaohua.lu
 * @email luxiaohua@agora.io
 * @version 1.0.0.1
 * @date 2021-11-17
 * @license Copyright (C) 2021 AgoraIO Inc. All rights reserved.
 */

package com.agora.doorbell.my;

import android.content.Intent;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.agora.agoracallkit.callkit.AgoraCallKit;
import com.agora.agoracallkit.callkit.CallKitAccount;
import com.agora.agoracallkit.callkit.ICallKitCallback;
import com.agora.doorbell.CalledActivity;
import com.agora.doorbell.LoginActivity;
import com.agora.doorbell.MainActivity;
import com.agora.doorbell.SplashActivity;
import com.agora.doorbell.base.BaseFragment;
import com.agora.doorbell.databinding.FragmentMyBinding;

import java.util.ArrayList;
import java.util.List;

public class MyFragment extends BaseFragment implements ICallKitCallback {
    private static final String TAG = "DoorBell/MyFragment";

    private FragmentMyBinding mBinding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "<onCreateView>");
        mBinding = FragmentMyBinding.inflate(inflater, container, false);

        CallKitAccount account = AgoraCallKit.getInstance().getLocalAccount();
        mBinding.tvName.setText("当前用户: " + account.getName());

        mBinding.btLoginOut.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onBtnLogout();
            }
        });

        mBinding.btnSetPrivParam.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String privParam = mBinding.etPrivParam.getText().toString();
                if (privParam.isEmpty()) {
                    return;
                }
                int ret = AgoraCallKit.getInstance().setTalkPrivateParam(privParam);
                if (ret == AgoraCallKit.ERR_NONE) {
                    mPopupMessage("设置私参: " + privParam + " 成功!");
                } else {
                    mPopupMessage("设置私参: " + privParam + " 失败!");
                }
            }
        });

        View rootView = mBinding.getRoot();
        return rootView;
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

        //注册呼叫监听
        AgoraCallKit.getInstance().registerListener(this);
    }

    @Override
    public void onStop() {
        Log.d(TAG, "<onStop>");
        super.onStop();

        // 注销呼叫监听
        AgoraCallKit.getInstance().unregisterListener(this);
    }


    /*
     * @brief 进行登出操作
     */
    void onBtnLogout() {
        mPgDigLogShow("正在登出当前账号......");

        int ret = AgoraCallKit.getInstance().accountLogOut();
        if (ret != AgoraCallKit.ERR_NONE) {
            mPgDigLogHide();
            mPopupMessage("账号登出失败, 错误码: " + ret);
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    /////////////////// Override ICallKitCallback Methods //////////////////////
    ////////////////////////////////////////////////////////////////////////////
    @Override
    public void onLogOutDone(CallKitAccount account, int errCode) {
        Log.d(TAG, "<onLogOutDone> errCode=" + errCode);

        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (errCode != AgoraCallKit.ERR_NONE)   {
                    mPopupMessage("账号: " + account.getName() + " 登出失败");

                } else  {
                    // 登出成功后直接退出当前界面，返回到登录界面，清空Activity堆栈
                    GotoLoginActivity();
                }
            }
        });
    }

    @Override
    public void onAlarmReceived(CallKitAccount account, CallKitAccount peer_account,
                                long timestamp, String alarmMsg) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mPopupMessage("接收到来自: " + peer_account.getName() + " 的告警消息: " + alarmMsg);
            }
        });
    }


}
