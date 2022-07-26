/**
 * @file IAgoraIotAppSdk.java
 * @brief This file define the SDK interface for Agora Iot AppSdk 2.0
 * @author xiaohua.lu
 * @email luxiaohua@agora.io
 * @version 1.0.0.1
 * @date 2022-01-26
 * @license Copyright (C) 2021 AgoraIO Inc. All rights reserved.
 */
package io.agora.iotcallkit;


import io.agora.iotcallkit.sdkimpl.AgoraCallkitSdk;


/*
 * @brief SDK引擎接口
 */

public class ACallkitSdkFactory  {

    private static IAgoraCallkitSdk  mSdkInstance = null;

    public static IAgoraCallkitSdk getInstance() {
        if(mSdkInstance == null) {
            synchronized (AgoraCallkitSdk.class) {
                if(mSdkInstance == null) {
                    mSdkInstance = new AgoraCallkitSdk();
                }
            }
        }

        return mSdkInstance;
    }

}
