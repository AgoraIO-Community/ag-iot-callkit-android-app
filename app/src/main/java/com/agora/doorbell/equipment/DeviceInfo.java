package com.agora.doorbell.equipment;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.agora.agoracallkit.callkit.AgoraCallKit;
import com.agora.agoracallkit.callkit.CallKitAccount;
import com.agora.doorbell.base.BaseFragment;
import com.agora.doorbell.databinding.FragmentEquipmentBinding;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class DeviceInfo {
    private final String TAG = "DoorBell/DeviceInfo";

    private String mDeviceId;               ///< 设备Id
    private boolean mIsOnline = false;      ///< 是否在线


    DeviceInfo(String devId, boolean online) {
        mDeviceId = devId;
        mIsOnline = online;
    }

    String getDeviceId() {
        return mDeviceId;
    }

    boolean isOnline() {
        return mIsOnline;
    }
}
