/**
 * @file CalledActivity.java
 * @brief This file implement user interface of a dialing
 * For application role, it will pull and render the remote audio and video
 * For IoT device role, it will publish video and audio to channel
 * @author xiaohua.lu
 * @email luxiaohua@agora.io
 * @version 1.0.0.1
 * @date 2021-10-13
 * @license Copyright (C) 2021 AgoraIO Inc. All rights reserved.
 */

package com.agora.doorbell;

import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.agora.agoracallkit.callkit.AgoraCallKit;
import com.agora.agoracallkit.callkit.CallKitAccount;
import com.agora.agoracallkit.callkit.ICallKitCallback;
import com.agora.doorbell.databinding.ActivityCalledBinding;
import com.agora.doorbell.rtc.RtcActivity;

public class CalledActivity extends AppCompatActivity implements ICallKitCallback {
    private final String TAG = "DoorBell/CalledActivity";

    private ActivityCalledBinding binding;    ///< 自动生成的view绑定类


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //创建view绑定类的实例，使其成为屏幕上的活动视图
        binding = ActivityCalledBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        //view初始化
        initView();
    }

    @Override
    protected void onStart() {
        super.onStart();
        //注册呼叫监听
        AgoraCallKit.getInstance().registerListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        //页面恢复时候检查是否在呼叫状态
        int state = AgoraCallKit.getInstance().getState();

        if ((AgoraCallKit.STATE_CALL_TALKING != state) &&
                (AgoraCallKit.STATE_CALL_DIALING != state) &&
                (AgoraCallKit.STATE_CALL_INCOMING != state)) {
            popupMessage("呼叫已经失效!");
            finish();
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
    }

    // view初始化
    private void initView() {
        // 获取登录页面传递过来的UID信息
        String caller = this.getIntent().getStringExtra("caller_name");
        String attachMsg = this.getIntent().getStringExtra("attach_msg");
        Log.i("activity", "caller name is " + caller);

        // 显示呼叫者信息
        binding.callerName.setText(caller);

        // 显示附带消息
        binding.tvAttachMsg.setText("附带消息: " + attachMsg);

        // 接听按钮
        binding.btnAnswer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //选择接听
                Log.i(TAG, "Answer the call");
                AgoraCallKit.getInstance().callAnswer();

                // 跳转到通话页面
                Intent activityIntent = new Intent(CalledActivity.this, RtcActivity.class);
                activityIntent.putExtra("caller_name", caller);
                activityIntent.putExtra("call_state", "通话中...");
                activityIntent.putExtra("answer", true);
                startActivity(activityIntent);
                finish();
            }
        });

        // 拒绝按钮
        binding.btnRefuse.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 选择拒绝
                Log.i("activity", "Refuse the call");
                AgoraCallKit.getInstance().callHangup();
                finish();
            }
        });
    }

    void popupMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }


    ////////////////////////////////////////////////////////////////////////////
    /////////////////// Override ICallKitCallback Methods //////////////////////
    ////////////////////////////////////////////////////////////////////////////
    @Override
    public void onLoginOtherDevice(CallKitAccount account) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String tips = "账号在其他设备登录,本地立即退出!";
                //Toast.makeText(this, tips, Toast.LENGTH_SHORT).show();

                // 直接退出当前界面，返回到登录界面，清空Activity堆栈
                new android.os.Handler(Looper.getMainLooper()).postDelayed(
                        new Runnable() {
                            public void run() {
                                Intent intent = new Intent(CalledActivity.this, LoginActivity.class);
                                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(intent);
                                finish();
                            }
                        },
                        3000);
            }
        });
    }

    @Override
    public void onPeerBusy(CallKitAccount account) {
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
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                popupMessage("您呼叫用户拒绝，请稍后再拨！");
                finish();
            }
        });
    }

    @Override
    public void onPeerTimeout(CallKitAccount account) {
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


}