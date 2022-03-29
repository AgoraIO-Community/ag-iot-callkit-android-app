/**
 * @file MainActivity.java
 * @brief This file implement the entry user interface of application
 *        User should select the application role (App or IoT device) firstly
 *        Then input account for login or register a new account
 * @author xiaohua.lu
 * @email luxiaohua@agora.io
 * @version 1.0.0.1
 * @date 2021-10-13
 * @license Copyright (C) 2021 AgoraIO Inc. All rights reserved.
 */
package com.agora.agoracalldemo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatEditText;
import com.agora.agoracallkit.beans.UidInfoBean;
import com.agora.agoracallkit.callkit.AgoraCallKit;
import com.agora.agoracallkit.callkit.CallKitAccount;
import com.agora.agoracallkit.callkit.ICallKitCallback;
import com.hyphenate.easeim.R;
import com.hyphenate.easeim.databinding.ActivityMainBinding;

import java.util.ArrayList;


public class MainActivity extends BaseActivity implements ICallKitCallback {

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////// Constant Definition /////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    private final String TAG = "DEMO/MainActivity";



    //
    // message Id
    //
    public static final int MSGID_ROLE_CHOICED = 0x1001;    ///< Application role already choiced


    ///////////////////////////////////////////////////////////////////////////
    ///////////////////// Variable Definition /////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////

    private static Handler mMsgHandler = null;      ///< 主线程中的消息处理

    private PushApplication mApplication;           ///< Current application
    private ActivityMainBinding mBinding;           ///< 自动生成的view绑定类
    private AppCompatEditText mAccountWgt;          ///< 绑定账户输入框的数据
    private LoadingDialog mProgressDlg;             ///< 进度显示对话框
    private LoadingDialog.Builder mDlgBuilder;      ///< 进度对话框工厂类
    private int mChoicedRole = 0;                   ///< 选择的角色, 1:表示device;  其他:表示user
    private boolean mDblTalk = false;               ///< 是否双讲




