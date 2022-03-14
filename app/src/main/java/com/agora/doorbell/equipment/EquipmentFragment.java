/**
 * @file EquipmentFragment.java
 * @brief This file implement fragment management for two sub-fragments
 *        Each sub-fragment used for dsiplay device list
 *
 * @author xiaohua.lu
 * @email luxiaohua@agora.io
 * @version 1.0.0.1
 * @date 2021-11-16
 * @license Copyright (C) 2021 AgoraIO Inc. All rights reserved.
 */

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



public class EquipmentFragment extends BaseFragment {
    private final String TAG = "DoorBell/EquipFragment";
    private final String[] mTabTitle = new String[] {"门铃", "门锁"};

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////// Variable Definition /////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    private FragmentEquipmentBinding mBinding;
    private HashMap<String, Fragment> mFragmentMap;
    private int mFocusedPosition = 0;





    ////////////////////////////////////////////////////////////////////////////
    ////////////////////////////// Public Methods /////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    public EquipmentFragment() {
        // 创建不同类型的设备列表fragment
        mFragmentMap = new HashMap<>();
        mFragmentMap.put("0", new EquipmentListFragment());
        mFragmentMap.put("1", new EquipmentListFragment());
    }


    ////////////////////////////////////////////////////////////////////////////
    ///////////////////////// Override Fragment Methods ////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mBinding = FragmentEquipmentBinding.inflate(inflater, container, false);
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mBinding.vpPager.setAdapter(new EquipmentAdapter(this));
        mBinding.vpPager.setSaveEnabled(false);
        new TabLayoutMediator(mBinding.tabHead, mBinding.vpPager, new TabLayoutMediator.TabConfigurationStrategy() {
            @Override
            public void onConfigureTab(@NonNull TabLayout.Tab tab, int position) {
                tab.setText(mTabTitle[position]);
            }
        }).attach();

        mBinding.vpPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                super.onPageScrolled(position, positionOffset, positionOffsetPixels);
            }

            @Override
            public void onPageSelected(int position) {
                Log.d(TAG, "<onPageSelected> position=" + position);
                super.onPageSelected(position);
                mFocusedPosition = position;
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                super.onPageScrollStateChanged(state);
            }
        });

        // 添加绑定设备按钮
        mBinding.btnAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String key = String.valueOf(mFocusedPosition);
                EquipmentListFragment focusedFragment = (EquipmentListFragment)mFragmentMap.get(key);
                focusedFragment.onBtnAdd();
            }
        });
    }

    public class EquipmentAdapter extends FragmentStateAdapter {
        public EquipmentAdapter(Fragment fragment) {
            super(fragment);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return (Fragment) mFragmentMap.values().toArray()[position];
        }

        @Override
        public int getItemCount() {
            return mFragmentMap.size();
        }
    }




}
