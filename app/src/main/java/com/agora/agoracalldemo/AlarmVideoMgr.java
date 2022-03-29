/**
 * @file AlarmVideoMgr.java
 * @brief This file implement video file management for alarm
 * @author xiaohua.lu
 * @email luxiaohua@agora.io
 * @version 1.0.0.1
 * @date 2021-12-17
 * @license Copyright (C) 2021 AgoraIO Inc. All rights reserved.
 */

package com.agora.agoracalldemo;


import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

import com.alibaba.sdk.android.oss.ClientConfiguration;
import com.alibaba.sdk.android.oss.ClientException;
import com.alibaba.sdk.android.oss.OSS;
import com.alibaba.sdk.android.oss.OSSClient;
import com.alibaba.sdk.android.oss.ServiceException;
import com.alibaba.sdk.android.oss.callback.OSSCompletedCallback;
import com.alibaba.sdk.android.oss.common.OSSLog;
import com.alibaba.sdk.android.oss.common.auth.OSSAuthCredentialsProvider;
import com.alibaba.sdk.android.oss.common.auth.OSSCredentialProvider;
import com.alibaba.sdk.android.oss.common.auth.OSSCustomSignerCredentialProvider;
import com.alibaba.sdk.android.oss.common.auth.OSSFederationCredentialProvider;
import com.alibaba.sdk.android.oss.common.auth.OSSFederationToken;
import com.alibaba.sdk.android.oss.common.auth.OSSStsTokenCredentialProvider;
import com.alibaba.sdk.android.oss.internal.OSSAsyncTask;
import com.alibaba.sdk.android.oss.internal.ResponseParsers;
import com.alibaba.sdk.android.oss.model.BucketLifecycleRule;
import com.alibaba.sdk.android.oss.model.CannedAccessControlList;
import com.alibaba.sdk.android.oss.model.CreateBucketRequest;
import com.alibaba.sdk.android.oss.model.CreateBucketResult;
import com.alibaba.sdk.android.oss.model.DeleteBucketLifecycleRequest;
import com.alibaba.sdk.android.oss.model.DeleteBucketLifecycleResult;
import com.alibaba.sdk.android.oss.model.DeleteBucketLoggingRequest;
import com.alibaba.sdk.android.oss.model.DeleteBucketLoggingResult;
import com.alibaba.sdk.android.oss.model.DeleteBucketRequest;
import com.alibaba.sdk.android.oss.model.DeleteBucketResult;
import com.alibaba.sdk.android.oss.model.DeleteMultipleObjectRequest;
import com.alibaba.sdk.android.oss.model.DeleteMultipleObjectResult;
import com.alibaba.sdk.android.oss.model.GetBucketInfoRequest;
import com.alibaba.sdk.android.oss.model.GetBucketInfoResult;
import com.alibaba.sdk.android.oss.model.GetBucketACLRequest;
import com.alibaba.sdk.android.oss.model.GetBucketACLResult;
import com.alibaba.sdk.android.oss.model.GetBucketLifecycleRequest;
import com.alibaba.sdk.android.oss.model.GetBucketLifecycleResult;
import com.alibaba.sdk.android.oss.model.GetBucketLoggingRequest;
import com.alibaba.sdk.android.oss.model.GetBucketLoggingResult;
import com.alibaba.sdk.android.oss.model.GetBucketRefererRequest;
import com.alibaba.sdk.android.oss.model.GetBucketRefererResult;
import com.alibaba.sdk.android.oss.model.HeadObjectRequest;
import com.alibaba.sdk.android.oss.model.HeadObjectResult;
import com.alibaba.sdk.android.oss.model.ListBucketsRequest;
import com.alibaba.sdk.android.oss.model.ListBucketsResult;
import com.alibaba.sdk.android.oss.model.ListObjectsRequest;
import com.alibaba.sdk.android.oss.model.ListObjectsResult;
import com.alibaba.sdk.android.oss.model.OSSBucketSummary;
import com.alibaba.sdk.android.oss.model.OSSObjectSummary;
import com.alibaba.sdk.android.oss.model.Owner;
import com.alibaba.sdk.android.oss.model.PutBucketLifecycleRequest;
import com.alibaba.sdk.android.oss.model.PutBucketLifecycleResult;
import com.alibaba.sdk.android.oss.model.PutBucketLoggingRequest;
import com.alibaba.sdk.android.oss.model.PutBucketLoggingResult;
import com.alibaba.sdk.android.oss.model.PutBucketRefererRequest;
import com.alibaba.sdk.android.oss.model.PutBucketRefererResult;
import com.alibaba.sdk.android.oss.model.PutObjectRequest;

