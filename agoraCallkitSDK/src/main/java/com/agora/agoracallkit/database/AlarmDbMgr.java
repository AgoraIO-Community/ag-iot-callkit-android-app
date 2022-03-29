/**
 * @file AlarmDbMgr.java
 * @brief This file implement the database management of device alarm
 * @author xiaohua.lu
 * @email luxiaohua@agora.io
 * @version 1.0.0.1
 * @date 2021-12-01
 * @license Copyright (C) 2021 AgoraIO Inc. All rights reserved.
 */
package com.agora.agoracallkit.database;


import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.agora.agoracallkit.callkit.AgoraCallKit;
import com.agora.agoracallkit.logger.ALog;

import java.util.ArrayList;
import java.util.List;


public class AlarmDbMgr {
    ////////////////////////////////////////////////////////////////////////
    //////////////////////// Constant Definition ///////////////////////////
    ////////////////////////////////////////////////////////////////////////
    private static final String TAG = "CALLKIT/AlarmDbMgr";

    // the SQL statement for create table
    public static final String CREATE_ALARM_TAB = "create table Alarm ( "
            + "id integer primary key autoincrement, "
            + "timestamp integer, "
            + "deviceId text, "
            + "uid integer, "
            + "type integer, "
            + "priority integer, "
            + "url text, "
            + "message text  )";
    private static final String TAB_NAME = "Alarm";             ///< table name
    private static final String FIELD_ID = "id";
    private static final String FIELD_TIMESTAMP = "timestamp";
    private static final String FIELD_DEVICEID = "deviceId";
    private static final String FIELD_UID = "uid";
    private static final String FIELD_TYPE = "type";
    private static final String FIELD_PRIORITY = "priority";
    private static final String FIELD_URL = "url";
    private static final String FIELD_MESSAGE = "message";
    private static final String[] ALL_FIELDS = {
        FIELD_ID, FIELD_TIMESTAMP, FIELD_DEVICEID, FIELD_UID,
        FIELD_TYPE, FIELD_PRIORITY, FIELD_URL, FIELD_MESSAGE
    };



    ////////////////////////////////////////////////////////////////////////
    //////////////////////// Variable Definition ///////////////////////////
    ////////////////////////////////////////////////////////////////////////
    private Context mContext;
    private String mDbName;
    private AlarmDbHelper mDbHelper;


    ////////////////////////////////////////////////////////////////////////////
    ////////////////////// Override SQLiteOpenHelper Methods ////////////////////
    ////////////////////////////////////////////////////////////////////////////
    /*
     * @brief 初始化告警数据库，如果没有数据库则自动创建
     * @param ctx : 数据库上下文
     * @param dbName : 数据库名字
     * @return 返回成功与否
     */
    public synchronized boolean initialize(Context ctx, String dbName) {
        mContext = ctx;
        mDbName = dbName;
        mDbHelper = new AlarmDbHelper(ctx, dbName, null, 1);
        ALog.getInstance().d(TAG, "<initialize> done, dbName=" + dbName);
        return true;
    }

    /*
     * @brief 释放告警数据库
     */
    public synchronized void release() {
        if (mDbHelper != null) {
            mDbHelper.close();
            mDbHelper = null;
            ALog.getInstance().d(TAG, "<release> done");
        }
    }


    /*
     * @brief 插入告警记录
     * @param recordList : 告警记录列表
     * @return 返回成功与否
     */
    public synchronized boolean insertRecords(List<AgoraCallKit.AlarmRecord> recordList) {
        int recordCount = recordList.size();
        ALog.getInstance().d(TAG, "<insertRecords> ==>Enter, recordCount=" + recordCount);

        // 获取到了 SQLiteDatabase 对象
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        if (db == null) {
            ALog.getInstance().e(TAG, "<insertRecords> <==Exit with error");
            return false;
        }

        for (int i = 0; i < recordCount; i++) {
            AgoraCallKit.AlarmRecord record = recordList.get(i);

            ContentValues values = new ContentValues();
            values.put(FIELD_TIMESTAMP, record.mTimestamp);
            values.put(FIELD_DEVICEID, record.mDeviceId);
            values.put(FIELD_UID, record.mUid);
            values.put(FIELD_TYPE, record.mType);
            values.put(FIELD_PRIORITY, record.mPriority);
            values.put(FIELD_URL, record.mVideoUrl);
            values.put(FIELD_MESSAGE, record.mMessage);

            db.insert(TAB_NAME,null, values);
            values.clear();

            ALog.getInstance().d(TAG, "<insertRecords> insert record=" + record.toString());
        }

        //关闭数据库资源
        db.close();

        ALog.getInstance().d(TAG, "<insertRecords> <==Exit");
        return true;
    }

