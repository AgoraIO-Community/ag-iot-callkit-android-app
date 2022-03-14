package com.agora.doorbell.equipment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.agora.doorbell.R;

import java.util.List;

public class EquipmentListAdapter extends RecyclerView.Adapter<EquipmentListAdapter.ViewHolder> {
    private final String TAG = "DoorBell/ListAdapter";

    private EquipmentListFragment mOwnerWgt;
    private DeviceInfo[] mLocalDataSet = null;

    /**
     * Provide a reference to the type of views that you are using
     * (custom ViewHolder).
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {
        private EquipmentListAdapter mAdapter;
        private final TextView mTvDevId;
        private final TextView mTvDevDetail;
        private final Button mBtnOperate;
        private int mPosition = 0;


        public ViewHolder(EquipmentListAdapter adapter, View view) {
            super(view);
            mAdapter = adapter;
            mTvDevId = (TextView) view.findViewById(R.id.text_view);
            mTvDevDetail = (TextView) view.findViewById(R.id.tv_dev_detail);
            mBtnOperate = (Button)view.findViewById(R.id.btn_dev_menu);

            // 设备操作按钮
            mBtnOperate.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mAdapter.onItemBtnClicked(mPosition, v);
                }
            });
        }

        /*
         * @brief 根据设备信息来刷新显示
         */
        public void updateUI(DeviceInfo devInfo, int position) {
            mPosition = position;
            mTvDevId.setText(devInfo.getDeviceId());

            if (devInfo.isOnline()) {
                mTvDevDetail.setText("设备在线");
            } else {
                mTvDevDetail.setText("设备已经离线");
            }
        }

    }

    /**
     * Initialize the dataset of the Adapter.
     *
     * @param dataSet String[] containing the data to populate views to be used
     *                by RecyclerView.
     */
    public EquipmentListAdapter(EquipmentListFragment ownerWgt, List<DeviceInfo> dataSet) {
        mOwnerWgt = ownerWgt;
        if (dataSet == null) {
            mLocalDataSet = null;
            return;
        }

        int count = dataSet.size();
        mLocalDataSet = new DeviceInfo[count];
        for (int i = 0; i < count; i++) {
            mLocalDataSet[i] = dataSet.get(i);
        }
    }

    public DeviceInfo getItemByPos(int position) {
        if (mLocalDataSet == null) {
            return null;
        }
        if (position >= mLocalDataSet.length) {
            return null;
        }

        return mLocalDataSet[position];
    }

    public void onItemBtnClicked(int position, View v) {
        Log.d(TAG, "<onItemBtnClicked> position=" + position);
        mOwnerWgt.onBtnDeviceOpt(mLocalDataSet[position], v);
    }

    // Create new views (invoked by the layout manager)
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        // Create a new view, which defines the UI of the list item
        View view = LayoutInflater.from(viewGroup.getContext())
                .inflate(R.layout.rv_equipment, viewGroup, false);

        return new ViewHolder(this, view);
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(ViewHolder viewHolder, final int position) {
        if (mLocalDataSet == null) {
            return;
        }
        // Get element from your dataset at this position and replace the
        // contents of the view with that element
        viewHolder.updateUI(mLocalDataSet[position], position);
    }

    // Return the size of your dataset (invoked by the layout manager)
    @Override
    public int getItemCount() {
        if (mLocalDataSet == null) {
            return 0;
        }

        return mLocalDataSet.length;
    }

}

