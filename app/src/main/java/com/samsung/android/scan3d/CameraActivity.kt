/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.samsung.android.scan3d

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.samsung.android.scan3d.databinding.ActivityCameraBinding
import com.samsung.android.scan3d.http.HttpService
import com.samsung.android.scan3d.serv.Cam
import kotlinx.coroutines.channels.Channel


class CameraActivity : AppCompatActivity() {

    private lateinit var activityCameraBinding: ActivityCameraBinding

    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            finish()
            //      android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("CAMERAACTIVITY", "CAMERAACTIVITY onCreate")
        activityCameraBinding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(activityCameraBinding.root)
        sendCam {
            it.action = "start"
        }
        registerReceiver(receiver, IntentFilter("KILL"))
    }

    override fun onPause() {
        super.onPause()
        sendCam {
            it.action = "onPause"
        }
    }

    fun sendCam(extra: (Intent) -> Unit) {
        var intent = Intent(this, Cam::class.java)
        extra(intent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sendCam {
            it.action = "stop"
        }
        unregisterReceiver(receiver)
    }

    override fun onResume() {
        super.onResume()
        sendCam {
            it.action = "onResume"
        }
    }
}
