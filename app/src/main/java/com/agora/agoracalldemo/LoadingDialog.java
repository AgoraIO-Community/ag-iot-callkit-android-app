/**
 * @file LoadingDialog.java
 * @brief This file implement the progress dialog
 * @author xiaohua.lu
 * @email luxiaohua@agora.io
 * @version 1.0.0.1
 * @date 2021-10-13
 * @license Copyright (C) 2021 AgoraIO Inc. All rights reserved.
 */
package com.agora.agoracalldemo;

import android.app.Dialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import com.hyphenate.easeim.databinding.LoadingAlertBinding;


class LoadingDialog extends Dialog {

    LoadingDialog(Context ctx)
    {
        super(ctx);
    }

    static class Builder {

        private LoadingAlertBinding binding;
        private String message = "";                ///< 提示信息
        private boolean isShowMessage = true;       ///< 是否展示提示信息
        private boolean isCancelable = true;        ///< 是否按返回键取消
        private boolean isCancelOutside = false;    ///< 是否取消

        /**
         * 设置提示信息
         * @param message
         * @return
         */
        void setMessage(String message) {
            this.message = message;
        }

        /**
         * 设置是否显示提示信息
         * @param isShowMessage
         * @return
         */
        void setShowMessage(boolean isShowMessage) {
            this.isShowMessage = isShowMessage;
        }

        /**
         * 设置是否可以按返回键取消
         * @param isCancelable
         * @return
         */
        void setCancelable(boolean isCancelable) {
            this.isCancelable = isCancelable;
        }

        void updateMessage(String message) {
            binding.tipTextView.setText(message);
        }

        /**
         * 设置是否可以取消
         * @param isCancelOutside
         * @return
         */
        void setCancelOutside(boolean isCancelOutside) {
            this.isCancelOutside = isCancelOutside;
        }

        //创建Dialog
        LoadingDialog create(Context context) {
            LayoutInflater inflater = LayoutInflater.from(context);
            binding = LoadingAlertBinding.inflate(inflater);

            //设置带自定义主题的dialog
            LoadingDialog dlg = new LoadingDialog(context);
            if (isShowMessage) {
                binding.tipTextView.setText(message);
            } else {
                binding.tipTextView.setVisibility(View.GONE);
            }
            dlg.setContentView(binding.getRoot());
            dlg.setCancelable(isCancelable);
            dlg.setCanceledOnTouchOutside(isCancelOutside);
            return dlg;
        }
    }


}