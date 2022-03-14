package com.agora.doorbell.base;


import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.agora.agoracallkit.callkit.CallKitAccount;
import com.agora.doorbell.LoginActivity;
import com.agora.doorbell.SplashActivity;

import java.util.ArrayList;
import java.util.List;

public class BaseFragment extends Fragment {

    private ProgressDialog pd;


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        pd = new ProgressDialog(getContext());
        pd.setMessage("loading");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (pd != null) {
            pd.dismiss();
        }
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
        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
    }


    protected String combineText(List<CallKitAccount> text_list) {
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

    protected String combineAccountInfo(List<CallKitAccount> accountList) {
        String acountInfo = "";
        for (int i = 0; i < accountList.size(); i++) {
            CallKitAccount devAccount = accountList.get(i);
            acountInfo = acountInfo + devAccount + "\n";
        }
        return acountInfo;
    }

    protected void GotoLoginActivity() {
        new android.os.Handler(Looper.getMainLooper()).postDelayed(
                new Runnable() {
                    public void run() {
                        Intent intent = new Intent(getActivity(), LoginActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        getActivity().finish();
                    }
                },
                3000);
    }



}
