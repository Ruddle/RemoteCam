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
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.samsung.android.scan3d.databinding.ActivityCameraBinding
import com.samsung.android.scan3d.serv.Cam
import com.samsung.android.scan3d.serv.CameraActionState
import com.samsung.android.scan3d.serv.CameraActionState.ON_PAUSE
import com.samsung.android.scan3d.serv.CameraActionState.ON_RESUME
import com.samsung.android.scan3d.serv.CameraActionState.START
import com.samsung.android.scan3d.serv.CameraActionState.STOP

const val KILL_THE_APP = "KILL"

class CameraActivity : AppCompatActivity() {

    private var _binding: ActivityCameraBinding? = null
    private val binding get() = _binding!!

    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            finish()
            //      android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setCameraForegroundServiceState(START)
        registerReceiver(receiver, IntentFilter(KILL_THE_APP))
    }

    override fun onPause() {
        super.onPause()
        setCameraForegroundServiceState(ON_PAUSE)
    }

    override fun onDestroy() {
        _binding = null
        super.onDestroy()
        setCameraForegroundServiceState(STOP)
        unregisterReceiver(receiver)
    }

    override fun onResume() {
        super.onResume()
        setCameraForegroundServiceState(ON_RESUME)
    }

    fun setCameraForegroundServiceState(action: CameraActionState, extra: ((Intent) -> Unit)? = null) {
        try {
            val intent = Intent(this, Cam::class.java).also { it.action = action.name }
            if (extra != null) extra(intent)

            startForegroundService(intent)
        } catch (exc: Throwable) {
            Log.e("CameraFragment.TAG", "Error closing camera", exc)
        }
    }
}