import java.util.ArrayList;
import java.util.List;

public class AlarmVideoMgr {

    ////////////////////////////////////////////////////////////////////////
    //////////////////////// Data Structure Definition /////////////////////
    ////////////////////////////////////////////////////////////////////////
    /*
     * @brief 单个告警视频文件信息
     */
    public static class AlarmVideoInfo {
        OSSObjectSummary mOssInfo;
        String mUrl;                ///< 公共的签名URL，外部应用可以通过这个URL访问视频文件

    }

    /*
     * @brief 视频文件管理的回调
     */
    public interface OnVideoMagrCallback{

        /*
         * @brief 查询文件页的回调
         * @param errCode : 错误码，XOK表示查询成功，并且可以继续；
         *                         XERR_EOF 表示查询成功，并且所有文件都查询完成
         *                         其他值表示查询失败，不可再继续，并且此时outFileList为空
         * @param outFileList : 输出查询到文件列表
         */

        public void onRetrieveDone(int errCode, List<AlarmVideoInfo> outFileList);
    }


    ///////////////////////////////////////////////////////////////////////////
    ///////////////////// Constant Definition /////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    private final static String TAG = "DEMO/AlarmVideoMgr";
    private static final int WAIT_OPT_TIMEOUT = 3000;
    private final static String ENDPOINT = "oss-cn-shanghai.aliyuncs.com";

    /*
     * @brief 通话引擎初始化参数
     */
    public static class VideoMgrInitParam {
        public Context mContext;
        public OnVideoMagrCallback mCallback;   ///< 回调接口

        public int mRegionId = 1;           ///< 区域信息
        public String mBucket = "agora-iot-oss-test";
        public String mAccessKey = "LTAI5t9JUNPgxDqsH5KAYgfx";
        public String mSecretKey = "b3iKLg94tyLBGmC3TeaOPg3BwCmzLD";
    }



    ///////////////////////////////////////////////////////////////////////////
    ///////////////////// Variable Definition /////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    private VideoMgrInitParam mInitParam;
    private OSS mOssInstance;
    private String mRetrieveMarker = null;              ///< 枚举文件列表标记
    private boolean mRetrieveCompleted = false;         ///< 枚举文件列表结束标记
    private int mRetrieveErrCode = ErrCode.XOK;         ///< 枚举错误代码




    ///////////////////////////////////////////////////////////////////////////
    //////////////////////// Public Methods ///////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    /*
     * @brief 初始化操作，主要是OSS对象初始化
     * @param initParam ：初始化参数
     * @return 返回错误码
     */
    public synchronized int initialize(VideoMgrInitParam initParam) {
        mInitParam = initParam;

        // 配置登录账号信息
        OSSCredentialProvider credentialProvider = new OSSStsTokenCredentialProvider(mInitParam.mAccessKey,
                mInitParam.mSecretKey, "");


        // 配置类如果不设置，会有默认配置。
        ClientConfiguration conf = new ClientConfiguration();
        conf.setConnectionTimeout(15 * 1000);       // 连接超时，默认15秒。
        conf.setSocketTimeout(15 * 1000);           // socket超时，默认15秒。
        conf.setMaxConcurrentRequest(5);            // 最大并发请求数，默认5个。
        conf.setMaxErrorRetry(2);                   // 失败后最大重试次数，默认2次。
        mOssInstance = new OSSClient(mInitParam.mContext, ENDPOINT, credentialProvider, conf);


        Log.d(TAG, "<initialize> done");
        return ErrCode.XOK;
    }

    /*
     * @brief 释放文件管理器
     * @param  None
     * @return None
     */
    public synchronized void release()
    {
        if (mOssInstance != null) {
            mOssInstance = null;
            Log.d(TAG, "<release> done");
        }
    }



