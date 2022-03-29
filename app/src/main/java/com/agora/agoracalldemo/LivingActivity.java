/**
 * @file LivingActivity.java
 * @brief This file implement user interface of a dialing, it will be shown as following status:
 *        while there is a remote call incoming
 *        or dial a cll to remote
 * @author xiaohua.lu
 * @email luxiaohua@agora.io
 * @version 1.0.0.1
 * @date 2021-10-13
 * @license Copyright (C) 2021 AgoraIO Inc. All rights reserved.
 */
package com.agora.agoracalldemo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.text.InputFilter;
import android.text.format.Time;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.agora.agoracallkit.beans.UidInfoBean;
import com.agora.agoracallkit.callkit.AgoraCallKit;
import com.agora.agoracallkit.callkit.CallKitAccount;
import com.agora.agoracallkit.callkit.ICallKitCallback;
import com.hyphenate.easeim.R;
import com.hyphenate.easeim.databinding.ActivityLivingBinding;
import com.hyphenate.easeim.databinding.AudioEffectsBinding;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


public class LivingActivity extends AppCompatActivity implements ICallKitCallback {
    private final String TAG = "DEMO/LivingActivity";
    public static int TIMER_UPDATE_NETSATUS = 2000;     ///< 网络状态定时2秒刷新一次

    //
    // message Id
    //
    public static final int MSGID_UPDATE_NETSTATUS = 0x1001;    ///< 更新网络状态



    ///////////////////////////////////////////////////////////////////////////
    ///////////////////// Variable Definition /////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    private static Handler mMsgHandler = null;      ///< 主线程中的消息处理
    private AppCompatActivity mActivity;
    private com.agora.agoracalldemo.PushApplication mApplication;
    private ActivityLivingBinding mBinding;         ///< 自动生成的view绑定类
    private boolean mVoiceMuted = false;            ///< 是否已经静音
    private boolean mConnected = false;             ///< 是否已经跟对端建立联接
    private SurfaceView mPeerView;                  ///< 显示对端视频的控件


