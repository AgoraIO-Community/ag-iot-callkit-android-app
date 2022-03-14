package com.agora.doorbell.msg;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.agora.agoracallkit.callkit.AgoraCallKit;
import com.agora.agoracallkit.callkit.CallKitAccount;
import com.agora.agoracallkit.callkit.ICallKitCallback;
import com.agora.doorbell.base.BaseFragment;
import com.agora.doorbell.base.RecyclerViewClickListener;
import com.agora.doorbell.base.RecyclerViewTouchListener;
import com.agora.doorbell.databinding.FragmentEquipmentListBinding;
import com.agora.doorbell.databinding.FragmentMsgListBinding;
import com.agora.doorbell.equipment.EquipmentListAdapter;
import com.agora.doorbell.play.VideoListAdapter;

import java.util.ArrayList;
import java.util.List;

public class MsgListFragment extends BaseFragment implements ICallKitCallback {
    private static final String TAG = "DoorBell/MsgListFrag";
    private FragmentMsgListBinding mBinding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mBinding = FragmentMsgListBinding.inflate(inflater, container, false);
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 查询告警记录列表
        List<AgoraCallKit.AlarmRecord> recordList = AgoraCallKit.getInstance().queryAllAlarm();

        mBinding.rvEquipment.setAdapter(new MsgListAdapter(recordList));
        mBinding.rvEquipment.setLayoutManager(new LinearLayoutManager(getContext()));
        mBinding.rvEquipment.addOnItemTouchListener(new RecyclerViewTouchListener(getContext(), mBinding.rvEquipment, new RecyclerViewClickListener() {
            @Override
            public void onClick(View view, int position) {

            }
        }));
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
    public void onAlarmReceived(CallKitAccount account, CallKitAccount peer_account,
                                long timestamp, String alarmMsg) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mPopupMessage("接收到来自: " + peer_account.getName() + " 的告警消息: " + alarmMsg);

                // 刷新当前设备告警列表
                List<AgoraCallKit.AlarmRecord> recordList = AgoraCallKit.getInstance().queryAllAlarm();
                mBinding.rvEquipment.setAdapter(new MsgListAdapter(recordList));
                mBinding.rvEquipment.invalidate();
            }
        });
    }
}