    /*
     * @brief 检索文件复位，通常开始检索时调用该方法
     *        之后就是反复的调用 retrieveFilesStep()来获取一页文件列表
     * @param pageSize ： 每次检索时最大文件数量
     * @return 返回错误码
     */
    public synchronized int retrieveFilesReset()
    {
        if (mOssInstance == null) {
            Log.e(TAG, "<retrieveFilesStart> [ERROR] bad status");
            return ErrCode.XERR_BAD_STATE;
        }

        mRetrieveMarker = null;        // 初始为空，后面每次枚举后设置为前一次的
        mRetrieveCompleted = false;
        mRetrieveErrCode = ErrCode.XOK;

        Log.d(TAG, "<retrieveFilesStart> done");
        return ErrCode.XOK;
    }


    /*
     * @brief 同步检索一页文件
     * @param fileList : 输出检索到的当前页的文件列表
     * @return 返回错误码，XOK 表示当前检索成功，还可以继续检索
     *                    XERR_EOF 表示检索已经全部结束
     *                    其他值表示检索错误
     */
    public synchronized int retrieveFilesStep(int pageSize, List<AlarmVideoInfo> outFileList)
    {
        if (mOssInstance == null) {
            Log.e(TAG, "<retrieveFilesStep> [ERROR] bad status");
            return ErrCode.XERR_BAD_STATE;
        }

        ListObjectsRequest request = new ListObjectsRequest(mInitParam.mBucket);
        request.setMaxKeys(pageSize);
        request.setMarker(mRetrieveMarker);
        outFileList.clear();

        boolean listCompleted = false;
        int errCode = ErrCode.XOK;

        try {
            ListObjectsResult listResult = mOssInstance.listObjects(request);
            if (listResult == null) {
                Log.e(TAG,"<retrieveFilesStep> fail to listObjects()");
                return ErrCode.XERR_UNKNOWN;
            }

            for (OSSObjectSummary ossObj : listResult.getObjectSummaries()) {
                AlarmVideoInfo videoInfo = new AlarmVideoInfo();
                videoInfo.mOssInfo = ossObj;
                videoInfo.mUrl = mOssInstance.presignPublicObjectURL(mInitParam.mBucket, ossObj.getKey());
                Log.e(TAG,"<retrieveFilesStep> key=" + ossObj.getKey()
                                + ", url=" + videoInfo.mUrl);
                outFileList.add(videoInfo);
            }

            if (!listResult.isTruncated()) {  // 最后一页
                listCompleted = true;
                errCode = ErrCode.XERR_EOF;
            }

            // 下一次列举文件的marker。
            mRetrieveMarker = listResult.getNextMarker();

        } catch (ClientException clientExp) {
            clientExp.printStackTrace();
            Log.e(TAG,"<retrieveFilesStep> [CLIENT_EXP] clientExp=" + clientExp.toString());
            return ErrCode.XERR_NETWORK;

        } catch (ServiceException serviceExp) {
            serviceExp.printStackTrace();
            Log.e(TAG,"<retrieveFilesStep> [SERVICE_EXP] serviceExp=" + serviceExp.toString());
            return ErrCode.XERR_SERVICE;
        }


        Log.d(TAG, "<retrieveFilesStep> done, fileCount=" + outFileList.size()
                + ", errCode=" + errCode);
        return errCode;
    }

