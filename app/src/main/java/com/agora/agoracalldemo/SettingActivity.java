/**
 * @file SettingActivity.java
 * @brief This file implement some config setting
 * @author xiaohua.lu
 * @email luxiaohua@agora.io
 * @version 1.0.0.1
 * @date 2021-11-24
 * @license Copyright (C) 2021 AgoraIO Inc. All rights reserved.
 */
package com.agora.agoracalldemo;


import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.agora.agoracallkit.callkit.AgoraCallKit;
import com.hyphenate.easeim.databinding.ActivitySettingBinding;

import java.util.ArrayList;
import java.util.Locale;


public class SettingActivity extends AppCompatActivity  {
    private final String TAG = "DEMO/SettingActivity";

    //
    // Codec Type Value
    //
    private final int CODEC_TYPE_PCMU = 0;         // 8000, 160, 1, 64000
    private final int CODEC_TYPE_PCMA = 8;         // 8000, 160, 1, 64000
    private final int CODEC_TYPE_G722 = 9;         // 16000, 320, 1, 64000
    private final int CODEC_TYPE_AACLC1_2CH = 70;  // 48000, 960, 2, 192000
    private final int CODEC_TYPE_AACLC1 = 71;      // 44100, 960, 1, 96000
    private final int CODEC_TYPE_HEAAC2 = 72;      // 48000, 1920, 1, 48000
    private final int CODEC_TYPE_HEAAC2_2CH = 73;  // 48000, 1920, 2, 64000
    private final int CODEC_TYPE_AACLC_2CH = 74;   // 48000, 960, 2, 192000
    private final int CODEC_TYPE_AACLC = 75;       // 48000, 960, 1, 96000
    private final int CODEC_TYPE_HWAAC = 77;       // 32000, 960, 1, 32000
    private final int CODEC_TYPE_HEAAC_2CH = 78;   // 48000, 1920, 2, 32000
    private final int CODEC_TYPE_HEAAC = 79;       // 32000, 1920, 1, 24000
    private final int CODEC_TYPE_OPUS = 120;       // 16000, 320, 1, 16000
    private final int CODEC_TYPE_OPUSSWB = 121;    // 32000, 640, 1, 25000
    private final int CODEC_TYPE_OPUSFB = 122;     // 48000, 960, 1, 64000

    private final int ACODEC_VAL_List[] = {
        CODEC_TYPE_PCMU,
        CODEC_TYPE_PCMA,
        CODEC_TYPE_G722,
        CODEC_TYPE_AACLC1_2CH,
        CODEC_TYPE_AACLC1,
        CODEC_TYPE_HEAAC2,
        CODEC_TYPE_HEAAC2_2CH,
        CODEC_TYPE_AACLC_2CH,
        CODEC_TYPE_AACLC,
        CODEC_TYPE_HWAAC,
        CODEC_TYPE_HEAAC_2CH,
        CODEC_TYPE_HEAAC,
        CODEC_TYPE_OPUS,
        CODEC_TYPE_OPUSSWB,
        CODEC_TYPE_OPUSFB
    };

    private final String ACODEC_NAME_List[] = {
        "PCMU",
        "PCMA",
        "G722",
        "AACLC1_2CH",
        "AACLC1",
        "HEAAC2",
        "HEAAC2_2CH",
        "AACLC_2CH",
        "AACLC",
        "HWAAC",
        "HEAAC_2CH",
        "HEAAC",
        "OPUS",
        "OPUSSWB",
        "OPUSFB",
    };


    private com.agora.agoracalldemo.PushApplication mApplication;
    private ActivitySettingBinding mBinding;         ///< 自动生成的view绑定类

    private ArrayList mAudioCodecList = new ArrayList<String>();



    ///////////////////////////////////////////////////////////////////////////
    ///////////////////// Override Activity Methods ////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "<onCreate>");
        super.onCreate(savedInstanceState);
        mApplication = (com.agora.agoracalldemo.PushApplication) getApplication();

        // 创建view绑定类的实例，使其成为屏幕上的活动视图
        mBinding = ActivitySettingBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());

        // 确认按钮
        mBinding.btnConfirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });


        //
        // AudioCodec列表
        //
        for (int i = 0; i < ACODEC_NAME_List.length; i++) {
            mAudioCodecList.add(ACODEC_NAME_List[i]);
        }
        ArrayAdapter audioCodecAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, mAudioCodecList);
        audioCodecAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mBinding.spinnerAudioCodec.setAdapter(audioCodecAdapter);
        int audioCodecIndex = mApplication.getAudioCodecIndex();
        mBinding.spinnerAudioCodec.setSelection(audioCodecIndex);

        mBinding.spinnerAudioCodec.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int currCodecIndex = mApplication.getAudioCodecIndex();
                if (currCodecIndex == position) {
                    return;
                }

                String privParam = String.format(Locale.getDefault(),
                "{\"rtc.audio.custom_payload_type\":%d}", ACODEC_VAL_List[position]);

                int ret = AgoraCallKit.getInstance().setTalkPrivateParam(privParam);
                if (ret == AgoraCallKit.ERR_NONE) {
                    mApplication.setAudioCodecIndex(position);
                    popupMessage("设置音频格式成功, privParam=" + privParam);
                } else {
                    popupMessage("设置音频格式失败, privParam=" + privParam);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });


        // 私参设置参数按钮
        mBinding.btnSetPrivParam.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String privParam = mBinding.etPrivParam.getText().toString();
                if (privParam.isEmpty()) {
                    return;
                }
                int ret = AgoraCallKit.getInstance().setTalkPrivateParam(privParam);
                if (ret == AgoraCallKit.ERR_NONE) {
                    popupMessage("设置私参成功!");
                } else {
                    popupMessage("设置私参失败!");
                }
            }
        });


    }

    @Override
    protected void onStart() {
        Log.d(TAG, "<onStart>");
        super.onStart();
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "<onResume>");
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
    }

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////// Messages or Button Events ////////////////////////////
    ////////////////////////////////////////////////////////////////////////////

    void popupMessage(String message)
    {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }



}