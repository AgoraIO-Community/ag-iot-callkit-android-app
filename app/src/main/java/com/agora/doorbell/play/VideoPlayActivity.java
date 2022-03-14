/**
 * @file VideoPlayActivity.java
 * @brief This file implement video list and video player
 *
 * @author xiaohua.lu
 * @email luxiaohua@agora.io
 * @version 1.0.0.1
 * @date 2021-11-17
 * @license Copyright (C) 2021 AgoraIO Inc. All rights reserved.
 */

package com.agora.doorbell.play;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.agora.agoracallkit.callkit.AgoraCallKit;
import com.agora.agoracallkit.callkit.CallKitAccount;
import com.agora.agoracallkit.callkit.ICallKitCallback;
import com.agora.doorbell.CalledActivity;
import com.agora.doorbell.LoginActivity;
import com.agora.doorbell.MainActivity;
import com.agora.doorbell.base.BaseActivity;
import com.agora.doorbell.databinding.ActivityVideoPlayBinding;
import com.agora.doorbell.equipment.EquipmentListAdapter;
import com.agora.doorbell.my.MyFragment;
import com.agora.doorbell.rtc.RtcActivity;
import com.shuyu.gsyvideoplayer.GSYVideoManager;
import com.shuyu.gsyvideoplayer.builder.GSYVideoOptionBuilder;
import com.shuyu.gsyvideoplayer.listener.GSYSampleCallBack;
import com.shuyu.gsyvideoplayer.listener.LockClickListener;
import com.shuyu.gsyvideoplayer.utils.Debuger;
import com.shuyu.gsyvideoplayer.utils.OrientationUtils;

import java.util.ArrayList;
import java.util.List;

