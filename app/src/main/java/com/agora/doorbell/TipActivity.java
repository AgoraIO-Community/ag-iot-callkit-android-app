package com.agora.doorbell;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;

import androidx.annotation.Nullable;

import com.agora.doorbell.base.BaseActivity;
import com.agora.doorbell.databinding.ActivityTipBinding;

import java.util.List;

public class TipActivity extends BaseActivity {
    private ActivityTipBinding mbinding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mbinding = ActivityTipBinding.inflate(getLayoutInflater());
        View view = mbinding.getRoot();
        setContentView(view);

        mbinding.btOk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // 退出app
                ActivityManager activityManager = (ActivityManager) TipActivity.this.getApplicationContext().getSystemService(Context.ACTIVITY_SERVICE);
                List<ActivityManager.AppTask> appTaskList = activityManager.getAppTasks();
                for (ActivityManager.AppTask appTask : appTaskList) {
                    appTask.finishAndRemoveTask();
                }
            }
        });
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
