package com.agora.agoracalldemo;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.agora.agoracallkit.callkit.AgoraCallKit;
import com.hyphenate.easeim.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class VideoListAdapter extends RecyclerView.Adapter<VideoListAdapter.ViewHolder> {

    public interface IAdapterCallback{

        public void onItemClicked(int position, AgoraCallKit.AlarmRecord item);
    }

    private List<AgoraCallKit.AlarmRecord> mLocalDataSet = null;    ///< 告警记录列表
    private IAdapterCallback mCallback;


    /**
     * @brief Provide a reference to the type of views that you are using (custom ViewHolder).
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {
        private VideoListAdapter mAdapter;
        private final TextView mTvTime;
        private final TextView mTVMessage;
        private int mPosition = 0;

        public ViewHolder(VideoListAdapter adapter, View view) {
            super(view);
            mAdapter = adapter;
            mTvTime = (TextView) view.findViewById(R.id.tv_alarm_time);
            mTVMessage = (TextView) view.findViewById(R.id.tv_alarm_message);

            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {




                }
            });
        }

        /*
         * @brief 根据 告警记录信息来刷新界面显示
         */
        public void updateUI(AgoraCallKit.AlarmRecord record, int position) {
            mPosition = position;
            mTVMessage.setText(record.mMessage);
            mTvTime.setText(getTimeText(record.mTimestamp));
        }


        public String getTimeText(long timestamp) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String strTime = dateFormat.format(new Date(timestamp));
            return strTime;
        }
    }


    /**
     * @brief 初始化告警列表适配器
     * @param dataSet : 告警数据集
     * @return None
     */
    public VideoListAdapter(List<AgoraCallKit.AlarmRecord> dataSet, IAdapterCallback callback) {
        if (dataSet == null) {
            mLocalDataSet = null;
            return;
        }

        mLocalDataSet = dataSet;
        mCallback = callback;
    }

    public AgoraCallKit.AlarmRecord getItemByPos(int position) {
        if (mLocalDataSet == null) {
            return null;
        }
        if (position >= mLocalDataSet.size()) {
            return null;
        }

        return mLocalDataSet.get(position);
    }

    // Create new views (invoked by the layout manager)
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        // Create a new view, which defines the UI of the list item
        View view = LayoutInflater.from(viewGroup.getContext())
                .inflate(R.layout.rv_msg, viewGroup, false);

        return new ViewHolder(this, view);
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(ViewHolder viewHolder, final int position) {
        if (mLocalDataSet == null) {
            return;
        }
        if (position >= mLocalDataSet.size()) {
            return;
        }

        viewHolder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mCallback.onItemClicked(position, getItemByPos(position));
            }
        });


        // Get element from your dataset at this position and replace the
        // contents of the view with that element
        AgoraCallKit.AlarmRecord record = mLocalDataSet.get(position);
        viewHolder.updateUI(record, position);
    }

    // Return the size of your dataset (invoked by the layout manager)
    @Override
    public int getItemCount() {
        if (mLocalDataSet == null) {
            return 0;
        }

        return mLocalDataSet.size();
    }
}

