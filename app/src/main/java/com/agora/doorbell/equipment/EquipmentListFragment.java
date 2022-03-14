/**
 * @file EquipmentListFragment.java
 * @brief This file implement fragment for display binded device list
 * @author xiaohua.lu
 * @email luxiaohua@agora.io
 * @version 1.0.0.1
 * @date 2021-11-16
 * @license Copyright (C) 2021 AgoraIO Inc. All rights reserved.
 */

package com.agora.doorbell.equipment;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.InputFilter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.Switch;

import com.agora.doorbell.R;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.agora.agoracallkit.callkit.AgoraCallKit;
import com.agora.agoracallkit.callkit.CallKitAccount;
import com.agora.agoracallkit.callkit.ICallKitCallback;
import com.agora.doorbell.CalledActivity;
import com.agora.doorbell.base.BaseFragment;
import com.agora.doorbell.base.RecyclerViewClickListener;
import com.agora.doorbell.base.RecyclerViewTouchListener;
import com.agora.doorbell.databinding.FragmentEquipmentListBinding;
import com.agora.doorbell.play.VideoPlayActivity;
import com.agora.doorbell.rtc.RtcActivity;
import java.util.ArrayList;
import java.util.List;




public class EquipmentListFragment extends BaseFragment implements ICallKitCallback {
    private final String TAG = "DoorBell/EquipListFrag";

    //
    // message Id
    //
    public static final int MSGID_REFRESH_LIST = 0x1001;    ///< 刷新绑定设备列表

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////// Variable Definition /////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    private FragmentEquipmentListBinding mBinding;
    private List<DeviceInfo> mBindedDevList = new ArrayList<>();    ///< 当前已经绑定的设备列表
    private Handler mMsgHandler = null;             ///< 主线程中的消息处理
    private boolean mQueriedDevList = false;        ///< 是否首次已经查询过设备列表
    private String mDialingDevId = "";              ///< 正在呼叫的设备Id



