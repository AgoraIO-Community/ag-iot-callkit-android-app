/**
 * @file CloudRecordResult.java
 * @brief This file define the cloud recording results
 * @author xiaohua.lu
 * @email luxiaohua@agora.io
 * @version 1.0.0.1
 * @date 2021-12-13
 * @license Copyright (C) 2021 AgoraIO Inc. All rights reserved.
 */

package com.agora.agoracallkit.utils;


import android.content.Context;
import android.os.Bundle;

import java.util.ArrayList;

/*
 * @brief 云录制结果
 */
public class CloudRecordResult {

    /*
     * @brief 云录制的单个文件信息
     */
    public static class RecordFileInfo {
        public String mFilePath;
        public String mTrackType;
        public long mUid;
        public boolean mMixedAllUser;
        public boolean mIsPlayable;
        public long mSliceStartTime;
    }


    public ArrayList<RecordFileInfo> mFileList = new ArrayList<>();
    public String mUploadingStatus;

}
