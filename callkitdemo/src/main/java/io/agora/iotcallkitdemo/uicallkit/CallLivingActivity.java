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
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.text.format.Time;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;

import io.agora.iotcallkitdemo.PushApplication;
import io.agora.iotcallkitdemo.R;
import io.agora.iotcallkitdemo.databinding.ActivityCallLivingBinding;
import io.agora.iotcallkitdemo.uibase.BaseActivity;
import io.agora.iotcallkit.ACallkitSdkFactory;
import io.agora.iotcallkit.ErrCode;
import io.agora.iotcallkit.IAccountMgr;
import io.agora.iotcallkit.ICallkitMgr;


import java.util.HashMap;
import java.util.Locale;
import java.util.Map;


public class CallLivingActivity extends BaseActivity implements IAccountMgr.ICallback,
        ICallkitMgr.ICallback    {
    private final String TAG = "IOTAPP20/DevLiveAct";
    private static final int TIMER_UPDATE_NETSATUS = 2000;     ///< 网络状态定时2秒刷新一次

    //
    // message Id
    //
    public static final int MSGID_UPDATE_NETSTATUS = 0x1001;    ///< 更新网络状态

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////// Variable Definition /////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    private ActivityCallLivingBinding mBinding;       ///< 自动生成的view绑定类
    private PushApplication mApplication;
    private String mLivingPeerAccountId;                ///< 当前联接的对端账号
    private volatile boolean mConnected = false;        ///< 当前设备是否已经拨通
    private volatile boolean mIsAnswer = false;         ///< 是否通过接听来电进入的

    private Handler mMsgHandler = null;                 ///< 主线程中的消息处理
    private volatile boolean mSendingVoice = false;     ///< 是否正在发送语音
    private boolean mIsOrientLandscape = false;         ///< 当前是否正在横屏显示

    ////////////////////////////////////////////////////////////////////////////
    ///////////////////////// Override Activity Methods ////////////////////////
    ////////////////////////////////////////////////////////////////////////////

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "<onCreate>");
        super.onCreate(savedInstanceState);

        mMsgHandler = new Handler(getMainLooper())
        {
            @SuppressLint("HandlerLeak")
            public void handleMessage(Message msg)
            {
                switch (msg.what)
                {
                    case MSGID_UPDATE_NETSTATUS:
                        onMsgUpdateNetStatus();
                        break;
                }
            }
        };

        //创建view绑定类的实例，使其成为屏幕上的活动视图
        mBinding = ActivityCallLivingBinding.inflate(getLayoutInflater());
        View view = mBinding.getRoot();
        setContentView(view);

        mApplication = (PushApplication)this.getApplication();
        mLivingPeerAccountId = mApplication.getLivingPeerAccountId();

        if ((mLivingPeerAccountId != null) && (!mLivingPeerAccountId.isEmpty())) {
            mBinding.nvTitleBar.tvTitle.setText(mLivingPeerAccountId);
        } else {
            mBinding.nvTitleBar.tvTitle.setText(" ");
        }
        mBinding.nvTitleBar.tvOption.setVisibility(View.INVISIBLE);
        mBinding.nvTitleBar.ivBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {  onBtnBack();   }
        });

        mSendingVoice = false;

        mIsOrientLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        if (mIsOrientLandscape) {
            mBinding.lyTitleBar.setVisibility(View.GONE);
            View decorView = getWindow().getDecorView();
            int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN;
            decorView.setSystemUiVisibility(uiOptions);
        } else {
            mBinding.lyTitleBar.setVisibility(View.VISIBLE);
        }

        mIsAnswer = getIntent().getBooleanExtra("answer", false);
        if (mIsAnswer) {
            mConnected = true;
            mBinding.tvStatus.setText("设备浏览中...");
            mMsgHandler.sendEmptyMessageDelayed(MSGID_UPDATE_NETSTATUS, TIMER_UPDATE_NETSATUS);
        }


        mBinding.rlLandscape.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {  onBtnLandscape();   }
        });

        mBinding.rlScreenshot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {  onBtnScreenshot();   }
        });

        mBinding.btnAudioEffect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {  onBtnAudioEffect();   }
        });


        // 设置视频显示View控件
        ACallkitSdkFactory.getInstance().getCallkitMgr().setPeerVideoView(mBinding.peerView);

        if (!mConnected) {
            mBinding.tvStatus.setText("设备联接中...");
        }

    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "<onDestory>");
        super.onDestroy();

        if (mMsgHandler != null) {
            mMsgHandler.removeMessages(MSGID_UPDATE_NETSTATUS);
            mMsgHandler = null;
        }
    }

    @Override
    protected void onStart() {
        Log.d(TAG, "<onStart>");
        super.onStart();

        // 注册 账号、呼叫、设备管理、告警、通知 回调监听
        ACallkitSdkFactory.getInstance().getAccountMgr().registerListener(this);
        ACallkitSdkFactory.getInstance().getCallkitMgr().registerListener(this);
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
    protected void onResume() {
        Log.d(TAG, "<onResume>");
        super.onResume();

    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            onBtnBack();
        }
        return super.onKeyDown(keyCode, event);
    }

    ////////////////////////////////////////////////////////////////////////////
    ///////////////////////// Message and Events Handler ///////////////////////
    ////////////////////////////////////////////////////////////////////////////
    /*
     * @brief 更新网络状态
     */
    void onMsgUpdateNetStatus()
    {
        ICallkitMgr.RtcNetworkStatus networkStatus;
        networkStatus = ACallkitSdkFactory.getInstance().getCallkitMgr().getNetworkStatus();

        String status1 = String.format(Locale.getDefault(),
                "Lastmile Delay: %d ms", networkStatus.lastmileDelay);
        String status2 = String.format(Locale.getDefault(),
                "Video Send/Recv: %d kbps / %d kbps",
                networkStatus.txVideoKBitRate, networkStatus.rxVideoKBitRate);
        String status3 = String.format(Locale.getDefault(),"Audio Send/Recv: %d kbps / %d kbps",
                networkStatus.txAudioKBitRate, networkStatus.rxAudioKBitRate);

        String status = status1 + "\n" + status2 + "\n" + status3;
        mBinding.tvNetwork.setText(status);

        mMsgHandler.removeMessages(MSGID_UPDATE_NETSTATUS);
        mMsgHandler.sendEmptyMessageDelayed(MSGID_UPDATE_NETSTATUS, TIMER_UPDATE_NETSATUS);
    }

    /*
     * @brief 横屏切换
     */
    void onBtnLandscape() {
        if (mIsOrientLandscape) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
    }

    /*
     * @brief 截屏功能
     */
    void onBtnScreenshot() {
        Bitmap videoFrameBmp = ACallkitSdkFactory.getInstance().getCallkitMgr().capturePeerVideoFrame();
        if (videoFrameBmp != null) {
            Time time = new Time("GMT+8");
            time.setToNow();
            int year = time.year;
            int month = time.month;
            int day = time.monthDay;
            int hour = time.hour;
            int minute = time.minute;
            int sec = time.second;

            String strSdPath = Environment.getExternalStorageDirectory().getAbsolutePath();
            String strFilePath = String.format(Locale.getDefault(),"%s/%s_shot_%d%d%d_%d%d%d.jpg",
                    strSdPath, mLivingPeerAccountId, year, month, day, hour, minute, sec);
            saveBmpToFile(videoFrameBmp, strFilePath);

            String tips = "截图保存到： " + strFilePath;
            popupMessage(tips);
        }
    }

    /*
     * @brief 音频特效
     */
    void onBtnAudioEffect() {
        PopupMenu popupMenu = new PopupMenu(this, mBinding.btnAudioEffect);
        MenuInflater inflater = popupMenu.getMenuInflater();
        inflater.inflate(R.menu.menu_audioeffect, popupMenu.getMenu());

        popupMenu.setOnMenuItemClickListener(
                new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        int itemId = item.getItemId();
                        ICallkitMgr.AudioEffectId effectId = ICallkitMgr.AudioEffectId.NORMAL;
                        if (itemId == R.id.menu_audio_normal) {
                            effectId = ICallkitMgr.AudioEffectId.NORMAL;
                        } else if (itemId == R.id.menu_audio_oldman) {
                            effectId = ICallkitMgr.AudioEffectId.OLDMAN;
                        } else if (itemId == R.id.menu_audio_boy) {
                            effectId = ICallkitMgr.AudioEffectId.BABYBOY;
                        } else if (itemId == R.id.menu_audio_girl) {
                            effectId = ICallkitMgr.AudioEffectId.BABYGIRL;
                        } else if (itemId == R.id.menu_audio_zhubajie) {
                            effectId = ICallkitMgr.AudioEffectId.ZHUBAJIE;
                        } else if (itemId == R.id.menu_audio_ethereal) {
                            effectId = ICallkitMgr.AudioEffectId.ETHEREAL;
                        } else if (itemId == R.id.menu_audio_hulk) {
                            effectId = ICallkitMgr.AudioEffectId.HULK;
                        } else {
                            return true;
                        }

                        int errCode = ACallkitSdkFactory.getInstance().getCallkitMgr().setAudioEffect(effectId);
                        if (errCode != ErrCode.XOK) {
                            popupMessage("设置音频特效错误，错误码: " + errCode);
                            return true;
                        }

                        popupMessage("设置音频特效完成");
                        return true;
                    }
                });
        popupMenu.show();
    }


    void onBtnBack() {
        if (mIsOrientLandscape) { // 退回到 portrait竖屏显示
            Log.d(TAG, "<onKeyDown> BACK Key return portrait mode");
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        } else {
            Log.d(TAG, "<onKeyDown> BACK Key exit talking");
            ACallkitSdkFactory.getInstance().getCallkitMgr().callHangup(); // 本地挂断处理
            finish();  // 退出当前界面，返回到已经登录界面
        }
    }



    ////////////////////////////////////////////////////////////////////////////
    /////////////////// Override IAccountMgr.ICallback Methods /////////////////
    ////////////////////////////////////////////////////////////////////////////
    @Override
    public void onLoginOtherDevice(final String account) {
        Log.d(TAG, "<onLoginOtherDevice> account=" + account);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                progressHide();
                popupMessage("账号在其他设备登录,本地立即退出!");
                ACallkitSdkFactory.getInstance().getCallkitMgr().callHangup(); // 本地挂断处理

                // 返回到登录界面，清空Activity堆栈
                GotoEntryActivity(mActivity);
            }
        });
    }


    ////////////////////////////////////////////////////////////////////////////
    /////////////////// Override ICallkitMgr.ICallback Methods /////////////////
    ////////////////////////////////////////////////////////////////////////////
    @Override
    public void onPeerAnswer(final String peerAccountId) {
        mIsAnswer = true;
        Log.d(TAG, "<onPeerAnswer> peerAccountId=" + peerAccountId);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mBinding.tvStatus.setText("设备浏览中...");
                ACallkitSdkFactory.getInstance().getCallkitMgr().setPeerVideoView(mBinding.peerView);
                mMsgHandler.sendEmptyMessageDelayed(MSGID_UPDATE_NETSTATUS, TIMER_UPDATE_NETSATUS);
            }
        });
    }

    @Override
    public void onPeerHangup(final String peerAccountId) {
        Log.d(TAG, "<onPeerHangup> peerAccountId=" + peerAccountId);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mIsAnswer) {
                    popupMessage("对方已挂断！");
                } else {
                    popupMessage("您呼叫用户拒绝，请稍后再拨！");
                }
                finish();
            }
        });
    }

    @Override
    public void onPeerTimeout(final String peerAccountId) {
        mIsAnswer = false;
        Log.d(TAG, "<onPeerTimeout> peerAccountId=" + peerAccountId);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                popupMessage("您呼叫用户无人接听，请稍后再拨！");
                finish();
            }
        });
    }

}