    ////////////////////////////////////////////////////////////////////////////
    ////////////////////////////// Public Methods /////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    public EquipmentListFragment() {
        mQueriedDevList = false;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "<onCreateView>");
        mBinding = FragmentEquipmentListBinding.inflate(inflater, container, false);
        View rootView = mBinding.getRoot();

        mMsgHandler = new Handler(getActivity().getMainLooper())
        {
            @SuppressLint("HandlerLeak")
            public void handleMessage(Message msg)
            {
                switch (msg.what)
                {
                    case MSGID_REFRESH_LIST:
                        onMsgRefreshDevList();
                        break;
                }
            }
        };

        return rootView;
    }

    @Override
    public void onDestroyView() {
        Log.d(TAG, "<onDestroyView>");
        super.onDestroyView();

        if (mMsgHandler != null) {
            mMsgHandler.removeMessages(MSGID_REFRESH_LIST);
            mMsgHandler = null;
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "<onViewCreated>");
        super.onViewCreated(view, savedInstanceState);

        mBinding.rvEquipment.setAdapter(new EquipmentListAdapter(this, mBindedDevList));
        mBinding.rvEquipment.setLayoutManager(new LinearLayoutManager(getContext()));
        mBinding.rvEquipment.addOnItemTouchListener(new RecyclerViewTouchListener(getContext(), mBinding.rvEquipment, new RecyclerViewClickListener() {
            @Override
            public void onClick(View view, int position) {
            }
        }));

        mBinding.srlDevList.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                onMsgRefreshDevList();
            }
        });

    }

    @Override
    public void onStart() {
        Log.d(TAG, "<onStart>");
        super.onStart();

        //注册呼叫监听
        AgoraCallKit.getInstance().registerListener(this);

        if (!mQueriedDevList) { // 设备列表查询只进行一次
            Log.d(TAG, "<onViewCreated> querying device list...");
            Message msg = new Message();
            msg.what = MSGID_REFRESH_LIST;
            mMsgHandler.sendMessageDelayed(msg, 100);
            mQueriedDevList = true;
        }
    }

    @Override
    public void onStop() {
        Log.d(TAG, "<onStop>");
        super.onStop();

        // 注销呼叫监听
        AgoraCallKit.getInstance().unregisterListener(this);
    }


    ////////////////////////////////////////////////////////////////////////////
    /////////////////// Implement all Buttons Events ///////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    /*
     * @brief 添加按钮点击事件，新增绑定设备
     */
    public void onBtnAdd() {
        final EditText inputEditText = new EditText(getActivity());
        inputEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(50)});
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("输入要绑定的设备ID: ")
                .setIcon(android.R.drawable.ic_menu_add)
                .setView(inputEditText)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                     public void onClick(DialogInterface dialog, int which) {
                         String inputDevice = inputEditText.getText().toString();
                         if (inputDevice.isEmpty()) {
                             return;
                         }
                         onBtnBindDevice(inputDevice);
                    }});
        builder.show();
    }

    void onBtnBindDevice(String newDevice) {
        String tips = "正在绑定新设备: " + newDevice + " ......";
        mPgDigLogShow(tips);
        List<String> devList = new ArrayList<>();
        devList.add(newDevice);
        int ret = AgoraCallKit.getInstance().bindDevice(devList);
        if (ret != AgoraCallKit.ERR_NONE) {
            mPgDigLogHide();
            mPopupMessage("不能进行绑定操作");
        }
    }

    /*
     * @brief 设备操作按钮点击事件，弹出操作菜单项
     */
    void onBtnDeviceOpt(DeviceInfo devInfo, View v) {

        PopupMenu popupMenu = new PopupMenu(getActivity(), v);
        MenuInflater inflater = popupMenu.getMenuInflater();
        inflater.inflate(R.menu.device_menu, popupMenu.getMenu());

        //绑定菜单项的点击事件
        popupMenu.setOnMenuItemClickListener(
            new PopupMenu.OnMenuItemClickListener() {
             @Override
             public boolean onMenuItemClick(MenuItem item) {
                 int itemId = item.getItemId();
                 if (itemId == R.id.menu_dev_dial) {
                     onDevMenuDial(devInfo);
                 } else if (itemId == R.id.menu_dev_unbind) {
                     onDevMenuUnbind(devInfo);
                 } else if (itemId == R.id.menu_dev_detail) {
                     onDevMenuDetail(devInfo);
                 }
                 return true;
             }
         });

        popupMenu.show();
    }

    /*
     * @brief 菜单项操作：联接设备
     */
    void onDevMenuDial(DeviceInfo deviceInfo) {
        mDialingDevId = deviceInfo.getDeviceId();
//        String tips = "正在联接设备: " + mDialingDevId + " .....";
//        mPgDigLogShow(tips);
//
//        CallKitAccount dialAccount = new CallKitAccount(mDialingDevId, CallKitAccount.ACCOUNT_TYPE_DEV);
//        List<CallKitAccount> dialList = new ArrayList<>();
//        dialList.add(dialAccount);
//        int ret = AgoraCallKit.getInstance().callDial(dialList, "I'm DoorBell demo");
//        if (ret != AgoraCallKit.ERR_NONE) {
//            mDialingDevId = "";
//            mPgDigLogHide();
//            mPopupMessage("不能呼叫, 错误码: " + ret);
//            return;
//        }

        // 直接跳转到通话界面
        Intent intent = new Intent(getActivity(), RtcActivity.class);
        intent.putExtra(RtcActivity.DEVICE_ID_KEY, mDialingDevId);
        startActivity(intent);
        mDialingDevId = "";
    }

    /*
     * @brief 菜单项操作：解绑设备
     */
    void onDevMenuUnbind(DeviceInfo deviceInfo) {
        String deviceId = deviceInfo.getDeviceId();
        String tips = "正在解绑设备: " + deviceId + " .....";
        mPgDigLogShow(tips);

        List<String> devList = new ArrayList<>();
        devList.add(deviceId);
        int ret = AgoraCallKit.getInstance().unbindDevice(devList);
        if (ret != AgoraCallKit.ERR_NONE) {
            mPgDigLogHide();
            mPopupMessage("不能解绑设备, 错误码: " + ret);
        }
    }

    /*
     * @brief 菜单项操作：查看详情
     */
    void onDevMenuDetail(DeviceInfo deviceInfo) {
        String deviceId = deviceInfo.getDeviceId();

        Intent intent = new Intent(getActivity(), VideoPlayActivity.class);
        intent.putExtra(VideoPlayActivity.DEVICE_ID_KEY, deviceId);
        startActivity(intent);
    }


    /*
     * @brief 刷新绑定设备列表
     */
    void onMsgRefreshDevList() {
        mPgDigLogShow("正在刷新绑定的设备列表.....");
        int ret = AgoraCallKit.getInstance().queryBindedDevList();
        if (ret != AgoraCallKit.ERR_NONE) {
            mPgDigLogHide();
            mPopupMessage("查询绑定设备失败, 错误码: " + ret);
        }
    }


    ////////////////////////////////////////////////////////////////////////////
    /////////////////// Override ICallKitCallback Methods //////////////////////
    ////////////////////////////////////////////////////////////////////////////
     @Override
    public void onBindDeviceDone(CallKitAccount account, List<CallKitAccount> bindedDevList, int errCode) {
        Log.d(TAG, "<onBindDeviceDone> errCode=" + errCode);
        Log.d(TAG, combineAccountInfo(bindedDevList));

        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mPgDigLogHide();
                updateBindDevList(bindedDevList);

                if (errCode == AgoraCallKit.ERR_NONE) {
                    mPopupMessage("绑定成功");
                } else {
                    mPopupMessage("绑定失败");
                }
            }
        });
    }

    @Override
    public void onUnbindDeviceDone(CallKitAccount account, List<CallKitAccount> bindedDevList, int errCode) {
        Log.d(TAG, "<onUnbindDeviceDone> errCode=" + errCode);
        Log.d(TAG, combineAccountInfo(bindedDevList));

        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mPgDigLogHide();
                updateBindDevList(bindedDevList);

                if (errCode == AgoraCallKit.ERR_NONE) {
                    mPopupMessage("解绑成功");
                } else {
                    mPopupMessage("解绑失败");
                }
            }
        });
    }

    @Override
    public void onQueryBindDevListDone(CallKitAccount account, List<CallKitAccount> bindedDevList, int errCode) {
        Log.d(TAG, "<onQueryBindDevListDone> errCode=" + errCode);
        Log.d(TAG, combineAccountInfo(bindedDevList));

        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mBinding.srlDevList.setRefreshing(false);
                mPgDigLogHide();
                updateBindDevList(bindedDevList);
            }
        });
    }

    @Override
    public void onAlarmReceived(CallKitAccount account, CallKitAccount peer_account,
                                long timestamp, String alarmMsg) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mPopupMessage("接收到来自: " + peer_account.getName() + " 的告警消息: " + alarmMsg);
            }
        });
    }


    ///////////////////////////////////////////////////////////////
    //////////////////////// Internal Methods /////////////////////
    ///////////////////////////////////////////////////////////////
    /*
     * @brief 刷新当前绑定设备列表控件
     */
    void updateBindDevList(List<CallKitAccount> deviceList)  {
        synchronized (mBindedDevList) {
            mBindedDevList.clear();
            if (deviceList != null) {
                for (CallKitAccount devAccount : deviceList) {
                    DeviceInfo devInfo = new DeviceInfo(devAccount.getName(), devAccount.getOnline());
                    mBindedDevList.add(devInfo);
                }
            }
        }

        mBinding.rvEquipment.setAdapter(new EquipmentListAdapter(this, mBindedDevList));
        mBinding.rvEquipment.invalidate();
    }
}
