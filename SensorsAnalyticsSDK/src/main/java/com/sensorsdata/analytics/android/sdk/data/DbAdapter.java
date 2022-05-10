/*
 * Created by wangzhuozhou on 2015/08/01.
 * Copyright 2015－2019 Sensors Data Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sensorsdata.analytics.android.sdk.data;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.text.TextUtils;

import com.sensorsdata.analytics.android.sdk.SALog;
import com.sensorsdata.analytics.android.sdk.SensorsDataAPI;

import org.json.JSONObject;

import java.io.File;
import java.util.List;

public class DbAdapter {
    private static final String TAG = "SA.DbAdapter";
    private static DbAdapter instance;
    private final File mDatabaseFile;
    private final DbParams mDbParams;
    /* Session 时长间隔 */
    private int mSessionTime = 30 * 1000, mSavedSessionTime = 0;//保存的数据库备份
    /* AppPaused 的时间戳 */
    private long mAppPausedTime = 0;
    /* AppEnd 事件是否发送，true 发送、false 未发送 */
    private boolean mAppEndState = true;
    /* App 是否启动到 onResume */
    private boolean mAppStart = false;
    private ContentResolver contentResolver;
    private final Context mContext;


    public static DbAdapter getInstance(Context context, String packageName) {
        if (instance == null) {
            instance = new DbAdapter(context, packageName);
        }
        return instance;
    }

    public static DbAdapter getInstance() {
        if (instance == null) {
            throw new IllegalStateException("The static method getInstance(Context context, String packageName) should be called before calling getInstance()");
        }
        return instance;
    }

    private long getMaxCacheSize(Context context) {
        try {
            return SensorsDataAPI.sharedInstance(context).getMaxCacheSize();
        } catch (Exception e) {
            com.sensorsdata.analytics.android.sdk.SALog.printStackTrace(e);
            return 32 * 1024 * 1024;
        }
    }

    private boolean belowMemThreshold() {
        if (mDatabaseFile.exists()) {
            return Math.max(
                    mDatabaseFile.getUsableSpace(),
                    getMaxCacheSize(mContext)
            ) < mDatabaseFile.length();
        }
        return false;
    }

    private DbAdapter(Context context, String packageName) {
        mContext = context.getApplicationContext();
        contentResolver = mContext.getContentResolver();
        mDatabaseFile = context.getDatabasePath(DbParams.DATABASE_NAME);
        mDbParams = DbParams.getInstance(packageName);
    }

    /**
     * Adds a JSON string representing an event with properties or a person record
     * to the SQLiteDatabase.
     *
     * @param j the JSON to record
     * @return the number of rows in the table, or DB_OUT_OF_MEMORY_ERROR/DB_UPDATE_ERROR
     * on failure
     */
    public int addJSON(JSONObject j) {
        // we are aware of the race condition here, but what can we do..?
        int count = DbParams.DB_UPDATE_ERROR;
        Cursor c = null;
        try {
            if (belowMemThreshold()) {
                SALog.i(TAG, "There is not enough space left on the device to store events, so will delete some old events");
                String[] eventsData = generateDataString(DbParams.TABLE_EVENTS, 100);
                if (eventsData == null) {
                    return DbParams.DB_OUT_OF_MEMORY_ERROR;
                }

                final String lastId = eventsData[0];
                count = cleanupEvents(lastId);
                if (count <= 0) {
                    return DbParams.DB_OUT_OF_MEMORY_ERROR;
                }
            }

            final ContentValues cv = new ContentValues();
            cv.put(DbParams.KEY_DATA, j.toString() + "\t" + j.toString().hashCode());
            cv.put(DbParams.KEY_CREATED_AT, System.currentTimeMillis());
            contentResolver.insert(mDbParams.getEventUri(), cv);
            c = contentResolver.query(mDbParams.getEventUri(), null, null, null, null);
            if (c != null) {
                count = c.getCount();
            }
        } catch (Exception e) {
            com.sensorsdata.analytics.android.sdk.SALog.printStackTrace(e);
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return count;
    }

    /**
     * Adds a JSON string representing an event with properties or a person record
     * to the SQLiteDatabase.
     *
     * @param eventsList the JSON to record
     * @return the number of rows in the table, or DB_OUT_OF_MEMORY_ERROR/DB_UPDATE_ERROR
     * on failure
     */
    public int addJSON(List<JSONObject> eventsList) {
        // we are aware of the race condition here, but what can we do..?
        int count = DbParams.DB_UPDATE_ERROR;
        Cursor c = null;
        try {
            if (belowMemThreshold()) {
                SALog.i(TAG, "There is not enough space left on the device to store events, so will delete some old events");
                String[] eventsData = generateDataString(DbParams.TABLE_EVENTS, 100);
                if (eventsData == null) {
                    return DbParams.DB_OUT_OF_MEMORY_ERROR;
                }
                final String lastId = eventsData[0];
                count = cleanupEvents(lastId);
                if (count <= 0) {
                    return DbParams.DB_OUT_OF_MEMORY_ERROR;
                }
            }
            ContentValues[] contentValues = new ContentValues[eventsList.size()];
            ContentValues cv;
            int index = 0;
            for (JSONObject j : eventsList) {
                cv = new ContentValues();
                cv.put(DbParams.KEY_DATA, j.toString() + "\t" + j.toString().hashCode());
                cv.put(DbParams.KEY_CREATED_AT, System.currentTimeMillis());
                contentValues[index++] = cv;
            }
            contentResolver.bulkInsert(mDbParams.getEventUri(), contentValues);
            c = contentResolver.query(mDbParams.getEventUri(), null, null, null, null);
            if (c != null) {
                count = c.getCount();
            }
        } catch (Exception e) {
            com.sensorsdata.analytics.android.sdk.SALog.printStackTrace(e);
        } finally {
            try {
                if (c != null) {
                    c.close();
                }
            } finally {

            }
        }
        return count;
    }

    /**
     * Removes all events from table
     */
    public void deleteAllEvents() {
        try {
            contentResolver.delete(mDbParams.getEventUri(), null, null);
        } catch (Exception e) {
            com.sensorsdata.analytics.android.sdk.SALog.printStackTrace(e);
        }
    }

    /**
     * Removes events with an _id &lt;= last_id from table
     *
     * @param last_id the last id to delete
     * @return the number of rows in the table
     */
    public int cleanupEvents(String last_id) {
        Cursor c = null;
        int count = DbParams.DB_UPDATE_ERROR;

        try {
            contentResolver.delete(mDbParams.getEventUri(), "_id <= ?", new String[]{last_id});
            c = contentResolver.query(mDbParams.getEventUri(), null, null, null, null);
            if (c != null) {
                count = c.getCount();
            }
        } catch (Exception e) {
            com.sensorsdata.analytics.android.sdk.SALog.printStackTrace(e);
        } finally {
            try {
                if (c != null) {
                    c.close();
                }
            } finally {

            }
        }
        return count;
    }

    /**
     * 保存 Activity 的状态
     *
     * @param appStart Activity 的状态
     */
    public void commitAppStart(boolean appStart) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(DbParams.TABLE_APP_STARTED, appStart);
        contentResolver.insert(mDbParams.getAppStartUri(), contentValues);
        mAppStart = appStart;
    }

    /**
     * 返回 Activity 的状态
     *
     * @return Activity 的状态
     */
    public boolean getAppStart() {
        return mAppStart;
    }

    /**
     * 保存 Activity 启动时间戳
     *
     * @param appStartTime Activity 启动时间戳
     */
    public void commitAppStartTime(long appStartTime) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(DbParams.TABLE_APP_START_TIME, appStartTime);
        contentResolver.insert(mDbParams.getAppStartTimeUri(), contentValues);
    }

    /**
     * 获取 Activity 启动时间
     *
     * @return Activity 启动时间戳
     */
    public long getAppStartTime() {
        long startTime = 0;
        Cursor cursor = contentResolver.query(mDbParams.getAppStartTimeUri(), null, null, null, null);
        if (cursor != null && cursor.getCount() > 0) {
            while (cursor.moveToNext()) {
                startTime = cursor.getLong(0);
            }
        }

        if (cursor != null) {
            cursor.close();
        }
        SALog.d(TAG, "getAppStartTime:" + startTime);
        return startTime;
    }

    /**
     * 保存 Activity 暂停时间戳
     *
     * @param appPausedTime Activity 暂停时间戳
     */
    public void commitAppPausedTime(long appPausedTime) {
        try {
            ContentValues contentValues = new ContentValues();
            contentValues.put(DbParams.TABLE_APP_PAUSED_TIME, appPausedTime);
            contentResolver.insert(mDbParams.getAppPausedUri(), contentValues);
        } catch (Exception ex) {
            SALog.printStackTrace(ex);
        }
        mAppPausedTime = appPausedTime;
    }

    /**
     * 获取 Activity 暂停时间戳
     *
     * @return Activity 暂停时间戳
     */
    public long getAppPausedTime() {
        if (System.currentTimeMillis() - mAppPausedTime > mSessionTime) {
            Cursor cursor = null;
            try {
                cursor = contentResolver.query(mDbParams.getAppPausedUri(), null, null, null, null);
                if (cursor != null && cursor.getCount() > 0) {
                    while (cursor.moveToNext()) {
                        mAppPausedTime = cursor.getLong(0);
                    }
                }
            } catch (Exception e) {
                com.sensorsdata.analytics.android.sdk.SALog.printStackTrace(e);
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        return mAppPausedTime;
    }

    /**
     * 保存 $AppEnd 事件状态
     *
     * @param appEndState $AppEnd 事件状态
     */
    public void commitAppEndState(boolean appEndState) {
        if (appEndState == mAppEndState) return;
        try {
            ContentValues contentValues = new ContentValues();
            contentValues.put(DbParams.TABLE_APP_END_STATE, appEndState);
            contentResolver.insert(mDbParams.getAppEndStateUri(), contentValues);
        } catch (Exception e) {
            com.sensorsdata.analytics.android.sdk.SALog.printStackTrace(e);
        }

        mAppEndState = appEndState;
    }

    /**
     * 获取 $AppEnd 事件状态
     *
     * @return $AppEnd 事件状态
     */
    public boolean getAppEndState() {
        Cursor cursor = null;
        if (mAppEndState) {
            try {
                cursor = contentResolver.query(mDbParams.getAppEndStateUri(), null, null, null, null);
                if (cursor != null && cursor.getCount() > 0) {
                    while (cursor.moveToNext()) {
                        mAppEndState = cursor.getInt(0) > 0;
                    }
                }
            } catch (Exception e) {
                com.sensorsdata.analytics.android.sdk.SALog.printStackTrace(e);
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }

        SALog.d(TAG, "getAppEndState:" + mAppEndState);
        return mAppEndState;
    }

    /**
     * 获取 $AppEnd 事件数据
     *
     * @param appEndData $AppEnd 事件数据
     */
    public void commitAppEndData(String appEndData) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(DbParams.TABLE_APP_END_DATA, appEndData);
        contentResolver.insert(mDbParams.getAppEndDataUri(), contentValues);
    }

    /**
     * 获取 $AppEnd 事件数据
     *
     * @return $AppEnd 事件数据
     */
    public String getAppEndData() {
        String data = "";
        Cursor cursor = contentResolver.query(mDbParams.getAppEndDataUri(), null, null, null, null);
        if (cursor != null && cursor.getCount() > 0) {
            while (cursor.moveToNext()) {
                data = cursor.getString(0);
            }
        }

        if (cursor != null) {
            cursor.close();
        }
        SALog.d(TAG, "getAppEndData:" + data);
        return data;
    }

    /**
     * 存储 LoginId
     *
     * @param loginId 登录 Id
     */
    public void commitLoginId(String loginId) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(DbParams.TABLE_LOGIN_ID, loginId);
        contentResolver.insert(mDbParams.getLoginIdUri(), contentValues);
    }

    /**
     * 获取 LoginId
     *
     * @return LoginId
     */
    public String getLoginId() {
        String data = "";
        Cursor cursor = contentResolver.query(mDbParams.getLoginIdUri(), null, null, null, null);
        if (cursor != null && cursor.getCount() > 0) {
            while (cursor.moveToNext()) {
                data = cursor.getString(0);
            }
        }

        if (cursor != null) {
            cursor.close();
        }
        SALog.d(TAG, "getLoginId:" + data);
        return data;
    }

    /**
     * 存储 $AppEnd 触发 Session 时长
     *
     * @param sessionIntervalTime $AppEnd 触发 Session 时长
     */
    public void commitSessionIntervalTime(int sessionIntervalTime) {
        if (sessionIntervalTime == mSavedSessionTime) return;
        try {
            ContentValues contentValues = new ContentValues();
            contentValues.put(DbParams.TABLE_SESSION_INTERVAL_TIME, sessionIntervalTime);
            contentResolver.insert(mDbParams.getSessionTimeUri(), contentValues);
        } catch (Exception e) {
            com.sensorsdata.analytics.android.sdk.SALog.printStackTrace(e);
        }
        mSavedSessionTime = sessionIntervalTime;
    }

    /**
     * 返回 $AppEnd 触发 Session 时长
     *
     * @return $AppEnd 触发 Session 时长
     */
    public int getSessionIntervalTime() {
        //如果不同
        if (mSessionTime != mSavedSessionTime) {
            Cursor cursor = null;
            try {
                //尝试获取存储的数据
                cursor = contentResolver.query(mDbParams.getSessionTimeUri(), null, null, null, null);
                //如果存储过数据
                if (cursor != null && cursor.getCount() > 0) {
                    while (cursor.moveToNext()) {
                        mSessionTime = cursor.getInt(0);
                    }
                }
            } catch (Exception e) {
                com.sensorsdata.analytics.android.sdk.SALog.printStackTrace(e);
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        mSavedSessionTime = mSessionTime;
        SALog.d(TAG, "getSessionIntervalTime:" + mSessionTime);
        return mSessionTime;
    }

    public String[] generateDataString(String tableName, int limit) {
        Cursor c = null;
        String data = null;
        String last_id = null;
        try {
            c = contentResolver.query(mDbParams.getEventUri(), null, null, null, DbParams.KEY_CREATED_AT + " ASC LIMIT " + String.valueOf(limit));

            if (c != null) {
                StringBuilder dataBuilder = new StringBuilder();
                final String flush_time = ",\"_flush_time\":";
                String suffix = ",";
                dataBuilder.append("[");
                String keyData, crc, content;
                while (c.moveToNext()) {
                    if (c.isLast()) {
                        suffix = "]";
                        last_id = c.getString(c.getColumnIndex("_id"));
                    }
                    try {
                        keyData = c.getString(c.getColumnIndex(DbParams.KEY_DATA));
                        if (!TextUtils.isEmpty(keyData)) {
                            int index = keyData.lastIndexOf("\t");
                            if (index > -1) {
                                crc = keyData.substring(index).replaceFirst("\t", "");
                                content = keyData.substring(0, index);
                                if (TextUtils.isEmpty(content) || TextUtils.isEmpty(crc)
                                        || !crc.equals(String.valueOf(content.hashCode()))) {
                                    continue;
                                }
                                keyData = content;
                            }
                            dataBuilder.append(keyData, 0, keyData.length() - 1)
                                    .append(flush_time)
                                    .append(System.currentTimeMillis())
                                    .append("}").append(suffix);
                        }
                    } catch (Exception e) {
                        SALog.printStackTrace(e);
                    }
                }
                data = dataBuilder.toString();
            }
        } catch (final SQLiteException e) {
            SALog.i(TAG, "Could not pull records for SensorsData out of database " + tableName
                    + ". Waiting to send.", e);
            last_id = null;
            data = null;
        } finally {
            if (c != null) {
                c.close();
            }
        }

        if (last_id != null) {
            return new String[]{last_id, data};
        }
        return null;
    }
}
