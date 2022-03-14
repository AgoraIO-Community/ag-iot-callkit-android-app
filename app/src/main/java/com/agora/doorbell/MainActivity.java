/**
 * @file MainActivity.java
 * @brief This file implement main UI after login the user account
 *
 * @author xiaohua.lu
 * @email luxiaohua@agora.io
 * @version 1.0.0.1
 * @date 2021-11-16
 * @license Copyright (C) 2021 AgoraIO Inc. All rights reserved.
 */
package com.agora.doorbell;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import androidx.annotation.NonNull;
import com.agora.agoracallkit.callkit.AgoraCallKit;
import com.agora.agoracallkit.callkit.CallKitAccount;
import com.agora.agoracallkit.callkit.ICallKitCallback;
import com.agora.doorbell.base.BaseActivity;
import com.agora.doorbell.databinding.ActivityMainBinding;
import com.agora.doorbell.equipment.EquipmentFragment;
import com.agora.doorbell.msg.MsgFragment;
import com.agora.doorbell.my.MyFragment;
import com.agora.doorbell.rtc.RtcActivity;
import com.google.android.material.navigation.NavigationBarView;
import java.util.ArrayList;
import java.util.List;



public class MainActivity extends BaseActivity implements ICallKitCallback {

    private final String TAG = "DoorBell/MainActivity";



    ///////////////////////////////////////////////////////////////////////////
    ///////////////////// Variable Definition /////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    private ActivityMainBinding mBinding;



    ////////////////////////////////////////////////////////////////////////////
    ///////////////////////// Override Activity Methods ////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "<onCreate>");
        super.onCreate(savedInstanceState);

        mBinding = ActivityMainBinding.inflate(getLayoutInflater());
        View view = mBinding.getRoot();
        setContentView(view);

        EquipmentFragment equipmentFragment = new EquipmentFragment();
        MsgFragment msgFragment = new MsgFragment();
        MyFragment myFragment = new MyFragment();
        getSupportFragmentManager().beginTransaction().replace(R.id.fl_main, equipmentFragment).commit();

        mBinding.bnvMain.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                int itemId = item.getItemId();
                if (itemId == R.id.equipment) {
                    getSupportFragmentManager().beginTransaction().replace(R.id.fl_main, equipmentFragment).commit();
                } else if (itemId == R.id.msg) {
                    getSupportFragmentManager().beginTransaction().replace(R.id.fl_main, msgFragment).commit();
                } else if (itemId == R.id.my) {
                    getSupportFragmentManager().beginTransaction().replace(R.id.fl_main, myFragment).commit();
                }
                return false;
            }
        });


    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "<onDestroy>");
        super.onDestroy();
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


    //////////////////////////////////////////////////////////////////////////////////
    /////////////////// Implement all Buttons Events & Messages //////////////////////
    //////////////////////////////////////////////////////////////////////////////////



    ////////////////////////////////////////////////////////////////////////////
    /////////////////// Override ICallKitCallback Methods //////////////////////
    ////////////////////////////////////////////////////////////////////////////
    @Override
    public void onPeerIncoming(CallKitAccount account, CallKitAccount peer_account, String attachMsg) {
        Log.d(TAG, "<onPeerIncoming>");

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Intent activityIntent = new Intent(MainActivity.this, CalledActivity.class);
                activityIntent.putExtra("caller_name", peer_account.getName());
                activityIntent.putExtra("attach_msg", attachMsg);
                startActivity(activityIntent);
            }
        });
    }

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
                                Intent intent = new Intent(MainActivity.this, LoginActivity.class);
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



}