    /*
     * @brief 异步检索一页文件
     * @param fileList : 输出检索到的当前页的文件列表
     * @return 返回错误码，XOK 表示当前检索成功，还可以继续检索
     *                    XERR_EOF 表示检索已经全部结束
     *                    其他值表示检索错误
     */
    public synchronized int asyncRetrieveFilesStep(int pageSize, List<AlarmVideoInfo> outFileList)
    {
        if (mOssInstance == null) {
            Log.e(TAG, "<retrieveFilesStep> [ERROR] bad status");
            return ErrCode.XERR_BAD_STATE;
        }

        ListObjectsRequest request = new ListObjectsRequest(mInitParam.mBucket);
        request.setMaxKeys(pageSize);
        request.setMarker(mRetrieveMarker);
        request.setPrefix("_96_");
        outFileList.clear();

        mRetrieveErrCode = ErrCode.XOK;

        OSSAsyncTask<ListObjectsResult> task = mOssInstance.asyncListObjects(request,
            new OSSCompletedCallback<ListObjectsRequest, ListObjectsResult>() {
                @Override
                public void onSuccess(ListObjectsRequest request, ListObjectsResult listResult) {
                    if (listResult == null) {
                        Log.e(TAG,"<asyncRetrieveFilesStep.onFailure> listResult is NULL");
                        return ;
                    }

                    for (OSSObjectSummary ossObj : listResult.getObjectSummaries()) {
                        AlarmVideoInfo videoInfo = new AlarmVideoInfo();
                        videoInfo.mOssInfo = ossObj;
                        videoInfo.mUrl = mOssInstance.presignPublicObjectURL(mInitParam.mBucket, ossObj.getKey());
                        Log.e(TAG,"<retrieveFilesStep> key=" + ossObj.getKey()
                                + ", url=" + videoInfo.mUrl);
                        outFileList.add(videoInfo);
                    }

                    if (!listResult.isTruncated()) {  // 最后一页
                        mRetrieveCompleted = true;
                        mRetrieveErrCode = ErrCode.XERR_EOF;
                    }

                    // 下一次列举文件的marker。
                    mRetrieveMarker = listResult.getNextMarker();
                }

                @Override
                public void onFailure(ListObjectsRequest request, ClientException clientException,
                                      ServiceException serviceException) {
                    if (clientException != null) {
                        Log.e(TAG,"<asyncRetrieveFilesStep.onFailure> [CLIENT_EXP] clientExp="
                                + clientException.toString());
                    }

                    if (serviceException != null) {
                        Log.e(TAG,"<asyncRetrieveFilesStep.onFailure> [SERVICE_EXP] serviceExp="
                                + serviceException.toString());
                    }

                    Log.e(TAG,"<asyncRetrieveFilesStep.onFailure> done");
                    mInitParam.mCallback.onRetrieveDone(ErrCode.XERR_SERVICE, null);
                }
            });


        return mRetrieveErrCode;
    }



    /*
     * @brief 同步删除视频文件
     * @param removeKeyList : 输入准备删除的视频文件的Key列表
     * @param delSuccessedList : 输出删除成功的Key列表
     * @param delFailedList : 输出删除失败的Key列表
     * @return 返回错误码
     */
    public synchronized int deleteFiles(List<String> removeKeyList,
                                        List<String> delSuccessedList,
                                        List<String> delFailedList   )
    {
        if (mOssInstance == null) {
            Log.e(TAG, "<deleteFiles> [ERROR] bad status");
            return ErrCode.XERR_BAD_STATE;
        }
        delSuccessedList.clear();
        delFailedList.clear();


        try {
            // 设置为简单模式，只返回删除失败的文件列表
            DeleteMultipleObjectRequest request = new DeleteMultipleObjectRequest(mInitParam.mBucket,
                    removeKeyList, true);
            DeleteMultipleObjectResult deleteResult = mOssInstance.deleteMultipleObject(request);
            if (deleteResult == null) {
                Log.e(TAG,"<deleteFiles> fail to deleteMultipleObject()");
                return ErrCode.XERR_UNKNOWN;
            }
            delSuccessedList.addAll(deleteResult.getDeletedObjects());
            delFailedList.addAll(deleteResult.getFailedObjects());

        } catch (ClientException clientExp) {
            clientExp.printStackTrace();
            Log.e(TAG,"<deleteFiles> [CLIENT_EXP] clientExp=" + clientExp.toString());
            return ErrCode.XERR_NETWORK;

        } catch (ServiceException serviceExp) {
            serviceExp.printStackTrace();
            Log.e(TAG,"<deleteFiles> [SERVICE_EXP] serviceExp=" + serviceExp.toString());
            return ErrCode.XERR_SERVICE;
        }

        Log.d(TAG, "<deleteFiles> done, removeCount=" + removeKeyList.size()
                + ", successCount=" + delSuccessedList.size()
                + ", failCount=" + delFailedList.size());
        return ErrCode.XOK;
    }



}