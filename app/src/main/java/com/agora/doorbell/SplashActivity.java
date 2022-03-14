package com.agora.doorbell;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.agora.agoracallkit.callkit.AgoraCallKit;
import com.agora.agoracallkit.callkit.CallKitAccount;
import com.agora.agoracallkit.callkit.ICallKitCallback;
import com.agora.doorbell.base.BaseActivity;
import com.agora.doorbell.databinding.ActivitySplashBinding;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import pub.devrel.easypermissions.AppSettingsDialog;
import pub.devrel.easypermissions.EasyPermissions;

public class SplashActivity extends BaseActivity implements EasyPermissions.PermissionCallbacks, ICallKitCallback {
    private final String TAG = "DoorBell/SplashActivity";

    private final int REQUEST_OVERLAYS_PERMISSION = 1001;

    private final String[] mPerms = {
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO
    };

    //
    // message Id
    //
    public static final int MSGID_INIT_SDK = 0x1001;


    private ActivitySplashBinding mBinding;
    private boolean mIsHasPermissions = false;
    private MyApplication mApplication;
    private static Handler mMsgHandler = null;      ///< 主线程中的消息处理



    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        Log.d(TAG, "<onCreate>");
        super.onCreate(savedInstanceState);
        mBinding = ActivitySplashBinding.inflate(getLayoutInflater());
        View view = mBinding.getRoot();
        setContentView(view);

        mActionBar.hide();
        mApplication = (MyApplication) getApplication();

        mMsgHandler = new Handler(this.getMainLooper())
        {
            @SuppressLint("HandlerLeak")
            public void handleMessage(Message msg)
            {
                switch (msg.what)
                {
                    case MSGID_INIT_SDK: {
                        onMsgInitEngine(msg.arg1);
                    } break;
                }
            }
        };

        // 悬浮窗权限检测
        if(!checkFloatPermission(this)) {
            //权限请求方法
            requestSettingCanDrawOverlays();
            return;
        }