public class VideoPlayActivity extends BaseActivity implements ICallKitCallback,
        VideoListAdapter.IAdapterCallback {
    private final String TAG = "DoorBell/VPActivity";
    public static String DEVICE_ID_KEY = "DEVICE_ID";


    ///////////////////////////////////////////////////////////////////////////
    ///////////////////// Variable Definition /////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    private VideoPlayActivity mActivity;
    private ActivityVideoPlayBinding mBinding;
    private OrientationUtils orientationUtils;
    private String mDeviceId;
    private GSYVideoOptionBuilder mGsyVideoOption;
    private boolean isPause;
    private boolean isPlay;


    ////////////////////////////////////////////////////////////////////////////
    ///////////////////////// Override Activity Methods ////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mActivity = this;
        mBinding = ActivityVideoPlayBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());

        mDeviceId = getIntent().getExtras().getString(DEVICE_ID_KEY);

        // 查询告警记录列表
        List<AgoraCallKit.AlarmRecord> recordList = AgoraCallKit.getInstance().queryAlarmByDeviceId(mDeviceId);

        // 设置告警信息列表
        mBinding.rvVideoList.setAdapter(new VideoListAdapter(recordList, this));
        mBinding.rvVideoList.setLayoutManager(new LinearLayoutManager(this));

        mBinding.btRtc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onBtnTalking();
            }
        });

        initPlayer();
    }

    private void initPlayer() {
        //外部辅助的旋转，帮助全屏
        orientationUtils = new OrientationUtils(this, mBinding.player);
        //初始化不打开外部的旋转
        orientationUtils.setEnable(false);

//        mGsyVideoOption= new GSYVideoOptionBuilder();
//        mGsyVideoOption.setThumbImageView(null)
//                .setIsTouchWiget(true)
//                .setRotateViewAuto(false)
//                .setLockLand(false)
//                .setAutoFullWithSize(true)
//                .setShowFullAnimation(false)
//                .setNeedLockFull(true)
//                //.setUrl("https://media.w3.org/2010/05/sintel/trailer.mp4")
//                .setUrl("http://agora-iot-oss-test.oss-cn-shanghai.aliyuncs.com/18e12695164239fc4655aa84d58ec9ba_96_1.m3u8")
//                .setCacheWithPlay(false)
//                .setVideoTitle("测试视频")
//                .setVideoAllCallBack(new GSYSampleCallBack() {
//                    @Override
//                    public void onPrepared(String url, Object... objects) {
//                        super.onPrepared(url, objects);
//                        //开始播放了才能旋转和全屏
//                        orientationUtils.setEnable(true);
////                        isPlay = true;
//                    }
//
//                    @Override
//                    public void onQuitFullscreen(String url, Object... objects) {
//                        super.onQuitFullscreen(url, objects);
//                        Debuger.printfError("***** onQuitFullscreen **** " + objects[0]);//title
//                        Debuger.printfError("***** onQuitFullscreen **** " + objects[1]);//当前非全屏player
//                        if (orientationUtils != null) {
//                            orientationUtils.backToProtVideo();
//                        }
//                    }
//                }).setLockClickListener(new LockClickListener() {
//            @Override
//            public void onClick(View view, boolean lock) {
//                if (orientationUtils != null) {
//                    //配合下方的onConfigurationChanged
//                    orientationUtils.setEnable(!lock);
//                }
//            }
//        }).build(mBinding.player);

        mBinding.player.getFullscreenButton().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //直接横屏
                orientationUtils.resolveByClick();

                //第一个true是否需要隐藏actionbar，第二个true是否需要隐藏statusbar
                mBinding.player.startWindowFullscreen(VideoPlayActivity.this, true, true);
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (orientationUtils != null) {
            orientationUtils.backToProtVideo();
        }
        if (GSYVideoManager.backFromWindowFull(this)) {
            return;
        }
        super.onBackPressed();
    }


    @Override
    protected void onPause() {
        mBinding.player.getCurrentPlayer().onVideoPause();
        super.onPause();
        isPause = true;
    }

    @Override
    protected void onResume() {
        mBinding.player.getCurrentPlayer().onVideoResume(false);
        super.onResume();
        isPause = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isPlay) {
            mBinding.player.getCurrentPlayer().release();
        }
        if (orientationUtils != null)
            orientationUtils.releaseListener();
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

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        //如果旋转了就全屏
        if (isPlay && !isPause) {
            mBinding.player.onConfigurationChanged(this, newConfig, orientationUtils, true, true);
        }
    }

    /*
     * @brief 进入实时通话
     */
    void onBtnTalking() {
//        String tips = "正在联接设备: " + mDeviceId + " .....";
//        mPgDigLogShow(tips);
//
//        CallKitAccount dialAccount = new CallKitAccount(mDeviceId, CallKitAccount.ACCOUNT_TYPE_DEV);
//        List<CallKitAccount> dialList = new ArrayList<>();
//        dialList.add(dialAccount);
//        String attachMsg = "I'm doorbell app";
//        int ret = AgoraCallKit.getInstance().callDial(dialList, attachMsg);
//        if (ret != AgoraCallKit.ERR_NONE) {
//            mPgDigLogHide();
//            mPopupMessage("不能呼叫, 错误码: " + ret);
//            return;
//        }

        // 直接跳转到通话界面，在通话界面进行呼叫
        Intent intent = new Intent(VideoPlayActivity.this, RtcActivity.class);
        intent.putExtra(RtcActivity.DEVICE_ID_KEY, mDeviceId);
        startActivity(intent);
    }

    ////////////////////////////////////////////////////////////////////////////
    /////////////////// Override ICallKitCallback Methods //////////////////////
    ////////////////////////////////////////////////////////////////////////////
    @Override
    public void onLoginOtherDevice(CallKitAccount account) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mPopupMessage("账号在其他设备登录,本地立即退出!");

                // 直接退出当前界面，返回到登录界面，清空Activity堆栈
                new android.os.Handler(Looper.getMainLooper()).postDelayed(
                        new Runnable() {
                            public void run() {
                                Intent intent = new Intent(VideoPlayActivity.this, LoginActivity.class);
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
    public void onPeerIncoming(CallKitAccount account, CallKitAccount peer_account, String attachMsg) {
        Log.d(TAG, "<onPeerIncoming>");

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Intent activityIntent = new Intent(VideoPlayActivity.this, CalledActivity.class);
                activityIntent.putExtra("caller_name", peer_account.getName());
                activityIntent.putExtra("attach_msg", attachMsg);
                startActivity(activityIntent);
            }
        });
    }

    @Override
    public void onAlarmReceived(CallKitAccount account, CallKitAccount peer_account,
                                long timestamp, String alarmMsg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mPopupMessage("接收到来自: " + peer_account.getName() + " 的告警消息: " + alarmMsg);

                if (mDeviceId.equals(peer_account.getName())) {
                    // 刷新当前设备告警列表
                    List<AgoraCallKit.AlarmRecord> recordList = AgoraCallKit.getInstance().queryAlarmByDeviceId(mDeviceId);
                    mBinding.rvVideoList.setAdapter(new VideoListAdapter(recordList, mActivity));
                    mBinding.rvVideoList.invalidate();
                }
            }
        });
    }

    ////////////////////////////////////////////////////////////////////////////
    /////////////////// Override IAdapterCallback Methods //////////////////////
    ////////////////////////////////////////////////////////////////////////////
    @Override
    public void onItemClicked(int position, AgoraCallKit.AlarmRecord item) {

        mGsyVideoOption = new GSYVideoOptionBuilder();
        mGsyVideoOption.setThumbImageView(null)
                .setIsTouchWiget(true)
                .setRotateViewAuto(false)
                .setLockLand(false)
                .setAutoFullWithSize(true)
                .setShowFullAnimation(false)
                .setNeedLockFull(true)
                .setUrl(item.mVideoUrl)
                .setCacheWithPlay(false)
                .setVideoTitle(item.mMessage)
                .setVideoAllCallBack(new GSYSampleCallBack() {
                    @Override
                    public void onPrepared(String url, Object... objects) {
                        super.onPrepared(url, objects);
                        //开始播放了才能旋转和全屏
                        orientationUtils.setEnable(true);
//                        isPlay = true;
                    }

                    @Override
                    public void onQuitFullscreen(String url, Object... objects) {
                        super.onQuitFullscreen(url, objects);
                        Debuger.printfError("***** onQuitFullscreen **** " + objects[0]);//title
                        Debuger.printfError("***** onQuitFullscreen **** " + objects[1]);//当前非全屏player
                        if (orientationUtils != null) {
                            orientationUtils.backToProtVideo();
                        }
                    }
                }).setLockClickListener(new LockClickListener() {
            @Override
            public void onClick(View view, boolean lock) {
                if (orientationUtils != null) {
                    //配合下方的onConfigurationChanged
                    orientationUtils.setEnable(!lock);
                }
            }
        }).build(mBinding.player);
    }
}

