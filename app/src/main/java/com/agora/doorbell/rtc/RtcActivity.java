package com.agora.doorbell.rtc;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.InputFilter;
import android.text.format.Time;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.agora.agoracallkit.callkit.AgoraCallKit;
import com.agora.agoracallkit.callkit.CallKitAccount;
import com.agora.agoracallkit.callkit.ICallKitCallback;
import com.agora.doorbell.LoginActivity;
import com.agora.doorbell.R;
import com.agora.doorbell.base.BaseActivity;
import com.agora.doorbell.databinding.ActivityRtcBinding;
import com.agora.doorbell.play.VideoPlayActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RtcActivity extends BaseActivity implements ICallKitCallback {
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////// Constant Definition /////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    private final String TAG = "DoorBell/RtcActivity";
    public static String DEVICE_ID_KEY = "DEVICE_ID";
    public static int TIMER_UPDATE_NETSATUS = 2000;     ///< 网络状态定时2秒刷新一次


    //
    // message Id
    //
    public static final int MSGID_DIAL = 0x1001;    ///< 开始呼叫设备
    public static final int MSGID_UPDATE_NETSTATUS = 0x1002;    ///< 更新网络状态



    ///////////////////////////////////////////////////////////////////////////
    ///////////////////// Variable Definition /////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    private ActivityRtcBinding mBinding;
    private boolean IS_ORIENTATION_LANDSCAPE;
    private volatile boolean mVoiceTalking = false; ///< 是否正在发送语音
    private static Handler mMsgHandler = null;      ///< 主线程中的消息处理
    private boolean mPeerMuted = false;             ///< 对端是否已经静音
    private boolean mConnected = false;             ///< 是否已经跟对端建立联接

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////// Override Acitivyt Methods ////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        Log.d(TAG, "<onCreate>");
        super.onCreate(savedInstanceState);
        mBinding = ActivityRtcBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());
        getSupportActionBar().setTitle("实时画面");
        mVoiceTalking = false;

        // 如果是来电则默认建立联接
        mConnected = this.getIntent().getBooleanExtra("answer", false);;

        IS_ORIENTATION_LANDSCAPE = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;

        if (IS_ORIENTATION_LANDSCAPE) {
            getSupportActionBar().hide();
            View decorView = getWindow().getDecorView();
            int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN;
            decorView.setSystemUiVisibility(uiOptions);
        } else {
            getSupportActionBar().show();
        }

        if (mBinding.ivBack != null) {
            mBinding.ivBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                }
            });
        }

         if (mBinding.ivFullScreen != null) {
            mBinding.ivFullScreen.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (IS_ORIENTATION_LANDSCAPE) {
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                    } else {
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                    }
                }
            });
        }

        if (mBinding.ivScreenCut != null) {
            mBinding.ivScreenCut.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    onBtnVideoShot();
                }
            });
        }

        if (mBinding.ivQuickReply != null) {
            mBinding.ivQuickReply.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    onBtnQuickReply();
                }
            });
        }

        if (mBinding.ivVoiceCall != null) {
            mBinding.ivVoiceCall.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    onBtnVoiceTalk();
                }
            });
        }

        if (mBinding.ivMute != null) {
            mBinding.ivMute.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    onBtnVoiceMute();
                }
            });
        }

        if (mBinding.btnVoiceOldman != null) {
            mBinding.btnVoiceOldman.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    onBtnVoiceOldman();
                }
            });
        }

        if (mBinding.btnVoiceBoy != null) {
            mBinding.btnVoiceBoy.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    onBtnVoiceBoy();
                }
            });
        }

        if (mBinding.btnVoiceGirl != null) {
            mBinding.btnVoiceGirl.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    onBtnVoiceGirl();
                }
            });
        }

        if (mBinding.btnVoiceZbj != null) {
            mBinding.btnVoiceZbj.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    onBtnVoiceZhubajie();
                }
            });
        }

        if (mBinding.btnVoiceEthereal != null) {
            mBinding.btnVoiceEthereal.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    onBtnVoiceEthereal();
                }
            });
        }

        if (mBinding.btnVoiceHulk != null) {
            mBinding.btnVoiceHulk.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    onBtnVoiceHulk();
                }
            });
        }

        if (mBinding.btnVoiceNormal != null) {
            mBinding.btnVoiceNormal.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    onBtnVoiceNormal();
                }
            });
        }



        // 设置视频显示View控件
        AgoraCallKit.getInstance().setPeerVideoView(mBinding.svPeer);

        mMsgHandler = new Handler(this.getMainLooper())
        {
            @SuppressLint("HandlerLeak")
            public void handleMessage(Message msg)
            {
                switch (msg.what)
                {
                    case MSGID_DIAL:
                        onMsgDial();
                        break;
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

        if (!mConnected) {
            mMsgHandler.sendEmptyMessage(MSGID_DIAL);
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
        Log.d(TAG, "<onDestroy>");
        super.onDestroy();

        if (mMsgHandler != null) {  // remove all messages
            mMsgHandler.removeMessages(MSGID_UPDATE_NETSTATUS);
            mMsgHandler = null;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {

            if (IS_ORIENTATION_LANDSCAPE) { // 退回到 portrait竖屏显示
                Log.d(TAG, "<onKeyDown> BACK Key return portrait mode");
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

            } else {
                Log.d(TAG, "<onKeyDown> BACK Key exit talking");
                AgoraCallKit.getInstance().callHangup();  // 本地挂断处理
            }
        }
        return super.onKeyDown(keyCode, event);
    }



    ////////////////////////////////////////////////////////////////////////////
    /////////////////// Implement all Buttons Events ///////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    void onBtnVideoShot() {
        if (!mConnected) {
            return;
        }

        Bitmap videoFrameBmp = AgoraCallKit.getInstance().capturePeerVideoFrame();
        if (videoFrameBmp == null) {
            mPopupMessage("截图失败!");
            return;
        }

        String strFilePath = getShotFilePath();
        if (strFilePath == null) {
            mPopupMessage("创建截图保存目录失败!");
            return;
        }

        if (!saveBmpToFile(videoFrameBmp, strFilePath)) {
            mPopupMessage("截图保存失败!");
            return;
        }

        // 刷新图库
        Uri fileUri = Uri.fromFile(new File(strFilePath));
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, fileUri);
        this.sendBroadcast(mediaScanIntent);

        String tips = "截图保存到： " + strFilePath;
        mPopupMessage(tips);
    }

    void onBtnQuickReply()
    {
        if (!mConnected) {
            return;
        }

        final EditText inputEditText = new EditText(this);
        inputEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(50)});
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("输入要发送的消息: ")
                .setIcon(android.R.drawable.ic_menu_add)
                .setView(inputEditText)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        String inputMessage = inputEditText.getText().toString();
                        if (inputMessage.isEmpty()) {
                            return;
                        }

                        // 发送消息处理
                        AgoraCallKit.getInstance().sendCustomizeMessage(inputMessage);
                    }});
        builder.show();
    }

    void onBtnVoiceTalk()
    {
        if (!mConnected) {
            return;
        }

        if (mVoiceTalking) {
            // 结束语音发送处理
            AgoraCallKit.getInstance().mutePeerAudioStream(false);
            AgoraCallKit.getInstance().muteLocalAudioStream(true);
            if (mBinding.tvVoiceCall != null) {
                mBinding.tvVoiceCall.setText("语音通话");
            }
            mVoiceTalking = false;

        } else {
            // 开始语音发送处理
            AgoraCallKit.getInstance().mutePeerAudioStream(true);
            AgoraCallKit.getInstance().muteLocalAudioStream(false);
            if (mBinding.tvVoiceCall != null) {
                mBinding.tvVoiceCall.setText("结束通话");
            }
            mVoiceTalking = true;
        }
    }

    void onBtnVoiceMute()
    {
        if (!mConnected) {
            return;
        }

        if (mVoiceTalking) {
            mPopupMessage("通话时门铃端已经强制静音");
            return;
        }

        if (mPeerMuted) {
            mPeerMuted = false;
            AgoraCallKit.getInstance().mutePeerAudioStream(false);
            mPopupMessage("门铃端取消静音");

        } else {
            mPeerMuted = true;
            AgoraCallKit.getInstance().mutePeerAudioStream(true);
            mPopupMessage("门铃端静音");
        }
    }

    void onBtnVoiceOldman() {
        boolean ret = AgoraCallKit.getInstance().setLocalVoiceType(AgoraCallKit.VoiceType.OLDMAN);
        if (ret) {
            mPopupMessage("设置音效成功：oldman");
        } else {
            mPopupMessage("设置音效失败：oldman");
        }
    }

    void onBtnVoiceBoy() {
        boolean ret = AgoraCallKit.getInstance().setLocalVoiceType(AgoraCallKit.VoiceType.BABYBOY);
        if (ret) {
            mPopupMessage("设置音效成功：boy");
        } else {
            mPopupMessage("设置音效失败：boy");
        }
    }

    void onBtnVoiceGirl() {
        boolean ret = AgoraCallKit.getInstance().setLocalVoiceType(AgoraCallKit.VoiceType.BABYGIRL);
        if (ret) {
            mPopupMessage("设置音效成功：girl");
        } else {
            mPopupMessage("设置音效失败：girl");
        }
    }

    void onBtnVoiceZhubajie() {
        boolean ret = AgoraCallKit.getInstance().setLocalVoiceType(AgoraCallKit.VoiceType.ZHUBAJIE);
        if (ret) {
            mPopupMessage("设置音效成功：ZhuBaJie");
        } else {
            mPopupMessage("设置音效失败：ZhuBaJie");
        }
    }

    void onBtnVoiceEthereal() {
        boolean ret = AgoraCallKit.getInstance().setLocalVoiceType(AgoraCallKit.VoiceType.ETHEREAL);
        if (ret) {
            mPopupMessage("设置音效成功：Ethereal");
        } else {
            mPopupMessage("设置音效失败：Ethereal");
        }
    }

    void onBtnVoiceHulk() {
        boolean ret = AgoraCallKit.getInstance().setLocalVoiceType(AgoraCallKit.VoiceType.HULK);
        if (ret) {
            mPopupMessage("设置音效成功：Hulk");
        } else {
            mPopupMessage("设置音效失败：Hulk");
        }
    }


    void onBtnVoiceNormal() {
        boolean ret = AgoraCallKit.getInstance().setLocalVoiceType(AgoraCallKit.VoiceType.NORMAL);
        if (ret) {
            mPopupMessage("设置原声音效成功");
        } else {
            mPopupMessage("设置原声音效失败");
        }
    }


    /*
     * @brief 呼叫对端设备
     */
    void onMsgDial() {
        if (mConnected) {
            return;
        }

        String deviceID = getIntent().getExtras().getString(DEVICE_ID_KEY);
        CallKitAccount dialAccount = new CallKitAccount(deviceID, CallKitAccount.ACCOUNT_TYPE_DEV);
        List<CallKitAccount> dialList = new ArrayList<>();
        dialList.add(dialAccount);
        int ret = AgoraCallKit.getInstance().callDial(dialList, "I'm DoorBell demo");
        if (ret != AgoraCallKit.ERR_NONE) {
            mPgDigLogHide();
            mPopupMessage("不能呼叫, 错误码: " + ret);
            return;
        }
    }


    /*
     * @brief 更新网络状态
     */
    void onMsgUpdateNetStatus()
    {
        AgoraCallKit.NetworkStatus networkStatus = AgoraCallKit.getInstance().getNetworkStatus();
        String status1 = String.format(Locale.getDefault(),"Lastmile Delay: %d ms", networkStatus.lastmileDelay);
        String status2 = String.format(Locale.getDefault(),"Video Send/Recv: %d kbps / %d kbps",
                networkStatus.txVideoKBitRate, networkStatus.rxVideoKBitRate);
        String status3 = String.format(Locale.getDefault(),"Audio Send/Recv: %d kbps / %d kbps",
                networkStatus.txAudioKBitRate, networkStatus.rxAudioKBitRate);

        String status = status1 + "\n" + status2 + "\n" + status3;
        mBinding.tvMs.setText(status);
        mMsgHandler.removeMessages(MSGID_UPDATE_NETSTATUS);
        mMsgHandler.sendEmptyMessageDelayed(MSGID_UPDATE_NETSTATUS, TIMER_UPDATE_NETSATUS);
    }

    String getShotFilePath() {
        // 创建保存的目录
        String deviceID = getIntent().getExtras().getString(DEVICE_ID_KEY);
        String strSdPath = Environment.getExternalStorageDirectory().getAbsolutePath();
        String strShotPath = strSdPath + "/DCIM/" + deviceID;
        try {
            File file = new File(strShotPath);
            if (!file.exists()) {
                boolean bCrated = file.mkdir();
                if (!bCrated) {
                    Log.e(TAG, "<getShotFilePath> failure create folder=" + strShotPath);
                    return null;
                }
            }
        } catch (Exception except) {
            except.printStackTrace();
            return null;
        }

        Time time = new Time("GMT+8");
        time.setToNow();
        int year = time.year;
        int month = time.month;
        int day = time.monthDay;
        int hour = time.hour;
        int minute = time.minute;
        int sec = time.second;
        String strFilePath = String.format(Locale.getDefault(), "%s/%d%d%d_%d%d%d.jpg",
                strShotPath, year, month, day, hour, minute, sec);
        return strFilePath;
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
                                Intent intent = new Intent(RtcActivity.this, LoginActivity.class);
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
    public void onDialDone(CallKitAccount account, List<CallKitAccount> dialAccountList, int errCode) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                CallKitAccount dialAccount = dialAccountList.get(0);
                if (errCode != AgoraCallKit.ERR_NONE) {
                    mPopupMessage("呼叫: " + dialAccount.getName() + " 错误");
                    finish();
                } else {
                    //mPopupMessage("呼叫: " + dialAccount.getName() + " 成功");
                }
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
                mMsgHandler.sendEmptyMessage(MSGID_UPDATE_NETSTATUS);
                AgoraCallKit.getInstance().setPeerVideoView(mBinding.svPeer);
            }
        });
    }

    @Override
    public void onPeerBusy(CallKitAccount account) {
        Log.d(TAG, "<onPeerBusy> account=" + account.getName());

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mPopupMessage("门铃设备忙音，请稍后再拨！");
                finish();
            }
        });
    }

    @Override
    public void onPeerTimeout(CallKitAccount account) {
        Log.d(TAG, "<onPeerHangup> account=" + account.getName());

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mPopupMessage("门铃设备无人接听，请稍后再拨！");
                finish();
            }
        });
    }

    @Override
    public void onPeerHangup(CallKitAccount account) {
        Log.d(TAG, "<onPeerHangup>");

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mPopupMessage("对方已挂断！");
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
                mPopupMessage("收到对端消息: " + peerMessage);
            }
        });
    }
}
