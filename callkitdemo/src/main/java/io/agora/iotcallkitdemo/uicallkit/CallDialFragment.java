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

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import io.agora.iotcallkitdemo.HomePageActivity;
import io.agora.iotcallkitdemo.PushApplication;
import io.agora.iotcallkitdemo.databinding.FragmentCallDialBinding;
import io.agora.iotcallkitdemo.uibase.BaseFragment;
import io.agora.iotcallkitdemo.uibase.PermissionHandler;
import io.agora.iotcallkitdemo.uibase.PermissionItem;
import io.agora.iotcallkit.ACallkitSdkFactory;
import io.agora.iotcallkit.ErrCode;
import io.agora.iotcallkit.IAccountMgr;
import io.agora.iotcallkit.ICallkitMgr;
import com.amazonaws.util.Base32;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;



public class CallDialFragment extends BaseFragment implements IAccountMgr.ICallback,
        ICallkitMgr.ICallback, PermissionHandler.ICallback {
    private final String TAG = "IOTAPP20/CallDialFrag";

    //
    // message Id
    //
    public static final int MSGID_CALL_DIAL = 0x1001;    ///< 主动呼叫

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////// Variable Definition /////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    private FragmentCallDialBinding mBinding;

    private Handler mMsgHandler = null;             ///< 主线程中的消息处理

    private PermissionHandler mPermHandler;         ///< 权限申请处理
    private String mDialingAccountId;               ///< 要呼叫的对端账号Id

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////// Override Fragment Methods ////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    public CallDialFragment() {
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "<onCreateView>");
        mBinding = mBinding.inflate(inflater, container, false);
        View rootView = mBinding.getRoot();

        mMsgHandler = new Handler(getActivity().getMainLooper())
        {
            @SuppressLint("HandlerLeak")
            public void handleMessage(Message msg)
            {
                switch (msg.what)
                {
                    case MSGID_CALL_DIAL:
                        onMsgCallDial();
                        break;
                    default:
                        break;
                }
            }
        };

        return rootView;
    }


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "<onViewCreated>");
        super.onViewCreated(view, savedInstanceState);

        String accountName = ACallkitSdkFactory.getInstance().getAccountMgr().getAccount();
        String accountId = ACallkitSdkFactory.getInstance().getAccountMgr().getAccountId();
        mBinding.tvMyaccount.setText("当前账号: " + accountName);
        mBinding.tvAccountid.setText(accountId);

        mBinding.btnDial.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBtnDial();
            }
        });

        mBinding.btnDialdevice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBtnDialDevice();
            }
        });
    }

    @Override
    public void onDestroyView() {
        Log.d(TAG, "<onDestroyView>");
        super.onDestroyView();

        if (mMsgHandler != null) {
            mMsgHandler.removeMessages(MSGID_CALL_DIAL);
            mMsgHandler = null;
        }
    }


    @Override
    public void onStart() {
        Log.d(TAG, "<onStart>");
        super.onStart();

        // 注册 账号、呼叫、回调监听
        ACallkitSdkFactory.getInstance().getAccountMgr().registerListener(this);
        ACallkitSdkFactory.getInstance().getCallkitMgr().registerListener(this);
    }

    @Override
    public void onStop() {
        Log.d(TAG, "<onStop>");
        super.onStop();

        // 注销 账号、呼叫 回调监听
        ACallkitSdkFactory.getInstance().getAccountMgr().unregisterListener(this);
        ACallkitSdkFactory.getInstance().getCallkitMgr().unregisterListener(this);
    }


    ////////////////////////////////////////////////////////////////////////////
    /////////////////// Implement all Buttons Events ///////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    /**
     * @brief 通过 accountId来拨号对端
     */
    void onBtnDial()  {
        String peerAccountId = mBinding.etAccountid.getText().toString();
        if ((peerAccountId == null) || (peerAccountId.length() <= 0)) {
            popupMessage("请输入正确的对端账号Id");
            return;
        }

        mDialingAccountId = peerAccountId;
        mMsgHandler.sendEmptyMessage(MSGID_CALL_DIAL);
    }

    /**
     * @brief 通过 (productKey+deviceId)==> accountId 来拨号对端
     */
    void onBtnDialDevice()  {

        String productKey = mBinding.etProductKey.getText().toString();
        if ((productKey == null) || (productKey.length() <= 0)) {
            popupMessage("请输入正确的 ProductKey");
            return;
        }

        String deviceId = mBinding.etDeviceid.getText().toString();
        if ((deviceId == null) || (deviceId.length() <= 0)) {
            popupMessage("请输入正确的 deviceId");
            return;
        }

        // 生成相应的 accountId
        String peerName = productKey + "-" + deviceId;
        String base32Name = Base32.encodeAsString(peerName.getBytes());
        String trimBase32 = base32Name.replace("=", "");
        Log.d(TAG, "<onBtnDialDevice> trimBase32=" + trimBase32);

        mDialingAccountId = trimBase32;
        mMsgHandler.sendEmptyMessage(MSGID_CALL_DIAL);
    }


    void onMsgCallDial() {
        //
        // Microphone权限判断处理
        //
        int[] permIdArray = new int[1];
        permIdArray[0] = PermissionHandler.PERM_ID_RECORD_AUDIO;
        mPermHandler = new PermissionHandler(getActivity(), this, permIdArray);
        if (!mPermHandler.isAllPermissionGranted()) {
            Log.d(TAG, "<onMsgCallDial> requesting permission...");
            mPermHandler.requestNextPermission();
        } else {
            Log.d(TAG, "<onMsgCallDial> permission ready");
            doCallDial();
        }
    }

    public void onFragRequestPermissionsResult(int requestCode, @NonNull String permissions[],
                                               @NonNull int[] grantResults) {
        Log.d(TAG, "<onFragRequestPermissionsResult> requestCode=" + requestCode);
        if (mPermHandler != null) {
            mPermHandler.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    public void onAllPermisonReqDone(boolean allGranted, final PermissionItem[] permItems) {
        Log.d(TAG, "<onAllPermisonReqDone> allGranted = " + allGranted);

        if (permItems[0].requestId == PermissionHandler.PERM_ID_CAMERA) {  // Camera权限结果
            if (allGranted) {
                doCallDial();
            } else {
                popupMessage("没有相应的操作权限");
            }

        } else if (permItems[0].requestId == PermissionHandler.PERM_ID_RECORD_AUDIO) { // 麦克风权限结果
            if (allGranted) {
                doCallDial();
            } else {
                popupMessage("没有相应的操作权限");
            }
        }
    }


    void doCallDial() {

        // 设置当前通话的设备信息
        PushApplication application = (PushApplication)(this.getActivity().getApplication());
        application.setLivingPeerAccountId(mDialingAccountId);

        //调试呼叫设备
        progressShow("正在呼叫对端: " + mDialingAccountId + " ...");
        ICallkitMgr callkitMgr = ACallkitSdkFactory.getInstance().getCallkitMgr();
        int errCode = callkitMgr.callDial(mDialingAccountId, "This is application");
        if (errCode == ErrCode.XERR_CALLKIT_PEER_BUSY) {
            progressHide();
            popupMessage("拨号失败，对端忙!");
        } else if (errCode != ErrCode.XOK) {
            progressHide();
            popupMessage("拨号失败，错误码=" + errCode);
        }
    }

    @Override
    public void onDialDone(int errCode, final String peerAccountId) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                progressHide();
                if (errCode != ErrCode.XOK)   {
                    popupMessage("呼叫: " + peerAccountId + " 错误");
                } else  {
                    // 跳转到设备控制界面
                    Intent intent = new Intent(getActivity(), CallLivingActivity.class);
                    startActivity(intent);
                }
            }
        });
    }

    @Override
    public void onPeerIncoming(final String peerAccountId, final String attachMsg) {
        //切换到呼叫接听选择页面
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // 界面切换到前台
                HomePageActivity hompageActivity = (HomePageActivity)(getActivity());
                hompageActivity.setTopApp(hompageActivity);

                // 设置当前通话的设备信息
                PushApplication application = (PushApplication)(getActivity().getApplication());
                application.setLivingPeerAccountId(peerAccountId);

                // 跳转到来电呼叫界面，带上来电账号信息
                Intent activityIntent = new Intent(getActivity(), CallIncomingActivity.class);
                activityIntent.putExtra("attach_msg", attachMsg);
                startActivity(activityIntent);
            }
        });
    }


    @Override
    public void onLoginOtherDevice(final String account) {

        //切换到呼叫接听选择页面
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                popupMessage("账号: " + account + " 已经在其他设备上登录!");

                // 返回到登录界面，清空Activity堆栈
                GotoEntryActivity();
            }
        });
    }

    @Override
    public void onCallkitError(int errCode) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (errCode == ErrCode.XERR_NETWORK) {
                    popupMessage("网络错误");
                } else {
                    popupMessage("系统错误，错误代码=" + errCode);
                }
            }
        });
    }
}