    /////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////// Override Methods of Activity ////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "<onCreate>");
        super.onCreate(savedInstanceState);
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);

        mApplication = (com.agora.agoracalldemo.PushApplication) getApplication();
        mActivity = this;

        //创建view绑定类的实例，使其成为屏幕上的活动视图
        mBinding = ActivityLivingBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());

        // 标题栏的Back按钮
        mBinding.nvTitleBar.ivBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "<ivBack.OnClickListener> ");
                AgoraCallKit.getInstance().callHangup();  // 本地挂断处理
                finish();  // 退出当前界面，返回到已经登录界面
            }
        });

        // 如果是来电则默认建立联接
        mConnected = this.getIntent().getBooleanExtra("answer", false);

        // 显示设备名称
        String caller = this.getIntent().getStringExtra("caller_name");
        mBinding.tvDevName.tvPrompt.setText("设备名称: ");
        mBinding.tvDevName.tvText.setText(caller);

        // 初始化网络状态
        mBinding.tvNetStatus.tvPrompt.setText("网络状态: ");
        mBinding.tvNetStatus.tvText.setText("正常");

        // 初始化分辨率
        mBinding.tvResolution.tvPrompt.setText("分辨率: ");
        mBinding.tvResolution.tvText.setText("720P");

        // 初始化帧率
        mBinding.tvFramerate.tvPrompt.setText("帧率: ");
        mBinding.tvFramerate.tvText.setText("15fps");

        // 初始化码率
        mBinding.tvBitrate.tvPrompt.setText("码率: ");
        mBinding.tvBitrate.tvText.setText("0Kbps");

        // 初始化时延
        mBinding.tvDelay.tvPrompt.setText("时延: ");
        mBinding.tvDelay.tvText.setText("50ms");


        // 获取呼叫状态显示信息
        String state = this.getIntent().getStringExtra("call_state");

        // 显示对端视频
        mPeerView = mBinding.svPeerView;
        AgoraCallKit.getInstance().setPeerVideoView(mPeerView);

        // 通话禁音按钮
        mVoiceMuted = false;
        mBinding.btnMute.ivBtnImg.setImageResource(R.mipmap.ic_unmute);
        mBinding.btnMute.tvBtnText.setText("音频");
        AgoraCallKit.getInstance().mutePeerAudioStream(false);
        AgoraCallKit.getInstance().muteLocalAudioStream(false);
        mBinding.llMute.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "<llMute.OnClickListener> mute or unmute");
                onBtnMuteUnmute();
            }
        });

        //挂断按钮
        mBinding.btnHangup.ivBtnImg.setImageResource(R.mipmap.ic_hangup);
        mBinding.btnHangup.tvBtnText.setText("挂断");
        mBinding.llHangup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "<llHangup.OnClickListener> Hangup the call");
                AgoraCallKit.getInstance().callHangup();  // 本地挂断处理
                finish();  // 退出当前界面，返回到已经登录界面
            }
        });

        // 变声按钮
        mBinding.btnEffect.ivBtnImg.setImageResource(R.mipmap.ic_audeffect);
        mBinding.btnEffect.tvBtnText.setText("变声");
        mBinding.llEffect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "<llEffect.OnClickListener> change the voice");
                onBtnEffect();
            }
        });

        mMsgHandler = new Handler(this.getMainLooper())
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

        if (mConnected) {
            mMsgHandler.sendEmptyMessageDelayed(MSGID_UPDATE_NETSTATUS, TIMER_UPDATE_NETSATUS);
        }


    }

    @Override
    protected void onStart() {
        Log.d(TAG, "<onStart>");
        super.onStart();
        //注册呼叫监听
        AgoraCallKit.getInstance().registerListener(this);
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "<onResume>");
        super.onResume();

        // 页面恢复时候检查是否在呼叫状态
        int state = AgoraCallKit.getInstance().getState();

        if ((AgoraCallKit.STATE_CALL_TALKING != state) &&
            (AgoraCallKit.STATE_CALL_DIALING != state) &&
            (AgoraCallKit.STATE_CALL_INCOMING != state)) {
            popupMessage("对方已经挂断!");
            finish();
        }

        // 如果在通话状态
        if (AgoraCallKit.STATE_CALL_TALKING == state) {

        }

    }

    @Override
    protected void onStop() {
        Log.d(TAG, "<onStop>");
        super.onStop();
        //注销呼叫监听
        AgoraCallKit.getInstance().unregisterListener(this);
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "<onDestory>");
        super.onDestroy();

        if (mMsgHandler != null) {  // remove all messages
            mMsgHandler.removeMessages(MSGID_UPDATE_NETSTATUS);
            mMsgHandler = null;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            Log.i(TAG, "<onKeyDown> Hangup the call");
            AgoraCallKit.getInstance().callHangup();  // 本地挂断处理
            finish();  // 退出当前界面，返回到已经登录界面
        }
        return super.onKeyDown(keyCode, event);
    }


    /////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////// Message or Event Handle ////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////

    void onBtnMuteUnmute()
    {
        if (mVoiceMuted) {
            AgoraCallKit.getInstance().mutePeerAudioStream(false);
            AgoraCallKit.getInstance().muteLocalAudioStream(false);

            mBinding.btnMute.ivBtnImg.setImageResource(R.mipmap.ic_unmute);
            mVoiceMuted = false;

        } else {
            AgoraCallKit.getInstance().mutePeerAudioStream(true);
            AgoraCallKit.getInstance().muteLocalAudioStream(true);

            mBinding.btnMute.ivBtnImg.setImageResource(R.mipmap.ic_mute);
            mVoiceMuted = true;
        }
    }

    void onBtnEffect()
    {
        LayoutInflater inflater = LayoutInflater.from(this);
        AudioEffectsBinding effectmBinding = AudioEffectsBinding.inflate(inflater);
        final View effectView = inflater.inflate(R.layout.audio_effects, effectmBinding.getRoot());

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("变 声")
               .setView(effectView);
        AlertDialog dialog = builder.show();

        effectmBinding.llOrg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                boolean ret = AgoraCallKit.getInstance().setLocalVoiceType(AgoraCallKit.VoiceType.NORMAL);
                String result = (ret) ? "成功" : "失败";
                popupMessage("设置 原声 音效" + result);
            }
        });

        effectmBinding.llUncle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                boolean ret = AgoraCallKit.getInstance().setLocalVoiceType(AgoraCallKit.VoiceType.OLDMAN);
                String result = (ret) ? "成功" : "失败";
                popupMessage("设置 大叔 音效" + result);
            }
        });

        effectmBinding.llGirl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                boolean ret = AgoraCallKit.getInstance().setLocalVoiceType(AgoraCallKit.VoiceType.BABYGIRL);
                String result = (ret) ? "成功" : "失败";
                popupMessage("设置 萝莉 音效" + result);
            }
        });

        effectmBinding.llBoy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                boolean ret = AgoraCallKit.getInstance().setLocalVoiceType(AgoraCallKit.VoiceType.BABYBOY);
                String result = (ret) ? "成功" : "失败";
                popupMessage("设置 少年 音效" + result);
            }
        });

    }

    /*
     * @brief 更新网络状态
     */
    void onMsgUpdateNetStatus()
    {
        AgoraCallKit.NetworkStatus networkStatus = AgoraCallKit.getInstance().getNetworkStatus();

        // 更新码率
        mBinding.tvBitrate.tvPrompt.setText("码率: ");
        //String bitrate = String.valueOf(networkStatus.txKBitRate + networkStatus.rxKBitRate);
        String bitrate = String.valueOf(networkStatus.txKBitRate);
        mBinding.tvBitrate.tvText.setText(bitrate + "Kbps");

        // 更新时延
        mBinding.tvDelay.tvPrompt.setText("时延: ");
        String delay = String.valueOf(networkStatus.lastmileDelay + "ms");
        mBinding.tvDelay.tvText.setText(delay);

        mMsgHandler.removeMessages(MSGID_UPDATE_NETSTATUS);
        mMsgHandler.sendEmptyMessageDelayed(MSGID_UPDATE_NETSTATUS, TIMER_UPDATE_NETSATUS);
    }

    void popupMessage(String message)
    {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }



    ////////////////////////////////////////////////////////////////////////////
    /////////////////// Override ICallKitCallback Methods //////////////////////
    ////////////////////////////////////////////////////////////////////////////
    @Override
    public void onLoginOtherDevice(CallKitAccount account) {
        Log.i(TAG, "<onLoginOtherDevice>");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                popupMessage("账号在其他设备登录,本地立即退出!");
                AgoraCallKit.getInstance().callHangup();  // 本地挂断处理
                finish();  // 退出当前界面，返回到已经登录界面
            }
        });
    }

    @Override
    public void onPeerAnswer(CallKitAccount account) {
        Log.d(TAG, "<onPeerAnswer> account=" + account.getName());

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnected = true;
                mMsgHandler.sendEmptyMessageDelayed(MSGID_UPDATE_NETSTATUS, TIMER_UPDATE_NETSATUS);

                //刷新呼叫状态
                AgoraCallKit.getInstance().setPeerVideoView(mPeerView);
            }
        });
    }

    @Override
    public void onPeerBusy(CallKitAccount account) {
        mConnected = false;
        Log.d(TAG, "<onPeerBusy> mConnected=" + mConnected);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // 立即退出频道
                //mApplication.getTalkEngine().leaveChannel();

                popupMessage("对方正在通话中，请稍后再拨！");
                finish();
            }
        });
    }

    @Override
    public void onPeerHangup(CallKitAccount account) {
        Log.d(TAG, "<onPeerHangup> mConnected=" + mConnected);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mConnected) {
                    popupMessage("对方已挂断！");
                } else {
                    popupMessage("您呼叫用户拒绝，请稍后再拨！");
                }
                finish();
            }
        });
    }

    @Override
    public void onPeerTimeout(CallKitAccount account) {
        mConnected = false;
        Log.d(TAG, "<onPeerTimeout> isAnswer=" + mConnected);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // 立即退出频道
                //mApplication.getTalkEngine().leaveChannel();

                popupMessage("您呼叫用户无人接听，请稍后再拨！");
                finish();
            }
        });
    }

    @Override
    public void onPeerCustomizeMessage(CallKitAccount account, String peerMessage) {
        Log.d(TAG, "<onPeerCustomizeMessage> peerMessage=" + peerMessage);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                popupMessage(peerMessage);
            }
        });
    }
}