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

import android.content.Context;
import android.content.SharedPreferences;

import com.sensorsdata.analytics.android.sdk.SensorsDataThreadPool;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

class SharedPreferencesLoader {

    SharedPreferencesLoader() {
        sensorsDataThreadPool = SensorsDataThreadPool.getInstance();
    }

    Future<SharedPreferences> loadPreferences(Context context, String name) {

        //创建Callable任务
        final LoadSharedPreferences loadSharedPrefs =
                new LoadSharedPreferences(context, name);

        //创建FutureTask
        final FutureTask<SharedPreferences> task = new FutureTask<>(loadSharedPrefs);
        //线程池执行任务
        sensorsDataThreadPool.execute(task);
        return task;
    }

    //Callable返回SharedPreferences对象
    private static class LoadSharedPreferences implements Callable<SharedPreferences> {
        LoadSharedPreferences(Context context, String prefsName) {
            mContext = context;
            mPrefsName = prefsName;
        }

        @Override
        public SharedPreferences call() {
            final SharedPreferences ret = mContext.getSharedPreferences(mPrefsName, Context.MODE_PRIVATE);
            return ret;
        }

        private final Context mContext;
        private final String mPrefsName;
    }


    private final SensorsDataThreadPool sensorsDataThreadPool;
}
