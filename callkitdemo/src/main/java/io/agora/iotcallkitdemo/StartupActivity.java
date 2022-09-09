
package io.agora.iotcallkitdemo;


import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import io.agora.iotcallkit.ACallkitSdkFactory;
import io.agora.iotcallkit.ErrCode;
import io.agora.iotcallkit.IAccountMgr;
import io.agora.iotcallkitdemo.databinding.ActivityStartupBinding;
import io.agora.iotcallkitdemo.thirdpartyaccount.ThirdAccountMgr;
import io.agora.iotcallkitdemo.uiaccount.EntryActivity;
import io.agora.iotcallkitdemo.uibase.BaseActivity;


public class StartupActivity extends BaseActivity implements IAccountMgr.ICallback  {

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////// Constant Definition /////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    private final String TAG = "IOTAPP20/StartupAct";
    private final long UI_DISPLAY_TIME = 2000;

    //
    // message Id
    //
    public static final int MSGID_CHECK_STATE = 0x1001;    ///< 检测当前是否已经登录

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////// Variable Definition /////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    private ActivityStartupBinding mBinding;                 ///< 自动生成的view绑定类
    private Handler mMsgHandler = null;                     ///< 主线程中的消息处理
    private volatile boolean mAutoLogining = false;         ///< 当前是否正在自动登录中

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////// Override Activity Methods ////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "<onCreate>");
        super.onCreate(savedInstanceState);
        mBinding = ActivityStartupBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());


        mMsgHandler = new Handler(getMainLooper())
        {
            @SuppressLint("HandlerLeak")
            public void handleMessage(Message msg)
            {
                switch (msg.what)
                {
                    case MSGID_CHECK_STATE:
                        onMsgCheckState();
                        break;
                }
            }
        };

        // 初始化引擎
        PushApplication appInstance = (PushApplication) getApplication();
        appInstance.initializeEngine();

        mMsgHandler.removeMessages(MSGID_CHECK_STATE);
        mMsgHandler.sendEmptyMessage(MSGID_CHECK_STATE);

        // 注册账号管理监听
        ACallkitSdkFactory.getInstance().getAccountMgr().registerListener(this);
    }


    @Override
    protected void onStart() {
        Log.d(TAG, "<onStart>");
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "<onStop>");
        super.onStop();

        // 注销账号管理监听
        ACallkitSdkFactory.getInstance().getAccountMgr().unregisterListener(this);
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "<onDestory>");
        super.onDestroy();


        if (mMsgHandler != null) {
            mMsgHandler.removeMessages(MSGID_CHECK_STATE);
            mMsgHandler = null;
        }
    }

    /*
     @ brief 检测当前是否已经登录
     */
    void onMsgCheckState() {
        //
        // 当前已经登录，直接跳转到主页界面
        //
        String account = ACallkitSdkFactory.getInstance().getAccountMgr().getLoggedAccount();
        if ((account != null) && (!account.isEmpty())) {
            Log.d(TAG, "<onMsgCheckState> account already logined, goto HomePage activity");
            gotoHomePage();
            return;
        }


        String storedAccount = AppStorageUtil.queryValue(AppStorageUtil.KEY_ACCOUNT);
        String storedPassword = AppStorageUtil.queryValue(AppStorageUtil.KEY_PASSWORD);
        if ((storedAccount.length() <= 0) || (storedPassword.length() <= 0)) {
            Log.d(TAG, "<onMsgCheckState> NO history account, goto Entry activity");
            delayGotoEntryUI();
            return;
        }


        //
        // 尝试使用上一次的账号进行登录
        //
        ThirdAccountMgr.getInstance().login(storedAccount, storedPassword, new ThirdAccountMgr.ILoginCallback() {
            @Override
            public void onThirdAccountLoginDone(int errCode, final String errMessage,
                                                final String account, final String password,
                                                final IAccountMgr.LoginParam loginParam) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (errCode != ErrCode.XOK) {  // 自动登录失败
                            Log.e(TAG, "<onThirdAccountLoginDone> thirdparty login failure, errCode=" + errCode);
                            delayGotoEntryUI();
                            return;
                        }

                        int ret = ACallkitSdkFactory.getInstance().getAccountMgr().login(loginParam);
                        if (ret != ErrCode.XOK) {  // 自动SDK登录失败
                            Log.e(TAG, "<onThirdAccountLoginDone> SDK login failure, ret=" + ret);
                            delayGotoEntryUI();
                            return;
                        }

                    }
                });
            }
        });
    }

    /**
     * @brief 延迟两秒切换到入口界面
     */
    void delayGotoEntryUI() {
        Log.d(TAG, "<delayGotoEntryUI>");
        new android.os.Handler(Looper.getMainLooper()).postDelayed(
                new Runnable() {
                    public void run() {
                        Intent intent = new Intent(StartupActivity.this, EntryActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK); // 清除Activity堆栈
                        startActivity(intent);
                    }
                },
                UI_DISPLAY_TIME);
    }


    void gotoHomePage() {
        // 切换到主页界面
        Intent intent = new Intent(StartupActivity.this, HomePageActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK); // 清除Activity堆栈
        startActivity(intent);
    }

    ////////////////////////////////////////////////////////////////////////////
    /////////////////// Override IAccountMgr.ICallback Methods /////////////////
    ////////////////////////////////////////////////////////////////////////////
    @Override
    public void onLoginDone(int errCode, String account) {
        Log.d(TAG, "<onLoginDone> errCode=" + errCode
                + ", account=" + account);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if ((account == null) || (errCode != ErrCode.XOK)) {
                    Log.e(TAG, "<onLoginDone> failure, goto EntryActivity");

                    // 切换到 登录注册界面
                    Intent intent = new Intent(StartupActivity.this, EntryActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK); // 清除Activity堆栈
                    startActivity(intent);

                } else {
                    Log.d(TAG, "<onLoginDone> successful, goto HomePageActivity");

                    // 切换到主页界面
                    Intent intent = new Intent(StartupActivity.this, HomePageActivity.class);
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