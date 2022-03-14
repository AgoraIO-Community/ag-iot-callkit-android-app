package com.agora.doorbell.msg;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.agora.doorbell.base.BaseFragment;
import com.agora.doorbell.databinding.FragmentMsgBinding;
import com.agora.doorbell.equipment.EquipmentListFragment;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.HashMap;

public class MsgFragment extends BaseFragment {


    private FragmentMsgBinding mBinding;
    private HashMap<String, MsgListFragment> fragmentMap;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mBinding = FragmentMsgBinding.inflate(inflater, container, false);
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);


        fragmentMap = new HashMap<>();
        fragmentMap.put("门铃", new MsgListFragment());
        fragmentMap.put("门锁", new MsgListFragment());
        String s = (String) fragmentMap.keySet().toArray()[0];


        mBinding.vpPager.setAdapter(new MsgAdapter(this));
        mBinding.vpPager.setSaveEnabled(false);
        new TabLayoutMediator(mBinding.tabHead, mBinding.vpPager, new TabLayoutMediator.TabConfigurationStrategy() {
            @Override
            public void onConfigureTab(@NonNull TabLayout.Tab tab, int position) {
                tab.setText((String) fragmentMap.keySet().toArray()[position]);
            }
        }).attach();
    }

    public class MsgAdapter extends FragmentStateAdapter {
        public MsgAdapter(Fragment fragment) {
            super(fragment);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return (Fragment) fragmentMap.values().toArray()[position];
        }

        @Override
        public int getItemCount() {
            return fragmentMap.size();
        }
    }
}