    /*
     * @brief 根据deviceId来查询记录
     * @param deviceId : 需要查询的deviceId
     * @return 查询到的记录列表
     */
    @SuppressLint("Range")
    public synchronized List<AgoraCallKit.AlarmRecord> queryByDeviceId(String deviceId) {
        ArrayList<AgoraCallKit.AlarmRecord> recordList = new ArrayList<>();

        // 获取到了 SQLiteDatabase 对象
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        if (db == null) {
            ALog.getInstance().e(TAG, "<queryByDeviceId> error, deviceId=" + deviceId);
            return recordList;
        }

        Cursor cursor = db.query(false, TAB_NAME, ALL_FIELDS,
                "deviceId like ?", new String[] {deviceId},
                null, null, "timestamp desc", null);
        if (cursor == null) {
            ALog.getInstance().e(TAG, "<queryByDeviceId> fail to query(), deviceId=" + deviceId);
            return recordList;
        }

        while (cursor.moveToNext()) {
            AgoraCallKit.AlarmRecord newRecord = new AgoraCallKit.AlarmRecord();
            newRecord.mRecordId = cursor.getLong(cursor.getColumnIndex(FIELD_ID));
            newRecord.mTimestamp = cursor.getLong(cursor.getColumnIndex(FIELD_TIMESTAMP));
            newRecord.mDeviceId = cursor.getString(cursor.getColumnIndex(FIELD_DEVICEID));
            newRecord.mUid = cursor.getLong(cursor.getColumnIndex(FIELD_UID));
            newRecord.mType = cursor.getInt(cursor.getColumnIndex(FIELD_TYPE));
            newRecord.mPriority = cursor.getInt(cursor.getColumnIndex(FIELD_PRIORITY));
            newRecord.mVideoUrl = cursor.getString(cursor.getColumnIndex(FIELD_URL));
            newRecord.mMessage = cursor.getString(cursor.getColumnIndex(FIELD_MESSAGE));
            recordList.add(newRecord);
        }

        //关闭数据库资源
        db.close();

        ALog.getInstance().e(TAG, "<queryByDeviceId> done, deviceId=" + deviceId
                + ", queriedCount=" + recordList.size());
        return recordList;
    }

    /*
     * @brief 查询所有的告警记录
     * @param None
     * @return 查询到的记录列表
     */
    @SuppressLint("Range")
    public synchronized List<AgoraCallKit.AlarmRecord> queryAll() {
        ArrayList<AgoraCallKit.AlarmRecord> recordList = new ArrayList<>();

        // 获取到了 SQLiteDatabase 对象
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        if (db == null) {
            ALog.getInstance().e(TAG, "<queryAll> error");
            return recordList;
        }

        Cursor cursor = db.query(false, TAB_NAME, ALL_FIELDS,
                null, null,
                null, null, "timestamp desc", null);
        if (cursor == null) {
            ALog.getInstance().e(TAG, "<queryAll> fail to query()");
            return recordList;
        }

        while (cursor.moveToNext()) {
            AgoraCallKit.AlarmRecord newRecord = new AgoraCallKit.AlarmRecord();
            newRecord.mRecordId = cursor.getLong(cursor.getColumnIndex(FIELD_ID));
            newRecord.mTimestamp = cursor.getLong(cursor.getColumnIndex(FIELD_TIMESTAMP));
            newRecord.mDeviceId = cursor.getString(cursor.getColumnIndex(FIELD_DEVICEID));
            newRecord.mUid = cursor.getLong(cursor.getColumnIndex(FIELD_UID));
            newRecord.mType = cursor.getInt(cursor.getColumnIndex(FIELD_TYPE));
            newRecord.mPriority = cursor.getInt(cursor.getColumnIndex(FIELD_PRIORITY));
            newRecord.mVideoUrl = cursor.getString(cursor.getColumnIndex(FIELD_URL));
            newRecord.mMessage = cursor.getString(cursor.getColumnIndex(FIELD_MESSAGE));
            recordList.add(newRecord);
        }

        //关闭数据库资源
        db.close();

        ALog.getInstance().d(TAG, "<queryAll> done, queriedCount=" + recordList.size());
        return recordList;
    }

    /*
     * @brief 根据recordId来删除记录
     * @param deviceId : 需要删除的 deviceId
     * @return 删除记录的个数
     */
    public synchronized int deleteByDeviceId(String deviceId) {
        // 获取到了 SQLiteDatabase 对象
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        if (db == null) {
            ALog.getInstance().e(TAG, "<deleteByDeviceId> open DB error");
            return 0;
        }

        int result = db.delete(TAB_NAME,"deviceId like ?", new String[]{deviceId});

        //关闭数据库资源
        db.close();

        ALog.getInstance().d(TAG, "<deleteByDeviceId> done, deviceId=" + deviceId
                + ", deletedCount=" + result);
        return result;
    }


    /*
     * @brief 根据recordId来删除记录
     * @param recordIdList : 需要删除的 recordId列表
     * @return 删除记录的个数
     */
    public synchronized int deleteByRecordIdList(List<Long> recordIdList) {

        // 获取到了 SQLiteDatabase 对象
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        if (db == null) {
            ALog.getInstance().e(TAG, "<deleteByRecordIdList> open DB error");
            return 0;
        }

        int deletedCount = 0;
        for (int i = 0; i < recordIdList.size(); i++) {
            long recordId = recordIdList.get(i);
            int result = db.delete(TAB_NAME,"id = ?", new String[]{String.valueOf(recordId)});
            deletedCount += result;
        }


        //关闭数据库资源
        db.close();

        ALog.getInstance().d(TAG, "<deleteByRecordIdList> done, deletedCount=" + deletedCount);
        return deletedCount;
    }



}