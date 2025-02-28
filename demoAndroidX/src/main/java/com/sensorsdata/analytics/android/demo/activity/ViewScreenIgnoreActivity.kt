/*
 * Created by chenru on 2019/06/20.
 * Copyright 2015－2022 Sensors Data Inc.
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

package com.sensorsdata.analytics.android.demo.activity

import android.os.Bundle
import com.sensorsdata.analytics.android.demo.R
import com.sensorsdata.analytics.android.sdk.SensorsDataIgnoreTrackAppViewScreen
import kotlinx.android.synthetic.main.activity_view_screen.*

@SensorsDataIgnoreTrackAppViewScreen
class ViewScreenIgnoreActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_screen)
        btn.text = ""
    }
}