    ///////////////////////////////////////////////////////////////////////////
    ///////////////////// Override Acitivyt Methods ////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "<onCreate>");
        super.onCreate(savedInstanceState);
        mBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());
        mApplication = (PushApplication) getApplication();

        mMsgHandler = new Handler(this.getMainLooper())
        {
            @SuppressLint("HandlerLeak")
            public void handleMessage(Message msg)
            {
                switch (msg.what)
                {
                    case MSGID_ROLE_CHOICED:
                        onMsgRoleChoiced();
                        break;
                }
            }
        };

        initView();
    }


    @Override
    protected void onStart() {
        Log.d(TAG, "<onStart>");
        super.onStart();
    }

    @Override
    protected void onAllPermissionGranted()
    {
        popupRoleChoiceDlg();
    }


    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "<onStop>");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "<onDestory>");
        super.onDestroy();

        if (mMsgHandler != null) {  // remove all messages
            mMsgHandler.removeMessages(MSGID_ROLE_CHOICED);
            mMsgHandler = null;
        }

        //注销呼叫监听
        AgoraCallKit.getInstance().unregisterListener(this);
    }

    /*
     * @brief 界面控件初始化
     */
    private void initView() {
        // 创建进度对话框
        mDlgBuilder = new LoadingDialog.Builder();
        mDlgBuilder.setMessage("");
        mDlgBuilder.setCancelable(false);
        mProgressDlg = mDlgBuilder.create(this);
        mProgressDlg.dismiss();

        //从view绑定类中获取账号密码
        mAccountWgt = mBinding.etAccount;

        // 设置注册按钮事件
        mBinding.btnRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBtnRegister();
            }
        });

        // 设置登录按钮事件
        mBinding.btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBtnLogin();
            }
        });

    }




    /*
     * @brief 注册按钮事件
     */
    void onBtnRegister()
    {
        //显示进度对话框
        mDlgBuilder.updateMessage("注册中...");
        mProgressDlg.show();

        //注册账号
        int roleType = ((PushApplication) getApplication()).getRoleType();
        String accountName = mAccountWgt.getText().toString();
        CallKitAccount registerAccount = new CallKitAccount(accountName, roleType);

        int ret = AgoraCallKit.getInstance().accountRegister(registerAccount);
        if (ret != AgoraCallKit.ERR_NONE) {
            mProgressDlg.dismiss();
            popupMessage("不能继续注册, 错误码: " + ret);
            return;
        }
    }

    /*
     * @brief 登录按钮事件
     */
    void onBtnLogin()
    {
        //显示进度对话框
        mDlgBuilder.updateMessage("登录中...");
        mProgressDlg.show();

        int roleType = ((PushApplication) getApplication()).getRoleType();
        String accountName = mAccountWgt.getText().toString();
        CallKitAccount logInAccount = new CallKitAccount(accountName, roleType);

        int ret = AgoraCallKit.getInstance().accountLogIn(logInAccount);
        if (ret != AgoraCallKit.ERR_NONE) {
            mProgressDlg.dismiss();
            popupMessage("不能登录, 错误码: " + ret);
            return;
        }
    }

    void popupRoleChoiceDlg()
    {
//        LayoutInflater inflater = LayoutInflater.from(this);
//        final View initEntryView = inflater.inflate(R.layout.init_application, null);
//        final RadioButton btnAsUser = (RadioButton)initEntryView.findViewById(R.id.btn_as_user);
//        final RadioButton btnAsDev = (RadioButton)initEntryView.findViewById(R.id.btn_as_dev);
//        final CheckBox cbDblTalk = (CheckBox)initEntryView.findViewById(R.id.cb_doubletalk);
//        AlertDialog.Builder builder = new AlertDialog.Builder(this);
//        builder.setTitle("初始化参数: ")
//                .setIcon(android.R.drawable.ic_menu_add)
//                .setView(initEntryView)
//                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
//                    public void onClick(DialogInterface dialog, int which) {
//                        if (btnAsDev.isChecked()) {
//                            mChoicedRole = 1;
//                        } else {
//                            mChoicedRole = 0;
//                        }
//
//                        boolean dblTalkChecked = cbDblTalk.isChecked();
//                        mDblTalk = dblTalkChecked;
//
//                        mDlgBuilder.updateMessage("正在初始化...");
//                        mProgressDlg.show();
//                        mMsgHandler.removeMessages(MSGID_ROLE_CHOICED);
//                        mMsgHandler.sendEmptyMessage(MSGID_ROLE_CHOICED);
//                    }});
//        builder.show();


        mDblTalk = false;
        mChoicedRole = 0;

        mDlgBuilder.updateMessage("正在初始化...");
        mProgressDlg.show();
        mMsgHandler.removeMessages(MSGID_ROLE_CHOICED);
        mMsgHandler.sendEmptyMessage(MSGID_ROLE_CHOICED);
    }

    /*
     * @brief: 角色选择确定事件
     */
    void onMsgRoleChoiced()
    {
        if (mChoicedRole == 1) {
            ((PushApplication) getApplication()).setRoleType(UidInfoBean.TYPE_MAP_DEVICE);
        } else {
            ((PushApplication) getApplication()).setRoleType(UidInfoBean.TYPE_MAP_USER);
        }
        ((PushApplication) getApplication()).setDblTalk(mDblTalk);

        // 在获得权限后才能初始化引擎，内部已经做了多次初始化处理
        mApplication.initializeEngine();

        //注册呼叫监听
        AgoraCallKit.getInstance().registerListener(this);


        //先尝试自动登录，如果自动登录成功，直接进入拨号页面
        mDlgBuilder.updateMessage("自动登录中...");
        mProgressDlg.show();

        int ret = AgoraCallKit.getInstance().accountAutoLogin();
        if (ret != AgoraCallKit.ERR_NONE) {
            mProgressDlg.dismiss();
            popupMessage("不能自动登录, 错误码: " + ret);
            return;
        }
    }


    void popupMessage(String message)
    {
        Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
    }




    ////////////////////////////////////////////////////////////////////////////
    /////////////////// Override ICallKitCallback Methods //////////////////////
    ////////////////////////////////////////////////////////////////////////////
    @Override
    public void onPeerIncoming(CallKitAccount account, CallKitAccount peer_account, String attachMsg) {
        Log.d(TAG, "<onPeerIncoming>");
        //切换到呼叫接听选择页面
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //跳转到联系人列表页面，附带UID信息
                Intent activityIntent = new Intent(MainActivity.this, CalledActivity.class);
                activityIntent.putExtra("caller_name", peer_account.getName());
                activityIntent.putExtra("attach_msg", attachMsg);
                startActivity(activityIntent);
            }
        });
    }

    @Override
    public void onRegisterDone(CallKitAccount account, int errCode) {
        Log.d(TAG, "<onRegisterDone>");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mProgressDlg.dismiss();

                if (errCode != AgoraCallKit.ERR_NONE)   {
                    popupMessage("账号: " + account.getName() + " 注册失败");

                } else  {
                    popupMessage("账号: " + account.getName() + " 注册成功，正在登录中...");
                    onBtnLogin();
                }
            }
        });
    }

    @Override
    public void onLogInDone(CallKitAccount account, int errCode) {
        Log.d(TAG, "<onLogInDone>");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mProgressDlg.dismiss();

                if (account == null) {
                    popupMessage("登录失败，没有有效的账号");
                    return;
                }

                if (errCode != AgoraCallKit.ERR_NONE)   {
                    popupMessage("账号: " + account.getName() + " 登录失败");

                } else  {
                    // 切换到登录成功界面
                    Intent activityIntent = new Intent(MainActivity.this, LoggedActivity.class);
                    startActivity(activityIntent);
                }
            }
        });
    }

}