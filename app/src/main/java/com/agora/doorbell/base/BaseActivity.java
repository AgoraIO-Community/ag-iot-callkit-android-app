package com.agora.doorbell.base;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import com.agora.agoracallkit.callkit.AgoraCallKit;
import com.agora.agoracallkit.callkit.CallKitAccount;
import com.agora.agoracallkit.callkit.ICallKitCallback;
import com.agora.doorbell.LoginActivity;
import com.agora.doorbell.TipActivity;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

public class BaseActivity extends AppCompatActivity implements ICallKitCallback {
    private final String TAG = "DoorBell/BaseActivity";
    protected ActionBar mActionBar;
    private ProgressDialog pd;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mActionBar = getSupportActionBar();

        pd = new ProgressDialog(this);
        pd.setMessage("loading");

        AgoraCallKit.getInstance().registerListener(this);
    }

    protected void mPgDigLogShow(String msg) {
        if (msg == "") {
            pd.show();
            return;
        }
        pd.setMessage(msg);
        pd.show();
    }

    protected void mPgDigLogHide() {
        pd.hide();
    }

    protected void mPopupMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    protected String combineText(List<String> text_list) {
        if (text_list == null) {
            return " { } ";
        }
        int count = text_list.size();
        String combine = " { ";

        for (int i = 0; i < (count-1); i++) {
            combine = combine + text_list.get(i) + ", ";
        }

        combine = combine + text_list.get(count-1) + " } ";
        return combine;
    }

    /*
     * @brief save bitmap to local file
     */
    protected boolean saveBmpToFile(Bitmap bmp, String fileName) {
        File f = new File(fileName);
        if (f.exists()) {
            f.delete();
        }
        try {
            FileOutputStream out = new FileOutputStream(f);
            bmp.compress(Bitmap.CompressFormat.JPEG, 100, out);
            out.flush();
            out.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Log.e(TAG, "<saveBmpToFile> file not found: " + fileName);
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "<saveBmpToFile> IO exception");
            return false;
        }

        return true;
    }

    @Override
    public void onLoginOtherDevice(CallKitAccount account) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AgoraCallKit.getInstance().callHangup();  // 本地挂断处理
                Intent intent = new Intent(BaseActivity.this, TipActivity.class);
                startActivity(intent);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (pd != null) {
            pd.dismiss();
        }
    }

}
