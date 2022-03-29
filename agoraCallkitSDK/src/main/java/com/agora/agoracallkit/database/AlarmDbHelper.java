/**
 * @file AlarmDbHelper.java
 * @brief This file inherit the class of SQLiteOpenHelper
 *        The fields of alarm table are following:
 *          { id, deviceId, uid, type, priority, url }
 *
 * @author xiaohua.lu
 * @email luxiaohua@agora.io
 * @version 1.0.0.1
 * @date 2021-12-01
 * @license Copyright (C) 2021 AgoraIO Inc. All rights reserved.
 */
package com.agora.agoracallkit.database;

import android.content.Context;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.agora.agoracallkit.logger.ALog;


public class AlarmDbHelper  extends SQLiteOpenHelper {
    ////////////////////////////////////////////////////////////////////////
    //////////////////////// Constant Definition ///////////////////////////
    ////////////////////////////////////////////////////////////////////////
    private static final String TAG = "CALLKIT/AlarmDbHelper";


    ////////////////////////////////////////////////////////////////////////
    //////////////////////// Variable Definition ///////////////////////////
    ////////////////////////////////////////////////////////////////////////
    private Context mContext;



    ////////////////////////////////////////////////////////////////////////////
    ////////////////////// Override SQLiteOpenHelper Methods ////////////////////
    ////////////////////////////////////////////////////////////////////////////
    /*
     * @brief 构造函数
     * @param context : 数据库上下文
     * @param name : 数据库名字，数据库固定位于当前APP目录下
     * @param factory : 游标操作
     * @param version : 数据库版本号
     */
    public AlarmDbHelper(Context context, String name, SQLiteDatabase.CursorFactory factory, int version) {
        super(context, name, factory, version);
        this.mContext = context;
    }


    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        ALog.getInstance().d(TAG, "<onCreate>");

        try {
            sqLiteDatabase.execSQL(AlarmDbMgr.CREATE_ALARM_TAB); //执行建表语句，创建数据库

        } catch (SQLException sqlExcept) {
            sqlExcept.printStackTrace();
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {
        ALog.getInstance().d(TAG, "<onUpgrade> i=" + i + ", i1=" + i1);
    }
}

