
package io.agora.iotcallkitdemo.uiaccount;


import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import io.agora.iotcallkitdemo.databinding.ActivityAccountEntryBinding;
import io.agora.iotcallkitdemo.uibase.BaseActivity;


public class EntryActivity extends BaseActivity {

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////// Constant Definition /////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    private final String TAG = "IOTAPP20/EntryActivity";



    ///////////////////////////////////////////////////////////////////////////
    ///////////////////// Variable Definition /////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    private ActivityAccountEntryBinding mBinding;           ///< 自动生成的view绑定类


    ///////////////////////////////////////////////////////////////////////////
    ///////////////////// Override Acitivyt Methods ////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "<onCreate>");
        super.onCreate(savedInstanceState);
        mBinding = ActivityAccountEntryBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());


        // 设置注册按钮事件
        mBinding.btnAccountRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBtnRegister();
            }
        });

        // 设置登录按钮事件
        mBinding.btnAccountLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBtnLogin();
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
        super.onResume();
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "<onStop>");
        super.onStop();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "<onDestroy>");
        super.onDestroy();
    }


    /*
     * @brief 登录按钮事件
     */
    void onBtnLogin()
    {
        //Intent activityIntent = new Intent(EntryActivity.this, LoginActivity.class);
        Intent activityIntent = new Intent(EntryActivity.this, ThirdLoginActivity.class);
        startActivity(activityIntent);
    }

    /*
     * @brief 注册按钮事件
     */
    void onBtnRegister()
    {
        //Intent activityIntent = new Intent(EntryActivity.this, RegisterActivity.class);
        //activityIntent.putExtra("ACCOUNT_RESET", false);
        Intent activityIntent = new Intent(EntryActivity.this, ThirdRegActivity.class);
        startActivity(activityIntent);
    }

}