/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2022 Agora Lab, Inc (http://www.agora.io/)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */
package io.agora.iotcallkitdemo;


import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;

import io.agora.iotcallkitdemo.databinding.ActivityHomepageBinding;
import io.agora.iotcallkitdemo.uibase.BaseActivity;
import io.agora.iotcallkitdemo.uicallkit.CallDialFragment;
import io.agora.iotcallkitdemo.uipersonal.MineFragment;
import com.google.android.material.navigation.NavigationBarView;


public class HomePageActivity extends BaseActivity {

    private final String TAG = "IOTAPP20/HomePageAct";



    ///////////////////////////////////////////////////////////////////////////
    ///////////////////// Variable Definition /////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    private ActivityHomepageBinding mBinding;



    ////////////////////////////////////////////////////////////////////////////
    ///////////////////////// Override Activity Methods ////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "<onCreate>");
        super.onCreate(savedInstanceState);

        mBinding = ActivityHomepageBinding.inflate(getLayoutInflater());
        View view = mBinding.getRoot();
        setContentView(view);


        CallDialFragment dialFragment = new CallDialFragment();
        MineFragment personalFragment = new MineFragment();

        getSupportFragmentManager().beginTransaction().replace(R.id.fl_main, dialFragment).commit();

        mBinding.bnvMain.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                int itemId = item.getItemId();
                if (itemId == R.id.devicelist) {
                    getSupportFragmentManager().beginTransaction().replace(R.id.fl_main, dialFragment).commit();
                    return true;
                } else if (itemId == R.id.personal) {
                    getSupportFragmentManager().beginTransaction().replace(R.id.fl_main, personalFragment).commit();
                    return true;
                }

                return false;
            }
        });


    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "<onDestroy>");
        super.onDestroy();
    }

    @Override
    public void onStart() {
        Log.d(TAG, "<onStart>");
        super.onStart();
    }

    @Override
    public void onStop() {
        Log.d(TAG, "<onStop>");
        super.onStop();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        Log.d(TAG, "<onRequestPermissionsResult> requestCode=" + requestCode);
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        CallDialFragment dialFragment = (CallDialFragment)getSupportFragmentManager().findFragmentById(R.id.fl_main);
        if (dialFragment != null) {
            dialFragment.onFragRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }


    //////////////////////////////////////////////////////////////////////////////////
    /////////////////// Implement all Buttons Events & Messages //////////////////////
    //////////////////////////////////////////////////////////////////////////////////




}