        // 其他权限检测
        checkMainPermissions();

    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "<onDestroy>");
        super.onDestroy();

        if (mMsgHandler != null) {  // remove all messages
            mMsgHandler.removeMessages(MSGID_INIT_SDK);
            mMsgHandler = null;
        }
    }

    @Override
    protected void onStart() {
        Log.d(TAG, "<onStart>");
        super.onStart();
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "<onStop>");
        super.onStop();
        //注销呼叫监听
        AgoraCallKit.getInstance().unregisterListener(this);
    }

    /*
     * @brief 进行SDK初始化操作
     * @param needAutoLogin : 初始化完成后是否进行自动登录，
     */
     void onMsgInitEngine(int needAutoLogin) {
         Log.d(TAG, "<onMsgInitEngine> needAutoLogin=" + needAutoLogin);

         if (mApplication.isEngineReady()) {  // 已经处于登录状态了，直接进入MainActivity
             Log.d(TAG, "<onMsgInitEngine> engine is ready, switch to MainActivity");
             Intent intent = new Intent(SplashActivity.this, MainActivity.class);
             startActivity(intent);
             finish();
             return;
         }

        // 在获得权限后才能初始化引擎，内部已经做了多次初始化处理
        mApplication.initializeEngine();

        //注册呼叫监听
        AgoraCallKit.getInstance().registerListener(this);

        if (needAutoLogin == 1) {
            // 进行自动登录
            int ret = AgoraCallKit.getInstance().accountAutoLogin();
            if (ret != AgoraCallKit.ERR_NONE) {
                mPopupMessage("不能自动登录, 错误码: " + ret);
            }

        } else {

            // 直接跳转到登录界面
            Intent intent = new Intent(SplashActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
        }
    }

    /*
     * @brief 判断是否开启悬浮窗权限
     */
    public boolean checkFloatPermission(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT)
            return true;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            try {
                Class cls = Class.forName("android.content.Context");
                Field declaredField = cls.getDeclaredField("APP_OPS_SERVICE");
                declaredField.setAccessible(true);
                Object obj = declaredField.get(cls);
                if (!(obj instanceof String)) {
                    return false;
                }
                String str2 = (String) obj;
                obj = cls.getMethod("getSystemService", String.class).invoke(context, str2);
                cls = Class.forName("android.app.AppOpsManager");
                Field declaredField2 = cls.getDeclaredField("MODE_ALLOWED");
                declaredField2.setAccessible(true);
                Method checkOp = cls.getMethod("checkOp", Integer.TYPE, Integer.TYPE, String.class);
                int result = (Integer) checkOp.invoke(obj, 24, Binder.getCallingUid(), context.getPackageName());
                return result == declaredField2.getInt(cls);
            } catch (Exception e) {
                return false;
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                AppOpsManager appOpsMgr = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
                if (appOpsMgr == null)
                    return false;
                int mode = appOpsMgr.checkOpNoThrow("android:system_alert_window", android.os.Process.myUid(), context
                        .getPackageName());
                //return mode == AppOpsManager.MODE_ALLOWED || mode == AppOpsManager.MODE_IGNORED;
                return mode == AppOpsManager.MODE_ALLOWED;
            } else {
                return Settings.canDrawOverlays(context);
            }
        }
    }

    /*
     * @brief 申请悬浮窗权限打开
     */
    private void requestSettingCanDrawOverlays() {
        int sdkInt = Build.VERSION.SDK_INT;
        if (sdkInt >= Build.VERSION_CODES.O) {//8.0以上
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            startActivityForResult(intent, REQUEST_OVERLAYS_PERMISSION);

        } else if (sdkInt >= Build.VERSION_CODES.M) {//6.0-8.0
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_OVERLAYS_PERMISSION);

        } else {//4.4-6.0以下
            //无需处理了
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_OVERLAYS_PERMISSION) {
            checkMainPermissions();
        }
    }

    /*
     * @brief 检查存储空间、麦克风、Camera等权限
     */
    void checkMainPermissions() {
        if (EasyPermissions.hasPermissions(this, mPerms)) {
            // 已经有权限，进行相关初始化和自动登录操作
            Log.d(TAG, "<onCreate> have all permissions");
            Message msg = new Message();
            msg.what = MSGID_INIT_SDK;
            msg.arg1 = 1;
            mMsgHandler.removeMessages(MSGID_INIT_SDK);
            mMsgHandler.sendMessage(msg);

        } else {
            // 没有权限，进行权限请求
            Log.d(TAG, "<onCreate> requesting all permissions...");
            EasyPermissions.requestPermissions(this, "权限不全",
                    100, mPerms);
        }
    }


    ////////////////////////////////////////////////////////////////////////////
    /////////////////// Override ICallKitCallback Methods //////////////////////
    ////////////////////////////////////////////////////////////////////////////
    @Override
    public void onLogInDone(CallKitAccount account, int errCode) {
        Log.d(TAG, "<onLogInDone>");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mPgDigLogHide();
                if (account == null) {
                    mPopupMessage("登录失败，没有有效的账号");
                    mToLoginActivity();
                } else {
                    if (errCode != AgoraCallKit.ERR_NONE) {
                        mPopupMessage("账号: " + account.getName() + " 登录失败");
                        mToLoginActivity();
                    } else {
                        // 切换到登录成功界面
                        Intent intent = new Intent(SplashActivity.this, MainActivity.class);
                        startActivity(intent);
                        finish();
                    }
                }
            }
        });
    }

    private void mToLoginActivity() {
        new android.os.Handler(Looper.getMainLooper()).postDelayed(
                new Runnable() {
                    public void run() {
                        Intent intent = new Intent(SplashActivity.this, LoginActivity.class);
                        startActivity(intent);
                        finish();
                    }
                },
                3000);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    @Override
    public void onPermissionsGranted(int requestCode, @NonNull List<String> perms) {
        Log.d(TAG, "<onPermissionsGranted> perms=" + perms);

        if (EasyPermissions.hasPermissions(this, mPerms)) {
            // 已经有所有权限，进行相关初始化操作，之后直接跳转到登录界面
            Message msg = new Message();
            msg.what = MSGID_INIT_SDK;
            msg.arg1 = 0;
            mMsgHandler.removeMessages(MSGID_INIT_SDK);
            mMsgHandler.sendMessage(msg);
        }
    }

    @Override
    public void onPermissionsDenied(int requestCode, @NonNull List<String> perms) {
        if (EasyPermissions.somePermissionPermanentlyDenied(this, perms)) {
            new AppSettingsDialog.Builder(this).build().show();
        }
    }

    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {
    }
}
